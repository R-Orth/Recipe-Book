package com.chefit.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private static final String PEPPER = "test-pepper-test-pepper-test";
    private static final String OTHER_PEPPER = "other-pepper-other-pepper-other";

    private PasswordHasher hasher(String pepper) {
        return new PasswordHasher(pepper);
    }

    // --- construction ---

    @Test
    void construction_requiresNonBlankPepper() {
        assertThrows(IllegalStateException.class, () -> new PasswordHasher(null));
        assertThrows(IllegalStateException.class, () -> new PasswordHasher(""));
        assertThrows(IllegalStateException.class, () -> new PasswordHasher("   "));
    }

    // --- hash format ---

    @Test
    void hash_producesSaltDollarHashFormat() {
        String stored = hasher(PEPPER).hash("Abcdefg12!");

        assertTrue(stored.contains("$"), "expected separator");
        String[] parts = stored.split("\\$");
        assertEquals(2, parts.length, "expected exactly one separator");
        assertFalse(parts[0].isEmpty(), "salt segment must not be empty");
        assertEquals(64, parts[1].length(), "hex SHA-256 is 64 chars");
        assertTrue(parts[1].matches("[a-f0-9]{64}"), "hex hash must be lowercase hex");
    }

    @Test
    void hash_outputIsNotPlaintext() {
        String plain = "Abcdefg12!";
        String stored = hasher(PEPPER).hash(plain);

        assertNotEquals(plain, stored);
        assertFalse(stored.contains(plain), "stored value must not contain plaintext");
    }

    // --- salt randomness ---

    @Test
    void hash_producesDifferentOutputEachCall() {
        PasswordHasher h = hasher(PEPPER);
        String plain = "Abcdefg12!";

        Set<String> outputs = new HashSet<>();
        for (int i = 0; i < 100; i++) outputs.add(h.hash(plain));

        assertEquals(100, outputs.size(),
                "salt randomness should make every hash of the same password unique");
    }

    @Test
    void hash_twoUsersWithSamePassword_getDifferentHashes() {
        PasswordHasher h = hasher(PEPPER);

        String aliceStored = h.hash("Abcdefg12!");
        String bobStored = h.hash("Abcdefg12!");

        assertNotEquals(aliceStored, bobStored,
                "same password must yield different stored values for different users");
    }

    // --- verify happy path ---

    @Test
    void verify_acceptsCorrectPassword() {
        PasswordHasher h = hasher(PEPPER);
        String stored = h.hash("Abcdefg12!");

        assertTrue(h.verify("Abcdefg12!", stored));
    }

    @Test
    void verify_rejectsWrongPassword() {
        PasswordHasher h = hasher(PEPPER);
        String stored = h.hash("Abcdefg12!");

        assertFalse(h.verify("WRONG-password!", stored));
    }

    // --- pepper rotation ---

    @Test
    void verify_rejectsHashWhenPepperRotated() {
        String stored = hasher(PEPPER).hash("Abcdefg12!");

        assertFalse(hasher(OTHER_PEPPER).verify("Abcdefg12!", stored),
                "rotating the pepper must invalidate stored hashes — operational property to be aware of");
    }

    // --- malformed stored values ---

    @ParameterizedTest(name = "malformed=\"{0}\"")
    @ValueSource(strings = {
            "",                                                           // empty
            "no-separator-at-all",                                        // no $
            "$onlyhash",                                                  // empty salt
            "onlysalt$",                                                  // empty hash
            "$",                                                          // only separator
            "salt$short",                                                 // hash too short
            "salt$zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",  // non-hex hash (64 z's)
            "!!!notbase64!!!$0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "salt$0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef$extra"
    })
    void verify_malformedStored_returnsFalse_notThrows(String stored) {
        assertFalse(hasher(PEPPER).verify("Abcdefg12!", stored));
    }

    @Test
    void verify_nullArgs_returnFalse_notThrow() {
        PasswordHasher h = hasher(PEPPER);
        assertFalse(h.verify(null, "x$y"));
        assertFalse(h.verify("x", null));
        assertFalse(h.verify(null, null));
    }

    // --- DoS guard ---

    @Test
    void hash_rejectsExcessivelyLongPlaintext() {
        PasswordHasher h = hasher(PEPPER);
        String tooLong = "A".repeat(1025);

        assertThrows(IllegalArgumentException.class, () -> h.hash(tooLong));
    }

    @Test
    void verify_rejectsExcessivelyLongPlaintext_withoutHashing() {
        // Verify must not pay CPU on attacker-supplied super-long inputs.
        PasswordHasher h = hasher(PEPPER);
        String stored = h.hash("Abcdefg12!");
        String tooLong = "A".repeat(1_000_000);

        assertFalse(h.verify(tooLong, stored));
    }

    // --- FAKE_HASH constant ---

    @Test
    void fakeHash_isWellFormedAndNeverMatchesRealPassword() {
        PasswordHasher h = hasher(PEPPER);

        assertNotNull(PasswordHasher.FAKE_HASH);
        assertTrue(PasswordHasher.FAKE_HASH.contains("$"));
        // Verifying any plain against FAKE_HASH consistently returns false (or at minimum
        // does not throw), so the dummy verify in AuthController is safe.
        assertFalse(h.verify("Abcdefg12!", PasswordHasher.FAKE_HASH));
        assertFalse(h.verify("anything-else", PasswordHasher.FAKE_HASH));
    }

    // --- UTF-8 ---

    @Test
    void hash_andVerify_roundTripUtf8Password() {
        PasswordHasher h = hasher(PEPPER);
        String plain = "Pässwörd1!Ω🔑";

        String stored = h.hash(plain);
        assertTrue(h.verify(plain, stored));
        assertFalse(h.verify(plain + "x", stored));
    }
}
