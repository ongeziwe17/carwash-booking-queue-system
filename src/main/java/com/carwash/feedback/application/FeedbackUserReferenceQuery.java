package com.carwash.feedback.application;

/** Published feedback contract for account deletion. Includes historical moderation and reports. */
@FunctionalInterface
public interface FeedbackUserReferenceQuery {
    boolean referencesUser(String userId);
}
