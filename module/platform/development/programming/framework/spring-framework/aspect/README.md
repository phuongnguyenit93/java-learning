# 📂 README MODULE STRUCTURE (EN)

* **1.CrossCutting**
    * [CrossCutting](readme/en/menu/1.CrossCutting/CrossCutting.md)
* **2.Terminology**
    * [Terminology](readme/en/menu/2.Terminology/Terminology.md)
* **3.Proxy**
    * [Proxy](readme/en/menu/3.Proxy/Proxy.md)
* **4.Pointcut**
    * [Pointcut](readme/en/menu/4.Pointcut/Pointcut.md)
* **5.Annotation**
    * [Annotation](readme/en/menu/5.Annotation/Annotation.md)
* **6.Advice**
    * [Advice](readme/en/menu/6.Advice/Advice.md)
* **7.Around**
    * [Around](readme/en/menu/7.Around/Around.md)
* **8.Ordering**
    * [Ordering](readme/en/menu/8.Ordering/Ordering.md)
* **9.Introduction**
    * [Introduction](readme/en/menu/9.Introduction/Introduction.md)
* **10.ProxyFactory**
    * [ProxyFactory](readme/en/menu/10.ProxyFactory/ProxyFactory.md)
* **11.Advisor**
    * [Advisor](readme/en/menu/11.Advisor/Advisor.md)
* **12.Infrastructure**
    * [Infrastructure](readme/en/menu/12.Infrastructure/Infrastructure.md)
* **13.SelfInvocation**
    * [SelfInvocation](readme/en/menu/13.SelfInvocation/SelfInvocation.md)
* **14.Patterns**
    * [Patterns](readme/en/menu/14.Patterns/Patterns.md)
* **15.RuntimeBoundary**
    * [RuntimeBoundary](readme/en/menu/15.RuntimeBoundary/RuntimeBoundary.md)

# Spring AOP

This module teaches **Spring Aspect-Oriented Programming** as a proxy-based method-interception model, not as a collection of annotations to memorize.

The learning goal is to understand the full runtime path:

```text
cross-cutting concern
→ pointcut/advisor selection
→ AOP proxy
→ interceptor/advice chain
→ target invocation
→ observable result
```

## Why this module exists

Some concerns apply across many application operations: auditing, tracing, timing, logging, policy checks, and other infrastructure behavior. Spring AOP provides a way to keep those concerns separate from ordinary business flow when a clear interception boundary exists.

The important question is not merely which annotation is present. The learner must be able to reason about whether a proxy exists, whether the call crosses it, whether the pointcut/advisor matches, which advice participates, and which target is ultimately invoked.

## Prerequisites

Before this module, the learner should already understand Java classes/interfaces/annotations/reflection and the Spring IoC container at a basic level. `spring-framework/core-container` is the primary Spring prerequisite because auto-proxying integrates with bean lifecycle and BeanPostProcessor infrastructure.

## Learning journey

The 15 Knowledge chapters materialize the 9 Roadmap milestones into stable learner-facing identities:

```text
Foundation / core model
1. CrossCutting → 2. Terminology → 3. Proxy

Declaration / pointcut contracts
4. Pointcut → 5. Annotation

Advice semantics / invocation control
6. Advice → 7. Around

Composition / lifecycle / introductions
8. Ordering → 9. Introduction

Low-level infrastructure
10. ProxyFactory → 11. Advisor → 12. Infrastructure

Failure model / trade-offs
13. SelfInvocation → 14. Patterns

Synthesis
15. RuntimeBoundary
```

This progression moves from the problem AOP solves, through proxy and advice mechanics, into aspect composition/lifecycle and introductions, then down into low-level Spring AOP infrastructure, failure/debugging and design trade-offs before ending at the Spring AOP ↔ AspectJ boundary.

## Module boundaries

This module owns Spring AOP terminology, proxy mechanics, pointcuts, advice, advisors, ordering, introductions, self-invocation, `ProxyFactory`, auto-proxy infrastructure, `Advised`, `TargetSource`, and the Spring AOP versus AspectJ boundary.

It does **not** own the full semantics of neighboring features that consume the same interceptor model:

- transaction propagation/rollback/isolation → `spring-framework/transaction-management`;
- cache behavior/provider semantics → `spring-framework/cache`;
- `@Async`, executors, scheduling → `spring-framework/concurrency`;
- method-security policy → Spring Security;
- Spring Boot AOP auto-configuration defaults → Spring Boot.

Those features may appear only as boundary/integration examples.

## Spring AOP versus AspectJ

Keep these ideas separate:

```text
@AspectJ declaration style
≠ AspectJ compiler/weaver
```

The primary runtime in this module is proxy-based Spring AOP focused on method execution. Full AspectJ supports bytecode weaving and a broader join-point model.

## Using the experiments

Runtime endpoints are evidence for narrow questions, not curriculum authority. Use them to verify claims such as whether an object is proxied, whether a call crosses the proxy boundary, how JDK and CGLIB proxy surfaces differ, how advice ordering nests, or how programmatic `ProxyFactory` differs from container auto-proxying.

## Expected outcome

After the module, the learner should be able to explain and predict Spring AOP behavior from the runtime model rather than annotation presence alone, design stable pointcuts/advice, debug proxy-boundary failures systematically, inspect low-level proxy infrastructure when needed, and know when a requirement belongs to another Spring module or full AspectJ.
