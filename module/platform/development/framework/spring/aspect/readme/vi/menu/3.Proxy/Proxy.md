<a id="back-to-top"></a>

# Spring AOP Proxy Mental Model

## Menu
- [1. Caller không nhất thiết gọi target trực tiếp](#proxy-mental-model)
- [2. Demo trong module](#proxy-demo)
- [3. Spring-managed bean và object tạo bằng new](#managed-vs-new-demo)
- [4. JDK Dynamic Proxy và CGLIB](#proxy-strategies)
- [5. Kết luận](#proxy-conclusion)

Proxy boundary là nền tảng để hiểu hầu hết behavior của Spring AOP.

## <a id="proxy-mental-model">1. Caller không nhất thiết gọi target trực tiếp</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
Caller
  ↓
Spring AOP Proxy
  ↓
Advice chain
  ↓
Target Object
```

Khi một bean cần AOP, Spring thường đưa cho consumer một proxy thay vì reference trực tiếp tới target object.

Proxy có thể được tạo theo các chiến lược khác nhau như JDK dynamic proxy hoặc class-based proxy. Không nên hardcode assumption rằng mọi bean luôn dùng cùng một loại proxy.

Điều quan trọng hơn loại proxy là:

```text
invocation có đi qua proxy hay không?
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-demo">2. Demo trong module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
ProxyMentalModelController#inspectProxy()
```

Endpoint:

```text
GET /aop/proxy/inspect
```

Target:

```text
ProxyMentalModelService#invokeTarget()
```

Aspect:

```text
ProxyMentalModelAspect#observeProxyBoundary(...)
```

Response `facts` cho biết:

```text
runtimeClass
targetClass
isAopProxy
isCglibProxy
isJdkDynamicProxy
```

Response `events` cho thấy call chain:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

Không cần cố đoán tên generated proxy class. Hãy dùng `AopUtils` để kiểm tra semantics thay vì phụ thuộc tên class runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-vs-new-demo">3. Spring-managed bean và object tạo bằng new</a>

<details>
<summary>Click for details</summary>

Controller:

```text
ProxyMentalModelController#compareManagedBeanAndPlainObject()
```

Endpoint:

```text
GET /aop/proxy/managed-vs-new
```

Experiment gọi cùng một method theo hai đường:

```text
Spring-managed bean
→ AOP proxy
→ advice
→ target

new ProxyMentalModelService(...)
→ plain Java object
→ target trực tiếp
```

Nhánh managed có event:

```text
proxy-boundary:before-target
target:invokeTarget
proxy-boundary:after-target
```

Nhánh `new` chỉ có:

```text
target:invokeTarget
```

`facts` còn cho thấy:

```text
managedIsAopProxy = true
plainIsAopProxy   = false
```

**Kết luận:** pointcut expression đúng vẫn chưa đủ. Object phải nằm trong Spring AOP proxy boundary thì advice mới có cơ hội tham gia invocation.

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-strategies">4. JDK Dynamic Proxy và CGLIB</a>

<details>
<summary>Click for details</summary>

Hai chiến lược proxy cần phân biệt:

```text
JDK Dynamic Proxy
→ proxy dựa trên interface

Class-based / CGLIB Proxy
→ proxy là subclass của target class
```

Ở fundamentals chỉ cần biết strategy ảnh hưởng type surface và limitation của proxy.

Experiment tạo **cả hai strategy bằng code** nằm ở:

```text
readme/vi/menu/11.ProxyFactory/ProxyFactory.md
ProgrammaticProxyController#compareProxyFactoryStrategies()
```

---

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-conclusion">5. Kết luận</a>

<details>
<summary>Click for details</summary>

Annotation hoặc pointcut tự nó chưa đủ để advice chạy.

Mental model đúng là:

```text
method call
→ đi qua Spring proxy
→ pointcut match
→ advice có cơ hội chạy
```

Chapter self-invocation sau này sẽ chứng minh trường hợp method có annotation nhưng call không quay lại qua proxy.

</details>

- [Quay lại đầu trang](#back-to-top)
