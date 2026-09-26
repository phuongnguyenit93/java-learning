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

## <a id="assignment-compatibility">Assignment Compatibility</a>

Java permits assignments only when type/value relationships satisfy language rules.

Subtype-to-supertype assignment is normally implicit; the reverse direction requires a cast and may require a runtime check.

Primitive assignment has a different conversion model from reference assignment.

Static typing prevents many invalid operations before execution, but it cannot prove every runtime cast/reference operation will succeed.

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

## <a id="type-system-boundaries">Compile-time vs Runtime Boundaries</a>

The compiler can check name/type resolution, assignment compatibility, overload applicability, definite assignment, and many access/cast constraints.

Runtime still handles facts the compiler cannot know with certainty, such as actual downcast type, runtime array component type, null dereference, or array index bounds.

That is why static type checking coexists with runtime exceptions such as `ClassCastException`, `ArrayStoreException`, and `NullPointerException`.

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
