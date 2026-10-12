package com.carwash.catalog.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.catalog.domain.Service;

public interface ServiceRepository extends Repository<Service, String>, com.carwash.shared.application.PagedRepository<Service>{
    default java.util.List<Service> findByIds(java.util.Collection<String> ids) { return ids.stream().map(this::findById).flatMap(java.util.Optional::stream).toList(); }
}
