package com.jsoftsip.core.exception;

public class JSoftSipException extends RuntimeException {

    public JSoftSipException(String message) {
        super(message);
    }

    public JSoftSipException(String message, Throwable cause) {
        super(message, cause);
    }
}