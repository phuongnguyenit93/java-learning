# Variables and Scope

Knowing a value's type is not enough. We also need to know **where a variable name is visible and which runtime context owns it**. Scope is a source-code rule; object lifetime is a separate concern.

## <a id="variable-kinds-and-lifetime">Variable Kinds</a>

Common categories are:

```text
local variable
→ declared inside a block/method

parameter
→ receives a value when a method/constructor is invoked

instance field
→ per-object state

static field
→ class-level state
```

Local variables and parameters belong to execution frames/blocks; fields belong to object/class state.

Do not confuse the lifetime of a reference variable with the lifetime of the object it references.

### Declaration, initialization, and reassignment

```java
int count;      // declaration
count = 10;     // first assignment
count = 20;     // reassignment

int size = 5;   // declaration + initializer
```

Fields receive default initialization as part of object/class state. Local variables instead rely on definite-assignment analysis.

### `var` is still static typing

Since Java 10, a local variable with a suitable initializer may use `var`, letting the compiler **infer its static type**:

```java
var count = 10;             // int
var name = "Java";          // String
var user = new User("A");   // User
```

`var` does not make Java dynamically typed and does not mean the variable has no type. Once inferred, the type is fixed for compile-time checking:

```java
var value = "Java";
// value = 10; // invalid: value has static type String
```

The compiler needs an initializer that provides enough type information:

```java
// var x;        // invalid: no initializer
// var y = null; // invalid: no concrete type to infer
```

`var` is primarily syntax for **local-variable type inference**. It does not replace field types, return types, or ordinary method-parameter types.

Use it when the initializer and variable name make the type/intent obvious; prefer an explicit type when inference would make readers guess.

### Scope is not lifetime

```java
User saved;
{
    User local = new User("A");
    saved = local;
}
// local is out of scope, but the object remains reachable through saved
```

Scope asks where a **name is visible in source**. Lifetime/reachability asks how long runtime state remains available. Those are different questions.

## <a id="scope-and-shadowing">Scope and Shadowing</a>

Scope determines which name is visible at a source location.

A parameter may shadow a field:

```java
void setName(String name) {
    this.name = name;
}
```

`this.name` is the field; `name` is the parameter.

Legal shadowing is not always readable. Avoid reusing names across nested scopes when it obscures intent.

Block-local variables exist only in the block where their names are in scope:

```java
if (condition) {
    int result = 10;
    System.out.println(result);
}
// result is not visible here
```

Loop initializer variables are similarly scoped:

```java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
// i is not visible here
```

### Flow scope of pattern variables

Pattern variables are governed not only by braces but also by control flow: the compiler tracks where a successful match is guaranteed.

```java
if (value instanceof String text) {
    System.out.println(text.length());
}
// text is not visible here
```

The right side of `&&` executes only after the match succeeds, so the pattern variable is available there:

```java
if (value instanceof String text && !text.isBlank()) {
    System.out.println(text);
}
```

A guard clause can also extend the useful flow scope:

```java
if (!(value instanceof String text)) {
    return;
}

System.out.println(text.length());
```

At the final line, the compiler knows execution can continue only when the pattern has matched. This is an important example of **scope derived from control-flow guarantees**, not only lexical braces.

## <a id="definite-assignment">Definite Assignment</a>

The compiler must prove a local variable is assigned on every path before it is read:

```java
int x;
if (condition) {
    x = 1;
}
System.out.println(x); // may not compile
```

This is a **compile-time guarantee**, not field-style runtime default initialization.

The compiler reasons across control-flow paths:

```java
int x;
if (condition) {
    x = 1;
} else {
    x = 2;
}
System.out.println(x); // valid: every path assigns x
```

By contrast, a loop body may execute zero times, so an assignment inside it may not satisfy definite assignment afterward.

Next we connect primitives to object-oriented/generic APIs through wrapper types and boxing.
