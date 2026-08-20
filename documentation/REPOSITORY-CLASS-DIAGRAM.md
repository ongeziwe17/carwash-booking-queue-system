# Repository Class Diagram

```mermaid
classDiagram
    class Repository~T, ID~ {
        <<interface>>
        +insert(T entity) boolean
        +update(T entity) boolean
        +findById(ID id) Optional~T~
        +findAll() List~T~
        +deleteById(ID id) boolean
    }

    class ModuleRepositoryPort {
        <<interface>>
        +deterministic module queries
    }

    class InMemoryRepositoryAdapter {
        +ConcurrentHashMap storage
    }

    class PostgresRepositoryAdapter {
        +map domain to flat JPA entity
        +translate persistence conflicts
    }

    class SpringDataRepository {
        <<infrastructure only>>
    }

    class DataTransactionOperations {
        <<interface>>
        +read(action)
        +write(action)
        +afterCommitBestEffort(action)
    }

    class InMemoryDataCoordinator {
        +fair ReentrantReadWriteLock
    }

    class PostgresTransactionOperations {
        +REQUIRED TransactionTemplate
        +REQUIRES_NEW after commit
    }

    Repository~T, ID~ <|-- ModuleRepositoryPort
    ModuleRepositoryPort <|.. InMemoryRepositoryAdapter
    ModuleRepositoryPort <|.. PostgresRepositoryAdapter
    PostgresRepositoryAdapter --> SpringDataRepository
    DataTransactionOperations <|.. InMemoryDataCoordinator
    DataTransactionOperations <|.. PostgresTransactionOperations
```

Every domain repository port stays persistence-agnostic. Each capability infrastructure package owns both its in-memory adapter and flat JPA entities/Spring Data repository/PostgreSQL adapter. Application services select adapters by profile through the composition root and never receive JPA entities, proxies, or foreign infrastructure repositories. `insert` is create-only, `update` is update-only, and reads preserve explicit deterministic ordering. The obsolete database-user stub and storage factory are removed.
