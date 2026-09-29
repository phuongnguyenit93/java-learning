# Type System Mental Model

The module ends by separating two worlds that are often conflated: **what the compiler knows from static types** and **what runtime knows from the actual object/value**.

## <a id="compile-time-vs-runtime-type">Compile-time vs Runtime Type</a>

```java
Animal animal = new Dog();
```

Here:

```text
declared / compile-time type
→ Animal

runtime object type
→ Dog
```

The compiler uses `Animal` for member availability, conversions, and overload rules. The runtime `Dog` type participates in dynamic dispatch for overridden instance methods.

Static type belongs to the expression/reference view:

```java
Animal animal = new Dog();
animal.eat();
// animal.bark(); // invalid if Animal does not declare bark
```

Even though the runtime object is a `Dog`, the compiler checks members through the static type of the expression. Runtime type does not cause overload resolution to be re-run.

## <a id="assignment-compatibility">Assignment Compatibility</a>

Java permits assignments only when type/value relationships satisfy language rules.

Subtype-to-supertype assignment is normally implicit; the reverse direction requires a cast and may require a runtime check.

Primitive assignment has a different conversion model from reference assignment.

Static typing prevents many invalid operations before execution, but it cannot prove every runtime cast/reference operation will succeed.

Assignment is a compile-time contract:

```java
Object value = "java";      // reference widening
String text = (String) value; // explicit downcast + runtime check

long n = 10;        // primitive widening
Integer boxed = 10; // boxing
```

Do not use "cast" as a catch-all for every conversion. Widening, narrowing, boxing, unboxing, and reference casts follow different rules and have different runtime implications.

### The same value may convert differently in different contexts

One reason Java conversions can feel inconsistent is that **different contexts permit different sets of conversions**.

| Context | Mental model | Example |
|---|---|---|
| assignment | assignment conversion | `long x = 10;` |
| arithmetic/operator | numeric promotion | `byte + byte -> int` |
| method invocation | invocation conversion | `use(long)` accepts an `int` argument through widening |
| explicit cast | casting conversion | `byte b = (byte) x;` |
| primitive/object boundary | boxing / unboxing | `Integer n = 10; int x = n;` |

A conversion that is legal in one context is **not necessarily inserted automatically** in another.

Constant assignment has a special rule, for example:

```java
byte a = 1; // valid: constant value fits byte

void use(byte value) { }
// use(1);   // invalid: method invocation does not apply that narrowing constant conversion
use((byte) 1);
```

Operators have their own promotion rules:

```java
byte x = 1;
byte y = 2;
// byte z = x + y; // x + y has type int
int z = x + y;
```

When two nearly identical lines behave differently, first ask: **is this assignment, an operator expression, method invocation, or an explicit-cast context?**

## <a id="overload-vs-override-dispatch">Overload vs Override Dispatch</a>

Keep the distinction:

```text
overload selection
→ compile time
→ method set + static argument types/conversions

override dispatch
→ runtime
→ runtime receiver type after a signature is selected
```

That is why one expression can choose an overload using a declared type while still executing a subclass override body at runtime.

Example:

```java
class Parent {
    void speak() { System.out.println("Parent"); }
}

class Child extends Parent {
    @Override
    void speak() { System.out.println("Child"); }
}

void use(Parent x) { x.speak(); }
void use(Object x) { System.out.println("Object"); }

Parent p = new Child();
use(p);
```

The compiler selects `use(Parent)` from the static type of `p`. Inside that method, runtime dispatch invokes `Child.speak()` because the actual receiver is a `Child`.

## <a id="type-system-boundaries">Compile-time vs Runtime Boundaries</a>

The compiler can check name/type resolution, assignment compatibility, overload applicability, definite assignment, and many access/cast constraints.

Runtime still handles facts the compiler cannot know with certainty, such as actual downcast type, runtime array component type, null dereference, or array index bounds.

That is why static type checking coexists with runtime exceptions such as `ClassCastException`, `ArrayStoreException`, and `NullPointerException`.

| Situation | Compile time | Runtime still decides/checks |
|---|---|---|
| local variable | definite assignment | concrete value |
| overload | applicability + most-specific | overload is not selected again |
| override | selected signature | receiver type dispatches body |
| downcast | legal type relationship | actual object compatibility |
| array store | static assignment compatibility | runtime component type |
| array index | integer-compatible expression | bounds |
| dereference | member exists on static type | reference is non-null |

When reading difficult Java code, ask in order: what is the static type, is the value primitive or reference, which conversion context applies, what does the compiler decide, what remains for runtime, and is a mutation changing a local variable or shared object state?

The module's final chain is:

```text
value model
→ scope/lifetime
→ conversions/expressions
→ control flow
→ method calls
→ pass-by-value/reference sharing
→ arrays/packages/null
→ compile-time types vs runtime behavior
```
