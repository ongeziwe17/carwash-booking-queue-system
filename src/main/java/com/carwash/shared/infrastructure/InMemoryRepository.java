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
import java.util.function.Consumer;
import java.util.function.Function;

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

    /** Atomically replaces an identified value only while its authorization predicate still holds. */
    protected final boolean updateMatching(ID id, T entity, Predicate<T> predicate) {
        requireId(id);
        if (entity == null) throw new IllegalArgumentException("Entity is required");
        if (!id.equals(requireEntityId(entity))) {
            throw new IllegalArgumentException("Entity ID does not match guarded mutation ID");
        }
        synchronized (repositoryMonitor) {
            T current = storage.get(id);
            if (current == null || !predicate.test(current)) return false;
            storage.put(id, entity);
            return true;
        }
    }

    /** Atomically inspects and mutates one value while the repository guard remains held. */
    protected final <R> Optional<R> mutateMatching(
            ID id, Predicate<T> predicate, Function<T, R> mutation) {
        requireId(id);
        if (predicate == null || mutation == null) throw new IllegalArgumentException("Mutation is required");
        synchronized (repositoryMonitor) {
            T current = storage.get(id);
            if (current == null || !predicate.test(current)) return Optional.empty();
            return Optional.ofNullable(mutation.apply(current));
        }
    }

    /** Atomically mutates all matching values and returns the affected count. */
    protected final int mutateMatching(Predicate<T> predicate, Consumer<T> mutation) {
        if (predicate == null || mutation == null) throw new IllegalArgumentException("Mutation is required");
        synchronized (repositoryMonitor) {
            int affected = 0;
            for (Map.Entry<ID, T> entry : storage.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                if (predicate.test(entry.getValue())) {
                    mutation.accept(entry.getValue());
                    affected++;
                }
            }
            return affected;
        }
    }

    /** Atomically removes an identified value only while its authorization predicate still holds. */
    protected final boolean deleteMatching(ID id, Predicate<T> predicate) {
        requireId(id);
        synchronized (repositoryMonitor) {
            T current = storage.get(id);
            if (current == null || !predicate.test(current)) return false;
            return storage.remove(id, current);
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
