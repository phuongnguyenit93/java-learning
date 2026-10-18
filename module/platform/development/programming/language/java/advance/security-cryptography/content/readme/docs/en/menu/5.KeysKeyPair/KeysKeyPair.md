<a id="back-to-top"></a>

# Secret Keys, Public Keys, and Key Pairs

## Menu
- [Secret, Public, and Private Keys](#key-models)
- [KeyGenerator and KeyPairGenerator](#key-generation)
- [KeyFactory, SecretKeyFactory, and Key Specifications](#key-factory-specification)
- [Key-Material Lifecycle and Protection Boundaries](#key-material-boundary)

## <a id="key-models">Secret, Public, and Private Keys</a>

<details>
<summary>Click for details</summary>

Cryptographic primitives use different key models because they express different trust relationships.

A **secret key** is confidential material used by symmetric primitives such as AES or HMAC. A party that has the secret usually has the authority to perform the corresponding operation.

A **public/private key pair** separates capabilities. The public key can be distributed more broadly, while the private key must remain protected. Depending on the primitive, the public key may verify signatures, encrypt or encapsulate, or participate in key establishment; the private key performs the complementary operation.

Java represents these roles through Key, SecretKey, PublicKey, and PrivateKey. The type does not manage lifecycle by itself: the application still needs to know where the key came from, how long it should exist, and whether it can be exported.
</details>

- [Back to top](#back-to-top)

---

## <a id="key-generation">KeyGenerator and KeyPairGenerator</a>

<details>
<summary>Click for details</summary>

A **generator creates new key material**, unlike a factory that reconstructs a key from existing material.

For symmetric keys, KeyGenerator is the main abstraction:

~~~java
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(256);
SecretKey key = generator.generateKey();
~~~

For asymmetric pairs, use KeyPairGenerator:

~~~java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair pair = generator.generateKeyPair();
~~~

The algorithms and sizes above demonstrate API shape, not a universal production recommendation. Real systems must follow current protocol and security policy.

Key generation ultimately depends on suitable randomness, which is why SecureRandom appears earlier in the learning path.
</details>

- [Back to top](#back-to-top)

---

## <a id="key-factory-specification">KeyFactory, SecretKeyFactory, and Key Specifications</a>

<details>
<summary>Click for details</summary>

Factories solve a different problem from generators: they **convert existing representations into Key objects or back into specifications**.

KeyFactory is commonly used with public/private keys. An encoded public key may be represented by X509EncodedKeySpec, while PKCS8EncodedKeySpec commonly represents encoded private-key material.

SecretKeyFactory is the analogous provider-based factory for supported secret-key representations, especially password-based material.

~~~text
brand-new key material
→ KeyGenerator / KeyPairGenerator

existing representation / KeySpec
→ KeyFactory / SecretKeyFactory
~~~

SecretKeySpec can wrap raw bytes as a provider-independent SecretKey for suitable algorithms, but it does not validate every algorithm-specific invariant. A specialized factory/specification may express the contract more precisely.
</details>

- [Back to top](#back-to-top)

---

## <a id="key-material-boundary">Key-Material Lifecycle and Protection Boundaries</a>

<details>
<summary>Click for details</summary>

A Key object is a point where Java code touches **high-value secret material**, so lifecycle matters as much as the algorithm.

A design review should ask:

- was the key generated, derived, established, or loaded from a store/HSM?
- can the raw key be exported?
- how long does it remain in memory?
- can logging, serialization, crash dumps, or exceptions expose it?
- who owns rotation and revocation?

Some key implementations participate in Destroyable-style lifecycle controls, but destroying one object should not be interpreted as proof that every copy has disappeared from heap, provider, or native memory.

This module owns the Java API boundary. Key rotation, backup, escrow, and hardware protection are infrastructure concerns.
With the key model established, the integrity/authenticity family can start from its simplest case: an unkeyed digest.

</details>

- [Back to top](#back-to-top)
