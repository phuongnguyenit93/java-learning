<a id="back-to-top"></a>

# Pointcut dựa trên Annotation và Declarative Contract

## Menu
- [1. Annotation như một declarative contract](#annotation-contract)
- [2. Demo trong module](#annotation-demo)
- [3. Khi nào nên dùng pointcut dựa trên annotation?](#annotation-vs-expression)
- [4. Kết luận](#annotation-conclusion)

Custom annotation cho phép mô tả hành vi cắt ngang theo kiểu declarative.

## <a id="annotation-contract">1. Annotation như một declarative contract</a>

<details>
<summary>Xem chi tiết</summary>

Pointcut dựa trên annotation phù hợp khi mã ứng dụng cần tuyên bố ý định rõ ràng, thay vì chỉ được chọn ngầm qua package hoặc quy ước đặt tên.

Module định nghĩa:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackExecution {
    String value();
}
```

Hai meta-annotation này là một phần của contract:

```text
@Retention(RUNTIME)
  annotation còn tồn tại ở runtime
  advice có thể bind/đọc metadata

@Target(METHOD)
  contract này dành cho method
```

Phương thức nghiệp vụ có thể chủ động opt in:

```java
@TrackExecution("annotation-demo")
public String executeTrackedOperation() {
    ...
}
```

Bản thân annotation không chạy code đo thời gian và cũng không tạo proxy. Nó chỉ là metadata. Aspect mới cung cấp phần hiện thực:

```java
@Around("@annotation(trackExecution)")
public Object track(
        ProceedingJoinPoint joinPoint,
        TrackExecution trackExecution) throws Throwable {
    ...
}
```

Ở đây `@annotation(trackExecution)` làm hai việc: chọn method execution mang `@TrackExecution` và bind annotation instance thực tế vào parameter của advice. Advice có thể đọc `trackExecution.value()` mà không phải tự tra metadata bằng reflection.

Ta có một contract dễ đọc:

```text
target method
  khai báo ý định bằng @TrackExecution

aspect
  hiện thực chính sách dùng chung

proxy + pointcut
  nối declaration với advice ở runtime
```

Yêu cầu về proxy vẫn giữ nguyên. Metadata trên method không làm một invocation trực tiếp, không qua proxy, tự trở thành interceptable.

### Tài liệu tham khảo

- Spring Framework Reference — Declaring a Pointcut
- Spring Framework Reference — Declaring Advice

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-demo">2. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
AnnotationDrivenController#trackByAnnotation()
```

Endpoint:

```text
GET /aop/annotation/track
```

Target:

```text
AnnotationDrivenService#executeTrackedOperation()
```

Aspect:

```text
TrackingAspect#track(...)
```

Pointcut:

```text
@annotation(trackExecution)
```

Target khai báo `@TrackExecution("annotation-demo")`. Khi invocation đi qua proxy, pointcut match và advice nhận chính annotation instance đó.

Các event làm hai phần này quan sát được:

```text
track-before:label=annotation-demo
target:executeTrackedOperation
track-success:method=executeTrackedOperation
track-finished:elapsed-nanos=...
```

Label chứng minh metadata đã được bind; target event chứng minh advice tiếp tục invocation; event đo thời gian cuối cùng cho thấy aspect có thể bao quanh method mà phương thức nghiệp vụ không cần chứa code timing.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-vs-expression">3. Khi nào nên dùng pointcut dựa trên annotation?</a>

<details>
<summary>Xem chi tiết</summary>

Nên dùng annotation contract khi chính sách cắt ngang đủ quan trọng để người đọc cần thấy việc opt in ngay tại method declaration:

```text
@TrackExecution
@Audited
@Measured
```

Cách này có một số lợi ích:

- ý định hiện rõ cạnh operation;
- metadata có thể mang tham số cho chính sách;
- refactor package ít làm matching thay đổi ngoài ý muốn;
- aspect có thể bind annotation trực tiếp.

Pointcut expression dựa trên cấu trúc lại phù hợp khi chính sách thuộc một architectural boundary cần áp dụng đồng nhất mà không muốn annotate từng method, ví dụ mọi public method trong một service package.

Đánh đổi chính là mức độ hiển thị và cách chọn tập method:

```text
annotation contract
  opt in explicit tại method

structural expression
  quy tắc tập trung dựa trên type/package/signature
```

Tránh dùng marker annotation có hậu quả khó đoán hoặc có semantics phụ thuộc vào thứ tự ẩn giữa nhiều aspect. Annotation nên đặt tên cho một ý định ổn định, còn aspect tương ứng nên hiện thực một chính sách cắt ngang rõ ràng.

Cuối cùng, annotation có mặt không làm mất proxy semantics. Self-invocation hoặc direct target reference vẫn có thể bypass advice dù method đã mang annotation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-conclusion">4. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Pointcut dựa trên annotation phù hợp khi mã ứng dụng cần tuyên bố một ý định áp dụng concern cắt ngang rõ ràng.

Giữ các lớp trách nhiệm tách biệt:

```text
annotation
  metadata / ý định

pointcut
  chọn join point và có thể bind context

advice
  hiện thực chính sách

proxy
  interception boundary ở runtime
```

Cách tách này giúp contract dễ suy luận hơn và dẫn sang chapter tiếp theo, nơi lifecycle của advice quyết định chính xác mỗi hành động chạy lúc nào.

</details>

- [Quay lại đầu trang](#back-to-top)
