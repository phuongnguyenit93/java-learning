# SecureRandom

When randomness is part of a security contract, "looks random" is not enough. The important property is that an attacker should have difficulty predicting internal state and future output.

`SecureRandom` provides a cryptographically strong random source for appropriate use cases.

## <a id="secure-random-purpose">Purpose of SecureRandom</a>

Typical uses include:

- security tokens;
- nonces when a protocol requires a random nonce;
- salts;
- secret material;
- key-generation input when the surrounding API/protocol requires a random source.

```java
SecureRandom secureRandom = new SecureRandom();

byte[] bytes = new byte[32];
secureRandom.nextBytes(bytes);
```

### WHY not use SecureRandom for everything?

Cryptographic guarantees come with different initialization, provider, and performance characteristics from ordinary PRNGs.

For simulations or deterministic tests:

```text
reproducibility
→ often valuable

cryptographic unpredictability
→ not part of the requirement
```

An ordinary pseudo-random generator may therefore be the better tool.

## <a id="entropy-seeding">Entropy and Seeding</a>

Mental model:

```text
entropy source
    ↓
hard-to-predict seed / state
    ↓
cryptographic PRNG
    ↓
hard-to-predict output
```

### Default application rule

For ordinary application code:

```java
SecureRandom secureRandom = new SecureRandom();
```

and allowing the platform/provider to manage seeding is usually safer than inventing a seed manually.

### Pitfall: weak manual seeding

```java
SecureRandom secureRandom = new SecureRandom();
secureRandom.setSeed(System.currentTimeMillis()); // do not rely on a timestamp as primary entropy
```

An important nuance is that `setSeed` **supplements** existing seed/state, so repeated calls do not by themselves reduce the randomness of an instance that was already seeded well. However, for a newly created PRNG `SecureRandom`, calling `setSeed` **before the first `nextBytes`/`reseed` call** prevents automatic self-seeding; the caller must then ensure that the supplied seed has enough entropy.

Therefore a timestamp or output from ordinary `Random` should not be treated as the primary entropy source for a security-sensitive generator.

Do not reason:

```text
SecureRandom class
→ every manually chosen seed is automatically secure
```

The security property depends on the complete entropy/state lifecycle.

### `getInstanceStrong` is not a universal default

`SecureRandom.getInstanceStrong()` may choose a provider/algorithm with stronger platform-specific characteristics, but it may also have different latency, blocking, or availability behavior.

```text
default SecureRandom
→ usually appropriate for application use cases

getInstanceStrong()
→ use when a concrete requirement needs that contract
```

## <a id="security-boundary">Boundary to Security/Cryptography</a>

The Numbers module only needs this distinction:

```text
Random
→ deterministic pseudo-random contract
→ tests / simulations / general randomness

SecureRandom
→ attacker-oriented unpredictability contract
→ security-sensitive randomness
```

Deeper topics such as key-size selection, cipher modes, nonce uniqueness, IV construction, provider configuration, entropy-source internals, and cryptographic protocol design belong in the security/cryptography module.

Using `SecureRandom` alone is not enough to design cryptography safely. It solves the **random-source** part of a larger protocol contract.

After this module, the key question for any numeric value is:

> Which representation and policy match the problem contract: range, exactness, decimal semantics, rounding, or unpredictability?
