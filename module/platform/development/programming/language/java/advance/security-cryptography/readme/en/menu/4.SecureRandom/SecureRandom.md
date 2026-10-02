<a id="back-to-top"></a>

# Secure Randomness with SecureRandom

## Menu
- [Why Cryptography Needs Unpredictable Randomness](#cryptographic-randomness)
- [Entropy and Seeding](#entropy-and-seeding)
- [Selecting and Using SecureRandom](#secure-random-selection)
- [Common Cryptographic Randomness Mistakes](#randomness-misuse)

## <a id="cryptographic-randomness">Why Cryptography Needs Unpredictable Randomness</a>

<details>
<summary>Click for details</summary>

Many cryptographic operations depend on values that must be **unpredictable**: newly generated keys, salts, challenges, security tokens, and random nonces for designs that require random nonces.

java.util.Random may be appropriate for simulation or ordinary application behavior, but it does not provide the cryptographic contract. SecureRandom is designed as a cryptographically strong random-number generator.

~~~java
SecureRandom random = new SecureRandom();
byte[] bytes = new byte[32];
random.nextBytes(bytes);
~~~

The important property is not that the bytes “look random”; an attacker should not be able to predict useful future output from what has already been observed.

When a crypto engine accepts SecureRandom during initialization or generation, use an appropriate secure source rather than substituting a convenience PRNG.
</details>

- [Back to top](#back-to-top)

---

## <a id="entropy-and-seeding">Entropy and Seeding</a>

<details>
<summary>Click for details</summary>

A PRNG commonly expands a **seed** containing entropy into a longer output sequence. If the seed is predictable, the resulting output may become predictable even when the PRNG algorithm itself is sound.

Do not replace system entropy with timestamps, user identifiers, or fixed constants:

~~~java
// Not a production security pattern.
SecureRandom random = new SecureRandom();
random.setSeed(123456789L);
~~~

For a PRNG-based `SecureRandom`, the first output request normally forces self-seeding from the implementation's entropy source. There is an important exception: if `setSeed(...)` is called **before** `nextBytes(...)` or `reseed(...)`, that self-seeding step does not occur and the caller is responsible for providing seed material with enough entropy. Once seeded, later `setSeed(...)` calls supplement the existing seed rather than reducing randomness.

That is why application code should not “seed for safety” with timestamps, user identifiers, or constants. In ordinary application code, letting the JDK/provider handle seeding is safer than inventing predictable seed material.

If tests need reproducibility, build a test seam rather than making the production security path deterministic.
</details>

- [Back to top](#back-to-top)

---

## <a id="secure-random-selection">Selecting and Using SecureRandom</a>

<details>
<summary>Click for details</summary>

For many applications, new SecureRandom() is a reasonable default because the provider architecture chooses an implementation. If policy requires a specific algorithm or provider, SecureRandom.getInstance(...) gives more control.

SecureRandom.getInstanceStrong() selects an implementation configured as strong for the platform, but “strong” does not automatically mean “best for every request path.” Entropy acquisition and latency characteristics may differ.

Practical guidance:

- use SecureRandom, not Random, for secrets;
- reuse an instance where appropriate instead of creating one repeatedly to appear “more random”;
- leave normal seeding to the provider/runtime unless the protocol says otherwise;
- measure behavior if the workload consumes large amounts of random data.
</details>

- [Back to top](#back-to-top)

---

## <a id="randomness-misuse">Common Cryptographic Randomness Mistakes</a>

<details>
<summary>Click for details</summary>

Common mistakes confuse **random-looking** with **security-random**:

- using Random or a predictable seed for reset tokens or keys;
- reusing a nonce in a mode that requires uniqueness;
- using one fixed salt for every password record;
- deriving keys by arbitrarily hashing predictable application data;
- logging raw secrets for debugging.

Good randomness cannot repair a bad primitive choice. A random key does not turn an unauthenticated mode into AEAD, and a random nonce does not replace certificate validation.

During review, ask two separate questions: “must this value be unpredictable?” and “must it be unique?” Protocols may require one, the other, or both.
Secure randomness commonly feeds key, nonce, and seed generation, so the next step is to distinguish key types and key-material lifecycles.

</details>

- [Back to top](#back-to-top)
