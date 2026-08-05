package com.carwash.security;

import com.carwash.domain.User;
import com.carwash.enums.AccountStatus;
import com.carwash.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private UserAuthenticationService authentication;

    @BeforeEach
    void setUp() {
        when(credentials.createDummyEncoding()).thenReturn(DUMMY_ENCODING);
        authentication = new UserAuthenticationService(users, credentials, tokens);
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

    private User activeUser(String userId, String email) {
        User user = User.withEncodedPassword(userId, "Authentication Test User", email,
                "0821234567", "encoded-password", RoleCatalog.role(RoleName.CUSTOMER));
        user.registerAccount();
        return user;
    }
}
