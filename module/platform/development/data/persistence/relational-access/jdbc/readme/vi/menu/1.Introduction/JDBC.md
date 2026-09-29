# JDBC

## <a id="jdbc-what">1. JDBC là gì?</a>

JDBC (Java Database Connectivity) là API chuẩn của Java để application giao tiếp với relational database thông qua database driver.

JDBC không phải database, không phải ORM và cũng không tự động map object graph. Nó cung cấp abstraction cho connection, statement, parameter binding, result set và transaction boundary ở mức thấp.

## <a id="jdbc-why">2. Tại sao JDBC tồn tại?</a>

Mỗi database có protocol và driver riêng. Nếu Java application phải làm việc trực tiếp với từng vendor API, code data access sẽ phụ thuộc mạnh vào database cụ thể.

JDBC cung cấp một contract chung để application có thể làm việc với nhiều relational database thông qua driver tương ứng.

## <a id="jdbc-before">3. Nếu không có JDBC thì sao?</a>

Application vẫn có thể dùng native protocol hoặc vendor library trực tiếp, nhưng portability, resource management và API consistency sẽ khó hơn.

JDBC chuẩn hóa phần giao tiếp phổ biến để higher-level library/framework có thể xây trên cùng một nền tảng.

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

Flow thường gặp:

```text
DataSource / DriverManager
→ Connection
→ PreparedStatement
→ ResultSet
→ map result
→ close resources
```

## <a id="jdbc-boundary">5. Boundary</a>

Module này sở hữu JDBC ở góc nhìn **application-side persistence API**.

Database engine, index, query planner và storage internals thuộc `infrastructure/system/database`. Spring JDBC hoặc Spring Data integration thuộc Spring-specific modules.
