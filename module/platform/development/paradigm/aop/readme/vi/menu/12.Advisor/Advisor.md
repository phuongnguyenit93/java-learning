<a id="back-to-top"></a>

# Advanced - Advisor và Programmatic Pointcut

## Menu
- [1. Advisor = Pointcut + Advice](#advisor-mental-model)
- [2. Pointcut = ClassFilter + MethodMatcher](#pointcut-internals)
- [3. Static và dynamic MethodMatcher](#static-dynamic-pointcut)
- [4. Demo trong module](#advisor-demo)
- [5. Kết luận](#advisor-conclusion)

Chapter này trả lời câu hỏi: nếu `MethodInterceptor` mô tả **làm gì**, thì object nào mô tả **class/method/invocation nào cần áp dụng behavior đó**?

## <a id="advisor-mental-model">1. Advisor = Pointcut + Advice</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
Advisor
├── Pointcut
│     └── invocation nào match?
│
└── Advice / MethodInterceptor
      └── match rồi làm gì?
```

Trong annotation style, hai phần thường xuất hiện cạnh nhau:

```java
@Around("execution(...)")
public Object around(...) { ... }
```

Ở Spring AOP API thấp hơn, chúng có thể là hai object độc lập rồi được ghép bởi `Advisor`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-internals">2. Pointcut = ClassFilter + MethodMatcher</a>

<details>
<summary>Click for details</summary>

Ở Spring AOP API thấp hơn, `Pointcut` không chỉ là một expression string. Mental model cốt lõi là:

```text
Pointcut
├── ClassFilter
│     └── target class này có phải candidate không?
│
└── MethodMatcher
      └── method này có match không?
```

`ClassFilter` loại sớm những type không liên quan. `MethodMatcher` quyết định method nào trên candidate type được chọn.

Experiment static đầu tiên dùng:

```text
NameMatchMethodPointcut
```

và chỉ map method:

```text
write
```

Target có:

```text
read()
write()
```

Vì vậy cùng một proxy nhưng chỉ `write()` đi qua advice:

```text
read()
→ pointcut false
→ target

write()
→ pointcut true
→ advice
→ target
```

Response đọc trực tiếp:

```text
NameMatchMethodPointcut#getMethodMatcher().isRuntime()
```

và cho kết quả `false`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-dynamic-pointcut">3. Static và dynamic MethodMatcher</a>

<details>
<summary>Click for details</summary>

Static matching chỉ cần metadata của method + target class:

```text
matches(Method, targetClass)
```

Nếu:

```text
MethodMatcher#isRuntime() == false
```

thì framework không cần runtime arguments để quyết định match cho từng invocation.

Dynamic matching có thêm bước:

```text
static phase
matches(Method, targetClass)
        ↓ true
runtime phase
matches(Method, targetClass, args...)
```

Module định nghĩa:

```text
RuntimeArgumentPointcut
```

với rule:

```text
ClassFilter
→ AdvisorTarget

static MethodMatcher
→ method name = writeWithMode

runtime MethodMatcher
→ first argument = "audit"
```

Hai invocation cùng gọi một method:

```text
writeWithMode("audit")
→ static match true
→ runtime match true
→ advice chạy

writeWithMode("plain")
→ static match true
→ runtime match false
→ target chạy nhưng advice không chạy
```

Dynamic pointcut hữu ích khi selection thật sự phụ thuộc dữ liệu runtime, nhưng nó cần thêm runtime evaluation. Nếu selection chỉ phụ thuộc type/method metadata thì static matching đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advisor-demo">4. Demo trong module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
AdvisorController#observeAdvisor()
```

Endpoint:

```text
GET /aop/advanced/advisor/observe
```

Response có hai experiment con.

### Static `NameMatchMethodPointcut`

Fact:

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

Facts được lấy từ chính `ClassFilter` và `MethodMatcher`:

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

## <a id="advisor-conclusion">5. Kết luận</a>

<details>
<summary>Click for details</summary>

`Advisor` làm rõ architecture nền:

```text
Pointcut
  ├── ClassFilter
  └── MethodMatcher
+
Advice / MethodInterceptor
```

`MethodMatcher` có thể chỉ dựa trên static metadata hoặc yêu cầu thêm runtime arguments. `@AspectJ` style giúp authoring dễ hơn, nhưng Spring AOP infrastructure vẫn có các abstraction thấp hơn để biểu diễn cùng selection rule đó.

</details>

- [Quay lại đầu trang](#back-to-top)
