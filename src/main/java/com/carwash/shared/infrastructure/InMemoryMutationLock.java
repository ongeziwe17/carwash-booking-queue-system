package com.carwash.shared.infrastructure;

import com.carwash.shared.application.MutationLock;

import java.util.Collection;

/** The enclosing fair coordinator write lock already serializes in-memory mutations. */
public final class InMemoryMutationLock implements MutationLock {

    @Override
    public void acquire(Collection<String> keys) {
        // Intentionally empty.
    }
}
