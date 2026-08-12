package com.carwash.notification.application;

/**
 * Generates server-owned notification identifiers.
 *
 * <p>Clients never supply notification IDs. Implementations must produce IDs
 * that are safe for repository keys and API responses.</p>
 */
public interface NotificationIdGenerator {

    String nextId();
}
