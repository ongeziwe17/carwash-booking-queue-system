package com.carwash.identity.application;

/**
 * Identity-facing credential contract implemented by the access module.
 */
public interface CredentialService {

    void validatePolicy(String rawPassword);

    String encode(String rawPassword);
}
