<a id="back-to-top"></a>

# Mô hình DAO và Data Access Exception

## Menu
- [Vì sao Spring dùng mô hình exception nhất quán cho data access?](#data-access-exception-model)
- [Các nhóm DataAccessException quan trọng](#data-access-exception-categories)
- [Chuyển đổi exception trong JDBC và R2DBC](#jdbc-r2dbc-exception-translation)
- [@Repository và cơ chế persistence exception translation](#repository-exception-translation)
- [Xử lý và lan truyền lỗi data access](#data-access-failure-handling)

## <a id="data-access-exception-model">Vì sao Spring dùng mô hình exception nhất quán cho data access?</a>

<details>
<summary>Xem chi tiết</summary>

Các công nghệ persistence phát sinh lỗi bằng những kiểu khác nhau: JDBC chủ yếu dùng SQLException, R2DBC dùng R2dbcException, còn ORM có thêm hệ exception riêng. Nếu service phụ thuộc trực tiếp vào các kiểu này, công nghệ persistence sẽ rò lên tầng trên và bên gọi phải xử lý nhiều loại lỗi có cùng ý nghĩa.

Spring dùng hệ phân cấp DataAccessException dạng unchecked để mô tả lỗi theo **ý nghĩa data access**, không theo một driver API cụ thể. Spring JDBC, Spring R2DBC và các tích hợp được hỗ trợ sẽ chuyển native exception sang từ vựng chung đó.

Unchecked không có nghĩa là bỏ qua lỗi. Nó chỉ tránh việc mọi tầng đều phải catch hoặc declare database exception. Nơi nào thật sự có chính sách hữu ích thì xử lý subtype phù hợp; nếu không, để exception đi tiếp tới transaction/ranh giới xử lý lỗi của ứng dụng.

Nguyên nhân gốc vẫn được giữ lại, nên log và debugging vẫn xem được thông tin vendor. Translation bổ sung phân loại độc lập công nghệ chứ không xóa bằng chứng ở tầng thấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-access-exception-categories">Các nhóm DataAccessException quan trọng</a>

<details>
<summary>Xem chi tiết</summary>

DataAccessException là một hierarchy có chủ đích. Nhờ đó ứng dụng có thể phân biệt lỗi có khả năng thành công khi thử lại, lỗi integrity, lỗi lock hay lỗi do số lượng kết quả không đúng kỳ vọng.

Một số nhóm quan trọng:

- TransientDataAccessException: cùng một thao tác có thể thành công khi thử lại, ví dụ một số lỗi lock/tài nguyên.
- NonTransientDataAccessException: thử lại nguyên xi thường không sửa được nguyên nhân.
- RecoverableDataAccessException: có thể phục hồi nếu thực hiện một bước recovery rõ ràng.
- DataIntegrityViolationException: lỗi vi phạm integrity; DuplicateKeyException là trường hợp cụ thể hơn.
- PessimisticLockingFailureException và CannotAcquireLockException: nhóm lỗi liên quan locking.
- QueryTimeoutException: thao tác vượt timeout được cấu hình.
- IncorrectResultSizeDataAccessException: số row thực tế không phù hợp với contract mà bên gọi yêu cầu.

Không nên catch subtype cực kỳ cụ thể chỉ vì nó tồn tại. Chỉ xử lý subtype khi ứng dụng có chính sách thực sự cho ý nghĩa đó; phần chẩn đoán vendor vẫn dựa vào log và nguyên nhân gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdbc-r2dbc-exception-translation">Chuyển đổi exception trong JDBC và R2DBC</a>

<details>
<summary>Xem chi tiết</summary>

Với JDBC, SQLExceptionTranslator là strategy chuyển SQLException thành DataAccessException. Từ Spring Framework 6.0, đường mặc định dùng SQLExceptionSubclassTranslator để nhận diện các JDBC 4 SQLException subtype rồi fallback sang SQL-state analysis. Khi cần độ chính xác theo vendor, vẫn có thể dùng hoặc tùy biến SQLErrorCodeSQLExceptionTranslator.

JdbcTemplate áp translation quanh JDBC quy trình, nên bên gọi thông thường nhận Spring exception thay vì SQLException thô.

Với R2DBC, Spring chuyển R2dbcException sang cùng hệ phân cấp DAO. DatabaseClient thực hiện translation trong quy trình chuẩn; ConnectionFactoryUtils cung cấp tiện ích tương ứng ở tầng thấp hơn.

~~~text
native driver exception
        ↓
Spring translator
        ↓
DataAccessException subtype
        ↓
ranh giới service/ứng dụng
~~~

Translation là bước phân loại, không đảm bảo mọi vendor cho cùng mức chi tiết. Khi debug hành vi đặc thù database, cần xem nguyên nhân gốc; tránh gắn business logic trực tiếp với vendor error code trừ khi chủ động chấp nhận mất portability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-exception-translation">@Repository và cơ chế persistence exception translation</a>

<details>
<summary>Xem chi tiết</summary>

@Repository có hai vai trò: stereotype cho persistence component và dấu hiệu để exception-translation infrastructure nhận diện bean phù hợp. Bản thân annotation **không tự catch hay translate exception**.

Khi PersistenceExceptionTranslationPostProcessor được đăng ký, nó áp PersistenceExceptionTranslationAdvisor lên bean @Repository đủ điều kiện. Advisor sử dụng các PersistenceExceptionTranslator mà container phát hiện để chuyển native persistence exception được hỗ trợ thành DataAccessException.

Cơ chế này đặc biệt hữu ích ở ranh giới với ORM hoặc persistence tích hợp khác. Spring JDBC và DatabaseClient đã tự translate exception trong quy trình chuẩn; @Repository không phải nguyên nhân khiến JdbcTemplate có exception translation.

~~~text
@Repository
    → đánh dấu persistence bean

PersistenceExceptionTranslationPostProcessor
    → gắn advisor/proxy khi phù hợp

PersistenceExceptionTranslator
    → chuyển lỗi persistence gốc
~~~

Đây là một ranh giới tích hợp. Chi tiết ORM mapping và AOP proxy mechanics sâu hơn thuộc module tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-access-failure-handling">Xử lý và lan truyền lỗi data access</a>

<details>
<summary>Xem chi tiết</summary>

Chỉ nên xử lý data-access exception ở nơi ứng dụng có thể đưa ra quyết định tốt hơn việc "log rồi wrap lại". Ví dụ: chuyển duplicate business key thành domain conflict, áp chính sách thử lại có kiểm soát cho lỗi lock phù hợp, hoặc chuyển cardinality condition đã dự kiến thành kết quả của use case.

Tránh catch rộng rồi bọc thành RuntimeException không thêm ý nghĩa:

~~~java
try {
    repository.save(value);
}
catch (DataAccessException ex) {
    throw new RuntimeException("database failed", ex);
}
~~~

Nếu business contract rõ ràng, có thể map subtype cụ thể:

~~~java
try {
    jdbcClient.sql(sql).params(params).update();
}
catch (DuplicateKeyException ex) {
    throw new CustomerAlreadyExists(customerId, ex);
}
~~~

Không được suy ra mọi DataIntegrityViolationException đều là duplicate key. Cũng không thử lại mù mọi TransientDataAccessException; việc thử lại cần tính idempotent, backoff và giới hạn số lần.

Giá trị của mô hình exception chung là giúp tầng trên suy luận theo **ý nghĩa lỗi**, trong khi tầng hạ tầng vẫn giữ nguyên nhân gốc để chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)
