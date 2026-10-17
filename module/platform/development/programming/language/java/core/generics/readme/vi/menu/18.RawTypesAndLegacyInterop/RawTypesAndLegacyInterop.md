<a id="back-to-top"></a>

# Kiểu thô

## Menu
- [Kiểu thô (raw type) là gì và vai trò trong mã trước generics](#raw-types-and-legacy-interop-001)
- [Phân biệt raw type với kiểu thông thường không có tham số kiểu](#raw-types-and-legacy-interop-002)
- [Chuyển từ kiểu tham số hóa sang raw type](#raw-types-and-legacy-interop-003)
- [Chuyển từ raw type về kiểu tham số hóa và cảnh báo unchecked](#raw-types-and-legacy-interop-004)
- [Kiểu của thành viên khi được truy cập qua raw type](#raw-types-and-legacy-interop-005)
- [Nguy cơ trộn dữ liệu sai kiểu và lỗi xuất hiện muộn](#raw-types-and-legacy-interop-006)
- [Raw type ở lớp bên trong và giới hạn kiểu tham số hóa một phần](#raw-types-and-legacy-interop-007)
- [Di chuyển API cũ sang generics theo từng ranh giới](#raw-types-and-legacy-interop-008)

## <a id="raw-types-and-legacy-interop-001">Kiểu thô (raw type) là gì và vai trò trong mã trước generics</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu thô (raw type) là gì và vai trò trong mã Java trước generics

## Mục tiêu

**Raw type** là cách dùng một kiểu generic nhưng **không cung cấp đối số kiểu**, ví dụ viết `List` thay vì `List<String>` hoặc `List<?>`. Hiểu raw type giúp đọc và tích hợp các thư viện, lớp, phương thức viết trước khi Java hỗ trợ generics, đồng thời nhận ra vị trí mà compiler không thể kiểm tra đầy đủ tính an toàn kiểu.

Sau bài này, bạn có thể:

- Phân biệt kiểu generic, kiểu tham số hóa và raw type bằng ví dụ Java thực tế.
- Giải thích vì sao mã Java trước phiên bản 5 thường dùng `List`, `Object`, phép cast và vì sao Java vẫn hỗ trợ cách viết đó.
- Nắm vai trò của **type erasure**, **backward compatibility** và **migration compatibility** trong sự tồn tại của raw type.
- Đọc đúng cảnh báo `[rawtypes]`, `[unchecked]`, xác định lúc nào heap pollution và `ClassCastException` có thể xuất hiện.
- Chọn `List<?>`, `List<Object>`, `List<T>` hay một adapter có kiểm tra phần tử thay cho raw type.
- Biết cách khoanh vùng tiếp xúc với API legacy, từng bước chuyển mã cũ sang generics mà không tạo cảm giác an toàn giả.

## 1. Nhận biết raw type trong mã nguồn

Với interface `List<E>` và lớp `ArrayList<E>` trong `java.util`:

```java
List<String> names = new ArrayList<>(); // Parameterized type.
List<?> unknown = names;                // Parameterized type với wildcard.
List raw = names;                       // Raw type.
```

Các đoạn trên minh họa khai báo trong ngữ cảnh có import `java.util.List` và `java.util.ArrayList`. Sự khác biệt là:

| Cách viết | Ý nghĩa ở compile time | Có phải raw type? |
| --- | --- | --- |
| `List<String>` | Danh sách được khai báo có phần tử kiểu `String` | Không |
| `List<Integer>` | Danh sách được khai báo có phần tử kiểu `Integer` | Không |
| `List<Object>` | Danh sách được khai báo có phần tử kiểu `Object` | Không |
| `List<?>` | Danh sách với kiểu phần tử cụ thể chưa biết | Không |
| `List<E>` bên trong khai báo generic | Có tham số kiểu `E` | Không |
| `List` | Cách dùng kiểu generic mà bỏ đối số kiểu | **Có** |
| `ArrayList` | Cách dùng lớp generic mà bỏ đối số kiểu | **Có** |

Raw type xuất hiện khi tên lớp/interface generic được dùng **không có phần tham số kiểu**. Ví dụ:

```java
class Box<T> {
    private T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
```

Với lớp trên, `Box<String>` là kiểu tham số hóa; chỉ viết `Box` làm kiểu biến hoặc tham số là dùng **raw `Box`**.

Một điểm dễ nhầm: `Object` bản thân **không** là raw type. Nó là kiểu thông thường không khai báo tham số generic. `List<Object>` cũng **không phải raw type** vì đã chỉ rõ đối số kiểu `Object`.

## 2. Vì sao raw type có mặt trong lịch sử Java?

Java có `java.util.List` và các collection trước khi ngôn ngữ có generics. Trước **Java 5 (2004)**, code thường có hình dạng:

```java
import java.util.ArrayList;
import java.util.List;

class OldStyleExample {
    public static void main(String[] args) {
        List names = new ArrayList();
        names.add("Lan");
        names.add("Minh");

        String first = (String) names.get(0);
        System.out.println(first); // Lan
    }
}
```

Ngày nay đoạn chương trình vẫn biên dịch bằng JDK hiện đại; khi bật `-Xlint:rawtypes` và `-Xlint:unchecked`, compiler cảnh báo những chỗ dùng raw generic hoặc thao tác không được kiểm tra. Đây là **mã theo phong cách lịch sử**, không có nghĩa lập trình viên thời trước Java 5 đã có generic `List<E>` để cố ý bỏ đối số kiểu.

Vì chưa có generics, `List` lưu các tham chiếu dạng `Object`. `get(int)` trả về `Object`, người gọi phải cast thủ công nếu muốn dùng như `String`:

```java
String first = (String) names.get(0);
```

Những vấn đề của cách viết cũ:

- Không có cam kết cấp ngôn ngữ rằng danh sách chỉ chứa `String`.
- Có thể thêm một `Integer` vào danh sách chứa chuỗi mà compiler không bác bỏ vì nội dung cụ thể không được mô tả trong kiểu.
- Cast sai chỉ bị phát hiện khi chạy và có thể ném `ClassCastException`.
- Mỗi chỗ đọc phải chủ động cast; việc thay đổi API và bảo trì phức tạp hơn.

Từ Java 5, generics cho phép chỉ định `List<String>` để compiler kiểm tra việc thêm/lấy phần tử:

```java
List<String> names = new ArrayList<>();
names.add("Lan");
// names.add(123); // Không biên dịch.
String first = names.get(0); // Không cần tự viết cast trong source.
```

Một số thay đổi tạo nên cải tiến: compiler biết các phép gọi qua `List<String>` có kiểu phần tử `String`, hỗ trợ suy luận kiểu và cảnh báo/lỗi ngay lúc biên dịch. Compiler vẫn có thể tự chèn cast vào bytecode theo cơ chế type erasure.

Raw type tiếp tục tồn tại vì Java cần **tương thích với mã và thư viện cũ** đã viết trước generics, giúp mã cũ vẫn có thể sử dụng các lớp collection đã được tổng quát hóa.

## 3. Raw type liên quan thế nào đến type erasure?

Java generics chủ yếu kiểm tra kiểu tại **compile time**. Trong quá trình biên dịch, tham số kiểu được xóa khỏi JVM descriptor ở các vị trí thích hợp. Với tham số kiểu không có bound:

```java
class Box<T> {
    private T value;
    void set(T value) { this.value = value; }
    T get() { return value; }
}
```

`T` có erasure là `Object`. Các descriptor tiêu biểu:

| Thành phần trong source | Descriptor của JVM |
| --- | --- |
| Field `T value` | `Ljava/lang/Object;` |
| `void set(T value)` | `(Ljava/lang/Object;)V` |
| `T get()` | `()Ljava/lang/Object;` |
| `List<E>.add(E)` với `E` không bound | `(Ljava/lang/Object;)Z` |
| `List<E>.get(int)` | `(I)Ljava/lang/Object;` |

Điều này giúp mã sử dụng các phiên bản API trước generics tiếp tục có khả năng liên kết với bytecode phù hợp. Ví dụ, khi thư viện collection thêm metadata generic `List<E>` ở mã nguồn, các descriptor cơ bản như `get(int):Object` không đột ngột biến thành `get(int):String` theo từng cách gọi. Không có runtime class riêng `ArrayList<String>` và `ArrayList<Integer>` cho từng đối số generic.

**Tuy vậy raw type và erasure không phải một khái niệm.**

- **Type erasure** là cách compiler biểu diễn và dịch generic sang bytecode.
- **Raw type** là cách lập trình viên **viết một tham chiếu tới kiểu generic mà không có type arguments** ở mã nguồn.
- Một phương thức generic được dùng đúng với `List<String>` cũng trải qua erasure; không phải cứ erasure là code đã dùng raw type.
- Raw type bỏ bớt thông tin kiểm tra kiểu ở source, vì vậy có thể dẫn đến cảnh báo unchecked.

Việc compiler biên dịch generics bằng erasure cho phép tương thích runtime với code cũ; raw type là cầu nối cú pháp để tương tác với mã nguồn/API theo phong cách cũ.

## 4. Ví dụ đầy đủ: raw type có thể làm hỏng dữ liệu typed

Tạo tệp **`RawTypeDemo.java`**, Java 17 trở lên:

```java
import java.util.ArrayList;
import java.util.List;

public class RawTypeDemo {
    static class Box<T> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    // API theo phong cách trước Java 5: List không có type argument.
    static void legacyAppend(List list, Object value) {
        list.add(value);
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        words.add("Java");
        legacyAppend(words, 42);
        System.out.println("list data: " + words);

        try {
            String second = words.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("list read: " + ex.getClass().getSimpleName());
        }

        Box<String> box = new Box<>("safe");
        Box rawBox = box;
        rawBox.set(123);
        try {
            String value = box.get();
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println("box read: " + ex.getClass().getSimpleName());
        }
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac -Xlint:rawtypes -Xlint:unchecked RawTypeDemo.java
java RawTypeDemo
```

Stdout chương trình **đã kiểm tra bằng JDK**:

```text
list data: [Java, 42]
list read: ClassCastException
box read: ClassCastException
```

Compiler cũng cảnh báo, chẳng hạn `[rawtypes] found raw type: List` và `[unchecked] unchecked call to add(E)` hay `set(T)`. Nội dung hiển thị cụ thể của cảnh báo phụ thuộc phiên bản compiler và vị trí dòng; đây không phải stdout của `java RawTypeDemo`.

### Diễn biến chính xác

1. `words` được khai báo `List<String>`, nên những lời gọi bình thường như `words.add(42)` **không biên dịch**.
2. `legacyAppend` nhận raw `List`; khi truyền `words` vào, phương thức nhận cùng một đối tượng danh sách. `list.add(42)` không được kiểm tra như thao tác của `List<String>` và compiler phát cảnh báo unchecked trong thân phương thức.
3. Nội dung thật của danh sách bây giờ là `[Java, 42]`, dù biến `words` vẫn được khai báo `List<String>`. Tình trạng dữ liệu không phù hợp với lời hứa generic này gọi là **heap pollution**.
4. Khi `String second = words.get(1)`, compiler dựa vào `List<String>` và có thể sinh `checkcast String`. Giá trị thật là `Integer`, nên `ClassCastException`.
5. Với `Box<String>`, gán `Box rawBox = box` không tạo hộp thứ hai: cả hai biến tham chiếu cùng một instance. Gọi `rawBox.set(123)` làm nhiễm dữ liệu trong hộp. Lời đọc `String value = box.get()` thất bại khi cast.

**Điều cần rút ra:** raw type vẫn có thể chạy được và tương thích về binary, nhưng mất một phần bảo đảm kiểm tra kiểu ở compile time. Lỗi có thể xuất hiện **muộn tại điểm đọc**, xa nơi dữ liệu sai kiểu được đưa vào.

## 5. Đọc đúng hai loại cảnh báo của compiler

Bật cả hai nhóm cảnh báo khi xem mã legacy:

```bash
javac -Xlint:rawtypes -Xlint:unchecked RawTypeDemo.java
```

### `[rawtypes]`: dùng tên generic mà không có type argument

Ví dụ:

```java
List list = new ArrayList();
Box box = new Box("X");
```

Hai dòng này dùng raw `List`, raw `ArrayList`, raw `Box`. Với `-Xlint:rawtypes`, compiler cảnh báo sự thiếu vắng tham số kiểu. Cảnh báo `rawtypes` giúp chỉ ra nơi có thể cải thiện API để diễn tả dữ liệu rõ hơn.

### `[unchecked]`: một phép thao tác/biến đổi không thể được compiler kiểm tra đầy đủ

Ví dụ:

```java
List raw = new ArrayList();
raw.add("A"); // Unchecked call tới add(E) qua raw List.

List<String> words = raw; // Unchecked conversion từ raw List.
```

Ở thời điểm gán, compiler không thể xác minh toàn bộ phần tử trong raw list thật sự là `String`. Việc gán có thể biên dịch nhưng phát cảnh báo unchecked, tạo điều kiện dẫn đến `ClassCastException` khi dữ liệu được lấy ra.

Bảng phân biệt:

| Cảnh báo | Chỉ ra điều gì? | Ví dụ |
| --- | --- | --- |
| `rawtypes` | Tên kiểu generic được dùng như raw type | `List list` |
| `unchecked` | Việc gọi/gán/cast liên quan generic không được kiểm tra đầy đủ | `raw.add(1)`, `List<String> x = raw` |
| Lỗi biên dịch | Compiler chứng minh được lời gọi typed không tương thích | `List<String> x; x.add(1)` |
| Lỗi runtime | Dữ liệu thực không phù hợp phép cast/giả định | `ClassCastException` khi đọc |

Hai cảnh báo có thể cùng xuất hiện tại các chỗ khác nhau; chúng **không đồng nghĩa**. Một khai báo raw không nhất thiết ngay lập tức gây lỗi runtime, và một unchecked cast có thể xuất hiện cả khi không có raw declaration gần đó. Tắt cảnh báo không làm chương trình an toàn hơn.

`@SuppressWarnings({"rawtypes", "unchecked"})` đôi khi cần thiết ở một adapter legacy đã được kiểm tra thủ công, nhưng không nên rải chú thích đó khắp code để loại cảnh báo mà chưa chứng minh dữ liệu đúng.

## 6. So sánh raw `List`, `List<?>`, `List<Object>` và `List<String>`

Sự khác biệt quan trọng không chỉ là cách viết:

| Kiểu biến | Có thể đọc thành | Có thể thêm `String`? | Có thể thêm `Integer`? |
| --- | --- | --- | --- |
| `List<String>` | `String` | Có | Không |
| `List<Object>` | `Object` | Có | Có |
| `List<?>` | `Object` | Không, trừ `null` | Không, trừ `null` |
| Raw `List` | `Object` | Có, với cảnh báo unchecked | Có, với cảnh báo unchecked |

Bảng giả định danh sách có thể sửa; với `List.of(...)` hoặc danh sách không hỗ trợ ghi, mọi lời gọi sửa đổi có thể ném `UnsupportedOperationException` ở runtime dù được phép theo kiểu Java.

### Vì sao `List<?>` tốt hơn raw `List` khi chỉ cần đọc?

```java
static void printElements(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Đây là method dùng được khi đặt trong một class và import `java.util.List`. Nó chấp nhận `List<String>`, `List<Integer>` và nhiều loại danh sách khác nhưng **không cho phép tự ý nhét đối tượng vào danh sách khi chưa biết kiểu phần tử**.

Ngược lại, `static void printElements(List items)` đưa raw type vào API và mở khả năng gọi các phương thức ghi có cảnh báo unchecked. Nếu chỉ cần quan sát, `List<?>` diễn tả ý định an toàn và chính xác hơn.

### Vì sao không luôn thay raw `List` bằng `List<Object>`?

Vì `List<String>` **không phải subtype của** `List<Object>`. Generics của Java bất biến (*invariant*); phép gán:

```java
List<String> names = new ArrayList<>();
// List<Object> all = names; // Lỗi biên dịch.
List<?> any = names;         // Hợp lệ.
```

Nếu `List<String>` được xem như `List<Object>`, code khác có thể thêm `Integer` thông qua biến `all`, phá cam kết của `names`. Với API chỉ đọc, `List<?>` thường là cách thay raw type có tính tương thích tốt hơn `List<Object>`.

Nếu **đúng yêu cầu nghiệp vụ** là một danh sách có thể chứa nhiều loại giá trị khác nhau, `List<Object>` có ý nghĩa riêng; nó không phải giải pháp thay thế tùy tiện cho mọi `List<String>`.

## 7. Vai trò hợp lệ của raw type khi giao tiếp với API legacy

Raw type không xuất hiện chỉ do lập trình viên quên cú pháp. Nó còn là một phần trong chiến lược chuyển đổi giữa thế giới Java **trước** và **sau** generics.

Các tình huống thường thấy:

1. **Mã nguồn cũ** khai báo `List`, `Map`, `Class` hoặc container riêng như `Box` mà không có type arguments.
2. **API cũ** nhận/trả collection không có thông tin kiểu phần tử, vì hợp đồng API lúc được viết không có generics.
3. **Thư viện binary cũ** vẫn phải liên kết được với chương trình mới. Type erasure giúp các descriptor tương thích ở các trường hợp được thiết kế cho mục đích này.
4. **Quá trình nâng cấp dần**: có thể thay từng chỗ raw type bằng dạng generic, dùng adapter tại ranh giới thay vì bắt buộc viết lại toàn bộ ứng dụng cùng lúc.

Tuy nhiên, raw type nên được nhìn như **điểm tiếp xúc cần kiểm soát**, không phải cách thiết kế API generic mới. Khi thư viện đã có dạng generic tương ứng, ưu tiên tham số hóa kiểu theo dữ liệu thật.

Một nguyên tắc nâng cấp: đừng vội tuyên bố raw `List` chứa `String` chỉ vì dữ liệu thử nghiệm hiện tại có vẻ là chuỗi. Kiểm tra hợp đồng trả về từ thư viện hoặc kiểm tra từng phần tử trước khi xuất ra một `List<String>` được tin cậy.

## 8. Tạo một ranh giới đọc có kiểm tra khi gặp API cũ

Nếu code cũ trả raw `List` mà phía mới muốn bảo đảm tất cả phần tử là chuỗi, **không cast cả list thành `List<String>`**. Hãy đưa nguồn vào một API nhận `List<?>` rồi kiểm tra từng phần tử:

```java
static java.util.List<String> validatedNames(java.util.List<?> source) {
    java.util.List<String> result = new java.util.ArrayList<>();
    for (Object item : source) {
        result.add(String.class.cast(item));
    }
    return java.util.List.copyOf(result);
}
```

Đây là một **method minh họa** để đặt trong class; bài đã có chương trình độc lập `RawTypeDemo.java` ở phần 4 để quan sát raw write và late failure. Trong helper, `String.class.cast` xác minh runtime class của từng phần tử; `List.copyOf` từ chối `null` và tạo danh sách không cho chỉnh sửa qua API thông thường. Nếu muốn cho phép null, phải định nghĩa hợp đồng và dùng kết quả khác.

Đường kiểm tra này hữu ích khi **legacy interface chưa thể sửa**, nhưng phía hiện đại không muốn phát tán unchecked cast. Một bản adapter ở production còn cần làm rõ index khi dữ liệu sai, quyền sở hữu/cập nhật của list nguồn và việc sao chép có thực sự đủ. Bài ở đây chỉ giới thiệu chiến lược; chương trình di trú nhiều tầng, xử lý caller cũ và tương thích nhị phân cần được thiết kế tại phạm vi hệ thống.

## 9. Raw type khác wildcard ở kiểm tra khi biên dịch

Phần này thường gây nhầm:

```java
List<String> strings = new ArrayList<>();
List raw = strings;       // Raw type.
List<?> unknown = strings;
```

Khi viết:

```java
raw.add(100);
```

Trình biên dịch có thể cho phép lời gọi nhưng cảnh báo **unchecked**: raw type làm mất liên kết với đối số `String` trong tham chiếu đó.

Khi viết:

```java
// unknown.add(100); // Không biên dịch.
// unknown.add("X"); // Không biên dịch.
```

Compiler **không biết** wildcard cụ thể là kiểu nào, nên không cho phép ghi một đối tượng không phải `null`. Điều này giúp `List<?>` duy trì an toàn kiểu khi code chỉ cần đọc.

Cả `raw` và `unknown` đều là tham chiếu tới **chính danh sách `strings`**, không tạo bản sao. Khác biệt nằm ở **hợp đồng compile time** của hai kiểu tham chiếu.

Một ví dụ khác cần tránh:

```java
// if (obj instanceof List<String>) { } // Không hợp lệ vì type argument không reifiable.
if (obj instanceof List<?>) {
    // Biết obj là List, không kết luận phần tử bên trong là String.
}
```

Đoạn kiểm tra trên giả định đã có `Object obj` trong ngữ cảnh. `List<?>` là trường hợp có thể kiểm tra bằng `instanceof`, còn `List<String>` không phải một runtime class chuyên biệt để kiểm tra trực tiếp.

## 10. Một raw type không xóa mọi giới hạn kiểu

Có một ngoại lệ cần hiểu đúng khi suy luận: nếu type parameter **có upper bound**, erasure thường dùng upper bound đó, không luôn là `Object`.

Ví dụ minh họa:

```java
class NumberHolder<T extends Number> {
    void accept(T number) {
        System.out.println(number);
    }
}
```

Sau erasure, `accept(T)` có descriptor nhận `Number`. Nếu dùng:

```java
NumberHolder rawHolder = new NumberHolder();
rawHolder.accept(Integer.valueOf(7)); // Hợp lệ.
// rawHolder.accept("text");           // Vẫn là lỗi biên dịch.
```

Đoạn trên cần đặt vào một class/method phù hợp để chạy; nó tập trung giải thích chữ ký. Dù `rawHolder` là raw type, phương thức nhận `Number` sau erasure, nên `String` không thể truyền thẳng vào. Không thể kết luận rằng raw type cho phép **mọi kiểu giá trị** ở **mọi phương thức**.

Nếu mã đang dùng `Box<T extends Number>`, khả năng can thiệp từ raw type phải được phân tích theo bound và chữ ký erased thật. Raw type làm giảm thông tin generic, nhưng các ràng buộc còn được biểu diễn trong chữ ký erased vẫn có hiệu lực.

## 11. Định hướng chuyển đổi khi bắt gặp raw API

Trong codebase lâu năm, raw type có thể xuất hiện ở một chỗ khai báo, trong chữ ký method, hoặc được truyền tới các callers khác. Hãy bắt đầu với **mục đích sử dụng** thay vì thay tất cả `List` thành `List<Object>`:

| Bối cảnh | Hành động ưu tiên | Vì sao |
|---|---|---|
| Sở hữu dữ liệu và biết chắc chỉ chứa `String` | Sửa nơi tạo, nơi ghi và chữ ký thành `List<String>` | Compiler bảo vệ invariant từ nguồn |
| Không biết element type, chỉ cần quan sát | Nhận `List<?>` | Không cho mã quan sát thêm non-null tùy ý |
| API legacy trả raw `List`, ứng dụng cần `List<String>` | Dùng helper kiểm tra từng phần tử rồi copy | Không biến unchecked cast thành lời hứa giả |
| Chưa thể chứng minh element type | Giữ `List<?>` và xử lý như `Object` | Trung thực với thông tin hiện có |

Bật `javac -Xlint:rawtypes,unchecked` để định vị raw declarations và unchecked operations. Việc biên dịch vẫn thành công với warning không chứng minh dữ liệu an toàn. Nếu phải để một raw boundary tồn tại, cô lập nó và dùng tests với dữ liệu sai loại trước khi công bố API typed.

**Điểm kết nối:** Phân biệt chiều chuyển đổi raw/parameterized, cách type erasure tác động tới members và kế hoạch di trú caller/callee là các bước tiếp theo; định nghĩa nền tảng của raw type không tự quyết định toàn bộ phương án migration.

## 12. Các lỗi phổ biến

### Nhầm raw `List` với `List<Object>`

`List<Object>` có type argument rõ ràng, cho phép chứa nhiều loại đối tượng nhưng không thể nhận trực tiếp tham chiếu `List<String>` do invariance. Raw `List` bỏ kiểm tra tham số generic và có thể phát cảnh báo.

### Thấy “biên dịch được” là cho rằng “an toàn”

Unchecked warning là thông tin compiler **không chứng minh được** thao tác an toàn. Chạy được với dữ liệu thử không bảo đảm an toàn khi dữ liệu thay đổi.

### Đọc `List<String>` sau khi nguồn raw đã thêm giá trị sai

Compiler vẫn xem biểu thức `words.get(i)` có kiểu nguồn `String`; khi cast do compiler chèn kiểm tra giá trị thực là `Integer`, lỗi có thể xảy ra. Vì thế vị trí ném lỗi thường là chỗ **đọc/ép kiểu**, không nhất thiết là chỗ thêm dữ liệu sai.

### Coi `List<?>` là raw vì nó không nêu kiểu cụ thể

`List<?>` vẫn là **kiểu tham số hóa**, mang ràng buộc wildcard và hạn chế thao tác ghi để giữ an toàn. Nó là lựa chọn phù hợp khi API chỉ cần xem nội dung của một danh sách có kiểu chưa biết.

### Ép `Object` hoặc `Class` mà không xác minh

`Object` và raw `Class` không tự cung cấp thông tin về mọi đối số generic. Muốn khôi phục kiểu dữ liệu ở ranh giới, cần contract hoặc metadata/thao tác kiểm tra đáng tin cậy.

### Dùng `@SuppressWarnings` để tắt toàn bộ cảnh báo trong dự án

Suppression rộng có thể che các unchecked calls mới, khiến vấn đề khó truy vết. Giữ raw type trong module biên khi bất khả kháng, và cố gắng để phần lớn code dùng các kiểu tham số hóa.

### Quên rằng raw collection vẫn có thể không sửa được

`List raw = List.of("A")` là raw reference tới danh sách không hỗ trợ sửa. Dù compiler có thể cho phép `raw.add("B")` với unchecked warning, lời gọi vẫn có thể ném `UnsupportedOperationException`. Raw type không thay đổi các giới hạn về khả năng sửa của đối tượng thực.

## 13. Bài tập tự kiểm tra

**Bài 1 — Phân loại kiểu.** Trong các dạng `List`, `List<?>`, `List<Object>`, `List<String>`, dạng nào là raw type?

**Đáp án:** chỉ `List` không có đối số kiểu.

**Bài 2 — Dự đoán cảnh báo.** Với `List raw = new ArrayList(); raw.add(1);`, `-Xlint:rawtypes` và `-Xlint:unchecked` có ý nghĩa gì?

**Đáp án:** `rawtypes` phát hiện chỗ không cung cấp đối số generic; `unchecked` cảnh báo lời gọi `add(E)` qua raw reference không được kiểm tra như một lời gọi typed.

**Bài 3 — Phân tích lỗi muộn.**

```java
List<String> words = new ArrayList<>();
List raw = words;
raw.add(99);
String s = words.get(0);
```

Đoạn minh họa cần đặt trong method có import collection. Vì sao lỗi phát sinh ở dòng cuối, dù dòng `add` đã chạy?

**Đáp án:** raw `List` cho phép một giá trị `Integer` đi vào đối tượng danh sách với cảnh báo; tại `words.get(0)`, code typed kỳ vọng `String` và cast thất bại.

**Bài 4 — Chọn chữ ký.** Một method chỉ duyệt và in mọi phần tử của danh sách bất kể kiểu, nên nhận `List`, `List<Object>` hay `List<?>`?

**Đáp án:** `List<?>`, vì chấp nhận các dạng danh sách typed khác nhau và không cho phép tùy tiện thêm phần tử.

**Bài 5 — Phân biệt erasure.** Vì sao `List<String>` vẫn bị erasure mặc dù nó không phải raw type?

**Đáp án:** erasure là cơ chế biên dịch chung cho generics; raw type chỉ là kiểu tham chiếu ở Java source khi bỏ đối số generic.

**Bài 6 — Nâng cấp API cũ.** Thư viện cũ trả `List` có thể chứa cả `String` lẫn `Integer`. Bạn cần API trả `List<String>`. Có nên cast cả danh sách thành `List<String>`?

**Đáp án:** không thể coi unchecked cast đó là bằng chứng an toàn. Cần xác minh từng phần tử, từ chối phần tử sai, tạo collection typed mới, hoặc công bố đúng kiểu dữ liệu thực nếu không thể bảo đảm.

**Bài 7 — Raw type có còn bound không?** Với `NumberHolder<T extends Number>`, gọi `rawHolder.accept("hello")` có hợp lệ không?

**Đáp án:** không. Erasure của `T` là `Number`, nên `String` vẫn không phù hợp với chữ ký nhận `Number`.

## Kết luận thực hành

**Raw type là tên một lớp/interface generic được dùng mà không cung cấp type arguments**, như `List` hay `Box`. Nó tồn tại chủ yếu để các API và chương trình Java cũ có thể tiếp tục sống cùng generics được bổ sung từ Java 5. Cơ chế **type erasure** hỗ trợ tương thích binary, nhưng việc dùng raw type ở mã nguồn làm giảm kiểm tra an toàn kiểu và có thể mở đường cho **unchecked calls, heap pollution và `ClassCastException`**.

Khi viết Java hiện đại, hãy khai báo kiểu cụ thể như `List<String>` nếu biết dữ liệu; dùng `List<?>` nếu chỉ cần đọc một danh sách chưa biết kiểu; dùng `List<Object>` khi thật sự cần chứa nhiều loại đối tượng; và khoanh raw type ở ranh giới tích hợp với mã legacy. **Giữ cảnh báo compiler hữu ích, kiểm tra dữ liệu tại ranh giới và tránh unchecked cast tùy tiện** là cách tận dụng tương thích với mã cũ mà vẫn duy trì tính an toàn kiểu của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-002">Phân biệt raw type với kiểu thông thường không có tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt raw type với kiểu thông thường không có tham số kiểu trong Java

## 1. Hai khái niệm dễ nhầm

Trong Java, **raw type (kiểu thô)** là cách sử dụng một tên kiểu **đã được khai báo là generic** nhưng **bỏ qua danh sách tham số kiểu** ở vị trí sử dụng.

Ví dụ, thư viện Java khai báo interface `List<E>`. Khi viết:

~~~java
List<String> names = new ArrayList<>(); // Kiểu tham số hóa: List<String>.
List rawNames = new ArrayList();        // Raw type: List và ArrayList.
~~~

`List` và `ArrayList` trong dòng thứ hai là **raw types**, bởi chính các kiểu được khai báo với type parameter nhưng nơi sử dụng lại bỏ chúng.

Trái lại, **kiểu không generic (non-generic type)** được khai báo ngay từ đầu mà **không có type parameter**:

~~~java
final class Person {
    private final String name;

    Person(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }
}
~~~

Viết `Person person = new Person("Lan");` là dùng một kiểu thông thường, **không phải raw type**. Không thể viết `Person<String>` vì `Person` không được khai báo là kiểu generic.

**Kết luận nền tảng:** muốn nhận diện raw type, phải xem **định nghĩa của kiểu đó có khai báo type parameter hay không**, rồi mới xem vị trí sử dụng có bỏ type argument hay không. Không thể chỉ dựa vào việc một tên kiểu có dấu `<...>` xuất hiện trong dòng code hay không.

## 2. Thuật ngữ cần phân biệt chính xác

- **Type parameter**: biến kiểu trong khai báo, ví dụ `T` ở `class Box<T>` hoặc `E` ở `interface List<E>`.
- **Type argument**: đối số kiểu khi sử dụng một kiểu generic, ví dụ `String` trong `Box<String>` và `List<String>`.
- **Parameterized type**: kiểu generic đã được cung cấp type argument, ví dụ `List<String>`, `List<Object>`, `List<?>`.
- **Raw type**: tên generic thiếu danh sách type argument trong ngữ cảnh sử dụng thích hợp, ví dụ `List`, `Map`, `Box` khi các khai báo tương ứng là generic.
- **Non-generic type**: khai báo lớp, interface hoặc kiểu vốn không nhận type argument, ví dụ `String`, `Integer` hoặc một lớp `LegacyRecord` không có `<T>`.
- **Type erasure**: cơ chế biên dịch làm phần lớn thông tin tham số generic không còn là thành phần của JVM method descriptor. Đây là cơ chế triển khai, **không đồng nghĩa raw type**.

Nói cách khác: *raw* mô tả cách sử dụng một **generic type**; *non-generic* mô tả bản thân một khai báo kiểu **không generic**.

## 3. So sánh bằng hai lớp tự định nghĩa

Xét hai khai báo:

~~~java
// Không generic: không khai báo tham số kiểu nào.
class LegacyBox {
    private final Object value;

    LegacyBox(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}

// Generic: khai báo type parameter T.
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
~~~

Cách dùng:

~~~java
LegacyBox ordinary = new LegacyBox("Java");
Object fromOrdinary = ordinary.get();

Box<String> typed = new Box<>("Generics");
String fromTyped = typed.get();

Box raw = new Box("Raw");
Object fromRaw = raw.get();
~~~

| Khía cạnh | `LegacyBox` | `Box<String>` | `Box` |
| --- | --- | --- | --- |
| Kiểu gốc khai báo `<T>`? | Không | Có | Có |
| Cách dùng là raw type? | Không | Không | Có |
| `get()` nhìn từ source trả gì? | Object theo khai báo | String nhờ T = String | Object sau erasure/bound mặc định |
| Có thể viết `<String>` cho kiểu này? | Không | Đã viết | Có thể sửa thành `Box<String>` |
| Ý nghĩa | API vốn không generic | API có thông tin kiểu cụ thể | API generic bị bỏ type arguments |
| Nguy cơ cảnh báo unchecked vì raw? | Không do bản thân kiểu này | Không nếu sử dụng đúng | Có thể có khi gọi thành viên nhận T hoặc chuyển sang dạng parameterized |

Cả `LegacyBox` và raw `Box` đều có thể cho ra Object khi gọi get, nhưng **lý do hoàn toàn khác nhau**. Với `LegacyBox`, phương thức được tác giả khai báo trả Object. Với raw `Box`, phương thức ban đầu trả T nhưng việc sử dụng raw làm mất phần đảm bảo kiểu tham số hóa tại compile time.

Không nên kết luận một kiểu là raw chỉ vì có phương thức trả Object. Rất nhiều lớp không generic có các API chủ ý dùng Object.

### Không phải “không thấy dấu <>” là raw

~~~java
String text = "hello";         // String không generic.
Integer number = 17;          // Integer không generic.
Runnable action = () -> {};   // Runnable không generic.
Object obj = new Object();    // Object không generic.
~~~

Các lớp/interface trên không khai báo type parameter, nên chúng là **non-generic types**. Không có type argument nào bị bỏ sót cả.

Lệnh `new ArrayList<>()` lại thuộc một trường hợp khác: **diamond operator** yêu cầu compiler suy luận các type arguments cho một kiểu generic. Nó không tương đương `new ArrayList()` dùng raw constructor type.

## 4. Ví dụ chạy đầy đủ trên Java 17+

Lưu toàn bộ chương trình sau vào **RawVsOrdinaryDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class RawVsOrdinaryDemo {
    static final class NonGenericBox {
        private final Object value;

        NonGenericBox(Object value) {
            this.value = value;
        }

        Object get() {
            return value;
        }
    }

    static final class GenericBox<T> {
        private final T value;

        GenericBox(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        NonGenericBox oldStyle = new NonGenericBox("plain");
        System.out.println("plain=" + oldStyle.get());

        GenericBox<String> typed = new GenericBox<>("typed");
        String text = typed.get();
        System.out.println("typed=" + text);

        // Bỏ tham số kiểu của GenericBox<T>: sử dụng raw type.
        GenericBox rawBox = new GenericBox("raw");
        Object fromRaw = rawBox.get();
        System.out.println("rawBox=" + fromRaw);

        List<String> words = new ArrayList<>();
        words.add("Java");

        // Raw List và List<String> cùng trỏ đến một ArrayList.
        List raw = words;
        raw.add(42);
        System.out.println("rawList=" + raw);

        Object first = words.get(0);
        System.out.println("first=" + first);

        try {
            String second = words.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("typedRead=ClassCastException");
        }

        // Wildcard vẫn là một parameterized type, không phải raw.
        List<?> unknown = words;
        System.out.println("wildcardSize=" + unknown.size());

        // Generics thông thường được triển khai bằng type erasure.
        System.out.println(
                "sameRuntimeClass=" +
                (typed.getClass() == rawBox.getClass())
        );
    }
}
~~~

Biên dịch để thấy các cảnh báo tại đúng chỗ dùng raw:

~~~bash
javac -Xlint:rawtypes -Xlint:unchecked RawVsOrdinaryDemo.java
java RawVsOrdinaryDemo
~~~

Compiler có thể cảnh báo **rawtypes** tại các vị trí khai báo/khởi tạo `GenericBox` và `List` không có type arguments; cảnh báo **unchecked** xuất hiện ở các lời gọi có khả năng không an toàn như `raw.add(42)` hoặc constructor raw. Các cảnh báo biên dịch không thuộc stdout của chương trình.

Stdout của chương trình:

~~~text
plain=plain
typed=typed
rawBox=raw
rawList=[Java, 42]
first=Java
typedRead=ClassCastException
wildcardSize=2
sameRuntimeClass=true
~~~

Giải thích:

1. `NonGenericBox` dùng Object theo thiết kế gốc; không có raw generic nào trong kiểu này.
2. `GenericBox<String>` có thông tin kiểu String tại compile time, nên compiler kiểm tra `typed.get()` có thể dùng như String.
3. `GenericBox rawBox` bỏ type argument của `GenericBox<T>`; `get()` được nhìn theo chữ ký đã xóa, ở ví dụ này là Object.
4. `List raw = words` tạo **một tham chiếu raw** tới chính danh sách `List<String>` đang tồn tại; không tạo bản sao và không chuyển đổi phần tử.
5. `raw.add(42)` lọt qua kiểm tra generic theo đường raw kèm unchecked warning. Vì ArrayList chứa Integer ở vị trí thứ hai, lời gọi `words.get(1)` dùng kết quả như String sẽ ném ClassCastException.
6. `List<?> unknown` vẫn có type argument là wildcard `?`, nên **không phải raw**.
7. `typed.getClass() == rawBox.getClass()` cho `true`: raw và parameterized sử dụng cùng generic class ở runtime theo type erasure thông thường. Điều này **không** làm kiểu compile-time của các biến giống nhau.

Đoạn minh họa cố ý sử dụng raw type để giải thích rủi ro; trong code ứng dụng nên sửa nguồn raw thay vì bắt ClassCastException như một giải pháp dài hạn.

## 5. Raw type không phải List<Object>

Một lỗi quan trọng là cho rằng các khai báo sau tương đương:

~~~java
List raw;              // Raw type.
List<Object> objects;  // Parameterized type với E = Object.
List<?> unknown;       // Parameterized type với unbounded wildcard.
List<String> names;    // Parameterized type với E = String.
~~~

Chúng không tương đương.

### List<Object> vẫn được kiểm tra generic

~~~java
List<Object> objects = new ArrayList<>();
objects.add("Java");
objects.add(42);
Object value = objects.get(0);
~~~

`List<Object>` là một generic type với tham số kiểu xác định Object. Nó cho phép thêm String/Integer vì chúng đều là subtype của Object, nhưng **compiler vẫn kiểm tra theo E = Object**.

Trong khi đó, raw `List` bỏ thông tin E khi dùng các thành viên bị ảnh hưởng bởi erasure, tạo khả năng chuyển dữ liệu sai kiểu vào các collection vốn được sử dụng ở nơi khác với tham số cụ thể.

### List<?> giữ sự không chắc chắn về kiểu phần tử

~~~java
List<String> names = new ArrayList<>();
names.add("Java");
List<?> unknown = names;

Object value = unknown.get(0);
// unknown.add("Kotlin"); // Lỗi biên dịch.
~~~

`List<?>` là parameterized type: nó mô tả một `List` có kiểu phần tử nào đó chưa biết. Compiler không cho thêm một String tùy ý vì giá trị đó có thể không tương thích với kiểu phần tử thực. Có thể thêm `null` theo quy tắc chung của wildcard, nhưng collection cụ thể có thể không hỗ trợ thao tác sửa đổi.

Ngược lại:

~~~java
List raw = names;
raw.add(42); // Unchecked warning: có thể phá giả định của List<String>.
~~~

**List<?> thường là lựa chọn phù hợp khi chỉ cần đọc phần tử dưới dạng Object mà không biết kiểu cụ thể**, đặc biệt ở API chỉ đọc/quan sát danh sách.

### List<Object> không phải supertype của List<String>

Mặc dù String extends Object, Java generics nhìn chung **invariant**:

~~~java
List<String> names = new ArrayList<>();
// List<Object> wrong = names; // Không biên dịch.
List<?> acceptable = names;    // Hợp lệ.
List rawButUnsafe = names;     // Hợp lệ, raw usage.
~~~

Nếu `List<Object>` nhận được một `List<String>` rồi thêm Integer, tính đúng kiểu sẽ bị phá vỡ. Wildcard đại diện sự chưa biết một cách an toàn hơn; raw type chỉ tồn tại để phục vụ tương thích với mã cũ và các tình huống rất đặc thù.

## 6. Raw type khác với kiểu generic dùng diamond

Xem:

~~~java
List<String> a = new ArrayList<>();
var b = new ArrayList<String>();
var c = new ArrayList<>();
List raw = new ArrayList();
~~~

- `a` có kiểu tĩnh List<String>; biểu thức `new ArrayList<>()` dùng diamond và ngữ cảnh List<String> để suy luận E = String.
- `b` có kiểu ArrayList<String> được suy luận từ initializer ghi rõ String.
- `c` có kiểu ArrayList<Object> trong trường hợp constructor rỗng, không có thông tin khác để suy ra E; **đây vẫn là parameterized type**, không phải raw.
- `raw` dùng raw type List và raw construction ArrayList, thực sự bỏ qua các type arguments ở vị trí sử dụng.

Do đó, **không thể suy ra rằng dùng var cùng diamond là raw**. Ngay cả khi trình biên dịch suy luận E thành Object, `ArrayList<Object>` vẫn là generic có tham số kiểu.

Thêm một ví dụ:

~~~java
var inferred = new ArrayList<>(List.of("Java", "Kotlin"));
// ArrayList<String>, vì constructor nhận dữ liệu String.
~~~

Thông tin type argument có thể đến từ target type, các đối số constructor hoặc vị trí suy luận phù hợp. Raw type là **sự bỏ type arguments của generic type**, không phải bất kỳ trường hợp nào trình biên dịch tự suy luận kiểu.

## 7. Generic method không có nghĩa class là generic

Không nên đánh đồng hai khái niệm:

~~~java
final class Utilities {
    static <T> T same(T value) {
        return value;
    }
}
~~~

Lớp `Utilities` **không generic** vì bản thân class không có `<T>`. Nhưng phương thức `same` là một **generic method** khai báo tham số kiểu riêng.

Dùng:

~~~java
String result = Utilities.same("Java");
Integer number = Utilities.same(17);
~~~

Điều này bình thường và không sử dụng raw type. Không có cú pháp `Utilities<String>` vì Utilities không nhận tham số kiểu ở cấp class.

Một phương thức trả Object mà không generic:

~~~java
final class PlainFactory {
    static Object create() {
        return "Java";
    }
}
~~~

`PlainFactory` cũng là non-generic. Việc caller muốn ép một Object thành String là một tình huống kiểm tra/cast thông thường, không tự động là vấn đề raw type.

**Câu hỏi nhận diện:** dấu `<T>` nằm ở **khai báo class/interface** hay chỉ ở **khai báo phương thức**? Raw type được xác định dựa vào kiểu generic, không dựa vào việc class có một phương thức generic độc lập.

## 8. Một lớp con non-generic vẫn có thể kế thừa generic class

Ví dụ:

~~~java
class Base<T> {
    T value;

    Base(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

// FixedStringBox không khai báo tham số kiểu.
final class FixedStringBox extends Base<String> {
    FixedStringBox(String value) {
        super(value);
    }
}
~~~

`FixedStringBox` là **non-generic class**: không có danh sách tham số kiểu trong khai báo của chính nó. Tuy nhiên nó kế thừa `Base<String>`, nên:

~~~java
FixedStringBox box = new FixedStringBox("Java");
String text = box.get(); // Kiểu trả về được chuyên biệt là String.
~~~

Việc sử dụng `FixedStringBox` **không phải raw**, và compiler vẫn kiểm tra các thành viên đã được chuyên biệt hóa từ Base<String>.

Nếu thay superclass bằng dạng `extends Base` không có String, đó mới là **raw use of Base** tại mệnh đề kế thừa. Lớp con tự nó vẫn có thể không generic, nhưng việc kế thừa raw superclass làm mất thông tin kiểu của các thành viên generic liên quan.

Đây là phản ví dụ quan trọng đối với ngộ nhận “lớp nào từng đụng đến generic thì bắt buộc phải ghi `<...>` ở tên của chính nó”.

## 9. Cảnh báo rawtypes và unchecked có phải cùng một lỗi?

Hai nhóm cảnh báo thường đi cùng nhau nhưng không đồng nhất:

- **rawtypes**: bạn đã dùng một tên generic ở dạng raw, ví dụ `List raw;`.
- **unchecked**: compiler phát hiện thao tác liên quan đến generic mà khả năng an toàn kiểu không được chứng minh đầy đủ, ví dụ gọi `raw.add(42)` hoặc chuyển raw List sang List<String>.

Ví dụ cố ý gây cảnh báo:

~~~java
List raw = new ArrayList();
raw.add("Java");
List<String> strings = raw; // Unchecked conversion.
~~~

Lệnh gán raw List vào List<String> có thể được compiler chấp nhận kèm cảnh báo vì cơ chế tương thích với mã cũ. Nó **không kiểm tra mọi phần tử của danh sách ở runtime**. Nếu raw List đã chứa đối tượng không phải String thì lỗi có thể xuất hiện muộn khi lấy dữ liệu ra như String.

Đối với non-generic class:

~~~java
final class Plain {
    Object read() {
        return 42;
    }
}
~~~

Viết `Plain p = new Plain(); Object n = p.read();` không có lý do phát cảnh báo rawtypes hoặc unchecked chỉ vì Plain không dùng generic. Nếu ép `(String) p.read()`, đó là cast runtime có thể thất bại, một vấn đề riêng.

### Dùng đúng tùy chọn javac

~~~bash
javac -Xlint:rawtypes -Xlint:unchecked YourFile.java
~~~

Lệnh trên yêu cầu javac đưa ra cảnh báo liên quan raw và unchecked. Số dòng và định dạng chẩn đoán thay đổi theo JDK và mã nguồn; không nên viết logic dựa trên chính xác từng dòng warning.

**Không nên ẩn hết cảnh báo** bằng `@SuppressWarnings({"rawtypes", "unchecked"})` trên cả file. Nếu buộc dùng API cũ, hãy giới hạn đoạn raw càng nhỏ càng tốt, kiểm tra dữ liệu trước khi chuyển sang kiểu tham số hóa, và chú thích giả định đã kiểm chứng.

## 10. Vì sao raw type xuất hiện trong Java?

Generics được thêm vào Java 5. Nhiều thư viện và ứng dụng đã có interface hoặc class dùng collection trước đó. Để hỗ trợ biên dịch và tương thích nhị phân với mã cũ, Java cho phép tên generic được dùng ở dạng raw.

~~~java
// Phong cách tiền generics:
List legacy = new ArrayList();

// Phong cách hiện đại:
List<String> modern = new ArrayList<>();
~~~

Raw type là một **cơ chế tương thích ngược có chủ đích**, không phải cú pháp bắt buộc cho mọi chương trình Java mới.

Điều này cũng giải thích vì sao trong bytecode generics phần lớn dùng type erasure. Nhưng hãy phân biệt: **type erasure xảy ra cả khi dùng generic đúng cách** như `List<String>`. Không thể nhìn vào bytecode descriptor trả Object rồi kết luận mã nguồn đã sử dụng raw type.

### Dùng cùng một runtime class không làm hai kiểu compile-time bằng nhau

Ví dụ trong chương trình chạy:

~~~java
GenericBox<String> typed = new GenericBox<>("A");
GenericBox raw = new GenericBox("B");
boolean sameClass = typed.getClass() == raw.getClass();
~~~

`sameClass` là true với generic class thông thường do erasure. Tuy nhiên biến typed vẫn được compiler kiểm tra như `GenericBox<String>`, còn raw bị mất thông tin tham số trong nhiều thao tác. Đây là **khác biệt compile-time** có hậu quả thực tế, dù `getClass()` trùng nhau.

## 11. Những trường hợp dễ gây hiểu lầm

### Trường hợp A: Class và Class<?> không giống nhau

`java.lang.Class<T>` là một generic class:

~~~java
Class rawClass = String.class; // Raw type.
Class<?> anyClass = String.class; // Parameterized type với wildcard.
Class<String> stringClass = String.class; // Parameterized type cụ thể.
~~~

`Class<?>` thường phù hợp khi xử lý metadata về một lớp chưa biết tham số kiểu. `Class` raw dễ làm mất kiểm tra kiểu liên quan. Việc `String.class` có kiểu `Class<String>` không biến `String` thành generic type.

### Trường hợp B: Mảng không phải raw generic

~~~java
String[] words = {"A", "B"};
int[] numbers = {1, 2};
~~~

Array types không có type parameter theo cách `List<E>` có. Dùng mảng String[] hay int[] không phải raw type. Mảng có cơ chế kiểm tra kiểu runtime khác generic; không nên đồng nhất hai cơ chế.

### Trường hợp C: Một non-generic interface có thể là functional interface

~~~java
Runnable task = () -> System.out.println("run");
task.run();
~~~

`Runnable` không có type parameter, nên không phải raw. Sự có mặt của lambda cũng không làm interface đó generic.

### Trường hợp D: Danh sách mất String vì suy luận Object, nhưng vẫn không raw

~~~java
var values = new ArrayList<>();
values.add("Java");
values.add(42);
~~~

`values` được suy luận là ArrayList<Object> trong trường hợp này. Đây là một parameterized type có E = Object. Việc add cả String lẫn Integer được cho phép **theo kiểm tra generic đúng kiểu Object**, không phải vì raw usage.

### Trường hợp E: Non-generic lớp con của generic superclass

~~~java
final class StringList extends ArrayList<String> {}
StringList list = new StringList();
list.add("Java");
// list.add(42); // Compiler từ chối.
~~~

`StringList` không có `<T>` ở khai báo của nó, nên dùng StringList không phải raw. Lớp này kế thừa ArrayList<String>, vì vậy phương thức add được chuyên biệt hóa thành add(String) ở mức kiểm tra Java source.

### Trường hợp F: Raw type không xóa hết mọi cấu trúc kiểu của chương trình

Câu “dùng raw thì mọi chỗ trở thành Object” là quá tổng quát. Các thành viên không dùng type parameter có thể giữ nguyên kiểu tường minh của chúng, và erasure của type parameter bị giới hạn như `T extends Number` là Number thay vì Object. Cần xem **chữ ký cụ thể sau erasure**, không mặc định mọi method/field đều có kiểu Object.

## 12. Cách sửa mã raw type khi gặp trong dự án

Khi nhìn thấy:

~~~java
List items = loadItems();
~~~

đừng vội thay bằng `List<String>` nếu chưa biết hợp đồng thực tế của dữ liệu. Có ba hướng xử lý:

**Trường hợp đã biết mọi phần tử đúng một kiểu cụ thể:** khai báo `List<String>`, chỉnh signature của `loadItems` và các điểm tạo dữ liệu để compiler kiểm tra kiểu từ đầu đến cuối.

**Trường hợp chỉ cần đọc nhưng kiểu phần tử chưa biết:** dùng `List<?>`, duyệt các phần tử như Object, kiểm tra kiểu tại nơi cần xử lý.

**Trường hợp nhận dữ liệu từ API cũ hoặc mã không đáng tin:** kiểm tra dữ liệu trước khi chuyển thành một collection có kiểu rõ ràng:

~~~java
static List<String> validatedCopy(List<?> source) {
    List<String> result = new ArrayList<>();

    for (Object element : source) {
        if (!(element instanceof String text)) {
            throw new IllegalArgumentException("Only String is allowed");
        }
        result.add(text);
    }

    return result;
}
~~~

Mã trên hoạt động với Java 17+, dùng pattern matching cho instanceof và tạo danh sách mới chứa phần tử đã xác nhận là String. Nó từ chối null; nếu nghiệp vụ cho phép null, cần thiết kế một chính sách riêng.

Đây là hướng chuyển đổi an toàn hơn so với:

~~~java
@SuppressWarnings("unchecked")
List<String> assumed = (List<String>) raw;
~~~

Phép cast từ raw List sang List<String> không xác nhận kiểu của từng phần tử, nên có thể khiến lỗi xuất hiện muộn khi đọc.

**Quy trình nâng cấp code cũ:**

1. Xác định declaration gốc: type đang dùng generic hay non-generic?
2. Với raw generic, tìm type argument đúng từ miền nghiệp vụ thay vì chọn Object một cách ngẫu nhiên.
3. Dùng `?` nếu type argument thực sự chưa biết và API không cần thêm phần tử.
4. Tìm các unchecked warnings, đường ghi dữ liệu và raw aliases có thể gây heap pollution.
5. Thay đổi từng API boundary có kiểm soát và kiểm tra việc đọc/ghi collection.
6. Với non-generic type phù hợp thiết kế, không cần “generic hóa” chỉ để loại cảnh báo vốn không tồn tại.

## 13. Bảng nhận diện nhanh

| Cú pháp | Khai báo kiểu gốc | Phân loại |
| --- | --- | --- |
| `String s` | String không có type parameter | Non-generic type |
| `Runnable r` | Runnable không có type parameter | Non-generic type |
| `LegacyBox x` | LegacyBox được khai báo không generic | Non-generic type |
| `List list` | List<E> có E nhưng bị bỏ tại nơi dùng | Raw type |
| `ArrayList list` | ArrayList<E> có E nhưng bị bỏ | Raw type |
| `List<Object> list` | List<E> với E = Object | Parameterized type |
| `List<?> list` | List<E> với wildcard type argument | Parameterized type |
| `var list = new ArrayList<>()` | Diamond suy luận ArrayList<Object> khi không có ràng buộc khác | Parameterized type, không raw |
| `class Fixed extends Base<String>` | Fixed không có type parameter, Base có | Fixed là non-generic class dùng parameterized superclass |
| `Class clazz` | Class<T> bị bỏ T | Raw type |
| `Class<?> clazz` | Class<T> với wildcard | Parameterized type |
| `String[] array` | Kiểu mảng của String | Array type, không raw |

Phân loại phải căn cứ **khai báo gốc và vị trí sử dụng**, không chỉ hình thức bên ngoài. Đặc biệt, `Object` xuất hiện sau erasure không đủ để kết luận một đoạn mã dùng raw type.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** `class A {}` rồi `A a = new A();` có phải raw type không?

**Đáp án:** Không. A không khai báo type parameter nên vốn là non-generic type. Không có tham số generic nào bị bỏ.

**Câu 2.** `List<?> a = new ArrayList<String>();` có phải raw type không?

**Đáp án:** Biến a có kiểu parameterized `List<?>`, không raw. Tuy nhiên biểu thức `new ArrayList<String>()` cũng là parameterized constructor type; cả hai sử dụng generic, dù wildcard không tiết lộ phần tử cụ thể.

**Câu 3.** `var a = new ArrayList<>();` suy luận ArrayList<Object>. Có nên gọi a là raw type không?

**Đáp án:** Không. Object là type argument cụ thể do compiler suy luận; a vẫn thuộc kiểu `ArrayList<Object>`.

**Câu 4.** Một `class StringBox extends Box<String>` có phải generic class không?

**Đáp án:** Không nếu StringBox không tự khai báo type parameter. Nhưng nó kế thừa một parameterized superclass nên các phương thức generic được chuyên biệt hóa phù hợp với String.

**Câu 5.** Tại sao `List<Object>` không thể nhận một `List<String>` nhưng raw `List` có thể được gán từ `List<String>`?

**Đáp án:** Java generics invariant để tránh ghi phần tử sai kiểu. Raw List tồn tại để tương thích mã cũ và bỏ kiểm tra type argument tại những thao tác liên quan, nên cho phép alias có rủi ro.

**Câu 6.** `Class<String>`, `Class<?>` và `Class` phân biệt thế nào?

**Đáp án:** Hai dạng đầu là parameterized types (cụ thể và wildcard); dạng cuối là raw use của generic class `Class<T>`.

**Câu 7.** Vì sao một lớp non-generic trả Object không tự động là nguồn unchecked warning?

**Đáp án:** Trả Object là kiểu API được khai báo trực tiếp. Cảnh báo unchecked liên quan các thao tác generic không thể chứng minh an toàn, chẳng hạn raw conversion/call; non-generic class tự nó không có type argument bị bỏ.

## 15. Điều cần ghi nhớ

**Raw type = dùng một kiểu vốn generic mà bỏ type arguments. Non-generic type = khai báo kiểu vốn không có type parameters.** Hai trường hợp có thể cùng chứa các method trả Object nhưng có bản chất khác nhau.

Trong Java hiện đại, hãy ưu tiên `List<String>` khi biết chính xác kiểu phần tử, `List<?>` khi chỉ cần xử lý một danh sách chưa biết kiểu phần tử, và chỉ dùng raw generic ở phạm vi nhỏ khi tích hợp legacy code có lý do rõ ràng. Compiler và `-Xlint:rawtypes -Xlint:unchecked` giúp chỉ ra nơi thông tin kiểu bị bỏ qua, còn việc kiểm tra dữ liệu thật sự là cần thiết khi đi qua các ranh giới không an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-003">Chuyển từ kiểu tham số hóa sang raw type</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển từ kiểu tham số hóa sang raw type trong Java Generics

Một **kiểu tham số hóa (parameterized type)** cung cấp thông tin đối số kiểu, chẳng hạn `List<String>`, `Map<String, Integer>` hay `Box<Customer>`. **Raw type (kiểu thô)** là cách dùng tên một lớp hoặc interface generic mà không viết các đối số kiểu tương ứng: `List`, `Map` hoặc `Box`.

Khi viết `List raw = typed;` với `typed` có kiểu `List<String>`, Java cho phép phép gán này để tương tác với mã cũ. Tuy nhiên, kể từ điểm sử dụng biến `raw`, trình biên dịch **không còn áp dụng hợp đồng phần tử `String` khi gọi những thao tác thông qua biến raw**. Sự chuyển đổi đó đặc biệt nguy hiểm nếu mã khác thêm phần tử không tương thích vào cùng collection.

Bài học quan trọng nhất: **chuyển từ kiểu tham số hóa sang raw type không tạo danh sách mới, không xóa các phần tử và không kiểm tra hoặc đổi kiểu dữ liệu đang lưu**. Nó thay đổi **thông tin kiểu mà trình biên dịch sử dụng đối với tham chiếu**, trong khi hai biến vẫn có thể cùng trỏ đến một đối tượng runtime.

## 1. Định nghĩa và cú pháp

Ví dụ với thư viện collections:

```java
import java.util.ArrayList;
import java.util.List;

// Mã minh họa; đặt trong phương thức hoặc chương trình Java hợp lệ.
List<String> names = new ArrayList<>();
names.add("An");

List raw = names; // Chuyển từ List<String> sang raw List
```

Ở đây:

- `List<String>`: biết rằng các phương thức generic của `List<E>` đang được dùng với `E = String`.
- `List`: raw type của interface `List<E>`; việc gọi các phương thức chịu tác động của erasure và các quy tắc dùng raw type.
- `raw = names`: gán cùng một **tham chiếu** sang biến có kiểu tĩnh ít thông tin hơn; không hề sao chép collection.
- `new ArrayList<>()`: tạo đối tượng `ArrayList` như bình thường; cú pháp diamond `<>` để compiler suy luận đối số kiểu tại nguồn, không tạo một class runtime `ArrayList<String>` mới.

Raw type được Java duy trì chủ yếu để hỗ trợ **migration compatibility** với API và mã nguồn cũ có trước generics. Trong code hiện đại, hãy khai báo kiểu tham số hóa hoặc wildcard khi có thể.

### Phân biệt raw type với các kiểu gần giống

```java
// Từng dòng là một cách khai báo tham chiếu khác nhau:
List<String> exact;     // Phần tử String
List<?> unknown;        // List của một kiểu phần tử chưa biết
List<Object> objects;   // List có E = Object
List raw;               // Raw List, bỏ kiểm tra generic tại điểm dùng raw
```

`List<?>` **không phải** raw type; nó là kiểu tham số hóa với wildcard, vẫn được trình biên dịch kiểm tra an toàn theo những khả năng có thể chứng minh. `List<Object>` cũng không tương đương `List<String>`: generic Java bất biến, nên không thể gán `List<String>` trực tiếp vào `List<Object>`.

## 2. Chiều chuyển đổi `List<String>` → `List`: hợp lệ nhưng mất bảo đảm ở bên nhận

Xét ví dụ:

```java
List<String> names = new ArrayList<>();
names.add("An");

List raw = names;  // Hợp lệ
Object item = raw.get(0);
raw.add(42);       // Hợp lệ về cú pháp, có unchecked warning
```

Theo kiểu tĩnh `List<String>`, `names.add(42)` **không biên dịch** vì `42` được boxing thành `Integer` chứ không phải `String`. Nhưng `raw.add(42)` vẫn biên dịch: sử dụng raw `List` khiến đối số của phương thức `add(E)` được xét theo kiểu đã xóa là `Object`.

Như vậy, thao tác nguy hiểm là **dùng tham chiếu raw để ghi phần tử**, chứ bản thân lệnh `List raw = names;` chưa làm hỏng dữ liệu.

Với `javac -Xlint:rawtypes -Xlint:unchecked`, bạn nên kiểm tra hai loại chẩn đoán:

- **`rawtypes`**: nơi dùng kiểu generic mà bỏ đối số kiểu, ví dụ khai báo `List raw`.
- **`unchecked`**: thao tác không được compiler bảo đảm an toàn generic, ví dụ `raw.add(42)`.

Các cảnh báo có thể khác nhau về nội dung trình bày theo JDK và lựa chọn compiler. Không nên đồng nhất việc một phép gán sang raw được phép với việc mọi thao tác tiếp theo qua raw đều an toàn.

### Không tạo bản sao và không có “lớp raw” riêng

Với cùng một đối tượng:

```java
// Snippet minh họa cùng phương thức:
List<String> typed = new ArrayList<>();
List raw = typed;
System.out.println(typed == raw); // true
```

Hai biến cùng tham chiếu tới **một** collection. Java generics thông thường sử dụng type erasure, nên `List<String>` và raw `List` là hai cách nhìn qua hệ thống kiểu tại nguồn; chúng không đại diện cho hai bản sao dữ liệu.

Nếu `raw.add(123)`, `typed` có thể đọc thấy phần tử `Integer` qua cùng collection. Tại thời điểm đọc theo `String`, compiler có thể chèn ép kiểu runtime và phát sinh `ClassCastException`.

## 3. Nhìn trước chiều ngược: raw → parameterized không tự kiểm tra dữ liệu

Chiều đang học là **`List<String>` → raw `List`**: hợp lệ, nhưng phần mã dùng biến raw mất ràng buộc phần tử `String`. Chiều ngược **raw `List` → `List<String>`** cũng thường được compiler cho phép để tương thích mã cũ, nhưng nó phát **unchecked conversion warning**:

```java
List<String> typed = new ArrayList<>();
List raw = typed;              // Chuyển sang raw
raw.add(42);                   // Unchecked call, có thể làm hỏng dữ liệu
List<String> recovered = raw; // Unchecked conversion, KHÔNG xác thực String
```

Đây là snippet trong method, cần import `java.util.List` và `java.util.ArrayList`. Không có bản sao hay việc quét từng phần tử trong các phép gán này; `recovered` có thể nhìn thấy phần tử `Integer`.

| Chiều | Bằng chứng compiler còn thiếu |
|---|---|
| Typed → raw | Các lời gọi qua receiver raw không còn được kiểm tra theo argument cũ |
| Raw → typed | Không chứng minh được dữ liệu thực phù hợp type argument mới |

Nếu chỉ cần duyệt mà chưa biết element type, dùng `List<?>`. Nếu cần trả `List<String>` đáng tin cậy, kiểm tra từng phần tử và quản lý alias; riêng **unchecked cast/conversion** không giải quyết được. Phần quan trọng của bài này vẫn là tác động của **typed → raw** tới các thao tác tiếp theo.

## 4. Heap pollution: mã biên dịch được nhưng dữ liệu trái với kiểu khai báo

```java
// Snippet cố ý tạo dữ liệu sai kiểu:
List<String> names = new ArrayList<>();
names.add("An");

List raw = names;
raw.add(42);                // Lẫn Integer vào List<String>
String first = names.get(1); // Có thể ném ClassCastException
```

Tình huống một biến kiểu parameterized trỏ đến dữ liệu không còn thỏa hợp đồng kiểu mà nó tuyên bố được gọi là **heap pollution**. Trong ví dụ, heap pollution xảy ra sau khi thêm `Integer` qua raw `List`.

Tại sao lỗi thường xuất hiện **khi đọc**, thay vì ngay lúc thêm?

1. `raw.add(42)` được kiểm tra theo erasure của `List.add(E)`, có tham số tương ứng `Object`, nên runtime collection cho phép thêm `Integer`.
2. Biến `names` vẫn được compiler nhìn như `List<String>`. Khi biểu thức `names.get(1)` phải tạo kết quả kiểu `String`, bytecode có thể có `checkcast java/lang/String`.
3. `Integer` không thể cast sang `String`, nên xuất hiện `ClassCastException`.

Không phải mọi lần đọc raw đều ném lỗi. Ví dụ `Object x = names.get(1)` có thể nhận `Integer` như một `Object`; lỗi phụ thuộc thao tác cần kiểm tra kiểu cụ thể tại runtime.

Đây là lý do một lời cảnh báo unchecked đáng xem xét ngay cả khi chương trình chạy “bình thường” trong vài trường hợp thử.

## 5. Type erasure liên quan gì tới chuyển đổi sang raw?

Java generics sử dụng **xóa kiểu (type erasure)**: type parameter không có bound được xóa thành `Object`, còn type parameter có bound được xóa thành bound đầu tiên. Ví dụ, khi dùng `List<E>`, `E` có erasure là `Object`, vì thế các thao tác generic được nhìn qua raw receiver có thể nhận/trả `Object`.

Tuy nhiên, **không phải mọi raw generic method đều trả `Object`**. Xét:

```java
// Snippet minh họa chữ ký, không phải chương trình độc lập.
class NumericBox<N extends Number> {
    private N value;

    NumericBox(N value) {
        this.value = value;
    }

    N get() {
        return value;
    }

    void set(N value) {
        this.value = value;
    }
}
```

Khi dùng raw `NumericBox`, `N` có erasure là `Number`. Vì vậy `rawBox.get()` có kiểu trả về `Number` khi quan sát theo kiểu đã xóa; `rawBox.set(1.5)` có thể thực hiện với `Double` vì phương thức nhận `Number`, dù không còn ràng buộc một đối số kiểu cụ thể.

```java
// Snippet tiếp tục ví dụ NumericBox:
NumericBox<Integer> integers = new NumericBox<>(10);
NumericBox rawBox = integers;
Number current = rawBox.get(); // Hợp lệ; không phải mặc định Object
rawBox.set(1.5);               // Unchecked call; có thể gây heap pollution
// Integer later = integers.get(); // Có thể ném ClassCastException
```

Vì vậy, **raw type là cách sử dụng khai báo generic khi bỏ type argument ở nguồn**; **erasure là cơ chế biên dịch** giải thích vì sao những chữ ký generic ấy có thể được dùng ở dạng đã xóa. Hai khái niệm liên quan nhưng không hoàn toàn đồng nghĩa.

## 6. Chương trình Java 17+ thực thi đầy đủ

Lưu tệp **`RawTypeConversionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RawTypeConversionDemo {
    static List<String> checkedCopy(List<?> values) {
        List<String> result = new ArrayList<>();

        for (Object value : values) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException(
                        "Phan tu khong phai String");
            }
            result.add(text);
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("An", "Binh"));

        // Chuyển từ parameterized type sang raw type:
        List raw = names;
        System.out.println("Cung doi tuong: " + (raw == names));
        System.out.println("Doc qua raw: " + raw.get(0));

        // Thao tác sai kiểu qua raw receiver (unchecked):
        raw.add(42);
        System.out.println("Sau raw.add: " + names);

        try {
            String third = names.get(2);
            System.out.println(third);
        } catch (ClassCastException exception) {
            System.out.println("Doc sai kieu: ClassCastException");
        }

        // Chuyển ngược từ raw sang parameterized type (unchecked):
        List<String> uncheckedBack = raw;
        System.out.println("Gan raw ve List<String> cung doi tuong: "
                + (uncheckedBack == names));

        // Đọc qua wildcard nhưng không xác nhận phần tử là String:
        List<?> readOnlyView = names;
        System.out.println("Qua List<?>: " + readOnlyView.get(0));

        try {
            checkedCopy(readOnlyView);
        } catch (IllegalArgumentException exception) {
            System.out.println("Kiem tra du lieu: tu choi Integer");
        }

        List<String> clean = checkedCopy(List.of("Lan", "Mai"));
        System.out.println("Ban sao an toan: " + clean);
    }
}
```

Dùng JDK 17 trở lên:

```bash
javac -encoding UTF-8 -Xlint:rawtypes -Xlint:unchecked RawTypeConversionDemo.java
java RawTypeConversionDemo
```

**Standard output** của chương trình (không bao gồm cảnh báo do compiler in khi biên dịch):

```text
Cung doi tuong: true
Doc qua raw: An
Sau raw.add: [An, Binh, 42]
Doc sai kieu: ClassCastException
Gan raw ve List<String> cung doi tuong: true
Qua List<?>: An
Kiem tra du lieu: tu choi Integer
Ban sao an toan: [Lan, Mai]
```

Ý nghĩa từng nhóm:

- `Cung doi tuong: true`: chuyển sang raw chỉ làm đổi **kiểu tĩnh của biến**, không clone danh sách.
- `raw.add(42)`: là ví dụ cố ý gây heap pollution để nhìn thấy hậu quả; không dùng trong code nghiệp vụ.
- `ClassCastException`: xuất hiện khi kết quả lấy từ danh sách phải được xem là `String` nhưng giá trị thực là `Integer`.
- `List<String> uncheckedBack = raw`: compiler có thể cảnh báo dù runtime vẫn chỉ có cùng một danh sách. Việc không ném lỗi ngay lúc gán **không bảo đảm** mọi phần tử đúng kiểu.
- `List<?> readOnlyView`: hạn chế những thao tác thêm phần tử không tương thích qua view này. Nó không lọc hoặc khôi phục dữ liệu đã bị làm bẩn.
- `checkedCopy`: kiểm tra từng phần tử thực tế trước khi xây dựng một `ArrayList<String>` mới; nhờ đó kết quả được bảo đảm theo quy tắc kiểm tra trong hàm. Ví dụ vẫn giữ nguyên danh sách nguồn.

Chạy với `-Xlint:rawtypes` và `-Xlint:unchecked` còn giúp thấy chỗ dùng raw type, thao tác `add` không an toàn và phép chuyển raw → `List<String>`. Phần cảnh báo là kết quả của **lệnh biên dịch**, không phải standard output được liệt kê ở trên.

## 7. So sánh raw type với `List<?>`: tại sao wildcard tốt hơn khi không biết kiểu phần tử?

Khi một API chỉ cần xem các phần tử mà không cần biết kiểu cụ thể, dùng:

```java
static void printAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Lời gọi `printAll(List<String>)` hoặc `printAll(List<Integer>)` đều hợp lệ. Kiểu `List<?>` nói rõ “đây là một danh sách với kiểu phần tử chưa biết” và **giữ các quy tắc generic**, do đó không cho phép thêm tùy ý `String`, `Integer` hay `Object` qua tham chiếu `List<?>`. Thông thường chỉ có thể thêm `null` qua `add`, vì `null` tương thích mọi kiểu tham chiếu.

Đối chiếu:

```java
// Snippet minh họa, không biên dịch dòng được chú thích lỗi.
List<String> source = new ArrayList<>();
List<?> wildcard = source;
List raw = source;

// wildcard.add("A"); // Lỗi biên dịch: không biết capture type
raw.add("A");         // Có thể biên dịch nhưng unchecked warning
Object value = wildcard.get(0); // Hợp lệ nếu có phần tử
```

Việc ví dụ cuối cần danh sách có phần tử trước khi gọi `get(0)` chỉ liên quan tới giới hạn chỉ số; `List<?>` không tự ngăn `IndexOutOfBoundsException`.

Đối với một collection nhận giá trị hoặc cung cấp giá trị theo kiểu cụ thể, có thể dùng wildcard có bound:

- `List<? extends Number>`: phù hợp khi cần đọc phần tử như `Number`.
- `List<? super Integer>`: phù hợp khi cần thêm `Integer` vào consumer, nhưng đọc trở ra chỉ có thể mặc định là `Object`.

Đây là ý tưởng **PECS (Producer Extends, Consumer Super)**. Dùng wildcard đúng mục tiêu vẫn cho phép tương tác linh hoạt mà không cần raw type.

## 8. Phương thức nhận raw type có thể làm hỏng hợp đồng của bên gọi

Giả sử API cũ khai báo:

```java
// Mã minh họa API cũ; không phải chương trình độc lập.
static void legacyAppend(List values) {
    values.add(100);
}
```

Bên gọi mới truyền `List<String>`:

```java
List<String> names = new ArrayList<>(List.of("An"));
legacyAppend(names); // Hợp lệ, nhưng API raw có thể thêm Integer
```

Compiler của bên gọi không thể chứng minh thân `legacyAppend` có giữ đúng `String` hay không. Nếu phương thức thật sự thêm `Integer`, danh sách của bên gọi bị ô nhiễm kiểu và lỗi có thể chỉ xuất hiện khi lấy ra kiểu `String`.

Với code mà bạn kiểm soát, hãy dùng một phương thức generic đúng hợp đồng:

```java
static <T> void append(List<T> values, T value) {
    values.add(value);
}

// append(names, "Binh"); // Hợp lệ
// append(names, 100);    // Bị từ chối khi không có suy luận kiểu phù hợp
```

Đối với chữ ký generic trên, nếu biến `names` là `List<String>`, việc truyền thêm giá trị `Integer` không thể được chấp nhận chỉ bằng cách “suy luận T thành Object”, vì `List<T>` là bất biến. Đây là ràng buộc hữu ích giúp giữ dữ liệu đúng kiểu.

### API cũ không sửa được thì xử lý thế nào?

Có thể chọn một trong những hướng sau tùy hợp đồng:

1. **Không cho API cũ sửa trực tiếp dữ liệu quan trọng:** truyền một bản sao hoặc view không cho chỉnh sửa khi API chỉ cần đọc; cần nhớ rằng nếu API cũ thật sự cố `add`, nó sẽ thất bại.
2. **Kiểm tra và sao chép dữ liệu khi nhận về:** dùng `List<?>` để đọc phần tử như `Object`, kiểm tra `instanceof` hoặc `Class<T>.cast`, rồi tạo collection tham số hóa mới.
3. **Viết adapter tại biên hệ thống:** cô lập raw type và unchecked conversion tại một chỗ nhỏ, tài liệu hóa điều kiện an toàn, tránh để raw type lan khắp ứng dụng.
4. **Generic hóa chữ ký khi có quyền sửa mã nguồn:** thêm type parameter hoặc wildcard phù hợp, sau đó sửa các caller và chạy kiểm tra compiler.

Việc `Collections.unmodifiableList` hay `List.copyOf` có phù hợp còn phụ thuộc API có cần sửa list hay không, có chấp nhận `null` không và bạn muốn view hay bản sao. Không có giải pháp chuyển đổi tự động nào bảo đảm biến một `List` raw không đáng tin thành `List<String>` chỉ bằng một cast.

## 9. Ép kiểu `(List<String>) raw` có an toàn hơn phép gán không?

Không. Xét:

```java
// Snippet minh họa unchecked cast:
List raw = new ArrayList();
raw.add(42);
List<String> converted = (List<String>) raw;
```

Cast từ raw `List` sang `List<String>` không kiểm tra được `String` là kiểu phần tử của từng giá trị tại runtime. Vì Java generics bị xóa kiểu, một phép cast tới kiểu parameterized như vậy thường chỉ kiểm tra được phần cấu trúc kiểu raw mà runtime hỗ trợ, không thể duyệt và xác nhận mọi phần tử.

Nếu một phần tử sai kiểu, `String s = converted.get(0)` mới có thể ném `ClassCastException`. Do đó, **thêm cast tường minh không phải là giải pháp xác thực dữ liệu**. Hãy dùng quy trình chuyển đổi có kiểm tra, như `checkedCopy(List<?>)` ở ví dụ hoàn chỉnh.

Nếu phải duy trì một unchecked cast do hợp đồng thư viện bên ngoài đã được chứng minh, có thể đặt nó trong một adapter riêng với phạm vi `@SuppressWarnings("unchecked")` tối thiểu. Tuy nhiên, cần có bất biến đủ mạnh bảo đảm nguồn dữ liệu phù hợp và kiểm thử đường đi dữ liệu, không được xem annotation là biện pháp sửa lỗi runtime.

## 10. Tác động của raw type tới method invocation

Sử dụng raw receiver có thể khiến kiểu tham số và kết quả quan sát được của các thành viên generic thay đổi theo erasure:

```java
// Snippet chỉ minh họa, không độc lập.
class Store<T> {
    private T item;

    void set(T value) {
        item = value;
    }

    T get() {
        return item;
    }
}

Store<String> typed = new Store<>();
Store rawStore = typed;    // Raw Store
rawStore.set(123);        // Unchecked call
Object value = rawStore.get();
// String text = typed.get(); // Có thể ClassCastException
```

Ở đây, type parameter `T` không bound nên `Store<T>.set` và `Store<T>.get` được xem theo kiểu đã xóa `Object` khi gọi qua raw receiver. Nhưng nếu type parameter có bound, kiểu đã xóa có thể là `Number`, `CharSequence` hoặc bound đầu tiên phù hợp.

Khi một lớp generic có **phương thức generic riêng**, cách dùng raw receiver cũng có thể ảnh hưởng việc xem chữ ký các instance member theo quy tắc raw type. Không nên tự giả định rằng mọi phương thức gọi qua raw receiver đều giữ được toàn bộ suy luận generic như khi dùng tham chiếu parameterized. Cách chắc chắn là để biến/caller mang kiểu tham số hóa đúng hoặc wildcard, rồi để compiler kiểm tra các lời gọi.

**Static member** của class generic không phụ thuộc một instance gắn type argument riêng; không viết `Store<String>.someStaticMethod()` để tạo một bản static method cho từng kiểu `T`.

## 11. Những điều raw type không làm

- **Không tạo một đối tượng mới.** `List raw = typed;` giữ nguyên tham chiếu.
- **Không biến phần tử `String` thành `Object` theo nghĩa thay đổi runtime class.** `String` vẫn là đối tượng `String`; chỉ cách compiler nhìn giá trị thay đổi.
- **Không đồng nghĩa với `List<Object>`.** Một raw `List` nhận được `List<String>` trong khi `List<Object>` không thể nhận trực tiếp `List<String>`.
- **Không làm runtime tự kiểm tra toàn bộ nội dung.** Chuyển ngược hoặc cast sang `List<String>` không duyệt xác thực các phần tử.
- **Không bỏ mọi kiểm tra kiểu Java.** Raw `List` vẫn là một `List`, và các ràng buộc không liên quan generic, kiểm tra cast thông thường, giới hạn truy cập và cú pháp Java vẫn tồn tại.
- **Không khiến `List<String>` và `List<Integer>` được phép gán trực tiếp cho nhau.** Tính bất biến của generic vẫn được áp dụng tại mã nguồn có tham số kiểu.
- **Không có nghĩa mọi lỗi xảy ra ở `raw.add`.** Dữ liệu sai kiểu thường chỉ lộ ra khi phía code parameterized cần đọc thành kiểu cụ thể.

## 12. Khi nào việc sử dụng raw type còn có lý do?

Raw type chủ yếu xuất hiện khi tương tác với code hoặc API cũ được thiết kế trước generics, hoặc tại một số ranh giới tích hợp khó tránh. Trong những tình huống đó:

1. Giới hạn raw type trong adapter nhỏ, đặt tên rõ chức năng.
2. Dùng compiler warnings `-Xlint:rawtypes` và `-Xlint:unchecked` như tín hiệu để tìm đường đi có nguy cơ heap pollution.
3. Đánh giá API có **được phép ghi** vào collection không; chỉ dùng view không cho chỉnh sửa khi hợp đồng cho phép.
4. Khi dữ liệu đến từ nguồn không đáng tin, kiểm tra từng phần tử rồi sao chép sang kiểu đúng; đừng chỉ cast.
5. Tránh loại bỏ cảnh báo bằng `@SuppressWarnings` ở cả lớp hoặc cả dự án.

Trong code Java mới, nếu chưa biết kiểu phần tử, **ưu tiên `List<?>` thay vì raw `List`**. Nếu biết kiểu cần đọc hoặc ghi, dùng kiểu tham số hóa hoặc bounded wildcard tương ứng.

## 13. Bài tập tự kiểm tra

**Câu 1.** `List<String> strings = new ArrayList<>(); List raw = strings;` có tạo một danh sách mới không?

Không. `strings == raw` là `true` vì chúng là hai tham chiếu tới cùng đối tượng.

**Câu 2.** Tại sao `raw.add(42)` có thể biên dịch khi `strings.add(42)` không biên dịch?

Qua raw receiver, ràng buộc type argument `String` không được áp dụng cho `add(E)`; chữ ký erasure có thể nhận `Object`. Compiler thường đưa ra cảnh báo unchecked.

**Câu 3.** `List<String> casted = (List<String>) raw;` có kiểm tra tất cả các phần tử thành `String` không?

Không. Cast tới parameterized collection có hạn chế do type erasure; để xác thực nội dung cần tự kiểm tra từng phần tử hoặc dùng nguồn có hợp đồng bảo đảm.

**Câu 4.** `List<?> unknown = strings;` có an toàn hơn `List raw = strings;` không?

Có nếu cần nhận một danh sách với kiểu phần tử chưa biết mà vẫn muốn compiler ngăn việc thêm giá trị tùy ý. Nhưng `List<?>` không tự khắc phục heap pollution đã xảy ra trước đó.

**Câu 5.** Vì sao `List<String> back = raw;` bị cảnh báo unchecked?

Vì compiler không có bằng chứng rằng mọi phần tử đang lưu trong raw list thỏa `String`; phép gán không thực hiện quét nội dung để xác nhận.

**Câu 6.** Có thể dùng `List<Object> objects = strings;` để thay cho raw type không?

Không. `List<String>` không phải subtype của `List<Object>` vì generic Java bất biến. Nếu cần xem mọi phần tử ở mức `Object`, cân nhắc `List<?>` hoặc wildcard phù hợp.

## 14. Quy tắc ghi nhớ

```text
List<String> typed = ...;
List raw = typed;          // Cho phép; cùng đối tượng, mất thông tin generic ở biến raw
raw.add(42);              // Unchecked; có thể gây heap pollution
List<String> back = raw;  // Unchecked conversion; KHÔNG xác thực lại phần tử
List<?> view = typed;     // View tham số hóa an toàn hơn khi chưa biết type argument
```

**Kết luận:** chuyển từ **kiểu tham số hóa sang raw type** là sự chuyển đổi hợp lệ để duy trì khả năng tương tác với mã Java cũ, nhưng làm mất một phần kiểm tra kiểu generic ở các thao tác qua tham chiếu raw. Rủi ro lớn nhất là mã raw sửa collection dùng chung bằng dữ liệu sai kiểu và lỗi chỉ bộc lộ khi code parameterized đọc lại. Với mã mới, hãy giữ kiểu tham số hóa hoặc wildcard, cô lập API cũ và kiểm tra dữ liệu thực trước khi chuyển trở lại kiểu cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-004">Chuyển từ raw type về kiểu tham số hóa và cảnh báo unchecked</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển từ raw type về kiểu tham số hóa và xử lý cảnh báo `unchecked` trong Java Generics

## 1. Mục tiêu học tập

**Raw type** (*kiểu thô*) là việc dùng tên một kiểu generic mà bỏ các type argument. Ví dụ `List` là raw type của `List<E>`; `Map` là raw type của `Map<K,V>`. Raw type chủ yếu tồn tại để mã Java cũ, viết trước thời kỳ Generics, có thể tiếp tục tương tác với các API mới.

Khi bảo trì ứng dụng, bạn thường gặp:

```java
List names = new ArrayList();
names.add("Lan");

List<String> typed = names; // Cảnh báo unchecked conversion
```

Chương trình có thể biên dịch, nhưng compiler không thể chứng minh rằng mọi phần tử trong `names` đều là `String`. Nhiệm vụ chuyển đổi không chỉ là thêm `<String>` vào vài khai báo: bạn cần xác định **dữ liệu thực**, **kiểu dự định**, **luồng đọc/ghi** và **ranh giới legacy**.

Sau bài này, bạn có thể:

- Phân biệt raw type, `List<?>`, `List<Object>` và `List<String>`.
- Đọc được cảnh báo `[rawtypes]`, `[unchecked]`, đặc biệt là *unchecked conversion*, *unchecked call* và *unchecked cast*.
- Chuyển khai báo, tham số, kiểu trả về và phương thức tiện ích sang generic một cách có hệ thống.
- Nhận diện *heap pollution* và lý do `ClassCastException` có thể xuất hiện muộn hơn vị trí cảnh báo.
- Dùng `Class<T>` và kiểm tra từng phần tử để chuyển dữ liệu legacy vào cấu trúc có kiểu được bảo đảm.
- Khoanh vùng trường hợp bất khả kháng phải dùng `@SuppressWarnings("unchecked")`, với bằng chứng về invariant thay vì ẩn cảnh báo hàng loạt.

Ví dụ chính sử dụng Java 17+, được biên dịch và chạy để đối chiếu cả cảnh báo lẫn kết quả.

## 2. Raw type thực chất bỏ mất điều gì?

Một collection kiểu tham số hóa:

```java
List<String> names = new ArrayList<>();
names.add("Java");

// names.add(17); // Lỗi biên dịch: int/Integer không phù hợp String.
String first = names.get(0);
```

Tham số kiểu `String` là một **hợp đồng tại compile time**: người gọi chỉ được thêm giá trị tương thích và khi đọc có thể nhận `String` mà không tự viết cast.

Dạng raw:

```java
List names = new ArrayList();
names.add("Java");
names.add(17); // Compiler có thể cảnh báo unchecked call.
```

cho phép lời gọi `add` với nhiều kiểu khác nhau, bởi `List<E>` được dùng mà không có `E` cụ thể. Đây **không phải** một danh sách type-safe chứa mọi loại đối tượng. Nó đơn giản làm mất nhiều kiểm tra generic tại điểm sử dụng.

Các phép gán raw và generic có thể được compiler cho phép vì lý do tương thích ngược, song có thể đi cùng cảnh báo:

```java
List raw = new ArrayList();
List<String> strings = raw; // Unchecked conversion.
```

Kết quả gán **không duyệt hoặc chuyển đổi phần tử**. Nếu `raw` chứa `Integer`, lệnh đọc `String` ở nơi khác có thể ném `ClassCastException`.

### 2.1. `List`, `List<?>`, `List<Object>` khác nhau

| Kiểu | Hợp đồng kiểu | Đọc phần tử | Thêm phần tử |
| --- | --- | --- | --- |
| `List` | Raw; không có type argument để compiler kiểm tra đầy đủ | Thường như `Object` | Có thể gọi `add` với object khác nhau, sinh unchecked warning |
| `List<?>` | List có kiểu phần tử **chưa biết** nhưng vẫn là dạng generic hợp lệ | Đọc như `Object` | Không được thêm phần tử khác `null` qua tham chiếu đó |
| `List<Object>` | Danh sách có phần tử kiểu `Object` | Đọc như `Object` | Thêm được nhiều loại đối tượng |
| `List<String>` | Danh sách có phần tử `String` | Đọc như `String` | Thêm `String` hoặc `null`, tùy chính sách dữ liệu |

Một `List<String>` có thể được nhìn như `List<?>`:

```java
List<String> strings = List.of("a", "b");
List<?> unknown = strings;
Object first = unknown.get(0);
```

Nhưng **không** thể tự ý gán cho `List<Object>`:

```java
// List<Object> values = strings; // Lỗi compile: generic bất biến.
```

Dù `String extends Object`, `List<String>` không phải subtype của `List<Object>`. Vì vậy thay mọi `List` raw bằng `List<Object>` có thể khiến API sai ngữ nghĩa hoặc làm code cũ không biên dịch. Khi chỉ cần quan sát một danh sách chưa biết kiểu phần tử, `List<?>` thường phù hợp hơn.

### 2.2. Raw type khác wildcard

`List<?>` giữ lại việc kiểm tra kiểu generic. Compiler ngăn bạn thêm một giá trị không phù hợp với kiểu phần tử bí ẩn.

```java
static int count(List<?> list) {
    return list.size();
}
```

Hàm chỉ đếm nên không cần một tham số kiểu riêng. Nếu một phương thức cần **giữ mối quan hệ kiểu** giữa input và output, nên dùng phương thức generic:

```java
static <T> T first(List<T> list) {
    return list.get(0);
}
```

`List<?>` không thể thay thế mọi phương thức `<T>`: wildcard diễn đạt “kiểu phần tử không quan trọng/không biết”; biến kiểu `T` diễn đạt “những vị trí này phải có cùng kiểu”.

## 3. Ba loại cảnh báo thường gặp

Hãy biên dịch với các tùy chọn của `javac`:

```bash
javac --release 17 -Xlint:rawtypes -Xlint:unchecked YourFile.java
```

Thay `YourFile.java` bằng tên tệp nguồn. `-Xlint:rawtypes` yêu cầu cảnh báo khi dùng raw type không phù hợp; `-Xlint:unchecked` mở cảnh báo các thao tác không thể kiểm tra đầy đủ kiểu trong hệ thống generic. Cảnh báo cụ thể, số dòng và định dạng có thể khác theo phiên bản JDK.

### 3.1. `[rawtypes] found raw type`

Ví dụ:

```java
List values = new ArrayList();
```

Compiler nhận thấy hai kiểu generic `List<E>` và `ArrayList<E>` đều được dùng mà thiếu type argument.

Nếu mọi phần tử thực sự là chuỗi:

```java
List<String> values = new ArrayList<>();
```

Nếu kiểu phần tử hiện chưa biết và chỉ đọc:

```java
void inspect(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Việc đổi thành `List<?>` giúp tránh truyền quyền `add(Object)` không kiểm soát ra ngoài. Tuy nhiên, không nên cố đổi đối tượng thực sự cần thay đổi nội dung thành một API chỉ đọc khi nghiệp vụ vẫn yêu cầu ghi.

### 3.2. `[unchecked] unchecked call to add(E)`

Ví dụ:

```java
List raw = new ArrayList();
raw.add(123);
```

Với raw receiver, compiler không có kiểu `E` cụ thể để kiểm tra đối số `add`. Cảnh báo `unchecked call` chỉ ra **lời gọi sử dụng member generic qua raw type**.

Nếu danh sách phải chứa số nguyên:

```java
List<Integer> integers = new ArrayList<>();
integers.add(123);
```

Chỉ thay khai báo từ `List` sang `List<Integer>` khi điều đó đúng với **mọi nơi ghi**, kể cả các hàm nhận alias của cùng danh sách. Một danh sách đã bị nhiều nơi thêm nhiều kiểu không tương thích phải được sửa luồng dữ liệu, chia theo mô hình domain hoặc kiểm tra/chuyển đổi từng phần tử.

### 3.3. `[unchecked] unchecked conversion`

Ví dụ:

```java
List raw = new ArrayList();
List<String> strings = raw;
```

Phép gán được cho phép vì tương thích ngược, nhưng compiler không thể chứng minh từng phần tử là `String`. Nó không tự động kiểm tra phần tử và không “làm sạch” collection.

Cũng nên chú ý kiểu trả về hoặc tham số của phương thức legacy:

```java
static List loadLegacy() {
    return new ArrayList();
}

// ...
List<String> names = loadLegacy(); // Unchecked conversion.
```

Đổi biến nhận thành `List<String>` chỉ đẩy cảnh báo tới ranh giới sử dụng. Nếu không kiểm soát implementation của `loadLegacy`, hãy chuyển dữ liệu sang danh sách mới bằng phép kiểm tra từng phần tử.

### 3.4. `[unchecked] unchecked cast`

Ví dụ:

```java
Object value = new ArrayList<String>();
List<String> names = (List<String>) value;
```

Cast `List<String>` không thể xác minh **type argument** `String` bằng runtime class của đối tượng. Runtime có thể kiểm tra object là `List`, nhưng không thể kiểm tra một generic specialization như `List<String>`.

Đặc biệt nguy hiểm:

```java
List raw = List.of(123);
List<String> wrong = (List<String>) raw;
String text = wrong.get(0); // Có thể ném ClassCastException.
```

Không có bất kỳ câu lệnh nào trên kiểm tra **từng phần tử** trước khi tạo `wrong`. Việc cast hoàn tất mà không ném lỗi không chứng minh danh sách có đúng kiểu phần tử.

## 4. Vì sao cảnh báo unchecked cần được sửa?

### 4.1. Heap pollution

*Heap pollution* xảy ra khi một biến có kiểu tham số hóa giữ tham chiếu tới một đối tượng không phù hợp với kiểu tham số hóa đã khai báo. Raw alias hoặc unchecked operation có thể tạo tình huống này.

Ví dụ gây lỗi có kiểm soát, tệp **`RawWarningDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RawWarningDemo {
    public static void main(String[] args) {
        List<String> typed = new ArrayList<>();
        List raw = typed;
        raw.add(123);

        try {
            String value = typed.get(0);
            System.out.println(value);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException");
        }
    }
}
```

Chạy:

```bash
javac --release 17 -Xlint:rawtypes -Xlint:unchecked RawWarningDemo.java
java RawWarningDemo
```

`javac` phát cảnh báo chính tại `List raw = typed` (raw type) và `raw.add(123)` (unchecked call). Chương trình **vẫn biên dịch được**, và stdout:

```text
ClassCastException
```

Diễn biến:

1. Một `ArrayList<String>` được tạo qua tham chiếu có kiểu.
2. Raw alias `raw` trỏ đến **cùng object**, không tạo bản sao.
3. `raw.add(123)` chèn `Integer` vào danh sách mà code khác đang tin là chỉ chứa `String`.
4. Lời gọi `typed.get(0)` được compiler xem là biểu thức `String`; kiểm tra/cast về `String` xảy ra trên kết quả đọc và thất bại.

Thông báo unchecked ở vị trí ghi là cảnh báo sớm cho lỗi runtime muộn hơn. Bỏ cảnh báo không có nghĩa phần tử sai đã được tự động loại bỏ.

### 4.2. Type erasure không xóa kiểm tra compile time

Trong JVM, `List<String>` và `List<Integer>` thường cùng dùng class runtime như `ArrayList`. Nhưng trước khi chạy, `javac` vẫn phân tích type argument để ngăn chèn `Integer` qua biến `List<String>`.

Raw type tạo vùng “mất bảo vệ” tại compile time; các cast compiler sinh khi đọc có thể lộ lỗi về sau. Đừng suy luận rằng erasure làm Generics vô ích: chính phần kiểm tra compile time bị raw/unchecked operation bỏ qua là điều cần khôi phục khi migration.

## 5. Xử lý warning theo nguồn mất bảo đảm kiểu

Khi nâng dần một method từ raw sang generic, hãy **phân biệt cụ thể warning trước khi quyết định sửa code**:

| Triệu chứng | Nguồn rủi ro | Bước sửa được ưu tiên |
|---|---|---|
| `List raw`, `new ArrayList()` | Bỏ type arguments | Dùng `List<T>`, `List<?>` hoặc một kiểu cụ thể đúng với dữ liệu |
| `raw.add(value)` | Invocation qua receiver raw | Di chuyển đường ghi sang API typed tại nơi sở hữu dữ liệu |
| `List<String> x = raw` | Unchecked conversion | Xác minh dữ liệu đầu vào hoặc chứng minh invariant từ nơi tạo |
| `(List<String>) object` | Unchecked cast | Kiểm tra từng phần tử; không giả sử cast kiểm tra type argument |
| `@SuppressWarnings("unchecked")` | Chỉ ẩn warning | Giữ duy nhất tại helper nhỏ với rationale và tests |

Ví dụ, sửa `List<String> names = legacy.load()` bằng một cast được suppress không khiến danh sách mới chỉ chứa chuỗi. Một adapter `checkedCopy(List<?>, Class<T>)` có thể xác nhận element class rồi tạo snapshot typed; cần xét `null` và mutation của nguồn.

Sau khi cô lập warning, chạy `javac -Xlint:rawtypes,unchecked`, kiểm tra behavior với dữ liệu trộn và quan sát **nơi lỗi được phát hiện**. Mục này tập trung vào **chứng minh unchecked conversion an toàn**; việc di trú nhiều module, thay public contract và bảo toàn binary compatibility cần được lập kế hoạch riêng, không giải quyết bằng annotation.

## 6. Chuyển raw collection qua adapter kiểm tra dữ liệu

Nếu nguồn là raw `List`, thay vì unchecked cast, có thể kiểm tra **từng phần tử** để xây một collection mới. Dùng `Class<T>` làm runtime type token cho kiểu phần tử cụ thể, chẳng hạn `String.class` hoặc `Order.class`.

Hàm:

```java
static <T> List<T> validatedCopy(List<?> input, Class<T> elementType) {
    List<T> result = new ArrayList<>();
    for (Object item : input) {
        result.add(elementType.cast(item));
    }
    return List.copyOf(result);
}
```

Cách này có vài đặc tính:

- `List<?>` có thể nhận tham chiếu tới nhiều `List` khác nhau mà không yêu cầu giả định mọi phần tử có cùng kiểu cụ thể từ bên gọi.
- `Class<T>.cast(item)` kiểm tra **mỗi phần tử** theo runtime class; nếu sai sẽ ném `ClassCastException`.
- Nếu duyệt thành công, `result` được tạo mới và có kiểu `List<T>` được xây qua giá trị đã kiểm tra.
- `List.copyOf(result)` trả một danh sách không thể sửa qua các phương thức cập nhật thông thường và **không chấp nhận phần tử `null`**; nếu nguồn có `null`, phép tạo bản sao này ném `NullPointerException`.
- Đây là **shallow copy**: nếu phần tử là đối tượng mutable, bản thân các phần tử đó không được sao chép sâu.

Với các kiểu generic lồng nhau như `List<List<String>>`, `Class<List<String>>` không được biểu diễn theo cách thông thường bằng một `Class` runtime chính xác cho toàn bộ type argument. Dùng `Class<List>` chỉ kiểm tra phần tử ngoài là `List`, **không kiểm tra các chuỗi trong nested list**. Nếu muốn đảm bảo cấu trúc lồng nhau, phải validate đệ quy hoặc có parser/schema thể hiện đầy đủ cấu trúc.

### 6.1. Vì sao `instanceof List<String>` không dùng được?

Không thể viết:

```java
// Không biên dịch:
if (value instanceof List<String>) {
}
```

Vì type argument `String` không reifiable. Có thể kiểm tra:

```java
if (value instanceof List<?> list) {
    System.out.println(list.size());
}
```

Nhưng kiểm tra đó **chỉ** chứng minh `value` là một `List`, không bảo đảm các phần tử là `String`. Muốn nâng nó thành `List<String>`, vẫn phải kiểm tra từng giá trị.

### 6.2. Khi cần chuẩn hóa thay vì chỉ kiểm tra

Nếu `legacy()` có dữ liệu không nhất quán, bạn có thể muốn **chuyển đổi** theo nghiệp vụ thay vì ném lỗi. Chẳng hạn field là ID có thể được nhận dưới dạng số nguyên hoặc chuỗi, nhưng việc chuyển tất cả sang `String` cần quy định rõ:

- Số nào được phép chuyển sang ID dạng chữ?
- `null` nghĩa là thiếu dữ liệu hay một giá trị hợp lệ?
- Nếu có object không mong đợi, nên bỏ, ghi nhận lỗi, hay dừng toàn bộ?
- Thứ tự và bản ghi trùng lặp có cần bảo toàn không?

Không nên mặc định gọi `.toString()` cho mọi phần tử: kết quả có thể là chuỗi vô nghĩa, che mất lỗi dữ liệu đầu vào.

## 7. Chương trình Java 17 hoàn chỉnh để thực hành migration

Tạo tệp **`MigrationDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class MigrationDemo {
    static List legacyNames() {
        List values = new ArrayList();
        values.add("Lan");
        values.add("Minh");
        return values;
    }

    static <T> List<T> validatedCopy(List<?> input, Class<T> elementType) {
        List<T> result = new ArrayList<>();
        for (Object item : input) {
            result.add(elementType.cast(item));
        }
        return List.copyOf(result);
    }

    static <T> void copy(List<? extends T> source, List<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    public static void main(String[] args) {
        List<String> names = validatedCopy(legacyNames(), String.class);
        System.out.println("names=" + names);

        List<Number> numbers = new ArrayList<>();
        copy(List.of(3, 5), numbers);
        System.out.println("numbers=" + numbers);

        List<?> unknown = List.of("A", 10);
        System.out.println("unknown-size=" + unknown.size());

        try {
            validatedCopy(List.of("OK", 99), String.class);
        } catch (ClassCastException e) {
            System.out.println("invalid=" + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch:

```bash
javac --release 17 -Xlint:rawtypes -Xlint:unchecked MigrationDemo.java
```

`javac` phát cảnh báo từ **duy nhất hàm legacy** `legacyNames()`:

```text
[rawtypes] found raw type: List
[rawtypes] found raw type: List
[rawtypes] found raw type: ArrayList
[unchecked] unchecked call to add(E) as a member of the raw type List
[unchecked] unchecked call to add(E) as a member of the raw type List
```

Đây là các **dòng nhận diện cảnh báo**, rút từ output compiler; phần location, dòng caret và mô tả bound được bỏ khỏi trích dẫn vì phụ thuộc vị trí file. Với source trên và `javac --release 17`, compiler đã báo **5 warnings**. Không có cảnh báo unchecked từ `validatedCopy` hoặc `copy`: cả hai dùng generic đúng theo hợp đồng.

Chạy:

```bash
java MigrationDemo
```

Stdout chính xác:

```text
names=[Lan, Minh]
numbers=[3, 5]
unknown-size=2
invalid=ClassCastException
```

Giải thích:

- `legacyNames()` mô phỏng mã cũ còn raw type; logic mới chưa tin ngay rằng dữ liệu trả về đúng kiểu.
- `validatedCopy(..., String.class)` duyệt nguồn, cast từng phần tử và trả danh sách mới với kiểu khai báo `List<String>`.
- Hàm `copy` dùng hướng dữ liệu: nguồn `List<? extends T>` để **đọc**, đích `List<? super T>` để **ghi**. Với `List<Integer>` nguồn và `List<Number>` đích, việc copy hợp lệ.
- `List<?> unknown` có thể chứa giá trị được suy luận với kiểu phần tử chưa biết; ở đây chỉ đọc `size()`, nên không cần raw type.
- Lần thử với danh sách `"OK", 99` bị adapter từ chối tại phần tử `Integer`, **trước khi** danh sách sai được trao như một `List<String>` cho caller.

### 7.1. Bước chuyển đổi tiếp theo khi sở hữu legacy API

Sau khi biết và kiểm chứng rằng `legacyNames()` chỉ trả chuỗi, nếu bạn **sở hữu** source của nó, có thể thay phần raw bằng:

```java
static List<String> names() {
    List<String> values = new ArrayList<>();
    values.add("Lan");
    values.add("Minh");
    return List.copyOf(values);
}
```

Nếu tất cả writer đã giữ đúng kiểu, code ở caller có thể gọn hơn:

```java
List<String> names = names();
```

Khi ranh giới nguồn dữ liệu thực sự không còn raw và không có đường ghi sai, adapter kiểm tra cùng cấu trúc có thể không cần nữa. Đừng vội xóa adapter nếu dữ liệu vẫn đến từ bên thứ ba, reflection, file nhập, serialization hoặc API trả `Object` chưa được xác thực.

### 7.2. Kiểm chứng cả compile và runtime

Một chương trình **không có warning** vẫn có thể chứa lỗi logic nghiệp vụ; ngược lại, một project cũ còn warning không đồng nghĩa mọi lời gọi đang thất bại. Để migration có bằng chứng rõ ràng, cần cả:

- Compilation với `-Xlint:rawtypes -Xlint:unchecked`.
- Test luồng input hợp lệ, input sai kiểu, dữ liệu rỗng và `null` nếu API cho phép.
- Kiểm tra caller của hàm vừa đổi signature.
- Kiểm tra bất biến kiểu còn được giữ khi có alias hoặc code bên ngoài thay đổi dữ liệu.
- So sánh hành vi trả về: danh sách mutable hay immutable, thứ tự phần tử, khả năng chấp nhận duplicate và `null`.

`List.copyOf` trong chương trình tạo kết quả không sửa được và từ chối `null`. Nếu hợp đồng cũ yêu cầu mutable hoặc cho phép null, hãy chọn bản sao phù hợp thay vì coi đây là thay thế tương đương hoàn toàn.

## 8. Chuyển chữ ký phương thức và class cũ

### 8.1. Đổi một method chỉ đọc

Trước:

```java
static void logAll(List values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Sau:

```java
static void logAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Hàm không sửa danh sách và không đòi biết kiểu phần tử. Dùng `List<?>` làm hợp đồng đầu vào rõ hơn `List<Object>`, vì cả `List<String>` lẫn `List<Integer>` đều có thể truyền vào.

### 8.2. Đổi một method phải bảo toàn kiểu

Trước:

```java
static Object first(List values) {
    return values.get(0);
}
```

Sau:

```java
static <T> T first(List<T> values) {
    return values.get(0);
}
```

Caller được hưởng type inference:

```java
String name = first(List.of("Java"));
Integer number = first(List.of(7));
```

Lúc này liên hệ `T` giữa tham số và kết quả được giữ ngay trong chữ ký. Nếu đầu vào vẫn là raw `List`, caller vẫn có thể phát sinh cảnh báo; chỉ sửa helper chưa đủ để chứng minh mọi giá trị nguồn.

### 8.3. Đổi class chứa state

Trước:

```java
class OldHolder {
    private Object value;

    Object get() {
        return value;
    }

    void set(Object value) {
        this.value = value;
    }
}
```

Nếu class cần nhiều loại value nhưng mỗi instance chỉ giữ một kiểu xác định:

```java
class Holder<T> {
    private T value;

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }
}
```

Dùng:

```java
Holder<String> holder = new Holder<>();
holder.set("hello");
String result = holder.get();
```

Mẫu trên không còn phải ép kiểu khi đọc. Tuy nhiên, đừng generify class khi mô hình thực cần một tổng hợp nhiều loại giá trị khác nhau và có quy tắc xử lý riêng. Khi đó có thể dùng một interface domain, một sealed hierarchy hoặc các record rõ nghĩa.

### 8.4. Generic API ở ranh giới thư viện

Giả sử code tiêu thụ một thư viện cũ:

```java
List loadFromLibrary(); // Chữ ký legacy; không tự sửa được.
```

Nếu API ngoài không kiểm tra type argument, hãy thêm adapter ở code mình kiểm soát:

```java
static List<String> readNamesSafely(List<?> incoming) {
    List<String> names = new ArrayList<>();
    for (Object item : incoming) {
        if (!(item instanceof String name)) {
            throw new IllegalArgumentException(
                    "Expected a String name, got: " + item);
        }
        names.add(name);
    }
    return names;
}
```

Khi viết hàm trên thành source độc lập, cần import `java.util.List` và `java.util.ArrayList`. `instanceof String name` là cú pháp pattern matching có sẵn ở Java 17. Trong ví dụ này `null` cũng không thỏa `instanceof String`; hành vi báo lỗi là có chủ đích.

Adapter giúp nơi khác chỉ nhìn thấy `List<String>`, nhưng **không ép thư viện cũ trở nên type-safe**. Nếu thư viện tiếp tục sửa cùng một list, trả về bản sao sau validation giúp tách đối tượng mới khỏi alias nguồn.

## 9. Dùng wildcard cho producer và consumer

Khi chuyển API raw, nhiều trường hợp cần type variable và wildcard thay vì biến tất cả tham số thành `List<T>` giống nhau.

Phương thức chỉ đọc phần tử từ nguồn:

```java
static double total(List<? extends Number> numbers) {
    double sum = 0;
    for (Number number : numbers) {
        sum += number.doubleValue();
    }
    return sum;
}
```

Nhận được `List<Integer>`, `List<Double>` và các subtype phù hợp của `Number`. Nguồn là **producer**, nên `? extends Number` có ý nghĩa.

Phương thức chỉ thêm số nguyên vào đích:

```java
static void seed(List<? super Integer> target) {
    target.add(1);
    target.add(2);
}
```

Có thể gọi với `List<Integer>`, `List<Number>`, `List<Object>`; đích là **consumer**, nên `? super Integer` phù hợp.

Với phương thức copy trong chương trình chạy được:

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T item : source) {
        target.add(item);
    }
}
```

Quan hệ `T` giữa nguồn và đích không thể diễn đạt đầy đủ bằng hai `List<?>` độc lập nếu cần chuyển phần tử mà không ép kiểu. Quy tắc **PECS** (*Producer Extends, Consumer Super*) là công cụ tốt để thiết kế sau migration, nhưng hãy áp dụng dựa trên quyền đọc/ghi thực sự, không theo thói quen thay mọi raw `List` thành wildcard.

## 10. Khi nào có thể dùng `@SuppressWarnings("unchecked")`?

Một số API legacy, reflection hoặc serialization buộc bạn thực hiện phép cast mà compiler không kiểm tra được type argument đầy đủ. Khi thật sự không có lựa chọn khả thi, có thể giới hạn suppression:

```java
@SuppressWarnings("unchecked")
static <T> List<T> uncheckedBoundary(Object input) {
    return (List<T>) input;
}
```

**Đây chỉ là minh họa cú pháp**, không phải một adapter an toàn. Bản thân method không xác minh `input` là `List<T>` đúng nghĩa; nó thậm chí có thể thất bại nếu input không phải `List`. Không nên copy method này vào production rồi coi cảnh báo đã được giải quyết.

Điều kiện để cân nhắc suppression có trách nhiệm:

1. Chứng minh invariant mà compiler không chứng minh được (ví dụ đối tượng được tạo tại chỗ và **mọi thao tác ghi** qua alias đã được kiểm soát).
2. Đặt suppression ở **phạm vi nhỏ nhất**: một local variable hoặc method nhỏ có vai trò xác định, tránh suppression trên cả class/package.
3. Ghi rõ nguồn bảo đảm kiểu, dữ liệu nào được kiểm tra và điều gì khiến invariant bị phá.
4. Viết test với đầu vào đúng và sai kiểu, đặc biệt nếu có dữ liệu không tin cậy hoặc alias từ bên ngoài.
5. Ưu tiên adapter tạo bản sao với element check khi có thể, vì nó cung cấp bằng chứng runtime rõ ràng.

`@SuppressWarnings("rawtypes")` cũng chỉ ẩn cảnh báo raw type. Việc ẩn `rawtypes` hoặc `unchecked` **không bổ sung kiểm tra kiểu** và không sửa dữ liệu sai.

### 10.1. `Collections.checkedList` hỗ trợ điều gì?

Java có wrapper kiểm tra phần tử khi ghi:

```java
List<String> checked =
        java.util.Collections.checkedList(new ArrayList<>(), String.class);
```

Wrapper kiểm tra các thao tác bổ sung phần tử **đi qua chính wrapper**. Nếu code raw cố thêm giá trị sai qua wrapper, runtime có thể ném `ClassCastException` tại lúc ghi.

Giới hạn quan trọng:

- Wrapper không tự rà soát để sửa mọi phần tử đã tồn tại khi được tạo.
- Nếu có alias trực tiếp đến backing list, code viết qua alias có thể bỏ qua kiểm tra của wrapper.
- Nó không thay thế việc thiết kế public generic signature đúng và loại bỏ raw alias.
- Với type argument lồng nhau, `Class<T>` vẫn không xác minh đầy đủ cấu trúc phần tử generic.

Sử dụng wrapper khi cần một lớp bảo vệ runtime cho các đường ghi legacy; nếu cần **dữ liệu sạch ở đầu ra**, adapter kiểm tra từng phần tử và tạo bản sao thường rõ ràng hơn.

## 11. Những lỗi migration phổ biến

### Lỗi 1 — Thay raw bằng `List<Object>` ở khắp nơi

`List<Object>` không nhận được `List<String>` theo phép gán thông thường. Nếu method chỉ quan sát dữ liệu bất kỳ, dùng `List<?>`. Nếu cần quan hệ kiểu giữa input/output, dùng `<T>`.

### Lỗi 2 — Thêm `@SuppressWarnings` trước khi hiểu cảnh báo

Suppress khiến build bớt thông báo nhưng không thay đổi dữ liệu hoặc quyền ghi. Cảnh báo unchecked thường chỉ ra **ranh giới mất kiểm tra kiểu**; hãy xử lý ranh giới ấy bằng kiểu tham số hóa hoặc validation.

### Lỗi 3 — Cast `List<?>` sang `List<String>` và coi như đã validate

Cast không kiểm tra từng phần tử. Kiểm tra `value instanceof List<?>` cũng chỉ xác minh kiểu collection. Muốn một `List<String>` thật sự sạch từ dữ liệu không tin cậy, duyệt và kiểm tra phần tử.

### Lỗi 4 — Cho rằng raw alias tạo bản sao

```java
List<String> strings = new ArrayList<>();
List raw = strings;
```

Cả hai biến trỏ tới một danh sách. Thao tác ghi qua raw alias có thể làm hỏng hợp đồng của `strings`. Nếu cần tách khỏi dữ liệu legacy, tạo bản sao **sau khi** kiểm tra giá trị.

### Lỗi 5 — Chỉ sửa kiểu biến nhận mà bỏ qua producer

Đổi `List names = legacy()` thành `List<String> names = legacy()` có thể chỉ chuyển vấn đề thành unchecked conversion. Hãy sửa chữ ký producer nếu sở hữu code, hoặc dùng adapter tại boundary nếu không sửa được.

### Lỗi 6 — Quên null, mutability và ordering

Một bản sao được tạo bằng `List.copyOf` là unmodifiable và không chấp nhận null. Nếu dữ liệu cũ có null hoặc caller trông chờ mutable list, migration có thể thay đổi hành vi mặc dù type safety đã được cải thiện. Cần ghi rõ contract đầu ra trước khi chuyển.

### Lỗi 7 — Lẫn `unchecked` với lỗi generic biên dịch cứng

Một số sai lệch kiểu không chỉ là cảnh báo:

```java
List<String> texts = new ArrayList<>();
// texts.add(10); // Compilation error.
```

Compiler từ chối vì biết type argument cụ thể. Trái lại, raw call có thể tiếp tục sau warning. Phân biệt error và warning giúp ưu tiên sửa và tránh “vá” bằng cast không cần thiết.

### Lỗi 8 — Quên các API nhận/đưa dữ liệu qua phương thức generic

Khi đổi chữ ký, cần kiểm tra các method references, lambdas, overloaded methods, overrides và serialization boundaries liên quan. Một chữ ký raw có thể đã vô tình cho phép nhiều giá trị khác nhau; sau migration code sẽ phát lộ những chỗ vi phạm hợp đồng cũ chưa được thiết kế rõ.

## 12. Quy trình kiểm tra cuối cùng khi hoàn tất migration

| Câu hỏi | Bằng chứng mong đợi |
| --- | --- |
| Collection chứa đúng kiểu phần tử nào? | Hợp đồng API, producer và mọi writer đã được rà soát. |
| Có raw type nào vẫn cần thiết? | Chỉ còn tại boundary legacy được xác định và giải thích. |
| Có còn unchecked conversion/cast/call không? | `javac -Xlint:rawtypes -Xlint:unchecked` cho kết quả rõ; warning còn lại được khoanh vùng. |
| Nếu dữ liệu từ ngoài vào, ai kiểm tra phần tử? | Adapter kiểm tra runtime hoặc parser/schema chuyên dụng. |
| Có alias có thể ghi sai vào collection không? | Luồng ownership/copy hoặc wrapper được kiểm tra. |
| Null, mutability và thứ tự có còn đúng contract? | Unit/integration tests trên API đã chuyển. |
| Các caller có nhận đúng kiểu trả về? | Biên dịch toàn bộ module và các consumer cần thiết. |
| Suppression có invariant chứng minh được không? | Comment sát đoạn cast, phạm vi nhỏ, kiểm thử sai kiểu. |

Một quy trình thực dụng là sửa **từ producer đến consumer**, đảm bảo mỗi điểm nhận dữ liệu có hợp đồng đã được xác minh, rồi mới xóa cast thủ công trong business logic. Khi API phải duy trì tương thích với caller cũ, tổ chức migration theo từng ranh giới cụ thể giúp giữ an toàn và dễ review.

## 13. Bài tự kiểm tra

**Bài 1.** Đoạn dưới có làm biến `strings` thực sự được kiểm tra mọi phần tử chưa?

```java
List raw = List.of(1, 2);
List<String> strings = raw;
```

<details>
<summary>Đáp án</summary>

Chưa. Có unchecked conversion; không phần tử nào được chuyển thành `String`. Khi đọc một phần tử theo kiểu `String`, có thể phát sinh `ClassCastException`. Nếu nguồn không tin cậy, hãy validate/copy từng phần tử hoặc sửa producer.

</details>

**Bài 2.** Phương thức chỉ nhận một danh sách để in kích thước, nên dùng `List`, `List<Object>` hay `List<?>`?

<details>
<summary>Đáp án</summary>

Dùng `List<?>`: nó nhận danh sách với kiểu phần tử bất kỳ, vẫn dùng generic type checking và không cho chèn giá trị tùy ý thông qua tham chiếu này. `List<Object>` hạn chế caller `List<String>`; raw `List` bỏ mất kiểm tra kiểu.

</details>

**Bài 3.** Vì sao cảnh báo ở `raw.add(123)` có thể gây lỗi khi một method khác đọc `typed.get(0)`?

<details>
<summary>Đáp án</summary>

`raw` và `typed` có thể là hai alias của cùng collection. Raw add bỏ qua kiểm tra generic, tạo dữ liệu không phù hợp với giả định `List<String>`. Khi đọc phần tử và cast về `String`, runtime phát hiện kiểu thực là `Integer`.

</details>

**Bài 4.** Nếu có `List<?>` từ API ngoài, có thể dùng `instanceof List<String>` để kiểm tra không?

<details>
<summary>Đáp án</summary>

Không. `List<String>` không phải kiểu reifiable. Có thể xác minh object là `List<?>`, rồi dùng `Class<String>` hoặc `instanceof String` để kiểm tra từng phần tử khi chuyển sang một danh sách `List<String>` mới.

</details>

**Bài 5.** `@SuppressWarnings("unchecked")` đã làm cho cast an toàn chưa?

<details>
<summary>Đáp án</summary>

Chưa. Annotation chỉ ẩn cảnh báo compiler. Tính an toàn phải dựa trên invariant có bằng chứng hoặc một phép kiểm tra runtime thực sự. Ưu tiên adapter có validation khi dữ liệu có thể sai kiểu.

</details>

## 14. Ghi nhớ

Chuyển raw type sang kiểu tham số hóa là công việc **khôi phục hợp đồng kiểu từ nơi tạo dữ liệu đến nơi sử dụng**. `rawtypes` báo bạn thiếu type argument; `unchecked` báo compiler không chứng minh được một phép gọi, gán hoặc cast là type-safe. Không có warning không thay thế cho kiểm thử, còn việc chỉ ẩn warning không hề sửa hợp đồng dữ liệu.

Với source tự kiểm soát, khai báo rõ `List<T>`, `Map<K,V>` và phương thức `<T>` thích hợp. Với nguồn legacy không đảm bảo dữ liệu, sử dụng `List<?>`, kiểm tra **từng phần tử**, rồi tạo collection mới có kiểu cụ thể. Chỉ khoanh vùng unchecked cast khi invariant được chứng minh và thật sự không thể loại bỏ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-005">Kiểu của thành viên khi được truy cập qua raw type</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu của thành viên khi được truy cập qua raw type trong Java Generics

## 1. Vấn đề: cùng một class, nhưng kiểu của lời gọi có thể khác

Xét class generic:

```java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void set(T next) {
        value = next;
    }
}
```

Khi truy cập qua `Box<String>`, compiler biết `T` tương ứng với `String`: `get()` được nhìn nhận là trả `String`, còn `set(...)` nhận `String`.

Nhưng nếu dùng **raw type** `Box` (bỏ phần đối số kiểu `<...>`), kiểu của các thành viên có thể thay đổi ngay trong quá trình **kiểm tra mã nguồn tại vị trí truy cập**:

```java
Box<String> typed = new Box<>("Java");
Box raw = typed;

String a = typed.get();  // Hợp lệ: kết quả kiểu String
Object b = raw.get();    // Hợp lệ: T đã xóa thành Object
raw.set(42);             // Có thể biên dịch, nhưng cảnh báo unchecked
```

Đây là **đoạn mã minh họa**, đặt trong một phương thức để biên dịch. Việc dùng raw `Box` không tạo ra class runtime khác; nó làm mất một phần bảo đảm generic mà compiler có được khi biết đối số kiểu.

> **Quy tắc chính:** Đối với constructor, instance method và non-static field được khai báo trên kiểu generic `C` (không phải thành viên kế thừa từ superclass/superinterface), khi truy cập qua **raw type `C`**, kiểu của thành viên được tính theo **erasure** của kiểu thành viên trong khai báo generic `C`. Có những ngoại lệ quan trọng cho static members và inherited members.

Do đó, câu hỏi “thành viên này có kiểu gì?” phải xét **kiểu tĩnh của biểu thức nhận** (*receiver*), không chỉ nhìn vào khai báo class và không chỉ nhìn vào class runtime của đối tượng.

## 2. Raw type là gì, khác gì với kiểu tham số hóa?

Một class được khai báo:

```java
class Holder<T> { }
```

Có thể được dùng ở nhiều dạng:

| Cách dùng | Ý nghĩa |
|---|---|
| `Holder<String>` | Kiểu tham số hóa; `T` là `String` trong quá trình kiểm tra kiểu |
| `Holder<Integer>` | Kiểu tham số hóa; `T` là `Integer` |
| `Holder<?>` | Kiểu tham số hóa với wildcard: một đối số kiểu chưa biết, vẫn giữ các ràng buộc generic |
| `Holder` | **Raw type**: bỏ đối số kiểu, tương thích mã Java trước generics |
| `Holder<Object>` | Kiểu tham số hóa với `T` đúng là `Object`; không giống raw `Holder` |

Không thể tùy ý gán `Holder<String>` cho `Holder<Object>`, vì generic Java mặc định **bất biến** (*invariant*). Trong khi đó, vì lý do tương thích mã cũ, raw type có các quy tắc chuyển đổi ít chặt chẽ hơn và có thể phát sinh cảnh báo `rawtypes` hoặc `unchecked`.

`Holder<?>` và raw `Holder` đều có thể đọc giá trị của `T` như `Object` nếu `T` không có bound, nhưng chúng **khác nhau rõ rệt khi gọi method nhận `T`**, dùng generic method hoặc truy cập một thành viên có type argument riêng.

### Tại sao Java còn có raw type?

Generics được thêm vào Java sau khi nhiều API và thư viện đã tồn tại. Raw type hỗ trợ **migration compatibility**: code cũ dùng `List`, `Map`, `Class` có thể tiếp tục biên dịch và tương tác với code generic. Đổi lại, compiler không thể bảo đảm toàn bộ các lời gọi đó thỏa hợp đồng kiểu mới.

Trong code mới, khi không biết đối số kiểu, `Holder<?>` thường phù hợp và an toàn hơn `Holder`. Raw type chủ yếu nên xuất hiện ở ranh giới với mã cũ, khi làm việc với những API có chủ đích dùng kiểu thô, hoặc trong ví dụ giải thích cơ chế type erasure.

## 3. Quy tắc về instance field, method và constructor

Theo Java Language Specification §4.8, đối với raw type `C`:

1. **Constructor của `C`** được nhìn theo kiểu đã xóa của constructor trong khai báo generic `C`.
2. **Instance method do `C` khai báo**, nếu không phải inherited member, được nhìn theo erasure của kiểu method đó.
3. **Non-static field do `C` khai báo**, nếu không phải inherited member, được nhìn theo erasure của kiểu field đó.
4. **Static method và static field của `C`** giữ kiểu như trong khai báo generic; chúng không bị “raw hóa” chỉ vì `C` là class generic.
5. **Inherited instance methods và fields** có quy tắc riêng: kiểu thành viên được xét trong *supertype của `C` mà khai báo thành viên đó*. Vì vậy không được phát biểu rằng mọi inherited member trên raw receiver đều tự động biến thành raw theo cùng một cách.

Erasure của các thành phần cơ bản:

| Kiểu trong khai báo | Kiểu sau erasure |
|---|---|
| `T` khi `<T>` | `Object` |
| `T` khi `<T extends Number>` | `Number` |
| `T` khi `<T extends Number & Comparable<T>>` | `Number` (giới hạn đứng đầu) |
| `List<T>` | Raw `List` |
| `List<String>` | Raw `List` |
| `Map<String, T>` | Raw `Map` |
| `T[]` khi `<T>` | `Object[]` |
| `T[]` khi `<T extends Number>` | `Number[]` |
| `String` | `String` (không thay đổi) |
| `int` | `int` (không thay đổi) |

**Điểm dễ sai:** Một instance method trong `Box<T>` có kiểu trả `List<String>` vẫn có thể được nhìn là trả raw `List` nếu được truy cập qua raw `Box`. Quy tắc không chỉ xóa những vị trí chứa `T`; nó áp dụng erasure cho **kiểu của thành viên** trong bối cảnh raw type.

Đồng thời, việc compiler nhìn thấy erasure tại nơi gọi không phải là thao tác sửa lại khai báo mã nguồn `Box<T>` và cũng không làm thay đổi những nơi khác đang dùng `Box<String>`.

## 4. Ví dụ chạy được: cùng một object qua receiver typed và raw

Tạo tệp `RawMemberTypesDemo.java`:

```java
import java.util.List;

public class RawMemberTypesDemo {

    static class Box<T> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void set(T next) {
            value = next;
        }

        List<String> fixedLabels() {
            return List.of("A", "B");
        }

        <U> U ownEcho(U value) {
            return value;
        }
    }

    public static void main(String[] args) {
        Box<String> typed = new Box<>("start");

        String first = typed.get();
        List<String> labels = typed.fixedLabels();
        String echo = typed.ownEcho("hello");

        System.out.println(first);
        System.out.println(labels.get(1));
        System.out.println(echo);

        Box raw = typed;

        Object rawValue = raw.get();
        List rawLabels = raw.fixedLabels();
        Object rawEcho = raw.ownEcho("hello");

        System.out.println(rawValue);
        System.out.println(rawLabels.get(0));
        System.out.println(rawEcho);

        raw.set(123);
        System.out.println(raw.get());

        try {
            String result = typed.get();
            System.out.println(result);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac RawMemberTypesDemo.java
java RawMemberTypesDemo
```

Stdout:

```text
start
B
hello
start
A
hello
123
ClassCastException
```

Chương trình có thể xuất hiện thông báo về thao tác không kiểm tra kiểu khi biên dịch. Để yêu cầu compiler in chi tiết các cảnh báo raw/unchecked, chạy:

```bash
javac -Xlint:rawtypes -Xlint:unchecked RawMemberTypesDemo.java
```

Dòng `Box raw = typed;` dùng raw type. Dòng `raw.set(123)` là lời gọi nguy hiểm vì phương thức `set(T)` có tham số sau erasure là `Object`; compiler không còn kiểm tra rằng `123` tương thích với `String` mà đối tượng `typed` vốn dự định chứa.

### Diễn giải từng giai đoạn

**Giai đoạn dùng receiver `typed`:**

- `typed` có kiểu `Box<String>`.
- `typed.get()` có kiểu tĩnh `String`.
- `typed.fixedLabels()` có kiểu tĩnh `List<String>`.
- `typed.ownEcho("hello")` là lời gọi generic method; compiler có thể suy luận `U = String`, nên kết quả là `String`.

**Giai đoạn dùng receiver `raw`:**

- `raw` có kiểu thô `Box`.
- `raw.get()` có kiểu tĩnh `Object` vì erasure của `T` không giới hạn là `Object`.
- `raw.fixedLabels()` có kiểu raw `List`, dù method nguồn khai báo `List<String>`.
- `raw.ownEcho("hello")` có kiểu `Object`; sự suy luận `U = String` của generic instance method do class khai báo không còn được cung cấp trong bối cảnh raw receiver.

**Giai đoạn gây lỗi runtime:**

- `raw.set(123)` ghi một `Integer` vào cùng object được biến `typed` tham chiếu.
- `raw.get()` trả `Object` nên có thể in `123`.
- Khi chương trình gán `typed.get()` vào `String`, compiler đã biên dịch lời gọi theo kiểu `Box<String>` và chèn kiểm tra/cast tương ứng với `String`.
- Đối tượng thực được lưu là `Integer`, nên cast thất bại với `ClassCastException`.

Đây là một ví dụ về **heap pollution**: trạng thái runtime không còn phù hợp với hợp đồng generic của tham chiếu `Box<String>`. Raw type giúp lỗi lọt qua quá trình biên dịch nhưng không làm `Integer` tự biến thành `String`.

## 5. Tại sao phương thức không dùng `T` vẫn có thể bị raw hóa?

Đây là phần nhiều người bất ngờ nhất.

Xét đoạn khai báo:

```java
class Repository<T> {
    java.util.List<String> names() {
        return java.util.List.of("Lan", "Mai");
    }
}
```

Đây là class hợp lệ nhưng không có `main`. Method `names()` **không dùng `T` trong return type**, vậy tại sao truy cập qua raw `Repository` vẫn có kiểu `List`, thay vì `List<String>`?

Bởi quy tắc raw member xét **kiểu của instance method** trong khai báo generic class. Erasure của return type `List<String>` là raw `List`. Compiler áp dụng erasure cho signature tương ứng của method khi receiver là raw `Repository`.

Phân biệt:

```java
Repository<Integer> typed = new Repository<>();
java.util.List<String> good = typed.names(); // Hợp lệ

Repository raw = typed;
java.util.List unchecked = raw.names();      // Kiểu tĩnh raw List
```

Nếu viết:

```java
// Ví dụ biên dịch với cảnh báo unchecked conversion.
java.util.List<String> risky = raw.names();
```

Java có thể cho phép **unchecked conversion** từ raw `List` về `List<String>` và phát cảnh báo; không có bằng chứng runtime nào chứng minh toàn bộ phần tử đều là `String`. Dù `names()` thực tế trả chuỗi ở đây, compiler áp dụng quy tắc tổng quát của raw type.

Không nên xóa cảnh báo bằng `@SuppressWarnings("unchecked")` chỉ để giữ code biên dịch “sạch”. Hãy sửa luồng kiểu để tiếp tục dùng receiver có parameterized type.

## 6. Một generic instance method tự khai báo `<U>` cũng bị ảnh hưởng

Phương thức generic có thể khai báo tham số kiểu riêng, độc lập với tham số của class:

```java
class Converter<T> {
    <U> U identity(U value) {
        return value;
    }
}
```

Trên receiver `Converter<String>`:

```java
Converter<String> typed = new Converter<>();
String text = typed.identity("abc"); // U được suy luận là String
```

Trên receiver raw `Converter`:

```java
Converter raw = typed;
Object result = raw.identity("abc"); // Kết quả theo erasure là Object
```

Những dòng trên chỉ minh họa và cần được đặt trong class/method phù hợp nếu muốn chạy. Điểm quan trọng là method **do class generic `Converter<T>` khai báo** thuộc phạm vi quy tắc raw member, dù `<U>` không dựa vào `T`.

Tham số kiểu của chính generic method cũng bị xóa trong bối cảnh này. Nếu `<U>` không có bound thì `U` có erasure `Object`; nếu method khai báo `<U extends Number>` thì `U` có erasure là `Number`.

**Dự đoán kiểu trước khi viết code** là kỹ năng hữu ích:

| Biểu thức | Kiểu tĩnh của kết quả |
|---|---|
| `typed.identity("abc")` | `String` (compiler suy luận `U`) |
| `raw.identity("abc")` | `Object` |
| `typed.identity(12)` | `Integer` |
| `raw.identity(12)` | `Object` |

Dùng đối số kiểu tường minh không phải biện pháp sửa thiết kế raw type:

```java
// Cố ý không hợp lệ: instance generic method của raw receiver đã bị xóa.
String s = raw.<String>identity("abc");
```

Cách đúng là giữ một tham chiếu đã được parameterize khi có thể, ví dụ `Converter<?>`, hoặc tách thuật toán generic thành một utility thích hợp.

## 7. Trường hợp type variable có bound: kết quả không nhất thiết là `Object`

Mốc quan trọng của erasure là **leftmost bound** (giới hạn đứng đầu) của type variable. `<T>` không có bound ngầm định là `Object`, nhưng `<T extends Number>` có erasure là `Number`.

Ví dụ chương trình chạy được, tệp `BoundedRawMemberDemo.java`:

```java
public class BoundedRawMemberDemo {

    static class NumberBox<T extends Number> {
        private T value;

        NumberBox(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void set(T value) {
            this.value = value;
        }

        double doubled() {
            return value.doubleValue() * 2;
        }
    }

    public static void main(String[] args) {
        NumberBox<Integer> typed = new NumberBox<>(5);
        NumberBox raw = typed;

        Number number = raw.get();
        System.out.println(number.intValue());
        System.out.println(raw.doubled());

        raw.set(2.5);
        System.out.println(raw.get().doubleValue());

        try {
            Integer integer = typed.get();
            System.out.println(integer);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BoundedRawMemberDemo.java
java BoundedRawMemberDemo
```

Stdout:

```text
5
10.0
2.5
ClassCastException
```

Giải thích:

- `T extends Number` nên `NumberBox` ở dạng raw nhìn `get()` như trả `Number`, `set(...)` như nhận `Number`.
- Dù `typed` là `NumberBox<Integer>`, `raw.set(2.5)` vẫn qua được tham số `Number` (với cảnh báo unchecked). Hợp đồng `Integer` bị phá vỡ.
- `raw.get()` cho `Number` và có thể gọi `doubleValue()` trực tiếp.
- `typed.get()` ở nguồn được nhìn là `Integer`; khi cần cast, runtime phát hiện giá trị thực là `Double` và ném `ClassCastException`.

Nếu giới hạn là `<T extends Number & Comparable<T>>`, erasure vẫn là `Number`. Interface `Comparable<T>` ở vị trí tiếp theo trong intersection bound không làm kiểu đã xóa của `T` đổi thành `Comparable`.

Đừng kết luận từ ví dụ đầu rằng raw `get()` **luôn** trả `Object`; phải xem bound của type variable.

## 8. Constructor nhìn theo erasure khi tạo object bằng raw type

Constructor là trường hợp thường gặp khi chuyển đổi mã cũ. Nếu class khai báo:

```java
class Envelope<T> {
    Envelope(T content) {
    }
}
```

Thì cách tạo có khai báo đối số kiểu:

```java
Envelope<String> typed = new Envelope<>("hello");
```

cho phép compiler kiểm tra `T = String`. Ngược lại:

```java
Envelope raw = new Envelope("hello");
```

dùng raw `Envelope`. Constructor nhận `T` được nhìn qua erasure `Object`, và compiler có thể phát cảnh báo cho lời gọi vì formal parameter đã thay đổi khi xóa.

Ví dụ **không được hiểu** rằng `new Envelope("hello")` runtime tạo class khác với `new Envelope<String>("hello")`. Cả hai là instance của `Envelope` được mô tả bằng cùng tên class. Điểm khác biệt nằm ở việc compiler còn theo dõi đối số generic hay không.

Với constructor có generic type parameter riêng, cùng logic erasure áp dụng cho các trường hợp phù hợp của raw `Envelope`; static factory method không chịu đúng quy tắc này chỉ vì nó nằm trong class generic, xem phần static members tiếp theo.

## 9. Static field và static method: ngoại lệ quan trọng

Một static member không phụ thuộc vào từng instance generic. Java cũng không cho static member tham chiếu trực tiếp tới type parameter `T` của class bao ngoài như một kiểu instance riêng.

Xét chương trình `RawStaticMemberDemo.java`:

```java
import java.util.List;

public class RawStaticMemberDemo {

    static class Container<T> {
        static List<String> labels = List.of("red", "blue");

        static <U> U staticEcho(U value) {
            return value;
        }

        <U> U instanceEcho(U value) {
            return value;
        }
    }

    public static void main(String[] args) {
        Container raw = new Container();

        List<String> names = Container.labels;
        String first = Container.staticEcho("hello");

        Object instanceResult = raw.instanceEcho("hello");

        System.out.println(names.get(0));
        System.out.println(first);
        System.out.println(instanceResult);
    }
}
```

Biên dịch và chạy:

```bash
javac RawStaticMemberDemo.java
java RawStaticMemberDemo
```

Stdout:

```text
red
hello
hello
```

`Container.labels` tiếp tục có kiểu `List<String>`, và `Container.staticEcho("hello")` tiếp tục dùng generic method để compiler suy luận `U = String`. Trong khi đó, `raw.instanceEcho("hello")` trả `Object` theo quy tắc raw instance method.

Kết luận chính xác:

- **Raw receiver** làm thay đổi kiểu của một số **instance members** thuộc phạm vi §4.8.
- **Static members** của generic class không bị xóa thêm theo việc bạn sử dụng raw receiver.
- Các static field/method vẫn có erasure trong bytecode theo quy tắc chung của Java Generics, nhưng ở **mã nguồn** người gọi vẫn nhận khai báo generic đầy đủ của static member.

Nên truy cập static member bằng tên class `Container.staticEcho(...)`, không qua biến instance: cách này rõ ràng và không gây nhầm lẫn về receiver.

## 10. Thành viên kế thừa: không thể áp dụng một quy tắc “xóa hết” chung

JLS §4.8 nêu riêng rằng kiểu của một **inherited instance method hoặc non-static field** phải xét theo **supertype của raw `C` mà khai báo thành viên đó**. Điều này tạo ra những tình huống mà generic method kế thừa vẫn giữ generic signature của nó.

Tạo tệp `InheritedRawMemberDemo.java`:

```java
public class InheritedRawMemberDemo {

    static class Base {
        <U> U inheritedEcho(U input) {
            return input;
        }
    }

    static class Container<T> extends Base {
        <U> U ownEcho(U input) {
            return input;
        }
    }

    public static void main(String[] args) {
        Container raw = new Container();

        String inherited = raw.inheritedEcho("kept");
        Object own = raw.ownEcho("erased");

        System.out.println(inherited);
        System.out.println(own);
    }
}
```

Chạy:

```bash
javac InheritedRawMemberDemo.java
java InheritedRawMemberDemo
```

Stdout:

```text
kept
erased
```

`inheritedEcho` được khai báo trong `Base`, là một **non-generic superclass**. Việc receiver có kiểu raw `Container` không buộc phương thức generic này trở thành raw, bởi thành viên được kế thừa qua supertype `Base` vẫn có hợp đồng generic của riêng `Base`.

Ngược lại, `ownEcho` được khai báo **trên class generic `Container<T>`**: khi dùng `Container` raw, kiểu method được nhìn theo erasure, nên `raw.ownEcho("erased")` trả `Object`.

### Khi superclass tự là generic

Không nên suy ra rằng mọi inherited generic method đều luôn giữ nguyên. Xét:

```java
class Parent<X> {
    X read() {
        return null;
    }
}

class Child<T> extends Parent<T> {
}
```

Đây là đoạn class hợp lệ, chưa có `main`. Nếu dùng raw `Child`, supertype tương ứng của `Parent<T>` cũng bị xóa theo quy tắc supertypes của raw type. Vì vậy, thành viên `read()` kế thừa từ `Parent` trong ngữ cảnh đó có thể được nhìn theo erasure của `Parent`, ở đây là `Object`.

**Cách kiểm tra đúng:** Tìm class/interface **khai báo gốc** của member, rồi xét supertype tương ứng của receiver. Đừng tự mặc định rằng chỉ cần biết receiver raw là mọi generic method kế thừa đều mất type parameter của nó.

## 11. So sánh receiver raw với wildcard `<?>`

Raw `Box` và parameterized `Box<?>` có thể đều đọc `get()` thành `Object` khi `T` không giới hạn. Nhưng wildcard vẫn bảo vệ tính nhất quán generic.

Tệp `WildcardInsteadOfRawDemo.java`:

```java
import java.util.List;

public class WildcardInsteadOfRawDemo {

    static class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        List<String> fixedLabels() {
            return List.of("A", "B");
        }

        <U> U echo(U input) {
            return input;
        }
    }

    public static void main(String[] args) {
        Box<String> strings = new Box<>("Java");
        Box<?> unknown = strings;

        Object value = unknown.get();
        List<String> labels = unknown.fixedLabels();
        String echoed = unknown.echo("safe");

        System.out.println(value);
        System.out.println(labels.get(1));
        System.out.println(echoed);
    }
}
```

Chạy:

```bash
javac WildcardInsteadOfRawDemo.java
java WildcardInsteadOfRawDemo
```

Stdout:

```text
Java
B
safe
```

Khác biệt thực tế:

- `Box<?>` vẫn là **parameterized type**, nên `fixedLabels()` còn kiểu `List<String>`.
- Method generic riêng `<U> U echo(U)` vẫn cho phép suy luận `U = String`.
- Giá trị `get()` có kiểu `Object` khi `T` không bound, vì đối số thật của `T` chưa biết.
- Nếu `Box<T>` có `void set(T)` thì trên `Box<?>` compiler **không cho** truyền tùy tiện `"hello"` hay `123` vào `set`, vì không chứng minh được chúng phù hợp với kiểu được wildcard đại diện. Thông thường chỉ có thể truyền `null` vào `set(T)` trong bối cảnh này.

Một đoạn cố ý **không biên dịch**:

```java
Box<?> unknown = new Box<String>("Java");
unknown.set("other"); // Không biết T thực tế là gì
```

`Box<?>` giữ an toàn khi không biết `T`; raw `Box` bỏ qua hàng rào kiểu, có thể cho phép lời gọi ghi dữ liệu không phù hợp với cảnh báo unchecked. Vì vậy wildcard phù hợp hơn khi API chỉ muốn “bất kỳ Box nào” và không cần thao tác gán phần tử mới.

## 12. Các trường hợp truy cập field: đọc, gán và warning

Một field khai báo bằng `T` hoặc `List<T>` cũng chịu raw member typing nếu được truy cập qua raw receiver.

```java
import java.util.List;

class Row<T> {
    T item;
    List<T> items;
    List<String> names;
}
```

Đây là đoạn class hợp lệ để phân tích, không có `main`.

Với `Row<Integer> typed`:

- `typed.item` có kiểu `Integer`.
- `typed.items` có kiểu `List<Integer>`.
- `typed.names` có kiểu `List<String>`.

Với `Row raw`:

- `raw.item` có kiểu `Object` do erasure của `T`.
- `raw.items` có kiểu raw `List` do erasure của `List<T>`.
- `raw.names` cũng có kiểu raw `List`, dù nó không phụ thuộc trực tiếp vào `T`.

Các tình huống cảnh báo không hoàn toàn giống nhau:

1. **Gán qua field của raw receiver** như `raw.item = "text"` hoặc `raw.items = someList` có thể được báo là thao tác unchecked nếu erasure làm thay đổi kiểu field.
2. **Gọi method của raw receiver** có thể bị báo unchecked khi erasure làm thay đổi kiểu các tham số hình thức của method. Chẳng hạn `raw.set("text")` khi khai báo là `set(T)`.
3. **Đọc một giá trị raw và gán sang kiểu tham số hóa**, ví dụ `List<String> names = raw.names`, có thể phát sinh **unchecked conversion** từ raw `List` sang `List<String>`.
4. Không phải **mọi** thao tác trên raw receiver đều luôn tạo cảnh báo `unchecked`. Việc đọc một kết quả đã bị xóa thành `Object` thường chỉ đơn giản cho kiểu `Object`; compiler có thể cảnh báo ở phần khai báo raw type (`rawtypes`) nhưng đó là một loại cảnh báo khác.

Sự khác biệt giữa `rawtypes` và `unchecked` hữu ích khi sửa code: một cảnh báo báo việc **bỏ đối số kiểu**, còn cảnh báo kia thường chỉ ra **thao tác không được compiler xác minh đầy đủ**. Chỉ ẩn cảnh báo khi đã chứng minh một biên giới legacy an toàn và khoanh vùng thật nhỏ.

## 13. Method return type và parameter type có thể bị ảnh hưởng khác nhau

Xét khai báo:

```java
class Service<T> {
    T get() {
        return null;
    }

    void accept(T input) {
    }

    java.util.List<String> names() {
        return java.util.List.of();
    }

    void print(String message) {
        System.out.println(message);
    }
}
```

Các đoạn dưới đây dùng để suy luận kiểu, cần đặt trong method phù hợp.

| Thành viên | Qua `Service<String>` | Qua raw `Service` |
|---|---|---|
| `get()` | Trả `String` | Trả `Object` |
| `accept(...)` | Nhận `String` | Nhận `Object` |
| `names()` | Trả `List<String>` | Trả raw `List` |
| `print(...)` | Nhận `String` | Vẫn nhận `String` |
| `getClass()` | Trả `Class<? extends ...>` theo quy tắc `Object.getClass()` | Theo khai báo kế thừa từ `Object`, không áp dụng máy móc quy tắc raw member của `Service` |

Đối với `print(String)`, erasure của `String` vẫn là `String`, nên lời gọi không tự biến thành `print(Object)`.

Một API có thể mất **kiểu trả về generic** ngay cả khi method không nhận tham số; nhưng cảnh báo unchecked của **method invocation** theo quy tắc raw type liên quan trực tiếp đến việc erasure làm thay đổi **formal parameter types**. Vì thế việc không thấy cảnh báo tại `raw.names()` **không chứng minh** kết quả còn là `List<String>`.

## 14. Type erasure trong bytecode và kiểu ở lời gọi

Nên phân biệt hai bước:

1. **Mã nguồn:** Compiler biết receiver là `Box<String>`, `Box<?>` hay raw `Box`, từ đó xác định kiểu tĩnh của biểu thức và các thao tác được phép.
2. **Bytecode:** Các kiểu generic được thể hiện qua erasure trong descriptor, cùng các lệnh chuyển kiểu thích hợp; compiler có thể ghi metadata generic `Signature` để công cụ/reflection truy xuất thông tin khai báo.

Ví dụ khai báo trong `Box<T>`:

```java
T get() { return value; }
void set(T value) { this.value = value; }
java.util.List<String> fixedLabels() {
    return java.util.List.of("A", "B");
}
```

Với `<T>` không bound, các descriptor tương ứng về mặt JVM là:

```text
get:         ()Ljava/lang/Object;
set:         (Ljava/lang/Object;)V
fixedLabels: ()Ljava/util/List;
```

Nếu biên dịch class `RawMemberTypesDemo` ở phần trước, có thể dùng:

```bash
javap -p -s 'RawMemberTypesDemo$Box'
javap -p -v 'RawMemberTypesDemo$Box'
```

`javap -s` cho thấy descriptor. `javap -v` có thể cho thấy generic `Signature` mang dấu vết `T` và `List<String>` ở phần khai báo. Các ký hiệu `#` trỏ tới constant pool hoặc offset lệnh có thể thay đổi giữa các bản JDK; điều cần quan sát là **mối quan hệ giữa descriptor đã xóa và generic metadata**.

Cùng một descriptor `get(): Object` trong class file vẫn có thể được Java source nhìn thành `String` khi receiver là `Box<String>` nhờ kiểm tra kiểu và cast compiler chèn ở nơi sử dụng. Với raw `Box`, thông tin `String` đã bị loại khỏi kiểu của biểu thức nhận, nên trình biên dịch không gán kết quả cho biến `String` nếu không có cast thích hợp.

**Không có bản sao method `get()` riêng cho từng `T`** chỉ vì gọi qua `Box<String>` hoặc `Box<Integer>`. Chính việc compiler giữ hoặc bỏ generic information ở nơi gọi tạo nên khác biệt.

## 15. Mối liên hệ với bridge method và override

Raw type có thể tạo điều kiện cho một lời gọi có tham số sau erasure đi vào **bridge method** do compiler sinh để giữ hành vi override sau generic erasure. Một ví dụ rõ ràng:

Tạo `RawBridgeDemo.java`:

```java
public class RawBridgeDemo {

    static class Parent<T> {
        void save(T value) {
            System.out.println("parent");
        }
    }

    static final class StringChild extends Parent<String> {
        @Override
        void save(String value) {
            System.out.println(value.toUpperCase());
        }
    }

    public static void main(String[] args) {
        Parent<String> typed = new StringChild();
        typed.save("ok");

        Parent raw = typed;
        try {
            raw.save(42);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac RawBridgeDemo.java
java RawBridgeDemo
```

Stdout:

```text
OK
ClassCastException
```

Giải thích:

- `Parent<T>.save(T)` bị xóa thành method có tham số `Object`.
- `StringChild.save(String)` là override hợp lệ sau khi xét `Parent<String>`.
- Compiler có thể tạo trong `StringChild` một **bridge method** nhận `Object`, chuyển kiểu sang `String` rồi chuyển tiếp đến `save(String)`.
- Khi gọi qua raw `Parent`, compiler cho phép `raw.save(42)` theo tham số `Object` và có cảnh báo unchecked; lúc runtime bridge phải ép `Integer` thành `String`, dẫn tới `ClassCastException`.

Bridge method **không phải lỗi của Java Generics** và cũng không làm raw type an toàn hơn. Nó duy trì đa hình theo hợp đồng Java ở nơi lời gọi được biên dịch hợp lệ; raw reference có thể làm lọt một giá trị sai hợp đồng generic đến chỗ cast runtime.

Để quan sát bridge, dùng:

```bash
javap -p -v 'RawBridgeDemo$StringChild'
```

Tìm `save(java.lang.Object)` được đánh dấu `ACC_BRIDGE` và `ACC_SYNTHETIC` bên cạnh `save(java.lang.String)`.

## 16. Làm sao đọc lỗi compiler và sửa đúng nguyên nhân?

### Lỗi “Object cannot be converted to String”

Mã cố ý không hợp lệ:

```java
Box raw = new Box("abc");
String text = raw.get();
```

Giả sử `Box<T>` có `T get()`, việc `raw.get()` được nhìn thành `Object` giải thích lỗi. **Sửa nguyên nhân** bằng cách khai báo kiểu của receiver:

```java
Box<String> box = new Box<>("abc");
String text = box.get();
```

Hai đoạn cần có định nghĩa `Box<T>` và nằm trong một phương thức; chúng nhằm minh họa lỗi và cách sửa, không phải chương trình độc lập.

### Cảnh báo “unchecked call to set(T)”

Mã raw:

```java
Box raw = new Box("abc");
raw.set(99);
```

Compiler không còn biết `T = String`; gọi `set(...)` với tham số erasure `Object` gây unchecked warning. Không thể sửa triệt để bằng cách **ép kiểu giá trị** ngẫu nhiên hoặc tắt warning.

Đổi sang:

```java
Box<String> box = new Box<>("abc");
box.set("new value");
```

giúp compiler kiểm tra lời gọi. `box.set(99)` sẽ bị từ chối.

### Cảnh báo “unchecked conversion” khi gán raw collection

Mã:

```java
java.util.List<String> labels = raw.fixedLabels();
```

khi `raw` là `Box` và `fixedLabels()` là một instance method của class generic, kết quả static type là raw `List`. Compiler cho phép chuyển sang `List<String>` ở một số bối cảnh nhưng **không thể xác minh**, nên cảnh báo unchecked.

Sửa bằng cách dùng receiver parameterized:

```java
Box<String> box = new Box<>("abc");
java.util.List<String> labels = box.fixedLabels();
```

### Bẫy “Tôi đã thêm generic vào method, vậy sẽ an toàn”

Mã:

```java
class Api<T> {
    <U> U convert(U input) {
        return input;
    }
}
```

Qua raw `Api`, generic instance method này có thể bị nhìn theo erasure. Bổ sung `<U>` trong khai báo **không khôi phục được** kiểu `String` tại `raw.convert("abc")`. Cần sửa kiểu receiver hoặc chuyển phương thức thành static generic utility nếu hợp đồng thiết kế cho phép.

## 17. Quy trình phân tích kiểu thành viên khi gặp raw receiver

Khi phải đánh giá một đoạn Java dùng raw type, hãy theo thứ tự:

1. **Xác định kiểu tĩnh của receiver**, không dựa vào class runtime của object. `Box<String>` và `Box` có quy tắc member typing khác nhau.
2. **Xem receiver có thực sự raw không**; `Box<?>` không phải raw.
3. **Tìm nơi khai báo thành viên.** Nó là instance method/field khai báo trực tiếp ở `Box`, inherited member từ superclass/interface hay static member?
4. **Nếu thuộc phạm vi raw member của class đó, áp dụng erasure lên kiểu thành viên.** Không chỉ thay `T` bằng `Object`; phải xóa cả những parameterized types như `List<String>`.
5. **Nếu type variable có bound**, dùng erasure của giới hạn đứng đầu, ví dụ `<T extends Number>` cho `Number`.
6. **Nếu là inherited member**, xét member trong supertype tương ứng mà khai báo nó; không phán đoán từ receiver raw một cách máy móc.
7. **Nếu static**, dùng khai báo generic bình thường của static member, không xóa kiểu thêm vì receiver raw.
8. **Kiểm tra chiều đọc/ghi.** Việc đọc có thể cho `Object`/`List`, còn ghi hoặc gọi method với kiểu đầu vào đã bị erasure có thể tạo unchecked warning và phá hợp đồng generic.
9. **Phân biệt compile time với runtime.** Raw không tạo runtime type argument; cast và bridge method có thể ném `ClassCastException` về sau.
10. **Ưu tiên sửa API/khai báo kiểu**, thay raw bằng `Box<T>`, `Box<?>` hoặc một abstraction phù hợp thay vì tắt cảnh báo.

Một bảng nhận diện nhanh:

| Biểu thức | Kết quả/kết luận chủ yếu |
|---|---|
| `Box<String> b; b.get()` | `String` khi `T` không bound |
| `Box b; b.get()` | `Object` khi `T` không bound |
| `NumberBox b; b.get()` | `Number` nếu `T extends Number` |
| `Box b; b.fixedLabels()` | Raw `List` dù khai báo return là `List<String>` |
| `Box<?> b; b.fixedLabels()` | `List<String>` được giữ lại |
| `Box<?> b; b.echo("x")` | Method generic vẫn có thể suy luận `U = String` |
| `Box b; b.echo("x")` | Method generic do `Box` khai báo bị xem theo erasure |
| `Container.staticEcho("x")` | Static generic method vẫn generic |
| `Container raw; raw.inheritedEcho("x")` | Xét kiểu ở superclass khai báo method |
| `Box raw; raw.set("x")` | Có thể gây unchecked warning và heap pollution |

## 18. Bài tập có đáp án

**Bài 1 — Kiểu của method trả về**

```java
class C<T> {
    java.util.List<String> result() {
        return java.util.List.of("x");
    }
}
```

Với `C<Integer> typed`, `typed.result()` có kiểu gì? Với raw `C raw`, `raw.result()` có kiểu gì?

**Đáp án:** Lần lượt là `List<String>` và raw `List`. Việc method không nhắc đến `T` không làm nó đứng ngoài quy tắc raw instance member của class `C<T>`.

**Bài 2 — Bound ảnh hưởng như thế nào?**

```java
class C<T extends Number & Comparable<T>> {
    T value;
    T get() { return value; }
}
```

Với raw `C`, kiểu của `get()` là gì?

**Đáp án:** `Number`, là erasure của giới hạn đứng đầu `T extends Number & ...`.

**Bài 3 — Generic method tự khai báo kiểu**

```java
class C<T> {
    <U> U identity(U input) { return input; }
}
```

`String x = new C<String>().identity("a")` và `String y = new C().identity("a")` có cùng hợp lệ không?

**Đáp án:** Dòng thứ nhất hợp lệ nhờ suy luận `U = String`. Dòng thứ hai không thể gán trực tiếp kết quả đã bị nhìn như `Object` cho `String` trong Java source.

**Bài 4 — Inherited method**

```java
class Base {
    <U> U keep(U value) { return value; }
}
class Child<T> extends Base { }
```

Với raw `Child raw = new Child();`, `String s = raw.keep("a")` có hợp lệ không?

**Đáp án:** Có. `keep` được khai báo trong non-generic superclass `Base`, nên receiver raw `Child` không làm method này tự nhiên bị erasure theo cách của instance method được khai báo trực tiếp trên `Child<T>`.

**Bài 5 — Static field**

```java
class C<T> {
    static java.util.List<String> defaults = java.util.List.of("ok");
}
```

`C.defaults` còn kiểu `List<String>` ở mã nguồn không?

**Đáp án:** Có; static member giữ kiểu khai báo của nó. Compiler vẫn dùng erasure trong field descriptor của class file theo quy tắc JVM, nhưng đây không phải “raw member conversion” do receiver instance.

**Bài 6 — Chọn giải pháp**

Bạn nhận `Box<?> unknown` và muốn chép toàn bộ dữ liệu ra log, không cần thêm phần tử. Có nên ép về raw `Box` để tránh cast?

**Đáp án:** Không. Sử dụng `Box<?>` để đọc bằng `Object` và làm việc với các thành viên generic còn nguyên. Việc đổi sang raw chỉ khiến compiler bỏ qua các hàng rào kiểm tra kiểu không cần thiết.

**Bài 7 — Dự đoán lỗi runtime**

Có `Box<String> typed = new Box<>("a")`, sau đó `Box raw = typed; raw.set(1);`. Khi gán `String s = typed.get();`, điều gì có thể xảy ra?

**Đáp án:** Sau unchecked call qua raw receiver, object lưu `Integer`. Lời gọi typed có thể phát sinh `ClassCastException` khi kết quả được kiểm tra thành `String`.

## 19. Tổng kết và tài liệu chuẩn

Raw type là công cụ tương thích với code Java cũ, nhưng nó thay đổi cách compiler xác định **kiểu của một số thành viên**. Với constructor, instance method và non-static field được khai báo trực tiếp trên một generic class/interface, truy cập qua raw type khiến kiểu member được nhìn theo **erasure**; hậu quả có thể gồm kết quả bị hạ từ `T` xuống `Object`/bound, từ `List<String>` xuống raw `List`, cùng cảnh báo unchecked khi ghi hoặc gọi method có tham số mất kiểm tra kiểu.

Hai ngoại lệ cần luôn nhớ: **static members không bị raw hóa theo receiver**, còn **inherited members phải xét kiểu của member trong supertype khai báo nó**. So với raw type, một wildcard như `Box<?>` vẫn giữ các quan hệ generic và thường an toàn hơn khi chưa biết tham số kiểu.

Tài liệu đối chiếu:

- [Java Language Specification §4.8 — Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8): quy tắc chính thức cho kiểu của constructor, instance fields/methods, inherited/static members và unchecked warnings.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): cách tính erasure cho type variable, parameterized type và method signature.
- [Java Language Specification §4.5.2 — Members and Constructors of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.2): thành viên của kiểu tham số hóa.
- [Java Language Specification §5.1.9 — Unchecked Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.9): chuyển đổi không thể được kiểm tra đầy đủ ở compile time.
- Công cụ JDK `javac` với `-Xlint:rawtypes -Xlint:unchecked` và `javap -p -s/-v`: hỗ trợ kiểm tra warning, descriptor và bridge method thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-006">Nguy cơ trộn dữ liệu sai kiểu và lỗi xuất hiện muộn</a>

<details>
<summary>Xem chi tiết</summary>

# Nguy cơ trộn dữ liệu sai kiểu và lỗi xuất hiện muộn khi dùng kiểu thô trong Java Generics

## Mục tiêu học tập

Kiểu thô (*raw type*) là tên của một kiểu generic được dùng **mà không cung cấp đối số kiểu**: viết `List` thay cho `List<String>`, `Map` thay cho `Map<String, Integer>`. Java vẫn hỗ trợ cú pháp này chủ yếu để tương thích với thư viện, mã nguồn cũ trước generics. Nhưng khi dùng kiểu thô để ghi dữ liệu, những bảo đảm về kiểu phần tử có thể bị phá vỡ mà chương trình vẫn biên dịch và tiếp tục chạy.

Sau bài này, bạn có thể:

- Phân biệt `List<String>`, raw `List`, `List<?>` và `List<Object>`.
- Giải thích cơ chế **heap pollution**: đối tượng thực tế chứa phần tử không đúng với kiểu generic mà một tham chiếu khác đang tin tưởng.
- Mô tả vì sao một lời gọi `add` có thể thành công nhưng `ClassCastException` chỉ nổ ở thao tác `get`, vòng lặp hoặc tầng nghiệp vụ chạy sau đó.
- Đọc đúng các cảnh báo `rawtypes`, `unchecked` và bytecode `checkcast`.
- Sửa ranh giới API bằng kiểu tham số hóa, wildcard đúng mục đích, xác thực phần tử và các collection được kiểm tra kiểu lúc chạy khi thật sự cần.

## 1. Vì sao raw alias có thể khiến lỗi lộ rất muộn?

Một `List<String>` có contract phần tử `String` ở compile time. Nếu một tham chiếu khác nhìn **chính object ấy** dưới dạng raw `List`, Java cho phép gọi `add` theo erasure của method `List.add(E)` và thường chỉ báo unchecked warning:

```java
List<String> cities = new ArrayList<>();
cities.add("Ha Noi");
List rawAlias = cities; // Không tạo bản sao
rawAlias.add(42);       // Lời gọi unchecked
```

Đây là snippet trong method với imports `java.util.List` và `java.util.ArrayList`. Mặc dù hiện tại `cities` được khai báo typed, `rawAlias` đã đưa một phần tử `Integer` vào object đang dùng chung. **Không có phép kiểm tra `String.class` tự động ở raw `ArrayList.add`**. Hành vi sai chỉ bị lộ khi một downstream operation đòi hỏi `String` cụ thể.

So sánh nhanh: `List<?>` giữ quyền đọc dưới `Object` nhưng không cho thêm non-null tùy ý; `List<Object>` được tham số hóa `Object` thực sự và không nhận trực tiếp `List<String>` vì invariance. Điểm trung tâm của bài này là **truy vết từ điểm ghi sai đến vị trí cast thất bại**, thay vì chỉ ghi nhớ định nghĩa raw type.

## 2. Tình huống cốt lõi: chèn sai kiểu bây giờ, lỗi xảy ra sau

Ví dụ minh họa nằm **bên trong một phương thức**, với các import thích hợp:

```java
List<String> cities = new ArrayList<>();
cities.add("Ha Noi");

List raw = cities;
raw.add(42);             // Lời gọi unchecked; có thể chạy thành công.

String first = cities.get(0);  // "Ha Noi", vẫn hợp lệ.
String second = cities.get(1); // ClassCastException lúc đọc Integer thành String.
```

Điểm bất thường: tại dòng `raw.add(42)`, Java không tự động ném `ClassCastException` chỉ vì có một tham chiếu `List<String>` tới cùng đối tượng. Raw `List` có phương thức `add(Object)` sau erasure; `Integer.valueOf(42)` cũng là `Object` nên có thể được lưu trong một `ArrayList` thông thường.

Khi tới dòng `String second = cities.get(1)`, chương trình cần tin rằng phần tử là `String`. Trình biên dịch chèn phép cast từ giá trị trả về `Object` của `List.get(int)` sang `String`. Vì phần tử thật là `Integer`, cast thất bại **tại điểm đọc**, có thể nằm rất xa nơi ghi sai dữ liệu.

Đó là nguồn gốc của lỗi **xuất hiện muộn** (*delayed failure*): thao tác làm hỏng bất biến xảy ra ở một thời điểm, còn ngoại lệ được kích hoạt ở lần dùng tiếp theo đòi hỏi một kiểu cụ thể.

Lời gọi không an toàn được cảnh báo khi biên dịch, nhưng **cảnh báo không phải ngoại lệ runtime**. Compiler vẫn tạo class file và JVM chạy tiếp trừ khi bạn cấu hình quy trình build từ chối cảnh báo.

## 3. Heap pollution: vì sao dữ liệu và kiểu tham chiếu có thể mâu thuẫn?

*Heap pollution* trong ngữ cảnh Java generics là tình huống một biến thuộc kiểu được tham số hóa lại tham chiếu đến đối tượng không phù hợp với kiểu tham số hóa đó. Trong ví dụ trên:

```text
Danh sách ArrayList thực tế:
index 0 -> String("Ha Noi")
index 1 -> Integer(42)

Một tham chiếu đang tin:
List<String> cities
  └── mọi phần tử được lấy để dùng như String

Một tham chiếu raw cùng trỏ vào đối tượng:
List raw
  └── add(Object) đã chèn Integer

Đọc từ cities:
get(1) trả về Object(Integer)
    ↓
compiler chèn checkcast String
    ↓
ClassCastException
```

Vì hai tham chiếu giữ **cùng một đối tượng**, không thể sửa lỗi chỉ bằng cách đổi tên biến hoặc ép kiểu tham chiếu:

```java
// Không phải biện pháp sửa lỗi:
List<String> anotherView = cities;
```

`anotherView` chỉ là một alias khác, cũng nhìn danh sách bị ô nhiễm. Việc cast một collection sang `List<String>` thông qua unchecked cast cũng **không** quét và xác minh tất cả phần tử bên trong.

Một số đặc điểm đáng nhớ:

1. Lỗi có thể ẩn rất lâu nếu ứng dụng mới đọc những phần tử hợp lệ trước tiên.
2. Nếu code chỉ in `list.size()` hoặc truyền danh sách qua các tầng mà chưa đòi hỏi `String`, chưa chắc sẽ có ngoại lệ.
3. Lỗi có thể xuất hiện tại một service, formatter, mapper, consumer hoặc vòng lặp khác, khiến người debug nghi ngờ nhầm tầng đó.
4. Vị trí phát hiện sai kiểu thường là lệnh cast do compiler chèn ở **nơi đọc**, không phải câu lệnh raw `add` đã làm hỏng dữ liệu.
5. Heap pollution không chỉ đến từ raw collection: unchecked cast, varargs generic không an toàn và những ranh giới dùng reflection/API legacy cũng có thể tạo điều kiện tương tự.

## 4. Ví dụ Java 17 hoàn chỉnh: phát hiện muộn và phòng vệ sớm

Chương trình sau cố ý tạo dữ liệu sai kiểu qua raw `List` để chứng minh lỗi trì hoãn, sau đó minh họa hai cách bảo vệ: **kiểm tra dữ liệu khi nhận từ nguồn chưa rõ kiểu** và **runtime checked collection**.

**Tệp `RawTypeRiskDemo.java`:**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RawTypeRiskDemo {
    static List<String> validatedCopy(List<?> input) {
        List<String> result = new ArrayList<>();
        for (Object element : input) {
            if (!(element instanceof String text)) {
                String found = element == null
                        ? "null" : element.getClass().getSimpleName();
                throw new IllegalArgumentException(
                        "Expected String, found " + found);
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> cities = new ArrayList<>(List.of("Ha Noi", "Hue"));
        List raw = cities;
        raw.add(42);
        System.out.println("size=" + cities.size());
        System.out.println("first=" + cities.get(0));

        try {
            for (String city : cities) {
                System.out.println("upper=" + city.toUpperCase(Locale.ROOT));
            }
        } catch (ClassCastException exception) {
            System.out.println("late error="
                    + exception.getClass().getSimpleName());
            System.out.println("late frame="
                    + exception.getStackTrace()[0].getMethodName());
        }

        List<?> observed = cities;
        System.out.println("third type="
                + observed.get(2).getClass().getSimpleName());

        try {
            validatedCopy(observed);
        } catch (IllegalArgumentException exception) {
            System.out.println("validation=" + exception.getMessage());
        }
        System.out.println("clean=" + validatedCopy(List.of("Da Nang", "Hue")));

        List<String> guarded = Collections.checkedList(
                new ArrayList<>(), String.class);
        List rawGuarded = guarded;
        rawGuarded.add("Safe");
        try {
            rawGuarded.add(42);
        } catch (ClassCastException exception) {
            System.out.println("guarded error="
                    + exception.getClass().getSimpleName());
        }
        System.out.println("guarded=" + guarded);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 RawTypeRiskDemo.java
java RawTypeRiskDemo
```

`javac` có thể in ghi chú về unchecked/unsafe operations khi biên dịch; đây là hệ quả cố ý của raw type. **Stdout của lệnh `java`** đã được kiểm chứng:

```text
size=3
first=Ha Noi
upper=HA NOI
upper=HUE
late error=ClassCastException
late frame=main
third type=Integer
validation=Expected String, found Integer
clean=[Da Nang, Hue]
guarded error=ClassCastException
guarded=[Safe]
```

Tại sao đầu ra đúng như vậy?

- `raw.add(42)` tăng kích thước danh sách lên ba phần tử mà chưa ném exception.
- `cities.get(0)` trả về phần tử chuỗi ở đầu, nên vẫn in `Ha Noi`.
- Vòng lặp `for (String city : cities)` xử lý hai chuỗi đầu tiên. Đến phần tử `Integer` thứ ba, cast sang `String` thất bại. Ngoại lệ được bắt và in `late error=ClassCastException`; phương thức chứa lệnh cast là `main`.
- `List<?> observed` chỉ đọc phần tử dưới dạng `Object`, vì thế `observed.get(2).getClass()` nhìn được lớp runtime `Integer` mà không yêu cầu cast sang `String`.
- `validatedCopy` duyệt tất cả phần tử, kiểm tra từng phần tử bằng `instanceof String` và dừng với thông báo có ý nghĩa ngay tại ranh giới xác thực.
- `Collections.checkedList(..., String.class)` tạo một **wrapper kiểm tra kiểu lúc cập nhật qua wrapper**. `rawGuarded.add(42)` bị ném CCE ngay lúc ghi, khiến danh sách `guarded` chỉ còn phần tử hợp lệ `Safe`.

Ví dụ `validatedCopy` dùng *pattern matching for instanceof*, tính năng chính thức trên Java 17. `List.copyOf` trả về danh sách không sửa được và từ chối giá trị `null`; trong hàm này, `null` đã bị chặn ở bước `instanceof` nên hợp đồng đầu ra là danh sách chỉ gồm String không null.

**Không bỏ các lệnh raw khỏi chương trình nếu muốn tái tạo lỗi**: mục đích của ví dụ là chứng minh nguy cơ. Khi áp dụng trong ứng dụng thật, thay chúng bằng các API có generic chính xác.

## 5. Vì sao lỗi không nổ ngay ở `add`?

Có ba cấp độ khác nhau:

### 5.1. Hợp đồng ở mã nguồn

Với:

```java
List<String> names = new ArrayList<>();
// names.add(42); // Compile error.
```

compiler hiểu `names.add(...)` cần đối số `String`. Một literal số nguyên 42 được boxing thành `Integer`, không phù hợp `String`, nên chương trình bị từ chối từ compile time.

### 5.2. Lời gọi trên raw type

Với:

```java
List raw = names;
raw.add(42); // Unchecked warning.
```

thao tác trên raw `List` không còn ràng buộc đầu vào theo `String`. `ArrayList` mặc định lưu tham chiếu đối tượng, không tự mang cơ chế kiểm tra `String.class` trên từng lần thêm. Vì vậy thêm `Integer` có thể thành công và làm ô nhiễm danh sách.

### 5.3. Cast được compiler chèn khi đọc

Lúc đọc:

```java
String result = names.get(0);
```

ở góc nhìn bytecode, phương thức `List.get(int)` có descriptor trả về `Object`. Compiler chèn `checkcast String` trước khi gán vào `result`. Nếu phần tử thực không là `String`, JVM ném `ClassCastException` tại đây.

**Khác biệt cốt lõi:** erasure không có nghĩa là JVM hoàn toàn không kiểm tra kiểu. JVM vẫn kiểm tra các phép cast tham chiếu **cụ thể**; điều bị mất là khả năng đảm bảo tự động rằng một collection generic bị dùng bằng raw type sẽ giữ đúng tham số kiểu của các tham chiếu khác.

## 6. Quan sát bằng cảnh báo trình biên dịch và bytecode

### 6.1. Bật các cảnh báo có liên quan

Chạy:

```bash
javac --release 17 -Xlint:rawtypes,unchecked RawTypeRiskDemo.java
```

Các cảnh báo quan trọng của chương trình:

| Vị trí | Loại cảnh báo | Ý nghĩa |
| --- | --- | --- |
| `List raw = cities;` | `[rawtypes]` | Tên kiểu generic được dùng mà không ghi đối số kiểu. |
| `raw.add(42);` | `[unchecked]` | Lời gọi `add(E)` trên raw `List`; compiler không chứng minh được dữ liệu phù hợp. |
| `List rawGuarded = guarded;` | `[rawtypes]` | Vẫn là khai báo raw, dù đối tượng thật là checked wrapper. |
| `rawGuarded.add(...)` | `[unchecked]` | Compiler không thể xác nhận lời gọi raw là an toàn; checked wrapper kiểm tra ở runtime. |

Trong bản chạy đã kiểm chứng, lệnh lint trên báo năm cảnh báo: hai cảnh báo rawtypes và ba cảnh báo unchecked cho các lời gọi `add` thông qua biến raw. Số cảnh báo hoặc cách diễn đạt có thể khác khi thay đổi chương trình hoặc phiên bản trình biên dịch.

Sự khác nhau giữa hai cờ:

- `rawtypes` giúp tìm những chỗ **bỏ tham số generic**.
- `unchecked` giúp tìm những thao tác mà compiler **không kiểm tra được hoàn toàn**, đặc biệt lời gọi qua raw type và phép chuyển đổi generic không an toàn.

Một cảnh báo unchecked **không khẳng định lệnh sẽ luôn ném ngoại lệ**. Nó chỉ nói compiler không thể bảo đảm điều đó là an toàn. `rawGuarded.add("Safe")` vẫn thành công; `rawGuarded.add(42)` ném CCE do checked wrapper.

### 6.2. Xem vị trí cast thực tế

Biên dịch chương trình phần 4 rồi chạy:

```bash
javap -c -p RawTypeRiskDemo
```

Trong bytecode của `main`, hãy tìm `invokeinterface java/util/List.get` hoặc `java/util/Iterator.next` và `checkcast java/lang/String` ngay sau kết quả lấy từ danh sách.

Một đoạn logic tương đương:

```text
invokeinterface java/util/Iterator.next:()Ljava/lang/Object;
checkcast java/lang/String
astore ...
```

Đây là **trích yếu minh họa**; các chỉ số local variable và constant pool tùy bản biên dịch. Với chương trình đã kiểm chứng, `javap` thực sự cho thấy `checkcast java/lang/String` ở vị trí đọc kết quả `List.get` và trong vòng lặp sau `Iterator.next`.

Nhờ đó, bạn có thể tách biệt:

- **Lỗi được gây ra ở đâu?** Raw `add` đưa `Integer` vào cấu trúc có hợp đồng `List<String>`.
- **Lỗi bị phát hiện ở đâu?** Lệnh `checkcast` sau khi đọc qua tham chiếu có kiểu `List<String>`.
- **Lỗi hiện trong stack trace ở đâu?** Thường ở phương thức chứa thao tác đọc/cast, như `RawTypeRiskDemo.main` trong chương trình này.

Khi stack trace báo lỗi ở service đọc dữ liệu, đừng vội sửa dòng `get` bằng cast khác. Hãy truy ngược những nơi đã thêm phần tử hoặc nhận dữ liệu không được xác thực.

## 7. Lỗi xuất hiện muộn qua nhiều tầng ứng dụng

Trong dự án thực tế, thao tác gây heap pollution và thao tác phát hiện có thể thuộc các tầng khác nhau.

Sơ đồ:

```text
Adapter legacy / API chưa tham số hóa
  |
  | ghi Integer vào collection tưởng là List<String>
  v
Cache / state / DTO nội bộ
  |
  | truyền qua nhiều phương thức, chưa đọc phần tử
  v
Service nghiệp vụ
  |
  | đọc String, áp dụng String::trim / toUpperCase
  v
checkcast String thất bại
  |
  v
ClassCastException trong Service
```

Ví dụ với một `Map`:

```java
// Đoạn minh họa bên trong một phương thức:
Map<String, Integer> ages = new HashMap<>();
Map rawAges = ages;
rawAges.put("Mai", "unknown"); // unchecked: giá trị String thay vì Integer

Integer age = ages.get("Mai"); // CCE khi kết quả Object bị cast Integer
```

Cần import `java.util.Map` và `java.util.HashMap` nếu tách thành chương trình riêng. `HashMap` thông thường không kiểm tra cặp generic `String, Integer` ở runtime. Giá trị sai được ghi, đến lúc caller kỳ vọng `Integer` thì cast thất bại.

Nếu một component khác chỉ gọi `ages.containsKey("Mai")`, có thể chưa gặp vấn đề dù dữ liệu đã sai. Điều này lý giải vì sao stack trace ở component đọc không nhất thiết trỏ đến nguồn gây lỗi.

Một trường hợp khác là ranh giới API legacy:

```java
// API cũ, minh họa:
static List fetchLegacyRows() {
    return new ArrayList();
}

// Caller:
// List<String> rows = fetchLegacyRows(); // unchecked conversion
```

Ngay tại câu lệnh gán, Java không duyệt từng phần tử để xác thực chúng đều là `String`. Nếu trong danh sách có một phần tử khác kiểu, lỗi sẽ phát sinh khi chương trình sử dụng phần tử ở vai trò `String`.

Trong các hệ thống dùng cache, hàng đợi, callback hoặc chạy song song, khoảng cách thời gian có thể lớn hơn nữa. Raw type **không đồng nghĩa với lỗi đồng thời**; ở đây vấn đề chính vẫn là mất ràng buộc kiểu, không phụ thuộc có nhiều luồng hay không.

## 8. Wildcard giúp chặn lần ghi sai mới, không làm sạch heap pollution cũ

Nếu một thành phần chỉ quan sát danh sách chưa biết element type, nhận `List<?>` thay vì raw `List`:

```java
static void reportValues(List<?> source) {
    for (Object value : source) {
        System.out.println(value);
    }
}
```

Method cần import `java.util.List` và đặt trong class. Wildcard ngăn caller của method này thêm một `String` hoặc `Integer` tùy ý **thông qua tham chiếu `List<?>`**. Nó không có nghĩa `source` immutable: việc `clear()` hoặc `remove(index)` vẫn được compiler cho phép theo contract của `List`, tùy runtime mutability.

Đặc biệt, nếu một raw alias **đã** làm ô nhiễm `List<String>`, chuyển góc nhìn sang `List<?>` sẽ không xóa phần tử `Integer` đang có. Muốn công bố kết quả `List<String>`, adapter cần kiểm tra từng phần tử và thường tạo bản sao để cách ly alias.

Một API **chủ động ghi `String`** vào đích có thể nhận `List<? super String>`; nhưng API chỉ kiểm tra luồng dữ liệu nên chọn kiểu giúp chặn các thao tác ghi không thuộc trách nhiệm của nó. Khi đọc/ghi một danh sách cụ thể đã có type argument rõ, giữ `List<String>` thay vì raw để compiler bảo vệ invariant.

## 9. Xác thực dữ liệu tại ranh giới: fail fast

Giả sử một API cũ trả về `List`, một endpoint trả về dữ liệu `Object`, hoặc một thư viện chưa cung cấp kiểu generic phù hợp. Nếu không thể sửa ngay nguồn, hãy xác thực dữ liệu **trước** khi công bố nó dưới dạng `List<String>`.

Không nên làm thế này:

```java
// Minh họa cách làm nguy hiểm, không nên dùng:
@SuppressWarnings("unchecked")
static List<String> trustRaw(List raw) {
    return (List<String>) raw;
}
```

Cast `(List<String>) raw` là một cast không thể kiểm tra đầy đủ nội dung generic tại runtime. Nó có thể thành công trên danh sách chứa `Integer` vì erasure chỉ cho JVM kiểm tra đối tượng là một `List`. Annotation `@SuppressWarnings("unchecked")` không sửa dữ liệu hoặc biến cast đó thành kiểm tra thật.

Thay vào đó, xét mẫu xác thực ở phần 4:

```java
static List<String> validatedCopy(List<?> input) {
    List<String> result = new ArrayList<>();
    for (Object element : input) {
        if (!(element instanceof String text)) {
            String found = element == null
                    ? "null" : element.getClass().getSimpleName();
            throw new IllegalArgumentException(
                    "Expected String, found " + found);
        }
        result.add(text);
    }
    return List.copyOf(result);
}
```

Mẫu này là một phương thức trích từ chương trình hoàn chỉnh, cần import `List` và `ArrayList`.

Nó có ba đặc tính có ích:

1. **Xác minh từng phần tử thực** thay vì chỉ cast collection.
2. **Báo lỗi ở ranh giới nhận dữ liệu**, với thông báo chỉ ra kiểu được kỳ vọng và kiểu thực.
3. **Tạo danh sách kết quả mới**, không chia sẻ alias với danh sách đầu vào; biến raw giữ đối tượng đầu vào không thể chèn phần tử vào bản sao sau khi kiểm tra.

Mẫu trên cố ý từ chối `null`. Nếu hợp đồng cho phép null, hãy xác định quy tắc rõ ràng, kiểm tra phù hợp và chọn loại collection kết quả hỗ trợ null (vì `List.copyOf` không chấp nhận null).

Nếu dữ liệu tới từ bên ngoài mà cần trả về một cấu trúc generic nhiều tầng, kiểm tra `instanceof List` chỉ chứng minh được cấu trúc ngoài là `List`, không chứng minh từng phần tử con hoặc mỗi cặp khóa/giá trị đúng kiểu. Cần xác thực **tới mức sâu mà hợp đồng yêu cầu**.

## 10. Dùng `Collections.checkedList` để chặn dữ liệu sai ngay lúc ghi

Java Collections Framework có các wrapper kiểm tra kiểu runtime, ví dụ:

```java
List<String> checked = Collections.checkedList(
        new ArrayList<>(), String.class);
```

Khi một lời gọi đi **qua wrapper `checked`**, wrapper kiểm tra đối tượng được thêm vào bằng `Class<String>`. Trong chương trình:

```java
List rawGuarded = checked;
rawGuarded.add("Safe"); // Thành công.
rawGuarded.add(42);     // ClassCastException tại add qua wrapper.
```

Phần trên là đoạn minh họa, cần import `List`, `ArrayList` và `Collections`. Việc sử dụng raw type để gọi vẫn bị cảnh báo; nhưng checked wrapper bổ sung một lớp phòng vệ runtime, khiến lỗi được phát hiện **gần nơi đưa dữ liệu sai vào**, thay vì kéo dài tới lúc đọc.

Giới hạn quan trọng:

- Wrapper chỉ kiểm tra các thao tác **đi qua wrapper**. Nếu mã khác còn giữ tham chiếu trực tiếp tới backing list và ghi vào đó, wrapper không thể ngăn cách ghi ấy.
- Tạo wrapper quanh một danh sách đã nhiễm phần tử sai **không có nghĩa là xác thực hay làm sạch mọi phần tử có sẵn**.
- Kiểm tra dựa trên `Class<E>` kiểm tra kiểu runtime có thể biểu diễn bằng class token, chẳng hạn `String.class`; nó không thể xác minh sâu kiểu như `List<String>` chỉ bằng một `List.class`.
- Các wrapper checked không thay thế thiết kế generic đúng và cũng không tự biến một collection thành thread-safe.
- Một số collection cho phép `null`; việc chấp nhận null và các ràng buộc dữ liệu khác cần kiểm soát riêng.

Khi bạn kiểm soát được nguồn và tất cả caller, **ưu tiên sửa chữ ký** sang `List<String>` hoặc API thích hợp. Checked wrapper hữu ích như biện pháp bảo vệ bổ sung ở ranh giới với code legacy/khó tin cậy.

## 11. Vì sao `List.of` hoặc `List.copyOf` có thể giúp, và giới hạn của chúng

Một collection không sửa được có thể giảm nguy cơ dữ liệu bị chèn sai **sau khi** tạo:

```java
List<String> cities = List.of("Ha Noi", "Hue");
```

Một alias raw có gọi `add(42)` lên danh sách này cũng không thêm được phần tử: triển khai `List.of` là unmodifiable nên thao tác sửa sẽ ném `UnsupportedOperationException`. Nhưng đây là cơ chế **không cho sửa**, không phải phép xác minh generic của một danh sách bất kỳ.

Trong `validatedCopy`, sau khi kiểm tra từng phần tử, `List.copyOf(result)` tạo danh sách kết quả không sửa được, giúp ngăn một caller khác chèn thêm phần tử vào bản sao đó. Với đầu vào tùy ý, chỉ gọi `List.copyOf(someRawList)` và ép kết quả sang `List<String>` **không chứng minh các phần tử là String**. Nếu collection nguồn đã có dữ liệu sai kiểu, bạn vẫn cần xác thực từng phần tử.

Nếu cần cập nhật danh sách trong nghiệp vụ, một `List<String>` có kiểu đúng và phạm vi chia sẻ/alias được kiểm soát thường hữu ích hơn việc áp đặt bất biến không phù hợp.

## 12. Hướng dẫn xử lý một lỗi `ClassCastException` xuất hiện muộn

Giả sử stack trace báo lỗi tại dòng:

```java
String label = results.get(index);
```

trong khi phần lớn code tin rằng `results` là `List<String>`. Hãy điều tra theo chuỗi nguyên nhân:

1. **Xác định kiểu thật và vị trí phát hiện.** Exception nói kiểu nào không cast được sang kiểu nào? Nếu có `javap`, tìm `checkcast String` sau `get` hoặc `Iterator.next`.
2. **Kiểm tra dữ liệu thực ở ranh giới.** Quan sát kiểu runtime của phần tử bằng tham chiếu `List<?>` và `Object`, tránh cast lại về `String` trong lúc chẩn đoán.
3. **Tìm nơi collection được tạo và truyền đi.** Liệt kê các alias, mapper, repository, cache và API legacy có thể ghi vào cùng đối tượng.
4. **Tìm raw hoặc unchecked gần điểm ghi.** Bật `-Xlint:rawtypes,unchecked`. Chú ý `List raw = typed`, `Map raw = typed`, cast generic không được kiểm tra và hàm trả về raw.
5. **Xác định khoảng cách thời gian.** Lỗi có thể chỉ xuất hiện sau nhiều bước xử lý; đừng kết luận dòng đọc là nơi dữ liệu bị làm sai.
6. **Sửa nguồn vi phạm.** Ưu tiên loại raw type, dùng ràng buộc generic nhất quán và sửa contract phương thức. Nếu dữ liệu từ ngoài, xác thực/copy trước khi đưa vào logic typed.
7. **Thêm kiểm tra hành vi có giá trị.** Kiểm thử ranh giới với giá trị hợp lệ, sai kiểu và null tùy hợp đồng; đảm bảo phát hiện trước khi dữ liệu đi sâu vào hệ thống.

Ví dụ một test không nên chỉ kiểm tra `list.size()` sau khi gọi code legacy: một danh sách có ba phần tử vẫn có thể chứa sai kiểu. Test cần thực sự đọc phần tử theo kiểu kỳ vọng hoặc xác minh dữ liệu ngay tại ranh giới.

### Nhận diện nhanh qua các dấu hiệu

| Dấu hiệu | Khả năng xảy ra | Việc nên kiểm tra |
| --- | --- | --- |
| `ClassCastException` ở `get`/for-each | Cast do compiler chèn khi đọc phần tử | Phần tử runtime tại index đó và mọi alias có quyền ghi. |
| `ClassCastException` ngay tại `add` của checked collection | Wrapper kiểm tra phần tử trước khi ghi | Dữ liệu đầu vào có đúng `Class<E>` của wrapper không. |
| `Unchecked call to add(E)` trên raw `List` | Compiler không xác minh được kiểu phần tử | Thay raw type, hoặc cô lập và xác thực ranh giới legacy. |
| `Unchecked conversion` khi nhận `List` thành `List<String>` | Không có kiểm tra sâu phần tử | Duyệt, xác thực, sao chép trước khi công bố kiểu mới. |
| `ClassCastException` trong service cách xa nơi tạo list | Lỗi phát hiện muộn | Truy luồng ghi/read, trạng thái bị chia sẻ và API trung gian. |

## 13. Những cách sửa sai phổ biến

### Sai lầm A — Giấu cảnh báo bằng `@SuppressWarnings` toàn lớp

`@SuppressWarnings({"rawtypes", "unchecked"})` làm compiler im lặng, nhưng không có tác động tới dữ liệu, phép cast hay hành vi runtime. Dùng annotation này cho cả lớp có thể che giấu thêm những lỗi mới sau này.

Nếu bắt buộc tương tác với API cũ, hãy cô lập chỗ chuyển đổi, kiểm tra được những điều có thể kiểm tra, và giới hạn annotation ở vùng nhỏ nhất kèm lời giải thích chính xác.

### Sai lầm B — Ép kiểu danh sách rồi tin rằng nội dung đã được kiểm tra

`(List<String>) raw` có thể biên dịch với warning nhưng vẫn để dữ liệu sai bên trong. Không dùng cast ấy thay cho vòng lặp xác thực từng phần tử.

### Sai lầm C — Thay raw type bằng `List<Object>` mà không đổi nhu cầu nghiệp vụ

`List<Object>` có nghĩa là danh sách được phép chứa các Object không đồng nhất. Nếu nghiệp vụ yêu cầu toàn `String`, đó là một hợp đồng sai. Nó cũng không phải kiểu nhận được trực tiếp mọi `List<String>` vì generic bất biến.

### Sai lầm D — Cho rằng không ném lỗi ở `add` nghĩa là dữ liệu an toàn

`ArrayList` thông thường không mang ràng buộc String ở runtime. Cảnh báo unchecked tại `add` là tín hiệu cần xử lý dù chưa có exception.

### Sai lầm E — Luôn ép thêm `String.valueOf` khi đọc

`String.valueOf(42)` có thể biến số thành chuỗi `"42"`, nhưng đó là **quy tắc chuyển đổi dữ liệu**, không phải sửa lỗi kiểu theo nghĩa bảo toàn hợp đồng cũ. Nếu nghiệp vụ thực sự cho phép số và chuỗi rồi muốn chuyển thành text, hãy mô hình hóa điều đó rõ ràng. Nếu dữ liệu chỉ được phép là String gốc, chuyển đổi âm thầm có thể che giấu lỗi.

### Sai lầm F — Cứ dùng checked wrapper là dữ liệu bên trong chắc chắn sạch

Wrapper chỉ phòng vệ những thao tác được thực hiện qua nó, không nhất thiết sửa trạng thái trước đó hoặc các alias tới backing collection. Xác thực dữ liệu đã có vẫn là một bước độc lập.

## 14. Bài tập tự kiểm tra

**Bài 1.** Tại sao đoạn sau có thể biên dịch với warning, nhưng lỗi chỉ nổ tại câu lệnh thứ ba?

```java
List<String> items = new ArrayList<>();
List raw = items;
raw.add(100);
String item = items.get(0);
```

**Đáp án:** Raw `List` cho phép lời gọi thêm `Integer` với cảnh báo unchecked. `items.get(0)` trả về đối tượng thực là Integer; tại vị trí yêu cầu String, compiler đã chèn cast sang String và JVM ném `ClassCastException`.

**Bài 2.** `List<?> view = items` có loại bỏ Integer đã bị chèn vào `items` không?

**Đáp án:** Không. Wildcard chỉ thay đổi các thao tác được compiler cho phép qua biến `view`; nó không tạo bản sao và không xác thực nội dung cũ.

**Bài 3.** Với `List<String>` bị nhiễm dữ liệu, gọi `size()` thành công có chứng minh các phần tử đúng kiểu không?

**Đáp án:** Không. `size()` không ép phần tử về String. Lỗi có thể xuất hiện muộn hơn khi đọc cụ thể.

**Bài 4.** Khi nào `Collections.checkedList` giúp fail fast?

**Đáp án:** Khi thao tác thêm phần tử đi qua wrapper. Với wrapper yêu cầu String, thêm Integer bị từ chối ở lần ghi thay vì đợi đến lần đọc qua tham chiếu typed.

**Bài 5.** Có nên sửa bằng `@SuppressWarnings("unchecked")` hoặc cast `(List<String>) raw` không?

**Đáp án:** Không. Chúng không xác minh các phần tử. Cần API generic đúng hoặc xác thực từng phần tử ở ranh giới.

**Bài 6.** Một `Map<String, Integer>` bị raw `Map` chèn giá trị String. Vì sao `containsKey` có thể chạy tốt nhưng `Integer n = map.get(key)` ném lỗi?

**Đáp án:** `containsKey` chỉ kiểm tra khóa, không buộc giá trị trở thành Integer. Lúc `get` trả giá trị Object cho caller yêu cầu Integer, compiler chèn cast và ngoại lệ có thể xảy ra.

## 15. Ghi nhớ

**Nguy cơ lớn nhất của kiểu thô không chỉ là mất tính rõ ràng của chữ ký, mà là có thể phá hợp đồng generic của một đối tượng được chia sẻ, để lỗi phát sinh muộn tại nơi khác.** Lời gọi raw `add` có thể chỉ báo warning và ghi dữ liệu sai; `ClassCastException` xuất hiện khi code typed buộc phải dùng giá trị dưới kiểu kỳ vọng.

Hãy ưu tiên generic đúng trong toàn bộ API, dùng wildcard khi chỉ cần quan sát một kiểu chưa biết, xác thực sâu dữ liệu legacy trước khi gán hợp đồng mạnh hơn, và cân nhắc checked collection ở ranh giới cần phòng vệ runtime. Khi debug, lần theo thứ tự **warning/unchecked write → heap pollution → lần đọc cần cast → ClassCastException** để tìm đúng nguồn vi phạm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-007">Raw type ở lớp bên trong và giới hạn kiểu tham số hóa một phần</a>

<details>
<summary>Xem chi tiết</summary>

# Raw type ở lớp bên trong và giới hạn kiểu tham số hóa một phần trong Java Generics

## 1. Vấn đề: vì sao không thể tùy ý kết hợp raw và generic?

Một lớp generic có thể khai báo lớp thành viên bên trong:

~~~java
class Outer<T> {
    class Inner<U> {
    }
}
~~~

Ở đây có **hai tham số kiểu thuộc hai khai báo khác nhau**:

- `T` được khai báo bởi `Outer<T>`. Mọi đối tượng `Inner<U>` không static được gắn với một đối tượng `Outer<T>`, nên có thể sử dụng các thành viên mang kiểu T của đối tượng ngoài.
- `U` được khai báo bởi `Inner<U>`. Nó điều khiển những thành viên dùng U của lớp bên trong.

Các cách biểu diễn sau rất quan trọng:

~~~java
Outer<String>.Inner<Integer> complete; // Hợp lệ về mặt kiểu.
Outer rawOuter;                         // Raw Outer.
Outer.Inner rawInner;                   // Raw member type.
~~~

Ngược lại, với lớp `Inner<U>` được khai báo trực tiếp bên trong `Outer<T>` như trên, Java **không cho phép tham số hóa chỉ một phần** bằng cách kết hợp một outer raw với một inner có type argument, hoặc outer parameterized với một inner raw:

~~~java
// Outer.Inner<Integer> bad1; // Không hợp lệ.
// Outer<String>.Inner bad2;   // Không hợp lệ.
~~~

Hai ví dụ cố ý sai được đặt trong comment, để bạn không vô tình đưa chúng vào chương trình chạy. Lỗi không đơn giản chỉ là “quên dấu <>”. Java có quy tắc về **raw member types** và **các kiểu được khai báo ở những mức lồng nhau**, giúp tránh một kiểu kết hợp nửa có thông tin generic, nửa đã đánh mất ngữ cảnh generic bao quanh.

Bài học này tập trung vào **member classes**, đặc biệt là **non-static inner classes** của generic outer class; sau đó phân biệt với **static nested classes**, lớp member không tự khai báo U, diamond, wildcard và những tình huống gây cảnh báo unchecked.

## 2. Khái niệm nền tảng: member, inner, nested và enclosing type

Với Java:

~~~java
class Outer<T> {
    class Inner<U> { }       // Non-static member class (inner class).
    class PlainInner { }    // Inner class không có type parameter riêng.
    static class Nested<V> { } // Static nested class.
}
~~~

Có ba khái niệm cần tách biệt:

**Member class** là một class được khai báo là thành viên của class/interface khác. Nó có thể static hoặc non-static.

**Inner class** trong ví dụ là class thành viên **không static**, gắn với một đối tượng bên ngoài. Một đối tượng `Outer<String>.Inner<Integer>` liên kết với một đối tượng `Outer<String>` cụ thể; phương thức bên trong có thể đọc trường T của outer và trường U của inner.

**Static nested class** là class thành viên static. Nó **không có đối tượng outer ngầm định** và không sử dụng trực tiếp tham số kiểu T của `Outer<T>`. Nếu static nested class muốn generic, nó phải khai báo type parameter riêng, như `V`.

Không nên gọi tất cả nested classes là inner classes. Khác biệt static/non-static quyết định cách truy cập tham số kiểu của outer và quy tắc tạo đối tượng.

## 3. Quy tắc raw type của member class

Một *raw type* là cách dùng tên của generic class/interface mà không cung cấp các type arguments (theo ngữ cảnh mà Java cho phép). Với `Outer<T>`, tên `Outer` đứng một mình ở vị trí kiểu là raw.

Khi một non-static member class được **khai báo trực tiếp** trong raw outer class, kiểu thành viên của raw outer cũng bị coi là raw. Điều này vẫn quan trọng nếu chính member class không tự khai báo type parameter.

~~~java
class Outer<T> {
    class Inner<U> {
        T ownerValue;
        U innerValue;
    }

    class PlainInner {
        T ownerValue;
    }
}
~~~

Các trường hợp:

| Cú pháp tên kiểu | Hợp lệ? | Ý nghĩa |
| --- | --- | --- |
| `Outer<String>.Inner<Integer>` | Có | Outer có T = String, Inner có U = Integer. |
| `Outer.Inner` | Có | Raw Outer và raw member Inner; thông tin generic liên quan bị xóa. |
| `Outer<String>.Inner<?>` | Có | Outer biết String; phần tử U chưa biết, được biểu diễn bằng wildcard. |
| `Outer<?>.Inner<?>` | Có | Cả hai mức đều có tham số hóa bằng wildcard, không phải raw. |
| `Outer.Inner<Integer>` | Không | Không thể gắn U = Integer lên Inner của raw Outer. |
| `Outer<String>.Inner` | Không | Không thể giữ Outer<String> nhưng dùng raw Inner<U> kiểu này. |
| `Outer<String>.PlainInner` | Có | PlainInner không có U riêng, nhưng biết T của outer. |
| `Outer.PlainInner` | Có | Member type của raw Outer; thành viên dùng T bị nhìn qua erasure. |

Bảng này dựa trên cấu trúc **member class được khai báo trực tiếp** trong Outer ở ví dụ. Với member type được kế thừa từ một superclass hay superinterface, cần xem chính xác nơi member được khai báo; không nên áp dụng máy móc quy tắc cho mọi tên có dấu chấm.

Cần hiểu “không tham số hóa một phần” trong phạm vi cụ thể: quy tắc này **không cấm mọi kiểu thiếu dấu ngoặc nhọn <> ở cấp trong**. Ví dụ `Outer<String>.PlainInner` hoàn toàn hợp lệ vì `PlainInner` không có type parameter U nào cần cung cấp. Ngược lại `Inner<U>` là generic member class nên cách bỏ U sẽ mang ý nghĩa raw trong tổ hợp không hợp lệ.

## 4. Chương trình chạy hoàn chỉnh trên Java 17+

Lưu chương trình sau vào tệp **RawInnerDemo.java**:

~~~java
public class RawInnerDemo {
    static class Outer<T> {
        private final T label;

        Outer(T label) {
            this.label = label;
        }

        class Inner<U> {
            private final U value;

            Inner(U value) {
                this.value = value;
            }

            T ownerLabel() {
                return label;
            }

            U payload() {
                return value;
            }
        }

        // Không generic ở cấp PlainMember, nhưng dùng T của Outer.
        class PlainMember {
            T ownerLabel() {
                return label;
            }
        }

        // Không sử dụng trực tiếp T của Outer.
        static class StaticNested<V> {
            private final V value;

            StaticNested(V value) {
                this.value = value;
            }

            V get() {
                return value;
            }
        }
    }

    public static void main(String[] args) {
        Outer<String> typed = new Outer<>("owner");

        // Cả outer và inner được tham số hóa đầy đủ.
        Outer<String>.Inner<Integer> inner = typed.new Inner<>(7);
        System.out.println(
                "typed=" + inner.ownerLabel() + "/" + inner.payload()
        );

        // PlainMember không có U riêng, nhưng biết outer T = String.
        Outer<String>.PlainMember plain = typed.new PlainMember();
        System.out.println("plain=" + plain.ownerLabel());

        // Wildcard vẫn là tham số hóa, không phải raw.
        Outer<?> wildcardOuter = typed;
        Outer<?>.Inner<?> unknown = wildcardOuter.new Inner<>("text");
        Object read = unknown.payload();
        System.out.println("wildcard=" + read);

        // Raw Outer khiến member Inner được dùng ở dạng raw.
        Outer rawOuter = typed;
        Outer.Inner rawInner = rawOuter.new Inner(42);
        Object rawOwner = rawInner.ownerLabel();
        Object rawPayload = rawInner.payload();
        System.out.println("raw=" + rawOwner + "/" + rawPayload);

        // Member không generic cũng bị ảnh hưởng bởi raw outer.
        Outer.PlainMember rawPlain = rawOuter.new PlainMember();
        Object rawPlainOwner = rawPlain.ownerLabel();
        System.out.println("rawPlain=" + rawPlainOwner);

        // Static nested class có tham số kiểu độc lập với T.
        Outer.StaticNested<String> nested =
                new Outer.StaticNested<>("static");
        System.out.println("nested=" + nested.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -Xlint:rawtypes -Xlint:unchecked RawInnerDemo.java
java RawInnerDemo
~~~

`-Xlint:rawtypes` giúp báo nơi dùng raw class/member type; `-Xlint:unchecked` báo các thao tác raw có kiểm tra kiểu không đầy đủ, chẳng hạn gọi constructor `Inner(U)` qua raw member type. Cảnh báo của javac không nằm trong stdout của chương trình.

Stdout khi chương trình chạy:

~~~text
typed=owner/7
plain=owner
wildcard=text
raw=owner/42
rawPlain=owner
nested=static
~~~

### Vì sao mỗi dòng có kiểu như vậy?

- `typed` là `Outer<String>` nên `inner.ownerLabel()` được trình biên dịch nhìn như trả `String`. `inner` là `Inner<Integer>` nên `payload()` trả `Integer`.
- `PlainMember` không có tham số riêng; nó vẫn nhìn thấy T từ `Outer<String>` nên `plain.ownerLabel()` trả `String`.
- Với `Outer<?>.Inner<?>`, kiểu T và U chưa xác định; có thể đọc kết quả dưới dạng `Object`, nhưng vẫn giữ cơ chế kiểm tra generic theo wildcard.
- `rawOuter` có kiểu raw `Outer` dù đối tượng runtime vẫn chính là đối tượng đã tạo cho biến `typed`. Thành viên `Inner` qua raw Outer cũng raw. Do đó cả `ownerLabel()` và `payload()` được nhìn theo kiểu đã xóa, ở ví dụ này là `Object`.
- `Outer.PlainMember` dùng raw outer, nên getter phụ thuộc T cũng được nhìn sau erasure là `Object`.
- `StaticNested<String>` là một generic type hoàn chỉnh của **static member class**; không có đối tượng outer, và type argument String độc lập với T.

Lưu ý: việc chương trình biên dịch và chạy với raw types **không chứng minh raw an toàn**. Đây là ví dụ chủ ý để làm rõ vị trí thông tin kiểu đã mất.

## 5. Tại sao Outer.Inner<Integer> bị từ chối?

Xét lại:

~~~java
class Outer<T> {
    class Inner<U> {
        T ownerValue;
        U value;
    }
}
~~~

Dòng sau là **cú pháp kiểu không hợp lệ**:

~~~java
// Outer.Inner<Integer> invalid;
~~~

`Outer` là raw. Theo quy tắc raw member type, `Inner` đi theo raw Outer trong trường hợp này; Java không cho phép “khôi phục lại” chỉ tham số U thành Integer trong khi vẫn bỏ tham số T của outer.

Khi thử trong Java 17+, javac có thể báo:

~~~text
improperly formed type, type arguments given on a raw type
~~~

Cách sửa là tham số hóa outer và inner đầy đủ:

~~~java
Outer<String>.Inner<Integer> valid;
~~~

Nếu chưa biết kiểu T của outer, wildcard cũng có thể giữ cấu trúc tham số hóa:

~~~java
Outer<?>.Inner<Integer> validWithUnknownOuter;
~~~

Dòng khai báo phía trên hợp lệ về mặt kiểu; cần một đối tượng `Outer<?>` tương ứng và cú pháp `outerInstance.new Inner<>(...)` để khởi tạo vì Inner là non-static. Không thể tạo inner tùy ý mà bỏ đối tượng outer liên kết.

### Trường hợp chỉ biết U nhưng không biết T

Đừng chọn raw Outer để lách lỗi. Có thể dùng:

~~~java
Outer<?> anyOuter = new Outer<String>("owner");
Outer<?>.Inner<Integer> value = anyOuter.new Inner<>(99);
Object ownerPart = value.ownerLabel();
Integer innerPart = value.payload();
~~~

Phần T chưa biết được biểu diễn bằng wildcard; U vẫn rõ ràng là Integer. `ownerPart` chỉ đọc được an toàn như Object, còn `innerPart` là Integer. Đây là sự khác biệt lớn giữa **không biết type argument** và **bỏ hẳn hệ thống tham số kiểu bằng raw**.

## 6. Tại sao Outer<String>.Inner cũng bị từ chối?

Dòng sau cũng không hợp lệ với generic Inner<U>:

~~~java
// Outer<String>.Inner invalid;
~~~

Một outer đã tham số hóa không được kết hợp với inner raw ở vị trí kiểu này. Javac thường chẩn đoán:

~~~text
improperly formed type, some parameters are missing
~~~

Lập trình viên có thể muốn biểu diễn “outer chắc chắn chứa String, nhưng tôi chưa biết kiểu U”. Cú pháp thích hợp là **wildcard**:

~~~java
Outer<String>.Inner<?> validUnknownInner;
~~~

Nếu đã biết U là Integer:

~~~java
Outer<String>.Inner<Integer> validKnownInner;
~~~

Nếu cần xử lý nhiều kiểu U khác nhau, có thể khai báo phương thức nhận `Outer<String>.Inner<?>` và đọc `payload()` như Object, đồng thời giữ quan hệ String của owner.

**Không dùng raw type chỉ để thể hiện “không biết”.** `?` chính là cách diễn đạt sự không biết mà vẫn chịu các quy tắc kiểm tra của generics.

## 7. Vì sao lớp member không generic vẫn có thể bị ảnh hưởng?

Một ví dụ tinh tế:

~~~java
class Outer<T> {
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    class PlainInner {
        T readOuter() {
            return value;
        }
    }
}
~~~

Bản thân `PlainInner` không có tham số kiểu riêng, nhưng nó **dùng T của Outer**.

~~~java
Outer<String> typed = new Outer<>("hello");
Outer<String>.PlainInner a = typed.new PlainInner();
String x = a.readOuter();
~~~

Cách viết này hợp lệ và giữ được String nhờ qualifier `Outer<String>`.

Khi outer bị dùng raw:

~~~java
Outer raw = typed;
Outer.PlainInner b = raw.new PlainInner();
Object y = b.readOuter();
~~~

Giá trị runtime có thể vẫn là String, nhưng kiểu compile-time của kết quả `readOuter()` qua raw member là Object do mất quan hệ generic với outer.

Điều này cho thấy raw member class không chỉ xuất hiện khi chính inner khai báo `<U>`. Quy tắc liên quan đến **member type của raw enclosing class**, không chỉ đến số type parameters nằm ngay sau tên inner.

## 8. Static nested class là trường hợp khác

Với:

~~~java
class Outer<T> {
    static class Nested<V> {
        V value;

        Nested(V value) {
            this.value = value;
        }
    }
}
~~~

`Nested<V>` là static. Nó không có enclosing instance của `Outer<T>` và không thể trực tiếp dùng biến kiểu T của Outer làm một thành viên instance phụ thuộc T, vì static member không gắn với tham số hóa của outer.

Cách tham số hóa đúng:

~~~java
Outer.Nested<String> a = new Outer.Nested<>("text");
Outer.Nested<Integer> b = new Outer.Nested<>(123);
~~~

`Outer` ở đây là **tên đủ điều kiện của static nested class**, không biểu thị rằng outer instance raw được dùng để tạo inner instance. `Nested<String>` vẫn là parameterized type đầy đủ của chính nested class.

Cú pháp sau bị từ chối:

~~~java
// Outer<String>.Nested<Integer> bad; // Không hợp lệ.
~~~

javac có thể báo:

~~~text
cannot select a static class from a parameterized type
~~~

Vì static Nested không cần outer instance và không phụ thuộc vào T, bạn không được gắn `Outer<String>` vào tên của nó theo cách trên.

Một non-generic static nested class như `Outer.Helper` là kiểu không generic nếu chính Helper không khai báo type parameter. Không nên áp quy tắc raw non-static member lên static nested class chỉ vì nó xuất hiện bên trong một class generic.

## 9. Raw outer và lời gọi constructor

Khi có biến outer đã tham số hóa:

~~~java
Outer<String> outer = new Outer<>("label");
Outer<String>.Inner<Integer> inner = outer.new Inner<>(42);
~~~

Cú pháp `outer.new Inner<>(42)` thực hiện hai việc:

1. Liên kết đối tượng Inner vừa tạo với **đối tượng outer** cụ thể.
2. Suy luận type argument U nhờ constructor hoặc ngữ cảnh đích; ở đây U là Integer.

Diamond `<>` không tạo raw type. Nó biểu thị **suy luận type arguments**.

Ngược lại:

~~~java
Outer raw = new Outer("label");
Outer.Inner inner = raw.new Inner(42);
~~~

Ví dụ này dùng raw Outer và raw Inner; javac có thể báo rawtypes/unchecked. Phần `(42)` không khiến compiler tự nâng `Inner` thành `Inner<Integer>`, vì nó được gọi thông qua raw enclosing type.

Chính sự khác biệt giữa **raw construction** và **diamond construction** dễ khiến người đọc nhầm rằng compiler lúc nào cũng tự suy luận được U từ đối số constructor. Khi enclosing type đã raw, phải tuân thủ giới hạn raw member type.

## 10. Đọc đúng lỗi và các lựa chọn sửa

| Mục tiêu | Viết dễ sai | Cách biểu diễn đúng |
| --- | --- | --- |
| Outer String, Inner Integer | `Outer<String>.Inner<Integer>` | Giữ nguyên, đã hợp lệ. |
| Outer chưa biết T, Inner Integer | `Outer.Inner<Integer>` | `Outer<?>.Inner<Integer>` |
| Outer String, Inner chưa biết U | `Outer<String>.Inner` | `Outer<String>.Inner<?>` |
| Cả T và U đều chưa biết | `Outer.Inner` nếu chỉ cần quan sát dữ liệu | Ưu tiên `Outer<?>.Inner<?>` |
| Member không có U riêng | Nhầm `Outer<String>.PlainMember` là thiếu type arguments | `Outer<String>.PlainMember` hợp lệ. |
| Static nested generic | `Outer<String>.Nested<Integer>` | `Outer.Nested<Integer>` |

Dòng “cả T và U chưa biết” đáng chú ý: raw `Outer.Inner` vẫn là tên kiểu có thể biên dịch trong một số đoạn mã, nhưng vì thiếu thông tin generic, nó không phải lựa chọn an toàn cho API mới. `Outer<?>.Inner<?>` giúp thể hiện rõ sự không biết của từng lớp tham số và giữ lại các ràng buộc.

### Vì sao không cast raw Inner sang kiểu tham số hóa rồi dùng?

Ép kiểu unchecked không thể phục hồi thông tin kiểu bị bỏ đi một cách đáng tin:

~~~java
// Giả sử rawInner là Outer.Inner:
@SuppressWarnings("unchecked")
Outer<String>.Inner<Integer> guessed =
        (Outer<String>.Inner<Integer>) rawInner;
~~~

Đây là **mảnh mã minh họa** và không độc lập để chạy. Runtime thông thường không thể kiểm tra đầy đủ cả T = String và U = Integer của đối tượng generic. Nếu dữ liệu thực tế không khớp, lỗi có thể xuất hiện ở chỗ đọc một trường hoặc gọi phương thức sau đó.

Hướng ưu tiên: giữ type arguments đúng ngay tại lúc tạo outer/inner; nếu nhận dữ liệu raw từ API cũ, giới hạn nó ở ranh giới tích hợp và xác minh đối tượng/dữ liệu cần sử dụng.

## 11. Type erasure và nguy cơ heap pollution

Raw type không có nghĩa JVM tạo một bản class hoàn toàn khác cho mỗi giá trị type parameter. Đối với generic thông thường, phần lớn tham số kiểu được xóa khi biên dịch.

Ở ví dụ:

~~~java
class Outer<T> {
    class Inner<U> {
        T owner();
        U value();
    }
}
~~~

(Đây là minh họa chữ ký; một chương trình Java độc lập cần cung cấp phần thân phương thức hoặc khai báo phù hợp.)

Khi T và U đều không có bound cụ thể, descriptor trả về sau erasure của các phương thức đó là Object. Nhưng đối với biến có kiểu `Outer<String>.Inner<Integer>`, compiler vẫn dùng generic signature để kiểm tra dữ liệu tương ứng String/Integer và có thể chèn cast khi cần.

Khi dùng raw `Outer.Inner`, compiler không còn thông tin T/U tương ứng. Một chương trình có thể vô tình gắn dữ liệu không phù hợp với khai báo parameterized được giữ ở nơi khác. Rủi ro là **heap pollution** hoặc `ClassCastException` khi dữ liệu được đọc theo kiểu cụ thể.

**Không nhầm nguyên nhân và kết quả:** bản thân việc có raw reference chưa chắc đã gây lỗi ngay. Vấn đề phát sinh khi đường raw cho phép những thao tác mà compiler không thể chứng minh an toàn; lỗi có thể xuất hiện muộn ở call site khác.

## 12. Phân biệt wildcard, raw và Object

Ba cách thể hiện chưa biết thông tin generic khác nhau:

~~~java
Outer.Inner rawMember;                 // Raw member.
Outer<?>.Inner<?> unknownMember;      // Hai wildcard.
Outer<Object>.Inner<Object> objectMember; // T và U đều Object.
~~~

- **Raw**: bỏ type arguments của các generic types liên quan; những thao tác cần kiểm tra T/U có thể sinh cảnh báo unchecked.
- **Wildcard**: biết đối tượng có dạng generic với type arguments nào đó nhưng không biết chính xác; compiler giới hạn thao tác ghi một cách phù hợp.
- **Object arguments**: khẳng định T và U chính là Object; vẫn là parameterized type và vẫn chịu kiểm tra theo Object.

`Outer<String>.Inner<Integer>` không tự chuyển thành `Outer<Object>.Inner<Object>`. Các generic types Java nhìn chung **invariant**. Wildcard có thể nhận những kiểu tham số hóa khác nhau theo quy tắc tương thích; Object không thay thế được wildcard một cách máy móc.

Nếu mục tiêu chỉ đọc giá trị từ một đối tượng inner bất kỳ, một API dùng wildcard thường diễn đạt ý định rõ hơn raw hoặc ép thành Object parameters.

## 13. Các sai lầm thường gặp trong code review

### Sai lầm A: chỉ thêm <Integer> sau một raw inner

~~~java
// Outer.Inner<Integer> fixed; // Vẫn sai.
~~~

Nếu outer thiếu type argument, phải xử lý qualifier của outer trước. Dùng `Outer<?>.Inner<Integer>` khi T chưa biết hoặc `Outer<String>.Inner<Integer>` khi đã biết T = String.

### Sai lầm B: dùng Outer<String>.Inner mà nghĩ “Inner tự hiểu U = Object”

~~~java
// Outer<String>.Inner value; // Không hợp lệ.
~~~

Nếu thật sự muốn U = Object, phải viết `Outer<String>.Inner<Object>`. Nếu chưa biết U, dùng `Outer<String>.Inner<?>`. Không thể bỏ ngoặc kiểu ở mức Inner<U> rồi trông chờ compiler hiểu thành Object.

### Sai lầm C: coi PlainMember là generic vì nó nằm trong Outer<T>

`PlainMember` không khai báo U riêng. `Outer<String>.PlainMember` đúng, trong khi `Outer<String>.PlainMember<Integer>` không đúng nếu PlainMember không khai báo type parameter. Nó vẫn dùng được T của outer thông qua ngữ cảnh.

### Sai lầm D: coi static nested class như inner class

`Outer.Nested<Integer>` không có enclosing instance. `Outer<String>.Nested<Integer>` không phù hợp vì static Nested độc lập với tham số T của Outer.

### Sai lầm E: dùng raw để giải quyết mọi mismatch

Raw member có thể làm một số đoạn code cũ biên dịch kèm warning, nhưng đánh đổi kiểm tra kiểu. Cần xem lại type arguments, mô hình dữ liệu và cách sử dụng wildcard/generic method thay vì lạm dụng raw và suppress toàn bộ cảnh báo.

### Sai lầm F: nhầm quy tắc trực tiếp với mọi trường hợp thừa kế

Các ví dụ trong bài dùng member classes **khai báo trực tiếp** trong Outer. Khi một member type xuất hiện qua lớp con do kế thừa, quy tắc về tên kiểu và raw member phải xét **nơi member được khai báo**. Không suy luận toàn bộ chỉ từ tên lớp đứng bên trái dấu chấm.

## 14. Cách thiết kế API tránh raw inner

Khi thiết kế một API với outer/inner generic, nên:

1. Đặt kiểu rõ ràng ở cả outer và inner khi chúng thật sự mang hai tập dữ liệu có kiểu độc lập.
2. Cung cấp factory hoặc phương thức truy cập phù hợp nếu syntax `outer.new Inner<>(...)` khiến người sử dụng phải lặp lại kiểu quá nhiều, nhưng không giấu mối quan hệ với outer instance.
3. Dùng wildcard ở chữ ký nhận dữ liệu khi không biết T hoặc U nhưng vẫn cần giữ kiểm tra generic.
4. Chọn static nested class khi thành viên không cần truy cập trạng thái hoặc type parameter của outer instance.
5. Giữ raw type ở biên tích hợp legacy code, ưu tiên viết adapter kiểm tra dữ liệu thay vì để raw lan sang nghiệp vụ.
6. Bật `javac -Xlint:rawtypes -Xlint:unchecked` khi rà soát code liên quan generic; xem warning là dấu hiệu cần hiểu và xử lý.
7. Phân biệt tên kiểu hợp lệ với khả năng tạo đối tượng: non-static inner cần đối tượng outer, trong khi static nested class không cần.

Không phải cứ có Outer<T> là tất cả lớp nằm bên trong đều phải có tham số generic. Hãy chọn non-static inner, static nested hay một top-level class dựa trên **quan hệ dữ liệu thật sự cần**, sau đó áp dụng quy tắc type arguments tương ứng.

## 15. Câu hỏi tự kiểm tra

**1.** Với `class Outer<T> { class Inner<U> {} }`, tại sao `Outer.Inner<Integer>` không hợp lệ?

**Đáp án:** Outer đang ở dạng raw; non-static member Inner được dùng trong ngữ cảnh raw và không thể chỉ tham số hóa U trong khi qualifier Outer thiếu T. Dùng `Outer<?>.Inner<Integer>` hoặc một Outer có T cụ thể.

**2.** Vì sao `Outer<String>.Inner` bị từ chối dù đã biết T của outer?

**Đáp án:** Inner vẫn có type parameter U; một raw Inner trong qualifier Outer<String> là tổ hợp tham số hóa một phần không hợp lệ. Dùng `Inner<?>` nếu chưa biết U hoặc `Inner<Integer>` nếu biết.

**3.** `Outer<String>.PlainInner` có hợp lệ nếu PlainInner không khai báo U riêng không?

**Đáp án:** Có. PlainInner không có type parameter riêng để bỏ, và T của Outer đã được xác định là String.

**4.** `Outer.PlainInner` có thể bị ảnh hưởng bởi raw type dù PlainInner không generic không?

**Đáp án:** Có, với một non-static member class khai báo trực tiếp trong raw Outer; những thành viên của PlainInner phụ thuộc T được nhìn qua erasure.

**5.** Vì sao `Outer.Nested<String>` là cú pháp đúng với static generic nested class?

**Đáp án:** Nested là static và không gắn với một đối tượng hoặc T của Outer; type parameter String thuộc chính Nested<V>. Không được chọn static class bằng `Outer<String>.Nested`.

**6.** `Outer<?>.Inner<?>` có phải raw type không?

**Đáp án:** Không. Cả hai vị trí đều có type argument là wildcard; đây là parameterized types, và compiler vẫn kiểm tra các ràng buộc generic phù hợp.

**7.** Diamond trong `typed.new Inner<>(7)` có biến Inner thành raw không?

**Đáp án:** Không. Diamond yêu cầu suy luận U; với outer được tham số hóa đầy đủ và initializer phù hợp, inner có type argument đúng.

**8.** Nếu một lời gọi bị javac cảnh báo unchecked khi dùng raw Outer.Inner, điều đó có nghĩa chương trình chắc chắn ném exception không?

**Đáp án:** Không. Warning có nghĩa compiler không chứng minh đầy đủ an toàn kiểu; chương trình có thể chạy, nhưng nguy cơ lỗi hoặc heap pollution tăng lên khi dữ liệu không đúng kiểu thực sự được đưa vào và sử dụng.

## 16. Ghi nhớ

Trong cây generic có non-static member class, **tên outer và inner tạo thành một ngữ cảnh tham số kiểu liên quan**. Khi inner generic được khai báo trực tiếp trong outer generic:

- `Outer<T>.Inner<U>` mô tả hai tham số kiểu rõ ràng.
- `Outer.Inner` mô tả cách dùng raw liên quan.
- `Outer.Inner<U>` và `Outer<T>.Inner` là hai dạng tham số hóa một phần không hợp lệ trong cấu trúc này.
- `Outer<?>.Inner<?>` cho phép thể hiện hai kiểu chưa biết mà không bỏ kiểm tra generics.
- `Outer<T>.PlainInner` hợp lệ nếu member không có type parameter U riêng.
- `Outer.Nested<V>` của static nested class có quy tắc độc lập vì không gắn với một enclosing instance.

Khi gặp lỗi `improperly formed type`, hãy kiểm tra **cả qualifier của outer lẫn generic declaration của member class**, rồi thay raw/missing type arguments bằng kiểu cụ thể hoặc wildcard theo đúng thông tin hiện có.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="raw-types-and-legacy-interop-008">Di chuyển API cũ sang generics theo từng ranh giới</a>

<details>
<summary>Xem chi tiết</summary>

# Di chuyển API cũ sang generics theo từng ranh giới

## Mục tiêu và bối cảnh

Một hệ thống Java lâu năm có thể chứa những API được viết trước khi generics xuất hiện trong Java 5: phương thức trả `List` không có đối số kiểu, nhận `Collection` dạng thô, hoặc nhận và trả `Object`. Các API ấy vẫn chạy, nhưng compiler không thể bảo đảm phần tử được sử dụng đúng loại dữ liệu. Mục tiêu khi di trú là **đưa thông tin kiểu và kiểm tra dữ liệu vào đúng điểm chuyển giao giữa mã cũ và mã mới**, rồi mở rộng phạm vi mã an toàn theo từng bước.

Sau bài này, bạn có thể:

- Xác định **ranh giới** (boundary) giữa thư viện/mô-đun legacy và phần ứng dụng đang dùng generics.
- Chọn cách thay `List` bằng `List<?>`, `List<T>`, `List<User>` hoặc wrapper/adapter đúng mục đích.
- Tạo một **adapter typed** nhận dữ liệu raw, kiểm tra từng phần tử, trả kết quả `List<User>` không chia sẻ trạng thái sửa đổi với tập dữ liệu cũ.
- Bọc API kiểu `Object` bằng `Class<T>` khi cần kiểm tra kiểu lúc chạy.
- Biết cảnh báo `rawtypes` và `unchecked` nên được khoanh vùng ở đâu và khi nào một cast không đủ để bảo đảm an toàn.
- Lập thứ tự di trú theo từng phạm vi cùng chiến lược kiểm tra tương thích source, binary và hành vi.

Bài tập trung vào **chiến lược di trú theo ranh giới**, không yêu cầu viết lại toàn bộ hệ thống cùng lúc. Các ví dụ sử dụng Java 17 trở lên và được biên dịch, chạy để kiểm tra output.

## 1. Vì sao không nên đổi toàn bộ raw type bằng tìm và thay thế?

Xét một API cũ:

```java
interface LegacyRepository {
    java.util.List findAll();
    void save(Object value);
}
```

Chương trình mới muốn làm việc với `User` thay vì mọi `Object`. Có thể nảy sinh ý định sửa trực tiếp thành:

```java
interface UserRepository {
    java.util.List<User> findAll();
    void save(User user);
}
```

Hai khai báo là **minh họa hợp đồng**, phải có lớp `User` để biên dịch độc lập. Đây là hai API khác nhau về cam kết dữ liệu và kiểu tham số của `save`; không thể mặc nhiên coi việc đổi chữ ký là an toàn cho mọi caller.

Những rủi ro trước khi thay:

- Dữ liệu thật do `LegacyRepository.findAll()` trả về có thể gồm `User`, kiểu dữ liệu cũ khác, thậm chí `null`.
- Mã cũ có thể gọi `save(Object)` với các loại đối tượng khác `User`; đổi thành `save(User)` sẽ ảnh hưởng source compatibility.
- Các implementation và module đã biên dịch có thể dựa vào descriptor cũ; với `save(Object)` đổi sang `save(User)`, **descriptor thay đổi**, cần kiểm tra binary compatibility.
- Nếu chỉ thực hiện `(List<User>) legacy.findAll()`, cast không kiểm tra từng phần tử của danh sách vì **type erasure**.
- Một collection mà mã cũ vẫn giữ quyền ghi có thể bị sửa sau thời điểm cast, phá giả định của phần mã mới.

Điểm xuất phát tốt hơn là xác định: **đầu vào nào chưa đáng tin về kiểu, dữ liệu có thể bị ai sửa, và hợp đồng mới cần bảo đảm điều gì**.

## 2. Ranh giới là gì? Nhìn theo hướng di chuyển dữ liệu

Một ranh giới xuất hiện tại điểm một giá trị đi từ vùng thiếu kiểm soát kiểu sang vùng có hợp đồng generic rõ ràng.

```text
        Mã/thư viện legacy
        (raw List, Object, dữ liệu hỗn hợp)
                 |
                 v
       Ranh giới chuyển đổi
       - kiểm tra phần tử
       - đổi biểu diễn nếu cần
       - sao chép/tách quyền sửa
       - báo lỗi có ngữ cảnh
                 |
                 v
          API có generics
       (List<User>, UserRepository)
                 |
                 v
           Business logic
       (chỉ cần dùng User)
```

Một chương trình có thể có nhiều ranh giới: thư viện bên thứ ba, driver cũ, API import dữ liệu, callback, hàng đợi hoặc API trả về `Object`. **Di trú từng ranh giới** nghĩa là hoàn thiện kiểm tra và hợp đồng cho một đường dữ liệu trước, để các lớp sử dụng phía sau không tiếp tục đoán/cast rải rác.

Khi xác định ranh giới, ghi lại:

| Câu hỏi | Điều cần biết |
| --- | --- |
| Ai sở hữu API cũ? | Có thể sửa source hay chỉ có thể viết adapter? |
| Dữ liệu thực gồm những loại nào? | Một kiểu duy nhất, nhiều kiểu hay không rõ schema? |
| Có cho phép `null` không? | Cần giữ, loại bỏ hoặc từ chối `null` rõ ràng |
| Collection có thể bị sửa sau khi trả về? | Nếu có, cần xem xét bản sao/snapshot |
| Caller cần đọc hay ghi? | Đọc có thể dùng wildcard; ghi cần hợp đồng cụ thể |
| Khi dữ liệu sai, xử lý thế nào? | Fail-fast, báo lỗi kèm vị trí, hay thu thập lỗi |
| Có caller/binary cũ không? | Cần adapter song song, migration plan hoặc kiểm tra tương thích |

Việc bắt đầu từ **dữ liệu và hợp đồng** trước khi sửa cú pháp giúp tránh cảnh báo đã biến mất nhưng nguy cơ runtime vẫn còn.

## 3. Kiểu raw, kiểu wildcard và kiểu typed ở hai phía ranh giới

Ba cách khai báo sau không tương đương:

```java
java.util.List raw = obtainLegacyList();
java.util.List<?> unknown = raw;
java.util.List<User> users = convertAndCheck(unknown);
```

Đây là mã **minh họa luồng chuyển đổi**: `obtainLegacyList()` và `convertAndCheck(...)` tượng trưng các phương thức do ứng dụng cung cấp, còn `User` là kiểu nghiệp vụ.

- **`List`**: raw type, bỏ đối số generic. Có thể gọi `add(...)` qua tham chiếu raw với cảnh báo unchecked.
- **`List<?>`**: danh sách có kiểu phần tử chưa biết; an toàn để duyệt dưới dạng `Object` và hạn chế việc đưa giá trị không phải `null` vào.
- **`List<User>`**: lời hứa mà compiler hiểu là mỗi phần tử được trả về dưới dạng `User`. Muốn xây lời hứa này từ nguồn raw, phải có cơ sở kiểm tra thực sự.

Nếu nhiệm vụ chỉ là hiển thị phần tử, `List<?>` có thể là đích cuối của việc cải thiện API:

```java
static void printAnything(java.util.List<?> input) {
    for (Object value : input) {
        System.out.println(value);
    }
}
```

Đoạn trên là phương thức đặt trong một lớp Java. Nó không phải `List<Object>`: Java generics bất biến, `List<String>` **không** tự chuyển thành `List<Object>`. Dùng `List<Object>` khi API cần một danh sách mà hợp đồng chủ động cho phép chứa nhiều loại `Object`; dùng `List<?>` khi chưa biết loại phần tử và chủ yếu cần **đọc**.

## 4. Ví dụ hoàn chỉnh: adapter kiểm tra từng phần tử trước khi trả API typed

Giả sử bạn có `LegacyDirectory` nhận `Object` và trả raw `List`; không thể sửa thư viện đó trong giai đoạn đầu. Mã mới muốn làm việc với `UserDirectory.users(): List<User>`. Ta xây **adapter** nằm giữa.

Tạo tệp **`MigrationBoundaryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MigrationBoundaryDemo {
    // Giả lập thư viện cũ: không được sửa hoặc chưa thể đổi API.
    static final class LegacyDirectory {
        private final List entries = new ArrayList();

        void add(Object entry) {
            entries.add(entry);
        }

        List findAll() {
            return entries;
        }
    }

    record User(String id, String name) {
        User {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }
    }

    interface UserDirectory {
        List<User> users();
    }

    static List<User> checkedUsers(List<?> source) {
        Objects.requireNonNull(source);
        List<User> result = new ArrayList<>();

        for (int index = 0; index < source.size(); index++) {
            Object item = source.get(index);
            if (!(item instanceof User user)) {
                String type = item == null
                        ? "null"
                        : item.getClass().getName();
                throw new IllegalArgumentException(
                        "Unexpected entry at index " + index + ": " + type);
            }
            result.add(user);
        }
        return List.copyOf(result);
    }

    static final class LegacyDirectoryAdapter implements UserDirectory {
        private final LegacyDirectory legacy;

        LegacyDirectoryAdapter(LegacyDirectory legacy) {
            this.legacy = Objects.requireNonNull(legacy);
        }

        @Override
        public List<User> users() {
            return checkedUsers(legacy.findAll());
        }
    }

    public static void main(String[] args) {
        LegacyDirectory old = new LegacyDirectory();
        old.add(new User("u1", "Lan"));
        old.add(new User("u2", "Minh"));

        UserDirectory directory = new LegacyDirectoryAdapter(old);
        List<User> snapshot = directory.users();
        System.out.println("users: " + snapshot);

        old.add("wrong format");
        try {
            directory.users();
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }

        System.out.println("previous snapshot size: " + snapshot.size());
    }
}
```

Từ thư mục chứa tệp:

```bash
javac -Xlint:rawtypes -Xlint:unchecked MigrationBoundaryDemo.java
java MigrationBoundaryDemo
```

**Stdout đã kiểm chứng trên Java:**

```text
users: [User[id=u1, name=Lan], User[id=u2, name=Minh]]
rejected: Unexpected entry at index 2: java.lang.String
previous snapshot size: 2
```

Các cảnh báo compiler được dự kiến ở vùng `LegacyDirectory` do dùng raw `List`, raw `ArrayList` và `entries.add(entry)`. Chúng xuất hiện trong đầu ra của `javac`, không phải stdout của lệnh `java`.

### Giải thích theo từng lớp

**`LegacyDirectory`:** giữ API cũ, cho phép lưu các `Object`. Trong ứng dụng thực tế, đó có thể là lớp trong JAR bên ngoài hoặc nguồn đã triển khai ở nhiều hệ thống. Không đưa `@SuppressWarnings` vào mẫu để người học nhìn rõ cảnh báo.

**`User`:** dùng `record` Java 17 và từ chối field `null`. Điều này làm cho `List<User>` ở phía hiện đại có một kiểu domain rõ ràng.

**`checkedUsers(List<?>)`:** nhận danh sách chưa biết kiểu phần tử. Từng đối tượng được kiểm tra `instanceof User`. Giá trị sai được từ chối bằng lỗi có **vị trí** và **kiểu thực tế**; `null` cũng bị từ chối. Vì mọi phần tử đã qua kiểm tra, phương thức có thể đưa các `User` ấy vào `ArrayList<User>` mà không cần unchecked cast.

**`List.copyOf(result)`:** tạo danh sách không thể sửa qua API thông thường và tách khỏi list đầu vào. Vì `User` là record chỉ giữ `String` bất biến, mẫu ví dụ có một snapshot phù hợp; với một phần tử domain có state mutable, việc copy list không tự biến mọi phần tử thành immutable.

**`LegacyDirectoryAdapter`:** là điểm duy nhất mã mới cần biết đến `LegacyDirectory`. Caller sử dụng `UserDirectory` và không thấy raw `List`.

**Lời gọi thứ hai:** sau khi thư viện cũ thêm một `String` không hợp đồng, lần gọi adapter tiếp theo phát hiện ngay ở index 2. Danh sách `snapshot` đã trả trước đó không bị thêm phần tử vì đã tách dữ liệu.

Ví dụ cố ý viết API cũ và adapter trong cùng một tệp để chạy dễ dàng; ở hệ thống thực, thư viện cũ và adapter thường ở các package/module khác nhau.

## 5. Vì sao không cast thẳng `List` thành `List<User>`?

Một “sửa nhanh” nguy hiểm:

```java
// Ví dụ cần ngữ cảnh có legacyDirectory và lớp User.
// @SuppressWarnings("unchecked")
// List<User> users = (List<User>) legacyDirectory.findAll();
```

Nếu bỏ suppression, compiler thường báo **unchecked cast**. Lý do: do erasure, JVM biết đối tượng là một `List`, nhưng không xác minh được **mọi phần tử** là `User`. Việc cast có thể hoàn tất mà chưa phát hiện một `String` nằm trong danh sách.

Lỗi thường đến sau đó:

```java
// for (User user : users) {
//     System.out.println(user.name()); // Có thể ClassCastException.
// }
```

Trong vòng lặp trên, trình biên dịch có thể sinh cast khi đọc từng phần tử theo giả định `User`. Đây là một dạng **heap pollution**: thực trạng dữ liệu trong đối tượng collection không còn khớp với kiểu generic mà biến đang hứa.

**Kiểm tra từng phần tử rồi tạo list mới** phù hợp khi miền dữ liệu nhỏ, cần type safety và hợp đồng rõ. Nếu dữ liệu rất lớn hoặc streaming, có thể thiết kế validator xử lý từng record, thu thập lỗi/đẩy sang pipeline, hoặc cung cấp view typed có kiểm tra khi đọc; lựa chọn phải làm rõ thời điểm phát hiện lỗi và quyền thay đổi dữ liệu.

Cũng không thể cho rằng:

```java
if (candidate instanceof List<?>) {
    // candidate là List, chưa có bằng chứng tất cả phần tử là User.
}
```

là đủ để trả `List<User>`. `instanceof List<?>` chỉ kiểm tra **kiểu collection ở runtime**; thông tin `User` là đối số kiểu không tồn tại dưới dạng một runtime class `List<User>` chuyên biệt.

## 6. Ranh giới cho API trả `Object`: dùng type token khi thích hợp

Không phải API cũ nào cũng trả collection. Một hệ thống cấu hình có thể dùng `Object read(String key)`, `void write(String key, Object value)`. Mã mới muốn đọc một giá trị cụ thể và phát hiện sai kiểu ngay tại cửa ngõ.

Tạo tệp **`TypedGatewayDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TypedGatewayDemo {
    static final class LegacySettings {
        private final Map<String, Object> values = new HashMap<>();

        Object read(String key) {
            return values.get(key);
        }

        void write(String key, Object value) {
            values.put(key, value);
        }
    }

    static final class SettingsGateway {
        private final LegacySettings legacy;

        SettingsGateway(LegacySettings legacy) {
            this.legacy = Objects.requireNonNull(legacy);
        }

        <T> T read(String key, Class<T> type) {
            Objects.requireNonNull(type);
            return type.cast(legacy.read(key));
        }

        <T> void write(String key, Class<T> type, T value) {
            Objects.requireNonNull(type);
            legacy.write(key, type.cast(value));
        }
    }

    public static void main(String[] args) {
        LegacySettings old = new LegacySettings();
        SettingsGateway gateway = new SettingsGateway(old);

        gateway.write("retries", Integer.class, 3);
        Integer retries = gateway.read("retries", Integer.class);
        System.out.println("retries: " + retries);

        old.write("retries", "three");
        try {
            gateway.read("retries", Integer.class);
        } catch (ClassCastException ex) {
            System.out.println("rejected: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac TypedGatewayDemo.java
java TypedGatewayDemo
```

**Stdout đã kiểm chứng:**

```text
retries: 3
rejected: ClassCastException
```

Phân tích:

- Adapter nhận `Class<T>` tường minh, ví dụ `Integer.class`.
- `Class.cast(Object)` thực hiện **kiểm tra kiểu tại runtime**. Khi giá trị thật là `String` nhưng caller yêu cầu `Integer`, lỗi xảy ra tại ranh giới adapter.
- Generic `<T> T read(...)` cho phép caller nhận kết quả typed, còn token quyết định việc kiểm tra lúc chạy.
- `Class.cast(null)` trả về `null`, nên trong API thật cần định nghĩa rõ quy tắc cho key thiếu/giá trị `null` (ví dụ trả `Optional<T>` hoặc ném ngoại lệ).
- `Class<T>` mô tả được kiểu runtime như `Integer` nhưng **không kiểm tra đầy đủ kiểu lồng nhau** `List<String>` chỉ nhờ `List.class`. Nếu API trả `List` bất kỳ, vẫn phải kiểm tra từng phần tử hoặc dùng biểu diễn type metadata phong phú hơn.

Mẫu này minh họa cách dùng generic method tại **một boundary** mà không buộc phải sửa chữ ký và dữ liệu của `LegacySettings`.

## 7. Di trú dần một API: trước, trong và sau

Hãy hình dung ba giai đoạn cùng tồn tại thay vì một thay đổi toàn cục.

| Giai đoạn | Bên legacy | Ranh giới | Bên consumer mới |
| --- | --- | --- | --- |
| Trước khi di trú | `List findAll()` | Chưa có | Cast rải rác, có nguy cơ lỗi muộn |
| Bước trung gian | Giữ API legacy | `checkedUsers(List<?>)` + `UserDirectory` | Chỉ dùng `List<User>` |
| Khi làm chủ được API nguồn | Có thể đưa generic vào lớp nội bộ theo hợp đồng thật | Adapter còn phục vụ caller cũ hoặc được thu gọn | Dữ liệu typed xuyên suốt |

**Bước 1 — Lập bản đồ caller/callee.** Xác định phương thức cũ, nơi tạo dữ liệu, nơi nhận và sửa collection. Đặc biệt phân biệt **quyền sở hữu** collection: một phương thức trả raw `List` được backing bởi state mutable sẽ khác phương thức trả snapshot.

**Bước 2 — Viết hợp đồng kiểu đích.** Chọn `User`, `List<User>` hoặc `List<?>` đúng với dữ liệu thật. Quyết định `null`, phần tử sai kiểu và hành vi báo lỗi. Không tự suy luận rằng mọi record cũ đều cùng loại.

**Bước 3 — Tạo điểm kiểm tra nhỏ.** Viết adapter chấp nhận raw API nhưng chỉ công bố typed API sau khi dữ liệu được xác minh. Có thể bắt lỗi bằng exception có ngữ cảnh hoặc trả danh sách các lỗi validation; lựa chọn theo nhu cầu nghiệp vụ.

**Bước 4 — Chuyển một nhóm caller sang typed interface.** Thay lời gọi và cast rải rác bằng dependency tới `UserDirectory`. Giữ vùng truy cập API cũ tập trung trong adapter.

**Bước 5 — Kiểm tra với cả dữ liệu đúng lẫn sai.** Dữ liệu trộn kiểu, `null`, danh sách rỗng, đổi dữ liệu sau khi trả về và khả năng concurrent mutation là các điều kiện quan trọng.

**Bước 6 — Di trú nguồn khi có quyền sở hữu.** Khi kiểm chứng được mọi nơi ghi và đọc, thay raw declarations bằng generic cụ thể ở lớp sở hữu dữ liệu; đánh giá ảnh hưởng đến các consumer cũ, subclass/interface và class file.

**Bước 7 — Thu hẹp vùng legacy.** Gỡ những adapter không còn cần khi tất cả caller chuyển xong; giữ adapter làm lớp tương thích nếu hệ thống vẫn phụ thuộc API trước đây.

Các giai đoạn phải có điểm kiểm tra và phạm vi rõ; việc đổi các hàng trăm khai báo `List` thành `List<User>` bằng replace sẽ khó phân biệt cải thiện thực sự với unchecked cast che lỗi.

## 8. Tính tương thích của chữ ký generic không chỉ là cú pháp

Java generics dùng **type erasure**, nên việc tham số hóa một số chữ ký có thể giữ nguyên JVM descriptor. Ví dụ:

```java
// Dạng cũ:
java.util.List findAll();

// Dạng khai báo generic:
java.util.List<User> findAll();
```

Hai khai báo trên có **cùng descriptor kiểu trả về** `()Ljava/util/List;` sau erasure. Nhưng không được kết luận từ đây rằng mọi thay đổi từ raw sang generic đều hoàn toàn tương thích ở mọi chiều:

- **Binary compatibility:** cần kiểm tra descriptor của từng method, tên, visibility, các quan hệ kế thừa/bridge và class đã biên dịch sẵn. Ví dụ đổi `save(Object)` thành `save(User)` thay descriptor từ `(Ljava/lang/Object;)V` sang `(L.../User;)V` và có thể khiến caller binary cũ không liên kết đúng.
- **Source compatibility:** code cũ có thể biên dịch với cảnh báo khi truyền raw type cho API typed, nhưng cũng có những thay đổi gây lỗi biên dịch do các quy tắc generic, wildcard, overload hoặc name clash.
- **Behavioral compatibility:** đổi kiểu khai báo không tự lọc hay chuyển dữ liệu đã lưu. Adapter có thể chủ động báo lỗi khi dữ liệu sai; thay đổi này phải được thống nhất trong hợp đồng.
- **Reflection compatibility:** framework đọc generic `Signature`, tên method hoặc annotation có thể quan sát khác biệt về metadata dù erased descriptor giống nhau.
- **Subclass/interface compatibility:** các phương thức generic có thể khiến compiler tạo **bridge method** khi override; cần biên dịch và kiểm tra theo quan hệ kế thừa thực tế.

Có thể tự quan sát một phần từ tệp `MigrationBoundaryDemo.java` đã biên dịch:

```bash
javap -s -p 'MigrationBoundaryDemo$LegacyDirectory'
javap -s -p 'MigrationBoundaryDemo$LegacyDirectoryAdapter'
```

Đoạn tiêu biểu từ kết quả đã kiểm tra:

```text
java.util.List findAll();
  descriptor: ()Ljava/util/List;

public java.util.List<MigrationBoundaryDemo$User> users();
  descriptor: ()Ljava/util/List;
```

Hai phương thức mang **tên khác nhau**, nên không thể coi chúng là hai phiên bản nhị phân của cùng một method; trích đoạn chỉ chứng minh **raw `List` và `List<User>` đều có return descriptor `Ljava/util/List;`**. Với một hệ thống thật, muốn xác nhận binary compatibility phải so sánh toàn bộ API hiện tại với bản đề xuất, bao gồm method name, tham số và hierarchy.

Tương tự, chạy:

```bash
javap -s -p 'TypedGatewayDemo$SettingsGateway'
```

cho thấy phương thức generic `<T> T read(String, Class<T>)` có dạng erased `(String, Class)Object`. Đây là lý do compiler vẫn phải dựa vào kiểm tra kiểu generic ở source cùng token `Class<T>` tại runtime.

## 9. Chọn chữ ký phù hợp theo hướng dữ liệu

Khi sửa API công khai, hãy quyết định tham số là **nguồn để đọc** hay **đích để ghi**. Wildcard không phải cách trang trí, mà thể hiện ràng buộc kiểu.

**Đọc phần tử như một kiểu cha** (*producer*):

```java
static double sumNumbers(java.util.List<? extends Number> source) {
    double sum = 0;
    for (Number number : source) {
        sum += number.doubleValue();
    }
    return sum;
}
```

Có thể truyền `List<Integer>`, `List<Double>` hoặc `List<Number>`; compiler không cho thêm `Number` tùy ý vào một nguồn có thể là `List<Integer>`. Phương thức là đoạn mã hoàn chỉnh về hành vi, cần đặt trong một class để chạy.

**Ghi một kiểu vào đích chấp nhận kiểu cha** (*consumer*):

```java
static void addLabels(java.util.List<? super String> target) {
    target.add("migrated");
    target.add("validated");
}
```

Có thể truyền `List<String>` hoặc `List<Object>`, vì cả hai có thể nhận `String`. Khi đọc từ `List<? super String>`, chỉ có thể bảo đảm kết quả dưới dạng `Object`.

Quy tắc **PECS** (Producer Extends, Consumer Super) có ích khi làm sạch API legacy có nhiều caller. Không cần thay mọi tham số thành `List<T>`: nếu method chỉ đọc, `List<?>` hoặc `List<? extends T>` thường thể hiện ý định rõ hơn; nếu ghi, hãy chỉ ra kiểu dữ liệu được phép ghi và cân nhắc `? super T`.

Đối với collection hỗn hợp từ nguồn cũ, một typed API như `List<User>` chỉ hợp lệ sau khi có bằng chứng về phần tử. PECS giúp diễn đạt hướng dùng dữ liệu, **không tự xác thực dữ liệu bên trong raw collection**.

## 10. Cảnh báo compiler: theo dõi có chủ đích khi di trú

Trong lúc chạy lệnh:

```bash
javac -Xlint:rawtypes -Xlint:unchecked MigrationBoundaryDemo.java
```

Compiler đã cảnh báo raw `List`, raw `ArrayList` và unchecked `entries.add(entry)` trong phần giả lập legacy. Hai nhóm cần đọc riêng:

| Nhóm cảnh báo | Ý nghĩa | Hành động ưu tiên |
| --- | --- | --- |
| `rawtypes` | Khai báo/tên generic bị dùng không có đối số kiểu | Thay bằng kiểu tham số hóa hoặc wildcard nếu biết hợp đồng |
| `unchecked` | Lời gọi/chuyển kiểu không được kiểm tra đầy đủ | Xác định nguồn dữ liệu, thêm bước xác minh hoặc giới hạn cast có chứng minh |

Trong ví dụ adapter `checkedUsers`, việc nhận `List<?>` và duyệt `Object` không tự tạo unchecked cast. Cảnh báo được giữ lại tại lớp cũ để **xác định chính xác khoản nợ kiểu** chứ không phân tán nó vào `UserDirectory`.

Nếu bạn sở hữu mã nguồn legacy, có thể loại bỏ cảnh báo từng điểm bằng cách thay raw field, constructor và method với generic đúng bản chất. Nếu mã cũ thuộc thư viện bên ngoài, không cần ép thư viện bên ngoài thay đổi ngay: ưu tiên một boundary typed đã kiểm tra.

Chỉ dùng `@SuppressWarnings("unchecked")` sau khi có lập luận tin cậy chứng minh tính đúng kiểu; đặt ở phạm vi nhỏ nhất có thể, kèm lý do và bài kiểm tra phù hợp. **Không dùng suppression như bước di trú thay cho kiểm tra dữ liệu.**

## 11. Chính sách `null`, copy và khả năng sửa của dữ liệu

Không thể coi “trả `List<User>`” là hợp đồng đầy đủ nếu chưa thống nhất các thuộc tính sau.

### `null`

Trong `MigrationBoundaryDemo`, `checkedUsers` từ chối `null` vì pattern `instanceof User` trả `false`. Nếu nghiệp vụ cho phép thiếu phần tử, hãy chọn một mô hình rõ ràng: giá trị mặc định, lọc bỏ có báo cáo, hoặc mô tả `Optional` ở lớp khác. `List.copyOf` cũng không chấp nhận phần tử `null`.

Với `SettingsGateway.read`, nếu key không tồn tại thì `LegacySettings.read` trả `null`; `Class.cast(null)` cho `null`. API production có thể cần phân biệt **key vắng mặt** với **giá trị đã tồn tại nhưng bằng null**.

### Alias và quyền sửa

Trả trực tiếp raw list từ legacy khiến mã typed có thể đang cầm một **alias** của collection mutable. Một legacy caller khác sửa danh sách có thể làm dữ liệu mới không còn đúng kiểu sau thời điểm bạn kiểm tra.

Tạo snapshot mới sau khi kiểm tra giúp loại bỏ việc chia sẻ **cấu trúc danh sách** trong trường hợp tuần tự của ví dụ. Nhưng nếu phần tử `User` có field mutable hoặc nguồn đang bị sửa đồng thời trong quá trình duyệt, cần cách quản lý quyền sở hữu, lock/snapshot hoặc dữ liệu bất biến sâu thích hợp. `List.copyOf` không phải giải pháp thay thế cơ chế đồng bộ.

### Thời điểm phát hiện lỗi

Một adapter có thể chọn:

- **Fail-fast**: ném exception tại phần tử sai đầu tiên, như ví dụ.
- **Collect-errors**: gom các lỗi validation kèm index, trả lại báo cáo để người dùng chỉnh.
- **Skip có chủ đích**: loại bỏ phần tử không hợp lệ nếu hợp đồng nghiệp vụ cho phép và có thống kê minh bạch.

Không lặng lẽ bỏ qua phần tử sai kiểu nếu người dùng tin rằng toàn bộ dữ liệu đã được xử lý đầy đủ.

## 12. Thiết kế test cho từng ranh giới

Bộ test có ý nghĩa của một migration boundary nên dựa vào **hợp đồng thực tế**, không chỉ vào chữ ký sau khi sửa. Với `checkedUsers` trong ví dụ, các trường hợp cần nghĩ đến:

| Ca kiểm thử | Đầu vào | Kỳ vọng |
| --- | --- | --- |
| Danh sách hợp lệ | Hai `User` | Hai phần tử đúng thứ tự |
| Danh sách rỗng | `List.of()` | Danh sách rỗng |
| Sai kiểu | `User`, sau đó `String` | Báo lỗi chỉ rõ index 1 |
| Giá trị `null` | Một phần tử `null` | Bị từ chối theo policy |
| Sửa nguồn sau khi chuyển | Thêm phần tử vào raw list | Snapshot cũ không thêm phần tử |
| Caller cố ghi vào kết quả | Gọi `add` trên list trả về | Bị từ chối nếu dùng `List.copyOf` |
| Nguồn bị sửa đồng thời | Thay đổi trong khi duyệt | Cần quy định/kiểm tra đồng bộ theo hợp đồng |
| Giá trị object bên trong mutable | Sửa field của phần tử | Cần xem xét có cần deep copy không |

Với `SettingsGateway`, kiểm thử đọc đúng token, token sai, key vắng mặt và giá trị `null`. Với API công khai đã tồn tại, thêm các bài kiểm tra build/callers hiện hữu để xác nhận source và binary compatibility nếu cần.

Một kiểm tra đặc biệt giá trị: **đặt dữ liệu sai kiểu vào nguồn raw trước**, rồi chứng minh phần typed không nhận dữ liệu đó theo hợp đồng. Nếu chỉ test đường thành công, bạn có thể bỏ sót lỗi thuộc chính mục tiêu chuyển đổi.

## 13. Các lỗi thường gặp khi di trú

**1. Thay `List` thành `List<Object>` cho tất cả trường hợp.**

Việc này làm mất cơ hội diễn đạt kiểu miền dữ liệu và có thể khiến `List<String>` không truyền được vào API do invariance. Hãy chọn theo mục đích: `List<?>` để đọc chưa rõ kiểu, `List<User>` sau validation, hoặc `List<Object>` khi muốn hỗn hợp kiểu thật.

**2. Cast `(List<User>) raw` ngay tại mọi caller.**

Mỗi cast làm tăng vùng cần tin tưởng, không kiểm tra các phần tử và khiến lỗi xuất hiện xa nguồn. Tập trung chuyển đổi tại một adapter đã kiểm chứng.

**3. Đổi return generic rồi mặc định dữ liệu đã được làm sạch.**

Chữ ký compile-time không tự chuyển string thành user, không từ chối phần tử sai lúc runtime và không cách ly danh sách mutable. Cần kiểm tra schema cùng chính sách quyền sở hữu dữ liệu.

**4. Thay tham số `Object` bằng `User` trên API public nhưng bỏ qua caller đã biên dịch.**

Descriptor có thể thay đổi, gây `NoSuchMethodError` hoặc lỗi liên kết khác khi chạy binary chưa được cập nhật. Cần xem xét adapter/phương thức song song và xác minh tương thích bằng build, `javap`, hoặc kiểm tra API.

**5. Tắt toàn bộ `unchecked` trong mã mới.**

Cảnh báo là tín hiệu chưa chứng minh được an toàn kiểu. Khoanh vùng ở nơi tiếp xúc legacy, ghi nhận nguyên nhân cụ thể, dần xóa khi nguồn đã typed.

**6. Kiểm tra `instanceof List<?>` rồi tưởng đã xác thực `List<User>`.**

Thao tác ấy chỉ xác minh kiểu container; để xác thực phần tử, duyệt và kiểm tra từng giá trị, hoặc dùng metadata/contract có căn cứ.

**7. Trộn việc di trú generics với đổi semantics của API.**

Nếu API cũ trả một collection live view còn API mới trả snapshot bất biến, đó là thay đổi hành vi cần diễn đạt rõ. Đôi khi thay đổi có chủ đích là cần thiết, nhưng phải cập nhật caller/test theo hợp đồng mới.

**8. Cố gắng generic hóa các miền dữ liệu không đồng nhất.**

Nếu một `List` chứa nhiều loại record có ý nghĩa nghiệp vụ, có thể cần một kiểu tổng quát rõ ràng (ví dụ sealed hierarchy), chuyển đổi thành các dạng domain riêng hoặc giữ `List<?>` tại điểm chưa đủ thông tin. Ép tất cả thành `List<User>` không giải quyết sự đa dạng dữ liệu.

## 14. Bài luyện tập: áp dụng di trú theo ranh giới

### Bài 1 — Tìm điểm nguy hiểm

Cho API cũ:

```java
static java.util.List load() {
    java.util.List data = new java.util.ArrayList();
    data.add("A");
    data.add(7);
    return data;
}
```

Đâu là rủi ro nếu caller mới viết `List<String> x = (List<String>) load();`?

**Gợi ý đáp án:** cast unchecked không kiểm tra từng phần tử, `Integer` sẽ gây lỗi khi đọc với kỳ vọng `String`. Di trú bằng adapter kiểm tra từng phần tử; fail-fast hoặc trả lỗi về caller.

### Bài 2 — Chọn chữ ký đích

Một API chỉ duyệt các phần tử của một `List` không biết kiểu; có nên chuyển sang `List<Object>`?

**Đáp án:** thường nên dùng `List<?>`. Nó nhận `List<String>`, `List<Integer>` và nhiều dạng khác mà không cho phép ghi tùy tiện.

### Bài 3 — Giới hạn của `Class<T>`

Adapter nhận `Class<T>` có thể kiểm tra `String.class`. Nó có xác minh được toàn bộ `List<String>` chỉ bằng `List.class` không?

**Đáp án:** không; `List.class` chỉ kiểm tra lớp/container. Kiểu tham số `String` bị xóa ở runtime descriptor; phải kiểm tra phần tử hoặc giữ thêm metadata/hợp đồng đáng tin.

### Bài 4 — Source, binary và dữ liệu

Thay `List findAll()` bằng `List<User> findAll()` có luôn an toàn không?

**Đáp án:** không có bảo đảm chung. Hai return type dùng cùng JVM descriptor `List` trong ví dụ, nhưng source callers, generic signature metadata, hợp đồng phần tử, quyền sửa collection và cách xử lý lỗi có thể khác. Cần kiểm tra API, hành vi và consumer cụ thể.

### Bài 5 — Lợi ích của snapshot

Vì sao ví dụ `checkedUsers` tạo `ArrayList<User>` và dùng `List.copyOf` thay vì trả raw list dưới một cast?

**Đáp án:** vừa kiểm tra từng phần tử vừa tạo danh sách mới, giúp caller typed không chia sẻ cấu trúc mutable với legacy list; kết quả cũng không thể sửa qua API list thông thường.

### Bài 6 — Thứ tự ưu tiên khi sửa hệ thống lớn

Một dự án có 20 caller dùng `Object` từ thư viện cũ; chỉ có 3 caller cần `User`. Có nên sửa ngay thư viện và 20 caller?

**Gợi ý:** xác định hợp đồng của 3 caller, tạo adapter chỉ phục vụ `User`, test dữ liệu sai, chuyển 3 caller trước. Sau khi quan sát các luồng khác, mới cân nhắc chỉnh nguồn và loại bỏ phần legacy phù hợp.

## Checklist áp dụng trên một ranh giới

Trước khi đánh dấu một đường di trú là hoàn chỉnh, hãy trả lời:

1. Có xác định **nguồn dữ liệu, quyền sở hữu, nơi sửa đổi** và loại dữ liệu thực không?
2. Có chọn đúng chữ ký API mới, tránh raw types trong lớp consumer không?
3. Dữ liệu từ legacy được **xác minh trước khi gắn cam kết generic typed** chưa?
4. Có chính sách rõ cho `null`, phần tử sai và báo lỗi không?
5. Collection trả về có cần snapshot/immutable hay được phép live view không?
6. Các cảnh báo `rawtypes`/`unchecked` còn nằm ở vùng nào, có được hiểu và giới hạn chưa?
7. Những thay đổi chữ ký public có được đánh giá về source, binary và behavioral compatibility không?
8. Các bài kiểm tra đúng/sai kiểu, dữ liệu rỗng, thay đổi sau khi trả về và caller cũ đã chạy chưa?

## Kết luận

**Di chuyển API cũ sang generics theo từng ranh giới** là cách biến sự không chắc chắn về kiểu thành một hợp đồng rõ ràng tại điểm dữ liệu đi vào phần mã mới. Raw `List` hoặc `Object` có thể được giữ tạm ở phía legacy; adapter đảm nhiệm kiểm tra, chuyển đổi, báo lỗi và bảo vệ quyền sở hữu dữ liệu; consumer mới dùng `List<User>` hoặc generic method có `Class<T>` mà không phải cast tùy tiện. Khi đã xác thực dữ liệu và caller ở một vùng, bạn có thể tiến đến vùng tiếp theo, đồng thời xem xét tương thích API bằng cả compiler, runtime và bytecode.


</details>

- [Quay lại đầu trang](#back-to-top)
