package com.chefit.users.dto;

// HTTP request body for DELETE /users/{id}.
// Password verification is required to confirm identity before deleting an account.
public record DeleteUserRequest(String password) {}
