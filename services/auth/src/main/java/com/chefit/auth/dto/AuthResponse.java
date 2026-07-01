package com.chefit.auth.dto;

import java.util.List;

// Uniform success body for all three login-issuing endpoints (Google, register, login).
public record AuthResponse(
    String token,
    String uuid,
    String identifier,
    String realname,
    List<String> providers
) {
}
