package com.example.demo.exception;

/** An explicit external AI validation or availability failure; never an inferred result. */
public class AiServiceException extends RuntimeException {
    private final int statusCode;

    public AiServiceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiServiceException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
