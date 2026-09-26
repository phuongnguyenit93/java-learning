# SecureRandom

When randomness is part of a security contract, “looks random” is not enough. The important property is that state/output should be **difficult for an attacker to predict**.

## <a id="secure-random-purpose">Purpose of SecureRandom</a>

`SecureRandom` provides cryptographically strong random values suitable for cases such as security tokens, nonces, salts, and secret/key-generation inputs when the surrounding API/protocol calls for them.

It has different cost and initialization characteristics from `Random`, so ordinary simulations or non-security game mechanics do not automatically need it.

## <a id="entropy-seeding">Entropy and Seeding</a>

A useful mental model is:

```text
good entropy
→ hard-to-predict seed/state
        ↓
cryptographic generator
→ hard-to-predict output
```

Do not weaken the design by manually seeding from timestamps or ordinary `Random` output unless a protocol explicitly requires controlled seeding.

In most application code, platform/provider seeding is the safer default.

## <a id="security-boundary">Boundary to Security/Cryptography</a>

The Numbers module only needs the distinction between the `Random` and `SecureRandom` contracts.

Topics such as key generation, cipher/nonce requirements, providers, entropy sources, and cryptographic protocols belong in the security/cryptography module.

The final question after this module should be:

> Which representation and policy match the problem's contract: range, exactness, decimal semantics, rounding, or unpredictability?
