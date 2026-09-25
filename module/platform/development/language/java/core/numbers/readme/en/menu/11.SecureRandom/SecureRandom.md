# SecureRandom and Security Boundaries

## <a id="secure-random-purpose">SecureRandom purpose</a>
`SecureRandom` is the JDK abstraction for cryptographically strong random bytes/numbers. It is intended for security-sensitive values where an attacker must not feasibly predict future output from observed values.

## <a id="entropy-seeding">Entropy and seeding mental model</a>
A secure generator needs suitable entropy to initialize or reseed its internal state. Applications normally should let the provider seed `SecureRandom` automatically instead of supplying low-entropy timestamps, counters, or hard-coded seeds. Provider/OS details can affect startup/blocking behavior.

## <a id="security-boundary">Security usage boundary and handoff to cryptography module</a>
This module only establishes the randomness boundary: choose `SecureRandom` when unpredictability is a security property. Key generation, IV/nonce requirements, salts, algorithms, providers, signatures, and encryption belong to `java/advance/security-cryptography`, where the exact cryptographic contract matters.
