# 📂 README MODULE STRUCTURE (VI)

* **1.CrossCutting**
    * [CrossCutting](readme/vi/menu/1.CrossCutting/CrossCutting.md)
* **2.Terminology**
    * [Terminology](readme/vi/menu/2.Terminology/Terminology.md)
* **3.Proxy**
    * [Proxy](readme/vi/menu/3.Proxy/Proxy.md)
* **4.Pointcut**
    * [Pointcut](readme/vi/menu/4.Pointcut/Pointcut.md)
* **5.Advice**
    * [Advice](readme/vi/menu/5.Advice/Advice.md)
* **6.Around**
    * [Around](readme/vi/menu/6.Around/Around.md)
* **7.Annotation**
    * [Annotation](readme/vi/menu/7.Annotation/Annotation.md)
* **8.Ordering**
    * [Ordering](readme/vi/menu/8.Ordering/Ordering.md)
* **9.SelfInvocation**
    * [SelfInvocation](readme/vi/menu/9.SelfInvocation/SelfInvocation.md)
* **10.Patterns**
    * [Patterns](readme/vi/menu/10.Patterns/Patterns.md)
* **11.ProxyFactory**
    * [ProxyFactory](readme/vi/menu/11.ProxyFactory/ProxyFactory.md)
* **12.Advisor**
    * [Advisor](readme/vi/menu/12.Advisor/Advisor.md)
* **13.Infrastructure**
    * [Infrastructure](readme/vi/menu/13.Infrastructure/Infrastructure.md)
* **14.Introduction**
    * [Introduction](readme/vi/menu/14.Introduction/Introduction.md)
* **15.RuntimeBoundary**
    * [RuntimeBoundary](readme/vi/menu/15.RuntimeBoundary/RuntimeBoundary.md)

# Spring AOP Learning Roadmap

Module này dùng để học **Aspect-Oriented Programming (AOP) trong Spring**, với trọng tâm là hiểu đúng mental model của Spring AOP thay vì chỉ ghi nhớ các annotation như `@Before`, `@After` hay `@Around`.

Mục tiêu cuối cùng là có thể trả lời được các câu hỏi:

- AOP giải quyết loại vấn đề nào?
- Aspect, Advice, Pointcut và Join Point khác nhau thế nào?
- Spring AOP thực sự chặn method call bằng cơ chế gì?
- Vì sao có method được advice chạy, nhưng method khác lại không?
- `@Around` khác gì so với các advice còn lại?
- Khi nhiều Aspect cùng match một method thì thứ tự thực thi ra sao?
- Vì sao self-invocation thường làm AOP không hoạt động như mong đợi?
- Khi nào nên dùng AOP và khi nào không nên dùng?

---

## Learning flow

Roadmap của module đi theo thứ tự:

```text
Cross-cutting concern
        ↓
AOP terminology
        ↓
Spring AOP proxy mental model
        ↓
Pointcut matching
        ↓
Advice lifecycle
        ↓
@Around + ProceedingJoinPoint
        ↓
Annotation-based Aspect
        ↓
Multiple Aspects + Ordering
        ↓
Proxy limitations + Self-invocation
        ↓
Practical patterns + Pitfalls
        ↓
================ Fundamentals complete ================
        ↓
ProxyFactory + MethodInterceptor
        ↓
Advisor + Programmatic Pointcut
        ↓
Auto Proxy Creator + Advised
        ↓
Introduction / Interface Enrichment
        ↓
Spring AOP runtime boundary + AspectJ
```

Không nên học AOP theo hướng:

```text
@Before là gì?
@After là gì?
@Around là gì?
```

rồi dừng lại ở đó.

Điểm quan trọng hơn là hiểu:

```text
method call có đi qua Spring proxy hay không?
        ↓
pointcut có match hay không?
        ↓
advice nào được thực thi?
        ↓
target method được gọi khi nào?
```

---

## 1. AOP và Cross-Cutting Concern

Phần đầu tiên trả lời câu hỏi:

> Vì sao AOP tồn tại?

Các concern như:

