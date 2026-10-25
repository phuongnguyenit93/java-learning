<a id="back-to-top"></a>

# Wildcard extends

## Menu
- [Ý nghĩa của ? extends T](#upper-bounded-wildcards-001)
- [Nhận List&lt;T&gt; và danh sách của các kiểu con](#upper-bounded-wildcards-002)
- [Đọc phần tử theo giới hạn trên](#upper-bounded-wildcards-003)
- [Vì sao không thể thêm một giá trị T tùy ý](#upper-bounded-wildcards-004)
- [Giới hạn thêm null không có nghĩa collection phải chấp nhận null](#upper-bounded-wildcards-005)
- [Wildcard extends không biến đối tượng nguồn thành bất biến](#upper-bounded-wildcards-006)
- [Áp dụng cho đầu vào cung cấp dữ liệu](#upper-bounded-wildcards-007)

## <a id="upper-bounded-wildcards-001">Ý nghĩa của ? extends T</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa của `? extends T` trong Java Generics

## 1. `? extends T` biểu diễn điều gì?

Trong Java Generics, `? extends T` là **upper-bounded wildcard** (wildcard có cận trên). Cú pháp này nói rằng đối số kiểu thực sự của một kiểu generic là **một kiểu chưa biết, nhưng kiểu đó phải là `T` hoặc một kiểu con của `T`**.

```java
List<? extends Number> values;
```

Trong khai báo trên, `values` có thể tham chiếu tới `List<Number>`, `List<Integer>`, `List<Double>`, `List<Long>` và các `List<S>` khác khi `S` là kiểu con của `Number`. Tuy nhiên, tại vị trí dùng biến `values`, trình biên dịch không biết chính xác `S` là kiểu nào.

Có hai phần cần đọc riêng:

- **`?`**: đại diện cho một đối số kiểu chưa biết; nó không phải `Object` và không có nghĩa được ghi giá trị tùy ý.
- **`extends T`**: ràng buộc đối số kiểu chưa biết phải thuộc tập `T` và các kiểu con của `T`; `T` được gọi là *upper bound* (cận trên).

Từ `extends` trong wildcard là một **ràng buộc kiểu**. Nó dùng được cả khi cận trên là một lớp (`Number`) và khi cận trên là một interface (`CharSequence`, `Comparable<?>`...). Không cần cận trên `T` là lớp kế thừa trực tiếp; quan hệ kiểu con gián tiếp cũng hợp lệ.

```java
List<Integer> integers = new ArrayList<>();
List<Double> doubles = new ArrayList<>();
List<Number> numbers = new ArrayList<>();

List<? extends Number> a = integers;
List<? extends Number> b = doubles;
List<? extends Number> c = numbers;
```

Cả ba phép gán đều hợp lệ. Đây là **các phép gán tham chiếu**, không tạo danh sách mới và không chuyển mọi phần tử sang `Number` trong bộ nhớ.

**Điểm quan trọng:** `? extends Number` nói *kiểu phần tử của danh sách nằm dưới cận Number*, chứ không nói *mỗi lần thêm có thể chọn bất kỳ kiểu con nào của Number*.

## 2. Mô hình kiểu ẩn: tại sao biết cận trên vẫn chưa biết kiểu chính xác?

Hãy tưởng tượng một phương thức nhận `List<? extends Number>`. Một bên gọi có thể đưa vào danh sách chỉ chứa `Integer`, bên khác đưa vào danh sách chỉ chứa `Double`. Trong thân phương thức, ta không biết lời gọi nào sẽ xuất hiện.

```java
static void inspect(List<? extends Number> source) {
    Number value = source.get(0); // Hợp lệ khi danh sách không rỗng
}
```

Trình biên dịch có đủ thông tin để kết luận rằng **mọi phần tử đọc ra đều có thể được xem như `Number`**. Nhưng không đủ thông tin để kết luận một `Integer` mới có thể được ghi vào một danh sách vốn có thể là `List<Double>`.

Có thể hình dung mối quan hệ như sau:

```text
                   Number
               /      |      \
          Integer   Double   Long
               \      |      /
          ? extends Number
          (một kiểu ẩn thuộc nhóm trên)
```

Sơ đồ chỉ minh họa các khả năng của cận trên; dấu `?` **không thay đổi kiểu thực** của từng danh sách đang được tham chiếu.

Thực tế, compiler có thể dùng một *capture type* nội bộ (ví dụ một tên biểu diễn kiểu ẩn như `CAP#1`) để ghi nhớ ràng buộc. Ta không viết tên kiểu nội bộ ấy trong mã nguồn. Điều quan trọng là các thao tác trên `List<? extends Number>` vẫn phải an toàn cho **mọi** kiểu có thể được thay thế vào dấu hỏi.

## 3. Tính bất biến của Generics và vai trò của `extends`

Dù `Integer extends Number`, Java Generics là **invariant** (bất biến đối với đối số kiểu): `List<Integer>` không phải là kiểu con của `List<Number>`.

Đây là đoạn minh họa lỗi biên dịch:

```java
List<Integer> integers = new ArrayList<>(List.of(1, 2));

// List<Number> numbers = integers; // Không biên dịch
```

Nếu phép gán được phép, người dùng `numbers` có thể thêm `Double` vào một danh sách được hứa chỉ chứa `Integer`. Java ngăn điều đó bằng kiểm tra tại thời điểm biên dịch.

Khi API chỉ **cần lấy ra các giá trị như `Number`**, có thể nới hợp đồng đầu vào thành:

```java
static double sum(List<? extends Number> source) {
    double total = 0;
    for (Number number : source) {
        total += number.doubleValue();
    }
    return total;
}
```

Giờ cả `List<Integer>` và `List<Double>` đều là đối số hợp lệ. API vẫn bảo đảm các phần tử đọc ra dùng được dưới kiểu `Number`.

| Kiểu đối số truyền vào | `List<Number>` | `List<? extends Number>` |
|---|---|---|
| `List<Number>` | Được | Được |
| `List<Integer>` | Không | Được |
| `List<Double>` | Không | Được |
| `List<java.math.BigDecimal>` | Không | Được |
| `List<Object>` | Không | Không |
| `List<String>` | Không | Không |

`Object` là kiểu cha của `Number`, nên `List<Object>` không đáp ứng `? extends Number`. `String` không kế thừa `Number`. Nhớ rằng việc chấp nhận một đối số danh sách phụ thuộc **tham số kiểu khai báo** của nó, không phải chỉ nhìn vào vài phần tử tình cờ đang có.

## 4. Quyền đọc: cận trên giúp compiler đảm bảo điều gì?

Với `List<? extends Number>`, mọi phần tử đọc ra đều gán an toàn cho `Number` hoặc `Object`:

```java
List<Integer> integers = List.of(10, 20, 30);
List<? extends Number> source = integers;

Number first = source.get(0);  // Được
Object same = source.get(0);   // Được
int size = source.size();      // Được
double value = first.doubleValue();

// Integer exact = source.get(0); // Không được: ? có thể là Double
```

Có thể gọi các phương thức được định nghĩa trên `Number`, chẳng hạn `intValue()`, `longValue()`, `doubleValue()`. Không thể gọi một phương thức chỉ thuộc `Integer` trực tiếp từ giá trị có kiểu tĩnh `Number` nếu chưa kiểm tra hoặc chuyển kiểu phù hợp.

Ví dụ:

```java
static void printAsDouble(List<? extends Number> source) {
    for (Number item : source) {
        System.out.println(item.doubleValue());
    }
}
```

Phương thức này được xác định hợp lệ bởi **cận trên** của wildcard. Nếu cận trên là `CharSequence`, phần tử đọc ra có thể dùng như `CharSequence` và gọi `length()`; không thể giả định đó là `String`.

Chú ý: `source.get(0)` chỉ hợp lệ lúc chạy nếu danh sách có phần tử. `extends` không bảo đảm danh sách không rỗng, không ngăn giá trị `null`, không bảo đảm danh sách mutable, cũng không đảm bảo các giá trị số phù hợp nghiệp vụ. Ví dụ phép `doubleValue()` có thể mất độ chính xác với số nguyên rất lớn hoặc `BigDecimal`; ràng buộc kiểu và độ chính xác số học là hai vấn đề riêng.

## 5. Quyền ghi: vì sao không thể thêm `Integer`, `Double`, hay `Number`?

Khai báo:

```java
List<? extends Number> source = new ArrayList<Integer>();
```

Phía sau `source` có thể là `List<Integer>`. Nếu compiler cho thêm một `Double` thì hợp đồng danh sách gốc bị phá vỡ. Nhưng ngay cả `source.add(123)` cũng không được phép, vì ở một lời gọi khác `source` hoàn toàn có thể tham chiếu `List<Double>`.

```java
List<? extends Number> source =
        new ArrayList<Integer>(List.of(1, 2, 3));

// source.add(42);            // Lỗi biên dịch
// source.add(2.5);           // Lỗi biên dịch
// source.add((Number) 42);   // Vẫn lỗi biên dịch
// source.set(0, 42);         // Lỗi biên dịch
```

Dù đối số ghi là một `Number`, ta chỉ biết kiểu ẩn `S extends Number`. Từ “`Integer` là một `Number`” không suy ra “`Integer` có thể gán cho mọi `S` thuộc các kiểu con của `Number`”.

Một ngoại lệ về type checking là `null`:

```java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<? extends Number> view = integers;

view.add(null); // Biên dịch được vì null có thể gán cho mọi kiểu tham chiếu
```

`ArrayList` ở ví dụ này cho phép null và danh sách thực sự được thêm null. Nhưng **không phải mọi implementation của `List` đều cho phép sửa đổi hoặc thêm null**. Ví dụ `List.of(...)` là danh sách không sửa đổi được; thao tác `add` sẽ ném `UnsupportedOperationException`. Phân biệt rõ **một thao tác được chấp nhận về kiểu** với **một thao tác thành công lúc chạy**.

## 6. Wildcard `extends` không biến danh sách thành immutable

`List<? extends Number>` giới hạn **kiểu giá trị được phép chèn qua tham chiếu**, không biến đối tượng nguồn thành immutable. Nếu nguồn thực là `ArrayList<Integer>`, một alias `List<Integer>` khác vẫn có thể thay đổi phần tử, và những thao tác không cần cung cấp phần tử mới như `clear()` hoặc `remove(int)` vẫn biên dịch qua wildcard.

```java
List<Integer> original = new ArrayList<>(List.of(4, 5, 6));
List<? extends Number> source = original;
source.remove(0);
System.out.println(original); // [5, 6]
```

Với một danh sách unmodifiable, thao tác xóa có thể ném `UnsupportedOperationException`. Muốn bảo vệ nội dung, cần cơ chế quản lý alias, wrapper không sửa được hoặc snapshot; bài riêng về **wildcard và tính bất biến** sẽ phân tích chi tiết các lựa chọn đó.

## 7. Ví dụ Java 17 hoàn chỉnh: đọc, chuyển dữ liệu và giữ an toàn kiểu

Tạo tệp `ExtendsWildcardDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsWildcardDemo {
    static double sum(List<? extends Number> source) {
        double result = 0;
        for (Number n : source) {
            result += n.doubleValue();
        }
        return result;
    }

    static void transfer(
            List<? extends Number> source,
            List<? super Number> destination) {
        for (Number n : source) {
            destination.add(n);
        }
    }

    static void swapFirstTwo(List<? extends Number> source) {
        swapCaptured(source);
    }

    private static <E extends Number> void swapCaptured(List<E> values) {
        if (values.size() < 2) {
            return;
        }
        E first = values.get(0);
        values.set(0, values.get(1));
        values.set(1, first);
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(10, 20, 30));
        List<Double> doubles = List.of(1.5, 2.5);

        System.out.println("Integer sum: " + sum(integers));
        System.out.println("Double sum: " + sum(doubles));

        List<Number> combined = new ArrayList<>();
        transfer(integers, combined);
        transfer(doubles, combined);
        System.out.println("Combined: " + combined);

        List<? extends Number> view = integers;
        Number first = view.get(0);
        System.out.println("First: " + first);

        swapFirstTwo(view);
        System.out.println("After swap: " + integers);

        view.add(null);
        System.out.println("After null: " + integers);
    }
}
```

Biên dịch và chạy:

```bash
javac ExtendsWildcardDemo.java
java ExtendsWildcardDemo
```

Stdout:

```text
Integer sum: 60.0
Double sum: 4.0
Combined: [10, 20, 30, 1.5, 2.5]
First: 10
After swap: [20, 10, 30]
After null: [20, 10, 30, null]
```

Phân tích chương trình:

1. `sum` nhận nguồn có đối số kiểu là `Number` hoặc bất kỳ kiểu con nào. Cơ thể hàm chỉ cần đọc mỗi phần tử dưới kiểu `Number` và lấy `doubleValue()`.
2. `transfer` dùng nguồn `List<? extends Number>` để đọc các số an toàn. Đích `List<? super Number>` là danh sách có kiểu phần tử `Number` hoặc một kiểu cha của nó (`Object`), nên cho phép thêm các giá trị `Number`. `combined` là một `List<Number>` mới, không phải alias của `integers` hoặc `doubles`.
3. `view` cùng tham chiếu `integers`. `get(0)` trả về giá trị có thể đọc an toàn dưới kiểu `Number`.
4. `swapFirstTwo` không tạo một `Integer` hoặc `Double` mới để thêm vào danh sách. Nó đổi chỗ hai phần tử đã có, thông qua helper giữ cùng kiểu phần tử ẩn. Phần 8 giải thích cơ chế này.
5. `view.add(null)` hợp lệ ở mức biên dịch. Vì đối tượng thực là `ArrayList<Integer>`, việc thêm null thành công và kết quả hiển thị trong `integers`. Nếu gọi lại `sum(integers)` sau bước này, vòng lặp sử dụng `n.doubleValue()` trên null sẽ gây `NullPointerException`; ví dụ chủ động tính tổng trước khi thêm null.

Các kết quả số trong ví dụ đều biểu diễn chính xác ở dạng `double` nên stdout xác định rõ. Trong ứng dụng cần tính tiền hoặc độ chính xác thập phân, không nên dùng phép cộng `doubleValue()` thay cho mô hình số học phù hợp.

## 8. Wildcard capture: có thể hoán đổi phần tử như thế nào?

Mỗi `? extends Number` là một **kiểu phần tử chưa biết nhưng nhất quán**. Khi cần ghi lại chính những phần tử đã lấy từ cùng danh sách, helper generic có thể *capture* kiểu ẩn đó:

```java
static void swapFirstTwo(List<? extends Number> source) {
    swapCaptured(source);
}
private static <E extends Number> void swapCaptured(List<E> xs) {
    if (xs.size() < 2) return;
    E first = xs.get(0);
    xs.set(0, xs.get(1));
    xs.set(1, first);
}
```

Compiler cho ghi `E` đã lấy từ chính `xs`, **không** cho thêm một `Number` bất kỳ vào `List<? extends Number>`. Nếu list không cho phép `set`, lời gọi vẫn có thể thất bại lúc chạy. Chương trình hoàn chỉnh ở mục 7 đã minh họa việc hoán đổi; bài về **giới hạn thêm `T`** tiếp tục chứng minh wildcard capture.

## 9. Quy tắc PECS: tại sao `extends` phù hợp với nguồn dữ liệu?

**PECS — Producer Extends, Consumer Super**: nếu tham số cung cấp phần tử mà thuật toán **đọc**, dùng `? extends T`; nếu tham số là nơi thuật toán **ghi** các phần tử `T`, dùng `? super T`.

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> destination) {
    for (T value : source) destination.add(value);
}
```

Chữ ký nối một producer chứa `T` hoặc subtype với một consumer có thể nhận `T`. Ví dụ nguồn `List<Integer>`, đích `List<Number>` hợp lệ. Đây là một **quy tắc lựa chọn tham số API**, không hứa source immutable hay destination mutable. Phần thiết kế API đầu vào cung cấp dữ liệu ở cuối chương giải thích cách chọn cả `Iterable`/`Collection`/`List` theo thao tác cần thiết.

## 10. `extends` dùng được với interface

Trong cú pháp wildcard, `extends` dùng cả với interface, ngay cả khi một lớp dùng từ khóa `implements` để triển khai interface đó.

```java
static void showLengths(List<? extends CharSequence> texts) {
    for (CharSequence text : texts) {
        System.out.println(text.length());
    }
}
```

Hàm này nhận `List<String>` hoặc `List<StringBuilder>` vì cả `String` và `StringBuilder` đều triển khai `CharSequence`.

```java
List<String> names = List.of("An", "Binh");
List<StringBuilder> builders =
        List.of(new StringBuilder("Java"));

showLengths(names);
showLengths(builders);
```

Chú ý không thể viết `texts.add("new")` bên trong hàm. Dù `String` là `CharSequence`, danh sách thực tế có thể là `List<StringBuilder>`. Chỉ có **bảo đảm khi đọc** rằng phần tử có thể sử dụng như `CharSequence`.

Khi cần giới hạn bằng nhiều interface, Java có thể khai báo type parameter có nhiều bound, ví dụ `<T extends Number & Comparable<T>>`; đây là cú pháp type parameter có tên và là bài toán khác với wildcard đơn giản `? extends T`. Mỗi wildcard `? extends ...` thường diễn tả một cận trên theo cú pháp kiểu đã chọn.

## 11. So sánh `? extends T`, `T`, `?` và `? super T`

| Cách khai báo với `Number` | Nhận những kiểu danh sách nào? | Kiểu đọc an toàn | Thêm `Integer` bất kỳ? |
|---|---|---|---|
| `List<Number>` | `List<Number>` | `Number` | Có |
| `List<? extends Number>` | `List<Number>` và `List` của các kiểu con `Number` | `Number` | Không |
| `List<?>` | `List` với đối số kiểu bất kỳ | `Object` | Không |
| `List<? super Number>` | `List<Number>`, `List<Object>` | `Object` | Có |

Ở bảng này, “thêm” chỉ là **được compiler cho phép**; đối tượng thực vẫn có thể không hỗ trợ sửa đổi.

`List<?>` và `List<? extends Object>` mang ý nghĩa rất gần nhau: mọi đối số kiểu tham chiếu đều nằm dưới `Object`, nên cận `Object` không giúp nâng mức đảm bảo khi đọc lên cao hơn `Object`. Ngược lại, `List<? extends Number>` đem lại thông tin hữu ích rằng các phần tử thuộc họ `Number`.

Với `List<? super Number>`, giá trị lấy ra chỉ chắc chắn có kiểu `Object` vì danh sách có thể khai báo kiểu phần tử là `Object`. Đổi lại, ta có thể thêm `Number` và các kiểu con, ví dụ `Integer`. Đây là đối xứng **đọc từ cận trên, ghi theo cận dưới**.

## 12. Những lỗi suy luận thường gặp

**Lỗi 1 — “`extends` nghĩa là có thể thêm mọi kiểu con.”** Không. `List<? extends Number>` có thể thực sự là `List<Double>`; thêm một `Integer` là không an toàn. Chỉ cần biết upper bound chưa biết được kiểu chính xác để ghi.

**Lỗi 2 — “`List<Integer>` là `List<Number>`.”** Không. Java Generics bất biến; dùng `List<? extends Number>` khi mục tiêu là chấp nhận nhiều nguồn để đọc như `Number`.

**Lỗi 3 — “Đọc được `Number` thì ghi được `Number`.”** Không. Thông tin đủ để **đọc lên kiểu cha** không đủ để ghi một kiểu cha vào nơi có thể cần kiểu con cụ thể.

**Lỗi 4 — “`extends` làm danh sách immutable.”** Không. `clear()` hoặc `remove(...)` vẫn hợp lệ theo kiểu. Mutability là hợp đồng của implementation và cách cung cấp đối tượng.

**Lỗi 5 — “`? extends Number` chắc chắn là `Integer` nếu trước mắt thấy toàn số nguyên.”** Không. Compiler dựa vào **kiểu tĩnh và upper bound**, không tự suy luận kiểu generic từ những phần tử tình cờ quan sát. `List<Number>` hoàn toàn có thể đang chứa một loạt `Integer` mà vẫn không phải `List<Integer>`.

**Lỗi 6 — “Ép `(List<Integer>)` sẽ biến một `List<? extends Number>` thành danh sách số nguyên an toàn.”** Việc ép kiểu không tự kiểm chứng phần tử do type erasure; có thể nhận cảnh báo unchecked và để lại lỗi runtime muộn. Ưu tiên thiết kế API đủ ràng buộc kiểu hoặc kiểm tra dữ liệu khi buộc phải xử lý nguồn không tin cậy.

**Lỗi 7 — “`? extends T` là cú pháp tạo đối tượng.”** Không. Wildcard là cú pháp đối số kiểu tại nơi sử dụng; không thể viết `new ArrayList<? extends Number>()`. Có thể tạo `new ArrayList<Integer>()` và gán tham chiếu cho biến `List<? extends Number>`.

## 13. Bài tập tự kiểm tra

Cho mã:

```java
List<Integer> ints = new ArrayList<>(List.of(2, 4, 6));
List<Double> doubles = new ArrayList<>(List.of(1.5, 2.5));
List<? extends Number> source = ints;
```

Hãy dự đoán:

1. `source.get(0)` gán được cho `Number` hay `Integer` mà không cần ép kiểu?
2. `source.add(7)` có biên dịch không? `source.add(null)` có biên dịch không?
3. `source.clear()` có biên dịch không và nó ảnh hưởng `ints` thế nào?
4. `List<Number> numbers = ints;` có biên dịch không?
5. `source = doubles;` có biên dịch không? Sau phép gán, `source.get(0)` an toàn ở kiểu tĩnh nào?
6. `List<? extends Number> data = new ArrayList<Object>();` có hợp lệ không?

**Đáp án và lý do:**

1. Gán cho `Number` (hoặc `Object`) an toàn; compiler không biết kiểu ẩn chính xác là `Integer`.
2. `add(7)` không biên dịch vì kiểu ẩn có thể không phải `Integer`. `add(null)` biên dịch vì null tương thích mọi kiểu tham chiếu; mức runtime phụ thuộc implementation.
3. `clear()` biên dịch. Ở đây `source` và `ints` cùng trỏ đến `ArrayList` mutable, nên `ints` sẽ trống sau khi xóa.
4. Không. `List<Integer>` không phải `List<Number>`.
5. Có. `List<Double>` cũng thỏa `? extends Number`; sau phép gán vẫn chỉ bảo đảm đọc dưới kiểu `Number`.
6. Không. `Object` là kiểu cha, không phải `Number` hay kiểu con của `Number`.

## 14. Nguyên tắc áp dụng

Chọn **`? extends T`** khi API cần **lấy giá trị từ một nguồn có phần tử thuộc `T` hoặc kiểu con của `T`**, và không cần tự ý thêm một giá trị `T` mới vào chính nguồn ấy. Upper bound cho phép đọc kết quả như `T` mà vẫn nhận nhiều loại `List<S>` an toàn ở compile time. Dù vậy, dấu `?` vẫn biểu thị kiểu ẩn: quyền ghi bị giới hạn để bảo vệ danh sách gốc, và khả năng sửa đổi thực tế do đối tượng `List` quyết định.

Nếu code cần ghi `T` vào danh sách, cân nhắc `? super T`; nếu cần giữ quan hệ kiểu chính xác giữa đầu vào và đầu ra hoặc giữa nhiều tham số, cân nhắc một type parameter có tên như `<T>`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upper-bounded-wildcards-002">Nhận List&lt;T&gt; và danh sách của các kiểu con</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận `List<T>` và danh sách của các kiểu con bằng `? extends T`

Một phương thức cần xử lý các giá trị kiểu `T` thường cũng muốn nhận danh sách chứa **kiểu con của `T`**. Trong Java Generics, chữ ký `List<T>` khi `T` đã cố định **không tự nhận** `List<Subtype>` chỉ vì `Subtype extends T`. Cách diễn đạt một *nguồn dữ liệu có phần tử là `T` hoặc một subtype của `T`* là **`List<? extends T>`**.

Ví dụ, `Integer` và `Double` đều là subtype của `Number`:

~~~java
List<Integer> integers = List.of(1, 2, 3);
List<Double> doubles = List.of(1.5, 2.5);

// List<Number> a = integers; // LỖI BIÊN DỊCH.
List<? extends Number> b = integers; // Hợp lệ.
List<? extends Number> c = doubles;  // Hợp lệ.
~~~

Đây là **đoạn minh họa cần đặt trong class và import `java.util.List`**. Dòng gán trực tiếp vào `List<Number>` bị chú thích vì cố ý sai kiểu. Hai dòng sử dụng wildcard hợp lệ mà không ép kiểu không kiểm tra và không sao chép danh sách.

Ý nghĩa quan trọng: **`? extends T` cho phép nhận nhiều kiểu danh sách, đổi lại compiler hạn chế ghi phần tử mới qua tham chiếu đó.** Cần hiểu cả hai mặt để xây dựng API đúng.

## 1. Vì sao `List<T>` không tự nhận `List<Subtype>` khi `T` đã xác định?

Với biến kiểu thường, quan hệ subtype hoạt động như mong đợi:

~~~java
Integer number = 10;
Number wider = number; // Hợp lệ: Integer extends Number.
~~~

Nhưng Java generic **bất biến (invariant)** theo đối số kiểu:

~~~java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers; // LỖI BIÊN DỊCH.
~~~

Đây là **đoạn cố ý không biên dịch**, cần import `java.util.List` và `java.util.ArrayList`. Nếu phép gán ấy được phép, mã dùng `numbers` có thể thêm `Double`:

~~~java
numbers.add(3.14); // Chỉ là giả định nếu phép gán sai phía trên được chấp nhận.
~~~

Khi đó, danh sách ban đầu được khai báo `List<Integer>` lại chứa `Double`, gây nguy cơ `ClassCastException` lúc một nơi khác đọc phần tử như `Integer`. Compiler chặn nguy cơ ngay từ phép gán.

Nói bằng quan hệ kiểu:

- `Integer` là subtype của `Number`.
- `List<Integer>` **không** là subtype của `List<Number>`.
- `List<Integer>` có thể được dùng như `List<? extends Number>`.
- `List<Number>` cũng có thể được dùng như `List<? extends Number>`.

Việc `List<Integer>` không phải `List<Number>` là quy tắc **invariance**; việc wildcard upper bound cho phép sử dụng `List<Integer>` ở nơi yêu cầu `List<? extends Number>` là một quan hệ kiểu **được Java chứng minh an toàn**.

## 2. Cách đọc chữ ký `List<? extends T>`

Trong chữ ký:

~~~java
static void process(List<? extends Number> source) {
    // Được đọc mỗi phần tử như Number.
}
~~~

`?` là **wildcard** biểu thị một đối số kiểu chưa được biết chính xác. `extends Number` giới hạn kiểu ẩn đó phải là `Number` hoặc một subtype phù hợp của `Number`.

Nhờ vậy, chữ ký có thể nhận:

~~~java
List<Number> numbers = new ArrayList<>();
List<Integer> integers = new ArrayList<>();
List<Double> doubles = new ArrayList<>();

process(numbers);  // Có.
process(integers); // Có.
process(doubles);  // Có.
~~~

Hai đoạn trên là **mã phương thức/lời gọi minh họa**, cần đặt trong class và import `java.util.*`. Chúng cho thấy một phương thức nhận nhiều kiểu đối số mà vẫn có hợp đồng an toàn.

Tuy nhiên, **không phải bất kỳ subtype nào của `Object` cũng được nhận**: `List<String>` không phù hợp `List<? extends Number>` vì `String` không phải subtype của `Number`.

Điều kiện có thể đọc như sau:

> Mỗi phần tử mà danh sách cung cấp đều phải có thể được sử dụng theo hợp đồng `T`.

Điều kiện này quan trọng khi phương thức gọi các API của `T`. Với `Number`, ta có thể gọi `doubleValue()`, `intValue()`, v.v. trên mỗi phần tử dù danh sách thật chứa `Integer` hay `Double`.

## 3. Ví dụ Java 17+ hoàn chỉnh: một hàm nhận cả số nguyên lẫn số thực

Tạo tệp **`ExtendsNumbersDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ExtendsNumbersDemo {
    private static void printNumbers(List<? extends Number> values) {
        System.out.print("values:");
        for (Number value : values) {
            System.out.print(" " + value);
        }
        System.out.println();
    }

    private static double sum(List<? extends Number> values) {
        double total = 0.0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(2, 4, 6));
        List<Double> doubles = new ArrayList<>(List.of(1.5, 2.5));
        List<Number> mixed = new ArrayList<>(List.of(10, 20.5));

        printNumbers(integers);
        printNumbers(doubles);
        printNumbers(mixed);

        System.out.println("sum integers = " + sum(integers));
        System.out.println("sum doubles = " + sum(doubles));
        System.out.println("sum mixed = " + sum(mixed));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ExtendsNumbersDemo.java
java ExtendsNumbersDemo
~~~

Stdout chính xác:

~~~text
values: 2 4 6
values: 1.5 2.5
values: 10 20.5
sum integers = 12.0
sum doubles = 4.0
sum mixed = 30.5
~~~

Phương thức `printNumbers` và `sum` chỉ phụ thuộc vào các hành vi được bảo đảm bởi `Number`, nên không cần biết subtype cụ thể của phần tử. `doubleValue()` là API được khai báo trong `Number`; ở runtime, implementation tương ứng của `Integer` hay `Double` được gọi.

Điểm lợi là **thay đổi phía caller không buộc thay chữ ký hàm**: cùng một hàm có thể làm việc với `List<Integer>`, `List<Double>`, `List<Number>` và danh sách của các subclass `Number` khác.

## 4. Đọc được như `T` nhưng không được tùy tiện ghi `T`

Với một đối số `List<? extends Number>`, mỗi phần tử đọc ra có thể sử dụng an toàn như `Number`, dù danh sách thật mang `Integer` hay `Double`:

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<? extends Number> input = integers;
Number first = input.get(0);
// input.add(3); // Không biên dịch: kiểu phần tử ẩn có thể là Double.
~~~

Chính vì vậy, chữ ký nhận danh sách của **nhiều subtype** mà không đánh mất kiểm tra kiểu. Nếu dùng `List<Number>`, compiler không cho truyền biến `List<Integer>` trực tiếp. Đặc biệt, việc nhìn nguồn qua wildcard **không tạo list mới** và không chuyển kiểu phần tử lúc runtime.

Phép `add(null)` có thể qua type checking, nhưng thành công lúc chạy còn phụ thuộc chính sách null/mutability của collection; phép `remove` hoặc `clear` cũng có thể hợp kiểu và sửa dữ liệu thật. Các bài tiếp theo tách riêng ba vấn đề **đọc theo upper bound**, **cấm ghi giá trị tùy ý** và **null** để không đồng nhất chúng.

## 5. Giải nghĩa `List<? extends T>` khi `T` là tham số generic

Đến đây, ta đã dùng `Number` như một upper bound cụ thể. Trong phương thức generic, `T` còn có thể là tham số kiểu:

~~~java
static <T> T first(List<? extends T> source) {
    if (source.isEmpty()) {
        throw new IllegalArgumentException("source must not be empty");
    }
    return source.get(0);
}
~~~

Đây là **một phương thức cần đặt trong class và import `java.util.List`**. `T` là kiểu mà caller muốn nhận, còn `? extends T` cho phép phần tử nguồn thuộc `T` hoặc subtype của `T`.

Ví dụ sử dụng với `T` được chỉ định rõ:

~~~java
List<Integer> integers = List.of(10, 20);
List<Double> doubles = List.of(1.5, 2.5);

Number a = MyUtils.<Number>first(integers); // Hợp lệ.
Number b = MyUtils.<Number>first(doubles);  // Hợp lệ.
~~~

Đoạn gọi này chỉ minh họa chữ ký; giả sử phương thức `first` là `static` trong class `MyUtils`. Ký pháp `<Number>` là **explicit type argument** chọn `T = Number`. Khi đó `List<? extends T>` trở thành `List<? extends Number>`, nên nhận được cả danh sách `Integer` và `Double`.

Một cách viết tương đương trong chương trình thật là đặt `first` và lời gọi vào cùng một class rồi gọi `ClassName.<Number>first(...)`.

### Điều dễ nhầm: phương thức generic nhận `List<T>` cũng có thể dùng cho nhiều kiểu

Cần phân biệt **`T` được cố định** với **`T` được suy luận độc lập mỗi lần gọi**:

~~~java
static <T> void show(List<T> values) {
    System.out.println(values.size());
}
~~~

Phương thức trên có thể được gọi lần lượt với `List<Integer>`, `List<String>`, `List<Double>`, vì compiler chọn một `T` phù hợp cho **mỗi lần gọi riêng**:

~~~java
show(List.of(1, 2));     // T có thể là Integer.
show(List.of("a", "b")); // T có thể là String.
~~~

Các đoạn này là **mã phương thức/lời gọi minh họa cần đặt trong class**. Vì vậy câu “`List<T>` không bao giờ nhận danh sách subtype” sẽ là khái quát **sai** nếu không nói rõ `T` đang được xác định như thế nào.

Khác biệt thật sự xuất hiện khi ta **cần duy trì một upper bound đã biết**, chẳng hạn tham số `Number` cố định, hoặc cần giữ liên hệ kiểu giữa nhiều tham số của cùng một phương thức. Khi `T` đã là `Number`, `List<T>` nghĩa là `List<Number>`, còn `List<? extends T>` vẫn nhận `List<Integer>`, `List<Double>` và `List<Number>`.

## 6. Khi hàm cần đọc nguồn và ghi vào đích: ghép `extends` với `super`

Một hàm sao chép các phần tử kiểu `T` có hai vai trò trái ngược nhau:

- **Nguồn (producer)** cung cấp những phần tử có thể dùng như `T`: `List<? extends T>`.
- **Đích (consumer)** phải nhận được mọi phần tử `T`: `List<? super T>`.

Chữ ký phù hợp:

~~~java
static <T> void copyTo(List<? extends T> source,
                       List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
~~~

Đây là **phương thức cần đặt trong class và import `java.util.List`**. Nó được bảo đảm an toàn khi caller cung cấp các kiểu tương thích. Công thức dễ nhớ là **PECS — Producer Extends, Consumer Super**.

Tạo chương trình hoàn chỉnh **`GenericCopyDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class GenericCopyDemo {
    static <T> void copyTo(List<? extends T> source,
                           List<? super T> destination) {
        for (T element : source) {
            destination.add(element);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);
        List<Double> doubles = List.of(3.5, 4.5);
        List<Number> numbers = new ArrayList<>();

        copyTo(integers, numbers);
        copyTo(doubles, numbers);

        List<Object> objects = new ArrayList<>();
        copyTo(numbers, objects);

        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);
        System.out.println("integers = " + integers);
    }
}
~~~

Biên dịch và chạy trên Java 17+:

~~~bash
javac GenericCopyDemo.java
java GenericCopyDemo
~~~

Stdout chính xác:

~~~text
numbers = [1, 2, 3.5, 4.5]
objects = [1, 2, 3.5, 4.5]
integers = [1, 2]
~~~

Giải thích kiểu:

1. `integers` là `List<Integer>`. Nó là nguồn hợp lệ khi đích là `List<Number>`, vì `Integer` có thể được nhận như `Number`.
2. `doubles` là `List<Double>`. Nó cũng cung cấp các `Number` hợp lệ cho đích.
3. `numbers` là một `List<Number>`, tiếp tục là nguồn hợp lệ cho `List<Object>`.
4. Không có phép gán sai `List<Integer>` sang `List<Number>`; phương thức **duyệt phần tử** rồi thêm từng giá trị vào danh sách khác.
5. `integers` giữ nguyên, vì nguồn không bị thay đổi.

Nếu thật sự chỉ cần “thêm tất cả phần tử” vào collection đích, thư viện chuẩn đã có `Collection.addAll(Collection<? extends E>)`. Tương tự, `Collections.copy` có chữ ký `<T> void copy(List<? super T> dest, List<? extends T> src)`, nhưng chức năng của nó là **ghi đè các vị trí đã có** và yêu cầu đích đủ độ dài. Không nhầm `Collections.copy` với phép nối vào cuối như `addAll`.

## 7. Có thể giữ danh sách gốc và vẫn tạo `List<T>` rộng hơn

Đôi khi phương thức phía sau thực sự yêu cầu `List<Number>` vì cần thêm các `Number` khác loại. Khi đó **không ép** `List<Integer>` thành `List<Number>`; hãy tạo bản sao:

~~~java
List<Integer> integers = List.of(10, 20);

// List<Number> wrong = integers; // Không biên dịch.
List<Number> right = new ArrayList<>(integers);
right.add(2.5);

System.out.println(integers); // [10, 20]
System.out.println(right);    // [10, 20, 2.5]
~~~

Đây là **đoạn mã cần đặt trong class và import `java.util.*`**. Constructor `ArrayList(Collection<? extends E>)` nhận danh sách có phần tử subtype của `E`. Kiểu đích giúp suy luận `E = Number`. Bản sao giữ các tham chiếu tới phần tử, nhưng cấu trúc danh sách là độc lập: thêm vào `right` không làm thay đổi `integers`.

Phân biệt rõ hai nhu cầu:

- **Chỉ cần quan sát hoặc xử lý các phần tử như `T`**: nhận `List<? extends T>`, không bắt caller sao chép.
- **Cần sở hữu collection chứa được nhiều subtype của `T` và thường xuyên thêm giá trị**: tạo/chấp nhận `List<T>` đúng kiểu và sao chép nguồn khi cần.

Wildcard không làm đổi kiểu khai báo của collection thật.

## 8. Khi upper bound là interface hoặc lớp tự định nghĩa

`extends` trong wildcard là cú pháp áp dụng cho **cả class và interface**, không chỉ class. Ví dụ:

~~~java
static int totalLength(List<? extends CharSequence> texts) {
    int total = 0;
    for (CharSequence text : texts) {
        total += text.length();
    }
    return total;
}
~~~

Đây là **phương thức minh họa cần đặt trong class** và import `java.util.List`. Cả `String` và `StringBuilder` đều triển khai `CharSequence`, nên danh sách tương ứng là đầu vào hợp lệ:

~~~java
int x = totalLength(List.of("Hi", "Java"));
int y = totalLength(List.of(new StringBuilder("AB"),
                            new StringBuilder("C")));
~~~

`x` bằng `6` và `y` bằng `3`. Các phần tử có các lớp thực khác nhau nhưng đều cung cấp phương thức `length()` được khai báo bởi `CharSequence`.

Ở đây, `List<? extends CharSequence>` **không** hứa danh sách chứa đúng kiểu `CharSequence`. Nó hứa danh sách chứa một kiểu cụ thể nào đó phù hợp với `CharSequence`. Vì thế phương thức được phép gọi `length()` nhưng không được tự thêm một chuỗi mới qua tham số wildcard.

Wildcard có thể dùng với lớp tự định nghĩa:

~~~java
class Animal {
    String name() { return "animal"; }
}
class Dog extends Animal {
    @Override String name() { return "dog"; }
}

static void printAnimals(List<? extends Animal> animals) {
    for (Animal animal : animals) {
        System.out.println(animal.name());
    }
}
~~~

Đây là **các khai báo minh họa**. `printAnimals` nhận `List<Animal>`, `List<Dog>` hoặc một danh sách lớp con khác của `Animal`. Nếu cần gọi phương thức chỉ tồn tại trên `Dog`, phải thu hẹp kiểu một cách an toàn, vì hợp đồng chung chỉ biết `Animal`.

## 9. Wildcard capture: tại sao đôi khi vẫn có thể ghi trở lại cùng kiểu?

Với `List<? extends T>`, ta không được đưa một phần tử `T` tùy ý vào danh sách vì subtype thật chưa biết. Tuy nhiên, nếu phần tử được lấy từ **chính danh sách ấy**, phương thức generic có thể nắm bắt kiểu ẩn (wildcard capture) và sử dụng lại nó.

Tạo tệp **`CaptureSwapDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class CaptureSwapDemo {
    static void swapFirstTwo(List<? extends Number> values) {
        if (values.size() >= 2) {
            swapCaptured(values, 0, 1);
        }
    }

    private static <E> void swapCaptured(List<E> list, int i, int j) {
        E previous = list.get(i);
        list.set(i, list.get(j));
        list.set(j, previous);
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(1, 2, 3));
        List<Double> doubles = new ArrayList<>(List.of(1.5, 2.5));

        swapFirstTwo(integers);
        swapFirstTwo(doubles);

        System.out.println(integers);
        System.out.println(doubles);
    }
}
~~~

~~~bash
javac CaptureSwapDemo.java
java CaptureSwapDemo
~~~

Stdout chính xác:

~~~text
[2, 1, 3]
[2.5, 1.5]
~~~

Tại điểm gọi `swapCaptured(values, 0, 1)`, compiler nắm bắt subtype ẩn của wildcard thành một tham số kiểu `E` nhất quán. Hai phần tử được hoán đổi **giữa các vị trí trong cùng một danh sách**, nên không thêm phần tử thuộc lớp khác.

Điều này cho thấy `extends` không có nghĩa “cấm sửa mọi thứ”. Nó cấm những thao tác **không chứng minh được an toàn kiểu**; một generic helper có thể chứng minh việc ghi lại chính phần tử thuộc cùng kiểu được phép.

## 10. Phân biệt `List<T>`, `List<? extends T>` và `List<? super T>`

Khi `T` được cố định (chẳng hạn `T = Number`), ta có:

| Chữ ký | Có thể nhận | Đọc với kiểu tĩnh | Thêm `T` được không? | Vai trò thường gặp |
| --- | --- | --- | --- | --- |
| `List<T>` | Danh sách có đối số kiểu chính xác `T` | `T` | Có | Vừa đọc vừa ghi chính xác `T` |
| `List<? extends T>` | Danh sách của `T` hoặc subtype của `T` | `T` | Không (ngoại trừ `null`) | Nguồn dữ liệu |
| `List<? super T>` | Danh sách của `T` hoặc supertype của `T` | `Object` | Có | Đích nhận dữ liệu |
| `List<?>` | Danh sách của kiểu bất kỳ | `Object` | Không (ngoại trừ `null`) | Đọc/quan sát không cần biết kiểu |

Ví dụ `List<? extends Number>` không nhận `List<Object>` vì `Object` **không phải subtype** của `Number`. Ngược lại, `List<? super Number>` nhận được `List<Object>` nhưng **không nhận `List<Integer>`**, vì `Integer` không phải supertype của `Number`.

Đây là hai chiều sử dụng khác nhau của subtype relation. Chỉ nên chọn wildcard theo vai trò dữ liệu trong API.

## 11. Các lỗi phổ biến khi viết API nhận danh sách kiểu con

### Sai lầm 1 — Dùng `List<Number>` cho một hàm chỉ đọc tất cả các loại số

~~~java
static double total(List<Number> values) {
    return values.stream().mapToDouble(Number::doubleValue).sum();
}

// total(List.of(1, 2)); // Có thể lỗi khi đầu vào đã có kiểu List<Integer>.
~~~

Đoạn này là **mã phương thức và ví dụ minh họa**, không phải chương trình độc lập. Với một biến `List<Integer> integers` cố định, `total(integers)` chắc chắn không biên dịch. Khi đầu vào là biểu thức `List.of(1, 2)` chưa có kiểu đích cố định, **target typing** có thể khiến compiler suy luận `Number`, vì vậy không nên dùng duy nhất biểu thức đó để chứng minh lỗi. Cách kiểm tra rõ ràng là:

~~~java
List<Integer> integers = List.of(1, 2);
// total(integers); // LỖI BIÊN DỊCH.
~~~

Nếu `total` chỉ đọc, chữ ký thích hợp thường là `List<? extends Number>`.

### Sai lầm 2 — Tưởng `? extends T` cho phép thêm một subtype bất kỳ

~~~java
static void addPi(List<? extends Number> numbers) {
    // numbers.add(3.14); // LỖI BIÊN DỊCH.
}
~~~

Một danh sách truyền vào có thể là `List<Integer>`, nên thêm `Double` không được bảo đảm an toàn.

### Sai lầm 3 — Tưởng wildcard biến kiểu phần tử thành `T` lúc runtime

`List<? extends Number>` vẫn có thể trỏ tới `List<Integer>`. Wildcard không sao chép, không đổi đối tượng, không thực hiện ép kiểu từng phần tử. Nó thay đổi **những thao tác compiler cho phép qua tham chiếu**.

### Sai lầm 4 — Ép kiểu unchecked để “làm cho gán được”

~~~java
List<Integer> integers = new ArrayList<>();
@SuppressWarnings("unchecked")
List<Number> forged = (List<Number>) (List<?>) integers;
forged.add(3.14); // Nguy cơ heap pollution.
~~~

Đây là **mã mô tả nguy cơ**, không phải cách chuyển đổi. `@SuppressWarnings("unchecked")` chỉ giấu cảnh báo; nó không xác minh danh sách có thể chứa `Double`. Một nơi khác đọc phần tử như `Integer` có thể gặp `ClassCastException`.

### Sai lầm 5 — Quên phân biệt giới hạn của generic và khả năng sửa của collection

`ArrayList<Integer>` là collection mutable nhưng vẫn được nhìn dưới kiểu `List<? extends Number>`. Ngược lại, `List.of(1, 2)` là unmodifiable dù tham chiếu khai báo `List<Integer>` hay `List<? extends Number>`. Giới hạn ghi do wildcard và việc collection hỗ trợ sửa là **hai quy tắc độc lập**.

### Sai lầm 6 — Dùng wildcard khi API cần trả về đúng subtype của caller

Nếu cần trả chính xác một phần tử thuộc cùng kiểu với danh sách đầu vào, generic method có thể rõ hơn:

~~~java
static <E> E firstExact(List<E> values) {
    return values.get(0);
}
~~~

Đây là **phương thức minh họa cần đặt trong class**. Khi đầu vào có kiểu `List<Dog>`, compiler có thể suy luận `E = Dog` và kiểu trả về là `Dog`. Với một phương thức chỉ cần tính toán qua API của `Animal`, `List<? extends Animal>` có thể đủ và đơn giản hơn.

## 12. Bài tập tự kiểm tra

Giả sử các dòng sau đã được khai báo bên trong một phương thức và import `java.util.*`:

~~~java
List<Integer> ints = new ArrayList<>(List.of(1, 2));
List<Double> doubles = new ArrayList<>(List.of(2.5, 3.5));
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();
List<? extends Number> input = ints;
~~~

Xác định các lệnh biên dịch được hay không:

| Lệnh | Biên dịch? | Vì sao? |
| --- | --- | --- |
| `List<Number> x = ints;` | Không | Generic invariance |
| `List<? extends Number> x = ints;` | Có | `Integer` là subtype của `Number` |
| `List<? extends Number> x = doubles;` | Có | `Double` là subtype của `Number` |
| `List<? extends Number> x = objects;` | Không | `Object` không phải subtype của `Number` |
| `Number value = input.get(0);` | Có | Phần tử được bảo đảm là `Number` |
| `Integer value = input.get(0);` | Không | Không bảo đảm subtype là `Integer` |
| `input.add(3);` | Không | Không biết kiểu phần tử ẩn |
| `input.add(null);` | Có | `null` tương thích với mọi kiểu tham chiếu; runtime có thể từ chối |
| `input.remove(0);` | Có | Không cần cung cấp phần tử kiểu chưa biết |
| `numbers.addAll(ints);` | Có | `addAll` chấp nhận `Collection<? extends Number>` |
| `objects.addAll(numbers);` | Có | Mọi `Number` cũng là `Object` |

Các ví dụ trong bảng được xem như **lệnh độc lập**; những lệnh đánh dấu “Không” cố ý sai kiểu và không được sao chép vào một chương trình muốn biên dịch thành công.

## 13. Quy tắc ứng dụng

Khi nhận danh sách có kiểu phần tử `T` hoặc các subtype của `T`, hãy xác định phương thức sẽ **làm gì với phần tử**. Nếu chủ yếu **đọc** như `T` và dùng API của `T`, chọn `List<? extends T>`. Nếu cần **ghi** các phần tử `T` vào collection do caller cung cấp, dùng `List<? super T>` cho đích. Nếu cần cả đọc và ghi chính xác một kiểu duy nhất hoặc cần trả về subtype cụ thể, cân nhắc generic method với `<T>`. Nếu cần trộn các subtype khác nhau trong một danh sách mới, tạo `List<T>` thích hợp và sao chép phần tử.

**`List<? extends T>` giải quyết bài toán chấp nhận `List<T>` và danh sách các kiểu con mà không làm mất an toàn kiểu.** Phương thức đổi lấy khả năng đọc linh hoạt bằng việc giới hạn thêm giá trị mới qua góc nhìn wildcard, nhờ đó giữ nguyên hợp đồng của danh sách gốc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upper-bounded-wildcards-003">Đọc phần tử theo giới hạn trên</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc phần tử theo giới hạn trên với wildcard `? extends` trong Java Generics

## 1. Mục tiêu của bài học

Trong Java, `? extends UpperBound` là **wildcard có giới hạn trên** (*upper-bounded wildcard*). Nó diễn đạt rằng phần tử của một kiểu generic có thể thuộc **một kiểu cụ thể chưa biết**, nhưng kiểu cụ thể ấy chắc chắn là `UpperBound` hoặc một subtype của `UpperBound`.

Nhờ bảo đảm đó, ta có thể **đọc phần tử dưới dạng `UpperBound`** mà không cần cast không kiểm tra:

~~~java
List<Integer> integers = List.of(10, 20);
List<? extends Number> numbers = integers;

Number first = numbers.get(0); // Hợp lệ: Integer là Number.
~~~

Kết quả `get(0)` có thể là `Integer`, `Double` hoặc một subtype khác của `Number`; compiler chỉ cam kết nó **gán an toàn cho `Number`**. Đây là cơ chế trọng tâm của bài học. Ta sẽ phân biệt giữa “biết giới hạn trên” và “biết chính xác loại phần tử”, hiểu vì sao đọc được nhưng không tự do ghi thêm, rồi dùng nguyên tắc đó để viết API Java 17+.

## 2. Cách hiểu chính xác `List<? extends Number>`

Xét một nguồn có kiểu phần tử cụ thể nhưng được nhìn qua wildcard:

~~~java
List<Integer> integers = List.of(1, 2, 3);
List<? extends Number> source = integers;
Number first = source.get(0);
~~~

Compiler coi element type của `source` là một kiểu **chưa biết** (wildcard capture) nhưng có upper bound `Number`. Vì thế `get(0)` đọc được thành `Number`, còn không được mặc nhiên gán vào `Integer`: một lời gọi khác có thể nhận `List<Double>`.

Ở bài **đọc theo giới hạn trên**, trọng tâm không chỉ là xác định subtype nào được chấp nhận, mà còn là **những hành vi của `Number` có thể gọi an toàn sau khi lấy phần tử**, cách duyệt, ranh giới null/heap pollution và nhu cầu giữ subtype bằng generic method.

## 3. Compiler cho phép đọc dưới kiểu nào?

| Kiểu danh sách được khai báo | Kiểu an toàn khi đọc bằng `get(...)` | Ghi chú |
| --- | --- | --- |
| `List<Integer>` | `Integer` | Type argument cụ thể đã biết. |
| `List<Number>` | `Number` | Có thể chứa nhiều subtype của `Number`. |
| `List<? extends Number>` | `Number` | Phần tử thuộc một subtype *chưa biết* của `Number`. |
| `List<? extends Integer>` | `Integer` | Kiểu đọc an toàn là `Integer`; bản thân `Integer` là final. |
| `List<? extends CharSequence>` | `CharSequence` | Có thể gọi các phương thức của `CharSequence`, ví dụ `length()`. |
| `List<?>` | `Object` | Wildcard không giới hạn tương ứng với giới hạn trên `Object`. |

Mã minh họa các phép đọc hợp lệ:

~~~java
List<? extends Number> numbers = List.of(3, 5);
Number number = numbers.get(0);
double value = numbers.get(1).doubleValue(); // Method của Number.

List<? extends CharSequence> names = List.of("Java", "Generics");
CharSequence name = names.get(0);
int length = names.get(1).length();

List<?> unknown = List.of("A", "B");
Object element = unknown.get(0);
~~~

Nhưng không thể tự ý gán kết quả từ giới hạn rộng sang subtype cụ thể:

~~~java
List<? extends Number> numbers = List.of(3, 5);

// KHÔNG biên dịch: Number không thể bảo đảm là Integer.
Integer integer = numbers.get(0);

// KHÔNG biên dịch: kết quả đọc từ List<?> chỉ được bảo đảm là Object.
String string = List.of("A").stream().findFirst().orElse("");
// Dòng trên độc lập là hợp lệ vì Stream<String>, KHÔNG phải ví dụ của List<?>.
~~~

Ở tình huống thứ hai, ví dụ sai chính xác cần xét là:

~~~java
List<?> unknown = List.of("A", "B");
String value = unknown.get(0); // KHÔNG biên dịch: cần Object hoặc kiểm tra kiểu.
~~~

Không nên sử dụng `(Integer) numbers.get(0)` chỉ vì mình nhìn thấy danh sách ban đầu được tạo từ `Integer` ở một nơi nào đó. Nếu API chỉ hứa `Number`, hãy thiết kế người dùng API phụ thuộc vào `Number`, hoặc dùng cấu trúc type parameter để giữ thông tin `Integer` khi thực sự cần.

## 4. Những phép toán đọc an toàn trên `? extends`

### 4.1. Gọi phương thức của giới hạn trên

~~~java
static double sum(List<? extends Number> numbers) {
    double result = 0.0;
    for (Number n : numbers) {
        result += n.doubleValue();
    }
    return result;
}
~~~

Đoạn trên là một **phương thức minh họa cần đặt trong lớp Java và import `java.util.List`**. Nó nhận được nhiều danh sách cụ thể:

~~~java
sum(List.of(1, 2, 3));   // List<Integer> -> 6.0
sum(List.of(1.5, 2.5)); // List<Double>  -> 4.0
~~~

Ta dùng `doubleValue()` bởi vì `Number` công bố phương thức ấy. Đây là phép chuyển đổi giá trị số có thể mất độ chính xác với kiểu như `BigDecimal`; trong ứng dụng tiền tệ, hãy sử dụng kiểu nghiệp vụ cụ thể và phép tính chính xác thay vì giả định tổng bằng `double` luôn phù hợp.

### 4.2. `get`, for-each, iterator và stream đều tôn trọng giới hạn trên

~~~java
List<? extends Number> numbers = List.of(1, 2, 3);

Number a = numbers.get(0);
for (Number n : numbers) {
    System.out.println(n.intValue());
}
java.util.Iterator<? extends Number> it = numbers.iterator();
Number b = it.next();

double total = numbers.stream()
        .mapToDouble(Number::doubleValue)
        .sum();
~~~

Từng cách truy xuất đều dựa trên **cùng một bảo đảm**: phần tử đọc được là subtype của `Number`. `mapToDouble(Number::doubleValue)` hoạt động vì mỗi phần tử có thể được nhìn như một `Number`.

Không cần chèn `(Number)` thủ công. Compiler đã kiểm tra tính tương thích tĩnh cho toàn bộ thao tác trong phạm vi kiểu generic hợp lệ.

### 4.3. Dùng giới hạn trên là interface

Không chỉ class như `Number`, giới hạn trên có thể là một interface:

~~~java
static void printLengths(List<? extends CharSequence> values) {
    for (CharSequence text : values) {
        System.out.println(text.length());
    }
}
~~~

Hàm nhận `List<String>` hoặc danh sách các kiểu khác thực thi `CharSequence` (chẳng hạn `StringBuilder`), rồi chỉ sử dụng API do `CharSequence` bảo đảm.

## 5. Vì sao đọc được nhưng không thêm phần tử tùy ý?

Chữ ký `List<? extends Number>` **cho phép lấy giá trị theo upper bound**, nhưng không công bố element type chính xác để chèn một `Integer` hoặc `Double` mới:

~~~java
List<Double> actual = new ArrayList<>(List.of(1.5));
List<? extends Number> numbers = actual;
Number first = numbers.get(0);
// numbers.add(5); // Lỗi biên dịch: kiểu ẩn có thể không nhận Integer.
~~~

Một `Number` đọc ra là hợp lệ vì mọi subtype của `Number` đều có thể sử dụng như `Number`. Chiều ngược lại không đúng: một `Integer` không bảo đảm thuộc *mọi* subtype ẩn.

Ngoại lệ `add(null)` chỉ là sự cho phép ở **tầng type checking**, không bảo đảm collection hỗ trợ mutation hoặc null. `clear()` vẫn có thể biên dịch. Khi cần chứng minh quy tắc không thêm `T`, capture helper và chiến lược chuyển sang `? super T`, hãy xem bài chuyên đề liền sau; bài hiện tại tập trung vào phép **đọc an toàn và các giới hạn khi sử dụng giá trị đọc**.

## 6. Ví dụ Java 17+ hoàn chỉnh: đọc từ nhiều kiểu Number

Tạo tệp **ReadUpperBoundDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ReadUpperBoundDemo {
    static double total(List<? extends Number> numbers) {
        double sum = 0;
        for (Number number : numbers) {
            sum += number.doubleValue();
        }
        return sum;
    }

    static <T extends Number> T firstTyped(List<? extends T> values) {
        return values.get(0);
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(2, 4, 6));
        List<Double> doubles = List.of(1.5, 2.5);

        List<? extends Number> view = integers;
        Number first = view.get(0);
        System.out.println("first: " + first);
        System.out.println("int total: " + total(integers));
        System.out.println("double total: " + total(doubles));

        Integer typed = firstTyped(integers);
        System.out.println("typed first: " + typed);

        List<? extends Integer> narrow = integers;
        Integer exact = narrow.get(1);
        System.out.println("narrow: " + exact);

        List<? extends Number> mutableView = integers;
        mutableView.remove(1);
        System.out.println("after remove: " + integers);

        List<? extends Number> nullable = new ArrayList<Integer>();
        nullable.add(null);
        System.out.println("null entry: " + nullable.get(0));

        List<?> unknown = List.of("hello");
        Object value = unknown.get(0);
        System.out.println("unknown: " + value);
    }
}
~~~

Biên dịch và chạy bằng Java 17 hoặc mới hơn:

~~~bash
javac --release 17 -Xlint:all ReadUpperBoundDemo.java
java ReadUpperBoundDemo
~~~

**Stdout chính xác** (mẫu đã biên dịch bằng `javac --release 17 -Xlint:all`, không phát sinh unchecked warning):

~~~text
first: 2
int total: 12.0
double total: 4.0
typed first: 2
narrow: 4
after remove: [2, 6]
null entry: null
unknown: hello
~~~

Giải thích từng phần:

1. `List<Integer>` gán được cho `List<? extends Number>`; giá trị `get(0)` đọc an toàn dưới dạng `Number`.
2. `total` nhận cả `List<Integer>` và `List<Double>`; chỉ dùng phương thức của `Number`.
3. `firstTyped` giữ liên hệ kiểu `T` ở giá trị trả về nên `Integer typed = firstTyped(integers)` không cần cast.
4. `List<? extends Integer>` cho phép đọc như `Integer` vì đó là giới hạn trên chính xác hơn.
5. `remove(1)` làm biến đổi **danh sách gốc** thông qua một reference wildcard; giới hạn trên không biến list thành immutable.
6. `add(null)` được compiler chấp nhận với một `ArrayList` mutable, nhưng người dùng API phải xử lý trường hợp phần tử đọc ra là `null`.
7. `List<?>` cho phép đọc tối thiểu như `Object`.

## 7. Làm thế nào để giữ được kiểu phần tử cụ thể khi cần?

Nếu mã chỉ khai báo:

~~~java
List<? extends Number> view = List.of(1, 2, 3);
Number first = view.get(0);  // Bảo đảm Number.
~~~

Thì việc giá trị runtime tình cờ là `Integer` **không nâng hợp đồng của `view` thành `List<Integer>`**. Dòng sau là lỗi biên dịch:

~~~java
Integer exact = view.get(0); // KHÔNG hợp lệ nếu không cast.
~~~

Nếu API của bạn thực sự cần giữ mối liên hệ giữa kiểu đầu vào và kiểu trả về, hãy khai báo **type parameter có tên**:

~~~java
static <T extends Number> T firstTyped(List<? extends T> values) {
    return values.get(0);
}

List<Integer> ints = List.of(10, 20);
Integer firstInteger = firstTyped(ints); // T suy luận phù hợp với Integer.
~~~

Khi gọi `firstTyped` bằng một biến đã bị mở rộng thành `List<? extends Number>`, compiler vẫn không được phép hứa một `Integer` cụ thể. Type parameter chỉ giữ lại **những liên hệ kiểu mà đầu vào thật sự còn biểu diễn được**; nó không phục hồi bằng chứng đã bị mất.

Nếu thực sự cần xử lý riêng một subtype, có thể dùng kiểm tra runtime rõ ràng:

~~~java
Number item = view.get(0);
if (item instanceof Integer integer) {
    System.out.println(integer + 1);
}
~~~

Đây là nhánh xử lý theo kiểu thực tế của **một phần tử**. Nó không chứng minh rằng *toàn bộ* danh sách có kiểu `List<Integer>`, vì mã có thể dùng collection chứa phần tử không đồng nhất hoặc đã chịu tác động từ raw/unchecked API.

## 8. Trường hợp giới hạn trên là `Object` và so sánh với `? super`

`List<?>` là dạng ngắn của `List<? extends Object>` xét về ý nghĩa wildcard không giới hạn. Do đó `get` trên `List<?>` được xem là `Object`:

~~~java
List<?> anything = List.of("A", 1, 2.5);
Object first = anything.get(0);
~~~

Khi API chỉ muốn in mọi phần tử hoặc kiểm tra `equals`/`toString`, `List<?>` có thể là lựa chọn rõ ràng nhất. Không cần tự ràng buộc `Number` nếu thuật toán chẳng dùng đặc điểm nào của `Number`.

Trong khi đó, với **wildcard giới hạn dưới**:

~~~java
List<? super Integer> sink = new ArrayList<Number>();
sink.add(42);             // Hợp lệ.
Object value = sink.get(0); // Chỉ bảo đảm Object.
~~~

`? super Integer` mô tả một **kiểu bao chứa Integer** chưa biết (có thể là `Integer`, `Number` hoặc `Object`). Việc đọc từ đó chỉ bảo đảm `Object`. Bảng so sánh nhanh:

| Kiểu view | Đọc an toàn thành | Thêm `Integer` |
| --- | --- | --- |
| `List<Integer>` | `Integer` | Có, nếu list hỗ trợ sửa. |
| `List<Number>` | `Number` | Có, nếu list hỗ trợ sửa. |
| `List<? extends Number>` | `Number` | Không, vì subtype cụ thể chưa biết. |
| `List<? super Integer>` | `Object` | Có, nếu list hỗ trợ sửa. |
| `List<?>` | `Object` | Không, trừ `null`. |

PECS là hướng dẫn chọn **tham số API**: đầu vào chủ yếu cung cấp giá trị để đọc → `extends`; đầu vào chủ yếu nhận giá trị để ghi → `super`. Không nên hiểu PECS là quy tắc cấm đọc từ `super` hay cấm tất cả thao tác biến đổi trên `extends`.

## 9. Khi nào đọc phần tử có thể gặp lỗi?

### 9.1. Danh sách rỗng hoặc chỉ số không hợp lệ

`List<? extends Number> numbers = List.of();` vẫn hoàn toàn hợp lệ. Nhưng `numbers.get(0)` ném `IndexOutOfBoundsException`. Wildcard không bảo đảm một collection có phần tử.

Hãy duyệt for-each, kiểm tra `isEmpty()` hoặc kiểm soát chỉ số đúng nếu cần đọc vị trí cố định.

### 9.2. Giá trị `null`

Một collection cho phép `null` có thể chứa `null` dù khai báo kiểu `List<? extends Number>`:

~~~java
List<? extends Number> numbers = new ArrayList<Integer>();
numbers.add(null);
Number n = numbers.get(0); // n == null
// n.doubleValue();          // Ném NullPointerException nếu bỏ comment.
~~~

An toàn về **kiểu** không đồng nghĩa **non-null**. Chỉ gọi phương thức trên giá trị sau khi hiểu rõ hợp đồng nullability của API.

### 9.3. Heap pollution từ mã raw/unchecked

Một chương trình có thể phá bảo đảm tĩnh của generics bằng raw type hoặc unchecked conversion:

~~~java
List raw = new ArrayList<String>();
raw.add("not-a-number");               // Raw API: cảnh báo unchecked.
List<? extends Number> claimed = raw;  // Unchecked conversion.
Number n = claimed.get(0);             // Có thể ClassCastException ở runtime.
~~~

Đây là **đoạn phản ví dụ có chủ đích**, không phải mẫu nên đưa vào sản xuất. Khi đọc như `Number`, compiler/JVM có thể phải kiểm tra kiểu runtime do type erasure. Nếu raw list thật sự chứa `String`, kiểm tra đó sẽ thất bại. Việc này không có nghĩa `? extends Number` mất an toàn: lỗi đến từ chỗ trước đó đã bỏ qua kiểm tra generic bằng raw/unchecked code.

### 9.4. Giới hạn trên không bảo đảm mọi phương thức của subtype

~~~java
List<? extends Number> values = List.of(1, 2);
Number a = values.get(0);

// KHÔNG biên dịch vì Number không có phương thức compareTo(Integer).
int result = values.get(0).compareTo(2);
~~~

`Integer` có các phương thức riêng, nhưng kiểu chung `Number` không hứa toàn bộ chúng. Chỉ dựa vào API của giới hạn trên, hoặc dùng kiểu generic chặt hơn khi thuật toán đòi quan hệ bổ sung.

## 10. Thiết kế phương thức nhận dữ liệu: nên dùng chữ ký nào?

| Nhu cầu | Chữ ký phù hợp | Lý do |
| --- | --- | --- |
| Tính tổng các số, chỉ đọc | `double total(List<? extends Number> input)` | Nhận nhiều danh sách số khác nhau, chỉ cần phương thức từ `Number`. |
| Lấy một số và giữ kiểu subtype ở nơi gọi | `<T extends Number> T first(List<? extends T> input)` | Ràng buộc kết quả vào `T` của caller. |
| Duyệt mọi giá trị không phụ thuộc kiểu | `void print(List<?> input)` | `Object` đã đủ. |
| Ghi các giá trị `Integer` vào collection | `void fill(List<? super Integer> target)` | Có thể chèn Integer, không cần đọc dưới dạng Number. |
| Phải bổ sung `Integer` và `Double` vào cùng list | `void appendBoth(List<Number> target)` (hoặc API đích phù hợp) | Cần một contract chấp nhận các Number, không thể ghi tùy ý qua `? extends`. |

Nếu có một `List<Integer>` mà cần tạo **danh sách mới** cho phép chèn nhiều subtype `Number`, hãy tạo bản sao:

~~~java
List<Integer> ints = List.of(1, 2);
List<Number> mixed = new ArrayList<>(ints);
mixed.add(2.5);
~~~

Đây là **copy constructor an toàn**, không phải cast hoặc thay đổi quan hệ subtyping. `List<Integer>` nguồn vẫn là danh sách riêng.

## 11. Những nhầm lẫn phổ biến và cách tự kiểm tra

| Nhầm lẫn | Giải thích đúng |
| --- | --- |
| “`? extends Number` chỉ chứa `Number`, không nhận `Integer`.” | Nó được thiết kế để nhận cả `Number` và các subtype như `Integer`, `Double`. |
| “Đã gán `List<Integer>` vào `List<? extends Number>` thì `get` trả về `Integer` theo biến view.” | Biến view chỉ bảo đảm kết quả đọc có thể dùng như `Number`. |
| “Có thể thêm bất kỳ `Number` vì bound là `Number`.” | Không: compiler không biết subtype cụ thể của wildcard. |
| “`? extends` tạo danh sách chỉ đọc/bất biến.” | Không: vẫn có thể `remove`/`clear` nếu implementation hỗ trợ; `add(null)` cũng hợp lệ về kiểu. |
| “Nếu đọc được `Number` thì chắc chắn không có `null`.” | Phần tử có thể là `null` nếu collection cho phép. |
| “Cần cast `(Number) numbers.get(i)` để an toàn.” | Cast thủ công không cần thiết; type system đã bảo đảm upper bound trong mã generic an toàn. |
| “`List<?>` và `List<? extends Number>` đọc được cùng kiểu.” | `List<?>` chỉ bảo đảm `Object`; `List<? extends Number>` bảo đảm `Number`. |

**Câu hỏi ôn tập**

1. Tại sao `List<Double>` truyền được vào `total(List<? extends Number>)`? **Vì `Double <: Number` và wildcard cho phép type argument thuộc subtype của `Number`.**
2. Tại sao `Number n = values.get(0)` hợp lệ với `values` kiểu `List<? extends Number>`? **Vì kiểu capture của phần tử chắc chắn là subtype của `Number`.**
3. Tại sao không được `values.add(10)`? **Vì kiểu phần tử cụ thể có thể là `Double` hoặc subtype khác không nhận `Integer`.**
4. `List<? extends Number>` có phải danh sách immutable? **Không.** Wildcard chỉ hạn chế những thao tác ghi bị mất an toàn về kiểu.
5. Khi dùng `List<?>`, kiểu an toàn để nhận `get(0)` là gì? **`Object`.**
6. Nếu muốn kết quả `get` giữ type `Integer` khi caller truyền `List<Integer>`, nên làm gì? **Dùng chữ ký có tham số kiểu `<T extends Number> T firstTyped(List<? extends T>)` hoặc dùng thẳng `List<Integer>` khi API chuyên biệt.**

## 12. Tài liệu chuẩn

- [JLS Java SE 17 §4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): ý nghĩa upper-bounded wildcard và containment.
- [JLS Java SE 17 §4.10.2 — Subtyping among Class and Interface Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2): quan hệ subtype với parameterized types.
- [JLS Java SE 17 §5.1.10 — Capture Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.10): cách compiler xử lý kiểu chưa biết do wildcard biểu diễn.
- [Oracle Java Tutorials — Upper Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/upperBounded.html): mô hình dùng wildcard `extends` để xử lý các danh sách con của một kiểu chung.
- [Java SE 17 API — List<E>](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): `get`, `add`, `remove`, `clear` và ngoại lệ khi truy cập/sửa danh sách.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upper-bounded-wildcards-004">Vì sao không thể thêm một giá trị T tùy ý</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không thể thêm một giá trị T tùy ý vào `List<? extends T>`?

## 1. Mục tiêu và tình huống

Giả sử ta cần viết phương thức nhận danh sách các số để tính toán:

```java
static double sum(List<? extends Number> values) {
    double result = 0.0;
    for (Number value : values) {
        result += value.doubleValue();
    }
    return result;
}
```

Phương thức trên có thể nhận `List<Integer>`, `List<Double>`, `List<BigDecimal>` hoặc `List<Number>`. Nhưng trong chính phương thức, thao tác sau không biên dịch:

```java
values.add(42); // LỖI BIÊN DỊCH
```

Vì sao `Integer` là `Number`, giá trị `42` là `Integer`, và wildcard khai báo `extends Number`, Java vẫn từ chối?

**Câu trả lời cốt lõi:** `List<? extends T>` chứa phần tử thuộc **một kiểu cụ thể chưa biết, là kiểu con của hoặc chính T**. Để thêm phần tử, phải chứng minh phần tử mới tương thích với **kiểu cụ thể đó**, không chỉ tương thích với giới hạn trên `T`. Java không thể chứng minh điều này cho một giá trị `T` bất kỳ.

Bài học này giải thích quy tắc kiểu, chứng minh lý do an toàn, cách đọc/ghi thực sự được phép, trường hợp `null`, và cách thiết kế API khi cần thêm dữ liệu.

## 2. `? extends T` thực sự có nghĩa gì?

Một tham chiếu `List<? extends Number>` có thể nhận `List<Integer>`, `List<Double>` hoặc `List<Number>`, nhưng **không công khai element type chính xác** ở chỗ sử dụng:

```java
List<Integer> ints = new ArrayList<>();
List<? extends Number> numbers = ints;
Number value = numbers.isEmpty() ? 0 : numbers.get(0);
```

`Number` là upper bound cho phép **đọc**, không phải giấy phép **chèn bất kỳ `Number` nào**. Trong bài này, ta sẽ chứng minh cụ thể compiler phải cấm ghi để không phá contract của `List<Integer>` hoặc `List<Double>`, kể cả khi caller biết object runtime đang là loại nào.

## 3. Chứng minh bằng phản ví dụ: thêm `T` sẽ phá an toàn kiểu

Giả sử trình biên dịch cho phép thêm một `Number` qua biến `List<? extends Number>`:

```java
List<Integer> integers = new ArrayList<>();
List<? extends Number> view = integers;

// Giả định Java cho phép, nhưng thực tế dòng này KHÔNG biên dịch:
view.add(3.14);
```

`3.14` được boxing thành `Double`, là kiểu con của `Number`. Nó vẫn **không phải `Integer`**. Nếu phép thêm được chấp nhận, `integers` sẽ chứa `Double` trong khi mã nguồn khác tiếp tục tin rằng danh sách đó chỉ chứa `Integer`:

```java
// Mô phỏng hậu quả giả định; không phải chương trình Java hợp lệ.
Integer first = integers.get(0); // Dữ liệu thực là Double!
```

Vấn đề không chỉ xuất hiện khi thêm `Double`. Ngay cả `view.add(42)` cũng phải bị cấm, vì biến `view` có thể đang trỏ tới một `List<Double>`. Trình biên dịch không thể phán đoán dựa trên một loại giá trị cụ thể có vẻ “hợp lý” trong một trường hợp.

Do đó, với:

```java
static void cannotAdd(List<? extends Number> values) {
    values.add(Integer.valueOf(42)); // LỖI BIÊN DỊCH
    values.add(Double.valueOf(3.14)); // LỖI BIÊN DỊCH
    values.add((Number) Integer.valueOf(1)); // VẪN LỖI
}
```

Cả ba dòng đều không hợp lệ. Ép kiểu giá trị thành `Number` không giúp gì, bởi `Number` là giới hạn trên, không phải kiểu phần tử cụ thể đã biết của danh sách.

## 4. Vì sao đọc được dưới kiểu T nhưng ghi T lại không được?

Giả sử một danh sách có kiểu phần tử thực tế `U` với `U extends T`.

**Khi đọc:** mọi giá trị kiểu `U` đều là giá trị kiểu `T`, nên có thể đọc và sử dụng dưới kiểu `T`.

**Khi ghi:** một giá trị kiểu `T` bất kỳ không nhất thiết là `U`. Nó có thể là một kiểu con khác của `T`, nên không thể đưa vào danh sách chỉ nhận `U`.

```java
List<? extends Number> values = List.of(10, 20);

Number number = values.get(0); // ĐÚNG: phần tử chắc chắn là Number
Object object = values.get(0); // ĐÚNG: Number cũng là Object
Integer integer = values.get(0); // SAI: phần tử có thể là Double
values.add(number);             // SAI: Number chưa chắc đúng kiểu phần tử
```

Đây là một chiều thông tin có chủ đích: **giới hạn trên bảo đảm khả năng đọc**, không bảo đảm khả năng ghi giá trị mới.

Một ví dụ với hierarchy tự định nghĩa:

```java
class Animal {}
class Dog extends Animal {}
class Cat extends Animal {}

List<Dog> dogs = new ArrayList<>();
List<? extends Animal> animals = dogs;

Animal a = animals.get(0); // Hợp lệ về kiểu; sẽ lỗi chỉ số nếu list đang trống
animals.add(new Dog());    // LỖI BIÊN DỊCH
animals.add(new Cat());    // LỖI BIÊN DỊCH
animals.add(new Animal()); // LỖI BIÊN DỊCH
```

Ngay cả `new Dog()` cũng bị từ chối qua `animals`, bởi kiểu tĩnh của biến là `List<? extends Animal>`, không phải `List<Dog>`. Khi biết chính xác kiểu danh sách, hãy ghi qua biến có kiểu rõ ràng:

```java
dogs.add(new Dog()); // ĐÚNG
```

## 5. Wildcard capture: trình biên dịch đang kiểm tra điều gì?

Khi trình biên dịch sử dụng `List<? extends Number>`, nó có thể biểu diễn `?` bằng một kiểu tạm, thường được nói tới trong thông báo lỗi là **`CAP#1`** (*capture of ? extends Number*). Có thể hình dung:

```text
CAP#1 là kiểu phần tử chưa biết
CAP#1 <: Number

get(int)  : CAP#1
add(CAP#1): boolean
```

Đây là sơ đồ giải thích kiểu, không phải cú pháp Java. Vì `CAP#1` là kiểu con chưa biết của `Number`, trình biên dịch **không** có quy tắc cho phép đưa một `Number` hoặc `Integer` bất kỳ vào vị trí yêu cầu `CAP#1`.

Thông báo lỗi của trình biên dịch có thể đề cập:

```text
incompatible types: Integer cannot be converted to CAP#1
where CAP#1 is a fresh type-variable:
  CAP#1 extends Number from capture of ? extends Number
```

Cách trình bày lỗi cụ thể phụ thuộc bản JDK và biểu thức nguồn; ý nghĩa vẫn là kiểu giá trị định chèn chưa được chứng minh tương thích với kiểu phần tử đã capture.

Điều đáng lưu ý: kiểu tham số `T` trong `List<? extends T>` **không phải** kiểu `CAP#1`. `T` là **giới hạn trên**; `CAP#1` là kiểu phần tử cụ thể nhưng chưa biết nằm bên dưới giới hạn ấy.

### 5.1. Viết phương thức generic với T vẫn không cho phép thêm T

```java
static <T> void tryAdd(List<? extends T> target, T value) {
    target.add(value); // LỖI BIÊN DỊCH
}
```

Có thể truyền `List<Integer>` cùng một giá trị `Number` làm tham số để kiểu `T` được suy luận là `Number`, nhưng danh sách có thể chỉ nhận `Integer`. Việc đặt tên `T` cho giới hạn trên không khiến kiểu cụ thể bên dưới dấu `?` trở nên biết được.

Tương tự:

```java
static <T extends Number> void tryAddNumber(
        List<? extends T> list, T value) {
    list.add(value); // VẪN LỖI
}
```

Cách khai báo `<T extends Number>` chỉ giới hạn chính `T` trong họ `Number`; nó không xóa bỏ wildcard của `List<? extends T>`.

### 5.2. Biết đối tượng thực tế vẫn chưa đủ nếu biến bị khai báo bằng wildcard

```java
List<? extends Number> view = new ArrayList<Number>();
view.add(1); // LỖI BIÊN DỊCH
```

Mặc dù `new ArrayList<Number>()` nhận được `Integer`, kiểu được khai báo cho `view` vẫn là `List<? extends Number>`. Trình biên dịch kiểm tra lời gọi dựa trên **kiểu tĩnh của biểu thức nhận phương thức** và các quy tắc capture.

Nếu muốn ghi, giữ tham chiếu đúng kiểu:

```java
List<Number> numbers = new ArrayList<>();
List<? extends Number> readable = numbers;

numbers.add(1);    // ĐÚNG
// readable.add(1); // SAI
```

Cả hai tham chiếu trỏ cùng danh sách; sự khác nhau nằm ở khả năng mà từng **kiểu khai báo** cho phép mã nguồn sử dụng.

## 6. Có thật sự không thể thêm bất cứ thứ gì?

**Ngoại lệ quan trọng: `null`.** Trong Java, `null` có thể chuyển đổi sang bất kỳ kiểu tham chiếu nào, kể cả kiểu capture chưa biết:

```java
List<Integer> integers = new ArrayList<>();
List<? extends Number> view = integers;

view.add(null); // BIÊN DỊCH ĐƯỢC
```

Nếu đối tượng danh sách hỗ trợ thêm phần tử và cho phép `null`, thao tác thành công. Nhưng đây không phải cách thêm một giá trị `T` hữu ích:

- `null` không mang một đối tượng `Number` hoặc một giá trị số cụ thể.
- `List.of(...)` và các danh sách không hỗ trợ sửa đổi sẽ ném `UnsupportedOperationException` khi gọi `add(null)`.
- Một số triển khai hoặc hợp đồng tập hợp có thể từ chối `null` bằng ngoại lệ riêng.

Do đó cách nói chính xác là: **không thể thêm một giá trị khác `null` có kiểu `T` tùy ý qua `List<? extends T>`**; `add(null)` là trường hợp hợp lệ về mặt hệ thống kiểu, chưa chắc thành công khi chạy.

`set(index, value)` cũng có cùng giới hạn kiểu ở đối số mới:

```java
List<? extends Number> view = new ArrayList<Integer>(List.of(1, 2));

view.set(0, 5);    // LỖI BIÊN DỊCH
view.set(0, null); // BIÊN DỊCH ĐƯỢC nếu xét riêng dòng này
```

`set(0, null)` chỉ thành công nếu vị trí tồn tại và danh sách thực tế hỗ trợ thay thế, chấp nhận `null`. Chỉ số không hợp lệ có thể ném `IndexOutOfBoundsException`.

## 7. `? extends T` không đồng nghĩa với danh sách bất biến

Cấm ghi một `T` mới tùy ý **không** có nghĩa đối tượng nguồn immutable:

```java
List<Integer> ints = new ArrayList<>(List.of(1, 2, 3));
List<? extends Number> view = ints;
view.remove(1); // Biên dịch; xóa phần tử nếu list hỗ trợ.
view.clear();   // Biên dịch; không cần giá trị thuộc capture type.
```

Các thao tác này có thể ném `UnsupportedOperationException` với list không cho sửa. `Collections.swap` và helper generic capture cũng có thể di chuyển những phần tử **đã thuộc cùng list**. Đó là thao tác khác với chèn một `Number` mới. Bài riêng về **wildcard không làm đối tượng bất biến** phân tích alias, unmodifiable views và snapshots.

## 8. Vì sao helper generic có thể thêm lại phần tử đã đọc?

Đoạn sau có thể gây bất ngờ:

```java
static void cannotDuplicate(List<? extends Number> values) {
    values.add(values.get(0)); // LỖI BIÊN DỊCH
}
```

Dù `get(0)` và `add(...)` cùng được gọi trên `values`, Java có thể capture các lần sử dụng wildcard ở những vị trí kiểu khác nhau. Trình biên dịch không bảo đảm giá trị đọc trong biểu thức này được xem như chính kiểu tham số mà `add` yêu cầu.

Một phương thức helper kiểu `<E>` có thể giữ **cùng một kiểu phần tử E** trong toàn bộ thân phương thức:

```java
static <E> void duplicateFirst(List<E> values) {
    if (!values.isEmpty()) {
        E first = values.get(0);
        values.add(first); // ĐÚNG: cùng E
    }
}

static void caller(List<? extends Number> source) {
    duplicateFirst(source); // ĐÚNG: capture wildcard thành E tại lời gọi
}
```

Điều này an toàn vì giá trị được thêm **đến từ chính danh sách và giữ đúng kiểu E**. `duplicateFirst` không tự tạo một `Number` tùy ý. Nếu nguồn là `List<Integer>`, helper nhận `E` tương ứng với kiểu phần tử đó; nếu là `List<Double>`, nó giữ kiểu `Double`.

Helper chỉ giải quyết kiểm tra kiểu; nếu danh sách không hỗ trợ `add`, lời gọi vẫn ném `UnsupportedOperationException`.

Đừng dùng raw `List` hoặc ép kiểu unchecked để “đánh lừa” compiler. Những cách đó có thể khiến phần tử sai loại lọt vào danh sách và gây lỗi `ClassCastException` khi đọc sau này.

## 9. Khi muốn thực sự ghi T, thiết kế API thế nào?

### 9.1. Dùng `List<T>` khi cần đúng cùng một kiểu

Nếu phương thức cần bảo đảm giá trị mới cùng kiểu phần tử:

```java
static <T> void append(List<T> target, T value) {
    target.add(value);
}
```

Ví dụ:

```java
List<Integer> integers = new ArrayList<>();
append(integers, 10); // Hợp lệ: T là Integer
```

Kiểu `T` trong `List<T>` là cùng tham số kiểu của giá trị `value`; danh sách không chứa wildcard chưa biết trong chữ ký phương thức. Phương thức này vẫn phụ thuộc danh sách có hỗ trợ `add` lúc chạy hay không.

### 9.2. Dùng `List<? super T>` khi danh sách là nơi nhận giá trị T

Nếu một phương thức được thiết kế để ghi phần tử `T` vào đích, wildcard **giới hạn dưới** phù hợp:

```java
static <T> void appendTo(List<? super T> target, T value) {
    target.add(value); // Hợp lệ về kiểu
}
```

Với `T` là `Integer`, đích có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`: tất cả đều chấp nhận một `Integer`. Đây là điều ngược với `List<? extends T>`, vốn có thể trỏ tới danh sách của một kiểu con hẹp hơn mà `T` không được phép chèn vào.

Ví dụ:

```java
List<Number> numbers = new ArrayList<>();
appendTo(numbers, Integer.valueOf(7));
appendTo(numbers, Double.valueOf(2.5));
```

### 9.3. Dùng `extends` cho nguồn đọc và `super` cho đích ghi

Chữ ký thường gặp khi chuyển phần tử:

```java
static <T> void transfer(
        List<? extends T> source,
        List<? super T> destination) {
    for (T value : source) {
        destination.add(value);
    }
}
```

Ví dụ gọi với `List<Integer>` và `List<Number>` hợp lệ. `source` chỉ cần bảo đảm mỗi phần tử đọc được là `T`; `destination` bảo đảm tiếp nhận được `T`.

Đây là quy tắc **PECS (Producer Extends, Consumer Super)**:

- **Producer `? extends T`**: cung cấp giá trị đọc được dưới dạng `T`.
- **Consumer `? super T`**: nhận giá trị kiểu `T` để ghi.

PECS hướng dẫn lựa chọn chữ ký API theo cách sử dụng dữ liệu. Nó không có nghĩa một danh sách `extends` là bất biến về nội dung hoặc danh sách `super` luôn hỗ trợ sửa đổi.

## 10. Chương trình Java 17+ đầy đủ

Lưu tệp `ExtendsWriteDemo.java` với nội dung:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsWriteDemo {
    // Giữ một kiểu E cho cả phần tử đọc và phần tử thêm vào.
    static <E> void duplicateFirst(List<E> list) {
        if (!list.isEmpty()) {
            E first = list.get(0);
            list.add(first);
        }
    }

    // Kiểu T được bảo đảm phù hợp với danh sách đích.
    static <T> void appendTo(List<? super T> target, T value) {
        target.add(value);
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(10, 20));
        List<? extends Number> source = integers;

        Number read = source.get(0);
        System.out.println("Read: " + read);

        // source.add(30); // Lỗi biên dịch nếu bỏ dấu chú thích.
        duplicateFirst(source);
        System.out.println("Same list: " + integers);

        List<Number> destination = new ArrayList<>();
        appendTo(destination, 3.5);
        System.out.println("Destination: " + destination);

        try {
            List<? extends Number> unmodifiable = List.of(1, 2);
            unmodifiable.add(null); // Hợp lệ về kiểu nhưng không sửa được List.of.
        } catch (UnsupportedOperationException e) {
            System.out.println(
                    "Unmodifiable: " + e.getClass().getSimpleName());
        }
    }
}
```

Trong thư mục chứa tệp, biên dịch và chạy:

```bash
javac -encoding UTF-8 ExtendsWriteDemo.java
java ExtendsWriteDemo
```

Đầu ra:

```text
Read: 10
Same list: [10, 20, 10]
Destination: [3.5]
Unmodifiable: UnsupportedOperationException
```

Phân tích:

1. `source.get(0)` trả giá trị đọc được dưới kiểu `Number`.
2. `source.add(30)` được chú thích vì không thể biên dịch với `List<? extends Number>`.
3. `duplicateFirst(source)` hợp lệ vì helper capture cùng một kiểu `E` và lấy phần tử từ chính danh sách. Nó thay đổi đối tượng `integers`, vì `source` và `integers` cùng tham chiếu tới đối tượng đó.
4. `appendTo` có đích `List<? super T>`, nên có thể thêm giá trị thực.
5. `List.of` từ chối sửa đổi ở runtime. Điều này độc lập với việc `add(null)` hợp lệ về kiểu.

## 11. Sai lầm thường gặp

| Nhận định hoặc cách viết | Kết luận | Lý do |
| --- | --- | --- |
| “`? extends Number` nghĩa là thêm được mọi `Number`” | Sai | Kiểu phần tử thực tế có thể chỉ là `Integer` hoặc `Double` |
| `list.add((Number) 1)` qua `List<? extends Number>` | Không biên dịch | Ép thành `Number` không chứng minh tương thích với kiểu capture |
| “Danh sách thực tế là `ArrayList<Number>` nên biến wildcard thêm được” | Sai | Kiểm tra ở compile-time dựa trên kiểu tĩnh `List<? extends Number>` |
| `list.add(null)` | Biên dịch được | `null` tương thích với kiểu tham chiếu chưa biết; runtime vẫn có thể từ chối |
| “Wildcard `extends` đồng nghĩa immutable” | Sai | Một số thao tác xóa/sắp xếp lại có thể hợp lệ và chạy được |
| `list.add(list.get(0))` với biến wildcard | Có thể không biên dịch | Kiểu capture ở hai vị trí không được buộc là một |
| Helper `<E>` nhận `List<E>` rồi thêm phần tử vừa đọc | Hợp lệ | Cùng một `E` kiểm soát cả đọc và ghi |
| Ghi `T` vào `List<? super T>` | Hợp lệ về kiểu | Đích nhận được bất kỳ giá trị `T` nào |
| Dùng raw `List` để thêm giá trị không hợp kiểu | Không an toàn | Bỏ qua kiểm tra kiểu, dễ gây lỗi khi đọc |

### Gợi ý đọc lỗi biên dịch

Khi gặp lỗi liên quan `CAP#1` hay “capture of `? extends ...`”, hãy tự hỏi: “Tôi đang đưa một giá trị **bên ngoài** vào nơi yêu cầu đúng một kiểu con chưa biết phải không?” Nếu có, hãy xem API của bạn cần `List<T>`, `List<? super T>`, hay chỉ cần dùng helper để thao tác với **các phần tử đã có**.

## 12. Bài tập tự kiểm tra

Xét riêng từng đoạn sau với `import java.util.*;`. Hãy xác định biên dịch được không, và nếu được thì có chắc chạy thành công không.

**A — Đọc nguồn**

```java
List<? extends Number> a = List.of(1, 2, 3);
Number value = a.get(0);
```

**B — Thêm kiểu con**

```java
List<? extends Number> b = new ArrayList<Integer>();
b.add(4);
```

**C — Thêm chính giới hạn trên**

```java
List<? extends Number> c = new ArrayList<Number>();
c.add((Number) 4);
```

**D — Thêm null**

```java
List<? extends Number> d = new ArrayList<Integer>();
d.add(null);
System.out.println(d.size());
```

**E — Ghi qua giới hạn dưới**

```java
List<? super Integer> e = new ArrayList<Number>();
e.add(4);
```

**F — Dùng helper capture**

```java
static <E> void copyFirst(List<E> values) {
    if (!values.isEmpty()) {
        values.add(values.get(0));
    }
}
List<? extends Number> f = new ArrayList<>(List.of(1, 2));
copyFirst(f);
```

Đoạn F là hai khai báo đặt trong một lớp hoặc ngữ cảnh khai báo phương thức hợp lệ; không thể khai báo phương thức `static` tự do giữa các câu lệnh trong cùng phương thức `main`.

**Đáp án:**

- **A:** Biên dịch được; đọc `Number` an toàn.
- **B:** Không biên dịch; `Integer` không được chứng minh là kiểu phần tử capture của `b`.
- **C:** Không biên dịch; dù đối tượng thực sự là `ArrayList<Number>`, biến `c` có kiểu wildcard.
- **D:** Biên dịch và chạy với `ArrayList`; in `1`. Không thể suy mọi triển khai `List` khác cũng cho phép `add(null)`.
- **E:** Biên dịch; `List<? super Integer>` nhận được giá trị `Integer`. Đối tượng `ArrayList<Number>` hỗ trợ thêm nên thao tác thành công.
- **F:** Biên dịch; helper dùng cùng tham số `E` cho `get` và `add`. Đối tượng nguồn `ArrayList` hỗ trợ thay đổi nên thao tác thành công.

## 13. Ghi nhớ

`List<? extends T>` là danh sách của **một kiểu con chưa biết của T**. Đọc ra một `T` là an toàn vì mọi phần tử đều thuộc `T`; thêm một `T` tùy ý là không an toàn vì giá trị ấy có thể không thuộc kiểu phần tử thực tế. `null` là ngoại lệ hợp lệ về mặt kiểu, nhưng runtime có thể từ chối thao tác.

Khi thiết kế API: dùng `? extends T` cho nguồn đọc, `? super T` cho đích ghi, và `List<T>` hoặc helper generic khi cần liên kết chính xác kiểu phần tử đang đọc với kiểu phần tử sẽ ghi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upper-bounded-wildcards-005">Giới hạn thêm null không có nghĩa collection phải chấp nhận null</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thêm `null` không có nghĩa collection phải chấp nhận `null`

Trong Java Generics, với một biến kiểu `Collection<? extends T>` (ví dụ `List<? extends Number>`), trình biên dịch không cho phép thêm một đối tượng `T` cụ thể tùy ý vì kiểu phần tử thực tế chưa được biết. Tuy nhiên, **`add(null)` vẫn hợp lệ về mặt kiểu**. Điều đó chỉ nói rằng biểu thức `null` có thể được truyền cho tham số mang kiểu tham chiếu chưa biết; **không bảo đảm** collection được trỏ tới có cho thêm phần tử, hoặc có chấp nhận `null` khi chạy.

Đây là hai tầng quy tắc cần phân biệt:

1. **Kiểm tra tại thời điểm biên dịch (compile time):** hệ kiểu generic xác định lời gọi `add(...)` có hợp lệ với `? extends T` hay không.
2. **Hợp đồng và triển khai collection tại thời điểm chạy (runtime):** collection quyết định thao tác thêm có được hỗ trợ và giá trị `null` có được chấp nhận hay không.

Hiểu đúng hai tầng này giúp tránh nhầm lẫn giữa *generic type safety*, *mutability* và *null policy*.

## 1. Vì sao `add(null)` biên dịch được với `? extends T`?

Xét đoạn mã minh họa trong một phương thức:

```java
static void inspect(List<? extends Number> numbers) {
    Number first = numbers.isEmpty() ? null : numbers.get(0); // Hợp lệ.

    // numbers.add(10);   // LỖI BIÊN DỊCH.
    // numbers.add(2.5);  // LỖI BIÊN DỊCH.
    numbers.add(null);    // HỢP LỆ VỀ KIỂU; runtime còn phụ thuộc list.
}
```

`List<? extends Number>` biểu diễn một danh sách mà kiểu phần tử chính xác chưa được biết, nhưng là `Number` hoặc một kiểu con của `Number`. Danh sách ấy có thể là:

- `List<Integer>`;
- `List<Double>`;
- `List<Number>`;
- hoặc một danh sách của kiểu số khác.

Nếu cho `numbers.add(10)`, đối tượng `Integer` có thể bị thêm vào một `List<Double>` thật sự. Vì vậy compiler từ chối. Tương tự, `2.5` là `Double` nên không an toàn cho mọi kiểu phần tử có thể nằm sau dấu `?`.

Ngược lại, **`null` là giá trị đặc biệt có thể chuyển đổi sang mọi kiểu tham chiếu trong hệ kiểu Java thông thường**, kể cả kiểu tham chiếu chưa biết được trình biên dịch tạo ra khi *capture* wildcard. Nó không có kiểu runtime cụ thể như `Integer` hay `Double` có thể làm sai loại phần tử của danh sách.

Có thể hình dung các điều kiện:

```text
List<? extends Number>
          |
          v
Kiểu phần tử thực tế: một kiểu X chưa biết, X extends Number
          |
          +-- add(Integer): Không chắc Integer là X  -> compiler từ chối
          +-- add(Double):  Không chắc Double là X   -> compiler từ chối
          +-- add(null):    null tương thích với X    -> compiler chấp nhận
```

Tính hợp lệ về kiểu của lời gọi `add(null)` **không tự tạo quyền sửa đổi cấu trúc dữ liệu** và cũng **không ghi đè quy tắc cấm `null`** của một triển khai collection.

## 2. Ví dụ đầy đủ: ba kết quả runtime khác nhau

Lưu tệp `ExtendsNullDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;

public class ExtendsNullDemo {
    static void attemptAddNull(String label, Collection<? extends Number> target) {
        try {
            boolean added = target.add(null); // Biên dịch được.
            System.out.println(label + ": added=" + added);
        } catch (UnsupportedOperationException e) {
            System.out.println(label + ": UnsupportedOperationException");
        } catch (NullPointerException e) {
            System.out.println(label + ": NullPointerException");
        }
    }

    public static void main(String[] args) {
        ArrayList<Integer> mutable = new ArrayList<>(List.of(1, 2));
        attemptAddNull("ArrayList", mutable);
        System.out.println("contents=" + mutable);

        List<Integer> immutable = List.of(1, 2);
        attemptAddNull("List.of", immutable);

        Queue<Integer> queue = new ArrayDeque<>();
        attemptAddNull("ArrayDeque", queue);
    }
}
```

Chạy bằng Java 17 trở lên:

```bash
javac ExtendsNullDemo.java
java ExtendsNullDemo
```

Kết quả:

```text
ArrayList: added=true
contents=[1, 2, null]
List.of: UnsupportedOperationException
ArrayDeque: NullPointerException
```

**Phân tích từng trường hợp:**

- **`ArrayList<Integer>`:** là danh sách hỗ trợ thêm phần tử và cho phép lưu `null`. Lời gọi `add(null)` thành công, trả về `true`.
- **`List.of(1, 2)`:** trả về danh sách không sửa đổi. Lời gọi `add(null)` bị từ chối bằng `UnsupportedOperationException`. Bản thân các factory `List.of(...)` cũng không chấp nhận phần tử `null` khi tạo danh sách.
- **`ArrayDeque<Integer>`:** hỗ trợ thêm phần tử không-null, nhưng chủ động cấm `null`. Lời gọi `add(null)` ném `NullPointerException` ngay cả khi queue đang trống và có thể nhận thêm số nguyên.

Điểm chung: cả ba lần gọi đều dùng chữ ký tham số `Collection<? extends Number>` và **đều qua được trình biên dịch**. Kết quả khác nhau vì đối tượng thực tế có hợp đồng runtime khác nhau.

## 3. Hai câu hỏi độc lập: có sửa được không, và có nhận `null` không?

Một collection có thể hỗ trợ thay đổi nhưng cấm `null`; hoặc có thể chứa `null` nhưng không cho thêm phần tử qua một thao tác cụ thể. Vì vậy cần xét ít nhất hai thuộc tính độc lập:

| Collection và cách tạo | Hỗ trợ `add`? | Chấp nhận `null`? | `add(null)` thường cho kết quả |
| --- | --- | --- | --- |
| `new ArrayList<Integer>()` | Có | Có | Thêm `null` thành công |
| `new HashSet<Integer>()` | Có | Có | Thành công lần đầu; nếu đã có `null` thì `add` trả `false` |
| `new ArrayDeque<Integer>()` | Có | Không | `NullPointerException` |
| `new TreeSet<Integer>()` dùng thứ tự tự nhiên | Có | Không | `NullPointerException` |
| `List.of(1, 2)` | Không | Không | `UnsupportedOperationException` |
| `List.copyOf(source)` | Không | Không | `UnsupportedOperationException` |
| `Arrays.asList(1, 2)` | Không hỗ trợ thay đổi kích thước | Có thể chứa `null` | `UnsupportedOperationException` |
| `Collections.unmodifiableList(list)` | Không qua wrapper | Phụ thuộc nội dung danh sách gốc; wrapper không thêm được | `UnsupportedOperationException` |

Bảng giả định lời gọi là `add(null)` trên các triển khai/thể hiện thông thường như nêu trong cột đầu. Với các collection tự viết hoặc cấu hình comparator riêng, hợp đồng có thể khác. Đặc biệt, `TreeSet` với comparator tùy chỉnh cần tuân theo quy tắc của comparator và triển khai, nên không suy rộng hàng “thứ tự tự nhiên” sang mọi `TreeSet`.

**Cần phân biệt ba dạng trạng thái:**

- **Thêm phần tử được:** `add` là thao tác tùy chọn của `Collection`; vài implementation cố tình không hỗ trợ.
- **Chấp nhận `null`:** collection có thể lưu `null` hợp lệ theo hợp đồng của nó.
- **Chứa sẵn `null`:** ngay cả một góc nhìn không cho thêm phần tử vẫn có thể nhìn thấy `null` tồn tại trong backing collection.

Không thể kết luận ba trạng thái này chỉ từ kiểu generic `Collection<? extends T>`.

## 4. Quy tắc `extends` bảo vệ kiểu phần tử, không xác định chính sách `null`

Giả sử:

```java
List<Integer> integers = new ArrayList<>();
List<? extends Number> values = integers;

values.add(null);       // Biên dịch; ArrayList nhận null.
Number number = values.get(0); // Hợp lệ; number == null.
```

Các phát biểu sau đều đúng đồng thời:

1. Biến `values` không cho phép thêm một giá trị `Integer` cụ thể bằng `values.add(1)`, bởi compiler không biết kiểu phần tử thật sự sau wildcard.
2. Biểu thức `values.add(null)` hợp lệ về kiểu vì `null` có thể dùng ở vị trí đối số mang kiểu tham chiếu chưa biết.
3. Runtime `ArrayList` cho phép thêm `null`; việc đọc ra sau đó có thể trả về `null`.
4. Hệ kiểu Java thông thường không coi tham số `T extends Number` là cam kết các giá trị luôn non-null.

Ngay cả cận kiểu `Number` hay một interface tự định nghĩa cũng **không phải cận non-null**. Trong Java 17 tiêu chuẩn, `T extends Number` nói về quan hệ kiểu danh nghĩa của các đối tượng không-null; nó không cấm giá trị `null`.

Những hệ thống kiểm tra nullness bổ sung có thể áp dụng annotation hoặc luật riêng để cảnh báo hoặc chặn `null`, nhưng đây là cơ chế ngoài những bảo đảm của cú pháp wildcard thông thường.

## 5. Không riêng `add`: truyền `null` vào `set` cũng qua kiểm tra kiểu

Với `List<? extends Number>`, `set(index, element)` yêu cầu phần tử có kiểu chưa biết tương tự `add`. Vì thế:

```java
List<Integer> base = new ArrayList<>(List.of(10, 20));
List<? extends Number> view = base;

// view.set(0, 99); // LỖI BIÊN DỊCH.
view.set(0, null);  // HỢP LỆ VỀ KIỂU; ArrayList chấp nhận null.
System.out.println(base); // [null, 20]
```

Sự hợp lệ về kiểu vẫn không nói rằng danh sách có hỗ trợ `set` hoặc có nhận `null`. Một danh sách không sửa đổi có thể từ chối cả `add` lẫn `set`. Một danh sách có kích thước cố định có thể **cho `set` nhưng cấm `add`**.

Ví dụ Java 17 độc lập, lưu thành `FixedSizeNullDemo.java`:

```java
import java.util.Arrays;
import java.util.List;

public class FixedSizeNullDemo {
    public static void main(String[] args) {
        List<Integer> fixedSize = Arrays.asList(1, 2);
        List<? extends Number> view = fixedSize;

        view.set(0, null);
        System.out.println("after set=" + fixedSize);

        try {
            view.add(null);
        } catch (UnsupportedOperationException e) {
            System.out.println("add=UnsupportedOperationException");
        }
    }
}
```

```bash
javac FixedSizeNullDemo.java
java FixedSizeNullDemo
```

Kết quả:

```text
after set=[null, 2]
add=UnsupportedOperationException
```

`Arrays.asList` trả về danh sách được hỗ trợ bởi một mảng có độ dài cố định. Nó cho thay đổi giá trị phần tử hiện có qua `set` và có thể chứa `null`, nhưng không cho tăng kích thước bằng `add`. Ví dụ cho thấy “collection nhận `null`” và “thao tác `add` được hỗ trợ” thực sự là hai điều khác nhau.

## 6. Phân biệt hai loại exception khi thêm `null`

Hai exception rất thường gặp:

### `UnsupportedOperationException`

Đây là dấu hiệu collection **không hỗ trợ thao tác được yêu cầu**. Ví dụ `List.of(...)` trả về danh sách không sửa đổi. Một lời gọi như:

```java
List<? extends Number> readOnly = List.of(1, 2);
readOnly.add(null); // Biên dịch, runtime ném UnsupportedOperationException.
```

không trở nên hợp lệ về mặt thay đổi trạng thái chỉ vì đối số là `null`. Bản thân phép thêm đã là thao tác bị cấm.

### `NullPointerException`

Đây có thể là dấu hiệu implementation **cấm giá trị `null`**. Ví dụ:

```java
Queue<? extends Number> numbers = new ArrayDeque<Integer>();
numbers.add(null); // Biên dịch, runtime ném NullPointerException.
```

`ArrayDeque` có thể thêm phần tử `Integer` bình thường thông qua một tham chiếu thích hợp như `Queue<Integer>`, nhưng vẫn từ chối `null`. Do đó `NullPointerException` ở đây phản ánh chính sách dữ liệu chứ không phải lỗi bất biến generics.

Không thể chỉ nhìn `catch (NullPointerException)` mà kết luận chắc chắn nguyên nhân trong mọi chương trình; stack trace, hợp đồng API và vị trí ném exception mới xác định nguyên nhân. Trong ví dụ trên, nguyên nhân được biết rõ từ hợp đồng của `ArrayDeque`.

## 7. `List.of` và `List.copyOf` kiểm tra `null` ngay khi tạo

Một lỗi thường gặp khác là nghĩ chỉ cần gán kết quả sang `List<? extends Number>` thì quy tắc tiếp nhận `null` sẽ thay đổi. Wildcard chỉ là đặc tính kiểu của tham chiếu; nó không sửa hành vi factory.

Lưu thành `NullFactoryDemo.java`:

```java
import java.util.Arrays;
import java.util.List;

public class NullFactoryDemo {
    public static void main(String[] args) {
        try {
            List<Integer> first = List.of(1, null);
            System.out.println(first);
        } catch (NullPointerException e) {
            System.out.println("List.of rejected null");
        }

        try {
            List<Integer> second = List.copyOf(Arrays.asList(1, null));
            System.out.println(second);
        } catch (NullPointerException e) {
            System.out.println("List.copyOf rejected null");
        }

        List<? extends Number> safe = List.of(1, 2);
        System.out.println("size=" + safe.size());
    }
}
```

```bash
javac NullFactoryDemo.java
java NullFactoryDemo
```

Kết quả:

```text
List.of rejected null
List.copyOf rejected null
size=2
```

- `List.of(1, null)` không tạo thành công danh sách vì factory từ chối `null`.
- `List.copyOf(...)` cũng kiểm tra danh sách nguồn và từ chối nếu có phần tử `null`; nó không âm thầm bỏ `null`.
- Một danh sách hợp lệ do `List.of` trả về có thể được gán cho `List<? extends Number>` để đọc số, nhưng `add(null)` qua tham chiếu ấy vẫn không làm nó trở thành mutable.

Hành vi từ chối `null` của factory và hành vi từ chối thay đổi của danh sách kết quả là hai điều riêng biệt, dù có thể cùng xuất hiện.

## 8. `null` có thể đọc ra dưới kiểu `Number` dù cận wildcard là `Number`

Một ngộ nhận hay gặp: “`? extends Number` bảo đảm mỗi lần `get` trả về một đối tượng `Number` hợp lệ, nên có thể gọi `doubleValue()` ngay.” Thực tế, compiler chỉ bảo đảm kiểu kết quả **tương thích với `Number`**, chứ không bảo đảm giá trị khác `null`.

Ví dụ:

```java
List<Integer> integers = new ArrayList<>();
integers.add(null);

List<? extends Number> source = integers;
Number first = source.get(0); // first == null.

// double x = first.doubleValue(); // Ném NullPointerException nếu chạy.
```

Nếu dữ liệu có thể có `null`, cần xử lý rõ ràng trong thuật toán. Ví dụ phương thức cộng, bỏ qua phần tử `null`:

Lưu thành `NullableNumberSum.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class NullableNumberSum {
    static double sumPresent(List<? extends Number> source) {
        double sum = 0.0;

        for (Number number : source) {
            if (number != null) {
                sum += number.doubleValue();
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
        source.add(null);

        System.out.println(sumPresent(source));
    }
}
```

```bash
javac NullableNumberSum.java
java NullableNumberSum
```

Kết quả:

```text
6.0
```

Đây là **chính sách nghiệp vụ** “bỏ qua phần tử `null`”, không phải mặc định bắt buộc. Trong ứng dụng khác, việc gặp `null` có thể là lỗi dữ liệu và cần ném exception sớm, hoặc cần được biểu diễn bằng một mô hình dữ liệu rõ ràng hơn. Điều quan trọng là không suy từ cận `extends Number` ra cam kết non-null.

## 9. `add(null)` với `?` và `? super T` cũng có hai tầng kiểm tra

Cơ chế `null` không độc quyền của `? extends`. Với tham số generic là kiểu tham chiếu, một biến có `?` hoặc `? super T` cũng có thể cho phép truyền `null` ở những API nhận phần tử:

```java
static void examples(
        List<?> unknown,
        List<? extends Number> numbers,
        List<? super Integer> destination) {

    unknown.add(null);     // Hợp lệ về kiểu.
    numbers.add(null);     // Hợp lệ về kiểu.
    destination.add(null); // Hợp lệ về kiểu.
    destination.add(10);   // Cũng hợp lệ: Integer phù hợp cận dưới.

    // unknown.add(10); // Không biên dịch.
    // numbers.add(10); // Không biên dịch.
}
```

Đoạn này là minh họa chữ ký và kiểm tra kiểu, **không phải** chương trình chạy độc lập. Mọi lời gọi `add` thành công hay ném exception vẫn tùy thuộc danh sách thực tế được truyền vào.

Đối chiếu ý nghĩa:

| Biến | Giá trị không-null có thể thêm bằng `add` | Có thể gọi `add(null)` về mặt kiểu? |
| --- | --- | --- |
| `List<?>` | Không thể chọn tùy ý một giá trị cụ thể | Có |
| `List<? extends Number>` | Không thể chọn tùy ý một `Number` cụ thể | Có |
| `List<? super Integer>` | `Integer` (và kiểu con, nếu có) | Có |
| `List<Integer>` | `Integer` | Có |

Cột cuối **chỉ nói về trình biên dịch**, không phải bảo đảm runtime. Không nên biến bảng này thành “danh sách được phép chứa `null`”; mỗi implementation có quy tắc riêng.

## 10. Khác biệt giữa `add(null)` và ép một giá trị có kiểu sang `null`

Compiler quan tâm **kiểu tĩnh của biểu thức** truyền vào, không chỉ giá trị runtime. Khi một biến có kiểu cụ thể đang chứa `null`, nó vẫn mang kiểu khai báo của biến đó:

```java
static void addExamples(List<? extends Number> target) {
    Integer boxed = null;

    target.add(null);   // Hợp lệ: null literal.
    // target.add(boxed); // Không biên dịch: boxed có kiểu Integer.
    // target.add((Integer) null); // Không biên dịch: biểu thức có kiểu Integer.
    // target.add((Number) null);  // Không biên dịch: biểu thức có kiểu Number.
}
```

Đây là điểm đặc biệt dễ bỏ sót: một đối số có giá trị runtime là `null` **không** tự động được đối xử như *null literal* trong phân tích kiểu. `boxed` vẫn có kiểu `Integer`, và `Integer` không được chứng minh tương thích với kiểu capture cụ thể chưa biết của `? extends Number`.

Tương tự, trong lời gọi như `target.add(getNumberOrNull())`, kiểu trả về khai báo của phương thức có thể là `Number`. Nếu thế compiler vẫn từ chối lời gọi dù tại runtime phương thức có thể trả `null`. Không nên lách kiểm tra bằng raw type hay ép kiểu unchecked; hãy thiết kế lại API cho đúng mục đích.

## 11. Vì sao `add(null)` không phải cách khắc phục giới hạn của producer?

Quy tắc thực hành PECS (*Producer Extends, Consumer Super*) nhắc rằng `? extends T` phù hợp với nguồn **cung cấp** giá trị kiểu `T` cho chương trình. Nó không tuyên bố rằng `? extends T` tuyệt đối không thể có bất kỳ lời gọi thay đổi nào; nó nói rằng **không thể ghi một đối tượng `T` cụ thể tùy ý qua wildcard ấy một cách an toàn**.

Ví dụ:

```java
static double total(List<? extends Number> source) {
    double sum = 0.0;
    for (Number number : source) {
        if (number != null) {
            sum += number.doubleValue();
        }
    }
    return sum;
}
```

Một phương thức chuyên đọc như `total` hoàn toàn **không cần gọi `add(null)`**. Quyền biên dịch được phép thêm `null` chỉ là hệ quả của quy tắc chuyển đổi kiểu, không phải mục đích thiết kế của wildcard `extends`.

Nếu API cần thực sự thêm các giá trị `Integer` vào collection, dùng tham số có khả năng nhận chúng:

```java
static void addDefaults(List<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
```

Ngay cả `List<? super Integer>` cũng không bảo đảm runtime hỗ trợ sửa đổi. Nếu caller truyền một `List<Number>` không sửa đổi, compiler vẫn chấp nhận kiểu tham số nhưng `add(10)` có thể ném `UnsupportedOperationException`. Lựa chọn wildcard đúng mới giải quyết **an toàn kiểu**; muốn thêm được thật sự còn cần hợp đồng về collection có thể thay đổi.

## 12. Sai lầm thường gặp

### Sai lầm 1: “Compiler cho `add(null)` nên API bảo đảm chấp nhận `null`”

Sai vì kiểm tra kiểu và chính sách dữ liệu là hai tầng. `ArrayDeque` là ví dụ có thể thêm phần tử bình thường nhưng cấm `null`; `List.of` cấm mọi thay đổi cấu trúc.

**Cách làm đúng:** đọc hợp đồng collection cụ thể; không suy luận null policy từ `? extends`.

### Sai lầm 2: “`? extends Number` luôn trả về giá trị không-null”

Sai. `Number n = list.get(0)` hoàn toàn có thể cho `n == null` nếu nguồn chứa `null`. Gọi `n.doubleValue()` cần có bảo đảm không-null từ dữ liệu hoặc kiểm tra trước.

**Cách làm đúng:** xác định chính sách đầu vào và kiểm tra `null` khi cần, độc lập với wildcard.

### Sai lầm 3: “Không hỗ trợ `add` tức là không thể có `null` trong collection”

Sai. `Arrays.asList(1, null)` chứa `null` nhưng có kích thước cố định và không hỗ trợ `add`. `Collections.unmodifiableList(backingList)` cũng có thể hiển thị phần tử `null` của danh sách nền mà không cho thêm phần tử qua wrapper.

**Cách làm đúng:** phân biệt thao tác được hỗ trợ với trạng thái dữ liệu được phép.

### Sai lầm 4: “Chỉ cần ép một `Integer` thành `Number` rồi thêm qua `extends`”

Sai. `target.add((Number) 10)` vẫn không biên dịch với `List<? extends Number>`: compiler không thể khẳng định đối số kiểu thực tế là `Number` thay vì một lớp con khác.

**Cách làm đúng:** sử dụng `List<? super Integer>` nếu cần ghi `Integer`, hoặc một kiểu chính xác `List<Integer>` khi caller và callee đã thống nhất.

### Sai lầm 5: “`(Integer) null` và literal `null` hoàn toàn giống nhau với compiler”

Sai trong ngữ cảnh wildcard capture. Literal `null` chuyển được sang mọi kiểu tham chiếu, còn `(Integer) null` là biểu thức có kiểu tĩnh `Integer`. Vì thế, `target.add((Integer) null)` vẫn không hợp lệ với `List<? extends Number>`.

**Cách làm đúng:** chú ý kiểu tĩnh của biểu thức; đừng dùng cast/raw type để né quy tắc an toàn generic.

### Sai lầm 6: “`List.copyOf` sẽ tự bỏ các phần tử `null`”

Sai. `List.copyOf` từ chối đầu vào có phần tử `null` bằng `NullPointerException`; nếu cần lọc, phải lọc rõ ràng và quyết định xử lý dữ liệu tại lớp nghiệp vụ.

## 13. Thiết kế API để không dựa vào sự cho phép tình cờ của `add(null)`

Nếu một phương thức chỉ dùng collection làm nguồn dữ liệu, nên viết hợp đồng theo hướng đọc:

```java
static double average(List<? extends Number> values) {
    double sum = 0.0;
    int count = 0;

    for (Number number : values) {
        if (number != null) {
            sum += number.doubleValue();
            count++;
        }
    }
    return count == 0 ? 0.0 : sum / count;
}
```

Nếu yêu cầu nghiệp vụ **cấm `null`**, có thể kiểm tra rõ tại ranh giới API thay vì hy vọng wildcard hoặc cận kiểu làm việc ấy:

```java
import java.util.List;
import java.util.Objects;

static void requireNoNullElements(List<? extends Number> source) {
    for (Number value : source) {
        Objects.requireNonNull(value, "source must not contain null");
    }
}
```

Hai ví dụ trên là phương thức minh họa: ví dụ thứ hai có các dòng `import` để dùng trong class Java của ứng dụng. `Objects.requireNonNull` sẽ ném `NullPointerException` với thông điệp chỉ định nếu gặp phần tử `null`; phương thức này không sửa collection và không thay đổi cách generic hoạt động.

Nếu cần xây dựng danh sách mới chỉ chứa giá trị không-null, có thể tạo danh sách đích cụ thể và thêm từng giá trị được kiểm tra. Khi đó sự chấp nhận `null` và tính khả biến nằm trong thiết kế của **đối tượng đích**, chứ không ngầm suy ra từ kiểu `List<? extends Number>` của nguồn.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Với `List<? extends Number> xs`, lời gọi nào qua được trình biên dịch?

```java
xs.add(null);        // A
// xs.add(1);         // B
// xs.add((Integer) null); // C
```

**Đáp án:** A. Biểu thức literal `null` tương thích với kiểu tham chiếu capture; `Integer` không được chứng minh tương thích với mọi kiểu phần tử có thể nằm sau wildcard.

**Câu 2.** Vì sao `List.of(1, 2)` không cho `add(null)` dù compiler chấp nhận lời gọi trên `List<? extends Number>`?

**Đáp án:** `List.of` tạo danh sách không sửa đổi; `add` ném `UnsupportedOperationException` theo hợp đồng collection.

**Câu 3.** Với `ArrayDeque<Integer>`, việc `add(null)` thất bại có phải vì `Integer` không phải kiểu con của `Number` không?

**Đáp án:** Không. Nguyên nhân là `ArrayDeque` cấm `null`; lời gọi đã vượt qua kiểm tra kiểu.

**Câu 4.** Một `List<? extends Number>` chứa `null` có thể trả `null` từ `get(0)` không?

**Đáp án:** Có. Cận `Number` không chứng minh giá trị khác `null`.

**Câu 5.** `Arrays.asList(1, 2)` có thể thực hiện `set(0, null)` nhưng không thực hiện `add(null)`. Vì sao?

**Đáp án:** Danh sách kích thước cố định có thể thay giá trị phần tử đã có bằng `null`, nhưng không hỗ trợ thao tác tăng kích thước.

## 15. Ghi nhớ

- Với `List<? extends T>` và `T` là kiểu tham chiếu, `add(null)` **biên dịch được**; `add(nonNullT)` thường bị từ chối vì kiểu phần tử thực tế chưa biết.
- **Quyền gọi phương thức theo kiểu tĩnh khác với thành công của thao tác tại runtime.** `UnsupportedOperationException` phản ánh khả năng sửa đổi; `NullPointerException` có thể phản ánh chính sách cấm `null`.
- `ArrayList`, `HashSet` có thể chứa `null`; `ArrayDeque` không nhận `null`; `List.of` vừa cấm `null` khi tạo vừa không cho thêm sau khi tạo.
- `set(index, null)` cũng có thể biên dịch qua `List<? extends T>`, nhưng thành công hay không vẫn tùy collection và chỉ số hợp lệ.
- Cận `extends Number` **không phải** cam kết “không có phần tử `null`”. Thuật toán đọc vẫn phải xử lý `null` theo hợp đồng dữ liệu.
- Thiết kế API nên chọn wildcard theo chiều đọc/ghi, đồng thời quy định riêng tính khả biến và chính sách `null` của dữ liệu.

**Kết luận cốt lõi:** `add(null)` được chấp nhận ở bước *kiểm tra kiểu generic* chỉ vì `null` tương thích với kiểu tham chiếu chưa biết. Collection cụ thể có thể chấp nhận, từ chối `null` hoặc từ chối toàn bộ thao tác thêm; phải dựa trên hợp đồng thực tế của collection để biết điều gì sẽ xảy ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upper-bounded-wildcards-006">Wildcard extends không biến đối tượng nguồn thành bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Wildcard `extends` không biến đối tượng nguồn thành bất biến

## Mục tiêu

`List<? extends Number>` thường xuất hiện trong các API **đọc dữ liệu số**, vì nó có thể nhận danh sách các kiểu phần tử như `Integer`, `Double` hoặc `Number`. Dù không thể thêm một giá trị `Number` bất kỳ thông qua tham chiếu đó, danh sách thực tế **vẫn có thể thay đổi**.

Sau bài này, bạn cần giải thích được bốn vấn đề:

1. `? extends Number` ràng buộc kiểu phần tử ở compile time như thế nào?
2. Vì sao sửa danh sách qua một tham chiếu nguồn vẫn làm thay đổi nội dung nhìn qua `List<? extends Number>`?
3. Vì sao `remove` và `clear` có thể gọi được qua wildcard nhưng đôi khi ném `UnsupportedOperationException`?
4. Làm thế nào để phân biệt wildcard, unmodifiable view và immutable snapshot khi thiết kế API?

## 1. Giới hạn của `extends` thuộc về hệ kiểu

Đầu tiên, xem đoạn mã trong một phương thức Java đã import `java.util.List` và `java.util.ArrayList`:

```java
List<Integer> source = new ArrayList<>(List.of(10, 20));
List<? extends Number> view = source;

Number value = view.get(0); // hợp lệ

// view.add(42);             // lỗi biên dịch
// view.add(3.14);           // lỗi biên dịch
// view.set(0, 42);          // lỗi biên dịch
```

Vì `Integer` kế thừa `Number`, `List<Integer>` có thể được **tham chiếu qua** `List<? extends Number>`. Điều này không có nghĩa `List<Integer>` là subtype của `List<Number>`: generics Java bất biến (*invariant*) đối với các đối số kiểu cụ thể.

`? extends Number` nghĩa là kiểu phần tử có một kiểu thực chưa biết và **có upper bound là `Number`**. Trình biên dịch cho phép đọc phần tử ra `Number` vì mọi phần tử hợp lệ của danh sách đều là một `Number`. Nhưng nó không thể cho phép thêm `42` hay `3.14`: nếu danh sách thực tế là `List<Double>` thì `42` không phải `Double`, còn nếu là `List<Integer>` thì `3.14` không phải `Integer`.

Các dòng lỗi đã được chú thích, nên đoạn code trên mang tính minh họa, không phải một chương trình Java độc lập.

Điểm rất quan trọng: **wildcard chỉ thay đổi những thao tác được chấp nhận khi biên dịch trên biến `view`.** Nó không sửa implementation, không tạo bản sao, không đóng băng đối tượng và không tự áp đặt cơ chế đồng bộ.

## 2. Nguồn và wildcard view có thể cùng trỏ tới một đối tượng

Với hai câu lệnh:

```java
List<Integer> source = new ArrayList<>(List.of(10, 20));
List<? extends Number> view = source;
```

mô hình tham chiếu là:

```text
source: List<Integer> ------------+
                                  |
                                  v
                         ArrayList<Integer>
                              [10, 20]
                                  ^
                                  |
view: List<? extends Number> -----+
```

Phép gán `view = source` **không sao chép phần tử**. Nó tạo thêm một biến trỏ đến chính đối tượng `ArrayList<Integer>` đang được `source` giữ.

Nếu sửa qua `source`:

```java
source.add(30);
source.set(0, 99);
System.out.println(view); // [99, 20, 30]
```

thì `view` thấy ngay kết quả khi đọc. Compiler cho phép `source.add(30)` và `source.set(0, 99)` vì biến nguồn có kiểu `List<Integer>` và các giá trị mới đều là `Integer`. Việc `view.add(30)` bị chặn không hề thay đổi quyền sửa danh sách của `source`.

Suy ra: **không thêm được thông qua một tham chiếu không đồng nghĩa không có tham chiếu nào thêm được**.

Một đối tượng còn có thể được truyền qua phương thức, lưu trong field, được nhận bằng constructor hoặc được chia sẻ cho nhiều thành phần. Nếu các tham chiếu khác có quyền sửa và implementation hỗ trợ sửa, dữ liệu nhìn qua `List<? extends Number>` vẫn biến động.

## 3. Ví dụ Java 17+ biên dịch và chạy độc lập

Tạo tệp tên **`ExtendsNotImmutableDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtendsNotImmutableDemo {
    public static void main(String[] args) {
        List<Integer> source = new ArrayList<>(List.of(10, 20));
        List<? extends Number> view = source;

        System.out.println("Initially: " + view);

        source.add(30);
        source.set(0, 99);
        System.out.println("After source changes: " + view);

        Number first = view.get(0);
        System.out.println("First as Number: " + first);

        boolean removed = view.remove(Integer.valueOf(20));
        System.out.println("Removed through wildcard: " + removed);
        System.out.println("Source after removal: " + source);

        view.clear();
        System.out.println("Source after clear: " + source);

        List<Integer> backing = new ArrayList<>(List.of(1, 2));
        List<? extends Number> unmodifiable =
                Collections.unmodifiableList(backing);

        try {
            unmodifiable.clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("Wrapper rejects clear");
        }

        backing.add(3);
        System.out.println("Wrapper reflects source: " + unmodifiable);

        List<? extends Number> snapshot = List.copyOf(backing);
        backing.add(4);
        System.out.println("Snapshot: " + snapshot);
        System.out.println("Backing: " + backing);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ExtendsNotImmutableDemo.java
java ExtendsNotImmutableDemo
```

**Stdout:**

```text
Initially: [10, 20]
After source changes: [99, 20, 30]
First as Number: 99
Removed through wildcard: true
Source after removal: [99, 30]
Source after clear: []
Wrapper rejects clear
Wrapper reflects source: [1, 2, 3]
Snapshot: [1, 2, 3]
Backing: [1, 2, 3, 4]
```

**Vì sao chương trình cho kết quả trên?**

- `source` và `view` cùng trỏ tới `ArrayList<Integer>`; do đó `add(30)` và `set(0, 99)` qua `source` làm thay đổi dữ liệu nhìn qua `view`.
- `view.get(0)` đọc được phần tử kiểu `Number` nên biến `first` nhận giá trị `99`.
- `view.remove(Integer.valueOf(20))` hợp lệ vì overload `remove(Object)` chỉ cần một đối tượng để tìm/xóa. `ArrayList` hỗ trợ xóa nên trả về `true`.
- `view.clear()` không nhận phần tử mới, vì vậy nó được phép về mặt kiểu và hoạt động trên `ArrayList`. Do cùng một đối tượng, `source` trở thành `[]`.
- `Collections.unmodifiableList(backing)` tạo wrapper từ chối sửa qua wrapper, nhưng `backing.add(3)` vẫn được phép; kết quả hiển thị qua wrapper trở thành `[1, 2, 3]`.
- `List.copyOf(backing)` tạo snapshot cấu trúc không sửa được từ `backing` đang mutable. Khi thêm `4` vào `backing`, snapshot vẫn chứa `[1, 2, 3]`.

Điểm chung của `view`, `unmodifiable` và `snapshot` là **chúng đều có kiểu `List<? extends Number>`**. Nhưng hành vi khác nhau hoàn toàn tùy đối tượng nền. Đây là bằng chứng trực tiếp rằng wildcard **không phải công cụ bảo đảm tính bất biến**.

## 4. Vì sao vẫn có thể `remove` hoặc `clear` qua `? extends`?

Để hiểu, xem chữ ký rút gọn liên quan của `List<E>` và `Collection<E>`. Đây là **hình dạng API minh họa**, không phải mã source đầy đủ của JDK:

```java
E get(int index);
boolean add(E element);
E set(int index, E element);

boolean remove(Object value);
E remove(int index);
void clear();
```

Dấu wildcard ở `List<? extends Number>` khiến compiler **không biết chính xác `E`**. Những phương thức đưa một giá trị `E` mới vào collection vì thế bị giới hạn:

- `add(E)` phải biết rằng giá trị cần thêm có đúng kiểu `E`.
- `set(int, E)` phải biết rằng giá trị thay thế có đúng kiểu `E`.

Trong khi đó, các phương thức xóa không tạo giá trị phần tử mới:

- `clear()` không nhận tham số kiểu `E`.
- `remove(Object)` nhận `Object` để đối chiếu với phần tử đang có.
- `remove(int)` nhận chỉ số nguyên và trả lại một phần tử kiểu `E`; phần tử trả về có thể được dùng dưới kiểu `Number`.

Ví dụ, hai phương thức dưới đây có thể biên dịch khi đặt trong một lớp đã import `java.util.List`:

```java
static Number takeFirst(List<? extends Number> numbers) {
    return numbers.remove(0);
}

static void empty(List<? extends Number> numbers) {
    numbers.clear();
}
```

Cả hai không hề thêm một `Integer` hay `Double` mới. `takeFirst` yêu cầu phần tử tại chỉ số `0`; nếu danh sách rỗng, `remove(0)` ném `IndexOutOfBoundsException`. Với implementation không hỗ trợ xóa, thao tác có thể ném `UnsupportedOperationException`.

**Đừng nhầm hai overload của `remove`:**

```java
List<Integer> source = new ArrayList<>(List.of(10, 20, 30));
List<? extends Number> view = source;

view.remove(1);                    // xóa tại index 1 (giá trị 20)
view.remove(Integer.valueOf(30));  // xóa giá trị 30
System.out.println(source);       // [10]
```

Lời gọi `remove(1)` chọn `remove(int)`; lời gọi `remove(Integer.valueOf(30))` chọn `remove(Object)`. Đây cũng là đoạn minh họa trong phương thức, cần import `java.util.List` và `java.util.ArrayList`.

## 5. Một số danh sách cho sửa, một số từ chối: phải xem implementation

| Đối tượng được tham chiếu qua `List<? extends Number>` | Gọi `add(Number)` qua wildcard | Gọi `clear()` qua wildcard | Trạng thái thực tế |
| --- | --- | --- | --- |
| `new ArrayList<Integer>()` | Compiler chặn | Biên dịch được và chạy được | Danh sách mutable, alias nguồn có thể thêm/thay/xóa |
| `new LinkedList<Double>()` | Compiler chặn | Biên dịch được và chạy được | Danh sách mutable, alias nguồn có thể thêm/thay/xóa |
| `Arrays.asList(1, 2)` | Compiler chặn | Biên dịch được, nhưng xóa kích thước không được hỗ trợ | Danh sách kích thước cố định; có thể `set` qua tham chiếu có kiểu phù hợp |
| `List.of(1, 2)` | Compiler chặn | Biên dịch được, runtime từ chối | Danh sách không cho sửa |
| `Collections.unmodifiableList(source)` | Compiler chặn | Biên dịch được, runtime từ chối | Wrapper chặn thay đổi qua nó; nguồn có thể vẫn mutable |
| `List.copyOf(source)` | Compiler chặn | Biên dịch được, runtime từ chối | Danh sách không cho sửa, tách khỏi các lần sửa cấu trúc nguồn mutable về sau |

Ở cột `add(Number)`, “compiler chặn” nói về việc thêm một **biểu thức non-null kiểu `Number`** qua `List<? extends Number>`. Bảng cũng không ngụ ý mọi lời gọi `clear()` trên mọi implementation đều thực hiện thành công: Java Collections có các thao tác sửa đổi tùy chọn (*optional operations*), nên runtime có thể ném `UnsupportedOperationException`.

`Arrays.asList` tạo **danh sách kích thước cố định**, không tương đương immutable hoàn toàn. Thay thế giá trị ở một chỉ số đã có bằng `set` có thể thành công qua tham chiếu đúng kiểu. `List.of` lại không cho thay thế, thêm hoặc xóa phần tử.

Với `Collections.unmodifiableList(source)`, wrapper không cho phép sửa **qua nó**, nhưng nội dung wrapper có thể thay đổi nếu bên khác vẫn giữ `source` và sửa danh sách gốc. Đây là view không cho sửa, khác với snapshot độc lập.

## 6. `List<?>`, `List<? extends Number>` và `List<Number>` khác nhau

Những kiểu tham chiếu này khác **quyền đọc/ghi theo kiểu**, không tự quyết định mutability:

- `List<?>`: phần tử chưa biết kiểu, đọc an toàn dưới `Object`.
- `List<? extends Number>`: phần tử có upper bound `Number`, đọc dưới `Number`.
- `List<Number>`: element type khai báo chính xác `Number`; thêm `Integer` hay `Double` hợp kiểu nếu list cho sửa.

```java
List<Integer> original = new ArrayList<>(List.of(1, 2));
List<?> any = original;
List<? extends Number> numbers = original;
Object a = any.get(0);
Number b = numbers.get(0);
// List<Number> invalid = original; // Invariance.
```

Cả `any` và `numbers` trỏ tới **cùng list**. Alias gốc vẫn có thể sửa; wildcard không tạo bản sao hoặc khóa object. Những ngoại lệ liên quan `add(null)` cần phân biệt type checking với chính sách runtime null, được giải thích riêng ở bài chuyên đề.

## 7. Dùng `? extends` đúng mục đích trong API

Giả sử một hàm chỉ cần tính tổng:

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number n : values) {
        total += n.doubleValue();
    }
    return total;
}
```

Đây là đoạn phương thức minh họa. Chữ ký này nhận `List<Integer>`, `List<Double>` và các danh sách mang subtype khác của `Number` mà không buộc người gọi chuyển tất cả thành `List<Number>`.

**Producer Extends** trong quy tắc PECS có nghĩa `? extends T` thuận tiện khi API cần **nhận và đọc dữ liệu kiểu `T`**. Quy tắc này không tuyên bố API là read-only theo nghĩa bất biến; các thao tác xóa vẫn có thể khả dụng.

Nếu phương thức chỉ cần duyệt, dùng `Iterable<? extends Number>` còn biểu đạt phạm vi API hẹp hơn; nó không trực tiếp cung cấp `clear()`. Tuy nhiên, giới hạn những phương thức nhìn thấy qua tham chiếu vẫn không khóa đối tượng nền. Một iterator thậm chí có thể có thao tác `remove()` tùy chọn.

Nếu bạn thực sự cần **thêm `Integer`**, kiểu `List<Integer>` hoặc `List<? super Integer>` thường thích hợp hơn:

```java
static void appendInteger(List<? super Integer> destination) {
    destination.add(42);
}
```

`? super Integer` chấp nhận danh sách có kiểu phần tử là `Integer` hoặc siêu kiểu của `Integer` và cho phép thêm `Integer` an toàn. Nó không có nghĩa danh sách thực tế chắc chắn hỗ trợ thêm (ví dụ, một unmodifiable list vẫn có thể từ chối khi chạy).

## 8. Muốn bảo vệ dữ liệu, dùng cơ chế bảo vệ dữ liệu

### Chặn sửa qua kết quả trả về

```java
static List<? extends Number> exposeView(List<Integer> source) {
    return java.util.Collections.unmodifiableList(source);
}
```

Người nhận kết quả không thể sửa qua wrapper. Nhưng bên giữ `source` vẫn có thể sửa; mọi thay đổi từ nguồn có thể xuất hiện khi người nhận đọc wrapper lần sau. Vì thế, wrapper thích hợp cho **chính sách chỉ đọc qua đường truy cập** chứ không tự tạo ảnh chụp dữ liệu.

### Tạo snapshot về cấu trúc danh sách

```java
static List<? extends Number> exposeSnapshot(List<Integer> source) {
    return List.copyOf(source);
}
```

Với `source` là một mutable list, `List.copyOf(source)` tạo danh sách không cho sửa chứa các phần tử hiện tại. Sau này thay đổi kích thước hoặc thay phần tử trong `source` không ảnh hưởng tới cấu trúc snapshot. `List.copyOf` không cho phần tử `null`; nếu đầu vào là danh sách không cho sửa thích hợp, JDK có thể tái sử dụng instance.

**Lưu ý về sao chép nông:** `List.copyOf` không clone sâu từng phần tử. Nếu danh sách chứa đối tượng mutable và có một tham chiếu bên ngoài sửa trạng thái đối tượng đó, sự thay đổi thuộc tính vẫn có thể được quan sát qua snapshot. Muốn snapshot bất biến hoàn toàn cho cả đồ thị đối tượng, phải dùng đối tượng phần tử immutable hoặc sao chép trạng thái phù hợp.

### Thiết kế quyền sở hữu dữ liệu

Đôi khi bạn không cần wrapper nếu API chỉ bộc lộ kết quả tính toán hoặc một cấu trúc đọc đã cố định, nhưng điều đó là quyết định thiết kế dựa trên **quyền sở hữu và giới hạn tham chiếu**. Bản thân wildcard không kiểm soát alias hay lifetime.

Đồng thời, `? extends` không thêm thread-safety. Nếu một luồng thay đổi `ArrayList` khi luồng khác đang duyệt, bạn phải tự thiết kế đồng bộ hoặc chọn collection thích hợp; wildcard không bảo đảm snapshot nhất quán hay ngăn `ConcurrentModificationException`.

## 9. Các sai lầm phổ biến

**“Biến `List<? extends Number>` không gọi được `add(42)` nên đối tượng của nó đã immutable.”** Sai. Điều bị chặn là phép thêm giá trị đó **qua biến với kiểu wildcard**. Một tham chiếu `List<Integer>` khác có thể sửa cùng đối tượng.

**“`? extends Number` đồng nghĩa `List<Number>`.”** Sai. Một danh sách `Integer` có thể được nhìn dưới `List<? extends Number>` nhưng không tự biến thành `List<Number>`; chèn `Double` vào `List<Integer>` là không an toàn kiểu.

**“`? extends` không thể gọi bất kỳ phương thức sửa nào.”** Sai. `remove(Object)`, `remove(int)`, `clear()` không chèn một phần tử `E` tùy ý; vì vậy compiler cho phép gọi và implementation mutable có thể thực hiện thành công.

**“Dùng `Collections.unmodifiableList` sẽ không thấy nguồn đổi nữa.”** Sai. Wrapper theo dõi danh sách nguồn. Để giữ snapshot cấu trúc độc lập với nguồn mutable, hãy tạo bản sao không cho sửa thích hợp.

**“`List.copyOf` biến mọi phần tử thành immutable.”** Sai. Danh sách được sao chép về cấu trúc và giữ tham chiếu tới phần tử; đối tượng phần tử mutable vẫn có thể thay đổi trạng thái từ nơi khác.

**“Wildcard làm dữ liệu an toàn khi nhiều luồng cùng truy cập.”** Sai. Generics xử lý kiểu, không xử lý đồng bộ.

## 10. Tự kiểm tra

**Câu 1.** Với `List<Integer> a = new ArrayList<>(); List<? extends Number> b = a;`, vì sao `a.add(42)` chạy được nhưng `b.add(42)` không biên dịch?

Vì compiler biết chính xác kiểu phần tử của `a` là `Integer`, còn của `b` là một subtype chưa biết của `Number`. Cả hai vẫn trỏ đến cùng một đối tượng.

**Câu 2.** Sau khi `a.add(42)`, `b.size()` có đổi không?

Có, vì danh sách nền chính là đối tượng mà `a` vừa sửa. Phép gán `b = a` không tạo bản sao.

**Câu 3.** `b.clear()` có biên dịch không? Có luôn chạy thành công không?

Biên dịch được. Nó chạy thành công nếu đối tượng nền hỗ trợ `clear`; ví dụ `ArrayList` hỗ trợ, còn `List.of` và unmodifiable wrapper từ chối.

**Câu 4.** `Collections.unmodifiableList(a)` và `List.copyOf(a)` khác nhau ở điểm nào?

Wrapper chặn sửa qua chính nó nhưng có thể thấy các lần sửa `a`; với `a` mutable, `List.copyOf(a)` tạo danh sách không cho sửa lưu cấu trúc phần tử ở thời điểm tạo, không phản ánh lần thêm/xóa sau đó trên `a`.

**Câu 5.** Vì sao `List<? extends Number>` vẫn hữu ích nếu nó không bảo đảm bất biến?

Vì nó cho phép API đọc nhiều loại `List` có phần tử là subtype của `Number`, đồng thời ngăn mã dùng tham chiếu đó thêm nhầm một kiểu phần tử không tương thích. Đó là **an toàn kiểu**, một mục tiêu khác với **bất biến dữ liệu**.

## Kết luận

`? extends Number` diễn đạt “tôi có thể đọc những phần tử thuộc một subtype chưa biết của `Number`”. Nó **không tạo ra một đối tượng bất biến**: tham chiếu nguồn có kiểu cụ thể vẫn sửa được nếu implementation cho phép, và các thao tác xóa không cần chèn phần tử mới cũng có thể gọi được qua wildcard. Muốn bảo vệ trạng thái, phải chọn hợp đồng object phù hợp, wrapper hoặc snapshot, đồng thời quản lý các alias và các đối tượng phần tử mutable.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="upper-bounded-wildcards-007">Áp dụng cho đầu vào cung cấp dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Áp dụng `? extends T` cho đầu vào cung cấp dữ liệu trong Java Generics

Khi một tham số phương thức **cung cấp dữ liệu cho phương thức đọc và xử lý** (*producer/source*), cách khai báo thường thích hợp là **`? extends T`**. Nó cho phép một API chấp nhận danh sách hoặc tập hợp của `T` **và các kiểu con của `T`** trong khi vẫn giữ kiểm tra kiểu tại thời điểm biên dịch.

Ví dụ, một hàm tính tổng chỉ cần lấy các phần tử dưới dạng `Number`. Nó không cần thêm `Number` vào danh sách được truyền vào. Vì vậy tham số `List<? extends Number>` phù hợp hơn `List<Number>`: cả `List<Integer>`, `List<Double>` lẫn `List<Number>` đều dùng được làm nguồn dữ liệu.

Quy tắc này là nửa đầu của **PECS — Producer Extends, Consumer Super**. Bài này tập trung vào nửa **Producer Extends**, nghĩa là cách thiết kế và sử dụng **tham số đầu vào cung cấp phần tử**.

## 1. “Đầu vào cung cấp dữ liệu” nghĩa là gì?

Một collection là **producer** đối với một phương thức khi phương thức đó chủ yếu **lấy phần tử từ collection** để đọc, phân tích, chuyển đổi, tổng hợp hoặc ghi sang một đích khác.

Các tình huống thường gặp:

- Tính tổng, trung bình, cực trị từ danh sách số.
- Đọc danh sách chuỗi để hiển thị hoặc ghi log.
- Duyệt tập hợp đối tượng kế thừa một lớp cha hoặc triển khai một interface chung.
- Sao chép các phần tử từ danh sách nguồn sang một danh sách đích.
- Tạo một kết quả mới từ các phần tử của đầu vào.

**Producer và consumer là vai trò của tham số trong thao tác cụ thể**, không phải đặc điểm bất biến của đối tượng. Cùng một `ArrayList<Integer>` có thể là nguồn dữ liệu đối với phương thức `sum`, nhưng là nơi nhận dữ liệu đối với phương thức `addDefaults`.

Ví dụ mô tả ý định:

```java
static double sum(List<? extends Number> source) {
    double result = 0.0;
    for (Number n : source) {
        result += n.doubleValue();
    }
    return result;
}
```

Tên `source` và vòng lặp `for` cho thấy phương thức chỉ **nhận giá trị do danh sách cung cấp**. Nó không cần biết chính xác danh sách đó có tham số kiểu `Integer`, `Double` hay `BigDecimal`; mọi phần tử đều là `Number` để gọi `doubleValue()`.

Giá trị tính được ở đây dùng số dấu phẩy động `double`, có thể gặp sai số với một số phép tính. Với nghiệp vụ tài chính cần độ chính xác thập phân, nên thiết kế phép tính riêng dùng `BigDecimal`; wildcard không quyết định quy tắc số học.

## 2. Vì sao `List<Number>` quá chặt cho dữ liệu đầu vào?

Một API **chỉ nhận và đọc dữ liệu số** không cần buộc người gọi chuyển `List<Integer>` thành `List<Number>`:

```java
static double sum(Iterable<? extends Number> source) {
    double total = 0;
    for (Number value : source) total += value.doubleValue();
    return total;
}
```

`List<Integer>` không phải subtype của `List<Number>` vì generics invariant. Nhưng `Iterable<? extends Number>` chấp nhận `List<Integer>`, `List<Double>` và `List<Number>`; caller không phải dùng unchecked cast hay tạo bản sao chỉ để thỏa một chữ ký quá chặt.

Bài này lấy **vai trò producer** của tham số làm điểm xuất phát: không chỉ thêm wildcard, mà còn chọn interface đầu vào **nhỏ nhất cần thiết**. Nếu chỉ lặp qua phần tử, `Iterable<? extends Number>` có thể tốt hơn `List<? extends Number>` vì không đòi truy cập chỉ số hoặc method sửa list.

## 3. Cơ chế: đọc an toàn nhờ giới hạn trên

Với `Iterable<? extends CharSequence>`, một phương thức có thể đọc từng phần tử như `CharSequence` và gọi `length()`, `charAt()` hoặc `toString()`:

```java
static int totalLength(Iterable<? extends CharSequence> source) {
    int total = 0;
    for (CharSequence item : source) total += item.length();
    return total;
}
```

Danh sách `String` lẫn danh sách `StringBuilder` đều phù hợp vì hai class triển khai `CharSequence`. Wildcard có upper bound là **interface** vẫn dùng từ khóa `extends`. Cơ chế capture/upper bound đã được trình bày ở đầu chương; ở đây ta ứng dụng nó để **thiết kế một tham số đầu vào tối thiểu, không cần ép kiểu và không buộc caller dùng một container duy nhất**.

## 4. Vì sao producer thường không được thêm phần tử?

Một producer `List<? extends Number>` có thể thật sự là `List<Double>`, nên phép `add(10)` không được compiler cho phép. Cấm chèn giá trị mới giúp API đọc dữ liệu mà không làm mất contract element type của caller.

| Thao tác với `List<? extends Number>` | Compile-time | Ý nghĩa |
| --- | --- | --- |
| `Number n = source.get(0)` | Cho phép | Mọi phần tử thuộc subtype của `Number` |
| `source.add(10)` | Từ chối | Không biết subtype ẩn có nhận `Integer` không |
| `source.add(null)` | Cho phép về kiểu | Runtime có thể cấm null/mutation |
| `source.clear()` | Cho phép về kiểu | Không thêm phần tử mới, nhưng có thể thay dữ liệu |

Trong một **API producer**, điều quan trọng hơn việc kiểm tra lệnh `add` là **hợp đồng hành vi**: method có nên sửa source hay không? Nếu cam kết không sửa, hãy giữ implementation chỉ đọc và test điều đó. `? extends` tự nó không khiến list thành immutable hay read-only tuyệt đối.

## 5. Thiết kế một hàm nhận nhiều loại dữ liệu

Khi phương thức cần xử lý cùng một hành vi của **lớp cha hoặc interface**, hãy dùng giới hạn trên đúng mức.

### 5.1. Nguồn số

```java
static double average(List<? extends Number> source) {
    if (source.isEmpty()) {
        throw new IllegalArgumentException("source must not be empty");
    }

    double total = 0.0;
    for (Number number : source) {
        total += number.doubleValue();
    }
    return total / source.size();
}
```

Hàm này nhận `List<Integer>`, `List<Long>`, `List<Float>`, `List<Double>`, `List<BigDecimal>`... vì tất cả đều cung cấp `Number`. Nó có quyền đọc, dùng `size()` và `isEmpty()`; không cần ghi vào nguồn.

Lưu ý về hợp đồng hàm: `source` phải không rỗng; nếu không, hàm chủ động ném `IllegalArgumentException`. Nếu người gọi truyền `null`, ví dụ sẽ phát sinh `NullPointerException`; có thể dùng `Objects.requireNonNull(source)` ở API cần báo lỗi rõ hơn.

### 5.2. Nguồn interface chung

```java
static void display(Iterable<? extends CharSequence> source) {
    for (CharSequence text : source) {
        System.out.println(text.length());
    }
}
```

Ví dụ này có thể nhận `List<String>` và `List<StringBuilder>`. Nó chỉ cần thuộc tính chung `length()` của `CharSequence`. Khai báo bằng `Iterable` thay vì `List` còn giúp nhận các nguồn dữ liệu không nhất thiết hỗ trợ truy cập theo chỉ số.

### 5.3. Nguồn lớp cơ sở trong mô hình đối tượng

```java
class Animal {
    String name() {
        return "animal";
    }
}
class Dog extends Animal {
    @Override
    String name() {
        return "dog";
    }
}

static void printNames(Iterable<? extends Animal> animals) {
    for (Animal animal : animals) {
        System.out.println(animal.name());
    }
}
```

Các khai báo này là **đoạn minh họa cần đặt trong ngữ cảnh lớp/tệp phù hợp**, không phải chương trình độc lập có `main`. `printNames` có thể dùng với `List<Animal>` hoặc `List<Dog>`. Nếu chữ ký là `Iterable<Animal>`, `List<Dog>` không truyền trực tiếp được do invariance.

Điểm chung của cả ba ví dụ: phương thức quan tâm **hành vi đọc qua `T`**, chứ không cần quyền đưa các đối tượng `T` mới vào nguồn.

## 6. Chọn interface đầu vào tối thiểu

Wildcard giúp linh hoạt về **kiểu phần tử**; chọn interface đầu vào còn giúp linh hoạt về **kiểu collection**.

Các lựa chọn:

- `Iterable<? extends T>`: cần `for-each`, không yêu cầu `size()` hoặc truy cập chỉ số.
- `Collection<? extends T>`: cần khả năng duyệt, `size()`, `isEmpty()` hoặc thao tác chung của `Collection`.
- `List<? extends T>`: cần thứ tự và truy cập theo vị trí bằng `get(index)`.
- `Stream<? extends T>`: API thực sự nhận một luồng để thực hiện pipeline; nguồn stream có vòng đời và tính tiêu thụ riêng, nên không mặc nhiên dùng thay `Collection`.

Ví dụ, hàm tính tổng chỉ cần `Iterable`:

```java
static double sum(Iterable<? extends Number> source) {
    double total = 0.0;
    for (Number n : source) {
        total += n.doubleValue();
    }
    return total;
}
```

Điều này vừa thể hiện rằng nguồn **được đọc**, vừa cho phép nhiều implementation đầu vào hơn. Nếu hàm cần `source.size()` thì `Iterable` không cung cấp phương thức đó; lúc ấy dùng `Collection<? extends Number>` hoặc `List<? extends Number>` tùy nhu cầu.

Đừng chọn `List<? extends T>` chỉ vì thói quen nếu thuật toán chỉ cần vòng lặp: interface nhỏ hơn giúp hợp đồng API rõ hơn.

## 7. Sao chép từ producer sang consumer: kết hợp `extends` với `super`

Khi thao tác có **hai tham số**, một tham số cung cấp dữ liệu và một tham số nhận dữ liệu, chữ ký nên thể hiện cả hai vai trò:

```java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T item : source) {
        destination.add(item);
    }
}
```

Ở đây:

1. `source` là **producer**: cung cấp các giá trị kiểu `T` hoặc kiểu con của `T`, vì vậy dùng `? extends T`.
2. `destination` là **consumer**: chấp nhận các giá trị `T`, vì vậy dùng `? super T`.
3. Biến kiểu `T` kết nối hai vai trò; trình biên dịch xác minh quan hệ khi gọi.

Ví dụ:

```java
List<Integer> integers = List.of(2, 4, 6);
List<Number> numbers = new ArrayList<>();

appendAll(integers, numbers); // Hợp lệ.
System.out.println(numbers);  // [2, 4, 6]
```

Nếu viết cả hai tham số thành `List<?>`, không thể an toàn làm `destination.add(source.get(0))`, vì mỗi wildcard có thể đại diện cho kiểu khác nhau. Nếu viết tham số nguồn thành `List<T>`, API không thể hiện linh hoạt bằng `? extends T` trong trường hợp cần nhận một subtype của `T` đã xác định từ đích.

Đây là một trong các trường hợp PECS hữu ích nhất: **đầu vào cung cấp giá trị → `extends`; đầu ra nhận giá trị → `super`**.

Ví dụ gần gũi của thư viện chuẩn là `Collection.addAll(Collection<? extends E> c)`: đối số `c` được đọc để bổ sung phần tử vào collection đích, nên dùng `? extends E`.

Lưu ý: phương thức `appendAll` viết bằng vòng lặp. Nếu `source` và `destination` tham chiếu cùng một `ArrayList`, thêm phần tử trong lúc đang duyệt có thể dẫn tới `ConcurrentModificationException` hoặc hành vi không mong muốn. Nếu cho phép nguồn và đích trùng nhau, hãy thiết kế yêu cầu rõ ràng và cân nhắc tạo snapshot trước.

## 8. Chương trình Java 17+ chạy được

Ví dụ độc lập sau minh họa nguồn cung cấp số, chuỗi ký tự và thao tác sao chép an toàn sang đích. Không cần thư viện ngoài JDK.

Tạo tệp **`ProducerExtendsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class ProducerExtendsDemo {
    static double sum(Iterable<? extends Number> source) {
        double result = 0.0;
        for (Number n : source) {
            result += n.doubleValue();
        }
        return result;
    }

    static String joinText(Iterable<? extends CharSequence> source) {
        StringJoiner joined = new StringJoiner(" | ");
        for (CharSequence value : source) {
            joined.add(value.toString());
        }
        return joined.toString();
    }

    static <T> void appendAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    static List<Number> twice(List<? extends Number> source) {
        List<Number> output = new ArrayList<>();
        for (Number value : source) {
            output.add(value.doubleValue() * 2);
        }
        return output;
    }

    public static void main(String[] args) {
        List<Integer> ints = List.of(2, 4, 6);
        List<Double> doubles = List.of(1.5, 2.5);

        System.out.println("sum integers = " + sum(ints));
        System.out.println("sum doubles = " + sum(doubles));

        List<Number> mixed = new ArrayList<>();
        appendAll(ints, mixed);
        appendAll(doubles, mixed);
        System.out.println("mixed = " + mixed);

        System.out.println("twice = " + twice(doubles));

        List<StringBuilder> words = List.of(
                new StringBuilder("A"),
                new StringBuilder("B"));
        System.out.println("text = " + joinText(words));
    }
}
```

Biên dịch và chạy:

```bash
javac ProducerExtendsDemo.java
java ProducerExtendsDemo
```

**Stdout chính xác:**

```text
sum integers = 12.0
sum doubles = 4.0
mixed = [2, 4, 6, 1.5, 2.5]
twice = [3.0, 5.0]
text = A | B
```

Cách đọc kết quả:

- `sum(ints)` và `sum(doubles)` cùng gọi được vì `Iterable<? extends Number>` chấp nhận hai nguồn phần tử khác nhau; các phần tử được đọc dưới dạng `Number`.
- `appendAll` chuyển giá trị từ hai nguồn riêng biệt vào một `List<Number>` có khả năng nhận cả `Integer` lẫn `Double`.
- `twice` chỉ **đọc** danh sách nguồn và tạo một **danh sách mới** chứa các `Double` được tự động boxing để gán vào `List<Number>`. Nó không chỉnh sửa `doubles`.
- `joinText` chấp nhận `List<StringBuilder>` vì `StringBuilder` triển khai `CharSequence`.

Phần mã minh họa sự khác nhau giữa **kiểu nguồn cho phép đọc rộng** và **kiểu kết quả/đích xác định rõ**. Wildcard trên tham số nguồn không bắt buộc kiểu trả về cũng phải là wildcard.

## 9. So sánh `? extends T`, `T` và `? super T` trong chữ ký

Giả sử có:

```java
static void readNumbers(List<? extends Number> source) { /* đọc */ }
static void updateNumbers(List<Number> list) { /* đọc và thêm Number */ }
static void putIntegers(List<? super Integer> destination) { /* thêm Integer */ }
```

Ba chữ ký phục vụ ba nhu cầu khác nhau:

| Nhu cầu | Kiểu tham số phù hợp | Lý do |
| --- | --- | --- |
| Đọc số từ `List<Integer>`, `List<Double>`, `List<Number>` | `List<? extends Number>` | Mọi phần tử đọc được như `Number` |
| Đọc và thêm `Number` bất kỳ vào đúng một danh sách cho phép mọi `Number` | `List<Number>` | Biết chính xác kiểu phần tử cho cả đọc và ghi |
| Thêm `Integer` vào `List<Integer>` hoặc `List<Number>` hoặc `List<Object>` | `List<? super Integer>` | Đích chắc chắn tiếp nhận `Integer` |
| Chỉ cần duyệt số, không cần `List` cụ thể | `Iterable<? extends Number>` | Hợp đồng nguồn tối thiểu |
| Cần đọc từ nguồn và ghi sang đích khác có quan hệ kiểu | `<T>` với `? extends T` / `? super T` | Mô tả và kiểm tra mối quan hệ |

Bảng này nói về **khả năng được hệ kiểu chấp nhận**, không bảo đảm một collection cụ thể luôn hỗ trợ sửa đổi lúc chạy.

Một lời khuyên thiết kế: nếu phương thức nhận nguồn chỉ để đọc, hãy diễn tả bằng `? extends T` ngay trong chữ ký; đừng yêu cầu người gọi ép kiểu hoặc tự sao chép `List<Integer>` thành `List<Number>` khi không cần.

## 10. Những lỗi và hiểu lầm thường gặp

### 10.1. Dùng `List<Number>` cho tham số chỉ đọc

```java
static void printAll(List<Number> numbers) {
    for (Number n : numbers) {
        System.out.println(n);
    }
}
```

Nếu muốn nhận cả `List<Integer>` và `List<Double>`, đổi tham số thành `List<? extends Number>`. Không nên ép kiểu không an toàn để qua mặt trình biên dịch.

### 10.2. Tưởng `extends` là quyền thêm mọi subtype

Đây là cách hiểu sai:

```java
static void addSample(List<? extends Number> source) {
    // source.add(Integer.valueOf(1)); // LỖI BIÊN DỊCH.
    // source.add(Double.valueOf(2));  // LỖI BIÊN DỊCH.
}
```

`? extends Number` nghĩa là **một kiểu con chưa biết**, không phải danh sách trộn tùy ý mọi kiểu con của `Number`. Khi thật sự cần trộn `Integer` và `Double`, hãy tạo hoặc nhận `List<Number>`.

### 10.3. Nhầm một tham số producer với một đối tượng bất biến

`List<? extends Number>` vẫn có thể tham chiếu tới `ArrayList<Integer>` có thể chỉnh sửa. `clear()` và `remove(...)` có thể biên dịch. Muốn cam kết phương thức không sửa collection thì phải tuân theo thiết kế đó, có thể dùng defensive copy/unmodifiable view khi cần hợp đồng mạnh hơn. **Wildcard không phải một cơ chế bảo đảm bất biến dữ liệu.**

### 10.4. Lạm dụng wildcard cho kiểu trả về

`static List<? extends Number> createNumbers()` có thể hợp lệ, nhưng buộc phía gọi phải xử lý một kiểu phần tử không xác định, kể cả khi người viết API biết chính xác danh sách mình tạo ra.

Nếu trả về một danh sách số có thể thêm nhiều subtype, `List<Number>` thường rõ hơn. Nếu luôn tạo danh sách `Integer`, trả về `List<Integer>` rõ hơn. Ưu tiên wildcard ở **tham số** khi muốn mở rộng những đầu vào được chấp nhận; lựa chọn kiểu trả về theo hợp đồng đầu ra thật sự.

### 10.5. Dùng `List<?>` dù thuật toán cần `Number`

`List<?>` có thể nhận `List<String>`, nên `get()` chỉ sử dụng an toàn như `Object`. Nếu thuật toán gọi `doubleValue()`, giới hạn `? extends Number` giúp trình biên dịch bảo đảm mọi phần tử phù hợp và tránh kiểm tra/ép kiểu không cần thiết.

### 10.6. Nhầm wildcard với generic type parameter

Có thể viết hàm đơn giản chỉ đọc:

```java
static void inspect(List<? extends Number> source) {
    for (Number number : source) {
        System.out.println(number);
    }
}
```

Không nhất thiết phải viết:

```java
static <T extends Number> void inspectTyped(List<T> source) {
    for (T number : source) {
        System.out.println(number);
    }
}
```

Hai chữ ký đều dùng được cho nhiều loại `Number`. Nếu `T` xuất hiện **chỉ một lần** và không cần gọi thêm thao tác yêu cầu một kiểu cụ thể thống nhất, wildcard thường gọn hơn. Khi phải thể hiện **quan hệ giữa nhiều tham số hoặc giữa đầu vào và đầu ra**, generic type parameter `<T>` thường có giá trị hơn.

### 10.7. Cho rằng `extends` tự động chuyển hoặc sao chép danh sách

`List<? extends Number> source = integers` chỉ tạo **tham chiếu** đến cùng danh sách; nó không xây dựng một `List<Number>` mới và không chuyển mọi phần tử thành `Number` theo cách sao chép.

Nếu muốn một danh sách mới có kiểu `List<Number>` để thêm đa dạng số, có thể tạo:

```java
List<Integer> integers = List.of(1, 2, 3);
List<Number> numbers = new ArrayList<>(integers);
numbers.add(4.5);
```

Constructor này tạo danh sách mới về cấu trúc; các phần tử đã có được sao chép tham chiếu (shallow copy).

### 10.8. Tưởng `List<int>` có thể dùng làm nguồn

Generics Java nhận **kiểu tham chiếu**, nên `List<int>` không hợp lệ. Với số nguyên primitive, dùng `List<Integer>` và cơ chế boxing/unboxing hoặc chọn `IntStream` trong thuật toán mà stream primitive phù hợp hơn.

## 11. Bài tập nhận diện producer

Xét ba phương thức sau và xác định wildcard nào nên dùng:

**Tình huống A — xem tên đối tượng:** Phương thức duyệt danh sách các đối tượng có interface `CharSequence`, gọi `length()` và in kết quả; không thêm gì vào danh sách. Câu trả lời phù hợp là `Iterable<? extends CharSequence>`. Điều này nhận được `List<String>` và `List<StringBuilder>`.

**Tình huống B — tổng hợp số:** Phương thức tính tổng từ `Collection<Integer>`, `Collection<Double>` hoặc `Collection<Number>` bằng `doubleValue()`. Câu trả lời phù hợp là `Collection<? extends Number>` (hoặc `Iterable<? extends Number>` nếu không cần API riêng của `Collection`).

**Tình huống C — thêm phần tử từ nguồn vào đích:** Nguồn `List<Integer>` và đích `List<Number>`, đồng thời muốn thuật toán có thể dùng cho các cặp kiểu tương thích khác. Câu trả lời phù hợp là `<T> void appendAll(List<? extends T> source, List<? super T> destination)`.

Kiểm tra nhanh các khai báo sau:

```java
List<Integer> ints = List.of(1, 2);
List<Double> doubles = List.of(1.5, 2.5);
List<? extends Number> source1 = ints;    // Hợp lệ.
List<? extends Number> source2 = doubles; // Hợp lệ.

Number a = source1.get(0);                // Hợp lệ.
// source1.add(3);                        // LỖI BIÊN DỊCH.
// List<Number> nums = ints;              // LỖI BIÊN DỊCH.
```

Điều cần ghi nhớ ở tất cả tình huống: **mở rộng tập kiểu đầu vào được nhận** bằng wildcard chỉ có ý nghĩa khi thao tác thực tế vẫn giữ an toàn kiểu; nếu phương thức cần đưa phần tử vào chính nguồn, hãy đánh giá lại vai trò của tham số và chữ ký.

## Ghi nhớ

- **Producer** là tham số **cung cấp phần tử** cho thuật toán đọc; thường khai báo `? extends T`.
- `? extends T` giúp phương thức đọc được các phần tử như `T` từ collection có kiểu phần tử `T` hoặc subtype của `T`.
- **Generics bất biến**, nên `List<Integer>` không phải `List<Number>`; wildcard giải quyết nhu cầu nhận nguồn rộng hơn.
- `List<? extends T>` **không cho thêm giá trị không-null kiểu cụ thể** vào nguồn, vì kiểu phần tử thật chưa được biết.
- `extends` **không đồng nghĩa immutable**, không tự sao chép danh sách và không bảo đảm collection hỗ trợ sửa đổi.
- Khi có nguồn và đích, kết hợp **`? extends T` cho nguồn** và **`? super T` cho đích**.
- Chọn `Iterable`, `Collection` hoặc `List` theo đúng thao tác mà thuật toán cần; ưu tiên interface nhỏ nhất diễn đạt đủ hợp đồng.

Một chữ ký phương thức được thiết kế đúng vai trò sẽ nhận được nhiều loại đầu vào hơn mà không cần unchecked cast, đồng thời ngăn các thao tác ghi không an toàn ngay từ khi biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)
