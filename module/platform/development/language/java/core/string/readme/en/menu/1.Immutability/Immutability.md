# String and Immutability

`String` is Java's central text type, but text is more complicated than “an array of chars”. We need to distinguish the String value inside the JVM, the bytes used at external boundaries, and Unicode's model of characters.

**Immutability** is the foundation connecting these topics. Once a `String` object is created, its content does not change in place. That makes sharing, pooling, hashing, and API boundaries much easier to reason about.

Roadmap:

```text
Can a String value change in place?
Immutability
        ↓
Why can equal literals sometimes share identity?
String Pool
        ↓
How should text values be compared?
Equality
        ↓
What happens when Strings are combined repeatedly?
Concatenation
        ↓
What if we need a mutable construction buffer?
StringBuilder → StringBuffer
        ↓
What does String.intern canonicalize?
Intern
        ↓
How does text become bytes and bytes become text?
Encoding / Charset
        ↓
Why is char not always one user-visible character?
Unicode / Code Point / Grapheme
        ↓
How do we describe text patterns?
Regex
        ↓
How do we write multiline String literals cleanly?
Text Blocks
```

## <a id="string-immutability">Why Is String Immutable?</a>

After a `String` object is created, operations do not mutate its character sequence in place.

```java
String s = "java";
s.toUpperCase();
System.out.println(s); // java
```

`toUpperCase()` returns another String value when content must change.

Immutability enables safe sharing, stable hashing, literal pooling, and simpler aliasing rules.

## <a id="immutability-consequences">Consequences of Immutability</a>

String immutability does not make every object containing a String thread-safe. It guarantees only that the String value itself cannot be mutated.

```text
immutable String
→ sharing the same String object is usually safe

mutable List<String>
→ the collection can still change
```

Stable content is also why String works naturally as a `HashMap` key.

## <a id="string-operation-new-value">String Operations Return New Values</a>

Use the returned result when an operation transforms text:

```java
String s = " java ";
s.trim();       // result ignored
s = s.trim();   // s now refers to "java"
```

The variable can be reassigned even though each String object is immutable.

The next chapter uses immutability to explain safe literal sharing in the String pool.
