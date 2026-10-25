# Arrays

An array is a special object representing a **fixed-size sequence** of elements with one component type. Array variables are reference variables, so ordinary reference-copy and `null` rules apply; pass-by-value is connected after methods are introduced.

## <a id="array-type-model">Array Type Model</a>

Arrays have a runtime array type, a fixed `length`, zero-based indexes, and element default values based on the component type.

The array itself is an object and can be shared through several references or set to `null`. A null array reference is different from an existing reference array whose elements start as null:

```java
String[] a = null;          // no array object
String[] b = new String[1]; // array exists; b[0] == null
int[] empty = new int[0];   // a real array object with length 0
```

Null and an empty array are different states: `null` identifies no array object, while `new int[0]` creates a valid array object that simply contains no elements.

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

For a reference array, a null element still fails when dereferenced:

```java
User[] users = new User[1];
users[0].getName(); // NullPointerException because users[0] == null
```

Indexed loops are useful when position or mutation matters; enhanced-for is clearer when only element values are needed.

The enhanced-for loop variable receives an element value on each iteration. Reassigning that local variable does **not** replace the array slot:

```java
int[] numbers = {1, 2, 3};
for (int number : numbers) {
    number = 0; // changes only the local loop variable
}
```

Use an index or another appropriate API when the array slot itself must change.

Enhanced-for also works with `Iterable` values such as Collections introduced later. The loop-variable rule is the same: reassigning the local loop variable does not replace the element stored in the container.

```java
List<String> items = List.of("A", "B"); // preview of a Collection API
for (String item : items) {
    item = item.toLowerCase(); // does not replace an element in items
}
```

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

The next chapter uses arrays as the foundation for varargs and then explains how data crosses a method-call boundary.
