# Generics và Generic Type

Module này đứng **trước Collection**, nên chưa cần biết `List`, `Set` hay `Map` hoạt động thế nào. Trước hết chỉ cần giữ một câu hỏi:

```text
Nếu cùng một logic phải làm việc với nhiều kiểu dữ liệu khác nhau,
làm sao viết logic đó một lần mà compiler vẫn kiểm tra đúng kiểu?
```

Đó chính là bài toán của **Generics**.

> **Generics là cơ chế của Java cho phép class, interface, constructor hoặc method khai báo kiểu dữ liệu như một tham số.**
>
> Nhờ vậy ta có thể tái sử dụng cùng một cấu trúc/thuật toán cho nhiều kiểu dữ liệu mà vẫn giữ kiểm tra kiểu ở compile time.

`<T>`, wildcard, PECS hay type erasure chỉ là các cơ chế và quy tắc phát triển từ ý tưởng cốt lõi này.

## <a id="generic-type-purpose">Generics là gì và vì sao cần dùng?</a>

### BÀI TOÁN: CÙNG MỘT LOGIC NHƯNG NHIỀU KIỂU DỮ LIỆU

Giả sử ta cần một chiếc hộp chỉ có nhiệm vụ lưu một giá trị rồi trả lại giá trị đó.

Nếu không có Generics, cách đầu tiên là viết riêng cho từng kiểu:

```java
final class StringBox {
    private String value;
    void set(String value) { this.value = value; }
    String get() { return value; }
}

final class IntegerBox {
    private Integer value;
    void set(Integer value) { this.value = value; }
    Integer get() { return value; }
}
```

Hai class gần như giống hệt nhau. Nếu ngày mai cần `UserBox`, `OrderBox`, `ProductBox`, ta lại sao chép cùng một logic.

Ta có thể tránh lặp bằng `Object`:

```java
final class ObjectBox {
    private Object value;

    void set(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}
```

Nhưng lúc này ta giải quyết được **tái sử dụng code** bằng cách đánh đổi **thông tin kiểu**:

```java
ObjectBox box = new ObjectBox();
box.set("java");

String value = (String) box.get(); // caller phải cast
```

Và đây mới là vấn đề lớn:

```java
ObjectBox box = new ObjectBox();
box.set(123);

String value = (String) box.get(); // runtime: ClassCastException
```

Compiler không biết `ObjectBox` “đáng lẽ” phải chứa `String`. Sai kiểu lọt qua compile và chỉ nổ khi chương trình chạy.

### GENERICS GIẢI CẢ HAI BÀI TOÁN

Ta thay kiểu cụ thể bằng một **ô kiểu giữ chỗ**:

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

`T` ở đây có thể hiểu đơn giản là:

```text
Box<T>
    ↑
    chỗ trống cho "kiểu dữ liệu mà Box sẽ chứa"
```

Khi sử dụng, ta điền kiểu thật vào chỗ trống đó:

```java
Box<String> textBox = new Box<>();
textBox.set("java");
String text = textBox.get();

Box<Integer> numberBox = new Box<>();
numberBox.set(100);
Integer number = numberBox.get();
```

Chỉ có **một** class `Box<T>`, nhưng dùng được cho cả `String`, `Integer`, `User`, `Order`...

Đồng thời compiler vẫn biết mỗi box đang chứa kiểu gì:

```java
Box<String> box = new Box<>();

box.set("java"); // OK
// box.set(123); // compile error
```

Sai kiểu bị chặn **ngay tại nơi viết code**, thay vì chờ tới runtime.

### VẬY TẠI SAO PHẢI DÙNG GENERICS?

So sánh trực tiếp:

| Cách làm | Tái sử dụng code | Compiler biết kiểu cụ thể | Cần cast | Sai kiểu bị phát hiện |
| --- | --- | --- | --- | --- |
| Viết riêng `StringBox`, `IntegerBox`... | Kém | Có | Không | Compile time |
| Dùng `Object` | Tốt | Không | Có | Có thể tới runtime |
| Dùng `Box<T>` | **Tốt** | **Có** | **Thường không** | **Compile time** |

Generics cho ta đồng thời:

- **tái sử dụng cùng một implementation cho nhiều kiểu dữ liệu**;
- **type safety ở compile time**;
- **không phải cast thủ công ở mọi nơi**;
- **signature nói rõ dữ liệu đi vào và đi ra có quan hệ kiểu gì**.

