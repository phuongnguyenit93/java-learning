# MyBatis

## <a id="mybatis-what">1. MyBatis là gì?</a>

MyBatis là một data-mapper framework giúp application viết SQL chủ động nhưng giảm phần boilerplate JDBC khi binding parameter và chuyển result set thành object.

MyBatis **không phải ORM đầy đủ theo kiểu Hibernate**. SQL vẫn là một phần trung tâm của design.

## <a id="mybatis-why">2. Tại sao MyBatis tồn tại?</a>

JDBC cho quyền kiểm soát SQL rất cao nhưng yêu cầu nhiều code lặp quanh statement, parameter, result mapping và resource handling.

MyBatis giữ quyền kiểm soát SQL trong tay developer nhưng tự động hóa phần mapping và orchestration lặp lại.

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

## <a id="mybatis-vs-orm">4. MyBatis khác ORM như thế nào?</a>

ORM thường lấy entity/object model làm abstraction trung tâm rồi sinh hoặc quản lý SQL phía dưới.

MyBatis giữ SQL là abstraction hiển thị rõ. Developer chủ động query shape, join và statement, còn framework hỗ trợ mapping.

## <a id="mybatis-boundary">5. Boundary</a>

Module này sở hữu MyBatis framework ở persistence layer. Database-side SQL semantics và query optimization vẫn thuộc database curriculum tương ứng.
