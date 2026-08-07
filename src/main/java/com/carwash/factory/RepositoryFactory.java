package com.carwash.factory;

import com.carwash.repository.*;
import com.carwash.repository.inmemory.*;

public final class RepositoryFactory {

    private RepositoryFactory(){}

    public static UserRepository getUserRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            // DATABASE remains intentionally unsupported for now.
            // See com.carwash.repository.database.DatabaseUserRepository for the future stub structure.
            case MEMORY -> new InMemoryUserRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    public static RoleRepository getRoleRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            case MEMORY -> new InMemoryRoleRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    public static VehicleRepository getVehicleRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            case MEMORY -> new InMemoryVehicleRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    public static ServiceRepository getServiceRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            case MEMORY -> new InMemoryServiceRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    public static BookingRepository getBookingRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            case MEMORY -> new InMemoryBookingRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    public static QueueEntryRepository getQueueEntryRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            case MEMORY -> new InMemoryQueueEntryRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    public static NotificationRepository getNotificationRepository(StorageType storageType) {
        validateStorageType(storageType);
        return switch (storageType) {
            case MEMORY -> new InMemoryNotificationRepository();
            default -> unsupportedStorageType(storageType);
        };
    }

    private static void validateStorageType(StorageType storageType) {
        if (storageType == null) {
            throw new IllegalArgumentException("Storage type cannot be null");
        }
    }

    private static <T> T unsupportedStorageType(StorageType storageType) {
        throw new UnsupportedOperationException("Storage type is not implemented yet: " + storageType);
    }
}
