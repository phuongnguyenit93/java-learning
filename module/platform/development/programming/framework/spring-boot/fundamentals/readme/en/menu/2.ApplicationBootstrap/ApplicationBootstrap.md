<a id="back-to-top"></a>

# Application Bootstrap with SpringApplication

## Menu
- [What Is SpringApplication and Why Is It the Boot Entry Point?](#springapplication-role)
- [How SpringApplication.run Turns main into an ApplicationContext](#main-to-context)
- [What Happens During Bootstrap at a High Level?](#bootstrap-flow)
- [How Does the Application Move Through Start, Run, and Orderly Stop?](#high-level-lifecycle)
- [Where Does the Fundamentals Lifecycle Model Hand Off?](#runtime-handoff)

## <a id="springapplication-role">What Is SpringApplication and Why Is It the Boot Entry Point?</a>

<details>
<summary>Click for details</summary>

`SpringApplication` is Boot's application-bootstrap abstraction. A normal Java program begins in `main`; a Boot application usually keeps that Java entry point but delegates the work of creating and starting the Spring application to `SpringApplication`.

The common form is deliberately small:

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

The `main` method is still an ordinary JVM entry point. `SpringApplication.run(...)` is the Boot-specific handoff: it receives a primary source plus command-line arguments and returns a running `ApplicationContext`. Business logic should not be hidden in this bootstrap call; its job is to establish the application environment and container.

This makes `SpringApplication` a bridge between Java process startup and the Spring container. Later modules add detail to the lifecycle, but this chapter keeps the mental model at that boundary.

### References

- [Spring Boot 3.3 — SpringApplication](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="main-to-context">How SpringApplication.run Turns main into an ApplicationContext</a>

<details>
<summary>Click for details</summary>

`SpringApplication.run(DemoApplication.class, args)` does more than instantiate `DemoApplication`. The class argument is a primary source from which Boot and Spring can discover configuration, while `args` are made available as application arguments and can participate in environment configuration.

At a high level, the call creates an appropriate `ApplicationContext`, loads configuration into it, refreshes the context so singleton beans can be created, and returns the running context. The exact context type depends on the application shape and classpath.

The important relationship is:

```text
JVM calls main(String[])
        ↓
main calls SpringApplication.run(primarySource, args)
        ↓
Boot prepares and refreshes a Spring ApplicationContext
        ↓
application is ready to perform its runtime role
```

Because `run` returns the context, a small program can even keep a reference when it needs controlled access:

```java
ConfigurableApplicationContext context =
        SpringApplication.run(DemoApplication.class, args);
```

Most applications do not need to manually manage that reference; the code simply demonstrates that the result is a real Spring context, not a hidden Boot-only runtime.

</details>

- [Back to top](#back-to-top)

---

## <a id="bootstrap-flow">What Happens During Bootstrap at a High Level?</a>

<details>
<summary>Click for details</summary>

Bootstrap is easiest to reason about as a sequence of responsibilities rather than a list of every internal event. Spring Boot prepares the application environment, decides what broad kind of application it is starting, creates the corresponding context, loads the primary sources, refreshes the context, and completes startup.

```text
primary source + args
        ↓
prepare environment and application settings
        ↓
choose/create an ApplicationContext
        ↓
load configuration sources
        ↓
refresh the context and create beans
        ↓
running application
```

Two forces already introduced in this module affect the result. The **classpath** tells Boot which libraries and capabilities are available; **configuration inputs** supply values and explicit choices. Auto-configuration may react to both, but the detailed condition model belongs to `auto-configuration`.

This level of detail is enough to debug the first question during startup: did the JVM reach `main`, did Boot begin bootstrap, and did the Spring context refresh successfully? Deeper event ordering belongs later.

</details>

- [Back to top](#back-to-top)

---

## <a id="high-level-lifecycle">How Does the Application Move Through Start, Run, and Orderly Stop?</a>

<details>
<summary>Click for details</summary>

A Boot application has a high-level lifecycle even before you learn every lifecycle event. The JVM starts the process, `main` delegates to `SpringApplication`, the context is prepared and refreshed, and the application then keeps running according to its shape and active non-daemon work.

For a non-web command-style application, work may complete and the process may naturally become eligible to exit. For a web application, the embedded server and its runtime threads normally keep the process alive while it serves requests. When the JVM is shutting down normally, Boot's registered shutdown hook closes the application context so Spring-managed destruction callbacks can run.

```text
process start
   ↓
context start
   ↓
application running
   ↓
orderly JVM shutdown
   ↓
context close
```

This is intentionally a lifecycle **mental model**, not the full event contract. It gives you enough vocabulary to understand why a Boot application is more than a one-shot `main` method while keeping detailed runtime hooks in their proper module.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-handoff">Where Does the Fundamentals Lifecycle Model Hand Off?</a>

<details>
<summary>Click for details</summary>

Detailed runtime events, runners, availability states, task execution, virtual threads, logging integration, and runtime services belong to the `application-runtime` module.

That handoff includes questions such as "which Boot event is published at this moment?", "when do `ApplicationRunner` and `CommandLineRunner` execute?", "how are liveness/readiness states modeled?", and "how does Boot integrate runtime services such as task execution or logging?" Those questions build on the simple start/run/stop model established here.

Fundamentals should still let you locate a failure at a coarse level: before `SpringApplication`, during context bootstrap/refresh, or after the application is already running. The next runtime module adds the finer-grained lifecycle vocabulary.

</details>

- [Back to top](#back-to-top)
