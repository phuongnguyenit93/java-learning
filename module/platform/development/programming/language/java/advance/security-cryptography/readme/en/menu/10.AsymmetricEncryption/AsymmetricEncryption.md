<a id="back-to-top"></a>

# Asymmetric Encryption

## Menu
- [Asymmetric Encryption Model](#asymmetric-encryption-model)
- [Public- and Private-Key Roles](#public-private-key-roles)
- [Limits and Costs of Asymmetric Encryption](#asymmetric-encryption-limits)
- [Hybrid Encryption Model](#hybrid-encryption-model)

## <a id="asymmetric-encryption-model">Asymmetric Encryption Model</a>

<details>
<summary>Click for details</summary>

Asymmetric encryption uses a public/private key pair to separate the party that can **protect data for a receiver** from the party that can **decrypt it**.

A common model is:

~~~text
plaintext + receiver public key
        ↓ encrypt
ciphertext

ciphertext + receiver private key
        ↓ decrypt
plaintext
~~~

The public key can be distributed; the private key must remain protected. But simply possessing a public key does not tell the sender whose key it is. Certificates and trust mechanisms provide that identity binding in many protocols.

Asymmetric encryption is not digital signature in reverse. Their goals, schemes, encodings, and failure properties are different.
</details>

- [Back to top](#back-to-top)

---

## <a id="public-private-key-roles">Public- and Private-Key Roles</a>

<details>
<summary>Click for details</summary>

Key roles follow the primitive, not a generic rule that “public and private keys can just be used in opposite directions.”

For public-key encryption, the public key commonly encrypts for the private-key owner. For signatures, the private key signs and the public key verifies. In key agreement, each participant combines its own private key with peer public information according to the protocol.

In Java, public-key encryption still uses `Cipher`; the key role is what changes:

~~~java
Cipher encryptor = Cipher.getInstance(transformation);
encryptor.init(Cipher.ENCRYPT_MODE, receiverPublicKey);
byte[] ciphertext = encryptor.doFinal(plaintext);

Cipher decryptor = Cipher.getInstance(transformation);
decryptor.init(Cipher.DECRYPT_MODE, receiverPrivateKey);
byte[] recovered = decryptor.doFinal(ciphertext);
~~~

The `transformation` must come from a clearly specified protocol or scheme. Do not use an ambiguous string such as `"RSA"` merely to avoid choosing the required padding/encoding scheme.

The PublicKey and PrivateKey interfaces also do not mean every key family works with every engine. An Ed25519 signing key, for example, is not automatically an RSA encryption key.

Code should derive key usage from the protocol and trusted metadata, not try arbitrary engines and silently fall back after InvalidKeyException.
</details>

- [Back to top](#back-to-top)

---

## <a id="asymmetric-encryption-limits">Limits and Costs of Asymmetric Encryption</a>

<details>
<summary>Click for details</summary>

Public-key operations are typically more expensive than symmetric encryption and often have strict input-size limits imposed by the scheme and key size. RSA encryption, for example, should not be treated as a stream cipher for arbitrarily chunking large files.

Padding and encoding schemes are part of the security definition. “RSA” alone is not a complete secure protocol.

Public-key cryptography is therefore commonly used to protect **small secret material** or establish keys, while large payloads are protected by symmetric authenticated encryption.

That split leads naturally to hybrid encryption: each primitive is used for the job it handles well.
</details>

- [Back to top](#back-to-top)

---

## <a id="hybrid-encryption-model">Hybrid Encryption Model</a>

<details>
<summary>Click for details</summary>

Hybrid encryption combines public-key and symmetric mechanisms:

~~~text
generate random content-encryption key
        ↓
protect payload with symmetric AEAD
        ↓
protect/establish the content key with a public-key mechanism
        ↓
package protected key + nonce + ciphertext + metadata
~~~

The receiver performs the private-key operation to recover or derive the content key, then uses the symmetric key for the payload.

This avoids expensive public-key operations over large data and lets AEAD provide efficient authenticated payload protection.

Prefer an established protocol or container format over inventing a custom hybrid envelope. Key wrapping, context binding, algorithm identifiers, and versioning all affect security and interoperability.
Hybrid encryption addresses payload efficiency and key transport, but confidentiality alone still does not guarantee tamper detection; authenticated encryption is the next step.

</details>

- [Back to top](#back-to-top)
