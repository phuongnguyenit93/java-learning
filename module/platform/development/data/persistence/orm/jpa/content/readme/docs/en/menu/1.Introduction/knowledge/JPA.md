# Jakarta Persistence (JPA)

## <a id="jpa-what">1. What is JPA?</a>

JPA is the familiar name for the Jakarta Persistence specification: a specification that defines contracts for object-relational persistence in the Java/Jakarta ecosystem.

JPA is **not a concrete ORM implementation**. It defines APIs and semantics; providers such as Hibernate can implement that specification.

## <a id="jpa-why">2. Why does JPA exist?</a>

If applications depend directly on each ORM provider's proprietary API, entity lifecycle, query APIs, and persistence behavior become strongly coupled to that implementation.

JPA provides a standard contract for concepts such as entities, persistence contexts, EntityManager, mappings, queries, and lifecycle.

## <a id="jpa-model">3. Mental model</a>

```text
application
      ↓
Jakarta Persistence API / semantics
      ↓
JPA Provider
      ↓
SQL / JDBC
      ↓
database
```

## <a id="jpa-provider">4. Specification and provider</a>

```text
JPA specification
      ↓ implemented by
Hibernate / other provider
```

Learning JPA requires separating behavior guaranteed by the specification from provider-specific extensions and behavior.

## <a id="jpa-boundary">5. Boundary</a>

This module does not own Spring Data JPA. Repository abstractions, `JpaRepository`, and Spring integration belong to `programming/framework/spring-data/jpa`.

Hibernate-specific APIs and internals belong to the sibling `persistence/orm/hibernate` module.
