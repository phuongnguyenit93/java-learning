# Type Erasure

By this point the compiler seems to know a lot:

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

But ask the runtime:

```java
System.out.println(texts.getClass() == numbers.getClass()); // true
```

If `String` and `Integer` matter to generics, **why does runtime see the same `ArrayList` class?**

The key is that Java generics are checked primarily at **compile time**. The compiler uses type arguments to validate source code and then translates generic declarations/calls into bytecode compatible with the older JVM class model.

That translation mechanism is called **type erasure**.

Understanding erasure connects several otherwise surprising facts:

- `List<String>` and `List<Integer>` share the same runtime class;
- an arbitrary broad reference such as `Object` cannot simply be tested as `List<String>`;
- the compiler may insert casts;
- bridge methods can appear;
- several restrictions in the next chapter exist.

## <a id="erasure-model">Type Erasure Mental Model</a>

A useful conceptual model:

```text
source contains T / List<String> / bounds
        ↓
compiler checks generic constraints
        ↓
type parameters erase to their bound or Object
        ↓
compiler inserts casts where needed
        ↓
bytecode operates on ordinary runtime types
```

For:

```java
class Box<T> {
    T value;
    T get() { return value; }
}
```

Conceptually, an unbounded `T` erases toward:

```java
class Box {
    Object value;
    Object get() { return value; }
}
```

A typed caller such as:

```java
String s = box.get();
```

is protected by compile-time checking plus the casts the compiler emits where necessary.

If `T extends Number`, the erasure of `T` is based on its leftmost bound `Number`, not always `Object`.

## <a id="erased-runtime-type">Runtime Type after Erasure</a>

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

System.out.println(texts.getClass() == numbers.getClass()); // true
```

Both objects are instances of the same runtime `ArrayList` class. The JVM does not create separate `ArrayList<String>` and `ArrayList<Integer>` runtime classes.

So runtime cannot take an arbitrary reference and ask whether the object is truly a `List<String>`:

```java
Object value = new ArrayList<String>();
// if (value instanceof List<String>) { } // compile error
```

However, since Java 16 it is too broad to memorize “a parameterized type can never appear after `instanceof`.” This is legal:

```java
List<Integer> values = new ArrayList<>();

if (values instanceof ArrayList<Integer>) {
    // legal
}
```

This does **not** mean the JVM inspected every element to verify `Integer`. The static type `List<Integer>` already carries the `Integer` constraint; the `instanceof` test is refining the runtime class from `List` to `ArrayList`.

Keep three ideas separate:

- erasure means runtime objects do not carry concrete type arguments in a form that supports arbitrary element-type tests;
- Java 16+ allows some parameterized `instanceof` checks when the static type and casting conversion make the test valid;
- class files can retain **generic signature metadata** for declarations; the Reflection module later shows how to inspect that metadata.

## <a id="bridge-method">Bridge Methods</a>

This is an **advanced compiler-implementation section**. You do not need to memorize bridge generation to use everyday generics; it matters when reading bytecode/Reflection or explaining why polymorphism still works after erasure.

Erasure can make an overriding method's runtime signature differ from the erased generic contract. The compiler may generate a **synthetic bridge method** to preserve polymorphism.

```java
interface Mapper<T> {
    T map(T value);
}

class StringMapper implements Mapper<String> {
    @Override
    public String map(String value) {
        return value.trim();
    }
}
```

After erasure, the interface contract is conceptually close to:

```java
Object map(Object value)
```

while the real implementation is:

```java
String map(String value)
```

The compiler can add a bridge conceptually like:

```java
public Object map(Object value) {
    return map((String) value);
}
```

Reflection can observe this synthetic/bridge method. It is not a method the developer explicitly wrote.

## <a id="non-reifiable-types">Reifiable and Non-Reifiable Types</a>

This is also an **advanced runtime section**. On a first pass, keep the practical rule: runtime cannot always verify a concrete generic argument, which is why generic arrays, casts, and some runtime checks have restrictions.

A **reifiable type** has enough runtime representation for operations that require runtime type checking.

Examples of reifiable types:

```text
String
List
List<?>
int
String[]
List<?>[]
```

Examples of non-reifiable types:

```text
List<String>
List<Integer>
T
List<String>[]
```

Reifiability still matters where runtime genuinely needs a fully represented type, especially array creation, unchecked casts, and other runtime boundaries. For `instanceof`, Java 16+ also considers **checked cast compatibility**, so do not turn “non-reifiable” into the blanket rule that parameterized types are always forbidden.

An unchecked cast may compile even when runtime cannot verify the concrete type argument completely:

```java
List<String> names = (List<String>) value;
```

Treat such a cast as a **trust boundary**, not proof that the runtime checked every generic argument.

Erasure explains many restrictions that otherwise look unrelated. The final chapter groups the major restrictions and shows safer design alternatives.
