package com.backend.Sstorage.common.error;

public class BusinessConflictException extends RuntimeException {
    private final String errorCode;

    public BusinessConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
