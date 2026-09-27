# Type Erasure

Đến đây compiler có vẻ biết rất nhiều:

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Nhưng thử hỏi runtime:

```java
System.out.println(texts.getClass() == numbers.getClass()); // true
```

Nếu `String` và `Integer` quan trọng với Generics, **tại sao runtime lại thấy cùng một `ArrayList` class?**

Câu trả lời là Java Generics chủ yếu được kiểm tra ở **compile time**. Compiler dùng type arguments để kiểm tra code, rồi dịch generic declarations/calls sang bytecode tương thích với mô hình class cũ của JVM.

Cơ chế dịch đó được gọi là **type erasure**.

Hiểu erasure giúp nối nhiều câu hỏi tưởng như không liên quan:

- vì sao `List<String>` và `List<Integer>` có cùng runtime class;
- vì sao một reference rộng như `Object` không thể tùy ý được kiểm tra thành `List<String>`;
- vì sao compiler đôi khi chèn cast;
- vì sao xuất hiện bridge method;
- vì sao nhiều giới hạn ở chương sau tồn tại.

## <a id="erasure-model">Mô hình Type Erasure</a>

Một mô hình tư duy hữu ích:

```text
source code có T / List<String> / bounds
        ↓
compiler kiểm tra generic constraints
        ↓
type parameters được erase về bound hoặc Object
        ↓
compiler chèn cast khi cần
        ↓
bytecode làm việc với ordinary runtime types
```

Ví dụ:

```java
class Box<T> {
    T value;
    T get() { return value; }
}
```

Về mặt conceptual erasure khi `T` không bound:

```java
class Box {
    Object value;
    Object get() { return value; }
}
```

Caller typed:

```java
String s = box.get();
```

được compiler bảo vệ bằng generic checking và cast thích hợp trong bytecode.

Nếu `T extends Number`, erasure của `T` dựa trên leftmost bound `Number`, không phải luôn luôn `Object`.

## <a id="erased-runtime-type">Generic Type ở Runtime</a>

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

System.out.println(texts.getClass() == numbers.getClass()); // true
```

Cả hai object runtime là instance của cùng `ArrayList` class; JVM không tạo `ArrayList<String>` class và `ArrayList<Integer>` class riêng.

Vì vậy runtime không thể lấy một reference bất kỳ rồi hỏi “object này có thật sự là `List<String>` không?”:

```java
Object value = new ArrayList<String>();
// if (value instanceof List<String>) { } // compile error
```

Tuy nhiên, từ Java 16 trở đi cần tránh học một rule quá rộng rằng “parameterized type không bao giờ được xuất hiện sau `instanceof`”. Ví dụ sau hợp lệ:

```java
List<Integer> values = new ArrayList<>();

if (values instanceof ArrayList<Integer>) {
    // legal
}
```

Điều này **không** có nghĩa JVM vừa kiểm tra từng element có phải `Integer` hay không. Static type `List<Integer>` đã mang constraint `Integer`; phép `instanceof` ở đây chỉ refine phần runtime class từ `List` sang `ArrayList`.

Giữ ba ý tách biệt:

- erasure làm runtime object không mang concrete type argument theo cách cho phép arbitrary element-type test;
- Java 16+ cho phép một số parameterized `instanceof` khi static type và casting conversion khiến phép kiểm tra hợp lệ;
- class file có thể giữ **generic signature metadata** của declarations; module Reflection sẽ học cách đọc metadata đó.

## <a id="bridge-method">Bridge Method</a>

Đây là **phần nâng cao về compiler implementation**. Không cần nhớ cách bridge method được sinh để sử dụng Generics hằng ngày; cần hiểu nó khi đọc bytecode/Reflection hoặc giải thích vì sao polymorphism vẫn đúng sau erasure.

Erasure có thể làm signature runtime của override không còn khớp trực tiếp. Compiler tạo **synthetic bridge method** để giữ polymorphism.

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

Sau erasure, interface method về mặt runtime gần với:

```java
Object map(Object value)
```

Trong khi implementation thật là:

```java
String map(String value)
```

Compiler có thể sinh bridge conceptual như:

```java
public Object map(Object value) {
    return map((String) value);
}
```

Reflection có thể quan sát method synthetic/bridge này. Nó không phải method do developer tự viết.

## <a id="non-reifiable-types">Reifiable và Non-Reifiable Types</a>

Đây cũng là **phần runtime nâng cao**. Ở lượt đầu, chỉ cần nhớ practical rule: runtime không phải lúc nào cũng kiểm tra được concrete generic argument; vì thế generic array, cast và một số runtime check có giới hạn.

**Reifiable type** là type có đủ representation cần thiết ở runtime cho các operation yêu cầu runtime type check.

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

Reifiability vẫn rất quan trọng với những nơi runtime thực sự cần một kiểu có representation đầy đủ, đặc biệt array creation, unchecked casts và một số boundary runtime. Với `instanceof`, Java 16+ còn xét **checked cast compatibility**, nên không nên biến “non-reifiable” thành câu nhớ máy móc rằng parameterized type luôn bị cấm.

Một unchecked cast có thể compile nhưng compiler không thể kiểm tra đầy đủ type argument ở runtime:

```java
List<String> names = (List<String>) value;
```

Vì vậy unchecked cast phải được xem như một **trust boundary**.

Erasure giải thích nhiều restriction tưởng như rời rạc của generics. Chương cuối gom các restriction quan trọng và chỉ ra cách thiết kế thay thế an toàn.
