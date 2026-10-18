<a id="back-to-top"></a>

# Testcontainers Service Connections and `ConnectionDetails`

## Menu
- [Why Does Boot Provide Service Connections for Testcontainers?](#service-connection-purpose)
- [What Does the `spring-boot-testcontainers` Test Dependency Enable?](#spring-boot-testcontainers-dependency)
- [Why Do Service `ConnectionDetails` Override Connection Properties?](#connection-details-precedence)
- [How Does `@ServiceConnection` Work on a Testcontainers-Managed Container Field?](#service-connection-container-field)
- [How Do Container Beans in `@TestConfiguration` Participate in Service Connections?](#service-connection-container-bean)
- [When Does a `GenericContainer` Need an Explicit Service-Connection Name?](#service-connection-name-hints)
- [When Is `@DynamicPropertySource` the Better Fallback?](#dynamic-property-source-fallback)
- [Where Does Boot Integration End and Generic Testcontainers Ownership Begin?](#testcontainers-ownership-boundary)

## <a id="service-connection-purpose">Why Does Boot Provide Service Connections for Testcontainers?</a>

<details>
<summary>Click for details</summary>
Testcontainers can start a real service, but the application still needs connection details such as host, port, credentials, or URLs. Boot service connections bridge that gap by deriving typed `ConnectionDetails` from supported containers and making those details available to the application's auto-configuration.

The result is a cleaner integration test: a container field managed through Testcontainers' JUnit integration keeps its Testcontainers lifecycle, while a container declared as a Spring `@Bean` follows the Spring application-context lifecycle. In either case, Boot configures its normal client infrastructure from the service connection instead of requiring every test to copy dynamic container values into properties.

### References

- [Spring Boot 3.3 — Testcontainers](https://docs.spring.io/spring-boot/3.3/reference/testing/testcontainers.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-boot-testcontainers-dependency">What Does the `spring-boot-testcontainers` Test Dependency Enable?</a>

<details>
<summary>Click for details</summary>
Boot's Testcontainers integration lives in the `spring-boot-testcontainers` module. Adding it as a test dependency provides `@ServiceConnection` and the connection-details factories that recognize supported Testcontainers types or container image names.

The dependency does not replace the Testcontainers library or its JUnit integration. It adds the Boot-specific adapter that turns container information into connection details consumable by Boot auto-configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="connection-details-precedence">Why Do Service `ConnectionDetails` Override Connection Properties?</a>

<details>
<summary>Click for details</summary>
When Boot auto-configuration receives a matching `ConnectionDetails` bean, those connection details take precedence over ordinary connection-related configuration properties. This allows a test container to become the authoritative endpoint for the test without rewriting the application's normal property set.

This priority is intentional: the service connection represents a concrete running dependency whose location is often dynamic. Other unrelated application properties continue to use the normal externalized configuration model.

</details>

- [Back to top](#back-to-top)

---

## <a id="service-connection-container-field">How Does `@ServiceConnection` Work on a Testcontainers-Managed Container Field?</a>

<details>
<summary>Click for details</summary>
A common pattern is a Testcontainers-managed field inside a test class that enables the Testcontainers JUnit extension with `@Testcontainers`. The field uses Testcontainers' `@Container` together with Boot's `@ServiceConnection`; Boot inspects the container type or image and creates the corresponding `ConnectionDetails` bean.

```java
@Testcontainers
@SpringBootTest
class PostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");
}
```

Container startup and shutdown remain Testcontainers responsibilities. Boot consumes the running container's connection information to configure application infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="service-connection-container-bean">How Do Container Beans in `@TestConfiguration` Participate in Service Connections?</a>

<details>
<summary>Click for details</summary>
Containers can also be declared as `@Bean` methods in `@TestConfiguration` and annotated with `@ServiceConnection`. This makes container configuration reusable and allows Boot to manage the bean within the test application context.

For bean methods, Boot uses the declared return type to decide which connection-details factory applies without eagerly calling the bean method just to inspect the Docker image. Strongly typed container return types therefore provide more information than a generic container type.

</details>

- [Back to top](#back-to-top)

---

## <a id="service-connection-name-hints">When Does a `GenericContainer` Need an Explicit Service-Connection Name?</a>

<details>
<summary>Click for details</summary>
A `GenericContainer` return type does not identify the service by Java type. For a container bean, Boot may therefore need an explicit `@ServiceConnection(name = "...")` hint so it can select the right connection-details factory without creating the container just to discover its image.

Use the name recognized by Boot's service-connection factory for the intended technology. This is a Boot integration hint, not a Testcontainers container name or lifecycle concept.

</details>

- [Back to top](#back-to-top)

---

## <a id="dynamic-property-source-fallback">When Is `@DynamicPropertySource` the Better Fallback?</a>

<details>
<summary>Click for details</summary>
Use `@DynamicPropertySource` when no Boot service-connection factory exists, when the application consumes a custom property contract, or when the test needs to publish values that are not represented by a supported `ConnectionDetails` type.

This approach is more manual: the test reads values from the container and registers dynamic properties itself. The generic mechanism belongs to Spring TestContext; Boot service connections are preferable when a supported typed integration already expresses the same intent.

</details>

- [Back to top](#back-to-top)

---

## <a id="testcontainers-ownership-boundary">Where Does Boot Integration End and Generic Testcontainers Ownership Begin?</a>

<details>
<summary>Click for details</summary>
Boot testing owns `@ServiceConnection`, its `ConnectionDetails` adapters, and how those details feed Boot auto-configuration. For containers managed through Testcontainers' JUnit annotations or extension, Testcontainers owns startup and shutdown. A container declared as a Spring `@Bean` instead follows the Spring application-context lifecycle: Spring creates and starts it with the context and stops it when the context closes. Generic Docker images, networks, wait strategies, reusable-container behavior, and Docker connectivity remain Testcontainers concerns.

When a Testcontainers-managed field does not start or a network/wait strategy behaves incorrectly, debug Testcontainers. For a Spring-managed container bean, also check bean creation and context lifecycle. When the container is running but Boot fails to configure the application client from it, debug the service-connection integration.

</details>

- [Back to top](#back-to-top)
