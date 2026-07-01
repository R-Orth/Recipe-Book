package com.chefit.users.dao;

// Partial-update payload passed from the controller to UserDAO.update().
// All fields are nullable — DAO only writes non-null ones.
// newHashedPassword must already be hashed by the controller before passing here.
public record UpdateRequest(String realname, String identifier, String newHashedPassword) {}