Generics không làm cho Java trở thành dynamically typed. Ngược lại, nó giúp compiler kiểm tra quan hệ kiểu chặt hơn trước khi chương trình chạy.

### KHI NÀO NÊN NGHĨ ĐẾN GENERICS?

Khi bạn thấy một đoạn code có dạng:

```text
"logic này giống hệt nhau,
chỉ khác kiểu dữ liệu"
```

Generics thường là ứng viên.

Sau này bạn sẽ gặp pattern này rất nhiều, ví dụ:

```java
List<String>
Set<Long>
Map<String, User>
```

Bạn chưa cần hiểu `List`, `Set` hay `Map` ở đây; module Collection sẽ học chúng ngay sau Generics. Mục đích của ví dụ chỉ là nhận ra pattern `Type<OtherType>`.

Hoặc khi tự thiết kế:

```java
class Box<T> { ... }
interface Repository<ID, T> { ... }
static <T> T first(List<T> values) { ... }
```

Không phải class nào cũng cần Generics. Nếu logic chỉ làm việc với đúng một kiểu cố định và không có quan hệ kiểu cần biểu diễn, thêm `<T>` chỉ làm code phức tạp hơn.

### MỘT CÂU CẦN NHỚ

```text
Generics
= viết code một lần cho nhiều kiểu dữ liệu
+ vẫn giữ type safety để compiler kiểm tra giúp mình
```

Bây giờ khi đã biết Generics là gì và vì sao nó tồn tại, ta mới cần học các thuật ngữ dùng để mô tả nó.

### CÁCH HỌC MODULE NÀY: KHÔNG CẦN NUỐT HẾT TRONG MỘT LƯỢT

Generics có hai tầng kiến thức:

```text
LƯỢT 1 — CORE, PHẢI DÙNG ĐƯỢC

Generic Type
Generic Method
Bounded Type cơ bản
Wildcard ? / ? extends / ? super
PECS
Invariance
Raw Type ở mức nhận diện và tránh dùng sai

→ mục tiêu:
đọc được signature generic phổ biến
viết API generic thông thường
chọn extends/super đúng hướng
hiểu compiler đang bảo vệ điều gì


LƯỢT 2 — DEEP, HIỂU COMPILER / RUNTIME

recursive bound
wildcard capture
heap pollution
type erasure chi tiết
bridge method
reifiable / non-reifiable type
generic array / exception restrictions

→ mục tiêu:
đọc JDK/framework code khó hơn
hiểu unchecked warning và runtime limitation
debug các generic edge case
```

Nếu đang học Generics lần đầu, hãy ưu tiên **lượt 1**. Các section nâng cao vẫn nằm trong module để kiến thức đầy đủ, nhưng không phải điều kiện để bắt đầu dùng Generics đúng trong code Java hằng ngày.

Lộ trình của module:

```text
Kiểu dữ liệu trở thành "tham số" của class/interface thế nào?
Generic Type
        ↓
Một method có thể tự khai báo type variable không?
Generic Method
        ↓
Làm sao yêu cầu T có một khả năng tối thiểu?
Bounded Type
        ↓
Làm sao nhận một họ generic type mà không biết chính xác type argument?
Wildcards
        ↓
Đọc và ghi ảnh hưởng wildcard direction thế nào?
PECS
        ↓
Vì sao List<Dog> không phải List<Animal>?
Invariance
        ↓
Điều gì xảy ra khi raw type bỏ qua generic safety?
Raw Types
        ↓
Generic type information còn lại gì ở runtime?
Type Erasure
        ↓
Những giới hạn nào xuất hiện từ erasure và non-reifiable types?
Generic Limitations
```

Các thuật ngữ chính sẽ lần lượt được giải thích:

```text
type parameter
→ "ô kiểu" được generic class/interface/method khai báo

type argument
→ kiểu thật được điền vào ô đó khi sử dụng

bounded type parameter
→ giới hạn những kiểu nào được phép điền vào

wildcard
→ nói về một type argument chưa biết chính xác ở nơi sử dụng

PECS
→ cách suy luận ? extends / ? super theo hướng đọc và ghi dữ liệu

invariance
→ giải thích vì sao List<Dog> không tự trở thành List<Animal>

type erasure
→ giải thích Generics được compiler/JVM xử lý thế nào ở runtime
```

