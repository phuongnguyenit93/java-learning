# Jakarta Persistence (JPA)

## <a id="jpa-what">1. JPA là gì?</a>

JPA là tên quen thuộc của Jakarta Persistence specification: một specification định nghĩa contract cho object-relational persistence trong hệ sinh thái Java/Jakarta.

JPA **không phải một ORM implementation cụ thể**. Nó định nghĩa API và semantics; provider như Hibernate có thể implement specification đó.

## <a id="jpa-why">2. Tại sao JPA tồn tại?</a>

Nếu application phụ thuộc trực tiếp vào API riêng của từng ORM provider, entity lifecycle, query API và persistence behavior sẽ bị khóa mạnh vào implementation.

JPA tạo một contract chuẩn cho các concept như entity, persistence context, EntityManager, mapping, query và lifecycle.

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

## <a id="jpa-provider">4. Specification và provider</a>

```text
JPA specification
      ↓ implemented by
Hibernate / other provider
```

Học JPA cần tách rõ behavior được specification đảm bảo và extension/behavior riêng của provider.

## <a id="jpa-boundary">5. Boundary</a>

Module này không sở hữu Spring Data JPA. Repository abstraction, `JpaRepository` và Spring integration thuộc `programming/framework/spring-data/jpa`.

Hibernate-specific API và internals thuộc sibling module `persistence/orm/hibernate`.
