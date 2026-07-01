package com.chefit.users.controller;

import com.chefit.users.dao.IdentifierTakenException;
import com.chefit.users.dao.UpdateRequest;
import com.chefit.users.dao.UserDAO;
import com.chefit.users.dto.DeleteUserRequest;
import com.chefit.users.dto.ErrorResponse;
import com.chefit.users.dto.PublicUser;
import com.chefit.users.dto.UpdateUserRequest;
import com.chefit.users.model.User;
import com.chefit.users.service.JwtService;
import com.chefit.users.service.PasswordHasher;
import com.chefit.users.service.PasswordPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

// Management interface over the shared user records. Every endpoint requires a valid
// ChefIt session JWT (validated locally — no call back to auth). Reads are open to any
// authenticated caller; mutations (PUT/DELETE) are self-only, gated on the JWT subject
// matching the path uuid. Sensitive fields are stripped via PublicUser on every response.
@RestController
@RequestMapping("/users")
public class UsersController {

    private final UserDAO userDAO;
    private final JwtService jwtService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;

    public UsersController(UserDAO userDAO, JwtService jwtService,
                           PasswordHasher passwordHasher, PasswordPolicy passwordPolicy) {
        this.userDAO = userDAO;
        this.jwtService = jwtService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(HttpServletRequest request) {
        if (subject(request) == null) return unauthorized();
        List<PublicUser> users = userDAO.findAll().stream().map(PublicUser::from).toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id, HttpServletRequest request) {
        if (subject(request) == null) return unauthorized();
        Optional<User> user = userDAO.findById(id);
        return user.<ResponseEntity<?>>map(u -> ResponseEntity.ok(PublicUser.from(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id,
                                        @RequestBody UpdateUserRequest body,
                                        HttpServletRequest request) {
        String caller = subject(request);
        if (caller == null) return unauthorized();
        if (!caller.equals(id)) return forbidden();

        Optional<User> found = userDAO.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        User stored = found.get();

        String newHashedPassword = null;
        if (body.newPassword() != null) {
            if (stored.hashedPassword() == null) {
                return ResponseEntity.badRequest().body(new ErrorResponse("no_password_auth"));
            }
            if (body.currentPassword() == null) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("invalid_request", List.of("missing_current_password")));
            }
            if (!passwordHasher.verify(body.currentPassword(), stored.hashedPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("invalid_credentials"));
            }
            List<String> violations = passwordPolicy.violations(body.newPassword());
            if (!violations.isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", violations));
            }
            newHashedPassword = passwordHasher.hash(body.newPassword());
        }

        UpdateRequest req = new UpdateRequest(body.realname(), body.identifier(), newHashedPassword);
        try {
            User updated = userDAO.update(id, req);
            return ResponseEntity.ok(PublicUser.from(updated));
        } catch (IdentifierTakenException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("identifier_taken"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id,
                                        @RequestBody DeleteUserRequest body,
                                        HttpServletRequest request) {
        String caller = subject(request);
        if (caller == null) return unauthorized();
        if (!caller.equals(id)) return forbidden();

        Optional<User> found = userDAO.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        User stored = found.get();

        if (body == null || body.password() == null) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("invalid_request", List.of("missing_password")));
        }
        if (stored.hashedPassword() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("no_password_auth"));
        }
        if (!passwordHasher.verify(body.password(), stored.hashedPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("invalid_credentials"));
        }

        userDAO.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Returns the validated JWT subject (user uuid), or null if the Authorization header
    // is missing/malformed, the signature is invalid, or the token carries no subject.
    private String subject(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring("Bearer ".length());
        if (token.isEmpty() || token.contains(" ")) return null;
        try {
            Claims claims = jwtService.parse(token);
            String sub = claims.getSubject();
            return (sub == null || sub.isBlank()) ? null : sub;
        } catch (JwtException e) {
            return null;
        }
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("unauthorized"));
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("forbidden"));
    }
}
