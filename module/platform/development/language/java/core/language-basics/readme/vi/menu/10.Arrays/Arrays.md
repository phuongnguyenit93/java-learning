# Mảng trong Java

## <a id="array-type-model">Array type, length và covariance</a>
Array là object có fixed length và runtime component type. Reference array là covariant: `String[]` assign được cho `Object[]`. Sự tiện lợi này cần runtime store check vì write element incompatible phải fail.

## <a id="array-initialization">Khởi tạo array</a>
Tạo array sẽ cấp toàn bộ element và initialize bằng default value. Array initializer có thể cung cấp value trực tiếp. Length cố định sau khi tạo; muốn thay số element phải dùng array khác hoặc dynamic collection.

## <a id="array-covariance-risk">Array covariance và ArrayStoreException</a>
```java
Object[] values = new String[1];
values[0] = 123; // ArrayStoreException
```
Reference type cho phép assignment expression, nhưng runtime array vẫn nhớ nó là `String[]`. Đây là contrast quan trọng với generics invariance.

## <a id="multidimensional-arrays">Multidimensional array là array của array</a>
`int[][]` là array chứa reference tới `int[]`. Mỗi row có thể dài khác nhau hoặc thậm chí `null`. Java vì vậy hỗ trợ jagged array chứ không bắt buộc rectangular matrix.
