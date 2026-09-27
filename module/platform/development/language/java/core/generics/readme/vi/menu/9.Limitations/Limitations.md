# Giới hạn của Generics

Chương này là **lượt học sâu** nhiều hơn là syntax cần dùng mỗi ngày. Beginner nên biết các restriction tồn tại và hiểu nguyên nhân chung; không cần học thuộc từng edge case trước khi bắt đầu sử dụng Generics.

Sau khi hiểu `T`, wildcard và erasure, một loạt câu hỏi thường xuất hiện:

```java
// List<int> numbers;             // tại sao không được?
// T value = new T();             // tại sao không được?
// T[] values = new T[10];        // tại sao không được?
// Object value; value instanceof List<String> // tại sao không được?
```

Nếu chỉ học từng dòng như một “luật cấm”, Generics rất dễ biến thành một đống quy tắc phải học thuộc.

Chương này gom chúng lại theo **nguyên nhân**.

Phần lớn giới hạn đến từ ba điều:

```text
Generics chủ yếu được kiểm tra ở compile time
        +
type erasure làm mất một phần generic type identity ở runtime
        +
một số cơ chế của JVM cần biết kiểu thật ở runtime
```

Khi ba ý này đã rõ, các restriction như primitive type argument, `new T()`, generic array hay generic exception sẽ bớt “vô lý” và có thể suy ra thay vì học thuộc.

## <a id="no-generic-primitives">Không dùng Primitive làm Type Argument</a>

Không hợp lệ:

```java
// List<int> numbers;
// Box<double> value;
```

Type argument phải là reference type:

```java
List<Integer> numbers = new ArrayList<>();
Box<Double> value = new Box<>();
```

Autoboxing giúp cú pháp sử dụng thuận tiện:

```java
numbers.add(1); // int -> Integer
```

Nhưng wrapper vẫn là object, nên có khác biệt về allocation/identity/nullability và chi phí so với primitive. Generics không biến `List<Integer>` thành primitive-specialized `int[]`.

## <a id="no-new-type-parameter">Không thể new T trực tiếp</a>

Không hợp lệ:

```java
static <T> T create() {
    // return new T();
}
```

Sau erasure, runtime không có constructor target cụ thể chỉ từ `T`.

Generic code phải nhận **chiến lược tạo object từ bên ngoài** thay vì tự đoán constructor. Ta có thể diễn tả ý tưởng chỉ bằng những kiến thức đã học:

```java
interface Factory<T> {
    T create();
}

static <T> T create(Factory<T> factory) {
    return factory.create();
}
```

Caller quyết định cách tạo `T`; generic method chỉ giữ quan hệ kiểu. Những cách ngắn hơn bằng lambda hoặc cách dựa vào `Class<T>`/Reflection thuộc các module sau.

## <a id="generic-array-limit">Giới hạn của Generic Array</a>

Không thể tạo:

```java
// T[] values = new T[10];
// List<String>[] lists = new List<String>[10];
```

Array là reified/covariant: runtime biết component type và kiểm tra store. Parameterized type như `List<String>` lại non-reifiable, nên JVM không thể thực hiện đầy đủ check mà array contract yêu cầu.

`List<?>[]` có thể tạo vì `List<?>` là reifiable:

```java
List<?>[] lists = new List<?>[10];
```

Trong generic implementation, thường ưu tiên collection thay vì generic array. Khi internal optimization bắt buộc dùng array, unchecked cast phải được cô lập và invariant phải rất rõ.

Erasure cũng giới hạn việc kiểm tra concrete type argument ở runtime. Với một reference quá rộng:

```java
Object value = new ArrayList<String>();
// value instanceof List<String> // compile error
```

Nhưng từ Java 16, một số parameterized `instanceof` vẫn hợp lệ khi static type cho phép checked cast, như `List<Integer> → ArrayList<Integer>`. Phần Type Erasure đã giải thích nuance này; restriction thật sự không phải “mọi parameterized instanceof đều bị cấm”.

## <a id="static-type-parameter-limit">Static Context không dùng Class Type Parameter</a>

```java
class Box<T> {
    // static T cached; // compile error
}
```

`static` member chỉ tồn tại một lần cho class, trong khi `Box<String>`, `Box<Integer>`... không tạo các class runtime riêng có static state riêng cho từng type argument.

Static method vẫn có thể tự generic:

```java
class Box<T> {
    static <U> U identity(U value) {
        return value;
    }
}
```

`U` thuộc method, không thuộc parameterization của `Box`.

Một hệ quả khác của erasure: không thể overload chỉ dựa vào generic arguments nếu các signature erase giống nhau:

```java
// void process(List<String> values) {}
// void process(List<Integer> values) {} // name clash sau erasure
```

## <a id="generic-exception-limit">Giới hạn với Exception</a>

Không thể tạo generic subclass của `Throwable`:

```java
// class Problem<T> extends Exception {} // compile error
```

Không thể catch một type parameter:

```java
// catch (T ex) { ... }
```

Nhưng type parameter có thể xuất hiện trong `throws` khi bound phù hợp:

```java
static <E extends Exception> void run(ThrowingTask<E> task) throws E {
    task.run();
}

interface ThrowingTask<E extends Exception> {
    void run() throws E;
}
```

Điều này cho phép một API truyền thông tin checked-exception type qua compile-time contract mà không tạo generic exception class.

### Tổng kết mô hình Generics

```text
Generic Type / Method
→ tạo quan hệ kiểu tái sử dụng được

Bounds
→ yêu cầu capability tối thiểu

Wildcards + PECS
→ mở rộng API boundary theo hướng đọc/ghi

Invariance
→ bảo vệ mutable generic structures

Raw Types
→ compatibility escape hatch, dễ mất safety

Type Erasure
→ nền triển khai và nguồn của nhiều restriction
```

Khi thiết kế API generic, mục tiêu không phải dùng cú pháp phức tạp nhất mà là **diễn đạt đúng quan hệ kiểu để compiler bảo vệ caller và implementation càng sớm càng tốt**.
