package com.chefit.auth.dto;

// Body of POST /auth/google — the ID token the GIS button hands the frontend.
public record GoogleLoginRequest(String credential) {
}
