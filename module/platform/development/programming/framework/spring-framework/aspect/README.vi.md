# 📂 README MODULE STRUCTURE (VI)

* **1.CrossCutting**
    * [CrossCutting](readme/vi/menu/1.CrossCutting/CrossCutting.md)
* **2.Terminology**
    * [Terminology](readme/vi/menu/2.Terminology/Terminology.md)
* **3.Proxy**
    * [Proxy](readme/vi/menu/3.Proxy/Proxy.md)
* **4.Pointcut**
    * [Pointcut](readme/vi/menu/4.Pointcut/Pointcut.md)
* **5.Annotation**
    * [Annotation](readme/vi/menu/5.Annotation/Annotation.md)
* **6.Advice**
    * [Advice](readme/vi/menu/6.Advice/Advice.md)
* **7.Around**
    * [Around](readme/vi/menu/7.Around/Around.md)
* **8.Ordering**
    * [Ordering](readme/vi/menu/8.Ordering/Ordering.md)
* **9.Introduction**
    * [Introduction](readme/vi/menu/9.Introduction/Introduction.md)
* **10.ProxyFactory**
    * [ProxyFactory](readme/vi/menu/10.ProxyFactory/ProxyFactory.md)
* **11.Advisor**
    * [Advisor](readme/vi/menu/11.Advisor/Advisor.md)
* **12.Infrastructure**
    * [Infrastructure](readme/vi/menu/12.Infrastructure/Infrastructure.md)
* **13.SelfInvocation**
    * [SelfInvocation](readme/vi/menu/13.SelfInvocation/SelfInvocation.md)
* **14.Patterns**
    * [Patterns](readme/vi/menu/14.Patterns/Patterns.md)
* **15.RuntimeBoundary**
    * [RuntimeBoundary](readme/vi/menu/15.RuntimeBoundary/RuntimeBoundary.md)

# Spring AOP

Module này dạy **Aspect-Oriented Programming trong Spring** như một mô hình method interception dựa trên proxy, không phải như một danh sách annotation để ghi nhớ.

Mục tiêu là hiểu toàn bộ runtime path:

```text
cross-cutting concern
→ pointcut/advisor selection
→ AOP proxy
→ interceptor/advice chain
→ target invocation
→ observable result
```

## Vì sao module này tồn tại

Một số concern xuất hiện trên nhiều operation như auditing, tracing, timing, logging, policy check và các infrastructure behavior khác. Spring AOP giúp tách chúng khỏi business flow khi interception boundary đủ rõ ràng.

Câu hỏi quan trọng không chỉ là method có annotation gì. Learner phải suy luận được proxy có tồn tại không, invocation có đi qua proxy không, pointcut/advisor có match không, advice nào tham gia và target nào cuối cùng được gọi.

## Prerequisite

Trước module này, learner nên hiểu Java class/interface/annotation/reflection và Spring IoC container ở mức cơ bản. `spring-framework/core-container` là Spring prerequisite chính vì auto-proxying tích hợp với bean lifecycle và BeanPostProcessor infrastructure.

## Learning journey

15 Knowledge chapter materialize 9 Roadmap milestone thành các learner-facing identity ổn định:

```text
Foundation / core model
1. CrossCutting → 2. Terminology → 3. Proxy

Declaration / pointcut contract
4. Pointcut → 5. Annotation

Advice semantics / invocation control
6. Advice → 7. Around

Composition / lifecycle / introduction
8. Ordering → 9. Introduction

Low-level infrastructure
10. ProxyFactory → 11. Advisor → 12. Infrastructure

Failure model / trade-off
13. SelfInvocation → 14. Patterns

Synthesis
15. RuntimeBoundary
```

Flow này đi từ bài toán AOP giải quyết, qua proxy/advice mechanics, tới composition/lifecycle/introduction, sau đó xuống low-level Spring AOP infrastructure, failure/debugging và design trade-off trước khi kết thúc ở boundary Spring AOP ↔ AspectJ.

## Boundary của module

Module này sở hữu Spring AOP terminology, proxy mechanics, pointcut, advice, advisor, ordering, introduction, self-invocation, `ProxyFactory`, auto-proxy infrastructure, `Advised`, `TargetSource` và boundary Spring AOP với AspectJ.

Module không sở hữu full semantics của các feature lân cận chỉ tiêu thụ cùng interceptor model:

- transaction propagation/rollback/isolation → `spring-framework/transaction-management`;
- cache behavior/provider semantics → `spring-framework/cache`;
- `@Async`, executor, scheduling → `spring-framework/concurrency`;
- method-security policy → Spring Security;
- Spring Boot AOP auto-configuration defaults → Spring Boot.

Các feature đó chỉ nên xuất hiện như boundary/integration example.

## Spring AOP và AspectJ

Luôn tách hai ý:

```text
@AspectJ declaration style
≠ AspectJ compiler/weaver
```

Runtime chính của module là proxy-based Spring AOP tập trung vào method execution. Full AspectJ hỗ trợ bytecode weaving và join-point model rộng hơn.

## Cách dùng experiment

Runtime endpoint là evidence cho câu hỏi hẹp, không phải curriculum authority. Dùng chúng để kiểm chứng object có được proxy không, invocation có đi qua proxy boundary không, JDK/CGLIB proxy surface khác nhau thế nào, advice ordering lồng nhau ra sao, hoặc programmatic `ProxyFactory` khác container auto-proxying như thế nào.

## Kết quả mong đợi

Sau module, learner phải có thể giải thích và dự đoán Spring AOP từ runtime model thay vì chỉ nhìn annotation, thiết kế pointcut/advice ổn định, debug proxy-boundary failure có hệ thống, inspect low-level proxy infrastructure khi cần, và biết khi nào requirement thuộc module Spring khác hoặc full AspectJ.
