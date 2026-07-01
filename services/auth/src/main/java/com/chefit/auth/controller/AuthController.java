package com.chefit.auth.controller;

import com.chefit.auth.dao.IdentifierTakenException;
import com.chefit.auth.dao.UserDAO;
import com.chefit.auth.dto.AuthResponse;
import com.chefit.auth.dto.ErrorResponse;
import com.chefit.auth.dto.GoogleLoginRequest;
import com.chefit.auth.dto.LoginRequest;
import com.chefit.auth.dto.PublicUser;
import com.chefit.auth.dto.RegisterRequest;
import com.chefit.auth.model.User;
import com.chefit.auth.service.GoogleTokenVerifier;
import com.chefit.auth.service.GoogleTokenVerifier.GoogleIdentity;
import com.chefit.auth.service.JwtService;
import com.chefit.auth.service.PasswordHasher;
import com.chefit.auth.service.PasswordPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final ErrorResponse INVALID_CREDENTIALS = new ErrorResponse("invalid_credentials");
    private static final ErrorResponse REGISTRATION_UNAVAILABLE = new ErrorResponse("registration_unavailable");

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserDAO userDAO;
    private final JwtService jwtService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;

    public AuthController(GoogleTokenVerifier googleTokenVerifier,
                          UserDAO userDAO,
                          JwtService jwtService,
                          PasswordHasher passwordHasher,
                          PasswordPolicy passwordPolicy) {
        this.googleTokenVerifier = googleTokenVerifier;
        this.userDAO = userDAO;
        this.jwtService = jwtService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
    }

    // ------------------------------------------------------------------ Google sign-in

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody GoogleLoginRequest request,
                                             HttpServletRequest httpRequest) {
        Optional<GoogleIdentity> verified = (request.credential() == null)
                ? Optional.empty()
                : googleTokenVerifier.verify(request.credential());

        if (verified.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }

        GoogleIdentity identity = verified.get();
        String normalizedEmail = normalize(identity.email());

        Optional<User> byGoogle = userDAO.findByGoogleSub(identity.sub());
        if (byGoogle.isPresent()) {
            return ResponseEntity.ok(toAuthResponse(byGoogle.get()));
        }

        Optional<User> byIdentifier = userDAO.findByIdentifier(normalizedEmail);
        if (byIdentifier.isPresent()) {
            if (!identity.emailVerified()) {
                // Same-shape 401 used everywhere we refuse to confirm or deny an account.
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
            }
            User merged = userDAO.linkGoogle(byIdentifier.get().uuid(), identity.sub());
            return ResponseEntity.ok(toAuthResponse(merged));
        }

        try {
            User created = userDAO.save(new User(
                    null,
                    normalizedEmail,
                    true,
                    null,
                    identity.sub(),
                    List.of("google"),
                    identity.name(),
                    httpRequest.getRemoteAddr(),
                    null,
                    null));
            return ResponseEntity.ok(toAuthResponse(created));
        } catch (IdentifierTakenException e) {
            // Lost a race — fall back to indistinguishable 401.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }
    }

    // ------------------------------------------------------------------ Register (local)

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        List<String> validation = validateRegister(request);
        if (!validation.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", validation));
        }

        String normalized = normalize(request.identifier());
        boolean isEmail = User.looksLikeEmail(normalized);
        String hashed = passwordHasher.hash(request.password());

        try {
            User created = userDAO.save(new User(
                    null,
                    normalized,
                    isEmail,
                    hashed,
                    null,
                    List.of("local"),
                    request.realname(),
                    httpRequest.getRemoteAddr(),
                    null,
                    null));
            return ResponseEntity.ok(toAuthResponse(created));
        } catch (IdentifierTakenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(REGISTRATION_UNAVAILABLE);
        }
    }

    // ------------------------------------------------------------------ Login (local)

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        List<String> validation = validateLogin(request);
        if (!validation.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", validation));
        }

        String normalized = normalize(request.identifier());
        Optional<User> found = userDAO.findByIdentifier(normalized);

        if (found.isEmpty()) {
            // Dummy verify against a fake stored value flattens response timing so attackers
            // can't enumerate accounts by latency. Same on the Google-only branch below.
            passwordHasher.verify(request.password(), PasswordHasher.FAKE_HASH);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (user.hashedPassword() == null) {
            passwordHasher.verify(request.password(), PasswordHasher.FAKE_HASH);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }

        if (!passwordHasher.verify(request.password(), user.hashedPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_CREDENTIALS);
        }

        return ResponseEntity.ok(toAuthResponse(user));
    }

    // ------------------------------------------------------------------ GET /auth/{identifier} (self-only)

    @GetMapping("/{identifier}")
    public ResponseEntity<?> getUser(@PathVariable String identifier, HttpServletRequest httpRequest) {
        Optional<String> callerUuid = parseAuthorization(httpRequest);
        if (callerUuid.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> found = userDAO.findByIdentifier(normalize(identifier));
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!callerUuid.get().equals(found.get().uuid())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(PublicUser.from(found.get()));
    }

    // ------------------------------------------------------------------ helpers

    // Returns the caller's uuid (JWT sub) if the Authorization header carries a valid Bearer
    // token, otherwise empty. Treats: missing header, wrong scheme, multiple headers,
    // missing/extra whitespace, malformed/expired/wrong-signature tokens — all as 401.
    private Optional<String> parseAuthorization(HttpServletRequest request) {
        java.util.Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) return Optional.empty();
        String header = headers.nextElement();
        if (headers.hasMoreElements()) return Optional.empty(); // multiple Authorization headers

        if (header == null) return Optional.empty();
        int space = header.indexOf(' ');
        if (space < 0) return Optional.empty();
        String scheme = header.substring(0, space);
        String token = header.substring(space + 1);
        if (!"bearer".equalsIgnoreCase(scheme)) return Optional.empty();
        if (token.isEmpty() || token.startsWith(" ")) return Optional.empty();

        try {
            Claims claims = jwtService.parse(token);
            String sub = claims.getSubject();
            return (sub == null || sub.isBlank()) ? Optional.empty() : Optional.of(sub);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String normalize(String raw) {
        return raw == null ? null : raw.strip().toLowerCase();
    }

    private List<String> validateRegister(RegisterRequest r) {
        List<String> errors = new java.util.ArrayList<>();
        if (r == null) { errors.add("missing_body"); return errors; }
        if (r.identifier() == null || r.identifier().isBlank()) errors.add("missing_identifier");
        if (r.password() == null || r.password().isEmpty())     errors.add("missing_password");
        if (errors.isEmpty()) {
            errors.addAll(passwordPolicy.violations(r.password()));
            String id = r.identifier().strip();
            if (id.length() > 254) errors.add("identifier_too_long");
            if (id.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) errors.add("identifier_control_characters");
            if (id.contains("@") && !validEmail(id)) errors.add("invalid_email_shape");
        }
        return errors;
    }

    private List<String> validateLogin(LoginRequest r) {
        List<String> errors = new java.util.ArrayList<>();
        if (r == null) { errors.add("missing_body"); return errors; }
        if (r.identifier() == null || r.identifier().isBlank()) errors.add("missing_identifier");
        if (r.password() == null || r.password().isEmpty())     errors.add("missing_password");
        return errors;
    }

    private static boolean validEmail(String s) {
        int at = s.indexOf('@');
        if (at <= 0 || at >= s.length() - 1) return false;
        if (s.indexOf('@', at + 1) >= 0) return false;
        String local = s.substring(0, at);
        String domain = s.substring(at + 1);
        if (local.contains(" ") || domain.contains(" ")) return false;
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) return false;
        return true;
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.issue(user),
                user.uuid(),
                user.identifier(),
                user.realname(),
                user.providers());
    }
}
