package com.carwash.repository.database;

import com.carwash.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUserRepositoryTest {

    private final DatabaseUserRepository repository = new DatabaseUserRepository();

    @Test
    void saveThrowsUnsupportedOperationException() {
        User user = User.withEncodedPassword("U-001", "Test User", "test@example.com", "01234", "hash", null);

        assertThrows(UnsupportedOperationException.class, () -> repository.save(user));
    }

    @Test
    void findByIdThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> repository.findById("U-001"));
    }

    @Test
    void findAllThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, repository::findAll);
    }

    @Test
    void deleteThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> repository.delete("U-001"));
    }

    @Test
    void findByEmailThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> repository.findByEmail("test@example.com"));
    }
}
