package com.jsoftsip.core.exception;

public class RepositoryException extends JSoftSipException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}