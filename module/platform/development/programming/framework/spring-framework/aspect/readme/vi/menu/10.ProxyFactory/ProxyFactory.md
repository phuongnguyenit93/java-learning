<a id="back-to-top"></a>

# ProxyFactory và MethodInterceptor

## Menu
- [1. Từ annotation đến hạ tầng proxy](#proxy-factory-mental-model)
- [2. JDK Dynamic Proxy và CGLIB trên cùng một target](#jdk-vs-cglib)
- [3. Demo trong module](#proxy-factory-demo)
- [4. Kết luận](#proxy-factory-conclusion)

Chương này mở trực tiếp tầng proxy thay vì chỉ nhìn Spring AOP qua `@Aspect`.

## <a id="proxy-factory-mental-model">1. Từ annotation đến hạ tầng proxy</a>

<details>
<summary>Xem chi tiết</summary>

Các chương trước dùng Aspect khai báo và để container tự tạo proxy. `ProxyFactory` mở trực tiếp mô hình thấp hơn đó bằng mã:

```text
Target
  +
Advice / Advisor
  +
cấu hình proxy
  ↓
ProxyFactory
  ↓
AOP Proxy
```

`MethodInterceptor` là một advice kiểu around theo AOP Alliance:

```java
public Object invoke(MethodInvocation invocation) throws Throwable {
    before();
    try {
        return invocation.proceed();
    } finally {
        after();
    }
}
```

`MethodInvocation.proceed()` tiếp tục phần còn lại của chuỗi interceptor rồi cuối cùng gọi đối tượng đích. Interceptor cũng có thể chặn chuỗi, gọi `proceed()` nhiều lần, biến đổi kết quả hoặc thay đổi cách exception đi ra ngoài. Chỉ nên dùng những quyền này khi contract cross-cutting thực sự yêu cầu.

`ProxyFactory#addAdvice(...)` thêm hành vi mà không kèm pointcut từ chính lời gọi đó. `addAdvisor(...)` là đường phù hợp khi cần ghép hành vi với metadata về phạm vi áp dụng.

Tạo proxy bằng mã hữu ích cho hạ tầng và thử nghiệm. Với bean ứng dụng thông thường, container auto-proxy thường là cách tự nhiên hơn.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — [`ProxyFactory`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aop/framework/ProxyFactory.html)
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-vs-cglib">2. JDK Dynamic Proxy và CGLIB trên cùng một target</a>

<details>
<summary>Xem chi tiết</summary>

Thử nghiệm giữ nguyên target và interceptor, chỉ thay chiến lược proxy.

Với JDK proxy:

```java
factory.setInterfaces(GreetingOperations.class);
factory.setProxyTargetClass(false);
```

bên gọi nhìn thấy các interface đã cấu hình. Proxy triển khai `GreetingOperations` nhưng không phải subclass của `GreetingTarget`:

```text
proxyIsGreetingOperations = true
proxyIsGreetingTarget     = false
```

Vì vậy một phương thức chỉ tồn tại trên `GreetingTarget` không thuộc contract interface của JDK proxy.

Với class-based proxy:

```java
factory.setProxyTargetClass(true);
```

Spring tạo một subclass runtime của `GreetingTarget`. Bề mặt concrete type vì thế vẫn nhìn thấy qua proxy, nên demo có thể gọi `targetOnlyCapability()`.

Điều đó không làm class-based proxy tốt hơn trong mọi trường hợp. Subclassing có giới hạn riêng: lớp `final` không thể được subclass; phương thức `final` hoặc `private` không thể được override; phương thức không nhìn thấy từ subclass sinh ra cũng không thể được intercept.

Khác biệt này cũng giải thích `this(...)` và `target(...)` trong pointcut. `this(...)` quan sát đối tượng proxy, còn `target(...)` quan sát đối tượng đích phía sau proxy. Với JDK proxy, hai kiểu này có thể khác nhau rõ rệt.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-factory-demo">3. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
ProgrammaticProxyController#compareProxyFactoryStrategies()
```

Endpoint:

```text
GET /aop/advanced/proxy-factory/compare
```

Service:

```text
ProgrammaticProxyService#compareProxyStrategies()
```

Interceptor:

```text
TracingMethodInterceptor#invoke(...)
```

Cả hai proxy đều tạo event cho `greet(...)`:

```text
interceptor-before:greet
target:greet:...
interceptor-after:greet
```

Nhưng trường `facts` cho thấy một đối tượng là JDK dynamic proxy và đối tượng còn lại là CGLIB proxy.

Nhánh CGLIB còn có thêm:

```text
interceptor-before:targetOnlyCapability
target:targetOnlyCapability
interceptor-after:targetOnlyCapability
```

Trong khi JDK proxy không expose method đó qua `GreetingOperations`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-factory-conclusion">4. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

`ProxyFactory` làm rõ runtime của Spring AOP: chiến lược proxy, target, advice và Advisor đều là các đối tượng cấu hình tường minh thay vì chỉ nhìn thấy annotation và để container che phần còn lại.

Chương tiếp theo bổ sung thành phần mô tả phạm vi áp dụng. `Advice` mô tả hành vi; một Advisor dựa trên pointcut ghép hành vi đó với các lớp và phương thức nơi nó cần chạy.
</details>

- [Quay lại đầu trang](#back-to-top)
