# Generic Method

Ở chương trước, `Box<T>` cần type parameter vì **cả object** làm việc với cùng một kiểu `T`. Nhưng có nhiều trường hợp class hoàn toàn không cần generic; chỉ **một method** có logic giống nhau cho nhiều kiểu dữ liệu.

Ví dụ nếu không có generic method, ta có thể bị kéo tới các method lặp:

```java
static String echoString(String value) { return value; }
static Integer echoInteger(Integer value) { return value; }
```

Logic không đổi; chỉ kiểu dữ liệu đổi. Không có lý do để biến cả utility class thành `Util<T>` chỉ vì một operation như vậy.

**Generic Method** cho phép chính method khai báo “ô kiểu” riêng:

```java
static <T> T echo(T value) {
    return value;
}
```

Một method duy nhất dùng được với `String`, `Integer`, `User`... và compiler vẫn giữ quan hệ: **đưa vào T thì trả về T**.

## <a id="generic-method-syntax">Cú pháp Generic Method</a>

Type parameter list được đặt **trước return type**:

```java
static <T> T echo(T value) {
    return value;
}
```

Ở đây:

- `<T>` khai báo type parameter của method;
- `T` trong parameter và return type cùng chỉ một type variable;
- method có thể nằm trong generic class hoặc non-generic class.

Ví dụ method nhận hai giá trị cùng kiểu:

```java
static <T> Box<T> boxOf(T value) {
    Box<T> box = new Box<>();
    box.set(value);
    return box;
}
```

Generic method hữu ích khi **quan hệ kiểu chỉ tồn tại trong một operation** chứ không phải state của object.

## <a id="generic-constructor">Generic Constructor</a>

Constructor cũng có thể tự khai báo type parameter, kể cả khi class không generic:

```java
class Message {
    <T> Message(T source) {
        System.out.println(source);
    }
}
```

`T` ở đây thuộc **constructor**, không thuộc class `Message`.

Khi gọi:

```java
new Message("java"); // T được suy luận là String
new Message(123);    // T được suy luận là Integer
```

Caller thường không phải ghi explicit type argument cho constructor; compiler suy luận từ argument.

Generic class cũng có thể có constructor với một type parameter độc lập:

```java
class Box<T> {
    <U> Box(U initialMetadata) {
        // T thuộc class, U thuộc constructor
    }
}
```

Đây không phải pattern cần dùng thường xuyên. Điểm cần hiểu là phạm vi của type parameter phụ thuộc nơi nó được khai báo: class/interface, constructor hoặc method có thể có type parameter riêng.

## <a id="type-inference">Type Inference</a>

Thông thường caller không cần viết type argument cho method:

```java
String name = echo("java");
Integer number = echo(100);
```

Compiler suy luận `T` từ:

- kiểu của argument;
- target type ở ngữ cảnh gán/return;
- các constraint khác trong lời gọi.

Có thể chỉ định explicit type witness khi cần:

```java
String value = Util.<String>echo("java");
```

Nhưng nếu inference đã rõ, cách viết này thường dư thừa.

Type inference không phải runtime guessing. Compiler phải tìm được một kiểu thỏa mọi constraint trước khi sinh bytecode.

## <a id="static-generic-method">Static Generic Method</a>

Static method không gắn với một instance cụ thể, nhưng nó hoàn toàn có thể generic nếu **method tự khai báo type parameter**:

```java
final class Boxes {
    static <T> Box<T> of(T value) {
        Box<T> box = new Box<>();
        box.set(value);
        return box;
    }
}
```

Điểm quan trọng:

```java
class Container<T> {
    // static T value;              // không hợp lệ
    static <U> U identity(U value) { // hợp lệ
        return value;
    }
}
```

`U` thuộc static method; nó không phụ thuộc vào type parameter `T` của từng parameterization của `Container`.

## <a id="generic-method-vs-type">Type Parameter của Method và của Class</a>

Trong:

```java
class Holder<T> {
    T current;

    <R> R echoOther(R value) {
        return value;
    }
}
```

- `T` thuộc class và có ý nghĩa xuyên suốt instance;
- `R` chỉ tồn tại trong declaration của `echoOther`.

Method cũng có thể khai báo một type parameter trùng tên với class type parameter, nhưng khi đó nó **shadow** type variable bên ngoài và rất dễ gây nhầm:

```java
class Sample<T> {
    <T> T identity(T value) {
        return value;
    }
}
```

Hai `T` ở đây là hai type variable khác nhau. Nên chọn tên khác nếu không có lý do mạnh.

Khi generic code cần gọi method chỉ có trên một nhóm kiểu nhất định, `T` tự do là chưa đủ. Chương tiếp theo thêm **bound** để nói rõ capability mà type parameter phải có.
