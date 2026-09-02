package com.carwash.audit.application;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Per-request server-owned correlation and duplicate suppression context. */
public final class AuditRequestContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private AuditRequestContext() { }

    public static String open() {
        String correlationId = UUID.randomUUID().toString();
        CURRENT.set(new State(correlationId, new HashSet<>()));
        return correlationId;
    }

    public static void open(String correlationId) { CURRENT.set(new State(correlationId, new HashSet<>())); }
    public static String correlationId() {
        State state = CURRENT.get();
        return state == null ? UUID.randomUUID().toString() : state.correlationId();
    }
    public static boolean markOnce(String key) {
        State state = CURRENT.get();
        return state == null || state.recorded().add(key);
    }
    public static boolean hasRecorded() {
        State state = CURRENT.get();
        return state != null && !state.recorded().isEmpty();
    }
    public static void close() { CURRENT.remove(); }

    private record State(String correlationId, Set<String> recorded) { }
}
