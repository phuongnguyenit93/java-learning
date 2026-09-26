# Primitive and Reference Values

Before learning `if`, methods, arrays, or OOP, answer one foundational question: **what kind of value does a Java variable actually hold?**

The first mental split is:

```text
primitive value
→ the numeric/boolean/char primitive value itself

reference value
→ a value identifying an object/array, or null
```

That distinction appears throughout Java Core: assignment, boxing, casting, method calls, pass-by-value, arrays, `null`, equality, and runtime types.

Roadmap:

```text
What kinds of values do Java variables hold?
Primitive vs Reference
        ↓
Where are names visible and how long do variables live?
Variables & Scope
        ↓
How do primitives enter object/generic APIs?
Wrapper & Boxing
        ↓
How are values combined and converted?
Operators → Casting
        ↓
How does execution choose a path?
Control Flow
        ↓
How is reusable behavior named and invoked?
Methods → Varargs → Pass-by-Value
        ↓
How are fixed-size sequences represented?
Arrays
        ↓
How are type names organized?
Packages & Imports
        ↓
What does “no object” mean for a reference?
null
        ↓
How do compile-time and runtime types fit together?
Type System Mental Model
```

## <a id="primitive-vs-reference-model">Primitive vs Reference</a>

A primitive variable holds a primitive value directly:

```java
int age = 20;
boolean active = true;
```

A reference variable holds a **reference value** that may identify an object/array or be `null`:

```java
User user = new User();
int[] values = new int[3];
```

Java still copies **values** during assignment and argument passing. For a reference variable, the copied value is the reference value itself.

## <a id="primitive-ranges-and-defaults">Primitive Ranges and Defaults</a>

Java has eight primitive types: `byte`, `short`, `int`, `long`, `float`, `double`, `char`, and `boolean`.

Integer primitives have fixed ranges; floating point follows IEEE 754; `char` is a 16-bit UTF-16 code unit; `boolean` represents logical truth values.

Fields receive language-defined default values. Local variables do not become readable through those defaults; the compiler requires definite assignment first.

Numeric literals also have compile-time types, so suffixes such as `L`/`F`, radix forms, and constant-expression rules can affect assignment.

## <a id="reference-value-semantics">What Does a Reference Value Mean?</a>

Java does not require programmers to treat references as manipulable physical memory addresses.

A sufficient mental model is:

```text
reference value
→ identifies an object/array
→ can be copied
→ can be compared for identity with ==
→ can be dereferenced
→ can be null
```

Several references may alias the same object. Losing one reference does not destroy the object while another reachable path remains.

The next chapter asks where variables are visible and how variable lifetime differs from object lifetime.
