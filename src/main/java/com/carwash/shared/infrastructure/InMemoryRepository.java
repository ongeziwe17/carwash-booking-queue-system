package com.carwash.shared.infrastructure;

import com.carwash.shared.domain.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

public abstract class InMemoryRepository<T, ID extends Comparable<? super ID>> implements Repository<T, ID> {

    private final ConcurrentMap<ID, T> storage = new ConcurrentHashMap<>();
    private final Object repositoryMonitor = new Object();

    protected abstract ID getId(T entity);

    @Override
    public boolean insert(T entity) {
        ID id = requireEntityId(entity);
        synchronized (repositoryMonitor) {
            return storage.putIfAbsent(id, entity) == null;
        }
    }

    @Override
    public boolean update(T entity) {
        ID id = requireEntityId(entity);
        synchronized (repositoryMonitor) {
            return storage.replace(id, entity) != null;
        }
    }

    @Override
    public Optional<T> findById(ID id) {
        requireId(id);
        synchronized (repositoryMonitor) {
            return Optional.ofNullable(storage.get(id));
        }
    }

    @Override
    public List<T> findAll() {
        return findMatching(entity -> true);
    }

    @Override
    public boolean deleteById(ID id) {
        requireId(id);
        synchronized (repositoryMonitor) {
            return storage.remove(id) != null;
        }
    }

    @Override
    public boolean existsById(ID id) {
        requireId(id);
        synchronized (repositoryMonitor) {
            return storage.containsKey(id);
        }
    }

    /**
     * Returns a deterministic, unmodifiable snapshot without exposing the
     * repository's mutable internal map.
     */
    public Map<ID, T> storageSnapshot() {
        synchronized (repositoryMonitor) {
            Map<ID, T> snapshot = new LinkedHashMap<>();
            storage.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
            return Collections.unmodifiableMap(snapshot);
        }
    }

    protected final List<T> findMatching(Predicate<T> predicate) {
        synchronized (repositoryMonitor) {
            return storage.entrySet().stream()
                    .filter(entry -> predicate.test(entry.getValue()))
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }

    protected final boolean anyMatch(Predicate<T> predicate) {
        synchronized (repositoryMonitor) {
            return storage.values().stream().anyMatch(predicate);
        }
    }

    /**
     * Performs one repository-local bulk mutation. Callers coordinating this
     * with other repositories must still use {@link InMemoryDataCoordinator}.
     */
    protected final int deleteMatching(Predicate<T> predicate) {
        synchronized (repositoryMonitor) {
            List<ID> matchingIds = storage.entrySet().stream()
                    .filter(entry -> predicate.test(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            matchingIds.forEach(storage::remove);
            return matchingIds.size();
        }
    }

    private ID requireEntityId(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity is required");
        }
        return requireId(getId(entity));
    }

    private ID requireId(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID is required");
        }
        if (id instanceof String stringId && stringId.isBlank()) {
            throw new IllegalArgumentException("Repository ID must not be blank");
        }
        return id;
    }
}
