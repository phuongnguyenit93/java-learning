<a id="back-to-top"></a>

# Key Management with KeyStore

## Menu
- [KeyStore Model](#keystore-model)
- [KeyStore Entry Types](#keystore-entry-types)
- [Loading, Storing, and Protecting a KeyStore](#keystore-protection)
- [Keystore vs Truststore Roles](#keystore-vs-truststore)

## <a id="keystore-model">KeyStore Model</a>

<details>
<summary>Click for details</summary>

KeyStore is Java's abstraction for storing and retrieving **key entries and certificate entries** through a store type and provider. It should not be reduced mentally to “a .jks file.”

~~~java
KeyStore keyStore = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(path)) {
    keyStore.load(in, storePassword);
}
~~~

A store may be file-backed or mapped by a provider to another source. Application code should depend on the KeyStore contract and deployment configuration instead of assuming every environment has the same physical format.

KeyStore centralizes access to key/certificate material, but does not by itself solve rotation policy, filesystem permissions, HSM administration, or secret distribution.
</details>

- [Back to top](#back-to-top)

---

## <a id="keystore-entry-types">KeyStore Entry Types</a>

<details>
<summary>Click for details</summary>

Three entry types are especially important:

- **PrivateKeyEntry** — a private key with its corresponding certificate chain;
- **SecretKeyEntry** — a symmetric secret key;
- **TrustedCertificateEntry** — a trusted certificate without an associated private key.

An alias existing in the store does not imply that it represents a private key.

~~~java
KeyStore.Entry entry = keyStore.getEntry(
        alias,
        new KeyStore.PasswordProtection(entryPassword)
);
~~~

Code should validate the expected entry type and fail clearly when deployment provides the wrong alias or entry shape. Avoid turning configuration mistakes into opaque ClassCastException failures.
</details>

- [Back to top](#back-to-top)

---

## <a id="keystore-protection">Loading, Storing, and Protecting a KeyStore</a>

<details>
<summary>Click for details</summary>

Two password concepts are often confused:

- a **store password** used while loading/storing some KeyStore formats;
- **entry protection** that may protect an individual private or secret key.

Do not assume these passwords always exist, always match, or behave identically across providers.

If the store is file-backed, filesystem permissions, backup policy, and deployment-secret handling still matter. Hard-coding the password in source merely moves the secret to a weaker location.

With providers such as PKCS#11, KeyStore can also act as a Java boundary to hardware-backed material where a private key may never be exported into ordinary application memory.
</details>

- [Back to top](#back-to-top)

---

## <a id="keystore-vs-truststore">Keystore vs Truststore Roles</a>

<details>
<summary>Click for details</summary>

**Keystore** and **truststore** are usually names for **roles**, not distinct Java types or mandatory file formats.

- a local identity store commonly holds a PrivateKeyEntry plus certificate chain;
- a trust store commonly holds TrustedCertificateEntry values representing trusted issuers or anchors.

Both can be PKCS12 KeyStore instances. In JSSE, a KeyManager consumes local credentials while a TrustManager consumes trust material.

Separate stores often make operations and least privilege clearer, but that is a deployment design choice. Do not infer a store's role only from its filename extension; inspect entry types and configuration.
KeyStore explains where key and certificate material is held and under which entry roles; deciding whether a public key or certificate is trusted requires certification-path validation next.

</details>

- [Back to top](#back-to-top)
