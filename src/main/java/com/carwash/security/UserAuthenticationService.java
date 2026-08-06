package com.carwash.security;

import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import com.carwash.repository.inmemory.InMemoryDataCoordinator;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Service
public class UserAuthenticationService {

    private final UserRepository users;
    private final UserCredentialService credentials;
    private final JwtTokenService tokens;
    private final InMemoryDataCoordinator coordinator;
    private final String dummyEncodedPassword;

    public UserAuthenticationService(
            UserRepository users,
            UserCredentialService credentials,
            JwtTokenService tokens,
            InMemoryDataCoordinator coordinator
    ) {
        this.users = Objects.requireNonNull(users, "User repository is required");
        this.credentials = Objects.requireNonNull(credentials, "Credential service is required");
        this.tokens = Objects.requireNonNull(tokens, "JWT token service is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.dummyEncodedPassword = credentials.createDummyEncoding();
    }

    public AuthenticationResult authenticate(String email, String rawPassword) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return coordinator.write(() -> {
            User user = users.findByEmail(normalizedEmail).orElse(null);
            String encodedPassword = user == null ? dummyEncodedPassword : user.getEncodedPassword();
            boolean credentialMatches = credentials.matches(rawPassword, encodedPassword);
            boolean activeUser = user != null && user.getAccountStatus() == AccountStatus.ACTIVE;
            if (!activeUser || !credentialMatches) {
                throw new InvalidCredentialsException();
            }
            JwtTokenService.IssuedToken token = tokens.issue(user);
            user.recordSuccessfulLogin();
            if (!users.update(user)) {
                throw new IllegalStateException("Authenticated user no longer exists");
            }
            return new AuthenticationResult(user, token.value(), token.expiresAt(), token.expiresInSeconds());
        });
    }
}
