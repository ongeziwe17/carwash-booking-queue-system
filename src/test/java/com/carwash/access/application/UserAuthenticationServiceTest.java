package com.carwash.access.application;

import com.carwash.access.application.AuthenticationResult;
import com.carwash.access.application.InvalidCredentialsException;
import com.carwash.access.application.JwtTokenService;
import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.domain.RoleName;
import com.carwash.access.application.UserAuthenticationService;
import com.carwash.access.application.UserCredentialService;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.AccountStatus;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.infrastructure.InMemoryDataCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    private static final String RAW_PASSWORD = "LocalTestPassword123!";
    private static final String DUMMY_ENCODING = "dummy-encoding";

    @Mock UserRepository users;
    @Mock UserCredentialService credentials;
    @Mock JwtTokenService tokens;
    private InMemoryDataCoordinator coordinator;
    private UserAuthenticationService authentication;

    @BeforeEach
    void setUp() {
        when(credentials.createDummyEncoding()).thenReturn(DUMMY_ENCODING);
        coordinator = new InMemoryDataCoordinator();
        authentication = new UserAuthenticationService(users, credentials, tokens, coordinator);
    }

    @Test
    void unknownUserPerformsOnePasswordMatchAndIssuesNoToken() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(credentials.matches(RAW_PASSWORD, DUMMY_ENCODING)).thenReturn(false);
        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(" Missing@Example.com ", RAW_PASSWORD));
        verify(credentials, times(1)).createDummyEncoding();
        verify(credentials, times(1)).matches(RAW_PASSWORD, DUMMY_ENCODING);
        verifyNoInteractions(tokens);
        verify(users, never()).update(any());
    }

    @Test
    void wrongPasswordPerformsOnePasswordMatchAndDoesNotUpdateLoginState() {
        User user = activeUser("wrong-password-user", "wrong-password@example.com");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(credentials.matches(RAW_PASSWORD, user.getEncodedPassword())).thenReturn(false);
        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(user.getEmail(), RAW_PASSWORD));
        verify(credentials, times(1)).matches(RAW_PASSWORD, user.getEncodedPassword());
        verifyNoInteractions(tokens);
        verify(users, never()).update(any());
        assertNull(user.getLastLoginAt());
    }

    @Test
    void inactiveUserStillPerformsOnePasswordMatchAndIssuesNoToken() {
        User user = activeUser("inactive-user", "inactive@example.com");
        user.setAccountStatus(AccountStatus.SUSPENDED);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(credentials.matches(RAW_PASSWORD, user.getEncodedPassword())).thenReturn(true);
        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(user.getEmail(), RAW_PASSWORD));
        verify(credentials, times(1)).matches(RAW_PASSWORD, user.getEncodedPassword());
        verifyNoInteractions(tokens);
        verify(users, never()).update(any());
        assertNull(user.getLastLoginAt());
    }

    @Test
    void dummyEncodingIsGeneratedOnceForMultipleFailedRequests() {
        when(users.findByEmail("first@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("second@example.com")).thenReturn(Optional.empty());
        when(credentials.matches(RAW_PASSWORD, DUMMY_ENCODING)).thenReturn(false);
        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate("first@example.com", RAW_PASSWORD));
        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate("second@example.com", RAW_PASSWORD));
        verify(credentials, times(1)).createDummyEncoding();
        verify(credentials, times(2)).matches(RAW_PASSWORD, DUMMY_ENCODING);
        verifyNoInteractions(tokens);
        verify(users, never()).update(any());
    }

    @Test
    void successfulAuthenticationRevalidatesCurrentUserBeforePersistingLoginAndIssuingToken() {
        User initialUser = activeUser("successful-user", "successful@example.com");
        User currentUser = activeUser("successful-user", "successful@example.com");
        currentUser.setRole(RoleCatalog.role(RoleName.BUSINESS_OWNER));
        Instant expiresAt = Instant.parse("2026-08-07T08:30:00Z");

        when(users.findByEmail(initialUser.getEmail())).thenReturn(Optional.of(initialUser));
        when(credentials.matches(RAW_PASSWORD, initialUser.getEncodedPassword())).thenReturn(true);
        when(users.findById(initialUser.getUserId())).thenReturn(Optional.of(currentUser));
        when(users.update(currentUser)).thenReturn(true);
        when(tokens.issue(currentUser)).thenReturn(new JwtTokenService.IssuedToken("access-token", expiresAt, 1200));

        AuthenticationResult result = authentication.authenticate(initialUser.getEmail(), RAW_PASSWORD);

        assertSame(currentUser, result.user());
        assertEquals("access-token", result.accessToken());
        assertEquals(expiresAt, result.expiresAt());
        assertEquals(1200, result.expiresInSeconds());
        assertNotNull(currentUser.getLastLoginAt());
        verify(users).findById(initialUser.getUserId());
        verify(users).update(currentUser);
        verify(tokens).issue(currentUser);
    }

    @Test
    void userDeletedAfterPasswordVerificationCannotAuthenticate() {
        User initialUser = activeUser("deleted-user", "deleted@example.com");
        when(users.findByEmail(initialUser.getEmail())).thenReturn(Optional.of(initialUser));
        when(credentials.matches(RAW_PASSWORD, initialUser.getEncodedPassword())).thenReturn(true);
        when(users.findById(initialUser.getUserId())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(initialUser.getEmail(), RAW_PASSWORD));

        verify(users, never()).update(any());
        verifyNoInteractions(tokens);
        assertNull(initialUser.getLastLoginAt());
    }

    @Test
    void passwordChangedAfterPasswordVerificationCannotAuthenticate() {
        User initialUser = activeUser("password-changed-user", "password-changed@example.com");
        User currentUser = activeUser(
                "password-changed-user",
                "password-changed@example.com",
                "new-encoded-password"
        );
        when(users.findByEmail(initialUser.getEmail())).thenReturn(Optional.of(initialUser));
        when(credentials.matches(RAW_PASSWORD, initialUser.getEncodedPassword())).thenReturn(true);
        when(users.findById(initialUser.getUserId())).thenReturn(Optional.of(currentUser));

        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(initialUser.getEmail(), RAW_PASSWORD));

        verify(users, never()).update(any());
        verifyNoInteractions(tokens);
        assertNull(currentUser.getLastLoginAt());
    }

    @Test
    void accountSuspendedAfterPasswordVerificationCannotAuthenticate() {
        User initialUser = activeUser("suspended-during-login", "suspended-during-login@example.com");
        User currentUser = activeUser("suspended-during-login", "suspended-during-login@example.com");
        currentUser.setAccountStatus(AccountStatus.SUSPENDED);
        when(users.findByEmail(initialUser.getEmail())).thenReturn(Optional.of(initialUser));
        when(credentials.matches(RAW_PASSWORD, initialUser.getEncodedPassword())).thenReturn(true);
        when(users.findById(initialUser.getUserId())).thenReturn(Optional.of(currentUser));

        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(initialUser.getEmail(), RAW_PASSWORD));

        verify(users, never()).update(any());
        verifyNoInteractions(tokens);
        assertNull(currentUser.getLastLoginAt());
    }

    @Test
    void emailChangedAfterPasswordVerificationCannotAuthenticate() {
        User initialUser = activeUser("email-changed-user", "original@example.com");
        User currentUser = activeUser("email-changed-user", "changed@example.com");
        when(users.findByEmail(initialUser.getEmail())).thenReturn(Optional.of(initialUser));
        when(credentials.matches(RAW_PASSWORD, initialUser.getEncodedPassword())).thenReturn(true);
        when(users.findById(initialUser.getUserId())).thenReturn(Optional.of(currentUser));

        assertThrows(InvalidCredentialsException.class,
                () -> authentication.authenticate(initialUser.getEmail(), RAW_PASSWORD));

        verify(users, never()).update(any());
        verifyNoInteractions(tokens);
        assertNull(currentUser.getLastLoginAt());
    }

    @Test
    void passwordVerificationDoesNotHoldApplicationWideWriteLock() throws Exception {
        User user = activeUser("lock-scope-user", "lock-scope@example.com");
        CountDownLatch matchStarted = new CountDownLatch(1);
        CountDownLatch allowMatchToFinish = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(credentials.matches(RAW_PASSWORD, user.getEncodedPassword())).thenAnswer(invocation -> {
            matchStarted.countDown();
            if (!allowMatchToFinish.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release password verification");
            }
            return false;
        });

        Future<?> authenticationAttempt = executor.submit(() -> assertThrows(
                InvalidCredentialsException.class,
                () -> authentication.authenticate(user.getEmail(), RAW_PASSWORD)
        ));

        try {
            assertTrue(matchStarted.await(1, TimeUnit.SECONDS), "Password verification did not start");
            Future<String> unrelatedWrite = executor.submit(() -> coordinator.write(() -> "write-completed"));
            assertEquals("write-completed", unrelatedWrite.get(1, TimeUnit.SECONDS),
                    "BCrypt verification must not hold the application-wide write lock");
        } finally {
            allowMatchToFinish.countDown();
            authenticationAttempt.get(2, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private User activeUser(String userId, String email) {
        return activeUser(userId, email, "encoded-password");
    }

    private User activeUser(String userId, String email, String encodedPassword) {
        User user = User.withEncodedPassword(userId, "Authentication Test User", email,
                "0821234567", encodedPassword, RoleCatalog.role(RoleName.CUSTOMER));
        user.registerAccount();
        return user;
    }
}
