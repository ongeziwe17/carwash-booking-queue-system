package com.carwash.repository.inmemory;

import com.carwash.repository.Repository;

import java.util.*;

public abstract class InMemoryRepository<T, ID> implements Repository<T, ID> {
    protected final Map<ID, T> storage = new HashMap<>();

    protected abstract ID getId(T entity);

    @Override
    public void save(T entity) {
        storage.put(getId(entity), entity);
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void delete(ID id) {
        storage.remove(id);
    }

    public Map<ID, T> storageSnapshot(){
        return Map.copyOf(storage);
    }

    public void printStorage(){
        System.out.println(storageAsString());
    }

    public String storageAsString() {
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        storage.forEach((id, entity) -> joiner.add(id + "=" + entity));
        return joiner.toString();
    }
}
