package com.carwash.shared.infrastructure;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

public final class PersistenceSupport {

    private PersistenceSupport() {
    }

    public static LocalDateTime databaseTime(LocalDateTime value) {
        if (value == null) return null;
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    public static short nanoRemainder(LocalDateTime value) {
        return value == null ? 0 : (short) (value.getNano() % 1_000);
    }

    public static LocalDateTime domainTime(LocalDateTime value, short nanoRemainder) {
        if (value == null) return null;
        return value.withNano(value.getNano() / 1_000 * 1_000 + nanoRemainder);
    }

    public static boolean constraint(DataIntegrityViolationException failure, String constraintName) {
        Throwable cause = failure;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraintName)) return true;
            cause = cause.getCause();
        }
        return false;
    }

}
