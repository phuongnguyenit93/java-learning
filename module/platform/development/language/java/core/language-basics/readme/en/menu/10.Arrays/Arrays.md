# Arrays in Java

## <a id="array-type-model">Array type, length and covariance</a>
Arrays are objects with a fixed length and a runtime component type. Reference arrays are covariant: `String[]` is assignable to `Object[]`. That convenience requires a runtime store check because writing an incompatible element must fail.

## <a id="array-initialization">Array creation and initialization</a>
Creating an array allocates all elements and initializes them to default values. Array initializer syntax can provide values directly. Length is fixed after creation; changing the number of elements requires another array or a dynamic collection.

## <a id="array-covariance-risk">Array covariance and ArrayStoreException</a>
```java
Object[] values = new String[1];
values[0] = 123; // ArrayStoreException
```
The reference type allows the assignment expression, but the runtime array remembers it is a `String[]`. This is a key contrast with invariant generics.

## <a id="multidimensional-arrays">Multidimensional arrays are arrays of arrays</a>
`int[][]` is an array whose elements are references to `int[]`. Rows can have different lengths and can even be `null`. Java therefore supports jagged arrays rather than requiring a rectangular memory matrix.
