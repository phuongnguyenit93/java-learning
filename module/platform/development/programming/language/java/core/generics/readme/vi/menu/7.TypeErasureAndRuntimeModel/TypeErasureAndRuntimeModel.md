# Xóa kiểu (Type Erasure) và mô hình lúc chạy

Đến đây compiler có vẻ biết rất nhiều thông tin kiểu:

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Nhưng nếu nhìn ở lúc chạy:

```java
System.out.println(texts.getClass() == numbers.getClass()); // true
```

Nếu `String` và `Integer` quan trọng với Generics, **tại sao lúc chạy lại thấy cùng một class `ArrayList`?**

Câu trả lời là Java Generics chủ yếu được kiểm tra **khi biên dịch**. Compiler dùng type argument để kiểm tra mã nguồn, rồi dịch các khai báo/lời gọi generic sang bytecode tương thích với mô hình class cũ của JVM.

Cơ chế dịch đó được gọi là **type erasure**.

Hiểu erasure giúp nối nhiều câu hỏi tưởng như không liên quan:

- vì sao `List<String>` và `List<Integer>` có cùng class ở lúc chạy;
- vì sao một tham chiếu rộng như `Object` không thể tùy ý được kiểm tra thành `List<String>`;
- vì sao compiler đôi khi chèn phép ép kiểu;
- vì sao xuất hiện bridge method;
- vì sao nhiều giới hạn ở chương sau tồn tại.

## <a id="erasure-model">Mô hình xóa kiểu (Type Erasure)</a>

Một mô hình tư duy hữu ích:

```text
mã nguồn có T / List<String> / bounds
        ↓
compiler kiểm tra các ràng buộc generic
        ↓
type parameter như T được xóa về bound hoặc Object
parameterized type như List<String> được xóa về List
        ↓
compiler chèn phép ép kiểu khi cần
        ↓
bytecode làm việc với các kiểu runtime thông thường
```

Cần tách hai quy tắc:

```text
T
→ erasure là leftmost bound nếu có
→ nếu không có bound rõ ràng thì thường là Object

List<String>
→ type argument String bị xóa khỏi kiểu dùng cho thực thi
→ erasure của parameterized type là List
```

Vì vậy “erasure về `Object`” **không áp dụng cho mọi generic type**. Nó chủ yếu mô tả trường hợp type variable không có bound cụ thể; còn một parameterized type như `List<String>` được xóa về raw type tương ứng là `List`.

Ví dụ:

```java
class Box<T> {
    T value;
    T get() { return value; }
}
```

Ở mức khái niệm, khi `T` không có bound thì erasure gần với:

```java
class Box {
    Object value;
    Object get() { return value; }
}
```

Bên gọi dùng kiểu cụ thể:

```java
String s = box.get();
```

được compiler bảo vệ bằng kiểm tra generic và phép ép kiểu thích hợp trong bytecode.

Nếu `T extends Number`, erasure của `T` dựa trên leftmost bound `Number`, không phải luôn luôn `Object`.

Với nhiều bound, quy tắc vẫn dựa trên **bound đứng đầu**:

```java
<T extends Number & Comparable<T>>
```

thì erasure của `T` là `Number`. Đây chính là lý do chapter Bounded Type Parameters yêu cầu class bound, nếu có, phải đứng trước các interface bound.

## <a id="erased-runtime-type">Kiểu generic ở lúc chạy</a>

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

System.out.println(texts.getClass() == numbers.getClass()); // true
```

Cả hai đối tượng ở lúc chạy đều là instance của cùng class `ArrayList`; JVM không tạo class `ArrayList<String>` và `ArrayList<Integer>` riêng.

Vì vậy lúc chạy không thể lấy một tham chiếu bất kỳ rồi hỏi “đối tượng này có thật sự là `List<String>` không?”:

```java
Object value = new ArrayList<String>();
// if (value instanceof List<String>) { } // compile error
```

Ở lượt học đầu, chỉ cần giữ hai ý tách biệt:

- erasure làm đối tượng ở lúc chạy không mang type argument cụ thể theo cách cho phép kiểm tra tùy ý kiểu phần tử;
- class file có thể giữ **siêu dữ liệu chữ ký generic (generic signature metadata)** của khai báo; module Reflection sẽ học cách đọc metadata đó.

## <a id="generic-signature-metadata">Siêu dữ liệu chữ ký Generic còn lại sau xóa kiểu</a>

Type erasure không có nghĩa **mọi dấu vết của Generics đều biến mất khỏi class file**.

Ví dụ một field được khai báo:

```java
List<String> names;
```

bytecode thực thi chủ yếu làm việc với kiểu đã xóa `List`, nhưng class file có thể đồng thời giữ chữ ký generic mô tả `List<String>` để compiler, Reflection và các công cụ khác đọc lại.

Vì vậy cần tách hai lớp thông tin:

```text
kiểu dùng cho thực thi lúc chạy
→ chủ yếu là kiểu sau erasure

