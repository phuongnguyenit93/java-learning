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

Next we connect primitives to object-oriented/generic APIs through wrapper types and boxing.
