# Random

`Random` does not produce "absolute randomness". It produces a **pseudo-random** sequence from internal state/seed using a deterministic algorithm.

The key distinction is:

```text
useful statistical randomness
versus
security unpredictability
```

Those are different contracts.

## <a id="pseudo-random-model">Pseudo-randomness and Seed</a>

### WHAT

A pseudo-random generator can be viewed as:

```text
seed / internal state
        ↓
deterministic algorithm
        ↓
sequence of values
```

The same seed and the same call sequence can reproduce the same outputs:

```java
Random a = new Random(42);
Random b = new Random(42);

System.out.println(a.nextInt(1000));
System.out.println(b.nextInt(1000));
```

### WHY is determinism useful?

For tests and simulations:

```text
random-looking data
        +
reproducible seed
        =
reproducible failures
```

A fixed seed makes it possible to reproduce the same sequence during debugging.

### Bounded random values

```java
int value = random.nextInt(100);
```

produces:

```text
0 <= value < 100
```

Prefer bounded APIs over manually applying modulo:

```java
random.nextInt() % 100
```

because manual modulo can produce negative results and distribution problems when handled incorrectly.

## <a id="threadlocal-random-boundary">Random vs ThreadLocalRandom</a>

`ThreadLocalRandom` is designed for concurrent code to reduce contention when many threads need pseudo-random values.

```java
int value =
        ThreadLocalRandom.current()
                .nextInt(10, 20);
```

It is suitable for simulations, randomized scheduling, sampling, and non-security load-balancing heuristics.

The right questions are:

```text
does the use case have concurrent contention?
and
does the randomness require security unpredictability?
```

The repository uses a Java 21 baseline, where the modern `java.util.random.RandomGenerator` API and multiple generator implementations are available. The important boundary is that **algorithm choice, reproducibility, and concurrency model are separate concerns**; choosing among individual algorithms belongs at a deeper level.

`Math.random()` is also an ordinary pseudo-random convenience API; it does not make the result cryptographically secure.

## <a id="random-not-security">Not for Security</a>

`Random` and `ThreadLocalRandom` are not designed to resist an attacker predicting state or future output.

Do not use them for:

- password-reset tokens;
- session secrets;
- authentication tokens;
- cryptographic nonces;
- key material;
- security-sensitive identifiers where unpredictability is required.

### A seed is not a password

```java
new Random(System.currentTimeMillis())
```

does not make the generator secure. A timestamp has very different predictability and search space from strong entropy.

If an attacker must not be able to predict the next output, move to `SecureRandom`.
