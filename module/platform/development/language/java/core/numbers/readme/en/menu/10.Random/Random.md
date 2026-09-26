# Random

`Random` does not produce “absolute randomness”. It produces a **pseudo-random** sequence from internal state/seed according to a deterministic algorithm.

## <a id="pseudo-random-model">Pseudo-randomness and Seed</a>

The same seed and call sequence can reproduce the same outputs:

```java
Random a = new Random(42);
Random b = new Random(42);
```

That determinism is useful for tests, simulations, and reproducible experiments.

A PRNG seed is not automatically a security secret; ordinary pseudo-random output may be predictable if state or algorithm behavior is inferred.

## <a id="threadlocal-random-boundary">Random vs ThreadLocalRandom</a>

`ThreadLocalRandom` is designed for concurrent code to reduce contention when many threads need pseudo-random values.

It is suitable for simulations, randomized scheduling, or sampling with no cryptographic requirement.

Choose it for concurrency/performance reasons, not merely because the name contains “thread”.

## <a id="random-not-security">Not for Security</a>

Security tokens, session identifiers, nonces, secrets, and key material need unpredictability stronger than ordinary PRNGs provide.

`Random` and `ThreadLocalRandom` are **not cryptographic random sources**.

The next chapter uses `SecureRandom` when unpredictability is part of the contract.
