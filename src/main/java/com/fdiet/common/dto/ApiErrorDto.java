package com.fdiet.common.dto;

import java.time.Instant;

/** Error body returned for every handled failure. */
public record ApiErrorDto(Instant timestamp, int status, String error, String message) {

    public static ApiErrorDto of(int status, String error, String message) {
        return new ApiErrorDto(Instant.now(), status, error, message);
    }
}
