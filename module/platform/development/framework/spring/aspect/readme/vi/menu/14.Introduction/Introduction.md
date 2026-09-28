<a id="back-to-top"></a>

# Advanced - Introduction và Interface Enrichment

## Menu
- [1. Introduction là gì?](#introduction-mental-model)
- [2. @DeclareParents](#declare-parents)
- [3. Demo trong module](#introduction-demo)
- [4. Kết luận](#introduction-conclusion)

Phần lớn AOP trong module tới đây chỉ thay đổi behavior **xung quanh method hiện có**. Introduction cho thấy proxy còn có thể expose **interface mới** mà target class ban đầu không implement.

## <a id="introduction-mental-model">1. Introduction là gì?</a>

<details>
<summary>Click for details</summary>

Target ban đầu:

```text
IntroductionTargetService
→ không implements UsageTracked
```

Sau khi introduction được apply:

```text
Spring AOP Proxy
├── behavior của IntroductionTargetService
└── UsageTracked
```

Caller nhìn proxy có capability rộng hơn target class gốc.

Đây không phải sửa bytecode của target class. Capability mới nằm trên **proxy type surface**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declare-parents">2. @DeclareParents</a>

<details>
<summary>Click for details</summary>

Module dùng:

```java
@DeclareParents(
    value = "...IntroductionTargetService",
    defaultImpl = DefaultUsageTracked.class
)
public static UsageTracked usageTracked;
```

`DefaultUsageTracked` cung cấp implementation cho interface được introduction.

Experiment cố ý match **exact target type** thay vì một type pattern rộng. Với learning module, boundary hẹp giúp behavior dễ dự đoán và tránh đưa các bean không liên quan vào quá trình type matching.

Introduction là capability nâng cao và ít gặp hơn logging/timing. Nó phù hợp để hiểu sức mạnh của proxy/advisor model hơn là một pattern phải dùng thường xuyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-demo">3. Demo trong module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
IntroductionController#observeIntroduction()
```

Endpoint:

```text
GET /aop/advanced/introduction/observe
```

Target:

```text
IntroductionTargetService#businessOperation()
```

Response chứng minh:

```text
targetClassImplementsUsageTracked = false
proxyImplementsUsageTracked       = true
introducedInterface               = true
```

Controller cast proxy sang:

```text
UsageTracked
```

rồi tăng counter từ `0` lên `2` trước khi gọi business method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-conclusion">4. Kết luận</a>

<details>
<summary>Click for details</summary>

Proxy-based AOP không chỉ có thể intercept method. Nó còn có thể thay đổi **interface contract mà caller nhìn thấy** thông qua introduction.

Tuy nhiên càng thêm nhiều behavior/interface ngầm qua proxy, architecture càng cần documentation rõ để tránh surprise.

</details>

- [Quay lại đầu trang](#back-to-top)
