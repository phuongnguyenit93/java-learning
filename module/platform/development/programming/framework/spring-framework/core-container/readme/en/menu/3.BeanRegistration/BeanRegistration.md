<a id="back-to-top"></a>

# Bean Registration and Configuration

## Menu
- [Explicit Registration vs Component Scanning](#explicit-vs-scanning)
- [Stereotype Annotations and Managed Components](#stereotype-components)
- [Component Scan Boundaries and Filters](#component-scan-boundaries)
- [@Configuration and @Bean](#configuration-and-bean)
- [Bean Names, Aliases, and Name Generation](#bean-names-and-aliases)
- [Full vs Lite Configuration Semantics](#full-vs-lite-configuration)
- [Composing Configuration with @Import](#import-composition)
- [Conditional Registration with @Conditional](#conditional-registration)
- [XML and Programmatic Bean Registration](#xml-and-programmatic-registration)

## <a id="explicit-vs-scanning">Explicit Registration vs Component Scanning</a>

<details>
<summary>Click for details</summary>

Before Spring can create a bean, the container needs a definition for it. The first design choice is therefore how those definitions enter the container.

With **explicit registration**, configuration names the components deliberately, for example through `@Bean` methods or programmatic registration. The wiring is easy to discover because the configuration itself shows which objects are part of the graph and how third-party classes are constructed.

With **component scanning**, Spring searches selected packages for candidate classes, normally classes annotated with `@Component` or a stereotype meta-annotated with it. This reduces repetitive registration for application-owned components but makes package boundaries and annotation conventions part of the configuration model.

Neither style is universally better. A useful rule is:

- prefer scanning for cohesive application components whose classes you own and can annotate;
- prefer explicit `@Bean` registration when construction needs visible configuration, when integrating third-party classes, or when you want the object graph to be obvious in one place;
- mix the two when each solves a different part of the graph clearly.

The important architectural point is that both styles converge on bean definitions. Scanning is not a separate container; it is one discovery mechanism that contributes definitions to the same `BeanFactory` used by explicitly registered beans.

</details>

- [Back to top](#back-to-top)

---

## <a id="stereotype-components">Stereotype Annotations and Managed Components</a>

<details>
<summary>Click for details</summary>

`@Component` marks a class as a candidate for component scanning. Specialized stereotypes such as `@Service`, `@Repository`, and `@Controller` are themselves meta-annotated with `@Component`, so they participate in the same discovery mechanism while communicating a more specific role.

That role matters for readability and, in some cases, for framework integration. For example, `@Repository` expresses persistence responsibility and participates in Spring's persistence exception translation infrastructure when the relevant post-processor is present. The stereotype should therefore describe what the class represents, not merely serve as a convenient way to make scanning find it.

```java
@Service
final class BillingService {
    private final InvoiceRepository invoices;

    BillingService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }
}
```

When a scan finds `BillingService`, Spring registers a bean definition; the class is not instantiated merely because the annotation exists. Creation still follows normal container rules for dependency resolution, scope, post-processing, and lifecycle.

Custom composed stereotypes are possible by meta-annotating your own annotation with `@Component` or another stereotype. In Spring Framework 6.1, if a composed annotation is intended to expose or override attributes such as the component name, declare that relationship explicitly with `@AliasFor` rather than relying on implicit attribute-name conventions.

</details>

- [Back to top](#back-to-top)

---

## <a id="component-scan-boundaries">Component Scan Boundaries and Filters</a>

<details>
<summary>Click for details</summary>

Component scanning is only useful when its boundary is intentional. A scan that is too narrow silently misses components; a scan that is too broad can register infrastructure or application classes that were never meant to share the same context.

`@ComponentScan` can define packages directly or use type-safe marker classes through `basePackageClasses`. If no package is specified, scanning starts from the package of the class declaring `@ComponentScan`.

```java
@Configuration
@ComponentScan(basePackageClasses = BillingModule.class)
class BillingConfig { }
```

By default, Spring includes classes carrying `@Component` or a meta-annotation based on it. Include and exclude filters can refine that set by annotation, assignable type, AspectJ pattern, regex, or custom `TypeFilter` logic.

Treat filters as configuration policy, not as a substitute for sensible package design. Complex include/exclude rules make the effective graph harder to predict and test. Stable package boundaries plus small, explicit filters are usually easier to reason about.

Also remember that scanning only discovers candidates. It does not override normal bean-resolution rules: two scanned implementations of the same interface can still create ambiguity, and naming collisions can still fail registration or require an intentional naming strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-and-bean">@Configuration and @Bean</a>

<details>
<summary>Click for details</summary>

Java configuration lets ordinary Java methods describe bean creation. A class annotated with `@Configuration` is a source of bean definitions, and each `@Bean` method describes how one bean should be obtained.

```java
@Configuration
class PaymentConfig {
    @Bean
    PaymentGateway paymentGateway(HttpClient client) {
        return new StripeGateway(client);
    }

    @Bean
    HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
```

The method parameters are dependency points. Spring resolves `HttpClient` from the container before invoking `paymentGateway`, so configuration methods do not need to call other factory methods directly just to obtain dependencies.

`@Bean` is especially useful for classes you cannot annotate, for factories requiring explicit construction logic, or when configuration should make a dependency choice visible. The returned object becomes the managed bean; Spring applies scope, lifecycle, post-processing, and dependency rules to that bean like any other registered definition.

Keep factory methods focused on object construction. Heavy business logic, external side effects, or hidden runtime decisions inside `@Bean` methods make context startup harder to understand and can turn configuration failures into surprising operational failures.

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-names-and-aliases">Bean Names, Aliases, and Name Generation</a>

<details>
<summary>Click for details</summary>

Every bean definition has a name. Names matter for diagnostics, explicit lookup, qualifiers, aliases, and cases where type information alone cannot uniquely identify a bean.

For a component discovered by scanning, the default name is typically derived from the short class name, for example `BillingService` becomes `billingService`. A stereotype can supply an explicit name when a stable semantic name is useful.

For `@Bean`, the default bean name is the method name:

```java
@Bean
DataSource reportingDataSource() { ... }
```

The default name is `reportingDataSource`. The `name`/`value` attributes of `@Bean` can provide an explicit primary name and additional aliases. An alias is another name that resolves to the same canonical bean definition; it does not create a second bean definition. Instance identity still follows the bean's scope, so repeated lookups of a prototype through its canonical name or an alias can produce different instances.

Do not build application design around fragile default names when a name is part of an integration contract. Conversely, avoid assigning explicit names to every bean without a reason: type-based injection is usually clearer for normal dependencies, while qualifiers can express semantic distinctions when multiple beans share a type.

Naming strategy also affects scanning collisions. If two components resolve to the same bean name, registration may fail unless configuration deliberately changes the naming policy. Such collisions are often a sign that package boundaries or component identities need clarification.

</details>

- [Back to top](#back-to-top)

---

## <a id="full-vs-lite-configuration">Full vs Lite Configuration Semantics</a>

<details>
<summary>Click for details</summary>

Spring distinguishes **full** configuration semantics from **lite** `@Bean` processing. The difference becomes visible when one `@Bean` method calls another directly.

With ordinary `@Configuration` semantics (`proxyBeanMethods = true`, the default), Spring can enhance the configuration class so an inter-bean call to an interceptable instance `@Bean` method is routed through the container. Calling the singleton `repository()` method in the example below therefore returns the managed singleton rather than blindly constructing a second instance.

```java
@Configuration
class AppConfig {
    @Bean
    Repository repository() { return new JdbcRepository(); }

    @Bean
    Service service() { return new Service(repository()); }
}
```

In **lite** mode, such as `@Configuration(proxyBeanMethods = false)` or a `@Bean` method declared on a class that is not treated as full configuration, direct Java calls are ordinary method calls. `repository()` in the example would execute like normal Java and can create a new object outside the managed singleton lookup path.

Full-mode interception has Java-level limits: the configuration class and relevant instance `@Bean` methods must remain overridable for subclass-based enhancement. Final/private methods cannot participate in that interception path, and calls to `static @Bean` methods are never intercepted.

This is why parameter injection is usually the clearest configuration style:

```java
@Bean
Service service(Repository repository) {
    return new Service(repository);
}
```

It works naturally in both full and lite modes and makes the dependency edge explicit. Use full configuration when inter-bean method interception is intentionally required; use lite configuration when methods are independent factories and avoiding configuration-class proxying is desirable.

</details>

- [Back to top](#back-to-top)

---

## <a id="import-composition">Composing Configuration with @Import</a>

<details>
<summary>Click for details</summary>

Large configuration should be composed rather than accumulated in one class. `@Import` lets one configuration class bring another configuration source into the same application context.

```java
@Configuration
@Import({PersistenceConfig.class, BillingConfig.class})
class ApplicationConfig { }
```

For ordinary application configuration, importing configuration classes is the most readable form: the dependency between configuration modules is visible and Spring processes the imported classes as configuration sources.

The mechanism is also used by framework infrastructure. `@Import` can work with `ImportSelector`, `DeferredImportSelector`, and `ImportBeanDefinitionRegistrar`, allowing infrastructure code to choose or register definitions programmatically from annotation metadata. These are powerful extension mechanisms, but most application code should not reach for them when a normal configuration class or `@Bean` method is sufficient.

Composition should preserve understandable ownership. If feature A imports feature B, that relation should reflect a real configuration dependency. A dense web of imports makes it difficult to know why a bean exists and can hide module coupling just as effectively as an excessively broad component scan.

</details>

- [Back to top](#back-to-top)

---

## <a id="conditional-registration">Conditional Registration with @Conditional</a>

<details>
<summary>Click for details</summary>

Sometimes a bean or configuration should exist only when a specific condition is true. Spring Framework provides the general `@Conditional` mechanism for that purpose.

A condition implements `Condition` and decides whether the annotated configuration component should be registered:

```java
final class ProductionCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().acceptsProfiles(Profiles.of("prod"));
    }
}
```

`@Conditional` can be placed on `@Bean` methods and on component types, including `@Configuration` classes; it can also be composed as a meta-annotation. The condition is evaluated while configuration metadata is being processed, before the target bean is created. A condition may inspect the `Environment`, resource loader, class loader, registry context, and annotation metadata exposed through `ConditionContext`.

Conditions belong to the configuration phase. The `Condition` contract requires them to determine outcomes from configuration state without interacting with bean instances. Do not obtain or invoke application beans from `matches`; registration is still being decided at that point.

Use `@Conditional` when conditional registration is truly a framework/configuration concern. This section covers only the Spring Framework contract; higher-level Spring Boot conditional-registration facilities are outside this module.

</details>

- [Back to top](#back-to-top)

---

## <a id="xml-and-programmatic-registration">XML and Programmatic Bean Registration</a>

<details>
<summary>Click for details</summary>

Java annotations are not the only way to feed definitions into the Spring container. XML and programmatic APIs remain part of the same configuration model and can be appropriate at integration boundaries or in infrastructure code.

XML can define bean classes, constructor arguments, properties, scopes, aliases, and other metadata without modifying application classes. A context such as `ClassPathXmlApplicationContext` reads those definitions and builds the same kind of bean factory used by Java configuration.

Programmatic registration gives code direct control. For example, `GenericApplicationContext` exposes registration APIs, and `AnnotationConfigApplicationContext` can register configuration classes before refresh. Lower-level infrastructure can also register `BeanDefinition` objects directly through a `BeanDefinitionRegistry`.

These mechanisms are alternatives for supplying metadata, not separate dependency-injection engines:

```text
XML
Java configuration
component scanning
programmatic registration
        ↓
BeanDefinition registry
        ↓
same container lifecycle and resolution rules
```

Choose the least surprising source for the ownership boundary. Java configuration is usually convenient for application code; XML can remain useful where declarative external wiring is already established; programmatic registration is valuable when definitions must be generated dynamically. Mixing styles is valid, but the resulting graph should still be easy to trace.

</details>

- [Back to top](#back-to-top)
