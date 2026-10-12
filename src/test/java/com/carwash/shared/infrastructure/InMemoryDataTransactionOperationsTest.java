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

    @Test void pageCountAndContentRemainOneSnapshotDuringAConcurrentWrite() throws Exception {
        var repository=new com.carwash.catalog.infrastructure.InMemoryServiceRepository();
        var first=new com.carwash.catalog.domain.Service("snapshot-first","First",null,new java.math.BigDecimal("10.00"),20);
        repository.insert(first);
        var query=com.carwash.catalog.application.ServiceListPolicy.POLICY.parse(java.util.Map.of());
        var countRead=new java.util.concurrent.CountDownLatch(1);
        var writerStarted=new java.util.concurrent.CountDownLatch(1);
        var writerCompleted=new AtomicBoolean();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var reader=executor.submit(() -> transactions.readSnapshot(() -> {
                long total=repository.findPage(query).totalElements(); countRead.countDown();
                try { assertTrue(writerStarted.await(5,java.util.concurrent.TimeUnit.SECONDS)); }
                catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                assertFalse(writerCompleted.get());
                var content=repository.findPage(query).content();
                return com.carwash.shared.application.PageResult.of(content,query.page(),total);
            }));
            assertTrue(countRead.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var writer=executor.submit(() -> {
                writerStarted.countDown();
                transactions.write(() -> repository.insert(new com.carwash.catalog.domain.Service(
                        "snapshot-later","Later",null,new java.math.BigDecimal("10.00"),20)));
                writerCompleted.set(true);
            });
            var snapshot=reader.get(5,java.util.concurrent.TimeUnit.SECONDS); writer.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1,snapshot.totalElements()); assertEquals(java.util.List.of("snapshot-first"),snapshot.content().stream().map(com.carwash.catalog.domain.Service::getServiceId).toList());
            assertEquals(2,transactions.readSnapshot(() -> repository.findPage(query).totalElements()));
        }
    }

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
