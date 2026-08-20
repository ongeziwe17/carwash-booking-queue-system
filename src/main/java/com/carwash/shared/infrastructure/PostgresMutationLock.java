package com.carwash.shared.infrastructure;

import com.carwash.shared.application.MutationLock;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

/** PostgreSQL transaction-scoped advisory locks acquired in deterministic order. */
@Component
@Profile("postgres")
public final class PostgresMutationLock implements MutationLock {

    private final JdbcTemplate jdbc;

    public PostgresMutationLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void acquire(Collection<String> keys) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Mutation locks require an active database transaction");
        }
        keys.stream().distinct().sorted().forEach(key -> jdbc.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                statement -> statement.setString(1, key),
                resultSet -> null));
    }
}
