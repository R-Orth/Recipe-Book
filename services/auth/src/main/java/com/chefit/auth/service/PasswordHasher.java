package com.chefit.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

// Salt + pepper SHA-256. Stored format: `<base64-salt>$<hex-hash>` where
// hash = SHA-256(salt || pepper || plaintext). Salt is per-user random; pepper is a
// server-side secret loaded from configuration (not committed). Rotating the pepper
// invalidates every stored hash by design.
//
// Note: SHA-256 + salt + pepper is meaningfully stronger than plain SHA-256 but still
// weaker than BCrypt/Argon2 against GPU brute-force. Locked in by Task 5 spec.
//
// FAKE_HASH is a precomputed stored value used for the dummy `verify` call on login
// failures where the user wasn't found — flattens timing so attackers can't enumerate
// accounts by response latency.
@Service
public class PasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final int MAX_PLAINTEXT_LENGTH = 1024;
    private static final String SEPARATOR = "$";

    public static final String FAKE_HASH =
            "ZmFrZS1zYWx0LWZvci10aW1pbmctZGVmZW5zZQ==$"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final byte[] pepper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHasher(@Value("${password.pepper}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("password.pepper must be configured (non-blank)");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String plain) {
        requirePlain(plain);
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = digest(salt, plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(salt) + SEPARATOR + toHex(hash);
    }

    public boolean verify(String plain, String stored) {
        if (plain == null || stored == null) return false;
        if (plain.length() > MAX_PLAINTEXT_LENGTH) return false;
        int sep = stored.indexOf(SEPARATOR);
        if (sep <= 0 || sep >= stored.length() - 1) return false;
        if (stored.indexOf(SEPARATOR, sep + 1) >= 0) return false;
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(stored.substring(0, sep));
            expected = fromHex(stored.substring(sep + 1));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (expected.length != 32) return false;
        byte[] actual = digest(salt, plain.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] digest(byte[] salt, byte[] plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pepper);
            md.update(plain);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void requirePlain(String plain) {
        if (plain == null) throw new IllegalArgumentException("plain must not be null");
        if (plain.length() > MAX_PLAINTEXT_LENGTH) {
            throw new IllegalArgumentException("plain exceeds max length " + MAX_PLAINTEXT_LENGTH);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        if ((hex.length() & 1) != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("non-hex character");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
