<a id="back-to-top"></a>

# Xóa kiểu

## Menu
- [Xóa kiểu (type erasure) là gì và lý do tương thích](#type-erasure-001)
- [Xóa tham số không có giới hạn về Object](#type-erasure-002)
- [Xóa tham số có giới hạn về giới hạn đứng đầu](#type-erasure-003)
- [Xóa kiểu trong chữ ký phương thức và kiểu mảng](#type-erasure-004)
- [Phép ép kiểu được trình biên dịch chèn vào khi đọc dữ liệu](#type-erasure-005)
- [Vì sao Box&lt;String&gt; và Box&lt;Integer&gt; dùng cùng lớp lúc chạy](#type-erasure-006)
- [Phân biệt xóa kiểu với tạo lớp chuyên biệt cho từng đối số kiểu](#type-erasure-007)
- [Thông tin chữ ký generic còn lưu trong class file](#type-erasure-008)

## <a id="type-erasure-001">Xóa kiểu (type erasure) là gì và lý do tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# Xóa kiểu (type erasure) là gì và vì sao Java chọn cách này?

**Type erasure (xóa kiểu)** là cơ chế Java dùng để biên dịch phần lớn thông tin kiểu generic thành các kiểu thông thường trong bytecode. Lập trình viên viết `List<String>`, `Box<T>` hoặc `<T extends Number>` để trình biên dịch kiểm tra an toàn kiểu; khi tạo bytecode, trình biên dịch thay tham số kiểu bằng **giới hạn trên** tương ứng, thêm phép ép kiểu nếu cần và đôi khi tạo **bridge method** để duy trì tính đa hình.

Mục tiêu quan trọng của thiết kế này là cho mã generic được đưa vào Java từ Java 5 có thể **tương tác với thư viện và bytecode được viết trước generics**, thay vì yêu cầu mọi thư viện cũ tạo các bản lớp riêng cho từng kiểu `T`.

> Nhớ trước: **generic chủ yếu được kiểm tra tại thời điểm biên dịch**; giá trị trong `List<String>` không tự mang một nhãn runtime “đây là danh sách String”. Tuy nhiên, không phải mọi dấu vết generic đều biến mất: chữ ký generic vẫn có thể được lưu trong metadata của class để phục vụ trình biên dịch và reflection.

## 1. Generic mang lại điều gì trước khi xóa kiểu?

Xét hai cách viết danh sách:

```java
// Minh họa, chưa phải chương trình hoàn chỉnh:
List raw = new ArrayList();                 // Raw type
raw.add("Java");
raw.add(123);                               // Vẫn biên dịch; bỏ kiểm tra phần tử

List<String> typed = new ArrayList<>();
typed.add("Java");
// typed.add(123);                          // Lỗi biên dịch: int/Integer không phải String
String text = typed.get(0);                 // Không cần tự viết cast
```

Ở mã nguồn, `List<String>` giúp trình biên dịch biết `add` cần `String` và `get` được xem là trả `String`. Với raw `List`, phần tử được nhìn qua `Object`, và lập trình viên phải tự bảo đảm hoặc tự ép kiểu.

Generic vì thế tạo **kiểm tra kiểu sớm** và làm API dễ đọc, dễ bảo trì hơn. Type erasure là cách Java hiện thực hóa những bảo đảm ở bước biên dịch; nó không có nghĩa generics “vô ích” hay “chỉ để viết đẹp”.

## 2. Trình biên dịch xóa kiểu theo những quy tắc nào?

Có ba nhóm biến đổi cần hiểu:

1. **Thay tham số kiểu** bằng giới hạn trên của nó; nếu không khai báo giới hạn thì dùng `Object`. Nếu có nhiều giới hạn, kiểu được dùng để xóa là giới hạn đầu tiên.
2. **Chèn cast** tại những chỗ mã nguồn đòi hỏi kiểu cụ thể hơn kiểu sau khi xóa. Cast vẫn có thể kiểm tra lúc chạy và ném `ClassCastException` khi dữ liệu bị làm bẩn qua raw type hoặc unchecked operation.
3. **Tạo bridge method khi cần** để phương thức ghi đè tiếp tục hoạt động đúng với quy tắc dispatch của JVM sau khi chữ ký tham số đã đổi do erasure.

Đối với một tham số kiểu:

| Khai báo nguồn | Kiểu sau khi xóa | Ý nghĩa |
| --- | --- | --- |
| `<T>` | `Object` | `T` không có giới hạn rõ ràng |
| `<T extends Number>` | `Number` | Biết chắc đối tượng thỏa giới hạn `Number` |
| `<T extends Number & Comparable<T>>` | `Number` | Giới hạn đầu tiên quyết định erasure của `T` |
| `List<String>` | `List` | Tham số kiểu `String` không tạo lớp runtime mới |
| `Map<String, Integer>` | `Map` | Hai tham số kiểu không nằm trong descriptor thông thường |

**Lưu ý:** dấu `extends` của type parameter có thể đi kèm lớp và interface. Ví dụ `T extends Number & Comparable<T>` hợp lệ vì `Number` là lớp đứng trước interface. Không thể dùng nhiều lớp cha.

### 2.1. Hình dung bản trước và sau erasure

Mã nguồn:

```java
// Mã nguồn minh họa:
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Có thể hình dung các chữ ký sau khi xóa tương đương với:

```java
// Pseudocode minh họa erasure, KHÔNG đặt chung với class Box<T> ở trên:
class Box {
    private Object value;

    Box(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}
```

Đây là mô hình giảng giải, không phải tệp nguồn mà javac sinh ra. Javac tạo bytecode và có thể giữ thêm `Signature` metadata mô tả khai báo generic. Nếu gọi:

```java
// Snippet đặt trong chương trình có khai báo Box<T>:
Box<String> box = new Box<>("Java");
String value = box.get();
```

Lời gọi `get()` có kiểu nhìn từ nguồn là `String`; vì phương thức bị xóa có kết quả `Object`, compiler có thể chèn `checkcast String` trong bytecode của bên gọi.

Với `class NumericBox<N extends Number>`, trường kiểu `N`, tham số kiểu `N` và kiểu trả về `N` trong descriptor thông thường được xóa về `Number`, không phải `Object`.

## 3. Chương trình Java 17+ quan sát erasure và bridge method

Lưu nội dung sau thành tệp `TypeErasureDemo.java`:

```java
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TypeErasureDemo {
    static class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static class NumericBox<N extends Number> {
        private final N value;

        NumericBox(N value) {
            this.value = value;
        }

        N get() {
            return value;
        }
    }

    static class Node<T> {
        private T value;

        void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static class TextNode extends Node<String> {
        @Override
        void set(String value) {
            super.set(value);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        List<String> words = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        words.add("Java");
        numbers.add(17);

        System.out.println("Same runtime class: "
                + (words.getClass() == numbers.getClass()));

        Box<String> box = new Box<>("Java");
        String text = box.get();
        System.out.println("Read through Box<String>: " + text);

        Method boxGet = Box.class.getDeclaredMethod("get");
        Method numericGet = NumericBox.class.getDeclaredMethod("get");

        System.out.println("Box.get erased return: "
                + boxGet.getReturnType().getSimpleName());
        System.out.println("Box.get generic return: "
                + boxGet.getGenericReturnType().getTypeName());
        System.out.println("NumericBox.get erased return: "
                + numericGet.getReturnType().getSimpleName());

        Node<String> node = new TextNode();
        node.set("hello");
        System.out.println("Stored value: " + node.get());

        Method bridge = Arrays.stream(TextNode.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow();

        System.out.println("Bridge method parameter: "
                + bridge.getParameterTypes()[0].getSimpleName());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 TypeErasureDemo.java
java TypeErasureDemo
```

Kết quả mong đợi:

```text
Same runtime class: true
Read through Box<String>: Java
Box.get erased return: Object
Box.get generic return: T
NumericBox.get erased return: Number
Stored value: hello
Bridge method parameter: Object
```

Diễn giải lần lượt:

- Hai đối tượng `ArrayList<String>` và `ArrayList<Integer>` có cùng **runtime class** là `ArrayList`. Chúng vẫn chứa các giá trị khác nhau, và các cách sử dụng kiểu ở nguồn vẫn được compiler kiểm tra.
- `Box.get()` được phản chiếu qua `Method.getReturnType()` với kiểu trả về **`Object`** sau erasure. Trong khi đó `Method.getGenericReturnType()` có thể đọc lại khai báo **`T`** từ metadata.
- `NumericBox.get()` trả về **`Number`** ở tầng bytecode vì giới hạn trên của `N` là `Number`.
- Biến `Node<String> node` tham chiếu một `TextNode`; gọi `set("hello")` vẫn đi qua phương thức ghi đè của `TextNode`. Compiler tạo bridge nhận `Object` để đường gọi theo descriptor đã xóa vẫn tới được phương thức nhận `String`.

Có thể dùng `javap -c -p TypeErasureDemo\$Box` hoặc, tùy shell, trích tên lớp lồng nhau bằng cú pháp quoting phù hợp để tự xem bytecode. `javap -v` cho thấy thêm các thuộc tính như `Signature` và các cờ của bridge method. Những lệnh quan sát bytecode mang tính bổ sung; chương trình Java ở trên đủ để thấy khác biệt bằng reflection.

## 4. Bridge method giải quyết vấn đề ghi đè nào?

Trong chương trình ở mục 3, ở mã nguồn:

```java
class Node<T> {
    void set(T value) { /* ... */ }
}

class TextNode extends Node<String> {
    @Override
    void set(String value) { /* ... */ }
}
```

Đây là snippet rút gọn để minh họa và không thay thế chương trình chạy độc lập bên trên. Sau erasure, phương thức của lớp cha có descriptor nhận `Object`, còn phương thức cụ thể của lớp con nhận `String`.

Nếu chỉ xét hai descriptor đó, JVM sẽ không tự hiểu `set(String)` là phương thức cần được gọi khi client đã biên dịch lời gọi tới `Node.set(Object)`. Compiler vì vậy có thể tạo thêm một phương thức **synthetic bridge** về bản chất tương đương:

```java
// Mã mô phỏng phương thức được compiler tạo bên trong TextNode:
void set(Object value) {
    set((String) value);
}
```

Bridge thật là bytecode do compiler tạo, **không** phải phương thức bạn phải tự viết trong lớp Java. Phương thức này bảo toàn dispatch động: `Node<String> n = new TextNode(); n.set("abc");` vẫn chạy logic ghi đè ở `TextNode`.

Vì bridge phải cast `Object` về `String`, mã sử dụng raw type sai cách có thể gặp `ClassCastException` ngay tại bridge. Đây là hành vi bảo vệ kiểu runtime, không phải dấu hiệu generics được chuyên biệt hóa.

## 5. Vì sao type erasure giúp tương thích với Java trước generics?

Java xuất hiện trước khi generics được đưa vào Java 5. Nhiều thư viện lâu đời dùng `Object`, raw `List`, `Collection` và API không khai báo type parameter. Chọn erasure giúp quá trình đưa generics vào thư viện cũ theo hướng **migration compatibility**.

### 5.1. Chữ ký JVM quen thuộc được giữ ở nhiều API

Xét cùng một ý định API ở hai thời kỳ:

```java
// Phiên bản trước generic (minh họa):
void consume(java.util.List items) { /* ... */ }

// Phiên bản khai báo generic (minh họa, thay thế bản trên):
void consume(java.util.List<String> items) { /* ... */ }
```

Hai phương thức trên đều có descriptor tham số JVM dùng `java/util/List`, thay vì tạo một descriptor riêng cho `List<String>`. Về mặt mô hình bytecode, đoạn tham số là:

```text
(Ljava/util/List;)V
```

Trong đó `L...;` biểu thị kiểu đối tượng `List`, còn `V` là `void`. Khi một thư viện được generic hóa nhưng **chữ ký đã xóa và các điều kiện tương thích khác được giữ nguyên**, bytecode client cũ vẫn có cơ hội liên kết đến các phương thức như trước.

Hai khai báo này **không thể cùng tồn tại dưới dạng overload trong một lớp**, vì sau erasure chúng trùng chữ ký.

### 5.2. Mã cũ và mã mới có thể gọi nhau

Ví dụ, một thư viện cũ cung cấp một `List` raw vẫn có thể được mã mới xử lý, dù compiler có thể báo **unchecked warning** khi gán hoặc ép về `List<String>`. Ngược lại, mã cũ nhận `List` vẫn có thể gọi API generic mà descriptor thông thường tương thích.

Nhờ đó, Java không phải tạo một lớp runtime mới `List<String>`, một lớp khác `List<Integer>`, rồi thay toàn bộ hạ tầng class loading và thư viện cũ. Các đối tượng `ArrayList` vẫn mang cùng tên lớp ở runtime; phần bổ sung của generics chủ yếu nằm ở hệ thống kiểu nguồn và metadata.

Điều này giúp **tương thích và triển khai dần**; không có nghĩa mọi thay đổi API đều tự động tương thích. Ví dụ, đổi giới hạn đầu tiên của `T` có thể đổi erasure của tham số hoặc kiểu trả về, làm đổi descriptor; đổi overload hoặc quy tắc kế thừa cũng có thể phá mã client. Phiên bản class file của bytecode mới và các API/JVM được sử dụng vẫn phải được môi trường chạy hỗ trợ. Type erasure không biến mọi class biên dịch bằng JDK mới thành class chạy được trên JVM rất cũ.

## 6. Vì sao `List<String>` và `List<Integer>` không có hai class runtime?

Đối số kiểu chỉ thay đổi **hợp đồng source-level**, không sinh một class `ArrayList` riêng cho `String` và `Integer`:

```java
List<String> words = new ArrayList<>();
List<Integer> ids = new ArrayList<>();
System.out.println(words.getClass() == ids.getClass()); // true
```

Phép so sánh dùng hai object khác nhau có **cùng class runtime**. Điều đó không biến `List<String>` thành `List<Integer>` hoặc `List<Object>`: compiler vẫn kiểm tra tính bất biến (*invariance*) và từ chối phép gán sai generic argument. Bài về hai cách dùng `Box<T>` cùng lớp runtime sẽ chứng minh riêng class identity, subtype metadata và type tokens.

## 7. Những điều không thể kiểm tra trực tiếp tại runtime

Type erasure dẫn tới một số giới hạn rõ ràng:

| Tình huống | Hợp lệ? | Vì sao |
| --- | --- | --- |
| `value instanceof List<?>` | Có | Kiểm tra raw runtime `List`; wildcard `?` không đòi biết kiểu phần tử |
| `value instanceof List<String>` | Không | Không thể kiểm tra tham số `String` của `List` ở runtime theo cách đó |
| `new T()` trong một lớp generic | Không | Không có thông tin class cụ thể và bảo đảm constructor cho `T` |
| `T.class` | Không | Type variable không có class literal tương ứng |
| `new List<String>[10]` | Không | Array kiểm tra kiểu phần tử lúc chạy trong khi `List<String>` không là kiểu reifiable |
| `List<int>` | Không | Type argument phải là kiểu tham chiếu; dùng `List<Integer>` và boxing/unboxing |

Với lớp generic, có thể lưu `Class<T>`, `Supplier<T>` hoặc factory được truyền từ ngoài nếu phải tạo đối tượng `T`. Chẳng hạn:

```java
// Snippet minh họa phương án dùng factory:
static <T> T create(java.util.function.Supplier<T> factory) {
    return factory.get();
}

// create(java.util.ArrayList::new) sẽ tạo một ArrayList.
```

Mặt khác, **array có kiểu thành phần reifiable** và kiểm tra kiểu này lúc chạy: cố ghi một `Integer` vào đối tượng runtime `String[]` có thể gây `ArrayStoreException`. Generic và array vì thế áp dụng hai cơ chế kiểm tra kiểu khác nhau.

## 8. Raw type và heap pollution: giới hạn thực tế của kiểm tra kiểu

Generic bảo vệ mã nguồn được biên dịch đúng kiểu, nhưng có các “lối thoát” như **raw type**, unchecked cast và một số thao tác phản xạ.

Xét snippet **cố ý không an toàn**:

```java
import java.util.ArrayList;
import java.util.List;

// Minh họa hành vi lỗi, không phải mẫu code nên dùng:
List<String> names = new ArrayList<>();
List raw = names;            // Raw type bỏ thông tin String khỏi lần thao tác này
raw.add(123);               // Có thể có unchecked warning
String first = names.get(0); // Có thể ném ClassCastException
```

Đối tượng danh sách thực chất chứa `Integer` ở chỉ số 0. Tuy nhiên, mã biên dịch với `names.get(0)` kỳ vọng `String` và sẽ có một cast thích hợp trên đường lấy dữ liệu; vì vậy lỗi thường lộ ra **khi đọc**, thay vì nhất thiết lúc raw `add`.

Tình trạng một biến có kiểu parameterized nhưng trỏ đến dữ liệu không bảo đảm theo kiểu khai báo gọi là **heap pollution**. Đây là lý do nên dùng `javac -Xlint:unchecked` để chú ý unchecked warning, hạn chế raw type, và không ép kiểu generic vô căn cứ. Một unchecked cast có thể không kiểm tra được tham số `String` ngay tại vị trí cast do erasure.

## 9. Nếu đã erasure, reflection còn thấy gì?

Cần tách **runtime class**, **kiểu đã xóa** và **generic declaration metadata**. Ví dụ, ở `class Box<T> { T get(); }` (đoạn ký hiệu chữ ký), JVM có thể sử dụng descriptor trả `Object`, trong khi `getGenericReturnType()` trả một `TypeVariable` tên `T` từ thuộc tính `Signature`.

Metadata ấy mô tả **cách lớp/field/method được khai báo**, không ghi lịch sử type inference của mọi lần tạo `new Box<String>()`. `obj.getClass()` không tự trả về `Box<String>` khi runtime class chung là `Box`. Thông tin về khai báo superclass cụ thể như `class StringBox extends Box<String>` có thể được reflection đọc riêng. Bài cuối chương đi sâu vào class-file `Signature` và các loại `Type` của reflection.

## 10. Type erasure khác reification và specialization thế nào?

Ba thuật ngữ không đồng nghĩa:

| Cơ chế | Câu hỏi chính | Với Java 17 |
| --- | --- | --- |
| **Erasure** | JVM descriptor dùng kiểu nào khi bỏ generic arguments? | Thường một class generic với `Object` hoặc first bound |
| **Reification** | Kiểu nào có đủ biểu diễn runtime để kiểm tra? | `String[]` có runtime component type; `List<String>` không reifiable theo cách đó |
| **Specialization** | Có sinh code/class riêng cho từng tổ hợp kiểu không? | `Box<String>` và `Box<Integer>` không tự sinh hai class generic riêng |

Không thể dùng cơ chế trên để suy ngay hiệu năng. Wrapper boxing, object layout, JIT và workload cần được đánh giá độc lập. Bài **erasure so với specialization** phân tích trade-offs và ví dụ class `IntBox` được lập trình thủ công.

## 11. Những nhầm lẫn thường gặp

**Nhầm 1 — “Generic mất hết tác dụng sau khi biên dịch.”** Sai. Rất nhiều lỗi kiểu bị chặn ngay khi javac biên dịch; các cast được chèn còn tiếp tục phát hiện sai kiểu trong một số đường chạy.

**Nhầm 2 — “`List<String>` là lớp khác `List<Integer>`.”** Sai đối với runtime class: hai cách dùng `ArrayList` vẫn là `ArrayList`; type arguments được compiler theo dõi.

**Nhầm 3 — “Vì bytecode giống nhau nên có thể overload `process(List<String>)` và `process(List<Integer>)`.”** Sai. Hai chữ ký xóa thành `process(List)` và gây **name clash** trong cùng lớp.

**Nhầm 4 — “`instanceof List<String>` hoạt động vì trong list đang toàn String.”** Sai. Một collection hiện đang chỉ chứa `String` không làm JVM có kiểu parameterized reifiable tương ứng để kiểm tra.

**Nhầm 5 — “Reflection không thể thấy bất kỳ generic nào.”** Sai. Metadata của khai báo generic có thể tồn tại dù không thể suy ra type argument từ bất kỳ instance nào.

**Nhầm 6 — “Type erasure bảo đảm mọi thay đổi từ API cũ sang API generic tương thích tuyệt đối.”** Sai. Tính tương thích phụ thuộc descriptor đã xóa, quan hệ kế thừa, overload, khả năng JVM đọc class file và hành vi API.

## 12. Cách tự kiểm chứng khi làm việc với API thực tế

Khi gặp một lỗi liên quan đến generics, hãy đi theo trình tự sau:

1. **Đọc type parameter và bounds** ở chữ ký nguồn, ví dụ `<T>`, `<T extends Number>`, `Comparable<? super T>`.
2. **Tìm kiểu sau erasure**: nếu `T` không bound thì `Object`; có bound thì lấy giới hạn đầu tiên; `List<String>` dùng raw `List` trong descriptor.
3. **Xác định chỗ cast có thể xuất hiện** khi gán kết quả generic cho kiểu cụ thể.
4. **Kiểm tra quan hệ override**: phương thức cụ thể trong subclass có cần bridge để phục vụ descriptor của superclass/interface hay không.
5. **Đọc unchecked warning** và kiểm tra raw/unchecked operations nếu lỗi chỉ xảy ra lúc chạy.
6. **Dùng `javap` hoặc reflection** để đối chiếu erasure, `Signature` và bridge khi cần xác minh sâu.

Ví dụ chạy chương trình ở mục 3 cùng với `javap -p -c TypeErasureDemo` và `javap -p -v TypeErasureDemo\$TextNode` (với quoting tên class lồng nhau theo shell) giúp quan sát lớp biên dịch; kết quả `Method.isBridge()` là cách đơn giản hơn để nhận biết bridge trong chương trình Java.

**Kết luận:** Java chọn type erasure để thêm khả năng kiểm tra kiểu generic vào một hệ sinh thái thư viện và bytecode đã tồn tại. Compiler thay type parameter bằng bound phù hợp, chèn cast và tạo bridge khi cần. Đổi lại, Java generic thông thường không cung cấp kiểm tra trực tiếp các type argument như `List<String>` tại runtime, nên hiểu rõ erasure, metadata và unchecked warning là kỹ năng thiết yếu khi đọc hoặc thiết kế API generic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-002">Xóa tham số không có giới hạn về Object</a>

<details>
<summary>Xem chi tiết</summary>

# Xóa tham số generic không có giới hạn về `Object` trong Java

## Mục tiêu học tập

Bài này tập trung vào **type erasure (xóa kiểu)** đối với một tham số kiểu generic **không khai báo giới hạn** như `<T>`. Sau khi học, bạn có thể:

- Giải thích tại sao `T` không có `extends ...` được xóa thành `java.lang.Object` trong dạng bytecode của field, tham số phương thức và kiểu trả về trực tiếp sử dụng `T`.
- Phân biệt kiểm tra kiểu lúc **biên dịch** với kiểu trong **method descriptor của JVM**.
- Tìm bằng chứng bằng `javap` và reflection, kể cả lý do reflection vẫn có thể đọc ký hiệu `T` từ metadata.
- Giải thích chỗ trình biên dịch thêm phép ép kiểu khi lấy giá trị generic; nhận diện nguy cơ `ClassCastException` khi phá vỡ an toàn kiểu.
- Nhận biết những hạn chế điển hình do type erasure gây ra và tránh hiểu nhầm về `List<String>` so với `List<Integer>`.

## 1. Từ generic ở mã nguồn đến chương trình chạy

Xét một lớp generic:

```java
final class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

`T` là **tham số kiểu** (*type parameter*) do lớp `Box` khai báo. Trong `Box<T>`, `T` không có giới hạn tường minh: không có `T extends Number`, `T extends Comparable<T>` hay giao diện tương tự.

Ở mã nguồn, người dùng có thể khai báo:

```java
Box<String> name = new Box<>("Lan");
String value = name.get();
```

Trình biên dịch xác nhận `name.get()` có kiểu biểu thức `String` và cấm, chẳng hạn, `name` nhận một đối tượng `Integer` qua phương thức `set(T)` nếu có khai báo phương thức ấy.

Nhưng thông tin `String` của `Box<String>` không tạo ra một lớp máy ảo riêng tên `Box<String>`. Java generics sử dụng **type erasure**: trình biên dịch biểu diễn những vị trí dùng tham số kiểu bằng kiểu có thể tồn tại trong JVM descriptor, đồng thời chèn một số phép kiểm tra/cast cần thiết để bảo toàn ngữ nghĩa ở phía người gọi.

Quy tắc trọng tâm của bài:

> Khi xóa một tham số kiểu không có giới hạn như `T`, kiểu được dùng thay thế là **`Object`**.

Điều này mô tả **cách biên dịch khai báo/tham chiếu kiểu**, không phải một hành động chuyển đối tượng `String` thành đối tượng `Object` mới. Chuỗi `"Lan"` vẫn thực sự là một đối tượng `String` lúc chạy.

## 2. `<T>` và `<T extends Object>`

Hai dạng dưới đây có cùng giới hạn trên hiệu dụng đối với `T`:

```java
class Unbounded<T> {
    T value;
}

class ExplicitObject<T extends Object> {
    T value;
}
```

Theo quy tắc type erasure:

| Vị trí dùng `T` | Mã nguồn | Kiểu xóa trong descriptor |
| --- | --- | --- |
| Field | `T value` | `java.lang.Object value` |
| Tham số | `void set(T value)` | `void set(Object value)` |
| Giá trị trả về | `T get()` | `Object get()` |
| Phương thức generic | `static <T> T echo(T x)` | `Object echo(Object x)` |
| Giá trị bên trong `List<T>` | `List<T> items` | Bản thân tham số là `java.util.List` (kiểu đối số `T` bị xóa) |

Trường hợp cuối cần chú ý: toàn bộ tham số `List<T>` trở thành descriptor của **`List`**, không phải descriptor `Object`; chính `T` ở vị trí đối số kiểu bị xóa khỏi descriptor. Nếu `T` xuất hiện trực tiếp như tham số hoặc kiểu trả về, vị trí ấy được thay bằng `Object`.

Để đối chiếu ở mức quy tắc: nếu khai báo `<T extends Number>` thì erasure của `T` là `Number`, không còn là `Object`. Với nhiều giới hạn như `<T extends Runnable & AutoCloseable>`, erasure của `T` dựa trên **giới hạn đầu tiên** là `Runnable`. Bài này tập trung vào trường hợp `<T>` không giới hạn.

## 3. Ví dụ Java 17 chạy được, có bằng chứng từ reflection

Tạo đúng tệp `ErasureDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class ErasureDemo {
    static final class Box<T> {
        private T value;

        Box(T value) { this.value = value; }

        T get() { return value; }

        void set(T value) { this.value = value; }
    }

    static <T> T echo(T value) {
        return value;
    }

    static <T> T first(List<T> items) {
        return items.get(0);
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Box<String> textBox = new Box<>("Java");
        String text = textBox.get();
        System.out.println("value: " + text.toUpperCase());
        System.out.println("echo: " + echo(123));
        System.out.println("first: " + first(List.of("A", "B")));

        Field field = Box.class.getDeclaredField("value");
        Method method = ErasureDemo.class
                .getDeclaredMethod("echo", Object.class);
        System.out.println("field type: " + field.getType().getTypeName());
        System.out.println("field generic type: "
                + field.getGenericType().getTypeName());
        System.out.println("echo parameter: "
                + method.getParameterTypes()[0].getTypeName());
        System.out.println("echo return: "
                + method.getReturnType().getTypeName());
        System.out.println("echo generic return: "
                + method.getGenericReturnType().getTypeName());
        System.out.println("same class: "
                + textBox.getClass().equals(new Box<>(7).getClass()));
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên, từ thư mục chứa tệp:

```bash
javac ErasureDemo.java
java ErasureDemo
```

**Kết quả thực tế đã kiểm tra:**

```text
value: JAVA
echo: 123
first: A
field type: java.lang.Object
field generic type: T
echo parameter: java.lang.Object
echo return: java.lang.Object
echo generic return: T
same class: true
```

Đọc từng điểm:

1. `Box<String>` cho phép gán `textBox.get()` vào `String` ở mã nguồn. JVM lại biết `Box.get()` trả về `Object`; trình biên dịch chèn một phép `checkcast String` ở phía người gọi.
2. `echo(123)` dùng autoboxing từ `int` thành `Integer`. `T` được suy luận hợp lý tại compile time, nhưng method descriptor của `echo` vẫn dùng `Object`.
3. `first(List<T>)` minh họa kiểu tham số được xóa là `List`, còn kết quả `T` được xóa thành `Object`. Ở vị trí cần dùng kết quả như chuỗi, có thể xuất hiện cast tương ứng.
4. `field.getType()` và `method.getReturnType()` báo **kiểu khai báo bị xóa** trong runtime descriptor: `Object`.
5. `getGenericType()` và `getGenericReturnType()` có thể báo **`T`** vì class file còn chứa thông tin khai báo generic phục vụ reflection.
6. Hai biểu thức `new Box<>("Java")` và `new Box<>(7)` tạo đối tượng của **cùng runtime class `Box`**, cho nên dòng cuối in `true`.

Lưu ý: `Box.class` và `ErasureDemo.class` là các class literal hợp lệ. Không có class literal riêng dành cho `Box<String>` hoặc `Box<Integer>`.

## 4. Xác minh bằng `javap`: nhìn descriptor thật

Sau khi biên dịch tệp trên, dùng lệnh sau. Cú pháp đặt dấu nháy quanh tên nested class tùy shell: trong Bash, dùng dấu nháy đơn để tránh `$` bị diễn giải; trong PowerShell, cách viết bên dưới cũng hợp lệ.

```bash
javap -s -p 'ErasureDemo$Box'
javap -s -p ErasureDemo
```

Trích đoạn **đã xác minh bằng `javap`** (chỉ những thành phần liên quan):

```text
final class ErasureDemo$Box<T> {
  private T value;
    descriptor: Ljava/lang/Object;

  ErasureDemo$Box(T);
    descriptor: (Ljava/lang/Object;)V

  T get();
    descriptor: ()Ljava/lang/Object;

  void set(T);
    descriptor: (Ljava/lang/Object;)V
}
```

Với `ErasureDemo`:

```text
static <T> T echo(T);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;

static <T> T first(java.util.List<T>);
  descriptor: (Ljava/util/List;)Ljava/lang/Object;
```

**Cách đọc descriptor:** `Ljava/lang/Object;` là kiểu tham chiếu `java.lang.Object`; `Ljava/util/List;` là `java.util.List`; `V` là `void`. Trong descriptor của phương thức, phần nằm giữa `(` và `)` là danh sách kiểu tham số; phần sau `)` là kiểu trả về.

`javap` vẫn in `T` ở chữ ký hiển thị trong một số chế độ vì thông tin **generic signature** của khai báo có thể được ghi kèm class file. Điều quyết định lời gọi ở mức JVM là phần `descriptor` đã xóa kiểu. Không nhầm chữ ký hiển thị `T get()` với descriptor `()Ljava/lang/Object;`.

Muốn nhìn thấy phép ép kiểu được compiler chèn, chạy:

```bash
javap -c -p ErasureDemo
```

Trong bytecode phương thức `main`, đoạn gọi `textBox.get()` có chuỗi lệnh tương tự:

```text
invokevirtual ... Box.get:()Ljava/lang/Object;
checkcast     ... class java/lang/String
astore_2
```

Các chỉ số constant pool, số dòng bytecode và tên lớp được `javap` viết tắt có thể thay đổi theo compiler/cấu trúc chương trình, nhưng `get` trả `Object` rồi `checkcast String` là điểm cần kiểm tra. Đoạn trích minh họa này thuộc chương trình đã biên dịch ở trên.

## 5. Tại sao lập trình viên vẫn được bảo vệ về kiểu?

Trong Java, generics đưa kiểm tra kiểu vào **compile time**. Ví dụ:

```java
Box<String> box = new Box<>("OK");
box.set("Java");  // Hợp lệ.
// box.set(42);   // Lỗi biên dịch: Integer không phù hợp với String.
String s = box.get();
```

Chữ ký `set(T)` của `Box<String>` được trình biên dịch hiểu là cần `String` tại điểm gọi. Khi xuống bytecode, `set` nhận `Object` nhưng tính đúng đắn được đảm bảo bởi những lời gọi Java có kiểm tra kiểu. Tương tự, `get` trả `Object` ở descriptor nhưng compiler thêm cast khi phía nhận cần `String`.

Cách hình dung gần đúng (chỉ **minh họa mô hình biên dịch**, không phải mã mà compiler bắt buộc sinh lại dưới dạng source Java):

```java
final class BoxErased {
    private Object value;

    BoxErased(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }

    void set(Object value) {
        this.value = value;
    }
}

// Mô phỏng tại chỗ gọi:
// BoxErased box = new BoxErased("Java");
// String s = (String) box.get();
```

Ở chương trình thật, compiler không nhất thiết tạo một lớp `BoxErased`; ví dụ chỉ làm hiện rõ các vị trí `T` được thay bằng `Object`. Lớp gốc `Box` đã được biên dịch trực tiếp với descriptor phù hợp.

Việc xóa tham số kiểu góp phần giúp các API dùng generics tương tác với lớp và mã Java đời trước. Nhưng đó không phải một bảo đảm rằng bất kỳ đường đi dữ liệu nào ở runtime cũng tuyệt đối an toàn: cast vẫn có thể thất bại nếu một lời gọi bỏ qua kiểm tra kiểu thông qua raw type hoặc thao tác không an toàn khác.

## 6. Khi raw type làm hỏng giả định của compiler

Đây là ví dụ **cố tình không an toàn** để quan sát giới hạn của erasure:

```java
import java.util.ArrayList;
import java.util.List;

public class UnsafeRawDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        List raw = words;              // Raw type: mất đối số kiểu String.
        raw.add(42);                  // Cảnh báo unchecked của compiler.

        try {
            String first = words.get(0); // Cast sang String lúc đọc.
            System.out.println(first);
        } catch (ClassCastException ex) {
            System.out.println("Runtime failure: " + ex.getClass().getSimpleName());
        }
    }
}
```

Chạy bằng:

```bash
javac -Xlint:unchecked UnsafeRawDemo.java
java UnsafeRawDemo
```

Stdout dự kiến:

```text
Runtime failure: ClassCastException
```

Lệnh `javac -Xlint:unchecked` cũng đưa ra cảnh báo biên dịch về `raw.add(42)`; văn bản cảnh báo cụ thể thay đổi theo JDK và không thuộc stdout của ứng dụng.

Diễn biến: `raw` và `words` tham chiếu **cùng một** `ArrayList`; do raw type, lời gọi `add(42)` lọt qua với cảnh báo. Khi đọc lại qua `List<String>`, compiler kỳ vọng `String` và sinh cast; đối tượng thực là `Integer`, nên phát sinh `ClassCastException`. Lỗi này không chứng minh rằng generic vô hiệu: nó cho thấy **bỏ qua cảnh báo và phá vỡ kiểu hóa** khiến bảo đảm compile time không còn đầy đủ.

Trong dự án thông thường, tránh raw types, unchecked casts tùy tiện và trộn dữ liệu generic với API không an toàn. Không dùng `@SuppressWarnings("unchecked")` để che một vấn đề chưa có bằng chứng an toàn.

## 7. Những giới hạn trực tiếp từ việc `T` bị xóa

### 7.1. Không thể phân biệt các tham số kiểu bằng `instanceof`

Sai:

```java
// if (value instanceof List<String>) { } // Không biên dịch.
```

Vì đối số kiểu `String` của `List<String>` không được hiện thực hóa thành runtime subtype riêng. Có thể kiểm tra phần kiểu còn tồn tại:

```java
if (value instanceof List<?>) {
    // Chỉ biết đó là một List; chưa thể khẳng định
    // tất cả phần tử là String.
}
```

Đoạn minh họa trên giả định `value` là biến tham chiếu đã khai báo, chẳng hạn `Object value`.

### 7.2. Không có `T.class` hoặc `new T()`

Đoạn sau không hợp lệ:

```java
class Factory<T> {
    // Class<T> type = T.class;        // Lỗi.
    // T value = new T();              // Lỗi.
    // T[] items = new T[10];          // Lỗi.
}
```

Khi `T` không được hiện thực hóa thành một `Class` cụ thể trong JVM, không thể dùng `T` ở các biểu thức cần lớp runtime cụ thể như trên.

Nếu cần tạo đối tượng, truyền một `Supplier<? extends T>`; nếu cần một `Class<T>`, truyền token tường minh:

```java
static <T> T make(java.util.function.Supplier<? extends T> supplier) {
    return supplier.get();
}

// Ví dụ gọi trong ngữ cảnh có phương thức make:
// StringBuilder builder = make(StringBuilder::new);
```

Đây là đoạn mã minh họa cần được đặt trong một lớp và có chỗ gọi phù hợp.

### 7.3. Không thể overload chỉ nhờ khác đối số generic

Hai phương thức sau xung đột **name clash**, vì sau erasure cả hai có cùng dạng tham số `List`:

```java
// Không thể cùng khai báo trong một lớp:
void show(List<String> strings) { /* ... */ }
void show(List<Integer> integers) { /* ... */ }
```

Tương tự, trong cùng một lớp, `void receive(Object x)` và `<T> void receive(T x)` có cùng danh sách tham số bị xóa là `(Object)`, nên không thể dùng chúng làm hai overload riêng.

Cách xử lý: chọn tên phương thức rõ mục đích (`showStrings`, `showIntegers`), hoặc thiết kế **một** generic method có hành vi phù hợp, hoặc truyền thêm chiến lược/metadata tường minh.

### 7.4. Không suy ra `T` cụ thể từ chính đối tượng `Box<T>`

```java
Box<String> a = new Box<>("A");
Box<Integer> b = new Box<>(10);
// a.getClass().equals(b.getClass()) == true
```

Một đối tượng `Box<String>` không mang thẻ runtime ghi rằng nó đã được khởi tạo với `String` theo cách một runtime class riêng sẽ có. Reflection có thể đọc **khai báo** `Box<T>` và metadata generic trên field/phương thức, nhưng nhìn vào `a.getClass()` không đủ để suy luận tham số kiểu cụ thể `String` ở nơi đã tạo `a`.

Không lẫn điều này với `a.get()`: giá trị trả về vẫn là đối tượng `String` thực và có thể gọi `a.get().getClass()` để biết lớp của **giá trị đang có**. Kết quả đó không chứng minh lớp `Box` giữ tham số kiểu `T` cụ thể qua mọi trường hợp; hộp có thể rỗng hoặc chứa `null`.

## 8. Generic signature không mâu thuẫn với type erasure

Một field khai báo `T value` trong `Box<T>` không bound có hai cách nhìn hợp lệ:

- **Descriptor JVM**: field có kiểu `Object` (mã `Ljava/lang/Object;`).
- **Generic `Signature` metadata**: vẫn có thể lưu tên biến kiểu `T` và khai báo generic của lớp.

Vì vậy, `Field.getType()` có thể trả `Object.class`, còn `Field.getGenericType()` có thể là `TypeVariable`. Đây không phải hai runtime classes cho `Box<String>` và `Box<Integer>`, cũng không bảo đảm biết đối số kiểu của từng object. Bài cuối chương giải thích chi tiết cơ chế `Signature`; tại đây điều cần chứng minh là **erasure của unbounded `T` vẫn là `Object`**.

## 9. So sánh trước/sau xóa kiểu: nắm đúng phạm vi

| Câu hỏi | Trước erasure ở mã nguồn | Sau erasure/ở runtime |
| --- | --- | --- |
| Có thể gọi `box.set(42)` với `Box<String>`? | Compiler báo lỗi | JVM chỉ thấy descriptor `set(Object)`, nhưng chương trình hợp lệ đã được kiểm tra lúc biên dịch |
| `T get()` không giới hạn trở thành gì? | `T` | Descriptor trả `Object` |
| `<T> T echo(T)` trở thành gì? | Phương thức generic | Descriptor `(Object)Object` |
| `<T> T first(List<T>)`? | Nhận `List<T>`, trả `T` | Descriptor `(List)Object` |
| Có `Box<String>.class` riêng? | Cú pháp class literal như vậy không hợp lệ | Một `Box.class` dùng chung |
| `String` có bị đổi thành `Object` khi lưu trong `Box`? | Không | Không; đối tượng vẫn là `String` |
| Có thể biết tên tham số khai báo là `T` không? | Có | Có thể qua generic signature/reflection |
| Cast lấy `String` khi `get()` trả erased `Object`? | Compiler hiểu kiểu biểu thức là `String` | Compiler có thể chèn `checkcast String` |

Không nên tưởng tượng quá trình xóa kiểu là thay mọi chữ `String` trong chương trình bằng `Object`. Quy tắc ở đây tác động đến **tham số generic và các vị trí dùng tham số generic**. Một biến trực tiếp khai báo `String s` vẫn có descriptor `String` khi thích hợp; đối tượng thực vẫn giữ lớp thực của nó.

## 10. Tự luyện: đọc, dự đoán và sửa lỗi

**Bài 1 — Dự đoán descriptor.** Với:

```java
static <T> T choose(T x, T y) {
    return x;
}
```

Hỏi: tham số và kiểu trả về ở descriptor là gì? **Đáp án:** `(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;`.

**Bài 2 — Tham số chứa `T`.** Với:

```java
static <T> T tail(java.util.List<T> items) {
    return items.get(items.size() - 1);
}
```

Hỏi: descriptor của `tail` là gì? **Đáp án:** `(Ljava/util/List;)Ljava/lang/Object;`. `List` giữ loại container, đối số generic `T` bị xóa khỏi descriptor.

**Bài 3 — Kiểu cụ thể có biến mất khỏi đối tượng?** Cho `Box<String> x = new Box<>("Hello");`. Hỏi `x.get().getClass().getName()` cho biết gì? **Đáp án:** `java.lang.String`, vì giá trị thật vẫn là chuỗi; kết quả không cho biết một runtime class riêng dành cho `Box<String>`.

**Bài 4 — Reflection.** Với field `T value` không giới hạn, tại sao `getType()` báo `Object` nhưng `getGenericType()` có thể báo `T`? **Đáp án:** descriptor erased và metadata generic signature phản ánh hai lớp thông tin khác nhau.

**Bài 5 — Thiết kế API.** Bạn muốn lớp generic tạo `T` mới nhưng `new T()` không hợp lệ. Hãy viết hàm nhận `Supplier<? extends T>` rồi gọi `get()`. Kiểm tra tại sao cách này làm quyết định tạo đối tượng trở nên tường minh.

**Bài 6 — Dự đoán lỗi.** Giải thích vì sao hai overload `show(List<String>)` và `show(List<Integer>)` trong cùng lớp bị từ chối. **Đáp án:** cùng danh sách kiểu tham số `List` sau erasure; không thể phân biệt overload bằng đối số generic vốn bị xóa.

## Ghi nhớ

**Tham số generic không có giới hạn `<T>` có erasure là `Object`.** Điều đó biểu hiện trực tiếp ở bytecode descriptor của field, tham số và giá trị trả về dùng `T`; với `List<T>`, descriptor giữ `List` và xóa đối số kiểu. Java vẫn kiểm tra tương thích kiểu tại thời điểm biên dịch, có thể thêm cast ở phía dùng dữ liệu và giữ generic signature để reflection đọc lại khai báo. Dù không có runtime class `Box<String>` riêng, các đối tượng như `String` được lưu bên trong vẫn là `String` thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-003">Xóa tham số có giới hạn về giới hạn đứng đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Xóa tham số có giới hạn về giới hạn đứng đầu trong Java Generics

## 1. Vấn đề cần hiểu

Generics cho phép viết một thuật toán làm việc với nhiều kiểu mà vẫn được trình biên dịch kiểm tra kiểu. Ví dụ:

```java
static <T extends Number> int asInt(T value) {
    return value.intValue();
}
```

`T` có **giới hạn trên** (*upper bound*) là `Number`: mọi kiểu dùng làm đối số cho `T` phải phù hợp với `Number`. Nhưng sau khi biên dịch, JVM không có một phiên bản `asInt(Integer)` riêng và một phiên bản `asInt(Double)` riêng. Trình biên dịch Java áp dụng **xóa kiểu** (*type erasure*) để chuyển các khai báo generic sang kiểu có thể biểu diễn trong bytecode.

Quy tắc trọng tâm của bài:

> **Kiểu bị xóa của một tham số kiểu là kiểu bị xóa của giới hạn đứng đầu (leftmost bound) của tham số đó. Nếu không khai báo giới hạn, giới hạn ngầm định là `Object`.**

Vì vậy:

```java
<T>                                   // T bị xóa thành Object
<T extends Number>                    // T bị xóa thành Number
<T extends Number & Comparable<T>>    // T bị xóa thành Number
<T extends Comparable<T> & java.io.Serializable>
                                      // T bị xóa thành Comparable
<T extends Runnable & AutoCloseable>  // T bị xóa thành Runnable
```

Các ví dụ trên là **đoạn chữ ký generic để phân tích**, chưa phải chương trình Java độc lập.

Chú ý sự khác biệt: **giới hạn đầu tiên quyết định kiểu đại diện sau xóa kiểu**; các giới hạn tiếp theo **không biến mất khỏi yêu cầu kiểm tra kiểu của mã nguồn**. Trình biên dịch vẫn biết `T` phải đáp ứng *toàn bộ* những giới hạn đã khai báo.

## 2. Giải nghĩa cú pháp nhiều giới hạn

Một khai báo có thể ràng buộc `T` bằng nhiều kiểu:

```java
<T extends B1 & B2 & B3>
```

Trong đó:

- `T` là biến kiểu (*type variable*), được khai báo cho class, interface, method hoặc constructor.
- `B1` là giới hạn đứng đầu; `B2`, `B3` là các giới hạn bổ sung.
- `extends` ở vị trí này diễn tả điều kiện kiểu con, bao gồm cả **class** và **interface**.
- Dấu `&` nối các giới hạn tạo thành một **giới hạn giao nhau** (*intersection bound*).
- Một kiểu làm đối số cho `T` phải thỏa mãn **tất cả** giới hạn, không phải một trong số đó.

Ví dụ:

```java
<T extends Number & Comparable<T> & java.io.Serializable>
```

Một đối số kiểu hợp lệ vừa phải là subtype của `Number`, vừa phù hợp với `Comparable<T>`, vừa thực thi `Serializable`. `Integer` đáp ứng bộ điều kiện này; `String` thì không phải subtype của `Number`.

Quy tắc sắp xếp cú pháp:

1. Nếu có giới hạn là **class**, nó phải đứng **đầu tiên**.
2. Sau giới hạn class có thể là các giới hạn interface.
3. Không thể dùng hai class độc lập làm hai giới hạn class trong một danh sách `&`.
4. Nếu chỉ có interface, interface đứng đầu do người viết khai báo sẽ quyết định erasure.
5. Các kiểu trong danh sách giới hạn cũng chịu các ràng buộc hợp lệ của Java, chẳng hạn không được chứa các giới hạn có cùng erasure theo cách bị JLS cấm.

Ví dụ hợp lệ:

```java
<T extends Number & Comparable<T>>
<T extends Runnable & AutoCloseable>
<T extends AutoCloseable & Runnable>
```

Ví dụ **không hợp lệ**:

```java
<T extends Comparable<T> & Number> // Number là class nhưng không đứng đầu
<T extends Number & Thread>        // Có hai class: Number và Thread
```

Hai ví dụ không hợp lệ chỉ nhằm minh họa lỗi cú pháp/giới hạn; không đưa chúng vào chương trình cần biên dịch.

## 3. Quy tắc erasure chính xác

Theo mô hình của Java Language Specification, khi xóa một biến kiểu `T`, trình biên dịch lấy **erasure của giới hạn đứng đầu** của `T`. Có hai bước tư duy:

1. Xác định giới hạn đầu tiên của `T`. Nếu `<T>` không có `extends`, coi giới hạn là `Object`.
2. Xóa kiểu của giới hạn đầu tiên. Nếu giới hạn đó đã là class/interface không tham số hóa, tên kiểu giữ nguyên; nếu nó là kiểu tham số hóa, các đối số generic của chính giới hạn đó cũng bị xóa.

Bảng đối chiếu:

| Khai báo | Giới hạn đầu tiên | Erasure của `T` | Điều kiện thêm vẫn được kiểm tra ở mã nguồn |
|---|---|---|---|
| `<T>` | `Object` ngầm định | `Object` | Không có |
| `<T extends Object>` | `Object` | `Object` | Không có |
| `<T extends Number>` | `Number` | `Number` | Không có |
| `<T extends Number & Comparable<T>>` | `Number` | `Number` | `Comparable<T>` |
| `<T extends Comparable<T> & java.io.Serializable>` | `Comparable<T>` | `Comparable` | `Serializable` |
| `<T extends Runnable & AutoCloseable>` | `Runnable` | `Runnable` | `AutoCloseable` |
| `<T extends java.util.List<String> & java.util.RandomAccess>` | `List<String>` | `List` | `RandomAccess` |

Dòng `List<String>` cho thấy một điểm dễ bỏ qua: erasure không giữ đối số `<String>`. Đó là lý do `T` cuối cùng có kiểu đại diện là `java.util.List`, không phải `List<String>` theo nghĩa bytecode descriptor thông thường.

**Không được hiểu** quy tắc là "`T` trở thành `Object` trong mọi trường hợp". Điều đó chỉ đúng với type parameter không có giới hạn hoặc có erasure của giới hạn đầu là `Object`.

### Phạm vi áp dụng

Quy tắc này áp dụng cho **biến kiểu** ở nhiều vị trí:

```java
class Holder<T extends Number & Comparable<T>> {
    T value;

    T get() {
        return value;
    }

    void set(T newValue) {
        value = newValue;
    }
}
```

Đây là đoạn class hợp lệ; không có phương thức `main`, nên chưa phải chương trình tự chạy. Trong mô tả kiểu ở bytecode, trường `value`, tham số `set` và kết quả `get` dùng kiểu đại diện `Number`. Thông tin generic như `T` và các bound có thể được giữ trong thuộc tính `Signature` để công cụ và reflection đọc, nhưng đó **không phải** cơ chế JVM tạo nhiều class cho nhiều giá trị `T`.

## 4. Ví dụ chạy được: class đầu tiên và interface bổ sung

Tạo tệp `LeftmostBoundDemo.java`:

```java
public class LeftmostBoundDemo {

    static <T extends Number & Comparable<T>> String compare(T left, T right) {
        int first = left.intValue();
        int second = right.intValue();
        boolean greater = left.compareTo(right) > 0;
        return first + " vs " + second + " => " + greater;
    }

    static <T extends Runnable & AutoCloseable> void execute(T task)
            throws Exception {
        task.run();
        task.close();
    }

    static final class Task implements Runnable, AutoCloseable {
        @Override
        public void run() {
            System.out.println("run");
        }

        @Override
        public void close() {
            System.out.println("close");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(compare(9, 4));
        execute(new Task());
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac LeftmostBoundDemo.java
java LeftmostBoundDemo
```

Stdout:

```text
9 vs 4 => true
run
close
```

### Phân tích `compare`

```java
static <T extends Number & Comparable<T>> String compare(T left, T right)
```

- Giới hạn đứng đầu của `T` là `Number`.
- `T` bị xóa thành `Number`.
- Hai tham số của phương thức sau erasure được biểu diễn bằng `Number`.
- `intValue()` có trên `Number`, nên việc gọi thành viên này khớp trực tiếp với khả năng do giới hạn đầu cung cấp.
- `compareTo(T)` đến từ `Comparable<T>`, là **giới hạn thứ hai**. Dù `Number` không tự khai báo `compareTo`, lời gọi vẫn hợp lệ ở mã nguồn nhờ compiler kiểm tra đầy đủ các bound.

Để thực hiện lời gọi tới một phương thức của giới hạn bổ sung trong bytecode, compiler có thể chèn phép ép kiểu tới interface thích hợp (ở ví dụ này là `Comparable`) trước khi gọi phương thức trên interface. Việc xóa `T` thành `Number` **không làm lời gọi `compareTo` bị cấm**.

### Phân tích `execute`

```java
static <T extends Runnable & AutoCloseable> void execute(T task)
```

Cả `Runnable` lẫn `AutoCloseable` đều là interface. Vì `Runnable` đứng đầu, erasure của `T` là `Runnable`; compiler vẫn kiểm tra `task.close()` nhờ giới hạn thứ hai là `AutoCloseable`. Tương tự, có thể xuất hiện ép kiểu ở bytecode trước khi gọi `close()`.

Lời gọi `execute(new Task())` chỉ hợp lệ vì `Task` triển khai **cả hai** interface. Một đối tượng chỉ triển khai `Runnable` mà không triển khai `AutoCloseable` sẽ không đáp ứng giới hạn của phương thức.

Lưu ý riêng về ví dụ: phương thức dùng `run()` và `close()` theo thứ tự để minh họa hai giới hạn. Nếu `run()` ném exception thì `close()` trong ví dụ chưa chắc được gọi; trong mã quản lý tài nguyên thực tế, hãy thiết kế xử lý lỗi và đóng tài nguyên phù hợp, chẳng hạn `try/finally` hoặc `try-with-resources` khi áp dụng được.

## 5. Quan sát kiểu đã xóa bằng `javap`

JDK có công cụ `javap` để xem chữ ký và bytecode. Sau khi biên dịch tệp `LeftmostBoundDemo.java`, chạy:

```bash
javap -p -s LeftmostBoundDemo
```

Tập trung vào hai descriptor của các phương thức (trích riêng phần liên quan, không phải toàn bộ đầu ra `javap`):

```text
compare(java.lang.Number, java.lang.Number)
  descriptor: (Ljava/lang/Number;Ljava/lang/Number;)Ljava/lang/String;

execute(java.lang.Runnable)
  descriptor: (Ljava/lang/Runnable;)V
```

Vì `compare` trả về `String` nên kiểu trả về trong descriptor là `String`. `execute` là `void` nên descriptor kết thúc bằng `V`. Các descriptor này cho thấy giới hạn đứng đầu quyết định kiểu của vị trí có biến kiểu `T` trong chữ ký đã xóa.

Để quan sát lời gọi thực thi và cast do trình biên dịch sinh, dùng:

```bash
javap -p -c LeftmostBoundDemo
```

Kết quả chi tiết có offset lệnh và constant-pool index phụ thuộc bản JDK/biên dịch. Những điểm quan trọng cần tìm gồm:

- Lời gọi `Number.intValue()` từ giới hạn đầu tiên.
- Phép chuyển phù hợp sang `Comparable` và lời gọi `Comparable.compareTo(...)` khi dùng giới hạn bổ sung.
- Lời gọi `Runnable.run()` và thao tác với `AutoCloseable.close()` trong `execute`.

Không nên học thuộc offset bytecode; hãy so sánh **descriptor** và **kiểu chủ sở hữu của lời gọi**. Điều đó giải thích được cả kết quả compile time lẫn cách JVM thi hành chương trình.

## 6. Nếu giới hạn đầu tiên là interface

Giả sử một phương thức cần tham số vừa có khả năng so sánh, vừa serializable:

```java
static <T extends Comparable<T> & java.io.Serializable> T choose(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Đây là một **đoạn phương thức**, cần đặt bên trong class nếu muốn biên dịch. Ở đây:

- Giới hạn đầu tiên là `Comparable<T>`, một interface tham số hóa.
- Erasure của `Comparable<T>` là interface raw `Comparable`.
- Do đó erasure của biến kiểu `T` là `Comparable`.
- Các vị trí kiểu `T` trong descriptor phương thức dùng `Comparable`.
- `Serializable` vẫn là yêu cầu bắt buộc đối với đối số kiểu ở thời điểm biên dịch.

Chữ ký đã xóa tương ứng về mặt descriptor:

```text
(Ljava/lang/Comparable;Ljava/lang/Comparable;)Ljava/lang/Comparable;
```

**Thứ tự interface có ý nghĩa đối với erasure.** Nếu về mặt cú pháp hai khai báo sau đều hợp lệ:

```java
<T extends Runnable & AutoCloseable>
<T extends AutoCloseable & Runnable>
```

thì trường hợp đầu xóa `T` về `Runnable`, còn trường hợp sau xóa `T` về `AutoCloseable`. Tập điều kiện cần thỏa ở mã nguồn có thể giống nhau, nhưng **kiểu đại diện trong chữ ký đã xóa** lại khác.

### Vì sao không đổi thứ tự cho đẹp?

Nếu khai báo generic xuất hiện trong API công khai và bạn đổi vị trí các interface trong danh sách giới hạn, descriptor của field/method dùng biến kiểu có thể đổi. Việc này có thể ảnh hưởng tới tương thích nhị phân của thư viện, các class đã biên dịch trước đó, và cách tạo bridge method.

Vì thế, thứ tự bound không chỉ là lựa chọn trình bày. Khi thiết kế API, hãy chọn giới hạn đầu tiên có chủ đích, đặc biệt nếu class/interface được sử dụng bởi module khác hoặc xuất bản thành thư viện.

## 7. Giới hạn class bắt buộc đứng trước interface

Ví dụ:

```java
static <T extends Number & Comparable<T> & java.io.Serializable>
T pick(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Với `<T extends Number & ...>`, `Number` là class bound và bắt buộc đứng đầu. Bạn **không thể** thay đổi thứ tự thành `<T extends Comparable<T> & Number>` để ép erasure của `T` chuyển sang `Comparable`; Java sẽ từ chối khai báo vì giới hạn class đứng sai vị trí.

Nếu thực sự không cần thành viên của `Number`, bạn có thể thiết kế một API khác chỉ dựa trên interface:

```java
static <T extends Comparable<T> & java.io.Serializable>
T pickComparable(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Việc bỏ `Number` làm thay đổi **hợp đồng đầu vào**: phương thức bây giờ có thể chấp nhận những kiểu non-number thỏa hai interface, ví dụ `String`. Đây là thay đổi thiết kế API, không đơn thuần là thay đổi erasure.

## 8. Erasure ảnh hưởng phương thức, field và overload như thế nào?

### Trường, tham số, kết quả

Xét class:

```java
class NumberHolder<T extends Number & Comparable<T>> {
    T item;

    NumberHolder(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}
```

Khi xóa kiểu, các chỗ dùng `T` liên quan được biểu diễn bằng `Number`:

```java
// Mô hình minh họa kết quả sau erasure; không phải mã Java do
// javac trực tiếp in ra và không thay thế class generic ban đầu.
class NumberHolderErasedModel {
    Number item;

    NumberHolderErasedModel(Number item) {
        this.item = item;
    }

    Number get() {
        return item;
    }
}
```

Đây là **hai class khác nhau nhằm so sánh**, không phải yêu cầu tạo hai tệp khi sử dụng `NumberHolder`. Compiler/JVM thực sự dùng mô tả kiểu đã xóa trong bytecode, đồng thời compiler có thể tạo cast và các phương thức cầu nối nếu cần để duy trì hành vi generic.

Khi gọi:

```java
NumberHolder<Integer> holder = new NumberHolder<>(12);
Integer value = holder.get();
```

Mã nguồn trên có kiểu kết quả `Integer` đã được kiểm tra; ở bytecode, nếu cần, compiler có thể chèn cast từ `Number` về `Integer` tại nơi nhận. Không có hai bản runtime khác nhau cho `NumberHolder<Integer>` và `NumberHolder<Double>`.

### Không thể overload nếu chữ ký sau xóa bị trùng

Ví dụ **sai**, chỉ dùng để phân tích lỗi compile time:

```java
class BadOverload {
    static <T extends Number & Comparable<T>> void log(T value) {}

    static void log(Number value) {} // name clash vì cùng erasure
}
```

Ở phương thức đầu, `T` bị xóa thành `Number`. Hai lời khai báo sẽ có cùng tên `log` và cùng danh sách tham số sau erasure, nên không thể tồn tại như hai overload hợp lệ trong cùng class. Trình biên dịch sẽ báo lỗi trùng tên/chữ ký (*name clash*).

Cách khắc phục tùy mục tiêu: dùng tên phương thức khác, thống nhất thành một phương thức generic, hoặc thay đổi hợp đồng tham số theo cách tạo ra chữ ký thực sự khác biệt. Đừng kỳ vọng hai bound khác nhau hoặc hai đối số generic khác nhau luôn đủ để overload.

### Xóa kiểu khác với overload resolution ở mã nguồn

Compiler trước tiên kiểm tra tính hợp lệ và phân giải lời gọi theo hệ thống kiểu Java; erasure phục vụ việc sinh bytecode và duy trì mô hình tương thích. Không nên suy luận "mọi biến kiểu đã là `Number` ngay từ đầu" trong khi đọc chương trình Java nguồn. Ở nguồn, compiler vẫn biết `T` cùng một kiểu ở hai tham số và phải đáp ứng toàn bộ các bound.

## 9. Bridge method: giữ đúng hành vi override sau erasure

Một hệ quả đáng học là phương thức **cầu nối** (*bridge method*). Hãy tạo tệp `LeftmostBridgeDemo.java`:

```java
public class LeftmostBridgeDemo {

    static class Store<T extends Number & Comparable<T>> {
        T next(T value) {
            return value;
        }
    }

    static final class IntegerStore extends Store<Integer> {
        @Override
        Integer next(Integer value) {
            return value + 1;
        }
    }

    public static void main(String[] args) {
        Store<Integer> store = new IntegerStore();
        System.out.println(store.next(10));

        @SuppressWarnings("rawtypes")
        Store rawStore = new IntegerStore();

        try {
            rawStore.next(2.5);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac LeftmostBridgeDemo.java
java LeftmostBridgeDemo
```

Stdout:

```text
11
ClassCastException
```

`Store<T>.next(T)` có erasure tương ứng với `Number next(Number)`, vì `Number` là giới hạn đầu tiên. Trong khi đó, `IntegerStore.next(Integer)` nhận và trả `Integer`. Để lời gọi qua biến kiểu `Store<Integer>` vẫn dispatch đến override của `IntegerStore`, `javac` có thể sinh ra trong class con một bridge method ở cấp bytecode, có chữ ký tương thích với `next(Number)`; bridge này thực hiện cast phù hợp rồi gọi `next(Integer)`.

Có thể quan sát thông tin class con bằng:

```bash
javap -p -s 'LeftmostBridgeDemo$IntegerStore'
javap -p -v 'LeftmostBridgeDemo$IntegerStore'
```

Dùng dấu nháy đơn quanh tên nested class để ký tự `$` được giữ nguyên trong shell thường gặp (bao gồm PowerShell và bash). Trong dữ liệu được in ra, tìm hai phương thức `next` có descriptor tương ứng với `Integer` và `Number`; bản bridge được đánh dấu `ACC_BRIDGE` và `ACC_SYNTHETIC`.

`rawStore` là **raw type** và cố ý bỏ qua tham số generic để minh họa giới hạn an toàn kiểu khi trộn code cũ. Ở lời gọi `rawStore.next(2.5)`, đối số `Double` vẫn thuộc `Number`, nên có thể đi qua chữ ký đã xóa `next(Number)`. Nhưng bridge của `IntegerStore` cần chuyển đối số thành `Integer`; cast thất bại và ném `ClassCastException`. Phần này là tình huống cần **tránh** trong ứng dụng bình thường, không phải cách khuyến nghị sử dụng generic.

Thông báo biên dịch về raw/unchecked có thể xuất hiện khi thêm tùy chọn lint, nhưng chương trình vẫn được biên dịch với cú pháp trên. Không nên nuốt `ClassCastException` trong business logic; đây chỉ là phần minh họa có kiểm soát.

## 10. Có phải mọi giới hạn phía sau đều bị loại bỏ hoàn toàn?

Không. Cần phân biệt ba góc nhìn.

### Góc nhìn 1: hệ thống kiểu ở mã nguồn

Với:

```java
<T extends Number & Comparable<T>>
```

Compiler biết `T` có hành vi từ `Number` **và** `Comparable<T>`. Nó từ chối một đối số kiểu không thỏa cả hai. Erasure dùng `Number` làm kiểu đại diện không làm việc kiểm tra `Comparable<T>` bị hủy bỏ.

### Góc nhìn 2: mô tả kiểu JVM và mã máy ảo

Đối với các vị trí kiểu `T` trong field descriptor hoặc method descriptor, JVM thấy kiểu bị xóa theo bound đứng đầu. Các lệnh cần thực thi qua giới hạn phụ có thể bao gồm `checkcast` tương ứng. Tại nơi kiểu generic được chuyên biệt hóa, compiler có thể sinh thêm cast và bridge method.

Đây không phải phép biến đổi có thể thực hiện chính xác bằng cách xóa một vài chuỗi `<...>` trong mã nguồn. Erasure của Java là một cơ chế biên dịch có quy tắc cho type variables, parameterized types, arrays, method signatures, override và các phép chuyển kiểu cần thiết.

### Góc nhìn 3: metadata generic và reflection

Thông tin generic khai báo vẫn có thể được lưu vào classfile, ví dụ thuộc tính `Signature`. Reflection như `TypeVariable.getBounds()` có khả năng trả về danh sách giới hạn đã khai báo, theo thứ tự. Việc thấy metadata đó **không có nghĩa** JVM lưu đầy đủ đối số kiểu runtime cho mỗi instance generic.

Ví dụ độc lập trong tệp `BoundsReflectionDemo.java`:

```java
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public class BoundsReflectionDemo {

    static class Sample<T extends Number & Comparable<T>> {}

    public static void main(String[] args) {
        TypeVariable<?> type = Sample.class.getTypeParameters()[0];
        System.out.println(type.getName());
        for (Type bound : type.getBounds()) {
            System.out.println(bound.getTypeName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BoundsReflectionDemo.java
java BoundsReflectionDemo
```

Stdout trên JDK 17+ thông dụng:

```text
T
java.lang.Number
java.lang.Comparable<T>
```

Metadata cho biết `T` khai báo **hai** bound; quy tắc erasure vẫn chọn bound đầu là `Number`. Chỉ nhìn `getBounds()` không chứng minh một instance cụ thể của `Sample<Integer>` còn giữ được `Integer` làm đối số runtime. Bạn cũng không thể viết `new T()` hoặc `T.class` trong mã generic thông thường.

## 11. Mối liên hệ với `Comparable` và generic API

Một mẫu thường gặp khi làm thuật toán so sánh:

```java
static <T extends Comparable<? super T>> T greater(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Ở đây giới hạn đầu tiên là `Comparable<? super T>`, nên erasure của `T` là **raw `Comparable`**. `? super T` mở rộng khả năng chấp nhận một kiểu mà phép so sánh được định nghĩa trên một supertype của `T`, đồng thời không thay đổi quy tắc erasure của biến kiểu `T`.

Chữ ký khác:

```java
static <T extends Number & Comparable<? super T>>
T greaterNumber(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Ở đây giới hạn đầu tiên là `Number`, nên erasure của `T` là **`Number`**, mặc dù chính thao tác so sánh được cung cấp bởi giới hạn thứ hai. Đây là hai hợp đồng generic khác nhau.

Đối với các thuật toán tổng quát, không nên chọn bound đầu dựa trên mong muốn làm descriptor ngắn hơn. Hãy chọn các điều kiện đúng cho miền đầu vào của API; trong những giới hạn mà Java cho phép sắp xếp, quan tâm đến việc giữ ổn định thứ tự nếu API đã được phát hành.

## 12. Các nhầm lẫn thường gặp

**Nhầm lẫn 1 — "Bound cuối cùng quyết định erasure."** Sai. Với `<T extends Number & Comparable<T>>`, erasure của `T` là `Number`. Toàn bộ các bound được kiểm tra tại compile time, nhưng **bound đứng đầu** quyết định kiểu đại diện khi xóa.

**Nhầm lẫn 2 — "Các interface sau dấu `&` không còn dùng được."** Sai. Chương trình `LeftmostBoundDemo` đã gọi được cả `compareTo` và `close` từ giới hạn phụ. Compiler có thể tạo cast ở bytecode để truy cập interface phù hợp.

**Nhầm lẫn 3 — "Giới hạn đầu tiên luôn phải là class."** Sai. Nếu không có class bound, một interface có thể đứng đầu. Ví dụ `<T extends Runnable & AutoCloseable>` có erasure `Runnable`.

**Nhầm lẫn 4 — "Đổi thứ tự giới hạn không bao giờ ảnh hưởng thư viện."** Sai. Với các interface, đổi bound đầu tiên có thể thay đổi field/method descriptor hoặc cầu nối liên quan. Nếu class/method công khai đã được các binary khác dùng, phải đánh giá tương thích nhị phân trước khi sửa.

**Nhầm lẫn 5 — "Type erasure xảy ra trước nên compiler không biết các bound khác."** Sai. Compiler phân tích và kiểm tra hệ thống kiểu generic, rồi tạo bytecode theo erasure. Các phép cast cần thiết có thể được chèn trong quá trình sinh mã.

**Nhầm lẫn 6 — "Mọi generic đều xóa thành `Object`."** Sai. `<T>` thường xóa về `Object`, nhưng `<T extends Number>` xóa về `Number`, và `<T extends Comparable<T>>` xóa về `Comparable`.

**Nhầm lẫn 7 — "Generic metadata có nghĩa type arguments tồn tại riêng cho từng instance ở runtime."** Sai. Có thể đọc bound được khai báo của class/method bằng reflection, nhưng đối số kiểu của một instance thông thường không được tái hiện đầy đủ chỉ vì có metadata generic.

**Nhầm lẫn 8 — "Giới hạn `<T extends Number & Comparable<T>>` tương đương hai overload."** Sai. Đây là một phương thức generic có một biến kiểu phải đáp ứng cả hai ràng buộc; compiler không tạo một overload riêng cho `Number` và một overload riêng cho `Comparable`.

## 13. Các bước kiểm tra một chữ ký generic

Khi cần dự đoán erasure của một API:

1. Tìm phần khai báo tham số kiểu, ví dụ `<T extends Number & Comparable<T>>`.
2. Xác định **giới hạn đứng đầu**: ở đây là `Number`.
3. Xóa chính kiểu giới hạn đầu nếu nó có đối số generic, ví dụ `List<String>` thành `List`.
4. Thay các vị trí dùng biến kiểu `T` trong descriptor bằng kiểu đã xóa tương ứng.
5. Kiểm tra các giới hạn còn lại để hiểu vì sao phương thức nguồn có thể gọi thành viên của chúng.
6. Nếu có override với đối số kiểu cụ thể, xem khả năng sinh bridge method.
7. Nếu có overload, kiểm tra va chạm chữ ký sau erasure.
8. Nếu API công khai thay đổi thứ tự bound, đánh giá tương thích với client đã biên dịch.

Ví dụ tự luyện:

**Tình huống A**

```java
<T extends java.util.List<String> & java.util.RandomAccess>
```

Đáp án: giới hạn đầu là `List<String>`, erasure của `T` là `java.util.List`. `RandomAccess` vẫn là yêu cầu đối với đối số kiểu.

**Tình huống B**

```java
<T extends AutoCloseable & Runnable>
```

Đáp án: erasure của `T` là `AutoCloseable`. Nếu đảo thứ tự thành `Runnable & AutoCloseable`, erasure đổi sang `Runnable`.

**Tình huống C**

```java
<T extends Object & Runnable>
```

Đáp án: erasure của `T` là `Object`, dù trong mã nguồn `T` cũng phải triển khai `Runnable`. Đây là một ví dụ cho thấy chọn `Object` làm bound đầu tiên có thể tạo khác biệt về descriptor so với `<T extends Runnable>`.

**Tình huống D**

```java
<T extends Number & java.io.Serializable>
```

Đáp án: erasure của `T` là `Number`. `Serializable` là điều kiện bổ sung; nó không ghi đè giới hạn đầu tiên.

**Tình huống E**

```java
<T extends Comparable<T> & java.io.Serializable>
```

Đáp án: erasure của `T` là raw `Comparable`. Phần `<T>` bên trong `Comparable<T>` cũng bị xóa khỏi biểu diễn descriptor.

## 14. Ghi nhớ và tài liệu đối chiếu

Mệnh đề cần ghi nhớ là **erasure của type variable dựa vào erasure của bound đứng đầu**. Nếu không viết bound, `Object` là giới hạn ngầm định. Class bound, nếu có, luôn đứng trước các interface; nếu chỉ có interface, interface đứng đầu quyết định kiểu đại diện. Các bound tiếp theo vẫn có hiệu lực đối với kiểm tra kiểu ở mã nguồn.

Từ quy tắc nhỏ này có các hệ quả rất thực tế: field/method descriptor dùng kiểu đã xóa, compiler có thể chèn cast để dùng interface bổ sung, phương thức override có thể cần bridge method, và hai overload có thể bị từ chối vì trùng chữ ký sau erasure. Khi thiết kế thư viện, hãy coi thứ tự các giới hạn là một phần của chữ ký có thể tác động đến tương thích nhị phân.

Tài liệu chuẩn để tra cứu thêm:

- [Java Language Specification, §4.4 — Type Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.4): cú pháp, tính hợp lệ và ý nghĩa của các bound.
- [Java Language Specification, §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): quy tắc erasure của parameterized type, type variable và method signature.
- [Java Language Specification, §8.4.8.3 — Requirements in Overriding and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.3): các quy tắc liên quan tới override và name clash.
- Công cụ `javap` của JDK: dùng `-s` để kiểm tra descriptor, `-c` để xem bytecode và `-v` để xem metadata như `Signature` hoặc cờ bridge method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-004">Xóa kiểu trong chữ ký phương thức và kiểu mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Xóa kiểu trong chữ ký phương thức và kiểu mảng (Java Generics)

## 1. Bức tranh tổng quan

**Type erasure** (xóa kiểu) là cơ chế Java sử dụng để biên dịch phần lớn thông tin kiểu generic thành dạng phù hợp với các lớp và chữ ký phương thức trên JVM. Khi bạn viết `List<String>`, `List<Integer>`, `T`, `T[]`, hoặc một phương thức `<T> T identity(T value)`, trình biên dịch kiểm tra chúng bằng thông tin generic, rồi phát sinh bytecode có những kiểu đã được xóa.

Bài này tập trung vào **hai nơi có hệ quả rõ nhất**:

1. **Chữ ký phương thức**: tham số, kiểu trả về, overload, override và những phương thức bridge compiler có thể tạo.
2. **Kiểu mảng**: kiểu phần tử mảng ở runtime, mảng chứa type variable hoặc generic parameterized type, tính *reifiable*, và cách tạo mảng an toàn.

Đọc xong, bạn có thể dự đoán chữ ký bytecode của một phương thức generic, phân tích lỗi `name clash`/`generic array creation`, hiểu vì sao mảng và `List<T>` có quy tắc khác nhau, cũng như dùng `javap` và reflection để kiểm tra.

Đây là cơ chế **biên dịch và biểu diễn kiểu**. Erasure không xóa đối tượng khỏi bộ nhớ và cũng không biến mọi kiểm tra kiểu thành một phép kiểm tra muộn tùy ý ở runtime.

## 2. Quy tắc xóa kiểu cho chữ ký generic

Những quy tắc cần nắm:

| Kiểu trong source | Dạng sau erasure | Lý do |
| --- | --- | --- |
| Type variable `T` không có bound tường minh | `Object` | Bound mặc định là `Object`. |
| `<T extends Number>` | `Number` | Type variable được thay bằng bound đầu tiên. |
| `<T extends Number & Comparable<T>>` | `Number` | Trong nhiều bound, **bound đầu tiên** được dùng để xóa `T`. |
| `<T extends Comparable<T> & java.io.Serializable>` | `Comparable` (raw) | Bound đầu tiên là interface `Comparable<T>`, bản thân kiểu tham số hóa cũng bị xóa. |
| `List<String>` | `List` (raw ở mức bytecode descriptor) | Kiểu đối số `String` không tham gia descriptor. |
| `Map<String, List<Integer>>` | `Map` | Các type argument lồng nhau không còn trong descriptor. |
| `T[]` khi `T` không có bound tường minh | `Object[]` | Xóa kiểu thành phần `T` trước, vẫn giữ số chiều mảng. |
| `T[]` với `T extends Number` | `Number[]` | Kiểu thành phần được xóa về `Number`. |
| `List<T>[]` | `List[]` | Mảng vẫn là mảng; kiểu thành phần `List<T>` bị xóa thành `List`. |

Quy tắc “bound đầu tiên” đòi hỏi thứ tự khai báo hợp lệ của Java: nếu có bound là lớp thì bound lớp phải đứng trước các bound interface. Các bound còn lại vẫn được compiler dùng **khi kiểm tra source**; chúng không đồng nghĩa với việc JVM giữ nhiều kiểu tham số trong một descriptor.

Một ví dụ ngắn:

```java
static <T> T identity(T value) {
    return value;
}

static <T extends Number> T keep(T value) {
    return value;
}
```

Ở mức *method descriptor* JVM, chúng tương ứng với:

```text
identity: (Ljava/lang/Object;)Ljava/lang/Object;
keep:     (Ljava/lang/Number;)Ljava/lang/Number;
```

Ký pháp JVM:

- `L...;` biểu thị kiểu tham chiếu class/interface, dấu `/` phân tách package.
- Các kiểu bên trong `(...)` là tham số theo thứ tự.
- Phần sau `)` là kiểu trả về.
- `V` biểu thị `void`; dấu `[` đứng trước biểu thị một chiều mảng. Ví dụ `[Ljava/lang/String;` là `String[]`.

**Lưu ý:** Descriptor và **generic Signature attribute** trong class file có vai trò khác nhau. Descriptor ghi dạng xóa kiểu dùng để liên kết lời gọi tại JVM; Signature, nếu có, có thể lưu siêu dữ liệu generic để compiler và reflection xem lại. Đừng kết luận “generic không tồn tại ở đâu trong `.class`” — điều đúng là **mỗi đối tượng `List<String>` không mang một tham số `String` runtime riêng có thể kiểm tra bằng `instanceof List<String>`**.

## 3. Xóa kiểu ở tham số và kết quả phương thức

### 3.1. Phương thức generic tự khai báo type variable

Xem phương thức:

```java
static <T> T identity(T value) {
    return value;
}
```

Từ góc nhìn source:

```java
String s = identity("Java");
Integer n = identity(5);
```

Compiler suy luận `T` riêng cho từng lời gọi. Nhưng phương thức trong bytecode không nhân đôi cho mỗi kiểu:

```text
identity(Object) -> Object
```

Chữ ký JVM chỉ có một thân phương thức. Compiler có thể chèn `checkcast` tại vị trí **sử dụng kết quả** khi ngữ cảnh cần kiểu cụ thể như `String`. Nó cũng tận dụng các chuyển đổi boxing/unboxing theo quy tắc của Java.

Không vì thế mà `identity` có thể trả `String` từ một input `Integer` khi source khai báo `String s = identity(5);`: bước kiểm tra generic đã ngăn lỗi trước khi chạy.

### 3.2. Type variable của class cũng bị xóa

```java
class Box<T extends Number> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Trong bytecode, trường và chữ ký cơ bản sẽ dùng `Number`:

```text
value: Number
Box(Number)
get() -> Number
```

Khi viết `Box<Integer> box = new Box<>(7); Integer n = box.get();`, kiểu đích `Integer` vẫn được compiler kiểm tra, và bytecode của nơi gọi có thể cần cast từ `Number` sang `Integer`.

Nếu khai báo hai biến `Box<Integer>` và `Box<Double>`, không có hai định nghĩa class `Box` chuyên biệt. Java generics chủ yếu dùng **một class đã xóa kiểu**, khác với cơ chế chuyên biệt hóa template theo từng tham số kiểu ở một số ngôn ngữ khác.

### 3.3. Type bound phối hợp: erasure khác kiểm tra source

```java
static <T extends Number & Comparable<T>> T greater(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Tại source, `T` phải đồng thời là `Number` và thực hiện `Comparable<T>`, nên gọi được `compareTo`. Vì `Number` đứng đầu, descriptor là:

```text
(Ljava/lang/Number;Ljava/lang/Number;)Ljava/lang/Number;
```

Điều này **không có nghĩa** JVM sẽ tự coi mọi `Number` là `Comparable`: compiler phát sinh thao tác bytecode phù hợp để truy cập interface cần dùng và vẫn bảo vệ ràng buộc generic ở compile time. Ví dụ `greater(4, 9)` hợp lệ vì `Integer` phù hợp các bound; đừng truyền một lớp `Number` tùy ý không triển khai `Comparable` rồi trông chờ erasure kiểm tra giúp.

## 4. Xung đột chữ ký sau xóa kiểu: `name clash`

Java **không cho overload chỉ dựa vào type argument generic** nếu hai khai báo có cùng chữ ký sau erasure:

```java
import java.util.List;

class InvalidOverloads {
    void print(List<String> strings) {}

    void print(List<Integer> numbers) {} // Lỗi biên dịch: name clash
}
```

Hai phương thức trên đều có tham số bị xóa thành `List`, tương ứng:

```text
print(Ljava/util/List;)V
```

Đổi tên biến tham số hoặc đổi `String` thành `Long` không giúp gì. JVM không có đủ thông tin phân biệt hai lời gọi này theo type argument, nên compiler ngăn chúng ngay từ source.

Cách sửa tùy mục đích:

```java
import java.util.List;

class DistinctOperations {
    void printStrings(List<String> strings) {}
    void printNumbers(List<Integer> numbers) {}
}
```

Hoặc viết **một phương thức generic** khi hành vi xử lý chung thực sự giống nhau:

```java
static <T> void printAll(List<T> items) {
    items.forEach(System.out::println);
}
```

Nếu nghiệp vụ cần dispatch theo loại phần tử, cân nhắc API hoặc strategy mang type token (`Class<T>`) **khi cần**, thay vì giả định `List<String>` và `List<Integer>` sẽ có hai lớp runtime riêng.

Phân biệt với overload có raw parameter khác nhau: `work(List<String>)` và `work(ArrayList<Integer>)` **có thể** là hai overload vì erasure cho tham số đầu lần lượt là `List` và `ArrayList`. Vấn đề không nằm ở việc phương thức “có generic”, mà là **chữ ký sau erasure có trùng** theo quy tắc Java hay không.

### 4.1. Kiểu trả về không giúp phân biệt overload

Hai phương thức cùng tên, cùng danh sách kiểu tham số mà chỉ khác kiểu trả về không tạo thành overload hợp lệ trong Java:

```java
// Minh họa không biên dịch:
String pick() { return "a"; }
Integer pick() { return 1; }
```

Trên class file, descriptor gồm cả return type, nhưng **quy tắc khai báo và lựa chọn overload của ngôn ngữ Java** không cho bạn viết hai phương thức khác nhau chỉ bằng kiểu trả về. Các phương thức bridge mà compiler có thể tạo là trường hợp phục vụ override ở bytecode, không phải một tính năng cho phép programmer overload theo return type.

## 5. Override với generic và phương thức bridge

Type erasure có thể làm **descriptor của method được override khác chữ ký implementation**. Ví dụ:

```java
class Parent<T> {
    T value() { return null; }
}
class Child extends Parent<String> {
    @Override String value() { return "child"; }
}
```

Trong `Parent<T>`, `value()` bị xóa về `()Ljava/lang/Object;`. `Child.value()` trả `String` nên compiler có thể phát sinh một **bridge method** synthetic trả `Object` để lời gọi đa hình qua `Parent<String>` vẫn dispatch đúng. Kiểm chứng bằng `javap -v` hoặc `Method.isBridge()`. Đây **không phải** specialization của `Parent<T>`, và không phải method nào có generic cũng cần bridge.

Bài về **first bound** giải thích thêm trường hợp descriptor bridge dùng `Number` thay vì `Object`. Bài hiện tại tiếp tục trọng tâm về **chữ ký method và kiểu mảng sau erasure**.

## 6. Mảng trong JVM khác generic container

**Mảng có kiểu thành phần runtime**: một `String[]` biết tại runtime thành phần là `String`, còn `Number[]` biết thành phần là `Number`. Java mảng là **covariant**, ví dụ `String[]` có thể gán cho `Object[]`, và JVM kiểm tra khi ghi phần tử:

```java
String[] names = new String[1];
Object[] objects = names;
objects[0] = 42; // ArrayStoreException khi chạy
```

Ngược lại, `List<String>` không phải subtype của `List<Object>` vì generic Java **invariant**:

```java
List<String> names = List.of("Java");
// List<Object> objects = names; // Lỗi biên dịch
```

Sự khác biệt này là một nguyên nhân quan trọng khiến Java **không cho tạo trực tiếp mảng của đa số kiểu generic không reifiable**: cơ chế kiểm tra phần tử runtime của mảng không thể biết đầy đủ type argument để bảo vệ `List<String>[]`.

### 6.1. Kiểu reifiable là gì?

Một kiểu được gọi là *reifiable* nếu thông tin runtime của nó đủ để biểu diễn kiểu đó theo những điều kiện của ngôn ngữ Java. Một số trường hợp thường dùng:

- `String`, `Integer`, `Number`, các primitive type, và `String[]` là reifiable.
- `List` raw type và `List<?>` với **tất cả** type argument là wildcard không giới hạn là reifiable.
- `List<String>`, `List<? extends Number>`, type variable `T` không phải kiểu reifiable.
- Mảng có kiểu thành phần reifiable cũng là reifiable, ví dụ `List<?>[]`.

Đây là lý do:

```java
String[] names = new String[2];      // Hợp lệ
List<?>[] all = new List<?>[2];      // Hợp lệ
// List<String>[] bad = new List<String>[2]; // generic array creation
// T[] bad = new T[2];                       // Không hợp lệ trong generic context
```

Dòng `List<?>[]` không cho phép bạn nhét tùy ý phần tử sai vào một `List<String>` mà không được kiểm tra. Nó chỉ nói mỗi ô là một `List<?>`; ta đọc giá trị từng phần tử như `Object` nếu không có thông tin kiểu hẹp hơn.

### 6.2. Array type sau erasure vẫn giữ số chiều

Với các chữ ký:

```java
static <T> T[] sameArray(T[] input) {
    return input;
}

// Khai báo minh họa:
static <T extends Number> T[] echoNumbers(T[] input) {
    return input;
}
```

Dạng descriptor tương ứng là:

```text
sameArray:   ([Ljava/lang/Object;)[Ljava/lang/Object;
echoNumbers: ([Ljava/lang/Number;)[Ljava/lang/Number;
```

Dấu `[` không biến mất; chỉ **kiểu thành phần** là `Object` hoặc bound `Number`. Nếu là `T[][]` với `T` không bound, erasure là `Object[][]`, descriptor bắt đầu bằng `[[`.

Ở runtime, mảng **thực tế** được truyền có thể là `String[]`, và `sameArray` trả lại đúng mảng đó (không cần tạo một `Object[]` mới). Erasure của chữ ký không hề sửa kiểu mảng runtime của đối tượng đã được tạo.

### 6.3. Mảng của parameterized type trong khai báo vẫn có thể hợp lệ

So sánh hai tình huống:

```java
// Chỉ là khai báo trường, không cấp phát mảng:
List<String>[] buckets;
```

và:

```java
// Không biên dịch:
List<String>[] buckets = new List<String>[5];
```

**Khai báo** biến kiểu mảng chứa `List<String>` được phép; **tạo** mảng bằng `new List<String>[5]` thì không được, vì component type không reifiable. Một số API cũ cung cấp giá trị có kiểu này qua cast unchecked; điều đó không biến kiểu thành reifiable hoặc làm thao tác đọc hoàn toàn an toàn.

Tránh cách:

```java
// Minh họa workaround kém an toàn; KHÔNG khuyến nghị:
@SuppressWarnings("unchecked")
List<String>[] values = (List<String>[]) new List<?>[2];
```

Array runtime chỉ kiểm tra phần tử có là `List` hay không, không kiểm tra `List<String>`. Một alias dạng `Object[]` có thể đưa `List<Integer>` vào mảng, làm phát sinh **heap pollution**; khi đọc phần tử theo giả định `String`, chương trình có thể ném `ClassCastException`.

## 7. Tạo và xử lý mảng generic một cách an toàn

### 7.1. Nhận mảng do bên gọi cung cấp

Phương thức generic có thể **nhận, trả lại hoặc sao chép** một mảng kiểu `T[]` mà không tự dùng `new T[...]`:

```java
static <T> T[] duplicate(T[] input) {
    return input.clone();
}
```

`clone()` trên mảng giữ kiểu thành phần runtime của mảng gốc. Nếu `input` là `String[]`, mảng trả về cũng là `String[]`. Bản sao này là *shallow copy*: nếu phần tử là đối tượng mutable, chúng vẫn có thể được chia sẻ qua hai mảng.

### 7.2. Để bên gọi cấp phát qua constructor reference

Khi cần tạo mảng mới với component type phù hợp, truyền factory:

```java
import java.util.function.IntFunction;

static <T> T[] createArray(int length, IntFunction<T[]> factory) {
    if (length < 0) {
        throw new IllegalArgumentException("length must be >= 0");
    }
    return factory.apply(length);
}

// Bên trong một phương thức:
String[] names = createArray(3, String[]::new);
Integer[] ages = createArray(2, Integer[]::new);
```

Phương thức không giả định runtime class của `T` là gì; factory cung cấp mảng thật. Nên kiểm tra contract của factory trong API công khai nếu bạn cần đảm bảo length không bị thay đổi.

Một lựa chọn khác là dùng `java.lang.reflect.Array.newInstance(componentClass, length)` rồi cast sang `T[]`, nhưng Java không thể kiểm tra cast theo type variable ở runtime, thường phải đặt cast unchecked trong một hàm được đóng gói và kiểm thử cẩn thận. Với API hiện đại, factory `IntFunction<T[]>` hoặc `Collection.toArray(T[]::new)` thường rõ nghĩa hơn.

### 7.3. Dùng `List<T>` khi không thật sự cần mảng

Nếu dữ liệu cần kích thước động và API không bắt buộc array, `List<T>` là lựa chọn dễ dùng:

```java
List<String> names = new ArrayList<>();
names.add("Java");
names.add("Generics");
String first = names.get(0);
```

Khi thật sự cần mảng để gọi API khác:

```java
String[] array = names.toArray(String[]::new); // Java 11+
```

Tạo array tại ranh giới sử dụng giúp duy trì kiểm tra kiểu compile time của `List<T>`, đồng thời cung cấp đúng component type runtime của `String[]`.

### 7.4. Generic varargs và `@SafeVarargs`

Varargs thực chất được truyền dưới dạng **mảng**. Khai báo:

```java
static <T> void inspect(List<T>... groups) {
    // ...
}
```

liên quan tới mảng có component type không reifiable. Compiler có thể cảnh báo về *possible heap pollution* ở khai báo hoặc *unchecked generic array creation* tại lời gọi. Một ví dụ method được thiết kế chỉ **đọc mảng varargs và sao chép dữ liệu**:

```java
import java.util.ArrayList;
import java.util.List;

@SafeVarargs
static <T> List<T> flatten(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return result;
}
```

`@SafeVarargs` là cam kết của **người viết phương thức** rằng thân phương thức không thực hiện thao tác không an toàn với varargs array. Annotation không tự chứng minh tính đúng đắn, không làm mất erasure và không “biến” mảng generic thành một mảng reifiable. Trong Java 17, nó chỉ được áp dụng cho các phương thức hợp lệ như `static`, `final`, hoặc instance method `private` (và constructor); không gắn bừa lên phương thức instance có thể override.

Đặc biệt không để varargs array bị trả ra ngoài, ghi vào nó phần tử không cùng `T`, hoặc chuyển sang phương thức khác gây heap pollution nếu chưa kiểm soát đầy đủ hợp đồng. Nếu người dùng API thấy cảnh báo unchecked, hãy kiểm tra **cách phương thức xử lý array thật** trước khi dùng `@SafeVarargs`.

## 8. Ví dụ độc lập: chạy và quan sát erasure bằng Java 17

Ví dụ sau kiểm chứng các dạng chính: type variable không bound/bound, mảng `T[]`, mảng `List<?>[]`, override sinh bridge và Reflection API.

Tạo tệp **`ErasureSignatureDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ErasureSignatureDemo {
    static final class Box<T extends Number> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static <T> T identity(T value) {
        return value;
    }

    static <T extends Number & Comparable<T>> T greater(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static <T> T[] sameArray(T[] input) {
        return input;
    }

    static List<?>[] wildcardArray() {
        return new List<?>[] { List.of("Java"), List.of(17) };
    }

    static class Parent<T> {
        T value() {
            return null;
        }
    }

    static final class Child extends Parent<String> {
        @Override
        String value() {
            return "child";
        }
    }

    public static void main(String[] args) throws Exception {
        Box<Integer> box = new Box<>(7);
        String[] values = {"a", "b"};

        System.out.println(identity("ok"));
        System.out.println(greater(4, 9));
        System.out.println(box.get());
        System.out.println(Arrays.toString(sameArray(values)));
        System.out.println(wildcardArray()[0].get(0));
        System.out.println(new Child().value());

        Method identity = ErasureSignatureDemo.class.getDeclaredMethod(
                "identity", Object.class);
        Method greater = ErasureSignatureDemo.class.getDeclaredMethod(
                "greater", Number.class, Number.class);
        Method sameArray = ErasureSignatureDemo.class.getDeclaredMethod(
                "sameArray", Object[].class);
        Method bridge = Arrays.stream(Child.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow();

        System.out.println("identity: "
                + identity.getReturnType().getSimpleName()
                + " / " + identity.getGenericReturnType().getTypeName());
        System.out.println("greater: "
                + greater.getReturnType().getSimpleName()
                + " / " + greater.getGenericReturnType().getTypeName());
        System.out.println("sameArray: "
                + sameArray.getReturnType().getTypeName()
                + " / " + sameArray.getGenericReturnType().getTypeName());
        System.out.println("bridge: " + bridge.getName()
                + " -> " + bridge.getReturnType().getSimpleName());
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac --release 17 ErasureSignatureDemo.java
java ErasureSignatureDemo
```

Stdout chính xác:

```text
ok
9
7
[a, b]
Java
child
identity: Object / T
greater: Number / T
sameArray: java.lang.Object[] / T[]
bridge: value -> Object
```

Giải thích những dòng cuối:

- `getReturnType()` nhìn **kiểu raw sau erasure**: `Object`, `Number`, `Object[]`.
- `getGenericReturnType()` đọc **khai báo generic**, vì class file có thông tin Signature tương ứng: `T`, `T`, `T[]`.
- `Child` có một phương thức bridge (`isBridge() == true`) trả `Object` để khớp với chữ ký đã xóa của `Parent.value()`.
- Không có dòng nào chứng minh một instance `List<String>` giữ tham số `String` như một trường runtime của chính đối tượng list.

### 8.1. Xem descriptor thật bằng `javap`

Sau lệnh `javac`, chạy:

```bash
javap -p -s ErasureSignatureDemo
javap -p -s 'ErasureSignatureDemo$Box'
javap -p -s 'ErasureSignatureDemo$Child'
```

Lưu ý dấu nháy đơn quanh tên lớp lồng khi chạy trong Unix shell để tránh `$` bị khai triển như biến môi trường. Trong PowerShell cũng có thể dùng chuỗi nháy đơn như trên.

Các đoạn quan trọng của đầu ra:

```text
static <T> T identity(T);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;

static <T extends java.lang.Number & java.lang.Comparable<T>> T greater(T, T);
  descriptor: (Ljava/lang/Number;Ljava/lang/Number;)Ljava/lang/Number;

static <T> T[] sameArray(T[]);
  descriptor: ([Ljava/lang/Object;)[Ljava/lang/Object;
```

Với `ErasureSignatureDemo$Box`, các thành phần đáng quan sát gồm:

```text
private final T value;
  descriptor: Ljava/lang/Number;

T get();
  descriptor: ()Ljava/lang/Number;
```

Với `ErasureSignatureDemo$Child`, bạn sẽ thấy **hai** phương thức `value()` ở mức class file, với descriptor:

```text
()Ljava/lang/String;
()Ljava/lang/Object;
```

Dùng thêm:

```bash
javap -v -p 'ErasureSignatureDemo$Child'
```

để xem các cờ `ACC_BRIDGE`/`ACC_SYNTHETIC` của phương thức forwarding. `javap -s` vừa cho bạn xem chữ ký generic dưới dạng dễ đọc vừa hiển thị descriptor JVM; vì vậy hãy tìm đúng dòng `descriptor` khi đánh giá erasure.

### 8.2. Vì sao `getDeclaredMethod` dùng `Object.class` và `Number.class`?

Trong chương trình:

```java
getDeclaredMethod("identity", Object.class)
getDeclaredMethod("greater", Number.class, Number.class)
getDeclaredMethod("sameArray", Object[].class)
```

Reflection nhận **các kiểu tham số runtime**, tức là những kiểu ở descriptor đã xóa. Nếu thử tìm `identity(String.class)` bạn sẽ không tìm thấy một overload `identity(String)`: compiler không tạo riêng phương thức cho `String`.

Reflection có API `getGenericParameterTypes()` / `getGenericReturnType()` để xem **khai báo generic còn lưu trong metadata**, nhưng không dùng metadata ấy để xác định tham số kiểu của mọi lời gọi hoặc phần tử thật đang nằm trong collection. Đừng nhầm sự tồn tại của Signature attribute với khả năng runtime tự phân biệt `List<String>` và `List<Integer>`.

## 9. Các lỗi dễ gặp khi phối hợp phương thức và mảng

### Lỗi 1: tưởng `T[]` và `Object[]` giống nhau ở mọi cấp

Erasure có thể biến **chữ ký** `T[]` thành `Object[]`, nhưng bạn không thể lấy một mảng `Object[]` bất kỳ, ép vô điều kiện sang `String[]` rồi mong nó hoạt động. Runtime mảng vẫn biết component type thật và cast sai sẽ gây `ClassCastException`:

```java
Object[] objects = new Object[2];
// String[] strings = (String[]) objects; // Cast có thể biên dịch nhưng lỗi runtime
```

Phân biệt **method descriptor đã xóa** với **kiểu class thực của một mảng** khi debug.

### Lỗi 2: nhầm `new List<?>[n]` với `new List<String>[n]`

Cái đầu hợp lệ vì `List<?>` là reifiable; cái sau không hợp lệ vì `List<String>` không reifiable. Dấu `?` ở đây không phải một cách “bỏ qua hết type safety”, mà phản ánh rằng không có lời hứa cụ thể về kiểu phần tử của từng List.

### Lỗi 3: dùng `@SuppressWarnings("unchecked")` để “chữa” erasure

Suppress chỉ ẩn cảnh báo; cast không được kiểm tra vẫn tồn tại. Chỉ dùng khi API buộc tương tác với legacy hoặc runtime reflection, và bạn có invariant rõ ràng để chứng minh kiểu phần tử được bảo toàn. Với mảng, ưu tiên constructor reference, factory hoặc chuyển dữ liệu qua collection.

### Lỗi 4: chọn overload hoặc override theo type argument

Sau erasure, `List<String>` và `List<Integer>` có cùng raw type `List`; hai phương thức cùng tên, cùng tham số raw tương ứng có thể `name clash`. Với subclass generic, đôi khi compiler **cần** tạo bridge dù bạn không viết phương thức đó. Hãy kiểm tra `javap` trước khi kết luận lớp có một “phương thức lạ” do thư viện khác thêm.

### Lỗi 5: tin rằng xóa kiểu làm biến mất mọi thông tin generic

`javap -v` có thể hiển thị Signature attribute; `Method.getGenericReturnType()` có thể trả `TypeVariable`, `ParameterizedType` hoặc `GenericArrayType`. Nhưng metadata mô tả **khai báo**, không luôn nói được loại phần tử thực tế của mỗi instance tại runtime.

### Lỗi 6: bỏ qua `ArrayStoreException` vì compile time đã chấp nhận phép gán

Mảng có covariance và store check khi chạy. Ví dụ `Object[] alias = new String[1]` hợp lệ, nhưng ghi `Integer` qua alias sẽ ném `ArrayStoreException`. Điều này khác lỗi `ClassCastException` do đọc sai generic ở collection sau heap pollution; hai loại lỗi có nguyên nhân khác nhau.

## 10. Quy trình chẩn đoán một lỗi liên quan erasure

Khi gặp `name clash`, `generic array creation`, `ClassCastException`, `ArrayStoreException`, hoặc phương thức override hoạt động khó hiểu:

1. **Viết lại kiểu source đầy đủ** gồm type variable, type argument, bound và mọi chiều mảng.
2. **Tính erasure**: type variable về bound đầu tiên (hoặc `Object`), parameterized type về raw type, array xóa kiểu thành phần nhưng giữ số chiều.
3. **So sánh chữ ký phương thức sau erasure** để tìm overload trùng nhau; với override, so sánh descriptor lớp cha/lớp con để nhận diện bridge có thể cần.
4. **Kiểm tra kiểu runtime thực** khi dữ liệu là mảng (`getClass().getComponentType()`) hoặc khi có cast unchecked.
5. **Dùng `javap -p -s`**, và `javap -v` khi cần biết bridge/Signature; dùng reflection với các kiểu tham số đã xóa.
6. **Thay đổi hợp đồng API đúng chỗ**: đổi tên overload, dùng một phương thức generic, chọn collection hoặc array factory, tránh raw/unchecked cast.
7. **Biên dịch và thử runtime** với cả đầu vào hợp lệ lẫn đầu vào dễ lộ lỗi (mảng khác component type, nhiều subtype, subclass overriding).

Bảng quyết định nhanh:

| Tình huống | Kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `print(List<String>)` xung đột `print(List<Integer>)` | Cùng descriptor `List` | Đổi tên theo chức năng hoặc hợp nhất thành phương thức generic. |
| `T[] result = new T[n]` | Component `T` không reifiable | Nhận array factory `IntFunction<T[]>` hoặc mảng từ caller. |
| `List<String>[] x = new List<String>[n]` | Component parameterized không reifiable | Dùng `List<List<String>>` hoặc `List<?>[]` nếu wildcard đúng ý nghĩa. |
| Caller cần `String` nhưng `getReturnType()` trả `Object` | Descriptor đã erasure | Xem `getGenericReturnType()` và cast do compiler sinh; đừng kỳ vọng reflection truy xuất specialization riêng. |
| `Child` xuất hiện thêm method synthetic trả `Object` | Generic override có bridge | Xem cờ `ACC_BRIDGE`, quan hệ override và class hierarchy. |
| `String[]` qua alias `Object[]` nhận `Integer` | Runtime store check | Tránh ghi sai component type; sửa cấu trúc dữ liệu nếu cần đa kiểu. |

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Sau erasure, descriptor cho phương thức bên dưới là gì?

```java
static <T extends Number> T[] echo(T[] input) {
    return input;
}
```

<details>
<summary>Đáp án</summary>

`([Ljava/lang/Number;)[Ljava/lang/Number;` vì `T` bị xóa về bound `Number`, còn kiểu mảng vẫn giữ một chiều.

</details>

**Câu 2.** Tại sao không được overload `find(List<String>)` và `find(List<Integer>)` trong cùng một lớp?

<details>
<summary>Đáp án</summary>

Cả hai danh sách tham số bị xóa thành `List` nên không còn khác biệt về chữ ký phương thức theo quy tắc overload Java; compiler báo `name clash`.

</details>

**Câu 3.** Tại sao `new List<?>[3]` hợp lệ nhưng `new List<String>[3]` không hợp lệ?

<details>
<summary>Đáp án</summary>

Mảng yêu cầu kiểu thành phần reifiable; `List<?>` reifiable còn `List<String>` thì không. JVM runtime component check không thể xác minh riêng type argument `String`.

</details>

**Câu 4.** Một lớp con override `T get()` của lớp cha generic với `String get()`. Vì sao bytecode lớp con có thể chứa cả `Object get()`?

<details>
<summary>Đáp án</summary>

`T` ở lớp cha không bound bị xóa thành `Object`; compiler sinh một bridge synthetic chuyển tiếp tới `String get()` nhằm giữ dispatch đa hình qua reference kiểu lớp cha.

</details>

**Câu 5.** `getGenericReturnType()` của `identity` hiển thị `T`. Điều đó có chứng minh mỗi lần gọi `identity(5)` tạo một phương thức bytecode mới cho `Integer`?

<details>
<summary>Đáp án</summary>

Không. `T` là metadata về **khai báo** generic của một phương thức, trong khi descriptor thực dùng `Object`. Compiler kiểm tra suy luận kiểu cho từng lời gọi; nó không tạo specialization mới của `identity` cho mỗi kiểu tham số.

</details>

## 12. Ghi nhớ

Để đọc đúng erasure, phải tách **ba tầng**: kiểu generic mà Java compiler kiểm tra ở source, **descriptor JVM** sau xóa kiểu và **kiểu runtime thật của đối tượng/mảng**. Trong chữ ký phương thức, `T` trở thành `Object` hoặc bound đầu tiên; `List<String>` và `List<Integer>` đều về `List`; override generic có thể cần bridge. Trong kiểu mảng, `T[]` erases thành `Object[]` hoặc mảng của bound, nhưng JVM vẫn kiểm tra component type của mảng được tạo. Đây là nền tảng để tránh overload trùng chữ ký, cast unchecked nguy hiểm và tạo mảng generic sai cách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-005">Phép ép kiểu được trình biên dịch chèn vào khi đọc dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phép ép kiểu được trình biên dịch chèn vào khi đọc dữ liệu trong Java Generics

## 1. Vì sao đọc List<String> vẫn có thể gây ClassCastException?

Giả sử chương trình khai báo:

~~~java
List<String> words = new ArrayList<>();
words.add("Java");
String first = words.get(0);
~~~

Ở **mã nguồn**, phương thức get được nhìn như trả về String vì biến words có kiểu List<String>. Tuy nhiên, cách Java triển khai generics chủ yếu dựa trên **type erasure (xóa kiểu)**: trong bytecode, phương thức get của List có kiểu trả về đã xóa là Object. Khi giá trị từ lời gọi đó cần được sử dụng như một String, trình biên dịch có thể chèn lệnh kiểm tra và ép kiểu ở **vị trí sử dụng kết quả**.

Sơ đồ ý tưởng:

~~~text
Mã nguồn:       String first = words.get(0);
Kiểu tĩnh:      words.get(0) được kiểm tra như String
Sau xóa kiểu:   List.get(int) trả Object
Bytecode:       gọi List.get(int), checkcast String, lưu vào first
~~~

Lệnh JVM tương ứng là **checkcast**. Nó kiểm tra tại thời điểm chạy rằng giá trị tham chiếu nhận được là null hoặc tương thích với lớp/kiểu được kiểm tra. Nếu một đối tượng khác kiểu thực sự được dùng thay String, JVM ném ClassCastException.

Điều quan trọng: **generics được kiểm tra ở thời điểm biên dịch, còn checkcast thực hiện kiểm tra runtime tại nơi kết quả bị xóa kiểu được sử dụng với kiểu cụ thể.** Hai cơ chế phối hợp để giữ cú pháp generic thuận tiện mà vẫn duy trì tính tương thích với mã Java trước khi có generics.

Bài này tập trung vào **phép ép kiểu do compiler chèn khi đọc hoặc sử dụng dữ liệu generic**, không nhằm mô tả mọi dạng bytecode hay mọi lý do phát sinh ClassCastException.

## 2. Kiểu ở nguồn và kiểu sau khi xóa

Hãy xét interface và class đơn giản:

~~~java
interface Store<T> {
    T get();
}

final class Box<T> implements Store<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }
}
~~~

Khi viết:

~~~java
Store<String> store = new Box<>("Generics");
String content = store.get();
~~~

trình biên dịch biết giá trị cần đọc có kiểu **String**. Nhưng giới hạn trên của T trong Store<T> là Object nếu không có bound tường minh, nên chữ ký JVM sau xóa kiểu của get về cơ bản trả **Object**. Ở chỗ gán cho content, có thể cần kiểm tra Object thành String.

Để tư duy, có thể so sánh với dạng *gần tương đương* sau khi xóa kiểu (đây **không phải** chính xác toàn bộ source code mà javac tự tạo):

~~~java
interface Store {
    Object get();
}

final class Box implements Store {
    private final Object value;

    Box(Object value) {
        this.value = value;
    }

    @Override
    public Object get() {
        return value;
    }
}

// Tại phía gọi:
Store store = new Box("Generics");
String content = (String) store.get();
~~~

Mã bên trên chỉ giúp hiểu hình dạng API bị xóa generic và vị trí cast. Trong chương trình thật, compiler cũng có thể phát sinh bridge method, thay đổi descriptor theo bound, hoặc tối ưu bytecode tùy ngữ cảnh; không được xem mã minh họa này là bản decompile chính xác.

### Vì sao có lúc thấy cast, có lúc không?

So sánh:

~~~java
List<String> names = new ArrayList<>();
names.add("Java");

Object asObject = names.get(0);
String asString = names.get(0);
~~~

Trong trường hợp asObject, kết quả trả về Object của List.get có thể được nhận trực tiếp vào biến Object; **không cần checkcast String** chỉ để thực hiện phép gán đó.

Ở dòng asString, compiler phải bảo đảm kết quả phù hợp với String nên chèn **checkcast java/lang/String** theo dạng bytecode thông thường.

Như vậy, phát biểu “đọc List<String> luôn chèn một cast String” là **quá tuyệt đối**. Compiler dựa trên cách giá trị được sử dụng; nó có thể chèn cast khi kiểu đã xóa không đủ cho kiểu mong muốn. Thêm nữa, mức độ cast thực tế phụ thuộc những phép kiểm tra đã có và các quyết định sinh mã của compiler.

## 3. Nguồn gốc checkcast: chữ ký List.get sau erasure

Khai báo nguồn khái niệm của List có:

~~~java
interface List<E> {
    E get(int index);
}
~~~

Trong JVM, do E không có giới hạn trên cụ thể, kiểu trả về ở method descriptor bị xóa thành Object:

~~~text
get(int) → Object
JVM descriptor: (I)Ljava/lang/Object;
~~~

Generic signature bổ sung thông tin E trong metadata của class file cho compiler và công cụ, nhưng **method descriptor dùng khi thực thi** vẫn nói Object. JVM không dựa trên generic signature để xác minh rằng mọi phần tử của một List<String> là String ngay lúc gọi List.get.

Khi javac phân tích câu:

~~~java
String value = names.get(0);
~~~

nó dùng generic signature để biết kết quả có kiểu tĩnh String, rồi phát sinh bytecode có thể được hình dung như:

~~~text
aload        names
iconst_0
invokeinterface java/util/List.get:(I)Ljava/lang/Object;
checkcast    java/lang/String
astore       value
~~~

Đây là dạng mô tả **lược giản** các opcode, không chứa offset và constant-pool index chính xác. Dùng javap -c -p trên chương trình của bạn sẽ thấy lệnh thực tế của phiên bản javac đang chạy.

### Checkcast không tạo đối tượng mới

checkcast **không chuyển đổi nội dung** từ Integer thành String, không gọi toString() và cũng không sao chép đối tượng. Với một tham chiếu hợp lệ, nó giữ nguyên tham chiếu. Nếu kiểu đối tượng không tương thích với loại được kiểm tra, nó ném ClassCastException. Vì vậy:

~~~java
Object value = Integer.valueOf(17);
// String text = (String) value; // Chạy tới đây sẽ ném ClassCastException.
String text = value.toString();  // Chuyển nội dung thành "17", là thao tác khác.
~~~

Mảnh mã này chỉ nhằm phân biệt **cast tham chiếu** và **chuyển đổi biểu diễn dữ liệu**.

## 4. Ví dụ đầy đủ: compiler kiểm tra kiểu dù dữ liệu bị nhiễm

Lưu chương trình sau thành **ErasureCastDemo.java**. Đây là chương trình hoàn chỉnh, chạy trên Java 17+.

~~~java
import java.util.ArrayList;
import java.util.List;

public class ErasureCastDemo {
    static <T> T identity(T value) {
        return value;
    }

    static final class Holder<T> {
        private final T value;

        Holder(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("Java");

        String name = names.get(0);
        System.out.println("name=" + name);

        Holder<String> holder = new Holder<>("Generics");
        String fromHolder = holder.get();
        System.out.println("holder=" + fromHolder);

        String text = identity("Typed");
        System.out.println("identity=" + text);

        List<String> contaminated = new ArrayList<>();

        // Raw reference làm mất kiểm tra kiểu tham số khi gọi add.
        List raw = contaminated;
        raw.add(42); // javac -Xlint:unchecked phát cảnh báo.

        Object first = contaminated.get(0);
        System.out.println("asObject=" + first);

        try {
            String failing = contaminated.get(0);
            System.out.println(failing);
        } catch (ClassCastException error) {
            System.out.println("asString=ClassCastException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -Xlint:unchecked ErasureCastDemo.java
java ErasureCastDemo
~~~

Trong lúc biên dịch, javac phát **unchecked warning** tại dòng raw.add(42) vì gọi add trên raw List. Cảnh báo là thông báo của compiler; không thuộc stdout của chương trình.

**Stdout chính xác khi chạy:**

~~~text
name=Java
holder=Generics
identity=Typed
asObject=42
asString=ClassCastException
~~~

Giải thích diễn biến:

1. names là List<String>, chứa đúng chuỗi Java nên phép lấy ra và sử dụng như String thành công.
2. Holder<String> có get() trả T tại source, nhưng T không có bound cụ thể nên descriptor của get trả Object sau erasure. Caller dùng String cần kiểm tra cast tương ứng.
3. Phương thức identity cũng trả T ở source và Object ở descriptor đã xóa. Caller vẫn có thể sử dụng kết quả như String khi type inference suy ra T là String.
4. contaminated ban đầu có kiểu List<String> và trỏ đến một ArrayList; alias raw cùng trỏ đến **chính đối tượng đó**. raw.add(42) vượt qua kiểm tra generic của lời gọi add, tạo tình trạng **heap pollution**: giá trị runtime trong cấu trúc không khớp với kiểu tham số hóa mà một tham chiếu khác đang khai báo.
5. Khi đọc vào Object, số nguyên đóng hộp Integer vẫn là một Object hợp lệ, nên chương trình in asObject=42.
6. Khi đọc vào String, compiler phát sinh cast cần thiết, JVM phát hiện Integer không phải String và ném ClassCastException; catch in dòng xác nhận lỗi.

Cần nhìn đúng nguồn lỗi: **ArrayList.get không tự đòi phần tử là String tại runtime**. Lỗi xuất hiện khi kết quả Object được dùng như String. Nếu không có raw alias hoặc đường không an toàn tương đương, compiler sẽ chặn việc thêm Integer trực tiếp vào List<String>.

### Thử thay raw bằng lời gọi generic an toàn

~~~java
List<String> safe = new ArrayList<>();
safe.add("hello");
// safe.add(42); // Không biên dịch: int không chuyển được thành String.
~~~

Lệnh bị comment là ví dụ **cố ý không hợp lệ**. Với List<String>, lời gọi add thông thường được kiểm tra ở compile time. Không có bất kỳ nhu cầu tự ép kiểu Integer thành String nào.

## 5. Kiểm tra bytecode bằng javap

Sau khi chạy javac, dùng công cụ đi kèm JDK:

~~~bash
javap -c -p ErasureCastDemo
javap -c -p 'ErasureCastDemo$Holder'
~~~

Lệnh thứ nhất hiển thị bytecode của main. Hãy tìm đoạn gọi List.get và lời gọi Holder.get, identity. Ở các trường hợp kết quả được nhận dưới dạng String, có thể quan sát opcode checkcast với lớp java/lang/String. Trong đoạn đọc vào Object, không cần cast String ở vị trí phép gán đó.

Lệnh thứ hai hiển thị method descriptor hoặc kiểu đã xóa của get trong Holder: nó trả Object vì T không có bound tường minh. Khi muốn xem thêm generic signature, dùng:

~~~bash
javap -v -p 'ErasureCastDemo$Holder'
~~~

Thông tin Signature trong class file có thể mô tả kiểu generic T, còn bytecode và descriptor dùng kiểu đã xóa. **Signature không phải runtime type check**.

Đối với PowerShell, cmd hoặc bash, quy tắc quote tên nested class có thể khác nhau. Với bash và các shell POSIX, dấu nháy đơn ở ví dụ trên bảo vệ ký tự $. Nếu dùng cmd.exe trên Windows, có thể nhập tên lớp trong dấu nháy kép thay vì dấu nháy đơn. Mục đích chính là tránh để shell diễn giải ký tự $ trước khi javap nhận tên lớp.

Không nên khẳng định số offset bytecode hay thứ tự chỉ thị luôn giống nhau giữa các bản JDK; hãy kiểm tra chính file .class mà compiler tạo ra.

## 6. Bound của T thay đổi kiểu bị xóa và vị trí cast

Khi T có giới hạn trên cụ thể, erasure không nhất thiết là Object.

~~~java
class NumberBox<T extends Number> {
    private final T value;

    NumberBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
~~~

Dạng nhìn tương đương sau erasure:

~~~text
NumberBox.get() trả Number
Nếu caller muốn Number: không cần cast Integer
Nếu caller muốn Integer: có thể cần checkcast Integer
~~~

Ví dụ sử dụng:

~~~java
NumberBox<Integer> box = new NumberBox<>(123);

Number asNumber = box.get();   // Giá trị trả về sau erasure là Number.
Integer asInteger = box.get(); // Có thể cần checkcast Integer.
~~~

**Quy tắc tổng quát:** erasure của một type variable lấy cận trái nhất trong các giới hạn của nó (nếu không có bound, dùng Object). Kiểu được trả về hoặc nhận vào sau erasure là cơ sở để compiler quyết định những phép chuyển đổi cần được thực hiện ở nơi sử dụng.

Không nên ghi nhớ rằng mọi T luôn thành Object. Với T extends Number, T ở đây bị xóa thành Number. Với tham số có nhiều bound, thứ tự bound quan trọng đối với erasure và giới hạn được phép ghi cũng có quy tắc của Java.

## 7. Generic method, biến cục bộ và kiểu mà người đọc “thấy”

Một generic method có thể suy luận T từ đối số và/hoặc kiểu đích:

~~~java
static <T> T first(T left, T right) {
    return left;
}
~~~

Nếu viết:

~~~java
String x = first("A", "B");
~~~

compiler cho phép kết quả sử dụng như String. Tuy nhiên descriptor của generic method khi T không có bound vẫn trả Object; bytecode ở caller có thể phải kiểm tra cast String.

Tương tự:

~~~java
var x = first("A", "B"); // x có kiểu tĩnh String.
~~~

var suy luận kiểu biến cục bộ từ biểu thức khởi tạo, **không khiến generic được giữ lại ở runtime**. Nếu kết quả generic đã xóa thành Object cần dùng như String, cast vẫn có thể được chèn vào vị trí thích hợp.

### Một Object variable không đổi thành String nhờ gán sau đó

~~~java
Object x = first("A", "B");
// String y = x;     // Không biên dịch.
String y = (String) x; // Có explicit checked cast.
~~~

Ở đây compiler không tự biến kiểu tĩnh Object của x thành String chỉ vì ta muốn khai báo biến y. Cần ép kiểu tường minh khi chỉ biết x là Object.

Vì vậy hãy phân biệt:

- **Cast ẩn do compiler chèn** để thực thi việc dùng kết quả generic sau xóa kiểu, khi kiểu tĩnh nguồn hợp lệ.
- **Cast tường minh do người lập trình viết** khi biểu thức đang có kiểu Object/supertype và cần dùng như kiểu con.
- **Unchecked cast** sang một kiểu tham số hóa mà runtime không thể kiểm tra đầy đủ, ví dụ chuyển raw List thành List<String>.

Ba trường hợp không có cùng ý nghĩa an toàn.

## 8. Checked cast và unchecked cast khác nhau thế nào?

Ví dụ:

~~~java
Object value = "text";
String s = (String) value; // Runtime kiểm tra đối tượng có là String không.
~~~

Dạng cast đến String là **reifiable type**, có thể được JVM kiểm tra tại runtime. Nếu value thực sự chứa Integer, cast ném ClassCastException.

Ngược lại:

~~~java
List raw = new ArrayList();
List<String> strings = (List<String>) raw; // Unchecked cast.
~~~

Runtime có thể kiểm tra đối tượng là List, nhưng **không thể xác minh mọi phần tử là String** chỉ bằng phép cast List<String>, do tham số generic thường bị xóa. javac có thể cảnh báo unchecked. Nếu trong raw có phần tử Integer, lấy phần tử ra dùng như String mới có thể phát sinh ClassCastException.

Sự khác biệt thực chất:

| Phép chuyển đổi | Kiểm tra gì tại runtime? | Nguy cơ |
| --- | --- | --- |
| Object → String | Kiểu runtime của tham chiếu có phù hợp String? | Có thể ném ClassCastException ngay khi cast. |
| Raw List → List<String> | Thường chỉ kiểm tra được lớp/interface List; không kiểm chứng String cho phần tử | Có thể gây heap pollution và lỗi khi lấy phần tử ra. |
| Đọc List<String>.get vào String | Kiểu runtime của giá trị lấy ra có tương thích String? | Lỗi nếu phần tử thực tế sai kiểu. |
| Đọc List<String>.get vào Object | Không cần kiểm tra String để dùng dưới dạng Object | Phần tử sai kiểu vẫn đọc được như Object. |

Hãy bật -Xlint:unchecked và xử lý cảnh báo tại nguồn. Đừng sử dụng SuppressWarnings chỉ để ẩn dấu hiệu dữ liệu không phù hợp với khai báo generic.

## 9. Vì sao lỗi có thể xuất hiện muộn?

Heap pollution thường tạo ra lỗi ở một vị trí **khác nơi dữ liệu bị đưa vào**:

~~~java
List<String> values = new ArrayList<>();
List raw = values;
raw.add(10);          // Gốc gây nhiễm dữ liệu; unchecked warning.

Object ok = values.get(0);   // Vẫn hợp lệ, vì Integer là Object.
// String bad = values.get(0); // Khi chạy sẽ ném ClassCastException.
~~~

Phép gán vào List<String> ban đầu **không tự bảo đảm runtime phần tử luôn là String** nếu mã khác đã có quyền truy cập qua raw type, unchecked conversion, reflection hoặc những đường không an toàn khác.

Mẫu lỗi này dễ làm lập trình viên truy nguyên sai chỗ: stack trace trỏ vào dòng đọc nhưng nguyên nhân nằm ở lệnh ghi trước đó. Vì vậy, khi gặp lỗi ClassCastException tại thao tác get, hãy kiểm tra cả các đường thêm dữ liệu và các cast/alias liên quan.

### Không thể tự “cứu” bằng instanceof List<String>

Do generics không reifiable theo cách thông thường, không được viết:

~~~java
// if (obj instanceof List<String>) { } // Không biên dịch.
~~~

Có thể kiểm tra:

~~~java
if (obj instanceof List<?> list) {
    // Chỉ biết đây là List với kiểu phần tử chưa xác định.
}
~~~

Nhưng List<?> không chứng minh mọi phần tử là String. Để bảo đảm dữ liệu thực sự là chuỗi, cần kiểm tra từng phần tử hoặc sử dụng API xác thực dữ liệu tại ranh giới tin cậy.

Ví dụ kiểm tra bằng vòng lặp:

~~~java
static List<String> copyStrings(List<?> input) {
    List<String> result = new ArrayList<>();

    for (Object element : input) {
        if (!(element instanceof String s)) {
            throw new IllegalArgumentException("Expected String");
        }
        result.add(s);
    }

    return result;
}
~~~

Ví dụ này dùng pattern matching cho instanceof có trong Java 17. Nó tạo một danh sách mới chỉ chứa chuỗi sau khi kiểm tra từng phần tử. Nếu ứng dụng cần chấp nhận null, phải xác định và xử lý chính sách null riêng vì điều kiện instanceof String ở trên không chấp nhận null.

## 10. Kiểm tra với lớp generic do mình định nghĩa

Một chương trình có thể có:

~~~java
class ValueHolder<T> {
    T value;

    ValueHolder(T value) {
        this.value = value;
    }

    T read() {
        return value;
    }
}
~~~

Khi được khởi tạo:

~~~java
ValueHolder<String> holder = new ValueHolder<>("OK");
String result = holder.read();
~~~

type erasure khiến trường value và kiểu trả về của read có descriptor Object, còn compiler vẫn kiểm tra holder là ValueHolder<String>. Phép lấy dữ liệu để dùng như String có thể phải nhận một cast tại nơi gọi, giống List.get.

Điểm cần chú ý: **không có một bản class riêng dành cho từng loại tham số String/Integer của ValueHolder** trong cơ chế generic thông thường. Runtime class về cơ bản là cùng một ValueHolder, còn compile time sử dụng generic signature để ngăn những phép gán không an toàn.

### Trường hợp kiểu dữ liệu sai được tạo bằng cách nào?

Nếu người lập trình chỉ dùng API được tham số hóa đúng, họ không thể viết:

~~~java
ValueHolder<String> box = new ValueHolder<>("OK");
// box.value = Integer.valueOf(7); // Không biên dịch.
~~~

Một lệnh sai kiểu muốn lọt qua thường phải đi qua raw type, unchecked operation, reflection, hoặc mã/thư viện vi phạm hợp đồng generic. Cast tại điểm đọc là **lớp phòng vệ cuối** trước lúc sử dụng dữ liệu theo kiểu cụ thể; nó không thay thế việc ngăn dữ liệu sai kiểu ngay từ đầu.

## 11. Khác biệt giữa cast tại nơi đọc và bridge method

Cả hai có thể xuất hiện vì type erasure nhưng mục đích khác nhau:

- **Cast tại caller khi dùng giá trị:** kiểm tra kết quả đã xóa kiểu, ví dụ Object từ List.get phải được dùng như String.
- **Bridge method do compiler tạo:** giữ tương thích và đa hình cho phương thức được ghi đè/triển khai khi chữ ký erasure của chúng khác với chữ ký cụ thể.

Ví dụ minh họa bridge:

~~~java
interface Provider<T> {
    T get();
}

final class TextProvider implements Provider<String> {
    @Override
    public String get() {
        return "Hello";
    }
}
~~~

Do interface có get() trả Object sau erasure, compiler có thể sinh bridge method get() trả Object trong TextProvider, chuyển tiếp đến get() trả String để bảo toàn cơ chế dispatch.

Bridge method **không phải** phép cast String được chèn tại mọi chỗ gọi List.get. Một bytecode listing có thể đồng thời có cả bridge và checkcast, nhưng cần đọc từng method và từng call site để hiểu chúng đang giải quyết vấn đề gì.

## 12. Những hiểu lầm phổ biến

### “Compiler lưu String bên trong ArrayList nên get trả String trực tiếp”

Không đúng ở method descriptor runtime của List.get: phương thức vẫn trả Object sau erasure. Generic signature giúp compiler hiểu phần tử là String ở compile time, rồi chèn cast khi nơi sử dụng đòi hỏi.

### “Đã khai báo List<String> thì chắc chắn runtime chỉ chứa String”

Không hoàn toàn đúng nếu có raw alias, unchecked conversion, reflection hoặc mã không tuân thủ hợp đồng. Một List<String> thuần được thao tác qua API generic đúng sẽ được compiler kiểm tra khi thêm, nhưng erasure không tạo kiểm tra String tại mọi lệnh add của raw List.

### “ClassCastException chứng minh dòng get sai”

Lỗi thường lộ ở chỗ đọc và cast, trong khi đối tượng sai có thể đã được đưa vào collection từ trước. Hãy lần theo cả bên ghi lẫn bên đọc.

### “Cast Object thành List<String> chắc chắn đã kiểm tra kiểu mọi phần tử”

Sai. Phần runtime của generic parameter thường không có đủ thông tin để kiểm tra danh sách đã đúng kiểu phần tử chưa. Đó là lý do unchecked cast/heap pollution nguy hiểm.

### “Tất cả generic type parameter đều bị xóa thành Object”

Không. Tham số kiểu có bound như T extends Number bị xóa về Number. Các bridge method, cast cần thiết và descriptor sẽ phụ thuộc quan hệ bound, override và nơi sử dụng cụ thể.

### “Compiler tự chèn cast khi đọc nên không cần quan tâm warning”

Cast được chèn là cơ chế thực thi kiểm tra kiểu ở điểm dùng; nếu dữ liệu bị sai kiểu, chương trình vẫn có thể ném ClassCastException. Sửa unchecked warning ở đường ghi là biện pháp phòng ngừa quan trọng.

## 13. Quy trình đọc lỗi ClassCastException trong mã generic

1. **Xác định kiểu tĩnh của biểu thức đọc**: ví dụ List<String>.get trả String trong mắt compiler.
2. **Tìm method descriptor sau erasure**: List.get trả Object, Holder<T>.get có thể trả Object hoặc bound tương ứng.
3. **Xem giá trị được dùng dưới dạng gì**: gán vào Object hay String, truyền đến API nào, có explicit cast hay không.
4. **Kiểm tra bytecode nếu cần**: dùng javap -c -p để tìm checkcast sau lời gọi generic.
5. **Truy ngược đường ghi**: raw collection, unchecked conversion, alias, reflectively added value hoặc dữ liệu từ nguồn khác.
6. **Giữ lại thông tin kiểu tĩnh chính xác**: tránh raw type, dùng List<T> hoặc List<?> thay vì ép thành tham số giả định.
7. **Xác thực dữ liệu ở ranh giới**: với nguồn không đáng tin, kiểm tra phần tử trước khi xem như List<String>.
8. **Chạy lại ví dụ với -Xlint:unchecked** và loại bỏ nguyên nhân warning phù hợp.

Khi đọc kết quả javap, nhớ rằng công cụ mô tả bytecode do compiler tạo cho phiên bản đang chạy. Các vị trí cast có thể phụ thuộc ngữ cảnh hoặc tối ưu compiler, nhưng **ý nghĩa ngữ nghĩa** về kiểm tra kiểu khi dùng kết quả generic phải được bảo toàn.

## 14. Câu hỏi tự kiểm tra

**1.** Vì sao Object obj = listOfStrings.get(0) có thể chạy được khi phần tử thật là Integer, còn String s = listOfStrings.get(0) ném ClassCastException?

**Đáp án:** List.get trả Object sau erasure. Dùng dưới dạng Object không cần kiểm tra String; dùng dưới dạng String cần kiểm tra tương thích runtime, thất bại đối với Integer.

**2.** Compiler có thêm cast trong định nghĩa phương thức get của ArrayList không?

**Đáp án:** Không nên hiểu theo cách đó. Ở các vị trí caller dùng kết quả trả Object đã bị xóa kiểu như String, compiler có thể chèn checkcast. Nội bộ phương thức get không phải nơi tự động xác nhận generic parameter String cho mọi instance.

**3.** Với T extends Number, kiểu đã xóa của T là gì và khi nào cần cast Integer?

**Đáp án:** T bị xóa về Number. Dùng kết quả dưới dạng Number không cần cast Integer chỉ để gán; dùng kết quả như Integer có thể cần checkcast Integer.

**4.** Vì sao phép ép raw List sang List<String> phát cảnh báo unchecked dù cast đến String là runtime-checked?

**Đáp án:** Runtime xác minh đối tượng là List, nhưng không thể xác minh mọi phần tử có kiểu String thông qua generic parameter bị xóa. Với Object → String, lớp String là kiểu có thể kiểm tra được trực tiếp.

**5.** Khi stack trace báo ClassCastException ở dòng đọc từ List<String>, điểm đầu tiên nên kiểm tra là gì?

**Đáp án:** Xem lệnh nào đã đưa phần tử sai kiểu vào danh sách, đặc biệt raw alias, unchecked cast, API bên ngoài hoặc reflection. Dòng đọc thường là chỗ lỗi bị phát hiện, không nhất thiết là chỗ phát sinh dữ liệu sai.

**Ghi nhớ:** Java Generics bảo vệ kiểu phần lớn ở compile time. Vì erasure loại bỏ phần lớn thông tin tham số generic khỏi method descriptor runtime, **javac chèn những cast cần thiết ở vị trí dùng giá trị generic như kiểu cụ thể**. Một chương trình tuân thủ generic contract sẽ đi qua các cast đó an toàn; dữ liệu bị nhiễm bởi thao tác unchecked có thể làm chúng thất bại ở runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-006">Vì sao Box&lt;String&gt; và Box&lt;Integer&gt; dùng cùng lớp lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `Box<String>` và `Box<Integer>` dùng cùng lớp lúc chạy?

## Mục tiêu học tập

Khi học Java generics, ta thường thấy hai biến có hai kiểu khác nhau:

```java
Box<String> words = new Box<>("Java");
Box<Integer> numbers = new Box<>(42);
```

Ở **mã nguồn**, `Box<String>` và `Box<Integer>` là hai kiểu tham số hóa khác nhau, được trình biên dịch kiểm tra theo những ràng buộc khác nhau. Tuy nhiên, nếu cả hai đối tượng đều được tạo bằng chính lớp `Box`, câu lệnh `words.getClass() == numbers.getClass()` lại cho kết quả `true`.

Bài này giúp bạn:

- Giải thích nguyên nhân bằng **type erasure** và cơ chế nạp lớp của JVM.
- Phân biệt **kiểu tham số hóa tại thời điểm biên dịch**, **runtime class của đối tượng Box**, **runtime class của giá trị nằm trong Box** và **metadata generic**.
- Quan sát một chương trình Java 17 chạy thực tế, kiểm tra `javap` và `java.lang.reflect`.
- Hiểu tại sao cùng một lớp runtime vẫn có thể bảo đảm `Box<String>` không được nhận `Integer` khi biên dịch.
- Nhận diện các trường hợp ngoại lệ dễ gây nhầm lẫn: lớp con của `Box<String>`, nhiều class loader, đối tượng `null`, raw type và type token truyền tường minh.

## 1. Đặt vấn đề: một lớp khai báo, nhiều cách dùng có tham số kiểu

Ta khai báo lớp generic:

```java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }
}
```

`T` là **tham số kiểu** được khai báo trong định nghĩa lớp. `Box<String>` và `Box<Integer>` là hai cách **tham số hóa kiểu** `Box<T>` bằng các đối số kiểu khác nhau; chúng không phải hai lớp Java độc lập được viết hoặc sinh ra với mã nguồn `class BoxString` và `class BoxInteger`.

Ví dụ các phép gán được compiler xem xét như sau:

```java
Box<String> words = new Box<>("Java");
words.set("Generics");       // Hợp lệ: String phù hợp với T = String.
// words.set(123);          // Lỗi biên dịch: int/Integer không phải String.

Box<Integer> numbers = new Box<>(42);
numbers.set(100);           // Hợp lệ: Integer.
// numbers.set("wrong");    // Lỗi biên dịch.
```

Dù loại đối số được kiểm tra khi biên dịch, Java không tạo một runtime class riêng ứng với mỗi `T` cụ thể. Đây là đặc tính quan trọng của cách Java triển khai generics, khác với việc chuyên biệt hóa lớp cho từng kiểu tham số ở một số ngôn ngữ hoặc mô hình khác.

Cần phân biệt:

| Thành phần | Ví dụ | Bản chất |
| --- | --- | --- |
| Lớp generic được khai báo | `Box<T>` | Một định nghĩa lớp |
| Kiểu tham số hóa trong mã nguồn | `Box<String>` | Trình biên dịch biết phần tử của `Box` là `String` |
| Một kiểu tham số hóa khác | `Box<Integer>` | Trình biên dịch biết phần tử của `Box` là `Integer` |
| Runtime class của hai đối tượng tạo trực tiếp bằng `new Box<>(...)` | `Box.class` | Cùng một đối tượng `Class` trong cùng ngữ cảnh class loader |
| Giá trị nằm bên trong | `"Java"` và `42` (được boxing thành `Integer`) | Hai đối tượng có kiểu thực lần lượt là `String` và `Integer` |

**Cùng runtime class không đồng nghĩa hai biến tham chiếu cùng một đối tượng.** `words` và `numbers` vẫn trỏ tới hai `Box` được tạo riêng, mỗi hộp có trạng thái của nó.

## 2. Lý do: Java generic sử dụng type erasure

Với `class Box<T>` không bound, những vị trí dùng `T` thường có JVM descriptor dạng `Object`, thay vì sinh `Box$String` hay `Box$Integer`:

```java
static class Box<T> {
    private T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
```

| Thành phần của `Box<T>` | Erased JVM descriptor |
| --- | --- |
| `T value` | `Ljava/lang/Object;` |
| `Box(T)` | `(Ljava/lang/Object;)V` |
| `T get()` | `()Ljava/lang/Object;` |

Đây là **cơ chế giải thích cùng runtime class**. Khi sử dụng `Box<String>` và `Box<Integer>`, compiler vẫn kiểm tra kiểu các lời gọi, và có thể chèn cast ở caller. Không nên hiểu `Box<String>` là `Box<Object>` ở **source-level type system**. Phần tiếp theo kiểm chứng class identity trực tiếp bằng `getClass()`/`Box.class` và `javap`.

## 3. Chương trình Java 17: chứng minh cùng runtime class

Tạo tệp `SameRuntimeClassDemo.java` (toàn bộ nội dung sau biên dịch độc lập với Java 17+):

```java
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;

public final class SameRuntimeClassDemo {
    static class Box<T> {
        private T value;

        Box(T value) { this.value = value; }
        T get() { return value; }
        void set(T value) { this.value = value; }
    }

    static final class StringBox extends Box<String> {
        StringBox(String value) { super(value); }
    }

    public static void main(String[] args) {
        Box<String> words = new Box<>("Java");
        Box<Integer> numbers = new Box<>(42);

        System.out.println("same class: " + (words.getClass() == numbers.getClass()));
        System.out.println("class equals Box.class: " + (words.getClass() == Box.class));
        System.out.println("runtime name: " + words.getClass().getName());
        System.out.println("actual values: " + words.get().getClass().getSimpleName()
                + ", " + numbers.get().getClass().getSimpleName());
        System.out.println("values: " + words.get().toUpperCase()
                + " / " + (numbers.get() + 1));

        Class<?> clazz = words.getClass();
        TypeVariable<?> parameter = clazz.getTypeParameters()[0];
        System.out.println("declared parameter: " + parameter.getName());
        System.out.println("declared bound: "
                + Arrays.toString(parameter.getBounds()));

        Type superclass = StringBox.class.getGenericSuperclass();
        System.out.println("subclass generic parent: " + superclass.getTypeName());
        System.out.println("subclass shares Box.class: "
                + (new StringBox("X").getClass() == words.getClass()));
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac SameRuntimeClassDemo.java
java SameRuntimeClassDemo
```

**Stdout đã kiểm tra bằng `javac` và `java`:**

```text
same class: true
class equals Box.class: true
runtime name: SameRuntimeClassDemo$Box
actual values: String, Integer
values: JAVA / 43
declared parameter: T
declared bound: [class java.lang.Object]
subclass generic parent: SameRuntimeClassDemo$Box<java.lang.String>
subclass shares Box.class: false
```

Đọc và lý giải:

1. **`same class: true`**: hai đối tượng đều là instance trực tiếp của runtime class `SameRuntimeClassDemo$Box`. Toán tử `==` so sánh hai tham chiếu `Class`; kết quả `true` chứng minh hai lời gọi lấy cùng một đối tượng mô tả runtime class.
2. **`class equals Box.class: true`**: `Box.class` là class literal của lớp khai báo `Box`. Không có `Box<String>.class` dành riêng cho tham số kiểu `String`.
3. **`runtime name: SameRuntimeClassDemo$Box`**: lớp lồng tĩnh có tên binary chứa ký tự `$`. Tên không kèm `<String>` hay `<Integer>`.
4. **`actual values: String, Integer`**: cùng kiểu hộp không làm thay đổi loại đối tượng được giữ trong mỗi instance. Một hộp chứa `String`, hộp kia chứa `Integer`.
5. **`values: JAVA / 43`**: compiler vẫn hỗ trợ sử dụng `String.toUpperCase()` và phép tính trên `Integer` theo đúng kiểu do generic khai báo.
6. **`declared parameter: T`**: reflection nhận được **tên tham số kiểu được khai báo ở class**, không trả lại lời gọi `Box<String>` của biến `words`.
7. **`declared bound: [class java.lang.Object]`**: `T` không khai báo ràng buộc, nên bound tương ứng là `Object`.
8. **`subclass generic parent: ...Box<java.lang.String>`**: lớp con `StringBox` khai báo cố định quan hệ kế thừa `extends Box<String>`, nên metadata của chính lớp con có thể mô tả `String`. Đây là thông tin **trên khai báo lớp con**, không phải bằng chứng rằng mỗi lần tạo `Box<String>` sinh runtime class mới.
9. **`subclass shares Box.class: false`**: `StringBox` là một lớp con **thực sự khác lớp** `Box`; `StringBox.class != Box.class` là kết quả đúng và không mâu thuẫn với quy tắc xóa kiểu.

Ví dụ dùng `Box` không `final` chỉ để cho phép khai báo lớp con `StringBox`; chính điều đó giúp chỉ ra sự khác biệt giữa **tham số hóa** và **kế thừa**.

## 4. Kiểm tra tệp class với `javap`

Trong ví dụ trên, `Box` là lớp lồng tĩnh, nên `javac` tạo tệp tương ứng có tên binary `SameRuntimeClassDemo$Box`. Sau khi biên dịch, chạy:

```bash
javap -s -p 'SameRuntimeClassDemo$Box'
```

Đặt tên lớp trong dấu nháy đơn khi dùng Bash hoặc PowerShell để tránh trình bao xử lý ký tự `$`. Đoạn kết quả chính **đã kiểm chứng bằng `javap`**:

```text
class SameRuntimeClassDemo$Box<T> {
  private T value;
    descriptor: Ljava/lang/Object;

  SameRuntimeClassDemo$Box(T);
    descriptor: (Ljava/lang/Object;)V

  T get();
    descriptor: ()Ljava/lang/Object;

  void set(T);
    descriptor: (Ljava/lang/Object;)V
}
```

Bên trái, `javap` có thể hiển thị chữ ký generic như `T get()`. Bên phải, descriptor mà JVM sử dụng cho phương thức tương ứng là `()Ljava/lang/Object;`. Class file có thể lưu **generic Signature metadata** cùng với descriptor; metadata này giúp công cụ/reflection hiển thị khai báo `T` nhưng **không tạo một biến thể runtime riêng cho từng đối số kiểu**.

Cách đọc phần quan trọng:

- `Ljava/lang/Object;` đại diện cho kiểu tham chiếu `java.lang.Object`.
- `(Ljava/lang/Object;)V` có một tham số `Object`, trả về `void`.
- `()Ljava/lang/Object;` không có tham số, trả về `Object`.

Bạn có thể chạy thêm:

```bash
javap -c -p SameRuntimeClassDemo
```

Khi phương thức `main` gọi `words.get()` ở ngữ cảnh cần `String`, bytecode có thể bao gồm:

```text
invokevirtual ... Box.get:()Ljava/lang/Object;
checkcast     ... class java/lang/String
```

Các chỉ số constant pool, offset và cách hiển thị tên lớp tùy bản `javac`/`javap`; phần đáng chú ý là **`get` trả `Object` ở descriptor**, sau đó compiler chèn **`checkcast String`** khi kiểu cụ thể cần dùng là `String`.

Tại sao chỉ có một `Box.class` lại chạy được cho nhiều kiểu? Bởi JVM không phải giải quyết mỗi tham số kiểu thành một class mới; nó dùng bytecode erased của `Box`, còn compiler chịu trách nhiệm áp dụng ràng buộc generic tại các điểm gọi.

## 5. Cùng lớp runtime nhưng vẫn có kiểm tra kiểu an toàn

Nếu JVM chỉ thấy phương thức `set(Object)`, vì sao `words.set(123)` bị từ chối ở source?

Vì compiler biết biến `words` được khai báo là `Box<String>`. Do đó, theo cách nhìn của trình biên dịch, các phép thao tác thông thường trên biến ấy tương ứng với:

- `set(String)` theo kiểu nguồn đã thay `T = String`;
- `String get()` theo kiểu nguồn;
- đối số `Integer` truyền vào `set` sẽ vi phạm ràng buộc kiểu ở **thời điểm biên dịch**.

Sau khi bytecode được tạo, JVM có thể thực thi các lời gọi erased. Ví dụ, khi lấy kết quả của `get()` gán vào biến `String`, compiler chèn một cast tương ứng. Nếu chương trình không dùng các đường đi dữ liệu không an toàn, bảo đảm compile time giúp cast đó thành công.

**Điều không đúng:** “vì JVM thấy `Object` nên compiler cũng cho phép `words.set(new Object())`”. Java có hai mức khác nhau: kiểm tra kiểu generic của mã nguồn và kiểu erased trong bytecode.

### Type erasure không làm `Box<String>` trở thành `Box<Object>`

Phép gán sau **không hợp lệ**:

```java
Box<String> strings = new Box<>("one");
// Box<Object> objects = strings; // Lỗi biên dịch.
```

Java generics **bất biến** (*invariant*): `String` là con của `Object` không kéo theo `Box<String>` là subtype của `Box<Object>`. Nếu phép gán đó hợp lệ, người dùng có thể đưa một `Object` không phải chuỗi vào hộp vốn được hứa chỉ chứa `String`.

Khi cần tham chiếu đọc dữ liệu từ nhiều loại `Box` mà không phụ thuộc `T` cụ thể, `Box<?>` có thể phù hợp:

```java
Box<?> unknown = new Box<>("hello");
Object value = unknown.get();
// unknown.set("another"); // Không biên dịch: T cụ thể không được biết.
// unknown.set(null);      // Hợp lệ về kiểu; cách xử lý null tùy API.
```

`Box<?>` cho phép đọc giá trị như `Object`, nhưng compiler không thể cho phép ghi một đối tượng không phải `null` khi không biết tham số kiểu thực. Quy tắc này vẫn tồn tại dù các biến thể `Box` dùng chung runtime class.

## 6. Reflection đọc được gì — và không đọc được gì?

Hai phương thức reflection dễ bị nhầm lẫn:

```java
Class<?> clazz = words.getClass();
System.out.println(clazz.getTypeParameters()[0].getName()); // T
```

`getTypeParameters()` mô tả **tham số kiểu khai báo trong phần định nghĩa lớp**. Ký tự `T` xuất hiện trong khai báo `class Box<T>` và có thể được lưu trong metadata; reflection không kết luận riêng đối tượng `words` đã nhận `String` tại điểm khai báo biến.

Ngược lại:

```java
System.out.println(StringBox.class.getGenericSuperclass().getTypeName());
```

có thể trả về `SameRuntimeClassDemo$Box<java.lang.String>` vì chính lớp `StringBox` được biên dịch từ câu lệnh `extends Box<String>`. Metadata của **khai báo kế thừa** mang thông tin đó.

Ba câu hỏi khác nhau:

| Câu hỏi | API thường dùng | Điều nhận được |
| --- | --- | --- |
| Đối tượng này là instance của lớp runtime nào? | `obj.getClass()` | `Class<?> ` của runtime class thực |
| Lớp generic khai báo tên tham số kiểu nào? | `Box.class.getTypeParameters()` | `T`, các bound |
| Lớp con khai báo kiểu cha generic nào? | `StringBox.class.getGenericSuperclass()` | Có thể thấy `Box<String>` từ metadata lớp con |

**Không thể dựa riêng vào `new Box<String>("X").getClass()` để khôi phục `String` như một tham số runtime đã được gắn vào `Class` của Box.** Với `Box<Integer>`, phương thức ấy trả về cùng `Box.class` trong cùng ngữ cảnh nạp lớp.

Cũng không nên cố suy luận kiểu `T` bằng `box.get().getClass()`. Cách đó chỉ cho biết **kiểu đối tượng hiện đang nằm trong hộp**, có thể vắng mặt nếu giá trị là `null`, và không phải bằng chứng tổng quát về khai báo `Box<T>`.

## 7. Khi thật sự cần thông tin kiểu ở runtime: truyền `Class<T>`

Type erasure không cấm ứng dụng lưu metadata một cách **tường minh**. Nếu API cần kiểm tra đối tượng đến từ bên ngoài có đúng kiểu mong muốn, hãy nhận một **type token** `Class<T>` rồi lưu nó trong từng instance.

Ví dụ `TypeTokenDemo.java` sau biên dịch và chạy độc lập với Java 17+:

```java
import java.util.Objects;

public final class TypeTokenDemo {
    static final class Box<T> {
        private final Class<T> type;
        private T value;

        Box(Class<T> type, T value) {
            this.type = Objects.requireNonNull(type);
            this.value = type.cast(value);
        }

        Class<T> type() { return type; }
        T get() { return value; }

        void setFromObject(Object candidate) {
            value = type.cast(candidate);
        }
    }

    public static void main(String[] args) {
        Box<String> text = new Box<>(String.class, "Hi");
        Box<Integer> number = new Box<>(Integer.class, 7);

        System.out.println("same Box class: "
                + (text.getClass() == number.getClass()));
        System.out.println("saved tokens: " + text.type().getSimpleName()
                + ", " + number.type().getSimpleName());

        try {
            text.setFromObject(8);
        } catch (ClassCastException ex) {
            System.out.println("rejected: " + ex.getClass().getSimpleName());
        }
    }
}
```

Lệnh:

```bash
javac TypeTokenDemo.java
java TypeTokenDemo
```

Stdout đã kiểm chứng:

```text
same Box class: true
saved tokens: String, Integer
rejected: ClassCastException
```

Điều thay đổi so với ví dụ đầu:

- Các instance vẫn thuộc **cùng class `TypeTokenDemo$Box`**.
- Mỗi đối tượng chứa **một field dữ liệu `type`**, được truyền từ `String.class` hoặc `Integer.class`. Sự khác nhau về token thuộc **trạng thái từng đối tượng**, không phải do JVM tự khôi phục generic của `Box<T>`.
- `Class.cast` kiểm tra đối tượng nhận vào theo token; khi `Box<String>` nhận một `Integer` qua `setFromObject`, nó phát sinh `ClassCastException`.
- `Class<T>` dùng thuận tiện cho các kiểu runtime có `Class` token như `String`, `Integer`. Nó không thể giữ trọn tham số lồng nhau của một kiểu như `List<String>` chỉ bằng `List.class`; nếu cần kiểu generic phức tạp, phải có metadata kiểu khác (chẳng hạn `Type`/`ParameterizedType` phù hợp).

Có thể chọn thiết kế không cho phép thay đổi giá trị qua `Object` khi ứng dụng chỉ cần API generic có kiểm tra kiểu ở compile time. Type token hữu ích khi nhận dữ liệu động, reflect, deserialize hoặc tương tác với API không mang đủ tham số generic.

## 8. Các hệ quả thường gặp

Các cách sử dụng `Box<String>` và `Box<Integer>` có cùng runtime class dẫn đến một số giới hạn, nhưng chúng không làm mất compile-time type checking:

| Tình huống | Kết quả trên Java 17 | Vì sao |
| --- | --- | --- |
| `obj instanceof Box<?>` | Hợp lệ | Chỉ kiểm tra runtime class `Box` |
| `obj instanceof Box<String>` với `obj: Object` | Bị từ chối | Runtime không biết đối số generic `String` |
| `new Box<String>[2]` | Bị từ chối | Component type không reifiable |
| Hai overload `f(Box<String>)` và `f(Box<Integer>)` | Bị từ chối | Trùng erasure |
| Gán `Box<Integer>` vào `Box<String>` | Bị từ chối | Generic type invariance |
| Raw alias/unchecked cast | Có cảnh báo hoặc nguy cơ runtime | Bỏ qua bảo đảm kiểu ở source |

Trường hợp `Box<String>` và `Box<Integer>` vẫn có thể mang **giá trị object thực khác class**; `getClass()` của chính giá trị trong hộp không làm cho class của hộp trở nên specialized. Nếu cần validation ở runtime, truyền `Class<T>` token như chương trình kế tiếp, nhưng nhớ rằng token không tự kiểm tra type arguments lồng sâu như `List<String>`.

## 9. Ba nhầm lẫn quan trọng cần gỡ

**Nhầm lẫn A — “Cùng `Box.class` nghĩa là cùng đối tượng”.**

Sai. Hai lời gọi `new Box<>(...)` tạo hai đối tượng `Box` khác nhau dù cùng class. So sánh:

```java
Box<String> x = new Box<>("A");
Box<String> y = new Box<>("A");
System.out.println(x == y);                    // false
System.out.println(x.getClass() == y.getClass()); // true
```

`==` trên `x`, `y` kiểm tra **danh tính instance**. `==` trên kết quả `getClass()` kiểm tra **danh tính Class**.

**Nhầm lẫn B — “Xóa kiểu `T` thành `Object` nghĩa là mất lớp thật của `String`”.**

Sai. Đối tượng `String` vẫn thuộc `String.class`. Compiler/JVM có thể dùng `Object` trong chữ ký erased của hộp vì mọi đối tượng `String` cũng là `Object`. Thông tin **đối số generic của hộp** mất khỏi runtime descriptor, không phải lớp thực của từng đối tượng tham chiếu.

**Nhầm lẫn C — “Reflection in được `Box<String>` thì chắc JVM có lớp `Box<String>` riêng”.**

Sai. Reflection đọc được thông tin generic của **khai báo** (ví dụ một lớp con thực sự viết `extends Box<String>`) vì class file có thể lưu metadata `Signature`. Nó không khẳng định có lớp runtime chuyên biệt cho mỗi cách dùng `new Box<String>(...)`.

**Một giới hạn nâng cao về class loader:** câu “hai `Box` dùng cùng `Class`” ngầm giả định chúng được tạo từ cùng định nghĩa lớp do cùng ngữ cảnh nạp lớp xác định. Trong JVM, hai class loader khác nhau có thể định nghĩa hai lớp có cùng tên binary nhưng là hai runtime class khác nhau. Điều đó là quy tắc về **class identity và class loading**, độc lập với việc `Box<String>` hay `Box<Integer>` chọn đối số generic nào.

## 10. Câu hỏi tự luyện

**Câu 1.** Với hai biến cùng tạo trực tiếp bằng `new Box<>(...)`:

```java
Box<String> a = new Box<>("Hello");
Box<Integer> b = new Box<>(10);
```

Dự đoán `a == b` và `a.getClass() == b.getClass()`.

**Giải thích:** trong ví dụ hai biến có kiểu nguồn không tương thích trực tiếp, việc viết `a == b` có thể bị compiler báo là hai kiểu incomparable. Để kiểm tra danh tính instance một cách hợp lệ trong bài này, ép hai phía về `Object` hoặc lưu chúng trong `Object` trước. Khi đó hai instance khác nhau nên kết quả là `false`; còn hai `getClass()` là cùng `Box.class` nên `true`.

**Câu 2.** Vì sao `Box<T>.get()` có thể có descriptor `()Ljava/lang/Object;`, trong khi `String text = box.get();` hợp lệ khi `box` là `Box<String>`?

**Đáp án:** type erasure khiến method descriptor trả `Object`; compiler kiểm tra kiểu nguồn và chèn cast sang `String` tại điểm sử dụng khi cần.

**Câu 3.** `Box<String>` và `Box<Integer>` giống nhau ở điều gì và khác nhau ở điều gì?

**Đáp án:** nếu cùng được tạo trực tiếp bằng lớp `Box`, chúng giống nhau về runtime class; chúng khác về kiểu tham số hóa được compiler kiểm tra và có thể khác về giá trị, trạng thái và các token được truyền vào từng instance.

**Câu 4.** Nếu khai báo `class StringBox extends Box<String>` rồi gọi `new StringBox("A").getClass()`, kết quả có bằng `new Box<>("A").getClass()` không?

**Đáp án:** không. `StringBox` là lớp con riêng biệt, có runtime class riêng, không phải một biến thể tự sinh từ việc truyền `String` làm đối số generic.

**Câu 5.** Vì sao `Box.class.getTypeParameters()` không trả về `String` cho một biến được khai báo là `Box<String>`?

**Đáp án:** API đó đọc danh sách tham số kiểu **của khai báo class `Box<T>`**, không đọc thông tin đối số kiểu được chọn trong khai báo biến cụ thể.

**Câu 6.** Thiết kế `Box<T>` sao cho chương trình từ chối đưa một đối tượng không đúng kiểu vào hộp khi dữ liệu đến từ nguồn `Object`.

**Gợi ý:** truyền một `Class<T>` tường minh vào constructor, lưu trong field, dùng `type.cast(candidate)` tại biên nhận dữ liệu. Phân biệt field token của **instance** và đối tượng `Class` của chính `Box`.

## Tự tổng kết bằng một mô hình chính xác

- **Mã nguồn:** `Box<String>` và `Box<Integer>` là hai kiểu tham số hóa khác nhau; compiler bảo vệ những thao tác bình thường với `T` theo đối số kiểu đã cung cấp.
- **Bytecode:** các vị trí dùng tham số `T` không giới hạn trong `Box<T>` có erased type là `Object`; JVM dùng định nghĩa lớp `Box` chung.
- **Đối tượng:** mỗi lần `new` vẫn tạo một instance riêng; giá trị bên trong vẫn có runtime class thật như `String`, `Integer`.
- **Reflection:** có thể biết lớp runtime và đọc generic metadata khai báo, nhưng `Box.class` không tự cung cấp đối số `String` hay `Integer` của mọi instance.
- **Thiết kế API:** nếu logic chạy thực sự phải biết kiểu cụ thể, truyền token/metadata đúng mục đích; đừng dựa vào `getClass()` của hộp để suy ra tham số generic.

Điểm then chốt: `Box<String>` và `Box<Integer>` **chia sẻ lớp runtime vì Java dùng type erasure**, trong khi an toàn kiểu generic chủ yếu được kiểm tra lúc biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-007">Phân biệt xóa kiểu với tạo lớp chuyên biệt cho từng đối số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt xóa kiểu (type erasure) với tạo lớp chuyên biệt cho từng đối số kiểu

Khi gặp `Box<String>`, `Box<Integer>`, `List<String>` hoặc `List<Integer>`, ta có thể hình dung hai cách triển khai generics:

- **Xóa kiểu (type erasure):** trình biên dịch kiểm tra các đối số kiểu ở mã nguồn rồi biên dịch thành mã sử dụng kiểu đã xóa; những cách dùng khác nhau của cùng một khai báo lớp generic thường chia sẻ **cùng lớp runtime**.
- **Tạo lớp chuyên biệt cho từng đối số kiểu (specialization/monomorphization):** hệ thống biên dịch hoặc thực thi có thể tạo một dạng thực thi riêng cho mỗi tổ hợp kiểu cụ thể, chẳng hạn một phiên bản chuyên biệt cho `String` và một phiên bản cho `Integer`. Đây là mô hình để đối chiếu, **không phải hành vi mặc định của Java generics thông thường**.

Khác biệt quan trọng nằm ở việc **kiểu cụ thể ảnh hưởng thế nào đến mã thực thi và cách biểu diễn dữ liệu**. Java chủ yếu dùng erasure để hiện thực hóa generics ở bytecode. Đừng đồng nhất việc nhiều kiểu generic được kiểm tra khác nhau ở nguồn với việc JVM phải có bấy nhiêu lớp chuyên biệt.

## 1. Cùng một khai báo generic, hai hướng triển khai

Giả sử ta định nghĩa:

```java
// Snippet mô tả khai báo; đoạn chạy đầy đủ ở mục 6.
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Và sử dụng:

```java
Box<String> greeting = new Box<>("Java");
Box<Integer> number = new Box<>(42);
```

Ở **cấp mã nguồn**, trình biên dịch biết `greeting.get()` có kiểu `String`, còn `number.get()` có kiểu `Integer`. Bởi vậy, lệnh `String s = number.get();` bị từ chối khi biên dịch. Song điều đó chưa cho biết JVM lưu bao nhiêu định nghĩa lớp.

### Hướng A — xóa kiểu như Java sử dụng

Vì `T` không có giới hạn trên tường minh, erasure của `T` là `Object`. Có thể hình dung dạng tương đương ở tầng descriptor như sau:

```java
// Pseudocode giải thích erasure, không phải mã Java cần khai báo thêm.
class Box {
    private final Object value;

    Box(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}
```

Đây là **mô hình diễn giải**: javac thực sự tạo bytecode, không sinh tệp `Box.java` mới theo cách minh họa. Có một định nghĩa lớp `Box` dùng cho cả hai cách gắn kiểu, và compiler có thể chèn cast `String` hoặc `Integer` ở nơi nhận kết quả.

```text
Nguồn Java:
  Box<String>         Box<Integer>
        \                  /
         \                /
          v              v
       cùng class runtime Box
        field value: Object
        method get(): Object
```

### Hướng B — giả định tạo dạng chuyên biệt theo kiểu

Một hệ thống chọn specialization có thể tạo hai phiên bản tương đương về ý nghĩa:

```java
// Pseudocode: minh họa chiến lược chuyên biệt hóa.
// Đây KHÔNG phải hai lớp do javac tự sinh từ Box<T>.
final class BoxForString {
    private final String value;
    BoxForString(String value) { this.value = value; }
    String get() { return value; }
}

final class BoxForInteger {
    private final Integer value;
    BoxForInteger(Integer value) { this.value = value; }
    Integer get() { return value; }
}
```

Theo giả định này, việc sử dụng `Box<String>` và `Box<Integer>` có hai dạng thực thi riêng, có thể chứa thao tác đọc trường, chữ ký và tối ưu hóa khác nhau. Nhưng **specialization không nhất thiết luôn có nghĩa tạo tệp `.class` hoặc lớp được đặt tên riêng**; tùy thiết kế ngôn ngữ, runtime có thể phát sinh các dạng mã chuyên biệt hoặc tái sử dụng một phần mã thực thi. Ví dụ trên cố ý chọn cách “tạo lớp riêng” để thấy rõ sự đối lập.

## 2. Bảng so sánh trực tiếp

| Khía cạnh | Type erasure của Java generics thông thường | Mô hình tạo dạng chuyên biệt theo đối số kiểu |
| --- | --- | --- |
| Sau khi kiểm tra kiểu nguồn | Xóa các tham số kiểu khỏi descriptor thông thường, dùng bound hoặc `Object` | Có thể tạo dạng thực thi với kiểu cụ thể |
| `Box<String>` và `Box<Integer>` | Cùng lớp runtime `Box` | Có thể ánh xạ tới hai dạng chuyên biệt |
| Kiểu trường `T value` | `Object` khi `T` không bound | Có thể dùng `String` và `Integer` trong từng dạng |
| Kiểu trả về `T get()` | Descriptor trả `Object`; nơi gọi có thể cần cast | Có thể có chữ ký/đường chạy cụ thể tương ứng |
| Lượng mã phải sinh | Không nhân số lượng class theo mọi cách gắn kiểu | Có thể tăng theo số trường hợp chuyên biệt |
| Runtime biết đối số kiểu? | Không có type argument cụ thể gắn trực tiếp vào mỗi instance generic thông thường | Tùy thiết kế; chuyên biệt hóa và phản chiếu kiểu là hai đặc tính khác nhau |
| Kiểu nguyên thủy trong Java | `List<int>` không hợp lệ; dùng `List<Integer>` với boxing/unboxing | Một thiết kế chuyên biệt có thể hỗ trợ giá trị nguyên thủy trực tiếp nếu ngôn ngữ cho phép |
| Tương thích thư viện Java cũ | Erasure được chọn để hỗ trợ việc đưa generics vào hệ sinh thái bytecode cũ | Cần chiến lược tương thích riêng; không tự bảo đảm giữ nguyên descriptor cũ |
| Chi phí tại runtime | Một dạng class phổ biến; cast, boxing và dispatch có chi phí tùy ngữ cảnh | Có thể giảm một số chi phí nhưng có nguy cơ tăng code size và áp lực cache |
| An toàn kiểu mã nguồn | Compiler kiểm tra generics | Compiler cũng có thể kiểm tra generics; chiến lược thực thi không quyết định một mình tính an toàn |

Lưu ý: cột thứ hai nói về **thiết kế generic của Java hiện hành**, còn cột thứ ba là **mô hình triển khai giả định** dùng để phân biệt khái niệm. Không nên dùng bảng để kết luận mọi ngôn ngữ có specialization đều sinh một bản class cho mọi đối số kiểu.

## 3. Java xóa kiểu chính xác như thế nào?

Trước khi so sánh hai chiến lược, cần xác định chính xác **phần nào của Java bị erasure**:

| Source-level form | Kiểu thường dùng ở JVM descriptor | Có tạo class mới theo `T`? |
| --- | --- | --- |
| `T` không bound | `Object` | Không |
| `T extends Number` | `Number` | Không |
| `T extends Number & Comparable<T>` | `Number` (first bound) | Không |
| `List<String>` | `List` | Không |
| `T[]` | Mảng của erasure của `T` | Không |
| `List<T>` trong method parameter | `List` | Không |

Compiler kiểm tra các generic relationships **trước** erasure, có thể chèn `checkcast` khi caller đọc dữ liệu và sinh **bridge method** khi cần bảo toàn overriding. Các cơ chế ấy không đồng nghĩa specialization: bridge xử lý liên kết/dispatch, cast kiểm tra giá trị ở chỗ sử dụng; không cơ chế nào biến `Box<Integer>` thành primitive `Box<int>`.

Bài riêng về **type variable bounds**, **method/array descriptors** và **checkcast** đã mô tả từng quy tắc. Ở đây chúng được đặt chung để đánh giá sự khác biệt giữa một binary generic **dùng chung** và các bản implementation chuyên biệt.

## 4. Vì sao Java chọn erasure thay vì tạo một lớp cho từng đối số kiểu?

Khi Java 5 bổ sung generics, `List`, `Collection` và nhiều thư viện cũ đã tồn tại. **Migration compatibility** là một lý do lớn để chọn erasure: API generic hóa nhiều khi vẫn giữ **cùng erased JVM method descriptors**, để class đã biên dịch và mã nguồn cũ có thể từng bước chuyển sang generic form.

```java
// Mô hình đối chiếu hai phiên bản nguồn của API:
void accept(java.util.List values) {}          // API cũ kiểu raw
// void accept(java.util.List<String> values) {} // Dạng generic hóa
```

Hai chữ ký trên **không thể cùng là overload** của một class vì cùng erasure; chúng minh họa lựa chọn giữa hai cách khai báo API. Erasure không bảo đảm mọi thay đổi source/binary đều tương thích tuyệt đối: inheritance, overload, generic `Signature`, bridge, runtime class-file version và semantics API vẫn phải kiểm tra. Sự đánh đổi với specialization là **số lượng artifacts/code versions** và khả năng tối ưu riêng từng representation.

## 5. Cùng runtime class không có nghĩa hai kiểu generic gán được cho nhau

Java coi `Box<String>` và `Box<Integer>` là hai cách gắn tham số **khác nhau về kiểu tĩnh**. Erasure là cơ chế thực thi, không xóa quy tắc gán kiểu của javac.

```java
// Snippet minh họa, không phải một chương trình độc lập:
List<String> names = new java.util.ArrayList<>();
List<Integer> ids = new java.util.ArrayList<>();

// names = ids;             // Không biên dịch
// List<Object> all = names; // Cũng không biên dịch
```

Generics thông thường trong Java **bất biến**: `List<String>` không phải subtype của `List<Object>` dù `String` là subtype của `Object`. Có thể dùng wildcard `List<? extends Object>` khi chỉ cần đọc đối tượng ở mức `Object`, hoặc `List<? super String>` khi API cần nhận các phần tử `String` theo hướng phù hợp.

Hai dòng `new ArrayList<>()` phía trên có cùng runtime class **vì cùng chọn triển khai `ArrayList`**. Nhưng một `ArrayList<String>` và `LinkedList<String>` có runtime class khác nhau vì bản thân **triển khai collection khác nhau**, không liên quan chuyện đối số kiểu bằng nhau.

## 6. Chương trình Java 17+ chứng minh sự khác biệt

Lưu mã sau trong tệp **`ErasureVsSpecializationDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ErasureVsSpecializationDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static final class NumberBox<N extends Number> {
        private final N value;

        NumberBox(N value) {
            this.value = value;
        }

        N get() {
            return value;
        }
    }

    static class Holder<T> {
        private T value;

        void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static final class StringHolder extends Holder<String> {
        @Override
        void set(String value) {
            super.set(value);
        }
    }

    static final class Counter<T> {
        static int created;

        Counter() {
            created++;
        }
    }

    public static void main(String[] args) throws NoSuchMethodException {
        Box<String> stringBox = new Box<>("Java");
        Box<Integer> integerBox = new Box<>(42);
        List<String> texts = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();

        System.out.println("Box<String> va Box<Integer> cung class: "
                + (stringBox.getClass() == integerBox.getClass()));
        System.out.println("ArrayList<String> va ArrayList<Integer> cung class: "
                + (texts.getClass() == ids.getClass()));

        Method boxGet = Box.class.getDeclaredMethod("get");
        System.out.println("Kieu tra ve da xoa cua Box.get: "
                + boxGet.getReturnType().getSimpleName());
        System.out.println("Kieu tra ve generic cua Box.get: "
                + boxGet.getGenericReturnType().getTypeName());
        System.out.println("Kieu tra ve da xoa cua NumberBox.get: "
                + NumberBox.class.getDeclaredMethod("get").getReturnType()
                    .getSimpleName());

        String text = stringBox.get();
        System.out.println("Gia tri String: " + text);

        Holder<String> holder = new StringHolder();
        holder.set("bridge");
        System.out.println("Gia tri qua Holder<String>: " + holder.get());
        System.out.println("StringHolder co bridge: "
                + Arrays.stream(StringHolder.class.getDeclaredMethods())
                    .anyMatch(Method::isBridge));

        new Counter<String>();
        new Counter<Integer>();
        System.out.println("Counter.created dung chung: " + Counter.created);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ErasureVsSpecializationDemo.java
java ErasureVsSpecializationDemo
```

Đầu ra:

```text
Box<String> va Box<Integer> cung class: true
ArrayList<String> va ArrayList<Integer> cung class: true
Kieu tra ve da xoa cua Box.get: Object
Kieu tra ve generic cua Box.get: T
Kieu tra ve da xoa cua NumberBox.get: Number
Gia tri String: Java
Gia tri qua Holder<String>: bridge
StringHolder co bridge: true
Counter.created dung chung: 2
```

Kết quả đã được đối chiếu bằng cách chạy các lớp và phương thức tương đương trong JDK với cú pháp tương thích Java 17. Giải thích từng điểm:

1. **Hai `Box` dùng cùng class:** `getClass()` so sánh hai `Class` object đại diện cùng khai báo runtime `Box`. Nếu Java tự sinh lớp riêng cho mỗi `T`, phép so sánh này sẽ không phản ánh mô hình dùng chung như ở đây.
2. **Hai `ArrayList` cùng class:** khác biệt `String`/`Integer` là thông tin kiểu ở nguồn; bản thân `ArrayList` được dùng chung.
3. **`Box.get` trả về `Object` trong reflection thông thường:** method descriptor là dạng đã xóa `T` không bound. `getGenericReturnType()` đọc được metadata cho biết khai báo gốc có type variable `T`. Nó **không** tự trả về `String` của riêng biến `stringBox`.
4. **`NumberBox.get` trả về `Number`:** giới hạn `N extends Number` quyết định erasure, cho thấy “xóa kiểu” không luôn có nghĩa thay tất cả bằng `Object`.
5. **Đọc `String` vẫn an toàn ở mã nguồn:** trình biên dịch đã kiểm tra `Box<String>`; nếu cần cast, cast diễn ra ở nơi thích hợp. Đây không phải lớp `BoxForString` riêng.
6. **Bridge method có thật:** `StringHolder` là một **lớp con do lập trình viên khai báo rõ**; bridge bảo toàn override sau erasure của `Holder<T>`. Cờ `isBridge()` không chứng minh có specialization theo tham số kiểu.
7. **Biến static được dùng chung:** `Counter<String>` và `Counter<Integer>` dùng một trường `Counter.created`, nên sau hai lần tạo đối tượng giá trị là 2. Trong cùng một class được tải bởi cùng class loader, mỗi lần gắn type argument không tạo bộ static field riêng.

Để quan sát sâu hơn, sau khi biên dịch hãy chạy:

```bash
javap -p -s 'ErasureVsSpecializationDemo$Box'
javap -p -s 'ErasureVsSpecializationDemo$NumberBox'
javap -p -v 'ErasureVsSpecializationDemo$StringHolder'
```

Những lệnh này xem descriptor `Object`/`Number`, metadata `Signature` và phương thức bridge trong bytecode. Tên lớp lồng nhau có dấu `$`; dùng dấu nháy để shell không diễn giải ký tự này ngoài ý muốn. Phiên bản javap có thể trình bày các thuộc tính phụ khác nhau, nhưng ý nghĩa của descriptor và bridge là như nhau.

## 7. “Một lớp dùng chung” có thật sự không còn thông tin generic nào?

Không. Một binary generic dùng chung vẫn có thể chứa thuộc tính `Signature` mô tả **khai báo**: type parameters, bounds, generic superclasses, fields và methods. Reflection như `Box.class.getTypeParameters()` có thể đọc tên `T`; một subclass khai báo `extends Box<String>` có thể lưu đối số `String` trên **kiểu cha**.

Điều nó không tự có là **actual type argument riêng của mỗi instance `new Box<T>()`**. `getClass()` trả class runtime chung; nếu cần xác minh kiểu bên ngoài API, hãy nhận `Class<T>` token hoặc metadata khác một cách có chủ đích. Bài cuối chương về `Signature` minh họa đầy đủ descriptor, field/method metadata và `TypeVariable`/`ParameterizedType`.

## 8. Erasure, specialization và reification là ba khái niệm liên quan nhưng khác nhau

**Erasure** nói tới cách diễn đạt/biên dịch khai báo generic: tham số kiểu được thay bằng các kiểu đã xóa trong descriptor thường và mã được chia sẻ. **Specialization** nói tới khả năng tạo những dạng thực thi riêng cho các tổ hợp kiểu. **Reification** nói tới mức độ thông tin kiểu có mặt để runtime có thể quan sát hoặc kiểm tra.

Chẳng hạn:

- Mảng Java như `String[]` có runtime component type, nên `Object[] array = new String[1]; array[0] = 123;` ném `ArrayStoreException` khi chạy (giả sử đặt trong một khối Java hợp lệ). Kiểu thành phần của array mang thông tin runtime cần thiết.
- `List<String>` **không phải kiểu reifiable** để kiểm tra bằng `instanceof List<String>`. Có thể kiểm tra `instanceof List<?>` vì wildcard không buộc JVM xác minh loại phần tử.
- Một hệ thống có thể lưu type argument ở runtime mà không nhất thiết tạo bản sao hoàn toàn của mọi đoạn mã generic. Ngược lại, tạo mã chuyên biệt có thể phục vụ tối ưu hóa mà không tự động hứa cung cấp đầy đủ API reflection của mọi type argument.

Vì vậy, đừng định nghĩa specialization đơn giản là “runtime biết generic” hoặc erasure đơn giản là “runtime không còn phân biệt String với Integer”. Cần hỏi chính xác: **runtime phân biệt cái gì, ở lớp đối tượng nào và bằng cơ chế nào?**

## 9. Những hệ quả dễ thấy của erasure trong Java

Từ việc binary generic dùng chung, những hạn chế hay gặp là:

- Không thể overload chỉ theo `List<String>` và `List<Integer>` vì erased method descriptors trùng nhau.
- Không dùng `instanceof List<String>` để kiểm tra mọi element ở runtime; `List<?>` chỉ kiểm tra class.
- Không trực tiếp viết `new T()`, `T.class`, `new T[n]` cho type variable không reifiable.
- Raw/unchecked APIs có thể tạo heap pollution, khiến cast ở một lời gọi khác thất bại.
- Java generics reference thông thường không chấp nhận `List<int>`; `List<Integer>` không đồng nghĩa representation primitive.

Những giới hạn này **không chứng minh** specialization luôn tốt hơn: nó có thể cải thiện một số đường xử lý primitive nhưng cũng tăng code size hoặc complexity. Hãy so sánh bằng mục tiêu representation và số liệu workload, không chỉ bằng tên cơ chế. Đối với code riêng cần primitive field, có thể định nghĩa `IntBox` tường minh; đó là **thiết kế API thủ công**, không phải generic specialization tự động.

## 10. Chi phí bộ nhớ và hiệu năng: vì sao không có chiến lược luôn thắng?

Có hai loại chi phí khác nhau:

**Số lượng phiên bản mã:** nếu tạo thực thi riêng cho rất nhiều tổ hợp kiểu (`Box<Customer>`, `Box<Order>`, `Box<Product>`, v.v.), code size có thể tăng. Nhiều bản mã có thể làm tăng thời gian biên dịch hoặc tải mã, dùng thêm bộ nhớ mã và ảnh hưởng instruction cache. Erasure tạo ít biến thể class hơn, thường thuận lợi cho dùng lại thư viện.

**Biểu diễn dữ liệu và thao tác:** dạng chung chứa `Object` có thể cần cast; khi generic nhận wrapper cho primitive, boxing/unboxing và cấp phát có thể ảnh hưởng hiệu năng. Dạng chuyên biệt cho dữ liệu thích hợp có thể giảm những chi phí đó, cải thiện locality và tối ưu trực tiếp theo kiểu.

Tuy nhiên, **không nên kết luận** “erasure luôn nhanh hơn vì ít class” hoặc “specialization luôn nhanh hơn vì bỏ cast”. HotSpot JVM có JIT, profile, inline, escape analysis và những tối ưu khác. Nó có thể tạo các đường mã máy hiệu quả cho các kiểu thực tế đang chạy mà **không tạo lớp Java mới `BoxForString` cho mỗi type argument**. Cũng có phép cast rất rẻ hoặc bị tối ưu đi.

Ví dụ, `String` và `Integer` trong hai `Box` không khiến trường `Object` phải lưu “chuỗi đã xóa” hay “số nguyên đã xóa”; nó giữ tham chiếu đến các đối tượng thực. Đo benchmark hợp lệ cần kiểm soát warm-up, JIT, allocation, loại workload và sử dụng công cụ như JMH; không thể chỉ dựa vào cơ chế generic để dự đoán thời gian chạy.

## 11. Có thể tự tạo lớp riêng cho từng kiểu trong Java không?

Có, nhưng đây là **quyết định lập trình API**, không phải automatic specialization của `Box<T>`:

```java
// Snippet thiết kế hai lớp riêng; độc lập với cơ chế erasure của Box<T>.
final class StringBox {
    private final String value;
    StringBox(String value) { this.value = value; }
    String get() { return value; }
}

final class IntBox {
    private final int value;
    IntBox(int value) { this.value = value; }
    int get() { return value; }
}
```

Hai lớp này có hai runtime classes thực sự. `IntBox` chứa trường `int` chứ không cần `Integer`; đây là sự chuyên biệt **do lập trình viên viết bằng tay**. Nếu cần nhiều kiểu, có thể phát sinh một lượng code trùng lặp lớn. Đây cũng là lý do thư viện Java cung cấp các API chuyên cho primitive trong những trường hợp nhất định, ví dụ `IntStream`, `LongStream`, `DoubleStream`, bên cạnh `Stream<T>`.

Hãy phân biệt rõ:

- `Box<String>` và `Box<Integer>` theo cùng khai báo `Box<T>`: **một class generic bị erasure**.
- `StringBox` và `IntBox` do tự khai báo: **hai class khác nhau** với kiểu trường có thể khác.
- `StringHolder extends Holder<String>`: **một subclass thực do lập trình viên viết**, nhưng `Holder<T>` vẫn không được nhân thành `Holder<String>` và `Holder<Integer>` riêng.
- Mã máy mà JIT tối ưu hóa theo kiểu runtime: có thể có **mã máy chuyên biệt cho đường chạy**, nhưng điều đó không đồng nghĩa JVM giới thiệu class generic Java mới cho mỗi `T`.

Đừng lấy việc tồn tại một subclass riêng hoặc mã JIT riêng làm bằng chứng compiler Java đã chuyển từ erasure sang monomorphization của khai báo generic ban đầu.

## 12. Bài tập tư duy: chọn nhận định đúng

**Tình huống A.** Hai biến `List<String>` và `List<Integer>` được khởi tạo bằng `new ArrayList<>()`. Có thể kết luận gì?

Có thể kết luận runtime class đều là `ArrayList`. Không thể kết luận chúng gán được cho nhau; compiler vẫn biết đối số kiểu khác nhau ở nguồn.

**Tình huống B.** Một đối tượng của `class Data<T extends Number>` có `T data;`. Sau erasure, kiểu trường trong descriptor thường là gì?

Là `Number`, không phải `Object` và không được tự động chọn `Integer` chỉ vì ở một nơi ta viết `Data<Integer>`.

**Tình huống C.** Reflection trả về `T` khi gọi `getGenericReturnType()`. Có phải runtime đang giữ kiểu `T = String` cho từng instance?

Không. Reflection đang đọc **khai báo generic**; object `Box<String>` thông thường không có class runtime riêng chứa `String` làm tham số cho chính class ấy.

**Tình huống D.** Một phương thức bridge `set(Object)` xuất hiện trong lớp con `StringHolder`. Có chứng minh compiler tạo specialization cho `Holder<String>` không?

Không. Bridge được tạo để **bảo toàn ghi đè** khi phương thức generic bị xóa kiểu.

**Tình huống E.** Một bộ biên dịch chọn tạo mã máy riêng cho `int` và `double`. Có đồng nghĩa nó chắc chắn tạo hai lớp Java được nhìn thấy qua `Class` không?

Không nhất thiết. Dạng mã thực thi chuyên biệt, cấu trúc runtime class và khả năng reflection có thể được thiết kế độc lập ở những mức độ nhất định.

## 13. Checklist khi phân tích một API generic

Khi muốn biết một đoạn Java sử dụng erasure hay có lớp đặc thù, hãy kiểm tra theo thứ tự:

1. **Khai báo gốc là gì?** `class Box<T>` hay hai lớp `StringBox`, `IntBox` thật sự được định nghĩa?
2. **Bound của type parameter là gì?** `T` không bound xóa thành `Object`; `T extends Number` xóa thành `Number`.
3. **Đối số kiểu có tạo class riêng không?** Đừng đoán từ cú pháp `<String>`. Dùng `getClass()` để quan sát runtime class của các đối tượng cùng generic class.
4. **Có cast hoặc bridge không?** Đây thường là dấu hiệu cơ chế giữ an toàn kiểu/đa hình với erasure, không phải class specialization.
5. **Runtime metadata có gì?** `getReturnType()` thể hiện kiểu đã xóa, `getGenericReturnType()` phản ánh khai báo có generic nếu metadata hiện diện.
6. **Bottleneck thật nằm ở đâu?** Kiểm tra boxing, allocation, dữ liệu và kết quả benchmark trước khi viết lớp chuyên biệt bằng tay.

**Kết luận cốt lõi:** trong Java generics thông thường, `Box<String>` và `Box<Integer>` là hai cách sử dụng có **kiểu tĩnh khác nhau** nhưng cùng **khai báo class runtime `Box`**. Type erasure thực hiện điều này bằng cách xóa tham số kiểu khỏi descriptor, thêm cast và bridge khi cần. Việc tạo lớp hoặc mã thực thi riêng cho từng đối số kiểu là một chiến lược specialization khác, có những cơ hội tối ưu và chi phí riêng; Java không tự động thực hiện chiến lược “mỗi `T` một class” chỉ vì ta dùng cú pháp generic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-erasure-008">Thông tin chữ ký generic còn lưu trong class file</a>

<details>
<summary>Xem chi tiết</summary>

# Thông tin chữ ký generic còn lưu trong class file

## 1. Xóa kiểu không có nghĩa mọi dấu vết generic đều biến mất

Java Generics chủ yếu cung cấp **kiểm tra kiểu tại thời điểm biên dịch**. Sau khi kiểm tra, trình biên dịch dùng cơ chế *type erasure* (xóa kiểu) để tạo mô tả kiểu mà JVM có thể sử dụng. Chẳng hạn, `List<String>` và `List<Integer>` đều dùng cùng class runtime `java.util.ArrayList`; JVM không sinh một bản riêng của `ArrayList` cho mỗi đối số kiểu.

Tuy nhiên, câu nói “sau biên dịch không còn bất kỳ thông tin generic nào” là **không chính xác**. Class file có thể lưu **chữ ký generic được khai báo** bằng thuộc tính `Signature`. Nhờ vậy, công cụ như `javap` và API reflection có thể đọc lại những phần khai báo như:

- Tên và giới hạn của tham số kiểu, ví dụ `<T extends Number & Comparable<T>>`.
- Kiểu cha/interface có đối số kiểu, ví dụ `extends Base<String>`.
- Kiểu của field khai báo `List<String>`.
- Kiểu generic ở tham số, kết quả và một số thành phần chữ ký method/constructor.
- Wildcard `? extends`, `? super`, mảng generic và type variable khi chúng xuất hiện trong chữ ký được lưu.

Điểm mấu chốt là cần phân biệt hai lớp thông tin:

1. **Descriptor đã xóa**: kiểu JVM dùng để định danh field/method, gọi phương thức và kiểm tra bytecode, ví dụ `Ljava/util/List;`.
2. **Generic signature**: metadata bổ sung mô tả khai báo generic, ví dụ `Ljava/util/List<Ljava/lang/String;>;`.

`Signature` không làm mỗi đối tượng tự nhớ lại mọi đối số kiểu được dùng tại biểu thức `new`. Nó giúp khôi phục **thông tin ở vị trí khai báo** mà compiler đã ghi vào class file.

## 2. Đọc đúng ba khái niệm: kiểu ở mã nguồn, descriptor và Signature

Xét khai báo:

```java
class Catalog {
    java.util.List<String> names;
}
```

Đây là một class Java hợp lệ, chưa có chương trình `main`.

Trong mã nguồn, `names` có kiểu `List<String>`. Sau xóa kiểu, **field descriptor** là:

```text
Ljava/util/List;
```

Nếu compiler ghi generic `Signature` cho field, chuỗi metadata tương ứng là:

```text
Ljava/util/List<Ljava/lang/String;>;
```

Hai chuỗi này cùng mô tả field từ hai góc độ khác nhau:

| Góc nhìn | Biểu diễn | Mục đích chính |
|---|---|---|
| Java source type | `List<String>` | Compiler kiểm tra kiểu khi viết và dùng `names` |
| JVM field descriptor | `Ljava/util/List;` | Định dạng bytecode đã xóa dùng cho field |
| Generic Signature attribute | `Ljava/util/List<Ljava/lang/String;>;` | Giữ lại đối số kiểu `String` trong metadata khai báo |

Các ký hiệu trong hai chuỗi **không** phải chính cú pháp Java source:

- `L...;` ký hiệu một kiểu class/interface tham chiếu trong descriptor hoặc signature.
- `/` phân tách các thành phần tên nội bộ JVM, ví dụ `java/util/List`.
- `<...>` bên trong signature mã hóa đối số kiểu khi có.
- Chữ ký này được ghi dưới dạng chuỗi theo ngữ pháp class file của JVM, chứ không lưu một đối tượng `ParameterizedType` Java được khởi tạo sẵn. Reflection có thể đọc và diễn giải chuỗi đó khi ứng dụng yêu cầu.

Một class file còn có thể chứa những thuộc tính khác như `Code`, `Exceptions`, `RuntimeVisibleAnnotations`, `LocalVariableTable` hoặc `LocalVariableTypeTable`. Chúng có vai trò riêng; **không được đồng nhất tất cả với `Signature`**.

## 3. Thuộc tính `Signature` nằm ở đâu?

Theo định dạng Java class file, `Signature` là một thuộc tính gắn với các cấu trúc có thể có khai báo generic, cụ thể là:

- **Class/interface**: lưu tham số kiểu của class/interface và biểu diễn generic của superclass/superinterfaces.
- **Field**: lưu kiểu generic của trường.
- **Method** (bao gồm constructor ở dạng method trong class file): lưu phần chữ ký generic liên quan đến type parameters, parameter types, return type và throws signatures nếu thích hợp.

Một ví dụ tổng hợp:

```java
import java.io.Serializable;
import java.util.List;

class Box<T extends Number & Comparable<T>> implements Serializable {
    private T value;
    private List<? extends T> history;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void replace(T next) {
        value = next;
    }

    List<? extends T> history() {
        return history;
    }
}
```

Đây là class để phân tích, không có `main`. Các vị trí chứa dấu vết generic:

| Thành phần | Kiểu ở Java source | Kiểu/descriptor sau erasure | Thông tin có thể còn trong Signature |
|---|---|---|---|
| Tham số kiểu `T` | `T extends Number & Comparable<T>` | Erasure của `T` là `Number` | Tên `T`, giới hạn `Number`, `Comparable<T>` |
| `value` | `T` | `Number` | Field có type variable `T` |
| `history` | `List<? extends T>` | `List` | `List` với wildcard upper bound `T` |
| `Box(T)` | Tham số `T` | `(Number)void` ở mức descriptor | Constructor nhận `T` |
| `get()` | Trả `T` | Trả `Number` | Method trả `T` |
| `replace(T)` | Nhận `T` | Nhận `Number` | Method nhận `T` |
| `history()` | Trả `List<? extends T>` | Trả `List` | Method trả kiểu generic có wildcard |

Class file chỉ cần một số `Signature` tương ứng với các khai báo thực sự yêu cầu biểu diễn generic. Một thành viên hoàn toàn không generic có thể **không có** thuộc tính này. Không nên mặc định mọi field hoặc mọi method đều được gắn `Signature`.

### `Signature` là metadata bổ sung

JVM định danh các phương thức bằng tên và descriptor; `Signature` không thay thế descriptor, không phải một thành phần bổ sung trong lời gọi phương thức và không tự áp đặt phép kiểm tra rằng `ArrayList` runtime đang chứa toàn `String`.

Khi thực thi bytecode, JVM vẫn dựa trên các kiểu đã xóa, kiểm tra kiểu runtime thông thường và các lệnh cast mà compiler sinh. Generic signature phục vụ biểu diễn khai báo và cho các công cụ có khả năng hiểu generic.

## 4. Ví dụ thực hành: `javap` thấy cả descriptor lẫn generic signature

Tạo tệp **`SignatureClassFileDemo.java`** với nội dung đầy đủ:

```java
import java.util.List;

public class SignatureClassFileDemo<T extends Number & Comparable<T>> {
    private T value;
    private List<? extends T> history;

    public SignatureClassFileDemo(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T next) {
        value = next;
    }

    public List<? extends T> history() {
        return history;
    }

    public static void main(String[] args) {
        SignatureClassFileDemo<Integer> box =
                new SignatureClassFileDemo<>(7);
        System.out.println(box.get());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SignatureClassFileDemo.java
java SignatureClassFileDemo
```

Stdout:

```text
7
```

Dùng công cụ đi cùng JDK:

```bash
javap -p -s SignatureClassFileDemo
```

Tập trung vào những đoạn có ý nghĩa dưới đây (đây là **bản trích rút gọn**, không phải toàn bộ kết quả từ lệnh):

```text
private T value;
  descriptor: Ljava/lang/Number;

private java.util.List<? extends T> history;
  descriptor: Ljava/util/List;

public T get();
  descriptor: ()Ljava/lang/Number;

public void set(T);
  descriptor: (Ljava/lang/Number;)V

public java.util.List<? extends T> history();
  descriptor: ()Ljava/util/List;
```

Ý nghĩa:

- `T` có **giới hạn đứng đầu là `Number`**, nên tại chỗ dùng `T`, descriptor dùng `Number`.
- `List<? extends T>` bị xóa thành `List`; wildcard không xuất hiện trong descriptor.
- Phương thức `get()` trả về `Number` ở descriptor, nhưng `javap` vẫn có thể hiển thị khai báo `T` nhờ đọc generic metadata.
- `V` biểu thị kiểu trả về `void` trong method descriptor.

Để xem rõ thuộc tính lưu trong class file:

```bash
javap -p -v SignatureClassFileDemo
```

Tìm các dòng có nhãn `Signature:`. Ký hiệu `#...` tham chiếu tới constant pool có thể thay đổi giữa các bản biên dịch, nhưng các chuỗi signature cần chú ý gồm:

```text
TT;
Ljava/util/List<+TT;>;
()TT;
(TT;)V
<T:Ljava/lang/Number;:Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;
```

Đây là **các mẫu chuỗi signature liên quan**, không phải một đoạn output nguyên trạng theo đúng thứ tự trong `javap -v`. `TT;` nói tới type variable có tên `T`. Phần `+TT;` biểu diễn một wildcard `? extends T`. Chữ ký cuối lưu khai báo type parameter `T` cùng hai giới hạn và superclass `Object` của class.

Nếu muốn xem cả những phép ép kiểu do xóa kiểu tạo ra trong thân method, dùng:

```bash
javap -p -c SignatureClassFileDemo
```

`-c` hiển thị bytecode; `-s` hiển thị descriptor; `-v` cung cấp thông tin class file mở rộng, trong đó có các thuộc tính `Signature`. Đọc ba góc nhìn này cùng nhau giúp tránh nhầm metadata với logic runtime.

## 5. Cú pháp cơ bản của chuỗi generic Signature

Generic signature dùng một ngữ pháp riêng do JVM Specification định nghĩa. Không cần học thuộc hết ngữ pháp để lập trình ứng dụng, nhưng nhận diện một số ký hiệu giúp đọc `javap -v` và phân tích thư viện.

| Mẫu | Ý nghĩa minh họa |
|---|---|
| `Ljava/lang/String;` | Một kiểu class `String` |
| `Ljava/util/List<Ljava/lang/String;>;` | `List<String>` |
| `TT;` | Type variable tên `T` |
| `Ljava/util/List<TT;>;` | `List<T>` |
| `Ljava/util/List<+Ljava/lang/Number;>;` | `List<? extends Number>` |
| `Ljava/util/List<-Ljava/lang/Integer;>;` | `List<? super Integer>` |
| `Ljava/util/List<*>;` | `List<?>` |
| `[TT;` | Mảng `T[]` trong cú pháp signature |
| `(TT;)TT;` | Method nhận `T`, trả `T` |
| `<E:Ljava/lang/Object;>(TE;)TE;` | Method khai báo `<E>`, nhận `E`, trả `E` |

Các dấu quan trọng:

- `T` mở đầu biểu diễn *type variable signature*, còn tên biến kiểu nằm trước dấu `;`. Bởi vậy `TT;` là cú pháp “type variable `T`”, không phải class tên `TT`.
- `+` biểu diễn wildcard có upper bound (`? extends`).
- `-` biểu diễn wildcard có lower bound (`? super`).
- `*` biểu diễn wildcard không ràng buộc (`?`).
- `[` biểu diễn mảng.
- Cặp ngoặc `(...)` chứa các kiểu tham số method.
- Trong phần khai báo type parameter, dấu `:` mã hóa class/interface bound; với nhiều bound, có thể có nhiều dấu `:`.

Mã generic của **một class cụ thể** có thể chứa thêm nội dung như superclass, superinterfaces, return/throws signatures. Bảng chỉ nhằm giải thích các mẫu cơ bản, không thay thế toàn bộ ngữ pháp quy định trong JVMS.

### `List<?>` không cùng nghĩa với raw `List`

Mặc dù cả hai thường có cùng descriptor `Ljava/util/List;`, kiểu nguồn `List<?>` có wildcard với các kiểm tra kiểu nhất định, còn raw `List` chủ động bỏ thông tin tham số kiểu và có thể gây cảnh báo unchecked.

Nếu declaration có `List<?>`, generic signature có thể ghi lại wildcard `*`. Nếu declaration dùng raw `List`, sẽ không có đối số kiểu để mã hóa ở vị trí đó. Chúng không nên được đánh đồng chỉ vì descriptor trùng nhau.

## 6. Reflection có thể đọc generic ở khai báo

Java Reflection cung cấp hai nhóm API cần phân biệt:

- Các phương thức trả về `Class<?>` thường cho biết **kiểu đã xóa hoặc kiểu runtime** tương ứng.
- Các phương thức trả về `java.lang.reflect.Type` có tên chứa `Generic` thường cho biết **kiểu khai báo generic**, nếu metadata cần thiết hiện diện và hợp lệ.

Tạo tệp **`GenericSignatureReflectionDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;

public class GenericSignatureReflectionDemo {

    static class Box<T extends Number & Comparable<T>> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void accept(List<? extends T> input) {
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Box.class;

        TypeVariable<?> variable = type.getTypeParameters()[0];
        System.out.println("variable: " + variable.getName());

        for (Type bound : variable.getBounds()) {
            System.out.println("bound: " + bound.getTypeName());
        }

        Field field = type.getDeclaredField("value");
        System.out.println("field raw: " + field.getType().getTypeName());
        System.out.println("field generic: " + field.getGenericType().getTypeName());

        Method get = type.getDeclaredMethod("get");
        System.out.println("return raw: " + get.getReturnType().getTypeName());
        System.out.println("return generic: "
                + get.getGenericReturnType().getTypeName());

        Method accept = type.getDeclaredMethod("accept", List.class);
        Type parameter = accept.getGenericParameterTypes()[0];

        ParameterizedType listType = (ParameterizedType) parameter;
        WildcardType wildcard = (WildcardType)
                listType.getActualTypeArguments()[0];

        System.out.println("parameter raw: "
                + accept.getParameterTypes()[0].getTypeName());
        System.out.println("parameter generic raw: "
                + listType.getRawType().getTypeName());
        System.out.println("wildcard upper: "
                + wildcard.getUpperBounds()[0].getTypeName());
    }
}
```

Chạy:

```bash
javac GenericSignatureReflectionDemo.java
java GenericSignatureReflectionDemo
```

Stdout:

```text
variable: T
bound: java.lang.Number
bound: java.lang.Comparable<T>
field raw: java.lang.Number
field generic: T
return raw: java.lang.Number
return generic: T
parameter raw: java.util.List
parameter generic raw: java.util.List
wildcard upper: T
```

Giải thích kết quả:

1. `Class.getTypeParameters()` đọc tham số kiểu khai báo bởi class `Box`; tên biến là `T`.
2. `TypeVariable.getBounds()` trả về các giới hạn đã khai báo: `Number`, `Comparable<T>`. Erasure không buộc reflection chỉ thấy `Number`.
3. `Field.getType()` cho kiểu trường đã xóa là `Number`, còn `Field.getGenericType()` biết trường được khai báo bằng biến `T`.
4. `Method.getReturnType()` cho kiểu trả về erasure `Number`, còn `getGenericReturnType()` cho `T`.
5. `getDeclaredMethod("accept", List.class)` tìm method qua tham số đã xóa `List` vì overload lookup dùng `Class<?>` của các tham số runtime.
6. `getGenericParameterTypes()[0]` là `ParameterizedType` đại diện `List<? extends T>`; đối số bên trong là `WildcardType` có upper bound là `T`.

Chương trình ép kiểu sang `ParameterizedType` và `WildcardType` vì **chúng ta biết trước chính xác khai báo của method**. Khi làm công cụ reflection tổng quát, luôn kiểm tra bằng `instanceof` trước khi cast: một đối tượng `Type` có thể thuộc nhiều dạng khác nhau.

## 7. Các dạng `Type` mà reflection có thể trả về

`java.lang.reflect.Type` là giao diện chung cho nhiều hình thức mô tả kiểu. Đây là bản đồ thực dụng:

| Dạng phản ánh | Ví dụ nguồn | Interface/class reflection |
|---|---|---|
| Kiểu class không tham số hóa | `String` | `Class<?>` |
| Kiểu tham số hóa | `List<String>` | `ParameterizedType` |
| Biến kiểu | `T` trong `class Box<T>` | `TypeVariable<?>` |
| Wildcard | `? extends Number` | `WildcardType` |
| Mảng có thành phần kiểu generic | `T[]`, `List<String>[]` | Có thể là `GenericArrayType` |
| Mảng với thành phần class cụ thể | `String[]` | `Class<?>` |

Các điểm cần nhớ:

- `ParameterizedType.getRawType()` cho biết kiểu gốc của tham số hóa, ví dụ `List.class`.
- `ParameterizedType.getActualTypeArguments()` cho các đối số khai báo, ví dụ `String.class` với `List<String>`.
- `TypeVariable.getBounds()` và `WildcardType.getUpperBounds()/getLowerBounds()` đọc các ràng buộc được biểu diễn trong generic signature.
- `GenericArrayType.getGenericComponentType()` phân tích thành phần mảng khi component là kiểu generic thích hợp.
- `Type.getTypeName()` phù hợp để trình bày tên kiểu trong nhiều công cụ học tập, nhưng đừng phân tích chữ ký bằng cách cắt chuỗi tên một cách tùy tiện nếu đã có đối tượng `Type` có cấu trúc.

Một API cần nhận nhiều hình thức `Type` nên phân nhánh theo interface thay vì cast tất cả về `ParameterizedType`.

Ngoài các API đã minh họa, còn có `Class.getGenericSuperclass()`, `Class.getGenericInterfaces()`, `Method.getGenericExceptionTypes()`, `Constructor.getGenericParameterTypes()` và những API tương tự. Chúng trả về *thông tin được khai báo* chứ không khám phá được một cách thần kỳ các đối số kiểu chỉ tồn tại trong suy luận compiler tại vị trí gọi.

## 8. Vì sao `getClass()` không khôi phục được `List<String>` của một đối tượng?

Một hiểu lầm phổ biến là:

> “Nếu class file còn Signature, gọi `list.getClass()` chắc chắn sẽ biết danh sách này là `List<String>`.”

Điều đó **sai** đối với đối tượng generic thông thường. Thử chương trình độc lập trong tệp **`GenericCaptureDemo.java`**:

```java
public class GenericCaptureDemo {

    static class Container<X> {
    }

    static class IntContainer extends Container<Integer> {
    }

    public static void main(String[] args) {
        Container<String> words = new Container<>();
        Container<Integer> numbers = new Container<>();

        System.out.println(words.getClass().equals(numbers.getClass()));
        System.out.println(Container.class.getTypeParameters()[0].getName());

        System.out.println(IntContainer.class.getSuperclass().getTypeName());
        System.out.println(
                IntContainer.class.getGenericSuperclass().getTypeName());

        Container<String> anonymous = new Container<String>() {};
        System.out.println(
                anonymous.getClass().getGenericSuperclass().getTypeName());
    }
}
```

Biên dịch và chạy:

```bash
javac GenericCaptureDemo.java
java GenericCaptureDemo
```

Stdout:

```text
true
X
GenericCaptureDemo$Container
GenericCaptureDemo$Container<java.lang.Integer>
GenericCaptureDemo$Container<java.lang.String>
```

Phân tích:

- `new Container<String>()` và `new Container<Integer>()` thông thường cùng là instance của **một class runtime** `Container`.
- `Container.class.getTypeParameters()` chỉ nói class khai báo type variable `X`; nó không cho biết mỗi instance đã được tạo với đối số kiểu gì.
- `IntContainer extends Container<Integer>` khai báo **một quan hệ kế thừa cụ thể**. Class file của `IntContainer` có thể lưu supertype generic `Container<Integer>`; `getGenericSuperclass()` đọc được quan hệ này.
- `new Container<String>() {}` tạo **một anonymous subclass riêng** tại compile time. Superclass generic được ghi trong class file của subclass đó, vì vậy reflection có thể thấy `String`.

Hai dòng cuối không có nghĩa generic được *reify* cho mọi instance; đó là thông tin của **class con đã có khai báo hoặc cấu trúc kế thừa mang đối số kiểu cụ thể**.

### Trường hợp subclass generic vẫn chưa biết kiểu cụ thể

Ví dụ:

```java
class Child<U> extends java.util.ArrayList<U> {
}
```

`Child.class.getGenericSuperclass()` có thể cho biết quan hệ `ArrayList<U>`, nhưng không thể tự biết một instance `Child<String>` đã được tạo với `U = String`. Ở đây `U` vẫn là type variable của `Child`.

Muốn nắm một kiểu cụ thể tại runtime, API thường yêu cầu người gọi truyền một **type token** (`Class<T>` cho class không tham số hóa, hoặc `Type`/mẫu type reference phù hợp để mô tả kiểu tham số hóa), hoặc dùng một lớp con có metadata được khai báo cụ thể như trên. Type token không phải bằng chứng dữ liệu bên trong một collection đã thực sự thỏa mọi điều kiện generic.

## 9. Phương thức generic cũng có Signature độc lập

`Signature` không chỉ dùng cho class hoặc field. Một method tự khai báo tham số kiểu cũng có thể được phản ánh đầy đủ.

Tạo tệp **`GenericMethodSignatureDemo.java`**:

```java
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public class GenericMethodSignatureDemo {

    static <T extends Runnable & AutoCloseable> T pass(T input) {
        return input;
    }

    public static void main(String[] args) throws Exception {
        Method method = GenericMethodSignatureDemo.class
                .getDeclaredMethod("pass", Runnable.class);

        System.out.println("parameter raw: "
                + method.getParameterTypes()[0].getTypeName());
        System.out.println("parameter generic: "
                + method.getGenericParameterTypes()[0].getTypeName());
        System.out.println("return generic: "
                + method.getGenericReturnType().getTypeName());

        TypeVariable<Method> variable = method.getTypeParameters()[0];
        for (Type bound : variable.getBounds()) {
            System.out.println("bound: " + bound.getTypeName());
        }
    }
}
```

Chạy:

```bash
javac GenericMethodSignatureDemo.java
java GenericMethodSignatureDemo
```

Stdout:

```text
parameter raw: java.lang.Runnable
parameter generic: T
return generic: T
bound: java.lang.Runnable
bound: java.lang.AutoCloseable
```

`T` có giới hạn đầu là `Runnable`, nên method descriptor dùng `Runnable` ở vị trí tham số và kết quả. Nhưng method-level generic `Signature` giữ lại tên `T` cùng hai giới hạn, cho phép reflection phân biệt chúng.

Đây cũng là lý do cần nhìn đúng **nơi khai báo**: type variable của method không nhất thiết là type variable của class chứa method. `Method.getTypeParameters()` đọc tham số kiểu **của method**, còn `Class.getTypeParameters()` đọc tham số kiểu **của class**.

Với generic constructor, phương thức reflection tương ứng là `Constructor.getTypeParameters()` và `Constructor.getGenericParameterTypes()`. Constructor không có kiểu trả về như method thông thường.

## 10. Signature và descriptor tác động khác nhau đến runtime

Hãy xét:

```java
import java.util.List;

class Report {
    List<String> labels;
}
```

Class `Report` có field `labels`, nhưng đối tượng `List` ở runtime không tự kiểm tra từng giá trị chèn vào để bảo đảm chỉ chứa `String`. Nếu một nơi dùng raw type hoặc unchecked cast sai, lỗi có thể xuất hiện sau đó khi đọc phần tử dưới dạng `String`.

Generic signature của field có thể giúp một framework biết **field này được khai báo là `List<String>`**. Chẳng hạn, thư viện ánh xạ JSON có thể dùng thông tin đó để chọn cách chuyển đổi phần tử. Nhưng metadata không tự xác minh rằng dữ liệu thực tế đã an toàn.

### Điều JVM dùng khi liên kết và thực thi

- **Field/method descriptor** biểu diễn kiểu đã xóa mà bytecode sử dụng.
- **Lệnh runtime** như `checkcast` và kiểm tra kiểu mảng thực thi các quy tắc runtime cần thiết.
- **Synthetic bridge methods** có thể được compiler tạo khi override generic cần tương thích với chữ ký đã xóa.
- **Signature** hỗ trợ công cụ và reflection hiểu khai báo generic; nó không tạo thêm overload ở runtime theo từng đối số kiểu.

Ví dụ một interface `Processor<T>` khai báo `T process(T input)`, còn class triển khai dùng `String` có thể khiến compiler sinh bridge method để lời gọi qua `Processor` tiếp tục dispatch đúng sau xóa kiểu. Các công cụ reflection có thể thấy bridge method; method đó có thể được đánh dấu `isBridge()` và `isSynthetic()`.

Bridge method là **mã thực thi** do compiler sinh để giữ hành vi override, còn Signature là **metadata khai báo**. Chúng liên quan tới cùng vấn đề xóa kiểu nhưng không phải một thứ.

### Chữ ký generic không thay thế binary descriptor

Nếu một thư viện công khai đổi giới hạn đầu của type parameter, kiểu đã xóa ở field/method descriptor có thể thay đổi. Thay đổi đó có thể tác động tới tương thích nhị phân, dù kiểu Java nhìn tương tự ở mức ý tưởng.

Ngược lại, hai khai báo có cùng descriptor vẫn có thể mang generic signature khác nhau. JVM không dùng generic signature để phân biệt hai overload xung đột erasure; compiler Java có quy tắc ngăn một số khai báo gây name clash.

## 11. Signature có phải lúc nào cũng còn không?

Không thể giả định một class file bất kỳ luôn mang đầy đủ generic metadata.

**Trường hợp biên dịch Java hiện đại thông thường:** `javac` ghi Signature khi cần biểu diễn generic ở class/field/method. Đây là trường hợp của các ví dụ đang học.

**Trường hợp nguồn hoặc bytecode không có khai báo generic:** Có thể không có Signature tương ứng; reflection khi ấy cho thông tin kiểu không generic hoặc thông tin đã xóa.

**Trường hợp công cụ biến đổi bytecode:** Một số pipeline obfuscation, bytecode weaving, tối ưu hóa hoặc xây dựng class file thủ công có thể loại bỏ hoặc thay đổi Signature. Vì thuộc tính là metadata cho generic, nên công cụ phụ thuộc reflection cần cấu hình để giữ thuộc tính cần thiết.

**Trường hợp metadata không hợp lệ hoặc thiếu kiểu được tham chiếu:** API reflection phân tích generic có thể báo lỗi như `GenericSignatureFormatError`, `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` tùy tình huống. Không nên xem Signature như dữ liệu đáng tin tuyệt đối khi nạp class file từ nguồn không kiểm soát.

### `javac -g:none` và `LocalVariableTypeTable` là chuyện khác

`javac -g:none` vô hiệu hóa việc sinh các loại thông tin **debug** được điều khiển bởi tùy chọn `-g`. Nó không có nghĩa phải xóa Signature generic của class, field và method. Chữ ký generic không chỉ là một bảng debug.

Ngược lại, `LocalVariableTypeTable` là thuộc tính **tùy chọn**, thường gắn với thông tin debug của các biến cục bộ có kiểu generic, và có thể không hiện diện. Reflection chuẩn qua `Field` và `Method` không lấy generic của mọi biến cục bộ trong thân method.

Thử so sánh:

```bash
javac -g:none SignatureClassFileDemo.java
javap -p -v SignatureClassFileDemo
```

Bạn vẫn có thể tìm thấy `Signature` cho các khai báo generic của ví dụ, trong khi thông tin biến cục bộ/debug đã được lược bớt. Đây là một phép thử tốt để tránh nhầm giữa **generic declaration metadata** và **debug metadata**.

### Bảng phân biệt metadata

| Thuộc tính/cấu trúc | Chứa gì? | Có thay thế Signature không? |
|---|---|---|
| Descriptor | Kiểu JVM đã xóa cho field/method | Không; descriptor là phần cần thiết cho mô tả bytecode |
| `Signature` | Khai báo generic của class/field/method khi áp dụng | Đây là nơi giữ generic signature |
| `LocalVariableTable` | Thông tin tên, scope và descriptor của biến cục bộ trong debug | Không |
| `LocalVariableTypeTable` | Thông tin generic cho biến cục bộ nếu compiler/công cụ ghi | Không; phục vụ local variables |
| `RuntimeVisibleAnnotations` | Annotation có runtime retention ở những vị trí thích hợp | Không |
| `RuntimeVisibleTypeAnnotations` | Type-use annotations có runtime retention ở những vị trí thích hợp | Không |
| `Code` | Bytecode thân method cùng cấu trúc liên quan | Không |

Một số class file còn lưu tham số method, exceptions hoặc thuộc tính bổ sung; mục đích từng phần cần được xem độc lập.

## 12. Type-use annotation và Signature là hai cơ chế

Java cho phép annotation xuất hiện tại vị trí kiểu khi annotation đó có target phù hợp, ví dụ `List<@Nonnull String>` trong một hệ sinh thái có định nghĩa annotation type-use thích hợp.

Generic Signature mô tả cấu trúc generic như `List<String>`. Thông tin về annotation gắn lên một vị trí kiểu được lưu theo cơ chế **type annotations**, chứ không đơn giản mã hóa thành tên annotation trong chuỗi Signature.

Do đó:

- `Field.getGenericType()` trả về cấu trúc kiểu generic.
- `Field.getAnnotatedType()` dùng để xem các annotation trên kiểu tại vị trí khai báo field khi annotation và metadata phù hợp.
- Có hay không annotation runtime phụ thuộc `@Retention`, `@Target` và cách class file được tạo.

Khi viết framework cần cả generic structure lẫn type-use annotations, có thể phải dùng **cả hai** nhóm API. Không nên suy luận rằng chỉ cần phân tích Signature là đã thấy mọi annotation hoặc toàn bộ ràng buộc nghiệp vụ.

## 13. Phân biệt metadata khai báo với kiểu thực của dữ liệu

Xét ba tình huống:

**Tình huống A — field có generic cố định**

```java
class Registry {
    java.util.List<String> names;
}
```

Reflection trên `Registry.names` có thể thấy `List<String>`, vì đối số `String` được viết **ngay trong khai báo field**.

**Tình huống B — local variable do suy luận kiểu**

```java
var names = new java.util.ArrayList<String>();
```

Đây là một câu lệnh Java nằm trong method hợp lệ, không phải chương trình chạy độc lập. `var` để compiler suy ra kiểu tĩnh của biến cục bộ; việc một local variable được suy luận là `ArrayList<String>` không khiến mọi instance của `ArrayList` ở runtime mang đối số `String`, cũng không có nghĩa reflection chuẩn có API đọc trực tiếp mọi thông tin local-variable generic từ object.

**Tình huống C — subclass khai báo kiểu cha cụ thể**

```java
class StringQueue extends java.util.ArrayDeque<String> {
}
```

Class file của `StringQueue` có thể ghi quan hệ generic với `ArrayDeque<String>`. Reflection trên **class `StringQueue`** có thể truy xuất supertype parameterized. Điều đó khác với tạo `new ArrayDeque<String>()` trực tiếp: một instance `ArrayDeque` thường không giữ `String` như thuộc tính runtime của chính đối tượng.

Những khác biệt này giải thích vì sao các thư viện JSON, ORM, dependency injection hay phân tích schema có thể đọc một số thông tin generic rất hữu ích, nhưng đôi khi vẫn yêu cầu type token hoặc cấu hình rõ hơn. Metadata phản ánh **chữ ký khai báo đang có**, không phải lịch sử suy luận kiểu của mọi lời gọi.

## 14. Ứng dụng thực tế và lưu ý khi thiết kế API

### Viết thư viện dựa trên reflection

Khi đọc kiểu field, tham số method hoặc kiểu cha generic, ưu tiên API có chữ `Generic` và xử lý đúng các dạng `Type`. Nếu chỉ dùng `getType()`/`getReturnType()`, bạn thường nhìn thấy erasure và có thể đánh mất cấu trúc như `List<Invoice>`.

Ví dụ đoạn mã kiểm tra kiểu field (cần đặt trong class và có `Field field` phù hợp):

```java
Type type = field.getGenericType();

if (type instanceof ParameterizedType parameterized) {
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();
    // Kiểm tra raw và từng argument theo hợp đồng của framework.
}
```

Đây chỉ là đoạn minh họa API reflection, không phải chương trình độc lập. Bên ngoài ví dụ, cần giải quyết `TypeVariable`, `WildcardType`, generic array, nested/owner type và các tình huống metadata không đầy đủ trước khi coi thông tin là một class cụ thể.

### Thiết kế thư viện phát hành cho người khác

- Nếu thay đổi thứ tự giới hạn generic, kiểm tra cả **descriptor sau erasure** và **Signature**.
- Nếu một framework cần generic metadata, cấu hình pipeline build/shrink/obfuscation để giữ các thuộc tính cần thiết.
- Dùng `javap -p -s` và `javap -p -v` để kiểm chứng phần class file thực tế thay vì chỉ suy luận từ Java source.
- Với API nhận `Class<T>`, nhớ rằng `Class<List<String>>` không thể được cung cấp trực tiếp bằng biểu thức class literal `List<String>.class`. `List.class` chỉ là class literal của raw `List`; cấu trúc type parameterized cần biểu diễn bằng `Type` hoặc một cơ chế type reference phù hợp.
- Không sử dụng `Signature` để chứng nhận dữ liệu collection đã sạch hoặc hoàn toàn an toàn. Kiểm tra dữ liệu và bảo đảm kiểu runtime vẫn là trách nhiệm của ứng dụng.

### Quan hệ với công cụ bytecode

Công cụ đọc/sinh bytecode có thể nhìn thấy raw descriptor đồng thời với generic Signature. Khi tạo class file mới, cần đảm bảo chúng **nhất quán theo ngữ nghĩa**: một field khai báo `List<String>` cần descriptor tương ứng `List`, còn Signature mô tả đối số `String`. Nếu metadata không khớp với bytecode, các công cụ downstream và reflection có thể hiểu sai hoặc gặp lỗi.

Đừng viết một bộ parser Signature thủ công chỉ bằng `split("<")` hoặc thay thế chuỗi: ngữ pháp hỗ trợ nested parameterized types, owner types, bounds, arrays và wildcard. Nếu thực sự thao tác bytecode, sử dụng công cụ có parser Signature phù hợp với classfile version đang làm việc.

## 15. Những lỗi hiểu sai thường gặp

**Lỗi 1: “Type erasure xóa sạch toàn bộ thông tin generic.”** Không đúng. Generic declarations có thể còn trong `Signature` dù descriptor đã dùng kiểu bị xóa.

**Lỗi 2: “JVM dùng `Signature` để biết chính xác loại phần tử của mọi `ArrayList`.”** Không đúng. Phần tử generic của mỗi instance thông thường không được reify bằng Signature; kiểm tra kiểu tại runtime vẫn theo quy tắc kiểu đã xóa.

**Lỗi 3: “`getType()` và `getGenericType()` luôn giống nhau.”** Không đúng. Với field `T value` có bound `Number`, `getType()` có thể là `Number.class`, còn `getGenericType()` là `TypeVariable` mang tên `T`.

**Lỗi 4: “`Signature` chỉ lưu đối số kiểu của field.”** Không đúng. Nó còn dùng cho class-level và method-level generic declarations khi cần.

**Lỗi 5: “Nếu reflection thấy `<T>` thì biết mỗi object có `T` là gì.”** Không đúng. `Class.getTypeParameters()` nêu các biến kiểu được khai báo bởi class, không biết đối số của mọi lần khởi tạo.

**Lỗi 6: “Đổi bound không ảnh hưởng gì vì generic đã bị xóa.”** Không đúng. Bound đứng đầu quyết định erasure của type variable; sự thay đổi có thể ảnh hưởng descriptor và tương thích nhị phân.

**Lỗi 7: “`-g:none` sẽ xóa generic Signature.”** Không đúng theo cách hoạt động bình thường của `javac`: Signature khai báo là metadata generic, tách biệt các bảng thông tin debug.

**Lỗi 8: “`List<?>` chỉ là raw `List`.”** Không đúng. Hai kiểu có thể dùng cùng raw descriptor, nhưng generic signature và kiểm tra kiểu ở mã nguồn khác nhau.

**Lỗi 9: “Một đối tượng `Type` đọc được luôn là `ParameterizedType`.”** Không đúng. Cần phân biệt `Class`, `TypeVariable`, `WildcardType`, `GenericArrayType` và `ParameterizedType`.

**Lỗi 10: “Có thể tin mọi generic signature đọc từ class file bất kỳ.”** Không nên. Class file do công cụ biến đổi, thiếu dependency hoặc chứa chữ ký lỗi có thể khiến reflection/công cụ thất bại; ứng dụng xử lý dữ liệu không tin cậy cần bảo vệ luồng phân tích.

## 16. Tự kiểm tra kiến thức

1. Với `List<String> values;`, field descriptor có còn `<String>` không? **Đáp án:** không, descriptor dùng `Ljava/util/List;`; generic Signature có thể giữ đối số `String`.
2. Với `<T extends Number & Comparable<T>>`, erasure của `T` là gì? **Đáp án:** `Number`, vì `Number` là giới hạn đầu tiên. `Comparable<T>` vẫn có thể xuất hiện trong `Signature` khai báo.
3. Vì sao `Container.class.getTypeParameters()` không suy ra một instance `Container<Integer>` đang giữ `Integer`? **Đáp án:** API này phản ánh biến kiểu được **khai báo** trên class, không phản ánh đối số của mọi instance.
4. `Class.getGenericSuperclass()` hữu ích khi nào? **Đáp án:** khi class có quan hệ superclass được khai báo bằng kiểu parameterized, ví dụ `IntContainer extends Container<Integer>`.
5. `Method.getReturnType()` và `Method.getGenericReturnType()` khác gì? **Đáp án:** cái đầu trả `Class<?>` thể hiện kiểu đã xóa, cái sau trả `Type` diễn đạt kiểu generic trong khai báo nếu có.
6. `LocalVariableTypeTable` có phải thuộc tính `Signature` của field không? **Đáp án:** không; nó là thuộc tính riêng cho thông tin generic của biến cục bộ, thường phụ thuộc debug metadata.
7. Có thể dùng `new ArrayList<String>().getClass()` để biết `String` không? **Đáp án:** không theo cách chung; class runtime của `ArrayList<String>` và `ArrayList<Integer>` là cùng `ArrayList.class`.
8. `getGenericType()` cho phép bỏ qua việc kiểm tra dữ liệu JSON đưa vào `List<String>` không? **Đáp án:** không; metadata mô tả khai báo, không kiểm tra chất lượng hoặc kiểu thực của từng giá trị input.

### Bài thực hành nhỏ

Tạo class có ba field:

```java
class WildcardFields {
    java.util.List<?> unknown;
    java.util.List<? extends Number> upper;
    java.util.List<? super Integer> lower;
}
```

Biên dịch và dùng `javap -p -s WildcardFields`, sau đó `javap -p -v WildcardFields`. Hãy dự đoán trước:

- Cả ba descriptor đều là `Ljava/util/List;`.
- Signature thứ nhất có ký hiệu `*` cho wildcard.
- Signature thứ hai có `+` trước kiểu `Number`.
- Signature thứ ba có `-` trước kiểu `Integer`.

Mục tiêu bài tập là nhìn thấy **cùng một raw descriptor** nhưng có **ba generic signatures khác nhau**. Nếu tiếp tục đọc bằng reflection, hãy phân tích các đối số của `ParameterizedType` dưới dạng `WildcardType` thay vì cố cast trực tiếp sang `Class<?>`.

## 17. Tổng kết và tài liệu tra cứu

**Generic Signature là dấu vết có cấu trúc của khai báo generic được lưu trong class file.** JVM vẫn dùng descriptor đã xóa để biểu diễn field/method, trong khi các công cụ và reflection có thể đọc metadata `Signature` để biết type parameters, bounds, parameterized types và wildcard khi chúng được ghi.

Cần ghi nhớ cùng lúc hai điều: **erasure vẫn có hiệu lực đối với mô hình thực thi**, và **một phần khai báo generic vẫn có thể truy xuất ở runtime qua class metadata**. Khả năng truy xuất này không làm generic trở thành reified cho mọi object và không thay thế việc xác minh dữ liệu.

Nguồn tham khảo chuẩn:

- [JVM Specification, §4.7.9 — The `Signature` Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.9): cấu trúc, vị trí và điều kiện của thuộc tính Signature.
- [JVM Specification, §4.7.9.1 — Signatures](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.9.1): ngữ pháp class, method và field type signatures.
- [Java Language Specification, §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): định nghĩa kiểu bị xóa.
- [Java SE 17 — `java.lang.reflect.Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html): hệ phân cấp API biểu diễn kiểu generic.
- [Java SE 17 — `java.lang.reflect.Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): `getType()` và `getGenericType()`.
- [Java SE 17 — `java.lang.Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): tham số kiểu, kiểu cha generic và generic interfaces.

</details>

- [Quay lại đầu trang](#back-to-top)
