<a id="back-to-top"></a>

# Environment, Property Sources, Profiles, and SpEL

## Menu
- [The Environment Abstraction](#environment-abstraction)
- [Property Sources and Precedence](#property-sources-and-precedence)
- [Property Placeholders](#property-placeholders)
- [Profiles and @Profile](#profiles)
- [Why SpEL Exists](#spel-purpose)
- [The SpEL Evaluation Model](#spel-evaluation-model)
- [SpEL in Bean Definitions and @Value](#spel-in-bean-definitions)
- [Boundary with Spring Boot Externalized Configuration](#boot-config-boundary)

## <a id="environment-abstraction">The Environment Abstraction</a>

<details>
<summary>Click for details</summary>

`Environment` gives the container one model for two related concerns: **which bean definitions are eligible for registration** and **which named properties are available to configuration code**.

The two sides are intentionally different:

```text
profiles
→ decide whether profile-guarded definitions participate in the context

properties
→ resolve named values from an ordered set of PropertySource objects
```

A bean can depend on the narrow `Environment` API instead of knowing where configuration came from:

```java
@Bean
ClientSettings clientSettings(Environment environment) {
    String endpoint = environment.getRequiredProperty("client.endpoint");
    int timeout = environment.getProperty("client.timeout-ms", Integer.class, 2000);
    return new ClientSettings(endpoint, timeout);
}
```

This keeps the consumer independent from whether a value originated in JVM system properties, OS environment variables, an explicitly added property file, or another registered `PropertySource`.

`ConfigurableEnvironment` exposes mutation operations such as setting active profiles or adding property sources. Those operations belong to context setup and should normally be completed before `refresh()`, because profile evaluation and bean-definition processing happen while the container is being built. Application services should usually consume the read-oriented `Environment` contract rather than mutate global configuration after startup.

The key boundary is that `Environment` is a Spring Framework abstraction. It does not itself define Spring Boot's Config Data loading rules.

### References

- Spring Framework Reference — Environment Abstraction

</details>

- [Back to top](#back-to-top)

---

## <a id="property-sources-and-precedence">Property Sources and Precedence</a>

<details>
<summary>Click for details</summary>

A `PropertySource` is a named view over key-value data. An `Environment` holds multiple sources in an explicit order and searches them by precedence. For a given key, the first source that supplies a value wins.

For example, a plain `StandardEnvironment` includes JVM system properties and OS environment variables. You can add an application-specific source ahead of them:

```java
ConfigurableEnvironment environment = context.getEnvironment();
MutablePropertySources sources = environment.getPropertySources();

Map<String, Object> overrides = Map.of("client.timeout-ms", 500);
sources.addFirst(new MapPropertySource("localOverrides", overrides));
```

Now `client.timeout-ms` from `localOverrides` wins over the same key in lower-precedence sources.

`@PropertySource` is a convenient Framework-level way to contribute a resource-backed property source:

```java
@Configuration
@PropertySource("classpath:client.properties")
class ClientConfiguration {
}
```

Do not treat "properties" as one merged map with a mysterious winner. Precedence is the result of ordered sources. When override order is part of the application's contract, make that order deliberate. Relying on incidental discovery order among independently scanned configuration classes makes duplicate keys difficult to reason about.

Also distinguish absence from an explicitly present value. `Environment.getProperty(...)` can return `null`; `getRequiredProperty(...)` expresses that startup should fail if the key is missing.

Framework property-source ordering is the mechanism taught here. More elaborate loading and precedence conventions supplied by Spring Boot belong to the Boot configuration curriculum.

</details>

- [Back to top](#back-to-top)

---

## <a id="property-placeholders">Property Placeholders</a>

<details>
<summary>Click for details</summary>

Property placeholders insert a value identified by a key. The familiar form is:

```text
${client.timeout-ms}
${client.timeout-ms:2000}
```

The second form supplies a default. A placeholder is fundamentally a **lookup**, not an arbitrary expression.

`@Value` can use placeholders at injection points:

```java
@Component
final class RemoteClient {
    private final int timeoutMs;

    RemoteClient(@Value("${client.timeout-ms:2000}") int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
```

After placeholder resolution, Spring can convert the resulting text to the injection-point type using the conversion facilities available in that container. Simple scalar targets such as `int` work with the standard type-conversion infrastructure. Richer application types require suitable conversion support to be configured; a plain Framework context should not be assumed to provide every formatting converter that higher-level Spring projects may configure. The conversion step is separate from finding the property.

There is an important strictness choice in a plain Framework context. Spring can provide a default lenient embedded value resolver: an unresolved placeholder can remain as its literal placeholder text. If missing values must fail context initialization, explicitly register a `PropertySourcesPlaceholderConfigurer`. With Java configuration, declare that `@Bean` method as `static` to avoid the early-instantiation lifecycle conflicts that apply to `BeanFactoryPostProcessor` beans:

```java
@Bean
static PropertySourcesPlaceholderConfigurer placeholders() {
    return new PropertySourcesPlaceholderConfigurer();
}
```

That configurer resolves `${...}` values against the `Environment` and its `PropertySources` while bean metadata is being prepared.

Keep the syntax distinction clear: `${...}` requests a property value; `#{...}` asks SpEL to evaluate an expression. Use the simpler placeholder whenever a lookup is all you need.

</details>

- [Back to top](#back-to-top)

---

## <a id="profiles">Profiles and @Profile</a>

<details>
<summary>Click for details</summary>

Profiles are a **bean-registration condition**. They answer "should this definition participate in this environment?" rather than "which branch should this service execute for each request?"

```java
@Configuration
@Profile("development")
class DevelopmentConfiguration {
    @Bean
    PaymentGateway paymentGateway() {
        return new StubPaymentGateway();
    }
}

@Configuration
@Profile("production")
class ProductionConfiguration {
    @Bean
    PaymentGateway paymentGateway() {
        return new RemotePaymentGateway();
    }
}
```

At context bootstrap, active profiles determine which definitions are eligible. If `@Profile` guards a configuration class, its associated bean definitions and imports are skipped when the condition does not match.

Profile expressions can describe simple composition:

```text
production & eu
development | test
!cloud
```

`&` and `|` must be grouped with parentheses when combined in one expression. Multiple profiles may be active at once; profiles are not inherently mutually exclusive.

If no profile is explicitly active, Spring uses its default profile set, whose conventional name is `default`. Active/default profiles can be configured through the `Environment` and should be established before the context is refreshed.

Use profiles for coarse environmental composition of the object graph. If a business rule changes per customer, tenant, request, or data value at runtime, model that rule in application code instead of hiding it in profile-based registration.

</details>

- [Back to top](#back-to-top)

---

## <a id="spel-purpose">Why SpEL Exists</a>

<details>
<summary>Click for details</summary>

A property placeholder can fetch a value, but sometimes container metadata needs to **compute** a value from an object graph. Spring Expression Language (SpEL) exists for that expression-evaluation role.

SpEL can navigate properties, invoke methods, use operators, work with collections, refer to types, variables, or beans, and produce a typed result. For example:

```java
ExpressionParser parser = new SpelExpressionParser();
Expression expression =
        parser.parseExpression("name.toUpperCase()");

String value = expression.getValue(customer, String.class);
```

This is useful when configuration is naturally declarative and a small expression captures the relationship more clearly than another adapter class.

The power also creates a design trade-off. A large SpEL expression hides logic in a string, loses normal Java refactoring support, and fails later than ordinary Java code. If an expression grows into business logic or needs unit-level reasoning, move the behavior into Java and reference the resulting bean or property instead.

SpEL belongs in this module because Spring's core container can evaluate it in bean metadata and `@Value`. Other Spring modules may use SpEL for their own domains, but those modules own the meaning of the expression in that domain.

Do not evaluate untrusted user-supplied SpEL as if it were a harmless template language. Full SpEL can reach methods, constructors, types, and other objects exposed through the evaluation context.

</details>

- [Back to top](#back-to-top)

---

## <a id="spel-evaluation-model">The SpEL Evaluation Model</a>

<details>
<summary>Click for details</summary>

Programmatic SpEL has three main roles:

```text
ExpressionParser
→ parses expression text

Expression
→ reusable parsed representation

EvaluationContext
→ controls what names, properties, methods, types, variables,
  bean references, and conversions mean during evaluation
```

The root object supplies the default object graph:

```java
ExpressionParser parser = new SpelExpressionParser();
Expression expression = parser.parseExpression("address.city");

String city = expression.getValue(customer, String.class);
```

For richer evaluation, `StandardEvaluationContext` exposes the full configurable model:

```java
StandardEvaluationContext context = new StandardEvaluationContext(customer);
context.setVariable("discount", new BigDecimal("0.10"));

BigDecimal amount = parser
        .parseExpression("orderTotal * (1 - #discount)")
        .getValue(context, BigDecimal.class);
```

Variables use `#name`. Bean references such as `@pricingPolicy` require a `BeanResolver` in the evaluation context. Type conversion is also part of evaluation, so the requested result type can influence conversion of the computed value.

`SimpleEvaluationContext` deliberately exposes a smaller configurable subset and is useful when full language features are unnecessary. It should still be configured with care; reducing the feature set does not make arbitrary untrusted expressions automatically safe.

Keep parsing and evaluation conceptually separate. A syntax problem fails while parsing; a syntactically valid expression can still fail during evaluation because a property, method, type, variable, or conversion is unavailable for the current context.

### References

- Spring Framework Reference — Spring Expression Language: Evaluation

</details>

- [Back to top](#back-to-top)

---

## <a id="spel-in-bean-definitions">SpEL in Bean Definitions and @Value</a>

<details>
<summary>Click for details</summary>

Inside Spring bean metadata, SpEL uses `#{...}`. The application context supplies container-aware evaluation facilities, so expressions can reach standard context data and beans.

```java
@Component
final class CatalogClient {
    private final String region;
    private final int batchSize;

    CatalogClient(
            @Value("#{systemProperties['user.region'] ?: 'global'}") String region,
            @Value("${catalog.batch-size:100}") int batchSize) {
        this.region = region;
        this.batchSize = batchSize;
    }
}
```

The two values illustrate different mechanisms:

```text
${catalog.batch-size:100}
→ resolve a property placeholder

#{systemProperties['user.region'] ?: 'global'}
→ evaluate a SpEL expression
```

The bean-expression environment also exposes useful context objects such as `environment`, `systemProperties`, and `systemEnvironment`. Bean references can be expressed with `@beanName` when the container's bean resolver is participating.

`@Value` is supported on fields, methods, and constructor/method parameters. Prefer constructor parameters for required configuration because they keep the object immutable and make its configuration dependency visible.

One lifecycle caveat matters: `@Value` is processed by a `BeanPostProcessor`. Code that itself runs as a `BeanPostProcessor` or `BeanFactoryPostProcessor` should not expect ordinary `@Value` injection to bootstrap that same infrastructure reliably.

Use SpEL when the value truly requires expression evaluation. For a direct external value, `${...}` is easier to inspect, validate, and override.

</details>

- [Back to top](#back-to-top)

---

## <a id="boot-config-boundary">Boundary with Spring Boot Externalized Configuration</a>

<details>
<summary>Click for details</summary>

Spring Framework provides the underlying pieces taught in this chapter:

```text
Environment
PropertySource / MutablePropertySources
profiles and @Profile
@PropertySource
placeholder resolution infrastructure
SpEL
```

Spring Boot builds an application configuration system on top of those foundations. Boot decides how its Config Data locations are discovered and loaded, how `application.properties` or YAML participates, how Boot-specific precedence works, and how structured binding such as `@ConfigurationProperties` is performed.

That relationship can be pictured as:

```text
Boot configuration loading/binding
        ↓ populates / configures
Spring Environment + PropertySources
        ↓ consumed by
Framework container and application beans
```

When debugging a Framework-level question, ask which `PropertySource` contains the key and what its precedence is. When the question is why Boot loaded a particular file, profile-specific document, import, or structured configuration object, hand that problem to Spring Boot's externalized-configuration model.

This module therefore uses simple Framework property sources to teach the mechanism and intentionally stops before Boot Config Data or `@ConfigurationProperties` semantics.

</details>

- [Back to top](#back-to-top)
