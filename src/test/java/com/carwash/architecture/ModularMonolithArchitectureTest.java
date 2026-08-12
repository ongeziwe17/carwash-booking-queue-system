package com.carwash.architecture;


import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModularMonolithArchitectureTest {

    private static final JavaClasses APPLICATION = new ClassFileImporter().importPackages("com.carwash");
    private static final String[] BUSINESS_MODULES = {
            "access", "booking", "catalog", "identity", "notification", "queue", "reporting", "vehicle"
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
        classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage("..api..")
                .check(APPLICATION);
    }

    @Test
    void repository_implementations_are_module_infrastructure() {
        classes().that().haveSimpleNameStartingWith("InMemory")
                .and().haveSimpleNameEndingWith("Repository")
                .and().doNotHaveSimpleName("InMemoryRepository")
                .should().resideInAPackage("..infrastructure..")
                .check(APPLICATION);
    }

    private static String[] businessPackages() {
        return java.util.Arrays.stream(BUSINESS_MODULES)
                .map(module -> "com.carwash." + module + "..")
                .toArray(String[]::new);
    }
}
