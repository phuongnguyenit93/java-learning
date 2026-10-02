<a id="back-to-top"></a>

# Shared-Secret Establishment

## Menu
- [The Shared-Secret Establishment Problem](#key-establishment-purpose)
- [KeyAgreement Model](#key-agreement-model)
- [Key Encapsulation Mechanism Model](#kem-model)
- [KeyAgreement vs KEM](#agreement-vs-kem)

## <a id="key-establishment-purpose">The Shared-Secret Establishment Problem</a>

<details>
<summary>Click for details</summary>

Two parties that want symmetric protection but do not yet share a secret need a **key-establishment mechanism**. Sending the raw symmetric key over an observable channel defeats the purpose.

Java 21 exposes two useful mental models:

- **KeyAgreement** — participants use key pairs/public information to compute a shared secret;
- **KEM** — a sender encapsulates to produce a secret key plus an encapsulation message, and a receiver decapsulates with a private key to recover the same secret.

The protocol may still need derivation and context binding before final session keys are used.

Key establishment solves secret creation/exchange mechanics; peer identity and trust must still be established separately.
</details>

- [Back to top](#back-to-top)

---

## <a id="key-agreement-model">KeyAgreement Model</a>

<details>
<summary>Click for details</summary>

KeyAgreement represents mechanisms such as Diffie-Hellman or ECDH.

Conceptually:

~~~text
Alice private + Bob public
        ↓
shared secret

Bob private + Alice public
        ↓
same shared secret
~~~

The Java flow obtains an engine, initializes it with the local private key, processes peer public information with `doPhase`, and calls `generateSecret`:

~~~java
KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
agreement.init(alicePrivateKey);
agreement.doPhase(bobPublicKey, true);
byte[] sharedSecret = agreement.generateSecret();
~~~

The raw shared secret is commonly input keying material for a protocol KDF or key schedule. Do not assume those bytes should be used directly as an application key unless the protocol explicitly defines that use.

Public-key exchange does **not** authenticate the peer by itself. If an attacker can replace the exchanged public keys and the protocol has no authenticated binding, the participants may establish secrets with the attacker.

Protocols such as TLS compose key agreement with certificate/signature-based authentication precisely to close that gap.
</details>

- [Back to top](#back-to-top)

---

## <a id="kem-model">Key Encapsulation Mechanism Model</a>

<details>
<summary>Click for details</summary>

Java 21 includes the KEM API for Key Encapsulation Mechanisms.

~~~text
receiver owns public/private key pair

sender:
public key
  ↓ encapsulate
secret key + encapsulation message

receiver:
private key + encapsulation message
  ↓ decapsulate
same secret key
~~~

The encapsulation message can travel publicly; the receiver's private key enables recovery of the shared secret. Java 21 exposes the flow directly:

~~~java
KEM kem = KEM.getInstance("DHKEM");

KEM.Encapsulated encapsulated =
        kem.newEncapsulator(receiverPublicKey).encapsulate();

SecretKey senderSecret = encapsulated.key();
byte[] message = encapsulated.encapsulation();

SecretKey receiverSecret =
        kem.newDecapsulator(receiverPrivateKey).decapsulate(message);
~~~

The no-argument `encapsulate()` returns a `SecretKey` whose algorithm name is `Generic`; that secret is commonly used as input keying material for a KDF or protocol key schedule. KEM is useful for protocols designed around encapsulation rather than interactive agreement.

When `KEM.getInstance(...)` does not pin a provider, Java 21 may select providers later when creating the encapsulator and decapsulator based on the key and parameters. Creating the `KEM` object alone therefore does not necessarily pin one provider for every operation.
</details>

- [Back to top](#back-to-top)

---

## <a id="agreement-vs-kem">KeyAgreement vs KEM</a>

<details>
<summary>Click for details</summary>

Both KeyAgreement and KEM can produce shared secret material, but the interaction models differ:

| | KeyAgreement | KEM |
| --- | --- | --- |
| main input | local private + peer public | receiver public/private + encapsulation |
| output | shared secret | secret key + encapsulation message |
| interaction | often both parties contribute key material | sender encapsulates to receiver |
| Java engine | KeyAgreement | KEM |

Choose according to protocol design and peer capabilities, not because one API is newer.

Neither mechanism automatically solves certificate trust, identity, replay, or lifecycle. Those concerns belong to the surrounding authenticated protocol.
Freshly established secret material commonly feeds a KDF or key schedule, while other secrets may be derived locally from inputs such as passwords; that leads into key derivation.

</details>

- [Back to top](#back-to-top)
