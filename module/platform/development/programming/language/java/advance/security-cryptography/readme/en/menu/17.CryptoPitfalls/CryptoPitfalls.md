<a id="back-to-top"></a>

# Safe Cryptographic Design and Common Pitfalls

## Menu
- [Start from Security Goals, Not APIs](#security-goal-before-api)
- [Portable Algorithm and Provider Selection](#algorithm-provider-portability)
- [Handling Secrets, Keys, IVs, and Nonces](#secret-key-nonce-handling)
- [Handle Failures Without Weakening Security](#crypto-failure-handling)
- [Review Cryptographic Designs End to End](#end-to-end-crypto-review)

## <a id="security-goal-before-api">Start from Security Goals, Not APIs</a>

<details>
<summary>Click for details</summary>

A common anti-pattern is asking “AES or RSA?” before deciding what needs protection.

A better review flow is:

~~~text
asset + attacker capability
        ↓
security goal
        ↓
trust/key model
        ↓
standard protocol/primitive
        ↓
Java API + provider
        ↓
parameters + lifecycle + failure policy
~~~

If the real need is only a fingerprint for deduplication, encryption is the wrong abstraction. If a confidential record also needs tamper detection, authenticated encryption is clearer than combining unrelated operations by intuition.

The API choice should be the result of the design, not the beginning of it.
</details>

- [Back to top](#back-to-top)

---

## <a id="algorithm-provider-portability">Portable Algorithm and Provider Selection</a>

<details>
<summary>Click for details</summary>

Algorithm and provider selection must balance current security policy with portability.

Prefer to:

- use standard algorithm names where possible;
- avoid provider pinning without a requirement;
- validate required algorithms during bootstrap;
- version persistent/protocol formats so algorithm or parameter changes can be represented later;
- allow JDK constraints to reject weak choices rather than silently downgrading.

Ciphertext that outlives the process also needs enough metadata to recover the intended transformation, nonce, version, and KDF parameters.

“Works on my JDK” is not a portability guarantee. Provider inventories and policy can differ across operating systems, JDK vendors, HSM deployments, and future JDK versions.
</details>

- [Back to top](#back-to-top)

---

## <a id="secret-key-nonce-handling">Handling Secrets, Keys, IVs, and Nonces</a>

<details>
<summary>Click for details</summary>

Many failures happen in **material lifecycle**, not inside the primitive:

- secrets hard-coded in source;
- keys leaked through logs or serialization;
- passwords kept in immutable strings longer than necessary;
- AEAD nonces reused under the same key;
- one fixed salt used for every credential;
- one key reused across purposes without a protocol design that permits it.

Classify data explicitly:

~~~text
secret: private key, symmetric key, password
non-secret but security-relevant: certificate, salt, IV/nonce
public protocol metadata: algorithm/version identifiers
~~~

“Public” does not mean “unimportant.” A nonce or salt can be public while still having strict uniqueness/randomness requirements.
</details>

- [Back to top](#back-to-top)

---

## <a id="crypto-failure-handling">Handle Failures Without Weakening Security</a>

<details>
<summary>Click for details</summary>

Security failures should not trigger a fallback that weakens the guarantee.

Reject or surface failures such as:

- AEAD tag verification failure;
- invalid MAC/signature;
- certificate-path or endpoint-identity failure;
- algorithms/keys blocked by policy;
- missing required provider services.

Do not retry with weaker padding, trust-all managers, disabled hostname verification, or legacy protocols simply to make the operation succeed.

Logs should contain useful non-secret context — algorithm, alias, certificate subject/fingerprint when appropriate, record identifier — but not passwords, private keys, raw secret keys, or plaintext secrets.

Fail closed can still be observable and well structured; it does not require chaotic crashes.
</details>

- [Back to top](#back-to-top)

---

## <a id="end-to-end-crypto-review">Review Cryptographic Designs End to End</a>

<details>
<summary>Click for details</summary>

An end-to-end review should follow the entire lifecycle instead of inspecting only one Cipher.getInstance call:

1. **asset/threat** — reading, modification, impersonation, replay?
2. **primitive/protocol** — is there an established mechanism?
3. **key origin** — generate, derive, KeyAgreement, KEM, or KeyStore?
4. **parameters** — nonce, salt, work factor, transformation, protocol version?
5. **trust** — how is a public key/certificate authenticated?
6. **storage/transport** — which metadata travels with the protected value?
7. **failure** — what happens on tag/signature/trust failure?
8. **rotation/migration** — how will keys and algorithms change later?

An unanswered item is often a real design gap even when the code compiles and happy-path tests pass.

The module's goal is not memorizing every class; it is learning the reasoning chain from a security goal to the Java mechanism and its production boundary.
</details>

- [Back to top](#back-to-top)
