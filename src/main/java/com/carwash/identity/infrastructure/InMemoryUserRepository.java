package com.carwash.identity.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;

import java.util.Optional;

public class InMemoryUserRepository extends InMemoryRepository<User, String> implements UserRepository {

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return findMatching(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(email))
                .stream()
                .findFirst();
    }

    @Override
    protected String getId(User entity) {
        return entity.getUserId();
    }
}
