package com.carwash.testsupport;

import java.util.Locale;
import java.util.Objects;

/** Creates readable identifiers whose sequence is local to one test method. */
public final class TestIdFactory {

    private static final int MAX_PREFIX_LENGTH = 32;

    private final String prefix;
    private int userSequence;
    private int vehicleSequence;
    private int serviceSequence;
    private int bookingSequence;
    private int queueSequence;
    private int notificationSequence;
    private int plateSequence;
    private int businessSequence;
    private int branchSequence;
    private int closureSequence;

    public TestIdFactory(String testName) {
        this.prefix = sanitizePrefix(testName);
    }

    public String user() {
        return next("user", ++userSequence);
    }

    public String vehicle() {
        return next("vehicle", ++vehicleSequence);
    }

    public String service() {
        return next("service", ++serviceSequence);
    }

    public String booking() {
        return next("booking", ++bookingSequence);
    }

    public String queueEntry() {
        return next("queue", ++queueSequence);
    }

    public String notification() {
        return next("notification", ++notificationSequence);
    }

    public String business() {
        return next("business", ++businessSequence);
    }

    public String branch() {
        return next("branch", ++branchSequence);
    }

    public String closure() {
        return next("closure", ++closureSequence);
    }

    public String emailFor(String id) {
        return sanitizeIdentifier(Objects.requireNonNull(id, "ID is required")) + "@example.test";
    }

    public String plate() {
        String platePrefix = prefix.length() > 20 ? prefix.substring(0, 20) : prefix;
        return ("PLT-" + platePrefix + "-" + String.format("%03d", ++plateSequence))
                .toUpperCase(Locale.ROOT);
    }

    private String next(String type, int sequence) {
        return prefix + "-" + type + "-" + String.format("%03d", sequence);
    }

    private static String sanitizePrefix(String value) {
        String sanitized = sanitizeIdentifier(value);
        return sanitized.length() > MAX_PREFIX_LENGTH
                ? sanitized.substring(0, MAX_PREFIX_LENGTH).replaceAll("-+$", "")
                : sanitized;
    }

    private static String sanitizeIdentifier(String value) {
        String sanitized = Objects.requireNonNullElse(value, "test")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "test" : sanitized;
    }
}
