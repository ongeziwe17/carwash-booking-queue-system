package com.carwash.marketplace.application;

import com.carwash.access.application.TenantAccessContext;
import com.carwash.identity.domain.RoleName;
import com.carwash.marketplace.domain.CarWashBranch;
import com.carwash.marketplace.domain.CarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBranchRepository;
import com.carwash.marketplace.infrastructure.InMemoryCarWashBusinessRepository;
import com.carwash.shared.application.DataTransactionOperations;
import com.carwash.shared.application.MutationLock;
import com.carwash.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicTenantMutationAuthorizationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T06:00:00Z"), ZoneOffset.UTC);
    private static final TenantAccessContext TENANT_A =
            new TenantAccessContext("owner-a", RoleName.BUSINESS_OWNER, "business-a");
    private static final TenantAccessContext ADMIN =
            new TenantAccessContext("platform-admin", RoleName.PLATFORM_ADMIN, null);

    @Test
    void tenantAuthorizationAndGuardedWriteExecuteInsideOneLockedWriteBoundary() {
        TrackingTransactions transactions = new TrackingTransactions();
        TrackingLock lock = new TrackingLock();
        InMemoryCarWashBusinessRepository businesses = businesses();
        InMemoryCarWashBranchRepository delegate = branches("business-a");
        BoundaryCheckingBranchRepository branches =
                new BoundaryCheckingBranchRepository(delegate, transactions, lock);
        MarketplaceManagementService service = new MarketplaceManagementService(
                businesses, branches, transactions, lock, CLOCK);

        BranchSnapshot updated = service.updateBranch(TENANT_A, " branch-1 ", update("Atomic update"));

        assertEquals("Atomic update", updated.branchName());
        assertEquals(1, transactions.writeCount.get());
        assertEquals(0, transactions.readCount.get());
        assertEquals("business-a", branches.actualWriteBusinessId);
        assertTrue(branches.scopedReadInsideWrite.get());
        assertTrue(branches.scopedWriteInsideWrite.get());
        assertEquals(0, branches.unscopedReadCount.get());
    }

    @Test
    void deleteAndRecreateUnderAnotherTenantCannotBeMutatedAcrossFormerCheckWriteGap() throws Exception {
        InMemoryCarWashBusinessRepository businesses = businesses();
        InMemoryCarWashBranchRepository branches = branches("business-a");
        CountDownLatch writeBoundaryEntered = new CountDownLatch(1);
        CountDownLatch replacementCommitted = new CountDownLatch(1);
        BarrierTransactions transactions = new BarrierTransactions(writeBoundaryEntered, replacementCommitted);
        MarketplaceManagementService service = new MarketplaceManagementService(
                businesses, branches, transactions, MutationLock.noOp(), CLOCK);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> mutation = executor.submit(() -> capture(
                    () -> service.updateBranch(TENANT_A, "branch-1", update("Tenant A overwrite"))));
            Future<?> replacement = executor.submit(() -> {
                await(writeBoundaryEntered);
                assertTrue(branches.deleteById("branch-1"));
                assertTrue(branches.insert(branch("branch-1", "business-b", "Tenant B canonical")));
                replacementCommitted.countDown();
            });

            replacement.get(5, TimeUnit.SECONDS);
            Throwable failure = mutation.get(5, TimeUnit.SECONDS);
            assertInstanceOf(ResourceNotFoundException.class, failure);
        }

        CarWashBranch canonical = branches.findById("branch-1").orElseThrow();
        assertEquals("business-b", canonical.getBusinessId());
        assertEquals("Tenant B canonical", canonical.getBranchName());
    }

    @Test
    void zeroRowTenantGuardNeverRetriesThroughAdministratorScope() {
        TrackingTransactions transactions = new TrackingTransactions();
        TrackingLock lock = new TrackingLock();
        BoundaryCheckingBranchRepository branches = new BoundaryCheckingBranchRepository(
                branches("business-a"), transactions, lock);
        branches.rejectGuardedWrite = true;
        MarketplaceManagementService service = new MarketplaceManagementService(
                businesses(), branches, transactions, lock, CLOCK);

        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.updateBranch(TENANT_A, "branch-1", update("Rejected")));

        assertEquals("Branch not found", failure.getMessage());
        assertEquals(0, branches.administratorWriteCount.get());
        assertEquals("Original", branches.delegate.findById("branch-1").orElseThrow().getBranchName());
    }

    @Test
    void lostTenantScopeCannotFallBackToAnUnscopedResourceLookup() {
        TrackingTransactions transactions = new TrackingTransactions();
        TrackingLock lock = new TrackingLock();
        BoundaryCheckingBranchRepository branches = new BoundaryCheckingBranchRepository(
                branches("business-a"), transactions, lock);
        branches.denyScopedRead = true;
        MarketplaceManagementService service = new MarketplaceManagementService(
                businesses(), branches, transactions, lock, CLOCK);

        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.updateBranch(TENANT_A, "branch-1", update("Stale membership")));

        assertEquals("Branch not found", failure.getMessage());
        assertEquals(0, branches.unscopedReadCount.get());
        assertEquals(0, branches.administratorWriteCount.get());
        assertEquals("Original", branches.delegate.findById("branch-1").orElseThrow().getBranchName());
    }

    @Test
    void foreignAndMissingResourcesHaveTheSameSafeFailureWhileAdminScopeIsExplicit() {
        TrackingTransactions transactions = new TrackingTransactions();
        TrackingLock lock = new TrackingLock();
        BoundaryCheckingBranchRepository branches = new BoundaryCheckingBranchRepository(
                branches("business-b"), transactions, lock);
        MarketplaceManagementService service = new MarketplaceManagementService(
                businesses(), branches, transactions, lock, CLOCK);

        ResourceNotFoundException foreign = assertThrows(ResourceNotFoundException.class,
                () -> service.updateBranch(TENANT_A, "branch-1", update("Denied")));
        ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
                () -> service.updateBranch(TENANT_A, "missing", update("Denied")));
        BranchSnapshot adminUpdate = service.updateBranch(ADMIN, "branch-1", update("Admin update"));

        assertEquals(missing.getMessage(), foreign.getMessage());
        assertEquals("Branch not found", foreign.getMessage());
        assertEquals("Admin update", adminUpdate.branchName());
        assertEquals(1, branches.administratorWriteCount.get());
    }

    private static InMemoryCarWashBusinessRepository businesses() {
        InMemoryCarWashBusinessRepository businesses = new InMemoryCarWashBusinessRepository();
        assertTrue(businesses.insert(new com.carwash.marketplace.domain.CarWashBusiness(
                "business-a", "Tenant A", "a@example.test", "+27821234567", "REG-A",
                com.carwash.marketplace.domain.BusinessStatus.ACTIVE,
                java.time.LocalDateTime.now(CLOCK), java.time.LocalDateTime.now(CLOCK))));
        assertTrue(businesses.insert(new com.carwash.marketplace.domain.CarWashBusiness(
                "business-b", "Tenant B", "b@example.test", "+27821234568", "REG-B",
                com.carwash.marketplace.domain.BusinessStatus.ACTIVE,
                java.time.LocalDateTime.now(CLOCK), java.time.LocalDateTime.now(CLOCK))));
        return businesses;
    }

    private static InMemoryCarWashBranchRepository branches(String businessId) {
        InMemoryCarWashBranchRepository branches = new InMemoryCarWashBranchRepository();
        assertTrue(branches.insert(branch("branch-1", businessId, "Original")));
        return branches;
    }

    private static CarWashBranch branch(String id, String businessId, String name) {
        return new CarWashBranch(
                id, businessId, name, "1 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241), "Africa/Johannesburg",
                com.carwash.marketplace.domain.BranchStatus.ACTIVE, true,
                java.time.LocalDateTime.now(CLOCK), java.time.LocalDateTime.now(CLOCK));
    }

    private static UpdateBranchCommand update(String name) {
        return new UpdateBranchCommand(
                name, "2 Main Road", null, "Cape Town", "Western Cape", "8001", "ZA",
                BigDecimal.valueOf(-33.9249), BigDecimal.valueOf(18.4241),
                "Africa/Johannesburg", true);
    }

    private static Throwable capture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static class TrackingTransactions implements DataTransactionOperations {
        private final ThreadLocal<Boolean> insideWrite = ThreadLocal.withInitial(() -> false);
        private final AtomicInteger readCount = new AtomicInteger();
        private final AtomicInteger writeCount = new AtomicInteger();

        @Override
        public <T> T read(Supplier<T> action) {
            readCount.incrementAndGet();
            return action.get();
        }

        @Override
        public <T> T write(Supplier<T> action) {
            writeCount.incrementAndGet();
            insideWrite.set(true);
            try {
                return action.get();
            } finally {
                insideWrite.remove();
            }
        }

        @Override public void compensate(RuntimeException failure, Runnable compensation) { compensation.run(); }
        @Override public void afterCommitBestEffort(Runnable action, Consumer<RuntimeException> handler) {
            try { action.run(); } catch (RuntimeException failure) { handler.accept(failure); }
        }

        boolean insideWrite() {
            return insideWrite.get();
        }
    }

    private static final class BarrierTransactions extends TrackingTransactions {
        private final CountDownLatch writeBoundaryEntered;
        private final CountDownLatch replacementCommitted;

        private BarrierTransactions(
                CountDownLatch writeBoundaryEntered, CountDownLatch replacementCommitted) {
            this.writeBoundaryEntered = writeBoundaryEntered;
            this.replacementCommitted = replacementCommitted;
        }

        @Override
        public <T> T write(Supplier<T> action) {
            return super.write(() -> {
                writeBoundaryEntered.countDown();
                await(replacementCommitted);
                return action.get();
            });
        }
    }

    private static final class TrackingLock implements MutationLock {
        private final Set<String> held = ConcurrentHashMap.newKeySet();

        @Override
        public void acquire(Collection<String> keys) {
            keys.stream().sorted().forEach(held::add);
        }

        boolean holds(String key) {
            return held.contains(key);
        }
    }

    private static final class BoundaryCheckingBranchRepository implements CarWashBranchRepository {
        private final InMemoryCarWashBranchRepository delegate;
        private final TrackingTransactions transactions;
        private final TrackingLock lock;
        private final AtomicBoolean scopedReadInsideWrite = new AtomicBoolean();
        private final AtomicBoolean scopedWriteInsideWrite = new AtomicBoolean();
        private final AtomicInteger unscopedReadCount = new AtomicInteger();
        private final AtomicInteger administratorWriteCount = new AtomicInteger();
        private String actualWriteBusinessId;
        private boolean rejectGuardedWrite;
        private boolean denyScopedRead;

        private BoundaryCheckingBranchRepository(
                InMemoryCarWashBranchRepository delegate,
                TrackingTransactions transactions,
                TrackingLock lock
        ) {
            this.delegate = delegate;
            this.transactions = transactions;
            this.lock = lock;
        }

        @Override public List<CarWashBranch> findByBusinessId(String id) { return delegate.findByBusinessId(id); }

        @Override
        public Optional<CarWashBranch> findByIdAndBusinessId(String id, String businessId) {
            scopedReadInsideWrite.set(transactions.insideWrite());
            assertTrue(lock.holds(MutationLock.branch(id)), "Branch lock must precede scoped canonical read");
            return denyScopedRead ? Optional.empty() : delegate.findByIdAndBusinessId(id, businessId);
        }

        @Override
        public boolean updateForBusiness(CarWashBranch branch, String businessId) {
            scopedWriteInsideWrite.set(transactions.insideWrite());
            actualWriteBusinessId = businessId;
            assertTrue(lock.holds(MutationLock.branch(branch.getBranchId())),
                    "Branch lock must remain held for guarded write");
            return !rejectGuardedWrite && delegate.updateForBusiness(branch, businessId);
        }

        @Override
        public boolean updateForAdministrator(CarWashBranch branch) {
            administratorWriteCount.incrementAndGet();
            return delegate.updateForAdministrator(branch);
        }

        @Override public boolean insert(CarWashBranch value) { return delegate.insert(value); }
        @Override public boolean update(CarWashBranch value) { throw new AssertionError("Unscoped update is forbidden"); }
        @Override public Optional<CarWashBranch> findById(String id) {
            unscopedReadCount.incrementAndGet();
            return delegate.findById(id);
        }
        @Override public List<CarWashBranch> findAll() { return delegate.findAll(); }
        @Override public boolean deleteById(String id) { return delegate.deleteById(id); }
        @Override public boolean existsById(String id) { return delegate.existsById(id); }
    }
}
