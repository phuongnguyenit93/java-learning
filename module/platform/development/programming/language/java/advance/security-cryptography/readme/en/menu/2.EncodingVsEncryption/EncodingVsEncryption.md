<a id="back-to-top"></a>

# Encoding, Hashing, and Encryption

## Menu
- [What Encoding Is For](#encoding-purpose)
- [What Hashing Is For](#hashing-purpose)
- [What Encryption Is For](#encryption-purpose)
- [What MACs and Digital Signatures Add](#authentication-purpose)

## <a id="encoding-purpose">What Encoding Is For</a>

<details>
<summary>Click for details</summary>

**Encoding** changes representation so data fits a format or transport. It does not create a confidentiality boundary. Base64 is the common example: binary bytes become text, but anyone can decode them without a secret.

~~~java
String encoded = Base64.getEncoder()
        .encodeToString("hello".getBytes(StandardCharsets.UTF_8));

byte[] original = Base64.getDecoder().decode(encoded);
~~~

A token that is only Base64-encoded remains a secret in a trivially reversible representation. “Not immediately readable” is not encryption.

Encoding still appears around cryptography because ciphertext, digests, signatures, and certificates often need text-safe representation. Treat it as a **representation layer**, not as a security primitive.
</details>

- [Back to top](#back-to-top)

---

## <a id="hashing-purpose">What Hashing Is For</a>

<details>
<summary>Click for details</summary>

A cryptographic hash accepts arbitrary input and produces a fixed-size digest. The same input produces the same digest, while suitable algorithms are designed so that reversing the digest or finding useful collisions is computationally difficult.

Hashes are useful for fingerprints, trusted file checks, and as building blocks inside other constructions. They have **no secret key**, which means an attacker who can change both the data and the stored digest can simply compute a new digest.

That is why hashing alone is not authentication. It is also why storing passwords as a single MessageDigest value is insufficient: human passwords have low entropy and attackers can test candidates quickly. Password-based derivation with salt and work factor appears later in the module.
</details>

- [Back to top](#back-to-top)

---

## <a id="encryption-purpose">What Encryption Is For</a>

<details>
<summary>Click for details</summary>

Encryption provides confidentiality by transforming plaintext into ciphertext under the control of a key. A party with the appropriate key and parameters can reverse the transformation.

~~~text
plaintext + key + parameters
        ↓ encrypt
ciphertext
        ↓ decrypt
plaintext
~~~

This reversibility under key control is the key distinction from hashing. Hashing has no decrypt operation. Encoding is reversible too, but requires no secret.

Confidentiality does not automatically imply integrity. If both are required, an authenticated-encryption construction such as GCM is usually a better starting point than inventing an ad hoc combination of encryption and integrity checks.
</details>

- [Back to top](#back-to-top)

---

## <a id="authentication-purpose">What MACs and Digital Signatures Add</a>

<details>
<summary>Click for details</summary>

MACs and digital signatures both address the question “was this data modified, and is it tied to an expected key holder?”, but their trust models differ.

A **MAC** uses a secret shared by the participating parties. Every holder of that secret can usually create a valid MAC, so the model fits a shared trust domain.

A **digital signature** uses a private key for signing and a public key for verification. Verifiers do not need the private key, so they can validate data without gaining signing authority.

Neither mechanism hides the message by default. When data needs both confidentiality and authenticity, use a protocol or primitive that composes those guarantees correctly, such as AEAD or TLS, instead of assuming MAC or Signature is encryption.
With those distinctions clear, the next step is to understand how Java maps primitive and algorithm names to concrete implementations through JCA/JCE and Providers.

</details>

- [Back to top](#back-to-top)
