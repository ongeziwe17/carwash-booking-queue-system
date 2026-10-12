package com.carwash.shared.application;

import com.carwash.shared.exception.BusinessRuleViolationException;

/** Zero-based offset pagination. The bounded offset is representable by both adapters. */
public record PageRequest(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public PageRequest {
        if (page < 0 || size < 1 || size > MAX_SIZE || (long) page * size > Integer.MAX_VALUE)
            throw new BusinessRuleViolationException("Invalid page or size; size must be 1-100 and offset must fit a signed integer");
    }
    public int offset() { return Math.toIntExact((long) page * size); }
}
