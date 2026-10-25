<a id="back-to-top"></a>

# Adapting and Composing Method Handles

## Menu
- [Adaptation and Composition Mental Model](#method-handle-adaptation-model)
- [MethodHandle Type Adaptation](#method-handle-type-adaptation)
- [Binding, Inserting, and Reordering Arguments](#method-handle-binding)
- [Transforming Arguments and Return Values](#method-handle-transformations)
- [Composing Handles into Invocation Pipelines](#method-handle-composition)
- [Guards, SwitchPoint, and Runtime Target Selection](#method-handle-guards)

## <a id="method-handle-adaptation-model">Adaptation and Composition Mental Model</a>

<details>
<summary>Click for details</summary>

Once a MethodHandle identifies the correct operation, the powerful part of `java.lang.invoke` is creating **new handles** through adaptation and composition instead of writing a wrapper method for every shape.

```text
(Greeter,String)String
        ↓ bind receiver
(String)String
        ↓ filter argument
(Object)String
        ↓ filter return
(Object)Object
```

Each step produces a new handle with a new `MethodType`, while the result remains directly executable.

- **adaptation** changes how callers present arguments/results to a target;
- **composition** combines handles into a larger executable operation.

Adapters are not automatically free. Conversions, boxing, casts, and deep pipelines can have costs. Choose them for a sound runtime design first, then benchmark when performance actually matters.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-handle-type-adaptation">MethodHandle Type Adaptation</a>

<details>
<summary>Click for details</summary>

`asType` creates an adapter with another `MethodType` when the required conversions are supported.

```java
MethodHandle typed = ...; // (String)String

MethodHandle generic =
        typed.asType(
                MethodType.methodType(
                        Object.class,
                        Object.class
                )
        );
```

Calling `generic` converts/casts the incoming `Object` to the String expected by the target and exposes the String result as Object.

`explicitCastArguments` has different conversion rules and is appropriate when explicit casting/numeric conversion semantics are intended.

```text
current target type
        ↓
desired call contract
        ↓
legal adapter?
        ↓
new handle with an exact new type
```

</details>

- [Back to top](#back-to-top)

---

## <a id="method-handle-binding">Binding, Inserting, and Reordering Arguments</a>

<details>
<summary>Click for details</summary>

Binding fixes an argument in advance:

```java
Greeter greeter = new Greeter();

MethodHandle greet = ...; // (Greeter,String)String

MethodHandle bound =
        greet.bindTo(greeter); // (String)String
```

`insertArguments` generalizes this to a selected parameter position:

```java
MethodHandle fixedPrefix =
        MethodHandles.insertArguments(
                target,
                0,
                "INFO"
        );
```

`permuteArguments` can reorder or reuse incoming arguments according to a mapping:

```text
before: (A,B)R
after : (B,A)R
```

These adapters are useful when infrastructure code normalizes many target signatures to one internal calling convention.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-handle-transformations">Transforming Arguments and Return Values</a>

<details>
<summary>Click for details</summary>

`filterArguments` transforms inputs before the target runs:

```java
MethodHandle trim =
        lookup.findVirtual(
                String.class,
                "trim",
                MethodType.methodType(String.class)
        );

MethodHandle normalized =
        MethodHandles.filterArguments(
                boundGreet,
                0,
                trim
        );
```

```text
"  Phuong "
→ trim
→ "Phuong"
→ greet
→ "Hello Phuong"
```

`filterReturnValue` applies the same idea to output. Fold/combiner APIs can compute additional values from arguments before a target is invoked.

If a pipeline becomes harder to understand than a normal Java wrapper method, that is a design signal: a plain method may be the better abstraction.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-handle-composition">Composing Handles into Invocation Pipelines</a>

<details>
<summary>Click for details</summary>

Composition lets multiple small operations become one executable pipeline:

```text
input
 ↓
normalize
 ↓
validate / select
 ↓
business target
 ↓
format result
```

Advantages in runtime/framework code:

- pipelines can be constructed once;
- the final handle has a clear MethodType;
- callers invoke one executable value;
- the typed handle chain gives the JVM optimization opportunities; actual performance still needs measurement on the relevant workload.

But deep composition also increases reasoning and debugging cost. Prefer semantic variable names:

```java
MethodHandle normalizeName = ...;
MethodHandle invokeGreeting = ...;
MethodHandle formatResult = ...;
```

rather than opaque `mh1`, `mh2`, `mh3`.

</details>

- [Back to top](#back-to-top)

---

## <a id="method-handle-guards">Guards, SwitchPoint, and Runtime Target Selection</a>

<details>
<summary>Click for details</summary>

`guardWithTest` selects between two compatible targets using a predicate handle:

```text
test(args)
 ├─ true  → target(args)
 └─ false → fallback(args)
```

```java
MethodHandle guarded =
        MethodHandles.guardWithTest(
                test,
                fastPath,
                fallback
        );
```

Important type invariants:

- `test` must return `boolean`;
- `target` and `fallback` must have the same type;
- the parameters consumed by `test` must match a **prefix** of the target/fallback parameters; the test may consume fewer arguments.

If these types do not line up, `guardWithTest` throws `IllegalArgumentException` while the adapter is being built, before the resulting handle is invoked.

`SwitchPoint` adds one-way invalidation. Before invalidation, a guard can use the primary target; after invalidation, it permanently falls back.

```text
cached assumption valid
→ fast target

assumption invalidated
→ SwitchPoint.invalidateAll(...)
→ fallback thereafter
```

This is different from `MutableCallSite`: SwitchPoint models a **one-way invalidated assumption**, while a mutable call site's target can be updated repeatedly.

</details>

- [Back to top](#back-to-top)
