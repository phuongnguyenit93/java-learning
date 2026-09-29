<a id="back-to-top"></a>

# Custom Annotation-Based Aspect

## Menu
- [1. Annotation là contract](#annotation-contract)
- [2. Demo trong module](#annotation-demo)
- [3. Khi nào annotation-based pointcut hợp lý?](#annotation-vs-expression)
- [4. Kết luận](#annotation-conclusion)

Custom annotation cho phép mô tả cross-cutting behavior theo kiểu declarative.

## <a id="annotation-contract">1. Annotation là contract</a>

<details>
<summary>Click for details</summary>

Module định nghĩa:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackExecution {
    String value();
}
```

`RUNTIME` cần thiết vì Aspect đọc metadata khi application đang chạy.

`METHOD` thể hiện annotation này chỉ dùng trên method.

Một method có thể khai báo:

```java
@TrackExecution("annotation-demo")
public String executeTrackedOperation() {
    ...
}
```

Business method chỉ tuyên bố **muốn behavior gì**, còn implementation timing/tracking nằm trong Aspect.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

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

Event cho thấy Aspect đọc được cả annotation metadata lẫn method:

```text
track-before:label=annotation-demo
target:executeTrackedOperation
track-success:method=executeTrackedOperation
track-finished:elapsed-nanos=...
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-vs-expression">3. Khi nào annotation-based pointcut hợp lý?</a>

<details>
<summary>Click for details</summary>

Annotation phù hợp khi behavior có thể xem như một contract rõ ràng:

```text
@TrackExecution
@Audited
@Measured
```

Nó thường dễ đọc hơn một pointcut phụ thuộc quá sâu vào package hoặc method naming convention.

Nhưng annotation không tự đảm bảo advice sẽ chạy. Invocation vẫn phải đi qua proxy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-conclusion">4. Kết luận</a>

<details>
<summary>Click for details</summary>

Custom annotation tốt nhất khi nó biểu diễn một intent rõ ràng và Aspect thực hiện cross-cutting behavior tương ứng.

Không nên dùng annotation chỉ như một marker bí ẩn khiến method có side effect mà người đọc khó đoán.

</details>

- [Quay lại đầu trang](#back-to-top)
