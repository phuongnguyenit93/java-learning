<a id="back-to-top"></a>

# Type-Safe Configuration with @ConfigurationProperties

## Menu
- [Why Group Configuration into a Typed Object?](#configuration-properties-purpose)
- [Prefixes and Configuration Namespaces](#prefix-namespace)
- [Registering Configuration Properties with Scanning or Explicit Enablement](#properties-registration)
- [JavaBean and Setter Binding](#javabean-binding)
- [Constructor Binding in Spring Boot 3.x](#constructor-binding)
- [Records as Configuration Property Types](#record-binding)
- [Binding Configuration onto a Third-Party @Bean](#third-party-bean-binding)
- [Configuration Properties Lifecycle and Bean Boundary](#configuration-properties-lifecycle-boundary)

## <a id="configuration-properties-purpose">Why Group Configuration into a Typed Object?</a>

<details>
<summary>Click for details</summary>

A configuration namespace usually represents one application concept: mail delivery, storage limits, a remote client, or feature settings. Reading each key independently spreads that contract across many injection points. @ConfigurationProperties lets Boot bind the namespace once into a typed object that the rest of the application can depend on.

~~~properties
mail.host=smtp.example.com
mail.port=587
mail.retry-count=3
~~~

A MailProperties object can hold host, port, and retryCount together. The benefit is not just less annotation noise: the type becomes the documented boundary for configuration, can be validated as one unit, supports metadata generation, and makes refactoring safer. The Environment still owns value resolution; @ConfigurationProperties begins after effective values are available and turns them into an application-facing model.

</details>

- [Back to top](#back-to-top)

---

## <a id="prefix-namespace">Prefixes and Configuration Namespaces</a>

<details>
<summary>Click for details</summary>

The prefix on @ConfigurationProperties defines the namespace Boot binds. For @ConfigurationProperties("mail"), keys such as mail.host and mail.retry-count belong to that object.

Prefixes must use canonical kebab-case. A good prefix is stable and domain-oriented rather than tied to a class name or deployment environment. The prefix answers “which part of the configuration belongs to this contract?” while the fields answer “what values make up that contract?”

Avoid overly broad prefixes such as app when unrelated teams or features will accumulate settings beneath it. Smaller coherent namespaces make validation, metadata, ownership, and deprecation easier to reason about.

</details>

- [Back to top](#back-to-top)

---

## <a id="properties-registration">Registering Configuration Properties with Scanning or Explicit Enablement</a>

<details>
<summary>Click for details</summary>

Annotating a type with @ConfigurationProperties describes how it should bind, but Boot still needs that type registered as a bean. Two common mechanisms are @ConfigurationPropertiesScan and explicit @EnableConfigurationProperties.

~~~java
@SpringBootApplication
@ConfigurationPropertiesScan
class Application { }
~~~

Scanning is convenient for application-owned property classes placed under an intentional package boundary. Explicit enablement is useful when configuration classes want to declare exactly which property types participate or when a library auto-configuration registers its own property type. Neither mechanism changes property precedence; registration only determines which typed binding targets Boot creates.

</details>

- [Back to top](#back-to-top)

---

## <a id="javabean-binding">JavaBean and Setter Binding</a>

<details>
<summary>Click for details</summary>

JavaBean binding uses a no-argument construction path plus writable properties. Boot matches configuration keys to bean property names and calls setters after converting values to the declared target types.

~~~java
@ConfigurationProperties("mail")
public class MailProperties {
    private String host;
    private int port = 25;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
~~~

This style is useful when mutability is acceptable or a framework/tool expects JavaBean conventions. Defaults can live in field initializers, but remember that a default in the object is different from a lower-precedence Environment property: it is a binding-target default used when no value is bound.

</details>

- [Back to top](#back-to-top)

---

## <a id="constructor-binding">Constructor Binding in Spring Boot 3.x</a>

<details>
<summary>Click for details</summary>

Constructor binding creates the configuration object from constructor parameters rather than mutating it through setters. In Spring Boot 3.x, a @ConfigurationProperties type with a single parameterized constructor uses that constructor for binding without requiring @ConstructorBinding. If several constructors exist, @ConstructorBinding can identify the intended one; an @Autowired constructor opts out of constructor binding.

~~~java
@ConfigurationProperties("mail")
public class MailProperties {
    private final String host;
    private final int port;

    public MailProperties(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
~~~

Constructor binding fits immutable configuration contracts well. It also makes missing/nullable decisions explicit at construction time. A constructor-bound `@ConfigurationProperties` type must be registered through `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties`; it is not a regular Spring-created component and cannot rely on ordinary creation paths such as `@Component`, a `@Bean` method, or `@Import`. The separate case where `@ConfigurationProperties` is placed on a `@Bean` method binds the already-created returned instance and therefore does not use constructor binding.

Constructor binding also needs Java parameter names to be available at runtime, so the code must be compiled with `-parameters`. The Spring Boot Gradle plugin configures that option automatically, and Maven projects inherit it when they use `spring-boot-starter-parent`; a custom Maven build or a build that merely applies `spring-boot-maven-plugin` must configure the compiler accordingly.

</details>

- [Back to top](#back-to-top)

---

## <a id="record-binding">Records as Configuration Property Types</a>

<details>
<summary>Click for details</summary>

Java records are a compact fit for immutable configuration because their canonical constructor and component names already describe the binding target.

~~~java
@ConfigurationProperties("client")
public record ClientProperties(
        URI baseUrl,
        Duration timeout) {
}
~~~

Boot binds client.base-url and client.timeout to the record components using the same relaxed-name and conversion rules as other configuration properties. A record does not make configuration magically required: nullability, primitive defaults, explicit defaults, and validation still determine what happens when values are absent or invalid.

Use a record when the configuration is naturally immutable data. Use a class when you need construction logic, inheritance, mutability, or a shape that a record would make less clear.

</details>

- [Back to top](#back-to-top)

---

## <a id="third-party-bean-binding">Binding Configuration onto a Third-Party @Bean</a>

<details>
<summary>Click for details</summary>

Sometimes the useful binding target is a class from a library that you do not own. @ConfigurationProperties can be placed on a @Bean method so Boot binds external values onto the returned instance.

~~~java
@Bean
@ConfigurationProperties("client.pool")
ConnectionPoolSettings connectionPoolSettings() {
    return new ConnectionPoolSettings();
}
~~~

This approach avoids copying every third-party setting into a parallel wrapper solely for binding. It works best when the target exposes writable JavaBean-style properties. Because the application does not control the target type, metadata and validation opportunities may be more limited than with an application-owned configuration class.

</details>

- [Back to top](#back-to-top)

---

## <a id="configuration-properties-lifecycle-boundary">Configuration Properties Lifecycle and Bean Boundary</a>

<details>
<summary>Click for details</summary>

Configuration-properties beans are infrastructure configuration objects. Keep them focused on data and lightweight validation rather than business services or expensive runtime work. Boot may need binding and conversion very early, before the rest of the application is fully initialized.

Constructor-bound property types in particular must be discovered or enabled through the configuration-properties mechanisms, not instantiated as ordinary Spring components. They should not depend on ordinary application beans as collaborators. If configuration values must drive creation of a client or service, bind the values first and let a separate @Configuration/@Bean method use that property object to construct the runtime component.

~~~text
Environment values
      ↓
@ConfigurationProperties binding
      ↓
validated configuration object
      ↓
@Bean / application component consumes it
~~~

This separation keeps configuration resolution deterministic and prevents the property object from becoming a hidden service locator.

</details>

- [Back to top](#back-to-top)
