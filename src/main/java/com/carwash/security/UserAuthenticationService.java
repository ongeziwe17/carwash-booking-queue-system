package com.carwash.security;

import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserAuthenticationService {

    private final UserRepository users;
    private final UserCredentialService credentials;
    private final JwtTokenService tokens;
    private final String dummyEncodedPassword;

    public UserAuthenticationService(
            UserRepository users,
            UserCredentialService credentials,
            JwtTokenService tokens
    ) {
        this.users = users;
        this.credentials = credentials;
        this.tokens = tokens;
        this.dummyEncodedPassword = credentials.createDummyEncoding();
    }

    public AuthenticationResult authenticate(String email, String rawPassword) {
        String normalizedEmail = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);

        User user = users.findByEmail(normalizedEmail).orElse(null);
        String encodedPassword = user == null
                ? dummyEncodedPassword
                : user.getEncodedPassword();

        boolean credentialMatches = credentials.matches(rawPassword, encodedPassword);
        boolean activeUser = user != null && user.getAccountStatus() == AccountStatus.ACTIVE;

        if (!activeUser || !credentialMatches) {
            throw new InvalidCredentialsException();
        }

        JwtTokenService.IssuedToken token = tokens.issue(user);
        user.recordSuccessfulLogin();
        users.save(user);
        return new AuthenticationResult(
                user,
                token.value(),
                token.expiresAt(),
                token.expiresInSeconds()
        );
    }
}
