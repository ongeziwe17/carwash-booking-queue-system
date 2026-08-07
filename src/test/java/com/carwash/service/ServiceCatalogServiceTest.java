package com.carwash.service;

import com.carwash.domain.Service;
import com.carwash.service.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
}
