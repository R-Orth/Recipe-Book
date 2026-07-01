package com.chefit.users.dao;

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
