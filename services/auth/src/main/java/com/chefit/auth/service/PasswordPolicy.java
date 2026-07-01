package com.chefit.auth.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// Locked policy: ≥ 10 chars, ≤ 1024 chars, must contain lowercase, uppercase, digit,
// and special character. Whitespace is not a class; passwords cannot consist of only
// whitespace; leading/trailing whitespace is rejected (common copy-paste failure mode).
//
// Modern guidance (NIST SP 800-63B-3) recommends length-only checks; the character-class
// requirement is by Task 5 spec.
@Service
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 1024;
    public static final String ALLOWED_SPECIAL = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";

    public List<String> violations(String plain) {
        List<String> errors = new ArrayList<>();
        if (plain == null || plain.isEmpty()) {
            errors.add("missing_password");
            return errors;
        }
        if (!plain.equals(plain.strip())) errors.add("surrounding_whitespace");
        if (plain.length() < MIN_LENGTH) errors.add("too_short");
        if (plain.length() > MAX_LENGTH) errors.add("too_long");

        boolean lower = false, upper = false, digit = false, special = false;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else if (ALLOWED_SPECIAL.indexOf(c) >= 0) special = true;
        }
        if (!lower)   errors.add("missing_lowercase");
        if (!upper)   errors.add("missing_uppercase");
        if (!digit)   errors.add("missing_digit");
        if (!special) errors.add("missing_special");
        return errors;
    }

    public boolean isValid(String plain) {
        return violations(plain).isEmpty();
    }
}
