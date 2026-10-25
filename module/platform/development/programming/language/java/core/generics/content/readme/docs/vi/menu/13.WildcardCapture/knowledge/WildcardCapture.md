<a id="back-to-top"></a>

# Bắt giữ wildcard

## Menu
- [Phép chuyển đổi bắt giữ (capture conversion) giải quyết vấn đề gì](#wildcard-capture-001)
- [Đọc thông báo CAP#1 và capture of ?](#wildcard-capture-002)
- [Đặt tên kiểu chưa biết bằng phương thức hỗ trợ generic](#wildcard-capture-003)
- [Hoán đổi phần tử của List&lt;?&gt; bằng phương thức hỗ trợ](#wildcard-capture-004)
- [Vì sao hai wildcard giống cách viết vẫn có thể là hai kiểu khác nhau](#wildcard-capture-005)
- [Giới hạn trên và dưới của kiểu được bắt giữ](#wildcard-capture-006)
- [Wildcard lồng nhau và giới hạn của phép bắt giữ](#wildcard-capture-007)
- [Phân biệt giải quyết lỗi bằng capture với ép kiểu không kiểm tra](#wildcard-capture-008)

## <a id="wildcard-capture-001">Phép chuyển đổi bắt giữ (capture conversion) giải quyết vấn đề gì</a>

<details>
<summary>Xem chi tiết</summary>

# Phép chuyển đổi bắt giữ (capture conversion) giải quyết vấn đề gì trong Java Generics?

**Capture conversion** là cơ chế của trình biên dịch Java giúp xử lý một kiểu generic có **wildcard** (`?`, `? extends T`, `? super T`) như một **kiểu chưa biết nhưng xác định trong một ngữ cảnh kiểm tra kiểu**. Trình biên dịch tạm đặt một tên kiểu nội bộ cho dấu `?`, thường hiển thị trong thông báo lỗi dưới dạng `capture#1 of ?` hoặc `CAP#1`. Nhờ vậy, phương thức generic có thể duy trì mối liên hệ giữa **phần tử lấy ra** và **phần tử đưa trở lại chính collection ấy**, dù mã gọi không biết đối số kiểu thực sự.

Vấn đề capture conversion giải quyết không phải “biến `List<?>` thành `List<Object>`”. Nó giải quyết việc **giữ một kiểu ẩn nhất quán trong một lần xử lý**, cho phép các thao tác thật sự an toàn với một collection có kiểu phần tử chưa biết.

Một ví dụ cốt lõi:

```java
static void swap(List<?> items, int i, int j) {
    swapCaptured(items, i, j);
}

private static <T> void swapCaptured(List<T> items, int i, int j) {
    T saved = items.get(i);
    items.set(i, items.get(j));
    items.set(j, saved);
}
```

`swap` nhận được `List<String>`, `List<Integer>`, `List<Number>` và nhiều kiểu danh sách khác. `swapCaptured` sử dụng **một biến kiểu `T` cho toàn bộ thao tác trong một danh sách**, nên đọc rồi ghi lại một phần tử đúng loại mà không cần ép kiểu không an toàn.

## 1. Bài toán: Java biết “có một kiểu” nhưng chưa biết tên kiểu đó

Ví dụ:

```java
List<Integer> integers = new ArrayList<>(List.of(10, 20));
List<?> unknown = integers;
```

Ở runtime, danh sách chứa các đối tượng `Integer`. Tuy nhiên, theo kiểu khai báo `List<?>` của `unknown`, trình biên dịch chỉ biết đối số kiểu là **một kiểu tham chiếu chưa xác định**.

Các thao tác đọc đơn giản vẫn an toàn:

```java
Object element = unknown.get(0);  // Hợp lệ.
int length = unknown.size();      // Hợp lệ.
```

Ngược lại, hai thao tác này bị từ chối:

```java
// unknown.add(10);            // LỖI BIÊN DỊCH.
// unknown.set(0, unknown.get(0)); // LỖI BIÊN DỊCH.
```

Lệnh `add(10)` không an toàn nói chung, vì `unknown` có thể trỏ tới `List<String>`. Nhưng `set(0, unknown.get(0))` lại có vẻ hợp lý: lấy phần tử ở chính danh sách đó rồi đặt nó trở lại cùng vị trí, không thể đưa một kiểu khác vào.

**Vấn đề nằm ở thông tin kiểu mà biểu thức cung cấp cho trình biên dịch.** Qua `List<?>`, kết quả `get(0)` có kiểu phần tử ẩn; trong kiểm tra lời gọi `set`, Java không tự đồng nhất kiểu capture ở hai lần xuất hiện wildcard thành một kiểu được đặt tên và sử dụng ổn định. Vì thế, biểu thức `unknown.set(0, unknown.get(0))` không biên dịch, dù ý tưởng thao tác là an toàn khi hai lần truy cập thật sự thuộc cùng một danh sách.

Cần một cách **đặt tên cho kiểu phần tử chưa biết**, để chứng minh giá trị đọc ra có thể ghi trở lại đúng nơi đã tạo ra nó. Capture conversion kết hợp generic helper làm được điều này.

## 2. Capture conversion là gì?

Xét kiểu:

```java
List<?> values;
```

Theo cách hình dung đơn giản, khi Java cần kiểm tra một biểu thức kiểu `List<?>`, nó có thể mở wildcard thành một **biến kiểu mới do trình biên dịch sinh ra**:

```text
List<?>            --capture conversion-->  List<CAP#1>

CAP#1 là một kiểu chưa biết, có giới hạn trên là Object.
```

Tên `CAP#1` chỉ để minh họa; người lập trình không khai báo hay tham chiếu trực tiếp được `CAP#1` trong mã nguồn. Thông báo lỗi của các phiên bản `javac` có thể gọi nó là `capture#1 of ?` hoặc dùng số khác.

Điều quan trọng không nằm ở giá trị tên này, mà ở quan hệ kiểu:

- Một phần tử được lấy ra từ `List<CAP#1>` có kiểu `CAP#1`.
- Một phương thức nhận `List<CAP#1>` có thể sử dụng `CAP#1` như **một kiểu thống nhất trong thân phương thức**.
- Vì danh sách có kiểu `List<CAP#1>`, nó chấp nhận phần tử `CAP#1` khi thao tác ghi phù hợp.
- Java không cần biết kiểu thực tế đó là `Integer` hay `String`; các ràng buộc kiểu vẫn đủ để kiểm tra an toàn.

**Capture conversion diễn ra ở compile time.** Nó không thay đổi danh sách ở runtime, không tạo bản sao, không làm mất/đổi kiểu của phần tử và không chuyển `List<Integer>` thành `List<Object>`.

### Quan hệ với các loại wildcard

| Wildcard | Mô hình giới hạn của kiểu capture | Đọc ra an toàn | Thao tác ghi không-null được bảo đảm |
| --- | --- | --- | --- |
| `?` | Một kiểu ẩn có giới hạn trên mặc định `Object` | `Object` | Không có kiểu cụ thể nào thông qua tham chiếu wildcard |
| `? extends Number` | Một kiểu ẩn nằm dưới giới hạn trên `Number` | `Number` | Không có kiểu cụ thể nào thông qua tham chiếu wildcard |
| `? super Integer` | Một kiểu ẩn có giới hạn dưới `Integer`; với `List<E>` giới hạn trên thường là `Object` | `Object` | `Integer` |
| `T` trong `List<T>` | Biến kiểu được đặt tên, có thể dùng lại trong chữ ký/thân phương thức | `T` | `T` |

Đây là mô hình giải thích các trường hợp thông dụng. Quy tắc chính xác về giới hạn còn phụ thuộc ràng buộc của tham số kiểu generic gốc theo Java Language Specification; không nên xem `CAP#1` là một kiểu Java có thể viết trong source code.

## 3. Một wildcard không cho phép tự ý ghi phần tử

Ví dụ phương thức sau cố thêm phần tử vào danh sách bất kỳ:

```java
static void addValue(List<?> items) {
    // items.add("Java"); // LỖI BIÊN DỊCH.
}
```

Nếu `items` là `List<Integer>`, thêm `String` sẽ vi phạm hợp đồng kiểu. Capture conversion **không** hợp pháp hóa câu lệnh này.

Có thể gọi `items.add(null)` xét riêng về kiểm tra kiểu vì `null` tương thích với mọi kiểu tham chiếu. Dù vậy, một danh sách như `List.of(...)` không hỗ trợ chỉnh sửa, và một số implementation có thể không chấp nhận `null`. Phải phân biệt **hợp lệ về kiểu** với **khả năng thao tác lúc chạy**.

`List<?>` đọc ra được `Object`, nhưng không có nghĩa collection này thực sự là `List<Object>`:

```java
List<String> names = new ArrayList<>();
List<?> unknown = names;    // Hợp lệ.
// List<Object> objects = names; // LỖI BIÊN DỊCH.
```

Generics của Java là **bất biến**. Nếu `List<String>` được gán thành `List<Object>`, một đoạn mã có thể chèn một `Integer` vào danh sách chỉ hứa chứa `String`. Wildcard và capture giữ an toàn kiểu mà không phải phá bỏ quy tắc đó.

## 4. Lỗi “capture of ?” xuất hiện từ đâu?

Một ví dụ tiêu biểu:

```java
static void replaceWithItself(List<?> list) {
    list.set(0, list.get(0)); // LỖI BIÊN DỊCH.
}
```

Đây là **đoạn mã cố tình không biên dịch**. Khi biên dịch, `javac` có thể nhắc đến một hoặc nhiều kiểu `capture#... of ?` hay thông báo `Object cannot be converted to capture#...`. Nội dung và số thứ tự cụ thể của thông báo phụ thuộc trình biên dịch.

Tham số đầu vào được khai báo `List<?>`. Lời gọi `set(int, E)` cần một phần tử đúng kiểu `E` của danh sách. Biểu thức `get` sử dụng một lần kiểm tra kiểu/capture, trong khi vị trí tham số của `set` có thể dùng một capture khác. Trình biên dịch không có một **biến kiểu được đặt tên** nối hai thao tác đó trong một hợp đồng generic ổn định.

Vì vậy, thông báo “capture of ?” thường biểu thị: **trình biên dịch đã mở wildcard thành một kiểu ẩn nhưng các ràng buộc hiện có không đủ để chứng minh phép gán hoặc lời gọi là an toàn**.

Phải phân biệt:

- **Lỗi biểu diễn kiểu:** thao tác vốn có thể an toàn (như hoán đổi phần tử trong cùng danh sách), nhưng chữ ký chỉ dùng wildcard nên thiếu quan hệ kiểu rõ ràng.
- **Lỗi thiết kế thật:** thao tác đưa phần tử từ `List<String>` sang `List<Integer>` mà không có quan hệ tương thích. Không có capture helper nào biến việc ghi sai kiểu thành đúng.

## 5. Generic helper “đặt tên” một kiểu đã có thật

Giả sử API ngoài cần nhận mọi danh sách:

```java
static void preserveFirst(List<?> values) {
    preserveCaptured(values);
}

private static <E> void preserveCaptured(List<E> values) {
    if (!values.isEmpty()) {
        E first = values.get(0);
        values.set(0, first);
    }
}
```

Đây là **các method minh họa** cần nằm trong class có import `java.util.List`. Bên ngoài, `?` không chỉ ra tên element type. Khi truyền `values` vào helper, compiler capture wildcard và suy luận một `E` phù hợp; `get` lẫn `set` bên trong cùng dùng `E`. Không có raw type hay unchecked cast.

Ví dụ chỉ ghi lại giá trị cũ để cô lập cơ chế: **kiểu phần tử không cần biết tên nhưng phải nhất quán trong một lời gọi helper**. Nếu list không hỗ trợ `set`, chương trình vẫn có thể ném `UnsupportedOperationException`. Các biến thể ứng dụng như swap, nhân bản phần tử hoặc thao tác với bounds cần xét thêm index, mutability và ranh giới kiểu; capture không cho ghi một giá trị bên ngoài tùy ý.

## 6. Khi nào cần helper, khi nào không?

Không phải mọi phép đọc `List<?>` đều cần capture helper.

Đọc bình thường:

```java
static void show(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Mã này hợp lệ vì đầu ra của `get` hoặc vòng lặp có thể xem như `Object`.

Một generic method một tham số cũng có thể nhận `List<?>`:

```java
static <T> T first(List<T> values) {
    return values.get(0);
}

static void printFirst(List<?> unknown) {
    Object firstValue = first(unknown);
    System.out.println(firstValue);
}
```

Ở lời gọi `first(unknown)`, compiler có thể capture wildcard để tìm một `T` thích hợp. Kiểu giá trị trả về được người gọi dùng an toàn dưới dạng `Object`. Phương thức `first` không cần biết `T` cụ thể và không thêm phần tử sai kiểu.

**Cần generic helper nhất khi nhiều thao tác nội bộ phải dùng lại cùng kiểu ẩn**, chẳng hạn vừa đọc vừa ghi lại phần tử trong một collection. Nếu chỉ duyệt và gọi `toString()`, `size()` hoặc `contains(Object)`, thêm helper có thể làm mã phức tạp không cần thiết.

## 7. Hai wildcard có thể là hai kiểu khác nhau

Đây là giới hạn rất quan trọng của capture conversion:

```java
static <T> void requireSameType(List<T> left, List<T> right) {
    // Cả hai tham số phải khớp cùng một T.
}

static void cannotProveSame(List<?> first, List<?> second) {
    requireSameType(first, second); // LỖI BIÊN DỊCH.
}
```

`first` có thể là `List<Integer>`, còn `second` có thể là `List<String>`. Từng `?` có thể tạo một capture khác nhau. Không thể suy ra `T` duy nhất đáp ứng cả hai tham số `List<T>`.

Đặc biệt, **ngay cả truyền cùng một biến hai lần cũng có thể bị từ chối**:

```java
static void stillCannotProveSame(List<?> items) {
    requireSameType(items, items); // LỖI BIÊN DỊCH.
}
```

Hai lần biểu thức `items` được dùng tại hai vị trí đối số có thể trải qua capture conversion riêng biệt. Hệ kiểu không tự chứng minh hai capture là **một kiểu được đặt tên chung**, dù con người biết cùng một object được truyền hai lần.

Ngược lại:

```java
static void worksWithKnownType(List<Integer> items) {
    requireSameType(items, items); // Hợp lệ: T = Integer.
}
```

Ở đây tham số kiểu `Integer` đã được biết từ chữ ký. Nếu API thực sự yêu cầu nhiều tham số cùng một kiểu, hãy truyền thông tin ấy qua biến kiểu generic `<T>` ngay từ chữ ký phù hợp, thay vì xóa thành các `List<?>` độc lập.

### Vì sao helper một tham số hoạt động nhưng helper hai tham số thất bại?

- `swapCaptured(List<T>)` cần **một kiểu ẩn** để đọc và ghi trong cùng một danh sách. Một capture có thể được suy luận thành `T`.
- `requireSameType(List<T>, List<T>)` yêu cầu **hai đối số** có cùng `T`. Từ hai lần sử dụng `List<?>`, trình biên dịch không thể cam kết hai capture là một.

Đó là ranh giới giữa **đặt tên kiểu chưa biết** và **tự tạo ra quan hệ kiểu không có trong chữ ký**.

## 8. Capture conversion với wildcard có giới hạn

Cơ chế capture cũng liên quan đến `? extends T` và `? super T`, không chỉ `?`.

### `? extends Number`: đọc số như `Number`

```java
static double sum(List<? extends Number> source) {
    double result = 0.0;
    for (Number n : source) {
        result += n.doubleValue();
    }
    return result;
}
```

Kiểu phần tử ẩn thỏa giới hạn trên `Number`. Nhờ đó đọc ra như `Number` an toàn, nhưng không được thêm một `Integer` cụ thể vào `source`: nguồn có thể thật sự là `List<Double>`.

Ví dụ phần tử từ chính danh sách có thể được ghi trở lại bằng helper:

```java
static void repeatFirst(List<? extends Number> source) {
    repeatCaptured(source);
}

private static <T extends Number> void repeatCaptured(List<T> source) {
    if (!source.isEmpty()) {
        T value = source.get(0);
        source.add(value);
    }
}
```

Thao tác này chỉ đưa **đúng một phần tử kiểu `T` đang có** vào cùng `List<T>`. Nó an toàn về kiểu, nhưng collection phải hỗ trợ thêm phần tử; nếu là `List.of(1, 2)`, thao tác sẽ ném `UnsupportedOperationException`.

### `? super Integer`: ghi `Integer` vào đích

```java
static void addDefaults(List<? super Integer> target) {
    target.add(10);
    target.add(20);

    Object any = target.get(0);  // Chỉ bảo đảm đọc dưới dạng Object.
    System.out.println(any);
}
```

Với kiểu lower-bounded, đối số kiểu thật có thể là `Integer`, `Number` hoặc `Object`. Đích chắc chắn nhận được `Integer`, nhưng các phần tử hiện có không nhất thiết là `Integer`.

**Capture không làm thay đổi quyền đọc/ghi an toàn do bounds quyết định.** Nó không thể biến `List<? extends Number>` thành đích nhận mọi `Number`, hay biến `List<? super Integer>` thành nguồn chắc chắn chỉ chứa `Integer`.

## 9. Khi cần chuyển phần tử giữa hai danh sách: mô tả quan hệ kiểu

Một helper một tham số giải quyết việc đọc/ghi trong **cùng danh sách**. Nếu muốn **chuyển phần tử giữa hai danh sách khác nhau**, hãy mô tả rõ ràng quan hệ nguồn–đích:

```java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
```

`source` là producer, có thể cung cấp các phần tử dùng như `T`; `destination` là consumer, chấp nhận `T`.

Ví dụ:

```java
List<Integer> integers = List.of(3, 5);
List<Number> numbers = new ArrayList<>();

appendAll(integers, numbers); // Hợp lệ: Integer là Number.
```

Một hàm nhận tùy ý hai `List<?>` không thể có cùng hợp đồng:

```java
static void unsafeAttempt(List<?> destination, List<?> source) {
    // destination.add(source.get(0)); // LỖI BIÊN DỊCH.
}
```

Lý do không phải thiếu thủ thuật capture. Hai danh sách có thể chứa kiểu không liên quan (`String` và `Integer`); API phải thể hiện **quan hệ tương thích thật** nếu muốn chuyển phần tử.

Có thể gọi `appendAll` giữa các danh sách có quan hệ hợp lệ; nhưng không nên cho phép source và destination là cùng một danh sách có thể thay đổi trong lúc duyệt mà không xử lý trường hợp đó. Với `ArrayList`, vừa duyệt vừa thêm thường gây `ConcurrentModificationException`. Khi thiết kế API, có thể quy định hai đối số không được trùng nhau hoặc lấy snapshot phù hợp trước khi thao tác.

## 10. Ví dụ Java 17+ hoàn chỉnh

Chương trình dưới đây minh họa năm ý:

1. `List<?>` nhận danh sách có những kiểu phần tử khác nhau.
2. Phương thức generic một tham số đọc được từ `List<?>` nhờ capture conversion.
3. Helper `<T>` hoán đổi và sao chép phần tử **trong chính danh sách**.
4. Hai danh sách không có kiểu phần tử giống nhau vẫn được xử lý riêng mà không ép kiểu.
5. Khi cần chuyển phần tử giữa danh sách nguồn và đích, API dùng `extends/super` để biểu diễn quan hệ kiểu.

Lưu thành tệp **`CaptureConversionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CaptureConversionDemo {
    static Object first(List<?> items) {
        return items.get(0);
    }

    static void swap(List<?> items, int a, int b) {
        swapCaptured(items, a, b);
    }

    private static <T> void swapCaptured(List<T> items, int a, int b) {
        T saved = items.get(a);
        items.set(a, items.get(b));
        items.set(b, saved);
    }

    static void duplicateFirst(List<?> items) {
        duplicateCaptured(items);
    }

    private static <T> void duplicateCaptured(List<T> items) {
        if (!items.isEmpty()) {
            items.add(items.get(0));
        }
    }

    static <T> void appendAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T element : source) {
            destination.add(element);
        }
    }

    public static void main(String[] args) {
        List<Integer> ids = new ArrayList<>(List.of(7, 8, 9));
        List<String> tags = new ArrayList<>(List.of("core", "java"));

        List<?> hiddenIds = ids;
        List<?> hiddenTags = tags;

        System.out.println("first ids = " + first(hiddenIds));
        System.out.println("first tags = " + first(hiddenTags));

        swap(hiddenIds, 0, 2);
        swap(hiddenTags, 0, 1);
        duplicateFirst(hiddenIds);

        System.out.println("ids = " + ids);
        System.out.println("tags = " + tags);

        List<Number> destination = new ArrayList<>();
        appendAll(ids, destination);
        System.out.println("destination = " + destination);
    }
}
```

Biên dịch/chạy:

```bash
javac CaptureConversionDemo.java
java CaptureConversionDemo
```

Stdout:

```text
first ids = 7
first tags = core
ids = [9, 8, 7, 9]
tags = [java, core]
destination = [9, 8, 7, 9]
```

Giải thích:

- `hiddenIds` trỏ tới `List<Integer>` và `hiddenTags` trỏ tới `List<String>`; cả hai đều dùng được với `List<?>`.
- `first` chỉ lấy giá trị như `Object` nên không cần quyền ghi.
- `swapCaptured` giữ một `T` cho từng lời gọi, nên `Integer` chỉ được hoán đổi với `Integer` và `String` chỉ với `String`.
- `duplicateCaptured` lấy phần tử đầu kiểu `T` rồi thêm chính phần tử đó vào `List<T>`. `ids` có thêm `9`, còn `tags` không thay đổi cấu trúc sau `swap`.
- `appendAll` đọc `Integer` từ `ids` rồi đưa vào danh sách đích `List<Number>`. Việc này khác với một phép gán sai kiểu `List<Number> x = ids`.

Chương trình dùng `ArrayList` có thể sửa đổi; nếu thay bằng danh sách được tạo bởi `List.of(...)` ở những chỗ `swap`/`duplicateFirst` thay đổi phần tử, phải xét các ngoại lệ sửa đổi runtime.

## 11. Capture conversion khác gì type inference và type erasure?

Ba khái niệm này có liên quan nhưng không đồng nghĩa.

| Cơ chế | Xảy ra chủ yếu ở đâu? | Mục đích |
| --- | --- | --- |
| **Capture conversion** | Trình biên dịch, khi xử lý kiểu chứa wildcard | Mở `?` thành một biến kiểu ẩn mới với ràng buộc thích hợp |
| **Type inference** | Trình biên dịch, khi suy luận tham số kiểu của lời gọi generic | Tìm `T` phù hợp từ đối số, ràng buộc và ngữ cảnh; có thể làm việc cùng capture |
| **Type erasure** | Quá trình biên dịch sang bytecode | Biểu diễn generic ở runtime chủ yếu qua kiểu xóa và các cast/bridge thích hợp |

Một lời gọi `swapCaptured(items)` với `items` thuộc `List<?>` thường cần cả **capture conversion** (một kiểu ẩn cho dấu `?`) và **type inference** (suy luận `T` trong lời gọi helper). Sau khi biên dịch, việc có một biến kiểu `T` tạm thời không tạo ra một lớp runtime mới có tên `CAP#...`.

Ví dụ sau **không** dùng capture để kiểm tra phần tử runtime:

```java
// Không hợp lệ: không thể kiểm tra đối số kiểu Integer bằng instanceof.
// if (unknown instanceof List<Integer>) { ... }
```

Tương tự, một unchecked cast có thể làm trình biên dịch bớt cảnh báo, nhưng không chứng minh hai wildcard là cùng kiểu và không khiến thao tác ghi sai kiểu trở nên an toàn.

## 12. Những giới hạn cần nhớ

### Capture conversion không cấp quyền ghi tùy ý

`List<?>` vẫn không chấp nhận thêm `"text"`; `List<? extends Number>` vẫn không chấp nhận thêm `Integer` tùy ý. Capture chỉ cho phép sử dụng **phần tử đã biết là đúng cùng kiểu `T`** thông qua helper thích hợp.

### Capture không thống nhất hai wildcard độc lập

`List<?> a` và `List<?> b` có thể ứng với hai kiểu khác nhau. Không thể suy ra các phần tử trao đổi được chỉ vì cả hai biến có cùng dạng `List<?>`.

### Capture không khôi phục đối số generic ở runtime

Type erasure vẫn áp dụng. Không có thao tác runtime tự động “đọc được kiểu thật của `List<?>`” từ capture conversion.

### Capture không loại bỏ lỗi runtime khác

Một generic helper an toàn về kiểu vẫn có thể ném `IndexOutOfBoundsException` do chỉ số sai, `UnsupportedOperationException` khi danh sách không sửa được, hoặc `NullPointerException` nếu dùng tham chiếu `null` mà không kiểm tra.

### Capture không thay thế thiết kế chữ ký API

Nếu phương thức cần thêm số vào đích, dùng `Collection<? super Integer>` hoặc `Collection<? super T>`. Nếu chỉ lấy số từ nguồn, dùng `Collection<? extends Number>`. Nếu cần ràng buộc nhiều đối số, sử dụng biến kiểu `<T>` thể hiện quan hệ đó. Chỉ dùng capture helper khi một thao tác bên trong thật sự cần đặt tên cho **một kiểu ẩn đã có**.

## 13. Quy trình xử lý lỗi “capture#... of ?”

Khi gặp lỗi biên dịch đề cập `capture of ?`, hãy kiểm tra theo thứ tự:

1. **Tham số nào chứa wildcard?** Ví dụ `List<?>`, `List<? extends Number>` hay `List<? super Integer>`.
2. **Thao tác muốn đọc hay ghi?** Đọc dưới dạng giới hạn trên thường hợp lệ; ghi phải tương thích với kiểu ẩn cụ thể.
3. **Giá trị ghi có xuất phát từ chính đối tượng đó không?** Nếu đọc từ cùng một danh sách rồi dùng lại, một generic helper `<T>` nhận **một `List<T>`** có thể diễn đạt quan hệ an toàn.
4. **Có hai danh sách/wildcard khác nhau không?** Nếu có, không được tự coi chúng cùng kiểu; phải thể hiện quan hệ nguồn–đích hoặc dùng kiểu xác định hơn.
5. **Có đang cố ép kiểu hoặc dùng raw type để lách compiler không?** Hãy sửa chữ ký, loại bỏ cast không kiểm tra và để compiler chứng minh quan hệ trước khi ghi.
6. **Giải pháp có còn hợp đồng runtime không?** Chỉ số hợp lệ, collection mutable và điều kiện aliasing vẫn cần được bảo đảm.

Ví dụ sửa nhanh:

```java
// Sai: không có tên kiểu chung để vừa get vừa set.
// static void wrong(List<?> x) {
//     x.set(0, x.get(0));
// }

// Đúng: helper giữ nhất quán T ở bên trong.
static void right(List<?> x) {
    rightCaptured(x);
}

private static <T> void rightCaptured(List<T> x) {
    if (!x.isEmpty()) {
        T value = x.get(0);
        x.set(0, value);
    }
}
```

Các phương thức `right`/`rightCaptured` là đoạn code minh họa để đặt trong một lớp; dòng `wrong` đã comment nhằm chỉ ra lỗi biên dịch. Khi sử dụng, cần import `java.util.List`.

## 14. Bài tập tự kiểm tra

Giả sử đã có:

```java
List<Integer> ints = new ArrayList<>(List.of(1, 2));
List<String> words = new ArrayList<>(List.of("a", "b"));
List<?> a = ints;
List<?> b = words;
```

Đánh giá những câu sau:

| Biểu thức/thao tác | Kết quả | Lý do |
| --- | --- | --- |
| `Object x = a.get(0);` | Hợp lệ | Đọc `List<?>` ra `Object` |
| `a.add(3);` | Lỗi biên dịch | Kiểu phần tử cụ thể chưa biết |
| `a.set(0, a.get(0));` | Lỗi biên dịch | Thiếu một `T` thống nhất giữa hai thao tác qua wildcard |
| `swap(a, 0, 1);` với helper ở phần 10 | Hợp lệ | Java capture một kiểu ẩn cho lời gọi helper |
| `swap(b, 0, 1);` | Hợp lệ | Một capture khác, áp dụng cho danh sách `String` |
| `requireSameType(a, b);` với `<T> void requireSameType(List<T>, List<T>)` | Lỗi biên dịch | Hai capture không có quan hệ bằng nhau |
| `a.add(null);` | Hợp lệ về kiểu | Nhưng còn phụ thuộc việc collection hỗ trợ ghi/null |
| `Object first = first(a);` với `first(List<?>)` ở phần 10 | Hợp lệ | Đọc như `Object` |
| `appendAll(ints, new ArrayList<Number>());` | Hợp lệ | Nguồn `Integer` và đích `Number` tương thích |

Các phép gọi cần phần tử hoặc vị trí hợp lệ đều có thể gặp ngoại lệ nếu collection rỗng; bảng chỉ xét **tính hợp lệ kiểu của Java**.

## Ghi nhớ

- **Capture conversion** “mở” một wildcard thành **kiểu ẩn có giới hạn** để trình biên dịch kiểm tra các phép dùng generic một cách nhất quán.
- Với `List<?>`, ta có thể đọc như `Object` nhưng không thể tự thêm một phần tử không-null tùy ý.
- `list.set(0, list.get(0))` trên `List<?>` thường bị từ chối do thiếu một quan hệ kiểu được đặt tên chung; **generic helper `<T>`** giải quyết trường hợp an toàn này bằng cách nhận một `List<T>`.
- **Một wildcard không tự bằng wildcard khác.** Hai lần dùng `List<?>` có thể tạo capture độc lập, kể cả khi truyền cùng một biến vào hai đối số.
- Capture không thể làm hợp lệ một phép chuyển `String` vào `List<Integer>`, không loại bỏ tính bất biến của Java Generics và không thay đổi kiểu ở runtime.
- Nếu API thật sự liên hệ hai tham số, hãy dùng `<T>` với các giới hạn `? extends T` cho nguồn và `? super T` cho đích để biểu diễn quan hệ an toàn.
- Khi thấy lỗi `capture#... of ?`, kiểm tra **bạn đang cần một kiểu ẩn thống nhất trong cùng collection**, hay đang giả định sai rằng hai kiểu ẩn độc lập là như nhau.

Hiểu capture conversion giúp viết phương thức nhận wildcard linh hoạt mà vẫn thao tác đúng kiểu với dữ liệu, đồng thời phân biệt được những lỗi có thể giải quyết bằng generic helper và những lỗi cần thay đổi hợp đồng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-002">Đọc thông báo CAP#1 và capture of ?</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc thông báo CAP#1 và "capture of ?" trong Java Generics

## 1. Tại sao javac nhắc đến CAP#1?

Trong Java Generics, một biến có kiểu `List<?>` nói rằng danh sách có **một đối số kiểu nào đó**, nhưng mã đang sử dụng biến không biết đó là `String`, `Integer` hay một kiểu khác. Khi kiểm tra lời gọi generic, trình biên dịch Java (`javac`) có thể tạo một **kiểu nội bộ tạm thời** để biểu diễn đối số kiểu chưa biết. Quá trình đó gọi là **wildcard capture** hoặc **capture conversion**.

Khi báo lỗi, `javac` đôi khi hiển thị một tên do compiler tạo như:

- `CAP#1`: ký hiệu tạm do compiler đặt cho một kiểu đã được capture.
- `capture of ?`: cho biết kiểu ẩn đó đến từ wildcard không giới hạn `?`.
- `capture of ? extends Number`: cho biết kiểu ẩn đến từ wildcard có cận trên `Number`.
- `capture of ? super Integer`: cho biết kiểu ẩn đến từ wildcard có cận dưới `Integer`.

**`CAP#1` không phải là lớp Java, một biến, tên type parameter có thể tự khai báo hay thứ tồn tại độc lập ở runtime.** Đó là cách thông báo chẩn đoán giúp người đọc phân biệt các kiểu ẩn trong phép kiểm tra kiểu tại thời điểm biên dịch. Con số `#1`, `#2`,... chỉ là nhãn nội bộ; không được suy ra thứ tự thực thi hoặc một định danh ổn định giữa các bản JDK.

Ví dụ:

```java
List<String> names = new ArrayList<>();
List<?> unknown = names;
```

Compiler biết `unknown` **đang có kiểu tĩnh `List<?>`**. Khi xử lý một lời gọi nhận phần tử thuộc `E` như `add(E)`, nó có thể xem `E` là một kiểu ẩn `CAP#1` nào đó. Tuy nhiên, không có căn cứ để khẳng định `String` bất kỳ gán được cho kiểu ẩn ấy.

```java
unknown.add("An"); // Lỗi biên dịch: không chứng minh được String là CAP#1
```

Việc nhìn thấy `CAP#1` có nghĩa rất thực tế: **bạn đang cần đưa giá trị hoặc liên hệ hai giá trị với một kiểu mà compiler hiện chưa biết chính xác**.

## 2. Phân biệt kiểu thật, kiểu tĩnh và kiểu capture

Giả sử:

```java
List<String> names = new ArrayList<>(List.of("An", "Binh"));
List<?> view = names;
```

Có ba mức cần tách riêng:

1. **Đối tượng/danh sách thực tế**: `names` và `view` cùng trỏ tới một `ArrayList` đang lưu các chuỗi; `names` được khai báo `List<String>`.
2. **Kiểu tĩnh của biểu thức `view`**: `List<?>`. Mã viết qua biến `view` chỉ được dựa vào hợp đồng của dấu hỏi, không tự suy ra `String`.
3. **Kiểu capture nội bộ**: khi kiểm tra lời gọi, `javac` có thể đặt tên tạm như `CAP#1` cho đối số kiểu ẩn. Đây là chi tiết hỗ trợ type checking; tên này không phải một kiểu nguồn mà ta có thể viết trong Java.

Java Generics sử dụng **type erasure**: đối số kiểu như `String` không được giữ lại dưới dạng một thuộc tính runtime của bản thân đối tượng `ArrayList` để kiểm tra tự động toàn bộ phần tử. Wildcard capture không đọc một “generic type thật” từ heap; nó là phép biến đổi kiểu của compiler nhằm kiểm tra ràng buộc an toàn.

Do đó không thể sửa lỗi bằng cách tìm một lớp tên `CAP#1`, khai báo `CAP#1 value`, hoặc cố “xóa” tên đó bằng một phép ép kiểu tùy tiện.

## 3. Đọc cấu trúc thông báo lỗi từ javac

Ví dụ sau **cố ý không biên dịch**. Đặt vào tệp `BrokenCapture.java` để quan sát compiler:

```java
import java.util.List;

public class BrokenCapture {
    static void appendText(List<?> values) {
        values.add("new");
    }
}
```

Chạy:

```bash
javac -Xdiags:verbose BrokenCapture.java
```

Lệnh này thất bại ở bước biên dịch. **Không có stdout của chương trình**, vì chưa tạo được chương trình chạy. Nội dung và cách trình bày stderr có thể khác nhau theo bản JDK, tùy compiler và tùy ngữ cảnh; không nên học thuộc nguyên văn hay số `CAP`.

Thông báo thường chứa các ý tương đương:

```text
no suitable method found for add(String)
required: CAP#1
found:    String
reason:  String cannot be converted to CAP#1
where CAP#1 is a fresh type-variable from capture of ?
```

Đây là **dạng thông tin minh họa**, không cam kết là toàn bộ stderr chính xác của mọi bản `javac`. Thông báo thực tế có thể liệt kê thêm overload `add(int,E)` hoặc diễn đạt lỗi là `incompatible types`.

Hãy đọc theo thứ tự:

1. **Vị trí lỗi**: lời gọi `values.add("new")`, nhất là tham số được truyền.
2. **`found` / `actual`**: compiler biết đối số bạn cung cấp là `String`.
3. **`required` / `formal`**: phương thức generic `List<E>.add(E)` đang cần một giá trị thuộc kiểu phần tử ẩn, có thể được gọi là `CAP#1`.
4. **`capture of ?`**: kiểu cần thiết đến từ dấu `?` trong `List<?>`.
5. **Kết luận**: chưa có bằng chứng `String` là kiểu phần tử của danh sách. Vấn đề nằm ở hợp đồng kiểu, không phải cú pháp chuỗi hay lỗi JVM.

Khi gặp lỗi dài, hãy tìm tên biến có wildcard, lần ngược tới kiểu tham số của phương thức đang gọi, rồi hỏi: “Tôi đang **đọc** giá trị từ kiểu ẩn hay muốn **ghi** một giá trị có kiểu cụ thể vào kiểu ẩn?”

Tùy thông báo, `javac` có thể nêu `CAP#1 extends Object` hoặc dùng cụm “fresh type-variable”. Những phần này mô tả ràng buộc của kiểu ẩn; chúng không phải gợi ý để đổi `CAP#1` thành `Object`.

## 4. Vì sao `List<?>.add` gây lỗi nhưng `get` đọc được?

`List<E>` có hai thao tác đặc trưng:

```java
E get(int index);
boolean add(E element);
```

Đây là **chữ ký rút gọn** minh họa hai phương thức, không phải chương trình chạy độc lập.

Đối với `List<?>`, `get` cung cấp một giá trị thuộc kiểu ẩn; mọi kiểu đối số của generics đều là kiểu tham chiếu, nên có thể sử dụng giá trị ấy dưới kiểu `Object`:

```java
List<String> names = List.of("An");
List<?> values = names;

Object first = values.get(0); // Hợp lệ
// String text = values.get(0); // Không an toàn theo kiểu tĩnh List<?>
```

Nhưng `add` cần một giá trị **đúng kiểu ẩn**. Compiler không biết kiểu đó:

```java
List<?> values = new ArrayList<String>();

// values.add("An"); // Lỗi vì dấu ? không có nghĩa String
// values.add(42);   // Lỗi vì dấu ? không có nghĩa Integer
values.add(null);    // Hợp lệ về kiểu
```

Chỉ có `null` được phép ở mức type checking vì `null` gán được cho mọi kiểu tham chiếu. Tuy nhiên `List.of(...)` và các danh sách unmodifiable vẫn có thể ném `UnsupportedOperationException` khi sửa đổi; một số implementation có quy tắc riêng đối với null.

**Không nên chữa lỗi CAP bằng raw `List`**. Ép về raw type để gọi `add` bỏ qua kiểm tra tham số generic và có thể tạo *heap pollution*, khiến phần mã đọc danh sách theo kiểu đã khai báo gặp `ClassCastException` muộn hơn.

## 5. `CAP#1 extends Number`: đọc được `Number` nhưng không ghi được `Number`

Với wildcard có cận trên:

```java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<? extends Number> source = integers;

Number first = source.get(0); // Hợp lệ
// source.add(3);              // Không biên dịch
// source.add(3.5);            // Không biên dịch
// source.add((Number) 3);     // Cũng không biên dịch
```

Compiler có thể mô tả kiểu phần tử là một `CAP#1` với **cận trên `Number`**. Điều nó biết là “`CAP#1` là `Number` hoặc một kiểu con của `Number`”. Vì thế:

- **Đọc** một `CAP#1` rồi gán lên `Number` là an toàn.
- **Ghi** một `Number` hay `Integer` mới vào `CAP#1` chưa chắc an toàn: danh sách có thể là `List<Double>`, `List<Integer>` hoặc `List<Number>`.

Đoạn minh họa lỗi:

```java
static void brokenAdd(List<? extends Number> numbers) {
    // numbers.add(Integer.valueOf(10)); // Không thể gán Integer vào CAP#1 extends Number
}
```

Khi thông báo có `capture of ? extends Number`, hãy đọc nó như **“kiểu ẩn nằm dưới Number”**, không phải **“kiểu ẩn chính là Number”**. Đây là lý do cận trên giúp producer cung cấp giá trị để đọc, nhưng không cấp quyền thêm giá trị mới tùy ý.

## 6. `CAP#1 super Integer`: ghi được `Integer` nhưng khi đọc chỉ chắc chắn là `Object`

Với wildcard có cận dưới:

```java
List<Number> numbers = new ArrayList<>(List.of(1, 2));
List<? super Integer> destination = numbers;

destination.add(3); // Hợp lệ: mọi kiểu phù hợp đều nhận được Integer
Object first = destination.get(0); // Hợp lệ

// Integer exact = destination.get(0); // Không biên dịch
// Number number = destination.get(0);  // Không biên dịch
```

Một `List<? super Integer>` có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`. Một danh sách `List<Object>` có thể chứa các đối tượng khác `Integer` từ trước, nên `get` chỉ bảo đảm kiểu `Object`.

Compiler có thể gắn nhãn `CAP#1` cho đối số kiểu với thông tin về **cận dưới `Integer`** và cận trên tổng quát `Object`. Lỗi khi cố gán kết quả `get` cho `Integer` không có nghĩa `CAP#1` là kiểu con của `Integer`; wildcard `super` giới hạn chiều ngược lại.

Hai cách đọc cần ghi nhớ:

| Wildcard | Compiler biết gì về kiểu ẩn? | Giá trị lấy ra bảo đảm | Giá trị đưa vào được bảo đảm |
|---|---|---|---|
| `?` | Chưa biết kiểu tham số | `Object` | Chỉ `null` về mặt kiểu |
| `? extends Number` | Kiểu ẩn là `Number` hoặc kiểu con | `Number` | Chỉ `null` về mặt kiểu |
| `? super Integer` | Kiểu ẩn là `Integer` hoặc kiểu cha | `Object` | `Integer`, các kiểu con của nó và `null` |
| `Integer` | Kiểu phần tử chính xác là `Integer` | `Integer` | `Integer` và `null` |

Các cột “đưa vào” nói về khả năng compiler cho phép, không bảo đảm danh sách thực tế hỗ trợ mutation hoặc chấp nhận null. Với generic, nguyên tắc PECS vẫn hữu ích: **Producer Extends, Consumer Super**.

## 7. Một lỗi capture tinh tế: lấy ra rồi đặt lại chính phần tử đó

Có trường hợp không hề muốn thêm phần tử lạ: chỉ muốn đưa một phần tử hiện có về đúng danh sách. Dù vậy, đoạn mã sau thường gây lỗi capture trong `javac`:

```java
static void putFirstBack(List<?> values) {
    // values.set(0, values.get(0)); // Lỗi capture wildcard
}
```

Về ý nghĩa, việc đặt lại phần tử vừa lấy ra là an toàn nếu danh sách mutable. Nhưng ở mức biểu thức generic, compiler không nhất thiết giữ được **bằng chứng hai vị trí dùng wildcard trong lời gọi là cùng một kiểu được capture**. Trong thông báo có thể xuất hiện `CAP#1` và `CAP#2` hoặc `Object` và `CAP#1`. Điều đó không có nghĩa danh sách chứa hai lớp phần tử khác nhau; đó là hai nhãn compiler cần để bảo vệ ràng buộc kiểu trong ngữ cảnh kiểm tra này.

Để diễn tả quan hệ “đọc ra `T` rồi ghi lại đúng `T`” rõ ràng, dùng **wildcard capture helper**:

```java
static void putFirstBack(List<?> values) {
    putFirstBackCaptured(values);
}

private static <T> void putFirstBackCaptured(List<T> values) {
    if (!values.isEmpty()) {
        T first = values.get(0);
        values.set(0, first);
    }
}
```

Đây là các phương thức minh họa có thể đặt vào một lớp Java. `putFirstBackCaptured` có **một tham số kiểu tên `T` nhất quán**: kết quả `get` là `T`, và `set` chấp nhận `T`. Lời gọi helper cho phép compiler suy luận `T` từ wildcard ẩn của danh sách được truyền vào.

Không cần biết tên kiểu thực tế là `String` hay `Integer`. Không dùng raw type, không ép kiểu `(List<T>)`, không tắt kiểm tra compile time.

Tuy nhiên, helper chỉ đảm bảo **an toàn kiểu**; nếu danh sách không cho sửa, `set` vẫn có thể ném `UnsupportedOperationException`. Nếu danh sách rỗng, đoạn trên không thực hiện `set`.

## 8. Ví dụ Java 17 hoàn chỉnh: giữ cùng kiểu ẩn bằng helper

Tạo tệp `WildcardCaptureDiagnosticsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WildcardCaptureDiagnosticsDemo {
    static void repeatFirst(List<?> values) {
        repeatCaptured(values);
    }

    private static <T> void repeatCaptured(List<T> values) {
        if (!values.isEmpty()) {
            T first = values.get(0);
            values.add(first);
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

    static <T> void copyAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T element : source) {
            destination.add(element);
        }
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("red", "blue"));
        List<?> unknown = words;

        Object first = unknown.get(0);
        System.out.println("First: " + first);

        repeatFirst(unknown);
        System.out.println("Repeated: " + words);

        swapFirstTwo(unknown);
        System.out.println("Swapped: " + words);

        Collections.swap(unknown, 0, 1);
        System.out.println("Library swap: " + words);

        List<Integer> numbers = List.of(2, 4);
        List<Number> collected = new ArrayList<>();
        copyAll(numbers, collected);
        System.out.println("Collected: " + collected);
    }
}
```

Biên dịch và chạy:

```bash
javac WildcardCaptureDiagnosticsDemo.java
java WildcardCaptureDiagnosticsDemo
```

**Stdout chính xác:**

```text
First: red
Repeated: [red, blue, red]
Swapped: [blue, red, red]
Library swap: [red, blue, red]
Collected: [2, 4]
```

Giải thích:

1. `unknown` có kiểu tĩnh `List<?>`, nên `get(0)` có thể dùng như `Object`. Đối tượng được tham chiếu là `ArrayList<String>`.
2. `repeatFirst` nhận một `List<?>`, gọi helper `repeatCaptured`. Trong helper, `T first = values.get(0)` và `values.add(first)` cùng dùng `T`. Vì `ArrayList` mutable, kết quả là `[red, blue, red]`.
3. `swapFirstTwo` dùng cùng cơ chế để hoán đổi hai phần tử đầu mà không cần tự tạo giá trị kiểu `T` mới. Kết quả là `[blue, red, red]`.
4. `Collections.swap` là API thư viện chấp nhận `List<?>`; nó cho phép hoán đổi phần tử theo chỉ số khi danh sách hỗ trợ `set`. Kết quả là `[red, blue, red]`.
5. `copyAll` minh họa cách nối kiểu giữa **hai danh sách có vai trò khác nhau**: nguồn `List<? extends T>` cung cấp `T`, đích `List<? super T>` nhận `T`. `List<Integer>` và `List<Number>` tương thích theo quy tắc PECS.

Mọi phương thức trong tệp đều có đầy đủ khai báo/import, không dùng raw type và không có unchecked cast. Nếu thay `words` bằng danh sách trả về từ `List.of(...)`, `repeatFirst` và `swapFirstTwo` có thể ném `UnsupportedOperationException` khi cố sửa.

## 9. Vì sao đôi khi thấy CAP#1 và CAP#2 trong cùng một lỗi?

Các ký hiệu `CAP#1`, `CAP#2`,... là những kiểu ẩn nội bộ do compiler đặt tên khi capture các wildcard trong những ngữ cảnh khác nhau. **Không thể mặc định hai wildcard độc lập có cùng kiểu phần tử** chỉ vì cùng được viết là `?`.

Ví dụ:

```java
static void moveOne(List<?> from, List<?> to) {
    // to.add(from.get(0)); // Không biên dịch
}
```

Bên gọi hoàn toàn có thể cung cấp:

```java
List<String> strings = new ArrayList<>(List.of("red"));
List<Integer> integers = new ArrayList<>(List.of(1));

// moveOne(strings, integers); // Nếu thân hàm ghi phần tử, sẽ hỏng kiểu!
```

`from` có thể là `List<String>`, còn `to` là `List<Integer>`. Một helper chỉ đặt cùng tên `T` cho hai danh sách **không thể biến hai kiểu thật khác nhau thành cùng kiểu**.

Nếu muốn sao chép giữa các danh sách có quan hệ kiểu phù hợp, hãy thiết kế hợp đồng ngay trong chữ ký:

```java
static <T> void moveAll(
        List<? extends T> from,
        List<? super T> to) {
    for (T item : from) {
        to.add(item);
    }
}
```

Với `List<String>` làm nguồn và `List<Object>` làm đích, compiler có thể suy luận một `T` phù hợp; với `List<String>` làm nguồn và `List<Integer>` làm đích, không có quan hệ kiểu phù hợp để truyền giá trị theo hợp đồng trên.

**Bài học khi thấy nhiều CAP:** hãy phân biệt “các lần sử dụng của **một danh sách** cần giữ cùng kiểu” với “**hai danh sách khác nhau** mà kiểu phần tử chưa chắc liên hệ”. Nhóm đầu thường giải được bằng helper capture; nhóm sau cần mô hình quan hệ kiểu thực sự hoặc thay đổi thiết kế.

## 10. Khi nào helper capture giải quyết được lỗi?

Helper generic thích hợp khi bạn **cần biểu diễn một quan hệ về cùng kiểu ẩn** mà compiler chưa thể chứng minh trong cách viết trực tiếp qua wildcard.

Mẫu chung:

```java
static void operation(List<?> values) {
    operationCaptured(values);
}

private static <E> void operationCaptured(List<E> values) {
    // Ở đây các thao tác đọc và ghi cùng kiểu E được kiểm tra thống nhất.
}
```

Đây là **mẫu khai báo phương thức**, không phải chương trình độc lập. Tên `E` do tác giả đặt (có thể dùng `T`, `X`...) và khác hoàn toàn với `CAP#1` do compiler tạo.

Một helper **không** hợp thức hóa việc ghi giá trị sai kiểu:

```java
private static <T> void unsafeAttempt(List<T> values) {
    // values.add("new"); // Vẫn lỗi: T chưa chắc là String
}
```

Helper chỉ cấp tên thống nhất cho kiểu ẩn; nó không chứng minh một `String` bất kỳ có thể gán cho mọi `T`.

Khi bài toán chỉ cần một thao tác thư viện sẵn có như `Collections.swap(List<?>, ...)`, dùng API có sẵn có thể rõ ràng hơn viết helper. Khi bài toán chỉ muốn **đọc** giá trị dưới `Object`, không cần helper vì `List<?>` đã đủ.

Nếu cần **ghi `T` từ bên ngoài vào đích**, hãy cân nhắc `List<? super T>`. Nếu cần **đọc `T` từ nguồn**, hãy cân nhắc `List<? extends T>`. Nếu một đối tượng cần vừa đọc vừa ghi đúng một kiểu phần tử, `List<T>` có thể là hợp đồng phù hợp hơn wildcard.

## 11. Những cách xử lý sai cần tránh

### Sai: dùng raw type để vượt qua CAP

```java
static void unsafe(List<?> values) {
    List raw = values;   // Raw type, mất kiểm tra đối số kiểu
    raw.add("new");      // Có thể xuất hiện cảnh báo unchecked
}
```

Ví dụ cố ý minh họa mã **không an toàn**. Nếu `values` đang là `List<Integer>`, dòng cuối có thể chèn `String` vào một danh sách mà các vị trí khác tin là chỉ chứa `Integer`. Sau đó mã đọc theo kiểu `Integer` có thể gặp `ClassCastException`. Cảnh báo `unchecked` không phải lỗi tương đương với capture error; việc xóa cảnh báo không làm phép ghi trở nên an toàn.

### Sai: ép kiểu `List<String>` khi chưa chứng minh phần tử

```java
static void unsafeCast(List<?> values) {
    // Đừng dùng cast như một phương pháp sửa lỗi CAP khi không biết dữ liệu.
    // List<String> strings = (List<String>) values;
}
```

Ép kiểu generic không tự xác nhận từng phần tử do type erasure. Khi nhận dữ liệu chưa tin cậy, hãy kiểm tra từng phần tử rồi sao chép vào một `List<T>` mới nếu cần đảm bảo nội dung theo `T`.

### Sai: chỉ đổi `CAP#1` thành `Object`

Nếu phương thức đòi phần tử của `List<?>`, thay dữ liệu đầu vào thành `Object` không giúp compiler biết kiểu ẩn:

```java
static void broken(List<?> values, Object candidate) {
    // values.add(candidate); // Vẫn không hợp lệ
}
```

Một `List<String>` không thể nhận một `Object` bất kỳ. `Object` là **kiểu cha chung của các kiểu tham chiếu**, không phải tên khác của tham số kiểu ẩn.

### Sai: nghĩ thông báo CAP nói về runtime

Các lỗi `CAP#1` và `capture of ?` đang xét được phát hiện **khi biên dịch**. Chúng khác với `ClassCastException` xảy ra lúc chạy. `-Xdiags:verbose` giúp đọc thông báo chi tiết hơn, còn `@SuppressWarnings("unchecked")` không giải quyết một **compile error** về đối số kiểu không tương thích.

## 12. Bảng dịch nhanh thông báo compiler sang câu hỏi cần giải

| Cụm thường gặp | Cách đọc | Câu hỏi xử lý |
|---|---|---|
| `CAP#1` | Tên tạm của một kiểu ẩn do capture | Kiểu này đến từ wildcard ở đâu? |
| `capture of ?` | Đối số kiểu bất kỳ chưa biết | Tôi có đang cố ghi giá trị vào `List<?>`? |
| `capture of ? extends T` | Kiểu ẩn là `T` hoặc kiểu con | Tôi có đang cố thêm `T` vào nguồn chỉ bảo đảm đọc được như `T`? |
| `capture of ? super T` | Kiểu ẩn là `T` hoặc kiểu cha | Tôi có đang đòi đọc ra `T` trong khi chỉ đảm bảo `Object`? |
| `required CAP#1, found String` | Phương thức cần kiểu ẩn, nhưng được truyền `String` | Ai chứng minh `String` đúng bằng kiểu ẩn? |
| `CAP#1, CAP#2` | Compiler đang theo dõi các kiểu ẩn khác nhau | Hai vị trí có thật sự chia sẻ một kiểu phần tử không? |
| `incompatible types` | Phép gán/truyền tham số vi phạm ràng buộc | Có thể đổi hợp đồng bằng wildcard phù hợp hoặc helper `<T>` không? |

Số thứ tự CAP có thể thay đổi tùy ngữ cảnh; không nên viết logic dựa vào thứ tự nhãn ấy. Thông báo “capture of ...” **không tự động có nghĩa phải ép kiểu**, cũng không chứng minh dữ liệu trong danh sách đã bị hỏng.

## 13. Cách điều tra một lỗi thực tế theo từng bước

Khi gặp một thông báo lớn liên quan đến capture:

1. **Đọc dòng gọi gây lỗi**, không bắt đầu bằng tìm tên `CAP#1` trong project. Xác định `add`, `set`, `get` hay lời gọi một phương thức generic.
2. **Tìm kiểu khai báo của receiver**: `List<?>`, `List<? extends Number>`, `List<? super Integer>` hay một kiểu lồng nhau như `List<List<?>>`.
3. **Tìm tham số kiểu mà phương thức cần**. Ví dụ `add(E)` cần `E` của chính danh sách, `set(int,E)` cần một `E`, còn `get(int)` trả `E`.
4. **Ghi rõ hướng dữ liệu**: phần tử đi **từ danh sách ra** hay **đi vào danh sách**? Hướng này quyết định `extends`/`super` nếu có thể thay đổi chữ ký.
5. **Kiểm tra quan hệ giữa nhiều kiểu ẩn**: cùng một danh sách có thể dùng helper `<T>`; nhiều danh sách độc lập đòi ràng buộc thực sự trong chữ ký.
6. **Chọn sửa đổi nhỏ nhất mà vẫn an toàn kiểu**: dùng `Object` khi chỉ đọc chung, dùng helper capture khi giữ cùng kiểu ẩn, dùng PECS cho nguồn/đích, hoặc dùng `List<T>` nếu cần một kiểu cụ thể.
7. **Biên dịch lại** với `javac -Xdiags:verbose` nếu cần chi tiết; sau khi biên dịch thành công, kiểm tra thêm hành vi runtime như danh sách rỗng hoặc unmodifiable.

Điều quan trọng là **sửa quan hệ kiểu thật của chương trình**, không biến một lỗi compile-time có ích thành một lỗi runtime bằng raw cast.

## 14. Bài tập tự kiểm tra

Cho:

```java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<?> a = integers;
List<? extends Number> b = integers;
List<? super Integer> c = integers;
```

Hãy dự đoán:

1. `a.add(3)` có biên dịch không? Nếu báo `CAP#1`, kiểu ẩn đến từ đâu?
2. `Number x = b.get(0)` có hợp lệ không? `b.add(3)` thì sao?
3. `c.add(3)` có hợp lệ không? `Integer y = c.get(0)` thì sao?
4. `a.set(0, a.get(0))` có thể gây lỗi capture không? Có cách dùng helper nào an toàn hơn?
5. Hai biến `List<?> from` và `List<?> to` có được mặc định cùng kiểu phần tử chỉ vì cùng dùng `?` không?
6. `CAP#1` là một lớp runtime hay chỉ là ký hiệu compiler?

**Đáp án:**

1. Không. `CAP#1` biểu diễn kiểu phần tử ẩn được capture từ `List<?>`; compiler không biết liệu `Integer` có phù hợp.
2. `get` đọc an toàn dưới `Number` vì cận trên; `add(3)` không biên dịch vì kiểu ẩn có thể là một kiểu con khác của `Number`.
3. `add(3)` hợp lệ vì mọi kiểu `? super Integer` đều nhận được `Integer`. `get` chỉ bảo đảm `Object`, nên không gán thẳng cho `Integer`.
4. Có; dùng `static <T> void helper(List<T> values)` và đặt cả `get`/`set` trong helper để cùng kiểu `T` được kiểm tra thống nhất.
5. Không. Hai wildcard có thể biểu diễn hai kiểu phần tử khác nhau; nếu cần sao chép, thiết kế liên hệ kiểu bằng `<T>`, `? extends T` và `? super T`.
6. `CAP#1` là tên nội bộ trong chẩn đoán compiler, không phải lớp runtime. Không thể tạo đối tượng hay khai báo biến mang tên kiểu `CAP#1` trong Java.

## 15. Ghi nhớ

Khi thấy **`CAP#1` / `capture of ?`**, hãy đọc: **“compiler đang theo dõi một kiểu generic có tồn tại nhưng chưa biết chính xác; thao tác của tôi có đang giả định quá nhiều về kiểu ấy không?”**. Nếu thông báo nói `? extends T`, bạn thường biết đủ để **đọc dưới `T`**; nếu nói `? super T`, bạn thường biết đủ để **ghi giá trị `T`**; nếu nói `?`, bạn chủ yếu chỉ có thể đọc an toàn dưới `Object`.

Nếu cần giữ nhất quán **một kiểu ẩn** qua nhiều thao tác trên cùng danh sách, một helper có type parameter `<T>` giúp compiler biểu diễn mối liên hệ đó. Nếu các nguồn/đích thật sự có các kiểu khác nhau, hãy mô tả quan hệ bằng wildcard có cận phù hợp. Cách tiếp cận này giải quyết nguyên nhân của lỗi capture mà vẫn giữ kiểm tra an toàn kiểu của Java Generics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-003">Đặt tên kiểu chưa biết bằng phương thức hỗ trợ generic</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên kiểu chưa biết bằng phương thức hỗ trợ generic (wildcard capture)

## 1. Wildcard capture giải quyết vấn đề gì?

Trong Java Generics, ký hiệu `?` cho biết một đối số kiểu **cụ thể nhưng chưa biết** tại nơi sử dụng, chẳng hạn `List<?>`, `List<? extends Number>` hoặc `List<? super Integer>`. Ta vẫn có thể đọc dữ liệu theo những bảo đảm của giới hạn kiểu, nhưng nhiều thao tác ghi bị từ chối vì trình biên dịch không thể chứng minh giá trị được ghi đúng kiểu phần tử ẩn.

Ví dụ:

```java
import java.util.List;

static void replaceWithSameValue(List<?> values) {
    values.set(0, values.get(0)); // LỖI BIÊN DỊCH
}
```

Người lập trình thấy cùng một danh sách được đọc và ghi, nhưng ở cấp độ kiểm tra kiểu, biểu thức `get(0)` không được bảo đảm có đúng một kiểu mà lời gọi `set(0, ...)` yêu cầu khi wildcard được capture qua các ngữ cảnh sử dụng khác nhau. Vì thế Java không chấp nhận mã này.

Để diễn đạt mối quan hệ “giá trị lấy ra và vị trí ghi vào thuộc **cùng kiểu phần tử**”, ta sử dụng **phương thức hỗ trợ generic** (*generic helper method*) có tham số kiểu đặt tên:

```java
static <E> void replaceHelper(List<E> values) {
    if (!values.isEmpty()) {
        E current = values.get(0);
        values.set(0, current); // Hợp lệ: get và set dùng cùng E
    }
}

static void replace(List<?> values) {
    replaceHelper(values); // Trình biên dịch bắt giữ wildcard thành E
}
```

Trong lời gọi `replaceHelper(values)`, Java thực hiện **capture conversion** để coi đối số wildcard chưa biết là một tham số kiểu tạm thích hợp khi suy luận kiểu `E`. Bên trong helper, cùng tên `E` được sử dụng nhất quán ở `List<E>`, `get` và `set`.

Đây là kỹ thuật **đặt tên cho kiểu chưa biết bằng phương thức hỗ trợ generic**. Tên `E` chỉ tồn tại trong phạm vi helper; không có chuyện wildcard bị thay đổi vĩnh viễn thành `Object` hay thành một kiểu được người lập trình đoán trước.

## 2. Wildcard `?` và tham số kiểu `<E>` khác nhau thế nào?

Xét hai chữ ký:

```java
static void inspect(List<?> list) { /* ... */ }

static <E> void transform(List<E> list) { /* ... */ }
```

Cả hai có thể nhận `List<String>`, `List<Integer>` và nhiều kiểu danh sách khác. Khác biệt nằm ở thông tin kiểu có thể sử dụng **trong thân phương thức**.

| Đặc điểm | `List<?>` | `<E> ... (List<E>)` |
| --- | --- | --- |
| Kiểu phần tử | Một kiểu chưa biết | Một kiểu được đặt tên `E` |
| Đọc bằng `get` | Có thể nhận an toàn như `Object` | Nhận an toàn dưới `E` |
| Thêm giá trị cụ thể từ ngoài | Không được phép tùy ý | Chỉ được nếu giá trị đã được chứng minh là `E` |
| Đọc rồi ghi lại cùng giá trị | Biểu thức trực tiếp với wildcard có thể lỗi capture | Có thể giữ giá trị trong biến `E` và ghi lại |
| Liên hệ các vị trí kiểu | Không đặt tên trực tiếp cho `?` | Một `E` có thể dùng tại nhiều vị trí |
| Bảo đảm sửa đổi runtime | Không | Không; phụ thuộc triển khai danh sách |

`<E>` là **khai báo một tham số kiểu mới** cho phương thức. Nó không đòi caller phải viết rõ kiểu `E`: trình biên dịch thường tự suy luận qua đối số và áp dụng capture conversion.

Ví dụ, `List<?> unknown = new ArrayList<String>();` có thể gọi `transform(unknown)` nếu thân `transform` chỉ thực hiện các thao tác an toàn trên cùng một `E`. Trình biên dịch **không kết luận `E = String` dựa vào đối tượng runtime**; nó coi `E` là kiểu ẩn phù hợp của lời gọi.

## 3. Mô hình capture conversion: kiểu chưa biết có danh tính trong một phạm vi

Trình biên dịch thường mô tả wildcard qua ký hiệu kiểu nội bộ như `CAP#1`. Với:

```java
List<?> items = new ArrayList<String>();
```

ta có thể hình dung `items` là danh sách chứa một kiểu `CAP#1` chưa biết, có giới hạn trên `Object`. Đây là ký hiệu diễn giải, **không phải** cú pháp Java có thể viết trực tiếp trong mã nguồn.

```text
Kiểu khai báo tại caller:
    List<?>

Capture conversion tại một lời gọi thích hợp:
    ?  -> CAP#1 (kiểu chưa biết, có danh tính nội bộ)

Suy luận tham số kiểu của helper:
    E  = CAP#1

Trong helper:
    E value = list.get(0)
    list.set(0, value)
    // Cả nơi đọc và nơi ghi đều dùng E.
```

Không nên hiểu dấu `?` là “`Object`” hoặc là “mỗi phần tử có thể mang một kiểu tùy ý”. Đối tượng thật vẫn giữ hợp đồng `List<String>`, `List<Integer>` hoặc kiểu tương ứng. `Object` chỉ là kiểu chung an toàn khi ta đọc từ `List<?>` mà chưa biết thêm điều gì về phần tử.

Thông điệp của lỗi biên dịch có thể nhắc tới `capture of ?` hoặc `CAP#1`, `CAP#2`. Các số đánh dấu này chỉ là tên nội bộ để trình bày sự không thống nhất kiểu ở nơi compiler phát hiện lỗi; không dùng chúng để khai báo biến Java.

### Vì sao cần một helper?

Một helper là nơi lập trình viên **khai báo cùng một `E` xuất hiện ở cả hai vị trí liên quan**. Trình biên dịch có thể chứng minh mọi thao tác trong helper không đưa phần tử sai kiểu vào danh sách. Khi caller có `List<?>`, suy luận kiểu của lời gọi sẽ capture wildcard một cách an toàn.

Helper không làm các phép ép kiểu không an toàn trở nên hợp lệ; nó chỉ biểu đạt đúng mối quan hệ đã tồn tại giữa những phần tử của một đối tượng generic.

## 4. Thiết kế ranh giới API ngoài và generic helper bên trong

Một helper không chỉ dùng cho thao tác hoán đổi. Ví dụ **sao chép phần tử đầu vào vị trí cuối trong cùng list**:

```java
static void copyFirstToLast(List<?> values) {
    copyCaptured(values);
}

private static <E> void copyCaptured(List<E> values) {
    if (values.size() >= 2) {
        E first = values.get(0);
        values.set(values.size() - 1, first);
    }
}
```

Đây là các method minh họa đặt trong class có import `java.util.List`. Nếu dữ liệu ban đầu là `[A, B, C]` trong `ArrayList<String>`, kết quả là `[A, B, A]`. Nếu là `[1, 2, 3]` trong `ArrayList<Integer>`, kết quả là `[1, 2, 1]`.

**Tại biên API**, `List<?>` cho phép caller truyền danh sách của bất kỳ element type. **Trong helper**, `List<E>` kết nối `get` và `set` bằng cùng một `E`; compiler không cần biết `E` là String hay Integer. Không thể thay `first` bằng một `Object` lấy tùy ý từ ngoài list.

Một ví dụ khác của kỹ thuật này là swap hai vị trí; Java đã có `Collections.swap(List<?>,i,j)`. Khi chỉ cần swap, hãy ưu tiên API chuẩn; khi cần một thuật toán riêng như trên, generic helper giúp trình bày bất biến kiểu rõ hơn. Nếu list không hỗ trợ `set`, runtime vẫn có thể ném `UnsupportedOperationException`.

## 5. Thêm lại phần tử đang có: một thao tác có thể hợp lệ trong helper

Mã trực tiếp sau có lỗi kiểu:

```java
static void duplicateWrong(List<?> values) {
    values.add(values.get(0)); // LỖI BIÊN DỊCH
}
```

Có thể giữ kiểu phần tử cùng một `E` bằng helper:

```java
static <E> void duplicateCaptured(List<E> values) {
    if (!values.isEmpty()) {
        E first = values.get(0);
        values.add(first); // Đúng ở cấp độ generic
    }
}

static void duplicateFirst(List<?> values) {
    duplicateCaptured(values);
}
```

Ở đây `first` **được lấy từ chính danh sách**, không phải một `Object` hay `String` bất kỳ đến từ bên ngoài. Nếu `values` thực chất là `List<Integer>`, helper chỉ nhân bản phần tử `Integer` có sẵn. Nếu là `List<String>`, helper nhân bản `String`.

Cần phân biệt rõ hai điều:

- Helper hợp lệ về **kiểu** vì sử dụng cùng một `E`.
- `add` vẫn có thể thất bại ở **runtime** nếu danh sách cố định kích thước hoặc không cho sửa đổi (`Arrays.asList`, `List.of`).

Còn nếu danh sách rỗng, mã trên chủ động không làm gì để tránh `IndexOutOfBoundsException`.

### Capture không cấp quyền thêm một giá trị bên ngoài tùy ý

Không thể viết:

```java
static void addString(List<?> unknown) {
    addCaptured(unknown, "Java"); // Không đảm bảo biên dịch
}

static <E> void addCaptured(List<E> list, E value) {
    list.add(value);
}
```

Vì không thể chứng minh `unknown` là `List<String>`. Tại lời gọi `addCaptured(unknown, "Java")`, các ràng buộc kiểu của `List<E>` và `E value` không có một cách gán hợp lệ chỉ dựa trên `List<?>`; `E` bị giới hạn bởi kiểu phần tử ẩn, không thể tùy tiện coi là `String`.

Để thực sự thêm `String` vào một đích có thể nhận nó, hãy thay hợp đồng API:

```java
static void addText(List<? super String> destination, String text) {
    destination.add(text);
}
```

Đích có thể là `List<String>`, `List<CharSequence>` hoặc `List<Object>`, và mọi kiểu đó đều tiếp nhận `String`. Không cần wildcard capture helper khi quan hệ giữa giá trị ghi và đích đã được nêu đầy đủ bằng giới hạn dưới.

## 6. Helper với wildcard có giới hạn trên `? extends T`

Cơ chế tương tự khi đối số không phải `?` trần mà là wildcard có bound:

```java
static void repeatLastNumber(List<? extends Number> values) {
    repeatNumberCaptured(values);
}

private static <E extends Number> void repeatNumberCaptured(List<E> values) {
    if (!values.isEmpty()) {
        E last = values.get(values.size() - 1);
        values.add(last);
    }
}
```

Lời gọi `repeatNumberCaptured(values)` cho phép trình biên dịch capture kiểu phần tử ẩn dưới giới hạn `Number`; `E` đồng thời bị giới hạn trên bởi `Number`.

Trong helper, có thể dùng:

```java
E number = values.get(0);
double d = number.doubleValue(); // Đúng: E extends Number
```

Nhưng không thể thêm một `Integer` tùy ý:

```java
static <E extends Number> void invalid(List<E> values) {
    values.add(Integer.valueOf(10)); // LỖI BIÊN DỊCH
}
```

Vì `E` có thể là `Double`, `BigDecimal` hoặc một kiểu con khác của `Number`. Capture giải quyết danh tính của một kiểu ẩn **đã có**, không cho phép giả định kiểu ấy là một subtype cụ thể nào đó.

Nếu chỉ cần thao tác với phần tử như `Number`, việc dùng `<E extends Number>` trong helper là phù hợp. Nếu chỉ cần chuyển phần tử giữa các vị trí của cùng danh sách, một helper `<E>` không bound cũng đủ; đừng thêm bound khi không có lý do.

## 7. Trường hợp giới hạn dưới `? super T`: capture không làm phần tử đọc ra thành T

Với:

```java
List<? super Integer> sink = new ArrayList<Number>();
sink.add(7); // Có thể thêm Integer
Object result = sink.get(0); // Chỉ được bảo đảm là Object
```

Wildcard của `sink` có giới hạn dưới `Integer`. Kiểu phần tử thực tế có thể là `Integer`, `Number`, `Object` hoặc supertype phù hợp khác. Do đó không thể đọc trực tiếp `Integer` từ `sink`.

Một helper generic nhận `List<E>` cũng không được phép giả định `E` chính xác là `Integer`:

```java
static <E> void inspectCaptured(List<E> list) {
    E element = list.get(0); // Hợp lệ
    // Integer n = element; // LỖI: E không biết có phải Integer hay không
}
```

Việc có thể chuyển `List<? super Integer>` vào helper và giữ kiểu `E` không làm giới hạn dưới chuyển thành giới hạn trên `Integer`. **Kiểu đầu ra bảo đảm** qua wildcard dưới vẫn là `Object`, trừ khi API cung cấp thêm chứng cứ kiểu độc lập.

Mặt khác, nếu helper chỉ muốn đưa **phần tử đã có** trở lại đúng danh sách, nó vẫn có thể sử dụng một `E` nhất quán; đó là tính an toàn của thao tác nội bộ, không phải khả năng đọc một `Integer` bên ngoài helper.

## 8. Capture trong phương thức generic và suy luận kiểu

Ví dụ:

```java
static <E> E first(List<E> list) {
    return list.get(0);
}

static Object readUnknown(List<?> values) {
    return first(values);
}
```

Trình biên dịch có thể capture wildcard trong `values` thành một kiểu chưa biết để suy luận `E` tại lời gọi `first(values)`. Giá trị trả về là chính kiểu đã capture, nhưng nơi gọi chỉ biết gán an toàn thành `Object` khi xuất phát từ `List<?>`.

Trường hợp có giới hạn trên:

```java
static Number readNumber(List<? extends Number> values) {
    return first(values);
}
```

Đây là phép trả về hợp lệ vì kiểu đã capture có giới hạn trên `Number`.

Đáng chú ý: generic helper **không bắt người gọi biết tên kiểu thực tế**. Trong thực tế các phương thức generic của Java dùng type inference và capture conversion rất nhiều, kể cả khi không thấy compiler thông báo `CAP#1`.

### Mỗi tham số wildcard độc lập không tự thành cùng một E

Xét:

```java
static void moveBad(List<?> source, List<?> destination) {
    destination.add(source.get(0)); // LỖI BIÊN DỊCH
}
```

Hai wildcard ở `source` và `destination` có thể biểu diễn **hai kiểu phần tử khác nhau**. Chúng không mặc nhiên được capture thành **cùng một `E`**.

Ngay cả khi cả hai biến tình cờ cùng trỏ tới một `List<String>` ở runtime, chữ ký `List<?>, List<?>` không diễn đạt quan hệ ấy. Helper `<E>` không thể “ép buộc” hai capture độc lập phải cùng kiểu nếu caller chưa cung cấp quan hệ kiểu an toàn.

Nếu nhu cầu là chuyển phần tử từ nguồn sang đích rộng hơn, hãy **diễn đạt đúng quan hệ** bằng PECS:

```java
static <E> void copyTo(
        List<? extends E> source,
        List<? super E> destination) {
    for (E value : source) {
        destination.add(value);
    }
}
```

Khi đó `List<Integer>` có thể được đọc và chuyển vào `List<Number>` hoặc `List<Object>`; nhưng hai danh sách `List<?>` độc lập không đủ chứng minh quan hệ cần thiết.

Không nên dùng raw type, ép kiểu unchecked hoặc `@SuppressWarnings("unchecked")` để giả vờ hai danh sách có cùng kiểu. Các cách đó có thể làm sai hợp đồng phần tử và gây lỗi ép kiểu muộn.

## 9. Khi nào nên dùng helper và khi nào không?

Dùng helper generic khi:

1. API bên ngoài nhận một `List<?>` hoặc `List<? extends T>` đủ linh hoạt.
2. Một nhóm thao tác **cần giữ cùng kiểu phần tử ẩn** qua nhiều vị trí trong thân hàm.
3. Giá trị ghi lại được lấy từ chính danh sách hoặc qua phép biến đổi đã được ràng buộc `E -> E`.
4. Có thể thực hiện thao tác mà không đoán kiểu cụ thể, không dùng cast unchecked.

Không cần helper nếu:

- API chỉ đọc phần tử qua `List<? extends T>`, không ghi trở lại.
- API chỉ ghi giá trị `T` vào `List<? super T>`.
- API đã dùng `<T> void process(List<T> list, ...)` và các vị trí đã liên hệ cùng một `T`.
- Có sẵn một API chuẩn thể hiện chính xác thao tác, chẳng hạn `Collections.swap` hoặc `Collections.reverse`.

Bảng chọn nhanh:

| Nhu cầu | Chữ ký gợi ý |
| --- | --- |
| Chỉ quan sát một danh sách chưa biết kiểu | `void inspect(List<?> list)` |
| Hoán đổi các phần tử của một danh sách kiểu chưa biết | `void swap(List<?> list)` gọi helper `<E>`, hoặc `Collections.swap` |
| Đọc rồi ghi lại đúng kiểu phần tử ẩn | `void update(List<?> list)` gọi helper `<E>` |
| Đọc và xử lý như `Number` | `void inspect(List<? extends Number> list)` |
| Biến đổi phần tử cùng kiểu `E` | `<E> void update(List<E> list, UnaryOperator<E> f)` |
| Thêm `String` vào danh sách có thể chứa `String` | `void write(List<? super String> sink)` |
| Sao chép nguồn sang đích tương thích | `<E> void copy(List<? extends E> src, List<? super E> dst)` |

Nếu triển khai helper gọi `add` hoặc `set`, danh sách phải hỗ trợ phép thay đổi đó ở runtime. `List.of` thường không cho sửa; `Arrays.asList` cho `set` nhưng không cho `add` thay đổi kích thước. Giới hạn kiểu là câu chuyện **biên dịch**, còn khả năng thay đổi cấu trúc và nội dung thuộc **hợp đồng đối tượng**.

## 10. Chương trình Java 17+ đầy đủ

Tạo tệp **`CaptureExample.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CaptureExample {
    // API bên ngoài nhận danh sách chưa biết kiểu.
    static void reorder(List<?> items) {
        if (items.size() > 1) {
            swap(items, 0, 1);
        }
    }

    // Helper đặt tên E cho kiểu đã được capture.
    private static <E> void swap(
            List<E> items, int left, int right) {
        E old = items.get(left);
        items.set(left, items.get(right));
        items.set(right, old);
    }

    static void duplicateUnknown(List<?> items) {
        duplicate(items);
    }

    private static <E> void duplicate(List<E> items) {
        if (!items.isEmpty()) {
            E first = items.get(0);
            items.add(first);
        }
    }

    // Wildcard có giới hạn trên được capture trong helper.
    static void repeatNumber(List<? extends Number> items) {
        repeatLast(items);
    }

    private static <E extends Number> void repeatLast(List<E> items) {
        if (!items.isEmpty()) {
            E last = items.get(items.size() - 1);
            items.add(last);
        }
    }

    public static void main(String[] args) {
        List<String> words =
                new ArrayList<>(List.of("red", "green", "blue"));
        List<?> unknown = words;

        reorder(unknown);
        System.out.println("Swapped: " + words);

        duplicateUnknown(unknown);
        System.out.println("Duplicated: " + words);

        List<Integer> integers = new ArrayList<>(List.of(2, 4));
        List<? extends Number> numbers = integers;

        repeatNumber(numbers);
        System.out.println("Numbers: " + integers);
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac -encoding UTF-8 CaptureExample.java
java CaptureExample
```

Đầu ra:

```text
Swapped: [green, red, blue]
Duplicated: [green, red, blue, green]
Numbers: [2, 4, 4]
```

Giải thích:

- `unknown` có kiểu `List<?>`, nên phía ngoài không biết phần tử là `String`. Lời gọi `reorder(unknown)` vẫn đúng: `swap` giữ cùng `E` khi đọc và ghi qua `set`.
- `duplicateUnknown` capture kiểu ẩn một lần trong lời gọi `duplicate(items)`; phần tử mới là phần tử có sẵn, đã đúng `E`.
- `repeatNumber` nhận `List<? extends Number>` và dùng helper `<E extends Number>` để nhân bản phần tử cuối. Kiểu phần tử thực tế vẫn là `Integer`, nên `[2, 4]` thành `[2, 4, 4]`.
- Tất cả danh sách được thay đổi ở ví dụ đều là `ArrayList`, có hỗ trợ `set` và `add`. Biến wildcard chỉ là góc nhìn có kiểu tĩnh rộng hơn lên cùng đối tượng.

Các phương thức helper trên an toàn mà **không cần raw type, unchecked cast hoặc suppress warning**.

## 11. Lỗi phổ biến và cách sửa

| Lỗi hoặc suy luận sai | Giải thích | Cách sửa |
| --- | --- | --- |
| `list.set(0, list.get(0))` trên `List<?>` được cho là luôn biên dịch | Những vị trí wildcard trong biểu thức không được ràng buộc theo cùng `E` | Đưa cả `get` và `set` vào `<E> helper(List<E>)` |
| Thấy `List<?>` là coi kiểu phần tử bằng `Object` | Kiểu thật có thể là `String`, `Integer`, v.v. | Hiểu `?` là kiểu chưa biết; chỉ **đọc** an toàn dưới `Object` |
| Dùng helper `<E>` để thêm một `String` bất kỳ vào `List<?>` | Không có chứng cứ kiểu `E` nhận `String` | Dùng `List<? super String>` khi cần ghi `String` |
| Coi hai `List<?>` khác nhau có cùng capture | Hai wildcard độc lập không nhất thiết cùng kiểu | Khai báo quan hệ bằng `List<E>` hoặc cặp PECS `? extends E` / `? super E` |
| Nghĩ helper cho phép mọi thao tác sửa đổi | Compiler chỉ kiểm tra kiểu, danh sách runtime có thể không hỗ trợ `add/set` | Xem hợp đồng implementation và trường hợp ngoại lệ |
| Dùng cast unchecked thay helper | Cast không chứng minh kiểu phần tử thật, có nguy cơ heap pollution | Dùng phương thức generic giữ đúng cùng `E` |
| Tạo helper chỉ để gọi `get` rồi trả `Object` | Không có nhu cầu giữ liên hệ giữa nhiều vị trí `E` | Dùng trực tiếp `List<?>` và `Object` |
| Tưởng `<E extends Number>` cho phép thêm mọi `Number` | `E` vẫn có thể là `Integer` hoặc `Double` | Chỉ ghi giá trị được chứng minh đúng `E` |

## 12. Bài tập tự kiểm tra

Các đoạn sau xét **riêng lẻ**. Giả sử đã import `java.util.*`. Nếu có khai báo `static`, hãy đặt phương thức vào thân một lớp Java; các đoạn này không phải tệp Java độc lập.

**A — Gán lại giá trị lấy từ wildcard**

```java
static void a(List<?> list) {
    list.set(0, list.get(0));
}
```

**B — Đưa thao tác vào helper**

```java
static <E> void bHelper(List<E> list) {
    if (!list.isEmpty()) {
        list.set(0, list.get(0));
    }
}
static void b(List<?> list) {
    bHelper(list);
}
```

**C — Helper thêm giá trị cụ thể vào wildcard**

```java
static <E> void addHelper(List<E> list, E x) {
    list.add(x);
}
static void c(List<?> list) {
    addHelper(list, "hello");
}
```

**D — Capture danh sách số có giới hạn trên**

```java
static <E extends Number> void doubleLast(List<E> list) {
    if (!list.isEmpty()) {
        list.add(list.get(list.size() - 1));
    }
}
static void d(List<? extends Number> list) {
    doubleLast(list);
}
```

**E — Hai wildcard độc lập**

```java
static void e(List<?> left, List<?> right) {
    right.add(left.get(0));
}
```

**F — Dùng PECS với hai vai trò riêng biệt**

```java
static <E> void f(
        List<? extends E> source,
        List<? super E> destination) {
    for (E value : source) {
        destination.add(value);
    }
}
```

**G — Có helper nhưng danh sách không sửa được**

```java
static <E> void gHelper(List<E> list) {
    if (!list.isEmpty()) {
        list.add(list.get(0));
    }
}
static void g() {
    gHelper(List.of(1, 2));
}
```

**Đáp án:**

- **A:** Không biên dịch; kiểu trả về của `get` không được chấp nhận ở vị trí `set` vì không giữ được quan hệ capture cần thiết.
- **B:** Biên dịch. `bHelper` dùng cùng `E` cho cả thao tác đọc và ghi; nếu danh sách không hỗ trợ `set`, runtime vẫn có thể từ chối.
- **C:** Không biên dịch tại lời gọi `addHelper(list, "hello")`; không thể chứng minh kiểu phần tử ẩn là `String`.
- **D:** Biên dịch. `E` capture kiểu phần tử số chưa biết và giữ đúng kiểu khi nhân bản phần tử cuối; runtime cần hỗ trợ `add`.
- **E:** Không biên dịch; kiểu phần tử của `left` không có quan hệ được chứng minh với kiểu phần tử của `right`.
- **F:** Biên dịch; nguồn cung cấp `E`, đích chấp nhận `E`. Khi gọi, compiler vẫn kiểm tra cặp đối số có quan hệ kiểu phù hợp.
- **G:** Biên dịch nhưng `List.of` không hỗ trợ thêm phần tử; lúc chạy ném `UnsupportedOperationException`.

## 13. Ghi nhớ

**Wildcard capture** là cơ chế trình biên dịch dùng để biểu diễn một kiểu wildcard chưa biết thành một kiểu tạm có danh tính và giới hạn phù hợp. **Generic helper `<E>`** cho phép mã nguồn đặt tên kiểu đó và gắn các vị trí `get`, `set`, `add` vào cùng một tham số kiểu.

Mẫu thường dùng:

```java
static void publicOperation(List<?> values) {
    helper(values);
}

private static <E> void helper(List<E> values) {
    // Đọc và ghi các giá trị đã được chứng minh có cùng kiểu E.
}
```

Kỹ thuật này không biến `List<?>` thành `List<Object>`, không cho chèn một giá trị bất kỳ từ bên ngoài và không chứng minh hai wildcard độc lập có cùng kiểu. Nó bảo toàn an toàn kiểu khi thuật toán cần **đọc rồi ghi lại phần tử có cùng kiểu thực tế**. Khi thiết kế API nguồn/đích riêng biệt, hãy diễn đạt quan hệ bằng `? extends E` và `? super E`; khi chỉ cần thao tác chuẩn, ưu tiên dùng API thư viện đã có.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-004">Hoán đổi phần tử của List&lt;?&gt; bằng phương thức hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Hoán đổi phần tử của `List<?>` bằng phương thức hỗ trợ

## Mục tiêu

Một `List<?>` có thể là `List<String>`, `List<Integer>` hoặc danh sách của một kiểu phần tử bất kỳ mà mã đang gọi không biết chính xác. Bạn vẫn có thể **đọc** phần tử dưới kiểu `Object`, nhưng thử đọc một phần tử rồi đưa lại vào `list.set(...)` ngay trong phương thức nhận `List<?>` có thể bị trình biên dịch từ chối. Đây là một tình huống tiêu biểu của **wildcard capture** (bắt giữ wildcard).

Sau bài học, bạn sẽ:

1. Giải thích được vì sao `List<?>` không tương đương `List<Object>` và tại sao việc hoán đổi trực tiếp gặp lỗi biên dịch.
2. Viết được phương thức hỗ trợ generic `<T> void swapCaptured(List<T>, int, int)` để compiler giữ mối liên hệ giữa phần tử lấy ra và phần tử ghi lại.
3. Dùng phương thức công khai `swap(List<?>, int, int)` mà không cần cast hay raw type.
4. Biết khi nào nên dùng API chuẩn `Collections.swap(List<?>, int, int)`.
5. Phân biệt **đúng kiểu khi biên dịch** với **cho phép thay phần tử tại runtime** và xử lý các trường hợp chỉ số, danh sách không sửa được.

## 1. Vấn đề: `get` đọc được nhưng `set` lại bị chặn

Xem đoạn mã sau. Đây là **ví dụ cố ý chứa lỗi biên dịch**, không phải chương trình để chạy:

```java
import java.util.List;

class BrokenSwap {
    static void swap(List<?> list, int i, int j) {
        Object saved = list.get(i);
        list.set(i, list.get(j)); // lỗi kiểu
        list.set(j, saved);       // lỗi kiểu
    }
}
```

`list.get(i)` gọi được vì một phần tử của kiểu tham chiếu bất kỳ đều có thể được quan sát dưới kiểu `Object`. Tuy nhiên, `list.set(i, element)` cần một **đối số tương thích với kiểu phần tử chính xác `E`** của chính danh sách.

`List<?>` có nghĩa kiểu phần tử là **một kiểu chưa biết**, không phải “chứa thoải mái mọi `Object`”. Giả sử danh sách thực tế là `List<Integer>`: nếu compiler cho phép truyền mọi `Object` vào `set`, ta có thể thay một số nguyên bằng `String` và phá vỡ hợp đồng kiểu của nguồn.

Bạn có thể nghĩ `list.get(j)` và `list.set(i, ...)` đều nói tới cùng list, nên phần tử vừa lấy ra đương nhiên đúng kiểu. Ý tưởng về mặt thuật toán là đúng; nhưng ở biểu thức trực tiếp, compiler không chứng minh được mối liên hệ kiểu cần thiết giữa các lần sử dụng wildcard ấy. Thông báo lỗi của `javac` thường có dạng “`Object cannot be converted to CAP#1`” hoặc nhắc tới các kiểu `capture of ?`.

Không nên sửa vấn đề bằng cách khai báo `Object saved` rồi ép kiểu, bởi `Object` đã làm mất thông tin “giá trị này đến từ đúng kiểu phần tử của danh sách”.

## 2. Wildcard capture: đặt tên cho kiểu chưa biết bằng `T`

Giải pháp là chuyển phần thao tác đọc–ghi vào một **generic helper method**:

```java
static void swap(List<?> list, int i, int j) {
    swapCaptured(list, i, j);
}

private static <T> void swapCaptured(List<T> list, int i, int j) {
    T saved = list.get(i);
    list.set(i, list.get(j));
    list.set(j, saved);
}
```

Đây là đoạn minh họa chữ ký và thân phương thức; ví dụ chạy độc lập bên dưới bổ sung kiểm tra chỉ số và xử lý `i == j`.

Cơ chế diễn ra ở **compile time**:

1. Khi gọi `swapCaptured(list, i, j)`, compiler **bắt giữ** dấu `?` của đối số `List<?>` như một kiểu nội bộ chưa biết, thường được mô tả là `CAP#1`.
2. Compiler suy luận `T` trong một lần gọi helper chính là kiểu bắt giữ ấy. Vì vậy trong toàn bộ thân `swapCaptured`, `list` có kiểu nhất quán `List<T>`.
3. `list.get(i)` trả ra `T`, nên `T saved = ...` hợp lệ.
4. `list.set(i, list.get(j))` nhận đúng một `T` từ **cùng danh sách**; `list.set(j, saved)` cũng nhận đúng `T`.
5. Caller chỉ thấy `List<?>` và có thể gọi `swap` với nhiều loại danh sách mà không cần biết `T` là gì.

**Lưu ý:** helper **không tìm ra hoặc kiểm tra kiểu phần tử thật tại runtime**. Nó giúp compiler chứng minh sự thống nhất kiểu tại một vị trí cụ thể. Do generic type erasure, Java cũng không sinh một phiên bản máy riêng của helper cho từng kiểu `T`.

### Sơ đồ quan hệ trong một lần gọi

```text
Caller:
    List<?> values        -- wildcard chưa biết --
             |
             | swapCaptured(values, i, j)
             v
Generic helper:
    List<T> list           -- T = capture của ? --
             |
     T first = get(i)      -- đọc T --
             |
     set(i, get(j))        -- ghi T vào List<T> --
     set(j, first)         -- ghi T vào List<T> --
```

Tất cả thao tác vẫn thực hiện trên **chính đối tượng list của caller**. Không hề sao chép danh sách hay biến list thành `List<Object>`.

## 3. Cài đặt hoàn chỉnh trên Java 17+

Phiên bản thực tế nên kiểm tra đối tượng và chỉ số trước khi đụng tới dữ liệu, đồng thời coi việc đổi một vị trí với chính nó là thao tác không cần thay phần tử.

Tạo tệp tên **`WildcardSwapDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class WildcardSwapDemo {
    public static void swap(List<?> values, int i, int j) {
        Objects.requireNonNull(values, "values");
        Objects.checkIndex(i, values.size());
        Objects.checkIndex(j, values.size());
        swapCaptured(values, i, j);
    }

    private static <T> void swapCaptured(List<T> values, int i, int j) {
        if (i == j) {
            return;
        }
        T saved = values.get(i);
        values.set(i, values.get(j));
        values.set(j, saved);
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("An", "Binh", "Chi"));
        List<?> unknown = names;
        swap(unknown, 0, 2);
        System.out.println("names: " + names);

        List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
        swap(numbers, 0, 1);
        System.out.println("numbers: " + numbers);

        Collections.swap(unknown, 0, 1);
        System.out.println("Collections.swap: " + names);

        List<String> fixed = Arrays.asList("red", "blue");
        swap(fixed, 0, 1);
        System.out.println("fixed-size: " + fixed);

        List<String> immutable = List.of("x", "y");
        try {
            swap(immutable, 0, 1);
        } catch (UnsupportedOperationException e) {
            System.out.println("immutable: unsupported");
        }

        List<String> one = new ArrayList<>(List.of("only"));
        swap(one, 0, 0);
        System.out.println("same index: " + one);

        try {
            swap(one, 0, 1);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("invalid index: " + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp với JDK 17 trở lên:

```bash
javac WildcardSwapDemo.java
java WildcardSwapDemo
```

**Đầu ra chuẩn:**

```text
names: [Chi, Binh, An]
numbers: [20, 10, 30]
Collections.swap: [Binh, Chi, An]
fixed-size: [blue, red]
immutable: unsupported
same index: [only]
invalid index: IndexOutOfBoundsException
```

### Đọc kết quả từng bước

- `names` thực sự là `List<String>` chứa `[An, Binh, Chi]`; biến `unknown` kiểu `List<?>` cùng trỏ tới danh sách. `swap(unknown, 0, 2)` đổi phần tử ở vị trí `0` và `2`, nên kết quả `[Chi, Binh, An]`.
- `swap(numbers, 0, 1)` áp dụng cùng một phương thức cho `List<Integer>` và tạo `[20, 10, 30]`; mã không cần biết trước `T` là `Integer` hay `String`.
- `Collections.swap(unknown, 0, 1)` là API chuẩn của JDK, đổi hai vị trí đầu thành `[Binh, Chi, An]`.
- `Arrays.asList("red", "blue")` tạo danh sách **kích thước cố định** nhưng hỗ trợ `set`, vì vậy vẫn hoán đổi được hai giá trị.
- `List.of("x", "y")` tạo danh sách **không cho sửa**; gọi `set` bên trong helper ném `UnsupportedOperationException`, dù chữ ký generic hoàn toàn hợp lệ.
- Khi `i == j`, helper tự trả về sau khi hai chỉ số đã được xác nhận hợp lệ. Với `[only]`, nội dung giữ nguyên.
- `Objects.checkIndex(1, one.size())` ném `IndexOutOfBoundsException` vì danh sách một phần tử chỉ có chỉ số hợp lệ là `0`.

Ví dụ hoàn chỉnh này dùng import chính xác từ `java.util`. `Objects.checkIndex` có từ Java 9, vì vậy chạy được trên Java 17.

## 4. Vì sao helper là giải pháp đúng về kiểu?

Trong `swapCaptured`, ta khai báo:

```java
private static <T> void swapCaptured(List<T> values, int i, int j) {
    T saved = values.get(i);
    values.set(i, values.get(j));
    values.set(j, saved);
}
```

Kiểu `T` biểu thị **cùng một kiểu phần tử cho cả ba thao tác**. Compiler kiểm tra:

| Biểu thức | Kiểu được compiler nhìn thấy | Ý nghĩa |
| --- | --- | --- |
| `values` | `List<T>` | Danh sách có kiểu phần tử `T` |
| `values.get(i)` | `T` | Đọc đúng kiểu của danh sách |
| `T saved` | `T` | Lưu giá trị không mất thông tin kiểu |
| `values.set(i, values.get(j))` | `T` truyền vào `set(int, T)` | Ghi phần tử cùng kiểu |
| `values.set(j, saved)` | `T` truyền vào `set(int, T)` | Phục hồi giá trị cũ tại vị trí kia |

Tương phản với phương thức chỉ nhận `List<?>`, nếu bạn viết `Object saved = list.get(i)`, biểu thức `saved` chỉ còn kiểu tĩnh `Object`. Một `List<Integer>` không cho phép compiler coi mọi `Object` là `Integer`. Vì vậy **giữ kiểu `T` xuyên suốt phép hoán đổi** là mấu chốt.

Không cần ràng buộc kiểu `<T extends Number>` vì thuật toán hoán đổi không thực hiện phép toán số học hay cần tính chất nào của `Number`. Nó chỉ vận chuyển phần tử qua hai vị trí cùng danh sách. Đây là lý do helper **không giới hạn** chạy với kiểu dữ liệu cụ thể.

Nếu một ngày cần xử lý kiểu cụ thể, chẳng hạn tính toán trên `Number`, hãy thêm bound vì **nhu cầu xử lý** chứ không phải vì bản thân việc swap cần bound.

## 5. `Collections.swap`: khi nào nên dùng sẵn API chuẩn?

JDK đã có phương thức:

```java
public static void swap(List<?> list, int i, int j)
```

Đây là **chữ ký API** được trích ở dạng tối giản; phương thức thực sự thuộc lớp `java.util.Collections`. Bạn có thể gọi trực tiếp:

```java
List<String> words = new ArrayList<>(List.of("left", "right"));
List<?> view = words;
Collections.swap(view, 0, 1);
System.out.println(words); // [right, left]
```

Đoạn code trên minh họa các lệnh trong phương thức đã import `List`, `ArrayList`, `Collections`.

Điều đáng chú ý là chính API chuẩn cũng công khai nhận `List<?>`. Điều đó **không có nghĩa mọi phần tử đều là `Object`**, mà thể hiện rằng thao tác đổi vị trí **không phụ thuộc vào kiểu phần tử cụ thể**. Người dùng API được hưởng lợi từ tính tổng quát mà không phải viết hoặc nhìn thấy cơ chế bắt giữ wildcard bên trong.

**Trong ứng dụng thực tế, nếu chỉ cần hoán đổi hai vị trí, ưu tiên `Collections.swap`.** Tự viết helper là cách học capture và hữu ích khi một thuật toán tùy chỉnh khác cần liên kết các giá trị lấy từ cùng một kiểu generic.

`Collections.swap` có thể ném `IndexOutOfBoundsException` nếu chỉ số không hợp lệ, và `UnsupportedOperationException` nếu danh sách không hỗ trợ `set`. Nó không biến các danh sách immutable thành mutable.

## 6. Hoán đổi là thay phần tử, không phải thay kích thước

Một phép swap thông thường giữ nguyên số phần tử và chỉ thay giá trị tại hai vị trí. Do đó nó cần khả năng **lấy `get` và thay `set`** chứ không cần `add` hoặc `remove` để thay đổi kích thước.

| Loại danh sách | Thay bằng `set` | Swap được khi chỉ số hợp lệ? | Ghi chú |
| --- | --- | --- | --- |
| `new ArrayList<>(...)` | Có | Có | Có thể thay phần tử theo chỉ số |
| `new LinkedList<>(...)` | Có | Có | Truy cập theo chỉ số có thể tốn thời gian |
| `Arrays.asList(...)` | Có | Có | Kích thước cố định nhưng hỗ trợ thay phần tử |
| `List.of(...)` | Không | Không | Danh sách không cho sửa |
| `List.copyOf(...)` | Không | Không | Danh sách không cho sửa |
| `Collections.unmodifiableList(...)` | Không qua wrapper | Không qua wrapper | Có thể phản ánh thay đổi nguồn từ alias khác |

Vì vậy, hai khái niệm cần tách:

- **Type-safety:** helper bảo đảm không đưa sai kiểu phần tử vào danh sách.
- **Mutability:** implementation quyết định có chấp nhận `set` hay không.

Một lời gọi như `swap(List.of("a", "b"), 0, 1)` **biên dịch được**; đến runtime mới ném `UnsupportedOperationException`. Còn `swap(Arrays.asList("a", "b"), 0, 1)` thực hiện thành công vì nó không cần thay đổi kích thước.

Đối với `Collections.unmodifiableList(source)`, alias `source` có thể vẫn mutable; wrapper chỉ chặn thao tác thay qua chính wrapper. Wildcard và unmodifiable wrapper là hai cơ chế khác nhau.

## 7. Kiểm soát chỉ số và cùng một vị trí

Cài đặt mẫu gọi:

```java
Objects.checkIndex(i, values.size());
Objects.checkIndex(j, values.size());
```

Đây là các lệnh minh họa trong phương thức. Với một list có `size() == n`, chỉ số hợp lệ thỏa `0 <= index < n`. Kiểm tra cả hai trước khi bắt đầu đổi sẽ cho lỗi rõ ràng nếu có chỉ số âm hoặc vượt giới hạn, đồng thời tránh trường hợp sửa vị trí đầu rồi mới phát hiện vị trí thứ hai không hợp lệ.

Nếu `i == j`, hai phần tử vốn ở cùng một vị trí, nên việc swap không thay đổi kết quả. Helper minh họa **trả về sớm** để không phải gọi `get/set` khi không cần thiết, nhưng vẫn kiểm tra tính hợp lệ của chỉ số trước đó. Lựa chọn này còn khiến một danh sách không hỗ trợ `set` có thể chấp nhận trường hợp cùng chỉ số vì không phải thực hiện thao tác thay đổi nào; đừng suy rộng đặc điểm đó cho mọi implementation swap khác.

Nếu danh sách rỗng, không có chỉ số hợp lệ để swap hai phần tử. Hai tham số chỉ số **không âm** cũng chưa đủ; phải nhỏ hơn `size()` hiện tại.

Các kiểm tra trên không bảo đảm an toàn nếu một luồng khác đang thay đổi cấu trúc danh sách đồng thời. Cần cơ chế đồng bộ khi truy cập chia sẻ.

## 8. Không dùng raw type hoặc cast để né wildcard capture

Một giải pháp hấp tấp là cast `List<?>` về `List<Object>`, hoặc dùng raw `List` để ép lời gọi `set` biên dịch:

```java
// Ví dụ cố ý KHÔNG an toàn, không nên sử dụng:
@SuppressWarnings({"rawtypes", "unchecked"})
static void unsafe(List<?> values, int index) {
    List raw = values;
    raw.set(index, "some text");
}
```

Đây là phương thức minh họa về lỗi thiết kế. Nếu `values` thực sự trỏ tới `List<Integer>`, cách làm này có thể đặt một `String` vào danh sách mà code còn lại tin rằng chỉ chứa `Integer`. Chương trình có thể gặp `ClassCastException` khi đọc về sau.

Helper `<T>` tránh được vấn đề ấy vì:

1. Không thay đổi kiểu phần tử thực của list.
2. Chỉ chuyển chính các giá trị được đọc từ `List<T>` vào lại `List<T>`.
3. Không cần `@SuppressWarnings("unchecked")`.
4. Compiler kiểm tra toàn bộ các phép `set` theo một `T` nhất quán.

Điểm quan trọng: **không phải mọi helper nhận `List<T>` đều an toàn**, mà an toàn đến từ cách dùng `T`. Nếu truyền các giá trị không liên quan qua raw type hoặc cast không kiểm tra, bạn vẫn có thể phá ràng buộc kiểu.

## 9. Capture dành cho một danh sách, đừng ghép hai wildcard khác nhau

Hai biến cùng khai báo `List<?>` **không bảo đảm chúng có cùng kiểu phần tử**:

```java
List<?> left = List.of("a", "b");
List<?> right = List.of(1, 2);

// Không được giả định phần tử từ left ghi được vào right,
// hoặc ngược lại.
```

`left` có thể chứa `String` trong khi `right` chứa `Integer`. Vì vậy, một phương thức hỗ trợ nhận **một** `List<T>` là đủ để hoán đổi **hai vị trí của chính danh sách đó**. Nếu muốn hoán đổi phần tử giữa hai list khác nhau, phải thiết kế ràng buộc kiểu cho cả hai list: hai wildcard độc lập không chứng minh được phần tử của list này tương thích với list kia.

Một ví dụ về chữ ký cần quan hệ chung, chỉ mang tính minh họa:

```java
static <T> void exchange(List<T> a, int i, List<T> b, int j) {
    T saved = a.get(i);
    a.set(i, b.get(j));
    b.set(j, saved);
}
```

Phương thức trên đòi **cả hai list được chấp nhận dưới cùng `T`** và đều phải hỗ trợ `set`. Nó không phải giải pháp tổng quát cho hai `List<?>` độc lập; không nên tự ép chúng thành một kiểu chung để tránh lỗi compiler. Khi bài toán chỉ là swap nội bộ một list, dùng `swap(List<?>)` đơn giản hơn và chính xác hơn.

## 10. Độ phức tạp và giới hạn của thuật toán

Helper thực hiện tối đa hai lần `get` và hai lần `set`, cùng một số phép kiểm tra hằng số:

- Với `ArrayList`, truy cập theo chỉ số thường O(1), nên swap theo hai chỉ số là O(1) thời gian.
- Với `LinkedList`, truy cập vị trí theo chỉ số thường cần duyệt các node, chi phí có thể tới O(n) đối với danh sách dài.
- Helper chỉ lưu một tham chiếu `T saved`, nên dùng O(1) bộ nhớ phụ.
- Hoán đổi **không sao chép sâu** phần tử; nó chỉ thay những tham chiếu nằm ở hai vị trí.

Nếu một implementation cho phép `set` ở vị trí đầu nhưng từ chối hoặc lỗi ở lần `set` tiếp theo, phương thức không cam kết tính nguyên tử/rollback. Với các list mutable bình thường, hai lần `set` thực hiện thành công; nhưng không nên quảng bá helper như một giao dịch bất khả phân.

Khi phần tử bản thân là đối tượng mutable, swap chỉ thay thứ tự tham chiếu tới các đối tượng đó; trạng thái nội tại của đối tượng không thay đổi. Nếu hai vị trí cùng tham chiếu đến cùng một đối tượng, swap không tạo sự khác biệt quan sát được.

Không có một generic wildcard nào tự tạo ra khả năng thread-safe. Nếu list được chia sẻ giữa các luồng, phải tuân thủ hợp đồng đồng bộ của collection được dùng.

## 11. Những hiểu lầm thường gặp

**“`List<?>` luôn không cho sửa.”** Sai. Wildcard hạn chế khả năng đưa một giá trị có kiểu chưa biết vào list qua biến, nhưng helper generic vẫn có thể thực hiện `set` với phần tử lấy ra từ chính list, nếu implementation hỗ trợ.

**“Có thể đổi `List<?>` thành `List<Object>` cho dễ.”** Sai. `List<String>` không phải subtype của `List<Object>`. Raw/cast không kiểm tra có thể phá an toàn kiểu.

**“`List<?>.get(...)` trả về `Object` nên ghi `Object` ấy trở lại chắc chắn hợp lệ.”** Về mặt logic, giá trị từ cùng list đúng kiểu; nhưng biểu thức `Object` không giữ được quan hệ kiểu đó để compiler chứng minh. Generic helper tạo `T` nhất quán cho cả lần đọc và lần ghi.

**“Helper `<T>` biến list thành `List<T>` mới.”** Sai. Nó không tạo list, không copy, không sửa kiểu runtime; `T` là biến kiểu phục vụ kiểm tra compile time.

**“`Collections.swap` nhận `List<?>` nên đổi được cả `List.of`.”** Sai. Chữ ký nhận được nhiều loại generic, nhưng đối tượng immutable vẫn từ chối `set`.

**“Danh sách kích thước cố định không thể swap.”** Không đúng trong mọi trường hợp. `Arrays.asList` không cho thêm/xóa làm đổi kích thước, nhưng vẫn hỗ trợ thay thế bằng `set`.

**“Swap hai vị trí ở hai `List<?>` cũng dùng cùng helper này.”** Không thể mặc nhiên, vì hai wildcard có thể bắt giữ hai kiểu phần tử hoàn toàn khác nhau.

## 12. Tự kiểm tra

**Câu 1.** Tại sao `swap(List<?> list, ...)` gọi được với cả `List<Integer>` và `List<String>`?

Vì mọi `List<T>` đều có thể được tham chiếu qua `List<?>`. Trong lần gọi helper, compiler bắt giữ một kiểu phần tử nhất quán cho riêng danh sách đó.

**Câu 2.** Trong helper, vì sao dùng `T saved` tốt hơn `Object saved`?

Vì `T saved` giữ đúng kiểu phần tử của `List<T>` để truyền lại vào `set(int, T)`. Với `Object`, compiler không thể coi đó là `T` một cách tổng quát.

**Câu 3.** Với `List<?> view = Arrays.asList("x", "y")`, `swap(view, 0, 1)` có chạy được không?

Có. `Arrays.asList` hỗ trợ `set` và swap không thay đổi kích thước, nên kết quả là `[y, x]`.

**Câu 4.** Với `List<?> view = List.of("x", "y")` thì sao?

Biên dịch được, nhưng tại runtime phương thức cố `set` và ném `UnsupportedOperationException`.

**Câu 5.** Có phải `Collections.swap` yêu cầu `List<Object>`?

Không. Chữ ký công khai là `swap(List<?>, int, int)`; nó chấp nhận list có kiểu phần tử bất kỳ.

**Câu 6.** Nếu danh sách có 3 phần tử, `swap(list, 0, 3)` cho kết quả gì?

`Objects.checkIndex(3, 3)` ném `IndexOutOfBoundsException` trước khi dữ liệu được thay, vì các chỉ số hợp lệ chỉ là `0`, `1`, `2`.

**Câu 7.** Nếu `swap(list, 1, 1)` và vị trí `1` hợp lệ, helper ở ví dụ chính có thay phần tử không?

Không. Sau khi xác minh hai chỉ số, helper thấy `i == j` và trở về mà không thực hiện `get/set`.

## Ghi nhớ

`List<?>` lưu một **kiểu phần tử chưa biết**, nên không được tùy ý đưa giá trị `Object` vào qua `set`. **Wildcard capture** giúp một generic helper `<T>` giữ cùng kiểu `T` giữa `get` và `set`, từ đó hoán đổi phần tử của chính danh sách mà không cần raw type hay cast. Khi chỉ cần chức năng này trong chương trình, có thể dùng trực tiếp `Collections.swap(List<?>, int, int)`. Dù dùng cách nào, danh sách thực tế vẫn phải hỗ trợ thay thế phần tử, chỉ số phải hợp lệ và generics không tự bảo đảm tính nguyên tử hay đồng bộ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-005">Vì sao hai wildcard giống cách viết vẫn có thể là hai kiểu khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao hai wildcard giống cách viết vẫn có thể là hai kiểu khác nhau?

## 1. Vấn đề: cùng viết `?` nhưng không chắc cùng một kiểu

Trong Java Generics, dấu `?` (*wildcard*) đại diện cho **một kiểu chưa biết**. Khi hai vị trí đều viết `List<?>`, ta chỉ biết mỗi danh sách có **một loại phần tử nào đó**; không có lời hứa rằng hai loại phần tử phải giống nhau.

~~~java
List<String> words = List.of("A", "B");
List<Integer> numbers = List.of(1, 2);

List<?> first = words;
List<?> second = numbers;

Object a = first.get(0);
Object b = second.get(0);
~~~

Hai biến `first` và `second` cùng có **kiểu khai báo** `List<?>`. Nhưng một danh sách chứa `String`, danh sách kia chứa `Integer`. Vì vậy không có cơ sở để chuyển phần tử lấy từ `first` sang `second`:

~~~java
// KHÔNG biên dịch:
second.add(first.get(0));
~~~

Đó là tình huống quan trọng nhất khi học **wildcard capture**. Khi compiler cần kiểm tra phép gọi phương thức của một generic type chứa wildcard, nó có thể **bắt giữ** (*capture*) kiểu chưa biết thành một type variable mới để suy luận. Hai lần capture có thể được compiler biểu diễn bằng `CAP#1` và `CAP#2`, mỗi kiểu có ràng buộc riêng. Cùng hình thức `?` **không tự tạo quan hệ bằng nhau** giữa các capture.

Trong bài này, ta sẽ phân biệt kiểu khai báo và kiểu capture, giải thích cả trường hợp hai vị trí dùng **chính một biến `List<?>`**, kiểm chứng lỗi biên dịch thật, rồi sửa thiết kế bằng generic helper `<T>` hoặc quan hệ PECS phù hợp.

## 2. Kiểu khai báo giống nhau khác với danh tính kiểu bị capture

### 2.1. `List<?>` là một kiểu hợp lệ; `CAP#1` không phải tên do lập trình viên viết

Ba khai báo sau đều hợp lệ:

~~~java
List<?> unknownA = List.of("Java");
List<?> unknownB = List.of(42);
List<?> unknownC = List.of(3.5);
~~~

Các biến có cùng *declared type* `List<?>` và đều có thể đọc phần tử như `Object`. Đây là một quan hệ an toàn, không có unchecked conversion.

Tuy nhiên, trong một phép gọi như `unknownB.add(...)`, tham số `E` của `List<E>` phải được xác định đủ để compiler biết giá trị có thể thêm không. Java áp dụng **capture conversion** để tạm biểu diễn wildcard bằng một kiểu ẩn, chẳng hạn:

~~~text
List<?>                -> List<CAP#1>
CAP#1 extends Object
~~~

Đây là **sơ đồ mô hình hóa**, không phải cú pháp Java. `CAP#1` là tên chẩn đoán do compiler tạo, không phải một class, một runtime type token hay một tham số kiểu người lập trình có thể viết trực tiếp.

Một phép capture khác có thể tạo ra:

~~~text
List<?>                -> List<CAP#2>
CAP#2 extends Object
~~~

Hai ràng buộc `CAP#1 extends Object` và `CAP#2 extends Object` **không suy ra** `CAP#1 == CAP#2`, `CAP#1 <: CAP#2` hoặc `CAP#2 <: CAP#1`. Việc compiler dùng nhãn `CAP#1`, `CAP#2` theo thứ tự nào là chi tiết thông báo lỗi; điều quan trọng là **không tồn tại bằng chứng tương thích giữa hai kiểu chưa biết**.

### 2.2. Capture là thao tác ở compile time, không làm thay đổi list

Khi gán `List<String>` cho `List<?>`, Java không chuyển phần tử sang kiểu mới và không tạo bản sao. Cả hai reference có thể cùng trỏ một object danh sách. **Capture conversion chỉ giúp hệ thống kiểu phân tích một biểu thức**.

Nó không được lưu lại ở runtime thành những loại `CAP#1`/`CAP#2`. Generic type arguments phần lớn bị *type erasure* trong runtime representation. Java phải quyết định lời gọi `add`, `set` hay `get` có hợp lệ **trước khi chạy**.

### 2.3. “Không bằng nhau” không có nghĩa mọi việc đều bị cấm

~~~java
List<?> x = List.of("A");
List<?> y = List.of(10);

Object left = x.get(0);  // Hợp lệ.
Object right = y.get(0); // Hợp lệ.

boolean same = left.equals(right); // Hợp lệ: equals nhận Object.
boolean hasIt = y.contains(left);  // Hợp lệ: contains nhận Object.
~~~

`contains(Object)` không đòi đối số có đúng type parameter `E` của list. Trong khi đó `add(E)` và `set(int,E)` cần một giá trị phù hợp với `E`. Chính **chữ ký phương thức** quyết định liệu quan hệ giữa các kiểu capture có cần được chứng minh hay không.

## 3. Trường hợp hai biến `List<?>`: đọc được, nhưng không được chuyển phần tử tùy ý

Xét phương thức chỉ minh họa lỗi:

~~~java
static void brokenTransfer(List<?> source, List<?> target) {
    target.add(source.get(0)); // KHÔNG biên dịch.
}
~~~

Phân tích một cách khái niệm:

1. `source.get(0)` trả về giá trị thuộc một kiểu capture chưa biết, tạm gọi `CAP#1`.
2. `target.add(...)` chỉ nhận kiểu phần tử của `target`, tạm gọi `CAP#2`.
3. Không có bảo đảm `CAP#1` là subtype của `CAP#2`.
4. Compiler từ chối việc chuyển phần tử.

Ví dụ phản chứng cụ thể:

~~~java
List<String> source = List.of("text");
List<Integer> target = new ArrayList<>();
// Nếu được phép truyền cả hai như List<?> rồi target.add(source.get(0)),
// String sẽ bị thêm vào danh sách đã khai báo List<Integer>.
~~~

Dù một lần chạy nào đó người gọi truyền hai `List<String>`, chữ ký `brokenTransfer(List<?>, List<?>)` phải bảo đảm type safety cho **mọi cặp** hợp lệ, bao gồm `List<String>` và `List<Integer>`.

**Không nên chữa bằng ép kiểu raw/unchecked.** Ép `List<?>` sang `List<String>` khi chưa có bằng chứng về kiểu phần tử chỉ chuyển rủi ro sang runtime và có thể tạo heap pollution.

## 4. Cùng một biến `List<?>` vẫn có thể phát sinh hai capture khác nhau

Điểm dễ gây ngạc nhiên nhất: kể cả **một tham chiếu duy nhất** cũng không bảo đảm compiler nhận ra hai kết quả capture trong các biểu thức riêng rẽ là một kiểu có thể gán cho nhau.

~~~java
static void brokenSelfAssign(List<?> list) {
    list.set(0, list.get(0)); // KHÔNG biên dịch.
}
~~~

Ở góc nhìn con người, `get(0)` lấy một phần tử từ đúng list mà `set(0, ...)` sẽ ghi lại, nên thao tác này có thể an toàn nếu list mutable. Nhưng compiler phân tích các biểu thức generic chứa wildcard bằng capture conversion. Khi kiểm tra lời gọi `set`, nó có thể thấy một kiểu `CAP#1` cho tham số `E` cần ghi và một kiểu `CAP#2` cho kết quả `get`. Việc hai biểu thức dùng **cùng biến nguồn** không tự sinh ra ràng buộc `CAP#1 == CAP#2` trong lời gọi đó.

Biên dịch bằng Java 17 với `javac -Xdiags:verbose` sẽ cho lỗi với nội dung tương đương:

~~~text
required: int,CAP#1
found:    int,CAP#2
...
CAP#1 extends Object from capture of ?
CAP#2 extends Object from capture of ?
~~~

Thứ tự/cách đánh số CAP có thể khác theo compiler và phiên bản. Dòng `set(0, list.get(0))` **không biên dịch** dù hai biểu thức tham chiếu cùng object.

**Điểm chính xác cần ghi nhớ:** Không nên coi mỗi biến `List<?>` có một “mã CAP cố định” suốt cả phương thức. Những tên CAP là kết quả phân tích các biểu thức/capture; hãy giải quyết bằng quan hệ kiểu được biểu diễn rõ ràng thay vì dựa vào tên biến hay sự đồng nhất object lúc chạy.

### 4.1. Alias cũng không tự tạo bằng chứng capture bằng nhau

~~~java
List<String> actual = new ArrayList<>(List.of("A", "B"));
List<?> a = actual;
List<?> b = actual;
~~~

Hai reference `a` và `b` cùng trỏ đến `actual` tại runtime. Tuy nhiên, một hàm có chữ ký `f(List<?> a, List<?> b)` không thể giả định hai biến luôn trỏ chung object, hay có cùng kiểu phần tử trong mọi lần gọi. Khi muốn trao đổi giá trị theo type parameter, phải đặt quan hệ đó trong chữ ký hoặc capture qua helper cho **một list duy nhất**.

## 5. Wildcard giống nhau với `extends` cũng chưa chắc cùng subtype

Tình huống không chỉ xảy ra với `List<?>`. Hai biến cùng khai báo `List<? extends Number>` có thể tham chiếu đến:

~~~java
List<? extends Number> ints = List.of(2, 4);
List<? extends Number> doubles = List.of(1.5, 2.5);

Number a = ints.get(0);    // Hợp lệ.
Number b = doubles.get(0); // Hợp lệ.
~~~

Nhưng không thể chuyển trực tiếp giá trị đọc từ một list sang list kia:

~~~java
static void brokenUpperBound(
        List<? extends Number> source,
        List<? extends Number> target) {
    target.add(source.get(0)); // KHÔNG biên dịch.
}
~~~

Có thể mô hình hóa hai capture độc lập:

~~~text
CAP#1 <: Number
CAP#2 <: Number
~~~

Cả hai đều là subtype của `Number`; điều đó **không chứng minh CAP#1 là subtype của CAP#2**. Nếu `source` thực sự là `List<Integer>` và `target` là `List<Double>`, việc ghi `Integer` vào list `Double` sẽ sai.

Đây là lý do `? extends Number` phù hợp làm **nguồn đọc**: nó bảo đảm một kiểu chung ở chiều đọc, nhưng không cho phép đưa một `Number` tùy ý vào.

## 6. Wildcard `super`: có thể ghi giá trị đã biết, không thể suy ra kết quả đọc có cùng kiểu

Với `List<? super Integer>`, mỗi list có một kiểu phần tử chưa biết, nhưng **mỗi kiểu ấy đều nhận `Integer`**:

~~~java
List<? super Integer> x = new ArrayList<Number>();
List<? super Integer> y = new ArrayList<Object>();

x.add(10); // Hợp lệ.
y.add(20); // Hợp lệ.
~~~

Thế nhưng việc **chuyển phần tử đọc từ `x` sang `y`** vẫn không được bảo đảm:

~~~java
static void brokenLowerBound(
        List<? super Integer> source,
        List<? super Integer> target) {
    target.add(source.get(0)); // KHÔNG biên dịch.
}
~~~

Lý do: `source` có thể là `List<Object>` đang chứa `String`, trong khi `target` có thể là `List<Integer>`. Phần tử đọc từ `List<? super Integer>` chỉ được bảo đảm như `Object`, không phải `Integer`.

Có thể an toàn khi **thêm chính biến `Integer` đã có**, không phải mọi phần tử tình cờ đọc ra từ một list có thể nhận Integer:

~~~java
Integer n = 10;
x.add(n);
y.add(n);
~~~

Kết luận chung cho cả `?`, `? extends T` và `? super T`: **hai wildcard có cùng biểu thức giới hạn không có nghĩa chúng biểu diễn cùng type argument**.

## 7. Sửa lỗi cho một list: wildcard capture helper với `<T>`

Trường hợp `brokenSelfAssign(List<?>)` có thể được giải quyết bằng **phương thức helper generic**. API ngoài vẫn cho phép bất cứ `List<?>` nào; bên trong helper, ta **đặt tên cho kiểu phần tử** để dùng nhất quán qua `get` và `set`.

~~~java
static void swapEnds(List<?> values) {
    swapEndsCaptured(values);
}

private static <T> void swapEndsCaptured(List<T> values) {
    if (values.size() < 2) {
        return;
    }
    int last = values.size() - 1;
    T temporary = values.get(0);
    values.set(0, values.get(last));
    values.set(last, temporary);
}
~~~

**Cơ chế:**

1. Lời gọi `swapEndsCaptured(values)` cho phép compiler suy luận một `T` tương ứng với kiểu được capture của list nguồn.
2. Trong helper, cả kết quả `get(...)` và đối số `set(..., value)` đều liên hệ với cùng **tham số kiểu `T` có tên**.
3. `T temporary = values.get(0)` tạo một biến có kiểu tĩnh `T`. Hai lần `set` cũng cần `T`, nên type checking thành công mà không có cast.
4. Caller không cần biết list chứa `String`, `Integer` hay kiểu cụ thể nào.

Đây là một phương thức an toàn về **kiểu phần tử**. Nó vẫn đòi collection mutable; `List.of(...)` không hỗ trợ `set` và có thể ném `UnsupportedOperationException` nếu đưa trực tiếp vào `swapEnds`. Việc kiểm tra `size() < 2` bảo vệ các danh sách rỗng/1 phần tử khỏi lỗi chỉ số.

### 7.1. Vì sao helper thành công còn biểu thức lồng trực tiếp lại thất bại?

Trong helper `List<T>`, **một chữ `T` được khai báo** và dùng tại mọi vị trí liên quan. Trong `List<?>`, mỗi lần kiểm tra biểu thức liên quan đến wildcard có thể cần capture khác. Việc compiler chấp nhận phương thức helper **không chứng minh các wildcard từ những tham số không liên quan đều giống nhau**: helper đang xử lý một object list dưới một `T` thống nhất.

Với hai biến `List<?> source` và `List<?> target` độc lập, không thể tùy ý làm cho chúng có cùng `T` chỉ bằng cách gọi một helper `<T>`; caller phải cung cấp **quan hệ kiểu thật sự** để compiler kiểm tra.

## 8. Hai list độc lập: biểu diễn quan hệ trong chữ ký phương thức

Nếu hai list thực sự cùng một loại phần tử, một phương thức có type parameter ở cả hai vị trí là hợp lý:

~~~java
static <T> void swapFirst(List<T> left, List<T> right) {
    T temporary = left.get(0);
    left.set(0, right.get(0));
    right.set(0, temporary);
}
~~~

Phương thức trên chấp nhận **hai `List<String>`** hoặc **hai `List<Integer>`**; nó bảo đảm type argument của cả hai phù hợp với cùng `T`. Giả định: hai list có ít nhất một phần tử và cho phép `set`.

~~~java
List<String> left = new ArrayList<>(List.of("A"));
List<String> right = new ArrayList<>(List.of("B"));
swapFirst(left, right); // Hợp lệ.
~~~

Nhưng một cặp `List<String>` và `List<Integer>` không thể truyền cho `swapFirst` theo kiểu `List<T>` chung; việc này là **đúng với hợp đồng** của thuật toán trao đổi phần tử. Đừng dùng raw type để ép lời gọi vượt qua giới hạn đó.

### 8.1. Trường hợp producer/consumer: `<T>` kết hợp với hai wildcard khác hướng

Đôi khi source và target không có cùng type argument nhưng vẫn **chuyển phần tử an toàn** được. Ví dụ `List<Integer>` cung cấp `Integer` cho `List<Number>`.

~~~java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
~~~

`T` là cầu nối giữa nơi đọc và nơi ghi; các wildcard thể hiện vai trò:

- `source: List<? extends T>` bảo đảm **đọc ra `T`**.
- `destination: List<? super T>` bảo đảm **có thể ghi `T`**.
- Không cần bắt hai type argument thực tế phải giống nhau: một bên có thể là `Integer`, bên kia là `Number`.

Ví dụ:

~~~java
List<Integer> ints = List.of(2, 4);
List<Number> numbers = new ArrayList<>(List.of(1.5));
appendAll(ints, numbers);
System.out.println(numbers); // [1.5, 2, 4]
~~~

Đây là áp dụng **PECS** (*Producer Extends, Consumer Super*) cho một quan hệ mà Java có thể kiểm tra. Nó **khác** với việc giả định hai wildcard cùng ký pháp là cùng một capture.

API chuẩn `Collections.copy(List<? super T> dest, List<? extends T> src)` thể hiện cùng mô hình quan hệ kiểu, nhưng hành vi của nó là **thay thế phần tử theo vị trí**, yêu cầu list đích có đủ kích thước; không phải append như đoạn code trên.

## 9. Chương trình Java 17+ chạy được để quan sát sự khác biệt

Tạo tệp **WildcardCaptureIdentityDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class WildcardCaptureIdentityDemo {
    static void rotateEnds(List<?> values) {
        rotateEndsCaptured(values);
    }

    private static <T> void rotateEndsCaptured(List<T> values) {
        if (values.size() < 2) {
            return;
        }
        int last = values.size() - 1;
        T temporary = values.get(0);
        values.set(0, values.get(last));
        values.set(last, temporary);
    }

    static <T> void swapFirst(List<T> left, List<T> right) {
        T temporary = left.get(0);
        left.set(0, right.get(0));
        right.set(0, temporary);
    }

    static <T> void appendAll(List<? extends T> source, List<? super T> destination) {
        for (T element : source) {
            destination.add(element);
        }
    }

    public static void main(String[] args) {
        List<String> left = new ArrayList<>(List.of("A", "B", "C"));
        List<String> right = new ArrayList<>(List.of("X", "Y"));

        rotateEnds(left);
        System.out.println("rotated: " + left);

        swapFirst(left, right);
        System.out.println("left: " + left);
        System.out.println("right: " + right);

        List<?> view1 = left;
        List<?> view2 = right;
        Object first = view1.get(0);
        Object second = view2.get(0);
        System.out.println("read: " + first + " / " + second);

        List<Integer> integers = List.of(2, 4);
        List<Number> destination = new ArrayList<>(List.of(1.5));
        appendAll(integers, destination);
        System.out.println("numbers: " + destination);

        List<? extends Number> n1 = List.of(5);
        List<? extends Number> n2 = List.of(8.5);
        Number a = n1.get(0);
        Number b = n2.get(0);
        System.out.println("numeric views: " + a + " / " + b);
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac --release 17 -Xlint:all WildcardCaptureIdentityDemo.java
java WildcardCaptureIdentityDemo
~~~

**Stdout chính xác**:

~~~text
rotated: [C, B, A]
left: [X, B, A]
right: [C, Y]
read: X / C
numbers: [1.5, 2, 4]
numeric views: 5 / 8.5
~~~

Chương trình đã được kiểm tra với `javac --release 17 -Xlint:all`: biên dịch thành công, không có unchecked warning.

Diễn biến:

1. `rotateEnds` nhận wildcard, helper `<T>` giữ một kiểu capture xuyên suốt thao tác hoán đổi; kết quả `[C, B, A]`.
2. `swapFirst` nhận hai `List<String>` có chung type argument, nên hoán đổi phần tử đầu thành công.
3. Hai view `List<?>` đọc an toàn dưới dạng `Object`. Cùng kiểu viết không cho biết giá trị thực tế hoặc cấp phép chuyển tùy ý giữa chúng.
4. `appendAll` cho phép source `List<Integer>` truyền phần tử vào destination `List<Number>` bằng một `T` chung và hai wildcard khác hướng.
5. Hai `List<? extends Number>` đọc an toàn như `Number` nhưng có thể chứa hai subtype thực tế khác nhau, như `Integer` và `Double`.

## 10. Kiểm chứng các trường hợp KHÔNG biên dịch

Tạo riêng tệp **InvalidCaptures.java** sau để thử lỗi chủ đích. **Tệp này cố ý không biên dịch** và không phải phần của chương trình chạy ở mục 9.

~~~java
import java.util.List;

class InvalidCaptures {
    static void sameVariable(List<?> list) {
        list.set(0, list.get(0)); // Hai capture không được suy ra là một.
    }

    static void twoVariables(List<?> source, List<?> target) {
        target.add(source.get(0)); // Không có quan hệ giữa hai wildcard.
    }

    static void twoUpperBounds(List<? extends Number> source,
                               List<? extends Number> target) {
        target.add(source.get(0)); // Cùng upper bound vẫn không đủ.
    }
}
~~~

Lệnh thử:

~~~bash
javac --release 17 -Xdiags:verbose InvalidCaptures.java
~~~

**Kỳ vọng:** `javac` kết thúc **khác 0**, báo ba lỗi biên dịch. Các đoạn chẩn đoán tiêu biểu đã được kiểm tra bằng Java hỗ trợ `--release 17`:

~~~text
required: int,CAP#1
found:    int,CAP#2
...
no suitable method found for add(CAP#1)
...
CAP#1 extends Number from capture of ? extends Number
CAP#2 extends Number from capture of ? extends Number
3 errors
~~~

Đây là các **đoạn rút gọn tiêu biểu**, không phải nguyên văn toàn bộ stderr; số thứ tự `CAP#`, vị trí file và cách trình bày có thể đổi theo compiler. Ba lỗi cùng chỉ đến một nguyên nhân: chữ ký chưa đưa ra bằng chứng rằng một giá trị thuộc kiểu capture ở vị trí đọc phù hợp với kiểu capture ở vị trí ghi.

Không sửa bằng `@SuppressWarnings("unchecked")`: ba trường hợp trên là **lỗi không tương thích kiểu**, không đơn thuần là cảnh báo unchecked mà annotation có thể làm biến mất. Giải pháp là **đổi chữ ký/hợp đồng kiểu** hoặc dùng helper capture khi làm việc với chính một collection.

## 11. Điều gì xảy ra nếu hai wildcard được tạo từ cùng một nguồn?

Xem:

~~~java
List<String> strings = new ArrayList<>(List.of("A", "B"));
List<?> one = strings;
List<?> two = strings;
~~~

Biết `one` và `two` đang trỏ cùng object **không phải một điều kiện kiểu hóa do `List<?>` diễn đạt**. Dữ liệu alias là một sự kiện runtime tại thời điểm đó; một phương thức khai báo hai `List<?>` vẫn chấp nhận hai list hoàn toàn khác nhau.

Mặt khác, ta có thể giữ thông tin `List<String>` ở call site và truyền nó cho một phương thức `<T>` cần hai `List<T>`. Khi đó hai tham số được kiểm tra theo một quan hệ kiểu rõ ràng. Nếu API bên ngoài chỉ nhận **hai `List<?>` độc lập**, không tồn tại một cách generic hoàn toàn an toàn để giả định chúng có cùng kiểu phần tử chỉ bằng cách so sánh reference hoặc nhìn ký pháp wildcard.

Đối chiếu:

| Trường hợp | Compiler có bằng chứng gì? | Chuyển phần tử giữa hai list? |
| --- | --- | --- |
| `List<String> a`, `List<String> b` | Cùng type argument `String` | Hợp lệ nếu đích hỗ trợ sửa. |
| `List<?> a`, `List<?> b` | Hai loại chưa biết, có thể khác nhau | Không được bảo đảm. |
| `List<? extends Number> a` và `b` cùng loại khai báo | Mỗi loại là subtype nào đó của `Number` | Không thể `b.add(a.get(0))`. |
| `List<? super Integer> a` và `b` cùng loại khai báo | Mỗi loại nhận được `Integer` | Có thể thêm một `Integer` đã biết vào cả hai, không thể chuyển mọi phần tử `a.get(...)` sang `b`. |
| `<T> swap(List<T> a,List<T> b)` | Hai list cùng một `T` trong lời gọi | Hợp lệ khi caller đáp ứng chữ ký. |
| `<T> append(List<? extends T>,List<? super T>)` | Nguồn đọc được `T`, đích nhận được `T` | Hợp lệ dù type arguments thực tế khác nhau. |

## 12. Giới hạn và lỗi thường gặp

### 12.1. Hiểu sai “cùng `?`” thành “cùng subtype”

Wildcard là một ràng buộc, **không phải một biến được đặt tên**. Hai vị trí cùng `? extends Number` chỉ có upper bound chung là `Number`, không mặc nhiên cùng là `Integer` hoặc `Double`.

### 12.2. Tin rằng `CAP#1` là lớp runtime

Các nhãn `CAP#1`, `CAP#2` là cách compiler diễn giải các kiểu ẩn trong quá trình type checking. Chúng không xuất hiện như những class mới. Không dùng `instanceof CAP#1`, không thể viết `new CAP#1()` hoặc đặt tên type variable bằng cú pháp này.

### 12.3. Dùng cùng một alias rồi kỳ vọng compiler theo dõi danh tính object

Java không dựa vào việc hai reference trỏ cùng list để tự cho phép mọi phép ghi qua generic wildcard. Khi thuật toán cần quan hệ, hãy trình bày bằng type parameter hoặc helper capture.

### 12.4. Coi `? extends Number` là `List<Number>`

`List<Integer>` không phải subtype của `List<Number>` do invariance. `List<? extends Number>` là view đọc theo upper bound; nó không cho phép thêm `Number` tùy ý và không đồng nhất kiểu capture giữa hai list.

### 12.5. Dùng `? super Integer` rồi giả định giá trị đọc là Integer

`List<? super Integer>` có thể trỏ đến `List<Number>` hoặc `List<Object>` với phần tử rộng hơn `Integer`. Kiểu đọc an toàn của `List<E>` là `Object`. Một `Integer` do caller chủ động tạo vẫn có thể thêm vào mọi `List<? super Integer>`.

### 12.6. Nghĩ bất kỳ helper `<T>` nào cũng buộc được hai wildcard độc lập giống nhau

Một helper có thể **giữ capture của một list** xuyên các thao tác. Với **hai list độc lập**, quan hệ phải được chứng minh bởi chữ ký method và các đối số caller truyền vào. Generic type parameter không tự cho phép tráo kiểu của `List<String>` với `List<Integer>`.

### 12.7. Bỏ qua mutability và lỗi chỉ số

Generic typing chỉ cho phép hoặc cấm thao tác **về mặt kiểu**. `get(0)`, `set(0, value)` vẫn có thể ném `IndexOutOfBoundsException` nếu danh sách rỗng; `List.of(...)` không sửa được và có thể ném `UnsupportedOperationException` khi gọi `add` hoặc `set`.

## 13. Quy trình chẩn đoán lỗi wildcard capture

1. **Đọc đúng dòng lỗi `CAP#n`:** hai nhãn khác nhau thường báo compiler chưa chứng minh hai kiểu capture có quan hệ phù hợp.
2. **Xác định chữ ký của phương thức đang gọi:** `get` trả về `E`; `add` cần `E`; `set` vừa nhận vừa trả `E`; `contains` lại nhận `Object`.
3. **Kiểm tra số lượng nguồn chứa wildcard:** một list cần thao tác nội bộ, hay hai list thực sự độc lập?
4. **Với một list:** cân nhắc public method `List<?>` chuyển sang private helper `<T> ... (List<T>)` để capture một kiểu thống nhất.
5. **Với hai list:** yêu cầu quan hệ mà thuật toán thực sự cần bằng `List<T>`/`List<T>` hoặc `List<? extends T>`/`List<? super T>`.
6. **Không sửa bằng raw type hoặc unchecked cast** khi không có bằng chứng về nội dung. Kiểm thử lại các call site với `javac -Xlint:unchecked`.
7. **Phân biệt lỗi tĩnh và rủi ro runtime:** compile-time capture error khác với `ClassCastException`/heap pollution do cố tình vượt hệ thống kiểu.

## 14. Câu hỏi ôn tập

1. Hai biến đều là `List<?>` có chắc có cùng type argument không? **Không.** Một bên có thể là `List<String>`, bên kia `List<Integer>`.
2. Hai `CAP#1` và `CAP#2` cùng `extends Object` có đồng nghĩa hai kiểu bằng nhau không? **Không.** Chung upper bound không chứng minh quan hệ bằng nhau.
3. Vì sao `list.set(0,list.get(0))` không biên dịch trong method nhận `List<?>`? **Vì hai biểu thức có thể bị capture riêng; compiler không nối chúng thành cùng type variable.**
4. Vì sao helper `<T> void swap(List<T>)` có thể thao tác an toàn? **Vì `T` được dùng nhất quán ở cả đầu vào và đầu ra của các thao tác.**
5. Hai `List<? extends Number>` có thể chuyển phần tử trực tiếp cho nhau không? **Không bảo đảm**, vì có thể là `List<Integer>` và `List<Double>`.
6. Hai `List<? super Integer>` có thể cùng nhận `Integer n` không? **Có về mặt kiểu**, vì mỗi supertype hợp lệ đều nhận `Integer`. Nhưng không suy ra có thể chuyển mọi `source.get(...)` sang đích.
7. Khi source là `List<Integer>` và target là `List<Number>`, chữ ký nào mô tả chép phần tử an toàn? **`<T> void append(List<? extends T>,List<? super T>)`**.
8. `CAP#1` có phải một class tồn tại ở runtime không? **Không**, đó là type variable do compiler dùng khi kiểm tra kiểu.
9. Nếu hai biến wildcard cùng tham chiếu một object, liệu đó có thay thế generic contract không? **Không**, cần thể hiện quan hệ kiểu trong chữ ký hoặc dùng helper đúng phạm vi.
10. Có thể dùng `@SuppressWarnings("unchecked")` để biến lỗi capture incompatibility thành hợp lệ không? **Không.** Đây là lỗi type checking, không phải chỉ là unchecked warning.

## 15. Tài liệu tham khảo

- [JLS Java SE 17 §5.1.10 — Capture Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.10): quy tắc tạo fresh capture variables từ wildcard và tính không tương đương giữa các capture.
- [JLS Java SE 17 §4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): upper/lower bounded wildcards, các giới hạn type argument và quan hệ containment.
- [JLS Java SE 17 §4.10.2 — Subtyping Among Class and Interface Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2): quan hệ subtype của parameterized types, lý do `List<Integer>` không là subtype của `List<Number>`.
- [Oracle Java Tutorials — Wildcard Capture and Helper Methods](https://docs.oracle.com/javase/tutorial/java/generics/capture.html): lỗi `CAP#`, ví dụ helper generic dùng để capture wildcard.
- [Oracle Java Tutorials — Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html): PECS, cách chọn wildcard cho nguồn và đích.
- [Java SE 17 API — List<E>](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): hợp đồng `get`, `set`, `add` và phân biệt type safety với khả năng sửa list.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-006">Giới hạn trên và dưới của kiểu được bắt giữ</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn trên và dưới của kiểu được bắt giữ (wildcard capture) trong Java Generics

**Wildcard capture** (*bắt giữ wildcard*) là cơ chế của trình biên dịch Java: khi gặp một kiểu generic chứa `?`, compiler có thể thay đối số kiểu chưa biết bằng **một biến kiểu tạm thời, riêng biệt**. Biến kiểu đó thường xuất hiện trong thông báo lỗi dưới tên như `CAP#1` hoặc `capture of ? extends Number`. Dù ta không thể viết trực tiếp tên `CAP#1` trong mã nguồn, compiler vẫn dùng các **giới hạn trên và dưới** (*upper bound* và *lower bound*) của nó để quyết định phép đọc, ghi, gán và gọi phương thức có an toàn hay không.

Tóm tắt với `List<E>`, nơi `E` không khai báo cận đặc biệt (cận trên mặc định là `Object`):

| Đối số wildcard | Kiểu được bắt giữ: cận trên | Cận dưới | Đọc từ `get` dưới kiểu nào? | Có thể thêm loại giá trị nào? |
| --- | --- | --- | --- | --- |
| `?` | `Object` | Kiểu `null` | `Object` | Chỉ literal `null` về mặt hệ kiểu |
| `? extends Number` | `Number` | Kiểu `null` | `Number` | Chỉ literal `null` về mặt hệ kiểu |
| `? super Integer` | `Object` | `Integer` | `Object` | `Integer` và những kiểu con phù hợp |
| `? super Number` | `Object` | `Number` | `Object` | `Number` và các kiểu con |

Bảng thể hiện **bảo đảm tĩnh của hệ kiểu**, không cam kết collection ở runtime hỗ trợ sửa đổi hoặc chấp nhận `null`. Với generic có tham số kiểu tự khai báo cận trên, ví dụ `Box<T extends Number>`, cận trên của kiểu được capture có thể là `Number` thay vì `Object`; phần này được giải thích chi tiết ở dưới.

## 1. Vì sao phải bắt giữ wildcard?

Xét:

```java
List<? extends Number> numbers = List.of(1, 2);
Number number = numbers.get(0);
```

Đối số kiểu thực tế của danh sách có thể là `Integer`, `Double`, `Number` hoặc một kiểu con khác của `Number`. Compiler không biết chính xác, nhưng cần lý giải **một kiểu nhất quán** cho các phần tử của cùng danh sách.

Có thể hình dung compiler tạm đặt tên cho kiểu phần tử ấy:

```text
List<? extends Number>
          |
          v
List<CAP#1>
  với CAP#1 <: Number
```

Ký hiệu `CAP#1` chỉ để diễn giải. Không tồn tại cú pháp khai báo `class CAP#1` hay `List<CAP#1>` hợp lệ trong chương trình Java thông thường.

Vì kiểu được capture nằm dưới `Number`, kết quả `numbers.get(0)` luôn có thể xem là `Number`. Nhưng compiler không thể tự giả định `CAP#1` chính là `Integer`:

```java
Number n = numbers.get(0);  // Đúng.
// Integer i = numbers.get(0); // Không biên dịch.
// numbers.add(42);           // Không biên dịch.
```

Nếu danh sách thật là `List<Double>`, việc thêm một `Integer` sẽ phá hợp đồng kiểu phần tử. Bắt giữ wildcard giúp compiler giữ được thông tin quan hệ kiểu mà không đoán đối số kiểu thực tế.

## 2. Cận trên và cận dưới của một biến kiểu được capture

Về trực giác, một biến kiểu được bắt giữ `CAP` có thể bị ràng buộc hai phía:

```text
UpperBound
     ^
     | CAP <: UpperBound
    CAP
     ^
     | LowerBound <: CAP
LowerBound
```

Ký hiệu `A <: B` có nghĩa `A` là kiểu con của `B` theo quan hệ kiểu Java. Khi biết cận trên, ta biết giá trị có kiểu `CAP` có thể **đọc ra** và xem như kiểu cận trên. Khi biết cận dưới, ta có thể **đưa vào** giá trị của kiểu cận dưới, vì mọi kiểu `CAP` hợp lệ đều phải là kiểu cha của cận dưới đó.

Hai giới hạn khác nhau về tác dụng:

- **Cận trên (`upper bound`):** một đối tượng có kiểu `CAP` có thể được chuyển lên kiểu này, nên giúp xác định những API có thể gọi khi đọc.
- **Cận dưới (`lower bound`):** kiểu này và các kiểu con phù hợp có thể được đưa vào vị trí cần `CAP`, nên giúp kiểm tra những giá trị có thể ghi.
- **Cả hai cận không xác định một class runtime cố định.** Capture được dùng trong phân tích kiểu khi biên dịch, không tạo một class mới.

Java còn có **kiểu `null`** đặc biệt. Trong cách mô tả quy tắc capture của ngôn ngữ, đây là cận dưới của `?` và `? extends B`. Nó giải thích vì sao literal `null` có thể được truyền vào nơi yêu cầu một kiểu tham chiếu capture, nhưng không hứa collection chấp nhận giá trị ấy khi chạy.

Không nên hiểu “cận dưới là kiểu `null`” là mọi phần tử đang tồn tại đều `null`, hoặc cận trên `Number` nghĩa mọi giá trị luôn khác `null`. Đó là hai kết luận sai.

## 3. Capture của `? extends Number`: cận trên mạnh, cận dưới yếu

Với `List<? extends Number>`:

- `CAP` có **cận trên `Number`** (trong trường hợp đơn giản này);
- cận dưới là kiểu `null`;
- compiler cho đọc ra như `Number`;
- compiler không cho thêm một `Integer`, `Double` hoặc `Number` cụ thể tùy ý.

Ví dụ:

```java
static void readSource(List<? extends Number> source) {
    for (Number value : source) {
        System.out.println(value);
    }

    // source.add(1);           // Không biên dịch.
    // source.add(1.5);         // Không biên dịch.
    // source.add((Number) 3);  // Không biên dịch.
    source.add(null);           // Hợp lệ về kiểu; có thể lỗi ở runtime.
}
```

Trong `source.add(1)`, `Integer` chỉ được biết là kiểu con của `Number`, không được chứng minh là kiểu con của chính `CAP`. Có thể nguồn là `List<Double>` nên phép thêm không an toàn.

Cận dưới kiểu `null` giải thích vì sao `source.add(null)` được chấp nhận bởi compiler. Nhưng nếu `source` thực sự là danh sách không sửa đổi của `List.of(...)` thì `add(null)` vẫn ném `UnsupportedOperationException`; nếu implementation cấm `null` thì có thể ném `NullPointerException`. Các điều kiện runtime độc lập với wildcard capture.

Một ví dụ thường gặp: `? extends Number` là **producer** trong nguyên tắc PECS (*Producer Extends, Consumer Super*). Nó phù hợp khi ta cần lấy giá trị dưới kiểu `Number`, không cần thêm một đối tượng `Number` mới qua tham chiếu wildcard.

## 4. Capture của `? super Integer`: cận dưới mạnh, cận trên tùy khai báo

Với `List<? super Integer>`:

- `CAP` có **cận dưới `Integer`**;
- cận trên mặc định là `Object` vì tham số `E` của `List<E>` không có cận chặt hơn;
- compiler cho thêm `Integer`;
- compiler chỉ bảo đảm khi đọc nhận được `Object`.

Ví dụ:

```java
static void writeDestination(List<? super Integer> destination) {
    destination.add(10); // Hợp lệ.
    destination.add(20); // Hợp lệ.

    // destination.add(2.5); // Không biên dịch.

    Object value = destination.get(0); // Hợp lệ.
    // Integer n = destination.get(0); // Không biên dịch.
}
```

Lý do: `destination` có thể là `List<Integer>`, `List<Number>`, `List<Object>`, thậm chí `List<java.io.Serializable>`. Mọi danh sách này nhận `Integer`, nhưng một `List<Object>` có thể chứa sẵn `String`. Vì thế không thể coi mỗi phần tử đọc ra là `Integer` hoặc `Number`.

Điều này tạo ra mô hình hai chiều cần nhớ:

```text
? extends Number                 ? super Integer
-----------------------          -----------------------
Đọc:  Number                    Đọc:  Object
Ghi:  không thêm Number cụ thể   Ghi:  Integer
Upper: Number                   Upper: Object (với List<E>)
Lower: kiểu null                 Lower: Integer
```

Cận trên của `? super Integer` **không nhất thiết luôn là `Object`**, bởi nó còn phụ thuộc cận của tham số kiểu khai báo trong generic. Sự khác biệt đó rất quan trọng.

## 5. Ví dụ Java 17 hoàn chỉnh: đọc/ghi theo cận của capture

Lưu thành `CaptureBoundsDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class CaptureBoundsDemo {
    static void fromExtends(List<? extends Number> source) {
        Number first = source.get(0);
        System.out.println("upper read=" + first);

        // source.add(99); // Không biên dịch: không biết CAP#1.
        source.add(null);   // Chỉ hợp lệ vì null tương thích với CAP#1.
    }

    static void fromSuper(List<? super Integer> destination) {
        destination.add(10);
        Object first = destination.get(0);
        System.out.println("lower read=" + first);

        // Number n = destination.get(0); // Không biên dịch.
    }

    public static void main(String[] args) {
        List<Integer> source = new ArrayList<>(List.of(1, 2));
        fromExtends(source);
        System.out.println("source=" + source);

        List<Number> destination = new ArrayList<>();
        fromSuper(destination);
        System.out.println("destination=" + destination);
    }
}
```

Lệnh:

```bash
javac CaptureBoundsDemo.java
java CaptureBoundsDemo
```

Kết quả:

```text
upper read=1
source=[1, 2, null]
lower read=10
destination=[10]
```

Chương trình dùng `ArrayList` để bảo đảm thao tác thêm `null` được hỗ trợ. Khi dùng collection khác, không thể suy từ tính hợp lệ về kiểu sang kết quả runtime. Hai phương thức cũng cho thấy quyền đọc/ghi được xác định từ **chữ ký tham số**, không phải từ cách tạo list tại điểm gọi.

## 6. Cận khai báo của generic ảnh hưởng cận trên capture

Đây là khác biệt quan trọng giữa một `List<E>` thông thường và generic có cận trên tự khai báo.

Xét:

```java
class NumberBox<T extends Number> {
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
}
```

Ở đây, kiểu `T` **bắt buộc là `Number` hoặc kiểu con của `Number`**. Vì thế cả khi dùng wildcard cận dưới, cận trên của kiểu capture vẫn không thể rộng quá `Number`.

| Kiểu | Cận trên của kiểu capture | Cận dưới |
| --- | --- | --- |
| `List<? super Integer>` | `Object` | `Integer` |
| `NumberBox<? super Integer>` | `Number` | `Integer` |
| `NumberBox<?>` | `Number` | Kiểu `null` |
| `NumberBox<? extends Integer>` | `Integer` | Kiểu `null` |

Với `NumberBox<? super Integer>`, ta **đọc được `Number`** và **ghi được `Integer`**. Điều này không mâu thuẫn với quy tắc `List<? super Integer>` chỉ đọc được `Object`. Sự khác biệt đến từ cận trên `T extends Number` được khai báo tại `NumberBox`.

### Ví dụ chạy được: capture vừa có upper `Number` vừa có lower `Integer`

Lưu thành `BoundedBoxCaptureDemo.java`:

```java
public class BoundedBoxCaptureDemo {
    static final class NumberBox<T extends Number> {
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
    }

    static void useBox(NumberBox<? super Integer> box) {
        Number before = box.get(); // Cận trên là Number.
        box.set(42);               // Cận dưới là Integer.
        Number after = box.get();

        System.out.println("before=" + before);
        System.out.println("after=" + after);
    }

    public static void main(String[] args) {
        NumberBox<Number> numbers = new NumberBox<>(2.5);
        useBox(numbers);
        System.out.println("actual=" + numbers.get());

        NumberBox<Integer> integers = new NumberBox<>(7);
        useBox(integers);
        System.out.println("actual=" + integers.get());
    }
}
```

```bash
javac BoundedBoxCaptureDemo.java
java BoundedBoxCaptureDemo
```

Kết quả:

```text
before=2.5
after=42
actual=42
before=7
after=42
actual=42
```

Cả `NumberBox<Number>` lẫn `NumberBox<Integer>` đều phù hợp với `NumberBox<? super Integer>`. Mọi đối số kiểu được chấp nhận của `NumberBox` phải là kiểu con của `Number`, nên đọc ra dưới `Number` luôn an toàn; mọi ứng viên cho `? super Integer` đều nhận `Integer`, nên gọi `set(42)` cũng an toàn.

Bài học: không nên học máy móc “`? super` chỉ đọc được `Object`” cho **mọi** generic. Đúng hơn phải nói: đọc được giá trị dưới **cận trên của biến capture**; ở `List<E>` cận này thông thường là `Object`, còn với `NumberBox<T extends Number>` là `Number`.

## 7. Cách compiler xác định upper và lower bound

Với một tham số kiểu khai báo `T` của generic, compiler xem xét **cận khai báo của `T`** trước, rồi kết hợp nó với wildcard tại nơi sử dụng. Có thể ghi nhớ ba trường hợp cơ bản:

### Trường hợp A: wildcard không giới hạn `?`

- **Upper bound:** cận trên khai báo của tham số kiểu `T` (thông thường `Object`).
- **Lower bound:** kiểu `null`.

Do đó `List<?>` đọc được `Object`, còn `NumberBox<?>` đọc được `Number`.

### Trường hợp B: wildcard `? extends B`

- **Upper bound:** được kết hợp từ cận `B` của wildcard và cận khai báo của `T`, về mặt ngôn ngữ là *greatest lower bound* (`glb`) của các cận cần thỏa.
- **Lower bound:** kiểu `null`.

Trong trường hợp đơn giản `List<? extends Number>`, cận khai báo của `List<E>` là `Object` nên kết quả upper là `Number`. Với `NumberBox<? extends Integer>`, upper là `Integer` vì `Integer` nằm dưới `Number`.

Khi cận khai báo có nhiều interface, cận trên có thể được mô tả bằng một **kiểu giao (intersection type)** ở phía compiler. Chẳng hạn, một class khai báo `T extends Number & java.io.Serializable` cùng wildcard có cận trên khác có thể buộc CAP thỏa đồng thời nhiều ràng buộc. Không được tự nghĩ rằng compiler luôn giữ đúng nguyên văn duy nhất kiểu viết sau `extends`.

### Trường hợp C: wildcard `? super B`

- **Upper bound:** cận trên **khai báo của `T`**, sau khi xét các tham số generic liên quan.
- **Lower bound:** `B`.

Với `List<? super Integer>`, upper là `Object`, lower `Integer`. Với `NumberBox<? super Integer>`, upper là `Number`, lower `Integer`.

Các quy tắc này mô tả ý tưởng về capture conversion được quy định trong *Java Language Specification*, mục 5.1.10. Những generic có cận tự tham chiếu hoặc nhiều tham số kiểu phụ thuộc nhau có thể tạo các cận sau khi thay thế phức tạp hơn; nhưng nguyên tắc đọc từ upper và truyền giá trị tương thích lower vẫn là nền tảng hữu ích.

## 8. Cần phân biệt cận của wildcard với cận của kiểu capture

Biểu thức:

```java
List<? super Integer> sink;
```

có một **wildcard ghi rõ cận dưới `Integer`**. Khi được capture, compiler tạo kiểu tạm thời có cả hai phía giới hạn: upper từ `List<E>` là `Object`, lower `Integer`.

Vì vậy, cụm “giới hạn trên và dưới của kiểu được bắt giữ” nói về **đầy đủ hai mặt** của kiểu tạm thời CAP, chứ không chỉ đọc chữ `extends` hay `super` trong mã nguồn.

Một sơ đồ minh họa:

```text
List<? super Integer>      -> CAP#1:
                                  Integer <: CAP#1 <: Object

NumberBox<? super Integer> -> CAP#2:
                                  Integer <: CAP#2 <: Number

List<? extends Number>     -> CAP#3:
                                  null-type <: CAP#3 <: Number

NumberBox<?>               -> CAP#4:
                                  null-type <: CAP#4 <: Number
```

`null-type` ở đây là cách diễn giải cận dưới của hệ kiểu, không phải tên một class. Mỗi lần capture có thể tạo biến kiểu riêng; ký hiệu `CAP#1`, `CAP#2` trong lỗi compiler không ổn định giữa trình biên dịch hoặc vị trí biên dịch.

## 9. Hai capture từ hai wildcard khác nhau không đồng nhất

Ngay cả khi hai tham số dùng cùng cú pháp `List<? extends Number>`, compiler **không mặc nhiên xem kiểu phần tử capture của chúng là một kiểu**.

Ví dụ:

```java
static void moveFirst(
        List<? extends Number> source,
        List<? extends Number> destination) {

    Number value = source.get(0); // Đọc được Number.

    // destination.add(value); // Không biên dịch.
}
```

`source` có thể là `List<Integer>`, còn `destination` có thể là `List<Double>`. Capture của hai wildcard là các kiểu tạm thời khác nhau. Cả hai cùng có cận trên `Number` không làm chúng bằng nhau.

Muốn chuyển số từ nguồn sang một **đích consumer**, có thể khai báo:

```java
static void append(
        List<? extends Number> source,
        List<? super Number> destination) {
    for (Number value : source) {
        destination.add(value);
    }
}
```

Ở đây upper `Number` giúp đọc nguồn dưới `Number`, lower `Number` giúp đích nhận mọi `Number`. Đó chính là cách kết hợp producer/consumer của PECS. Đích có thể là `List<Number>` hoặc `List<Object>`, nhưng không thể là `List<Integer>` nếu nguồn có thể chứa `Double`.

Nếu cần dùng một kiểu chung do caller quyết định, chữ ký tổng quát hơn là:

```java
static <T> void appendGeneric(
        List<? extends T> source,
        List<? super T> destination) {
    for (T value : source) {
        destination.add(value);
    }
}
```

Cách này dùng cùng một `T` để liên kết cận trên của producer và cận dưới của consumer, trong khi vẫn cho phép hai đối số generic khác nhau.

## 10. Helper giữ cận trên khi xử lý một captured type

Ở `List<? extends Number>`, compiler biết element type ẩn là một subtype của `Number`, nhưng không cho ghi một `Integer` bất kỳ: danh sách thật có thể là `List<Double>`.

Nếu cần **lặp lại chính một phần tử đã tồn tại**, dùng helper có bound tương ứng:

```java
static void repeatFirstNumber(List<? extends Number> source) {
    repeatCapturedNumber(source);
}

private static <E extends Number> void repeatCapturedNumber(List<E> source) {
    if (!source.isEmpty()) {
        E first = source.get(0);
        double numericalValue = first.doubleValue();
        source.add(first);
        System.out.println("repeated=" + numericalValue);
    }
}
```

Các method cần đặt trong class có import `java.util.List`. Lời gọi `repeatCapturedNumber(source)` capture `? extends Number` thành một `E` thỏa `E extends Number`. Do đó `doubleValue()` hợp lệ **và** giá trị lấy từ chính `List<E>` có thể được thêm lại cùng list dưới dạng `E`.

Trái lại, `source.add(42)` ngay trên `List<? extends Number>` không biên dịch, và helper cũng không cho tự tạo ra một `E` mới từ `42`. Runtime vẫn phải xét list có cho thêm phần tử không; `List.of` không sửa được. Với `? super Integer`, quyền đọc còn phụ thuộc **cận trên của type parameter khai báo**: `List<E>` cho đọc `Object`, còn `NumberBox<E extends Number>` cho đọc `Number`.

Trọng tâm ở đây là **bound của captured type quyết định thao tác hợp lệ**, không phải thuật toán swap hay đặt tên `CAP#` trong diagnostic.

## 11. Đọc/ghi với upper và lower không đồng nghĩa ép kiểu tự động

Xét `NumberBox<? super Integer>`: có thể đọc `Number`, ghi `Integer`. Điều này **không** có nghĩa compiler tự chuyển tất cả giá trị đọc thành `Integer`:

```java
NumberBox<? super Integer> box = new NumberBox<Number>(2.5);
Number read = box.get(); // Đúng.
// Integer i = box.get(); // Không biên dịch.
box.set(3);              // Đúng.
// box.set(3.5);          // Không biên dịch.
```

Các đoạn ở mục này dựa vào khai báo `NumberBox<T extends Number>` từ mục 6 và dùng làm minh họa chữ ký, không phải file Java độc lập.

`box.get()` có thể trả `Double` vì đối tượng thực sự là `NumberBox<Number>`. Nếu ép kiểu tường minh sang `Integer` thì cast có thể gây `ClassCastException`; không dùng cast để xóa sự không chắc chắn về kiểu capture.

Ngược lại, lời gọi `box.set(3)` hợp lệ vì `Integer` có thể đi vào mọi loại `NumberBox` đáp ứng `? super Integer` cùng cận `T extends Number`. Cận dưới cho biết loại giá trị nào an toàn **đưa vào**, không phải loại giá trị nào chắc chắn **lấy ra**.

## 12. Type erasure và phạm vi của capture

Wildcard capture là thao tác của **hệ kiểu tĩnh**, không phải một cuộc kiểm tra đối số generic khi chạy. Java thường dùng *type erasure* cho generic, nên tại runtime không tồn tại một đối tượng kiểu riêng tên `CAP#1` để ta truy vấn trực tiếp.

Ví dụ:

```java
List<? extends Number> first = new ArrayList<Integer>();
List<? extends Number> second = new ArrayList<Double>();

System.out.println(first.getClass() == second.getClass());
```

Kết quả là `true` vì hai đối tượng runtime đều là `ArrayList`; khác biệt giữa `Integer` và `Double` được phản ánh qua ràng buộc compile time của từng khai báo, không qua hai lớp runtime riêng biệt `ArrayList<Integer>` và `ArrayList<Double>`.

Một chương trình hoàn chỉnh để chạy đoạn này cần class chứa phương thức `main` cùng `import java.util.List;` và `import java.util.ArrayList;`. Đoạn hiện tại chỉ nhằm giải thích erasure.

Điều đó cũng không cho phép tự ý ép `List<Integer>` thành `List<Double>`. Chính hệ kiểu trước khi chạy đã ngăn các thao tác làm hỏng tính nhất quán của dữ liệu. Raw type hoặc cast unchecked có thể né kiểm tra, nhưng là nguyên nhân của lỗi kiểu xuất hiện muộn và cần tránh.

## 13. Các bẫy thường gặp

### Bẫy 1: cho rằng `? super T` luôn đọc được `Object` bất kể khai báo generic

Với `List<? super Integer>`, đọc `Object` là bảo đảm thông thường. Nhưng với `NumberBox<? super Integer>`, cận khai báo `T extends Number` cho phép đọc dưới `Number`. Cần xét **cả wildcard lẫn cận của tham số kiểu được khai báo**.

### Bẫy 2: nhìn thấy `? extends Number` thì tưởng capture luôn là `Number`

Sai. Kiểu thực tế có thể là `Integer` hay `Double`. `CAP <: Number` chỉ là giới hạn trên, không phải phương trình `CAP = Number`. Vì thế vẫn không thêm được tùy ý một `Number` cụ thể.

### Bẫy 3: thấy `? super Integer` thì tưởng mọi phần tử hiện hữu là `Integer`

Sai. `List<? super Integer>` có thể là `List<Object>` chứa sẵn chuỗi. Cận dưới bảo đảm có thể thêm `Integer`, không bảo đảm mọi phần tử đọc ra thuộc `Integer`.

### Bẫy 4: nghĩ các wildcard có cùng cận là cùng một capture

Hai nguồn `List<? extends Number>` có thể chứa các kiểu con khác nhau. Không được chép phần tử từ một nguồn trực tiếp vào nguồn kia chỉ dựa trên cùng cận trên.

### Bẫy 5: dùng `List<Object>` thay cho `List<?>` để xử lý capture

`List<Object>` không thể nhận trực tiếp `List<String>` vì generic bất biến. `List<?>` mới là chữ ký phù hợp khi thuật toán chỉ đọc các giá trị chung dưới `Object` và không cần một kiểu phần tử chính xác.

### Bẫy 6: nghĩ helper method làm mất giới hạn kiểu

Generic helper không cho phép thêm sai kiểu. Nó chỉ đặt tên một capture để chuyển **chính giá trị cùng kiểu `E`** giữa các vị trí an toàn. Nếu cố `items.add((E) someObject)` bằng cast unchecked thì vẫn có nguy cơ sai kiểu.

### Bẫy 7: tin rằng upper bound `Number` cấm giá trị `null`

Không. Một `ArrayList<Integer>` có thể chứa `null`, và khi đọc qua `List<? extends Number>`, giá trị nhận được có thể là `null`. Cận kiểu mô tả quan hệ subtype, không phải cam kết non-null.

### Bẫy 8: nhầm lỗi capture với lỗi dữ liệu runtime

Thông báo chứa `CAP#1` thường liên quan compiler không chứng minh được quan hệ kiểu tại lời gọi generic. `UnsupportedOperationException`, `NullPointerException` hoặc `ClassCastException` khi chạy lại phải điều tra theo hợp đồng collection, dữ liệu thực tế và các cast đã dùng; không nên gọi chung là “lỗi wildcard”.

## 14. Cách đọc thông báo lỗi capture của compiler

Khi thử truyền một `Integer` vào `List<? extends Number>`:

```java
static void addBad(List<? extends Number> values) {
    // values.add(10); // Không biên dịch.
}
```

Một thông báo compiler có thể đề cập `CAP#1`, kiểu `Integer` không chuyển được sang `CAP#1`, hoặc “where CAP#1 is a fresh type-variable”. Đây là cách compiler diễn đạt:

- `CAP#1` là kiểu phần tử chưa biết của `values`;
- `CAP#1` được biết có cận trên `Number`;
- nhưng không có bằng chứng `Integer <: CAP#1`;
- vì vậy lời gọi `add(10)` không an toàn.

Không phụ thuộc vào chính xác số thứ tự `CAP#1` hay wording của thông báo, vì các bản compiler có thể trình bày khác nhau.

Khi gặp lỗi kiểu này, nên lần lượt tự hỏi:

1. Generic ở vị trí tham số có khai báo cận `T extends ...` không?
2. Wildcard tại điểm dùng là `?`, `? extends B` hay `? super B`?
3. Từ hai thông tin đó, cận trên/cận dưới của capture là gì?
4. Code đang **đọc** từ capture hay **ghi** một giá trị vào capture?
5. Nếu cần đọc và ghi cùng một kiểu chưa biết, có nên chuyển thao tác vào generic helper `<E>` để compiler giữ liên kết kiểu không?

Giải quyết đúng thường là đổi chữ ký sang producer `? extends T`, consumer `? super T`, hoặc generic helper, thay vì ép kiểu không kiểm tra.

## 15. Câu hỏi luyện tập

**Câu 1.** Với `List<? extends Number> values`, compiler bảo đảm gì?

- Cận trên capture: `Number`.
- Cận dưới capture: kiểu `null`.
- `Number n = values.get(0)` hợp lệ nếu chỉ xét kiểu, nhưng `get(0)` vẫn có thể gặp danh sách rỗng.
- `values.add(10)` không hợp lệ; `values.add(null)` hợp lệ về kiểu nhưng chưa chắc thành công khi chạy.

**Câu 2.** Với `List<? super Integer> values`, cận trên và dưới là gì?

- Cận trên: `Object`, do `List<E>` không có cận chặt hơn.
- Cận dưới: `Integer`.
- `values.add(10)` hợp lệ; `Object v = values.get(0)` hợp lệ.
- `Number n = values.get(0)` không được đảm bảo.

**Câu 3.** Với `NumberBox<T extends Number>` và `NumberBox<? super Integer>`, kiểu kết quả của `get` có thể nhận vào biến nào?

**`Number`.** Upper của CAP là `Number`; lower là `Integer`. Vì vậy `Number n = box.get()` được chấp nhận mà không cần cast.

**Câu 4.** `NumberBox<?>` có đọc `Number` được không?

**Có.** Wildcard không giới hạn lấy cận trên từ tham số `T extends Number` của class; dấu `?` không làm biến `T` mất cận khai báo.

**Câu 5.** Có thể sao chép trực tiếp từ `List<? extends Number> a` sang `List<? extends Number> b` bằng `b.add(a.get(0))` không?

**Không.** Capture của hai nguồn độc lập; `b` có thể là `List<Integer>` còn `a` có thể là `List<Double>`. Nếu đích phải nhận mọi `Number`, khai báo đích `List<? super Number>`.

**Câu 6.** Vì sao generic helper `<E> void helper(List<E> values)` khắc phục một số lỗi liên quan `List<?>`?

Vì compiler suy luận `E` là **kiểu capture cụ thể cho một lời gọi**, rồi dùng đúng một tham số kiểu `E` xuyên suốt thân helper. Nhờ vậy có thể lấy một phần tử kiểu `E` và đặt lại vào danh sách cùng `E` an toàn.

## 16. Ghi nhớ để áp dụng

- Wildcard capture tạo **kiểu tạm thời chưa biết nhưng có giới hạn**; nó không tạo kiểu runtime mới.
- `? extends B` cho **upper** từ `B` kết hợp cận khai báo, **lower** là kiểu `null`: thuận tiện để **đọc**.
- `? super B` cho **lower** là `B`, **upper** từ cận khai báo của tham số generic: thuận tiện để **ghi** giá trị `B`.
- `?` cũng có upper từ cận khai báo, lower là kiểu `null`; vì vậy `NumberBox<?>` có thể đọc `Number` trong khi `List<?>` thường chỉ đọc `Object`.
- Hai wildcard nhìn giống nhau có thể là **hai capture riêng**; cận giống nhau không buộc đối số kiểu thực tế giống nhau.
- Khi phải chuyển giá trị giữa những vị trí thực sự dùng **cùng một kiểu chưa biết**, generic helper `<E>` đặt tên kiểu capture và bảo toàn quan hệ ấy.
- Luôn phân biệt **hợp lệ về kiểu** với collection có thể sửa đổi, cho phép `null` hoặc bảo đảm dữ liệu runtime thỏa yêu cầu.

Câu hỏi quyết định khi phân tích một đoạn Java Generics là: **“Kiểu CAP này có upper nào để đọc được gì, lower nào để ghi được gì, và các vị trí đang thao tác có thực sự dùng cùng một CAP không?”**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-007">Wildcard lồng nhau và giới hạn của phép bắt giữ</a>

<details>
<summary>Xem chi tiết</summary>

# Wildcard lồng nhau và giới hạn của phép bắt giữ trong Java Generics

Khi gặp những kiểu như `List<List<?>>`, `List<? extends List<?>>` hoặc `List<? extends List<? extends Number>>`, chỉ một dấu `?` cũng đã cần chú ý đến hướng đọc/ghi; **nhiều dấu `?` ở những tầng khác nhau không hề có nghĩa chúng cùng một kiểu**. Java kiểm tra ràng buộc ở từng tầng generic và có cơ chế *wildcard capture* để tạm đặt tên cho một kiểu ẩn. Nhưng capture có giới hạn: nó không biến các wildcard độc lập thành một kiểu chung, không cho phép ghi giá trị tùy ý, và không thay đổi tính bất biến của generic.

Bài này làm rõ ba câu hỏi:

1. **Wildcard nằm ở tầng nào?** Tầng danh sách ngoài và tầng danh sách trong quyết định những phép gán và cập nhật được phép.
2. **Capture giải quyết được gì?** Một phương thức generic có thể nắm bắt một kiểu ẩn để đọc và ghi lại **giá trị cùng kiểu của chính collection đó**.
3. **Capture không giải quyết được gì?** Nó không chứng minh hai wildcard độc lập là cùng kiểu, không ép danh sách của subtype thành danh sách của supertype, và không vượt qua giới hạn sửa dữ liệu của đối tượng thật.

Các ví dụ runnable sử dụng Java 17 trở lên. Các đoạn cố ý sai kiểu đều được chú thích và giải thích riêng, không phải các chương trình chạy độc lập.

## 1. Từ một wildcard đến nhiều tầng generic

`List<?>` có một element type chưa biết: đọc ra an toàn như `Object`, nhưng không được tùy ý `add("text")`. Nó **không** tương đương `List<Object>`. Java có thể capture dấu `?` thành một kiểu tạm có tên nội bộ `CAP#1`; generic helper `<E>` giúp giữ cùng element type khi đọc rồi ghi lại vào **chính list đó**.

Khi có **danh sách các danh sách**, vấn đề không còn nằm ở một dấu `?` đơn lẻ: vị trí wildcard ở **tầng ngoài** khác wildcard nằm trong **kiểu phần tử của tầng trong**. Hai hàng khác nhau có thể chứa các loại dữ liệu khác nhau, dù đều được nhìn dưới `List<?>`.

Ví dụ minh họa (đặt trong method có import `java.util.List`):

~~~java
List<List<?>> rows = List.of(List.of("A"), List.of(1));
Object first = rows.get(0).get(0); // "A"
Object next  = rows.get(1).get(0); // 1
~~~

Mã chỉ đọc nên không cần capture helper. Nếu cần thay đổi một hàng cụ thể, phải xác định **capture đang thuộc tầng nào và danh sách thực có mutable không**. Các phần sau phân biệt `List<List<?>>`, `List<? extends List<?>>` và cách đặt helper ở đúng tầng; không được suy từ cùng ký hiệu `?` rằng hai hàng có một element type chung.

## 2. Phân biệt các tầng kiểu: ba khai báo trông gần giống nhưng rất khác

Xét ba kiểu:

~~~java
List<?> a;
List<List<?>> b;
List<? extends List<?>> c;
~~~

Chúng khác nhau ở **vị trí wildcard**:

| Kiểu | Wildcard ở đâu? | Đọc từ danh sách ngoài | Thêm hàng mới vào danh sách ngoài |
| --- | --- | --- | --- |
| `List<?>` | Ngay phần tử của danh sách ngoài | `Object` | Không thêm hàng không phải `null` |
| `List<List<?>>` | Trong kiểu phần tử; tầng ngoài có đối số kiểu chính xác `List<?>` | `List<?>` | Có thể thêm một `List<X>` bất kỳ vì nó dùng được như `List<?>` |
| `List<? extends List<?>>` | Tầng ngoài có wildcard với upper bound `List<?>` | `List<?>` | Không thêm hàng không phải `null` vì không biết kiểu phần tử thực của tầng ngoài |

### 2.1. `List<List<?>>`: danh sách ngoài biết phần tử là `List<?>`

~~~java
List<List<?>> rows = new ArrayList<>();
rows.add(new ArrayList<String>(List.of("A", "B")));
rows.add(new ArrayList<Integer>(List.of(1, 2)));

List<?> firstRow = rows.get(0); // Hợp lệ.
// firstRow.add("C");           // LỖI: không biết kiểu của hàng này.
rows.add(List.of(true, false)); // Hợp lệ: thêm một hàng Boolean.
~~~

Đây là đoạn minh họa cần import `java.util.*` và đặt trong class. `rows.add(...)` biên dịch vì **kiểu phần tử của danh sách ngoài chính xác là `List<?>`**, và `List<String>`, `List<Integer>`, `List<Boolean>` đều chuyển được sang góc nhìn `List<?>`.

Tuy nhiên, khi lấy ra một hàng, biến `List<?>` lại không biết **kiểu phần tử bên trong hàng ấy**. Việc danh sách ngoài chứa được nhiều kiểu hàng không cho phép trộn các kiểu phần tử trong một hàng cụ thể.

### 2.2. `List<? extends List<?>>`: tầng ngoài cũng có kiểu ẩn

~~~java
List<List<Integer>> integerRows = new ArrayList<>();
integerRows.add(new ArrayList<>(List.of(1, 2)));

List<? extends List<?>> rows = integerRows;
List<?> firstRow = rows.get(0); // Hợp lệ.

// rows.add(List.of("X")); // LỖI: tầng ngoài có wildcard.
// rows.add(List.of(3));   // CŨNG LỖI.
~~~

Đây là đoạn minh họa cần import `java.util.*` và đặt trong class. `integerRows` có đối số kiểu `List<Integer>`; đối số này phù hợp upper bound `List<?>` nên có thể gán sang `List<? extends List<?>>`.

Tuy nhiên, compiler **không biết chính xác loại hàng mà danh sách ngoài chấp nhận**, chỉ biết nó là một subtype của `List<?>`. Đích thật đang nhận các hàng `List<Integer>`; cho thêm một `List<String>` là nguy hiểm. Vì vậy, ngay cả `List<Integer>` mới cũng không được thêm qua tham chiếu `rows`, bởi kiểu tĩnh không giữ được thông tin rằng đối số kiểu thực đúng là `List<Integer>`.

### 2.3. Sự khác nhau có thể quan sát được bằng một phép gán

~~~java
List<List<Integer>> integerRows = new ArrayList<>();

List<? extends List<?>> accepted = integerRows; // Hợp lệ.
// List<List<?>> rejected = integerRows;         // LỖI BIÊN DỊCH.
~~~

**Vì sao dòng thứ hai sai?** Java generic bất biến theo đối số kiểu tầng ngoài. Nếu nó cho phép chuyển `List<List<Integer>>` sang `List<List<?>>`, ta có thể gọi `rejected.add(List.of("text"))`, khiến danh sách được khai báo chứa các hàng `List<Integer>` bỗng có một hàng `List<String>`.

**Vì sao dòng đầu đúng?** `? extends List<?>` giữ ràng buộc rằng kiểu hàng thật có thể là bất kỳ subtype cụ thể nào của `List<?>`; do không cho thêm hàng tùy ý, phép gán vẫn an toàn.

Đây là nguyên tắc quan trọng nhất của wildcard lồng nhau: **chỗ đặt `?` quan trọng không kém phần upper bound đứng sau nó**.

## 3. Hai tầng wildcard có thể cùng xuất hiện

Bây giờ xét:

~~~java
List<? extends List<? extends Number>> groups;
~~~

Hãy đọc **từ trong ra ngoài**:

1. `List<? extends Number>` là một danh sách mà mỗi phần tử đọc ra dùng được như `Number`. Nó có thể thực sự là `List<Integer>`, `List<Double>` hoặc `List<Number>`.
2. `List<? extends List<? extends Number>>` là một danh sách ngoài có kiểu hàng thực là một subtype cụ thể của `List<? extends Number>`. Đọc một hàng ra sẽ sử dụng được như `List<? extends Number>`.
3. Qua tham chiếu này, ta không tự ý thêm một hàng mới vào danh sách ngoài, và cũng không tự ý thêm một `Number` mới vào hàng lấy ra. Nhưng ta có thể **đọc mỗi phần tử số như `Number`**.

Ví dụ:

~~~java
List<List<Integer>> integerGroups =
        List.of(List.of(1, 2), List.of(3));

List<? extends List<? extends Number>> groups = integerGroups;

List<? extends Number> firstGroup = groups.get(0);
Number firstNumber = firstGroup.get(0); // Hợp lệ.

// groups.add(List.of(4, 5));  // LỖI ở tầng ngoài.
// firstGroup.add(3.14);       // LỖI ở tầng trong.
~~~

Đây là đoạn minh họa cần import `java.util.List` và đặt trong class. Tương tự, kiểu `List<List<Double>>` có thể gán vào biến `groups`.

Quan sát kỹ rằng **mỗi tầng wildcard có một ràng buộc khác**. Tầng ngoài kiểm soát kiểu hàng; tầng trong kiểm soát kiểu phần tử của một hàng. Một phép capture ở tầng trong **không tự động mở khóa** quyền thêm hàng vào tầng ngoài.

## 4. Chương trình Java 17+: quan sát các tầng và thao tác an toàn

Tạo tệp **`NestedWildcardViewsDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class NestedWildcardViewsDemo {
    private static void showRows(List<? extends List<?>> rows) {
        for (List<?> row : rows) {
            System.out.println("row = " + row);
            for (Object item : row) {
                System.out.println("  item = " + item);
            }
        }
    }

    public static void main(String[] args) {
        List<List<Integer>> integerRows = new ArrayList<>();
        integerRows.add(new ArrayList<>(List.of(1, 2)));
        integerRows.add(new ArrayList<>(List.of(3)));

        List<List<?>> mixedRows = new ArrayList<>();
        mixedRows.add(new ArrayList<>(List.of("A", "B")));
        mixedRows.add(new ArrayList<>(List.of(true, false)));

        showRows(integerRows);
        showRows(mixedRows);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac NestedWildcardViewsDemo.java
java NestedWildcardViewsDemo
~~~

Stdout chính xác:

~~~text
row = [1, 2]
  item = 1
  item = 2
row = [3]
  item = 3
row = [A, B]
  item = A
  item = B
row = [true, false]
  item = true
  item = false
~~~

Phương thức `showRows` nhận cả hai kiểu nguồn vì `List<List<Integer>>` và `List<List<?>>` đều phù hợp `List<? extends List<?>>`. Ở mỗi lần lặp, một hàng được đọc như `List<?>` và từng phần tử như `Object`. Không có cast unchecked hoặc phép gán thiếu an toàn.

Đừng suy từ `mixedRows` có thể chứa nhiều loại hàng rằng **mọi hàng đều nhận được mọi loại phần tử**. Hàng đầu thật là một `ArrayList<String>` nên vẫn có hợp đồng phần tử riêng của nó.

## 5. Capture một hàng bên trong danh sách lồng nhau

Nếu cần thay đổi vị trí của các phần tử *trong chính một hàng*, có thể dùng helper generic để nắm bắt kiểu của hàng ấy.

Tạo **`NestedCaptureDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class NestedCaptureDemo {
    public static void swapEndsInEveryRow(List<? extends List<?>> rows) {
        for (List<?> row : rows) {
            swapCaptured(row);
        }
    }

    private static <E> void swapCaptured(List<E> row) {
        int last = row.size() - 1;
        if (last > 0) {
            E first = row.get(0);
            row.set(0, row.get(last));
            row.set(last, first);
        }
    }

    public static void main(String[] args) {
        List<List<?>> mixed = new ArrayList<>();
        mixed.add(new ArrayList<>(List.of("A", "B", "C")));
        mixed.add(new ArrayList<>(List.of(1, 2)));
        mixed.add(new ArrayList<>(List.of(true)));

        swapEndsInEveryRow(mixed);
        System.out.println(mixed);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac NestedCaptureDemo.java
java NestedCaptureDemo
~~~

Stdout:

~~~text
[[C, B, A], [2, 1], [true]]
~~~

Diễn giải chính xác:

1. `swapEndsInEveryRow` nhận một danh sách có các hàng mà kiểu thực chưa biết, nhưng mỗi hàng sử dụng được như `List<?>`.
2. Mỗi lần lặp tạo một tham chiếu `row: List<?>` tới **một hàng cụ thể**.
3. Lời gọi `swapCaptured(row)` cho compiler capture đối số kiểu ẩn của **hàng hiện tại** thành `E`.
4. Trong `swapCaptured`, `get` và `set` cùng sử dụng `E` nên hoán đổi các phần tử **của chính hàng đó** an toàn về kiểu.
5. Khi vòng lặp chuyển sang một hàng khác, một capture khác có thể đại diện cho một kiểu phần tử hoàn toàn khác. Helper không ép mọi hàng thành cùng một `E`.

Phương thức không cần `@SuppressWarnings("unchecked")` và không dùng raw type. Việc `mixed` là `ArrayList` gồm các hàng `ArrayList` giúp `set` hoạt động lúc chạy; nếu một hàng được tạo bởi `List.of(...)`, `set` sẽ ném `UnsupportedOperationException` dù capture thành công.

### Tại sao đây không phải cách “đưa bất kỳ Object nào vào hàng”?

Giả sử helper:

~~~java
private static <E> void addCaptured(List<E> row, E value) {
    row.add(value);
}
~~~

Đây là **phương thức minh họa cần đặt trong class với `import java.util.List;`**. Việc có tham số `E` không tự tạo ra một giá trị `E` mới. Nếu caller chỉ có `List<?>` và một `Object` bất kỳ thì vẫn không thể chứng minh `Object` phù hợp với `E` ẩn:

~~~java
static void addUnknown(List<?> row, Object value) {
    // addCaptured(row, value); // LỖI: không bảo đảm value có kiểu E ẩn.
}
~~~

Vì vậy, **helper generic chỉ giúp diễn đạt quan hệ kiểu thật sự được bảo đảm**, chẳng hạn giá trị lấy ra từ chính `List<E>`. Nó không phải công cụ bỏ qua kiểm tra kiểu.

## 6. Capture tầng ngoài khác với capture tầng trong

Có hai công việc hoàn toàn khác nhau:

- **Hoán đổi hai hàng có sẵn trong cùng danh sách ngoài:** cần giữ đúng kiểu hàng ẩn của tầng ngoài.
- **Hoán đổi hai phần tử có sẵn trong cùng một hàng:** cần giữ đúng kiểu phần tử ẩn của tầng trong.

Tầng nào bị wildcard che đi, tầng ấy có thể cần capture riêng. Chương trình sau minh họa **capture tầng ngoài** bằng việc hoán đổi hai hàng `List<Integer>` trong một `List<List<Integer>>` được nhận dưới góc nhìn rộng hơn.

Tạo **`OuterCaptureDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class OuterCaptureDemo {
    static void swapOuterRows(List<? extends List<?>> rows) {
        if (rows.size() >= 2) {
            swapCaptured(rows, 0, 1);
        }
    }

    private static <E> void swapCaptured(List<E> list, int i, int j) {
        E first = list.get(i);
        list.set(i, list.get(j));
        list.set(j, first);
    }

    public static void main(String[] args) {
        List<List<Integer>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of(1, 2)));
        rows.add(new ArrayList<>(List.of(3, 4)));

        swapOuterRows(rows);

        System.out.println(rows);
    }
}
~~~

~~~bash
javac OuterCaptureDemo.java
java OuterCaptureDemo
~~~

Stdout chính xác:

~~~text
[[3, 4], [1, 2]]
~~~

Ở helper, `E` là kiểu hàng thực của **danh sách ngoài** (ở ví dụ là `List<Integer>`), chứ không phải kiểu phần tử `Integer` bên trong mỗi hàng. Hoán đổi hai hàng vốn cùng một danh sách là an toàn vì cả hai có cùng kiểu phần tử của collection ngoài.

Không nên đọc chương trình này thành “wildcard capture cho phép thêm một hàng `List<String>` mới”. Capture chỉ giữ kiểu `E` của những hàng **đang có**, không cho phép tạo một giá trị bất kỳ thuộc subtype của `List<?>` rồi chèn vào.

## 7. Giới hạn quan trọng: hai wildcard độc lập không đồng nhất với nhau

Một nhầm lẫn phổ biến là nếu hai tham số cùng viết `List<?>` thì chúng “cùng một wildcard”. Thực tế mỗi vị trí wildcard chỉ biết có một kiểu ẩn; **không có điều kiện nào khẳng định hai kiểu ẩn bằng nhau**.

~~~java
List<?> first = new ArrayList<String>(List.of("A"));
List<?> second = new ArrayList<Integer>(List.of(1));

// first.set(0, second.get(0)); // LỖI BIÊN DỊCH.
~~~

Đây là đoạn minh họa cần import `java.util.*` và đặt trong class. Nếu phép gán được chấp nhận, `Integer` sẽ bị ghi vào danh sách chứa `String`. Cả hai biến cùng viết `List<?>` hoàn toàn không đủ để bảo đảm an toàn.

Tương tự trong một danh sách lồng nhau:

~~~java
List<List<?>> rows = new ArrayList<>();
rows.add(new ArrayList<String>(List.of("A")));
rows.add(new ArrayList<Integer>(List.of(1)));

List<?> row0 = rows.get(0);
List<?> row1 = rows.get(1);

// row0.set(0, row1.get(0)); // LỖI BIÊN DỊCH.
~~~

Một helper generic nắm bắt `row0` cũng không chứng minh phần tử từ `row1` có cùng kiểu. Việc hai hàng cùng thuộc một `List<List<?>>` chỉ bảo đảm **mỗi hàng là một `List<?>`**, không bảo đảm **các hàng có cùng đối số kiểu**.

### Giải pháp khi thực sự cần liên hệ kiểu giữa nguồn và đích

Nếu bài toán yêu cầu chuyển một phần tử từ danh sách nguồn sang danh sách đích có khả năng nhận kiểu đó, hãy **diễn đạt quan hệ bằng type parameter và cận rõ ràng**:

~~~java
static <T> void appendFirst(
        List<? extends T> source,
        List<? super T> destination) {
    if (!source.isEmpty()) {
        destination.add(source.get(0));
    }
}
~~~

Đây là **phương thức minh họa cần import `java.util.List` và đặt trong class**.

Với `List<Integer>` nguồn và `List<Number>` đích, compiler chứng minh được ràng buộc bằng PECS và lời gọi hợp lệ. Với `List<?>` nguồn và `List<?>` đích không có mối liên hệ khai báo, compiler không được tự giả định chúng cùng kiểu. **Nếu thông tin kiểu đã bị xóa khỏi API ở hai đầu**, có thể cần thiết kế lại API hoặc giữ generic type parameter xuyên suốt thay vì tìm cách “bắt giữ mạnh hơn”.

## 8. Giới hạn của wildcard lồng nhau khi truyền collection cấp hai

Xem các kiểu:

~~~java
List<List<Integer>> integerGroups = new ArrayList<>();
List<List<? extends Number>> groupSlots = new ArrayList<>();

// groupSlots = integerGroups; // LỖI BIÊN DỊCH.
groupSlots.add(new ArrayList<Integer>()); // Hợp lệ.
~~~

Đoạn minh họa cần import `java.util.*` và đặt trong class. Điểm dễ nhầm: `List<Integer>` **tương thích** với `List<? extends Number>`, nhưng không vì thế mà `List<List<Integer>>` trở thành subtype của `List<List<? extends Number>>`. Tầng ngoài vẫn **invariant** theo đối số kiểu.

Nếu phép gán vào `groupSlots` được cho phép, mã sau đó có thể thêm một `List<Double>` vào danh sách ban đầu chỉ chứa các `List<Integer>`. Compiler vì thế từ chối.

Cách để **nhận** một danh sách các nhóm subtype `Number` là:

~~~java
List<List<Integer>> integerGroups =
        List.of(List.of(1, 2), List.of(3));

List<? extends List<? extends Number>> readOnlyView = integerGroups;
~~~

“readOnlyView” là tên biến minh họa; nó không bảo đảm collection runtime bất biến. Nó là một **góc nhìn bị giới hạn việc thêm hàng/phần tử**. Hai dấu `? extends` cho phép đọc số an toàn qua cả hai tầng mà không mở quyền ghi sai kiểu.

### Một bảng phân biệt

| Kiểu tham số | Nhận `List<List<Integer>>`? | Có thể thêm một hàng `List<Double>` qua tham số? | Có thể đọc số như `Number`? |
| --- | --- | --- | --- |
| `List<List<Number>>` | Không | Không; chỉ thêm được một hàng có kiểu `List<Number>` | Có |
| `List<List<? extends Number>>` | Không | Có, vì `List<Double>` tương thích `List<? extends Number>` | Có |
| `List<? extends List<? extends Number>>` | Có | Không thêm hàng mới không phải `null` | Có |
| `List<? extends List<?>>` | Có | Không thêm hàng mới không phải `null` | Chỉ đọc phần tử như `Object` |

Ở hàng đầu, dù một `List<Number>` có thể chứa giá trị `Double`, bản thân `List<Double>` **không** phải là `List<Number>`. Bởi vậy không thể thêm nguyên hàng `List<Double>` vào `List<List<Number>>`. Hãy giữ riêng hai khái niệm *đối số kiểu của hàng* và *giá trị phần tử trong hàng*.

## 9. Ví dụ thực tế: làm phẳng nhiều nhóm số bằng nested producer

Một phương thức chỉ **đọc** các nhóm số nên nhận danh sách ngoài với hai tầng wildcard producer:

~~~java
static List<Number> flatten(
        List<? extends List<? extends Number>> groups) {
    List<Number> result = new ArrayList<>();
    for (List<? extends Number> group : groups) {
        for (Number number : group) {
            result.add(number);
        }
    }
    return result;
}
~~~

Đây là **phương thức minh họa cần import `java.util.*` và đặt trong class**. Nó chấp nhận danh sách gồm các hàng `Integer` hoặc `Double`; mọi phần tử đọc ra đều là `Number`. Danh sách kết quả là **một `ArrayList<Number>` mới**, nên việc thêm `Number` vào kết quả không hề vi phạm giới hạn ghi trên nguồn wildcard.

Tạo **`NestedFlattenDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class NestedFlattenDemo {
    static List<Number> flatten(
            List<? extends List<? extends Number>> groups) {
        List<Number> result = new ArrayList<>();
        for (List<? extends Number> group : groups) {
            for (Number value : group) {
                result.add(value);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        List<List<Integer>> integerGroups =
                List.of(List.of(1, 2), List.of(3));
        List<List<Double>> doubleGroups =
                List.of(List.of(1.5, 2.5));

        System.out.println("integer groups = " + flatten(integerGroups));
        System.out.println("double groups = " + flatten(doubleGroups));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NestedFlattenDemo.java
java NestedFlattenDemo
~~~

Stdout chính xác:

~~~text
integer groups = [1, 2, 3]
double groups = [1.5, 2.5]
~~~

Ở ví dụ này ta **không cần wildcard capture helper** vì chỉ đọc từ nguồn và thêm vào danh sách `result` có kiểu phần tử xác định `Number`. Không nên dùng capture chỉ vì thấy nhiều dấu `?`; hãy dùng khi thật sự cần liên kết giá trị đọc/ghi có cùng kiểu ẩn.

Nếu đầu vào cần chứa **nhiều loại hàng khác nhau trong cùng một danh sách ngoài**, có thể tạo `List<List<? extends Number>>` và thêm các hàng `List<Integer>`, `List<Double>` rồi gọi `flatten`. Điều này được phép vì mỗi hàng đều phù hợp `List<? extends Number>`; danh sách ngoài lúc ấy khai báo chính xác kiểu phần tử `List<? extends Number>`.

## 10. Khi cần cả nguồn lồng nhau và đích nhận phần tử

Kết hợp PECS với nested wildcard:

~~~java
static <T> void appendFlattened(
        List<? extends List<? extends T>> groups,
        List<? super T> destination) {
    for (List<? extends T> group : groups) {
        for (T value : group) {
            destination.add(value);
        }
    }
}
~~~

Đây là **phương thức minh họa**, cần import `java.util.List` và đặt trong class.

- `groups`: tầng ngoài đọc ra các nhóm, tầng trong đọc ra các `T` hoặc subtype của `T`.
- `destination`: nhận giá trị `T` hoặc subtype của `T`.
- `T` kết nối **hướng đọc từ các nhóm** với **hướng ghi vào đích**, không yêu cầu mọi nhóm phải chứa chính xác cùng subtype.
- Phương thức không đưa phần tử mới vào các nhóm nguồn nên không cần nắm bắt riêng kiểu của từng nhóm.

Ví dụ:

~~~java
List<List<Integer>> groups = List.of(List.of(1, 2), List.of(3));
List<Number> destination = new ArrayList<>();

appendFlattened(groups, destination);
System.out.println(destination); // [1, 2, 3]
~~~

Đây là đoạn lời gọi minh họa cần import `java.util.*` và đặt trong class cùng phương thức `appendFlattened`. Compiler có thể suy luận `T` phù hợp từ cả nguồn và đích, hoặc ta có thể gọi `ClassName.<Number>appendFlattened(groups, destination)` để chỉ định `T = Number` tường minh.

Ngược lại, nếu muốn ghi tất cả phần tử `Number` vào các hàng `List<Integer>`, phép ghi không an toàn: một `Double` có thể lọt vào hàng `Integer`. Không có cách đặt helper capture nào làm hợp đồng sai ấy trở thành đúng.

## 11. Capture không phải phép biến đổi runtime hoặc kỹ thuật “xóa” giới hạn

### 11.1. Capture không tạo ra đối tượng mới, không kiểm tra subtype thực

Khi gọi `helper(List<E>)` với tham số `List<?>`, compiler đưa ra một kiểu `E` tạm để kiểm tra biểu thức. Đây là **cơ chế compile-time**, không phải lời gọi tạo class đặc biệt ở runtime. Java generics sử dụng **type erasure**, nên chương trình không có thông tin đầy đủ về đối số generic của từng `List` tại runtime.

Không thể làm kiểm tra:

~~~java
Object value = List.of(1, 2);

// if (value instanceof List<String>) { ... } // LỖI BIÊN DỊCH.
if (value instanceof List<?> list) {
    System.out.println(list.size());
}
~~~

Đây là đoạn minh họa cần import `java.util.List` và đặt trong class. `instanceof List<?>` dùng được vì chỉ kiểm tra được đối tượng là `List`, không xác nhận subtype phần tử của nó.

### 11.2. Capture không chứng minh hai kiểu ẩn bằng nhau

Hai biến `List<?>` chứa hai danh sách cùng là `String` cũng **không trở thành một hợp đồng generic chung** chỉ vì dữ liệu hiện tại giống nhau. Compiler kiểm tra **chữ ký và ràng buộc kiểu**, không suy luận một đẳng thức generic mới từ việc nhìn nội dung runtime.

Nếu cần một API dùng hai danh sách có chung kiểu `E`, hãy giữ chữ ký `<E> ... (List<E> a, List<E> b)` từ lúc dữ liệu còn được định kiểu, hoặc cung cấp ràng buộc chuyển giá trị an toàn như `List<? extends E>` và `List<? super E>`.

### 11.3. Capture không bảo đảm collection có thể thay đổi

~~~java
List<?> immutable = List.of("A", "B");
swapCaptured(immutable); // Biên dịch, nhưng set ném UnsupportedOperationException.
~~~

Đoạn này là minh họa ở trong class có phương thức `swapCaptured(List<E>)` như đã định nghĩa ở trên. Khi helper sử dụng `set`, danh sách thật vẫn có quyền từ chối sửa. Điều tương tự áp dụng ở tầng ngoài và trong của một danh sách lồng nhau: mỗi tầng có thể được cài đặt bằng collection cho phép hoặc không cho phép sửa.

### 11.4. Capture không hợp thức hóa unchecked cast

~~~java
List<Integer> integers = new ArrayList<>();
@SuppressWarnings("unchecked")
List<String> forged = (List<String>) (List<?>) integers;
forged.add("wrong"); // Nguy cơ heap pollution.
~~~

Đây là **mã minh họa nguy cơ**, không phải hướng giải quyết. Cast unchecked có thể đánh lừa hệ thống kiểu tĩnh và làm hỏng hợp đồng `List<Integer>`. Capture helper đúng cách **không cần unchecked cast**.

## 12. Các lỗi thường gặp khi đọc wildcard lồng nhau

**Lỗi 1 — Bỏ qua tầng có wildcard.** `List<List<?>>` và `List<? extends List<?>>` không có cùng quyền ghi lên danh sách ngoài. Một kiểu cho thêm một `List<X>` phù hợp; kiểu còn lại không.

**Lỗi 2 — Suy ra quan hệ subtype xuyên qua hai tầng generic bất biến.** Từ `List<Integer> <: List<? extends Number>` không được suy ra `List<List<Integer>> <: List<List<? extends Number>>`. Muốn mở rộng đầu vào ở tầng ngoài, cần một wildcard ở đúng tầng đó.

**Lỗi 3 — Nhìn thấy hai dấu `?` thì mặc định chúng cùng kiểu.** `List<List<?>>` có thể chứa một hàng `List<String>` và một hàng `List<Integer>`. Đối số kiểu của hai hàng hoàn toàn có thể khác nhau.

**Lỗi 4 — Nghĩ capture helper có thể nhận thêm bất kỳ `Object`.** Helper `<E>` chỉ thao tác được với giá trị đã được chứng minh thuộc `E`; không thể biến một `Object` ngẫu nhiên thành `E` bằng suy luận.

**Lỗi 5 — Nghĩ capture của hàng đầu tự động mở quyền ghi lên danh sách ngoài.** Cấp ngoài và cấp trong có thể có hai kiểu ẩn khác nhau; capture phải gắn với đúng collection mà phép đọc/ghi đang thực hiện.

**Lỗi 6 — Nhầm wildcard với bất biến runtime.** `List<?>` hoặc `List<? extends Number>` không đồng nghĩa immutable. `remove` hoặc `clear` có thể hợp lệ về kiểu; `set` qua helper có thể hợp lệ; runtime lại tùy collection cho sửa hay không.

**Lỗi 7 — Dùng `List<Object>` để thay `List<?>`.** `List<Object>` chỉ chấp nhận đối số kiểu chính xác `Object`. Nó không là “dạng dễ ghi của mọi danh sách”; ép kiểu từ `List<String>` sang `List<Object>` thường là dấu hiệu sai thiết kế.

**Lỗi 8 — Quên điều kiện kích thước và khả năng sửa.** Helper hoán đổi phần tử nên kiểm tra có ít nhất hai phần tử trước khi đọc/ghi; `List.of(...)` vẫn ném ngoại lệ nếu muốn `set`. Compiler chỉ kiểm tra kiểu, không bảo đảm logic runtime.

## 13. Bài tập kiểm tra kiến thức

Giả sử có các biến trong một phương thức Java với `import java.util.*;`:

~~~java
List<List<Integer>> integers = new ArrayList<>();
integers.add(new ArrayList<>(List.of(1, 2)));

List<List<?>> mixed = new ArrayList<>();
List<? extends List<?>> upperOuter = integers;
List<? extends List<? extends Number>> upperBoth = integers;
~~~

Hãy xác định các biểu thức sau **biên dịch được hay không**, xét từng dòng độc lập:

| Biểu thức | Biên dịch? | Giải thích |
| --- | --- | --- |
| `mixed.add(List.of("A"));` | Có | Phần tử tầng ngoài có kiểu `List<?>` |
| `mixed.add(List.of(1, 2));` | Có | `List<Integer>` phù hợp `List<?>` |
| `mixed = integers;` | Không | Tầng ngoài invariant; `List<List<Integer>>` không là `List<List<?>>` |
| `upperOuter = integers;` | Có | Kiểu hàng `List<Integer>` phù hợp upper bound `List<?>` |
| `upperOuter.add(List.of(3));` | Không | Tầng ngoài có kiểu phần tử ẩn |
| `List<?> row = upperOuter.get(0);` | Có | Hàng đọc ra dùng được như `List<?>` |
| `Object x = upperOuter.get(0).get(0);` | Có | Phần tử tầng trong dùng được như `Object` |
| `upperBoth = integers;` | Có | Cả hai tầng upper bound đều phù hợp |
| `Number n = upperBoth.get(0).get(0);` | Có | Bảo đảm đọc như `Number` |
| `upperBoth.get(0).add(10);` | Không | Không biết kiểu phần tử của hàng |
| `upperBoth.add(List.of(10));` | Không | Không biết kiểu hàng thực của tầng ngoài |
| `mixed.get(0).add("X");` | Không | Tầng trong là `List<?>` |
| `mixed.get(0).clear();` | Có | Xóa không cần cung cấp giá trị của kiểu ẩn; runtime tùy collection |

Với bài tập, `mixed.get(0)` có thể ném `IndexOutOfBoundsException` khi chạy vì danh sách `mixed` ban đầu rỗng; bảng chỉ hỏi **khả năng biên dịch**. Trong phép gán `mixed = integers`, compiler chặn ngay cả khi tình cờ các hàng hiện tại trống hoặc cùng kiểu.

### Câu hỏi vận dụng

1. Vì sao việc dùng `List<? extends List<?>>` để đọc nhiều kiểu hàng là an toàn, trong khi thêm `List<String>` vào nó bị chặn?
2. Có thể dùng helper `<E> void swap(List<E> row)` để hoán đổi phần tử trong một hàng `List<?>` không? **Có**, vì đọc và ghi dùng cùng `E` được capture.
3. Có thể lấy phần tử từ hai hàng `List<?>` khác nhau rồi hoán đổi chéo mà không ràng buộc chung không? **Không thể bảo đảm**, vì hai kiểu ẩn có thể khác nhau.
4. Nếu chỉ muốn tính tổng `Number` từ `List<List<Integer>>`, nên dùng helper capture hay nested `extends`? **Nested `extends` là đủ**, vì chỉ cần đọc giá trị.
5. Tại sao `List<List<? extends Number>>` không nhận trực tiếp `List<List<Integer>>`? **Bởi generic ở tầng ngoài invariant**, dù kiểu hàng tương thích wildcard.

## 14. Kết luận và quy tắc thiết kế

Muốn dùng wildcard lồng nhau an toàn, hãy **đọc kiểu từ tầng ngoài vào tầng trong**, sau đó xác định thao tác ở tầng nào:

- `List<List<?>>`: danh sách ngoài có phần tử chính xác kiểu `List<?>`; có thể thêm những hàng `List<X>` phù hợp vào ngoài, nhưng mỗi hàng khi đọc ra vẫn có đối số kiểu ẩn.
- `List<? extends List<?>>`: nhận danh sách ngoài có kiểu hàng là subtype của `List<?>` (ví dụ `List<List<Integer>>`), nhưng không được thêm hàng không phải `null` qua góc nhìn wildcard.
- `List<? extends List<? extends Number>>`: nhận danh sách nhóm số subtype ở cả hai tầng và đọc phần tử như `Number`; không được tự ý ghi phần tử/hàng mới qua góc nhìn ấy.
- **Capture helper `<E>`** hữu ích khi cần thao tác với các giá trị **cùng kiểu ẩn từ chính một collection**. Nếu có nhiều collection độc lập, phải có quan hệ kiểu được khai báo rõ; capture không tự tạo quan hệ đó.

Giới hạn cốt lõi: **một wildcard là một kiểu cụ thể nhưng chưa biết; hai wildcard là hai điều chưa biết có thể khác nhau; mỗi tầng wildcard giữ ràng buộc riêng**. Capture giúp compiler theo dõi một kiểu ẩn nhất quán trong phạm vi nhỏ, không loại bỏ tính bất biến generic, không cho phép ghi sai subtype, và không thay đổi khả năng sửa của collection runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wildcard-capture-008">Phân biệt giải quyết lỗi bằng capture với ép kiểu không kiểm tra</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giải quyết lỗi bằng capture với ép kiểu không kiểm tra trong Java Generics

Trong Java Generics, khi trình biên dịch báo lỗi liên quan đến `capture of ?`, có hai hướng xử lý rất khác nhau:

- **Wildcard capture và generic helper**: giúp trình biên dịch **chứng minh** một thao tác thực sự an toàn về kiểu, thường là lấy phần tử từ một collection rồi dùng lại với **chính collection có cùng kiểu phần tử ẩn**.
- **Unchecked cast**: yêu cầu trình biên dịch **tin vào khẳng định của lập trình viên** mà không thể kiểm tra đầy đủ đối số kiểu generic. Mã có thể biên dịch, nhưng giả định sai có thể gây **heap pollution** và `ClassCastException` khi chạy.

Hai kỹ thuật không tương đương. Một lời gọi generic helper dựa trên capture giữ được kiểm tra kiểu tại thời điểm biên dịch; một ép kiểu không kiểm tra có thể vượt qua chốt kiểm tra đó. Đây là sự khác biệt về **bằng chứng an toàn kiểu**, chứ không chỉ khác cú pháp hoặc có/không có cảnh báo.

## 1. Tình huống xuất phát: `List<?>` và kiểu phần tử chưa biết

Giả sử cần viết một phương thức hoán đổi hai vị trí trong một danh sách có kiểu phần tử bất kỳ:

```java
static void swapWrong(List<?> list, int i, int j) {
    Object saved = list.get(i);
    // list.set(i, list.get(j)); // LỖI BIÊN DỊCH.
    // list.set(j, saved);       // LỖI BIÊN DỊCH.
}
```

Đây là **đoạn minh họa lỗi**, không phải chương trình chạy độc lập. `List<?>` có nghĩa danh sách chứa **một kiểu phần tử nào đó chưa biết**. Khi đọc qua wildcard không giới hạn, giá trị có thể dùng như `Object`; nhưng `set` đòi giá trị đúng **kiểu phần tử ẩn** của danh sách.

Thử viết trực tiếp cũng không được:

```java
static void stillWrong(List<?> list) {
    // list.set(0, list.get(0)); // LỖI BIÊN DỊCH.
}
```

Dù thao tác đặt lại chính phần tử đang có là an toàn về ý tưởng, các lần sử dụng biểu thức mang wildcard không được tự động thống nhất thành một tên kiểu `T` chung trong lời gọi `set`. `javac` có thể báo `capture#1 of ?` hoặc `Object cannot be converted to capture#...`. Số thứ tự của capture trong thông báo không phải API ổn định.

Có thể xử lý lỗi này bằng **generic helper** mà không cần ép kiểu.

## 2. Giải bằng generic helper: giữ quan hệ kiểu, không giả mạo type argument

Khi muốn **nhân bản phần tử đầu trong chính một list**, API ngoài có thể nhận `List<?>`, còn helper dùng một type parameter:

```java
static void duplicateFirst(List<?> values) {
    duplicateCaptured(values);
}

private static <T> void duplicateCaptured(List<T> values) {
    if (!values.isEmpty()) {
        T value = values.get(0);
        values.add(value);
    }
}
```

Đây là các method minh họa cần import `java.util.List` và đặt trong class. Ở lời gọi helper, compiler capture `?` từ **một list cụ thể** và suy luận `T`; kết quả `get(0)` và tham số của `add` cùng dùng `T`. Không có phép cast giả định kiểu phần tử là `Object` hay `String`.

So sánh với một hướng làm sai:

```java
// KHÔNG dùng khi chưa chứng minh generic element type:
@SuppressWarnings("unchecked")
static void unsafeInsert(List<?> list) {
    List<String> forged = (List<String>) list;
    forged.add("not necessarily the right type");
}
```

Cast chỉ kiểm tra raw runtime `List`, không chứng minh mọi phần tử vốn là `String`. Nó có thể làm ô nhiễm `List<Integer>` rồi ném `ClassCastException` ở một lần đọc khác. **Generic helper bảo toàn một quan hệ kiểu đã có; unchecked cast tự khẳng định một quan hệ chưa được chứng minh.**

`duplicateFirst` vẫn cần list mutable; với `List.of` có thể có `UnsupportedOperationException`. Nếu cần thay hai phần tử, `Collections.swap` hoặc helper capture tương tự đều phù hợp. Điểm kiểm tra trong bài này là **chỗ nào có type proof và chỗ nào chỉ che cảnh báo**, không phải cài đặt swap.

## 3. Unchecked cast là gì và tại sao nguy hiểm?

**Unchecked cast** là phép ép kiểu mà JVM không thể kiểm tra đầy đủ đối số kiểu generic, ví dụ từ `List<?>` thành `List<String>`:

```java
static List<String> trustMe(List<?> source) {
    return (List<String>) source; // Unchecked cast warning.
}
```

Đoạn mã trên **biên dịch nhưng sinh cảnh báo unchecked** (nếu không bị tắt cảnh báo). Bởi **type erasure**, đối số `<String>` của `List<String>` thường không được kiểm tra ở runtime. JVM có thể xác minh đối tượng là một `List`, nhưng không có bằng chứng mọi phần tử trong đó là `String`.

Hãy xét:

```java
List<Integer> integers = new ArrayList<>(List.of(7, 8));
List<?> unknown = integers;
List<String> strings = (List<String>) unknown; // Cảnh báo unchecked.

String s = strings.get(0); // ClassCastException khi chạy.
```

Phép cast ở dòng thứ ba thường **không ném** `ClassCastException` vì đối tượng thật là một `List`. Lỗi xuất hiện tại `strings.get(0)` khi kết quả đọc được coi là `String` và runtime cast cần thiết phát hiện giá trị thực là `Integer`.

Tính an toàn kiểu đã mất: biến có kiểu biên dịch `List<String>` thực chất tham chiếu một danh sách được tạo và sử dụng như `List<Integer>`.

Điểm quan trọng:

- **Capture helper** dùng đúng biến kiểu `T` do compiler chứng minh; không đòi người dùng khẳng định `T` là kiểu cụ thể nào.
- **Unchecked cast** tự khẳng định đối số kiểu là `String` hoặc một kiểu khác mà compiler không đủ thông tin để xác minh.
- Type erasure **không làm cast trở nên an toàn**; nó chính là một lý do phép kiểm tra tham số generic không thể hoàn tất tại runtime.
- Việc chương trình chưa phát sinh `ClassCastException` ngay lập tức **không chứng minh** cast là đúng.

Không nên đánh đồng unchecked cast với một ép kiểu runtime thông thường như `Object o = "x"; String s = (String) o;`: ép kiểu từ `Object` sang `String` có kiểm tra kiểu thực ở runtime. Với `List<String>`, phần đối số kiểu `<String>` không được kiểm tra đầy đủ.

## 4. So sánh bản chất của hai cách xử lý

| Tiêu chí | Capture + generic helper | Unchecked cast |
| --- | --- | --- |
| Mục đích | Đặt tên một kiểu ẩn và bảo toàn quan hệ kiểu đã tồn tại | Khẳng định một kiểu tham số hóa mà compiler không kiểm chứng đầy đủ |
| Ví dụ | `<T> void swapCaptured(List<T>)` | `(List<String>) someListOfUnknownElementType` |
| Cần biết kiểu thực là `String`/`Integer`? | Không | Thường phải **giả định** hoặc xác minh bằng cách khác |
| Kiểm tra an toàn kiểu | Compiler chứng minh các thao tác trên `T` | Kiểm tra đối số generic có thể thiếu |
| Cảnh báo `unchecked` | Không, nếu helper được viết đúng | Thường có |
| Nguy cơ heap pollution do chính kỹ thuật | Không tạo ra khi dùng đúng | Có, nếu giả định sai |
| Thất bại runtime | Vẫn có thể do chỉ số, mutable, null, v.v. | Ngoài các lỗi thường gặp còn có `ClassCastException` do giả định kiểu sai |
| Dùng tốt nhất | Thao tác nội bộ với một wildcard hoặc ràng buộc kiểu rõ ràng | Chỉ ở ranh giới dữ liệu đã được chứng minh bởi hợp đồng ngoài hệ kiểu |

**Kết luận sử dụng:** trước một lỗi `capture of ?`, kiểm tra liệu có thể viết helper `<T>` nhận một tham số kiểu cụ thể và giữ nguyên quan hệ kiểu trong thân phương thức hay không. Một cast chỉ để compiler im lặng không phải lời giải thích cho tính đúng đắn.

## 5. Minh họa heap pollution: ghi sai kiểu qua raw type

**Heap pollution** xảy ra khi một biến thuộc kiểu tham số hóa tham chiếu đối tượng không phù hợp với kiểu tham số hóa mà biến đó tuyên bố. Unchecked cast và raw type là những con đường có thể tạo điều kiện cho vấn đề này.

Ví dụ rủi ro, **không dùng trong code ứng dụng**:

```java
@SuppressWarnings({"rawtypes", "unchecked"})
static void pollute(List<Integer> numbers) {
    List raw = numbers;
    raw.add("wrong"); // Bỏ qua kiểm tra phần tử Integer.
}

static void caller() {
    List<Integer> numbers = new ArrayList<>(List.of(10));
    pollute(numbers);

    Integer value = numbers.get(1); // ClassCastException khi chạy.
}
```

Hệ kiểu `List<Integer>` yêu cầu mọi phần tử được lấy ra dưới dạng `Integer`. Nhưng raw reference cho phép thêm một `String`. Lúc `get(1)` được dùng làm `Integer`, runtime cast do compiler sinh ra có thể phát hiện sai kiểu và ném `ClassCastException`.

Một cách rủi ro tương tự là ép `List<?>` sang `List<String>` rồi tiếp tục thêm phần tử `String` vào danh sách thật `List<Integer>`. Lần đọc dưới dạng `Integer` ở phía chủ sở hữu có thể thất bại về sau, rất xa nơi thực hiện unchecked cast.

Không phải mọi unchecked cast đều tức khắc làm hỏng nội dung danh sách. Nhưng nó mở ra khả năng sai kiểu vì **lời hứa của kiểu tham số hóa chưa được xác minh**.

## 6. Vì sao `@SuppressWarnings("unchecked")` không phải giải pháp?

Ví dụ:

```java
@SuppressWarnings("unchecked")
static List<String> uncheckedStrings(List<?> source) {
    return (List<String>) source;
}
```

Annotation `@SuppressWarnings("unchecked")` chỉ **ẩn cảnh báo của compiler trong phạm vi được áp dụng**. Nó không:

- Kiểm tra các phần tử của `source`.
- Biến `Integer` thành `String`.
- Thêm kiểm tra generic bị thiếu vào bytecode.
- Ngăn `ClassCastException` hoặc heap pollution.
- Tạo ra quan hệ capture giữa hai kiểu wildcard khác nhau.

Nếu thật sự có một hợp đồng ngoài hệ kiểu bảo đảm cast đúng, hãy đặt annotation ở **phạm vi nhỏ nhất**, ghi rõ nguồn bảo đảm, kiểm tra ranh giới nếu có thể, và không để kiểu sai lan rộng.

Ví dụ một phương thức thư viện cũ trả về raw `List` có thể cần xử lý tương thích. Nhưng đó là bài toán **tin cậy dữ liệu legacy**, không phải lý do dùng unchecked cast để sửa lỗi có thể giải quyết bằng generic helper.

## 7. Cách an toàn khi cần một danh sách có kiểu cụ thể: kiểm tra phần tử và sao chép

Khi thực sự phải chuyển từ `List<?>` sang `List<String>`, capture không thể chứng minh rằng mọi phần tử đều là `String`: wildcard có thể biểu thị `Integer`, `Double` hoặc kiểu khác. Có thể kiểm tra **từng phần tử tại runtime** bằng `Class.cast` và xây một danh sách mới:

```java
static <T> List<T> checkedCopy(
        List<?> source, Class<T> elementType) {
    List<T> destination = new ArrayList<>();
    for (Object item : source) {
        destination.add(elementType.cast(item));
    }
    return destination;
}
```

Ví dụ sử dụng (các import thuộc `java.util`):

```java
List<?> unknown = List.of("a", "b");
List<String> strings = checkedCopy(unknown, String.class);
System.out.println(strings); // [a, b]
```

Nếu gọi `checkedCopy(List.of(1, 2), String.class)`, nó ném `ClassCastException` **trong lúc kiểm tra phần tử**; không trả về một `List<String>` sai kiểu.

Đây là một kỹ thuật khác với capture helper:

- `checkedCopy` **có kiểm tra runtime theo `Class<T>`** rồi tạo collection mới.
- `swapCaptured` **không kiểm tra runtime để đoán `T`**; compiler giữ quan hệ phần tử trong cùng collection.
- `Class<T>` ở ví dụ nhận các lớp có reifiable runtime class token như `String.class`. Không thể có `List<String>.class` để kiểm tra đệ quy một cấu trúc generic bất kỳ bằng `Class.cast`.
- `Class.cast(null)` trả về `null`, nên một nguồn chứa null có thể được sao chép với phần tử null. Việc collection đích cho phép null phụ thuộc implementation; `ArrayList` trong ví dụ cho phép.

Nếu không cần tạo collection mới, có thể **kiểm tra từng phần tử tại ranh giới sử dụng** bằng `instanceof`/`Class.cast` mà không cast cả danh sách thành một kiểu tham số hóa cụ thể. Chi phí kiểm tra và sao chép được đánh đổi để lấy an toàn kiểu rõ ràng khi xử lý dữ liệu không tin cậy.

## 8. Capture chỉ giải quyết quan hệ kiểu có thật

Giả sử hai danh sách được khai báo:

```java
List<?> left = List.of(1, 2);
List<?> right = List.of("a", "b");
```

Không có phép chuyển phần tử tổng quát an toàn từ `right` sang `left`:

```java
static void copyUnknown(List<?> target, List<?> source) {
    // target.add(source.get(0)); // LỖI BIÊN DỊCH.
}
```

Một helper nhận hai `List<T>` cũng **không** giải quyết được khi chỉ có hai đối số wildcard độc lập:

```java
static <T> void requiresSameType(List<T> a, List<T> b) {
    a.addAll(b);
}

static void fails(List<?> a, List<?> b) {
    // requiresSameType(a, b); // LỖI BIÊN DỊCH.
}
```

Hai dấu `?` có thể ứng với hai kiểu khác nhau. Compiler không có bằng chứng rằng một `T` duy nhất phù hợp với cả hai tham số `List<T>`. Thậm chí truyền cùng một biến `List<?>` hai lần cho hai tham số `List<T>` cũng có thể bị từ chối do các capture tại từng vị trí đối số là độc lập.

**Không được sửa bằng**:

```java
@SuppressWarnings("unchecked")
static void fakeCopy(List<?> target, List<?> source) {
    List<Object> forgedTarget = (List<Object>) target;
    forgedTarget.addAll(source); // Có thể chèn String vào List<Integer>.
}
```

Đoạn mã này chỉ minh họa hướng làm sai. Ép `List<?>` thành `List<Object>` không chứng minh rằng `target` là danh sách dành cho mọi loại `Object`. Nó có thể phá hợp đồng phần tử của danh sách thật.

Khi dữ liệu nguồn–đích có quan hệ kiểu xác định, hãy **thể hiện quan hệ ngay trong chữ ký**:

```java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    destination.addAll(source);
}
```

`source` cung cấp giá trị dùng được như `T`, `destination` chấp nhận `T`. Đây là ứng dụng **Producer Extends, Consumer Super (PECS)**. Ví dụ `List<Integer>` có thể là nguồn cho `List<Number>`, không cần unchecked cast. Nếu hai loại phần tử không có quan hệ phù hợp, lời gọi bị từ chối như mong muốn.

## 9. Chương trình Java 17+ hoàn chỉnh

Chương trình sau chạy được với JDK 17 trở lên. Nó thể hiện ba hành vi khác nhau:

1. **Capture helper:** hoán đổi và lặp lại phần tử trong một danh sách mà vẫn an toàn kiểu.
2. **Generic nguồn–đích:** chuyển các `Integer` sang đích `List<Number>` có quan hệ kiểu hợp lệ.
3. **Unchecked cast/raw:** giả định sai hoặc ghi sai kiểu dẫn tới `ClassCastException`. Hai ngoại lệ được bắt để chương trình minh họa vẫn hoàn tất.

Lưu thành tệp **`CaptureVsCastDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CaptureVsCastDemo {
    static void swap(List<?> list, int i, int j) {
        swapCaptured(list, i, j);
    }

    private static <T> void swapCaptured(List<T> list, int i, int j) {
        T old = list.get(i);
        list.set(i, list.get(j));
        list.set(j, old);
    }

    static void duplicateFirst(List<?> list) {
        duplicateCaptured(list);
    }

    private static <T> void duplicateCaptured(List<T> list) {
        if (!list.isEmpty()) {
            list.add(list.get(0));
        }
    }

    static <T> void appendAll(
            List<? extends T> src, List<? super T> dst) {
        dst.addAll(src);
    }

    @SuppressWarnings("unchecked")
    static List<String> uncheckedStrings(List<?> list) {
        return (List<String>) list;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void pollute(List<Integer> integers) {
        List raw = integers;
        raw.add("wrong");
    }

    public static void main(String[] args) {
        List<Integer> ints = new ArrayList<>(List.of(1, 2, 3));
        swap(ints, 0, 2);
        duplicateFirst(ints);
        System.out.println("ints = " + ints);

        List<String> words = new ArrayList<>(List.of("a", "b"));
        swap(words, 0, 1);
        System.out.println("words = " + words);

        List<Number> numbers = new ArrayList<>();
        appendAll(ints, numbers);
        System.out.println("numbers = " + numbers);

        List<String> forged = uncheckedStrings(ints);
        try {
            String value = forged.get(0);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println(
                    "unchecked read: " + ex.getClass().getSimpleName());
        }

        List<Integer> polluted = new ArrayList<>(List.of(10));
        pollute(polluted);
        try {
            Integer value = polluted.get(1);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println(
                    "raw write: " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CaptureVsCastDemo.java
java CaptureVsCastDemo
```

Stdout chính xác:

```text
ints = [3, 2, 1, 3]
words = [b, a]
numbers = [3, 2, 1, 3]
unchecked read: ClassCastException
raw write: ClassCastException
```

Giải thích kết quả:

- Hai danh sách đầu tiên được hoán đổi thành công bằng capture helper, không có chèn phần tử sai kiểu.
- `duplicateFirst` đọc phần tử đầu và thêm lại **chính kiểu đó** thông qua helper `List<T>`, nên `ints` thành `[3, 2, 1, 3]`.
- `appendAll` truyền các `Integer` sang `List<Number>` nhờ giới hạn `extends/super`.
- `uncheckedStrings(ints)` không xác minh các phần tử thuộc `String`; lỗi bộc lộ khi đọc kết quả dưới dạng `String`.
- `pollute` dùng raw type để thêm `String` vào `List<Integer>`; lần đọc dưới dạng `Integer` thất bại.

Các hàm `uncheckedStrings` và `pollute` chỉ dùng để **chứng minh hậu quả của code không an toàn**. Không sao chép chúng vào code sản phẩm như một giải pháp sửa lỗi generics.

### Kiểm chứng cách sao chép có kiểm tra kiểu

Nếu cần chuyển dữ liệu từ một `List<?>` không tin cậy thành danh sách phần tử `String`, có thể dùng chương trình độc lập sau.

Lưu thành **`CheckedCopyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CheckedCopyDemo {
    static <T> List<T> checkedCopy(
            List<?> source, Class<T> elementType) {
        List<T> result = new ArrayList<>();
        for (Object item : source) {
            result.add(elementType.cast(item));
        }
        return result;
    }

    public static void main(String[] args) {
        List<?> strings = List.of("a", "b");
        System.out.println(
                "verified = " + checkedCopy(strings, String.class));

        try {
            checkedCopy(List.of(1, 2), String.class);
        } catch (ClassCastException ex) {
            System.out.println(
                    "checked mismatch: " + ex.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac CheckedCopyDemo.java
java CheckedCopyDemo
```

Stdout:

```text
verified = [a, b]
checked mismatch: ClassCastException
```

Sự khác biệt nằm ở vị trí kiểm tra: `checkedCopy` xác minh ngay khi nhận từng phần tử, còn unchecked cast toàn bộ `List<?>` có thể tạo ra một tham chiếu sai kiểu và chỉ lỗi muộn khi đọc hoặc sử dụng dữ liệu.

## 10. Quy trình quyết định khi gặp lỗi generics

Khi gặp thông báo `incompatible types` hoặc `capture#... of ?`, hãy đi theo các bước sau.

### Bước 1: Xác định thao tác đọc hay ghi

`List<?>` đọc ra `Object` an toàn, nhưng không cho phép thêm giá trị không-null tùy ý. `List<? extends Number>` cho đọc như `Number`; `List<? super Integer>` cho thêm `Integer` nhưng đọc chung như `Object`.

Nếu chỉ cần đọc, có thể không cần helper:

```java
static void printAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

### Bước 2: Kiểm tra có một kiểu ẩn cần duy trì xuyên suốt không

Một thao tác cần lấy rồi đặt lại phần tử **trong cùng danh sách** thường là trường hợp phù hợp với `<T> void helper(List<T>)`. Giá trị đọc từ `List<T>` có đúng kiểu `T` để đưa lại vào chính danh sách ấy.

Không dùng cast chỉ vì không biết tên `T`. Một helper có thể giữ thông tin mà compiler đã có nhưng chưa thể biểu diễn qua wildcard ngoài.

### Bước 3: Kiểm tra có nhiều wildcard độc lập không

Nếu một phương thức nhận `List<?> a` và `List<?> b`, chúng **có thể có hai kiểu khác nhau**. Capture không chứng minh chúng bằng nhau. Hãy xem nguồn và đích có một quan hệ `<T>` thực sự hay không, và thể hiện bằng `? extends T`/`? super T` khi thích hợp.

### Bước 4: Nếu thật sự cần kiểu cụ thể, kiểm tra tại ranh giới

Một API muốn `List<String>` từ dữ liệu không xác định phải có bằng chứng nguồn đúng kiểu. Có thể kiểm tra từng phần tử, sao chép và tạo danh sách đã xác minh. Tránh khẳng định cả danh sách chỉ bằng `(List<String>) source`.

### Bước 5: Chỉ dùng unchecked cast khi có lý do kiểm chứng được

Tích hợp raw API cũ có thể buộc phải cast không kiểm tra. Khi đó nên:

- Xác định rõ **hợp đồng bên ngoài nào bảo đảm kiểu phần tử**.
- Thu hẹp vùng chứa raw type/unchecked cast và dùng `@SuppressWarnings("unchecked")` chỉ sát lời gọi cần thiết.
- Xác minh dữ liệu nếu hợp đồng không thể tin cậy.
- Không trả tham chiếu có kiểu khai báo sai ra phần còn lại của chương trình.
- Kiểm tra đường đọc và đường ghi, vì lỗi có thể phát sinh muộn và không ở dòng cast.

Cảnh báo unchecked là thông tin hữu ích của compiler; loại bỏ cảnh báo bằng annotation không thay cho phân tích an toàn kiểu.

## 11. Các hiểu lầm phổ biến

### “Capture conversion chính là một kiểu cast”

**Sai.** Capture đưa wildcard thành biến kiểu ẩn để compiler giải quyết ràng buộc; không có ép kiểu runtime từ `Integer` sang `String` và không biến tham số kiểu của danh sách thật. Một unchecked cast khẳng định một kiểu tham số hóa mới mà compiler chưa chứng minh được.

### “Ép sang `List<Object>` an toàn vì mọi đối tượng là `Object`”

**Sai.** `Integer` là `Object` nhưng `List<Integer>` không phải `List<Object>`. Nếu danh sách vốn chỉ cho phép `Integer`, tham chiếu giả dạng `List<Object>` có thể thêm `String` hoặc kiểu khác và làm hỏng hợp đồng generic.

### “Không có `ClassCastException` ngay dòng cast tức là cast đúng”

**Sai.** Type erasure khiến đối số `<String>` trong `List<String>` không thể được kiểm tra như một lớp runtime đầy đủ. Lỗi có thể xuất hiện khi đọc phần tử, khi gán kết quả sang kiểu cụ thể, hoặc sau một lần ghi gây heap pollution.

### “Dùng cùng một `List<?>` hai lần là đủ để suy ra hai `T` bằng nhau”

**Không chắc.** Mỗi lần capture của biểu thức wildcard có thể được xử lý độc lập. Một helper `<T> void helper(List<T>)` dùng **một tham số** giữ được một `T` trong thân phương thức; helper `<T> void helper(List<T>, List<T>)` có thể không nhận được hai biểu thức `List<?>` dù cùng tham chiếu ban đầu.

### “`@SuppressWarnings` làm cast được kiểm tra”

**Sai.** Annotation chỉ ảnh hưởng chẩn đoán của trình biên dịch. Nó không tạo kiểm tra phần tử ở runtime, không khôi phục đối số kiểu đã bị erasure, và không sửa heap pollution.

### “Capture helper bảo đảm chương trình không thể lỗi runtime”

**Sai.** Capture đảm bảo quan hệ kiểu trong thao tác generic, nhưng chỉ số sai vẫn có thể gây `IndexOutOfBoundsException`, danh sách bất biến có thể gây `UnsupportedOperationException`, và dữ liệu đã bị làm ô nhiễm từ raw code khác có thể gây `ClassCastException`.

### “Thêm generic helper là đủ để di chuyển mọi phần tử giữa hai `List<?>`”

**Sai.** Hai wildcard có thể khác kiểu thật. Cần hợp đồng quan hệ giữa source/destination; nếu không có quan hệ đó, việc ép kiểu để di chuyển phần tử chỉ che giấu rủi ro.

## 12. Bài tập tự kiểm tra

Cho:

```java
List<Integer> ids = new ArrayList<>(List.of(1, 2));
List<?> unknown = ids;
```

Hãy đánh giá:

| Biểu thức/hướng xử lý | Kết quả | Giải thích |
| --- | --- | --- |
| `Object x = unknown.get(0)` | Hợp lệ | Đọc wildcard không giới hạn thành `Object` |
| `unknown.add("a")` | Lỗi biên dịch | Không biết kiểu phần tử thật |
| `unknown.set(0, unknown.get(0))` | Lỗi biên dịch | Kiểu capture của thao tác ghi chưa được nối với giá trị đọc |
| `swap(unknown, 0, 1)` với generic helper | Hợp lệ | Capture vào một `List<T>` giúp đọc/ghi cùng `T` |
| `List<String> bad = (List<String>) unknown` | Có thể biên dịch với cảnh báo unchecked | Không chứng minh được phần tử là `String` |
| `String s = bad.get(0)` với danh sách `ids` ban đầu | `ClassCastException` khi chạy | Phần tử thật là `Integer` |
| `@SuppressWarnings("unchecked")` trên cast | Không sửa an toàn kiểu | Chỉ ẩn cảnh báo |
| `checkedCopy(unknown, Integer.class)` | Hợp lệ, trả bản sao `List<Integer>` | Kiểm tra runtime từng phần tử |
| `checkedCopy(unknown, String.class)` | `ClassCastException` ở bước chuyển | Phần tử thực không phải `String` |

Câu về `set/get` là minh họa lỗi compile-time theo cách viết trực tiếp; helper cho phép thao tác tương đương với một `T` dùng nhất quán. Các lời gọi `get(0)` còn yêu cầu danh sách không rỗng khi chạy.

## Ghi nhớ

- **Capture + generic helper** giúp compiler giữ một kiểu ẩn nhất quán khi thao tác trên cùng dữ liệu generic; đây là cách sửa lỗi an toàn khi quan hệ kiểu đã có thật.
- **Unchecked cast** tuyên bố một đối số kiểu mà compiler không thể kiểm tra đầy đủ; giả định sai có thể gây heap pollution và `ClassCastException`.
- **`@SuppressWarnings("unchecked")` chỉ ẩn cảnh báo**, không nâng mức an toàn của cast.
- Capture không làm hai wildcard độc lập thành cùng kiểu; khi cần dữ liệu đi giữa nguồn và đích, hãy diễn đạt quan hệ kiểu bằng `<T>`, `? extends T` và `? super T`.
- Nếu không biết kiểu phần tử nhưng API thật sự cần một danh sách kiểu cụ thể, hãy xác minh phần tử tại runtime hoặc dựa trên hợp đồng dữ liệu được chứng minh, thay vì cast mù.
- **Thông báo `capture of ?` là lời nhắc xem lại quan hệ kiểu**, không phải chỉ dẫn thêm một cast.

Một thiết kế generics tốt làm cho thao tác an toàn được **chứng minh bởi chữ ký và compiler**. Khi phải vượt qua ranh giới hệ kiểu, lập trình viên cần chứng cứ cụ thể hoặc kiểm tra runtime phù hợp, đồng thời giới hạn nơi chứa giả định không được compiler xác nhận.


</details>

- [Quay lại đầu trang](#back-to-top)
