# Kiểu generic và phương thức generic

Chương trước đã giới thiệu `T` như một tham số kiểu. Bây giờ ta dùng nó ở hai phạm vi khác nhau:

```text
kiểu generic
→ lớp/giao diện sở hữu type parameter
→ nhiều thành viên cùng chia sẻ quan hệ kiểu đó

phương thức generic
→ riêng phương thức sở hữu type parameter
→ quan hệ kiểu chỉ tồn tại trong thao tác đó
```

## <a id="generic-type-declaration">Khai báo và sử dụng kiểu generic</a>

Một lớp generic khai báo type parameter ngay sau tên lớp:

```java
final class Box<T> {
    private T value;

    void set(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Khi sử dụng, một đối số kiểu cụ thể tạo ra một **kiểu được tham số hóa (parameterized type)**:

```java
Box<String> textBox = new Box<>();
Box<Integer> numberBox = new Box<>();
```

Kiểu generic không chỉ là `class`. `interface` cũng có thể khai báo type parameter:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

Lớp triển khai có thể tiếp tục dùng tham số kiểu hoặc cố định chúng thành kiểu cụ thể:

```java
class User {}

final class UserRepository implements Repository<Long, User> {
    @Override
    public User findById(Long id) {
        return null;
    }

    @Override
    public void save(User value) {
    }
}
```

`ID` và `T` đều là type parameter của `Repository`. Một lớp triển khai khác cũng có thể tiếp tục giữ chúng generic thay vì cố định ngay thành `Long` và `User`.

Điểm quan trọng không phải cú pháp `<T>` tự nó, mà là **một quan hệ kiểu được sở hữu bởi khai báo và dùng nhất quán qua các thành viên**.

## <a id="diamond-operator">Toán tử kim cương (Diamond Operator) — &lt;&gt;</a>

Khi viết:

```java
Box<String> box = new Box<>();
```

phần `<String>` bên trái nói rõ biến có kiểu `Box<String>`. Ở phía `new Box<>()`, cặp `<>` rỗng được gọi không chính thức là **diamond operator**.

Compiler suy luận **đối số kiểu của lớp generic đang được khởi tạo** từ ngữ cảnh:

```java
Box<String> box = new Box<>();
// tương đương về type với:
Box<String> other = new Box<String>();
```

Diamond không có nghĩa “raw type”, và cũng không phải type parameter riêng của một generic constructor. Hai dòng sau khác nhau:

```java
Box<String> safe = new Box<>(); // generic, compiler suy luận String
Box<String> risky = new Box();  // raw Box -> unchecked conversion warning
```

Với người mới học, quy tắc thực tế rất đơn giản: nếu compiler suy luận được type argument khi tạo đối tượng generic, ưu tiên `<>` thay vì lặp lại type argument.

### Khi chỉ một phương thức cần generic

`Box<T>` cần type parameter vì **cả đối tượng** làm việc với cùng một kiểu `T`. Nhưng có nhiều trường hợp lớp hoàn toàn không cần generic; chỉ **một phương thức** có cùng cách xử lý cho nhiều kiểu dữ liệu.

Ví dụ nếu không có phương thức generic, ta có thể bị kéo tới các phương thức lặp:

```java
static String echoString(String value) { return value; }
static Integer echoInteger(Integer value) { return value; }
```

Cách xử lý không đổi; chỉ kiểu dữ liệu đổi. Không có lý do để biến cả lớp tiện ích thành `Util<T>` chỉ vì một thao tác như vậy.

**Generic Method** cho phép chính phương thức khai báo “ô kiểu” riêng:

```java
static <T> T echo(T value) {
    return value;
}
```

Một phương thức duy nhất dùng được với `String`, `Integer`, `User`... và compiler vẫn giữ quan hệ: **đưa vào T thì trả về T**.

## <a id="generic-method-syntax">Cú pháp phương thức generic</a>

Danh sách type parameter được đặt **trước kiểu trả về (return type)**:

```java
static <T> T echo(T value) {
    return value;
}
```

Ở đây:

- `<T>` khai báo type parameter của phương thức;
- `T` trong tham số và kiểu trả về cùng chỉ một type variable;
- phương thức có thể nằm trong lớp generic hoặc lớp không generic.

Ví dụ phương thức dùng cùng một `T` cho tham số đầu vào và kiểu trả về:

```java
static <T> Box<T> boxOf(T value) {
    Box<T> box = new Box<>();
    box.set(value);
    return box;
}
```

Phương thức generic hữu ích khi **quan hệ kiểu chỉ tồn tại trong một thao tác** chứ không phải trạng thái của đối tượng.

## <a id="generic-constructor">Hàm tạo generic (Generic Constructor)</a>

Hàm tạo cũng có thể tự khai báo type parameter, kể cả khi lớp không generic:

```java
class Message {
    <T> Message(T source) {
        System.out.println(source);
    }
}
```

`T` ở đây thuộc **hàm tạo**, không thuộc lớp `Message`.

Khi gọi:

```java
new Message("java"); // T được suy luận là String
new Message(123);    // T được suy luận là Integer
```

Bên gọi thường không phải ghi type argument tường minh cho hàm tạo; compiler suy luận từ đối số.

Lớp generic cũng có thể có hàm tạo với một type parameter độc lập:

```java
class Box<T> {
    <U> Box(U initialMetadata) {
        // T thuộc lớp, U thuộc hàm tạo
    }
}
```

Đây không phải mẫu cần dùng thường xuyên. Điểm cần hiểu là phạm vi của type parameter phụ thuộc nơi nó được khai báo: lớp/giao diện, hàm tạo hoặc phương thức có thể có type parameter riêng.

## <a id="type-inference">Suy luận kiểu (Type Inference)</a>

Thông thường bên gọi không cần viết type argument cho phương thức:

```java
String name = echo("java");
Integer number = echo(100);
```

Compiler suy luận `T` từ:

- kiểu của đối số;
- kiểu đích ở ngữ cảnh gán hoặc trả về;
- các ràng buộc khác trong lời gọi.

Có thể chỉ định **type witness** tường minh khi cần:

```java
String value = Util.<String>echo("java");
```

Nhưng nếu suy luận kiểu đã rõ, cách viết này thường dư thừa.

Suy luận kiểu không phải “đoán kiểu lúc chạy”. Compiler phải tìm được một kiểu thỏa mọi ràng buộc trước khi sinh bytecode.

## <a id="static-generic-method">Phương thức static generic</a>

Phương thức `static` không gắn với một đối tượng cụ thể, nhưng nó hoàn toàn có thể generic nếu **phương thức tự khai báo type parameter**:

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

`U` thuộc phương thức `static`; nó không phụ thuộc vào type parameter `T` của từng parameterization của `Container`.

## <a id="generic-method-vs-type">Tham số kiểu của phương thức và của lớp</a>

Trong:

```java
class Holder<T> {
    T current;

    <R> R echoOther(R value) {
        return value;
    }
}
```

- `T` thuộc lớp và có ý nghĩa xuyên suốt đối tượng;
- `R` chỉ tồn tại trong khai báo của `echoOther`.

Phương thức cũng có thể khai báo một type parameter trùng tên với type parameter của lớp, nhưng khi đó nó **che khuất (shadow)** type variable bên ngoài và rất dễ gây nhầm:

```java
class Sample<T> {
    <T> T identity(T value) {
        return value;
    }
}
```

Hai `T` ở đây là hai type variable khác nhau. Nên chọn tên khác nếu không có lý do mạnh.

Khi mã generic cần gọi phương thức chỉ có trên một nhóm kiểu nhất định, `T` tự do là chưa đủ. Chương tiếp theo thêm **giới hạn (bound)** để nói rõ khả năng mà type parameter phải có.
