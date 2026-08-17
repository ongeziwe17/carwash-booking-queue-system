package com.carwash.discovery.application;

import com.carwash.shared.exception.BusinessRuleViolationException;

public enum NearbyBranchSort {
    BRANCH_ID,
    DISTANCE;

    public static NearbyBranchSort fromApiValue(String value) {
        if (value == null) {
            return BRANCH_ID;
        }
        if ("distance".equals(value)) {
            return DISTANCE;
        }
        throw new BusinessRuleViolationException("Sort must be 'distance' when supplied");
    }
}
