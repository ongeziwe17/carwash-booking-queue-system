package com.carwash.marketplace.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public final class WeeklyIntervalId implements Serializable {
    public String branchId;
    public int order;
    public WeeklyIntervalId() { }
    public WeeklyIntervalId(String branchId, int order) { this.branchId=branchId; this.order=order; }
    @Override public boolean equals(Object other) { return this == other || other instanceof WeeklyIntervalId that && order == that.order && Objects.equals(branchId, that.branchId); }
    @Override public int hashCode() { return Objects.hash(branchId, order); }
}
