# Generics là gì và vì sao cần?

Module này đứng **trước Collection**, nên chưa cần biết `List`, `Set` hay `Map` hoạt động thế nào. Trước hết chỉ cần giữ một câu hỏi:

```text
Nếu cùng một cách xử lý phải làm việc với nhiều kiểu dữ liệu khác nhau,
làm sao viết cách xử lý đó một lần mà compiler vẫn kiểm tra đúng kiểu?
```

Đó chính là bài toán của **Generics**.

> **Generics là cơ chế của Java cho phép lớp (`class`), giao diện (`interface`), hàm tạo (`constructor`) hoặc phương thức (`method`) khai báo kiểu dữ liệu như một tham số.**
>
> Nhờ vậy ta có thể tái sử dụng cùng một cấu trúc/thuật toán cho nhiều kiểu dữ liệu mà vẫn giữ kiểm tra kiểu ngay khi biên dịch.

`<T>`, wildcard, PECS hay type erasure chỉ là các cơ chế và quy tắc phát triển từ ý tưởng cốt lõi này.

## <a id="generic-type-purpose">Generics là gì và vì sao cần dùng?</a>

### BÀI TOÁN: CÙNG MỘT CÁCH XỬ LÝ NHƯNG NHIỀU KIỂU DỮ LIỆU

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

Hai class gần như giống hệt nhau. Nếu ngày mai cần `UserBox`, `OrderBox`, `ProductBox`, ta lại sao chép cùng một cách xử lý.

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

Nhưng lúc này ta giải quyết được **tái sử dụng mã** bằng cách đánh đổi **thông tin kiểu**:

```java
ObjectBox box = new ObjectBox();
box.set("java");

String value = (String) box.get(); // bên gọi phải ép kiểu
```

Và đây mới là vấn đề lớn:

```java
ObjectBox box = new ObjectBox();
box.set(123);

String value = (String) box.get(); // lúc chạy: ClassCastException
```

Compiler không biết `ObjectBox` “đáng lẽ” phải chứa `String`. Sai kiểu lọt qua bước biên dịch và chỉ phát sinh lỗi khi chương trình chạy.

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

Sai kiểu bị chặn **ngay tại nơi viết mã**, thay vì chờ tới lúc chạy.

### VẬY TẠI SAO PHẢI DÙNG GENERICS?

So sánh trực tiếp:

| Cách làm | Tái sử dụng mã | Compiler biết kiểu cụ thể | Cần ép kiểu | Sai kiểu bị phát hiện |
| --- | --- | --- | --- | --- |
| Viết riêng `StringBox`, `IntegerBox`... | Kém | Có | Không | Khi biên dịch |
| Dùng `Object` | Tốt | Không | Có | Có thể tới lúc chạy |
| Dùng `Box<T>` | **Tốt** | **Có** | **Thường không** | **Khi biên dịch** |

Generics cho ta đồng thời:

- **tái sử dụng cùng một phần triển khai cho nhiều kiểu dữ liệu**;
- **an toàn kiểu (type safety) ngay khi biên dịch**;
- **không phải ép kiểu thủ công ở mọi nơi**;
- **chữ ký (signature) nói rõ dữ liệu đi vào và đi ra có quan hệ kiểu gì**.

Generics không làm Java trở thành ngôn ngữ kiểu động (dynamically typed). Ngược lại, nó giúp compiler kiểm tra quan hệ kiểu chặt hơn trước khi chương trình chạy.

### KHI NÀO NÊN NGHĨ ĐẾN GENERICS?

Khi bạn thấy một đoạn mã có dạng:

```text
"cách xử lý này giống hệt nhau,
chỉ khác kiểu dữ liệu"
```

Generics thường là ứng viên.

Sau này bạn sẽ gặp mẫu cấu trúc này rất nhiều, ví dụ:

```java
List<String>
Set<Long>
Map<String, User>
```

Bạn chưa cần hiểu `List`, `Set` hay `Map` ở đây; module Collection sẽ học chúng ngay sau Generics. Mục đích của ví dụ chỉ là nhận ra dạng `Type<OtherType>`.

Hoặc khi tự thiết kế:

```java
class Box<T> { ... }
interface Repository<ID, T> { ... }
static <T> T first(List<T> values) { ... }
```

Không phải class nào cũng cần Generics. Nếu cách xử lý chỉ làm việc với đúng một kiểu cố định và không có quan hệ kiểu cần biểu diễn, thêm `<T>` chỉ làm mã phức tạp hơn.

### MỘT CÂU CẦN NHỚ

```text
Generics
= viết mã một lần cho nhiều kiểu dữ liệu
+ vẫn giữ an toàn kiểu để compiler kiểm tra giúp mình
```

