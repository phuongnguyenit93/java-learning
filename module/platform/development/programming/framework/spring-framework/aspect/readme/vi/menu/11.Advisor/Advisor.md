<a id="back-to-top"></a>

# Advisor API và Programmatic Pointcut

## Menu
- [PointcutAdvisor = Pointcut + Advice; Advisor là contract rộng hơn](#advisor-mental-model)
- [Pointcut = ClassFilter + MethodMatcher](#pointcut-internals)
- [MethodMatcher tĩnh và động](#static-dynamic-pointcut)
- [Minh chứng thực thi cho Advisor API và Programmatic Pointcut](#advisor-demo)
- [Tổng hợp Advisor API và Programmatic Pointcut](#advisor-conclusion)

Chương này trả lời câu hỏi: nếu `MethodInterceptor` mô tả **làm gì**, thì thành phần nào mô tả **lớp/phương thức/invocation nào cần áp dụng hành vi đó**?

## <a id="advisor-mental-model">PointcutAdvisor = Pointcut + Advice; Advisor là contract rộng hơn</a>

<details>
<summary>Xem chi tiết</summary>

`Advisor` là contract rộng hơn của Spring AOP. Nó cung cấp `Advice`, nhưng generic Advisor **không đồng nghĩa** với "Pointcut + Advice".

Dạng dựa trên pointcut phổ biến là `PointcutAdvisor`:

```text
Advisor
└── Advice
      └── hành vi nào thuộc chuỗi proxy?

PointcutAdvisor
├── Pointcut
│     └── hành vi đó áp dụng ở đâu?
└── Advice
      └── match rồi chạy hành vi gì?
```

`DefaultPointcutAdvisor` mà module dùng là phần triển khai tổng quát quen thuộc cho cặp này.

Các loại Advisor khác có mô hình áp dụng khác. Ví dụ, `IntroductionAdvisor` mô tả introduction và những interface được bổ sung. Vì vậy mối quan hệ chính xác là:

```text
PointcutAdvisor = Pointcut + Advice
Advisor         = contract rộng hơn mang Advice/cấu hình AOP
```

### Tài liệu tham khảo

- Spring Framework Reference — [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-internals">Pointcut = ClassFilter + MethodMatcher</a>

<details>
<summary>Xem chi tiết</summary>

Ở tầng API thấp hơn của Spring AOP, `Pointcut` là một cấu trúc rõ ràng chứ không chỉ là chuỗi expression:

```text
Pointcut
├── ClassFilter
│     └── lớp đích này có phải ứng viên không?
└── MethodMatcher
      └── phương thức này có khớp trên lớp ứng viên không?
```

`ClassFilter` có thể loại sớm các kiểu đích không liên quan. `MethodMatcher` sau đó quyết định operation nào trên kiểu ứng viên đủ điều kiện.

Thử nghiệm tĩnh dùng `NameMatchMethodPointcut` và chỉ map `write`. Cùng một proxy phơi bày `read()` và `write()`, nhưng chỉ `write()` đi vào interceptor của Advisor:

```text
read()
→ pointcut false
→ chỉ target

write()
→ pointcut true
→ advice
→ target
```

`MethodMatcher#isRuntime()` trả `false`, nên việc khớp không cần nhìn argument của từng lời gọi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-dynamic-pointcut">MethodMatcher tĩnh và động</a>

<details>
<summary>Xem chi tiết</summary>

`MethodMatcher` tĩnh có thể quyết định chỉ từ phương thức và lớp đích:

```text
matches(Method, targetClass)
```

Khi `isRuntime() == false`, Spring không cần kiểm tra thêm argument ở từng lời gọi.

Matcher động có hai giai đoạn:

```text
giai đoạn tĩnh
matches(Method, targetClass)
        ↓ true
giai đoạn runtime cho từng invocation
matches(Method, targetClass, args...)
```

Giai đoạn tĩnh vẫn quan trọng. Spring trước hết xác định phương thức có khả năng khớp hay không. Chỉ khi bước đó thành công và `isRuntime()` là `true` thì kiểm tra phụ thuộc argument mới tham gia cho từng invocation.

`RuntimeArgumentPointcut` của module trước hết chọn `AdvisorTarget#writeWithMode`, sau đó yêu cầu argument đầu tiên bằng `"audit"`:

```text
writeWithMode("audit")
→ static true
→ runtime true
→ advice + target

writeWithMode("plain")
→ static true
→ runtime false
→ target chạy mà không có advice này
```

Pointcut động chỉ nên dùng khi phạm vi áp dụng thực sự phụ thuộc dữ liệu runtime. Nó thêm chi phí kiểm tra trên từng lời gọi và làm kết quả phụ thuộc argument; nếu metadata của type và method đã đủ thì matcher tĩnh đơn giản hơn.

### Tài liệu tham khảo

- Spring Framework Reference — [Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advisor-demo">Minh chứng thực thi cho Advisor API và Programmatic Pointcut</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
AdvisorController#observeAdvisor()
```

Endpoint:

```text
GET /aop/advanced/advisor/observe
```

Phản hồi có hai thử nghiệm con.

### Static `NameMatchMethodPointcut`

Dữ kiện:

```text
staticNameMatchPointcut.methodMatcherIsRuntime = false
```

Events:

```text
target:read
advisor-before:write
target:write
advisor-after:write
```

`read()` là baseline chứng minh proxy tồn tại nhưng Advisor không bắt buộc intercept mọi method.

### Dynamic `RuntimeArgumentPointcut`

Các dữ kiện được lấy trực tiếp từ `ClassFilter` và `MethodMatcher`:

```text
classFilterMatchesAdvisorTarget = true
staticMethodMatch              = true
methodMatcherIsRuntime         = true
runtimeMatchAudit              = true
runtimeMatchPlain              = false
```

Events:

```text
dynamic-advisor-before:writeWithMode:mode=audit
target:writeWithMode:mode=audit
dynamic-advisor-after:writeWithMode:mode=audit
target:writeWithMode:mode=plain
```

Invocation `plain` vẫn tới target nhưng không có `dynamic-advisor-*`, chứng minh runtime matcher đã từ chối invocation sau khi static method match thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advisor-conclusion">Tổng hợp Advisor API và Programmatic Pointcut</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình low-level cần giữ là:

```text
Advice
→ hành vi

Pointcut
→ ClassFilter + MethodMatcher

PointcutAdvisor
→ Pointcut + Advice

Advisor
→ contract rộng hơn luôn cung cấp Advice;
  điều kiện áp dụng phụ thuộc loại Advisor cụ thể
```

Phong cách `@AspectJ` giúp khai báo thuận tiện hơn, nhưng Spring vẫn xây các Advisor/interceptor thấp hơn để proxy biết hành vi nào phải có trong chuỗi của mỗi invocation.

</details>

- [Quay lại đầu trang](#back-to-top)
