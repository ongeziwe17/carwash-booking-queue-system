package com.carwash.feedback.application;

import com.carwash.shared.application.ListPolicy;
import com.carwash.shared.application.SortOrder;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeedbackListPolicy {
    private FeedbackListPolicy() { }
    public static final ListPolicy REVIEWS = new ListPolicy(Set.of("id", "createdAt", "rating"),
            List.of(new SortOrder("createdAt", true)), Map.of("branchId", ListPolicy.text(64),
                    "offeringId", ListPolicy.text(64), "rating", ListPolicy.choices("1", "2", "3", "4", "5"),
                    "publication", ListPolicy.choices("PUBLISHED", "HIDDEN")), Set.of());
    public static final ListPolicy REPORTS = new ListPolicy(Set.of("id", "createdAt"),
            List.of(new SortOrder("createdAt", true)), Map.of("state", ListPolicy.choices("OPEN", "RESOLVED")),
            Set.of());
}
