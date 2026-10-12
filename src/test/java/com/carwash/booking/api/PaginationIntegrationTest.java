package com.carwash.booking.api;

import com.carwash.testsupport.*;
import com.carwash.booking.api.dto.CreateBookingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "spring.application.name=pagination-api-isolation")
class PaginationIntegrationTest extends ApiIntegrationTestSupport {
    @org.springframework.beans.factory.annotation.Autowired com.carwash.identity.application.UserManagementService userManagement;
    @org.springframework.beans.factory.annotation.Autowired com.carwash.vehicle.application.VehicleManagementService vehicleManagement;
    @org.springframework.beans.factory.annotation.Autowired com.carwash.catalog.application.ServiceCatalogService catalog;
    @org.springframework.beans.factory.annotation.Autowired com.carwash.identity.domain.UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired com.carwash.vehicle.domain.VehicleRepository vehicles;
    @org.springframework.beans.factory.annotation.Autowired com.carwash.catalog.domain.ServiceRepository services;
    @org.springframework.beans.factory.annotation.Autowired com.carwash.shared.application.DataTransactionOperations transactions;

    @Test void listProjectionsStayDetachedAfterTheReadSnapshotIsReleased() throws Exception {
        var f=new BookingApiFixture(api,ids).createBooking(LocalDateTime.of(2089,1,15,10,0));
        var r=f.resources();
        var admin=new com.carwash.access.application.TenantAccessContext("snapshot-reader",com.carwash.identity.domain.RoleName.PLATFORM_ADMIN,null);
        var userQuery=com.carwash.identity.application.UserListPolicy.POLICY.parse(java.util.Map.of("id",java.util.List.of(r.user().userId())));
        var vehicleQuery=com.carwash.vehicle.application.VehicleListPolicy.POLICY.parse(java.util.Map.of("id",java.util.List.of(r.vehicle().vehicleId())));
        var serviceQuery=com.carwash.catalog.application.ServiceListPolicy.POLICY.parse(java.util.Map.of("id",java.util.List.of(r.service().serviceId())));
        var user=userManagement.findPage(admin,userQuery).content().getFirst();
        var vehicle=vehicleManagement.findPage(admin,r.business().businessId(),vehicleQuery).content().getFirst();
        var service=catalog.findPage(serviceQuery).content().getFirst();
        transactions.write(() -> {
            var changedUser=users.findById(user.userId()).orElseThrow(); changedUser.setFullName("Changed profile");
            var changedVehicle=vehicles.findById(vehicle.vehicleId()).orElseThrow(); changedVehicle.setBrand("Changed brand");
            var changedService=services.findById(service.serviceId()).orElseThrow(); changedService.setServiceName("Changed service");
            users.update(changedUser);vehicles.update(changedVehicle);services.update(changedService);
        });
        org.junit.jupiter.api.Assertions.assertNotEquals("Changed profile",user.fullName());
        org.junit.jupiter.api.Assertions.assertNotEquals("Changed brand",vehicle.brand());
        org.junit.jupiter.api.Assertions.assertNotEquals("Changed service",service.serviceName());
        assertEquals("Changed profile",userManagement.findPage(admin,userQuery).content().getFirst().fullName());
        assertEquals("Changed brand",vehicleManagement.findPage(admin,r.business().businessId(),vehicleQuery).content().getFirst().brand());
        assertEquals("Changed service",catalog.findPage(serviceQuery).content().getFirst().serviceName());
    }

