<a id="back-to-top"></a>

# Programmatic server customization

## Menu
- [Properties First, Customizer Second, Factory Bean Last](#server-customization-decision-order)
- [WebServerFactoryCustomizer as the Main Programmatic Hook](#web-server-factory-customizer)
- [Generic versus Server-specific Factory Targets](#generic-vs-specific-factory-targets)
- [How Boot and User Customizers Are Ordered](#customizer-ordering)
- [Supplying a Custom WebServerFactory Bean](#custom-web-server-factory)
- [Extra Connectors, Listeners, and Other Advanced Server Topology](#advanced-server-topology)

## <a id="server-customization-decision-order">Properties First, Customizer Second, Factory Bean Last</a>

<details>
<summary>Click for details</summary>
Use the least invasive customization mechanism that can express the requirement. Start with a common `server.*` property. If the setting is implementation-specific, check `server.tomcat.*`, `server.jetty.*`, `server.undertow.*`, or `server.netty.*`. Move to code only when the property model cannot represent the required server change.

The usual escalation path is:

```text
common property
    ↓
server-specific property
    ↓
WebServerFactoryCustomizer
    ↓
custom WebServerFactory bean
```

Each step increases coupling to server APIs and makes upgrades or server replacement more expensive, so the escalation should follow the actual requirement rather than preference.

</details>

- [Back to top](#back-to-top)

---

## <a id="web-server-factory-customizer">WebServerFactoryCustomizer as the Main Programmatic Hook</a>

<details>
<summary>Click for details</summary>
`WebServerFactoryCustomizer<T>` lets application code modify Boot's selected factory before it creates the server. The generic type narrows the customizer to the factory types it supports, so a Tomcat-specific customizer does not accidentally run against Jetty.

```java
@Bean
WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
    return factory -> factory.setBackgroundProcessorDelay(10);
}
```

The exact server API used inside the callback is implementation-specific. The Boot-owned lesson is the lifecycle hook: customize the factory while keeping Boot responsible for creating and starting the web server.

### References

- [Spring Boot 3.3 API — WebServerFactoryCustomizer](https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/web/server/WebServerFactoryCustomizer.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="generic-vs-specific-factory-targets">Generic versus Server-specific Factory Targets</a>

<details>
<summary>Click for details</summary>
Target the broadest factory type that still exposes the capability you need. A customizer for `ConfigurableWebServerFactory` can express portable concerns available on that abstraction. A customizer for `TomcatServletWebServerFactory` or `NettyReactiveWebServerFactory` intentionally opts into a specific server implementation and web stack.

This distinction is useful during maintenance. A generic customizer can often survive a server swap; a server-specific customizer becomes part of the migration checklist because its API and behavior are tied to that implementation.

</details>

- [Back to top](#back-to-top)

---

## <a id="customizer-ordering">How Boot and User Customizers Are Ordered</a>

<details>
<summary>Click for details</summary>
Multiple customizers can target the same factory. Spring ordering rules determine the sequence, and Boot's auto-configured `WebServerFactoryCustomizer` beans use order `0`. A user customizer can implement `Ordered` or use `@Order` when it must run before or after another customization.

Avoid relying on accidental bean discovery order. When two customizers touch the same field, make the ordering intent explicit or consolidate the responsibility so the final factory state is predictable.

</details>

- [Back to top](#back-to-top)

---

## <a id="custom-web-server-factory">Supplying a Custom WebServerFactory Bean</a>

<details>
<summary>Click for details</summary>
Declaring your own `ServletWebServerFactory` or `ReactiveWebServerFactory` bean causes Boot's implementation-specific factory auto-configuration to back off. The application now chooses how that factory is constructed.

This does **not** bypass the entire Boot customization pipeline. Auto-configured `WebServerFactoryCustomizer` beans are still applied to the custom factory. Treat a custom factory as the last resort when factory construction itself must change, and verify how Boot customizers interact with the initial state you supplied.

</details>

- [Back to top](#back-to-top)

---

## <a id="advanced-server-topology">Extra Connectors, Listeners, and Other Advanced Server Topology</a>

<details>
<summary>Click for details</summary>
Some topologies require server-specific code. A common example is exposing HTTPS through `server.ssl.*` while adding a second plain HTTP connector programmatically. Spring Boot does not model simultaneous HTTP and HTTPS connectors as a pair of ordinary `application.properties` settings, so the extra connector belongs in a server-specific customizer.

The same principle applies to custom listeners, connector resources, protocol handlers, or other server objects that are outside Boot's portable property model. Keep such code isolated around the factory instead of spreading container APIs through application business code.

### References

- [Spring Boot 3.3 How-to — Configure the Web Server](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.configure)

</details>

- [Back to top](#back-to-top)
