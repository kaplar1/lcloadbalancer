package com.assignment.loadbalancer;

public final class NoServerAvailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoServerAvailableException(String message) {
        super(message);
    }
}
