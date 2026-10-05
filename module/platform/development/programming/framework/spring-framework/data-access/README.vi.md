# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [DataAccess](readme/vi/menu/1.Introduction/DataAccess.md)
* **2.DaoExceptionModel**
    * [DaoExceptionModel](readme/vi/menu/2.DaoExceptionModel/DaoExceptionModel.md)
* **3.SpringJdbcCore**
    * [SpringJdbcCore](readme/vi/menu/3.SpringJdbcCore/SpringJdbcCore.md)
* **4.AdvancedJdbc**
    * [AdvancedJdbc](readme/vi/menu/4.AdvancedJdbc/AdvancedJdbc.md)
* **5.SpringR2dbcCore**
    * [SpringR2dbcCore](readme/vi/menu/5.SpringR2dbcCore/SpringR2dbcCore.md)
* **6.ResourceTransactionParticipation**
    * [ResourceTransactionParticipation](readme/vi/menu/6.ResourceTransactionParticipation/ResourceTransactionParticipation.md)
* **7.AbstractionChoice**
    * [AbstractionChoice](readme/vi/menu/7.AbstractionChoice/AbstractionChoice.md)

# Spring Framework Data Access

Spring Framework Data Access là lớp hạ tầng cấp framework cho truy cập dữ liệu quan hệ trên JDBC và R2DBC. Nó giảm mã lặp về quản lý tài nguyên và xử lý exception nhưng vẫn giữ SQL, ánh xạ kết quả và lựa chọn công nghệ ở trạng thái rõ ràng.

## Kiến thức cần có

Người học nên nắm Java nền tảng và các khái niệm JDBC cơ bản. Reactive Streams và mô hình non-blocking là kiến thức nền cho chương R2DBC, không phải nội dung do module này sở hữu.

## Luồng học

Module bắt đầu từ mục đích và ranh giới của Spring Data Access, sau đó xây dựng mô hình DAO và exception chung của Spring. Tiếp theo là Spring JDBC, `JdbcClient`, ánh xạ kết quả và các quy trình JDBC nâng cao trước khi chuyển sang Spring R2DBC và `DatabaseClient`. Phần vòng đời tài nguyên và tham gia transaction nối hai hướng truy cập lại với nhau; chương cuối cùng tổng hợp cách lựa chọn giữa JDBC thuần, Spring JDBC/R2DBC, Spring Data và ORM.

## Ranh giới

Module này sở hữu Spring Framework DAO support, `DataAccessException`, Spring JDBC, Spring R2DBC core và các tiện ích quản lý tài nguyên data access. Ngữ nghĩa JDBC thuần thuộc module JDBC. Repository và aggregate mapping thuộc Spring Data JDBC/R2DBC. Transaction propagation, isolation, chính sách rollback và transaction demarcation thuộc Spring Transaction Management. ORM entity mapping và vòng đời thuộc các module persistence tương ứng.
