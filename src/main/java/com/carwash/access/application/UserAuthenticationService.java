package com.carwash.access.application;

import com.carwash.identity.domain.User;
import com.carwash.identity.domain.AccountStatus;
import com.carwash.identity.domain.UserRepository;
import com.carwash.shared.application.DataTransactionOperations;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.carwash.identity.domain.RoleCatalog;
import com.carwash.identity.application.TenantMembershipQuery;
import com.carwash.audit.application.*;
import com.carwash.audit.domain.*;

import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserAuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAuthenticationService.class);

    private final UserRepository users;
    private final UserCredentialService credentials;
    private final JwtTokenService tokens;
    private final DataTransactionOperations coordinator;
    private final String dummyEncodedPassword;
    private final TenantMembershipQuery memberships;
    private final AuditOperations audit;

    public UserAuthenticationService(
            UserRepository users,
            UserCredentialService credentials,
            JwtTokenService tokens,
            DataTransactionOperations coordinator
    ) {
        this(users, credentials, tokens, coordinator, null, AuditOperations.noOp());
    }

    @Autowired
    public UserAuthenticationService(
            UserRepository users,
            UserCredentialService credentials,
            JwtTokenService tokens,
            DataTransactionOperations coordinator,
            TenantMembershipQuery memberships,
            AuditOperations audit
    ) {
        this.users = Objects.requireNonNull(users, "User repository is required");
        this.credentials = Objects.requireNonNull(credentials, "Credential service is required");
        this.tokens = Objects.requireNonNull(tokens, "JWT token service is required");
        this.coordinator = Objects.requireNonNull(coordinator, "Data coordinator is required");
        this.memberships = memberships;
        this.audit = Objects.requireNonNull(audit, "Audit operations are required");
        this.dummyEncodedPassword = credentials.createDummyEncoding();
    }

    public AuthenticationResult authenticate(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        AuthenticationCandidate candidate = coordinator.read(() -> authenticationCandidate(normalizedEmail));

        boolean credentialMatches = credentials.matches(rawPassword, candidate.encodedPassword());
        if (!candidate.active() || !credentialMatches) {
            try {
                audit.appendIsolated(loginFailure(), AuditOutcome.FAILURE, "INVALID_CREDENTIALS");
            } catch (RuntimeException auditFailure) {
                LOGGER.error("audit_login_failure_persistence_failure correlationId={}",
                        AuditRequestContext.correlationId());
            }
            throw new InvalidCredentialsException();
        }

        AuditCommand success = AuditCommand.actionForBusiness(AuditAction.LOGIN_SUCCESS,
                AuditActor.user(candidate.userId(), candidate.role(), candidate.businessId()),
                candidate.businessId(), "USER", candidate.userId(), AuditSource.SECURITY);
        AuditCommand failure = loginFailure();
        return audit.execute(success, failure,
                () -> coordinator.write(() -> completeAuthentication(candidate, normalizedEmail)));
    }

    private AuthenticationCandidate authenticationCandidate(String normalizedEmail) {
        User user = users.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return new AuthenticationCandidate(null, dummyEncodedPassword, false, null, null);
        }
        return new AuthenticationCandidate(
                user.getUserId(),
                user.getEncodedPassword(),
                user.getAccountStatus() == AccountStatus.ACTIVE,
                RoleCatalog.name(user.getRole()).name(),
                memberships == null ? null : memberships.findByUserId(user.getUserId())
                        .map(com.carwash.identity.domain.TenantMembership::businessId).orElse(null)
        );
    }

    private AuditCommand loginFailure() {
        return AuditCommand.action(AuditAction.LOGIN_FAILURE, AuditActor.anonymous(),
                "AUTHENTICATION", null, AuditSource.SECURITY);
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

        // Resolve canonical role and membership before mutating login state. An unassigned
        // operational user must fail closed without attempting an otherwise-invalid write.
        JwtTokenService.IssuedToken token = tokens.issue(currentUser);

        currentUser.recordSuccessfulLogin();
        if (!users.update(currentUser)) {
            throw new InvalidCredentialsException();
        }
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

    private record AuthenticationCandidate(
            String userId, String encodedPassword, boolean active, String role, String businessId) {
    }
}
