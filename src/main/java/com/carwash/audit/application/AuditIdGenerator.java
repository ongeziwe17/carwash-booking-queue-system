package com.carwash.audit.application;

@FunctionalInterface
public interface AuditIdGenerator { String nextId(); }
