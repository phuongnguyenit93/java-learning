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

## <a id="boxing-unboxing">Boxing and Unboxing</a>

**Boxing** converts a primitive value to a wrapper; **unboxing** extracts the primitive value.

```java
Integer boxed = 10; // autoboxing
int value = boxed;  // unboxing
```

Autoboxing is convenient, but it does not make primitives and wrappers the same type. Overload resolution, `null`, identity, and performance can still differ.

## <a id="wrapper-caching">Wrapper Caching</a>

Some wrapper values may be cached, so identity demos can be misleading:

```java
Integer a = 100;
Integer b = 100;
a == b // may be true because of caching
```

Do not use wrapper identity as value equality. Prefer `equals` or explicit primitive comparison according to the contract.

## <a id="unboxing-null">Unboxing null</a>

Wrappers may be `null`:

```java
Integer boxed = null;
int value = boxed; // NullPointerException
```

Unboxing requires an actual wrapper object from which to extract a primitive value.

The next chapter examines operators and implicit promotions inside expressions.
