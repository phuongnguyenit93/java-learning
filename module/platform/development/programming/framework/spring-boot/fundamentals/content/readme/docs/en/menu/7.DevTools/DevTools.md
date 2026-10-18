<a id="back-to-top"></a>

# Spring Boot DevTools

## Menu
- [Why Does Spring Boot DevTools Exist?](#devtools-purpose)
- [How Does Automatic Restart Use Two ClassLoaders?](#restart-classloader-model)
- [How Do LiveReload and Development-Time Property Defaults Help?](#livereload-property-defaults)
- [How Do You Add DevTools and Control Restart Scope?](#devtools-setup-restart-scope)
- [What Is Remote DevTools and Why Is It Security-Sensitive?](#remote-devtools-security)
- [How Does DevTools Differ from Production Operations Tooling?](#devtools-production-boundary)

## <a id="devtools-purpose">Why Does Spring Boot DevTools Exist?</a>

<details>
<summary>Click for details</summary>

`spring-boot-devtools` is an optional development-time module that shortens the edit → rebuild → observe loop. It does not add business capabilities to the application. Instead, it makes local development more responsive by supporting automatic restart, LiveReload, and development-friendly property defaults.

The key boundary is the environment in which the tool is useful. DevTools is meant for development. Fully packaged applications disable developer tools automatically in the normal case, and Boot's build integrations keep DevTools out of repackaged production artifacts by default.

That means DevTools should be understood as **developer feedback-loop support**, not as an application lifecycle requirement. Removing it should not remove the application's business behavior.

### References

- [Spring Boot 3.3 — Developer Tools](https://docs.spring.io/spring-boot/3.3/reference/using/devtools.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="restart-classloader-model">How Does Automatic Restart Use Two ClassLoaders?</a>

<details>
<summary>Click for details</summary>

Automatic restart is faster than a cold JVM restart because DevTools uses two classloaders. Classes that rarely change, typically regular third-party jars, are loaded by a **base classloader**. Classes that you are actively developing are loaded by a **restart classloader**.

```text
base classloader
└── stable dependency jars

restart classloader
└── application classes being developed
```

When DevTools detects a relevant classpath update, it discards and recreates the restart classloader while keeping the populated base classloader. The application context is restarted against that new restart loader, so the feedback loop is usually faster than restarting the entire process from scratch.

This also explains a real failure mode: objects or libraries that make assumptions about classloader identity can behave differently under DevTools. Multi-module projects are especially worth checking when a shared module ends up in an unexpected loader. If disabling restart removes the problem, classloader placement is a useful next diagnostic step.

</details>

- [Back to top](#back-to-top)

---

## <a id="livereload-property-defaults">How Do LiveReload and Development-Time Property Defaults Help?</a>

<details>
<summary>Click for details</summary>

LiveReload solves a different problem from restart. DevTools can start an embedded LiveReload server that signals a compatible browser extension when resources change, allowing the browser to refresh automatically. Static resources and templates are commonly handled without requiring a full application restart.

DevTools also applies development-time property defaults. Caching is valuable in production but can hide edits during development, so DevTools disables or adjusts caches for supported template/resource integrations. For example, the default set includes `spring.thymeleaf.cache=false` when DevTools properties are active.

These features are related through the same goal: shorten feedback time. Restart refreshes application classes/context when classpath content changes; LiveReload refreshes the browser for relevant resource changes; property defaults make those changes easier to observe.

The defaults are still controllable. Setting `spring.devtools.add-properties=false` disables DevTools' additional property defaults, and `spring.devtools.livereload.enabled=false` disables its LiveReload server.

</details>

- [Back to top](#back-to-top)

---

## <a id="devtools-setup-restart-scope">How Do You Add DevTools and Control Restart Scope?</a>

<details>
<summary>Click for details</summary>

With Gradle, keep DevTools in the development-only configuration:

```groovy
dependencies {
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
}
```

DevTools watches classpath directories, so saving a source file is not universally enough. The changed source must be compiled or copied so the runtime classpath is updated. In IntelliJ IDEA, building the project triggers that update; IDE auto-build support can automate the step when configured appropriately.

For multi-module or unusual layouts, DevTools can watch additional paths:

```properties
spring.devtools.restart.additional-paths=../infrastructure/src/main/java
```

You can also control which resources trigger restart with `spring.devtools.restart.exclude` / `additional-exclude`, and classloader placement can be refined with `META-INF/spring-devtools.properties` using `restart.include.*` and `restart.exclude.*` entries.

The evidence to check is simple: make a classpath-visible change and verify that the application reports a restart. If the change never reaches the runtime classpath, changing DevTools properties cannot compensate for a build/IDE step that never produced updated classes.

</details>

- [Back to top](#back-to-top)

---

## <a id="remote-devtools-security">What Is Remote DevTools and Why Is It Security-Sensitive?</a>

<details>
<summary>Click for details</summary>

Remote DevTools can support a development workflow in which a local client communicates with DevTools support included in a remotely running application. It is an opt-in feature and requires the remote side to include DevTools plus a configured `spring.devtools.remote.secret`.

The security boundary is more important than the convenience. Spring Boot explicitly warns that remote support can be a security risk, should be used only on a trusted network or protected with SSL, and should **never be enabled on a production deployment**. It is also not a general deployment protocol: it exists to support a development feedback loop.

Remote support is therefore an exception to the usual "DevTools is absent from packaged artifacts" model. Enabling it requires intentionally including DevTools in the repackaged application. That extra step should make the risk visible rather than normalize DevTools as a production dependency.

In Spring Boot 3.3, remote DevTools support is not available for Spring WebFlux applications.

</details>

- [Back to top](#back-to-top)

---

## <a id="devtools-production-boundary">How Does DevTools Differ from Production Operations Tooling?</a>

<details>
<summary>Click for details</summary>

DevTools and Actuator both expose behavior that developers can observe, but they serve different stages of the software lifecycle.

| DevTools | Actuator |
| --- | --- |
| development-time feedback | production-ready operational surface |
| restart, LiveReload, development property defaults | health, metrics integration, loggers, diagnostic/management endpoints |
| normally excluded/disabled for packaged production use | intentionally designed for running applications, with exposure/security decisions |

The distinction protects the mental model: a faster local restart is not health monitoring, and a LiveReload server is not a production management endpoint. If the question is "how can I see the result of my edit sooner?", DevTools may help. If the question is "what is the state of this running service?", the curriculum moves to `actuator`.

</details>

- [Back to top](#back-to-top)
