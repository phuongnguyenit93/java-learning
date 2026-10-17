<a id="back-to-top"></a>

# Varargs generic

## Menu
- [Ô nhiễm kiểu trong heap (heap pollution) và biến có kiểu tham số hóa](#generic-varargs-001)
- [Varargs generic được biểu diễn bằng mảng](#generic-varargs-002)
- [Cảnh báo tại nơi khai báo và nơi gọi phương thức varargs](#generic-varargs-003)
- [Rủi ro ghi qua tham chiếu mảng kiểu cha](#generic-varargs-004)
- [Rủi ro làm lộ mảng varargs ra bên ngoài](#generic-varargs-005)
- [@SafeVarargs và những phương thức hoặc constructor được phép dùng](#generic-varargs-006)
- [@SafeVarargs là cam kết an toàn, không sửa thân phương thức](#generic-varargs-007)
- [Phân biệt cảnh báo unchecked, rawtypes và varargs](#generic-varargs-008)

## <a id="generic-varargs-001">Ô nhiễm kiểu trong heap (heap pollution) và biến có kiểu tham số hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Ô nhiễm kiểu trong heap (heap pollution) và biến có kiểu tham số hóa

## Mục tiêu

Trong Java Generics, biến `List<String>` hứa với trình biên dịch rằng khi đọc phần tử, chương trình có thể xem phần tử đó là `String`. Tuy nhiên, nếu một đường gọi khác dùng **raw type**, **unchecked conversion/cast** hoặc thao tác thiếu an toàn trên **generic varargs**, cấu trúc dữ liệu thực có thể không còn đáp ứng cam kết. Tình trạng ấy được gọi là **heap pollution (ô nhiễm kiểu trong heap)**.

Bài này thuộc chủ đề **Varargs generic** trong Java Core — Generics. Sau khi học, bạn có thể:

- Giải thích heap pollution theo quan hệ giữa kiểu tham số hóa ở source và đối tượng thực trong heap.
- Phân biệt **heap pollution**, **unchecked warning** và **`ClassCastException`**; chúng liên quan nhưng không đồng nghĩa.
- Chứng minh hai nguồn gây ô nhiễm: raw `List` và **mảng varargs** `List<String>...`.
- Giải thích tại sao phép gán `Object[] alias = groups; alias[0] = List.of(99);` có thể qua được runtime array store check nhưng lời đọc `String` thất bại.
- Dùng `-Xlint:rawtypes`, `-Xlint:unchecked`, `-Xlint:varargs` để đọc những cảnh báo có ích.
- Áp dụng `@SafeVarargs` có trách nhiệm; hiểu giới hạn của annotation và chọn collection/generic method không varargs khi phù hợp.
- Bọc dữ liệu không đáng tin bằng adapter **kiểm tra từng phần tử** thay vì unchecked cast cả collection.

Các chương trình hoàn chỉnh chạy được bằng **Java 17 trở lên**. Các đoạn có comment báo lỗi biên dịch chỉ để phân tích; không được sao chép thành chương trình độc lập mà bỏ comment.

## 1. Heap pollution chính xác là gì?

Một cách diễn đạt chuẩn: **heap pollution xảy ra khi một biến có kiểu tham số hóa tham chiếu tới một đối tượng không tương thích với kiểu tham số hóa ấy**. Đây không phải một lỗi syntax hay exception riêng tên `HeapPollutionException`: nó là **trạng thái không bảo đảm an toàn kiểu**, có thể chưa gây lỗi ngay lúc được tạo ra.

Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

class Illustration {
    static void demonstrate() {
        List<String> names = new ArrayList<>();
        names.add("Java");

        List raw = names;   // Hai biến trỏ cùng một ArrayList.
        raw.add(42);       // Lời gọi unchecked.

        // names được khai báo List<String>, nhưng chứa Integer.
        // String second = names.get(1); // ClassCastException khi chạy.
    }
}
```

Đoạn trên là một lớp minh họa không có `main`; chương trình đầy đủ có output kiểm chứng ở mục 4.

Tại sao compiler không chặn ngay `raw.add(42)`? Kiểu nguồn của biến `raw` là **raw `List`**, đã bỏ đối số kiểu `String`. Phương thức `add` vì thế được dùng mà không có bảo đảm compile-time dành cho `List<String>`; compiler phát cảnh báo unchecked, nhưng không biết rằng biến `raw` đang làm hỏng một cam kết generic của biến khác.

Thao tác `raw.add(42)` không nhất thiết ném exception, bởi runtime class `ArrayList` không có một phiên bản riêng cho `ArrayList<String>`. Lỗi có thể được phát hiện ở một chỗ **đọc/ép kiểu** sau đó: khi compiler kỳ vọng `String` nhưng giá trị thật là `Integer`.

### Ba khái niệm không được đánh đồng

| Khái niệm | Nói về | Ví dụ |
| --- | --- | --- |
| **Heap pollution** | Đối tượng mà một tham chiếu generic tin tưởng không còn phù hợp với tham số kiểu | `List<String>` chứa `Integer` |
| **Unchecked warning** | Compiler không xác minh được toàn bộ tính đúng kiểu của phép gọi/chuyển đổi | `raw.add(42)`, generic varargs không an toàn |
| **`ClassCastException`** | Một phép cast runtime cụ thể thất bại | Lấy `Integer` từ `List<String>` rồi dùng như `String` |

Một cảnh báo unchecked **chỉ báo rủi ro**, không khẳng định heap pollution chắc chắn đã xảy ra. Ngược lại, nếu code đã cố tình lách những kiểm tra kiểu, heap pollution có thể tồn tại trước khi một exception được ném. Đừng sử dụng “chạy không lỗi” làm bằng chứng rằng đường dữ liệu đã an toàn.

## 2. Kiểm tra kiểu của generics và type erasure

`List<E>` được định nghĩa với tham số kiểu `E`; trong `List<String>`, compiler dùng `String` để kiểm tra lời gọi. Ví dụ:

```java
List<String> messages = new ArrayList<>();
messages.add("Hello");
// messages.add(3); // Lỗi biên dịch nếu gọi qua tham chiếu typed.
String first = messages.get(0);
```

Đoạn này giả định có `import java.util.List;` và `import java.util.ArrayList;`.

Đối số kiểu `String` **không tạo một runtime class `List<String>` riêng**. Java generics thực hiện **type erasure**; một lời gọi như `List.get(int)` sử dụng descriptor trả `Object`, còn compiler có thể chèn `checkcast String` tại vị trí đọc kết quả thành `String`.

Đừng hiểu việc xóa kiểu là biến đối tượng `String` thành một `Object` khác. Đối tượng thực vẫn có runtime class `String`; chỉ phần thông tin về **type argument của container** không được JVM thể hiện như một runtime class chuyên biệt.

Hệ quả liên quan tới heap pollution: ở mức runtime, `ArrayList` không thể tự dùng một loại “store check cho `String`” trên mọi lời `add`. Nếu dữ liệu đi qua raw/unchecked path, phần tử sai có thể lọt vào, rồi lỗi xảy ra khi code typed lấy ra.

## 3. Generic varargs: điểm gặp nhau của mảng và type erasure

Trong Java, lời khai báo:

```java
static void show(List<String>... groups) {
    // ...
}
```

sử dụng **varargs**: ở runtime, các đối số varargs được truyền bằng một **mảng**. Nhưng kiểu phần tử `List<String>` là **non-reifiable**: JVM không có runtime class chuyên biệt cho `List<String>`, và không thể tạo trực tiếp `new List<String>[n]` trong Java source.

Điều quan trọng:

- `List<String>...` hợp lệ trong chữ ký varargs, nhưng compiler có thể phát cảnh báo liên quan **possible heap pollution**.
- Mảng runtime nhận phần tử là `List`, không thể kiểm tra type argument `String` của từng danh sách.
- Có thể xem mảng varargs qua một tham chiếu rộng hơn `Object[]` vì array covariance.
- Ghi một `List<Integer>` vào một ô của mảng qua `Object[]` có thể **không** gây `ArrayStoreException`, vì runtime component type vẫn là `List`.
- Lời đọc phần tử với kỳ vọng `String` có thể gây `ClassCastException`.

So sánh hai trường hợp:

```java
String[] texts = {"A"};
Object[] textAlias = texts;
// textAlias[0] = 99; // Biên dịch được, nhưng ArrayStoreException lúc chạy.
```

và:

```java
// Trong một method đang nhận List<String>... groups:
Object[] groupsAlias = groups;
groupsAlias[0] = List.of(99); // JVM vẫn thấy đây là List.
```

Đoạn thứ hai **làm ô nhiễm mảng có kiểu source `List<String>[]`**. Runtime store check chỉ xem đối tượng đưa vào có là `List` không, chứ không thể kiểm tra mọi phần tử bên trong theo type argument `String`. Vì vậy lời gán không bị chặn theo cách `String[]` chặn `Integer`.

Đó là lý do Java hạn chế **generic array creation**, cảnh báo với **non-reifiable varargs** và cung cấp `@SafeVarargs` cho những khai báo có thể được chứng minh an toàn.

## 4. Chương trình A — Thấy heap pollution qua raw type và generic varargs

Tạo tệp **`HeapPollutionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class HeapPollutionDemo {
    static List<String>[] pollute(List<String>... groups) {
        Object[] alias = groups;
        alias[0] = List.of(99);
        return groups;
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("Java"));
        List raw = words;
        raw.add(42);
        System.out.println("raw contents: " + words);

        try {
            String item = words.get(1);
            System.out.println(item);
        } catch (ClassCastException ex) {
            System.out.println("raw read failure: "
                    + ex.getClass().getSimpleName());
        }

        List<String> original = new ArrayList<>(List.of("safe"));
        List<String>[] groups = pollute(original);
        System.out.println("varargs component: "
                + groups.getClass().getComponentType().getName());
        System.out.println("original unaffected: " + original);

        try {
            String item = groups[0].get(0);
            System.out.println(item);
        } catch (ClassCastException ex) {
            System.out.println("varargs read failure: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac -Xlint:all HeapPollutionDemo.java
java HeapPollutionDemo
```

**Stdout đã kiểm chứng:**

```text
raw contents: [Java, 42]
raw read failure: ClassCastException
varargs component: java.util.List
original unaffected: [safe]
varargs read failure: ClassCastException
```

Với Java 17 và `-Xlint:all`, compiler cũng phát ra **bốn cảnh báo** ở ví dụ được kiểm thử:

- `[unchecked] Possible heap pollution from parameterized vararg type List<String>` tại khai báo `pollute`.
- `[rawtypes]` tại `List raw`.
- `[unchecked]` tại `raw.add(42)`.
- `[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]` tại lời gọi `pollute(original)`.

Văn bản, vị trí và số lượng cảnh báo là đặc thù của phiên bản compiler/tùy chọn lint; chúng là **diagnostics của `javac`**, không phải stdout của chương trình.

### Phần A1 — Raw type phá hợp đồng của `List<String>`

Hai biến `words` và `raw` là **hai tham chiếu tới cùng một `ArrayList`**. Vì `raw` bỏ đối số kiểu, lời `add(42)` có thể chèn một `Integer` vào danh sách mà `words` vẫn tin là `List<String>`.

```text
           List<String> words ---+
                                  +---> ArrayList đang chứa ["Java", 42]
                 List raw -------+
```

Đến lúc `String item = words.get(1)`, code typed cần `String`, compiler đã tạo hành vi cast phù hợp, nhưng đối tượng thật là `Integer`. Chính dòng đọc gây `ClassCastException`, không phải dòng `raw.add(42)`.

### Phần A2 — Generic varargs làm ô nhiễm array, không tự sửa original list

Method:

```java
static List<String>[] pollute(List<String>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(99);
    return groups;
}
```

thực hiện ba việc nguy hiểm:

1. `groups` là một mảng runtime có **component type `List`**, mặc dù compiler biểu diễn nó bằng `List<String>[]` ở source.
2. `Object[] alias = groups` cho phép thao tác qua góc nhìn mảng chứa `Object`.
3. `alias[0] = List.of(99)` thay **ô thứ nhất của mảng groups** bằng danh sách chứa `Integer`. Mảng vẫn chấp nhận vì đối tượng đó là một `List`.
4. Trả `groups` làm tham chiếu tới **mảng đã bị nhiễm** thoát ra ngoài, nơi caller nghĩ có `List<String>[]`.

Dòng:

```text
original unaffected: [safe]
```

cực kỳ quan trọng: phương thức **không thêm `99` vào danh sách `original`**. Nó thay một **phần tử của mảng varargs** — tức thay tham chiếu tới danh sách. Với lời gọi varargs thông thường `pollute(original)`, compiler tạo mảng chuyển đối số, và `original` vẫn giữ phần tử `"safe"`.

Khi code đọc:

```java
String item = groups[0].get(0);
```

nó dựa trên `List<String>[]`, nhưng danh sách tại ô 0 đã chứa `Integer`; phép cast sang `String` thất bại. Đó là heap pollution đi qua **generic varargs**, khác đường raw `List` trước đó.

### Vì sao không ném `ArrayStoreException`?

Mảng thực của `groups` có component type là `java.util.List`. Cả `List<String>` lẫn `List<Integer>` đều có đối tượng runtime thực hiện `List`. Kiểm tra store runtime của mảng không biết type argument đã bị xóa. Ngược lại, nếu cố đưa một `String` trực tiếp vào ô mảng có component class `List`, runtime array store có thể từ chối vì `String` không phải một `List`.

## 5. Vị trí phát hiện lỗi và sự khác nhau giữa ba cơ chế

Một chuỗi diễn biến cần ghi nhớ:

```text
Compile time:
    List<String> x
        |
        +-- x.add(42)                     -> lỗi biên dịch
        |
        +-- raw.add(42)                   -> cảnh báo unchecked

Runtime:
    raw.add(42)                           -> có thể ghi thành công
        |
        v
    List<String> bị ô nhiễm
        |
        v
    String s = x.get(i)                    -> ClassCastException

Generic varargs:
    List<String>... groups
        |
        v
    Object[] alias = groups
        |
        v
    alias[0] = List.of(99)                 -> mảng chỉ kiểm tra component List
        |
        v
    String s = groups[0].get(0)            -> ClassCastException
```

Không phải mọi `ClassCastException` đều bắt nguồn từ heap pollution: cast sai thông thường trên `Object` cũng có thể gây lỗi. Ngược lại, heap pollution chưa chắc ném exception nếu code không đọc/cast dữ liệu sai; đó là lý do test chỉ chạy đường thành công không đủ chứng minh an toàn generic.

**Khác nhau về nơi kiểm tra:**

| Tình huống | Compiler | Runtime | Kết quả |
| --- | --- | --- | --- |
| `List<String>.add(42)` | Bác bỏ | Không chạy | Lỗi biên dịch |
| `List raw = words; raw.add(42)` | Cảnh báo unchecked | `ArrayList` không kiểm tra type argument `String` | Có thể gây heap pollution |
| `Object[]` trỏ `String[]`, ghi `Integer` | Cho phép vì kiểu biến `Object[]` | Kiểm tra component `String` | `ArrayStoreException` |
| `Object[]` trỏ `List<String>[]` của varargs, ghi `List<Integer>` | Có thể không thấy sai qua alias | Chỉ kiểm tra component `List` | Có thể gây heap pollution |
| Đọc `Integer` như `String` | Tin vào kiểu nguồn `List<String>` | `checkcast String` thất bại | `ClassCastException` |

Bảng giả định đúng các đối tượng thực được mô tả và không có logic validation riêng trong container.

## 6. Tại sao `List<String>[]` không được tạo trực tiếp?

Trong Java, đoạn sau là lỗi biên dịch:

```java
// List<String>[] groups = new List<String>[2]; // Generic array creation.
```

Nhưng:

```java
List<?>[] groups = new List<?>[2]; // Hợp lệ.
```

Mảng Java lưu runtime component class và kiểm tra ghi phần tử vào mảng. `List<String>` **không reifiable**: kiểu `String` bên trong không được biểu diễn như lớp runtime riêng cho `List<String>`. Cho phép `new List<String>[2]` sẽ hứa một khả năng kiểm tra mà runtime không thực hiện được.

`List<?>` dùng **unbounded wildcard** và là một reifiable type trong ngữ cảnh này. Mảng `List<?>[]` vẫn có thể chứa các danh sách với loại phần tử khác nhau, vì hợp đồng chỉ nói mỗi ô là một `List` có type argument không xác định; khi đọc qua `List<?>`, phần tử được xem là `Object`, không được giả định là `String`.

Generic varargs `List<String>...` tạo ra vấn đề liên quan: ở source, parameter trông như mảng có thành phần generic; ở bytecode, tham số array được thể hiện theo erased component `List`. Compiler cho phép khai báo varargs vì đó là cơ chế ngôn ngữ hữu ích, đồng thời cảnh báo khi không thể bảo đảm sự an toàn do phần tử không reifiable.

## 7. Phân biệt rủi ro của method, lời gọi và dữ liệu

Trong bài lab, compiler có thể cảnh báo **tại khai báo method**:

```java
static List<String>[] pollute(List<String>... groups)
```

và **tại điểm gọi**:

```java
List<String>[] groups = pollute(original);
```

Hai vị trí cảnh báo giúp bạn phát hiện rủi ro khác nhau:

- **Declaration warning:** method nhận một generic varargs array có thể gây heap pollution. Không phải bản thân chữ ký luôn đã làm sai dữ liệu, nhưng compiler không thể chứng minh từ chữ ký rằng body an toàn.
- **Invocation warning:** khi compiler phải tạo mảng varargs với component không reifiable, lời gọi mang một cấu trúc kiểu không thể kiểm tra đầy đủ ở runtime.
- **Unchecked call/conversion warning:** code có thể đã đi qua raw type hoặc cast không được xác minh, như `raw.add(42)`.

Đừng chỉ tắt cảnh báo ở nơi tiện nhất. Nếu method thuộc quyền sở hữu của bạn, đánh giá **toàn bộ thân method** và quan hệ alias của mảng varargs trước. Nếu method đến từ thư viện, đọc hợp đồng và tránh lối gọi không an toàn.

Khi một `List<String>[]` đã bị nhiễm, trả mảng ra khỏi method làm lỗi khó theo dõi hơn vì caller có thể đọc phần tử sai sau rất nhiều thao tác khác. Đặc biệt nguy hiểm khi lưu mảng varargs vào field dùng chung, trả nó qua API hoặc cung cấp nó cho mã khác sửa.

## 8. Cách viết varargs generic an toàn có chủ đích

Một phương thức chỉ duyệt các nhóm và tạo collection mới có thể tránh ô nhiễm. Ví dụ:

```java
@SafeVarargs
static <T> List<T> merge(List<? extends T>... parts) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> part : parts) {
        result.addAll(part);
    }
    return List.copyOf(result);
}
```

Mẫu này **không ghi** vào array `parts`, không cast/đánh tráo danh sách bên trong, không đưa array varargs ra ngoài để mã khác sửa. Phần tử được thêm vào `result` dưới hợp đồng `T` do compiler kiểm tra. Kết quả trả về là một danh sách mới, không phải bản thân mảng varargs.

### Khi nào được dùng `@SafeVarargs`?

`@SafeVarargs` là **cam kết của tác giả** rằng phần thực thi trên varargs kiểu non-reifiable an toàn. Java cho phép dùng trên constructor, static method, final instance method, và (từ Java 9) private instance method. Không được dùng trên instance method thường có thể override, vì một override có thể vi phạm bảo đảm mà annotation đưa ra.

Annotation:

- Giúp compiler không phát một số cảnh báo unchecked liên quan tới khai báo/lời gọi varargs mà tác giả đã chứng minh an toàn.
- **Không làm JVM lưu generic type arguments** cho mảng.
- **Không tự biến một method nguy hiểm thành an toàn**.
- Không bảo đảm loại bỏ mọi cảnh báo khác phát sinh do các thao tác unchecked thật sự trong body.

Đặc biệt, đừng đặt `@SafeVarargs` lên method `pollute` trong phần 4 chỉ để làm sạch output. Method ấy đã cố ý đặt một `List<Integer>` vào mảng mà caller tin là `List<String>[]`; annotation sẽ đưa ra lời cam kết **sai**.

## 9. Chương trình B — `@SafeVarargs` đúng cách

Tạo tệp **`SafeVarargsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class SafeVarargsDemo {
    @SafeVarargs
    static <T> List<T> merge(List<? extends T>... parts) {
        List<T> result = new ArrayList<>();

        for (List<? extends T> part : parts) {
            result.addAll(part);
        }
        return List.copyOf(result);
    }

    static void showAny(List<?> items) {
        for (Object value : items) {
            System.out.println("seen: " + value);
        }
    }

    public static void main(String[] args) {
        List<String> merged = merge(List.of("An"), List.of("Binh"));
        System.out.println("merged: " + merged);
        showAny(merged);

        List<Number> nums = merge(List.of(1, 2), List.of(3.5));
        System.out.println("numbers: " + nums);
    }
}
```

Chạy:

```bash
javac -Xlint:all SafeVarargsDemo.java
java SafeVarargsDemo
```

**Stdout đã kiểm chứng:**

```text
merged: [An, Binh]
seen: An
seen: Binh
numbers: [1, 2, 3.5]
```

**Tại sao an toàn trong ví dụ này?**

1. `parts` được **đọc**, không bị thay đổi các ô của array.
2. Mọi `part` là một `List<? extends T>`: các phần tử đọc ra có thể dùng như `T`.
3. `result` là một `ArrayList<T>` hoàn toàn mới, không giữ alias tới mảng varargs.
4. Không trả mảng `parts`, không gán nó vào field, không chuyển nó cho mã không kiểm soát.
5. `List.copyOf(result)` trả một list không hỗ trợ các phép sửa đổi thông thường. Trong ví dụ này các phần tử là `String` và `Number` bất biến; `copyOf` không tự tạo immutable sâu cho phần tử mutable nếu có.

Khi gọi `merge(List.of(1, 2), List.of(3.5))` trong ngữ cảnh gán vào `List<Number>`, compiler có thể chọn `T = Number`. Các phần tử `Integer` và `Double` đều có thể được xem như `Number`, nên output hợp lệ.

**Không nên khái quát sai:** `@SafeVarargs` không tuyên bố rằng một phương thức bất kỳ có varargs generic sẽ luôn an toàn. Nó áp dụng khi **body thực sự không có thao tác phá vỡ cam kết generic**.

## 10. Lựa chọn tốt hơn khi không cần varargs

Nếu một API thường xuyên nhận collection từ caller, một chữ ký không varargs có thể diễn đạt ý định rõ hơn:

```java
static <T> List<T> copyOfItems(
        java.util.Collection<? extends T> source) {
    return List.copyOf(source);
}
```

Đây là đoạn method minh họa, cần đặt trong một class có import `java.util.List`. Nó không buộc compiler tạo generic varargs array tại lời gọi.

Hoặc nếu cần nhiều nhóm, dùng một collection chứa các nhóm:

```java
static <T> List<T> flatten(
        List<? extends List<? extends T>> groups) {
    List<T> out = new ArrayList<>();
    for (List<? extends T> group : groups) {
        out.addAll(group);
    }
    return List.copyOf(out);
}
```

Dùng `List<List<String>>` hoặc một biểu thức typed tương thích theo yêu cầu thực tế. Cách này không có array varargs ẩn và tránh một số nguy cơ alias của generic array, nhưng vẫn cần tôn trọng hợp đồng về `null`, quyền sửa collection và concurrency.

**Nguyên tắc lựa chọn:** varargs hợp lý khi API được gọi tiện bằng nhiều đối số rời và method chỉ đọc/tiêu thụ an toàn; collection parameters thường phù hợp hơn khi caller đã có danh sách động hoặc cần hợp đồng ownership rõ.

## 11. Ranh giới dữ liệu từ raw API: kiểm tra phần tử trước khi nhận `List<String>`

Trong mã legacy, không phải lúc nào bạn cũng sửa được API trả raw `List`. Thay vì:

```java
// Không nên tuyên bố dữ liệu an toàn chỉ bằng cast:
// @SuppressWarnings("unchecked")
// List<String> strings = (List<String>) legacyList;
```

hãy nhận `List<?>`, kiểm tra phần tử và tạo kết quả typed mới. Đây là cách đưa lỗi về **ranh giới dữ liệu** thay vì để lỗi xuất hiện muộn ở code nghiệp vụ.

Tạo tệp **`HeapPollutionBoundaryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class HeapPollutionBoundaryDemo {
    static List<String> checkedStrings(List<?> input) {
        List<String> copy = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            Object value = input.get(i);
            if (!(value instanceof String s)) {
                throw new IllegalArgumentException(
                        "invalid element at index " + i);
            }
            copy.add(s);
        }
        return List.copyOf(copy);
    }

    public static void main(String[] args) {
        List<?> good = List.of("A", "B");
        System.out.println("validated: " + checkedStrings(good));

        List<?> bad = List.of("A", 5);
        try {
            checkedStrings(bad);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -Xlint:all HeapPollutionBoundaryDemo.java
java HeapPollutionBoundaryDemo
```

**Stdout đã kiểm chứng:**

```text
validated: [A, B]
rejected: invalid element at index 1
```

Hàm `checkedStrings` xác nhận **mọi phần tử** là `String` trước khi trả `List<String>`. Nó không dùng unchecked cast, từ chối `null` (vì `null instanceof String` là `false`), và trả một danh sách mới, không thể bị sửa trực tiếp bằng API list thông thường.

Nếu có một danh sách legacy chứa các object của nhiều kiểu, cần thỏa thuận rõ phải **từ chối**, **chuyển đổi**, hay **bỏ qua có báo cáo** đối với phần tử sai. Cách `checkedStrings` là một chính sách fail-fast, không phải chính sách hợp lý duy nhất.

Khi hệ thống có nhiều luồng cùng sửa collection đầu vào trong lúc validate, muốn cam kết mạnh hơn cần chính sách synchronization/snapshot nhất quán; việc copy từng phần tử trong ví dụ không tự bảo đảm atomic snapshot nếu nguồn bị cập nhật đồng thời.

## 12. Cơ chế cảnh báo: bật đúng công cụ khi tìm heap pollution

Các tùy chọn compile rất hữu ích:

```bash
javac -Xlint:rawtypes -Xlint:unchecked -Xlint:varargs HeapPollutionDemo.java
```

| Nhóm | Ví dụ nhận diện | Cần kiểm tra |
| --- | --- | --- |
| `rawtypes` | `List raw` | Vì sao bỏ type argument và có thể thay bằng generic/wildcard không? |
| `unchecked` | `raw.add(42)`, generic varargs không an toàn, cast không kiểm tra | Dữ liệu có được chứng minh đúng loại không? |
| `varargs` | Thao tác có khả năng thiếu an toàn trong thân varargs method, đặc biệt khi dùng annotation | Body có leak, ghi hoặc alias mảng varargs không? |

Trên chương trình đã chạy, `javac -Xlint:all` phát hiện cả raw type, unchecked call, generic varargs declaration và generic varargs invocation. Bạn có thể dùng `-Xlint:all` trong quá trình rà soát và thu hẹp các nhóm cần theo dõi cho build pipeline sau khi hiểu nguyên nhân.

Trong code thực tế, compiler warnings có thể xuất hiện ở những nơi khác nhau tùy JDK. Không biến chính xác chuỗi văn bản/line number trong ví dụ thành quy tắc nền tảng; điều cần kiểm tra là **bản chất của đường đi dữ liệu và khả năng compiler chứng minh kiểu**.

Thứ tự ưu tiên khi sửa:

1. Không để raw type lan ra khỏi ranh giới legacy.
2. Thay unchecked cast bằng kiểm tra dữ liệu có cấu trúc khi có thể.
3. Kiểm tra mọi generic varargs method có ghi/để lộ array.
4. Cân nhắc loại bỏ varargs nếu không thật cần.
5. Chỉ dùng `@SuppressWarnings("unchecked")` trong phạm vi hẹp có giải thích chứng minh.
6. Chỉ đặt `@SafeVarargs` khi đã rà soát an toàn thân method.

## 13. Quan sát type erasure với `javap`

Sau khi biên dịch `HeapPollutionDemo.java`, chạy:

```bash
javap -s -p HeapPollutionDemo
```

Trích đoạn **đã xác minh**:

```text
static java.util.List<java.lang.String>[] pollute(
    java.util.List<java.lang.String>...);
  descriptor: ([Ljava/util/List;)[Ljava/util/List;
```

Cách đọc: ký hiệu `[` đứng trước type descriptor nghĩa là **mảng**; `Ljava/util/List;` nghĩa là đối tượng `List`. Descriptor `([Ljava/util/List;)[Ljava/util/List;` cho biết method nhận mảng `List[]` và trả mảng `List[]` ở JVM. Phần type argument `String` không xuất hiện trong descriptor — nó chỉ có thể được mô tả trong generic `Signature` metadata.

Đây là bằng chứng rõ ràng vì sao runtime component type của array varargs trong ví dụ là `java.util.List`. JVM có thể kiểm tra object được đưa vào mảng có tương thích với `List`, nhưng **không thể kiểm tra riêng đối số `String`**.

Nếu muốn quan sát thêm bytecode cast tại nơi đọc:

```bash
javap -c -p HeapPollutionDemo
```

Trong `main` có các phép đọc mà kiểu nguồn kỳ vọng `String`; compiler có thể sinh `checkcast java/lang/String`. Cách hiển thị index/offset của opcode phụ thuộc bản biên dịch, nên hãy tập trung vào **method descriptor và opcode**, không học thuộc số dòng bytecode.

## 14. Những lỗi phổ biến cần tránh

**Lỗi 1 — Nghĩ heap pollution là exception riêng.** Heap pollution là một tình trạng an toàn kiểu bị vi phạm, không phải một exception có tên tương ứng. `ClassCastException` là một hậu quả có thể xảy ra khi cast.

**Lỗi 2 — Bỏ qua cảnh báo vì chương trình vẫn chạy.** Một unchecked path có thể chạy qua nhiều test chỉ vì chưa có dữ liệu sai; rủi ro vẫn còn. Bổ sung trường hợp phần tử không phù hợp và đường đọc typed.

**Lỗi 3 — Nghĩ `@SafeVarargs` tự làm method an toàn.** Annotation không tạo runtime check mới. Nếu body viết `alias[0] = List.of(99)` thì vẫn nguy hiểm dù compiler không còn cảnh báo tại lời gọi.

**Lỗi 4 — Nhầm `List<?>[]` với `List<String>[]`.** `List<?>[]` không hứa tất cả phần tử là `List<String>`; không được cast tùy tiện rồi dựa vào đó để đọc chuỗi.

**Lỗi 5 — Cho rằng `ArrayStoreException` sẽ cứu mảng varargs generic.** Runtime array store chỉ biết component `List`, không biết type arguments của mỗi list.

**Lỗi 6 — Không phân biệt việc sửa mảng với sửa collection.** Trong ví dụ `pollute`, `alias[0]` bị thay bằng một list khác; `original` không bị chèn `99`. Khi debug, kiểm tra identity/alias của **array** và **list elements** riêng biệt.

**Lỗi 7 — Coi `List<Object>` là thay thế chung cho `List<String>`.** Generics Java invariant; `List<String>` không phải subtype của `List<Object>`. Dùng `List<?>` để đọc chưa biết kiểu, hoặc `List<T>` đúng miền dữ liệu.

**Lỗi 8 — Dùng unchecked cast cả list thay cho validation.** `(List<String>) raw` không duyệt kiểm tra mọi phần tử. Nó có thể gắn cam kết generic không đúng cho dữ liệu thực.

**Lỗi 9 — Cho rằng chỉ generic varargs mới có heap pollution.** Raw types, unchecked conversions, phản chiếu/thao tác unsafe hoặc API legacy cũng có thể dẫn đến dữ liệu không phù hợp với hợp đồng generic.

**Lỗi 10 — Để mảng varargs thoát ra ngoài scope mà không cân nhắc.** Khi mảng bị giữ trong field hoặc trả ra ngoài, mã khác có thể sửa các slot qua alias, phá cam kết generic mà người đọc phía sau đang tin cậy.

## 15. Bài luyện tập kèm đáp án

### Bài 1 — Nhận biết heap pollution

```java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(100);
```

**Câu hỏi:** Khi nào heap pollution có thể xảy ra? Có phải `raw.add(100)` chắc chắn ném exception không?

**Đáp án:** sau khi `Integer` đi vào danh sách mà `names` khai báo là `List<String>`, lời hứa generic đã bị vi phạm. `raw.add(100)` có thể ghi thành công với cảnh báo unchecked; lỗi có thể chỉ xuất hiện khi code đọc như `String`.

### Bài 2 — Quan hệ giữa varargs và array covariance

Một method nhận `List<String>... groups`, rồi viết `Object[] x = groups; x[0] = List.of(7)`. Vì sao không nhất thiết có `ArrayStoreException` ngay tại phép gán?

**Đáp án:** runtime component class của array là `List`; `List.of(7)` tạo đối tượng thực hiện `List`. JVM không có thông tin runtime rằng slot ấy phải là `List<String>`.

### Bài 3 — Hai lỗi khác nhau

Một `Object[]` trỏ tới `String[]` bị ghi một `Integer`; một `List<String>` bị ghi `Integer` qua raw type rồi đọc như `String`. Hai loại lỗi thường là gì?

**Đáp án:** mảng `String[]` phát `ArrayStoreException` tại phép ghi; lời đọc `List<String>` đã nhiễm thường phát `ClassCastException`.

### Bài 4 — Cần và không cần `@SafeVarargs`

Method chỉ lặp qua `List<? extends T>... items` để sao chép sang `ArrayList<T>`; không ghi hay trả mảng varargs. Có thể dùng `@SafeVarargs` trên static method đó không?

**Đáp án:** có, nếu tác giả xác minh body không tạo heap pollution/để lộ array. Không nên dùng annotation cho method ghi dữ liệu không phù hợp vào varargs array.

### Bài 5 — Hai nhóm cảnh báo

`-Xlint:rawtypes` và `-Xlint:unchecked` khác nhau như thế nào?

**Đáp án:** `rawtypes` phát hiện kiểu generic được dùng mà không có đối số kiểu (`List raw`), còn `unchecked` phát hiện những thao tác generic không thể kiểm tra đầy đủ (`raw.add(42)`, cast, hoặc generic varargs).

### Bài 6 — Vì sao `List<?>[]` tạo được?

`new List<?>[2]` hợp lệ trong khi `new List<String>[2]` không hợp lệ. Vì sao?

**Đáp án:** `List<?>` với unbounded wildcard là dạng reifiable cho mục đích tạo array, không khẳng định phần tử generic cụ thể. `List<String>` không reifiable nên không thể là component type của một mảng được tạo trực tiếp bằng cú pháp đó.

### Bài 7 — Tạo adapter từ raw API

Một legacy `List` có thể chứa `String`, `Integer`, `null`; bạn cần API trả `List<String>`. Có nên dùng `(List<String>) raw` rồi `@SuppressWarnings` không?

**Đáp án:** không thể coi cast là validation. Nhận dạng `List<?>`, kiểm tra từng phần tử, chọn cách xử lý sai kiểu/`null`, rồi tạo danh sách typed mới như `checkedStrings`.

### Bài 8 — Dùng `javap` để kiểm chứng

Với `static List<String>[] pollute(List<String>... groups)`, descriptor có `String` hay chỉ `List[]`?

**Đáp án:** descriptor dùng `List[]` ở tham số và kết quả: `([Ljava/util/List;)[Ljava/util/List;`. Generic `String` có thể hiện trong Signature metadata, không thuộc descriptor runtime.

## 16. Checklist tự đánh giá

Bạn nên có thể tự giải thích:

1. Heap pollution là gì và vì sao không đồng nghĩa cảnh báo unchecked hoặc một exception.
2. Raw `List` có thể làm hỏng dữ liệu mà biến `List<String>` vẫn đang tham chiếu.
3. Generic varargs được truyền bằng một array runtime có component type bị xóa thành `List`.
4. `Object[]` alias có thể ghi `List<Integer>` vào varargs array mà runtime không kiểm tra `String`.
5. `ClassCastException` có thể xuất hiện muộn tại lời đọc typed; `ArrayStoreException` thuộc cơ chế kiểm tra phần tử mảng thực.
6. `@SafeVarargs` là một cam kết cần được chứng minh, không phải công tắc sửa lỗi an toàn kiểu.
7. `List<?>[]` khác `List<String>[]` ở mức cam kết và khả năng tạo mảng.
8. Đường nâng cấp an toàn là bỏ raw type không cần thiết, kiểm tra dữ liệu ở boundary, giới hạn unchecked casts và tránh thao tác nguy hiểm trên mảng varargs.
9. `javap -s` và `-c` cung cấp bằng chứng về erasure và cast phát sinh ở bytecode.

## Kết luận

**Heap pollution** là khi cam kết của một kiểu tham số hóa không còn phản ánh đúng đối tượng được tham chiếu. Với generic varargs, nguyên nhân đáng chú ý là **mảng runtime chỉ biết component type đã bị xóa**, nên việc ghi một list sai type argument qua `Object[]` có thể lọt qua kiểm tra store. Phép cast khi đọc với kỳ vọng `String` sau đó gây `ClassCastException`.

Hãy dùng generics typed thay raw type, tránh để lộ/sửa array varargs, chỉ áp dụng `@SafeVarargs` sau khi chứng minh body an toàn, và kiểm tra phần tử từ nguồn không đáng tin trước khi công bố `List<String>`. Như vậy Java generics tiếp tục phát huy bảo vệ kiểu ngay từ compiler thay vì đẩy lỗi về runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-002">Varargs generic được biểu diễn bằng mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Varargs generic được biểu diễn bằng mảng trong Java

## 1. Hiểu đúng bản chất của T...

Varargs (*variable-length arguments*) cho phép gọi một phương thức với số lượng đối số thay đổi mà không phải tự viết mảng trong mỗi lời gọi:

~~~java
static void log(String... messages) {
    for (String message : messages) {
        System.out.println(message);
    }
}

log("Java", "Generics", "Varargs");
~~~

Điểm kỹ thuật quan trọng: `String...` **không tạo một cơ chế lưu trữ mới**. Tham số `messages` ở trong phương thức là một **mảng String[]**.

Tương tự, một phương thức generic:

~~~java
static <T> void printAll(T... values) {
    for (T value : values) {
        System.out.println(value);
    }
}
~~~

có tham số `values` với kiểu tĩnh là **T[]**, tức **mảng các T**, chứ không phải một danh sách động riêng biệt.

Chữ ký `T...` có một cách đọc thực dụng:

> Phương thức nhận một tham số cuối cùng có kiểu mảng T[], và cú pháp varargs cho phép compiler đóng gói một số lượng đối số riêng lẻ thành array tại điểm gọi khi sử dụng dạng gọi có số lượng đối số thay đổi.

Đây là cội nguồn của cả sự tiện lợi lẫn các cảnh báo khi `T` là generic type không reifiable.

## 2. Varargs không phải ArrayList, và cũng không phải nhiều tham số độc lập

So sánh:

~~~java
static int count(String... items) {
    return items.length;
}
~~~

Có thể gọi:

~~~java
count();                       // Không có phần tử.
count("A");                    // Một phần tử.
count("A", "B");               // Hai phần tử.
count(new String[] {"A","B"}); // Truyền một mảng trực tiếp.
~~~

Trong thân hàm, biến `items` luôn là **String[]**. Nó có thuộc tính `length`, hỗ trợ truy cập chỉ số `items[0]`, và không có `add` như một List.

Biểu thức `count("A", "B")` được biên dịch theo cơ chế gọi có số lượng đối số thay đổi, tương đương về ý nghĩa với việc truyền một `String[]` chứa hai phần tử. Còn `count(new String[]{"A","B"})` truyền mảng do caller đã tạo.

Không được hình dung rằng phương thức varargs nhận hai biến riêng rẽ tên `items0` và `items1`. **Tại runtime, đây vẫn là một tham số mảng.**

Với generic varargs, quy tắc tương ứng:

~~~java
static <T> int countGeneric(T... items) {
    return items.length;
}
~~~

`items` có kiểu tĩnh `T[]`. Nhưng vì `T` là biến kiểu được Java generics xử lý qua type erasure, ta phải cẩn thận về **runtime component type** của mảng được tạo tại call site.

## 3. Quy tắc khai báo varargs trong method và constructor

Trong một method declaration, varargs parameter phải nằm ở vị trí **cuối cùng**:

~~~java
static <T> void describe(String title, T... items) {
    System.out.println(title + ": " + items.length);
}
~~~

Hai kiểu gọi có thể hợp lệ:

~~~java
describe("Languages", "Java", "Go");
describe("Numbers", 1, 2, 3);
~~~

Compiler suy luận T theo các đối số và ngữ cảnh lời gọi. Cả hai đều được đưa vào phương thức qua tham số mảng.

Những dạng sau **cố ý không biên dịch**:

~~~java
// static <T> void wrong(T... items, String title) { }
// Varargs parameter không được đứng trước một tham số khác.

// static void wrong(String... first, Integer... second) { }
// Một danh sách tham số không được có hai varargs parameters.
~~~

Ký hiệu `...` khai báo một tham số kiểu mảng nhưng có cách gọi thuận tiện. Nó không phải loại generic thứ hai và không biến T thành một giá trị runtime có thể kiểm tra sâu.

## 4. Hai giai đoạn: nguồn Java và phương thức trong bytecode

Giả sử:

~~~java
static <T> void inspect(T... values) {
    System.out.println(values.length);
}
~~~

Ở **Java source**:
- `T` là type parameter của method.
- `T...` là varargs, tức tham số `T[]` ở phía thân hàm.
- Khi caller truyền các đối số riêng lẻ, compiler lựa chọn và chuẩn bị array phù hợp theo quy tắc suy luận kiểu và varargs invocation.

Sau **type erasure**:
- Nếu T không có bound tường minh, erasure của T là Object.
- Method descriptor có tham số mảng **Object[]**.
- Call site có thể truyền array runtime kiểu cụ thể như String[], vì **String[] là subtype của Object[]**.

Ví dụ:

~~~java
inspect("Java", "Kotlin");
~~~

Ở một biên dịch thông thường, compiler có thể tạo một **String[]** tại caller rồi chuyển nó thành Object[] theo quan hệ array subtype để gọi method đã xóa kiểu. Method nhận được vẫn là array có runtime component class String, dù chữ ký đã xóa của tham số là Object[].

Đây là hai điều khác nhau:
1. **Kiểu của tham số sau erasure trong method descriptor** có thể là Object[].
2. **Kiểu thực của đối tượng array được truyền vào** có thể là String[] hoặc List[].

Không nên diễn giải câu “T bị xóa thành Object” thành “mọi array truyền qua T... đều có runtime component Object”. Điều đó sai vì runtime class của array được tạo ở caller vẫn được giữ.

### Bound của T tác động đến erasure

~~~java
static <T extends Number> void inspectNumbers(T... numbers) {
    System.out.println(numbers.length);
}
~~~

Ở đây T bị xóa về bound `Number` thay vì Object, nên tham số sau erasure có dạng `Number[]`. Khi gọi với các Integer, caller vẫn có thể truyền runtime array Integer[] tương thích với Number[].

Vì vậy, muốn phân tích chính xác bytecode, phải xem **cận của T**, kiểu của các đối số và ngữ cảnh gọi, thay vì luôn nói varargs generic chỉ là Object[].

## 5. Vì sao T... có thể có cảnh báo generic array creation?

Java cho phép khai báo:

~~~java
static void consume(List<String>... groups) {
    System.out.println(groups.length);
}
~~~

Dạng này hợp lệ ở source; bên trong `groups` là **List<String>[]** theo kiểu tĩnh.

Nhưng Java không cho tạo mảng trực tiếp:

~~~java
// List<String>[] groups = new List<String>[2];
// Lỗi: generic array creation.
~~~

`List<String>` không phải **reifiable type**: một runtime List object không mang đủ generic type argument String để JVM kiểm chứng từng phần tử.

Khi gọi:

~~~java
consume(List.of("A"), List.of("B"));
~~~

compiler vẫn phải đóng gói các đối số varargs vào **một array**. Nó tạo một array có component runtime là **List**, không phải một runtime class riêng biệt `List<String>`. Vì vậy compiler có thể cảnh báo:

~~~text
unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Thông điệp chính xác có thể khác theo phiên bản javac. Cảnh báo cho biết bước tạo array ở dạng varargs không duy trì được đầy đủ cam kết type arguments.

Đây là cách Java cho phép các API varargs generic tiện dụng tồn tại, nhưng cảnh báo ở các trường hợp compiler không thể chứng minh được an toàn.

## 6. Generic array creation bị cấm ở source nhưng varargs vẫn có thể được gọi

Hai câu sau không tương đương về quy tắc ngôn ngữ:

~~~java
// List<String>[] direct = new List<String>[2]; // Không biên dịch.

consume(List.of("A"), List.of("B")); // Có thể biên dịch với warning.
~~~

Dòng thứ nhất là một **array creation expression tường minh** có component type không reifiable, bị Java cấm.

Dòng thứ hai là **variable arity invocation** của một method được khai báo varargs. Compiler được phép tạo array thích hợp để thực thi lời gọi, nhưng có thể báo unchecked warning về sự không an toàn của generic array backing.

Vì vậy không nên ghi nhớ giản lược rằng “Java không thể tạo bất kỳ mảng nào cho generic”. Chính xác hơn:

> Java cấm một số biểu thức tạo array với component type không reifiable; lời gọi varargs có thể được chuyển thành việc truyền một array đã xóa generic, kéo theo cảnh báo và khả năng heap pollution.

Nếu phương thức dùng mảng đó đúng cách và không phơi ra alias nguy hiểm, API có thể hoạt động an toàn. Nếu thân phương thức hoặc bên ngoài lạm dụng array covariance/raw reference, dữ liệu sai kiểu có thể lọt vào.

## 7. Mảng varargs thuộc quyền sở hữu của ai?

Với một lời gọi các đối số riêng lẻ:

~~~java
inspect("A", "B");
~~~

compiler thường tạo array tại **điểm gọi**, sau đó truyền reference vào method. Method nhận một tham chiếu đến array ấy; không có quy tắc bắt buộc phải sao chép tất cả phần tử trước khi dùng.

Với:

~~~java
String[] existing = {"A", "B"};
inspect(existing);
~~~

lời gọi có thể diễn ra ở dạng **fixed arity invocation**: caller truyền array `existing` trực tiếp làm tham số cuối. Khi đó, nếu body của method thay đổi `values[0]`, nó có thể thay đổi chính `existing[0]`.

Mối quan hệ alias rất quan trọng khi thiết kế API:
- Một method chỉ đọc array rồi tính kết quả thường dễ chứng minh an toàn hơn.
- Một method sửa array, lưu array vào trường static/instance hoặc trả trực tiếp array có thể khiến caller và callee quan sát những thay đổi của nhau.
- Với **non-reifiable component type**, việc alias array qua Object[] là một đường tạo **heap pollution** đặc biệt nguy hiểm.

Không được giả định “vì tham số khai báo T... nên Java tự copy phần tử”. Varargs chủ yếu thay đổi cách viết lời gọi và cách compiler đóng gói đối số, **không tự cung cấp ownership hoặc immutability**.

## 8. Hệ quả của việc biểu diễn varargs generic bằng mảng

Ở **phương thức** nhận `List<String>... groups`, kiểu source của tham số là `List<String>[]`, nhưng runtime component của array là `List`. Vì vậy một alias `Object[]` có thể thay slot bằng một đối tượng `List<Integer>` mà runtime store check vẫn chấp nhận:

```java
static void unsafe(List<String>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(99);           // Vẫn là một List ở runtime.
    String text = groups[0].get(0);   // ClassCastException.
}
```

Dùng đoạn này **chỉ để tái hiện lỗi**, với import `java.util.List` và lời gọi `unsafe(List.of("safe"))`; nó cố ý gây cảnh báo unchecked tại declaration/call site. Trong trường hợp mảng thật là `String[]`, ghi Integer qua `Object[]` bị `ArrayStoreException` ngay tại phép ghi vì JVM biết component type String. Nhưng với mảng `List[]`, JVM không kiểm tra được type argument String trong từng list.

Cần phân biệt **mảng do compiler tạo để thực hiện variable-arity invocation** với **mảng caller truyền sẵn ở fixed-arity invocation**: method không tự sao chép mảng trong mọi trường hợp. Việc gán qua alias chỉ thay slot trong mảng, không nhất thiết sửa nội dung object List ban đầu. Bài này tập trung vào cách **varargs → array**; các bài sau phân tích kỹ rủi ro ghi alias, làm lộ mảng và cách đọc compiler diagnostics.

## 9. Chương trình Java 17+ chạy được

Lưu nội dung sau thành **GenericVarargsDemo.java**:

~~~java
import java.util.Arrays;
import java.util.List;

public class GenericVarargsDemo {
    // Chỉ quan sát array, không sửa hoặc làm lộ reference.
    @SafeVarargs
    static <T> void inspect(String tag, T... values) {
        System.out.println(
                tag + " length=" + values.length
                        + " component="
                        + values.getClass()
                                .getComponentType()
                                .getTypeName()
        );
    }

    // Không trả chính mảng varargs; tạo bản sao qua List.copyOf.
    @SafeVarargs
    static <T> List<T> safeCopy(T... values) {
        return List.copyOf(Arrays.asList(values));
    }

    // Đọc các List<String> mà không thay đổi mảng hoặc trả alias.
    @SafeVarargs
    static void showGroups(List<String>... groups) {
        System.out.println(
                "groups component="
                        + groups.getClass()
                                .getComponentType()
                                .getTypeName()
        );
        System.out.println(
                "groups=" + Arrays.toString(groups)
        );
    }

    // CỐ Ý KHÔNG AN TOÀN: để minh họa heap pollution.
    static void unsafe(List<String>... groups) {
        Object[] alias = groups;
        alias[0] = List.of(99);

        try {
            String first = groups[0].get(0);
            System.out.println(first);
        } catch (ClassCastException error) {
            System.out.println("unsafe=ClassCastException");
        }
    }

    public static void main(String[] args) {
        // Compiler tạo array khi gọi bằng nhiều phần tử riêng lẻ.
        inspect("strings", "Java", "Go");

        // Truyền một array thật do caller đã có.
        String[] explicit = {"A", "B"};
        inspect("explicit", explicit);

        // Khi T là List<String>, runtime component là raw List.
        inspect("lists", List.of("x"), List.of("y"));

        // Snapshot an toàn cho các đối số String thông thường.
        System.out.println(
                "copy=" + safeCopy("red", "blue")
        );

        // Không sửa hoặc làm lộ array varargs.
        showGroups(List.of("A"), List.of("B"));

        // Tạo lỗi có kiểm soát để quan sát ranh giới an toàn.
        unsafe(List.of("safe"));
    }
}
~~~

Biên dịch ở mức ngôn ngữ Java 17:

~~~bash
javac --release 17 -Xlint:unchecked GenericVarargsDemo.java
java GenericVarargsDemo
~~~

**Cảnh báo biên dịch được dự kiến** tại method `unsafe` và lời gọi `unsafe(List.of("safe"))` vì method không an toàn sử dụng `List<String>...` với component không reifiable. Cảnh báo đại ý:

~~~text
Possible heap pollution from parameterized vararg type List<String>
unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Các warning này không nằm trong stdout của chương trình.

**Stdout chính xác:**

~~~text
strings length=2 component=java.lang.String
explicit length=2 component=java.lang.String
lists length=2 component=java.util.List
copy=[red, blue]
groups component=java.util.List
groups=[[A], [B]]
unsafe=ClassCastException
~~~

### Những điểm cần đọc từ output

**`strings ... java.lang.String`:** khi T suy ra String và caller đưa hai chuỗi riêng lẻ, mảng thực tế có component String.

**`explicit ... java.lang.String`:** lời gọi truyền sẵn String[] cũng cho runtime component String. Output giống nhau nhưng đường truyền tham số có thể khác: caller đã tự tạo mảng thay vì compiler cần gói nhiều đối số.

**`lists ... java.util.List`:** khi T suy ra List<String>, runtime component của array được tạo là List. Điều này không chứng minh mọi phần tử của mảng là List<String> theo runtime check.

**`copy=[red, blue]`:** method sao chép phần tử vào một List không cho phép sửa đổi bằng các phương thức bình thường. Với đối số đầu vào không null như trong ví dụ, `List.copyOf` thành công. Đây là một thiết kế hữu ích để không trả chính array varargs ra ngoài.

**`groups ... java.util.List`:** showGroups đọc mảng List[] mà compiler tạo; method không tạo đường ghi vào mảng backing.

**`unsafe=ClassCastException`:** một List<Integer> được đưa vào mảng dùng như List<String>[] qua Object[] alias, và lỗi được phát hiện khi đọc phần tử đó dưới dạng String.

## 10. @SafeVarargs có ý nghĩa gì?

`@SafeVarargs` là annotation mà tác giả method/constructor dùng để cam kết việc triển khai **không thực hiện các thao tác có nguy cơ làm hỏng an toàn kiểu liên quan đến tham số varargs**. Nó có thể làm compiler không phát những cảnh báo varargs/unchecked tương ứng ở vị trí khai báo và call sites thích hợp.

Ví dụ hợp lý:

~~~java
@SafeVarargs
static <T> List<T> snapshot(T... values) {
    return List.copyOf(Arrays.asList(values));
}
~~~

Method chỉ đọc phần tử và tạo List mới, không trả mảng, không gán sai kiểu vào mảng, không lưu một alias sửa được đến mảng. Khi các giá trị đầu vào phù hợp, cách dùng này có thể an toàn đối với vấn đề heap pollution của varargs array.

**Không có phép kiểm tra runtime mới** do annotation tạo ra. `@SafeVarargs` không khiến `List<String>` thành reifiable, không làm component array biến thành List<String> ở runtime, và không tự kiểm tra từng phần tử của các List.

Nếu gắn `@SafeVarargs` vào phương thức `unsafe`, annotation không biến body đó thành an toàn. Nó chỉ đưa ra **một lời cam kết sai**, có thể che các cảnh báo ở call site; code vẫn có thể ném ClassCastException. Javac có thể đưa ra cảnh báo bổ sung hoặc không phát hiện tất cả mẫu không an toàn, nên **tác giả API phải tự chứng minh** phương thức tuân thủ hợp đồng.

### Những vị trí Java 17 cho phép @SafeVarargs

Annotation có thể áp dụng cho:
- Constructor có varargs.
- Method `static` có varargs.
- Method `final` có varargs.
- Method `private` có varargs.

Không sử dụng `@SafeVarargs` trên một instance method thông thường **có thể override** vì lời cam kết của triển khai hiện tại không chứng minh được những implementation ở lớp con cũng an toàn.

Một method dù `static` hay `final` nhưng không phải varargs cũng không phải nơi thích hợp để đặt annotation này.

### @SafeVarargs và @SuppressWarnings có giống nhau không?

Không hoàn toàn:

- `@SafeVarargs` thông báo về tính an toàn của **varargs declaration**, có hiệu lực liên quan đến cảnh báo ở lời gọi.
- `@SuppressWarnings("unchecked")` là chỉ thị suppress các cảnh báo unchecked trong phạm vi được đặt; nó không đưa ra bảo đảm ngữ nghĩa được compiler chứng minh.
- Cả hai **không kiểm tra runtime generic arguments**.

Đừng dùng bất kỳ annotation nào để giấu cảnh báo của một method đã biết có thể sửa array bằng Object[] alias không an toàn.

## 11. Xem varargs trong bytecode bằng javap

Sau khi biên dịch:

~~~bash
javap -p GenericVarargsDemo
javap -c -p GenericVarargsDemo
javap -v -p GenericVarargsDemo
~~~

Điểm cần quan sát:

1. Chữ ký method ở trình đọc class file có dấu varargs, nhưng đây vẫn là **array parameter**.
2. Với `<T> void inspect(String,T...)`, phương thức bị erasure về một descriptor dùng tham số String và Object[] vì T không có bound.
3. Ở `main`, những lời gọi variable arity có thể được javac biến thành việc tạo các array bằng bytecode tương ứng, ví dụ `anewarray java/lang/String` và `anewarray java/util/List`.
4. Với `List<String>...`, descriptor của mảng varargs dùng List[] sau erasure.
5. `javap -v` hiển thị các cờ method như **ACC_VARARGS** khi phù hợp.

Đừng dựa vào offset chỉ thị hay thứ tự bytecode chính xác của một bản javac. Điều cần hiểu là mỗi lời gọi varargs phải được quy về cách truyền **một tham số mảng** tại runtime, và generic arguments không được giữ trong array component class.

### Fixed arity so với variable arity

Với chữ ký:

~~~java
static void emit(String... values) { }
~~~

Hai lời gọi sau có thể đi theo hai dạng khác nhau:

~~~java
String[] existing = {"A", "B"};

emit(existing); // Truyền array có sẵn.
emit("A", "B"); // Compiler đóng gói hai đối số thành array.
~~~

Không phải compiler “luôn tạo thêm một mảng” ở mọi lời gọi varargs. Nếu caller truyền đúng một array phù hợp và lời gọi được giải quyết ở fixed arity, array đó được dùng trực tiếp.

## 12. Generic varargs với wildcard và reifiable type

Không phải mọi varargs có chứa generic syntax đều gây cảnh báo unchecked.

Ví dụ:

~~~java
static void inspectUnknown(List<?>... groups) {
    for (List<?> group : groups) {
        System.out.println(group.size());
    }
}
~~~

`List<?>` là parameterized type với **unbounded wildcard**, được xem là **reifiable type**. Vì vậy Java cho phép biểu thức:

~~~java
List<?>[] groups = new List<?>[2];
~~~

Và method `inspectUnknown(List<?>... groups)` có thể dùng loại tham số mảng này mà không vướng vấn đề tạo array có component type `List<String>` không reifiable.

Tuy nhiên, `List<?>` không bảo đảm phần tử của từng danh sách là String, Number hay một kiểu cụ thể nào. Sự “reifiable” ở đây chỉ tương ứng với việc runtime có thể nhận diện đối tượng là List mà **không phải kiểm tra generic type argument cụ thể**.

Tương tự:
- `Map<?, ?>...` có các type arguments là wildcard không giới hạn.
- `List<String>...` có type argument cụ thể String, không reifiable.
- `List<? extends Number>...` có wildcard có bound, không thuộc trường hợp chỉ wildcard không giới hạn.

Đừng đồng nhất “có dấu `<...>`” với “chắc chắn phải cảnh báo”. Cần kiểm tra **loại tham số generic có reifiable hay không**.

## 13. varargs của List<String> khác List<List<String>>

Cùng một mục tiêu “nhận nhiều danh sách chuỗi”, hai API có hình dạng khác nhau:

~~~java
static void processVarargs(List<String>... groups) {
    for (List<String> group : groups) {
        System.out.println(group.size());
    }
}

static void processList(List<List<String>> groups) {
    for (List<String> group : groups) {
        System.out.println(group.size());
    }
}
~~~

`processVarargs` thuận tiện khi caller có số ít các List riêng lẻ:

~~~java
processVarargs(List.of("A"), List.of("B"));
~~~

Nhưng compiler phải xây hoặc nhận một mảng `List<String>[]` theo cơ chế varargs; với component không reifiable, có thể có unchecked warnings và nguy cơ alias/heap pollution nếu method xử lý không an toàn.

`processList` nhận một **List gồm các List<String>**:

~~~java
processList(List.of(List.of("A"), List.of("B")));
~~~

Không có bước tạo `List<String>[]` bắt buộc. Compiler vẫn kiểm tra đúng generic types ở API. Khi không cần cú pháp varargs, dạng collection lồng nhau thường giúp API tránh mảng generic và các cảnh báo liên quan.

Đừng nghĩ List<List<String>> tự khiến dữ liệu hoàn toàn miễn nhiễm với unchecked casts hoặc raw types. Nhưng nó **không yêu cầu mảng varargs có component non-reifiable** trong đường truyền thông thường.

## 14. Vì sao trả trực tiếp T[] từ một varargs method có thể nguy hiểm?

Hãy xem:

~~~java
@SafeVarargs // Đặt thế này không phải cam kết an toàn đúng đắn.
static <T> T[] expose(T... values) {
    return values; // Làm lộ array backing cho caller.
}
~~~

Nếu caller truyền mảng đã tồn tại, trả mảng có thể làm lộ cùng một reference. Nếu caller gọi bằng các đối số có generic type không reifiable, mảng backing có thể là một array của raw runtime component khác với kỳ vọng của code về tính bất biến hoặc generic arguments.

Một phương thức chỉ làm `return values` không chắc gây lỗi ngay trong mọi tình huống; rủi ro phụ thuộc đường gọi và việc caller làm gì tiếp với array. Nhưng **trả trực tiếp alias của mảng varargs** là một thiết kế khó chứng minh an toàn chung, đặc biệt nếu sau đó người khác có thể sửa nó qua array covariance/raw reference.

Nếu cần kết quả là collection, sao chép:

~~~java
@SafeVarargs
static <T> List<T> toListCopy(T... values) {
    return List.copyOf(Arrays.asList(values));
}
~~~

Đoạn này giữ kiểu generic của phần tử trong chữ ký API và không trả array backing. `List.copyOf` từ chối phần tử null và thực hiện sao chép shallow: các đối tượng T bên trong vẫn là cùng object nếu chúng mutable.

Nếu cần một array mới, phải xem xét **runtime component type** cụ thể; không thể giả định `new Object[values.length]` ép thành T[] sẽ hoạt động khi caller mong đợi String[].

## 15. Khi nào bắt gặp null với varargs?

Cú pháp varargs có một góc cạnh liên quan đến **null**:

~~~java
static void show(String... values) {
    System.out.println(values == null ? "null array" : values.length);
}
~~~

Một lời gọi `show()` truyền mảng rỗng theo dạng variable arity; nhưng `show((String[]) null)` cố ý truyền **null làm chính tham số array**, không phải mảng với một phần tử null.

~~~java
show();                  // In 0.
show((String[]) null);   // In "null array".
show((String) null);     // Một phần tử null: in 1.
~~~

Một lời gọi `show(null)` không ghi kiểu ép có thể dẫn đến cảnh báo mơ hồ về cách sử dụng varargs/array parameter, tùy method và overload. Để tránh nhầm, hãy phân biệt rõ **null array** với **array chứa phần tử null**.

Trong `safeCopy`, nếu caller truyền `(String[]) null`, lời gọi có thể đưa null array vào body; `Arrays.asList(values)` không tương đương một array bình thường trong trường hợp đó, nên API nên định rõ null handling hoặc dùng `Objects.requireNonNull(values)` trước khi xử lý.

Điều này không trực tiếp là lỗi type erasure, nhưng là hệ quả của việc **tham số varargs thực chất là array**.

## 16. Cách đọc các cảnh báo compiler

Với `javac --release 17 -Xlint:unchecked`, một phương thức varargs generic không an toàn có thể sinh hai nhóm cảnh báo:

**Cảnh báo ở khai báo**:

~~~text
Possible heap pollution from parameterized vararg type List<String>
~~~

Nó nói rằng phương thức sử dụng varargs với component generic không reifiable, có thể gây heap pollution. Không phải compiler đã chứng minh thực tế dữ liệu bị hỏng ở mọi lời gọi.

**Cảnh báo ở call site**:

~~~text
unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Nó nói rằng việc compiler chuẩn bị array để thực hiện variable-arity call không bảo đảm an toàn generic đầy đủ.

Một số phương thức đã được xem là an toàn theo `@SafeVarargs` có thể không tạo những warnings này, nhưng đó là do **hợp đồng của tác giả**, không vì JVM tự kiểm tra generic arguments.

Các warnings không phải stdout của ứng dụng. Khi viết tài liệu hoặc kiểm thử, hãy tách rõ kết quả biên dịch và kết quả chạy.

## 17. Checklist thiết kế varargs generic an toàn

Khi viết phương thức `<T> f(T... values)` hoặc `f(List<String>... groups)`, hãy tự kiểm:

1. **Varargs có thật sự cần thiết?** Nếu người dùng thường có collection, nhận `List<T>` hoặc `Collection<T>` có thể rõ và an toàn hơn.
2. **Kiểu phần tử của array có reifiable không?** `String`, `List<?>` khác `List<String>`.
3. **Body có gán qua `Object[]` hoặc raw alias không?** Đây là dấu hiệu heap pollution.
4. **Body có lưu hoặc trả thẳng array không?** Có thể làm lộ alias và khó kiểm soát kiểu phần tử.
5. **Có truyền array sang code có thể sửa không?** An toàn cần xét **transitive**, không chỉ vài dòng trong method hiện tại.
6. **Có sao chép hay chỉ giữ reference?** `Arrays.asList(values)` là một fixed-size List view backed by array, trong khi `List.copyOf` tạo một List không cho sửa đổi và không tiếp tục phơi trực tiếp mảng.
7. **Có null array hoặc null elements không?** Cần hợp đồng rõ ràng.
8. **Có dùng `@SafeVarargs` không?** Chỉ gắn khi đã chứng minh an toàn. Annotation không biến kiểu non-reifiable thành reifiable.
9. **Có kiểm thử qua compiler warnings không?** Bật `-Xlint:unchecked`; kiểm tra đặc biệt các lời gọi varargs generic.
10. **Có alias và nhiều callers không tin cậy không?** Nếu có, ưu tiên thiết kế không để thao tác ghi vào backing array ngoài tầm kiểm soát.

Nếu ví dụ trong code cần intentionally unsafe behavior để dạy học, tách rõ khỏi API production và bắt lỗi tại đúng chỗ để chương trình minh họa có thể tiếp tục chạy.

## 18. Những ngộ nhận thường gặp

### “T... được Java giữ như một số lượng tham số runtime không xác định”

Không đúng. Một varargs parameter là **một tham số array**; compiler biến lời gọi nhiều đối số thành dạng truyền array khi cần.

### “T... luôn trở thành Object[] tại runtime”

Không đúng. Method descriptor sau erasure có thể là Object[] hoặc array của bound, nhưng runtime array truyền vào có thể là String[] hoặc List[] theo cách caller tạo.

### “Nếu new List<String>[n] bị cấm thì List<String>... cũng bị cấm”

Không đúng. Java cho phép **khai báo varargs parameter non-reifiable** và sử dụng cơ chế generic array creation tại điểm gọi với cảnh báo nếu cần.

### “@SafeVarargs kiểm tra mọi phần tử trong List<String>”

Không đúng. Annotation không thực hiện runtime validation; nó chỉ liên quan đến cam kết an toàn trong cách method xử lý varargs array và warnings của compiler.

### “ArrayStoreException sẽ ngăn mọi List<Integer> lọt vào List<String>[]”

Không đúng. Runtime component của array sau erasure có thể chỉ là List; List<Integer> vẫn là một List. Sai generic argument thường lộ khi đọc phần tử như String và xảy ra `ClassCastException`.

### “Trả Arrays.asList(values) chắc chắn không làm lộ mảng”

Không đúng. `Arrays.asList` trả một fixed-size list **được backed bởi array đầu vào**. Thay đổi list bằng `set` hoặc thay đổi array có thể ảnh hưởng lẫn nhau. Nếu mục tiêu là tránh alias, hãy dùng giải pháp sao chép phù hợp như `List.copyOf`, đồng thời xem xét mutable elements.

### “Phương thức có @SafeVarargs chắc chắn an toàn”

Không đúng. Compiler không thể chứng minh toàn diện cách mọi body và những hàm được gọi xử lý dữ liệu. Nếu tác giả annotation vi phạm hợp đồng, vẫn có nguy cơ heap pollution.

## 19. Câu hỏi tự kiểm tra

**1.** Trong phương thức `static <T> void f(T... xs)`, kiểu của xs bên trong thân hàm là gì?

**Đáp án:** `T[]` ở source Java. Sau erasure, descriptor sử dụng array của erasure(T), mặc định là Object[] nếu T không có bound tường minh.

**2.** Compiler cần tạo gì khi caller viết `f("A", "B")`?

**Đáp án:** Theo dạng gọi variable arity, compiler đóng gói hai String vào một array thích hợp và truyền array làm tham số varargs. Runtime method nhận một array.

**3.** Vì sao `f(List.of("A"), List.of("B"))` có thể tạo array runtime có component type List thay vì List<String>?

**Đáp án:** `List<String>` không reifiable do erasure. Component runtime chỉ biểu diễn được raw List; unchecked generic array creation có thể được báo.

**4.** Tại sao `Object[] alias = groups; alias[0] = List.of(123);` trong method nhận `List<String>...` có thể thành công?

**Đáp án:** Array backing có runtime component là List, nên List<Integer> vẫn phù hợp lớp runtime cần gán. Array store không kiểm tra String trong generic argument.

**5.** Lỗi gì có thể xảy ra khi gọi `String s = groups[0].get(0)` sau khi mảng bị nhiễm?

**Đáp án:** `ClassCastException` khi runtime phát hiện phần tử Integer không thể dùng như String.

**6.** `@SafeVarargs` có làm `new List<String>[2]` trở nên hợp lệ không?

**Đáp án:** Không. Annotation không thay đổi quy tắc array creation với non-reifiable component types.

**7.** Gửi `String[]` vào `f(T... values)` có tạo bản sao tự động không?

**Đáp án:** Không nhất thiết. Nếu lời gọi ở dạng fixed arity và truyền array tương thích, method có thể nhận chính array caller đã tạo.

**8.** Với bài toán “nhận nhiều danh sách chuỗi” mà không cần cú pháp varargs, cấu trúc nào thường tránh rủi ro generic array?

**Đáp án:** `List<List<String>>`, vì không cần xây `List<String>[]` tại điểm gọi.

**9.** `List<?>...` và `List<String>...` có giống nhau về tính reifiable không?

**Đáp án:** Không. `List<?>` chỉ chứa unbounded wildcard nên reifiable; `List<String>` có type argument cụ thể và không reifiable.

**10.** Vì sao `List.copyOf(Arrays.asList(values))` thường là hướng đáng cân nhắc thay vì trả thẳng values?

**Đáp án:** Nó tạo một List không cho sửa đổi bằng API thông thường và không tiếp tục phơi ra backing varargs array. Tuy nhiên sao chép chỉ shallow, từ chối null, và không thay thế xác thực dữ liệu bên trong các object mutable.

## 20. Ghi nhớ

**`T...` là cú pháp varargs cho một tham số `T[]`.** Ở runtime, JVM nhận array; với generic type variables, chữ ký method chịu type erasure, còn call site có thể tạo array mang runtime component cụ thể hoặc raw component khi các type arguments không reifiable.

Điểm nguy hiểm xuất hiện rõ với `List<String>...`: backing array runtime có thể là List[], cho phép alias Object[] đưa một List<Integer> vào và làm hỏng cam kết List<String>[]. Cảnh báo unchecked phản ánh nguy cơ đó.

Khi thiết kế API:
- Không sửa hoặc làm lộ backing array của varargs khi điều đó có thể phá bất biến kiểu.
- Chỉ sử dụng `@SafeVarargs` sau khi chứng minh body và những đường gọi liên quan an toàn.
- Ưu tiên copy hoặc nhận `List<T>`/`List<List<T>>` khi không cần varargs.
- Kiểm tra compiler warnings và nắm rõ chỗ nào xảy ra array store check, chỗ nào xảy ra cast khi đọc generic value.

Hiểu được mối liên hệ giữa **varargs → array → erasure → unchecked warnings → heap pollution** sẽ giúp dùng generic varargs đúng và tránh các lỗi chỉ bộc lộ muộn tại runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-003">Cảnh báo tại nơi khai báo và nơi gọi phương thức varargs</a>

<details>
<summary>Xem chi tiết</summary>

# Cảnh báo tại nơi khai báo và nơi gọi phương thức varargs trong Java Generics

## 1. Tình huống mở đầu

Java cho phép phương thức nhận số lượng đối số thay đổi (*varargs*) qua dấu `...`:

```java
static void show(String... values) {
    for (String value : values) {
        System.out.println(value);
    }
}
```

Cách gọi:

```java
show("Java", "Generics");
```

rất tiện vì người gọi không phải tự tạo `String[]`. Khi kết hợp varargs với **Generics**, tình hình phức tạp hơn:

```java
static void showFirst(List<String>... lists) {
    System.out.println(lists[0].get(0));
}

// Ở một method khác:
showFirst(List.of("Java"), List.of("Generics"));
```

`javac` có thể phát **hai cảnh báo tại hai vị trí khác nhau**:

- **Tại khai báo** `List<String>... lists`: `Possible heap pollution from parameterized vararg type List<String>`.
- **Tại lời gọi** `showFirst(...)`: `unchecked generic array creation for varargs parameter of type List<String>[]`.

Cả hai liên quan đến cơ chế **mảng varargs và type erasure**, nhưng không đồng nghĩa rằng compiler đã phát hiện lỗi thực tế trong dữ liệu. Chúng là tín hiệu: kiểu mảng được dùng để truyền đối số có kiểu thành phần **không reifiable**, nên compiler/runtime không thể xác minh mọi type argument bằng array store check.

Bài này giải thích khi nào cảnh báo xuất hiện, vì sao chúng chỉ đến các vị trí khác nhau, lúc nào có rủi ro *heap pollution*, cách đọc `javac -Xlint`, phân biệt varargs reifiable và generic varargs, và cách thiết kế hoặc chú thích API an toàn bằng `@SafeVarargs` khi có đủ cơ sở.

## 2. Varargs thực chất là một tham số mảng

Ở source:

```java
static void printAll(String... items) {
    for (String item : items) {
        System.out.println(item);
    }
}
```

Tham số `items` **bên trong phương thức** có kiểu `String[]`. Lời gọi:

```java
printAll("a", "b");
```

thường được compiler xử lý như một lời gọi truyền mảng `String[]` chứa các đối số.

Bạn cũng có thể gọi bằng mảng có sẵn:

```java
String[] names = {"a", "b"};
printAll(names);
```

Một varargs method về bản chất có tham số mảng ở vị trí cuối cùng; vì thế:

```java
static void take(List<String>... groups) {
    System.out.println(groups.length);
}
```

có tham số `groups` kiểu source là **`List<String>[]`**, dù cú pháp được viết dưới dạng `...`.

Vấn đề: Java không cho biểu thức tạo mảng thông thường:

```java
// Không hợp lệ:
List<String>[] array = new List<String>[2];
```

vì `List<String>` không phải **reifiable type**. Thế nhưng cú pháp varargs lại cho phép compiler xử lý lời gọi `take(List.of("a"), List.of("b"))` bằng một mảng runtime có component `List`. Đây chính là chỗ cần cảnh báo.

### 2.1. Hai cách gọi varargs

Giả sử:

```java
static void count(String... words) {
    System.out.println(words.length);
}
```

Có hai hình thức quan trọng:

```java
count("a", "b");                 // Variable-arity invocation.
count(new String[] {"a", "b"});  // Fixed-arity invocation với array thật.
```

Ở cách gọi thứ nhất, compiler sắp xếp các đối số thành array. Ở cách gọi thứ hai, caller chủ động cung cấp array.

Với `List<T>...`, cảnh báo **tại lời gọi** thường nhắm đến trường hợp compiler phải tạo array varargs **ngầm** với kiểu thành phần non-reifiable. Một lời gọi dùng sẵn array có thể không phát đúng cảnh báo “unchecked generic array creation” tại dòng gọi, nhưng cách array đó được tạo/lấy từ đâu vẫn phải được kiểm tra. Không nên chỉ dựa vào việc dòng gọi không có warning để kết luận API an toàn.

## 3. Vì sao kiểu thành phần của varargs quan trọng?

Mảng Java giữ runtime component type:

```java
String[] strings = new String[1];
Object[] alias = strings;
alias[0] = 123; // ArrayStoreException.
```

JVM biết mảng thật là `String[]` và từ chối giá trị `Integer`.

Nhưng với:

```java
List<String> words = List.of("Java");
List<Integer> ids = List.of(123);
```

`List<String>` và `List<Integer>` đều có runtime interface cơ bản là `List`; type argument không được chuyên biệt hóa thành class runtime riêng.

Do đó nếu có một array chứa `List` và code đưa `List<Integer>` vào ô mà code khác **tin** là `List<String>`, array store check chỉ xác minh object là `List` — nó không duyệt từng phần tử bên trong.

Trong trường hợp varargs:

```java
static void method(List<String>... groups) {
    // Trong method, groups là List<String>[] ở source.
}
```

type erasure khiến JVM không có component class runtime cho `List<String>` riêng biệt. Compiler cảnh báo rằng code bên trong hoặc các alias có thể phá hợp đồng phần tử của array/list.

### 3.1. Reifiable và non-reifiable

| Loại varargs | Kiểu thành phần của array | Cảnh báo unchecked varargs thông thường? |
| --- | --- | --- |
| `String... values` | `String` reifiable | Không |
| `int... values` | Primitive `int` | Không |
| `List<?>... groups` | `List<?>` reifiable | Không, nếu không có thao tác unchecked khác |
| `Map<?, ?>... maps` | `Map<?, ?>` reifiable | Không vì chính varargs component |
| `List... groups` | Raw `List` reifiable | Không vì non-reifiable component; có thể có cảnh báo `rawtypes` |
| `List<String>... groups` | `List<String>` non-reifiable | Có thể có warning ở khai báo và lời gọi |
| `List<? extends Number>... groups` | Wildcard có bound, non-reifiable | Có thể có warning |
| `<T> void run(T... values)` | `T` không reifiable | Có thể có warning |
| `<T> void run(List<T>... groups)` | `List<T>` không reifiable | Có thể có warning |

**Dấu `?` chỉ đủ cho reifiability khi là wildcard hoàn toàn không giới hạn và tất cả type arguments ở kiểu tham số hóa đều như vậy.** `List<? extends Number>` vẫn là non-reifiable; không thể coi nó tương đương `List<?>`.

Một warning được gắn với varargs component **không có nghĩa** mọi code dùng generic đều không an toàn. Chẳng hạn một method `<T> T first(List<T> values)` không phải varargs, nên không có cơ chế array ngầm này.

## 4. Cảnh báo tại nơi khai báo phương thức

Xem:

```java
static void printFirst(List<String>... groups) {
    System.out.println(groups[0].get(0));
}
```

Khi bật:

```bash
javac --release 17 -Xlint:unchecked VarargsWarningDemo.java
```

`javac` phát warning ở vị trí khai báo varargs:

```text
warning: [unchecked] Possible heap pollution from parameterized vararg type List<String>
```

Thông báo này có nghĩa **về mặt cấu trúc**, tham số cuối được biểu diễn như một array có component `List<String>` non-reifiable, và heap pollution *có thể* xảy ra khi thao tác không an toàn trên array hoặc alias.

Nó không chứng minh thân `printFirst` đã ghi sai bất kỳ phần tử nào. Nếu chỉ đọc và không làm array thoát ra ngoài, method đó có thể thực tế an toàn. Compiler vẫn cảnh báo vì kiểu khai báo tạo điều kiện cho các thao tác không kiểm tra được.

### 4.1. Khai báo method có type parameter riêng

```java
static <T> int countElements(List<T>... groups) {
    int total = 0;
    for (List<T> group : groups) {
        total += group.size();
    }
    return total;
}
```

Ở đây `T` là type parameter của **method**, còn mỗi đối số varargs là một `List<T>`. `List<T>` non-reifiable, nên compiler phát cảnh báo:

```text
warning: [unchecked] Possible heap pollution from parameterized vararg type List<T>
```

Đừng nhầm type variable của method với phần tử của array. Java không có cách tạo một class array runtime có component specialization `List<T>` với `T` thay đổi theo từng lời gọi.

### 4.2. Cảnh báo ở khai báo có thể tồn tại dù thân method chỉ đọc

```java
static <T> int countElements(List<T>... groups) {
    int result = 0;
    for (List<T> group : groups) {
        result += group.size();
    }
    return result;
}
```

Method chỉ đọc `size`, không sửa `groups` hay danh sách con. Rủi ro trong implementation này thấp nếu không có alias bên ngoài sửa array. Nhưng compiler không phải một hệ thống chứng minh tổng quát cho mọi thiết kế method generic varargs.

Điều cần làm là **review invariant và cách xử lý array**: có ghi vào slot không, có tiết lộ array cho code khác không, có alias bằng `Object[]` không, có ép kiểu hoặc truyền array sang API không kiểm soát không? Sau review, mới cân nhắc `@SafeVarargs` nếu phương thức đủ điều kiện.

## 5. Cảnh báo tại nơi gọi phương thức

Định nghĩa:

```java
static void printFirst(List<String>... groups) {
    System.out.println(groups[0].get(0));
}
```

Lời gọi:

```java
printFirst(List.of("Java"), List.of("Generics"));
```

có thể phát cảnh báo:

```text
warning: [unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
```

Nội dung chính xác của thông báo có thể khác theo context suy luận kiểu, nhưng ý nghĩa là: compiler phải cung cấp array chứa các đối số varargs mà kiểu thành phần ở source là `List<String>` — một kiểu non-reifiable — nên việc tạo/đưa array đó qua ranh giới varargs không được kiểm tra generic đầy đủ.

**Điểm phân biệt:** cảnh báo khai báo nói API nhận varargs với kiểu thành phần không reifiable; cảnh báo ở lời gọi nói **tại đây** compiler đang tạo/cung cấp array varargs non-reifiable.

### 5.1. Type inference khiến thông báo ở lời gọi có kiểu cụ thể

Với:

```java
static <T> int countElements(List<T>... groups) {
    int count = 0;
    for (List<T> group : groups) count += group.size();
    return count;
}
```

Lời gọi:

```java
countElements(List.of("A", "B"), List.of("C"));
```

khi `javac` suy luận `T = String` có thể sinh:

```text
warning: [unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
```

Ở khai báo, thông báo dùng `List<T>`; ở lời gọi, type inference đã xác định `String`, nên thông báo có thể hiển thị `List<String>[]`. Đây không phải mâu thuẫn: cùng một rủi ro được nhìn từ **hai bối cảnh khác nhau**.

Không được kết luận rằng vì caller tự tạo `List.of("A")` có generic type safety, mảng varargs `List<String>[]` chắc chắn reifiable. Collection được tạo an toàn và cách **đóng gói vào array generic** là hai vấn đề khác nhau.

### 5.2. Một phương thức có thể sinh hai warning

Bật lint cho cùng file có thể thấy warning tại cả dòng định nghĩa:

```java
static void printFirst(List<String>... groups) { ... }
```

và dòng sử dụng:

```java
printFirst(List.of("Java"));
```

Hai warning không phải compiler đếm một lỗi hai lần. Chúng phản ánh **hai bề mặt rủi ro**: thiết kế API và việc tạo mảng tại caller.

Bạn có thể xử lý một trong hai nhưng vẫn còn vấn đề khác nếu phương thức hoặc lời gọi khác tiếp tục sử dụng cấu trúc không kiểm soát.

## 6. Tại sao compiler cảnh báo dù body trông vô hại?

Một method chỉ đọc `size()` của `List<String>...` có thể chạy đúng mà vẫn có **declaration/call-site warnings**: type argument String không reifiable trong array được chuẩn bị cho varargs call. Heap pollution chỉ **thực sự hình thành** khi có đường ghi/cast làm một biến generic chứa dữ liệu không đúng hợp đồng.

Đường gây lỗi điển hình:

```java
static void corrupt(List<String>... groups) {
    Object[] alias = groups;       // Cùng mảng, không copy.
    alias[0] = List.of(42);       // Component runtime chỉ là List.
    String item = groups[0].get(0); // ClassCastException khi đọc.
}
```

Phương thức minh họa trên cần import `java.util.List`, đặt trong class và **không dùng production**. Nếu thử ghi một String trực tiếp vào array runtime List[], JVM lại có thể phát `ArrayStoreException`. Vì thế array store check **vẫn tồn tại**, chỉ không nhận biết generic argument bên trong List.

Khi chẩn đoán, đừng suy từ việc có warning rằng code chắc chắn làm sai dữ liệu. Hãy tìm thao tác ghi/escape cụ thể rồi quyết định có thể cam kết `@SafeVarargs` hay nên thay API. **Nội dung chính của bài này là hai vị trí cảnh báo**: declaration mô tả nguy cơ của signature; call site mô tả việc tạo array non-reifiable tại lần gọi.

## 7. `@SafeVarargs` giải quyết điều gì?

Java có annotation:

```java
@SafeVarargs
```

Dùng khi tác giả một varargs method hoặc constructor **cam kết** implementation không thực hiện các thao tác không an toàn trên array varargs có component non-reifiable.

Ví dụ method an toàn:

```java
@SafeVarargs
static <T> List<T> flatten(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return result;
}
```

Method này:

- Chỉ duyệt các danh sách nguồn.
- Đưa phần tử vào một `ArrayList<T>` mới bằng API generic đã được kiểm tra.
- Không ghi một list sai kiểu vào array `groups`.
- Không công bố array `groups` cho code khác sửa.
- Trả **list mới**, không trả array varargs hoặc alias tới nó.

Đây là trường hợp có thể có cơ sở để dùng `@SafeVarargs`. Khi có annotation hợp lệ, compiler có thể bỏ những warning *unchecked generic varargs* ở **khai báo và nơi gọi** liên quan tới method đó.

**Quan trọng:** annotation **không** làm `List<T>` reifiable, không tạo runtime component type mới, không validate từng phần tử và không tự chứng minh code an toàn.

### 7.1. `@SafeVarargs` khác `@SuppressWarnings("unchecked")`

`@SuppressWarnings("unchecked")` là suppression cảnh báo trong một phạm vi nhất định, có thể áp dụng cho nhiều loại thao tác unchecked khác nhau.

`@SafeVarargs` là lời cam kết đặc thù cho varargs method/constructor về cách sử dụng non-reifiable varargs parameter. Nó tác động tới các cảnh báo varargs có liên quan, bao gồm ở caller, nhưng không phải một chứng nhận được JVM cưỡng chế.

Đừng gắn `@SafeVarargs` lên một method chỉ để làm output lint sạch khi body còn gán alias hoặc đưa list sai kiểu vào array.

### 7.2. Điều kiện áp dụng annotation trong Java 17

`@SafeVarargs` được phép tại:

- `static` varargs method.
- `final` instance varargs method.
- `private` instance varargs method (được phép từ Java 9).
- Constructor có tham số varargs.

Nó không hợp lệ trên một instance method **có thể bị override** và không hợp lệ trên method **không phải varargs**.

Ví dụ hợp lệ về **vị trí gắn**:

```java
@SafeVarargs
static <T> void staticMethod(T... values) {
    // Phải bảo đảm nội dung xử lý an toàn.
}
```

```java
class Example {
    @SafeVarargs
    private final <T> void privateMethod(T... values) {
        // Cần bảo đảm hợp đồng an toàn.
    }
}
```

Khai báo `private final` ở đây chỉ minh họa điều kiện được phép; `private` đã đủ cho vị trí gắn annotation trong Java 17. Đừng nhầm việc annotation hợp lệ về cú pháp với việc method thực sự an toàn.

### 7.3. Compiler có thể cảnh báo ngay trong method dù đã có annotation

Xét method **không an toàn**, cố tình gắn annotation sai về ngữ nghĩa:

```java
@SafeVarargs
static <T> void lie(List<T>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(42);
}
```

Với:

```bash
javac --release 17 -Xlint:varargs -Xlint:unchecked UnsafeAnnotationDemo.java
```

`javac` đã được kiểm chứng phát warning ở dòng alias:

```text
warning: [varargs] Varargs method could cause heap pollution from non-reifiable varargs parameter groups
```

Điều này cho thấy ngay cả khi annotation khiến một số cảnh báo varargs tại khai báo/caller biến mất, các thao tác có vẻ không an toàn trong body vẫn có thể bị `-Xlint:varargs` cảnh báo.

**Không nên để annotation này trong code thật** khi method có khả năng phá type invariant. Hãy sửa thiết kế body, không chỉ dập warning.

## 8. Những hành vi khiến varargs API không an toàn

Khi review phương thức có `List<T>...` hoặc `T...`, hãy chú ý:

### 8.1. Ghi vào array qua alias rộng

```java
Object[] alias = groups;
alias[0] = List.of(42);
```

Đây là ví dụ kinh điển gây heap pollution với `List<T>[]`. Dù `groups` khai báo là `List<String>[]`, runtime array chỉ biết component `List`.

### 8.2. Trả nguyên varargs array

```java
@SafeVarargs // Không phải lời khuyên dùng annotation ở đây.
static <T> List<T>[] expose(List<T>... groups) {
    return groups;
}
```

Nếu array lọt ra ngoài cho caller hoặc mã khác giữ và sửa, invariant có thể bị phá. Với `-Xlint:varargs`, compiler có thể cảnh báo về việc để non-reifiable varargs array thoát khỏi phạm vi kiểm soát.

Nếu mục tiêu là trả dữ liệu, hãy cân nhắc trả `List<List<T>>` mới hoặc một cấu trúc immutable/copy đã được thiết kế, không tiết lộ array varargs.

### 8.3. Truyền array cho code không kiểm soát

```java
static void forward(Object[] values) {
    // Method này có thể thay các phần tử trong array.
}
```

Nếu một method có generic varargs gọi một API nhận `Object[]` rồi cho phép API đó sửa array, không thể khẳng định varargs an toàn chỉ vì code hiện tại không trực tiếp gán `groups[0]`.

Một *safe varargs method* cần kiểm soát cả alias và những method được gọi với array. **Không có thao tác ghi trong vài dòng hiện tại chưa đủ** nếu array có thể escape tới nơi khác.

### 8.4. Mở quyền ghi vào các list con

Ngay cả khi array không được ghi trực tiếp, code dùng raw alias của từng list để thêm phần tử sai kiểu vẫn có thể gây heap pollution:

```java
List raw = groups[0];
raw.add(42); // Unchecked call có thể phá List<String>.
```

Đây là vấn đề raw type kết hợp với varargs. Tránh raw alias, unchecked cast và mutation khi hợp đồng type argument không được bảo đảm.

## 9. Varargs reifiable: khi nào không cần `@SafeVarargs`?

### 9.1. `List<?>...`

```java
static int countUnknown(List<?>... groups) {
    int total = 0;
    for (List<?> group : groups) {
        total += group.size();
    }
    return total;
}
```

`List<?>` là reifiable vì type argument là wildcard không giới hạn. Do đó parameter của varargs này không gặp cùng cảnh báo về non-reifiable component.

Call hợp lệ:

```java
int count = countUnknown(
        List.of("a"),
        List.of(1, 2)
);
// count == 3
```

Dấu `?` không cho phép bạn đọc phần tử thành một kiểu cụ thể như `String` nếu chưa kiểm tra. Nhưng method chỉ tính kích thước nên không cần biết type argument.

**Chỉ thay `List<T>...` bằng `List<?>...` khi quan hệ kiểu `T` thực sự không cần thiết.** Nếu method phải liên kết kiểu đầu vào với kết quả (`List<T>`), xóa `T` có thể làm API mất type safety hoặc khả năng suy luận kiểu có ý nghĩa.

### 9.2. `String...`, `int...`

```java
static int sum(int... values) {
    int result = 0;
    for (int value : values) result += value;
    return result;
}
```

`int` là kiểu primitive reifiable. `String` cũng reifiable. Những varargs này không có rủi ro **generic array component non-reifiable** như `List<T>...`.

Method vẫn có thể chứa lỗi logic hoặc mutation không mong muốn, nhưng đó không phải cảnh báo unchecked generic array creation đang xét.

### 9.3. `List<?>...` không đồng nghĩa `List<String>...`

Nếu API phải bảo đảm tất cả nhóm chứa `String`, `List<?>...` không tự kiểm tra điều đó. `List<?>` có thể chứa `String`, `Integer`, `Boolean` và các giá trị khác. Đổi chữ ký chỉ để làm warning biến mất có thể nới lỏng hợp đồng sai mục đích.

Khi cần cùng một kiểu `T`, dùng generics phù hợp, hoặc cân nhắc truyền `List<? extends List<T>>` thay cho varargs nếu muốn tránh array ngầm.

## 10. Ví dụ Java 17 hoàn chỉnh: cả hai vị trí cảnh báo

Tạo tệp **`VarargsWarningDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VarargsWarningDemo {
    static void printFirst(List<String>... batches) {
        System.out.println("first=" + batches[0].get(0));
    }

    static <T> int countElements(List<T>... batches) {
        int total = 0;
        for (List<T> batch : batches) total += batch.size();
        return total;
    }

    @SafeVarargs
    static <T> List<T> flatten(List<? extends T>... batches) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> batch : batches) result.addAll(batch);
        return result;
    }

    static int countUnknown(List<?>... batches) {
        int total = 0;
        for (List<?> batch : batches) total += batch.size();
        return total;
    }

    static <T> T corruptAndRead(List<T>... batches) {
        Object[] alias = batches;
        alias[0] = List.of(42);
        return batches[0].get(0);
    }

    public static void main(String[] args) {
        printFirst(List.of("Java"), List.of("Generics"));

        System.out.println("count="
                + countElements(List.of("A", "B"), List.of("C")));

        System.out.println("flatten="
                + flatten(List.of(1, 2), List.of(3)));

        System.out.println("unknown="
                + countUnknown(List.of("x"), List.of(1, 2)));

        try {
            String item = corruptAndRead(List.of("safe"));
            System.out.println(item);
        } catch (ClassCastException ex) {
            System.out.println("pollution=" + ex.getClass().getSimpleName());
        }
    }
}
```

Lưu ý `import java.util.Arrays` không được dùng trong body; đây là import thừa không ảnh hưởng biên dịch và có thể bỏ khi đưa code vào dự án. Ba method `printFirst`, `countElements`, `corruptAndRead` **cố ý giữ chữ ký non-reifiable varargs không có `@SafeVarargs`** để người học quan sát warning. Trong đó `corruptAndRead` cố tình không an toàn; không đưa vào production.

Biên dịch:

```bash
javac --release 17 -Xlint:unchecked -Xlint:varargs VarargsWarningDemo.java
```

Với source trên, `javac` đã phát **6 cảnh báo**:

| Vị trí | Nhóm cảnh báo | Nội dung chính |
| --- | --- | --- |
| Khai báo `printFirst(List<String>...)` | `[unchecked]` | `Possible heap pollution from parameterized vararg type List<String>` |
| Khai báo `countElements(List<T>...)` | `[unchecked]` | `Possible heap pollution from parameterized vararg type List<T>` |
| Khai báo `corruptAndRead(List<T>...)` | `[unchecked]` | `Possible heap pollution from parameterized vararg type List<T>` |
| Lời gọi `printFirst(...)` | `[unchecked]` | `unchecked generic array creation ... List<String>[]` |
| Lời gọi `countElements(...)` | `[unchecked]` | `unchecked generic array creation ... List<String>[]` |
| Lời gọi `corruptAndRead(...)` | `[unchecked]` | `unchecked generic array creation ... List<String>[]` |

Không có cảnh báo varargs từ `flatten(...)` vì annotation được dùng trên method tĩnh có body an toàn trong ví dụ. `countUnknown(...)` không cần annotation vì component `List<?>` reifiable.

Tên class, số dòng, đường dẫn source và một phần chi tiết diagnostic có thể phụ thuộc JDK. Hãy quan sát **thông điệp** và vị trí khai báo/lời gọi, thay vì khẳng định caret luôn ở cùng một cột.

Chạy:

```bash
java VarargsWarningDemo
```

Stdout chính xác:

```text
first=Java
count=3
flatten=[1, 2, 3]
unknown=3
pollution=ClassCastException
```

### 10.1. Diễn giải output

`first=Java` và `count=3` cho thấy method generic varargs có thể thực thi đúng dù có compiler warning. Warning báo **rủi ro cấu trúc**, không phải dữ liệu chắc chắn đang bị sai.

`flatten=[1, 2, 3]` minh họa cách gom phần tử vào list mới không làm varargs array escape hoặc bị thay bởi list sai kiểu.

`unknown=3` chứng minh một API dùng `List<?>...` vẫn có thể xử lý nhiều loại list mà không cần một type variable liên kết chúng.

`pollution=ClassCastException` là hậu quả có chủ đích từ method unsafe: nó ghi `List<Integer>` qua alias `Object[]` rồi caller dùng giá trị như `String`.

## 11. Ví dụ cho biết `@SafeVarargs` không phải chứng nhận an toàn

Một tệp minh họa riêng **`UnsafeAnnotationDemo.java`**:

```java
import java.util.List;

class UnsafeAnnotationDemo {
    @SafeVarargs
    static <T> void lie(List<T>... groups) {
        Object[] objects = groups;
        objects[0] = List.of(42);
    }

    static void invoke() {
        lie(List.of("ok"));
    }
}
```

Biên dịch:

```bash
javac --release 17 -Xlint:varargs -Xlint:unchecked UnsafeAnnotationDemo.java
```

`javac` đã cảnh báo:

```text
warning: [varargs] Varargs method could cause heap pollution
from non-reifiable varargs parameter groups
```

Cảnh báo nằm tại thao tác dùng alias của varargs array. Annotation không làm body an toàn và không bảo đảm mọi compiler/toolchain có thể xác minh tất cả nguy cơ thực tế.

**Cách sửa:** bỏ alias có quyền ghi không kiểm soát; không thay list trong varargs array bằng dữ liệu có type argument khác; nếu chỉ cần dữ liệu đầu ra, tạo collection mới với kiểu rõ ràng.

### 11.1. Annotation bị từ chối ở phương thức không hợp lệ

Tệp minh họa lỗi **`InvalidAnnotationDemo.java`**:

```java
import java.util.List;

class InvalidAnnotationDemo {
    @SafeVarargs
    void notFinal(List<String>... values) {
    }

    @SafeVarargs
    static void notVarargs(List<String> values) {
    }
}
```

Chạy:

```bash
javac --release 17 InvalidAnnotationDemo.java
```

Compiler từ chối cả hai trường hợp:

```text
Invalid SafeVarargs annotation. Instance method ... is neither final nor private.
Invalid SafeVarargs annotation. Method ... is not a varargs method.
```

Những ví dụ này dùng để nhận diện giới hạn cú pháp của `@SafeVarargs`; chúng **cố tình không biên dịch**.

Không nên thay `void notFinal` thành `final void notFinal` chỉ nhằm qua compiler nếu bản thân method vẫn làm heap pollution. Annotation vừa cần **đúng vị trí**, vừa cần **đúng cam kết an toàn**.

## 12. Cách xử lý cảnh báo hợp lý khi thiết kế API

Khi thấy warning ở khai báo hoặc lời gọi, đừng lập tức thêm suppression. Hãy quyết định theo nhu cầu thật của API.

### Hướng A — Dùng `List<?>...` nếu chỉ quan sát các list

```java
static int totalSize(List<?>... lists) {
    int total = 0;
    for (List<?> list : lists) total += list.size();
    return total;
}
```

Hợp đồng rõ ràng: nhận nhiều list có type argument bất kỳ, chỉ tính size. Không cần `<T>`, vì không có quan hệ cùng `T` phải bảo toàn.

### Hướng B — Dùng collection bên ngoài thay varargs array

Nếu caller đã có nhiều list cùng một kiểu `T`, có thể nhận `List<? extends List<? extends T>>` hoặc chữ ký phù hợp hơn với nghiệp vụ:

```java
static <T> List<T> flattenGroups(
        List<? extends List<? extends T>> groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return result;
}
```

Đây là method minh họa cần import `java.util.List` và `java.util.ArrayList`. Cách gọi:

```java
List<String> result = flattenGroups(
        List.of(List.of("a", "b"), List.of("c")));
```

Không cần compiler tự tạo một `List<T>[]` varargs array. Phương pháp này thường phù hợp khi số nhóm đã có sẵn trong collection hoặc cấu trúc cần chia sẻ giữa nhiều API.

### Hướng C — Giữ varargs nhưng chứng minh method an toàn

Nếu fluent API cần cú pháp truyền nhiều đối số tiện lợi, giữ `List<? extends T>...` nhưng:

- Không ghi phần tử sai kiểu vào array.
- Không trả/giữ/thả array tới mã có thể sửa.
- Không tạo raw/unchecked alias phá contract.
- Chỉ dùng giá trị đọc được qua API generic được compiler kiểm tra.
- Trả collection mới hoặc kết quả immutable khi hợp đồng phù hợp.
- Gắn `@SafeVarargs` **chỉ khi** method/constructor thuộc nhóm được phép và các điều kiện an toàn đã được chứng minh.

### Hướng D — Sửa nguồn dữ liệu hoặc ownership

Một method có vẻ chỉ đọc nhưng nhận được array hoặc list mutable từ bên ngoài vẫn cần hiểu ownership. Nếu code khác giữ alias và sửa đồng thời, vấn đề có thể vượt ra ngoài generic varargs warning (đồng bộ hóa, snapshot, validation). Không giả định `@SafeVarargs` làm đầu vào immutable hoặc thread-safe.

## 13. Đọc warning theo vị trí và loại

Một cách chẩn đoán từng bước:

1. **Bật lint rõ ràng**: `javac -Xlint:unchecked -Xlint:varargs`.
2. **Xác định caret nằm ở khai báo hay lời gọi**; đừng chỉ nhìn số warnings.
3. Tại khai báo, ghi lại component type của varargs: `T`, `List<T>`, `List<String>`, `List<?>`, `String`.
4. Tại lời gọi, kiểm tra compiler có phải tạo mảng varargs ngầm hay caller truyền array có sẵn; xem type inference chọn `T` thành gì.
5. Xem body: có `Object[]` alias, gán array slot, trả varargs array hoặc truyền sang code có thể sửa không?
6. Kiểm tra các raw types, unchecked casts, thao tác với list con và luồng dữ liệu bên ngoài.
7. Chọn sửa API (`List<?>...`, collection ngoài) hoặc giữ generic varargs với `@SafeVarargs` khi có chứng cứ.
8. Biên dịch lại với lint và chạy cả đường hợp lệ/đường lỗi để xác nhận đã giải quyết **nguyên nhân**, không chỉ ẩn thông báo.

### 13.1. Bảng chẩn đoán nhanh

| Thông báo/hiện tượng | Nơi xuất hiện thường gặp | Cách đọc đúng |
| --- | --- | --- |
| `Possible heap pollution from parameterized vararg type List<T>` | Khai báo method | Parameter varargs có component non-reifiable. |
| `unchecked generic array creation for varargs parameter ...` | Lời gọi varargs | Compiler tạo array ngầm mà type argument không được runtime kiểm tra. |
| `[varargs] Varargs method could cause heap pollution ...` | Bên trong method có `@SafeVarargs`, khi bật lint phù hợp | Có thao tác trên varargs array có thể mất type safety. |
| `[rawtypes] found raw type: List` | Khai báo dùng raw List | Đây là vấn đề raw type; khác việc `List<T>` non-reifiable. |
| `ClassCastException` khi nhận generic result | Runtime, thường tại chỗ đọc/nhận | Có thể dữ liệu bị heap pollution trước đó do alias/cast. |
| `ArrayStoreException` khi ghi vào array | Runtime tại phép gán | Object không phù hợp với **runtime component class** của array. |
| Không còn warning sau `@SafeVarargs` | Khai báo/caller | Chưa chứng minh method an toàn; cần review body. |

### 13.2. Khi build chỉ báo “uses unchecked or unsafe operations”

Nếu chạy `javac` không bật chi tiết lint, compiler có thể chỉ phát một ghi chú yêu cầu biên dịch lại với `-Xlint:unchecked`. Hãy dùng lệnh:

```bash
javac --release 17 -Xlint:unchecked -Xlint:varargs VarargsWarningDemo.java
```

để thấy vị trí cụ thể. Đừng tìm cách fix qua việc đổi `-nowarn`, tắt hết compiler warnings hoặc suppression ở cấp cả package.

## 14. Những lỗi tư duy thường gặp

**Lỗi 1 — Warning ở khai báo chứng minh method đã ghi sai dữ liệu.** Không đúng. Compiler báo về khả năng heap pollution từ component non-reifiable, kể cả khi body chỉ đọc.

**Lỗi 2 — Warning ở lời gọi là một lỗi mới không liên quan method signature.** Không đúng. Nó phản ánh thao tác tạo mảng varargs ngầm cho một method có component non-reifiable; có liên hệ trực tiếp với signature và type inference.

**Lỗi 3 — Generic type argument chắc chắn được kiểm tra trong một `List<String>[]`.** Không đúng. Runtime array thường chỉ kiểm tra component class `List`, không duyệt các phần tử bên trong list.

**Lỗi 4 — `@SafeVarargs` làm mọi lời gọi an toàn.** Không đúng. Nó là lời cam kết của tác giả. Method sai vẫn có thể gây heap pollution và `ClassCastException`.

**Lỗi 5 — Dùng `@SuppressWarnings("unchecked")` là đã giải quyết vấn đề.** Không đúng. Nó chỉ ẩn cảnh báo, không làm runtime có thêm thông tin kiểu hay kiểm tra dữ liệu.

**Lỗi 6 — `List<?>...` cũng có warning giống `List<T>...`.** Với đúng component `List<?>` reifiable, warning non-reifiable varargs component không phát sinh. Vẫn có thể có warning khác nếu code dùng raw/cast không an toàn.

**Lỗi 7 — Cứ thấy `List<T>...` thì phải đổi ngay thành `List<?>...`.** Nếu method cần liên kết một `T` giữa input và output, đổi sang wildcard có thể làm mất tính chính xác của API. Đánh giá mục đích trước khi sửa.

**Lỗi 8 — Chỉ kiểm tra body tại một thời điểm mà quên array alias có thể escape.** Một array truyền sang method khác, trả ra ngoài hoặc lưu lại để sửa sau vẫn có thể phá invariant. Review toàn bộ đường dữ liệu.

**Lỗi 9 — Compiler warning biến mất nghĩa là dữ liệu đã được validate.** Không. Một API có thể im lint nhưng dữ liệu từ raw/legacy hoặc nguồn ngoài vẫn sai. Generic type checking, runtime validation và ownership là các trách nhiệm khác nhau.

## 15. Bài thực hành

### Bài 1 — Nhận diện hai nơi cảnh báo

Cho method:

```java
static void visit(List<String>... lists) {
    System.out.println(lists.length);
}
```

và call:

```java
visit(List.of("A"), List.of("B"));
```

Nếu bật `-Xlint:unchecked`, những vị trí nào có thể được cảnh báo?

<details>
<summary>Đáp án</summary>

Tại khai báo: `Possible heap pollution from parameterized vararg type List<String>`. Tại lời gọi: `unchecked generic array creation for varargs parameter of type List<String>[]`. Chúng mô tả hai khía cạnh của cùng cấu trúc mảng non-reifiable.

</details>

### Bài 2 — Vì sao `List<?>...` khác `List<String>...`?

<details>
<summary>Đáp án</summary>

`List<?>` là reifiable vì argument là wildcard không giới hạn; `List<String>` không reifiable. JVM có thể tạo mảng component `List` cho cả một số cấu trúc, nhưng việc dùng generic component `List<String>` trong varargs mang rủi ro về type argument không kiểm tra được, khiến compiler phát cảnh báo.

</details>

### Bài 3 — Có nên gắn `@SafeVarargs`?

```java
static <T> T wrong(List<T>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(42);
    return groups[0].get(0);
}
```

<details>
<summary>Đáp án</summary>

Không. Body ghi một `List<Integer>` vào array có source component `List<T>`, có thể tạo heap pollution. Phải sửa body/thiết kế trước, không gắn annotation chỉ để làm warning ở caller biến mất.

</details>

### Bài 4 — Khi nào nên chọn collection bên ngoài thay varargs?

<details>
<summary>Đáp án</summary>

Khi caller đã có danh sách nhóm, cần truyền qua nhiều tầng hoặc không cần cú pháp tiện lợi của `...`, nhận một `List<? extends List<? extends T>>` hay cấu trúc domain phù hợp có thể tránh array varargs ngầm và giữ generic contract rõ ràng.

</details>

### Bài 5 — Một method `static` với `@SafeVarargs` đã được compiler chấp nhận. Có phải body luôn an toàn không?

<details>
<summary>Đáp án</summary>

Không. `static` chỉ là điều kiện hợp lệ về vị trí annotation; người viết vẫn phải bảo đảm không tạo heap pollution. `-Xlint:varargs` có thể cảnh báo về thao tác alias hoặc exposure của non-reifiable varargs array.

</details>

### Bài 6 — `@SafeVarargs` có thể gắn vào instance method không `final`, không `private` trên Java 17 không?

<details>
<summary>Đáp án</summary>

Không. Instance method có thể bị override không đủ điều kiện gắn `@SafeVarargs`; trên Java 17 chỉ các instance varargs methods `final` hoặc `private` là hợp lệ, cùng với `static` methods và constructors có varargs.

</details>

## 16. Kết luận

**Cảnh báo ở khai báo** generic varargs cho biết signature nhận một array có component không reifiable, tạo khả năng heap pollution. **Cảnh báo ở lời gọi** cho biết compiler đang tạo/cung cấp một generic varargs array mà type arguments không thể kiểm tra đầy đủ ở runtime. Hai thông báo quan hệ với nhau nhưng nằm ở hai vị trí khác nhau của chương trình.

`@SafeVarargs` chỉ nên được dùng khi method hợp lệ về vị trí annotation **và** có cơ sở bảo đảm array varargs không bị ghi sai, không escape tới code không kiểm soát và không làm hỏng dữ liệu qua raw alias. Nếu không cần liên kết kiểu `T`, `List<?>...` có thể là một API reifiable phù hợp. Nếu cần giữ quan hệ generic mà không muốn dùng varargs array, cân nhắc truyền collection bên ngoài.

Với Java Generics, loại bỏ cảnh báo đúng cách nghĩa là **giữ hợp đồng kiểu và kiểm soát dữ liệu**, không chỉ làm compiler im lặng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-004">Rủi ro ghi qua tham chiếu mảng kiểu cha</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro ghi qua tham chiếu mảng kiểu cha trong Generic Varargs Java

## 1. Vấn đề cần nhận diện

Một phương thức nhận varargs có thể được gọi bằng nhiều đối số như:

```java
static void print(String... values) {
    for (String value : values) {
        System.out.println(value);
    }
}
```

Đây là đoạn phương thức minh họa; cần đặt bên trong class để chạy. Về cơ chế, `String...` là tham số có kiểu mảng `String[]`, và lời gọi `print("A", "B")` có thể được compiler biên dịch bằng cách tạo một mảng chứa hai chuỗi.

Sự nguy hiểm rõ hơn khi kiểu phần tử của varargs là một **kiểu generic không reifiable**, ví dụ:

```java
static void process(java.util.List<String>... lists) {
    // ...
}
```

Bên trong phương thức, `lists` được nhìn như `List<String>[]`, nhưng runtime **không có** component class riêng cho `List<String>`. Mảng dùng để truyền tham số có component type runtime tương ứng với raw `List`.

Nếu code cho một tham chiếu kiểu cha, chẳng hạn `Object[]`, trỏ tới chính mảng đó rồi **ghi** vào mảng:

```java
Object[] alias = lists;
alias[0] = java.util.List.of(123);
```

Java có thể chấp nhận phép ghi vì object `List<Integer>` vẫn là một `List` khi kiểm tra component type runtime. Tuy nhiên, một chỗ khác đang đọc `lists[0]` như `List<String>` có thể ném `ClassCastException` khi lấy `Integer` ra làm `String`.

**Kết luận cốt lõi:**

> Với generic varargs, việc gán mảng cho tham chiếu cha như `Object[]` có thể mở đường **ghi phần tử không đúng đối số kiểu generic** vào chính mảng varargs. Runtime array store check chỉ biết raw component type, còn compiler đã giả định các phần tử tuân theo hợp đồng generic. Hiện tượng lệch giữa hợp đồng và dữ liệu thực là *heap pollution*.

Không phải mọi phép gán sang `Object[]` đều nguy hiểm. Rủi ro cụ thể xuất hiện khi có thao tác ghi không an toàn hoặc làm lộ alias cho mã có thể ghi như vậy.

## 2. Ba cơ chế làm phép ghi qua tham chiếu cha trở nên nguy hiểm

| Cơ chế | Quy tắc quan trọng | Hệ quả trong bài |
| --- | --- | --- |
| **Varargs dùng mảng** | `List<String>...` có kiểu tham số source `List<String>[]`. | Mảng có các slot có thể được thay bằng phép gán chỉ số. |
| **Array covariance** | Một mảng tham chiếu như `List[]` có thể được nhìn qua `Object[]`. | Alias rộng cho phép viết code `alias[0] = newValue`. |
| **Generic erasure** | Component type runtime là `List`, không phải specialization `List<String>`. | JVM không tự kiểm tra type argument String khi thay slot. |

Sự kết hợp cả ba mới là trọng tâm: `Object[]` không tạo bản sao, nên ghi vào alias thay **chính slot của mảng varargs**. Nếu ghi một `String` vào runtime `List[]` thì JVM vẫn có thể ném `ArrayStoreException`; nhưng một `List<Integer>` là List hợp lệ ở cấp runtime nên có thể lọt qua. Lỗi chỉ xuất hiện khi caller dùng list đó dưới hợp đồng `List<String>`, thường tại phép đọc cần String.

Đặc biệt, `new List<String>[2]` bị cấm vì component không reifiable, còn `List<String>...` được phép khai báo với cảnh báo unchecked tương ứng. Bài này tập trung vào **đường ghi qua alias**, không coi mọi alias hay mọi varargs tự động gây heap pollution.

## 3. Một ví dụ chuẩn: array covariance với `String[]`

Trước khi xem generic varargs, hãy chạy một ví dụ mảng bình thường để biết khi nào JVM chặn được thao tác ghi sai.

Tệp **`CovariantArrayDemo.java`**:

```java
public class CovariantArrayDemo {
    public static void main(String[] args) {
        String[] strings = {"Java", "Generics"};
        Object[] alias = strings;

        System.out.println(strings.getClass()
                .getComponentType().getName());

        try {
            alias[0] = Integer.valueOf(42);
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }

        System.out.println(strings[0]);
        System.out.println(alias == strings);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac CovariantArrayDemo.java
java CovariantArrayDemo
```

Stdout:

```text
java.lang.String
ArrayStoreException
Java
true
```

Lý do:

1. Object mảng có component type runtime là `String`.
2. `Object[] alias = strings` tạo **tham chiếu khác đến cùng object**, không sao chép mảng.
3. `alias[0] = Integer.valueOf(42)` thỏa kiểu tĩnh của `Object[]`, nhưng không thỏa runtime component type của `String[]`.
4. JVM ném `ArrayStoreException` ngay khi ghi; mảng vẫn chứa `"Java"` ở vị trí 0.
5. `alias == strings` là `true`, nhấn mạnh đây là alias chứ không phải copy.

Cơ chế `ArrayStoreException` cho thấy Java arrays có runtime store check. Tuy nhiên, kiểm tra này chỉ mạnh đến mức **kiểu thành phần runtime có thể biểu diễn**.

## 4. Khác biệt khi phần tử mảng là danh sách generic

Giả sử mảng có component type runtime là `java.util.List`. Khi gán vào một phần tử của mảng:

- Một `String` **không** phải `List`, nên JVM có thể từ chối.
- Một `List<String>` là `List`, nên được chấp nhận.
- Một `List<Integer>` cũng là `List`, nên **vẫn được chấp nhận**.
- Runtime không kiểm tra `String` hay `Integer` ở bên trong từng `List`.

Bảng đối chiếu:

| Object mảng thực tế | Object được ghi qua `Object[]` | Runtime array store check | Hợp đồng generic có thể bị phá? |
|---|---|---|---|
| `String[]` | `Integer` | Từ chối bằng `ArrayStoreException` | Không liên quan generic |
| `List[]` | `String` | Từ chối bằng `ArrayStoreException` | Chưa có ghi thành công |
| `List[]` | `List<String>` | Chấp nhận | Phù hợp nếu slot kỳ vọng `List<String>` |
| `List[]` | `List<Integer>` | Chấp nhận | Có nếu slot được xem là `List<String>` |
| `Object[]` | `List<Integer>` | Chấp nhận | Tùy hợp đồng dữ liệu bên ngoài mảng |

Điểm quan trọng là **một mảng `List[]` không biết generic argument của object `List` được lưu**. Việc kiểu mảng runtime có thể chặn một object không phải `List` không đồng nghĩa nó xác minh được `List<String>`.

## 5. Chương trình chính: heap pollution qua `Object[]` alias

Tạo tệp **`UnsafeGenericVarargsDemo.java`**:

```java
import java.util.List;

public class UnsafeGenericVarargsDemo {

    static void corrupt(List<String>... groups) {
        System.out.println(groups.length);

        Object[] alias = groups;
        alias[0] = List.of(42);

        System.out.println(groups[0].size());

        String word = groups[0].get(0);
        System.out.println(word);
    }

    public static void main(String[] args) {
        try {
            corrupt(List.of("safe"));
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac UnsafeGenericVarargsDemo.java
java UnsafeGenericVarargsDemo
```

Stdout của **chương trình**:

```text
1
1
ClassCastException
```

`javac` có thể in ghi chú/cảnh báo về thao tác generic varargs không an toàn trong quá trình biên dịch. **Thông báo biên dịch không thuộc stdout của chương trình**. Muốn xem rõ cảnh báo, dùng:

```bash
javac -Xlint:unchecked -Xlint:varargs UnsafeGenericVarargsDemo.java
```

### Diễn giải theo từng dòng

1. `corrupt(List.of("safe"))` truyền một danh sách chuỗi. Method khai báo `List<String>... groups`.
2. Varargs được truyền bằng một mảng. Với generic argument `<String>`, runtime array component chỉ là `List`.
3. `Object[] alias = groups` hợp lệ bởi mảng có tính covariance.
4. `alias[0] = List.of(42)` đặt một `List<Integer>` vào mảng; runtime component `List` vẫn chấp nhận nên **không** ném `ArrayStoreException`.
5. `groups[0].size()` hoạt động: số phần tử là 1, và `size()` không phải lấy `Integer` làm `String`.
6. `String word = groups[0].get(0)` là nơi compiler kỳ vọng kết quả có kiểu `String`. Giá trị thực tế là `Integer`, dẫn tới `ClassCastException`.
7. Khối `catch` in tên exception.

Đây là **heap pollution**: một tham chiếu được xem là mang kiểu tham số hóa (`List<String>[]`) trỏ đến trạng thái thực chứa danh sách không đáp ứng hợp đồng ấy.

Ví dụ này **cố ý không an toàn để học tập**. Không sử dụng thân method `corrupt` trong code sản xuất, và không đặt `@SafeVarargs` lên nó.

## 6. Vì sao câu lệnh `alias[0] = List.of(42)` hợp lệ ở mã nguồn?

Hãy phân biệt ba kiểu tĩnh:

```java
List<String>[] groups; // Một mảng được nhìn với generic component type.
Object[] alias;         // Một tham chiếu mảng kiểu cha.
List<Integer> wrong;    // Một danh sách có phần tử Integer.
```

Đây là các đoạn khai báo minh họa; `groups` và `alias` phải được khởi tạo đúng trước khi dùng trong một method hoàn chỉnh.

Ở mã nguồn, `groups[0]` có kiểu `List<String>`; compiler sẽ từ chối gán trực tiếp một `List<Integer>`:

```java
// Cố ý không hợp lệ nếu đặt trong method và khởi tạo các biến:
groups[0] = List.of(42);
```

Nhưng nếu code tạo alias:

```java
Object[] alias = groups;
alias[0] = List.of(42);
```

thì kiểu tĩnh của phần tử `alias[0]` là `Object`. `List<Integer>` là một object, nên compiler **không có căn cứ từ kiểu `Object[]` để từ chối**. Phép ghi chuyển sang cơ chế runtime array store check.

Vấn đề là runtime chỉ biết mảng có component class raw `List`, nên cho phép `List<Integer>` đi qua. Thông tin `<String>` không được phục hồi ở thời điểm ghi.

**Không phải compiler “quên kiểm tra” `groups[0]`.** Ta đã thay góc nhìn của compiler bằng tham chiếu kiểu cha và đưa thao tác ghi qua một đường không còn gắn với generic component type.

## 7. Alias không phải copy: vì sao thay đổi nhìn thấy từ mọi tham chiếu?

Câu lệnh:

```java
Object[] alias = groups;
```

không tạo mảng mới. `alias` và `groups` đều trỏ tới cùng vùng lưu trữ phần tử mảng. Do đó:

- `alias[0] = ...` làm thay đổi giá trị mà `groups[0]` sẽ đọc.
- `groups[0] = ...` cũng làm thay đổi giá trị mà `alias[0]` đọc.
- Gán `alias = new Object[1]` lại là việc đổi tham chiếu `alias`, **không** thay thế mảng mà `groups` đang trỏ tới.

Phân biệt này quan trọng đối với mọi API nhận hoặc trả mảng. Một method có thể không tự ghi vào mảng, nhưng nếu nó **đưa alias ra bên ngoài** cho mã không đáng tin, nơi khác có thể sửa và gây cùng nguy cơ.

Ví dụ: `return groups;`, đưa `groups` vào một field có thể sửa, hoặc gửi `groups` cho một callback nắm giữ `Object[]` là các tình huống cần phân tích. Không phải thao tác nào cũng tự động gây heap pollution; yếu tố rủi ro là khả năng **sửa nội dung array thông qua alias không duy trì generic contract**.

## 8. Phân biệt `ArrayStoreException` với `ClassCastException`

Hai exception này có thể xuất hiện ở những thời điểm khác nhau:

**`ArrayStoreException`:**

- Thường phát sinh khi lưu một object không tương thích với **runtime component type của mảng**.
- Trong ví dụ `String[]`, lúc ghi `Integer` qua `Object[]`, JVM chặn ngay.
- Trong mảng component `List`, ghi một `String` không phải `List` cũng sẽ bị chặn.

**`ClassCastException`:**

- Có thể phát sinh **muộn**, khi một chỗ sử dụng dữ liệu theo kiểu hẹp hơn mà runtime object không đáp ứng.
- Trong ví dụ generic varargs, đọc phần tử `List<String>.get(0)` và nhận giá trị thực `Integer` làm lỗi.
- Heap pollution đã xảy ra ở dòng ghi; exception là triệu chứng phát hiện ở dòng dùng sau đó.

Vì vậy, “ghi không ném exception” **không chứng minh** phép ghi giữ an toàn kiểu generic.

## 9. Thử mảng wildcard được phép tạo trực tiếp

Java cấm tạo `new List<String>[n]`, nhưng cho phép tạo một mảng có component là wildcard không giới hạn:

```java
List<?>[] groups = new List<?>[2];
```

`List<?>` là kiểu reifiable theo trường hợp wildcard không giới hạn. Một slot có thể giữ bất kỳ danh sách nào, dù danh sách đó chứa chuỗi, số hay kiểu khác.

Tệp **`WildcardArrayDemo.java`**:

```java
import java.util.List;

public class WildcardArrayDemo {

    public static void main(String[] args) {
        List<?>[] groups = new List<?>[2];

        groups[0] = List.of("A", "B");
        groups[1] = List.of(10, 20);

        System.out.println(groups.getClass()
                .getComponentType().getName());
        System.out.println(groups[0].get(0));
        System.out.println(groups[1].get(1));

        Object[] alias = groups;
        try {
            alias[0] = "not a list";
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac WildcardArrayDemo.java
java WildcardArrayDemo
```

Stdout:

```text
java.util.List
A
20
ArrayStoreException
```

Ví dụ minh họa một điểm **khác với heap pollution**: `List<?>[]` không hứa mọi slot chứa `List<String>`. Đặt một `List<Integer>` vào slot là hợp lệ theo hợp đồng vì type argument của từng list là chưa biết. Nếu ứng dụng yêu cầu mọi nhóm là `List<String>`, `List<?>[]` không đáp ứng mục tiêu ấy.

Cũng đừng dùng `List<?>[]` rồi ép unchecked sang `List<String>[]` chỉ để tạo một cách dùng nguy hiểm tương tự ví dụ phần 5.

## 10. Không phải mọi trường hợp đọc generic varargs đều nguy hiểm

Một method có thể chỉ đọc các phần tử từ generic varargs và đưa chúng vào một cấu trúc mới:

```java
static <T> void printCount(java.util.List<T>... groups) {
    for (java.util.List<T> group : groups) {
        System.out.println(group.size());
    }
}
```

Đoạn này cần đặt trong class; chưa có `main`. Bản thân việc duyệt và gọi `size()` **không tạo heap pollution**, vì không có thao tác gán danh sách sai kiểu vào mảng và không làm lộ mảng để nơi khác ghi.

Tuy nhiên, khai báo một generic varargs với component non-reifiable vẫn có thể phát cảnh báo từ compiler. Cảnh báo nhằm nhắc người viết và người gọi rằng phương thức đang dùng một cấu trúc mảng không mang đầy đủ type argument ở runtime.

Để coi một triển khai là an toàn, cần xem toàn bộ thân method, các đối tượng nó chuyển mảng cho, và cách nó dùng dữ liệu. “Không nhìn thấy phép gán trực tiếp” là một tín hiệu tốt nhưng **chưa đủ** nếu method vẫn để array alias thoát ra nơi có thể sửa.

## 11. Ví dụ generic varargs an toàn: duyệt và copy dữ liệu

Tạo tệp **`SafeVarargsMergeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class SafeVarargsMergeDemo {

    @SafeVarargs
    static <T> List<T> merge(List<? extends T>... groups) {
        List<T> output = new ArrayList<>();

        for (List<? extends T> group : groups) {
            output.addAll(group);
        }

        return List.copyOf(output);
    }

    public static void main(String[] args) {
        List<String> words = merge(
                List.of("red"), List.of("blue", "green"));

        List<Number> numbers = merge(
                List.of(1, 2), List.of(3.5));

        System.out.println(words);
        System.out.println(numbers);
    }
}
```

Biên dịch/chạy với Java 17 trở lên:

```bash
javac SafeVarargsMergeDemo.java
java SafeVarargsMergeDemo
```

Stdout:

```text
[red, blue, green]
[1, 2, 3.5]
```

### Vì sao method này có cơ sở để dùng `@SafeVarargs`?

- Nó chỉ **duyệt** các slot của array varargs.
- Không thay slot trong mảng bằng một danh sách có generic argument khác.
- Không trả về chính mảng, không lưu array vào một field có thể được mã khác truy cập và ghi.
- Dữ liệu được sao chép vào một `ArrayList<T>` mới theo hợp đồng generic.
- Kết quả trả về là `List<T>`, không phải `List<T>[]`, và được chuyển thành một danh sách không thể sửa qua API chuẩn bằng `List.copyOf`.

`@SafeVarargs` là một **lời cam kết của người viết** về cách triển khai dùng varargs parameter; compiler không chứng minh tự động mọi đường thực thi đều an toàn chỉ nhờ annotation.

`List.copyOf` từ chối phần tử `null`. Trong ví dụ, đầu vào không chứa `null`; với dữ liệu cho phép `null`, nên quyết định chính sách khác thay vì giả sử `copyOf` chấp nhận.

## 12. `@SafeVarargs` có tác dụng gì, và không có tác dụng gì?

`java.lang.SafeVarargs` là annotation để biểu đạt rằng người khai báo một method/constructor với varargs có kiểu phần tử không reifiable **cam kết không thực hiện thao tác không an toàn** trên biến varargs của mình.

Nó có thể giảm các cảnh báo unchecked liên quan tới khai báo và lời gọi varargs trong trường hợp được phép.

Điều annotation **không** làm:

- Không đổi runtime array component type từ raw `List` thành `List<String>`.
- Không bổ sung kiểm tra generic argument cho JVM.
- Không ngăn `Object[]` alias viết một `List<Integer>` vào mảng.
- Không tự sao chép hoặc bảo vệ mảng đầu vào.
- Không tự phát hiện mọi thân method nguy hiểm.
- Không cho phép viết trực tiếp `new List<String>[n]`.

Vì vậy, đoạn sau là **một lời cam kết sai**:

```java
// CỐ Ý NGUY HIỂM: không áp dụng mẫu này.
@SafeVarargs
static void unsafe(java.util.List<String>... groups) {
    Object[] alias = groups;
    alias[0] = java.util.List.of(99);
}
```

Một số compiler có thể phát thêm cảnh báo trong thân method dù có annotation; bất kể cảnh báo thế nào, annotation không hợp thức hóa hành vi heap pollution.

### Vị trí được phép áp dụng

Trên Java 17, `@SafeVarargs` được phép trên constructor, method static, method final và private instance methods theo quy tắc ngôn ngữ. Không thể tùy ý đặt lên một instance method public có thể override.

Điều kiện hợp lệ của annotation **khác** với sự an toàn thực sự của phương thức. Một method `static` có thể gắn annotation nhưng vẫn nguy hiểm nếu nó viết sai kiểu hoặc làm lộ array.

## 13. Một lỗi tinh vi: làm lộ mảng thay vì trực tiếp ghi vào nó

Không nhất thiết phải có `alias[0] = ...` ngay trong method chứa varargs thì heap pollution mới có thể xảy ra. Nếu method trả chính array cho caller hoặc lưu ở chỗ có thể truy cập, caller có thể ghi thông qua reference kiểu rộng hơn.

Hãy xem thiết kế **không nên dùng**:

```java
// Đoạn này chỉ minh họa nguy cơ API, cần đặt trong class.
static <T> T[] expose(T... values) {
    return values;
}
```

Khi caller thực sự nhận được một array có component runtime type không đủ cụ thể, phương thức `expose` có thể làm lộ mảng tới code khác. Một nơi khác có quyền sửa array có thể tạo sự lệch kiểu mà caller không lường trước.

Cần phân biệt các trường hợp:

- Với `String...` và caller truyền chuỗi thông thường, array runtime có thể là `String[]`, nên ghi `Integer` qua `Object[]` bị chặn.
- Với `List<String>...`, array runtime có component raw `List`, nên `List<Integer>` có thể lọt qua store check.
- Với `T...` không giới hạn, erasure của `T` là `Object`; array runtime được tạo còn phụ thuộc kiểu cụ thể và ngữ cảnh lời gọi. Không thể suy luận tất cả các trường hợp đều an toàn hoặc đều lỗi từ duy nhất chữ `T...`.

**Nguyên tắc an toàn:** Không làm lộ reference tới generic varargs array cho code có thể thay các phần tử không đúng hợp đồng. Nếu cần giữ dữ liệu, sao chép sang một generic collection có contract rõ ràng hoặc tạo một bản sao phù hợp với API.

## 14. Ví dụ trả về một collection mới thay vì mảng varargs

Tệp **`VarargsSnapshotDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class VarargsSnapshotDemo {

    @SafeVarargs
    static <T> List<T> snapshot(T... values) {
        List<T> result = new ArrayList<>();

        for (T value : values) {
            result.add(value);
        }

        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> words = snapshot("a", "b");
        List<Integer> numbers = snapshot(1, 2, 3);

        System.out.println(words);
        System.out.println(numbers);
    }
}
```

Biên dịch/chạy:

```bash
javac VarargsSnapshotDemo.java
java VarargsSnapshotDemo
```

Stdout:

```text
[a, b]
[1, 2, 3]
```

Trong ví dụ này, `snapshot` không cung cấp cho caller quyền truy cập trực tiếp tới array varargs. Nó tạo `ArrayList<T>` mới và trả collection snapshot unmodifiable.

Đây là một mẫu tốt khi nghiệp vụ **cần kết quả là tập dữ liệu** chứ không cần object mảng ban đầu. Lưu ý tính chất *shallow copy*: nếu các phần tử là object mutable, việc sao chép danh sách không sao chép sâu trạng thái của từng phần tử.

## 15. Varargs forwarding: alias có thể xuất hiện qua method khác

Giả sử một phương thức nhận `List<String>...` rồi chuyển nguyên mảng cho một method khác dưới kiểu rộng hơn:

```java
static void acceptsObjects(Object[] values) {
    // Có khả năng ghi vào values.
}

static void forward(java.util.List<String>... groups) {
    acceptsObjects(groups);
}
```

Đây là ví dụ cấu trúc, chưa phải chương trình độc lập. Việc chuyển `groups` cho `acceptsObjects(Object[])` tự nó không gây heap pollution. Nhưng nếu method nhận sau đó ghi `List<Integer>` vào một slot, nó sẽ gây cùng vấn đề dù không nằm trong `forward`.

Do đó, khi review `@SafeVarargs`, không chỉ tìm phép gán trực tiếp. Phải theo dấu mọi nơi array varargs:

- được truyền tới helper;
- được lưu trong field hay callback;
- được trả về hoặc expose qua getter;
- được chuyển thành view hoặc container backed by array mà bên ngoài còn sửa được.

Mục tiêu là biết liệu một đoạn code bên ngoài có thể dùng alias có kiểu rộng để thay slot bằng object sai generic argument hay không.

## 16. Đừng nhầm sửa nội dung list với sửa slot của varargs array

Hãy so sánh hai hành vi:

```java
// Bên trong method nhận List<String>... groups:

groups[0] = java.util.List.of("new");  // Thay slot bằng List<String>.
groups[0].add("x");                    // Thử sửa nội dung list.
```

Các dòng này chỉ minh họa; lời gọi `add` có thể ném `UnsupportedOperationException` nếu danh sách cụ thể không cho sửa, như `List.of`.

**Thay slot của array** và **sửa đối tượng list bên trong slot** là hai thao tác khác nhau:

- `groups[0] = someList` thay một reference trong mảng varargs.
- `groups[0].add(...)` tác động vào object list mà slot đang trỏ đến, không nhất thiết thay reference của slot.
- Nếu list cho phép sửa, việc gọi `add("x")` qua `List<String>` trong code typed là hợp lệ; compiler tiếp tục kiểm tra tham số `String`.
- Tuy nhiên, nếu list được truyền cho helper raw/unchecked, chính nội dung list cũng có thể bị heap pollution bằng cơ chế khác.

Cảnh báo về generic varargs và alias mảng tập trung vào rủi ro runtime array component type không phản ánh đầy đủ generic argument. Nó có thể kết hợp với các rủi ro raw collection khác, nhưng nên phân tích từng đường truy cập riêng.

## 17. `List<String>[]` có thể được khai báo nhưng không trực tiếp `new`

Một số người thấy phương thức:

```java
static void use(java.util.List<String>... groups) { }
```

rồi tưởng có thể khai báo và cấp phát mảng tùy ý:

```java
// CỐ Ý KHÔNG HỢP LỆ:
java.util.List<String>[] arr = new java.util.List<String>[2];
```

Hai trường hợp chịu quy tắc khác nhau.

- **Kiểu khai báo** `List<String>[]` có thể xuất hiện hợp lệ trong tham số/biến.
- **Biểu thức tạo array** `new List<String>[2]` bị cấm vì component type `List<String>` là non-reifiable.
- Generic varargs có cú pháp và cơ chế đặc biệt: compiler có thể tạo array ở call site theo erasure và đưa ra cảnh báo unchecked.
- Một lời gọi có sẵn mảng thay vì danh sách nhiều đối số còn liên quan tới quy tắc *fixed arity* và *variable arity invocation*; không nên đồng nhất mọi lời gọi varargs với việc luôn cấp phát một mảng mới.

**Sự tồn tại của generic varargs không phải ngoại lệ cho phép trực tiếp viết mọi generic array creation expression.**

## 18. Varargs và những cách gọi có thể khác nhau

Method:

```java
static void printAll(String... values) {
    System.out.println(values.length);
}
```

có thể được gọi như:

```java
printAll("A", "B");
printAll(new String[] {"A", "B"});
```

Trong lời gọi thứ nhất, compiler sử dụng cơ chế varargs để truyền hai phần tử. Trong lời gọi thứ hai, caller truyền trực tiếp mảng `String[]` như tham số duy nhất thích hợp với chữ ký.

Với generic varargs, một mảng đã được tạo qua một nguồn khác cũng có thể được truyền vào, nhưng **tính an toàn của nguồn mảng đó** vẫn phải được đánh giá. Nếu array bị heap pollution trước khi truyền, method chỉ đọc có thể nhận dữ liệu sai hợp đồng.

Bài học dành cho người viết API: không chỉ cần xem method có “tự làm bẩn” mảng không, mà còn hiểu mức độ tin cậy của tham số mảng và cách nó được dùng ở các đường gọi khác nhau.

## 19. Các cách tránh generic varargs khi không cần thiết

Nếu bài toán chỉ cần xử lý nhiều danh sách, có thể đổi API sang collection lồng nhau:

```java
static void printCounts(
        java.util.Collection<? extends java.util.List<String>> groups) {
    for (java.util.List<String> group : groups) {
        System.out.println(group.size());
    }
}
```

Đoạn này cần đặt trong class. Caller có thể gọi:

```java
printCounts(java.util.List.of(
        java.util.List.of("A", "B"),
        java.util.List.of("C")));
```

Không có array varargs với component `List<String>` non-reifiable được tạo cho lời gọi ấy. Compiler vẫn kiểm tra inner list có type argument `String` khi làm việc qua API typed.

### Ví dụ đầy đủ: không dùng varargs để gộp nhóm

Tệp **`CollectionAlternativeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionAlternativeDemo {

    static <T> List<T> merge(
            Collection<? extends List<? extends T>> groups) {
        List<T> output = new ArrayList<>();

        for (List<? extends T> group : groups) {
            output.addAll(group);
        }

        return List.copyOf(output);
    }

    public static void main(String[] args) {
        List<String> words = merge(List.of(
                List.of("A"), List.of("B", "C")));

        System.out.println(words);
    }
}
```

Biên dịch/chạy:

```bash
javac CollectionAlternativeDemo.java
java CollectionAlternativeDemo
```

Stdout:

```text
[A, B, C]
```

Thay vì một mảng varargs, API nhận một collection các danh sách. Nó có thể nhận nhiều nguồn collection được định kiểu phù hợp và không phải phơi bày generic array ở parameter boundary.

Đổi lại, caller phải xây collection chứa các nhóm. Nếu API cần sự tiện lợi của lời gọi `merge(list1, list2)` và đã chứng minh method là an toàn, generic varargs kết hợp `@SafeVarargs` có thể hoàn toàn hợp lý.

## 20. PECS và generic varargs: mở rộng đầu vào an toàn

Trong ví dụ an toàn:

```java
@SafeVarargs
static <T> java.util.List<T> merge(
        java.util.List<? extends T>... groups) {
    // ...
}
```

Tham số `List<? extends T>` diễn tả mỗi nhóm là một nguồn cung cấp phần tử có thể đọc dưới dạng `T`. Đây là áp dụng **Producer Extends** trong PECS.

Nhưng bounded wildcard **không làm array varargs trở thành reifiable**. Nó giảm yêu cầu kiểu của phần tử trong mỗi list ở compile time, đồng thời generic varargs array vẫn có nguy cơ nếu method dùng một alias có thể ghi sai kiểu vào slot.

Phân biệt:

- `? extends T` bảo vệ cách **dùng từng list** trong mã nguồn.
- `Object[] alias` mở đường **thay cả list object** trong array nếu code thực hiện ghi.
- `@SafeVarargs` chỉ hợp lý khi thiết kế method và các helper không tạo ra việc ghi/lộ array không an toàn.

Không thể thay thế kiểm toán aliasing bằng việc chỉ thêm wildcard.

## 21. Nhận diện lỗi trong code review

Đọc một method generic varargs theo trình tự:

1. **Xác định element type của varargs:** `String` là reifiable; `List<String>` không reifiable; `T` không có bound thường bị xóa thành `Object`.
2. **Tìm chỗ array được xem như kiểu cha:** `Object[]`, raw `List[]`, hoặc một helper nhận mảng tương thích.
3. **Tìm mọi thao tác ghi vào slot:** `array[i] = ...`, `System.arraycopy`, `Arrays.fill`, method helper sửa mảng và callback có thể sửa.
4. **Tìm chỗ reference thoát ra:** return array, lưu field, trả view backed by array, ghi vào shared state.
5. **Đối chiếu runtime component type với dữ liệu được ghi:** `List<Integer>` vẫn là raw `List`.
6. **Đánh giá lời gọi đọc dữ liệu:** cast khi đọc theo `List<String>` có thể ném exception muộn.
7. **Xem annotation:** nếu có `@SafeVarargs`, kiểm tra nó phản ánh triển khai thật chứ không chỉ giấu cảnh báo.
8. **Chọn giải pháp:** chỉ đọc và copy; không làm lộ mảng; hoặc chuyển API sang `Collection`/`List`.

### Mẫu code đáng nghi

```java
// Đoạn phân tích, không phải API an toàn.
static void mutate(java.util.List<String>... groups) {
    Object[] values = groups;
    values[0] = java.util.List.of(123);
}
```

Mẫu này có cả ba tín hiệu: generic varargs với non-reifiable component, alias kiểu cha, và thao tác ghi một danh sách không cùng generic argument.

### Mẫu code ít rủi ro hơn

```java
@SafeVarargs
static <T> int count(
        java.util.List<? extends T>... groups) {
    int total = 0;
    for (java.util.List<? extends T> group : groups) {
        total += group.size();
    }
    return total;
}
```

Đoạn trên cần đặt trong class. Nó chỉ đọc `size()` và không làm lộ array; đây là tình huống phù hợp hơn để người viết cân nhắc cam kết `@SafeVarargs`.

## 22. Những hiểu nhầm thường gặp

**Hiểu nhầm 1 — “Mọi `Object[]` alias đều nguy hiểm.”** Không. Việc gán alias không tự làm hỏng dữ liệu; rủi ro tăng khi alias được dùng để ghi hoặc đưa cho code có thể ghi sai kiểu.

**Hiểu nhầm 2 — “Array store check sẽ luôn chặn `List<Integer>` đưa vào `List<String>[]`.”** Không. Runtime component thường chỉ là raw `List`, nên cả hai loại danh sách đều qua được kiểm tra.

**Hiểu nhầm 3 — “`ClassCastException` xuất hiện ngay lúc `alias[0] = List.of(42)`.”** Không trong ví dụ chính. Ghi được chấp nhận; lỗi phát sinh khi phần tử `Integer` bị dùng như `String`.

**Hiểu nhầm 4 — “Có `@SafeVarargs` tức JVM đã reify generic type.”** Không. Annotation không thay đổi erasure hoặc runtime component type.

**Hiểu nhầm 5 — “Không thể khai báo `List<String>...`.”** Có thể khai báo, nhưng compiler đưa ra cảnh báo và quy tắc để giúp nhận diện nguy cơ không reifiable.

**Hiểu nhầm 6 — “`List<?>[]` không thể tạo bằng `new`.”** Có thể tạo `new List<?>[n]` vì wildcard không giới hạn là reifiable theo quy tắc của Java.

**Hiểu nhầm 7 — “`List<?>[]` tương đương `List<String>[]`.”** Không. Loại thứ nhất cho phép nhiều generic arguments khác nhau ở mỗi list; loại thứ hai mang kỳ vọng phần tử là `List<String>`.

**Hiểu nhầm 8 — “Dùng `? extends T` là đủ để an toàn nếu vẫn ghi qua `Object[]`.”** Không. Wildcard ảnh hưởng compile-time operations trên `List`, không ngăn array alias thay cả slot.

**Hiểu nhầm 9 — “Method chỉ forward varargs sang helper chắc chắn an toàn.”** Không. Nếu helper giữ hoặc ghi vào array, vấn đề vẫn tồn tại.

**Hiểu nhầm 10 — “Heap pollution chỉ xảy ra ở varargs.”** Không. Raw types, unchecked casts và các đường API khác cũng có thể gây trạng thái dữ liệu không phù hợp với parameterized type.

**Hiểu nhầm 11 — “Chỉ nên xử lý warning bằng `@SuppressWarnings`.”** Không. Cần hiểu đường dữ liệu và sửa thiết kế; suppress chỉ có thể dùng cho một ranh giới được chứng minh và khoanh vùng.

**Hiểu nhầm 12 — “Chuyển `List<String>...` sang `Collection<List<String>>` làm generic reified.”** Không. Nó loại bỏ rủi ro array alias cụ thể khi không cần array, trong khi Generics vẫn chủ yếu kiểm tra ở compile time.

## 23. Bài tập và đáp án

**Bài 1 — Dự đoán exception**

```java
String[] arr = new String[1];
Object[] alias = arr;
alias[0] = 123;
```

Khi thực thi dòng cuối, exception nào?

**Đáp án:** `ArrayStoreException`, vì runtime component type là `String`.

**Bài 2 — Generic list array**

```java
List<?>[] arr = new List<?>[1];
Object[] alias = arr;
alias[0] = List.of(123);
```

Dòng cuối có phát sinh `ArrayStoreException` không?

**Đáp án:** Không. Runtime component là `List` và `List.of(123)` trả object triển khai `List`. `List<?>[]` không hứa các phần tử là danh sách chuỗi.

**Bài 3 — Heap pollution**

Giả sử có một `List<String>[]` do unchecked conversion/varargs tạo ra và runtime component là `List`. `Object[] alias = array; alias[0] = List.of(123);` có thể thành công không?

**Đáp án:** Có; mảng runtime chỉ kiểm tra raw `List`. Nếu đọc sau đó như `String`, có thể phát sinh `ClassCastException`.

**Bài 4 — Alias hay copy**

```java
Object[] alias = groups;
```

Nó có sao chép mảng không?

**Đáp án:** Không. Nó tạo thêm một tham chiếu tới cùng object array.

**Bài 5 — Annotation**

Có nên gắn `@SafeVarargs` lên method cố tình ghi `List<Integer>` vào mảng `List<String>...` không?

**Đáp án:** Không. Annotation là cam kết an toàn, không phải cơ chế tự bảo vệ.

**Bài 6 — API thay thế**

Một method chỉ cần duyệt các nhóm chuỗi và in số phần tử. Có thể dùng kiểu tham số nào để tránh generic array?

**Đáp án:** `Collection<? extends List<String>>` là lựa chọn phù hợp trong nhiều trường hợp, hoặc `List<List<String>>` nếu cần index và order.

**Bài 7 — Safe use**

Một method varargs chỉ lặp qua các nhóm để tạo `ArrayList<T>` mới rồi trả `List.copyOf`, không ghi vào varargs array hoặc expose array. Có phải ứng viên hợp lý cho `@SafeVarargs` không?

**Đáp án:** Có, khi method thỏa ràng buộc áp dụng annotation và toàn bộ đường gọi/helper thực sự an toàn.

**Bài 8 — Runtime component**

Khi method `void f(List<String>... lists)` được gọi theo varargs, mảng runtime có component class nào?

**Đáp án:** Tương ứng với raw `java.util.List`, không phải class riêng `List<String>`.

**Bài 9 — Ghi list hay sửa phần tử trong list**

`groups[0] = List.of("A")` và `groups[0].add("A")` có cùng nghĩa không?

**Đáp án:** Không. Dòng đầu thay reference ở slot 0; dòng sau sửa nội dung object list đang nằm trong slot (và có thể không được phép nếu list unmodifiable).

**Bài 10 — Phát hiện muộn**

Vì sao lệnh `groups[0].size()` vẫn có thể chạy sau khi slot bị thay bằng `List<Integer>`, nhưng `String s = groups[0].get(0)` lại lỗi?

**Đáp án:** `size()` không đòi phần tử là `String`, còn `get(0)` được dùng trong ngữ cảnh cần `String`, buộc runtime cast kiểm tra giá trị thực.

## 24. Quy trình sửa code và kiểm tra bằng công cụ

Khi nhận một cảnh báo về generic varargs:

1. Biên dịch với `javac -Xlint:unchecked -Xlint:varargs` để xác định vị trí cảnh báo và đường gọi.
2. Kiểm tra element type của varargs có non-reifiable hay không.
3. Tìm alias tới `Object[]` hoặc array type rộng hơn.
4. Xác minh không có ghi sai kiểu vào array slot, kể cả qua helper.
5. Xác minh array không escape tới nơi có thể ghi sai kiểu.
6. Nếu chỉ cần xử lý một tập các nhóm, cân nhắc `Collection<? extends List<T>>`.
7. Nếu varargs thật sự là API tốt nhất, giữ thân method chỉ đọc/copy và xem xét `@SafeVarargs` trong vị trí hợp lệ.
8. Viết test minh họa cả đường nhập hợp lệ và đường thử gây heap pollution có kiểm soát.
9. Tránh assert rằng “compiler không cảnh báo” có nghĩa nội dung thực sự an toàn.
10. Phân biệt `ArrayStoreException` khi gán với `ClassCastException` khi dùng dữ liệu.

### Quan sát descriptor bằng `javap`

Với class `UnsafeGenericVarargsDemo` đã biên dịch:

```bash
javap -p -s UnsafeGenericVarargsDemo
```

Ở mức mô tả kiểu JVM, method `corrupt(List<String>...)` có một tham số là array của raw `List`, với descriptor tiêu biểu:

```text
([Ljava/util/List;)V
```

Ký tự `[` biểu thị array, `L...;` là kiểu tham chiếu, `V` là `void`. Descriptor không mang `<String>`.

Để nhìn kỹ hơn những lệnh gán phần tử và cast:

```bash
javap -p -c UnsafeGenericVarargsDemo
```

Tìm `aastore` cho thao tác lưu phần tử mảng, `invokeinterface`/`invokevirtual` cho lời gọi method, và `checkcast` về `String` khi kết quả của `get()` được nhận như `String`.

Đối với một classfile do `javac` sinh, `javap -v` còn có thể hiển thị `Signature` giữ khai báo generic. Điều đó phục vụ metadata, **không khiến array store check tự kiểm tra được `<String>` ở runtime**.

## 25. Tổng kết

Rủi ro **ghi qua tham chiếu mảng kiểu cha** nằm ở sự kết hợp của:

1. **Generic varargs** có kiểu phần tử non-reifiable như `List<String>`.
2. **Array covariance** cho phép một tham chiếu `Object[]` trỏ đến mảng varargs.
3. **Type erasure** khiến runtime array component chỉ là raw `List`.
4. **Một phép ghi không an toàn** thay slot bằng `List<Integer>` mà JVM không thể phân biệt theo generic argument.
5. **Một phép đọc/cast về sau** vẫn tin hợp đồng `List<String>`, có thể gây `ClassCastException`.

Một `String[]` thường phát hiện ghi sai object ở thời điểm store bằng `ArrayStoreException`, còn mảng varargs của `List<String>` có thể không phát hiện được sai lệch generic argument cho tới lúc đọc.

Để tránh vấn đề, **không ghi qua alias kiểu rộng, không làm lộ mảng varargs cho mã có thể sửa sai kiểu, và ưu tiên chỉ đọc/copy**. Dùng `@SafeVarargs` khi thân method thực sự đáp ứng cam kết an toàn; nếu yêu cầu không cần array, cân nhắc chuyển API sang một `Collection`/`List` generic phù hợp.

### Tài liệu chuẩn

- [Java Language Specification §8.4.1 — Formal Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1): cách khai báo variable arity parameter.
- [Java Language Specification §15.12.4.2 — Evaluate Arguments](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.2): cơ chế đánh giá đối số và tạo mảng varargs khi cần.
- [Java Language Specification §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): các kiểu có đủ thông tin runtime.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): erasure của kiểu generic.
- [Java Language Specification §10.5 — Array Store Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5): kiểm tra khi ghi vào mảng.
- [Java Language Specification §9.6.4.7 — `@SafeVarargs`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.7): annotation và điều kiện sử dụng.
- [Java SE 17 `java.util.List`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): generic list methods trong các ví dụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-005">Rủi ro làm lộ mảng varargs ra bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro làm lộ mảng varargs ra bên ngoài trong Java Generics

## Mục tiêu học tập

Varargs là tính năng giúp viết phương thức nhận nhiều đối số: `log("A", "B", "C")` thay vì tự dựng mảng ở vị trí gọi. Tuy nhiên, tham số khai báo `T... values` thực chất được xử lý như **mảng `T[]`** trong thân phương thức. Khi T là kiểu không *reifiable* (*non-reifiable*), như `List<String>`, mảng runtime không giữ đủ thông tin generic để kiểm tra toàn bộ hợp đồng `List<String>[]`.

Rủi ro nổi bật là **varargs array escape**: phương thức trả thẳng mảng nhận được, lưu vào field, trao nó cho callback không tin cậy, hoặc trả một view dùng chính mảng làm backing storage. Một nơi khác có thể giữ alias mảng và thay một slot bằng đối tượng hợp lệ theo runtime component type nhưng sai tham số generic. Việc này tạo *heap pollution* và có thể dẫn tới `ClassCastException` muộn.

Bài này giúp bạn:

- Mô tả chính xác varargs được truyền dưới dạng mảng và vì sao generics làm phát sinh cảnh báo.
- Hiểu sự khác nhau giữa `String...`, `List<?>...` và `List<String>...`.
- Theo dõi quá trình tạo alias `Object[]`, chèn `List<Integer>` vào `List<String>[]` và lỗi phát sinh khi đọc.
- Đánh giá một phương thức có làm lộ varargs array bằng đường trực tiếp hay gián tiếp.
- Biết tác dụng, điều kiện và giới hạn của `@SafeVarargs`.
- Sửa API bằng snapshot độc lập, giữ bất biến dữ liệu và sử dụng collection có hợp đồng rõ ràng.
- Chạy `javac -Xlint` và viết kiểm thử chống lỗi alias/generic.

## 1. Varargs thực chất là mảng

Ví dụ:

```java
static void show(String... words) {
    System.out.println(words.length);
}
```

Caller có thể viết:

```java
show("A", "B", "C");
show(new String[] {"A", "B", "C"});
```

Trong `show`, biến `words` có kiểu `String[]`. Nó có `length`, hỗ trợ truy cập theo chỉ số và nhận phép gán vào slot nếu code thực hiện. Dạng lời gọi đầu được compiler tổ chức thành mảng trong quá trình gọi varargs; dạng thứ hai có thể đưa chính mảng caller đã tạo vào phương thức theo lời gọi fixed-arity.

Hai dạng khai báo `String... words` và `String[] words` không thể đứng cạnh nhau làm overload chỉ khác dấu ba chấm: bên trong và sau khi biên dịch, tham số varargs được biểu diễn bằng mảng. Dấu `...` thay đổi khả năng gọi với số lượng đối số biến đổi, không tạo ra một container runtime mới.

Với generic:

```java
static <T> void consume(T... values) {
    for (T value : values) {
        System.out.println(value);
    }
}
```

Trong thân phương thức, `values` có kiểu nguồn `T[]`. Nếu T là biến kiểu không có bound, erasure của T là `Object`. Với kiểu generic sâu hơn như `List<String>`, mảng runtime không có component class riêng mang chính xác tham số String; đó là điểm giao nhau giữa varargs và type erasure.

## 2. Vì sao reifiability và ownership cùng quan trọng?

Trong JVM, mảng mang runtime component class. Với `String[]`, phép ghi Integer qua alias `Object[]` bị `ArrayStoreException`. Còn với generic varargs `List<String>...`, runtime component type của array là `List` và không thể xác nhận type argument String; một `List<Integer>` vẫn qua được array store check.

| Element type của varargs | Reifiable? | Điều cần nhớ |
| --- | --- | --- |
| `String` hoặc `int` | Có | Mảng có runtime component String/int tương ứng. |
| `List<?>` | Có | Chỉ bảo đảm mỗi slot là List, không cam kết kiểu phần tử bên trong. |
| `List<String>`, `List<? extends Number>` | Không | Tham số hóa cụ thể/bounded wildcard không được runtime kiểm tra sâu. |
| `T` trong `<T> void f(T... values)` | Không nói chung | Erasure(T) và cách caller tạo array quyết định runtime component thực. |

**Ngay cả mảng reifiable cũng mutable**: method có thể được truyền chính mảng caller đã tạo. Bài này phân tích điều xảy ra khi method **trả mảng, lưu field hoặc công bố view backed by array**, khiến người khác giữ alias và có thể sửa các slot sau khi method kết thúc. Đây là vấn đề **quyền sở hữu và lifetime của array**, không chỉ là khả năng compiler tạo cảnh báo unchecked.

## 3. Thế nào là làm lộ mảng varargs?

Một phương thức đã **làm lộ** (*escaped*) varargs array nếu một nơi khác có thể giữ lại và sử dụng chính tham chiếu mảng sau khi phương thức kết thúc hoặc trong ngữ cảnh mà phương thức không còn kiểm soát.

Các ví dụ minh họa:

```java
// Nguy hiểm nếu T có kiểu non-reifiable và caller nhận T[]:
static <T> T[] returnArray(T... items) {
    return items;
}

// Cũng làm lộ nếu lưu chính tham chiếu mảng vào field:
// this.cachedItems = items;

// Hoặc giao chính mảng cho callback có thể giữ/chỉnh sửa:
// callback.accept(items);

// Arrays.asList trả view backed by mảng đầu vào:
// return Arrays.asList(items);
```

Các khai báo/đoạn chú thích này dùng để phân tích thiết kế, không phải một chương trình Java độc lập.

Đường escape không chỉ là `return items`. Một **view được chống lưng bởi mảng** cũng có thể tạo alias gián tiếp. Nếu trả `Arrays.asList(items)`, người nhận list có thể sử dụng `set` để sửa phần tử mảng gốc; ngược lại, sửa mảng phản ánh trong list view.

Ngược lại, một phương thức chỉ duyệt các giá trị để tính tổng, kiểm tra điều kiện hoặc copy phần tử sang collection mới thường không cần làm lộ array.

Hai khía cạnh phải đánh giá riêng:

1. **Type safety:** array component runtime có phản ánh đầy đủ loại phần tử generic hay không?
2. **Ownership/mutability:** caller và callee có cùng giữ hoặc sửa một đối tượng array không?

Ngay cả `String...` reifiable vẫn có nguy cơ chia sẻ và sửa mảng đầu vào nếu callee cố giữ hoặc ghi mảng của caller. Nhưng riêng non-reifiable varargs còn có rủi ro **heap pollution** do generic lồng sâu không được kiểm tra ở runtime.

## 4. Cơ chế tạo heap pollution khi mảng bị lộ

Xét phương thức:

```java
// Cần import java.util.List.
static <E> List<E>[] expose(List<E>... groups) {
    return groups;
}
```

Ở mã nguồn, chữ ký trả về `List<E>[]`. Khi caller truyền các đối số `List<String>` dưới dạng varargs, compiler có thể tạo mảng với component type runtime là `List` vì `List<String>` không reifiable.

Sau đó:

```java
List<String>[] escaped = expose(List.of("A"), List.of("B"));
Object[] alias = escaped;
alias[0] = List.of(42);           // Ghi thành công vào mảng List[].
String first = escaped[0].get(0); // ClassCastException lúc đọc.
```

Đây là đoạn trình bày tình huống lỗi; chương trình hoàn chỉnh ở phần 6 sẽ bắt ngoại lệ để hiển thị kết quả.

Phân tích chính xác:

1. `expose` nhận một mảng varargs mới hoặc mảng caller cung cấp; ở lời gọi này compiler tạo mảng có component runtime là List.
2. `return groups` trả chính tham chiếu mảng, không clone và không đổi cấu trúc.
3. Biến `escaped` được khai báo `List<String>[]`; lập trình viên đang tin từng slot trỏ tới một List String.
4. `Object[] alias = escaped` hợp lệ vì mảng tham chiếu của Java có tính hiệp biến (*covariance*).
5. `alias[0] = List.of(42)` ghi một List<Integer> vào mảng runtime List[]. Array store check **không ném lỗi** vì đối tượng được thêm thực sự là một List.
6. `escaped[0].get(0)` đưa ra Integer; code nguồn yêu cầu String. Compiler chèn phép `checkcast String` ở nơi sử dụng kết quả và JVM ném `ClassCastException`.

Kết luận: việc JVM kiểm tra thành phần mảng là List **không đồng nghĩa** kiểm tra được List chứa toàn String. Vì vậy lỗi không nhất thiết xảy ra tại thời điểm alias ghi giá trị không hợp lệ.

### Sơ đồ đường đi

```text
expose(List<String>, List<String>)
    |
    v
mảng runtime List[] (varargs)
    |
    | return groups; -- ESCAPE
    v
List<String>[] escaped
    |
    | Object[] alias = escaped;
    | alias[0] = List.of(42);
    v
mảng List[] vẫn chấp nhận List<Integer>
    |
    | String s = escaped[0].get(0);
    v
List.get() trả Object(Integer)
    |
    | compiler chèn checkcast String
    v
ClassCastException tại caller
```

Đây là một hình thức *heap pollution*: giá trị thực ở slot không còn thỏa hợp đồng `List<String>` mà biến được khai báo. Nơi thay đổi sai dữ liệu và nơi ngoại lệ phát sinh có thể thuộc các module khác nhau.

## 5. Cảnh báo compiler có ý nghĩa gì?

Với phương thức `expose`, chạy:

```bash
javac --release 17 -Xlint:all VarargsEscapeDemo.java
```

Compiler có thể đưa cảnh báo tại **khai báo** và **lời gọi**:

```text
[unchecked] Possible heap pollution from parameterized vararg type List<E>
[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
```

Hai cảnh báo này đã được kiểm chứng bằng `javac --release 17 -Xlint:all` trong chương trình ở phần 6.

- Cảnh báo thứ nhất: tham số varargs là kiểu non-reifiable; method có khả năng sử dụng mảng theo cách không an toàn.
- Cảnh báo thứ hai: tại điểm gọi varargs, compiler tạo mảng của kiểu đã xóa nên không xác minh đầy đủ phần generic.

**Warning không đồng nghĩa method chắc chắn gây lỗi**; một method chỉ duyệt phần tử và không làm lộ mảng có thể được thiết kế an toàn dù có chữ ký non-reifiable. Nhưng warning là tín hiệu cần review, không phải thứ nên triệt tiêu theo phản xạ.

`@SuppressWarnings("unchecked")` chỉ che các cảnh báo trong phạm vi áp dụng; nó không sửa runtime component type, không tạo copy và không bảo vệ alias. `@SafeVarargs` còn có tác động tới cảnh báo ở call site, nên đòi hỏi trách nhiệm lớn hơn từ tác giả thư viện.

## 6. Ví dụ Java 17 hoàn chỉnh có thể chạy

**Tệp `VarargsEscapeDemo.java`:**

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VarargsEscapeDemo {
    static <E> List<E>[] expose(List<E>... groups) {
        return groups;
    }

    @SafeVarargs
    static <T> List<T> snapshot(T... values) {
        List<T> copy = new ArrayList<>(values.length);
        for (T value : values) copy.add(value);
        return List.copyOf(copy);
    }

    @SafeVarargs
    static <T> List<List<T>> snapshotGroups(List<? extends T>... groups) {
        List<List<T>> copy = new ArrayList<>();
        for (List<? extends T> group : groups) {
            copy.add(List.copyOf(group));
        }
        return List.copyOf(copy);
    }

    static void observe(List<?>... groups) {
        for (List<?> group : groups) {
            System.out.println("observed-size=" + group.size());
        }
    }

    public static void main(String[] args) {
        List<String>[] escaped = expose(List.of("A"), List.of("B"));
        System.out.println("runtime-component="
                + escaped.getClass().getComponentType().getSimpleName());

        Object[] alias = escaped;
        alias[0] = List.of(42);
        System.out.println("array-store-accepted=" + escaped.length);

        try {
            String result = escaped[0].get(0);
            System.out.println(result);
        } catch (ClassCastException error) {
            System.out.println("late-failure=" + error.getClass().getSimpleName());
        }

        List<String> flat = snapshot("x", "y");
        System.out.println("flat=" + flat);
        try {
            flat.add("z");
        } catch (UnsupportedOperationException error) {
            System.out.println("flat-is-fixed="
                    + error.getClass().getSimpleName());
        }

        List<List<String>> groups = snapshotGroups(
                List.of("one"), List.of("two"));
        System.out.println("groups=" + groups);
        try {
            groups.get(0).set(0, "changed");
        } catch (UnsupportedOperationException error) {
            System.out.println("inner-is-fixed="
                    + error.getClass().getSimpleName());
        }

        observe(List.of("a"), List.of(3));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -Xlint:all VarargsEscapeDemo.java
java VarargsEscapeDemo
```

**Stdout đã kiểm chứng:**

```text
runtime-component=List
array-store-accepted=2
late-failure=ClassCastException
flat=[x, y]
flat-is-fixed=UnsupportedOperationException
groups=[[one], [two]]
inner-is-fixed=UnsupportedOperationException
observed-size=1
observed-size=1
```

Chương trình cố ý có **hai cảnh báo unchecked** tại `expose` và lời gọi tương ứng. Cảnh báo xuất hiện ở bước `javac`, không nằm trong stdout của `java`.

### 6.1. Mảng đã lộ có component runtime là List

Dòng `runtime-component=List` đến từ `getClass().getComponentType().getSimpleName()`. Mặc dù kiểu tĩnh là `List<String>[]`, mảng thực chứa các đối tượng List mà không có kiểm tra String ở cấp component runtime.

### 6.2. Ghi sai không tạo ArrayStoreException

`Object[] alias` và `escaped` là hai tham chiếu tới **cùng đối tượng mảng**. Ghi `List.of(42)` phù hợp với component runtime List. Vì thế chương trình tiếp tục tới dòng `array-store-accepted=2`; đây không phải bằng chứng dữ liệu generic vẫn đúng.

### 6.3. Lỗi xuất hiện muộn khi đọc

`escaped[0].get(0)` lấy phần tử Integer, nhưng caller đang yêu cầu String. Khi JVM thực thi cast do compiler chèn, ngoại lệ `ClassCastException` được bắt và in. Lúc này code gây heap pollution đã chạy xong.

### 6.4. Hai snapshot không trả mảng varargs

`snapshot` duyệt các phần tử và copy vào List mới. `snapshotGroups` duyệt mảng các nhóm rồi copy từng nhóm thành List mới trước khi trả một List ngoài mới. Không có tham chiếu varargs array trong giá trị trả về, nên caller không thể lấy lại mảng đó qua API này.

`List.copyOf` trả collection không sửa được; đó là lý do chương trình bắt `UnsupportedOperationException` ở hai nơi. Đây là **tính không sửa được về cấu trúc**, không phải deep-copy tự động các object phần tử T.

### 6.5. Khi chỉ cần quan sát, đừng hứa hơn mức cần thiết

`observe(List<?>... groups)` chỉ dùng `size()` trên các nhóm. Nó không đòi hỏi mọi nhóm chứa cùng kiểu tham số cụ thể; `List<?>` phù hợp với nhu cầu này và là reifiable type. Hàm không trả hoặc lưu array.

## 7. Dùng `@SafeVarargs` khi nào?

`@SafeVarargs` là một **cam kết của lập trình viên**: thân phương thức hoặc constructor xử lý varargs của kiểu không reifiable mà không tạo tình huống không an toàn liên quan mảng đó. Annotation cho phép compiler bỏ một số cảnh báo unchecked ở khai báo và call site varargs.

Nó **không**:

- Biến `List<String>` thành reifiable.
- Biến runtime `List[]` thành một mảng có component `List<String>`.
- Tự copy hoặc đóng băng tham số varargs.
- Chặn code khác giữ alias đã bị làm lộ.
- Bảo đảm compiler chứng minh đầy đủ an toàn của thân method.

Không được gắn annotation để che cảnh báo ở mẫu sau:

```java
// MẪU KHÔNG AN TOÀN - không thêm @SafeVarargs:
static <E> List<E>[] expose(List<E>... groups) {
    return groups;
}
```

Ở đây rủi ro phát sinh trực tiếp vì trả mảng ra ngoài. Nếu gắn `@SafeVarargs`, cảnh báo của caller có thể mất nhưng dữ liệu vẫn có thể bị làm sai.

### Điều kiện dùng annotation trong Java 17

`@SafeVarargs` hợp lệ trên constructor hoặc các **phương thức varargs** là `static`, `final` hoặc `private`. Không được gắn tùy ý lên một instance method có thể override. Ví dụ cố ý không biên dịch:

```java
class InvalidSafeVarargs {
    @SafeVarargs
    void instanceMethod(String... values) {}
}
```

Khi kiểm tra bằng `javac --release 17`, compiler từ chối vì method không static/final/private. Ngoài ra, `@SafeVarargs` là thừa khi phần tử varargs đã reifiable, như `String...`, vì không có cảnh báo tương ứng cần suppress.

**Đặc tả Java không coi annotation là chứng minh hình thức.** Compiler có thể cảnh báo với những body trông nguy hiểm, nhưng không bảo đảm phát hiện mọi đường alias. Vì vậy tác giả phải review toàn bộ thân method trước khi annotate.

### Mẫu có thể dùng annotation

```java
@SafeVarargs
static <T> List<T> snapshot(T... values) {
    List<T> copy = new ArrayList<>(values.length);
    for (T value : values) {
        copy.add(value);
    }
    return List.copyOf(copy);
}
```

Đoạn này nằm trong class với import `List` và `ArrayList`. Nó chỉ đọc mảng, sao chép phần tử vào List, rồi trả một cấu trúc mới; không sửa slot varargs, không trả/lưu mảng và không trao tham chiếu mảng cho bên ngoài.

Không nên kết luận từ `@SafeVarargs` rằng mọi dữ liệu về sau an toàn tuyệt đối: phần tử có thể mutable, null có thể không được chấp nhận bởi `List.copyOf` và caller vẫn có thể đã cố tình dùng raw type trước đó. Annotation chỉ nói về cách phương thức xử lý varargs theo hợp đồng.

## 8. View được chống lưng bởi mảng cũng có thể làm lộ alias

Một ví dụ thường gặp:

```java
static <T> List<T> backedView(T... values) {
    return java.util.Arrays.asList(values);
}
```

Đây là mẫu phân tích, không nên tự thêm `@SafeVarargs` khi chưa xem xét đường alias. `Arrays.asList(values)` tạo một List **có kích thước cố định** nhưng backed by mảng `values`. Nó không cho thêm/xóa thay đổi kích thước như ArrayList, nhưng `set` thay slot mảng.

Kiểm tra trên mảng reifiable:

```java
String[] original = {"A", "B"};
List<String> view = java.util.Arrays.asList(original);
view.set(0, "C");
System.out.println(original[0]); // C
```

`original` và `view` cùng truy cập một backing array. Trường hợp này chưa phải heap pollution vì phần tử là String, nhưng cho thấy **lộ alias qua view**. Với mảng varargs của một kiểu non-reifiable, trao view cho bên ngoài có thể làm đường thay đổi khó kiểm soát hơn.

Có thể tạo snapshot cấu trúc bằng cách copy:

```java
List<String> copy =
        List.copyOf(java.util.Arrays.asList(original));
```

`copy` là một List không sửa được độc lập về cấu trúc. Tuy vậy, khi xây dựng một generic varargs method được annotate, việc truyền trực tiếp mảng varargs vào API trung gian như `Arrays.asList(values)` có thể khiến `javac -Xlint:varargs` cảnh báo về khả năng rò rỉ tham chiếu, mặc dù đoạn copy cuối cùng có thể an toàn. Dùng vòng lặp như `snapshot` ở trên có đường dữ liệu trực quan hơn.

## 9. Clone mảng có giải quyết hoàn toàn không?

`values.clone()` trả về một mảng khác với mảng đầu vào, giúp tách **tham chiếu mảng** ở ranh giới. Nhưng điều đó không đồng nghĩa một `List<String>[]` được kiểm tra sâu kiểu String tại runtime.

Giả sử một helper trả về bản clone của mảng có component runtime List; caller có thể tiếp tục viết:

```java
List<String>[] result = /* mảng được trả về, kiểu tĩnh List<String>[] */;
Object[] wider = result;
wider[0] = List.of(99); // Runtime List[] vẫn chấp nhận List<Integer>.
```

Đoạn trên là sơ đồ giả định, không phải chương trình độc lập (biến result cần được tạo bởi một nguồn thích hợp). Mảng clone độc lập vẫn là List[] ở runtime và không có class token `List<String>`. Một alias `Object[]` mới của chính bản clone vẫn có thể gây heap pollution.

Ngoài ra, clone mảng chỉ sao chép **các tham chiếu phần tử**. Nếu phần tử là đối tượng mutable, cả mảng ban đầu và mảng clone còn tham chiếu tới cùng phần tử; việc thay đổi đối tượng đó vẫn hiển thị từ cả hai phía.

Từ đó có ba mức độ phòng vệ:

1. **Không trả mảng input:** giảm rò rỉ alias tới mảng ban đầu.
2. **Không trả mảng non-reifiable nếu không cần:** tránh việc caller có một `List<String>[]` dễ bị alias qua Object[].
3. **Copy dữ liệu và định nghĩa mutability rõ ràng:** với nested lists, copy từng tầng cần bảo vệ; nếu phải bảo đảm bất biến của object phần tử, cần thêm chiến lược deep copy phù hợp nghiệp vụ.

Trong nhiều API, trả `List<List<T>>` độc lập rõ nghĩa hơn trả `List<T>[]`.

## 10. Mảng của caller có thể được truyền thẳng qua varargs

Đừng mặc định compiler luôn tạo mảng mới. Với lời gọi có sẵn một mảng đúng kiểu, varargs method có thể nhận chính mảng đó:

```java
static void edit(String... words) {
    words[0] = "CHANGED";
}

// Trong main:
String[] input = {"A", "B"};
edit(input);
System.out.println(input[0]); // CHANGED
```

Ví dụ này độc lập với vấn đề generic không reifiable, nhưng cho thấy một **rủi ro ownership**: callee sửa mảng input của caller.

Ngược lại, `edit("A", "B")` khiến compiler tổ chức mảng tại vị trí gọi; caller không trực tiếp giữ tham chiếu mảng tạm được tạo theo cách gọi ấy, trừ khi method làm lộ nó.

Nếu contract của bạn muốn **không sửa dữ liệu caller**, đừng ghi các slot varargs. Nếu phải lưu giữ để xử lý bất đồng bộ hoặc sau khi method kết thúc, nên copy với chính sách null/mutability rõ ràng thay vì giữ `values`.

## 11. Làm gì khi cần nhiều nhóm List?

Nếu nghiệp vụ chỉ tính tổng số phần tử:

```java
static int count(List<?>... groups) {
    int result = 0;
    for (List<?> group : groups) {
        result += group.size();
    }
    return result;
}
```

`List<?>` giúp hàm chấp nhận cả `List<String>` và `List<Integer>` mà không hứa các nhóm chứa cùng kiểu T. Phương thức chỉ đọc và trả `int`, không có lý do nào để trả một mảng.

Nếu nghiệp vụ cần giữ dữ liệu lâu hơn:

```java
@SafeVarargs
static <T> List<List<T>> snapshotGroups(List<? extends T>... groups) {
    List<List<T>> copy = new ArrayList<>();
    for (List<? extends T> group : groups) {
        copy.add(List.copyOf(group));
    }
    return List.copyOf(copy);
}
```

Ở đây phương thức tạo các nhóm mới bằng `List.copyOf(group)` và tạo cả List ngoài mới. Không có mảng varargs nào được công bố. Đổi lại, API cần quy định rằng `null` không được chấp nhận bởi `List.copyOf`; các object T mutable không được deep-copy.

### Khi nào chọn `Collection` thay vì varargs?

Nếu caller vốn có collection, một chữ ký nhận `Collection<? extends T>` hoặc `List<? extends T>` thường đủ tốt và tránh tạo thêm varargs array. Varargs phù hợp khi mục tiêu chính là cách gọi ngắn với vài đối số; nó không phải bắt buộc cho mọi API chứa nhiều phần tử.

Cần dùng mảng thực có component runtime như `String[]`? Hãy xem giải pháp nhận `IntFunction<T[]>` (ví dụ `String[]::new`) hoặc `Class<T>`. Đừng ép `Object[]` thành `T[]` mà không có bảo đảm runtime tương ứng.

## 12. Các lỗi phổ biến trong code review

| Nhận định hoặc cách làm | Vấn đề | Cách xử lý |
| --- | --- | --- |
| “Tham số `T...` không phải mảng.” | Trong thân method, đó là `T[]`. | Tìm mọi alias/đường gán/lần truyền mảng. |
| “Mảng `List<String>[]` chặn mọi List Integer.” | Runtime component là List; không reify String. | Không lộ array; copy sang cấu trúc typed hợp lý. |
| “Không có `ArrayStoreException` thì không sai.” | Ghi List<Integer> vào List[] được chấp nhận. | Kiểm tra hợp đồng generic và điểm cast muộn. |
| “Thêm `@SafeVarargs` sẽ làm phương thức an toàn.” | Annotation không kiểm tra body hay sao chép. | Chỉ dùng sau khi chứng minh cách xử lý mảng an toàn. |
| “`return items.clone()` luôn giải quyết mọi rủi ro.” | Clone vẫn giữ kiểu mảng non-reifiable và chia sẻ object phần tử. | Đánh giá loại API trả về và copy tầng cần thiết. |
| “`Arrays.asList(items)` tạo List tách biệt.” | List view backed by mảng. | Dùng copy nếu mục tiêu là snapshot. |
| “Dập mọi unchecked warning ở caller là được.” | Làm mất tín hiệu rủi ro, không sửa heap pollution. | Review khai báo, điểm gọi và đường alias. |
| “`List<?>...` hứa mọi nhóm cùng kiểu.” | Wildcard không giới hạn không cam kết loại phần tử cụ thể. | Chọn chữ ký phù hợp đúng mục đích chỉ đọc. |
| “Trả snapshot là deep copy.” | `List.copyOf` chỉ copy cấu trúc, giữ object T. | Nếu cần deep copy, thiết kế riêng theo domain. |
| “Mọi varargs luôn tạo mảng mới.” | Caller có thể truyền mảng sẵn có. | Không ghi vào mảng nếu contract không cho phép. |

## 13. Quy trình kiểm tra và tái hiện lỗi

Khi một `ClassCastException` xuất hiện trong code đọc một `List<String>` từ một mảng hay collection do API varargs tạo, làm theo các bước:

1. **Giữ stack trace đầy đủ** để xác định vị trí `checkcast` thất bại và kiểu thực tế.
2. **Tìm gốc dữ liệu**: phương thức nhận `T...` hoặc `List<T>...` nào đã tạo/trả/lưu mảng?
3. **Kiểm tra chữ ký nguồn**: element type có non-reifiable không, có raw hoặc unchecked cast không?
4. **Tìm đường escape**: `return varargs`, gán field, cache, callback hoặc `Arrays.asList` view.
5. **Tìm alias kiểu rộng**: `Object[]`, raw `List[]`, `Collection[]` hoặc một supertype array.
6. **Tìm lệnh thay slot**: `alias[index] = ...` có thể đã đưa một container đúng kiểu runtime nhưng sai tham số generic.
7. **Bật lint**: `javac --release 17 -Xlint:unchecked,varargs ...`. Đừng che warning trước khi hiểu nó.
8. **Tái hiện có kiểm soát**: xây dựng test bằng các List String, đưa một List Integer qua alias rồi đọc; xác nhận trường hợp cũ thất bại và bản sửa không để lộ mảng.
9. **Refactor điểm sở hữu dữ liệu**: cho callee xử lý tại chỗ hoặc trả snapshot, không trả mảng non-reifiable trừ khi thực sự có lý do thiết kế mạnh.
10. **Đánh giá hợp đồng sau sửa**: null, phần tử mutable, performance copy, thread-safety, phụ thuộc legacy.

Bất kể compiler có hay không có cảnh báo trong một bản build cụ thể, việc một API công khai trao tham chiếu array mutable ra ngoài luôn đáng được xem xét về quyền sở hữu dữ liệu.

### Vì sao lỗi có thể bị trì hoãn?

Sau khi một phần tử mảng `List<String>[]` bị thay bằng `List<Integer>`, code gọi `array.length`, `array[0].size()` hoặc truyền mảng qua nhiều tầng có thể hoạt động bình thường. Lỗi chỉ phát sinh khi chương trình đòi hỏi một phần tử String từ List đang chứa Integer. Vì thế sửa dòng `get(0)` bằng ép kiểu khác thường chỉ che triệu chứng, không sửa nguồn heap pollution.

## 14. Bài tập thực hành

**Bài 1.** Mã dưới đây có an toàn không?

```java
static <T> T[] save(T... values) {
    return values;
}
```

**Đáp án:** Không thể coi là một thiết kế an toàn tổng quát. Nó làm lộ chính array. Nếu T là non-reifiable (đặc biệt khi T là một kiểu generic tham số hóa), caller có thể gặp heap pollution/aliasing. Ngay cả khi T là reifiable, nó vẫn có rủi ro chia sẻ mảng mutable. Không nên gắn `@SafeVarargs` chỉ để che warning.

**Bài 2.** Với `List<String>[]` mà runtime component là List, tại sao `Object[] alias` có thể ghi một `List<Integer>`?

**Đáp án:** Array covariance cho phép alias. Array store check chỉ biết component là List; `List<Integer>` là List ở runtime. JVM không kiểm tra tham số String trong slot.

**Bài 3.** Tại sao lỗi có thể xuất hiện ở `String value = escaped[0].get(0)`?

**Đáp án:** `List.get` sau erasure trả Object. Caller muốn String nên compiler chèn `checkcast String`; nếu phần tử thực là Integer, JVM ném `ClassCastException`.

**Bài 4.** `@SafeVarargs` có nên áp dụng cho một phương thức trả thẳng `List<E>[]` từ `List<E>...` không?

**Đáp án:** Không, vì làm lộ mảng cho caller giữ alias và thay slot. Annotation không thay đổi cơ chế runtime.

**Bài 5.** Một `String[]` truyền vào `void update(String... values)` có bị thay đổi nếu callee ghi `values[0]` không?

**Đáp án:** Có thể. Caller truyền chính mảng đã có, nên hai biến cùng trỏ tới một array. Cần contract quy định quyền sửa.

**Bài 6.** `Arrays.asList(varargs)` có tách khỏi backing array không?

**Đáp án:** Không. Đây là view kích thước cố định, nơi `set` có thể sửa mảng. Nếu cần snapshot độc lập, phải copy.

**Bài 7.** `List.copyOf` có làm một đối tượng T mutable trong list trở nên immutable không?

**Đáp án:** Không. Nó bảo vệ cấu trúc List không sửa được và tạo bản sao collection; bản thân object phần tử vẫn có thể mutable.

**Bài 8.** Nếu chỉ đếm phần tử các nhóm `List<String>` và `List<Integer>`, nên nhận `List<String>...` hay `List<?>...`?

**Đáp án:** `List<?>...` diễn đạt đúng nhu cầu không phụ thuộc kiểu phần tử; chỉ đọc `size` và không trả mảng.

**Bài 9.** Vì sao một method dùng `List<E>...` có warning dù body chỉ đọc?

**Đáp án:** Phần tử varargs không reifiable; compiler cảnh báo theo khả năng heap pollution ở ranh giới mảng. Nếu method thực sự chỉ đọc, không đổi slot, không chuyển mảng tới nơi có thể giữ alias và không làm lộ array, tác giả có thể cân nhắc `@SafeVarargs` khi đủ điều kiện.

**Bài 10.** Có nên trả `List<T>[]` được clone từ varargs để tránh mọi rủi ro không?

**Đáp án:** Clone tách khỏi mảng gốc, nhưng caller vẫn giữ một mảng non-reifiable có runtime component rộng, có thể tạo alias mới và thay slot. Nếu cần giao kết quả dưới dạng nhiều nhóm, `List<List<T>>` thường phù hợp hơn.

## 15. Tổng kết và tài liệu

Mấu chốt là sự kết hợp giữa ba cơ chế: **varargs được hiện thực bằng mảng**, **mảng có tính hiệp biến và mutable**, còn **đối số kiểu generic không reifiable không được JVM kiểm tra sâu**. Khi một mảng `List<String>[]` bị trả hoặc trao ra ngoài, code khác có thể thay một slot bằng `List<Integer>` thông qua `Object[]` mà array store check không cản được. Vi phạm có thể chỉ bộc lộ ở nơi đọc String, dưới dạng `ClassCastException`.

Cách thiết kế đáng tin hơn là giữ mảng varargs trong phạm vi cục bộ, chỉ đọc hoặc chuyển các phần tử sang cấu trúc mới rồi trả kết quả có hợp đồng rõ ràng. `@SafeVarargs` là lời cam kết của tác giả sau khi đã kiểm tra body, không phải phép biến đổi tự động khiến code sai trở nên an toàn.

Đọc thêm các tài liệu Java chính thức:

- [JLS Java SE 17: phần SafeVarargs và heap pollution](https://docs.oracle.com/javase/specs/jls/se17/jls17.pdf)
- [Oracle Java Tutorials: Non-Reifiable Types và generic varargs](https://docs.oracle.com/javase/tutorial/java/generics/nonReifiableVarargsType.html)
- [Java API: SafeVarargs annotation](https://docs.oracle.com/en/java/javase/18/docs/api/java.base/java/lang/SafeVarargs.html)

**Quy tắc thực hành:** Đừng coi tham chiếu mảng varargs là một giá trị vô hại để trả hoặc lưu. Hãy xác định ai có quyền sửa mảng, liệu kiểu phần tử có reifiable không và dữ liệu sẽ được sử dụng ra sao sau khi phương thức kết thúc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-006">@SafeVarargs và những phương thức hoặc constructor được phép dùng</a>

<details>
<summary>Xem chi tiết</summary>

# `@SafeVarargs`: Khi nào được dùng cho phương thức và constructor trong Java?

`@SafeVarargs` là annotation thuộc `java.lang`, dùng để **cam kết rằng phần thân phương thức hoặc constructor nhận varargs không thực hiện thao tác không an toàn với tham số varargs**. Nó đặc biệt hữu ích với **generic varargs** có phần tử kiểu *non-reifiable*, ví dụ `List<String>...`, `T...` hoặc `List<? extends T>...`, nơi trình biên dịch thường cảnh báo về nguy cơ *heap pollution*.

Tuy nhiên, `@SafeVarargs` **không khiến kiểu generic trở thành reifiable**, không thêm runtime check cho đối số kiểu, và không bảo đảm an toàn bằng phép màu. Trách nhiệm chứng minh tính an toàn thuộc về người viết phương thức/constructor.

**Trên Java 17+, có thể áp dụng `@SafeVarargs` cho:**

- **Constructor** có khai báo tham số varargs.
- **Phương thức `static`** có khai báo tham số varargs.
- **Phương thức instance `final`** có khai báo tham số varargs.
- **Phương thức `private`** có khai báo tham số varargs, bao gồm cả `private static` và `private` instance (khả năng dùng cho `private` instance được hỗ trợ từ Java 9).

Không được dùng annotation này cho một **phương thức instance thông thường có thể bị override** (chẳng hạn `public`/package-private/protected không `final`, không `static`), hoặc cho phương thức **không có `...`**.

## 1. Ôn lại varargs và generic varargs

Varargs cho phép caller truyền một số lượng đối số thay đổi mà không phải tự tạo mảng tại điểm gọi:

```java
// Snippet: phương thức có thể đặt trong một class.
static void show(String... names) {
    for (String name : names) {
        System.out.println(name);
    }
}

// Cách gọi:
show("An", "Binh");
show(new String[] { "An", "Binh" });
```

Trong thân phương thức, `names` thực chất là **`String[]`**. Java compiler có thể tạo mảng thích hợp khi gọi theo cú pháp nhiều đối số hoặc sử dụng mảng đã truyền trong lời gọi fixed-arity tương thích.

Varargs về cơ bản là một cú pháp dành cho **tham số mảng cuối cùng**; không phải một kiểu container generic mới.

Vấn đề xuất hiện khi kiểu phần tử của mảng varargs là một type variable hoặc parameterized type không reifiable:

```java
// Snippet có thể biên dịch, nhưng có thể bị cảnh báo:
static void process(java.util.List<String>... groups) {
    for (java.util.List<String> group : groups) {
        System.out.println(group.size());
    }
}
```

Tương đương về kiểu tham số ở phần thân: `groups` có kiểu `List<String>[]`. Nhưng Java **không cho phép khởi tạo trực tiếp** `new List<String>[n]`, vì `List<String>` không reifiable. Khi compiler phải xử lý varargs tại điểm gọi, nó có thể dùng mảng dựa trên erasure và phát cảnh báo unchecked, cho thấy không thể xác minh tất cả type arguments ở runtime.

Vì vậy, không nên suy luận rằng “cú pháp `List<String>...` biên dịch được” đồng nghĩa `List<String>[]` có thể được tạo và kiểm tra đầy đủ như `String[]`.

## 2. Lý do cần một cam kết riêng cho generic varargs

Với `static void process(List<String>... groups)`, tham số trong body là `List<String>[]` ở cấp source, nhưng component runtime của mảng chỉ thể hiện `List` sau erasure. Một `Object[]` alias có thể thay slot bằng `List<Integer>` mà array store check không kiểm tra được phần String trong generic argument.

```java
// Đoạn cố ý sai để hiểu rủi ro, không được gắn @SafeVarargs:
static void unsafe(List<String>... groups) {
    Object[] alias = groups;
    alias[0] = List.of(42);
    String value = groups[0].get(0); // ClassCastException.
}
```

Đoạn trên cần import `java.util.List` và được đặt trong một class. **Bản thân khai báo varargs non-reifiable chưa phải heap pollution**; chính đường ghi sai mới phá hợp đồng kiểu. Compiler có thể phát cảnh báo tại khai báo và lời gọi vì nó không chứng minh tổng quát rằng những đường như trên bị loại bỏ.

`@SafeVarargs` có ý nghĩa khi người viết có thể cam kết **body và các helper không tạo/để lộ thao tác không an toàn trên array**, và lời khai báo thuộc một nhóm được phép trong Java 17. Điều kiện đặt annotation và cam kết về hành vi là **hai phép kiểm tra riêng**; phần tiếp theo tập trung vào điều kiện cú pháp và ý nghĩa của annotation.

## 3. `@SafeVarargs` thực chất làm gì?

Annotation được dùng trên declaration varargs được phép, ví dụ:

```java
@SafeVarargs
static <T> void printAll(T... values) {
    for (T value : values) {
        System.out.println(value);
    }
}
```

Nó nói với compiler rằng **người viết phương thức đã xác minh phần thân không làm hỏng tính an toàn kiểu của mảng varargs**. Tác dụng nổi bật là compiler **không phát một số cảnh báo unchecked liên quan đến khai báo và lời gọi generic varargs này**.

Cụ thể, annotation thường giúp giảm:

- Cảnh báo tại **declaration site**, ví dụ cảnh báo có thể có heap pollution từ tham số có kiểu varargs non-reifiable.
- Cảnh báo tại **call site** về việc tạo mảng generic không kiểm tra đầy đủ được khi gọi một phương thức varargs non-reifiable đã được đánh dấu an toàn.

Tuy nhiên:

- Nó **không bảo đảm** phần thân an toàn; compiler không chứng minh được tất cả tình huống.
- Nó **không biến** `List<String>`, `T` hay `List<T>[]` thành reifiable.
- Nó **không cho phép** một phương thức instance tùy ý có thể bị override nhận annotation.
- Nó **không tắt mọi cảnh báo** không liên quan trong method body. Những raw/unchecked operations được viết trực tiếp trong thân vẫn cần được xem xét riêng.
- Nó **không làm thay đổi** cơ chế truyền varargs thành array, type erasure hay runtime dispatch.

Hãy hiểu `@SafeVarargs` là **một lời cam kết được compiler tin cậy trong phạm vi varargs**, không phải biện pháp sửa lỗi an toàn kiểu tại runtime.

## 4. Những declaration nào được gắn `@SafeVarargs` trên Java 17+?

| Declaration | Được phép? | Lý do |
| --- | --- | --- |
| `@SafeVarargs static <T> void f(T... xs)` | Có | `static` không bị override |
| `@SafeVarargs final <T> void f(T... xs)` | Có | `final` instance method không bị override |
| `@SafeVarargs private <T> void f(T... xs)` | Có, Java 9+ | `private` instance method không bị override |
| `@SafeVarargs private static <T> void f(T... xs)` | Có | Đồng thời `private` và `static` |
| `@SafeVarargs C(T... xs)` trên constructor | Có | Constructor không phải phương thức override |
| `@SafeVarargs public <T> void f(T... xs)` không `final` | Không | Instance method có thể bị override |
| `@SafeVarargs protected <T> void f(T... xs)` không `final` | Không | Instance method có thể bị override |
| `@SafeVarargs void f(T value)` | Không | Không có tham số varargs |
| `@SafeVarargs abstract void f(T... xs)` | Không | Không thuộc nhóm static/final/private/contructor được phép; không có thân để cam kết |

Bảng mô tả từng declaration độc lập; với class hoặc interface thực tế, vẫn cần đáp ứng các quy tắc Java khác như tính hợp lệ của modifier, signature và kiểu tham số.

### Vì sao chỉ những nhóm không bị override?

Annotation liên quan đến **hành vi của thân phương thức varargs**. Nếu một phương thức có thể bị override, bên gọi có thể dispatch đến phần thân khác; compiler không thể dựa vào một lời cam kết an toàn gắn với declaration dễ bị thay thế.

Constructor không bị override. `static` method bị *hiding* chứ không bị override bằng dispatch instance; `final` instance method không thể bị override; `private` instance method không được kế thừa theo cơ chế override.

Trên Java 8 trở về trước, điều kiện đối với `private` instance method khác Java 9+. Khi viết code Java 17, có thể dùng `@SafeVarargs` trên `private` instance varargs đúng cú pháp.

## 5. `@SafeVarargs` cho constructor

Một constructor có thể nhận generic varargs và sao chép dữ liệu sang collection riêng:

```java
import java.util.Arrays;
import java.util.List;

final class Batch<T> {
    private final List<T> values;

    @SafeVarargs
    Batch(T... items) {
        values = List.copyOf(Arrays.asList(items));
    }

    List<T> values() {
        return values;
    }
}
```

Khi caller dùng:

```java
Batch<String> names = new Batch<>("An", "Binh");
```

constructor nhận mảng `T...` và **không công khai mảng ấy**. Nó sao chép các phần tử vào một `List` không thể sửa cấu trúc bằng `List.copyOf`. Cách này tránh giữ một alias mảng varargs do caller có thể thay đổi sau đó.

Lưu ý về hợp đồng: `List.copyOf` không chấp nhận phần tử `null`. Nếu cần cho phép null, phải chọn cách lưu trữ khác và tài liệu hóa quy tắc rõ ràng.

**Chỉ giữ lại `T[] items` trong field và trả nó ra ngoài chưa chắc an toàn.** Array varargs có thể chính là mảng caller truyền vào; việc lưu reference cho tương lai cần phân tích khả năng alias và mutation, không chỉ dựa vào kiểu `T[]` được viết ở nguồn.

## 6. `@SafeVarargs` cho static generic varargs method

Một ví dụ hữu ích là hợp nhất nhiều danh sách generic theo hướng **chỉ đọc**:

```java
@SafeVarargs
static <T> java.util.List<T> merge(
        java.util.List<? extends T>... parts) {
    java.util.List<T> output = new java.util.ArrayList<>();

    for (java.util.List<? extends T> part : parts) {
        output.addAll(part);
    }

    return java.util.List.copyOf(output);
}
```

Cách gọi:

```java
java.util.List<String> both =
        merge(java.util.List.of("An"), java.util.List.of("Binh"));
```

Phương thức duyệt các `List<? extends T>` rồi thêm phần tử vào `List<T>` mới; không ghi một list sai kiểu vào các slot của mảng `parts`; không expose mảng `parts` cho code khác.

Khi dùng với `List.of`/`List.copyOf`, cần nhớ các API này không nhận null. Nếu nghiệp vụ cho phép null, có thể chọn `ArrayList` và một kết quả/mô hình sở hữu phù hợp.

**Tại sao generic wildcard `? extends T` hữu ích?** Từng danh sách nguồn chỉ cần sản xuất các giá trị có thể được xem như `T`; không cần phải là `List<T>` đúng chính xác. Nhưng việc wildcard phù hợp **không tự tạo** an toàn cho varargs; phần thân vẫn phải tránh heap pollution và rò rỉ mảng.

## 7. `final` instance method và `private` instance method

Hai nhóm này hợp lệ trong Java 17:

```java
class Reporter {
    @SafeVarargs
    final <T> int count(T... items) {
        return items.length;
    }

    @SafeVarargs
    private <T> String format(T... items) {
        return java.util.Arrays.toString(items);
    }

    String report() {
        return format("An", "Binh") + ": " + count(1, 2);
    }
}
```

Trong trường hợp này:

- `count` là `final` nên lớp con không thể override.
- `format` là `private` nên không tham gia override từ subclass.
- Cả hai nhận varargs thật bằng dấu `...`.
- Nội dung chỉ đọc mảng hoặc dựng chuỗi từ các phần tử; chúng không dùng `Object[]` alias để đưa một giá trị trái kiểu vào mảng tham số.

Một phương thức **`private static`** cũng được phép:

```java
@SafeVarargs
private static <T> T first(T... items) {
    return items[0];
}
```

Ví dụ giả định caller bảo đảm ít nhất một phần tử; trong code thực tế nên kiểm tra độ dài và báo lỗi thích hợp cho trường hợp rỗng. Khả năng dùng `@SafeVarargs` không đồng nghĩa method body tự xử lý mọi lỗi ngoài vấn đề type safety.

## 8. Những cách dùng sai bị compiler từ chối

### 8.1. Đặt trên method không phải varargs

```java
// KHÔNG BIÊN DỊCH:
class InvalidOne {
    @SafeVarargs
    static <T> void copy(T value) {
    }
}
```

`T value` là một tham số thường; method không có `...`. Annotation chỉ áp dụng cho **declaration có variable arity parameter**.

### 8.2. Đặt trên instance method có thể override

```java
// KHÔNG BIÊN DỊCH:
class InvalidTwo {
    @SafeVarargs
    public <T> void send(T... items) {
    }
}
```

`send` ở đây không `final`, không `private`, không `static`, cũng không phải constructor. Việc phương thức có type parameter `T` **không đủ** để annotation hợp lệ.

Sửa theo thiết kế:

```java
// Những hướng sửa độc lập về declaration:
class ValidTwo {
    @SafeVarargs
    public final <T> void send(T... items) {
        for (T item : items) {
            System.out.println(item);
        }
    }
}
```

Nếu phương thức phải có khả năng override, **không nên thêm `final` chỉ để dập cảnh báo** mà không cân nhắc thiết kế. Khi đó, có thể bỏ annotation, thay varargs bằng `List<T>`, hoặc đổi API thành một phương thức helper `static`/`private` thích hợp.

### 8.3. Annotation hợp lệ không có nghĩa body an toàn

```java
// Có thể biên dịch dù mang hành vi không an toàn:
@SafeVarargs
static void dangerous(java.util.List<String>... lists) {
    Object[] array = lists;
    array[0] = java.util.List.of(42);
    String item = lists[0].get(0); // ClassCastException
}
```

Lỗi này **không được sửa** bằng `@SafeVarargs`. Ngược lại, nó cho thấy người viết phương thức đã đưa ra **một lời cam kết sai**.

## 9. Chương trình Java 17+ hoàn chỉnh: trường hợp hợp lệ và phản ví dụ

Lưu mã sau thành **`SafeVarargsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SafeVarargsDemo {
    static final class Batch<T> {
        private final List<T> values;

        @SafeVarargs
        Batch(T... items) {
            values = List.copyOf(Arrays.asList(items));
        }

        List<T> values() {
            return values;
        }
    }

    @SafeVarargs
    static <T> List<T> merge(List<? extends T>... parts) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> part : parts) {
            result.addAll(part);
        }
        return List.copyOf(result);
    }

    @SafeVarargs
    private static <T> T first(T... items) {
        return items[0];
    }

    @SafeVarargs
    final <T> int count(T... items) {
        return items.length;
    }

    @SafeVarargs
    private <T> String show(T... items) {
        return Arrays.toString(items);
    }

    // KHÔNG AN TOÀN: chỉ giữ để chứng minh annotation không bảo vệ runtime.
    @SafeVarargs
    static void dangerous(List<String>... lists) {
        Object[] array = lists;
        array[0] = List.of(42);
        String ignored = lists[0].get(0);
    }

    public static void main(String[] args) {
        System.out.println("Constructor: "
                + new Batch<>("An", "Binh").values());
        System.out.println("Static method: "
                + merge(List.of("a"), List.of("b", "c")));
        System.out.println("Private static: " + first("A", "B"));

        SafeVarargsDemo demo = new SafeVarargsDemo();
        System.out.println("Final instance: " + demo.count(1, 2, 3));
        System.out.println("Private instance: " + demo.show("x", "y"));

        try {
            dangerous(List.of("safe"));
        } catch (ClassCastException exception) {
            System.out.println("Wrong @SafeVarargs: ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 -Xlint:varargs -Xlint:unchecked SafeVarargsDemo.java
java SafeVarargsDemo
```

**Standard output** của chương trình:

```text
Constructor: [An, Binh]
Static method: [a, b, c]
Private static: A
Final instance: 3
Private instance: [x, y]
Wrong @SafeVarargs: ClassCastException
```

Giải thích kết quả:

1. Constructor `Batch(T...)` hợp lệ; nó sao chép nội dung thành một list không thể sửa cấu trúc.
2. Static `merge(List<? extends T>...)` duyệt từng danh sách an toàn và trả list mới.
3. `private static first`, `final count` và `private show` minh họa những declaration hợp lệ cho annotation trên Java 17.
4. Phương thức `dangerous` cũng được phép **về mặt vị trí đặt annotation**, nhưng cố ý thay phần tử mảng varargs bằng một `List<Integer>` qua `Object[]`. Sau đó lấy `Integer` thành `String` và ném `ClassCastException`.
5. `try/catch` bao phản ví dụ cuối giúp toàn bộ chương trình vẫn chạy hết và cho thấy chính xác lỗi bị bắt.

Lệnh `javac -Xlint:varargs` có thể phát **cảnh báo bên trong phương thức `dangerous`** vì thao tác alias/mutation đáng ngờ. Đó là hành vi hữu ích: `@SafeVarargs` không phải một cam kết được compiler chứng minh hoặc một lá chắn loại bỏ mọi thông báo về code không an toàn.

**Không dùng phương thức `dangerous` trong ứng dụng thật.** Nó chỉ là phản ví dụ có kiểm soát, giúp phân biệt “annotation được phép đặt” với “lời cam kết an toàn là đúng”.

## 10. Những hoạt động nào thường an toàn với mảng varargs?

Một thân phương thức được đánh dấu `@SafeVarargs` nên được xem xét kỹ theo dữ liệu đi qua tham số varargs.

### 10.1. Duyệt để đọc, không sửa array slots

```java
@SafeVarargs
static <T> void visit(java.util.function.Consumer<? super T> action,
                       T... values) {
    for (T value : values) {
        action.accept(value);
    }
}
```

Phương thức không gán một object không tương thích vào `values[i]` và không trả mảng `values` trực tiếp. Nhưng khi đánh giá tính an toàn hoàn toàn, vẫn phải xem **`action` làm gì**: callback không đáng tin có thể gây tác dụng phụ hoặc ném exception. Việc callback không được cấp tham chiếu mảng `values` tự thân giúp tránh một đường rò rỉ array, song mọi invariants khác của nghiệp vụ vẫn cần kiểm soát.

### 10.2. Sao chép sang container typed mới

```java
@SafeVarargs
static <T> java.util.List<T> snapshot(T... values) {
    return java.util.List.copyOf(java.util.Arrays.asList(values));
}
```

Việc trả về một `List` mới thay vì tham chiếu array varargs giúp tách bộ chứa. Tuy nhiên `List.copyOf` không chấp nhận null và chỉ thực hiện **shallow copy**; đối tượng phần tử mutable vẫn có thể thay đổi trạng thái.

### 10.3. Những điều cần cảnh giác

Không nên đánh dấu an toàn một method nếu phần thân:

- Gán phần tử không đúng type argument vào array varargs qua `Object[]`, raw type hoặc unchecked alias.
- Trả trực tiếp mảng varargs `T[]` ra ngoài hoặc giữ một alias có thể bị sửa theo cách không kiểm soát, khiến caller tin vào một runtime component type không phù hợp.
- Truyền mảng đến phương thức khác mà bạn không kiểm soát, nơi nó có thể bị sửa hoặc rò rỉ.
- Thực hiện cast unchecked và ghi dữ liệu trái hợp đồng generic, rồi dựa vào annotation để che cảnh báo.
- Phá hợp đồng bằng dữ liệu không phù hợp dù việc tạo mảng varargs lúc đầu hợp lệ.

Các điểm này là **tiêu chí phân tích an toàn**, không phải lệnh cấm cú pháp đối với mọi thao tác trả array. Có API trả mảng có thể được chứng minh an toàn trong điều kiện rất cụ thể; tuy nhiên, với generic varargs non-reifiable, việc làm lộ trực tiếp mảng là nơi dễ tạo heap pollution và lời hứa kiểu sai, nên mặc định cần tránh.

## 11. Cảnh báo ở declaration và call site: phân biệt rõ

Không có annotation:

```java
// Snippet: javac có thể cảnh báo tại declaration.
static void log(java.util.List<String>... messages) {
    for (java.util.List<String> list : messages) {
        System.out.println(list);
    }
}
```

Một lời gọi:

```java
log(java.util.List.of("a"), java.util.List.of("b"));
```

Khi compiler phải sắp xếp các `List<String>` thành một array varargs, kiểu phần tử `List<String>` là non-reifiable. Vì vậy, compiler có thể cảnh báo **unchecked generic array creation for varargs parameter** ở nơi gọi.

Nếu đã kiểm tra phần thân `log` là an toàn, có thể đổi declaration thành:

```java
@SafeVarargs
static void log(java.util.List<String>... messages) {
    for (java.util.List<String> list : messages) {
        System.out.println(list);
    }
}
```

Cảnh báo varargs liên quan thường được giảm hoặc bỏ tại declaration và các call sites của method được đánh dấu. Nhưng nếu method còn sử dụng raw types không an toàn bên trong, `-Xlint:unchecked` hoặc `-Xlint:varargs` vẫn có thể chỉ ra hành vi cần điều tra.

### Không nên dùng `@SuppressWarnings("unchecked")` thay bừa `@SafeVarargs`

`@SuppressWarnings("unchecked")` là cơ chế tổng quát hơn để ẩn cảnh báo trong phạm vi nguồn có annotation. Nó không cam kết chính thức về độ an toàn của **varargs method** với caller, và có thể che các unchecked operations khác nếu đặt quá rộng.

`@SafeVarargs` giao tiếp hợp đồng tới cả nơi khai báo và nơi gọi. Chỉ nên dùng khi method/constructor thỏa điều kiện cho phép và được chứng minh an toàn; không dùng nó chỉ để “hết warning”.

## 12. Các câu hỏi dễ nhầm về generic varargs

### `@SafeVarargs` có bắt buộc với mọi varargs không?

**Không.** `String...`, `int...`, `Object...` thông thường có reifiable component types và có thể không gây cảnh báo generic varargs cần giải quyết. Dùng `@SafeVarargs` chỉ khi có lý do, tránh annotation dư thừa trên các varargs element types đã reifiable.

### `List<?>...` có luôn cần `@SafeVarargs` không?

`List<?>` là kiểu tham số hóa với toàn wildcard không bound và thuộc nhóm **reifiable**, nên tình huống khác `List<String>...`. Compiler có thể coi một `List<?>[]` là mảng với component type reifiable. Không nên gắn annotation theo thói quen chỉ vì thấy dấu `< >`.

### Có thể dùng `@SafeVarargs` để sửa `new T[10]` không?

**Không.** Annotation chỉ áp dụng cho declaration có tham số varargs và không làm type variable `T` reifiable. Biểu thức `new T[n]` vẫn bị cấm trong generic class/method.

### Nó có khiến một phương thức có thể override trở nên `final` không?

**Không.** `@SafeVarargs` không thay đổi modifier. Nếu method không thuộc nhóm được phép, phải sửa thiết kế (ví dụ `final` khi thực sự hợp lý) hoặc không dùng annotation.

### Annotation có tự ngăn heap pollution lúc runtime không?

**Không.** Code `dangerous` trong chương trình chạy cho thấy method được annotate sai vẫn có thể tạo dữ liệu sai kiểu và gây `ClassCastException`.

### Dùng trên constructor của class generic có hợp lệ không?

**Có.** Ví dụ `Batch(T...)` trong chương trình chạy. Constructor không bị override nên được phép; nhưng vẫn phải tự đảm bảo phần thân không làm hỏng array varargs.

### Dùng trên method instance `private` có hợp lệ từ khi nào?

Từ **Java 9**. Trên Java 17+ được dùng; trên các phiên bản Java trước đó, cần xem quy tắc hỗ trợ tương ứng thay vì giả định mã nguồn sẽ compile được ở mọi mức `--release`.

## 13. Quy trình tự kiểm tra trước khi thêm annotation

Trước khi viết `@SafeVarargs`, hãy đi qua checklist:

1. **Có varargs thật không?** Tham số cuối phải có `...`.
2. **Đúng loại declaration chưa?** Constructor, `static`, `final`, hoặc `private` instance method (Java 9+).
3. **Element type có non-reifiable không?** Với `T...`, `List<String>...`, `List<? extends T>...`, nguy cơ thường đáng chú ý. Với `String...` hoặc `List<?>...`, annotation có thể thừa.
4. **Có ghi vào array slots không?** Đặc biệt kiểm tra các phép gán qua `Object[]`, raw type hoặc reflection.
5. **Mảng có bị rò rỉ không?** Kiểm tra `return values`, field assignment, callback và các phương thức được gọi bên ngoài.
6. **Có unchecked operations khác không?** Không dùng annotation làm lá chắn che những chỗ chưa được chứng minh an toàn.
7. **Caller có thể truyền array trực tiếp không?** Varargs method cũng có thể được gọi với mảng có sẵn; phân tích nguy cơ alias khi method sửa hoặc giữ array.
8. **Bản sao có độc lập không?** Nếu cần lưu/trả tập phần tử, tạo container mới hoặc sao chép theo hợp đồng, tránh cho code khác thay đổi mảng varargs.
9. **Đã thử `javac -Xlint:varargs -Xlint:unchecked` chưa?** Cảnh báo là dấu hiệu cần xem, kể cả khi annotation hợp lệ về cú pháp.
10. **Đã có test dữ liệu bất thường chưa?** Thử null, empty array, các kiểu phần tử khác nhau và các trường hợp raw/unchecked nếu ranh giới API có thể gặp chúng.

Với code thư viện được nhiều nơi gọi, chi phí review cẩn thận thấp hơn rất nhiều so với việc xuất hiện `ClassCastException` tại một vị trí xa nơi heap pollution được tạo.

## 14. Bài tập tự kiểm tra

**Câu 1.** `@SafeVarargs static <T> void f(T... items)` có hợp lệ trên Java 17 không?

Có, vì đây là static varargs method. Nhưng body phải thực sự an toàn để lời cam kết đúng.

**Câu 2.** `@SafeVarargs public <T> void f(T... items)` trong một class không `final`, method không `final`, có hợp lệ không?

Không. Đây là instance method có thể bị override. Cần xem lại API, thêm `final` nếu thiết kế cho phép, hoặc bỏ annotation.

**Câu 3.** `@SafeVarargs private <T> void f(T... items)` có hợp lệ với Java 17 không?

Có. Java 9+ cho phép annotation trên private instance varargs method.

**Câu 4.** Có thể dùng `@SafeVarargs` cho constructor của `final class Bag<T>` nhận `T... values` không?

Có. Constructor không bị override; nhưng vẫn phải đảm bảo không gây heap pollution hoặc làm lộ array không an toàn.

**Câu 5.** Phương thức `dangerous(List<String>...)` đã có annotation nhưng ghi `List<Integer>` qua `Object[]`. Nó có an toàn không?

Không. Annotation không ngăn thao tác sai; mã có thể ném `ClassCastException` khi giá trị được đọc như `String`.

**Câu 6.** Vì sao `@SafeVarargs` giúp giảm cảnh báo ở call site?

Nó thông báo lời cam kết của tác giả rằng method không thực hiện thao tác không an toàn với tham số varargs, nên compiler có thể tin tưởng giảm những cảnh báo về generic array creation của lời gọi tương ứng.

**Câu 7.** Một method chỉ có `String... names` có cần `@SafeVarargs` không?

Thông thường không. `String` là reifiable và mảng varargs `String[]` có component type runtime rõ ràng; annotation có thể bị compiler nhận xét là không cần thiết.

**Câu 8.** `@SafeVarargs` có cho phép `new List<String>[3]` trong thân phương thức không?

Không. Generic array creation có component type non-reifiable vẫn bị cấm.

## 15. Kết luận

`@SafeVarargs` là **cam kết an toàn của tác giả** dành cho các constructor/method có tham số varargs, đặc biệt khi element type không reifiable. Trên Java 17+, annotation được phép trên **constructor, phương thức static, final instance và private instance** có varargs. Nó giúp xử lý các cảnh báo generic varargs tại declaration/call site, nhưng **không thay đổi erasure và không ngăn mã sai kiểu ở runtime**.

Khi sử dụng, hãy đảm bảo phần thân không ghi phần tử trái kiểu vào array varargs, không làm rò rỉ mảng theo cách phá an toàn kiểu, và không dùng unchecked tricks thiếu kiểm soát. Nếu không thể đưa ra lập luận an toàn chắc chắn, hãy giữ cảnh báo để điều tra hoặc thiết kế API khác, chẳng hạn dùng `List<T>` thay cho generic varargs.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-007">@SafeVarargs là cam kết an toàn, không sửa thân phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# `@SafeVarargs` là cam kết an toàn, không sửa thân phương thức

## Mục tiêu bài học

Trong Java Generics, phương thức nhận **generic varargs** như `List<String>... groups` có thể làm xuất hiện cảnh báo từ `javac` vì tham số varargs được biểu diễn bằng **mảng**, trong khi `List<String>` không phải một kiểu có thể kiểm tra đầy đủ tại runtime (*non-reifiable type*).

`@SafeVarargs` là một annotation cho phép tác giả của một số constructor/phương thức khẳng định rằng cách triển khai của mình **không thực hiện các thao tác không an toàn với varargs**. Nó có thể giảm những cảnh báo liên quan đến khai báo và điểm gọi, nhưng **không chèn runtime check mới, không bổ sung thông tin generic bị xóa, không làm sạch dữ liệu và không sửa bất kỳ câu lệnh nào trong method body**.

Sau bài này, bạn có thể:

- Giải thích đúng ý nghĩa của `@SafeVarargs`, khác biệt giữa cam kết từ tác giả và bảo đảm được runtime kiểm chứng.
- Xác định nguồn rủi ro của `List<T>...`: generic array, type erasure, array covariance và heap pollution.
- Phân biệt cảnh báo ở **khai báo**, **điểm gọi** và **bên trong thân phương thức**.
- Thấy một phương thức được gắn `@SafeVarargs` nhưng vẫn ném `ClassCastException` do chính phần triển khai không an toàn.
- Viết phương thức generic varargs chỉ đọc dữ liệu, tạo kết quả typed mới và không để array varargs thoát ra ngoài.
- Biết chính xác những loại constructor/method được phép sử dụng annotation trong Java 17+.
- Quyết định lúc nào cần bỏ `@SafeVarargs`, đổi cấu trúc API hoặc thay varargs bằng collection.
- Kiểm thử lời cam kết an toàn với trường hợp dữ liệu sai, alias, raw type và mã tiêu thụ kết quả.

Các ví dụ hoàn chỉnh đã biên dịch và chạy được trên **Java 17**; kết quả từ `javac`, `java` và `javap` được phân biệt rõ.

## 1. Định nghĩa và tư duy đúng về `@SafeVarargs`

Ví dụ khai báo một phương thức generic có varargs:

```java
@SafeVarargs
static <T> List<T> copyAll(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return List.copyOf(result);
}
```

Đoạn trên cần import `java.util.ArrayList`, `java.util.List` và đặt trong một lớp; chương trình đầy đủ ở mục 7.

Ý nghĩa của annotation là: tác giả đã kiểm tra cách dùng tham số varargs `groups` và **đứng ra bảo đảm rằng method không thực hiện thao tác thiếu an toàn với varargs có phần tử không reifiable**. Compiler không nhất thiết chứng minh được cam kết này qua mọi đường đi; nó tin vào cam kết để giảm một số cảnh báo tại khai báo và invocation.

Annotation **không**:

- Tạo thêm kiểu runtime `List<String>`.
- Biến `List<T>[]` thành một mảng có runtime component type `List<T>` riêng.
- Tự sửa phép gán hoặc cast sai.
- Kiểm tra mọi phần tử của các `List` ở runtime.
- Tự biến một implementation đang gây **heap pollution** thành implementation an toàn.
- Chặn `ClassCastException` nếu code vẫn đọc/cast dữ liệu sai.

Nói cách khác, `@SafeVarargs` giống một lời **cam kết về phương thức**, không phải công cụ chuyển đổi dữ liệu, cũng không phải lệnh yêu cầu JVM “bật generics runtime”.

## 2. Tại sao annotation không thể sửa thân method?

Một tham số `List<String>...` là `List<String>[]` ở source, nhưng runtime array chỉ kiểm tra component `List`, không kiểm tra String. Khi một method dùng `Object[]` alias để thay slot bằng `List<Integer>`, JVM có thể chấp nhận phép ghi; caller đọc phần tử dưới String sẽ ném `ClassCastException`.

```java
// Trong một static varargs method có import List:
Object[] alias = groups;     // Cùng array.
alias[0] = List.of(99);      // Runtime component là List.
String text = groups[0].get(0); // Sai kiểu khi đọc.
```

`@SafeVarargs` **không thay đổi** phép gán, cast, bytecode của body hoặc runtime component type. Nó chỉ nêu lời cam kết của tác giả để compiler giảm một số warning varargs tại declaration và caller. Compiler vẫn có thể phát `[varargs]` trong body khi thấy array alias/escape. Vì vậy ở bài này câu hỏi quyết định là **lời cam kết có đúng với implementation thực tế không?** — kể cả khi Java cho phép annotation về mặt cú pháp.

## 3. Heap pollution là rủi ro cần đánh giá

**Heap pollution (ô nhiễm kiểu trong heap)** xảy ra khi một biến của kiểu tham số hóa trỏ tới dữ liệu/đối tượng không tương thích với tham số kiểu mà biến ấy cam kết. Ví dụ `List<String>[]` được dùng để đọc các `List<String>` nhưng một ô của array thực đang chứa `List<Integer>`.

Đừng nhầm ba điều:

1. **Cảnh báo `unchecked`**: compiler không thể bảo đảm đầy đủ tính an toàn của một thao tác. Nó không đồng nghĩa chắc chắn đã có heap pollution.
2. **Heap pollution**: lời hứa kiểu ở source đã bị vi phạm đối với object/dữ liệu thực; lỗi có thể chưa xuất hiện.
3. **`ClassCastException`**: một cast runtime cụ thể thất bại, có thể là hệ quả của heap pollution hoặc một cast sai không liên quan generic.

Một `Object[]` trỏ tới `String[]` bị ghi `Integer` sẽ ném `ArrayStoreException` ngay lúc ghi, bởi JVM biết component type thật là `String`. Nhưng một `Object[]` trỏ tới varargs array `List<String>[]` có runtime component `List` **không** biết phần tử `String` bên trong list, nên phép ghi `List<Integer>` có thể lọt qua.

Không được kết luận từ một ví dụ an toàn rằng mọi generic varargs method luôn an toàn, cũng không được kết luận từ một cảnh báo rằng mọi lời gọi chắc chắn thất bại. Cần đọc **thân phương thức và đường đi của dữ liệu**.

## 4. Ví dụ phản chứng: đã gắn `@SafeVarargs` nhưng vẫn sai

Tạo tệp **`FalsePromiseDemo.java`**:

```java
import java.util.List;

public final class FalsePromiseDemo {
    @SafeVarargs
    static List<String>[] corrupt(List<String>... groups) {
        Object[] alias = groups;
        alias[0] = List.of(99);
        return groups;
    }

    public static void main(String[] args) {
        List<String> original = List.of("safe");
        List<String>[] result = corrupt(original);

        System.out.println("original: " + original);
        System.out.println("array component: "
                + result.getClass().getComponentType().getName());

        try {
            String bad = result[0].get(0);
            System.out.println(bad);
        } catch (ClassCastException ex) {
            System.out.println("broken promise: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all FalsePromiseDemo.java
java FalsePromiseDemo
```

**Stdout đã xác minh:**

```text
original: [safe]
array component: java.util.List
broken promise: ClassCastException
```

Hãy đặc biệt chú ý: file **biên dịch được** dù annotation đang đưa ra lời cam kết sai. Compiler vẫn có thể cảnh báo một số thao tác trong thân phương thức, nhưng Java không tự biến code nguy hiểm thành an toàn chỉ vì annotation hiện diện.

### Từng bước dẫn tới lỗi

1. `main` tạo `original` là một `List<String>` chứa `"safe"`.
2. Lời gọi `corrupt(original)` sử dụng varargs. Khi đóng gói một đối số rời thành mảng, array runtime có component type `java.util.List`.
3. `Object[] alias = groups` tạo **tham chiếu thứ hai tới cùng một mảng**. Không tạo bản sao và không sao chép nội dung từng list.
4. `alias[0] = List.of(99)` thay phần tử tại slot 0 của **mảng** thành một list mới chứa `Integer`. Runtime array store chấp nhận vì list mới vẫn là đối tượng thuộc `List`.
5. Method trả `groups`, đưa **mảng đã bị ô nhiễm** ra caller dưới hợp đồng nguồn `List<String>[]`.
6. `String bad = result[0].get(0)` yêu cầu một `String` nhưng giá trị thật là `Integer`; compiler đã phát sinh cast cần thiết, dẫn đến `ClassCastException`.

Dòng:

```text
original: [safe]
```

cũng minh họa một chi tiết debugging quan trọng: code trên **không** chèn số `99` vào chính `original`. Nó thay tham chiếu trong một **slot của mảng varargs**. `original` vẫn chứa chuỗi ban đầu, trong khi `result[0]` đã trỏ tới một list khác.

Nếu chỉ quan sát `original`, bạn có thể tưởng mọi thứ an toàn. Vì vậy khi đánh giá generic varargs, cần theo dõi riêng **mảng varargs** và **các collection tham chiếu bên trong mảng**.

## 5. Compiler có thể vẫn cảnh báo code nguy hiểm trong thân method

Trong ví dụ `FalsePromiseDemo.java`, chạy `javac -Xlint:all` đã phát ra các cảnh báo `[varargs]` trên hai biểu thức:

```java
Object[] alias = groups;
return groups;
```

Đoạn cảnh báo tiêu biểu đã xác minh:

```text
warning: [varargs] Varargs method could cause heap pollution from non-reifiable varargs parameter groups
```

Trong bản JDK 17 dùng kiểm thử, chương trình đã cho **hai** cảnh báo `[varargs]`: một tại phép gán alias, một tại việc trả array varargs. Đường xuất tệp và số dòng phụ thuộc nơi biên dịch; điều cần học là **compiler phát hiện những thao tác cụ thể có thể không an toàn**.

Điều đó đồng thời chứng minh hai nhận định:

- `@SafeVarargs` có thể giảm một số cảnh báo **unchecked ở khai báo hoặc điểm gọi**, nhưng không phải “tắt mọi chẩn đoán về varargs”.
- Khi **thân phương thức** thực hiện thao tác nguy hiểm, compiler có thể vẫn phát `[varargs]` nếu bật lint phù hợp; dù không cảnh báo, code vẫn có thể sai và cần kiểm tra bằng lý luận.

Đừng đánh giá một method là an toàn chỉ vì không thấy cảnh báo trong build mặc định. Mức cảnh báo tùy `javac` flags và phiên bản JDK; quan trọng hơn là **nội dung method có làm lộ, sửa, đánh tráo hoặc chuyển varargs array sang đường không được kiểm soát không**.

## 6. Ví dụ đối chứng: không annotation thì cảnh báo ở đâu?

Tạo tệp **`UnannotatedWarningDemo.java`**:

```java
import java.util.List;

public final class UnannotatedWarningDemo {
    static void print(List<String>... groups) {
        for (List<String> group : groups) {
            System.out.println("group: " + group);
        }
    }

    public static void main(String[] args) {
        print(List.of("ok"));
    }
}
```

Chạy:

```bash
javac -Xlint:all UnannotatedWarningDemo.java
java UnannotatedWarningDemo
```

Stdout của ứng dụng:

```text
group: [ok]
```

Ở bản Java 17 đã kiểm thử, `javac -Xlint:all` phát hai cảnh báo `[unchecked]`:

```text
Possible heap pollution from parameterized vararg type List<String>
unchecked generic array creation for varargs parameter of type List<String>[]
```

Một cảnh báo nằm tại **chữ ký method**, một cảnh báo ở **lời gọi** nơi cần tạo generic varargs array. Method `print` này chỉ đọc và duyệt các `List<String>`; cảnh báo phản ánh hạn chế chứng minh an toàn của kiểu varargs, không có nghĩa bản thân `print` đã sửa sai dữ liệu.

Nếu đã kiểm tra rằng một method phù hợp như `print` thật sự không tạo heap pollution, tác giả có thể cân nhắc `@SafeVarargs` để công bố cam kết. Ngược lại, nếu chưa chứng minh được, nên giữ cảnh báo, cải tiến body hoặc thay đổi thiết kế API.

## 7. Chương trình đúng: dùng `@SafeVarargs` với thân method an toàn

Tạo tệp **`SafeVarargsContractDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public final class SafeVarargsContractDemo {
    @SafeVarargs
    static <T> List<T> copyAll(List<? extends T>... groups) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> group : groups) {
            result.addAll(group);
        }
        return List.copyOf(result);
    }

    @SafeVarargs
    static <T> List<T> copyArrayElements(T... elements) {
        List<T> result = new ArrayList<>();
        for (T element : elements) {
            result.add(element);
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        System.out.println("strings: "
                + copyAll(List.of("An"), List.of("Binh")));

        List<Number> numbers = copyAll(
                List.of(1, 2), List.of(3.5));
        System.out.println("numbers: " + numbers);

        System.out.println("elements: "
                + copyArrayElements("A", "B"));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all SafeVarargsContractDemo.java
java SafeVarargsContractDemo
```

**Stdout đã xác minh:**

```text
strings: [An, Binh]
numbers: [1, 2, 3.5]
elements: [A, B]
```

Ở lượt biên dịch kiểm thử với `-Xlint:all`, chương trình này **không phát cảnh báo**. Nhưng điều bảo đảm phương thức an toàn không phải chỉ là số lượng cảnh báo bằng 0: **chính cách thực thi** mới là lý do có thể gắn annotation một cách có trách nhiệm.

### Kiểm tra `copyAll`

- Đọc `groups`, không ghi vào các slot của array.
- Không ép `groups` sang `Object[]` để thay phần tử.
- Không trả chính mảng varargs, không giữ nó trong field và không chuyển nó cho code có thể lưu/sửa nó.
- Với mỗi `List<? extends T>`, chỉ đọc phần tử dưới dạng `T`; không đẩy phần tử không phù hợp vào nguồn.
- Tạo một `ArrayList<T>` mới, thêm các phần tử được compiler kiểm tra, rồi trả bản sao qua `List.copyOf`.
- `List.copyOf` tạo một list không cho phép sửa trực tiếp qua API list; nếu `T` là object mutable, **phần tử không tự trở thành immutable sâu**.

Từ góc nhìn API, wildcard `? extends T` còn diễn đạt vai trò **producer** của mỗi danh sách nguồn: các phần tử được đọc từ nguồn và đưa vào danh sách kết quả.

### Kiểm tra `copyArrayElements`

Method duyệt từng phần tử `T` có trong varargs `elements`, tạo danh sách mới và không làm ô nhiễm mảng. Với các lời gọi typed thông thường, nó không đưa một giá trị không phù hợp vào array hoặc làm lộ array cho mã khác sửa.

Phương thức không ghi đè hay thay thế các slot `elements`, và không cast/chèn những đối tượng ngoài cam kết generic. Đây là mẫu đơn giản về **copy data out, do not leak the varargs array**.

Đối với cả hai method, hợp đồng còn phải nói rõ cách xử lý `null`: `List.copyOf` từ chối phần tử `null`. Đó là một chính sách API độc lập với an toàn của generic varargs; nếu muốn hỗ trợ `null`, cần chọn cách trả kết quả khác và ghi rõ hợp đồng.

## 8. Những method/constructor nào được gắn `@SafeVarargs`?

Java cho phép `@SafeVarargs` trên **constructor** và những method **không thể override** theo các điều kiện ngôn ngữ. Cụ thể:

| Vị trí | Hợp lệ? | Vì sao |
| --- | --- | --- |
| `static` varargs method | Có | Không có override thông thường |
| `final` instance varargs method | Có | Không thể override |
| `private` instance varargs method (Java 9+) | Có | Không thể override |
| Constructor có varargs | Có | Constructor không override |
| Public/protected instance varargs method có thể override | Không | Override có thể phá lời cam kết |
| Method không phải varargs | Không | Annotation chỉ dành cho lời khai báo có variable arity |
| `String...` reifiable | Về điều kiện hợp lệ có thể được gắn, nhưng thừa | Compiler có thể cảnh báo `Redundant SafeVarargs annotation` |

Mục tiêu của giới hạn không chỉ là syntax: nếu một method có thể được lớp con override bằng thân method gây heap pollution, lời cam kết gắn trên method của lớp cha có thể không được bảo đảm khi dispatch tới implementation khác.

### Kiểm tra với Java 17

Ví dụ dưới đây biên dịch được nhưng có một cảnh báo **redundant annotation** trên constructor `String...`:

```java
public final class AnnotationEligibility {
    @SafeVarargs
    private <T> void privateMethod(T... values) {}

    @SafeVarargs
    public final <T> void finalMethod(T... values) {}

    @SafeVarargs
    public AnnotationEligibility(String... parts) {}

    public static void main(String[] args) {
        new AnnotationEligibility().privateMethod("ok");
    }
}
```

Lệnh:

```bash
javac -Xlint:all AnnotationEligibility.java
java AnnotationEligibility
```

Chương trình không in dòng nào. Compiler Java 17 đã kiểm chứng phát cảnh báo tương ứng:

```text
warning: [varargs] Redundant SafeVarargs annotation. Varargs element type String is reifiable.
```

Như vậy, `@SafeVarargs` **không cần thiết** nếu varargs component đã reifiable như `String`; mảng `String[]` ở runtime đã có kiểm tra component type đầy đủ.

### Những khai báo không hợp lệ — chỉ để phân tích

Không sao chép các đoạn dưới như một chương trình cần chạy:

```java
class InvalidExamples {
    // ERROR: public instance method có thể override.
    // @SafeVarargs
    // public <T> void overridable(T... values) {}

    // ERROR: method không nhận varargs.
    // @SafeVarargs
    // static <T> void ordinary(java.util.List<T> values) {}
}
```

Việc `@SafeVarargs` bị giới hạn ở những lời khai báo phù hợp không chứng minh body của một method hợp lệ là an toàn. Ví dụ `FalsePromiseDemo.corrupt` là `static`, nên có thể gắn annotation **về mặt vị trí cú pháp**, nhưng body vẫn cố ý phá hợp đồng.

## 9. `@SafeVarargs` thay đổi điều gì ở compile time?

Cần phân biệt **mức độ cảnh báo** và **hành vi chương trình**:

| Khía cạnh | Không annotation | Có `@SafeVarargs` đúng chỗ |
| --- | --- | --- |
| Generic varargs declaration | Có thể phát cảnh báo unchecked | Compiler có thể bỏ cảnh báo ở khai báo |
| Invocation cần tạo generic varargs array | Có thể phát cảnh báo unchecked | Compiler có thể bỏ cảnh báo ở invocation nhờ cam kết của tác giả |
| Method body có alias/write/return nguy hiểm | Vẫn nguy hiểm | **Vẫn nguy hiểm**, `-Xlint:varargs` có thể cảnh báo |
| Erasure trong bytecode | Vẫn xóa generic type arguments | Vẫn xóa generic type arguments |
| Runtime component type của varargs array | Thường là erased component `List` | **Không đổi** |
| Một phép cast sai lúc runtime | Vẫn có thể ném exception | **Không bị annotation ngăn chặn** |

Trong chương trình `FalsePromiseDemo`, một lời gọi thiếu cảnh báo unchecked tại invocation **không** làm thao tác `alias[0] = List.of(99)` an toàn. Các cảnh báo `[varargs]` trong body giúp phát hiện vấn đề, nhưng không thay thế trách nhiệm của người thiết kế API.

Cũng không nên so sánh số warning giữa hai file để kết luận file nào an toàn hơn khi chúng có body khác nhau. Hãy kiểm tra **đường đi dữ liệu**, sau đó dùng cảnh báo như công cụ hỗ trợ.

## 10. Quan sát runtime và bytecode bằng `javap`

Sau khi biên dịch `FalsePromiseDemo.java`, chạy:

```bash
javap -s -p FalsePromiseDemo
```

Trích đoạn từ kiểm thử thực tế:

```text
static java.util.List<java.lang.String>[] corrupt(java.util.List<java.lang.String>...);
  descriptor: ([Ljava/util/List;)[Ljava/util/List;
```

Đọc descriptor:

- `[` biểu thị mảng.
- `Ljava/util/List;` biểu thị một `List` ở JVM descriptor.
- `([Ljava/util/List;)[Ljava/util/List;` tức nhận và trả một **mảng `List[]`** theo erased descriptor.

Trình dịch `javap` có thể hiển thị `List<String>...` vì generic `Signature` metadata, nhưng runtime array element check chỉ có component type `List`. Đây là nguyên nhân căn bản cho thấy annotation không thể làm `String` xuất hiện trở lại ở runtime descriptor.

Muốn thấy phương thức thực sự làm gì:

```bash
javap -c -p FalsePromiseDemo
```

Hãy tìm lệnh lấy phần tử mảng, lệnh `aastore` thực hiện phép ghi array, và lệnh `checkcast java/lang/String` tại điểm đọc/cast theo kiểu nguồn. Những số offset và index constant-pool thay đổi tùy phiên bản `javac`; kết luận đúng dựa vào **opcode và descriptor**, không dựa vào chỉ số cụ thể.

`@SafeVarargs` cũng là một annotation có thể quan sát trong class-file metadata, nhưng một dấu annotation hiện diện **không sửa instruction sequence** chứa phép ghi làm sai dữ liệu.

## 11. Dấu hiệu đỏ khi review generic varargs method

Những thao tác sau cần xem xét kỹ trước khi đặt hoặc giữ `@SafeVarargs`:

**Dấu hiệu A — Upcast varargs array thành `Object[]`.**

```java
Object[] alias = groups;
```

Bản thân một tham chiếu alias chưa nhất thiết làm hỏng dữ liệu ngay tức thì. Tuy nhiên, nó mở đường cho phép ghi các phần tử không phù hợp với type arguments bị xóa, như ví dụ `FalsePromiseDemo`.

**Dấu hiệu B — Ghi vào slot của mảng varargs.**

```java
alias[0] = List.of(99);
```

Nếu `groups` được khai báo `List<String>...`, đây là thao tác đánh tráo một `List<Integer>` vào vị trí người gọi có thể nghĩ là `List<String>`.

**Dấu hiệu C — Trả chính array varargs ra caller.**

```java
return groups;
```

Việc trả mảng có thể làm lộ array để caller hoặc mã khác sửa sau này. Nguy cơ phụ thuộc chữ ký, ownership và cách caller sử dụng; đó là lý do cần tránh các API giữ hoặc xuất varargs array không kiểm soát.

**Dấu hiệu D — Lưu array vào field dùng chung.**

```java
// Minh họa thiết kế nguy hiểm:
Object[] saved = groups;
```

Nếu phần state này tồn tại sau khi lời gọi kết thúc hoặc bị code khác truy cập, mảng có thể bị thay ngoài dự kiến. Hãy cân nhắc snapshot/collection có kiểu phù hợp và quyền sửa rõ ràng.

**Dấu hiệu E — Truyền array tới helper không rõ hợp đồng.**

```java
untrustedHelper(groups);
```

Một helper có thể lưu array, cast về `Object[]` hoặc thay phần tử. Không đủ cơ sở để tuyên bố an toàn chỉ vì method hiện tại không trực tiếp chứa `aastore`.

**Dấu hiệu F — Unchecked cast/raw operations.**

```java
// Ví dụ cần ngữ cảnh khai báo array:
@SuppressWarnings("unchecked")
List<String>[] assumed = (List<String>[]) someArray;
```

Cast unchecked không kiểm tra từng phần tử của các `List`; suppression không phải bằng chứng về kiểu thực. Cần xác nhận nguồn dữ liệu hoặc đổi thiết kế.

## 12. Khi nào dùng collection thay cho varargs?

Không phải API nào cũng cần `List<T>...`. Nếu caller đã có một collection động, phương thức nhận collection thường rõ ràng hơn:

```java
static <T> List<T> flatten(
        List<? extends List<? extends T>> groups) {
    List<T> out = new ArrayList<>();
    for (List<? extends T> group : groups) {
        out.addAll(group);
    }
    return List.copyOf(out);
}
```

Đây là đoạn method minh họa; import `java.util.List`, `java.util.ArrayList` và đặt trong lớp phù hợp. Với một caller có `List<List<String>>`, nó có thể truyền danh sách các nhóm theo hợp đồng tương thích mà không cần tạo generic varargs array tại invocation.

Một phương án khác là nhận một nhóm nguồn:

```java
static <T> List<T> snapshot(
        java.util.Collection<? extends T> source) {
    return List.copyOf(source);
}
```

Lợi ích của việc bỏ varargs khi không cần thiết:

- Không cần lời cam kết `@SafeVarargs`.
- Tránh cơ chế tạo varargs array của kiểu phần tử non-reifiable tại lời gọi.
- API thể hiện rõ một tập nguồn động thay vì danh sách đối số rời.
- Dễ diễn đạt ownership (snapshot, readonly view hay live collection) và chính sách `null`.

Nhưng chuyển sang collection **không** tự chặn raw type, unchecked cast hoặc dữ liệu sai kiểu từ nguồn ngoài. An toàn kiểu vẫn phải dựa vào hợp đồng đúng và việc xác minh dữ liệu ở ranh giới nếu nguồn không đáng tin.

## 13. Cần làm gì khi dữ liệu nguồn có thể đã sai kiểu?

Giả sử thư viện legacy trả raw `List`, hoặc một deserializer cung cấp danh sách chưa được kiểm tra. Gắn `@SafeVarargs` vào một method nhận dữ liệu đó **không xác nhận** rằng list thật sự chứa các `String`.

Một adapter mẫu:

```java
static List<String> checkedStrings(List<?> input) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < input.size(); i++) {
        Object item = input.get(i);
        if (!(item instanceof String s)) {
            throw new IllegalArgumentException(
                    "Invalid String element at index " + i);
        }
        result.add(s);
    }
    return List.copyOf(result);
}
```

Đoạn method minh họa dùng `List`, `ArrayList` từ `java.util`; có thể đặt trong một class để chạy. Nó thực hiện **kiểm tra từng phần tử**, từ chối giá trị sai và `null`, rồi trả collection typed mới mà không cần unchecked cast.

Những lưu ý bổ sung:

- Một `List<?>` cho phép duyệt phần tử dưới dạng `Object` mà không cho phép ghi tùy tiện qua tham chiếu này.
- `List<String>` trong chữ ký trả về là **cam kết sau validation**, không phải kết quả của một cast vô điều kiện.
- Nếu collection đầu vào bị sửa đồng thời, cần quy tắc synchronization/snapshot phù hợp; vòng lặp kiểm tra đơn thuần không là atomic snapshot.
- Nếu phần tử bên trong là object mutable, copy list không tự tạo deep immutability.

Dùng adapter tại ranh giới giúp lỗi kiểu dữ liệu được báo ngay nơi nhận dữ liệu, thay vì xuất hiện xa hơn khi mã generic nhận một lời hứa không đúng.

## 14. Những hiểu lầm quan trọng

### “Cứ method gắn `@SafeVarargs` là compiler đã chứng minh an toàn”

Sai. Đây là **cam kết từ tác giả**, và compiler có thể không xác minh được mọi trạng thái/đường dữ liệu. `FalsePromiseDemo` có annotation hợp lệ trên `static` method mà vẫn chủ động gây heap pollution.

### “`@SafeVarargs` loại bỏ mọi warning”

Sai. `javac -Xlint:all` vẫn có thể phát cảnh báo `[varargs]` cho alias/return array nguy hiểm trong method được annotate. Raw types, unchecked casts ở nơi khác cũng có thể tiếp tục tạo warning.

### “Không warning thì runtime không thể ném `ClassCastException`”

Sai. Không warning không đồng nghĩa JVM kiểm tra mọi type argument generic. `ClassCastException` có thể đến từ cast sai hoặc dữ liệu bị làm bẩn trên một đường mà compiler không quan sát đầy đủ.

### “Annotation biến `List<String>...` thành một mảng kiểm tra `String` ở runtime”

Sai. Runtime component type bị xóa về `List`; một `List<Integer>` cũng là một `List` ở cấp runtime array store.

### “Chỉ ghi vào từng `List` mới nguy hiểm; ghi vào mảng thì không sao”

Sai. Có thể làm hỏng tham chiếu generic bằng **thay slot của varargs array**, ngay cả khi danh sách gốc `original` không bị sửa.

### “Thấy `ClassCastException` nghĩa là method body chắc chắn gây heap pollution”

Không nhất thiết. Cast sai ở những vị trí không liên quan generics cũng có thể gây exception. Để tìm root cause, truy lại nơi dữ liệu sai được đưa vào và nơi type contract bị phá.

### “`@SafeVarargs` phù hợp cho mọi instance method”

Sai. Annotation chỉ được phép trên các lời khai báo đáp ứng quy tắc không override như `final`, `private` (Java 9+) hoặc `static`, và constructor. Một instance method có thể override không đủ điều kiện.

### “Nếu varargs type reifiable như `String...`, annotation càng an toàn hơn”

Thường **không cần annotation**. Runtime `String[]` đã giữ component type, và `javac` có thể báo annotation dư thừa.

### “Một lần test thành công đủ chứng minh `@SafeVarargs` hợp lý”

Không đủ. Test thường chỉ quan sát một vài đường dữ liệu. Cần rà soát aliasing, writes, array escape, helper calls và những tình huống có phần tử sai kiểu; một build pass không thay thế lập luận về thân method.

## 15. Quy trình review một phương thức `@SafeVarargs`

Khi gặp một method đã annotate trong codebase:

1. **Đọc chữ ký:** varargs component có non-reifiable không? Ví dụ `List<T>`, `List<String>` hay chỉ `String`?
2. **Kiểm tra điều kiện đặt annotation:** constructor, static, final hoặc private method hợp lệ theo phiên bản Java mục tiêu?
3. **Tìm mọi phép gán alias:** `Object[] a = args`, ép kiểu mảng, sao chép tham chiếu vào field, return array.
4. **Tìm mọi phép ghi vào slot mảng:** `args[i] = ...`, `a[i] = ...`; đánh giá khả năng ghi một giá trị có generic argument sai.
5. **Tìm helper nhận array:** helper có lưu/đổi mảng, trả mảng, hoặc gọi code không đáng tin không?
6. **Xem các generic casts/raw conversions:** đã có điều kiện chứng minh dữ liệu đúng kiểu không?
7. **Bật compiler lint:** `-Xlint:rawtypes`, `-Xlint:unchecked`, `-Xlint:varargs`; đọc các cảnh báo ở thân method.
8. **Thử dữ liệu xấu và đường đọc typed:** xác minh không tạo heap pollution khi có những loại phần tử khác nhau.
9. **Đánh giá API thay thế:** snapshot, `Collection<? extends T>`, các method overload thích hợp hoặc cơ chế type token khi thực sự cần.
10. **Chỉ giữ annotation nếu có lập luận an toàn:** ghi rõ invariant của method để người bảo trì sau hiểu vì sao nó hợp lý.

Điểm lưu ý: một method hiện tại an toàn có thể bị thay đổi sau này. Khi refactor body của method đang mang `@SafeVarargs`, phải **xác minh lại lời cam kết** thay vì xem annotation như phần trang trí cố định.

## 16. Câu hỏi tự luyện và đáp án

**Bài 1 — Tác dụng trực tiếp của annotation**

```java
@SafeVarargs
static void f(List<String>... values) { /* ... */ }
```

`@SafeVarargs` có tự thêm runtime check cho mọi phần tử `String` không?

**Đáp án:** không. Nó là lời bảo đảm từ tác giả về sự an toàn của varargs usage; runtime component type của array vẫn bị xóa về `List`.

**Bài 2 — Phát hiện đường sai**

Một method gắn annotation viết `Object[] x = groups; x[0] = List.of(7);` khi `groups` có kiểu `List<String>...`. Có thể xem method an toàn không?

**Đáp án:** không. Slot của mảng chứa danh sách có type argument thực không tương thích với `List<String>`; nếu caller đọc như `String` có thể gây `ClassCastException`.

**Bài 3 — Ý nghĩa warning trong body**

Tại sao `javac -Xlint:all` vẫn có thể cảnh báo `[varargs]` khi method đã gắn `@SafeVarargs`?

**Đáp án:** annotation giảm một số cảnh báo khai báo/điểm gọi nhờ cam kết, nhưng compiler vẫn có thể nhận ra thao tác alias/return array có rủi ro trong body. Nó không phải cơ chế tắt mọi cảnh báo.

**Bài 4 — Vị trí dùng annotation**

Trong Java 17, constructor, private varargs, static varargs, final instance varargs và public instance varargs có thể override — những vị trí nào hợp lệ?

**Đáp án:** bốn loại đầu hợp lệ, public instance method có thể override không hợp lệ. Điều kiện cú pháp không thay thế bằng chứng an toàn body.

**Bài 5 — So sánh hai dạng mảng**

Vì sao `Object[] x = groups; x[0] = List.of(7)` có thể thành công với `List<String>...`, còn `Object[] a = new String[1]; a[0] = 7` bị `ArrayStoreException`?

**Đáp án:** component type runtime của varargs array là `List`, nên `List.of(7)` vẫn là `List`; còn array `String[]` có component type `String` và không nhận `Integer`.

**Bài 6 — Mẫu triển khai nào phù hợp?**

Một static varargs method chỉ duyệt `List<? extends T>... groups`, tạo một `ArrayList<T>` mới và trả `List.copyOf` mà không giữ/sửa mảng. Có thể gắn `@SafeVarargs` không?

**Đáp án:** có thể, sau khi đánh giá đúng mọi đường của body. Đây là mẫu an toàn trong `SafeVarargsContractDemo`; không có thao tác đánh tráo array slot hoặc để array thoát ra ngoài.

**Bài 7 — Sửa method sai**

Method `FalsePromiseDemo.corrupt` có thể sửa tối thiểu như thế nào để tránh heap pollution?

**Đáp án định hướng:** không cast mảng varargs sang `Object[]` để thay phần tử, không ghi `List<Integer>` vào array mà caller tin là `List<String>[]`, không trả một array không được kiểm soát. Nếu cần tập hợp kết quả, duyệt nguồn và trả `List<String>` hoặc `List<List<String>>` mới theo hợp đồng; lúc đó đánh giá lại có còn cần varargs/annotation không.

**Bài 8 — Kiểm tra sau khi refactor**

Một method `@SafeVarargs` trước kia chỉ đọc varargs; sau khi tối ưu, tác giả chuyển array đó sang helper mới để cache. Có thể giữ annotation mà không review lại không?

**Đáp án:** không. Helper có thể giữ hoặc thay array khiến lời cam kết cũ không còn đúng. Cần phân tích quyền sở hữu array, tham chiếu alias và mọi đường ghi/đọc trước khi giữ annotation.

## Checklist cuối bài

Trước khi đặt `@SafeVarargs`, hãy tự xác nhận:

- [ ] Khai báo varargs hợp lệ và method/constructor đủ điều kiện được annotate.
- [ ] Đã biết type arguments nào không reifiable và runtime array component type thực tế là gì.
- [ ] Body không ghi phần tử không tương thích vào mảng varargs.
- [ ] Body không đưa mảng varargs sang đường có thể bị sửa hoặc giữ ngoài kiểm soát.
- [ ] Các cast/unchecked conversions trong body có bằng chứng hợp lệ hoặc đã được thay bằng cách typed an toàn.
- [ ] Các helper mà method gọi không làm phá cam kết.
- [ ] Đã biên dịch với lint phù hợp và điều tra mọi cảnh báo quan trọng.
- [ ] Đã kiểm thử dữ liệu không phù hợp và đường đọc typed có thể phát sinh `ClassCastException`.
- [ ] Nếu phần varargs không cần thiết, đã cân nhắc dùng collection/wildcard thay thế.
- [ ] Khi implementation thay đổi, sẽ kiểm tra lại toàn bộ lời cam kết an toàn.

## Kết luận

**`@SafeVarargs` là lời cam kết có trách nhiệm của tác giả API rằng phần xử lý generic varargs không gây mất an toàn kiểu; nó không phải công cụ tự sửa code.** Vì varargs được triển khai bằng array với component type generic bị xóa, một method ghi dữ liệu sai qua `Object[]` vẫn có thể gây heap pollution và `ClassCastException`, dù đã được annotate. Chọn một cách triển khai không ghi/không làm lộ mảng varargs, tạo kết quả typed mới khi phù hợp, bật lint và xác minh đường dữ liệu — sau đó mới sử dụng `@SafeVarargs` để biểu diễn cam kết thật sự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-varargs-008">Phân biệt cảnh báo unchecked, rawtypes và varargs</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt cảnh báo unchecked, rawtypes và varargs trong Java Generics

## 1. Ba loại cảnh báo: có liên hệ nhưng không đồng nghĩa

Khi biên dịch Java Generics, ba nhóm cảnh báo của `javac` thường xuất hiện cùng nhau:

- **`[rawtypes]`**: một tên class hoặc interface đã khai báo generic được dùng ở dạng **raw type**, ví dụ `List` thay cho `List<String>` hay `List<?>`.
- **`[unchecked]`**: compiler không thể chứng minh đầy đủ an toàn kiểu của một **phép chuyển đổi hoặc lời gọi**, chẳng hạn gọi `add` qua raw `List`, cast sang `List<String>` hoặc chuẩn bị một array cho `List<String>...`.
- **`[varargs]`**: cảnh báo liên quan đến **cách sử dụng tham số varargs không reifiable trong thân phương thức**, đặc biệt khi một phương thức được gắn `@SafeVarargs` lại làm lộ hoặc chuyển tiếp mảng varargs theo cách compiler coi là có thể gây heap pollution.

Một đoạn code có thể có **cả ba** nhóm. Nhưng:
- Raw type **không nhất thiết** tự tạo unchecked warning nếu chỉ khai báo biến mà không thực hiện thao tác nguy hiểm; dùng `-Xlint:rawtypes` để phát hiện dạng khai báo.
- Unchecked warning **không đòi** phải có raw type tường minh: một cast `Object` thành `List<String>` cũng có thể là unchecked.
- Cảnh báo ở khai báo varargs generic như `List<String>...` và lúc caller tạo array thường mang nhãn **`[unchecked]`**, không nhất thiết là `[varargs]`.
- `[varargs]` không phải đồng nghĩa với “bất kỳ phương thức nào có dấu `...`”, mà là category của những thao tác varargs được bộ phân tích của javac nhận diện theo các quy tắc cụ thể.

Mục tiêu không phải “làm mọi warning biến mất bằng annotation”, mà là hiểu **compiler đang thiếu bằng chứng về điều gì** rồi khắc phục ở đúng lớp thiết kế.

## 2. Cách bật chính xác các nhóm cảnh báo

Trên JDK 17 trở lên, biên dịch một tệp:

~~~bash
javac --release 17 -Xlint:rawtypes -Xlint:unchecked -Xlint:varargs LintCategoriesDemo.java
~~~

Hoặc viết gọn:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked,varargs LintCategoriesDemo.java
~~~

Ý nghĩa các tùy chọn:

| Tùy chọn javac | Cảnh báo cần quan sát |
| --- | --- |
| `-Xlint:rawtypes` | Chỗ dùng generic type mà không có type arguments. |
| `-Xlint:unchecked` | Unchecked method invocation, conversion, cast, generic array creation tại varargs và các thao tác không kiểm tra đầy đủ. |
| `-Xlint:varargs` | Cách dùng tham số varargs có nguy cơ gây heap pollution, đặc biệt trong method gắn `@SafeVarargs`. |
| `-Xlint:all` | Bật tất cả nhóm lint được javac hỗ trợ, có thể bao gồm thêm những nhóm không liên quan bài này. |
| `-Werror` | Coi các warning do compilation ghi nhận là lỗi build; **không** làm phép chuyển đổi trở nên an toàn hơn. |

Các thông điệp có thể thay đổi về cách diễn đạt, vị trí dòng, số lượng tùy phiên bản JDK và ngữ cảnh source. Hãy đọc **category trong dấu vuông**, vị trí được đánh dấu và các dòng `required/found` để xác định nguyên nhân.

`javac` đôi lúc chỉ đưa ra ghi chú:

~~~text
Note: ... uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
~~~

Ghi chú không có nghĩa warning đã được xử lý. Bật `-Xlint:unchecked` để xem thao tác cụ thể. Nếu dự án dùng Gradle/Maven, cấu hình compiler tương ứng hoặc chạy lệnh javac tái hiện với đúng classpath và source release.

## 3. rawtypes — lỗi ở cách ghi tên kiểu

Java định nghĩa:

~~~java
interface List<E> { /* thành viên generic */ }
~~~

Trong thư viện chuẩn, `java.util.List<E>` là interface generic. Nếu viết:

~~~java
List raw = new ArrayList();
~~~

hai chỗ cần chú ý:

1. `List` thiếu type argument E.
2. `new ArrayList()` cũng dùng tên generic `ArrayList<E>` theo dạng raw.

Với `-Xlint:rawtypes`, javac cảnh báo:

~~~text
[rawtypes] found raw type: List
[rawtypes] found raw type: ArrayList
~~~

Trong khi đó:

~~~java
List<String> names = new ArrayList<>();
List<?> unknown = names;
List<Object> anyObjects = new ArrayList<>();
~~~

đều là **parameterized types**, không phải raw. `List<?>` có wildcard, `List<Object>` có type argument Object cụ thể. Diamond `<>` ở `new ArrayList<>()` yêu cầu suy luận type arguments; nó **không** là cách viết raw `new ArrayList()`.

### Không phải kiểu nào thiếu <> cũng là raw

~~~java
String name = "Java";
Integer count = 7;
Object value = name;
Runnable job = () -> System.out.println("run");
~~~

Các kiểu `String`, `Integer`, `Object` và `Runnable` vốn không khai báo type parameter; đây là **non-generic types**. `-Xlint:rawtypes` không phải cảnh báo về mọi tên class viết không có `<...>`, mà chỉ về generic type được dùng raw.

Một lớp không generic vẫn có thể chứa generic method:

~~~java
final class Helpers {
    static <T> T same(T value) {
        return value;
    }
}
~~~

`Helpers` không generic, nên viết `Helpers.same("A")` không phải raw. Tham số kiểu T thuộc về method, không thuộc về class.

### Chỉ thấy rawtypes chưa kết luận đã có ClassCastException

~~~java
List legacy;
~~~

Đây là khai báo raw có thể sinh `[rawtypes]`, nhưng chưa có lời gọi thêm sai phần tử, ép kiểu hoặc thao tác runtime nào. Warning chỉ ra một **điểm mất type arguments**, không chứng minh đã xuất hiện lỗi runtime.

Khi xử lý, hãy xác định type argument phù hợp: `List<String>` nếu biết chắc loại phần tử, `List<?>` nếu chỉ cần đọc mà chưa biết loại, `List<Object>` nếu hợp đồng thật sự chấp nhận mọi Object.

## 4. unchecked — compiler không chứng minh đầy đủ an toàn kiểu

`[unchecked]` thường đi kèm những thao tác có thể hợp lệ ở cú pháp Java nhưng **không được xác thực toàn diện bằng runtime generic information**.

### 4.1. Unchecked call qua raw collection

~~~java
List raw = new ArrayList();
raw.add("Java");
~~~

Vì `raw` không gắn type argument E, lời gọi `add(E)` không được kiểm tra theo một E cụ thể. Với `-Xlint:unchecked`, javac cảnh báo:

~~~text
[unchecked] unchecked call to add(E) as a member of the raw type List
~~~

Lỗi không phải vì String bất hợp lệ với mọi List; vấn đề là **lời gọi qua raw type làm mất ràng buộc loại phần tử**. Cùng tham chiếu raw đó có thể được dùng để thêm Integer vào một danh sách mà alias khác khai báo là `List<String>`.

### 4.2. Unchecked conversion từ raw sang parameterized

~~~java
List raw = new ArrayList();
List<String> strings = raw;
~~~

javac có thể cho phép phép gán vì cơ chế tương thích mã cũ, kèm:

~~~text
[unchecked] unchecked conversion
required: List<String>
found:    List
~~~

Compiler không đi kiểm tra mọi phần tử trong raw List. Một giá trị sai có thể chỉ gây `ClassCastException` khi đọc với kiểu String về sau.

### 4.3. Unchecked cast không cần raw syntax tường minh

~~~java
Object unknown = List.of("A");

@SuppressWarnings("unchecked")
List<String> assumed = (List<String>) unknown;
~~~

Ngay cả khi không khai báo biến raw ở dòng này, cast `(List<String>)` vẫn không thể chứng minh mọi phần tử là String. Nếu bỏ annotation, `-Xlint:unchecked` báo:

~~~text
[unchecked] unchecked cast
~~~

Ở runtime, cast có thể kiểm tra được **lớp/interface List**, nhưng không xác nhận generic argument String. Việc danh sách ví dụ thực sự chứa "A" không khiến kiểu cast trở thành checked cast trong mắt compiler.

### 4.4. Checked cast là trường hợp khác

~~~java
Object input = "Java";
String string = (String) input;
~~~

Cast này nhắm đến class runtime reifiable `String` nên được JVM kiểm tra trực tiếp. Nó có thể ném `ClassCastException` nếu đối tượng sai lớp, nhưng **không vì thế mà là unchecked generic cast**.

Cần phân biệt: “cast có thể lỗi ở runtime” không tương đương “unchecked warning”. Unchecked nói về **điều compiler/runtime không đủ thông tin generic để kiểm tra toàn bộ**.

## 5. Vì sao cảnh báo unchecked và rawtypes thường xuất hiện cùng nhau?

Đây là ví dụ:

~~~java
List raw = new ArrayList();
raw.add("Java");
List<String> typed = raw;
~~~

Đọc theo thứ tự:

| Vị trí | Nhóm cảnh báo | Vấn đề |
| --- | --- | --- |
| `List raw` | `[rawtypes]` | Dùng raw List. |
| `new ArrayList()` | `[rawtypes]` | Khởi tạo raw ArrayList. |
| `raw.add("Java")` | `[unchecked]` | Gọi add qua raw generic receiver. |
| `List<String> typed = raw` | `[unchecked]` | Gán raw collection vào parameterized variable. |

Một đoạn rất ngắn có thể sinh nhiều cảnh báo vì chúng nói về **những thao tác khác nhau**. Sửa khai báo raw nhưng bỏ quên unchecked cast cũng không loại bỏ hết rủi ro. Ngược lại, chỉ suppress `[unchecked]` sẽ **không tự xử lý** thực tế raw types vẫn tồn tại trong code.

Với mã legacy, nên sửa tại nguồn tạo/giao dữ liệu, tránh truyền raw references qua nhiều tầng application. Nếu thật sự chưa biết E, chọn wildcard và validate phần tử trước khi khẳng định một E cụ thể.

## 6. Vì sao một warning có chữ varargs lại thuộc `unchecked`?

`T...` và `List<String>...` là **tham số mảng** ở source. Khi compiler cần tạo array cho một variable-arity invocation với component không reifiable như `List<String>`, runtime array chỉ kiểm tra component `List` mà không kiểm tra String; vì vậy nó có thể phát **`[unchecked]` generic array creation** ở caller, cùng cảnh báo khả năng heap pollution tại method declaration.

```java
static void groups(List<String>... values) {
    System.out.println(values.length);
}
// groups(List.of("A"), List.of("B"));
// Hai warning [unchecked] có thể xuất hiện tại khai báo và nơi gọi.
```

Đoạn minh họa cần import `java.util.List` và lời gọi trong method phù hợp. Cần phân biệt: `new List<String>[2]` là **compile error** do component type không reifiable; nhưng gọi generic varargs có thể vẫn biên dịch kèm warnings. `[varargs]` lại thường chỉ **cách dùng/forward/escape array trong thân method đã được đánh dấu `@SafeVarargs`**, không nhất thiết xuất hiện chỉ vì dấu `...` trong chữ ký.

Đây là khác biệt cốt lõi giữa **phân loại diagnostic** và **nguyên nhân runtime**. Muốn quan sát đủ ba nhóm, dùng `-Xlint:rawtypes,unchecked,varargs` thay vì chỉ bật `-Xlint:varargs`.

## 7. Cảnh báo varargs ở khai báo và call site thường là [unchecked]

Một chương trình có:

~~~java
static void groups(List<String>... values) {
    System.out.println(values.length);
}

static void call() {
    groups(List.of("A"), List.of("B"));
}
~~~

khi bật `-Xlint:unchecked` thường có **hai vị trí cảnh báo**:

~~~text
[unchecked] Possible heap pollution from parameterized vararg type List<String>
[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Cảnh báo thứ nhất gắn với **declaration** sử dụng `List<String>...`; cảnh báo thứ hai gắn với **call site** cần tạo một generic array backing cho lời gọi.

Điểm dễ gây nhầm: dù thông điệp chứa chữ `vararg`, nhãn lint thực tế thường là **`[unchecked]`**, vì compiler đang nói đến tạo và sử dụng mảng generic có component không reifiable, không có phép kiểm tra kiểu đầy đủ.

Chỉ bật:

~~~bash
javac --release 17 -Xlint:varargs Example.java
~~~

không nhất thiết cho thấy **hai cảnh báo trên ở dạng chi tiết**. Khi debug varargs generics, luôn bật cả `unchecked` để quan sát declaration/call-site warnings.

## 8. [varargs] kiểm tra điều gì trong thân phương thức?

Hãy xét một method được gắn annotation:

~~~java
@SafeVarargs
static <T> T[] expose(T... values) {
    return values;
}
~~~

`@SafeVarargs` là cam kết rằng tác giả **không làm thao tác có thể gây mất an toàn kiểu liên quan đến varargs array**. Nhưng body trên lại trả trực tiếp mảng backing cho caller, một cách làm có thể làm lộ alias và dẫn đến heap pollution hoặc lỗi runtime.

Với `-Xlint:varargs`, javac 17 đưa cảnh báo tại `return values`:

~~~text
[varargs] Varargs method could cause heap pollution from non-reifiable varargs parameter values
~~~

Cảnh báo `[varargs]` thường nhằm vào **việc sử dụng mảng trong body**, ví dụ:
- Trả trực tiếp mảng varargs.
- Truyền mảng vào một API khác mà compiler không thể chứng minh là không sửa/làm lộ alias.
- Các đường thao tác có thể làm varargs array bị phơi ra hoặc dùng không an toàn.

Không phải mọi thao tác đọc `values.length`, duyệt phần tử, hoặc dùng một varargs method đều đương nhiên sinh `[varargs]`.

### Vì sao @SafeVarargs không bị compiler coi là “đã chứng minh an toàn”?

Annotation là **lời cam kết của người viết API**, không phải chứng minh hình thức rằng mọi đường chạy đều an toàn. Compiler có thể cảnh báo khi thấy mẫu đáng ngờ nhưng không phát hiện tất cả hành vi không an toàn xuyên qua lời gọi hàm khác.

Nếu bỏ annotation để lộ `[unchecked]`, rồi sau đó gắn `@SafeVarargs` chỉ để im warning nhưng method vẫn trả mảng, ta đang **che dấu hiệu rủi ro, không giải quyết bất biến**.

Với Java 17, `@SafeVarargs` chỉ được đặt ở constructor hoặc method varargs phù hợp như static, final hay private (không phải một instance method có thể override tùy ý). Nó không khiến type arguments trở thành reifiable.

## 9. Chương trình Java 17+ hoàn chỉnh minh họa cả ba nhóm

Lưu toàn bộ nội dung sau vào **LintCategoriesDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class LintCategoriesDemo {
    static void rawExample() {
        // [rawtypes] ở hai vị trí sử dụng generic class raw.
        List raw = new ArrayList();

        // [unchecked] vì gọi add trên raw List.
        raw.add("Java");

        // [unchecked] vì chuyển raw List thành List<String>.
        List<String> strings = raw;

        System.out.println("raw=" + strings.get(0));
    }

    static void uncheckedCast() {
        Object unknown = List.of("A");

        // [unchecked] dù không dùng biến raw ở đây.
        List<String> assumed = (List<String>) unknown;

        System.out.println("cast=" + assumed.get(0));
    }

    // [unchecked] declaration/call-site với non-reifiable List<String>.
    static void groups(List<String>... values) {
        System.out.println("groups=" + values.length);
    }

    // Đọc và copy vào collection mới, không trả hoặc phơi mảng.
    @SafeVarargs
    static <T> List<T> safeCopy(T... values) {
        List<T> result = new ArrayList<>();

        for (T value : values) {
            result.add(value);
        }

        return List.copyOf(result);
    }

    // Cố ý vi phạm ý định an toàn: phơi mảng backing cho caller.
    // Khi -Xlint:varargs được bật, javac cảnh báo ngay tại return.
    @SafeVarargs
    static <T> T[] unsafeExpose(T... values) {
        return values;
    }

    public static void main(String[] args) {
        rawExample();
        uncheckedCast();

        groups(List.of("x"), List.of("y"));

        System.out.println(
                "copy=" + safeCopy("A", "B")
        );

        System.out.println(
                "exposed=" + unsafeExpose("a", "b").length
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked,varargs LintCategoriesDemo.java
java LintCategoriesDemo
~~~

Khi biên dịch với javac 17-compatible, chương trình minh họa sinh các nhóm cảnh báo tương ứng:

~~~text
[rawtypes] found raw type: List
[rawtypes] found raw type: ArrayList
[unchecked] unchecked call to add(E) as a member of the raw type List
[unchecked] unchecked conversion
[unchecked] unchecked cast
[unchecked] Possible heap pollution from parameterized vararg type List<String>
[varargs] Varargs method could cause heap pollution from non-reifiable varargs parameter values
[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
~~~

Danh sách trên **chỉ liệt kê phần lõi của các warning**, không phải chép nguyên văn toàn bộ output của javac (có thể có file path, vị trí dòng, phần `required/found` và tổng số warning). Điều quan trọng là dòng nào có nhãn nào.

**Stdout chính xác của chương trình:**

~~~text
raw=Java
cast=A
groups=2
copy=[A, B]
exposed=2
~~~

Bài dùng `unsafeExpose` như một ví dụ **sai thiết kế**, không phải mẫu nên triển khai ở ứng dụng thật. Vì caller ở đây truyền hai String thật, chương trình vẫn chạy và in `exposed=2`. Việc chạy thành công **không chứng minh** rằng thân method thỏa hợp đồng `@SafeVarargs` cho mọi lời gọi và tương tác alias.

### Tại sao chương trình có warning nhưng không ném exception?

Compiler đang chỉ ra **khả năng** không an toàn do các đường sử dụng raw/unchecked/varargs, không tuyên bố mọi dữ liệu trong ví dụ đã sai. Một danh sách chỉ chứa String và được đọc như String sẽ chạy được dù từng có unchecked conversion. Nhưng cùng kiểu warning đó có thể che đường đưa Integer vào danh sách rồi dẫn tới ClassCastException.

Điều cần sửa là kiến trúc hoặc đường chuyển đổi không được kiểm tra, không phải chỉ xem test này có ném ngoại lệ hay không.

## 10. Bật từng nhóm riêng để xác định nguyên nhân

Vẫn dùng `LintCategoriesDemo.java` như trên:

~~~bash
javac --release 17 -Xlint:rawtypes LintCategoriesDemo.java
javac --release 17 -Xlint:unchecked LintCategoriesDemo.java
javac --release 17 -Xlint:varargs LintCategoriesDemo.java
javac --release 17 -Xlint:rawtypes,unchecked,varargs LintCategoriesDemo.java
~~~

Tùy chọn đầu tiên làm nổi bật **nơi raw types được sử dụng**; những cảnh báo unchecked chưa được bật chi tiết có thể được tóm lược bằng Note.

Tùy chọn thứ hai cho thấy:
- Lời gọi `raw.add` và chuyển từ raw sang `List<String>`.
- Cast `(List<String>) unknown`.
- Declaration `List<String>...` và call-site generic array creation.

Tùy chọn thứ ba có thể chỉ ra cách body `unsafeExpose` trả tham số varargs ra bên ngoài. Những cảnh báo unchecked khác có thể không được in chi tiết ở lần chạy chỉ bật varargs.

Tùy chọn cuối giúp đặt **mọi vị trí cạnh nhau** để phân biệt: hai warning có cùng nguyên nhân tổng quát “mất khả năng kiểm tra kiểu” nhưng thuộc hai loại thao tác khác nhau.

Nên dùng cùng `--release 17` và cùng nguồn khi đối chiếu, tránh nhầm do các thay đổi compiler hay source-level.

## 11. Phân biệt unchecked conversion, unchecked cast và checked cast

Ba thao tác có vẻ giống nhau nhưng khác bản chất.

### Unchecked conversion

~~~java
List raw = List.of("Java");
List<String> words = raw;
~~~

Gán từ raw List sang `List<String>` được phép do tương thích cũ, nhưng compiler không xác minh mọi phần tử. `[unchecked] unchecked conversion` mô tả đúng điều đó.

### Unchecked cast

~~~java
Object input = List.of(10);
List<String> assumed = (List<String>) input;
~~~

Cast theo `List<String>` không kiểm tra phần tử Integer. Nếu gọi `assumed.get(0)` như String, có thể ném ClassCastException. `[unchecked] unchecked cast` xuất hiện vì runtime không có đủ generic argument information.

### Checked cast

~~~java
Object input = "Java";
String text = (String) input;
~~~

Đích String là class runtime kiểm tra được. Đây là checked reference cast; nếu input thực sự chứa Integer, nó ném ClassCastException ngay, nhưng **không phải cảnh báo unchecked generic**.

Không nên suppress warnings chỉ vì “dù sao Java có cast ở runtime”: cast runtime có thể kiểm tra class, nhưng không chứng minh phần tử lồng nhau đúng generic type arguments.

## 12. Heap pollution: khi nào mối lo ngại thành lỗi thật?

Xét một phương thức varargs sai:

~~~java
static void pollute(List<String>... lists) {
    Object[] alias = lists;
    alias[0] = List.of(42);

    String text = lists[0].get(0);
    System.out.println(text);
}
~~~

Đây là **mảnh code minh họa**, không phải đoạn đã ghép vào chương trình hoàn chỉnh. Nếu gọi `pollute(List.of("safe"))`, có thể xảy ra:

1. Compiler tạo array có runtime component `List` vì `List<String>` không reifiable.
2. Method nhận array mà kiểu tĩnh là `List<String>[]`.
3. Một alias `Object[]` trỏ đến cùng array.
4. Lệnh gán `List<Integer>` vào phần tử array được JVM chấp nhận do runtime chỉ kiểm tra đó là List.
5. Khi gọi `get(0)` rồi dùng như String, JVM có thể ném `ClassCastException`.

Điểm 4 giải thích vì sao lỗi không nhất thiết là ArrayStoreException tại chỗ gán: runtime component là List, và đối tượng được gán vẫn là một List.

**Nguyên nhân gốc** là thao tác không an toàn qua alias/raw hoặc cách phơi array. Hai warning liên quan declaration/call-site sẽ chỉ ra rủi ro chung, nhưng không chỉ có warning là chứng minh chắc chắn đã xảy ra heap pollution.

### Unsafe varargs không có nghĩa sai trong mọi lời gọi

Một phương thức chỉ đọc các `List<String>` và không phơi array có thể hoạt động an toàn dù kiểu component không reifiable. Nếu tác giả đủ điều kiện và chứng minh an toàn, `@SafeVarargs` được dùng để tránh cảnh báo không cần thiết.

Ngược lại, ngay cả phương thức được gắn `@SafeVarargs` cũng có thể gây lỗi nếu làm lộ hoặc sửa mảng. Annotation không thay đổi hành vi JVM.

## 13. Sửa rawtypes như thế nào?

Với:

~~~java
List raw = new ArrayList();
~~~

chọn thay thế theo hợp đồng:

~~~java
List<String> strings = new ArrayList<>(); // Biết mọi phần tử là String.
List<?> unknown = strings;                // Chưa biết loại khi chỉ đọc.
List<Object> mixed = new ArrayList<>();   // Chủ động cho nhiều loại Object.
~~~

Đừng coi ba kiểu đó tương đương:
- `List<String>` khóa kiểu phần tử là String trong thao tác qua API generic.
- `List<?>` biết tồn tại một kiểu phần tử chưa xác định; compiler chặn thêm tùy ý.
- `List<Object>` chấp nhận mọi Object nhưng **không phải supertype của `List<String>`** vì generic invariance.

Nếu data thật sự đến từ legacy raw API, có thể nhận tạm dạng `List<?>` và duyệt kiểm tra:

~~~java
static List<String> validatedStrings(List<?> input) {
    List<String> result = new ArrayList<>();

    for (Object value : input) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("String required");
        }
        result.add(text);
    }

    return List.copyOf(result);
}
~~~

Đoạn này cần import `java.util.List` và `java.util.ArrayList`, dùng Java 17 pattern matching. Nó từ chối null và tạo kết quả không cho sửa đổi bằng API thông thường. Mục tiêu là **khôi phục một bất biến đã kiểm chứng bằng dữ liệu**, thay vì tự gắn tham số `String` bằng unchecked cast.

## 14. Sửa unchecked mà không phải che warning

Nếu thấy:

~~~java
@SuppressWarnings("unchecked")
List<String> strings = (List<String>) unknown;
~~~

hãy hỏi:
1. Ai tạo `unknown` và cung cấp hợp đồng nào?
2. Runtime có thể kiểm tra top-level object là List không?
3. Có thể duyệt từng phần tử như Object rồi xác nhận String không?
4. Có thể đổi API nguồn để trả ngay `List<String>` không?
5. Có cần defensive copy để ngăn raw alias về sau thay đổi dữ liệu không?

Một cách an toàn ở ranh giới bất kỳ Object:

~~~java
static List<String> checkedCopy(Object source) {
    if (!(source instanceof List<?> unknown)) {
        throw new IllegalArgumentException("List required");
    }

    List<String> result = new ArrayList<>();
    for (Object value : unknown) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("String required");
        }
        result.add(s);
    }

    return List.copyOf(result);
}
~~~

Không cần suppress warning. Dùng `instanceof List<?>` chỉ xác nhận đây là List; chính vòng lặp `instanceof String` mới kiểm tra phần tử.

Đối với cast unchecked **không thể loại bỏ** trong một thư viện generic thấp tầng, giữ annotation thật hẹp và ghi rõ **bất biến bảo đảm cast**, giới hạn các đường ghi, và thử nghiệm các trường hợp sai kiểu. Không nên tắt cảnh báo toàn dự án chỉ để làm compile output sạch.

## 15. Sửa cảnh báo varargs như thế nào?

### 15.1. Nếu API không cần biến số đối số, bỏ varargs

Thay:

~~~java
static void process(List<String>... groups) {
    // ...
}
~~~

bằng:

~~~java
static void process(List<List<String>> groups) {
    // ...
}
~~~

Đây là hướng hợp lý nếu caller thường có collection sẵn. Nó bỏ yêu cầu tạo `List<String>[]` backing array ở call site.

### 15.2. Nếu cần varargs, không sửa hay phơi array

Mẫu có thể an toàn:

~~~java
@SafeVarargs
static <T> List<T> copyValues(T... values) {
    List<T> result = new ArrayList<>();

    for (T value : values) {
        result.add(value);
    }

    return List.copyOf(result);
}
~~~

Body chỉ đọc phần tử, không trả `values`, không gán sai kiểu vào array và không truyền mảng cho API có thể giữ alias. `List.copyOf` không nhận null và là shallow copy; đây là giới hạn riêng của ví dụ.

### 15.3. Nếu compiler báo [varargs] ở return hoặc forward, xem lại ownership

~~~java
@SafeVarargs
static <T> T[] returnBackingArray(T... values) {
    return values; // [varargs] có thể cảnh báo.
}
~~~

Không nên dùng thêm `@SuppressWarnings("varargs")` rồi coi đã khắc phục an toàn. Cần đổi API để tránh phơi array hoặc chứng minh điều kiện đặc biệt khiến alias không thể bị dùng sai. Nếu thư viện bắt buộc trả array, phải xử lý runtime component type và contract của caller.

### 15.4. Không copy nhãn annotation một cách máy móc

`@SafeVarargs` không đặt tùy ý trên một instance method có thể override, và không nên dùng trên method không phải varargs. Nó là hợp đồng cho tác giả, không phải “mẹo” tắt lint.

Các cảnh báo dù bị suppress vẫn có thể phản ánh một bug runtime. Test an toàn phải có trường hợp dữ liệu sai, không chỉ test happy path.

## 16. Tại sao Arrays.asList(values) cũng cần suy nghĩ?

`Arrays.asList(values)` tạo một **fixed-size List backed by array**. Nó không tự sao chép mảng backing:

~~~java
String[] source = {"A", "B"};
List<String> view = java.util.Arrays.asList(source);

view.set(0, "changed");
System.out.println(source[0]); // changed
~~~

Nếu một method có `@SafeVarargs` truyền trực tiếp mảng varargs tới `Arrays.asList` hoặc một method khác, compiler khi bật `-Xlint:varargs` **có thể cảnh báo** ở vị trí forward đó. Một lời gọi method bên ngoài có thể giữ lại hoặc tạo view của chính array, nên việc chuyển tiếp cần được chứng minh an toàn xuyên suốt.

Một mẫu rõ ý định hơn để tránh làm lộ mảng trong `@SafeVarargs` là **tạo collection riêng bằng vòng lặp qua T**, sau đó `List.copyOf` nếu cần kết quả không cho sửa đổi. Compiler không phải giả định một lời gọi ngoài kia có giữ hoặc thay đổi alias của `values` không.

**Quan trọng:** sao chép collection chỉ là **shallow copy**; nếu phần tử T chính nó mutable hoặc là một raw/unchecked List không đúng generic type, chỉ copy danh sách bên ngoài không xác minh mọi phần tử lồng nhau.

## 17. Khi nào dùng @SuppressWarnings?

`@SuppressWarnings` cho phép chỉ rõ category ở một vùng code, ví dụ:

~~~java
@SuppressWarnings("unchecked")
static <T> T controlledCast(Object value) {
    return (T) value;
}
~~~

Đoạn code trên chỉ để minh họa cú pháp, **không tự an toàn**: caller có thể yêu cầu T không tương thích và gặp ClassCastException khi sử dụng kết quả. Không nên dùng y nguyên cho ứng dụng mà không có bằng chứng về kiểu runtime.

Các category thường được truyền dưới dạng string:
- `"rawtypes"` dành cho raw-type diagnostics.
- `"unchecked"` dành cho unchecked operations.
- `"varargs"` dành cho các varargs warnings tương ứng khi compiler hỗ trợ.

Nhưng suppression nên là **biện pháp cuối ở đoạn code nhỏ đã được chứng minh an toàn**. Nên thêm bình luận nêu bất biến:
- Type information được xác thực ở đâu?
- Ai có quyền ghi dữ liệu?
- Tại sao cast này không thể nhận object sai kiểu?
- Tại sao mảng varargs không bị sửa/làm lộ?
- Liệu invariant còn đúng nếu API được kế thừa hay gọi qua raw type?

Suppress cả lớp hoặc cả build sẽ che những warning mới xuất hiện về sau; thực tế tốt là giảm phạm vi đến method, dòng hoặc một biến cần thiết.

## 18. Bảng quyết định nhanh theo warning

| Thông điệp thường gặp | Nhóm | Nguyên nhân gần | Cách xử lý ưu tiên |
| --- | --- | --- | --- |
| `found raw type: List` | `rawtypes` | Dùng tên generic không có type argument. | Chọn `List<T>` hoặc `List<?>` phù hợp. |
| `unchecked call to add(E)` | `unchecked` | Gọi method phụ thuộc E qua raw receiver. | Loại raw alias và dùng generic typed reference. |
| `unchecked conversion` | `unchecked` | Raw List → List<String> không kiểm tra phần tử. | Đổi API nguồn hoặc kiểm tra/copy dữ liệu. |
| `unchecked cast` | `unchecked` | Cast về generic type không reifiable. | Validator hoặc invariant rõ ràng. |
| `Possible heap pollution from parameterized vararg type` | `unchecked` | Khai báo varargs với non-reifiable component. | Xem lại API, dùng @SafeVarargs khi thật sự an toàn. |
| `unchecked generic array creation for varargs parameter` | `unchecked` | Caller đóng gói đối số thành array generic không reifiable. | Dùng API an toàn có @SafeVarargs hoặc collection thay thế. |
| `Varargs method could cause heap pollution ...` | `varargs` | Trong body, mảng varargs có thể bị phơi/chuyển tiếp sai. | Sửa body để tránh unsafe exposure hoặc chứng minh contract. |

Thông điệp cụ thể có thể khác giữa các bản javac. Đừng chỉ tìm từ khóa “varargs” trong văn bản rồi mặc định đó là category `[varargs]`; hãy đọc phần nhãn và vị trí được compiler đánh dấu.

## 19. Checklist review mã generic

1. **Bật đủ lint categories:** `-Xlint:rawtypes,unchecked,varargs` ở mức source release dự án.
2. **Đọc nhãn warning**, không chỉ phần nội dung thông báo.
3. **Tìm kiểu gốc generic:** List<E>, Map<K,V>, Optional<T>, hay một class do dự án định nghĩa.
4. **Xem raw type có do thiết kế không:** ưu tiên wildcard khi không biết type argument.
5. **Xem unchecked chuyển đổi ở đâu:** khai báo, gọi method, ép kiểu, hay varargs call site.
6. **Kiểm tra runtime invariant:** dữ liệu được tạo từ đâu, có alias raw không, có kiểm tra phần tử không.
7. **Nếu varargs:** xác định backing array ở caller, fixed/variable arity, component runtime sau erasure.
8. **Kiểm tra body method được đánh @SafeVarargs:** có trả mảng, lưu vào field, chuyển mảng cho API khác, dùng Object[] alias hoặc array writes không.
9. **Ưu tiên sửa API:** parameterized collections, validator, defensive copy; suppress annotation chỉ khi có chứng minh cụ thể.
10. **Thử trường hợp âm tính:** Integer trong List<String> qua raw alias, generic varargs bị phơi, sai kiểu khi đọc.
11. **Tách compiler diagnostics khỏi stdout:** warnings là dữ liệu phát sinh khi build, không phải kết quả chương trình.
12. **Đừng dùng -Werror thay việc sửa:** nó buộc build thất bại khi có warnings, giúp đảm bảo chất lượng, nhưng không tự làm code an toàn.

Các bước này phù hợp khi chuyển legacy Java code sang generics, thiết kế thư viện API công khai hoặc review việc sử dụng `@SafeVarargs` trong codebase.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** `List raw;` có cảnh báo gì khi bật `-Xlint:rawtypes` mà không có thao tác thêm phần tử?

**Đáp án:** `[rawtypes]` vì tên List<E> bị dùng raw. Không bắt buộc có `[unchecked]` nếu chưa làm thao tác nào cần generic type checking.

**Câu 2.** `List<String> values = raw;` cảnh báo gì?

**Đáp án:** `[unchecked] unchecked conversion` vì compiler không chứng minh được raw collection thực sự chỉ chứa String.

**Câu 3.** Một cast `(List<String>) unknown` từ Object có cần raw List variable không mới cảnh báo?

**Đáp án:** Không. `[unchecked] unchecked cast` có thể xuất hiện vì cast đến parameterized type không reifiable, ngay cả khi source là Object.

**Câu 4.** `static void f(List<String>... xs)` và lời gọi `f(List.of("A"))` thường cảnh báo category nào?

**Đáp án:** Khi bật `-Xlint:unchecked`, javac có thể báo `[unchecked]` tại declaration (“Possible heap pollution...”) và call site (“unchecked generic array creation...”); chữ vararg trong message không làm category đổi thành `[varargs]`.

**Câu 5.** `@SafeVarargs static <T> T[] f(T... xs) { return xs; }` có chắc không warning?

**Đáp án:** Không. `-Xlint:varargs` có thể cảnh báo ở `return xs` vì phương thức làm lộ mảng varargs dù đã gắn annotation. Annotation không chứng minh code an toàn.

**Câu 6.** Nếu code có cả rawtypes và unchecked warning, chỉ suppress "rawtypes" đã giải quyết an toàn chưa?

**Đáp án:** Chưa. Raw declaration và unchecked operations phản ánh các vấn đề riêng. Suppress chỉ che diagnostic, không sửa dữ liệu hay invariant.

**Câu 7.** Vì sao `List<String>...` có nguy cơ heap pollution?

**Đáp án:** Tham số là mảng List<String>[] ở source nhưng component runtime sau erasure thường là List. Qua Object[] alias, một List<Integer> vẫn có thể được lưu vào mảng; đọc phần tử đó như String có thể ném ClassCastException.

**Câu 8.** `@SafeVarargs` có tự động kiểm tra từng phần tử của List<String> không?

**Đáp án:** Không. Nó không tạo cơ chế runtime validation và không thay đổi type erasure. Người thiết kế vẫn cần bảo đảm body không gây thao tác array unsafe.

**Câu 9.** Tại sao `List.copyOf(Arrays.asList(values))` trong một method @SafeVarargs vẫn có thể bị `-Xlint:varargs` cảnh báo?

**Đáp án:** Vì method đã chuyển trực tiếp backing array `values` cho API khác. Dù kết quả cuối có thể là bản sao, compiler cảnh báo tại đường forward có thể phơi hoặc sử dụng mảng. Có thể tự duyệt các phần tử vào một collection mới để thể hiện rõ hơn rằng array reference không thoát ra ngoài.

**Câu 10.** Có warning nhưng chương trình vẫn in đúng kết quả có nghĩa warning là sai?

**Đáp án:** Không. Warning thể hiện compiler không chứng minh đầy đủ tính an toàn ở mọi trường hợp. Dữ liệu hiện tại đúng kiểu vẫn có thể chạy, nhưng code khác dùng raw alias/unchecked cast có thể phá invariant.

## 21. Kết luận

**`rawtypes` nói về cách sử dụng một tên kiểu generic không kèm tham số kiểu. `unchecked` nói về thao tác không thể chứng minh đầy đủ an toàn generic. `varargs` nói về những cách sử dụng mảng varargs có thể không an toàn, đặc biệt khi một method được quảng bá bằng `@SafeVarargs` lại làm lộ hoặc chuyển tiếp mảng.**

Điểm phân biệt thực hành quan trọng nhất: **cảnh báo `List<String>...` ở declaration và varargs call site thường mang nhãn `[unchecked]`, còn `[varargs]` có thể gắn với thao tác sử dụng mảng trong body**. Cùng một method có thể làm phát sinh cả hai nhóm.

Hãy dùng compiler warnings như **đầu mối kiểm tra hợp đồng kiểu**, sửa nguyên nhân ở chỗ sinh dữ liệu/API, giữ `@SafeVarargs` và `@SuppressWarnings` ở phạm vi được chứng minh an toàn, và nhớ rằng việc một chương trình chạy đúng trên dữ liệu hiện tại không làm những thao tác unchecked trở thành an toàn cho mọi đầu vào.


</details>

- [Quay lại đầu trang](#back-to-top)
