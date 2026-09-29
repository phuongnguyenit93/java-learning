# Spring AOP Learning Roadmap

This module is for learning **Aspect-Oriented Programming (AOP) in Spring**, with the emphasis on building the correct Spring AOP mental model instead of only memorizing annotations such as `@Before`, `@After`, or `@Around`.

The final goal is to be able to answer questions such as:

- What kind of problem does AOP solve?
- How are Aspect, Advice, Pointcut, and Join Point different?
- How does Spring AOP actually intercept a method call?
- Why does advice run for one method but not another?
- How is `@Around` different from the other advice types?
- What happens when multiple Aspects match the same method?
- Why does self-invocation often make AOP behave differently from what we expect?
- When should AOP be used, and when should it be avoided?

---

## Learning flow

The module follows this sequence:

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

AOP should not be learned only as:

```text
What is @Before?
What is @After?
What is @Around?
```

and stop there.

The more important questions are:

```text
does the method call pass through a Spring proxy?
        ↓
does the pointcut match?
        ↓
which advice executes?
        ↓
when is the target method invoked?
```

---

## 1. AOP and Cross-Cutting Concerns

The first section answers:

> Why does AOP exist?

Concerns such as:

- logging;
- execution timing;
- metrics;
- auditing;
- authorization checks;
- transaction boundaries;
- tracing;

often appear in many classes or methods.

If these concerns are written directly into business logic, code is easily duplicated and business methods become harder to read.

This topic builds the mental model:

```text
business concern
        ≠
cross-cutting concern
```

and shows how AOP separates cross-cutting logic from the main code.

---

## 2. AOP Terminology

After understanding the problem, the following concepts must be distinguished precisely:

```text
Aspect
Advice
Pointcut
Join Point
Target Object
AOP Proxy
Weaving
```

In Spring AOP, remember especially:

```text
Join Point
= method execution

Pointcut
= rule that selects a subset of method executions

Proxy boundary
= whether an invocation actually enters the AOP chain
```

`weaving` is a general AOP term. Spring AOP performs runtime weaving through proxies; AspectJ also supports compile-time/load-time weaving and a broader join point model.

Spring AOP is not a full implementation of the complete AspectJ weaving model.

This module focuses mainly on **proxy-based Spring AOP**.

---

## 3. Spring AOP Proxy Mental Model

This is one of the most important chapters in the module.

Basic mental model:

```text
Caller
  ↓
Spring Proxy
  ↓
Advice chain
  ↓
Target object
```

The important observation is that the caller often invokes a proxy object supplied by Spring instead of calling the target object directly.

This topic covers:

- what a proxy is;
- what a target object is;
- where advice is inserted into the call chain;
- JDK dynamic proxies;
- subclass/CGLIB proxies;
- how a Spring-managed bean differs from an object created directly with `new`;
- why the proxy boundary determines whether AOP can participate.

At the end of this chapter, the learner should understand:

```text
Spring AOP does not "automatically run around every method".

Advice only has a chance to run when the method invocation passes through the appropriate proxy.
```

---

## 4. Pointcuts and Method Matching

A pointcut decides **which join points are selected**.

The module goes from simple pointcuts to more practical matching styles:

```text
execution(...)
within(...)
args(...)
this(...)
target(...)
@annotation(...)
```

The goal is not to memorize as much syntax as possible. The important question is:

```text
what is the pointcut matching against?

method signature?
package/type?
runtime argument?
annotation?
proxy type?
target type?
```

This topic also covers named pointcuts, composition with `&&`, `||`, and `!`, and the importance of keeping pointcuts narrow enough to avoid intercepting unintended methods.

The module also demonstrates `bean(...)`, a Spring-specific pointcut designator, and clarifies that Spring AOP uses AspectJ expression syntax without providing the entire join point model of full AspectJ weaving.

`@within(...)`, `@target(...)`, and other designators still exist in the AspectJ pointcut language, but the fundamentals section is not intended to become a complete syntax catalog. The goal is to understand representative matching categories and how to debug selection rules.

One experiment intentionally declares a method parameter as `Object` while passing a runtime `String`, making the difference between `args(...)` (runtime argument type) and `execution(...)` (declared method signature) observable.

---

## 5. Advice Lifecycle

Once a pointcut matches, an Aspect can run different advice types:

```text
@Before
@After
@AfterReturning
@AfterThrowing
```

The chapter observes the exact execution conditions and ordering of each type.

Especially distinguish:

```text
@After
        → runs when the method finishes, with finally-like semantics

@AfterReturning
        → runs only when the method returns normally

@AfterThrowing
        → runs when the method exits through a matching exception
```

The experiments include:

- a successful method;
- a method with a return value;
- a method that throws an exception.

