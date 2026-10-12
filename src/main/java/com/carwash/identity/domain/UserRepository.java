package com.carwash.identity.domain;

import com.carwash.shared.domain.Repository;

import com.carwash.identity.domain.User;

import java.util.Optional;

public interface UserRepository extends Repository<User, String>, com.carwash.shared.application.PagedRepository<User>{
    Optional<User> findByEmail(String email);
}
