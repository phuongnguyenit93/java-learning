<a id="back-to-top"></a>

# Test Environment and Dynamic Properties

## Menu
- [Activating profiles for tests](#active-profiles)
- [Profile resolution and inheritance](#profile-resolution-and-inheritance)
- [Test property sources](#test-property-source)
- [Test property precedence](#test-property-precedence)
- [DynamicPropertySource and runtime-backed values](#dynamic-property-source)
- [Environment inheritance and context cache identity](#environment-inheritance-and-cache-identity)

## <a id="active-profiles">Activating profiles for tests</a>

<details>
<summary>Click for details</summary>

`@ActiveProfiles` selects which bean-definition profiles are active while the test `ApplicationContext` is being built.

```java
@ActiveProfiles("integration")
@ContextConfiguration(classes = TestConfig.class)
class PricingProfileTests {
}
```

Profiles affect which beans enter the context, so they change the configuration being tested rather than simply supplying input values.

Use profiles when production configuration genuinely varies by profile. Do not create a special profile merely to hide awkward test setup if a smaller fixture or explicit test configuration would communicate intent better.

</details>

- [Back to top](#back-to-top)

---

## <a id="profile-resolution-and-inheritance">Profile resolution and inheritance</a>

<details>
<summary>Click for details</summary>

Active profiles are inherited from superclasses and enclosing test classes by default. Subclasses can add profiles on top of inherited ones or disable inheritance when they need an independent configuration.

For programmatic selection, `@ActiveProfiles(resolver = ...)` delegates to an `ActiveProfilesResolver`. This is useful when profile choice depends on deterministic test metadata rather than a fixed literal.

Because profiles change which beans are registered, the resolved active-profile set participates in context cache identity. Two tests that differ only by active profiles should not share the same cached context.

Keep profile resolution deterministic. Tying it to volatile machine state can fragment the cache and make tests behave differently across environments.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-property-source">Test property sources</a>

<details>
<summary>Click for details</summary>

`@TestPropertySource` adds test-specific property sources to the `Environment` of the test `ApplicationContext`. It supports resource locations and inline key/value properties.

```java
@TestPropertySource(
        properties = "pricing.discount=0.10"
)
class PricingPropertyTests {
}
```

If `@TestPropertySource` is declared without explicit locations or properties, Spring performs default file detection based on the test class. If the expected default properties file does not exist, configuration fails instead of silently ignoring it.

Spring Framework 6.1 also supports richer resource descriptors, including resource patterns, explicit encoding, and a custom `PropertySourceFactory`.

</details>

- [Back to top](#back-to-top)

---

## <a id="test-property-precedence">Test property precedence</a>

<details>
<summary>Click for details</summary>

Property precedence matters because the same key can appear in several sources.

Within TestContext, the important ordering is:

```text
@DynamicPropertySource
        ↓ higher precedence
inline @TestPropertySource properties
        ↓
@TestPropertySource resource locations
        ↓
normal application / system / environment property sources
```

Inline test properties therefore override values loaded from test property resource files. Dynamic properties override both.

Use the strongest source only when the test intentionally needs to replace a lower-precedence value. Scattering the same key across many layers makes the fixture difficult to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-property-source">DynamicPropertySource and runtime-backed values</a>

<details>
<summary>Click for details</summary>

`@DynamicPropertySource` is designed for values that become known at runtime, for example a port allocated by a test-managed external service.

The annotated method must be `static` and accept exactly one `DynamicPropertyRegistry` argument:

```java
@DynamicPropertySource
static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("service.port", testServer::getPort);
}
```

Values are registered as suppliers and resolved lazily when the `Environment` asks for them. Dynamic properties have higher precedence than `@TestPropertySource`, operating-system environment values, JVM system properties, and application property sources.

Spring inherits dynamic properties contributed by `@DynamicPropertySource` methods declared on superclasses, interfaces, and enclosing test classes. If subclasses reuse the same inherited registration but the supplier resolves to different values that require a different context, mark the appropriate test context dirty so a stale cached context is not reused.

</details>

- [Back to top](#back-to-top)

---

## <a id="environment-inheritance-and-cache-identity">Environment inheritance and context cache identity</a>

<details>
<summary>Click for details</summary>

Profiles and test property declarations are part of the configuration identity that drives context caching. In Spring 6.1, cache-key inputs include active profiles, test property descriptors/properties, and context customizers.

`@TestPropertySource` declarations are inherited by default. Resource locations and inline properties have separate controls — `inheritLocations` and `inheritProperties` — and both default to `true`. Setting one to `false` shadows that inherited dimension instead of extending it. When inherited and local sources define the same key, the more local test declaration wins according to normal property-source precedence.

`@DynamicPropertySource` contributes through a context customizer. The **registration method** therefore participates in context identity, but the value later returned by a supplier is not automatically a separate cache-key dimension.

That distinction explains a subtle failure mode:

1. a base test declares one dynamic-property method;
2. subclasses inherit the same registration;
3. the supplier returns a different external endpoint for each subclass;
4. the cache may still consider their Spring configuration equivalent unless another key dimension differs.

When the dynamic value changes the validity of an already-created context, use `@DirtiesContext` or structure the configuration so the cache identity differs explicitly.

### References

- [Spring Framework 6.1.14 API — @TestPropertySource](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/TestPropertySource.html)
- [Spring Framework 6.1.14 API — @DynamicPropertySource](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/DynamicPropertySource.html)

</details>

- [Back to top](#back-to-top)
