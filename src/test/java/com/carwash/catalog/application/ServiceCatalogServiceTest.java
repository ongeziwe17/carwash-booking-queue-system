package com.carwash.catalog.application;

import com.carwash.testsupport.ServiceTestSupport;

import com.carwash.booking.domain.Booking;
import com.carwash.queue.domain.QueueEntry;
import com.carwash.catalog.domain.Service;
import com.carwash.catalog.domain.ServiceOffering;
import com.carwash.catalog.domain.ServiceOfferingStatus;
import com.carwash.shared.exception.BusinessRuleViolationException;
import com.carwash.testsupport.TestDates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceCatalogServiceTest extends ServiceTestSupport {

    @Test
    void serviceCreationSucceeds() {
        String serviceId = ids.service();
        Service service = new Service(serviceId, "Premium Wash", "desc", BigDecimal.TEN, 30);
        assertEquals(serviceId, catalogService.createService(service).getServiceId());
    }

    @Test
    void serviceCreationFailsWithInvalidPrice() {
        Service service = new Service(ids.service(), "Premium Wash", "desc", BigDecimal.valueOf(-1), 30);
        assertThrows(BusinessRuleViolationException.class, () -> catalogService.createService(service));
    }

    @Test
    void serviceCreationFailsWithInvalidDuration() {
        Service service = new Service(ids.service(), "Premium Wash", "desc", BigDecimal.TEN, 0);
        assertThrows(BusinessRuleViolationException.class, () -> catalogService.createService(service));
    }

    @Test
    void deactivateServiceSucceeds() {
        Service service = createService();
        assertFalse(catalogService.deactivateService(service.getServiceId()).isActive());
    }

    @Test
    void serviceReferencedByInactiveBranchOfferingCannotBeDeleted() {
        Service service = createService();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        ServiceOffering inactiveOffering = new ServiceOffering(
                "offering-001", "branch-001", service.getServiceId(), BigDecimal.TEN, 30, 2,
                ServiceOfferingStatus.INACTIVE, now, now);
        assertTrue(serviceOfferingRepository.insert(inactiveOffering));

        BusinessRuleViolationException failure = assertThrows(
                BusinessRuleViolationException.class,
                () -> catalogService.deleteService(service.getServiceId()));

        assertEquals("Service referenced by a branch offering cannot be deleted; deactivate it instead",
                failure.getMessage());
        assertTrue(serviceRepository.findById(service.getServiceId()).isPresent());
    }

    @Test
    void findAllServicesReturnsUnfilteredCatalog() {
        Service active = createService();
        Service inactive = new Service(ids.service(), "Basic Wash", "desc", BigDecimal.ONE, 15);
        inactive.deactivate();
        catalogService.createService(inactive);
        List<Service> services = catalogService.findAll();
        assertEquals(2, services.size());
        assertTrue(services.stream().anyMatch(item -> item.getServiceId().equals(active.getServiceId())));
        assertTrue(services.stream().anyMatch(item -> item.getServiceId().equals(inactive.getServiceId())));
    }

    @Test
    void findByActiveReturnsOnlyActiveServices() {
        Service active = createService();
        Service inactive = new Service(ids.service(), "Basic Wash", "desc", BigDecimal.ONE, 15);
        inactive.deactivate();
        catalogService.createService(inactive);
        List<Service> services = catalogService.findByActive(true);
        assertEquals(1, services.size());
        assertEquals(active.getServiceId(), services.getFirst().getServiceId());
    }

    @Test
    void findByActiveReturnsOnlyInactiveServices() {
        createService();
        Service inactive = new Service(ids.service(), "Basic Wash", "desc", BigDecimal.ONE, 15);
        inactive.deactivate();
        catalogService.createService(inactive);
        List<Service> services = catalogService.findByActive(false);
        assertEquals(1, services.size());
        assertEquals(inactive.getServiceId(), services.getFirst().getServiceId());
    }

    @Test
    void findByActiveReturnsEmptyListWhenNoServicesMatch() {
        createService();
        assertTrue(catalogService.findByActive(false).isEmpty());
    }

    @Test
    void legacyGlobalDurationUpdateDoesNotReplaceOfferingDurationForQueueWaits() {
        Booking firstBooking = createConfirmedBooking(TestDates.futureDays(1));
        Service firstService = firstBooking.getService();
        catalogService.updateService(firstService.getServiceId(), firstService.getServiceName(),
                firstService.getDescription(), firstService.getPrice(), 10);
        Booking secondBooking = createConfirmedBooking(TestDates.futureDays(2));
        QueueEntry firstResponse = queueService.createQueueEntry(
                ids.queueEntry(), firstBooking.getBookingId(), firstService.getServiceId());
        QueueEntry secondResponse = queueService.createQueueEntry(
                ids.queueEntry(), secondBooking.getBookingId(), secondBooking.getService().getServiceId());
        QueueEntry first = queueRepository.findById(firstResponse.getQueueEntryId()).orElseThrow();
        QueueEntry second = queueRepository.findById(secondResponse.getQueueEntryId()).orElseThrow();
        assertEquals(30, second.getEstimatedWaitMin());

        catalogService.updateService(firstService.getServiceId(), firstService.getServiceName(),
                firstService.getDescription(), firstService.getPrice(), 40);

        assertEquals(0, first.getEstimatedWaitMin());
        assertEquals(30, second.getEstimatedWaitMin());
        assertEquals(30, queueRepository.findById(second.getQueueEntryId()).orElseThrow().getEstimatedWaitMin());
    }
}
