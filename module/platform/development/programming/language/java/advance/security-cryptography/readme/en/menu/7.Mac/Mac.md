<a id="back-to-top"></a>

# Message Authentication Codes

## Menu
- [What Problem Does a MAC Solve?](#mac-model)
- [The Role of the Shared Secret Key](#mac-shared-secret)
- [MAC Generation and Verification](#mac-workflow)
- [MACs vs Digests and Digital Signatures](#mac-comparison)

## <a id="mac-model">What Problem Does a MAC Solve?</a>

<details>
<summary>Click for details</summary>

A Message Authentication Code adds a **secret key** to the integrity/authenticity problem. A receiver accepts a tag only when it can reproduce it from the same message and the expected shared secret.

~~~text
message + shared secret
       ↓ MAC
authentication tag
~~~

An attacker who modifies the message but does not know the secret should not be able to construct a valid replacement tag under the algorithm's security assumptions.

Unlike a plain digest, the trust comes from the shared secret. Unlike a digital signature, every holder of that secret can normally create valid MACs.
</details>

- [Back to top](#back-to-top)

---

## <a id="mac-shared-secret">The Role of the Shared Secret Key</a>

<details>
<summary>Click for details</summary>

The shared secret defines the trust boundary of a MAC. If services A and B share an HMAC key, A can verify messages from B, but A can also generate a message/tag pair that looks valid to any other holder of the same key.

That is appropriate when the protocol accepts shared authority inside one trust domain. It is not appropriate when verifiers must not have signing authority; an asymmetric signature fits that requirement better.

The MAC key must have suitable entropy and size for the chosen algorithm. A human password should not be used directly as an HMAC key; derive key material first when the source is a password.
</details>

- [Back to top](#back-to-top)

---

## <a id="mac-workflow">MAC Generation and Verification</a>

<details>
<summary>Click for details</summary>

Java exposes MAC algorithms through javax.crypto.Mac:

~~~java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(secretKey);
mac.update(headerBytes);
byte[] tag = mac.doFinal(payloadBytes);
~~~

The verifier computes the MAC over the **same canonical bytes** and compares tags. For byte-array tags, `MessageDigest.isEqual(expectedTag, receivedTag)` is preferable to a hand-written loop that exits on the first mismatch:

~~~java
byte[] expectedTag = verifier.doFinal(payloadBytes);
boolean valid = MessageDigest.isEqual(expectedTag, receivedTag);
~~~

`MessageDigest.isEqual(...)` examines all bytes of the first digest and its calculation time does not depend on the digest contents. If one side serializes JSON differently, normalizes newlines differently, or uses another charset, the MAC still changes even if the application considers the logical content equivalent.

A protocol therefore needs a precise definition of the authenticated byte sequence.

When debugging a mismatch, log non-secret metadata such as algorithm, message identifier, version, and lengths — not the shared secret itself.
</details>

- [Back to top](#back-to-top)

---

## <a id="mac-comparison">MACs vs Digests and Digital Signatures</a>

<details>
<summary>Click for details</summary>

A useful distinction is “who can create a valid value?”:

| Mechanism | Key used to create | Who can create |
| --- | --- | --- |
| digest | none | anyone with the data |
| MAC | shared secret | every secret holder |
| signature | private key | private-key holder |

A MAC provides integrity plus shared-secret authenticity, but does not hide the message.

For a new protocol that needs both confidentiality and integrity, authenticated encryption is often clearer and less error-prone than designing a custom encrypt-then-MAC composition, unless an existing specification already defines that composition.
A MAC provides authenticity inside a shared-secret trust domain; when verifiers must not gain signing authority, the next model separates that authority with digital signatures.

</details>

- [Back to top](#back-to-top)
