package com.carwash.notification.application;

import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;
import com.carwash.identity.domain.User;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.notification.domain.NotificationCursor;
import com.carwash.notification.domain.NotificationSnapshot;
import com.carwash.notification.domain.DeliveryStatus;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;
import com.carwash.access.application.TenantAccessContext;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NotificationManagementService implements BookingNotificationPublisher {

    private static final String DEFAULT_CHANNEL = "IN_APP";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final DataTransactionOperations coordinator;
    private final NotificationIdGenerator notificationIdGenerator;
    private final NotificationPolicyProperties notificationPolicy;
    private final Clock clock;

    public NotificationManagementService(NotificationRepository notificationRepository,
                                         UserRepository userRepository,
                                         BookingRepository bookingRepository,
                                         DataTransactionOperations coordinator,
                                         NotificationIdGenerator notificationIdGenerator,
                                         NotificationPolicyProperties notificationPolicy,
                                         Clock clock) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository,
                "Notification repository is required");
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.notificationIdGenerator = Objects.requireNonNull(notificationIdGenerator,
                "Notification ID generator is required");
        this.notificationPolicy = Objects.requireNonNull(notificationPolicy, "Notification policy is required");
        this.clock = Objects.requireNonNull(clock, "Application clock is required");
    }

    Notification createNotification(User user, Booking booking, String type, String message) {
        return coordinator.write(() -> {
            User canonicalUser = resolveUser(user);
            Booking canonicalBooking = resolveBooking(booking);
            return createInside(canonicalUser, canonicalBooking, type, message);
        });
    }

    @Override
    public Notification publishForAuthorizedBooking(
            Booking authorizedBooking, String type, String message) {
        return coordinator.write(() -> {
            if (authorizedBooking == null) {
                throw new BusinessRuleViolationException("Authorized notification booking is required");
            }
            User canonicalUser = resolveUser(authorizedBooking.getUser());
            return createInside(canonicalUser, authorizedBooking, type, message);
        });
    }

    private Notification createInside(User canonicalUser, Booking canonicalBooking, String type, String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessRuleViolationException("Notification message is required");
        }
        Notification notification = new Notification(notificationIdGenerator.nextId(), canonicalUser,
                canonicalBooking, type, message, DEFAULT_CHANNEL);
        notification.send(LocalDateTime.now(clock));
        if (!notificationRepository.insert(notification)) {
            throw new BusinessRuleViolationException("Notification ID already exists");
        }
        canonicalUser.addNotification(notification);
        if (userRepository != null && !userRepository.update(canonicalUser)) {
            ResourceNotFoundException failure =
                    new ResourceNotFoundException("User not found: " + canonicalUser.getUserId());
            coordinator.compensate(failure, () -> {
                canonicalUser.removeNotification(notification.getNotificationId());
                notificationRepository.deleteById(notification.getNotificationId());
            });
            throw failure;
        }
        return notification;
    }

    public List<Notification> findByUserId(String userId) {
        return coordinator.read(() -> {
            validateUserId(userId);
            return hydrateForUser(userId, notificationRepository.findByUserId(userId));
        });
    }

    List<Notification> findRecentByUserId(String userId) {
        return findRecentByUserId(userId, notificationPolicy.recentLimit());
    }

    /** Scalar replacement used by the backward-compatible recent-list HTTP operation. */
    public List<NotificationSnapshot> findRecentSnapshotsByUserId(
            TenantAccessContext access,
            String userId,
            String administratorBusinessId
    ) {
        Objects.requireNonNull(access, "Tenant access context is required");
        String normalizedUserId = normalizeUserId(userId);
        QueryScope scope = authorizeRead(access, normalizedUserId, administratorBusinessId);
        return coordinator.read(() -> scope.businessId() == null
                ? notificationRepository.findPageByUserId(
                        normalizedUserId, false, null, notificationPolicy.recentLimit())
                : notificationRepository.findPageByUserIdAndBusinessId(
                        normalizedUserId, scope.businessId(), false, null, notificationPolicy.recentLimit()));
    }

    public NotificationInboxPage findInbox(
            TenantAccessContext access,
            String userId,
            String administratorBusinessId,
            boolean unreadOnly,
            String encodedCursor,
            Integer requestedLimit
    ) {
        Objects.requireNonNull(access, "Tenant access context is required");
        String normalizedUserId = normalizeUserId(userId);
        QueryScope scope = authorizeRead(access, normalizedUserId, administratorBusinessId);
        int limit = normalizeInboxLimit(requestedLimit);
        String scopeKey = cursorScope(access, normalizedUserId, scope.businessId(), unreadOnly);
        NotificationCursor cursor = decodeCursor(encodedCursor, scopeKey);
        return coordinator.read(() -> {
            List<NotificationSnapshot> selected = scope.businessId() == null
                    ? notificationRepository.findPageByUserId(
                            normalizedUserId, unreadOnly, cursor, limit + 1)
                    : notificationRepository.findPageByUserIdAndBusinessId(
                            normalizedUserId, scope.businessId(), unreadOnly, cursor, limit + 1);
            long unreadCount = scope.businessId() == null
                    ? notificationRepository.countUnreadByUserId(normalizedUserId)
                    : notificationRepository.countUnreadByUserIdAndBusinessId(
                            normalizedUserId, scope.businessId());
            boolean more = selected.size() > limit;
            List<NotificationSnapshot> notifications = more
                    ? List.copyOf(selected.subList(0, limit)) : List.copyOf(selected);
            String nextCursor = more && !notifications.isEmpty()
                    ? encodeCursor(notifications.getLast(), scopeKey) : null;
            return new NotificationInboxPage(notifications, unreadCount, nextCursor);
        });
    }

    public NotificationSnapshot markAsRead(TenantAccessContext access, String notificationId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        String normalizedNotificationId = normalizeNotificationId(notificationId);
        return coordinator.write(() -> {
            NotificationSnapshot result = notificationRepository.markAsReadByUserId(
                            normalizedNotificationId, access.userId(), LocalDateTime.now(clock))
                    .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
            if (result.deliveryStatus() != DeliveryStatus.READ || result.readAt() == null) {
                throw new BusinessRuleViolationException("Notification cannot be marked as read");
            }
            return result;
        });
    }

    public MarkAllNotificationsReadResult markAllAsRead(
            TenantAccessContext access, String requestedUserId) {
        Objects.requireNonNull(access, "Tenant access context is required");
        String normalizedUserId = normalizeUserId(requestedUserId);
        if (!access.userId().equals(normalizedUserId)) {
            throw new AccessDeniedException("Notification read state is recipient self-service only");
        }
        return coordinator.write(() -> {
            LocalDateTime readAt = LocalDateTime.now(clock);
            int affected = notificationRepository.markAllAsReadByUserId(normalizedUserId, readAt);
            return new MarkAllNotificationsReadResult(affected, readAt);
        });
    }

    List<Notification> findRecentByUserId(String userId, int limit) {
        return coordinator.read(() -> {
            validateUserId(userId);
            if (limit <= 0) throw new BusinessRuleViolationException("Notification limit must be positive");
            return hydrateForUser(userId, notificationRepository.findByUserId(userId)).stream()
                    .sorted(Comparator.comparing(Notification::getSentAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Notification::getNotificationId,
                                    Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(limit).toList();
        });
    }

    private User resolveUser(User user) {
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw new BusinessRuleViolationException("Notification user is required");
        }
        if (userRepository == null) return user;
        return userRepository.findById(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + user.getUserId()));
    }

    private Booking resolveBooking(Booking booking) {
        if (booking == null || bookingRepository == null) return booking;
        if (booking.getBookingId() == null || booking.getBookingId().isBlank()) {
            throw new BusinessRuleViolationException("Notification booking ID is required");
        }
        return bookingRepository.findById(booking.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + booking.getBookingId()));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessRuleViolationException("User ID is required");
        }
    }

    private String normalizeUserId(String userId) {
        validateUserId(userId);
        String normalized = userId.trim();
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException("User ID must not exceed 64 characters");
        }
        return normalized;
    }

    private String normalizeNotificationId(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new BusinessRuleViolationException("Notification ID is required");
        }
        String normalized = notificationId.trim();
        if (normalized.length() > 128) {
            throw new BusinessRuleViolationException("Notification ID must not exceed 128 characters");
        }
        return normalized;
    }

    private QueryScope authorizeRead(
            TenantAccessContext access, String userId, String administratorBusinessId) {
        if (access.isCustomer()) {
            if (!access.userId().equals(userId)) {
                throw new AccessDeniedException("Notification self-service access is required");
            }
            if (administratorBusinessId != null) {
                throw new AccessDeniedException("Customers cannot select notification tenant scope");
            }
            return new QueryScope(null);
        }
        if (access.isOperational()) {
            if (administratorBusinessId != null) {
                throw new AccessDeniedException("Notification tenant scope is derived from authentication");
            }
            return new QueryScope(access.requireBusinessId());
        }
        access.requirePlatformAdministrator();
        return new QueryScope(normalizeBusinessId(administratorBusinessId));
    }

    private int normalizeInboxLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? notificationPolicy.inboxDefaultPageSize() : requestedLimit;
        if (limit < 1 || limit > notificationPolicy.inboxMaximumPageSize()) {
            throw new BusinessRuleViolationException("Notification inbox limit is outside the configured bounds");
        }
        return limit;
    }

    private static String cursorScope(
            TenantAccessContext access, String userId, String businessId, boolean unreadOnly) {
        return digest(String.join("\n", access.userId(), access.canonicalRoleName(),
                Objects.toString(access.businessId(), ""), userId,
                Objects.toString(businessId, ""), Boolean.toString(unreadOnly)));
    }

    private static String encodeCursor(NotificationSnapshot notification, String scopeKey) {
        String encodedId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                notification.notificationId().getBytes(StandardCharsets.UTF_8));
        String value = "1\n" + scopeKey + "\n" + notification.sentAt() + "\n" + encodedId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static NotificationCursor decodeCursor(String cursor, String expectedScope) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 512) {
            throw new BusinessRuleViolationException("Notification cursor is invalid");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", -1);
            if (parts.length != 4 || !"1".equals(parts[0]) || !expectedScope.equals(parts[1])) {
                throw new IllegalArgumentException();
            }
            String notificationId = new String(
                    Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8);
            if (notificationId.isBlank() || notificationId.length() > 128) {
                throw new IllegalArgumentException();
            }
            return new NotificationCursor(LocalDateTime.parse(parts[2]), notificationId);
        } catch (RuntimeException invalid) {
            throw new BusinessRuleViolationException("Notification cursor is invalid");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private List<Notification> hydrateForUser(String userId, List<Notification> source) {
        if (source.isEmpty()) return List.of();
        User user = userRepository == null
                ? source.getFirst().getUser()
                : userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Map<String, Booking> bookings = bookingRepository == null
                ? Map.of()
                : bookingRepository.findByUserId(userId).stream().collect(Collectors.toMap(
                        Booking::getBookingId, Function.identity()));
        return source.stream().map(notification -> {
            Booking booking = notification.getBooking() == null
                    ? null
                    : bookingRepository == null
                    ? notification.getBooking()
                    : Objects.requireNonNull(bookings.get(notification.getBooking().getBookingId()),
                    "Notification booking is missing");
            return hydrate(notification, user, booking);
        }).toList();
    }

    private String normalizeBusinessId(String businessId) {
        String normalized = businessId == null ? null : businessId.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessRuleViolationException(
                    "businessId is required for platform administrator notification access");
        }
        if (normalized.length() > 64) {
            throw new BusinessRuleViolationException("Business ID must not exceed 64 characters");
        }
        return normalized;
    }

    private record QueryScope(String businessId) { }

    private Notification hydrate(Notification source, User user, Booking booking) {
        Notification value = new Notification();
        value.setNotificationId(source.getNotificationId());
        value.setUser(user);
        value.setBooking(booking);
        value.setBranchId(source.getBranchId());
        value.setServiceOfferingId(source.getServiceOfferingId());
        value.setType(source.getType());
        value.setMessage(source.getMessage());
        value.setChannel(source.getChannel());
        value.setSentAt(source.getSentAt());
        value.setReadAt(source.getReadAt());
        value.setDeliveryStatus(source.getDeliveryStatus());
        return value;
    }
}
