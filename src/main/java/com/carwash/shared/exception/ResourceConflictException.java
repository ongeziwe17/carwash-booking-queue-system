package com.carwash.shared.exception;

/** Stable domain conflict for an already existing resource or terminal transition. */
public final class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) { super(message); }
}