This chapter also consolidates advice parameter binding such as `JoinPoint`, `args(...)`, `@annotation(...)`, `returning`, and `throwing`, so the learner can see how data is supplied to advice.

Beyond lifecycle, the chapter emphasizes two practical rules: use the **weakest advice type that satisfies the requirement**, and do not rely on source order when multiple advice methods of the same type in one Aspect match the same join point.

---

## 6. Around Advice and ProceedingJoinPoint

`@Around` is the most powerful advice because it wraps the target invocation.

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

This topic covers:

- `ProceedingJoinPoint`;
- `proceed()`;
- reading arguments;
- replacing arguments with `proceed(Object[])`;
- measuring execution time;
- observing return values;
- transforming return values when truly necessary;
- exception propagation;
- what happens when `proceed()` is not called;
- why calling `proceed()` multiple times can repeat target execution/side effects and is not a default recommended pattern.

The module also documents the boundary of `proceed(Object[])`: the demo uses Spring AOP proxy-runtime semantics; AspectJ-compiled around advice follows different argument-binding rules and should not be assumed to behave identically.

This chapter also makes clear that `@Around` can alter control flow and therefore needs careful use.

---

## 7. Custom Annotation-Based Aspect

A common pattern is to use a custom annotation to mark methods that need a specific cross-cutting behavior.

Example mental model:

```text
@TrackExecution
        ↓
@annotation(...) pointcut
        ↓
TimingAspect
```

This topic covers:

- creating a custom annotation;
- choosing the correct retention;
- choosing the correct target;
- matching annotations with a pointcut;
- reading annotation metadata;
- when annotation-based pointcuts are easier to maintain than package/method expressions.

The goal is to make the annotation a **declarative contract**, not just a demo marker.

---

## 8. Multiple Aspects and Ordering

One method can match multiple Aspects at the same time.

For example:

```text
LoggingAspect
TimingAspect
AuditAspect
        ↓
all match one service method
```

This topic observes:

- multiple advice methods participating in one call chain;
- `@Order`;
- outer/inner Aspects;
- entry and exit ordering of `@Around`;
- why a linear log can be misleading unless the call stack is visualized as nested.

If multiple advice methods have no explicit precedence contract, the module does not treat an accidentally observed log order as guaranteed behavior.

The target mental model is:

```text
Aspect A before
    Aspect B before
        target
    Aspect B after
Aspect A after
```

---

## 9. Proxy Limitations and Self-Invocation

This is a required chapter before applying AOP in real code.

Classic case:

```java
public void outer() {
    inner();
}
```

if `outer()` and `inner()` belong to the same target object, the call to `inner()` may simply be:

```text
this.inner()
```

and does not pass through the Spring proxy again.

Result:

```text
Caller → Proxy → outer()
                 ↓
              this.inner()

inner() does not pass through the Proxy a second time
```

This topic covers:

- self-invocation;
- proxy boundaries;
- class-based proxy limitations;
- final/private methods and interceptability;
- why "the annotation exists" does not mean "the advice definitely runs";
- refactoring boundaries instead of trying to bypass the proxy model.

The JDK proxy type-surface limitation is demonstrated directly in chapter `11.ProxyFactory`, where the same target is proxied with both JDK and CGLIB strategies for comparison.

This chapter also prepares the learner for similar issues with:

```text
@Transactional
@Async
@Cacheable
```

because many Spring features also rely on proxy/interceptor mechanics.

---

## 10. Practical Patterns and Pitfalls

After understanding the mechanism, the module applies it to practical patterns such as:

```text
execution logging
execution timing
auditing
metrics/tracing hooks
annotation-driven behavior
```

It also distinguishes cases where AOP is easy to misuse:

- pointcuts that are too broad;
- Aspects containing business logic;
- surprising argument/return-value changes;
- swallowed exceptions;
- hidden dependencies among many Aspects;
- overly complex ordering;
- mutable state in singleton Aspects without a clear concurrency contract;
- using AOP when a normal abstraction/service would be easier to understand.

Final mental model:

```text
AOP fits best when behavior is cross-cutting,
has a clear boundary,
and can be described declaratively.
```

---

## 11. Advanced - ProxyFactory and MethodInterceptor

After the fundamentals, the module moves from:

```text
"How do I use @Aspect?"
```

to:

```text
"Which abstractions actually build a Spring AOP proxy?"
```

This chapter creates proxies directly with `ProxyFactory` and uses `MethodInterceptor` to inspect the call chain:

```text
Proxy
→ MethodInterceptor
→ invocation.proceed()
→ Target
```

The same target is also proxied with two strategies:

```text
JDK Dynamic Proxy
CGLIB / class-based proxy
```

so the limitations discussed in chapters 3 and 9 have a concrete foundation.

The experiment also demonstrates the difference in **type surface**: a JDK proxy exposes the interface contract, while a class-based proxy subclasses the target and therefore still exposes appropriate concrete methods.

