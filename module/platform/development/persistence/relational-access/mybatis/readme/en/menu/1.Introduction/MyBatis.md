# MyBatis

## <a id="mybatis-what">1. What is MyBatis?</a>

MyBatis is a data-mapper framework that lets applications keep explicit control over SQL while reducing JDBC boilerplate for parameter binding and result-set-to-object mapping.

MyBatis is **not a full ORM in the same sense as Hibernate**. SQL remains a central part of the design.

## <a id="mybatis-why">2. Why does MyBatis exist?</a>

JDBC provides strong SQL control but requires repetitive code around statements, parameters, result mapping, and resource handling.

MyBatis keeps SQL under developer control while automating repetitive mapping and orchestration.

## <a id="mybatis-model">3. Mental model</a>

```text
application method
      ↓
mapper
      ↓
explicit SQL
      ↓
JDBC / driver
      ↓
database
      ↓
result mapping
```

## <a id="mybatis-vs-orm">4. How is MyBatis different from ORM?</a>

ORM systems usually make the entity or object model the central abstraction and generate or manage SQL underneath it.

MyBatis keeps SQL visible. Developers control query shape, joins, and statements while the framework assists with mapping.

## <a id="mybatis-boundary">5. Boundary</a>

This module owns the MyBatis framework at the persistence layer. Database-side SQL semantics and query optimization still belong to the corresponding database curriculum.
