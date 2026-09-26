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

### Declaration khác creation

```java
int[] a;          // chỉ khai báo reference variable
a = new int[3];   // tạo array object rồi gán reference

int b[] = new int[3]; // hợp lệ nhưng style int[] b thường dễ đọc hơn
```

`length` là field-like property cố định của array sau creation:

```java
System.out.println(a.length);
```

Không có `length()` method cho array.

### Array là mutable object

```java
int[] first = {1, 2};
int[] second = first;
second[0] = 99;
```

`first[0]` cũng thành `99` vì assignment chỉ copy reference. Muốn array độc lập cần copy elements bằng API phù hợp.

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

### Default element value

```java
int[] numbers = new int[2];       // [0, 0]
boolean[] flags = new boolean[2]; // [false, false]
String[] names = new String[2];   // [null, null]
```

Điều này khác local variable definite assignment: array object đã được khởi tạo và từng slot có default value.

### Iteration

```java
for (int i = 0; i < values.length; i++) {
    System.out.println(values[i]);
}

for (int value : values) {
    System.out.println(value);
}
```

Dùng index khi cần vị trí hoặc mutate slot. Enhanced-for phù hợp khi chỉ cần đọc từng element.

### Equality và copy

`==` trên array so reference identity, không so từng element. Khi cần content equality/copy, dùng utility phù hợp như `Arrays.equals`, `Arrays.copyOf` hoặc `System.arraycopy` tùy use case.

### Copy array không đồng nghĩa deep-copy element

Với primitive array, copy element tạo ra primitive value độc lập ở array mới. Với reference array, array mới nhận **bản copy của từng reference value**:

```java
User[] original = {
    new User("A")
};

User[] copied = Arrays.copyOf(original, original.length);

copied[0].setName("B");
System.out.println(original[0].getName()); // B
```

Ở đây `original` và `copied` là hai array object khác nhau, nhưng element index `0` của cả hai chứa reference tới cùng `User` object:

```text
original array            copied array
┌─────────────┐            ┌─────────────┐
│ ref R ──────┼──────┐     │ ref R ──────┼──────┐
└─────────────┘      │     └─────────────┘      │
                     └────────→ User ←───────────┘
```

Vì vậy `Arrays.copyOf`/`System.arraycopy` copy array contents theo element value; chúng không tự deep-copy object được reference bởi các element. Shallow/deep object-copy strategy được đào sâu ở module `class-object`.

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

### WHY - Vì sao lỗi xuất hiện ở runtime?

Variable `objects` có compile-time type `Object[]`, nên compiler cho phép gán `Integer`. Nhưng object thật vẫn là `String[]` và array mang runtime component type, vì vậy JVM phải kiểm tra mỗi store để giữ type safety.

```text
compile-time view: Object[]
runtime object:     String[]
store Integer
        ↓
runtime check → ArrayStoreException
```

Điều này minh họa rõ ranh giới static type vs runtime type mà chapter cuối sẽ tổng kết.

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

Mỗi row có thể có length khác nhau hoặc thậm chí là `null`:

```java
int[][] data = new int[3][];
data[0] = new int[2];
data[1] = new int[5];
// data[2] vẫn null
```

Do đó code nested-loop nên dùng `data[row].length` cho từng row và xử lý null row nếu contract cho phép.

chương tiếp theo rời khỏi value/container và xem Java tổ chức **tên type và khả năng truy cập theo package** như thế nào.
