<a id="back-to-top"></a>

# Wildcard không giới hạn

## Menu
- [Dấu ? biểu diễn một kiểu chưa biết](#unbounded-wildcard-001)
- [List&lt;?&gt; khác với danh sách chứa Object](#unbounded-wildcard-002)
- [Đọc giá trị từ wildcard không giới hạn](#unbounded-wildcard-003)
- [Giới hạn thêm và thay phần tử qua List&lt;?&gt;](#unbounded-wildcard-004)
- [Vì sao vẫn có thể clear hoặc remove trên một số List&lt;?&gt;](#unbounded-wildcard-005)
- [Mỗi wildcard có thể đại diện cho một kiểu khác nhau](#unbounded-wildcard-006)
- [Các vị trí cú pháp không cho phép wildcard](#unbounded-wildcard-007)

## <a id="unbounded-wildcard-001">Dấu ? biểu diễn một kiểu chưa biết</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu `?` biểu diễn một kiểu chưa biết trong Java Generics

## 1. Dấu `?` nói gì về kiểu dữ liệu?

Trong Java Generics, `?` là **wildcard** (ký tự đại diện cho một đối số kiểu). Khi được viết không kèm ràng buộc như `List<?>`, nó được gọi là **unbounded wildcard**. Dấu `?` mang nghĩa: *có một kiểu phần tử cụ thể nào đó, nhưng tại vị trí sử dụng này ta không biết kiểu ấy là gì*.

```java
List<String> strings = new ArrayList<>();
List<Integer> integers = new ArrayList<>();

List<?> first = strings;   // Kiểu phần tử thực của danh sách là String
List<?> second = integers; // Kiểu phần tử thực của danh sách là Integer
```

Cả `first` và `second` đều có kiểu khai báo `List<?>`, nhưng chúng tham chiếu đến các danh sách có tham số kiểu khác nhau. Với `first`, kiểu ẩn bên trong dấu hỏi là `String`; với `second`, kiểu ẩn là `Integer`. Compiler **không cho mã đang sử dụng biến `List<?>` tự ý giả định** đó là `String` hay `Integer`.

Nói chính xác hơn, `List<?>` mô tả “một `List<T>` với `T` nào đó chưa biết”, không mô tả “một danh sách có thể thay đổi tự do giữa mọi kiểu phần tử”. Java sẽ giữ sự an toàn này trong kiểm tra kiểu tại thời điểm biên dịch (*compile time*).

Lưu ý: `?` không phải là tên của một lớp, một kiểu dữ liệu độc lập, một giá trị `null`, hoặc “bất kỳ giá trị nào” theo nghĩa muốn ghi gì cũng được. Nó là cú pháp chỉ **một đối số kiểu chưa được xác định**.

## 2. Tại sao cần biểu diễn một kiểu chưa biết?

Giả sử ta cần một hàm chỉ in các phần tử, bất kể danh sách chứa chuỗi, số hay đối tượng khác. Hàm không cần thêm phần tử, cũng không cần giữ lại kiểu cụ thể khi trả kết quả.

```java
static void printAll(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Hàm này nhận được `List<String>`, `List<Integer>`, `List<Customer>` hoặc `List<Object>`. Khai báo `List<?>` diễn tả đúng ý định rằng thuật toán **không phụ thuộc vào tham số kiểu `E`** của `List<E>`.

Nếu khai báo tham số `List<Object>`, ta lại không thể truyền trực tiếp `List<String>`: generic trong Java là **bất biến** (*invariant*). Nếu khai báo raw `List`, ta đánh mất kiểm tra an toàn đối số kiểu và dễ phát sinh thao tác `unchecked`. Wildcard vừa cho phép tiếp nhận danh sách có nhiều tham số kiểu khác nhau, vừa giữ giới hạn thao tác cần thiết.

## 3. Hiểu `?` qua phép gán

Ví dụ sau mô tả những phép gán hợp lệ:

```java
List<String> names = new ArrayList<>(List.of("Lan", "Minh"));
List<Integer> ages = new ArrayList<>(List.of(20, 25));
List<Object> mixed = new ArrayList<>(List.of("Java", 21));

List<?> unknownNames = names; // Hợp lệ
List<?> unknownAges = ages;   // Hợp lệ
List<?> unknownMixed = mixed; // Hợp lệ
```

Các phép gán ấy không tạo bản sao danh sách. `unknownNames` và `names` trỏ đến **cùng một đối tượng**. Wildcard chỉ thay đổi thông tin kiểu mà đoạn mã qua biến mới được phép dựa vào.

Hai phép gán sau **không biên dịch**:

```java
List<String> names = new ArrayList<>();
List<?> anyNames = names;

// List<Object> objects = names;     // Lỗi: List<String> không phải List<Object>
// List<String> recovered = anyNames; // Lỗi: dấu ? chưa chắc là String
```

Compiler nhận `List<String>` làm `List<?>` vì không cần biết chính xác `String` để thực hiện các thao tác chung. Nhưng chiều ngược lại đòi hỏi bằng chứng rằng kiểu ẩn là `String`; chỉ biết `?` là chưa đủ.

Không nên đánh đồng “**nhận được** danh sách của bất kỳ kiểu phần tử nào” với “**cho phép thêm** bất kỳ loại phần tử nào vào danh sách”.

## 4. Đọc dữ liệu qua `List<?>`: biết được gì?

`List<E>.get(int)` vốn trả về `E`. Khi biến có kiểu `List<?>`, compiler giữ `E` như một kiểu ẩn, nhưng mọi phần tử thuộc kiểu tham chiếu đều có thể nhìn dưới kiểu `Object`. Vì vậy:

```java
List<String> names = List.of("Lan", "Minh");
List<?> items = names;

Object first = items.get(0);     // Hợp lệ
int count = items.size();        // Hợp lệ
boolean found = items.contains("Lan"); // Hợp lệ: contains nhận Object

// String text = items.get(0);  // Lỗi: compiler không biết ? là String
```

Có thể xử lý từng phần tử bằng kiểm tra kiểu khi thực sự cần:

```java
for (Object element : items) {
    if (element instanceof String text) {
        System.out.println(text.toUpperCase());
    }
}
```

Biểu thức `element instanceof String` kiểm tra đối tượng **tại runtime**. Nó không biến cả `List<?>` thành `List<String>`: danh sách có thể chứa kiểu phần tử khác trong những ngữ cảnh khác.

Các thao tác không phụ thuộc tham số kiểu như `size()`, `isEmpty()`, `iterator()`, `contains(Object)` và `toString()` vẫn dùng được. Với `iterator()`, mỗi phần tử có thể được nhận qua biến `Object` hoặc xử lý theo tham số kiểu ẩn trong một phương thức generic phụ.

## 5. Vì sao không thể thêm một phần tử tùy ý?

Hãy tưởng tượng compiler cho phép mã sau:

```java
List<String> names = new ArrayList<>(List.of("Lan"));
List<?> items = names;

// items.add(123); // Bị cấm khi biên dịch
```

Nếu `items.add(123)` được phép, chính `names` sẽ chứa một `Integer` dù cam kết là `List<String>`. Các lệnh đọc kiểu `String` sau này có thể gặp `ClassCastException`. Việc **từ chối thêm giá trị có kiểu cụ thể** bảo vệ lời cam kết của danh sách gốc.

Về nguyên tắc, với `List<?>`:

- `items.add("text")`: không biên dịch vì `?` có thể là `Integer`.
- `items.add(123)`: không biên dịch vì `?` có thể là `String`.
- `items.set(0, "text")`: không biên dịch vì không biết kiểu cần gán.
- `items.add(null)`: **biên dịch được**, do `null` có thể gán cho bất kỳ kiểu tham chiếu nào.

Ví dụ một danh sách mutable:

```java
List<String> names = new ArrayList<>(List.of("Lan"));
List<?> unknown = names;
unknown.add(null);              // Được phép ở mức kiểm tra kiểu
System.out.println(names);     // [Lan, null]
```

Việc `add(null)` biên dịch được **không đảm bảo mọi danh sách đều cho thêm null**: `List.of(...)` tạo danh sách không thể sửa đổi; một số implementation còn có quy định riêng về null. Trong các trường hợp đó lời gọi sửa đổi có thể ném exception. Quy tắc type-checking và khả năng mutation của implementation là hai vấn đề riêng.

## 6. Wildcard không giới hạn không làm danh sách bất biến

`List<?>` cấm thêm một giá trị non-null có kiểu tùy ý, nhưng **không cấm mọi thay đổi**. Các phương thức không cần cung cấp một phần tử thuộc kiểu ẩn, như `clear()`, `remove(Object)` và `remove(int)`, vẫn hợp lệ ở thời điểm biên dịch:

```java
List<String> names = new ArrayList<>(List.of("Lan", "Minh"));
List<?> view = names;
view.remove("Lan");
System.out.println(names); // [Minh]
```

Hai biến cùng trỏ một `ArrayList`; wildcard không tự tạo bản sao. **Hợp lệ về kiểu không đồng nghĩa chạy được:** `List.of(...)` không hỗ trợ sửa đổi và có thể ném `UnsupportedOperationException`. Nếu caller thực sự không được phép thay đổi dữ liệu, phải cấp một unmodifiable view/snapshot thích hợp thay vì chỉ dùng wildcard.

## 7. Minh họa Java 17 hoàn chỉnh

Tạo tệp `UnknownWildcardDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class UnknownWildcardDemo {
    static void printAll(List<?> values) {
        for (Object value : values) {
            System.out.println("Value: " + value);
        }
    }

    static void swapFirstTwo(List<?> values) {
        swapCaptured(values);
    }

    private static <T> void swapCaptured(List<T> values) {
        if (values.size() < 2) {
            return;
        }
        T first = values.get(0);
        values.set(0, values.get(1));
        values.set(1, first);
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("Lan", "Minh"));
        List<Integer> numbers = new ArrayList<>(List.of(3, 5));

        printAll(names);
        printAll(numbers);

        List<?> unknown = names;
        Object first = unknown.get(0);
        System.out.println("First: " + first);

        swapFirstTwo(unknown);
        System.out.println("After swap: " + names);

        unknown.add(null);
        System.out.println("After null: " + names);

        Object payload = numbers;
        if (payload instanceof List<?> found) {
            System.out.println("Size: " + found.size());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac UnknownWildcardDemo.java
java UnknownWildcardDemo
```

**Stdout chính xác:**

```text
Value: Lan
Value: Minh
Value: 3
Value: 5
First: Lan
After swap: [Minh, Lan]
After null: [Minh, Lan, null]
Size: 2
```

Giải thích cơ chế:

1. `printAll` chỉ đọc phần tử qua kiểu `Object`, nên có thể nhận danh sách chứa `String` hoặc `Integer`.
2. `unknown` trỏ cùng đối tượng với `names`: kết quả hoán đổi và thêm null qua `unknown` đều thấy được khi in `names`.
3. `swapFirstTwo` giữ nguyên khai báo `List<?>` cho bên gọi, rồi dùng `swapCaptured` để compiler gán một tên kiểu `T` nhất quán cho đối số kiểu ẩn; cách làm này **không đưa kiểu phần tử lạ vào danh sách**.
4. `instanceof List<?>` hợp lệ vì đây là dạng kiểm tra được ở runtime rằng `payload` là một `List`; điều kiện ấy **không xác định** kiểu phần tử cụ thể.

Ví dụ dùng `ArrayList` vì danh sách hỗ trợ `set` và `add(null)`. Nếu thay `names` bằng `List.of("Lan", "Minh")`, mã vẫn thỏa nhiều điều kiện kiểu tĩnh nhưng `set` hoặc `add` có thể ném `UnsupportedOperationException`.

## 8. Wildcard capture: khi cần làm việc với kiểu ẩn

Trong một biểu thức generic, compiler có thể gán tạm một ký hiệu nội bộ như `CAP#1` để biểu diễn kiểu mà dấu `?` đang che giấu. Đây là *wildcard capture*. Một số thao tác lấy một phần tử rồi đặt trở lại danh sách có thể khó kiểm tra nếu mã trực tiếp làm việc với `List<?>`:

```java
static void putFirstBack(List<?> items) {
    // items.set(0, items.get(0)); // Có thể báo lỗi capture type
}
```

Dùng helper có type parameter rõ ràng:

```java
static void putFirstBack(List<?> items) {
    putFirstBackCaptured(items);
}

private static <T> void putFirstBackCaptured(List<T> items) {
    if (!items.isEmpty()) {
        T first = items.get(0);
        items.set(0, first);
    }
}
```

Vì `items.get(0)` và `items.set(0, first)` đều dùng cùng `T`, compiler chứng minh được giá trị lấy ra sẽ hợp với vị trí ghi vào. Lưu ý: helper có thể thay đổi dữ liệu nhưng chỉ đặt lại phần tử thuộc **cùng kiểu `T`**; helper không cấp quyền thêm một giá trị tùy ý như `Integer` vào `List<String>`. Cả hai đoạn trên là **đoạn minh họa phương thức**, không phải hai phương thức cùng được đặt trong một lớp; để chạy hãy chọn phiên bản có helper.

### Hai dấu hỏi không mặc nhiên chỉ cùng một kiểu

```java
static void inspectPair(List<?> left, List<?> right) {
    Object first = left.get(0);
    Object second = right.get(0);
    System.out.println(first + " / " + second);

    // right.set(0, left.get(0)); // Không đảm bảo an toàn: hai ? có thể khác kiểu
}
```

`left` có thể là `List<String>`, còn `right` có thể là `List<Integer>`. Dù cả hai cùng mang kiểu `List<?>`, ta **không có bằng chứng** hai tham số kiểu ẩn giống nhau.

Nếu cần ràng buộc hai danh sách có cùng một kiểu phần tử, dùng một tham số kiểu được đặt tên:

```java
static <T> void copyFirst(List<? extends T> source, List<? super T> target) {
    if (!source.isEmpty() && !target.isEmpty()) {
        target.set(0, source.get(0));
    }
}
```

Ví dụ `List<String>` là nguồn và `List<Object>` là đích sẽ hợp lệ. Ràng buộc `extends/super` ở đây nằm ngoài wildcard không giới hạn nhưng minh họa vì sao một ký hiệu `T` chung cho phép compiler kiểm soát quan hệ giữa nhiều vị trí.

## 9. `?` có mặt ở đâu, và không phải là gì?

Dấu hỏi xuất hiện ở vị trí **đối số kiểu** của một kiểu generic khi không cần biết đối số cụ thể:

```java
List<?> unknownElements;
Set<?> unknownMembers;
Map<?, ?> unknownKeysAndValues;
Class<?> unknownClass;
```

Ví dụ `Map<?, ?>` không biết kiểu khóa và kiểu giá trị; có thể đọc qua `Object` và gọi các phương thức tra cứu chung nhưng không thể tự ý `put` cặp khóa/giá trị khác null vì các kiểu ẩn chưa xác định. `Class<?>` biểu diễn một đối tượng `Class` cho kiểu nào đó chưa biết, hữu ích cho các API phản chiếu chỉ cần làm việc với đối tượng `Class`.

Ngược lại, các cách viết sau **không phải khai báo Java hợp lệ**:

```java
// class Box<?> { }             // Không được dùng ? để khai báo tham số kiểu
// List<?> list = new ArrayList<?>(); // Không được dùng wildcard khi tạo đối tượng
// <?> void process() { }        // Không được dùng ? để khai báo tham số kiểu phương thức
```

Khi định nghĩa lớp hoặc phương thức generic, cần **tham số kiểu có tên** như `T` hoặc `E`. Khi tạo đối tượng, hãy dùng kiểu thực hoặc diamond `<>` để compiler suy luận:

```java
List<?> view = new ArrayList<String>(); // Hợp lệ
List<?> inferred = new ArrayList<>();    // Hợp lệ; compiler suy luận kiểu đối số
```

Một điểm nữa là generics trong Java dùng *type erasure*: runtime không lưu lại `String` hay `Integer` như một kiểm tra phần tử trong đối tượng `ArrayList`. Vì vậy:

```java
Object input = List.of("x");
boolean isList = input instanceof List<?>; // Hợp lệ: kiểm tra là List
// boolean isStrings = input instanceof List<String>; // Không hợp lệ
```

`instanceof List<?>` không chứng minh các phần tử đều cùng kiểu cụ thể nào. Nó chỉ xác nhận đối tượng mang kiểu `List` ở runtime; tham số kiểu vẫn là ẩn số.

## 10. Khi nào nên viết `List<?>`, khi nào dùng `<T>`?

Dùng `List<?>` khi điều bạn muốn nói thật sự là **“kiểu phần tử không liên quan tới việc tôi đang làm”**:

```java
static boolean hasAnyElement(List<?> values) {
    return !values.isEmpty();
}

static void describe(List<?> values) {
    System.out.println("Count = " + values.size());
}
```

Dùng một type parameter có tên `<T>` khi cần **truyền thông tin kiểu** giữa tham số và kết quả, hoặc giữa nhiều tham số:

```java
static <T> T firstOrNull(List<T> values) {
    return values.isEmpty() ? null : values.get(0);
}
```

Với `List<String>`, lời gọi `firstOrNull` trả về kiểu tĩnh `String`; với `List<Integer>`, kết quả là kiểu tĩnh `Integer`. Nếu đổi tham số thành `List<?>` và kết quả thành `Object`, API sẽ làm mất mối liên hệ kiểu có ích ấy.

Nhìn chung, nếu bên gọi cần truyền vào một danh sách bất kỳ để API chỉ đọc/đếm/in, `List<?>` là hợp đồng gọn và an toàn. Nếu thuật toán cần nhận và trả lại **cùng kiểu phần tử**, `<T>` thể hiện mối quan hệ rõ hơn. Nếu cần **thêm giá trị thuộc một họ kiểu xác định**, wildcard có giới hạn như `? super T` có thể phù hợp hơn; đó là một yêu cầu khác với “không biết kiểu nào”.

## 11. Lỗi hiểu sai thường gặp

| Quan niệm | Điều đúng |
|---|---|
| “`?` nghĩa là `Object`.” | `?` là tham số kiểu chưa biết; `Object` là một kiểu xác định. |
| “`List<?>` chứa được mọi thứ.” | Nó có thể **tham chiếu** nhiều loại `List<T>`, nhưng không thể thêm tùy tiện vào một danh sách được tham chiếu. |
| “Không thêm được nên `List<?>` là immutable.” | Vẫn có thể `clear`, `remove`, `add(null)` khi implementation hỗ trợ. |
| “Hai biến `List<?>` chắc chắn có cùng kiểu phần tử.” | Mỗi dấu hỏi có thể biểu diễn một kiểu ẩn khác nhau. |
| “`instanceof List<?>` chứng minh kiểu phần tử.” | Nó chỉ kiểm tra đối tượng là `List` ở runtime. |
| “Muốn đọc `String` thì ép `List<?>` thành `List<String>`.” | Ép kiểu không tự kiểm tra từng phần tử; hãy dùng bằng chứng kiểu ở API hoặc kiểm tra từng đối tượng khi cần. |
| “Dùng raw `List` cũng an toàn như wildcard.” | Raw type bỏ mất kiểm tra tham số kiểu và có nguy cơ gây heap pollution. |

## 12. Tự kiểm tra và đáp án

Cho đoạn mã:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20));
List<?> unknown = numbers;
```

Hãy tự trả lời trước khi xem đáp án:

1. `unknown.get(0)` có thể gán thẳng cho biến `Integer` hay chỉ có thể gán an toàn cho `Object`?
2. `unknown.add(30)` có biên dịch không, dù danh sách thật là `List<Integer>`?
3. `unknown.add(null)` có biên dịch không, và ở đây có làm thay đổi `numbers` không?
4. `unknown.remove(0)` có biên dịch không? Trong `List`, overload nào được chọn khi truyền số nguyên `0`?
5. Có thể thay tham số của `printAll(List<?>)` bằng `List<Object>` mà vẫn truyền `List<Integer>` vào được không?

**Đáp án:** (1) Chỉ gán an toàn cho `Object` vì compiler chỉ biết `?`; (2) Không, vì kiểu tĩnh không cho phép ghi `Integer` vào kiểu ẩn; (3) Có, và `ArrayList` chấp nhận null nên `numbers` nhận thêm phần tử null; (4) Có, chọn `remove(int index)` và xóa phần tử ở chỉ số 0 nếu có, không phải xóa đối tượng `Integer.valueOf(0)`; (5) Không, bởi `List<Integer>` không phải `List<Object>`.

## 13. Ghi nhớ bản chất của unbounded wildcard

**`?` nghĩa là “một kiểu chưa biết”, không phải “một kiểu tùy tiện được phép ghi”.** `List<?>` cho phép mã làm việc với một danh sách mà không cần biết đối số kiểu của nó, miễn thao tác không đòi hỏi việc tự ý cung cấp phần tử thuộc kiểu ẩn. Đọc phần tử dưới `Object` là an toàn; thêm một giá trị `String` hay `Integer` ngẫu nhiên thì không. Khi cần liên kết cùng kiểu ẩn trong nhiều thao tác, hãy dùng wildcard capture qua helper có tham số `<T>`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unbounded-wildcard-002">List&lt;?&gt; khác với danh sách chứa Object</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `List<?>` khác với `List<Object>` trong Java Generics?

Hai khai báo sau trông giống nhau vì mọi đối tượng Java đều có thể được xem như `Object`, nhưng chúng diễn đạt **hai hợp đồng kiểu hoàn toàn khác nhau**:

~~~java
List<?> unknownElements;
List<Object> objectElements;
~~~

- **`List<?>`**: một danh sách có **một kiểu phần tử cụ thể nào đó nhưng chưa biết kiểu ấy là gì**. Nó có thể là `List<String>`, `List<Integer>`, `List<Object>` hoặc một kiểu tham số hóa khác. Ta có thể đọc phần tử dưới dạng `Object`, nhưng không được tự ý thêm phần tử không phải `null` vì không biết danh sách thật chấp nhận kiểu gì.
- **`List<Object>`**: một danh sách có **đối số kiểu chính xác là `Object`**. Ta có thể thêm `String`, `Integer`, `Boolean` và bất kỳ giá trị tham chiếu nào khác vì tất cả đều gán được cho `Object`. Tuy nhiên, không thể gán trực tiếp `List<String>` hoặc `List<Integer>` vào biến `List<Object>`.

Điểm phân biệt cốt lõi: **“đọc được một phần tử như `Object`” không đồng nghĩa “được phép ghi mọi `Object` vào danh sách”.** Kiểu ở phía đọc và hợp đồng ghi là hai vấn đề khác nhau.

## 1. Hiểu chính xác dấu `?` và tên `Object`

Trong `List<?>`, dấu `?` là **unbounded wildcard**: compiler biết đây là một `List` có tham số kiểu, nhưng không biết tham số đó là kiểu nào. Ví dụ:

~~~java
List<String> names = new ArrayList<>();
List<Integer> ages = new ArrayList<>();

List<?> first = names; // Hợp lệ.
List<?> second = ages; // Hợp lệ.
~~~

Mỗi danh sách vẫn giữ đối số kiểu ban đầu. Wildcard **không chuyển** `List<String>` thành `List<Object>` và không thay đổi dữ liệu của collection. Một biến `List<?>` chỉ cung cấp **góc nhìn có khả năng sử dụng hạn chế** lên collection.

Trong `List<Object>`, `Object` là một **đối số kiểu xác định**, giống như `String` hoặc `Integer` cũng là các đối số kiểu xác định. Vì generic class/interface thông thường trong Java là **invariant** theo đối số kiểu, phép gán sau bị từ chối:

~~~java
List<String> names = new ArrayList<>();
List<Object> objects = names; // LỖI BIÊN DỊCH.
~~~

Đây là ví dụ **cố ý không biên dịch**. Lý do: nếu phép gán được phép, ta có thể gọi `objects.add(123)`, từ đó đưa `Integer` vào danh sách đang hứa chỉ chứa `String`.

Câu “mọi `String` đều là `Object`” vẫn đúng; điều không đúng là suy ra “mọi `List<String>` đều là `List<Object>`”.

## 2. So sánh trực tiếp về phép gán, đọc và ghi

| Đặc điểm | `List<?>` | `List<Object>` |
| --- | --- | --- |
| Ý nghĩa | Danh sách có **kiểu phần tử chưa biết** | Danh sách có kiểu phần tử **chính xác là `Object`** |
| Nhận `List<String>` | Có | Không |
| Nhận `List<Integer>` | Có | Không |
| Nhận `List<Object>` | Có | Có |
| Kiểu tĩnh của `get(index)` khi gán thông thường | `Object` | `Object` |
| `add("Java")` | Không biên dịch | Biên dịch |
| `add(123)` | Không biên dịch | Biên dịch (autoboxing thành `Integer`) |
| `add(new Object())` | Không biên dịch | Biên dịch |
| `add(null)` | Biên dịch; collection thật có thể từ chối lúc chạy | Biên dịch; collection thật có thể từ chối lúc chạy |
| `size()`, `isEmpty()`, `contains(Object)` | Dùng được | Dùng được |
| `clear()`, `remove(index)` | Biên dịch; khả năng thực thi tùy collection | Biên dịch; khả năng thực thi tùy collection |
| Dùng làm tham số nhận danh sách bất kỳ | Thường phù hợp | Chỉ nhận danh sách có đối số kiểu `Object` |

Bảng so sánh nói về **kiểm tra kiểu tại thời điểm biên dịch**. Dù một lời gọi `add` hay `clear` biên dịch, danh sách thật có thể không cho sửa (ví dụ `List.of(...)`) và ném `UnsupportedOperationException` lúc chạy. Một số collection cũng không cho chứa `null`.

### Vì sao cả hai đều trả `Object` khi đọc?

`Object` là một supertype của tất cả các kiểu tham chiếu Java. Với `List<?>`, phần tử thực có thể là `String`, `Integer` hay bất kỳ kiểu nào khác nên compiler chỉ bảo đảm mức chung là `Object`. Với `List<Object>`, `Object` chính là kiểu phần tử được khai báo.

~~~java
List<String> names = new ArrayList<>(List.of("An"));
List<?> unknown = names;

Object a = unknown.get(0); // Hợp lệ.
String b = names.get(0);   // Hợp lệ nhờ tham chiếu gốc List<String>.

// String c = unknown.get(0); // LỖI: chỉ bảo đảm Object.
~~~

Giá trị vật lý vẫn là cùng đối tượng `String`; chỉ **kiểu tĩnh mà compiler cam kết** khác nhau. Có thể kiểm tra `instanceof` hoặc ép kiểu có chủ đích, nhưng khi ấy phải xử lý trường hợp kiểu thực không phù hợp.

## 3. Chương trình chạy được: một API nhận mọi danh sách, một danh sách chứa nhiều loại đối tượng

Tạo tệp **`UnknownVsObjectDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class UnknownVsObjectDemo {
    private static void printItems(List<?> items) {
        for (Object item : items) {
            System.out.println("item: " + item);
        }
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("Ada", "Lin"));
        List<Integer> ids = new ArrayList<>(List.of(7, 9));

        List<?> unknown = names;
        List<Object> objects = new ArrayList<>();

        objects.add("Ada");
        objects.add(7);
        objects.add(true);

        System.out.println("first = " + unknown.get(0));
        System.out.println("objects = " + objects);

        printItems(names);
        printItems(ids);

        unknown = ids;
        System.out.println("new first = " + unknown.get(0));
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên**:

~~~bash
javac UnknownVsObjectDemo.java
java UnknownVsObjectDemo
~~~

Stdout chính xác:

~~~text
first = Ada
objects = [Ada, 7, true]
item: Ada
item: Lin
item: 7
item: 9
new first = 7
~~~

Phân tích kết quả:

1. `unknown` ban đầu nhìn vào `List<String>`. Khi gọi `get(0)`, kiểu tĩnh là `Object` nhưng đối tượng thực là chuỗi `"Ada"`.
2. `objects` thực sự là `List<Object>`. Nó được phép chứa đồng thời một chuỗi, một `Integer` và một `Boolean`.
3. `printItems(List<?>)` nhận cả `List<String>` và `List<Integer>` mà không cần raw type hay unchecked cast. Vì chỉ đọc và in phần tử như `Object` nên nó không cần biết đối số kiểu cụ thể.
4. Sau khi gán `unknown = ids`, biến wildcard nhìn vào danh sách số nguyên. Thao tác này hợp lệ vì `List<?>` có thể tham chiếu nhiều kiểu danh sách khác nhau **ở các thời điểm khác nhau**.

Lưu ý: mỗi đối tượng collection vẫn có một kiểu phần tử phù hợp với tham chiếu gốc trong chương trình. Việc đổi biến `unknown` sang danh sách khác **không biến một danh sách thành loại danh sách khác**.

## 4. Vì sao `List<?>` không thêm được `Object`?

Xét:

~~~java
List<String> words = new ArrayList<>(List.of("one", "two"));
List<?> unknown = words;

Object newItem = Integer.valueOf(3);

// unknown.add(newItem); // LỖI BIÊN DỊCH.
// unknown.add("three"); // CŨNG LỖI BIÊN DỊCH.
~~~

Compiler nhìn thấy `List<?>` và hiểu rằng wildcard đại diện cho **một kiểu cụ thể chưa biết**. Có thể kiểu ấy là `String`, như ở ví dụ; cũng có thể là `Integer`. Nếu nhận bất kỳ `Object` nào, Java sẽ vô tình cho phép đưa `Integer` vào `List<String>`. Nếu chỉ cho thêm `String`, Java lại có thể làm sai trong trường hợp biến đang trỏ vào `List<Integer>`.

Do đó, **biến `List<?>` không cho phép thêm một giá trị có kiểu tham chiếu cụ thể**. Điều này khác hẳn `List<Object>`:

~~~java
List<Object> objects = new ArrayList<>();
objects.add("three");
objects.add(Integer.valueOf(3));
objects.add(new Object()); // Cả ba lời gọi đều hợp lệ.
~~~

### Trường hợp đặc biệt: `null`

~~~java
List<String> words = new ArrayList<>();
List<?> unknown = words;

unknown.add(null); // Biên dịch được.
System.out.println(words); // [null]
~~~

`null` là giá trị tương thích với mọi kiểu tham chiếu, nên compiler cho phép thêm qua wildcard. Tuy nhiên, điều này **không có nghĩa mọi collection chấp nhận `null`**: ví dụ danh sách từ `List.of(...)` không cho thêm phần tử, còn nhiều collection cấm `null`. Mã có thể biên dịch mà vẫn ném ngoại lệ khi chạy.

Không nên lấy khả năng `add(null)` làm lý do dùng `List<?>` để chứa dữ liệu. Ý nghĩa chính của wildcard là không giả định sai về kiểu phần tử.

## 5. `List<?>` không phải `List<Object>`, cũng không phải raw `List`

Ba dạng khai báo dễ nhầm:

~~~java
List<?> unknown;        // Có generic; kiểu phần tử tồn tại nhưng không xác định.
List<Object> objects;   // Có generic; đối số kiểu được xác định là Object.
List raw;               // Raw type; bỏ qua tham số generic trong khai báo.
~~~

Với `List<?>`, compiler giữ được thông tin “đây là một danh sách với một kiểu phần tử nhất quán nhưng chưa biết”. Vì vậy, nó ngăn một lời gọi `add(42)` không an toàn.

Với raw `List`, một số phép ghi sẽ **biên dịch kèm cảnh báo unchecked**, có thể gây lỗi lúc đọc:

~~~java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(42); // Cảnh báo unchecked: có thể làm nhiễm kiểu List<String>.
~~~

Đây là **đoạn mã minh họa nguy cơ**, không phải thiết kế được khuyến nghị. Trường hợp `names.get(0)` được nhận vào biến `String`, runtime có thể ném `ClassCastException` vì phần tử thực là `Integer`. Dùng `List<?>` để khai báo tham số chỉ đọc là cách an toàn hơn nhiều so với raw `List`.

Không nên coi `List<?>` là “`List<Object>` viết gọn”; hai kiểu này có quyền ghi khác nhau. Cũng không nên coi wildcard là bỏ kiểm tra kiểu; mục tiêu của nó là **giữ kiểm tra kiểu mà không cần biết một đối số kiểu cụ thể**.

## 6. Khác biệt về thao tác ghi không đồng nghĩa với immutability

`List<?>` hạn chế kiểu của **giá trị mới** đưa vào danh sách; nó không phải một giao diện bất biến. Chẳng hạn, nếu backing list sửa được, `remove(int)`, `remove(Object)` và `clear()` vẫn có thể thay đổi nội dung qua alias wildcard. Trong khi đó, `List<Object>` còn được phép `add` một tham chiếu thuộc kiểu Object.

```java
List<String> words = new ArrayList<>(List.of("A", "B"));
List<?> unknown = words;
Object removed = unknown.remove(0);
System.out.println(removed + " -> " + words); // A -> [B]
```

Điểm khác giữa hai hợp đồng vẫn là **đối số kiểu chưa biết** so với **đối số kiểu Object chính xác**. Việc lời gọi `remove` biên dịch không bảo đảm luôn thành công: `List.of` không cho thay đổi, `Arrays.asList` có kích thước cố định, còn `ArrayList` hỗ trợ xóa. Đó là tính chất của implementation, không phải hệ quả của wildcard.

## 7. Wildcard capture: tại sao vẫn có thể hoán đổi hai phần tử trong `List<?>`?

Thoạt nhìn, nếu không biết kiểu `?`, ta không thể gọi `set(index, newValue)` với một `Object` bất kỳ. Nhưng ta có thể nhờ **một phương thức generic nắm bắt kiểu ẩn** và thao tác với những phần tử **đã lấy ra từ chính danh sách đó**.

Tạo tệp **`WildcardCaptureDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class WildcardCaptureDemo {
    static void swapFirstTwo(List<?> values) {
        if (values.size() < 2) {
            return;
        }
        swapCaptured(values, 0, 1);
    }

    private static <T> void swapCaptured(List<T> values, int i, int j) {
        T first = values.get(i);
        values.set(i, values.get(j));
        values.set(j, first);
    }

    public static void main(String[] args) {
        List<String> letters =
                new ArrayList<>(List.of("A", "B", "C"));
        List<Integer> numbers =
                new ArrayList<>(List.of(10, 20));

        swapFirstTwo(letters);
        swapFirstTwo(numbers);

        System.out.println(letters);
        System.out.println(numbers);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac WildcardCaptureDemo.java
java WildcardCaptureDemo
~~~

Stdout:

~~~text
[B, A, C]
[20, 10]
~~~

Giải thích từng bước:

1. `swapFirstTwo` nhận `List<?>` nên có thể được gọi với `List<String>` và `List<Integer>`.
2. Khi chuyển `values` tới `swapCaptured`, compiler dùng **capture conversion** để xem wildcard như một kiểu nhất quán tạm thời, ký hiệu ở phương thức đích là `T`.
3. Cả phần tử đọc ra và vị trí ghi trở lại đều có kiểu `T`. Vì vậy, thao tác hoán đổi **không tạo phần tử có kiểu khác** so với collection ban đầu.
4. Không có cast từ `Object` sang `String`, không có raw type, không cần `@SuppressWarnings("unchecked")`.

Thư viện chuẩn có sẵn `Collections.swap(List<?>, int, int)` với ý tưởng tương tự. Ví dụ này giúp thấy rõ: wildcard **không cấm mọi phép biến đổi**, mà cấm các phép ghi không chứng minh được an toàn kiểu.

## 8. API nên nhận `List<?>` khi nào, nên nhận `List<Object>` khi nào?

### 8.1. Chỉ cần xử lý phần tử như `Object`: chọn `List<?>`

Ví dụ phương thức đếm, duyệt/in, kiểm tra rỗng hoặc đăng log mà không cần biết kiểu phần tử:

~~~java
static void describe(List<?> items) {
    System.out.println("Count: " + items.size());
    for (Object item : items) {
        System.out.println(item);
    }
}
~~~

Đây là **phương thức minh họa**, cần đặt trong một class và import `java.util.List`. Nó nhận `List<String>`, `List<Integer>`, `List<Object>` và các kiểu danh sách khác.

Nếu thay tham số bằng `List<Object>`, caller truyền `List<String>` sẽ gặp lỗi compile-time dù thân phương thức chỉ đọc. Hợp đồng `List<Object>` đã **vô tình yêu cầu cụ thể hơn nhu cầu thật của API**.

### 8.2. Cần thêm những giá trị `Object` bất kỳ: chọn `List<Object>`

~~~java
static void appendMixedValues(List<Object> destination) {
    destination.add("created");
    destination.add(2026);
    destination.add(Boolean.TRUE);
}
~~~

Đây cũng là **phương thức cần đặt trong class**. Nó có quyền thêm giá trị thuộc nhiều loại khác nhau vì caller phải cung cấp chính xác một `List<Object>`:

~~~java
List<Object> destination = new ArrayList<>();
appendMixedValues(destination); // Hợp lệ.

// List<String> names = new ArrayList<>();
// appendMixedValues(names); // LỖI BIÊN DỊCH.
~~~

Nếu thay bằng `List<?>`, thân phương thức sẽ không được gọi `add("created")` vì không biết loại danh sách mà caller truyền vào.

### 8.3. Cần lưu mọi phần tử từ danh sách cụ thể vào một `List<Object>` mới

Do generic invariance, đoạn sau không hợp lệ:

~~~java
List<String> words = new ArrayList<>(List.of("A", "B"));
// List<Object> direct = words; // LỖI BIÊN DỊCH.
~~~

Nhưng bạn có thể **sao chép**:

~~~java
List<String> words = new ArrayList<>(List.of("A", "B"));
List<Object> copied = new ArrayList<>(words);

copied.add(42);               // Hợp lệ với danh sách mới.
System.out.println(words);    // [A, B]
System.out.println(copied);   // [A, B, 42]
~~~

Đây là **đoạn mã để đặt trong class**, có import `java.util.List` và `java.util.ArrayList`. Constructor `ArrayList` nhận `Collection<? extends E>`; với đích có `E = Object`, một `Collection<String>` cung cấp phần tử hợp lệ. Hai collection riêng biệt có thể chia sẻ **các đối tượng phần tử** nhưng không chia sẻ cấu trúc danh sách; thêm phần tử vào `copied` không thêm vào `words`.

Nếu cần “xem” bất kỳ danh sách nào thì dùng `List<?>`. Nếu thật sự cần **chứa trộn các loại đối tượng** dưới một kiểu phần tử rộng, tạo `List<Object>` rõ ràng.

## 9. Liên hệ với `? extends Object` và các generic khác

Giới hạn mặc định của unbounded wildcard là `Object` (với các kiểu tham chiếu). Trong những ngữ cảnh thông thường, `List<?>` mang ý nghĩa tương đương `List<? extends Object>`. Nhưng chúng **không tương đương `List<Object>`**.

~~~java
List<String> words = new ArrayList<>();

List<?> first = words;                 // Hợp lệ.
List<? extends Object> second = words; // Hợp lệ.
// List<Object> third = words;         // LỖI.
~~~

Sự khác nhau đến từ **wildcard cho phép nhiều đối số kiểu cụ thể** còn `Object` là một **đối số kiểu chính xác**.

Quy tắc cũng xuất hiện ở các generic khác:

~~~java
Set<String> names = new HashSet<>();
Set<?> unknownSet = names; // Hợp lệ.
// Set<Object> objectSet = names; // LỖI.

Map<String, Integer> scores = new HashMap<>();
Map<?, ?> unknownMap = scores; // Hợp lệ.
// Map<Object, Object> objectMap = scores; // LỖI.
~~~

Đây là **mã minh họa cần import `java.util.*` và đặt trong class**. `Map<?, ?>` cho phép duyệt entry dưới góc nhìn `Object` cho cả key và value, nhưng không cho tự ý thêm một cặp key/value mới với kiểu không xác định (trừ các trường hợp đặc biệt liên quan `null`). Quy tắc an toàn kiểu là cùng một cơ chế.

### Không thể overload chỉ bằng `List<?>` và `List<Object>`

Mặc dù hai kiểu có ý nghĩa khác nhau ở thời điểm biên dịch, ta **không được** viết đồng thời hai overload sau trong cùng class:

~~~java
void handle(List<?> values) {}
void handle(List<Object> values) {} // LỖI: name clash do same erasure.
~~~

Đây là **ví dụ cố ý không biên dịch**. Cả hai chữ ký đều bị xóa đối số generic thành cùng dạng `handle(List)` khi áp dụng **type erasure**. Như vậy, khác biệt trong khả năng gán/ghi của chúng là thật ở nguồn Java, nhưng không đủ để định danh hai overload ở bytecode.

### Kiểm tra kiểu với `instanceof`

`List<?>` là dạng dùng được trong phép kiểm tra `instanceof`, còn kiểm tra đối số `Object` cụ thể thì không:

~~~java
Object value = List.of("x", "y");

if (value instanceof List<?> items) {
    System.out.println(items.size());
}

// if (value instanceof List<Object>) { ... } // LỖI BIÊN DỊCH.
~~~

Đây là **mã minh họa cần import `java.util.List` và đặt trong class**. Runtime chỉ biết chắc đối tượng là `List`, không thể khẳng định đối số kiểu generic cụ thể của danh sách là `Object`.

## 10. Lỗi thường gặp và cách nhận diện

**Lỗi 1 — Cho rằng `List<?>` là viết tắt của `List<Object>`.** Sai: `List<?>` chấp nhận `List<String>`; `List<Object>` thì không. Phép gán và khả năng ghi khác nhau.

**Lỗi 2 — Dùng `List<Object>` làm tham số chỉ để in mọi danh sách.** Phương thức sẽ từ chối `List<String>` và `List<Integer>` vô ích. Nếu chỉ đọc như `Object`, dùng `List<?>`.

**Lỗi 3 — Nghĩ rằng vì `get()` trả `Object` nên `add(new Object())` phải hợp lệ.** Không đúng: đọc kiểu rộng không chứng minh danh sách thật chấp nhận thêm một `Object` bất kỳ.

**Lỗi 4 — Nghĩ `List<?>` là immutable.** Sai: các thao tác xóa, làm rỗng và hoán đổi qua generic helper có thể hợp lệ. Khả năng sửa phụ thuộc đối tượng collection thật.

**Lỗi 5 — Dùng raw `List` để “giải quyết” lỗi compiler.** Raw type có thể cho thêm sai phần tử với cảnh báo unchecked rồi gây `ClassCastException` về sau. Wildcard và tham số generic đúng mục đích an toàn hơn.

**Lỗi 6 — Ép kiểu `List<?>` thành `List<Object>` để gọi `add`.** Kiểu thực có thể là `List<String>`; unchecked cast có thể gây heap pollution. Không được xem cast là cách nâng cấp khả năng ghi hợp lệ.

**Lỗi 7 — Nhầm việc sao chép với gán tham chiếu.** `new ArrayList<Object>(aStringList)` tạo một collection mới, có thể thêm `Object` tùy ý. `List<Object> objects = aStringList` là phép gán cùng đối tượng nhưng sai kiểu, nên bị cấm.

## 11. Tự kiểm tra

Cho:

~~~java
List<String> names = new ArrayList<>(List.of("A", "B"));
List<?> unknown = names;
List<Object> objects = new ArrayList<>();
~~~

Xác định từng câu đúng/sai về **khả năng biên dịch**:

1. `Object x = unknown.get(0);`
2. `String s = unknown.get(0);`
3. `unknown.add("C");`
4. `unknown.add(null);`
5. `objects.add("C");`
6. `List<Object> direct = names;`
7. `List<?> other = objects;`
8. `objects.add(unknown.get(0));`
9. `unknown.clear();`
10. `List<Object> copy = new ArrayList<>(names);`

**Đáp án:**

| Câu | Biên dịch? | Giải thích |
| --- | --- | --- |
| 1 | Có | `get` từ wildcard có thể dùng như `Object` |
| 2 | Không | Kiểu tĩnh của `get` không bảo đảm là `String` |
| 3 | Không | Không biết kiểu phần tử ẩn phía sau wildcard |
| 4 | Có | `null` tương thích với mọi kiểu tham chiếu; runtime tùy collection |
| 5 | Có | `String` là subtype của `Object` |
| 6 | Không | Generic invariant; `List<String>` không phải `List<Object>` |
| 7 | Có | `List<?>` có thể nhận `List<Object>` |
| 8 | Có | Phần tử `unknown.get(0)` được dùng như `Object`, phù hợp đích |
| 9 | Có | `clear()` không đòi biết kiểu phần tử; runtime tùy collection |
| 10 | Có | Sao chép phần tử vào `ArrayList<Object>` mới |

Các biểu thức không biên dịch trong bài tập chỉ để phân tích, không đưa nguyên vào chương trình chạy.

## 12. Kết luận

**`List<?>` biểu thị “một danh sách thuộc một kiểu phần tử nào đó”**, còn **`List<Object>` biểu thị “một danh sách có đối số kiểu chính xác `Object`”**. Cả hai đều cho đọc phần tử dưới kiểu `Object` nhưng chỉ `List<Object>` cho phép ghi các giá trị `Object` bất kỳ. Unbounded wildcard chấp nhận nhiều danh sách khác nhau mà vẫn chặn những phép ghi không an toàn. Khi thiết kế API chỉ cần quan sát phần tử, `List<?>` thường là chữ ký chính xác; khi thật sự cần bổ sung nhiều loại đối tượng vào danh sách, dùng `List<Object>` hoặc tạo danh sách mới với đối số kiểu phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unbounded-wildcard-003">Đọc giá trị từ wildcard không giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc giá trị từ wildcard không giới hạn (`?`) trong Java Generics

Trong Java Generics, khai báo `List<?>` có nghĩa là **một danh sách có kiểu phần tử cụ thể nhưng chưa được biết tại vị trí sử dụng**. Danh sách ấy có thể thực sự là `List<String>`, `List<Integer>`, `List<Customer>` hoặc một kiểu tham số hóa khác. Java cho phép đọc phần tử từ `List<?>`, nhưng kiểu tĩnh an toàn chung nhất mà chương trình nhận được khi đọc là **`Object`**.

Quy tắc nền tảng:

> Từ `List<?>`, có thể đọc phần tử dưới dạng `Object`; không được tự giả định phần tử là `String`, `Integer` hay bất kỳ kiểu cụ thể nào. Muốn dùng API riêng của kiểu phần tử, cần kiểm tra kiểu lúc chạy hoặc dùng chữ ký generic mang thông tin kiểu tốt hơn.

Bài này tập trung vào thao tác **đọc** từ wildcard không giới hạn, cách hiểu kiểu kết quả, vòng lặp, thao tác lấy dữ liệu an toàn, những giới hạn thường gặp và cách thiết kế phương thức nhận `?`.

## 1. Ý nghĩa chính xác của `List<?>`

Xét hai danh sách:

```java
List<String> names = List.of("An", "Binh");
List<Integer> ages = List.of(20, 21);
```

Cả hai đều có thể được tham chiếu qua `List<?>`:

```java
List<?> first = names; // Hợp lệ.
List<?> second = ages; // Hợp lệ.
```

Dấu `?` là **wildcard không giới hạn** (*unbounded wildcard*). Nó biểu diễn một đối số kiểu chưa xác định. `List<?>` không phải một danh sách “không có kiểu” và cũng không phải một danh sách có kiểu phần tử là `Object`; mỗi đối tượng danh sách vẫn tuân theo kiểu phần tử trong khai báo của nó.

So sánh:

| Khai báo | Có nhận trực tiếp `List<String>`? | Kiểu đọc từ `get` | Ý nghĩa |
| --- | --- | --- | --- |
| `List<String>` | Có | `String` | Biết rõ phần tử là `String` |
| `List<Object>` | Không | `Object` | Danh sách được khai báo chứa `Object`, có thể thêm các kiểu tham chiếu vào |
| `List<?>` | Có | `Object` | Có một kiểu phần tử chưa biết; an toàn nhất là đọc dưới `Object` |
| Raw `List` | Có thể dùng nhưng mất kiểm tra generic | `Object` | Bỏ qua thông tin kiểm tra kiểu; dễ sinh cảnh báo `unchecked` |

Điểm dễ nhầm: mặc dù `List<Object>` và `List<?>` đều cho phép lấy giá trị vào biến `Object`, chúng **khác nhau khi nhận tham số và ghi phần tử**. `List<String>` không thể gán cho `List<Object>` vì generic Java bất biến theo đối số kiểu; nhưng có thể gán cho `List<?>`.

## 2. Đọc bằng `get(index)`: kết quả an toàn là `Object`

Với một biến `List<?> items`, biểu thức `items.get(index)` có kiểu được compiler xem là một kiểu **capture** chưa biết, thường được mô tả trong thông báo lỗi bằng ký hiệu như `CAP#1`. Vì kiểu này chưa biết nhưng là kiểu tham chiếu và có cận trên mặc định `Object`, kết quả có thể được gán vào biến `Object`:

```java
List<?> items = List.of("Java", "Generics");

Object value = items.get(0); // Hợp lệ.
System.out.println(value);   // Java

// String text = items.get(0); // Không biên dịch:
// compiler không thể chứng minh kiểu phần tử là String.
```

Về mặt suy luận kiểu, có thể hình dung:

```text
List<?> items
    |
    | get(0)
    v
phần tử kiểu CAP#1 (kiểu thực tế chưa biết)
    |
    | chuyển kiểu tham chiếu lên Object an toàn
    v
Object value
```

Điều này đúng ngay cả khi người viết biết đối tượng thực tế được tạo từ `List.of("Java", "Generics")`. Một biến đã khai báo là `List<?>` không còn cung cấp bảo đảm kiểu `String` ở điểm gọi `get`. Compiler dựa vào **kiểu tĩnh của biểu thức**, không tự suy ngược đối số kiểu từ một giá trị từng gán vào biến.

Một số giới hạn khác vẫn tồn tại dù phép đọc hợp lệ:

- `get(0)` trên danh sách rỗng ném `IndexOutOfBoundsException`.
- Nếu một triển khai danh sách cho phép phần tử `null`, `get` có thể trả về `null`; kiểm tra trước khi gọi phương thức trên giá trị.
- Đọc qua `List<?>` không thay đổi, chuyển đổi hay sao chép đối tượng phần tử; kết quả `Object` vẫn tham chiếu tới cùng đối tượng.
- Không thể giả định kiểu tham chiếu tĩnh `Object` chứa mọi phương thức riêng của kiểu phần tử.

## 3. Ví dụ Java 17 hoàn chỉnh: đọc nhiều kiểu danh sách

Lưu chương trình sau thành `UnboundedReadDemo.java`:

```java
import java.util.List;

public class UnboundedReadDemo {
    static void printElements(List<?> items) {
        System.out.println("size=" + items.size());

        for (int i = 0; i < items.size(); i++) {
            Object element = items.get(i);
            String type = element == null
                    ? "null"
                    : element.getClass().getSimpleName();

            System.out.println(i + ": " + element + " (" + type + ")");
        }
    }

    public static void main(String[] args) {
        List<String> words = List.of("Java", "API");
        List<Integer> numbers = List.of(10, 20);

        printElements(words);
        printElements(numbers);
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac UnboundedReadDemo.java
java UnboundedReadDemo
```

Kết quả:

```text
size=2
0: Java (String)
1: API (String)
size=2
0: 10 (Integer)
1: 20 (Integer)
```

Phương thức `printElements(List<?> items)` không cần biết danh sách chứa kiểu gì để đếm phần tử, truy cập theo chỉ số và in ra biểu diễn của từng đối tượng. Phương thức `getClass()` trong ví dụ chỉ dùng để **quan sát kiểu runtime** của một phần tử không phải `null`. Thông tin đó không làm thay đổi kiểu tĩnh của `items` từ `List<?>` thành `List<String>` hay `List<Integer>`.

Đây là tình huống điển hình nên dùng wildcard không giới hạn: thuật toán làm việc đồng đều với mọi đối tượng và không cần gọi một phương thức chuyên biệt của kiểu phần tử.

## 4. Đọc bằng enhanced `for`, iterator, stream và mảng

Không nhất thiết phải dùng chỉ số. `List<?>` triển khai `Iterable<?>` nên có thể duyệt như sau:

```java
static void printEach(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Với iterator, kiểu kết quả cũng chưa biết:

```java
Iterator<?> it = items.iterator();
while (it.hasNext()) {
    Object item = it.next();
    System.out.println(item);
}
```

Hai đoạn trên minh họa **thân phương thức**, không phải chương trình độc lập. Đoạn dùng `Iterator<?>` cần `import java.util.Iterator;` và `items` phải là `List<?>` đã khai báo.

Tương tự:

```java
Stream<?> stream = items.stream();
Object[] array = items.toArray();
```

Đoạn này cần `import java.util.stream.Stream;`. `Stream<?>` có thể được duyệt dưới kiểu `Object`; `toArray()` không tham số trả về `Object[]`. Nếu muốn có phần tử kiểu `String` hoặc `Number`, phải có cơ sở về kiểu rồi mới chuyển đổi hoặc áp dụng chữ ký có cận kiểu.

**Điểm kỹ thuật:** `Iterable<?>`, `Iterator<?>`, `Stream<?>` và `List<?>` là những kiểu generic riêng biệt; compiler kiểm tra mỗi biểu thức qua chữ ký phương thức tương ứng. Chúng cùng thể hiện nguyên tắc: đọc giá trị thuộc một đối số kiểu không xác định vẫn an toàn khi xem giá trị là `Object`.

## 5. Muốn sử dụng phương thức riêng của kiểu phần tử thì làm gì?

Vì `Object` không có phương thức như `String.toUpperCase()` hay `Number.doubleValue()`, đoạn sau không biên dịch:

```java
List<?> items = List.of("java");

// items.get(0).toUpperCase(); // Không biên dịch.
// String text = items.get(0);  // Không biên dịch.
```

Có hai hướng giải quyết, tùy ý nghĩa nghiệp vụ.

### 5.1. Kiểm tra kiểu runtime bằng `instanceof`

Nếu dữ liệu thật sự có thể khác kiểu và cần xử lý theo từng kiểu, dùng kiểm tra kiểu trước khi gọi phương thức. Java 17 hỗ trợ pattern matching cho `instanceof`:

Lưu thành `SafeWildcardRead.java`:

```java
import java.util.List;

public class SafeWildcardRead {
    static void describeFirst(List<?> items) {
        if (items.isEmpty()) {
            System.out.println("empty");
            return;
        }

        Object first = items.get(0);

        if (first instanceof String text) {
            System.out.println("text: " + text.toUpperCase());
        } else if (first instanceof Number number) {
            System.out.println("number: " + number.doubleValue());
        } else if (first == null) {
            System.out.println("null");
        } else {
            System.out.println("other: " + first.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        describeFirst(List.of("java"));
        describeFirst(List.of(7));
        describeFirst(List.of());
    }
}
```

```bash
javac SafeWildcardRead.java
java SafeWildcardRead
```

Kết quả:

```text
text: JAVA
number: 7.0
empty
```

Nhánh `instanceof String text` đảm bảo bên trong nhánh ấy, **biến `text`** có kiểu `String`. Điều đó không làm cho toàn bộ danh sách `items` được suy ra là `List<String>`: có thể có những phần tử kiểu khác ở chỉ số khác, nhất là khi nguồn được truyền vào qua các kiểu khác nhau.

Nếu bắt buộc phải ép kiểu tường minh, chẳng hạn `String text = (String) items.get(0);`, hãy nhớ ép kiểu chỉ được xác minh tại runtime và có thể ném `ClassCastException`. Không dùng ép kiểu chỉ để che đi việc thiếu thông tin kiểu.

### 5.2. Dùng wildcard có cận nếu nghiệp vụ cần một nhóm kiểu cụ thể

Nếu phương thức cần đọc số để tính toán, chữ ký thích hợp hơn là `List<? extends Number>`:

```java
static double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number number : numbers) {
        total += number.doubleValue();
    }
    return total;
}
```

Bây giờ compiler bảo đảm mọi phần tử đọc ra có thể xem là `Number`. Phương thức nhận được `List<Integer>`, `List<Double>` và `List<Number>` mà không cần kiểm tra `instanceof Number` cho từng phần tử. Ngược lại, `List<?>` dành cho trường hợp thuật toán **không cần** biết đặc tính cụ thể nào ngoài những gì `Object` cung cấp.

Có thể ghi nhớ sự khác nhau:

| Kiểu tham số | Compiler biết gì khi đọc? | Có thể gọi trực tiếp |
| --- | --- | --- |
| `List<?>` | Phần tử là một kiểu tham chiếu chưa biết | Phương thức `Object` |
| `List<? extends Number>` | Phần tử là `Number` hoặc kiểu con | `doubleValue()`, `intValue()`, v.v. |
| `List<? super Integer>` | Phần tử thuộc một kiểu cha chưa biết của `Integer` | Khi đọc chỉ chắc chắn có `Object` |
| `List<String>` | Phần tử là `String` | `length()`, `toUpperCase()`, v.v. |

`? super Integer` cho phép **ghi** `Integer` nhưng không giúp đọc ra giá trị có kiểu `Integer`. Cận dưới chỉ bảo đảm đích nhận được `Integer`; danh sách thực tế cũng có thể là `List<Object>`.

## 6. Ví dụ với `Map<?, ?>`: đọc khóa và giá trị đều là `Object`

Với `Map<K, V>`, hai đối số kiểu độc lập nhau. `Map<?, ?>` cho biết cả kiểu khóa lẫn kiểu giá trị đều chưa biết. Ta có thể đọc từ bản đồ nhưng không tự kết luận khóa là `Integer` hoặc giá trị là `String`.

Lưu thành `UnknownMapRead.java`:

```java
import java.util.Map;

public class UnknownMapRead {
    static void printEntries(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            System.out.println(key + " -> " + value);
        }
    }

    public static void main(String[] args) {
        Map<Integer, String> labels = Map.of(7, "seven");
        printEntries(labels);

        Map<?, ?> unknown = labels;
        Object value = unknown.get(7);
        System.out.println("lookup: " + value);
    }
}
```

```bash
javac UnknownMapRead.java
java UnknownMapRead
```

Kết quả:

```text
7 -> seven
lookup: seven
```

Trong `Map<?, ?>`, `get(Object key)` **nhận tham số kiểu `Object`** theo hợp đồng của `Map`, nên được phép gọi với đối tượng khóa dù kiểu khóa generic chưa biết. Tuy nhiên, kết quả đọc ra vẫn chỉ bảo đảm gán được cho `Object`. `Map.Entry<?, ?>.getKey()` và `getValue()` cũng tuân theo quy tắc tương tự.

Nếu muốn giữ kiểu tĩnh của khóa và giá trị để thao tác chuyên biệt, nên dùng `Map<K,V>` hoặc cận wildcard đúng nhu cầu, thay vì ép kiểu tùy ý.

## 7. `?` khác `Object` ở đâu khi đọc?

Xét:

```java
List<String> names = List.of("Lan", "Mai");
List<?> unknown = names;
Object first = unknown.get(0); // Hợp lệ.

// List<Object> objects = names; // Không biên dịch.
```

Kết quả từ `unknown.get(0)` có thể lưu trong `Object`, nhưng **không có phép chuyển đổi ngầm từ `List<?>` sang `List<Object>`**.

Lý do là một biến `List<Object>` cho phép thêm một `Object` bất kỳ (nếu danh sách có hỗ trợ sửa đổi); nếu nó trỏ tới `List<String>`, việc thêm `Integer` sẽ phá hợp đồng phần tử. Ngược lại, `List<?>` duy trì giới hạn thao tác khiến ta không thể thêm một phần tử bất kỳ chỉ dựa trên việc đọc được kiểu `Object`.

Thử so sánh các lời gọi sau trong một ngữ cảnh có danh sách thay đổi được:

```java
List<String> strings = new ArrayList<>();
List<?> unknown = strings;
Object value = "hello";

unknown.size();       // Hợp lệ.
unknown.contains(value); // Hợp lệ: contains nhận Object.
unknown.getClass();   // Hợp lệ: phương thức của Object.

// unknown.add(value); // Không biên dịch.
// unknown.add("hi");  // Không biên dịch.
```

Đoạn minh họa trên cần `import java.util.ArrayList;` và `import java.util.List;`.

**Đọc được một phần tử không đồng nghĩa có quyền ghi chính phần tử ấy trở lại bằng kiểu `Object`**. Việc đọc và việc ghi cần những bảo đảm kiểu khác nhau.

## 8. Có thể đọc phần tử rồi đặt lại vào cùng danh sách không?

Đây là trường hợp dễ gây nhầm lẫn với *wildcard capture*:

```java
static void replaceFirstWithSecond(List<?> items) {
    // items.set(0, items.get(1)); // Không biên dịch.
}
```

Mặc dù `items.get(1)` đọc từ chính danh sách đó, kiểu tĩnh mà compiler xử lý tại lời gọi `set` vẫn cần một đối số tương thích với kiểu phần tử chưa biết được capture. Không thể đơn giản coi kết quả của lời gọi `get` là một `Object` rồi đưa vào `set`.

Khi cần sử dụng **cùng một kiểu phần tử `T`** xuyên suốt thao tác, dùng một phương thức generic để compiler đặt tên cho kiểu được capture:

Lưu thành `WildcardCaptureDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardCaptureDemo {
    static void swapFirstTwo(List<?> items) {
        if (items.size() < 2) {
            return;
        }
        swapCaptured(items);
    }

    private static <T> void swapCaptured(List<T> items) {
        T first = items.get(0);
        items.set(0, items.get(1));
        items.set(1, first);
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("before", "after"));
        swapFirstTwo(words);
        System.out.println(words);

        List<Integer> numbers = new ArrayList<>(List.of(1, 2));
        swapFirstTwo(numbers);
        System.out.println(numbers);
    }
}
```

```bash
javac WildcardCaptureDemo.java
java WildcardCaptureDemo
```

Kết quả:

```text
[after, before]
[2, 1]
```

Phương thức ngoài nhận mọi `List<?>`. Phương thức `swapCaptured` ràng buộc mọi lần đọc và ghi trong thân của nó vào cùng tham số kiểu `T`, nên truyền phần tử từ vị trí này sang vị trí khác an toàn về kiểu. Trong thư viện chuẩn, `Collections.swap(List<?>, int, int)` cũng cung cấp chức năng hoán đổi tương ứng; ví dụ trên giúp hiểu vì sao phương thức generic hỗ trợ những trường hợp kiểu capture mà code trực tiếp bị từ chối.

Lưu ý `set` có thể ném `UnsupportedOperationException` nếu danh sách không hỗ trợ sửa đổi; trong ví dụ dùng `ArrayList` để bảo đảm danh sách có thể cập nhật.

## 9. Kiểm tra và duyệt nội dung mà không biết đối số kiểu

Một số thao tác không yêu cầu kiểu phần tử cụ thể, nên có thể triển khai chung qua `List<?>`:

```java
static boolean hasAnyItem(List<?> items) {
    return !items.isEmpty();
}

static Object firstOrNull(List<?> items) {
    return items.isEmpty() ? null : items.get(0);
}

static void logObjects(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Đây là các phương thức minh họa; để biên dịch cùng nhau, đặt chúng trong một class có `import java.util.List;`. Ở đây:

- `isEmpty()` và `size()` không phụ thuộc đối số kiểu.
- `get(0)` và vòng lặp đọc ra được `Object`.
- `contains(Object)`, `indexOf(Object)` và `lastIndexOf(Object)` nhận đối số là `Object` nên có thể gọi mà không phải biết kiểu generic.

`List<?>` thường thích hợp cho API kiểm tra, hiển thị, thống kê số lượng phần tử, logging hoặc xem xét cấu trúc dữ liệu khi không yêu cầu phương thức riêng trên từng phần tử.

Với kiểu hoàn toàn không biết, xử lý theo `Object` là an toàn về **hệ kiểu**, nhưng không tự bảo đảm mọi thao tác nghiệp vụ đều thành công: phương thức của phần tử có thể ném exception; `toString()` có thể chứa thông tin nhạy cảm, hoặc danh sách có thể bị thay đổi trong khi duyệt. Chỉ sử dụng những dữ liệu thật sự cần xử lý.

## 10. Đọc qua wildcard không đảm bảo danh sách bất biến

Một phương thức **chỉ đọc** nên chọn `List<?>` khi không cần type argument cụ thể, nhưng chữ ký ấy không ngăn người khác sửa backing list; bản thân `clear()` hoặc `remove(Object)` cũng có thể gọi qua wildcard.

```java
List<String> source = new ArrayList<>(List.of("A", "B"));
List<?> view = source;
Object first = view.get(0);
view.clear();
System.out.println(first + " / " + source); // A / []
```

Ví dụ dùng `ArrayList` có thể sửa. Với `List.of(...)`, thao tác `clear()` sẽ không được hỗ trợ. Nếu cần một snapshot không cho chỉnh sửa cấu trúc, cân nhắc `List.copyOf(source)` (không chấp nhận phần tử null); không nhầm bảo đảm bất biến với kiểu wildcard hay khả năng an toàn đa luồng.

## 11. Wildcard không giới hạn và generic method: chọn theo nhu cầu đọc

Một phương thức chỉ cần đọc dưới kiểu `Object` có thể nhận `List<?>` trực tiếp:

```java
static void display(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Không cần tạo một tham số kiểu `<T>` chỉ để viết lại:

```java
static <T> void displayGeneric(List<T> items) {
    for (T item : items) {
        System.out.println(item);
    }
}
```

Cả hai ví dụ đều xử lý được `List<String>` và `List<Integer>`. Khi `T` chỉ xuất hiện một lần trong chữ ký và không có quan hệ giữa các tham số, `List<?>` thường thể hiện ý định đơn giản hơn.

Tuy nhiên, `<T>` hữu ích khi kiểu cần được **duy trì và kết nối** qua giá trị trả về hoặc nhiều tham số:

```java
static <T> T first(List<T> items) {
    return items.get(0);
}
```

Với:

```java
List<String> words = List.of("hello");
String word = first(words); // Compiler suy ra T là String.
```

Nếu thay bằng phương thức trả `Object`:

```java
static Object firstUnknown(List<?> items) {
    return items.get(0);
}
```

thì:

```java
Object value = firstUnknown(words); // Hợp lệ.
// String word = firstUnknown(words); // Không biên dịch.
```

Không có gì “sai” trong phương thức `firstUnknown`: nó chỉ cung cấp một hợp đồng yếu hơn. Chọn `List<?>` khi chỉ cần đối tượng tổng quát; chọn `<T>` khi caller cần giữ chính xác kiểu phần tử đọc ra.

## 12. Vấn đề `null`, danh sách rỗng và tính an toàn khi đọc

Đọc dữ liệu từ `List<?>` vẫn cần kiểm tra các điều kiện runtime thông thường.

**Danh sách rỗng:** `items.get(0)` ném `IndexOutOfBoundsException` nếu `items.isEmpty()` là `true`. Wildcard không bảo đảm số phần tử.

**Phần tử `null`:** một danh sách mutable có thể chứa `null`. Khi đọc vào `Object`, vẫn phải kiểm tra `null` trước khi gọi `getClass()`, `toString()` hoặc các phương thức khác. `List.of(...)` từ Java 9 không nhận `null`, nhưng điều này chỉ đúng với cách tạo danh sách đó.

**Danh sách không thể sửa:** `List.of(...)` tạo danh sách không sửa đổi; điều này độc lập với kiểu wildcard. Việc đọc có thể thành công, trong khi `clear` hoặc `set` sẽ ném `UnsupportedOperationException`.

**Đa luồng:** kiểu `List<?>` không tự đảm bảo an toàn luồng. Nếu danh sách bị sửa đồng thời, kết quả đọc hoặc duyệt phụ thuộc hợp đồng của triển khai và cách đồng bộ. Đừng suy diễn rằng “chỉ đọc qua wildcard” là một cơ chế đồng bộ.

**Sai kiểu ở runtime:** việc đọc thành `Object` không gây ép kiểu xuống lớp cụ thể. `ClassCastException` thường phát sinh khi mã sau đó ép kiểu không hợp lệ, hoặc khi chương trình trước đó phá bảo đảm generic bằng raw type/unchecked conversion.

## 13. Câu hỏi kiểm tra nhanh

**Câu 1.** Với `List<?> items`, câu nào chắc chắn hợp lệ về kiểu?

```java
Object a = items.get(0);
// String b = items.get(0);
// Integer c = items.get(0);
```

**Đáp án:** chỉ dòng đầu. Hai dòng sau yêu cầu compiler biết kiểu phần tử cụ thể, nhưng `?` không cung cấp thông tin ấy. Dòng đầu vẫn có thể thất bại lúc chạy nếu danh sách rỗng.

**Câu 2.** Vì sao có thể viết `for (Object value : items)`?

**Đáp án:** mọi phần tử của một `List<?>` là đối tượng thuộc một kiểu tham chiếu chưa biết, nên việc xem từng phần tử như `Object` là an toàn.

**Câu 3.** `List<?>` và `List<Object>` có đồng nhất không?

**Đáp án:** không. Cả hai đọc ra được `Object`, nhưng `List<?>` nhận trực tiếp danh sách tham số hóa với kiểu bất kỳ, còn `List<Object>` chỉ nhận danh sách có đối số kiểu phù hợp với `Object` theo các quy tắc kế thừa generic.

**Câu 4.** Nếu cần gọi `Number.doubleValue()` trên mọi phần tử thì nên nhận `List<?>` hay `List<? extends Number>`?

**Đáp án:** `List<? extends Number>`. Compiler khi đó biết mỗi phần tử đọc ra có thể xem là `Number`, nên gọi `doubleValue()` an toàn mà không phải ép kiểu.

**Câu 5.** Nếu cần trả về đúng kiểu phần tử đầu tiên cho caller, chữ ký nào phù hợp hơn?

```java
static <T> T first(List<T> source)
```

**Đáp án:** phương thức generic này giữ quan hệ giữa kiểu phần tử của `source` và kiểu trả về `T`, trong khi `Object first(List<?> source)` chỉ cung cấp kiểu tổng quát.

## 14. Tổng kết để áp dụng

- `?` biểu diễn một đối số kiểu chưa biết; **đọc từ `List<?>` bảo đảm gán được vào `Object`**.
- `get`, vòng lặp `for` và `Iterator<?>` đều cho phép lấy giá trị dưới kiểu `Object`; cần kiểm tra rỗng và `null` như bình thường.
- Không được tự động nhận kết quả đọc là `String`, `Integer` hay lớp cụ thể khác. Sử dụng `instanceof` nếu dữ liệu thực sự hỗn hợp và cần phân nhánh tại runtime.
- Khi thuật toán cần một họ kiểu cụ thể, ví dụ `Number`, dùng `? extends Number` để tăng thông tin kiểu cho kết quả đọc.
- Khi cần giữ nguyên kiểu phần tử từ đầu vào đến đầu ra, dùng tham số kiểu `<T>`; khi cần xử lý bất kỳ phần tử nào như `Object`, `List<?>` thường đủ.
- Wildcard không giới hạn **không biến collection thành immutable**, không bảo đảm thread-safety và không cho phép thêm tùy ý phần tử chỉ vì đọc được một `Object`.

Đặt câu hỏi “**Sau khi đọc ra, thuật toán cần biết điều gì về phần tử?**” sẽ giúp chọn giữa `List<?>`, wildcard có cận và generic method một cách chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unbounded-wildcard-004">Giới hạn thêm và thay phần tử qua List&lt;?&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thêm và thay phần tử qua `List<?>` trong Java

## 1. Vấn đề cần hiểu

Trong Java, `List<?>` là một `List` có **kiểu phần tử chưa biết**. Ký hiệu `?` là *unbounded wildcard* (wildcard không giới hạn). Nó cho phép tham chiếu tới danh sách có nhiều kiểu phần tử khác nhau mà không đánh mất kiểm tra kiểu của generics:

```java
List<String> words = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

List<?> first = words;    // Hợp lệ
List<?> second = numbers; // Hợp lệ
```

Tuy nhiên, việc có thể tham chiếu tới `List<String>` hoặc `List<Integer>` không có nghĩa có thể thêm bất kỳ giá trị nào vào danh sách thông qua biến `List<?>`.

Nguyên tắc trung tâm:

- **Đọc:** phần tử lấy ra có thể được dùng an toàn dưới kiểu `Object`.
- **Thêm hoặc thay bằng giá trị khác `null`:** trình biên dịch không biết giá trị có đúng kiểu phần tử thực tế hay không, nên không cho phép.
- **Thêm/thay bằng `null`:** có thể **biên dịch**, bởi `null` tương thích với mọi kiểu tham chiếu; việc thực thi vẫn có thể thất bại tùy danh sách.
- **Xóa, làm rỗng hoặc thay đổi vị trí phần tử:** nhiều thao tác vẫn có thể biên dịch vì không phải đưa một giá trị mới không rõ kiểu vào danh sách.

`List<?>` **không đồng nghĩa với danh sách bất biến hoặc chỉ đọc** (*immutable/read-only list*). Giới hạn kiểu ở lúc biên dịch và khả năng sửa đổi của đối tượng danh sách tại lúc chạy là hai vấn đề khác nhau.

## 2. Vì sao không thể `add` một phần tử cụ thể?

Xét ví dụ:

```java
List<String> strings = new ArrayList<>();
List<?> unknown = strings;

unknown.add("Java"); // LỖI BIÊN DỊCH
unknown.add(10);     // LỖI BIÊN DỊCH
unknown.add(new Object()); // LỖI BIÊN DỊCH
```

Trình biên dịch biết `unknown` tham chiếu đến danh sách có một kiểu phần tử nào đó, nhưng **không thể giả định kiểu đó là `String`, `Integer` hay `Object`**. Nếu `add(10)` được phép qua `List<?>` đang trỏ vào `List<String>`, một giá trị `Integer` sẽ lọt vào danh sách vốn được cam kết chứa `String`.

Về khái niệm, trình biên dịch tạo một kiểu nội bộ thường được gọi là **wildcard capture**, chẳng hạn `CAP#1`, để đại diện cho kiểu phần tử chưa biết trong một ngữ cảnh sử dụng `List<?>`. `add(E)` cần đối số thuộc đúng kiểu `E`, trong khi `String` hay `Integer` không được bảo đảm tương thích với `CAP#1`.

Lỗi này không liên quan đến việc danh sách có đang trống hay không:

```java
List<?> empty = new ArrayList<String>();
empty.add("hello"); // Vẫn là lỗi biên dịch
```

Biểu thức `new ArrayList<String>()` ở vế phải không khiến tham số `?` của **biến** `empty` trở thành `String` trong các lời gọi tiếp theo.

Đồng thời, `List<?>` **không phải** `List<Object>`:

```java
List<String> strings = new ArrayList<>();
List<Object> objects = strings; // LỖI BIÊN DỊCH
List<?> unknown = strings;     // Hợp lệ
```

`List<Object>` có thể nhận đối tượng `Object` bất kỳ qua `add`; `List<?>` không cho phép thao tác đó.

## 3. Ngoại lệ về kiểu: có thể thêm `null`

`null` là giá trị tham chiếu đặc biệt, tương thích với mọi kiểu tham chiếu, kể cả kiểu phần tử chưa biết do wildcard capture biểu diễn. Vì vậy:

```java
List<String> strings = new ArrayList<>();
List<?> unknown = strings;

unknown.add(null); // BIÊN DỊCH ĐƯỢC; ArrayList cho phép null
System.out.println(strings); // [null]
```

Chú ý chính xác sự khác biệt giữa **được trình biên dịch chấp nhận** và **thao tác thành công lúc chạy**:

```java
List<?> unmodifiable = List.of("Java");
unmodifiable.add(null); // Biên dịch được; ném UnsupportedOperationException
```

`List.of(...)` tạo danh sách không hỗ trợ các thao tác sửa đổi. Do đó, mặc dù `null` được phép ở mức kiểu, phương thức `add` vẫn ném `UnsupportedOperationException`. Một triển khai danh sách tự viết cũng có thể từ chối `null` theo hợp đồng riêng.

**Không nên** coi `add(null)` là cách hữu ích để vượt qua wildcard. Nó không cho phép thêm dữ liệu nghiệp vụ có kiểu cụ thể và có thể tạo phần tử `null` không mong muốn.

## 4. Giới hạn của `set(index, value)`

Phương thức `set(int index, E element)` thay phần tử tại vị trí có sẵn. Nó không thay đổi kích thước danh sách, nhưng vẫn đưa **một giá trị mới** vào vị trí phải giữ đúng kiểu phần tử `E`. Vì vậy quy tắc wildcard vẫn áp dụng.

```java
List<?> unknown = new ArrayList<String>(List.of("A", "B"));

unknown.set(0, "C"); // LỖI BIÊN DỊCH
unknown.set(0, 99);  // LỖI BIÊN DỊCH
unknown.set(0, null); // BIÊN DỊCH ĐƯỢC
```

Nếu danh sách thực tế hỗ trợ `set` và cho phép `null`, phép thay thế cuối hoạt động:

```java
List<String> source = new ArrayList<>(List.of("A", "B"));
List<?> unknown = source;

Object previous = unknown.set(0, null);
System.out.println(previous); // A
System.out.println(source);   // [null, B]
```

`set` trả về phần tử cũ. Qua `List<?>`, ta có thể nhận giá trị đó dưới kiểu `Object`, vì mọi kiểu phần tử tham chiếu của generic đều là kiểu con của `Object`.

`set(index, null)` vẫn có thể thất bại do:

- **`IndexOutOfBoundsException`** nếu chỉ số ngoài phạm vi hợp lệ.
- **`UnsupportedOperationException`** nếu danh sách không hỗ trợ `set` (ví dụ danh sách trả về bởi `List.of`).
- **`NullPointerException` hoặc lỗi khác** nếu một triển khai có hợp đồng cấm `null` hoặc kiểm tra ràng buộc đặc thù.

Không được suy từ khai báo `List<?>` rằng danh sách chắc chắn sửa được.

### 4.1. Vì sao `list.set(0, list.get(0))` cũng có thể lỗi biên dịch?

Một trường hợp dễ gây ngạc nhiên:

```java
static void replaceWithItself(List<?> list) {
    list.set(0, list.get(0)); // LỖI BIÊN DỊCH
}
```

Lời gọi `get(0)` đọc được một phần tử. Nhưng ở biểu thức trên, Java không bảo đảm trình biên dịch sử dụng **cùng một kiểu capture** cho giá trị trả về của `get` và kiểu tham số cần thiết của `set`; kết quả `get` có thể được nhìn như một kiểu capture khác, nên không gán được cho tham số của `set`. Việc người lập trình biết hai thao tác dùng cùng đối tượng danh sách không đủ để cú pháp trên được chấp nhận.

Nếu cần đọc rồi ghi lại phần tử với cùng một kiểu chưa biết, hãy dùng **phương thức helper generic** để bắt giữ kiểu đó một lần:

```java
static <T> void replaceWithItselfCaptured(List<T> list) {
    if (!list.isEmpty()) {
        list.set(0, list.get(0)); // Hợp lệ: cả hai cùng T
    }
}

static void caller(List<?> values) {
    replaceWithItselfCaptured(values); // Wildcard capture khi gọi
}
```

Đây là thao tác thay bằng chính giá trị cũ nhằm minh họa kỹ thuật capture; nó không làm thay đổi dữ liệu. Phương thức helper vẫn có thể ném `UnsupportedOperationException` nếu danh sách thực tế không cho phép `set`.

## 5. Xóa hoặc hoán đổi là nhóm thao tác khác với đưa phần tử mới vào

`add(E)` và `set(int,E)` cần **giá trị thuộc kiểu phần tử E**. Còn `remove(int)`, `remove(Object)`, `clear()` chỉ bỏ dữ liệu hiện có nên không cần caller cung cấp một E mới. `Collections.swap(List<?>,i,j)` cũng hợp lệ vì thư viện dùng generic helper để bảo toàn cùng kiểu phần tử khi hoán đổi.

| Lời gọi qua `List<?>` | Biên dịch? | Lưu ý |
| --- | --- | --- |
| `get(0)`, `size()` | Có | `get` cần chỉ số hợp lệ. |
| `add("A")`, `set(0,"A")` | Không | String chưa chắc là kiểu của wildcard. |
| `add(null)`, `set(0,null)` | Có | Thao tác còn tùy mutability/null contract. |
| `remove(0)`, `remove("A")` | Có | Một bên xóa theo chỉ số, một bên theo Object. |
| `clear()`, `Collections.reverse(list)` | Có | Runtime list phải hỗ trợ sửa đổi cần thiết. |

```java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
List<?> view = values;
Object removed = view.remove(1);
Collections.reverse(view);
System.out.println(removed + " / " + values); // B / [C, A]
```

Các lời gọi này không chèn phần tử sai kiểu, nhưng vẫn thay đổi dữ liệu. Phần kế tiếp phân biệt cụ thể `ArrayList`, `Arrays.asList` và `List.of` để lý giải tại sao cùng một lời gọi đúng kiểu có thể thành công hoặc ném `UnsupportedOperationException`.

## 6. Phân biệt ba loại danh sách thường gặp

| Danh sách | `add(null)` | `set(0, null)` | `remove(0)` |
| --- | --- | --- | --- |
| `new ArrayList<>(List.of("A"))` | Thường thành công | Thành công | Thành công |
| `Arrays.asList("A")` | `UnsupportedOperationException` | Thành công | `UnsupportedOperationException` |
| `List.of("A")` | `UnsupportedOperationException` | `UnsupportedOperationException` | `UnsupportedOperationException` |

Cả ba trường hợp đều có thể được gán cho một biến `List<?>`. Chúng khác nhau ở **hợp đồng sửa đổi của đối tượng cụ thể**:

1. `ArrayList` thông thường cho phép thêm, thay, xóa và chấp nhận `null`.
2. `Arrays.asList` trả về danh sách có **kích thước cố định**, liên kết với mảng nền: cho phép `set` nhưng không cho thêm hoặc xóa làm đổi kích thước.
3. `List.of` trả về danh sách **không thể sửa đổi**; không cho `add`, `set`, `remove`, và không cho chứa `null`.

Không nên đánh đồng "bất biến theo tham số kiểu của generics" (*invariance*) với "không thể sửa đổi dữ liệu" (*unmodifiable/immutable collection*).

## 7. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp `WildcardMutationDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WildcardMutationDemo {
    // Helper giữ cùng một T khi vừa đọc vừa ghi giá trị đã có.
    private static <T> void swap(List<T> items, int left, int right) {
        T first = items.get(left);
        items.set(left, items.get(right));
        items.set(right, first);
    }

    static void swapFirstTwo(List<?> items) {
        if (items.size() >= 2) {
            swap(items, 0, 1); // Java capture kiểu của '?' khi gọi helper.
        }
    }

    public static void main(String[] args) {
        List<String> words =
                new ArrayList<>(Arrays.asList("Java", "Generics", "List"));
        List<?> unknown = words;

        System.out.println("Read: " + unknown.get(0));

        Object old = unknown.set(1, null);
        System.out.println("Old: " + old);
        System.out.println("After set null: " + words);

        unknown.remove(1);
        System.out.println("After remove: " + words);

        swapFirstTwo(unknown);
        System.out.println("After swap: " + words);

        unknown.add(null);
        System.out.println("After add null: " + words);

        try {
            List<?> fixed = List.of("immutable");
            fixed.add(null);
        } catch (UnsupportedOperationException e) {
            System.out.println(
                    "Unmodifiable: " + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 WildcardMutationDemo.java
java WildcardMutationDemo
```

Kết quả:

```text
Read: Java
Old: Generics
After set null: [Java, null, List]
After remove: [Java, List]
After swap: [List, Java]
After add null: [List, Java, null]
Unmodifiable: UnsupportedOperationException
```

Diễn giải:

- `unknown.get(0)` đọc `String` dưới dạng `Object`; thao tác in vẫn hiện nội dung chuỗi.
- `set(1, null)` thay `Generics` bằng `null`; biến `old` giữ giá trị cũ.
- `remove(1)` xóa phần tử `null`, còn `[Java, List]`.
- Helper `swap` bắt giữ kiểu phần tử cùng một `T`, cho phép đổi hai phần tử mà không cần ép kiểu và không thêm giá trị sai kiểu.
- `add(null)` thành công với `ArrayList`.
- `List.of` từ chối `add` tại runtime dù đối số `null` hợp lệ về mặt generic.

## 8. Khi cần thêm hoặc thay bằng một giá trị thật: chọn kiểu API khác

### 8.1. Biết chính xác kiểu phần tử: dùng `List<T>`

Nếu phương thức phải thao tác với chuỗi, khai báo rõ:

```java
static void replaceFirst(List<String> list, String replacement) {
    if (!list.isEmpty()) {
        list.set(0, replacement);
    }
}
```

Phương thức chỉ nhận `List<String>` (và các triển khai của nó) nên có thể thay bằng `String`. Trình biên dịch kiểm tra kiểu của giá trị mới.

### 8.2. Cùng kiểu chưa biết: dùng type parameter và wildcard capture

Nếu phương thức phải đọc phần tử đã có rồi đưa nó vào lại danh sách hoặc hoán đổi hai phần tử, dùng `<T>` để giữ quan hệ giữa giá trị và danh sách:

```java
static <T> void rotateFirstTwo(List<T> list) {
    if (list.size() < 2) {
        return;
    }
    T first = list.get(0);
    list.set(0, list.get(1));
    list.set(1, first);
}
```

Một biến `List<?>` vẫn truyền được vào phương thức này vì compiler có thể capture kiểu wildcard cho **cả lời gọi**:

```java
List<?> list = new ArrayList<>(List.of(1, 2));
rotateFirstTwo(list); // Hợp lệ nếu đối tượng hỗ trợ set
```

Điểm mấu chốt: `<T>` không cho phép nhét kiểu tùy ý vào danh sách; nó buộc **các vị trí dùng `T` thống nhất với nhau**.

### 8.3. Muốn thêm giá trị kiểu `T`: dùng `? super T`

Đối với phương thức chỉ nhận dữ liệu để ghi:

```java
static void appendText(List<? super String> destination) {
    destination.add("Java");
}
```

Phương thức chấp nhận `List<String>` và `List<Object>`, vì cả hai đều có thể chứa một `String`. Ngược lại, `List<?>` không bảo đảm điều này.

### 8.4. Muốn đọc với giới hạn trên: dùng `? extends T`

```java
static double total(List<? extends Number> source) {
    double result = 0;
    for (Number number : source) {
        result += number.doubleValue();
    }
    return result;
}
```

`? extends Number` hữu ích để **đọc** giá trị như `Number`, nhưng vẫn không cho `source.add(1)` hay `source.set(0, 1)`. Giới hạn trên không làm cho kiểu phần tử cụ thể trở nên xác định.

## 9. Lỗi phổ biến và hướng xử lý

| Nhầm lẫn | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `List<?>` cho phép `add(Object)` | Kiểu phần tử thực tế có thể hẹp hơn `Object` | Dùng `List<Object>` khi đối tượng thực sự phải chứa mọi `Object`, hoặc `? super T` để nhận `T` |
| `List<?>` luôn chỉ đọc | `remove`, `clear`, `set(..., null)`, hoán đổi có thể biên dịch | Xem riêng hợp đồng thay đổi của implementation |
| `add(null)` luôn thành công | `List.of` không hỗ trợ sửa đổi; triển khai khác có thể không nhận `null` | Kiểm tra hợp đồng danh sách; tránh chèn `null` khi không cần |
| `set(index, x)` được phép chỉ vì không đổi kích thước | Nó vẫn đưa giá trị `x` mới vào vị trí có kiểu phần tử chưa biết | Dùng `List<T>` hoặc `List<? super T>` thích hợp |
| `list.set(0, list.get(0))` chắc chắn hợp lệ với `List<?>` | Hai lần dùng wildcard có thể thành các capture không tương thích | Đưa cả thao tác đọc và ghi vào helper `<T>` |
| Ép `List<?>` thành `List<String>` để thêm giá trị | Ép kiểu unchecked không chứng minh danh sách thật chứa `String`; có thể tạo heap pollution | Tạo API có type parameter đúng ngay từ đầu |
| `Collections.swap` hoạt động nên `list.add("x")` cũng phải hoạt động | Swap chỉ chuyển các giá trị đã cùng nằm trong danh sách; add đưa giá trị bên ngoài vào | Phân biệt sắp xếp phần tử hiện có và chèn phần tử mới |

**Đừng dùng raw type** (ví dụ `List raw = unknown;`) như cách vượt giới hạn. Raw type bỏ qua phần lớn kiểm tra generic ở bước biên dịch, có thể gây lỗi ép kiểu về sau khi phần tử được đọc qua một tham chiếu có kiểu cụ thể.

## 10. Tự kiểm tra: biên dịch hay lỗi runtime?

Dự đoán kết quả của mỗi đoạn **độc lập** sau, giả sử đã import `java.util.*`.

**A — Thêm phần tử cụ thể**

```java
List<?> a = new ArrayList<String>();
a.add("X");
```

**B — Thêm `null` vào danh sách sửa được**

```java
List<?> b = new ArrayList<String>();
b.add(null);
System.out.println(b.size());
```

**C — Thay bằng `null` trên danh sách không sửa được**

```java
List<?> c = List.of("X");
c.set(0, null);
```

**D — Hoán đổi phần tử trên danh sách có thể sửa**

```java
List<?> d = new ArrayList<>(List.of("X", "Y"));
Collections.swap(d, 0, 1);
System.out.println(d);
```

**E — Đọc rồi truyền thẳng vào `set`**

```java
List<?> e = new ArrayList<>(List.of("X"));
e.set(0, e.get(0));
```

**Đáp án:**

- **A:** Không biên dịch; `String` không được bảo đảm là kiểu phần tử của `List<?>`.
- **B:** Biên dịch và chạy thành công với `ArrayList`; in `1`.
- **C:** Biên dịch được, nhưng `List.of` ném `UnsupportedOperationException` khi gọi `set`.
- **D:** Biên dịch và chạy thành công; in `[Y, X]`.
- **E:** Không biên dịch vì wildcard capture trong lời gọi `get` không thống nhất với kiểu tham số mà `set` yêu cầu. Có thể dùng helper `<T>` để bảo đảm cả hai vị trí cùng một kiểu.

## 11. Tổng kết

Đối với `List<?>`, trình biên dịch **không chấp nhận chèn hay thay thế bằng một giá trị khác `null`** vì kiểu phần tử chưa biết. `add(null)` và `set(index, null)` hợp lệ về mặt kiểu nhưng không bảo đảm chạy thành công. Xóa, làm rỗng và sắp xếp lại phần tử có thể hợp lệ về kiểu; chúng vẫn tuân theo khả năng sửa đổi thực tế của đối tượng danh sách.

Khi cần ghi dữ liệu thật, hãy mô tả quan hệ kiểu rõ ràng bằng `List<T>`, `List<? super T>` hoặc helper generic `<T>`. Wildcard `?` giúp API nhận nhiều kiểu danh sách, nhưng vẫn giữ nguyên bảo đảm an toàn kiểu của Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unbounded-wildcard-005">Vì sao vẫn có thể clear hoặc remove trên một số List&lt;?&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao vẫn có thể `clear()` hoặc `remove()` trên một số `List<?>`?

## Mục tiêu

Sau bài học, bạn có thể giải thích chính xác vì sao khai báo `List<?>` ngăn nhiều thao tác **thêm hoặc thay phần tử** nhưng vẫn cho phép **gọi các phương thức xóa** như `clear()`, `remove(Object)`, `remove(int)` và `removeIf(...)`. Bạn cũng sẽ phân biệt được hai câu hỏi riêng biệt:

1. **Trình biên dịch cho phép gọi phương thức không?** — phụ thuộc vào kiểu tĩnh `List<?>` và chữ ký phương thức.
2. **Đối tượng danh sách thực tế có hỗ trợ thay đổi không?** — phụ thuộc vào implementation, trạng thái và hợp đồng của danh sách tại runtime.

Hai câu hỏi này giải thích từ khóa **“một số”** trong đề bài: cùng một khai báo `List<?>` có thể trỏ đến danh sách sửa được, danh sách kích thước cố định hoặc danh sách không cho thay đổi.

## 1. `List<?>` nghĩa là gì?

`List<?>` là một `List` có **kiểu phần tử cụ thể chưa biết** (unbounded wildcard). Chẳng hạn, cùng một tham số phương thức kiểu `List<?>` có thể nhận `List<String>`, `List<Integer>` hoặc `List<Customer>`. Trình biên dịch không được giả định kiểu phần tử ấy là `String`, `Integer` hay `Object`.

```java
static void inspect(List<?> values) {
    Object element = values.get(0); // hợp lệ nếu danh sách có phần tử
    System.out.println(element);

    // values.add("new");   // lỗi biên dịch
    // values.add(123);     // lỗi biên dịch
    // values.set(0, "new"); // lỗi biên dịch
}
```

Đây là **đoạn phương thức minh họa**, không phải tệp Java độc lập. Với `get(0)`, một phần tử thuộc kiểu chưa biết vẫn có thể được nhìn qua kiểu `Object` vì mọi kiểu tham chiếu Java đều là subtype của `Object`. Ngược lại, để thêm một `String` vào `List<?>`, compiler cần chứng minh `String` tương thích với **kiểu phần tử thực chưa biết**. Nó không thể chứng minh điều đó nếu đối tượng thực tế có thể là `List<Integer>`.

**Không nên hiểu `List<?>` đồng nghĩa với danh sách chỉ đọc (read-only).** Wildcard mô tả giới hạn thông tin kiểu ở compile time; nó không tự biến đối tượng thành immutable.

## 2. Cơ chế: thao tác xóa không cần biết `E` để đưa một giá trị mới vào danh sách

Xem các chữ ký chính của API `List<E>` và `Collection<E>`. Đoạn dưới mô tả **hình dạng chữ ký để học**, không phải bản sao đầy đủ khai báo interface JDK:

```java
// Những phương thức liên quan đến E:
boolean add(E element);
E set(int index, E element);
E get(int index);

// Những phương thức xóa:
void clear();
boolean remove(Object value);
E remove(int index);
boolean removeIf(java.util.function.Predicate<? super E> filter);
```

Các phương thức có thể được khai báo trực tiếp trong `List` hoặc được `List` kế thừa từ `Collection`. Khi biến có kiểu `List<?>`, compiler coi `E` là một **kiểu bắt wildcard chưa biết** (capture type). Điều quan trọng là phương thức sẽ **nhận** hay **trả về** một giá trị kiểu `E`:

- `add(E)` và `set(int, E)`: đưa một phần tử mới vào vị trí có kiểu `E`. Không thể tự ý truyền một đối tượng non-null đã biết như `String` hoặc `Integer` vào `List<?>`.
- `clear()`: **không nhận** phần tử mới. Xóa hết phần tử hiện có không tạo nguy cơ đưa sai kiểu vào danh sách.
- `remove(Object)`: nhận đối số kiểu `Object` để tìm/xóa theo so sánh bằng nhau, **không yêu cầu** tham số thuộc đúng kiểu `E`.
- `remove(int)`: chỉ nhận **chỉ số `int`**. Giá trị bị loại bỏ có kiểu `E` chưa biết, nhưng có thể nhận kết quả đó dưới kiểu `Object`.
- `removeIf(Predicate<? super E>)`: predicate chỉ **kiểm tra** từng phần tử rồi quyết định xóa; không cần chèn phần tử kiểu `E` mới.

Vì thế, tất cả lời gọi xóa trên đều **có thể biên dịch** qua tham chiếu `List<?>`. Cụm từ “có thể biên dịch” không có nghĩa chúng luôn chạy thành công.

### Tại sao `remove(Object)` dùng `Object`?

Chữ ký nhận `Object` cho phép tìm và thử xóa một đối tượng dựa trên `equals`, ngay cả khi kiểu của đối tượng truyền vào không trùng kiểu phần tử của danh sách. Ví dụ, gọi `listOfStrings.remove(123)` **hợp lệ khi biên dịch**; trên một `ArrayList<String>` thông thường, lệnh trả về `false` nếu không có phần tử tương ứng. Một implementation chuyên biệt vẫn có thể áp dụng các ràng buộc hợp đồng riêng; đừng suy ra mọi danh sách phải phản hồi giống nhau cho mọi đối số.

Trong khi đó, `add` nhận `E` vì mục đích của nó là **lưu** đối tượng vào danh sách. Nếu cho phép thêm tùy tiện qua `List<?>` thì lời hứa kiểu của một `List<Integer>` có thể bị phá vỡ.

### Vì sao `remove(int)` hợp lệ dù trả về `E`?

Kiểu trả về có thể là kiểu capture chưa biết mà compiler vẫn gán an toàn cho `Object`:

```java
static Object takeFirst(List<?> list) {
    return list.remove(0); // hợp lệ, danh sách phải có ít nhất một phần tử
}
```

Phương thức không cần tạo hoặc chèn một đối tượng kiểu `E`. Tuy nhiên, nếu danh sách rỗng, `remove(0)` ném `IndexOutOfBoundsException`; nếu implementation không hỗ trợ xóa, nó có thể ném `UnsupportedOperationException`.

## 3. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`WildcardRemovalDemo.java`** với nội dung đầy đủ:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WildcardRemovalDemo {

    static void removeAndClear(List<?> elements) {
        Object first = elements.remove(0);
        System.out.println("Removed by index: " + first);

        boolean removedByValue = elements.remove("cam");
        System.out.println("Removed by value: " + removedByValue);

        elements.clear();
        System.out.println("After clear: " + elements);
    }

    public static void main(String[] args) {
        List<String> fruits =
                new ArrayList<>(List.of("tao", "cam", "le"));

        List<?> view = fruits;
        System.out.println("First: " + view.get(0));
        removeAndClear(view);
        System.out.println("Original: " + fruits);

        List<?> numbers = new ArrayList<>(List.of(1, 2, 3, 4));
        boolean changed = numbers.removeIf(
                e -> e instanceof Integer i && i % 2 == 0);
        System.out.println("Remove evens: " + changed + " -> " + numbers);

        List<?> fixedSize = Arrays.asList("a", "b");
        try {
            fixedSize.clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("Arrays.asList: clear unsupported");
        }

        List<?> immutable = List.of("a", "b");
        try {
            immutable.remove(0);
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of: remove unsupported");
        }
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên từ thư mục chứa tệp:

```bash
javac WildcardRemovalDemo.java
java WildcardRemovalDemo
```

Stdout:

```text
First: tao
Removed by index: tao
Removed by value: true
After clear: []
Original: []
Remove evens: true -> [1, 3]
Arrays.asList: clear unsupported
List.of: remove unsupported
```

Giải thích trình tự:

1. `fruits` có kiểu `List<String>`, nhưng `view` có kiểu `List<?>`; **cả hai trỏ tới cùng một `ArrayList`**. `view.get(0)` đọc được phần tử đầu.
2. `elements.remove(0)` xóa `"tao"` và trả về phần tử dưới kiểu `Object`. `elements.remove("cam")` xóa chuỗi `"cam"` vì nó tồn tại, nên trả về `true`.
3. `elements.clear()` xóa nốt `"le"`. In lại `fruits` thấy `[]`, chứng minh wildcard không tạo bản sao và không đóng băng đối tượng.
4. `removeIf` duyệt các giá trị và xóa hai số chẵn khỏi `ArrayList`. Predicate ở đây kiểm tra kiểu bằng `instanceof` rồi xét phần dư; cú pháp pattern matching này hợp lệ từ Java 16.
5. Hai lời gọi cuối đều **qua `List<?>` và hợp lệ khi biên dịch**, nhưng thất bại tại runtime vì danh sách nền không hỗ trợ xóa.

Chương trình cố ý dùng `new ArrayList<>(List.of(...))` cho phần cần xóa: `List.of(...)` tự nó không hỗ trợ các thao tác thay đổi. Hàm `removeAndClear` giả định danh sách đầu vào **không rỗng và sửa được**; không nên gọi nó với bất kỳ `List<?>` nào khi chưa biết hợp đồng của đối tượng.

## 4. “Một số” danh sách: khác nhau ở mutability, không ở wildcard

| Cách tạo danh sách | Có thể gọi `clear/remove` từ `List<?>`? | Khi danh sách có phần tử, có hỗ trợ xóa thực tế? |
| --- | --- | --- |
| `new ArrayList<>(...)` | Có | Có; danh sách thay đổi kích thước được |
| `new LinkedList<>(...)` | Có | Có; danh sách thay đổi kích thước được |
| `Arrays.asList("a", "b")` | Có | Không; danh sách kích thước cố định, thường cho phép `set` nhưng không cho thêm/xóa |
| `List.of("a", "b")` | Có | Không; danh sách không thể thay đổi |
| `List.copyOf(source)` | Có | Không; tạo danh sách không thể thay đổi |
| `Collections.unmodifiableList(source)` | Có | Không qua wrapper; thao tác thay đổi bị từ chối |
| `new CopyOnWriteArrayList<>(...)` | Có | Các phương thức `remove/clear` trực tiếp được hỗ trợ; `Iterator.remove()` không được hỗ trợ |

Bảng phân biệt **lời gọi hợp lệ theo kiểu tĩnh** với **hành vi runtime của các implementation tiêu biểu**. Trong JDK, nhiều thao tác sửa đổi collection được coi là *optional operations*; khi không được hỗ trợ, implementation có thể ném `UnsupportedOperationException`. Điều này đúng cả với biến `List<String>` hay `List<Integer>`, không phải do riêng dấu `?`.

Đặc biệt, **kích thước cố định** không có nghĩa là bất biến hoàn toàn: danh sách do `Arrays.asList(...)` tạo cho phép thay giá trị tại chỉ số hiện có bằng `set` khi gọi qua tham chiếu có kiểu phần tử thích hợp, nhưng không cho đổi số phần tử. Ngược lại, `List.of(...)` không cho thay giá trị lẫn thay kích thước.

Với `Collections.unmodifiableList(source)`, wrapper không cho sửa qua nó; nếu mã khác còn giữ `source` và sửa được `source`, nội dung nhìn qua wrapper có thể thay đổi. Vì vậy “unmodifiable view” cũng khác “immutable snapshot”.

## 5. Hai overload `remove`: xóa theo chỉ số và theo giá trị

Java có cả `remove(int index)` và `remove(Object value)`. Cả hai gọi được trên `List<?>`, nhưng chúng chọn mục tiêu khác nhau.

```java
List<?> values = new ArrayList<>(List.of(10, 20, 30));
values.remove(1);                 // xóa chỉ số 1: xóa 20
values.remove(Integer.valueOf(30)); // xóa giá trị 30
System.out.println(values);       // [10]
```

Đây là **đoạn minh họa**, có thể đặt bên trong phương thức của một lớp Java đã import `java.util.ArrayList` và `java.util.List`.

- Đối số kiểu nguyên thủy `int` như `1` chọn `remove(int)`.
- Đối số `Integer.valueOf(30)` chọn `remove(Object)` theo quy tắc chọn overload, nên tìm phần tử bằng giá trị.
- Nếu bạn muốn xóa số nguyên theo giá trị mà không gây nhầm với chỉ số, dùng `Integer.valueOf(value)` hoặc ép đối số sang `Object`.
- `remove(int)` trả về phần tử bị xóa, và có thể ném `IndexOutOfBoundsException` nếu chỉ số không hợp lệ. `remove(Object)` trả về `boolean` cho biết việc xóa theo giá trị có xảy ra hay không trên các danh sách hỗ trợ phép xóa.

Nhầm overload có thể dẫn tới xóa nhầm phần tử hoặc lỗi chỉ số ngay cả khi wildcard và implementation hoàn toàn cho phép xóa.

## 6. Xóa bằng iterator và các phương thức collection khác

Wildcard cũng không tự cấm xóa qua iterator, vì `Iterator.remove()` không yêu cầu cung cấp phần tử kiểu `E`:

```java
static void removeFirstWithIterator(List<?> values) {
    var iterator = values.iterator();
    if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
    }
}
```

Đây là **đoạn phương thức minh họa**. Với iterator của `ArrayList`, lời gọi `remove()` sau `next()` xóa phần tử vừa duyệt. Nhưng `Iterator.remove()` cũng là thao tác tùy chọn: iterator của `CopyOnWriteArrayList` không hỗ trợ nó, dù `CopyOnWriteArrayList.remove(Object)` gọi trực tiếp vẫn có thể hoạt động. Gọi `remove()` khi chưa có `next()` thích hợp cũng có thể ném `IllegalStateException`.

Các phương thức kế thừa từ `Collection` như `removeIf(Predicate<? super E>)`, `removeAll(Collection<?>)` và `retainAll(Collection<?>)` cũng có thể được gọi qua `List<?>`. Chúng chọn hoặc giữ lại phần tử **đang có**, chứ không cần chèn một phần tử tùy tiện vào kiểu `E` chưa biết. Chúng vẫn có thể không được implementation hỗ trợ.

Trong các phép duyệt/xóa, tránh sửa `ArrayList` trực tiếp bằng `list.remove(...)` bên trong vòng lặp foreach đang sử dụng iterator của chính nó; điều đó có thể gây `ConcurrentModificationException`. Nếu cần lọc để xóa, ưu tiên `removeIf` hoặc `Iterator.remove` đúng hợp đồng của implementation.

## 7. Trường hợp đặc biệt của `null`

Dù `add("abc")` không biên dịch khi receiver là `List<?>`, **`list.add(null)` có thể biên dịch**: `null` chuyển được sang bất kỳ kiểu tham chiếu nào, kể cả capture type chưa biết. Tương tự, `list.set(0, null)` vượt qua kiểm tra kiểu tĩnh.

Điều đó **không bảo đảm thành công ở runtime**. Ví dụ `List.of(...)` không hỗ trợ thêm/thay phần tử, và một số collection không chấp nhận `null`. Không nên xem khả năng truyền `null` là giải pháp để ghi phần tử kiểu tùy ý vào `List<?>`.

`remove(null)` cũng biên dịch vì overload `remove(Object)` chấp nhận tham chiếu `null`. Kết quả còn tùy đối tượng nền: danh sách có cho phép phần tử null không, có hỗ trợ xóa không, và quy định kiểm tra đối số là gì.

## 8. Thiết kế API an toàn

Nếu một phương thức chỉ cần xóa một phần tử theo đối tượng hay làm rỗng danh sách, khai báo `List<?>` diễn đạt đúng rằng **logic không phụ thuộc kiểu phần tử**:

```java
static void reset(List<?> items) {
    items.clear(); // gọi được nếu runtime list hỗ trợ clear()
}
```

Nếu mã cần **thêm hoặc thay các phần tử `String` mới**, hãy khai báo tham số `List<String>` hoặc một kiểu generic phù hợp. Nếu mã cần đọc rồi giữ quan hệ kiểu của phần tử qua nhiều thao tác, có thể dùng generic method thay vì ép kiểu không an toàn:

```java
static <T> void replaceFirstWithSameValue(List<T> items) {
    if (!items.isEmpty()) {
        T first = items.get(0);
        items.set(0, first);
    }
}
```

Đây là **đoạn phương thức minh họa**. Mối liên hệ giữa `T` của kết quả `get` và đối số của `set` được compiler giữ lại; phương thức vẫn yêu cầu implementation hỗ trợ `set`.

Không nên chuyển `List<?>` thành raw `List` hoặc cast tùy tiện sang `List<Object>` chỉ để gọi `add`. Cast không làm kiểu phần tử thực thay đổi và có thể phá an toàn kiểu, dẫn tới lỗi ở vị trí sử dụng khác. Tên `List<?>` cũng không đồng nghĩa `List<Object>`: `List<?>` chấp nhận nhiều loại danh sách, còn `List<Object>` có thể nhận bất kỳ `Object` được phép thêm.

## 9. Kiểm tra nhanh

**Câu 1.** Nếu `List<?> a = new ArrayList<String>();`, `a.clear()` có biên dịch và chạy được không?

**Trả lời:** Có. `clear` không cần đối số kiểu `String`, và `ArrayList` hỗ trợ thao tác xóa.

**Câu 2.** Nếu `List<?> b = List.of("x");`, `b.remove(0)` có lỗi biên dịch không?

**Trả lời:** Không. Lời gọi hợp lệ khi biên dịch; lúc chạy nó ném `UnsupportedOperationException` vì danh sách từ `List.of` không cho thay đổi.

**Câu 3.** Vì sao `a.remove("x")` được phép nhưng `a.add("x")` bị compiler chặn?

**Trả lời:** `remove` nhận `Object` và chỉ tìm/xóa phần tử cũ; `add` nhận `E` và cần chắc chắn phần tử mới đúng với kiểu chưa biết mà wildcard biểu diễn.

**Câu 4.** Có phải `List<?>` lúc nào cũng cho phép xóa bằng iterator?

**Trả lời:** Không. Về mặt kiểu tĩnh, lời gọi `iterator.remove()` hợp lệ; về runtime, một số iterator từ chối thao tác này.

## Kết luận

**`List<?>` giới hạn việc đưa một giá trị non-null bất kỳ vào danh sách vì kiểu phần tử chưa được biết; nó không tự cấm việc xóa các phần tử đã có.** Các phương thức `clear()`, `remove(Object)` và `remove(int)` không đòi bạn cung cấp phần tử mới có kiểu `E`, nên chúng có thể vượt qua kiểm tra kiểu của compiler. Việc xóa **thực sự thực hiện được hay không** là trách nhiệm của implementation và hợp đồng runtime của danh sách, không phải dấu `?`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unbounded-wildcard-006">Mỗi wildcard có thể đại diện cho một kiểu khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Mỗi wildcard `?` có thể đại diện cho một kiểu khác nhau trong Java Generics

Trong Java Generics, dấu hỏi `?` biểu thị **một kiểu tham số chưa xác định**. Khi khai báo `List<?>`, ta biết đó là danh sách thuộc **một kiểu phần tử cụ thể nào đó**, nhưng không biết kiểu ấy là `String`, `Integer` hay một kiểu tham chiếu khác.

Điểm then chốt: **hai vị trí wildcard không mặc nhiên cùng đại diện cho một kiểu**. Mỗi wildcard có thể được trình biên dịch xử lý như một kiểu ẩn riêng. Hai kiểu ẩn đó *có thể trùng nhau trong thực tế*, nhưng nếu chương trình không biểu diễn được quan hệ giữa chúng, trình biên dịch **không được phép giả định chúng bằng nhau**.

Bài học này nằm trong chủ đề *wildcard không giới hạn (unbounded wildcard)* và tập trung vào cách đọc dữ liệu, lý do thao tác chuyển phần tử giữa hai danh sách `List<?>` bị từ chối, cơ chế **wildcard capture**, và cách dùng generic method để giữ an toàn kiểu.

## 1. `List<?>` thực sự nói điều gì?

Xét:

```java
List<String> names = List.of("An", "Binh");
List<Integer> ages = List.of(20, 25);

List<?> first = names;
List<?> second = ages;
```

Cả hai phép gán đều hợp lệ. Khác với `List<Object>`, `List<?>` không khẳng định danh sách chứa `Object`; nó chỉ nói rằng **kiểu đối số hiện chưa được biết**.

- Với `first`, kiểu phần tử thực của danh sách đang được tham chiếu là `String`.
- Với `second`, kiểu phần tử thực là `Integer`.
- Khi chỉ nhìn chữ ký `List<?>`, trình biên dịch không biết rõ từng kiểu ẩn đó.
- Hai dấu `?` **không phải hai biến cùng tên** để ràng buộc chúng với nhau. Chúng có thể biểu thị hai kiểu độc lập.

Một danh sách bất kỳ có thể được đọc qua `List<?>` ở dạng `Object`:

```java
Object name = first.get(0);
Object age = second.get(0);
```

Điều này an toàn vì mọi đối tượng Java thuộc một kiểu tham chiếu đều có thể được sử dụng như `Object`. Nhưng gán trực tiếp `String text = first.get(0)` sẽ không biên dịch, mặc dù người lập trình biết `first` vừa được gán từ `names`: biểu thức `first.get(0)` được kiểm tra theo **kiểu khai báo của `first` là `List<?>`**.

> Hãy hiểu `?` là “có một kiểu phần tử, nhưng người dùng tham chiếu này chưa biết kiểu đó”; đừng hiểu là “mọi phần tử có thể có kiểu bất kỳ và được tự do thêm vào”.

`List<?>` tương đương về ý nghĩa giới hạn kiểu với `List<? extends Object>`. Đây là wildcard **không có ràng buộc tường minh**, có giới hạn trên mặc định là `Object`.

## 2. Hai wildcard độc lập: cùng ký hiệu, chưa chắc cùng kiểu

Phương thức sau nhận **hai danh sách bất kỳ**:

```java
static void showFirstPair(List<?> left, List<?> right) {
    Object a = left.get(0);
    Object b = right.get(0);
    System.out.println(a + " | " + b);
}
```

Chữ ký có hai dấu `?` khác nhau:

```text
showFirstPair(List<?>, List<?>)
                   ^        ^
                kiểu X    kiểu Y
```

Đây là sơ đồ minh họa về **hai kiểu chưa biết**; `X` và `Y` không phải tên kiểu thật trong khai báo. `X` và `Y` *có thể bằng nhau hoặc khác nhau*, nhưng chữ ký không đòi hỏi chúng bằng nhau.

Ví dụ cả hai lời gọi đều hợp lệ (giả sử đã khai báo phương thức và danh sách):

```java
showFirstPair(List.of(10, 20), List.of("An", "Binh"));
showFirstPair(List.of("Java"), List.of("Generics"));
```

Ở lời gọi đầu, các danh sách mang phần tử thuộc hai kiểu khác nhau. Ở lời gọi sau, cả hai danh sách đều có phần tử `String`. **Chữ ký `List<?>` chấp nhận cả hai trường hợp**, vì nó không ràng buộc hai đối số kiểu với nhau.

Điểm quan trọng hơn: kể cả khi hai tham chiếu thực tế trỏ vào **cùng một đối tượng danh sách**, quan hệ đó cũng không được suy ra thành một ràng buộc kiểu chung giữa hai lần dùng `?`.

```java
List<Integer> original = new ArrayList<>(List.of(1, 2));
List<?> a = original;
List<?> b = original;

Object x = a.get(0); // Hợp lệ.
Object y = b.get(0); // Hợp lệ.
a.set(0, b.get(0));  // LỖI BIÊN DỊCH.
```

Dòng `set` cố tình minh họa lỗi, không phải mã chạy độc lập. Trong hoàn cảnh này, con người thấy `a` và `b` cùng trỏ tới `original`, nhưng kiểu tĩnh `List<?>` không cung cấp bằng chứng về việc kiểu capture dùng cho tham số ghi của `a` bằng kiểu phần tử đọc từ `b`.

## 3. Vì sao không thể chuyển phần tử giữa hai `List<?>`?

Thử viết phương thức:

```java
static void moveFirst(List<?> destination, List<?> source) {
    destination.add(source.get(0)); // LỖI BIÊN DỊCH.
}
```

Giả sử hai dấu `?` luôn được coi là cùng kiểu thì lệnh `add` sẽ có vẻ hợp lệ. Nhưng phương thức này cũng có thể được gọi với:

```java
List<Integer> destination = new ArrayList<>(List.of(1));
List<String> source = List.of("Java");

// moveFirst(destination, source);
// Nếu phương thức sai ở trên được chấp nhận, nó có thể
// đưa String vào danh sách chỉ dành cho Integer.
```

Hợp đồng `List<Integer>` yêu cầu phần tử thêm vào tương thích với `Integer`. `source.get(0)` từ `List<String>` trả về `String`. Do đó Java cấm phép ghi ngay ở thời điểm biên dịch.

Ta có thể tóm tắt nguyên tắc như sau:

| Thao tác với hai `List<?>` | Hợp lệ? | Vì sao |
| --- | --- | --- |
| `Object a = left.get(0)` | Có | Mọi kiểu phần tử đều đọc được dưới dạng `Object` |
| `Object b = right.get(0)` | Có | Tương tự cho wildcard thứ hai |
| `left.add(right.get(0))` | Không | Chưa biết kiểu của `right` có phù hợp với `left` |
| `left.set(0, right.get(0))` | Không | Đổi phần tử cũng phải thỏa kiểu phần tử của `left` |
| `left.add(null)` | Có về mặt kiểu | `null` tương thích mọi kiểu tham chiếu; collection vẫn có thể từ chối runtime |
| `left.clear()` | Có về mặt kiểu | Xóa phần tử không đưa vào kiểu mới; collection vẫn có thể không hỗ trợ |
| `left.contains(right.get(0))` | Có về mặt kiểu | `contains(Object)` nhận `Object` để kiểm tra, không thêm phần tử |

Không được dùng `?` như cách lách quy tắc an toàn kiểu. Dấu `?` cho phép **quan sát dữ liệu chung**, đồng thời giới hạn các thao tác yêu cầu phải biết kiểu phần tử.

## 4. Wildcard capture: trình biên dịch gắn một kiểu ẩn vào `?`

Khi cần xử lý `List<?>`, trình biên dịch có thể thực hiện **capture conversion**: tạm thời xem wildcard như một kiểu chưa biết nhưng nhất quán trong phạm vi thích hợp. Thông báo lỗi của `javac` đôi khi nhắc đến `capture#1 of ?`, `capture#2 of ?`.

Ví dụ mang tính minh họa, không phải mã Java cần khai báo:

```text
List<?> left  → List<CAP#1>
List<?> right → List<CAP#2>

CAP#1 và CAP#2:
- Đều có giới hạn trên tương thích Object.
- Không có ràng buộc rằng CAP#1 = CAP#2.
- Có thể trùng kiểu cụ thể tại runtime; trình biên dịch vẫn không được giả định.
```

Các tên `CAP#1` và `CAP#2` chỉ mô tả cách hiểu lỗi, **không phải kiểu Java có thể viết trong mã nguồn**. Số thứ tự và lời báo lỗi cụ thể do trình biên dịch quyết định, không phải một phần của API.

Một ví dụ đặc biệt dễ nhầm:

```java
static <T> void requireSameType(List<T> one, List<T> two) {
    // Chữ ký yêu cầu cùng một T cho cả hai tham số.
}

static void demo(List<?> items) {
    requireSameType(items, items); // LỖI BIÊN DỊCH.
}
```

Dù lặp **chính cùng biến** `items` hai lần, hai biểu thức đối số có thể được capture độc lập. Trình biên dịch không suy ra được một `T` chung thỏa cả hai ràng buộc bằng nhau.

Điều này không mâu thuẫn với việc truyền **một** `List<?>` cho phương thức generic có **một** tham số `List<T>`: khi đó Java chỉ cần capture một kiểu ẩn và dùng nó nhất quán bên trong phương thức.

## 5. Vì sao phép đọc thành Object không chứng minh được quyền ghi?

Mỗi `List<?>` có một kiểu phần tử ẩn. Giá trị từ `get(0)` có thể dùng như `Object`, nhưng Object quá rộng để tự động trở thành kiểu phần tử của danh sách đích:

```java
static void inspect(List<?> left, List<?> right) {
    Object first = left.get(0);
    Object second = right.get(0);
    System.out.println(first + " / " + second);
    // left.add(second); // Không biên dịch: hai capture độc lập.
}
```

Ngay cả `left.add(first)` cũng không tự an toàn theo kiểu tĩnh, vì biến `first` là Object. Ngoại lệ đặc biệt `add(null)` chỉ nói về khả năng gán của null, không bảo đảm danh sách mutable hay chấp nhận null. **Bài toán riêng của hàng này:** cần một mối liên hệ kiểu có thể chứng minh giữa các vị trí wildcard khác nhau. Một generic helper chỉ có thể capture an toàn **một đối số danh sách** và giữ cùng T nội bộ; muốn chuyển phần tử giữa hai danh sách, phải khai báo quan hệ giữa hai kiểu ngay trong API.

## 6. Bắt cùng một kiểu bằng generic helper

Có những phép chỉnh sửa **an toàn** với danh sách chưa biết kiểu, miễn là ta chỉ **di chuyển các phần tử trong chính danh sách đó**, không đưa phần tử của danh sách không liên quan vào.

Ví dụ hoán đổi hai vị trí trong một `List<?>`. Viết trực tiếp rất dễ gặp lỗi capture:

```java
static void swapWrong(List<?> items, int a, int b) {
    // items.set(a, items.get(b)); // LỖI BIÊN DỊCH.
}
```

Giải pháp thông dụng là một phương thức ngoài nhận wildcard và một **generic helper** bên trong đặt tên cho kiểu bị ẩn:

```java
static void swap(List<?> items, int a, int b) {
    swapCaptured(items, a, b);
}

private static <T> void swapCaptured(List<T> items, int a, int b) {
    T oldA = items.get(a);
    items.set(a, items.get(b));
    items.set(b, oldA);
}
```

Cách hoạt động:

1. `swap` nhận `List<?>` nên người gọi có thể truyền `List<String>`, `List<Integer>` hoặc các danh sách kiểu khác.
2. Khi gọi `swapCaptured(items, ...)`, Java capture wildcard và suy luận một `T` phù hợp **cho một đối số `items`**.
3. Trong `swapCaptured`, mỗi lần `get` và `set` cùng làm việc với **một `List<T>`**; lấy `T` từ vị trí này và đặt lại vào vị trí khác là hợp lệ.
4. Phương thức không cần biết `T` chính xác là `String` hay `Integer`, chỉ cần bảo đảm thao tác nội bộ không phá hợp đồng của `T`.

Đây là giải pháp cho **một wildcard của một danh sách**, không biến hai danh sách `List<?>` độc lập thành cùng kiểu. Nó là kỹ thuật *wildcard capture helper* thường dùng khi API cần chấp nhận `List<?>` nhưng một thao tác nội bộ phải giữ liên hệ với đúng kiểu phần tử bị ẩn.

Ngoài kiểm tra generic, vẫn cần bảo đảm chỉ số `a`, `b` hợp lệ và danh sách có thể chỉnh sửa. Nếu không, runtime có thể ném `IndexOutOfBoundsException` hoặc `UnsupportedOperationException`.

## 7. Nếu hai danh sách phải có quan hệ kiểu, hãy biểu diễn bằng `T`

Khi muốn **chuyển phần tử từ danh sách này sang danh sách khác**, cần mô tả chính xác mối quan hệ nguồn–đích.

### 7.1. Yêu cầu cùng đối số kiểu

```java
static <T> void appendFirstSameType(List<T> destination, List<T> source) {
    destination.add(source.get(0));
}
```

`T` ở hai vị trí **là cùng một biến kiểu**. Nếu người gọi có `List<String>` và `List<String>`, lời gọi phù hợp. Nếu người gọi có `List<String>` và `List<Integer>`, Java không thể chọn `T` thỏa cả hai tham số `List<T>` vì generic collections bất biến.

Đối chiếu:

```java
List<String> source = List.of("Java");
List<String> destination = new ArrayList<>();
appendFirstSameType(destination, source); // Hợp lệ.

// List<Integer> integers = new ArrayList<>();
// appendFirstSameType(destination, integers); // LỖI BIÊN DỊCH.
```

Đây là cách thể hiện **yêu cầu cùng kiểu** tại API. Nó khác với hai dấu `?` không ràng buộc.

### 7.2. Nguồn thuộc kiểu con, đích nhận kiểu cha

Đôi khi nguồn là `List<Integer>` và đích là `List<Number>`. Hai loại danh sách **khác nhau**, nhưng vẫn có thể truyền phần tử an toàn vì mỗi `Integer` cũng là một `Number`.

```java
static <T> void appendFirst(
        List<? extends T> source,
        List<? super T> destination) {
    destination.add(source.get(0));
}
```

- `List<? extends T>` cho phép **đọc** phần tử như `T` từ nguồn.
- `List<? super T>` cho phép **ghi** phần tử `T` vào đích.
- **Cùng một `T`** kết nối hai tham số, với wildcard có giới hạn phù hợp.

Đây là ứng dụng của nguyên tắc **PECS**: *Producer Extends, Consumer Super*. Ở đây `source` là nơi cung cấp phần tử; `destination` là nơi nhận phần tử. Trường hợp này không thể diễn tả đầy đủ bằng hai `List<?>` độc lập, vì chúng không cung cấp thông tin về quan hệ kiểu phần tử.

Không nên ép kiểu `(List<Number>)(List<?>) integers` để “chữa” lỗi. Ép kiểu không an toàn không tạo ra quan hệ kiểu thật và có thể làm hỏng hợp đồng của danh sách.

## 8. Ví dụ Java 17+ hoàn chỉnh

Chương trình dưới đây thể hiện đồng thời:

- Hai tham số `List<?>` độc lập có thể nhận hai kiểu danh sách khác nhau.
- Generic helper có thể hoán đổi phần tử **trong cùng danh sách**.
- Generic method có ràng buộc `extends/super` có thể chuyển phần tử từ `List<Integer>` sang `List<Number>`.

Lưu thành **`WildcardDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardDemo {
    static void showFirstPair(List<?> left, List<?> right) {
        Object a = left.get(0);
        Object b = right.get(0);
        System.out.println(a + " | " + b);
    }

    static void swap(List<?> items, int a, int b) {
        swapCaptured(items, a, b);
    }

    private static <T> void swapCaptured(List<T> items, int a, int b) {
        T oldA = items.get(a);
        items.set(a, items.get(b));
        items.set(b, oldA);
    }

    static <T> void appendFirst(
            List<? extends T> source,
            List<? super T> destination) {
        destination.add(source.get(0));
    }

    public static void main(String[] args) {
        List<Integer> ints = new ArrayList<>(List.of(10, 20, 30));
        List<String> names = new ArrayList<>(List.of("An", "Binh"));

        List<?> first = ints;
        List<?> second = names;
        showFirstPair(first, second);

        swap(ints, 0, 2);
        swap(names, 0, 1);
        System.out.println(ints);
        System.out.println(names);

        List<Number> numbers = new ArrayList<>();
        appendFirst(ints, numbers);
        System.out.println(numbers);

        showFirstPair(ints, names);
    }
}
```

Biên dịch và chạy:

```bash
javac WildcardDemo.java
java WildcardDemo
```

Stdout:

```text
10 | An
[30, 20, 10]
[Binh, An]
[30]
30 | Binh
```

**Giải thích kết quả:** `first` tham chiếu `List<Integer>` và `second` tham chiếu `List<String>`, nên `showFirstPair` đọc được `10` và `An` như `Object`. `swap` đổi vị trí các phần tử bên trong từng danh sách với đúng kiểu thực của chúng. Sau khi đổi chỗ, số đầu của `ints` là `30`, được thêm hợp lệ vào `numbers` kiểu `List<Number>`. Hai danh sách có kiểu phần tử khác nhau vẫn được xử lý chung khi chỉ cần đọc.

## 9. Không thể “dùng một `T`” để ép hai capture độc lập trở thành cùng kiểu

Có thể bạn sẽ thử giải quyết vấn đề bằng cách đưa cả hai `List<?>` vào một generic helper:

```java
static <T> void copySameType(List<T> to, List<T> from) {
    to.add(from.get(0));
}

static void copyUnknown(List<?> to, List<?> from) {
    copySameType(to, from); // LỖI BIÊN DỊCH.
}
```

`copySameType` tự nó hợp lệ vì yêu cầu hai `List<T>` có **chính xác một `T`**. Nhưng lời gọi `copySameType(to, from)` không thể chứng minh hai capture độc lập của `to` và `from` tương ứng với cùng kiểu. Một helper không thể tự phát minh ra thông tin kiểu mà hai tham số bên ngoài không cung cấp.

Ngay cả trường hợp sau vẫn có thể thất bại:

```java
static void copyUnknown(List<?> items) {
    copySameType(items, items); // LỖI BIÊN DỊCH: hai lần capture độc lập.
}
```

Khác biệt quan trọng:

- `swapCaptured(items)` chỉ nhận **một `List<T>`**; sau khi capture, mọi phép đọc/ghi trong helper đều dùng cùng `T`.
- `copySameType(items, items)` có **hai vị trí đối số** cùng phải phù hợp với một `T`; từ biểu thức `List<?>`, capture của từng vị trí không mặc nhiên là cùng kiểu.
- `copySameType(typed, typed)` với `typed` khai báo là `List<Integer>` thì hợp lệ vì loại `Integer` đã được thể hiện trong hệ kiểu.

Khi tác vụ thực sự đòi hỏi quan hệ giữa hai tham số, nên đặt quan hệ ấy ở **chữ ký API đầu vào**, bằng `<T>` và các giới hạn cần thiết. Nếu chỉ xử lý nội bộ một danh sách chưa biết kiểu, hãy dùng capture helper với **một danh sách**.

## 10. `Map<?, ?>`: hai wildcard trong cùng một kiểu generic

Tình huống tương tự xuất hiện khi một kiểu generic có **nhiều đối số kiểu**:

```java
Map<?, ?> data = Map.of("language", 17);
Object key = data.keySet().iterator().next();
Object value = data.values().iterator().next();
```

Dấu `?` thứ nhất mô tả kiểu khóa chưa biết; dấu `?` thứ hai mô tả kiểu giá trị chưa biết. Không có điều kiện mặc định rằng **kiểu khóa phải bằng kiểu giá trị**. Ví dụ `Map<String, Integer>` và `Map<Long, Long>` đều có thể được tham chiếu bằng `Map<?, ?>`.

Trong phần lớn thao tác ghi, ta không thể đưa một khóa hoặc giá trị không-null bất kỳ vào `Map<?, ?>`, vì kiểu ẩn của chúng chưa được biết. Tư duy này cũng áp dụng cho `BiFunction<?, ?, ?>` và các generic type có nhiều dấu `?`: **mỗi vị trí wildcard cần được xét theo ràng buộc riêng**.

## 11. Những nhầm lẫn dễ gặp

### “Dùng cùng ký hiệu `?` nghĩa là cùng kiểu”

**Sai.** `?` không phải biến có tên dùng lại như `T`. Hai wildcard khác nhau không tự sinh ra ràng buộc bằng nhau. Có thể ở runtime chúng liên quan đến cùng một loại phần tử, nhưng trình biên dịch cần chứng cứ tĩnh được biểu diễn qua kiểu.

### “Hai danh sách đều `List<?>` nên thêm từ bên này sang bên kia được”

**Sai.** Cả hai cùng tương thích với kiểu giao diện `List<?>` để **đọc**, không có nghĩa kiểu phần tử của chúng tương thích để **ghi**. Ví dụ `List<Integer>` và `List<String>` đều gán được cho `List<?>` nhưng không thể chuyển phần tử tùy ý giữa chúng.

### “Một danh sách khai báo `List<?>` có thể chứa đủ mọi loại đối tượng”

**Sai theo cách hiểu về khả năng ghi.** `List<?>` tham chiếu một danh sách có kiểu phần tử cụ thể chưa biết; điều đó không cấp quyền thêm mọi loại đối tượng. Nếu muốn một danh sách có phần tử mang nhiều kiểu khác nhau và cần thêm đối tượng bất kỳ, có thể dùng `List<Object>` khi thiết kế bài toán thật sự đòi hỏi như vậy.

### “Một helper generic sẽ làm hai kiểu ẩn tự động thống nhất”

**Sai.** Helper chỉ giúp Java **đặt tên một capture** và áp dụng nó nhất quán trong thân phương thức. Nếu nhận hai danh sách chưa có quan hệ kiểu, không thể khẳng định một `T` chung bằng cách gọi helper một cách mù quáng.

### “`List<?>` là immutable”

**Sai.** Kiểu wildcard và tính có thể chỉnh sửa của collection là hai vấn đề độc lập. Một `ArrayList<String>` thông qua `List<?>` có thể `clear()`, còn `List.of(1, 2)` có thể từ chối chỉnh sửa dù tham chiếu được khai báo với `List<Integer>` cụ thể.

### “Type erasure khiến các wildcard không khác nhau”

**Sai.** Type erasure ảnh hưởng đến cách biểu diễn generic ở runtime; các phép gán, capture và ràng buộc kiểu vẫn được **trình biên dịch Java** áp dụng trước khi chạy. Không thể lấy việc nhiều `List<T>` cùng bị xóa đối số generic để suy ra có thể trao đổi phần tử giữa hai `List<?>`.

## 12. Tự kiểm tra

Với các khai báo sau, hãy xét **khả năng biên dịch**, giả sử phương thức được gọi trên danh sách không rỗng khi đọc:

```java
List<Integer> ints = new ArrayList<>(List.of(1, 2));
List<String> texts = new ArrayList<>(List.of("A", "B"));
List<?> a = ints;
List<?> b = texts;
```

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `Object x = a.get(0);` | Hợp lệ | Đọc `List<?>` ra `Object` |
| `String s = b.get(0);` | Lỗi biên dịch | `b.get(0)` chỉ bảo đảm một kiểu chưa biết, dùng an toàn như `Object` |
| `a.add(b.get(0));` | Lỗi biên dịch | Không chứng minh được hai wildcard có kiểu tương thích |
| `a.set(0, a.get(0));` | Lỗi biên dịch | Hai lần capture có thể độc lập; cần generic helper |
| `a.add(null);` | Hợp lệ về kiểu | `null` được chấp nhận về kiểu; runtime tùy collection |
| `showFirstPair(a, b);` | Hợp lệ | Phương thức chỉ đọc hai danh sách dưới dạng `Object` |
| `swap(ints, 0, 1);` | Hợp lệ | Generic helper xử lý một danh sách kiểu `Integer` |
| `appendFirst(ints, new ArrayList<Number>());` | Hợp lệ | `Integer` có thể được thêm vào đích nhận `Number` |

Chú ý: “hợp lệ về kiểu” không bảo đảm chạy luôn thành công; ví dụ `get(0)` trên danh sách rỗng vẫn ném `IndexOutOfBoundsException`, hoặc `add(null)` trên collection không hỗ trợ chỉnh sửa vẫn có thể ném ngoại lệ.

## Ghi nhớ

1. `?` là **một kiểu tham số chưa biết**, không phải một biến chung mang tên `?`.
2. **Mỗi wildcard/capture có thể tương ứng với một kiểu khác nhau**; chúng cũng có thể trùng nhau, nhưng trình biên dịch không tự giả định quan hệ bằng nhau.
3. `List<?>` cho phép **đọc ra `Object`**; nó không bảo đảm có thể ghi phần tử không-null thuộc một kiểu cụ thể.
4. Khi cần thao tác bên trong **một danh sách** mà phải giữ nguyên kiểu phần tử, dùng generic helper để capture thành `List<T>`.
5. Khi cần thao tác giữa **hai danh sách có quan hệ kiểu**, hãy mô tả quan hệ bằng `<T>`, `? extends T` và `? super T`, thay vì chỉ đưa cả hai về `List<?>`.

Hiểu tính độc lập của mỗi dấu `?` giúp giải thích các lỗi `capture of ?` trong Java và lựa chọn chữ ký generic chính xác cho API, vừa linh hoạt khi đọc dữ liệu vừa bảo vệ an toàn kiểu lúc ghi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unbounded-wildcard-007">Các vị trí cú pháp không cho phép wildcard</a>

<details>
<summary>Xem chi tiết</summary>

# Các vị trí cú pháp không cho phép wildcard trong Java Generics

## 1. Mục tiêu học tập

Dấu hỏi `?` trong Java Generics là **wildcard type argument**: nó biểu thị một đối số kiểu chưa được biết chính xác, ví dụ `List<?>`. Wildcard không phải tên của một kiểu độc lập, không khai báo ra biến kiểu mới và không thể thay thế `T` ở mọi vị trí.

Bài này tập trung vào những **vị trí cú pháp hoặc ngữ cảnh kiểu mà Java không cho phép wildcard**, cách đọc thông báo lỗi và cách thay thế cho đúng. Trọng tâm là wildcard không giới hạn `?`; khi cần thiết, bài cũng đối chiếu với `? extends T` và `? super T` để tránh suy luận sai.

Sau bài học, bạn có thể:

- Phân biệt khai báo tham số kiểu (`<T>`) với việc sử dụng đối số kiểu wildcard (`<?>`).
- Nhận ra các vị trí cấm wildcard trong khai báo, kế thừa, khởi tạo đối tượng và lời gọi generic.
- Hiểu các ngữ cảnh *chỉ* cho phép wildcard không giới hạn, đặc biệt `instanceof` và tạo mảng generic.
- Chọn cách sửa bằng kiểu cụ thể, `<T>`, diamond `<>`, hoặc tham chiếu `List<?>` tùy mục đích.
- Phân biệt lỗi **cú pháp/ngữ cảnh** với lỗi **thao tác không an toàn trên wildcard**.

## 2. Quy tắc nền tảng: wildcard là type argument, không phải type parameter

Xem hai khái niệm khác nhau:

```java
class Box<T> {             // T là type parameter: tên biến kiểu được khai báo.
    private final T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}

Box<String> named = new Box<>("Java"); // String là đối số kiểu cụ thể.
Box<?> unknown = named;                 // ? là đối số kiểu wildcard hợp lệ.
```

`Box<T>` định nghĩa một kiểu generic mới với tên `T`. Trong `Box<?>`, dấu `?` không định nghĩa tham số: nó chỉ nói rằng tham số thực tế của đối tượng `Box` có một kiểu nào đó nhưng tham chiếu hiện tại không biết kiểu đó.

Cú pháp tổng quát:

```text
Khai báo kiểu/phương thức generic:
  class Box<T> { ... }
  static <T> T identity(T value) { ... }

Sử dụng kiểu generic:
  Box<String> b1;      // Đối số kiểu cụ thể.
  Box<?> b2;           // Wildcard không giới hạn.
  Box<? extends Number> b3;  // Wildcard cận trên.
  Box<? super Integer> b4;   // Wildcard cận dưới.
```

**Quy tắc nhớ nhanh:** Nếu một chỗ cần **tên kiểu hoặc tên tham số kiểu** để chương trình tham chiếu lại, `?` không thể thay thế tên đó. Khi một chỗ cần **đối số kiểu của một kiểu generic được sử dụng**, wildcard có thể hợp lệ, nhưng vẫn có những hạn chế theo ngữ cảnh được trình bày dưới đây.

## 3. Không được dùng `?` để khai báo tham số kiểu

### 3.1. Khai báo class, interface, record và generic method

Các khai báo sau không hợp lệ:

```java
// class Box<?> { }                        // SAI
// interface Repository<?> { }             // SAI
// record Pair<?>(Object left, Object right) { }  // SAI
// static <?> void print(Object value) { }  // SAI
```

Danh sách tham số kiểu của khai báo phải có một định danh như `T`, `E`, `K`, `V` hoặc một tên khác hợp lệ. `?` không phải định danh và không thể đóng vai trò là type parameter.

Cách sửa:

```java
class Box<T> {
    T value;
}

interface Repository<E> {
    E find();
}

record Pair<T>(T left, T right) {}

class GenericMethods {
    static <T> T identity(T value) {
        return value;
    }
}
```

Đây là các khai báo minh họa, không phải một chương trình có `main`. Wildcard vẫn có thể **xuất hiện bên trong kiểu tham số hoặc kiểu trả về** của một phương thức, chẳng hạn `void print(List<?> values)`; điều bị cấm là dùng `?` **làm tên tham số kiểu**.

### 3.2. Không thể viết `?` độc lập như tên kiểu biến hay kiểu trả về

```java
// ? item;                    // SAI
// static ? first() { ... }   // SAI
// void accept(? value) { }   // SAI
// ?[] elements;              // SAI
```

`?` không phải một type name đứng riêng. Hãy bọc nó trong một kiểu generic ở nơi cho phép type argument:

```java
Object item;
List<?> elements;

static Object first(List<?> values) {
    return values.get(0);
}

static void accept(List<?> values) {
    System.out.println(values.size());
}
```

Lưu ý: `List<?>` là **một kiểu dùng được trong chữ ký**, còn riêng dấu `?` thì không.

### 3.3. Không đặt dấu `?` độc lập sau `extends` ở khai báo type parameter

```java
// class NumberBox<T extends ?> { }  // SAI: ? không phải một kiểu làm cận.
```

Khi muốn giới hạn `T` phải là kiểu số:

```java
class NumberBox<T extends Number> { }
```

Trong khi đó, wildcard hoàn toàn có thể nằm **bên trong một kiểu dùng làm cận**:

```java
class HasComparable<T extends Comparable<?>> { }
```

Đây là một điểm khác biệt quan trọng: `Comparable<?>` là một kiểu tham số hóa hợp lệ trong mệnh đề cận; bản thân dấu `?` đứng lẻ sau `extends` không phải kiểu hợp lệ.

Tương tự, cú pháp `<T super Number>` không phải cách khai báo cận dưới cho type parameter. Từ khóa `super` để diễn đạt cận dưới là một phần của wildcard type argument, ví dụ `List<? super Number>`.

## 4. Không dùng wildcard trong kiểu cha hoặc interface cha trực tiếp

Khi định nghĩa một class hoặc interface có quan hệ kế thừa, phần kiểu generic trực tiếp ở `extends`/`implements` **không được chứa wildcard**.

Các ví dụ không hợp lệ:

```java
class Parent<T> { }

interface Handler<T> {
    void handle(T value);
}

// class Child extends Parent<?> { }   // SAI
// class X implements Handler<?> {      // SAI
//     public void handle(Object x) {}
// }
// interface ChildHandler extends Handler<?> { }  // SAI
```

Khi thực hiện kế thừa, Java cần một parameterization phù hợp để định nghĩa hợp đồng thành viên được kế thừa. Wildcard diễn tả một loại đối số chưa biết ở **nơi sử dụng tham chiếu**, không phải một đối số thích hợp cho quan hệ cha trực tiếp được khai báo.

Các phương án đúng:

```java
class StringChild extends Parent<String> { }

class GenericChild<T> extends Parent<T> { }

class StringHandler implements Handler<String> {
    @Override
    public void handle(String value) {
        System.out.println(value);
    }
}

interface GenericHandler<T> extends Handler<T> { }
```

Bạn **vẫn được khai báo biến** `Parent<?>` để tham chiếu đến `Parent<String>` hoặc `Parent<Integer>`:

```java
Parent<?> view = new StringChild(); // HỢP LỆ.
```

Vấn đề nằm ở vị trí `extends/implements` của khai báo kiểu; không phải toàn bộ các kiểu `Parent<?>` đều bị cấm.

## 5. Không dùng wildcard làm type argument trong biểu thức `new` khởi tạo đối tượng

Hãy phân biệt **kiểu tham chiếu bên trái** và **kiểu đối tượng cần tạo bên phải**:

```java
List<?> view = new ArrayList<String>();  // HỢP LỆ
List<?> inferred = new ArrayList<>();     // HỢP LỆ: diamond
// List<?> wrong = new ArrayList<?>();    // SAI
```

Với biểu thức khởi tạo đối tượng `new ArrayList<...>()`, kiểu cần tạo phải được xác định bằng đối số kiểu cụ thể hoặc để trình biên dịch suy luận qua `<>` (diamond). Java không cho viết wildcard type argument trực tiếp ở vị trí đó.

`<>` và `<?>` khác nhau hoàn toàn:

| Cú pháp | Ý nghĩa | Có thể dùng ngay sau `new ArrayList`? |
| --- | --- | --- |
| `<String>` | Chọn `String` làm đối số kiểu | Có |
| `<>` | Diamond: yêu cầu trình biên dịch suy luận kiểu | Có |
| `<?>` | Tham chiếu đến đối số kiểu chưa biết | Không |
| `<? extends Number>` | Wildcard cận trên | Không |
| `<? super Integer>` | Wildcard cận dưới | Không |

Ví dụ sửa lỗi:

```java
// List<?> a = new ArrayList<?>();           // SAI
List<?> a = new ArrayList<Object>();         // ĐÚNG
List<?> b = new ArrayList<>();               // ĐÚNG
List<? extends Number> c = new ArrayList<Integer>(); // ĐÚNG
```

Đừng hiểu `List<?> view = new ArrayList<>()` là đối tượng thực sự được khởi tạo với kiểu `?`. Biến `view` có một kiểu nhìn ra bên ngoài là wildcard, trong khi việc khởi tạo vẫn chọn được một đối số kiểu cụ thể qua cơ chế suy luận.

Quy tắc này cũng áp dụng cho đối số kiểu tường minh của generic constructor trong biểu thức `new`; không dùng `?` để yêu cầu Java suy luận. Hãy để trình biên dịch suy luận hoặc truyền kiểu cụ thể phù hợp.

## 6. Không dùng wildcard ở danh sách type argument tường minh khi gọi generic method/constructor

Giả sử có:

```java
static <T> T identity(T value) {
    return value;
}
```

Lời gọi generic method được phép để trình biên dịch suy luận `T`, hoặc nêu **kiểu cụ thể**:

```java
String a = identity("Java");
String b = MyClass.<String>identity("Generics");
```

Trong đó `MyClass` tượng trưng cho class chứa phương thức `static identity`; đoạn code này chỉ minh họa cú pháp gọi, chưa phải file độc lập.

Wildcard không được viết vào danh sách type argument tường minh của lời gọi:

```java
// String x = MyClass.<?>identity("Java"); // SAI
// List<?> y = Collections.<?>emptyList();  // SAI
```

Có thể sửa bằng cách bỏ phần type argument tường minh hoặc chọn kiểu cụ thể:

```java
List<?> a = java.util.Collections.emptyList();
List<?> b = java.util.Collections.<String>emptyList();
```

Quy tắc tương tự tồn tại ở type argument tường minh của lệnh gọi constructor `this(...)` hoặc `super(...)`, và đối số kiểu tường minh trong method reference: wildcard không đóng vai trò loại kiểu được truyền tường minh cho một lời gọi generic. **`List<?> result` là hợp lệ**, nhưng **`Collections.<?>emptyList()` không hợp lệ**; hai vị trí mang ý nghĩa khác nhau.

## 7. Không dùng wildcard trong kiểu nhận dạng member của method reference

Method reference dạng `TypeName::method` có thể dùng với class/interface generic, nhưng **kiểu tham chiếu dùng để tìm phương thức** không được chứa wildcard type argument.

Ví dụ cách dùng thông thường:

```java
java.util.function.ToIntFunction<List<String>> size = List::size;
int count = size.applyAsInt(List.of("a", "b"));
```

Biểu thức `List::size` hợp lệ; đoạn sau minh họa trường hợp cấm:

```java
// ToIntFunction<List<String>> wrong = List<?>::size; // SAI
```

Nếu cần giữ danh sách với kiểu phần tử không xác định, hãy dùng lambda nhận tham số đã được định kiểu rõ ràng hoặc chọn method reference hợp lệ:

```java
java.util.function.ToIntFunction<List<?>> countAny = values -> values.size();
```

Không nên thêm `?` vào chữ ký type reference chỉ để thể hiện rằng mã không phụ thuộc kiểu phần tử. Lambda và khai báo biến kiểu `List<?>` đã truyền tải điều đó.

## 8. Hai ngữ cảnh đặc biệt: chỉ wildcard không giới hạn là an toàn

Một số vị trí **cho phép `List<?>`**, nhưng không cho phép biểu thức dùng một generic type không reifiable như `List<String>` hay `List<? extends Number>` trong những ví dụ phổ biến từ biến `Object`. Lý do là **type erasure**: JVM không lưu thông tin kiểu phần tử generic để kiểm tra chúng ở runtime.

### 8.1. `instanceof`

```java
Object value = List.of("a", "b");

boolean valid = value instanceof List<?>; // HỢP LỆ
// boolean wrong1 = value instanceof List<String>; // SAI
// boolean wrong2 = value instanceof List<? extends Number>; // SAI
```

`List<?>` là một **reifiable type** (kiểu có đủ thông tin cần thiết cho kiểm tra kiểu ở runtime); do đó `instanceof List<?>` kiểm tra được liệu đối tượng là một `List` hay không. Nhưng phép kiểm tra đó **không** chứng minh phần tử bên trong có kiểu cụ thể nào.

Ví dụ pattern matching trong Java 17 cũng hợp lệ:

```java
Object input = List.of("one");
if (input instanceof List<?> list) {
    System.out.println(list.size()); // 1
}
```

Một số biến thể `instanceof` từ JDK mới còn có quy tắc kiểm tra khả năng chuyển kiểu theo kiểu tĩnh của biểu thức. Trong tình huống phổ biến `Object input`, hãy chọn `List<?>` và kiểm tra phần tử riêng nếu cần, thay vì trông chờ JVM xác thực `List<String>`.

### 8.2. Tạo mảng

Không phải mọi mảng có thành phần generic đều bị cấm tạo. Khi thành phần là kiểu reifiable `List<?>`, cú pháp sau hợp lệ:

```java
List<?>[] groups = new List<?>[2]; // HỢP LỆ
groups[0] = List.of(1, 2);
groups[1] = List.of("A", "B");
```

Đối chiếu:

```java
// List<String>[] a = new List<String>[2];              // SAI
// List<? extends Number>[] b = new List<? extends Number>[2]; // SAI
// List<? super Integer>[] c = new List<? super Integer>[2];   // SAI
```

`List<String>` và các tham số hóa có wildcard giới hạn không thuộc loại reifiable dùng làm kiểu thành phần trong **biểu thức tạo mảng**. Ngược lại `List<?>` reifiable và được phép trong `new List<?>[2]`. Khai báo một **tham chiếu** `List<String>[]` có thể viết về mặt cú pháp, nhưng khác với việc trực tiếp tạo ra mảng đó.

Điều được cho phép cũng không có nghĩa array là cấu trúc tốt nhất cho dữ liệu generic. Mảng có covariance và kiểm tra kiểu runtime khác với generics; với dữ liệu tập hợp, `List<List<?>>` thường dễ dùng và tránh nhiều vấn đề liên quan đến mảng.

## 9. Không có class literal cho kiểu generic với wildcard

Trong Java, class literal mang dạng `Type.class`, nhưng không thể viết nó dựa trên tham số hóa generic:

```java
Class<?> listClass = List.class;  // HỢP LỆ
Class<?> stringClass = String.class; // HỢP LỆ

// Class<?> wrong1 = List<?>.class;     // SAI
// Class<?> wrong2 = List<String>.class; // SAI
```

Vì type arguments bị xóa ở runtime, `List<String>` và `List<Integer>` không có hai đối tượng `Class` riêng biệt. Hãy dùng `List.class` khi cần đại diện runtime của lớp/interface; biến nhận `Class<?>` lại là một **vị trí sử dụng wildcard hợp lệ**.

Ví dụ:

```java
Class<?> type = java.util.ArrayList.class;
System.out.println(type.getSimpleName()); // ArrayList
```

Khi thực sự phải mô tả đầy đủ `List<String>` ở runtime (ví dụ framework serialization), cần một biểu diễn kiểu khác như `java.lang.reflect.ParameterizedType` hoặc cơ chế type token của thư viện; `List<?>.class` không giải quyết được bài toán này.

## 10. Những vị trí wildcard hoàn toàn được phép

Tránh phản xạ sai kiểu “cứ thấy dấu `?` là lỗi”. Các cách dùng dưới đây đều hợp lệ:

```java
class WildcardUses {
    List<?> field;                           // Field.
    static List<?> shared;                   // Static field.
    List<List<?>> nested;                    // Generic lồng nhau.
    java.util.Map<String, ?> byName;         // Type argument thứ hai.
    Class<?> runtimeType = String.class;     // Class<?>.

    void consume(List<?> values) {           // Tham số.
        for (Object value : values) {
            System.out.println(value);
        }
    }

    List<?> readOnlyView() {                  // Kiểu trả về.
        return List.of(1, 2, 3);
    }
}
```

Ở mỗi ví dụ, `?` nằm **bên trong danh sách đối số kiểu của một kiểu generic đang sử dụng**. Nó không đứng một mình như `? value`, không xuất hiện ở đầu danh sách khai báo type parameter của class và không chiếm chỗ type argument tường minh của biểu thức `new`.

### 10.1. Wildcard không giới hạn không phải raw type

```java
List<?> safeView = List.of("a", "b");  // Generic còn được kiểm tra kiểu.
List raw = List.of("a", "b");         // Raw type, tránh dùng trong code mới.
```

`List<?>` vẫn giữ kiểm tra kiểu: trình biên dịch không cho `safeView.add("x")` vì kiểu phần tử chưa biết. Raw `List` bỏ qua một phần cơ chế generic và dễ dẫn đến unchecked warning. Không dùng raw type làm “cách sửa nhanh” cho wildcard bị cấm.

### 10.2. Lỗi thao tác trên wildcard khác lỗi vị trí cú pháp

```java
List<?> values = new java.util.ArrayList<String>();
Object item = values.isEmpty() ? null : values.get(0); // HỢP LỆ
// values.add("hello"); // KHÔNG HỢP LỆ về kiểm tra kiểu phần tử.
```

Trong đoạn này, `List<?>` **đặt đúng vị trí và hoàn toàn hợp lệ về cú pháp**; vấn đề là lệnh `add("hello")` không bảo đảm an toàn, vì danh sách thực tế có thể có kiểu phần tử khác `String`. `values.add(null)` hợp lệ về kiểm tra kiểu, nhưng implementation bất biến có thể từ chối thay đổi ở runtime.

Hai nhóm lỗi khác nhau dẫn đến cách sửa khác nhau:

- **Vị trí wildcard bị cấm:** đổi cú pháp/kiến trúc khai báo, ví dụ `<T>`, `new ArrayList<>()`, `Parent<String>`.
- **Wildcard ở vị trí hợp lệ nhưng bị hạn chế thao tác:** đổi cách dùng API, ví dụ chỉ đọc dưới dạng `Object`, thêm type parameter `<T>` để giữ liên hệ kiểu, hoặc chọn `? super T` nếu API cần nhận `T`.

## 11. Chương trình Java 17+ đầy đủ, chạy được

Lưu đoạn sau vào tệp **`WildcardSyntaxDemo.java`**:

```java
import java.util.List;

public class WildcardSyntaxDemo {
    static void show(List<?> values) {
        for (Object value : values) {
            System.out.print(value + " ");
        }
        System.out.println();
    }

    static <T> T first(List<T> values) {
        return values.get(0);
    }

    public static void main(String[] args) {
        List<?> words = List.of("Java", "Generics");
        Object firstWord = words.get(0);

        List<?>[] groups = new List<?>[2];
        groups[0] = words;
        groups[1] = List.of(1, 2);
        Object firstNumber = groups[1].get(0);

        Object candidate = groups[0];
        boolean isList = candidate instanceof List<?>;
        Class<?> type = List.class;

        System.out.println("word=" + firstWord);
        System.out.println("number=" + firstNumber);
        System.out.println("groups=" + groups.length);
        System.out.println("isList=" + isList);
        System.out.println("class=" + type.getSimpleName());
        System.out.println("first=" + first(List.of("OK")));
        show(words);
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac WildcardSyntaxDemo.java
java WildcardSyntaxDemo
```

Stdout:

```text
word=Java
number=1
groups=2
isList=true
class=List
first=OK
Java Generics 
```

Dòng cuối có **một khoảng trắng ngay sau chữ `Generics`** vì `System.out.print(value + " ")` được gọi cho từng phần tử trước `println()`. Chương trình cho thấy `List<?>` có thể làm tham số, làm kiểu biến, làm thành phần mảng, và được dùng trong `instanceof`. Đồng thời `<T>` là cách khai báo generic method; `Class<?> type = List.class` phân biệt kiểu biến với class literal.

## 12. Bảng tra nhanh: đúng, sai và cách thay thế

| Vị trí | Ví dụ | Kết quả | Cách viết đúng / lý do |
| --- | --- | --- | --- |
| Khai báo type parameter của class | `class Box<?> {}` | Sai | `class Box<T> {}` |
| Khai báo type parameter của method | `<?> void run()` | Sai | `<T> void run()` khi thực sự cần `T` |
| Kiểu biến độc lập | `? x;` | Sai | `Object x;` hoặc `List<?> x;` |
| Kiểu tham số độc lập | `void f(? x)` | Sai | `void f(Object x)` hoặc `void f(List<?> x)` |
| Cận type parameter | `<T extends ?>` | Sai | `<T extends Number>` |
| Wildcard nằm trong cận hợp lệ | `<T extends Comparable<?>>` | Đúng | `Comparable<?>` là một kiểu được dùng làm bound |
| Kiểu cha trực tiếp | `extends Parent<?>` | Sai | `extends Parent<T>` hoặc `extends Parent<String>` |
| Interface cha trực tiếp | `implements Handler<?>` | Sai | `implements Handler<String>` |
| Biểu thức khởi tạo object | `new ArrayList<?>()` | Sai | `new ArrayList<>()` hoặc `new ArrayList<String>()` |
| Type argument của lời gọi generic method | `Collections.<?>emptyList()` | Sai | `Collections.emptyList()` |
| Receiver type của method reference | `List<?>::size` | Sai | `List::size` hoặc lambda |
| Class literal | `List<?>.class` | Sai | `List.class` |
| `instanceof` với wildcard không giới hạn | `obj instanceof List<?>` | Đúng | Chỉ kiểm tra danh sách, không kiểm tra kiểu phần tử |
| `instanceof` từ biến `Object` với kiểu phần tử cụ thể | `obj instanceof List<String>` | Sai | Kiểm tra `List<?>` rồi kiểm tra dữ liệu |
| Tạo mảng wildcard không giới hạn | `new List<?>[2]` | Đúng | Thành phần `List<?>` reifiable |
| Tạo mảng generic có đối số cụ thể | `new List<String>[2]` | Sai | Dùng `new List<?>[2]` hoặc `List<List<String>>` |
| Kiểu field | `List<?> field;` | Đúng | `?` là type argument |
| Kiểu trả về phương thức | `List<?> get()` | Đúng | `?` nằm trong `List<...>` |
| Generic lồng nhau | `Map<String, List<?>> data;` | Đúng | Wildcard là đối số kiểu của `List` bên trong |

Lưu ý: bảng giả định các class/interface và biến liên quan đã được khai báo, import thích hợp. Những dòng `Sai` là các mảnh cú pháp để phân tích, không đưa trực tiếp vào một tệp Java đang cần biên dịch.

## 13. Bài tập phân loại và giải thích

Giả sử có `import java.util.*;` và đã định nghĩa `class Holder<T> {}`.

### Câu 1. Có được khai báo như sau không?

```java
// class Strange<?> {}  // (a)
// Holder<?> h = new Holder<String>(); // (b)
// Holder<?> h = new Holder<?>(); // (c)
```

**Đáp án:** (a) sai vì dấu `?` không thể là tên type parameter; (b) đúng vì wildcard là type argument ở kiểu tham chiếu; (c) sai vì wildcard không được dùng làm đối số kiểu trong biểu thức khởi tạo đối tượng. Thay (c) bằng `new Holder<>()` hoặc `new Holder<String>()`.

### Câu 2. Vì sao cách này sai?

```java
// class TextHolder extends Holder<?> {}
```

**Đáp án:** `extends` chỉ định kiểu cha trực tiếp. Trong ngữ cảnh này wildcard type argument bị cấm. Chọn kiểu cụ thể (`extends Holder<String>`) hoặc truyền theo type parameter của lớp con (`class TextHolder<T> extends Holder<T> {}`).

### Câu 3. Các phép kiểm tra hoặc khởi tạo mảng này có hợp lệ không?

```java
Object o = List.of(42);
boolean a = o instanceof List<?>; // (a)
// boolean b = o instanceof List<Integer>; // (b)
List<?>[] c = new List<?>[3]; // (c)
// List<Integer>[] d = new List<Integer>[3]; // (d)
```

**Đáp án:** (a) hợp lệ; (b) không hợp lệ với biến `Object` vì không thể kiểm tra type argument `Integer` ở runtime; (c) hợp lệ vì `List<?>` reifiable; (d) không hợp lệ vì generic array creation có thành phần `List<Integer>` không reifiable.

### Câu 4. Cách gọi phương thức sau sai ở đâu?

```java
// List<?> result = Collections.<?>emptyList();
```

**Đáp án:** Wildcard được phép ở kiểu biến bên trái, nhưng bị cấm trong danh sách type argument tường minh của generic method invocation bên phải. Sửa thành `List<?> result = Collections.emptyList();` hoặc `List<?> result = Collections.<String>emptyList();`.

### Câu 5. Wildcard có thể được đặt trong một bound hay không?

```java
class Sample<T extends Comparable<?>> {}
```

**Đáp án:** Có. `T` là tên type parameter, còn `Comparable<?>` là **một kiểu được sử dụng làm cận**. Đây không phải cú pháp sai `T extends ?`. Khi cần bảo đảm `T` so sánh với chính `T`, mô hình `T extends Comparable<T>` thường diễn đạt quan hệ chặt chẽ hơn.

## 14. Quy trình xử lý khi javac báo lỗi có dấu `?`

1. **Đọc vị trí lỗi trước:** dấu `?` nằm trong `List<?>` hay đang đứng riêng như `? x`?
2. **Xác định ngữ cảnh:** đang khai báo tham số kiểu, khai báo cha, tạo đối tượng, gọi generic method, viết method reference, `instanceof` hay tạo mảng?
3. **Nếu đang đặt tên type parameter**, thay `?` bằng `T`/`E`... và khai báo phạm vi sử dụng.
4. **Nếu đang dùng `new`**, chọn một type argument cụ thể hoặc diamond `<>`.
5. **Nếu đang khai báo kiểu cha trực tiếp**, nêu kiểu cụ thể hoặc tiếp tục truyền type parameter từ lớp con.
6. **Nếu là type argument tường minh của lời gọi**, thường bỏ phần `<...>` để suy luận, hoặc nêu kiểu cụ thể.
7. **Nếu là `instanceof` hoặc tạo mảng**, kiểm tra tính reifiable. `List<?>` khác `List<String>` và `List<? extends Number>`.
8. **Nếu wildcard đúng chỗ nhưng thao tác `add`, `set` vẫn lỗi**, đây là bài toán bảo đảm kiểu dữ liệu/capture, không phải cú pháp. Xem xét `<T>` hoặc wildcard cận phù hợp.

## 15. Nguồn đặc tả và kết luận

- [Java Language Specification 17, §4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): wildcard là type argument và ngữ nghĩa cận.
- [Java Language Specification 17, §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): vì sao `List<?>` khác `List<String>` khi kiểm tra runtime.
- [Java Language Specification 17, §4.11 — Where Types Are Used](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.11): tổng hợp các ngữ cảnh cấm wildcard và các ngữ cảnh đòi hỏi reifiable type.
- [Java Language Specification 17, §15.9 — Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9): quy tắc về type argument và diamond trong `new`.

**Ghi nhớ:** `?` dùng để biểu thị *đối số kiểu chưa biết* trong một kiểu generic được sử dụng, chẳng hạn `List<?>`; nó không thay thế được type parameter `T`, không làm kiểu cha trực tiếp, không nằm trong `new ArrayList<?>()` hay `Collections.<?>emptyList()`. Riêng `List<?>` là kiểu reifiable nên `instanceof List<?>` và `new List<?>[n]` hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)
