# Tham số kiểu có giới hạn

Generics giúp ta nói “phương thức này làm việc với **một kiểu T nào đó**”. Nhưng đôi khi “kiểu nào cũng được” lại quá rộng.

Giả sử ta muốn viết một phương thức nhận nhiều loại số:

```java
static <T> double twice(T value) {
    // value.doubleValue(); // compile error
    return 0;
}
```

Compiler chỉ biết `T` là một kiểu chưa xác định. Nó **không thể giả định** mọi `T` đều có `doubleValue()`.

Ta cần diễn đạt thêm:

```text
"T có thể thay đổi,
nhưng T phải ít nhất là Number"
```

Đó là vai trò của **tham số kiểu có giới hạn (bounded type parameter)**: vẫn giữ khả năng tái sử dụng của Generics, nhưng giới hạn tập kiểu hợp lệ để phần triển khai có một hợp đồng đủ mạnh để làm việc.

## <a id="upper-bounded-type">Tham số kiểu có giới hạn trên</a>

```java
static <T extends Number> double twice(T value) {
    return value.doubleValue() * 2;
}
```

`T extends Number` nghĩa là đối số kiểu phải là `Number` hoặc một kiểu con (subtype) của nó.

```java
twice(10);     // Integer
twice(2.5);    // Double
// twice("10"); // compile error
```

Từ khóa vẫn là `extends` ngay cả khi giới hạn là một giao diện (`interface`):

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Bound vừa giới hạn bên gọi, vừa mở ra các khả năng an toàn cho phần triển khai.

Khai báo type parameter chỉ hỗ trợ **giới hạn trên (upper bound)** bằng `extends`:

```java
<T extends Number> // hợp lệ
// <T super Integer> // không hợp lệ
```

Nếu cần diễn tả “một kiểu cha (supertype) nào đó của `Integer`”, Java dùng **wildcard giới hạn dưới (lower-bounded wildcard)** `? super Integer` ở nơi sử dụng; chương Wildcard/PECS sẽ học phần đó.

## <a id="multiple-bounds">Nhiều giới hạn (Multiple Bounds)</a>

Một type parameter có thể cần thỏa nhiều hợp đồng kiểu:

```java
static <T extends Number & Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Nếu có giới hạn bằng lớp (`class`), lớp phải đứng đầu và chỉ có thể có tối đa một lớp:

```java
<T extends SomeClass & InterfaceA & InterfaceB>
```

Không hợp lệ:

```java
// <T extends InterfaceA & SomeClass> ...
```

Thứ tự bound còn có ảnh hưởng ở lúc biên dịch/runtime. Chương **Type Erasure** sẽ giải thích chính xác vì sao bound đứng đầu có vai trò đặc biệt; ở đây chỉ cần nhớ quy tắc cú pháp: nếu có class bound thì nó đứng trước các interface bound.

## <a id="recursive-bound">Giới hạn đệ quy (Recursive / Self Bound)</a>

Đây là **phần nâng cao**. Ở lượt học đầu, chỉ cần hiểu rằng bound có thể mô tả quan hệ giữa `T` và chính khả năng generic của nó; chưa cần tự thiết kế F-bound phức tạp.

Một bound có thể tham chiếu lại chính type parameter:

```java
<T extends Comparable<T>>
```

Ý nghĩa là “`T` phải so sánh được với chính `T`”.

Ví dụ:

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Đây thường được gọi là giới hạn đệ quy (recursive bound) hoặc kiểu F-bounded. Mục tiêu không phải tạo đệ quy lúc chạy; nó mô tả **quan hệ ràng buộc kiểu**.

Trong API thực tế có thể cần bound linh hoạt hơn như `Comparable<? super T>`, đặc biệt khi khả năng so sánh được khai báo ở một supertype. Chương Wildcard/PECS sẽ giải thích hướng `super`.

## <a id="bound-api-capability">Giới hạn mở ra khả năng an toàn</a>

Không bound:

```java
static <T> double area(T value) {
    // value.doubleValue(); // compiler không biết T có method này
    return 0;
}
```

Có bound:

```java
static <T extends Number> double asDouble(T value) {
    return value.doubleValue();
}
```

Compiler cho phép gọi những thành viên được bảo đảm bởi bound.

Mô hình tư duy:

```text
bound
→ giới hạn tập đối số kiểu hợp lệ
→ đổi lại phần triển khai nhận được một hợp đồng mạnh hơn
```

Không nên thêm bound chỉ để “cho chặt”. Bound quá cụ thể làm API khó tái sử dụng. Chỉ yêu cầu đúng khả năng mà thuật toán thực sự cần.

Bound nói về một **type variable có tên**. Trước khi chuyển sang wildcard, cần hiểu một quy tắc nền của kiểu generic: quan hệ kiểu con giữa các type argument **không tự truyền qua** kiểu generic. Chương tiếp theo giải thích tính bất biến (invariance) và vì sao `List<Dog>` không tự trở thành `List<Animal>`.
