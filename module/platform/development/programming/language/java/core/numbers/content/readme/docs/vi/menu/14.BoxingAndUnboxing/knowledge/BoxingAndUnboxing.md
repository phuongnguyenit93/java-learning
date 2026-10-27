<a id="back-to-top"></a>

# Boxing và unboxing

## Menu
- [Autoboxing và các ngữ cảnh chuyển primitive thành wrapper](#boxing-and-unboxing-001)
- [Unboxing trong gán, phép toán và lời gọi phương thức](#boxing-and-unboxing-002)
- [NullPointerException khi unbox giá trị null](#boxing-and-unboxing-003)
- [So sánh == giữa hai wrapper và giữa wrapper với primitive](#boxing-and-unboxing-004)
- [Cache wrapper và giới hạn không nên dùng làm hợp đồng nhận dạng](#boxing-and-unboxing-005)
- [List&lt;Integer&gt; không phải int&#91;&#93;](#boxing-and-unboxing-006)
- [Chi phí boxing trong vòng lặp và cấu trúc dữ liệu](#boxing-and-unboxing-007)
- [Objects.equals và xử lý wrapper có thể null](#boxing-and-unboxing-008)
- [Toán tử ?: có thể unbox nhánh wrapper](#boxing-and-unboxing-009)
- [Không dùng wrapper làm khóa đồng bộ](#boxing-and-unboxing-010)
- [Phân biệt tham chiếu null với giá trị số 0](#boxing-and-unboxing-011)

## <a id="boxing-and-unboxing-001">Autoboxing và các ngữ cảnh chuyển primitive thành wrapper</a>

<details>
<summary>Xem chi tiết</summary>

# Autoboxing và các ngữ cảnh chuyển primitive thành wrapper trong Java

## 1. Autoboxing là gì?

Trong Java, **primitive** là các kiểu dữ liệu nguyên thủy như `int`, `long`, `double`, `boolean`; còn **wrapper** là các lớp đóng gói một giá trị primitive tương ứng thành đối tượng. **Autoboxing** là việc trình biên dịch tự áp dụng *boxing conversion* trong những ngữ cảnh Java cho phép, để một giá trị nguyên thủy có thể được sử dụng ở nơi cần một đối tượng wrapper.

```java
int primitive = 42;
Integer boxed = primitive;  // Autoboxing: int -> Integer.
int again = boxed;          // Unboxing: Integer -> int.

System.out.println(boxed);  // 42
System.out.println(again);  // 42
```

Về mặt ý nghĩa, `Integer boxed = primitive` gần với `Integer.valueOf(primitive)`. Java không yêu cầu bạn viết `new Integer(...)` (constructor wrapper cũ đã bị deprecated và không nên dùng); có thể để trình biên dịch tạo chuyển đổi cần thiết. Tuy nhiên, **autoboxing không có nghĩa Java biến mọi kiểu số thành mọi lớp số tùy ý**; chuyển đổi phải hợp lệ theo đúng ngữ cảnh và kiểu đích.

Ví dụ nổi bật:

- Một danh sách `List<Integer>` chỉ nhận phần tử là tham chiếu `Integer`, nhưng bạn có thể gọi `list.add(5)`; Java box `5` thành `Integer`.
- Một phương thức nhận `Double` có thể được gọi với literal `1.5` kiểu `double` nhờ boxing.
- Một biến `Number n = 5;` hợp lệ: Java **box `int` thành `Integer`, sau đó mở rộng tham chiếu** lên `Number`.
- Ngược lại, `Long x = 5;` **không** hợp lệ: Java không tự thực hiện chuỗi `int -> long -> Long` trong phép gán thông thường. Cần `5L` hoặc chuyển đổi rõ ràng.

## 2. Bảng ánh xạ đầy đủ giữa tám primitive và wrapper

| Primitive | Wrapper | Ví dụ autoboxing | Cách tạo tường minh |
| --- | --- | --- | --- |
| `boolean` | `Boolean` | `Boolean b = true;` | `Boolean.valueOf(true)` |
| `byte` | `Byte` | `Byte b = (byte) 5;` | `Byte.valueOf((byte) 5)` |
| `short` | `Short` | `Short s = (short) 5;` | `Short.valueOf((short) 5)` |
| `char` | `Character` | `Character c = 'A';` | `Character.valueOf('A')` |
| `int` | `Integer` | `Integer n = 5;` | `Integer.valueOf(5)` |
| `long` | `Long` | `Long n = 5L;` | `Long.valueOf(5L)` |
| `float` | `Float` | `Float f = 1.5f;` | `Float.valueOf(1.5f)` |
| `double` | `Double` | `Double d = 1.5;` | `Double.valueOf(1.5)` |

Kiểu `void` có lớp `Void` liên quan trong API, nhưng **không có một giá trị `void` để autobox** như tám primitive ở bảng trên.

**Wrapper là đối tượng và có thể là `null`**, trong khi biến primitive không thể chứa `null`. Wrapper được thiết kế **bất biến** (*immutable*): một đối tượng `Integer` chứa `5` không bị thay giá trị thành `6`; phép tăng trên biến wrapper sẽ unbox, tính toán và box lại một giá trị.

## 3. Ngữ cảnh gán: biến, trường dữ liệu và phần tử mảng

### 3.1. Gán primitive cho wrapper

```java
int quantity = 7;
Integer quantityObject = quantity;

double price = 19.5;
Double priceObject = price;

boolean enabled = true;
Boolean enabledObject = enabled;
```

Trong cả ba trường hợp, phép gán có kiểu đích là wrapper tương ứng nên Java tự boxing. Cơ chế tương tự áp dụng cho việc khởi tạo trường dữ liệu hoặc gán lại một biến wrapper.

Ví dụ khai báo trường trong lớp:

```java
class Product {
    Integer stock = 10;   // Literal int được autobox.
    Boolean active = true; // Literal boolean được autobox.
}
```

Đây là một lớp minh họa độc lập về cú pháp; không yêu cầu phải có `main` mới chứng minh được boxing hợp lệ.

### 3.2. Boxing rồi mở rộng kiểu tham chiếu

```java
Number number = 123; // int -> Integer -> Number.
Object object = 2.5; // double -> Double -> Object.

System.out.println(number.getClass().getSimpleName()); // Integer
System.out.println(object.getClass().getSimpleName()); // Double
```

Đây là *boxing conversion* kết hợp với *widening reference conversion*. Kiểu tĩnh của biến là `Number` hoặc `Object`, nhưng đối tượng được tạo/nhận sau boxing vẫn thuộc lớp wrapper cụ thể.

Vì vậy không nên suy từ `Number n = 123` rằng Java tạo ra một “đối tượng Number” trực tiếp. `Number` là lớp trừu tượng; giá trị `123` ở ví dụ được đóng gói dưới dạng `Integer`.

### 3.3. Khởi tạo mảng wrapper

```java
Integer[] scores = {10, 20, 30}; // Mỗi phần tử được boxing.
Double[] rates = {0.5, 1.5};    // double -> Double.
```

**Không có phép biến đổi tự động cho toàn bộ mảng primitive thành mảng wrapper**:

```java
int[] raw = {1, 2, 3};
// Integer[] boxedArray = raw; // Lỗi biên dịch.
```

Nếu phải chuyển toàn bộ mảng, cần thực hiện trên **từng phần tử** hoặc dùng API thích hợp, ví dụ `IntStream.of(raw).boxed().toArray(Integer[]::new)`. Autoboxing không phải cơ chế “thay kiểu cả container”.

## 4. Ngữ cảnh gọi phương thức: truyền đối số primitive

Phương thức khai báo tham số là wrapper có thể được gọi bằng đối số primitive cùng kiểu:

```java
static void acceptInteger(Integer value) {
    System.out.println(value);
}

static void acceptDouble(Double value) {
    System.out.println(value);
}

// Khi gọi trong mã khác của cùng lớp:
// acceptInteger(8);    // int -> Integer.
// acceptDouble(2.5);   // double -> Double.
```

Trong ngữ cảnh gọi phương thức, trình biên dịch xác định phương thức áp dụng được theo các giai đoạn của **overload resolution**. Boxing được phép trong các giai đoạn phù hợp; tuy nhiên không phải mọi chuỗi chuyển đổi đều được cho phép và không phải overload nhận wrapper luôn được ưu tiên.

### Overload: mở rộng primitive được xét trước boxing

```java
static void pick(long value) {
    System.out.println("long");
}

static void pick(Integer value) {
    System.out.println("Integer");
}

// pick(1); // In "long": int -> long được xét ở giai đoạn strict invocation.
```

Với literal `1` kiểu `int`, `pick(long)` áp dụng được bằng *widening primitive conversion* mà không cần boxing; `pick(Integer)` cần boxing. Java giải quyết phương thức trong pha cho phép chuyển đổi không boxing trước, vì vậy ví dụ trên chọn `pick(long)`.

Điểm thực hành: **đừng thiết kế API chỉ dựa vào cảm giác rằng Java sẽ ưu tiên wrapper “gần hơn”**. Khi các overload trộn primitive, wrapper, varargs và kiểu tham chiếu, hãy đọc chữ ký phương thức và hiểu thứ tự lựa chọn; nếu khó đọc, ép kiểu hoặc truyền giá trị rõ ràng.

### Varargs của wrapper cũng có thể nhận các giá trị primitive

```java
static void acceptMany(Integer... values) {
    System.out.println(values.length);
}

// acceptMany(1, 2, 3); // In 3: từng int được boxing thành Integer.
```

Varargs là cơ chế tạo/nhận một mảng tham số, **không phải boxing toàn bộ một mảng `int[]`**. Lời gọi `acceptMany(rawIntArray)` với `int[]` sẽ không trở thành `Integer[]` hợp lệ chỉ bằng autoboxing.

## 5. Giá trị trả về, lambda và generic

### Return statement

Một phương thức khai báo trả về wrapper có thể trả về primitive khi phép chuyển đổi hợp lệ:

```java
static Integer boxedResult(int input) {
    return input; // int -> Integer trong ngữ cảnh return.
}
```

Ngược lại, phương thức trả về `int` có thể `return` một `Integer` không null nhờ unboxing, nhưng sẽ ném `NullPointerException` nếu giá trị wrapper trả về là `null`.

### Lambda trả về wrapper

```java
import java.util.function.Supplier;

Supplier<Integer> supplier = () -> 42;
System.out.println(supplier.get()); // 42
```

Đích của lambda là một functional interface có kết quả `Integer`. Biểu thức `42` được boxing để đáp ứng kiểu trả về. Điều này hữu ích khi code hiện đại sử dụng `Supplier`, `Function` hoặc các functional interface generic khác.

### Generics chỉ nhận kiểu tham chiếu

```java
import java.util.ArrayList;
import java.util.List;

List<Integer> values = new ArrayList<>();
values.add(10); // int -> Integer.
values.add(20); // int -> Integer.

// List<int> invalid = new ArrayList<>(); // Lỗi biên dịch.
```

Một tham số kiểu generic như `T` của `List<T>` phải là kiểu tham chiếu, không thể là primitive. Các lớp wrapper cho phép dùng dữ liệu số trong generics.

Cũng cần phân biệt `List<Integer>` với `List<Number>`: chúng **không có quan hệ kế thừa trực tiếp** theo quy tắc generic invariance, mặc dù `Integer` kế thừa `Number`. Autoboxing không biến `List<Integer>` thành `List<Number>`.

## 6. Những chuyển đổi Java không tự thực hiện

Autoboxing **không** có nghĩa mọi primitive có thể gán cho mọi wrapper số. Ngữ cảnh và chuỗi chuyển đổi đều quan trọng.

```java
Integer a = 10; // Đúng: int -> Integer.
Long b = 10L;   // Đúng: long -> Long.
Double c = 1.0; // Đúng: double -> Double.

// Long invalid = 10;   // Sai: không tự nối int -> long -> Long.
// Double bad = 1;      // Sai: không tự nối int -> double -> Double.
```

Cách làm rõ nghĩa:

```java
long widened = 10; // int -> long.
Long wrapped = widened; // long -> Long.
// Hoặc:
Long also = 10L;
```

Tương tự, Java không tự đổi wrapper thành wrapper khác bằng phép gán:

```java
Integer input = 10;
// Long invalid = input; // Không biên dịch.
Long converted = input.longValue(); // long -> Long.
```

Cần lưu ý một ngoại lệ ở **ngữ cảnh gán hằng số**: Java cho phép thu hẹp một biểu thức hằng `int` nằm trong miền `byte`, `short` hoặc `char`, rồi boxing vào wrapper tương ứng. Ví dụ:

```java
Byte small = 100; // Hợp lệ: hằng int 100 nằm trong miền byte.
Short shortValue = 123; // Hợp lệ.
// Byte outside = 200; // Lỗi: 200 vượt miền byte.
```

Đây là quy tắc đặc biệt về **constant expression và assignment conversion**, không phải bằng chứng rằng Java tự cho phép mọi chuyển đổi số học rồi boxing. Với biến `int` thông thường, `Byte boxed = someInt;` không hợp lệ nếu không có bước chuyển kiểu rõ ràng.

## 7. Autoboxing và unboxing trong biểu thức số học

Khi wrapper xuất hiện trong phép toán số học, Java thường phải **unbox** để lấy primitive, rồi áp dụng quy tắc nâng kiểu số học của biểu thức.

```java
Integer a = 7;
Integer b = 5;

int sum = a + b; // Unbox a,b -> int; cộng int cho 12.
Integer boxedSum = a + b; // Tính phép cộng int, rồi boxing 12.
```

Câu `Integer boxedSum = a + b` **không có nghĩa hai đối tượng `Integer` cộng trực tiếp với nhau**. Giá trị cần được lấy ra khỏi wrapper, thực hiện phép cộng primitive rồi mới boxing kết quả để gán.

### Gán ghép và tăng giảm wrapper

```java
Integer count = 7;
Integer original = count;

count += 5; // Unbox -> phép cộng int -> boxing về Integer.
count++;    // Unbox -> tăng 1 -> boxing lại.

System.out.println(original); // 7
System.out.println(count);    // 13
```

Đối tượng `original` vẫn chứa `7` vì wrapper **bất biến**. Biến `count` sau các phép toán giữ tham chiếu tới đối tượng boxing kết quả mới (hoặc đối tượng tái sử dụng từ cache), chứ không sửa giá trị của đối tượng cũ.

Những phép toán này vẫn chịu giới hạn miền nguyên thủy:

```java
Integer max = Integer.MAX_VALUE;
max++; // Phép cộng int vượt giới hạn và wraparound.
System.out.println(max); // -2147483648
```

Boxing không tự thêm kiểm tra tràn số. Nếu tràn không được phép, dùng `Math.addExact` hoặc kiểm tra biên phù hợp trước khi boxing kết quả.

## 8. Bẫy quan trọng: wrapper có thể `null`

Primitive không thể là `null`, wrapper có thể:

```java
Integer optionalCount = null;

// int count = optionalCount; // Khi thực thi sẽ ném NullPointerException.
```

Không có giá trị `int` tương ứng với `null`. Bất cứ khi nào Java cần **unbox một tham chiếu null**, chương trình ném `NullPointerException`.

Các tình huống thường gặp:

```java
Integer a = null;

// int x = a;         // NPE khi unbox.
// int y = a + 1;     // NPE trước phép cộng.
// a++;               // NPE khi unbox.
// boolean eq = a == 0; // NPE khi so sánh với primitive 0.
```

Trong khi đó, **boxing một primitive hợp lệ không tự tạo null**; rủi ro ở đây phát sinh từ nơi wrapper có thể chứa null.

Một cách xử lý rõ ràng:

```java
Integer count = null;
int safe = count != null ? count : 0;
System.out.println(safe); // 0
```

Quy tắc `null -> 0` phải là **quyết định nghiệp vụ**, không phải mặc định của autounboxing. Nếu null nghĩa là “chưa biết”, “chưa nhập” hoặc “không áp dụng”, thay bằng 0 có thể làm sai dữ liệu. Khi cần giữ trạng thái thiếu dữ liệu, dùng wrapper/Optional hoặc thông báo lỗi rõ ràng thay vì ép về primitive.

Nếu wrapper được truyền qua `List<Integer>` hoặc `Map<String, Integer>`, dữ liệu lấy ra vẫn có thể `null`. Autoboxing ở bước thêm phần tử không bảo đảm mọi lần đọc sau đều an toàn để unbox.

## 9. Wrapper và dấu `==`: không dùng danh tính làm phép so sánh số

Khi **cả hai toán hạng là tham chiếu wrapper**:

```java
Integer first = Integer.valueOf(1_000);
Integer second = Integer.valueOf(1_000);

System.out.println(first.equals(second)); // true
```

Phương thức `equals` của wrapper so sánh **giá trị theo hợp đồng của lớp**, còn `first == second` so sánh **có cùng đối tượng hay không**. Đừng lấy `==` giữa hai wrapper làm tiêu chí bằng nhau của số.

Có cơ chế **cache/tái sử dụng** đối tượng khi boxing một số giá trị nhỏ. Ví dụ, đối với `Integer`, giá trị `100` thuộc miền được bảo đảm tái sử dụng trong boxing:

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b); // true theo bảo đảm boxing cho giá trị này.
```

Nhưng với giá trị lớn hơn, việc tái sử dụng đối tượng phụ thuộc quy tắc và triển khai, nên không được viết:

```java
Integer x = 1_000;
Integer y = 1_000;

// boolean equalNumbers = (x == y); // Không dùng: so sánh danh tính.
boolean equalNumbers = x.equals(y); // true.
```

**Đừng giả định mọi giá trị ngoài cache luôn trả false khi dùng `==`**; cách triển khai có thể tái sử dụng một số đối tượng. Dùng `equals` khi muốn so sánh wrapper cùng kiểu, hoặc unbox có kiểm tra null nếu muốn so sánh primitive.

Khi một vế là primitive:

```java
Integer boxed = 5;
int primitive = 5;
System.out.println(boxed == primitive); // true, boxed được unbox.
```

Lúc này `==` là phép so sánh giá trị primitive, không phải so sánh danh tính tham chiếu. Sự khác nhau này giải thích vì sao đổi kiểu khai báo biến có thể vô tình thay đổi ý nghĩa của cùng ký hiệu `==`.

Lưu ý với `Float`/`Double`, các hợp đồng `equals` còn khác `==` primitive ở các giá trị `NaN` và `+0.0`/`-0.0`. Khi dùng wrapper làm khóa `HashMap`/`HashSet`, các collection dựa trên `equals` và `hashCode`, không phải phép so sánh `==` của primitive.

## 10. Ảnh hưởng tới hiệu năng và bộ nhớ

Kiểu wrapper là **đối tượng**, vì vậy có thể đòi hỏi thêm bộ nhớ, việc cấp phát/quản lý đối tượng và chuyển đổi so với làm việc trực tiếp bằng primitive. Trình biên dịch/JVM có thể tối ưu nhiều trường hợp, nhưng không nên giả định việc autoboxing là “miễn phí” trong tất cả vòng lặp nóng hoặc tập dữ liệu lớn.

Ví dụ:

```java
long total = 0L;
for (int i = 0; i < 1_000_000; i++) {
    total += i; // Toàn bộ đường tính toán dùng primitive.
}
```

So với:

```java
Long total = 0L;
for (int i = 0; i < 1_000_000; i++) {
    total += i; // Unboxing và boxing lại tổng mỗi lần lặp.
}
```

Đây là ví dụ để nhận biết **các chuyển đổi được yêu cầu về mặt ngữ nghĩa**, không phải benchmark khẳng định tốc độ chính xác. Hiệu năng thực tế phụ thuộc JVM, tối ưu hóa JIT, escape analysis, kích thước dữ liệu và cách đo.

Khi cần xử lý rất nhiều số nguyên, cân nhắc:

- Dùng `int[]`/`long[]`/`double[]` thay vì mảng wrapper nếu không cần `null` hay API đối tượng.
- Dùng `IntStream`, `LongStream`, `DoubleStream` cho pipeline dữ liệu primitive; gọi `boxed()` chỉ khi cần chuyển sang `Stream<Integer>`, `Stream<Long>` hoặc `Stream<Double>`.
- Dùng `List<Integer>` hoặc wrapper khi API generic, dữ liệu tùy chọn/nullable và cấu trúc tập hợp đòi hỏi đối tượng.

**Không tối ưu vội:** ưu tiên đúng nghĩa và rõ ràng, sau đó dùng benchmark phù hợp (ví dụ JMH) để kiểm chứng chi phí trong phần chương trình thực sự quan trọng.

## 11. Không nhầm autoboxing với mọi chuyển đổi ngầm

Có một số tình huống trông như “biến số thành đối tượng” hoặc “in một số ra màn hình” nhưng **không bắt buộc autoboxing xảy ra**:

```java
System.out.println(10);          // Có overload println(int).
String message = "count=" + 10; // Nối chuỗi có quy tắc string conversion.
```

Không thể lấy hai dòng này làm bằng chứng rằng mọi primitive luôn được tạo thành wrapper. Autoboxing là chuyển đổi được áp dụng trong các ngữ cảnh định kiểu nhất định; nối chuỗi và lựa chọn overload có thể hoạt động mà không cần tạo wrapper.

Tương tự, cast kiểu tham chiếu thông thường và autoboxing là hai việc khác nhau. Việc chuyển `Number number = 10;` tạo ra `Integer` rồi mở rộng kiểu tham chiếu không có nghĩa `Number` là lớp bao trực tiếp cho `int`. Và việc gọi `Integer.valueOf("10")` là **phân tích chuỗi rồi tạo Integer**, không phải autoboxing của một giá trị `String`.

## 12. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp **`AutoboxingContextsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class AutoboxingContextsDemo {
    static void receive(Integer value) {
        System.out.println("method=" + value);
    }

    static void choose(long value) {
        System.out.println("overload=long");
    }

    static void choose(Integer value) {
        System.out.println("overload=Integer");
    }

    static Integer boxReturn(int input) {
        return input;
    }

    public static void main(String[] args) {
        int primitive = 7;
        Integer boxed = primitive;
        int unboxed = boxed;

        System.out.println("assignment=" + boxed);
        System.out.println("unboxed=" + unboxed);

        receive(8); // Autoboxing tham số.
        choose(1);  // Widening primitive ưu tiên hơn boxing.

        Object object = 2.5;
        Number number = 6;
        System.out.println("Object holds="
                + object.getClass().getSimpleName());
        System.out.println("Number holds="
                + number.getClass().getSimpleName());

        Integer[] array = {2, 3};
        System.out.println("array second=" + array[1]);

        List<Integer> values = new ArrayList<>();
        values.add(4);
        values.add(5);
        int sum = 0;
        for (int value : values) {
            sum += value; // Unbox mỗi Integer.
        }
        System.out.println("list=" + values);
        System.out.println("list sum=" + sum);

        System.out.println("return boxed=" + boxReturn(12));

        Supplier<Integer> supplier = () -> 13;
        System.out.println("lambda boxed=" + supplier.get());

        Integer smallA = 100;
        Integer smallB = 100;
        System.out.println("small cached identity=" + (smallA == smallB));

        Integer largeA = 1_000;
        Integer largeB = 1_000;
        System.out.println("large equals=" + largeA.equals(largeB));

        Integer current = 7;
        Integer previous = current;
        current += 5;
        System.out.println("updated=" + current);
        System.out.println("previous=" + previous);

        Integer missing = null;
        try {
            int willFail = missing;
            System.out.println(willFail);
        } catch (NullPointerException ex) {
            System.out.println("null unboxing=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac AutoboxingContextsDemo.java
java AutoboxingContextsDemo
```

Kết quả:

```text
assignment=7
unboxed=7
method=8
overload=long
Object holds=Double
Number holds=Integer
array second=3
list=[4, 5]
list sum=9
return boxed=12
lambda boxed=13
small cached identity=true
large equals=true
updated=12
previous=7
null unboxing=NullPointerException
```

Ví dụ thể hiện năm nhóm chuyển đổi: **gán**, **đối số phương thức**, **mảng/generic collection**, **giá trị trả về/lambda**, và **unboxing trong phép toán**. Nó không dựa vào kết quả so sánh danh tính với giá trị lớn ngoài vùng cache; chỉ sử dụng `equals` cho trường hợp đó.

## 13. Các lỗi phổ biến và cách tránh

| Nhầm lẫn | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| “Autoboxing là đổi mọi primitive sang mọi wrapper.” | Boxing ánh xạ tới wrapper tương ứng và bị giới hạn bởi ngữ cảnh chuyển đổi. | Chọn literal đúng kiểu, dùng cast hoặc chuyển đổi tường minh khi cần. |
| “`Long x = 5` chắc chắn hợp lệ vì `int` mở rộng sang `long`.” | Phép gán không tự nối `int → long → Long`. | `Long x = 5L` hoặc chuyển qua biến `long`. |
| “`List<int>` có thể dùng giống `List<Integer>`.” | Tham số kiểu generics phải là kiểu tham chiếu. | Sử dụng `List<Integer>` hoặc cấu trúc primitive. |
| “`int[]` sẽ tự boxing thành `Integer[]`.” | Boxing không áp dụng trực tiếp cho cả mảng. | Chuyển từng phần tử. |
| “Wrapper luôn không null vì autoboxing.” | Biến/collection tham chiếu có thể giữ `null`. | Kiểm tra null trước khi unbox. |
| “`Integer a == Integer b` luôn so sánh giá trị số.” | Nếu cả hai là tham chiếu, `==` so sánh danh tính. | Dùng `equals` hoặc unbox an toàn. |
| “Ngoài miền cache, `==` chắc chắn false.” | Cách tái sử dụng wrapper ngoài miền bảo đảm không nên được giả định. | Không phụ thuộc identity của boxed number. |
| “`Integer count++;` sửa đối tượng `Integer` tại chỗ.” | Wrapper bất biến; thao tác unbox, tính, rồi box và gán lại. | Hiểu rõ kiểu primitive bên trong phép toán. |
| “Autoboxing ngăn phép toán nguyên tràn số.” | Phép tính vẫn dùng primitive và tuân thủ giới hạn của nó. | Kiểm tra biên/`Math.*Exact` nếu hợp đồng cấm tràn. |
| “Mọi chỗ in số/nghiệm chuỗi đều cấp phát wrapper.” | Overload primitive và string conversion không đồng nghĩa boxing. | Xem đúng API và kiểu biểu thức. |
| “Dùng wrapper trong vòng lặp không thể tốn chi phí.” | Chuyển đổi và quản lý đối tượng có thể phát sinh overhead. | Dùng primitive khi phù hợp, đo hiệu năng trước khi tối ưu. |

## 14. Checklist khi áp dụng autoboxing trong mã nguồn

1. **Kiểu nguồn/đích:** primitive nào được chuyển tới wrapper nào? Có cần widening primitive hay narrowing trước khi boxing không?
2. **Ngữ cảnh:** gán, return, gọi hàm, lambda hay generic collection có cho phép chuyển đổi đó không?
3. **Khả năng null:** wrapper này đến từ đâu? Có thể là `null` khi unboxing không?
4. **So sánh:** bạn cần so sánh giá trị `equals` hay danh tính đối tượng `==`? Nếu là `Float`/`Double`, các trường hợp NaN/signed zero có quan trọng không?
5. **Overloading:** nếu cùng tên hàm có tham số primitive và wrapper, overload nào được chọn theo pha resolution?
6. **Độ chính xác và giới hạn:** boxing chỉ đóng gói giá trị; không sửa tràn số, không phục hồi sai số dấu phẩy động.
7. **Chi phí:** cấu trúc generic có thật sự cần wrapper không, hay kiểu primitive/mảng/stream primitive đủ dùng?
8. **API/độ rõ:** khi nghi ngờ, dùng `Integer.valueOf`, `longValue()`, chuyển kiểu rõ ràng hoặc tách câu lệnh để làm sáng tỏ chuyển đổi.

**Ghi nhớ:** Autoboxing là chuyển đổi do Java tự cung cấp từ **primitive sang wrapper tương ứng trong những ngữ cảnh định kiểu cho phép**. Nó thuận tiện cho API đối tượng và generics, nhưng không tạo ra kiểu số mới, không xử lý tràn, không tự bảo đảm wrapper khác `null`, không thay đổi quy tắc so sánh danh tính và có thể mang chi phí hiệu năng. Hiểu chính xác thời điểm boxing/unboxing giúp tránh lỗi biên dịch, `NullPointerException` và các bug khó nhận ra khi xử lý số.

**Tài liệu tham khảo:** [JLS §5.1.7 — Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.7), [JLS §5.1.8 — Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.8), [JLS §5.2 — Assignment Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.2), [JLS §5.3 — Invocation Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.3), [JLS §15.12.2 — Compile-Time Step 2: Determine Method Signature](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.2), [Integer API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-002">Unboxing trong gán, phép toán và lời gọi phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Unboxing trong gán, phép toán và lời gọi phương thức trong Java

**Unboxing** là phép chuyển một giá trị từ **wrapper object** sang **kiểu nguyên thủy (primitive)** tương ứng. Ví dụ, Java có thể chuyển `Integer` thành `int`, `Long` thành `long` và `Double` thành `double` một cách ngầm định khi ngữ cảnh yêu cầu giá trị primitive.

```java
Integer boxed = 12; // Integer object
int number = boxed; // unboxing: Integer -> int

Integer a = 7, b = 5;
int sum = a + b;    // unboxing cả a và b, sau đó cộng int

static int twice(int x) {
    return x * 2;
}
// twice(boxed) gọi được: Integer -> int trước khi truyền đối số.
```

*Chú ý:* `twice` ở trên là **phương thức minh họa cần đặt trong class**. Bài sẽ có chương trình hoàn chỉnh chạy được ở phần sau.

Unboxing không phải phép biến đổi vô hại với mọi đầu vào: **unbox một wrapper có giá trị `null` sẽ ném `NullPointerException` khi thực hiện**. Và unboxing không làm thay đổi các quy tắc như nâng kiểu số học, overflow, phép chia nguyên hoặc chọn phương thức overload.

## 1. Primitive khác wrapper như thế nào?

Java có các cặp primitive và wrapper:

| Primitive | Wrapper | Phương thức lấy primitive điển hình |
| --- | --- | --- |
| `boolean` | `Boolean` | `booleanValue()` |
| `byte` | `Byte` | `byteValue()` |
| `short` | `Short` | `shortValue()` |
| `char` | `Character` | `charValue()` |
| `int` | `Integer` | `intValue()` |
| `long` | `Long` | `longValue()` |
| `float` | `Float` | `floatValue()` |
| `double` | `Double` | `doubleValue()` |

- **Primitive** chứa giá trị thuộc kiểu đó, ví dụ `int` có giá trị số nguyên 32 bit.
- **Wrapper** là kiểu tham chiếu, ví dụ `Integer` là đối tượng chứa giá trị `int`. Một biến tham chiếu wrapper có thể là `null`.
- **Boxing** là chiều ngược lại: `int → Integer`, thường xảy ra khi gán primitive vào biến wrapper hoặc đưa vào collection.
- **Unboxing** là chiều `Integer → int`, được chèn tự động khi Java cần một primitive.

```java
int primitive = 10;
Integer boxed = primitive; // boxing

int again = boxed;         // unboxing
```

Không nên đồng nhất **đối tượng** wrapper và **giá trị primitive**. Unboxing đọc giá trị primitive từ wrapper; nếu wrapper không tham chiếu đến một đối tượng nào (`null`), không có giá trị để đọc.

## 2. Unboxing trong phép gán (assignment)

Một kiểu dùng phổ biến là **gán wrapper cho biến primitive tương ứng**:

```java
Integer boxedInt = 42;
int primitiveInt = boxedInt;  // Integer -> int

Double boxedDouble = 3.5;
double primitiveDouble = boxedDouble; // Double -> double

Boolean enabled = Boolean.TRUE;
boolean flag = enabled; // Boolean -> boolean
```

Đây là các **assignment contexts** nơi Java có thể thực hiện unboxing conversion. Kết quả sau unboxing phải tuân theo các quy tắc kiểu của biến đích.

### Unboxing rồi widening primitive

Java còn cho phép **unboxing rồi mở rộng primitive**, ví dụ `Integer → int → long`:

```java
Integer boxed = 12;
long wider = boxed;    // Integer -> int -> long, giá trị 12L

Byte small = 10;
int number = small;    // Byte -> byte -> int
double real = boxed;   // Integer -> int -> double
```

Tuy nhiên, không suy luận rằng Java tự thực hiện mọi chuỗi chuyển kiểu:

```java
Integer source = 12;
// Long invalid = source; // Lỗi biên dịch: không có Integer -> int -> long -> Long tự động

long widened = source;  // Hợp lệ: unboxing rồi widening primitive
Long target = (long) source; // Hợp lệ: cast sang long, sau đó boxing thành Long
```

Chuyển `Integer` sang `Long` không phải một phép unboxing trực tiếp. Nếu cần `Long`, hãy chuyển sang `long` một cách rõ ràng rồi boxing.

### Unboxing `null` khi gán

```java
Integer count = null;
// int value = count; // Biên dịch được nhưng NPE khi thực hiện
```

Trình biên dịch không từ chối chỉ vì một wrapper **có thể** null. Nhưng khi chạy tới phép gán primitive, quá trình lấy `intValue()` trên tham chiếu null dẫn đến `NullPointerException`.

Với giá trị có thể thiếu, nên kiểm tra và xử lý rõ theo hợp đồng dữ liệu:

```java
Integer count = null;
int safe = count == null ? 0 : count;  // Chỉ dùng 0 nếu nghiệp vụ quy định
```

Biểu thức điều kiện này không đánh giá nhánh `count` khi điều kiện `count == null` đúng, nên tránh unboxing ở trường hợp null. **Không tự thay null bằng 0** nếu 0 cũng là dữ liệu có ý nghĩa khác; có thể chọn từ chối, báo lỗi hoặc giữ kiểu wrapper đến khi cần.

## 3. Unboxing trong toán tử số học

Các toán tử số học `+`, `-`, `*`, `/`, `%` hoạt động trên các giá trị primitive số học. Khi toán hạng là wrapper kiểu số, Java có thể **unbox rồi áp dụng numeric promotion**:

```java
Integer a = 7;
Integer b = 5;

int sum = a + b;          // 12
int difference = a - b;   // 2
int product = a * b;      // 35
int quotient = a / b;     // 1, phép chia nguyên
int remainder = a % b;    // 2
```

`a` và `b` vẫn là các biến `Integer`; chỉ giá trị của chúng được chuyển thành `int` trong **biểu thức mới**. Kết quả `a + b` có kiểu `int`, **không phải `Integer` chỉ vì toán hạng được khai báo là `Integer`**.

### Wrapper nhỏ hơn `int` vẫn chịu numeric promotion

```java
Byte first = 10, second = 20;
int sum = first + second; // Byte -> byte rồi nâng lên int
```

Các toán hạng `byte` và `short` thường được nâng ít nhất thành `int` trong phép toán nhị nguyên. Nếu một toán hạng là `Long` và bên kia là `Integer`, sau unboxing phép toán sẽ dùng `long`:

```java
Integer i = 2;
Long l = 3L;
long total = i + l; // int + long -> long
```

Không có nghĩa wrapper tự được mở rộng về object wrapper khác; việc nâng kiểu xảy ra trong **biểu thức primitive**.

### Unboxing không bảo vệ khỏi overflow hoặc mất phần lẻ

```java
Integer a = 50_000;
Integer b = 50_000;

int wrong = a * b;          // -1794967296: nhân int đã tràn
long alsoWrong = a * b;     // Mở rộng kết quả int đã tràn
long correct = (long) a * b; // 2500000000: unbox a, cast long trước phép nhân

Integer seven = 7;
Integer three = 3;
int q = seven / three;       // 2, chia nguyên cắt phần phân số
double real = (double) seven / three; // Khoảng 2.3333333333333335
```

Kết quả phụ thuộc **kiểu primitive sau unboxing và chuyển kiểu**, không phụ thuộc vào việc ta viết wrapper hay primitive ở khai báo ban đầu. Nếu cần phát hiện overflow thay vì nhận giá trị tràn `int`/`long`, cân nhắc `Math.multiplyExact` hoặc `Math.addExact` với overload và kiểu đối số phù hợp.

## 4. Toán tử tăng giảm, gán rút gọn và boxing trở lại

Các lệnh như `++`, `--`, `+=` có thể đưa wrapper qua chu trình **unbox → tính toán → boxing lại**:

```java
Integer count = 5;
count++;     // lấy int 5, cộng 1, tạo giá trị Integer 6 để gán lại
count += 2;  // lấy int 6, cộng 2, boxing trở lại thành Integer 8
```

Vì wrapper là reference type, câu lệnh không làm thay đổi trực tiếp nội dung của một đối tượng `Integer` bất biến (*immutable*). Nó **gán lại tham chiếu biến** tới giá trị wrapper sau boxing.

```java
Integer counter = Integer.MAX_VALUE;
counter++; // Integer.MIN_VALUE do phép cộng int bị tràn
```

Unboxing rồi boxing lại **không tạo kiểm tra tràn số**. Nếu `counter` là null, `counter++` và `counter += 1` đều cần unbox để tính và gây `NullPointerException`.

Ngoài ra, `count += 0.5` không làm `Integer` biến thành `Double`: compound assignment có các chuyển kiểu tương ứng về loại primitive của `count` trước khi boxing lại. Đừng dùng `+=` để “tự nâng kiểu” một wrapper.

## 5. Unboxing khi so sánh: `==` cần xét kiểu hai bên

Trường hợp này thường gây lỗi nhiều hơn các phép cộng vì `==` có thể là **so sánh tham chiếu** hoặc **so sánh giá trị primitive** tùy kiểu toán hạng:

```java
Integer left = Integer.valueOf(1000);
Integer right = Integer.valueOf(1000);

boolean references = (left == right); // So sánh tham chiếu; đừng dùng để so giá trị
boolean values = left.equals(right);  // So sánh giá trị wrapper: true

int primitive = 1000;
boolean mixed = (left == primitive);   // Unbox left, so sánh int: true
```

**Hai tham chiếu `Integer` trong `left == right` không tự unbox** chỉ vì cùng là wrapper. Khi một bên là primitive số và bên kia là wrapper số, Java áp dụng các quy tắc số học và có thể unbox wrapper để so sánh.

Cũng vì vậy:

```java
Integer missing = null;
boolean absent = (missing == null); // true: kiểm tra tham chiếu, an toàn
// boolean isZero = (missing == 0);  // Biên dịch, nhưng NPE khi so sánh
```

Các toán tử quan hệ `<`, `<=`, `>`, `>=` trên wrapper số cần so sánh giá trị số học, nên sẽ thực hiện unboxing:

```java
Integer low = 3, high = 7;
boolean ordered = low < high; // true, unbox cả hai
```

Với `null`, quan hệ thứ tự này gây NPE tại runtime. Viết `missing != null && missing > 0` để **short-circuit** bảo vệ phép unboxing ở vế phải. Không dùng `missing > 0 && missing != null` vì vế trái đã unbox trước.

## 6. Unboxing khi gọi phương thức (method invocation)

Java có thể chuyển wrapper thành primitive để phù hợp kiểu tham số:

```java
static int twice(int value) {
    return value * 2;
}

// Trong phương thức khác:
Integer number = 12;
int answer = twice(number); // Integer -> int; kết quả 24
```

Phương thức `twice` yêu cầu `int`, do đó Java unbox `number` khi đánh giá đối số. Nếu `number == null`, lời gọi không thể cung cấp giá trị `int`, và quá trình chuẩn bị đối số ném NPE **trước khi thân phương thức `twice` được chạy**.

Unboxing có thể kết hợp với **widening primitive** cho tham số rộng hơn:

```java
static void acceptLong(long value) {
    System.out.println(value);
}

// Integer boxed = 12;
// acceptLong(boxed); // Integer -> int -> long
```

Đây là mảnh mã minh họa, không phải chương trình độc lập. Sự tương thích kiểu được kiểm tra lúc biên dịch, còn việc đọc giá trị wrapper có thể gây NPE ở runtime.

### Overload resolution: không phải cứ có overload primitive là Java unbox

Nếu nhiều phương thức cùng tên, Java chọn overload theo **các giai đoạn áp dụng được của method invocation**: trước hết các lời gọi không cần boxing/unboxing (strict invocation), sau đó mới xét các chuyển đổi rộng hơn, rồi varargs.

Ví dụ:

```java
static String choose(int value)  { return "int"; }
static String choose(long value) { return "long"; }

Integer boxed = 12;
// choose(boxed) -> "int": cần unbox; overload int cụ thể hơn long
```

Nhưng với các overload:

```java
static String route(long value)     { return "long"; }
static String route(Integer value)  { return "Integer"; }

Integer boxed = 12;
// route(boxed) -> "Integer": truyền tham chiếu không cần unbox
// route(7)     -> "long": widening int -> long được xét trước boxing int -> Integer
```

Và có một tình huống tinh tế:

```java
static String view(Object value) { return "Object"; }
static String view(int value)    { return "int"; }

Integer boxed = 12;
// view(boxed) -> "Object": Integer -> Object là widening reference
//                nên được chọn ở giai đoạn strict trước khi xét unboxing int
```

**Vì vậy không thể khẳng định “overload nhận primitive luôn được ưu tiên” hoặc “Java luôn unbox khi có thể”.** Phải xem tập overload cùng giai đoạn chuyển đổi áp dụng được. Khi đọc code khó, dùng kiểu tham số tường minh, tránh overload quá nhiều giữa primitive/wrapper nếu chúng dẫn tới ngữ nghĩa khác nhau.

### Null cũng phụ thuộc overload nào được chọn

```java
Integer missing = null;

// route(missing) có thể gọi overload route(Integer) mà không unbox.
// twice(missing) bắt buộc unbox vì tham số là int -> NPE.
```

Một tham chiếu null **không tự gây NPE khi được truyền**. NPE xuất hiện khi biểu thức hoặc tham số thực sự đòi primitive và phải unbox null.

## 7. Thứ tự đánh giá và thời điểm NPE

Java không “kiểm tra wrapper null từ xa” khi biên dịch. Nó đánh giá biểu thức và chỉ unbox khi cần giá trị primitive:

| Ngữ cảnh | Có cần unbox không? | Nếu wrapper null |
| --- | --- | --- |
| `int n = boxed;` | Có | NPE |
| `long n = boxed;` với `boxed` là `Integer` | Có, rồi widening | NPE |
| `int x = boxed + 1;` | Có | NPE |
| `boxed > 0` | Có | NPE |
| `boxed == 0` | Có | NPE |
| `boxed == null` | Không, so sánh tham chiếu | Không NPE vì phép so sánh này |
| `boxed == anotherBoxed` với hai `Integer` | Không, so sánh tham chiếu | Không NPE chỉ vì một bên null |
| Gọi `twice(boxed)` với `twice(int)` | Có | NPE khi chuẩn bị đối số |
| Gọi `route(boxed)` khi chọn `route(Integer)` | Không | Có thể truyền null bình thường |
| `boxed++` / `boxed += 1` | Có và boxing lại | NPE |

Một trường hợp nữa là biểu thức điều kiện có một nhánh primitive:

```java
Integer maybe = null;
boolean useBoxed = false;
int value = useBoxed ? maybe : 0; // 0, không đánh giá nhánh maybe
```

Nếu `useBoxed` là `true`, nhánh `maybe` phải cung cấp giá trị `int` và sẽ NPE khi null. Trình biên dịch xác định kiểu biểu thức theo cả hai nhánh, nhưng **chỉ nhánh được chọn được đánh giá tại runtime**.

Trong lời gọi nhiều đối số, các biểu thức đối số được đánh giá theo thứ tự của Java; nếu unboxing một đối số ném NPE, thân phương thức không chạy. Đây là lý do chỉ đặt `Objects.requireNonNull` bên trong một phương thức nhận `int` **không thể bảo vệ** trước NPE do unboxing ở phía caller; tham số `int` đã phải được tạo trước khi vào phương thức.

## 8. Java 17+: ví dụ hoàn chỉnh đã kiểm chứng

Lưu vào tệp `UnboxingContextsDemo.java`:

```java
public class UnboxingContextsDemo {
    static String choose(int value) { return "int"; }
    static String choose(long value) { return "long"; }

    static String route(long value) { return "long"; }
    static String route(Integer value) { return "Integer"; }

    static String view(Object value) { return "Object"; }
    static String view(int value) { return "int"; }

    static int twice(int value) { return value * 2; }

    public static void main(String[] args) {
        Integer boxed = 12;
        int assigned = boxed;
        long widened = boxed;
        System.out.println("assignment: " + assigned + ", long: " + widened);

        Integer a = 7, b = 5;
        System.out.println("sum: " + (a + b) + ", product: " + (a * b));
        System.out.println("comparison: " + (a > b));

        System.out.println("choose(boxed): " + choose(boxed));
        System.out.println("route(boxed): " + route(boxed));
        System.out.println("route(7): " + route(7));
        System.out.println("view(boxed): " + view(boxed));
        System.out.println("twice(boxed): " + twice(boxed));

        Integer counter = Integer.MAX_VALUE;
        counter++;
        System.out.println("counter after ++: " + counter);

        Integer absent = null;
        System.out.println("absent == null: " + (absent == null));
        System.out.println("route(absent): " + route(absent));

        try {
            int x = absent;
        } catch (NullPointerException ex) {
            System.out.println("assignment null: NPE");
        }
        try {
            int x = absent + 1;
        } catch (NullPointerException ex) {
            System.out.println("arithmetic null: NPE");
        }
        try {
            boolean x = absent == 0;
        } catch (NullPointerException ex) {
            System.out.println("comparison null: NPE");
        }
        try {
            twice(absent);
        } catch (NullPointerException ex) {
            System.out.println("invocation null: NPE");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac UnboxingContextsDemo.java
java UnboxingContextsDemo
```

Stdout:

```text
assignment: 12, long: 12
sum: 12, product: 35
comparison: true
choose(boxed): int
route(boxed): Integer
route(7): long
view(boxed): Object
twice(boxed): 24
counter after ++: -2147483648
absent == null: true
route(absent): Integer
assignment null: NPE
arithmetic null: NPE
comparison null: NPE
invocation null: NPE
```

**Đọc kết quả:**

- Gán `Integer` cho `int` hoặc `long` minh họa **unboxing**, và với `long` có bước mở rộng sau unboxing.
- Phép `a + b`, `a * b`, `a > b` buộc lấy các giá trị `int` để tính.
- `choose`, `route` và `view` chứng minh lựa chọn overload **không chỉ dựa vào “primitive hay wrapper”**, mà theo giai đoạn chuyển đổi phù hợp.
- `counter++` dùng `int` ở bên trong nên vẫn tràn và boxing lại.
- `absent == null` cùng `route(absent)` không yêu cầu unboxing; bốn tình huống cuối bắt buộc unboxing và gây NPE được bắt riêng.

## 9. Unboxing trong collection và generic

Generics Java dùng reference type cho type argument, nên `List<int>` không hợp lệ; thường dùng `List<Integer>`. Khi thêm một `int`, Java boxing; khi lấy ra để tính với `int`, Java unboxing:

```java
java.util.List<Integer> scores = new java.util.ArrayList<>();
scores.add(10);    // int -> Integer (boxing)
scores.add(20);

int sum = 0;
for (Integer score : scores) {
    sum += score;   // Integer -> int (unboxing)
}
```

Nếu collection chứa `null`, vòng lặp `sum += score` có thể ném NPE. Điều này không xảy ra chỉ vì `scores.add(null)` được gọi; lỗi xảy ra khi dữ liệu `null` **được dùng trong primitive context**.

Với `Map<String, Integer>`, `map.get(key)` có thể trả `null` nếu không có key (hoặc nếu key được gán null). Đoạn `int n = map.get(key);` dễ phát sinh NPE. Tùy nghiệp vụ, có thể kiểm tra `containsKey`, dùng `getOrDefault` với default phù hợp, hoặc tiếp tục giữ `Integer` để biểu diễn giá trị không có.

**Lưu ý hiệu năng:** boxing/unboxing có thể tạo hoặc truy cập wrapper và tăng chi phí trong những vòng lặp lớn hoặc cấu trúc dữ liệu nhiều phần tử. Nhưng đừng tối ưu bằng cách bỏ mọi wrapper nếu cần nullable/generic. Khi lưu số lượng lớn dữ liệu chỉ cần primitive, mảng `int[]` hoặc `double[]` là lựa chọn cần cân nhắc.

## 10. `var` và wrapper: kiểu có thể nhìn không rõ

`var` suy luận kiểu từ biểu thức khởi tạo, không dựa vào cách dùng sau đó:

```java
var wrapper = Integer.valueOf(5); // Integer
var primitive = wrapper + 1;       // int vì unboxing rồi phép cộng int
var another = wrapper;             // Integer, giữ kiểu tĩnh của expression
```

Vì thế `var` không tự né NPE: `var x = wrapper + 1` vẫn phải unbox `wrapper` khi đánh giá initializer. Nếu `wrapper` null, phép cộng ném NPE **trước khi `x` nhận được giá trị**.

Đừng nhầm phương thức trả `Integer` với phương thức trả `int` chỉ vì khi chạy chúng đều in cùng chữ số. Trong những đoạn xử lý nhiều overload/generic, có thể viết kiểu tường minh để người đọc biết **giá trị có thể null** hay không.

## 11. Cách tránh lỗi unboxing và viết API rõ nghĩa

### Xác định có cho phép thiếu dữ liệu hay không

Nếu `null` có nghĩa là “chưa biết”, “không có” hoặc “chưa nhập”, dùng wrapper ở lớp dữ liệu có thể là hợp lý. Nhưng khi chuyển sang phép toán, cần quyết định:

- **Từ chối dữ liệu thiếu:** ví dụ `Objects.requireNonNull(value, "value")`, rồi thực hiện phép toán. NPE khi ấy được kiểm soát có thông điệp rõ ràng.
- **Dùng giá trị mặc định:** chỉ khi nghiệp vụ định nghĩa default, như số lượng thiếu được tính 0.
- **Giữ trạng thái không có giá trị:** không unbox cho đến khi nhánh xử lý phù hợp hoặc dùng mô hình kiểu dữ liệu diễn đạt sự thiếu vắng.

```java
Integer maybe = null;

if (maybe != null && maybe > 0) {
    System.out.println("Positive");
}
```

Toán tử `&&` đánh giá ngắn mạch: khi `maybe != null` là false, vế phải **không thực hiện**, nên không unbox null.

### Thiết kế method signature giảm bất ngờ

Nếu một hàm luôn yêu cầu số nguyên hợp lệ và không cho phép thiếu, một tham số `int` cho thấy rõ hợp đồng ấy. Nhưng lời gọi truyền `Integer` vẫn có thể NPE do unboxing ở phía caller; nên xác thực dữ liệu trước khi gọi.

Nếu phương thức thực sự cho phép null, dùng `Integer` và xử lý null **trong phương thức**. Khi dùng overload giữa `int`, `long`, `Integer` và `Object`, nên kiểm tra resolution với các trường hợp primitive, wrapper và null; nếu ý nghĩa khác nhau quá nhiều, cân nhắc tên hàm khác nhau hoặc cast tường minh để tránh khó đọc.

### Không dùng `==` giữa hai wrapper để so nội dung số

```java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);

// a == b: so sánh identity, không nên dùng kiểm tra bằng số.
boolean equalValues = a.equals(b); // true
```

Chọn `Objects.equals(a, b)` nếu cả hai có thể null và muốn null bằng null; hoặc unbox sau khi xác minh không null để dùng `==` primitive. Với `Float`/`Double`, `equals` có ngữ nghĩa NaN và signed zero khác phép `==` primitive, nên cần xác định quy tắc mong muốn.

### Ưu tiên chuyển đổi tường minh nếu miền quan trọng

Nếu `Integer` tham gia phép nhân có nguy cơ vượt 32 bit, unbox rồi nâng một toán hạng sang `long` **trước phép nhân**, chẳng hạn `(long) a * b`; khi cần kiểm tra overflow, dùng `Math.multiplyExact` với overload `long` phù hợp. Không cho rằng boxing/unboxing có cơ chế tự phát hiện lỗi tràn.

## 12. Các nhầm lẫn thường gặp

1. **“Wrapper chỉ là primitive có thể null, Java luôn unbox ngay khi tạo.”** Sai: unboxing xảy ra khi ngữ cảnh yêu cầu giá trị primitive, không phải ngay khi tham chiếu wrapper xuất hiện.
2. **“`Integer a = null; int b = a;` không biên dịch.”** Sai: có thể biên dịch, nhưng unboxing `null` gây NPE khi chạy.
3. **“`Integer + Integer` luôn tạo `Integer` mới.”** Sai: toán tử số học unbox toán hạng, áp dụng numeric promotion; kết quả có kiểu primitive, trừ khi được boxing sau đó ở ngữ cảnh gán/return phù hợp.
4. **“`Integer * Integer` an toàn khỏi overflow.”** Sai: sau unboxing vẫn là phép nhân `int` nếu hai bên là `Integer`.
5. **“`Integer a = null; a == null` ném NPE.”** Sai: đây là so sánh tham chiếu an toàn; `a == 0` mới cần unbox.
6. **“`a == b` với hai `Integer` luôn so giá trị số.”** Sai: thường là so tham chiếu, không tự unbox hai bên.
7. **“Overload nhận primitive luôn thắng overload nhận wrapper.”** Sai: overload `route(Integer)` được chọn với đối số `Integer` phù hợp mà không cần unboxing.
8. **“Overload primitive luôn thắng `Object` nếu đối số là `Integer`.”** Sai: widening reference sang `Object` có thể được xét ở giai đoạn strict trước khi unboxing.
9. **“Một tham chiếu `null` truyền vào bất kỳ phương thức nào cũng gây NPE.”** Sai: chỉ khi chuẩn bị đối số/thân phương thức cần dùng primitive hoặc có dereference không an toàn.
10. **“`counter++` trên `Integer` thay đổi object hiện có.”** Sai: wrapper bất biến; phép toán unbox và boxing rồi gán lại biến.
11. **“`Long target = integerWrapper` hợp lệ vì `long x = integerWrapper` hợp lệ.”** Sai: `Integer → int → long` có thể đi vào primitive `long`, nhưng không tự nối thêm boxing sang `Long` trong assignment đó.
12. **“`List<Integer>` đảm bảo không có `null` nên lấy ra gán `int` luôn an toàn.”** Sai: list có thể chứa null; unboxing ở phép gán/tính sẽ ném NPE.

## 13. Tự kiểm tra

**1. `Integer x = 8; int y = x;` xảy ra gì?** → Unboxing `Integer → int` khi gán, `y` bằng 8.

**2. `Integer x = 8; long y = x;` có hợp lệ không?** → Có: unbox `int` rồi widen thành `long`.

**3. `Integer a = 5, b = 6;` kiểu của `a + b` là gì?** → `int`, không phải `Integer`.

**4. `Integer n = null;` biểu thức nào an toàn hơn để kiểm tra null: `n == null` hay `n == 0`?** → `n == null`; biểu thức còn lại cần unbox và gây NPE.

**5. Nếu có cả `route(long)` và `route(Integer)`, `route(7)` gọi phiên bản nào?** → `route(long)` vì widening primitive `int → long` được xét trước boxing `int → Integer`. Còn `route(Integer.valueOf(7))` chọn `route(Integer)`.

**6. Nếu `view(Object)` và `view(int)` cùng tồn tại, truyền `Integer` chọn overload nào?** → `view(Object)` qua widening reference ở giai đoạn strict trước khi xét unboxing.

**7. `Integer counter = Integer.MAX_VALUE; counter++;` cho gì?** → `Integer.MIN_VALUE` do phép cộng primitive `int` tràn, rồi boxing lại.

**8. Trong `int v = map.get(key);` với `Map<String,Integer>`, rủi ro nào cần kiểm tra?** → `get` có thể trả null, unboxing lúc gán `int` gây NPE.

**Ghi nhớ:** Unboxing là **đưa wrapper về primitive khi ngữ cảnh cần giá trị**, thường thấy ở **gán**, **toán tử số học/so sánh** và **đối số phương thức**. Java có thể kết hợp unboxing với widening primitive và boxing lại sau một phép toán, nhưng **không tự chuyển mọi wrapper sang wrapper khác**. Kiểu của biểu thức, overload được chọn và nguy cơ `NullPointerException` phải được xét theo **ngữ cảnh cụ thể**, không dựa trên việc hai biến in ra cùng một con số.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-003">NullPointerException khi unbox giá trị null</a>

<details>
<summary>Xem chi tiết</summary>

# NullPointerException khi unbox giá trị null trong Java

Trong Java, **unboxing** là thao tác chuyển đối tượng wrapper, chẳng hạn `Integer`, `Long`, `Double`, thành giá trị nguyên thủy tương ứng `int`, `long`, `double`. Khi đối tượng wrapper là `null`, **không có giá trị nguyên thủy nào để lấy ra**. Nếu Java thực hiện unboxing trên giá trị đó, chương trình ném **`NullPointerException` (NPE)** tại thời điểm chạy.

Ví dụ ngắn nhất:

```java
Integer quantity = null;
int count = quantity; // NullPointerException khi chạy.
```

Dòng gán nhìn như một phép chuyển kiểu tự động hợp lệ. Nó **biên dịch thành công**, nhưng khi chạy tương đương về ý nghĩa với việc gọi phương thức lấy giá trị của wrapper, chẳng hạn `quantity.intValue()`; lời gọi đó không thực hiện được trên tham chiếu `null`.

Điểm khó của bài này là **unboxing thường diễn ra ngầm**: không cần viết `intValue()`; các toán tử `+`, `++`, `==`, toán tử ba ngôi, phép truyền tham số hoặc việc lấy dữ liệu từ collection đều có thể kích hoạt nó.

Bài học thuộc phần **Boxing và unboxing** trong **Java core – numbers**. Mục tiêu là hiểu chính xác **khi nào xảy ra unboxing**, vì sao NPE xuất hiện, cách phân biệt dữ liệu thiếu với số 0 và cách thiết kế API an toàn.

## 1. Boxing, unboxing và sự khác biệt giữa primitive với wrapper

| Primitive | Wrapper tương ứng | Có thể bằng `null`? | Ví dụ |
| --- | --- | --- | --- |
| `byte` | `Byte` | Primitive: không; wrapper: có | `Byte b = null;` |
| `short` | `Short` | Primitive: không; wrapper: có | `Short s = null;` |
| `int` | `Integer` | Primitive: không; wrapper: có | `Integer n = null;` |
| `long` | `Long` | Primitive: không; wrapper: có | `Long n = null;` |
| `float` | `Float` | Primitive: không; wrapper: có | `Float f = null;` |
| `double` | `Double` | Primitive: không; wrapper: có | `Double d = null;` |
| `char` | `Character` | Primitive: không; wrapper: có | `Character c = null;` |
| `boolean` | `Boolean` | Primitive: không; wrapper: có | `Boolean b = null;` |

`Integer` không chỉ là tên khác của `int`:

- Biến `int` lưu một giá trị nguyên; nó không thể có giá trị `null`.
- Biến `Integer` là một **tham chiếu đến đối tượng**. Tham chiếu này có thể là `null`.
- **Boxing** chuyển một giá trị nguyên thủy thành wrapper, ví dụ `Integer boxed = 7;`.
- **Unboxing** lấy giá trị nguyên thủy từ wrapper, ví dụ `int value = boxed;`.
- Boxing một primitive hợp lệ không tạo ra `null`, nhưng wrapper nhận từ tham số, cơ sở dữ liệu, map hoặc DTO có thể là `null`.

```java
int count = 7;
Integer boxed = count;     // Autoboxing.
int unboxed = boxed;       // Auto-unboxing, an toàn vì boxed khác null.
```

So sánh với:

```java
Integer boxed = null;
int unboxed = boxed;       // NPE tại lúc unboxing.
```

**NPE không phải lỗi do số vượt giới hạn** và không liên quan đến `Integer.MIN_VALUE`, tràn số hay precision. Đây là lỗi **truy cập giá trị của một tham chiếu không trỏ tới đối tượng**.

### 1.1. Tại sao trình biên dịch không từ chối?

Kiểu tĩnh của `boxed` là `Integer`, vốn hợp lệ để chuyển sang `int`. Trình biên dịch không thể mặc định mọi `Integer` đều khác `null` vào lúc chạy. Ví dụ, giá trị có thể đến từ tham số, kết quả hàm hoặc một nhánh điều kiện.

```java
static int consume(Integer value) {
    return value; // Biên dịch được; chỉ ném NPE nếu value là null lúc chạy.
}
```

Java không tự chèn giá trị mặc định 0 khi unboxing `null`, vì làm vậy sẽ **đánh đồng “không có dữ liệu” với “giá trị bằng 0”**.

## 2. Những thao tác trực tiếp gây unboxing null

### 2.1. Gán cho biến nguyên thủy

```java
Integer optionalCount = null;
// int count = optionalCount; // Khi chạy: NPE.
```

Vế trái `int` đòi giá trị nguyên thủy, nên Java phải unbox vế phải.

### 2.2. Gọi method có tham số nguyên thủy

```java
static void printCount(int count) {
    System.out.println(count);
}

// Integer nullableCount = null;
// printCount(nullableCount); // NPE trước khi thân printCount chạy.
```

Lời gọi `printCount(nullableCount)` yêu cầu chuyển `Integer` sang `int` để truyền tham số. Exception xảy ra **khi đánh giá đối số**, trước khi hàm có cơ hội tự kiểm tra giá trị.

Thiết kế hai API khác nhau:

```java
static void acceptRequiredCount(int count) {
    // Không có null: caller phải cung cấp số nguyên.
}

static void acceptNullableCount(Integer count) {
    if (count == null) {
        // Xử lý trạng thái thiếu.
        return;
    }
    int primitive = count;
    // ...
}
```

Nếu dữ liệu bắt buộc, dùng primitive ở biên sau khi đã validate giúp giảm rủi ro NPE ở các tầng sau. Nếu thiếu dữ liệu là trạng thái hợp lệ, API nhận wrapper nhưng phải có nhánh xử lý riêng.

### 2.3. Gọi trực tiếp intValue()

```java
Integer number = null;
// int value = number.intValue(); // NPE.
```

Đây là unboxing **tường minh**. Cách viết không tạo ra ngoại lệ khác với unboxing ngầm; bản chất là cùng việc cố lấy giá trị từ tham chiếu `null`.

## 3. Phép số học tự động unbox toán hạng wrapper

Một biểu thức trông đơn giản như:

```java
Integer quantity = null;
int total = quantity + 1; // NPE.
```

vẫn phải thực hiện unboxing để toán tử `+` nhận giá trị số. Tương tự với phép trừ, nhân, chia, lấy phần dư và các biểu thức dùng phép toán số nguyên.

```java
Integer amount = null;

// int a = amount * 2; // NPE.
// int b = amount / 2; // NPE.
// int c = amount % 2; // NPE.
// boolean positive = amount > 0; // NPE.
```

Khi `amount` là `null`, không có phép toán nguyên thủy nào thực sự được thực hiện trước khi exception phát sinh. Đừng nhầm với trường hợp `amount` hợp lệ nhưng số chia bằng 0: đó có thể là `ArithmeticException` sau khi unboxing thành công.

### 3.1. ++, -- và gán kết hợp cũng unbox

```java
Integer count = null;
// count++; // NPE.
// ++count; // NPE.
// count += 1; // NPE.
```

Các toán tử cập nhật yêu cầu lấy giá trị hiện tại của wrapper, thực hiện phép tăng/giảm hoặc phép cộng, rồi boxing giá trị mới vào biến tham chiếu. Nếu giá trị cũ là `null`, thao tác bị chặn ngay ở bước unboxing.

Một ví dụ không null:

```java
Integer count = 3;
count++;                // Unbox 3, cộng 1, box Integer 4.
System.out.println(count); // 4
```

Điểm quan trọng: `Integer` không tự trở thành kiểu primitive chỉ vì dùng `++`. Biến vẫn có kiểu wrapper và có thể được gán `null` ở một thời điểm khác.

### 3.2. Wrapper từ phép toán không làm biểu thức “an toàn với null”

```java
Integer a = 10;
Integer b = null;

// Integer result = a + b; // NPE khi unbox b.
```

Dù biến nhận có kiểu `Integer`, **toán tử cộng số học** trên `Integer` sẽ unbox toán hạng, tính toán theo numeric promotion rồi box kết quả nếu cần. Wrapper ở biến kết quả **không** khiến phép cộng biết xử lý `null`.

## 4. So sánh bằng: vì sao Integer == Integer có thể không unbox?

Đây là bẫy ngược lại: có trường hợp `==` **không** unbox. Phải xét **kiểu của cả hai toán hạng**, không chỉ nhìn tên toán tử.

### 4.1. Hai toán hạng đều là Integer: so sánh tham chiếu

```java
Integer left = null;
Integer right = null;

System.out.println(left == right); // true: cùng là null.
```

Ở đây, cả hai toán hạng có **kiểu tham chiếu `Integer`**. `==` thực hiện so sánh tham chiếu, không cần unboxing. Vì vậy hai giá trị `null` được coi là cùng tham chiếu `null` và không ném NPE.

Nhưng so sánh tham chiếu không phải cách chuẩn để so **giá trị số** của hai wrapper:

```java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);

System.out.println(a.equals(b)); // true
// a == b so sánh identity, không phải một hợp đồng so sánh giá trị.
```

Các trường hợp cache wrapper có thể làm `==` tình cờ đúng; không dựa vào điều đó để quyết định hai số có bằng nhau không.

### 4.2. Một bên là primitive: có unboxing

```java
Integer left = null;

// boolean same = left == 0; // NPE.
```

Vì vế phải `0` là primitive `int`, Java áp dụng quy tắc numeric promotion/unboxing để so sánh số. `left` phải trở thành `int`; nếu là `null` thì NPE.

Bảng phân biệt:

| Biểu thức | Có unboxing? | Hành vi |
| --- | --- | --- |
| `Integer a = null, b = null; a == b` | Không | `true` (so sánh tham chiếu) |
| `Integer a = null; a == 0` | Có | NPE |
| `Integer a = null; a < 0` | Có | NPE |
| `Integer a = null; Objects.equals(a, null)` | Không | `true` |
| `Integer a = null; a.equals(0)` | Không có bước unboxing bắt buộc | NPE do **gọi method trên receiver null** |
| `Integer a = 5; int n = 5; a == n` | Có | `true` sau unboxing |

Có ít nhất **hai đường đi dẫn đến NPE** cần phân biệt: một là **unboxing null**, hai là **gọi phương thức của đối tượng null**, chẳng hạn `a.equals(...)`. Chúng cùng ném `NullPointerException` nhưng có nguyên nhân trực tiếp khác nhau.

### 4.3. Chọn cách so sánh

- Với **hai wrapper có thể null**, dùng `Objects.equals(a, b)` nếu muốn null-safe và so giá trị theo hợp đồng `equals`.
- Với wrapper bắt buộc không null, validate trước rồi unbox; từ đó có thể dùng phép so sánh số học.
- Không dùng `==` giữa hai `Integer` để thay thế `equals`; nó so identity, có thể chịu ảnh hưởng caching.
- `Objects.equals(Integer.valueOf(1), Long.valueOf(1L))` là `false` vì hai wrapper có kiểu khác nhau. Muốn so **giá trị số khác kiểu**, hãy chọn quy tắc chuyển đổi tường minh sau khi kiểm tra null và miền giá trị.

## 5. Toán tử ba ngôi ?: có thể unbox dù biến nhận là wrapper

Toán tử điều kiện `condition ? expression1 : expression2` xác định **kiểu của biểu thức kết quả** từ hai nhánh, không đơn giản dựa vào kiểu biến nhận. Khi một nhánh là wrapper số và nhánh kia là primitive, quy tắc chọn kiểu có thể khiến wrapper phải unbox.

```java
Integer nullable = null;
boolean chooseNullable = true;

// int result = chooseNullable ? nullable : 0; // NPE nếu chọn nhánh nullable.
```

Nếu hai nhánh có kiểu `Integer` và `int` như trên, kết quả số học là `int`, nên nhánh `nullable` phải unbox khi được chọn.

Điều dễ gây ngạc nhiên là thay đổi biến nhận thành wrapper **không nhất thiết giúp tránh**:

```java
Integer nullable = null;
boolean chooseNullable = true;

// Integer result = chooseNullable ? nullable : 0;
// Vẫn có thể NPE vì phép điều kiện đã có kiểu int trước khi boxing vào Integer.
```

Nếu ý định là **giữ trạng thái thiếu**, hãy làm hai nhánh cùng có kiểu tham chiếu `Integer`:

```java
Integer nullable = null;
boolean chooseNullable = true;
Integer result = chooseNullable ? nullable : null;
// result == null; không unbox.
```

Hoặc tốt hơn, viết rõ nhánh nghiệp vụ:

```java
Integer result;
if (chooseNullable) {
    result = nullable;
} else {
    result = Integer.valueOf(0);
}
```

**Không cần sợ mọi toán tử ba ngôi**: chỉ nhánh được chọn được đánh giá khi chạy. Nhưng quy tắc xác định **kiểu biểu thức chung** có thể khiến giá trị wrapper của nhánh đó bị unbox.

## 6. Map, DTO, JDBC và dữ liệu thiếu

### 6.1. Map.get có thể trả null

```java
Map<String, Integer> counts = new HashMap<>();
Integer found = counts.get("missing"); // null khi không có mapping.
```

Nếu tiếp tục:

```java
// int n = counts.get("missing"); // NPE do unboxing null.
```

Kết quả `Map.get` thường là wrapper, vì Java collections không chứa trực tiếp primitive. Nếu key không tồn tại, `get` trả `null` theo hợp đồng. Một `HashMap` còn có thể lưu mapping từ key sang **giá trị null**.

### 6.2. getOrDefault không luôn thay thế giá trị null

```java
Map<String, Integer> counts = new HashMap<>();
counts.put("present-null", null);

int missing = counts.getOrDefault("missing", 7); // 7
// int present = counts.getOrDefault("present-null", 7); // NPE.
```

`Map.getOrDefault(key, defaultValue)` dùng giá trị mặc định khi **không có mapping cho key**. Nếu key có mapping tới `null`, phương thức trả `null` đó, không nhất thiết áp dụng default. Gán trực tiếp cho `int` khi đó gây NPE.

Cần phân biệt ba trạng thái theo hợp đồng:

| Trạng thái | `containsKey(key)` | `get(key)` |
| --- | --- | --- |
| Key không tồn tại | `false` | `null` |
| Key tồn tại, value là null | `true` | `null` |
| Key tồn tại, value là 0 | `true` | `Integer.valueOf(0)` |

Nếu số 0 và không có dữ liệu mang ý nghĩa khác nhau, **không gộp chúng chỉ để tránh NPE**.

### 6.3. Từ database/JSON/DTO

Một trường số tùy chọn trong DTO có thể được biểu diễn bằng `Integer` hoặc `Long`. Khi nguồn trả `null`, wrapper chứa null; sau đó một phép toán hoặc phép gán về primitive có thể làm chương trình ném NPE.

Ví dụ class DTO:

```java
final class OrderInput {
    Integer quantity; // Có thể null nếu nguồn không cung cấp giá trị.
}
```

`quantity == null` không có nghĩa là `quantity == 0`. Tùy đặc tả:
- **Bắt buộc:** báo lỗi validation khi vắng mặt trước khi tính.
- **Tùy chọn có mặc định 0:** áp dụng mặc định rõ ràng tại ranh giới nhập liệu.
- **Tùy chọn mà thiếu mang nghĩa riêng:** giữ wrapper hoặc mô hình dữ liệu thể hiện trạng thái thiếu.

Khi làm việc với JDBC, cần hiểu cột SQL `NULL`, phương thức đọc dữ liệu và kiểm tra `wasNull()` nếu API trả kiểu primitive; không suy diễn rằng mọi phép đọc SQL NULL sẽ tự tạo wrapper null đúng ý định.

## 7. Mảng wrapper và stream dễ phát sinh NPE

### 7.1. Mảng wrapper khởi tạo với các phần tử null

```java
Integer[] wrappers = new Integer[3];
// wrappers[0], wrappers[1], wrappers[2] đều null.

// int first = wrappers[0]; // NPE khi unbox.

int[] primitives = new int[3];
// primitives[0], primitives[1], primitives[2] đều là 0.
```

Đây là khác biệt về **giá trị mặc định** của phần tử mảng: wrapper array chứa tham chiếu khởi tạo null, còn primitive array có giá trị nguyên thủy mặc định 0.

Với **biến cục bộ** `int x;` chưa gán, Java không cho đọc ngay: đó là **lỗi biên dịch “variable might not have been initialized”**, không phải biến có mặc định 0. Chỉ field và phần tử mảng mới có giá trị mặc định theo quy tắc khởi tạo.

### 7.2. Stream<Integer> sang IntStream

Một pipeline có `null`:

```java
Stream<Integer> stream = Stream.of(1, null, 3);
// int sum = stream.mapToInt(Integer::intValue).sum(); // NPE.
```

Method reference `Integer::intValue` phải gọi phương thức trên mỗi phần tử; nếu phần tử là null thì NPE.

Nếu **quy tắc nghiệp vụ cho phép bỏ các phần tử thiếu**:

```java
int sum = Stream.of(1, null, 3)
        .filter(Objects::nonNull)
        .mapToInt(Integer::intValue)
        .sum(); // 4
```

Bỏ giá trị null là **quyết định nghiệp vụ**, không phải mặc định an toàn trong mọi bài toán. Nếu dữ liệu thiếu là lỗi, hãy validate và báo lỗi thay vì lọc bỏ âm thầm, vì số lượng mẫu và tổng có thể bị thay đổi.

## 8. Cách xử lý null trước khi unbox

### 8.1. Kiểm tra trực tiếp: khi null là không hợp lệ

```java
static int requiredQuantity(Integer quantity) {
    if (quantity == null) {
        throw new IllegalArgumentException("quantity is required");
    }
    return quantity; // Unboxing an toàn sau kiểm tra.
}
```

Ưu điểm là nơi báo lỗi gần nguồn dữ liệu và exception mô tả **quy tắc nghiệp vụ**, thay vì để NPE xuất hiện sâu trong phép tính. Hợp đồng có thể chọn loại exception khác nhưng phải nhất quán.

### 8.2. Cho phép mặc định khi null có ý nghĩa “không cung cấp”

```java
Integer quantity = null;
int count = quantity != null ? quantity : 0;
```

Nhánh `quantity` được unbox chỉ khi khác null; còn nhánh mặc định cung cấp giá trị primitive 0. Đoạn này là một ví dụ có chủ đích lựa chọn default.

Java 9+ có:

```java
int count = Objects.requireNonNullElse(quantity, 0);
```

`Objects.requireNonNullElse` trả đối số đầu nếu không null, hoặc giá trị mặc định (phải khác null). Kết quả `Integer` được unbox an toàn khi default là 0.

### 8.3. Optional<Integer>

```java
int count = Optional.ofNullable(quantity).orElse(0);
```

`Optional` có thể hữu ích tại API muốn thể hiện tính tùy chọn, nhưng **không tự hợp thức hóa** việc thay thiếu bằng số 0. Nếu đầu vào thiếu phải báo lỗi:

```java
int count = Optional.ofNullable(quantity)
        .orElseThrow(() -> new IllegalArgumentException("quantity required"));
```

Lúc gọi `orElseThrow`, giá trị `Integer` được trả ra khác null nếu có dữ liệu; nếu không, exception nghiệp vụ phát sinh.

**Không dùng `Optional.get()` mù quáng** rồi hy vọng nó giải quyết null. `Optional.empty().get()` có lỗi riêng (`NoSuchElementException`), và bạn vẫn cần quyết định ngữ nghĩa dữ liệu vắng mặt.

### 8.4. Objects.requireNonNull là phát hiện sớm, không phải cấp mặc định

```java
Integer quantity = null;
// int count = Objects.requireNonNull(quantity, "quantity must not be null");
// Ném NullPointerException có thông điệp do mình cung cấp.
```

`requireNonNull` hữu ích khi muốn **fail fast** và giữ hợp đồng NPE cho tham số bắt buộc. Nó không biến null thành 0 và không phải phương pháp duy nhất; ở biên yêu cầu nghiệp vụ, `IllegalArgumentException` hoặc lỗi validation thường mang ý nghĩa rõ hơn.

## 9. Phân biệt null, zero và dữ liệu không hợp lệ

Một điểm quan trọng khi thiết kế:

- `null`: **không có đối tượng/không có giá trị** trong một vị trí cho phép tham chiếu null.
- `0`: **giá trị số nguyên hợp lệ**, mang ý nghĩa số lượng bằng không.
- Một số âm ở trường số lượng: có thể là giá trị nguyên hợp lệ về kiểu nhưng **sai quy tắc nghiệp vụ**.
- Chuỗi không phải số: lỗi chuyển đổi đầu vào, không phải unboxing null.

Thí dụ API giỏ hàng:

| Đầu vào quantity | Ý nghĩa đề xuất | Cách xử lý |
| --- | --- | --- |
| `null` | Không cung cấp trường bắt buộc | Trả lỗi validation |
| `0` | Có cung cấp nhưng không chọn sản phẩm | Có thể bị từ chối hoặc chấp nhận tùy hợp đồng |
| `3` | Số lượng ba sản phẩm | Hợp lệ nếu giới hạn cho phép |
| `-3` | Âm | Có thể bị từ chối bởi quy tắc nghiệp vụ |

Nếu mặc định `null → 0` ở mọi nơi, hệ thống có thể che giấu lỗi nguồn dữ liệu, khó phát hiện việc field bị bỏ quên trong một lần cập nhật API.

**Thiết kế ưu tiên:** chuyển DTO nullable thành dữ liệu miền đã được validate ngay ở biên, rồi dùng primitive nếu không còn ý nghĩa “thiếu dữ liệu”.

## 10. Chương trình Java 17+ độc lập

Lưu nội dung sau vào file **`NullUnboxingDemo.java`**. Các trường hợp gây lỗi được bắt riêng để chương trình chạy đến hết, giúp quan sát đúng nơi NPE xuất hiện.

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class NullUnboxingDemo {
    static int requiredQuantity(Integer quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        return quantity;
    }

    public static void main(String[] args) {
        Integer boxed = null;

        try {
            int direct = boxed;
        } catch (NullPointerException ex) {
            System.out.println("direct-unbox=NPE");
        }

        try {
            int added = boxed + 1;
        } catch (NullPointerException ex) {
            System.out.println("arithmetic=NPE");
        }

        try {
            boxed++;
        } catch (NullPointerException ex) {
            System.out.println("increment=NPE");
        }

        Integer anotherNull = null;
        System.out.println("wrapper-reference-equality=" +
                (boxed == anotherNull));

        try {
            boolean equalsZero = boxed == 0;
        } catch (NullPointerException ex) {
            System.out.println("primitive-comparison=NPE");
        }

        System.out.println("objects-null-equality=" +
                Objects.equals(boxed, null));

        boolean selectNull = true;
        try {
            int selected = selectNull ? boxed : 0;
        } catch (NullPointerException ex) {
            System.out.println("ternary=NPE");
        }

        Integer retainedNull = selectNull ? boxed : null;
        System.out.println("nullable-ternary=" + retainedNull);

        Integer[] wrappers = new Integer[1];
        try {
            int arrayValue = wrappers[0];
        } catch (NullPointerException ex) {
            System.out.println("wrapper-array=NPE");
        }
        int[] primitives = new int[1];
        System.out.println("primitive-array=" + primitives[0]);

        Map<String, Integer> map = new HashMap<>();
        map.put("present-null", null);
        System.out.println("missing-default=" +
                map.getOrDefault("missing", 7));

        try {
            int present = map.getOrDefault("present-null", 7);
        } catch (NullPointerException ex) {
            System.out.println("mapped-null=NPE");
        }
        System.out.println("mapping-exists=" +
                map.containsKey("present-null"));

        try {
            int streamSum = Stream.of(1, null, 3)
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (NullPointerException ex) {
            System.out.println("stream=NPE");
        }

        int validSum = Stream.of(1, null, 3)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        System.out.println("filtered-sum=" + validSum);

        System.out.println("fallback=" +
                Objects.requireNonNullElse(boxed, 0));
        System.out.println("optional-fallback=" +
                Optional.ofNullable(boxed).orElse(5));

        try {
            requiredQuantity(boxed);
        } catch (IllegalArgumentException ex) {
            System.out.println("required-validation=error");
        }
        System.out.println("zero-is-valid-value=" +
                (requiredQuantity(0) == 0));
    }
}
```

Lệnh biên dịch và chạy bằng JDK 17+:

```bash
javac NullUnboxingDemo.java
java NullUnboxingDemo
```

Stdout:

```text
direct-unbox=NPE
arithmetic=NPE
increment=NPE
wrapper-reference-equality=true
primitive-comparison=NPE
objects-null-equality=true
ternary=NPE
nullable-ternary=null
wrapper-array=NPE
primitive-array=0
missing-default=7
mapped-null=NPE
mapping-exists=true
stream=NPE
filtered-sum=4
fallback=0
optional-fallback=5
required-validation=error
zero-is-valid-value=true
```

**Giải thích những kết quả quan trọng:**

1. `direct-unbox`, `arithmetic` và `increment` đều là NPE do unboxing null, dù cú pháp rất khác nhau.
2. `wrapper-reference-equality=true` không có NPE vì `boxed == anotherNull` so sánh **hai tham chiếu `Integer`**.
3. `primitive-comparison=NPE` vì `boxed == 0` đòi unbox `Integer` sang `int`.
4. Toán tử ba ngôi trả về `int` trong biểu thức trộn `Integer` và `int`, gây NPE khi nhánh wrapper null được chọn. Biểu thức hai nhánh tham chiếu `Integer`/`null` giữ được null.
5. Mảng `Integer[]` mới chứa phần tử null; mảng `int[]` mới chứa 0.
6. `getOrDefault` không thay null của một **mapping đang tồn tại**; `containsKey` cho biết đây không phải key bị thiếu.
7. Filter trong stream loại null **vì chương trình chủ ý cho phép bỏ qua** giá trị không có dữ liệu.
8. `requireNonNullElse` và `Optional.orElse` đưa ra giá trị mặc định có chủ đích; `requiredQuantity` ngược lại chủ động từ chối null nhưng chấp nhận giá trị 0.

## 11. Cách xác định dòng code gây NPE

Khi gặp NPE, hãy rà soát **nguồn wrapper** và **ngữ cảnh nguyên thủy**:

1. Nguồn của biến có thể null không? Tham số, kết quả `map.get`, field DTO, mảng wrapper mới, kết quả từ database?
2. Dòng lỗi có phép gán wrapper sang primitive, toán tử số học, `++`/`--`, `+=`, so sánh wrapper với số nguyên hay toán tử ba ngôi hỗn hợp không?
3. Nếu có `.intValue()`, `.longValue()`, `.doubleValue()` hoặc `Integer::intValue`, receiver có thể null không?
4. Wrapper có vừa bị lấy ra khỏi collection không? Giá trị null có phải trạng thái hợp lệ của mapping?
5. NPE có thực sự do unboxing, hay do gọi method như `x.equals(...)` trên receiver null?
6. Nên báo lỗi bắt buộc thiếu dữ liệu, giữ trạng thái null, hay chọn giá trị mặc định?
7. Có thể validate và chuyển sang primitive **một lần tại ranh giới hệ thống** để loại bỏ null khỏi phần tính toán phía sau không?

Với JVM hiện đại, thông điệp NPE có thể chỉ ra biến hoặc thao tác được dereference, nhưng **không nên viết test phụ thuộc chuỗi thông báo NPE**: wording có thể thay đổi theo phiên bản JVM, cách biên dịch và cấu trúc bytecode. Kiểm tra loại exception và kết quả nghiệp vụ thay thế.

### Không nên “chữa” bằng try/catch NullPointerException trên mọi phép tính

Bắt NPE trên toàn bộ pipeline như một đường xử lý dữ liệu bình thường thường che giấu lỗi ở những object khác. Nên kiểm tra null ở nguồn hoặc chuyển đổi có chủ đích trước phép tính. Chỉ dùng `try/catch` khi thật sự có một ranh giới xử lý ngoại lệ hợp lý và có thể nhận biết đúng nguyên nhân.

## 12. Những sai lầm thường gặp

| Nhầm lẫn | Vì sao sai? | Cách xử lý |
| --- | --- | --- |
| `Integer x = null; int n = x;` tự lấy 0 | Unboxing null ném NPE | Quy định null là lỗi, optional hoặc default |
| Chỉ có `intValue()` mới gây NPE | Java tự unbox trong toán tử và gọi method | Kiểm tra ngữ cảnh yêu cầu primitive |
| `Integer x=null; x++` khởi tạo thành 1 | `++` phải unbox giá trị cũ trước | Khởi tạo/validate trước cập nhật |
| `Integer x=null; x == 0` là false | So với primitive buộc unbox | `x != null && x == 0` hoặc validation |
| Hai `Integer` so bằng `==` là so giá trị | So tham chiếu và có caching | `Objects.equals` hoặc unbox sau validation |
| `x.equals(y)` an toàn khi x null | Gọi method trên null ném NPE | `Objects.equals(x,y)` |
| `Integer result = condition ? x : 0` chắc chắn giữ null | Kiểu biểu thức có thể là primitive `int` | Đặt nhánh/kiểu chung tường minh |
| `Map.getOrDefault` thay mọi null bằng default | Mapping key→null khác key vắng mặt | Xét `containsKey` hoặc chuẩn hóa theo hợp đồng |
| `Integer[]` mặc định chứa số 0 | Các phần tử mặc định là null | Khởi tạo phần tử hoặc dùng `int[]` |
| `Stream<Integer>.mapToInt` tự bỏ qua null | Chuyển phần tử null bằng `intValue` ném NPE | Filter/validate có chủ đích |
| Thay mọi null bằng 0 là cách sửa chung | Mất phân biệt “thiếu” và “bằng không” | Thiết kế miền dữ liệu rõ ràng |
| `Objects.requireNonNull` cấp giá trị mặc định | Nó ném NPE khi null | Dùng `requireNonNullElse` nếu default phù hợp |
| Tưởng NPE là ArithmeticException | Unboxing null là lỗi tham chiếu | Phân biệt NPE, lỗi miền và lỗi chia cho 0 |

## 13. Bài tập tự kiểm tra

1. **Gán và phép toán.** Dự đoán kết quả của `Integer value=null; int a=value;` và `int b=value+1;`. Vì sao cả hai biên dịch nhưng ném NPE?
2. **So sánh wrapper.** Cho `Integer a=null,b=null;`. Dự đoán `a==b`, `a==0`, `Objects.equals(a,b)` và `a.equals(b)`. Trong mỗi trường hợp, xác định có unboxing không.
3. **Toán tử ba ngôi.** Phân tích `Integer x=null; int y=flag?x:0;` với `flag=true` và `flag=false`. Nếu cần giữ null, viết lại kiểu kết quả và nhánh phù hợp.
4. **Map.** Một `HashMap<String,Integer>` chứa mapping `"a" → null` nhưng không có key `"b"`. `getOrDefault` cho kết quả nào? Khi unbox trực tiếp, trường hợp nào ném NPE?
5. **Mảng.** Vì sao `Integer[] a=new Integer[3]` dễ ném NPE khi tính tổng, còn `int[] b=new int[3]` thì không? Khi nào việc dùng 0 mặc định lại che mất dữ liệu chưa nhập?
6. **Stream.** Viết pipeline tính tổng các phần tử non-null; giải thích vì sao quyết định loại null cần xuất phát từ nghiệp vụ.
7. **Thiết kế API.** Một yêu cầu đặt hàng có `quantity` tùy chọn. Hãy chọn một trong ba hợp đồng: thiếu là lỗi, thiếu là số 0, hoặc thiếu có ý nghĩa riêng. Viết hàm chuyển DTO sang mô hình đã validate.
8. **Test biên.** Tạo unit test cho `requiredQuantity(null)`, `requiredQuantity(0)`, `requiredQuantity(5)`, và trường hợp số âm. NPE có phải kiểu lỗi tốt nhất để trả về từ tầng API không?

## 14. Checklist trước khi đưa code vào production

- Xem `Integer`/`Long`/`Double` như **tham chiếu có thể null**, không phải primitive.
- Xác định rõ các nơi Java tự unbox: gán sang primitive, tham số primitive, toán tử số học, `++`/`--`, phép so sánh với primitive, toán tử ba ngôi hỗn hợp, map/stream/mảng wrapper.
- Trước khi unbox, **kiểm tra null hoặc chuyển thành giá trị mặc định có chủ đích**.
- Không dùng `==` giữa hai wrapper để kiểm tra giá trị số; không gọi `equals` trên receiver có thể null.
- Phân biệt **key vắng mặt**, **mapping null** và **giá trị 0** khi đọc `Map`.
- Đừng giả định mảng wrapper mới có các phần tử 0.
- Nếu đầu vào bắt buộc, validate sớm và cân nhắc chuyển sang primitive cho phần tính toán nội bộ.
- Nếu dữ liệu tùy chọn, giữ wrapper/`Optional` hoặc trạng thái miền tường minh thay vì âm thầm biến null thành 0.
- Viết test cho cả null, 0, giá trị hợp lệ, giá trị ngoài miền và đường chuyển đổi vào primitive.
- Chỉ bắt NPE khi ranh giới xử lý thật sự có ý nghĩa; ưu tiên sửa nơi phát sinh dữ liệu null.

**Kết luận:** `NullPointerException` khi unbox xảy ra không phải vì phép toán số học sai, mà vì Java cần lấy giá trị nguyên thủy từ **một wrapper đang là null**. Cách phòng tránh bền vững là làm rõ ngữ nghĩa dữ liệu thiếu, kiểm tra tại ranh giới và chỉ unbox sau khi biết chắc có giá trị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-004">So sánh == giữa hai wrapper và giữa wrapper với primitive</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh `==` giữa hai wrapper và giữa wrapper với primitive trong Java

Java có hai nhóm kiểu thường gặp khi biểu diễn số: **primitive** (`int`, `long`, `double`, …) và **wrapper** (`Integer`, `Long`, `Double`, …). Wrapper là **đối tượng tham chiếu** chứa một giá trị primitive tương ứng. Điều này làm cho cùng ký hiệu `==` có thể mang hai ý nghĩa rất khác nhau:

- Khi **cả hai toán hạng là các wrapper có cùng kiểu tham chiếu**, `==` thường so sánh **có cùng đối tượng hay không** (*reference identity*), chứ không so sánh giá trị số.
- Khi **một toán hạng là wrapper số còn toán hạng kia là primitive số**, Java thường **unbox** wrapper, áp dụng chuyển kiểu số học thích hợp rồi so sánh **giá trị số**.
- Nếu wrapper cần unbox là `null`, thao tác có thể ném **`NullPointerException`**.
- `equals` và `Objects.equals` dùng quy tắc bằng nhau của đối tượng, khác `==` tham chiếu. Với các wrapper số, `equals` thường đòi hỏi **cùng loại wrapper và cùng giá trị theo quy tắc của loại đó**.

Hiểu rõ **kiểu tĩnh của hai toán hạng** và **có xảy ra unboxing không** là chìa khóa để giải thích một phép so sánh.

## 1. Phân biệt primitive, wrapper và boxing/unboxing

| Primitive | Wrapper tương ứng | Ví dụ |
| --- | --- | --- |
| `byte` | `Byte` | `byte n = 7; Byte w = n;` |
| `short` | `Short` | `Short w = (short) 7;` |
| `char` | `Character` | `Character w = 'A';` |
| `int` | `Integer` | `Integer w = 7;` |
| `long` | `Long` | `Long w = 7L;` |
| `float` | `Float` | `Float w = 7.0f;` |
| `double` | `Double` | `Double w = 7.0;` |
| `boolean` | `Boolean` | `Boolean w = true;` |

**Autoboxing** chuyển primitive thành wrapper khi ngữ cảnh cho phép:

```java
int value = 42;
Integer boxed = value; // int -> Integer
```

**Unboxing** chuyển wrapper về primitive:

```java
Integer boxed = 42;
int plain = boxed; // Integer -> int
```

Wrapper có thể là `null`; primitive số không thể là `null`. Các ví dụ ngắn trong phần giải thích được viết để đặt bên trong method/class, trừ khi có ghi rõ là chương trình chạy độc lập.

## 2. Hai wrapper `Integer`: `==` so sánh tham chiếu

`Integer` là một class. Với hai biến `Integer`, Java có thể dùng toán tử `==` cho **reference equality**:

```java
Integer a = Integer.valueOf(42);
Integer b = a;
boolean sameObject = (a == b); // true vì b trỏ đúng object của a
```

Nếu hai wrapper là hai object khác nhau nhưng chứa cùng giá trị `int`, `==` của hai tham chiếu sẽ là `false`; `equals` có thể vẫn `true`. Tuy nhiên, với `Integer.valueOf` và autoboxing, JVM có thể **tái sử dụng object**, khiến hai phép boxing riêng cùng trỏ một object.

**Không được kết luận** rằng `Integer x = 1000; Integer y = 1000; x == y` luôn có kết quả `false`. Với một số giá trị, implementation có thể cache thêm ngoài mức tối thiểu; code đúng phải **không phụ thuộc** identity của các wrapper được boxing riêng.

### Vì sao cache từ -128 đến 127 dễ đánh lừa người học?

Java bảo đảm việc boxing các giá trị `int` thuộc khoảng **-128 đến 127** dùng đối tượng được tái sử dụng theo quy định của ngôn ngữ. Do đó:

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b);      // true: khoảng cache bảo đảm
System.out.println(a.equals(b)); // true: cùng giá trị int
```

Nhưng bên ngoài khoảng bảo đảm:

```java
Integer x = 1_000;
Integer y = 1_000;

// Không viết chương trình nghiệp vụ phụ thuộc vào (x == y).
// Dùng:
boolean equalValues = x.equals(y); // true
```

Tại sao nhiều tài liệu nói `128 == 128` với hai `Integer` cho `false`? Đó là kết quả thường thấy của cấu hình JVM phổ biến, **không phải hợp đồng chung mà chương trình được quyền dựa vào**. Một số wrapper nguyên khác cũng có quy tắc tái sử dụng giá trị boxing nhỏ; vì thế cache không nên trở thành cách kiểm tra giá trị số.

### Tham chiếu giống hệt nhau vẫn luôn `==`

```java
Integer n = Integer.valueOf(500);
Integer alias = n;
System.out.println(n == alias); // true, bất kể n có trong cache hay không
```

Đây là identity của object, không liên quan tới miền giá trị của `int`.

## 3. Wrapper và primitive: unboxing để so sánh giá trị

Khi một vế là primitive số và vế kia là wrapper số, Java thực hiện **unboxing conversion** ở toán hạng wrapper, sau đó so sánh số học theo quy tắc chuyển kiểu thích hợp:

```java
Integer boxed = 1_000;
int primitive = 1_000;
System.out.println(boxed == primitive); // true
System.out.println(primitive == boxed); // true
```

Điều này **khác hẳn** `Integer == Integer`. So sánh ở đây không hỏi hai tham chiếu có chung object nào; `primitive` không phải tham chiếu.

Một ví dụ khác với widening:

```java
Integer a = 10;
long b = 10L;
System.out.println(a == b); // true: Integer -> int, int -> long
```

Hoặc:

```java
Long wrapped = 10L;
int primitive = 10;
System.out.println(wrapped == primitive); // true: Long -> long; int -> long
```

**Giá trị có thể được chuyển kiểu trước khi so sánh.** Nếu có số thực, numeric promotion có thể gây mất precision ở giá trị lớn, ví dụ `long` được mở rộng sang `double`. Đừng hiểu một phép `==` có unboxing là phép so sánh “chính xác tuyệt đối cho mọi cặp số” sau mọi chuyển kiểu.

### Tình huống ngược: ép wrapper thành primitive tường minh

Khi mục đích là so sánh giá trị giữa hai wrapper, bạn có thể làm rõ bằng unbox:

```java
Integer left = 1_000;
Integer right = 1_000;
boolean equalInts = (left.intValue() == right.intValue()); // true
```

Cách này dễ hiểu, nhưng cả hai lời gọi `intValue()` sẽ ném `NullPointerException` nếu tham chiếu tương ứng là `null`. Nếu không cần unbox, `left.equals(right)` hoặc `Objects.equals(left, right)` thường gọn hơn.

## 4. Khi có `null`, `==` có thể an toàn hoặc ném exception

`Integer` là tham chiếu nên có thể chứa `null`:

```java
Integer missing = null;
Integer alsoMissing = null;
System.out.println(missing == alsoMissing); // true: cả hai tham chiếu null
System.out.println(missing == null);        // true: không unbox
```

Ngược lại:

```java
Integer missing = null;
int zero = 0;
// System.out.println(missing == zero); // NPE lúc chạy vì phải unbox missing
```

Dòng được chú thích **biên dịch hợp lệ** nhưng ném `NullPointerException` nếu thực thi. So sánh `missing == null` không gây unboxing vì vế còn lại là literal `null` tham chiếu, không phải giá trị `int` 0.

### Cách kiểm tra null trước khi unbox

```java
Integer count = null;
boolean isTen = count != null && count == 10; // false, an toàn
```

`&&` đánh giá từ trái sang phải và **short-circuit**: nếu `count != null` là `false`, vế `count == 10` không được thực thi.

Đổi thứ tự sẽ mất an toàn:

```java
Integer count = null;
// boolean wrong = count == 10 && count != null;
// nếu thực thi, NPE ngay tại count == 10
```

Đối với object:

```java
Integer a = null;
Integer b = null;
boolean bothMissing = java.util.Objects.equals(a, b); // true
```

Cần xem **hai giá trị cùng thiếu** có thực sự được nghiệp vụ coi là bằng nhau không. `Objects.equals(null, null)` trả `true` theo contract, nhưng một rule xác thực đầu vào có thể đòi hỏi cả hai đều khác `null`.

## 5. `equals` và `Objects.equals` so sánh điều gì?

Với hai `Integer`, `a.equals(b)` so sánh **giá trị nguyên** khi `b` cũng là một `Integer`:

```java
Integer a = 1_000;
Integer b = 1_000;
System.out.println(a.equals(b)); // true
```

`equals` là method trên tham chiếu bên trái. Nếu `a == null`, lời gọi `a.equals(b)` gây NPE. `Objects.equals(a, b)` là tiện ích so sánh có xử lý null:

```java
Integer a = null;
Integer b = 7;
System.out.println(java.util.Objects.equals(a, b)); // false
System.out.println(java.util.Objects.equals(null, null)); // true
```

Bảng quyết định:

| Cú pháp | Khi hai đối số là `Integer` | Khi có `null` |
| --- | --- | --- |
| `a == b` | So sánh cùng tham chiếu object | Hai null bằng nhau, một null thì khác |
| `a.equals(b)` | So sánh cùng loại wrapper và giá trị | NPE nếu chính `a` là null |
| `Objects.equals(a, b)` | So sánh bằng `equals` khi khác null | Không NPE; hai null được coi bằng nhau |
| `a.intValue() == b.intValue()` | So sánh hai giá trị primitive `int` | NPE nếu một wrapper là null |

**Không thay toàn bộ `==` trong chương trình bằng `equals`**: primitive không gọi được method, và nhiều nơi bạn chủ đích muốn so sánh tham chiếu. Hãy xác định ý nghĩa mong muốn trước.

## 6. Hai wrapper kiểu số khác nhau: một trường hợp dễ nhầm

`Integer` và `Long` là **hai class không có quan hệ kế thừa trực tiếp** và không tự động trở thành cùng kiểu chỉ vì chúng đều chứa số nguyên.

```java
Integer i = 7;
Long l = 7L;

System.out.println(i.equals(l)); // false: Integer và Long khác loại
System.out.println(i.longValue() == l.longValue()); // true
```

Nếu viết `i == l` với hai toán hạng đều là tham chiếu `Integer` và `Long`, Java **báo lỗi biên dịch** vì hai kiểu tham chiếu không thể so sánh bằng `==` theo quy tắc reference equality trong trường hợp này. Nó **không tự động unbox cả hai wrapper số khác kiểu** để chọn phép so sánh số học.

Muốn so sánh theo giá trị, chuyển các toán hạng thành primitive rõ ràng, hoặc chọn API so sánh theo miền số đã xác định:

```java
Integer i = 7;
Long l = 7L;

boolean equal = i.longValue() == l.longValue(); // true
// boolean invalid = i == l; // lỗi biên dịch
```

Khi dùng `equals`, `Integer.valueOf(7).equals(Long.valueOf(7L))` là `false` dù giá trị toán học đều là 7. `Objects.equals` không tự chuyển loại wrapper: nó vẫn theo định nghĩa `equals` của object.

### Cẩn thận với so sánh `long` và `double`

```java
long exactLong = 9_007_199_254_740_993L; // 2^53 + 1
double roundedDouble = 9_007_199_254_740_992.0;
System.out.println(exactLong == roundedDouble); // true
```

Khi `long` so sánh với `double`, Java nâng `long` sang `double`; số nguyên quá lớn có thể bị làm tròn. `==` vì vậy có thể trả `true` cho hai dữ liệu có nguồn toán học ban đầu khác nhau. Đây là giới hạn của việc chuyển kiểu số học, không liên quan cache wrapper.

Nếu nghiệp vụ cần so sánh ID nguyên chính xác, không chuyển chúng qua `double`.

## 7. `Double`, `Float`: bằng nhau số học và bằng nhau object không đồng nhất

Hai giá trị primitive `double` được so sánh số học theo IEEE 754:

```java
double posZero = +0.0;
double negZero = -0.0;
System.out.println(posZero == negZero); // true
```

Nhưng `Double.equals` phân biệt bit dấu của zero:

```java
Double pos = Double.valueOf(+0.0);
Double neg = Double.valueOf(-0.0);
System.out.println(pos.equals(neg)); // false
System.out.println(pos == +0.0);     // true: unbox pos và so sánh primitive
System.out.println(neg == +0.0);     // true: unbox neg, primitive -0.0 == +0.0
```

Lưu ý ở đây `pos.equals(neg)` khác `pos == +0.0` vì **khác cơ chế so sánh**; câu đầu dùng định nghĩa `equals` của wrapper, câu sau unbox để dùng so sánh primitive.

Tương tự, `NaN`:

```java
double nan = Double.NaN;
System.out.println(nan == nan); // false theo IEEE 754

Double boxedNaN1 = Double.valueOf(Double.NaN);
Double boxedNaN2 = Double.valueOf(Double.NaN);
System.out.println(boxedNaN1.equals(boxedNaN2)); // true
```

**Không sử dụng `==` giữa hai tham chiếu `Double` để kiểm tra hai giá trị NaN**: đó là so sánh object identity và phụ thuộc việc tạo/tái sử dụng object, không trực tiếp nói lên ngữ nghĩa NaN.

`Float` có điểm tương ứng với zero có dấu và NaN. Các ngoại lệ này là lý do nên chọn phép so sánh theo yêu cầu cụ thể chứ không áp dụng khẩu quyết “wrapper thì chỉ dùng equals” một cách máy móc.

## 8. Wrapper trong collection: `HashSet`, `HashMap` và lookup

Collections dạng `HashSet<Integer>` và khóa của `HashMap<Integer, …>` hoạt động qua `equals` và `hashCode`, **không** dùng `==` tham chiếu để quyết định phần tử trùng nhau.

```java
java.util.Set<Integer> values = new java.util.HashSet<>();
values.add(1_000);
values.add(1_000);
System.out.println(values.size()); // 1
```

Ở dòng đầu của đoạn trên, việc Java có cache wrapper 1000 hay không hoàn toàn không ảnh hưởng kết quả set. Hai lần thêm cùng giá trị `Integer` được xem là trùng theo `equals`.

Ngược lại, tập `HashSet<Double>` **phân biệt `+0.0` và `-0.0`**, vì `Double.equals` phân biệt chúng:

```java
java.util.Set<Double> zeros = new java.util.HashSet<>();
zeros.add(+0.0);
zeros.add(-0.0);
System.out.println(zeros.size()); // 2
```

Điều này nhắc rằng một phép so sánh dùng cho khóa phải đúng **hợp đồng ngữ nghĩa**. Nếu ứng dụng muốn zero có dấu là một khóa, hãy chủ động chuẩn hóa giá trị theo chính sách, thay vì giả định mọi API coi chúng bằng nhau.

## 9. Điều gì diễn ra trong overload, biểu thức và toán tử điều kiện?

Khi đưa wrapper vào phương thức nhận primitive, Java có thể unbox theo quy tắc gọi method:

```java
static boolean positive(int value) {
    return value > 0;
}

Integer boxed = 10;
// positive(boxed) -> true: Java unbox Integer thành int
```

Đây là một method và lời gọi minh họa đặt trong class/method phù hợp. Nếu `boxed` là `null`, unboxing sẽ ném NPE.

### `==` không phải lúc nào cũng unbox

```java
Integer a = 10;
Integer b = 10;
int c = 10;

boolean wrapperWrapper = a == b; // reference equality
boolean wrapperPrimitive = a == c; // numeric equality sau unboxing
```

Đừng nhìn vào giá trị đều là 10 mà bỏ qua kiểu tĩnh. Kết quả của biểu thức thay đổi vì overload ngữ nghĩa của toán tử `==` được chọn khác nhau.

### Toán tử `+` kích hoạt unboxing trước so sánh

```java
Integer a = 10;
Integer b = 10;

boolean numeric = (a + 0) == b; // true: a + 0 là int; b được unbox
```

Trong ví dụ này `a + 0` tạo `int` nên `==` cuối cùng là so sánh số học; còn `a == b` là so sánh tham chiếu. Tuy nhiên, đây không phải mẹo nên dùng để “ép equals”. Dùng `a.equals(b)` hoặc `Objects.equals(a,b)` khi muốn so sánh giá trị object, và kiểm tra null một cách rõ ràng.

## 10. Chương trình Java 17+ chạy độc lập

Tạo file **`WrapperEqualityDemo.java`**:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WrapperEqualityDemo {
    public static void main(String[] args) {
        Integer smallA = 100;
        Integer smallB = 100;
        System.out.println("smallIdentity=" + (smallA == smallB));

        Integer largeA = 1_000;
        Integer largeB = 1_000;
        System.out.println("largeValuesEqual=" + largeA.equals(largeB));
        System.out.println("largePrimitiveEqual=" +
                (largeA.intValue() == largeB.intValue()));

        Integer alias = largeA;
        System.out.println("sameAlias=" + (largeA == alias));

        int primitive = 1_000;
        System.out.println("wrapperVsPrimitive=" + (largeA == primitive));
        long longPrimitive = 1_000L;
        System.out.println("wrapperVsLong=" + (largeA == longPrimitive));
        System.out.println("wrapperPlusZero=" + ((largeA + 0) == largeB));

        Integer missing = null;
        Integer alsoMissing = null;
        System.out.println("nullReferences=" + (missing == alsoMissing));
        System.out.println("nullLiteral=" + (missing == null));
        System.out.println("nullSafeEquals=" + Objects.equals(missing, alsoMissing));
        System.out.println("nullGuard=" + (missing != null && missing == 0));
        try {
            boolean outcome = (missing == 0);
            System.out.println("nullUnbox=" + outcome);
        } catch (NullPointerException ex) {
            System.out.println("nullUnbox=NullPointerException");
        }

        Integer numberInt = 7;
        Long numberLong = 7L;
        System.out.println("crossTypeEquals=" + numberInt.equals(numberLong));
        System.out.println("crossTypeNumeric=" +
                (numberInt.longValue() == numberLong.longValue()));

        Set<Integer> values = new HashSet<>();
        values.add(1_000);
        values.add(1_000);
        System.out.println("integerSetSize=" + values.size());

        Double plusZero = Double.valueOf(+0.0);
        Double minusZero = Double.valueOf(-0.0);
        System.out.println("doubleWrapperEquals=" + plusZero.equals(minusZero));
        System.out.println("doublePrimitiveCompare=" + (minusZero == +0.0));

        Set<Double> zeros = new HashSet<>();
        zeros.add(+0.0);
        zeros.add(-0.0);
        System.out.println("doubleSetSize=" + zeros.size());

        double nan = Double.NaN;
        System.out.println("nanPrimitiveSelf=" + (nan == nan));
        System.out.println("nanWrapperEquals=" +
                Double.valueOf(nan).equals(Double.valueOf(nan)));

        long big = 9_007_199_254_740_993L;
        double rounded = 9_007_199_254_740_992.0;
        System.out.println("longDoubleNumericEqual=" + (big == rounded));
    }
}
```

Biên dịch và chạy:

```bash
javac WrapperEqualityDemo.java
java WrapperEqualityDemo
```

Kết quả chính xác:

```text
smallIdentity=true
largeValuesEqual=true
largePrimitiveEqual=true
sameAlias=true
wrapperVsPrimitive=true
wrapperVsLong=true
wrapperPlusZero=true
nullReferences=true
nullLiteral=true
nullSafeEquals=true
nullGuard=false
nullUnbox=NullPointerException
crossTypeEquals=false
crossTypeNumeric=true
integerSetSize=1
doubleWrapperEquals=false
doublePrimitiveCompare=true
doubleSetSize=2
nanPrimitiveSelf=false
nanWrapperEquals=true
longDoubleNumericEqual=true
```

Chương trình **không in `largeA == largeB`**, vì với các giá trị ngoài miền cache bảo đảm, identity của hai wrapper tạo riêng không phải một hợp đồng ổn định để kiểm thử. Các dòng `largeValuesEqual` và `wrapperVsPrimitive` mới là ngữ nghĩa đáng dựa vào trong bài toán so sánh **giá trị**.

## 11. Sai lầm thường gặp và cách khắc phục

| Phát biểu hoặc cách viết | Vấn đề | Cách xử lý |
| --- | --- | --- |
| “Hai `Integer` dùng `==` là so sánh số.” | Sai: hai wrapper tham chiếu cùng kiểu dùng reference identity. | Dùng `equals` hoặc `Objects.equals`. |
| “`Integer 100 == Integer 100` đúng nên mọi `Integer` so sánh `==` đều an toàn.” | Sai: boxing/cache khiến giá trị nhỏ thường dùng chung object. | Không dựa vào cache để kiểm tra giá trị. |
| “`Integer 1000 == Integer 1000` chắc chắn sai.” | Không có bảo đảm identity cho vùng này. | Không phụ thuộc kết quả `==`. |
| “`Integer a == int b` vẫn so sánh tham chiếu.” | Primitive khiến wrapper được unbox để so sánh số. | Nếu có null, kiểm tra trước. |
| “`null Integer == 0` là false.” | Sai: unboxing null ném NPE. | `a != null && a == 0`. |
| “`a.equals(b)` an toàn với `a == null`.” | Sai: gọi method trên null ném NPE. | `Objects.equals(a,b)`, hoặc validate null trước. |
| “`Integer.equals(Long)` cho true nếu cùng 7.” | Sai: wrapper khác class không bằng nhau. | Chuyển sang kiểu số phù hợp trước khi so sánh. |
| “Có thể viết `Integer a == Long b` để tự unbox cả hai.” | Không hợp lệ khi hai vế đều là wrapper số khác loại không so sánh tham chiếu được. | Unbox tường minh rồi so sánh. |
| “`Double.equals` giống primitive `==` với ±0.0.” | Sai: wrapper phân biệt zero có dấu. | Chọn quy tắc cần dùng; có thể chuẩn hóa zero. |
| “`==` hai object `Double` sẽ kiểm tra NaN bằng nhau như `equals`.” | Sai: `==` so sánh identity, còn `equals` có quy tắc riêng cho NaN. | Sử dụng `Double.isNaN`/`Double.equals` phù hợp. |
| “`HashSet<Integer>` dựa vào cache để gộp số trùng.” | Sai: tập dùng `equals`/`hashCode`. | Hiểu contract collection. |

### Quy trình đọc một biểu thức `==`

1. **Xem kiểu tĩnh của mỗi vế.** Hai wrapper cùng loại là hai tham chiếu; wrapper và primitive số sẽ kích hoạt unboxing và numeric comparison.
2. **Xem có thể null không.** Hai tham chiếu so sánh với nhau hoặc với `null` không nhất thiết unbox; wrapper so sánh primitive có thể NPE.
3. **Đừng lấy cache làm logic nghiệp vụ.** Object reuse không phải bằng nhau về số.
4. **Chọn API bằng nhau theo nhu cầu.** `equals` cho wrapper cùng loại; `Objects.equals` để xử lý null; primitive numeric comparison khi chủ đích so sánh giá trị số sau chuyển kiểu.
5. **Kiểm tra khác loại và số thực.** `Integer`/`Long` không tự tương đương trong `equals`; `Double` có NaN/signed-zero và việc chuyển `long` sang `double` có thể mất chính xác.
6. **Với collection, xem `equals` và `hashCode`**, không phỏng đoán từ kết quả `==` của hai wrapper.

**Ghi nhớ:** `Integer == Integer` so sánh **identity**; `Integer == int` so sánh **giá trị sau unboxing**; `Integer.equals(Integer)` so sánh **giá trị trong cùng loại wrapper**. `null` và các quy tắc số thực có thể thay đổi hành vi, nên luôn xác định kiểu của hai toán hạng trước khi dùng `==`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-005">Cache wrapper và giới hạn không nên dùng làm hợp đồng nhận dạng</a>

<details>
<summary>Xem chi tiết</summary>

# Cache wrapper và giới hạn không nên dùng làm hợp đồng nhận dạng trong Java

## 1. Vấn đề: hai đối tượng có cùng giá trị chưa chắc là cùng đối tượng

Khi làm việc với `Integer`, `Long`, `Short`, `Byte` và các lớp wrapper khác, có hai câu hỏi **khác nhau**:

1. **Hai đối tượng biểu diễn cùng một giá trị hay không?** Thông thường dùng `equals` theo hợp đồng của lớp wrapper, hoặc dùng toán tử số học sau khi unbox nếu đã kiểm tra `null`.
2. **Hai biến tham chiếu có trỏ đến chính cùng một đối tượng hay không?** Với hai tham chiếu, `==` kiểm tra *reference identity*.

Java có cơ chế **cache wrapper**: một số lần boxing hoặc gọi `valueOf` có thể trả về đối tượng đã được dùng trước đó thay vì cấp phát đối tượng mới. Vì vậy, `==` giữa một số cặp wrapper có giá trị nhỏ có thể trả `true`, trong khi các giá trị khác có thể trả `false`.

Điều quan trọng nhất của bài học:

> **Cache wrapper không phải hợp đồng nhận dạng số.** Đừng dùng `==` giữa hai tham chiếu wrapper để quyết định chúng có cùng giá trị số, và đừng dùng wrapper làm khóa khóa đồng bộ dựa vào identity.

Ví dụ cơ bản:

```java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);
System.out.println(a == b);      // true: thuộc phạm vi cache được bảo đảm

Integer x = Integer.valueOf(200);
Integer y = Integer.valueOf(200);
System.out.println(x.equals(y)); // true: cùng kiểu Integer và cùng giá trị
```

Không kết luận rằng `x == y` **luôn** `false`. Việc cache thêm giá trị ngoài miền bảo đảm tùy triển khai và cấu hình JVM. Điều chắc chắn để kiểm tra giá trị là `x.equals(y)`.

## 2. Wrapper là đối tượng; boxing và unboxing là chuyển đổi

Java có sáu wrapper số quen thuộc:

| Primitive | Wrapper | Ví dụ tạo từ primitive |
| --- | --- | --- |
| `byte` | `Byte` | `Byte.valueOf((byte) 12)` |
| `short` | `Short` | `Short.valueOf((short) 12)` |
| `int` | `Integer` | `Integer.valueOf(12)` |
| `long` | `Long` | `Long.valueOf(12L)` |
| `float` | `Float` | `Float.valueOf(12.0f)` |
| `double` | `Double` | `Double.valueOf(12.0)` |

Mỗi wrapper là lớp `final`, bất biến, mở rộng `Number`. Đối tượng wrapper có **identity** như một đối tượng tham chiếu; nhưng giá trị số mà nó giữ mới là điều thường có ý nghĩa cho nghiệp vụ.

**Boxing** biến primitive thành wrapper:

```java
int primitive = 42;
Integer boxed = primitive; // autoboxing; tương đương ý nghĩa với Integer.valueOf
```

**Unboxing** lấy primitive từ wrapper:

```java
Integer boxed = Integer.valueOf(42);
int primitive = boxed; // gọi chuyển đổi giá trị tương ứng
```

Java tự chèn boxing/unboxing trong nhiều trường hợp, kể cả gán, phép toán, một số phép so sánh và thao tác collection. Những chuyển đổi đó có thể làm code trông như chỉ đang thao tác số, dù trong thực tế có tham chiếu đối tượng và nguy cơ `null`.

Các phương thức `valueOf` là lựa chọn tạo wrapper thông thường. Các constructor kiểu `new Integer(...)`, `new Long(...)` đã bị deprecate, được đánh dấu hướng tới loại bỏ trong Java hiện đại; không nên tạo đối tượng mới chỉ để ép kiểm tra identity. Nếu nghiệp vụ thật sự cần một thực thể có định danh riêng, hãy định nghĩa lớp thực thể có ID phù hợp.

## 3. Cache wrapper thực sự bảo đảm điều gì?

### 3.1. Phạm vi cache được bảo đảm

Các factory `valueOf` của nhóm wrapper nguyên có cam kết tái sử dụng các đối tượng trong phạm vi sau:

| Wrapper | Phạm vi có đối tượng cache được bảo đảm qua `valueOf` | Ghi chú |
| --- | --- | --- |
| `Byte` | Toàn bộ miền `byte`: `-128..127` | Mọi giá trị `byte` đều nằm trong miền này |
| `Short` | `-128..127` | Có thể cache thêm giá trị |
| `Integer` | `-128..127` | Có thể cache thêm giá trị |
| `Long` | `-128..127` | Có thể cache thêm giá trị |
| `Float` | **Không có cam kết phạm vi cache kiểu trên** | Không suy ra identity từ cùng giá trị |
| `Double` | **Không có cam kết phạm vi cache kiểu trên** | Không suy ra identity từ cùng giá trị |

Với `Byte.valueOf`, mọi giá trị hợp lệ đều thuộc khoảng `-128..127`. Với `Short.valueOf`, `Integer.valueOf` và `Long.valueOf`, giá trị trong khoảng đó được trả về từ cache như cam kết của API. Ngoài khoảng, việc hai lời gọi trả về cùng một đối tượng **không phải hợp đồng được bảo đảm**.

```java
Byte a = Byte.valueOf((byte) -128);
Byte b = Byte.valueOf((byte) -128);
// a == b được bảo đảm true

Short c = Short.valueOf((short) 127);
Short d = Short.valueOf((short) 127);
// c == d được bảo đảm true

Long e = Long.valueOf(127L);
Long f = Long.valueOf(127L);
// e == f được bảo đảm true
```

Có các wrapper khác như `Boolean` và `Character` nhưng chúng không thuộc sáu lớp số nói trên. `Boolean.valueOf` trả các instance `TRUE` hoặc `FALSE`; `Character.valueOf` bảo đảm cache các ký tự từ `'\u0000'` đến `'\u007f'`. Những ví dụ này nhấn mạnh rằng **mỗi API có hợp đồng riêng**; không có quy tắc “tất cả wrapper cache `-128..127`”.

### 3.2. Quy tắc boxing trong đặc tả Java

Ngoài cam kết API của `valueOf`, đặc tả Java quy định những trường hợp kết quả boxing có identity được chia sẻ, đáng chú ý gồm các biểu thức hằng (constant expression) kiểu `byte`, `short`, `int`, `long` trong `-128..127`, `char` trong `0..127`, và các giá trị `boolean`.

Nhưng **đừng diễn dịch ngược** rằng mọi phép boxing của bất kỳ biểu thức nào, ở mọi giá trị hoặc mọi wrapper, đều có identity ổn định. Đặc tả cho phép triển khai chia sẻ thêm đối tượng trong một số trường hợp và các tối ưu triển khai có thể thay đổi.

Một ví dụ thường thấy:

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b); // true trong phạm vi bảo đảm

Integer x = 200;
Integer y = 200;
// Không dựa vào x == y để xác định cùng giá trị
```

Trường hợp thứ hai có thể tạo hai đối tượng hoặc dùng cache mở rộng. **Ứng dụng phải cho cùng quyết định nghiệp vụ trên mọi JVM hợp lệ**, dù kết quả identity có thể khác.

### 3.3. Cache không phải “tập đối tượng có ID số”

Cache giúp giảm số lần cấp phát wrapper thường gặp, nhất là các giá trị nhỏ. Nhưng các giá trị nguyên thủy như `42` không có identity; identity chỉ là thuộc tính của tham chiếu đối tượng wrapper cụ thể.

Không được coi cache như một cơ chế *interning* tổng quát có thể thay cho:

- định danh bản ghi, mã người dùng hay khóa nghiệp vụ;
- cơ chế singleton do ứng dụng thiết kế;
- bộ khóa đồng bộ riêng;
- quan hệ `equals` và `hashCode` trong `HashMap`/`HashSet`.

Việc dùng wrapper để đại diện ID hoàn toàn hợp lệ (ví dụ `Map<Integer, Customer>`), **nếu giá trị được so sánh bằng `equals`**, không phải vì hai tham chiếu `Integer` phải trỏ đến cùng một instance.

## 4. `==` giữa hai wrapper khác `==` khi có primitive

Cùng ký hiệu `==` có thể dẫn đến hai cơ chế khác nhau, phụ thuộc **kiểu tĩnh của toán hạng**.

### Hai toán hạng đều là tham chiếu wrapper

```java
Integer a = Integer.valueOf(200);
Integer b = Integer.valueOf(200);

boolean sameObject = (a == b);  // so sánh tham chiếu
boolean sameValue = a.equals(b); // true
```

`sameObject` không nên được dùng để quyết định `sameValue`. Nếu có `null`, gọi `a.equals(b)` khi `a == null` gây `NullPointerException`.

### Một bên wrapper, một bên primitive

```java
Integer boxed = Integer.valueOf(200);
int raw = 200;

System.out.println(boxed == raw); // true: boxed được unbox, rồi so sánh int
```

Khi ngữ cảnh áp dụng *binary numeric promotion*, Java unbox wrapper để thực hiện so sánh giá trị số. Vì vậy kết quả `boxed == raw` có thể đúng ngay cả khi `boxed == anotherBoxed` không có bảo đảm đúng.

Cẩn thận với `null`:

```java
Integer missing = null;
// System.out.println(missing == 0); // Ném NullPointerException do unboxing
```

Đừng “sửa” một phép so sánh wrapper bằng cách đổi một toán hạng thành primitive nếu đầu vào có thể `null` mà chưa quy định cách xử lý dữ liệu thiếu.

### Hai tham chiếu `null`

```java
Integer first = null;
Integer second = null;

System.out.println(first == second); // true: cả hai đều là null
```

`true` ở đây chỉ nghĩa là cùng tham chiếu `null`. Nó **không** nghĩa hai giá trị số tồn tại và bằng nhau. Nếu cần diễn giải `null` là “thiếu dữ liệu”, phải nêu quy tắc riêng.

## 5. Cách kiểm tra giá trị đúng và an toàn

### 5.1. Hai wrapper cùng kiểu, chắc chắn không `null`

```java
Integer left = Integer.valueOf(200);
Integer right = Integer.valueOf(200);

boolean sameValue = left.equals(right); // true
```

`Integer.equals` so sánh giá trị `int` và loại wrapper tương ứng; không dựa vào số lượng đối tượng được cache.

### 5.2. Wrapper có thể `null`

Dùng `java.util.Objects.equals`:

```java
import java.util.Objects;

Integer left = null;
Integer right = Integer.valueOf(200);

System.out.println(Objects.equals(left, right)); // false
System.out.println(Objects.equals(null, null));  // true
```

`Objects.equals(a, b)` trả `true` khi hai tham chiếu cùng `null`, hoặc khi `a != null` và `a.equals(b)`. Đây là chính sách **so sánh đối tượng có tính đến null**, không phải phép kiểm tra “hai số hợp lệ bằng nhau”. Nếu miền nghiệp vụ không cho phép `null`, phải từ chối nó trước.

### 5.3. Nếu muốn so sánh số primitive

```java
Integer left = Integer.valueOf(200);
Integer right = Integer.valueOf(200);

if (left != null && right != null
        && left.intValue() == right.intValue()) {
    System.out.println("Same numeric int");
}
```

Việc unbox tường minh làm rõ phép so sánh giá trị. Đây là cách tốt khi phép tính sau đó thực sự cần `int` và kiểm tra `null` đã hoàn tất.

### 5.4. Hai wrapper thuộc *khác lớp*

`Integer.valueOf(10).equals(Long.valueOf(10L))` là `false`: hợp đồng `equals` của wrapper nguyên yêu cầu cùng loại wrapper và cùng giá trị.

```java
Integer a = Integer.valueOf(10);
Long b = Long.valueOf(10L);
System.out.println(a.equals(b)); // false
```

Nếu nghiệp vụ muốn hai số khác kiểu được coi tương đương về giá trị toán học, phải định nghĩa một cách chuẩn hóa **không làm mất dữ liệu** trước khi so sánh. Ví dụ với `Integer` và `Long` hữu hạn, `a.longValue() == b.longValue()` bảo toàn mọi giá trị `int`. Không tự chuyển mọi `Number` thành `double` vì nhiều `long` khác nhau có thể làm tròn thành cùng một `double`.

### 5.5. `Float` và `Double` có hợp đồng đặc biệt

`Double.equals` và `Float.equals` không đồng nhất với toán tử primitive `==` cho các giá trị đặc biệt:

```java
System.out.println(Double.NaN == Double.NaN); // false
System.out.println(
        Double.valueOf(Double.NaN)
                .equals(Double.valueOf(Double.NaN))); // true

System.out.println(+0.0 == -0.0); // true
System.out.println(
        Double.valueOf(+0.0).equals(Double.valueOf(-0.0))); // false
```

Tương tự với `Float`: wrapper `equals` coi các giá trị NaN là bằng nhau và phân biệt `+0.0f`/`-0.0f`. Đây là hợp đồng của lớp, **không** phải hệ quả của cache.

Khi cần so sánh gần đúng kết quả tính toán có sai số dấu phẩy động, phải định nghĩa dung sai và chính sách NaN/vô cực riêng. Quan hệ “gần bằng” không thể mặc nhiên thay `equals` cho khóa collection vì có thể không bắc cầu.

## 6. Ví dụ chạy được trên Java 17

Lưu mã sau vào `WrapperCacheDemo.java`:

```java
import java.util.Objects;

public class WrapperCacheDemo {
    public static void main(String[] args) {
        Integer smallA = Integer.valueOf(127);
        Integer smallB = Integer.valueOf(127);
        System.out.println("Integer 127 same object: " + (smallA == smallB));

        Byte byteA = Byte.valueOf((byte) -128);
        Byte byteB = Byte.valueOf((byte) -128);
        System.out.println("Byte -128 same object: " + (byteA == byteB));

        Short shortA = Short.valueOf((short) 127);
        Short shortB = Short.valueOf((short) 127);
        System.out.println("Short 127 same object: " + (shortA == shortB));

        Long longA = Long.valueOf(127L);
        Long longB = Long.valueOf(127L);
        System.out.println("Long 127 same object: " + (longA == longB));

        Integer largeA = Integer.valueOf(200);
        Integer largeB = Integer.valueOf(200);

        // Không in largeA == largeB: identity ngoài cache bảo đảm
        // không phải kết quả ổn định để dùng làm quy tắc nghiệp vụ.
        System.out.println("Integer 200 equals: "
                + largeA.equals(largeB));
        System.out.println("Integer 200 primitive values: "
                + (largeA.intValue() == largeB.intValue()));

        int primitive = 200;
        System.out.println("Wrapper vs primitive: "
                + (largeA == primitive));

        System.out.println("Integer vs Long equals: "
                + Integer.valueOf(10).equals(Long.valueOf(10L)));

        Integer missing = null;
        System.out.println("Objects.equals(null, 200): "
                + Objects.equals(missing, largeA));

        System.out.println("Double NaN wrapper equals: "
                + Double.valueOf(Double.NaN)
                        .equals(Double.valueOf(Double.NaN)));
        System.out.println("Double signed zero wrapper equals: "
                + Double.valueOf(0.0)
                        .equals(Double.valueOf(-0.0)));
    }
}
```

Biên dịch/chạy:

```bash
javac WrapperCacheDemo.java
java WrapperCacheDemo
```

Đầu ra:

```text
Integer 127 same object: true
Byte -128 same object: true
Short 127 same object: true
Long 127 same object: true
Integer 200 equals: true
Integer 200 primitive values: true
Wrapper vs primitive: true
Integer vs Long equals: false
Objects.equals(null, 200): false
Double NaN wrapper equals: true
Double signed zero wrapper equals: false
```

Các kiểm tra tham chiếu trong chương trình **chỉ dùng phạm vi cache được bảo đảm**. Chương trình chủ động không phụ thuộc kết quả `largeA == largeB`, do đó đầu ra mang cùng ý nghĩa trên các JVM tuân thủ hợp đồng API. Phần còn lại kiểm tra giá trị qua `equals` hoặc primitive sau khi unbox rõ ràng.

## 7. Tại sao không dùng wrapper làm khóa đồng bộ?

Ví dụ mã có hại:

```java
Integer lock = Integer.valueOf(42);
synchronized (lock) {
    // Không nên khóa theo đối tượng wrapper!
}
```

Một số wrapper được cache hoặc dùng chung; đoạn mã không liên quan cũng có thể nhận đúng **cùng tham chiếu**. Hệ quả có thể là tranh chấp khóa ngoài dự kiến, trì hoãn, hoặc rủi ro đồng bộ rất khó tìm. Các wrapper cũng được Java xem là **value-based classes**: nhà phát triển được khuyến cáo không dựa vào identity để đồng bộ hóa. Với JDK hiện đại, trình biên dịch có thể cảnh báo khi cố `synchronized` trên đối tượng wrapper.

Để khóa bảo vệ trạng thái của một instance, hãy tạo đối tượng khóa chuyên biệt do chính class sở hữu:

```java
public final class SafeCounter {
    private final Object lock = new Object();
    private int count;

    public void increment() {
        synchronized (lock) {
            count++;
        }
    }

    public int get() {
        synchronized (lock) {
            return count;
        }
    }
}
```

Ở đây `lock` có identity riêng phục vụ đồng bộ; không bị dùng chung do cache số nhỏ. Nếu yêu cầu là bộ đếm đồng thời, `AtomicInteger` cũng là lựa chọn có ý nghĩa, tùy bài toán.

**Không nên dùng `System.identityHashCode(integer)` làm ID nghiệp vụ.** Giá trị đó phụ thuộc identity đối tượng, không phải giá trị số hay mã định danh bền vững; nó có thể khác giữa các lần chạy và không bảo đảm duy nhất.

## 8. Cache ảnh hưởng hiệu năng như thế nào?

Wrapper cache chủ yếu là một tối ưu để **tái sử dụng đối tượng** cho những giá trị thường xuất hiện. Nhưng không nên suy diễn:

- Cứ autoboxing là cấp phát mới: nhiều trường hợp có cache hoặc JVM tối ưu loại bỏ cấp phát.
- Cứ `valueOf` là trả về cùng object cho mọi giá trị: ngoài khoảng bảo đảm, không có cam kết chung như vậy.
- Dùng `Integer` hiệu quả như primitive `int` trong mọi tình huống: wrapper là tham chiếu đối tượng, có thể `null`, cần boxing/unboxing và có thể tăng chi phí bộ nhớ hoặc thao tác.
- Mở rộng cache sẽ cải thiện hiệu năng mọi ứng dụng: hiệu quả phụ thuộc dữ liệu, JVM, GC, tối ưu JIT và cách truy cập; phải benchmark đúng tải thực.

Ví dụ:

```java
Integer count = 0;
count++; // unbox -> cộng một int -> box -> gán tham chiếu mới
```

Đối tượng `Integer` là bất biến: phép tăng **không sửa instance cũ**. Nếu giá trị mới thuộc vùng cache, JVM có thể dùng instance tái sử dụng; nếu ngoài vùng, cách triển khai có thể khác.

Để đếm số học thuần túy trong vòng lặp nóng, ưu tiên primitive:

```java
int sum = 0;
for (int i = 0; i < 1000; i++) {
    sum += i;
}
```

Khi cần `List<Integer>`, `Map<String, Long>` hoặc generic nhận kiểu tham chiếu, wrapper là công cụ đúng, nhưng cần phân biệt đánh giá hiệu năng và tính đúng đắn.

## 9. Cache mở rộng và tính di động

JDK/VM có thể có cấu hình hoặc tối ưu riêng để mở rộng phạm vi `Integer` được cache. Điều này giải thích vì sao cùng một đoạn thử:

```java
Integer x = Integer.valueOf(200);
Integer y = Integer.valueOf(200);
boolean sameReference = x == y;
```

có thể trả `true` trong một môi trường và `false` trong một môi trường khác. Kết quả đó **không chứng minh hai giá trị khác nhau**, và cũng **không phải** tín hiệu để phân nhánh logic.

Một số triển khai có thể tối ưu boxing nhờ phân tích escape hoặc tối ưu máy ảo. Đừng dùng kết quả đo identity, `identityHashCode` hay số lần cấp phát trong một lần chạy làm hợp đồng API về tính bằng nhau.

Quy tắc thiết kế bền vững:

1. Cần *numeric equality* → `equals`, `Objects.equals`, hoặc primitive comparison khi có thể unbox an toàn.
2. Cần định danh thực thể → giữ một ID bất biến riêng và định nghĩa `equals`/`hashCode` theo nghiệp vụ.
3. Cần identity đối tượng thực sự → dùng đối tượng có ownership và vòng đời rõ ràng, không dùng wrapper số.
4. Cần synchronization → dùng khóa riêng (`new Object()`), primitive atomic hoặc cơ chế đồng bộ thích hợp.
5. Cần tối ưu → đo hiệu năng và áp dụng cấu trúc dữ liệu phù hợp, không đánh đổi đúng/sai để phụ thuộc cache.

## 10. Vấn đề với collection và các phép kiểm tra

### `HashMap`/`HashSet` dùng `equals` và `hashCode`

Ví dụ:

```java
import java.util.HashSet;
import java.util.Set;

Set<Integer> ids = new HashSet<>();
ids.add(Integer.valueOf(200));

System.out.println(ids.contains(Integer.valueOf(200))); // true
```

Hai lần `valueOf(200)` **không cần** trả cùng tham chiếu. `HashSet` dùng hợp đồng `hashCode`/`equals` của `Integer` để tìm đúng phần tử.

Ngược lại, `IdentityHashMap` cố ý so sánh khóa theo tham chiếu, nên wrapper không phù hợp nếu ý định là nhận dạng bằng giá trị số. Kể cả khi thử cho thấy một số giá trị nhỏ hoạt động nhờ cache, logic sẽ dễ sai khi giá trị vượt vùng cache hoặc triển khai thay đổi.

### Phép so sánh gần đúng không phải `equals`

Với `Float`/`Double`, nếu cần dung sai vì sai số tính toán, hãy dùng tiêu chí riêng cho phép kiểm thử số học. Một tiêu chí như `Math.abs(a-b) < epsilon` không phải quan hệ tương đương tổng quát: có thể không bắc cầu, cũng không thay hợp đồng `hashCode`.

### Autounboxing trong collection

Khi lấy dữ liệu từ `List<Integer>`:

```java
Integer nullableId = null;
// int id = nullableId; // Ném NullPointerException
```

Bất kể cache có hay không, `null` là một tham chiếu hợp lệ với wrapper và cần chính sách xử lý. `Objects.equals` chỉ giúp so sánh an toàn với `null`; nó không bảo đảm mọi giá trị truyền vào hợp lệ theo miền nghiệp vụ.

## 11. Lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| `Integer a=200, b=200; if (a==b)` | So sánh tham chiếu; identity ngoài cache không ổn định | `a.equals(b)` nếu không null |
| Nghĩ toàn bộ wrapper cache `-128..127` | `Float`/`Double` không có cam kết cache như wrapper nguyên | Đọc đúng hợp đồng API từng loại |
| Giả định ngoài `127` thì `==` luôn sai | JVM có thể cache thêm | Không dựa vào identity |
| Dùng `synchronized(Integer.valueOf(1))` | Có thể khóa trùng object dùng chung; wrapper value-based | Khóa riêng do ứng dụng quản lý |
| `Integer x=null; x==0` | Tự unbox `null` gây `NullPointerException` | Kiểm tra `null` trước |
| So sánh `Integer(10)` với `Long(10)` bằng `equals` rồi kỳ vọng `true` | `equals` yêu cầu đúng wrapper type | Chọn chuyển đổi giữ nguyên giá trị và chính sách chung |
| Coi `Double.equals` giống `double ==` với NaN/±0 | Hai API có hợp đồng khác nhau | Dùng đúng kiểu so sánh |
| Tạo wrapper bằng constructor vì muốn identity riêng | API deprecated; wrapper value-based không phù hợp làm thực thể định danh | Thiết kế class định danh riêng |
| Dùng `IdentityHashMap<Integer, ...>` để tìm khóa theo giá trị | Identity khác equality; cache chỉ che giấu lỗi cho một số số | Dùng `HashMap<Integer, ...>` |
| Dùng `System.identityHashCode(wrapper)` làm mã bền vững | Không phải ID số, không bảo đảm duy nhất | Dùng ID rõ ràng từ domain |
| Giả định `Integer` bất biến có thể `++` tại chỗ | `++` unbox, tính, box đối tượng giá trị mới | Dùng primitive/atomic theo yêu cầu |
| Thấy `a==b` đúng nên kết luận phép so sánh số luôn đúng | Có thể tình cờ dùng cache | Viết test trên cả giá trị trong và ngoài cache; kiểm tra `equals` |

## 12. Bài tập có giải thích

**Bài 1.** Hãy dự đoán:

```java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);
boolean same = a == b;
```

**Đáp án:** `true`. Đây là phạm vi cache được cam kết bởi `Integer.valueOf`. Tuy nhiên, dùng `equals` vẫn là lựa chọn đúng để biểu diễn ý nghĩa “bằng giá trị”.

**Bài 2.** Nếu thay `127` bằng `200` thì có chắc `same` là `false` không?

**Đáp án:** Không. `Integer.valueOf` có thể cache thêm giá trị ngoài `-128..127`; kết quả identity không bảo đảm. `a.equals(b)` luôn là `true` cho hai `Integer` cùng giá trị 200.

**Bài 3.** Tại sao `Integer.valueOf(200) == 200` là `true` dù phép so sánh hai tham chiếu `Integer` có thể khác?

**Đáp án:** Một toán hạng là primitive `int`, nên wrapper được unbox và so sánh **giá trị số**; ở đây không còn là so sánh hai tham chiếu.

**Bài 4.** Điều gì xảy ra khi chạy `Integer a = null; System.out.println(a == 0);`?

**Đáp án:** `NullPointerException` khi Java cố unbox `a` để so sánh với primitive `0`.

**Bài 5.** `Objects.equals(null, null)` trả gì? Có nghĩa hai giá trị số hợp lệ bằng nhau không?

**Đáp án:** `true` theo hợp đồng so sánh tham chiếu nullable; không, cả hai đều biểu thị không có đối tượng số. Nghiệp vụ cần phân biệt dữ liệu thiếu với số 0.

**Bài 6.** Hai `Long.valueOf(127L)` có được phép coi là cùng tham chiếu? Có nên dùng điều này làm logic định danh?

**Đáp án:** Hợp đồng cache `Long.valueOf` bảo đảm chia sẻ ở `-128..127`. Nhưng không nên dùng identity wrapper làm định danh; thay vào đó so sánh bằng giá trị hoặc mô hình ID nghiệp vụ riêng.

**Bài 7.** Vì sao dùng `Integer.valueOf(42)` làm monitor cho `synchronized` nguy hiểm?

**Đáp án:** Giá trị cache có thể khiến những phần mã khác không liên quan nhận cùng object. Khóa bị chia sẻ ngoài ý muốn, và wrapper thuộc nhóm value-based nên không thích hợp cho đồng bộ dựa vào identity. Dùng đối tượng khóa riêng.

**Bài 8.** Khi một `HashSet<Integer>` chứa `200`, vì sao `contains(Integer.valueOf(200))` đúng mà không cần cache?

**Đáp án:** Collection so khớp dựa vào `hashCode`/`equals`, không dựa vào `==` của tham chiếu. Hai `Integer` cùng giá trị có hợp đồng hash/equality tương thích.

**Kết luận:** Cache wrapper là chi tiết tối ưu có **một số bảo đảm phạm vi cụ thể**, không phải cơ chế định danh giá trị. Đọc đúng kiểu của toán hạng để hiểu `==`; dùng `equals` hoặc `Objects.equals` cho wrapper (với chính sách `null`), dùng primitive khi cần tính toán, và luôn dùng đối tượng riêng khi cần identity hoặc khóa đồng bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-006">List&lt;Integer&gt; không phải int&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# `List<Integer>` không phải `int[]` trong Java

Trong Java, `int[]` là **mảng các giá trị nguyên thủy `int`**, còn `List<Integer>` là **kiểu danh sách tham chiếu đến các đối tượng `Integer`**. Cả hai có thể biểu diễn dãy số như 2, 4, 6, nhưng chúng **không có cùng kiểu, cách lưu trữ, API, khả năng thay đổi kích thước, cách xử lý `null` và chi phí thao tác**.

```java
int[] array = {2, 4, 6};
java.util.List<Integer> list = new java.util.ArrayList<>();
list.add(2);
list.add(4);
list.add(6);

System.out.println(array[0]);  // 2
System.out.println(list.get(0)); // 2
```

Kết quả in giống nhau không có nghĩa hai biến có thể hoán đổi cho nhau. Đặc biệt, trong chủ đề **Boxing và unboxing**, `List<Integer>` còn liên quan đến chuyển đổi ngầm giữa `int` và `Integer`, cấp phát/giữ tham chiếu đối tượng, và nguy cơ `NullPointerException` khi unboxing.

## 1. Bảng so sánh nhanh

| Đặc điểm | `int[]` | `List<Integer>` |
| --- | --- | --- |
| Kiểu dữ liệu | Mảng của primitive `int` | Kiểu generic `List` với tham số kiểu tham chiếu `Integer` |
| Phần tử | Giá trị `int` 32 bit | Tham chiếu tới đối tượng `Integer` hoặc `null` nếu implementation cho phép |
| Lấy phần tử | `a[i]` | `list.get(i)` |
| Gán phần tử | `a[i] = value` | `list.set(i, value)` nếu list hỗ trợ |
| Số phần tử | `a.length` (trường) | `list.size()` (phương thức) |
| Tăng/giảm số phần tử | Không; độ dài mảng cố định | Phụ thuộc implementation: `ArrayList` thêm/xóa được; `List.of` không sửa được |
| Phần tử mặc định khi tạo vùng chứa | `new int[3]` chứa ba số 0 | `new ArrayList<Integer>()` rỗng; `new Integer[3]` chứa ba `null` |
| Chấp nhận `null` làm phần tử | Không | Có thể, ví dụ `ArrayList`, nhưng `List.of` cấm |
| Generic type argument | Không áp dụng | Phải dùng `Integer`, không thể viết `List<int>` |
| Mô hình truy cập | Truy cập chỉ số của mảng | Hợp đồng `List`; độ phức tạp phụ thuộc loại list |
| Thư viện thao tác | `Arrays.sort`, `Arrays.stream`, `Arrays.equals` | `sort`, `stream`, `contains`, `add`, `remove`... tùy khả năng sửa đổi |
| Mức sử dụng bộ nhớ | Phần tử `int` được lưu trong mảng primitive | Thường có vùng tham chiếu và các đối tượng wrapper, thêm chi phí quản lý |
| Tính tương thích kiểu | Không phải `Integer[]` hay `List<Integer>` | Không tự trở thành `int[]`, cần chuyển đổi chủ động |

**Cần nhớ:** `List<Integer>` chỉ định *kiểu phần tử*, **không chỉ định cấu trúc bên trong**. `ArrayList<Integer>` thường giữ tham chiếu trong một mảng `Object[]`, nhưng `LinkedList<Integer>` dùng các nút liên kết. Không được suy ra rằng mọi `List<Integer>` đều liên tục trong bộ nhớ hoặc có tốc độ truy cập chỉ số như mảng.

## 2. `int` là primitive, `Integer` là lớp wrapper

`int` không phải một object. `Integer` là một lớp bất biến của `java.lang` chứa giá trị `int` và cung cấp các API như `intValue`, `parseInt`, `compare`.

```java
int primitive = 42;
Integer wrapper = Integer.valueOf(42);
int restored = wrapper.intValue();
```

Java hỗ trợ **autoboxing** và **unboxing** nên nhiều phép chuyển đổi được viết ngắn hơn:

```java
Integer boxed = 42; // Autoboxing: int -> Integer.
int unboxed = boxed; // Unboxing: Integer -> int.
```

Khi thêm `int` vào `List<Integer>`, việc boxing xảy ra ngầm:

```java
java.util.List<Integer> values = new java.util.ArrayList<>();
values.add(10);       // Về ý nghĩa là thêm một Integer chứa 10.
int first = values.get(0); // get trả Integer; gán vào int -> unboxing.
```

**Không phải mọi thao tác `List<Integer>` đều boxing mới**: có thể thêm một tham chiếu `Integer` đã tồn tại; JVM cũng có thể dùng cache wrapper đối với một số giá trị. Vì vậy, nói “mỗi phần tử luôn tạo đúng một object mới” là không chính xác. Điều quan trọng là API làm việc với **đối tượng/tham chiếu**, khác với ô `int` primitive.

### Tại sao `List<int>` không hợp lệ?

Java generics truyền thống yêu cầu **type argument là kiểu tham chiếu**; primitive `int` không phải type argument hợp lệ:

```java
// List<int> bad = new ArrayList<>(); // Không biên dịch.

java.util.List<Integer> good = new java.util.ArrayList<>();
```

Java không tự biến `List<int>` thành `List<Integer>` ở cú pháp generic. Khi cần tính toán theo luồng primitive tránh boxing, Java cung cấp `IntStream`; khi cần dữ liệu primitive lưu liên tục, `int[]` thường là lựa chọn phù hợp hơn.

### `Integer[]` cũng không phải `int[]`

`Integer[]` là mảng **tham chiếu đến `Integer`**, có thể chứa `null`. Trong khi đó `int[]` là mảng chứa **giá trị `int`**. Hai kiểu mảng không tương thích bằng một phép cast thông thường:

```java
int[] primitives = {1, 2, 3};
Integer[] objects = {1, 2, 3}; // Mỗi giá trị được boxing.

// Integer[] wrong = primitives; // Không biên dịch.
// int[] wrong2 = objects;       // Không biên dịch.
```

`Integer[]` có phần tử và bộ nhớ khác `int[]`; nó gần với `List<Integer>` về kiểu đối tượng phần tử, nhưng vẫn là **mảng có độ dài cố định**, không phải List.

## 3. Khác biệt API: `length`, `size()`, `[]`, `get()`, `set()`

Với mảng:

```java
int[] ages = {18, 20, 22};
System.out.println(ages.length); // 3.
System.out.println(ages[1]);     // 20.
ages[1] = 21;
```

Với danh sách:

```java
java.util.List<Integer> ages =
        new java.util.ArrayList<>(java.util.List.of(18, 20, 22));

System.out.println(ages.size()); // 3.
System.out.println(ages.get(1)); // 20.
ages.set(1, 21);
```

Không thể dùng `ages.length` hoặc `ages[1]` khi `ages` có kiểu `List<Integer>`. Ngược lại, mảng không có `get`, `set`, `add` hoặc `size()` như `List`.

### Kích thước và khả năng sửa đổi là hai trục khác nhau

Mảng có độ dài cố định, nhưng **phần tử có thể sửa**:

```java
int[] nums = {1, 2};
nums[0] = 9; // Hợp lệ.
// nums.add(3); // Không có add.
```

`List<Integer>` chỉ là interface: nó có phương thức thay đổi dữ liệu nhưng implementation có thể **không hỗ trợ**. Ví dụ:

```java
java.util.List<Integer> resizable = new java.util.ArrayList<>();
resizable.add(1);
resizable.add(2); // Tăng size lên 2.

java.util.List<Integer> fixedValues = java.util.List.of(1, 2);
// fixedValues.add(3); // Ném UnsupportedOperationException lúc chạy.
// fixedValues.set(0, 9); // Cũng ném UnsupportedOperationException.
```

`List.of` (Java 9+) tạo danh sách **không sửa đổi được** (*unmodifiable*). Nó không cho thêm/xóa, cũng không cho thay phần tử và **không chấp nhận phần tử `null`**. `ArrayList` mặc định có thể thêm/xóa/set và có thể chứa `null`.

Không nên suy luận rằng `List<Integer>` luôn “có thể tăng kích thước”; điều đó chỉ đúng với một số triển khai và điều kiện hoạt động.

## 4. Tác động của `null` và unboxing

Đây là khác biệt quan trọng nhất về độ an toàn.

`new int[3]` tạo **ba giá trị 0**. Mỗi phần tử của mảng primitive luôn là một `int` hợp lệ, không thể là `null`.

```java
int[] ints = new int[1];
System.out.println(ints[0]); // 0.
```

Ngược lại, mảng wrapper `new Integer[3]` có các phần tử mặc định bằng `null`, còn `new ArrayList<Integer>()` ban đầu có **size = 0**, không có phần tử nào:

```java
Integer[] objects = new Integer[1];
System.out.println(objects[0]); // null.

java.util.List<Integer> empty = new java.util.ArrayList<>();
System.out.println(empty.size()); // 0.
```

Danh sách cho phép `null` có thể gây lỗi khi bạn lấy giá trị primitive:

```java
java.util.List<Integer> list = new java.util.ArrayList<>();
list.add(null);

// int x = list.get(0); // Ném NullPointerException lúc unboxing.
Integer value = list.get(0);
System.out.println(value == null); // true.
```

Tương tự, `for (int x : list)` sẽ unbox từng `Integer` và có thể ném `NullPointerException` khi gặp `null`. `list.stream().mapToInt(Integer::intValue)` cũng không xử lý `null` một cách ngầm định.

**Thiết kế API:** nếu phần tử phải luôn có giá trị nguyên, có thể tránh `null` bằng validation hoặc dùng kiểu chứa primitive. Nếu `null` có nghĩa “chưa biết”, cần nêu rõ chính sách xử lý thay vì unboxing vô điều kiện hoặc tự động thay bằng 0 (vì 0 có thể là dữ liệu hợp lệ).

## 5. Vì sao hai vùng chứa không tương thích trực tiếp?

`int[]` là **một kiểu mảng runtime cụ thể** (array component type là primitive `int`). `List<Integer>` là **một đối tượng implements interface `List`**. Java không có chuyển đổi tự động giữa hai cấu trúc này:

```java
int[] raw = {1, 2, 3};

// List<Integer> list = raw; // Không biên dịch.
// int[] other = new ArrayList<Integer>(); // Không biên dịch.
```

Cả hai đều là object ở mức tham chiếu Java (mảng là object), nhưng **một mảng `int[]` không triển khai `List`**. Ép `(List<Integer>) raw` cũng không phải giải pháp; nó không chuyển dữ liệu thành wrapper và danh sách.

`List<Integer>` không có nghĩa “một mảng int với API khác”. Bất kỳ chuyển đổi thực sự nào phải duyệt phần tử, thực hiện boxing hoặc unboxing, và tạo cấu trúc đích phù hợp.

### Tính đa hình và generic invariance

`List<Integer>` **không phải subtype của** `List<Number>`, mặc dù `Integer extends Number`. Java generics có quy tắc *invariance* cho kiểu như `List<T>`:

```java
java.util.List<Integer> ints = java.util.List.of(1, 2);
// List<Number> numbers = ints; // Không biên dịch.

java.util.List<? extends Number> readOnlyView = ints;
Number x = readOnlyView.get(0);
```

`List<? extends Number>` cho phép đọc các phần tử như `Number`, nhưng không cho phép tùy tiện thêm các `Number` mới, bởi danh sách thật có thể chỉ chấp nhận `Integer`. Đây là vấn đề **generic type safety**, không liên quan đến khả năng ép `int[]` sang List.

Mảng đối tượng có quy tắc *covariance* khác generic và có thể phát sinh `ArrayStoreException` lúc chạy, nhưng `int[]` không phải subtype của `Object[]`: phần tử của `int[]` là primitive, không phải tham chiếu.

## 6. Bẫy phổ biến nhất: `Arrays.asList(int[])`

Nhiều người viết:

```java
int[] raw = {2, 4, 6};
var list = java.util.Arrays.asList(raw);
```

Điều bất ngờ: **`list` không phải `List<Integer>`**. Nó là `List<int[]>` có **đúng một phần tử là nguyên cả mảng `raw`**.

```java
System.out.println(list.size());      // 1.
System.out.println(list.get(0).length); // 3.
```

`Arrays.asList(T... a)` nhận varargs kiểu **tham chiếu** `T`. Với đối số `int[]`, bản thân `int[]` là một object; Java suy ra `T = int[]`. Không có quá trình tự “trải” từng số trong mảng primitive thành `Integer`.

Vì vậy:

```java
// List<Integer> wrong = Arrays.asList(new int[]{1,2,3});
// Lỗi biên dịch do kiểu suy ra không phù hợp.
```

Đừng cố sửa bằng raw type hoặc ép kiểu không an toàn: hậu quả có thể là `ClassCastException` khi lấy phần tử.

### `Arrays.asList(Integer[])` thì khác

```java
Integer[] objects = {2, 4, 6};
java.util.List<Integer> list = java.util.Arrays.asList(objects);

System.out.println(list.size()); // 3.
list.set(0, 10);
System.out.println(objects[0]); // 10.
```

Trong trường hợp này, mảng phần tử tham chiếu `Integer[]` được đưa vào `Arrays.asList`, nên danh sách nhận từng phần tử `Integer`. Danh sách trả về có **kích thước cố định** và **backed by** mảng `objects`:

- `list.set` làm thay đổi vị trí tương ứng trong mảng gốc.
- Thay đổi phần tử của mảng gốc được phản ánh qua danh sách.
- `list.add` và `list.remove` ném `UnsupportedOperationException` vì chúng thay đổi kích thước.

**Không nhầm `Arrays.asList` với `new ArrayList<>()`.** Cùng triển khai interface `List` nhưng có khả năng resize và quan hệ với mảng gốc rất khác nhau.

### `List.of(raw)` cũng không chuyển thành `List<Integer>`

Nếu `raw` là `int[]`, `List.of(raw)` tạo một danh sách chứa **một phần tử là mảng `int[]`**, không phải danh sách boxed integers. Khi cần boxed list, hãy dùng stream `boxed()` hoặc vòng lặp.

## 7. Chuyển từ `int[]` sang `List<Integer>`

### Cách 1 — Vòng lặp và `ArrayList` (danh sách thay đổi được)

```java
int[] raw = {2, 4, 6};
java.util.List<Integer> list = new java.util.ArrayList<>();

for (int value : raw) {
    list.add(value); // Autoboxing int -> Integer.
}
```

Đây là cách dễ hiểu và hoạt động với Java 17+. Nó tạo một danh sách **độc lập** với mảng gốc: thay `list.set(0, 99)` **không** sửa `raw[0]`, và thay `raw[0]` **không** sửa danh sách đã tạo.

Đối với dữ liệu lớn, có thể đặt dung lượng dự kiến:

```java
java.util.ArrayList<Integer> list =
        new java.util.ArrayList<>(raw.length);
for (int value : raw) {
    list.add(value);
}
```

`new ArrayList<>(raw.length)` chỉ chọn **initial capacity**; nó chưa tạo `raw.length` phần tử và `size()` ban đầu vẫn là 0. Không dùng `set(i, value)` ngay nếu chưa có phần tử ở index `i`.

### Cách 2 — `Arrays.stream(...).boxed()`

```java
int[] raw = {2, 4, 6};
java.util.List<Integer> list =
        java.util.Arrays.stream(raw).boxed().toList();
```

`Arrays.stream(raw)` tạo `IntStream` nhận các `int`. `boxed()` chuyển thành `Stream<Integer>`; `toList()` của Stream (có từ Java 16) trả về danh sách **unmodifiable**. Do đó `list.add(8)`/`list.set(0,9)` sẽ ném `UnsupportedOperationException`.

Nếu muốn danh sách sửa được:

```java
java.util.List<Integer> mutable =
        new java.util.ArrayList<>(
                java.util.Arrays.stream(raw).boxed().toList());
mutable.add(8);
```

Đừng giả định một cách chung rằng mọi terminal operation thu danh sách đều trả `ArrayList` có thể sửa. Hợp đồng mutability phải được kiểm tra rõ.

### Dùng `List.of` cho từng số biết trước

```java
java.util.List<Integer> values = java.util.List.of(2, 4, 6);
```

Ở đây từng đối số `int` được boxing để trở thành `Integer`. Kết quả là danh sách không sửa đổi được. Dạng này tiện cho dữ liệu cấu hình ngắn, **không** đồng nghĩa `List.of(new int[]{2,4,6})`.

## 8. Chuyển từ `List<Integer>` về `int[]`

### Cách 1 — Duyệt và unbox thủ công

```java
java.util.List<Integer> list = java.util.List.of(2, 4, 6);
int[] raw = new int[list.size()];

for (int i = 0; i < list.size(); i++) {
    raw[i] = list.get(i); // Integer -> int.
}
```

Với `ArrayList`, `get(i)` thường là O(1), nên cách này phù hợp. Nhưng với một số `List` khác như `LinkedList`, truy cập index `get(i)` nhiều lần có thể tốn O(n²) tổng cộng. Nếu không biết implementation hoặc muốn code dùng chung, nên dùng enhanced-for:

```java
int[] raw = new int[list.size()];
int i = 0;
for (Integer value : list) {
    raw[i++] = value; // Unboxing, có thể ném NPE nếu value là null.
}
```

Nếu List có thể bị sửa đồng thời, việc lấy `size()` trước rồi duyệt không tự bảo đảm snapshot nhất quán. Phải có hợp đồng đồng bộ/snapshot phù hợp với ứng dụng.

### Cách 2 — Stream `mapToInt`

```java
java.util.List<Integer> list = java.util.List.of(2, 4, 6);

int[] raw = list.stream()
        .mapToInt(Integer::intValue)
        .toArray();
```

`mapToInt(Integer::intValue)` thực hiện unboxing/chuyển sang `int`. Nếu danh sách chứa `null`, việc gọi `intValue` trên `null` sẽ ném `NullPointerException`.

Nếu nghiệp vụ muốn bỏ qua `null`, phải viết rõ, chẳng hạn:

```java
int[] nonNullOnly = list.stream()
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue)
        .toArray();
```

Nhưng việc **lọc mất phần tử** có thể thay đổi quan hệ vị trí, nên không áp dụng tùy tiện cho index, mẫu đo hay dữ liệu có thứ tự cố định. Trong nhiều API, từ chối `null` với lỗi rõ ràng là an toàn hơn.

### `toArray` không tạo `int[]`

```java
java.util.List<Integer> list = java.util.List.of(2, 4, 6);
Integer[] boxedArray = list.toArray(new Integer[0]); // Đúng: Integer[].
```

Phương thức `List.toArray` làm việc với **mảng đối tượng**; không có một cách gọi `toArray(new int[0])` để tự unbox toàn bộ. Nếu cần `int[]`, dùng vòng lặp hoặc `mapToInt(...).toArray()`.

## 9. Các lỗi chỉ xuất hiện khi sử dụng API tập hợp

### `remove(1)` không giống `remove(Integer.valueOf(1))`

`List<Integer>` có hai overload:

- `remove(int index)` — xóa theo **chỉ số**.
- `remove(Object o)` — xóa theo **giá trị** bằng quy tắc equality của đối tượng.

```java
java.util.List<Integer> nums =
        new java.util.ArrayList<>(java.util.List.of(10, 20, 30));

nums.remove(1);                  // Xóa phần tử tại index 1 (giá trị 20).
System.out.println(nums);       // [10, 30].

nums.remove(Integer.valueOf(30)); // Xóa theo giá trị 30.
System.out.println(nums);          // [10].
```

`nums.remove(1)` chọn overload `remove(int)`, không phải “xóa số 1”. Nếu cần xóa giá trị 1, viết `nums.remove(Integer.valueOf(1))` hoặc ép rõ `remove((Object) 1)` sau khi cân nhắc boxing.

Với `int[]` không có `remove`; muốn loại phần tử cần tạo mảng mới hoặc tự quản lý một đoạn hợp lệ trong mảng.

### So sánh `Integer` bằng `==` có bẫy tham chiếu

`Integer` là object; hai tham chiếu có thể chỉ vào cùng wrapper hoặc không. Java cache một số giá trị boxed; không dựa vào cache để làm equality:

```java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);

System.out.println(a.equals(b)); // true.
```

Không nên dùng `a == b` để kiểm tra hai wrapper có cùng **giá trị số**. Nó so sánh **định danh đối tượng** khi cả hai toán hạng là `Integer` (và không có unboxing do ngữ cảnh). Với `Integer` có thể `null`, dùng `java.util.Objects.equals(a,b)` khi muốn equality null-safe.

### `Arrays.equals` khác `List.equals`

`int[]` không override `equals` để so sánh từng phần tử; `array1.equals(array2)` là so sánh tham chiếu theo `Object`. Để so nội dung:

```java
int[] x = {1, 2};
int[] y = {1, 2};
System.out.println(java.util.Arrays.equals(x, y)); // true.
```

`List.equals`, ngược lại, được định nghĩa theo thứ tự và equality của các phần tử:

```java
java.util.List<Integer> x = java.util.List.of(1, 2);
java.util.List<Integer> y = new java.util.ArrayList<>(x);
System.out.println(x.equals(y)); // true.
```

`Arrays.equals(int[],int[])` và `List.equals` là hai API khác nhau, không có phép equals mặc định liên kiểu giữa `int[]` và `List<Integer>`.

## 10. Hiệu năng và bộ nhớ: khác biệt có ý nghĩa khi dữ liệu lớn

Với `int[]`, phần tử nguyên thủy dùng **4 byte mỗi giá trị `int`**, chưa tính phần header/căn chỉnh của mảng. Mảng chứa trực tiếp các giá trị, thường có tính locality tốt khi duyệt tuần tự.

Với `ArrayList<Integer>`:

- `ArrayList` thường lưu **tham chiếu** trong mảng `Object[]`, không phải 4 byte `int` trực tiếp trong backing array.
- `Integer` là một object wrapper; không gian lưu, cache và chính sách tối ưu JVM ảnh hưởng chi phí thực tế.
- Boxing/unboxing có thể có chi phí; boxing đôi khi tái sử dụng object cache hoặc được JVM tối ưu hóa, vì thế không nên tuyên bố “mỗi vòng lặp luôn cấp phát một object”.
- `ArrayList` còn quản lý capacity và có thể giữ chỗ chưa dùng.
- `LinkedList<Integer>` thường cần các node liên kết, dẫn đến đặc tính bộ nhớ/cache/truy cập khác `ArrayList`.

Kích thước chính xác trên heap phụ thuộc **JVM, pointer compression, layout, alignment, capacity, cache, vòng đời đối tượng và tối ưu JIT**. Không nên lấy một con số “`List<Integer>` tốn gấp X lần” làm chân lý cho mọi môi trường.

### Với hàng triệu số nguyên

Nếu bạn làm tính toán nguyên đơn giản trên hàng triệu phần tử và **không cần collection API** hay `null`, `int[]` thường có lợi về không gian và truy cập tuần tự. Ví dụ:

```java
long sum = 0L;
for (int value : data) {
    sum += value; // data là int[]; dùng long để giảm rủi ro tràn tổng int.
}
```

Đoạn này dùng `long` cho biến tổng nhưng tổng vẫn có thể tràn `long` nếu dữ liệu đủ lớn; tính toán chuẩn xác tuyệt đối đòi hỏi phân tích giới hạn miền.

Nếu cần thao tác thêm/xóa, thuật toán collection, các giao diện đa hình và sự thuận tiện của Java Collections, `List<Integer>` có thể hợp lý. Nhưng vì `List` là một interface, không thể suy ra hiệu năng chỉ từ tên kiểu khai báo.

### Benchmark đúng cách

Nếu tối ưu hiệu năng thực sự quan trọng, dùng benchmark như **JMH**, có warmup, đo nhiều lượt, kiểm soát kích thước dữ liệu và tránh compiler tối ưu loại bỏ công việc. Không kết luận từ một lần `System.nanoTime()` hoặc từ các vòng lặp quá nhỏ. Khi đo, phân biệt chi phí **khởi tạo/bộ nhớ**, **truy cập**, **boxing**, **GC** và **tần suất thay đổi kích thước**.

## 11. Duyệt, sắp xếp và Stream API

### Vòng lặp

Mảng:

```java
int[] arr = {3, 1, 2};
for (int value : arr) {
    System.out.println(value);
}
```

List:

```java
java.util.List<Integer> nums = java.util.List.of(3, 1, 2);
for (int value : nums) { // Mỗi Integer được unbox thành int.
    System.out.println(value);
}
```

Nếu List có `null`, vòng lặp `for (int value : nums)` có thể ném NPE. Dùng `for (Integer value : nums)` nếu cần xử lý nullable, và định nghĩa rõ ý nghĩa `null`.

### Sắp xếp

```java
int[] arr = {3, 1, 2};
java.util.Arrays.sort(arr); // Thay đổi mảng, kết quả {1,2,3}.
```

```java
java.util.List<Integer> nums =
        new java.util.ArrayList<>(java.util.List.of(3, 1, 2));
nums.sort(Integer::compareTo); // Thay đổi List có hỗ trợ set.
```

Sắp xếp List không sửa đổi được có thể ném `UnsupportedOperationException`. Nếu danh sách chứa `null`, comparator không hỗ trợ `null` có thể gây NPE; lựa chọn chính sách sắp xếp null cần minh bạch. Khi thiết kế comparator số nguyên, dùng `Integer.compare(a,b)` thay vì phép trừ `a-b` có thể overflow.

### Primitive stream và boxed stream

```java
int[] raw = {1, 2, 3};
int sum = java.util.Arrays.stream(raw).sum(); // IntStream.

java.util.List<Integer> list = java.util.List.of(1, 2, 3);
int otherSum = list.stream().mapToInt(Integer::intValue).sum();
```

`IntStream` chuyên cho primitive, giúp tránh một phần boxing trong chuỗi thao tác. `Stream<Integer>` vận hành trên wrapper và cần chuyển sang `IntStream` khi muốn các phép thống kê primitive như `sum`, `average` hoặc `summaryStatistics`.

**Cảnh báo về tràn:** `IntStream.sum()` trả `int`, có thể tràn như phép cộng `int`. Nếu cần tổng `long`, hãy map lên `long` trước:

```java
long total = java.util.Arrays.stream(raw).asLongStream().sum();
```

Với dữ liệu lớn vượt miền `long`, hãy cân nhắc `BigInteger`/kiểm tra tràn.

## 12. Ví dụ Java 17+ chạy độc lập

Lưu tệp `ListVsArrayDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListVsArrayDemo {
    public static void main(String[] args) {
        int[] raw = {2, 4, 6};
        List<Integer> boxed = new ArrayList<>();

        for (int n : raw) {
            boxed.add(n); // Boxing.
        }

        System.out.println("array length = " + raw.length);
        System.out.println("list size = " + boxed.size());

        boxed.set(0, 9);
        System.out.println("array first = " + raw[0]
                + ", list first = " + boxed.get(0));

        boxed.add(8);
        System.out.println("list after add = " + boxed);

        int[] unpacked = boxed.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        System.out.println("unpacked = " + Arrays.toString(unpacked));

        List<Integer> streamList = Arrays.stream(raw).boxed().toList();
        System.out.println("stream boxed = " + streamList);
        try {
            streamList.add(8);
        } catch (UnsupportedOperationException ex) {
            System.out.println("toList is unmodifiable");
        }

        List<int[]> trap = Arrays.asList(raw);
        System.out.println("Arrays.asList(int[]) size = " + trap.size());
        System.out.println("its first element length = "
                + trap.get(0).length);

        Integer[] wrappers = {2, 4, 6};
        List<Integer> fixed = Arrays.asList(wrappers);
        fixed.set(0, 10);
        System.out.println("Integer[] after set = "
                + Arrays.toString(wrappers));
        try {
            fixed.add(8);
        } catch (UnsupportedOperationException ex) {
            System.out.println("Arrays.asList fixed size");
        }

        List<Integer> nullable = new ArrayList<>();
        nullable.add(null);
        try {
            int n = nullable.get(0); // Unboxing null.
            System.out.println(n);
        } catch (NullPointerException ex) {
            System.out.println("null unboxing: NullPointerException");
        }

        int[] defaultInts = new int[1];
        Integer[] defaultWrappers = new Integer[1];
        System.out.println("default int = " + defaultInts[0]);
        System.out.println("default Integer = " + defaultWrappers[0]);

        List<Integer> remove = new ArrayList<>(List.of(10, 20, 30));
        remove.remove(1);
        System.out.println("remove index 1 = " + remove);
        remove.remove(Integer.valueOf(30));
        System.out.println("remove value 30 = " + remove);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 ListVsArrayDemo.java
java ListVsArrayDemo
```

Kết quả stdout:

```text
array length = 3
list size = 3
array first = 2, list first = 9
list after add = [9, 4, 6, 8]
unpacked = [9, 4, 6, 8]
stream boxed = [2, 4, 6]
toList is unmodifiable
Arrays.asList(int[]) size = 1
its first element length = 3
Integer[] after set = [10, 4, 6]
Arrays.asList fixed size
null unboxing: NullPointerException
default int = 0
default Integer = null
remove index 1 = [10, 30]
remove value 30 = [10]
```

**Giải thích:** việc gọi `set/add` trên `boxed` không sửa `raw` vì đó là hai vùng chứa độc lập. `Arrays.asList(raw)` cho `List<int[]>` có size 1. `Arrays.asList(wrappers)` cho danh sách cố định kích thước liên kết với mảng `Integer[]`. `Stream.toList` trả danh sách không sửa đổi được; unbox một `null` gây NPE; overload `remove(int)` và `remove(Object)` thực hiện hai thao tác khác nhau.

## 13. Nên chọn `int[]` hay `List<Integer>`?

| Nhu cầu | Lựa chọn ưu tiên | Lý do |
| --- | --- | --- |
| Dãy số nguyên kích thước cố định, xử lý số lượng lớn | `int[]` | Lưu primitive trực tiếp, thường có locality tốt và ít overhead |
| API Java yêu cầu `List<Integer>` | `List<Integer>` | Tương thích với Collection/Stream/thuật toán generic |
| Thêm/xóa linh hoạt | `ArrayList<Integer>` | Có khả năng resize; chú ý boxing/memory |
| Chỉ cần danh sách hằng ít phần tử | `List.of(1,2,3)` | Dễ đọc, không sửa đổi được, không cho `null` |
| Cần liên kết với một `Integer[]` có sẵn mà không đổi size | `Arrays.asList(integerArray)` | Backed by array; `set` hai chiều |
| Cần một `int[]` từ List để gọi API primitive | `mapToInt(Integer::intValue).toArray()` | Chuyển cấu trúc và unbox rõ ràng |
| Cần List từ mảng primitive | `Arrays.stream(arr).boxed()` hoặc vòng lặp | Boxing từng phần tử; chọn mutability rõ |
| Cần nhận biết “không có giá trị” bằng `null` | `List<Integer>`/`Integer[]` nếu hợp đồng cho phép | Không unbox trước khi xử lý `null` |
| Không được có `null` và dữ liệu luôn là số nguyên | `int[]` hoặc validation cho List | Loại nguy cơ null element |
| Yêu cầu độ chính xác từng số và hiệu năng cao | `int[]`/`long[]` tùy miền | Không dùng số thực hoặc wrapper nếu không cần |
| Collection cần equality/tra cứu/xóa phần tử linh hoạt | `List<Integer>` | Có API tiện dụng; phải hiểu `remove` và equality |

### Checklist trước khi chuyển qua lại

1. **Loại phần tử thực sự là gì?** `int`, `Integer`, `int[]` hay `Integer[]`? Đừng để `Arrays.asList` suy ra sai kiểu.
2. **Có cần thay đổi số phần tử không?** `int[]` cố định; `ArrayList` resize; `List.of` và `Stream.toList` không sửa đổi.
3. **Có cho phép `null` không?** Nếu có, chọn chính sách trước khi unbox; nếu không, validate hoặc chọn primitive.
4. **Có cần đồng bộ mảng và list không?** `Arrays.asList(Integer[])` backed by array; stream boxing tạo vùng chứa độc lập.
5. **Đã chọn API theo kiểu container chưa?** Mảng dùng `length`/`[]`; list dùng `size()`/`get`/`set`.
6. **Có rủi ro mất hiệu năng/bộ nhớ do boxing không?** Đo trên dữ liệu thật, cân nhắc `int[]` và `IntStream`.
7. **Khi so sánh/xóa, đang so nội dung hay tham chiếu/index?** `Arrays.equals` khác `List.equals`, `remove(int)` khác `remove(Object)`.
8. **Tổng số nguyên có thể vượt `int` không?** `IntStream.sum()` vẫn cho `int`; dùng `long`/chính sách checked theo nhu cầu.
9. **Có phụ thuộc implementation List không?** `get(i)` của `LinkedList` có tính chất khác `ArrayList`; đừng giả định mọi List đều random-access nhanh.
10. **Có yêu cầu tương thích Java cụ thể không?** `Stream.toList` từ Java 16; ví dụ chính biên dịch ở Java 17+.

## 14. Những lỗi hay gặp

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Viết `List<int>` | Không biên dịch | `List<Integer>` |
| Coi `int[]` là `List<Integer>` và cast | Không tương thích kiểu | Chuyển bằng vòng lặp/stream boxing |
| `Arrays.asList(new int[]{1,2,3})` rồi kỳ vọng list có ba số | Thực tế `List<int[]>` size 1 | `Arrays.stream(raw).boxed().toList()` |
| Cho rằng `Arrays.asList(Integer[])` đổi size được | `add/remove` ném exception | Sao chép vào `new ArrayList<>(...)` |
| Cho rằng `Stream.toList()` cho `ArrayList` mutable | Danh sách trả về unmodifiable | Bọc bằng `new ArrayList<>(...)` |
| Gọi `list.get(i)` mà không xét `null` | Unboxing có thể ném NPE | Kiểm tra null/validation |
| Tưởng `new ArrayList<>(capacity)` có sẵn `capacity` phần tử | `size()` vẫn 0 | Dùng `add` để điền |
| Dùng `list.remove(1)` để xóa giá trị số 1 | Java xóa index 1 | `remove(Integer.valueOf(1))` |
| Dùng `Integer == Integer` để so số | Có thể so identity wrapper, kết quả phụ thuộc object | `equals`/`Objects.equals` hoặc unbox có chủ đích |
| Dùng `array1.equals(array2)` để so nội dung | So identity mảng | `Arrays.equals(array1,array2)` |
| Giả định List phải có lưu trữ liên tục | `LinkedList` và implementation khác | Chọn implementation phù hợp |
| Lấy `List.toArray(new Integer[0])` làm `int[]` | Trả `Integer[]` | `mapToInt(...).toArray()` |
| Quên `IntStream.sum()` vẫn là `int` | Có thể overflow | `asLongStream().sum()` và kiểm tra miền |
| Chỉ đo benchmark bằng một lần chạy | Warmup/GC/JIT làm sai kết luận | Benchmark có kiểm soát như JMH |
| Coi nullable `Integer` bằng giá trị 0 | Mất khác biệt “thiếu” và 0 | Thiết kế dữ liệu rõ ràng |

## 15. Câu hỏi tự kiểm tra

1. `List<Integer>` có lưu trực tiếp các ô `int` 32 bit như `int[]` không? Vì sao?
2. Tại sao `List<int>` không biên dịch nhưng `List<Integer>` hợp lệ?
3. Viết ba cách đọc phần tử thứ hai từ `int[]`, `Integer[]` và `List<Integer>`.
4. Vì sao `Arrays.asList(intArray)` có size 1? Kiểu phần tử của list đó là gì?
5. `Arrays.asList(integerArray)` cho loại list nào? Vì sao `set` hợp lệ nhưng `add` không?
6. Nếu dùng `Arrays.stream(raw).boxed().toList()` rồi gọi `set`, điều gì xảy ra trên Java 17?
7. Có thể có `null` trong `int[]` không? Còn trong `ArrayList<Integer>`? Khi nào unboxing gây NPE?
8. Vì sao `new ArrayList<Integer>(100)` có `size() == 0`?
9. `List<Integer>.remove(1)` khác `remove(Integer.valueOf(1))` ở điểm nào?
10. Vì sao `Integer.valueOf(1000) == Integer.valueOf(1000)` không phải cách đáng tin để so giá trị?
11. Làm sao chuyển `List<Integer>` thành `int[]` mà không giữ các wrapper?
12. `Arrays.equals(int[],int[])` giải quyết bài toán gì mà `intArray.equals(otherIntArray)` không giải quyết?
13. Khi nào nên chọn `int[]` thay vì `ArrayList<Integer>` cho hàng triệu phần tử?
14. Nếu có `List<? extends Number>`, bạn có thể thêm mọi `Number` vào đó không? Vì sao?
15. Vì sao dùng `List<Integer>` không bảo đảm có thể gọi `get(i)` trong O(1)?

## 16. Tài liệu chính thức

- [Java SE 17 — `List<E>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): hợp đồng `get`, `set`, `add`, `remove`, `equals` và khả năng không hỗ trợ sửa đổi.
- [Java SE 17 — `ArrayList<E>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): danh sách có thể resize và cơ chế truy cập.
- [Java SE 17 — `Arrays.asList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)): danh sách kích thước cố định backed by array.
- [Java SE 17 — `Arrays.stream(int[])`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#stream(int[])): tạo `IntStream` từ mảng primitive.
- [Java SE 17 — `IntStream.boxed()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html#boxed()): boxing stream nguyên thủy sang `Stream<Integer>`.
- [Java SE 17 — `Stream.toList()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()): danh sách không sửa đổi được trả về từ stream.
- [Java Language Specification `5.1.7–5.1.8](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html): boxing và unboxing conversions trong Java.

**Kết luận:** `int[]` chứa trực tiếp các giá trị nguyên thủy có độ dài cố định; `List<Integer>` chứa các **tham chiếu `Integer`** với hành vi cấu trúc phụ thuộc implementation. Hai kiểu **không hoán đổi tự động** và cần boxing/unboxing khi chuyển đổi. Hãy chọn dựa trên nhu cầu API, thay đổi kích thước, `null`, hiệu năng và kiểu dữ liệu; đặc biệt tránh bẫy `Arrays.asList(int[])`, `remove` overload và các danh sách unmodifiable.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-007">Chi phí boxing trong vòng lặp và cấu trúc dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí boxing trong vòng lặp và cấu trúc dữ liệu Java

## 1. Boxing và unboxing là gì?

Trong Java, `int`, `long`, `double` và những kiểu primitive khác giữ giá trị theo các kiểu dữ liệu cơ bản của ngôn ngữ. Những lớp như `Integer`, `Long` và `Double` là **wrapper**: đối tượng tham chiếu biểu diễn giá trị primitive tương ứng.

**Boxing** là chuyển từ primitive sang wrapper. **Unboxing** là lấy giá trị primitive từ wrapper. Java có thể thực hiện chúng tự động khi kiểu/ngữ cảnh cho phép:

~~~java
int primitive = 42;
Integer boxed = primitive;  // autoboxing: int -> Integer
int again = boxed;          // unboxing: Integer -> int
~~~

Đây là đoạn minh họa, chưa phải chương trình độc lập. Về mặt ngữ nghĩa, có thể hình dung:

~~~java
Integer boxed = Integer.valueOf(primitive);
int again = boxed.intValue();
~~~

Điểm chính: **box/unbox là các chuyển đổi kiểu được trình biên dịch diễn giải**, không phải “miễn phí”. Chúng có thể kèm gọi phương thức, tham chiếu đối tượng, xử lý `null` và trong một số điều kiện **có cấp phát đối tượng**.

Khi xuất hiện một hai lần trong mã bình thường, chi phí thường không đáng kể. Nhưng trong **vòng lặp chạy hàng triệu lần**, **collection chứa hàng triệu phần tử** hoặc **đường xử lý hiệu năng nhạy cảm**, số lượng chuyển đổi và đối tượng có thể tác động lên:

- Bộ nhớ dùng để lưu dữ liệu và metadata đối tượng.
- Số lần cấp phát, thu gom rác (*garbage collection* – GC) và áp lực lên heap.
- Tính cục bộ bộ nhớ (*cache locality*), số lần theo tham chiếu và tốc độ truy cập.
- CPU của những phép kiểm tra `null`, chuyển kiểu và mã tạo wrapper nếu chúng không được tối ưu.
- Độ trễ đuôi (*tail latency*) khi GC hoặc cache miss xuất hiện.

Bài này chỉ rõ **nơi boxing ẩn xảy ra**, phân biệt **chi phí có thể phát sinh** với **chi phí chắc chắn xảy ra**, và hướng chọn cấu trúc dữ liệu theo yêu cầu.

## 2. Primitive và wrapper: giống giá trị, khác cách lưu trữ

| Primitive | Wrapper | Ví dụ autoboxing | Có thể chứa `null`? |
|---|---|---|---|
| `byte` | `Byte` | `Byte b = (byte) 5;` | Có |
| `short` | `Short` | `Short s = (short) 5;` | Có |
| `int` | `Integer` | `Integer i = 5;` | Có |
| `long` | `Long` | `Long l = 5L;` | Có |
| `float` | `Float` | `Float f = 1.5f;` | Có |
| `double` | `Double` | `Double d = 1.5;` | Có |
| `char` | `Character` | `Character c = 'A';` | Có |
| `boolean` | `Boolean` | `Boolean flag = true;` | Có |

Primitive không mang giá trị `null`. Wrapper là reference type, nên biến wrapper có thể là `null`; **unboxing `null` ném `NullPointerException`**.

Các wrapper không hoán đổi vai trò với nhau tùy ý. Ví dụ `Integer` là wrapper của `int`, không phải primitive `int`. Dù `Integer` là một `Number`, việc cần đối tượng và việc thực hiện số học là hai cơ chế khác nhau.

### Boxing không đồng nghĩa tạo đối tượng mới ở mọi lần

`Integer.valueOf(int)` sử dụng **caching** đối với ít nhất miền `-128` đến `127`. Cùng giá trị trong miền này có thể được biểu diễn bằng cùng đối tượng wrapper. Cấu hình/triển khai có thể cache thêm giá trị ngoài miền bắt buộc.

Hơn nữa, JVM/JIT có thể tối ưu những đối tượng tạm **không thoát** khỏi phạm vi phân tích (*escape analysis*, scalar replacement) và loại bỏ một số cấp phát vật lý. Vì vậy, phát biểu **“mỗi lần autoboxing chắc chắn cấp phát một đối tượng” là sai**.

Tuy nhiên, cũng không thể kết luận **“JIT sẽ xóa hết mọi boxing”**: khi cần giữ đối tượng `Integer` trong collection hoặc truyền nó đến nơi không thể loại bỏ tham chiếu, có thể phải lưu/cấp phát đối tượng thật. Tối ưu phụ thuộc mã, JVM, dữ liệu và quá trình warm-up.

## 3. Boxing ẩn trong vòng lặp

### 3.1. Biến tích lũy dạng primitive

~~~java
int sum = 0;
for (int i = 0; i < 5; i++) {
    sum += i;
}
~~~

`sum` và `i` đều có kiểu `int`. Vòng lặp thao tác trên primitive; không có boxing do kiểu của các biến này.

### 3.2. Biến tích lũy dạng wrapper

~~~java
Integer sum = 0;
for (int i = 0; i < 5; i++) {
    sum += i;
}
~~~

Hai đoạn là **mã minh họa**, không phải class độc lập. Về ngữ nghĩa, `sum += i` cần:

1. Lấy giá trị primitive từ `sum` (*unbox*).
2. Cộng với `i` kiểu `int`.
3. Chuyển kết quả `int` trở lại `Integer` (*box*) để gán cho biến `sum`.

Có thể hình dung gần tương đương:

~~~java
sum = Integer.valueOf(sum.intValue() + i);
~~~

**Cảnh báo:** đoạn diễn giải này không nhằm mô tả chính xác mọi bước CPU của mã máy do JIT sinh ra. Nó mô tả **nghĩa ngôn ngữ** và nơi việc box/unbox xuất hiện. JVM có thể tối ưu phần nào, nhưng viết tích lũy bằng `Integer` không đem lại lợi ích tự động so với `int` khi không cần đối tượng.

Nếu `sum == null` ở đầu phép cộng, chương trình sẽ ném `NullPointerException` tại bước unbox.

### Ví dụ Java 17+ chạy độc lập

Tạo tệp `BoxingLoopDemo.java`:

~~~java
public class BoxingLoopDemo {
    static int primitiveSum(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    static int boxedSum(int limit) {
        Integer sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i; // unboxing, phép cộng int, autoboxing
        }
        return sum; // unboxing khi trả về int
    }

    public static void main(String[] args) {
        System.out.println("primitiveSum=" + primitiveSum(5));
        System.out.println("boxedSum=" + boxedSum(5));

        int primitive = 42;
        Integer boxed = primitive;
        int recovered = boxed;
        System.out.println("recovered=" + recovered);

        Integer missing = null;
        try {
            int value = missing;
            System.out.println(value);
        } catch (NullPointerException ex) {
            System.out.println("nullUnboxing=NPE");
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac BoxingLoopDemo.java
java BoxingLoopDemo
~~~

Đầu ra:

~~~text
primitiveSum=10
boxedSum=10
recovered=42
nullUnboxing=NPE
~~~

Hai vòng lặp tạo cùng **giá trị số**. Khác nhau nằm ở các chuyển đổi kiểu và khả năng tạo đối tượng mà chương trình yêu cầu; **đầu ra không phải thước đo hiệu năng**. Không nên đưa ra số mili-giây cụ thể nếu chưa đo trên môi trường thật.

## 4. `int[]` và `List<Integer>` khác nhau ở đâu?

Java có hai lựa chọn phổ biến để lưu nhiều số nguyên:

- **`int[]`**: một mảng primitive chứa các giá trị `int` trực tiếp.
- **`List<Integer>`**: một collection chứa các **tham chiếu `Integer`**; với `ArrayList<Integer>`, cấu trúc thường dùng một mảng tham chiếu bên trong và những đối tượng wrapper được tham chiếu.

So sánh:

| Khía cạnh | `int[]` | `ArrayList<Integer>` |
|---|---|---|
| Phần tử | Giá trị `int` | Tham chiếu tới `Integer` |
| Có boxing khi thêm số primitive? | Không | Có thể có, ví dụ `add(42)` |
| Truy cập trả về | `int` | `Integer`; dùng số học thường phải unbox |
| Hỗ trợ `null` như phần tử? | Không | Có |
| Kích thước | Cố định sau khi tạo | Tăng/giảm động |
| Tích hợp API `List`/generics | Không trực tiếp | Có |
| Chi phí bộ nhớ mỗi phần tử | Giá trị primitive liên tiếp, ít gián tiếp | Tham chiếu và có thể thêm đối tượng wrapper/overhead |
| Truy cập ngẫu nhiên | `O(1)` | `O(1)` đối với `ArrayList` |
| Tính cục bộ bộ nhớ | Thường tốt với duyệt tuần tự | Thêm bước theo tham chiếu tới wrapper |

**Đừng suy ra một con số byte tuyệt đối cho mọi JVM:** kích thước header đối tượng, alignment, *compressed ordinary object pointers*, heap và JDK ảnh hưởng footprint. Ví dụ, `int` có giá trị 32 bit, nhưng một `Integer` object trong heap không chỉ có 4 byte vì còn metadata/header và căn chỉnh; `ArrayList` cũng có vùng tham chiếu, capacity dư, và đối tượng danh sách.

Đối với một triệu số `int`, mảng primitive thường tiết kiệm bộ nhớ hơn đáng kể so với một triệu `Integer` không được cache nằm trong `ArrayList`. Nhưng **đo lường thực tế** vẫn cần thiết nếu phải công bố phần trăm tiết kiệm hoặc thời gian chạy.

### Chi phí boxing khi thêm và unboxing khi đọc

~~~java
List<Integer> values = new ArrayList<>();
for (int i = 0; i < 1000; i++) {
    values.add(i);  // int -> Integer
}

long sum = 0;
for (int value : values) { // mỗi Integer được unbox thành int
    sum += value;
}
~~~

Đây là đoạn minh họa; cần `import java.util.List;` và `import java.util.ArrayList;` khi đặt trong chương trình độc lập.

Vòng `for-each` viết `int value` giúp mã ngắn, nhưng không có nghĩa collection đã chứa primitive. Khi duyệt `List<Integer>`, từng phần tử phải được chuyển từ `Integer` sang `int`; phần tử `null` sẽ ném NPE ở thời điểm đó.

## 5. Chương trình Java 17+: so sánh cấu trúc dữ liệu bằng kết quả xác định

Tạo tệp `PrimitiveVsBoxedCollectionsDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrimitiveVsBoxedCollectionsDemo {
    public static void main(String[] args) {
        int[] array = {10, 20, 30};
        List<Integer> list = new ArrayList<>();

        for (int value : array) {
            list.add(value); // boxing từng phần tử
        }

        int arraySum = 0;
        for (int value : array) {
            arraySum += value;
        }

        int listSum = 0;
        for (int value : list) { // unboxing
            listSum += value;
        }

        System.out.println("arraySum=" + arraySum);
        System.out.println("listSum=" + listSum);
        System.out.println("size=" + list.size());

        list.set(0, 15);
        System.out.println("arrayFirst=" + array[0]);
        System.out.println("listFirst=" + list.get(0));

        System.out.println("arrayStreamSum="
                + Arrays.stream(array).sum());
        System.out.println("listStreamSum="
                + list.stream().mapToInt(Integer::intValue).sum());
    }
}
~~~

~~~bash
javac PrimitiveVsBoxedCollectionsDemo.java
java PrimitiveVsBoxedCollectionsDemo
~~~

Đầu ra:

~~~text
arraySum=60
listSum=60
size=3
arrayFirst=10
listFirst=15
arrayStreamSum=60
listStreamSum=65
~~~

Khi chỉnh `list.set(0, 15)`, phần tử đầu mảng vẫn là 10 vì **mảng primitive và danh sách wrapper là hai cấu trúc riêng**. Dòng `arrayStreamSum` đọc trực tiếp `int[]`, còn `listStreamSum` dùng `Integer::intValue` để biến `Stream<Integer>` thành `IntStream`.

Ví dụ này so sánh **ngữ nghĩa và vị trí chuyển đổi kiểu**, **không phải benchmark**.

## 6. Mảng primitive, collection chuẩn và collection primitive-specialized

Khi cần lưu trữ khối lượng lớn dữ liệu số, có thể cân nhắc:

1. **`int[]`, `long[]`, `double[]`**: phù hợp với kích thước xác định hoặc có thể quản lý việc mở rộng mảng. Không boxing từng phần tử và có locality tốt khi duyệt tuần tự.
2. **`ArrayList<Integer>`, `HashMap<Integer, Long>`**: phù hợp khi cần API collection chuẩn, generic, kích thước linh hoạt, tích hợp thư viện, hoặc có thể cần `null`. Đổi lại, có khả năng overhead tham chiếu và wrapper.
3. **`IntStream`, `LongStream`, `DoubleStream`**: đường xử lý stream chuyên biệt cho primitive; giúp tránh một số boxing trong pipeline.
4. **Cấu trúc dữ liệu chuyên biệt cho primitive từ thư viện ngoài**: ví dụ danh sách/map có khóa và giá trị primitive. Có thể giảm overhead trong một số workload, nhưng thêm dependency, API và yêu cầu kiểm tra benchmark/độ ổn định.
5. **Thiết kế theo miền dữ liệu**: `BitSet` phù hợp với tập bit/boolean dày đặc; mảng đa chiều hoặc buffer cũng có thể phù hợp với xử lý số lớn. Chọn theo cách truy cập, không chỉ theo mong muốn “tránh boxing”.

Không nên tự động thay mọi `List<Integer>` thành `int[]`. API dạng `List` có lợi thế về thao tác thêm/xóa, cộng tác giữa nhiều thư viện, cấu trúc trừu tượng và mã dễ bảo trì. **Tối ưu dựa trên profile** hiệu quả hơn tối ưu dựa trên một giả định rằng wrapper luôn quá chậm.

### `HashMap<Integer, Integer>` trong vòng lặp nóng

Ví dụ:

~~~java
Map<Integer, Integer> frequencies = new HashMap<>();
for (int number : input) {
    frequencies.merge(number, 1, Integer::sum);
}
~~~

Đây là **đoạn minh họa**, giả sử đã khai báo `int[] input` và import `Map`, `HashMap`. Mã sử dụng `Integer` làm khóa và giá trị; primitive `number` được boxing khi làm key, giá trị đếm là wrapper, và callback `Integer::sum` xử lý các giá trị sau khi unbox thích hợp. Với dữ liệu nhiều khóa và cập nhật liên tục, chi phí hash table và đối tượng có thể đáng kể.

Nhưng thay thành primitive map chỉ có ích nếu workload thực sự bị ảnh hưởng bởi **GC/bộ nhớ/CPU** từ cấu trúc hiện tại và thư viện mới đáp ứng đúng quy tắc khóa, concurrency và hành vi của ứng dụng.

## 7. Stream: primitive stream và `Stream<Integer>`

Java Stream API có các specialization:

| Dạng | Phần tử logic | Phép toán thường dùng |
|---|---|---|
| `Stream<Integer>` | `Integer` (wrapper) | `map`, `filter`, `collect` |
| `IntStream` | `int` | `map`, `filter`, `sum`, `average` |
| `LongStream` | `long` | `map`, `sum`, `summaryStatistics` |
| `DoubleStream` | `double` | `map`, `sum`, `average` |

Ví dụ:

~~~java
int sumPrimitive = java.util.stream.IntStream.rangeClosed(1, 5).sum();
int sumBoxed = java.util.stream.Stream.of(1, 2, 3, 4, 5)
        .mapToInt(Integer::intValue)
        .sum();
~~~

Đây là đoạn minh họa. `Stream.of(1, 2, 3, 4, 5)` tạo một stream các đối tượng `Integer` vì generic type của `Stream` là reference type; còn `IntStream.rangeClosed` làm việc trực tiếp với `int`.

### Những điểm boxing thường lộ ra trong pipeline

- `IntStream.boxed()` biến mỗi phần tử `int` thành `Integer` để trả `Stream<Integer>`.
- `Stream<Integer>.mapToInt(Integer::intValue)` unbox các phần tử thành `int`.
- `Stream<Integer>.reduce(0, Integer::sum)` có thể cần xử lý các giá trị box/unbox ở mỗi bước giảm; không mặc nhiên nhanh bằng `IntStream.sum()`.
- `IntStream.sum()` trả `int`; nếu tổng toán học vượt miền `int` thì **vẫn có thể tràn số**. Tránh boxing **không đồng nghĩa tránh overflow**.
- Nếu cần tổng `long` từ các số `int`, hãy chuyển sang `LongStream` trước khi cộng: `intStream.asLongStream().sum()`.

### Ví dụ Java 17+ chạy độc lập về stream

Tạo tệp `PrimitiveStreamsDemo.java`:

~~~java
import java.util.List;
import java.util.stream.IntStream;

public class PrimitiveStreamsDemo {
    public static void main(String[] args) {
        int sum = IntStream.rangeClosed(1, 5).sum();

        List<Integer> boxed = IntStream.rangeClosed(1, 3)
                .boxed()
                .toList();

        int unboxedSum = boxed.stream()
                .mapToInt(Integer::intValue)
                .sum();

        int overflowed = IntStream.of(Integer.MAX_VALUE, 1).sum();
        long widened = IntStream.of(Integer.MAX_VALUE, 1)
                .asLongStream()
                .sum();

        System.out.println("primitiveSum=" + sum);
        System.out.println("boxedList=" + boxed);
        System.out.println("unboxedSum=" + unboxedSum);
        System.out.println("intOverflow=" + overflowed);
        System.out.println("longSum=" + widened);
    }
}
~~~

~~~bash
javac PrimitiveStreamsDemo.java
java PrimitiveStreamsDemo
~~~

Đầu ra:

~~~text
primitiveSum=15
boxedList=[1, 2, 3]
unboxedSum=6
intOverflow=-2147483648
longSum=2147483648
~~~

`Stream.toList()` (Java 16+) trả danh sách không thể sửa bằng các thao tác cập nhật thông thường. Trong ví dụ này, nó lưu `Integer` do trước đó đã gọi `boxed()`. Dòng `intOverflow` là lời nhắc rằng **chọn primitive stream chỉ giải quyết vấn đề boxing**, còn giới hạn miền số vẫn phải tự kiểm soát.

## 8. Autoboxing ở ranh giới phương thức và API generic

Một API nhận `Integer` hoặc `Number` có thể gây boxing ở nơi gọi, mặc dù biến của người gọi là `int`:

~~~java
static void accept(Number value) {
    System.out.println(value.intValue());
}

int count = 10;
accept(count); // int -> Integer -> Number
~~~

Đây là các đoạn minh họa cần đặt trong class. Từ góc độ kiểu tĩnh, `accept(count)` cần boxing một `int` thành `Integer` rồi chuyển tham chiếu tới `Number`.

Ngược lại:

~~~java
static int twice(int value) {
    return value * 2;
}

Integer number = Integer.valueOf(10);
int doubled = twice(number); // unbox Integer -> int
~~~

Một codebase có hàng nghìn dòng có thể chứa nhiều lần box/unbox ở **ranh giới method, generics và collection**, ngay cả khi không có từ khóa `new` hay lời gọi `Integer.valueOf` xuất hiện trực tiếp.

### Overload và unboxing

Nếu có các overload khác nhau:

~~~java
static void use(int value) { /* ... */ }
static void use(Integer value) { /* ... */ }
~~~

thì lựa chọn overload phụ thuộc vào kiểu biểu thức và các pha xác định overload của Java. Không nên dùng boxing như một “thao tác vô hình” để giả định mọi lời gọi đều chọn overload có cùng chi phí. Khi hiệu năng thật sự quan trọng, chọn API primitive rõ ràng có thể giúp tránh nhu cầu chuyển qua wrapper.

## 9. Cạm bẫy `null`, đồng nhất tham chiếu và kiểm tra bằng `==`

### `null` và unboxing

~~~java
Integer count = null;
// int value = count;     // Ném NullPointerException khi chạy
// int next = count + 1;  // Cũng ném NPE do cần unbox
~~~

Đây là mã minh họa, các dòng nguy hiểm đã được comment. Một `List<Integer>` có thể chứa `null`; phép `for (int x : list)` sẽ NPE khi gặp phần tử đó. Primitive array không có trạng thái `null` cho từng phần tử; mọi vị trí `int[]` có giá trị nguyên (mặc định 0 nếu chưa gán).

Nếu `null` có ý nghĩa như “chưa biết”, hãy quyết định chính sách xử lý rõ ràng, thay vì tự động đổi `null` thành 0 mà không kiểm tra yêu cầu.

### `==` với wrapper không có nghĩa so sánh giá trị tổng quát

Khi cả hai toán hạng đều là wrapper `Integer`, `a == b` so sánh **tham chiếu**, không phải luôn so sánh giá trị số. Một số đối tượng `Integer` được cache (ít nhất `-128..127`) nên `==` đôi khi cho `true`, gây nhầm lẫn; ngoài miền đó không nên dựa vào kết quả đồng nhất tham chiếu.

Ví dụ:

~~~java
Integer left = 1000;
Integer right = 1000;

boolean sameValue = left.equals(right);    // true
boolean samePrimitive = left.intValue() == right.intValue(); // true
// left == right so sánh tham chiếu, không dùng làm phép so sánh số.
~~~

Nếu một toán hạng kiểu `int` còn toán hạng kia kiểu `Integer`, phép `==` số học có thể thực hiện unboxing để so sánh primitive — và có thể NPE nếu wrapper `null`. Vì vậy, **kết quả `==` còn phụ thuộc kiểu tĩnh của cả hai toán hạng**.

Dùng `Objects.equals(a, b)` khi cần so sánh nội dung wrapper có thể `null`, hoặc unbox sau khi kiểm tra `null` nếu thật sự cần so sánh primitive.

### Ví dụ Java 17+ chạy độc lập

Tạo tệp `WrapperPitfallsDemo.java`:

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WrapperPitfallsDemo {
    public static void main(String[] args) {
        Integer a = 1000;
        Integer b = 1000;

        System.out.println("equals=" + a.equals(b));
        System.out.println("primitiveEquals="
                + (a.intValue() == b.intValue()));
        System.out.println("objectsEquals=" + Objects.equals(a, b));

        Map<Integer, String> names = new HashMap<>();
        names.put(a, "ok");
        System.out.println("mapLookup=" + names.get(b));

        Integer missing = null;
        try {
            int value = missing + 1;
            System.out.println(value);
        } catch (NullPointerException ex) {
            System.out.println("nullUnboxing=NPE");
        }

        int fallback = missing == null ? 0 : missing;
        System.out.println("fallback=" + fallback);
    }
}
~~~

~~~bash
javac WrapperPitfallsDemo.java
java WrapperPitfallsDemo
~~~

Đầu ra:

~~~text
equals=true
primitiveEquals=true
objectsEquals=true
mapLookup=ok
nullUnboxing=NPE
fallback=0
~~~

`HashMap` dùng `equals` và `hashCode` của key, không dùng phép `==` thuần túy như một so sánh nội dung số học. Bởi vậy, hai `Integer` có cùng giá trị có thể tra cứu cùng khóa mặc dù không cần là cùng đối tượng.

Ở biểu thức `missing == null ? 0 : missing`, khi `missing` bằng `null` thì nhánh `0` được chọn, nên nhánh cần unbox `missing` không chạy. Đây là một cách tạo giá trị mặc định rõ ràng, nhưng chỉ hợp lý nếu chính sách nghiệp vụ cho phép coi “thiếu” là 0.

## 10. Chi phí CPU, allocation và GC không giống nhau

Đừng gom mọi ảnh hưởng của boxing thành một “thời gian boxing” duy nhất:

1. **Chuyển kiểu theo nghĩa ngôn ngữ:** compiler phát sinh cách chuyển `int ↔ Integer`. Có thể gồm phương thức wrapper hoặc primitive extraction.
2. **Cấp phát vật lý:** một boxing cần đối tượng mới **khi không được cache hoặc loại bỏ nhờ tối ưu hóa**. Không có đảm bảo mỗi boxing tương ứng một allocation.
3. **Giữ đối tượng sống:** nếu collection giữ tham chiếu đến wrapper, các đối tượng ấy có thể tồn tại lâu và làm tăng lượng live objects phải xử lý.
4. **Chi phí bộ nhớ/CPU khi truy cập:** array primitive đọc giá trị trực tiếp; collection wrapper có thể phải đọc tham chiếu rồi đọc object, ảnh hưởng locality/cache.
5. **GC:** nhiều object cấp phát và sống lâu có thể tăng GC work. Ảnh hưởng cụ thể tùy collector, heap, tốc độ cấp phát và profile ứng dụng.

**Ví dụ vòng lặp accumulator `Integer`:** JIT đôi khi có thể tối ưu mạnh, nên benchmark đơn giản có thể cho kết quả gần primitive. **Ví dụ `ArrayList<Integer>` chứa hàng triệu giá trị khác nhau ngoài cache:** dữ liệu cần tồn tại dưới dạng object và tham chiếu, khó đạt footprint như `int[]`. Vì vậy, bài toán **lưu trữ dữ liệu** và bài toán **tính toán tạm thời** cần đánh giá riêng.

Chi phí phát sinh cũng phụ thuộc boxing của **`Long`/`Double`**, thường ảnh hưởng hơn khi dữ liệu lớn hoặc đường xử lý chứa nhiều bản sao.

## 11. Benchmark đúng: dùng workload thực và JMH khi cần microbenchmark

Không nên kết luận hiệu năng từ một đoạn ngắn như:

~~~java
long start = System.nanoTime();
// một vài phép cộng rất nhanh
long elapsed = System.nanoTime() - start;
~~~

Đây là đoạn minh họa **không phải phép benchmark đáng tin**. JVM có thể chưa warm-up, JIT có thể loại bỏ phép tính không dùng tới, GC ngẫu nhiên, và chi phí đo thời gian có thể lớn so với đoạn mã cần đo.

Với microbenchmark Java, có thể dùng **Java Microbenchmark Harness (JMH)** và thiết kế bài đo có:

- **Warm-up** đủ để JVM biên dịch/ổn định mã nóng.
- Nhiều **measurement iterations** và nhiều **forks** nhằm giảm ảnh hưởng trạng thái tiến trình.
- Input và kích thước dữ liệu phản ánh trường hợp thật: ít giá trị cache, nhiều giá trị ngoài cache, kích thước list/mảng, cách duyệt ngẫu nhiên hay tuần tự.
- Kết quả được **sử dụng thật hoặc truyền cho cơ chế tiêu thụ của JMH** để không bị dead-code elimination.
- Môi trường có kiểm soát tương đối về CPU, GC, heap và tải nền.
- Cả **throughput/latency** lẫn **allocation rate, GC, memory footprint** khi mục tiêu là giảm boxing.

JMH là thư viện/tool ngoài JDK chuẩn, nên muốn dùng phải bổ sung dependency và thiết lập dự án phù hợp; **không** thể giả định có sẵn chỉ vì đang dùng Java 17.

### Chọn baseline thực tế

Ví dụ cần đo tổng một triệu số:

1. Tổng theo `int[]`, vòng `for` primitive.
2. Tổng theo `ArrayList<Integer>`, vòng `for-each` unbox.
3. Tổng theo `Arrays.stream(intArray).asLongStream().sum()` khi cần miền kết quả rộng hơn.
4. Tổng theo `list.stream().mapToLong(Integer::longValue).sum()`.
5. Thao tác **tạo và lấp đầy dữ liệu** đo riêng với **thao tác duyệt**, bởi allocation thường tập trung ở giai đoạn thêm phần tử.

Nếu nghiệp vụ thực tế còn lọc/tra cứu/phân nhóm, hãy đo cả tác vụ đó thay vì chỉ so sánh phép cộng trần. Không thể suy từ **benchmark micro** rằng một ứng dụng backend nhất định sẽ nhanh hơn theo đúng cùng tỷ lệ.

## 12. Cách chọn cấu trúc dữ liệu theo trường hợp

| Bài toán | Lựa chọn khởi đầu | Khi nào cân nhắc thay đổi |
|---|---|---|
| Cộng, nhân, đếm trong vòng lặp tính toán | `int`/`long`/`double` primitive | Chỉ cần wrapper khi API bắt buộc |
| Dãy triệu số nguyên, đọc tuần tự, kích thước biết trước | `int[]` | Cần resize/generic API hoặc nhiều chức năng `List` |
| Danh sách số linh hoạt cho API ứng dụng | `ArrayList<Integer>` | Profile chứng minh bộ nhớ/GC/CPU từ boxing đáng kể |
| Tính toán stream từ `int[]` | `IntStream`/`LongStream` | Chuyển thành `Stream<Integer>` khi API/collector cần object |
| `Map` có khóa nguyên và cập nhật mật độ cao | `HashMap<Integer, ...>` trước cho đơn giản | Nếu bottleneck, cân nhắc thư viện primitive-specialized |
| Dữ liệu số có thể thiếu | Wrapper hoặc kiểu biểu diễn missing rõ ràng | Primitive cần sentinel/bitmap đi kèm, phải tránh nhập nhằng |
| Dữ liệu cần dùng cùng API generic | Wrapper, `List<Integer>` | Đánh giá copy/boxing ở ranh giới hot path |
| Bộ đếm chia sẻ đa luồng | `AtomicInteger` hoặc `LongAdder` tùy yêu cầu | Tránh biến mutable `int` không đồng bộ; boxing không giải quyết concurrency |

Không nên chọn `LongAdder` thay cho `long` trong mọi nơi: nó phục vụ bài toán cập nhật đồng thời và có ngữ nghĩa/chi phí riêng. **Tính an toàn luồng không suy ra từ việc dùng wrapper.** Các wrapper số phổ biến là immutable, nhưng collection chứa chúng vẫn có thể cần đồng bộ khi truy cập/chỉnh sửa từ nhiều luồng.

## 13. Những hiểu lầm phổ biến

| Nhận định | Cách hiểu đúng |
|---|---|
| “Mỗi `Integer` trong vòng lặp đều cấp phát mới.” | Có cache cho miền nhất định và tối ưu JIT; không được khẳng định luôn cấp phát. |
| “Vì JVM tối ưu, boxing hoàn toàn miễn phí.” | Không có bảo đảm; object sống trong collection, heap/GC và truy cập tham chiếu vẫn có thể tốn chi phí. |
| “`List<Integer>` lưu `int` trực tiếp như `int[]`.” | `List<Integer>` làm việc với reference `Integer`. |
| “`for (int value : list)` không boxing/unboxing vì biến là `int`.” | Nếu list chứa `Integer`, phải unbox từng phần tử khi đọc. |
| “`IntStream.boxed()` giúp tránh boxing.” | Ngược lại, nó biến primitive `int` thành wrapper `Integer`. |
| “`Stream<Integer>.sum()` luôn là primitive sum.” | `Stream<Integer>` không có `sum()` trực tiếp; cần `mapToInt` hoặc reducer. |
| “Dùng `IntStream` thì tổng không thể overflow.” | `IntStream.sum()` vẫn trả `int` và có thể overflow. |
| “`Integer a == Integer b` luôn so sánh giá trị.” | Với hai reference, `==` so sánh đồng nhất tham chiếu; dùng `equals` hoặc primitive có kiểm tra `null`. |
| “Unboxing `null` trả 0.” | Ném `NullPointerException`. |
| “`Integer.valueOf` luôn trả đối tượng mới.” | Có cache cho ít nhất `-128..127` và triển khai có thể cache thêm. |
| “`ArrayList<Integer>` luôn chậm hơn mọi `int[]` với mọi workload.” | Không thể kết luận nếu không xét thuật toán, locality, API, tối ưu JVM và benchmark thực tế. |
| “Boxing là vấn đề duy nhất cần tối ưu.” | Thuật toán, số lần tìm kiếm, object lifetime, IO, GC và cấu trúc dữ liệu có thể chi phối hơn nhiều. |

## 14. Checklist thực hành

1. **Đánh dấu hot path:** vòng lặp nào chạy nhiều, phương thức nào được gọi thường xuyên, collection nào chiếm nhiều heap?
2. **Kiểm tra kiểu của biến tích lũy:** có dùng `Integer`/`Long` thay cho `int`/`long` chỉ để cộng hoặc đếm không?
3. **Tìm boxing ẩn:** `list.add(i)`, `map.put(intKey, intValue)`, `return primitive` qua API wrapper, `Stream<Integer>` và vòng `for-each` trên collection wrapper.
4. **Chọn đúng cấu trúc lưu trữ:** `int[]` khi dữ liệu primitive dày đặc; `List<Integer>` khi cần collection chung; primitive-specialized map nếu workload chứng minh có lợi.
5. **Đừng bỏ qua `null`:** wrapper có thể biểu diễn thiếu dữ liệu nhưng cũng gây NPE khi unbox.
6. **Dùng primitive streams đúng cách:** `IntStream`/`LongStream`/`DoubleStream`; hiểu `boxed()` tạo luồng wrapper.
7. **Đo với dữ liệu thực:** quy mô, miền giá trị, kiểu truy cập, warm-up, bộ nhớ, allocation rate và GC.
8. **Kiểm tra số học riêng:** tràn số khi cộng/tổng không được giải quyết chỉ nhờ loại boxing.
9. **Giữ code dễ hiểu:** khi lợi ích nhỏ hoặc đường đi không nóng, abstraction chuẩn có thể quan trọng hơn tối ưu vi mô.
10. **Kiểm chứng sau thay đổi:** chạy test về giá trị, `null`, thứ tự, tính đồng thời và đo lại trên cùng workload.

## 15. Tự kiểm tra

1. `Integer sum = 0; sum += i;` thường đòi những chuyển đổi kiểu nào ở cấp ngữ nghĩa?
2. Vì sao không thể khẳng định mọi autoboxing đều cấp phát một object mới?
3. Một `ArrayList<Integer>` thường lưu trực tiếp bit của `int` hay tham chiếu tới các `Integer`?
4. Vòng `for (int n : listOfInteger)` có phát sinh unboxing không? Nếu phần tử là `null` sẽ ra sao?
5. `IntStream.boxed()` làm gì? `Stream<Integer>.mapToInt(Integer::intValue)` làm gì?
6. `IntStream.of(Integer.MAX_VALUE, 1).sum()` cho ra gì? Dùng gì khi cần tổng `long` chính xác ở ví dụ này?
7. Vì sao hai `Integer` có cùng giá trị không nên so sánh bằng `==` nếu mục tiêu là so sánh số?
8. So sánh dùng `System.nanoTime()` chỉ một lần đủ để kết luận `int[]` nhanh hơn `List<Integer>` không?
9. Khi nào nên cân nhắc primitive-specialized collection thay cho `HashMap<Integer, Integer>`?
10. Tại sao bỏ boxing không tự giải quyết bài toán thread safety?

**Gợi ý đáp án:** (1) Unbox `Integer → int`, cộng primitive rồi box lại `int → Integer`. (2) Wrapper có cache và JIT có thể khử đối tượng tạm. (3) Tham chiếu tới `Integer`. (4) Có; `null` gây NPE. (5) Box primitive thành wrapper và unbox wrapper thành primitive. (6) `-2147483648`; dùng `asLongStream().sum()` trước khi cộng để nhận `2147483648L`. (7) Hai wrapper dùng `==` so sánh tham chiếu; dùng `equals` hoặc unbox nếu an toàn. (8) Không; phải warm-up, lặp nhiều, tránh tối ưu loại bỏ mã và đo workload thực. (9) Khi profile chỉ ra overhead boxing/bộ nhớ/hash map là bottleneck và thư viện khác đáp ứng yêu cầu. (10) Tính đúng giữa các luồng là vấn đề đồng bộ/atomic, độc lập với chi phí boxing.

**Ghi nhớ:** Boxing là cầu nối giữa **primitive** và **wrapper**, cần thiết cho nhiều API generic nhưng có thể làm tăng **allocation, overhead bộ nhớ và chi phí truy cập** trong vòng lặp hoặc collection lớn. Ưu tiên primitive ở đường tính toán dày đặc, chọn cấu trúc dữ liệu theo yêu cầu, hiểu cache và JIT, xử lý `null` đúng, rồi **đo đạc** trước khi tối ưu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-008">Objects.equals và xử lý wrapper có thể null</a>

<details>
<summary>Xem chi tiết</summary>

# Objects.equals và xử lý wrapper có thể null trong Java

Trong Java, các kiểu wrapper như `Integer`, `Long`, `Double`, `Boolean` là **kiểu tham chiếu**, vì vậy biến wrapper có thể chứa `null`. Dù autoboxing/unboxing khiến nhiều biểu thức trông giống đang làm việc với primitive, **unboxing một tham chiếu null sẽ ném `NullPointerException`**. Mặt khác, gọi `a.equals(b)` cũng ném ngoại lệ nếu `a == null`.

`java.util.Objects.equals(a, b)` là công cụ tiêu chuẩn để so sánh hai đối tượng **mà không cần gọi `equals` trên một tham chiếu có thể null**. Tuy nhiên nó **không** tự chuyển `Integer` thành `Long`, **không** unbox để so sánh số học và **không** thay thế chính sách xử lý dữ liệu thiếu. Bài học trình bày cơ chế, các góc cạnh và cách viết code Java 17+ an toàn.

## 1. Wrapper nullable khác primitive như thế nào?

~~~java
int primitive = 0;          // Luôn có giá trị int
Integer wrapper = null;     // Có thể null
Integer boxed = 0;          // Autoboxing: int -> Integer
int unboxed = boxed;        // Unboxing: Integer -> int
~~~

Các wrapper `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `Character` và `Boolean` là các lớp tham chiếu. Chúng có thể đại diện cho **giá trị cụ thể** hoặc **không có đối tượng** (`null`). `null` không đồng nghĩa với số 0, `false` hoặc NaN.

| Trạng thái | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Primitive `int` | `0` | Có giá trị số nguyên 0 |
| Wrapper `Integer` | `Integer.valueOf(0)` | Có object chứa giá trị 0 |
| Wrapper `Integer` | `null` | Không có object để lấy giá trị |
| Primitive `double` | `Double.NaN` | Có một giá trị `double` đặc biệt; **không phải null** |

Một wrapper null có thể xuất hiện từ trường dữ liệu tùy chọn, một ô thiếu trong dữ liệu ngoài, giá trị trả về của API hoặc phần tử trong collection. Nếu yêu cầu nghiệp vụ phân biệt “không biết” và “bằng 0”, dùng wrapper nullable có thể phù hợp; nếu trường luôn bắt buộc, primitive hoặc validation không-null thường giúp loại bỏ một trạng thái lỗi.

## 2. Ba cách so sánh và mức an toàn khi null

### Cách 1 — `a.equals(b)`

~~~java
Integer a = null;
Integer b = 5;

// a.equals(b); // NullPointerException: a là null
~~~

Khi `a` không null, lời gọi `a.equals(b)` trả về boolean theo hợp đồng equality của object. Nhưng nếu `a` có thể null, bạn phải kiểm tra trước.

### Cách 2 — `Objects.equals(a, b)`

~~~java
import java.util.Objects;

Integer a = null;
Integer b = 5;

boolean first = Objects.equals(a, b);      // false
boolean second = Objects.equals(a, null);  // true
boolean third = Objects.equals(null, null); // true
~~~

`Objects.equals` định nghĩa logic tương đương:

~~~java
static boolean equivalentToObjectsEquals(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
}
~~~

Đây là **phương thức minh họa**, không phải định nghĩa thay thế của thư viện Java. Các nhánh có ý nghĩa:

- Hai đối số cùng null → `true`, vì cùng tham chiếu null.
- Một null, một khác null → `false`.
- Hai đối tượng cùng tham chiếu → `true`.
- Hai object khác tham chiếu và `a` khác null → gọi `a.equals(b)`.

Cụm “null-safe” nghĩa là công cụ **không gọi một phương thức instance trên đối số đầu tiên đang null**. Nó không có nghĩa có thể che mọi lỗi trong custom `equals` do lập trình viên tự viết, hoặc biến dữ liệu null thành một giá trị số.

### Cách 3 — toán tử `==`

`==` có **hai ngữ nghĩa** tùy kiểu tĩnh của toán hạng:

~~~java
Integer x = Integer.valueOf(5);
Integer y = Integer.valueOf(5);
int five = 5;

boolean numberComparison = (x == five);  // true, unbox x
boolean referenceComparison = (x == y);  // kiểm tra cùng tham chiếu
~~~

Ở phép `x == five`, Java unbox `Integer` thành `int` rồi so sánh primitive; nếu `x` null có thể ném `NullPointerException`. Ở `x == y` khi **cả hai toán hạng là các tham chiếu `Integer`**, Java so sánh tham chiếu object. Kết quả có thể phụ thuộc cơ chế cache đối tượng; không dùng `==` để biểu thị value equality giữa wrapper. Ngoại lệ đáng nhớ: `x == null` là phép so sánh tham chiếu, **không unbox `x`** và an toàn.

## 3. `Objects.equals` không tự unbox hoặc nâng kiểu

Các wrapper số triển khai `equals` theo lớp cụ thể. Vì vậy:

~~~java
Integer count = Integer.valueOf(10);
Long total = Long.valueOf(10L);

boolean sameInteger = Objects.equals(count, Integer.valueOf(10)); // true
boolean differentWrappers = Objects.equals(count, total);         // false
boolean literalsWithDifferentTypes = Objects.equals(10, 10L);     // false
~~~

Ở dòng cuối, Java boxing đối số `10` thành `Integer` và `10L` thành `Long` khi truyền cho phương thức nhận `Object`; `Objects.equals` không thực hiện phép số học `10 == 10L`.

So sánh nhanh:

| Biểu thức | Kết quả | Tại sao? |
| --- | --- | --- |
| `Objects.equals(null, null)` | `true` | Cả hai tham chiếu null |
| `Objects.equals(null, 10)` | `false` | Chỉ một bên null |
| `Objects.equals(10, 10)` | `true` | Cùng wrapper `Integer`, cùng giá trị |
| `Objects.equals(10, 10L)` | `false` | `Integer` khác `Long` |
| `10 == 10L` | `true` | Primitive numeric promotion |
| `Objects.equals(Double.NaN, Double.NaN)` | `true` | `Double.equals` xem hai NaN bằng nhau |
| `Objects.equals(+0.0, -0.0)` | `false` | `Double.equals` phân biệt zero có dấu |

**Không nên dùng `Objects.equals` để so sánh trị số giữa nhiều wrapper khác kiểu.** Nếu cần coi `Integer(10)` và `Long(10)` như cùng một số, phải định nghĩa miền số và quy tắc chuyển kiểu. Ví dụ, khi chắc chắn hai đối số là wrapper số nguyên trong phạm vi `long` và không null, có thể dùng `a.longValue() == b.longValue()`. Không chuyển bừa mọi `Number` sang `doubleValue()`: số nguyên lớn có thể mất độ chính xác.

## 4. Unboxing null: lỗi không biến mất nhờ autoboxing

Ví dụ này **cố ý gây lỗi khi chạy** nếu bỏ comment:

~~~java
Integer count = null;

// int primitive = count;   // NullPointerException: unboxing
// int next = count + 1;    // NullPointerException: toán tử + phải unbox
// boolean positive = count > 0; // NullPointerException
// boolean zero = (count == 0);  // NullPointerException
~~~

Dù `count` có kiểu wrapper, toán tử số học hoặc so sánh với primitive cần lấy giá trị `int` từ object. Khi object là null, unboxing không thể thực hiện.

Ngược lại:

~~~java
Integer count = null;
boolean isAbsent = (count == null); // true, không unbox
boolean same = Objects.equals(count, null); // true, không unbox
~~~

Sự khác biệt giữa `count == null` và `count == 0` rất quan trọng. Câu đầu kiểm tra tình trạng có object hay không; câu sau cố lấy giá trị số từ wrapper.

Đối với `Boolean` cũng vậy:

~~~java
Boolean enabled = null;
// if (enabled) { } // NullPointerException do unboxing Boolean
if (Boolean.TRUE.equals(enabled)) {
    // Chạy chỉ khi enabled là Boolean.TRUE
}
~~~

`Boolean.TRUE.equals(enabled)` an toàn vì hằng `Boolean.TRUE` chắc chắn không null. Nó coi cả null lẫn false là “không bật”, **chỉ phù hợp nếu nghiệp vụ chấp nhận cách gộp này**. Nếu null nghĩa là “chưa xác định”, đừng tự biến nó thành false mà không định nghĩa chính sách.

## 5. Dùng short-circuit `&&` và `||` để kiểm tra trước khi unbox

Trong các điều kiện, Java đánh giá `&&` và `||` từ trái sang phải và chỉ đánh giá vế phải khi cần. Ta có thể dùng tính chất này để tránh unboxing null:

~~~java
Integer count = null;

boolean positive = count != null && count > 0; // false
boolean missingOrPositive = count == null || count > 0; // true
~~~

- Với `&&`, nếu `count != null` đã false thì vế `count > 0` không được chạy.
- Với `||`, nếu `count == null` đã true thì vế `count > 0` không được chạy.

**Thứ tự điều kiện không được đảo tùy tiện:**

~~~java
// count > 0 && count != null  // Sai: có thể unbox null trước
// count > 0 || count == null  // Sai: có thể unbox null trước
~~~

Với toán tử boolean không-short-circuit `&` hoặc `|`, cả hai vế được đánh giá (khi dùng boolean), nên **không** áp dụng mẹo null guard như trên. Đây là vấn đề quan trọng khi refactor điều kiện hoặc xây biểu thức filter.

Một phương thức kiểm tra giá trị dương nullable có thể viết rõ ràng:

~~~java
static boolean isPositive(Integer value) {
    return value != null && value > 0;
}
~~~

Phương thức này chủ ý trả `false` nếu không có giá trị; đó là một **quy ước nghiệp vụ** cần được thể hiện rõ trong tên hàm và tài liệu.

## 6. Chọn mặc định hay từ chối null?

`Objects.equals` phục vụ **so sánh**, không trả giá trị thay thế. Khi ứng dụng cần dùng primitive, phải ra quyết định cụ thể với null.

### Cách A — trả về giá trị mặc định

~~~java
Integer optionalCount = null;
int safeCount = java.util.Objects.requireNonNullElse(optionalCount, 0);
// safeCount = 0
~~~

`Objects.requireNonNullElse(value, defaultValue)` trả giá trị đầu nếu khác null; nếu không thì trả default. **Default cũng bắt buộc khác null**, nếu cả hai đều null sẽ ném `NullPointerException`. Đối với wrapper, phương thức này trả về **wrapper**; trong ví dụ, gán vào `int` diễn ra unboxing sau khi đã có default không null.

Với biểu thức tạo default tốn tài nguyên, có `Objects.requireNonNullElseGet(value, supplier)`; supplier được gọi khi giá trị ban đầu null và phải trả giá trị không null. Nếu đối số đầu đã có giá trị, supplier không bị gọi.

Một lựa chọn tương tự là:

~~~java
int safeCount = optionalCount == null ? 0 : optionalCount;
~~~

Toán tử điều kiện ở đây chọn giá trị primitive an toàn sau khi kiểm tra null. Tuy nhiên **đừng** mặc định mọi null đều là 0: tổng đơn hàng chưa được nhập, tuổi chưa biết và số lượng chính xác bằng 0 thường là các trạng thái khác nhau.

### Cách B — báo lỗi ngay tại ranh giới

~~~java
static int requireCount(Integer value) {
    return java.util.Objects.requireNonNull(value, "count is required");
}
~~~

Hàm này ném `NullPointerException` với thông báo khi null và unbox sau khi đã xác nhận có object. Nếu null đại diện cho **đầu vào không hợp lệ**, bạn có thể chọn `IllegalArgumentException` hoặc một kiểu lỗi nghiệp vụ khác theo quy ước API.

### Cách C — giữ rõ trạng thái thiếu

Nếu thiếu dữ liệu là hợp lệ, bạn có thể giữ `Integer` nullable, dùng `Optional<Integer>` ở một số API trả về, hoặc mô hình hóa trạng thái riêng. Hãy xác định quy tắc downstream: tính toán phải dừng, bỏ qua phần tử thiếu, hay trả về kết quả “chưa đủ dữ liệu”? Đừng chọn một giá trị giả như `-1` hoặc 0 nếu nó cũng có thể là giá trị thật.

## 7. So sánh nullable ở các vị trí thực tế

### DTO và đối tượng nghiệp vụ

Giả sử hai request có trường `Integer quantity` cho phép null. Nếu cần kiểm tra xem trường có thay đổi so với bản cũ hay không:

~~~java
static boolean quantityChanged(Integer oldQuantity, Integer newQuantity) {
    return !java.util.Objects.equals(oldQuantity, newQuantity);
}
~~~

Khi cả hai đều null, hàm trả false (không thay đổi); khi chỉ một bên null, hàm trả true; khi hai bên có cùng `Integer`, trả false. Điều này khác với chính sách “null nghĩa là 0”: nếu nghiệp vụ coi null và 0 là giống nhau, phải chuẩn hóa **một cách công khai** trước khi so sánh.

### Collection và tìm kiếm

~~~java
java.util.List<Integer> values = java.util.Arrays.asList(1, null, 3);
boolean hasMissing = values.contains(null); // true
boolean hasThree = values.contains(3);      // true
~~~

Các collection sử dụng equality theo hợp đồng của phần tử; `List.contains` theo `equals`, nên có thể xử lý null nếu triển khai collection cho phép. Tuy nhiên, với `HashMap`/`HashSet` và wrapper làm khóa, đừng gộp `Integer(3)` với `Long(3)`; `Objects.equals` không hề xóa ranh giới kiểu này.

### Sắp xếp danh sách có null

~~~java
java.util.List<Integer> values =
        new java.util.ArrayList<>(java.util.Arrays.asList(3, null, 1));
values.sort(java.util.Comparator.nullsFirst(Integer::compare));
// [null, 1, 3]
~~~

`Integer::compare` so sánh các giá trị không null; `Comparator.nullsFirst` xử lý null trước khi chuyển cho comparator nội bộ. Có thể dùng `nullsLast` nếu muốn xếp giá trị thiếu cuối danh sách.

**Đừng dùng `Integer::compare` trực tiếp cho danh sách có null** khi chưa có lớp xử lý: unboxing đối số null có thể ném `NullPointerException` trong quá trình sort.

## 8. `Objects.equals` và mảng, đối tượng cấu trúc phức tạp

Hai mảng khác tham chiếu nhưng có cùng phần tử không được xem bằng nhau bằng `Objects.equals`:

~~~java
Integer[] a = {1, null};
Integer[] b = {1, null};

boolean shallow = java.util.Objects.equals(a, b);     // false
boolean deep = java.util.Objects.deepEquals(a, b);    // true
~~~

Mảng Java không override `Object.equals` theo nội dung; `Objects.equals` do đó so sánh cùng tham chiếu. `Objects.deepEquals` nhận biết mảng và so sánh phần tử (kể cả mảng lồng nhau) một cách thích hợp; hoặc dùng `Arrays.equals`/`Arrays.deepEquals` theo dạng dữ liệu. Không cho rằng tên phương thức “Objects” tự động so sánh sâu mọi object: đối với class tự định nghĩa, logic equality vẫn phụ thuộc triển khai `equals`.

Cẩn trọng khi viết class có field wrapper nullable: nếu dùng `equals` tự viết, `hashCode` nên sử dụng chính những field phù hợp, chẳng hạn `Objects.hash(fieldA, fieldB)`, để tuân theo hợp đồng “hai object bằng nhau phải có hash code bằng nhau”. Với Java `record`, equality sinh tự động cho component theo quy tắc của ngôn ngữ; vẫn cần hiểu ý nghĩa của giá trị null và kiểu của component.

## 9. Ví dụ Java 17+ hoàn chỉnh có thể chạy

Lưu tệp `NullableWrapperDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class NullableWrapperDemo {
    public static void main(String[] args) {
        Integer missing = null;
        Integer ten = 10;
        Integer tenAgain = Integer.valueOf(10);
        Long tenLong = 10L;

        System.out.println("missing == null: " + (missing == null));
        System.out.println("equals(null,null): "
                + Objects.equals(null, null));
        System.out.println("equals(missing,ten): "
                + Objects.equals(missing, ten));
        System.out.println("equals(ten,missing): "
                + Objects.equals(ten, missing));
        System.out.println("equals(ten,tenAgain): "
                + Objects.equals(ten, tenAgain));
        System.out.println("equals(ten,tenLong): "
                + Objects.equals(ten, tenLong));
        System.out.println("equals(10,10L): "
                + Objects.equals(10, 10L));
        System.out.println("ten == 10: " + (ten == 10));
        System.out.println("safe AND: "
                + (missing != null && missing > 0));
        System.out.println("safe OR: "
                + (missing == null || missing > 0));

        try {
            int primitive = missing;
            System.out.println(primitive);
        } catch (NullPointerException ex) {
            System.out.println("unbox null: NullPointerException");
        }

        System.out.println("default zero: "
                + Objects.requireNonNullElse(missing, 0));

        List<Integer> list =
                new ArrayList<>(Arrays.asList(3, null, 1));
        list.sort(Comparator.nullsFirst(Integer::compare));
        System.out.println("sorted null first: " + list);

        Integer[] a = {1, null};
        Integer[] b = {1, null};
        System.out.println("arrays equals: " + Objects.equals(a, b));
        System.out.println("arrays deepEquals: "
                + Objects.deepEquals(a, b));

        System.out.println("Double NaN equals: "
                + Objects.equals(Double.NaN, Double.NaN));
        System.out.println("signed zero equals: "
                + Objects.equals(+0.0, -0.0));
        System.out.println("BigDecimal scale equals: "
                + Objects.equals(
                        new BigDecimal("1.0"),
                        new BigDecimal("1.00")));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac NullableWrapperDemo.java
java NullableWrapperDemo
~~~

Kết quả stdout:

~~~text
missing == null: true
equals(null,null): true
equals(missing,ten): false
equals(ten,missing): false
equals(ten,tenAgain): true
equals(ten,tenLong): false
equals(10,10L): false
ten == 10: true
safe AND: false
safe OR: true
unbox null: NullPointerException
default zero: 0
sorted null first: [null, 1, 3]
arrays equals: false
arrays deepEquals: true
Double NaN equals: true
signed zero equals: false
BigDecimal scale equals: false
~~~

Chương trình xác nhận được cả đường đi có và không có null, wrapper khác kiểu, unboxing an toàn/không an toàn, thứ tự sắp xếp null và sự khác nhau giữa equality sâu/nông. Lưu ý `try/catch` ở đây chỉ dùng để minh họa ngoại lệ do unboxing null; **không nên dùng bắt ngoại lệ thay kiểm tra null** như một luồng xử lý thường ngày.

## 10. Những giới hạn cần nhớ của `Objects.equals`

- **Không tự biến null thành số 0:** `Objects.equals(null, 0)` là `false`; null vẫn là trạng thái vắng mặt.
- **Không nâng kiểu wrapper khi equality:** `Objects.equals(10, 10L)` là `false` vì `Integer` khác `Long`.
- **Không tự so sánh mảng theo phần tử:** dùng `Arrays.equals`, `Arrays.deepEquals` hoặc `Objects.deepEquals`.
- **Không xử lý null bên trong logic khác:** `Objects.equals(a, b)` không làm cho `a + 1` an toàn nếu `a == null`.
- **Không giải quyết số thực theo tolerance:** `Objects.equals(0.1 + 0.2, 0.3)` trả `false`; định nghĩa riêng tolerance nếu bài toán yêu cầu.
- **Không sửa hợp đồng `BigDecimal.equals`:** `new BigDecimal("1.0")` và `new BigDecimal("1.00")` có giá trị toán học bằng nhau theo `compareTo == 0` nhưng `equals` trả `false` vì khác scale.
- **Không đồng nhất Boolean null với false:** `Objects.equals(null, Boolean.FALSE)` trả `false`; nếu dùng `Boolean.TRUE.equals(value)` thì bạn đang chủ động diễn giải null giống “không bật” cho mục đích kiểm tra.

Một số lỗi phổ biến là dùng `if (wrapper == 0)` trước khi kiểm tra null, viết `wrapper.equals(other)` dù wrapper nullable, dựa vào `==` để so giá trị của hai wrapper, hoặc ép các giá trị null về mặc định tại quá nhiều lớp hệ thống làm mất dấu nguồn dữ liệu thiếu.

## 11. Cách lựa chọn khi xây dựng API

| Yêu cầu | Cách xử lý | Điểm cần chú ý |
| --- | --- | --- |
| So sánh hai wrapper nullable theo object equality | `Objects.equals(a, b)` | Hai null bằng nhau; các wrapper khác kiểu không tự bằng nhau |
| Kiểm tra wrapper null | `value == null` hoặc `value != null` | Không unbox |
| Dùng wrapper trong phép toán | Null guard rồi unbox | Thông báo lỗi/mặc định theo hợp đồng |
| Dùng Boolean nullable trong điều kiện | `Boolean.TRUE.equals(flag)` hoặc ba nhánh | Phân biệt false với không xác định nếu cần |
| Có giá trị mặc định hợp lệ | `Objects.requireNonNullElse(value, defaultValue)` | Default phải khác null; không che dữ liệu thiếu ngoài ý muốn |
| Bắt buộc có giá trị | `Objects.requireNonNull(value, message)` | Fail fast tại ranh giới |
| Sắp xếp list có null | `Comparator.nullsFirst/nullsLast` | Comparator bên trong chỉ xử lý non-null |
| Equality mảng | `Arrays.equals` hoặc `Objects.deepEquals` | Chọn đúng mức nông/sâu |
| So sánh số học khác wrapper type | Chuẩn hóa theo miền số đã xác định | Tránh ép mọi `Number` về `double` gây mất chính xác |

## 12. Tự luyện và đáp án

**Câu 1.** Với `Integer x = null`, kết quả của `Objects.equals(x, null)` và `x == 0` là gì?

**Đáp án:** `Objects.equals(x, null)` là `true`; `x == 0` cố unbox null và ném `NullPointerException`.

**Câu 2.** `Objects.equals(Integer.valueOf(3), Long.valueOf(3L))` trả gì? Còn `3 == 3L`?

**Đáp án:** Lời gọi đầu trả `false` do khác wrapper; phép so sánh primitive trả `true` sau numeric promotion.

**Câu 3.** Với `Boolean approved = null`, viết một điều kiện không ném NPE và chỉ nhận đúng true.

**Đáp án:** `Boolean.TRUE.equals(approved)`. Nếu null cần phân biệt với false, bổ sung nhánh xử lý riêng.

**Câu 4.** Tại sao `count != null && count > 0` an toàn hơn `count > 0 && count != null`?

**Đáp án:** `&&` đánh giá từ trái sang phải và short-circuit. Biểu thức đầu kiểm tra null trước; biểu thức sau đã unbox trước khi kiểm tra.

**Câu 5.** Hai mảng `Integer[]` mới tạo với cùng phần tử có nhất thiết `Objects.equals` là true không?

**Đáp án:** Không. `Objects.equals` dùng equality tham chiếu của mảng; dùng `Arrays.equals` hoặc `Objects.deepEquals` để so sánh phần tử.

**Câu 6.** Nếu null phải được coi là một giá trị “chưa cung cấp”, có nên thay nó bằng 0 chỉ để tránh NPE không?

**Đáp án:** Không nên, trừ khi đặc tả nghiệp vụ quy định rõ null tương đương 0. Có thể giữ nullable/`Optional` hoặc từ chối input không hợp lệ để bảo toàn ý nghĩa dữ liệu.

## Tài liệu tham khảo

- [Java SE 21 — Objects.equals và deepEquals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html).
- [Java SE 21 — Integer.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html#equals(java.lang.Object)).
- [Java SE 21 — Boolean](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Boolean.html).
- [Java SE 21 — Comparator.nullsFirst](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html#nullsFirst(java.util.Comparator)).
- [Java Language Specification `5.1.8 — Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.8).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-009">Toán tử ?: có thể unbox nhánh wrapper</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử ?: có thể unbox nhánh wrapper trong Java

> **Java core – numbers · Boxing và unboxing**  
> **Mục tiêu:** Hiểu vì sao biểu thức điều kiện `condition ? expression1 : expression2` đôi khi tự động chuyển `Integer`, `Long`, `Double`… thành primitive; biết lúc nào giá trị `null` gây `NullPointerException`, lúc nào nhánh không chọn không được đánh giá, và cách thiết kế xử lý giá trị tùy chọn an toàn trong Java 17+.

## 1. Một dòng Java có thể ném NullPointerException dù không gọi .intValue()

Xét đoạn mã:

```java
Integer boxed = null;
boolean chooseBoxed = true;

Integer result = chooseBoxed ? boxed : 0;
// NullPointerException khi chạy!
```

Thoạt nhìn, biến nhận có kiểu `Integer` nên ta dễ nghĩ nhánh `boxed` sẽ được giữ nguyên là `null`. Nhưng trong ví dụ này, một nhánh là **`Integer`** và nhánh còn lại là **`int`** (literal `0`). Theo quy tắc kiểu của **numeric conditional expression**, toán tử `?:` có thể xác định kiểu kết quả là **primitive `int`**.

Khi điều kiện là `true`, Java phải **unbox** nhánh `boxed` thành `int` để tạo giá trị của biểu thức `?:`. Vì `boxed == null`, thao tác này ném `NullPointerException`. Phép boxing để gán vào biến `Integer result` sẽ **không xảy ra**, bởi quá trình đánh giá biểu thức đã thất bại.

Cùng một nhánh wrapper nhưng điều kiện `false`:

```java
Integer boxed = null;
Integer result = false ? boxed : 0;
System.out.println(result); // 0
```

Lúc này Java chọn nhánh primitive `0`, nên không đọc giá trị `boxed` và không xảy ra lỗi.

**Điểm cốt lõi:** kiểu của **toàn bộ biểu thức điều kiện** được xác định **khi biên dịch** dựa trên quy tắc của Java; khi chạy, chỉ nhánh được chọn được đánh giá. Nếu nhánh được chọn là wrapper cần chuyển sang primitive, việc **unboxing có thể xảy ra ngay trong toán tử `?:`**, trước bước gán cuối.

## 2. Ôn tập: boxing, unboxing, primitive và wrapper

**Boxing** chuyển giá trị primitive sang đối tượng wrapper; **unboxing** lấy giá trị primitive từ wrapper.

| Primitive | Wrapper | Ví dụ unbox |
| --- | --- | --- |
| `byte` | `Byte` | `Byte.byteValue()` |
| `short` | `Short` | `Short.shortValue()` |
| `int` | `Integer` | `Integer.intValue()` |
| `long` | `Long` | `Long.longValue()` |
| `float` | `Float` | `Float.floatValue()` |
| `double` | `Double` | `Double.doubleValue()` |
| `char` | `Character` | `Character.charValue()` |
| `boolean` | `Boolean` | `Boolean.booleanValue()` |

Ví dụ:

```java
int n = 7;
Integer boxed = n;            // boxing, gần với Integer.valueOf(n)
int original = boxed;         // unboxing, gần với boxed.intValue()

Integer nullable = null;
// int failed = nullable;     // biên dịch được nhưng ném NPE khi chạy
```

Wrapper là kiểu **tham chiếu**, nên `Integer` có thể là `null`. Primitive `int` không chứa được `null`. Mỗi khi ngữ cảnh đòi chuyển wrapper đang bằng `null` về primitive, Java ném `NullPointerException`.

Trong thực tế unboxing có thể bị ẩn trong:

- phép cộng/trừ/nhân/chia như `boxed + 1`;
- so sánh wrapper với primitive, ví dụ `boxed == 5`;
- gán wrapper sang primitive, ví dụ `int x = boxed;`;
- điều kiện `if (boxedBoolean)` nếu `boxedBoolean` có kiểu `Boolean`;
- **toán tử `?:`**, khi quy tắc chọn kiểu cần primitive.

Bài này tập trung vào trường hợp cuối, đặc biệt những biểu thức nhìn có vẻ đang “chọn một đối tượng”.

## 3. Quy tắc nền tảng của toán tử điều kiện ?:

Cú pháp:

```java
condition ? whenTrue : whenFalse
```

Trình tự:

1. Đánh giá biểu thức `condition`.
2. Nếu điều kiện đúng, **chỉ đánh giá `whenTrue`**.
3. Nếu điều kiện sai, **chỉ đánh giá `whenFalse`**.
4. Chuyển giá trị nhánh được chọn thành **kiểu mà toán tử điều kiện yêu cầu**, nếu cần, rồi tạo kết quả biểu thức.

Hai nhánh **không chạy cả hai**. Nhưng compiler phải xác định được **kiểu chung/kiểu kết quả của biểu thức** theo ngữ pháp và quy tắc type system của Java trước khi chương trình chạy.

Một ví dụ không có wrapper:

```java
int x = true ? 10 : 20;    // 10
int y = false ? 10 : 20;   // 20
```

Toán tử `?:` cũng có thể lồng nhau; nó được nhóm theo hướng **phải sang trái**:

```java
int result = a ? 1 : b ? 2 : 3;
// Nhóm: a ? 1 : (b ? 2 : 3)
```

Ở đây `a` và `b` là các biến `boolean` đã có sẵn; đây là ví dụ **cú pháp minh họa**, không phải chương trình hoàn chỉnh. Khi lồng nhiều biểu thức, từng toán tử `?:` có quy tắc chọn kiểu riêng, vì vậy càng dễ xảy ra chuyển kiểu ngầm khó nhìn.

### Điều kiện boolean cũng có thể unbox

Ngay **condition** cũng có thể gây NPE:

```java
Boolean enabled = null;
// int status = enabled ? 1 : 0; // NPE khi unbox enabled để lấy boolean
```

Trường hợp này **khác** lỗi do nhánh wrapper được chọn: lỗi xảy ra **trước khi chọn nhánh**. Cần nhận biết đúng vị trí unboxing khi đọc stack trace hoặc kiểm thử.

## 4. Vì sao Integer và int có thể tạo kết quả primitive int?

Xét:

```java
Integer nullable = null;
int fallback = 0;

Integer value = choose ? nullable : fallback;
```

Đây là **numeric conditional expression** do hai nhánh có kiểu liên quan đến số. Với cặp nhánh `Integer` và `int`, kiểu của biểu thức điều kiện là `int`. Nếu chọn `nullable`, nó cần được **unbox**.

Diễn giải ý nghĩa của dòng mã trên theo các bước:

```text
condition ? Integer : int
          └── kiểu biểu thức theo quy tắc: int

Khi condition == true:
1. Lấy nullable (Integer).
2. Chuyển Integer -> int (unboxing).
3. Nếu nullable == null: ném NullPointerException.
4. Nếu thành công, kết quả int có thể được boxing khi
   gán vào biến Integer value.

Khi condition == false:
1. Lấy fallback (int).
2. Kết quả có kiểu int.
3. Boxing nếu đích gán có kiểu Integer.
```

Trong biểu thức có `Integer` và `long`, Java có thể cần mở rộng số sang `long`:

```java
Integer maybe = null;
// long value = true ? maybe : 0L; // NPE: nhánh Integer cần unbox rồi widen
```

**Không** suy diễn quy tắc tổng quát quá đơn giản như “hễ có một wrapper và một primitive, kết quả luôn là primitive có tên giống wrapper”. Các bảng chọn kiểu của toán tử `?:` có những trường hợp đặc biệt cho constant expression kiểu `int`, `byte`/`short`/`char` và các kiểu numeric khác. Cần xác định **cặp kiểu thực tế** của hai nhánh và kiểu của biểu thức lồng, không đoán từ kiểu biến đích.

Tuy nhiên, ví dụ nền tảng `Integer ? int` gây unboxing là đủ để thấy rủi ro.

## 5. Kiểu biến nhận không nhất thiết ngăn unboxing

So sánh:

```java
Integer boxed = null;
boolean c = true;

// Cả hai có thể ném NPE khi chọn boxed:
 // int primitiveResult = c ? boxed : 0;
 // Integer wrapperResult = c ? boxed : 0;
```

Vì trong cả hai biểu thức, chính nhánh `boxed` và literal `0` đã quyết định kiểu numeric conditional là `int`. Việc khai báo biến nhận là `Integer` **không tự biến nhánh `0` thành `Integer` trước khi toán tử tính xong**.

Tương tự, khi gán vào `Object`:

```java
Integer boxed = null;
// Object objectResult = true ? boxed : 0; // vẫn có thể NPE
```

Ngữ cảnh này không bảo đảm giữ nguyên tham chiếu `boxed`. Biểu thức điều kiện vẫn được xác định kiểu theo hai nhánh; giá trị `int` chỉ được boxing thành đối tượng **sau** khi đã đánh giá thành công.

Để tránh suy luận sai, hãy kiểm tra biểu thức từ **bên trong ra bên ngoài**: kiểu của từng nhánh → kiểu của `?:` → kiểu của phép gán. Đừng đi ngược bằng cách xem biến đích rồi suy ra mọi chuyển kiểu trung gian.

## 6. Cả hai nhánh đều Integer: khác biệt quan trọng

Bây giờ thay literal primitive `0` bằng một wrapper `Integer`:

```java
Integer nullable = null;
Integer zeroBox = Integer.valueOf(0);

Integer chosen = true ? nullable : zeroBox;
System.out.println(chosen == null); // true
```

Khi **cả hai nhánh đều có cùng kiểu `Integer`**, quy tắc chọn kiểu của biểu thức giữ kết quả kiểu `Integer`. Nếu chọn nhánh `nullable`, kết quả là tham chiếu `null`; không có lý do phải unbox tại toán tử này.

Một cách viết dễ đọc khác:

```java
Integer chosen = true
        ? nullable
        : Integer.valueOf(0); // cùng kiểu Integer với nhánh kia
```

Lúc này có thể giữ `null` trong biến nhận kiểu `Integer`. Tuy nhiên, nếu chuyển giá trị được chọn sang **primitive sau đó**, NPE vẫn xảy ra:

```java
Integer nullable = null;
Integer other = Integer.valueOf(0);

// Toán tử ?: trả Integer có thể null, nhưng phép gán sang int cần unbox.
// int number = true ? nullable : other; // NPE tại unboxing để gán
```

Câu hỏi cần trả lời không chỉ là “`?:` có unbox hay không”, mà còn là **bước dùng kết quả tiếp theo có yêu cầu unbox không**.

### Cùng kiểu wrapper nhưng có giá trị null ở một nhánh

```java
Integer a = null;
Integer b = 7;
Integer selectedA = true ? a : b;  // null
Integer selectedB = false ? a : b; // 7
```

Hai dòng đều an toàn **ở bước chọn nhánh và gán cho `Integer`**. Việc truy cập `selectedA.intValue()` hoặc cộng `selectedA + 1` sau đó mới phát sinh NPE.

**Cảnh báo:** `Integer` là một kiểu numeric wrapper; chỉ vì cả hai nhánh được viết trông giống đối tượng không có nghĩa mọi trường hợp trộn với kiểu numeric khác đều bảo toàn tham chiếu. Những trường hợp trên an toàn vì **cả hai nhánh có cùng kiểu `Integer`** và đích nhận cũng là `Integer`.

## 7. Điều kiện true/false quyết định có xảy ra unboxing lúc chạy

Một biểu thức có thể **có chuyển kiểu ngầm trong quy tắc kiểu** nhưng không ném lỗi ở mọi lần thực thi.

```java
Integer maybe = null;

int a = false ? maybe : 10; // 10; nhánh maybe không được đánh giá

// int b = true ? maybe : 10; // NPE; chọn nhánh maybe
```

Cơ chế **chỉ đánh giá nhánh được chọn** rất quan trọng: Java không chạy cả hai biểu thức rồi chọn giá trị. Nếu nhánh không được chọn có gọi hàm gây tác dụng phụ, ném ngoại lệ hoặc chứa một wrapper null, nó không bị đánh giá.

Ví dụ:

```java
static Integer loadNullable() {
    System.out.println("called");
    return null;
}

// Không in "called", không NPE:
int safe = false ? loadNullable() : 100;

// In "called" rồi NPE:
// int failed = true ? loadNullable() : 100;
```

Đây là mã minh họa; phương thức `loadNullable` phải được đặt trong một class, các câu lệnh khác trong phương thức có ngữ cảnh phù hợp.

Đồng thời, compiler vẫn **type-check cả hai nhánh**. Nhánh không chọn không có nghĩa bạn được phép viết mã không hợp lệ cú pháp/kiểu tại đó.

## 8. Boolean wrapper là một trường hợp riêng

Với wrapper `Boolean` có thể `null`, có ít nhất **hai điểm** cần cảnh giác.

### 8.1. Unbox để dùng làm điều kiện

```java
Boolean condition = null;

// String text = condition ? "yes" : "no"; // NPE khi dùng condition
```

Phần điều kiện phải có giá trị `boolean` (hoặc `Boolean` được unbox). Nếu `condition` là `null` thì không thể chọn nhánh.

Nếu ý nghĩa nghiệp vụ là **chỉ `Boolean.TRUE` được coi là đúng**, có thể viết:

```java
Boolean condition = null;
String text = Boolean.TRUE.equals(condition) ? "yes" : "no";
System.out.println(text); // no
```

Đây là **quy tắc nghiệp vụ được chọn**, không phải định nghĩa tự nhiên cho `null`. Hệ thống có trạng thái ba giá trị `true`/`false`/`unknown` có thể cần giữ nguyên trạng thái unknown, không âm thầm biến thành false.

### 8.2. Một nhánh Boolean, một nhánh boolean

```java
Boolean nullable = null;
// boolean selected = true ? nullable : false; // NPE khi chọn nullable
```

Tương tự numeric wrapper, trong trường hợp trộn `Boolean` với primitive `boolean`, chuyển kiểu có thể yêu cầu unboxing nhánh wrapper.

Nếu muốn giữ kết quả kiểu `Boolean` cho phép `null`:

```java
Boolean nullable = null;
Boolean fallback = Boolean.FALSE;
Boolean selected = true ? nullable : fallback;
System.out.println(selected == null); // true
```

Đừng dùng wrapper chứa null ở điều kiện `if`/`while` hoặc các toán tử `&&`, `||` mà không có quy tắc rõ ràng; việc unbox null sẽ ném NPE khi tới lượt đánh giá toán hạng đó.

## 9. Ví dụ với Long, Double và các wrapper số khác

Rủi ro không chỉ xảy ra với `Integer`. Ví dụ:

```java
Long nullableLong = null;
// long x = true ? nullableLong : 0L; // NPE

Double nullableDouble = null;
// double y = true ? nullableDouble : 0.0; // NPE

Float nullableFloat = null;
// float z = true ? nullableFloat : 0.0f; // NPE
```

Ở những ví dụ này, hai nhánh tương ứng trộn wrapper với primitive cùng loại numeric, dẫn tới kết quả primitive và unboxing nếu chọn nhánh null.

Với hai wrapper cùng loại, có thể trả một tham chiếu null:

```java
Long a = null;
Long b = Long.valueOf(0L);
Long x = true ? a : b;
System.out.println(x == null); // true
```

### Trộn numeric wrapper khác loại

Ví dụ `Integer` và `Double` là các kiểu wrapper khác nhau:

```java
Integer count = Integer.valueOf(2);
Double ratio = Double.valueOf(1.5);

double result = true ? count : ratio;
System.out.println(result); // 2.0
```

Trong ví dụ numeric conditional này, Java dùng quy tắc chuyển đổi số để cho kết quả `double`; khi chọn `count` cần unbox `Integer` rồi mở rộng số sang `double`. Nếu `count` là `null`, nhánh được chọn sẽ ném NPE.

**Không dựa vào “cả hai nhánh đều là object nên không unbox”.** Với numeric conditional expression, quy tắc kiểu numeric có thể yêu cầu giá trị primitive ngay cả khi mỗi nhánh bắt đầu từ wrapper.

## 10. Autoboxing lại ở bước cuối có thể che giấu bước unboxing trước đó

Xét chuỗi các bước:

```java
Integer maybe = Integer.valueOf(5);
Integer answer = true ? maybe : 0;
System.out.println(answer); // 5
```

Trong trường hợp này:

1. `maybe` có kiểu `Integer`;
2. nhánh kia là `int 0`;
3. conditional expression có kiểu `int`;
4. khi chọn `maybe`, Java unbox nó thành 5;
5. phép gán tới `Integer answer` có thể boxing lại 5.

Khi `maybe` khác null, chương trình vẫn chạy và người đọc có thể không nhận thấy **unboxing → boxing** đã diễn ra. Chỉ khi giá trị `null` xuất hiện mới bộc lộ NPE.

Vì vậy các bộ test chỉ sử dụng wrapper khác null **không đủ** để chứng minh một biểu thức chọn giá trị là null-safe. Cần test cả nhánh chứa null và các điều kiện true/false tương ứng.

## 11. Cách sửa: định nghĩa rõ hợp đồng null trước khi chọn giá trị

Không có một “bản sửa” duy nhất cho mọi bài toán. Cần xác định một trong ba yêu cầu:

1. **Giữ `null`** để thể hiện “không có giá trị”.
2. **Dùng một fallback** như 0 hoặc 1 khi wrapper null.
3. **Từ chối null** bằng ngoại lệ có thông điệp rõ ràng.

### 11.1. Muốn giữ nguyên null

Sử dụng hai nhánh cùng kiểu wrapper và đích nhận wrapper:

```java
Integer nullable = null;
Integer fallbackBoxed = Integer.valueOf(0);

Integer result = true ? nullable : fallbackBoxed;
System.out.println(result == null); // true
```

Hoặc viết `if/else` để thể hiện rõ:

```java
Integer result;
if (true) {
    result = nullable;
} else {
    result = fallbackBoxed;
}
```

Đoạn `if (true)` chỉ để minh họa luồng điều khiển; trong chương trình thực tế thay bằng điều kiện nghiệp vụ. **Không unbox `result` về sau nếu null vẫn hợp lệ.**

### 11.2. Muốn fallback khi wrapper null

Nếu cần số nguyên primitive khác null:

```java
Integer maybe = null;
int count = maybe != null ? maybe.intValue() : 0;
System.out.println(count); // 0
```

Ở đây **điều kiện kiểm tra chính giá trị wrapper**; khi `maybe == null`, nhánh gọi `intValue()` không được đánh giá. Cách viết rất rõ rằng `null` sẽ biến thành `0` theo quy tắc đã chọn.

Nếu còn một điều kiện nghiệp vụ `choose` nữa:

```java
Integer maybe = null;
boolean choose = true;
int result = choose && maybe != null ? maybe.intValue() : 0;
```

Cần xác định liệu “không chọn” và “chọn nhưng không có giá trị” có thực sự cùng được quy về 0 hay không. Nếu hai trạng thái có ý nghĩa khác nhau, đừng nhập chúng bằng một fallback.

Cũng có thể dùng `Objects.requireNonNullElse` khi cần một wrapper có fallback **khác null**, nhưng vẫn phải hiểu nó là lựa chọn thay thế null theo quy ước, không phải Java tự động áp dụng cho `?:`:

```java
import java.util.Objects;

Integer maybe = null;
Integer result = Objects.requireNonNullElse(
        maybe, Integer.valueOf(0));
System.out.println(result); // 0
```

### 11.3. Muốn từ chối null

```java
import java.util.Objects;

Integer maybe = null;
// int x = Objects.requireNonNull(maybe, "Missing count"); // ném NPE rõ thông điệp
```

Hoặc chủ động ném `IllegalArgumentException` ở ranh giới API nếu null là dữ liệu đầu vào không hợp lệ. Không nên che giấu lỗi bằng catch `NullPointerException` bao trùm một biểu thức lớn rồi âm thầm thay 0: cách đó dễ nuốt các lỗi lập trình khác.

## 12. Khi nào nên viết if/else thay cho ?:

Toán tử `?:` phù hợp khi hai nhánh ngắn, dễ đọc và kiểu kết quả rõ ràng. Khi có nhiều kiểu wrapper, nullability hoặc logic điều kiện phức tạp, `if/else` thường làm **bước unboxing** lộ rõ hơn:

```java
static Integer chooseNullable(boolean choose, Integer input) {
    if (choose) {
        return input;     // có thể trả null
    }
    return Integer.valueOf(0);
}

static int choosePrimitive(boolean choose, Integer input) {
    if (choose) {
        if (input == null) {
            throw new IllegalArgumentException("Missing input");
        }
        return input.intValue();
    }
    return 0;
}
```

Hai phương thức này có **hợp đồng khác nhau**:

- `chooseNullable` có thể trả `null`, cần người gọi xử lý.
- `choosePrimitive` luôn trả `int` hoặc ném một ngoại lệ chủ động nếu không có số cần dùng.

So sánh với phương thức dễ gây hiểu lầm:

```java
static Integer subtle(boolean choose, Integer input) {
    return choose ? input : 0;
}
```

Mặc dù chữ ký trả `Integer`, nhánh `input` vẫn có thể được unbox bởi numeric conditional expression. **Kiểu trả về của phương thức không tự sửa cách xác định kiểu của `?:` bên trong.**

Một thiết kế API tốt nên công bố rõ: tham số/giá trị trả về có cho phép null không, và có fallback nào được áp dụng.

## 13. Không ép kiểu tùy tiện để né NullPointerException

Có thể thấy cách “ép thành Object để Java không unbox”:

```java
Integer nullable = null;
Object selected = true ? (Object) nullable : 0;
System.out.println(selected == null); // true
```

Vì một nhánh có kiểu `Object`, biểu thức này thuộc nhóm **reference conditional expression** và có thể giữ tham chiếu null. Nhưng đó **không phải giải pháp mặc định** cho biến cần kiểu `Integer` hay `int`, vì trả về `Object` làm yếu type safety và có thể yêu cầu cast về sau.

Tương tự, đổi toàn bộ nhánh sang kiểu reference khác mà không nắm quy tắc conditional typing có thể che lỗi tại chỗ này rồi dời sang chỗ khác. Ưu tiên:

- cùng kiểu wrapper khi thật sự muốn giữ null;
- kiểm tra null rõ ràng khi cần primitive;
- `if/else` khi cần nhiều bước xử lý hoặc thông điệp lỗi tốt.

### Cẩn thận với Optional

`Optional<Integer>` biểu diễn việc có hoặc không có số nguyên:

```java
import java.util.Optional;

Integer maybe = null;
int count = Optional.ofNullable(maybe).orElse(0);
System.out.println(count); // 0
```

Đây là lựa chọn cho **một số ranh giới API**, không cần thiết cho mọi phép cộng đơn giản. `Optional` cũng không “trị” mọi lỗi null: vẫn cần chọn fallback, ném lỗi hoặc xử lý trường hợp vắng mặt một cách có chủ đích.

## 14. Java 17+ demo chạy độc lập

Tạo tệp **`ConditionalUnboxingDemo.java`**:

```java
import java.util.Objects;

public class ConditionalUnboxingDemo {
    private static Integer chooseNullable(boolean choose, Integer value) {
        return choose ? value : Integer.valueOf(0);
    }

    private static Integer subtle(boolean choose, Integer value) {
        return choose ? value : 0;
    }

    private static void runCase(String name, Runnable action) {
        try {
            action.run();
        } catch (NullPointerException ex) {
            System.out.println(name + ": NullPointerException");
        }
    }

    public static void main(String[] args) {
        Integer missing = null;

        runCase("selected Integer+int", () -> {
            Integer result = true ? missing : 0;
            System.out.println(result);
        });

        Integer notSelected = false ? missing : 0;
        System.out.println("unselected null = " + notSelected);

        Integer keptNull = true
                ? missing
                : Integer.valueOf(0);
        System.out.println("two Integer branches null = "
                + (keptNull == null));

        Integer fromMethod = chooseNullable(true, missing);
        System.out.println("nullable method null = "
                + (fromMethod == null));

        runCase("method returning Integer", () -> {
            System.out.println(subtle(true, missing));
        });

        runCase("assignment to int", () -> {
            Integer left = null;
            Integer right = Integer.valueOf(0);
            int number = true ? left : right;
            System.out.println(number);
        });

        int safe = missing != null ? missing.intValue() : 0;
        System.out.println("explicit fallback = " + safe);

        Integer fallback = Objects.requireNonNullElse(
                missing, Integer.valueOf(0));
        System.out.println("Objects fallback = " + fallback);

        runCase("Boolean condition", () -> {
            Boolean enabled = null;
            int status = enabled ? 1 : 0;
            System.out.println(status);
        });

        runCase("Long+long", () -> {
            Long amount = null;
            long total = true ? amount : 0L;
            System.out.println(total);
        });

        Integer a = Integer.valueOf(2);
        Double b = Double.valueOf(1.5);
        double mixed = true ? a : b;
        System.out.println("mixed numeric = " + mixed);

        Object keptObject = true ? (Object) missing : 0;
        System.out.println("Object branch null = "
                + (keptObject == null));
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac ConditionalUnboxingDemo.java
java ConditionalUnboxingDemo
```

**Đầu ra dự kiến trên Java 17+:**

```text
selected Integer+int: NullPointerException
unselected null = 0
two Integer branches null = true
nullable method null = true
method returning Integer: NullPointerException
assignment to int: NullPointerException
explicit fallback = 0
Objects fallback = 0
Boolean condition: NullPointerException
Long+long: NullPointerException
mixed numeric = 2.0
Object branch null = true
```

**Giải thích:**

1. `true ? missing : 0` tạo numeric conditional type `int`, nên phải unbox `missing` và ném NPE.
2. `false ? missing : 0` không đánh giá nhánh null; kết quả 0.
3. Hai nhánh `Integer` tạo giá trị tham chiếu, vì vậy có thể giữ null; nhưng gán kết quả vào `int` vẫn yêu cầu unbox.
4. Phương thức trả `Integer` không tự ngăn unboxing nếu biểu thức bên trong trộn `Integer` và `int`.
5. Kiểm tra `missing != null`, `Objects.requireNonNullElse` là hai cách định nghĩa fallback 0 rõ ràng.
6. `Boolean` null ở phần điều kiện và `Long` null được chọn trong numeric conditional đều có thể ném NPE.
7. Trộn `Integer` và `Double` tạo numeric result có thể được xử lý bằng chuyển kiểu số; chọn nhánh `Integer 2` cho kết quả `double 2.0`.
8. Ép nhánh sang `Object` minh họa reference conditional expression có thể giữ null, nhưng phải cân nhắc type safety.

## 15. Bảng phân biệt các tình huống thường gặp

Trong bảng này, `n` là biến `Integer n = null;`; `zero` là `Integer zero = Integer.valueOf(0);`.

| Biểu thức / hoàn cảnh | Kiểu hoặc chuyển đổi cốt lõi | Khi chạy |
| --- | --- | --- |
| `Integer x = true ? n : 0;` | `Integer` + `int` → kết quả `int` rồi boxing khi gán | **NPE** |
| `Integer x = false ? n : 0;` | Chọn nhánh primitive `int` | `x = 0` |
| `int x = true ? n : 0;` | Chọn `n` rồi unbox | **NPE** |
| `Object x = true ? n : 0;` | Toán tử numeric trước khi boxing sang Object | **NPE** |
| `Integer x = true ? n : zero;` | Hai nhánh cùng `Integer` | `x = null` |
| `int x = true ? n : zero;` | Toán tử giữ `Integer` nhưng gán sang primitive | **NPE tại gán** |
| `Integer x = n != null ? n : zero;` | Có kiểm tra null và nhánh được chọn cùng `Integer` | `x = 0` |
| `int x = n != null ? n.intValue() : 0;` | Unbox chỉ trong nhánh đã kiểm tra | `x = 0` |
| `boolean x = ((Boolean) null) ? true : false;` | Điều kiện phải unbox Boolean | **NPE trước chọn nhánh** |
| `Object x = true ? (Object)n : 0;` | Một nhánh ép Object → reference conditional | `x = null` |

**Độ chính xác về thời điểm:** cùng là NPE nhưng có thể xảy ra ở **điều kiện**, ở **nhánh được chọn** trong biểu thức `?:`, hoặc ở **bước chuyển kiểu sau khi toán tử trả giá trị**. Sửa lỗi nên bắt đầu bằng xác định đúng bước ấy.

## 16. Những hiểu lầm dễ dẫn đến lỗi

| Hiểu lầm | Giải thích đúng |
| --- | --- |
| “Biến nhận kiểu `Integer` nghĩa là không unbox” | Kiểu `?:` có thể là `int` trước khi boxing vào biến nhận |
| “Nhánh `null` không bao giờ gây NPE vì null là reference” | Nếu nhánh wrapper được chọn cần unbox, null gây NPE |
| “Toán tử `?:` luôn đánh giá cả hai nhánh” | Chỉ một nhánh được đánh giá; nhánh không chọn không gây tác dụng phụ runtime |
| “Cả hai nhánh đều wrapper thì luôn trả wrapper” | Với **cùng kiểu wrapper**, có thể giữ reference; nhưng khác loại numeric wrapper có thể bị numeric promotion |
| “Đổi `0` thành `Integer.valueOf(0)` luôn xóa mọi NPE” | Chỉ ngăn unboxing tại `?:` trong trường hợp hai nhánh cùng Integer; nếu đích là `int` vẫn có thể NPE |
| “`Objects.equals(n, 0)` giống `n == 0` khi n null” | `Objects.equals` null-safe, còn `n == 0` có thể unbox null |
| “Cứ cast Object thì kiểu nào cũng an toàn” | Có thể giữ null tại bước chọn, nhưng mất type safety và còn lỗi khi cast/unbox sau |
| “Boolean null tự được coi là false” | Không; dùng làm điều kiện cần unbox và có thể ném NPE |
| “Nếu code chạy với các giá trị khác null thì ?: an toàn” | Phải kiểm thử nhánh wrapper null và nhánh đối diện |
| “Compiler sẽ chọn loại nhánh dựa trên giá trị true/false đang biết” | Type-checking xác định kiểu từ hai operand theo quy tắc ngôn ngữ, bất kể nhánh nào được chọn khi chạy |
| “Chỉ cần catch NPE ở cuối là được” | NPE có thể phản ánh lỗi khác; cần kiểm tra null và nêu hợp đồng tại chỗ dùng dữ liệu |

## 17. Bài tập tự kiểm tra và lời giải

**Bài 1.** Với `Integer n = null;`, đánh giá `Integer r = true ? n : 0;`. Vì sao không chỉ nhận null?  
**Đáp án:** Hai nhánh `Integer` và `int` tạo numeric conditional type `int`. Chọn `n` cần unbox null, ném `NullPointerException` trước khi có thể boxing kết quả để gán.

**Bài 2.** `Integer r = false ? n : 0;` (với `n=null`) có NPE không?  
**Đáp án:** Không. Java chỉ đánh giá nhánh `0` vì điều kiện false; kết quả sau boxing là `Integer 0`.

**Bài 3.** `Integer z = Integer.valueOf(0); Integer r = true ? n : z;` có NPE khi `n=null` không?  
**Đáp án:** Không tại bước chọn/gán: cả hai nhánh đều là `Integer`, biểu thức trả tham chiếu `null`, `r=null`. Nhưng nếu dùng `int x=r;` sau đó sẽ NPE.

**Bài 4.** Vì sao `int x = true ? n : z;` vẫn có NPE, dù hai nhánh cùng Integer?  
**Đáp án:** Toán tử `?:` có thể trả `Integer null`, nhưng phép gán vào `int` buộc unbox tham chiếu null.

**Bài 5.** Với `Boolean b=null;`, `int x=b ? 1:0;` báo lỗi ở bước nào?  
**Đáp án:** Tại unboxing của **điều kiện** `b` để lấy primitive `boolean`, trước khi chọn một trong hai nhánh số.

**Bài 6.** Sửa `int r = choose ? nullable : 0;` theo yêu cầu: null được coi là 0.  
**Đáp án:** `int r = choose && nullable != null ? nullable.intValue() : 0;`, với `choose` là boolean và `nullable` có kiểu `Integer`. Điều kiện kiểm tra null trước khi gọi `intValue()`.

**Bài 7.** Sửa cùng biểu thức theo yêu cầu: khi choose=true và nullable=null, **phải trả null**.  
**Đáp án:** Kết quả phải là `Integer` và hai nhánh cùng wrapper: `Integer r = choose ? nullable : Integer.valueOf(0);`. Người gọi tiếp tục chịu trách nhiệm xử lý null.

**Bài 8.** `Object o = true ? nullable : 0;` có luôn tránh NPE không?  
**Đáp án:** Không. Kiểu của numeric conditional được xác định từ nhánh `Integer`/`int` trước, có thể unbox null. Việc gán vào `Object` không sửa lại bước đã xảy ra.

**Bài 9.** Nếu nhánh không được chọn chứa `Integer null` hoặc một phương thức ném lỗi, nó có chạy không?  
**Đáp án:** Không. Toán tử chỉ đánh giá một nhánh đã chọn; nhưng hai nhánh vẫn phải hợp lệ về cú pháp và kiểu khi biên dịch.

**Bài 10.** Khi review PR, thấy `return condition ? boxed : 0;` trong phương thức trả `Integer`. Cần kiểm tra điều gì?  
**Đáp án:** Kiểm tra `boxed` có thể null không, vì `?:` có thể là kiểu `int` và unbox trước khi boxing trả về. Nếu null được phép, dùng `Integer.valueOf(0)` làm nhánh còn lại hoặc viết `if/else` để giữ kiểu wrapper; nếu null không hợp lệ, kiểm tra và báo lỗi rõ ràng.

## 18. Checklist khi dùng ? với wrapper

1. Xác định kiểu của **condition**: `boolean` hay `Boolean` có thể null?
2. Xác định kiểu **từng nhánh**, không chỉ nhìn biến nhận: primitive, wrapper cùng loại hay wrapper numeric khác loại?
3. Xác định kiểu kết quả **của chính `?:`** theo quy tắc Java; không giả định vế gán `Integer` đồng nghĩa kết quả trước đó là wrapper.
4. Hỏi nhánh wrapper có thể `null` không; nếu được chọn, có cần unbox không?
5. Hỏi **đích sử dụng kết quả** có unbox tiếp không, ngay cả nếu `?:` giữ reference?
6. Nếu muốn null được giữ, dùng **hai nhánh cùng kiểu wrapper** và đích wrapper, đồng thời ghi rõ hợp đồng nullability.
7. Nếu muốn primitive bắt buộc, **kiểm tra null trước** hoặc từ chối dữ liệu với ngoại lệ có chủ đích.
8. Nếu cần fallback, ghi rõ đó là **quy tắc nghiệp vụ**; 0 không tự đồng nghĩa “không có giá trị”.
9. Viết kiểm thử đủ các trường hợp **condition true/false**, nhánh wrapper null/non-null, giá trị đã boxing và nested ternary.
10. Khi biểu thức phức tạp hoặc gây tranh luận về kiểu, **dùng `if/else`** để làm rõ ý định.

**Ghi nhớ:** `?:` chỉ đánh giá **một nhánh**, nhưng kiểu kết quả đã được chọn theo quy tắc khi biên dịch. Nếu kiểu ấy yêu cầu **primitive**, nhánh wrapper được chọn có thể bị **unbox tự động** và ném `NullPointerException` khi mang `null`—ngay cả khi biến nhận hoặc kiểu trả về cuối là wrapper. Muốn viết an toàn, hãy kiểm tra null và làm rõ kiểu của cả **biểu thức `?:`** lẫn nơi tiêu thụ kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-010">Không dùng wrapper làm khóa đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng wrapper làm khóa đồng bộ trong Java

**Nguyên tắc:** Không dùng các đối tượng wrapper như `Integer`, `Long`, `Boolean`, `Double`, `Character` làm đối tượng khóa cho `synchronized`. Khi cần bảo vệ dữ liệu chia sẻ giữa các luồng, hãy dùng một **đối tượng khóa chuyên dụng, ổn định về danh tính**, chẳng hạn `private final Object lock = new Object()`, hoặc dùng công cụ đồng thời phù hợp như `AtomicInteger`, `ReentrantLock`.

Wrapper có mục đích **biểu diễn giá trị primitive dưới dạng đối tượng**, không phải đại diện cho một tài nguyên dùng chung có danh tính ổn định để đồng bộ. Việc boxing, cache đối tượng và tính bất biến của wrapper khiến khóa trên nó dễ tạo lỗi tinh vi.

Ví dụ **không nên dùng trong ứng dụng**:

~~~java
Integer count = 0;

// KHÔNG NÊN: có thể khóa trên đối tượng wrapper được cache.
synchronized (count) {
    count++; // Unboxing -> cộng -> boxing lại; count trỏ sang đối tượng khác.
}
~~~

Đây là **đoạn minh họa mã sai**, không phải chương trình chạy độc lập. Trên JDK hiện đại, trình biên dịch có thể phát cảnh báo khi `synchronized` trực tiếp trên lớp value-based. Việc đoạn mã có thể biên dịch không có nghĩa đồng bộ đúng.

## 1. Monitor của synchronized gắn với đối tượng, không gắn với giá trị số

Trong Java, khối:

~~~java
synchronized (lock) {
    // Vùng tới hạn.
}
~~~

sử dụng **monitor** của đối tượng mà biểu thức `lock` đánh giá ra tại thời điểm đi vào khối. Hai luồng chỉ loại trừ nhau khi chúng cố gắng giữ **cùng một monitor của cùng một đối tượng**.

- Hai tham chiếu trỏ đến **cùng đối tượng**: dùng cùng monitor, có thể chặn nhau.
- Hai đối tượng **khác nhau**, dù nội dung số học bằng nhau: có hai monitor khác nhau, **không** loại trừ nhau.
- Biến tham chiếu `lock` bị gán lại: lần `synchronized(lock)` sau có thể khóa một đối tượng khác. Monitor của lần trước không tự chuyển sang đối tượng mới.

Đồng bộ không dựa vào `equals()`, `hashCode()`, giá trị số hoặc kiểu `int` được unbox. Nó dựa vào **danh tính tham chiếu** của đối tượng mà JVM khóa. Đồng thời, quan hệ *happens-before* giữa lần mở khóa và lần khóa tiếp theo được thiết lập khi làm việc trên **cùng monitor**, chứ không trên hai đối tượng chỉ có cùng giá trị.

Đây chính là lý do wrapper, vốn được tạo để đại diện cho **giá trị**, không phải là lựa chọn khóa an toàn.

## 2. Boxing, unboxing và vòng đời đối tượng wrapper

Các wrapper phổ biến của primitive:

| Primitive | Wrapper |
| --- | --- |
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

Với boxing, một primitive trở thành một đối tượng wrapper; unboxing lấy giá trị primitive ra.

~~~java
Integer n = 42;       // Autoboxing: int -> Integer
int rawValue = n;     // Unboxing: Integer -> int
n = n + 1;            // Unbox n, cộng int, box 43, rồi gán lại n
~~~

Các wrapper là **immutable**: giá trị số bên trong một đối tượng `Integer` không được sửa. Khi dùng `n++`, Java không tăng “tại chỗ” giá trị của đối tượng Integer; thay vào đó, nó tính ra giá trị primitive mới và cập nhật **tham chiếu** của biến `n` sau boxing.

Điều này đặc biệt nguy hiểm nếu chính biến wrapper đó được dùng làm lock. Mỗi lần cập nhật số có thể đổi đối tượng mà các lần khóa sau sử dụng.

### 2.1. Cached wrapper có thể chia sẻ danh tính

Java thường tái sử dụng wrapper cho một số giá trị nhất định. Ví dụ, `Integer.valueOf(int)` có bảo đảm cache ít nhất các giá trị từ **-128 đến 127**:

~~~java
Integer a = Integer.valueOf(42);
Integer b = Integer.valueOf(42);

System.out.println(a == b);       // true: cùng đối tượng trong miền cache
System.out.println(a.equals(b));  // true: cùng giá trị số
~~~

Vì vậy:

~~~java
// KHÔNG NÊN: hai đoạn chương trình không liên quan có thể dùng cùng monitor!
synchronized (Integer.valueOf(42)) {
    // ...
}
~~~

Hai thành phần không liên quan cùng khóa `Integer.valueOf(42)` có thể vô tình **chặn nhau**. Nếu chúng giữ thêm khóa khác theo những thứ tự khác nhau, việc chia sẻ lock không chủ ý còn góp phần tạo tình huống deadlock. Cũng có nguy cơ một thành phần bên ngoài can thiệp vào lịch thực thi của thành phần kia, vì đối tượng cache không thuộc quyền sở hữu riêng của bạn.

**Không được đảo ngược kết luận:** wrapper nằm *ngoài* miền cache không có nghĩa JVM chắc chắn trả đối tượng mới. Ví dụ, đừng dựa vào kết quả của `Integer.valueOf(1000) == Integer.valueOf(1000)` để xác định an toàn khóa; khả năng tái sử dụng đối tượng là chi tiết không phù hợp làm hợp đồng đồng bộ.

Các loại wrapper khác cũng có quy tắc boxing/cache riêng, và một số wrapper có thể được tái sử dụng. Bài học cốt lõi không phụ thuộc cache cụ thể: **wrapper được thiết kế theo ngữ nghĩa giá trị, không theo ngữ nghĩa danh tính khóa ổn định**.

### 2.2. Tạo wrapper mới không phải lời giải đúng

Từng có thể viết `new Integer(42)` để cố tạo đối tượng riêng. Nhưng constructor wrapper kiểu này đã bị **deprecated for removal**, và bản thân wrapper được coi là **value-based**. Việc dùng chúng làm lock vẫn sai về mục đích, khó đọc và không phù hợp hướng phát triển của Java.

Muốn có monitor riêng, hãy tạo hẳn:

~~~java
private final Object lock = new Object();
~~~

Đây là đối tượng có chức năng làm khóa, không mang ý nghĩa số học và không bị vô tình tái tạo bởi phép tăng giá trị.

## 3. Lỗi số một: khóa thay đổi khi tăng biến wrapper

Xem mã **không an toàn** sau (ví dụ minh họa, không áp dụng):

~~~java
class UnsafeCounter {
    private Integer count = 0;

    void increment() {
        synchronized (count) { // Khóa đối tượng count tại thời điểm này
            count++;           // count được unbox, tăng, box, gán lại
        }
    }

    int get() {
        return count;          // Đọc chưa có chính sách đồng bộ nhất quán
    }
}
~~~

Phân tích:

1. Ban đầu `count` tham chiếu tới wrapper cho số 0; luồng A vào `synchronized(count)` và giữ monitor của **đối tượng 0**.
2. Phép `count++` tạo giá trị số 1 và khiến `count` tham chiếu một wrapper đại diện cho 1.
3. Luồng B có thể vào `synchronized(count)` lúc này và giữ monitor của **đối tượng 1**, trong khi A vẫn chưa thoát khỏi vùng tới hạn được bảo vệ bởi monitor số 0.
4. Hai luồng không nhất thiết loại trừ nhau vì đang khóa **hai đối tượng khác nhau**. Phép tăng dự định an toàn có thể bị race, mất lượt tăng; việc đọc `get()` cũng không được bảo vệ.
5. Việc cache các wrapper không sửa lỗi này: cache có thể khiến hai đối tượng với **cùng** giá trị chia sẻ monitor, nhưng khi giá trị thay đổi, đối tượng khóa thường cũng thay đổi.

Lưu ý: mã có thể đồng thời kích hoạt cảnh báo của compiler về đồng bộ trên đối tượng value-based. **Race không phải lúc nào cũng xuất hiện trong một lần chạy**; lỗi là không có bảo đảm tương tranh đúng theo Java Memory Model.

### 3.1. final đặt lên wrapper chứa khóa cũng không giải quyết tận gốc

~~~java
// Không nên dùng làm lock, kể cả khi tham chiếu final:
private final Integer lock = Integer.valueOf(42);
~~~

`final` giúp không gán lại tham chiếu `lock` trong instance, nhưng **không khiến đối tượng Integer đó trở thành tài nguyên độc quyền**. Nó có thể là đối tượng cache dùng chung với mã khác. Và quy tắc không khóa trên value-based wrapper vẫn áp dụng.

## 4. Wrapper là value-based class: cảnh báo từ Java hiện đại

Các primitive wrapper của JDK được mô tả là **value-based classes**. Các lớp này đại diện cho giá trị; lập trình viên không được dựa vào danh tính đối tượng của chúng để phối hợp hệ thống. Tài liệu nền tảng Java cảnh báo rõ việc dùng đối tượng value-based để đồng bộ.

Từ JDK 16, qua JEP 390, công cụ biên dịch có hỗ trợ **cảnh báo cho phép đồng bộ trên đối tượng value-based**. Khi viết:

~~~java
Integer guard = Integer.valueOf(42);
// synchronized (guard) { /* Không nên */ }
~~~

Nếu bỏ comment ở dòng `synchronized`, `javac` trên JDK hiện đại có thể cảnh báo kiểu “attempt to synchronize on an instance of a value-based class”; có thể quan sát bằng tùy chọn kiểm tra liên quan `-Xlint:synchronization`. Nội dung cảnh báo cụ thể thay đổi theo phiên bản/cấu hình biên dịch.

Cảnh báo không đơn thuần là gợi ý phong cách: nó phản ánh vấn đề tương thích với cách Java phát triển các lớp định hướng giá trị. Một số chế độ JVM chẩn đoán nâng cao có thể cảnh báo hoặc xử lý nghiêm ngặt hơn khi bắt đồng bộ trên value-based instance. **Không lấy việc hiện tại code vẫn chạy làm bằng chứng có thể dùng wrapper làm khóa lâu dài**.

Nên phân biệt các khái niệm:

- `wrapper.equals(other)`: so sánh nội dung theo hợp đồng của lớp cụ thể.
- `wrapper == otherWrapper` khi cả hai là tham chiếu: so sánh **danh tính đối tượng**; cache làm kết quả có thể gây bất ngờ.
- `synchronized(wrapper)`: khóa monitor của **đối tượng wrapper**, không dùng giá trị số học.
- `synchronized(lockObject)` với lock chuyên dụng: khóa monitor được tạo để phục vụ bảo vệ trạng thái chia sẻ.

Nếu mục tiêu là bảo vệ một counter hoặc trạng thái, định danh của counter được bảo vệ không phải là chính giá trị số của nó.

## 5. Cách đúng: private final Object lock

Mẫu thông thường:

~~~java
class SafeCounter {
    private final Object lock = new Object();
    private int value;

    void increment() {
        synchronized (lock) {
            value++;
        }
    }

    int get() {
        synchronized (lock) {
            return value;
        }
    }
}
~~~

Đây là **đoạn lớp minh họa** có thể đặt trong tệp Java. Vì `lock` được tạo bằng `new Object()` và giữ ở trường `private final`, các phương thức của **cùng một instance** sử dụng monitor ổn định. Không có code khác lấy được tham chiếu `lock` qua API công khai, nên việc chặn nhau diễn ra trong phạm vi dự kiến.

Phải đảm bảo **mọi truy cập liên quan đến cùng invariant dữ liệu đều tuân theo giao thức khóa**. Việc chỉ khóa ở phương thức ghi nhưng đọc ở nơi khác không theo một cơ chế visibility phù hợp có thể làm mất bảo đảm bộ nhớ.

Với trạng thái `static` được chia sẻ giữa nhiều instance, khóa cũng cần thuộc về trạng thái `static` đó:

~~~java
class GlobalCounter {
    private static final Object LOCK = new Object();
    private static int value;

    static void increment() {
        synchronized (LOCK) {
            value++;
        }
    }

    static int get() {
        synchronized (LOCK) {
            return value;
        }
    }
}
~~~

Một lock instance bảo vệ trạng thái instance; lock static bảo vệ trạng thái static. Không nên dùng `synchronized(this)` hoặc `synchronized(SomeClass.class)` một cách tùy tiện nếu đối tượng monitor đó có thể được mã bên ngoài sử dụng và gây tranh chấp không mong muốn. Khóa chuyên dụng với phạm vi `private` thường rõ ràng hơn.

## 6. Cách đúng khác: AtomicInteger, AtomicLong và LongAdder

Nếu chỉ cần một counter độc lập, thay vì `Integer` và `synchronized` có thể dùng `AtomicInteger`:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger();

    void increment() {
        count.incrementAndGet();
    }

    int get() {
        return count.get();
    }
}
~~~

`AtomicInteger.incrementAndGet()` thực hiện cập nhật nguyên tử và cung cấp các bảo đảm visibility theo hợp đồng của lớp. Nó không yêu cầu người gọi tự chọn monitor. `AtomicLong` phù hợp nếu cần miền `long`, nhưng vẫn có tràn số khi vượt miền.

Với counter thống kê được cập nhật rất thường xuyên và tranh chấp cao, `LongAdder` thường là lựa chọn đáng cân nhắc. Tuy nhiên `LongAdder.sum()` không tạo snapshot nguyên tử trong lúc các luồng khác cập nhật. Vì vậy:

- `AtomicInteger`/`AtomicLong`: thích hợp khi cần thao tác nguyên tử trên **một biến** và giá trị đọc nhất quán với các cập nhật nguyên tử đó.
- `LongAdder`: thường tối ưu cho thống kê/cộng dồn nhiều luồng, không thay thế mọi giao thức đồng bộ cần snapshot tức thời.
- Một `Object lock` chuyên dụng hoặc `ReentrantLock`: phù hợp khi cần bảo vệ **nhiều trường/điều kiện liên quan** trong cùng một giao dịch logic.

Dùng atomic counter cũng không có nghĩa mọi tổ hợp nhiều thao tác riêng rẽ đều tự động nguyên tử. Ví dụ, kiểm tra `get()` rồi quyết định cập nhật bằng một lệnh riêng có thể race; cần xem `compareAndSet` hoặc thiết kế khóa phù hợp.

## 7. Cách đúng khác: ReentrantLock

Khi cần kiểm soát cấp cao hơn như `tryLock()`, khóa có timeout hay khả năng ngắt khi chờ khóa, JDK cung cấp `java.util.concurrent.locks.ReentrantLock`.

~~~java
import java.util.concurrent.locks.ReentrantLock;

class LockBasedCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count;

    void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock(); // Quan trọng: luôn giải phóng, kể cả khi có lỗi.
        }
    }

    int get() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}
~~~

Đây là lớp minh họa độc lập về API; muốn chạy, đặt trong một tệp Java và gọi các phương thức từ chương trình có `main`. Với `ReentrantLock`, tuyệt đối không quên `unlock()` trong `finally`. `synchronized` tự giải phóng monitor khi thoát block, kể cả khi có exception.

Không cần chuyển toàn bộ `synchronized` sang `ReentrantLock` nếu không có nhu cầu. Một `private final Object` đơn giản thường là giải pháp đủ tốt và dễ kiểm toán.

## 8. Ví dụ Java 17+ chạy độc lập

Tạo tệp `WrapperLockDemo.java`. Ví dụ **không đồng bộ trực tiếp trên wrapper**; chỉ chứng minh boxing/caching rồi cho bốn luồng cập nhật bằng hai cách an toàn.

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class WrapperLockDemo {
    static final class SafeCounter {
        private final Object lock = new Object();
        private int value;

        void increment() {
            synchronized (lock) {
                value++;
            }
        }

        int get() {
            synchronized (lock) {
                return value;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Integer a = Integer.valueOf(42);
        Integer b = Integer.valueOf(42);
        System.out.println("cached identity: " + (a == b));
        System.out.println("numeric equality: " + a.equals(b));

        Integer boxed = 42;
        Integer before = boxed;
        boxed++;
        System.out.println("after increment: " + boxed);
        System.out.println("same boxed object: " + (before == boxed));

        SafeCounter counter = new SafeCounter();
        AtomicInteger atomic = new AtomicInteger();

        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 1_000; j++) {
                    counter.increment();
                    atomic.incrementAndGet();
                }
            });
        }

        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("lock counter: " + counter.get());
        System.out.println("atomic counter: " + atomic.get());
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac WrapperLockDemo.java
java WrapperLockDemo
~~~

Kết quả:

~~~text
cached identity: true
numeric equality: true
after increment: 43
same boxed object: false
lock counter: 4000
atomic counter: 4000
~~~

**Phân tích:**

1. `Integer.valueOf(42)` cho cùng đối tượng wrapper trong miền cache được bảo đảm. Vì vậy biểu thức danh tính `a == b` là `true`; đây chính là lý do đối tượng đó không phải lock riêng.
2. Sau `boxed++`, biến `boxed` đại diện cho 43, còn `before` vẫn trỏ wrapper đại diện cho 42. Hai tham chiếu khác nhau; giá trị wrapper cũ không đổi.
3. `SafeCounter` dùng `private final Object` ổn định. Từng lượt tăng chạy dưới **cùng monitor** cho cùng counter.
4. `AtomicInteger` có `incrementAndGet()` nguyên tử. Bốn luồng tăng 1.000 lần mỗi luồng; sau khi `join()` tất cả, cả hai bộ đếm đều cho 4.000.
5. Ví dụ cố ý không mô phỏng chương trình race bằng `synchronized(Integer)` vì kết quả race có thể khác nhau giữa các lần chạy và tạo thói quen sai.

Lệnh `join()` làm luồng chính chờ các worker kết thúc trước khi đọc kết quả, nên đầu ra bộ đếm có thể dự đoán được. Nó không thay thế nhu cầu bảo vệ các lượt tăng trong chính các worker.

## 9. Sai lầm thường gặp và cách nhận biết

| Cách viết | Vấn đề | Cách thay phù hợp |
| --- | --- | --- |
| `synchronized (Integer.valueOf(1))` | Đối tượng cache dùng chung ngoài ý muốn | `private final Object lock = new Object()` |
| `synchronized (count) { count++; }` với `Integer count` | `count` có thể đổi tham chiếu khi tăng | `int` + lock riêng hoặc `AtomicInteger` |
| `private final Integer lock = 42` | `final` không bảo đảm monitor độc quyền | Tạo `new Object()` cho khóa |
| `synchronized (Boolean.TRUE)` | Đối tượng dùng chung, phạm vi khóa vượt module | Private lock riêng |
| `synchronized (Double.valueOf(x))` | Wrapper value-based; danh tính không nên làm khóa | Private lock riêng |
| `volatile Integer count; count++;` | `volatile` không làm `++` nguyên tử | AtomicInteger hoặc synchronized đúng |
| Chỉ khóa khi ghi nhưng đọc trạng thái không phối hợp | Thiếu bảo đảm visibility/happens-before | Đồng bộ cả đọc và ghi theo một giao thức |
| Dùng `ReentrantLock.lock()` nhưng không `unlock()` an toàn | Có thể giữ khóa mãi khi có exception | `try/finally` |
| Thay `Integer` bằng `new Integer(...)` để làm khóa | Constructor deprecated, sai tinh thần value-based | `new Object()`, không tạo wrapper làm monitor |

### 9.1. Vì sao volatile không cứu được việc tăng wrapper?

~~~java
// VẪN SAI nếu nhiều luồng đồng thời:
private volatile Integer value = 0;

void increment() {
    value++; // Đọc -> unbox -> cộng -> box -> ghi, không nguyên tử.
}
~~~

Đây là đoạn minh họa đặt trong lớp, không phải chương trình chạy độc lập. `volatile` cung cấp các bảo đảm visibility và thứ tự đọc/ghi của chính trường volatile, nhưng **không gộp toàn bộ chuỗi đọc–tính–ghi thành một thao tác nguyên tử**.

### 9.2. Vì sao public lock cũng không lý tưởng?

Nếu dùng một đối tượng monitor mà thành phần bên ngoài có thể truy cập, ví dụ `synchronized(this)` của một object công khai, mã ngoài cũng có thể `synchronized(obj)` và làm chương trình tranh chấp khóa ngoài dự kiến. Do đó, ngay cả khi không dùng wrapper, việc giữ lock ở một trường `private final` thường làm mô hình sở hữu khóa rõ ràng hơn.

### 9.3. Có được đồng bộ trên wrapper nếu chỉ chạy một luồng?

Khi chỉ có một luồng, `synchronized` thường không giải quyết nhu cầu chia sẻ đồng thời nào. Nhưng dùng wrapper làm lock vẫn là thiết kế không phù hợp; mã có thể được tái sử dụng trong bối cảnh nhiều luồng hoặc thay đổi phiên bản JDK. Hãy sửa sang lock chuyên dụng thay vì phụ thuộc giả định tình cờ hiện tại.

## 10. Bài tập tự kiểm tra

**Câu 1.** Hai module khác nhau đều dùng `synchronized(Integer.valueOf(42))`. Chúng có thể chặn nhau không dù không chia sẻ biến riêng?  
**Đáp án:** Có. `Integer.valueOf(42)` được cache; cả hai có thể khóa cùng một đối tượng. Đây là khóa dùng chung ngoài ý muốn.

**Câu 2.** Tại sao `private final Integer lock = 42` chưa phải lock tốt?  
**Đáp án:** `final` ổn định **tham chiếu của trường**, nhưng đối tượng wrapper có thể là cache dùng chung với nơi khác. Wrapper value-based không được thiết kế làm monitor. Dùng `private final Object lock = new Object()`.

**Câu 3.** Mã `synchronized(count) { count++; }` có bảo vệ được `count++` khi `count` là `Integer` không?  
**Đáp án:** Không đảm bảo. `count++` unbox, cộng, box và gán tham chiếu mới; những luồng sau có thể khóa monitor khác. Các lượt tăng có thể xảy ra đồng thời và mất cập nhật.

**Câu 4.** Có thể thay `Integer count` bằng `volatile Integer count` rồi tăng bằng `count++` cho an toàn không?  
**Đáp án:** Không. `volatile` đảm bảo một số quan hệ visibility nhưng không làm chuỗi đọc–cộng–ghi thành một phép tăng nguyên tử. Dùng `AtomicInteger.incrementAndGet()` hoặc một lock chuyên dụng.

**Câu 5.** Khi nào chọn `private final Object lock` thay vì `AtomicInteger`?  
**Đáp án:** Khi cần bảo vệ nhiều biến cùng lúc, duy trì một invariant liên quan hoặc chạy nhiều bước trong một vùng tới hạn. `AtomicInteger` phù hợp với thao tác nguyên tử trên một biến số nguyên.

**Câu 6.** Có nên chuyển mọi counter sang `LongAdder`?  
**Đáp án:** Không. `LongAdder` có lợi trong nhiều tình huống thống kê tranh chấp cao, nhưng `sum()` không phải snapshot nguyên tử khi có cập nhật đồng thời. Với kiểm tra–cập nhật nhất quán, cần thiết kế khác.

**Câu 7.** Điều kiện quan trọng nhất để `synchronized` loại trừ lẫn nhau là gì?  
**Đáp án:** Các luồng phải cố khóa **cùng monitor trên cùng đối tượng**, và mọi truy cập cần bảo vệ phải tuân theo cùng giao thức khóa. Không đủ nếu chỉ có hai wrapper chứa cùng giá trị số.

## 11. Ghi nhớ

- `Integer`, `Long`, `Boolean`, `Double` và các primitive wrapper là **value-based**, không phải đối tượng được thiết kế để dùng danh tính làm khóa.
- Cache/autoboxing có thể khiến wrapper bị **chia sẻ vô tình**; unboxing rồi boxing khi cập nhật có thể khiến biến wrapper **đổi tham chiếu khóa**.
- `synchronized` khóa **monitor của đối tượng**, không khóa một giá trị `int` hay một biểu thức số học trừu tượng.
- Chọn `private final Object lock = new Object()` cho vùng tới hạn có trạng thái liên quan; dùng `AtomicInteger`/`AtomicLong` cho phép cập nhật nguyên tử trên một biến, cân nhắc `ReentrantLock` cho yêu cầu khóa nâng cao.
- Tránh dùng `volatile` hay `final Integer` như “mẹo chữa” việc khóa bằng wrapper; chúng không giải quyết hợp đồng danh tính monitor.
- Khi thấy cảnh báo “synchronization on an instance of a value-based class”, hãy **thay mô hình khóa**, không chỉ tắt cảnh báo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boxing-and-unboxing-011">Phân biệt tham chiếu null với giá trị số 0</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tham chiếu null với giá trị số 0 trong Java

## 1. Vấn đề: “không có giá trị” khác “có giá trị bằng 0”

Trong Java, `null` và số `0` biểu thị hai trạng thái khác nhau:

- **`null`**: biến có kiểu tham chiếu hiện không trỏ tới đối tượng nào. Đây có thể là dữ liệu chưa được cung cấp, chưa biết, không áp dụng hoặc một lỗi trong việc khởi tạo, tùy hợp đồng của ứng dụng.
- **`0`**: một giá trị số thực sự, thường mang ý nghĩa đếm được bằng không, số dư bằng không, kết quả phép tính bằng không hoặc mã trạng thái có giá trị 0.

Với primitive `int`, không thể gán `null`. Với wrapper `Integer`, biến có thể tham chiếu một đối tượng chứa giá trị 0 **hoặc** chứa `null`.

~~~java
int primitiveZero = 0;
Integer boxedZero = Integer.valueOf(0);
Integer missing = null;

System.out.println(primitiveZero); // 0
System.out.println(boxedZero);     // 0
System.out.println(missing);       // null
~~~

Ba dòng trên là ví dụ đặt trong một phương thức. Khi in bằng `System.out.println(Object)`, tham chiếu null cho ra chữ `null`; điều đó không đồng nghĩa có một đối tượng `Integer` đặc biệt mang giá trị số “null”.

Mục tiêu của bài này là chọn đúng mô hình cho dữ liệu có thể thiếu, nhận diện mọi chỗ Java **tự unbox**, và không vô tình gộp “không biết số lượng” thành “số lượng chắc chắn bằng 0”.

## 2. Primitive và wrapper: hai miền giá trị khác nhau

| Kiểu | Giá trị số 0 | Có thể chứa null? | Dùng khi |
| --- | --- | --- | --- |
| `int` | `0` | Không | Luôn có số nguyên 32 bit hợp lệ |
| `long` | `0L` | Không | Luôn có số nguyên 64 bit hợp lệ |
| `double` | `0.0` | Không | Luôn có số thực, kể cả ±0.0 |
| `Integer` | `Integer.valueOf(0)` | Có | Cần phân biệt số 0 và thiếu số nguyên |
| `Long` | `Long.valueOf(0L)` | Có | Cần phân biệt số 0 và thiếu số nguyên dài |
| `Double` | `Double.valueOf(0.0)` | Có | Cần phân biệt số 0 và thiếu số thực |

`Integer`, `Long`, `Double`, `Short`, `Byte`, `Float` là các lớp wrapper cho primitive số tương ứng. `Character` và `Boolean` cũng là wrapper primitive, dù không phải lớp con của `Number`.

~~~java
// int n = null; // Không biên dịch: null không chuyển được thành int.
Integer n = null; // Hợp lệ: Integer là kiểu tham chiếu.
Integer z = 0;    // Hợp lệ: autoboxing từ int 0 thành Integer.
~~~

Một đối tượng wrapper bất biến: `Integer.valueOf(0)` đại diện giá trị 0, và không thể thay đổi nội dung của đối tượng thành 1. Tuy vậy, biến tham chiếu không `final` có thể được gán lại sang object khác hoặc `null`.

### Giá trị mặc định của field và phần tử mảng

Java gán mặc định `0` cho **field primitive `int`**, nhưng `null` cho **field kiểu `Integer`**:

~~~java
class Defaults {
    int primitiveCount;
    Integer optionalCount;
}

// Trong một phương thức:
Defaults value = new Defaults();
System.out.println(value.primitiveCount); // 0
System.out.println(value.optionalCount);  // null
~~~

Tương tự, `new int[3]` có các phần tử bằng 0; `new Integer[3]` có các phần tử bằng `null`. Biến cục bộ chưa gán không tự động được đọc với giá trị mặc định: trình biên dịch yêu cầu biến được gán chắc chắn (*definite assignment*) trước khi sử dụng.

Điểm này quan trọng với DTO, entity và các mô hình đầu vào: chọn `int` có thể làm mất khả năng biểu diễn “chưa cung cấp”; chọn `Integer` buộc code nhận dữ liệu phải quyết định cách xử lý `null`.

## 3. Boxing và unboxing có thể che giấu khác biệt null/0

**Boxing** chuyển giá trị primitive sang wrapper:

~~~java
int raw = 0;
Integer boxed = raw; // autoboxing, giá trị boxed là 0
~~~

**Unboxing** chuyển wrapper trở lại primitive:

~~~java
Integer boxed = Integer.valueOf(0);
int raw = boxed; // unboxing, raw là 0
~~~

Khi wrapper khác `null`, unboxing lấy giá trị primitive. Nếu wrapper là `null`, Java **ném `NullPointerException`** vì không có đối tượng nào để đọc giá trị bên trong.

~~~java
Integer count = null;

// Lệnh sau biên dịch được nhưng ném NullPointerException khi chạy:
// int primitive = count;
~~~

Phép chuyển đổi đó có thể xảy ra **ngầm**, ngay cả khi source code không viết `intValue()`:

~~~java
Integer count = null;

// Mỗi biểu thức sau đều đòi unbox count và sẽ ném NPE khi chạy:
// int next = count + 1;
// boolean positive = count > 0;
// boolean equalsZero = count == 0;
// int multiplied = count * 2;
~~~

Phân biệt lỗi unboxing với trường hợp giá trị là 0: với `Integer count = 0;`, các biểu thức trên hợp lệ và tính đúng theo số nguyên; với `count = null;`, Java không coi null là 0 để tiếp tục tính.

### Kiểm tra null trước khi dùng giá trị

~~~java
Integer count = null;

if (count == null) {
    System.out.println("Chưa có số lượng");
} else if (count == 0) {
    System.out.println("Số lượng bằng 0");
} else {
    System.out.println("Số lượng là " + count);
}
~~~

Đoạn trên in `Chưa có số lượng`. Trong nhánh sau `else`, ta biết chắc `count` khác `null` nên biểu thức `count == 0` có thể unbox an toàn.

## 4. Toán tử ==: kiểm tra tham chiếu hay so sánh số?

Ngữ nghĩa của `==` phụ thuộc **kiểu tĩnh của hai toán hạng**.

### Hai tham chiếu Integer

~~~java
Integer a = null;
Integer b = null;
Integer zero = Integer.valueOf(0);

System.out.println(a == b);    // true: cùng là null
System.out.println(a == zero); // false: null khác tham chiếu đối tượng
~~~

Khi cả hai toán hạng là wrapper tham chiếu, `==` kiểm tra **identity của tham chiếu**, không yêu cầu unboxing để so sánh giá trị.

Một ví dụ khác:

~~~java
Integer left = Integer.valueOf(1_000);
Integer right = Integer.valueOf(1_000);

System.out.println(left.equals(right)); // true
// Không dùng left == right để xác định hai giá trị số có bằng nhau.
~~~

`Integer.valueOf` bảo đảm cache ít nhất các giá trị từ −128 đến 127, và có thể cache thêm các giá trị khác. Vì vậy kết quả `==` giữa hai wrapper có giá trị bằng nhau không phải cách so sánh số ổn định.

### Một wrapper và một primitive

~~~java
Integer zero = 0;
System.out.println(zero == 0); // true: Integer bị unbox thành int
~~~

Ngược lại:

~~~java
Integer missing = null;
// System.out.println(missing == 0); // NPE khi chạy: unbox null.
~~~

Vì vậy `missing == null` là một phép kiểm tra tham chiếu an toàn, nhưng `missing == 0` là phép so sánh số cần unbox và **không** an toàn nếu tham chiếu có thể null.

**Thứ tự ưu tiên trong điều kiện:**

~~~java
Integer count = null;
boolean isKnownZero = count != null && count == 0;
System.out.println(isKnownZero); // false
~~~

`&&` có đánh giá ngắn mạch: khi `count != null` là `false`, vế sau không được thực thi. Đây là một mẫu điều kiện an toàn để kiểm tra “đã có số và số đó bằng 0”.

## 5. equals() và Objects.equals(): so sánh theo giá trị an toàn

`Integer.equals()` so sánh giá trị **giữa hai đối tượng Integer**, còn `java.util.Objects.equals(a, b)` là cách tiện lợi để xử lý cả trường hợp null:

~~~java
Integer missing = null;
Integer zero = Integer.valueOf(0);
Integer anotherZero = Integer.valueOf(0);

System.out.println(java.util.Objects.equals(missing, zero)); // false
System.out.println(java.util.Objects.equals(zero, anotherZero)); // true
System.out.println(java.util.Objects.equals(null, null)); // true
~~~

Giá trị `true` ở `Objects.equals(null, null)` chỉ nói rằng **hai tham chiếu đều thiếu** theo định nghĩa phép so sánh null-safe; không phải chúng bằng số 0.

Các lưu ý:

- `missing.equals(zero)` ném `NullPointerException` vì gọi phương thức trên null.
- `zero.equals(missing)` trả `false`; receiver `zero` khác null.
- `Objects.equals(missing, zero)` trả `false` an toàn.
- `Integer.valueOf(0).equals(Long.valueOf(0L))` trả `false` vì hai wrapper khác lớp dù cùng giá trị toán học 0.

Khi so sánh giá trị có thể null từ hai nguồn, `Objects.equals` thường rõ nghĩa hơn `==`. Nhưng nếu logic phải phân biệt các trạng thái “đều thiếu” và “đều có giá trị 0”, hãy kiểm tra null riêng rồi thực hiện so sánh nghiệp vụ.

## 6. Ba trạng thái thường gặp trong dữ liệu ứng dụng

Có thể cần phân biệt **ba trường hợp** thay vì chỉ hai:

| Trạng thái | Ví dụ | Ý nghĩa có thể có |
| --- | --- | --- |
| Không có trường/khóa | Request JSON không gửi `count`; `Map` không có key | Khách hàng không yêu cầu cập nhật trường |
| Có trường nhưng giá trị null | `{"count": null}`; `map.put("count", null)` | Khách hàng muốn xóa giá trị hoặc dữ liệu chưa biết |
| Có trường với giá trị 0 | `{"count": 0}`; `map.put("count", 0)` | Khách hàng khẳng định số lượng là 0 |

Cách hiểu phụ thuộc **hợp đồng API**. Với HTTP PATCH, việc không gửi field có thể mang nghĩa “giữ nguyên”, còn gửi `null` có thể mang nghĩa “xóa”; với HTTP POST, hai trường hợp có thể được validation xử lý khác. Đây là thiết kế API, không phải quy tắc cố định của mọi thư viện JSON.

Một biến `Integer count` riêng lẻ chỉ biểu diễn được **null hoặc một số**; nó không lưu được riêng trạng thái “field vắng mặt” so với “field có mặt và null”. Muốn phân biệt đầy đủ ba trạng thái, cần thêm metadata về sự hiện diện, một cấu trúc dữ liệu có `containsKey`, hoặc một kiểu dữ liệu được thiết kế rõ ràng cho yêu cầu cập nhật.

### Map.get() không phân biệt được key vắng mặt với mapping null

~~~java
java.util.Map<String, Integer> counts = new java.util.HashMap<>();
counts.put("explicitNull", null);
counts.put("zero", 0);

System.out.println(counts.get("absent"));       // null
System.out.println(counts.get("explicitNull")); // null
System.out.println(counts.get("zero"));         // 0
~~~

Hai lời gọi `get` đầu đều trả `null`, nhưng ý nghĩa khác nhau. Dùng `containsKey` để xác định key có tồn tại:

~~~java
System.out.println(counts.containsKey("absent"));       // false
System.out.println(counts.containsKey("explicitNull")); // true
System.out.println(counts.containsKey("zero"));         // true
~~~

Với `HashMap`, `getOrDefault` chỉ dùng giá trị mặc định khi **không có mapping cho key**; nếu key tồn tại và map tới null, kết quả vẫn là null:

~~~java
System.out.println(counts.getOrDefault("absent", -1));       // -1
System.out.println(counts.getOrDefault("explicitNull", -1)); // null
~~~

Không nên dùng `map.getOrDefault(key, 0)` như một giải pháp tổng quát để biến mọi null thành 0. Nó không xử lý key được map tới null, và chính việc tự chuyển dữ liệu thiếu thành 0 có thể trái với hợp đồng nghiệp vụ.

## 7. Chọn mô hình rõ ràng cho “không cung cấp số”

### Trường hợp 1: Giá trị luôn phải có

Dùng primitive như `int` và validation ở ranh giới nhận dữ liệu. Khi số lượng có thể là 0 nhưng không được thiếu, biểu diễn bằng primitive là hợp lý sau khi đã xác thực đầu vào.

~~~java
static int requireCount(Integer count) {
    if (count == null) {
        throw new IllegalArgumentException("count is required");
    }
    if (count < 0) {
        throw new IllegalArgumentException("count cannot be negative");
    }
    return count;
}
~~~

Đây là **phương thức Java** để đặt trong một lớp, chưa phải chương trình chạy độc lập. Nhánh `count < 0` chỉ chạy sau khi kiểm tra null, do đó unboxing an toàn. Nếu đầu vào cần cho phép số âm, bỏ điều kiện đó theo nghiệp vụ.

### Trường hợp 2: Giá trị có thể chưa biết

Dùng `Integer` cùng một nhánh xử lý null rõ ràng:

~~~java
static String describe(Integer count) {
    if (count == null) return "unknown";
    if (count == 0) return "none";
    return "count=" + count;
}
~~~

Phương thức này phân biệt “chưa biết” với “đã biết bằng 0”. Đừng dùng `count == null ? 0 : count` nếu bạn còn cần giữ sự khác biệt.

### Trường hợp 3: Muốn biểu thị thiếu giá trị bằng API chuyên dụng

`java.util.OptionalInt` có thể mô tả “có số nguyên” hoặc “không có số nguyên”:

~~~java
java.util.OptionalInt absent = java.util.OptionalInt.empty();
java.util.OptionalInt presentZero = java.util.OptionalInt.of(0);

System.out.println(absent.isPresent()); // false
System.out.println(presentZero.isPresent()); // true
System.out.println(presentZero.getAsInt()); // 0
~~~

`OptionalInt` loại nhu cầu dùng null cho chính giá trị optional này và tránh boxing của `Optional<Integer>`. Tuy nhiên, `absent.orElse(0)` **cố ý gộp** thiếu giá trị thành 0 khi lấy kết quả; chỉ làm vậy nếu nghiệp vụ cho phép.

`OptionalInt` phù hợp để thể hiện kết quả có/không có giá trị ở một số API. Với field entity, kiểu dữ liệu JSON, ORM hoặc thư viện serialization, nên chọn dạng được framework hỗ trợ rõ ràng và kiểm tra mapping thực tế; không mặc định mọi công cụ sẽ serialize Optional giống nhau.

## 8. Ternary, điều kiện và phương thức: unboxing ẩn

Toán tử điều kiện `condition ? a : b` có quy tắc xác định kiểu kết quả theo **kiểu các nhánh**. Khi trộn một wrapper với một primitive số cùng kiểu, Java có thể chọn kiểu primitive và unbox wrapper **chỉ ở nhánh được chọn**:

~~~java
Integer missing = null;
boolean useMissing = true;

// Đoạn này ném NullPointerException tại runtime:
int result = useMissing ? missing : 0;
~~~

Không có cách nào để ép Java tự hiểu `missing` là giá trị 0. Nếu muốn kết quả vẫn có khả năng null, phải chủ động dùng một biểu thức có kiểu tham chiếu và hợp đồng rõ ràng. Ví dụ:

~~~java
Integer missing = null;
boolean useMissing = true;
Integer selected = useMissing ? missing : Integer.valueOf(0);
System.out.println(selected); // null
~~~

Cũng cẩn thận với các hàm nhận primitive:

~~~java
static void printCount(int value) {
    System.out.println(value);
}

// Integer count = null;
// printCount(count); // NPE khi cố unbox trước khi gọi hàm.
~~~

Khi định nghĩa API nhận `Integer` hoặc trả `Integer`, người viết và người gọi phải hiểu liệu `null` là trạng thái hợp lệ hay vi phạm hợp đồng. Nếu một phương thức **luôn** trả số hợp lệ, kiểu trả về primitive tránh được khả năng trả null; nếu giá trị có thể vắng, hãy mô tả rõ và cân nhắc Optional hoặc kiểu kết quả biểu đạt trạng thái.

## 9. Phân biệt null và 0 trong JDBC, cơ sở dữ liệu

Trong SQL, `NULL` là dấu hiệu thiếu/không biết giá trị theo mô hình cơ sở dữ liệu; còn số `0` là giá trị số. Với một cột nullable, hai trường hợp thường cần được bảo toàn riêng.

Đặc biệt, `java.sql.ResultSet.getInt("quantity")` **trả 0 cho SQL NULL** theo hợp đồng JDBC. Để phân biệt, phải gọi `wasNull()` ngay sau getter tương ứng:

~~~java
// Đoạn minh họa bên trong mã đã có java.sql.ResultSet rs:
int raw = rs.getInt("quantity");
Integer quantity = rs.wasNull() ? null : raw;
~~~

Cần gọi `wasNull()` **ngay sau lần đọc cột đó**, vì nó phản ánh giá trị NULL của cột gần nhất được getter đọc. Nếu gọi các getter khác trước, thông tin có thể không còn tương ứng với `quantity`.

Một số driver/JDBC API có cách lấy object như `getObject(..., Integer.class)`, có thể trả null cho SQL NULL khi được hỗ trợ. Nên kiểm tra tài liệu và hành vi driver cụ thể, đặc biệt khi mapping sang DTO/ORM.

**Bài học nghiệp vụ:** nếu “không có số lượng tồn kho được báo cáo” và “tồn kho bằng 0” là hai trạng thái khác nhau, việc map thẳng SQL NULL vào primitive `int` hoặc lấy `getInt` mà bỏ qua `wasNull()` có thể làm mất dữ liệu quan trọng.

## 10. JSON, biểu mẫu và giá trị mặc định

Khi nhận dữ liệu từ JSON hoặc form, cần định nghĩa rõ các trường hợp:

- Field vắng: có dùng mặc định không, từ chối hay giữ nguyên giá trị cũ?
- Field có `null`: có hợp lệ không? Có nghĩa “xóa giá trị” không?
- Field có `0`: có phải giá trị hợp lệ hay vi phạm giới hạn tối thiểu?
- Field có chuỗi `"0"`: có cho phép chuyển đổi chuỗi sang số không?
- Field có chuỗi `"null"`: đó là chuỗi bốn ký tự, không phải Java `null` hay JSON `null`.

Các framework có thể có quy tắc deserialize, coercion, validation và default khác nhau. Không tự giả định Jackson, Gson, Spring Data Binding hoặc một thư viện khác luôn phân biệt đúng ba trạng thái khi ánh xạ mọi trường vào một `Integer` đơn giản.

Một ví dụ thiết kế:

| Payload | Hành vi giả định của API cập nhật |
| --- | --- |
| `{}` | Không thay đổi số lượng hiện có |
| `{"quantity": null}` | Xóa số lượng đã lưu, nếu được phép |
| `{"quantity": 0}` | Cập nhật số lượng thành 0 |
| `{"quantity": 5}` | Cập nhật số lượng thành 5 |

Đây là **quy ước API ví dụ**, không phải quy tắc tự động của Java hay JSON. Để triển khai đúng, phải lưu thông tin hiện diện của field và áp dụng validation theo hợp đồng.

## 11. Chương trình Java 17+ hoàn chỉnh

Tạo tệp `NullVsZeroDemo.java`:

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public class NullVsZeroDemo {
    static String describe(Integer count) {
        if (count == null) {
            return "missing";
        }
        if (count == 0) {
            return "zero";
        }
        return "nonzero: " + count;
    }

    public static void main(String[] args) {
        Integer absent = null;
        Integer zero = Integer.valueOf(0);

        System.out.println("absent is null: " + (absent == null));
        System.out.println("zero is null: " + (zero == null));
        System.out.println("absent equals zero: " + Objects.equals(absent, zero));
        System.out.println("zero equals 0: " + Objects.equals(zero, 0));

        System.out.println("describe absent: " + describe(absent));
        System.out.println("describe zero: " + describe(zero));
        System.out.println("describe five: " + describe(5));

        try {
            int primitive = absent; // NPE do unboxing null
            System.out.println(primitive);
        } catch (NullPointerException ex) {
            System.out.println("unboxing absent: NPE");
        }

        Map<String, Integer> counts = new HashMap<>();
        counts.put("explicitNull", null);
        counts.put("zero", 0);

        System.out.println("missing key present: " + counts.containsKey("absent"));
        System.out.println("null key present: " + counts.containsKey("explicitNull"));
        System.out.println("zero key present: " + counts.containsKey("zero"));
        System.out.println("default for absent: " + counts.getOrDefault("absent", -1));
        System.out.println("default for explicit null: " +
                counts.getOrDefault("explicitNull", -1));

        OptionalInt noCount = OptionalInt.empty();
        OptionalInt zeroCount = OptionalInt.of(0);
        System.out.println("optional absent present: " + noCount.isPresent());
        System.out.println("optional zero: " + zeroCount.getAsInt());
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac --release 17 NullVsZeroDemo.java
java NullVsZeroDemo
~~~

Kết quả stdout:

~~~text
absent is null: true
zero is null: false
absent equals zero: false
zero equals 0: true
describe absent: missing
describe zero: zero
describe five: nonzero: 5
unboxing absent: NPE
missing key present: false
null key present: true
zero key present: true
default for absent: -1
default for explicit null: null
optional absent present: false
optional zero: 0
~~~

Giải thích:

1. `absent == null` chỉ kiểm tra **tham chiếu**, trong khi `zero` trỏ tới wrapper hợp lệ chứa số 0.
2. `Objects.equals(absent, zero)` trả false mà không gây NPE; số 0 chỉ bằng một giá trị số 0 cùng ngữ nghĩa phù hợp.
3. `describe` chủ động bảo toàn ba trạng thái: thiếu, bằng 0, khác 0.
4. Ép `absent` thành `int` thất bại khi chạy, cho thấy unboxing không thay null bằng 0.
5. `HashMap.containsKey` phân biệt key vắng với key tồn tại được map tới null; `getOrDefault` chỉ lấy mặc định cho key vắng.
6. `OptionalInt.empty()` và `OptionalInt.of(0)` phân biệt thiếu giá trị với giá trị 0, không cần tham chiếu null bên trong optional.

## 12. Các kiểu wrapper khác cũng có cùng vấn đề

### Long, Short, Byte

~~~java
Long absentId = null;
Long idZero = 0L;
System.out.println(absentId == null); // true
System.out.println(idZero.longValue() == 0L); // true
~~~

Nếu có `Long id = null;`, các phép cộng, so sánh số học hoặc unboxing xuống `long` sẽ ném NPE tương tự `Integer`. Không nên sử dụng số 0 làm “giá trị không tồn tại” trừ khi hợp đồng dữ liệu đã khẳng định 0 **không thể là giá trị hợp lệ**.

### Double và Float

~~~java
Double absentMeasurement = null;
Double zeroMeasurement = 0.0;

System.out.println(absentMeasurement == null); // true
System.out.println(zeroMeasurement == null);    // false
~~~

Đối với floating point, còn có `+0.0` và `-0.0`. Cả hai là giá trị số thực hợp lệ, **đều khác `null`**. Phép toán số thực primitive `+0.0 == -0.0` trả true, trong khi `Double.equals()` phân biệt zero có dấu. Đây là câu hỏi **bình đẳng floating point**, tách biệt với việc kiểm tra tham chiếu null.

`Double.NaN` cũng **không phải null**. NaN đại diện một kết quả dấu phẩy động không xác định theo IEEE 754; đó vẫn là giá trị nằm trong một object `Double` nếu được boxing. Nếu ứng dụng còn cần phân biệt “thiếu số đo”, “NaN do phép toán” và “giá trị số 0”, phải quy định riêng từng trường hợp.

### Boolean: false khác null

Dù không phải số, `Boolean` là minh họa rất gần:

~~~java
Boolean noResponse = null;
Boolean explicitlyFalse = Boolean.FALSE;

System.out.println(noResponse == null);      // true
System.out.println(Boolean.FALSE.equals(noResponse)); // false
~~~

Một field có thể mang trạng thái “chưa trả lời”, “đã trả lời không”, “đã trả lời có”. Dùng primitive `boolean` sẽ không lưu được trạng thái chưa trả lời bằng null.

## 13. Khi nào được thay null bằng 0?

Chuyển giá trị thiếu sang 0 không phải lúc nào cũng sai. Nó phù hợp khi **hợp đồng nghiệp vụ xác định** thiếu dữ liệu tương đương số 0. Ví dụ một hàm thống kê tổng tiền của các loại phí tùy chọn có thể coi phí vắng mặt là 0.

~~~java
static int orZero(Integer value) {
    return value == null ? 0 : value;
}
~~~

Phương thức này làm mất sự khác biệt giữa null và 0 **có chủ đích**. Đó phải là quyết định rõ ràng tại ranh giới xử lý, không phải mặc định áp dụng trong mọi tầng hệ thống.

Cần tránh:

~~~java
// Mẫu dễ gây sai ý nghĩa nếu "chưa biết" khác "bằng 0":
// Integer stock = repository.findStock(...);
// int safeStock = stock == null ? 0 : stock;
~~~

Nếu `null` có nghĩa “chưa kiểm kê”, biến `safeStock` có thể bị hiểu nhầm thành “chắc chắn hết hàng”. Thay vào đó, giữ nullable value, tạo trạng thái enum rõ ràng, hoặc trả về một kết quả có cấu trúc chứa cả tình trạng xác thực.

**Nguyên tắc:** chỉ gộp trạng thái khi biết rõ người sử dụng kết quả cần ngữ nghĩa nào.

## 14. Lỗi thường gặp và cách tránh

| Cách viết/thói quen | Rủi ro | Cách đúng |
| --- | --- | --- |
| `Integer x = null; int y = x;` | NPE do unboxing | Kiểm tra null hoặc đặt hợp đồng non-null |
| `Integer x = null; x == 0` | NPE do so sánh số | `x != null && x == 0` |
| `x.equals(0)` khi `x` nullable | NPE nếu x null | `Objects.equals(x, 0)` hoặc kiểm tra null |
| `Integer a, b; a == b` để so giá trị | Phụ thuộc identity/cache | `Objects.equals(a, b)` |
| Coi mọi `Integer` null là 0 | Mất nghĩa “chưa biết” | Chỉ gán mặc định khi nghiệp vụ cho phép |
| `map.get(key) == null` nghĩa là key vắng | Không phân biệt key tồn tại với mapping null | `map.containsKey(key)` |
| `map.getOrDefault(key, 0)` xử lý mọi null | Mapping tới null vẫn trả null | Xác định chính sách cho key vắng và null riêng |
| `ResultSet.getInt` là đủ để đọc nullable SQL integer | SQL NULL cũng trả 0 | Gọi ngay `ResultSet.wasNull()` |
| Dùng primitive field khi cần biết JSON field có mặt không | Không biểu diễn được null; còn mất thông tin “absent” | Theo dõi sự hiện diện và hợp đồng deserialize |
| Coi chuỗi `"0"` bằng số 0 trước parse | Không cùng kiểu dữ liệu | Parse/validate theo định dạng đầu vào |
| Gọi `OptionalInt.orElse(0)` rồi quên trạng thái absent | Cố ý mất thông tin thiếu | Dùng `isPresent()` hoặc xử lý nhánh absent trước |
| Đánh đồng `Double.NaN`, `0.0` và `null` | Ba trường hợp có ngữ nghĩa khác nhau | Kiểm tra `null`, `Double.isNaN` và giá trị 0 riêng |

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Hai dòng sau có giống nhau không?

~~~java
Integer a = null;
Integer b = 0;
~~~

**Đáp án:** Không. `a` không trỏ tới object; `b` trỏ tới một `Integer` đại diện số 0. `a == null` true; `b == null` false.

**Câu 2.** Vì sao `Integer count = null; if (count == 0) ...` ném lỗi?

**Đáp án:** Toán hạng `0` là primitive `int`, khiến Java unbox `count` để so sánh giá trị. Unboxing tham chiếu null gây `NullPointerException`.

**Câu 3.** `Objects.equals(null, null)` trả true có nghĩa null bằng 0 không?

**Đáp án:** Không. Nó chỉ cho biết hai đối số cùng là null. `Objects.equals(null, 0)` trả false.

**Câu 4.** Trong `HashMap`, `get("x")` trả null có chứng minh không có key x không?

**Đáp án:** Không. Key có thể tồn tại và được map tới null. Dùng `containsKey("x")` để phân biệt.

**Câu 5.** Nếu cột SQL chứa NULL, `ResultSet.getInt("quantity")` trả gì và phải kiểm tra điều gì?

**Đáp án:** Nó trả primitive 0. Gọi ngay `wasNull()` để phân biệt SQL NULL với giá trị số 0 thực sự.

**Câu 6.** Khi nào dùng `Integer` thay `int`?

**Đáp án:** Khi cần API object/generic hoặc cần diễn tả trạng thái có thể thiếu giá trị; nếu dữ liệu bắt buộc có số và không cần biểu diễn null, `int` thường đơn giản hơn.

**Câu 7.** `OptionalInt.empty().orElse(0)` và `OptionalInt.of(0).orElse(0)` cùng trả 0. Có còn phân biệt trạng thái đầu vào không?

**Đáp án:** Không, vì lời gọi `orElse(0)` đã cố ý chuyển cả hai thành primitive 0. Nếu cần phân biệt, hãy dùng `isPresent()`/nhánh xử lý trước.

## 16. Ghi nhớ và tham khảo

- **`null` biểu thị không có object; số `0` biểu thị một giá trị hợp lệ**. Đây là khác biệt dữ liệu và nghiệp vụ, không chỉ cú pháp.
- **Primitive không chứa null; wrapper có thể chứa null**. Tự động unboxing null sẽ ném `NullPointerException`.
- **`== null` kiểm tra tham chiếu; `== 0` với wrapper yêu cầu unboxing**. Dùng null guard hoặc `Objects.equals` đúng ngữ cảnh.
- **`Map.get`, JDBC `getInt` và việc deserialize field** có thể làm mất thông tin nếu không xử lý trạng thái thiếu một cách chủ đích.
- Dùng `OptionalInt` hoặc cấu trúc kết quả có thông tin hiện diện khi hợp đồng API yêu cầu. Chỉ chuyển null thành 0 khi nghiệp vụ cho phép.

Tài liệu Java 17:

- [Java Language Specification — Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7) và [Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8).
- [Java Language Specification — Equality Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21).
- [Integer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html) và [Objects.equals API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object)).
- [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) và [OptionalInt API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalInt.html).
- [JDBC ResultSet API](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/ResultSet.html): `getInt` và `wasNull`.


</details>

- [Quay lại đầu trang](#back-to-top)
