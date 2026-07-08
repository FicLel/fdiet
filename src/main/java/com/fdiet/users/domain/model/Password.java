package com.fdiet.users.domain.model;

public record Password(String hash) {
    private static final int PASSWORD_LENGTH = 4;

    public Password {
        if (hash == null || hash.isBlank() || hash.length() < PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password not valid");
        }
    }
}
