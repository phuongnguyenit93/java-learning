# Bounded Type

Generic giúp ta nói “method này làm việc với **một kiểu T nào đó**”. Nhưng đôi khi “kiểu nào cũng được” lại quá rộng.

Giả sử ta muốn viết một method nhận nhiều loại số:

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

Đó là vai trò của **bounded type parameter**: vẫn giữ khả năng tái sử dụng của Generics, nhưng giới hạn tập kiểu hợp lệ để implementation có một contract đủ mạnh để làm việc.

## <a id="upper-bounded-type">Upper-Bounded Type Parameter</a>

```java
static <T extends Number> double twice(T value) {
    return value.doubleValue() * 2;
}
```

`T extends Number` nghĩa là type argument phải là `Number` hoặc subtype của nó.

```java
twice(10);     // Integer
twice(2.5);    // Double
// twice("10"); // compile error
```

Keyword vẫn là `extends` ngay cả khi bound là interface:

```java
static <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Bound vừa giới hạn caller, vừa mở capability an toàn cho implementation.

Type parameter declaration chỉ hỗ trợ **upper bound** bằng `extends`:

```java
<T extends Number> // hợp lệ
// <T super Integer> // không hợp lệ
```

Nếu cần diễn tả “một supertype nào đó của Integer”, Java dùng **lower-bounded wildcard** `? super Integer` ở nơi sử dụng; chương Wildcards sẽ học phần đó.

## <a id="multiple-bounds">Multiple Bounds</a>

Một type parameter có thể cần thỏa nhiều contract:

```java
static <T extends Number & Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Nếu có class bound, class phải đứng đầu và chỉ có thể có tối đa một class:

```java
<T extends SomeClass & InterfaceA & InterfaceB>
```

Không hợp lệ:

```java
// <T extends InterfaceA & SomeClass> ...
```

Thứ tự này cũng quan trọng với erasure: erasure của type variable dựa trên **leftmost bound**.

## <a id="recursive-bound">Recursive / Self Bound</a>

Đây là **phần nâng cao**. Ở lượt học đầu, chỉ cần hiểu rằng bound có thể mô tả quan hệ giữa `T` và chính capability generic của nó; chưa cần tự thiết kế F-bound phức tạp.

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

Đây thường được gọi là recursive bound hoặc F-bounded style. Mục tiêu không phải tạo recursion lúc runtime; nó mô tả **quan hệ type constraint**.

Trong API thực tế có thể cần bound linh hoạt hơn như `Comparable<? super T>`, đặc biệt khi capability được khai báo ở supertype. Chương Wildcards/PECS sẽ giải thích hướng `super`.

## <a id="bound-api-capability">Bound mở Capability an toàn</a>

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

Compiler cho phép gọi member được bảo đảm bởi bound.

Mô hình tư duy:

```text
bound
→ giới hạn tập type argument hợp lệ
→ đổi lại implementation nhận được một contract mạnh hơn
```

Không nên thêm bound chỉ để “cho chặt”. Bound quá cụ thể làm API khó tái sử dụng. Chỉ yêu cầu đúng capability mà thuật toán thực sự cần.

Bound nói về một **type variable có tên**. Ở API boundary, đôi khi ta không cần đặt tên kiểu cụ thể mà chỉ cần nói “một generic type nào đó”. Đó là vai trò của wildcard.
