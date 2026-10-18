<a id="back-to-top"></a>

# Dependency Injection and Bean Resolution

## Menu
- [Constructor, Setter, and Field Injection](#injection-styles)
- [Type-based Autowiring](#type-based-autowiring)
- [How Spring Selects Dependency Candidates](#candidate-selection)
- [@Primary and @Qualifier](#primary-and-qualifier)
- [Collection, Optional, and Lazy Dependencies](#collection-and-optional-dependencies)
- [Dependency Injection vs Service Locator](#service-locator-vs-di)
- [Missing, Ambiguous, and Circular Dependency Failures](#dependency-resolution-failures)

## <a id="injection-styles">Constructor, Setter, and Field Injection</a>

<details>
<summary>Click for details</summary>

Injection style determines how a class exposes its dependency contract. Spring supports constructor, setter, and field injection, but they communicate different design intent.

**Constructor injection** is the normal choice for required collaborators:

```java
final class CheckoutService {
    private final PaymentGateway gateway;

    CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

The dependency is visible in the type's construction contract, the field can remain `final`, and tests can instantiate the class without a Spring container. Since Spring Framework 4.3, a class with a single constructor does not need `@Autowired` on that constructor.

**Setter injection** is useful when a dependency is genuinely optional or when reconfiguration after construction is part of the object contract. The object can exist before the setter is called, so the class must define what that partially configured state means.

**Field injection** is concise but hides dependencies from the constructor contract, prevents `final` dependency fields, and makes plain unit construction less explicit. It is supported, but it is usually a weaker default for application code.

Choose the style from the dependency's semantics rather than from annotation convenience. Required dependencies belong naturally in constructors; optional or replaceable dependencies may justify setters. If a class needs an excessive number of constructor arguments, that is often a design signal that the class has accumulated too many responsibilities rather than a reason to hide dependencies in fields.

</details>

- [Back to top](#back-to-top)

---

## <a id="type-based-autowiring">Type-based Autowiring</a>

<details>
<summary>Click for details</summary>

Spring's `@Autowired` resolution is fundamentally **type-driven**. At an injection point, the container first asks which registered beans are assignable to the required type.

```java
final class ReportService {
    ReportService(ReportRepository repository) { ... }
}
```

If exactly one `ReportRepository` candidate is eligible, the choice is straightforward. The concrete class can change without changing `ReportService` as long as it still satisfies the required type.

Type-based resolution works with classes as well as interfaces and honors Java's assignability rules. Generic type information can also participate in matching, so `Store<String>` and `Store<Order>` can be distinguished when their registered type metadata is available.

The important mental model is that a type match creates a **candidate set**, not necessarily a final answer. With zero candidates, a required dependency fails. With several candidates, Spring needs more information such as `@Primary`, `@Qualifier`, or other candidate metadata to select one.

Do not use broad supertypes such as `Object` merely to make injection flexible. A dependency type should express the capability the consumer actually needs; precise types make both the object graph and resolution failures easier to understand.

</details>

- [Back to top](#back-to-top)

---

## <a id="candidate-selection">How Spring Selects Dependency Candidates</a>

<details>
<summary>Click for details</summary>

Candidate selection is a narrowing process. Spring begins with beans assignable to the dependency type, removes beans that are not autowire candidates, then applies additional metadata to determine whether one candidate has priority or whether the injection point intentionally accepts several.

For a single-valued dependency, common signals include:

```text
required type
   ↓
eligible autowire candidates
   ↓
qualifier constraints, if present
   ↓
primary / priority signals
   ↓
unique candidate or resolution failure
```

Bean names can participate as a fallback discriminator in appropriate name-matching cases, but a dependency should not accidentally rely on a field or parameter name when the semantic distinction is important. In Spring Framework 6.1, constructor and method parameter-name fallback requires Java parameter metadata to be present — normally by compiling with `-parameters`; field names are available directly from the field itself. Use qualifier metadata when the role itself matters.

Candidate selection is also affected by how a bean was registered. A definition can be marked as not eligible for autowiring while remaining available for explicit lookup. Infrastructure can also register resolvable dependencies that are supplied directly by the container rather than represented as ordinary bean definitions.

When resolution surprises you, debug the candidate set in this order: verify registration, verify assignable type, inspect qualifiers/primary metadata, then inspect bean names and custom autowire-candidate rules. Jumping directly to adding `@Primary` can hide an unintended duplicate registration.

</details>

- [Back to top](#back-to-top)

---

## <a id="primary-and-qualifier">@Primary and @Qualifier</a>

<details>
<summary>Click for details</summary>

`@Primary` and `@Qualifier` solve different problems when several beans share a type.

`@Primary` says: **when no stronger narrowing rule selects another bean, prefer this candidate for a single-valued dependency**.

```java
@Bean
@Primary
ExchangeRateProvider liveRates() { ... }

@Bean
ExchangeRateProvider cachedRates() { ... }
```

`@Qualifier` expresses a semantic constraint at the injection point and on candidate metadata:

```java
CheckoutService(@Qualifier("offline") PaymentGateway gateway) { ... }
```

A qualifier value should be understood as metadata that narrows type-compatible candidates. It is not merely a request to look up a bean by string name, even though a bean name can be considered as a fallback match for a qualifier value.

Use `@Primary` when one implementation is the sensible default for most consumers. Use qualifiers when consumers require different roles, regions, protocols, or policies and those distinctions are part of the model. Custom qualifier annotations can make that role safer and more expressive than repeated string values.

If every injection point needs a different qualifier, reconsider whether the shared interface is representing one substitutable capability or several unrelated roles. Resolution metadata can express real distinctions, but it should not compensate for a confused domain model.

</details>

- [Back to top](#back-to-top)

---

## <a id="collection-and-optional-dependencies">Collection, Optional, and Lazy Dependencies</a>

<details>
<summary>Click for details</summary>

Not every dependency means "give me exactly one bean right now." Spring supports richer dependency shapes for collections, optional collaborators, and deferred access.

Injecting an array, `Collection<T>`, `List<T>`, `Set<T>`, or supported map shape can supply multiple beans of the element/value type. This is useful for strategy pipelines or plugin-like designs:

```java
final class PriceEngine {
    PriceEngine(List<PriceRule> rules) { ... }
}
```

Eligible `PriceRule` beans are collected rather than treated as an ambiguity error. Ordering metadata such as `Ordered` or `@Order` can influence ordered collection injection where ordering is supported.

For a dependency that may legitimately be absent, Spring can work with `Optional<T>`, nullable injection points, or `@Autowired(required = false)` in supported forms. Make optionality explicit only when the class has a meaningful behavior without that collaborator; otherwise a required dependency should fail during startup.

For deferred access, `ObjectProvider<T>` is a container-aware handle that can obtain a bean later, check availability, stream multiple candidates, or obtain a bean with arguments in supported scenarios. `@Lazy` on an injection point can instead inject a lazy-resolution proxy so target resolution is postponed until first use.

Deferred mechanisms are useful for scope bridging, expensive objects, or genuinely dynamic access, but they trade immediate startup validation for runtime resolution. Do not make every dependency lazy simply to make startup pass.

</details>

- [Back to top](#back-to-top)

---

## <a id="service-locator-vs-di">Dependency Injection vs Service Locator</a>

<details>
<summary>Click for details</summary>

Dependency Injection and Service Locator both let code obtain collaborators, but they place responsibility in different places.

With DI, the class declares what it needs:

```java
final class ShippingService {
    ShippingService(CarrierClient carrier) { ... }
}
```

With Service Locator style, the class actively asks a registry or container for the collaborator:

```java
CarrierClient carrier = context.getBean(CarrierClient.class);
```

The second form hides the dependency from the constructor contract and couples business code to a lookup mechanism. Tests now need to prepare that locator or context, and reading the class signature no longer reveals everything required for normal operation.

Spring exposes lookup APIs because some code genuinely needs them: bootstrap logic, framework adapters, diagnostics, dynamic plugin selection, or integration code may not know the target until runtime. The presence of `ApplicationContext#getBean` does not make lookup the preferred dependency mechanism inside ordinary application services.

A useful rule is to inject stable collaborators and reserve lookup for dependencies whose dynamic nature is part of the requirement. If a class calls the container merely to avoid adding a constructor parameter, the design has usually moved responsibility in the wrong direction.

</details>

- [Back to top](#back-to-top)

---

## <a id="dependency-resolution-failures">Missing, Ambiguous, and Circular Dependency Failures</a>

<details>
<summary>Click for details</summary>

Dependency-resolution failures are evidence about the shape of the object graph. Grouping them by cause makes startup errors much easier to diagnose.

**Missing dependency:** no eligible bean satisfies a required injection point. This often surfaces through `UnsatisfiedDependencyException` with a nested `NoSuchBeanDefinitionException`. Check whether the bean was registered, scanned, conditionally excluded, or requested under the wrong type/qualifier.

**Ambiguous dependency:** several beans remain equally eligible for a single-valued injection point. `NoUniqueBeanDefinitionException` identifies the competing candidates. Fix the model with a deliberate default (`@Primary`) or semantic narrowing (`@Qualifier`) instead of deleting an otherwise valid bean.

**Circular dependency:** bean A needs B while B eventually needs A. Constructor cycles are fundamentally impossible to satisfy because neither object can be constructed first:

```text
A constructor → B
      ↑         ↓
      └──── C ←─┘
```

For singleton beans using setter or field injection, Spring can resolve some cycles through early references, but this behavior is not a sound design target. Cycles make initialization order fragile, complicate proxying, and usually indicate responsibilities that should be separated. Prototype cycles cannot be resolved this way because prototype instances are not cached for reuse during creation.

Treat startup failure as useful feedback. Fix registration and ownership rather than weakening dependencies until the context starts. A graph that starts only because collaborators became optional or lazy can move a deterministic configuration error into a later production request.

</details>

- [Back to top](#back-to-top)
