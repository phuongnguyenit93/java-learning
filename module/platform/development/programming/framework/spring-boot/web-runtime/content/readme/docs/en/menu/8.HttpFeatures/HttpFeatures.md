<a id="back-to-top"></a>

# HTTP features, compression, and connection settings

## Menu
- [HTTP Response Compression](#http-response-compression)
- [HTTP/2 Enablement and Server Support](#http2-support)
- [Request Header Size Limits](#request-header-limits)
- [Connection and Server-level HTTP Settings](#connection-server-settings)
- [Embedded Server Access Logging](#access-logging)
- [Portable HTTP Controls versus Server-specific Capabilities](#http-feature-portability)

## <a id="http-response-compression">HTTP Response Compression</a>

<details>
<summary>Click for details</summary>
Boot exposes response compression through `server.compression.*`. It is disabled by default; `server.compression.enabled=true` enables the feature on supported embedded servers. Boot 3.3 supports response compression with Jetty, Tomcat, Reactor Netty, and Undertow.

Compression also has eligibility rules. The default minimum response size is `2KB`, and only configured MIME types are compressed. Use `server.compression.min-response-size` and `server.compression.mime-types` to tune those decisions rather than assuming every response will be compressed.

### References

- [Spring Boot 3.3 How-to — Enable HTTP Response Compression](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.enable-response-compression)

</details>

- [Back to top](#back-to-top)

---

## <a id="http2-support">HTTP/2 Enablement and Server Support</a>

<details>
<summary>Click for details</summary>
`server.http2.enabled=true` is Boot's common switch for HTTP/2. With SSL enabled the server uses HTTP/2 over TLS (`h2`); without SSL, supported servers use clear-text HTTP/2 (`h2c`). This is a server capability decision, not a Spring MVC or WebFlux request-mapping feature.

Support details still vary by server. Tomcat 10.1 used by Boot 3.3 supports `h2` and `h2c` out of the box, while Jetty requires its additional HTTP/2 server dependency. Check the selected server's requirements when a common Boot switch does not produce the expected protocol behavior.

### References

- [Spring Boot 3.3 How-to — Configure HTTP/2](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.configure-http2)

</details>

- [Back to top](#back-to-top)

---

## <a id="request-header-limits">Request Header Size Limits</a>

<details>
<summary>Click for details</summary>
`server.max-http-request-header-size` expresses a common upper bound for HTTP request headers. It is useful for protecting the server from unexpectedly large request metadata and for aligning application limits with upstream proxies.

Do not assume one common property covers every header-related limit in every server. Response-header limits and lower-level parser or connector controls can require server-specific properties or APIs. Start from Boot's portable request limit and move deeper only when the deployment needs it.

</details>

- [Back to top](#back-to-top)

---

## <a id="connection-server-settings">Connection and Server-level HTTP Settings</a>

<details>
<summary>Click for details</summary>
Connection queues, idle behavior, worker/thread resources, low-level protocol options, and similar controls are often server-specific because the implementations expose different runtime models. Boot surfaces many of them under the corresponding server namespace and leaves uncommon cases to factory customizers.

Tune these settings only after identifying the actual server and operational symptom. A property copied from Tomcat has no portable meaning for Reactor Netty, and a Netty event-loop setting should not be presented as a generic Spring Boot web rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="access-logging">Embedded Server Access Logging</a>

<details>
<summary>Click for details</summary>
An embedded server access log records server-level request observations such as remote address, request line, status, timing, and bytes. It is separate from application logging performed inside controllers or filters.

Access logging is configured per server. Boot's 3.3 how-to documents Tomcat under `server.tomcat.accesslog.*`, Undertow under `server.undertow.accesslog.*`, and Jetty under `server.jetty.accesslog.*`. Format tokens, file locations, rotation, and available fields are implementation concerns, so consult the selected server once Boot has enabled the feature.

</details>

- [Back to top](#back-to-top)

---

## <a id="http-feature-portability">Portable HTTP Controls versus Server-specific Capabilities</a>

<details>
<summary>Click for details</summary>
A common Boot property means "one configuration intent across supported integrations". It does not promise identical internals, identical defaults below the Boot layer, or identical edge-case behavior. Compression, HTTP/2, request-header limits, and graceful shutdown all demonstrate this pattern.

Use portable controls to express application intent and server-specific settings for implementation-dependent requirements. If a production decision depends on protocol theory, proxy behavior, kernel/network tuning, or container internals, hand that depth to the infrastructure owner rather than expanding the Boot integration layer indefinitely.

</details>

- [Back to top](#back-to-top)
