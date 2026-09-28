# Hibernate ORM

## <a id="hibernate-what">1. What is Hibernate?</a>

Hibernate ORM is an ORM framework and provider for Java. It can implement the Jakarta Persistence specification while also providing native APIs and provider-specific extensions.

Hibernate maps between object/entity models and relational persistence operations while managing behaviors such as persistence context, dirty checking, fetching, and SQL generation.

## <a id="hibernate-why">2. Why does Hibernate exist?</a>

JDBC provides direct SQL control, but applications must manage substantial mapping and object-lifecycle logic themselves.

Hibernate provides a higher-level abstraction so developers can work with an entity/object model while the framework coordinates much of the persistence machinery.

## <a id="hibernate-model">3. Mental model</a>

```text
entity/object operations
        ↓
Hibernate Session / persistence context
        ↓
dirty checking + mapping + SQL generation
        ↓
JDBC
        ↓
database
```

## <a id="hibernate-jpa">4. Hibernate and JPA</a>

Hibernate and JPA are not the same:

```text
JPA
= specification

Hibernate
= implementation/provider + native features
```

Code using only Jakarta Persistence contracts has greater portability; code using Hibernate extensions accepts provider coupling in exchange for provider-specific capabilities.

## <a id="hibernate-boundary">5. Boundary</a>

The JPA contract belongs to the sibling `persistence/orm/jpa` module. Spring Data repository abstractions belong to `framework/spring/data/jpa`.

Database-engine internals and SQL optimizers belong to `infrastructure/system/database`.
