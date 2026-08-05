package com.carwash.repository;

import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {
    boolean insert(T entity);
    boolean update(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean deleteById(ID id);
    boolean existsById(ID id);
}
