package com.carwash.repository.database;

import com.carwash.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseUserRepositoryTest {

    private final DatabaseUserRepository repository = new DatabaseUserRepository();

    @Test
    void insertThrowsUnsupportedOperationException() {
        User user = User.withEncodedPassword("U-001", "Test User", "test@example.com", "01234", "hash", null);
        assertThrows(UnsupportedOperationException.class, () -> repository.insert(user));
    }

    @Test
    void updateThrowsUnsupportedOperationException() {
        User user = User.withEncodedPassword("U-001", "Test User", "test@example.com", "01234", "hash", null);
        assertThrows(UnsupportedOperationException.class, () -> repository.update(user));
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
        assertThrows(UnsupportedOperationException.class, () -> repository.deleteById("U-001"));
    }

    @Test
    void existsThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> repository.existsById("U-001"));
    }

    @Test
    void findByEmailThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> repository.findByEmail("test@example.com"));
    }
}
