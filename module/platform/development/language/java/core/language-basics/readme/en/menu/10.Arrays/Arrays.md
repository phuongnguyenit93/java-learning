# Arrays

An array is a special object representing a **fixed-size sequence** of elements with one component type. Array variables are reference variables, so ordinary reference-copy, `null`, and pass-by-value rules still apply.

## <a id="array-type-model">Array Type Model</a>

Arrays have a runtime array type, a fixed `length`, zero-based indexes, and element default values based on the component type.

The array itself is an object and can be shared through several references or set to `null`.

Declaration and creation are separate operations:

```java
int[] a;
a = new int[3];
```

`length` is a fixed array property after creation; arrays do not have a `length()` method.

Arrays are mutable objects, and assigning one array variable to another copies only the reference:

```java
int[] first = {1, 2};
int[] second = first;
second[0] = 99;
```

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

```java
int[] numbers = new int[2];       // [0, 0]
boolean[] flags = new boolean[2]; // [false, false]
String[] names = new String[2];   // [null, null]
```

Indexed loops are useful when position or mutation matters; enhanced-for is clearer when only element values are needed.

`==` compares array reference identity rather than element contents. Use utilities such as `Arrays.equals`, `Arrays.copyOf`, or `System.arraycopy` when content comparison/copying is the real intent.

### Copying an array does not deep-copy referenced elements

For a primitive array, copying elements produces independent primitive values in the new array. For a reference array, the new array receives **copies of the element reference values**:

```java
User[] original = {
    new User("A")
};

User[] copied = Arrays.copyOf(original, original.length);

copied[0].setName("B");
System.out.println(original[0].getName()); // B
```

`original` and `copied` are different array objects, but element `0` in both arrays identifies the same `User` object:

```text
original array            copied array
┌─────────────┐            ┌─────────────┐
│ ref R ──────┼──────┐     │ ref R ──────┼──────┐
└─────────────┘      │     └─────────────┘      │
                     └────────→ User ←───────────┘
```

Therefore `Arrays.copyOf`/`System.arraycopy` copy array contents element-by-element; they do not automatically deep-copy objects referenced by those elements. Shallow/deep object-copy strategies belong to the `class-object` module.

## <a id="array-covariance-risk">Array Covariance</a>

Reference arrays are covariant:

```java
String[] strings = new String[1];
Object[] objects = strings;
```

But the runtime array remains `String[]`, so storing an `Integer` through `objects` throws `ArrayStoreException`.

The compiler accepts the type relation; runtime preserves the real component type.

Why can the failure happen at runtime? The variable may have static type `Object[]`, while the actual array object remains `String[]`. The JVM checks stores against that runtime component type:

```text
compile-time view: Object[]
runtime object:     String[]
store Integer
        ↓
ArrayStoreException
```

## <a id="multidimensional-arrays">Multidimensional Arrays</a>

Java multidimensional arrays are **arrays of arrays**, so jagged shapes are valid.

Do not assume they are one contiguous rectangular matrix representation.

Rows may have different lengths or even be `null`:

```java
int[][] data = new int[3][];
data[0] = new int[2];
data[1] = new int[5];
// data[2] is still null
```

Nested loops should therefore use each row's own `length` and honor the nullability contract for rows.

The next chapter moves from values and containers to package-level naming and visibility.
