package com.rnvo.notifier.service;

import lombok.Getter;

/**
 * Thrown when the target webhook endpoint returns a server error (5xx).
 */
@Getter
public class ServerErrorException extends RuntimeException {

    private final int statusCode;

    public ServerErrorException(int statusCode) {
        super("Server error: HTTP " + statusCode);
        this.statusCode = statusCode;
    }

}
