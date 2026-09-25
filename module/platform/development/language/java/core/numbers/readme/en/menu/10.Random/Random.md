# Random Number Generation

## <a id="pseudo-random-model">Pseudo-random model and seed</a>
A pseudo-random generator produces a deterministic sequence from internal state. Equal algorithms and seeds can reproduce the same sequence, which is useful for tests/simulations. Random-looking output does not imply unpredictability against an attacker.

## <a id="threadlocal-random-boundary">Random vs ThreadLocalRandom boundary</a>
`Random` is a general stateful PRNG. `ThreadLocalRandom` avoids sharing one generator state across threads and is useful inside concurrent code, but its full concurrency behavior belongs to the concurrency curriculum. Neither should be selected for security merely because the sequence looks random.

## <a id="random-not-security">Why ordinary PRNG is not security</a>
Security tokens, keys, salts, and nonces require a cryptographically strong source. A predictable seed or state can reveal future outputs. Use `SecureRandom` for security-sensitive randomness and let the security-cryptography module own the deeper threat model.
