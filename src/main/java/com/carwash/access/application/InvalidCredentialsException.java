package com.carwash.access.application;

public class InvalidCredentialsException extends RuntimeException {
    public static final String SAFE_MESSAGE = "Invalid email or password";
    public InvalidCredentialsException() { super(SAFE_MESSAGE); }
}
