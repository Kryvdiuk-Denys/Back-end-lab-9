package com.coworking.booking;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException(String message) {
        super(message);
    }

    public WorkspaceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