- logging;
- execution timing;
- metrics;
- auditing;
- authorization check;
- transaction boundary;
- tracing;

thường xuất hiện ở nhiều class hoặc nhiều method khác nhau.

Nếu viết trực tiếp các concern này vào business logic, code rất dễ bị lặp và làm business method khó đọc.

Topic này sẽ xây mental model:

```text
business concern
        ≠
cross-cutting concern
```

và chỉ ra AOP giúp tách cross-cutting logic khỏi code chính như thế nào.

---

## 2. AOP Terminology

Sau khi hiểu bài toán, cần phân biệt chính xác các khái niệm:

```text
Aspect
Advice
Pointcut
Join Point
Target Object
AOP Proxy
Weaving
```

Trong Spring AOP, điểm cần nhớ đặc biệt là:

```text
Join Point
= method execution

Pointcut
= rule chọn subset các method execution

Proxy boundary
= invocation có thực sự đi vào AOP chain hay không
```

`weaving` là thuật ngữ AOP tổng quát. Spring AOP thực hiện runtime weaving bằng proxy; AspectJ còn hỗ trợ compile-time/load-time weaving và join point model rộng hơn.

Spring AOP không phải implementation đầy đủ của full AspectJ weaving model.

Module sẽ tập trung chủ yếu vào **proxy-based Spring AOP**.

---

## 3. Spring AOP Proxy Mental Model

Đây là một trong những chapter quan trọng nhất của module.

Mental model cơ bản:

```text
Caller
  ↓
Spring Proxy
  ↓
Advice chain
  ↓
Target object
```

Điều cần quan sát là caller thường không gọi trực tiếp target object, mà gọi một object proxy do Spring cung cấp.

Topic này sẽ tìm hiểu:

- proxy là gì;
- target object là gì;
- advice được chen vào call chain ở đâu;
- JDK dynamic proxy;
- subclass/CGLIB proxy;
- Spring-managed bean khác object tạo trực tiếp bằng `new` như thế nào;
- tại sao proxy boundary quyết định AOP có hoạt động hay không.

Kết thúc chapter này, người học phải hiểu được rằng:

```text
Spring AOP không "tự chạy quanh mọi method".

Advice chỉ có cơ hội chạy khi method invocation đi qua proxy phù hợp.
```

---

## 4. Pointcut và Method Matching

Pointcut quyết định **join point nào được chọn**.

Module sẽ đi từ pointcut đơn giản đến các cách match thực tế hơn:

```text
execution(...)
within(...)
args(...)
this(...)
target(...)
@annotation(...)
```

Mục tiêu không phải ghi nhớ cú pháp càng nhiều càng tốt, mà phải hiểu:

```text
pointcut đang match theo cái gì?

method signature?
package/type?
runtime argument?
annotation?
proxy type?
target type?
```

Topic này cũng sẽ học named pointcut + composition bằng `&&`, `||`, `!` và nhấn mạnh việc viết pointcut đủ hẹp để tránh vô tình intercept quá nhiều method.

Module cũng minh họa `bean(...)` — một Spring-specific pointcut designator — và làm rõ rằng Spring AOP dùng AspectJ expression syntax nhưng không cung cấp toàn bộ join point model của full AspectJ weaving.

`@within(...)`, `@target(...)` và các designator khác vẫn tồn tại trong AspectJ pointcut language, nhưng fundamentals không cố biến chapter này thành catalog cú pháp. Mục tiêu là hiểu các nhóm matching representative và cách debug selection rule.

Experiment còn cố ý dùng một method khai báo parameter là `Object` nhưng truyền `String` runtime để phân biệt `args(...)` (runtime argument type) với `execution(...)` (declared method signature).

---

## 5. Advice Lifecycle

Sau khi pointcut match, Aspect có thể chạy các loại advice khác nhau:

```text
@Before
@After
@AfterReturning
@AfterThrowing
```

Mục tiêu của chapter là quan sát chính xác thứ tự và điều kiện chạy của từng loại advice.

Đặc biệt cần phân biệt:

