<a id="back-to-top"></a>

# Symmetric Encryption

## Menu
- [Symmetric Encryption Model](#symmetric-encryption-model)
- [Cipher Transformations, Modes, and Padding](#cipher-transformation)
- [IVs and Algorithm Parameters](#iv-and-parameters)
- [Encryption and Decryption Workflow](#symmetric-encryption-workflow)

## <a id="symmetric-encryption-model">Symmetric Encryption Model</a>

<details>
<summary>Click for details</summary>

Symmetric encryption uses the **same secret key** for encryption and decryption. It is well suited to bulk data because symmetric operations are generally efficient compared with public-key operations.

~~~text
plaintext + secret key
        ↓ encrypt
ciphertext

ciphertext + same secret key
        ↓ decrypt
plaintext
~~~

Security depends on more than the algorithm name. The key must be generated or derived correctly, distributed safely, rotated according to policy, and kept out of logs and accidental serialization.

“Symmetric” does not mean a protocol must use **one key for both traffic directions**. Many protocols derive separate client-to-server and server-to-client keys for key separation even though each directional key is still used by a symmetric primitive.

Symmetric encryption provides confidentiality. If the mode does not authenticate the ciphertext, the application still needs tamper detection. That is why this chapter establishes the Cipher model and the AEAD chapter adds authenticated protection.
</details>

- [Back to top](#back-to-top)

---

## <a id="cipher-transformation">Cipher Transformations, Modes, and Padding</a>

<details>
<summary>Click for details</summary>

Java Cipher commonly uses a transformation of the form:

~~~text
algorithm / mode / padding
~~~

For example, AES/GCM/NoPadding and AES/CBC/PKCS5Padding describe meaningfully different security behavior. Supplying only “AES” can leave mode/padding choices to provider defaults and make the intended contract unclear.

The **algorithm** names the core primitive. The **mode** controls how the block cipher is applied across data and which IV/nonce rules matter. **Padding** describes how input that does not match a block boundary is handled when the selected mode needs padding.

Treat the transformation as a security contract, not as an arbitrary configuration string. New designs that need tamper detection should prefer an appropriate authenticated mode rather than inventing integrity composition around an unauthenticated mode.
</details>

- [Back to top](#back-to-top)

---

## <a id="iv-and-parameters">IVs and Algorithm Parameters</a>

<details>
<summary>Click for details</summary>

Many modes require an **IV or nonce** in addition to the secret key. The value often does not need confidentiality, but it must satisfy the mode-specific property: some designs require unpredictability, others critically require uniqueness.

“Not secret” does not mean “any value is safe.” Reuse can destroy security.

Java represents parameters through types such as IvParameterSpec and GCMParameterSpec. The parameters needed for decryption are typically stored or transmitted with the ciphertext.

~~~text
stored/transmitted package
= algorithm/version metadata
+ IV/nonce
+ ciphertext
(+ authentication tag for authenticated modes)
~~~

The key is secret; IVs and nonces are usually public parameters whose lifecycle still has to follow the protocol.
</details>

- [Back to top](#back-to-top)

---

## <a id="symmetric-encryption-workflow">Encryption and Decryption Workflow</a>

<details>
<summary>Click for details</summary>

The general Cipher workflow is:

~~~java
Cipher cipher = Cipher.getInstance(transformation);
cipher.init(Cipher.ENCRYPT_MODE, key, parameters);
byte[] ciphertext = cipher.doFinal(plaintext);

Cipher decryptor = Cipher.getInstance(transformation);
decryptor.init(Cipher.DECRYPT_MODE, key, parameters);
byte[] recovered = decryptor.doFinal(ciphertext);
~~~

This is an API-shape example, not a production recipe without an agreed transformation and parameter strategy.

Cipher is stateful and initialized for a particular operation. Do not treat one instance as an immutable service that can be shared freely among threads.

Most importantly, successful decryption does not always prove that ciphertext was authentic. An unauthenticated mode can produce plaintext after attacker-controlled modification. AEAD adds an authentication check to that boundary.
Symmetric encryption is efficient for payloads but introduces the problem of distributing and protecting a shared key; asymmetric encryption supplies another key model for suitable flows.

</details>

- [Back to top](#back-to-top)
