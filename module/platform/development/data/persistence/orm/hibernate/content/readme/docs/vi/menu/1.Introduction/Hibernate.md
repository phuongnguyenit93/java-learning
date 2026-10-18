# Hibernate ORM

## <a id="hibernate-what">1. Hibernate là gì?</a>

Hibernate ORM là một ORM framework/provider cho Java. Nó có thể implement Jakarta Persistence specification đồng thời cung cấp native API và extension riêng.

Hibernate chịu trách nhiệm chuyển đổi giữa object/entity model và relational persistence operations, đồng thời quản lý nhiều behavior như persistence context, dirty checking, fetching và SQL generation.

## <a id="hibernate-why">2. Tại sao Hibernate tồn tại?</a>

JDBC cho quyền kiểm soát SQL trực tiếp nhưng application phải tự quản lý nhiều mapping và object lifecycle logic.

Hibernate cung cấp abstraction cao hơn để developer làm việc với entity/object model trong khi framework điều phối phần lớn persistence mechanics.

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

## <a id="hibernate-jpa">4. Hibernate và JPA</a>

Hibernate và JPA không đồng nghĩa:

```text
JPA
= specification

Hibernate
= implementation/provider + native features
```

Code chỉ dùng Jakarta Persistence contract có portability tốt hơn; code dùng Hibernate extension chấp nhận coupling để lấy capability riêng.

## <a id="hibernate-boundary">5. Boundary</a>

JPA contract thuộc sibling module `data/persistence/orm/jpa`. Spring Data repository abstraction thuộc `programming/framework/spring-data/jpa`.

Database engine internals và SQL optimizer thuộc `infrastructure/system/database`.
