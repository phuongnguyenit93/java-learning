# Overloading and Overriding

This is a **Java mechanism chapter** placed next to polymorphism because the terms are commonly confused. Overloading is **not an OOP pillar**. It is compile-time method selection. Overriding + dynamic dispatch are the mechanisms directly responsible for subtype-polymorphic runtime behavior.

## <a id="overloading-compile-time">Overloading</a>

### WHAT

Overloading occurs when several methods share a name but have different parameter lists.

```java
String select(Parent value) { return "Parent"; }
String select(Child value)  { return "Child"; }
```

### HOW — how the compiler chooses an overload

The compiler uses the **compile-time type of the arguments** together with invocation conversions to choose a signature. The runtime object does not cause Java to reconsider a different overload later.

```java
Parent value = new Child();
select(value); // select(Parent)
```

Although the runtime object is `Child`, the expression `value` has compile-time type `Parent`, so `select(Parent)` is bound at compile time.

### BOUNDARY — this chapter does not replace full overload-resolution rules

The OOP goal is to contrast **compile-time overload selection** with **runtime override dispatch**. Detailed rules for widening, boxing, varargs, most-specific methods, and ambiguity belong to `language-basics → Methods`.

### EVIDENCE — `DispatchController#overloadVsOverride()`

One variable, `Parent x = new Child()`, is used for both overload and override behavior:

```text
select(x)
→ compile-time type Parent
→ select(Parent)

x.call()
→ runtime object Child
→ Child.call()
```

That contrast is the most important takeaway from this chapter.

## <a id="overriding-runtime">Overriding</a>

### WHAT

A subclass override supplies a new implementation of an inherited instance-method contract with a compatible signature.

The same overriding model also applies when a class provides an implementation for an inherited interface instance-method contract or overrides a default method. Detailed interface rules belong to `abstract-interface`; the OOP focus here is still **runtime selection of an instance-method implementation**.

```java
class Parent {
    String call() { return "Parent"; }
}

class Child extends Parent {
    @Override
    String call() { return "Child"; }
}
```

### HOW

The compiler first resolves the call to the `call()` signature. When the runtime receiver is `Child`, dynamic dispatch selects the `Child` body.

This is the mechanism that supports subtype polymorphism: a caller may hold a `Parent` reference and still receive subtype behavior.

## <a id="covariant-return">Covariant Return Types</a>

An overriding method may narrow a **reference return type** to a subtype of the parent's return type.

```java
class Parent {
    Animal create() { ... }
}

class Child extends Parent {
    @Override
    Dog create() { ... }
}
```

`Dog` is a subtype of `Animal`, so the supertype contract remains valid while callers that know `Child` get a more precise return type.

Primitive return types do not participate in covariant returns in this sense.

## <a id="override-rules">Overriding Rules</a>

Not every same-named subclass method is an override.

| Case | Runtime overriding? | Remember |
| --- | --- | --- |
| Compatible instance method | Yes | Dynamic dispatch |
| `final` instance method | No | Subclass cannot override it |
| `private` method | No | Not an inherited override target |
| Same-signature `static` method | No | Method hiding |

An override also cannot reduce accessibility and must respect checked-exception compatibility.

Use `@Override` whenever possible because the compiler can catch many relationship/signature mistakes early.

### MAKE THE RULES CONCRETE — visibility, return type, checked exceptions

An override cannot reduce accessibility:

```java
class Parent {
    public Number value() { return 1; }
}

class Child extends Parent {
    @Override
    protected Number value() { return 2; } // compile error
}
```

Reference return types may be covariant:

```java
class Parent {
    Number value() { return 1; }
}

class Child extends Parent {
    @Override
    Integer value() { return 2; } // OK
}
```

For checked exceptions, an override may keep, narrow, or remove the declared checked exception, but it cannot broaden the contract:

```java
class Parent {
    void load() throws IOException {}
}

class Child extends Parent {
    @Override
    void load() throws FileNotFoundException {} // OK
}
```

Using `throws Exception` on `Child.load()` in this example would be illegal because callers of the `Parent` contract would suddenly face a broader checked exception.

### CLASSIFY THE COMMON CONFUSIONS

```text
private method
→ not an inherited override target

final instance method
→ may be inherited but cannot be overridden

static method
→ same-signature subtype method is hiding, not runtime overriding

constructor
→ not inherited and never overridden
```

Java also does not let a subtype switch a same-signature method between static and instance form. That is a compile-time conflict, not another overriding variant.

## <a id="static-method-hiding">Static Method Hiding</a>

Static methods are type-level behavior and do not dispatch from the runtime receiver like instance methods.

```java
class Parent {
    static String call() { return "Parent"; }
}

class Child extends Parent {
    static String call() { return "Child"; }
}

Parent value = new Child();
value.call(); // Parent.call()
```

Selection follows the compile-time qualifying type `Parent`.

Calling static methods through instances may compile in some contexts but is misleading; prefer the class name so intent is explicit.

`super.someMethod()` explicitly selects the supertype implementation, so it is different from a normal virtual call through `this.someMethod()`. It is commonly used when an override extends rather than completely replaces parent behavior.

## <a id="field-hiding">Field Hiding</a>

Fields are not virtual and do not participate in dynamic dispatch.

```java
class Parent { String name = "parent"; }
class Child extends Parent { String name = "child"; }

Parent value = new Child();
System.out.println(value.name); // parent
```

The expression `value.name` resolves the field from compile-time type `Parent`. This is field hiding, not polymorphic state dispatch.

The `Parent.name` and `Child.name` declarations represent **two distinct members/slots**, not one field chosen dynamically. Different static types or casts can expose different fields on the same runtime object, which is why field hiding is usually best avoided.

Avoid reusing the same field name across a hierarchy unless there is a strong reason because it creates a confusing mental model.

## <a id="dispatch-vs-hiding">Dispatch vs Hiding</a>

Keep this matrix as a mental shortcut:

| Member/call | Main decision point | With `Parent x = new Child()` |
| --- | --- | --- |
| Overloaded method signature | Compile time | based on static argument type |
| Overridden instance-method body | Runtime | may execute `Child` body |
| Hidden static method | Compile time | follows qualifying type `Parent` |
| Hidden field | Compile time | follows expression type `Parent` |

### EVIDENCE — `DispatchController#dispatchVsHiding()`

The experiment returns all of these together:

```text
runtime        = Child
instanceMethod = Child.call
staticMethod   = Parent.staticCall
field          = parent-field
```

One runtime object produces different outcomes across member categories. This directly breaks the misconception that “everything accessed through an object uses dynamic dispatch”.

### RELATION — from dispatch to composition

Inheritance + overriding are powerful, but deeper hierarchies increase coupling. If the goal is merely to vary behavior, **composition** often lets us replace a collaborator without extending the consumer's hierarchy.
