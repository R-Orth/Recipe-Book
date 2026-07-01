package com.chefit.auth.dao;

// Thrown by UserDAO.save when SETNX on idx:identifier loses the race to another writer.
// The controller catches this and returns 401 (generic — no auth-method discriminator).
public class IdentifierTakenException extends RuntimeException {
    private final String identifier;

    public IdentifierTakenException(String identifier) {
        super("Identifier already taken: " + identifier);
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }
}
