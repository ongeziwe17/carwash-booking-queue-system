package com.carwash.access.application;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.AccountStatus;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
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
        String normalizedEmail = normalizeEmail(email);
        AuthenticationCandidate candidate = coordinator.read(() -> authenticationCandidate(normalizedEmail));

        boolean credentialMatches = credentials.matches(rawPassword, candidate.encodedPassword());
        if (!candidate.active() || !credentialMatches) {
            throw new InvalidCredentialsException();
        }

        return coordinator.write(() -> completeAuthentication(candidate, normalizedEmail));
    }

    private AuthenticationCandidate authenticationCandidate(String normalizedEmail) {
        User user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return new AuthenticationCandidate(null, dummyEncodedPassword, false);
        }
        return new AuthenticationCandidate(
                user.getUserId(),
                user.getEncodedPassword(),
                user.getAccountStatus() == AccountStatus.ACTIVE
        );
    }

    private AuthenticationResult completeAuthentication(AuthenticationCandidate candidate, String normalizedEmail) {
        User currentUser = users.findById(candidate.userId())
                .orElseThrow(InvalidCredentialsException::new);

        boolean stillValid = currentUser.getAccountStatus() == AccountStatus.ACTIVE
                && normalizedEmail.equals(normalizeEmail(currentUser.getEmail()))
                && Objects.equals(candidate.encodedPassword(), currentUser.getEncodedPassword());
        if (!stillValid) {
            throw new InvalidCredentialsException();
        }

        currentUser.recordSuccessfulLogin();
        if (!users.update(currentUser)) {
            throw new InvalidCredentialsException();
        }

        JwtTokenService.IssuedToken token = tokens.issue(currentUser);
        return new AuthenticationResult(
                currentUser,
                token.value(),
                token.expiresAt(),
                token.expiresInSeconds()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record AuthenticationCandidate(String userId, String encodedPassword, boolean active) {
    }
}
