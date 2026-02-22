package com.patternforge.api.dto;

/**
 * Generic error response body returned on 4xx / 5xx responses.
 *
 * @param status  always "error"
 * @param message human-readable description of what went wrong
 */
public record ApiErrorResponse(String status, String message) {

    /** Convenience factory — status is always "error". */
    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse("error", message);
    }
}
