# PECS

Once `? extends T` and `? super T` are available, the practical problem becomes:

```text
"When should I use extends?
When should I use super?
Why?"
```

Consider a copy operation:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

`source` is where the method **reads T from**. `target` is where the method **writes T into**.

**PECS — Producer Extends, Consumer Super** — is a mnemonic for that direction:

```text
source PRODUCES values we read  → ? extends T
target CONSUMES values we write → ? super T
```

PECS is not a new Java language feature; it is a heuristic for understanding and designing wildcard signatures.

## <a id="pecs-rule">The PECS Rule</a>

Ask what a parameter does with values of type `T`:

```text
parameter mainly PRODUCES T for the method to read
→ ? extends T

parameter mainly CONSUMES T written by the method
→ ? super T

parameter must both read exact T and write T
→ often use the exact type rather than a wildcard
```

Classic copy shape:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

## <a id="read-from-producer">Reading from a Producer — extends</a>

`List<? extends Animal>` may actually be:

```java
List<Animal>
List<Dog>
List<Cat>
```

The method can safely read:

```java
Animal animal = source.get(0);
```

Every possible list produces at least an `Animal`.

But it cannot safely write a `Dog`:

```java
// source.add(new Dog()); // compile error
```

The list might really be a `List<Cat>`.

“extends means read-only” is only a shorthand. Operations independent of element type, such as `clear()`, can still be legal. The important restriction is writing a **specific element value** when the exact captured type is unknown.

## <a id="write-to-consumer">Writing to a Consumer — super</a>

`List<? super Dog>` may be:

```java
List<Dog>
List<Animal>
List<Object>
```

Writing a `Dog` is safe:

```java
target.add(new Dog());
```

When reading, only this is guaranteed:

```java
Object value = target.get(0);
```

The actual list may contain other values when its true element type is `Animal` or `Object`.

## <a id="pecs-api-design">Applying PECS to API Design</a>

A narrow signature:

```java
static void moveDogs(List<Dog> source, List<Dog> target)
```

works only when both sides are exactly `List<Dog>`.

A more flexible signature:

```java
static void moveDogs(
        List<? extends Dog> source,
        List<? super Dog> target)
```

allows a source of a `Dog` subtype and a destination whose element type is `Dog` or a supertype.

The JDK uses this shape broadly; APIs such as `Collections.copy` model the source as a producer and the destination as a consumer.

Do not apply PECS mechanically:

- exact `List<T>` may be clearer when the parameter must both read and write the same `T`;
- a wildcard that adds no real flexibility only adds cognitive cost;
- a named type parameter is appropriate when the return type must relate directly to input types.

In particular, **avoid wildcard return types in public APIs** when a more specific return type can express the contract. A return such as `List<? extends Animal>` forces callers to keep carrying and reasoning about the unknown captured type. Wildcards are often most useful at **input boundaries**, where an API wants to accept a broader family of valid parameterized types.

PECS explains how to regain flexibility. The deeper question is **why wildcards are needed at all instead of making `List<Dog>` a subtype of `List<Animal>`**. The next chapter answers with invariance.
