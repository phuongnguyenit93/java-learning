<a id="back-to-top"></a>

# Cryptography and Security Goals

## Menu
- [Security Goals: Confidentiality, Integrity, and Authenticity](#security-goals)
- [What Part of Security Does Cryptography Solve?](#cryptography-role)
- [Threat Models and Module Boundaries](#threat-model-boundary)
- [Choose Cryptographic Mechanisms from Security Goals](#primitive-by-goal)

## <a id="security-goals">Security Goals: Confidentiality, Integrity, and Authenticity</a>

<details>
<summary>Click for details</summary>

Cryptography matters when data must retain one or more **security guarantees** even while an attacker may be able to observe, modify, replay, or forge traffic or stored records.

The three goals used throughout this module are:

- **confidentiality** — unauthorized parties should not learn the protected content;
- **integrity** — unauthorized modification should be detectable;
- **authenticity** — the receiver should have evidence that data or credentials are tied to the expected key holder.

One mechanism does not automatically provide all three. Encryption can hide content while an unauthenticated mode may still allow undetected modification. A digest creates a fingerprint but, without a secret or trusted reference, does not prove who produced the data. MACs and digital signatures add authenticity through different key models.

The useful habit is to **name the security guarantee first, then choose the primitive and Java API**. Starting from an algorithm name usually reverses the reasoning process.
</details>

- [Back to top](#back-to-top)

---

## <a id="cryptography-role">What Part of Security Does Cryptography Solve?</a>

<details>
<summary>Click for details</summary>

Cryptography turns selected trust assumptions into operations that can be checked with keys, data, algorithms, and parameters. Cipher can transform plaintext into ciphertext, Mac can authenticate data with a shared secret, and Signature can let a private-key holder produce a value that public-key holders verify.

Cryptography does **not** replace the rest of security architecture. It does not decide which user is authorized to call an API, protect a machine that is already compromised, rotate secrets by itself, or decide which certification authorities an organization should trust.

~~~text
security requirement
    ↓
cryptographic goal
    ↓
primitive + key model
    ↓
Java security API
    ↓
key/trust lifecycle + application policy
~~~

This module owns the middle of that chain. Authentication/authorization frameworks, IAM, infrastructure secret lifecycle, and operational PKI belong to their respective owners.
</details>

- [Back to top](#back-to-top)

---

## <a id="threat-model-boundary">Threat Models and Module Boundaries</a>

<details>
<summary>Click for details</summary>

Before choosing a primitive, ask **what the attacker can actually do**. If an attacker can only read a database copy, confidentiality may be the primary goal. If the attacker can also modify traffic, integrity and authenticity become relevant. If the endpoint itself is compromised and the attacker can read keys from the process, encrypting data inside that same process cannot recreate the lost trust boundary.

A minimal threat model should identify:

1. the assets that need protection;
2. where the data exists — memory, file, database, or network;
3. who is allowed to read or produce it;
4. whether the attacker can read, modify, replay, or impersonate;
5. which keys or certificates are treated as trust anchors.

The module boundary matters as well. Socket and HTTP mechanics belong to Networking. CA operations, HSM administration, IAM, and secret rotation are infrastructure concerns. Here we learn enough to use Java key, certificate, trust, and cryptographic APIs correctly.
</details>

- [Back to top](#back-to-top)

---

## <a id="primitive-by-goal">Choose Cryptographic Mechanisms from Security Goals</a>

<details>
<summary>Click for details</summary>

Use this table as an orientation map, not as a fixed algorithm prescription:

| Goal | Typical primitive | Main Java API |
| --- | --- | --- |
| fingerprint / compare with trusted reference | cryptographic hash | MessageDigest |
| integrity + shared-secret authenticity | MAC | Mac |
| authenticity with asymmetric keys | digital signature | Signature |
| confidentiality | encryption | Cipher |
| confidentiality + tamper detection | AEAD | Cipher with an AEAD mode |
| establish shared secret | key agreement / KEM | KeyAgreement / KEM |
| secure transport channel | TLS | JSSE |

Real systems often combine several rows. TLS, for example, uses certificate/trust handling to authenticate peers, a key-establishment mechanism to create session secrets, and symmetric authenticated protection for application traffic.

The module follows those dependencies deliberately:

~~~text
security goals + representation/primitive distinctions
        ↓
JCA/JCE + Providers
        ↓
SecureRandom + key material
        ↓
digest → MAC → digital signature
        ↓
symmetric/asymmetric encryption → AEAD
        ↓
key establishment → key derivation
        ↓
KeyStore → certification-path trust
        ↓
JSSE/TLS
        ↓
end-to-end design + failure review
~~~

Choose by security goal, trust model, protocol contract, and policy. “Strong algorithm” is not a substitute for a correct design.
</details>

- [Back to top](#back-to-top)
