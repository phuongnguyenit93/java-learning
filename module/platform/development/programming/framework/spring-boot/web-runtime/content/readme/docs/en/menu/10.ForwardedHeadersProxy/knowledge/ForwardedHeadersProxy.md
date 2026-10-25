<a id="back-to-top"></a>

# Forwarded headers and reverse-proxy deployment

## Menu
- [Why Proxy Deployment Changes Request Metadata](#proxy-request-metadata)
- [NONE, NATIVE, and FRAMEWORK Strategies](#forward-header-strategies)
- [When Native Server Forwarded-header Support Is Enough](#native-forward-headers)
- [When to Use Spring Framework Forwarded-header Handling](#framework-forward-headers)
- [Boot Defaults on Supported Cloud Platforms](#cloud-forward-header-defaults)
- [TLS Termination, Redirects, and External Scheme Awareness](#proxy-tls-termination)
- [Server-specific Proxy and Remote-IP Settings](#server-specific-proxy-settings)
- [Forwarded Headers and the Proxy Trust Boundary](#forwarded-header-trust-boundary)

## <a id="proxy-request-metadata">Why Proxy Deployment Changes Request Metadata</a>

<details>
<summary>Click for details</summary>
Behind a reverse proxy, the connection observed by the embedded server can differ from the public request. The application may receive traffic on `10.0.0.5:8080` over HTTP while the client actually used `https://example.org` on port `443`. If that difference is ignored, generated redirects, links, scheme checks, and client-address observations can be wrong.

Forwarded headers carry selected information about the original request across that proxy hop. Boot's job is to choose how the application/server consumes that metadata; proxy routing and header production remain infrastructure concerns.

### References

- [Spring Boot 3.3 How-to — Running Behind a Front-end Proxy Server](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)

</details>

- [Back to top](#back-to-top)

---

## <a id="forward-header-strategies">NONE, NATIVE, and FRAMEWORK Strategies</a>

<details>
<summary>Click for details</summary>
`server.forward-headers-strategy` chooses Boot's handling mode. `NONE` does not enable forwarded-header processing. `NATIVE` delegates to the embedded server's native mechanism. `FRAMEWORK` uses Spring Framework support in the application stack.

Choose the mode from the deployment contract. If the proxy provides standard/common headers that the server handles correctly, `NATIVE` keeps processing close to the server. If the application needs the broader Spring Framework transformation model, use `FRAMEWORK`.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-forward-headers">When Native Server Forwarded-header Support Is Enough</a>

<details>
<summary>Click for details</summary>
Boot's 3.3 guidance says `NATIVE` is sufficient when the proxy supplies commonly used `X-Forwarded-For` and `X-Forwarded-Proto` headers and the selected web server's native support matches the deployment needs.

Native behavior is server-specific. Header names, trusted-proxy rules, remote-address rewriting, and other details can differ, so use the selected server's documentation when the common Boot strategy is correct but the exact native result needs tuning.

</details>

- [Back to top](#back-to-top)

---

## <a id="framework-forward-headers">When to Use Spring Framework Forwarded-header Handling</a>

<details>
<summary>Click for details</summary>
When native server handling is insufficient, `FRAMEWORK` activates Spring Framework's forwarded-header facilities: `ForwardedHeaderFilter` for Servlet applications and `ForwardedHeaderTransformer` for reactive applications.

The Boot-owned decision is selecting this strategy. The detailed filtering/transformation behavior belongs to Spring Framework. This separation matters because changing the strategy can alter application-visible request metadata without changing the actual socket used by the embedded server.

</details>

- [Back to top](#back-to-top)

---

## <a id="cloud-forward-header-defaults">Boot Defaults on Supported Cloud Platforms</a>

<details>
<summary>Click for details</summary>
In Boot 3.3, `server.forward-headers-strategy` defaults to `NATIVE` when the application runs on a supported cloud platform. In other environments it defaults to `NONE`.

Do not copy the cloud assumption blindly into another deployment. The safe configuration depends on whether all direct traffic reaches the application through a trusted proxy that sanitizes and supplies the expected forwarded headers.

</details>

- [Back to top](#back-to-top)

---

## <a id="proxy-tls-termination">TLS Termination, Redirects, and External Scheme Awareness</a>

<details>
<summary>Click for details</summary>
TLS is often terminated at the proxy, leaving an HTTP hop between the proxy and the application. The server's local connection then appears insecure even though the client-facing request was HTTPS. Correct forwarded-protocol handling lets application-visible request metadata retain the external `https` scheme.

This matters for redirects and generated absolute URLs. With Tomcat, Boot specifically documents `server.tomcat.redirect-context-root=false` when SSL terminates at the proxy so `X-Forwarded-Proto` can be honored before a context-root redirect is produced.

</details>

- [Back to top](#back-to-top)

---

## <a id="server-specific-proxy-settings">Server-specific Proxy and Remote-IP Settings</a>

<details>
<summary>Click for details</summary>
When the common strategy is not enough, server-specific namespaces expose deeper controls. Tomcat, for example, allows custom forwarded header names and trusted internal-proxy patterns under `server.tomcat.remoteip.*`.

Use these settings only when the deployment contract requires them. They couple configuration to one server and should not be taught as portable `server.*` behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="forwarded-header-trust-boundary">Forwarded Headers and the Proxy Trust Boundary</a>

<details>
<summary>Click for details</summary>
Forwarded headers are trustworthy only when a trusted proxy controls them and direct untrusted clients cannot bypass that proxy path. Otherwise a client can send its own forwarded metadata and make the application believe a false scheme, host, or remote address.

Boot documentation therefore recommends enabling forwarded-header support only when traffic comes from a trusted HTTP proxy or trusted network. The full proxy trust model belongs to infrastructure/security learning, but the Boot configuration must preserve that boundary rather than treating incoming forwarding headers as inherently authoritative.

</details>

- [Back to top](#back-to-top)
