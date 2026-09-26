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

### WHY - Why this split matters

Many beginner bugs come from assuming every variable "contains an object" in the same way. Primitive assignment copies the primitive value; reference assignment copies the reference value.

```java
int x = 10;
int y = x;
y++;
// x is still 10

User first = new User("A");
User second = first;
second.setName("B");
// first observes the same mutated object
```

Use this mental model:

```text
variable slot → primitive value

or

variable slot → reference value ──→ object
```

Java references are not C/C++ pointers: source code cannot do pointer arithmetic on them.

## <a id="primitive-ranges-and-defaults">Primitive Ranges and Defaults</a>

Java has eight primitive types: `byte`, `short`, `int`, `long`, `float`, `double`, `char`, and `boolean`.

Integer primitives have fixed ranges; floating point follows IEEE 754; `char` is a 16-bit UTF-16 code unit; `boolean` represents logical truth values.

Fields receive language-defined default values. Local variables do not become readable through those defaults; the compiler requires definite assignment first.

Numeric literals also have compile-time types, so suffixes such as `L`/`F`, radix forms, and constant-expression rules can affect assignment.

### Primitive roles and literals

| Group | Types | Practical note |
|---|---|---|
| integer | `byte`, `short`, `int`, `long` | `int` is the default integer arithmetic type; use `long` for larger ranges |
| floating point | `float`, `double` | decimal literals default to `double`; `float` commonly needs `F` |
| character | `char` | one UTF-16 code unit, not necessarily one complete Unicode character |
| boolean | `boolean` | only `true`/`false`; no implicit numeric truthiness |

### Ranges and sizes worth knowing

For integer primitives, Java defines stable sizes/ranges across platforms:

| Type | Size | Range |
|---|---:|---|
| `byte` | 8-bit signed | `-128 .. 127` |
| `short` | 16-bit signed | `-32_768 .. 32_767` |
| `int` | 32-bit signed | `-2^31 .. 2^31 - 1` |
| `long` | 64-bit signed | `-2^63 .. 2^63 - 1` |
| `char` | 16-bit unsigned | `0 .. 65_535` UTF-16 code unit |

`float` follows IEEE 754 binary32 and `double` binary64. At the `language-basics` level, the key point is that they are finite-precision floating-point representations; overflow, rounding, NaN, and infinity belong to the `numbers` module.

Standard constants such as `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE`, and `Long.MAX_VALUE` are usually clearer than hard-coding boundary numbers in real code.

### Literals have types and syntax too

```java
int decimal = 10;
int hex = 0xFF;
int binary = 0b1010;
int octal = 012;          // decimal 10

long big = 8_000_000_000L;

double ratio = 1.5;
float smallRatio = 1.5F;
double scientific = 1.2e3;

char letter = 'A';
char newline = '\n';
boolean active = true;
```

Practical rules:

- integer literals normally have type `int` when the value fits; use `L` when a `long` literal is required;
- floating-point literals default to `double`; use `F` for `float`;
- `_` may improve numeric readability but only in syntax-valid positions;
- a leading `0` can denote octal, so `012` is not decimal `12`;
- `char` literals use single quotes while `String` literals use double quotes.

```java
long population = 8_000_000_000L;
float ratio = 0.5F;
int hex = 0xFF;
int binary = 0b1010;
```

Field defaults and local-variable rules are intentionally different: fields receive language-defined defaults during object/class initialization, while local variables must be definitely assigned before use.

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

### Aliasing, identity, and assignment

```java
User a = new User("A");
User b = a;
User c = new User("A");
```

`a` and `b` are different variables that identify the same object. `c` identifies a different object even if its state initially looks equal.

Reference assignment does **not clone** an object:

```java
User original = new User("A");
User alias = original;
```

The second line copies only the reference value. Independent state requires an explicit copy strategy chosen by the API/design.

The next chapter asks where variables are visible and how variable lifetime differs from object lifetime.
