package com.carwash.architecture;

import com.carwash.shared.domain.Repository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModularMonolithArchitectureTest {

    private static final JavaClasses APPLICATION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.carwash");
    private static final String[] BUSINESS_MODULES = {
            "access", "booking", "catalog", "discovery", "identity", "marketplace", "notification", "queue", "reporting",
            "recommendation", "vehicle"
    };

    @Test
    void domain_is_independent_of_api_infrastructure_and_bootstrap() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..", "..bootstrap..")
                .check(APPLICATION);
    }

    @Test
    void domain_and_application_layers_remain_persistence_framework_agnostic() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.springframework.data..", "org.hibernate..")
                .check(APPLICATION);
    }

    @Test
    void jpa_types_are_owned_by_module_infrastructure() {
        noClasses().that().resideOutsideOfPackages(repositoryImplementationPackages())
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                .check(APPLICATION);
    }

    @Test
    void business_modules_do_not_reach_into_foreign_infrastructure() {
        for (String owner : BUSINESS_MODULES) {
            String ownerPackage = "com.carwash." + owner + "..";
            noClasses().that().resideOutsideOfPackage(ownerPackage)
                    .and().resideOutsideOfPackage("com.carwash.bootstrap..")
                    .should().dependOnClassesThat().resideInAPackage("com.carwash." + owner + ".infrastructure..")
                    .check(APPLICATION);
        }
    }

    @Test
    void shared_does_not_depend_on_business_modules() {
        noClasses().that().resideInAPackage("com.carwash.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(businessPackages())
                .check(APPLICATION);
    }

    @Test
    void modules_do_not_depend_on_bootstrap() {
        noClasses().that().resideInAnyPackage(businessPackages())
                .should().dependOnClassesThat().resideInAPackage("com.carwash.bootstrap..")
                .check(APPLICATION);
    }

    @Test
    void controllers_are_owned_by_module_api_packages() {
        classes().that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().resideInAnyPackage(moduleApiPackages())
                .check(APPLICATION);
    }

    @Test
    void repository_implementations_are_module_infrastructure() {
        classes().that().areAssignableTo(Repository.class)
                .and().areNotInterfaces()
                .should().resideInAnyPackage(repositoryImplementationPackages())
                .check(APPLICATION);
    }

    @Test
    void legacy_global_technical_packages_are_empty() {
        noClasses().should().resideInAnyPackage(
                        "com.carwash.api..",
                        "com.carwash.config..",
                        "com.carwash.domain..",
                        "com.carwash.enums..",
                        "com.carwash.repository..",
                        "com.carwash.security..",
                        "com.carwash.service.."
                )
                .check(APPLICATION);
    }

    @Test
    void marketplace_isolated_foundation_does_not_depend_on_existing_business_capabilities() {
        noClasses().that().resideInAPackage("com.carwash.marketplace..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.access.api..",
                        "com.carwash.access.infrastructure..",
                        "com.carwash.booking..",
                        "com.carwash.catalog..",
                        "com.carwash.identity..",
                        "com.carwash.notification..",
                        "com.carwash.queue..",
                        "com.carwash.reporting..",
                        "com.carwash.vehicle.."
                )
                .check(APPLICATION);
    }

    @Test
    void catalog_domain_stays_independent_of_marketplace() {
        noClasses().that().resideInAPackage("com.carwash.catalog.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.carwash.marketplace..")
                .check(APPLICATION);
    }

    @Test
    void catalog_uses_only_the_published_marketplace_application_contract() {
        noClasses().that().resideInAPackage("com.carwash.catalog..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.marketplace.api..",
                        "com.carwash.marketplace.domain..",
                        "com.carwash.marketplace.infrastructure.."
                )
                .check(APPLICATION);
    }

    @Test
    void operational_modules_use_published_marketplace_and_catalog_contracts() {
        noClasses().that().resideInAnyPackage(
                        "com.carwash.booking.application..",
                        "com.carwash.queue.application..",
                        "com.carwash.reporting.application.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.marketplace.api..",
                        "com.carwash.marketplace.domain..",
                        "com.carwash.marketplace.infrastructure..",
                        "com.carwash.catalog.api..",
                        "com.carwash.catalog.infrastructure.."
                )
                .check(APPLICATION);
    }

    @Test
    void catalog_no_longer_reaches_into_booking_or_queue_modules() {
        noClasses().that().resideInAPackage("com.carwash.catalog..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.booking..",
                        "com.carwash.queue.."
                )
                .check(APPLICATION);
    }

    @Test
    void discovery_orchestrates_only_through_published_marketplace_and_catalog_contracts() {
        noClasses().that().resideInAPackage("com.carwash.discovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.marketplace.api..",
                        "com.carwash.marketplace.domain..",
                        "com.carwash.marketplace.infrastructure..",
                        "com.carwash.catalog.api..",
                        "com.carwash.catalog.domain..",
                        "com.carwash.catalog.infrastructure.."
                )
                .check(APPLICATION);
    }

    @Test
    void marketplace_and_catalog_do_not_depend_on_discovery() {
        noClasses().that().resideInAnyPackage("com.carwash.marketplace..", "com.carwash.catalog..")
                .should().dependOnClassesThat().resideInAPackage("com.carwash.discovery..")
                .check(APPLICATION);
    }

    @Test
    void availability_uses_only_the_discovery_port_without_a_reverse_dependency() {
        noClasses().that().resideInAPackage("com.carwash.booking.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.discovery.api..",
                        "com.carwash.discovery.infrastructure.."
                )
                .check(APPLICATION);
        noClasses().that().resideInAPackage("com.carwash.discovery..")
                .should().dependOnClassesThat().resideInAPackage("com.carwash.booking..")
                .check(APPLICATION);
    }

    @Test
    void recommendation_consumes_only_published_application_contracts() {
        noClasses().that().resideInAPackage("com.carwash.recommendation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carwash.booking.api..",
                        "com.carwash.booking.domain..",
                        "com.carwash.booking.infrastructure..",
                        "com.carwash.catalog.api..",
                        "com.carwash.catalog.domain..",
                        "com.carwash.catalog.infrastructure..",
                        "com.carwash.discovery.api..",
                        "com.carwash.discovery.domain..",
                        "com.carwash.discovery.infrastructure..",
                        "com.carwash.marketplace.api..",
                        "com.carwash.marketplace.domain..",
                        "com.carwash.marketplace.infrastructure..",
                        "com.carwash.queue.api..",
                        "com.carwash.queue.domain..",
                        "com.carwash.queue.infrastructure.."
                )
                .check(APPLICATION);
    }

    @Test
    void candidate_providers_do_not_depend_on_recommendation() {
        noClasses().that().resideInAnyPackage(
                        "com.carwash.booking..",
                        "com.carwash.catalog..",
                        "com.carwash.discovery..",
                        "com.carwash.marketplace..",
                        "com.carwash.queue.."
                )
                .should().dependOnClassesThat().resideInAPackage("com.carwash.recommendation..")
                .check(APPLICATION);
    }

    private static String[] businessPackages() {
        return java.util.Arrays.stream(BUSINESS_MODULES)
                .map(module -> "com.carwash." + module + "..")
                .toArray(String[]::new);
    }

    private static String[] moduleApiPackages() {
        return java.util.Arrays.stream(BUSINESS_MODULES)
                .map(module -> "com.carwash." + module + ".api..")
                .toArray(String[]::new);
    }

    private static String[] repositoryImplementationPackages() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("com.carwash.shared.infrastructure.."),
                        java.util.Arrays.stream(BUSINESS_MODULES)
                                .map(module -> "com.carwash." + module + ".infrastructure..")
                )
                .toArray(String[]::new);
    }
}
