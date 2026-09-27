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
Which APIs inspect, search, extract, and transform String values?
Core String Operations
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

### WHAT — what is immutable?

Separate three ideas that are often casually called “the String”:

```text
String variable
→ stores a reference

String object
→ immutable object referenced by the variable

String value
→ the text content represented by that object
```

Immutability applies to the object/value. It does not mean a variable holding a String reference can never be reassigned.

```java
String s = "java";
s.toUpperCase();
System.out.println(s); // java
```

`toUpperCase()` returns another String value when content must change.

```text
s ─────────────► "java"

s = s.toUpperCase()

s ─────────────► "JAVA"
```

The variable now refers to the result; the object representing `"java"` was not mutated into `"JAVA"`.

Immutability enables safe sharing, stable hashing, literal pooling, and simpler aliasing rules.

Those benefits connect directly to the rest of the module:

```text
immutable
→ safe sharing
→ pooling / intern can share identity

immutable
→ stable content
→ equals/hashCode remain stable

immutable
→ repeated construction does not modify an old String
→ concatenation and StringBuilder matter
```

### HOW — how is the contract exposed?

`String` has no public API that changes one code unit in place. Text transformations return a `String` result.

Do not build the mental model around an assumed internal field such as `char[]`. The JVM implementation has changed across Java versions. Application code should depend on this contract:

```text
current String
        │
        ├── inspect/search
        │      → value unchanged
        │
        └── transform
               → receive a String result
```

### Copy boundaries with mutable char[]

Immutability must also hold when a String is created from externally mutable data:

```java
char[] chars = {'j', 'a', 'v', 'a'};
String text = new String(chars);

chars[0] = 'J';

System.out.println(text); // java
System.out.println(chars); // Java
```

Mutating the source array after construction does not change the String. If String kept a mutable alias to the caller's array, the immutability contract would be broken.

The reverse direction also returns an independent mutable copy:

```java
String text = "java";
char[] copy = text.toCharArray();

copy[0] = 'J';

System.out.println(text);             // java
System.out.println(new String(copy)); // Java
```

Mental model:

```text
mutable char[] input
        ↓ copy boundary
immutable String value
        ↓ copy boundary
mutable char[] output
```

The important contract is that application code does not receive a mutable-array alias that can modify an existing String's content. String's actual internal representation remains a JVM implementation detail.

## <a id="immutability-consequences">Consequences of Immutability</a>

String immutability does not make every object containing a String thread-safe. It guarantees only that the String value itself cannot be mutated.

```text
immutable String
→ sharing the same String object is usually safe

mutable List<String>
→ the collection can still change
```

Stable content is also why String works naturally as a `HashMap` key.

But an immutable String does not make its container immutable:

```java
List<String> names = new ArrayList<>();
names.add("Ada");
```

```text
immutable String
→ element text does not mutate

mutable List<String>
→ list can still add/remove/reorder
```

### Immutable is not the same as final

```java
String a = "java";
a = "kotlin"; // valid

final String b = "java";
// b = "kotlin"; // compile error
```

```text
immutable object
→ object state does not change

final variable
→ variable cannot be reassigned after initialization
```

## <a id="string-operation-new-value">String Operations Return New Values</a>

Use the returned result when an operation transforms text:

```java
String s = " java ";
s.trim();       // result ignored
s = s.trim();   // s now refers to "java"
```

The variable can be reassigned even though each String object is immutable.

### A result value does not guarantee a distinct new object

Application code should not assume every String method must allocate another object. A method may return the same instance when no content change is required, and the compiler/runtime may optimize construction.

Depend on:

```text
the old String content is not mutated
+
the method returns a String representing the result
```

Do not use identity to validate text:

```java
result == s      // identity question
result.equals(s) // content question
```

The next chapter uses immutability to explain safe literal sharing in the String pool.