## <a id="type-parameter">Type Parameter và Type Argument</a>

Trong khai báo:

```java
class Box<T> { ... }
```

`T` là **type parameter**: một type variable thuộc declaration `Box`.

Trong:

```java
Box<String> textBox = new Box<>();
Box<Integer> numberBox = new Box<>();
```

`String` và `Integer` là **type argument**.

Có thể có nhiều type parameter:

```java
final class Pair<K, V> {
    private K key;
    private V value;
}
```

Tên thường dùng:

| Tên | Ý nghĩa thường gặp |
| --- | --- |
| `T` | Type |
| `E` | Element |
| `K` | Key |
| `V` | Value |
| `R` | Result |

Đây chỉ là convention. Compiler không gán ý nghĩa đặc biệt cho các tên này.

Một parameterized type như `Box<String>` là một kiểu dùng trong kiểm tra compile-time. Nó không có nghĩa JVM tạo một class bytecode hoàn toàn mới riêng cho mỗi type argument; phần đó sẽ được giải thích ở chương Type Erasure.

Generic type không chỉ là generic class. Interface cũng có thể khai báo type parameter:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

`ID` và `T` đều là type parameter của interface. Một implementation có thể tiếp tục giữ chúng generic hoặc cố định chúng thành type argument cụ thể.

## <a id="diamond-operator">Diamond Operator — &lt;&gt;</a>

Khi viết:

```java
Box<String> box = new Box<>();
```

phần `<String>` bên trái nói rõ variable có kiểu `Box<String>`. Ở phía `new Box<>()`, cặp `<>` rỗng được gọi không chính thức là **diamond operator**.

Compiler suy luận type argument từ context:

```java
Box<String> box = new Box<>();
// tương đương về type với:
Box<String> other = new Box<String>();
```

Diamond không có nghĩa “raw type”. Hai dòng sau khác nhau:

```java
Box<String> safe = new Box<>(); // generic, compiler suy luận String
Box<String> risky = new Box();  // raw Box -> unchecked conversion warning
```

Với người mới học, quy tắc thực tế rất đơn giản: nếu compiler suy luận được type argument khi tạo generic object, ưu tiên `<>` thay vì lặp lại type argument.

## <a id="generic-invariance-intro">Giới thiệu Invariance</a>

Giả sử:

```java
class Animal {}
class Dog extends Animal {}
```

`Dog` là subtype của `Animal`, nhưng:

```java
Box<Dog> dogs = new Box<>();
// Box<Animal> animals = dogs; // compile error
```

`Box<Dog>` **không** tự động là subtype của `Box<Animal>`.

Lý do trực giác:

```java
Box<Animal> animals = dogs; // giả sử được phép
animals.set(new Animal());  // hợp lệ theo Box<Animal>
```

Khi đó một `Box<Dog>` lại có thể chứa `Animal` không phải `Dog`, phá vỡ guarantee ban đầu.

Đây là **invariance**. Wildcard sẽ cung cấp cách diễn tả quan hệ linh hoạt hơn mà không phá type safety.

## <a id="generic-api-design">Thiết kế API Generic</a>

Generic type nên biểu diễn **một quan hệ kiểu có ý nghĩa**, không chỉ thay `Object` một cách máy móc.

Ví dụ repository đơn giản:

```java
interface Repository<ID, T> {
    T findById(ID id);
    void save(T value);
}
```

Signature cho caller biết:

- kiểu id là gì;
- repository đọc/trả về kiểu gì;
- giá trị nào được phép ghi.

Một số nguyên tắc thực tế:

- dùng type parameter khi nhiều member cần chia sẻ cùng một type identity;
- tránh trả `Object` nếu API thật sự biết kiểu;
- giữ số type parameter vừa đủ để signature còn đọc được;
- nếu caller chỉ cần “một kiểu chưa biết nào đó”, wildcard thường hợp lý hơn việc thêm một type parameter không tạo quan hệ nào;
- đừng dùng raw type để “cho ngắn”.

Chương tiếp theo thu nhỏ phạm vi: thay vì cả class mang `T`, **một method riêng lẻ có thể tự khai báo type parameter như thế nào?**