```text
@After
        → chạy khi method kết thúc, tương tự finally semantics

@AfterReturning
        → chỉ chạy khi method return bình thường

@AfterThrowing
        → chạy khi method thoát ra bằng exception phù hợp
```

Các experiment sẽ gồm cả:

- method thành công;
- method có return value;
- method ném exception.

Chapter này cũng gom lại advice parameter binding như `JoinPoint`, `args(...)`, `@annotation(...)`, `returning` và `throwing` để learner hiểu dữ liệu được truyền vào advice bằng cách nào.

Ngoài lifecycle, chapter còn nhấn mạnh hai rule thực tế: dùng **advice yếu nhất nhưng đủ requirement**, và không dựa vào source order khi nhiều advice cùng loại trong cùng Aspect cùng match một join point.

---

## 6. Around Advice và ProceedingJoinPoint

`@Around` là advice mạnh nhất vì nó bao quanh target invocation.

Mental model:

```text
Around - before proceed
        ↓
proceed()
        ↓
Target method
        ↓
Around - after proceed
```

Topic này sẽ học:

- `ProceedingJoinPoint`;
- `proceed()`;
- đọc arguments;
- thay arguments bằng `proceed(Object[])`;
- đo execution time;
- quan sát return value;
- biến đổi return value khi thật sự cần;
- exception propagation;
- điều gì xảy ra nếu không gọi `proceed()`;
- vì sao gọi `proceed()` nhiều lần có thể làm target/side effect lặp và không phải pattern mặc định nên dùng.

Module cũng ghi rõ boundary của `proceed(Object[])`: demo đang dùng semantics của Spring AOP proxy runtime; AspectJ-compiled around advice có rule binding arguments khác và không nên được coi là tương đương tuyệt đối.

Đây cũng là nơi làm rõ rằng `@Around` có khả năng thay đổi control flow nên cần được sử dụng cẩn thận.

---

## 7. Custom Annotation-Based Aspect

Một pattern phổ biến là dùng custom annotation để đánh dấu method cần một cross-cutting behavior cụ thể.

Ví dụ mental model:

```text
@TrackExecution
        ↓
@annotation(...) pointcut
        ↓
TimingAspect
```

Topic này sẽ học:

- tạo custom annotation;
- retention phù hợp;
- target phù hợp;
- match annotation bằng pointcut;
- đọc metadata từ annotation;
- khi nào annotation-based pointcut dễ maintain hơn package/method expression.

Mục tiêu là biến annotation thành một **declarative contract**, không chỉ là marker cho demo.

---

## 8. Multiple Aspects và Ordering

Một method có thể match nhiều Aspect cùng lúc.

Ví dụ:

```text
LoggingAspect
TimingAspect
AuditAspect
        ↓
cùng match một service method
```

Topic này sẽ quan sát:

- nhiều advice cùng tham gia call chain;
- `@Order`;
- outer/inner aspect;
- thứ tự đi vào và đi ra của `@Around`;
- vì sao nhìn log tuyến tính có thể gây hiểu nhầm nếu không hình dung call stack dạng nested.

Nếu nhiều advice không có precedence contract rõ ràng, module không coi thứ tự log tình cờ quan sát được là behavior được đảm bảo.

Mental model cần đạt được:

```text
Aspect A before
    Aspect B before
        target
    Aspect B after
Aspect A after
```

---

## 9. Proxy Limitation và Self-Invocation

Đây là chapter bắt buộc trước khi áp dụng AOP trong code thật.

Case kinh điển:

```java
public void outer() {
    inner();
}
```

nếu `outer()` và `inner()` cùng nằm trong target object, lời gọi `inner()` có thể chỉ là:

```text
this.inner()
```

và không đi lại qua Spring proxy.

Kết quả:

```text
Caller → Proxy → outer()
                 ↓
              this.inner()

inner() không đi qua Proxy lần thứ hai
```

Topic này sẽ học:

- self-invocation;
- proxy boundary;
- class-based proxy limitation;
- final/private method và khả năng interception;
- vì sao "có annotation" không đồng nghĩa "advice chắc chắn chạy";
- cách refactor boundary thay vì cố gắng né proxy model.

