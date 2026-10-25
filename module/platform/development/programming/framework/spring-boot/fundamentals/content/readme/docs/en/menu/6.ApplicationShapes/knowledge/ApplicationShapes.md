<a id="back-to-top"></a>

# Application Shapes and the Embedded Server Mental Model

## Menu
- [What Application Shapes Can Spring Boot Start?](#application-shapes)
- [How Do Non-Web, Servlet, and Reactive Shapes Differ at a High Level?](#non-web-servlet-reactive)
- [What Does an Embedded Server Mean in a Boot Application?](#embedded-server-mental-model)
- [How Can a Web Starter Change the Runtime Shape?](#classpath-changes-runtime-shape)
- [Where Does the Embedded-Server Overview Hand Off?](#web-runtime-handoff)

## <a id="application-shapes">What Application Shapes Can Spring Boot Start?</a>

<details>
<summary>Click for details</summary>

Spring Boot can start more than one kind of application. The same `SpringApplication` bootstrap abstraction can create a plain non-web context, a Servlet-based web application, or a reactive web application depending on the available classpath and explicit application settings.

This is an important correction to the beginner shortcut "Spring Boot means a web server." Boot is an application bootstrap and integration layer; a web server appears only when the application has a web runtime shape.

At the Fundamentals level, think of the shape as a high-level runtime choice:

```text
non-web     → ApplicationContext without a web server
Servlet web → Servlet web ApplicationContext + embedded Servlet server integration
reactive web→ reactive web ApplicationContext + reactive server integration
```

The `web-runtime` module owns the detailed detection and server configuration rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="non-web-servlet-reactive">How Do Non-Web, Servlet, and Reactive Shapes Differ at a High Level?</a>

<details>
<summary>Click for details</summary>

A **non-web** Boot application still gets Boot bootstrap, configuration, dependency injection, and other Spring/Boot integrations, but it has no need to listen for HTTP requests. Batch-style processes, command applications, and background workers can fit this shape.

A **Servlet** application uses Spring's Servlet web stack, commonly Spring MVC, and runs with a Servlet-capable web application context. A **reactive** application uses the reactive web stack, commonly Spring WebFlux, and a reactive web application context.

When Boot infers the type from the classpath, Spring MVC takes precedence if both MVC and WebFlux are present. An application can explicitly choose another `WebApplicationType` when that is intentional.

These labels describe Boot's application context/runtime shape. They do not replace the Spring Framework curricula for MVC request handling, WebFlux programming, controllers, codecs, or reactive semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="embedded-server-mental-model">What Does an Embedded Server Mean in a Boot Application?</a>

<details>
<summary>Click for details</summary>

An embedded server means the HTTP server is launched and managed as part of the application process instead of requiring the application to be copied into a separately operated external server as its only deployment model.

For a typical executable Servlet application, the relationship looks like this:

```text
java process
└── Boot application
    ├── Spring ApplicationContext
    └── embedded Servlet web server
```

This model makes a web application runnable with the same application-oriented startup command used for other JVM programs, such as `java -jar ...`. Boot coordinates the server integration with the context lifecycle.

Embedded does not mean "implemented by Boot from scratch." Boot integrates supported server implementations. Server choice, ports, connectors, TLS, proxy behavior, and graceful shutdown belong to the deeper `web-runtime` module.

### References

- [Spring Boot 3.3 — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="classpath-changes-runtime-shape">How Can a Web Starter Change the Runtime Shape?</a>

<details>
<summary>Click for details</summary>

Adding a web starter changes the classpath, and the classpath is one of the signals Boot uses when choosing an application shape. A project that previously contained no web stack can become eligible for a web application context and embedded-server auto-configuration after a web starter is added.

This is a concrete example of a relationship established earlier:

```text
add starter
   ↓
new libraries/classes become available
   ↓
Boot detects a different application capability
   ↓
runtime shape and automatic configuration may change
```

The reverse is also useful during debugging. If a supposedly non-web application unexpectedly starts a server, inspect the dependency tree and classpath for a web stack before assuming a server property is the root cause.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-runtime-handoff">Where Does the Embedded-Server Overview Hand Off?</a>

<details>
<summary>Click for details</summary>

Detailed Spring Boot web-application detection, embedded-server auto-configuration, server customization, TLS, proxy handling, and graceful shutdown belong to the `web-runtime` module.

That module answers questions such as which server implementation is selected, how `server.*` properties influence it, how SSL/TLS is connected, how forwarded headers are handled behind a proxy, and how the server participates in graceful shutdown.

Fundamentals keeps only the transferable mental model: a Boot application may be non-web or web; a web runtime can live inside the same process; and dependency/classpath choices can influence which shape is available.

</details>

- [Back to top](#back-to-top)
