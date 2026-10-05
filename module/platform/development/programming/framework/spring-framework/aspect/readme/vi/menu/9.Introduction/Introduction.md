<a id="back-to-top"></a>

# Introduction và mở rộng Interface

## Menu
- [1. Introduction là gì?](#introduction-mental-model)
- [2. @DeclareParents](#declare-parents)
- [3. Demo trong module](#introduction-demo)
- [4. Kết luận](#introduction-conclusion)

Phần lớn nội dung AOP trước đó thay đổi hành vi **xung quanh phương thức hiện có**. Introduction cho thấy proxy còn có thể phơi bày **interface mới** mà lớp đích ban đầu không triển khai.

## <a id="introduction-mental-model">1. Introduction là gì?</a>

<details>
<summary>Xem chi tiết</summary>

Phần lớn advice thay đổi hành vi xung quanh các phương thức đã tồn tại. Introduction làm một việc khác: nó mở rộng **kiểu mà proxy phơi bày** bằng một interface mà lớp đích ban đầu không triển khai.

```text
đối tượng đích ban đầu
IntroductionTargetService
→ không phải UsageTracked

tham chiếu đã được AOP xử lý
Spring AOP proxy
├── hành vi của IntroductionTargetService
└── contract UsageTracked
```

Lớp đích và bytecode của nó không thay đổi. Contract mới chỉ tồn tại trên proxy. Lời gọi tới các phương thức gốc vẫn đi qua chuỗi proxy/interceptor bình thường, còn phương thức thuộc interface được bổ sung sẽ do phần triển khai introduction xử lý.

Sự khác biệt này ảnh hưởng trực tiếp đến ép kiểu. Một instance thô của `IntroductionTargetService` không thể trở thành `UsageTracked` chỉ vì một Aspect khai báo introduction. Mã cần contract mới phải giữ tham chiếu proxy đã được advice.

Vì vậy, introduction cho phép bổ sung một khả năng công khai ở lớp proxy mà không bắt lớp đích phải biết về interface đó.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declare-parents">2. @DeclareParents</a>

<details>
<summary>Xem chi tiết</summary>

Module khai báo:

```java
@DeclareParents(
    value = "...IntroductionTargetService",
    defaultImpl = DefaultUsageTracked.class
)
public static UsageTracked usageTracked;
```

Mỗi thành phần có một vai trò riêng:

```text
kiểu của field: UsageTracked
→ interface được bổ sung vào các proxy phù hợp

value
→ AspectJ type pattern chọn các kiểu đích đủ điều kiện

defaultImpl: DefaultUsageTracked
→ phần triển khai của contract được bổ sung
```

Module cố ý dùng đúng kiểu đích thay vì một type pattern rộng. Ranh giới hẹp giúp contract mới dễ dự đoán và tránh việc các bean không liên quan âm thầm nhận thêm interface.

Introduction phù hợp khi proxy thực sự cần một contract phụ ổn định. Tuy nhiên, nó khó nhìn thấy hơn composition thông thường hoặc việc lớp tự `implements` interface, vì vậy nên dùng có chủ đích và ghi rõ ranh giới proxy.

### Tài liệu tham khảo

- Spring Framework Reference — [Introductions](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/introductions.html)
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-demo">3. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

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

Phản hồi của endpoint chứng minh:

```text
targetClassImplementsUsageTracked = false
proxyImplementsUsageTracked       = true
introducedInterface               = true
```

Controller cast proxy sang:

```text
UsageTracked
```

rồi tăng counter từ `0` lên `2` trước khi gọi phương thức nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="introduction-conclusion">4. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Introduction cho thấy Spring AOP không chỉ bao quanh việc thực thi phương thức. Proxy còn có thể phơi bày một interface mới trong khi lớp đích vẫn giữ nguyên.

Lợi ích là tách contract phụ khỏi lớp đích. Đánh đổi là khả năng đó chỉ tồn tại trên tham chiếu proxy, nên mã bỏ qua proxy hoặc chỉ nhìn vào concrete target type có thể hiểu sai đối tượng đang cung cấp những contract nào.
</details>

- [Quay lại đầu trang](#back-to-top)
