package com.backend.Sstorage.common.error;

public class InvalidRequestException extends RuntimeException {
    private final String errorCode;

    public InvalidRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
