<a id="back-to-top"></a>

# JCA/JCE, Algorithms, and Security Providers

## Menu
- [JCA/JCE Architecture](#jca-jce-architecture)
- [Engine Classes, SPIs, and Providers](#engine-spi-provider)
- [Standard Algorithm Names and Transformations](#algorithm-names-transformations)
- [How Java Selects Providers](#provider-selection)
- [Security Properties and Algorithm Constraints](#security-properties-constraints)

## <a id="jca-jce-architecture">JCA/JCE Architecture</a>

<details>
<summary>Click for details</summary>

The Java Cryptography Architecture provides a common API layer for security services. Applications work with **engine classes** such as MessageDigest, Signature, SecureRandom, and KeyStore; the same provider architecture covers JCE engines such as Cipher, Mac, KeyAgreement, KeyGenerator, SecretKeyFactory, and KEM.

Application code normally requests a service by algorithm name:

~~~java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
~~~

JCA then finds an implementation through the registered security Providers. This lets application code depend on a **service contract plus algorithm name** instead of directly constructing one vendor implementation.

That provider lookup is why provider architecture belongs in this module: it explains how an abstract cryptographic request becomes concrete runtime behavior.
</details>

- [Back to top](#back-to-top)

---

## <a id="engine-spi-provider">Engine Classes, SPIs, and Providers</a>

<details>
<summary>Click for details</summary>

Keep three layers separate:

- an **engine class** is the API used by application code, such as Signature or Cipher;
- an **SPI** is the provider-side contract, such as SignatureSpi or CipherSpi;
- a **Provider** registers implementations of security services.

~~~text
application
    ↓
engine class
    ↓
JCA service lookup
    ↓
Provider
    ↓
SPI implementation
~~~

Ordinary application code rarely implements the SPI itself. Writing a provider is an extensibility task used for special implementations, hardware, or compliance environments.

The separation lets the engine-level application code remain stable while the concrete provider can change across JDKs, operating systems, or deployments.
</details>

- [Back to top](#back-to-top)

---

## <a id="algorithm-names-transformations">Standard Algorithm Names and Transformations</a>

<details>
<summary>Click for details</summary>

Engine classes use **standard algorithm names** to describe the requested service. MessageDigest may use SHA-256, Mac may use HmacSHA256, and KeyStore may use PKCS12.

Cipher commonly uses a **transformation**:

~~~text
algorithm / mode / padding
~~~

AES/GCM/NoPadding says more than “AES”: it also fixes the mode and padding semantics, which in turn determine requirements such as nonce handling and authentication tags.

Avoid ambiguous transformation strings when security depends on mode or padding behavior. Explicit transformations make code review and interoperability clearer.

Valid names and mandatory support should be checked against the Java Security Standard Algorithm Names specification and the provider in the actual runtime, not inferred from a random example.
</details>

- [Back to top](#back-to-top)

---

## <a id="provider-selection">How Java Selects Providers</a>

<details>
<summary>Click for details</summary>

Calling getInstance with only an algorithm name lets JCA search Providers in preference order. That is often the most portable choice:

~~~java
Signature signature = Signature.getInstance("SHA256withRSA");
~~~

Other overloads let code name a Provider or pass a Provider instance. Bind to a provider only when there is a concrete reason, such as a required PKCS#11/HSM integration or a service available only from that provider.

For `Cipher`, `KeyAgreement`, `Mac`, and `Signature`, the JDK also supports **delayed provider selection**. Calling `getInstance(...)` does not necessarily commit the engine to its final provider. The JDK can defer that choice until an initialization call receives the actual `Key`, allowing it to select a provider that can handle that key — an important capability for unextractable token/HSM keys.

~~~java
Signature signature = Signature.getInstance("SHA256withRSA");
signature.initSign(privateKey); // the actual provider may be selected here
Provider selected = signature.getProvider();
~~~

Once a provider has been selected for that engine instance, do not assume a later initialization will transparently switch providers. To reselect based on another key, obtain a new engine instance and initialize it with that key.

Unnecessary provider pinning makes deployment less flexible because provider names and service inventories can differ across JDKs and operating systems.

When an algorithm is required by the application contract, validate availability during bootstrap and fail explicitly rather than silently substituting a different primitive.
</details>

- [Back to top](#back-to-top)

---

## <a id="security-properties-constraints">Security Properties and Algorithm Constraints</a>

<details>
<summary>Click for details</summary>

An API class being present does not mean every algorithm, protocol, or key size is allowed. JDK security properties and algorithm constraints can disable choices that are considered weak or incompatible with policy.

TLS and certification-path processing both apply such policy. Providers also expose only a particular set of services.

Treat policy-related failures as **security signals**, not invitations to downgrade automatically.

~~~text
API type exists
≠
provider implements service
≠
security policy permits use
~~~

Those three layers explain many NoSuchAlgorithmException, InvalidKeyException, certificate-validation, and handshake failures that the Java type system alone cannot reveal.
Once provider lookup is clear, the next dependency is the input material many cryptographic operations rely on: unpredictable randomness.

</details>

- [Back to top](#back-to-top)
