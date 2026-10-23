<a id="back-to-top"></a>

# Lớp bao nguyên thủy

## Menu
- [Integer, Long, Double, Boolean và các lớp bao khác](#primitive-wrappers-001)
- [Boxing, unboxing và autoboxing](#primitive-wrappers-002)
- [Unboxing tham chiếu null gây NullPointerException](#primitive-wrappers-003)
- [Phân biệt so sánh tham chiếu lớp bao với so sánh giá trị](#primitive-wrappers-004)
- [Bộ đệm đối tượng lớp bao và vì sao không dựa vào ==](#primitive-wrappers-005)
- [parseInt(), parseDouble(), valueOf() và kiểu kết quả](#primitive-wrappers-006)
- [NumberFormatException khi chuỗi không hợp lệ](#primitive-wrappers-007)
- [Chọn kiểu nguyên thủy hay lớp bao theo yêu cầu dữ liệu](#primitive-wrappers-008)

## <a id="primitive-wrappers-001">Integer, Long, Double, Boolean và các lớp bao khác</a>

<details>
<summary>Xem chi tiết</summary>

# `Integer`, `Long`, `Double`, `Boolean` và các lớp bao khác — Java 17+

Java có tám **kiểu nguyên thủy** (*primitive*) dùng để lưu giá trị như số, ký tự và đúng/sai. Mỗi primitive có một **lớp bao** (*wrapper class*) tương ứng trong gói `java.lang`. Wrapper là **kiểu tham chiếu**: biến có thể tham chiếu đối tượng wrapper hoặc mang giá trị `null`. Wrapper cho phép đưa giá trị nguyên thủy vào API làm việc với đối tượng, đặc biệt là các collection và generic.

~~~java
int count = 42;          // int: gia tri nguyen thuy.
Integer boxed = count;   // Integer: autoboxing.
int again = boxed;       // int: unboxing.
~~~

`Integer` không phải cách viết dài hơn của `int`: hai kiểu có **ngữ nghĩa khác nhau**, đặc biệt ở `null`, so sánh, đối tượng được tạo và chi phí boxing/unboxing.

## 1. Bảng đủ tám cặp primitive ↔ wrapper

| Primitive | Wrapper trong `java.lang` | Ví dụ |
| --- | --- | --- |
| `byte` | `Byte` | `Byte.valueOf((byte) 7)` |
| `short` | `Short` | `Short.valueOf((short) 300)` |
| `int` | `Integer` | `Integer.valueOf(42)` |
| `long` | `Long` | `Long.valueOf(9L)` |
| `float` | `Float` | `Float.valueOf(1.25f)` |
| `double` | `Double` | `Double.valueOf(3.5)` |
| `char` | `Character` | `Character.valueOf('J')` |
| `boolean` | `Boolean` | `Boolean.valueOf(true)` |

Một số điều cần nhớ:

- `int` và `Integer` là hai **kiểu khác nhau**; Java có thể tự chuyển giữa chúng trong ngữ cảnh phù hợp.
- Wrapper **bất biến**: không có phương thức thay đổi giá trị số/boolean/ký tự bên trong cùng một wrapper. Gán `boxed = 43` khiến biến tham chiếu kết quả boxing khác, không sửa nội dung đối tượng `Integer` cũ.
- `boolean`/`Boolean` không được ép thành số `0`/`1` bằng cast của Java. Muốn ánh xạ phải viết quy tắc, ví dụ `flag ? 1 : 0`.
- `Void` là lớp đặc biệt đại diện cho từ khóa `void` trong một số API phản chiếu, **không phải wrapper thứ chín chứa một giá trị primitive `void`**.

## 2. Java 17+ runnable: đủ wrapper, parsing, so sánh và `null`

Lưu tệp **`WrapperTypesDemo.java`** với mã nguồn sau:

~~~java
import java.util.List;

public class WrapperTypesDemo {
    public static void main(String[] args) {
        int primitive = 42;
        Integer boxed = primitive;  // Autoboxing int -> Integer.
        int unboxed = boxed;        // Unboxing Integer -> int.
        System.out.println("primitive=" + primitive);
        System.out.println("boxedInteger=" + boxed);
        System.out.println("unboxedInteger=" + unboxed);

        Long large = Long.valueOf(9_000_000_000L);
        Double fraction = Double.valueOf(3.5);
        Boolean enabled = Boolean.TRUE;
        Byte small = Byte.valueOf((byte) 7);
        Short medium = Short.valueOf((short) 32_000);
        Float ratio = Float.valueOf(1.25f);
        Character letter = Character.valueOf('J');

        System.out.println("long=" + large);
        System.out.println("double=" + fraction);
        System.out.println("boolean=" + enabled);
        System.out.println("other=" + small + "," + medium
                + "," + ratio + "," + letter);

        int parsedInt = Integer.parseInt("123");
        long parsedLong = Long.parseLong("9000000000");
        double parsedDouble = Double.parseDouble("2.5");
        boolean parsedBoolean = Boolean.parseBoolean("TrUe");
        boolean otherBoolean = Boolean.parseBoolean("yes");
        Integer boxedFromText = Integer.valueOf("123");

        System.out.println("parsedInt=" + parsedInt);
        System.out.println("parsedLong=" + parsedLong);
        System.out.println("parsedDouble=" + parsedDouble);
        System.out.println("parsedBoolean=" + parsedBoolean);
        System.out.println("parsedOtherBoolean=" + otherBoolean);
        System.out.println("valueOfInt=" + boxedFromText);

        Integer left = Integer.valueOf(1_000);
        Integer right = Integer.valueOf(1_000);
        System.out.println("sameValue=" + left.equals(right));

        Integer cachedA = Integer.valueOf(100);
        Integer cachedB = Integer.valueOf(100);
        System.out.println("cached100SameObject=" + (cachedA == cachedB));
        System.out.println("compare=" + Integer.compare(10, 20));

        List<Integer> numbers = List.of(3, 5, 7);
        int total = 0;
        for (Integer number : numbers) {
            total += number; // Unboxing de cong voi int.
        }
        System.out.println("listSum=" + total);
        System.out.println("charIsLetter=" + Character.isLetter(letter));

        Integer maybe = null;
        System.out.println("nullWrapper=" + (maybe == null));
        int fallback = maybe == null ? 0 : maybe;
        System.out.println("nullableDefault=" + fallback);

        try {
            int invalid = maybe; // Unboxing null.
            System.out.println("unexpected=" + invalid);
        } catch (NullPointerException ex) {
            System.out.println("unboxingNull=NullPointerException");
        }

        try {
            Integer.parseInt("abc");
        } catch (NumberFormatException ex) {
            System.out.println("badNumber=NumberFormatException");
        }

        System.out.println("nanPrimitiveEqual="
                + (Double.NaN == Double.NaN));
        System.out.println("nanWrapperEqual="
                + Double.valueOf(Double.NaN)
                        .equals(Double.valueOf(Double.NaN)));
        System.out.println("zeroPrimitiveEqual=" + (0.0 == -0.0));
        System.out.println("zeroWrapperEqual="
                + Double.valueOf(0.0).equals(Double.valueOf(-0.0)));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) ở thư mục chứa file. Dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 WrapperTypesDemo.java
java WrapperTypesDemo
~~~

**Expected output chính xác** của lệnh `java WrapperTypesDemo`:

~~~text
primitive=42
boxedInteger=42
unboxedInteger=42
long=9000000000
double=3.5
boolean=true
other=7,32000,1.25,J
parsedInt=123
parsedLong=9000000000
parsedDouble=2.5
parsedBoolean=true
parsedOtherBoolean=false
valueOfInt=123
sameValue=true
cached100SameObject=true
compare=-1
listSum=15
charIsLetter=true
nullWrapper=true
nullableDefault=0
unboxingNull=NullPointerException
badNumber=NumberFormatException
nanPrimitiveEqual=false
nanWrapperEqual=true
zeroPrimitiveEqual=true
zeroWrapperEqual=false
~~~

Hai lệnh kiểm tra phiên bản JDK in thông tin tùy môi trường; chúng không thuộc khối expected output trên.

## 3. Wrapper và primitive chuyển đổi qua boxing/unboxing

**Autoboxing** đóng gói giá trị nguyên thủy thành wrapper khi Java cho phép; **unboxing** lấy giá trị primitive tương ứng. Trong số học, unboxing có thể xảy ra mà không có lời gọi method rõ ràng:

~~~java
Integer object = 42;           // Boxing int -> Integer.
int value = object;            // Unboxing Integer -> int.
int next = object + 1;         // Unbox rồi cộng -> 43.
~~~

Một wrapper được phép chứa `null`; vì vậy `Integer missing = null; int n = missing;` **biên dịch được nhưng ném `NullPointerException` lúc chạy**. Kiểm tra null trước phép toán hoặc thiết kế hợp đồng dữ liệu bắt buộc, không tự gán mặc định 0 nếu “chưa có” khác “bằng 0”. Biến cục bộ chưa khởi tạo cũng không tự có giá trị `null`; chỉ các field/reference array elements mới được default theo quy tắc Java.

Bài chuyên boxing giải thích chi tiết overload, chuyển kiểu số học và thao tác `++`, còn bài unboxing null tập trung vào các biểu thức/điều kiện có thể gây NPE.


## 4. API chuyển đổi chuỗi: kiểu trả về phải rõ

Các lớp bao có cả factory trả wrapper và parser trả primitive:

| Biểu thức | Kiểu trả về |
| --- | --- |
| `Integer.parseInt("42")` | `int` |
| `Integer.valueOf("42")` | `Integer` |
| `Long.parseLong("42")` | `long` |
| `Double.parseDouble("2.5")` | `double` |
| `Boolean.parseBoolean("TrUe")` | `boolean` |
| `String.valueOf(42)` | `String` |

`Integer.parseInt("abc")` ném `NumberFormatException`; `Boolean.parseBoolean("yes")` lại trả `false`, nên phải **validate riêng** nếu API chỉ chấp nhận `true`/`false`. Khi gán `int` parser trả về vào `Integer`, Java boxing **sau** lời gọi; kiểu trả về của method không đổi. Bài `parseInt()/valueOf()` giải thích các overload/radix và bài `NumberFormatException` phân loại lỗi dữ liệu.


## 5. Giá trị wrapper khác identity của đối tượng

~~~java
Integer a = Integer.valueOf(1_000);
Integer b = Integer.valueOf(1_000);
System.out.println(a.equals(b)); // true.
~~~

Với hai tham chiếu `Integer`, `a == b` kiểm tra **identity**, không phải số bên trong. `Integer.valueOf(100)` được bảo đảm tái sử dụng wrapper ở vùng cache `-128..127`, nhưng không thể dựa vào `==` để so giá trị nói chung. Dùng `a.equals(b)` nếu `a` không null, hoặc `Objects.equals(a, b)` nếu hai phía có thể null. Khi so wrapper với **primitive**, Java có thể unbox, nên null lại gây NPE.

`Double.equals` và `Float.equals` còn có quy tắc khác phép `==` primitive: hai wrapper chứa `NaN` được xem là bằng nhau, trong khi `+0.0` và `-0.0` được `equals` phân biệt. Vì vậy phải chọn rõ semantics mong muốn. Bài equality phân tích trường hợp hỗn hợp, còn bài cache giải thích các vùng identity được bảo đảm cho từng wrapper.


## 6. Wrapper trong generic và collection

Generic như `List<T>` yêu cầu **kiểu tham chiếu**:

~~~java
import java.util.List;

List<Integer> scores = List.of(3, 5, 7); // Hop le.
// List<int> invalid;                    // Khong hop le.
~~~

Dùng `List<Integer>` vì phần tử là đối tượng `Integer`. Khi thêm một `int` vào `List<Integer>`, Java có thể boxing; khi lấy phần tử để tính toán `int`, Java có thể unboxing. `List.of(...)` tạo danh sách không thể thay đổi qua các thao tác thêm/xóa; nó **không cho phép phần tử null**.

So với mảng:

~~~java
int[] primitiveArray = new int[2];        // Cac phan tu mac dinh 0.
Integer[] wrapperArray = new Integer[2]; // Cac phan tu mac dinh null.
~~~

Nếu hiệu năng, bộ nhớ và tránh null là ưu tiên trong vòng lặp xử lý số lượng lớn, primitive array hoặc API primitive có thể phù hợp hơn việc boxing hàng loạt. Tuy nhiên, lựa chọn nên dựa vào yêu cầu thực tế và đo đạc khi cần, không mặc định wrapper luôn là lựa chọn sai.

## 7. Wrapper bất biến, biến tham chiếu vẫn có thể gán lại

Wrapper không cho phép sửa giá trị bên trong instance. Phép toán trên biến wrapper có thể unbox, tính toán, box rồi **gán một tham chiếu kết quả**; một biến khác giữ tham chiếu cũ không bị thay đổi:

~~~java
Integer count = 10;
Integer before = count;
count = count + 1;
System.out.println(count);  // 11
System.out.println(before); // 10
~~~

Khai báo `final Integer fixed = 10;` cấm **gán lại biến** nhưng không phải nguyên nhân khiến wrapper bất biến. Bài boxing phân tích riêng toán tử `++` và các chuyển đổi ngầm.

## 8. Tóm tắt lựa chọn kiểu theo tình huống

| Khi nào? | Kiểu phù hợp thường gặp | Cần nhớ |
| --- | --- | --- |
| Biến đếm/tính toán luôn có giá trị | `int`, `long`, `double` | Không có trạng thái null |
| Giá trị có thể chưa được cung cấp | `Integer`, `Long`, `Boolean` | Phải quy định ý nghĩa của null |
| Generic collection | `List<Integer>`, `Map<String, Long>` | Type argument cần kiểu tham chiếu |
| Parse số để tính toán | `Integer.parseInt`, `Double.parseDouble` | Parser trả primitive tương ứng |

`Boolean` có thể biểu diễn ba trạng thái `TRUE`, `FALSE` và `null` nếu hợp đồng dữ liệu thật sự cần. Đừng tự coi null là false hoặc 0. Bài cuối chương áp dụng các nguyên tắc này cho model, API, validation và hiệu năng.

## 9. Bài tập tự kiểm tra

1. **Ghép cặp:** Kiểu bao của `int`, `char`, `boolean` và `double` là gì?  
   *Đáp án:* `Integer`, `Character`, `Boolean` và `Double`.
2. **Boxing:** `Integer x = 7; int y = x;` gồm những bước nào?  
   *Đáp án:* Boxing `int -> Integer`, rồi unboxing `Integer -> int`.
3. **Null:** `Integer n = null; int x = n;` có biên dịch không, và chạy thế nào?  
   *Đáp án:* Biên dịch được; lúc chạy ném `NullPointerException` khi unboxing.
4. **Parsing:** `Integer.parseInt("12")` khác `Integer.valueOf("12")` ở điểm nào?  
   *Đáp án:* Phương thức đầu trả `int`, phương thức sau trả `Integer`.
5. **Boolean:** `Boolean.parseBoolean("YES")` trả gì?  
   *Đáp án:* `false`; chỉ chuỗi bằng `"true"` không phân biệt hoa/thường được coi là true.
6. **So sánh:** Vì sao không nên so hai biến `Integer` bằng `==`?  
   *Đáp án:* `==` kiểm tra đồng nhất đối tượng, có thể chịu tác động của cache; dùng `equals` để so giá trị khi không null.
7. **Số thực:** `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả gì?  
   *Đáp án:* `true` theo định nghĩa `Double.equals`, dù `Double.NaN == Double.NaN` là `false`.

## Checklist ghi nhớ

- [ ] Thuộc **tám cặp primitive–wrapper**, đặc biệt `char -> Character` và `int -> Integer`.
- [ ] Hiểu **autoboxing/unboxing** là chuyển đổi, không biến primitive thành reference vĩnh viễn.
- [ ] Biết wrapper có thể null và unboxing null gây `NullPointerException`.
- [ ] Dùng `parseXxx` khi muốn primitive; dùng `valueOf` khi muốn wrapper.
- [ ] Dùng `equals`/`Objects.equals` để so giá trị wrapper, không dựa vào `==` và cache.
- [ ] Nhận biết quy tắc riêng của `Double`/`Float` với `NaN` và zero có dấu.
- [ ] Biết generic như `List<Integer>` không chấp nhận kiểu tham số `int`.
- [ ] Chọn primitive cho dữ liệu luôn có giá trị, wrapper khi API/generic yêu cầu hoặc null là trạng thái có nghĩa.

**Kết luận:** Các lớp `Integer`, `Long`, `Double`, `Boolean`, `Byte`, `Short`, `Float` và `Character` giúp làm việc với giá trị primitive trong thế giới đối tượng của Java. Dùng chúng đúng nghĩa: **giữ rõ kiểu dữ liệu, xử lý null trước khi unboxing, phân biệt so sánh giá trị với tham chiếu, và chọn phương thức parsing/boxing phù hợp**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-002">Boxing, unboxing và autoboxing</a>

<details>
<summary>Xem chi tiết</summary>

# Boxing, unboxing và autoboxing trong Java 17+

## 1. Boxing là gì, unboxing là gì?

Java có **kiểu nguyên thủy** (*primitive*) như `int`, `long`, `double`, `boolean` và **lớp bao** (*wrapper class*) tương ứng như `Integer`, `Long`, `Double`, `Boolean`.

- **Boxing**: chuyển **giá trị primitive thành đối tượng wrapper tương ứng**, chẳng hạn `int → Integer`.
- **Unboxing**: lấy **giá trị primitive từ wrapper**, chẳng hạn `Integer → int`.
- **Autoboxing / auto-unboxing**: compiler chấp nhận các chuyển đổi này **ngầm** trong ngữ cảnh được Java cho phép, không bắt bạn gọi hàm chuyển đổi thủ công.

~~~java
int primitive = 42;
Integer boxed = primitive; // Autoboxing: int -> Integer
int restored = boxed;      // Auto-unboxing: Integer -> int

System.out.println(boxed);    // 42
System.out.println(restored); // 42
~~~

Phần trên có thể hình dung:

~~~mermaid
flowchart LR
    A["int: 42"] -->|"boxing"| B["Integer chứa giá trị 42"]
    B -->|"unboxing"| C["int: 42"]
~~~

**Quan trọng:** wrapper là **đối tượng tham chiếu**, không phải một kiểu primitive thứ hai. Hai thứ có thể biểu diễn cùng số nhưng khác nhau về khả năng giữ `null`, API, danh tính đối tượng và chi phí sử dụng.

## 2. Những cặp kiểu được boxing và giới hạn chuyển đổi

Tám cặp primitive → wrapper trong Java 17 là **`byte/Byte`, `short/Short`, `int/Integer`, `long/Long`, `float/Float`, `double/Double`, `char/Character`, `boolean/Boolean`**. `void` không có một giá trị để boxing; lớp `Void` phục vụ một số API riêng.

~~~java
Integer quantity = 7; // int -> Integer.
Long size = 7L;       // long -> Long.
// Long invalid = 7; // Sai: không tự ghép int -> long -> Long khi gán.
~~~

Lưu ý rằng compiler chỉ áp dụng **những chuyển đổi được ngôn ngữ cho phép trong từng ngữ cảnh**, không tự biến một primitive bất kỳ thành wrapper bất kỳ. Sau khi boxing, biến giữ tham chiếu và có thể được gán `null`; primitive thì không.


## 3. Boxing tường minh và autoboxing

Hai cách tạo một giá trị kiểu `Integer` từ một `int`:

~~~java
int value = 12;

Integer automatic = value;          // Autoboxing
Integer explicit = Integer.valueOf(value); // Gọi factory rõ ràng
~~~

Cả hai biến có kiểu `Integer`. Khi cần wrapper nhưng cũng muốn code thể hiện rõ ý định, `Integer.valueOf(int)` là API hữu ích. **Không cần** dùng constructor `new Integer(value)`: các constructor wrapper số kiểu này đã bị deprecated và đánh dấu để loại bỏ; dùng `valueOf` thích hợp hơn.

~~~java
Boolean flag = Boolean.valueOf(true);
Double reading = Double.valueOf(2.5);
Character letter = Character.valueOf('A');
~~~

**Không nên suy từ boxing rằng một object mới chắc chắn được cấp phát mỗi lần.** Java và các wrapper có thể sử dụng object đã được cache hoặc compiler/JVM có thể tối ưu; phần quan trọng cho ứng dụng là **giá trị và ngữ nghĩa**, không phải đếm số lần tạo object bằng mắt.

## 4. Unboxing tường minh và tự động

~~~java
Integer boxed = Integer.valueOf(15);

int automatic = boxed;       // Auto-unboxing
int explicit = boxed.intValue(); // Method lấy primitive

System.out.println(automatic); // 15
System.out.println(explicit);  // 15
~~~

Các wrapper định nghĩa method tương ứng như `longValue()`, `doubleValue()`, `booleanValue()`, `charValue()`, v.v.

**Chú ý:** `Number.intValue()` và các method chuyển số trên những wrapper khác có thể kết hợp cả **unboxing và thay đổi kiểu số học**, tùy class. Đừng giả định `doubleValue()`, `intValue()` giữ toàn bộ thông tin của một giá trị lớn hoặc có phần thập phân.

Trong phép toán số học thông thường, wrapper được **unbox** để tham gia tính toán:

~~~java
Integer one = 10;
Integer two = 20;

int total = one + two;
System.out.println(total); // 30
~~~

Ở đây `one + two` có kiểu `int`, không phải phép cộng hai “object Integer” theo kiểu nạp chồng toán tử.

## 5. Rủi ro lớn nhất: wrapper có thể null

Primitive `int` **không giữ được `null`**, nhưng một biến `Integer` có thể:

~~~java
Integer missing = null;
System.out.println(missing == null); // true
~~~

Cố auto-unbox `missing` gây **`NullPointerException` tại runtime**:

~~~java
Integer missing = null;

try {
    int count = missing; // Compile hợp lệ; chạy thì unboxing null
    System.out.println(count);
} catch (NullPointerException ex) {
    System.out.println("unboxing null failed");
}
~~~

Đầu ra:

~~~text
unboxing null failed
~~~

Một số bẫy tương tự:

~~~java
Integer quantity = null;
// int next = quantity + 1; // Unboxing null -> NullPointerException

Boolean permitted = null;
// if (permitted) { ... }    // Auto-unboxing null -> NullPointerException
~~~

**Thực hành an toàn:**

- Nếu dữ liệu **không được phép thiếu**, dùng primitive hoặc kiểm tra null ở ranh giới input.
- Nếu dữ liệu có thể **vắng mặt**, dùng wrapper cùng chính sách xử lý null rõ ràng.
- Khi chỉ cần kiểm tra có null, viết `boxed == null` mà **không unbox**.
- Không lạm dụng giá trị mặc định `0` để che một input thực sự chưa có.

Ví dụ policy mặc định:

~~~java
Integer maybeCount = null;
int count = maybeCount != null ? maybeCount : 0;
System.out.println(count); // 0
~~~

Đây là một **quyết định nghiệp vụ có chủ đích**, không phải hành vi mặc định của Java. Tránh dùng mặc định 0 nếu 0 và “chưa nhập” là hai trạng thái khác nhau.

## 6. Nơi unboxing thay đổi cách hiểu toán tử `==`

~~~java
Integer first = Integer.valueOf(1000);
Integer sameValue = Integer.valueOf(1000);
int primitive = 1000;

System.out.println(first.equals(sameValue)); // true: so giá trị.
System.out.println(first == primitive);      // true: unboxing, so int.
~~~

**`Integer == Integer`** so tham chiếu; **`Integer == int`** unbox và so số, có thể ném NPE nếu wrapper null. Với hai wrapper có thể null, `Objects.equals(a, b)` thường đúng mục tiêu so giá trị; với wrapper khác kiểu, `Integer.valueOf(5).equals(Long.valueOf(5L))` vẫn là false.

Wrapper cache khiến `==` trên số nhỏ đôi lúc trả true; không phụ thuộc điều đó để kiểm tra cùng số. Chi tiết về `NaN`, signed zero, cache và hợp đồng so sánh thuộc hai bài equality/cache kế tiếp. Ở đây điều quan trọng là biết **phép toán nào buộc unbox**, không đánh đồng các trường hợp `==`.


## 7. Wrapper là immutable: `Integer++` không sửa object

Các lớp wrapper chuẩn như `Integer`, `Double`, `Boolean` là **bất biến** (*immutable*): không có thao tác thay giá trị nguyên thủy bên trong một instance đã tạo.

~~~java
Integer count = 5;
Integer before = count;

count++;

System.out.println(count);  // 6
System.out.println(before); // 5
~~~

Ý nghĩa của `count++` (ở mức hành vi) là:

1. Unbox `count` từ `Integer` thành `int`.
2. Cộng thêm 1 theo quy tắc số học của `int`.
3. Box kết quả thành `Integer`.
4. Gán giá trị tham chiếu kết quả lại vào biến `count`.

Điều này **không** sửa instance mà biến `before` vẫn tham chiếu.

~~~java
final Integer limit = 5;
// limit++; // Lỗi biên dịch: cố gán lại biến final
~~~

Dùng `int` cho phép đếm/tính toán liên tục khi không cần null hoặc API kiểu tham chiếu, giúp tránh boxing/unboxing không cần thiết.

## 8. Boxing trong lời gọi phương thức và overload

Compiler áp dụng quy tắc thích ứng kiểu đối số trong **method invocation context**. Ví dụ:

~~~java
static void acceptInteger(Integer value) {
    System.out.println("Integer=" + value);
}

// Trong main:
acceptInteger(7); // Autoboxing int -> Integer
~~~

Ở ví dụ này `acceptInteger(7)` hợp lệ vì method nhận `Integer` và đối số là `int`.

Một overload dễ nhầm:

~~~java
static void choose(long value) {
    System.out.println("long");
}

static void choose(Integer value) {
    System.out.println("Integer");
}
~~~

Gọi:

~~~java
choose(7);                   // In long
choose(Integer.valueOf(7));  // In Integer
~~~

Khi đối số là `int`, widening primitive từ `int` sang `long` được xét trong giai đoạn phù hợp trước boxing. Khi đối số đã là `Integer`, overload nhận đúng `Integer` là phù hợp nhất ở ví dụ này.

Không phải quy tắc tổng quát “primitive luôn tốt hơn wrapper”: Java xét **toàn bộ các overload và kiểu đối số**, bao gồm strict/loose invocation, subtype, boxing/unboxing, varargs và tính cụ thể giữa các method.

### Một chuyển đổi KHÔNG hợp lệ

~~~java
static void acceptLongBox(Long value) {}

// acceptLongBox(7);  // Không compile: int không tự widening rồi boxing sang Long
acceptLongBox(7L);    // Compile: long -> Long bằng boxing
~~~

Một phương pháp học tốt là luôn hỏi: **kiểu của biểu thức truyền vào là gì?** Literal `7` mặc định `int`, còn `7L` là `long`.

## 9. Boxing, unboxing và numeric promotion trong biểu thức

Với số học, wrapper thường được unbox rồi áp dụng numeric promotion của Java:

~~~java
Byte small = (byte) 100;
Short additional = (short) 20;

int result = small + additional; // byte/short -> int
System.out.println(result); // 120
~~~

`small + additional` **không** tạo kết quả kiểu `Byte` hoặc `Short`: Java unbox rồi nâng kiểu số học thành `int`.

~~~java
Integer count = 10;
long wider = count; // Integer -> int (unboxing) -> long (widening)
System.out.println(wider); // 10
~~~

Cách này hợp lệ; trái lại Java không cho phép mọi chuỗi widening và boxing đảo chiều như `Long n = 10;`.

Lưu ý nếu kết quả số học xảy ra overflow **trước** boxing, việc gán sang wrapper rộng hơn cũng không cứu kết quả:

~~~java
Integer max = Integer.MAX_VALUE;
long wrong = max + 1;         // int overflow trước
long correct = (long) max + 1; // Unboxing/cast thành long trước

System.out.println(wrong);   // -2147483648
System.out.println(correct); // 2147483648
~~~

Khi viết phép tính, hãy xem **kiểu toán hạng tại thời điểm cộng**, không chỉ kiểu của biến nhận.

## 10. Boxing/unboxing tại ranh giới collection

Với Java 17, generic yêu cầu kiểu tham chiếu: `List<Integer>` hợp lệ, còn `List<int>` không hợp lệ. Quan sát chính xác **vị trí chuyển kiểu**:

~~~java
java.util.List<Integer> numbers = new java.util.ArrayList<>();
numbers.add(2); // int -> Integer khi thêm
numbers.add(3);

int sum = 0;
for (int n : numbers) { // Integer -> int khi lấy từng phần tử
    sum += n;
}
System.out.println(sum); // 5
~~~

Nếu collection có phần tử `null`, vòng lặp dùng biến `int` có thể ném `NullPointerException` ngay khi lấy phần tử. `List.of(...)` không cho phép phần tử null, trong khi một số collection khác có thể cho phép. Xác nhận hợp đồng null trước khi dựa vào unboxing.

## 11. Parsing là thao tác khác boxing

~~~java
int parsed = Integer.parseInt("42");       // String -> int bằng parsing
Integer wrapped = Integer.valueOf("42");   // String -> Integer bằng factory
Integer boxedParsed = Integer.parseInt("42"); // Parsing rồi boxing
~~~

Java không tự parse `String` chỉ vì đích là wrapper (`Integer n = "42";` không biên dịch). Chuỗi không hợp lệ có thể gây `NumberFormatException`; các bài parsing tiếp theo giải thích radix, kiểu trả về và validation.

## 12. Boxing không xóa đặc tính của số thực

~~~java
System.out.println(Double.NaN == Double.NaN); // false
System.out.println(Double.valueOf(Double.NaN)
        .equals(Double.valueOf(Double.NaN))); // true
~~~

`Double.equals` còn phân biệt `+0.0` và `-0.0`, khác `==` giữa hai `double`. Đây là **quy tắc equality của wrapper**, không phải sự chuyển đổi boxing sai. Bài so sánh wrapper đi sâu vào NaN, signed zero và cache.

## 13. Khi nào tránh chuyển đổi không cần thiết?

Một phép tính với dữ liệu luôn có mặt thường nên dùng primitive để code dễ hiểu và giảm boxing/unboxing không cần thiết. Khi API/generic yêu cầu reference hoặc `null` có nghĩa nghiệp vụ, wrapper phù hợp nhưng phải bảo vệ nơi unbox. Đừng kết luận mỗi lần boxing chắc chắn cấp phát object, cũng đừng chọn kiểu chỉ dựa vào suy đoán hiệu năng; phân tích yêu cầu trước, đo hiệu năng sau nếu cần.

## 14. Chương trình Java 17 hoàn chỉnh — chạy và đối chiếu

Tạo đúng tệp **`BoxingUnboxingDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BoxingUnboxingDemo {
    private BoxingUnboxingDemo() {}

    static void choose(long value) {
        System.out.println("choose=long");
    }

    static void choose(Integer value) {
        System.out.println("choose=Integer");
    }

    static void acceptLongBox(Long value) {
        System.out.println("Long wrapper=" + value);
    }

    public static void main(String[] args) {
        int primitive = 42;
        Integer boxed = primitive;
        int restored = boxed;
        System.out.println("boxed=" + boxed);
        System.out.println("unboxed=" + restored);
        System.out.println("explicit=" + boxed.intValue());

        Boolean flag = true;
        Character letter = 'A';
        System.out.println("wrappers=" + flag + "," + letter);

        Integer first = Integer.valueOf(1000);
        Integer alias = first;
        Integer sameValue = Integer.valueOf(1000);
        System.out.println("identity alias=" + (first == alias));
        System.out.println("equal values=" + first.equals(sameValue));
        System.out.println("boxed vs primitive=" + (first == 1000));

        Integer counter = 5;
        Integer snapshot = counter;
        counter++;
        System.out.println("incremented=" + counter);
        System.out.println("snapshot=" + snapshot);

        Integer missing = null;
        System.out.println("missing=" + (missing == null));
        System.out.println("null-safe equals="
                + Objects.equals(missing, Integer.valueOf(5)));
        try {
            int failed = missing;
            System.out.println(failed);
        } catch (NullPointerException ex) {
            System.out.println("unboxing null=NullPointerException");
        }

        Integer boxedInt = 10;
        long widened = boxedInt;
        System.out.println("unbox widen=" + widened);

        choose(7);
        choose(Integer.valueOf(7));
        acceptLongBox(7L);

        List<Integer> values = new ArrayList<>();
        values.add(2);
        values.add(3);
        int total = 0;
        for (int value : values) {
            total += value;
        }
        System.out.println("list=" + values);
        System.out.println("list sum=" + total);

        Integer a = Integer.valueOf("42");
        int b = Integer.parseInt("42");
        System.out.println("parsed wrappers=" + a + "," + b);

        Integer number = Integer.valueOf(5);
        Long bigger = Long.valueOf(5L);
        System.out.println("different wrapper classes="
                + number.equals(bigger));

        Double nan1 = Double.valueOf(Double.NaN);
        Double nan2 = Double.valueOf(Double.NaN);
        Double plusZero = Double.valueOf(0.0);
        Double minusZero = Double.valueOf(-0.0);
        System.out.println("boxed NaN equals=" + nan1.equals(nan2));
        System.out.println("boxed zero equals="
                + plusZero.equals(minusZero));
        System.out.println("primitive zero equals=" + (0.0 == -0.0));
    }
}
~~~

**Yêu cầu:** cài JDK 17+, terminal đang ở thư mục chứa tệp source. Không cần Maven, Gradle hay thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 BoxingUnboxingDemo.java
java -cp . BoxingUnboxingDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\BoxingUnboxingDemo.java
java -cp . BoxingUnboxingDemo
~~~

**Expected output chính xác:**

~~~text
boxed=42
unboxed=42
explicit=42
wrappers=true,A
identity alias=true
equal values=true
boxed vs primitive=true
incremented=6
snapshot=5
missing=true
null-safe equals=false
unboxing null=NullPointerException
unbox widen=10
choose=long
choose=Integer
Long wrapper=7
list=[2, 3]
list sum=5
parsed wrappers=42,42
different wrapper classes=false
boxed NaN equals=true
boxed zero equals=false
primitive zero equals=true
~~~

**Các điểm cần quan sát:**

1. `Integer boxed = primitive` và `int restored = boxed` cho thấy autoboxing/unboxing.
2. `first == alias` true vì hai biến giữ cùng tham chiếu; `first.equals(sameValue)` true vì bằng **giá trị**. Không dựa vào cache wrapper để dự đoán identity của `first` và `sameValue`.
3. `Integer counter++` tạo kết quả mới sau unboxing/cộng/boxing; `snapshot` giữ giá trị 5.
4. Unboxing `missing` gây `NullPointerException`, được bắt để chương trình tiếp tục.
5. `choose(7)` chọn overload `long`, còn `choose(Integer.valueOf(7))` chọn overload `Integer`.
6. `List<Integer>` nhận số primitive qua boxing và vòng lặp dùng `int` lấy giá trị qua unboxing.
7. `Double.equals` so NaN và signed zero khác `==` primitive.

## 15. Các lỗi thường gặp và cách khắc phục

| Lỗi / ngộ nhận | Vì sao sai? | Cách xử lý |
|---|---|---|
| `Integer` và `int` hoàn toàn tương đương | Một bên là tham chiếu có thể null, một bên primitive | Chọn theo hợp đồng dữ liệu/API |
| `int value = nullableInteger;` luôn an toàn | Unboxing null gây `NullPointerException` | Kiểm tra null hoặc thiết kế primitive bắt buộc |
| `if (Boolean.TRUE.equals(flag))` và `if (flag)` luôn giống nhau | `flag = null` khiến cách sau lỗi | Dùng policy null rõ ràng |
| `Integer a == Integer b` để so số | So identity, kết quả bị cache gây nhiễu | Dùng `equals`/`Objects.equals` |
| `Integer a == int b` là so object identity | Một toán hạng primitive kích hoạt unboxing | Chỉ dùng khi wrapper chắc chắn không null |
| `Integer a = 5; a++` sửa object cũ | Wrapper immutable | Biến `a` được gán tham chiếu kết quả mới |
| `Long n = 7;` | Không tự nối int -> long -> Long | Viết `Long n = 7L;` |
| `List<int>` trong Java 17 | Generic không nhận primitive type argument | Dùng `List<Integer>` |
| Vòng `for (int n : values)` có phần tử Integer null | Unboxing null ngay khi lặp | Kiểm tra dữ liệu/nullable elements |
| Dùng wrapper trong vòng lặp nặng mà không đo đạc | Boxing có thể tăng chi phí object và GC | Cân nhắc primitive, array và benchmark |
| `Integer n = "42";` | String không tự chuyển thành wrapper | `Integer.valueOf("42")` |
| `Integer.valueOf(5).equals(Long.valueOf(5L))` trả true | `equals` của Integer không đổi kiểu wrapper | Chọn phép chuyển số phù hợp nếu cần so số khác kiểu |
| `Double.equals` hoàn toàn giống `==` số thực | NaN và signed zero có quy tắc khác | Xác định rõ semantics equality |

### Những điều boxing KHÔNG làm

Boxing **không**:

- Tự biến `String` thành kiểu số.
- Bảo đảm mọi lần boxing đều cấp phát object mới.
- Ngăn lỗi overflow trong phép cộng primitive.
- Cho phép truyền/gán tự động qua mọi chuỗi widening, narrowing, boxing.
- Xóa khả năng `NullPointerException` khi unbox wrapper.
- Khiến `==` trên hai wrapper tự chuyển thành phép so nội dung.

## 16. Bài tập có đáp án

### Bài A — nhận dạng boxing và unboxing

~~~java
int n = 10;
Integer wrapped = n;
int back = wrapped;
System.out.println(back);
~~~

**Đáp án:** in `10`. Phép gán `n → wrapped` là autoboxing, `wrapped → back` là unboxing.

### Bài B — wrapper có null

~~~java
Integer value = null;
// int n = value;
System.out.println(value == null);
~~~

**Đáp án:** in `true`; nếu chạy dòng comment, sẽ gặp `NullPointerException` khi unboxing. Compile vẫn có thể thành công khi dòng đó được bật, vì lỗi xảy ra lúc chạy.

### Bài C — biến wrapper tăng một đơn vị

~~~java
Integer a = 3;
Integer b = a;
a++;
System.out.println(a);
System.out.println(b);
~~~

**Đáp án:**

~~~text
4
3
~~~

Wrapper bất biến nên `a++` không sửa giá trị object mà `b` đang trỏ.

### Bài D — cùng số khác wrapper

~~~java
Integer n = Integer.valueOf(5);
Long l = Long.valueOf(5L);
System.out.println(n.equals(l));
~~~

**Đáp án:** `false`. `Integer.equals` không so theo kiểu số học khác wrapper.

### Bài E — overload

~~~java
static void f(long n) { System.out.println("long"); }
static void f(Integer n) { System.out.println("Integer"); }

// Trong main:
f(7);
f(Integer.valueOf(7));
~~~

**Đáp án:**

~~~text
long
Integer
~~~

### Bài F — collection wrapper

~~~java
java.util.List<Integer> nums = java.util.List.of(2, 3);
int sum = 0;
for (int n : nums) {
    sum += n;
}
System.out.println(sum);
~~~

**Đáp án:** `5`. Các phần tử kiểu `Integer` được unbox khi gán vào biến vòng lặp kiểu `int`.

## 17. Checklist tổng kết

- [ ] Phân biệt rõ primitive `int` với wrapper `Integer`.
- [ ] Biết boxing/autoboxing chuyển primitive sang wrapper, unboxing lấy primitive.
- [ ] Kể đủ tám cặp primitive-wrapper có giá trị.
- [ ] Hiểu wrapper có thể `null`, primitive tương ứng không thể.
- [ ] Không unbox wrapper null khi làm toán, `if`, so sánh với primitive hoặc vòng lặp.
- [ ] Dùng `equals` hoặc `Objects.equals` khi muốn so **giá trị wrapper**.
- [ ] Biết `==` giữa hai wrapper so **identity**, còn wrapper với primitive có thể unbox.
- [ ] Hiểu caching làm `==` trên hai wrapper khó dùng để so giá trị.
- [ ] Hiểu `Integer++` là unbox → tính toán → box → gán lại; wrapper immutable.
- [ ] Nắm ảnh hưởng boxing/unboxing đến overload và numeric promotion.
- [ ] Biết `Long n = 7;` không tương đương `Long n = 7L;`.
- [ ] Biết tại sao generic như `List<Integer>` dùng wrapper.
- [ ] Phân biệt `parseInt` với `valueOf`.
- [ ] Nhớ khác biệt của `Double.equals` với `==` primitive cho NaN và signed zero.
- [ ] Biên dịch/chạy `BoxingUnboxingDemo.java` bằng `javac`/`java` Java 17+ và đối chiếu toàn bộ đầu ra.

**Kết luận:** **Boxing** biến giá trị nguyên thủy thành wrapper; **unboxing** lấy lại primitive; **autoboxing** cho phép Java thực hiện chuyển đổi đó ngầm ở những ngữ cảnh xác định. Tính tiện lợi này cần đi kèm kỷ luật xử lý `null`, phép so sánh, overload, độ chính xác và chi phí hiệu năng. Hãy ưu tiên primitive cho tính toán bắt buộc có giá trị, dùng wrapper khi API/generic hoặc dữ liệu có thể vắng mặt thật sự đòi hỏi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-003">Unboxing tham chiếu null gây NullPointerException</a>

<details>
<summary>Xem chi tiết</summary>

# Unboxing tham chiếu `null` gây `NullPointerException` trong Java

> **Java core — language-basics / Lớp bao nguyên thủy**  
> **Yêu cầu:** Java 17+, một chương trình Java chạy độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Nhận diện vị trí Java tự **unbox** wrapper về primitive, giải thích vì sao wrapper bằng `null` phát sinh `NullPointerException` (NPE), và chọn cách kiểm tra hoặc quy định dữ liệu đầu vào trước khi tính toán.

## 1. Unboxing là gì và vì sao `null` gây lỗi?

Các lớp bao nguyên thủy (*wrapper classes*) như `Integer`, `Long`, `Double`, `Boolean` và `Character` là **kiểu tham chiếu**. Chúng có thể nhận `null`; các kiểu nguyên thủy tương ứng như `int`, `long`, `double`, `boolean` và `char` thì không.

**Unboxing** là chuyển một giá trị wrapper thành primitive tương ứng, thường được compiler thực hiện tự động:

~~~java
Integer wrapped = 12;
int value = wrapped; // Tự động unboxing: tương đương lấy intValue().
~~~

Nếu tham chiếu wrapper bằng `null`, không có object nào để lấy giá trị primitive:

~~~java
Integer missing = null;
// int value = missing; // Chạy đến đây sẽ ném NullPointerException.
~~~

Java có thể tự sinh thao tác gần tương đương `missing.intValue()`. Vì `missing == null` nên việc gọi phương thức để lấy giá trị primitive gây **NPE**.

**Quan trọng:** Bản thân câu lệnh `Integer missing = null;` **không** gây NPE. Lỗi xảy ra tại **điểm sử dụng cần primitive**. Wrapper và primitive có hợp đồng dữ liệu khác nhau: wrapper có thể biểu diễn “thiếu giá trị”, primitive thì không.

## 2. Những chỗ Java có thể unbox ngầm

| Ngữ cảnh | Ví dụ có thể gây NPE | Lý do |
| --- | --- | --- |
| Gán wrapper sang primitive | `Integer x = null; int n = x;` | Cần `int` nên unbox `Integer` |
| Phép toán số học | `Integer x = null; int sum = x + 1;` | `+` cần toán hạng số nguyên thủy |
| So sánh với primitive | `Integer x = null; boolean ok = x == 0;` | So sánh số dẫn tới unbox `x` |
| Điều kiện Boolean | `Boolean flag = null; if (flag) { ... }` | `if` cần `boolean` |
| Phép logic Boolean | `Boolean flag = null; boolean ok = flag && true;` | Vế trái phải được unbox |
| Tăng/giảm wrapper | `Integer x = null; x++;` | Phải đọc giá trị số trước khi tăng |
| Biểu thức ba ngôi | `Integer x = null; int n = true ? x : 10;` | Nhánh được chọn cần giá trị `int` |

**Ngược lại:** `x == null` **không unbox `x`** vì đang so sánh một tham chiếu với `null`. Nó là cách kiểm tra `null` hợp lệ. So sánh hai biến `Integer` bằng `==` có thể chỉ so identity tham chiếu; đừng dùng phép so ấy để suy ra hai **giá trị** wrapper bằng nhau.

### Hai tình huống dễ gây nhầm

~~~java
Integer count = null;
boolean missing = (count == null); // true: không unbox.
// boolean zero = (count == 0);    // NPE: cần unbox để so với int 0.
~~~

~~~java
Boolean approved = null;
// if (approved) { ... } // NPE: điều kiện if yêu cầu boolean.
boolean safeApproved = Boolean.TRUE.equals(approved); // false.
~~~

`Boolean.TRUE.equals(approved)` an toàn khi đối số là `null`. Cách này có nghĩa nghiệp vụ: **chỉ đúng khi giá trị tường minh là true**, còn `false` và `null` đều xử lý như false. Nếu thiếu dữ liệu phải báo lỗi, **không** nên âm thầm đổi `null` thành false.

## 3. Ví dụ Java 17+ hoàn chỉnh, chạy được

Tạo tệp **UnboxingNullDemo.java** với toàn bộ nội dung sau:

~~~java
import java.util.Objects;

public final class UnboxingNullDemo {
    private static int requiredQuantity(Integer quantity) {
        return Objects.requireNonNull(quantity, "quantity is required");
    }

    private static int quantityOrZero(Integer quantity) {
        return quantity != null ? quantity : 0;
    }

    private static boolean isEnabled(Boolean flag) {
        return Boolean.TRUE.equals(flag);
    }

    public static void main(String[] args) {
        Integer present = 7;
        Integer missing = null;
        Boolean missingFlag = null;

        System.out.println("present=" + requiredQuantity(present));
        System.out.println("nullReference=" + (missing == null));
        System.out.println("safeDefault=" + quantityOrZero(missing));
        System.out.println("safeBoolean=" + isEnabled(missingFlag));
        System.out.println("safeGuard="
                + (missing != null && missing > 0));
        System.out.println("bothNullEqual=" + Objects.equals(missing, null));

        try {
            int number = missing;
            System.out.println("number=" + number);
        } catch (NullPointerException ex) {
            System.out.println("assignmentNpe=true");
        }

        try {
            int total = missing + 1;
            System.out.println("total=" + total);
        } catch (NullPointerException ex) {
            System.out.println("arithmeticNpe=true");
        }

        try {
            boolean isZero = missing == 0;
            System.out.println("isZero=" + isZero);
        } catch (NullPointerException ex) {
            System.out.println("comparisonNpe=true");
        }

        try {
            if (missingFlag) {
                System.out.println("enabled");
            }
        } catch (NullPointerException ex) {
            System.out.println("booleanNpe=true");
        }

        try {
            int chosen = true ? missing : 10;
            System.out.println("chosen=" + chosen);
        } catch (NullPointerException ex) {
            System.out.println("ternaryNpe=true");
        }

        try {
            requiredQuantity(missing);
        } catch (NullPointerException ex) {
            System.out.println("requiredError=" + ex.getMessage());
        }

        Integer[] values = {3, null, 5};
        int sum = 0;
        for (Integer element : values) {
            if (element != null) {
                sum += element;
            }
        }
        System.out.println("nonNullSum=" + sum);
    }
}
~~~

Biên dịch/chạy từ thư mục chứa tệp (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 UnboxingNullDemo.java
java UnboxingNullDemo
~~~

**Expected output chính xác:**

~~~text
present=7
nullReference=true
safeDefault=0
safeBoolean=false
safeGuard=false
bothNullEqual=true
assignmentNpe=true
arithmeticNpe=true
comparisonNpe=true
booleanNpe=true
ternaryNpe=true
requiredError=quantity is required
nonNullSum=8
~~~

Giải thích kết quả:

1. `requiredQuantity(present)` nhận `Integer(7)` hợp lệ, trả primitive 7. `missing == null` chỉ kiểm tra tham chiếu nên không gây lỗi.
2. `quantityOrZero(null)` chọn số 0 theo hợp đồng hàm. `Boolean.TRUE.equals(null)` trả false. Biểu thức `missing != null && missing > 0` dừng tại vế trái false nên không unbox.
3. Gán sang `int`, cộng với 1, so với `int 0` và dùng `Boolean` trong `if` đều cần unboxing của một tham chiếu `null`: cả bốn khối bắt NPE tương ứng.
4. `true ? missing : 10` chọn nhánh `missing`, nhưng kiểu biểu thức kết quả cần `int`, nên unboxing xảy ra và gây NPE. Dấu `?:` **không tự động bảo vệ khỏi null**.
5. `Objects.requireNonNull(missing, "quantity is required")` ném NPE **có chủ đích tại ranh giới hàm**, với thông điệp ổn định `quantity is required`.
6. Mảng `Integer[]` chứa một phần tử `null`. Guard từng phần tử trước phép `sum += element` giúp chỉ cộng các giá trị có mặt: `3 + 5 = 8`.

Các khối `catch` ở đây **chỉ để minh họa từng nguồn NPE và in kết quả dễ kiểm chứng**. Trong ứng dụng thật, hãy kiểm tra đầu vào và xử lý đúng nghiệp vụ **trước khi unbox**, thay vì dùng `try/catch NullPointerException` như luồng điều khiển bình thường.

## 4. Đặt kiểm tra ở đâu cho đúng?

Đầu tiên cần quyết định `null` có phải trạng thái hợp lệ không.

| Hợp đồng nghiệp vụ | Cách xử lý | Lưu ý |
| --- | --- | --- |
| Giá trị **bắt buộc** | `Objects.requireNonNull(x, "x required")` | Phát hiện sớm, lỗi rõ tại ranh giới API |
| Giá trị tùy chọn, có default hợp lệ | `x != null ? x : defaultValue` | Chỉ dùng default khi nghiệp vụ cho phép |
| Cờ chỉ đúng khi tường minh true | `Boolean.TRUE.equals(flag)` | `null` được xem như không bật |
| Cần bỏ qua giá trị thiếu trong danh sách | `if (element != null) sum += element;` | Kiểm tra **từng phần tử**, không chỉ array/list |
| `null` biểu thị trạng thái chưa nhập | Giữ wrapper hoặc trả lỗi nghiệp vụ | Không tự biến thành 0/false khi điều đó sai nghĩa |

Nếu dữ liệu buộc phải có mặt, cách sau rõ ràng:

~~~java
static int requireScore(Integer score) {
    return Objects.requireNonNull(score, "score must not be null");
}
~~~

Hàm từ chối `null` **trước khi** chuyển sang `int`. Nếu yêu cầu đầu vào của một API là primitive và không có lý do biểu diễn thiếu dữ liệu, bạn có thể khai báo tham số `int` ngay từ đầu; tuy vậy, **người gọi vẫn có thể gây NPE** ở chỗ chuyển `Integer null` thành `int` trước lời gọi.

## 5. Guard an toàn và bẫy short-circuit

~~~java
Integer quantity = null;

boolean positive = quantity != null && quantity > 0; // false, an toàn.
// boolean wrongOrder = quantity > 0 && quantity != null; // NPE.
// boolean wrongOperator = quantity != null & quantity > 0; // NPE.
~~~

`&&` và `||` có **short-circuit**. Với `a && b`, vế phải **không chạy** khi vế trái false. Toán tử boolean `&`, trái lại, đánh giá cả hai vế nên có thể unbox một wrapper null dù điều kiện trước đã false.

Với biểu thức ba ngôi, cần xác định **kiểu của toàn biểu thức**, không chỉ nhìn từng nhánh:

~~~java
Integer nullable = null;
int safe = nullable == null ? 10 : nullable; // 10.
~~~

Ở đây kiểm tra `nullable == null` chọn nhánh `10`, tránh unboxing null. Nếu chọn nhánh `nullable`, nó chỉ được unbox khi đã không null.

**Chú ý:** `Objects.equals(a, b)` là cách so sánh wrapper chịu được `null`. Nhưng `Objects.equals(null, null)` trả **true**; chỉ dùng nếu quy tắc đó hợp nghiệp vụ.

## 6. Nhận diện NPE qua stack trace và kiểu dữ liệu

Khi nhìn một NPE thực tế:

1. Tìm frame đầu tiên thuộc mã của bạn và **dòng** phát sinh lỗi.
2. Xác định biến nào có kiểu **wrapper** như `Integer`/`Boolean`, thay vì primitive.
3. Kiểm tra biểu thức có **ngữ cảnh đòi primitive**: toán tử `+`, `-`, `*`, `/`, so sánh với literal số, `if (Boolean)`, phép gán sang primitive hoặc `?:`.
4. Lần về nơi wrapper được gán `null`: dữ liệu DB nullable, DTO, kết quả tìm kiếm, phần tử collection hoặc giá trị chưa khởi tạo.
5. Quyết định một trong hai hợp đồng: **từ chối null sớm** hoặc **xử lý null hợp lệ theo quy tắc**. Bổ sung testcase cho cả trường hợp giá trị hợp lệ và `null`.

**Không nhầm lỗi khác:** `Integer.parseInt("abc")` ném `NumberFormatException`, không phải NPE do unboxing. Còn `Integer.parseInt(null)` cũng cần được xem xét theo tài liệu API riêng, không suy diễn mọi lỗi liên quan `null` đều là NPE tự động.

## 7. Bài tập tự kiểm tra

**Bài 1.** Cho `Integer x = null;`. Dự đoán `x == null`, `x == 0` và `int y = x;`. Giải thích cụ thể thao tác nào buộc unbox.

**Bài 2.** Cho `Boolean approved = null;`. Viết một cách kiểm tra an toàn khi nghiệp vụ xem `null` như false, và một cách từ chối `null` khi trạng thái phê duyệt là bắt buộc.

**Bài 3.** Cho `Integer[] data = {2, null, 4};`. Viết hàm tính tổng, bỏ qua phần tử null một cách có chủ đích. Nêu kết quả mong đợi.

**Bài 4.** Vì sao `Integer a = null; int chosen = true ? a : 5;` gây NPE? Sửa thành biểu thức ba ngôi có guard.

**Bài 5.** Một API nhận `Integer quantity` nhưng không chấp nhận giá trị thiếu. Viết hàm kiểm tra ngay đầu vào, thông báo lỗi cụ thể, sau đó thực hiện tính toán chỉ với giá trị hợp lệ.

### Ghi nhớ

**`null` có thể tồn tại trong wrapper nhưng không thể biến thành một giá trị primitive.** NPE do unboxing thường xuất hiện ngay tại **gán, phép toán, so sánh, điều kiện hoặc biểu thức ba ngôi** cần primitive. Phát hiện bằng cách đọc kiểu dữ liệu và ngữ cảnh toán tử; phòng tránh bằng **hợp đồng null rõ ràng**, guard đúng thứ tự, `Objects.requireNonNull` cho dữ liệu bắt buộc hoặc default có chủ đích khi dữ liệu được phép thiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-004">Phân biệt so sánh tham chiếu lớp bao với so sánh giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt so sánh tham chiếu lớp bao với so sánh giá trị — Java 17+

Trong Java, các lớp bao (*wrapper classes*) như `Integer`, `Long`, `Double`, `Float`, `Boolean`, `Character`, `Byte`, `Short` là **kiểu tham chiếu** bao bọc các giá trị nguyên thủy. Vì vậy, hai câu hỏi sau **không giống nhau**:

1. **Hai biến có trỏ cùng một đối tượng không?** Dùng `==` khi hai toán hạng thực sự được so sánh dưới dạng tham chiếu.
2. **Hai đối tượng có giá trị bằng nhau theo quy tắc của lớp bao không?** Dùng `equals()` hoặc `Objects.equals()`, tùy việc có thể gặp `null`.

~~~java
Integer a = Integer.valueOf(100);
Integer b = Integer.valueOf(100);

System.out.println(a == b);      // true trong truong hop gia tri 100 duoc cache.
System.out.println(a.equals(b)); // true do gia tri bang nhau.
~~~

**Điểm quan trọng:** kết quả `a == b` là `true` **không phải vì `==` đã so giá trị số**. Đây là hệ quả của việc hai tham chiếu cùng trỏ một wrapper được dùng chung thông qua cơ chế cache.

## 1. Bảng phân biệt các phép so sánh

| Biểu thức | Ý nghĩa chính | Có an toàn với null không? |
| --- | --- | --- |
| `wrapperA == wrapperB` | So **đồng nhất tham chiếu** khi cả hai là wrapper tham chiếu cùng kiểu | Có, nếu chỉ so tham chiếu |
| `wrapperA.equals(wrapperB)` | So **giá trị wrapper** theo triển khai `equals` | Không nếu `wrapperA == null` |
| `Objects.equals(wrapperA, wrapperB)` | So bằng nhau, có xử lý hai vế `null` | Có |
| `intA == intB` | So giá trị primitive `int` | Primitive không nhận `null` |
| `wrapperA == intB` | Wrapper có thể được **unbox**, rồi so giá trị primitive | Không an toàn nếu wrapper là `null` |
| `Integer.compare(a, b)` | So thứ tự hai `int`, trả âm/0/dương | `a` và `b` là primitive |
| `wrapperA.compareTo(wrapperB)` | So thứ tự hai wrapper tương thích | Có thể ném NPE khi dùng null |

Java chọn quy tắc dựa trên **kiểu toán hạng**, không phải vì một bên “trông giống số”. Đặc biệt, `Integer == Integer` (hai wrapper) và `Integer == int` (một wrapper, một primitive) có thể dẫn đến **hai kiểu so sánh khác nhau**.

## 2. Ví dụ Java 17+ chạy được

Lưu thành tệp UTF-8 **`WrapperEqualityDemo.java`**:

~~~java
import java.util.Objects;

public class WrapperEqualityDemo {
    public static void main(String[] args) {
        Integer smallA = Integer.valueOf(100);
        Integer smallB = Integer.valueOf(100);
        Integer alias = smallA;

        System.out.println("smallSameReference=" + (smallA == smallB));
        System.out.println("aliasSameReference=" + (alias == smallA));
        System.out.println("smallValueEqual=" + smallA.equals(smallB));

        Integer largeA = Integer.valueOf(1_000);
        Integer largeB = Integer.valueOf(1_000);
        System.out.println("largeValueEqual=" + largeA.equals(largeB));
        System.out.println("largeObjectsEqual="
                + Objects.equals(largeA, largeB));

        Integer integer42 = Integer.valueOf(42);
        Long long42 = Long.valueOf(42L);
        System.out.println("crossWrapperEquals="
                + integer42.equals(long42));
        System.out.println("compareOrder="
                + Integer.valueOf(5).compareTo(Integer.valueOf(9)));

        int primitive100 = 100;
        System.out.println("wrapperVsPrimitive="
                + (smallA == primitive100));
        System.out.println("equalsBoxedArgument="
                + smallA.equals(100));

        Integer missing = null;
        Integer alsoMissing = null;
        System.out.println("bothNullReferences="
                + (missing == alsoMissing));
        System.out.println("nullVsWrapper=" + (missing == smallA));
        System.out.println("bothNullObjects="
                + Objects.equals(missing, alsoMissing));
        System.out.println("mixedObjects="
                + Objects.equals(missing, smallA));
        System.out.println("knownEqualsNull=" + smallA.equals(missing));
        System.out.println("guardedCompare="
                + (missing != null && missing.intValue() == 100));

        try {
            System.out.println("dangerousCompare="
                    + (missing == primitive100));
        } catch (NullPointerException ex) {
            System.out.println("unboxNull=NullPointerException");
        }

        try {
            System.out.println("dangerousEquals="
                    + missing.equals(smallA));
        } catch (NullPointerException ex) {
            System.out.println("equalsNull=NullPointerException");
        }

        System.out.println("nanPrimitive="
                + (Double.NaN == Double.NaN));
        System.out.println("nanWrapper="
                + Double.valueOf(Double.NaN)
                        .equals(Double.valueOf(Double.NaN)));
        System.out.println("signedZeroPrimitive=" + (0.0d == -0.0d));
        System.out.println("signedZeroWrapper="
                + Double.valueOf(0.0d)
                        .equals(Double.valueOf(-0.0d)));
        System.out.println("signedZeroCompare="
                + Double.compare(0.0d, -0.0d));

        System.out.println("booleanWrapper="
                + Boolean.TRUE.equals(Boolean.valueOf(true)));
        System.out.println("numericDifferentWrappers="
                + Double.valueOf(1.0).equals(Integer.valueOf(1)));
    }
}
~~~

Trong PowerShell (Windows) hoặc Bash (Linux/macOS), chạy tại thư mục có tệp bằng **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 WrapperEqualityDemo.java
java WrapperEqualityDemo
~~~

**Expected output chính xác của lệnh `java WrapperEqualityDemo`:**

~~~text
smallSameReference=true
aliasSameReference=true
smallValueEqual=true
largeValueEqual=true
largeObjectsEqual=true
crossWrapperEquals=false
compareOrder=-1
wrapperVsPrimitive=true
equalsBoxedArgument=true
bothNullReferences=true
nullVsWrapper=false
bothNullObjects=true
mixedObjects=false
knownEqualsNull=false
guardedCompare=false
unboxNull=NullPointerException
equalsNull=NullPointerException
nanPrimitive=false
nanWrapper=true
signedZeroPrimitive=true
signedZeroWrapper=false
signedZeroCompare=1
booleanWrapper=true
numericDifferentWrappers=false
~~~

Các lệnh kiểm tra `java -version` và `javac -version` có đầu ra tùy JDK, không thuộc khối expected output trên.

## 3. Cache chỉ là một nguyên nhân khiến phép `==` có vẻ đúng

`Integer.valueOf(int)` được bảo đảm tái sử dụng đối tượng cho các số từ **−128 đến 127**:

~~~java
Integer a = Integer.valueOf(100);
Integer b = Integer.valueOf(100);
System.out.println(a == b); // true, vì cùng đối tượng được cache.
~~~

Với giá trị ngoài vùng bắt buộc, ví dụ `1_000`, không nên công bố output cố định cho `Integer.valueOf(1000) == Integer.valueOf(1000)`; JVM có thể cache rộng hơn. `equals` mới là hợp đồng so giá trị ổn định. Bài kế tiếp giải thích riêng về phạm vi cache của `Byte`, `Short`, `Long`, `Character`, `Boolean` và những gì **không được bảo đảm** về object identity.


## 4. Khi một vế là primitive, `==` có thể kích hoạt unboxing

~~~java
Integer wrapper = Integer.valueOf(100);
int number = 100;
System.out.println(wrapper == number); // true.
~~~

Với `Integer == int`, Java có thể **unbox** wrapper thành `int` và áp dụng phép so số primitive. Vì vậy kết quả phản ánh giá trị số **nhưng chỉ sau khi unboxing**.

Nếu wrapper là `null`:

~~~java
Integer maybe = null;
int number = 100;

// boolean same = maybe == number;
// Runtime: NullPointerException khi unboxing maybe.
~~~

Ngược lại, phép so sau **không unbox** chỉ vì gặp `null` nếu cả hai toán hạng có kiểu tham chiếu cùng kiểu:

~~~java
Integer a = null;
Integer b = Integer.valueOf(100);

System.out.println(a == b); // false, khong NPE.
~~~

**Quy tắc nhớ:** hãy xem **kiểu tĩnh của cả hai toán hạng** trước khi giải thích `==`, và tránh những biểu thức so sánh gây unboxing một wrapper có thể null.

## 5. `equals()` của wrapper so cái gì?

Với `Integer`, phương thức `equals` trả `true` khi đối số là một **`Integer`** chứa cùng giá trị `int`:

~~~java
Integer a = Integer.valueOf(42);
Integer b = Integer.valueOf(42);
Long c = Long.valueOf(42L);

System.out.println(a.equals(b)); // true.
System.out.println(a.equals(c)); // false.
~~~

`Integer(42)` và `Long(42)` mang **giá trị toán học** tương ứng nhưng **không phải cùng kiểu wrapper**. `equals` của hai lớp không tự chuyển `Long` sang `Integer`.

Đối với `Double`/`Float`, còn có hai điểm đặc biệt:

| Cặp dữ liệu | So bằng primitive `==` | Wrapper `equals` |
| --- | --- | --- |
| `Double.NaN` và `Double.NaN` | `false` | `true` |
| `+0.0d` và `-0.0d` | `true` | `false` |

`Double.equals` dùng quy tắc bit của `Double.doubleToLongBits`: các biểu diễn NaN được chuẩn hóa khi kiểm tra bình đẳng, còn dấu của zero được phân biệt. `Float.equals` có cách xử lý tương tự. Vì thế **không thay thế tùy tiện** so sánh số thực primitive bằng `equals` nếu đặc tả bài toán cần quy tắc IEEE 754 của toán tử `==`.

Nếu muốn so thứ tự, `Integer.compare`, `Long.compare`, `Double.compare` hoặc `compareTo` có ngữ nghĩa khác với kiểm tra cùng tham chiếu. `Double.compare(+0.0, -0.0)` trả kết quả **dương**, như ví dụ.

## 6. `null`: cách so an toàn

**`equals` có thể ném NPE khi đối tượng nhận lời gọi là null:**

~~~java
Integer a = null;
Integer b = 10;

// a.equals(b); // NullPointerException.
System.out.println(Objects.equals(a, b)); // false.
~~~

`Objects.equals(a, b)` kiểm tra an toàn khi một hoặc cả hai biến có thể null; hai giá trị null được coi là bằng nhau. Tuy vậy, việc **“hai giá trị đều thiếu” có nên được coi là tương đương trong nghiệp vụ hay không** là quyết định riêng của ứng dụng.

| Yêu cầu | Cách viết | Ghi chú |
| --- | --- | --- |
| Wrapper biết chắc không null, cùng kiểu | `a.equals(b)` | So giá trị wrapper |
| Hai wrapper có thể null | `Objects.equals(a, b)` | Null-safe, dùng `equals` cho đối tượng |
| Một wrapper so với giá trị cố định | `Integer.valueOf(10).equals(a)` | Đối tượng nhận lời gọi chắc chắn khác null |
| Muốn so primitive và wrapper nullable | `a != null && a.intValue() == 10` | Kiểm tra null trước unboxing |
| Muốn thứ tự tăng/giảm | `Integer.compare(x, y)` | Dùng primitive; xử lý null trước nếu nguồn là wrapper |
| Muốn kiểm tra cùng đối tượng | `a == b` | Chỉ khi identity thật sự là mục tiêu |

**Không dùng `Objects.equals` như một phép so số học xuyên kiểu:** `Objects.equals(Integer.valueOf(1), Long.valueOf(1))` là `false` vì hai wrapper khác lớp.

## 7. Autoboxing, cache và toán tử gán lại

~~~java
Integer a = 7;
Integer b = a;

a = a + 1;
System.out.println(a); // 8.
System.out.println(b); // 7.
~~~

`a + 1` unbox `Integer` thành `int`, cộng thành 8, sau đó boxing kết quả để gán lại `a`. Wrapper bất biến: phép gán này **không sửa** đối tượng đang được `b` tham chiếu.

Cache là tối ưu hóa khả thi cho đối tượng bất biến; sự tồn tại của cache không làm `Integer` trở thành primitive. Đừng cố suy đoán số lượng đối tượng được cấp phát chỉ từ cú pháp boxing nếu không có kiểm chứng thực tế và không phải trọng tâm nghiệp vụ.

## 8. Bài tập tự kiểm tra

1. **Hai wrapper `Integer`:** `Integer a = 100, b = 100; a == b` trả gì?  
   *Đáp án:* `true` với autoboxing hằng 100 thuộc vùng cache bắt buộc; nhưng không dùng `==` như phép so giá trị nói chung.
2. **Wrapper lớn:** `Integer a = Integer.valueOf(1000); Integer b = Integer.valueOf(1000); a.equals(b)` trả gì?  
   *Đáp án:* `true`. Không cần giả định kết quả `a == b`.
3. **Khác loại:** `Integer.valueOf(42).equals(Long.valueOf(42L))` trả gì?  
   *Đáp án:* `false` vì đối số không phải `Integer`.
4. **Unboxing:** `Integer x = null; boolean same = (x == 10);` xảy ra gì?  
   *Đáp án:* Ném `NullPointerException` khi unbox `x`.
5. **Null-safe:** `Objects.equals(null, null)` và `Objects.equals(null, Integer.valueOf(1))` là gì?  
   *Đáp án:* Lần lượt `true` và `false`.
6. **NaN:** Tại sao `Double.NaN == Double.NaN` là false, còn `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` là true?  
   *Đáp án:* Phép `==` primitive tuân theo quy tắc NaN số thực; wrapper `equals` áp dụng quy tắc bình đẳng trên biểu diễn bit đã chuẩn hóa NaN.
7. **Zero có dấu:** `Double.valueOf(0.0).equals(Double.valueOf(-0.0))` là gì?  
   *Đáp án:* `false`; wrapper phân biệt dấu của zero, dù `0.0 == -0.0` là `true`.

## Checklist

- [ ] Phân biệt **identity tham chiếu** với **bình đẳng giá trị wrapper**.
- [ ] Với hai wrapper cùng kiểu, `==` kiểm tra đối tượng; `equals` kiểm tra giá trị wrapper.
- [ ] Không dựa vào cache `Integer` để suy đoán kết quả của `==` cho mọi số.
- [ ] Khi wrapper so với primitive, kiểm tra nguy cơ **unboxing null**.
- [ ] Dùng `Objects.equals` khi hai wrapper có thể null.
- [ ] Không coi `Integer` và `Long` có giá trị bằng nhau là `equals` bằng nhau.
- [ ] Nhớ `Double`/`Float` khác biệt về `NaN` và `+0.0`/`-0.0`.
- [ ] Chọn `compare`/`compareTo` khi cần so thứ tự thay vì bình đẳng.

**Kết luận:** Muốn kiểm tra **cùng đối tượng**, hãy dùng `==` giữa tham chiếu và hiểu rõ cache. Muốn kiểm tra **cùng giá trị lớp bao**, dùng `equals` hoặc `Objects.equals` (nếu có null). Với một bên primitive, luôn xem xét **unboxing** trước khi tin rằng phép `==` an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-005">Bộ đệm đối tượng lớp bao và vì sao không dựa vào ==</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ đệm đối tượng lớp bao và vì sao không dựa vào `==` — Java 17+

## 1. Câu hỏi cốt lõi: cùng số có phải cùng đối tượng?

Java có kiểu nguyên thủy `int` và lớp bao tương ứng `Integer`. Hai biến `Integer` có thể chứa **cùng giá trị số** nhưng **không nhất thiết cùng tham chiếu tới một object**.

~~~java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);

System.out.println(a == b);      // true: vùng cache được bảo đảm
System.out.println(a.equals(b)); // true: cùng giá trị Integer
~~~

Kết quả `a == b` ở ví dụ này là `true` vì `Integer.valueOf` bảo đảm tái sử dụng object cho khoảng giá trị **-128 đến 127**. Nhưng hãy đổi giá trị sang `1000`:

~~~java
Integer x = Integer.valueOf(1000);
Integer y = Integer.valueOf(1000);

System.out.println(x.equals(y)); // true: luôn bằng nhau về giá trị
// Không dựa vào (x == y): identity ở giá trị này không được bảo đảm.
~~~

**Không có expected output cố định cho `x == y` ở ví dụ 1000.** Một số JVM/cấu hình cache thêm số lớn; một số khác không. Việc code chạy “đúng” trên máy hiện tại không biến cách so identity thành phép so giá trị được bảo đảm trên mọi môi trường Java.

**Kết luận trước khi đi sâu:** muốn biết **giá trị của hai wrapper có bằng nhau không**, dùng `equals` hoặc `Objects.equals` tùy khả năng `null`. Chỉ dùng `==` trên hai tham chiếu khi **chủ đích thật sự là kiểm tra cùng object**.

## 2. Vì sao wrapper có thể được cache?

Wrapper là đối tượng tham chiếu **bất biến** chứa một giá trị nguyên thủy; ví dụ `Integer` đóng gói `int`, `Character` đóng gói `char`. Vì object không thể đổi giá trị sau khi tạo, Java có thể chia sẻ wrapper cho những giá trị thường gặp mà không làm thay đổi nghĩa của giá trị.

~~~java
int primitive = 10;
Integer wrapper = primitive; // Autoboxing.
System.out.println(wrapper.equals(10)); // true: so giá trị.
~~~

`int` không mang identity tham chiếu, còn `Integer` có thể `null` và dùng trong `List<Integer>`. Các cặp primitive/wrapper và quy tắc boxing đã được trình bày ở bài tổng quan; ở đây tập trung **vùng cache, bảo đảm identity và lý do không dùng cache trong business logic**.


## 3. Cache đối tượng lớp bao là gì?

Khi cần biểu diễn các giá trị xuất hiện rất thường xuyên, class wrapper có thể **tái sử dụng những object bất biến** đã có. Gọi là *wrapper cache*.

Mô hình ý tưởng:

~~~mermaid
flowchart LR
    A["Integer a = 127"] -->|"autoboxing"| C["Integer instance: 127"]
    B["Integer b = 127"] -->|"autoboxing"| C
    X["Integer x = 1000"] -->|"identity ngoài vùng bảo đảm"| D["Object tùy triển khai"]
    Y["Integer y = 1000"] -->|"identity ngoài vùng bảo đảm"| E["Object tùy triển khai"]
~~~

Các mũi tên tới hai object ở giá trị 1000 chỉ nhằm nhấn mạnh **không thể suy ra identity theo giá trị**: tùy cấu hình, hai biến đó *có thể* tham chiếu cùng object hoặc khác object. Sơ đồ không được dùng để khẳng định luôn có hai object cho số 1000.

**Vì sao cache có ích?** Object wrapper là immutable; dùng lại một object cho giá trị nhất định không làm thay đổi nội dung của object đó. Việc chia sẻ đối tượng có thể giảm nhu cầu tạo object mới ở một số tình huống, nhưng **cache là chi tiết của cơ chế triển khai với phạm vi bảo đảm nhất định**, không phải phép so giá trị dành cho ứng dụng.

## 4. Các khoảng identity được bảo đảm và phần không bảo đảm

Trong Java 17, những giá trị boxed thường dùng có **một vùng identity được bảo đảm**. Các factory tương ứng thể hiện điều này qua đặc tả API:

| Wrapper | Giá trị được bảo đảm tái sử dụng khi boxing/`valueOf` | Ghi chú |
|---|---|---|
| `Byte` | Toàn bộ `byte`: -128..127 | Toàn bộ miền Byte |
| `Short` | -128..127 | Số ngoài vùng này có thể được cache thêm |
| `Integer` | -128..127 | Vùng cache quen thuộc |
| `Long` | -128..127 | Đừng suy rộng tới mọi `long` |
| `Character` | U+0000..U+007F | 128 UTF-16 code units đầu tiên |
| `Boolean` | `true` và `false` | Hai instance chuẩn `Boolean.TRUE`/`FALSE` |
| `Float`, `Double` | Không có khoảng cache giá trị số tương tự được bảo đảm cho mọi boxing | Không dựa vào identity của wrapper số thực |

Ví dụ kết quả có thể dự đoán:

~~~java
Integer x = Integer.valueOf(-128);
Integer y = Integer.valueOf(-128);
System.out.println(x == y); // true

Character c1 = Character.valueOf('A');
Character c2 = Character.valueOf('A');
System.out.println(c1 == c2); // true

Long l1 = Long.valueOf(127L);
Long l2 = Long.valueOf(127L);
System.out.println(l1 == l2); // true
~~~

**Cần hiểu kỹ:** với `Integer.valueOf(128)` hoặc `Long.valueOf(1000L)`, Java có thể dùng cache mở rộng; bạn **không được bảo đảm true hoặc false** khi so hai kết quả boxing độc lập bằng `==`. Một số cấu hình JVM có tùy chọn mở rộng cache `Integer`, nhưng giá trị cấu hình không nên trở thành điều kiện đúng của business logic.

**Không tạo wrapper bằng constructor để thử “ép ra hai object”.** Các constructor số wrapper như `new Integer(...)` đã bị deprecated để loại bỏ trong Java mới. `valueOf`, autoboxing và các API so nội dung thích hợp là cách dùng thông thường.

## 5. `==` kiểm tra identity, `equals` kiểm tra nội dung

Với **hai biểu thức đều có kiểu tham chiếu `Integer`**, toán tử `==` kiểm tra chúng có trỏ cùng object hay không:

~~~java
Integer first = Integer.valueOf(1000);
Integer alias = first;
Integer second = Integer.valueOf(1000);

System.out.println(first == alias); // true: gán alias không tạo object khác
System.out.println(first.equals(second)); // true: cùng giá trị 1000
~~~

Không in `first == second` kèm expected output vì Java không bảo đảm identity cho giá trị 1000.

Phân biệt các phép so:

| Biểu thức | Ý nghĩa |
|---|---|
| `a == b` với hai `int` | So giá trị primitive |
| `a == b` với hai `Integer` | So identity tham chiếu |
| `a.equals(b)` với `Integer` không null | So giá trị của hai `Integer` |
| `Objects.equals(a, b)` | So equality và xử lý trường hợp null |
| `a.compareTo(b) == 0` với `Integer` không null | So thứ tự số, kết quả 0 khi cùng giá trị |

`Integer.equals` chỉ xem wrapper `Integer` có cùng giá trị, không tự chuyển mọi kiểu số khác nhau thành `Integer`:

~~~java
Integer n = Integer.valueOf(5);
Long m = Long.valueOf(5L);
System.out.println(n.equals(m)); // false
~~~

Đây là **khác kiểu wrapper**, không phải do cache.

## 6. `==` giữa wrapper và primitive: có thể là so số

Xét:

~~~java
Integer boxed = Integer.valueOf(1000);
int primitive = 1000;

System.out.println(boxed == primitive); // true
~~~

**Vì sao true dù 1000 ngoài vùng cache bảo đảm?** Một toán hạng có kiểu `int`, vì vậy Java **unbox** `boxed` rồi so hai giá trị primitive. Đây không phải reference identity.

Điều này khác rõ rệt với:

~~~java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);
// a == b là so tham chiếu: không dùng để kiểm tra số bằng nhau.
~~~

Vì thế phát biểu “`==` với Integer luôn sai” cũng không chính xác. Phải xét **kiểu của cả hai toán hạng**:

- `Integer == Integer`: so tham chiếu.
- `Integer == int`: unboxing rồi so giá trị số, nếu wrapper không null.
- `int == int`: so giá trị số.
- `Integer.equals(Integer)`: so giá trị được class Integer định nghĩa.

**Bẫy null:** nếu `boxed == null`, biểu thức `boxed == primitive` sẽ ném `NullPointerException` khi unbox. Ngược lại, `boxed == null` là phép kiểm tra tham chiếu null an toàn.

## 7. Null không phải một giá trị được cache

Một biến `Integer` có thể mang `null`, khác với một object `Integer.valueOf(0)`. So sánh `maybe == null` không unbox; còn `maybe == 0` có thể unbox và ném `NullPointerException`:

~~~java
Integer maybe = null;
System.out.println(maybe == null); // true.
System.out.println(java.util.Objects.equals(maybe, (Integer) null)); // true.
~~~

`Objects.equals(a,b)` xử lý cả hai null theo ngữ nghĩa equality; nhưng nó không thay thế validation nếu nghiệp vụ yêu cầu giá trị hiện diện. Việc cache một wrapper không làm một tham chiếu null trở thành “cached zero”, và không thay đổi quy tắc NPE khi unboxing.


## 8. Cache không đồng nghĩa wrapper có thể thay đổi

Wrapper tiêu chuẩn như `Integer`, `Long`, `Character` là **immutable**. Khi cùng chia sẻ object cached, thay đổi một biến không thể sửa “giá trị bên trong object cached” để ảnh hưởng người khác.

~~~java
Integer before = Integer.valueOf(5);
Integer count = before;
count++;

System.out.println(before); // 5
System.out.println(count);  // 6
~~~

Về ngữ nghĩa, `count++` thực hiện **unbox → cộng 1 → box → gán lại biến `count`**, không sửa object cũ. Biến `before` vẫn giữ giá trị 5.

~~~java
final Integer limit = Integer.valueOf(5);
// limit++; // Lỗi compile: phép ++ cần gán lại biến final
~~~

Nếu bạn thật sự cần một bộ đếm mutable chia sẻ giữa các thành phần, wrapper `Integer` không đáp ứng vai trò đó. Hãy thiết kế một object có trạng thái mutable, hoặc dùng `AtomicInteger` khi phù hợp với yêu cầu tương tranh.

## 9. Collection dùng equals, không dựa vào cache

Các collection thông dụng dùng hợp đồng `equals` và `hashCode` để kiểm tra phần tử/khóa.

~~~java
import java.util.List;
import java.util.Map;

Integer key1 = Integer.valueOf(1000);
Integer key2 = Integer.valueOf(1000);

List<Integer> list = List.of(key1);
Map<Integer, String> map = Map.of(key1, "found");

System.out.println(list.contains(key2)); // true
System.out.println(map.get(key2));       // found
~~~

Ở đây giá trị `1000` nằm ngoài vùng cache bắt buộc, **nhưng** `List.contains` và `Map.get` vẫn xử lý đúng theo equality của `Integer`. Không phải kiểm tra hai tham chiếu bằng `==`.

**Ngoại lệ cố ý:** `IdentityHashMap` dùng identity (`==`) cho key, nên không tương đương `HashMap` trong nghiệp vụ yêu cầu equality theo giá trị. Đừng đổi qua `IdentityHashMap` chỉ vì muốn tối ưu hoặc vì nghe tên có “map”.

Hãy coi việc chọn collection là chọn **hợp đồng so sánh**, không phải chọn một cách tận dụng cache.

## 10. Không dự đoán cấp phát hoặc hiệu năng từ cache

Không phải mỗi lệnh autobox đều nhất định tạo object mới; cũng không phải mọi wrapper ngoài vùng cache đều nhất định cấp phát object mới. JVM có thể dùng cache hoặc thực hiện tối ưu hóa nội bộ, còn ngữ nghĩa chương trình phải giữ đúng theo Java.

~~~java
Integer a = 1000;
Integer b = 1000;
// Không thể dùng (a == b) làm phép đo tin cậy về cấp phát,
// và không thể dựa vào kết quả này để quyết định a,b có cùng giá trị.
~~~

**Các sai lầm cần tránh:**

- Dựa vào `System.identityHashCode(a)` để tuyên bố hai object có cùng địa chỉ; identity hash không phải địa chỉ bộ nhớ và có thể trùng giữa các object khác nhau.
- In `a.toString()` rồi so chuỗi hiển thị để quyết định hai tham chiếu có giống nhau không.
- Chuyển sang `int` chỉ để tránh cache mà không cân nhắc `null`.
- Viết business logic phụ thuộc kết quả `Integer.valueOf(128) == Integer.valueOf(128)`.
- Kết luận tốc độ hoặc lượng bộ nhớ từ một vài lần đo thủ công không warm-up.

Trong những vòng lặp tính số nhiều lần, **primitive `int`/`long` thường là lựa chọn mặc định dễ hiểu hơn** nếu không cần nullable hay generic. Nếu cần đo hiệu năng Java, dùng công cụ benchmark đúng phương pháp (ví dụ JMH) và đo trên dữ liệu tương đương thực tế.

## 11. Chương trình Java 17 hoàn chỉnh, chạy độc lập

Tạo tệp đúng tên **`WrapperCacheDemo.java`**:

~~~java
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WrapperCacheDemo {
    private WrapperCacheDemo() {}

    public static void main(String[] args) {
        Integer small1 = Integer.valueOf(127);
        Integer small2 = Integer.valueOf(127);
        System.out.println("Integer cached 127=" + (small1 == small2));

        Byte byte1 = Byte.valueOf((byte) 127);
        Byte byte2 = Byte.valueOf((byte) 127);
        System.out.println("Byte cached 127=" + (byte1 == byte2));

        Short short1 = Short.valueOf((short) -128);
        Short short2 = Short.valueOf((short) -128);
        System.out.println("Short cached -128=" + (short1 == short2));

        Long long1 = Long.valueOf(127L);
        Long long2 = Long.valueOf(127L);
        System.out.println("Long cached 127=" + (long1 == long2));

        Character char1 = Character.valueOf('A');
        Character char2 = Character.valueOf('A');
        System.out.println("Character cached A=" + (char1 == char2));

        Boolean bool1 = Boolean.valueOf(true);
        Boolean bool2 = Boolean.valueOf(true);
        System.out.println("Boolean shared true=" + (bool1 == bool2));

        Integer large1 = Integer.valueOf(1000);
        Integer large2 = Integer.valueOf(1000);
        Integer alias = large1;
        // Không dùng large1 == large2: identity ngoài vùng cache bảo đảm.
        System.out.println("large equality=" + large1.equals(large2));
        System.out.println("large compareTo=" + (large1.compareTo(large2) == 0));
        System.out.println("large alias identity=" + (large1 == alias));
        System.out.println("wrapper vs int=" + (large1 == 1000));

        Integer missing = null;
        System.out.println("null check=" + (missing == null));
        System.out.println("two nulls equal="
                + Objects.equals(missing, (Integer) null));
        System.out.println("null vs value=" + Objects.equals(missing, large1));
        try {
            System.out.println("null vs primitive=" + (missing == 0));
        } catch (NullPointerException ex) {
            System.out.println("null unboxing=NullPointerException");
        }

        Integer previous = Integer.valueOf(5);
        Integer incremented = previous;
        incremented++;
        System.out.println("previous wrapper=" + previous);
        System.out.println("incremented wrapper=" + incremented);

        List<Integer> list = List.of(large1);
        Map<Integer, String> map = Map.of(large1, "found");
        System.out.println("list contains equal wrapper=" + list.contains(large2));
        System.out.println("map lookup equal wrapper=" + map.get(large2));

        Integer numericFive = Integer.valueOf(5);
        Long longFive = Long.valueOf(5L);
        System.out.println("cross-class equals=" + numericFive.equals(longFive));
    }
}
~~~

**Yêu cầu:** JDK 17+ và terminal đang trong thư mục chứa file. Ví dụ không cần Maven/Gradle hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 WrapperCacheDemo.java
java -cp . WrapperCacheDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\WrapperCacheDemo.java
java -cp . WrapperCacheDemo
~~~

**Expected output chính xác, không phụ thuộc cache mở rộng:**

~~~text
Integer cached 127=true
Byte cached 127=true
Short cached -128=true
Long cached 127=true
Character cached A=true
Boolean shared true=true
large equality=true
large compareTo=true
large alias identity=true
wrapper vs int=true
null check=true
two nulls equal=true
null vs value=false
null unboxing=NullPointerException
previous wrapper=5
incremented wrapper=6
list contains equal wrapper=true
map lookup equal wrapper=found
cross-class equals=false
~~~

**Vì sao đầu ra ổn định?** Chương trình chỉ in identity ở **vùng bảo đảm chia sẻ** hoặc với **alias thật sự**. Với 1000, chương trình dùng `equals`/`compareTo` và các collection theo equality, **không in kết quả `large1 == large2`**. Vì vậy cache ngoài -128..127 có mở rộng hay không cũng không làm expected output thay đổi.

Điểm cần quan sát thêm:

1. Các wrapper nguyên nhỏ và ký tự ASCII sử dụng identity được bảo đảm bởi cache.
2. Hai `Integer` biểu diễn 1000 vẫn so giá trị chính xác bằng `equals`, dù không dựa vào identity.
3. `Integer == int` có unboxing trước khi so; khác `Integer == Integer`.
4. Kiểm tra `missing == null` an toàn; `missing == 0` cố unbox null và gây ngoại lệ.
5. `incremented++` không thay giá trị mà biến `previous` quan sát.
6. `List.contains`/`Map.get` vẫn hoạt động theo equality của wrapper.
7. Wrapper khác class (`Integer` và `Long`) không tự `equals` chỉ vì cùng biểu diễn số 5.

## 12. Bảng tổng hợp: nên dùng phép so nào?

| Mục đích | Cách làm | Tại sao |
|---|---|---|
| Hai `int` có cùng giá trị không? | `a == b` | Cả hai primitive |
| Hai `Integer` không null có cùng giá trị? | `a.equals(b)` | So giá trị, không dựa vào cache |
| Hai `Integer` có thể null có bằng nhau? | `Objects.equals(a,b)` | Bảo vệ null |
| Hai biến tham chiếu có chỉ cùng object? | `a == b` | Chủ đích so identity |
| Một wrapper có null không? | `a == null` | Không unbox |
| So wrapper với primitive, biết wrapper không null | `boxed == primitive` | Unbox rồi so số |
| Giá trị wrapper có giống số nhưng khác lớp? | Chọn chính sách đổi kiểu có kiểm soát | `Integer.equals(Long)` không tự so số |
| Collection generic dùng `Integer` làm key | `HashMap`/`Map.of` theo `equals` | Không phụ thuộc identity cache |

**Không dùng `==` chỉ vì các ví dụ số nhỏ đều in `true`.** Đó là tình huống cache làm bug ẩn đi: khi dữ liệu được mở rộng hoặc chuyển JVM, kết quả identity có thể khác.

## 13. Những lỗi thường gặp

| Lỗi / giả định | Điều thực sự xảy ra | Cách xử lý |
|---|---|---|
| `Integer a = 127; Integer b = 127; a == b` true nên luôn dùng `==` | Giá trị 127 nằm vùng cache bảo đảm | Dùng `equals` để so giá trị |
| `Integer.valueOf(128) == Integer.valueOf(128)` chắc chắn false | Cache có thể được mở rộng | Không khẳng định true/false ngoài vùng bảo đảm |
| Hai `Integer` có cùng số thì luôn cùng object | Wrapper có identity riêng với cùng giá trị | So bằng `equals` |
| Hai `Integer` khác object thì có giá trị khác nhau | `==` không phản ánh equality | Dùng `equals` |
| `Integer a == int b` là so identity | Java unbox wrapper khi so số | Cẩn thận null |
| `null Integer == 0` trả false | Unboxing null gây NPE | Kiểm tra null trước |
| `a.equals(b)` an toàn khi a null | Gọi method trên null ném NPE | `Objects.equals(a, b)` |
| `Integer.valueOf(5).equals(Long.valueOf(5L))` true | equals của Integer yêu cầu đúng kiểu | Xác định chính sách so số khác kiểu |
| Dùng `new Integer(...)` để tránh cache | Constructor deprecated để loại bỏ | Dùng `valueOf`, không cần cố điều khiển identity |
| `System.identityHashCode` chính là địa chỉ object | Giá trị hash không phải địa chỉ và không duy nhất | Không suy địa chỉ từ hash |
| Cache tự biến wrapper thành mutable | Wrapper vẫn immutable | `++` là phép tính và gán lại |
| `HashMap` dùng `==` cho key | Thông thường dùng `equals` và `hashCode` | Dùng đúng collection theo semantics |
| Wrapper không tạo chi phí vì có cache | Cache không bảo đảm loại bỏ mọi cấp phát | Chọn primitive khi phù hợp, benchmark đúng |

## 14. Bài tập kèm đáp án

### Bài A — cùng tham chiếu

~~~java
Integer a = Integer.valueOf(1000);
Integer b = a;
System.out.println(a == b);
~~~

**Đáp án:** `true`. `b = a` sao chép cùng giá trị tham chiếu, không liên quan giá trị 1000 có được cache hay không.

### Bài B — cùng giá trị, identity không được bảo đảm

~~~java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);
System.out.println(a.equals(b));
~~~

**Đáp án:** `true`. Không nên viết expected output cố định cho `a == b` trong ví dụ này; ngữ nghĩa được yêu cầu là **bằng nhau về giá trị**.

### Bài C — cache số nhỏ

~~~java
Long x = Long.valueOf(127L);
Long y = Long.valueOf(127L);
System.out.println(x == y);
~~~

**Đáp án:** `true`. Vùng -128..127 được bảo đảm chia sẻ cho `Long.valueOf`.

### Bài D — wrapper so với primitive

~~~java
Integer wrapped = Integer.valueOf(1000);
int raw = 1000;
System.out.println(wrapped == raw);
~~~

**Đáp án:** `true`. Java unbox wrapped thành int rồi so giá trị primitive.

### Bài E — null và unboxing

~~~java
Integer value = null;
System.out.println(value == null);
// System.out.println(value == 1);
~~~

**Đáp án:** dòng đầu in `true`. Nếu chạy dòng cuối, auto-unboxing của value null làm ném `NullPointerException`.

### Bài F — HashMap dựa vào giá trị

~~~java
java.util.Map<Integer, String> map = new java.util.HashMap<>();
map.put(Integer.valueOf(1000), "ok");
System.out.println(map.get(Integer.valueOf(1000)));
~~~

**Đáp án:** `ok`. `Integer.hashCode`/`equals` dựa trên giá trị, nên key tương đương được tìm thấy mà không cần cùng object.

## 15. Checklist hoàn thành

- [ ] Phân biệt `int` primitive với `Integer` wrapper.
- [ ] Giải thích được boxing cache và lợi ích tái sử dụng wrapper immutable.
- [ ] Nhớ vùng identity được bảo đảm của `Integer.valueOf`: -128..127.
- [ ] Biết `Byte` cache toàn miền, `Character` cache 0..127, `Boolean` có các instance chuẩn.
- [ ] Biết ngoài vùng được bảo đảm, cache có thể mở rộng nên `==` không đáng tin để so giá trị.
- [ ] Biết `Integer == Integer` so tham chiếu, `Integer == int` unbox rồi so số.
- [ ] Dùng `equals` cho wrapper không null và `Objects.equals` khi có thể null.
- [ ] Không unbox `null` trong toán tử, phép gán hoặc tham số primitive.
- [ ] Hiểu wrapper immutable: `count++` không sửa object cũ.
- [ ] Biết `Integer.equals(Long)` không tự đổi kiểu wrapper để so số.
- [ ] Biết collection thông thường dùng equality, không dựa vào wrapper cache.
- [ ] Không lấy cache/identityHashCode làm cơ sở dự đoán hiệu năng hoặc địa chỉ bộ nhớ.
- [ ] Biên dịch và chạy `WrapperCacheDemo.java` với Java 17+, so đúng expected output.

**Kết luận:** Wrapper cache làm một số phép `==` trên các giá trị nhỏ trả `true`, nhưng **`==` không phải phép so giá trị giữa hai wrapper**. Cache ngoài những vùng được Java bảo đảm có thể thay đổi tùy JVM. Để code chính xác và dễ bảo trì, dùng `equals` hoặc `Objects.equals` khi muốn so giá trị, xử lý `null` trước unboxing và chỉ dùng `==` khi thật sự muốn kiểm tra cùng một đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-006">parseInt(), parseDouble(), valueOf() và kiểu kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# `parseInt()`, `parseDouble()`, `valueOf()` và kiểu kết quả — Java 17+

Khi đọc số từ cấu hình, tham số dòng lệnh hoặc biểu mẫu, Java thường nhận dữ liệu ban đầu dưới dạng `String`. Muốn tính toán, cần **phân tích chuỗi** (*parsing*) để lấy giá trị số. Các phương thức tên gần giống nhau nhưng **kiểu trả về khác nhau**:

- `Integer.parseInt("42")` trả về **primitive `int`**.
- `Double.parseDouble("3.5")` trả về **primitive `double`**.
- `Integer.valueOf("42")` trả về **đối tượng wrapper `Integer`**.
- `Double.valueOf("3.5")` trả về **đối tượng wrapper `Double`**.

Không có phương thức `valueOf()` chung cho mọi lớp với cùng kiểu trả về. **Tên lớp đứng trước dấu chấm quyết định phương thức và kiểu kết quả**: `Integer.valueOf(...)` trả `Integer`, `Long.valueOf(...)` trả `Long`, `String.valueOf(...)` trả `String`.

## 1. Bảng so sánh kiểu trả về

| Lệnh Java | Đối số | Kiểu trả về khai báo | Giá trị minh họa |
| --- | --- | --- | --- |
| `Integer.parseInt("42")` | `String` | `int` | 42 |
| `Integer.valueOf("42")` | `String` | `Integer` | 42 |
| `Integer.valueOf(42)` | `int` | `Integer` | 42 |
| `Double.parseDouble("3.5")` | `String` | `double` | 3.5 |
| `Double.valueOf("3.5")` | `String` | `Double` | 3.5 |
| `Double.valueOf(3.5)` | `double` | `Double` | 3.5 |
| `Long.parseLong("9000000000")` | `String` | `long` | 9000000000 |
| `Long.valueOf("9000000000")` | `String` | `Long` | 9000000000 |
| `Boolean.parseBoolean("true")` | `String` | `boolean` | true |
| `Boolean.valueOf("true")` | `String` | `Boolean` | true |
| `String.valueOf(42)` | `int` | `String` | `"42"` |

`parseInt` và `parseDouble` thuộc **lớp khác nhau**. Không có `Integer.parseDouble` hay `Double.parseInt`. Tên `valueOf` là **overload** trong mỗi lớp, không có nghĩa mọi `valueOf` đều làm cùng một việc.

### Sơ đồ luồng dữ liệu

~~~text
"42" : String
  ├── Integer.parseInt("42") ──────> 42 : int
  └── Integer.valueOf("42") ───────> Integer chua gia tri 42

"3.5" : String
  ├── Double.parseDouble("3.5") ───> 3.5 : double
  └── Double.valueOf("3.5") ───────> Double chua gia tri 3.5

42 : int
  └── String.valueOf(42) ──────────> "42" : String
~~~

## 2. Chương trình Java 17+ chạy độc lập

Tạo tệp **`ParsingReturnTypesDemo.java`** với nội dung sau:

~~~java
public class ParsingReturnTypesDemo {
    static String typeOf(int ignored) {
        return "int";
    }

    static String typeOf(Integer ignored) {
        return "Integer";
    }

    static String typeOf(double ignored) {
        return "double";
    }

    static String typeOf(Double ignored) {
        return "Double";
    }

    public static void main(String[] args) {
        String wholeText = "42";
        String decimalText = "3.5";

        int primitiveInt = Integer.parseInt(wholeText);
        Integer boxedInt = Integer.valueOf(wholeText);
        double primitiveDouble = Double.parseDouble(decimalText);
        Double boxedDouble = Double.valueOf(decimalText);

        System.out.println("parseIntType=" + typeOf(primitiveInt));
        System.out.println("valueOfIntType=" + typeOf(boxedInt));
        System.out.println("parseDoubleType=" + typeOf(primitiveDouble));
        System.out.println("valueOfDoubleType=" + typeOf(boxedDouble));

        System.out.println("intValue=" + primitiveInt);
        System.out.println("doubleValue=" + primitiveDouble);
        System.out.println("wrapperIntPlusOne=" + (boxedInt + 1));
        System.out.println("wrapperDoublePlusHalf="
                + (boxedDouble + 0.5));

        Integer boxedByAssignment = Integer.parseInt("7");
        int unboxedByAssignment = Integer.valueOf("8");
        System.out.println("boxedAssignment=" + boxedByAssignment);
        System.out.println("unboxedAssignment=" + unboxedByAssignment);

        int hexPrimitive = Integer.parseInt("FF", 16);
        Integer hexWrapper = Integer.valueOf("FF", 16);
        System.out.println("hexPrimitive=" + hexPrimitive);
        System.out.println("hexWrapper=" + hexWrapper);

        long bigPrimitive = Long.parseLong("9000000000");
        Long bigWrapper = Long.valueOf("9000000000");
        System.out.println("longPrimitive=" + bigPrimitive);
        System.out.println("longWrapper=" + bigWrapper);

        boolean flagPrimitive = Boolean.parseBoolean("TrUe");
        Boolean flagWrapper = Boolean.valueOf("true");
        System.out.println("booleanPrimitive=" + flagPrimitive);
        System.out.println("booleanWrapper=" + flagWrapper);
        System.out.println("booleanUnknown="
                + Boolean.parseBoolean("yes"));

        String backToText = String.valueOf(primitiveInt);
        System.out.println("stringType=" + backToText.getClass().getSimpleName());
        System.out.println("stringValue=" + backToText);

        System.out.println("scientific="
                + Double.parseDouble("1e3"));
        System.out.println("nanAccepted="
                + Double.isNaN(Double.parseDouble("NaN")));
        System.out.println("infinityAccepted="
                + Double.isInfinite(Double.parseDouble("Infinity")));

        try {
            Integer.parseInt("42.0");
        } catch (NumberFormatException ex) {
            System.out.println("invalidInteger=NumberFormatException");
        }

        try {
            Double.parseDouble("not-a-number");
        } catch (NumberFormatException ex) {
            System.out.println("invalidDouble=NumberFormatException");
        }

        try {
            Integer.parseInt("2147483648");
        } catch (NumberFormatException ex) {
            System.out.println("integerOverflow=NumberFormatException");
        }

        try {
            Integer.parseInt(" 42 ");
        } catch (NumberFormatException ex) {
            System.out.println("integerSpaces=NumberFormatException");
        }

        System.out.println("strippedInteger="
                + Integer.parseInt(" 42 ".strip()));

        Integer missing = null;
        System.out.println("wrapperMissing=" + (missing == null));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp và chạy với **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ParsingReturnTypesDemo.java
java ParsingReturnTypesDemo
~~~

**Expected output chính xác của lệnh `java ParsingReturnTypesDemo`** (không tính đầu ra phiên bản JDK):

~~~text
parseIntType=int
valueOfIntType=Integer
parseDoubleType=double
valueOfDoubleType=Double
intValue=42
doubleValue=3.5
wrapperIntPlusOne=43
wrapperDoublePlusHalf=4.0
boxedAssignment=7
unboxedAssignment=8
hexPrimitive=255
hexWrapper=255
longPrimitive=9000000000
longWrapper=9000000000
booleanPrimitive=true
booleanWrapper=true
booleanUnknown=false
stringType=String
stringValue=42
scientific=1000.0
nanAccepted=true
infinityAccepted=true
invalidInteger=NumberFormatException
invalidDouble=NumberFormatException
integerOverflow=NumberFormatException
integerSpaces=NumberFormatException
strippedInteger=42
wrapperMissing=true
~~~

Các hàm `typeOf(...)` được **overload** với tham số `int`, `Integer`, `double`, `Double` để chứng minh compiler chọn kiểu đầu vào. Đây là minh họa theo **kiểu tĩnh** của biểu thức, không dựa vào `getClass()` của primitive (primitive không có phương thức `getClass()`).

## 3. `parseInt()`: trả về `int`, kiểm tra chuỗi số nguyên

~~~java
int amount = Integer.parseInt("42");
int negative = Integer.parseInt("-15");
int hex = Integer.parseInt("FF", 16);
~~~

`Integer.parseInt(String)` dùng cơ số 10. Overload `Integer.parseInt(String, int radix)` cho phép chọn cơ số từ 2 đến 36:

| Mã | Kết quả | Ghi chú |
| --- | ---: | --- |
| `Integer.parseInt("1010", 2)` | 10 | Nhị phân |
| `Integer.parseInt("17", 8)` | 15 | Bát phân |
| `Integer.parseInt("42")` | 42 | Thập phân |
| `Integer.parseInt("FF", 16)` | 255 | Hexadecimal |
| `Integer.parseInt("-10")` | −10 | Số âm |
| `Integer.parseInt("+10")` | 10 | Dấu cộng hợp lệ |

Nếu chuỗi không phải biểu diễn số nguyên hợp lệ theo cơ số được chọn, giá trị vượt miền `int` hoặc tham số không hợp lệ, phương thức sẽ ném `NumberFormatException`. Ví dụ `"42.0"` không phải cú pháp số nguyên, và `"2147483648"` vượt `Integer.MAX_VALUE`.

`"0xFF"` **không** được `Integer.parseInt(..., 16)` tự bỏ tiền tố `0x`. Để phân tích `"FF"` với radix 16, truyền đúng phần chữ số. API `Integer.decode("0xFF")` là một cơ chế khác, có quy tắc nhận tiền tố riêng.

## 4. `parseDouble()`: trả về `double`

~~~java
double a = Double.parseDouble("3.5");
double b = Double.parseDouble("1e3");  // 1000.0.
double special = Double.parseDouble("NaN");
~~~

`Double.parseDouble` nhận cú pháp biểu diễn `double` của Java cho chuỗi thích hợp; ngoài số thập phân, có thể nhận ký hiệu khoa học như `1e3` và các giá trị đặc biệt `NaN`, `Infinity`. Do vậy, **phân tích cú pháp thành công không có nghĩa dữ liệu là số hữu hạn**.

Nếu nghiệp vụ yêu cầu số hữu hạn:

~~~java
double rate = Double.parseDouble("1e3");
if (!Double.isFinite(rate)) {
    throw new IllegalArgumentException("Must be finite");
}
~~~

Nếu chuỗi không hợp lệ, ví dụ `"not-a-number"`, phương thức ném `NumberFormatException`. Lưu ý `double` là số thực nhị phân IEEE 754, nên giá trị thập phân như `0.1` thường không được biểu diễn hoàn toàn chính xác. Với tiền tệ cần độ chính xác thập phân, cân nhắc `BigDecimal` và quy tắc làm tròn rõ ràng thay vì chỉ dựa vào `double`.

## 5. `valueOf()`: kết quả phụ thuộc lớp và overload

### `Integer.valueOf`

~~~java
Integer fromText = Integer.valueOf("42");
Integer fromPrimitive = Integer.valueOf(42);
Integer fromHex = Integer.valueOf("FF", 16);
~~~

Cả ba biểu thức trên đều có kiểu **`Integer`**. Một số giá trị nhỏ có thể dùng đối tượng wrapper được cache. Vì thế **không dùng `==` giữa hai wrapper để kiểm tra giá trị số**; dùng `equals()` hoặc `Objects.equals()` khi có null.

### `Double.valueOf`

~~~java
Double fromText = Double.valueOf("3.5");
Double fromPrimitive = Double.valueOf(3.5);
~~~

Cả hai trả về kiểu **`Double`**. Khác với `Double.parseDouble`, kiểu trả về được khai báo là wrapper, không phải primitive.

### `String.valueOf` có chức năng hoàn toàn khác

~~~java
String text = String.valueOf(42);     // "42".
String flagText = String.valueOf(true); // "true".
~~~

`String.valueOf(...)` trả về **`String`**, chuyển một giá trị thành biểu diễn văn bản. Nó không phân tích chuỗi sang số. Nói “`valueOf` luôn trả wrapper” là sai vì còn phải xác định lớp chứa phương thức.

### `Boolean` cũng có `parseBoolean` và `valueOf`

~~~java
boolean primitive = Boolean.parseBoolean("true");
Boolean wrapper = Boolean.valueOf("true");
~~~

`Boolean.parseBoolean` chỉ trả `true` khi chuỗi bằng `"true"` không phân biệt hoa/thường; các chuỗi khác như `"yes"` trả `false` thay vì `NumberFormatException`. Nếu cần từ chối đầu vào ngoài `true/false`, phải **validation riêng**.

## 6. Boxing và unboxing không đổi kiểu trả về của phương thức

~~~java
Integer boxed = Integer.parseInt("42"); // parseInt TRA int, sau do boxing.
int primitive = Integer.valueOf("42"); // valueOf TRA Integer, sau do unboxing.
~~~

Hai câu trên biên dịch được nhưng **không** chứng minh `parseInt` trả wrapper hay `valueOf` trả primitive. Kiểu trả về thực tế được khai báo bởi phương thức; Java có thể tự chuyển ở **ngữ cảnh gán**.

| Biểu thức bên phải | Kiểu phương thức trả về | Kiểu biến bên trái | Chuyển đổi thêm |
| --- | --- | --- | --- |
| `Integer.parseInt("42")` | `int` | `int` | Không |
| `Integer.parseInt("42")` | `int` | `Integer` | Boxing |
| `Integer.valueOf("42")` | `Integer` | `Integer` | Không |
| `Integer.valueOf("42")` | `Integer` | `int` | Unboxing |
| `Double.parseDouble("3.5")` | `double` | `Double` | Boxing |
| `Double.valueOf("3.5")` | `Double` | `double` | Unboxing |

**Bẫy `null`:** nếu một biến `Integer` hoặc `Double` đang là null mà được unbox, Java ném `NullPointerException`. Việc gọi phương thức parsing trên chuỗi đầu vào sai lại là một dạng vấn đề khác, thường là `NumberFormatException`.

## 7. Làm sạch và kiểm tra đầu vào

`Integer.parseInt(" 42 ")` ném `NumberFormatException` vì dấu cách đầu/cuối không thuộc cú pháp số nguyên. Nếu ứng dụng cho phép khoảng trắng viền, chủ động chuẩn hóa:

~~~java
String raw = " 42 ";
int parsed = Integer.parseInt(raw.strip()); // 42.
~~~

**Không bao giờ mặc định mọi khoảng trắng đều vô hại** trong dữ liệu chính thức: ứng dụng cần quyết định có được bỏ chúng hay phải báo sai định dạng.

Một hàm đơn giản xử lý đầu vào bắt buộc là số nguyên:

~~~java
static int parseRequiredInt(String raw) {
    if (raw == null || raw.isBlank()) {
        throw new IllegalArgumentException("Value is required");
    }
    return Integer.parseInt(raw.strip());
}
~~~

- `null` và chuỗi trắng bị báo là thiếu.
- Chuỗi hợp lệ sau chuẩn hóa được phân tích thành `int`.
- Chuỗi sai định dạng hoặc vượt miền `int` ném `NumberFormatException`. Có thể bắt ngoại lệ này ở tầng tương tác người dùng để trả thông báo rõ ràng, thay vì nuốt lỗi và mặc định giá trị 0 khi không được phép.

### Phân biệt parsing với ép kiểu

~~~java
String text = "42";
// int bad = (int) text;  // LOI BIEN DICH.
int value = Integer.parseInt(text);
~~~

Cast số không đọc các chữ số trong `String`. Parsing chuỗi và ép kiểu số là hai thao tác khác nhau.

## 8. Chọn API nào khi viết Java?

| Nhu cầu | Cách chọn | Lý do |
| --- | --- | --- |
| Cần số nguyên primitive để tính toán | `Integer.parseInt(raw)` | Trả trực tiếp `int` |
| Cần `Integer` cho API/generic | `Integer.valueOf(raw)` | Trả wrapper |
| Cần số thực primitive | `Double.parseDouble(raw)` | Trả `double` |
| Cần wrapper số thực | `Double.valueOf(raw)` | Trả `Double` |
| Cần đọc số nguyên dài | `Long.parseLong(raw)` | Trả `long` |
| Cần wrapper số nguyên dài | `Long.valueOf(raw)` | Trả `Long` |
| Cần chuyển primitive thành văn bản | `String.valueOf(value)` | Trả `String` |
| Cần kiểm soát base 16 | `Integer.parseInt(raw, 16)` | Kết quả `int`, chuỗi chữ số hex |
| Cần giá trị tiền tệ chính xác | `new BigDecimal(raw)` và thiết kế scale/rounding | Tránh sai số nhị phân của `double` |

Lưu ý `valueOf` trên một wrapper **không nhất thiết tạo đối tượng mới mỗi lần**. API có thể cache các đối tượng bất biến. Vì thế đừng đánh đồng “kiểu trả về là wrapper” với “mỗi lần gọi luôn cấp phát mới”.

## 9. Những lỗi thường gặp

| Hiểu sai | Điều đúng |
| --- | --- |
| “`Integer.parseInt` trả `Integer`.” | Trả **`int`**; có thể được boxing khi gán vào `Integer` |
| “`Integer.valueOf` trả `int`.” | Trả **`Integer`**; có thể unbox khi gán vào `int` |
| “Mọi `valueOf` đều trả wrapper.” | `String.valueOf` trả **`String`** |
| “`Integer.parseInt("42.0")` ra 42.” | Ném `NumberFormatException` |
| “`Double.parseDouble` chỉ đọc số hữu hạn.” | Có thể nhận `NaN` hoặc `Infinity` |
| “`parseInt(" 42 ")` luôn tự trim.” | Không; nên chuẩn hóa theo hợp đồng |
| “`Integer.parseInt` tự đọc `0xFF` theo base 16.” | Cần xử lý cú pháp/tiền tố hoặc dùng API phù hợp |
| “Gán vào `Integer` khiến `parseInt` đổi kiểu trả về.” | Không; đó là **boxing sau phương thức** |
| “Hai kết quả `valueOf` bằng số thì `==` chắc đúng.” | Không; so wrapper theo **giá trị** bằng `equals` |

## 10. Bài tập tự kiểm tra

1. **Kiểu kết quả:** `Integer.parseInt("7")` và `Integer.valueOf("7")` lần lượt trả kiểu nào?  
   *Đáp án:* `int` và `Integer`.
2. **Số thực:** `Double.parseDouble("2.5")` khác `Double.valueOf("2.5")` ở kiểu trả về thế nào?  
   *Đáp án:* `double` và `Double`.
3. **Overload:** `Integer.parseInt("FF", 16)` và `Integer.valueOf("FF", 16)` cho giá trị và kiểu nào?  
   *Đáp án:* Cùng giá trị 255, lần lượt kiểu `int` và `Integer`.
4. **Ngữ cảnh gán:** `Integer n = Integer.parseInt("8");` có biên dịch không?  
   *Đáp án:* Có, nhờ boxing `int -> Integer`.
5. **Dữ liệu sai:** `Integer.parseInt("8.0")` có hợp lệ không?  
   *Đáp án:* Không, ném `NumberFormatException`.
6. **Boolean:** `Boolean.parseBoolean("YES")` ném ngoại lệ hay cho false?  
   *Đáp án:* Cho `false`. Muốn từ chối “YES” phải tự validation.
7. **NaN:** `Double.parseDouble("NaN")` thất bại hay thành công?  
   *Đáp án:* Thành công, trả `double` NaN; `Double.isNaN(...)` trả true.

## Checklist ghi nhớ

- [ ] `parseInt` / `parseLong` / `parseDouble` trả về **primitive** tương ứng.
- [ ] Wrapper `valueOf` trả về **đối tượng wrapper** tương ứng.
- [ ] `String.valueOf` trả về **String**, không phải wrapper số.
- [ ] Boxing/unboxing xảy ra **sau** lời gọi phương thức nếu ngữ cảnh cần.
- [ ] Phân biệt cú pháp, miền giá trị, radix và exception khi parsing.
- [ ] Phân tích `double` thành công không bảo đảm giá trị hữu hạn hoặc chính xác tuyệt đối.
- [ ] Chuẩn hóa whitespace và validate null theo yêu cầu ứng dụng.
- [ ] Không dùng cast để thay parsing và không dùng `==` để so giá trị wrapper.

**Kết luận:** Khi đọc `"42"`, hãy chọn API theo **kiểu kết quả cần sử dụng**: `Integer.parseInt` cho `int`, `Integer.valueOf` cho `Integer`. Tương tự, `Double.parseDouble` cho `double` và `Double.valueOf` cho `Double`. Cùng tên `valueOf` ở lớp khác có thể trả loại hoàn toàn khác, vì thế luôn xem **lớp khai báo + chữ ký phương thức + ngữ cảnh boxing/unboxing**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-007">NumberFormatException khi chuỗi không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# NumberFormatException khi chuỗi không hợp lệ trong Java

> **Java core — language-basics / Lớp bao nguyên thủy**  
> **Môi trường:** JDK 17+; không cần thư viện ngoài.  
> **Mục tiêu:** Xác định tại sao chuyển chuỗi sang số có thể ném `NumberFormatException`, phân biệt sai định dạng với vượt miền, áp dụng đúng `Integer.parseInt`/`Long.parseLong`/`Double.parseDouble` và xử lý giá trị nhập không hợp lệ tại ranh giới chương trình.

## 1. Một chuỗi không tự trở thành số

`String` chứa văn bản, còn các kiểu `int`, `long` và `double` lưu giá trị số. Muốn phân tích văn bản thành số, Java cung cấp các phương thức chuyển đổi của lớp bao (*wrapper*):

~~~java
int quantity = Integer.parseInt("42");  // 42
long count = Long.parseLong("3000000000"); // 3000000000L
double rate = Double.parseDouble("3.14"); // 3.14
~~~

Nếu không phân tích được chuỗi theo cú pháp hoặc miền giá trị mà phương thức yêu cầu, Java ném **`java.lang.NumberFormatException`** (*NFE*). Đây là một lớp con của `IllegalArgumentException`.

~~~java
// int value = Integer.parseInt("12x"); // Nếu chạy: NumberFormatException.
~~~

**Điểm chính:** `"12x"` vẫn là một `String` hợp lệ. Lỗi xảy ra khi **cố diễn giải nội dung đó thành số nguyên**, không phải khi tạo chuỗi.

## 2. Các nhóm đầu vào gây `NumberFormatException`

| Dữ liệu và phép chuyển | Kết quả | Nguyên nhân |
| --- | --- | --- |
| `Integer.parseInt("42")` | `42` | Chuỗi thập phân hợp lệ |
| `Integer.parseInt("-7")` | `-7` | Dấu trừ hợp lệ |
| `Integer.parseInt("+8")` | `8` | Dấu cộng đầu hợp lệ |
| `Integer.parseInt("007")` | `7` | Dẫn đầu bằng 0 vẫn là thập phân mặc định |
| `Integer.parseInt(" 42 ")` | NFE | Không tự bỏ khoảng trắng |
| `Integer.parseInt("")` | NFE | Chuỗi rỗng |
| `Integer.parseInt("12x")` | NFE | Có ký tự không hợp lệ ở hệ thập phân |
| `Integer.parseInt("3.14")` | NFE | Số thập phân có phần lẻ không phải cú pháp `int` |
| `Integer.parseInt("1_000")` | NFE | Dấu gạch dưới hợp lệ trong **literal Java**, không phải chuỗi nhập cho `parseInt` |
| `Integer.parseInt("2147483648")` | NFE | Vượt `Integer.MAX_VALUE` là 2147483647 |
| `Integer.parseInt(null)` | NFE | Không có chuỗi để phân tích |
| `Byte.parseByte("128")` | NFE | Giá trị 128 vượt giới hạn `byte` là 127 |

`Integer.parseInt` mặc định là **hệ cơ số 10**. Nó không hiểu cú pháp literal `0xFF` hoặc cách viết dấu gạch dưới trong mã nguồn; đừng nhầm định dạng của một **Java source literal** với định dạng của **chuỗi nhập ở runtime**.

Phương thức parse không tự xử lý toàn bộ ràng buộc nghiệp vụ: `"-1"` có thể phân tích thành `int` thành công, nhưng vẫn có thể **không hợp lệ** nếu đây là số lượng sản phẩm hoặc cổng mạng phải dương.

## 3. Radix: chuỗi hợp lệ phụ thuộc cơ số

Tham số `radix` quy định cơ số cần dùng cho `Integer.parseInt(String, int)`:

~~~java
int hex = Integer.parseInt("FF", 16);   // 255
int binary = Integer.parseInt("101", 2); // 5
int decimal = Integer.parseInt("101", 10); // 101

// Integer.parseInt("FF");     // NFE: F không phải chữ số thập phân.
// Integer.parseInt("102", 2);  // NFE: 2 không thuộc cơ số 2.
~~~

`Integer.parseInt("FF", 16)` yêu cầu chỉ định radix 16; không phải cast kiểu số. Radix hợp lệ cho `Integer.parseInt` nằm từ `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36); radix không hợp lệ cũng dẫn đến `NumberFormatException`.

Nếu đầu vào có tiền tố `0x`, API `Integer.decode(...)` có quy tắc riêng để hiểu tiền tố. Không tự động thay thế `parseInt` bằng `decode` nếu định dạng đầu vào phải được kiểm soát chặt.

## 4. Phân biệt cú pháp, miền giá trị và loại số

Các hàm parse có định dạng/miền khác nhau:

| API | Ví dụ hợp lệ | Điểm cần lưu ý |
| --- | --- | --- |
| `Byte.parseByte` | `"127"` | Vượt miền `byte` gây NFE |
| `Short.parseShort` | `"-30000"` | Vượt miền `short` gây NFE |
| `Integer.parseInt` | `"2147483647"` | Số thập phân có phần lẻ không hợp lệ |
| `Long.parseLong` | `"2147483648"` | Lớn hơn `int` vẫn vừa `long` |
| `Double.parseDouble` | `"3.14"` hoặc `"1e3"` | Hỗ trợ biểu diễn số thực, số mũ và token đặc biệt |
| `Float.parseFloat` | `"2.5"` | Theo quy tắc parsing số thực, không giống `parseInt` |

**Quan trọng:** `Double.parseDouble("NaN")` trả giá trị **`Double.NaN`**, không ném NFE. Một chuỗi hợp lệ theo quy tắc `double` cũng **không nhất thiết là giá trị hữu hạn**: ví dụ đầu vào quá lớn có thể trở thành vô cực. Khi nghiệp vụ chỉ cho phép số thực hữu hạn, kiểm tra thêm `Double.isFinite(value)` sau khi parse.

Với lượng tiền cần chính xác tới chữ số thập phân, cân nhắc `BigDecimal` tạo từ chuỗi hợp lệ và quy tắc scale/rounding nghiệp vụ; không tùy tiện đưa tiền qua `double` rồi cho rằng tuyệt đối chính xác.

## 5. Chương trình Java 17+ runnable

Tạo file **NumberFormatDemo.java**:

~~~java
public final class NumberFormatDemo {
    private static void checkInt(String label, String text) {
        try {
            System.out.println(label + "=" + Integer.parseInt(text));
        } catch (NumberFormatException ex) {
            System.out.println(label + "=NumberFormatException");
        }
    }

    private static int parsePort(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("port required");
        }

        int port;
        try {
            port = Integer.parseInt(input.strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "port must be decimal integer", ex);
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port outside 1..65535");
        }
        return port;
    }

    private static void checkPort(String label, String input) {
        try {
            System.out.println(label + "=" + parsePort(input));
        } catch (IllegalArgumentException ex) {
            System.out.println(label + "=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        checkInt("plain", "42");
        checkInt("negative", "-7");
        checkInt("leadingPlus", "+8");
        checkInt("leadingZero", "007");
        checkInt("spaces", " 42 ");
        checkInt("decimal", "3.14");
        checkInt("empty", "");
        checkInt("nonDigit", "12x");
        checkInt("overflow", "2147483648");
        checkInt("underscore", "1_000");
        checkInt("nullInput", null);

        System.out.println("hexFF=" + Integer.parseInt("FF", 16));
        try {
            Integer.parseInt("102", 2);
        } catch (NumberFormatException ex) {
            System.out.println("binaryInvalid=NumberFormatException");
        }

        System.out.println("longLarge=" + Long.parseLong("2147483648"));
        try {
            Byte.parseByte("128");
        } catch (NumberFormatException ex) {
            System.out.println("byteOverflow=NumberFormatException");
        }

        System.out.println("doubleDecimal=" + Double.parseDouble("3.14"));
        System.out.println("doubleNaN=" + Double.parseDouble("NaN"));
        try {
            Double.parseDouble("1.2.3");
        } catch (NumberFormatException ex) {
            System.out.println("doubleInvalid=NumberFormatException");
        }

        checkPort("validPort", "8080");
        checkPort("spacesPort", " 8080 ");
        checkPort("badPort", "abc");
        checkPort("tooLargePort", "65536");
        checkPort("missingPort", null);
    }
}
~~~

Mở terminal trong thư mục chứa file, biên dịch và chạy (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 NumberFormatDemo.java
java NumberFormatDemo
~~~

**Expected output chính xác:**

~~~text
plain=42
negative=-7
leadingPlus=8
leadingZero=7
spaces=NumberFormatException
decimal=NumberFormatException
empty=NumberFormatException
nonDigit=NumberFormatException
overflow=NumberFormatException
underscore=NumberFormatException
nullInput=NumberFormatException
hexFF=255
binaryInvalid=NumberFormatException
longLarge=2147483648
byteOverflow=NumberFormatException
doubleDecimal=3.14
doubleNaN=NaN
doubleInvalid=NumberFormatException
validPort=8080
spacesPort=8080
badPort=port must be decimal integer
tooLargePort=port outside 1..65535
missingPort=port required
~~~

Chương trình **không phụ thuộc vào văn bản lỗi mặc định của JDK**: ở những ví dụ sai format, nó chỉ in tên loại exception; ở `parsePort`, thông báo do code tự định nghĩa nên dễ kiểm thử.

## 6. Xử lý lỗi đúng tại ranh giới nhập liệu

Ở hàm `parsePort`, các bước có chủ đích:

1. **Kiểm tra dữ liệu thiếu:** `null` hoặc chuỗi trắng toàn bộ → `"port required"`. Tránh vô tình coi dữ liệu thiếu như số 0.
2. **Chuẩn hóa đầu vào khi được phép:** `strip()` loại bỏ khoảng trắng đầu/cuối, nhưng chỉ vì hợp đồng của ví dụ **cho phép** khoảng trắng. Nếu định dạng nghiêm ngặt không chấp nhận khoảng trắng, hãy bỏ bước này.
3. **Parse và bắt lỗi đúng nơi:** `Integer.parseInt` xử lý cú pháp số nguyên. `NumberFormatException` được chuyển thành một lỗi đầu vào có thông điệp nghiệp vụ, giữ nguyên `cause` để chẩn đoán.
4. **Kiểm tra miền nghiệp vụ sau khi parse:** số nguyên `65536` có cú pháp đúng và vừa `int`, nhưng không hợp lệ làm cổng mạng; `"-1"` tương tự.
5. **Không biến mọi lỗi thành số 0:** một chuỗi sai format thường cần được báo cho người dùng hoặc upstream, không phải âm thầm đưa vào phép tính.

`try/catch` ở đây bao quanh phần **phân tích dữ liệu không đáng tin cậy**, thay vì bắt một khối ứng dụng quá lớn hoặc bắt `Exception` chung rồi bỏ qua. API nội bộ với hợp đồng khác có thể để NFE đi ra cho caller xử lý, thay vì luôn chuyển thành `IllegalArgumentException` mới.

## 7. Vì sao không nên dựa vào `ex.getMessage()` để điều khiển logic?

`NumberFormatException` cho biết có sự cố phân tích số, nhưng **chi tiết text của thông điệp** không phải định dạng nghiệp vụ ổn định để parser hoặc UI dựa vào. Tránh viết:

~~~java
// Không nên:
// if (ex.getMessage().contains("For input string")) { ... }
~~~

Thay vào đó, bắt đúng **kiểu exception**, kiểm tra trường dữ liệu nguồn, rồi tự tạo một thông điệp rõ ràng như `"quantity must be a whole number"`. Có thể ghi log exception/cause theo chính sách hệ thống, nhưng không hiển thị chi tiết stack trace cho người dùng cuối.

**Không nhầm với lỗi khác:**

- `Integer.parseInt("12x")` → **`NumberFormatException`** do text không phân tích được.
- `int x = someIntegerWrapper;` khi wrapper là `null` → **`NullPointerException`** do unboxing, không phải lỗi cú pháp chuỗi.
- `Integer.valueOf(12)` tạo/nhận wrapper từ giá trị nguyên, không phải parser cho một chuỗi văn bản sai cú pháp.
- `Integer.valueOf("12x")` cũng có thể ném **`NumberFormatException`** vì phải phân tích chuỗi như số nguyên.

## 8. Bài tập tự luyện

**Bài 1:** Dự đoán kết quả của `Integer.parseInt("+15")`, `Integer.parseInt("015")`, `Integer.parseInt("15 ")`, `Integer.parseInt("15.0")` và giải thích vì sao chuỗi nguồn là `String` hợp lệ nhưng parser vẫn có thể từ chối.

**Bài 2:** So sánh `Integer.parseInt("2147483648")` với `Long.parseLong("2147483648")`. Điều gì thay đổi khi chọn wrapper khác?

**Bài 3:** Dùng `Integer.parseInt("101", 2)` và `Integer.parseInt("101", 10)`. Cùng một chuỗi vì sao có thể thành hai số khác nhau?

**Bài 4:** Viết hàm nhận số lượng sản phẩm dạng `String`. Từ chối null/trắng, parse số nguyên, sau đó từ chối lượng âm. Nêu lý do đây là hai lớp kiểm tra khác nhau.

**Bài 5:** Thử `Double.parseDouble("NaN")` và `Double.parseDouble("1e309")`. Viết điều kiện `Double.isFinite(result)` nếu ứng dụng yêu cầu số đo hữu hạn.

### Ghi nhớ

**`NumberFormatException` là dấu hiệu chuỗi không thể được diễn giải theo kiểu số, cơ số hoặc giới hạn đang yêu cầu.** Kiểm tra input thiếu và khoảng trắng theo hợp đồng; dùng đúng `parseInt`/`parseLong`/`parseDouble`; bắt NFE tại ranh giới dữ liệu không đáng tin cậy, rồi **kiểm tra tiếp điều kiện nghiệp vụ**. Đừng dựa vào text lỗi JDK và đừng âm thầm thay mọi đầu vào sai bằng 0.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primitive-wrappers-008">Chọn kiểu nguyên thủy hay lớp bao theo yêu cầu dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn kiểu nguyên thủy hay lớp bao theo yêu cầu dữ liệu — Java 17+

## 1. Nguyên tắc quyết định

Java cung cấp hai dạng liên quan nhưng **không tương đương**:

- **Kiểu nguyên thủy** (*primitive*), ví dụ `int`, `boolean`, `double`: giữ trực tiếp một giá trị thuộc miền của kiểu; **không thể nhận `null`**.
- **Lớp bao nguyên thủy** (*wrapper class*), ví dụ `Integer`, `Boolean`, `Double`: biến giữ **tham chiếu tới đối tượng wrapper** (hoặc `null`), có thể dùng trong generic, collection và những API yêu cầu object.

~~~java
int stock = 0;               // Có dữ liệu: số lượng thật sự bằng 0
Integer reportedStock = null; // Chưa có dữ liệu, chưa phải số 0

boolean enabled = false;     // Có dữ liệu: trạng thái không bật
Boolean approved = null;     // Chưa được duyệt, chưa phải false
~~~

**Câu hỏi quyết định đầu tiên không phải “kiểu nào tốt hơn?” mà là “dữ liệu có được phép vắng mặt hay không?”**

Nếu giá trị **luôn phải tồn tại** và chủ yếu dùng trong tính toán/điều kiện, primitive thường phù hợp. Nếu cần **biểu diễn trạng thái vắng mặt** hoặc API yêu cầu kiểu tham chiếu, wrapper có thể cần thiết. Dù chọn wrapper, vẫn phải quy định rõ cách xử lý `null`.

## 2. Bảng tám cặp primitive và wrapper

| Primitive | Wrapper | Đặc điểm dữ liệu điển hình |
|---|---|---|
| `byte` | `Byte` | Số nguyên 8 bit có dấu |
| `short` | `Short` | Số nguyên 16 bit có dấu |
| `int` | `Integer` | Số đếm/số nguyên 32 bit |
| `long` | `Long` | Số nguyên 64 bit |
| `float` | `Float` | Số dấu phẩy động 32 bit |
| `double` | `Double` | Số dấu phẩy động 64 bit |
| `char` | `Character` | Một UTF-16 code unit |
| `boolean` | `Boolean` | Giá trị true hoặc false |

`String` không phải kiểu nguyên thủy và cũng không phải lớp bao của `char`; nó là class biểu diễn một chuỗi UTF-16 code units. `Void` có vai trò đặc biệt trong một số API phản chiếu/generic nhưng **không có giá trị primitive `void` để boxing như các cặp trên**.

Ví dụ chuyển đổi tự động (*autoboxing* và *unboxing*):

~~~java
int primitive = 12;
Integer boxed = primitive; // int -> Integer
int restored = boxed;      // Integer -> int

System.out.println(restored); // 12
~~~

Việc Java hỗ trợ chuyển đổi tự động **không xóa bỏ** khác biệt giữa primitive và wrapper, đặc biệt là trạng thái `null` và cách so sánh.

## 3. Bảng quyết định theo yêu cầu dữ liệu

| Yêu cầu | Thường ưu tiên | Lý do |
|---|---|---|
| Số lượng đơn hàng **bắt buộc có giá trị** | `int` | 0 là một số lượng hợp lệ; không có trạng thái null |
| ID nguyên bắt buộc, vượt giới hạn int | `long` | Miền nguyên 64 bit, không cần wrapper khi không có nullable |
| Số lượng **chưa được nhập hoặc chưa báo cáo** | `Integer` | Phân biệt null với số 0 |
| Cờ bật/tắt chỉ có hai trạng thái | `boolean` | Đúng hai trạng thái `true`, `false` |
| Trạng thái duyệt gồm có/chưa/không | `Boolean` hoặc `enum` | `null` có thể biểu thị chưa xác định; enum rõ hơn khi trạng thái có ý nghĩa nghiệp vụ |
| API nhận hoặc trả `List<Integer>` | `Integer` | Generic Java 17 cần reference type |
| Mảng phép đo lớn, tính toán lặp nhiều | `int[]`, `double[]` khi phù hợp | Tránh nhu cầu wrapper từng phần tử |
| Trả kết quả tìm kiếm số nguyên có thể không có | `OptionalInt` hoặc hợp đồng nullable rõ ràng | Tách “không tìm thấy” khỏi giá trị 0 |
| Trường dữ liệu nguồn bắt buộc nhưng chưa xác minh | Kiểm tra trước rồi primitive | Không biến “không biết” thành 0/false |
| Số tiền cần đúng chính xác thập phân | `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất | `Double` wrapper không sửa sai số binary floating-point |

**Điểm chốt:** tính **có mặt/vắng mặt**, **miền giá trị**, **độ chính xác** và **API sử dụng** quan trọng hơn sở thích viết kiểu. Một kiểu wrapper không phải luôn chậm/không an toàn, và primitive cũng không tự khiến dữ liệu hợp lệ về nghiệp vụ.

## 4. Phân biệt “không có dữ liệu” với số 0

Giả sử ứng dụng thống kê số đơn hàng đã được xác nhận:

- `confirmedOrders = 0`: hệ thống xác nhận **không có đơn hàng nào**.
- `confirmedOrders = null`: **chưa nhận được báo cáo**, chưa thể khẳng định bằng 0.

~~~java
Integer confirmedOrders = null;

if (confirmedOrders == null) {
    System.out.println("no report");
} else {
    System.out.println("confirmed=" + confirmedOrders);
}
~~~

Đầu ra:

~~~text
no report
~~~

Nếu ép dữ liệu thiếu thành 0 tùy tiện:

~~~java
int count = confirmedOrders == null ? 0 : confirmedOrders;
~~~

thì bạn **đã quyết định gộp** hai trạng thái khác nhau. Điều đó chỉ đúng nếu yêu cầu nghiệp vụ thật sự cho phép coi “chưa có báo cáo” là 0, ví dụ một phép hiển thị có nhãn “mặc định 0” và có giải thích.

**Quy tắc thiết kế API:** nếu giá trị bắt buộc, validate lúc nhận input hoặc lúc tạo object thay vì đưa một biến `Integer` tùy ý xuống sâu các tầng tính toán rồi hy vọng chúng không `null`.

## 5. Boolean và bài toán ba trạng thái

Một `boolean` có đúng hai giá trị `true`/`false`. Với `Boolean`, tham chiếu còn có thể `null`.

~~~java
Boolean approved = null;

if (approved == null) {
    System.out.println("pending");
} else if (approved) {
    System.out.println("approved");
} else {
    System.out.println("rejected");
}
~~~

Đầu ra:

~~~text
pending
~~~

Trường hợp này hợp lý khi dữ liệu có **ba trạng thái**: chưa đánh giá, đồng ý, từ chối. Tuy nhiên, nếu hệ thống có nhiều trạng thái hơn (đang duyệt, tạm hoãn, bị khóa...), dùng `enum` thường rõ nghĩa hơn:

~~~java
enum Decision {
    PENDING, APPROVED, REJECTED
}

Decision decision = Decision.PENDING;
~~~

**Đừng lạm dụng `null` làm một “trạng thái bí mật”** khi API không mô tả ý nghĩa của nó. Có thể dùng wrapper nullable, nhưng phải thống nhất hợp đồng dữ liệu và tài liệu hóa cách xử lý.

## 6. Giá trị mặc định của field khác với biến cục bộ

Nếu tạo object Java mà **không gán khởi tạo cho field**, primitive field nhận default value; reference field mặc định `null`:

~~~java
static final class Defaults {
    int count;            // 0
    Integer optionalCount; // null
    boolean enabled;      // false
    Boolean optionalEnabled; // null
}
~~~

Điều này hữu ích để hiểu hành vi, nhưng **không chứng minh các default value đó là dữ liệu hợp lệ**.

| Field | Mặc định khi tạo object | Nguy cơ hiểu sai |
|---|---|---|
| `int count` | `0` | 0 có thể bị nhầm với “chưa nạp dữ liệu” |
| `Integer count` | `null` | Unboxing khi chưa kiểm tra sẽ lỗi |
| `boolean enabled` | `false` | false có thể bị nhầm với “chưa đánh giá” |
| `Boolean enabled` | `null` | Phải quy định cách xử lý chưa xác định |

**Biến cục bộ không tự nhận default value:**

~~~java
static void example() {
    int count;
    // System.out.println(count); // Lỗi compile: biến cục bộ chưa gán
    count = 0;
    System.out.println(count); // 0
}
~~~

Hãy khởi tạo field có ý nghĩa rõ ràng hoặc dùng constructor để buộc cung cấp các giá trị bắt buộc.

## 7. Đưa yêu cầu dữ liệu vào model: record Java 17

Một `record` giúp thể hiện ranh giới **bắt buộc** và **có thể chưa có** trong chữ ký:

~~~java
record Stock(String sku, int onHand, Integer reserved, Boolean approved) {
    Stock {
        java.util.Objects.requireNonNull(sku, "sku is required");
        if (onHand < 0) {
            throw new IllegalArgumentException("negative onHand");
        }
        if (reserved != null && reserved < 0) {
            throw new IllegalArgumentException("negative reserved");
        }
    }
}
~~~

Ý nghĩa:

- `onHand` là `int`: bắt buộc có giá trị tại thời điểm tạo record, nhưng vẫn cần kiểm tra `onHand >= 0`.
- `reserved` là `Integer`: có thể chưa nhận dữ liệu (null), có thể nhận giá trị **0** hoặc số dương.
- `approved` là `Boolean`: có thể chưa đánh giá, `true` hoặc `false`.
- `sku` là `String` tham chiếu: không tự cấm null, nên constructor gọi `Objects.requireNonNull`.

~~~java
Stock unknown = new Stock("SKU-1", 10, null, null);
Stock knownZero = new Stock("SKU-1", 10, 0, false);

System.out.println(unknown.reserved() == null); // true
System.out.println(knownZero.reserved());         // 0
~~~

**Điểm cốt lõi:** Java không có kiểu primitive nullable. Nếu phải đại diện vắng mặt, cần một cơ chế trong model (wrapper, `OptionalInt` cho API trả về, trạng thái riêng, object kết quả...) và phải thực thi hợp đồng nghiệp vụ bằng validation.

## 8. Chặn null ở ranh giới dữ liệu

Một `Integer` hoặc `Boolean` có thể chứa `null`. Khi Java cần giá trị primitive để cộng, so sánh với số hoặc chạy `if (flag)`, unboxing `null` sẽ gây `NullPointerException`. Điểm cần quyết định trong thiết kế dữ liệu là **được phép thiếu** hay **phải từ chối đầu vào thiếu**.

~~~java
static int requireCount(Integer count) {
    if (count == null) {
        throw new IllegalArgumentException("count is required");
    }
    return count; // Chỉ unbox sau khi kiểm tra
}
~~~

Nếu null là trạng thái hợp lệ, giữ wrapper đến lúc có quy tắc xử lý rõ ràng; đừng mặc định đổi null thành 0. Bài unboxing null phân tích chi tiết các bẫy ba ngôi, short-circuit và điều kiện.

## 9. So sánh theo giá trị hay tham chiếu?

Khi hai biến có kiểu `Integer`, `a == b` hỏi chúng có cùng object không; cache có thể khiến hai wrapper của một số nhỏ dùng chung object. Khi muốn hỏi hai **giá trị** có bằng nhau, chọn `a.equals(b)` nếu `a` không null, hoặc `Objects.equals(a, b)` khi cả hai có thể null.

~~~java
Integer first = Integer.valueOf(1000);
Integer second = Integer.valueOf(1000);
System.out.println(java.util.Objects.equals(first, second)); // true
~~~

Với `Integer == int`, Java unbox để so số; nếu wrapper null sẽ gây NPE. `Integer.equals(Long)` không tự đổi kiểu để so. Các bài equality và cache là nơi giải thích chính cho những quy tắc này.

## 10. Generic collection và chính sách dữ liệu thiếu

`List<Integer>` chứa wrapper vì generic Java 17 dùng kiểu tham chiếu. Khi chuyển sang `int` để tính toán, mỗi phần tử phải có giá trị:

~~~java
java.util.List<Integer> readings =
        java.util.Arrays.asList(2, null, 3);
int sum = 0;
for (Integer n : readings) {
    if (n != null) {
        sum += n;
    }
}
System.out.println(sum); // 5
~~~

Bỏ qua null ở đây là **chính sách minh họa**, không phải mặc định đúng cho mọi hệ thống. `List.of(...)` không chấp nhận phần tử null. Nếu dữ liệu số luôn bắt buộc, cần tính toán nhiều và không phụ thuộc generic, mảng primitive hoặc primitive stream có thể hợp lý hơn; phần hiệu năng ở dưới giải thích cách đánh giá.

## 11. `OptionalInt` cho API trả về kết quả có thể vắng mặt

Đôi khi caller cần biết **một kết quả số nguyên có tồn tại không**, nhưng bạn không muốn trả `Integer` nullable. Java cung cấp `java.util.OptionalInt`:

~~~java
import java.util.OptionalInt;

static OptionalInt findScore(boolean found) {
    return found ? OptionalInt.of(0) : OptionalInt.empty();
}
~~~

Bên gọi:

~~~java
OptionalInt missing = findScore(false);
OptionalInt existingZero = findScore(true);

System.out.println(missing.isEmpty());     // true
System.out.println(existingZero.orElse(-1)); // 0
~~~

`OptionalInt.empty()` **khác** `OptionalInt.of(0)`. Một API dùng `OptionalInt` thể hiện trạng thái có/không ngay trên kiểu trả về.

**Cân nhắc thực tế:** `OptionalInt` phù hợp nhất ở **kiểu trả về của method** có thể không có kết quả. Không mặc định thay mọi field `Integer` bằng field `OptionalInt`, vì các framework/định dạng dữ liệu hoặc code hiện có có thể có quy ước riêng. Chọn dựa trên hợp đồng API và hệ sinh thái đang dùng.

## 12. Primitive và wrapper về hiệu năng, bộ nhớ

Wrapper là object tham chiếu và có thể phát sinh chi phí **boxing/unboxing**, lưu tham chiếu, cấp phát, áp lực garbage collection tùy tình huống.

~~~java
int[] primitiveArray = new int[1_000];
Integer[] wrapperArray = new Integer[1_000];
~~~

Hai cấu trúc **khác nhau về ý nghĩa**:

- `int[]` giữ 1000 giá trị `int`, mỗi phần tử mặc định 0.
- `Integer[]` giữ 1000 **tham chiếu**, mỗi phần tử ban đầu null. Tạo mảng **không** tự tạo 1000 object `Integer`.
- Cách bố trí bộ nhớ, header và căn chỉnh object phụ thuộc JVM; **không** suy ra tổng RAM thực tế chỉ từ `Integer.SIZE`.

Trong xử lý số liệu khối lượng lớn, primitive array, primitive streams (`IntStream`, `LongStream`, `DoubleStream`) có thể giúp tránh một số wrapper không cần thiết:

~~~java
int total = java.util.stream.IntStream.of(2, 3, 5).sum();
System.out.println(total); // 10
~~~

Nhưng **không** nên khẳng định mọi primitive luôn nhanh hơn mọi wrapper trong mọi JVM. JIT có thể tối ưu hóa, cache có thể tái sử dụng wrapper, và hiệu năng còn tùy workload. Khi tối ưu thật sự quan trọng, hãy đo benchmark có phương pháp (ví dụ JMH).

**Nguyên tắc:** ưu tiên mô hình dữ liệu đúng trước; nếu nhiều lựa chọn đều đúng, cân nhắc tính đơn giản và đo hiệu năng thực tế.

## 13. Wrapper không giải quyết bài toán độ chính xác thập phân

Chọn `Double` thay `double` chỉ thay đổi **cách đóng gói giá trị và khả năng null**, không thay đổi quy tắc biểu diễn **binary floating-point**:

~~~java
double primitive = 0.1 + 0.2;
Double boxed = primitive;

System.out.println(primitive); // 0.30000000000000004
System.out.println(boxed);     // 0.30000000000000004
~~~

Nếu nghiệp vụ yêu cầu phép tính số tiền chính xác theo thập phân, cần chọn mô hình như `BigDecimal` hoặc số nguyên biểu diễn đơn vị nhỏ nhất:

~~~java
java.math.BigDecimal exact =
        new java.math.BigDecimal("0.1")
                .add(new java.math.BigDecimal("0.2"));

System.out.println(exact); // 0.3
~~~

**Tách hai quyết định độc lập:**

1. Giá trị có thể **thiếu** không? → primitive hay wrapper/kiểu kết quả có trạng thái vắng mặt.
2. Giá trị cần **độ chính xác và miền nào**? → int/long/double/BigDecimal/... theo toán học và nghiệp vụ.

Dùng wrapper chỉ vì “có vẻ chính xác hơn primitive” là sai về bản chất.

## 14. Thiết kế API: nêu rõ hợp đồng null và validation

Ví dụ một method nhận số lượng được phép thiếu:

~~~java
static String describeCount(Integer count) {
    return count == null ? "not reported" : "count=" + count;
}
~~~

Nó hợp lý khi **thiếu là một trạng thái hợp lệ**. Ngược lại, nếu dữ liệu bắt buộc có số lượng, hãy khai báo primitive:

~~~java
static boolean hasStock(int count) {
    if (count < 0) {
        throw new IllegalArgumentException("negative stock");
    }
    return count > 0;
}
~~~

Một field wrapper không nhất thiết nên `null`, và một primitive mặc định không nhất thiết hợp lệ. **Thiết kế dữ liệu cần thống nhất với validation**.

Các API nhận dữ liệu ngoài (JSON, database, HTTP request, form...) còn có thể phân biệt **không gửi field**, **gửi null**, **gửi 0**, hoặc **gửi chuỗi "0"**. Mapping cụ thể phụ thuộc framework, schema và quy ước API; không được đoán rằng mọi framework Java đều tự chuyển chúng thành primitive/wrapper giống nhau.

Khi nhận wrapper từ lớp ngoài, validate/chuẩn hóa tại ranh giới trước khi chuyển sang logic nội bộ cần primitive.

## 15. Chương trình Java 17 độc lập: chọn kiểu theo yêu cầu thực tế

Tạo tệp đúng tên **`PrimitiveOrWrapperDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public final class PrimitiveOrWrapperDemo {
    private PrimitiveOrWrapperDemo() {}

    static final class Defaults {
        int count;
        Integer optionalCount;
        boolean enabled;
        Boolean optionalEnabled;
    }

    record Stock(String sku, int onHand, Integer reserved, Boolean approved) {
        Stock {
            Objects.requireNonNull(sku, "sku is required");
            if (onHand < 0) {
                throw new IllegalArgumentException("negative onHand");
            }
            if (reserved != null && reserved < 0) {
                throw new IllegalArgumentException("negative reserved");
            }
        }
    }

    static String approvalLabel(Boolean approved) {
        if (approved == null) {
            return "unknown";
        }
        return approved ? "approved" : "rejected";
    }

    static int sumPresent(List<Integer> values) {
        int total = 0;
        for (Integer value : values) {
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    static OptionalInt findCount(boolean found) {
        return found ? OptionalInt.of(0) : OptionalInt.empty();
    }

    public static void main(String[] args) {
        Defaults defaults = new Defaults();
        System.out.println("default int=" + defaults.count);
        System.out.println("default Integer=" + defaults.optionalCount);
        System.out.println("default boolean=" + defaults.enabled);
        System.out.println("default Boolean=" + defaults.optionalEnabled);

        Stock unknown = new Stock("SKU-1", 10, null, null);
        Stock explicit = new Stock("SKU-1", 10, 0, false);
        System.out.println("required stock=" + unknown.onHand());
        System.out.println("missing reserved=" + (unknown.reserved() == null));
        System.out.println("reported reserved zero=" + explicit.reserved());
        System.out.println("approval unknown=" + approvalLabel(unknown.approved()));
        System.out.println("approval false=" + approvalLabel(explicit.approved()));
        System.out.println("approval true=" + approvalLabel(true));

        List<Integer> readings = Arrays.asList(2, null, 3);
        System.out.println("sum present=" + sumPresent(readings));

        Integer first = Integer.valueOf(1000);
        Integer second = Integer.valueOf(1000);
        Integer alias = first;
        System.out.println("wrapper value equality="
                + Objects.equals(first, second));
        System.out.println("same wrapper alias=" + (first == alias));

        Integer counter = 3;
        Integer snapshot = counter;
        counter++;
        System.out.println("wrapper counter=" + counter);
        System.out.println("wrapper snapshot=" + snapshot);

        Integer missing = null;
        try {
            int invalid = missing;
            System.out.println(invalid);
        } catch (NullPointerException ex) {
            System.out.println("null unboxing rejected");
        }

        OptionalInt absent = findCount(false);
        OptionalInt presentZero = findCount(true);
        System.out.println("optional absent=" + absent.isEmpty());
        System.out.println("optional zero=" + presentZero.orElse(-1));

        double binarySum = 0.1 + 0.2;
        Double wrappedSum = binarySum;
        System.out.println("double result=" + wrappedSum);
        System.out.println("decimal exact="
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));

        try {
            new Stock("SKU-X", -1, null, null);
        } catch (IllegalArgumentException ex) {
            System.out.println("negative stock rejected");
        }
    }
}
~~~

**Yêu cầu:** JDK 17+; mở terminal trong thư mục chứa tệp. Không cần Maven, Gradle hoặc thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 PrimitiveOrWrapperDemo.java
java -cp . PrimitiveOrWrapperDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\PrimitiveOrWrapperDemo.java
java -cp . PrimitiveOrWrapperDemo
~~~

**Expected output chính xác:**

~~~text
default int=0
default Integer=null
default boolean=false
default Boolean=null
required stock=10
missing reserved=true
reported reserved zero=0
approval unknown=unknown
approval false=rejected
approval true=approved
sum present=5
wrapper value equality=true
same wrapper alias=true
wrapper counter=4
wrapper snapshot=3
null unboxing rejected
optional absent=true
optional zero=0
double result=0.30000000000000004
decimal exact=0.3
negative stock rejected
~~~

**Điểm cần tự kiểm chứng:**

1. Field primitive `int`/`boolean` mặc định 0/false; field wrapper mặc định null.
2. Cùng một kiểu `Integer` phân biệt `reserved=null` và `reserved=0`.
3. `Boolean` cho phép trạng thái unknown bên cạnh false/true, nếu API có hợp đồng tương ứng.
4. Vòng lặp `List<Integer>` chủ động bỏ qua null **theo chính sách của ví dụ**.
5. So wrapper 1000 qua `Objects.equals` không dựa vào identity cache; `first == alias` true vì thật sự cùng tham chiếu.
6. Tăng `counter` không sửa object wrapper mà `snapshot` đang giữ.
7. Unboxing null được catch có chủ đích, minh họa lý do phải kiểm tra dữ liệu.
8. `OptionalInt.empty()` khác `OptionalInt.of(0)`.
9. `Double` wrapper không làm phép cộng thập phân trở nên exact; `BigDecimal` từ chuỗi đáp ứng một mô hình khác.
10. Constructor từ chối số lượng âm, thể hiện chọn đúng kiểu vẫn cần validation.

## 16. Những lỗi thường gặp và cách sửa

| Lỗi hoặc giả định | Vấn đề | Cách làm phù hợp |
|---|---|---|
| Dùng `int` cho dữ liệu “chưa được nhập” rồi ngầm dùng 0 | Gộp thiếu dữ liệu với số 0 | Dùng `Integer`/trạng thái vắng mặt rõ nghĩa |
| Dùng `Integer` cho mọi biến đếm | Tăng nhu cầu kiểm tra null/boxing | Dùng `int` khi dữ liệu bắt buộc và tính toán nhiều |
| `Boolean flag = null; if (flag)` | Unboxing null gây NPE | Kiểm tra null hoặc thiết kế enum |
| `Integer n = null; int x = n;` | Unboxing null gây NPE | Validate hoặc chọn policy rõ |
| Hai `Integer` so bằng `==` khi muốn so số | `==` so identity của hai tham chiếu | `equals`/`Objects.equals` |
| Coi mặc định field `int = 0` là dữ liệu hợp lệ | Default JVM không phải validation nghiệp vụ | Constructor/validation |
| Coi `Integer = null` tương đương số 0 | Mất ý nghĩa “chưa có dữ liệu” | Giữ và xử lý riêng null |
| Dùng `List<int>` | Generic Java 17 không nhận primitive | `List<Integer>` |
| Duyệt `List<Integer>` bằng `for (int n : list)` khi có null | Unboxing null trong vòng lặp | Validation hoặc xử lý từng `Integer` |
| Dùng `OptionalInt` bất kể API/framework | Khác hợp đồng/khả năng tích hợp | Dùng phù hợp ở kiểu trả về |
| Dùng `Double` thay `double` để sửa sai số thập phân | Wrapper không đổi biểu diễn binary | `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất |
| Cho rằng wrapper luôn cấp phát mới khi boxing | JVM có thể cache/tối ưu | Không phụ thuộc identity/cấp phát |
| Dùng primitive để tránh mọi lỗi dữ liệu | Primitive vẫn có giới hạn/tràn/giá trị không hợp lệ | Validation và chọn miền giá trị |
| Tự động đổi null → 0 ở mọi nơi | Có thể làm sai ý nghĩa nghiệp vụ | Thống nhất chính sách mặc định trước |

## 17. Bài tập ngắn và đáp án

### Bài A — số lượng đã báo cáo

`count=0` khác `count=null` như thế nào trong hệ thống ghi nhận hàng hóa?

**Đáp án:** `0` nghĩa là có một báo cáo xác nhận số lượng bằng không; `null` có thể biểu thị chưa có số liệu (nếu hợp đồng API định nghĩa như vậy). Nếu cần phân biệt hai trạng thái này, `Integer` hoặc kiểu có trạng thái vắng mặt là phù hợp.

### Bài B — primitive hay wrapper cho cờ trạng thái?

API chỉ có bật/tắt, không có trạng thái chưa biết. Chọn `boolean` hay `Boolean`?

**Đáp án:** thường chọn `boolean`. Nếu API có trạng thái “chưa duyệt” riêng, có thể dùng `Boolean` cùng quy tắc null hoặc một `enum` mang tên trạng thái rõ ràng.

### Bài C — null và unboxing

~~~java
Integer n = null;
// System.out.println(n + 1);
~~~

**Đáp án:** nếu bật dòng comment sẽ biên dịch được nhưng ném `NullPointerException` lúc chạy, vì phép cộng phải unbox `n` thành int trước.

### Bài D — so giá trị

~~~java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);
System.out.println(java.util.Objects.equals(a, b));
~~~

**Đáp án:** `true`. Dùng equality theo giá trị, không phụ thuộc vùng object cache.

### Bài E — generic

~~~java
java.util.List<Integer> nums = java.util.List.of(2, 3);
int sum = 0;
for (int n : nums) {
    sum += n;
}
System.out.println(sum);
~~~

**Đáp án:** `5`. List dùng wrapper; mỗi phần tử được unbox khi gán vào biến vòng lặp int. Đối với `List.of`, không được đưa null vào danh sách.

### Bài F — lựa chọn kiểu và miền nghiệp vụ

Một field `age` luôn phải được cung cấp và phải không âm. Dùng `int` có đủ để đảm bảo dữ liệu hợp lệ?

**Đáp án:** **chưa đủ**. `int` loại bỏ khả năng null nhưng vẫn chứa số âm. Cần kiểm tra `age >= 0` tại ranh giới input hoặc constructor.

## 18. Checklist và kết luận

- [ ] Biết tám cặp primitive-wrapper Java.
- [ ] Hiểu primitive không mang `null`, wrapper có thể `null`.
- [ ] Phân biệt dữ liệu bằng 0/false với dữ liệu chưa có.
- [ ] Chọn primitive khi giá trị bắt buộc và chủ yếu tính toán.
- [ ] Chọn wrapper khi thật sự cần nullable hoặc dùng API/generic yêu cầu reference type.
- [ ] Biết default field primitive và wrapper khác nhau; local variable phải được gán trước khi đọc.
- [ ] Biết boxing/unboxing và xử lý null trước khi unbox.
- [ ] Không so giá trị của hai wrapper bằng `==`.
- [ ] Kiểm tra input/miền dữ liệu ngay cả khi chọn primitive.
- [ ] Hiểu `List<Integer>` khác `int[]`, và lưu ý null trong collection.
- [ ] Biết `OptionalInt` là một lựa chọn cho method trả kết quả có thể vắng mặt.
- [ ] Không nhầm `Double` wrapper với giải pháp tính toán decimal chính xác.
- [ ] Biết cân nhắc bộ nhớ/hiệu năng dựa trên workload, không chỉ cảm tính.
- [ ] Chạy `PrimitiveOrWrapperDemo.java` trên Java 17+ và so từng dòng expected output.

**Kết luận:** Chọn **primitive** hay **wrapper** dựa trước tiên vào **ý nghĩa dữ liệu**: bắt buộc hay có thể vắng mặt. Primitive phù hợp với giá trị luôn tồn tại và tính toán trực tiếp; wrapper phù hợp với nullable, collection và API cần object. Dù chọn kiểu nào, bạn vẫn cần quy định miền hợp lệ, cách xử lý null, phép so sánh và độ chính xác đúng với yêu cầu thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)