JDK proxy type-surface limitation được chứng minh trực tiếp ở chapter `11.ProxyFactory`, nơi cùng một target được tạo cả JDK proxy và CGLIB proxy để so sánh.

Chapter này cũng tạo nền tảng để sau này hiểu các vấn đề tương tự với:

```text
@Transactional
@Async
@Cacheable
```

vì nhiều Spring feature cũng dựa trên proxy/interceptor.

---

## 10. Practical Patterns và Pitfalls

Sau khi hiểu cơ chế, module sẽ ghép kiến thức vào một số pattern thực tế:

```text
execution logging
execution timing
auditing
metrics/tracing hook
annotation-driven behavior
```

Đồng thời phân biệt những trường hợp AOP dễ bị lạm dụng:

- pointcut quá rộng;
- Aspect chứa business logic;
- thay đổi argument/return value một cách khó đoán;
- swallow exception;
- phụ thuộc ngầm giữa nhiều Aspect;
- ordering quá phức tạp;
- mutable state trong singleton Aspect mà không có concurrency contract rõ ràng;
- dùng AOP khi một abstraction/service bình thường dễ hiểu hơn.

Mental model cuối cùng:

```text
AOP phù hợp nhất cho behavior mang tính cross-cutting,
có boundary rõ ràng,
và có thể mô tả declaratively.
```

---

## 11. Advanced - ProxyFactory và MethodInterceptor

Sau fundamentals, module chuyển từ câu hỏi:

```text
"Dùng @Aspect như thế nào?"
```

sang:

```text
"Spring AOP proxy thực chất được xây từ những abstraction nào?"
```

Chapter này tự tạo proxy bằng `ProxyFactory` và dùng `MethodInterceptor` để nhìn trực tiếp call chain:

```text
Proxy
→ MethodInterceptor
→ invocation.proceed()
→ Target
```

Đồng thời cùng một target được proxy theo hai strategy:

```text
JDK Dynamic Proxy
CGLIB / class-based proxy
```

để những limitation ở chapter 3 và 9 có nền tảng concrete hơn.

Experiment cũng chứng minh **type surface** khác nhau: JDK proxy expose interface contract, còn class-based proxy là subclass của target nên vẫn expose concrete methods phù hợp.

Khác biệt type surface này cũng nối lại semantics của `this(...)` và `target(...)`: `this` nhìn proxy object, còn `target` nhìn target object phía sau proxy.

---

## 12. Advanced - Advisor và Programmatic Pointcut

Chapter này bóc `@Around("pointcut")` thành hai abstraction độc lập:

```text
Pointcut
→ chọn method

Advice / MethodInterceptor
→ behavior khi match

Advisor
→ ghép Pointcut + Advice
```

Chapter đi thêm một tầng vào Pointcut API:

```text
Pointcut
├── ClassFilter
└── MethodMatcher
```

Experiment thứ nhất dùng `NameMatchMethodPointcut` để chứng minh static MethodMatcher (`isRuntime=false`). Experiment thứ hai dùng custom `RuntimeArgumentPointcut` với `isRuntime=true`: cùng `writeWithMode(...)`, argument `"audit"` match advice còn `"plain"` đi thẳng tới target. Nhờ đó learner thấy rõ static method matching và runtime argument matching là hai phase khác nhau.

---

## 13. Advanced - Auto Proxy Creator và Advised

Application code bình thường không tự tạo `ProxyFactory` cho từng bean.

Spring có auto-proxy infrastructure dựa trên `BeanPostProcessor` family để:

```text
discover candidate Advisor
→ quyết định bean nào cần proxy
→ build interceptor chain
→ trả proxy cho consumer
```

Chapter này inspect:

```text
AbstractAutoProxyCreator
Advised
Advisor[]
runtime proxy class
target class
```

Với Spring Boot, chapter này còn giải thích vì sao module không cần viết `@EnableAspectJAutoProxy`: `spring-boot-starter-aop` + Boot auto-configuration bật AOP infrastructure theo mặc định, và documented default `spring.aop.proxy-target-class=true` làm class-based proxy trở thành default của Boot. Runtime endpoint tách explicit configuration (`Environment`) khỏi evidence thật (`AutoProxyCreator`, `AopUtils`) thay vì hard-code default thành một fact do application đã cấu hình.

