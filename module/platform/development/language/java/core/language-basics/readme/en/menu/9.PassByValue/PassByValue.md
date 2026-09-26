# Pass-by-Value

Java **always passes arguments by value**. The confusion comes from objects: for a reference argument, the copied value is the **reference value**.

## <a id="java-pass-by-value">Java Is Always Pass-by-Value</a>

Each parameter receives a copy of the argument value.

For a primitive, changing the parameter does not change the caller's variable.

For an object reference, the parameter receives another reference value pointing to the same object.

### Mental model: the callee never receives the caller's variable slot

```text
caller variable                    parameter variable
┌──────────────┐     copy value    ┌──────────────┐
│ reference R  │ ───────────────→  │ reference R  │
└──────────────┘                   └──────────────┘
        │                                  │
        └──────────────→ User object ←─────┘
```

The parameter is a new variable owned by the call frame. Primitive arguments copy primitive values; reference arguments copy reference values. Java therefore remains consistently pass-by-value.

## <a id="reference-copy-mutation">Reference Copy and Mutation</a>

```java
void rename(User user) {
    user.setName("B");
}
```

The caller observes the mutation because both references point to the same `User` object.

This does **not** make Java pass-by-reference: the method did not receive the caller's variable slot, only a copied reference value.

Arrays behave the same way:

```java
void changeFirst(int[] values) {
    values[0] = 99;
}
```

The caller sees the changed element because both references identify the same array object. That is shared mutation through aliases, not pass-by-reference.

With immutable objects, reassignment is easier to notice:

```java
void append(String text) {
    text = text + "!";
}
```

The caller's variable does not change because the parameter is reassigned to another value/object.

## <a id="reassignment-vs-mutation">Reassignment vs Mutation</a>

```java
void replace(User user) {
    user = new User("new");
}
```

Reassigning the parameter only changes the local parameter variable. The caller's reference still points to the original object.

This distinction is foundational for aliasing, defensive copying, and API design.

The classic `swap` example demonstrates the rule:

```java
void swap(User a, User b) {
    User temp = a;
    a = b;
    b = temp;
}
```

Only the local parameters are swapped. The caller's variables keep their original references.

For mutable inputs, an API should state whether it only reads the object, mutates it, returns a new object, or retains the reference. Defensive copying is an API-boundary decision, not something Java pass-by-value performs automatically.

The next chapter applies the same value/reference model to arrays.
