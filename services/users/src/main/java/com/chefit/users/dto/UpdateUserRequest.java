package com.chefit.users.dto;

// HTTP request body for PUT /users/{id}.
// All fields are optional (null = don't change). Password change requires both
// currentPassword (for verification) and newPassword (the replacement).
public record UpdateUserRequest(
    String realname,
    String identifier,
    String currentPassword,
    String newPassword
) {}
