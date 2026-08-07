package com.carwash.repository.database;

import com.carwash.domain.User;
import com.carwash.repository.UserRepository;

import java.util.List;
import java.util.Optional;

public class DatabaseUserRepository implements UserRepository {

    /**
     * Future database-backed UserRepository implementation stub.
     * -----------------------------------------------------------------------------
     * This class intentionally throws UnsupportedOperationException for all methods
     * until a real database persistence mechanism is introduced.
     * -----------------------------------------------------------------------------
     */

    private static final String NOT_IMPLEMENTED = "Database repository is not implemented yet";

    @Override
    public Optional<User> findByEmail(String email) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void save(User entity) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Optional<User> findById(String s) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<User> findAll() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void delete(String s) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
