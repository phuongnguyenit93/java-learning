<a id="back-to-top"></a>

# Khai báo Aspect và thiết kế Pointcut

## Menu
- [1. Chọn phong cách khai báo: @AspectJ và AOP dựa trên schema](#aop-declaration-styles)
- [2. Bật @AspectJ auto-proxy support trong Spring Framework](#spring-aop-enablement)
- [3. Pointcut đang khớp với yếu tố nào?](#pointcut-mental-model)
- [4. Các designator được hỗ trợ, cách kết hợp và runtime context](#pointcut-designators-and-composition)
- [5. Demo trong module](#pointcut-demo)
- [6. Spring AOP không hỗ trợ toàn bộ AspectJ join point model](#spring-aop-pointcut-boundary)
- [7. Kết luận](#pointcut-conclusion)

Pointcut quyết định **join point nào được chọn**.

## <a id="aop-declaration-styles">1. Chọn phong cách khai báo: @AspectJ và AOP dựa trên schema</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework cung cấp hai phong cách khai báo cấp cao trên cùng proxy-based AOP infrastructure.

**@AspectJ style** dùng Java class thông thường với AspectJ annotation:

```java
@Aspect
@Component
class AuditAspect {

    @Before("execution(* com.example.order..*(..))")
    void audit() {
        // hành vi cắt ngang
    }
}
```

**Schema-based AOP** khai báo aspect, pointcut và advice qua Spring XML `aop` namespace.

Hai style khác nhau ở syntax cấu hình nhưng dùng chung runtime model:

```text
declaration
→ Spring tạo advisor/interceptor
→ object phù hợp được proxy
→ method call đi qua proxy có thể được advise
```

Dùng @AspectJ style không tự kích hoạt AspectJ compiler hay load-time weaving. Dùng XML cũng không tạo ra một AOP engine riêng. Cả hai vẫn là Spring AOP trừ khi full AspectJ weaving được cấu hình explicit.

Với Java configuration thông thường, @AspectJ style thường dễ đặt gần code mà nó mô tả. Schema-based AOP vẫn hữu ích trong ứng dụng thiên về XML hoặc khi configuration cần nằm ngoài aspect class. Điều cần học là runtime contract chung, không xem hai syntax này như hai join-point model khác nhau.

### Tài liệu tham khảo

- Spring Framework Reference — @AspectJ support
- Spring Framework Reference — Schema-based AOP Support

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-enablement">2. Bật @AspectJ auto-proxy support trong Spring Framework</a>

<details>
<summary>Xem chi tiết</summary>

Khai báo class `@Aspect` mới chỉ là một phần của cấu hình. Spring còn phải bật @AspectJ auto-proxy support và aspect phải tồn tại như một bean trong `ApplicationContext` liên quan.

Java configuration:

```java
@Configuration
@EnableAspectJAutoProxy
class AopConfig {
}
```

XML configuration:

```xml
<aop:aspectj-autoproxy/>
```

Library AspectJ `aspectjweaver` cũng cần có trên classpath vì Spring dùng parser/matcher của AspectJ cho @AspectJ pointcut expression. Dependency đó **không** có nghĩa bytecode đang được weave.

Ở mức mental model, enablement tạo lifecycle:

```text
ApplicationContext phát hiện aspect bean
        ↓
Spring xây advisor metadata
        ↓
auto-proxy infrastructure kiểm tra bean ứng viên
        ↓
bean phù hợp có thể được expose qua AOP proxy
```

`@EnableAspectJAutoProxy` còn có các option liên quan proxy như `proxyTargetClass` và `exposeProxy`. Chúng thay đổi hành vi của proxy nhưng không mở rộng Spring AOP ra ngoài method-execution join point.

Cũng cần tách framework contract khỏi mặc định ứng dụng. Spring Boot có thể auto-configure AOP và chọn proxy setting cho ứng dụng Boot, nhưng module này mô tả cơ chế của Spring Framework.

### Tài liệu tham khảo

- Spring Framework Reference — Enabling @AspectJ Support

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-mental-model">3. Pointcut đang khớp với yếu tố nào?</a>

<details>
<summary>Xem chi tiết</summary>

Trong Spring AOP, mọi join point mà pointcut cuối cùng có thể chọn đều là **method execution** đi qua mô hình proxy. Sau đó mỗi pointcut designator giới hạn method execution đó theo một loại thông tin khác nhau.

Nhóm dựa trên signature/type:

```text
execution(...)
→ match method execution signature

within(...)
→ giới hạn vào method được khai báo trong type phù hợp
```

Nhóm dựa trên runtime context:

```text
this(...)
→ kiểm tra Spring AOP proxy object

target(...)
→ kiểm tra target object phía sau proxy

args(...)
→ kiểm tra runtime argument type
```

Nhóm dựa trên annotation:

```text
@annotation(...)
→ annotation trên method đang thực thi

@within(...)
→ annotation trên type nơi method được khai báo

@target(...)
→ annotation trên runtime target type

@args(...)
→ annotation trên runtime type của argument
```

Spring bổ sung một designator riêng:

```text
bean(...)
→ match theo Spring bean name
```

Điểm khác biệt giữa `this` và `target` đặc biệt quan trọng trong proxy-based AOP:

```text
this
→ identity / type của proxy

target
→ identity / type của target của ứng dụng
```

Vì vậy pointcut không chỉ là text pattern trên tên method. Nó có thể kết hợp cấu trúc tĩnh với runtime context, nhưng thứ được chọn cuối cùng vẫn là method execution có thể đi qua proxy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-designators-and-composition">4. Các designator được hỗ trợ, cách kết hợp và runtime context</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOP hỗ trợ các AspectJ pointcut designator sau trong execution-only model:

```text
execution
within
this
target
args
@target
@args
@within
@annotation
```

và Spring bổ sung `bean`.

Có thể compose pointcut expression bằng:

```text
&&  AND
||  OR
!   NOT
```

Một cách thiết kế tốt là tạo các named pointcut nhỏ mô tả architecture rồi compose chúng:

```java
@Pointcut("within(com.example.order.service..*)")
void inOrderService() {}

@Pointcut("execution(public * *(..))")
void publicOperation() {}

@Before("inOrderService() && publicOperation()")
void observeServiceCall() {
    // ...
}
```

Cách này dễ review hơn việc lặp một expression dài trong mọi advice annotation.

Pointcut còn có thể **bind context** vào advice parameter. Ví dụ:

```java
@Before("execution(* *(..)) && args(orderId)")
void observe(String orderId) {
    // orderId là runtime argument được args(...) bind
}
```

Các chapter khác dùng `@annotation(annotation)` để bind annotation metadata, `returning` để bind return value của luồng thành công và `throwing` để bind exception.

Một cách suy nghĩ hữu ích là tách ba câu hỏi:

```text
Chính sách này thuộc vùng cấu trúc nào?
→ within(...) / execution(...)

Runtime context nào thật sự quan trọng?
→ this(...) / target(...) / args(...)

Metadata nào là contract explicit?
→ @annotation(...) / @within(...) / các annotation designator liên quan
```

Nên ưu tiên pointcut hẹp mô tả boundary ổn định như service layer hoặc annotation contract explicit. Expression phụ thuộc vào package incidental, generated type hoặc pattern quá rộng kiểu `execution(* *(..))` dễ tạo accidental match.

### Tài liệu tham khảo

- Spring Framework Reference — Declaring a Pointcut

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-demo">5. Demo trong module</a>

<details>
<summary>Xem chi tiết</summary>

Controller:

```text
PointcutController#comparePointcuts()
```

Endpoint:

```text
GET /aop/pointcut/compare
```

Controller lần lượt gọi:

```text
PointcutService#byExecution()
PointcutService#byAnnotation()
PointcutService#byArgs("demo")
PointcutService#byRuntimeArgs("runtime-string")
PointcutService#byWithin()
PointcutService#byThisAndTarget()
PointcutService#byBean()
PointcutService#unmatched()
```

Aspect:

```text
PointcutMatchingAspect
```

Response `events` cho thấy một quy tắc matching khác nhau cho mỗi target call:

```text
pointcut:execution
target:byExecution

pointcut:@annotation
target:byAnnotation

pointcut:args:demo
target:byArgs:demo

pointcut:args-runtime-type=String
target:byRuntimeArgs:runtime-string:declaredType=Object

pointcut:within+named-composition
target:byWithin

(xuất hiện cả pointcut:this-proxy-type và pointcut:target-type)
target:byThisAndTarget

pointcut:bean-name
target:byBean

target:unmatched
```

`byRuntimeArgs(Object)` cố ý khai báo parameter là `Object` trong khi controller truyền `String`. Pointcut `args(java.lang.String)` vẫn match vì `args` xét runtime argument type. Ngược lại, `execution` mô tả declared method execution signature.

`byWithin()` minh họa named composition. `byThisAndTarget()` minh họa hai runtime identity khác nhau: `this` quan sát proxy còn `target` quan sát target object. Hai advice đều có thể match cùng invocation; thứ tự log tương đối giữa chúng không phải contract vì không có precedence explicit.

`bean(pointcutService)` chứng minh Spring-specific bean-name matcher. `unmatched()` là baseline cho thấy một call đi qua proxy nhưng không có pointcut phù hợp vẫn tới target mà không chạy advice đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-pointcut-boundary">6. Spring AOP không hỗ trợ toàn bộ AspectJ join point model</a>

<details>
<summary>Xem chi tiết</summary>

Spring AOP dùng AspectJ pointcut expression language, nhưng runtime của Spring vẫn proxy-based và chỉ hỗ trợ method execution.

Vì vậy suy luận sau là sai:

```text
Spring hiểu AspectJ pointcut syntax
→ Spring AOP hỗ trợ mọi AspectJ join point
```

Các designator thuộc join-point model rộng hơn của AspectJ không được Spring AOP hỗ trợ. Ví dụ:

```text
call(...)
get(...)
set(...)
initialization(...)
preinitialization(...)
staticinitialization(...)
handler(...)
adviceexecution(...)
withincode(...)
cflow(...)
cflowbelow(...)
if(...)
@this(...)
@withincode(...)
```

Dùng unsupported designator trong Spring AOP pointcut expression sẽ gây lỗi configuration/parsing, không âm thầm bật khả năng đó.

Boundary này giải thích lúc nào full AspectJ weaving mới trở nên phù hợp. Nếu yêu cầu cần field access, constructor execution, call-site join point hoặc control-flow pointcut như `cflow`, proxy quanh method execution của Spring bean không thể cung cấp model đó.

### Tài liệu tham khảo

- Spring Framework Reference — Declaring a Pointcut
- Spring Framework Reference — Using AspectJ with Spring Applications

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-conclusion">7. Kết luận</a>

<details>
<summary>Xem chi tiết</summary>

Khi advice không chạy, tách việc chẩn đoán thành các bước kiểm tra độc lập:

```text
1. AOP proxy đã được tạo chưa?
2. Invocation có đi qua proxy đó không?
3. Pointcut có hợp lệ với method-execution model của Spring AOP không?
4. Pointcut có match signature/context/metadata hiện tại không?
```

Pointcut tốt làm cross-cutting contract rõ và dễ dự đoán. Chapter tiếp theo tập trung vào một dạng contract đặc biệt hữu ích: annotation được đặt explicit trên operation cần advice.

</details>

- [Quay lại đầu trang](#back-to-top)