generic signature metadata
→ siêu dữ liệu mô tả type parameter / type argument của khai báo
→ có thể còn được lưu trong class file
```

Reflection có các kiểu biểu diễn như `ParameterizedType`, `TypeVariable` và `WildcardType` để đọc metadata này. Cơ chế Reflection cụ thể thuộc module Reflection; ở đây chỉ cần giữ mô hình tư duy rằng **phần thực thi đã bị erasure và metadata generic còn lưu lại có thể cùng tồn tại**.

## <a id="bridge-method">Phương thức cầu nối (Bridge Method)</a>

Đây là **phần nâng cao về cách compiler triển khai Generics**. Không cần nhớ cách bridge method được sinh để sử dụng Generics hằng ngày; cần hiểu nó khi đọc bytecode/Reflection hoặc giải thích vì sao đa hình (polymorphism) vẫn đúng sau erasure.

Erasure có thể làm chữ ký lúc chạy của phương thức override không còn khớp trực tiếp. Compiler tạo **synthetic bridge method** để giữ hành vi đa hình.

```java
interface Mapper<T> {
    T map(T value);
}

class StringMapper implements Mapper<String> {
    @Override
    public String map(String value) {
        return value.trim();
    }
}
```

Sau erasure, phương thức của interface ở mức runtime gần với:

```java
Object map(Object value)
```

Trong khi phương thức triển khai thật là:

```java
String map(String value)
```

Ở mức khái niệm, compiler có thể sinh bridge method như:

```java
public Object map(Object value) {
    return map((String) value);
}
```

Reflection có thể quan sát phương thức synthetic/bridge này. Nó không phải phương thức do lập trình viên tự viết.

## <a id="non-reifiable-types">Kiểu reifiable và non-reifiable</a>

Đây cũng là **phần nâng cao về lúc chạy**. Ở lượt đầu, chỉ cần nhớ quy tắc thực tế: JVM không phải lúc nào cũng kiểm tra được type argument cụ thể; vì thế mảng generic, phép ép kiểu và một số kiểm tra runtime có giới hạn.

**Reifiable type** là kiểu có đủ thông tin biểu diễn ở runtime cho các thao tác cần kiểm tra kiểu lúc chạy.

Ví dụ reifiable:

```text
String
List
List<?>
int
String[]
List<?>[]
```

Ví dụ non-reifiable:

```text
List<String>
List<Integer>
T
List<String>[]
```

Reifiability quan trọng với những thao tác phụ thuộc vào thông tin kiểu ở runtime, đặc biệt là tạo mảng và các runtime type check. Riêng với phép ép kiểu, **target non-reifiable có thể dẫn tới unchecked cast chính vì JVM không thể xác minh đầy đủ generic target đó**. Generic varargs cũng có thể tạo ranh giới tương tự, nơi compiler không chứng minh được toàn bộ quan hệ kiểu và heap pollution có thể xuất hiện.

### Ghi chú nâng cao — `instanceof` với parameterized type từ Java 16+

Không nên học thuộc quy tắc quá rộng rằng “parameterized type không bao giờ được xuất hiện sau `instanceof`”. Ví dụ sau hợp lệ:

```java
List<Integer> values = new ArrayList<>();

if (values instanceof ArrayList<Integer>) {
    // hợp lệ
}
```

Điều này **không** có nghĩa JVM vừa kiểm tra từng phần tử có phải `Integer` hay không. Kiểu tĩnh `List<Integer>` đã mang ràng buộc `Integer`; phép `instanceof` ở đây chỉ thu hẹp class lúc chạy từ `List` sang `ArrayList`.

Từ Java 16+, `instanceof` có thể dùng một số parameterized type khi tồn tại phép ép kiểu có thể kiểm tra (checked casting conversion) từ kiểu tĩnh của biểu thức sang kiểu được kiểm tra. Vì vậy `non-reifiable` **không đồng nghĩa** với “luôn bị cấm sau `instanceof`”.

Một unchecked cast có thể biên dịch được nhưng compiler không thể kiểm tra đầy đủ type argument ở runtime:

```java
List<String> names = (List<String>) value;
```

Vì vậy unchecked cast phải được xem như một **ranh giới tin cậy**, không phải bằng chứng rằng JVM đã kiểm tra đầy đủ generic argument.

Erasure giải thích nhiều giới hạn tưởng như rời rạc của Generics. Chương cuối gom các giới hạn quan trọng và chỉ ra cách thiết kế thay thế an toàn.
