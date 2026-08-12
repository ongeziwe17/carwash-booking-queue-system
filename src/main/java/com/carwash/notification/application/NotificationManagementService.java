package com.carwash.notification.application;

import com.carwash.notification.application.NotificationPolicyProperties;
import com.carwash.booking.domain.Booking;
import com.carwash.notification.domain.Notification;
import com.carwash.identity.domain.User;
import com.carwash.booking.domain.BookingRepository;
import com.carwash.notification.domain.NotificationRepository;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.shared.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class NotificationManagementService {

    private static final String DEFAULT_CHANNEL = "IN_APP";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final InMemoryDataCoordinator coordinator;
    private final NotificationIdGenerator notificationIdGenerator;
    private final NotificationPolicyProperties notificationPolicy;
    private final Clock clock;

    public NotificationManagementService(NotificationRepository notificationRepository,
                                         UserRepository userRepository,
                                         BookingRepository bookingRepository,
                                         InMemoryDataCoordinator coordinator,
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

    public Notification createNotification(User user, Booking booking, String type, String message) {
        return coordinator.write(() -> {
            User canonicalUser = resolveUser(user);
            Booking canonicalBooking = resolveBooking(booking);
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
                canonicalUser.removeNotification(notification.getNotificationId());
                notificationRepository.deleteById(notification.getNotificationId());
                throw new ResourceNotFoundException("User not found: " + canonicalUser.getUserId());
            }
            return notification;
        });
    }

    public List<Notification> findByUserId(String userId) {
        return coordinator.read(() -> {
            validateUserId(userId);
            return notificationRepository.findByUserId(userId);
        });
    }

    public List<Notification> findRecentByUserId(String userId) {
        return findRecentByUserId(userId, notificationPolicy.recentLimit());
    }

    public List<Notification> findRecentByUserId(String userId, int limit) {
        return coordinator.read(() -> {
            validateUserId(userId);
            if (limit <= 0) throw new BusinessRuleViolationException("Notification limit must be positive");
            return notificationRepository.findByUserId(userId).stream()
                    .sorted(Comparator.comparing(Notification::getSentAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Notification::getNotificationId,
                                    Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(limit).toList();
        });
    }

    public Notification markAsRead(String notificationId) {
        return coordinator.write(() -> {
            if (notificationId == null || notificationId.isBlank()) {
                throw new BusinessRuleViolationException("Notification ID is required");
            }
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
            notification.markAsRead(LocalDateTime.now(clock));
            if (!notificationRepository.update(notification)) {
                throw new ResourceNotFoundException("Notification not found: " + notificationId);
            }
            return notification;
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
}
