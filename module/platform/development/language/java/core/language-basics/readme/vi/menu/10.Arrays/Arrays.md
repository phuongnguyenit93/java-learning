# Array

Array là object đặc biệt biểu diễn **một sequence có kích thước cố định** và các phần tử cùng component type. Variable array vẫn là reference variable, vì vậy mọi quy tắc về reference copy, `null` và pass-by-value tiếp tục áp dụng.

## <a id="array-type-model">Mô hình Array Type</a>

```java
int[] numbers = new int[3];
String[] names = new String[3];
```

Array có:

- runtime array type;
- fixed `length` sau construction;
- index bắt đầu từ `0`;
- element default value theo component type.

Array itself là object, nên có thể gán `null`, truyền vào method và chia sẻ qua nhiều reference.

## <a id="array-initialization">Khởi tạo Array</a>

Có thể tạo array với length rồi gán từng phần tử:

```java
int[] values = new int[3];
values[0] = 10;
```

hoặc dùng initializer:

```java
int[] values = {10, 20, 30};
```

Primitive array nhận primitive default value; reference array nhận `null` cho từng phần tử ban đầu.

Truy cập index ngoài `[0, length)` gây `ArrayIndexOutOfBoundsException` ở runtime.

## <a id="array-covariance-risk">Array Covariance</a>

Reference array trong Java là covariant:

```java
String[] strings = new String[1];
Object[] objects = strings;
```

Assignment này compile, nhưng runtime array vẫn là `String[]`.

```java
objects[0] = Integer.valueOf(1); // ArrayStoreException
```

Compiler cho phép type relationship, còn runtime check bảo vệ component type thật của array.

Đây là một contrast quan trọng với generic collection, vốn invariant theo cách khác.

## <a id="multidimensional-arrays">Mảng nhiều chiều</a>

Java multidimensional array thực chất là **array chứa array**:

```java
int[][] matrix = new int[2][3];
```

Mỗi row là một `int[]` riêng, nên jagged array hoàn toàn hợp lệ:

```java
int[][] data = {
    {1, 2},
    {3, 4, 5}
};
```

Không nên mặc định nó là một contiguous rectangular memory matrix giống mọi ngôn ngữ khác.

chương tiếp theo rời khỏi value/container và xem Java tổ chức **tên type và khả năng truy cập theo package** như thế nào.
