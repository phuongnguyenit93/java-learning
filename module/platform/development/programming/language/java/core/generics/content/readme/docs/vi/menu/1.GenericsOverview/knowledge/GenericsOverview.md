<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Generics là gì và bài toán tái sử dụng mã an toàn kiểu](#generics-overview-001)
- [Vì sao dùng Object và ép kiểu dễ để lỗi đến lúc chạy](#generics-overview-002)
- [Quan hệ giữa kiểm tra kiểu lúc biên dịch và dữ liệu lúc chạy](#generics-overview-003)
- [Tham số kiểu, đối số kiểu, biến kiểu và kiểu tham số hóa](#generics-overview-004)
- [Generics kết hợp với lớp, giao diện, phương thức và collection](#generics-overview-005)
- [Phân biệt tính tổng quát về kiểu với kế thừa và đa hình](#generics-overview-006)
- [Lợi ích, giới hạn và trường hợp không cần generics](#generics-overview-007)
- [Kiến thức nền và trình tự học generics trong Java Core](#generics-overview-008)

## <a id="generics-overview-001">Generics là gì và bài toán tái sử dụng mã an toàn kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Java Generics: tái sử dụng mã an toàn kiểu

Generics (kiểu tổng quát) cho phép lớp, interface và phương thức nhận **kiểu dữ liệu làm tham số**. Nhờ đó, cùng một thuật toán hoặc cấu trúc dữ liệu có thể làm việc với nhiều kiểu đối tượng, trong khi trình biên dịch vẫn kiểm tra việc truyền và nhận giá trị có đúng kiểu hay không. Đây là nền tảng của các API quen thuộc như `List<String>`, `Set<Long>` và `Map<String, Integer>`.

Sau bài này, bạn có thể giải thích vì sao mã dùng `Object` dễ gây lỗi ép kiểu, đọc và viết khai báo `<T>`, phân biệt lỗi được phát hiện khi biên dịch với lỗi chỉ xảy ra lúc chạy, và nhận biết các giới hạn cơ bản của generics.

## 1. Bài toán trước khi có generics: tái sử dụng bằng `Object`

Giả sử ứng dụng cần một hộp giữ một giá trị. Nếu viết riêng `StringBox`, `IntegerBox`, `UserBox`..., ta lặp lại cấu trúc và logic gần như giống hệt nhau. Cách làm chung là lưu giá trị dưới kiểu `Object`, vì mọi lớp tham chiếu trong Java đều kế thừa `Object`.

```java
final class LegacyBox {
    private final Object value;

    LegacyBox(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}
```

Cách sử dụng:

```java
LegacyBox box = new LegacyBox("Java");
String language = (String) box.get(); // Người gọi phải ép kiểu.

LegacyBox another = new LegacyBox(42);
String errorLater = (String) another.get(); // Biên dịch được; lỗi khi chạy.
```

Dòng cuối biên dịch được vì trình biên dịch chỉ biết `get()` trả về `Object` và phép ép sang `String` là một phép ép kiểu tham chiếu hợp lệ về cú pháp. Khi chạy, đối tượng thực tế là `Integer`, nên Java ném `ClassCastException`.

Ở đây có ba vấn đề:

1. **Mất thông tin kiểu khi sử dụng API:** chữ ký `Object get()` không cho người gọi biết chắc hộp đang chứa gì.
2. **Phải ép kiểu thủ công:** mã gọi dài hơn và dễ nhầm; không được trình biên dịch bảo đảm giá trị bên trong đúng kiểu mong muốn.
3. **Lỗi xuất hiện muộn:** việc đưa một kiểu vào hộp rồi lấy ra dưới kiểu khác có thể chỉ thất bại khi nhánh code đó được chạy.

`Object` giúp *tái sử dụng phần lưu trữ*, nhưng không mô tả được ràng buộc “hộp này chỉ lưu chuỗi” ngay trong hợp đồng của kiểu dữ liệu.

## 2. Generics giải quyết điều gì?

Thay kiểu cố định `Object` bằng một **tham số kiểu** (type parameter), ví dụ `T`:

```java
final class Box<T> {
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
```

Trong khai báo `class Box<T>`, `T` là một tên đại diện cho kiểu tham chiếu sẽ được chỉ định khi sử dụng lớp. `Box<String>` và `Box<Integer>` là hai cách *tham số hóa* cùng định nghĩa `Box<T>`:

```java
Box<String> text = new Box<>("hello");
text.set("Java");
String value = text.get(); // Không cần ép kiểu tường minh.

Box<Integer> number = new Box<>(42);
int result = number.get(); // Integer được tự động unbox thành int.

// text.set(42); // KHÔNG biên dịch: yêu cầu String, nhưng nhận int.
```

**Phân biệt ba vị trí cú pháp:**

| Cú pháp | Ý nghĩa |
| --- | --- |
| `class Box<T>` | Khai báo lớp tổng quát; `T` là tham số kiểu. |
| `Box<String>` | Sử dụng lớp với đối số kiểu (type argument) là `String`. |
| `new Box<>(...)` | Toán tử kim cương `<>` yêu cầu trình biên dịch suy luận đối số kiểu từ ngữ cảnh. |

Tên `T` chỉ là quy ước, có thể đặt tên khác rõ nghĩa hơn. Các tên thường gặp gồm `T` (Type), `E` (Element), `K` (Key), `V` (Value). Chúng không phải từ khóa Java.

Điểm cốt lõi là **mối liên hệ kiểu dữ liệu xuyên suốt API**: đối số truyền vào constructor và `set(T)` phải phù hợp với `T`, còn `get()` trả về chính `T`. Trình biên dịch kiểm tra mối liên hệ đó khi gọi phương thức. Một phiên bản lớp duy nhất phục vụ nhiều kiểu mà vẫn cho phản hồi lỗi sớm.

## 3. Ví dụ hoàn chỉnh chạy với Java 17+

Tạo tệp `Main.java` với nội dung sau. Chương trình đặt cách dùng `Object` cạnh `Box<T>` và sử dụng thêm một phương thức tổng quát.

```java
import java.util.List;

public class Main {
    static final class LegacyBox {
        private final Object value;

        LegacyBox(Object value) {
            this.value = value;
        }

        Object get() {
            return value;
        }
    }

    static final class Box<T> {
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

    static <T> T first(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Danh sách rỗng");
        }
        return values.get(0);
    }

    public static void main(String[] args) {
        LegacyBox legacy = new LegacyBox("Java");
        String oldText = (String) legacy.get();
        System.out.println("Legacy: " + oldText);

        legacy = new LegacyBox(42);
        // String errorLater = (String) legacy.get(); // ClassCastException khi chạy.

        Box<String> text = new Box<>("Java");
        text.set("Generics");
        String word = text.get();
        System.out.println("Box<String>: " + word);

        Box<Integer> number = new Box<>(42);
        System.out.println("Box<Integer>: " + number.get());

        System.out.println("first String: " + first(List.of("A", "B")));
        System.out.println("first Integer: " + first(List.of(10, 20)));
        // text.set(42); // Lỗi biên dịch: Integer không phù hợp String.
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac -encoding UTF-8 Main.java
java Main
```

Kết quả chuẩn:

```text
Legacy: Java
Box<String>: Generics
Box<Integer>: 42
first String: A
first Integer: 10
```

Các dòng có chú thích về lỗi được cố ý để ở trạng thái comment. Nếu bỏ `//` trước `text.set(42)`, ví dụ sẽ **không biên dịch**: đó chính là cơ chế phát hiện sai kiểu sớm. Nếu bỏ `//` trước phép ép `legacy.get()` sang `String`, chương trình có thể ném `ClassCastException` tại dòng ấy, vì `legacy` đang chứa `Integer`.

## 4. Phương thức tổng quát: tái sử dụng thuật toán

Generics không chỉ áp dụng cho lớp. Phương thức cũng có thể **tự khai báo** tham số kiểu:

```java
static <T> T first(List<T> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("Danh sách rỗng");
    }
    return values.get(0);
}
```

`<T>` **đứng trước kiểu trả về** `T`: đó là phần khai báo tham số kiểu riêng của phương thức. Tham số `List<T>` bảo đảm kiểu của phần tử đầu vào và kiểu trả về liên hệ với nhau.

Ví dụ:

```java
String name = first(List.of("An", "Bình")); // T được suy luận là String.
Integer id = first(List.of(101, 102));     // T được suy luận là Integer.
```

Chỉ một thuật toán lấy phần tử đầu tiên được dùng cho cả chuỗi lẫn số nguyên. Trình biên dịch suy luận `T` dựa trên kiểu đối số và ngữ cảnh lời gọi; đôi khi bạn cũng có thể viết đối số kiểu tường minh, chẳng hạn `Main.<String>first(List.of("An"))`.

Lưu ý: generics **không tự kiểm tra dữ liệu rỗng**. Kiểm tra `values.isEmpty()` ở trên là quy tắc nghiệp vụ của phương thức để tránh việc `get(0)` gây `IndexOutOfBoundsException`. An toàn kiểu và hợp lệ của giá trị là hai khía cạnh khác nhau.

## 5. Vì sao lỗi sai kiểu được phát hiện sớm?

Hãy so sánh một danh sách dùng `Object` với danh sách có kiểu phần tử xác định:

```java
List<Object> mixed = new java.util.ArrayList<>();
mixed.add("Java");
mixed.add(100); // Hợp lệ: cả String và Integer đều là Object.

List<String> languages = new java.util.ArrayList<>();
languages.add("Java");
// languages.add(100); // Không biên dịch: phần tử phải là String.

String firstLanguage = languages.get(0); // Không cần cast.
```

Bộ biên dịch Java kiểm tra các thao tác như `add`, `set`, kết quả `get` theo **đối số kiểu trong biểu thức**. Những lời gọi không thỏa hợp đồng bị từ chối khi biên dịch, trước khi chương trình được chạy. Điều này rất hữu ích khi đổi API hoặc tái cấu trúc mã: các chỗ gọi không còn phù hợp thường hiện ra ngay dưới dạng lỗi biên dịch.

| Tiêu chí | API chỉ dùng `Object` | API dùng generics |
| --- | --- | --- |
| Tái sử dụng một cấu trúc cho nhiều kiểu | Có | Có |
| Diễn đạt kiểu giá trị qua chữ ký API | Mơ hồ: thường chỉ là `Object` | Rõ: `Box<String>`, `List<Integer>` |
| Đọc giá trị về kiểu cụ thể | Thường phải cast | Thường không cần cast tường minh |
| Ngăn truyền nhầm kiểu ở lời gọi thông thường | Không | Có, khi biên dịch |
| Loại bỏ tuyệt đối `ClassCastException` | Không | **Không**; mã raw/unchecked hoặc mã cũ vẫn có thể gây lỗi |

Generics cũng giúp IDE gợi ý phương thức và tái cấu trúc chính xác hơn vì thông tin kiểu được giữ trong API.

## 6. Các tình huống nên dùng generics

**Cấu trúc dữ liệu:** khi danh sách chỉ nên chứa tên người dùng, dùng `List<String>` thay vì `List<Object>`. Với từ điển số lượng theo mã sản phẩm, dùng `Map<String, Integer>` để mô tả rõ kiểu khóa và giá trị:

```java
java.util.Map<String, Integer> quantities = new java.util.HashMap<>();
quantities.put("SKU-01", 5);
Integer count = quantities.get("SKU-01");
```

**Thành phần dùng lại:** một `Box<T>` có thể giữ `String`, `Integer` hoặc kiểu miền nghiệp vụ như `Order`, mà không sao chép một lớp hộp mới cho mỗi loại. Nếu có hai kiểu liên hệ độc lập, một lớp có thể khai báo nhiều tham số kiểu, ví dụ `Pair<K, V>`.

**Tiện ích xử lý dữ liệu:** phương thức như `first(List<T>)` tái sử dụng thao tác mà vẫn giữ kiểu trả về cho từng lời gọi. Đây là lý do thư viện Java Collections dùng generics rộng rãi: hợp đồng kiểu mạnh trong khi không cần nhân bản thuật toán cho từng kiểu phần tử.

**Khi nào chưa cần dùng:** nếu lớp chỉ xử lý duy nhất một kiểu với quy tắc riêng, khai báo cụ thể như `UserService` thường dễ hiểu hơn. Tránh thêm `<T>` chỉ để tạo cảm giác “tổng quát”; tham số kiểu nên thể hiện **mối quan hệ kiểu thật sự** giữa tham số đầu vào, kết quả và trạng thái.

## 7. Những giới hạn cần hiểu từ đầu

### 7.1. Generics không phải kiểm tra kiểu động toàn diện

Java triển khai generics chủ yếu bằng **type erasure** (xóa kiểu): trình biên dịch kiểm tra ràng buộc kiểu ở nguồn rồi xóa phần lớn thông tin đối số kiểu khỏi mã thực thi, thêm các phép ép kiểu cần thiết tại một số điểm sử dụng. Vì vậy, `List<String>` và `List<Integer>` dùng cùng một lớp triển khai `ArrayList` ở thời gian chạy; bạn không thể viết `value instanceof List<String>` để kiểm tra kiểu phần tử chính xác.

Việc kiểm tra lúc biên dịch vẫn có ích rất lớn, nhưng **không bảo đảm mọi đường đi đều an toàn** nếu dữ liệu đi qua API cũ, phép ép kiểu không kiểm chứng hoặc loại raw.

### 7.2. Tránh kiểu raw và cảnh báo `unchecked`

Kiểu `List` không có đối số kiểu là *raw type*, chủ yếu dành cho tương thích với mã Java cũ:

```java
java.util.List<String> names = new java.util.ArrayList<>();
java.util.List raw = names; // Raw type: mất bảo đảm kiểu tại biến này.
raw.add(123);              // Có cảnh báo unchecked; có thể làm bẩn danh sách.
// String name = names.get(0); // Có thể ClassCastException nếu đọc trúng Integer.
```

Đoạn trên chỉ để chỉ ra rủi ro, **không phải mẫu sử dụng được khuyến nghị**. Ưu tiên khai báo có đủ đối số kiểu (`List<String>`) và không bỏ qua cảnh báo `unchecked` một cách máy móc. Lỗi `ClassCastException` vẫn có thể xảy ra khi đọc phần tử bị đưa vào qua `raw`.

### 7.3. Đối số kiểu phải là kiểu tham chiếu

`Box<int>` **không hợp lệ**; dùng `Box<Integer>`. Java hỗ trợ boxing/unboxing tự động khi chuyển giữa `int` và `Integer`. Tương tự, `List<double>` không hợp lệ, còn `List<Double>` hợp lệ.

### 7.4. Các kiểu tổng quát không tự có quan hệ kế thừa

Dù `Integer` là một lớp con của `Number`, `List<Integer>` **không phải** lớp con của `List<Number>`. Nếu cho phép gán như vậy, người dùng có thể thêm `Double` vào một danh sách thực chất chỉ chứa `Integer`. Generics mặc định bất biến (invariant). Wildcard như `? extends Number` hoặc `? super Integer` giải quyết một số nhu cầu linh hoạt hơn, nhưng thuộc bước học về ràng buộc kiểu và wildcard sau khi đã nắm phần cơ bản.

### 7.5. Generic type không tự bảo đảm mọi ràng buộc nghiệp vụ

`Box<String>` vẫn có thể giữ `null`; `List<Integer>` vẫn có thể chứa số âm. Generics đảm bảo sự phù hợp **về loại dữ liệu** trong các lời gọi được kiểm tra, không tự xác minh “chuỗi không rỗng”, “số dương” hay “đã được xác thực”. Các yêu cầu đó phải được xử lý bằng kiểm tra giá trị riêng.

## 8. Lỗi thường gặp và cách tránh

- **Dùng `Object` dù đã biết kiểu:** kết quả buộc nơi gọi cast và làm suy yếu hợp đồng API. Hãy đưa tham số kiểu vào lớp hoặc phương thức khi có mối quan hệ kiểu cần giữ.
- **Khai báo raw type** (`List items`): dễ gây cảnh báo `unchecked` và lỗi lúc chạy. Dùng `List<Item>` hoặc kiểu phần tử đúng yêu cầu.
- **Nghĩ generics “đổi kiểu đối tượng” ở thời gian chạy:** `T` là công cụ mô tả và kiểm tra kiểu trong mã nguồn; không phải cơ chế biến một `Integer` thành `String`.
- **Viết sai vị trí `<T>` của phương thức:** phải khai báo `static <T> T first(...)`, không chỉ viết `static T first(...)` nếu `T` chưa được khai báo trong phạm vi hợp lệ.
- **Nhầm an toàn kiểu với kiểm tra dữ liệu:** `List<String>` không ngăn chuỗi rỗng, `null` hoặc dữ liệu từ bên ngoài không đạt yêu cầu.
- **Lạm dụng ép kiểu unchecked để bỏ qua trình biên dịch:** chỉ ép kiểu khi có căn cứ rõ ràng về kiểu thực và có thể kiểm chứng ranh giới dữ liệu.

## 9. Tự kiểm tra

**Tình huống 1:** Hai lời gọi `new Box<String>("A")` và `new Box<Integer>(1)` có yêu cầu tạo hai định nghĩa lớp `Box` khác nhau không?

**Đáp án:** Không. Cùng một `Box<T>` được tái sử dụng với hai đối số kiểu khác nhau; trình biên dịch kiểm tra riêng các lời gọi theo kiểu đã chọn.

**Tình huống 2:** Vì sao `String s = (String) legacy.get()` có thể thất bại khi chạy, còn `Box<String> b; b.set(100)` bị từ chối khi biên dịch?

**Đáp án:** `LegacyBox.get()` khai báo trả `Object`, nên ép kiểu có thể được chấp nhận dù giá trị thực không phải `String`. Với `Box<String>`, tham số của `set` được xác định là `String`, nên truyền số nguyên không phù hợp với chữ ký phương thức đã tham số hóa.

**Tình huống 3:** Có thể gán `List<Integer>` cho biến `List<Number>` vì `Integer extends Number` không?

**Đáp án:** Không. Hai kiểu danh sách đó không có quan hệ subtype theo cách ấy; cơ chế bất biến giúp ngăn việc thêm loại số không phù hợp vào danh sách.

## Kết luận

Generics giải quyết một vấn đề thiết kế rất thực tế: **viết một cấu trúc hoặc thuật toán một lần và tái sử dụng cho nhiều kiểu mà vẫn giữ hợp đồng kiểu rõ ràng**. Cú pháp như `Box<T>`, `List<String>` và `static <T> T first(List<T>)` giúp đưa nhiều lỗi từ lúc chạy sang lúc biên dịch, giảm nhu cầu ép kiểu và khiến API dễ đọc hơn. Hãy bắt đầu bằng việc chỉ dùng kiểu tổng quát khi có quan hệ kiểu cần biểu diễn, tránh raw type và luôn kiểm tra ràng buộc giá trị độc lập với an toàn kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-002">Vì sao dùng Object và ép kiểu dễ để lỗi đến lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao dùng `Object` và ép kiểu dễ để lỗi đến lúc chạy?

Trong Java, `Object` là lớp cha của mọi **kiểu tham chiếu**. Vì thế, một biến `Object` có thể trỏ tới một đối tượng `String`, `Integer`, `Customer` hoặc nhiều loại đối tượng khác. Điều này có vẻ linh hoạt, nhưng nếu chương trình phải **ép kiểu xuống** (`downcast`) liên tục để dùng dữ liệu, sự linh hoạt ấy có thể làm mất thông tin kiểu tại nơi biên dịch và đẩy lỗi sang lúc chạy.

Bài này giải thích chính xác vì sao `Object` cùng ép kiểu có rủi ro, `ClassCastException` xuất hiện ở đâu, Java Generics giảm rủi ro đó thế nào và những trường hợp nào vẫn cần kiểm tra kiểu.

## 1. `Object` cho phép giữ nhiều loại đối tượng, nhưng không nói rõ loại mong đợi

Ví dụ:

```java
Object value = "Java";          // Đối tượng thực tế là String
value = Integer.valueOf(42);   // Đối tượng thực tế là Integer
```

Cả hai phép gán đều hợp lệ: `String` và `Integer` là các kiểu tham chiếu, nên đều có thể gán cho `Object`. Tuy nhiên, **kiểu tĩnh của biến** `value` vẫn là `Object`. Trình biên dịch chỉ cho phép gọi trực tiếp những phương thức có trong API của `Object`, chẳng hạn `toString()`; nó không thể suy luận rằng `value` chắc chắn có phương thức `toUpperCase()` của `String`.

Phải phân biệt hai khái niệm:

| Khái niệm | Trong `Object value = "Java";` | Ý nghĩa |
|---|---|---|
| Kiểu tĩnh (`compile-time type`) | `Object` | Trình biên dịch dựa vào đây để kiểm tra thao tác hợp lệ. |
| Kiểu thực tế lúc chạy (`runtime type`) | `String` | JVM dựa vào đối tượng thực tế để kiểm tra ép kiểu và thực thi phương thức. |

Nếu chỉ nhìn vào kiểu `Object`, người đọc API không biết dữ liệu thuộc kiểu gì và thường phải tìm thêm tài liệu, kiểm tra điều kiện hoặc ép kiểu để dùng đúng.

> `Object` tự thân không gây lỗi. Rủi ro xuất hiện khi chương trình **giả định** một giá trị kiểu `Object` chắc chắn có kiểu cụ thể, nhưng giả định ấy không được bảo đảm.

## 2. Vì sao ép kiểu vẫn biên dịch được nhưng có thể lỗi khi chạy?

Xem phép ép kiểu:

```java
Object value = Integer.valueOf(42);
String text = (String) value;
```

Biểu thức `(String) value` là một phép ép kiểu tường minh (`explicit cast`). Vì kiểu tĩnh của `value` là `Object`, trình biên dịch cho phép yêu cầu kiểm tra nó có phải `String` hay không. **Cho phép biên dịch không đồng nghĩa với xác nhận ép kiểu sẽ thành công**.

Khi chạy, JVM kiểm tra đối tượng thực tế. `value` đang tham chiếu tới một `Integer`, không phải `String`, vì vậy phép ép kiểu ném `ClassCastException`. Nếu không được bắt, ngoại lệ làm luồng thực thi hiện tại kết thúc bất thường.

Cơ chế theo thứ tự:

1. Giá trị `Integer(42)` được gán vào biến có kiểu tĩnh `Object`.
2. Trình biên dịch thấy một phép chuyển từ `Object` xuống `String` có thể hợp lệ về mặt cú pháp và quan hệ kiểu.
3. Khi thực thi phép ép, JVM kiểm tra đối tượng được tham chiếu có tương thích với `String` hay không.
4. Đối tượng là `Integer`, nên kiểm tra thất bại và JVM ném `ClassCastException` ngay tại phép ép.

Không phải **mọi** phép ép sai đều đợi đến lúc chạy: nếu trình biên dịch đã biết hai kiểu không thể chuyển đổi hợp lệ, nó có thể báo lỗi ngay khi biên dịch. Vấn đề nổi bật ở đây là thông tin kiểu bị che bớt sau `Object` nên trình biên dịch chưa đủ cơ sở bác bỏ phép ép.

### Ví dụ hoàn chỉnh: lỗi chỉ xuất hiện với phần tử thứ hai

Lưu thành `ObjectCastDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ObjectCastDemo {
    public static void main(String[] args) {
        List<Object> values = new ArrayList<>();
        values.add("Java");
        values.add(42);

        for (Object value : values) {
            try {
                String word = (String) value;
                System.out.println(word.toUpperCase());
            } catch (ClassCastException ex) {
                System.out.println(
                    "Không thể ép " + value.getClass().getSimpleName() + " thành String"
                );
            }
        }
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 ObjectCastDemo.java
java ObjectCastDemo
```

`stdout`:

```text
JAVA
Không thể ép Integer thành String
```

`List<Object>` ở đây **vẫn là một kiểu generic**, nhưng tham số kiểu là `Object`. Do đó danh sách được phép nhận cả chuỗi lẫn số nguyên. Vòng lặp giả định tất cả phần tử là `String`. Phần tử đầu phù hợp nên chạy tốt; phần tử thứ hai không phù hợp nên ném ngoại lệ.

Điều nguy hiểm trong thực tế là lỗi có thể nằm rất xa nơi dữ liệu được thêm vào: một thành phần lưu giá trị `Object`, thành phần khác đọc ra và ép kiểu, trong khi bộ test chỉ tình cờ dùng dữ liệu cùng kiểu.

## 3. Ba nguyên nhân khiến lỗi bị trì hoãn

**Mất ràng buộc kiểu của hợp đồng dữ liệu.** Một API trả về `Object` không thể diễn đạt rõ với trình biên dịch rằng “giá trị này phải là `String`”. Người gọi phải dựa vào quy ước, tài liệu hoặc giả định.

**Ép kiểu tường minh chỉ là yêu cầu kiểm tra.** Cú pháp `(String) value` không biến một `Integer` thành chuỗi và không bảo đảm dữ liệu đầu vào đúng kiểu. Ép kiểu tham chiếu khác với chuyển đổi dữ liệu; muốn chuyển một số thành biểu diễn chuỗi, có thể dùng `String.valueOf(42)`, với ngữ nghĩa khác hẳn.

**Dữ liệu có thể thay đổi theo nhánh chạy.** Một nhánh thêm `String`, nhánh khác thêm `Integer`, hay một giá trị đến từ plugin, cache hoặc dữ liệu bên ngoài. Đường chạy thông thường có thể thành công rất lâu trước khi nhánh sai kiểu được kích hoạt.

Phân biệt các lỗi thường gặp:

| Tình huống | Kết quả |
|---|---|
| Ép một đối tượng `Integer` sang `String` thông qua biến `Object` | `ClassCastException` tại lúc chạy. |
| Ép `null` sang `String` | Bản thân phép ép cho kết quả `null`; gọi phương thức trên `null` mới gây `NullPointerException`. |
| Gọi `value.toUpperCase()` khi `value` có kiểu tĩnh `Object` | Lỗi biên dịch vì `Object` không khai báo phương thức đó. |
| Dùng `String.valueOf(value)` | Chuyển sang biểu diễn chuỗi; không phải phép ép đối tượng thành `String`. |

## 4. Generics đưa kiểm tra kiểu về thời điểm biên dịch

Thay vì chỉ mô tả một hộp chứa `Object`, ta đặt **tham số kiểu** `T` để mô tả dữ liệu được chứa và được trả ra:

```java
final class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

public class GenericBoxDemo {
    public static void main(String[] args) {
        Box<String> labelBox = new Box<>("Java");
        System.out.println(labelBox.get().toUpperCase());

        Box<Integer> yearBox = new Box<>(2026);
        System.out.println(yearBox.get() + 1);
    }
}
```

Lưu thành `GenericBoxDemo.java`, sau đó chạy:

```bash
javac -encoding UTF-8 GenericBoxDemo.java
java GenericBoxDemo
```

`stdout`:

```text
JAVA
2027
```

Trong `Box<String>`, trình biên dịch biết `get()` cho ra một `String` theo hợp đồng kiểu; người gọi không cần tự viết `(String)`. Trong `Box<Integer>`, `get()` được dùng như `Integer` và có thể tự mở hộp (`unboxing`) khi tính toán.

Nếu sửa thành:

```java
Box<String> labelBox = new Box<>(42); // Không biên dịch
```

trình biên dịch sẽ từ chối do `Integer` không đáp ứng kiểu `String` mà biến `labelBox` yêu cầu. Đây là lợi ích quan trọng: phát hiện sai sót **trước khi chạy**, gần nơi truyền dữ liệu sai, thay vì chờ đến một nhánh ép kiểu xa hơn.

Với collection, nguyên tắc tương tự:

```java
List<String> words = new ArrayList<>();
words.add("Java");
// words.add(42);  // Bỏ // sẽ gây lỗi biên dịch
String first = words.get(0); // Không cần ép kiểu tường minh
```

Đoạn trên là **mã minh họa trong một phương thức**, cần import `java.util.List` và `java.util.ArrayList`. `List<String>` ràng buộc **kiểu phần tử** là `String`; `List<Object>` thì không. Cũng cần nhớ `List<String>` không phải kiểu con của `List<Object>`: generic Java mặc định **bất biến** (`invariant`), nhằm tránh những phép thêm phần tử sai kiểu qua tham chiếu có tham số kiểu khác.

### So sánh thiết kế

| Hợp đồng sử dụng | Mức bảo đảm ở lúc biên dịch | Việc người dùng API thường phải làm |
|---|---|---|
| `Object get()` | Chỉ biết kết quả là tham chiếu `Object` | Tìm hiểu kiểu mong đợi, kiểm tra hoặc ép kiểu trước khi dùng API riêng. |
| `List<Object>` | Từng phần tử có kiểu tĩnh `Object` | Chấp nhận dữ liệu nhiều kiểu; kiểm tra khi cần thao tác đặc thù. |
| `T get()` trong `Box<T>` | Ràng buộc theo tham số kiểu cụ thể | Dùng đúng kiểu được khai báo; lỗi đưa dữ liệu sai kiểu thường bị chặn khi biên dịch. |
| `List<String>` | Hạn chế phần tử thêm qua API được kiểm tra kiểu thành `String` | Lấy phần tử như `String` mà không tự ép kiểu. |

## 5. Khi chưa thể dùng generic: kiểm tra kiểu trước khi sử dụng

Với API cũ hoặc nguồn dữ liệu thực sự không biết trước kiểu, có thể kiểm tra bằng `instanceof` thay vì ép mù. Java 17 hỗ trợ **pattern matching for `instanceof`**:

Lưu thành `SafeReadDemo.java`:

```java
public class SafeReadDemo {
    public static void main(String[] args) {
        Object item = 42;

        if (item instanceof String text) {
            System.out.println(text.toUpperCase());
        } else {
            System.out.println("Không phải String");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SafeReadDemo.java
java SafeReadDemo
```

`stdout`:

```text
Không phải String
```

Trong nhánh đúng, biến `text` đã có kiểu `String`; nếu không phù hợp, ta xử lý nhánh còn lại một cách tường minh. Kiểm tra này phù hợp khi dữ liệu **chủ ý có nhiều kiểu** hoặc đi qua một ranh giới thiếu thông tin kiểu. Nếu toàn bộ dữ liệu về bản chất phải cùng một kiểu, hãy mô hình hóa hợp đồng bằng generic hoặc lớp dữ liệu cụ thể từ đầu, thay vì rải `instanceof` khắp chương trình.

## 6. Generics có chặn được mọi `ClassCastException` không?

**Không.** Trong mã Java dùng tham số kiểu một cách nhất quán, generics phát hiện được nhiều sai sót ngay lúc biên dịch. Tuy nhiên, Java triển khai generic chủ yếu qua **type erasure**: phần lớn thông tin tham số kiểu không được duy trì như một kiểm tra kiểu cho từng phần tử trong collection tại runtime. Mã dùng **raw type**, ép kiểu không kiểm tra, phản xạ hoặc tương tác với mã cũ vẫn có thể phá ràng buộc.

Ví dụ cố ý vi phạm hợp đồng generic, lưu thành `RawTypeTrapDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class RawTypeTrapDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        List raw = names;  // Raw type: đánh mất kiểm tra tham số kiểu
        raw.add(123);     // Có thể được biên dịch nhưng báo unchecked warning

        try {
            String first = names.get(0);
            System.out.println(first);
        } catch (ClassCastException ex) {
            System.out.println("Dữ liệu sai kiểu xuất hiện khi đọc");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 -Xlint:rawtypes,unchecked RawTypeTrapDemo.java
java RawTypeTrapDemo
```

`stdout` của lệnh `java`:

```text
Dữ liệu sai kiểu xuất hiện khi đọc
```

Lệnh `javac` còn cảnh báo về raw type và lời gọi `add` không được kiểm tra kiểu; nội dung cảnh báo cụ thể phụ thuộc trình biên dịch. Tại dòng `names.get(0)`, trình biên dịch đã chèn kiểm tra ép về `String` cho giá trị lấy ra. Vì dữ liệu bên trong thực tế là `Integer`, `ClassCastException` vẫn xảy ra mặc dù dòng đọc không có ép kiểu tường minh trong mã nguồn.

**Bài học:** đừng bỏ qua cảnh báo `rawtypes` và `unchecked`; khi phải tích hợp API cũ, hãy thu hẹp phạm vi tương tác, xác thực dữ liệu ở biên và trả về API đã được ràng buộc kiểu.

## 7. Chọn `Object` hay generic trong trường hợp nào?

- Dùng **generic** khi các giá trị của một collection, hộp dữ liệu, tham số hoặc kết quả phương thức có **quan hệ kiểu cần được bảo đảm** cho người gọi: ví dụ `List<Order>`, `Repository<T>`, `Optional<User>` hoặc `Box<T>`.
- Dùng **`Object` có chủ đích** khi hợp đồng thật sự chấp nhận mọi đối tượng và cách xử lý không đòi hỏi giả định một kiểu cụ thể; ví dụ một tham số chỉ dùng `toString()` hoặc một ranh giới API rất tổng quát. Không dùng `Object` chỉ để né thiết kế kiểu.
- Nếu giá trị hợp lệ có **một số biến thể xác định**, cân nhắc một interface chung hoặc mô hình kiểu có phân nhánh rõ ràng, thay cho một tập các phép ép tùy tiện.
- Khi đọc dữ liệu bên ngoài như JSON, thông điệp hoặc cache, **kiểm tra/giải mã** sang kiểu được kỳ vọng tại ranh giới. Khai báo một tham số generic ở mã ứng dụng không tự xác thực payload bên ngoài.
- Nếu cần nhiều kiểu trong cùng collection, hãy diễn đạt điều đó thành API dễ hiểu và dùng kiểm tra/dispatch phù hợp; việc đổi từ `List<Object>` sang `List<String>` chỉ hợp lý khi các phần tử thực sự phải là chuỗi.

### Sai lầm thường gặp

**“Ép kiểu là chuyển đổi dữ liệu.”** Không đúng với ép kiểu tham chiếu: `(String) object` chỉ kiểm tra object đang tham chiếu tới kiểu phù hợp, không tạo một chuỗi mới từ một số.

**“Đã có generic thì JVM sẽ không bao giờ ném `ClassCastException`.”** Không đúng khi có raw type, unchecked cast hoặc dữ liệu bị đưa vào qua đường không an toàn.

**“Dùng `List<Object>` đã đủ an toàn vì cũng là generic.”** Tham số `Object` không biểu đạt ràng buộc chỉ nhận `String`; cả `String` và `Integer` đều hợp lệ.

**“Chỉ cần `try/catch` mọi nơi là xong.”** Bắt ngoại lệ giúp ứng dụng xử lý thất bại, nhưng không giải quyết hợp đồng dữ liệu sai. Nên giảm ép kiểu bằng thiết kế kiểu trước; xử lý ngoại lệ tại ranh giới thật sự cần thiết.

## 8. Tự kiểm tra

1. Vì sao `Object x = 42; String s = (String) x;` có thể biên dịch nhưng ném `ClassCastException`? Hãy chỉ ra kiểu tĩnh và kiểu thực tế của `x`.
2. Trong `List<Object> values`, tại sao `values.add("A")` và `values.add(10)` đều hợp lệ? Nếu mọi phần tử đều phải là chuỗi, kiểu nào diễn đạt đúng ý định hơn?
3. Nếu `List<String> names` bị thêm một `Integer` thông qua raw type, lỗi có thể xuất hiện ở thao tác đọc hay thao tác thêm? Vì sao?

**Đáp án định hướng:** (1) Kiểu tĩnh của `x` là `Object`, kiểu thực tế là `Integer`; JVM bác phép ép thành `String`. (2) Vì `Object` cho phép mọi kiểu tham chiếu; dùng `List<String>` để ràng buộc phần tử là chuỗi. (3) Phép thêm qua raw type có thể chỉ tạo cảnh báo; lúc đọc thành `String`, phép kiểm tra ép kiểu do trình biên dịch chèn mới phát hiện đối tượng `Integer`.

Thông điệp cốt lõi là: **dùng `Object` làm mất khả năng mô tả chính xác kiểu dữ liệu tại điểm gọi; ép kiểu tường minh chuyển một phần trách nhiệm kiểm tra sang JVM lúc chạy. Generics giúp trình biên dịch kiểm tra hợp đồng kiểu sớm hơn, nhưng cần tránh các đường truy cập không an toàn để giữ lợi ích đó.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-003">Quan hệ giữa kiểm tra kiểu lúc biên dịch và dữ liệu lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa kiểm tra kiểu lúc biên dịch và dữ liệu lúc chạy trong Java Generics

Java Generics cho phép diễn đạt và kiểm tra quan hệ kiểu ngay trong mã nguồn, nhưng phần lớn đối số kiểu (như `String` trong `List<String>`) không trở thành thông tin kiểu riêng của từng đối tượng ở runtime. Để hiểu đúng Generics, phải phân biệt **kiểu tĩnh mà trình biên dịch kiểm tra** với **lớp và giá trị thực sự tồn tại khi JVM chạy**.

## 1. Hai tầng thông tin kiểu

- **Compile time (thời điểm biên dịch):** `javac` dựa vào khai báo `List<String>`, `Box<Integer>`, kiểu tham số phương thức, giới hạn `extends` và wildcard để kiểm tra phép gán, lời gọi phương thức, phần tử có thể thêm vào collection và kiểu giá trị trả về. Mục tiêu là phát hiện nhiều sai sót trước khi chạy.
- **Runtime (thời điểm chạy):** JVM làm việc với đối tượng thực tế như `ArrayList`, `String`, `Integer`; kiểm tra ép kiểu (`checkcast`), truy cập mảng (`aastore`), virtual dispatch và các quy tắc runtime khác. JVM không tự biết một đối tượng `new ArrayList<>()` hiện đang mang đối số kiểu `String` chỉ vì biến tham chiếu được khai báo `List<String>`.
- **Type erasure (xóa kiểu):** Java thường biên dịch đối số kiểu generic sang kiểu đã xóa: `List<String>` và `List<Integer>` cùng dùng lớp runtime `List`/`ArrayList` tương ứng. Biến kiểu `T` thường bị thay bằng cận trên của nó, hoặc `Object` nếu không có cận trên tường minh. Trình biên dịch chèn ép kiểu khi cần và có thể sinh bridge method để bảo toàn đa hình.

Điều này không có nghĩa Java “mất hết kiểm tra kiểu” ở runtime: runtime vẫn kiểm tra kiểu đối tượng và phép cast có thể gây `ClassCastException`. Điều bị xóa chủ yếu là **đối số kiểu của generic**, chẳng hạn `String` trong `List<String>`.

| Câu hỏi | Compile time | Runtime |
| --- | --- | --- |
| Có được gọi `names.add(123)` khi `names` là `List<String>`? | Không, lỗi biên dịch. | Không có cơ hội chạy nếu mã không biên dịch. |
| Đối tượng là `ArrayList` hay `LinkedList`? | Biết theo kiểu khởi tạo/luồng phân tích nếu đủ thông tin. | Xác định bằng lớp thực tế của đối tượng. |
| Một `ArrayList` mang `String` hay `Integer`? | Dùng khai báo generic để ràng buộc thao tác. | Bản thân đối tượng thông thường không giữ thông tin đối số kiểu đó. |
| `String name = names.get(0)` có an toàn tuyệt đối? | Hợp lệ khi giả định danh sách tuân thủ `List<String>`. | Cast do compiler chèn có thể thất bại nếu danh sách đã bị làm bẩn. |
| Có thể đọc `List<String>` từ khai báo trường? | Có trong mô hình type của compiler. | Reflection có thể đọc generic signature của **khai báo**, nếu metadata hiện diện; khác với đối số kiểu của từng đối tượng. |

## 2. Quan sát một chương trình chạy được

Tạo tệp `GenericsAtRuntime.java`:

```java
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GenericsAtRuntime {
    static class Catalog {
        List<String> titles = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        List<String> titles = new ArrayList<>();
        titles.add("java");

        String title = titles.get(0);
        System.out.println(title.toUpperCase());

        ArrayList<Integer> counts = new ArrayList<>();
        System.out.println(titles.getClass() == counts.getClass());
        System.out.println(titles instanceof List<?>);

        Field field = Catalog.class.getDeclaredField("titles");
        System.out.println(field.getGenericType().getTypeName());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac GenericsAtRuntime.java
java GenericsAtRuntime
```

Kết quả chuẩn (`stdout`):

```text
JAVA
true
true
java.util.List<java.lang.String>
```

Giải thích lần lượt:

1. `titles.add("java")` và `String title = titles.get(0)` được kiểm tra theo hợp đồng `List<String>` ở compile time.
2. `titles` và `counts` đều được khởi tạo bằng `new ArrayList<>()`. Đối số kiểu ở nguồn khác nhau nhưng lớp runtime vẫn cùng là `java.util.ArrayList`, nên phép so sánh `Class` trả về `true`.
3. `instanceof List<?>` hợp lệ: `List<?>` là một kiểu *reifiable* dùng được cho phép kiểm tra runtime. Điều này chỉ kiểm tra đối tượng có thực thi `List`; không xác minh kiểu phần tử.
4. `Field.getGenericType()` lấy metadata của **trường được khai báo** `Catalog.titles`, nên đọc được `List<String>`. Metadata này không chứng minh từng phần tử trong danh sách hiện đều là `String`.

Cần phân biệt `List<String> titles = new ArrayList<>();` với `new ArrayList<String>()`: cú pháp diamond `<>` yêu cầu compiler suy luận đối số kiểu, không thay đổi bản chất type erasure.

## 3. Type erasure hoạt động như thế nào?

Xét mã nguồn minh họa:

```java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class NumberBox<T extends Number> {
    T value;
}
```

Ở mức hình dung cấu trúc đã xóa kiểu:

```java
// Chỉ là mô hình giải thích sau erasure,
// không phải toàn bộ mã nguồn mà javac thật sự xuất ra.
class Box {
    private Object value;

    Box(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}

class NumberBox {
    Number value;
}
```

Quy tắc quan trọng:

- `T` không có cận trên tường minh thường xóa thành `Object`.
- `T extends Number` xóa thành `Number`. Với nhiều cận như `T extends Number & Comparable<T>`, cận **đầu tiên** chi phối erasure của `T`.
- Các tham số kiểu trong `List<String>`, `Map<String, Integer>` không tồn tại như các lớp JVM riêng biệt `ListOfString` hay `MapOfStringInteger`.
- Compiler có thể tạo cast hoặc bridge method. Một số generic signature vẫn được lưu trong metadata của class/method/field để công cụ và reflection đọc được, vì vậy erasure không đồng nghĩa xóa mọi dấu vết generic trong tệp `.class`.

Khi viết:

```java
Box<String> box = new Box<>("hello");
String message = box.get();
```

Compiler biết `box.get()` có kiểu biểu thức `String`, nhưng phiên bản đã xóa của phương thức `get` trả về `Object`. Ở bytecode tại nơi dùng giá trị, compiler có thể chèn `checkcast java/lang/String` để bảo vệ phép gán. Nếu dữ liệu thực tế sai kiểu, cast thất bại **khi chạy**.

Có thể tự kiểm tra bytecode của ví dụ biên dịch bằng:

```bash
javap -c -p GenericsAtRuntime
```

Hãy tìm các lệnh gọi `List.get` và `checkcast` ở nơi kết quả được dùng như `String`. Không cần giả định chính xác thứ tự/offset bytecode giống nhau ở mọi bản JDK.

## 4. Tại sao đã qua compiler vẫn có thể ClassCastException?

Generic type safety được đảm bảo trong điều kiện chương trình dùng generic API đúng cách và không phá vỡ hợp đồng qua raw type, unchecked cast, biến đổi mảng không an toàn, reflection hoặc mã ngoài đã biên dịch riêng.

Chương trình sau cố tình tạo **heap pollution**: một biến có kiểu tham số hóa trỏ tới cấu trúc chứa giá trị không phù hợp với kiểu đã khai báo.

Tạo tệp `HeapPollutionDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class HeapPollutionDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("Lan");

        List raw = names; // Raw type: bỏ kiểm tra đối số kiểu.
        raw.add(42);      // Unchecked call: đưa Integer vào List<String>.

        try {
            String second = names.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Lệnh chạy:

```bash
javac -Xlint:rawtypes -Xlint:unchecked HeapPollutionDemo.java
java HeapPollutionDemo
```

Kết quả `stdout`:

```text
ClassCastException
```

`javac` phát cảnh báo cho raw type và thao tác thêm phần tử không được kiểm tra. Cảnh báo không tự chặn việc biên dịch. `raw.add(42)` có thể lưu `Integer` vì runtime `ArrayList.add` nhận đối tượng theo kiểu đã xóa, không tự kiểm tra “phải là String”. Khi lấy phần tử và gán cho `String`, cast do compiler thêm mới phát hiện giá trị thực tế là `Integer`.

Ngược lại, viết `names.add(42)` trực tiếp sẽ bị lỗi biên dịch. Dòng `List raw = names` không sao chép dữ liệu: `raw` và `names` là hai tham chiếu tới **cùng một danh sách**. Cũng vì vậy, dùng `@SuppressWarnings("unchecked")` chỉ để giấu cảnh báo không sửa được heap pollution.

Một unchecked cast khác:

```java
Object unknown = new ArrayList<Integer>();

@SuppressWarnings("unchecked")
List<String> assumed = (List<String>) unknown;
```

Runtime chỉ có thể kiểm tra `unknown` là một `List` thích hợp cho cast đã xóa; không xác nhận phần tử là `String`. Cast ở dòng này có thể thành công và lỗi xuất hiện muộn hơn khi đọc phần tử thành `String`. Chỉ suppress warning khi đã có cơ sở độc lập chứng minh dữ liệu phù hợp, và thu hẹp phạm vi annotation tới dòng/biến cần thiết.

## 5. Reifiable và non-reifiable: có thể kiểm tra đến đâu lúc chạy?

Một kiểu *reifiable* giữ đủ thông tin cần cho phép kiểm tra kiểu runtime của JVM. Ví dụ: `String`, `String[]`, `List<?>` và raw type `List`. Kiểu tham số hóa cụ thể như `List<String>` nhìn chung là *non-reifiable*: `String` ở trong dấu `<...>` không được dùng để xác minh mọi phần tử lúc chạy.

| Biểu thức | Hợp lệ? | Ý nghĩa |
| --- | --- | --- |
| `obj instanceof List<?>` | Có | Kiểm tra đối tượng có kiểu runtime tương thích với `List`. |
| `obj instanceof List<String>` với `obj` có kiểu tĩnh `Object` | Không | Không thể xác minh đối số kiểu `String` bằng `instanceof`. |
| `obj instanceof String` | Có | Kiểm tra kiểu lớp runtime `String`. |
| `new List<String>[5]` | Không | Không được tạo trực tiếp mảng có thành phần generic non-reifiable. |
| `new List<?>[5]` | Có | Thành phần mảng `List<?>` reifiable. |
| `new T()` hoặc `new T[5]` trong `class Box<T>` | Không | Biến kiểu `T` không cung cấp constructor/class component runtime phù hợp. |

Java array và generic collection có cơ chế khác nhau:

- **Mảng là covariant và reified ở mức kiểu thành phần**: `String[]` là một `Object[]` hợp lệ về quan hệ subtype, nhưng JVM biết mảng thật chứa `String` và có thể ném `ArrayStoreException` nếu ai đó cố đặt `Integer` vào.
- **Generic là invariant theo đối số kiểu**: `List<Integer>` không phải subtype của `List<Number>`, dù `Integer extends Number`. Quy tắc này ngăn một người nhận `List<Number>` đưa `Double` vào danh sách được người khác coi là `List<Integer>`.
- `List<? extends Number>` cho phép đọc phần tử như `Number` nhưng không cho thêm một `Integer` hoặc `Double` tùy ý (trừ `null`), vì không biết loại danh sách cụ thể mà wildcard đại diện. `List<? super Integer>` cho phép thêm `Integer` và đọc ra theo kiểu an toàn chung là `Object`.

Ví dụ mảng để đối chiếu:

```java
Object[] objects = new String[1];

try {
    objects[0] = 42;
} catch (ArrayStoreException ex) {
    System.out.println("ArrayStoreException");
}
```

Đoạn này in `ArrayStoreException` nếu đặt trong phương thức chạy được. Mảng phát hiện lỗi tại điểm **ghi** nhờ component type runtime. Ngược lại, danh sách generics thông thường có thể bị làm bẩn qua raw type và lỗi bị phát hiện ở điểm **đọc/cast**.

## 6. Quan hệ kiểu compile time không phải quan hệ lớp runtime

Một số tình huống thường gây nhầm:

**a) Invariance không biến mất vì erasure.**

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // Lỗi biên dịch.
List<? extends Number> readableNumbers = integers;
Number number = readableNumbers.isEmpty() ? 0 : readableNumbers.get(0);
```

Dù cả hai danh sách được xóa về `List` ở runtime, compiler vẫn áp dụng quan hệ kiểu generic trong chương trình nguồn. Wildcard diễn đạt khả năng sử dụng, không làm JVM tạo một loại `List` mới.

**b) Hai overload khác đối số kiểu có thể trùng chữ ký sau erasure.**

```java
// Hai phương thức sau KHÔNG thể cùng được khai báo trong một lớp:
// void process(List<String> values) {}
// void process(List<Integer> values) {}
```

Sau erasure cả hai có tham số kiểu `List`, nên gây *name clash*. Hãy đặt tên phương thức khác nhau, bổ sung tham số thực sự phân biệt, hoặc thiết kế một phương thức generic chung phù hợp.

**c) Overriding có thể cần bridge method.**

```java
class Parent<T> {
    T get() {
        return null;
    }
}

class Child extends Parent<String> {
    @Override
    String get() {
        return "child";
    }
}
```

Ở cấp nguồn, `Child.get()` override `Parent<String>.get()`. Sau erasure, phương thức của `Parent` có dạng trả `Object`, còn `Child` trả `String`. Compiler có thể sinh một bridge method tổng hợp để lời gọi đa hình qua `Parent` vẫn định tuyến tới triển khai `Child.get()`. Có thể xem bằng `javap -c -p Child` sau khi biên dịch đoạn mã trong một tệp phù hợp.

**d) Không được tạo kiểu generic bằng nguyên thủy.**

`List<int>` không hợp lệ vì đối số generic phải là reference type; dùng `List<Integer>`. Boxing/unboxing tự động giúp làm việc với `Integer`, nhưng điều đó không biến `int` thành một tham số generic của JVM.

## 7. Reflection biết gì và không biết gì?

Một nhầm lẫn phổ biến là “reflection có `ParameterizedType`, vậy erasure không xảy ra”. Hai sự thật cùng đúng:

1. Metadata trong class file có thể lưu **khai báo generic** của trường, phương thức, superclass và interface. `Field.getGenericType()`, `Method.getGenericReturnType()`, `Class.getGenericSuperclass()` hoặc `Class.getTypeParameters()` giúp đọc dữ liệu này.
2. Một đối tượng `new ArrayList<String>()` thường không lưu `String` như thuộc tính runtime riêng. `list.getClass()` trả về `ArrayList.class`; `getTypeParameters()` của `ArrayList.class` nói rằng lớp khai báo biến kiểu `E`, chứ không trả về đối số `String` mà biến tham chiếu của người gọi đã dùng.

Ví dụ, nếu khai báo một lớp con chuyên biệt:

```java
class StringList extends ArrayList<String> {
}
```

thì `StringList.class.getGenericSuperclass()` có thể mô tả `ArrayList<String>` vì kiểu được cố định trong **khai báo lớp con**. Nhưng tạo `new ArrayList<String>()` và hỏi `getClass().getGenericSuperclass()` không cung cấp cùng thông tin: đối tượng thuộc lớp `ArrayList`, vốn khai báo superclass không gắn `String`.

Khi ứng dụng cần truyền thông tin kiểu vào runtime, có hai hướng cơ bản:

- **Class token:** nhận `Class<T>`, chẳng hạn `String.class`, rồi dùng `type.cast(value)` hoặc `type.isInstance(value)` để kiểm tra một kiểu lớp cụ thể. `Class<T>` không biểu đạt đầy đủ kiểu lồng như `List<String>`.
- **Đại diện kiểu phức tạp:** truyền `java.lang.reflect.Type` hoặc cơ chế type token/type reference để giữ cấu trúc `List<String>` cho serializer/deserializer, framework hoặc hệ thống mapping. Metadata chỉ giúp mô tả kiểu mong muốn; kiểm tra từng phần tử vẫn cần logic xử lý dữ liệu phù hợp.

Đừng suy luận kiểu phần tử chỉ từ `collection.getClass()`; đừng nhầm `ParameterizedType` mô tả khai báo hoặc type token với khả năng JVM tự cưỡng chế đối số kiểu của mỗi collection.

## 8. Muốn kiểm tra cả phần tử ở runtime thì làm gì?

Khi dữ liệu đến từ nguồn không đáng tin về kiểu (JSON, reflection, API cũ, raw collection), hãy kiểm tra ở ranh giới nhận dữ liệu.

**Cách 1 — kiểm tra phần tử tường minh, tạo bản sao typed:**

```java
import java.util.ArrayList;
import java.util.List;

public class CheckedConversion {
    static List<String> requireStrings(List<?> source) {
        List<String> result = new ArrayList<>();
        for (Object value : source) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Expected String");
            }
            result.add(text);
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> names = requireStrings(List.of("Lan", "Mai"));
        System.out.println(names);
    }
}
```

Chạy `javac CheckedConversion.java` và `java CheckedConversion` cho `stdout`:

```text
[Lan, Mai]
```

Hàm nhận `List<?>`, nên an toàn khi đọc từng giá trị như `Object`. Pattern matching cho `instanceof String text` (được hỗ trợ trong Java 17) chỉ thêm giá trị đã kiểm tra vào danh sách mới. Với `List.of("Lan", 42)`, hàm phát `IllegalArgumentException` tại ranh giới chuyển đổi. Dùng bản sao cũng giúp hạn chế alias với dữ liệu đầu vào có thể bị thay đổi sau đó; nếu cần không cho sửa danh sách kết quả, có thể trả `List.copyOf(result)` khi các phần tử không null.

**Cách 2 — dùng runtime-checked view của Collections:**

```java
List<String> checked = java.util.Collections.checkedList(
        new java.util.ArrayList<>(), String.class);
```

`Collections.checkedList` tạo một view chặn việc thêm phần tử không tương thích với `String.class`, kể cả khi có mã cũ truy cập view qua raw type. Nó dùng runtime class token để kiểm tra phần tử, không tạo kiểu `List<String>` reified. Lưu ý view này không bảo vệ nếu bên ngoài còn giữ tham chiếu trực tiếp tới danh sách backing và sửa nó, và không tự làm sạch dữ liệu sai kiểu đã có sẵn trước khi bọc.

**Cách 3 — duy trì API typed từ đầu:** dùng `List<String>`, `Map<K,V>`, phương thức generic và wildcard phù hợp; tránh raw type, unchecked cast, reflective insert và truyền alias mutable cho mã không bảo đảm hợp đồng. Đây là biện pháp chính; kiểm tra runtime bổ sung ở những biên giới cần thiết.

## 9. Thiết kế và đọc lỗi theo đúng hai thời điểm

Khi thiết kế một phương thức generic:

```java
import java.util.List;
import java.util.NoSuchElementException;

class FirstElement {
    static <T> T first(List<T> values) {
        if (values.isEmpty()) {
            throw new NoSuchElementException("Empty list");
        }
        return values.get(0);
    }
}
```

`<T>` diễn đạt quan hệ **đầu vào `List<T>` → đầu ra `T`**. Compiler suy luận kiểu ở điểm gọi:

```java
String name = FirstElement.first(List.of("Lan", "Mai"));
Integer count = FirstElement.first(List.of(1, 2, 3));
// String wrong = FirstElement.first(List.of(1, 2, 3)); // Lỗi kiểu.
```

Ví dụ này tập trung vào hợp đồng kiểu và chỉ là các đoạn khai báo/lời gọi minh họa; đặt ba lời gọi bên trong một phương thức khi chạy thực tế. Sau erasure, JVM không cần có phiên bản `first` riêng cho `String` và `Integer`. Kiểu trả về được compiler áp dụng tại vị trí gọi; nếu dữ liệu bị làm bẩn, cast có thể thất bại.

Khi điều tra lỗi, dùng đúng dấu hiệu:

| Dấu hiệu | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `incompatible types` | Sai quan hệ kiểu ở compile time, invariance hoặc thiếu chuyển đổi hợp lệ. | Sửa chữ ký generic, wildcard hoặc dữ liệu truyền vào; không ép cast để qua compiler. |
| `unchecked` / `rawtypes` warning | Trình biên dịch không chứng minh được hợp đồng generic. | Lần theo raw type, unchecked conversion/cast; thay bằng API typed hoặc kiểm tra từng phần tử. |
| `ClassCastException` khi đọc collection | Dữ liệu thực có lớp khác kiểu dự kiến tại điểm cast. | Truy ngược nơi chèn dữ liệu; tìm raw alias, reflection, deserialize hoặc unchecked cast. |
| `ArrayStoreException` | Mảng reified từ chối ghi sai component type. | Đừng dựa vào covariance để ghi phần tử; dùng kiểu mảng phù hợp hoặc collection với generic hợp đồng đúng. |
| `cannot select from a type variable` / `generic array creation` | Cần thông tin runtime không có sẵn từ `T`. | Nhận `Class<T>`, factory, `IntFunction<T[]>` hoặc thiết kế lại API. |
| `name clash ... same erasure` | Chữ ký generic khác nhau ở nguồn nhưng trùng sau erasure. | Đổi API hoặc chữ ký thực sự phân biệt. |

Lệnh `javac -Xlint:unchecked -Xlint:rawtypes TenFile.java` giúp phát hiện vị trí nguy cơ trước khi lỗi runtime xuất hiện. Khi đọc stack trace `ClassCastException`, tìm cả nơi **phát sinh dữ liệu không đúng kiểu**, vì vị trí ném lỗi thường chỉ là điểm dữ liệu được lấy ra và cast.

## 10. Các ngộ nhận cần tránh

1. **“Có Generics thì không bao giờ có ClassCastException.”** Sai: Generics kiểm tra hợp đồng ở compile time; dữ liệu vi phạm hợp đồng có thể đi vào qua đường unchecked/raw/reflection.
2. **“JVM biết đây là `List<String>` vì biến khai báo như vậy.”** Sai: khai báo biến là thông tin compiler dùng, không tự gắn `String` vào đối tượng `ArrayList`.
3. **“Mọi thông tin generic đều mất sạch sau biên dịch.”** Sai: erasure áp dụng cho thực thi generic, nhưng class file có thể giữ `Signature` metadata cho khai báo; reflection đọc được phần metadata này.
4. **“`instanceof List<?>` chứng minh các phần tử đều đúng kiểu.”** Sai: nó chỉ kiểm tra cấu trúc lớp `List` ở runtime.
5. **“`List<Integer>` có thể gán cho `List<Number>` vì `Integer extends Number`.”** Sai: bất biến theo đối số kiểu; dùng wildcard phù hợp nhu cầu đọc/ghi.
6. **“Cast `(List<String>) someList` sẽ rà soát các phần tử.”** Sai: unchecked cast thông thường không duyệt từng phần tử.
7. **“Bỏ cảnh báo bằng `@SuppressWarnings` khiến dữ liệu an toàn.”** Sai: annotation chỉ điều chỉnh cảnh báo compiler, không thay đổi đối tượng hoặc code kiểm tra runtime.

## 11. Tự kiểm tra hiểu bài

**Tình huống A.** Có `Object value = new ArrayList<Integer>();`. Có thể dùng `value instanceof List<?>` không? Có, nếu đối tượng thực thi `List` thì trả `true`. Có thể dùng `value instanceof List<String>` không? Không: kiểu đối số `String` không reifiable để JVM kiểm tra.

**Tình huống B.** Một phương thức nhận `List<String>` từ hệ thống cũ nhưng danh sách đã chứa `Integer`. Vì sao lời gọi `get` đôi khi chỉ lỗi sau đó? Phương thức `get` ở bytecode thao tác với giá trị theo kiểu đã xóa; lỗi phát sinh khi giá trị được cast sang `String` tại một điểm sử dụng đòi hỏi `String`. Không thể lấy thành công `String` từ một `Integer` bằng cast.

**Tình huống C.** Nếu cần nhận danh sách số chỉ để đọc, chọn `List<Number>` hay `List<? extends Number>`? Chọn `List<? extends Number>` khi muốn chấp nhận cả `List<Integer>`, `List<Double>`; đổi lại không thể tùy ý thêm số vào danh sách.

**Tình huống D.** `Field.getGenericType()` trả về `List<String>` có chứng minh dữ liệu lúc chạy là danh sách String không? Không. Nó phản ánh kiểu của trường được khai báo; phần tử vẫn có thể bị làm bẩn nếu các hàng rào an toàn bị vượt qua.

## Ghi nhớ

Generics là **hợp đồng kiểm tra kiểu mạnh ở compile time**; type erasure cho phép các instantiation dùng chung những lớp/thực thi runtime. JVM vẫn kiểm tra kiểu lớp thực và cast, nhưng không tự xác thực đối số generic cụ thể cho từng đối tượng collection. Vì vậy, hãy tin vào generic types để thiết kế API an toàn, coi raw/unchecked là dấu hiệu cần xem xét, và kiểm tra dữ liệu thực tại ranh giới nhận dữ liệu khi cần bảo đảm runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-004">Tham số kiểu, đối số kiểu, biến kiểu và kiểu tham số hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số kiểu, đối số kiểu, biến kiểu và kiểu tham số hóa trong Java

## 1. Mục tiêu và bức tranh tổng quát

Generics cho phép mô tả quan hệ giữa các kiểu ngay trong khai báo lớp, interface, constructor và phương thức. Thay vì viết một cấu trúc dữ liệu chỉ thao tác với Object rồi ép kiểu ở nơi sử dụng, ta khai báo một “chỗ trống” cho kiểu và để trình biên dịch kiểm tra các thao tác đối với kiểu đó.

Sau bài này, bạn cần nhận diện chính xác bốn thuật ngữ **type parameter**, **type argument**, **type variable**, **parameterized type**; đọc được khai báo generic lồng nhau, phân biệt generic class với generic method, hiểu wildcard/bound là gì và biết điểm nào chỉ tồn tại ở thời gian biên dịch.

Một ví dụ ngắn:

~~~java
class Box<T> {
    T value;
}

Box<String> title = new Box<>();
~~~

- **Box<T>** trong dòng khai báo: **T** được khai báo là *tham số kiểu* (type parameter), đồng thời tạo ra *biến kiểu* (type variable) tên T trong phạm vi của lớp.
- **Box<String>** tại nơi sử dụng: **String** là *đối số kiểu* (type argument) được truyền vào vị trí T; toàn bộ **Box<String>** là một *kiểu tham số hóa* (parameterized type).
- **new Box<>()**: toán tử kim cương (**diamond operator**) cho phép Java suy luận đối số kiểu từ ngữ cảnh gán, ở đây là String.
- Tên **Box** là tên khai báo lớp generic; dùng **Box** không kèm đối số trong một ngữ cảnh kiểu thường tạo ra *raw type*, cần phân biệt với **Box<?>**.

Bốn khái niệm liên hệ với nhau nhưng trả lời bốn câu hỏi khác nhau: **khai báo vị trí nhận kiểu ở đâu?** (type parameter), **tên kiểu đại diện bên trong khai báo là gì?** (type variable), **kiểu nào được đưa vào vị trí đó?** (type argument), và **kiểu hình thành sau khi áp dụng đối số kiểu là gì?** (parameterized type).

## 2. Tham số kiểu — type parameter

*Tham số kiểu* xuất hiện trong danh sách dấu ngoặc nhọn góc ngay sau tên lớp/interface, hoặc ngay trước kiểu trả về của phương thức generic. Nó khai báo một tên đại diện cho kiểu sẽ được sử dụng thống nhất tại các vị trí liên quan.

~~~java
class Pair<K, V> {
    private final K key;
    private final V value;

    Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    K key() { return key; }
    V value() { return value; }
}
~~~

**K** và **V** là hai tham số kiểu độc lập; cú pháp khai báo của chúng là **<K, V>**. Trình biên dịch dùng mối quan hệ này để bảo đảm phương thức key() trả về đúng kiểu đối với cặp cụ thể, và phương thức value() tương tự. Việc dùng hai chữ khác nhau thể hiện chúng không bắt buộc phải cùng kiểu.

Các vị trí khai báo thường gặp:

| Vị trí | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Lớp generic | class Box<T> | T thuộc khai báo lớp |
| Interface generic | interface Repository<ID, E> | ID và E thuộc interface |
| Phương thức generic | static <T> T identity(T value) | T chỉ thuộc phương thức |
| Constructor generic | <U> Holder(U source) | U thuộc constructor |
| Tham số có cận trên | <T extends Number> | T phải thỏa ràng buộc Number |
| Nhiều cận trên | <T extends Number & Comparable<T>> | T phải thỏa cả hai ràng buộc |

Trong cú pháp phương thức, **<T>** đặt **trước kiểu trả về**. Ví dụ **static <T> T identity(T value)** có tham số kiểu T, trả về T và nhận vào một giá trị kiểu T. Nếu viết **static T identity(T value)** trong một lớp không khai báo T, trình biên dịch không biết T là gì.

Danh sách tham số kiểu cũng biểu diễn ràng buộc:

~~~java
class NumericBox<T extends Number> {
    private final T value;

    NumericBox(T value) {
        this.value = value;
    }

    double doubleValue() {
        return value.doubleValue();
    }
}
~~~

Ở đây **T extends Number** nghĩa là kiểu đưa vào phải là Number hoặc một subtype của Number, ví dụ Integer hoặc Double. Từ khóa extends áp dụng cho cả lớp lẫn interface trong giới hạn generic; không có cú pháp **<T implements Runnable>**. Khi có nhiều cận, cận là class (nếu có) phải đứng đầu; những cận sau là interface, nối bằng dấu **&**. Không thể khai báo hai cận là hai class khác nhau.

Quy ước tên hay gặp: **T** (Type), **E** (Element), **K** (Key), **V** (Value), **R** (Result), **U** (một kiểu khác). Đây chỉ là quy ước, không phải từ khóa Java. Có thể dùng tên dài hơn, nhưng một tên dễ nhận diện giúp đọc chữ ký API nhanh hơn.

## 3. Biến kiểu — type variable

*Biến kiểu* là tên **T**, **K**, **V** hoặc tên khác được khai báo bởi type parameter. Nó được dùng như một **kiểu** bên trong phạm vi hợp lệ, không phải một biến chứa giá trị lúc chạy.

~~~java
class CacheEntry<K, V> {
    private K key;
    private V cachedValue;

    CacheEntry(K key, V cachedValue) {
        this.key = key;
        this.cachedValue = cachedValue;
    }

    V read() {
        return cachedValue;
    }
}
~~~

Trong ví dụ này, K và V dùng ở vị trí kiểu của field, tham số constructor và kiểu trả về. Chúng không phải hai đối tượng runtime tên K và V; không thể gán **T = String** hay in T như một giá trị chỉ vì đã khai báo **<T>**. Khi dùng CacheEntry<String, Integer>, trình biên dịch kiểm tra key thuộc String và cachedValue thuộc Integer.

Phạm vi (scope) rất quan trọng:

- Type variable khai báo trên lớp dùng được trong các instance field, instance method và phần khai báo kiểu thích hợp của lớp.
- Type variable khai báo bởi một phương thức chỉ tồn tại trong chữ ký/thân phương thức đó; hai phương thức cùng đặt tên **T** có thể có hai type variable khác nhau.
- Một thành viên static của lớp generic không được dùng trực tiếp T thuộc lớp, vì static thuộc về lớp nói chung chứ không thuộc riêng Box<String> hay Box<Integer>. Phương thức static muốn dùng T phải **tự khai báo** tham số kiểu của mình.

~~~java
class Helper<T> {
    T echo(T input) {          // T của lớp
        return input;
    }

    static <U> U identity(U input) { // U riêng của phương thức static
        return input;
    }
}
~~~

Có một điểm dễ nhầm: **type parameter** nhấn mạnh *vị trí khai báo* **<T>**, còn **type variable** nhấn mạnh *tên T đã được khai báo và dùng như một kiểu*. Chúng có quan hệ chặt chẽ nhưng không phải “type variable” là một kiểu được truyền vào.

Thậm chí cùng một ký hiệu T có thể giữ hai vai trò ở hai tầng: trong **class Box<T>**, T là tên của type parameter; trong **List<T> values**, T là *type variable* đang được dùng làm *type argument* cho generic interface List. Vai trò của một biểu thức phụ thuộc **ngữ cảnh xuất hiện**, không chỉ phụ thuộc ký tự nó chứa.

## 4. Đối số kiểu — type argument

*Đối số kiểu* nằm trong **<...> tại nơi sử dụng một kiểu generic**. Nó chỉ rõ kiểu mà một vị trí tham số kiểu phải đại diện ở cách sử dụng đó.

~~~java
Pair<String, Integer> age = new Pair<>("Lan", 24);
Pair<Long, Long> range = new Pair<>(100L, 200L);
java.util.List<String> names = java.util.List.of("An", "Binh");
java.util.Map<String, java.util.List<Integer>> marks =
        java.util.Map.of("java", java.util.List.of(8, 9));
~~~

Đọc lần lượt:

- Pair<String, Integer> có **String** là đối số cho K, **Integer** là đối số cho V.
- Pair<Long, Long> truyền cùng một kiểu Long vào hai vị trí *khác nhau*; không có quy tắc buộc K khác V.
- List<String> truyền String cho tham số E của List<E>.
- Map<String, List<Integer>> truyền String cho K và **cả kiểu List<Integer>** cho V; bên trong List<Integer> lại có Integer làm đối số kiểu.
- Đối số kiểu thông thường là kiểu tham chiếu (reference type), bao gồm interface, class, array type và kiểu tham số hóa. **List<int>** là lỗi; dùng **List<Integer>**. **List<int[]>** lại hợp lệ vì int[] là reference type.

Không chỉ tên lớp cụ thể mới là type argument. Wildcard cũng là một dạng đối số kiểu:

~~~java
java.util.List<?> unknown;
java.util.List<? extends Number> numbers;
java.util.List<? super Integer> integerConsumers;
~~~

Ở đây **?**, **? extends Number**, **? super Integer** là các *wildcard type arguments*. Chúng mô tả một tập những kiểu có thể tương thích, không khai báo một biến kiểu mang tên mới. **?** không phải tên khác của T, và không thể khai báo **class Box<?>** thay cho **class Box<T>**.

Khi thấy **<T>**, hãy hỏi nó đang **khai báo** tên T hay **truyền** T làm đối số cho một generic type khác: **class Box<T>** khai báo; **List<T>** bên trong Box lại đang dùng T làm đối số.

## 5. Kiểu tham số hóa — parameterized type

*Parameterized type* hình thành khi dùng một khai báo generic với danh sách type argument, ví dụ **List<String>**, **Map<String, Integer>**, **Box<T>**, **List<?>**. Khái niệm này nói đến **toàn bộ kiểu**, còn type argument chỉ nói đến thành phần được truyền vào.

| Biểu thức | Phân loại | Lý do |
| --- | --- | --- |
| List<E> trong khai báo interface List<E> | Generic interface declaration với tham số kiểu E | Đang giới thiệu E |
| List<String> | Parameterized type | Dùng List với đối số String |
| Map<String, List<Integer>> | Parameterized type lồng nhau | V là một parameterized type khác |
| List<T> trong một lớp có <T> | Parameterized type sử dụng type variable | T đã có phạm vi và được truyền vào List |
| List<?> | Parameterized type dùng wildcard | Không biết chính xác đối số kiểu |
| List (không có ngoặc góc trong vị trí kiểu) | Raw type | Bỏ qua đối số kiểu của List |
| String | Kiểu lớp thông thường | String không phải generic class |

Phân biệt **generic type declaration** và **parameterized type**: **interface List<E>** là khai báo generic; **List<String>** là một cách sử dụng đã truyền đối số. Một generic declaration có thể có nhiều kiểu tham số hóa hợp lệ và chúng khác nhau về kiểm tra kiểu ở thời gian biên dịch.

Đừng hiểu “parameterized type” là một lớp vật lý riêng được Java tạo ra cho từng đối số. Ở JVM, cơ chế type erasure khiến ArrayList<String> và ArrayList<Integer> đều thường dùng cùng runtime class ArrayList; phần generic chủ yếu bảo đảm quan hệ kiểu trong mã nguồn và các kiểm tra/chuyển kiểu cần thiết do compiler chèn vào.

## 6. Ví dụ chạy độc lập: xác định từng vai trò

Lưu thành **GenericTermsDemo.java**:

~~~java
import java.util.List;

public class GenericTermsDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static <E> E first(List<E> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Empty list");
        }
        return items.get(0);
    }

    public static void main(String[] args) {
        Box<String> greeting = new Box<>("Java generics");
        List<Integer> versions = List.of(17, 21);

        String message = greeting.get();
        int firstVersion = first(versions);

        System.out.println(message);
        System.out.println(firstVersion);
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 GenericTermsDemo.java
java GenericTermsDemo
~~~

Stdout:

~~~text
Java generics
17
~~~

Giải thích từng bước:

1. **Box<T>** khai báo một type parameter T; **T value**, **Box(T value)** và **T get()** dùng type variable T trong lớp.
2. **Box<String>** là parameterized type, String là đối số kiểu. Phép gán kết quả get() cho String được kiểm tra khi biên dịch.
3. **new Box<>** suy luận String nhờ bên trái phép gán; dấu **<>** ở constructor không khai báo một type parameter mới.
4. **<E> E first(List<E> items)** khai báo một generic method. **E** là type variable của phương thức; **List<E>** là parameterized type dùng E làm đối số.
5. Từ đối số **List<Integer>**, compiler suy luận E là Integer khi gọi first(versions). Integer được auto-unbox để gán vào **int firstVersion**.
6. **List.of(17, 21)** tạo danh sách không cho thay đổi cấu trúc, có kiểu phần tử Integer; đây là đặc tính của factory List.of, không phải thuộc tính chung của mọi List<Integer>.

Nếu thay câu lệnh bằng **String invalid = first(versions);**, trình biên dịch báo lỗi kiểu vì Integer không thể được dùng làm String; generics giúp lỗi được phát hiện sớm.

## 7. Generic class, generic method và generic constructor

Lớp generic khai báo tham số kiểu ngay sau tên lớp; các instance member dùng được tham số này. Phương thức generic có **danh sách tham số kiểu riêng**, đứng trước kiểu trả về, kể cả khi lớp chứa nó không generic.

~~~java
class Samples<T> {
    T stored;

    Samples(T stored) {
        this.stored = stored;
    }

    T get() {
        return stored;           // Dùng T của lớp; bản thân get() không khai báo <T>
    }

    <U> U select(U candidate) {
        return candidate;        // U chỉ thuộc phương thức select()
    }

    static <R> R same(R value) {
        return value;            // R do chính phương thức static khai báo
    }
}
~~~

Một constructor cũng có thể khai báo type parameter độc lập:

~~~java
class Receipt<T> {
    private final T value;

    <U> Receipt(U original, java.util.function.Function<U, T> converter) {
        this.value = converter.apply(original);
    }

    T get() { return value; }
}

// Ví dụ sử dụng:
// Receipt<Integer> receipt = new Receipt<>("42", Integer::parseInt);
~~~

T của Receipt thuộc lớp; U của constructor mô tả kiểu dữ liệu đầu vào, có thể khác T. **Receipt<Integer>** là parameterized type của lớp, trong khi lời gọi constructor còn cần compiler suy luận U là String.

Cần tách ba thao tác dễ giống nhau về hình dạng: khai báo **<T>**; sử dụng kiểu **Box<T>** trong phạm vi T đã có; và suy luận đối số bởi diamond **new Box<>()**. Chúng không thay thế lẫn nhau.

## 8. Ràng buộc type variable và wildcard: hai cơ chế khác nhau

Có hai kiểu giới hạn cần đọc đúng:

**Ràng buộc cho type parameter** viết khi khai báo generic: **<T extends Number>**, **<T extends Number & Comparable<T>>**. Nó đặt điều kiện lên T và cho phép thân hàm sử dụng các API của cận đã biết, ví dụ Number.doubleValue() hoặc Comparable.compareTo().

**Wildcard bound** viết ở nơi sử dụng kiểu: **List<? extends Number>** hoặc **List<? super Integer>**. Nó cho phép nhận một nhóm parameterized types mà không cần đặt tên biến kiểu cụ thể cho từng danh sách.

~~~java
import java.util.ArrayList;
import java.util.List;

public class BoundsAndVarianceDemo {
    static <T extends Number & Comparable<T>> T largest(List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Empty list");
        }
        T maximum = items.get(0);
        for (T item : items) {
            if (item.compareTo(maximum) > 0) {
                maximum = item;
            }
        }
        return maximum;
    }

    static double sum(List<? extends Number> source) {
        double total = 0;
        for (Number value : source) {
            total += value.doubleValue();
        }
        return total;
    }

    static void addTwo(List<? super Integer> target) {
        target.add(2);
        target.add(4);
    }

    public static void main(String[] args) {
        System.out.println(largest(List.of(3, 8, 5)));
        System.out.println(sum(List.of(1, 2, 3)));

        List<Number> destination = new ArrayList<>();
        addTwo(destination);
        System.out.println(destination);
    }
}
~~~

Lưu thành **BoundsAndVarianceDemo.java**, chạy:

~~~bash
javac -encoding UTF-8 BoundsAndVarianceDemo.java
java BoundsAndVarianceDemo
~~~

Stdout:

~~~text
8
6.0
[2, 4]
~~~

Giải thích: trong largest, T là một kiểu cụ thể thống nhất được suy luận là Integer và có compareTo; còn trong sum, dấu **? extends Number** cho phép nhận List<Integer>, List<Double> hoặc List<Number>. Có thể đọc phần tử tối thiểu như Number, nhưng không thể thêm một Integer mới một cách an toàn vì kiểu phần tử thực tế có thể là Double. Trong addTwo, **? super Integer** có thể đại diện cho Integer, Number hoặc Object; vì vậy thêm Integer an toàn, nhưng lấy phần tử ra chỉ bảo đảm kiểu Object.

Quy tắc gợi nhớ **PECS** (Producer Extends, Consumer Super): nguồn sản sinh phần tử dùng **? extends T** khi phù hợp; đích tiêu thụ phần tử dùng **? super T**. Đây là hướng dẫn thiết kế API, không phải một luật cú pháp bắt mọi danh sách chỉ được đọc hoặc chỉ được ghi. Với **List<?>**, có thể đọc dưới kiểu Object; không thể thêm phần tử khác null vì kiểu thực tế chưa biết.

Nếu một phương thức cần **liên kết cùng một kiểu** giữa nhiều tham số hoặc giữa đầu vào và đầu ra, type variable có tên thường biểu đạt được quan hệ tốt hơn wildcard. Ví dụ **static <T> T choose(T a, T b)** trả về kiểu được compiler suy luận từ cả hai đối số; một chữ ký chỉ dùng **?** không thể dùng wildcard làm tên kiểu trả về độc lập.

## 9. Bất biến (invariance): không suy diễn quan hệ sai

Mặc dù **Integer extends Number**, điều đó **không** có nghĩa **List<Integer> extends List<Number>**. Generic parameterization mặc định là *invariant*.

Đoạn sau không biên dịch:

~~~java
import java.util.List;

// Chỉ minh họa lỗi kiểu; không phải chương trình runnable.
List<Integer> integers = List.of(1, 2);
List<Number> numbers = integers;  // Compile-time error
~~~

Nếu phép gán trên được cho phép, ai đó có thể thêm Double vào numbers rồi đọc nó như Integer từ integers, làm mất an toàn kiểu. Khi muốn *đọc Number từ nhiều loại danh sách*, dùng **List<? extends Number>**. Khi muốn *ghi Integer vào nhiều loại danh sách đích*, dùng **List<? super Integer>**.

Tương tự, **List<String>** không phải subtype của **List<Object>**, dù String là subtype của Object. Một phương thức nhận **List<Object>** được phép thêm bất kỳ Object nào; phương thức đó không thể làm như vậy với một List<String>.

Ví dụ đối chiếu những chữ ký:

| Chữ ký | Nhận List<Integer>? | Có thể thêm Integer? | Kiểu đọc tối thiểu |
| --- | --- | --- | --- |
| List<Integer> | Có | Có (nếu list cho phép sửa) | Integer |
| List<Number> | Không | Có | Number |
| List<? extends Number> | Có | Không, trừ null | Number |
| List<? super Integer> | Có | Có (nếu list cho phép sửa) | Object |
| List<?> | Có | Không, trừ null | Object |

Khả năng biên dịch thao tác **add** không bảo đảm đối tượng danh sách có thể thay đổi ở runtime: List.of(...) vẫn ném UnsupportedOperationException nếu gọi add, dù tham số kiểu hợp lệ.

## 10. Type inference: khi không viết rõ đối số kiểu

Java thường suy luận kiểu generic từ kiểu biến nhận, tham số được truyền, ràng buộc generic và ngữ cảnh biểu thức.

~~~java
java.util.List<String> a = new java.util.ArrayList<String>();
java.util.List<String> b = new java.util.ArrayList<>();
java.util.List<Integer> c = java.util.List.of(1, 2, 3);
java.util.Map<String, java.util.List<Integer>> d = new java.util.HashMap<>();
~~~

Hai dòng đầu tạo cùng kiểu tham số hóa **List<String>** ở phía biến; dòng thứ hai dùng diamond để tránh lặp String. Trong **List.of(1, 2, 3)**, các literal int được boxing thành Integer và generic factory suy luận kiểu phù hợp. Kiểu tĩnh của biến bên trái vẫn quan trọng: ví dụ biến khai báo **List<String>** không tự trở thành **ArrayList<String>**, dù đối tượng là ArrayList.

Có thể chỉ rõ type argument khi gọi generic method nếu cần:

~~~java
String x = java.util.Objects.<String>requireNonNull("ok");
~~~

Phần **<String>** sau dấu chấm ở đây là **explicit method type argument**, không khai báo type parameter. Tương tự, **var names = java.util.List.of("A", "B");** suy luận kiểu biến từ initializer, không loại bỏ quy tắc generic. Dùng type inference khi rõ nghĩa; viết rõ kiểu khi nó giúp người đọc hiểu ý định hoặc giải quyết suy luận không đúng mong đợi.

## 11. Raw type và type erasure

Một **raw type** dùng tên generic mà không truyền type arguments, chẳng hạn **List** thay vì **List<String>** hoặc **List<?>**. Raw type chủ yếu dành cho tương thích mã Java cũ, bỏ qua phần kiểm tra kiểu cần thiết và có thể gây cảnh báo *unchecked*.

~~~java
// Minh họa rủi ro, không khuyến khích chạy:
java.util.List<String> safe = new java.util.ArrayList<>();
java.util.List raw = safe;      // Raw type
raw.add(123);                  // Unchecked call; gây heap pollution
String value = safe.get(0);    // ClassCastException khi chạy
~~~

Khác biệt với **List<?>**: List<?> vẫn thể hiện một danh sách có kiểu phần tử chưa biết và compiler ngăn thêm giá trị tùy ý; raw List làm mất nhiều kiểm tra đó. Hãy tránh raw type trong code mới; nếu phải làm việc với API cũ, giới hạn vùng unchecked và kiểm tra dữ liệu ở biên.

**Type erasure** là cơ chế Java dùng để hiện thực generics. Khi biên dịch, phần lớn type variables được thay bằng cận trên (hoặc Object nếu không có cận), compiler thêm cast cần thiết và có thể tạo bridge method để giữ tính đa hình. Do đó generic không tạo class riêng cho mỗi type argument.

Lưu thành **ErasureDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ErasureDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        System.out.println(words.getClass().equals(counts.getClass()));
    }
}
~~~

Chạy:

~~~bash
javac ErasureDemo.java
java ErasureDemo
~~~

Stdout:

~~~text
true
~~~

Hai đối tượng đều là ArrayList ở runtime. Tuy nhiên, **đừng kết luận toàn bộ thông tin generic đều bị xóa khỏi mọi metadata**: chữ ký generic được lưu trong một số metadata của class/method và reflection có thể đọc chúng. Điều thường không thể làm chỉ từ một đối tượng ArrayList bất kỳ là khôi phục chắc chắn type argument String hay Integer của biến đã trỏ tới nó.

Hệ quả thực hành:

- Không viết **new T()** hoặc **new T[10]** với một type variable thông thường: runtime không có class cụ thể của T để khởi tạo trực tiếp. Thường truyền **Supplier<T>** hay **Class<T>** hoặc một factory nếu cần tạo đối tượng.
- Không tạo trực tiếp **new List<String>[10]**: array kiểm tra thành phần ở runtime trong khi parameterized type này không reifiable. Có thể dùng collection of collections thay thế.
- Không dùng **List<String>.class** hoặc **instanceof List<String>** để kiểm tra đối số kiểu của một instance. **List.class** và **instanceof List<?>** là những hình thức hợp lệ trong tình huống tương ứng.
- Không khai báo hai overload chỉ khác nhau bởi **List<String>** và **List<Integer>** cùng vị trí tham số vì sau erasure chúng có cùng signature.
- Cảnh báo **unchecked** là dấu hiệu compiler không chứng minh được an toàn kiểu; đừng tùy tiện che bằng @SuppressWarnings khi chưa kiểm soát cast/nguồn dữ liệu.

## 12. Lỗi nhận diện thường gặp và cách sửa

**Nhầm type parameter với type argument.** Trong **class Result<T>**, T được khai báo; trong **Result<String>**, String được truyền vào. Trong **List<T>** xuất hiện bên trong khai báo Result<T>, T đã được khai báo ở ngoài và đang đóng vai trò đối số cho List.

**Tưởng T là giá trị runtime.** T là tên kiểu ở thời gian biên dịch. Nó không giữ đối tượng Class tương ứng; cần một **Class<T>**, Supplier<T> hoặc factory được truyền riêng khi API đòi hỏi thông tin tạo/kiểm tra runtime.

**Dùng type variable ngoài scope.** Viết static T transform(T x) trong lớp Generic<T> là không hợp lệ nếu T chỉ thuộc lớp. Sửa thành **static <U> U transform(U x)** hoặc dùng method instance với T thuộc lớp.

**Nhầm wildcard với khai báo tham số kiểu.** **List<?>** biểu diễn một dạng sử dụng danh sách, không khai báo tên để dùng lại. **<T> void copy(List<T> a, List<T> b)** liên kết hai danh sách cùng kiểu T; trong khi **void inspect(List<?> a)** thích hợp khi chỉ cần một danh sách có kiểu bất kỳ.

**Dùng List<Object> thay cho List<?>.** Hàm chỉ cần duyệt mọi loại danh sách có thể nhận List<?>; List<Object> không nhận List<String>. Muốn thao tác với một kiểu phần tử chung, hãy cân nhắc generic method hoặc bounded wildcard.

**Tin rằng diamond thay đổi ý nghĩa kiểu.** **new Box<>()** không tạo raw Box; nó yêu cầu compiler suy luận type arguments từ ngữ cảnh. **new Box()** mới là lời gọi constructor qua raw type trong ngữ cảnh đó.

**Bỏ qua khả năng thay đổi của collection.** Type argument mô tả kiểu phần tử, không mô tả danh sách có sửa được hay không. Ví dụ List.of trả về unmodifiable list.

**Bỏ qua cảnh báo unchecked.** Một phép gán/gọi thông qua raw API có thể biên dịch nhưng thất bại muộn với ClassCastException; xử lý ở nơi chuyển dữ liệu từ kiểu không bảo đảm sang kiểu generic cụ thể.

## 13. Quy trình đọc một chữ ký generic

Khi gặp một API phức tạp, hãy đọc theo thứ tự:

1. Tìm **phần khai báo tham số kiểu**: ví dụ **<T extends Number>** hoặc **<K, V>**. Xác định scope (class hay method) và bounds.
2. Tìm nơi type variable xuất hiện trong **tham số đầu vào, kiểu trả về và kiểu lồng nhau** để biết API ràng buộc các vị trí ra sao.
3. Tại điểm gọi, xác định **type arguments thực** hoặc kiểu mà compiler sẽ suy luận.
4. Xem **parameterized type toàn phần** và kiểm tra invariance, wildcard upper/lower bound nếu có.
5. Phân biệt điều **compiler bảo đảm** với điều cần kiểm tra ở runtime (erasure, raw type, collection mutability).

Ví dụ chữ ký:

~~~java
static <T extends Number> void collect(
        java.util.List<? extends T> input,
        java.util.List<? super T> output) {
    output.addAll(input);
}
~~~

- **<T extends Number>**: khai báo type parameter T, cận trên Number.
- **List<? extends T>**: parameterized type dùng upper-bounded wildcard, cho phép đọc những phần tử được bảo đảm tương thích T.
- **List<? super T>**: parameterized type dùng lower-bounded wildcard, là đích có thể nhận T.
- **T** xuất hiện trong wildcard bounds là *type variable* đã khai báo bên ngoài; nó không phải một type parameter mới.
- **output.addAll(input)** hợp lệ do wildcard và signature của addAll phối hợp để kiểm tra mối quan hệ kiểu.

## 14. Tự kiểm tra

**Câu 1.** Trong **class Mapper<S, R>**, phần nào là type parameters? Trong **Mapper<String, Integer>**, phần nào là type arguments và phần nào là parameterized type?

**Đáp án:** S và R là type parameters; chúng tạo type variables S, R. String và Integer là type arguments; toàn bộ Mapper<String, Integer> là parameterized type.

**Câu 2.** Tại sao **List<Number> n = new ArrayList<Integer>();** không biên dịch dù Integer kế thừa Number?

**Đáp án:** List bất biến theo type argument; cần List<Number> thực sự, hoặc dùng **List<? extends Number>** nếu mục tiêu là đọc Number từ danh sách Integer.

**Câu 3.** Trong **static <T> List<T> singleton(T value)**, T ở ba vị trí là gì?

**Đáp án:** T trong **<T>** là tên type parameter được khai báo; T trong **List<T>** là type variable đang được dùng làm type argument của List; T ở **(T value)** là type variable dùng làm kiểu của tham số value. Toàn bộ List<T> là parameterized type.

**Câu 4.** Phương thức muốn thêm Integer vào cả List<Integer> lẫn List<Number> cần tham số nào?

**Đáp án:** **List<? super Integer>**; vì cả Integer và Number đều là supertype (theo nghĩa bằng hoặc rộng hơn) của Integer. Phương thức có thể gọi add(Integer), nhưng không được giả định phần tử đọc ra chắc chắn là Integer.

**Câu 5.** So sánh **List**, **List<?>** và **List<String>**.

**Đáp án:** List là raw type thiếu type arguments và làm giảm type safety; List<?> là parameterized type với wildcard chưa biết kiểu phần tử; List<String> là parameterized type có đối số String và compiler kiểm tra các phần tử theo String.

Khi đã phân biệt rõ *khai báo tham số*, *tên biến kiểu*, *đối số truyền vào* và *kiểu được tạo thành*, các khái niệm tiếp theo như generic API, giới hạn kiểu, variance qua wildcard và type erasure trở nên dễ đọc hơn: chúng đều xây trên quan hệ giữa bốn thành phần cốt lõi này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-005">Generics kết hợp với lớp, giao diện, phương thức và collection</a>

<details>
<summary>Xem chi tiết</summary>

# Generics kết hợp với lớp, giao diện, phương thức và collection trong Java

Generics cho phép lớp, giao diện và phương thức nhận **kiểu dữ liệu như một tham số**. Nhờ đó, cùng một cấu trúc hoặc thuật toán có thể phục vụ nhiều kiểu mà vẫn được trình biên dịch kiểm tra tính đúng đắn. Trong Java Core, generics đặc biệt quan trọng khi thiết kế `List<E>`, `Set<E>`, `Map<K, V>`, các lớp bao dữ liệu, giao diện repository và phương thức xử lý collection.

Bài này sử dụng cú pháp tương thích **Java 17+**. Các đoạn mã ngắn dùng để minh họa từng quy tắc; cuối bài có chương trình `GenericsGuide.java` độc lập, biên dịch và chạy được.

## 1. Tư duy nền tảng: tham số kiểu thay cho `Object`

Một lớp chỉ dùng `Object` chứa được nhiều loại giá trị, nhưng người gọi phải ép kiểu và dễ gặp `ClassCastException` khi chạy:

```java
// Minh họa cách viết cũ, chưa có generics.
class ObjectBox {
    Object value;
    ObjectBox(Object value) { this.value = value; }
    Object get() { return value; }
}

// String name = (String) new ObjectBox(123).get();
// Biên dịch được, nhưng ép Integer thành String sẽ lỗi khi chạy.
```

Với lớp `Box<T>`, `T` đại diện cho kiểu do nơi sử dụng chọn:

```java
// Đoạn minh họa, được đặt trong một tệp Java thích hợp.
final class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

Box<String> title = new Box<>("Java");
String text = title.get();           // Không cần ép kiểu.
Box<Integer> count = new Box<>(12);
Integer number = count.get();
// Box<String> wrong = new Box<>(12); // Lỗi ngay khi biên dịch.
```

Trong `Box<String>`, tham số `T` được thay bằng `String` trong cách trình biên dịch kiểm tra lời gọi. Việc kiểm tra diễn ra **lúc biên dịch**; đây không phải cam kết Java tạo riêng một lớp runtime mới cho mỗi `T`.

Quy ước tên thường gặp: `T` (type), `E` (element), `K` (key), `V` (value), `R` (result), `S` (source). Các chữ này là tên tham số kiểu, không phải từ khóa. Nên dùng tên dễ hiểu nếu cần nhiều tham số cùng lúc.

**Kiểu tham số phải là kiểu tham chiếu**: dùng `List<Integer>` thay vì `List<int>`, `Box<Boolean>` thay vì `Box<boolean>`. Autoboxing có thể chuyển `int` thành `Integer` khi thêm phần tử.

## 2. Generics trên lớp: một hoặc nhiều tham số kiểu

Khai báo `class ClassName<T>` đặt `T` trong phạm vi các thành viên instance của lớp. Khi cần quan hệ giữa hai kiểu, khai báo nhiều tham số:

```java
// Minh họa một lớp bất biến có hai tham số kiểu.
final class Pair<L, R> {
    private final L left;
    private final R right;

    Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    L left() { return left; }
    R right() { return right; }
}

Pair<String, Integer> score = new Pair<>("Lan", 9);
String student = score.left();
int point = score.right();
```

`L` và `R` có thể giống hoặc khác nhau. Toán tử kim cương `<>` ở `new Pair<>(...)` cho phép trình biên dịch suy luận kiểu từ ngữ cảnh. Từ Java 16, có thể diễn đạt cặp dữ liệu ngắn hơn bằng `record Pair<L, R>(L left, R right) {}` khi phù hợp với thiết kế dữ liệu bất biến.

Một lớp generic có thể kế thừa hoặc triển khai cấu trúc generic theo hai cách:

```java
// Tiếp tục chuyển tham số kiểu từ lớp cha xuống lớp con.
class NamedBox<T> extends BoxBase<T> {
    NamedBox(T item) { super(item); }
}

// Cố định kiểu của lớp cha.
class StringBox extends BoxBase<String> {
    StringBox(String item) { super(item); }
}

class BoxBase<T> {
    private final T item;
    BoxBase(T item) { this.item = item; }
    T get() { return item; }
}
```

Trong `NamedBox<Integer>`, kiểu của `BoxBase<T>` tương ứng là `Integer`; trong `StringBox`, kiểu `String` đã cố định. Một lớp generic không được dùng chính tham số kiểu của lớp trong trường `static` hoặc phương thức `static` nếu phương thức đó không tự khai báo tham số kiểu: `T` phụ thuộc từng cách sử dụng lớp, còn thành viên `static` thuộc về lớp chung.

### Giới hạn tham số kiểu bằng `extends`

Nếu thuật toán cần thao tác chỉ có trên một loại đối tượng, đặt **upper bound**:

```java
// Minh họa phương thức generic có bound.
static <T extends Number> double total(java.util.Collection<T> items) {
    double sum = 0;
    for (T item : items) {
        sum += item.doubleValue();
    }
    return sum;
}
```

`T extends Number` nhận `Integer`, `Long`, `Double`... và cho phép gọi `doubleValue()`. Dù `Number` là lớp, từ khóa vẫn là `extends`; với interface cũng dùng `extends`, ví dụ `<T extends Comparable<T>>`.

Có thể đặt nhiều bound: `<T extends Number & Comparable<T>>`. Nếu có bound là lớp thì lớp phải đứng trước các interface. Bound không biến `List<Integer>` thành `List<Number>`; đó là vấn đề phương sai, sẽ xét riêng ở mục 6.

## 3. Generics trên giao diện: hợp đồng API có kiểu

Giao diện generic mô tả thao tác độc lập với loại khóa và dữ liệu, đồng thời giữ liên hệ giữa đầu vào và kết quả:

```java
// Các đoạn trong khối này minh họa cùng một API.
interface Repository<ID, E> {
    void save(ID id, E entity);
    java.util.Optional<E> findById(ID id);
}

final class InMemoryRepository<ID, E> implements Repository<ID, E> {
    private final java.util.Map<ID, E> data = new java.util.HashMap<>();

    @Override
    public void save(ID id, E entity) {
        data.put(id, entity);
    }

    @Override
    public java.util.Optional<E> findById(ID id) {
        return java.util.Optional.ofNullable(data.get(id));
    }
}

Repository<Long, String> repo = new InMemoryRepository<>();
repo.save(1L, "Lan");
String found = repo.findById(1L).orElse("Không tìm thấy");
// repo.save("sai kiểu khóa", "Lan"); // Lỗi biên dịch.
```

`ID` bảo đảm khóa dùng ở `save` và `findById` có cùng kiểu; `E` bảo đảm giá trị lưu và giá trị trong `Optional<E>` có cùng kiểu. `Optional<E>` biểu diễn khả năng không có kết quả, giúp API tránh trả về `null` trực tiếp.

Một lớp cũng có thể cố định kiểu ngay khi triển khai, chẳng hạn `UserRepository implements Repository<Long, User>`. Khi đó các chữ ký phương thức phải tương ứng với `Long` và `User`.

Các interface chuẩn như `Comparable<T>`, `Comparator<T>`, `Iterable<T>`, `Function<T, R>` hay `Predicate<T>` đều áp dụng cùng nguyên lý: khai báo mối quan hệ kiểu tại nơi thiết kế, để nơi sử dụng được kiểm tra nhất quán.

## 4. Generics trên phương thức: phạm vi kiểu thuộc từng lời gọi

Một phương thức generic khai báo `<T>` **trước kiểu trả về**, độc lập với việc lớp chứa nó có generic hay không:

```java
// Minh họa các phương thức trong một lớp tiện ích.
static <T> T first(java.util.List<T> items) {
    if (items.isEmpty()) {
        throw new java.util.NoSuchElementException("empty list");
    }
    return items.get(0);
}

static <A, B> java.util.Map.Entry<A, B> entry(A key, B value) {
    return new java.util.AbstractMap.SimpleImmutableEntry<>(key, value);
}
```

Khi gọi `first(List<String>)`, kiểu trả về được suy luận là `String`; khi gọi `first(List<Integer>)`, kiểu trả về là `Integer`. Phương thức `static` hoàn toàn có thể generic vì nó **tự khai báo** `<T>`.

```java
// Minh họa lời gọi trong phương thức/chương trình Java.
String a = first(java.util.List.of("A", "B"));
Integer b = first(java.util.List.of(3, 4));
java.util.Map.Entry<String, Integer> grade = entry("Lan", 9);
```

Trình biên dịch thường suy luận được tham số kiểu, nhưng có thể chỉ định tường minh khi cần: `Collections.<String>emptyList()`. Cần phân biệt:

- `class Box<T>`: `T` thuộc lớp, được cố định theo từng `Box<String>` hoặc `Box<Integer>`.
- `<T> T first(List<T> xs)`: `T` thuộc riêng phương thức và được suy luận cho mỗi lời gọi.
- `static <T> ...`: hợp lệ; `static T get()` bên trong `class Box<T>` thì không hợp lệ vì `T` thuộc instance context của lớp.

Khi cần so sánh phần tử theo thứ tự tự nhiên, bound `<T extends Comparable<? super T>>` hữu ích hơn việc ép mọi phần tử về `Object`:

```java
// Minh họa một phương thức tổng quát.
static <T extends Comparable<? super T>> T maximum(
        java.util.List<? extends T> values) {
    if (values.isEmpty()) {
        throw new java.util.NoSuchElementException("empty list");
    }
    T best = values.get(0);
    for (T value : values) {
        if (value.compareTo(best) > 0) {
            best = value;
        }
    }
    return best;
}
```

`Comparable<? super T>` chấp nhận cả trường hợp đối tượng `T` triển khai khả năng so sánh ở một lớp cha của `T`. Trong ứng dụng thực tế, ưu tiên hàm thư viện `Collections.max(...)` nếu đủ yêu cầu.

## 5. Generics kết hợp Java Collections Framework

Collection là nơi người học gặp generics thường xuyên nhất. Tham số kiểu biến ràng buộc dữ liệu thành một phần của API:

| Cấu trúc | Khai báo điển hình | Đặc điểm |
| --- | --- | --- |
| `List<E>` | `List<String>` | Có thứ tự vị trí, cho phép phần tử trùng; truy cập bằng chỉ số. |
| `Set<E>` | `Set<Long>` | Không chứa phần tử trùng theo quy tắc `equals`/`hashCode` hoặc comparator của triển khai. |
| `Queue<E>` | `Queue<Task>` | Mô hình hàng đợi; thứ tự lấy phụ thuộc triển khai. |
| `Collection<E>` | `Collection<Number>` | Giao diện chung cho nhóm phần tử; không có truy cập chỉ số chung. |
| `Map<K, V>` | `Map<String, Integer>` | Ánh xạ khóa đến giá trị; **`Map` không kế thừa `Collection`**. |

Ví dụ thao tác với collection có kiểu:

```java
// Minh họa lời gọi khi đã import java.util.*.
List<String> names = new ArrayList<>();
names.add("Lan");
names.add("Minh");
// names.add(42); // Lỗi biên dịch: phần tử phải là String.
String firstName = names.get(0);

Set<String> tags = new LinkedHashSet<>(List.of("java", "core", "java"));
// LinkedHashSet giữ thứ tự chèn, kết quả khi in: [java, core].

Map<String, List<Integer>> scores = new HashMap<>();
scores.computeIfAbsent("Lan", key -> new ArrayList<>()).add(9);
scores.computeIfAbsent("Lan", key -> new ArrayList<>()).add(8);
List<Integer> lanScores = scores.get("Lan"); // [9, 8]
```

`Map<String, List<Integer>>` là **generic lồng nhau**: khóa phải là `String`, mỗi giá trị là danh sách `Integer`. `computeIfAbsent` trả về giá trị đã có hoặc tạo giá trị mới khi khóa vắng mặt (hay đang ánh xạ tới `null`), rồi có thể gọi `add` trên danh sách tương ứng.

Một số tính chất của factory và implementation ảnh hưởng trực tiếp tới cách viết ví dụ:

- `List.of(...)`, `Set.of(...)`, `Map.of(...)` tạo collection **không sửa được**; các factory này không chấp nhận phần tử `null` (và `Set.of` không chấp nhận phần tử trùng).
- `new ArrayList<>(List.of(...))` tạo danh sách có thể sửa. `Arrays.asList(...)` tạo danh sách kích thước cố định: có thể thay phần tử bằng `set`, nhưng không `add/remove` để đổi kích thước.
- `HashMap` và `HashSet` không cam kết thứ tự duyệt; `LinkedHashMap` và `LinkedHashSet` giữ thứ tự chèn; `TreeMap` và `TreeSet` sắp xếp theo thứ tự hoặc comparator phù hợp.
- `Map.get(key)` trả `null` khi không có ánh xạ, nhưng cũng có thể trả `null` nếu map cho phép giá trị `null`. Dùng `containsKey` khi cần phân biệt.

Khi chỉ cần dùng hành vi của giao diện, nên khai báo phía trái là `List<E>`, `Set<E>` hoặc `Map<K,V>`, và chọn lớp triển khai ở phía phải. Cách này giữ API dễ thay đổi.

## 6. Invariance, wildcard và nguyên tắc PECS

Java generics **bất biến (invariant)**: dù `Integer extends Number`, `List<Integer>` **không phải** `List<Number>`:

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // Lỗi biên dịch.
```

Nếu phép gán trên được phép thì `numbers.add(3.14)` sẽ đưa `Double` vào danh sách vốn chỉ nhận `Integer`. Thay vì nới quy tắc một cách không an toàn, Java cung cấp wildcard:

| Kiểu tham số | Có thể đọc an toàn như | Có thể thêm trực tiếp |
| --- | --- | --- |
| `List<T>` | `T` | Giá trị `T` |
| `List<? extends Number>` | `Number` | Không thêm giá trị `Number` cụ thể; có thể thêm `null` |
| `List<? super Integer>` | `Object` | `Integer` và lớp con của `Integer` (thực tế `Integer` là final); `null` |
| `List<?>` | `Object` | Không thêm phần tử không phải `null` |

### `? extends T`: nguồn để đọc

```java
// Minh họa phương thức đọc từ các loại collection số.
static double sumNumbers(List<? extends Number> source) {
    double result = 0;
    for (Number number : source) {
        result += number.doubleValue();
    }
    return result;
}

// sumNumbers(List.of(1, 2, 3));     // List<Integer>
// sumNumbers(List.of(1.5, 2.5));     // List<Double>
```

`source` có thể là `List<Integer>`, `List<Double>` hoặc `List<Number>`. Không thể `source.add(1)` vì kiểu phần tử thực tế của danh sách có thể là `Double`. Ngay cả `Number` cũng chưa đủ để quyết định kiểu có thể thêm.

### `? super T`: đích để ghi

```java
// Minh họa phương thức ghi.
static void addDefaults(List<? super Integer> target) {
    target.add(1);
    target.add(2);
}

// addDefaults(new ArrayList<Integer>());
// addDefaults(new ArrayList<Number>());
// addDefaults(new ArrayList<Object>());
```

Một đích `List<? super Integer>` nhận được `Integer`, nhưng khi lấy phần tử ra chỉ có thể giả định `Object`, vì danh sách có thể khai báo là `List<Object>`.

**PECS = Producer Extends, Consumer Super**: tham số cung cấp đối tượng kiểu `T` nên cân nhắc `? extends T`; tham số nhận đối tượng kiểu `T` nên cân nhắc `? super T`.

Ví dụ tổng hợp cả hai phía:

```java
// Minh họa phương thức sao chép không thay đổi nguồn.
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}

// List<Integer> src = List.of(1, 2);
// List<Number> dst = new ArrayList<>();
// appendAll(src, dst); // dst chứa [1, 2].
```

Nếu hai tham số phải có **quan hệ cùng một kiểu** hoặc phương thức cần **trả về đúng kiểu đó**, dùng tên tham số `<T>` như `appendAll` và `first`. Nếu chỉ đọc/in ra dữ liệu mà không cần nối quan hệ giữa các vị trí, `List<?>` thường đơn giản hơn:

```java
static void printAll(List<?> values) {
    for (Object item : values) {
        System.out.println(item);
    }
}
```

Wildcard không phải cơ chế "cho phép mọi thao tác". Nó mô tả những gì API biết về kiểu, nên trình biên dịch giới hạn thao tác đọc và ghi cho an toàn.

## 7. Collections và hàm nhận/trả về generic: thiết kế API thực tế

Giả sử cần chuyển đổi một danh sách `S` thành danh sách `R`. Chữ ký linh hoạt thường kết hợp method type parameter với wildcard của `Function`:

```java
// Minh họa phương thức; import java.util.* và java.util.function.*.
static <S, R> List<R> mapEach(
        Collection<? extends S> source,
        Function<? super S, ? extends R> mapper) {
    List<R> result = new ArrayList<>();
    for (S element : source) {
        result.add(mapper.apply(element));
    }
    return result;
}

// List<Integer> lengths = mapEach(List.of("a", "java"), String::length);
// lengths bằng [1, 4].
```

Nguồn sinh ra `S`, hàm chuyển đổi có thể nhận `S` hoặc một kiểu cha của `S`, và kết quả hàm là `R` hoặc kiểu con của `R`. Đây là lý do `Function<? super S, ? extends R>` xuất hiện trong nhiều API chuẩn.

Khi trả về collection từ public API, cân nhắc:

1. Nếu người gọi chỉ cần đọc, trả kiểu giao diện như `List<E>` và xác định rõ có thể sửa hay không; ví dụ `List.copyOf(source)` tạo bản sao không sửa được và không chấp nhận `null`.
2. Nếu phương thức chỉ duyệt phần tử, nhận `Iterable<? extends T>` hoặc `Collection<? extends T>` tùy thao tác thực sự cần; tránh ép mọi thứ thành `ArrayList<T>`.
3. Nếu cần giữ quan hệ key/value, dùng `Map<K,V>` và không thay bằng `Map<Object,Object>`.
4. Nếu cần sắp xếp, cân nhắc `Comparator<? super T>` để chấp nhận comparator của kiểu cha, hoặc bound `Comparable<? super T>` cho thứ tự tự nhiên.
5. Chọn tham số kiểu có ý nghĩa, tránh khai báo `<T>` không xuất hiện trong đầu vào/kết quả hoặc không mang quan hệ kiểu nào.

Ví dụ biến đổi bằng Stream cũng giữ generic xuyên suốt:

```java
// Minh họa khi đã import java.util.*.
List<String> words = List.of("A", "Java");
List<Integer> lengths = words.stream()
        .map(String::length)
        .toList(); // [1, 4], List không sửa được từ Stream.toList().
```

Ở đây `Stream<String>` đi qua `map` thành `Stream<Integer>`. `toList()` (Java 16+) tạo danh sách không sửa được; nếu cần bản có thể sửa, dùng `collect(Collectors.toCollection(ArrayList::new))`.

## 8. Type erasure: generics tồn tại như thế nào lúc chạy?

Java triển khai phần lớn generics bằng **type erasure** (xóa thông tin tham số kiểu trong biểu diễn runtime thông thường). Trình biên dịch kiểm tra kiểu, chèn cast cần thiết và ánh xạ tham số kiểu tới bound thích hợp; ví dụ `T` không có bound thường xóa về `Object`, còn `T extends Number` thường xóa về `Number`. Trình biên dịch có thể tạo **bridge method** để giữ đúng đa hình sau erasure.

Hệ quả quan trọng:

- `new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass()` là `true`: hai cách tham số hóa dùng cùng lớp runtime `ArrayList`.
- Không thể kiểm tra `value instanceof List<String>`; có thể kiểm tra `value instanceof List<?>` vì lúc chạy không xác nhận được tham số `String`.
- Không thể viết `new T()`, `new T[10]` hoặc trực tiếp tạo mảng generic `new List<String>[10]` trong những trường hợp thông thường. Nếu cần tạo đối tượng `T`, có thể truyền factory `Supplier<T>` hoặc `Class<T>` phù hợp.
- Không thể overload chỉ theo tham số kiểu bị xóa giống nhau, ví dụ hai phương thức `process(List<String>)` và `process(List<Integer>)` cùng tên sẽ xung đột erasure.
- `static` field không thể có kiểu `T` lấy từ tham số kiểu của class; mỗi cách tham số hóa không có bản sao field `static` riêng.

**Erasure không có nghĩa toàn bộ dấu vết generic biến mất khỏi metadata.** Chẳng hạn reflection có thể đọc kiểu khai báo generic ở trường, phương thức và lớp cha qua các API như `getGenericType()` hoặc `getGenericSuperclass()`; tuy nhiên tham số cụ thể của một `new ArrayList<String>()` thông thường không trở thành một đặc tính runtime có thể kiểm tra bằng `instanceof`.

### Raw type và cảnh báo unchecked

```java
// Minh họa mã cũ, không nên đưa vào API mới.
List raw = new ArrayList();
raw.add("Java");
raw.add(42);
List<String> names = raw; // Cảnh báo unchecked conversion.
String second = names.get(1); // ClassCastException khi chạy.
```

Raw type (`List` thay vì `List<String>`) bỏ qua một phần kiểm tra generic, thường chỉ cần khi tương tác code cũ. **`List<?>` khác raw `List`**: `List<?>` vẫn giữ ràng buộc kiểu an toàn và không cho thêm đối tượng bất kỳ. Không dùng `@SuppressWarnings("unchecked")` để che lỗi thiết kế; chỉ dùng khi có lý do kiểm chứng được và giới hạn phạm vi cảnh báo.

## 9. Ví dụ hoàn chỉnh: lớp + interface + phương thức + collection

Tạo tệp **`GenericsGuide.java`** chứa đúng chương trình sau. Các lớp lồng `static` ở đây được dùng để có một tệp chạy độc lập và cho thấy rõ mối liên hệ giữa các phần.

```java
import java.util.*;
import java.util.function.Function;

public class GenericsGuide {
    static final class Box<T> {
        private final T value;

        Box(T value) { this.value = value; }

        T get() { return value; }

        <R> Box<R> map(Function<? super T, ? extends R> f) {
            return new Box<>(f.apply(value));
        }
    }

    interface Repository<ID, E> {
        void save(ID id, E value);
        Optional<E> findById(ID id);
    }

    static final class MemoryRepository<ID, E> implements Repository<ID, E> {
        private final Map<ID, E> data = new LinkedHashMap<>();

        @Override
        public void save(ID id, E value) {
            data.put(id, value);
        }

        @Override
        public Optional<E> findById(ID id) {
            return Optional.ofNullable(data.get(id));
        }
    }

    static <T> T first(List<T> values) {
        if (values.isEmpty()) {
            throw new NoSuchElementException("empty list");
        }
        return values.get(0);
    }

    static <T extends Number> double sum(Collection<T> values) {
        double result = 0;
        for (T value : values) {
            result += value.doubleValue();
        }
        return result;
    }

    static <T> void appendAll(List<? extends T> source, List<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    public static void main(String[] args) {
        Box<String> word = new Box<>("Java");
        Box<Integer> length = word.map(String::length);
        System.out.println("Box: " + word.get() + " -> " + length.get());

        Repository<Long, String> users = new MemoryRepository<>();
        users.save(10L, "Lan");
        System.out.println("Repository: " + users.findById(10L).orElse("unknown"));

        List<Integer> scores = List.of(2, 3, 5);
        System.out.println("First: " + first(scores));
        System.out.println("Sum: " + sum(scores));
        List<Number> numbers = new ArrayList<>();
        appendAll(scores, numbers);
        System.out.println("Copied: " + numbers);

        Set<String> tags = new LinkedHashSet<>(List.of("java", "generics", "java"));
        System.out.println("Set: " + tags);
        Map<String, List<Integer>> grades = new LinkedHashMap<>();
        grades.put("Lan", List.of(8, 9));
        System.out.println("Map: " + grades.get("Lan"));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac GenericsGuide.java
java GenericsGuide
```

Kết quả stdout **chính xác** (đã kiểm chứng với Java 21, cú pháp hợp lệ từ Java 17):

```text
Box: Java -> 4
Repository: Lan
First: 2
Sum: 10.0
Copied: [2, 3, 5]
Set: [java, generics]
Map: [8, 9]
```

Diễn giải mối liên hệ trong chương trình:

- `Box<T>` là lớp generic: `Box<String>` chứa từ; phương thức generic `<R> map(...)` trả `Box<Integer>` khi nhận `String::length`.
- `Repository<ID,E>` là interface generic; `MemoryRepository<ID,E>` thực thi hợp đồng bằng `Map<ID,E>`; `Optional<E>` giữ kiểu dữ liệu tìm thấy.
- `first` là phương thức generic không cần bound; `sum` yêu cầu `T extends Number` vì sử dụng `doubleValue()`.
- `appendAll` dùng `? extends T` cho nguồn `List<Integer>` và `? super T` cho đích `List<Number>`. Đây là áp dụng PECS để sao chép an toàn.
- `List<Integer>`, `Set<String>`, `Map<String,List<Integer>>` chứng minh tham số kiểu xuyên suốt thao tác lưu, đọc, lặp và ánh xạ.

Lưu ý: `Set` in theo thứ tự ổn định ở ví dụ vì dùng `LinkedHashSet`; `List.of` là danh sách không sửa được nhưng vẫn đọc và sao chép bình thường.

## 10. Lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `List<Number> x = new ArrayList<Integer>()` bị lỗi | Generics invariant | Dùng `List<? extends Number>` nếu chỉ đọc, hoặc khai báo kiểu phần tử thống nhất. |
| Không `add(1)` được vào `List<? extends Number>` | Không biết kiểu con cụ thể của danh sách | Dùng `List<? super Integer>` làm đích ghi hoặc `List<Number>` khi cần lưu nhiều kiểu số. |
| `new T()` không biên dịch | Type erasure; thiếu cách tạo `T` | Truyền `Supplier<T>`, factory hoặc constructor phù hợp. |
| `List<int>` không hợp lệ | Generic cần kiểu tham chiếu | Dùng `List<Integer>`. |
| `List raw` hoạt động nhưng cảnh báo unchecked | Đã bỏ kiểm tra tham số kiểu | Chuyển thành `List<E>`, hoặc `List<?>` nếu chưa biết `E`. |
| Ép `(List<String>)` từ `List<?>` | Không chứng minh phần tử thực sự là `String` | Kiểm tra/chuyển đổi từng phần tử có chủ đích; không ép collection mù quáng. |
| `add` vào `List.of(...)` ném `UnsupportedOperationException` | Factory trả danh sách không sửa được | Tạo `new ArrayList<>(List.of(...))` nếu cần sửa. |
| `static T value` trong `class Box<T>` bị lỗi | Tham số kiểu của lớp không thuộc `static` context | Dùng một kiểu cụ thể hoặc để phương thức `static` tự khai báo `<T>`. |
| Thêm phần tử sai kiểu vào raw list làm lỗi về sau | Mất kiểm tra compile-time, gây heap pollution | Tránh raw type và unchecked cast, chỉnh API tại nguồn. |

Không nên dùng wildcard trong mọi vị trí chỉ vì nó có vẻ linh hoạt. Nếu một `List<T>` thực sự cần cả đọc lẫn thêm `T`, kiểu chính xác `List<T>` thường thể hiện hợp đồng rõ hơn. Với các phương thức công khai, đặt giới hạn ở nơi cần thiết và để phần còn lại được suy luận tự nhiên.

## 11. Tự luyện tập và kiểm tra hiểu bài

**Bài 1 — Lớp:** Viết `Result<T>` chứa `T data` và `String message`. Tạo `Result<String>` và `Result<Integer>`. Câu hỏi tự kiểm tra: vì sao getter trả `T` giúp tránh cast?

**Bài 2 — Interface:** Thiết kế `Cache<K,V>` với `put(K,V)` và `Optional<V> get(K)`. Dùng `HashMap<K,V>` triển khai; tạo `Cache<String, Long>` rồi thử truyền khóa `Integer` để quan sát lỗi biên dịch.

**Bài 3 — Method + collection:** Viết `static <T> List<T> reversed(List<? extends T> source)` tạo danh sách mới theo thứ tự đảo ngược. Kiểm tra với `List<String>` và `List<Integer>`. Không sửa nguồn; phân biệt cách hoạt động này với `Collections.reverse`, vốn thay đổi danh sách truyền vào.

**Bài 4 — Chọn chữ ký:** Cần chuyển dữ liệu `List<Integer>` sang một `List<Number>`, nên nhận `List<Integer>`, `List<Number>`, `List<? extends Number>` hay `List<? super Integer>` tại mỗi phía? Giải thích bằng thao tác đọc và ghi, sau đó so sánh lời giải với chữ ký `appendAll`.

Để tự đánh giá, một lời giải đúng cần thể hiện: **tham số kiểu truyền qua lớp/giao diện/phương thức**, **compile-time type safety**, **invariance của collection**, và **lựa chọn `extends`/`super` theo hướng dữ liệu**. Đây là nền tảng để đọc hiểu các API generic trong thư viện Java và thiết kế collection API có thể tái sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-006">Phân biệt tính tổng quát về kiểu với kế thừa và đa hình</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tính tổng quát về kiểu với kế thừa và đa hình trong Java

Generics, kế thừa và đa hình đều giúp chương trình Java linh hoạt hơn, nhưng mỗi cơ chế giải một vấn đề riêng. Khi đọc các ví dụ như `List<Dog>`, `Dog extends Animal` và `Animal animal = new Dog()`, hãy phân biệt **quan hệ kiểu**, **mức độ tổng quát của thuật toán** và **cách chọn hành vi lúc chạy**.

## 1. Định nghĩa và bản chất

**Generics (tính tổng quát về kiểu)** cho phép khai báo lớp, interface hoặc phương thức có *tham số kiểu* (`T`, `E`, `K`, `V`...). Cùng cấu trúc hoặc thuật toán dùng được với nhiều kiểu, và trình biên dịch giữ lại mối liên hệ kiểu giữa đối số, biến chứa và kết quả. Ví dụ `Box<String>` và `Box<Dog>` dùng chung định nghĩa `Box<T>`; phương thức `<T> T first(List<T> items)` trả về đúng kiểu phần tử của danh sách đầu vào. Không cần có lớp cha/con giữa `String` và `Dog` để dùng chung thuật toán generic.

**Kế thừa (inheritance)** định nghĩa quan hệ “is-a”: `Dog extends Animal` nghĩa là `Dog` là subtype của `Animal`, thừa hưởng những thành phần có thể kế thừa và có thể mở rộng/override hành vi. `implements` xác lập quan hệ một lớp hiện thực interface; interface cũng có thể `extends` interface khác. Kế thừa lớp còn có thể tái sử dụng mã nhưng chỉ nên dùng khi quan hệ phân loại thực sự đúng.

**Đa hình (polymorphism)** nghĩa là dùng một giao diện/kiểu chung để làm việc với nhiều hình thái cụ thể. Trong **đa hình subtype tại runtime**, một tham chiếu `Animal` có thể trỏ đến `Dog` hoặc `Cat`; lời gọi phương thức instance bị override, ví dụ `animal.sound()`, chạy triển khai ứng với đối tượng thực tế. Trình biên dịch kiểm tra rằng `Animal` có phương thức `sound()`; runtime quyết định override nào chạy.

Một số tài liệu xếp generics vào **đa hình tham số (parametric polymorphism)**, còn override qua cha/interface vào **đa hình subtype (subtype polymorphism)**. Đây là hai nghĩa khác nhau của từ “đa hình”. Khi so sánh ba cơ chế trong Java, “đa hình” thường ám chỉ tính đa hình hành vi qua override và dynamic dispatch.

| Câu hỏi thiết kế | Generics | Kế thừa | Đa hình subtype |
|---|---|---|---|
| Giải quyết gì? | Dùng lại cấu trúc/thuật toán, giữ quan hệ kiểu | Tổ chức subtype và hợp đồng có thể kế thừa | Gọi chung nhưng hành vi khác nhau |
| Cú pháp tiêu biểu | `class Box<T>`, `<T> T id(T x)` | `extends`, `implements` | Override phương thức instance |
| Chọn/kiểm tra chủ yếu khi nào? | Compile time; Java generics thông thường dùng type erasure | Quan hệ subtype được kiểm tra khi biên dịch | Runtime chọn override theo đối tượng |
| Có cần các subtype không? | Không | Có quan hệ subtype | Thường có nhiều implementation của lớp cha/interface |
| Có khiến `List<Dog>` thành `List<Animal>`? | Không | Không | Không |

## 2. Chương trình minh họa đủ ba cơ chế

Ví dụ chạy độc lập với Java 17 trở lên. Lưu đúng tên **`GenericVsInheritanceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class GenericVsInheritanceDemo {
    static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        String sound() {
            return "(unknown)";
        }
    }

    static final class Dog extends Animal {
        Dog(String name) {
            super(name);
        }

        @Override
        String sound() {
            return "woof";
        }
    }

    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static <T> T first(List<T> items) {
        return items.get(0);
    }

    static <T extends Animal> String soundOf(T animal) {
        return animal.sound();
    }

    static <T> void copy(List<? extends T> source, List<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = List.of(new Dog("Milu"), new Dog("Bong"));

        Dog firstDog = first(dogs);
        System.out.println("generic: " + firstDog.name());

        Animal baseRef = firstDog;
        System.out.println("polymorphism: " + baseRef.sound());
        System.out.println("bound: " + soundOf(firstDog));

        Box<Dog> box = new Box<>(firstDog);
        System.out.println("box: " + box.get().name());

        List<? extends Animal> view = dogs;
        System.out.println("wildcard: " + view.get(1).name());

        List<Animal> copied = new ArrayList<>();
        copy(dogs, copied);
        System.out.println("copy count: " + copied.size());
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac GenericVsInheritanceDemo.java
java GenericVsInheritanceDemo
```

Stdout chính xác:

```text
generic: Milu
polymorphism: woof
bound: woof
box: Milu
wildcard: Bong
copy count: 2
```

Tách từng kết quả:

1. `first(dogs)` dùng **phương thức generic**; trình biên dịch suy luận `T` là `Dog`, nên biến nhận kết quả có kiểu `Dog` và không cần cast. Nó hoạt động với `List<String>`, `List<Integer>`... mà không cần những lớp này có cha chung do người viết định nghĩa. Hàm giả định danh sách không rỗng; gọi với danh sách rỗng sẽ ném `IndexOutOfBoundsException`.
2. `Dog extends Animal` tạo quan hệ **kế thừa**. Phép gán `Animal baseRef = firstDog` là upcast hợp lệ vì `Dog` là `Animal`. Không cần generics để gán một đối tượng con vào biến kiểu cha.
3. `baseRef.sound()` in `woof` do **đa hình runtime**: biến tĩnh là `Animal`, nhưng đối tượng được tạo bởi `new Dog(...)`; bản override ở `Dog` thực thi.
4. `Box<T>` dùng **generics** để lưu và trả về cùng kiểu `T`. `Box<Dog>` không phải subclass của `Box<Animal>`. Lời gọi `box.get()` được kiểm tra kiểu trả về là `Dog` khi biên dịch.
5. `<T extends Animal>` là **ràng buộc tham số kiểu**: `soundOf` có thể gọi `animal.sound()` vì mọi `T` hợp lệ đều là subtype của `Animal`. Nó sử dụng quan hệ kế thừa sẵn có, không tạo ra lớp con mới.
6. `List<? extends Animal>` cho phép xem danh sách `List<Dog>` để đọc phần tử dưới kiểu `Animal`; phương thức generic `copy` dùng wildcard ở nguồn và đích để chép các đối tượng vào `List<Animal>` an toàn.

## 3. Bẫy quan trọng: kế thừa của phần tử không truyền sang danh sách

Giả sử có `Dog extends Animal`. Điều sau đây **không biên dịch**:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs; // Lỗi incompatible types
```

Trong Java, generic parameterized type thông thường là **invariant (bất biến)**: `Dog` là subtype của `Animal` nhưng `List<Dog>` không là subtype của `List<Animal>`. Điều này bảo vệ tính an toàn kiểu.

Hãy tưởng tượng phép gán trên được cho phép: qua biến `animals`, chương trình có thể thêm `Cat` vào danh sách `dogs`; sau đó một lệnh đọc `Dog d = dogs.get(0)` có thể nhận nhầm `Cat`. Trình biên dịch ngăn chuyện này từ đầu.

Khi cần một mức tổng quát hơn, chọn wildcard theo hướng sử dụng:

- **Đọc:** `List<? extends Animal> view = dogs;`. Biến `view` có thể đọc `Animal a = view.get(0)`. Không được `view.add(new Dog(...))`: kiểu cụ thể đứng sau `?` là chưa biết (có thể chính là `Cat`). Chỉ giá trị `null` là ngoại lệ cho thao tác thêm.
- **Ghi:** `List<? super Dog> target = new ArrayList<Animal>();`. Có thể `target.add(new Dog(...))`. Khi đọc từ `target`, kiểu bảo đảm chung là `Object`, vì đích có thể là danh sách của `Dog`, `Animal` hay `Object`.
- **Đọc và ghi đúng một kiểu:** dùng `List<Dog>` nếu cần biết chính xác cả hai chiều.
- **Kiểu không xác định:** `List<?>` có thể đọc dưới kiểu `Object` nhưng không thêm phần tử không-null. `List<?>` khác `List<Object>`: danh sách `List<Object>` cho phép thêm `String`, `Dog` hay `Object` tùy ý.

Quy tắc nhớ **PECS: Producer Extends, Consumer Super**. Nguồn *cung cấp* phần tử thì dùng `? extends T`; đích *nhận* phần tử thì dùng `? super T`. Wildcard này diễn đạt phạm vi kiểu của container, không phải tạo subtype giữa `List<Dog>` và `List<Animal>`.

## 4. Khi nào cần tham số kiểu có bound, khi nào chỉ cần lớp cha?

So sánh hai chữ ký độc lập sau:

```java
static void printSound(Animal animal) {
    System.out.println(animal.sound());
}

static <T extends Animal> T echoSound(T animal) {
    System.out.println(animal.sound());
    return animal;
}
```

- `printSound(Animal)` đủ dùng khi chỉ cần hành vi chung `sound()`, không có yêu cầu bảo toàn kiểu con của kết quả.
- `echoSound(T)` có giá trị vì **liên kết kiểu đối số với kiểu trả về**. Nếu truyền `Dog`, trình biên dịch có thể suy luận kết quả là `Dog`; nếu thay bằng `Animal echoSound(Animal)`, người gọi chỉ nhận kiểu tĩnh `Animal`.
- Bound `T extends Animal` cho phép thân phương thức dùng các thành viên hợp lệ của `Animal`. Nó không đảm bảo mọi phương thức riêng của `Dog` sẽ có trên `T`, vì `T` có thể là subtype khác.

`<T extends Animal>` là **named type parameter** có upper bound. `? extends Animal` là **wildcard** không tên. Dùng `T` khi cần diễn đạt ràng buộc giữa hai hay nhiều vị trí trong API; dùng wildcard khi không phải tham chiếu lại cùng một kiểu chưa biết. Không thêm `<T>` chỉ để hàm trông tổng quát hơn: `void log(Animal a)` rõ ràng hơn `<T extends Animal> void log(T a)` nếu `T` không đem lại bảo đảm bổ sung.

Bound có thể gồm nhiều hợp đồng, chẳng hạn `<T extends Animal & Runnable>` (nếu có class bound thì class đó phải đứng trước các interface bound). Các bound ảnh hưởng cách kiểm tra kiểu lúc biên dịch; phương thức override vẫn được phân phối theo đối tượng thật khi chạy.

## 5. Thời điểm kiểm tra kiểu và chọn hành vi

**Generics chủ yếu là cơ chế compile-time.** Trình biên dịch kiểm tra kiểu phần tử, suy luận `T`, ràng buộc và các phép gán. Java generics thông thường dùng **type erasure**: `T` không bound thường được xóa thành `Object`, còn `T extends Animal` xóa về bound `Animal`. Trình biên dịch có thể chèn cast và tạo bridge method khi cần duy trì semantics của override sau erasure. Vì thế hai đối tượng `new ArrayList<String>()` và `new ArrayList<Integer>()` đều là instance của lớp runtime `ArrayList`; chúng không là hai lớp được “chuyên biệt hóa” riêng.

**Đa hình subtype tại runtime vẫn có thông tin lớp thực tế.** Khi `Animal a = new Dog("Milu")`, đối tượng thực sự là `Dog`, nên lệnh gọi phương thức instance có override `a.sound()` chạy bản `Dog.sound()`. Không có yêu cầu lưu `T = Dog` tại runtime để thực hiện điều này.

Không thể kiểm tra `value instanceof List<String>` trong Java vì tham số kiểu cụ thể `String` bị xóa và không kiểm tra được theo cách đó. Có thể kiểm tra `value instanceof List<?>` nhưng chỉ biết nó là `List`, không kết luận phần tử bên trong là `String`.

Cũng cần phân biệt **overriding** và **overloading**:

- **Overriding**: lớp con/interface implementation đưa ra triển khai mới của phương thức instance có chữ ký override hợp lệ; runtime chọn triển khai theo đối tượng.
- **Overloading**: cùng tên phương thức nhưng khác danh sách tham số; lựa chọn overload dựa trên kiểu tĩnh của đối số và quy tắc biên dịch, chứ không tự chuyển sang overload khác vì đối tượng runtime là `Dog`.

Generics có thể tham gia vào chọn overload lúc biên dịch nhưng **không thay thế dispatch của phương thức override**.

## 6. Chọn cơ chế trong thiết kế thực tế

| Nhu cầu | Cách biểu đạt | Vì sao |
|---|---|---|
| Hộp chứa đúng kiểu dữ liệu mà người gọi chọn | `Box<T>` | Lưu/đọc theo `T`, tránh cast |
| Thuật toán trả về phần tử cùng kiểu danh sách đầu vào | `<T> T first(List<T> xs)` | Giữ liên hệ kiểu giữa tham số và kết quả |
| Nhiều loài có âm thanh khác nhau | `Animal.sound()` được `Dog`, `Cat` override | Một lời gọi chung, hành vi runtime khác nhau |
| Nhiều lớp triển khai một dịch vụ thống nhất | `interface Service`; các lớp `implements Service` | Đa hình qua hợp đồng chung mà không bắt buộc lớp cha cụ thể |
| Hàm chỉ cần đọc mọi subtype của Animal | `List<? extends Animal>` | Chấp nhận `List<Dog>`, `List<Cat>` để đọc |
| Hàm muốn thêm Dog vào danh sách có khả năng chứa Dog | `List<? super Dog>` | Đảm bảo thao tác ghi an toàn |
| Hàm nhận và trả chính subtype của Animal | `<T extends Animal> T process(T input)` | Ràng buộc kiểu đầu vào/đầu ra |

Một API có thể kết hợp cả ba cơ chế: interface/lớp cha diễn tả **hợp đồng hành vi**; implementation override phần **hành vi riêng**; generics bảo vệ **quan hệ kiểu** của cấu trúc hay thuật toán. Chỉ thêm abstraction thực sự phục vụ yêu cầu.

## 7. Lỗi tư duy và lỗi biên dịch thường gặp

1. **“Bound `extends` nghĩa là khai báo một lớp con.”** Sai. `<T extends Animal>` là ràng buộc tham số kiểu. `class Dog extends Animal` mới tạo quan hệ kế thừa lớp.
2. **“`Dog extends Animal` nên `List<Dog> extends List<Animal>`.”** Sai do tính bất biến của tham số kiểu. Giải quyết bằng wildcard hoặc copy từng phần tử theo API phù hợp.
3. **“Với `List<? extends Animal>` có thể thêm bất cứ `Dog` nào.”** Sai vì tham số kiểu cụ thể của danh sách là ẩn; danh sách có thể là `List<Cat>`.
4. **“Dùng `Object` hoàn toàn tương đương `<T>`.”** `Object identity(Object x)` làm mất thông tin kiểu cụ thể của kết quả; `<T> T identity(T x)` cho trình biên dịch giữ mối liên hệ đầu vào–đầu ra.
5. **“Generics luôn tồn tại nguyên vẹn tại runtime.”** Sai do erasure thông thường. Không dùng `instanceof List<String>` để xác định phần tử; cần kiểm tra từng phần tử hoặc thiết kế cách mang type token thích hợp khi thật sự cần.
6. **“Ép kiểu hoặc raw type giải quyết được mọi vấn đề generic.”** Raw `List` và unchecked cast làm yếu kiểm tra biên dịch và có thể gây `ClassCastException` về sau. Ưu tiên chữ ký generic/wildcard đúng thay vì né kiểm tra kiểu.
7. **“Java 17 chấp nhận `List<int>` hoặc `new T()`.”** Sai. Type argument phải là kiểu tham chiếu (dùng `Integer`, có autoboxing); không thể trực tiếp khởi tạo `new T()` vì `T` không chỉ ra constructor runtime. Khi cần tạo phần tử generic, có thể nhận `Supplier<T>` hoặc factory.
8. **“Thêm generic là đã có đa hình runtime.”** Sai. `Box<T>` có thể không override phương thức nào. Dynamic dispatch đến từ các override của phương thức instance và đối tượng runtime, không từ tên `T`.

## 8. Câu hỏi tự kiểm tra có đáp án

**Câu 1.** `Dog extends Animal`, biến `Animal a = new Dog("Milu")` gọi `a.sound()`. Nếu `Dog` override `sound()` thì chạy phiên bản nào? **Đáp án:** `Dog.sound()` nhờ đa hình runtime.

**Câu 2.** Có thể gán `List<Dog>` trực tiếp vào `List<Animal>` không? **Đáp án:** không; generics invariant. Có thể nhận danh sách qua `List<? extends Animal>` khi cần đọc phần tử dưới kiểu `Animal`.

**Câu 3.** `<T> T identity(T x)` làm được gì hơn `Object identity(Object x)`? **Đáp án:** giữ được kiểu tĩnh của giá trị trả về dựa vào đối số, tránh cast tại điểm gọi.

**Câu 4.** Một tham số `List<? super Dog>` cho phép thêm `Dog`; tại sao khi lấy phần tử chỉ bảo đảm `Object`? **Đáp án:** kiểu cụ thể có thể là `Dog`, `Animal` hoặc `Object`, không biết chính xác hơn.

**Câu 5.** Hàm chỉ in `animal.sound()` có bắt buộc `<T extends Animal>` không? **Đáp án:** không; `void printSound(Animal animal)` là đủ nếu không cần giữ kiểu `T` ở vị trí khác.

**Câu 6.** `Box<Dog>` có là subtype của `Box<Animal>` không? **Đáp án:** không, trừ khi chính kiểu `Box` có một quan hệ subtype khác được định nghĩa tường minh; generic instantiation không tự kế thừa nhau.

Khi gặp bài toán mới, tự hỏi: **Các đối tượng có cùng hợp đồng nào?** → lớp cha/interface. **Chúng triển khai hành vi khác nhau ra sao?** → override và đa hình runtime. **Một cấu trúc hoặc thuật toán cần bảo toàn quan hệ kiểu nào?** → generics, bound, wildcard. Phân biệt được ba câu hỏi đó là nền tảng để viết API Java an toàn và dễ dùng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-007">Lợi ích, giới hạn và trường hợp không cần generics</a>

<details>
<summary>Xem chi tiết</summary>

# Lợi ích, giới hạn và trường hợp không cần generics trong Java

Generics giúp diễn đạt **mối quan hệ về kiểu dữ liệu** ngay trong chữ ký lớp, interface và phương thức. Chúng thường giúp phát hiện sai kiểu khi biên dịch, tái sử dụng thuật toán và làm cho API rõ nghĩa hơn. Tuy nhiên, không phải mọi lớp hay phương thức đều nên khai báo `<T>`: việc tổng quát hóa chỉ hữu ích khi có một quan hệ kiểu thực sự cần duy trì.

Mục tiêu của bài học là đánh giá được **lợi ích**, hiểu **ranh giới bảo đảm** của generics trong Java và quyết định **khi nào dùng kiểu tổng quát, kiểu cụ thể, `Object` hoặc wildcard**. Các ví dụ dùng Java 17 trở lên.

## 1. Câu hỏi đầu tiên: có mối quan hệ kiểu cần biểu diễn không?

Xét hai API:

```java
// A: kiểu đầu vào quyết định kiểu kết quả.
static <T> T first(java.util.List<T> items) {
    return items.get(0);
}

// B: phép tính luôn phục vụ một loại dữ liệu nghiệp vụ cụ thể.
static int totalStock(java.util.List<Product> products) {
    return products.stream().mapToInt(Product::stock).sum();
}
```

Ở A, `T` xuất hiện ở cả đối số `List<T>` và kiểu trả về `T`. Nó biểu diễn quy tắc: **lấy phần tử từ danh sách kiểu nào thì nhận về phần tử kiểu đó**. Một phương thức làm việc với `String`, `Product` hoặc nhiều kiểu tham chiếu khác mà vẫn kiểm tra kiểu.

Ở B, quy tắc nghiệp vụ là tính tổng tồn kho của `Product`. Không có nhu cầu bảo toàn hay trả về một kiểu tham số tùy ý. `List<Product>` làm rõ yêu cầu hơn `List<T>`: tổng quát hóa B sẽ không tạo thêm lợi ích chỉ vì cú pháp ngắn gọn hay có vẻ linh hoạt.

`Product` trong đoạn B là một kiểu miền nghiệp vụ minh họa, được khai báo đầy đủ ở chương trình chạy bên dưới. Các đoạn code ngắn trong bài chỉ mô tả từng ý; ví dụ `Main.java` ở phần 3 là chương trình hoàn chỉnh.

## 2. Các lợi ích quan trọng

### 2.1. Phát hiện sai kiểu ngay khi biên dịch

Hãy xem hợp đồng `List<String>`:

```java
java.util.List<String> names = new java.util.ArrayList<>();
names.add("Lan");
// names.add(123); // Lỗi biên dịch: add yêu cầu String.

String name = names.get(0); // Không phải tự ép từ Object.
```

Trình biên dịch biết kiểu phần tử của `names` là `String` nên từ chối việc thêm `Integer` bằng lời gọi thông thường. Nhờ đó, nhiều lỗi vốn chỉ lộ ra khi người dùng chạy đúng nhánh nghiệp vụ được phát hiện trước khi chương trình chạy.

Điều này **giảm nguy cơ** `ClassCastException` và các lỗi dùng sai kiểu, chứ **không triệt tiêu tuyệt đối** chúng. `raw type`, phép ép kiểu không kiểm chứng, reflection, deserialization hoặc tương tác với thư viện cũ vẫn có thể đưa dữ liệu sai kiểu vào bộ sưu tập.

### 2.2. Giảm ép kiểu tường minh, làm rõ hợp đồng API

Một API chỉ trả `Object` đặt trách nhiệm xác định kiểu thực lên bên gọi:

```java
Object result = getLegacyValue();
String text = (String) result; // Chỉ an toàn nếu dữ liệu thật là String.
```

Nếu hợp đồng có thể thể hiện bằng kiểu tổng quát, API có thể trả đúng kiểu liên quan tới lời gọi, chẳng hạn `Optional<Product>`, `Result<String>` hay `List<Order>`. Code gọi ngắn và dễ đọc hơn, còn IDE có thể hỗ trợ kiểm tra kiểu và gợi ý thành viên chính xác hơn.

Tuy nhiên, **không được đổi một API trả `Object` thành `<T> T` nếu thực tế không có cơ chế bảo đảm kiểu được yêu cầu**. Ví dụ một phương thức đọc dữ liệu không rõ schema mà tự ép `(T)` bên trong chỉ chuyển cảnh báo và nguy cơ lỗi sang chỗ khác, không tạo ra bảo đảm an toàn mới.

### 2.3. Tái sử dụng cấu trúc và thuật toán

`List<E>`, `Map<K, V>`, `Optional<T>` và `Comparator<T>` đều khai thác mối quan hệ kiểu. Ví dụ `Map<K,V>` buộc kiểu khóa và kiểu giá trị theo hai tham số độc lập; `Optional<T>` gói việc có hoặc không có giá trị mà vẫn giữ kiểu của giá trị đó.

Các thành phần dùng lại có thể nhận các kiểu miền nghiệp vụ khác nhau mà không phải viết `ProductResult`, `UserResult`, `InvoiceResult` có phần lưu dữ liệu giống nhau. Trong khi đó, phương thức generic có thể tái sử dụng phép tìm kiếm, chọn phần tử hoặc biến đổi dữ liệu theo kiểu được truyền vào.

Tính tái sử dụng nên đi kèm một **hợp đồng ổn định và có ý nghĩa**; tránh tạo lớp tổng quát chỉ để giảm vài dòng code nếu hành vi giữa các kiểu thực chất khác nhau.

### 2.4. Dễ bảo trì và đổi kiểu khi sửa API

Nếu tham số kiểu của một biến, field hoặc phương thức thay đổi, trình biên dịch chỉ ra nhiều vị trí sử dụng không tương thích. Các kiểu như `List<Product>` cũng là một dạng tài liệu được máy kiểm tra, giúp người đọc nhận biết đúng dữ liệu API chấp nhận.

Generics không thay thế tài liệu về ý nghĩa nghiệp vụ: `List<Integer>` vẫn không nói được số phải không âm, là mã định danh hay đại lượng đo lường. Những ràng buộc này cần kiểu miền nghiệp vụ hoặc validation riêng.

## 3. Ví dụ Java 17+ hoàn chỉnh: chọn generics ở đúng chỗ

Chương trình kết hợp ba quyết định thiết kế:

- `Result<T>` là lớp generic vì kiểu dữ liệu trả về thay đổi nhưng cấu trúc kết quả giữ nguyên.
- `findFirst` là phương thức generic vì kiểu đầu vào và phần tử tìm được phải đồng nhất.
- `totalStock` nhận đúng `List<Product>` và `describe` nhận `Object` vì cả hai không cần mối quan hệ kiểu tổng quát.

Lưu toàn bộ đoạn sau vào **`Main.java`**:

```java
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class Main {
    record Product(String code, int stock) {
    }

    static final class Result<T> {
        private final String status;
        private final T value;

        private Result(String status, T value) {
            this.status = status;
            this.value = value;
        }

        static <T> Result<T> ok(T value) {
            return new Result<>("OK", value);
        }

        String status() {
            return status;
        }

        T value() {
            return value;
        }
    }

    static <T> Optional<T> findFirst(
            List<T> items,
            Predicate<? super T> condition) {
        for (T item : items) {
            if (condition.test(item)) {
                return Optional.ofNullable(item);
            }
        }
        return Optional.empty();
    }

    static int totalStock(List<Product> products) {
        int sum = 0;
        for (Product product : products) {
            sum += product.stock();
        }
        return sum;
    }

    static String describe(Object value) {
        return String.valueOf(value);
    }

    public static void main(String[] args) {
        Result<String> message = Result.ok("ready");
        Result<Product> saved = Result.ok(new Product("P-01", 7));

        List<Product> inventory = List.of(
                new Product("P-01", 7),
                new Product("P-02", 2));

        Optional<Product> lowStock =
                findFirst(inventory, product -> product.stock() < 3);

        System.out.println("status: " + message.status());
        System.out.println("message: " + message.value());
        System.out.println("saved SKU: " + saved.value().code());
        System.out.println("low stock: "
                + lowStock.map(Product::code).orElse("none"));
        System.out.println("total stock: " + totalStock(inventory));
        System.out.println("describe: " + describe(42));

        // message = Result.ok(42); // Không biên dịch: Result<Integer>
        // không thể gán cho biến Result<String>.
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 Main.java
java Main
```

Kết quả trên stdout:

```text
status: OK
message: ready
saved SKU: P-01
low stock: P-02
total stock: 9
describe: 42
```

Giải thích thiết kế:

1. `Result.ok("ready")` suy luận `T` là `String`; `Result.ok(new Product(...))` suy luận `T` là `Product`. Người đọc `message.value()` được `String`, còn `saved.value()` được `Product`, không cần ép kiểu thủ công.
2. `findFirst` khai báo `<T>` trước kiểu trả về. `Predicate<? super T>` cho phép cung cấp một điều kiện có thể xử lý `T` hoặc kiểu cha của `T`; kết quả `Optional<T>` giữ kiểu phần tử của danh sách.
3. `totalStock(List<Product>)` cố ý không generic: logic `stock()` chỉ có ý nghĩa với `Product` trong ví dụ này.
4. `describe(Object)` cũng không cần `<T>`: nó nhận một giá trị bất kỳ, chuyển thành chuỗi và không trả lại giá trị cùng kiểu với đầu vào. Khai báo `static <T> String describe(T value)` sẽ thêm tham số kiểu không tạo ích lợi cho hợp đồng.

Các quy tắc thông báo lỗi ở ví dụ được để dưới dạng chú thích để chương trình vẫn biên dịch và chạy được. Việc thêm một tham số kiểu chỉ vì phương thức có đầu vào tùy ý thường là dấu hiệu cần xem lại thiết kế.

## 4. Ranh giới an toàn kiểu: Java generics không bảo đảm điều gì?

### 4.1. Type erasure: hầu hết đối số kiểu không còn ở runtime

Java generics chủ yếu hoạt động qua **type erasure**: trình biên dịch kiểm tra tính tương thích theo khai báo, sau đó chuyển phần lớn thông tin tham số kiểu thành kiểu bị xóa (với bound phù hợp), đồng thời chèn các phép ép kiểu cần thiết.

```java
java.util.List<String> texts = new java.util.ArrayList<>();
java.util.List<Integer> numbers = new java.util.ArrayList<>();

System.out.println(texts.getClass() == numbers.getClass()); // true
```

Hai đối tượng trên đều thuộc lớp runtime `java.util.ArrayList`. Kết quả `true` **không chứng minh** hai danh sách có cùng kiểu phần tử; nó chỉ chứng minh hai đối tượng có cùng lớp triển khai khi chạy.

Bạn có thể hỏi `value instanceof List<?>`, nhưng không thể kiểm tra chính xác `value instanceof List<String>` bằng cú pháp ấy: kiểu đối số `String` không được *reified* ở runtime. Tương tự, `List<String>.class` không phải một class literal hợp lệ.

Nếu cần kiểm tra kiểu runtime của một đối tượng riêng lẻ, có thể truyền **type token** `Class<T>`:

```java
static <T> T checkedCast(Object value, Class<T> expectedType) {
    return expectedType.cast(value);
}

// checkedCast("abc", String.class) -> String.
// checkedCast(123, String.class) -> ClassCastException khi chạy.
```

Đây là đoạn minh họa phương thức, không phải chương trình độc lập. `Class<T>` kiểm tra lớp của đối tượng được cast; nó **không kiểm tra sâu** danh sách thực sự chỉ chứa `String` qua `List<String>` vì không có `List<String>.class`.

### 4.2. Raw type và unchecked cast có thể làm hỏng bảo đảm kiểu

Raw type là việc bỏ đối số kiểu, ví dụ `List` thay vì `List<String>`, chủ yếu để tương thích ngược với Java trước generics:

```java
java.util.List<String> names = new java.util.ArrayList<>();
java.util.List raw = names;
raw.add(99); // Cảnh báo unchecked khi biên dịch.
```

Đoạn này vẫn có thể biên dịch cùng cảnh báo; danh sách gốc giờ chứa `Integer`. Nếu đoạn code khác lấy phần tử ấy với giả định `String`, có thể xảy ra `ClassCastException`. Phân biệt rõ: lỗi **không bắt buộc xảy ra ngay khi thêm**, mà thường lộ ra lúc lấy giá trị và trình biên dịch đã chèn cast tương ứng.

Cảnh báo `unchecked` phải được xem xét về nguồn gốc dữ liệu và hợp đồng kiểu; không nên xóa cảnh báo bằng `@SuppressWarnings("unchecked")` tùy tiện. Giữ thao tác ép kiểu không kiểm chứng tại ranh giới nhỏ nhất và kiểm tra giá trị khi cần.

### 4.3. Generics thường bất biến (invariant)

Dù `Integer` là subtype của `Number`, `List<Integer>` không phải subtype của `List<Number>`:

```java
java.util.List<Integer> integers = java.util.List.of(1, 2);
// java.util.List<Number> nums = integers; // Không biên dịch.
```

Nếu phép gán này hợp lệ, một phương thức nhận `List<Number>` có thể thêm `Double` vào danh sách vốn chỉ được thiết kế cho `Integer`.

Khi chỉ cần đọc các giá trị là số, wildcard có thể phù hợp:

```java
static double sum(java.util.List<? extends Number> values) {
    double result = 0;
    for (Number value : values) {
        result += value.doubleValue();
    }
    return result;
}
```

`sum` chấp nhận `List<Integer>`, `List<Double>` và nhiều danh sách số khác. Nhưng qua tham chiếu `List<? extends Number>`, bạn **không thể thêm một đối tượng `Number` tùy ý**; chỉ có thể thêm `null` trong những trường hợp phép thêm được hỗ trợ. Đây là đánh đổi để bảo vệ kiểu phần tử thực tế. `? super T` thường thích hợp khi API cần **nhận giá trị để ghi**; sự lựa chọn wildcard phải xuất phát từ hành vi đọc/ghi thực tế.

### 4.4. Không phải mọi cú pháp Java đều hỗ trợ tham số kiểu

Các ví dụ dưới đây **cố ý không hợp lệ**, dùng để nhận diện giới hạn; không ghép chúng vào `Main.java` để chạy:

```java
// List<int> values;               // Sai: đối số kiểu không là primitive.
// T x = new T();                   // Sai: không thể new T trực tiếp.
// T[] a = new T[10];               // Sai: không thể tạo mảng generic trực tiếp.
// new List<String>[10];            // Sai: không tạo mảng kiểu tham số hóa.
// if (obj instanceof List<String>) // Sai: không kiểm tra loại phần tử ấy.
// ...
```

Đối số kiểu như `List<Integer>` phải là **kiểu tham chiếu**, không phải primitive (`List<int>`). Java tự boxing/unboxing cho nhiều thao tác với `Integer`, nhưng vẫn có chi phí cấp phát hoặc chuyển đổi trong một số tình huống; không nên giả định dùng generics có hiệu năng giống `int[]`.

Trong một lớp `class Store<T>`, field `static T value` cũng không hợp lệ vì field static thuộc lớp dùng chung, còn `T` thuộc từng cách tham số hóa. Phương thức `static <T> T convert(...)` có thể tự khai báo `<T>` riêng; đây là hai trường hợp khác nhau.

Hai overload chỉ khác đối số kiểu như `void use(List<String> x)` và `void use(List<Integer> x)` sẽ xung đột do **cùng chữ ký sau erasure**. Java cũng không cho tạo một lớp generic kế thừa trực tiếp `Throwable`, nên kiểu ngoại lệ không thể được tham số hóa như `class MyException<T> extends Exception`.

### 4.5. Generics không thay thế validation hoặc xác thực dữ liệu

`List<String>` không bảo đảm phần tử khác `null`, không rỗng hay đúng định dạng email. `Result<Product>` không bảo đảm tồn kho không âm, bản ghi tồn tại trong cơ sở dữ liệu hay người dùng có quyền truy cập.

Kiểu tổng quát cho biết **hình dạng và mối quan hệ kiểu**. Các quy tắc như “số lượng lớn hơn hoặc bằng 0”, “mã sản phẩm duy nhất” và “được phép xem sản phẩm” phải được kiểm tra bởi constructor, domain model, tầng nghiệp vụ hoặc cơ chế bảo mật thích hợp.

Generics cũng không tự động làm API thread-safe, bất biến, nhanh hơn hay dùng ít bộ nhớ hơn. Các thuộc tính này phụ thuộc cấu trúc dữ liệu, cách đồng bộ hóa, thuật toán và cách lưu trữ thực tế.

## 5. Các trường hợp không cần tự khai báo generics

### 5.1. Thành phần chỉ có một kiểu miền nghiệp vụ cố định

Nếu nghiệp vụ xử lý đúng `Product`, dùng `Product` trực tiếp:

```java
static int quantityToReorder(Product product, int minimum) {
    return Math.max(0, minimum - product.stock());
}
```

Phương thức này không cần `<T>` vì nó đòi hỏi thuộc tính `stock()` của `Product`. Tổng quát hóa thành `<T>` buộc phải thêm ràng buộc hoặc ép kiểu, trong khi không có tình huống dùng lại có ý nghĩa. Bài học ở đây là: **kiểu cụ thể có thể là hợp đồng rõ nhất**.

### 5.2. Kiểu dữ liệu đầu vào không cần được giữ lại

Một phương thức ghi log hoặc hiển thị mô tả có thể nhận một đối tượng bất kỳ và luôn trả về chuỗi:

```java
static String describe(Object value) {
    return String.valueOf(value);
}
```

Tham số kiểu `<T>` không giúp liên kết đầu vào với đầu ra vì đầu ra luôn là `String`. Dùng `Object` là lựa chọn đơn giản, có chủ đích trong trường hợp này.

Cần phân biệt với API nhận danh sách *không rõ kiểu phần tử*. Khi chỉ duyệt và đọc một danh sách bất kỳ, `List<?>` thường phù hợp hơn `List<Object>`:

```java
static int count(java.util.List<?> items) {
    return items.size();
}
```

`count(List<?>)` nhận được `List<String>` và `List<Integer>`, trong khi `count(List<Object>)` **không** nhận trực tiếp `List<String>`. Đây vẫn là việc **sử dụng** cú pháp generic của thư viện, nhưng phương thức không phải tự khai báo tên kiểu `<T>`.

### 5.3. Tổng quát hóa làm mất ý nghĩa nghiệp vụ

`Payment`, `Invoice` và `UserAccount` có thể đều có một trường `id`, nhưng không vì thế mà mọi nghiệp vụ của chúng nên gom vào `Manager<T>`. Nếu validation, vòng đời và quyền truy cập khác nhau, những service cụ thể hoặc interface có trách nhiệm rõ ràng thường dễ đọc và an toàn hơn.

Tham số kiểu chỉ nên xuất hiện khi có **hành vi chung thực sự và quy tắc ràng buộc kiểu cần giữ**. Tránh khai báo nhiều tham số `<T,U,V>` mà phần lớn không xuất hiện ở đầu vào hoặc kết quả, khiến người gọi phải suy đoán ý nghĩa chúng.

### 5.4. Dữ liệu đa hình có tập kiểu và hành vi xác định

Nếu chương trình xử lý nhiều loại thông điệp với hành vi khác nhau, một interface hoặc sealed interface có thể diễn đạt miền dữ liệu tốt hơn một `Box<Object>` chứa mọi thứ. Với Java 17, ví dụ:

```java
sealed interface Event permits OrderCreated, PaymentFailed {
}

record OrderCreated(String orderId) implements Event {
}

record PaymentFailed(String reason) implements Event {
}
```

Đây là các khai báo minh họa, không phải chương trình độc lập. Chúng làm rõ các biến thể `Event` hợp lệ. Bạn vẫn có thể dùng `List<Event>` để chứa nhiều biến thể; việc lựa chọn đa hình theo hành vi và việc tham số hóa cấu trúc dữ liệu là **hai quyết định có thể phối hợp**.

Đối với dữ liệu JSON hoặc bản ghi động không xác định schema, không nên giả định `T` làm dữ liệu tự trở nên hợp lệ. Hãy parse, validate và chuyển đổi ở biên; chỉ dùng kiểu tổng quát nếu có cơ chế đáng tin để gắn đầu vào với kiểu kết quả.

### 5.5. Thuật toán sử dụng primitive trong đường xử lý hiệu năng cao

`List<Integer>` có thể phù hợp với logic nghiệp vụ thông thường. Nhưng khi xử lý một lượng lớn giá trị nguyên, `int[]`, `IntStream` hoặc cấu trúc chuyên dụng cho primitive có thể tránh một phần boxing và overhead đối tượng. Cần đo đạc trước khi tối ưu: generics không mặc định chậm và mảng primitive cũng không mặc định tốt nhất cho mọi bài toán.

### Bảng quyết định nhanh

| Nhu cầu thực tế | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Cùng cấu trúc lưu một giá trị với nhiều kiểu | `Box<T>` hoặc `Result<T>` | Bảo toàn kiểu payload qua các thao tác |
| Thuật toán trả về phần tử cùng kiểu với đầu vào | Phương thức `static <T> T ...` | Liên kết đầu vào và kết quả |
| Bảng ánh xạ khóa và giá trị có kiểu cụ thể | `Map<K,V>` khi khai báo API tổng quát; `Map<String,Integer>` khi sử dụng | Mô tả đúng hai loại dữ liệu |
| Chỉ tính tồn kho sản phẩm | `List<Product>` / `Product` | Đúng logic và kiểu miền nghiệp vụ |
| Chỉ đếm phần tử của danh sách bất kỳ | `List<?>` | Không cần biết hay truyền trả kiểu phần tử |
| Nhận đối tượng bất kỳ để ghi log/chuyển thành chuỗi | `Object` | Không cần quan hệ kiểu |
| Cần phân biệt nhiều trường hợp nghiệp vụ có hành vi riêng | Interface, sealed interface, class/record cụ thể | Diễn đạt tập biến thể và hành vi |
| Dữ liệu động chưa được kiểm chứng | Parse, validate, type token hoặc schema phù hợp | Generics không tự kiểm tra kiểu runtime sâu |
| Khối lượng lớn primitive, nhạy hiệu năng | `int[]`, `IntStream` hoặc cấu trúc primitive đã đo đạc | Tránh chi phí boxing ở nơi cần |

## 6. Dấu hiệu thiết kế generics đang bị lạm dụng

- **Tham số kiểu chỉ xuất hiện một lần, không có ràng buộc hữu ích:** ví dụ `static <T> void print(T value)` thường nên là `print(Object value)` nếu chỉ in bằng `String.valueOf`. Tuy nhiên, một bound cụ thể vẫn có thể diễn đạt yêu cầu kiểu; không dùng số lần xuất hiện như luật tuyệt đối.
- **Ép kiểu `(T)` bên trong mà không có căn cứ:** khiến API trông an toàn nhưng thực tế chỉ đẩy nguy cơ `ClassCastException` tới người gọi.
- **Mọi lớp đều có `<T>` dù logic chỉ dành cho một entity:** làm khó đọc và có thể làm lu mờ quy tắc nghiệp vụ.
- **Dùng raw type để tránh xử lý wildcard/bounds:** thường đánh mất kiểm tra kiểu và tạo cảnh báo `unchecked`.
- **Thử giải quyết bất biến hoặc phân quyền bằng generics:** kiểu tham số không thay thế constructor validation, kiểm tra quyền hay đồng bộ hóa.
- **Dùng wildcard quá rộng trong API cho phép ghi:** chẳng hạn trả `List<? extends Number>` rồi mong thêm số bất kỳ vào; khả năng đọc và ghi cần thiết phải được xác định trước khi chọn bound.
- **Tổng quát hóa vì dự đoán sẽ dùng lại nhưng chưa có trường hợp thật:** làm tăng chi phí học và bảo trì. Hãy thiết kế hợp đồng cụ thể đúng trước; trích xuất generic khi đã thấy quan hệ chung.

Một số API tổng quát phức tạp hoàn toàn có lý do chính đáng, nhất là thư viện, collection và framework. Điểm đánh giá không phải số lượng dấu `<...>`, mà là **tham số kiểu có làm rõ và kiểm tra được một ràng buộc thực sự hay không**.

## 7. Checklist khi quyết định dùng generics

Trước khi khai báo `<T>`, tự trả lời:

1. **Mối quan hệ kiểu nào cần giữ?** Kiểu của đối số, trạng thái, callback hoặc kết quả có cần phụ thuộc lẫn nhau không?
2. **Có hành vi chung thực sự?** Nhiều kiểu có thể chia sẻ cùng thuật toán mà không phải dùng `instanceof` và cast liên tục không?
3. **Người dùng API được lợi gì?** Có giảm ép kiểu, bắt lỗi sớm hoặc thể hiện hợp đồng rõ hơn không?
4. **Có cần kiểm tra kiểu runtime không?** Nếu có, cần type token/schema và validation ở đâu?
5. **Bound hoặc wildcard có phù hợp với hướng dữ liệu?** API chỉ đọc, chỉ ghi hay vừa đọc vừa ghi?
6. **Có đang che giấu logic nghiệp vụ?** Kiểu cụ thể, interface hoặc model miền có diễn đạt rõ hơn không?
7. **Có yếu tố primitive hoặc hiệu năng đặc biệt?** Khi đáng kể, nên benchmark giải pháp phù hợp.

Nếu câu hỏi 1–3 đều không cho thấy ích lợi, thường **không cần tự khai báo thêm generics**.

## 8. Tự kiểm tra kiến thức

**Câu 1.** Phương thức sau có cần khai báo `<T>` không?

```java
static <T> int lengthOfText(T value) {
    return String.valueOf(value).length();
}
```

**Đáp án:** Không bắt buộc. `T` không tham gia ràng buộc đầu ra hoặc thao tác phụ thuộc kiểu. `static int lengthOfText(Object value)` đơn giản và diễn đạt đúng hành vi hơn.

**Câu 2.** Có thể dùng `List<Number>` làm tham số để nhận trực tiếp mọi `List<Integer>` và `List<Double>` không?

**Đáp án:** Không, vì các tham số hóa này bất biến. Nếu chỉ đọc số và tính toán, `List<? extends Number>` có thể phù hợp.

**Câu 3.** `List<String>` có bảo đảm dữ liệu từ một raw `List` hoặc phản hồi JSON bên ngoài đều thật sự là `String` ở runtime không?

**Đáp án:** Không. Type erasure, raw type, unchecked operation và dữ liệu ngoài ranh giới kiểu khiến kiểm chứng runtime cần giải pháp riêng. Generics chủ yếu bảo vệ tại các điểm gọi được trình biên dịch kiểm tra.

**Câu 4.** Tại sao `totalStock(List<Product>)` lại là lựa chọn tốt hơn `static <T> int totalStock(List<T>)` trong ví dụ?

**Đáp án:** Vì phép tính cần phương thức `stock()` của `Product`, nên khai báo cụ thể mô tả trực tiếp điều kiện nghiệp vụ. Chữ `T` tự nó không cho phép gọi `stock()` nếu không có bound phù hợp, trong khi tính tổng tồn kho ở đây không phải hành vi tổng quát cho mọi loại đối tượng.

**Câu 5.** `Result<String>` có tự khiến chuỗi kết quả khác `null` hoặc khác rỗng không?

**Đáp án:** Không. `String` xác định loại dữ liệu, còn ràng buộc null/chuỗi rỗng phải có kiểm tra giá trị riêng trong API hoặc domain model.

## Kết luận

Generics có giá trị cao nhất khi chúng **bảo toàn quan hệ kiểu qua các thao tác**, giúp tái sử dụng logic và chuyển nhiều lỗi sang giai đoạn biên dịch. Chúng có giới hạn bởi type erasure, bất biến kiểu, quy tắc cú pháp Java, raw/unchecked code và sự tách biệt giữa kiểu dữ liệu với validation nghiệp vụ. Khi hành vi gắn chặt với một entity, đầu vào không cần liên kết với đầu ra hoặc một interface chuyên biệt diễn đạt miền dữ liệu tốt hơn, chọn **kiểu cụ thể hoặc abstraction phù hợp** sẽ làm API sáng sủa hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-overview-008">Kiến thức nền và trình tự học generics trong Java Core</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức nền và trình tự học Generics trong Java Core

Bài này là **bản đồ học tập và tự đánh giá** cho người bắt đầu Java Generics. Thay vì học thuộc từng cú pháp `<T>`, `? extends T` và `? super T`, bạn sẽ đi từ những kiến thức cần có, qua các bài học theo đúng quan hệ phụ thuộc, rồi thực hiện một chương trình tổng hợp và tự kiểm tra mức độ thành thạo.

Generics giúp compiler kiểm tra **quan hệ giữa các kiểu** trong cấu trúc dữ liệu và API. Đích học tập không phải viết nhiều dấu ngoặc nhọn góc, mà là **đọc được chữ ký**, **dự đoán được câu lệnh có biên dịch không** và **biết giới hạn của bảo đảm kiểu khi chạy**.

Bài học dùng **Java 17+**; có thể thực hành với JDK và trình soạn thảo thông thường, không cần Spring, Hibernate, Maven, JUnit hay thư viện ngoài JDK. Nếu đã quen Java cơ bản, dùng phần kiểm tra đầu vào để rút ngắn những chặng bạn đã thành thạo.

## 1. Kết quả cần đạt và cách dùng lộ trình

Hoàn thành lộ trình nghĩa là bạn tự làm được những việc sau:

1. Nêu sự khác nhau giữa **kiểu tĩnh của biến**, **class thực tế của object** và **type arguments** do compiler biết.
2. Đọc/viết một class `Box<T>`, interface `Store<K,V>` và method `static <T> T first(List<T>)`.
3. Phân biệt **type parameter**, **type variable**, **type argument**, **parameterized type**, **raw type** và **wildcard** bằng ví dụ riêng.
4. Dự đoán lời gọi `add`, `get`, `set` hợp lệ theo `List<T>` và hiểu vì sao `List<Integer>` không tự là `List<Number>`.
5. Chọn `? extends T` cho một nguồn đọc và `? super T` cho một đích ghi khi **dòng dữ liệu đúng là một chiều**.
6. Giữ một `T` có tên khi API cần liên kết kiểu ở **nhiều vị trí** (đầu vào, kết quả hoặc thao tác đọc–ghi hai chiều).
7. Giải thích type erasure, raw/unchecked warnings, và vì sao một `ClassCastException` có thể chỉ xuất hiện lúc dùng giá trị.
8. Đọc được một số generic signatures của thư viện chuẩn và thiết kế một phương thức đủ an toàn để caller không phải cast.
9. Biết lúc nào cần **kiểu cụ thể, interface hoặc một phép kiểm tra runtime**, thay vì tạo generic abstraction không cần thiết.

Một bài không được xem là “đã học” chỉ vì bạn xem xong ví dụ. Hãy **đoán kết quả trước khi chạy**, giải thích đúng lý do compiler chấp nhận/từ chối, rồi tự sửa một biến thể để chứng minh hiểu quy tắc.

## 2. Kiểm tra kiến thức nền trước khi học

| Kiến thức cần có | Phép tự kiểm tra | Tối thiểu cần hiểu |
| --- | --- | --- |
| Kiểu tham chiếu và object | `Number n = Integer.valueOf(5);` | Biến có kiểu tĩnh Number, object có runtime class Integer. |
| Primitive và wrapper | `int` khác `Integer` thế nào? | Generics dùng reference type arguments; boxing/unboxing có thể xảy ra. |
| `Object` và cast | `Object x = 42; String s = (String) x;` | Cast có thể biên dịch nhưng fail với ClassCastException khi chạy. |
| Class và interface | `Dog extends Animal`, `implements Runnable` | Phân biệt subtype, inheritance và interface contract. |
| Override và overload | `Animal a = new Dog(); a.sound();` | Runtime dispatch dùng bản override; overload chọn chủ yếu ở compile time. |
| Collection căn bản | `List`, `Set`, `Map`, `add`, `get` | Biết List lưu theo vị trí, Map theo khóa; hiểu khả năng sửa phụ thuộc implementation. |
| Compile vs runtime | Lỗi `incompatible types` khác `ClassCastException` thế nào? | Lỗi compile time ngăn tạo code; exception xuất hiện khi một operation chạy. |
| Method signatures | Kiểu trả về và kiểu tham số nằm đâu? | Có thể đọc chữ ký generic dựa trên cấu trúc method thông thường. |

Nếu chưa chắc cột thứ ba, ưu tiên ôn Java Core trước. Riêng khi chưa biết `List.of` và `ArrayList`, cần nhớ: `List.of(...)` trả list không cho sửa; `new ArrayList<>(...)` thường tạo list có thể thêm/thay phần tử. Đây là khác biệt runtime về mutability, **không phải** khác biệt về generic type arguments.

### Những thứ chưa bắt buộc

Bạn không cần học sâu lambda, Stream, reflection, class loaders, AspectJ hay framework trước khi học generic class và method. `Function<T,R>` hoặc reflection sẽ xuất hiện ở giai đoạn đọc API nâng cao; chỉ bổ sung những phần cần thiết khi đi tới chặng đó.

## 3. Sơ đồ phụ thuộc của các chặng

~~~text
NỀN TẢNG JAVA
Kiểu tĩnh/runtime ── Object/cast ── class/interface
           │                │
           └──── Collection/Method ─────┐
                                        ▼
CHẶNG 1: Vì sao generics?  List<String> vs List<Object>
                                        ▼
CHẶNG 2: Type parameter, type argument, generic class/interface
                                        ▼
CHẶNG 3: Generic method, inference, bounded type variable
                                        ▼
CHẶNG 4: Invariance, wildcard, PECS, read/write relationship
                                        ▼
CHẶNG 5: Type erasure, raw type, warnings, runtime boundaries
                                        ▼
CHẶNG 6: Đọc chữ ký JDK và quyết định thiết kế API
                                        ▼
CHẶNG 7: Mini-project, regression tests, tự đánh giá
~~~

Một **chặng** không tương ứng với một ngày học cố định. Chuyển chặng khi làm được đầu ra và bài kiểm tra của chặng trước; nếu vướng wildcard, quay lại **subtyping và invariant generic collection**, không cố học thuộc câu PECS.

## 4. Chặng 1 — Tại sao cần một hợp đồng kiểu?

**Mục tiêu:** nhận ra vấn đề khi một API chỉ trả Object hoặc một collection thiếu ràng buộc kiểu.

Bài thực hành:
- Tạo `List<Object>` chứa `"Java"` và `17`. Đọc phần tử đầu bằng cast String; xác định vì sao cast có thể thành công.
- Đọc phần tử thứ hai dưới kiểu String; dự đoán exception. Sau đó sửa bằng danh sách `List<String>` để compiler từ chối `add(17)` ngay lúc biên dịch.
- Phân biệt **một collection dùng generic với type argument Object** và **một raw collection `List`**: cả hai không mang hợp đồng “chỉ String”, nhưng raw List còn bỏ qua kiểm tra generic tại một số thao tác.

Cần trả lời: “Chương trình sai kiểu từ lúc đưa dữ liệu vào, hay chỉ sai ở phép cast lúc dùng? Cách khai báo nào cho compiler biết quy tắc phải giữ?”

**Tiêu chí qua chặng:** tự viết được hai phép thử (một compile error, một runtime exception) và chỉ rõ operation gây ra mỗi loại.

Với người mới, đây là bài quan trọng về **động cơ dùng Generics**. Không cần đi sâu bytecode ở chặng này; chỉ cần thấy rằng kiểu được mô tả chính xác trong API giúp chuyển nhiều lỗi sang thời điểm sớm hơn.

## 5. Chặng 2 — Ngôn ngữ của generic types

**Mục tiêu:** hiểu chữ ký trước khi kết hợp nhiều cơ chế. Hãy phân loại các thành phần sau:

~~~java
final class Box<T> {
    private final T value;

    Box(T value) { this.value = value; }

    T get() { return value; }
}

// Từ một method sử dụng:
// Box<String> box = new Box<>("Java");
~~~

- `T` trong `class Box<T>`: **type parameter** được khai báo.
- Các vị trí `T` trong field, constructor, return type: **type variable** được tham chiếu.
- `String` trong `Box<String>`: **type argument** cụ thể.
- `Box<String>`: một **parameterized type**.
- `new Box<>()`: **diamond**, compiler suy luận type argument từ ngữ cảnh; không phải raw instantiation.
- `Box` không có đối số kiểu ở vị trí sử dụng: có thể là **raw type** trong bối cảnh tương ứng, làm suy yếu type checking.
- `Box<?>`: parameterized type có **wildcard** chưa biết type argument cụ thể, khác raw Box.

Bài thực hành:
1. Viết `Pair<K,V>`, thử `Pair<String,Integer>` và `Pair<Integer,String>`.
2. Viết interface `Formatter<T>` có `String format(T value)` và một implementation nhận Integer.
3. Đánh dấu đâu là tên type parameter, đâu là đối số trong `Map<String,List<Integer>>`.
4. Giải thích vì sao `List<int>` không hợp lệ nhưng `List<Integer>` hợp lệ.

**Tiêu chí qua chặng:** đọc đúng hai type arguments trong một nested parameterized type và không nhầm phạm vi T của class với kiểu thực tế của object.

## 6. Chặng 3 — Method generic, type inference và giới hạn

**Mục tiêu:** hiểu khi nào một method cần **một type parameter của riêng nó**.

~~~java
static <T> T last(List<T> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("empty");
    }
    return values.get(values.size() - 1);
}
~~~

`<T>` đứng **trước kiểu trả về** và mô tả rằng kết quả có cùng kiểu phần tử với list đầu vào. Compiler có thể suy luận T từ đối số và ngữ cảnh lời gọi. Nếu method static nằm trong generic class, nó vẫn phải tự khai báo những type variables mà method static cần sử dụng, chứ không lấy T instance của class một cách tự động.

Bài thực hành:
- Gọi `last(List.of("A","B"))` và `last(List.of(10,20))`. Dự đoán kiểu tĩnh và giá trị trả về.
- Thử `String invalid = last(List.of(1,2));` rồi quan sát lỗi compiler.
- Thử method `static <T extends Number> double twice(T x)` sử dụng `doubleValue()`.
- So sánh generic method với một method nhận Object và trả Object: method nào **bảo toàn quan hệ kiểu** cho caller?

**Tiêu chí qua chặng:** giải thích được tại sao cú pháp `<T>` cần có ở declaration và vì sao `T extends Number` cho phép gọi các phương thức Number. Không đánh đồng **generic bounds** với khai báo một class kế thừa.

## 7. Chặng 4 — Invariance, wildcard và luồng dữ liệu

Đây thường là chặng phải luyện nhiều phép gọi nhất. Việc `Integer extends Number` **không** khiến `List<Integer> extends List<Number>`. Nếu có thể gán trực tiếp, một API nhận List<Number> sẽ có thể thêm Double vào List<Integer>, phá cam kết dữ liệu của caller.

Chọn chữ ký dựa trên **vai trò** của collection:

| Mục đích | Chữ ký gợi ý | Thao tác an toàn |
| --- | --- | --- |
| Không quan tâm element type | `List<?>` | Xem kích thước, đọc ra Object; không thêm giá trị non-null tùy ý. |
| Nguồn chỉ được đọc như Number | `List<? extends Number>` | Đọc được Number từ List<Integer>/List<Double>. |
| Đích nhận Integer | `List<? super Integer>` | Thêm Integer vào List<Integer>/List<Number>/List<Object>. |
| Vừa đọc vừa ghi cùng loại | `List<T>` | Lấy T và ghi lại T vào chính list khi implementation cho phép. |
| Nguồn/đích truyền một chiều | `List<? extends T>` và `List<? super T>` | Copy các giá trị T theo hướng producer → consumer. |

Quy tắc gợi nhớ **PECS (Producer Extends, Consumer Super)** chỉ là cách chọn đúng vị trí khi dữ liệu đi một chiều; nó **không** khuyên dùng wildcard ở mọi collection.

Bài thực hành:
- Viết hàm tính tổng nhận được cả `List<Integer>` và `List<Double>`, chỉ đọc phần tử.
- Viết hàm thêm Integer vào đích `List<Number>` và `List<Object>`.
- Viết phương thức `copyInto` có một T dùng chung cho nguồn và đích, với wildcards đúng chiều.
- So sánh với một phép **hoán đổi giá trị hai chiều** giữa hai `List<T>`; giải thích vì sao hai `List<?>` độc lập không tự cho phép hoán đổi chéo các phần tử.

**Tiêu chí qua chặng:** với mỗi lỗi compiler ở `add/get`, bạn chỉ rõ “kiểu đích có thể thực sự là gì?” thay vì trả lời máy móc “extends chỉ đọc, super chỉ ghi”.

### Chú ý thao tác trên collection vẫn phải được phép ở runtime

`List<? super Integer>` cho phép gọi `add(1)` theo **hệ kiểu**, nhưng nếu object là list không sửa được, thao tác vẫn có thể ném UnsupportedOperationException. Generics không mô tả mutability của mọi implementation.

## 8. Chặng 5 — Erasure và biên giới an toàn runtime

**Mục tiêu:** hiểu phần nào được compiler bảo vệ, phần nào JVM có thể kiểm tra, và khi nào cần validation riêng.

~~~text
List<String> (compile-time contract)
       │ kiểm tra add/get theo String
       ▼
Class runtime thường chỉ là ArrayList
       │ raw/unchecked aliases có thể phá invariant
       ▼
Typed read / inserted checkcast
       └── có thể phát ClassCastException
~~~

Các quy tắc cần tự chứng minh:
- `T` unbounded thường bị xóa thành Object; `T extends Number` bị xóa theo Number.
- Hai ArrayList instances được tạo bằng `new ArrayList<String>()` và `new ArrayList<Integer>()` thường có cùng runtime class.
- `instanceof List<?>` kiểm tra runtime List; `instanceof List<String>` không dùng được để xác minh type arguments cụ thể từ một biến Object.
- Không trực tiếp tạo `new T()`, `new T[10]` hoặc `new List<String>[10]` theo cách thông thường.
- Raw List/unchecked cast có thể làm dữ liệu sai kiểu tồn tại **trước** khi typed read gây ClassCastException.
- Kiểu generic không tự bảo đảm null policy, nội dung đúng schema hoặc phân quyền truy cập.

Bài thực hành:
1. Dùng `-Xlint:rawtypes,unchecked` để quan sát warnings của một raw alias được tạo cố ý trong file thử nghiệm.
2. Tách rõ **lúc ghi sai** và **lúc đọc thành String** trong test; dự đoán vì sao cảnh báo compile time không tự chặn write.
3. Nếu nhận `List<?>` từ nguồn không tin cậy, kiểm tra từng phần tử `instanceof String` rồi tạo bản sao typed. Đừng biến `(List<String>) unknown` thành “validator”.
4. Đối chiếu array covariance: Object[] alias tới String[] từ chối Integer bằng ArrayStoreException ngay tại write, khác nhiều generic collection cases.

**Tiêu chí qua chặng:** diễn giải được một lỗi ClassCastException theo **dataflow** và biết phòng ngừa ở boundary thay vì chỉ thêm một catch cuối pipeline.

## 9. Chặng 6 — Đọc JDK API và lựa chọn thiết kế

Hãy đọc chữ ký của API chuẩn **từng tham số một**, không chỉ copy ví dụ:

| API cần đọc | Hãy xác định |
| --- | --- |
| `List<E>.add(E)`, `List<E>.get(int)` | E được dùng để nhận và trả gì? |
| `Map<K,V>.put(K,V)`, `entrySet()` | K/V mô tả quan hệ khóa–giá trị ra sao? |
| `Optional<T>.map(Function<? super T,? extends U>)` | T biến thành U qua function ở chặng nào? |
| `Collections.copy(List<? super T>,List<? extends T>)` | Đích nhận, nguồn cấp T; tại sao đích phải có sẵn đủ phần tử? |
| `Comparator<? super T>`, `Comparable<? super T>` | Tại sao có thể dùng comparator của supertype? |
| `Stream.map` và `Stream.flatMap` | Khác nhau ở một R với một Stream<R> như thế nào? |

Một lưu ý về API thực tế: `Collections.copy` **thay thế theo chỉ số**, không tự `addAll` vào cuối; destination phải đủ kích thước. Đọc đúng generic signature **chưa đủ** nếu bỏ qua hợp đồng về mutability và dữ liệu.

Bài thực hành:
- Lấy chữ ký thật của hai API JDK rồi **đánh dấu nơi tiêu thụ T, nơi sản xuất R**.
- Đọc một pipeline `Stream<String> → map(String::length) → Stream<Integer> → toList()` và đoán kiểu ở từng bước.
- So sánh API `print(Object)` với `<T> void print(T)`: nếu chỉ chuyển mọi giá trị thành String và không có liên hệ kiểu khác, vì sao T có thể không cần thiết?

**Tiêu chí qua chặng:** giải thích được **chữ ký trước khi chạy thử**, đồng thời ghi rõ các giả định về null, empty collection, mutability và exception behavior.

## 10. Chặng 7 — Bài tổng hợp kết nối kiến thức

Mục tiêu bài tổng hợp **không phải lặp lại một loạt Box/PECS demos**, mà kết nối một cách có chủ đích: record generic, method generic có input→output linkage, một phép chuyển dữ liệu một chiều, một phép biến đổi đầu ra và quan sát class runtime.

Lưu thành **GenericsRoadmapDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class GenericsRoadmapDemo {
    record Attempt<T>(String topic, T value) {}

    static <T> T first(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        return values.get(0);
    }

    static <T> void copyInto(
            List<? extends T> source,
            List<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    static <T, R> List<R> transform(
            Collection<? extends T> source,
            Function<? super T, ? extends R> mapper) {
        List<R> output = new ArrayList<>();
        for (T item : source) {
            output.add(mapper.apply(item));
        }
        return output;
    }

    public static void main(String[] args) {
        Attempt<List<String>> lesson = new Attempt<>(
                "syntax", List.of("Type", "Bound")
        );

        System.out.println("first=" + first(lesson.value()));

        List<CharSequence> copied = new ArrayList<>();
        copyInto(lesson.value(), copied);
        System.out.println("copied=" + copied);

        List<Integer> sizes = transform(
                copied, CharSequence::length
        );
        System.out.println("lengths=" + sizes);

        System.out.println(
                "sameClass=" +
                (new ArrayList<String>().getClass()
                        == new ArrayList<Integer>().getClass())
        );
    }
}
~~~

Biên dịch và chạy với Java 17:

~~~bash
javac --release 17 -Xlint:all GenericsRoadmapDemo.java
java GenericsRoadmapDemo
~~~

Stdout:

~~~text
first=Type
copied=[Type, Bound]
lengths=[4, 5]
sameClass=true
~~~

### Giải thích luồng kiểu

1. `Attempt<T>` nhận T là `List<String>`, vì vậy `lesson.value()` có kiểu `List<String>`, không phải Object.
2. `first` nhận `List<String>` nên T được suy luận là String và trả String.
3. `copyInto` nhận nguồn List<String> và đích List<CharSequence> theo PECS, sao chép hai phần tử mà không đòi biến List<String> thành List<CharSequence>.
4. `transform` nhận các phần tử CharSequence, áp dụng `CharSequence::length` cho mỗi phần tử và xây `List<Integer>` mới; không sửa nguồn.
5. So sánh `getClass` chỉ cho thấy hai đối tượng ArrayList dùng chung runtime class, **không** chứng minh hai type arguments của các biến là như nhau.

### Bài biến thể để xác minh năng lực

- Thay `List<CharSequence> copied` bằng `List<Object>` và giải thích tại sao `copyInto` vẫn có thể thêm String; thay bằng `List<Integer>` và giải thích tại sao không còn một T phù hợp cho phép copy đó.
- Thay `CharSequence::length` bằng function trả Boolean, dự đoán `List<R>` trở thành kiểu gì.
- Thay list đích bằng `List.of(...)` rồi giải thích sự khác nhau giữa *compile được* và *thất bại khi gọi add*.
- Truyền một danh sách rỗng vào `first` và xác định exception, không gọi đó là “lỗi của Generics”.
- Tạo raw alias trong một file **test riêng** để thấy typed read có thể fail muộn; không thêm raw alias vào ví dụ production.

## 11. Bảng kiểm tra năng lực theo từng giai đoạn

| Chặng | Năng lực cần thể hiện không xem lời giải | Bằng chứng hoàn thành |
| --- | --- | --- |
| Nền | Giải thích type của biến vs runtime class và Object/cast | Phân biệt một compile error với runtime exception |
| 1 | Chỉ ra nhu cầu hợp đồng typed collection | Chuyển một List<Object> đang bị cast sang List<String> hợp lý |
| 2 | Đọc T/K/V và parameterized types | Box/Pair/interface generic được khai báo đúng |
| 3 | Viết generic method có kết quả phụ thuộc input | Không cần cast tại caller; có empty-input policy |
| 4 | Chọn wildcard dựa trên producer/consumer | Copy List<Integer> sang List<Number> không unchecked cast |
| 5 | Giải thích erasure và điều kiện heap pollution | Dự đoán chính xác operation gây CCE |
| 6 | Đọc chữ ký JDK có wildcard và mapper | Chỉ rõ T, R, chiều đọc/ghi, output type |
| 7 | Thiết kế và kiểm chứng một API nhỏ | Có ví dụ chạy được, output/edge cases được dự đoán trước |

Nếu còn sai ở chặng 4, quay về **invariance và quan hệ subtype**. Nếu sai ở chặng 5, tách **generic metadata tại compile time** với **object class tại runtime**. Nếu bạn hiểu lý thuyết nhưng không biên dịch được chặng 7, thử viết từng method với kiểu tường minh trước khi ghép toàn chương trình.

## 12. Bộ câu hỏi tổng hợp và đáp án ngắn

**Câu 1.** `Box<T>` và `Box<String>` khác nhau ở đâu?

**Đáp án:** `<T>` trong class declaration khai báo type parameter; String là type argument ở cách sử dụng, tạo parameterized type `Box<String>`.

**Câu 2.** `List<Integer>` có phải subtype của `List<Number>` không?

**Đáp án:** Không. Generic parameterization này invariant; dùng `? extends Number` khi cần đọc từ các list số khác nhau.

**Câu 3.** Tại sao `static <T> T identity(T value)` giữ được thông tin kiểu tốt hơn `Object identity(Object value)`?

**Đáp án:** Cùng T nối kiểu đầu vào và kiểu kết quả của lời gọi; caller có thể nhận type đã được compiler suy luận, thường không cần cast.

**Câu 4.** Với `List<? super Integer>`, vì sao `add(1)` hợp lệ nhưng `Integer x = get(0)` không được bảo đảm?

**Đáp án:** Actual type argument có thể là Integer, Number hoặc Object. Có thể ghi Integer; đọc ra chỉ chắc Object.

**Câu 5.** `List<?>` có giống raw `List` không?

**Đáp án:** Không. List<?> giữ một unknown captured element type, compiler không cho ghi phần tử non-null tùy ý. Raw List mất nhiều generic checks và có thể sinh unchecked warnings.

**Câu 6.** `String[]` và `List<String>` có giống nhau về kiểm tra runtime element type không?

**Đáp án:** Không. String[] có reified runtime component String và array-store check. Một ArrayList<String> thông thường không có String runtime token riêng để kiểm tra mỗi lần add.

**Câu 7.** Khi nào nên dùng generic method, khi nào kiểu cụ thể?

**Đáp án:** Dùng named type parameter khi có quan hệ kiểu cần giữ giữa các input/output; dùng kiểu cụ thể khi logic chỉ liên quan một entity hoặc một contract cố định. Không đặt `<T>` chỉ để gây ấn tượng “tổng quát”.

**Câu 8.** Khi một List<String> bị raw code thêm Integer, ClassCastException xuất hiện lúc nào?

**Đáp án:** Không bắt buộc ở raw add. Nó có thể xuất hiện khi typed read/usage khiến JVM cast Integer thành String; đọc như Object có thể vẫn thành công.

**Câu 9.** Type erasure có nghĩa Reflection không đọc được bất kỳ generic declaration nào không?

**Đáp án:** Không. Metadata của field/method/supertype có thể giữ Signature để Reflection phân tích declaration. Nhưng generic type argument của một đối tượng ArrayList thông thường không tự được reified.

**Câu 10.** Đích của `Collections.copy(dest,src)` có được tự tăng kích thước khi source dài hơn không?

**Đáp án:** Không. API này ghi đè theo index và đòi destination đủ kích thước; khác `addAll`.

## 13. Tự tổ chức buổi thực hành

Một quy trình học có thể lặp cho mỗi chặng:

1. **Dự đoán:** ghi kiểu của biểu thức và trạng thái compile/run trước khi dùng IDE.
2. **Chạy:** dùng `javac --release 17` để xác nhận; bật `-Xlint:rawtypes,unchecked` trong thử nghiệm legacy.
3. **Giải thích:** viết 2–3 câu về cơ chế kiểu và thời điểm kiểm tra.
4. **Biến thể:** thay type argument, wildcard hoặc method return type để xem điều gì đổi.
5. **So sánh:** kiểm tra cùng khái niệm ở một chữ ký JDK hoặc một test dữ liệu sai.
6. **Tự đánh giá:** chỉ qua chặng khi giải thích được cả case chạy đúng, case compiler từ chối và một edge case có ý nghĩa.

Không cần một lịch cố định hay số giờ tiêu chuẩn. Nếu đã giỏi collection và OOP, có thể chuyển nhanh qua chặng đầu; còn wildcard và erasure đáng dành thời gian để tự tạo test.

### Một số lỗi cần tự tìm ra mà không xem gợi ý

- Viết `List<int>` thay vì `List<Integer>`.
- Dùng `List<Object>` ở chỗ thực sự muốn nhận List<String> và List<Integer> nhưng chỉ đọc.
- Viết `static T method(T x)` trong generic class dù T chưa được khai báo tại static method.
- Lấy value từ `List<? super T>` rồi coi trực tiếp là T.
- Thêm T tùy ý vào `List<? extends T>`.
- Dùng raw `List` rồi suppress warning thay vì sửa invariant.
- Nghĩ `new T()` hợp lệ vì method có `<T>`.
- Nhầm `List.of` với một ArrayList sửa được.
- Dùng unchecked cast làm xác thực dữ liệu bên ngoài.

## 14. Tra cứu cú pháp ở đúng thời điểm

| Cú pháp | Khi nào ôn lại |
| --- | --- |
| `class Box<T>`, `interface Store<K,V>` | Chặng 2: declaration của generic type |
| `Box<String>`, `Map<String,Integer>` | Chặng 2: actual type arguments |
| `static <T> T first(List<T>)` | Chặng 3: generic method và quan hệ input/output |
| `<T extends Number>` | Chặng 3: upper bound để gọi members của Number |
| `List<?>` | Chặng 4: chỉ cần xem một danh sách có type chưa biết |
| `List<? extends Number>` | Chặng 4: producer và invariance |
| `List<? super Integer>` | Chặng 4: consumer và invariance |
| `Function<? super T,? extends R>` | Chặng 6–7: callback biến đổi kiểu |
| `Class<T>` và `Supplier<T>` | Sau chặng 5: cần class token hoặc factory runtime |
| `@SafeVarargs` / generic array constraints | Nâng cao sau khi hiểu erasure và heap pollution |

## 15. Định hướng học tiếp sau chương

Khi đã làm được mini-project và giải thích mọi câu hỏi tự kiểm tra, hãy đào sâu lần lượt theo vấn đề thực tế đang gặp:

- Generic inheritance, F-bounded types và bridge methods nếu bạn thiết kế hierarchy.
- Wildcard capture và bounded wildcards nâng cao nếu API cần nhiều nguồn/đích hoặc thao tác đọc–ghi nội bộ.
- Generic varargs, heap pollution và `@SafeVarargs` nếu đang xây tiện ích nhận varargs.
- `Type`, `ParameterizedType` và generic Reflection nếu cần đọc declaration metadata.
- `Function`, `Predicate`, `Stream.map` và `flatMap` nếu đang xây pipeline biến đổi dữ liệu.
- Tương thích nhị phân của public generic APIs nếu bạn phát hành library và tiến hóa bounds.

Với mỗi chủ đề, tiếp tục đặt ba câu hỏi: **Compiler biết quan hệ kiểu nào? JVM còn kiểm tra điều gì? Người gọi API được bảo đảm điều gì mà không cần cast?**

Đó là nền tảng để đi từ *sử dụng generics* tới *thiết kế generic APIs có chủ đích, an toàn và dễ bảo trì*.


</details>

- [Quay lại đầu trang](#back-to-top)