Mục tiêu là hiểu tại sao annotation-style AOP declarative nhưng runtime vẫn có infrastructure object rất cụ thể phía dưới.

---

## 14. Advanced - Introduction / Interface Enrichment

Introduction cho phép proxy expose thêm interface mà target class gốc không implement.

Mental model:

```text
Target class
→ business API ban đầu

Spring AOP proxy sau introduction
→ business API ban đầu
+ interface mới
```

Module dùng `@DeclareParents` để thêm `UsageTracked` lên proxy của một target service.

Đây là feature nâng cao, chủ yếu để hiểu capability của proxy/advisor model hơn là pattern bắt buộc cho application thông thường.

---

## 15. Advanced - Spring AOP Runtime Boundary và AspectJ

Chapter cuối làm rõ distinction:

```text
@AspectJ declaration style
≠
AspectJ compiler/weaver runtime
```

Module dùng AspectJ annotation syntax và pointcut language nhưng runtime vẫn là proxy-based Spring AOP.

Chapter này kết nối toàn bộ mental model:

```text
@Aspect metadata
        ↓
Advisor candidate
        ↓
Auto Proxy Creator
        ↓
Spring AOP Proxy
        ↓
Interceptor chain
        ↓
Target
```

Full AspectJ weaving là một model rộng hơn và nằm ngoài implementation scope chính của module này.

Chapter cuối cũng đặt boundary với async/reactive method: thời gian quanh `proceed()` chỉ đo method invocation, không tự động đồng nghĩa với thời điểm `CompletableFuture`, `Mono` hoặc `Flux` hoàn tất workload.

Boundary Spring AOP ↔ AspectJ còn bao gồm semantics của `proceed(Object[])`: hai runtime cùng dùng @AspectJ-style source nhưng không có contract thay argument hoàn toàn giống nhau.

---

## Cách học trong module

Mỗi topic sẽ cố gắng tuân theo flow:

```text
Theory / Mental Model
        ↓
Question cần chứng minh
        ↓
Controller trigger
        ↓
Service / Aspect implementation
        ↓
Response / Console / Exception / Timing
        ↓
Observation
        ↓
Conclusion
```

README là nơi giải thích theory và điều cần quan sát.

Controller chỉ đóng vai trò kích hoạt experiment.

Service chứa behavior của target object.

Aspect chứa cross-cutting behavior cần quan sát.

Các experiment nên đủ nhỏ để khi chạy có thể trả lời một câu hỏi cụ thể, thay vì một endpoint chứng minh quá nhiều concept cùng lúc.

---

## Kết quả mong đợi sau khi hoàn thành module

Sau roadmap này, người học nên có thể:

1. Nhận diện cross-cutting concern phù hợp với AOP.
2. Giải thích đúng Aspect, Advice, Pointcut, Join Point, Target và Proxy.
3. Dự đoán một method call có đi qua Spring AOP proxy hay không.
4. Viết pointcut đủ chính xác cho use case cụ thể.
5. Chọn đúng loại advice.
6. Sử dụng `@Around` và `ProceedingJoinPoint` có kiểm soát.
7. Xây annotation-based Aspect.
8. Giải thích thứ tự khi nhiều Aspect cùng match.
9. Nhận diện self-invocation và các proxy limitation phổ biến.
10. Biết khi nào AOP giúp thiết kế sạch hơn và khi nào nó làm code khó hiểu hơn.
11. Tự tạo JDK/CGLIB AOP proxy bằng `ProxyFactory` và `MethodInterceptor`.
12. Giải thích `Advisor = Pointcut + Advice` và dùng programmatic pointcut.
13. Inspect được `Advised`, Advisor chain và auto-proxy creator của Spring.
14. Hiểu Introduction có thể thêm interface lên proxy mà không sửa target class.
15. Phân biệt @AspectJ declaration style, Spring AOP proxy runtime và full AspectJ weaving.
