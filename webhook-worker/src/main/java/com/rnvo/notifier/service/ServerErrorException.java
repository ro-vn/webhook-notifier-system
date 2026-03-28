package com.rnvo.notifier.service;

import lombok.Getter;

/**
 * Thrown when the target webhook endpoint returns a server error (5xx).
 * Used as a signal for Resilience4j retry.
 */
@Getter
public class ServerErrorException extends RuntimeException {

    private final int statusCode;

    public ServerErrorException(int statusCode) {
        super("Server error: HTTP " + statusCode);
        this.statusCode = statusCode;
    }

}
