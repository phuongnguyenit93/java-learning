<a id="back-to-top"></a>

# Authenticated Encryption and AEAD

## Menu
- [Why Authenticated Encryption Is Needed](#aead-purpose)
- [Nonce/IV Uniqueness](#nonce-uniqueness)
- [AAD and Authentication Tags](#aad-and-authentication-tag)
- [Tag Verification and Decryption Failure](#aead-decryption-failure)

## <a id="aead-purpose">Why Authenticated Encryption Is Needed</a>

<details>
<summary>Click for details</summary>

Authenticated Encryption with Associated Data (AEAD) combines **confidentiality with integrity/authenticity of the protected record and its chosen context**.

Instead of decrypting ciphertext and merely hoping it was not modified, an AEAD construction produces an authentication tag. Decryption is successful only if verification succeeds.

~~~text
plaintext + key + nonce + optional AAD
        ↓ AEAD encrypt
ciphertext + tag
~~~

Java Cipher supports AEAD modes such as GCM through an appropriate transformation.

AEAD does not solve key ownership or identity by itself. A party that already has a valid key can generate valid ciphertext and tags, so key management and trust remain separate responsibilities.
</details>

- [Back to top](#back-to-top)

---

## <a id="nonce-uniqueness">Nonce/IV Uniqueness</a>

<details>
<summary>Click for details</summary>

For GCM, **nonce/IV uniqueness under the same key is critical**. Reuse can reveal relationships between plaintexts and undermine authentication security.

A nonce usually does not need to remain secret. The hard requirement is lifecycle:

- generate it with a strategy that prevents reuse under the same key;
- store or transmit it with the ciphertext;
- coordinate the strategy with key rotation;
- avoid rollback or duplicated state if counters are used.

Random nonces rely on collision probability at the system's scale; counter-based nonces rely on durable state discipline. Choose according to architecture rather than copying one universal snippet.

Do not try to “fix” reuse by hashing timestamps or previous nonces ad hoc.
</details>

- [Back to top](#back-to-top)

---

## <a id="aad-and-authentication-tag">AAD and Authentication Tags</a>

<details>
<summary>Click for details</summary>

**Additional Authenticated Data (AAD)** is data that does not need encryption but should be bound to the authentication tag, such as a protocol version, record identifier, or routing header.

~~~java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
cipher.updateAAD(headerBytes);
byte[] ciphertextAndTag = cipher.doFinal(plaintext);
~~~

The decrypting side must provide the same AAD bytes. With GCM/CCM, all AAD must be supplied through `updateAAD(...)` **before** ciphertext processing starts through `update(...)` or `doFinal(...)`. Modifying the authenticated header causes tag verification to fail even when the ciphertext itself is unchanged.

AAD is useful for preventing a valid ciphertext from being transplanted into a different context. As with signatures and MACs, the protocol must define the exact byte representation.
</details>

- [Back to top](#back-to-top)

---

## <a id="aead-decryption-failure">Tag Verification and Decryption Failure</a>

<details>
<summary>Click for details</summary>

A tag failure is an **authentication failure**, not a minor data-format error.

Java may report AEADBadTagException from doFinal when verification fails. Applications should not:

- release plaintext to business logic before authentication finishes;
- retry with a weaker algorithm or key;
- dump keys/plaintext into logs;
- expose unnecessarily detailed oracle-like errors to untrusted callers.

~~~java
try {
    byte[] plaintext = decryptor.doFinal(ciphertextAndTag);
    // Use plaintext only after successful final verification.
} catch (AEADBadTagException ex) {
    // Reject the record/message.
}
~~~

Failure semantics are part of the security design.
AEAD assumes both sides already possess suitable symmetric key material; the next question is how parties establish that shared secret in a structured way.

</details>

- [Back to top](#back-to-top)
