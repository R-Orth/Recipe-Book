package com.chefit.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

// Verifies a Google ID token (GIS flow) against Google's public keys, checking
// signature, issuer, expiry, and that the audience matches our client id.
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    public record GoogleIdentity(String sub, String email, boolean emailVerified, String name) {}

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public Optional<GoogleIdentity> verify(String idTokenString) {
        try {
            GoogleIdToken token = verifier.verify(idTokenString);
            if (token == null) return Optional.empty();

            GoogleIdToken.Payload payload = token.getPayload();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            return Optional.of(new GoogleIdentity(
                    payload.getSubject(),
                    payload.getEmail(),
                    emailVerified,
                    (String) payload.get("name")));
        } catch (Exception e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
