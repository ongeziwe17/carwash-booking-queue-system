package com.carwash.repository.database;

import com.carwash.domain.User;
import com.carwash.repository.UserRepository;

import java.util.List;
import java.util.Optional;

public class DatabaseUserRepository implements UserRepository {

    private static final String NOT_IMPLEMENTED = "Database repository is not implemented yet";

    @Override
    public Optional<User> findByEmail(String email) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean insert(User entity) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean update(User entity) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Optional<User> findById(String id) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<User> findAll() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean deleteById(String id) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean existsById(String id) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
