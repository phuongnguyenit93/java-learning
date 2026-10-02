<a id="back-to-top"></a>

# Lookup and Access Capabilities

## Menu
- [What Is Lookup and Why Does It Exist?](#lookup-purpose)
- [Lookup Class and Lookup Modes](#lookup-class-and-modes)
- [lookup, publicLookup, Lookup.in, and privateLookupIn](#lookup-context)
- [Creating MethodHandles for Methods, Constructors, and Field Accessors](#member-handle-lookup)
- [Lookup and Access-Checking Failures](#lookup-access-failures)

## <a id="lookup-purpose">What Is Lookup and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

`MethodHandles.Lookup` is a factory for creating MethodHandles or VarHandles when creation requires access checking. The key rule is: **member access is checked when the handle is created, not on every invocation of the handle**.

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
```

A Lookup carries a lookup class and a set of lookup modes. It therefore behaves like a capability object: code holding a particular Lookup can create only the handles allowed by that capability.

```text
caller / lookup class
        +
lookup modes
        ↓
Lookup capability
        ↓
findVirtual / findStatic / findConstructor / findVarHandle ...
        ↓
access checking occurs here
        ↓
created handle can invoke without repeating Java member access checks
```

That is why Lookup is more than a utility that “finds methods.”

</details>

- [Back to top](#back-to-top)

---

## <a id="lookup-class-and-modes">Lookup Class and Lookup Modes</a>

<details>
<summary>Click for details</summary>

`lookupClass()` identifies the class serving as access context. `lookupModes()` describes the remaining access capabilities. Java 21 exposes `PUBLIC`, `PRIVATE`, `PROTECTED`, `PACKAGE`, `MODULE`, `UNCONDITIONAL`, and `ORIGINAL` modes.

Two less-common modes are worth recognizing:

- `ORIGINAL`: identifies a lookup originating from the original lookup class (for example `MethodHandles.lookup()` or a VM-provided bootstrap lookup); transformations drop this mode;
- `UNCONDITIONAL`: used by `publicLookup()` for public access to unconditionally exported packages without depending on readability from a particular caller module.

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();

System.out.println(lookup.lookupClass());
System.out.println(lookup.lookupModes());
```

Capabilities can be **reduced** when a lookup moves into another context. Therefore the useful questions are:

```text
where was this Lookup created?
→ what is its lookup class?
→ which modes remain?
→ what are the package/module relationships?
```

Module boundaries matter on modern Java. A member may be public while package/module readability or openness still prevents the required lookup operation.

</details>

- [Back to top](#back-to-top)

---

## <a id="lookup-context">lookup, publicLookup, Lookup.in, and privateLookupIn</a>

<details>
<summary>Click for details</summary>

The common lookup/context operations have different purposes:

- `MethodHandles.lookup()`: a Lookup associated with the caller class;
- `MethodHandles.publicLookup()`: a restricted public-access lookup;
- `lookup.in(Target.class)`: changes lookup class while capabilities can be reduced by the rules;
- `MethodHandles.privateLookupIn(Target.class, lookup)`: requires the incoming lookup to have `MODULE` + `PRIVATE` access and creates a target lookup only when module/package relationships permit it.

```java
MethodHandles.Lookup callerLookup = MethodHandles.lookup();

MethodHandles.Lookup targetLookup =
        MethodHandles.privateLookupIn(
                Target.class,
                callerLookup
        );
```

`privateLookupIn` is not a “bypass private” switch. For a target in another module, the source module must read the target module and the target package must be opened appropriately. The resulting lookup uses the target as its lookup class, records the source as `previousLookupClass()`, and loses `MODULE` access during the cross-module teleport.

Framework code that needs non-public members should reason about these capabilities before reaching for broad JVM flags or opening modules indiscriminately.

</details>

- [Back to top](#back-to-top)

---

## <a id="member-handle-lookup">Creating MethodHandles for Methods, Constructors, and Field Accessors</a>

<details>
<summary>Click for details</summary>

`Lookup` exposes factories for executable handles:

```java
MethodHandle virtual =
        lookup.findVirtual(
                Greeter.class,
                "greet",
                MethodType.methodType(
                        String.class,
                        String.class
                )
        );

MethodHandle constructor =
        lookup.findConstructor(
                Greeter.class,
                MethodType.methodType(void.class)
        );
```

For `findConstructor`, the input `MethodType` uses `void` as the return type because it describes the constructor descriptor, but the returned handle's return type is the class being constructed. The example above therefore produces a `()Greeter` handle.

Fields can also be represented as **MethodHandle accessors**:

```java
MethodHandle getter =
        lookup.findGetter(
                User.class,
                "name",
                String.class
        );
```

But when the concern is **variable access semantics** such as volatile, acquire/release, or compare-and-set, VarHandle is the correct abstraction.

```text
field as callable getter/setter
→ MethodHandle accessor

field as variable with memory/access modes
→ VarHandle
```

</details>

- [Back to top](#back-to-top)

---

## <a id="lookup-access-failures">Lookup and Access-Checking Failures</a>

<details>
<summary>Click for details</summary>

Two major failure groups:

```text
symbolic member missing / descriptor wrong
→ NoSuchMethodException
→ NoSuchFieldException

member exists but Lookup lacks access
→ IllegalAccessException
```

For example:

```java
lookup.findVirtual(
        Greeter.class,
        "greet",
        MethodType.methodType(
                String.class,
                Object.class // wrong parameter type
        )
);
```

Lookup expects the member's exact symbolic signature. It does not search for the “nearest overload.”

When debugging access:

1. inspect lookup class and modes;
2. inspect declaring-class/member visibility;
3. inspect package/module readability and openness;
4. verify the exact `MethodType`;
5. only then reason about later handle adaptation.

</details>

- [Back to top](#back-to-top)
