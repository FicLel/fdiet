package com.fdiet.users.domain.model;

import java.util.regex.Pattern;

public record Email(String email) {
    private static final Pattern emailRegex = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    public Email {
        if (email == null || !emailRegex.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format for: " + email);
        }
        email = email.trim();
    }
}