Bây giờ khi đã biết Generics là gì và vì sao nó tồn tại, ta mới cần học các thuật ngữ dùng để mô tả nó.

### CÁCH HỌC MODULE NÀY: KHÔNG CẦN NUỐT HẾT TRONG MỘT LƯỢT

Generics có hai tầng kiến thức:

```text
LƯỢT 1 — CỐT LÕI, PHẢI DÙNG ĐƯỢC

Generic Type
Generic Method
Tham số kiểu có giới hạn cơ bản
Invariance
Wildcard ? / ? extends / ? super
PECS
Raw Type ở mức nhận diện và tránh dùng sai

→ mục tiêu:
đọc được chữ ký generic phổ biến
viết API generic thông thường
chọn extends/super đúng hướng
hiểu compiler đang bảo vệ điều gì


LƯỢT 2 — ĐÀO SÂU, HIỂU COMPILER / RUNTIME

recursive bound
wildcard capture
heap pollution
type erasure chi tiết
bridge method
reifiable / non-reifiable type
generic array / exception restrictions

→ mục tiêu:
đọc JDK/framework code khó hơn
hiểu cảnh báo unchecked và giới hạn lúc chạy
gỡ lỗi các trường hợp biên của Generics
```

Nếu đang học Generics lần đầu, hãy ưu tiên **lượt 1**. Các phần nâng cao vẫn nằm trong module để kiến thức đầy đủ, nhưng không phải điều kiện để bắt đầu dùng Generics đúng trong code Java hằng ngày.

Lộ trình của module bám theo 8 mốc trong ROADMAP:

```text
Generics giải quyết vấn đề gì và vì sao cần dùng?
Generics là gì và vì sao cần?
        ↓
Khi nào tham số kiểu thuộc về cả kiểu, khi nào chỉ thuộc một phương thức/hàm tạo?
Kiểu generic và phương thức generic
        ↓
Làm sao yêu cầu T có những khả năng tối thiểu mà thuật toán cần?
Tham số kiểu có giới hạn
        ↓
Vì sao List<Dog> không phải List<Animal>?
Tính bất biến và quan hệ kiểu con
        ↓
Làm sao nhận một họ kiểu generic tương thích và chọn extends/super theo chiều dữ liệu?
Wildcard, PECS và chiều đọc/ghi dữ liệu
        ↓
Điều gì xảy ra khi kiểu thô làm suy yếu các đảm bảo generic?
Kiểu thô và ranh giới mã cũ
        ↓
Thông tin generic nào dùng khi biên dịch và thông tin nào còn lại lúc chạy?
Xóa kiểu (Type Erasure) và mô hình lúc chạy
        ↓
Những giới hạn và đánh đổi nào cần cân nhắc khi thiết kế API?
Giới hạn của Generics và thiết kế API
```

Các thuật ngữ chính sẽ lần lượt được giải thích:

```text
type parameter
→ "ô kiểu" được lớp/giao diện/phương thức/hàm tạo generic khai báo

type argument
→ kiểu thật được điền vào ô đó khi sử dụng

bounded type parameter
→ giới hạn những kiểu nào được phép điền vào

invariance
→ giải thích vì sao List<Dog> không tự trở thành List<Animal>

wildcard
→ nói về một type argument chưa biết chính xác ở nơi sử dụng

PECS
→ cách suy luận ? extends / ? super theo hướng đọc và ghi dữ liệu

type erasure
→ giải thích Generics được compiler/JVM xử lý thế nào ở runtime
```

## <a id="type-parameter">Tham số kiểu (Type Parameter) và đối số kiểu (Type Argument)</a>

Trong khai báo:

```java
class Box<T> { ... }
```

`T` là **tham số kiểu (type parameter)**: một biến kiểu (type variable) thuộc khai báo `Box`.

Trong:

```java
Box<String> textBox = new Box<>();
Box<Integer> numberBox = new Box<>();
```

`String` và `Integer` là **đối số kiểu (type argument)**.

Có thể có nhiều tham số kiểu:

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

Đây chỉ là quy ước đặt tên. Compiler không gán ý nghĩa đặc biệt cho các tên này.

Một kiểu được tham số hóa (parameterized type) như `Box<String>` được dùng trong kiểm tra khi biên dịch. Nó không có nghĩa JVM tạo một class bytecode hoàn toàn mới riêng cho mỗi type argument; phần đó sẽ được giải thích ở chương Type Erasure.

Chương tiếp theo chuyển từ **“Generics là gì?”** sang **“khai báo và sử dụng kiểu generic/phương thức generic như thế nào?”**.
