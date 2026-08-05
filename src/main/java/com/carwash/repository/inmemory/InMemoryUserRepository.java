package com.carwash.repository.inmemory;

import com.carwash.domain.User;
import com.carwash.repository.UserRepository;

import java.util.Optional;

public class InMemoryUserRepository extends InMemoryRepository<User, String> implements UserRepository {

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return storage.values().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    protected String getId(User entity) {
        return entity.getUserId();
    }
}
