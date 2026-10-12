package com.carwash.shared.application;

public record SortOrder(String field, boolean descending) {
    public static SortOrder asc(String field) { return new SortOrder(field, false); }
}
