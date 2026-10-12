package com.carwash.identity.infrastructure;

import com.carwash.shared.infrastructure.InMemoryRepository;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.UserRepository;

import java.util.Optional;

public class InMemoryUserRepository extends InMemoryRepository<User, String> implements UserRepository {
    @Override public com.carwash.shared.application.PageResult<User> findPage(com.carwash.shared.application.ListQuery query) {
        var scope = query.scope();
        java.util.List<User> source = findMatching(v -> true);
        return query.select(source, java.util.Map.ofEntries(java.util.Map.entry("id", (java.util.function.Function<User, Object>) v -> v.getUserId()),
                java.util.Map.entry("name", (java.util.function.Function<User, Object>) v -> v.getFullName()),
                java.util.Map.entry("createdAt", (java.util.function.Function<User, Object>) v -> v.getCreatedAt()),
                java.util.Map.entry("accountStatus", (java.util.function.Function<User, Object>) v -> v.getAccountStatus())));
    }


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
