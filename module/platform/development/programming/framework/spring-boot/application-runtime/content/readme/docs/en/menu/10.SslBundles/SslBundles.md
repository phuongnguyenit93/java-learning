<a id="back-to-top"></a>

# SSL bundles and runtime TLS configuration

## Menu
- [Why Does Boot Provide Named SSL Bundles?](#ssl-bundle-purpose)
- [How Are JKS/PKCS12 and PEM Bundles Configured?](#ssl-bundle-jks-pem)
- [What Does the Auto-Configured `SslBundles` Catalog Provide?](#sslbundles-catalog)
- [What Runtime Material Can an `SslBundle` Expose?](#sslbundle-material)
- [How Do Supported Runtime Consumers Reuse a Named Bundle?](#ssl-bundle-consumers)
- [Where Does Generic SSL Bundle Ownership Hand Off to Web-Server TLS?](#ssl-web-runtime-handoff)
- [What Remains TLS and PKI Infrastructure Rather Than Boot Runtime?](#ssl-pki-boundary)

## <a id="ssl-bundle-purpose">Why Does Boot Provide Named SSL Bundles?</a>

<details>
<summary>Click for details</summary>

Applications often need the same trust or key material in more than one secure connection. Without a reusable abstraction, every supported client or server integration can end up with a separate set of keystore paths, passwords, certificates, and protocol options.

Spring Boot's SSL bundle model gives that material a name under `spring.ssl.bundle`. A runtime consumer can then refer to the named bundle instead of repeating the source material. The bundle becomes the Boot-managed boundary between configuration and the component that needs SSL objects.

This chapter focuses on that reusable runtime abstraction. It assumes the learner already understands what certificates, private keys, trust stores, and TLS are; certificate issuance, PKI trust design, cipher policy, and protocol security belong to security/network curricula.

</details>

- [Back to top](#back-to-top)

---

## <a id="ssl-bundle-jks-pem">How Are JKS/PKCS12 and PEM Bundles Configured?</a>

<details>
<summary>Click for details</summary>

Spring Boot 3.3 supports named bundles from Java keystore formats and from PEM material. JKS/PKCS12 bundles live under `spring.ssl.bundle.jks.<name>`, while PEM bundles live under `spring.ssl.bundle.pem.<name>`.

```yaml
spring:
  ssl:
    bundle:
      pem:
        partner-api:
          truststore:
            certificate: classpath:partner-ca.crt
```

For key material, a bundle can also define a certificate and private key. JKS/PKCS12 configuration uses keystore/truststore locations and passwords appropriate to those formats.

The meaningful design choice is the bundle boundary and name. Do not embed secrets in learning examples or application source; use the repository's normal externalized-configuration and secret-management practices for real credentials.

</details>

- [Back to top](#back-to-top)

---

## <a id="sslbundles-catalog">What Does the Auto-Configured `SslBundles` Catalog Provide?</a>

<details>
<summary>Click for details</summary>

Once named bundles are configured, Boot auto-configures an `SslBundles` bean. It acts as a runtime catalog: application or integration code can request a bundle by name rather than knowing how its trust/key material was represented in configuration.

```java
SslBundle bundle = sslBundles.getBundle("partner-api");
SSLContext sslContext = bundle.createSslContext();
```

This separation is valuable because the consumer depends on the SSL abstraction while configuration owns the material. Changing a PEM bundle to another supported representation does not require every consumer to invent a new configuration contract.

The catalog is not a certificate authority or secret store. It exposes Boot-managed SSL configuration that already exists in the application environment.

</details>

- [Back to top](#back-to-top)

---

## <a id="sslbundle-material">What Runtime Material Can an `SslBundle` Expose?</a>

<details>
<summary>Click for details</summary>

`SslBundle` exposes SSL material in layers so a consumer can choose the level it needs. `getStores()` provides key/trust store access, `getManagers()` provides key/trust manager factories and managers, and `createSslContext()` creates an `SSLContext`. The bundle also carries protocol/options and key details used by supported integrations.

The layered API avoids forcing every consumer to reconstruct lower-level objects from file paths. A library that accepts an `SSLContext` can use the high-level method; an integration that needs manager factories can use the manager layer.

Do not descend to a lower layer without a consumer requirement. More detailed TLS object manipulation increases application-owned security configuration and moves closer to generic TLS/JSSE responsibilities outside this Spring Boot runtime module.

### References

- [Spring Boot 3.3 — SSL](https://docs.spring.io/spring-boot/3.3/reference/features/ssl.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="ssl-bundle-consumers">How Do Supported Runtime Consumers Reuse a Named Bundle?</a>

<details>
<summary>Click for details</summary>

A named bundle becomes useful when a supported Boot integration can refer to it by name, or when application code retrieves it from `SslBundles`. This lets one configuration definition serve multiple runtime consumers without duplicating trust/key properties.

Consumer support is still explicit. A component must understand Boot's bundle integration or accept an SSL object that can be created from the bundle. The existence of a bundle does not automatically rewrite every third-party client on the classpath.

When integrating a library, first check whether Spring Boot's auto-configuration exposes a bundle property for that technology. If it does not, prefer the library's documented SSL hook and use `SslBundle` only at the object level that the library actually accepts.

</details>

- [Back to top](#back-to-top)

---

## <a id="ssl-web-runtime-handoff">Where Does Generic SSL Bundle Ownership Hand Off to Web-Server TLS?</a>

<details>
<summary>Click for details</summary>

This module owns creation and reuse of the generic named SSL bundle. Applying a bundle specifically to an embedded web server, choosing HTTPS ports, configuring server connectors, and reasoning about web-server TLS behavior belong to `web-runtime`.

The handoff can be summarized as:

```text
spring.ssl.bundle.*
  -> named SslBundle / SslBundles catalog       [application-runtime]
  -> server.ssl.bundle=<name> and server TLS   [web-runtime]
```

Keeping the stages separate lets other supported clients reuse the same abstraction without making the SSL chapter a web-server chapter. It also prevents server-specific options from being mistaken for universal bundle semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="ssl-pki-boundary">What Remains TLS and PKI Infrastructure Rather Than Boot Runtime?</a>

<details>
<summary>Click for details</summary>

Spring Boot can load and expose configured key/trust material, but it does not decide the organization's PKI. Certificate issuance and renewal, trust-chain design, hostname verification policy, protocol/cipher choices, key rotation, hardware security modules, and threat modeling remain security/network responsibilities.

Likewise, knowing that a bundle contains a truststore is different from knowing which certificate authorities *should* be trusted. Boot provides configuration structure; the security domain provides the policy.

When troubleshooting SSL, first separate a Boot binding/integration error from a TLS handshake or trust-policy error. A missing bundle name or unreadable resource is a Boot/configuration concern. An invalid certificate chain or hostname mismatch belongs to TLS/PKI investigation.

</details>

- [Back to top](#back-to-top)
