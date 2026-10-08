<a id="back-to-top"></a>

# TLS, SSL bundles, and web-server certificates

## Menu
- [Boot Entry Points for Web-server TLS](#web-server-tls-entry-points)
- [Keystore and PEM Configuration with server.ssl.*](#server-ssl-keystore-pem)
- [Applying a Named SSL Bundle to the Web Server](#ssl-bundle-consumption)
- [SSL Bundle Options versus Discrete server.ssl Properties](#bundle-vs-discrete-ssl-properties)
- [Server Name Indication and Certificate Selection](#server-name-indication)
- [Web-server TLS Integration versus Generic TLS and PKI](#tls-ownership-boundary)

## <a id="web-server-tls-entry-points">Boot Entry Points for Web-server TLS</a>

<details>
<summary>Click for details</summary>
Boot can secure the embedded server in two main declarative ways. The first configures certificate/key material directly under `server.ssl.*`. The second defines reusable named material under `spring.ssl.bundle.*` and points the server at one bundle with `server.ssl.bundle`.

Both paths configure the Boot-managed server; they do not change application routes. The choice is mainly about configuration ownership and reuse. A named bundle is useful when the same TLS material or options should be consumed consistently by multiple Boot-supported connections.

### References

- [Spring Boot 3.3 How-to — Configure SSL](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.configure-ssl)

</details>

- [Back to top](#back-to-top)

---

## <a id="server-ssl-keystore-pem">Keystore and PEM Configuration with server.ssl.*</a>

<details>
<summary>Click for details</summary>
For direct server configuration, `server.ssl.*` supports Java KeyStore material and PEM-encoded certificate/key material. A typical keystore setup provides a keystore location and passwords; a PEM setup provides the certificate and private-key locations.

Enabling server SSL changes the main connector to HTTPS. Boot does not provide a pair of ordinary properties that simultaneously configure one HTTP connector and one HTTPS connector; add an extra connector programmatically when that topology is required.

</details>

- [Back to top](#back-to-top)

---

## <a id="ssl-bundle-consumption">Applying a Named SSL Bundle to the Web Server</a>

<details>
<summary>Click for details</summary>
SSL bundles are configured under `spring.ssl.bundle.jks.<name>` or `spring.ssl.bundle.pem.<name>`. After the bundle exists, the embedded web server can consume it by name:

```properties
spring.ssl.bundle.pem.web.keystore.certificate=classpath:server.crt
spring.ssl.bundle.pem.web.keystore.private-key=classpath:server.key
server.ssl.bundle=web
```

The generic `SslBundle` / `SslBundles` abstraction belongs to application runtime. This chapter owns the web-specific consumption point: `server.ssl.bundle` tells Boot to apply that named bundle to the embedded server.

### References

- [Spring Boot 3.3 Reference — SSL Bundles](https://docs.spring.io/spring-boot/3.3/reference/features/ssl.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="bundle-vs-discrete-ssl-properties">SSL Bundle Options versus Discrete server.ssl Properties</a>

<details>
<summary>Click for details</summary>
`server.ssl.bundle` cannot be combined with the discrete Java KeyStore or PEM material options under `server.ssl`. When a bundle is used, properties such as `server.ssl.ciphers`, `server.ssl.enabled-protocols`, and `server.ssl.protocol` are ignored.

Put those protocol and cipher options into the named bundle's `spring.ssl.bundle.<type>.<name>.options` configuration instead. This keeps the bundle self-contained and prevents two configuration models from competing for the same TLS state.

</details>

- [Back to top](#back-to-top)

---

## <a id="server-name-indication">Server Name Indication and Certificate Selection</a>

<details>
<summary>Click for details</summary>
Boot 3.3 can map host names to additional SSL bundles through `server.ssl.server-name-bundles`. The bundle configured by `server.ssl.bundle` remains the default certificate material, while named host entries select alternatives for SNI-capable clients.

Tomcat, Netty, and Undertow have Boot-managed SNI configuration. Jetty is different: Boot's explicit SNI mapping is not supported there, although Jetty can automatically configure SNI when multiple certificates are supplied. Treat this as a server capability difference, not a generic TLS rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="tls-ownership-boundary">Web-server TLS Integration versus Generic TLS and PKI</a>

<details>
<summary>Click for details</summary>
This module should teach where Boot receives certificate material, how it attaches that material to the embedded server, how named bundles are selected, and where server-specific limitations appear. It should also teach deployment interactions such as TLS termination at a reverse proxy.

Certificate issuance, CA trust models, handshake cryptography, cipher design, certificate-chain validation, ACME protocol behavior, and PKI operations belong to security/network owners. Boot consumes those concepts through configuration; it does not redefine them.

</details>

- [Back to top](#back-to-top)
