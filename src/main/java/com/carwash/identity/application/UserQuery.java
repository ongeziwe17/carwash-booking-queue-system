package com.carwash.identity.application;

import com.carwash.identity.domain.User;

import java.util.Optional;

/** Published identity read contract for authentication infrastructure. */
public interface UserQuery {

    Optional<User> findOptionalById(String userId);
}
