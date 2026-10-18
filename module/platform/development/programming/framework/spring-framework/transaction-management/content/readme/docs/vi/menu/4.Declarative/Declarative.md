<a id="back-to-top"></a>

# Khai báo ranh giới transaction

## Menu
- [Mô hình declarative transaction](#transaction-declarative-model)
- [Bật transaction management bằng annotation](#transaction-enable-management)
- [Transactional metadata và cách phân giải attribute](#transactional-metadata)
- [Luồng thực thi của TransactionInterceptor](#transaction-interceptor)
- [Ranh giới interception của proxy mode](#transaction-proxy-boundary)
- [Proxy mode và AspectJ mode](#transaction-aspectj-mode)
- [Độ ưu tiên metadata và định danh transaction manager](#transaction-metadata-precedence)

## <a id="transaction-declarative-model">Mô hình declarative transaction</a>

<details>
<summary>Xem chi tiết</summary>

Declarative transaction management tách **transaction policy** khỏi thân business method. Thay vì tự gọi begin/commit/rollback, ứng dụng khai báo metadata và để Spring bọc lời gọi method phù hợp bằng transaction advice.

```text
bên gọi
  ↓
transaction interceptor/advice
  ↓ begin/join transaction
business method đích
  ↓
commit hoặc rollback theo kết quả/policy
```

Mô hình này giúp policy nhất quán khi nhiều service method có transaction rule ổn định. Method vẫn sở hữu hành vi nghiệp vụ; hạ tầng Spring sở hữu vòng đời transaction bao quanh nó.

Declarative không có nghĩa là “phép thuật”. Muốn chẩn đoán vì sao method có hoặc không chạy trong transaction, phải hiểu cách phân giải metadata, ranh giới proxy và cách chọn manager.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-enable-management">Bật transaction management bằng annotation</a>

<details>
<summary>Xem chi tiết</summary>

`@Transactional` chỉ là metadata; tự nó không kích hoạt transaction interception. Trong cấu hình Java, `@EnableTransactionManagement` import hạ tầng để tìm transactional metadata và đăng ký advisor/interceptor áp dụng metadata đó lên Spring bean.

```java
@Configuration
@EnableTransactionManagement
class TxConfig {
    @Bean
    PlatformTransactionManager transactionManager(...) { ... }
}
```

Application context vẫn phải có `TransactionManager` phù hợp. Nếu có nhiều manager, cấu hình hoặc metadata transaction phải xác định manager nào được dùng.

`@EnableTransactionManagement` mặc định dùng `AdviceMode.PROXY`. `proxyTargetClass` điều khiển proxy dựa trên interface hay class trong proxy mode, còn `order` điều khiển thứ tự advisor. Cơ chế proxy chuyên sâu thuộc AOP module; ở đây cần nhớ annotation chỉ có hiệu lực lúc chạy khi hạ tầng transaction đã được bật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactional-metadata">Transactional metadata và cách phân giải attribute</a>

<details>
<summary>Xem chi tiết</summary>

Spring đọc `@Transactional` qua `TransactionAttributeSource`. Với annotation của Spring, `AnnotationTransactionAttributeSource` phân tích metadata thành mô hình `TransactionAttribute` nội bộ, cuối cùng biểu diễn transaction rule theo dạng rule-based attribute.

Metadata có thể đặt ở class hoặc method. Annotation ở class cung cấp giá trị mặc định cho các method phù hợp; khai báo ở method có thể ghi đè giá trị mặc định cho chính method đó.

Nên ưu tiên annotation trên class/method cụ thể. Annotation trên interface có thể hoạt động với hạ tầng proxy của Spring, nhưng AspectJ weaving không kế thừa annotation trên Java interface theo cùng cách, khiến hành vi có thể phụ thuộc interception mode.

Điểm thực tế: transaction policy được phân giải từ metadata trên **target method/class thực tế**, sau đó transaction advice mới dùng policy đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-interceptor">Luồng thực thi của TransactionInterceptor</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionInterceptor` là AOP Alliance `MethodInterceptor` áp workflow transaction của Spring quanh một lời gọi. Về mặt khái niệm, nó làm bốn bước:

```text
1. resolve TransactionAttribute của method
2. chọn TransactionManager
3. tạo/tham gia transaction khi cần
4. gọi target rồi commit/rollback theo policy và kết quả
```

Workflow nền được cung cấp bởi `TransactionAspectSupport`, hỗ trợ cả imperative và reactive transaction manager khi chữ ký method và mô hình thực thi phù hợp.

Điều này giải thích vì sao hành vi phụ thuộc vào lời gọi được intercept. Nếu lời gọi không đi qua interceptor—ví dụ self-invocation thông thường trong proxy mode—transaction workflow của method bên trong không được kích hoạt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-proxy-boundary">Ranh giới interception của proxy mode</a>

<details>
<summary>Xem chi tiết</summary>

Trong proxy mode mặc định, transaction advice chỉ chạy với lời gọi **đi vào qua proxy**. Method gọi method khác bằng `this` vẫn ở bên trong target object và bypass proxy, nên `@Transactional` của inner method không được intercept độc lập.

Khả năng nhìn thấy của method cũng phụ thuộc loại proxy. Trong Spring 6.x, proxy dựa trên class có thể làm method `protected` và package-visible trở thành transactional theo mặc định. Proxy dựa trên interface yêu cầu transactional method là `public` và được expose qua interface đang được proxy. Method `private` không thể bị subclass proxy override/intercept.

```text
bên gọi bên ngoài → proxy → target method ✓ được intercept
target method → this.otherMethod()        ✗ bypass proxy
```

Không nên “sửa” self-invocation bằng cách thêm annotation. Hãy chuyển boundary sang bean khác, tái cấu trúc lời gọi hoặc chủ động chọn chiến lược interception khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-aspectj-mode">Proxy mode và AspectJ mode</a>

<details>
<summary>Xem chi tiết</summary>

`AdviceMode.ASPECTJ` là lựa chọn thay cho transaction interception dựa trên proxy. Thay vì bắt bên gọi đi qua proxy, AspectJ transaction aspect của Spring weave bytecode của target class, vì vậy self-invocation cũng có thể được áp advice.

Đánh đổi là hạ tầng phức tạp hơn: AspectJ mode cần `spring-aspects` và compile-time hoặc load-time weaving. Nó cũng thay đổi mô hình interception mà lập trình viên phải hiểu.

Chỉ dùng AspectJ mode khi ứng dụng thực sự cần transaction semantics qua weaving, không nên coi đây là phản xạ đầu tiên cho mọi vấn đề self-invocation. Nhiều trường hợp service boundary rõ hơn hoặc tách bean hợp lý sẽ đơn giản hơn.

Cơ chế weaving/pointcut chuyên sâu thuộc AOP module; transaction-management chỉ cần hiểu lựa chọn interception làm thay đổi phạm vi transaction có thể được áp dụng như thế nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-metadata-precedence">Độ ưu tiên metadata và định danh transaction manager</a>

<details>
<summary>Xem chi tiết</summary>

Spring ưu tiên transaction metadata cụ thể nhất áp dụng cho lời gọi. `@Transactional` ở method của target class có thể ghi đè mặc định ở class; metadata trên target class/method được ưu tiên hơn khai báo kém cụ thể hơn theo quy tắc phân giải transaction attribute của Spring.

Việc chọn manager cũng là một phần của policy có hiệu lực. `@Transactional(transactionManager = "ordersTxManager")` chọn manager theo qualifier value hoặc bean name. Composed annotation cũng có thể đóng gói transaction semantics lặp lại cho một domain policy.

Nên giữ độ ưu tiên dễ đọc. Một class có mặc định rõ ràng và vài method ghi đè có chủ ý dễ hiểu hơn nhiều lớp annotation kế thừa/composed dùng manager và rollback rule khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
