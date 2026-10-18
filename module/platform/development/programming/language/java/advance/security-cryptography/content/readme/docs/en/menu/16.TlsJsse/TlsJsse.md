<a id="back-to-top"></a>

# JSSE and TLS Security

## Menu
- [JSSE Model](#jsse-model)
- [SSLContext, KeyManager, and TrustManager](#ssl-context-managers)
- [TLS Handshake and Secure-Session Establishment](#tls-handshake-security)
- [Endpoint Identity Verification](#endpoint-identification)
- [TLS Protocol and Algorithm Constraints](#tls-algorithm-constraints)

## <a id="jsse-model">JSSE Model</a>

<details>
<summary>Click for details</summary>

JSSE (Java Secure Socket Extension) brings TLS security semantics into Java through APIs such as SSLContext, SSLSocket, SSLEngine, KeyManager, and TrustManager.

The focus here is **security composition**, not socket programming.

~~~text
local key/certificate material
        ↓ KeyManager
SSLContext
        ↑ TrustManager
trusted CA / peer trust material
        ↓
TLS handshake
        ↓
authenticated secure session
~~~

TLS combines capabilities learned earlier: certificate authentication, key establishment, negotiated algorithms, and symmetric authenticated traffic protection. That is why JSSE appears near the end of the module.
</details>

- [Back to top](#back-to-top)

---

## <a id="ssl-context-managers">SSLContext, KeyManager, and TrustManager</a>

<details>
<summary>Click for details</summary>

SSLContext is a factory/state holder for secure-protocol implementations. It is initialized with `KeyManager[]`, `TrustManager[]`, and `SecureRandom`:

~~~java
SSLContext context = SSLContext.getInstance("TLS");
context.init(keyManagers, trustManagers, secureRandom);
~~~

A **KeyManager** chooses local authentication credentials to present to the peer. A **TrustManager** decides whether remote authentication credentials should be trusted.

These roles are not simply “server versus client.” With mutual TLS, both peers may present an identity and validate the other side.

For `null`, distinguish the **Java SE API contract** from the JDK behavior documented by Oracle's JSSE guide:

- at the `SSLContext.init(...)` API level, a `null` `KeyManager[]` or `TrustManager[]` allows the runtime to search installed security providers for the highest-priority implementation of the corresponding factory; `SecureRandom == null` allows a default implementation;
- Oracle's JSSE Reference Guide for the JDK documents `KeyManager[] == null` as defining an empty `KeyManager` for the context, while `TrustManager[] == null` uses the default `TrustManagerFactory` to obtain an appropriate `TrustManager`.

Do not collapse these cases into one vague “use defaults” rule, and do not interpret `null` as disabling certificate or trust validation. If application behavior depends on default resolution, verify the provider/JDK behavior being deployed.
</details>

- [Back to top](#back-to-top)

---

## <a id="tls-handshake-security">TLS Handshake and Secure-Session Establishment</a>

<details>
<summary>Click for details</summary>

The TLS handshake establishes a security context before normal application data flows. At a conceptual level it needs to:

- negotiate compatible protocol and cryptographic capabilities;
- authenticate peers as required by the mode;
- perform key establishment;
- derive traffic keys;
- confirm both parties agree on the protected handshake state.

The record layer then uses symmetric authenticated protection for traffic.

Applications should not extract and repurpose “TLS keys” for unrelated application cryptography. TLS owns its key schedule and context binding.

Handshake failures often represent meaningful security/configuration problems: untrusted certificates, wrong identities, disabled algorithms, protocol mismatches, or unsuitable key material.
</details>

- [Back to top](#back-to-top)

---

## <a id="endpoint-identification">Endpoint Identity Verification</a>

<details>
<summary>Click for details</summary>

Trusting a certificate path is not enough; a client also needs **endpoint identity verification**.

`SSLParameters` supports endpoint-identification configuration for relevant JSSE clients. For example, an `SSLSocket` client can request HTTPS-style hostname verification:

~~~java
SSLParameters parameters = socket.getSSLParameters();
parameters.setEndpointIdentificationAlgorithm("HTTPS");
socket.setSSLParameters(parameters);
~~~

When the algorithm is non-null/non-empty, endpoint identification must be handled during the TLS handshake. Higher-level HTTP clients commonly configure HTTPS hostname-verification behavior for their own use case.

SNI and endpoint verification are related but different. SNI tells the server which name the client is trying to reach; endpoint verification checks whether the peer certificate actually represents that target.

Do not solve development-certificate problems in production by installing a trust-all manager or disabling hostname checks. For tests, create explicit test trust material instead of globally removing verification.
</details>

- [Back to top](#back-to-top)

---

## <a id="tls-algorithm-constraints">TLS Protocol and Algorithm Constraints</a>

<details>
<summary>Click for details</summary>

TLS behavior is shaped by multiple policy layers:

- enabled protocol versions and cipher suites in SSLParameters;
- provider support;
- JDK security properties and disabled-algorithm rules;
- certificate/key constraints;
- peer capabilities.

Therefore, configuring a cipher suite does not guarantee that the handshake can use it.

A JDK upgrade may cause an old handshake to fail because a weak algorithm or key size is newly restricted. The correct response is usually to update certificates, protocols, or configuration according to policy — not to weaken JVM-wide constraints without analysis.

Evolving security defaults protect the ecosystem, but deployment compatibility should be tested before upgrades.
JSSE/TLS demonstrates how multiple primitives and trust mechanisms cooperate inside one protocol; the final chapter synthesizes common design failures and an end-to-end review checklist.

</details>

- [Back to top](#back-to-top)
