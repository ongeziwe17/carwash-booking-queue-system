package com.carwash.service;

import com.carwash.domain.Booking;
import com.carwash.domain.Service;
import com.carwash.domain.User;
import com.carwash.domain.Vehicle;
import com.carwash.enums.BookingStatus;
import com.carwash.repository.BookingRepository;
import com.carwash.repository.ServiceRepository;
import com.carwash.repository.UserRepository;
import com.carwash.repository.VehicleRepository;
import com.carwash.service.exception.BusinessRuleViolationException;
import com.carwash.service.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;

public class BookingManagementService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationManagementService notificationManagementService;


    public BookingManagementService(BookingRepository bookingRepository, UserRepository userRepository, VehicleRepository vehicleRepository, ServiceRepository serviceRepository) {
        this(bookingRepository, userRepository, vehicleRepository, serviceRepository, null);
    }

    public BookingManagementService(BookingRepository bookingRepository, UserRepository userRepository, VehicleRepository vehicleRepository, ServiceRepository serviceRepository, NotificationManagementService notificationManagementService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceRepository = serviceRepository;
        this.notificationManagementService = notificationManagementService;
    }

    public Booking createBooking(Booking booking) {
        validateBooking(booking);
        bookingRepository.save(booking);
        return booking;
    }

    public Booking findById(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    public List<Booking> findAll() { return bookingRepository.findAll(); }

    public Booking updateBooking(Booking booking) {
        Booking existing = findById(booking.getBookingId());
        validateBooking(booking);
        existing.setUser(booking.getUser());
        existing.setVehicle(booking.getVehicle());
        existing.setService(booking.getService());
        existing.setScheduledDateTime(booking.getScheduledDateTime());
        existing.setSpecialRequest(booking.getSpecialRequest());
        bookingRepository.save(existing);
        return existing;
    }

    public Booking cancelBooking(String bookingId, String customerId) {
        Booking booking = findById(bookingId);
        validateCancellationRequest(booking, customerId);
        if (!booking.cancel()) throw new BusinessRuleViolationException("Invalid booking status transition");
        bookingRepository.save(booking);
        notifyCustomer(booking, "BOOKING_CANCELLED", "Your booking has been cancelled.");
        return booking;
    }

    private void validateCancellationRequest(Booking booking, String customerId){
        if (customerId == null || customerId.isBlank()) throw new BusinessRuleViolationException("Customer ID is required to cancel booking");
        if (booking.getUser() == null || booking.getUser().getUserId() == null || !booking.getUser().getUserId().equals(customerId)) {
            throw new BusinessRuleViolationException("Booking can only be cancelled by the owning customer");
        }
        if (booking.getScheduledDateTime() == null || booking.getScheduledDateTime().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("Only future bookings can be cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) throw new BusinessRuleViolationException("Completed booking cannot be cancelled");
        if (booking.getStatus() == BookingStatus.CANCELLED) throw new BusinessRuleViolationException("Cancelled booking cannot be cancelled again");
    }

    public Booking confirmBooking(String bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) throw new BusinessRuleViolationException("Cancelled booking cannot be confirmed");
        if (!booking.confirm()) throw new BusinessRuleViolationException("Invalid booking status transition");
        bookingRepository.save(booking);
        notifyCustomer(booking, "BOOKING_CONFIRMED", "Your booking has been confirmed.");
        return booking;
    }

    private void notifyCustomer(Booking booking, String type, String message) {
        if (notificationManagementService != null) {
            notificationManagementService.createNotification(booking.getUser(), booking, type, message);
        }
    }

    private void validateBooking(Booking booking) {
        if (booking == null) throw new BusinessRuleViolationException("Booking is required");
        User user = userRepository.findById(booking.getUser().getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + booking.getUser().getUserId()));
        Vehicle vehicle = vehicleRepository.findById(booking.getVehicle().getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + booking.getVehicle().getVehicleId()));
        Service service = serviceRepository.findById(booking.getService().getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + booking.getService().getServiceId()));
        if (!service.isActive()) throw new BusinessRuleViolationException("Inactive service cannot be booked");
        if (vehicle.getUserId() == null || !vehicle.getUserId().equals(user.getUserId())) {
            throw new BusinessRuleViolationException("Vehicle does not belong to selected user");
        }
        if (booking.getScheduledDateTime() == null || booking.getScheduledDateTime().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("Scheduled date/time cannot be in the past");
        }
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setService(service);
    }
}
