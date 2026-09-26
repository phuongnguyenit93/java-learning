# Wrapper Types and Boxing

Primitives are not objects, while many Java APIs—especially generic collections—operate on reference types. Wrapper classes bridge that gap, such as `int ↔ Integer` and `double ↔ Double`.

## <a id="wrapper-types">Wrapper Types</a>

Each primitive has a wrapper counterpart:

```text
byte    ↔ Byte
short   ↔ Short
int     ↔ Integer
long    ↔ Long
float   ↔ Float
double  ↔ Double
char    ↔ Character
boolean ↔ Boolean
```

Wrappers are immutable objects. Because they are references, they can be `null`, participate in generic APIs, and have object identity distinct from primitive values.

### WHY - Why wrappers exist

Many APIs require reference types, especially generics:

```java
List<Integer> numbers = new ArrayList<>();
// List<int> is not valid Java
```

Wrappers also expose parsing/conversion helpers:

```java
int value = Integer.parseInt("42");
Integer boxed = Integer.valueOf(42);
```

When nullable/object semantics are unnecessary, primitives are often simpler and avoid boxing overhead.

## <a id="boxing-unboxing">Boxing and Unboxing</a>

**Boxing** converts a primitive value to a wrapper; **unboxing** extracts the primitive value.

```java
Integer boxed = 10; // autoboxing
int value = boxed;  // unboxing
```

Autoboxing is convenient, but it does not make primitives and wrappers the same type. Overload resolution, `null`, identity, and performance can still differ.

Boxing/unboxing can be inserted implicitly in assignment, method invocation, and arithmetic contexts:

```java
Integer a = 10;
void accept(Integer x) { }
accept(10);

Integer count = 10;
int next = count + 1; // unboxing before arithmetic
```

## <a id="wrapper-caching">Wrapper Caching</a>

Some wrapper values may be cached, so identity demos can be misleading:

```java
Integer a = 100;
Integer b = 100;
a == b // may be true because of caching
```

Do not use wrapper identity as value equality. Prefer `equals` or explicit primitive comparison according to the contract.

For `Integer`, the platform guarantees caching for at least `-128..127` for the relevant boxing/valueOf semantics, which is exactly why `==` demos can mislead. The correct model is still: wrappers are objects; reference `==` checks identity.

## <a id="unboxing-null">Unboxing null</a>

Wrappers may be `null`:

```java
Integer boxed = null;
int value = boxed; // NullPointerException
```

Unboxing requires an actual wrapper object from which to extract a primitive value.

The failure can be hidden inside larger expressions:

```java
Integer count = null;
int x = count;
int y = count + 1;
if (count > 0) { }
```

If null is valid domain state, handle absence before unboxing. If it is invalid, validate at the boundary rather than allowing a distant NPE.

The next chapter examines operators and implicit promotions inside expressions.