    @Test void allOrdinaryResourcesUseDefaultPagesExactFiltersAndRejectInvalidQueries() throws Exception {
        var f = new BookingApiFixture(api, ids).createResources();
        var paths = java.util.Map.of(
                "/api/users", f.user().userId(), "/api/vehicles", f.vehicle().vehicleId(), "/api/services", f.service().serviceId(),
                "/api/marketplace/businesses", f.business().businessId(),
                "/api/marketplace/businesses/" + f.business().businessId() + "/branches", f.branch().branchId(),
                "/api/marketplace/branches/discoverable", f.branch().branchId(),
                "/api/marketplace/branches/" + f.branch().branchId() + "/offerings", f.offering().offeringId(),
                "/api/marketplace/branches/" + f.branch().branchId() + "/offerings/discoverable", f.offering().offeringId());
        for(var entry:paths.entrySet()) {
            var request=get(entry.getKey()).with(authentication.platformAdminJwt()).param("id",entry.getValue());
            if(entry.getKey().equals("/api/vehicles")) request.param("businessId",f.business().businessId());
            // A vehicle becomes visible to a business only through its booking below; initially count is zero.
            int total=entry.getKey().equals("/api/vehicles")?0:1;
            mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(total));
            var invalid=get(entry.getKey()).with(authentication.platformAdminJwt()).param("size","101");
            if(entry.getKey().equals("/api/vehicles")) invalid.param("businessId",f.business().businessId());
            mockMvc.perform(invalid).andExpect(status().isBadRequest());
        }
        String path="/api/marketplace/branches/"+f.branch().branchId()+"/closures";
        mockMvc.perform(get(path).with(authentication.platformAdminJwt()).param("page","12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.page").value(12));
        for(var pair:java.util.List.of(new String[]{"status","UNKNOWN"},new String[]{"from","2026-10-12"},new String[]{"sort","privateField,asc"}))
            mockMvc.perform(get(path).with(authentication.platformAdminJwt()).param(pair[0],pair[1])).andExpect(status().isBadRequest());
        mockMvc.perform(get(path).with(authentication.platformAdminJwt()).param("from","2089-01-18T00:00:00Z").param("to","2089-01-17T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test void serviceSortTiesAndFilterCombinationsKeepStableIdsAndFilteredTotals() throws Exception {
        for(int i=0;i<23;i++) api.createService(new com.carwash.catalog.api.dto.CreateServiceRequest(
                "page-service-"+String.format("%02d",i),"Identical name","Description",new java.math.BigDecimal("12.34"),20))
                .andExpect(status().isCreated());
        for(String direction:java.util.List.of("asc","desc")) for(int page=0;page<5;page++) {
            var result=mockMvc.perform(get("/api/services").with(authentication.platformAdminJwt()).param("name","Identical name").param("active","true")
                            .param("sort","price,asc","id,"+direction).param("page",String.valueOf(page)).param("size","7"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(23)).andReturn();
            var content=objectMapper.readTree(result.getResponse().getContentAsString()).path("content");
            for(int i=0;i<content.size();i++) assertEquals("page-service-"+String.format("%02d",direction.equals("asc")?page*7+i:22-page*7-i),content.get(i).path("serviceId").asText());
        }
        mockMvc.perform(get("/api/services").with(authentication.platformAdminJwt()).param("name","Identical name").param("active","false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void bookingPagesHaveAuthorizedTotalsFiltersRangesAndStableOutOfRangeMetadata() throws Exception {
        var a = new BookingApiFixture(api, ids).createResources();
        var other = new ApiTestClient(mockMvc,objectMapper,authentication);
        var b = new BookingApiFixture(other, ids).createResources();
        var at = LocalDateTime.of(2089,1,15,10,0,0,0);
        for (int i=0;i<21;i++) api.createBooking(new CreateBookingRequest(ids.booking(),a.user().userId(),a.vehicle().vehicleId(),
                a.branch().branchId(),a.offering().offeringId(),at.plusDays(i),null)).andExpect(status().isCreated());
        other.createBooking(new CreateBookingRequest(ids.booking(),b.user().userId(),b.vehicle().vehicleId(),b.branch().branchId(),
                b.offering().offeringId(),at,null)).andExpect(status().isCreated());
        for (int page=0;page<4;page++) mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt())
                        .param("businessId",a.business().businessId()).param("size","10").param("page",String.valueOf(page))
                        .param("sort","scheduledAt,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(page<2?10:page==2?1:0))
                .andExpect(jsonPath("$.hasNext").value(page<2)).andExpect(jsonPath("$.hasPrevious").value(page>0));
        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt()).param("businessId",a.business().businessId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(20)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].user").doesNotExist()).andExpect(jsonPath("$.content[0].vehicle").doesNotExist());
        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt()).param("businessId",a.business().businessId())
                        .param("from",at.toString()).param("to",at.toString()).param("status","CREATED").param("userId",a.user().userId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt()).param("businessId",a.business().businessId())
                        .param("from",at.plusNanos(1).toString()).param("to",at.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt()).param("businessId",a.business().businessId())
                        .param("userId",b.user().userId()).param("page","5000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(5000)).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/bookings").with(authentication.platformAdminJwt()).param("businessId",a.business().businessId())
                        .param("branchId",b.branch().branchId()).param("page","5000"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/bookings").with(authentication.customerJwt(a.user().userId())).param("businessId",a.business().businessId()))
                .andExpect(status().isForbidden());
    }
    @Test void invalidQueryParametersUseTheExistingApiErrorContract() throws Exception {
        for (String[] input : new String[][]{{"page","-1"},{"page","2147483648"},{"page","999999999999999999999999999"},
                {"size","0"},{"size","-20"},{"size","101"},{"size","one"},{"sort","email,asc"},{"sort","id,up"},
                {"accountStatus","unknown"},{"all","true"}})
            mockMvc.perform(get("/api/users").with(authentication.platformAdminJwt()).param(input[0],input[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.fieldErrors").isArray());
        mockMvc.perform(get("/api/users").with(authentication.platformAdminJwt()).param("page","2147483647").param("size","100"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/services").with(authentication.platformAdminJwt()).param("active","maybe"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/services").with(authentication.platformAdminJwt()).param("size","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }
}
