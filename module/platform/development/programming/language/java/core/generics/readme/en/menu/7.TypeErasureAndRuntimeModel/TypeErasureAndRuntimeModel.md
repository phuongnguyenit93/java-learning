# Type Erasure and Runtime Metadata

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
type variables such as T erase to their bound or Object
parameterized types such as List<String> erase to List
        ↓
compiler inserts casts where needed
        ↓
bytecode operates on ordinary runtime types
```

Keep two erasure rules distinct:

```text
T
→ erases to its leftmost bound when one exists
→ otherwise commonly erases to Object

List<String>
→ the String type argument is absent from the execution type
→ the parameterized type erases to List
```

So “erases to `Object`” is **not** a rule for every generic type. It mainly describes an unbounded type variable; a parameterized type such as `List<String>` erases to the corresponding raw type `List`.

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

With multiple bounds, the same rule uses the **first bound**:

```java
<T extends Number & Comparable<T>>
```

so the erasure of `T` is `Number`. This is the deeper reason the Bounded Type Parameters chapter requires a class bound, when present, to come before interface bounds.

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

On a first pass, keep two ideas separate:

- erasure means runtime objects do not carry concrete type arguments in a form that supports arbitrary element-type tests;
- class files can retain **generic signature metadata** for declarations; the Reflection module later shows how to inspect that metadata.

## <a id="generic-signature-metadata">Generic Signature Metadata Retained after Erasure</a>

Type erasure does not mean **every trace of generics disappears from the class file**.

For example, a field may be declared as:

```java
List<String> names;
```

Executable bytecode primarily works with the erased `List` type, while the class file can also retain a generic signature describing `List<String>` for compilers, Reflection, and tooling.

Keep these layers distinct:

```text
runtime execution type
→ primarily the erased type

generic signature metadata
→ describes declaration type parameters / type arguments
→ may remain in the class file
```

Reflection exposes abstractions such as `ParameterizedType`, `TypeVariable`, and `WildcardType` for reading this metadata. Reflection mechanics belong to the Reflection module; the key model here is that **erased execution and retained generic metadata can coexist**.

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

Reifiability matters for operations that depend on runtime type information, especially array creation and runtime type checks. For casts, a **non-reifiable target can produce an unchecked cast precisely because the JVM cannot verify the full generic target**. Generic varargs can create a similar boundary where the compiler cannot prove the complete type relationship and heap pollution may result.

### Advanced note — parameterized `instanceof` since Java 16

Do not memorize the overly broad rule that “a parameterized type can never appear after `instanceof`.” This is legal:

```java
List<Integer> values = new ArrayList<>();

if (values instanceof ArrayList<Integer>) {
    // legal
}
```

This does **not** mean the JVM inspected every element to verify `Integer`. The static type `List<Integer>` already carries the `Integer` constraint; the `instanceof` test is refining the runtime class from `List` to `ArrayList`.

Since Java 16, `instanceof` can use some parameterized types when a checked casting conversion exists from the expression's static type to the tested type. Therefore, `non-reifiable` does **not** mean “always forbidden after `instanceof`.”

An unchecked cast may compile even when runtime cannot verify the concrete type argument completely:

```java
List<String> names = (List<String>) value;
```

Treat such a cast as a **trust boundary**, not proof that the runtime checked every generic argument.

Erasure explains many restrictions that otherwise look unrelated. The final chapter groups the major restrictions and shows safer design alternatives.
