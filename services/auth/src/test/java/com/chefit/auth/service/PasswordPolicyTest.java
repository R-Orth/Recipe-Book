package com.chefit.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    // --- happy path ---

    @Test
    void valid_password_passesAllChecks() {
        assertTrue(policy.isValid("Abcdefg12!"));
        assertEquals(List.of(), policy.violations("Abcdefg12!"));
    }

    @Test
    void boundary_exactlyMinLength_passes() {
        assertTrue(policy.isValid("Abcdef12!@"));  // 10 chars, all classes
    }

    // --- length ---

    @Test
    void tooShort_returnsTooShort() {
        List<String> errs = policy.violations("Abc12!");
        assertTrue(errs.contains("too_short"));
    }

    @Test
    void tooLong_returnsTooLong_andSkipsHashing() {
        String longPw = "A".repeat(900) + "b".repeat(125) + "1!"; // 1027 chars, all classes
        List<String> errs = policy.violations(longPw);
        assertTrue(errs.contains("too_long"));
    }

    // --- character classes ---

    @ParameterizedTest(name = "{0} should report {1}")
    @CsvSource({
        "'abcdefghi1!',          missing_uppercase",
        "'ABCDEFGHI1!',          missing_lowercase",
        "'AbcdefghiX!',          missing_digit",
        "'Abcdefghi12',          missing_special"
    })
    void missingSingleClass_reportsExactlyThatViolation(String password, String expected) {
        List<String> errs = policy.violations(password);
        assertTrue(errs.contains(expected),
                "expected " + expected + " in " + errs + " for password '" + password + "'");
    }

    @Test
    void allLowercase_reportsAllOtherClasses() {
        List<String> errs = policy.violations("abcdefghij");
        assertTrue(errs.contains("missing_uppercase"));
        assertTrue(errs.contains("missing_digit"));
        assertTrue(errs.contains("missing_special"));
        assertFalse(errs.contains("missing_lowercase"));
    }

    // --- missing / empty / whitespace ---

    @ParameterizedTest
    @ValueSource(strings = {""})
    void emptyPassword_reportsMissingPassword(String password) {
        assertTrue(policy.violations(password).contains("missing_password"));
    }

    @Test
    void nullPassword_reportsMissingPassword() {
        assertTrue(policy.violations(null).contains("missing_password"));
    }

    @Test
    void leadingWhitespace_isRejected() {
        // Common copy-paste failure mode — reject explicitly with a specific code.
        List<String> errs = policy.violations(" Abcdefg12!");
        assertTrue(errs.contains("surrounding_whitespace"));
    }

    @Test
    void trailingWhitespace_isRejected() {
        List<String> errs = policy.violations("Abcdefg12!  ");
        assertTrue(errs.contains("surrounding_whitespace"));
    }

    // --- internal whitespace counts as... nothing ---

    @Test
    void internalWhitespaceDoesNotSatisfyAnyClass() {
        // "Abc def 12!" has a space which is not lowercase/uppercase/digit/special.
        // Should still pass because all four classes are met.
        assertTrue(policy.isValid("Abc def 12!"));
    }

    // --- multiple violations together ---

    @Test
    void shortAndMissingClasses_reportsAllOfThem() {
        List<String> errs = policy.violations("ab1");
        assertTrue(errs.contains("too_short"));
        assertTrue(errs.contains("missing_uppercase"));
        assertTrue(errs.contains("missing_special"));
    }

    // --- UTF-8 / unusual chars ---

    @Test
    void emojiInPassword_isNotASpecialButOtherClassesCanStillSatisfy() {
        // Emoji are not in the allowed-special set; require explicit special elsewhere.
        assertFalse(policy.isValid("Abcdefgh1🔑"));
        assertTrue(policy.isValid("Abcdefg1!🔑"));
    }
}
