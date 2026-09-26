# Arrays

An array is a special object representing a **fixed-size sequence** of elements with one component type. Array variables are reference variables, so ordinary reference-copy, `null`, and pass-by-value rules still apply.

## <a id="array-type-model">Array Type Model</a>

Arrays have a runtime array type, a fixed `length`, zero-based indexes, and element default values based on the component type.

The array itself is an object and can be shared through several references or set to `null`.

## <a id="array-initialization">Array Initialization</a>

Arrays can be created by length and populated later:

```java
int[] values = new int[3];
```

or created with an initializer:

```java
int[] values = {10, 20, 30};
```

Primitive elements receive primitive defaults; reference elements start as `null`. Invalid indexes fail with `ArrayIndexOutOfBoundsException` at runtime.

## <a id="array-covariance-risk">Array Covariance</a>

Reference arrays are covariant:

```java
String[] strings = new String[1];
Object[] objects = strings;
```

But the runtime array remains `String[]`, so storing an `Integer` through `objects` throws `ArrayStoreException`.

The compiler accepts the type relation; runtime preserves the real component type.

## <a id="multidimensional-arrays">Multidimensional Arrays</a>

Java multidimensional arrays are **arrays of arrays**, so jagged shapes are valid.

Do not assume they are one contiguous rectangular matrix representation.

The next chapter moves from values and containers to package-level naming and visibility.
