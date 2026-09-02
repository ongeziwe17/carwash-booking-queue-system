package com.carwash.audit.infrastructure;

import com.carwash.audit.application.*;
import com.carwash.audit.domain.AuditRepository;
import com.carwash.shared.application.DataTransactionOperations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfiguration {
    @Bean
    @Profile("!postgres")
    InMemoryAuditRepository inMemoryAuditRepository(DataTransactionOperations transactions,
                                                     AuditMetadataPolicy metadataPolicy) {
        return new InMemoryAuditRepository(transactions, metadataPolicy);
    }

    @Bean
    AuditMetadataPolicy auditMetadataPolicy(AuditProperties properties) {
        return new AuditMetadataPolicy(properties);
    }

    @Bean
    AuditIdGenerator auditIdGenerator() { return () -> "aud_" + UUID.randomUUID(); }

    @Bean
    AuditOperations auditOperations(AuditRepository repository, DataTransactionOperations transactions,
                                    AuditMetadataPolicy metadataPolicy, AuditIdGenerator ids, Clock clock) {
        return new AuditService(repository, transactions, metadataPolicy, ids, clock);
    }

    @Bean
    AuditQueryService auditQueryService(AuditRepository repository, DataTransactionOperations transactions,
                                        AuditOperations audit, AuditProperties properties, Clock clock) {
        return new AuditQueryService(repository, transactions, audit, properties, clock);
    }
}
