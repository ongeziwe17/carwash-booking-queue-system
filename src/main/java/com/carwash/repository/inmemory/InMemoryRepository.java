package com.carwash.repository.inmemory;

import com.carwash.repository.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public abstract class InMemoryRepository<T, ID> implements Repository<T, ID> {

    protected final Map<ID, T> storage = new ConcurrentHashMap<>();

    protected abstract ID getId(T entity);

    @Override
    public synchronized boolean insert(T entity) {
        ID id = requireEntityId(entity);
        return storage.putIfAbsent(id, entity) == null;
    }

    @Override
    public synchronized boolean update(T entity) {
        ID id = requireEntityId(entity);
        return storage.replace(id, entity) != null;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(storage.get(requireId(id)));
    }

    @Override
    public List<T> findAll() {
        List<T> snapshot = new ArrayList<>(storage.values());
        snapshot.sort(Comparator.comparing(entity -> String.valueOf(getId(entity))));
        return List.copyOf(snapshot);
    }

    @Override
    public synchronized boolean deleteById(ID id) {
        return storage.remove(requireId(id)) != null;
    }

    @Override
    public boolean existsById(ID id) {
        return storage.containsKey(requireId(id));
    }

    public Map<ID, T> storageSnapshot() {
        Map<ID, T> snapshot = new LinkedHashMap<>();
        storage.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(snapshot);
    }

    protected final ID requireEntityId(T entity) {
        Objects.requireNonNull(entity, "Entity is required");
        return requireId(getId(entity));
    }

    protected final ID requireId(ID id) {
        Objects.requireNonNull(id, "Entity ID is required");
        if (id instanceof String value && value.isBlank()) {
            throw new IllegalArgumentException("Entity ID must not be blank");
        }
        return id;
    }

    protected final List<T> immutableSorted(List<T> values) {
        List<T> snapshot = new ArrayList<>(values);
        snapshot.sort(Comparator.comparing(entity -> String.valueOf(getId(entity))));
        return List.copyOf(snapshot);
    }
}
