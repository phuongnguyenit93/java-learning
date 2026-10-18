<a id="back-to-top"></a>

# Suy luận kiểu

## Menu
- [Suy luận đối số kiểu từ tham số của lời gọi](#type-inference-001)
- [Kiểu đích và ảnh hưởng của ngữ cảnh gán hoặc truyền đối số](#type-inference-002)
- [Diamond và suy luận khi khởi tạo đối tượng](#type-inference-003)
- [Truyền đối số kiểu tường minh để làm rõ lời gọi](#type-inference-004)
- [Suy luận với nhiều đối số có các kiểu khác nhau](#type-inference-005)
- [Giới hạn trên, giới hạn dưới và kiểu cha chung phù hợp](#type-inference-006)
- [Poly expression, lời gọi lồng nhau và giới hạn lan truyền kiểu đích](#type-inference-007)
- [var với diamond và trường hợp suy luận thành Object](#type-inference-008)
- [Đọc và xử lý thông báo inference variable has incompatible bounds](#type-inference-009)

## <a id="type-inference-001">Suy luận đối số kiểu từ tham số của lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Suy luận đối số kiểu từ tham số của lời gọi trong Java Generics

## 1. Bài toán: vì sao có thể gọi phương thức generic mà không ghi `<T>`?

Trong Java, một phương thức generic khai báo tham số kiểu trong cặp dấu `<...>` trước kiểu trả về:

```java
static <T> T identity(T value) {
    return value;
}
```

Ta có thể chỉ định `T` tường minh:

```java
String result = Example.<String>identity("Java");
```

Nhưng thông thường chỉ cần:

```java
String result = Example.identity("Java");
```

Trình biên dịch nhìn vào **đối số thực tế của lời gọi** (ở đây là biểu thức `"Java"`) và kiểu tham số hình thức (ở đây là `T`) để suy luận cách dùng tham số kiểu `T`. Cơ chế này gọi là *method type inference* hoặc *invocation type inference*.

Trong ví dụ đơn giản, `T` có thể được suy luận phù hợp với `String`. Với lời gọi `identity(42)`, biểu thức `42` thuộc kiểu nguyên thủy `int` và có thể được boxing thành `Integer`, cho phép suy luận đối số kiểu tham chiếu thích hợp.

**Điểm chính:** trình biên dịch suy luận từ **kiểu tĩnh của biểu thức đối số**, các **ràng buộc do chữ ký phương thức đặt ra**, và đôi khi cả **kiểu đích của ngữ cảnh lời gọi**. Nó không nhìn vào phần tử thực tế bên trong danh sách ở runtime để đoán lại `T`.

Bài này tập trung vào các ràng buộc xuất phát từ **tham số và đối số của lời gọi**, đồng thời giải thích những trường hợp kiểu đích có thể tham gia để tránh suy luận sai.

## 2. Ba vai trò cần phân biệt

Xét khai báo:

```java
static <T> void append(List<T> destination, T value) {
    destination.add(value);
}
```

Trong lời gọi:

```java
List<String> names = new ArrayList<>();
append(names, "An");
```

các vai trò là:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Tham số kiểu** (*type parameter*) | `T` trong `<T>` | Biến kiểu được khai báo bởi phương thức |
| **Tham số hình thức** (*formal parameter*) | `List<T> destination`, `T value` | Vị trí và kiểu mà phương thức mong đợi |
| **Đối số thực tế** (*actual argument*) | `names`, `"An"` | Biểu thức được truyền khi gọi phương thức |
| **Đối số kiểu được suy luận** (*inferred type argument*) | Kiểu phù hợp với `String` | Kết quả giải các ràng buộc áp dụng cho `T` |

Không nên gọi `"An"` là “tham số kiểu”. Đây là **giá trị đối số**; từ kiểu tĩnh của nó, trình biên dịch thiết lập điều kiện cho tham số kiểu `T`.

Một tham số kiểu có thể xuất hiện ở nhiều vị trí của chữ ký, do đó việc suy luận **không diễn ra độc lập cho từng vị trí**. Cùng một `T` phải đáp ứng tất cả điều kiện có liên quan.

## 3. Trường hợp cơ bản: một tham số T, một đối số

Ví dụ:

```java
static <T> T echo(T value) {
    return value;
}
```

Lời gọi:

```java
String text = echo("hello");
Integer number = echo(10);
```

Ở lời gọi thứ nhất, đối số là biểu thức có kiểu `String`, vì thế compiler tìm một `T` thích hợp để phương thức nhận được `String`. Trong ngữ cảnh gán cho `String`, lời gọi hợp lệ và giá trị trả về giữ kiểu tương thích với `String`.

Ở lời gọi thứ hai, `10` có kiểu `int`; vì generics không thể sử dụng kiểu nguyên thủy làm đối số kiểu, phép boxing cho phép dùng kiểu wrapper `Integer`.

Những trường hợp khác:

```java
Object object = echo("hello"); // Hợp lệ
Number value = echo(10);       // Hợp lệ
```

Không nên khẳng định rằng `T` **luôn luôn** chính xác bằng kiểu cụ thể nhất của đối số. Ngữ cảnh đích `Object` hoặc `Number` có thể bổ sung ràng buộc, và thuật toán suy luận của Java có thể tìm một kiểu phù hợp rộng hơn khi cần.

Suy luận kiểu là quá trình giải các điều kiện hợp lệ theo ngôn ngữ, không phải phép tra bảng cố định “đối số là X thì T bắt buộc là X”.

## 4. Khi cùng T xuất hiện ở nhiều tham số

Xét:

```java
static <T> T choose(T first, T second, boolean pickFirst) {
    return pickFirst ? first : second;
}
```

Với hai đối số cùng kiểu:

```java
String selected = choose("A", "B", true);
```

Cả `"A"` và `"B"` đều tương thích với `String`, nên việc suy luận rất tự nhiên.

Với hai đối số khác kiểu:

```java
Number selected = choose(1, 2.5, true);
```

`1` được boxing thành `Integer`; `2.5` được boxing thành `Double`. Cả hai đều thuộc họ `Number`, nên một kiểu `T` phù hợp có thể đáp ứng những điều kiện cần thiết trong lời gọi này.

Tuy nhiên:

```java
Integer selected = choose(1, 2.5, true); // LỖI BIÊN DỊCH
```

Không có lý do gì để khẳng định kết quả luôn là `Integer` khi nhánh có thể trả `Double`. Kiểu đích `Integer` tạo thêm điều kiện không tương thích với khả năng nhận cả hai đối số.

**Lưu ý về kiểu chung:** trong các trường hợp phức tạp, Java có thể sử dụng kiểu chung nhỏ nhất hợp lệ theo quan hệ kiểu (*least upper bound*) và các kiểu giao (*intersection types*). Vì thế, không nên tự diễn giải kiểu `T` thành đúng một lớp cha cụ thể khi không có thông tin biên dịch đủ rõ. Quan trọng là biểu thức có tương thích với kiểu đích và các bound hay không.

## 5. `List<T>` tạo ràng buộc chặt hơn một đối số T đơn lẻ

Phương thức:

```java
static <T> void append(List<T> destination, T value) {
    destination.add(value);
}
```

Khi gọi:

```java
List<Integer> numbers = new ArrayList<>();
append(numbers, 7); // Đúng
```

`destination` có kiểu `List<Integer>`, nên vị trí `List<T>` tạo ràng buộc chặt rằng `T` phải phù hợp với tham số kiểu của danh sách này. Đối số `7` có thể boxing thành `Integer`, do đó các điều kiện tương thích.

Ngược lại:

```java
List<Integer> numbers = new ArrayList<>();
Number value = 2.5;
append(numbers, value); // LỖI BIÊN DỊCH
```

Nếu compiler coi `T` là `Integer`, đối số `value` có kiểu tĩnh `Number` chưa chắc là `Integer`. Nếu coi `T` là `Number`, `List<Integer>` lại không chuyển thành `List<Number>`, bởi generic mặc định **bất biến** (*invariant*).

Lỗi của `javac` thường có dạng:

```text
reason: inference variable T has incompatible bounds
```

Kèm các điều kiện như “equality constraints: Integer” và “lower bounds: Number”. Đây là mô tả kỹ thuật của các ràng buộc suy luận mâu thuẫn, không có nghĩa `T` được khai báo bằng cú pháp `<T super Number>`.

Trong khi đó:

```java
List<Number> numbers = new ArrayList<>();
append(numbers, 7);   // Đúng: Integer là Number
append(numbers, 2.5); // Đúng: Double là Number
```

`List<Number>` yêu cầu tham số kiểu `T` tương thích với `Number`; đối số `Integer` hoặc `Double` đều có thể chuyển thành `Number`. Đây là khác biệt rất quan trọng giữa **kiểu đối số giá trị** và **đối số kiểu của một List**.

## 6. Bound của phương thức cũng góp phần vào suy luận

Không phải `T` nào cũng không có giới hạn. Xét:

```java
static <T extends Number> double average(T left, T right) {
    return (left.doubleValue() + right.doubleValue()) / 2;
}
```

Các lời gọi hợp lệ:

```java
double a = average(2, 4);
double b = average(2, 4.5);
```

Với `average(2, 4.5)`, compiler phải tìm một `T` phù hợp với các đối số `Integer`, `Double` và giới hạn trên `Number`. Giá trị được sử dụng như `Number` trong thân phương thức, do đó cả hai đối số đều gọi được `doubleValue()`.

Lời gọi không hợp lệ:

```java
average("2", "4"); // LỖI: String không thỏa bound Number
```

Với một bound phức tạp hơn:

```java
static <T extends Comparable<? super T>> int compare(T a, T b) {
    return a.compareTo(b);
}
```

Compiler phải bảo đảm kiểu `T` còn đáp ứng khả năng so sánh do bound yêu cầu. Không phải hai đối số có một kiểu cha chung bất kỳ là đủ.

Một nguyên tắc quan trọng: **kiểu chung cần thỏa toàn bộ bound** đã khai báo. Nếu tập ràng buộc không có nghiệm hợp lệ, lời gọi không biên dịch dù về runtime các đối tượng đều có thể được lưu dưới `Object`.

## 7. Kiểu tĩnh của đối số quan trọng hơn giá trị runtime

```java
static <T> T identity(T value) {
    return value;
}
```

Ví dụ:

```java
Number n = Integer.valueOf(10);
Number result = identity(n);
```

Biểu thức đối số `n` có **kiểu khai báo `Number`**. Compiler không suy luận kiểu bằng cách kiểm tra đối tượng hiện tại có phải `Integer` hay không. Ở runtime, `n` có thể chứa `Integer`, `Double` hoặc một subclass khác của `Number`.

Do đó:

```java
Integer i = identity(n); // Không biên dịch: Number không bảo đảm Integer
```

Muốn nhận `Integer`, đầu vào cần có kiểu tĩnh phù hợp hoặc phải có bước kiểm tra kiểu rõ ràng:

```java
if (n instanceof Integer i) {
    Integer copy = identity(i); // Đúng: i có kiểu Integer sau pattern matching
}
```

`instanceof` ở đây là phép kiểm tra runtime để thu hẹp kiểu một cách an toàn; nó **không** có nghĩa generics tự suy luận lại kiểu từ đối tượng runtime.

## 8. Đối số không phải nguồn ràng buộc duy nhất

Đối số của lời gọi cung cấp thông tin ban đầu về T, nhưng trong **poly method invocation**, kiểu đích cũng có thể đưa thêm điều kiện. Ví dụ:

```java
static <T> List<T> box(T value) {
    return new ArrayList<>(List.of(value));
}

List<Integer> a = box(42);
List<Number> b = box(42);
List<Object> c = box(42);
```

Ba lần gọi dùng cùng giá trị 42 nhưng mục tiêu `List<T>` khác nhau. Với `b`, kiểu đích buộc kết quả tương thích `List<Number>`; đối số Integer vẫn gán được cho Number. Điều đó **không** có nghĩa `List<Integer>` đã tạo trước có thể gán cho `List<Number>`.

Ngược lại, không phải biểu thức nào cũng nhận target type và compiler không dự đoán ý định runtime. Nếu `T` chỉ xuất hiện ở tham số, hãy theo dõi kiểu tĩnh của từng đối số; nếu còn xuất hiện ở kết quả, cần xét thêm ngữ cảnh trả về và các bounds. Ngữ cảnh gán, truyền đối số và ranh giới poly expression được phân tích sâu ở bài **kiểu đích** tiếp theo.

## 9. Có thể truyền đối số kiểu tường minh khi cần kiểm soát

Cú pháp:

```java
List<Number> numbers = Example.<Number>box(42);
```

`<Number>` ở vị trí này là **đối số kiểu tường minh của lời gọi phương thức generic**, không phải khai báo tham số kiểu mới.

Với phương thức static, viết `Example.<Number>box(42)`. Với lời gọi trực tiếp trong cùng lớp có thể viết `Example.<Number>box(42)` hoặc dạng phù hợp khác tùy vị trí gọi.

Ví dụ giới hạn trên:

```java
static <T extends Number> T same(T value) {
    return value;
}
```

Hợp lệ:

```java
Number n = Example.<Number>same(1);
```

Không hợp lệ:

```java
Example.<String>same("x"); // String không thỏa bound Number
```

Chỉ định `T` tường minh **không bỏ qua quy tắc an toàn kiểu**. Nếu lựa chọn không thỏa bound hoặc đối số truyền vào không chuyển được sang kiểu yêu cầu, lời gọi vẫn bị từ chối.

Thông thường nên để compiler suy luận; chỉ định kiểu tường minh khi nó làm rõ hợp đồng hoặc giúp xử lý một tình huống khó đọc.

## 10. Tương tác với wildcard ở vị trí tham số

Khi nguồn và đích khác vai trò, một phương thức generic có thể cần wildcard:

```java
static <T> void copyAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T item : source) {
        destination.add(item);
    }
}
```

Ví dụ:

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>();
copyAll(integers, numbers);
```

Compiler có thể chọn `T` thỏa `List<Integer>` phù hợp với `List<? extends T>`, đồng thời `List<Number>` phù hợp với `List<? super T>`.

Đây là quy tắc **PECS** (*Producer Extends, Consumer Super*) ứng dụng cùng với suy luận `T`.

Ngược lại, nếu khai báo quá chặt:

```java
static <T> void copyStrict(List<T> source, List<T> destination) {
    destination.addAll(source);
}
```

Lời gọi `copyStrict(List<Integer>, List<Number>)` không hợp lệ, do `List<Integer>` và `List<Number>` không có cùng tham số kiểu. Suy luận không biến generics thành hiệp biến; compiler phải tôn trọng quan hệ bất biến của `List<T>`.

### Trường hợp wildcard capture

```java
static <T> T first(List<T> values) {
    return values.get(0);
}

List<? extends Number> source = List.of(3, 5);
Number n = first(source);
```

Compiler có thể *capture* wildcard chưa biết trong `source` để suy luận một `T` phù hợp tại lời gọi `first(source)`. Phần tử trả về vẫn dùng an toàn dưới `Number`.

Không thể từ đây suy ra có thể thêm `Number` bất kỳ vào `source`; wildcard chỉ bảo đảm **đọc** với giới hạn trên.

## 11. Boxing, kiểu nguyên thủy và varargs

Java generic type arguments phải là **kiểu tham chiếu**:

```java
static <T> T echo(T value) {
    return value;
}

Integer x = echo(1);  // int được boxing thành Integer
Double y = echo(2.5); // double được boxing thành Double
```

Không được viết:

```java
// Không hợp lệ:
List<int> numbers = new ArrayList<>();
// Tương tự, <int>echo(1) không phải đối số kiểu generic hợp lệ.
```

Cũng cần phân biệt phương thức varargs generic:

```java
@SafeVarargs
static <T> List<T> asList(T... values) {
    return List.of(values);
}
```

Ví dụ phương thức này là `static` và có thể được đánh dấu `@SafeVarargs` khi thân phương thức thực sự không làm hỏng kiểu mảng varargs; nhưng annotation là **cam kết của người viết**, không phải cơ chế làm varargs generic an toàn tự động. Các phương thức varargs với `T...` có thể phát sinh cảnh báo về mảng có kiểu phần tử không reifiable. Khi chỉ cần tạo danh sách, ưu tiên `List.of(...)` hoặc các API chuẩn.

Suy luận `T` trong lời gọi varargs phải xét kiểu từng đối số, kiểu mảng được truyền và các quy tắc áp dụng fixed-arity/variable-arity. Đừng suy ra từ sự tương đồng hình thức rằng một mảng `T[]` được tạo tùy ý ở runtime.

## 12. Chương trình Java 17+ đầy đủ và kết quả chính xác

Tạo file `InferenceDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class InferenceDemo {
    static <T> List<T> box(T value) {
        return new ArrayList<>(List.of(value));
    }

    static <T> void append(List<T> destination, T value) {
        destination.add(value);
    }

    static <T> T pickFirst(List<T> list) {
        return list.get(0);
    }

    static <T> T choose(T left, T right, boolean pickLeft) {
        return pickLeft ? left : right;
    }

    static <T extends Number> double average(T a, T b) {
        return (a.doubleValue() + b.doubleValue()) / 2;
    }

    static <T> void copyAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<String> words = box("Java");
        System.out.println("Words: " + words);

        List<Number> fromTarget = box(42);
        System.out.println("Target: " + fromTarget);

        List<Number> numbers = new ArrayList<>();
        append(numbers, 7);
        append(numbers, 2.5);
        System.out.println("Appended: " + numbers);

        List<? extends Number> source = List.of(3, 5);
        Number first = pickFirst(source);
        System.out.println("First: " + first);

        Number chosen = choose(1, 2.5, true);
        System.out.println("Chosen: " + chosen);

        System.out.println("Average: " + average(2, 4.5));

        copyAll(List.of(10, 20), numbers);
        System.out.println("Copied: " + numbers);

        List<Number> explicit = InferenceDemo.<Number>box(99);
        System.out.println("Explicit: " + explicit);
    }
}
```

Biên dịch và chạy tại thư mục chứa file:

```bash
javac -encoding UTF-8 InferenceDemo.java
java InferenceDemo
```

Kết quả:

```text
Words: [Java]
Target: [42]
Appended: [7, 2.5]
First: 3
Chosen: 1
Average: 3.25
Copied: [7, 2.5, 10, 20]
Explicit: [99]
```

**Phân tích cụ thể:**

1. `box("Java")` tạo danh sách chứa `String` và được gán cho `List<String>`.
2. `box(42)` đặt vào `List<Number>` hợp lệ vì compiler kết hợp ràng buộc từ đối số với kiểu đích; nó không biến một `List<Integer>` đã tồn tại thành `List<Number>`.
3. `append(numbers, 7)` và `append(numbers, 2.5)` đều dùng được cùng danh sách `List<Number>` vì mỗi lời gọi phải tương thích với `T` của chữ ký.
4. `pickFirst(source)` thực hiện wildcard capture để đọc được dưới kiểu `Number`.
5. `choose(1, 2.5, true)` chấp nhận hai kiểu số khác nhau và kết quả tương thích với `Number`.
6. `average(2, 4.5)` đáp ứng `T extends Number`; giá trị `3.25` là kết quả trung bình của `2.0` và `4.5`.
7. `copyAll` tận dụng PECS: nguồn cung cấp `Integer` và đích có thể nhận `Number`.
8. `InferenceDemo.<Number>box(99)` cho thấy cú pháp chỉ định đối số kiểu tường minh.

## 13. Các thông báo lỗi suy luận kiểu thường gặp

### 13.1. `inference variable T has incompatible bounds`

```java
static <T> void append(List<T> target, T value) {
    target.add(value);
}

List<Integer> integers = new ArrayList<>();
Number n = 1.5;
append(integers, n); // Không biên dịch
```

Vị trí `List<T>` buộc `T` phù hợp với `Integer`, còn đối số `n` có kiểu tĩnh `Number`. Do bất biến của generic, compiler không tìm được cách chọn `T` thỏa cả hai.

**Khắc phục:** truyền giá trị thật sự thuộc `Integer`, thay kiểu đích thành `List<Number>` nếu cần chứa nhiều kiểu số, hoặc thiết kế lại API bằng wildcard khi đúng hợp đồng nghiệp vụ. Không ép kiểu mù sang `Integer`.

### 13.2. `inferred type does not conform to upper bound`

```java
static <T extends Number> T same(T value) {
    return value;
}

String s = same("hello"); // Không biên dịch
```

`String` không phải `Number`. Đây là vi phạm bound, không phải lỗi do thiếu thông tin về `T`.

### 13.3. Hiểu sai giá trị trả về

```java
static <T> T choose(T a, T b, boolean left) {
    return left ? a : b;
}

Integer result = choose(1, 2.5, true); // Không biên dịch
```

Cần chọn kiểu đích có thể đại diện cho cả hai nhánh, ví dụ `Number`. Việc ở runtime nhánh `true` trả một `Integer` không giúp lời gọi trả về kiểu tĩnh `Integer` khi nhánh còn lại có thể là `Double`.

### 13.4. Dùng raw type hoặc unchecked cast để vượt suy luận

```java
List<Integer> values = new ArrayList<>();
List raw = values;
raw.add("bad");
```

Đây không phải cách “sửa lỗi inference”. Raw type làm mất kiểm tra generic, có thể tạo *heap pollution* rồi dẫn đến `ClassCastException` ở nơi đọc.

Không nên gắn `@SuppressWarnings("unchecked")` chỉ để ẩn cảnh báo của một lệnh ép kiểu thiếu chứng cứ.

### 13.5. Tưởng `var` tự chọn kiểu theo cách mình muốn

```java
var values = List.of(1, 2, 3);
```

`var` suy luận kiểu của **biến cục bộ** từ kiểu kết quả biểu thức khởi tạo. Đây là một bước khác với suy luận đối số kiểu của lời gọi `List.of(...)`. Không nên giả định `var` luôn tạo `List<Number>` hoặc luôn chọn một kiểu người lập trình mong muốn cho mọi tập hợp đối số hỗn hợp.

Khi API cần một hợp đồng cụ thể, khai báo kiểu đích rõ ràng:

```java
List<Number> values = List.of(1, 2.5);
```

## 14. Bài tập: suy luận từ những đối số nào?

Các đoạn sau **độc lập**, giả sử đã import `java.util.*;`. Phương thức `static` phải được đặt trong một lớp hợp lệ. Hãy nhận định biên dịch được hay không.

**A — Cùng T qua List và giá trị**

```java
static <T> void add(List<T> list, T value) {
    list.add(value);
}
List<String> texts = new ArrayList<>();
add(texts, "Java");
```

**B — Kiểu tĩnh Number vào List<Integer>**

```java
List<Integer> integers = new ArrayList<>();
Number n = 5;
add(integers, n);
```

**C — Kiểu tĩnh Integer vào List<Number>**

```java
List<Number> numbers = new ArrayList<>();
add(numbers, Integer.valueOf(5));
```

**D — Hai kiểu số và kết quả Number**

```java
static <T> T pick(T x, T y) {
    return x;
}
Number n = pick(10, 3.5);
```

**E — Bound Number với chuỗi**

```java
static <T extends Number> T unchanged(T x) {
    return x;
}
String s = unchanged("10");
```

**F — Kiểu đích của List<T>**

```java
static <T> List<T> singleton(T value) {
    return new ArrayList<>(List.of(value));
}
List<Number> numbers = singleton(10);
```

**G — Wildcard source và type inference**

```java
static <T> T first(List<T> values) {
    return values.get(0);
}
List<? extends Number> source = List.of(1, 2);
Number result = first(source);
```

**H — Hai List<T> bất biến**

```java
static <T> void merge(List<T> source, List<T> target) {
    target.addAll(source);
}
List<Integer> ints = List.of(1);
List<Number> nums = new ArrayList<>();
merge(ints, nums);
```

**Đáp án giải thích:**

- **A:** Hợp lệ, các ràng buộc phù hợp với `String`.
- **B:** Không hợp lệ: kiểu tĩnh `Number` không bảo đảm `Integer`; `List<Integer>` không thể coi là `List<Number>`.
- **C:** Hợp lệ: `Integer` chuyển được thành `Number`, và đích là `List<Number>`.
- **D:** Hợp lệ: có thể dùng kiểu chung phù hợp với `Integer`, `Double` và kiểu đích `Number`.
- **E:** Không hợp lệ: `String` không thỏa `T extends Number`.
- **F:** Hợp lệ: kiểu đích `List<Number>` tham gia cùng ràng buộc đầu vào.
- **G:** Hợp lệ: wildcard capture cho phép phương thức generic đọc một phần tử dưới dạng `Number`.
- **H:** Không hợp lệ: cùng một `List<T>` không thể lần lượt là `List<Integer>` và `List<Number>`. Dùng `List<? extends T>` ở nguồn, `List<? super T>` ở đích nếu muốn quan hệ đó.

## 15. Quy trình đọc chữ ký khi compiler báo không suy luận được T

Hãy kiểm tra theo thứ tự:

1. **Tìm chỗ khai báo `<T>`** và các giới hạn, ví dụ `<T extends Number>`.
2. **Ghi lại mọi chỗ xuất hiện `T`** trong tham số hình thức: `T`, `List<T>`, `List<? extends T>`, `List<? super T>`.
3. **Xác định kiểu tĩnh của đối số thực tế**, kể cả boxing, chuyển kiểu tham chiếu và wildcard; không dựa vào giá trị runtime.
4. **Xem có kiểu đích không**, ví dụ gán kết quả vào `List<Number>` hay `Integer`, và ngữ cảnh đó có tham gia suy luận không.
5. **Kiểm tra các ràng buộc có mâu thuẫn không**. `List<Integer>` cần đúng tham số kiểu `Integer`, trong khi đối số giá trị kiểu `Number` có thể quá rộng.
6. **Kiểm tra bound**, ví dụ `T extends Number`: một nghiệm suy luận không thỏa bound vẫn bị từ chối.
7. Nếu API đang mô tả **nguồn/đích khác vai trò**, cân nhắc cặp `? extends T` / `? super T`, thay vì ép mọi tham số có cùng `List<T>`.
8. Chỉ viết đối số kiểu tường minh khi nó thể hiện lựa chọn hợp lệ, không sử dụng cast unchecked để né lỗi.

### Ghi nhớ

**Suy luận đối số kiểu từ tham số của lời gọi** là việc compiler kết hợp kiểu tĩnh của các đối số với cấu trúc tham số hình thức để giải một hệ ràng buộc cho `T`. Cùng một `T` phải thỏa tất cả vị trí sử dụng và bound; trong những ngữ cảnh phù hợp, kiểu đích cũng có thể bổ sung điều kiện. Cơ chế này giúp gọi các phương thức generic ngắn gọn, đồng thời giữ an toàn kiểu mà không cần ép kiểu hoặc raw type.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-002">Kiểu đích và ảnh hưởng của ngữ cảnh gán hoặc truyền đối số</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu đích và ảnh hưởng của ngữ cảnh gán hoặc truyền đối số trong suy luận kiểu Java

## 1. Vấn đề cần giải quyết

Java Generics cho phép trình biên dịch **suy luận tham số kiểu** của phương thức generic, constructor dùng diamond `<>` và nhiều biểu thức khác từ thông tin sẵn có. Một nguồn thông tin đặc biệt quan trọng là **kiểu đích** (*target type*): kiểu mà kết quả của biểu thức cần tương thích tại vị trí sử dụng.

Hai dòng mã trông tương tự nhưng có thể dẫn đến suy luận khác nhau:

```java
List<Number> numbers = List.of(1, 2);
var integers = List.of(1, 2);
```

Ở dòng đầu, biểu thức `List.of(1, 2)` được dùng trong **ngữ cảnh gán** có kiểu đích `List<Number>`. Trình biên dịch có thể chọn tham số kiểu `E = Number`, vì `Integer` có thể được chuyển thành `Number`.

Ở dòng thứ hai, `var` không cung cấp một kiểu đích cụ thể. Các phần tử `1`, `2` được boxing thành `Integer`; kiểu của biến `integers` được suy luận là `List<Integer>`.

Sự khác biệt này ảnh hưởng đến khả năng truyền kết quả vào phương thức khác, giữ thông tin kiểu, chọn wildcard và tránh phải ép kiểu.

Bài học này tập trung vào **kiểu đích và ảnh hưởng của ngữ cảnh gán hoặc truyền đối số**, trong module Java Core Generics, với các ví dụ phù hợp Java 17+.

## 2. Mục tiêu học tập

Sau bài học, bạn có thể:

1. Phân biệt **kiểu đích**, **kiểu của biểu thức**, **kiểu trả về khai báo** và **kiểu phần tử runtime**.
2. Xác định ngữ cảnh gán, truyền đối số, `return`, lambda và method reference cung cấp thông tin kiểu như thế nào.
3. Giải thích vì sao một lời gọi generic như `List.of(1)` có thể được dùng dưới `List<Number>`, trong khi biến đã có kiểu `List<Integer>` không tự động gán được cho `List<Number>`.
4. Dự đoán ảnh hưởng của `var`, diamond `<>`, type witness `<T>` và target typing đối với chương trình.
5. Biết giới hạn của suy luận: target type không phá vỡ tính bất biến của Generics, không tự đổi kiểu đối tượng đã khai báo và không thay thế kiểm tra kiểu.
6. Viết và kiểm tra được các ví dụ Java 17 có đầu ra xác định.

## 3. Khái niệm: biểu thức có kiểu đích là gì?

Một biểu thức có **kiểu đích** khi Java dùng yêu cầu về kiểu ở vị trí đặt biểu thức để kiểm tra hoặc suy luận kiểu của chính biểu thức đó.

Ví dụ:

```java
List<Number> values = List.of(1, 2);
```

Ở đây:

- Bên trái có kiểu `List<Number>`: đó là kiểu đích.
- Bên phải gọi phương thức generic `List.of(E... elements)`: cần suy luận `E`.
- Hai đối số nguyên được boxing thành `Integer`.
- Suy luận có thể chọn `E = Number`, sao cho cả các giá trị `Integer` đều chấp nhận được và biểu thức tương thích với `List<Number>`.

Một biểu thức có thể phụ thuộc kiểu đích được gọi là **poly expression** khi đáp ứng các điều kiện của Java Language Specification. Ngược lại, **standalone expression** tự xác định kiểu mà không phụ thuộc vào target type theo cùng cơ chế.

Không phải mọi biểu thức đều là poly expression. Cũng không phải cứ bên trái có một kiểu là Java sẽ tự thay đổi kiểu của mọi biểu thức bên phải. **Chính loại biểu thức và quy tắc ngữ cảnh quyết định** thông tin kiểu đích có được dùng hay không.

### 3.1. Ba câu hỏi cần phân biệt

Với đoạn:

```java
List<Number> numbers = List.of(1, 2);
```

Hỏi: "Biểu thức tạo ra loại đối tượng nào ở runtime?" — Một đối tượng implementation của `List` chứa các tham chiếu đến phần tử `Integer`.

Hỏi: "Biến `numbers` có kiểu tĩnh gì?" — `List<Number>`.

Hỏi: "Thông tin `Number` từ đâu?" — Từ kiểu đích của phép gán, được sử dụng trong suy luận lời gọi generic.

Type erasure có nghĩa JVM không mang một `List<Number>` tách biệt hoàn toàn với `List<Integer>` chỉ vì type argument khác nhau. Nhưng **kiểm tra kiểu tĩnh** tại lúc biên dịch vẫn rất quan trọng.

## 4. Ngữ cảnh gán: kiểu bên trái có thể tham gia suy luận

Xét phương thức generic độc lập về mặt ý tưởng:

```java
static <T> List<T> one(T value) {
    return List.of(value);
}
```

Các phép gán sau đều hợp lệ:

```java
List<Integer> a = one(1);
List<Number> b = one(1);
List<Object> c = one(1);
```

Trình biên dịch có thể chọn `T` khác nhau tùy yêu cầu của biểu thức đích. Trong các phép gán đó, lời gọi `one(1)` không bị buộc phải có kiểu `List<Integer>` từ trước khi xét ngữ cảnh gán.

**Điểm quan trọng:** `one(1)` là một *lời gọi generic method* có thể tham gia suy luận; nó khác với **một biến** đã được khai báo là `List<Integer>`.

### 4.1. Gán từ một biểu thức generic và gán từ biến đã có kiểu

```java
List<Number> direct = List.of(1, 2); // Hợp lệ.

List<Integer> integers = List.of(1, 2);
// List<Number> invalid = integers;   // LỖI BIÊN DỊCH.
```

Tại sao?

- `List.of(1, 2)` là lời gọi generic; kiểu đích có thể tham gia suy luận tham số kiểu của lần gọi đó.
- `integers` là biểu thức biến với kiểu tĩnh **đã được xác lập** là `List<Integer>`; phép gán không suy luận lại type argument của biến.
- `List<Integer>` không phải subtype của `List<Number>`, bởi Java Generics **bất biến** (*invariant*) theo tham số kiểu.

Nếu có thể gán `integers` cho `List<Number>`, người dùng tham chiếu mới có thể thêm một `Double` vào danh sách `Integer`, gây vi phạm an toàn kiểu.

### 4.2. Cách sửa khi đã có một `List<Integer>`

Nếu chỉ cần **đọc** như `Number`, dùng wildcard:

```java
List<Integer> integers = List.of(1, 2);
List<? extends Number> view = integers;
Number first = view.get(0);
```

Nếu cần **danh sách `List<Number>` mới** để thêm nhiều subtype của `Number`, sao chép:

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>(integers);
numbers.add(2.5);
```

`new ArrayList<>(integers)` là tạo danh sách mới có type argument suy luận theo ngữ cảnh; đây là **sao chép nông**, không biến danh sách `integers` gốc thành `List<Number>`.

## 5. Ngữ cảnh truyền đối số: kiểu tham số của phương thức là đầu mối suy luận

Kiểu đích không chỉ xuất hiện bên trái dấu `=`. Khi truyền một biểu thức vào lời gọi phương thức, **tham số hình thức** cũng có thể tạo ra ngữ cảnh kiểu cho biểu thức đối số.

Ví dụ:

```java
static void acceptNumbers(List<Number> values) {
    System.out.println(values);
}

static <T> List<T> one(T value) {
    return List.of(value);
}
```

Trong lời gọi:

```java
acceptNumbers(one(10)); // HỢP LỆ.
```

Trình biên dịch biết `acceptNumbers` cần `List<Number>`. Lời gọi generic `one(10)` có thể được kiểm tra/suy luận sao cho `T = Number`, dù đối số cụ thể `10` được boxing thành `Integer`.

Đối chiếu:

```java
List<Integer> existing = one(10);
// acceptNumbers(existing); // LỖI: List<Integer> khác List<Number>.
```

Thông tin kiểu đích từ tham số phương thức giúp **lời gọi generic lồng bên trong**, nhưng không tự nới kiểu của một biến đã được định kiểu.

### 5.1. Phân biệt tham số generic và tham số cụ thể

```java
static void receiveExact(List<Number> values) { }

static void receiveProducer(List<? extends Number> values) { }
```

Với `List<Integer> integers`:

```java
List<Integer> integers = List.of(1, 2);

// receiveExact(integers);    // Sai vì generic invariant.
receiveProducer(integers);    // Đúng nhờ wildcard extends.
receiveExact(List.of(1, 2)); // Đúng nhờ suy luận với target type.
```

Ba dòng trên minh họa **hai cơ chế khác nhau**:

1. `receiveProducer(integers)`: chấp nhận tham số đã có kiểu `List<Integer>` theo quy tắc wildcard.
2. `receiveExact(List.of(1, 2))`: lời gọi `List.of` có thể được suy luận theo ngữ cảnh truyền đối số để tạo biểu thức tương thích `List<Number>`.

Không nên trộn hai cơ chế và nói rằng Java có covariance tự động cho `List<T>`.

### 5.2. Suy luận qua nhiều tham số

Xét:

```java
static <T> void appendAll(
        List<? super T> destination,
        List<? extends T> source) {
    destination.addAll(source);
}
```

Lời gọi sau hợp lệ:

```java
List<Number> numbers = new ArrayList<>();
List<Integer> integers = List.of(1, 2);
appendAll(numbers, integers);
```

Trình biên dịch giải đồng thời các ràng buộc từ hai đối số:

- Kiểu của `source` phải tương thích `List<? extends T>`.
- Kiểu của `destination` phải tương thích `List<? super T>`.

Ví dụ `T = Integer` là lựa chọn phù hợp: nguồn có `Integer`, đích `Number` có thể nhận `Integer`.

Đây là mẫu sử dụng PECS. Suy luận generic có thể phối hợp nhiều ràng buộc; không nhất thiết từng đối số phải có đúng kiểu `List<T>` ban đầu.

## 6. Biểu thức `return`: chữ ký trả về là ngữ cảnh

Trong phương thức:

```java
static List<Number> provideNumbers() {
    return List.of(1, 2.5);
}
```

`List<Number>` trong khai báo phương thức là kiểu đích của biểu thức `return`. Lời gọi `List.of` có thể suy luận `E = Number` để chấp nhận cả `Integer` và `Double`.

Tương tự:

```java
static Optional<CharSequence> provideText() {
    return Optional.of("Java");
}
```

`Optional.of(T value)` là một lời gọi generic, còn `Optional<CharSequence>` là kiểu đích trong ngữ cảnh `return`.

Đừng nhầm với:

```java
static List<Number> invalid() {
    List<Integer> local = List.of(1, 2);
    // return local; // Lỗi: local có kiểu List<Integer> cố định.
    return new ArrayList<>(local); // Hợp lệ: tạo List<Number> mới.
}
```

Kiểu trả về có thể hướng dẫn suy luận **trong biểu thức return**, nhưng không thay đổi kiểu tĩnh của `local`.

## 7. `var` làm thay đổi nguồn cung cấp kiểu đích

`var` suy luận kiểu **biến cục bộ** từ initializer, chứ không truyền một contract tường minh như `List<Number>` xuống lời gọi bên phải:

```java
List<Number> widened = List.of(1, 2); // E có thể là Number.
var narrowed = List.of(1, 2);         // List<Integer>.
```

Kiểu của `narrowed` được cố định ngay khi khai báo. Nó không trở thành `List<Number>` khi được dùng về sau. `var` vẫn có kiểu tĩnh, không phải kiểu động.

Một lời gọi không có đối số dữ liệu còn thể hiện khác biệt rõ hơn:

```java
List<String> texts = Collections.emptyList(); // T là String.
var unknown = Collections.emptyList();        // List<Object>.
```

Trong trường hợp `var`, không có kiểu đích String, nên suy luận mặc định có thể dùng Object. Khi cần contract phần tử, hãy khai báo rõ biến hoặc chọn type witness tại nơi tạo dữ liệu.

## 8. Diamond `<>` cũng chịu ảnh hưởng của ngữ cảnh

```java
List<Number> numbers = new ArrayList<>(); // ArrayList<Number>.
var objects = new ArrayList<>();          // ArrayList<Object>.
var strings = new ArrayList<>(List.of("A")); // ArrayList<String>.
```

Diamond không đồng nghĩa Object: đối số constructor như `List.of("A")` có thể ràng buộc tham số kiểu ngay cả khi dùng `var`. Phân biệt diamond `<>` (yêu cầu suy luận lúc tạo đối tượng) với wildcard `<?>` (đối số kiểu chưa biết trong một kiểu được sử dụng); `new ArrayList<?>()` không hợp lệ. Bài **Diamond** trình bày thêm các constructor và generic class lồng nhau; bài **var với diamond** đi sâu vào trường hợp thiếu thông tin khiến kiểu thành Object.

## 9. Lời gọi generic method: target type tham gia cùng đối số

Định nghĩa:

```java
static <T> T choose(T left, T right) {
    return left;
}
```

Một lời gọi như:

```java
Number n = choose(1, 2.5);
```

có các ràng buộc:

- `Integer` (sau boxing) phải được chấp nhận làm `T`.
- `Double` phải được chấp nhận làm `T`.
- Kết quả trả về `T` phải tương thích với kiểu đích `Number`.

Trình biên dịch có thể suy luận một `T` phù hợp các điều kiện, và `n` có kiểu khai báo là `Number`.

Ngược lại:

```java
// Integer i = choose(1, 2.5); // Lỗi: Double không thể trở thành Integer.
```

Kiểu đích **không thể ép** một lời gọi nhận đối số không tương thích. Suy luận phải đồng thời thỏa các ràng buộc do tham số, cận generic và target type đưa ra.

### 9.1. Type witness: chỉ định tham số kiểu tường minh

Khi muốn làm rõ ý định hoặc xử lý trường hợp suy luận khó đọc, có thể viết type witness:

```java
List<Number> numbers = List.<Number>of(1, 2.5);
Number value = TargetTypingDemo.<Number>choose(1, 2.5);
```

Ở đây `TargetTypingDemo` là tên class chứa phương thức `static choose`, được định nghĩa đầy đủ trong chương trình phần 13.

Type witness `<Number>` yêu cầu type argument của lời gọi generic là `Number`. Nó **khác** với diamond `<>` ở constructor: một bên chỉ định kiểu, một bên yêu cầu suy luận.

Đừng dùng type witness để che đi một API khai báo kiểu sai. Nếu một phương thức vốn trả `List<Integer>` cố định, viết type witness ở caller không tự biến nó thành `List<Number>`.

## 10. Lambda: target type xác định functional interface

Lambda **cần ngữ cảnh kiểu** (target type là một functional interface), ví dụ:

```java
Function<String, Integer> length = text -> text.length();
```

Target type `Function<String, Integer>` cung cấp:

- Kiểu của tham số lambda `text`: `String`.
- Kiểu kết quả mà thân lambda phải tương thích: `Integer`.
- Hợp đồng phương thức chức năng `apply`.

Vì thế:

```java
int n = length.apply("Java"); // 4
```

Trái lại:

```java
// var lambda = value -> value; // LỖI BIÊN DỊCH
```

Một lambda tự thân không xác định functional interface mà nó triển khai; `var` ở khai báo biến không cung cấp target functional interface tương ứng.

### 10.1. Lambda trong ngữ cảnh truyền đối số

```java
static void useText(Function<String, Integer> fn) {
    System.out.println(fn.apply("Java"));
}

useText(text -> text.length()); // Hợp lệ.
```

Ở lời gọi, kiểu tham số `Function<String, Integer>` tạo target type cho lambda. Compiler hiểu `text` có kiểu `String` dù không khai báo kiểu ngay trong lambda.

Đây là một minh họa quan trọng rằng **ngữ cảnh truyền đối số có thể ảnh hưởng không chỉ generic method mà cả kiểu của lambda**.

### 10.2. Lambda là đầu vào cho generic method

```java
static <T, R> R transform(T value, Function<? super T, ? extends R> fn) {
    return fn.apply(value);
}

Integer size = transform("Java", text -> text.length());
```

Compiler dùng kiểu `String` của đối số `"Java"`, target `Integer` của phép gán và chữ ký `Function<? super T, ? extends R>` để tìm ràng buộc phù hợp cho `T`, `R`.

Nếu cần hiểu một lỗi inference, hãy xem **tất cả vị trí đóng góp ràng buộc**: kiểu của đối số đầu vào, tham số hàm chức năng, biểu thức kết quả lambda, và kiểu đích của toàn lời gọi.

## 11. Method reference cũng nhận kiểu từ ngữ cảnh

Ví dụ:

```java
Function<String, Integer> length = String::length;
Supplier<List<String>> emptyStrings = Collections::emptyList;
```

`String::length` phù hợp với functional interface nhận `String` và trả `Integer` (giá trị `int` được boxing khi cần). `Collections::emptyList` là method reference đến generic method; functional interface `Supplier<List<String>>` cung cấp ràng buộc cho việc suy luận `T = String`.

```java
List<String> result = emptyStrings.get();
```

Nhưng:

```java
// var ref = String::length; // LỖI: method reference cần target type.
```

Không có một functional interface cụ thể, compiler không xác định được kiểu method reference chỉ từ cú pháp `String::length`.

## 12. Biểu thức điều kiện `?:` và kiểu đích

Trong ngữ cảnh gán, **reference conditional expression** có thể là poly expression. Target type có thể được dùng khi kiểm tra từng nhánh:

```java
boolean enabled = true;

List<Number> result = enabled
        ? List.of(1, 2)
        : List.of(2.5, 3.5);
```

Ở đây, cả hai nhánh `List.of(...)` là các lời gọi generic có thể tương thích với target `List<Number>`. Dù một nhánh chứa `Integer`, nhánh kia chứa `Double`, phép gán vẫn hợp lệ.

Đừng suy luận rằng **mọi** biểu thức `?:` đều hoạt động theo target type. Quy tắc Java phân biệt conditional expression với các loại boolean/numeric/reference, và chỉ một số loại reference conditional expression ở các ngữ cảnh thích hợp mới là poly expression.

### 12.1. Khi sử dụng `var` với conditional expression

```java
var result = enabled
        ? List.of(1, 2)
        : List.of(2.5, 3.5);
```

Biến `result` không có target type `List<Number>` tường minh, nên trình biên dịch xác định kiểu từ các nhánh theo những quy tắc type combination (*least upper bound*) tương ứng. Không nên khẳng định kiểu cuối luôn là `List<Number>`; có thể là một kiểu tổng hợp chứa wildcard/intersection phức tạp hơn.

Nếu API cần `List<Number>`, khai báo rõ kiểu đích để giữ hợp đồng dễ hiểu:

```java
List<Number> values = enabled
        ? List.of(1, 2)
        : List.of(2.5, 3.5);
```

## 13. Chương trình Java 17+ hoàn chỉnh, chạy được

Lưu đoạn sau thành **`TargetTypingDemo.java`**. Mã cố ý trình bày cả ngữ cảnh gán, truyền đối số, `return`, diamond, lambda, method reference và `var`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class TargetTypingDemo {
    static <T> List<T> one(T value) {
        return List.of(value);
    }

    static <T> T choose(T left, T right) {
        return left;
    }

    static void acceptNumbers(List<Number> values) {
        System.out.println("argument=" + values);
    }

    static List<Number> returnedNumbers() {
        return List.of(3, 4.5);
    }

    static void useText(Function<String, Integer> lengthFn) {
        System.out.println("lambda=" + lengthFn.apply("Java"));
    }

    public static void main(String[] args) {
        List<Number> assigned = one(1);
        System.out.println("assignment=" + assigned);

        acceptNumbers(one(2));
        System.out.println("return=" + returnedNumbers());

        Number selected = choose(5, 6.5);
        System.out.println("choose=" + selected);

        List<Number> diamond = new ArrayList<>();
        diamond.add(7);
        diamond.add(2.5);
        System.out.println("diamond=" + diamond);

        var integers = List.of(8, 9);
        List<Number> copied = new ArrayList<>(integers);
        copied.add(10.5);
        System.out.println("var=" + integers);
        System.out.println("copy=" + copied);

        useText(text -> text.length());

        Function<String, Integer> length = String::length;
        System.out.println("reference=" + length.apply("abcd"));

        Supplier<List<String>> empty = Collections::emptyList;
        System.out.println("empty=" + empty.get().size());

        boolean flag = true;
        List<Number> conditional = flag
                ? List.of(11)
                : List.of(12.5);
        System.out.println("conditional=" + conditional);
    }
}
```

Biên dịch và chạy:

```bash
javac TargetTypingDemo.java
java TargetTypingDemo
```

Stdout chính xác:

```text
assignment=[1]
argument=[2]
return=[3, 4.5]
choose=5
diamond=[7, 2.5]
var=[8, 9]
copy=[8, 9, 10.5]
lambda=4
reference=4
empty=0
conditional=[11]
```

Diễn giải theo thứ tự:

1. `assigned` có kiểu đích `List<Number>`, giúp lời gọi `one(1)` suy luận tham số kiểu tương thích.
2. `acceptNumbers(one(2))` dùng ngữ cảnh truyền đối số `List<Number>` cho lời gọi `one(2)`.
3. `returnedNumbers()` có `return List.of(...)` được kiểm tra với `List<Number>`.
4. `choose(5, 6.5)` kết hợp ràng buộc từ `Integer`, `Double` và target `Number`; phương thức trả về đối số đầu tiên nên in `5`.
5. `new ArrayList<>()` nhận kiểu phần tử `Number` từ khai báo `List<Number> diamond`.
6. `var integers` có kiểu `List<Integer>` từ initializer `List.of(8, 9)`; bản sao `List<Number>` là collection khác và nhận thêm `Double`.
7. Lambda `text -> text.length()` được định kiểu theo tham số `Function<String, Integer>`.
8. Method reference `String::length` phù hợp functional interface `Function<String, Integer>`.
9. `Collections::emptyList` nhận target `Supplier<List<String>>`.
10. Biểu thức điều kiện gồm hai lời gọi `List.of` được kiểm tra với target `List<Number>`; `flag = true` nên kết quả là `[11]`.

**Lưu ý về mutability:** `List.of` trả về danh sách không cho phép sửa. Việc `List.of` được suy luận thành `List<Number>` không đồng nghĩa `add` sẽ chạy thành công; với `List.of`, lệnh sửa vẫn có thể ném `UnsupportedOperationException`. Bản sao `new ArrayList<>(...)` trong ví dụ mới là mutable.

## 14. Vì sao compiler không luôn suy luận đúng “ý định của ta”?

### 14.1. Kiểu đích không phá invariance

```java
List<Integer> source = List.of(1);
// List<Number> target = source; // Lỗi.
```

Target `List<Number>` không thể làm `source` đổi từ `List<Integer>` sang `List<Number>`. Đây là phép gán giữa hai kiểu đã xác định, không phải lời gọi generic cần suy luận.

### 14.2. Kiểu đích không “chữa” đối số không tương thích

```java
static <T extends Number> T identity(T value) {
    return value;
}

// Number invalid = identity("text"); // Lỗi vì String không phải Number.
```

Dù target `Number` có vẻ rộng, cận `T extends Number` và đối số `String` mâu thuẫn. Không có lựa chọn `T` hợp lệ.

### 14.3. Không phải mọi generic API đều trả về kiểu `T` cần suy luận

```java
static List<Integer> fixed() {
    return List.of(1, 2);
}

// List<Number> result = fixed(); // Lỗi.
```

Phương thức `fixed()` khai báo trả về `List<Integer>` cụ thể. Compiler không được thay chữ ký thành `List<Number>` chỉ vì caller mong điều đó.

### 14.4. Kiểu đích không đổi runtime subtype

```java
List<Number> values = List.of(1, 2);
Number n = values.get(0); // Nắm tham chiếu đến đối tượng Integer.
```

Từ `Number` ở biến `n` không suy ra đối tượng đã biến thành một instance khác. **Kiểu tĩnh** và **class runtime** là hai khái niệm khác nhau.

### 14.5. Không nên dùng raw type và unchecked cast

```java
List<Integer> integers = List.of(1, 2);
// List<Number> unsafe = (List<Number>) (List<?>) integers;
```

Ép kiểu không kiểm tra có thể vượt qua lỗi compile-time nhưng phá hợp đồng kiểu, đặc biệt khi các phần tử mới được thêm qua tham chiếu `List<Number>`. Nếu cần chuyển một danh sách đã có kiểu, ưu tiên wildcard chỉ đọc hoặc tạo danh sách mới phù hợp.

## 15. Tương tác giữa type inference và overload resolution

Với nhiều phương thức trùng tên, compiler còn phải chọn overload phù hợp. Ngữ cảnh kiểu và suy luận có quan hệ qua lại, nhưng **không nên dự đoán overload chỉ từ kiểu bên trái**.

Ví dụ hai overload có thể phân biệt thật sự ở số tham số:

```java
static void log(String text) {
    System.out.println(text);
}

static void log(String text, int count) {
    System.out.println(text + ":" + count);
}
```

Ở đây overload được xác định đơn giản theo danh sách đối số. Nhưng nếu nhiều overload nhận functional interface hoặc generic type, lambda, method reference và inference có thể tạo ràng buộc phức tạp hoặc ambiguity.

Đặc biệt, không thể khai báo hai overload chỉ khác nhau bởi đối số generic sau erasure:

```java
// static void consume(List<Integer> values) {}
// static void consume(List<Number> values) {}
```

Hai chữ ký có cùng erasure là `consume(List)`; Java báo name clash. Target typing không vượt qua giới hạn của type erasure.

Khi gặp overload mơ hồ, thay vì đoán rằng compiler nhìn kiểu biến kết quả rồi chọn “đúng”, nên xem từng chữ ký, quy tắc applicability và most-specific, hoặc dùng kiểu tường minh/type witness có cơ sở.

## 16. Target typing với nested generics

Ngữ cảnh có thể giúp suy luận qua nhiều tầng generic:

```java
List<List<Number>> matrix = List.of(
    List.of(1, 2),
    List.of(3.5, 4.5)
);
```

Vế trái yêu cầu `List<List<Number>>`; các biểu thức `List.of` bên trong được kiểm tra trong ngữ cảnh tương ứng để xây cấu trúc phù hợp.

Đối chiếu:

```java
List<List<Integer>> matrixOfIntegers =
        List.of(List.of(1, 2));

// List<List<Number>> invalid = matrixOfIntegers; // Lỗi.
```

Một biến `List<List<Integer>>` đã có kiểu tĩnh cụ thể. Nó không tự động tương thích `List<List<Number>>` chỉ vì `Integer <: Number`, bởi invariance tồn tại ở từng cấp generic.

Nếu chỉ đọc:

```java
List<? extends List<? extends Number>> view = matrixOfIntegers;
Number first = view.get(0).get(0);
```

Đây là cách dùng wildcard ở **đúng các cấp** cần biến thiên khi không tạo bản sao. Còn khi đang gọi generic factory mới trong một target type, compiler có thể suy luận type argument ngay từ đầu.

## 17. Type witness, target type và thiết kế API

### 17.1. Khi API viết tốt, caller ít phải can thiệp

Một generic factory:

```java
static <T> List<T> singleton(T value) {
    return List.of(value);
}
```

cho phép:

```java
List<Number> a = singleton(1);
List<Integer> b = singleton(1);
List<Object> c = singleton(1);
```

Caller được chọn kiểu đích hợp lệ phù hợp yêu cầu sử dụng.

### 17.2. Khi API cần cố định quan hệ giữa nguồn và đích

Ví dụ:

```java
static <T> List<T> copyOf(Iterable<? extends T> source) {
    List<T> result = new ArrayList<>();
    for (T item : source) {
        result.add(item);
    }
    return result;
}
```

Lời gọi:

```java
List<Integer> ints = List.of(1, 2);
List<Number> nums = copyOf(ints);
```

Compiler phối hợp cận `? extends T` của nguồn với target `List<Number>` của kết quả. Việc trả **bản sao mới** cho phép chọn một `List<Number>` thực sự, trong khi phương thức vẫn nhận nguồn `Iterable<Integer>`.

Nếu API chỉ trả lại chính reference `List<Integer>` dưới kiểu `List<Number>`, điều đó sẽ không an toàn. Thiết kế generic không được đánh tráo hành vi copy với alias.

### 17.3. Khi muốn định kiểu không lệ thuộc ngữ cảnh, nêu rõ đối số

```java
var numbers = List.<Number>of(1, 2.5);
var sink = new ArrayList<Number>();
```

Dù dùng `var`, initializer ở ví dụ này đã nêu rõ `Number`, nên kiểu được suy luận cho biến tương ứng có type argument `Number`.

Dùng khai báo như vậy khi kiểu rộng có ý nghĩa đối với API, thay vì trông chờ compiler đọc ý định từ tên biến.

## 18. Bảng đối chiếu nhanh

| Tình huống | Có thông tin kiểu đích hữu ích? | Kết quả cần nhớ |
| --- | --- | --- |
| `List<Number> x = List.of(1)` | Có, từ phép gán | Có thể suy luận `E = Number` |
| `acceptNumbers(List.of(1))` | Có, từ tham số `List<Number>` | Lời gọi generic lồng bên trong tương thích |
| `return List.of(1)` trong hàm trả `List<Number>` | Có, từ kiểu trả về khai báo | Biểu thức generic được kiểm tra theo `List<Number>` |
| `var x = List.of(1)` | Không có `List<Number>` tường minh | `x` được suy luận là `List<Integer>` |
| `List<Number> x = new ArrayList<>()` | Có, cho diamond | Suy luận `ArrayList<Number>` |
| `var x = new ArrayList<>()` | Không có kiểu đích `Number` | Suy luận `ArrayList<Object>` khi không có ràng buộc khác |
| `Function<String,Integer> f = s -> s.length()` | Có, functional interface | `s` có kiểu `String` |
| `var f = s -> s.length()` | Không | Lỗi: lambda cần target functional interface |
| `Supplier<List<String>> f = Collections::emptyList` | Có | Method reference được định kiểu phù hợp |
| `var f = String::length` | Không | Lỗi: method reference cần target |
| `List<Number> y = existingListOfInteger` | Có target nhưng source cố định | Lỗi invariance |
| `List<Number> y = new ArrayList<>(existingListOfInteger)` | Có và tạo bản sao mới | Hợp lệ |

Các kết quả trong bảng dựa trên JDK 17 và các import hợp lệ, với tên biến/phương thức đã được định nghĩa đúng kiểu.

## 19. Lỗi thường gặp và cách tự chẩn đoán

### Lỗi A — Nhầm “suy luận” với “ép kiểu”

Lời gọi generic factory có thể chọn `T = Number` từ đầu. Đó là suy luận **khi biểu thức được kiểm tra**, không phải một ép kiểu `List<Integer>` sang `List<Number>` sau khi kiểu đã được xác định.

### Lỗi B — Tin rằng `var` luôn chọn “kiểu tốt nhất”

`var` suy luận dựa trên initializer và không tự biết rằng business logic muốn `List<Number>`. Nếu chương trình sau này cần kiểu đích rộng, hãy thể hiện ý định bằng khai báo hoặc type witness.

### Lỗi C — Đổi `? extends T` thành `List<T>` tùy tiện

Nếu phương thức nhận tham chiếu `List<Integer>` thực sự, chuyển nó thành `List<Number>` bằng ép kiểu là không an toàn. Dùng wildcard cho view đọc, hoặc tạo bản sao theo kiểu rộng.

### Lỗi D — Cho rằng mọi biểu thức dùng `?:` hay method call đều là poly expression

Quy tắc Java có điều kiện cụ thể. Chỉ một số dạng generic method invocation, reference conditional expression, lambda, method reference, diamond… dùng target typing theo cách mô tả. Đừng suy luận rằng phép gán có thể biến đổi kiểu tĩnh của bất kỳ biểu thức nào.

### Lỗi E — Quên boxing/unboxing

Primitive `int` không phải subtype của `Number`. Nhưng `1` có thể boxing thành `Integer`, sau đó `Integer <: Number`. Khi đọc lỗi generic từ literal primitive, hãy tính đến boxing conversion.

### Lỗi F — Lẫn kết quả suy luận với khả năng sửa collection

`List<Number> list = List.of(1,2)` hợp lệ, nhưng `list.add(3.5)` vẫn ném `UnsupportedOperationException`. Muốn thêm phần tử thật, dùng implementation mutable như `ArrayList`.

## 20. Bài tập tự kiểm tra

### Bài 1 — Dự đoán đúng/sai

```java
static <T> List<T> box(T item) {
    return List.of(item);
}

static void takeNumbers(List<Number> values) {}

List<Number> a = box(1); // (1)
takeNumbers(box(2));     // (2)
List<Integer> b = box(3);
// takeNumbers(b);       // (3)
```

**Đáp án:** (1) đúng nhờ target `List<Number>`; (2) đúng nhờ ngữ cảnh truyền đối số `List<Number>`; (3) sai vì biến `b` đã có kiểu `List<Integer>`, không phải `List<Number>`.

### Bài 2 — Kiểu của biến `var`

```java
var a = List.of(1, 2);
var b = new ArrayList<>();
List<Number> c = new ArrayList<>();
```

**Đáp án:** `a` là `List<Integer>` (vì các phần tử `Integer`); `b` là `ArrayList<Object>` khi không có ràng buộc khác; `c` có kiểu khai báo `List<Number>`, với diamond suy luận type argument của implementation theo target.

### Bài 3 — Target của `return`

```java
static List<CharSequence> load() {
    return List.of("A", "B");
}
```

**Đáp án:** Hợp lệ. `List.of` có thể suy luận type argument `CharSequence`, nhận các `String` là subtype của `CharSequence`. Nếu trả một **biến** đã khai báo `List<String>`, phép gán trực tiếp lại sai vì invariance.

### Bài 4 — Lambda và method reference

```java
Function<String, Integer> a = text -> text.length();
Function<String, Integer> b = String::length;
// var c = text -> text.length();
```

**Đáp án:** `a` và `b` hợp lệ vì có target functional interface; `c` sai vì `var` không cung cấp target type cần thiết cho lambda.

### Bài 5 — Khi nào nên dùng wildcard?

Một phương thức nhận danh sách `Integer` do caller đã tạo, nhưng chỉ cần đọc dưới dạng `Number`:

```java
static double sum(List<? extends Number> values) {
    double result = 0;
    for (Number number : values) {
        result += number.doubleValue();
    }
    return result;
}
```

**Đáp án:** `? extends Number` cho phép truyền `List<Integer>`, `List<Double>` hoặc `List<Number>`. Target typing của các **lời gọi factory** không thay thế được wildcard khi **biến nguồn đã có kiểu cụ thể**.

### Bài 6 — Đánh giá biểu thức `?:`

```java
boolean flag = false;
List<Number> values = flag ? List.of(1) : List.of(2.5);
```

**Đáp án:** Hợp lệ nhờ target type `List<Number>` trong ngữ cảnh gán của reference conditional expression; khi chạy, `flag = false` nên phần tử là `Double` có giá trị `2.5`. Không kết luận điều tương tự cho một biến đã có kiểu `List<Integer>` được đưa vào nhánh: kiểu tĩnh của biến không bị suy luận lại.

## 21. Quy trình phân tích khi compiler báo “incompatible types”

1. Xác định **toàn biểu thức** cần có kiểu gì và nguồn thông tin kiểu đích đến từ đâu: gán, truyền đối số, `return` hay target functional interface.
2. Kiểm tra biểu thức bên trong có thực sự thuộc loại dùng **target typing** hay không.
3. Nếu là generic method, tìm các type parameter `<T>`, kiểu đầu vào, kiểu trả về, bound và các ràng buộc cần đồng thời thỏa.
4. Nếu là constructor có `<>`, kiểm tra target type có giúp suy luận đối số kiểu không.
5. Nếu biến được khai báo bằng `var`, xác định kiểu initializer ngay tại khai báo; không giả định ý định từ tên biến.
6. Nếu nguồn đã là `List<Integer>` và đích cần `List<Number>`, xem lại invariance; cân nhắc `? extends Number` hoặc tạo bản sao.
7. Với lambda/method reference, kiểm tra functional interface ở ngữ cảnh đích, kiểu tham số và kiểu kết quả.
8. Nếu suy luận mơ hồ, thử khai báo type argument `<Number>` rõ ràng **khi hợp lệ**, hoặc đơn giản hóa biểu thức bằng biến trung gian có kiểu tường minh.
9. Sau khi biên dịch, đánh giá riêng các hợp đồng runtime: mutability, null, checked/unchecked exceptions.

## 22. Tài liệu tham khảo

- [Java Language Specification 17 — §15.12 Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): suy luận trong lời gọi phương thức và applicability.
- [Java Language Specification 17 — §18 Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html): ràng buộc, reduction và suy luận kiểu generic.
- [Java Language Specification 17 — §15.2 Poly Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.2): khái niệm poly expression và ngữ cảnh kiểu.
- [Java Language Specification 17 — §15.25 Conditional Operator](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.25): quy tắc kiểu của biểu thức điều kiện.
- [Java Language Specification 17 — §15.27 Lambda Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27): lambda và target functional interface.
- [Java Language Specification 17 — §15.13 Method Reference Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.13): method reference và ngữ cảnh kiểu.
- [Java Language Specification 17 — §14.4 Local Variable Declaration Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.4): suy luận cho biến cục bộ dùng `var`.
- [Java Tutorials — Type Inference](https://docs.oracle.com/javase/tutorial/java/generics/genTypeInference.html): ví dụ tổng quan về type inference.

**Kết luận:** Kiểu đích là một nguồn ràng buộc giúp Java suy luận type arguments trong những biểu thức phù hợp, đặc biệt khi **gán kết quả lời gọi generic, truyền biểu thức generic làm đối số, hoặc trả về từ một phương thức có kiểu đã khai báo**. Nó không biến `List<Integer>` đang tồn tại thành `List<Number>`, không làm `var` tự biết kiểu nghiệp vụ cần dùng và không xóa bỏ kiểm tra an toàn của Generics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-003">Diamond và suy luận khi khởi tạo đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Diamond và suy luận kiểu khi khởi tạo đối tượng trong Java Generics

## 1. Diamond `<>` là gì?

Khi tạo đối tượng của một lớp generic, Java có thể **suy luận đối số kiểu** từ ngữ cảnh và các đối số của constructor. Cặp dấu ngoặc nhọn góc rỗng `<>` được gọi là **diamond operator** (toán tử diamond). Cú pháp này có từ Java 7; mục đích chính là tránh viết lặp những đối số kiểu đã đủ thông tin để trình biên dịch tìm ra.

```java
// Viết đầy đủ đối số kiểu bên trái và bên phải
List<String> names = new ArrayList<String>();

// Diamond: compiler suy luận kiểu của đối tượng mới
List<String> names = new ArrayList<>();
```

Hai dòng là **hai cách khai báo tương đương**, cần chọn một dòng khi đặt trong chương trình thực tế. Trong dòng thứ hai, `javac` dùng ngữ cảnh `List<String>` để suy luận rằng đối tượng được tạo phù hợp với `ArrayList<String>`.

Điểm cần nhớ:

- Diamond là `<>` xuất hiện **sau tên lớp generic khi tạo đối tượng**, chẳng hạn `new ArrayList<>()`, `new HashMap<>()` hoặc `new Box<>(value)`.
- `<>` yêu cầu compiler **suy luận đối số kiểu**, không có nghĩa “không dùng generics”, cũng không có nghĩa “kiểu phần tử luôn là `Object`”.
- Diamond **khác raw type**. `new ArrayList()` bỏ đối số kiểu và có thể làm mất kiểm tra kiểu; `new ArrayList<>()` vẫn tham gia kiểm tra generics.
- Diamond **khác wildcard `<?>`**. `?` mô tả đối số kiểu không biết ở một kiểu được sử dụng, còn `<>` yêu cầu suy luận các đối số kiểu để thực hiện phép tạo đối tượng.

Đối số kiểu được kiểm tra chủ yếu khi **biên dịch**. Diamond không tự thêm kiểm tra từng phần tử tại runtime; Java Generics vẫn hoạt động theo cơ chế *type erasure*.

## 2. Mối quan hệ giữa kiểu đích và biểu thức `new`

Khi có một kiểu mong đợi ở vị trí chứa biểu thức `new`, compiler có thể dùng kiểu đó làm **target type** (kiểu đích). Các ngữ cảnh có target type gồm phép gán, khởi tạo biến, truyền đối số vào phương thức và trả kết quả từ một phương thức.

Ví dụ phổ biến nhất:

```java
List<Integer> numbers = new ArrayList<>();
Map<String, Integer> counts = new HashMap<>();
Set<Long> ids = new HashSet<>();
```

Các khai báo cần import các lớp từ `java.util`. Có thể đọc dòng đầu như sau:

1. Biến bên trái cần một `List<Integer>`.
2. Biểu thức bên phải đang tạo `ArrayList<E>` và dùng diamond.
3. Compiler suy luận `E = Integer` để phép gán hợp lệ.
4. Bên gọi nhận một danh sách có thể thêm `Integer`, nhưng không thể thêm một `String` tùy ý.

```java
List<Integer> numbers = new ArrayList<>();
numbers.add(10);
// numbers.add("ten"); // Lỗi biên dịch
```

Diamond giảm lặp cú pháp, **không** làm thay đổi các quy tắc bất biến (*invariance*) của Java Generics. Vì `Integer` kế thừa `Number` không có nghĩa `List<Integer>` là kiểu con của `List<Number>`.

```java
List<Integer> ints = new ArrayList<>();
// List<Number> nums = ints; // Lỗi: generic invariant
```

### Kiểu đích không nhất thiết bằng đúng lớp ở vế phải

```java
Collection<String> words = new ArrayList<>();
Iterable<Integer> input = new ArrayList<>();
Map<String, List<Integer>> grouped = new HashMap<>();
```

`ArrayList<E>` thực hiện `Collection<E>` và `Iterable<E>`; `HashMap<K,V>` thực hiện `Map<K,V>`. Diamond dùng thông tin kiểu mong đợi qua quan hệ kiểu tương thích, không chỉ sao chép nguyên tên lớp ở vế trái.

## 3. So sánh viết tường minh, diamond và raw type

| Cách viết | Ý nghĩa | An toàn kiểu |
|---|---|---|
| `new ArrayList<String>()` | Nêu tường minh `E = String` | Có |
| `new ArrayList<>()` với đích `List<String>` | Suy luận `E = String` từ ngữ cảnh | Có |
| `new ArrayList()` | Dùng raw `ArrayList`, không khai báo đối số kiểu | Bỏ kiểm tra generic ở những thao tác raw; có thể có cảnh báo `unchecked` |
| `new ArrayList<?>()` | Cố dùng wildcard làm đối số kiểu trong phép tạo đối tượng | Không hợp lệ |
| `new ArrayList` | Thiếu `()` cho constructor trong Java thông thường | Không hợp lệ |

Đặt hai dòng sau cạnh nhau:

```java
List<String> safe = new ArrayList<>();
List<String> unsafe = new ArrayList(); // Unchecked conversion
```

Dòng thứ hai có thể biên dịch với cảnh báo vì vế phải là raw type. Khi dùng `javac -Xlint:rawtypes,unchecked`, compiler có thể chỉ ra vị trí mất thông tin kiểu. Một ví dụ về rủi ro raw type:

```java
List raw = new ArrayList();
raw.add(42);
List<String> assumedStrings = raw; // Unchecked conversion
// String s = assumedStrings.get(0); // Có thể ném ClassCastException
```

Đây là **mã minh họa không an toàn**. Với diamond, quá trình suy luận và kiểm tra tham số kiểu diễn ra ở compile time, nên không cần mở rủi ro này chỉ để tránh lặp tên `String`.

## 4. Diamond dùng thông tin từ constructor

Compiler không chỉ nhìn kiểu đích. Nó còn có thể dùng các **đối số truyền cho constructor** và ràng buộc của constructor để suy luận đối số kiểu.

Ví dụ:

```java
ArrayList<String> copy = new ArrayList<>(List.of("Lan", "Minh"));
```

Constructor `ArrayList(Collection<? extends E>)` nhận một collection các phần tử có kiểu phù hợp với `E`. Lời gọi `List.of("Lan", "Minh")` cung cấp các chuỗi; kiểu đích `ArrayList<String>` cũng ràng buộc `E` là `String`. Các thông tin này nhất quán với nhau.

Khi đích là kiểu rộng hơn:

```java
List<Number> numbers = new ArrayList<>(List.of(1, 2.5));
```

Compiler giải các ràng buộc từ **kiểu đích `List<Number>`** và các đối số constructor. Kết quả có thể là `ArrayList<Number>`, vì các giá trị `Integer` và `Double` đều gán được cho `Number`. Điều đó không biến `ArrayList<Integer>` thành subtype của `ArrayList<Number>`.

Với lớp generic tự định nghĩa:

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
```

Có thể tạo:

```java
Box<String> a = new Box<>("Java");
Box<Integer> b = new Box<>(21);
```

Ở đây, constructor nhận `T`, trong khi target type nói rõ `T` là `String` hoặc `Integer`. Hai nguồn ràng buộc phối hợp để compiler chọn kiểu phù hợp.

**Lưu ý:** Compiler tìm kiểu thỏa các ràng buộc của toàn biểu thức, chứ không đơn giản “đọc chữ bên trái rồi dán vào diamond” trong mọi trường hợp. Nếu constructor, cận kiểu hoặc kiểu đích mâu thuẫn, chương trình có thể không biên dịch.

## 5. Khi khởi tạo với `var`, constructor phải tự cung cấp thông tin kiểu

`var` làm compiler suy luận kiểu của biến từ initializer, nên thiếu một kiểu đích như `List<String>` để định hướng diamond. Kết quả phụ thuộc chữ ký constructor và các đối số:

```java
var mixed = new ArrayList<>();             // ArrayList<Object>.
mixed.add("hello");
mixed.add(123);

var words = new ArrayList<>(List.of("A", "B")); // ArrayList<String>.
// words.add(3); // Lỗi: Integer không là String.

var fixed = new ArrayList<String>();      // ArrayList<String>.
```

Ở dòng đầu, constructor không có đối số ràng buộc E; cận trên mặc định Object dẫn đến `ArrayList<Object>`. Ở dòng thứ hai, constructor `ArrayList(Collection<? extends E>)` nhận nguồn String, nên E được xác định hữu ích dù không có target type bên trái. **Các lệnh add về sau không làm E được suy luận lại.**

| Khai báo | Kiểu tĩnh |
| --- | --- |
| `List<String> a = new ArrayList<>()` | `List<String>` |
| `var b = new ArrayList<String>()` | `ArrayList<String>` |
| `var c = new ArrayList<>()` | `ArrayList<Object>` |
| `var d = new ArrayList<>(List.of("x"))` | `ArrayList<String>` |

Đây là một nhánh đặc biệt của **suy luận constructor với diamond**: hãy xem ràng buộc ở đúng biểu thức `new`. Nếu mục tiêu là danh sách String rỗng, nên viết `List<String> list = new ArrayList<>()` hoặc `var list = new ArrayList<String>()`.

## 6. Diamond trong lời gọi phương thức

Biểu thức tạo đối tượng với diamond có thể xuất hiện trực tiếp ở vị trí **đối số của một phương thức**. Khi phương thức yêu cầu một kiểu cụ thể, compiler có thể dùng kiểu tham số hình thức làm target type.

```java
static void acceptStrings(List<String> input) {
    System.out.println(input.size());
}

static void example() {
    acceptStrings(new ArrayList<>());
}
```

`new ArrayList<>()` được kiểm tra trong ngữ cảnh yêu cầu `List<String>`; compiler có thể suy luận `E = String`. Việc danh sách lúc tạo đang rỗng không làm compiler mặc định suy luận `Object` trong trường hợp này.

Một ví dụ với tham số generic:

```java
static <T> void addDefault(List<T> values, T element) {
    values.add(element);
}

static void example() {
    addDefault(new ArrayList<>(), "Java");
}
```

Compiler có thể suy luận kiểu `T` để lời gọi hợp lệ, dựa vào thông tin từ cả tham số danh sách và đối số `"Java"`. Với API có nhiều overload hoặc các ràng buộc phức tạp, kiểu suy luận có thể ít hiển nhiên hơn; khi đó khai báo biến rõ kiểu trước khi gọi sẽ dễ đọc và dễ chẩn đoán hơn.

Không nên diễn giải mọi `new ArrayList<>()` là `ArrayList<Object>`: nó chỉ có thể suy luận như vậy trong **ngữ cảnh thiếu ràng buộc cụ thể**, chẳng hạn `var list = new ArrayList<>()` với constructor không đối số.

## 7. Diamond trong câu lệnh `return`

Kiểu trả về của một phương thức cũng có thể tạo target type.

```java
static List<String> emptyNames() {
    return new ArrayList<>();
}
```

Compiler kiểm tra `new ArrayList<>()` theo kiểu trả về `List<String>`. Không phải viết `return new ArrayList<String>();`.

Với phương thức generic:

```java
static <T> List<T> emptyList() {
    return new ArrayList<>();
}
```

Trong thân phương thức, `T` là một type parameter có tên; biểu thức diamond được suy luận sao cho kết quả phù hợp `List<T>`. Bên gọi có thể dùng:

```java
List<Integer> ints = emptyList();
List<String> texts = emptyList();
```

Ở ví dụ `emptyList`, phần suy luận **tại lời gọi phương thức** và phần suy luận **tại biểu thức tạo đối tượng** là hai bước liên quan nhưng khác nhau:

1. `emptyList()` là lời gọi phương thức generic, có thể dựa vào kiểu đích để tìm `T`.
2. `new ArrayList<>()` nằm trong thân phương thức, được kiểm tra theo kiểu trả về `List<T>` đã khai báo.

Đừng kết luận rằng một đối tượng `ArrayList` ở runtime chứa được thông tin đầy đủ của `T`; generics vẫn bị erasure.

## 8. Khởi tạo cấu trúc generic lồng nhau

Diamond đặc biệt hữu ích khi kiểu đích dài và lồng nhau:

```java
Map<String, List<Integer>> groups =
        new HashMap<String, List<Integer>>();
```

Có thể viết gọn:

```java
Map<String, List<Integer>> groups = new HashMap<>();
groups.put("even", new ArrayList<>());
```

Ở biểu thức bên ngoài, compiler dùng target type `Map<String, List<Integer>>` để xác định `K = String` và `V = List<Integer>`. Ở lời gọi `put`, tham số giá trị yêu cầu `List<Integer>`, nên `new ArrayList<>()` tiếp tục suy luận phần tử là `Integer`.

Một ví dụ có dữ liệu:

```java
Map<String, List<Integer>> groups = new HashMap<>();
groups.put("even", new ArrayList<>(List.of(2, 4)));
groups.put("odd", new ArrayList<>(List.of(1, 3)));

// groups.put("wrong", new ArrayList<>(List.of("x"))); // Không hợp kiểu
```

Diamond bỏ lặp cú pháp, nhưng compiler vẫn kiểm tra **từng tầng generic**. `Map<String, List<Integer>>` không chấp nhận `List<String>` ở vị trí giá trị.

Nên đặt tên biến rõ ràng khi nhiều lớp generic lồng nhau làm việc đọc khó khăn. Diamond làm giảm văn bản lặp, không loại bỏ nhu cầu thiết kế kiểu có ý nghĩa.

## 9. Ví dụ Java 17 hoàn chỉnh

Tạo tệp `DiamondInferenceDemo.java`:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiamondInferenceDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static void printStrings(List<String> input) {
        System.out.println("Argument: " + input);
    }

    static List<String> copyWords(List<String> source) {
        return new ArrayList<>(source);
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("Lan");
        System.out.println("Names: " + names);

        var scores = new ArrayList<Integer>();
        scores.add(3);
        scores.add(5);
        System.out.println("Scores: " + scores);

        var mixed = new ArrayList<>();
        mixed.add("text");
        mixed.add(42);
        System.out.println("Mixed inference: " + mixed);

        Map<String, List<Integer>> groups = new HashMap<>();
        groups.put("even", new ArrayList<>(List.of(2, 4)));
        System.out.println("Even: " + groups.get("even"));

        Box<String> greeting = new Box<>("Hello");
        var inferredBox = new Box<>("Hi");
        System.out.println("Greeting: " + greeting.get());
        System.out.println("Inferred box: " + inferredBox.get());

        List<String> copied = copyWords(List.of("red", "blue"));
        System.out.println("Copied: " + copied);

        printStrings(new ArrayList<>(List.of("A", "B")));
    }
}
```

Biên dịch bằng JDK 17 hoặc mới hơn:

```bash
javac DiamondInferenceDemo.java
java DiamondInferenceDemo
```

**Stdout chính xác:**

```text
Names: [Lan]
Scores: [3, 5]
Mixed inference: [text, 42]
Even: [2, 4]
Greeting: Hello
Inferred box: Hi
Copied: [red, blue]
Argument: [A, B]
```

Đọc chương trình theo ngữ cảnh:

1. `names` có target type `List<String>`, nên `new ArrayList<>()` suy luận phần tử `String`.
2. `scores` dùng `var`, nhưng kiểu `Integer` đã nằm trong `new ArrayList<Integer>()`, nên biến có kiểu `ArrayList<Integer>`.
3. `mixed` dùng `var` và diamond của constructor không tham số; compiler không có dấu hiệu cụ thể hơn và suy luận `ArrayList<Object>`. `add("text")` và `add(42)` đều an toàn đối với `Object`.
4. `groups` dùng hai tầng suy luận: `HashMap<>` từ kiểu khai báo và `ArrayList<>(List.of(2,4))` từ tham số của `put` và các giá trị đầu vào.
5. `greeting` suy luận `Box<String>` nhờ target type và constructor. `inferredBox` không có target type tường minh nhưng constructor nhận `String`, nên biến có kiểu `Box<String>`.
6. `copyWords` cho thấy diamond trong biểu thức `return`; `printStrings` cho thấy diamond ở vị trí đối số phương thức.

Ví dụ chủ động dùng `ArrayList` mutable ở những nơi thêm phần tử. `List.of(...)` cung cấp dữ liệu nguồn bất biến, nhưng constructor `new ArrayList<>(source)` tạo danh sách mới có thể sửa; hai thứ có hành vi mutation khác nhau.

## 10. Diamond không phải wildcard

Hai cú pháp nhìn gần giống nhưng phục vụ mục đích khác nhau:

```java
List<?> unknown;              // Wildcard: kiểu phần tử chưa biết
List<String> known = new ArrayList<>(); // Diamond: suy luận String
```

`?` xuất hiện trong **đối số kiểu của một kiểu generic được sử dụng**, để biểu diễn kiểu chưa biết theo hợp đồng. `<>` xuất hiện khi **tạo đối tượng** để cho compiler suy luận đối số kiểu. Không thể viết:

```java
// List<?> wrong = new ArrayList<?>(); // Không hợp lệ
// List<String> wrong2 = new ArrayList<? extends String>(); // Không hợp lệ
```

Ngay cả khi đích dùng wildcard, vế phải khi tạo đối tượng vẫn cần diamond hoặc một đối số kiểu cụ thể:

```java
List<?> view = new ArrayList<String>();
List<?> inferredView = new ArrayList<>();
```

Ở `inferredView`, ngữ cảnh `List<?>` không xác định một kiểu phần tử duy nhất. Compiler chọn một kiểu phù hợp với các ràng buộc; không được dựa vào việc nó nhất thiết là `String`. Để tạo danh sách `String` chắc chắn, viết `new ArrayList<String>()` hoặc cung cấp ngữ cảnh đủ rõ như `List<String> names = new ArrayList<>()`.

## 11. Diamond và constructor generic là hai cơ chế độc lập

Đối số kiểu của **lớp** và đối số kiểu của **constructor generic** có thể là hai thứ khác nhau.

Ví dụ lớp `Entry<T>`:

```java
final class Entry<T> {
    private final T value;

    <U> Entry(T value, U marker) {
        this.value = value;
        System.out.println("Marker: " + marker);
    }

    T get() {
        return value;
    }
}
```

Với:

```java
Entry<String> item = new Entry<>("Java", 2026);
```

`T` là tham số kiểu của lớp `Entry<T>`, được ràng buộc thành `String`. `U` là tham số kiểu riêng của constructor, được suy luận từ `2026` (một `Integer` sau boxing). Diamond `<>` đại diện **đối số kiểu của lớp cần suy luận**; nó không ghi lại `<U>` ở vị trí tham số của constructor.

Đây là đoạn minh họa khai báo lớp/khởi tạo, chưa có `main` nên không phải chương trình độc lập. Nếu muốn gọi constructor generic với type argument tường minh trong Java, cú pháp cụ thể khác với diamond; thông thường không cần làm vậy nếu suy luận đủ tốt.

Bài học là: compiler có thể giải nhiều biến kiểu trong cùng một biểu thức `new`, song mỗi biến có **nguồn khai báo và ràng buộc riêng**.

## 12. Giới hạn cú pháp và lỗi phổ biến

### Diamond chỉ phù hợp với lớp generic

```java
List<String> xs = new ArrayList<>(); // Hợp lệ
String text = new String("Hello");  // Hợp lệ, không cần diamond
// String bad = new String<>();     // Không hợp lệ: String không generic
```

Không thể viết diamond thay cho việc khai báo type parameter mới:

```java
// class Box<> { }                  // Không hợp lệ
class GenericBox<T> { }            // Khai báo type parameter có tên

// static <> void method() { }     // Không hợp lệ
static <T> T identity(T value) { return value; } // Ví dụ khai báo đúng
```

Hai khai báo `GenericBox` và `identity` là các ví dụ ngữ pháp riêng; trong chương trình thực tế hãy đặt phương thức trong một lớp phù hợp.

### Không thể áp dụng diamond cho mảng generic

Java không cho tạo trực tiếp nhiều dạng mảng của kiểu generic có đối số không reifiable:

```java
// List<String>[] lists = new ArrayList<>[10]; // Không hợp lệ
```

Lý do là mảng theo dõi kiểu phần tử runtime theo cách khác với generics đã bị erasure. Với tập hợp generic thông thường, hãy cân nhắc `List<List<String>>`:

```java
List<List<String>> lists = new ArrayList<>();
```

### Diamond không bảo đảm mọi phép gán đều hợp kiểu

```java
List<String> strings = new ArrayList<>();
// List<Integer> integers = strings; // Không biên dịch
// List<Number> numbers = new ArrayList<String>(); // Không biên dịch
```

Diamond không sửa được một quan hệ generic không tương thích. Compiler không tự biến `List<String>` thành `List<Number>` hay `List<Integer>` khi ràng buộc kiểu mâu thuẫn.

### Không viết wildcard ngay trong phép tạo đối tượng

```java
// new ArrayList<?>()                  // Không hợp lệ
// new ArrayList<? extends Number>()   // Không hợp lệ
List<? extends Number> values = new ArrayList<Integer>(); // Hợp lệ
```

Wildcard biểu diễn hợp đồng khi **sử dụng kiểu**. Biểu thức `new` cần suy luận một kiểu cụ thể có thể thỏa ràng buộc, hoặc chỉ rõ đối số kiểu thích hợp.

## 13. Diamond với anonymous class: khác biệt Java 7 và Java 9+

Trong Java 7–8, diamond không được dùng với việc khởi tạo lớp ẩn danh (*anonymous class*). Từ Java 9, Java cho phép diamond với anonymous class **khi kiểu được suy luận là denotable** (có thể biểu diễn phù hợp trong Java source).

Ví dụ tương thích Java 17:

```java
List<String> names = new ArrayList<>() {
    @Override
    public boolean add(String item) {
        return super.add(item.trim());
    }
};
names.add("  Lan  ");
System.out.println(names); // [Lan]
```

Đây là đoạn minh họa, cần import `java.util.List` và `java.util.ArrayList` và đặt trong một phương thức. Việc override `add` dùng đúng kiểu `String` đã suy luận từ `List<String>` ở bên trái.

Khi dùng diamond trong anonymous class, Java áp thêm ràng buộc cho các phương thức **non-private** mới khai báo trong thân anonymous class: chúng phải override/implement phương thức từ supertype. Nếu cần định nghĩa phương thức mới không override, hãy kiểm tra quy tắc này hoặc dùng named class; đừng giả định mọi anonymous class với diamond đều hợp lệ.

Điểm cần nhớ cho Java 17 là: diamond **có thể** dùng với anonymous class trong những trường hợp phù hợp, nhưng không phải là thay thế cú pháp phổ quát cho mọi tình huống.

## 14. Cách chẩn đoán lỗi suy luận diamond

Khi compiler thông báo `incompatible types`, `cannot infer type arguments` hoặc `inference variable ... has incompatible bounds`, hãy làm theo thứ tự:

1. **Xác định target type**: biểu thức `new C<>()` có đang được gán cho biến `List<String>`, truyền vào một tham số `Collection<Number>`, hay đứng trong `return` với kiểu trả về cụ thể?
2. **Xem constructor đang chọn**: là constructor không đối số, constructor nhận `Collection<? extends E>`, hay overload khác? Những giá trị truyền vào cũng đóng góp ràng buộc.
3. **Ghi rõ biến kiểu cần suy luận**: ví dụ `ArrayList<E>` cần tìm `E`; `HashMap<K,V>` cần tìm `K` và `V`.
4. **Tìm mâu thuẫn**: đích yêu cầu `String` nhưng dữ liệu hoặc overload yêu cầu kiểu không phù hợp? Có đang nhầm `List<Integer>` là `List<Number>`?
5. **Nếu dùng `var`**, nhớ không có một khai báo đích rõ kiểu ở bên trái. Kiểm tra kiểu của biểu thức khởi tạo trước khi suy luận về các lời gọi `add` tiếp theo.
6. **Thử nêu tường minh đối số kiểu ở bên phải** để làm rõ ý định: `new ArrayList<String>()`. Nếu vẫn báo lỗi, nhiều khả năng bản thân các ràng buộc không tương thích.
7. **Ưu tiên chữ ký/kiểu rõ ràng** khi biểu thức có quá nhiều tầng generic hoặc overload. Không dùng raw type hay unchecked cast để “bắt compiler im lặng”.

Ví dụ một lỗi dễ gặp:

```java
List<String> names = new ArrayList<>();
names.add("Lan");
// names.add(10); // Lỗi: int boxing thành Integer, không gán được cho String
```

Nguyên nhân không nằm ở diamond; nó đã suy luận `String` đúng. Lời gọi thêm `Integer` vi phạm hợp đồng của `List<String>`.

Ví dụ khác:

```java
var values = new ArrayList<>();
values.add(10);
values.add("Lan");
```

Trường hợp này **không lỗi** vì `values` đã có kiểu `ArrayList<Object>`. Nếu muốn cấm chuỗi, phải cung cấp kiểu đích hoặc khai báo kiểu phần tử rõ ràng ngay khi khởi tạo.

## 15. Bài tập tự kiểm tra

Hãy đoán kiểu tĩnh và kết quả biên dịch:

```java
// A
List<String> a = new ArrayList<>();

// B
var b = new ArrayList<>();

// C
var c = new ArrayList<>(List.of(1, 2, 3));

// D
Map<String, List<Integer>> d = new HashMap<>();
d.put("odd", new ArrayList<>(List.of(1, 3)));

// E
List<Number> e = new ArrayList<>(List.of(1, 2.5));

// F
List<?> f = new ArrayList<String>();
```

Câu hỏi:

1. Vì sao `a.add("x")` hợp lệ còn `a.add(1)` không hợp lệ?
2. `b.add("x")` và `b.add(1)` có biên dịch không? `b` có phải raw type không?
3. `c` thường được suy luận là danh sách phần tử kiểu gì? Có thể thêm `String` vào `c` không?
4. Tại sao `d.put("odd", new ArrayList<>(List.of(1,3)))` hợp lệ?
5. Tại sao `e` chứa được cả phần tử `Integer` và `Double`?
6. `f.add("x")` có hợp lệ không? Dấu `?` trong `List<?>` khác `<>` ở biểu thức tạo đối tượng như thế nào?

**Đáp án:**

1. `a` có kiểu `List<String>`; target type khiến diamond suy luận `String`. `Integer` không gán được cho `String`.
2. Cả hai đều biên dịch vì `b` là `ArrayList<Object>`. `b` **không** phải raw type.
3. `c` được suy luận thành `ArrayList<Integer>` từ collection đầu vào `List.of(1,2,3)`; một `String` không phù hợp.
4. Kiểu của `d` là `Map<String,List<Integer>>`. Phương thức `put` đòi giá trị `List<Integer>` và constructor lồng nhau tạo danh sách phần tử `Integer`.
5. Target type `List<Number>` cho phép constructor tạo danh sách có tham số `Number`; `Integer` và `Double` đều là các kiểu con của `Number`.
6. `f.add("x")` không biên dịch vì wildcard `?` không tiết lộ kiểu phần tử thật. `<?>` nói “kiểu ẩn chưa biết” trong khai báo; `<>` bảo compiler suy luận đối số kiểu khi tạo đối tượng.

## 16. Tóm tắt cách áp dụng

Dùng **diamond `<>`** khi tạo đối tượng generic mà compiler đã có đủ ràng buộc từ kiểu đích, đối số constructor hoặc ngữ cảnh gọi phương thức. Nó giữ kiểm tra an toàn kiểu và làm mã bớt lặp. Với `var`, chú ý kiểu có thể được suy luận rộng hơn dự định nếu khởi tạo bằng `new ArrayList<>()` không đối số; hãy cung cấp kiểu rõ ràng khi muốn giới hạn phần tử.

Khi khó hiểu một kết quả suy luận, truy ngược **target type → constructor arguments → type parameters cần suy luận → ràng buộc tương thích**. Diamond không thay thế cho việc hiểu Generics, invariance và wildcard; nó chỉ giao việc suy luận kiểu hợp lệ cho compiler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-004">Truyền đối số kiểu tường minh để làm rõ lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền đối số kiểu tường minh để làm rõ lời gọi

## Mục tiêu

Trong phần lớn lời gọi phương thức generic của Java, compiler tự suy luận kiểu từ đối số truyền vào và từ ngữ cảnh sử dụng kết quả. Tuy nhiên, có lúc bạn muốn **chỉ định rõ type argument** để thể hiện ý định, điều khiển kiểu kết quả hoặc giúp một lời gọi phức tạp dễ hiểu hơn.

Ví dụ:

```java
Number value = ExplicitTypeArgumentsDemo.<Number>chooseFirst(10, 2.5);
List<String> empty = Collections.<String>emptyList();
```

Hai câu lệnh này là **minh họa**: phương thức `chooseFirst` do ta định nghĩa ở ví dụ chạy độc lập bên dưới; `Collections.emptyList()` là API chuẩn trong `java.util.Collections`.

Sau bài này, bạn sẽ phân biệt được **type parameter khi khai báo** và **type argument khi gọi**, viết đúng cú pháp truyền đối số kiểu tường minh cho phương thức static/instance, giải thích mối quan hệ với **type inference** và **target typing**, đồng thời nhận biết khi nào cách viết tường minh thực sự hữu ích và khi nào chỉ làm mã dài hơn.

## 1. Type parameter trong khai báo khác type argument trong lời gọi

Hãy xem phương thức generic:

```java
static <T> T chooseFirst(T first, T second) {
    return first;
}
```

Đây là phương thức **minh họa** đặt trong một class. Trong `static <T> T chooseFirst(...)`:

- `<T>` đứng trước kiểu trả về là **khai báo biến kiểu** (*type parameter*) của phương thức.
- Hai tham số `first` và `second` được kiểm tra theo kiểu `T`.
- Phương thức trả về một giá trị có kiểu tĩnh `T`.

Người gọi có thể để compiler suy luận:

```java
Number result = chooseFirst(10, 2.5);
```

Hoặc chỉ định rõ đối số kiểu:

```java
Number result = ExplicitTypeArgumentsDemo.<Number>chooseFirst(10, 2.5);
```

Ở lời gọi thứ hai, **`<Number>` là explicit type argument**. Nó thay vai trò `T` bằng `Number` cho lần gọi ấy: `Integer` nhận từ literal `10` qua boxing và `Double` nhận từ literal `2.5` đều tương thích với `Number`. Kiểu trả về tại compile time là `Number`.

Đối số kiểu **không thay thế giá trị tham số phương thức**. Những biểu thức `10` và `2.5` vẫn là đối số dữ liệu thực. Chỉ định `T = Number` cũng **không biến đối tượng `Integer` thành `Number` mới**; trong ví dụ, đối tượng trả về là phần tử `first` như ban đầu.

Cần tách rõ hai cặp khái niệm:

| Vị trí | Mẫu cú pháp | Tác dụng |
| --- | --- | --- |
| Khai báo phương thức | `static <T> T chooseFirst(T a, T b)` | Định nghĩa biến kiểu `T` |
| Gọi với kiểu tường minh | `Demo.<Number>chooseFirst(1, 2.5)` | Chọn `Number` làm `T` cho lần gọi |
| Gọi không tường minh | `Demo.chooseFirst(1, 2.5)` | Compiler tự suy luận kiểu phù hợp |
| Tham số dữ liệu | `1, 2.5` | Cung cấp các giá trị cho `a` và `b` |

Một class có thể vừa generic vừa có generic method riêng. Type parameter `T` của phương thức không mặc nhiên là type parameter của class và chỉ có hiệu lực trong phạm vi khai báo của chính phương thức.

## 2. Cú pháp truyền đối số kiểu tường minh

### 2.1. Gọi phương thức static

Với một phương thức static generic trong class `Demo`, cú pháp rõ ràng là:

```java
Demo.<String>identity("hello");
Demo.<Number>chooseFirst(10, 2.5);
```

Hai lời gọi trên minh họa cú pháp; để biên dịch, class `Demo` cần khai báo các phương thức `identity`/`chooseFirst` phù hợp. Dấu chấm nằm **trước** `<String>`, tiếp theo là **tên phương thức**. Cách viết `Demo.<String>identity(...)` cho biết đây là đối số kiểu của *phương thức được gọi*.

Với `java.util.Collections`, mã thực tế là:

```java
List<String> values = Collections.<String>emptyList();
```

`Collections.emptyList()` khai báo theo dạng:

```java
public static <T> List<T> emptyList()
```

Đây là chữ ký rút gọn của API chuẩn, không phải phương thức cần tự định nghĩa. Gọi `Collections.<String>emptyList()` chỉ định `T = String`, nên kết quả có kiểu `List<String>`.

### 2.2. Gọi phương thức instance

Đối số kiểu tường minh cũng có thể đặt sau biểu thức nhận (*receiver*) của một phương thức instance:

```java
String description = printer.<String>describe("generic");
```

Đây là đoạn minh họa, trong đó `printer` là đối tượng có phương thức generic `<T> String describe(T value)`. Ví dụ chạy hoàn chỉnh ở phần sau dùng biểu thức `new Printer().<String>describe("generic")`.

Bạn không cần dùng kiểu tham số của class làm đối số kiểu cho mọi phương thức instance. Cần xem **phương thức cụ thể có khai báo `<T>` hay không**. Một phương thức thông thường của `List<String>` như `add(String)` không tự nhiên trở thành generic method chỉ vì class/interface chứa nó có type parameter.

### 2.3. Nhiều type parameter và giới hạn kiểu

Nếu phương thức khai báo nhiều type parameter:

```java
static <K, V> String describePair(K key, V value) {
    return key + "=" + value;
}
```

thì caller có thể truyền type arguments **theo đúng thứ tự**:

```java
String pair = Demo.<String, Integer>describePair("age", 20);
```

Đây là các ví dụ minh họa (giả sử phương thức được đặt vào class `Demo`). Số đối số kiểu được truyền phải phù hợp với số type parameter của phương thức. Các type argument phải tuân thủ bound và ràng buộc kiểu.

Ví dụ, với:

```java
static <T extends Number> double asDouble(T number) {
    return number.doubleValue();
}
```

thì `Demo.<Integer>asDouble(7)` hợp lệ, nhưng `Demo.<String>asDouble("7")` **không biên dịch**: `String` không thỏa ràng buộc `T extends Number`.

Type argument tường minh phải là **kiểu tham chiếu** như `Integer`, `String` hoặc `Number`, không phải primitive `int`, `double`. Khi truyền literal primitive `7` vào một tham số kiểu `Integer`, Java có thể dùng autoboxing.

## 3. Type inference và target typing hoạt động ra sao?

**Type inference** là quá trình compiler tìm type argument phù hợp từ các thông tin tại lời gọi. Với Java 17, thông tin ấy có thể bao gồm:

- Kiểu tĩnh của các đối số thực được truyền cho phương thức.
- Ràng buộc trên các type parameter như `<T extends Number>`.
- Kiểu mong đợi của ngữ cảnh, chẳng hạn phép gán, tham số của lời gọi bao quanh hoặc return context phù hợp.
- Quan hệ giữa các type parameter và wildcard ở khai báo phương thức.

Ví dụ:

```java
List<String> first = Collections.emptyList();
List<Integer> second = Collections.emptyList();
```

Ở hai phép gán này, compiler có đủ **target type** từ phía bên trái để suy luận `T = String` và `T = Integer` mà không cần viết `Collections.<String>emptyList()` hoặc `Collections.<Integer>emptyList()`.

Vì vậy, khi lời gọi đã ngắn và kiểu mong muốn rõ ràng, cách viết tường minh thường **không cần thiết**. Việc thêm `<T>` chỉ hữu ích khi nó nêu được một quyết định kiểu đáng chú ý.

Ngược lại, nếu dùng `var`, kiểu của biến đến từ kiểu tĩnh của biểu thức khởi tạo; không có kiểu khai báo ở vế trái như `List<String>` để định hướng suy luận:

```java
var inferred = Collections.emptyList();          // List<Object>
var explicit = Collections.<String>emptyList(); // List<String>
```

Các dòng trên là ví dụ minh họa trong một phương thức Java 17. Với lời gọi `emptyList()` không có đối số dữ liệu, compiler thường suy ra `Object` khi không có target type cụ thể. Explicit type argument là cách diễn đạt chủ ý `List<String>` ngay trong biểu thức khởi tạo.

Tuy nhiên, việc `emptyList()` trả về `List<String>` **không làm danh sách thành mutable**. Danh sách trống trả từ `Collections.emptyList()` không cho phép thêm phần tử. Nếu cần một danh sách sửa được, hãy tạo một `ArrayList` mới từ nó.

## 4. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`ExplicitTypeArgumentsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExplicitTypeArgumentsDemo {
    static <T> T chooseFirst(T first, T second) {
        return first;
    }

    static <T> List<T> copy(List<? extends T> source) {
        return new ArrayList<>(source);
    }

    static <T extends Number> double asDouble(T value) {
        return value.doubleValue();
    }

    static final class Printer {
        <T> String describe(T value) {
            return "value=" + value;
        }
    }

    public static void main(String[] args) {
        Number chosen = ExplicitTypeArgumentsDemo.<Number>chooseFirst(10, 2.5);
        System.out.println("chosen: " + chosen);

        List<Integer> integers = List.of(1, 2);
        List<Number> copied = ExplicitTypeArgumentsDemo.<Number>copy(integers);
        System.out.println("copied: " + copied);

        List<String> empty = Collections.<String>emptyList();
        System.out.println("empty size: " + empty.size());

        var texts = new ArrayList<>(Collections.<String>emptyList());
        texts.add("Java");
        System.out.println("texts: " + texts);

        String info = new Printer().<String>describe("generic");
        System.out.println("instance: " + info);

        System.out.println("as double: "
                + ExplicitTypeArgumentsDemo.<Integer>asDouble(7));

        List<Number> inferred = ExplicitTypeArgumentsDemo.copy(List.of(3, 4.5));
        System.out.println("inferred: " + inferred);
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac ExplicitTypeArgumentsDemo.java
java ExplicitTypeArgumentsDemo
```

**Stdout chính xác:**

```text
chosen: 10
copied: [1, 2]
empty size: 0
texts: [Java]
instance: value=generic
as double: 7.0
inferred: [3, 4.5]
```

### Giải thích kết quả

1. `<Number>chooseFirst(10, 2.5)` nói rõ `T = Number`. Phần tử đầu là số nguyên `10`, nên `System.out.println` in `10`, dù kiểu tĩnh của biến `chosen` là `Number`.
2. `<Number>copy(integers)` tạo `List<Number>` mới từ `List<Integer>`. Chữ ký `List<? extends T>` cho phép đọc `Integer` như `Number`. Đây là **sao chép cấu trúc danh sách** (shallow copy đối với phần tử tham chiếu), không tạo danh sách gốc kiểu `List<Number>`.
3. `Collections.<String>emptyList()` chỉ rõ kiểu phần tử của kết quả, còn `size()` bằng 0 vì danh sách trống.
4. `new ArrayList<>(Collections.<String>emptyList())` tạo danh sách mới có thể sửa, đồng thời giữ kiểu `String`. `texts.add("Java")` làm danh sách thành `[Java]`.
5. `new Printer().<String>describe("generic")` minh họa cách đặt type argument trên **lời gọi instance method**.
6. `<Integer>asDouble(7)` thỏa `T extends Number` và gọi `doubleValue()` trên đối tượng `Integer` nên in `7.0`.
7. `copy(List.of(3, 4.5))` không có explicit type argument; compiler suy luận kiểu tương thích với `List<Number> inferred`. Đây là một trường hợp nên **ưu tiên để Java suy luận** vì kết quả mong muốn đã hiện rõ ở kiểu biến nhận.

Ví dụ dùng Java 17, không cần thư viện bên ngoài JDK. `Integer`, `Number`, `String` thuộc `java.lang` nên không cần import.

## 5. Khi nào đối số kiểu tường minh thực sự giúp ích?

### 5.1. Không có thông tin kiểu từ đối số dữ liệu

`Collections.emptyList()` không nhận đối số dữ liệu; khi ngữ cảnh không cung cấp kiểu mong đợi, type argument tường minh là cách rõ ràng nhất để khẳng định ý định:

```java
var names = Collections.<String>emptyList();
```

Compiler có kiểu `List<String>` ngay từ biểu thức, thay vì suy ra danh sách của kiểu tổng quát hơn. Đây là ví dụ minh họa trong phương thức Java 17; `names` trỏ tới danh sách trống không sửa được.

### 5.2. Muốn nhìn thấy kiểu kết quả ở ngay lời gọi

Giả sử có phương thức:

```java
static <T> List<T> copy(List<? extends T> source) {
    return new ArrayList<>(source);
}
```

Đối số nguồn có thể là `List<Integer>` nhưng bạn cần kết quả được khai báo là `List<Number>`:

```java
List<Integer> source = List.of(1, 2);
List<Number> numbers = ExplicitTypeArgumentsDemo.<Number>copy(source);
```

Cú pháp `<Number>` cho người đọc biết **muốn mở rộng kiểu phần tử của bản sao thành `Number`**. Biến `numbers` có thể thêm `Double` sau này vì `copy` trả về `ArrayList<Number>`, nếu thao tác thêm phù hợp với logic ứng dụng:

```java
numbers.add(2.5);
System.out.println(numbers); // [1, 2, 2.5]
```

Đoạn code trên là ví dụ bổ sung trong một phương thức. **Nguồn `source` vẫn là `List<Integer>`** và không chứa `Double`. Kiểu tường minh giúp chọn kiểu trả về phù hợp; nó không ép kiểu không an toàn cho nguồn.

### 5.3. Muốn ràng buộc một lựa chọn kiểu đáng chú ý

Khi generic method có nhiều đối số thuộc các subtype khác nhau:

```java
Number chosen = ExplicitTypeArgumentsDemo.<Number>chooseFirst(1, 2.5);
```

`<Number>` thể hiện rằng caller muốn xử lý hai giá trị trong khuôn khổ `Number`. Đây là lựa chọn hữu ích về **tính rõ ràng**, nhất là khi kiểu suy luận mặc định có thể phức tạp. Dù vậy, bản thân `<Number>` không làm phép chuyển đổi số học từ `Integer` sang `Double` hay ngược lại.

### 5.4. Khi API dạng chuỗi làm mờ target type

Trong các biểu thức lồng nhau hoặc fluent chain, người đọc có thể phải suy luận qua nhiều lời gọi. Một explicit type argument đúng chỗ làm rõ kiểu ngay tại nguồn tạo dữ liệu:

```java
var mutableNames =
        new ArrayList<>(Collections.<String>emptyList());
mutableNames.add("Lan");
```

Cú pháp này rõ về `String` mà không cần thêm một khai báo type đầy đủ ở vế trái. Đừng rải type arguments ở mọi lời gọi trong chuỗi; chỉ đặt tại bước mà nó thực sự diễn đạt ý định hoặc giải quyết lỗi suy luận.

## 6. Đối số kiểu tường minh không thể ép một lời gọi sai thành đúng

**Quy tắc:** viết `<T>` chỉ định type parameter; Java vẫn kiểm tra kiểu của **mọi đối số giá trị**, kiểu trả về, generic bounds và overload applicability.

Ví dụ lỗi biên dịch, đã chú thích:

```java
// Không thể gán List<Integer> vào List<Number>:
List<Integer> integers = List.of(1, 2);
// List<Number> invalid = integers;

// T = Number không cho phép một danh sách String làm nguồn:
// List<Number> invalidCopy =
//         ExplicitTypeArgumentsDemo.<Number>copy(List.of("a"));

// T = String vi phạm giới hạn T extends Number:
// double invalidNumber =
//         ExplicitTypeArgumentsDemo.<String>asDouble("7");

// T = Integer khiến kết quả là List<Integer>, không phải List<Number>:
// List<Number> invalidResult =
//         ExplicitTypeArgumentsDemo.<Integer>copy(integers);
```

Từng dòng lỗi phải được hiểu qua chữ ký generic và tính bất biến của `List<T>`. Không có “type witness thần kỳ” nào đổi được một `List<Integer>` thành `List<Number>` bằng phép gán trực tiếp.

### Type argument không phải cast

- `(Number) expression` là cú pháp **cast** một biểu thức sang kiểu mục tiêu.
- `Demo.<Number>method(...)` là cú pháp **truyền type argument cho generic method**; compiler kiểm tra chữ ký với `T = Number`.
- `new ArrayList<Number>(source)` là **tạo đối tượng danh sách mới** có type argument `Number` ở constructor/class, khác lời gọi generic method.

Type argument có thể tác động đến các ràng buộc kiểu của lời gọi, nhưng nó không tạo thêm đối tượng, không tự thực hiện chuyển đổi dữ liệu và không làm biến mất lỗi runtime của code trong thân phương thức.

### Không có type parameter thì không có gì để chỉ định

Không thể viết một `<String>` tùy ý lên phương thức **không generic** rồi mong phương thức nhận kiểu mới. Hãy xem chữ ký khai báo của method: chỉ các type parameters do generic method cung cấp mới có ý nghĩa trong lời gọi type-witness tương ứng.

Nếu class là `Box<String>` nhưng phương thức `get()` không khai báo type parameter riêng, thì kiểu `String` của kết quả đến từ **class parameter**, chứ không có nghĩa bạn cần viết `box.<String>get()`.

## 7. Quan hệ với quá tải phương thức (overloading)

Một lớp có thể khai báo nhiều phương thức trùng tên nhưng khác danh sách tham số. Việc lựa chọn overload còn dựa vào **kiểu và số đối số**, khả năng áp dụng của các chữ ký, boxing, varargs và quy tắc “more specific” của Java.

Explicit type argument có thể ảnh hưởng đến việc một **ứng viên generic** có áp dụng được hay không, nhưng **không phải cú pháp chọn bất kỳ overload nào theo ý muốn**. Để hiểu lời gọi nào được chọn, cần đọc tất cả các chữ ký cùng tên và áp dụng quy tắc overload resolution.

Đặc biệt, đừng cố dùng `<T>` như cách giải quyết một overload mơ hồ mà chưa hiểu vì sao mơ hồ. Có những trường hợp phải **thay đổi kiểu của đối số biểu thức**, dùng API có tên rõ ràng hơn hoặc bổ sung thông tin loại khác. Không nên đánh đồng việc chỉ rõ type parameter với thao tác cast.

## 8. Liên hệ với wildcard và phương thức generic

Một generic method thường kết hợp type parameter và wildcard:

```java
static <T> List<T> copy(List<? extends T> source) {
    return new ArrayList<>(source);
}
```

Ở đây:

- `<T>` khai báo một type parameter của **phương thức**.
- `? extends T` nói rằng phần tử nguồn thuộc một subtype (chưa biết chính xác) của `T`.
- `List<T>` là kiểu trả về, nên caller có thể muốn quyết định chính xác `T`.

Lời gọi:

```java
List<Integer> input = List.of(1, 2);
List<Number> result = ExplicitTypeArgumentsDemo.<Number>copy(input);
```

là hợp lệ vì `Integer extends Number`. Trái lại:

```java
// List<String> invalid =
//         ExplicitTypeArgumentsDemo.<String>copy(input);
```

không hợp lệ: `Integer` không phải subtype của `String`.

Đây là ví dụ tốt cho thấy explicit type argument giúp diễn đạt mục tiêu, còn wildcard giúp **giữ an toàn kiểu giữa producer và kiểu kết quả**. Tuy nhiên, khi có target type đủ rõ, `List<Number> result = ExplicitTypeArgumentsDemo.copy(input);` cũng có thể được compiler suy luận thành công. Sự khác biệt chủ yếu ở việc bạn có muốn quyết định `Number` xuất hiện ngay tại vị trí gọi hay không.

## 9. Những lỗi thường gặp

**Nhầm cú pháp khai báo và cú pháp gọi.** `static <T> T f(T x)` là khai báo; `ClassName.<String>f("a")` là lời gọi. Không viết `<String>` trước toàn bộ tên class một cách tùy tiện.

**Chỉ định kiểu primitive.** `<int>` và `<double>` không phải type arguments hợp lệ của generics. Dùng `Integer`, `Double` khi cần.

**Cho rằng explicit type argument làm bất biến generic thành hiệp biến.** `List<Integer>` vẫn không phải `List<Number>`. Nếu cần tạo danh sách mới có kiểu `Number`, phải dùng một cơ chế phù hợp như generic `copy` ở ví dụ; không dùng phép gán trực tiếp.

**Tin rằng `<T>` có thể bỏ qua bound.** `<String>asDouble(...)` sai nếu method khai báo `T extends Number`.

**Lặp type argument không cần thiết.** `List<String> x = Collections.<String>emptyList()` đúng, nhưng `List<String> x = Collections.emptyList()` cũng rõ ràng và thường gọn hơn nhờ target typing.

**Nhầm loại collection.** `Collections.<String>emptyList()` mang kiểu `List<String>` nhưng không thêm được phần tử; an toàn kiểu không đồng nghĩa mutability.

**Nhầm type argument với runtime type token.** `<String>` không khiến Java giữ lại `Class<String>` để dùng reflection hoặc phân biệt specialization ở runtime. Generics Java chủ yếu được triển khai qua type erasure; muốn method nhận thông tin `Class<T>` ở runtime, API phải khai báo tham số kiểu `Class<T>` hoặc cơ chế tương ứng.

**Dùng type witness để che thiết kế API khó hiểu.** Nếu phải chèn type argument quá nhiều, hãy cân nhắc điều chỉnh chữ ký, kiểu trung gian, tên phương thức hoặc cấu trúc biểu thức cho dễ đọc hơn.

## 10. Tự kiểm tra

**Câu 1.** Hai lời gọi `Collections.emptyList()` và `Collections.<String>emptyList()` khác nhau ở đâu?

Lời gọi thứ hai chỉ định tường minh `T = String`. Nếu ngữ cảnh như `List<String> x = ...` đã đủ suy luận, cả hai có thể cho cùng kiểu kết quả tại compile time.

**Câu 2.** `var items = Collections.<Integer>emptyList();` cho `items` kiểu gì?

Compiler suy ra kiểu `List<Integer>` của biểu thức khởi tạo, nên `items` có kiểu tĩnh `List<Integer>`. Danh sách ấy vẫn không sửa được.

**Câu 3.** Tại sao `<Number>copy(List<Integer>)` hợp lệ?

Vì source khai báo `List<? extends Number>` sau khi chọn `T = Number`, và `Integer` là subtype của `Number`. Hàm trả một `List<Number>` mới.

**Câu 4.** Có viết `SomeClass.<int>genericMethod(1)` được không?

Không. Generics cần type argument là kiểu tham chiếu, ví dụ `Integer`. Literal `1` có thể được autobox sang `Integer` nếu chữ ký yêu cầu.

**Câu 5.** `Demo.<String>asDouble("7")` có qua được compiler nếu method khai báo `<T extends Number>` không?

Không, vì `String` không thỏa giới hạn kiểu `Number`.

**Câu 6.** Khi nào nên ưu tiên suy luận tự động?

Khi compiler suy luận đúng, các kiểu mong đợi đã rõ từ tham số và target type, và thêm `<T>` không đem lại thông tin đọc hiểu quan trọng.

## Ghi nhớ

Cú pháp `Receiver.<TypeArg>genericMethod(args)` cho phép **chỉ định đối số kiểu của generic method tại nơi gọi**. Nó làm rõ kiểu `T` mong muốn, nhất là khi không có target type hoặc khi muốn chọn kiểu kết quả rộng hơn một cách có chủ đích. Compiler vẫn áp dụng đầy đủ các ràng buộc kiểu, generic bounds và overload resolution. Nếu suy luận mặc định đã rõ ràng, hãy để Java tự suy luận; chỉ viết tường minh khi cách đó giúp biểu đạt ý nghĩa hoặc xử lý tình huống suy luận cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-005">Suy luận với nhiều đối số có các kiểu khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Suy luận kiểu với nhiều đối số có các kiểu khác nhau trong Java Generics

**Suy luận kiểu (type inference)** là quá trình trình biên dịch Java xác định các đối số kiểu generic khi chương trình không ghi chúng tường minh. Khi một lời gọi phương thức có **nhiều đối số khác kiểu**, compiler không đơn giản chọn kiểu của đối số đầu tiên: nó thu thập ràng buộc từ tất cả các đối số, các cận `extends`, cấu trúc generic và cả ngữ cảnh mong đợi của biểu thức, rồi tìm một cách gán tham số kiểu hợp lệ.

Ví dụ:

```java
static <T> T first(T a, T b) {
    return a;
}

Number result = first(10, 2.5);
```

`10` được boxing thành `Integer`, còn `2.5` thành `Double`. Hai kiểu này đều kế thừa `Number`, vì vậy compiler có thể suy luận một kiểu chung tương thích với cả hai và với ngữ cảnh `Number result`. Tuy nhiên, việc hai kiểu có chung kiểu cha **không có nghĩa** có thể truyền một `List<Integer>` và một `List<Double>` cho bất kỳ phương thức `<T>` nào nhận hai `List<T>`.

Bài này giải thích cách suy luận hoạt động, sự khác nhau giữa kiểu của giá trị và đối số kiểu của container, ảnh hưởng của kiểu kết quả, wildcard, cận kiểu và cách thiết kế chữ ký để nhiều đối số khác kiểu vẫn an toàn.

## 1. Suy luận bắt đầu từ chữ ký generic và các đối số

Một phương thức generic khai báo tham số kiểu trước kiểu trả về:

```java
static <T> T chooseFirst(T first, T second) {
    return first;
}
```

Trong lời gọi `chooseFirst("a", "b")`, cả hai đối số đều là `String`, nên compiler có thể dùng `T = String`. Với `chooseFirst(10, 20)`, boxing tạo ra hai `Integer` và `T` có thể là `Integer`.

Với `chooseFirst(10, 2.5)`, hai kiểu đầu vào là `Integer` và `Double`. Lời gọi vẫn có thể hợp lệ vì compiler tìm kiểu chung sao cho:

```text
Integer  <: T
Double   <: T
```

Ký hiệu `A <: B` nghĩa là `A` là subtype của `B`. Trong ví dụ, `Number` là một lựa chọn kiểu cha chung phù hợp.

**Không nên ghi nhớ rằng Java luôn chọn chính xác `Number` hay `Object` cho mọi cặp kiểu.** Với nhiều kiểu tham chiếu, kiểu chung nhỏ nhất (*least upper bound*, LUB) có thể bao gồm những interface và kiểu giao (*intersection type*) phức tạp. Ngữ cảnh gán hoặc cận của phương thức còn có thể làm kết quả suy luận khác nhau. Điều cần biết là compiler sẽ kiểm tra toàn bộ ràng buộc theo quy tắc của ngôn ngữ, thay vì chọn kiểu bằng cách nhìn một đối số riêng lẻ.

## 2. Ví dụ Java 17: hai đối số khác kiểu vẫn gọi được cùng một `<T>`

Lưu tệp `MixedArgumentsDemo.java`:

```java
public class MixedArgumentsDemo {
    static <T> T chooseFirst(T first, T second) {
        return first;
    }

    static <T> String describe(T first, T second) {
        return first.getClass().getSimpleName()
                + " + "
                + second.getClass().getSimpleName();
    }

    public static void main(String[] args) {
        Number number = chooseFirst(10, 2.5);
        Object object = chooseFirst("Java", 17);

        System.out.println(number);
        System.out.println(object);
        System.out.println(describe(10, 2.5));
        System.out.println(describe("Java", 17));
    }
}
```

Biên dịch/chạy bằng Java 17 trở lên:

```bash
javac MixedArgumentsDemo.java
java MixedArgumentsDemo
```

Đầu ra:

```text
10
Java
Integer + Double
String + Integer
```

Giải thích:

- `Number number = chooseFirst(10, 2.5)` hợp lệ: hai đối số khác class vẫn chuyển được tới một kiểu chung đáp ứng ngữ cảnh `Number`.
- `Object object = chooseFirst("Java", 17)` hợp lệ vì cả `String` và `Integer` đều là kiểu tham chiếu và có thể xem như `Object`.
- `describe` dùng các phương thức của `Object` là `getClass()`, nên không yêu cầu phải biết cụ thể `T` là `Integer`, `Double` hay `String`.
- Chương trình in kiểu runtime của từng **đối tượng**, không in chính xác kiểu `T` compiler suy luận. Generic không tạo ra một class runtime riêng cho `T`.

Hai đối số khác kiểu không có nghĩa mọi phép toán dành cho kiểu con đều hợp lệ. Bên trong `describe`, compiler không thể tùy tiện gọi `first.intValue()` hoặc `first.length()` vì `T` không bị ràng buộc lần lượt bởi `Number` hay `String`.

## 3. Từ nhiều đối số đến kiểu chung: quan hệ “cùng dùng được như `T`”

Với chữ ký `<T> void handle(T a, T b)`, compiler cần tìm một `T` mà **cả hai giá trị** có thể truyền vào. Giá trị của class con có thể tự chuyển lên class cha hoặc interface phù hợp tại vị trí tham số.

Ví dụ:

```java
static <T> void printPair(T a, T b) {
    System.out.println(a + " | " + b);
}
```

Các lời gọi hợp lệ về kiểu:

```java
printPair(1, 2);           // Hai Integer sau boxing.
printPair(1, 2.5);         // Integer và Double.
printPair("hello", 2026);  // String và Integer.
```

Việc dùng cùng `T` **không bắt buộc các đối số có đúng cùng class runtime**. Một biến kiểu phương thức có thể đại diện cho kiểu cha chung của nhiều kiểu con.

Đối chiếu hai vấn đề khác nhau:

| Trường hợp | Compiler kiểm tra điều gì? |
| --- | --- |
| `<T> void f(T a, T b)` | Mỗi giá trị có thể chuyển đến một `T` chung |
| `<T> void f(List<T> a, List<T> b)` | Hai danh sách có đối số kiểu phù hợp với **cùng `T`** theo tính bất biến |
| `<T> void f(List<? extends T> a, List<? extends T> b)` | Kiểu phần tử của từng danh sách là kiểu con của `T`, có thể khác nhau |
| `<T> void f(List<? extends T> a, List<? super T> b)` | Nguồn đọc được dưới `T` và đích nhận được `T` |

Đây là điểm mấu chốt của suy luận với nhiều đối số: **ràng buộc phụ thuộc vị trí của `T` trong chữ ký**, không chỉ phụ thuộc class của các giá trị đầu vào.

## 4. Generic bất biến: vì sao `List<Integer>` và `List<Double>` không khớp hai `List<T>`?

Xét:

```java
static <T> void inspectSameLists(List<T> first, List<T> second) {
    System.out.println(first.size() + second.size());
}
```

Với hai danh sách cùng đối số kiểu:

```java
List<Integer> first = List.of(1, 2);
List<Integer> second = List.of(3);
inspectSameLists(first, second); // Hợp lệ: T = Integer.
```

Nhưng trường hợp sau không biên dịch:

```java
List<Integer> integers = List.of(1, 2);
List<Double> doubles = List.of(2.5, 3.5);

// inspectSameLists(integers, doubles); // LỖI BIÊN DỊCH.
```

Vì `List<T>` là bất biến, compiler không thể tùy ý dùng `T = Number` để xem `List<Integer>` thành `List<Number>`, hoặc xem `List<Double>` thành `List<Number>`.

Thông báo lỗi có thể nêu **inference variable `T` has incompatible equality constraints** hoặc các yêu cầu kiểu không thống nhất. Ý nghĩa là hai tham số `List<T>` đòi cùng một đối số kiểu, trong khi caller cung cấp hai tham số hóa khác nhau.

Không sửa bằng:

```java
// List<Number> numbers = (List<Number>) (List<?>) integers;
```

Cách ép kiểu vòng qua wildcard có thể gây cảnh báo `unchecked` và phá an toàn kiểu. Một danh sách `Integer` có thể bị thêm `Double` qua tham chiếu bị ép sai, khiến lỗi xuất hiện muộn khi đọc.

**Cách thiết kế đúng** phụ thuộc mục tiêu: nếu chỉ muốn đọc hai nguồn cùng nhóm kiểu, dùng wildcard `? extends T` ở từng nguồn; nếu cần trao đổi dữ liệu qua lại giữa hai danh sách **cùng kiểu phần tử**, giữ `List<T>` để compiler từ chối nguồn không tương thích.

## 5. Wildcard `? extends T` cho phép suy luận từ nhiều nguồn khác kiểu

Khi thuật toán chỉ **đọc** phần tử từ hai danh sách, thay vì yêu cầu cả hai chính xác `List<T>`, ta có thể viết:

```java
static <T> List<T> merge(
        List<? extends T> first,
        List<? extends T> second) {
    List<T> result = new ArrayList<>();
    result.addAll(first);
    result.addAll(second);
    return result;
}
```

Compiler cần tìm một `T` sao cho:

```text
ElementType(first)  <: T
ElementType(second) <: T
```

Mỗi nguồn có wildcard **riêng**, nên một nguồn có thể là `List<Integer>` và nguồn kia là `List<Double>`. Chúng cùng cung cấp dữ liệu đọc được như `Number`.

### Chương trình hoàn chỉnh

Lưu thành `MergeDifferentTypes.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class MergeDifferentTypes {
    static <T> List<T> merge(
            List<? extends T> first,
            List<? extends T> second) {
        List<T> result = new ArrayList<>();
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);
        List<Double> doubles = List.of(2.5, 3.5);

        List<Number> numbers = merge(integers, doubles);
        System.out.println(numbers);

        List<Object> objects = merge(
                List.of("Java"),
                List.of(17));
        System.out.println(objects);

        // List<Integer> invalid = merge(integers, doubles);
        // Lỗi: kết quả Integer không nhận được Double.
    }
}
```

```bash
javac MergeDifferentTypes.java
java MergeDifferentTypes
```

Đầu ra:

```text
[1, 2, 2.5, 3.5]
[Java, 17]
```

Ngữ cảnh `List<Number> numbers = ...` giúp ràng buộc kiểu kết quả là `List<Number>`; hai danh sách nguồn có phần tử là kiểu con của `Number`. Tương tự, `List<Object>` chứa được cả `String` và `Integer`.

**Giới hạn của phép đọc từ `? extends T`:** không được tùy ý thêm một đối tượng `T` vào `first` hoặc `second`. Ví dụ `first.add((T) someValue)` là cách làm không an toàn; chúng có thể thực sự là `List<Integer>` hoặc `List<Double>` riêng biệt. Ở ví dụ, chỉ `result` là `List<T>` mới được phép nhận các phần tử của cả hai nguồn.

## 6. Kết hợp producer và consumer để suy luận qua nhiều đối số

PECS (*Producer Extends, Consumer Super*) giúp diễn đạt **chiều di chuyển dữ liệu**. Một phương thức nhận hai nguồn khác kiểu và một đích chung có thể viết:

```java
static <T> void appendAll(
        List<? extends T> left,
        List<? extends T> right,
        List<? super T> destination) {
    destination.addAll(left);
    destination.addAll(right);
}
```

Ràng buộc đối với compiler:

- Kiểu phần tử của `left` phải phù hợp với `T` hoặc kiểu con của `T`.
- Kiểu phần tử của `right` phải phù hợp với `T` hoặc kiểu con của `T`.
- Đích phải có khả năng nhận giá trị `T` theo hệ kiểu.

Vì thế:

```java
List<Integer> ints = List.of(1, 2);
List<Double> doubles = List.of(2.5, 3.5);
List<Number> output = new ArrayList<>();

appendAll(ints, doubles, output); // Hợp lệ.

// List<Integer> onlyInts = new ArrayList<>();
// appendAll(ints, doubles, onlyInts); // Không biên dịch.
```

Compiler có thể chọn `T = Number` cho trường hợp đầu. Trong trường hợp đích `List<Integer>`, không tồn tại `T` vừa có thể nhận nguồn `Double` vừa có thể thêm an toàn vào đích chỉ nhận `Integer`.

### Ví dụ Java 17 chạy được

Lưu thành `MultipleInputPecs.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class MultipleInputPecs {
    static <T> void appendAll(
            List<? extends T> first,
            List<? extends T> second,
            List<? super T> destination) {
        destination.addAll(first);
        destination.addAll(second);
    }

    public static void main(String[] args) {
        List<Number> numbers = new ArrayList<>();
        appendAll(List.of(1, 2), List.of(3.5, 4.5), numbers);
        System.out.println(numbers);

        List<Object> objects = new ArrayList<>();
        appendAll(List.of("A"), List.of(7), objects);
        System.out.println(objects);

        List<Integer> integers = new ArrayList<>();
        appendAll(List.of(1), List.of(2), integers);
        System.out.println(integers);
    }
}
```

```bash
javac MultipleInputPecs.java
java MultipleInputPecs
```

Đầu ra:

```text
[1, 2, 3.5, 4.5]
[A, 7]
[1, 2]
```

Đích dùng `ArrayList` nên cho phép `addAll`. Wildcard `? super T` chỉ bảo đảm **an toàn kiểu của phần tử được thêm**, không tự bảo đảm đích có thể sửa đổi: một collection không sửa đổi có thể ném `UnsupportedOperationException`.

## 7. Kiểu đích là một ràng buộc khác của lời gọi nhiều đối số

Khi lời gọi generic trả `List<T>`, ngữ cảnh gán cũng có thể ràng buộc T, ngoài các điều kiện từ đối số:

```java
static <T> List<T> one(T value) {
    return new ArrayList<>(List.of(value));
}

List<Integer> a = one(10);
List<Number> b = one(10);
List<Object> c = one(10);
// List<String> wrong = one(10); // Mâu thuẫn Integer và String.
```

Cùng một Integer có thể đi vào những lần gọi có T khác nhau, nhưng một biến `List<Integer>` đã được khai báo không tự chuyển thành `List<Number>`. Khi có **nhiều đối số khác loại**, kiểu đích phải được kiểm tra cùng ràng buộc từ tất cả đầu vào; không thể dùng nó để buộc một Double thành Integer.

## 8. `var` giữ kiểu được suy luận tại chính initializer

```java
var inferred = one(10);        // List<Integer>.
List<Number> wider = one(10);   // List<Number>.
```

`var` không truyền kiểu đích Number xuống initializer và không tự nới `inferred` thành `List<Number>` khi có một lệnh dùng nó về sau. Nếu kết quả cần chứa nhiều subtype Number, hãy biểu đạt contract ngay trong khai báo hoặc type witness hợp lệ. Trong bài này, điểm chính vẫn là **tập hợp nhiều lower/equality constraints từ các đối số**, không phải cú pháp `var`: đọc kiểu của `List<T>`, `List<? extends T>` và `List<? super T>` trước khi lựa chọn cách sửa.

## 9. Boxing, primitive và sự pha trộn kiểu số

Tham số kiểu generic chỉ nhận **kiểu tham chiếu**, không nhận primitive như `int` hoặc `double`. Vì thế:

```java
static <T> T first(T a, T b) {
    return a;
}

Number n = first(10, 2.5);
```

các biểu thức số primitive được **boxing** thành `Integer` và `Double` khi tham gia generic. Compiler sau đó xét ràng buộc kiểu tham chiếu.

Điều này khác với việc áp dụng phép toán cộng primitive:

```java
double sum = 10 + 2.5; // Chuyển đổi số primitive, kết quả 12.5.
```

Generic không tự áp dụng `Integer + Double` cho mọi `T`. Nếu cần tính tổng của nhiều kiểu số, hãy thêm cận cho tham số kiểu:

```java
static <T extends Number> double sum(T first, T second) {
    return first.doubleValue() + second.doubleValue();
}
```

Khi ấy `sum(10, 2.5)` có thể biên dịch vì cả `Integer` và `Double` thuộc họ `Number`, và compiler biết có thể gọi `doubleValue()` trên `T`.

Một ví dụ hoàn chỉnh:

```java
public class BoundedInferenceDemo {
    static <T extends Number> double sum(T a, T b) {
        return a.doubleValue() + b.doubleValue();
    }

    public static void main(String[] args) {
        System.out.println(sum(10, 2.5));
        System.out.println(sum(1L, 3));

        // sum("10", 2.5); // Không biên dịch: String không extends Number.
    }
}
```

Lưu thành `BoundedInferenceDemo.java`:

```bash
javac BoundedInferenceDemo.java
java BoundedInferenceDemo
```

Đầu ra:

```text
12.5
4.0
```

Cận `T extends Number` không buộc hai đối số có cùng subclass; nó chỉ buộc kiểu chung được suy luận phải nằm trong họ `Number`. Đồng thời nó loại những lời gọi chứa đối số không phù hợp như `String`.

**Giới hạn số học:** `Number.doubleValue()` có thể mất độ chính xác với kiểu số lớn hoặc khi tính toán tài chính. Ví dụ tập trung minh họa suy luận kiểu, không thay thế các quy tắc về số học chính xác hay `BigDecimal` trong ứng dụng thật.

## 10. Nhiều cận có thể khiến suy luận không có nghiệm

Với:

```java
static <T extends Number> T choose(T first, T second) {
    return first;
}
```

Lời gọi:

```java
Number n = choose(10, 2.5); // Hợp lệ.
```

có thể suy luận `T` phù hợp với cận `Number`. Nhưng:

```java
// String s = choose("text", 2.5); // Không biên dịch.
```

vì `String` không phải subtype của `Number`. Compiler không thể bỏ qua một đối số để tìm một kiểu phù hợp cho các đối số còn lại.

Tương tự, yêu cầu kiểu trả về quá hẹp có thể khiến hệ ràng buộc vô nghiệm:

```java
// Integer impossible = choose(10, 2.5); // Không biên dịch.
```

Một `Double` không thể tự chuyển thành `Integer` chỉ nhờ tham số kiểu generic; suy luận kiểu không làm chuyển đổi số theo ý muốn.

Khi gặp lỗi “incompatible bounds” hoặc “inference variable has incompatible bounds”, hãy phân tích toàn bộ yêu cầu:

1. Đối số thực tế cung cấp kiểu nào sau boxing nếu cần?
2. Tham số kiểu có cận `extends` hoặc các cận giao nào?
3. `T` xuất hiện ở vị trí giá trị đơn, `List<T>`, `? extends T` hay `? super T`?
4. Ngữ cảnh sử dụng kết quả có đòi một kiểu cụ thể không?
5. Có tồn tại một `T` thỏa tất cả các điều kiện cùng lúc không?

Phản hồi compiler phụ thuộc chữ ký cụ thể; đừng giả định mọi cặp đối số khác kiểu đều sẽ suy luận về `Object` thành công.

## 11. Hai wildcard giống nhau không có nghĩa hai đối số có cùng kiểu phần tử

Xét:

```java
static void printTwo(
        List<? extends Number> first,
        List<? extends Number> second) {
    Number a = first.get(0);
    Number b = second.get(0);
    System.out.println(a + " " + b);
}
```

Một lời gọi với `List<Integer>` và `List<Double>` được chấp nhận, vì mỗi danh sách đều có phần tử có thể xem như `Number`. Nhưng wildcard thứ nhất và wildcard thứ hai có thể được compiler **capture thành hai kiểu khác nhau**.

Vì vậy đoạn này không hợp lệ:

```java
static void wrongCopy(
        List<? extends Number> first,
        List<? extends Number> second) {
    // second.add(first.get(0)); // Không biên dịch.
}
```

`first` có thể là `List<Integer>`, `second` là `List<Double>`. Việc chúng có cùng cận trên `Number` không làm kiểu capture của chúng đồng nhất.

Muốn chép giá trị từ nguồn sang đích, đặt ràng buộc cho nơi nhận:

```java
static <T> void copyValues(
        List<? extends T> source,
        List<? super T> destination) {
    destination.addAll(source);
}
```

Hoặc, nếu cần **đúng cùng đối số kiểu** và phải ghi qua lại giữa hai danh sách, dùng `List<T>` cho cả hai tham số. Điều quyết định là mối quan hệ kiểu mà API muốn biểu diễn.

## 12. Phân biệt kiểu chung khi gán giá trị với quan hệ kiểu của container

Hai ví dụ sau phản ánh hai lớp kiểm tra khác nhau:

**Các giá trị đơn:**

```java
static <T> void accept(T a, T b) { }

accept(1, 2.5); // Hợp lệ: compiler tìm kiểu chung.
```

**Các container bất biến:**

```java
static <T> void acceptLists(List<T> a, List<T> b) { }

List<Integer> ints = List.of(1);
List<Double> doubles = List.of(2.5);

// acceptLists(ints, doubles); // Không biên dịch.
```

Không thể lập luận “`Integer extends Number` và `Double extends Number`, vậy cả hai `List` tự trở thành `List<Number>`”. Java generic không hiệp biến theo đối số kiểu.

Tuy nhiên:

```java
static <T> void acceptSources(
        List<? extends T> a,
        List<? extends T> b) { }

acceptSources(ints, doubles); // Hợp lệ.
```

Vì lời hứa giờ đây chỉ yêu cầu hai nguồn cung cấp giá trị mà thuật toán có thể xem như `T`. Compiler có thể chọn một kiểu chung để thỏa cả hai ràng buộc, mà không giả vờ hai danh sách có cùng đối số kiểu.

Các đoạn trong mục này minh họa cấu trúc chữ ký; cần đặt trong một class, có `import java.util.List;` và một `main` nếu muốn biên dịch/chạy độc lập.

## 13. Khi nào cần khai báo kiểu tường minh tại lời gọi?

Java hỗ trợ cung cấp đối số kiểu cho **generic method** trước tên phương thức, ví dụ:

```java
List<Number> numbers = MergeDifferentTypes.<Number>merge(
        List.of(1, 2),
        List.of(2.5, 3.5));
```

Đây là cú pháp minh họa khi phương thức `merge` là phương thức static trong class `MergeDifferentTypes` đã định nghĩa ở mục 5. `<Number>` yêu cầu compiler dùng `T = Number` trong lời gọi đó.

Trong phần lớn trường hợp, compiler tự suy luận đủ tốt và ta chỉ cần:

```java
List<Number> numbers = MergeDifferentTypes.merge(
        List.of(1, 2),
        List.of(2.5, 3.5));
```

Khai báo kiểu tường minh hữu ích khi muốn chỉ rõ ý định API hoặc cần giải quyết tình huống ngữ cảnh suy luận không diễn đạt được mong muốn. Nó **không** cho phép bỏ qua lỗi an toàn kiểu:

```java
// List<Integer> wrong = MergeDifferentTypes.<Integer>merge(
//         List.of(1), List.of(2.5));
// Không biên dịch: Double không thỏa ? extends Integer.
```

Cần phân biệt đối số kiểu tường minh với **cast**: `<Number>` đưa ràng buộc cho compiler kiểm tra từ đầu; nó không biến một đối tượng `Double` thành `Integer`, không bỏ kiểm tra an toàn generic và không thể cứu một tập ràng buộc vô nghiệm.

## 14. Những lỗi thường gặp khi suy luận nhiều kiểu

### Lỗi 1: nghĩ compiler chỉ nhìn đối số đầu tiên

```java
static <T> T first(T a, T b) {
    return a;
}

Number value = first(10, 2.5);
```

Compiler xét cả hai đối số và ngữ cảnh kết quả. Nó không “khóa” `T` thành `Integer` ngay khi thấy `10` rồi bỏ qua `Double`.

### Lỗi 2: nghĩ chung superclass là đủ để truyền hai `List<T>`

`List<Integer>` và `List<Double>` không tự động phù hợp với hai `List<T>`. Nếu phương thức chỉ đọc, hãy biểu diễn các tham số như `List<? extends T>`.

### Lỗi 3: dùng `List<Object>` cho mọi nguồn khác kiểu

```java
static void readObjects(List<Object> source) { }
```

Chữ ký trên không nhận trực tiếp `List<String>` hay `List<Integer>`. Nếu chỉ cần đọc các phần tử dưới dạng `Object`, `List<?>` thích hợp hơn; nếu cần giữ kiểu chung từ nhiều nguồn, dùng generic method với wildcard phù hợp.

### Lỗi 4: lạm dụng `var` rồi cho rằng kiểu động

`var` là suy luận **kiểu tĩnh cho biến cục bộ**. Sau khi compiler chọn kiểu, các phép gán/thao tác tiếp theo phải tuân thủ kiểu ấy. `var` không giúp “tự mở rộng” một `List<Integer>` khi muốn thêm `Double` sau đó.

### Lỗi 5: bỏ qua kiểu đích trong ràng buộc PECS

Hai nguồn `Integer` và `Double` cần một kiểu chung, nhưng nơi nhận cũng phải an toàn. Nếu đích chỉ là `List<Integer>` thì không thể đưa `Double` vào. Hãy kiểm tra đồng thời producer và consumer.

### Lỗi 6: coi suy luận kiểu là chuyển đổi giá trị số

`T` phù hợp với `Number` không có nghĩa Java tự chuyển `Double` thành `Integer`. Boxing, chuyển đổi primitive và chuyển kiểu tham chiếu có quy tắc riêng; generic inference không thay thế các phép biến đổi dữ liệu có chủ đích.

### Lỗi 7: ép generic để “sửa” lỗi suy luận

Cast raw/unchecked có thể xóa cảnh báo tại điểm gọi nhưng tạo lỗi dữ liệu về sau. Thay bằng chữ ký `<T>`, `? extends T`, `? super T` hoặc thiết kế một bước chuyển đổi dữ liệu thực sự khi cần.

### Lỗi 8: giả định suy luận bảo đảm collection thay đổi được

Compiler cho phép `destination.addAll(source)` theo kiểu không có nghĩa runtime sẽ hỗ trợ `addAll`. `List.of(...)` không sửa đổi; một collection có chính sách `null` hoặc đặc tính khác cũng có thể từ chối phần tử.

## 15. Bộ câu hỏi tự kiểm tra

**Câu 1.** Vì sao `chooseFirst(10, 2.5)` có thể hợp lệ với `static <T> T chooseFirst(T a, T b)`?

Vì hai đối số có thể được xem như các đối tượng thuộc một kiểu tham chiếu chung, và compiler có thể chọn `T` thỏa các ràng buộc đầu vào cùng ngữ cảnh sử dụng kết quả.

**Câu 2.** Có truyền `List<Integer>` và `List<Double>` cho `static <T> void f(List<T> a, List<T> b)` không?

**Không**, vì `List<T>` bất biến và hai danh sách được tham số hóa bởi các kiểu khác nhau.

**Câu 3.** Nếu đổi chữ ký thành `static <T> void f(List<? extends T> a, List<? extends T> b)` thì sao?

**Có**, vì mỗi nguồn có thể dùng một kiểu phần tử riêng là subtype của `T`, chẳng hạn cùng đọc dưới `Number`.

**Câu 4.** Hai nguồn `List<Integer>` và `List<Double>` có chuyển vào `List<Number>` theo `<T> void appendAll(List<? extends T>, List<? extends T>, List<? super T>)` không?

**Có.** `T = Number` đáp ứng điều kiện hai producer và consumer.

**Câu 5.** Nếu đích là `List<Integer>` thì sao?

**Không**, vì đích đó không nhận được mọi `Double` từ nguồn thứ hai.

**Câu 6.** Vì sao `List<Number> numbers = one(10)` có thể biên dịch dù `10` là `Integer`?

Vì ngữ cảnh kiểu trả về `List<Number>` cùng đối số `Integer` có thể dẫn compiler chọn `T = Number`.

**Câu 7.** Với `<T extends Number> double sum(T a, T b)`, `sum(10, 2.5)` và `sum("10", 2.5)` khác nhau ra sao?

Lời gọi thứ nhất phù hợp vì `Integer` và `Double` đều là `Number`. Lời gọi thứ hai bị từ chối vì `String` không thỏa cận `T extends Number`.

## 16. Quy trình chọn chữ ký khi có nhiều kiểu đầu vào

Khi xây dựng hoặc sửa một generic method, hãy đi theo thứ tự:

1. **Liệt kê kiểu thực tế** có thể đến từ từng đối số sau các phép chuyển đổi Java cho phép, chẳng hạn boxing.
2. **Xác định mục đích của từng tham số:** chỉ đọc, chỉ ghi, hay cần đọc và ghi chính xác cùng kiểu.
3. **Đặt `<T>` chung** nếu nhiều vị trí cần quan hệ kiểu nhất quán hoặc kết quả cần giữ loại phần tử.
4. **Dùng `? extends T` cho nguồn** nếu nguồn có thể chứa các subtype khác nhau của `T`.
5. **Dùng `? super T` cho đích** khi cần truyền các phần tử `T` vào một nơi nhận tương thích.
6. **Dùng `List<T>`** khi các danh sách thật sự phải chia sẻ cùng đối số kiểu và cần tương tác hai chiều.
7. **Thêm cận `<T extends Base>`** nếu thuật toán cần phương thức hoặc khả năng của `Base`.
8. **Xét kiểu kết quả mong đợi**; với `var`, nhớ rằng ngữ cảnh gán tường minh không còn.
9. **Kiểm tra mọi ràng buộc cùng lúc**, không chỉ tìm class cha chung của hai giá trị.

**Ghi nhớ:** suy luận generic cho nhiều đối số là việc tìm một nghiệm cho các ràng buộc kiểu của **toàn bộ lời gọi**. Hai giá trị `Integer` và `Double` có thể cùng dùng một `T` như `Number`, nhưng hai danh sách `List<Integer>` và `List<Double>` không tự trở thành hai `List<Number>`. Wildcard và PECS là cách diễn đạt chính xác quan hệ cần thiết để compiler vừa linh hoạt vừa giữ an toàn kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-006">Giới hạn trên, giới hạn dưới và kiểu cha chung phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Suy luận kiểu trong Java Generics: giới hạn trên, giới hạn dưới và kiểu cha chung phù hợp

## 1. Tại sao Java cần suy luận kiểu?

Khi gọi một phương thức generic, Java thường suy ra các type argument thay vì buộc lập trình viên chỉ rõ chúng. Ví dụ:

~~~java
static <T> T first(T a, T b) {
    return a;
}

String word = first("Java", "Generics");
Number number = first(10, 2.5);
~~~

Trong lời gọi đầu, cả hai đối số có kiểu `String`. Ở lời gọi sau, `10` và `2.5` lần lượt được boxing thành `Integer` và `Double` khi dùng trong quá trình suy luận với `T` là kiểu tham chiếu. Java phải chọn một `T` thỏa các điều kiện của **cả hai đối số**, đồng thời xét ngữ cảnh gán kết quả cho `Number`.

Bài học tập trung vào ba thành phần:

1. **Giới hạn dưới (lower bound):** những kiểu mà một biến suy luận phải là *supertype* của chúng; thường nảy sinh từ đối số được truyền vào.
2. **Giới hạn trên (upper bound):** những kiểu mà biến suy luận phải là *subtype* của chúng; có thể đến từ `extends` trong khai báo hoặc các điều kiện kiểu ở nơi sử dụng.
3. **Kiểu cha chung phù hợp (least upper bound, LUB):** giúp tìm loại chung đủ cụ thể để biểu diễn nhiều kiểu đầu vào khác nhau.

Đừng nhầm các **ràng buộc của thuật toán type inference** với wildcard `? extends T` và `? super T`. Wildcard là cấu trúc kiểu trong chương trình; thuật toán inference thu thập các ràng buộc về biến suy luận để quyết định chúng có thể được thay bằng những kiểu cụ thể nào. Hai cơ chế có liên quan nhưng không đồng nhất.

## 2. Thuật ngữ: `T` trong mã và biến suy luận `α` trong compiler

Một method generic khai báo **type parameter** `T`:

~~~java
static <T extends Number> T laterNumber(T a, T b) {
    return b;
}
~~~

Khi gọi `laterNumber(10, 2.5)` mà không viết type argument tường minh, compiler thay `T` bằng một **inference variable** tạm thời (trong mô tả JLS thường ghi `α`). Sau khi giải các ràng buộc, compiler chọn một kiểu hoặc quan hệ kiểu phù hợp rồi kiểm tra toàn bộ lời gọi.

Ký hiệu trong bảng sau là **mô hình toán học**, không phải cú pháp Java:

| Ràng buộc | Đọc thế nào? | Ví dụ ý nghĩa |
| --- | --- | --- |
| `Integer <: α` | `α` phải là một supertype của `Integer` | Đối số `Integer` phải truyền được cho tham số kiểu `T`. |
| `Double <: α` | `α` phải là một supertype của `Double` | Đối số `Double` cũng phải hợp lệ. |
| `α <: Number` | `α` phải thuộc `Number` hoặc subtype | Khai báo `<T extends Number>` đặt giới hạn trên. |
| `α = Integer` | `α` được ràng buộc bằng `Integer` | Một type argument cụ thể/quan hệ invariant có thể yêu cầu điều này. |

`A <: B` nghĩa là `A` là subtype của `B`. Vì vậy cần đặc biệt nhớ hướng:

- **Lower bound của `α` là `Integer`** tức `Integer <: α`, *không* có nghĩa `α` phải nhỏ hơn `Integer`.
- **Upper bound của `α` là `Number`** tức `α <: Number`, *không* có nghĩa `α` phải rộng hơn `Number`.

Một nghiệm hợp lệ phải thỏa **đồng thời** các ràng buộc đó. Với `Integer <: α <: Number`, `Integer` hoặc `Number` có thể là lựa chọn tương thích tùy những điều kiện khác của lời gọi. Java còn phải xử lý equality bounds, kiểu giao (*intersection types*), capture conversion, target typing và nhiều quy tắc đặc thù; không thể suy luận một kiểu duy nhất chỉ bằng việc nhìn một dấu `extends`.

## 3. Lower bounds được tạo ra từ các đối số ra sao?

Xét:

~~~java
static <T> T choose(T left, T right) {
    return left;
}

Number n = choose(10, 2.5);
~~~

Đối số `10` có kiểu primitive `int` và `2.5` có kiểu primitive `double`. Trong ngữ cảnh generic cần kiểu tham chiếu, compiler có thể sử dụng boxing thành `Integer` và `Double`. Hai điều kiện cốt lõi là:

~~~text
Integer <: α
Double  <: α
~~~

Nghĩa là `α` phải chấp nhận cả hai đối số. Việc `Integer` và `Double` cùng có cha là `Number` giúp `Number` trở thành **một kiểu tương thích** cho nơi nhận kết quả.

**Không nên kết luận kiểu suy luận nội bộ luôn chính xác là `Number`.** Theo JLS §4.10.4, LUB trong Java có thể là một *intersection type* bao gồm các interface chung như `Comparable<?>` cùng với một class chung; target context cũng có thể ảnh hưởng đến nghiệm. Người dùng API nên dựa trên kiểu khai báo và các phép gán hợp lệ thay vì cố đoán chính xác tên kiểu compiler chọn trong mọi trường hợp.

### 3.1. Trường hợp đối số có cùng kiểu

~~~java
String label = choose("A", "B");
Integer id = choose(7, 9);
~~~

Trong các tình huống đơn giản này, `T` có thể được suy ra tương ứng với `String` và `Integer`. Không cần cast, không cần viết `MyClass.<String>choose(...)`.

### 3.2. Khi nhiều nguồn cần kiểu cha chung

~~~java
Number number = choose(10, 2.5);     // Hợp lệ.
Object object = choose("text", 42);   // Hợp lệ.
~~~

- Với `Integer` và `Double`, có những supertype chung hữu ích cho phép trả về `Number`.
- Với `String` và `Integer`, `Object` là một supertype chung. Chúng còn có thể chia sẻ interface, nên LUB chính xác không nhất thiết chỉ là `Object`.

**Cần chú ý:** `choose("text", 42)` vẫn trả về **chính đối tượng được chọn**; type inference không chuyển `Integer` thành `String` hay biến hai giá trị thành một loại runtime mới.

## 4. Upper bounds từ khai báo `extends` giới hạn nghiệm

Một phương thức có thể yêu cầu type argument thuộc nhánh `Number`:

~~~java
static <T extends Number> T laterNumber(T a, T b) {
    return b;
}
~~~

Khi gọi:

~~~java
Number result = laterNumber(10, 2.5);
~~~

Compiler kết hợp:

~~~text
Integer <: α
Double  <: α
α       <: Number
~~~

Cần tìm một nghiệm thỏa cả ba. Đây là ví dụ có **lower bounds từ các đối số** và **upper bound từ khai báo type parameter**.

Khi chỉ có một đối số `String`, ràng buộc mâu thuẫn:

~~~java
static <T extends Number> T bounded(T value) {
    return value;
}

// KHÔNG biên dịch:
Number invalid = bounded("text");
~~~

Cốt lõi là `String <: α` và `α <: Number` **không thể đồng thời đúng**; `String` không phải subtype của `Number`.

### 4.1. Nhiều upper bounds với `&`

Java cho phép khai báo type parameter có nhiều bound, ví dụ:

~~~java
static <T extends Number & Comparable<T>> T maximum(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
~~~

- `T extends Number` cho phép gọi các phương thức của `Number`.
- `T extends Comparable<T>` bảo đảm hai giá trị cùng `T` có thể so sánh.
- Với `maximum(3, 9)`, `T = Integer` đáp ứng các điều kiện, kết quả là `Integer 9`.

**Đừng hiểu `<T extends Number & Comparable<T>>` là Java nhận mọi cặp số khác loại.** Ví dụ `Integer` và `Double` đều triển khai `Comparable` với **type argument riêng**, nên `maximum(3, 9.5)` không thỏa tự động hợp đồng “so sánh cùng `T`”. Muốn so sánh khác loại theo giá trị số, phải thiết kế một tiêu chí so sánh cụ thể và làm rõ chuyển đổi/độ chính xác.

### 4.2. Quy tắc với nhiều interface bounds

Trong khai báo type parameter, nếu có bound là class thì nó phải đứng đầu; các interface đứng sau `&`:

~~~java
<T extends Number & Comparable<T>>
~~~

Kiểu thực tế phải thỏa tất cả bound, không chỉ một bound bất kỳ. Nếu bộ ràng buộc mâu thuẫn, compiler báo lỗi thay vì tự bỏ bớt giới hạn để chương trình chạy.

## 5. LUB: kiểu cha chung “ít mất thông tin nhất” là gì?

**Least upper bound (LUB)** của một tập các reference types là một kiểu cha chung được tính theo thuật toán JLS §4.10.4 để không chọn một supertype rộng không cần thiết.

Ý tưởng đơn giản khi xét hai kiểu:

~~~text
      Object
        |
      Number
       /  \
  Integer  Double
~~~

`Number` là class cha chung gần nhất trong cây class của `Integer` và `Double`. Nếu chỉ nhìn cây class này, `Number` mang nhiều thông tin hơn `Object`. Vì vậy ta có thể xử lý hai giá trị như `Number`.

Tuy nhiên, **LUB trong JLS không chỉ là tìm một class cha gần nhất trong cây**:

- Một class có thể implements nhiều interface. Hai class khác nhau có thể chia sẻ nhiều interface quan trọng.
- Java có các *intersection types*, ví dụ một kiểu thỏa đồng thời `Number` và các interface chung.
- Với generic types, Java phải xét type arguments, erasure, wildcard và các quy tắc least containing parameterization.
- LUB là một phần trong quá trình resolution; nó không thay thế các upper bounds, equality constraints hay ngữ cảnh kiểu đích.

Vì thế, phát biểu có thể áp dụng là: **LUB giúp tìm kiểu chung phù hợp từ các lower bounds; compiler vẫn cần kiểm tra tất cả các ràng buộc khác.**

### 5.1. Khi có lower bounds, JLS dùng LUB thế nào?

Trong trường hợp đơn giản khi giải một inference variable với các **proper lower bounds** `L1, L2, ...`, JLS §18.4 mô tả ứng viên nghiệm là:

~~~text
α := lub(L1, L2, ...)
~~~

Ứng viên đó còn được đưa vào bộ ràng buộc để kiểm tra tính nhất quán. Nếu nó mâu thuẫn với upper bound hay quan hệ khác, inference có thể thất bại hoặc cần xử lý tiếp theo thuật toán chuẩn. Không thể cứ nhìn thấy LUB là bỏ qua tất cả các bound khác.

### 5.2. “Greatest lower bound” không phải LUB

Trong phần resolution của JLS, nếu không có proper lower bounds, một ứng viên có thể được hình thành từ **greatest lower bound (GLB)** của những proper upper bounds. Đây là khái niệm khác với LUB:

- `lub(Integer, Double)` tìm một **supertype chung** đủ cụ thể của hai kiểu.
- `glb(Upper1, Upper2)` xét sự kết hợp các yêu cầu kiểu **phải nằm dưới** nhiều upper bounds, thường liên hệ với intersection types.

Đừng nhầm “lower bound của inference variable” với “greatest lower bound của nhiều kiểu”. Hai cụm dùng chữ *lower* nhưng nói về các tầng khái niệm khác nhau.

## 6. Equality constraints và invariance của generic arguments

Không phải lúc nào compiler cũng có thể nâng type argument lên một kiểu cha chung. Các generic types như `List<Integer>` và `List<Number>` thông thường **invariant**:

~~~java
List<Integer> ints = List.of(1, 2);
// KHÔNG biên dịch:
List<Number> numbers = ints;
~~~

Nếu ngữ cảnh bắt buộc một generic argument chính xác, compiler có thể có **equality constraint**, ví dụ `α = Integer` thay vì chỉ `Integer <: α`.

Xem lời gọi:

~~~java
// KHÔNG biên dịch:
List<Integer> values = List.of(1, 2.5);
~~~

Trong `List.of(E, E)`, kiểu đích `List<Integer>` đòi type argument `E` tương thích với `Integer`, đồng thời các đối số yêu cầu `E` chấp nhận cả `Integer` và `Double`. Java báo các ràng buộc không tương thích (thường gồm `equality constraints: Integer` và `lower bounds: Double,Integer`). Không thể chỉ chọn `Number` vì `List<Number>` không phải `List<Integer>`.

Ngược lại:

~~~java
List<Number> numbers = List.of(1, 2.5); // Hợp lệ.
~~~

Ngữ cảnh `List<Number>` cho phép các giá trị `Integer` và `Double` cùng là phần tử của list có type argument `Number`. Phép gán trên **không tạo một List<Integer> rồi biến nó thành List<Number>**. Compiler sử dụng target type trong việc suy luận `E` cho lời gọi tạo list.

**Bài học:** Có một superclass chung chưa đủ để chứng minh hai **parameterized types cụ thể** có quan hệ subtype. Phải theo dõi cả invariance và các equality constraints phát sinh từ ngữ cảnh.

## 7. Kiểu đích bổ sung điều kiện cho biến suy luận

Với method trả `List<T>`, kiểu đích có thể tạo **equality constraint** trong ngữ cảnh poly invocation, bên cạnh lower/upper bounds của các đối số:

```java
static <T> List<T> singleton(T value) {
    return List.of(value);
}
List<Number> numbers = singleton(7);
```

`Number` được đưa vào từ kiểu đích `List<Number>`, còn Integer từ đối số 7 phải được chấp nhận làm T. Compiler tìm nghiệm phù hợp cho cả hai; target không thay đổi kiểu của một `List<Integer>` đã tồn tại.

Nếu cần nêu chủ đích ngay ở lời gọi:

```java
Number value = InferenceBoundsDemo.<Number>first(1, 2.5);
```

Đây là ví dụ với method `first` của chương trình ở mục 9. Với `var values = List.of(1, 2.5)`, không có target `List<Number>` tường minh; thuật toán LUB có thể đưa ra kiểu kết hợp phức tạp hơn một class cha. Ở đây, **mô hình lower/equality/upper bounds** mới là nội dung chính; các quy tắc ngữ cảnh poly expression được đào sâu ở bài kế tiếp.

## 8. Wildcard `extends`/`super` trong phương thức nhiều tham số

PECS (*Producer Extends, Consumer Super*) thường được dùng cùng type inference, chẳng hạn:

~~~java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
~~~

Khi gọi:

~~~java
List<Integer> source = List.of(2, 4);
List<Number> destination = new ArrayList<>(List.of(1.5));
appendAll(source, destination);
~~~

Compiler cần tìm một `T` sao cho:

- `source` cung cấp các phần tử gán được cho `T` (`Integer` phù hợp với `T`).
- `destination` có thể nhận những giá trị `T` (`T` phải phù hợp với kiểu phần tử `Number` của destination).

Với trường hợp đơn giản này, có thể hình dung các điều kiện:

~~~text
Integer <: T
T       <: Number
~~~

Có một hoặc nhiều lựa chọn `T` phù hợp với hai ràng buộc; compiler đưa các quy tắc generic wildcard, capture và inference vào để xác nhận lời gọi. Không cần cast, và không cần hai list có cùng type argument. Đây là **quan hệ giữa nguồn và đích**, chứ không chỉ tìm một supertype chung tùy ý.

### 8.1. Khi hai kiểu không có quan hệ truyền an toàn

~~~java
List<String> strings = List.of("A", "B");
List<Integer> integers = new ArrayList<>();

// KHÔNG thể dùng appendAll(strings, integers) để đưa String vào List<Integer>.
~~~

Lời gọi sẽ không biên dịch: không tồn tại `T` đáp ứng đồng thời các ràng buộc truyền từ `String` sang một danh sách chỉ chấp nhận `Integer`, trừ khi bạn đổi hợp đồng/destination type. Tránh raw types và unchecked casts để “bắt” compiler chấp nhận chuyển đổi dữ liệu không an toàn.

### 8.2. Giới hạn từ callback và quan hệ nhiều biến

Trong API xử lý chuyển đổi, một type parameter có thể đóng vai trò đầu vào, một type parameter khác làm đầu ra:

~~~java
static <T, R> List<R> transform(
        List<? extends T> input,
        java.util.function.Function<? super T, ? extends R> mapper) {
    List<R> output = new ArrayList<>();
    for (T item : input) {
        output.add(mapper.apply(item));
    }
    return output;
}
~~~

Đây là **một phương thức minh họa** cần được đặt trong lớp có import `List`, `ArrayList`. Compiler phải suy luận các ràng buộc liên hệ `T`, `R`, nguồn và `mapper`. Đây là trường hợp một upper/lower bound đơn lẻ không thể kể hết cách inference hoạt động; thứ tự suy luận và phụ thuộc giữa inference variables cũng có vai trò.

## 9. Chương trình Java 17+ hoàn chỉnh đã được biên dịch và chạy

Tạo tệp **InferenceBoundsDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class InferenceBoundsDemo {
    static <T> T first(T a, T b) {
        return a;
    }

    static <T extends Number> T laterNumber(T first, T second) {
        return second;
    }

    static <T> List<T> singleton(T value) {
        return List.of(value);
    }

    static <T> void appendAll(List<? extends T> source, List<? super T> destination) {
        for (T element : source) {
            destination.add(element);
        }
    }

    static <T extends Number & Comparable<T>> T maximum(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        String word = first("Java", "Generics");
        System.out.println("same type: " + word);

        Number common = first(10, 2.5);
        System.out.println("common: " + common);

        Number last = laterNumber(10, 2.5);
        System.out.println("bounded: " + last);

        List<Number> numbers = List.of(10, 2.5);
        System.out.println("target typed: " + numbers);

        List<Number> one = singleton(7);
        System.out.println("singleton: " + one);

        List<Integer> source = List.of(2, 4);
        List<Number> destination = new ArrayList<>(List.of(1.5));
        appendAll(source, destination);
        System.out.println("combined: " + destination);

        Integer max = maximum(3, 9);
        System.out.println("max: " + max);

        Number explicit = InferenceBoundsDemo.<Number>first(1, 2.5);
        System.out.println("explicit: " + explicit);
    }
}
~~~

Biên dịch và chạy với Java 17 hoặc mới hơn:

~~~bash
javac --release 17 -Xlint:all InferenceBoundsDemo.java
java InferenceBoundsDemo
~~~

**Stdout chính xác**:

~~~text
same type: Java
common: 10
bounded: 2.5
target typed: [10, 2.5]
singleton: [7]
combined: [1.5, 2, 4]
max: 9
explicit: 1
~~~

Đã thử thực tế bằng `javac --release 17 -Xlint:all` và chạy chương trình; ví dụ biên dịch thành công, không có unchecked warnings. Giải thích:

1. Hai `String` cho một lời gọi `first` với kết quả dùng như `String`.
2. `Integer` và `Double` cung cấp lower bounds để kết quả dùng được như `Number`.
3. `laterNumber` có thêm upper bound `Number`.
4. `List.of(...)` và `singleton(...)` chịu ràng buộc từ target type `List<Number>`.
5. `appendAll` cho phép chuyển `Integer` sang danh sách đích `Number`.
6. `maximum` buộc `T` thỏa đồng thời `Number` và `Comparable<T>`.
7. Explicit witness `<Number>` làm rõ type argument của lời gọi cuối.

## 10. Tệp cố ý sai để đọc thông báo `javac`

Tạo riêng **InvalidInference.java** như sau. **Tệp này được thiết kế để biên dịch thất bại**, không phải một phần chương trình chạy ở mục 9:

~~~java
import java.util.List;

class InvalidInference {
    static <T extends Number> T bounded(T value) { return value; }
    static <T> T choose(T a, T b) { return a; }

    static void broken() {
        Number no = bounded("text");
        Integer incompatible = choose(1, 2.5);
        List<Integer> ints = List.of(1, 2.5);
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 -Xdiags:verbose InvalidInference.java
~~~

**Kỳ vọng:** lệnh kết thúc với exit code khác 0 và ba lỗi. Những đoạn chẩn đoán tiêu biểu được xác minh bằng `javac`:

~~~text
inference variable T has incompatible bounds
  upper bounds: Number
  lower bounds: String
...
inferred type does not conform to upper bound(s)
...
inference variable E has incompatible bounds
  equality constraints: Integer
  lower bounds: Double,Integer
3 errors
~~~

Các dòng trên là **trích đoạn rút gọn**, không phải toàn bộ stderr; tên inference variable, intersection types, đường dẫn và vị trí dòng có thể khác giữa môi trường chạy.

Ba lỗi này minh họa ba cách thất bại:

- `bounded("text")` tạo lower bound `String` xung đột upper bound `Number`.
- `Integer incompatible = choose(1, 2.5)` yêu cầu kết quả có thể dùng như `Integer` nhưng các đối số khác loại không tương thích với yêu cầu đó. Compiler có thể hiển thị một intersection type trong lỗi.
- `List<Integer> ints = List.of(1, 2.5)` có target parameterization đòi `Integer`, nhưng danh sách đầu vào cũng chứa `Double`; equality và lower bounds xung đột.

Không nên làm mất các dấu hiệu lỗi bằng raw types, `@SuppressWarnings("unchecked")` hoặc cast không có cơ sở. Những lỗi này xuất phát từ **không tồn tại nghiệm kiểu phù hợp**, chứ không phải do thiếu một phép cast cú pháp.

## 11. Hướng dẫn đọc lỗi suy luận kiểu

Khi `javac` báo “`inference variable ... has incompatible bounds`”, hãy kiểm tra theo trình tự:

1. **Xác định biến suy luận:** xem method generic khai báo `<T>`, `<E>`, `<T,R>` hay bound nào.
2. **Tìm lower bounds:** lập danh sách các kiểu mà biến suy luận phải chấp nhận từ đối số (ví dụ `Integer`, `Double`, `String`).
3. **Tìm upper bounds:** đọc `extends`, callback constraints và target context có ràng buộc đầu ra.
4. **Kiểm tra equality/invariance:** liệu một `List<Integer>` đang bị yêu cầu nhận một generic type có type argument khác?
5. **Nhìn xem có supertype chung phù hợp không:** nếu có, kiểu cha chung đó có thỏa **tất cả** những điều kiện còn lại không? Chỉ có `Object` là cha chung không tự làm cho mọi chương trình hợp lệ.
6. **Xét target type và overload:** lời gọi đang đứng trong phép gán, `return`, tham số method khác hay được suy luận độc lập với `var`?
7. **Sửa contract ở đúng nơi:** đổi đầu ra sang `Number` khi nghiệp vụ thật sự cho phép nhiều loại số; dùng wildcard `extends`/`super` nếu source và destination có quan hệ biến thiên an toàn; dùng type witness khi cần thể hiện loại mong muốn.
8. **Biên dịch với diagnostics rõ:** dùng `javac -Xdiags:verbose`; không phải mọi lỗi “incompatible bounds” đều có cùng nguyên nhân.

Một dấu hiệu cần cảnh giác là ép `(List<Integer>)` vào collection đã chứa `Double`. Nó có thể dẫn tới heap pollution và `ClassCastException` sau này, không tạo ra một nghiệm suy luận đúng.

## 12. Lỗi hiểu biết thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Lower bound nghĩa là `α <: Lower`.” | Lower bound `L` của `α` nghĩa là `L <: α`. |
| “Upper bound nghĩa là `Upper <: α`.” | Upper bound `U` nghĩa là `α <: U`. |
| “Có cha chung là `Object` thì suy luận nào cũng hợp lệ.” | Các ràng buộc `extends`, equality, invariance và target typing vẫn phải thỏa. |
| “LUB của `Integer` và `Double` lúc nào cũng chỉ là `Number`.” | JLS có thể biểu diễn kiểu giao/interfaces, không chỉ một class cha. |
| “`<T extends Number>` tự động chuyển `String` thành số.” | Không. Nếu đầu vào không thỏa `Number`, compiler báo lỗi. |
| “Hai `List<T>` có thể luôn dùng chung `T` vì đều là List.” | Type arguments là invariant; phải có ràng buộc thật sự giữa chúng. |
| “`List<Integer>` là subtype của `List<Number>` vì Integer extends Number.” | Không. Cần wildcard thích hợp hoặc tạo collection mới đúng type argument. |
| “`var` luôn suy luận đúng kiểu cha mình hình dung.” | `var` dùng kiểu của initializer; kiểu đó có thể là kiểu giao/kiểu tham số hóa phức tạp. |
| “Thông báo upper/lower bounds là lỗi runtime.” | Đây là chẩn đoán **compile time** về tính nhất quán của kiểu. |
| “`@SuppressWarnings("unchecked")` sửa lỗi không có nghiệm inference.” | Không. Nó chỉ tác động tới một số cảnh báo unchecked, không hợp thức hóa kiểu sai. |

## 13. Câu hỏi tự kiểm tra

1. Với `<T extends Number> T f(T x)` và đối số `Integer`, hướng ràng buộc là gì? **`Integer <: α <: Number`**.
2. Tại sao `f("hello")` không hợp lệ? **Vì `String` không nằm dưới `Number` và không có nghiệm thỏa cả hai điều kiện.**
3. Lower bounds `Integer` và `Double` gợi ý tìm gì? **Một supertype chung phù hợp; JLS có thể tính LUB của các reference types.**
4. LUB có phải luôn một class đơn giản? **Không.** Có thể liên hệ với intersection types và parameterized types.
5. `List<Integer> ints = List.of(1, 2.5)` sai dù `Number` là cha chung, vì sao? **Target type tạo constraint buộc type argument phù hợp `Integer`, mâu thuẫn đối số `Double`.**
6. `List<Number> ns = List.of(1, 2.5)` có hợp lệ không? **Có.** Target type tương thích với cả hai loại số.
7. `<T> void append(List<? extends T>, List<? super T>)` có cần hai list có cùng type argument không? **Không.** Nó chỉ đòi quan hệ truyền `T` an toàn từ nguồn sang đích.
8. Tại sao explicit `<Number>` đôi khi hữu ích? **Nó cung cấp type argument chủ đích rõ ràng cho method, miễn đối số và bounds đều phù hợp.**

## 14. Tài liệu chuẩn

- [JLS Java SE 17 §18.1.3 — Bounds](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.1.3): định nghĩa upper, lower, equality, capture và `throws` bounds.
- [JLS Java SE 17 §18.4 — Resolution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.4): cách dùng `lub` từ proper lower bounds và `glb` từ proper upper bounds khi giải inference variables.
- [JLS Java SE 17 §18.5 — Uses of Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18.5): method applicability, invocation type inference và tác động của target type.
- [JLS Java SE 17 §4.10.4 — Least Upper Bound](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.4): LUB của reference types có xét generic declarations, intersection type và supertypes.
- [JLS Java SE 17 §4.5.1 — Type Arguments](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): wildcard containment và bất biến của các parameterized types.
- [JLS Java SE 17 §15.12 — Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): ngữ cảnh và quy tắc của lời gọi method, kể cả generic methods.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-007">Poly expression, lời gọi lồng nhau và giới hạn lan truyền kiểu đích</a>

<details>
<summary>Xem chi tiết</summary>

# Poly expression, lời gọi lồng nhau và giới hạn lan truyền kiểu đích trong Java Generics

## 1. Đọc đúng vấn đề

Khi sử dụng generics, Java có thể suy luận tham số kiểu nhờ **ngữ cảnh** nơi một biểu thức xuất hiện. Chẳng hạn:

~~~java
List<String> names = Collections.emptyList();
~~~

Phương thức Collections.emptyList() có khai báo đại ý là **static <T> List<T> emptyList()**. Nó không nhận đối số, nhưng trong phép gán trên, Java vẫn suy luận được T là String nhờ kiểu đích **List<String>**.

Khả năng này gắn với **poly expression**: biểu thức có kiểu phụ thuộc vào ngữ cảnh thích hợp, chứ không chỉ được quyết định độc lập từ nội dung bên trong biểu thức.

Câu hỏi khó hơn xuất hiện khi có lời gọi lồng nhau:

~~~java
List<String> a = identity(Collections.emptyList());
List<String> b = Collections.emptyList().stream().toList();
~~~

Hai biểu thức nhìn có vẻ đều bắt đầu từ emptyList(), nhưng câu đầu có thể biên dịch còn câu thứ hai **không biên dịch**. Ta phải xét **vị trí cú pháp của từng lời gọi**, loại ngữ cảnh cung cấp kiểu đích và cách ràng buộc kiểu đi qua các phương thức. Đây là giới hạn thực tế quan trọng nhất của suy luận kiểu lồng nhau.

Các ví dụ trong bài sử dụng Java 17+ và những API tiêu chuẩn thuộc java.util, java.util.function và java.util.stream.

## 2. Ba khái niệm nền tảng: target type, standalone expression và poly expression

**Target type (kiểu đích)** là kiểu mà ngữ cảnh mong muốn biểu thức tạo ra. Các nguồn kiểu đích thường gặp gồm:

- **Phép gán / khai báo có kiểu tường minh**: List<String> x = Collections.emptyList();.
- **Đối số lời gọi phương thức**: consume(Collections.emptyList()); khi consume nhận List<String>.
- **Biểu thức trả về từ phương thức**: phương thức khai báo trả về List<String>, rồi return Collections.emptyList();.
- **Ép kiểu tường minh** và một số ngữ cảnh khác được đặc tả.
- **Các nhánh của biểu thức điều kiện dạng tham chiếu** khi bản thân biểu thức điều kiện là poly expression.

**Standalone expression** là biểu thức có kiểu được xác định chủ yếu từ chính biểu thức và ngữ cảnh riêng của nó, thay vì được áp kiểu mong muốn từ phép gán bên ngoài theo cơ chế poly expression.

**Poly expression** là biểu thức được Java Language Specification quy định là nhận kiểu đích ở những ngữ cảnh nhất định. Không phải cứ nằm trong câu lệnh gán là mọi biểu thức con đều nhận cùng một kiểu đích.

Ví dụ:

~~~java
List<String> one = Collections.emptyList(); // Kiểu đích giúp suy ra T = String.
var two = Collections.emptyList();          // List<Object>: không có target String.
~~~

Với khai báo thứ hai, **var không phải kiểu đích String**. Tại lời gọi emptyList() không có ràng buộc cụ thể, T được suy luận thành Object và kiểu tĩnh của two là List<Object>. Việc dùng two ở các dòng sau không sửa kết quả đã suy luận.

### Poly expression không đồng nghĩa generic expression

Không phải biểu thức có generic là poly trong mọi vị trí. Đối với **method invocation** generic, các điều kiện chính gồm lời gọi xuất hiện trong **assignment context** hoặc **invocation context**, sử dụng tham số kiểu liên quan tới kiểu trả về, và thuộc dạng được JLS cho phép xét kiểu đích. Lời gọi đó nằm ở vị trí receiver của một lời gọi tiếp theo không tự động nhận target type từ phép gán ở ngoài.

Các dạng poly expression phổ biến khác gồm lambda expression, method reference, lời gọi constructor dùng diamond trong ngữ cảnh phù hợp, một số conditional expression tham chiếu và switch expression tham chiếu. Mỗi dạng có quy tắc riêng. Ta không thể kết luận rằng mọi biểu thức nằm bên trong một poly expression đều là poly expression.

## 3. Suy luận kiểu generic tại một lời gọi phương thức

Xét phương thức generic tự viết:

~~~java
static <T> T identity(T value) {
    return value;
}
~~~

Tham số kiểu T xuất hiện cả ở đối số đầu vào lẫn kiểu trả về. Vì thế compiler có thể nhận ràng buộc từ **đối số thực tế**, **kiểu đích của toàn lời gọi**, hoặc cả hai để tìm một kiểu thỏa mãn.

~~~java
String s = identity("Java");                  // T = String nhờ đối số "Java".
List<String> x = identity(Collections.emptyList());
~~~

Ở dòng x:

1. Kiểu đích của phép gán là List<String>.
2. identity(...) là lời gọi generic phù hợp để xét theo assignment context.
3. Ràng buộc từ kiểu trả về T của identity gắn với List<String>.
4. Lời gọi Collections.emptyList() bên trong được xét như đối số của identity, với ràng buộc từ tham số hình thức T.
5. Bộ giải ràng buộc generic phối hợp thông tin của lời gọi bên ngoài và lời gọi bên trong; kết quả hợp lệ là List<String>.

Vì vậy, có thể nói **thông tin kiểu đích được truyền qua suy luận lời gọi lồng nhau** trong trường hợp này. Tuy nhiên, đây không phải thao tác sao chép target type cơ học từ ngoài vào mọi biểu thức con. Compiler giải hệ ràng buộc theo các quy tắc suy luận kiểu của Java; khác biệt của vị trí cú pháp và kiểu formal parameter có thể thay đổi kết quả.

### Kiểu đích từ đối số phương thức

Giả sử có:

~~~java
static void consume(List<String> names) {
    System.out.println(names);
}
~~~

Hai lời gọi sau đều hợp lệ:

~~~java
consume(Collections.emptyList());
consume(identity(Collections.emptyList()));
~~~

Đối số của consume xuất hiện trong **invocation context** yêu cầu List<String>. Ở trường hợp lồng nhau, suy luận có thể phối hợp các ràng buộc qua identity và emptyList.

Ngược lại:

~~~java
var temp = Collections.emptyList(); // temp là List<Object>.
// consume(temp);                   // Lỗi biên dịch.
~~~

**Biến trung gian đã được gán kiểu** là một ranh giới quan trọng. Khi temp được suy luận thành List<Object>, một lời gọi consume ở phía sau không thể quay ngược lại để làm temp đổi thành List<String>.

### Kiểu đích từ return

Nếu phương thức đang định nghĩa có kiểu trả về List<String> thì biểu thức return cũng có ngữ cảnh kiểu đích:

~~~java
static List<String> makeNames() {
    return identity(Collections.emptyList());
}
~~~

Dòng return hợp lệ vì hệ ràng buộc có thể suy luận các lời gọi generic về kiểu phù hợp với List<String>. Đặt cùng một initializer vào biến var trước khi return sẽ đổi tình huống:

~~~java
static List<String> brokenExample() {
    var intermediate = Collections.emptyList(); // List<Object>.
    // return intermediate;                      // Không biên dịch.
    return Collections.emptyList();              // Hợp lệ.
}
~~~

Bản thân từ khóa return không thay đổi kiểu của biến đã khai báo.

## 4. Khi nào kiểu đích không lan truyền xuyên qua lời gọi lồng nhau?

Đây là điểm dễ sai nhất. So sánh hai trường hợp:

~~~java
List<String> valid = Collections.emptyList();
List<String> invalid = Collections.emptyList().stream().toList(); // Lỗi.
~~~

Ở dòng valid, emptyList() là **toàn bộ biểu thức phía phải** của phép gán nên nhận kiểu đích List<String>.

Ở dòng invalid, emptyList() làm **receiver** của lời gọi .stream(). Phần **Collections.emptyList()** không xuất hiện trực tiếp trong assignment context hoặc invocation context của biểu thức gán cho List<String>. Nó được xác định kiểu trước tại vị trí receiver, nơi không có ràng buộc buộc T là String. Vì không có đối số kiểu cụ thể, nó được suy luận thành List<Object>. Sau đó:

~~~text
Collections.emptyList()      → List<Object>
             .stream()       → Stream<Object>
             .toList()       → List<Object>
Gán cho List<String>         → Lỗi biên dịch
~~~

Mặc dù toàn biểu thức đứng bên phải List<String>, compiler **không truyền mục tiêu String ngược xuyên qua chuỗi gọi phương thức để sửa kiểu của receiver đã xác định**.

### Cách giải quyết ranh giới receiver

Cung cấp kiểu trước khi bắt đầu chuỗi:

~~~java
List<String> good = Collections.<String>emptyList()
        .stream()
        .toList();
~~~

Cụm **Collections.<String>emptyList()** là lời gọi phương thức generic có **type witness** <String>. Nó buộc T là String tại chính vị trí tạo receiver. Khi đó stream() trả về Stream<String> và toList() trả về List<String>.

Tương tự, với Stream.empty():

~~~java
List<String> bad = java.util.stream.Stream.empty().toList(); // Lỗi.
List<String> good = java.util.stream.Stream.<String>empty().toList();
~~~

Stream.empty() ở vị trí receiver của toList(), nên mặc định là Stream<Object> khi không có ràng buộc khác. Stream.<String>empty() khắc phục bằng việc chỉ rõ kiểu trước khi nối chuỗi.

**Quy tắc kiểm tra hữu ích:** hãy xác định biểu thức nào làm receiver tại từng dấu chấm. Nếu receiver là một lời gọi generic không có đối số mang thông tin kiểu, target type của phép gán ngoài cùng thường không đủ để định kiểu lại receiver đó.

## 5. Diamond trong lời gọi lồng nhau: theo dõi đúng ranh giới

Constructor dùng diamond nhận thông tin từ target type và từ các đối số được truyền vào:

```java
List<String> a = new ArrayList<>();
List<String> b = new ArrayList<>(Collections.emptyList());
var c = new ArrayList<>();                // ArrayList<Object>.
var d = new ArrayList<>(List.of("Java")); // ArrayList<String>.
```

Dòng `b` là một **lời gọi constructor có target** `List<String>`; ràng buộc E từ `ArrayList<E>` phối hợp với generic call `Collections.emptyList()` làm đối số constructor. Dòng `c` thiếu cả target String lẫn đối số xác định E; dòng `d` nhận thông tin String từ chính constructor argument.

Khác với **chuỗi receiver** ở mục 4, lời gọi generic bên trong constructor là **đối số**, nên có cơ hội tham gia inference qua invocation context. Nhưng nếu gán trước `var source = Collections.emptyList()`, kiểu `List<Object>` của biến đó đã chốt; `new ArrayList<>(source)` không tự biến nó thành `List<String>`.

Phân biệt hai ranh giới: *constructor argument có thể nhận thông tin ngữ cảnh*; *một generic method call đã trở thành receiver của method chain không nhất thiết nhận target ngoài cùng*. Đây là lý do bài này phân tích vị trí biểu thức, thay vì chỉ lặp lại hướng dẫn dùng diamond.

## 6. Lambda và method reference: poly expression cần kiểu đích

Khác biệt rất rõ khi dùng functional interface:

~~~java
Function<String, Integer> length = s -> s.length();
~~~

Biểu thức lambda **s -> s.length()** nhận kiểu đích Function<String, Integer>; compiler biết tham số s là String nên có thể tra cứu phương thức length().

Lệnh sau không biên dịch:

~~~java
var length = s -> s.length(); // Lỗi: lambda expression needs an explicit target-type.
~~~

Lý do không phải var bị cấm trong mọi khai báo liên quan lambda: lambda **cần target type là một functional interface**, còn var không tự suy ra functional interface mong muốn chỉ từ thân lambda. Một lambda có thể phù hợp với nhiều giao diện khác nhau.

Method reference tương tự:

~~~java
Supplier<List<String>> factory = Collections::emptyList;
Function<String, Integer> length = String::length;
~~~

Hai biến trên có target functional interface rõ ràng, nên compiler biết kiểu phương thức cần ghép.

~~~java
var factory = Collections::emptyList; // Lỗi: method reference cần target type.
~~~

Dùng var với **kết quả của một lời gọi phương thức trả về functional interface** thì khác; lúc đó initializer đã có kiểu riêng. Ví dụ:

~~~java
static java.util.function.Function<String, Integer> createLengthFunction() {
    return String::length;
}

var lengthFunction = createLengthFunction(); // Hợp lệ.
~~~

Ở đây var suy luận từ kiểu trả về Function<String, Integer> của createLengthFunction(), chứ không suy luận trực tiếp từ method reference.

## 7. Conditional expression và giới hạn của kiểu đích

Một biểu thức điều kiện kiểu tham chiếu có thể là poly expression khi xuất hiện trong ngữ cảnh phù hợp:

~~~java
List<String> chosen = flag
        ? Collections.emptyList()
        : List.of("fallback");
~~~

Trong một chương trình nơi flag là boolean đã khai báo, phép gán có target List<String>. Thông tin đó có thể dùng để kiểm tra hai nhánh, bao gồm suy luận emptyList() thành List<String>. Kiểu đích được phân phối theo quy tắc của **reference conditional expression**.

Nhưng không nên suy ra rằng mọi biểu thức toán tử đều lan truyền kiểu theo cơ chế này. Ví dụ số học, biểu thức điều kiện có nhánh primitive, lời gọi method chain, và lambda đều có quy tắc riêng; phép gán bên ngoài không làm tất cả biểu thức con thành poly một cách mặc định.

Đặc biệt:

~~~java
var chosen = flag
        ? Collections.emptyList()
        : List.of("fallback");
~~~

Không có target List<String> do vế trái cung cấp. Compiler phải tìm kiểu của conditional expression theo quy tắc áp dụng khi không có ngữ cảnh đích cụ thể; đừng giả định chosen chắc chắn có kiểu List<String> chỉ vì một nhánh chứa chuỗi.

## 8. Chương trình Java 17+ chạy được

Lưu mã sau thành **PolyTargetDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class PolyTargetDemo {
    static <T> T identity(T input) {
        return input;
    }

    static void consume(List<String> names) {
        System.out.println("consume=" + names);
    }

    static List<String> fromReturn() {
        return identity(Collections.emptyList());
    }

    public static void main(String[] args) {
        List<String> direct = Collections.emptyList();

        // Target type phối hợp với lời gọi generic lồng nhau.
        List<String> nested = identity(Collections.emptyList());
        consume(identity(Collections.emptyList()));

        // Diamond và lời gọi generic bên trong cùng được suy luận.
        List<String> constructed =
                new ArrayList<>(Collections.emptyList());

        // Type witness khắc phục ranh giới receiver trong method chain.
        List<String> chained = Collections.<String>emptyList()
                .stream()
                .toList();

        // Lambda và method reference nhận kiểu từ functional interface.
        Supplier<List<String>> factory = Collections::emptyList;
        Function<String, Integer> length = String::length;

        // Reference conditional expression nhận target List<String>.
        List<String> selected = true
                ? Collections.emptyList()
                : List.of("fallback");

        // Dù dùng var, đối số constructor đã cho thông tin kiểu.
        var inferred = new ArrayList<>(List.of("A", "B"));

        System.out.println("direct=" + direct);
        System.out.println("nested=" + nested);
        System.out.println("constructed=" + constructed);
        System.out.println("chained=" + chained);
        System.out.println("factory=" + factory.get());
        System.out.println("length=" + length.apply("Java"));
        System.out.println("selected=" + selected);
        System.out.println("inferred=" + inferred);
        System.out.println("fromReturn=" + fromReturn());
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac PolyTargetDemo.java
java PolyTargetDemo
~~~

Stdout chính xác:

~~~text
consume=[]
direct=[]
nested=[]
constructed=[]
chained=[]
factory=[]
length=4
selected=[]
inferred=[A, B]
fromReturn=[]
~~~

**Vì sao selected là danh sách rỗng?** Điều kiện true chọn nhánh Collections.emptyList(). Nhánh còn lại List.of("fallback") vẫn hợp lệ về kiểu nhưng không chạy. Hàm length.apply("Java") trả về 4 vì String "Java" có bốn ký tự.

Đối với mã thử nghiệm có dòng static <T> T identity(T input), target type phía ngoài có thể ràng buộc T và lời gọi inner emptyList(). Với dòng chained, việc viết type witness trực tiếp tại emptyList() đảm bảo receiver có kiểu List<String> trước khi gọi stream().

## 9. Những đoạn cố ý không biên dịch và cách sửa

Các ví dụ dưới đây là **mảnh mã độc lập để nhận diện lỗi**, không được chèn nguyên vào chương trình PolyTargetDemo.java.

### Sai A: target type không đi ngược qua receiver

~~~java
List<String> x = Collections.emptyList().stream().toList(); // Không biên dịch.
~~~

Lý do: lời gọi emptyList() ở vị trí receiver được suy luận List<Object>; sau đó toList() cho List<Object>, không thể gán cho List<String>.

~~~java
List<String> x = Collections.<String>emptyList().stream().toList();
~~~

Sửa bằng type witness ở lời gọi cần ràng buộc kiểu, trước chuỗi stream().

### Sai B: kỳ vọng biến trung gian thay đổi kiểu hồi tố

~~~java
var values = Collections.emptyList(); // List<Object>.
List<String> names = values;          // Không biên dịch.
~~~

Lý do: suy luận kiểu hoàn tất ở dòng khai báo values. Generic trong Java là invariant, nên List<Object> không thể tự chuyển thành List<String>.

~~~java
List<String> values = Collections.emptyList();
List<String> names = values;
~~~

Sửa bằng cách cung cấp kiểu đích ngay ở nơi tạo biến values.

### Sai C: dùng var trực tiếp với lambda/method reference

~~~java
var mapper = (String s) -> s.length(); // Không biên dịch.
var supplier = Collections::emptyList; // Không biên dịch.
~~~

Sửa bằng cách khai báo functional interface cụ thể:

~~~java
Function<String, Integer> mapper = s -> s.length();
Supplier<List<String>> supplier = Collections::emptyList;
~~~

### Sai D: nghĩ mọi nested method invocation đều nhận một target type duy nhất

So sánh:

~~~java
List<String> works = identity(Collections.emptyList());
List<String> fails = Collections.emptyList().stream().toList();
~~~

Lệnh thứ nhất có liên kết ràng buộc generic từ identity đến đối số emptyList(); lệnh thứ hai có **receiver boundary** nên không được suy luận cùng cách. “Lồng nhau” chỉ mô tả hình thức cú pháp; **vị trí lồng nhau** mới quyết định ngữ cảnh kiểu.

### Sai E: tưởng Object là raw type hoặc kiểu động

~~~java
var source = Collections.emptyList(); // List<Object>, có generic cụ thể.
~~~

List<Object> khác với raw List và List<?>:

- **List<Object>** cho phép thêm Object và các subtype nếu danh sách có thể sửa đổi.
- **Raw List** bỏ qua thông tin tham số generic và có thể dẫn tới cảnh báo unchecked.
- **List<?>** là danh sách có kiểu phần tử chưa biết, không thể tự do thêm giá trị khác null.

Tuy nhiên, riêng Collections.emptyList() trả về **danh sách rỗng không thể thay đổi**. Việc suy luận thành List<Object> không có nghĩa là gọi add sẽ thành công khi chạy. Mã đúng kiểu vẫn có thể ném UnsupportedOperationException nếu collection không hỗ trợ thay đổi.

## 10. Quy trình tự phân tích một biểu thức generic phức tạp

Khi không hiểu vì sao compiler chọn Object, String hoặc báo incompatible types, hãy đi theo các bước sau:

1. **Khoanh toàn bộ biểu thức được gán/trả về/truyền làm đối số** và ghi kiểu đích dự kiến nếu có.
2. **Tìm các generic method invocation**: xem khai báo phương thức, tham số kiểu, kiểu trả về và các tham số hình thức.
3. **Xác định ngữ cảnh của từng lời gọi cụ thể**: assignment, invocation, return hay receiver của một lời gọi tiếp theo.
4. **Tìm nguồn ràng buộc thật**: kiểu đích, đối số thực, type witness, diamond và cận trên của type parameter.
5. **Xác định ranh giới suy luận**: biến trung gian đã chốt kiểu; generic invocation ở vị trí receiver có thể không nhận target ngoài cùng.
6. **Kiểm tra kết quả kiểu tĩnh trước khi gọi tiếp**: List<Object> → Stream<Object> → List<Object> sẽ không tự đổi thành List<String>.
7. **Sửa tại nơi thiếu thông tin kiểu**: ghi kiểu tường minh ở biến, type witness ở generic method, tham số kiểu ở constructor, hoặc giữ biểu thức trong ngữ cảnh có target type phù hợp.

Không nên sửa bằng ép kiểu mù hoặc raw type chỉ để compiler chấp nhận: cách đó che mất nguyên nhân và có thể tạo cảnh báo unchecked hay lỗi runtime.

## 11. Tổng hợp theo ngữ cảnh

| Biểu thức | Kiểu đích sẵn có? | Điều xảy ra |
| --- | --- | --- |
| List<String> a = Collections.emptyList(); | Có, List<String> | T của emptyList được suy luận String. |
| var a = Collections.emptyList(); | Không có kiểu đích String | a là List<Object>. |
| List<String> a = identity(Collections.emptyList()); | Có, List<String> | Suy luận generic lồng nhau có thể phối hợp ràng buộc. |
| consume(Collections.emptyList()); | Có, tham số List<String> | Đối số generic được suy luận phù hợp với tham số formal. |
| List<String> a = Collections.emptyList().stream().toList(); | Có ở phép gán ngoài, nhưng thiếu ở receiver | emptyList suy luận List<Object>; phép gán lỗi. |
| List<String> a = Collections.<String>emptyList().stream().toList(); | String được chỉ rõ tại emptyList | Chuỗi có List<String> và biên dịch. |
| List<String> a = new ArrayList<>(); | Có, List<String> | Diamond suy luận ArrayList<String>. |
| var a = new ArrayList<>(); | Không có ràng buộc E cụ thể | a là ArrayList<Object>. |
| Function<String,Integer> f = String::length; | Có, functional interface | Method reference được kiểm tra theo target type. |
| var f = String::length; | Không có functional interface | Không biên dịch. |

Các dòng ví dụ có tên biến a/f giống nhau trong bảng là **các tình huống riêng**, không phải một chương trình được viết nối tiếp.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao List<String> x = identity(Collections.emptyList()); có thể hợp lệ trong khi var temp = Collections.emptyList(); rồi gán temp cho List<String> lại lỗi?

**Đáp án:** Trường hợp đầu giải ràng buộc generic trong một biểu thức đang có kiểu đích List<String>. Trường hợp sau chốt kiểu temp là List<Object> trước; kiểu đích tại phép gán sau không suy luận lại biến đó.

**Câu 2.** Tại sao List<String> x = Collections.emptyList().stream().toList(); lỗi dù vế trái đã ghi String?

**Đáp án:** emptyList() đứng ở vị trí receiver của .stream(), thiếu target List<String>; nó được suy luận List<Object> rồi loại phần tử Object lan tiếp qua Stream và toList.

**Câu 3.** Có hai cách sửa trường hợp ở câu 2 mà không dùng raw type hoặc unchecked cast?

**Đáp án:** Viết Collections.<String>emptyList().stream().toList(), hoặc tạo biến trung gian List<String> source = Collections.emptyList(); rồi gọi source.stream().toList().

**Câu 4.** Vì sao var f = String::length; không biên dịch, nhưng Function<String, Integer> f = String::length; lại hợp lệ?

**Đáp án:** Method reference cần một target functional interface để xác định chữ ký hàm; var không cung cấp chữ ký đó ở initializer.

**Câu 5.** var values = new ArrayList<>(List.of("A")); có chắc chắn là ArrayList<Object> không?

**Đáp án:** Không. Đối số List.of("A") mang thông tin phần tử String, giúp constructor suy luận ArrayList<String> ngay tại initializer.

**Ghi nhớ:** Type inference dựa trên **ràng buộc tại vị trí biểu thức được kiểm tra**. Kiểu đích có thể hỗ trợ lời gọi generic lồng nhau khi các quy tắc poly expression cho phép, nhưng không lan truyền vô điều kiện qua receiver, biến trung gian hoặc mọi kiểu biểu thức. Khi compiler suy luận không đúng ý định, cung cấp kiểu ngay tại điểm ranh giới nơi thông tin bị thiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-008">var với diamond và trường hợp suy luận thành Object</a>

<details>
<summary>Xem chi tiết</summary>

# var với diamond và trường hợp Java suy luận kiểu thành Object

## 1. Vấn đề cần hiểu

Trong Java Generics, hai cú pháp thường xuất hiện cùng nhau:

- **var**: từ Java 10, cho phép trình biên dịch suy luận kiểu **của biến cục bộ** từ biểu thức khởi tạo.
- **Diamond operator (<>)**: từ Java 7, cho phép bỏ bớt tham số kiểu khi tạo đối tượng generic; trình biên dịch sẽ suy luận tham số kiểu dựa trên các ràng buộc và ngữ cảnh đang có.

Khi viết:

~~~java
var items = new ArrayList<>();
~~~

nhiều người tưởng items sẽ tự trở thành danh sách String sau khi thêm một chuỗi. Thực tế, với ArrayList<E> không có ràng buộc cụ thể nào cho E trong câu lệnh này, Java suy luận **E = Object**. Kiểu tĩnh của items là **ArrayList<Object>**. Các lệnh thêm phần tử về sau **không làm Java suy luận lại kiểu**.

Điểm mấu chốt: **var suy luận từ biểu thức khởi tạo, còn diamond phải giải bài toán suy luận tham số generic ngay tại lúc tạo đối tượng.** Nếu cả hai phía đều thiếu thông tin kiểu cụ thể, tham số generic không bị ràng buộc thường được suy luận thành Object.

## 2. Phân biệt kiểu của biến và tham số generic

Xem ba khai báo:

~~~java
List<String> first = new ArrayList<>();
var second = new ArrayList<String>();
var third = new ArrayList<>();
~~~

| Khai báo | Kiểu tĩnh của biến | Tham số kiểu của ArrayList | Vì sao? |
| --- | --- | --- | --- |
| first | List<String> | String | Vế trái có kiểu đích List<String>, nên diamond suy ra String. |
| second | ArrayList<String> | String | Vế phải ghi rõ String; var suy ra kiểu của biểu thức khởi tạo. |
| third | ArrayList<Object> | Object | var không cung cấp một kiểu đích cụ thể cho diamond, và constructor rỗng không ràng buộc E. |

Trong khai báo đầu tiên, kiểu biến là **List<String>**, không phải ArrayList<String>, dù đối tượng được tạo là ArrayList<String>. Trong hai khai báo dùng var, kiểu tĩnh của biến theo kiểu biểu thức khởi tạo là **ArrayList<...>**, nên có thể dùng các phương thức riêng của ArrayList.

**var không có nghĩa là dynamic, Object hoặc kiểu bất kỳ.** Sau khi trình biên dịch xác định được kiểu, biến vẫn được kiểm tra kiểu tĩnh như mọi khai báo Java khác.

## 3. Vì sao var + diamond có thể cho ra Object?

Ví dụ sau là mã hợp lệ:

~~~java
import java.util.ArrayList;

class Example {
    public static void main(String[] args) {
        var values = new ArrayList<>();
        values.add("Java");
        values.add(21);
    }
}
~~~

Cơ chế suy luận của câu lệnh khai báo:

1. Trình biên dịch gặp biểu thức new ArrayList<>().
2. Constructor không tham số không đưa ra đối số nào để xác định E.
3. Với khai báo var, phía trái không có kiểu khai báo như List<String> để áp ràng buộc String cho biểu thức khởi tạo.
4. E của ArrayList<E> không bị ràng buộc cụ thể; giới hạn trên mặc định là Object, nên diamond được suy luận thành ArrayList<Object>.
5. var nhận kiểu tĩnh ArrayList<Object>. Các lệnh add phía sau chỉ được kiểm tra theo kiểu đã suy luận; chúng không sửa quyết định ở bước 4.

Vì Object là kiểu cha của String và Integer, cả hai lệnh add trên đều hợp lệ. Tuy vậy, danh sách này **không tương đương List<String>** và không thể sử dụng tại nơi yêu cầu List<String>.

Nếu tham số generic có cận trên khác Object, kết quả mặc định có thể chịu ảnh hưởng của cận đó. Vì vậy không nên biến câu “diamond luôn ra Object khi đi cùng var” thành quy tắc tuyệt đối: **Object là kết quả điển hình khi tham số kiểu không có thông tin cụ thể và cận trên mặc định là Object**.

## 4. Ví dụ chạy đầy đủ trên Java 17+

Lưu đoạn sau thành tệp **VarDiamondDemo.java**.

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VarDiamondDemo {
    public static void main(String[] args) {
        // Không có kiểu đích hoặc đối số ràng buộc E:
        var mixed = new ArrayList<>(); // ArrayList<Object>
        mixed.add("Java");
        mixed.add(17);

        // Vế trái cung cấp kiểu đích:
        List<String> words = new ArrayList<>();
        words.add("Java");

        // Khi dùng var, ghi rõ tham số generic ở vế phải:
        var explicit = new ArrayList<String>();
        explicit.add("Generics");

        // Đối số constructor cung cấp thông tin suy luận E:
        var inferred = new ArrayList<>(List.of("Java", "Kotlin"));
        inferred.add("Scala");

        // Cả K và V đều thiếu ràng buộc cụ thể:
        var attributes = new HashMap<>(); // HashMap<Object, Object>
        attributes.put("version", 17);
        attributes.put(99, true);

        System.out.println("mixed = " + mixed);
        System.out.println("words = " + words);
        System.out.println("explicit = " + explicit);
        System.out.println("inferred = " + inferred);
        System.out.println("version = " + attributes.get("version"));
        System.out.println("key 99 = " + attributes.get(99));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac VarDiamondDemo.java
java VarDiamondDemo
~~~

Kết quả chuẩn:

~~~text
mixed = [Java, 17]
words = [Java]
explicit = [Generics]
inferred = [Java, Kotlin, Scala]
version = 17
key 99 = true
~~~

Ở trường hợp **inferred**, List.of("Java", "Kotlin") tạo List<String>, nên đối số constructor giúp ArrayList<E> suy ra E là String ngay khi khởi tạo. Dùng var không bắt buộc tham số kiểu phải là Object; Java vẫn tận dụng thông tin kiểu xuất hiện **trong chính biểu thức khởi tạo**.

Các lệnh sau **cố ý gây lỗi biên dịch** và không thuộc chương trình chạy ở trên:

~~~java
var mixed = new ArrayList<>();  // ArrayList<Object>
List<String> wrong = mixed;     // Lỗi: ArrayList<Object> không chuyển thành List<String>

var inferred = new ArrayList<>(List.of("Java"));
inferred.add(42);              // Lỗi: add đòi hỏi String, không nhận int
~~~

Trong Java, generic là **bất biến (invariant)**: ArrayList<Object> không phải subtype của List<String>. Dù String là subtype của Object, quan hệ này không tự mở rộng thành List<String> là subtype của List<Object> hoặc ngược lại.

## 5. Khi có kiểu đích, khi không có kiểu đích

### 5.1. Vế trái khai báo kiểu tường minh

~~~java
List<String> names = new ArrayList<>();
~~~

Vế trái cung cấp **target type** List<String>. Diamond dùng thông tin đó để suy ra tham số String cho ArrayList. Đây là cách viết thường được ưu tiên khi muốn biểu lộ hợp đồng qua interface List.

### 5.2. var không tự tạo kiểu đích String

~~~java
var names = new ArrayList<>(); // ArrayList<Object>
~~~

Bản thân từ khóa var nói rằng trình biên dịch phải tìm kiểu từ initializer; nó không gợi ý String hay Number. Việc sau đó gọi names.add("Alice") không biến names thành ArrayList<String>.

### 5.3. Đối số constructor vẫn giúp suy luận

~~~java
var names = new ArrayList<>(List.of("Alice", "Bob"));
// ArrayList<String>
~~~

Constructor ArrayList(Collection<? extends E>) nhận Collection của phần tử String, tạo ràng buộc để suy ra E là String. Nguồn thông tin đến từ đối số constructor, không phải từ hành động add về sau.

Cần phân biệt:

~~~java
var fixed = new ArrayList<>(List.of("Alice")); // ArrayList<String>
var empty = new ArrayList<>(List.of());        // ArrayList<Object>
~~~

List.of() không có phần tử, và ở đây không có thông tin bổ sung buộc tham số kiểu thành String. Nếu mục tiêu là danh sách String rỗng, hãy cung cấp thông tin kiểu rõ ràng.

### 5.4. Lời gọi phương thức generic cũng có thể suy luận Object

~~~java
var first = java.util.Collections.emptyList();            // List<Object>
List<String> second = java.util.Collections.emptyList();  // List<String>
var third = java.util.Collections.<String>emptyList();    // List<String>
~~~

Ở dòng second, kiểu đích List<String> giúp phương thức generic suy ra String. Ở dòng third, **type witness** <String> xác định trực tiếp tham số kiểu của lời gọi phương thức. Đây là hiện tượng suy luận generic nói chung, không riêng constructor dùng diamond.

## 6. Các cách khai báo đúng với ý định

Khi cần danh sách String rỗng, có thể chọn một trong ba cách:

~~~java
// Ưu tiên kiểu interface khi chỉ cần hợp đồng List:
List<String> a = new ArrayList<>();

// Muốn dùng var nhưng vẫn giữ String:
var b = new ArrayList<String>();

// Muốn giữ diamond và có đối số mang thông tin String:
var c = new ArrayList<>(List.<String>of());
~~~

Cả a, b, c đều kiểm tra kiểu phần tử là String. Trong đó a có kiểu biến List<String>; b và c có kiểu biến ArrayList<String>. Với c, lời gọi List.<String>of() tạo List<String> rỗng để constructor có ràng buộc kiểu.

Với HashMap rỗng, vấn đề tương tự trên **hai** tham số kiểu:

~~~java
var unsafeForIntent = new java.util.HashMap<>(); // HashMap<Object, Object>
java.util.Map<String, Integer> byInterface = new java.util.HashMap<>();
var explicit = new java.util.HashMap<String, Integer>();
~~~

Nếu cần Map<String, Integer>, hãy chọn byInterface hoặc explicit. Việc ban đầu chỉ put("level", 1) vào unsafeForIntent **không suy luận lại** khóa String và giá trị Integer.

Một cách khác là khởi tạo từ nguồn có kiểu, ví dụ:

~~~java
var copy = new ArrayList<>(List.of("A", "B")); // ArrayList<String>
~~~

Không nên cố tình thêm phần tử mẫu chỉ để ép kiểu: nếu danh sách được yêu cầu rỗng, khai báo tham số generic rõ ràng dễ đọc và đúng ý định hơn.

## 7. Lỗi thực tế và cách phát hiện

### Lỗi A: Sau khi khởi tạo mới phát hiện không truyền được vào API

~~~java
import java.util.ArrayList;
import java.util.List;

class Service {
    static void saveNames(List<String> names) {
        // Xử lý các tên.
    }

    static void run() {
        var names = new ArrayList<>();
        names.add("Lan");
        // saveNames(names); // Lỗi biên dịch: names là ArrayList<Object>.
    }
}
~~~

**Nguyên nhân:** tham số của saveNames yêu cầu List<String>, nhưng names được suy luận thành ArrayList<Object> khi khai báo. **Sửa:** dùng List<String> names = new ArrayList<>(); hoặc var names = new ArrayList<String>(); ngay từ đầu.

### Lỗi B: Cho rằng diamond và var có cùng nhiệm vụ

~~~java
var numbers = new ArrayList<>();
numbers.add(10);
numbers.add("ten"); // Vẫn hợp lệ vì numbers là ArrayList<Object>.
~~~

var suy luận kiểu **biến**; diamond suy luận tham số kiểu của **kiểu được tạo**. Hai cơ chế liên quan nhưng không thay thế nhau. Trình biên dịch không xem phần tử được thêm đầu tiên là kiểu chuẩn cho các phần tử về sau.

### Lỗi C: Nhầm List<Object>, raw List và List<?>

~~~java
List<Object> objects = new ArrayList<>();
List raw = new ArrayList(); // Raw type: bỏ kiểm tra tham số kiểu, dễ có cảnh báo.
List<?> unknown = List.of("a", "b");
Object item = unknown.get(0);
// unknown.add("c");       // Lỗi: không biết kiểu phần tử cụ thể của List<?>.
~~~

- **List<Object>**: có tham số kiểu cụ thể Object; có thể thêm đối tượng của nhiều kiểu con.
- **Raw List**: sử dụng generic mà bỏ tham số kiểu; dễ tạo thao tác không an toàn và cảnh báo unchecked.
- **List<?>**: danh sách có kiểu phần tử chưa biết; có thể đọc dưới dạng Object, nhưng không tự do thêm phần tử tùy ý (ngoại trừ null).

ArrayList<Object> được suy luận bởi var + diamond là **kiểu generic có tham số Object**, không phải raw ArrayList.

### Lỗi D: Nhầm suy luận kiểu với kiểm tra kiểu lúc chạy

Java dùng **type erasure** cho phần lớn thông tin tham số generic khi chạy. Trình biên dịch vẫn biết biến là ArrayList<String> hay ArrayList<Object> khi kiểm tra lệnh add, phép gán và tham số phương thức. Không nên suy ra rằng vì không đọc được tham số kiểu trực tiếp từ đối tượng ArrayList tại runtime thì var sẽ có kiểu động.

### Lỗi E: Nghĩ var hợp lệ ở mọi nơi

var có các điều kiện sử dụng. Chẳng hạn, biến cục bộ dùng var phải có initializer để suy luận kiểu, và không thể chỉ khởi tạo bằng null:

~~~java
// var data;       // Lỗi: không có initializer.
// var data = null; // Lỗi: không có kiểu cụ thể để suy luận.
// var data = new ArrayList<>(); // Hợp lệ nhưng suy luận ArrayList<Object>.
~~~

Các câu bình luận trong ví dụ này nhằm minh họa cú pháp không hợp lệ; chúng không phải các khai báo để chạy.

## 8. Chọn cách viết nào trong mã thực tế?

- **Khai báo rõ kiểu ở phía trái** khi cần một hợp đồng ổn định qua interface hoặc khi initializer ngắn nhưng có thể mất thông tin generic: List<String> names = new ArrayList<>();.
- **Dùng var với kiểu generic tường minh ở phía phải** nếu muốn làm nổi bật cách khởi tạo cụ thể: var names = new ArrayList<String>();.
- **Dùng var và diamond cùng nhau** khi dữ liệu đầu vào của constructor hoặc những ràng buộc khác đã cung cấp chính xác kiểu mong muốn, ví dụ new ArrayList<>(List.of("A", "B"));.
- **Không tự động kết luận Object** nếu đã có đối số hoặc cận generic cụ thể; hãy xét toàn bộ ràng buộc tại initializer.
- **Kiểm tra ngay kiểu suy luận của biến** trong IDE hoặc bằng thử gán vào một kiểu mong đợi; đừng chỉ nhìn việc add một phần tử có biên dịch hay không.

Đặc biệt, một danh sách tạo bằng List.of(...) là danh sách **không thể thay đổi kích thước**; nó khác với ArrayList mới được tạo từ List.of(...). Dù cả hai có thể được suy luận là List<String>/ArrayList<String>, chỉ ArrayList trong ví dụ trên cho phép add. Đây là khác biệt về hành vi collection, không phải khác biệt về suy luận generic.

## 9. Câu hỏi tự kiểm tra

**1.** var a = new ArrayList<>(); a.add("x"); thì a có kiểu gì?

**Đáp án:** ArrayList<Object>. Lời gọi add("x") không thay đổi kết quả suy luận lúc khai báo.

**2.** var b = new ArrayList<>(List.of("x")); b.add(1); có hợp lệ không?

**Đáp án:** Không. b là ArrayList<String> do tham số của List.of("x") giúp suy luận String; add(1) không thỏa kiểu String.

**3.** Khác biệt giữa List<String> c = new ArrayList<>(); và var c = new ArrayList<String>(); là gì?

**Đáp án:** Cả hai giữ phần tử kiểu String; ở cách thứ nhất kiểu tĩnh của biến là interface List<String>, còn cách thứ hai là lớp cụ thể ArrayList<String>.

**4.** Vì sao var map = new HashMap<>(); không tự có kiểu HashMap<String, Integer> sau khi gọi put("x", 1)?

**Đáp án:** K và V được suy luận khi khởi tạo, thông thường là Object khi không có ràng buộc; lệnh put đến sau không tái suy luận chúng.

**Ghi nhớ:** Muốn generic có tham số kiểu cụ thể, hãy cung cấp thông tin đó **ngay tại câu lệnh khởi tạo** qua kiểu đích, tham số generic viết tường minh hoặc đối số/kiểu trả về có ràng buộc phù hợp. var giúp bỏ lặp kiểu, nhưng không đoán được ý định chưa xuất hiện trong biểu thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-inference-009">Đọc và xử lý thông báo inference variable has incompatible bounds</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc và xử lý thông báo `inference variable ... has incompatible bounds` trong Java Generics

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Xác định **biến kiểu đang được suy luận** (`T`, `U`, `K`, `V`…) trong thông báo lỗi `javac` và tìm đúng phương thức generic sinh ra biến đó.
- Đọc được các nhóm ràng buộc `equality constraints`, `lower bounds`, `upper bounds`, hiểu vì sao chúng không thể cùng thỏa mãn.
- Phân biệt lỗi do **kiểu đích**, đối số truyền vào, giới hạn generic (`extends`), tính bất biến của `List<T>`, và API dùng generic lồng nhau như `Comparator.comparing` hoặc `Collectors.toMap`.
- Sửa lỗi bằng cách làm rõ **ý nghĩa kiểu dữ liệu** hoặc điều chỉnh chữ ký generic, thay vì ép kiểu mù quáng.

Phạm vi: **suy luận kiểu ở thời điểm biên dịch trong Java 17+**. Đây là lỗi kiểm tra kiểu tĩnh; không phải lỗi do type erasure lúc chạy chương trình.

## 2. Một thông báo lỗi điển hình

Xét file `Broken.java`:

```java
import java.util.List;

public class Broken {
    static <T> List<T> singleton(T value) {
        return List.of(value);
    }

    public static void main(String[] args) {
        List<String> words = singleton(42); // Không biên dịch
    }
}
```

Chạy:

```bash
javac Broken.java
```

Phần cốt lõi của thông báo từ `javac` có dạng (cách trình bày, tên đầy đủ và số dòng có thể thay đổi theo phiên bản JDK):

```text
incompatible types: inference variable T has incompatible bounds
    equality constraints: java.lang.String
    lower bounds: java.lang.Integer
```

Đừng chỉ đọc dòng “incompatible types”. Hãy đọc ba thành phần:

| Thành phần | Cách hiểu trong ví dụ |
| --- | --- |
| `inference variable T` | `T` trong khai báo `<T> List<T> singleton(T value)`, là ẩn số mà trình biên dịch cần tìm. |
| `equality constraints: String` | Vì kết quả cần gán cho `List<String>`, chữ ký trả về `List<T>` buộc `T` là `String`. |
| `lower bounds: Integer` | Đối số `42` được boxing thành `Integer`; để truyền vào tham số `T`, kiểu của đối số phải tương thích với `T` (nôm na `Integer` là một cận dưới của `T`). |

Hai ràng buộc cùng tồn tại đòi hỏi `T` vừa bằng `String`, vừa chấp nhận giá trị `Integer`. Không có cách chọn `T` thỏa cả hai, nên trình biên dịch dừng.

**“Bounds” ở đây là các ràng buộc của bài toán suy luận kiểu**, không nhất thiết nghĩa là bạn đã viết `T extends ...`. Một phương thức chỉ khai báo `<T>` vẫn có thể báo `incompatible bounds`.

## 3. Java suy luận kiểu từ đâu?

Xem lại chữ ký:

```java
static <T> List<T> singleton(T value)
```

Tại lời gọi `singleton(42)`, trình biên dịch xét đồng thời:

1. **Kiểu của biểu thức đối số**: literal `42` có kiểu `int`; khi phù hợp với tham số kiểu tham chiếu `T`, Java xét boxing thành `Integer`.
2. **Kiểu đích (target type)**: phép gán `List<String> words = ...` yêu cầu một biểu thức tương thích với `List<String>`.
3. **Chữ ký phương thức**: kiểu tham số `T` và kiểu trả về `List<T>` đặt quan hệ giữa đối số, kết quả và kiểu đích.
4. **Giới hạn generic khai báo**, nếu có: ví dụ `<T extends Number>` thêm điều kiện `T` phải là subtype của `Number`.

Đây là một dạng *constraint solving* khi biên dịch. Không phải Java chạy chương trình rồi đoán kiểu; phép suy luận thất bại thì bytecode không được tạo.

### 3.1. Ba nhóm ràng buộc thường gặp

- **Equality constraints** (`T = X`): thường xuất hiện khi cấu trúc tham số hóa và kiểu đích yêu cầu cùng một đối số kiểu, chẳng hạn `List<T>` phải tương thích với `List<String>`. Vì generic Java **bất biến**, `List<Integer>` không phải subtype của `List<Number>`.
- **Lower bounds** (`L <: T`): `T` phải đủ rộng để chấp nhận kiểu `L` của giá trị đưa vào; ví dụ `Integer` phải tương thích với `T`.
- **Upper bounds** (`T <: U`): `T` phải thỏa giới hạn phía trên, chẳng hạn `T extends Number` hoặc giới hạn Comparable của một API.

Ký hiệu `A <: B` đọc là “`A` là subtype của `B`”. Đây là cách đọc trực quan hữu ích khi debug; chi tiết thuật toán suy luận thực tế của Java còn xét chuyển đổi biểu thức, capture conversion và các bước quy giản ràng buộc.

### 3.2. Đừng đọc nhầm tên biến kiểu thành lớp thật

Trong thông báo:

```text
inference variable U has incompatible bounds
```

`U` thường chỉ là tham số kiểu thứ hai của một phương thức thư viện, **không phải tên lớp `U`**. Ví dụ `Comparator.comparing` khai báo hai tham số kiểu `T` (đối tượng cần so sánh) và `U` (kiểu khóa so sánh). Muốn biết `U` là gì, hãy tìm khai báo generic của phương thức ở vị trí lỗi, không chỉ nhìn biến cục bộ nơi mũi tên `^` trỏ tới.

## 4. Tình huống 1: Kiểu đích xung đột với kiểu đối số

Đoạn sai:

```java
List<String> words = singleton(42);
```

Có ít nhất ba cách sửa, mỗi cách mang ý nghĩa khác nhau:

```java
List<Integer> numbers = singleton(42);             // Muốn danh sách số
List<String> words = singleton(String.valueOf(42)); // Muốn danh sách chuỗi
List<Number> values = singleton(42);                // Muốn kiểu số tổng quát
```

- Sửa **kiểu đích** nếu giá trị thực sự là số.
- **Chuyển đổi dữ liệu** có chủ đích nếu nghiệp vụ cần chuỗi.
- Chọn kiểu rộng hơn (`Number`) nếu API cần chấp nhận nhiều loại số. Đừng đổi thành `Object` chỉ để tắt lỗi khi về sau vẫn cần thao tác đặc thù của `String`.

Một cách tưởng như hợp lý nhưng vẫn sai:

```java
List<String> words = Broken.<String>singleton(42); // Vẫn lỗi
```

Việc ghi rõ `<String>` chỉ yêu cầu `T = String` sớm hơn; nó không biến `Integer` thành `String`.

### 4.1. Vai trò của `var`

```java
var result = singleton(42); // Suy luận result là List<Integer>
```

Ở đây không có đích `List<String>` áp đặt lên lời gọi, nên Java có thể suy luận `T` từ đối số. Tuy nhiên, đổi mọi khai báo thành `var` không phải giải pháp chung: `var` có thể khiến kiểu suy luận được khác với hợp đồng API mà bạn mong muốn. Hãy kiểm tra kiểu được suy luận trước khi quyết định.

## 5. Tình huống 2: Ràng buộc `extends` xung đột với kiểu đích

Một phương thức chỉ nhận kiểu số:

```java
static <T extends Number> T keepNumber(T value) {
    return value;
}
```

Lời gọi sau sai:

```java
String text = keepNumber(10); // Không biên dịch
```

Ta có đồng thời:

- `T` phải thỏa `T extends Number` → `T` không thể là `String`.
- Biểu thức được gán vào `String` → kiểu đích muốn một kết quả là `String`.
- Đối số số nguyên dẫn đến ràng buộc từ `Integer`.

Hãy sửa theo mục đích:

```java
Number value = keepNumber(10);               // Giữ số
String text = keepNumber(10).toString();     // Chuyển số sang chuỗi
```

Thông báo lỗi có thể hiển thị `upper bounds: Number` (hoặc một biểu diễn phức tạp hơn khi có nhiều giới hạn) cùng ràng buộc từ kiểu đích. Lưu ý rằng không phải mọi dòng `upper bounds` đều đến từ `extends` viết ngay trong phương thức của bạn; chúng cũng có thể đến từ chữ ký của API được gọi.

## 6. Tình huống 3: API yêu cầu khóa `Comparable` nhưng khóa không hỗ trợ so sánh

Chữ ký khái quát của overload một đối số:

```java
static <T, U extends Comparable<? super U>>
Comparator<T> comparing(Function<? super T, ? extends U> keyExtractor)
```

Đoạn dưới không hợp lệ:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

record Packet(String id, byte[] payload) {}

List<Packet> packets = new ArrayList<>();
packets.sort(Comparator.comparing(Packet::payload)); // Không biên dịch
```

`Packet::payload` trả về `byte[]`. Trong `Comparator.comparing`, `U` phải là kiểu có thể so sánh bằng `Comparable<? super U>`; mảng `byte[]` **không triển khai `Comparable`**. Vì vậy trình biên dịch có thể báo:

```text
inference variable U has incompatible bounds
    upper bounds: Comparable<? super U>
    lower bounds: byte[]
```

Cách sửa phụ thuộc **quy tắc xếp thứ tự** mong muốn.

Nếu sắp theo độ dài payload:

```java
packets.sort(Comparator.comparingInt(p -> p.payload().length));
```

`comparingInt` nhận khóa `int`, không đòi mảng byte phải tự `Comparable`. Nếu nghiệp vụ thực sự muốn so sánh nội dung byte, hãy cung cấp một `Comparator<byte[]>` có ngữ nghĩa rõ ràng qua overload hai tham số `Comparator.comparing(keyExtractor, keyComparator)`.

**Không nên** sửa bằng cách ép `(Comparable) packet.payload()` vì mảng không bỗng trở thành `Comparable`; một cast như vậy chỉ đẩy vấn đề sang lỗi runtime.

## 7. Tình huống 4: Generic lồng nhau trong Stream/Collector

Ví dụ:

```java
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

Map<String, Long> lengths = Stream.of("a", "abc")
        .collect(Collectors.toMap(Function.identity(), String::length)); // Sai
```

`String::length` trả về `int`, được boxing thành `Integer` nếu cần kiểu tham chiếu. Nhưng kiểu đích lại yêu cầu **giá trị trong `Map` là `Long`**. `Integer` và `Long` là hai lớp khác nhau; Java không tự thực hiện chuỗi chuyển đổi `int -> long -> Long` trong mọi ngữ cảnh suy luận kiểu generic này.

Để debug, tách hợp đồng:

```text
Đích:              Map<String, Long>
toMap(keyMapper, valueMapper):
                   Map<K, U>
keyMapper:         String -> String  => K phù hợp String
valueMapper:       String -> int     => U nhận Integer (qua boxing)
Đích đòi hỏi:      U = Long
Mâu thuẫn:         Integer không tương thích với Long
```

Hai cách sửa đúng:

```java
Map<String, Integer> lengthsAsInt = Stream.of("a", "abc")
        .collect(Collectors.toMap(Function.identity(), String::length));

Map<String, Long> lengthsAsLong = Stream.of("a", "abc")
        .collect(Collectors.toMap(Function.identity(), s -> (long) s.length()));
```

Ở cách thứ hai, `(long) s.length()` tạo giá trị `long`, sau đó boxing thành `Long`. Việc đổi kiểu ở **biểu thức sinh giá trị** làm hợp đồng rõ ràng hơn việc ép kiểu sau khi thu thập.

Khi lỗi xuất hiện ngay tại `.collect(...)`, `.map(...)` hoặc `Comparator.comparing(...)`, hãy truy ngược lambda/method reference bên trong. Mũi tên báo lỗi có thể nằm ở lời gọi ngoài cùng, trong khi mâu thuẫn nằm ở kiểu mà callback trả về.

## 8. Tình huống 5: Tính bất biến và wildcard `extends`/`super`

Giả sử có phương thức:

```java
static <T> void copy(List<T> target, List<T> source) {
    target.addAll(source);
}
```

Lời gọi này sai:

```java
List<Number> target = new ArrayList<>();
List<Integer> source = List.of(1, 2);
copy(target, source); // Không suy luận được cùng một T
```

Dù `Integer extends Number`, Java **không** coi `List<Integer>` là `List<Number>`. Khi cả hai tham số có dạng `List<T>`, phương thức buộc hai đối số phải tương thích với **cùng một** `T`. Bạn có thể thấy thông báo về `incompatible bounds`, `incompatible equality constraints`, hoặc “cannot infer type-variable(s)” tùy ngữ cảnh và phiên bản compiler.

Nếu ý định thật là *đọc phần tử từ nguồn và thêm vào đích*, ký hiệu wildcard làm hợp đồng chính xác hơn:

```java
static <T> void copy(
        List<? super T> target,
        List<? extends T> source) {
    for (T item : source) {
        target.add(item);
    }
}
```

Khi gọi với `List<Number>` và `List<Integer>`, có thể lấy `T = Integer`:

- `source` kiểu `List<? extends Integer>`: đọc được các phần tử như `Integer`.
- `target` kiểu `List<? super Integer>`: có thể thêm phần tử `Integer` vào `List<Number>`.

Đây là quy tắc **PECS**: *Producer Extends, Consumer Super*. Không thay `List<T>` bằng wildcard một cách máy móc. Chỉ dùng wildcard khi nó diễn tả đúng hướng dữ liệu và quyền đọc/ghi mà API dự định hỗ trợ.

## 9. Quy trình gỡ lỗi có hệ thống

Khi nhận thông báo, đi từ vị trí lỗi đến các ràng buộc:

1. **Giữ nguyên ví dụ lỗi** và chạy `javac -Xdiags:verbose Broken.java` khi cần thêm thông tin về phương thức/overload. Dùng JDK và tùy chọn biên dịch nhất quán với dự án.
2. **Xác định phương thức generic** xuất hiện trong biểu thức đang lỗi. Tìm khai báo `<T>`, `<T,U>`, kiểu tham số, kiểu trả về và các giới hạn `extends`. Với API thư viện, kiểm tra chữ ký trong Javadoc hoặc mã nguồn tương ứng JDK đang dùng.
3. **Khoanh đúng biến inference** mà compiler nêu, chẳng hạn `U` trong `Comparator.comparing` là kiểu khóa; không nhầm với `T` là kiểu phần tử.
4. **Ghi lại kiểu đích** ở phép gán, tham số phương thức bao ngoài hoặc kiểu trả về của lambda. Một biểu thức generic có thể chịu ràng buộc từ ngữ cảnh bên ngoài.
5. **Ghi lại kiểu thực** của từng đối số và lambda/method reference: primitive, boxing, kiểu mảng, kiểu generic, wildcard, `null`, overload có thể ảnh hưởng đến lựa chọn.
6. **Đặt ba nhóm ràng buộc cạnh nhau**: equality, lower, upper. Tìm cặp không cùng thỏa mãn: `String`/`Integer`, `Long`/`Integer`, hoặc `byte[]`/`Comparable`.
7. **Chọn sửa đổi đúng ý nghĩa dữ liệu**: thay kiểu đích, chuyển đổi kiểu tại nguồn, chọn overload có `Comparator`, điều chỉnh generic/wildcard, hoặc sửa API nếu thiết kế sai. Sau đó biên dịch lại và kiểm tra hành vi.

Bảng thao tác nhanh:

| Triệu chứng | Nơi cần kiểm tra đầu tiên | Hướng sửa thông thường |
| --- | --- | --- |
| `equality constraints: String`, `lower bounds: Integer` | Kiểu đích và đối số của phương thức generic | Giữ cùng kiểu hoặc chuyển đổi dữ liệu. |
| `upper bounds: Comparable...`, `lower bounds: byte[]` | Kiểu khóa được trả về từ key extractor | Đổi khóa thành kiểu có thứ tự hoặc truyền `Comparator` rõ ràng. |
| `U` suy luận là `Integer` nhưng đích yêu cầu `Long` | Hàm biến đổi giá trị của Stream/Collector | Tạo `long`/`Long` một cách chủ đích ở mapper. |
| Hai `List` mang kiểu phần tử khác nhau | Chữ ký dùng `List<T>` ở nhiều vị trí | Xác định có cần `? extends T`/`? super T` không. |
| Lỗi nằm tại lambda hay lời gọi phương thức ngoài | Kiểu tham số và giá trị trả về trong lambda | Thêm kiểu cục bộ hoặc tách biểu thức để cô lập xung đột. |

### 9.1. Kỹ thuật tách biểu thức phức tạp

Khi đoạn mã dài, khai báo kiểu cụ thể ở bước trung gian để làm rõ nơi ràng buộc xuất hiện:

```java
Function<String, Long> lengthMapper = s -> (long) s.length();

Map<String, Long> lengths = Stream.of("a", "abc")
        .collect(Collectors.toMap(Function.identity(), lengthMapper));
```

Nếu dòng khai báo `Function<String, Long>` không biên dịch, vấn đề nằm ở lambda. Nếu khai báo đó chạy được mà `.collect` lỗi, tiếp tục kiểm tra chữ ký `collect`, `toMap`, kiểu phần tử của stream và kiểu đích.

Có thể thử **type witness** để xác minh giả thuyết:

```java
List<Integer> numbers = Broken.<Integer>singleton(42);
```

Type witness chỉ chỉ rõ kiểu cần suy luận; nó **không chữa mâu thuẫn về ngữ nghĩa**. Nếu chọn `<String>` nhưng truyền `42`, lỗi vẫn đúng.

## 10. Ví dụ Java 17+ hoàn chỉnh, có thể chạy

Ví dụ sau thể hiện các sửa đổi có chủ đích cho từng tình huống: chọn đúng kiểu đích, `copy` với PECS, so sánh qua khóa `int`, chuyển `int` thành `long` khi tạo `Map`.

Tạo file `InferenceBoundsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InferenceBoundsDemo {
    record Packet(String id, byte[] payload) {}

    static <T> List<T> singleton(T value) {
        return List.of(value);
    }

    static <T> void copy(List<? super T> target, List<? extends T> source) {
        for (T item : source) {
            target.add(item);
        }
    }

    public static void main(String[] args) {
        List<String> names = singleton("Java");
        List<Integer> ids = singleton(42);

        List<Number> numbers = new ArrayList<>();
        copy(numbers, List.of(1, 2));

        List<Packet> packets = new ArrayList<>(List.of(
                new Packet("long", new byte[] {1, 2, 3}),
                new Packet("short", new byte[] {7})
        ));
        packets.sort(Comparator.comparingInt(p -> p.payload().length));

        Map<String, Long> lengths = Stream.of("a", "abc")
                .collect(Collectors.toMap(Function.identity(), s -> (long) s.length()));

        System.out.println(names);
        System.out.println(ids);
        System.out.println(numbers);
        System.out.println(packets.stream().map(Packet::id).toList());
        System.out.println(lengths.get("abc"));
    }
}
```

Biên dịch/chạy từ thư mục chứa file:

```bash
javac InferenceBoundsDemo.java
java InferenceBoundsDemo
```

Kết quả chuẩn:

```text
[Java]
[42]
[1, 2]
[short, long]
3
```

Giải thích: `names` và `ids` có kiểu phần tử đúng với đầu vào; `copy` cho phép đưa `Integer` vào `List<Number>`; `packets` sắp theo số phần tử của payload nên `short` đứng trước `long`; giá trị của khóa `"abc"` là `Long` biểu diễn độ dài 3.

## 11. Những cách “sửa” dễ gây lỗi khác

- **Ép kiểu thô** như `(List<String>) (List<?>) someList` có thể giấu sai lệch dữ liệu, sinh cảnh báo unchecked và gây `ClassCastException` khi dùng phần tử. Nó không chứng minh các ràng buộc generic đã được giải quyết đúng.
- **Dùng raw type** (`List` thay vì `List<String>`) làm mất kiểm tra kiểu ở thời điểm biên dịch. Chỉ dùng khi tương tác với API legacy có xử lý cảnh báo rõ ràng, không phải cách chữa lỗi suy luận thông thường.
- **Thêm `<Object>` khắp nơi** có thể làm mã biên dịch nhưng làm mất thông tin kiểu, buộc cast khi đọc và che giấu thiết kế API chưa hợp lý.
- **Suy luận rằng `List.of(1, "a")` luôn lỗi** là sai: Java có thể suy ra một kiểu cha chung cho các đối số khác nhau khi ngữ cảnh cho phép. Vấn đề là kiểu được suy ra có đáp ứng hợp đồng cụ thể (như `List<String>`) hay không.
- **Cho rằng boxing đồng nghĩa mọi chuyển đổi số đều hợp lệ** là sai: `Integer` không phải subtype của `Long`, và suy luận kiểu generic không tự động chuyển một đối tượng `Integer` thành đối tượng `Long`.
- **Chỉ sửa tại dòng compiler gạch chân** có thể bỏ lỡ nguyên nhân ở lambda, method reference hoặc kiểu trả về của phương thức được gọi ở sâu bên trong.

## 12. Tự kiểm tra hiểu bài

**Bài 1.** Với chữ ký `static <T> List<T> singleton(T value)`, vì sao `List<String> names = singleton(7)` lỗi, còn `List<Integer> ids = singleton(7)` hợp lệ?

<details>
<summary>Gợi ý đáp án</summary>

Kiểu đích đầu tiên đặt `T = String`, trong khi đối số cung cấp `Integer`. Kiểu đích thứ hai đặt `T = Integer`, phù hợp đối số đã boxing. Cách sửa phụ thuộc ý định: `singleton("7")` nếu muốn chuỗi, hoặc `List<Integer>` nếu muốn số.

</details>

**Bài 2.** Vì sao `Comparator.comparing(Packet::payload)` không thể sử dụng trực tiếp khi `payload()` trả `byte[]`? Nếu chỉ cần sắp theo số byte, thay bằng gì?

<details>
<summary>Gợi ý đáp án</summary>

`comparing` cần khóa `U` triển khai `Comparable<? super U>`; `byte[]` không triển khai giao diện đó. Dùng `Comparator.comparingInt(p -> p.payload().length)` để sắp theo độ dài.

</details>

**Bài 3.** `List<Number> target` và `List<Integer> source` nên đi cùng tham số phương thức nào khi chỉ sao chép phần tử từ `source` sang `target`?

<details>
<summary>Gợi ý đáp án</summary>

Có thể khai báo `<T> void copy(List<? super T> target, List<? extends T> source)` và thêm từng phần tử đọc từ nguồn vào đích. Với trường hợp này suy luận `T = Integer` là phù hợp. Cần hiểu rõ vai trò producer/consumer trước khi áp dụng wildcard.

</details>

## 13. Ghi nhớ khi gặp lỗi ở dự án thực tế

Một biến suy luận kiểu không thể có giá trị nếu đồng thời bị buộc bằng một kiểu, phải chấp nhận một kiểu đối số không tương thích và/hoặc phải thỏa một giới hạn trên không khả thi. Hãy đọc **tên biến kiểu → nơi khai báo → equality/lower/upper bounds → kiểu đích và đối số → ý nghĩa dữ liệu**, rồi sửa tại ràng buộc thực sự sai.

Thông báo `inference variable ... has incompatible bounds` là **manh mối giải bài toán kiểu** của compiler. Khi chuyển từng manh mối thành quan hệ giữa các kiểu cụ thể, lỗi tưởng khó đọc thường trở thành một sai lệch thiết kế hoặc chuyển đổi dữ liệu rất rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)
