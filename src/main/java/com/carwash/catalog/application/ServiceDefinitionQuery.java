package com.carwash.catalog.application;

import java.util.Optional;

/** Narrow Catalog contract for resolving reusable service definitions. */
public interface ServiceDefinitionQuery {

    Optional<ServiceDefinitionSnapshot> findServiceDefinitionOptional(String serviceId);
}
