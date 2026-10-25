# 📂 README MODULE STRUCTURE (EN)

* **1.Introduction**
    * [DataAccess](content/readme/docs/en/menu/1.Introduction/knowledge/DataAccess.md)
* **2.DaoExceptionModel**
    * [DaoExceptionModel](content/readme/docs/en/menu/2.DaoExceptionModel/knowledge/DaoExceptionModel.md)
* **3.SpringJdbcCore**
    * [SpringJdbcCore](content/readme/docs/en/menu/3.SpringJdbcCore/knowledge/SpringJdbcCore.md)
* **4.AdvancedJdbc**
    * [AdvancedJdbc](content/readme/docs/en/menu/4.AdvancedJdbc/knowledge/AdvancedJdbc.md)
* **5.SpringR2dbcCore**
    * [SpringR2dbcCore](content/readme/docs/en/menu/5.SpringR2dbcCore/knowledge/SpringR2dbcCore.md)
* **6.ResourceTransactionParticipation**
    * [ResourceTransactionParticipation](content/readme/docs/en/menu/6.ResourceTransactionParticipation/knowledge/ResourceTransactionParticipation.md)
* **7.AbstractionChoice**
    * [AbstractionChoice](content/readme/docs/en/menu/7.AbstractionChoice/knowledge/AbstractionChoice.md)

# Spring Framework Data Access

Spring Framework Data Access is the framework-level layer for consistent relational data access on top of JDBC and R2DBC. It reduces repetitive resource-management and exception-handling code while keeping SQL, result mapping, and technology choice explicit.

## Prerequisites

Learners should already understand Java fundamentals and basic JDBC concepts. Reactive Streams and non-blocking programming are prerequisites for the R2DBC chapter rather than concepts owned by this module.

## Learning Flow

The module starts with the purpose and boundaries of Spring Data Access, then establishes Spring's common DAO and exception model. It continues through Spring JDBC, `JdbcClient`, result mapping, and advanced JDBC workflows before introducing Spring R2DBC and `DatabaseClient`. Resource lifecycle and transaction participation connect both stacks, and the final chapter builds a decision model across raw JDBC, Spring JDBC/R2DBC, Spring Data, and ORM.

## Boundaries

This module owns Spring Framework DAO support, `DataAccessException`, Spring JDBC, Spring R2DBC core, and data-access resource helpers. Plain JDBC semantics remain with the JDBC module. Repository and aggregate-mapping abstractions belong to Spring Data JDBC/R2DBC. Transaction propagation, isolation, rollback policy, and transaction demarcation belong to Spring Transaction Management. ORM entity mapping and lifecycle remain with their persistence owners.
