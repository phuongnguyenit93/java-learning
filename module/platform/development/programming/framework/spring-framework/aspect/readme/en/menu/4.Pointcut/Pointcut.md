<a id="back-to-top"></a>

# Aspect Declaration and Pointcut Design

## Menu
- [1. Choosing a declaration style: @AspectJ and schema-based AOP](#aop-declaration-styles)
- [2. Enabling @AspectJ auto-proxy support in Spring Framework](#spring-aop-enablement)
- [3. What is the pointcut matching against?](#pointcut-mental-model)
- [4. Supported designators, composition, and runtime context](#pointcut-designators-and-composition)
- [5. Demo in this module](#pointcut-demo)
- [6. Spring AOP does not support the full AspectJ join point model](#spring-aop-pointcut-boundary)
- [7. Conclusion](#pointcut-conclusion)

A pointcut decides **which join points are selected**.

## <a id="aop-declaration-styles">1. Choosing a declaration style: @AspectJ and schema-based AOP</a>

<details>
<summary>Click for details</summary>

Spring Framework offers two high-level declaration styles on top of the same proxy-based AOP infrastructure.

**@AspectJ style** uses ordinary Java classes with AspectJ annotations:

```java
@Aspect
@Component
class AuditAspect {

    @Before("execution(* com.example.order..*(..))")
    void audit() {
        // cross-cutting behavior
    }
}
```

**Schema-based AOP** declares aspects, pointcuts, and advice through the Spring XML `aop` namespace.

The styles differ in configuration syntax, but they share the same important runtime model:

```text
declaration
→ Spring creates advisors/interceptors
→ eligible objects are proxied
→ method calls through the proxy can be advised
```

Using the @AspectJ style does not activate the AspectJ compiler or load-time weaving. Using XML does not create a separate AOP engine either. Both are Spring AOP unless full AspectJ weaving is configured explicitly.

For ordinary Java configuration, @AspectJ style is usually easier to keep near the Java code it describes. Schema-based AOP remains useful in XML-centric applications or when configuration needs to stay outside the aspect class. The learning goal is to understand the runtime contract rather than treat either syntax as a different join-point model.

### References

- Spring Framework Reference — @AspectJ support
- Spring Framework Reference — Schema-based AOP Support

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-enablement">2. Enabling @AspectJ auto-proxy support in Spring Framework</a>

<details>
<summary>Click for details</summary>

Declaring an `@Aspect` class is only one part of the setup. Spring must also enable its @AspectJ auto-proxy support and the aspect must exist as a bean in the relevant `ApplicationContext`.

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

The AspectJ `aspectjweaver` library is also required on the classpath because Spring uses AspectJ's parsing/matching support for @AspectJ pointcut expressions. That dependency does **not** mean bytecode weaving is occurring.

At a high level, enablement produces this lifecycle:

```text
ApplicationContext discovers aspect beans
        ↓
Spring builds advisor metadata
        ↓
auto-proxy infrastructure checks candidate beans
        ↓
eligible bean reference may be exposed as an AOP proxy
```

`@EnableAspectJAutoProxy` also has proxy-related options such as `proxyTargetClass` and `exposeProxy`. Those options change proxy behavior; they do not broaden Spring AOP beyond method-execution join points.

Keep framework and application defaults separate. Spring Boot may auto-configure AOP and choose proxy settings for a Boot application, but this module describes the Spring Framework mechanism itself.

### References

- Spring Framework Reference — Enabling @AspectJ Support

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-mental-model">3. What is the pointcut matching against?</a>

<details>
<summary>Click for details</summary>

In Spring AOP, every join point that a pointcut can ultimately select is a **method execution** reached through the proxy model. Pointcut designators then constrain that execution in different ways.

Signature/type-oriented examples:

```text
execution(...)
→ matches method execution signatures

within(...)
→ restricts execution to methods declared within matching types
```

Runtime-context examples:

```text
this(...)
→ checks the Spring AOP proxy object

target(...)
→ checks the target object behind the proxy

args(...)
→ checks runtime argument types
```

Annotation-oriented examples:

```text
@annotation(...)
→ annotation on the method being executed

@within(...)
→ annotation on the type within which the method is declared

@target(...)
→ annotation on the runtime target type

@args(...)
→ annotations on runtime argument types
```

Spring adds one designator of its own:

```text
bean(...)
→ matches by Spring bean name
```

The distinction between `this` and `target` is especially important in proxy-based AOP:

```text
this
→ proxy identity / proxy type

target
→ application target identity / target type
```

A pointcut is therefore more than a text pattern over method names. It can combine static structure with runtime context, but the selected thing is still a proxy-reachable method execution.

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-designators-and-composition">4. Supported designators, composition, and runtime context</a>

<details>
<summary>Click for details</summary>

Spring AOP supports these AspectJ pointcut designators for its execution-only model:

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

and Spring adds `bean`.

Pointcut expressions can be composed with:

```text
&&  AND
||  OR
!   NOT
```

A common design approach is to define small named pointcuts that express architecture and then compose them:

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

This is easier to review than repeating a long expression in every advice annotation.

Pointcuts can also **bind context** into advice parameters. For example:

```java
@Before("execution(* *(..)) && args(orderId)")
void observe(String orderId) {
    // orderId is the runtime argument bound by args(...)
}
```

Other chapters use `@annotation(annotation)` to bind annotation metadata, `returning` to bind a successful return value, and `throwing` to bind an exception.

A useful design rule is to separate three questions:

```text
What structural area is this policy for?
→ within(...) / execution(...)

What runtime context matters?
→ this(...) / target(...) / args(...)

What metadata is the explicit contract?
→ @annotation(...) / @within(...) / related annotation designators
```

Prefer narrow pointcuts that describe a stable boundary such as a service layer or an explicit annotation contract. Expressions coupled to incidental package names, generated types, or overly broad `execution(* *(..))` patterns make accidental matches more likely.

### References

- Spring Framework Reference — Declaring a Pointcut

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-demo">5. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
PointcutController#comparePointcuts()
```

Endpoint:

```text
GET /aop/pointcut/compare
```

The controller calls these methods in sequence:

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

The response `events` shows a different matching rule for each target call:

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

(both pointcut:this-proxy-type and pointcut:target-type appear)
target:byThisAndTarget

pointcut:bean-name
target:byBean

target:unmatched
```

`byRuntimeArgs(Object)` intentionally declares its parameter as `Object` while the controller passes a `String`. An `args(java.lang.String)` pointcut still matches because `args` evaluates the runtime argument type. By comparison, `execution` describes the declared method execution signature.

`byWithin()` demonstrates named composition. `byThisAndTarget()` demonstrates two different runtime identities: `this` observes the proxy and `target` observes the target object. Both advice methods can match the same invocation; their relative log order is not a contract because no explicit precedence is declared between them.

`bean(pointcutService)` proves the Spring-specific bean-name matcher. `unmatched()` provides a baseline showing that a proxy call with no matching pointcut reaches the target without that advice.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-aop-pointcut-boundary">6. Spring AOP does not support the full AspectJ join point model</a>

<details>
<summary>Click for details</summary>

Spring AOP uses the AspectJ pointcut expression language, but Spring's runtime is still proxy-based and execution-only.

Therefore this implication is wrong:

```text
Spring understands AspectJ pointcut syntax
→ Spring AOP supports every AspectJ join point
```

Designators tied to the broader AspectJ join-point model are not supported by Spring AOP. Examples include:

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

Using an unsupported designator in a Spring AOP pointcut expression results in a configuration/parsing failure rather than silently enabling that capability.

This boundary explains when full AspectJ weaving becomes relevant. If a requirement needs field access, constructor execution, call-site join points, or control-flow pointcuts such as `cflow`, a proxy around Spring bean method execution cannot provide that model.

### References

- Spring Framework Reference — Declaring a Pointcut
- Spring Framework Reference — Using AspectJ with Spring Applications

</details>

- [Back to top](#back-to-top)

---

## <a id="pointcut-conclusion">7. Conclusion</a>

<details>
<summary>Click for details</summary>

When advice does not run, split the diagnosis into independent checks:

```text
1. Was an AOP proxy created?
2. Did the invocation cross that proxy?
3. Is the pointcut valid for Spring AOP's method-execution model?
4. Did that pointcut match the current signature/context/metadata?
```

Good pointcut design makes the cross-cutting contract visible and predictable. The next chapter focuses on one especially useful form of that contract: an annotation placed explicitly on the operation being advised.

</details>

- [Back to top](#back-to-top)
