<a id="back-to-top"></a>

# Declarative Transaction Demarcation

## Menu
- [Declarative Transaction Model](#transaction-declarative-model)
- [Enabling Annotation-Driven Transaction Management](#transaction-enable-management)
- [Transactional Metadata and Attribute Resolution](#transactional-metadata)
- [TransactionInterceptor Runtime Flow](#transaction-interceptor)
- [Proxy-Mode Interception Boundaries](#transaction-proxy-boundary)
- [Proxy Mode vs AspectJ Mode](#transaction-aspectj-mode)
- [Metadata Precedence and Transaction Manager Qualification](#transaction-metadata-precedence)

## <a id="transaction-declarative-model">Declarative Transaction Model</a>

<details>
<summary>Click for details</summary>

Declarative transaction management separates **transaction policy** from the business method body. Instead of manually calling begin/commit/rollback, application code declares metadata and lets Spring wrap eligible method invocations with transaction advice.

```text
caller
  ↓
transaction interceptor/advice
  ↓ begin/join transaction
target business method
  ↓
commit or rollback according to outcome/policy
```

This model improves consistency when many service methods follow stable transaction rules. The method still owns business behavior; Spring infrastructure owns the transaction lifecycle around it.

Declarative does not mean invisible magic. Understanding metadata resolution, proxy boundaries, and manager selection is essential for diagnosing why a method did or did not run transactionally.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-enable-management">Enabling Annotation-Driven Transaction Management</a>

<details>
<summary>Click for details</summary>

`@Transactional` is metadata; by itself it does not activate transaction interception. In Java configuration, `@EnableTransactionManagement` imports the infrastructure that discovers transactional metadata and registers the advisor/interceptor needed to apply it to Spring beans.

```java
@Configuration
@EnableTransactionManagement
class TxConfig {
    @Bean
    PlatformTransactionManager transactionManager(...) { ... }
}
```

The context still needs an appropriate `TransactionManager`. With multiple managers, configuration or qualifiers must identify which manager is intended.

`@EnableTransactionManagement` defaults to `AdviceMode.PROXY`. `proxyTargetClass` controls interface-based vs class-based proxies in proxy mode, and `order` controls advisor ordering. Deep proxy mechanics belong to the AOP module; here the key is that transaction behavior requires runtime infrastructure, not annotation presence alone.

</details>

- [Back to top](#back-to-top)

---

## <a id="transactional-metadata">Transactional Metadata and Attribute Resolution</a>

<details>
<summary>Click for details</summary>

Spring reads `@Transactional` metadata through a `TransactionAttributeSource`. For Spring's annotation, `AnnotationTransactionAttributeSource` parses metadata into Spring's internal `TransactionAttribute` model, ultimately represented with rule-based transaction attributes.

Metadata can be declared at class or method level. A class-level annotation supplies defaults for eligible methods; a method-level declaration can override those defaults for that method.

Prefer annotations on concrete classes/methods. Interface annotations can work with Spring proxy infrastructure, but AspectJ weaving does not inherit Java interface annotations in the same way, which can make behavior depend on interception mode.

The practical lesson: transaction policy is resolved from metadata against the **actual target method/class**, then consumed by transaction advice.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-interceptor">TransactionInterceptor Runtime Flow</a>

<details>
<summary>Click for details</summary>

`TransactionInterceptor` is the AOP Alliance `MethodInterceptor` that applies Spring's transaction workflow around an invocation. Conceptually it performs four steps:

```text
1. resolve TransactionAttribute for method
2. choose TransactionManager
3. create/join transaction as necessary
4. invoke target, then complete by commit/rollback rules
```

The underlying workflow is implemented by `TransactionAspectSupport`, which also supports both imperative and reactive transaction managers when the method signature/execution model matches.

This explains why transaction behavior depends on the intercepted invocation. If a call never reaches the interceptor—such as ordinary self-invocation in proxy mode—the transaction workflow is never entered for that inner method.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-proxy-boundary">Proxy-Mode Interception Boundaries</a>

<details>
<summary>Click for details</summary>

In the default proxy mode, transaction advice runs only for calls that **enter through the proxy**. A method calling another method on `this` stays inside the target object and bypasses the proxy, so the inner method's `@Transactional` metadata is not independently intercepted.

Method visibility also depends on proxy type. In Spring 6.x, class-based proxies can make protected and package-visible methods transactional by default. Interface-based proxies require the transactional method to be public and exposed through the proxied interface. Private methods cannot be overridden/intercepted by a subclass proxy.

```text
external caller → proxy → target method   ✓ intercepted
target method → this.otherMethod()        ✗ bypasses proxy
```

Do not “fix” self-invocation by adding more annotations. Move the boundary to another bean, restructure the call, or deliberately choose a different interception strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-aspectj-mode">Proxy Mode vs AspectJ Mode</a>

<details>
<summary>Click for details</summary>

`AdviceMode.ASPECTJ` is the alternative to proxy-based transaction interception. Instead of routing calls through a proxy, Spring's transaction AspectJ aspect weaves the target class bytecode, so self-invocation can also be advised.

The trade-off is infrastructure complexity: AspectJ mode requires `spring-aspects` plus compile-time or load-time weaving. It also changes the interception model application developers must understand.

Use AspectJ mode when the application genuinely needs woven transaction semantics, not as the first response to every self-invocation problem. Often a clearer service boundary or bean decomposition produces simpler design.

Deep weaving/pointcut mechanics belong to the AOP module; transaction-management only needs to understand how the interception choice changes transaction reachability.

</details>

- [Back to top](#back-to-top)

---

## <a id="transaction-metadata-precedence">Metadata Precedence and Transaction Manager Qualification</a>

<details>
<summary>Click for details</summary>

Transaction metadata resolution favors the most specific applicable declaration. A method-level `@Transactional` on the target class can override class-level defaults; target-class metadata is preferred over less-specific declarations found through interfaces or parent types according to Spring's transaction attribute resolution rules.

Manager selection is part of the same effective policy. `@Transactional(transactionManager = "ordersTxManager")` chooses a manager by qualifier/bean name. A composed annotation can also carry transaction semantics for repeated domain policies.

Keep precedence understandable. A class with broad defaults and a few intentional method overrides is easier to reason about than many layers of inherited/composed annotations with different managers and rollback rules.

</details>

- [Back to top](#back-to-top)
