# JDBC

## <a id="jdbc-what">1. What is JDBC?</a>

JDBC (Java Database Connectivity) is Java's standard API for communicating with relational databases through database drivers.

JDBC is not a database, an ORM, or an automatic object-graph mapper. It provides lower-level abstractions for connections, statements, parameter binding, result sets, and transaction boundaries.

## <a id="jdbc-why">2. Why does JDBC exist?</a>

Each database has its own protocol and driver. If Java applications had to use vendor-specific APIs directly, data-access code would become tightly coupled to each database.

JDBC provides a common contract so applications can work with different relational databases through their corresponding drivers.

## <a id="jdbc-before">3. What happens without JDBC?</a>

Applications can still use native protocols or vendor libraries directly, but portability, resource management, and API consistency become harder.

JDBC standardizes common interaction patterns so higher-level libraries and frameworks can build on the same foundation.

## <a id="jdbc-model">4. Mental model</a>

```text
Java application
      ↓
JDBC API
      ↓
JDBC Driver
      ↓
Relational Database
```

Typical flow:

```text
DataSource / DriverManager
→ Connection
→ PreparedStatement
→ ResultSet
→ map result
→ close resources
```

## <a id="jdbc-boundary">5. Boundary</a>

This module owns JDBC as an **application-side persistence API**.

Database engines, indexes, query planners, and storage internals belong to `infrastructure/system/database`. Spring JDBC or Spring Data integration belongs to Spring-specific modules.