This difference also reconnects to `this(...)` and `target(...)`: `this` observes the proxy object, while `target` observes the target object behind the proxy.

---

## 12. Advanced - Advisor and Programmatic Pointcut

This chapter decomposes `@Around("pointcut")` into two independent abstractions:

```text
Pointcut
→ selects methods

Advice / MethodInterceptor
→ behavior after a match

Advisor
→ combines Pointcut + Advice
```

It then goes one level deeper into the Pointcut API:

```text
Pointcut
├── ClassFilter
└── MethodMatcher
```

The first experiment uses `NameMatchMethodPointcut` to demonstrate a static MethodMatcher (`isRuntime=false`). The second uses a custom `RuntimeArgumentPointcut` with `isRuntime=true`: for the same `writeWithMode(...)` method, argument `"audit"` matches the advice while `"plain"` goes directly to the target. This makes static method matching and runtime argument matching visibly separate phases.

---

## 13. Advanced - Auto Proxy Creator and Advised

Normal application code does not create a `ProxyFactory` for every bean.

Spring has auto-proxy infrastructure from the `BeanPostProcessor` family that can:

```text
discover candidate Advisors
→ decide which beans need proxies
→ build the interceptor chain
→ return proxies to consumers
```

This chapter inspects:

```text
AbstractAutoProxyCreator
Advised
Advisor[]
runtime proxy class
target class
```

For Spring Boot, this chapter also explains why the module does not need to declare `@EnableAspectJAutoProxy`: `spring-boot-starter-aop` plus Boot auto-configuration enables the AOP infrastructure by default, and the documented default `spring.aop.proxy-target-class=true` makes class-based proxying Boot's default strategy. The runtime endpoint separates explicit configuration (`Environment`) from real runtime evidence (`AutoProxyCreator`, `AopUtils`) rather than hard-coding a default as if the application explicitly configured it.

The goal is to understand why annotation-style AOP looks declarative while still having concrete infrastructure objects underneath.

---

## 14. Advanced - Introduction / Interface Enrichment

Introduction allows a proxy to expose an interface that the original target class does not implement.

Mental model:

```text
Target class
→ original business API

Spring AOP proxy after introduction
→ original business API
+ new interface
```

The module uses `@DeclareParents` to add `UsageTracked` to the proxy of one target service.

This is an advanced feature, mainly useful for understanding the capability of the proxy/advisor model rather than as a mandatory pattern for ordinary applications.

---

## 15. Advanced - Spring AOP Runtime Boundary and AspectJ

The final chapter clarifies this distinction:

```text
@AspectJ declaration style
≠
AspectJ compiler/weaver runtime
```

The module uses AspectJ annotation syntax and pointcut language, but the runtime is still proxy-based Spring AOP.

This chapter connects the entire mental model:

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

Full AspectJ weaving is a broader model and is outside the main implementation scope of this module.

The final chapter also defines a boundary for async/reactive methods: timing around `proceed()` measures method invocation time, which does not automatically mean the time when a `CompletableFuture`, `Mono`, or `Flux` finishes its workload.

The Spring AOP ↔ AspectJ boundary also includes `proceed(Object[])` semantics: the two runtimes can use the same @AspectJ-style source while still having different contracts for argument replacement.

---

## How to study this module

Each topic tries to follow this flow:

```text
Theory / Mental Model
        ↓
Question to prove
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

The README explains the theory and what should be observed.

The controller only triggers the experiment.

The service contains target-object behavior.

The Aspect contains the cross-cutting behavior being observed.

Experiments should stay small enough to answer one concrete question instead of making one endpoint demonstrate too many concepts at once.

---

## Expected outcome after completing the module

After this roadmap, the learner should be able to:

1. Identify cross-cutting concerns that are suitable for AOP.
2. Explain Aspect, Advice, Pointcut, Join Point, Target, and Proxy correctly.
3. Predict whether a method call passes through a Spring AOP proxy.
4. Write a sufficiently precise pointcut for a concrete use case.
5. Choose the correct advice type.
6. Use `@Around` and `ProceedingJoinPoint` with controlled behavior.
7. Build an annotation-based Aspect.
8. Explain ordering when multiple Aspects match.
9. Recognize self-invocation and common proxy limitations.
10. Know when AOP makes a design cleaner and when it makes code harder to understand.
11. Build JDK/CGLIB AOP proxies with `ProxyFactory` and `MethodInterceptor`.
12. Explain `Advisor = Pointcut + Advice` and use programmatic pointcuts.
13. Inspect `Advised`, the Advisor chain, and Spring's auto-proxy creator.
14. Understand how Introduction adds an interface to a proxy without modifying the target class.
15. Distinguish @AspectJ declaration style, the Spring AOP proxy runtime, and full AspectJ weaving.
