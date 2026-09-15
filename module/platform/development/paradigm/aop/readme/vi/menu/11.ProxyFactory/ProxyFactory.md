<a id="back-to-top"></a>

# Advanced - ProxyFactory và MethodInterceptor

## Menu
- [1. Từ annotation xuống proxy infrastructure](#proxy-factory-mental-model)
- [2. JDK Dynamic Proxy và CGLIB bằng cùng một target](#jdk-vs-cglib)
- [3. Demo trong module](#proxy-factory-demo)
- [4. Kết luận](#proxy-factory-conclusion)

Chapter này mở nắp phần proxy thay vì tiếp tục nhìn Spring AOP qua `@Aspect`.

## <a id="proxy-factory-mental-model">1. Từ annotation xuống proxy infrastructure</a>

<details>
<summary>Click for details</summary>

Ở các chapter trước ta nhìn call chain như:

```text
Caller
→ Spring AOP Proxy
→ Advice
→ Target
```

`ProxyFactory` cho phép tự tạo chain đó bằng code:

```text
Target
  +
MethodInterceptor
  ↓
ProxyFactory
  ↓
Proxy
```

`MethodInterceptor` là một advice kiểu around ở tầng thấp hơn:

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

Mental model rất gần `ProceedingJoinPoint`:

```text
MethodInvocation.proceed()
→ tiếp tục interceptor chain
→ cuối cùng tới target
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-vs-cglib">2. JDK Dynamic Proxy và CGLIB bằng cùng một target</a>

<details>
<summary>Click for details</summary>

Experiment dùng cùng:

```text
GreetingTarget
TracingMethodInterceptor
```

nhưng tạo hai proxy.

JDK proxy:

```java
factory.setInterfaces(GreetingOperations.class);
factory.setProxyTargetClass(false);
```

Caller tương tác qua interface `GreetingOperations`.

JDK proxy expose interface surface chứ không trở thành subclass của `GreetingTarget`. Vì vậy trong response:

```text
proxyIsGreetingOperations = true
proxyIsGreetingTarget     = false
targetOnlyCapabilityVisibleThroughProxyType = false
```

Fact `targetOnlyCapabilityVisibleThroughProxyType` được tính bằng cách inspect public methods trên **runtime proxy class**, không phải boolean hard-code.

CGLIB proxy:

```java
factory.setProxyTargetClass(true);
```

Proxy là subclass của `GreetingTarget`.

Vì thế concrete type surface vẫn nhìn thấy:

```text
proxyIsGreetingOperations = true
proxyIsGreetingTarget     = true
targetOnlyCapabilityVisibleThroughProxyType = true
```

Experiment còn gọi trực tiếp:

```java
cglibProxy.targetOnlyCapability()
```

để chứng minh một method chỉ tồn tại trên concrete target class vẫn nằm trên type surface của class-based proxy.

Điểm cần hiểu không phải proxy nào "tốt hơn" tuyệt đối. Strategy quyết định type surface và một số limitation của interception.

Điều này nối trực tiếp lại `this(...)` và `target(...)` ở chapter Pointcut. Với JDK proxy trong experiment:

```text
proxy object
→ implements GreetingOperations
→ không phải GreetingTarget

target object phía sau proxy
→ GreetingTarget
```

Vì vậy `this(...)` nhìn type surface của proxy, còn `target(...)` nhìn target type. Đây là lý do hai designator có thể cho kết quả khác nhau khi proxy strategy thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-factory-demo">3. Demo trong module</a>

<details>
<summary>Click for details</summary>

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

Nhưng `facts` cho thấy một object là JDK dynamic proxy và object còn lại là CGLIB proxy.

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
<summary>Click for details</summary>

`@Around` không phải cơ chế duy nhất có thể bao quanh method invocation. Ở tầng Spring AOP API, một proxy có thể được xây trực tiếp từ target + advice/interceptor.

Chapter tiếp theo thêm một abstraction quan trọng nữa: **Advisor**.

</details>

- [Quay lại đầu trang](#back-to-top)
