<a id="back-to-top"></a>

# Cryptographic Hashing with MessageDigest

## Menu
- [Cryptographic Digest Model](#digest-model)
- [MessageDigest Workflow](#message-digest-workflow)
- [Digests and Trusted Reference Values](#trusted-digest-reference)
- [Limits of Unkeyed Digests](#digest-limitations)

## <a id="digest-model">Cryptographic Digest Model</a>

<details>
<summary>Click for details</summary>

MessageDigest represents a cryptographic hash function. It accepts arbitrary input and produces a digest whose length is determined by the algorithm.

~~~text
bytes
  ↓ hash
fixed-size digest
~~~

A digest has **no secret key**. Any party with the same input and algorithm can compute it. That makes hashes useful for fingerprints and trusted comparisons, but it also means a digest by itself does not prove who produced the data.

A suitable hash algorithm should provide the required preimage and collision-resistance properties for the use case. Do not treat the word “hash” as a permanent guarantee; security policy evolves and older algorithms may be restricted.
</details>

- [Back to top](#back-to-top)

---

## <a id="message-digest-workflow">MessageDigest Workflow</a>

<details>
<summary>Click for details</summary>

A MessageDigest workflow feeds bytes into the engine and finishes with digest():

~~~java
MessageDigest md = MessageDigest.getInstance("SHA-256");
md.update(headerBytes);
md.update(payloadBytes);
byte[] digest = md.digest();
~~~

For a simple buffer, digest(byte[]) is convenient; update calls are useful for streamed or chunked data.

After digest(), the engine is reset to its initial state according to the MessageDigest contract. Even so, a mutable instance should not be casually shared across concurrent requests.

When hashing text, always choose the byte encoding explicitly, such as StandardCharsets.UTF_8. Two logically similar strings encoded differently produce different byte sequences and therefore different digests.
</details>

- [Back to top](#back-to-top)

---

## <a id="trusted-digest-reference">Digests and Trusted Reference Values</a>

<details>
<summary>Click for details</summary>

A digest detects meaningful modification only when the verifier has a **trusted reference value**.

For example, a repository may publish a SHA-256 digest through an authenticated channel. A client downloads the file from a mirror and compares its locally computed digest with that trusted value.

If an attacker can replace both the file and the stored reference:

~~~text
modified file
    +
digest(modified file)
~~~

the comparison still succeeds. The hash has not created authenticity.

For security-sensitive byte comparison, MessageDigest.isEqual(...) provides a digest-oriented comparison API instead of hand-writing a loop that exits on the first unequal byte.
</details>

- [Back to top](#back-to-top)

---

## <a id="digest-limitations">Limits of Unkeyed Digests</a>

<details>
<summary>Click for details</summary>

Three recurring mistakes are:

1. **digest = encryption** — false because a hash has no decrypt operation;
2. **digest = authentication** — false unless the reference itself is trusted;
3. **one fast hash = password protection** — insufficient because human passwords are low-entropy and candidates can be tested cheaply.

Use a MAC when shared-secret authenticity is required. Use a digital signature when public verification is required. Use password-based derivation for password-derived key material.

MessageDigest remains a foundational primitive; the problem is assigning it guarantees that it does not provide.
The central limitation of a digest is the absence of a secret that authenticates the producer; that limitation motivates the move to MACs.

</details>

- [Back to top](#back-to-top)
