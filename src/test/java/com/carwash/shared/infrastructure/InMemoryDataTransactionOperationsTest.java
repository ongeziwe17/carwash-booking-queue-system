package com.carwash.shared.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryDataTransactionOperationsTest {
    private final InMemoryDataCoordinator transactions=new InMemoryDataCoordinator();

    @Test void readReturnsValue(){assertEquals("value",transactions.read(()->"value"));}
    @Test void writeReturnsValue(){assertEquals(42,transactions.write(()->42));}
    @Test void nestedRequiredStyleOperationsRemainReentrant(){assertEquals("nested",transactions.write(()->transactions.read(()->"nested")));}
    @Test void compensationRunsForInMemoryFailure(){AtomicBoolean restored=new AtomicBoolean();RuntimeException failure=new RuntimeException("failed");transactions.compensate(failure,()->restored.set(true));assertTrue(restored.get());}
    @Test void failedCompensationIsSuppressed(){RuntimeException failure=new RuntimeException("failed");transactions.compensate(failure,()->{throw new IllegalStateException("restore");});assertEquals(1,failure.getSuppressed().length);}
    @Test void afterCommitWorkRunsAndItsFailureIsIsolated(){AtomicInteger failures=new AtomicInteger();transactions.afterCommitBestEffort(()->{throw new IllegalStateException("optional");},ignored->failures.incrementAndGet());assertEquals(1,failures.get());}
    @Test void isolatedWriteUsesEquivalentSharedWriteBoundary(){assertEquals("isolated",transactions.isolatedWrite(()->"isolated"));}
    @Test void registeredRollbackRunsOnlyWhenOutermostWriteFails(){AtomicBoolean rolledBack=new AtomicBoolean();assertThrows(IllegalStateException.class,()->transactions.write(()->{transactions.onRollback(()->rolledBack.set(true));throw new IllegalStateException("failed");}));assertTrue(rolledBack.get());rolledBack.set(false);transactions.write(()->transactions.onRollback(()->rolledBack.set(true)));assertFalse(rolledBack.get());}
    @Test void nestedRollbackRegistrationBelongsToAuthoritativeOuterWrite(){AtomicInteger rollbacks=new AtomicInteger();assertThrows(IllegalStateException.class,()->transactions.write(()->{transactions.write(()->transactions.onRollback(rollbacks::incrementAndGet));throw new IllegalStateException("outer");}));assertEquals(1,rollbacks.get());}
}
