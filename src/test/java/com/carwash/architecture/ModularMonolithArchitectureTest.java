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
            "access", "booking", "catalog", "identity", "marketplace", "notification", "queue", "reporting",
            "vehicle"
    };

    @Test
    void domain_is_independent_of_api_infrastructure_and_bootstrap() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..", "..bootstrap..")
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
