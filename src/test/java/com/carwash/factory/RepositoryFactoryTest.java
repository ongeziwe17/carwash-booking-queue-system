package com.carwash.factory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryFactoryTest {
    @Test
    void shouldReturnUserRepositoryForMemoryStorage() {
        assertNotNull(RepositoryFactory.getUserRepository(StorageType.MEMORY));
    }

    @Test
    void shouldReturnAllRepositoriesForMemoryStorage() {
        assertNotNull(RepositoryFactory.getUserRepository(StorageType.MEMORY));
        assertNotNull(RepositoryFactory.getRoleRepository(StorageType.MEMORY));
        assertNotNull(RepositoryFactory.getVehicleRepository(StorageType.MEMORY));
        assertNotNull(RepositoryFactory.getServiceRepository(StorageType.MEMORY));
        assertNotNull(RepositoryFactory.getBookingRepository(StorageType.MEMORY));
        assertNotNull(RepositoryFactory.getQueueEntryRepository(StorageType.MEMORY));
        assertNotNull(RepositoryFactory.getNotificationRepository(StorageType.MEMORY));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForNullStorageType() {
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryFactory.getUserRepository(null));
    }

    @Test
    void shouldThrowUnsupportedOperationExceptionForDatabaseStorageType() {
        assertThrows(UnsupportedOperationException.class,
                () -> RepositoryFactory.getUserRepository(StorageType.DATABASE));
    }

    @Test
    void shouldThrowUnsupportedOperationExceptionForFilesystemStorageType() {
        assertThrows(UnsupportedOperationException.class,
                () -> RepositoryFactory.getUserRepository(StorageType.FILESYSTEM));
    }

    @Test
    void shouldThrowUnsupportedOperationExceptionForApiStorageType() {
        assertThrows(UnsupportedOperationException.class,
                () -> RepositoryFactory.getUserRepository(StorageType.API));
    }
}