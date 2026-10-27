<a id="back-to-top"></a>

# Phân tích số thực

## Menu
- [Float.parseFloat, Double.parseDouble và valueOf](#floating-point-parsing-001)
- [Dạng thập phân, ký hiệu mũ và dạng hex được API hỗ trợ](#floating-point-parsing-002)
- [NaN, Infinity và dấu trong đầu vào](#floating-point-parsing-003)
- [Đầu vào hợp lệ có thể làm tròn, overflow hoặc underflow](#floating-point-parsing-004)
- [Phân biệt parse số theo cú pháp Java với parse theo locale](#floating-point-parsing-005)
- [Chuỗi thập phân sang BigDecimal để giữ ý nghĩa thập phân](#floating-point-parsing-006)
- [Vì sao new BigDecimal(double) khác new BigDecimal(String)](#floating-point-parsing-007)
- [BigDecimal.valueOf(double) và giới hạn của giá trị double nguồn](#floating-point-parsing-008)
- [Kiểm tra giá trị hữu hạn và miền dữ liệu sau khi parse](#floating-point-parsing-009)

## <a id="floating-point-parsing-001">Float.parseFloat, Double.parseDouble và valueOf</a>

<details>
<summary>Xem chi tiết</summary>

# Float.parseFloat, Double.parseDouble và valueOf trong Java

## 1. Mục tiêu: cùng chuỗi số thực, khác kiểu trả về

Trong Java, một chuỗi như `"3.5"` có thể được phân tích thành giá trị số thực primitive hoặc thành đối tượng wrapper:

```java
float f = Float.parseFloat("3.5");
Float boxedF = Float.valueOf("3.5");

double d = Double.parseDouble("3.5");
Double boxedD = Double.valueOf("3.5");
```

Cả bốn biến cùng thể hiện giá trị toán học 3,5 ở ví dụ trên, nhưng **kiểu dữ liệu và cách làm việc với chúng khác nhau**:

| Phương thức | Kiểu đầu vào | Kiểu trả về | Vai trò |
| --- | --- | --- | --- |
| `Float.parseFloat(String)` | `String` | primitive `float` | Phân tích về binary32 |
| `Float.valueOf(String)` | `String` | wrapper `Float` | Phân tích và trả đối tượng chứa binary32 |
| `Double.parseDouble(String)` | `String` | primitive `double` | Phân tích về binary64 |
| `Double.valueOf(String)` | `String` | wrapper `Double` | Phân tích và trả đối tượng chứa binary64 |

Bài này thuộc **Java Core — Numbers / Phân tích số thực**. Sau khi hoàn thành, bạn cần:

1. Đọc đúng chữ ký và chọn `parseFloat`/`parseDouble` khi cần primitive, `valueOf(String)` khi cần wrapper.
2. Hiểu **precision 32 bit so với 64 bit**: thay `Float` bằng `Double` không chỉ đổi lớp object mà còn đổi độ chính xác và miền giá trị.
3. Nhận diện parser có ngữ pháp số riêng, lỗi cú pháp và giá trị đặc biệt cần xác thực tách biệt.
4. Đọc được hiệu ứng boxing/unboxing, overload, wrapper equality, signed zero và NaN.
5. Tự chạy và giải thích ví dụ Java 17+ về kiểu trả về, precision và so sánh.

**Nguyên tắc chính:** `parseFloat` khác `Float.valueOf(String)` ở **kiểu trả về**; `parseDouble` khác `Double.valueOf(String)` tương tự. Nhưng `Float` và `Double` lại **khác nhau về định dạng số nhị phân và độ chính xác**. Không được gộp hai loại khác biệt này thành một.

## 2. Parse trả primitive, valueOf(String) trả wrapper

Hai cặp phương thức có chữ ký chuẩn:

```text
public static float  Float.parseFloat(String s)
public static Float  Float.valueOf(String s)
public static double Double.parseDouble(String s)
public static Double Double.valueOf(String s)
```

Các dòng trên là **cách ghi chữ ký để minh họa**, không phải mã Java dùng để khai báo lớp mới.

### 2.1. Cùng cách đọc chuỗi, khác kiểu đích

`Float.valueOf(s)` phân tích chuỗi theo cùng ngữ nghĩa như `Float.parseFloat(s)` và trả một `Float` chứa giá trị `float` thu được. `Double.valueOf(s)` tương tự với `Double.parseDouble(s)` và wrapper `Double`.

```java
float raw = Float.parseFloat("2.75");
Float object = Float.valueOf("2.75");

System.out.println(raw);       // 2.75
System.out.println(object);    // 2.75
```

Stdout giống nhau vì `println` hỗ trợ cả primitive và object. **Không được suy từ dòng in** rằng hai biểu thức có cùng kiểu compile-time.

### 2.2. Khác biệt kiểu ảnh hưởng tính toán hay API object

Primitive `float`/`double` phù hợp với toán học và các API nhận primitive. Wrapper `Float`/`Double` là đối tượng immutable, có thể:

- Được lưu trong `List<Float>` hoặc `Map<String,Double>`.
- Có thể nằm trong biến tham chiếu nhận `null` để biểu diễn trạng thái thiếu dữ liệu, nếu thiết kế cho phép.
- Cung cấp các phương thức instance như `equals`, `compareTo` và `hashCode`.
- Được dùng với generics vốn **không nhận primitive** làm type argument.

Điều này không có nghĩa mọi lời gọi `valueOf` đều buộc JVM tạo đối tượng mới. Wrapper có thể được tái sử dụng tùy chính sách implementation/API. **Không dựa vào tham chiếu `==` để kiểm tra cùng giá trị số** giữa hai đối tượng wrapper.

## 3. Boxing và unboxing làm hai cách viết trông giống nhau

Java có autoboxing/unboxing:

```java
float a = Float.parseFloat("1.25");    // float -> float
Float b = Float.valueOf("1.25");      // Float -> Float

Float c = Float.parseFloat("1.25");   // boxing float -> Float
float d = Float.valueOf("1.25");      // unboxing Float -> float

double e = Double.parseDouble("1.25"); // double -> double
Double f = Double.valueOf("1.25");     // Double -> Double

Double g = Double.parseDouble("1.25");// boxing double -> Double
double h = Double.valueOf("1.25");    // unboxing Double -> double
```

Cả tám phép gán đều hợp lệ. **Kiểu trả về gốc của phương thức không đổi** khi biến bên trái có kiểu khác: compiler chỉ áp dụng conversion được phép trong ngữ cảnh gán.

| Biểu thức nguồn | Biến đích | Bước bổ sung |
| --- | --- | --- |
| `Float.parseFloat(s)` | `float` | Không |
| `Float.parseFloat(s)` | `Float` | Boxing |
| `Float.valueOf(s)` | `Float` | Không |
| `Float.valueOf(s)` | `float` | Unboxing |
| `Double.parseDouble(s)` | `double` | Không |
| `Double.parseDouble(s)` | `Double` | Boxing |
| `Double.valueOf(s)` | `Double` | Không |
| `Double.valueOf(s)` | `double` | Unboxing |

**Lưu ý:** nếu một biến wrapper được gán `null` bởi nguồn khác, unboxing có thể ném `NullPointerException`:

```java
Double missing = null;
// double result = missing; // runtime NullPointerException
```

Nhưng `Double.valueOf("1.25")` với chuỗi hợp lệ không trả `null`: nó trả một đối tượng `Double`.

## 4. Sự khác nhau giữa float và double không phải chỉ là wrapper

`float` và `double` đều là primitive floating-point theo binary IEEE 754, nhưng có độ rộng và độ chính xác khác nhau.

| Thuộc tính | `float` | `double` |
| --- | --- | --- |
| Khuôn dạng | binary32 | binary64 |
| Độ dài | 32 bit | 64 bit |
| Significand precision | 24 bit | 53 bit |
| Khoảng chữ số thập phân có nghĩa hữu dụng gần đúng | 6–9 | 15–17 |
| Hằng số dương hữu hạn lớn nhất | `Float.MAX_VALUE` | `Double.MAX_VALUE` |
| Số dương khác 0 nhỏ nhất (subnormal) | `Float.MIN_VALUE` | `Double.MIN_VALUE` |
| Hàm phân tích | `Float.parseFloat(s)` | `Double.parseDouble(s)` |

Các số thập phân như `0.1` hầu hết không thể lưu đúng tuyệt đối trong biểu diễn nhị phân hữu hạn. `Float.parseFloat("0.1")` được làm tròn trực tiếp sang `float`; `Double.parseDouble("0.1")` được làm tròn sang `double`. Từ đó, **hai giá trị có thể khác nhau khi cùng được đưa về kiểu `double` để so sánh**.

### 4.1. Ví dụ độ chính xác

```java
float f = Float.parseFloat("0.1");
double d = Double.parseDouble("0.1");

System.out.println(f);           // 0.1
System.out.println(d);           // 0.1
System.out.println((double) f);  // 0.10000000149011612
System.out.println(f == d);      // false
```

Dòng in `0.1` cho `float` là dạng chuỗi thập phân ngắn tái tạo được giá trị khi parse lại; nó **không có nghĩa** `float` lưu đúng 1/10. Mở rộng `float` thành `double` ở dòng thứ ba cho thấy giá trị binary32 sau khi làm tròn.

Đặc biệt:

- `Float.parseFloat("0.1") == 0.1f` là true: cả hai có `float` và cùng giá trị binary32.
- `Float.parseFloat("0.1") == 0.1` là false: bên phải là `double`, còn `float` bên trái được widening nhưng không lấy lại bit chính xác đã mất.
- Khi cần biểu diễn thập phân chính xác theo nghiệp vụ, nên cân nhắc `BigDecimal` tạo trực tiếp từ **chuỗi gốc**, không cố nâng `float` thành `double` để “chữa” sai số.

### 4.2. Không parse về double rồi cast về float một cách máy móc

`(float) Double.parseDouble(s)` có thể trông giống `Float.parseFloat(s)`, nhưng Java/JDK cung cấp parser `float` riêng để làm tròn giá trị decimal theo quy tắc của **binary32**. Việc làm tròn trước sang binary64 rồi thu hẹp sang binary32 có thể tạo **double-rounding** tại các trường hợp ranh giới tinh vi.

Nếu yêu cầu là giá trị `float` được đọc từ chuỗi, **ưu tiên `Float.parseFloat(s)`**; không cần đi vòng qua `Double.parseDouble`. Nếu yêu cầu thực sự là `double`, dùng `Double.parseDouble` và giữ kiểu phù hợp trong các bước tính toán.

## 5. Parse thành công và validation là hai tầng riêng

`Double.parseDouble`/`Float.parseFloat` hỗ trợ số decimal (`"1.25"`, `"1e3"`), hexadecimal floating-point (`"0x1.8p1"`), suffix f/d và các token `NaN`, `Infinity`. Hậu tố không thay đổi kiểu trả về do API quy định. Chuỗi không đúng ngữ pháp như `"1_000.0"`, `"1,5"` và `"0x1.8"` gây `NumberFormatException`; đầu vào null gây `NullPointerException`.

Chuỗi hợp lệ nhưng quá lớn hoặc quá nhỏ có thể được làm tròn thành vô cực hoặc zero; `isFinite` loại NaN và Infinity nhưng không chứng minh không mất precision. Cú pháp chi tiết, số đặc biệt, rounding, locale, decimal exactness và validation miền được giải thích chuyên sâu ở những bài riêng của chương. Khi dùng `parseFloat` hay `parseDouble`, luôn lựa chọn kiểu đích trước và kiểm tra miền dữ liệu của kết quả.

## 6. Chương trình chạy được A: kiểu trả về và overload

Lưu thành **`FloatingParseTypes.java`**:

```java
public class FloatingParseTypes {
    static String describe(float x) {
        return "float " + x;
    }

    static String describe(Float x) {
        return "Float " + x;
    }

    static String describe(double x) {
        return "double " + x;
    }

    static String describe(Double x) {
        return "Double " + x;
    }

    public static void main(String[] args) {
        float primitiveFloat = Float.parseFloat("3.5");
        Float boxedFloat = Float.valueOf("3.5");
        double primitiveDouble = Double.parseDouble("3.5");
        Double boxedDouble = Double.valueOf("3.5");

        System.out.println(describe(primitiveFloat));
        System.out.println(describe(boxedFloat));
        System.out.println(describe(primitiveDouble));
        System.out.println(describe(boxedDouble));

        System.out.println(boxedFloat.getClass().getSimpleName());
        System.out.println(boxedDouble.getClass().getSimpleName());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 FloatingParseTypes.java
java FloatingParseTypes
```

**Stdout:**

```text
float 3.5
Float 3.5
double 3.5
Double 3.5
Float
Double
```

Bốn lời gọi `describe` chọn overload dựa trên **kiểu biểu thức** ở chỗ gọi, không dựa trên việc `"3.5"` trông như số thực thập phân. Đây là cách chứng minh thực tế sự khác nhau về kiểu trả về khi `println` mặc định lại in cùng dạng.

## 7. Wrapper equality, identity, NaN và signed zero

`Float.valueOf(String)`/`Double.valueOf(String)` trả đối tượng wrapper nên có hai kiểu so sánh dễ bị lẫn.

### 7.1. Hai wrapper: `equals` thay vì `==`

```java
Float a = Float.valueOf("1.5");
Float b = Float.valueOf("1.5");

System.out.println(a.equals(b)); // true
```

`a.equals(b)` dùng quy tắc equality của `Float`. `a == b` giữa hai reference `Float` xét **object identity**, có thể phụ thuộc caching; không nên dùng để hỏi hai số có cùng giá trị.

Nếu một bên là primitive `float`, phép `==` có thể unbox wrapper và so primitive:

```java
float raw = Float.parseFloat("1.5");
Float boxed = Float.valueOf("1.5");
System.out.println(raw == boxed); // true
```

Đừng suy mọi `==` viết với wrapper đều là phép so cùng kiểu giá trị; phải xem compile-time types của **cả hai** toán hạng.

### 7.2. `Float` khác `Double` theo `equals`

```java
System.out.println(Float.valueOf("1.5")
        .equals(Double.valueOf("1.5"))); // false
```

Hai wrapper khác class, nên `Float.equals` không coi `Double` tương đương, ngay cả khi cùng lượng toán học đúng 1.5. Nếu một API trộn `Number` hoặc nhiều wrapper, phải có chính sách dữ liệu rõ ràng.

### 7.3. NaN và signed zero

```java
System.out.println(Double.parseDouble("NaN")
        == Double.parseDouble("NaN")); // false

System.out.println(Double.valueOf("NaN")
        .equals(Double.valueOf("NaN"))); // true

System.out.println(Double.parseDouble("-0.0")
        == Double.parseDouble("0.0")); // true

System.out.println(Double.valueOf("-0.0")
        .equals(Double.valueOf("0.0"))); // false
```

Quy tắc này đến từ **primitive equality** và **wrapper equality**, không phải vì chuỗi `"NaN"` hay `"-0.0"` được parse “khác tiêu chuẩn” bởi `valueOf`.

`Double.compare(-0.0,+0.0)` cũng phân biệt hai zero và đặt zero âm trước zero dương. Với `Double.NaN`, `Double.compare` có thứ tự riêng phù hợp cho wrapper ordering. Khi dùng `HashSet<Double>` hoặc `TreeSet<Double>`, cần hiểu các quy tắc này thay vì tùy tiện thêm epsilon vào `equals` hoặc comparator.

## 8. valueOf có những overload nào?

Tên `valueOf` ở các wrapper xuất hiện với nhiều kiểu tham số, không phải overload nào cũng parse chuỗi:

```java
Float a = Float.valueOf(2.5f);    // valueOf(float), không parse String
Float b = Float.valueOf("2.5");   // valueOf(String), parse chuỗi

Double c = Double.valueOf(2.5);   // valueOf(double), không parse String
Double d = Double.valueOf("2.5"); // valueOf(String), parse chuỗi
```

**Quan trọng:** `Float.valueOf(2.5f)` đã nhận giá trị binary32 có sẵn; `Float.valueOf("2.5")` mới bắt đầu từ ký tự chuỗi. `Double.valueOf(2.5)` nhận một `double` có sẵn.

Nếu nguồn đầu vào là một `float` rồi chuyển sang `Double`:

```java
float f = Float.parseFloat("0.1");
Double wrappedWidened = Double.valueOf((double) f);
```

`wrappedWidened` chứa chính giá trị `float` đã được làm tròn trước rồi widening, **không phải** giá trị binary64 có được từ `Double.valueOf("0.1")`. Chuyển wrapper không thể làm mất những bước làm tròn đã xảy ra biến mất.

## 9. Bài tập củng cố kiểu trả về

1. So sánh kiểu compile-time của `Float.parseFloat("1.5")` và `Float.valueOf("1.5")`; khi đưa vào overload `describe(float)` và `describe(Float)`, lời gọi nào được chọn?
2. Giải thích vì sao `Float f = Float.parseFloat("2.5")` hợp lệ dù `parseFloat` trả primitive.
3. Dự đoán `Float.valueOf("1.5").equals(Double.valueOf("1.5"))` và lý giải hợp đồng wrapper.
4. Nêu lý do `Float.parseFloat("0.1") == Double.parseDouble("0.1")` là false nhưng `Float.parseFloat("0.1") == 0.1f` là true.
5. Chọn parser và kiểu dữ liệu phù hợp cho một bộ đo nhận `double` và một `List<Float>`. Điều kiện nào cần được kiểm tra sau khi parse?

**Đối chiếu:** `parseFloat` trả primitive, `valueOf(String)` trả wrapper; boxing/unboxing không đổi chữ ký. Wrapper khác lớp không `equals` dù cùng lượng toán học, còn giá trị binary32 không bằng binary64 khi decimal 0.1 được làm tròn khác nhau. Chọn API theo kiểu đích, đồng thời xác thực tính hữu hạn/miền theo hợp đồng.

**Tài liệu:** [Float](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html), [Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html), [JLS boxing/unboxing](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-002">Dạng thập phân, ký hiệu mũ và dạng hex được API hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Dạng thập phân, ký hiệu mũ và dạng hex được API hỗ trợ trong Java

## 1. Đọc chuỗi thành số thực: phải phân biệt cú pháp với giá trị

Trong Java, **Double.parseDouble(String)** và **Float.parseFloat(String)** chuyển biểu diễn văn bản của số thực thành giá trị IEEE 754 tương ứng. Cả hai hỗ trợ dạng thập phân thông thường, ký hiệu mũ cơ số 10 và dạng thập lục phân (hexadecimal floating-point) có số mũ cơ số 2. Chúng cũng nhận một số biểu diễn đặc biệt như NaN và Infinity.

Ví dụ:

~~~java
double a = Double.parseDouble("12.5");     // 12.5
double b = Double.parseDouble("1.25e2");   // 125.0
double c = Double.parseDouble("0x1.8p1");  // 3.0
float d = Float.parseFloat("-2.5E-1");    // -0.25f
~~~

Điểm cần nhớ: **chuỗi truyền vào API** và **literal viết trực tiếp trong mã Java** có cú pháp gần nhau nhưng không đồng nhất. Một chuỗi hợp lệ cho parser chưa chắc được viết y nguyên thành literal không cần kiểu; ngược lại, ký tự gạch dưới phân tách chữ số được phép trong một số literal Java nhưng không được các phương thức parse này chấp nhận.

## 2. Ba nhóm biểu diễn chính

| Nhóm | Ví dụ chuỗi hợp lệ | Ý nghĩa |
| --- | --- | --- |
| Thập phân thông thường | "42", "-3.5", ".75", "8." | Chữ số cơ số 10, có thể có dấu và dấu chấm thập phân |
| Thập phân có mũ | "1e3", "-2.5E-2", "6.02e23" | Phần định trị nhân với 10 lũy thừa của số mũ |
| Hex có mũ nhị phân | "0x1.8p1", "0X1P-2", "-0xA.BP3" | Phần định trị viết ở cơ số 16, nhân với 2 lũy thừa của số mũ sau p/P |

Ở đây, dấu chấm thập phân trong chuỗi luôn là **dấu chấm (.)**, kể cả khi người dùng quen viết dấu phẩy. Dấu cộng/trừ có thể đứng trước toàn bộ số; dấu cộng/trừ cũng có thể đứng trong phần số mũ ngay sau e/E hoặc p/P.

### 2.1. Dạng thập phân

Các ví dụ đều hợp lệ với Double.parseDouble:

~~~text
0
123
+123.0
-0.125
.5
5.
0.000001
12f
12.5D
~~~

**f/F và d/D** là hậu tố kiểu số thực được bộ parse chấp nhận, kể cả khi phương thức trả về kiểu còn lại. Chẳng hạn, Double.parseDouble("12f") vẫn trả về giá trị kiểu double; hậu tố trong chuỗi không đổi kiểu trả về. Trong mã Java, nên ưu tiên chuỗi không có hậu tố khi nhận dữ liệu từ người dùng hoặc hệ thống ngoài.

Không được dùng dấu phân nhóm hàng nghìn trong chuỗi truyền cho Double.parseDouble: "1,000.5" không hợp lệ. Chuỗi "1_000.5" cũng không hợp lệ dù literal 1_000.5 trong *mã nguồn Java* là hợp lệ.

### 2.2. Ký hiệu mũ thập phân: e/E

Cú pháp trực giác là:

~~~text
[+|-]phần_định_trị[e|E][+|-]số_mũ_nguyên
~~~

Phần định trị là số thập phân; số mũ là số nguyên biểu diễn lũy thừa của **10**.

- "1.5e2" = 1.5 × 10² = 150.0.
- "4E-3" = 4 × 10⁻³ = 0.004.
- "-2.5e+1" = -2.5 × 10¹ = -25.0.
- "1e0" = 1.0.

Đã xuất hiện e/E thì phải có số mũ hợp lệ sau đó. "1e", "1e+" và "e3" đều không hợp lệ. Chữ e/E không có nghĩa là hằng số toán học Euler; đây là ký hiệu số mũ thập phân.

### 2.3. Dạng hex: 0x/0X và p/P

Một số thực hex có ba phần: tiền tố **0x hoặc 0X**, phần định trị bằng chữ số hex (0–9, a–f, A–F) có thể kèm dấu chấm, và **p/P cùng số mũ nguyên thập phân**. Về giá trị:

~~~text
(hex_significand) × 2^(exponent)
~~~

Ví dụ "0x1.8p1":

1. Phần nguyên hex **1** có giá trị 1.
2. Chữ số sau dấu chấm **8** ở cơ số 16 có giá trị 8/16 = 0.5.
3. Phần định trị bằng 1.5.
4. p1 nghĩa là nhân 2¹, nên kết quả bằng **3.0**.

Những ví dụ khác:

| Chuỗi | Phép tính | Kết quả |
| --- | --- | --- |
| "0x1p0" | 1 × 2⁰ | 1.0 |
| "0x1p-2" | 1 × 2⁻² | 0.25 |
| "0x1.8p2" | (1 + 8/16) × 2² | 6.0 |
| "0XAp1" | 10 × 2¹ | 20.0 |
| "0xA.Bp-1" | (10 + 11/16) × 2⁻¹ | 5.34375 |

**p/P là bắt buộc trong cú pháp số thực hex**, kể cả với số mũ bằng 0; "0x1" và "0xff" không được Double.parseDouble chấp nhận, còn "0x1p0" và "0xffp0" thì được. Số mũ sau p/P được viết bằng chữ số *thập phân* (ví dụ p10 nghĩa là nhân 2¹⁰); đây không phải số mũ cơ số 16. Chữ e trong phần định trị hex có thể là một chữ số hex nên không thay thế p/P.

Lý do dạng hex hữu ích: nó diễn tả một cách trực tiếp các phân số nhị phân và các lũy thừa của 2. Nhiều giá trị như 0.5, 0.25, 3.0 có thể được biểu diễn chính xác bằng dạng này; dạng thập phân như 0.1 thường phải làm tròn khi đưa về nhị phân.

## 3. Các chuỗi đặc biệt được chấp nhận

Ngoài chuỗi số hữu hạn, hai API còn nhận **"NaN"** (*not a number*) và **"Infinity"**, có thể kèm dấu cho dạng đặc biệt. Tên có phân biệt chữ hoa/chữ thường: "NaN" hợp lệ, "nan" không; "Infinity" hợp lệ, "infinity" không.

- "NaN": giá trị đặc biệt cho kết quả không phải số.
- "Infinity": vô cực dương.
- "-Infinity": vô cực âm.
- "-0.0": số không có bit dấu âm; nó có thể quan trọng trong một số phép toán và thao tác bit.

Không nên suy ra rằng mọi kết quả parse đều hữu hạn chỉ vì không xảy ra NumberFormatException. Khi số quá lớn để biểu diễn, parser có thể trả về Infinity; khi độ lớn quá nhỏ, parser có thể làm tròn về 0.0 hoặc giá trị *subnormal* thay vì ném ngoại lệ.

## 4. Ví dụ hoàn chỉnh: chạy và đối chiếu kết quả

Tạo tệp **FloatingTextFormsDemo.java**:

~~~java
public class FloatingTextFormsDemo {
    public static void main(String[] args) {
        String[] samples = {
                "12.5",
                "6.02e2",
                "0x1.8p1",
                "0x1p-2",
                "-0xA.Bp-1",
                "NaN",
                "Infinity",
                "1_000",
                "0xff",
                "1e"
        };

        for (String sample : samples) {
            try {
                double value = Double.parseDouble(sample);
                System.out.println(sample + " -> " + value);
            } catch (NumberFormatException exception) {
                System.out.println(sample + " -> INVALID");
            }
        }

        System.out.println("Hex form of 3.0: "
                + Double.toHexString(3.0));
        System.out.println("Overflow: "
                + Double.parseDouble("1e309"));
        System.out.println("Underflow: "
                + Double.parseDouble("1e-4000"));
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac FloatingTextFormsDemo.java
java FloatingTextFormsDemo
~~~

Kết quả chuẩn:

~~~text
12.5 -> 12.5
6.02e2 -> 602.0
0x1.8p1 -> 3.0
0x1p-2 -> 0.25
-0xA.Bp-1 -> -5.34375
NaN -> NaN
Infinity -> Infinity
1_000 -> INVALID
0xff -> INVALID
1e -> INVALID
Hex form of 3.0: 0x1.8p1
Overflow: Infinity
Underflow: 0.0
~~~

Chương trình minh họa bốn điều: cả ba cú pháp hợp lệ đều vào chung API; dạng hex có thể được đổi ngược qua Double.toHexString; chuỗi sai cú pháp phát sinh NumberFormatException; còn tràn số và mất độ lớn có thể trả ra giá trị đặc biệt hoặc 0.0.

**Double.toHexString(double)** sinh biểu diễn hex của giá trị double đang có (ví dụ 3.0 thành "0x1.8p1"); đây không phải phương thức phân tích một chuỗi đầu vào. Phép đổi qua lại giúp quan sát giá trị nhị phân đã được lưu nhưng không biến số thực nhị phân thành kiểu số có độ chính xác thập phân tùy ý.

## 5. Cú pháp literal trong mã Java khác cú pháp của chuỗi đưa vào API

Hãy phân biệt ngữ cảnh:

~~~java
double fromLiteral = 1_000.5;                  // Hợp lệ trong mã Java
double fromString = Double.parseDouble("1000.5"); // Hợp lệ
double fromHexLiteral = 0x1.8p1;             // Hợp lệ trong mã Java
double fromHexString = Double.parseDouble("0x1.8p1");
~~~

Không thể dùng cách sau để parse thành công:

~~~java
// Biên dịch được, nhưng lúc chạy ném NumberFormatException:
Double.parseDouble("1_000.5");
Double.parseDouble("0xff");
Double.parseDouble("0b1010");
Double.parseDouble("1,25");
~~~

Sự khác biệt quan trọng:

| Biểu thức hoặc chuỗi | Mã nguồn Java | Double.parseDouble |
| --- | --- | --- |
| 1_000.5 | Literal hợp lệ | Chuỗi "1_000.5" không hợp lệ |
| 0x1.8p1 | Literal double hợp lệ | Chuỗi "0x1.8p1" hợp lệ |
| 0xff | Literal số nguyên hex hợp lệ | Chuỗi "0xff" không hợp lệ dưới dạng số thực hex |
| 0b1010 | Literal số nguyên nhị phân hợp lệ | Chuỗi "0b1010" không hợp lệ |
| 1,25 | Không phải một literal số thực | Chuỗi "1,25" không hợp lệ |

Các ví dụ trong bảng so sánh **ngữ cảnh biên dịch** và **giá trị String lúc chạy**. Không đưa dấu ngoặc kép vào literal số; trong cột API, chúng biểu diễn một chuỗi.

## 6. Những chi tiết quyết định hành vi parser

### 6.1. Không có quy tắc dấu phẩy theo locale

Double.parseDouble và Float.parseFloat không dùng Locale để quyết định dấu thập phân: chuỗi chuẩn cần dấu chấm. Nếu dữ liệu đến từ giao diện người dùng theo vùng địa lý (ví dụ cách viết "1.234,5"), hãy dùng NumberFormat hoặc DecimalFormat với Locale xác định và chính sách kiểm tra phần dư của chuỗi. NumberFormat.parse có quy tắc và kiểu kết quả khác với Double.parseDouble; một số cấu hình có thể chỉ đọc được tiền tố hợp lệ, vì vậy cần kiểm tra vị trí parse khi đòi hỏi toàn chuỗi hợp lệ.

### 6.2. Khoảng trắng ở đầu/cuối và ở giữa

Các phương thức parse số thực chấp nhận một số ký tự điều khiển/khoảng trắng **đầu và cuối** tương ứng với hành vi String.trim() (ký tự có mã không lớn hơn U+0020). Chẳng hạn "  1.5  " hợp lệ. Không được tùy ý đặt khoảng trắng giữa chữ số, dấu chấm, ký hiệu mũ và số mũ: "1 e3" hoặc "0x1.8 p1" không hợp lệ. Không nên giả định mọi khoảng trắng Unicode đều được parser tự bỏ.

### 6.3. Độ chính xác, tràn và giá trị không hữu hạn

- **double** có 64 bit theo IEEE 754, xấp xỉ 15–17 chữ số thập phân có nghĩa; **float** có 32 bit, xấp xỉ 6–9 chữ số có nghĩa.
- Parser nhận dạng cú pháp rồi chuyển sang biểu diễn nhị phân với phép làm tròn phù hợp. Chuỗi hợp lệ không bảo đảm biểu diễn chính xác mọi chữ số thập phân.
- Double.parseDouble("1e309") trả về Infinity; Double.parseDouble("1e-4000") trả về 0.0.
- Float.parseFloat nhận cùng nhóm cú pháp nhưng có phạm vi và độ chính xác hẹp hơn; cùng một chuỗi có thể cho giá trị làm tròn khác với double.
- Nếu dữ liệu nghiệp vụ chỉ cho phép giá trị hữu hạn, kiểm tra bằng Double.isFinite/Float.isFinite sau khi parse. Nếu cần phát hiện mất độ chính xác hoặc ngoài phạm vi, phải xác định quy tắc dữ liệu trước khi chuyển đổi.

Ví dụ hàm tiện ích chỉ nhận số double hữu hạn:

~~~java
public static double parseFiniteDouble(String text) {
    double value = Double.parseDouble(text);
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Gia tri phai huu han");
    }
    return value;
}
~~~

Đây là **đoạn phương thức để đặt vào một class**, không phải chương trình Java độc lập. Hàm chấp nhận mọi cú pháp mà Double.parseDouble chấp nhận, nhưng loại kết quả NaN và Infinity (kể cả Infinity do tràn). Hàm vẫn có thể nhận một số rất nhỏ bị làm tròn thành 0.0; tùy bài toán mà bổ sung bước xác minh.

### 6.4. Ngoại lệ và các phương thức liên quan

- **NumberFormatException**: chuỗi không khớp cú pháp số thực được hỗ trợ, ví dụ "", "1e", "0x1" hoặc "3,14".
- **NullPointerException**: truyền null vào Double.parseDouble/Float.parseFloat; xử lý null riêng nếu đầu vào có thể thiếu.
- **Double.valueOf(String)** và **Float.valueOf(String)** nhận cùng cú pháp và trả về lớp wrapper tương ứng; parseDouble/parseFloat trả về kiểu nguyên thủy.
- **Double.toString** và **Float.toString** cho chuỗi biểu diễn thập phân của giá trị đã lưu; **Double.toHexString** và **Float.toHexString** cho biểu diễn hex của giá trị tương ứng.

Khi hệ thống cần số thập phân chính xác để tính tiền hoặc lưu dữ liệu có quy tắc thập phân, cân nhắc **BigDecimal(String)** với chuỗi thập phân hợp lệ. BigDecimal không thay thế trực tiếp Double.parseDouble: nó không chấp nhận cú pháp số thực hex, NaN hay Infinity, và có ngữ nghĩa độ chính xác khác. Đừng tạo BigDecimal từ một double nếu mục tiêu là giữ nguyên chính xác chuỗi thập phân đầu vào; dùng constructor nhận String.

## 7. Lỗi phổ biến và cách chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| "1,23" bị từ chối | Dấu phẩy không phải dấu thập phân của parser | Dùng dấu chấm hoặc parser theo Locale |
| "1e" bị từ chối | Thiếu chữ số sau e/E | Bổ sung số mũ, ví dụ "1e0" |
| "0x10" bị từ chối | Dạng số thực hex thiếu p/P và số mũ | Dùng "0x10p0" nếu thực sự là số thực hex |
| "1_000.0" bị từ chối | Dấu gạch dưới chỉ là cú pháp literal trong mã Java | Bỏ dấu gạch dưới trước khi parse nếu dữ liệu cho phép |
| Parse thành công nhưng thành Infinity | Giá trị vượt phạm vi float/double | Kiểm tra isFinite và ràng buộc miền giá trị |
| Parse thành công nhưng thành 0.0 | Giá trị khác 0 quá nhỏ so với kiểu đích | Kiểm tra giới hạn miền và chọn biểu diễn phù hợp |
| Hiển thị đuôi chữ số lẻ | Làm tròn nhị phân của float/double | Không so sánh giá trị thập phân như thể luôn chính xác |

Không nên xử lý mọi NumberFormatException bằng cách âm thầm thay số 0. Việc này làm lẫn dữ liệu sai cú pháp với giá trị 0 hợp lệ. Với đầu vào bên ngoài, quy định rõ chuỗi có thể mang định dạng hex hay NaN/Infinity không; nếu chỉ chấp nhận dạng thập phân hữu hạn, phải thực thi thêm quy tắc kiểm tra đó.

## 8. Tự kiểm tra

1. Tại sao "1.2e3" có giá trị 1200.0 nhưng "0x1.2p3" có giá trị 9.0? Vì e dùng lũy thừa **10**, còn p dùng lũy thừa **2**; phần định trị hex 0x1.2 bằng 1 + 2/16 = 1.125, nhân 2³ bằng 9.0.
2. Double.parseDouble("0x1p-1") trả về gì? **0.5**.
3. Double.parseDouble("0x1e2") thành công không? **Không**: thiếu ký hiệu p/P và số mũ nhị phân. Chữ e nằm trong phần chữ số hex.
4. Double.parseDouble("1e309") có ném NumberFormatException không? **Không**: cú pháp hợp lệ nhưng giá trị vượt phạm vi double, kết quả là Infinity.
5. Muốn nhận số thập phân có phân cách theo ngôn ngữ/vùng và xác minh đã đọc hết đầu vào thì làm gì? Dùng **NumberFormat/DecimalFormat với Locale tường minh** và kiểm tra vị trí kết thúc parse; không mặc định rằng Double.parseDouble sẽ tự xử lý locale.

**Ghi nhớ:** Với API parse số thực chuẩn của Java, e/E biểu thị mũ cơ số 10; 0x...p/P... biểu thị phần định trị hex với mũ cơ số 2; cả hai được Double.parseDouble và Float.parseFloat hỗ trợ. Kiểm tra đồng thời **đúng cú pháp**, **đúng miền giá trị** và **độ chính xác cần thiết**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-003">NaN, Infinity và dấu trong đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# NaN, Infinity và dấu trong đầu vào số thực Java

Khi nhận một chuỗi từ bàn phím, biểu mẫu, CSV, API hoặc tệp cấu hình, không thể mặc định rằng **phân tích (*parse*) thành công nghĩa là có một số hữu hạn, khác 0 và hợp lệ nghiệp vụ**. Trong Java, `Double.parseDouble` và `Float.parseFloat` chấp nhận một số giá trị đặc biệt của dấu phẩy động:

- `NaN` (*Not a Number*) — giá trị không phải số hữu hạn thông thường.
- `Infinity` — dương vô cực; `-Infinity` — âm vô cực.
- Dấu `+` và `-` ở đầu chuỗi, bao gồm `+0.0` và `-0.0`. Hai zero có cùng giá trị so sánh số học nhưng **khác bit dấu**.
- Những số rất lớn hoặc rất nhỏ có thể **tràn thành vô cực** hoặc **làm tròn thành zero** ngay cả khi chuỗi có cú pháp số hợp lệ.

Đề bài thuộc nhóm **phân tích số thực**, nên trọng tâm là **chuỗi đầu vào được chấp nhận thế nào, giá trị nào được tạo ra, và phải xác thực kết quả ra sao**, thay vì chỉ thảo luận phép toán với `NaN`/`Infinity` đã tồn tại.

## 1. Hai API parse cơ bản

Java cung cấp:

```java
double d = Double.parseDouble("12.5");
float f = Float.parseFloat("-3.25");
```

Hai phương thức `static` này trả primitive (`double` và `float`). Các wrapper `Double.valueOf(String)` và `Float.valueOf(String)` tạo **đối tượng wrapper** chứa giá trị tương ứng; quy tắc phân tích chuỗi cơ bản là tương đương.

| Cú pháp | Kiểu trả về | Với chuỗi sai cú pháp | Với tham số `null` |
| --- | --- | --- | --- |
| `Double.parseDouble(String)` | `double` | `NumberFormatException` | `NullPointerException` |
| `Float.parseFloat(String)` | `float` | `NumberFormatException` | `NullPointerException` |
| `Double.valueOf(String)` | `Double` | `NumberFormatException` | `NullPointerException` |
| `Float.valueOf(String)` | `Float` | `NumberFormatException` | `NullPointerException` |

`NumberFormatException` là lỗi **chuỗi không khớp cú pháp được hỗ trợ**. Ngược lại, nhiều giá trị **ngoài miền hữu hạn** vẫn có cú pháp hợp lệ và parse thành công thành vô cực hoặc zero. Vì vậy `try/catch (NumberFormatException ...)` chỉ giải quyết **một phần** xác thực dữ liệu.

## 2. Chuỗi đặc biệt `NaN`, `Infinity` và dấu

Trong ngữ pháp của các phương thức parse số thực, chuỗi `NaN` và `Infinity` dùng **đúng chữ hoa/chữ thường**; có thể có tiền tố dấu `+` hoặc `-`.

| Đầu vào | `Double.parseDouble` trả về | Lưu ý |
| --- | --- | --- |
| `"NaN"` | `NaN` | Không có giá trị số học hữu hạn |
| `"+NaN"` | `NaN` | Dấu không tạo NaN dương có thể so sánh được |
| `"-NaN"` | `NaN` | Không nên suy ra ngữ nghĩa dấu NaN từ dấu chuỗi |
| `"Infinity"` | `Infinity` | Dương vô cực |
| `"+Infinity"` | `Infinity` | Dương vô cực |
| `"-Infinity"` | `-Infinity` | Âm vô cực |
| `"infinity"` | Lỗi | Phân biệt chữ hoa/chữ thường |
| `"Inf"` | Lỗi | Không phải cách viết đặc biệt chuẩn của API |
| `"nan"` | Lỗi | Không phải `"NaN"` |
| `"∞"` | Lỗi | Không phải tên literal được parser này nhận |

```java
System.out.println(Double.parseDouble("NaN"));       // NaN
System.out.println(Double.parseDouble("-NaN"));      // NaN
System.out.println(Double.parseDouble("+Infinity")); // Infinity
System.out.println(Double.parseDouble("-Infinity")); // -Infinity
```

**Không nên coi `-NaN` là một số “âm” theo nghĩa toán học.** NaN không có thứ tự số học bình thường. Dấu trong đầu vào NaN không cung cấp một thuộc tính nghiệp vụ đáng tin; một số cách biểu diễn bit của NaN có thể có bit dấu, nhưng đây không phải quy tắc phân loại NaN âm/dương để ứng dụng dựa vào.

Các chuỗi trên có thể được parse thành **giá trị đặc biệt**; nếu API của bạn chỉ cho phép số hữu hạn, phải chủ động **từ chối** chúng sau parse hoặc xác thực cú pháp trước parse.

## 3. Dấu `+`/`-` với số bình thường và zero có dấu

Một dấu đứng trước giá trị số thập phân hợp lệ:

```java
double a = Double.parseDouble("+12.5"); // 12.5
double b = Double.parseDouble("-12.5"); // -12.5
double c = Double.parseDouble("+0.0");  // +0.0
double d = Double.parseDouble("-0.0");  // -0.0
```

Đáng chú ý, cả `"-0"` và `"-0.0"` đều parse thành **âm zero `double`**, dù `-0` không khác 0 khi dùng số nguyên primitive.

```java
double positiveZero = Double.parseDouble("+0");
double negativeZero = Double.parseDouble("-0");

System.out.println(positiveZero == negativeZero); // true
System.out.println(1.0 / positiveZero);           // Infinity
System.out.println(1.0 / negativeZero);           // -Infinity
```

Hai zero bằng nhau theo toán tử `==` vì đó là **so sánh số học dấu phẩy động**, nhưng chia một số hữu hạn khác zero cho chúng có thể tạo vô cực trái dấu.

### Kiểm tra dấu zero ở mức bit

```java
long positiveBits = Double.doubleToRawLongBits(+0.0); // 0
long negativeBits = Double.doubleToRawLongBits(-0.0); // 0x8000000000000000

boolean differentBits = positiveBits != negativeBits; // true
```

Đối với `float`, dùng `Float.floatToRawIntBits`. **Không dùng điều kiện `x < 0.0` để nhận diện âm zero**; `-0.0 < 0.0` là `false`.

Khi không cần phân biệt signed zero trong nghiệp vụ, có thể chuẩn hóa `x == 0.0 ? 0.0 : x` một cách **có chủ đích**. Nhưng việc chuẩn hóa này làm mất thông tin bit dấu và có thể đổi dấu kết quả chia cho zero sau đó.

## 4. Dấu, dấu cách và ngữ pháp chuỗi

`parseDouble` nhận một số định dạng chuỗi rộng hơn dạng “số có dấu chấm” đơn giản:

```java
Double.parseDouble("  +1.5  "); // 1.5: bỏ whitespace ASCII ở đầu/cuối
Double.parseDouble(".5");       // 0.5
Double.parseDouble("5.");       // 5.0
Double.parseDouble("1e3");      // 1000.0
Double.parseDouble("-2.5E-2");  // -0.025
Double.parseDouble("0x1.8p1");  // 3.0: biểu diễn floating point hex
Double.parseDouble("1.25f");    // 1.25: hậu tố literal float vẫn được nhận
```

Những điều cần lưu ý:

- Parser chấp nhận **ký hiệu mũ thập phân** `e`/`E` và **literal floating-point hex** `0x...p...`. Khi nhận dữ liệu JSON/CSV có schema chặt, bạn có thể **không muốn cho phép** toàn bộ ngữ pháp của parser.
- Parser chấp nhận một số dấu cách đầu/cuối theo quy tắc tương đương `String.trim()` đối với ký tự ASCII/control `<= U+0020`; đừng đồng nhất với mọi loại Unicode whitespace.
- Chuỗi số thực **không cho phép dấu gạch dưới** như literal ở mã nguồn Java. `"1_000.0"` bị từ chối bởi `Double.parseDouble`, dù `1_000.0` là literal hợp lệ trong mã nguồn.
- `"1,5"` **không** trở thành 1.5 theo locale Việt Nam hay một locale khác. `Double.parseDouble` có ngữ pháp độc lập locale và dùng dấu chấm.
- Chuỗi rỗng, dấu đứng một mình, hai dấu liên tiếp hoặc chữ không thuộc ngữ pháp có thể gây `NumberFormatException`.

**Không tự ý bỏ dấu nội bộ, thay mọi dấu phẩy bằng dấu chấm hoặc xóa mọi khoảng trắng**: với định dạng đầu vào do bên ngoài định nghĩa, việc “sửa” dữ liệu trước parse có thể biến đầu vào sai thành đầu vào khác mà người gửi không định cung cấp.

## 5. Parse thành công vẫn có thể cho vô cực: overflow

Độ rộng của kiểu số thực là hữu hạn. Với `double`, giá trị hữu hạn lớn nhất xấp xỉ `1.7976931348623157E308`. Chuỗi số có số mũ lớn hơn có thể parse thành vô cực:

```java
double overflow = Double.parseDouble("1e400");
double negativeOverflow = Double.parseDouble("-1e400");

System.out.println(overflow);         // Infinity
System.out.println(negativeOverflow); // -Infinity
```

**Không có `NumberFormatException` trong hai trường hợp trên**, vì chuỗi đúng cú pháp số thực; kết quả toán học đã vượt miền hữu hạn `double`.

`float` có miền hữu hạn nhỏ hơn `double`:

```java
float f = Float.parseFloat("1e40");   // Infinity
double d = Double.parseDouble("1e40"); // 1.0E40 (hữu hạn)
```

Vì vậy chính cùng một chuỗi hợp lệ có thể cho kết quả khác nhau tùy kiểu parse. Nếu dữ liệu phải nằm trong một giới hạn cụ thể, hãy kiểm tra **kiểu đã chọn và giá trị sau parse**, không chỉ cú pháp.

## 6. Giá trị cực nhỏ có thể thành ±0.0: underflow

Ở phía số nhỏ, số thực rất gần 0 có thể làm tròn thành zero nếu độ lớn nhỏ hơn giá trị dương biểu diễn được ở định dạng đích:

```java
double tiny = Double.parseDouble("1e-4000"); // 0.0
double negativeTiny = Double.parseDouble("-1e-4000"); // -0.0
```

`Double.MIN_VALUE` trong Java là **số `double` dương nhỏ nhất lớn hơn 0**, không phải số âm lớn nhất về trị tuyệt đối. Với `float`, miền dưới chuẩn cũng khác; một giá trị còn đủ biểu diễn trong `double` có thể thành zero khi parse trực tiếp sang `float`.

**Quan trọng:** `Double.isFinite(negativeTiny)` vẫn là `true` vì `-0.0` là giá trị hữu hạn. Nếu nghiệp vụ bắt buộc bảo toàn sự khác biệt giữa đầu vào khác 0 và zero, **kiểm tra `isFinite` chưa đủ**. Cần chính sách từ chối underflow, dùng `BigDecimal` với đầu vào thập phân chính xác, hoặc quy định độ lớn đầu vào tối thiểu.

Không nên đồng nhất mọi kết quả zero với chuỗi `"0"`: `"-1e-4000"` khác 0 về toán học, nhưng được làm tròn thành `-0.0` ở kiểu `double`.

## 7. Phát hiện NaN, Infinity và số hữu hạn đúng cách

Các API chuyên dụng:

| API | `NaN` | `+Infinity` | `-Infinity` | Giá trị hữu hạn, kể cả ±0.0 |
| --- | --- | --- | --- | --- |
| `Double.isNaN(x)` | `true` | `false` | `false` | `false` |
| `Double.isInfinite(x)` | `false` | `true` | `true` | `false` |
| `Double.isFinite(x)` | `false` | `false` | `false` | `true` |

`Float.isNaN`, `Float.isInfinite` và `Float.isFinite` có hành vi tương ứng cho `float`.

```java
double x = Double.parseDouble("-Infinity");
System.out.println(Double.isInfinite(x)); // true
System.out.println(Double.isFinite(x));   // false

double y = Double.parseDouble("NaN");
System.out.println(Double.isNaN(y));     // true
System.out.println(Double.isFinite(y));  // false
```

**Đừng kiểm tra NaN bằng `x == Double.NaN`.** Phép so sánh primitive với NaN luôn `false`, kể cả `Double.NaN == Double.NaN`. Cách đúng là `Double.isNaN(x)`.

Việc có thể parse Infinity/NaN không có nghĩa chúng là dữ liệu hợp lệ của mọi hệ thống. Phần lớn hệ thống tính tổng tiền, lượng hàng hoặc tỷ lệ hữu hạn cần từ chối giá trị không hữu hạn. Trong tính toán khoa học, giá trị đặc biệt có thể được chấp nhận có kiểm soát. Hợp đồng dữ liệu phải nói rõ điều này.

## 8. Khi cần đầu vào chỉ gồm số thập phân hữu hạn

Một parser dùng trong nghiệp vụ thường có **hai tầng kiểm tra**:

1. **Kiểm tra ngữ pháp nguồn:** ứng dụng có cho phép `NaN`, `Infinity`, literal hex, hậu tố `f/d`, khoảng trắng, dấu `+`, dạng `.5` hay exponent không?
2. **Kiểm tra giá trị sau parse:** kết quả có hữu hạn không, có thuộc miền cho phép không, có làm tròn thành zero hoặc mất precision không?

Ví dụ helper **chỉ chấp nhận cú pháp thập phân ASCII** với dấu tùy chọn và exponent, sau đó bắt lỗi và từ chối số không hữu hạn:

```java
static double parseFiniteDecimal(String raw) {
    if (raw == null) {
        throw new IllegalArgumentException("Missing number");
    }

    String s = raw.trim();
    if (!s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) {
        throw new IllegalArgumentException("Invalid decimal syntax");
    }

    double value = Double.parseDouble(s);
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Non-finite number");
    }
    return value;
}
```

Đây là **một method minh họa cần đặt bên trong class**, không phải chương trình độc lập. Nó cố ý **không** cho phép `NaN`, `Infinity`, cú pháp hex và hậu tố `f/d`. Chuỗi `"+12.5"` và `"1e3"` được phép; `"1,5"` không được phép.

**Giới hạn của helper:** nó vẫn có thể nhận `"1e-4000"` thành `0.0`. Điều đó đúng nếu hợp đồng chỉ yêu cầu **kết quả hữu hạn**, nhưng **không đủ** nếu phải giữ chính xác mọi đầu vào khác 0. Muốn từ chối underflow, so sánh với dữ liệu thập phân gốc bằng `BigDecimal` hoặc đặt quy tắc miền giá trị rõ ràng.

### Nếu cần giữ chính xác decimal gốc

```java
java.math.BigDecimal exact =
        new java.math.BigDecimal("1e-4000");
// Vẫn chứa số dương khác zero dưới dạng BigDecimal
```

`BigDecimal` không nhận trực tiếp `"NaN"` hoặc `"Infinity"` như một giá trị số decimal thông thường. Nếu domain là tiền hoặc số thập phân cần chính xác, hãy dùng `BigDecimal` tạo từ chuỗi hoặc một mô hình số nguyên đơn vị nhỏ nhất, cùng chính sách precision/scale và làm tròn theo hợp đồng.

## 9. Khác biệt giữa parse và định dạng theo locale

`Double.parseDouble("1.5")` cho 1.5 bất kể locale máy. Nhưng người dùng có thể nhập số theo quy tắc địa phương, chẳng hạn `"1,5"`, dấu phân tách nhóm hoặc ký tự số khác ASCII.

Nếu hợp đồng thật sự cho phép định dạng địa phương, có thể sử dụng `java.text.NumberFormat`/`DecimalFormat` theo `Locale` đã được chỉ rõ. Tuy nhiên, các API đó có quy tắc riêng cho dấu, grouping, ký tự đặc biệt, các giá trị NaN/Infinity và có thể **parse một phần chuỗi rồi dừng**. Ứng dụng nên dùng `ParsePosition` hoặc cơ chế tương đương để xác nhận **toàn bộ đầu vào đã được tiêu thụ**, thay vì xem parse trả một `Number` là đủ.

Ví dụ minh họa:

```java
java.text.NumberFormat parser =
        java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMANY);
java.text.ParsePosition pos = new java.text.ParsePosition(0);
String input = "1,5";
Number result = parser.parse(input, pos);

boolean complete = result != null && pos.getIndex() == input.length();
// complete == true; result.doubleValue() == 1.5
```

Đây là **một đoạn method minh họa**, không phải chương trình độc lập. Dữ liệu tài chính vẫn cần chọn bộ parse decimal, grouping, scale và chính sách thập phân phù hợp; `NumberFormat` tự thân không đảm bảo mọi con số đều chính xác về nghiệp vụ.

## 10. Tránh để giá trị đặc biệt lan truyền âm thầm

Nếu chấp nhận NaN/Infinity ngoài ý muốn, các phép toán sau có thể tạo thêm kết quả không hữu hạn:

```java
double rate = Double.parseDouble("Infinity");
double cost = rate * 10.0; // Infinity

double undefined = Double.parseDouble("NaN");
double total = undefined + 100.0; // NaN
```

Các phép so sánh với NaN cũng dễ gây sai logic:

```java
double x = Double.NaN;
System.out.println(x > 0.0);  // false
System.out.println(x <= 0.0); // false
System.out.println(x == x);   // false
```

Nếu rule chỉ kiểm tra `x <= limit` để từ chối giá trị lớn, NaN có thể **đi qua kiểm tra** vì cả phép so sánh nhỏ hơn/lớn hơn thông thường với NaN đều false. Hãy kiểm tra `Double.isFinite(x)` trước các phép so sánh miền giá trị.

Khi truyền dữ liệu qua JSON hoặc cơ sở dữ liệu, không giả định mọi hệ thống sẽ tiếp nhận trực tiếp chuỗi hoặc giá trị đặc biệt `NaN`/`Infinity`. Giao thức và schema phải định nghĩa rõ cách biểu diễn hoặc từ chối chúng.

### `NaN` và dấu không phải lỗi dấu phẩy

Không nên “lọc sạch mọi ký tự không phải chữ số và dấu chấm” như một cách xác thực. Cách ấy có thể biến `"-Infinity"`, `"-1e-4000"` hoặc `"+1.5"` thành dữ liệu khác mà người dùng không nhập. Hãy áp dụng **ngữ pháp cho phép cụ thể**, sau đó kiểm tra miền giá trị.

## 11. Chương trình Java 17+ độc lập

Tạo file **`FloatingInputDemo.java`**:

```java
public class FloatingInputDemo {
    static double parseFiniteDecimal(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Missing number");
        }
        String s = raw.trim();
        if (!s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid decimal syntax");
        }
        double value = Double.parseDouble(s);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite number");
        }
        return value;
    }

    static String status(String raw) {
        try {
            return Double.toString(parseFiniteDecimal(raw));
        } catch (IllegalArgumentException ex) {
            return "REJECTED";
        }
    }

    public static void main(String[] args) {
        System.out.println("nan=" + Double.parseDouble("NaN"));
        System.out.println("plusNan=" + Double.parseDouble("+NaN"));
        System.out.println("minusNan=" + Double.parseDouble("-NaN"));
        System.out.println("plusInfinity=" +
                Double.parseDouble("+Infinity"));
        System.out.println("minusInfinity=" +
                Double.parseDouble("-Infinity"));

        double plusZero = Double.parseDouble("+0");
        double minusZero = Double.parseDouble("-0");
        System.out.println("zeroEquals=" + (plusZero == minusZero));
        System.out.println("positiveReciprocal=" + (1.0 / plusZero));
        System.out.println("negativeReciprocal=" + (1.0 / minusZero));
        System.out.println("minusZeroBits=" +
                Long.toHexString(Double.doubleToRawLongBits(minusZero)));

        System.out.println("withWhitespace=" +
                Double.parseDouble("  +1.5  "));
        System.out.println("hexFloating=" +
                Double.parseDouble("0x1.8p1"));
        System.out.println("suffixFloat=" +
                Double.parseDouble("1.25f"));

        double large = Double.parseDouble("1e400");
        double tinyNegative = Double.parseDouble("-1e-4000");
        System.out.println("doubleOverflow=" + large);
        System.out.println("doubleUnderflow=" + tinyNegative);
        System.out.println("floatOverflow=" + Float.parseFloat("1e40"));
        System.out.println("doubleFinite=" +
                Double.isFinite(Double.parseDouble("1e40")));
        System.out.println("overflowIsFinite=" + Double.isFinite(large));
        System.out.println("nanDetected=" +
                Double.isNaN(Double.parseDouble("NaN")));
        System.out.println("underflowIsFinite=" +
                Double.isFinite(tinyNegative));

        System.out.println("validDecimal=" + status("+12.5"));
        System.out.println("validExponent=" + status("1e3"));
        System.out.println("rejectNan=" + status("NaN"));
        System.out.println("rejectInfinity=" + status("-Infinity"));
        System.out.println("rejectOverflow=" + status("1e400"));
        System.out.println("rejectComma=" + status("1,5"));
        System.out.println("rejectUnderscore=" + status("1_000.0"));
        System.out.println("rejectHex=" + status("0x1.8p1"));
        System.out.println("underflowAllowed=" + status("1e-4000"));
        System.out.println("nullInput=" + status(null));

        try {
            Double.parseDouble("infinity");
            System.out.println("lowercaseInfinity=ACCEPTED");
        } catch (NumberFormatException ex) {
            System.out.println("lowercaseInfinity=REJECTED");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac FloatingInputDemo.java
java FloatingInputDemo
```

Kết quả chuẩn:

```text
nan=NaN
plusNan=NaN
minusNan=NaN
plusInfinity=Infinity
minusInfinity=-Infinity
zeroEquals=true
positiveReciprocal=Infinity
negativeReciprocal=-Infinity
minusZeroBits=8000000000000000
withWhitespace=1.5
hexFloating=3.0
suffixFloat=1.25
doubleOverflow=Infinity
doubleUnderflow=-0.0
floatOverflow=Infinity
doubleFinite=true
overflowIsFinite=false
nanDetected=true
underflowIsFinite=true
validDecimal=12.5
validExponent=1000.0
rejectNan=REJECTED
rejectInfinity=REJECTED
rejectOverflow=REJECTED
rejectComma=REJECTED
rejectUnderscore=REJECTED
rejectHex=REJECTED
underflowAllowed=0.0
nullInput=REJECTED
lowercaseInfinity=REJECTED
```

Các dòng `doubleOverflow`/`doubleUnderflow` cho thấy **đúng cú pháp không đủ bảo đảm giá trị nguồn được giữ nguyên**. `rejectInfinity` và `rejectOverflow` chứng minh việc áp dụng hợp đồng “chỉ nhận hữu hạn”; dòng `underflowAllowed` là giới hạn có chủ đích của helper — muốn tránh mất số rất nhỏ thì phải có kiểm tra bổ sung.

## 12. Lỗi phổ biến và checklist

| Hiểu nhầm | Giải thích đúng |
| --- | --- |
| “`Double.parseDouble` chỉ nhận số hữu hạn.” | Nhận `NaN`, `±Infinity` và các giá trị bị overflow thành Infinity. |
| “`NaN` phải viết không có dấu.” | `+NaN` và `-NaN` có thể được nhận nhưng kết quả vẫn là NaN, không có thứ tự số học. |
| “`infinity` hoặc `Inf` giống `Infinity`.” | Không; parser phân biệt chính xác cú pháp được định nghĩa. |
| “`-0` parse thành zero dương.” | Với parser số thực, có thể giữ âm zero, phân biệt ở bit và phép chia. |
| “Chỉ cần bắt `NumberFormatException` là có số hợp lệ.” | Chuỗi `1e400` đúng cú pháp vẫn tạo Infinity. |
| “Kiểm tra `Double.isFinite` bảo đảm không mất dữ liệu.” | Underflow có thể tạo zero hữu hạn; precision cũng có thể làm tròn. |
| “`Double.isNaN(x)` và `x == Double.NaN` tương đương.” | Không; phép `==` với NaN luôn false. |
| “`parseDouble` tự đọc số dấu phẩy theo locale.” | Không; parser dùng dấu chấm và ngữ pháp độc lập locale. |
| “Literal Java `1_000.0` và chuỗi `"1_000.0"` đều parse được.” | Literal trong mã nguồn cho phép underscore; parser chuỗi không. |
| “Nhìn `-Infinity` có thể suy ra lỗi xuất phát từ chia cho 0.” | Không; overflow và các phép tính khác cũng có thể tạo vô cực. |
| “Có thể tự xóa dấu và ký tự không phải số rồi parse.” | Có thể âm thầm làm sai nghĩa đầu vào; kiểm tra bằng ngữ pháp đúng và báo lỗi rõ. |

Checklist khi nhận dữ liệu số thực từ bên ngoài:

1. **Xác định định dạng hợp đồng**: cho phép dấu `+/-`, exponent, whitespace, số hex, hậu tố literal, grouping/locale, hay các giá trị đặc biệt?
2. **Chọn kiểu số thích hợp**: `float`, `double` hoặc `BigDecimal` nếu cần decimal chính xác.
3. **Kiểm tra `null`, cú pháp và khoảng cho phép** thay vì giả định parser xử lý toàn bộ.
4. **Nếu chỉ chấp nhận hữu hạn**, dùng `Double.isFinite`/`Float.isFinite` sau parse.
5. **Nếu phải giữ đầu vào khác 0**, phát hiện trường hợp underflow trở thành zero, ví dụ bằng kiểm tra miền hoặc đối chiếu decimal gốc.
6. **Nếu dấu zero có ý nghĩa**, đừng chuẩn hóa âm zero không chủ đích; nếu không có ý nghĩa, chuẩn hóa nhất quán và ghi rõ.
7. **Đừng để NaN vượt qua so sánh miền giá trị**: kiểm tra tính hữu hạn trước, vì các phép `<`, `>`, `<=`, `>=` với NaN đều false.
8. **Khi định dạng lại hoặc truyền sang hệ khác**, kiểm tra định nghĩa của giao thức đối với NaN/Infinity; không mặc định mọi JSON schema hay cơ sở dữ liệu đều cho phép.

**Ghi nhớ:** `Double.parseDouble` và `Float.parseFloat` phân tích **cú pháp dấu phẩy động**, không kiểm tra đầy đủ **hợp lệ nghiệp vụ**. `NaN`, `±Infinity`, `±0.0` và sự làm tròn do overflow/underflow là những kết quả cần xử lý có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-004">Đầu vào hợp lệ có thể làm tròn, overflow hoặc underflow</a>

<details>
<summary>Xem chi tiết</summary>

# Đầu vào số thực hợp lệ vẫn có thể bị làm tròn, overflow hoặc underflow trong Java

## 1. Cú pháp hợp lệ không có nghĩa giá trị được giữ chính xác

`Float.parseFloat(String)` và `Double.parseDouble(String)` chuyển một chuỗi ký tự hợp lệ thành giá trị dấu phẩy động `float` hoặc `double`. Hai API này kiểm tra **cú pháp chuỗi**, rồi chuyển giá trị toán học được mô tả bởi chuỗi sang tập hữu hạn các số dấu phẩy động mà Java biểu diễn được.

Có ba tình huống rất khác nhau:

1. **Làm tròn (rounding):** chuỗi mô tả một số hợp lệ nhưng không nằm chính xác trên lưới giá trị `float`/`double`; parser trả về một giá trị đại diện gần nó.
2. **Overflow:** độ lớn của số hữu hạn mà chuỗi mô tả quá lớn; kết quả có thể là `+Infinity` hoặc `-Infinity`.
3. **Underflow:** độ lớn quá nhỏ so với độ phân giải gần 0; kết quả có thể là **số dưới chuẩn** (*subnormal*) với độ chính xác giảm hoặc bị làm tròn hẳn thành `+0.0`/`-0.0`.

Cả ba đều có thể xảy ra **dù chuỗi hoàn toàn hợp lệ** và không có `NumberFormatException`.

```java
double a = Double.parseDouble("0.1");       // Hữu hạn, nhưng không chính xác tuyệt đối 0.1
double b = Double.parseDouble("1e400");     // Infinity
double c = Double.parseDouble("1e-4000");   // 0.0
float d = Float.parseFloat("3.5e38");       // Infinity
float e = Float.parseFloat("1e-50");        // 0.0
```

Đây là đặc tính của phép chuyển sang số dấu phẩy động có độ rộng cố định, không mặc nhiên là lỗi parser. Ứng dụng cần tự quyết định có chấp nhận kết quả làm tròn, số dưới chuẩn, số 0 do underflow hoặc vô cực hay không.

### Ba câu hỏi phải tách biệt

| Câu hỏi | Ví dụ | Cách xác minh |
| --- | --- | --- |
| Chuỗi có cú pháp được parser hỗ trợ không? | `"1e400"` | `parseDouble` có trả giá trị hay ném `NumberFormatException`? |
| Giá trị parse được có hữu hạn không? | `"1e400"` → `Infinity` | `Double.isFinite(result)` |
| Giá trị gốc được biểu diễn chính xác không? | `"0.1"` → một `double` gần 0.1 | So giá trị toán học nguồn với giá trị nhị phân chính xác theo cách không làm tròn nguồn thêm lần nữa |

Không kết luận “parse thành công” đồng nghĩa “nằm trong miền hữu hạn” hoặc “không mất chữ số”.

## 2. Nền tảng IEEE 754: một chuỗi có thể mô tả nhiều số hơn kiểu đích

Java `float` là binary32, có 32 bit tổng cộng với **24 bit độ chính xác phần trị hữu hiệu**. Java `double` là binary64, có 64 bit tổng cộng với **53 bit độ chính xác phần trị hữu hiệu**.

Mỗi kiểu có các giá trị hữu hạn, số chuẩn hóa (*normal*), số dưới chuẩn (*subnormal*), số 0 có dấu, vô cực và `NaN`. Khoảng cách giữa hai số biểu diễn liền kề thay đổi theo độ lớn.

| Thuộc tính | `float` | `double` |
| --- | --- | --- |
| Độ chính xác phần trị | 24 bit | 53 bit |
| `MAX_VALUE` (hữu hạn dương lớn nhất) | Khoảng `3.4028235E38` | Khoảng `1.7976931348623157E308` |
| `MIN_NORMAL` (dương chuẩn hóa nhỏ nhất) | Khoảng `1.17549435E-38` | Khoảng `2.2250738585072014E-308` |
| `MIN_VALUE` (dương khác 0 nhỏ nhất, dưới chuẩn) | Khoảng `1.4E-45` | Khoảng `4.9E-324` |
| Ký hiệu vô cực dương | `Float.POSITIVE_INFINITY` | `Double.POSITIVE_INFINITY` |
| Kiểm tra hữu hạn | `Float.isFinite(x)` | `Double.isFinite(x)` |

**Đặc biệt:** `Float.MIN_VALUE` và `Double.MIN_VALUE` **không phải số âm nhỏ nhất**. Chúng là các số **dương khác 0 nhỏ nhất** mà kiểu tương ứng biểu diễn được. Cận âm hữu hạn về thứ tự số học là `-Float.MAX_VALUE` hoặc `-Double.MAX_VALUE`.

Đừng hiểu `MIN_NORMAL` là ngưỡng mà mọi giá trị nhỏ hơn đều thành 0. **Số dưới chuẩn** cho phép biểu diễn thêm các giá trị dương rất nhỏ nằm giữa 0 và `MIN_NORMAL`, nhưng độ chính xác tương đối thường kém hơn các số chuẩn hóa.

### Miền biểu diễn và độ chính xác là hai giới hạn khác nhau

Một chuỗi `"9007199254740993"` nằm rất xa cận overflow của `double`. Nhưng số nguyên đó (`2^53 + 1`) **không thể** được biểu diễn chính xác bằng `double`: nó sẽ làm tròn thành `9007199254740992`.

Trong khi đó chuỗi `"1e400"` biểu diễn số hữu hạn theo toán học nhưng lớn hơn `Double.MAX_VALUE` nhiều lần. Parser trả về `Infinity`.

Do đó, một giá trị có thể:

- Hữu hạn và chính xác, như `0.5`.
- Hữu hạn nhưng bị làm tròn, như `0.1` hoặc `2^53 + 1`.
- Hữu hạn, rất nhỏ, dưới chuẩn và có thể bị làm tròn.
- Nhỏ đến mức thành `0.0`.
- Lớn đến mức thành vô cực.

## 3. Chuỗi hợp lệ nhưng bị làm tròn

### 3.1. Giá trị thập phân `0.1`

Số thập phân `0.1` không có biểu diễn nhị phân hữu hạn. Parser chọn một số `double` gần nó:

```java
double x = Double.parseDouble("0.1");

System.out.println(x);                         // 0.1
System.out.println(new java.math.BigDecimal(x));
// 0.1000000000000000055511151231257827021181583404541015625
```

`Double.toString` chọn dạng thập phân ngắn có thể parse ngược về cùng giá trị `double`, nên việc in `0.1` **không chứng minh** lưu chính xác `1/10`.

Ở đây `new BigDecimal(x)` là **thủ thuật quan sát chính xác giá trị nhị phân của `double` đã có**, không phải cách khuyên dùng để khởi tạo số tiền decimal. Nếu muốn giữ đúng số thập phân gốc, dùng `new BigDecimal("0.1")`.

Với `float`:

```java
float x = Float.parseFloat("0.1");
System.out.println((double) x); // 0.10000000149011612
```

Ép `float` đã parse sang `double` giúp thấy rõ giá trị `float` gần đúng (vì mọi `float` hữu hạn đều có thể được biểu diễn chính xác bằng `double`); việc ép kiểu không phục hồi số `0.1` toán học.

### 3.2. Số nguyên lớn vẫn có thể làm tròn

```java
double x = Double.parseDouble("9007199254740993");
double y = Double.parseDouble("9007199254740992");

System.out.println(x);      // 9.007199254740992E15
System.out.println(x == y); // true
```

Chuỗi đầu hợp lệ nhưng giá trị nguyên `2^53 + 1` không có biểu diễn `double` đúng. Hai chuỗi mô tả **hai số nguyên khác nhau**, nhưng sau khi parse thì cùng một giá trị.

Nếu đây là ID, timestamp nguyên với độ phân giải nhỏ, số lượng hoặc khóa cần phân biệt từng đơn vị, không nên lưu chúng trong `double`. Dùng `Long.parseLong` khi vừa miền `long`, hoặc `BigInteger` khi cần miền rộng hơn.

### 3.3. Parser làm tròn theo quy tắc nào?

Java chuyển số thập phân và số dấu phẩy động hexa hợp lệ sang giá trị có thể đại diện theo kiểu đích, làm tròn tới giá trị gần nhất. Nếu giá trị đúng nằm chính giữa hai giá trị đại diện được, chọn giá trị có phần trị chẵn ở bit cuối (*round to nearest, ties to even*).

Ví dụ hexa nhị phân biểu diễn chính xác giá trị nằm đúng **nửa ULP** trên `1.0`:

```java
double halfway = Double.parseDouble("0x1.00000000000008p0");
System.out.println(halfway); // 1.0
```

Ở đây chuỗi hexa mô tả `1 + 2^-53`, chính giữa `1.0` và `Math.nextUp(1.0)`; phía `1.0` có bit cuối phần trị chẵn nên được chọn.

Điều này khác `Math.round`, vốn có quy tắc riêng và trả kiểu nguyên. Cũng khác `BigDecimal.setScale`, nơi người dùng chủ động chọn một `RoundingMode`.

**Chú ý:** `Float.parseFloat` trực tiếp nhắm đến độ chính xác `float`. Không nên mặc định quy trình `Double.parseDouble(text)` rồi ép sang `float` tương đương về mọi trường hợp sát biên làm tròn, vì **làm tròn hai lần** có thể khác làm tròn trực tiếp một lần.

## 4. Chuỗi hợp lệ nhưng overflow thành vô cực

Overflow xảy ra khi giá trị có độ lớn vượt khả năng đại diện hữu hạn của kiểu đích. Parser vẫn có thể trả về một giá trị IEEE 754 hợp lệ: `+Infinity` hoặc `-Infinity`.

```java
double positive = Double.parseDouble("1e400");
double negative = Double.parseDouble("-1e400");
float tooBigForFloat = Float.parseFloat("3.5e38");

System.out.println(positive);       // Infinity
System.out.println(negative);       // -Infinity
System.out.println(tooBigForFloat); // Infinity
```

Không có `NumberFormatException` ở ba chuỗi trên. Chúng **có cú pháp hợp lệ** và biểu diễn các số hữu hạn thập phân rất lớn, nhưng không biểu diễn được dưới dạng số hữu hạn của kiểu được chọn.

```java
System.out.println(Double.isInfinite(positive)); // true
System.out.println(Double.isFinite(positive));   // false
System.out.println(Double.isNaN(positive));      // false
```

### `Infinity` viết trong chuỗi cũng là đầu vào hợp lệ

Parser hỗ trợ token `"Infinity"`, `"-Infinity"`, `"NaN"`:

```java
System.out.println(Double.parseDouble("Infinity")); // Infinity
System.out.println(Double.isNaN(Double.parseDouble("NaN"))); // true
```

Vì vậy khi kết quả là vô cực, chỉ dựa vào `Double.isInfinite(result)` **không thể biết**:

1. Người dùng đã gửi token `"Infinity"`, hay
2. Người dùng đã gửi một số hữu hạn như `"1e400"` nhưng số đó overflow.

Nếu hợp đồng cần phân biệt nguyên nhân, phải xét **chuỗi nguồn và cú pháp/giá trị nguồn trước khi mất thông tin**, không chỉ xét giá trị `double` trả về.

Ứng dụng có thể từ chối mọi giá trị không hữu hạn; nhưng đây là **kiểm tra miền nghiệp vụ sau parse**, không phải lỗi cú pháp mà Java tự phát hiện giúp.

### Overflow không chỉ xuất hiện khi parse

Ngay cả hai số `double` hữu hạn khi thực hiện phép nhân/cộng vẫn có thể tạo `Infinity`; vì vậy kiểm tra `isFinite` tại đầu vào chỉ xác nhận đầu vào, **không chứng minh mọi phép toán sau đó hữu hạn**. Nếu thuật toán yêu cầu miền hữu hạn, hãy xác minh tại các ranh giới phù hợp.

## 5. Chuỗi hợp lệ nhưng underflow thành dưới chuẩn hoặc số 0

Gần số 0, độ phân giải của `double` có giới hạn. Số **dương khác 0 nhỏ nhất** là `Double.MIN_VALUE`, khoảng `4.9406564584124654 × 10^-324`. Với `float`, tương ứng khoảng `1.401298464324817 × 10^-45`.

Khi một số dương rất nhỏ hơn mức biểu diễn tối thiểu, việc làm tròn có thể tạo ra `0.0`. Nếu số âm cực nhỏ, kết quả có thể là `-0.0` — số 0 âm vẫn có **bit dấu**.

```java
double tiny = Double.parseDouble("1e-4000");
double tinyNegative = Double.parseDouble("-1e-4000");

System.out.println(tiny);           // 0.0
System.out.println(tinyNegative);   // -0.0
System.out.println(tinyNegative == 0.0); // true
```

Cả `"1e-4000"` và `"-1e-4000"` đều là chuỗi hợp lệ, đều mô tả số toán học **khác 0**, nhưng sau khi parse mất độ lớn. Không có exception.

Kiểm tra dấu của số 0:

```java
long bits = Double.doubleToRawLongBits(tinyNegative);
System.out.println(Long.toHexString(bits)); // 8000000000000000
```

Dù `-0.0 == +0.0` là `true`, dấu có thể ảnh hưởng phép toán như `1.0 / -0.0` (cho `-Infinity`) và hành vi `Double.compare`, `Double.equals`.

### Vùng dưới chuẩn (subnormal)

Không phải số nào nhỏ hơn `Double.MIN_NORMAL` đều bị làm tròn về 0:

```java
double subnormal = Double.parseDouble("1e-320");
System.out.println(subnormal); // 1.0E-320
```

Đây là giá trị `double` dương, khác 0, nhưng **nhỏ hơn `Double.MIN_NORMAL`**. Nó nằm trong vùng dưới chuẩn. Tại vùng này, bước nhảy tuyệt đối giữa hai giá trị lân cận vẫn rất nhỏ, nhưng độ chính xác tương đối giảm khi độ lớn tiến về 0.

Với `float`:

```java
System.out.println(Float.parseFloat("1e-44")); // 9.8E-45
System.out.println(Float.parseFloat("1e-50")); // 0.0
```

Chuỗi `"1e-44"` tạo số dưới chuẩn khác 0 sau làm tròn, còn `"1e-50"` quá nhỏ và thành 0. Theo cách dùng IEEE 754 chặt chẽ, tín hiệu *underflow* gắn với độ nhỏ và việc mất độ chính xác; trong lập trình ứng dụng, “underflow” thường được dùng rộng hơn để nói kết quả đi vào vùng dưới chuẩn hoặc làm tròn về 0. Khi viết tài liệu kiểm thử, nên tách rõ **SUBNORMAL** và **ROUNDED_TO_ZERO** thay vì coi chúng hoàn toàn giống nhau.

### Tại sao `result == 0.0` chưa đủ để phát hiện underflow?

Hai chuỗi:

```java
Double.parseDouble("0.0");
Double.parseDouble("1e-4000");
```

đều trả `0.0`, nhưng **nguồn gốc khác nhau**. Từ giá trị `double` cuối cùng, không thể suy ra chắc chắn đầu vào gốc là 0 hay một số khác 0 đã underflow. Muốn phát hiện phải giữ nguyên chuỗi ban đầu và phân tích giá trị gốc theo cách đủ miền/độ chính xác.

## 6. `NumberFormatException` khác overflow/underflow như thế nào?

Các parser nhận chuỗi theo cú pháp mà Java hỗ trợ. Nếu chuỗi **không có dạng hợp lệ**, parser thường ném `NumberFormatException`. Ví dụ:

```java
Double.parseDouble("not-a-number"); // NumberFormatException
Double.parseDouble("1_000.0");      // NumberFormatException
```

Dấu gạch dưới trong literal Java như `1_000.0` là cú pháp **trong mã nguồn**, không tự trở thành cú pháp chuỗi được `Double.parseDouble` chấp nhận.

Một số chuỗi hợp lệ có thể bất ngờ đối với người mới:

| Chuỗi | `Double.parseDouble` | Vì sao |
| --- | --- | --- |
| `"0.5"` | `0.5` | Phân số nhị phân chính xác |
| `"0.1"` | In ra `0.1`, nhưng làm tròn nhị phân | Hữu hạn, không biểu diễn chính xác số decimal nguồn |
| `"9007199254740993"` | `9.007199254740992E15` | Giá trị vượt miền số nguyên liên tiếp chính xác của `double` |
| `"1e400"` | `Infinity` | Overflow |
| `"-1e400"` | `-Infinity` | Overflow |
| `"1e-4000"` | `0.0` | Làm tròn xuống 0 do quá nhỏ |
| `"1e-320"` | `1.0E-320` | Giá trị subnormal hữu hạn, khác 0 |
| `"Infinity"` | `Infinity` | Token được parser hỗ trợ |
| `"NaN"` | `NaN` | Token được parser hỗ trợ |
| `"0x1.8p1"` | `3.0` | Cú pháp dấu phẩy động hexa |
| `"1_000.0"` | `NumberFormatException` | Dấu `_` không hợp lệ trong chuỗi parser |
| `"1,5"` | `NumberFormatException` | Không phải cú pháp decimal của `parseDouble` |

`Double.parseDouble(" 1.25 ")` được chấp nhận sau khi loại các ký tự whitespace theo quy tắc của parser, nhưng **không nên suy ra mọi định dạng locale đều được chấp nhận**. Đầu vào dùng dấu phẩy làm dấu thập phân hoặc dấu nhóm hàng nghìn cần một parser theo locale được cấu hình cụ thể. Với đầu vào `null`, API có thể ném `NullPointerException`, không phải cách báo overflow.

Cũng cần phân biệt `Integer.parseInt("99999999999999999999")` với `Double.parseDouble` các số rất lớn: parser nguyên có thể ném `NumberFormatException` do vượt miền `int`, trong khi parser dấu phẩy động có thể trả `Infinity` khi giá trị hữu hạn thập phân quá lớn. Không áp dụng quy tắc báo lỗi của parser nguyên cho parser số thực.

## 7. Demo Java 17 đầy đủ: bốn kết quả từ chuỗi hợp lệ

Tạo tệp `FloatingParseDemo.java`:

```java
import java.math.BigDecimal;

public class FloatingParseDemo {
    public static void main(String[] args) {
        double preciseHalf = Double.parseDouble("0.5");
        double roundedDecimal = Double.parseDouble("0.1");

        System.out.println("half = " + preciseHalf);
        System.out.println("decimal printed = " + roundedDecimal);
        System.out.println("decimal exact binary value = "
                + new BigDecimal(roundedDecimal));

        double roundedInteger = Double.parseDouble("9007199254740993");
        double adjacentInteger = Double.parseDouble("9007199254740992");
        System.out.println("large integer = " + roundedInteger);
        System.out.println("two parsed integers equal = "
                + (roundedInteger == adjacentInteger));

        System.out.println("double overflow = "
                + Double.parseDouble("1e400"));
        System.out.println("negative overflow = "
                + Double.parseDouble("-1e400"));
        System.out.println("float overflow = "
                + Float.parseFloat("3.5e38"));

        double tiny = Double.parseDouble("1e-4000");
        double negativeTiny = Double.parseDouble("-1e-4000");
        System.out.println("double underflow = " + tiny);
        System.out.println("negative underflow bits = "
                + Long.toHexString(
                        Double.doubleToRawLongBits(negativeTiny)));
        System.out.println("double subnormal = "
                + Double.parseDouble("1e-320"));
        System.out.println("float underflow = "
                + Float.parseFloat("1e-50"));
        System.out.println("float subnormal = "
                + Float.parseFloat("1e-44"));

        System.out.println("hex floating point = "
                + Double.parseDouble("0x1.8p1"));
        System.out.println("explicit infinity = "
                + Double.isInfinite(Double.parseDouble("Infinity")));
        System.out.println("explicit NaN = "
                + Double.isNaN(Double.parseDouble("NaN")));

        try {
            Double.parseDouble("1_000.0");
            System.out.println("unexpected success");
        } catch (NumberFormatException ex) {
            System.out.println("invalid syntax: NumberFormatException");
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac FloatingParseDemo.java
java FloatingParseDemo
```

Đầu ra:

```text
half = 0.5
decimal printed = 0.1
decimal exact binary value = 0.1000000000000000055511151231257827021181583404541015625
large integer = 9.007199254740992E15
two parsed integers equal = true
double overflow = Infinity
negative overflow = -Infinity
float overflow = Infinity
double underflow = 0.0
negative underflow bits = 8000000000000000
double subnormal = 1.0E-320
float underflow = 0.0
float subnormal = 9.8E-45
hex floating point = 3.0
explicit infinity = true
explicit NaN = true
invalid syntax: NumberFormatException
```

Kết quả trên làm rõ bốn trạng thái: giá trị có thể biểu diễn đúng (`0.5`), bị làm tròn (`0.1` hoặc số nguyên lớn), overflow thành vô cực (`1e400`), underflow thành 0 hoặc số dưới chuẩn. Chỉ trường hợp cuối của ví dụ — chuỗi chứa dấu `_` — làm parser báo lỗi cú pháp.

## 8. Kiểm tra sau parse: `isFinite`, `isInfinite`, `isNaN` và phân loại subnormal

Nếu hợp đồng dữ liệu chỉ chấp nhận **số hữu hạn**, hãy kiểm tra *sau khi* parse:

```java
static double parseFiniteDouble(String text) {
    double result = Double.parseDouble(text);

    if (!Double.isFinite(result)) {
        throw new IllegalArgumentException(
                "Expected a finite number: " + text);
    }

    return result;
}
```

Đây là đoạn phương thức độc lập có thể đặt vào một class Java. Nó loại cả `NaN` lẫn vô cực, bất kể chúng đến từ token tường minh hay overflow. Nhưng nó **không** phát hiện mất chính xác do làm tròn hoặc số khác 0 bị làm tròn thành `0.0`, vì 0 và subnormal vẫn là giá trị hữu hạn.

Đối với các giá trị hữu hạn dương/âm khác 0:

```java
static boolean isSubnormal(double x) {
    return Double.isFinite(x)
            && x != 0.0
            && Math.abs(x) < Double.MIN_NORMAL;
}
```

Phương thức trên kiểm tra một giá trị kết quả **có nằm trong vùng subnormal không**, không chứng minh chuỗi nguồn bị làm tròn hay không. Một số subnormal được mô tả đúng bằng dạng nhị phân hoặc decimal phù hợp vẫn có thể được biểu diễn chính xác.

Nếu miền ứng dụng không cho phép kết quả subnormal do hạn chế độ chính xác, từ chối một cách tường minh; đừng tự hiểu `Double.isFinite` là đã bao hàm yêu cầu này.

### Cẩn thận với số 0 âm

`-0.0` là hữu hạn và `-0.0 == 0.0` là `true`. Khi dấu cần được phân biệt:

```java
double x = Double.parseDouble("-1e-4000");
boolean negativeZero =
        Double.doubleToRawLongBits(x)
        == Double.doubleToRawLongBits(-0.0);
```

Có hai trường hợp cho cùng `-0.0`: chuỗi thực sự mô tả âm không (`"-0.0"`) hoặc một số âm khác 0 đã underflow. Đọc bit dấu chỉ cho thấy **trạng thái cuối**, không khôi phục được nguyên nhân.

## 9. Muốn biết có làm tròn hay không phải giữ giá trị nguồn

Sau khi `Double.parseDouble("0.1")` đã trả về `double`, kết quả không còn mang đầy đủ thông tin về chuỗi gốc. `Double.toString(result)` in biểu diễn ngắn để round-trip thành cùng giá trị, **không** cho biết decimal nguồn được giữ tuyệt đối.

Với một đầu vào **thuần thập phân** nằm trong miền mà `BigDecimal(String)` có thể phân tích, có thể so sánh chính xác giá trị thập phân gốc với **giá trị nhị phân chính xác** đã parse:

```java
import java.math.BigDecimal;

BigDecimal source = new BigDecimal("0.1");
double parsed = Double.parseDouble("0.1");

// Constructor BigDecimal(double) giữ đúng giá trị nhị phân đã có.
// Nó được dùng ở đây để chẩn đoán, không phải để khởi tạo tiền tệ.
BigDecimal storedExactly = new BigDecimal(parsed);

boolean unchanged = source.compareTo(storedExactly) == 0;
// unchanged là false: decimal 0.1 khác giá trị nhị phân của parsed.
```

Với `"0.5"`, hai giá trị bằng nhau vì `1/2` có biểu diễn nhị phân hữu hạn. Với `"9007199254740993"`, kết quả khác nhau vì đã làm tròn số nguyên. Phải đảm bảo `parsed` hữu hạn trước khi gọi `new BigDecimal(parsed)` vì constructor đó không chấp nhận vô cực hoặc `NaN`.

**Lưu ý đặc biệt:** `BigDecimal.valueOf(parsed)` không tương đương `new BigDecimal(parsed)` trong bài toán chẩn đoán trên. `valueOf(double)` dùng biểu diễn decimal từ `Double.toString`, phù hợp nhiều nhu cầu thông thường nhưng có thể in lại `0.1` và **che đi khác biệt** giữa số nhị phân và decimal chính xác. Dùng `new BigDecimal(double)` **chỉ khi cần thấy đúng toàn bộ giá trị nhị phân**; với số tiền hoặc đầu vào decimal chính xác, ưu tiên `new BigDecimal(String)`.

### Phân loại đơn giản với decimal input

Nếu hợp đồng đầu vào chỉ chấp nhận số decimal hữu hạn viết thành chuỗi, thuật toán chẩn đoán có thể làm như sau:

1. Phân tích giá trị nguồn `new BigDecimal(text)` để giữ giá trị decimal chính xác.
2. Phân tích sang `double` bằng `Double.parseDouble(text)`.
3. Nếu kết quả vô cực → **overflow** (vì đã loại token `"Infinity"`/`"NaN"` trong bước 1).
4. Nếu nguồn khác 0 mà kết quả là `0.0` hoặc `-0.0` → **underflow to zero**.
5. Nếu kết quả khác 0 và có độ lớn nhỏ hơn `Double.MIN_NORMAL` → giá trị đầu ra là **subnormal**; có thể so sánh tiếp để biết nó chính xác hay bị làm tròn.
6. Nếu kết quả hữu hạn, dùng `source.compareTo(new BigDecimal(parsed))` để kiểm tra **có làm tròn** so với giá trị decimal nguồn hay không.

Ví dụ áp dụng:

| Chuỗi decimal | Giá trị đích | Kết luận |
| --- | --- | --- |
| `"0.5"` | `0.5` | Chính xác |
| `"0.1"` | Giá trị gần `0.1` | Hữu hạn nhưng làm tròn |
| `"9007199254740993"` | `9007199254740992` | Làm tròn giá trị nguyên |
| `"1e400"` | `Infinity` | Overflow |
| `"1e-4000"` | `0.0` | Underflow thành 0 |
| `"1e-320"` | Subnormal gần `1e-320` | Dưới chuẩn; cần xét thêm sai số nếu muốn biết có chính xác không |

Cách này là **chẩn đoán cho nguồn decimal**, không phải parser tổng quát cho chuỗi hexa, token `"Infinity"`/`"NaN"` hoặc mọi đầu vào cực dài: `BigDecimal(String)` có miền cú pháp và giới hạn exponent/scale riêng, và có thể tiêu tốn nhiều tài nguyên khi chuỗi quá lớn. Hãy đặt giới hạn kích thước đầu vào thích hợp.

## 10. Chọn kiểu số và chính sách xử lý theo bài toán

| Nhu cầu | Khuyến nghị |
| --- | --- |
| Chỉ chấp nhận chuỗi có cú pháp số thực Java | `Float.parseFloat` / `Double.parseDouble`, bắt `NumberFormatException` |
| Cần mọi kết quả **hữu hạn** | Sau parse, dùng `Float.isFinite` / `Double.isFinite` |
| Cần cấm `NaN` và vô cực rõ ràng | Dùng `isNaN`/`isInfinite` hoặc `isFinite`; thông báo lỗi nghiệp vụ cụ thể |
| Cần phát hiện số khác 0 bị parse thành 0 | Giữ và phân tích **nguồn trước làm tròn**, ví dụ `BigDecimal(String)` cho decimal |
| Cần phát hiện/chặn vùng dưới chuẩn | Kiểm tra giá trị hữu hạn, khác 0, và `abs(x) < MIN_NORMAL` |
| Cần decimal chính xác như số tiền | `BigDecimal(String)` với quy tắc làm tròn/tính toán thích hợp |
| Cần lưu nguyên lớn và phân biệt từng đơn vị | `long` / `BigInteger`, không chuyển thành `double` làm khóa |
| Cần giao tiếp JSON/API chặt | Xác định rõ cú pháp số được phép, có cho phép NaN/Infinity không, ngưỡng giá trị, cách báo overflow/underflow |
| Cần tính toán khoa học nhị phân | Dùng `double`/`float` nếu đáp ứng độ chính xác, kiểm tra lỗi số học và miền kết quả |

Một quy trình nhập dữ liệu đáng tin cậy nên có **ba lớp kiểm tra**:

**(1) Syntax:** chuỗi có đúng grammar mà ứng dụng hỗ trợ không? Điều này có thể khác grammar rộng của Java, vì nhiều giao thức không cho phép `"NaN"`, `"Infinity"` hoặc dạng số hexa.

**(2) Range and finiteness:** kết quả có nằm trong miền nghiệp vụ và hữu hạn không? Kiểm tra sau parse và xét cách nhận biết overflow.

**(3) Precision:** sai lệch do làm tròn có chấp nhận được không? Nếu không, giữ dữ liệu gốc và dùng kiểu số thích hợp; không kỳ vọng parser tự báo mọi trường hợp mất độ chính xác.

### Vì sao `try/catch NumberFormatException` chưa đủ?

```java
try {
    double value = Double.parseDouble(text);
    // Phải tiếp tục kiểm tra value theo hợp đồng ứng dụng.
} catch (NumberFormatException ex) {
    // Chỉ xử lý cú pháp không hợp lệ; không đại diện mọi mất độ chính xác.
}
```

Nếu `text = "1e400"`, khối `catch` không chạy dù `value == Infinity`; nếu `text = "1e-4000"`, khối `catch` cũng không chạy dù giá trị nguồn khác 0 bị làm tròn thành 0. Một hệ thống chấp nhận các kết quả đó mà không kiểm tra có thể tạo lỗi khó phát hiện.

## 11. Lỗi phổ biến

1. **Nhầm parse thành công với giữ nguyên giá trị.** Parser có thể làm tròn một số hữu hạn đúng cú pháp.
2. **Nghĩ overflow số thực sẽ ném `NumberFormatException`.** Chuỗi hợp lệ vượt cận có thể trả `Infinity`.
3. **Nghĩ underflow luôn là số 0.** Vùng subnormal vẫn chứa nhiều giá trị khác 0.
4. **Nghĩ `MIN_VALUE` là số âm nhất.** Với `Float`/`Double`, đó là số dương khác 0 nhỏ nhất.
5. **Nhìn `Double.toString(0.1)` rồi kết luận nhị phân lưu đúng 0.1.** Đây là chuỗi ngắn có khả năng round-trip về cùng `double`.
6. **Dùng `BigDecimal.valueOf(double)` để kiểm tra có giữ chính xác decimal nguồn hay không.** Nó không phản ánh toàn bộ số nhị phân chính xác, nên có thể che đi sự khác biệt; mục đích chẩn đoán này cần `new BigDecimal(double)` với đầu vào hữu hạn.
7. **Chuyển nguồn sang `double` rồi mới giữ bằng `BigDecimal`.** Thông tin đã mất không tự hồi phục.
8. **Dùng `result == 0` để kết luận chuỗi gốc biểu diễn số 0.** Có thể là số khác 0 đã underflow.
9. **Dùng `isFinite(result)` để kết luận không làm tròn.** Hữu hạn và chính xác là hai thuộc tính khác nhau.
10. **Dùng số thực làm ID nguyên lớn.** Các giá trị nguyên khác nhau có thể parse thành cùng một `double`.
11. **Cho rằng chuỗi parser chấp nhận mọi literal Java.** Literal mã nguồn có `_` không được `parseDouble` nhận như vậy; cú pháp giao thức/locale còn có thể khác.
12. **Không xét `-0.0`.** Mất độ lớn vẫn có thể giữ dấu, ảnh hưởng một số phép toán và biểu diễn bit.
13. **So sánh chuỗi đầu vào với kết quả `Double.toString` bằng ký tự.** Một giá trị decimal chính xác có thể in ra dưới dạng khác mà không thay đổi số; ngược lại cùng chuỗi ngắn cũng chưa chứng minh số nhị phân bằng giá trị decimal toán học.
14. **Chỉ kiểm tra đầu vào hữu hạn mà bỏ qua phép tính sau đó.** Overflow/underflow cũng có thể phát sinh trong toán học nội bộ.
15. **Áp dụng `BigDecimal` vô điều kiện với chuỗi cực dài.** Cần giới hạn độ dài và kích thước exponent để phòng trường hợp tiêu tốn tài nguyên quá mức.

## 12. Bài tập có đáp án

**Câu 1.** `Double.parseDouble("1e400")` có ném `NumberFormatException` không? Kết quả là gì?

**Đáp án:** Không. Chuỗi có cú pháp hợp lệ, biểu diễn giá trị hữu hạn toán học nhưng quá lớn so với miền `double` hữu hạn, nên parser trả `Infinity`.

**Câu 2.** Có thể phân biệt kết quả của `Double.parseDouble("0")` và `Double.parseDouble("1e-4000")` nếu chỉ giữ hai giá trị `double` trả về không?

**Đáp án:** Không. Cả hai đều có thể bằng `+0.0`. Muốn biết số thứ hai vốn khác 0, phải giữ và phân tích chuỗi nguồn trước khi thông tin mất.

**Câu 3.** Vì sao `Float.parseFloat("0.1")` và `Double.parseDouble("0.1")` không lưu đúng số thực `1/10`?

**Đáp án:** `0.1` không có biểu diễn nhị phân hữu hạn. Cả `float` lẫn `double` phải chọn giá trị gần nó trên lưới biểu diễn. `double` có độ chính xác phần trị cao hơn nên thường gần hơn.

**Câu 4.** Chuỗi `"1e-320"` có parse thành `0.0` trong `double` không?

**Đáp án:** Không với giá trị đã nêu: parser trả một số subnormal khác 0 (in ra `1.0E-320`). Nó nhỏ hơn `Double.MIN_NORMAL` nhưng lớn hơn độ phân giải tối thiểu gần 0.

**Câu 5.** Nếu yêu cầu mọi kết quả parse phải hữu hạn, dùng điều kiện nào?

**Đáp án:** Sau `double x = Double.parseDouble(text)`, dùng `Double.isFinite(x)`. Nếu trả `false`, dữ liệu có `NaN` hoặc vô cực. Nhưng kiểm tra này **không** khẳng định không làm tròn/underflow thành 0.

**Câu 6.** Khi kiểm tra sai lệch decimal giữa chuỗi `"0.1"` và kết quả `double` đã parse, vì sao `new BigDecimal(parsed)` giúp chẩn đoán tốt hơn `BigDecimal.valueOf(parsed)`?

**Đáp án:** `new BigDecimal(parsed)` chuyển **giá trị nhị phân chính xác** của `double` hữu hạn thành decimal, thể hiện phần lệch thực sự. `BigDecimal.valueOf(parsed)` dựa trên dạng thập phân ngắn `Double.toString`, có thể cho ra `"0.1"` và che phần lệch so với giá trị toán học nguồn.

**Câu 7.** Vì sao một chuỗi nguyên có cú pháp hợp lệ lại mất thông tin khi parse bằng `Double.parseDouble`?

**Đáp án:** `double` có 53 bit độ chính xác phần trị; nó không biểu diễn liên tiếp mọi số nguyên vượt `2^53`. Chẳng hạn `"9007199254740993"` làm tròn thành `9007199254740992` dù số đó còn xa ngưỡng overflow.

**Câu 8.** `Double.parseDouble("-1e-4000")` có thể cho `-0.0`. `-0.0 == 0.0` trả gì và có chứng minh không còn dấu âm không?

**Đáp án:** So sánh primitive `==` trả `true`, nhưng bit dấu âm còn tồn tại; `Double.doubleToRawLongBits` cho phép thấy bit dấu. `1.0/-0.0` tạo `-Infinity`.

**Kết luận:** Parser dấu phẩy động trả **một giá trị được biểu diễn bằng `float`/`double`**, chứ không hứa bảo toàn mọi số toán học trong chuỗi. Hãy tách lỗi cú pháp khỏi overflow, underflow và rounding; kiểm tra `isFinite` khi cần, phân biệt số dưới chuẩn với 0, và giữ giá trị nguồn ở biểu diễn phù hợp khi tính chính xác là yêu cầu bắt buộc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-005">Phân biệt parse số theo cú pháp Java với parse theo locale</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt parse số theo cú pháp Java với parse theo locale

Trong Java, **“chuỗi biểu diễn một số” không chỉ có một cú pháp duy nhất**. Chuỗi kỹ thuật như `"1234.56"`, `"1.25e3"` có cách hiểu khác với văn bản số do người dùng nhập theo khu vực, ví dụ `"1,234.56"` ở Hoa Kỳ hay `"1.234,56"` ở Đức.

Hai họ API phổ biến phục vụ **hai hợp đồng đầu vào khác nhau**:

- **Cú pháp độc lập locale, dùng cho dữ liệu máy:** `Double.parseDouble`, `Float.parseFloat`, `new BigDecimal(String)`. Dấu thập phân thông thường là **dấu chấm `.`**, không tự dùng quy ước phân cách của người dùng.
- **Cú pháp theo ngôn ngữ/khu vực (locale):** `NumberFormat` và `DecimalFormat`. Dấu thập phân, dấu nhóm hàng nghìn, ký tự số, thậm chí ký hiệu âm có thể phụ thuộc `Locale` và cấu hình định dạng.

Ví dụ:

```java
double machine = Double.parseDouble("1234.56"); // 1234.56

NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);

double fromUs = us.parse("1,234.56").doubleValue();  // 1234.56
double fromDe = de.parse("1.234,56").doubleValue();  // 1234.56
```

*Các dòng trên là mã minh họa đặt trong một phương thức, có `import java.text.NumberFormat;`, `import java.util.Locale;` và khai báo `throws ParseException` hoặc xử lý exception.*

**Không nên dùng `Double.parseDouble` để đọc trực tiếp chuỗi người dùng nhập theo locale**, cũng không nên sử dụng `NumberFormat` thay cho một parser dữ liệu giao thức cố định khi cần bảo đảm cú pháp hoàn toàn nhất quán.

Bài này thuộc phần **Phân tích số thực** trong module **Java core – numbers**. Ta sẽ đi từ cơ chế, các trường hợp ranh giới, lỗi parse đến cách thiết kế parser an toàn cho Java 17+.

## 1. Locale là gì và liên quan đến số ra sao?

`java.util.Locale` mô tả quy ước ngôn ngữ/khu vực dùng bởi các API có tính quốc tế hóa. Với một số, `Locale` có thể ảnh hưởng đến:

- **Dấu thập phân:** dấu chấm `.` hoặc dấu phẩy `,`.
- **Dấu nhóm chữ số:** dấu phẩy, dấu chấm, khoảng trắng thông thường, khoảng trắng không ngắt hoặc ký tự nhóm riêng.
- **Nhóm số:** quy ước chia nhóm theo 3 chữ số hoặc kiểu nhóm khác.
- **Chữ số:** một số locale dùng chữ số và ký hiệu không phải ASCII.
- **Biểu diễn số âm, phần trăm hoặc tiền tệ:** có thể thay đổi theo loại `NumberFormat`.

Hai ví dụ cơ bản:

| Giá trị toán học | Chuỗi theo `Locale.US` | Chuỗi theo `Locale.GERMANY` |
| ---: | --- | --- |
| Một nghìn hai trăm ba mươi bốn phẩy năm sáu | `1,234.56` | `1.234,56` |
| Một phẩy năm | `1.5` | `1,5` |
| Một nghìn | `1,000` | `1.000` |

**Điều nguy hiểm:** `"1.234"` trong dữ liệu kiểu US có thể được hiểu là một phẩy hai ba bốn, trong khi với quy ước số Đức nó có thể là một nghìn hai trăm ba mươi bốn. **Không thể suy ra ý nghĩa số chỉ từ chuỗi nếu thiếu hợp đồng locale/format**.

### Locale mặc định và locale tường minh

```java
Locale current = Locale.getDefault();
NumberFormat format = NumberFormat.getNumberInstance(current);
```

Locale mặc định có thể thay đổi theo hệ điều hành, cấu hình JVM hoặc do phần khác của chương trình sửa. Nếu một API phải xử lý **dữ liệu có format cố định**, hãy truyền locale tường minh thay vì mặc định môi trường:

```java
NumberFormat parser = NumberFormat.getNumberInstance(Locale.US);
```

**Chọn `Locale.US` không làm parser trở thành “parser cú pháp Java”**: nó vẫn là parser theo locale, với quy tắc grouping và hành vi parse có thể linh hoạt. Đối với giao thức máy quy định `1234.56`, thường nên dùng `Double.parseDouble` hoặc `BigDecimal(String)` và validation riêng.

## 2. Double.parseDouble và Float.parseFloat: cú pháp độc lập locale

`Double.parseDouble(String)` trả `double`; `Float.parseFloat(String)` trả `float`. Cả hai xử lý văn bản theo cú pháp số thực do Java quy định, **không nhìn `Locale.getDefault()` để tự đổi dấu thập phân**.

```java
double a = Double.parseDouble("1234.56"); // 1234.56
double b = Double.parseDouble("1.25e3");  // 1250.0
float c = Float.parseFloat("-2.5");      // -2.5f
```

Các cách viết thường được hỗ trợ:

| Input | Kết quả/ý nghĩa |
| --- | --- |
| `"12.5"` | Số thập phân 12.5 |
| `"-0.5"` | Số âm -0.5 |
| `"+3.0"` | Số dương có dấu + |
| `"1e3"` | 1000.0 |
| `"1.5E-2"` | 0.015 |
| `"0x1.8p1"` | Số thực hệ 16 với mũ nhị phân, bằng 3.0 |
| `"NaN"` | Giá trị đặc biệt NaN |
| `"Infinity"` | Vô cực dương |
| `"-Infinity"` | Vô cực âm |
| `"1.5f"` hoặc `"1.5d"` | Cú pháp có hậu tố kiểu được parser dấu phẩy động Java chấp nhận |

**Không đồng nhất hoàn toàn cú pháp literal trong source với chuỗi runtime:** `Double.parseDouble` chấp nhận một số cách viết giống literal số thực như dạng hexadecimal float hoặc hậu tố `f`/`d`, nhưng **không chấp nhận dấu gạch dưới phân tách chữ số** như Java source.

```java
double sourceLiteral = 1_234.56; // Literal Java có dấu gạch dưới.
// Double.parseDouble("1_234.56"); // NumberFormatException.
```

### Dấu thập phân không phụ thuộc locale

```java
double correct = Double.parseDouble("1.5"); // 1.5.
// Double.parseDouble("1,5"); // NumberFormatException.
```

`Locale.GERMANY` không khiến `Double.parseDouble("1,5")` tự thành 1.5. Muốn parse dữ liệu người dùng theo Đức, dùng `NumberFormat` với locale thích hợp hoặc một cú pháp được ứng dụng định nghĩa rõ.

### Khoảng trắng và suffix: những chi tiết cần nhớ

`Double.parseDouble` và `Float.parseFloat` cho phép một số whitespace ASCII ở đầu/cuối theo quy tắc trim của parser, nhưng không vì thế mà chấp nhận dấu nhóm hay khoảng trắng bất kỳ ở giữa một con số:

```java
double trimmed = Double.parseDouble(" 2.5 "); // 2.5.
// Double.parseDouble("2 5"); // NumberFormatException.
```

Không nên suy ra mọi dạng Unicode whitespace được tự loại bỏ. Nếu cần xử lý input nhập từ người dùng, hãy xác định chính sách `strip`/`trim`, Unicode và validation tường minh.

**Không nhận định rằng số quá lớn luôn ném NumberFormatException:** cú pháp có thể hợp lệ nhưng giá trị vượt miền hữu hạn của `double`.

```java
double overflow = Double.parseDouble("1e309");
System.out.println(Double.isInfinite(overflow)); // true

double underflow = Double.parseDouble("1e-400");
System.out.println(underflow == 0.0); // true
```

Ngược lại, chuỗi sai cú pháp như `"1_000.0"` hay `"abc"` gây `NumberFormatException`. **Tính hợp lệ cú pháp** và **tính hợp lệ về miền nghiệp vụ** là hai câu hỏi khác nhau.

## 3. BigDecimal(String): cú pháp thập phân chính xác, không theo locale

`new BigDecimal(String)` đọc biểu diễn số thập phân theo cú pháp được `BigDecimal` hỗ trợ. Nó cho phép **giữ chính xác giá trị thập phân** của chuỗi đầu vào thay vì buộc giá trị vào nhị phân `double`.

```java
BigDecimal amount = new BigDecimal("1234.56");
System.out.println(amount.toPlainString()); // 1234.56
```

`BigDecimal` thích hợp khi cần tính số tiền, thuế, hoặc các giá trị thập phân mà quy tắc làm tròn và `scale` phải được kiểm soát.

```java
BigDecimal price = new BigDecimal("0.10");
BigDecimal tax = new BigDecimal("0.20");
BigDecimal sum = price.add(tax);
System.out.println(sum.toPlainString()); // 0.30
```

Không nên viết:

```java
BigDecimal wrong = new BigDecimal(0.1);
```

Cách đó lấy **đúng giá trị nhị phân double đã xấp xỉ 0.1**, chứ không dựng lại số thập phân 0.1 mong muốn. Nếu đầu vào là text thập phân gốc, dùng `new BigDecimal(text)`. Nếu nhận `double` hợp lệ và chủ ý muốn chuyển theo biểu diễn thập phân chuẩn hóa, cân nhắc `BigDecimal.valueOf(double)`, nhưng nó không khôi phục dữ liệu đầu vào đã mất thông tin.

### BigDecimal(String) cũng không tự hiểu dấu phân cách locale

```java
new BigDecimal("1234.56"); // Hợp lệ.

// new BigDecimal("1.234,56"); // NumberFormatException.
// new BigDecimal("1,234.56"); // NumberFormatException.
```

Để xử lý giá trị **theo locale mà vẫn giữ số thập phân chính xác**, có thể cấu hình `DecimalFormat.setParseBigDecimal(true)` sau khi xác định đúng `Locale` và cú pháp được chấp nhận. Đây là chủ đề của phần tiếp theo.

### Khác biệt với NaN và Infinity

`BigDecimal` không có NaN hoặc vô cực làm giá trị biểu diễn:

```java
// new BigDecimal("NaN");       // NumberFormatException.
// new BigDecimal("Infinity");  // NumberFormatException.
```

Nếu hệ thống yêu cầu số hữu hạn, đó có thể là đặc điểm có lợi. Nếu nguồn là `double`, cần kiểm tra `Double.isFinite` trước khi chuyển tiếp sang kiểu thập phân chính xác.

## 4. NumberFormat: parse theo Locale

`java.text.NumberFormat` là lớp trừu tượng cho định dạng và phân tích các con số theo locale. Các factory method thường dùng:

- `NumberFormat.getNumberInstance(locale)`: định dạng/parse số thông thường.
- `NumberFormat.getIntegerInstance(locale)`: cấu hình phục vụ số nguyên; cần hiểu quy tắc làm tròn/parse của implementation.
- `NumberFormat.getPercentInstance(locale)`: định dạng/parse tỷ lệ phần trăm theo quy ước locale.
- `NumberFormat.getCurrencyInstance(locale)`: định dạng/parse số tiền có dấu tiền tệ.

Ví dụ parse số thông thường theo US và Đức:

```java
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
NumberFormat german = NumberFormat.getNumberInstance(Locale.GERMANY);

Number x = us.parse("1,234.56");      // 1234.56
Number y = german.parse("1.234,56");  // 1234.56
```

`NumberFormat.parse(String)` ném **`ParseException`** nếu không thể bắt đầu parse một giá trị hợp lệ, nhưng có một điểm cực kỳ quan trọng: phương thức **không mặc định đòi tiêu thụ hết chuỗi**.

### Cùng chuỗi, locale khác: dễ hiểu thành giá trị khác

Giả sử người dùng nhập `"1,5"`:

- Với định dạng Đức, dấu phẩy là dấu thập phân; ý nghĩa thường là 1.5.
- Với một định dạng US mặc định, dấu phẩy là dấu nhóm. Parser có thể diễn giải dấu nhóm theo cách linh hoạt thay vì báo lỗi như bạn kỳ vọng.

Kết quả phụ thuộc locale và cấu hình `NumberFormat`/`DecimalFormat`; vì thế **không đoán “người dùng chắc đang dùng dấu phẩy thập phân” rồi âm thầm đổi ký tự**.

### NumberFormat không nhất thiết trả Double

`NumberFormat.parse(...)` trả `Number`, không phải luôn `Double`:

```java
Number n = NumberFormat.getNumberInstance(Locale.US).parse("42");
```

Kiểu đối tượng thực tế có thể là `Long`, `Double` hoặc một kiểu `Number` khác tùy định dạng, giá trị và cấu hình. **Không ép thẳng sang `Double`** khi chưa có hợp đồng:

```java
// Double d = (Double) n; // Có thể gây ClassCastException.
double value = n.doubleValue(); // Có thể làm mất chính xác nếu n là số lớn.
```

Nếu cần số thập phân chính xác, hãy dùng cấu hình `DecimalFormat` trả `BigDecimal` như ở phần sau, chứ không parse `Number` rồi gọi `doubleValue()` trước khi chuyển sang `BigDecimal`.

## 5. ParseException và lỗi bị bỏ sót: parser có thể chỉ nhận tiền tố

Một parser theo locale có thể **parse thành công một phần đầu chuỗi rồi dừng**:

```java
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
Number parsed = us.parse("123xyz");
// Thông thường parsed là số 123; phần "xyz" không được dùng.
```

Đây là tình huống nguy hiểm khi ứng dụng tưởng toàn bộ chuỗi đã hợp lệ. `parse(String)` thành công **không tự chứng minh** dữ liệu đầu vào đã được tiêu thụ toàn bộ.

### Dùng ParsePosition để kiểm tra parse hết chuỗi

`ParsePosition` giữ vị trí bắt đầu, vị trí parser dừng (`getIndex()`) và vị trí lỗi (`getErrorIndex()`). Một hàm kiểm tra có thể là:

```java
static Number parseWhole(String text, NumberFormat format) {
    if (text == null) {
        throw new IllegalArgumentException("Value is required");
    }

    ParsePosition position = new ParsePosition(0);
    Number result = format.parse(text, position);

    if (result == null ||
            position.getIndex() != text.length() ||
            position.getErrorIndex() >= 0) {
        throw new IllegalArgumentException("Invalid localized number");
    }
    return result;
}
```

Đây là **mã minh họa đặt trong một class**, cần import `NumberFormat` và `ParsePosition`. Với `"123xyz"`, nếu parser tiêu thụ `"123"` và dừng ở index 3, phép kiểm tra vị trí sẽ **từ chối**, vì chuỗi dài 6 ký tự.

**Giới hạn của kiểm tra này:** dù parse hết chuỗi, `NumberFormat` vẫn có thể linh hoạt với grouping, khoảng trắng và những quy ước khác. Parse hết chuỗi **không đồng nghĩa validation cú pháp nghiêm ngặt theo mọi quy tắc nghiệp vụ**. Nếu cần cú pháp chính xác tuyệt đối, hãy xác thực pattern/dấu nhóm/độ dài trước hoặc chọn parser chuyên biệt.

### Vì sao không chỉ bắt ParseException?

`NumberFormat.parse(String)` có thể trả kết quả mà không ném `ParseException` khi còn phần đuôi. Ngược lại, `parse(String, ParsePosition)` **không ném ParseException** mà thể hiện tình trạng qua giá trị `null`, `index` và `errorIndex`. Hai API có quy tắc báo lỗi khác nhau; cần xử lý theo overload thực tế.

## 6. DecimalFormat.setParseBigDecimal(true): kết hợp locale với thập phân chính xác

`DecimalFormat` là implementation phổ biến của `NumberFormat` cho số thập phân. Khi cần đọc **số thập phân theo locale** mà tránh trung gian `double`, có thể bật:

```java
DecimalFormat parser =
        (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
parser.setParseBigDecimal(true);

BigDecimal amount =
        (BigDecimal) parser.parse("1.234,56");
System.out.println(amount.toPlainString()); // 1234.56
```

Đoạn này cần xử lý hoặc khai báo `ParseException` nếu dùng overload `parse(String)`; cũng dựa vào factory mặc định trả một `DecimalFormat` như các provider thông thường của JDK. Nếu hệ thống dùng `NumberFormatProvider` tùy biến, kiểm tra implementation trước khi cast.

**Khi `setParseBigDecimal(true)`:** kết quả hữu hạn thông thường được parse thành `BigDecimal`, nhưng các giá trị đặc biệt như NaN/vô cực không thể nằm trong `BigDecimal` nên cần chính sách kiểm tra riêng, không cast mù quáng với mọi đầu vào.

Đối với dữ liệu số tiền, nên kiểm tra cả:

- Chính xác locale của người nhập.
- Có được phép nhóm hàng nghìn không? Dấu nhóm đặt ở đâu?
- Có chấp nhận dấu cộng/âm hoặc ký hiệu tiền tệ không?
- `scale` tối đa là mấy chữ số thập phân?
- Nếu cần làm tròn, dùng `RoundingMode` nào?
- Parse phải tiêu thụ hết chuỗi và có cần pattern strict không?

### BigDecimal từ locale parser không phải BigDecimal từ Double

Tránh:

```java
Number n = NumberFormat.getNumberInstance(Locale.GERMANY)
        .parse("1.234,56");

BigDecimal potentiallyApproximate = new BigDecimal(n.doubleValue());
```

Ở đây dữ liệu đã đi qua `double` rồi mới vào `BigDecimal`. Nếu cần giữ giá trị thập phân chính xác từ văn bản, bật `setParseBigDecimal(true)` và parse trực tiếp thành `BigDecimal`.

## 7. NumberFormat và DecimalFormat có trạng thái: tránh dùng chung tùy tiện giữa luồng

Các formatter thuộc họ `NumberFormat`/`DecimalFormat` có thể được cấu hình:

```java
NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
format.setGroupingUsed(true);
format.setMaximumFractionDigits(2);
```

Các cấu hình này thay đổi **hành vi của object cụ thể**. `DecimalFormat` **không thread-safe**; không nên chia sẻ một instance mutable không đồng bộ giữa nhiều request/worker. Trong web service, thường tạo instance riêng theo request, sử dụng factory hoặc một phương án quản lý an toàn được kiểm chứng.

Cũng không tự ý thay `Locale.setDefault(...)` để parse một request: thao tác đó có thể ảnh hưởng các thành phần khác trong cùng JVM. Truyền `Locale` tường minh sẽ dễ kiểm thử hơn.

### Format để hiển thị không đồng nghĩa parse dữ liệu máy

```java
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);

String usText = us.format(1234.56); // Thường là "1,234.56".
String deText = de.format(1234.56); // Thường là "1.234,56".
```

`format` dùng cho giao diện theo locale; đầu ra có thể bị giới hạn số chữ số thập phân theo cấu hình mặc định. Không dùng output trình bày cho người đọc làm định dạng truyền dữ liệu chính xác mà không định nghĩa rõ pattern, độ chính xác và policy làm tròn.

## 8. Các chuỗi “trông giống nhau” nhưng parser hiểu khác

| Chuỗi | `Double.parseDouble` | `NumberFormat` US | `NumberFormat` Đức | Bài học |
| --- | --- | --- | --- | --- |
| `"1234.56"` | 1234.56 | 1234.56 | Dấu chấm có thể được hiểu là dấu nhóm | Cần chọn format tường minh |
| `"1,234.56"` | Lỗi cú pháp | Thường 1234.56 | Không nên coi là decimal Đức | Grouping theo locale |
| `"1.234,56"` | Lỗi cú pháp | Không nên coi là decimal US | Thường 1234.56 | Dấu chấm/phẩy đổi vai |
| `"1,5"` | Lỗi cú pháp | Có thể thành nhóm chữ số | 1.5 | Cùng text có thể đổi nghĩa |
| `"1e3"` | 1000.0 | Phụ thuộc parser locale/pattern | Phụ thuộc parser locale/pattern | Không giả định hỗ trợ exponent |
| `"NaN"` | NaN | Không phải số hữu hạn thông thường | Không phải số hữu hạn thông thường | Cần policy riêng |
| `"0x1.8p1"` | 3.0 | Không phải cú pháp number locale thông thường | Tương tự | Dạng hex float của Java |
| `"1_000"` | Lỗi | Không tự xem là literal Java | Tương tự | Dấu gạch dưới source khác dữ liệu |
| `" 2.5 "` | 2.5 | Theo quy tắc parse locale | Theo quy tắc parse locale | Whitespace cần hợp đồng |

Bảng diễn đạt **mục đích và những rủi ro điển hình**; `NumberFormat` còn phụ thuộc cấu hình và provider. Đừng suy diễn kết quả cứng cho các trường không được bảo đảm, đặc biệt khi parser linh hoạt và chấp nhận một tiền tố.

## 9. Thiết kế parser cho ba luồng dữ liệu thực tế

### 9.1. Dữ liệu giao thức máy: cú pháp cố định

Giả sử một API định nghĩa số đo JSON/text theo dấu chấm thập phân, không grouping và không NaN/vô cực:

```java
static double parseFiniteMachineDouble(String text) {
    if (text == null) {
        throw new IllegalArgumentException("Value is required");
    }

    final double value;
    try {
        value = Double.parseDouble(text);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Invalid numeric syntax", ex);
    }
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Finite value required");
    }
    return value;
}
```

Hàm trên dùng cú pháp Java `parseDouble`, cho phép dấu mũ, hexadecimal float, suffix và một số whitespace theo hợp đồng parser. **Nếu giao thức máy yêu cầu grammar hẹp hơn** (ví dụ chỉ `-?[0-9]+(\.[0-9]+)?`), phải validate grammar **trước** hoặc bên cạnh parse. `Double.isFinite` không thay thế validation ký tự.

### 9.2. Form nhập theo locale của người dùng

Một ứng dụng nhập số đo từ người dùng có thể cung cấp locale đang được chọn:

```java
Locale userLocale = Locale.GERMANY;
NumberFormat format = NumberFormat.getNumberInstance(userLocale);
Number number = parseWhole("1.234,56", format);
double value = number.doubleValue();
```

Sau khi parse, kiểm tra miền dữ liệu và `Double.isFinite`. Nếu giá trị là tiền hoặc cần đúng số thập phân đầu vào, dùng `DecimalFormat.setParseBigDecimal(true)` và `BigDecimal` phù hợp.

Với locale có dấu nhóm là **khoảng trắng không ngắt** hoặc dấu nhóm đặc biệt, không đơn giản thay toàn bộ khoảng trắng và dấu phẩy/dấu chấm mà không hiểu quy tắc Unicode. Hãy dùng parser locale đúng và validation theo format được hệ thống cho phép.

### 9.3. Tiền tệ/thập phân chính xác

Tiền không chỉ cần parse thành một giá trị; nó còn cần:

- **Đơn vị và loại tiền** (VND, USD, EUR...).
- **Số chữ số thập phân được chấp nhận**.
- **Định dạng của giao diện và của API** có thể khác nhau.
- **Policy làm tròn** rõ ràng cho phép chia, phần trăm, tỷ giá.
- **Không chuyển qua double** nếu yêu cầu chính xác thập phân.

Một thiết kế tốt thường tách: **input localized text → parser/validation → BigDecimal chuẩn hóa theo nghiệp vụ → dữ liệu miền**; khi hiển thị làm chiều ngược lại **BigDecimal → localized output text**.

## 10. Chương trình Java 17+ chạy độc lập

Lưu đoạn sau thành **`LocaleNumberParsingDemo.java`**:

```java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleNumberParsingDemo {
    static Number parseWhole(String text, NumberFormat format) {
        if (text == null) {
            throw new IllegalArgumentException("Text is required");
        }
        ParsePosition position = new ParsePosition(0);
        Number number = format.parse(text, position);
        if (number == null
                || position.getErrorIndex() >= 0
                || position.getIndex() != text.length()) {
            throw new IllegalArgumentException("Invalid localized number");
        }
        return number;
    }

    static String tryMachineDouble(String text) {
        try {
            return Double.toString(Double.parseDouble(text));
        } catch (NumberFormatException ex) {
            return "INVALID";
        }
    }

    public static void main(String[] args) {
        System.out.println("machine=" +
                Double.parseDouble("1234.56"));
        System.out.println("scientific=" +
                Double.parseDouble("1e3"));
        System.out.println("hex-float=" +
                Double.parseDouble("0x1.8p1"));
        System.out.println("whitespace=" +
                Double.parseDouble(" 2.5 "));
        System.out.println("comma-machine=" +
                tryMachineDouble("1,234.56"));
        System.out.println("underscore-machine=" +
                tryMachineDouble("1_000"));
        System.out.println("overflow-infinite=" +
                Double.isInfinite(Double.parseDouble("1e309")));
        System.out.println("nan=" +
                Double.isNaN(Double.parseDouble("NaN")));

        NumberFormat us = NumberFormat.getNumberInstance(Locale.US);
        NumberFormat de = NumberFormat.getNumberInstance(Locale.GERMANY);

        Number usValue = parseWhole("1,234.56", us);
        Number deValue = parseWhole("1.234,56", de);
        System.out.println("locale-us=" + usValue.doubleValue());
        System.out.println("locale-de=" + deValue.doubleValue());
        System.out.println("locale-same=" +
                (usValue.doubleValue() == deValue.doubleValue()));

        String withSuffix = "123xyz";
        ParsePosition position = new ParsePosition(0);
        Number prefix = us.parse(withSuffix, position);
        System.out.println("prefix=" + prefix +
                ",consumed=" + position.getIndex());
        try {
            parseWhole(withSuffix, us);
        } catch (IllegalArgumentException ex) {
            System.out.println("strict-consumption=INVALID");
        }

        DecimalFormat germanDecimal =
                (DecimalFormat) NumberFormat.getNumberInstance(
                        Locale.GERMANY);
        germanDecimal.setParseBigDecimal(true);
        BigDecimal exactLocalized =
                (BigDecimal) parseWhole("1.234,56", germanDecimal);
        System.out.println("localized-decimal=" +
                exactLocalized.toPlainString());

        BigDecimal exactMachine = new BigDecimal("1234.56");
        System.out.println("machine-decimal=" +
                exactMachine.toPlainString());
        System.out.println("decimal-sum=" +
                new BigDecimal("0.10")
                        .add(new BigDecimal("0.20"))
                        .toPlainString());

        System.out.println("format-us=" + us.format(1234.56));
        System.out.println("format-de=" + de.format(1234.56));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac LocaleNumberParsingDemo.java
java LocaleNumberParsingDemo
```

Stdout trên JDK 17+ với dữ liệu locale chuẩn US/Đức:

```text
machine=1234.56
scientific=1000.0
hex-float=3.0
whitespace=2.5
comma-machine=INVALID
underscore-machine=INVALID
overflow-infinite=true
nan=true
locale-us=1234.56
locale-de=1234.56
locale-same=true
prefix=123,consumed=3
strict-consumption=INVALID
localized-decimal=1234.56
machine-decimal=1234.56
decimal-sum=0.30
format-us=1,234.56
format-de=1.234,56
```

**Chú ý về locale provider:** dữ liệu ký hiệu/nhóm có thể được mở rộng qua hệ thống locale/provider của JDK. Chương trình cố định `Locale.US` và `Locale.GERMANY` để tránh lệ thuộc locale mặc định, nhưng với môi trường cấu hình provider tùy biến, hãy kiểm tra đầu ra thực tế thay vì gắn mọi biến thể ký hiệu vào một giả định chung.

### Giải thích kết quả

1. `machine`, `scientific`, `hex-float` cho thấy parser dấu phẩy động Java xử lý cú pháp độc lập locale, gồm exponent và hexadecimal float.
2. `comma-machine=INVALID` vì `Double.parseDouble` không tự nhận nhóm chữ số `1,234.56`; dấu `_` cũng bị từ chối trong chuỗi runtime.
3. `overflow-infinite=true` chứng minh parse hợp lệ về cú pháp **không đồng nghĩa giá trị hữu hạn**.
4. Hai số locale US/Đức biểu diễn cùng giá trị nhưng văn bản khác nhau.
5. `prefix=123,consumed=3` cho thấy parser có thể nhận **một đoạn đầu** rồi dừng; `parseWhole` từ chối vì không hết input.
6. `setParseBigDecimal(true)` trả `BigDecimal` cho chuỗi hữu hạn Đức, giữ thập phân **1234.56** mà không đi qua `double`.
7. `decimal-sum=0.30` cho thấy `BigDecimal` giữ chính xác chữ số thập phân và `scale` của dữ liệu mẫu.

## 11. Lỗi thường gặp và cách khắc phục

| Lỗi/nhầm lẫn | Vì sao sai | Hướng xử lý |
| --- | --- | --- |
| Cho rằng `Double.parseDouble` dùng locale mặc định | Nó dùng grammar riêng, không tự đổi dấu thập phân | Dùng NumberFormat nếu input localized |
| Parse `"1,5"` bằng `Double.parseDouble` | Dấu phẩy không phải dấu thập phân được nhận | Chọn locale/parser phù hợp |
| Dùng NumberFormat không truyền locale | Kết quả phụ thuộc cấu hình hệ thống | Truyền Locale tường minh |
| Cho rằng parse(String) tiêu thụ cả chuỗi | Có thể nhận tiền tố hợp lệ rồi dừng | Dùng ParsePosition và kiểm tra index |
| Cho rằng ParsePosition bảo đảm mọi grouping đúng format | Parser locale có thể linh hoạt | Validation pattern/grammar bổ sung |
| Ép `(Double) numberFormat.parse(text)` | Kết quả Number có thể là Long | Dùng Number, hoặc cấu hình BigDecimal |
| Gọi `new BigDecimal(number.doubleValue())` sau parse locale | Đưa sai số nhị phân vào BigDecimal | Parse trực tiếp với setParseBigDecimal(true) |
| Coi `Double.parseDouble("1e309")` là input hữu hạn | Kết quả Infinity dù cú pháp hợp lệ | Kiểm tra Double.isFinite |
| Parse số tiền thành double | Có thể mất decimal exactness | BigDecimal + scale/RoundingMode |
| Tự thay tất cả dấu chấm/phẩy | Dễ đổi nghĩa số hoặc chấp nhận format sai | Dùng locale chính xác và hợp đồng grammar |
| Cho rằng formatter dùng chung thread-safe | NumberFormat/DecimalFormat có trạng thái | Tạo instance riêng, tránh chia sẻ mutable |
| Lấy chuỗi format UI làm payload API | Format có grouping và rounding theo locale | Dùng schema số máy cố định |
| Coi NaN/Infinity là các BigDecimal hợp lệ | BigDecimal không biểu diễn chúng | Validation và loại dữ liệu tường minh |

## 12. Bài tập tự luyện

1. **So sánh hai parser.** Với `"1,234.56"` và `"1.234,56"`, thử `Double.parseDouble`, `NumberFormat` US và `NumberFormat` Đức. Mỗi trường hợp có tiêu thụ hết chuỗi không? Chỉ kết luận sau khi kiểm tra `ParsePosition`.
2. **Chuỗi có phần đuôi.** Parse `"42kg"` bằng `NumberFormat.parse(String)` và bằng hàm `parseWhole`. Vì sao một cách có thể nhận 42 còn cách kia phải từ chối?
3. **Cú pháp Java.** Dự đoán hành vi của `Double.parseDouble("1e3")`, `Double.parseDouble("0x1.8p1")`, `Double.parseDouble("1_000")`, `Double.parseDouble("NaN")`.
4. **Số hữu hạn.** Viết method nhận số máy và từ chối NaN, Infinity, giá trị ngoài `[-1000, 1000]`. Vì sao bắt NumberFormatException chưa đủ?
5. **Locale và tiền.** Tạo parser số tiền theo `Locale.GERMANY`, bật `setParseBigDecimal(true)` rồi tính thuế bằng `BigDecimal`. Chọn scale/RoundingMode theo hợp đồng cụ thể.
6. **Ràng buộc nhóm.** Thiết kế validator US chỉ chấp nhận `"1234.56"` hoặc `"1,234.56"` theo quy tắc nhóm ba chữ số, nhưng từ chối `"12,34.56"`. Tại sao chỉ parse hết chuỗi chưa đủ để chứng minh grouping hợp lệ?
7. **Quốc tế hóa.** Thử locale dùng dấu nhóm không phải dấu phẩy/chấm. Tại sao không nên tự xóa tất cả khoảng trắng?
8. **API và UI.** Thiết kế luồng lưu trữ: input localized → BigDecimal → dữ liệu miền → format localized; phân biệt bước nào được phép làm tròn.
9. **Test tự động.** Viết unit test cho `parseWhole` với null, chuỗi rỗng, chuỗi sai, số có suffix, số hợp lệ US/Đức và trường hợp có phần đuôi.
10. **Giao thức.** Một file CSV máy quy định dấu chấm thập phân và không có grouping. Tại sao đặt `Locale.getDefault()` vào parser khiến file có thể được hiểu khác khi chạy trên máy khác?

## 13. Checklist thực hành

- Hỏi đầu tiên: **đây là dữ liệu kỹ thuật cố định hay chuỗi hiển thị/nhập theo locale?**
- Với dữ liệu máy: dùng `Double.parseDouble`, `Float.parseFloat` hoặc `BigDecimal(String)` và validation grammar theo giao thức.
- Với dữ liệu người dùng theo vùng: dùng `NumberFormat`/`DecimalFormat` với **Locale tường minh**.
- Nhớ dấu `.`, `,`, grouping và ký tự Unicode có thể đổi nghĩa theo locale.
- Với `NumberFormat`, **kiểm tra tiêu thụ hết chuỗi** bằng `ParsePosition` khi cần; kết quả parse một tiền tố chưa phải chứng cứ input hợp lệ.
- Nếu grammar phải nghiêm ngặt, bổ sung validator cho grouping, dấu, whitespace, số chữ số và format chấp nhận.
- Không ép kết quả `Number` thành `Double` mà không kiểm tra kiểu; nếu cần thập phân chính xác, dùng `DecimalFormat.setParseBigDecimal(true)`.
- Không dùng `new BigDecimal(double)` sau khi đã parse qua double để “phục hồi” chuỗi thập phân.
- Kiểm tra `NaN`/`Infinity` và miền dữ liệu khi parse ra float/double.
- Với số tiền hoặc dữ liệu decimal exact, giữ `BigDecimal` và quy tắc làm tròn nghiệp vụ.
- Không chia sẻ `DecimalFormat` mutable giữa các thread nếu không có bảo vệ phù hợp.
- Kiểm thử trên nhiều `Locale`, kể cả locale khác dấu nhóm, và với input sai ở cuối chuỗi.

**Kết luận:** `parseDouble`/`parseFloat` và `BigDecimal(String)` phù hợp với **cú pháp số độc lập locale**, trong khi `NumberFormat`/`DecimalFormat` phục vụ **chuỗi số theo quy ước khu vực**. Chọn đúng parser, kiểm tra tiêu thụ hết input, giữ độ chính xác theo yêu cầu và tách parse khỏi validation là chìa khóa để tránh những lỗi số học âm thầm trong ứng dụng Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-006">Chuỗi thập phân sang BigDecimal để giữ ý nghĩa thập phân</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi thập phân sang `BigDecimal` để giữ ý nghĩa thập phân trong Java

## 1. Vì sao cần parse chuỗi thập phân bằng `BigDecimal`?

Khi người dùng nhập `"0.1"`, dữ liệu ban đầu là **chuỗi biểu diễn một phần mười trong hệ thập phân**. Nếu lập trình viên chuyển chuỗi đó sang `double`, giá trị được lưu thường chỉ là **số nhị phân gần nhất có thể biểu diễn**, không phải đúng một phần mười về mặt toán học.

Java có hai cách xử lý rất khác nhau:

~~~java
double binary = Double.parseDouble("0.1");
BigDecimal decimal = new BigDecimal("0.1");
~~~

Đây là đoạn minh họa; cần `import java.math.BigDecimal;` khi đặt vào class.

- `binary` là giá trị `double` gần `0.1` nhất theo quy tắc của binary64.
- `decimal` là giá trị thập phân **chính xác** `0.1`: phần nguyên chưa được scale là 1, `scale` là 1, tương đương `1 × 10^-1`.

`BigDecimal` đặc biệt hữu ích cho dữ liệu có **ý nghĩa thập phân từ nguồn**: đơn giá, số tiền, thuế, tỷ lệ kinh doanh, dữ liệu xuất nhập dạng CSV/JSON chứa số thập phân, hoặc yêu cầu so sánh/kiểm toán chính xác theo chữ số.

**Cốt lõi:** Nếu nguồn là chuỗi thập phân, tạo trực tiếp bằng **`new BigDecimal(text)`**. Đừng buộc dữ liệu đi qua `double` nếu cần giữ đúng ý nghĩa thập phân gốc.

~~~java
BigDecimal sum = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));
// sum.toString() bằng "0.3"
~~~

Phép cộng ở đây giữ chính xác giá trị thập phân vì hai đầu vào được parse trực tiếp, không có sai số nhị phân trung gian. Nhưng cần hiểu rõ rằng `BigDecimal` **không tự giúp mọi phép chia ra số hữu hạn**, cũng không thể tự khôi phục một giá trị thập phân đã bị thay đổi trước đó.

## 2. `BigDecimal` lưu số theo cấu trúc nào?

`java.math.BigDecimal` là một lớp **bất biến** (*immutable*), biểu diễn số thập phân với độ chính xác tùy ý trong giới hạn tài nguyên và ràng buộc của API.

Có thể hiểu giá trị của một `BigDecimal` bằng:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

Trong đó:

- `unscaledValue()` là số nguyên `BigInteger` mang toàn bộ chữ số của hệ số.
- `scale()` là số nguyên chỉ **số chữ số ở bên phải dấu thập phân** nếu dương; `scale` cũng có thể bằng 0 hoặc âm.
- `precision()` là **số chữ số thập phân có nghĩa của phần hệ số** theo định nghĩa của `BigDecimal`, không phải số chữ số sau dấu chấm.

Ví dụ:

| Chuỗi nhập | Giá trị thập phân | `unscaledValue()` | `scale()` | `precision()` |
|---|---:|---:|---:|---:|
| `"123.45"` | 123.45 | 12345 | 2 | 5 |
| `"0.1"` | 0.1 | 1 | 1 | 1 |
| `"1.20"` | 1.20 | 120 | 2 | 3 |
| `"1000"` | 1000 | 1000 | 0 | 4 |
| `"1E+3"` | 1000 | 1 | -3 | 1 |
| `"0.00"` | 0.00 | 0 | 2 | 1 |

**Ví dụ quan trọng:** `new BigDecimal("1.20")` và `new BigDecimal("1.2")` có cùng **giá trị toán học 1.2**, nhưng có **scale khác nhau** (2 và 1), vì thế có thể khác về định dạng và phương thức so sánh đối tượng.

`BigDecimal` không phải một kiểu số dấu phẩy động IEEE 754 như `double`. Nó dựa trên hệ số nguyên và lũy thừa 10, nên có thể biểu diễn chính xác nhiều phân số thập phân hữu hạn mà binary64 không thể.

## 3. Ba cách tạo từ dữ liệu — kết quả khác nhau

### 3.1. `new BigDecimal(String)`: cách đúng cho nguồn chữ số thập phân

~~~java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("12.3400");
BigDecimal c = new BigDecimal("-7.25");
~~~

Những chuỗi được parse trực tiếp theo cú pháp số thập phân mà `BigDecimal` hỗ trợ. `b` giữ scale 4, nên `b.toString()` thường in `12.3400`; đối tượng không tự ý bỏ các số 0 cuối.

### 3.2. `new BigDecimal(double)`: giữ **chính xác giá trị nhị phân** đã có

~~~java
BigDecimal fromBinary = new BigDecimal(0.1);
~~~

`double` chứa một giá trị nhị phân gần 0.1 nhưng **không đúng bằng** 0.1 toán học. Constructor nhận `double` chuyển **giá trị double đang lưu** thành một `BigDecimal` chính xác đối với *double đó*, nên nó hiện rõ phần sai lệch nhị phân:

~~~text
0.1000000000000000055511151231257827021181583404541015625
~~~

Đây không phải lỗi của `BigDecimal`. Dữ liệu đã bị xấp xỉ ngay từ bước tạo `double`.

### 3.3. `BigDecimal.valueOf(double)`: dựa trên biểu diễn thập phân của `double`

~~~java
BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
~~~

`valueOf(double)` sử dụng biểu diễn thập phân dạng chuỗi do `Double.toString(double)` tạo ra, rồi chuyển thành `BigDecimal`. Vì `Double.toString(0.1)` là `"0.1"`, kết quả ở ví dụ này có `toString()` bằng `"0.1"`.

**Nhưng `BigDecimal.valueOf(double)` không thể khôi phục toàn bộ dữ liệu thập phân gốc** nếu phép tính dùng `double` đã thay đổi giá trị:

~~~java
double sum = 0.1 + 0.2;
BigDecimal afterDouble = BigDecimal.valueOf(sum);
// afterDouble là 0.30000000000000004, không phải đúng 0.3
~~~

Nhớ quy tắc:

- **Nguồn là `String` thập phân:** dùng `new BigDecimal(text)`.
- **Nguồn thực sự đã là `double`:** hiểu rõ dữ liệu đã có sai số/rounding; `valueOf(double)` thường tạo dạng thập phân dễ đọc, nhưng không đảo ngược lịch sử phép toán.
- **`new BigDecimal(double)`:** chỉ chọn khi thật sự cần chuyển chính xác **giá trị binary64 hiện tại** sang thập phân, không phải vì muốn “tránh sai số”.

## 4. Ví dụ Java 17+ chạy độc lập: so sánh ba cách tạo

Tạo tệp `DecimalConstructionDemo.java`:

~~~java
import java.math.BigDecimal;

public class DecimalConstructionDemo {
    public static void main(String[] args) {
        String source = "0.1";

        BigDecimal exact = new BigDecimal(source);
        BigDecimal fromDouble =
                new BigDecimal(Double.parseDouble(source));
        BigDecimal viaValueOf =
                BigDecimal.valueOf(Double.parseDouble(source));

        System.out.println("text=" + exact);
        System.out.println("doubleConstructor=" + fromDouble);
        System.out.println("valueOfDouble=" + viaValueOf);
        System.out.println("binarySum=" + (0.1 + 0.2));
        System.out.println("decimalSum="
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));
        System.out.println("valueOfBinarySum="
                + BigDecimal.valueOf(0.1 + 0.2));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac DecimalConstructionDemo.java
java DecimalConstructionDemo
~~~

Đầu ra:

~~~text
text=0.1
doubleConstructor=0.1000000000000000055511151231257827021181583404541015625
valueOfDouble=0.1
binarySum=0.30000000000000004
decimalSum=0.3
valueOfBinarySum=0.30000000000000004
~~~

Dòng `decimalSum` mới là ví dụ giữ **ý nghĩa thập phân của hai chuỗi gốc**. Dòng `valueOfBinarySum` chỉ làm tròn chuỗi biểu diễn của **kết quả double đã được tính**; nó không sửa được sai số của phép cộng thực hiện trước đó.

## 5. Cú pháp chuỗi hợp lệ cho `BigDecimal`

`new BigDecimal(String)` chấp nhận văn bản số thập phân theo ngữ pháp của `BigDecimal`, bao gồm dấu tùy chọn, dấu chấm thập phân và ký pháp số mũ `e`/`E`.

Ví dụ hợp lệ:

~~~java
new BigDecimal("42");        // 42
new BigDecimal("+42.00");    // 42.00
new BigDecimal("-0.125");    // -0.125
new BigDecimal(".5");        // 0.5
new BigDecimal("5.");        // 5
new BigDecimal("1.23E+4");   // 12300
new BigDecimal("1e-3");     // 0.001
~~~

Đây là các lời gọi minh họa. Các dạng trên có thể có **scale** khác nhau; ví dụ `"1.23E+4"` có hệ số 123 và scale -2, giá trị 12300.

**Không nhầm** parser runtime với các biểu thức literal Java. Các dạng sau là lỗi định dạng nếu truyền trực tiếp cho `new BigDecimal(text)`:

- `"1_000.50"`: gạch dưới hợp lệ trong một số literal Java, **không phải** chữ số thập phân trong chuỗi parse.
- `"1,234.50"` hoặc `"1.234,50"`: dấu phân nhóm hay dấu phẩy thập phân theo locale **không được hiểu tự động** bởi constructor này.
- `" 12.34 "`: khoảng trắng đầu/cuối không được tự trim.
- `""`, `"+"`, `"."`: không có dãy chữ số hợp lệ.
- `"NaN"`, `"Infinity"`: không phải giá trị thập phân `BigDecimal` có thể biểu diễn.
- `"0x1.8p2"`: không phải cú pháp số thập phân của constructor này.

Các chuỗi không hợp lệ thường dẫn tới `NumberFormatException`. Nếu nguồn có thể là `null`, cần kiểm tra `null` riêng trước khi parse; không coi nó tương đương chuỗi `"0"`.

### Có thể dùng `trim()` không?

Nếu hợp đồng dữ liệu cho phép khoảng trắng đầu/cuối, bạn **có thể chủ động** gọi `text.trim()` hoặc `strip()` trước khi parse. Tuy nhiên, đây là **quyết định chuẩn hóa dữ liệu**. Trong hệ thống yêu cầu định dạng chặt (mã giao dịch, tài chính, ký số), nên từ chối đầu vào không đúng thay vì âm thầm thay đổi.

Các chuỗi hiển thị số theo locale cần một bước xử lý riêng: quy định rõ dấu thập phân, dấu tách hàng nghìn, nhóm số và cách kiểm tra. Đừng tùy tiện thay mọi dấu `,` bằng `.` vì `"1,234"` có thể nghĩa là 1234 hoặc 1.234 tùy locale.

## 6. Ví dụ Java 17+: parse và báo lỗi rõ ràng

Tạo tệp `DecimalInputValidationDemo.java`:

~~~java
import java.math.BigDecimal;

public class DecimalInputValidationDemo {
    static void parse(String input) {
        try {
            BigDecimal value = new BigDecimal(input);
            System.out.println("ok[" + input + "]="
                    + value.toPlainString());
        } catch (NumberFormatException ex) {
            System.out.println("bad[" + input + "]");
        }
    }

    public static void main(String[] args) {
        parse("42");
        parse("+42.00");
        parse("1.23E+4");
        parse("0.001");
        parse("1_000.50");
        parse("1,234.50");
        parse(" 12.34 ");
        parse("NaN");
        parse("");
    }
}
~~~

~~~bash
javac DecimalInputValidationDemo.java
java DecimalInputValidationDemo
~~~

Đầu ra:

~~~text
ok[42]=42
ok[+42.00]=42.00
ok[1.23E+4]=12300
ok[0.001]=0.001
bad[1_000.50]
bad[1,234.50]
bad[ 12.34 ]
bad[NaN]
bad[]
~~~

Hàm `parse` chỉ dùng dữ liệu ví dụ nên đầu vào luôn là chuỗi không `null`. Khi xử lý dữ liệu bên ngoài, kiểm tra `null` trước và đặt giới hạn độ dài/số chữ số/số mũ phù hợp yêu cầu hệ thống. `BigDecimal` không bị giới hạn bởi độ chính xác 53 bit như `double`, nhưng vẫn có **giới hạn tài nguyên, miền `int` của scale và độ dài tính toán**.

Nếu cần báo lỗi nhập liệu, hãy ghi rõ trường nào sai và định dạng được phép; đừng che giấu lỗi bằng `catch (Exception)` rồi trả về một số 0 mặc định.

## 7. Từ chuỗi đã parse đến phép tính thập phân chính xác

`BigDecimal` hỗ trợ các phép toán qua **phương thức** chứ không dùng các toán tử `+`, `-`, `*`, `/` trực tiếp trên đối tượng:

| Phép toán | Ví dụ `BigDecimal` | Ý nghĩa |
|---|---|---|
| Cộng | `a.add(b)` | Tổng số thập phân |
| Trừ | `a.subtract(b)` | Hiệu số thập phân |
| Nhân | `a.multiply(b)` | Tích số thập phân |
| Chia | `a.divide(b)` | Thương chính xác **chỉ khi biểu diễn được theo dạng thập phân hữu hạn** nếu không quy định làm tròn |
| So sánh số học | `a.compareTo(b)` | So sánh **giá trị số** |
| Đổi dấu | `a.negate()` | Số đối |
| Đặt scale | `a.setScale(2, RoundingMode.HALF_UP)` | Chọn số chữ số sau dấu chấm theo quy tắc làm tròn |

Các phương thức như `add`, `subtract`, `multiply` với **không có `MathContext` giới hạn độ chính xác** cho kết quả chính xác về giá trị thập phân trong giới hạn thực tế và API. Nếu cung cấp `MathContext` hoặc gọi `setScale` với chế độ làm tròn, kết quả **có thể bị làm tròn theo chính sách được chọn**.

### Vì sao dùng toán tử trực tiếp không được?

~~~java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
// BigDecimal wrong = a + b; // Lỗi biên dịch: không có toán tử + cho BigDecimal
BigDecimal right = a.add(b);
~~~

Đây là đoạn minh họa. `BigDecimal` là class, không phải primitive numeric type. Ngoại lệ thường gặp là toán tử `+` với `String` tạo nối chuỗi, nhưng điều đó **không phải phép cộng số**.

### `BigDecimal` là immutable

~~~java
BigDecimal total = new BigDecimal("1.20");
total.add(new BigDecimal("0.30"));  // Không sửa total!
total = total.add(new BigDecimal("0.30")); // total thành 1.50
~~~

Mỗi phép toán trả **đối tượng kết quả**. Quên gán lại là một lỗi logic phổ biến: lệnh `total.add(...)` riêng lẻ không thay đổi biến `total`.

## 8. Phép chia và quy tắc làm tròn: vấn đề riêng với `BigDecimal`

Kết quả `1 ÷ 3` là số thập phân vô hạn tuần hoàn. Không tồn tại `BigDecimal` hữu hạn nào biểu diễn chính xác toàn bộ thương đó. Vì vậy:

~~~java
BigDecimal one = new BigDecimal("1");
BigDecimal three = new BigDecimal("3");
// one.divide(three); // Ném ArithmeticException vì khai triển không kết thúc.
~~~

Muốn nhận kết quả thực dụng, chỉ định scale và `RoundingMode`:

~~~java
BigDecimal quotient = one.divide(three, 4, RoundingMode.HALF_UP);
// quotient = 0.3333
~~~

Đây là đoạn minh họa; cần import `java.math.RoundingMode`.

**Phân biệt rất quan trọng:**

- **Parse chuỗi**: `new BigDecimal("0.1")` giữ đúng số mà chuỗi biểu diễn.
- **Cộng/trừ/nhân không giới hạn precision**: giữ đúng giá trị thập phân toán học khi kết quả trong khả năng biểu diễn.
- **Chia không kết thúc**: cần chọn rounding/precision hoặc để API ném lỗi.
- **Làm tròn nghiệp vụ**: cần chính sách `scale`, `precision` và `RoundingMode`; “dùng BigDecimal” **không tự quyết định chính sách ấy**.

### `setScale` và `MathContext` không giống nhau

- `setScale(2, HALF_UP)` đặt **hai chữ số sau dấu chấm thập phân**, có thể làm tròn giá trị.
- `MathContext(4, HALF_UP)` giới hạn **4 chữ số có nghĩa của toàn bộ hệ số**, không đồng nghĩa 4 chữ số thập phân sau dấu chấm.
- `RoundingMode.UNNECESSARY` yêu cầu không cần làm tròn; nếu phép chuyển phải bỏ chữ số có ý nghĩa, ném `ArithmeticException`.

Ví dụ số `"123.456"`:
- `setScale(2, HALF_UP)` cho `123.46`.
- `round(new MathContext(4, HALF_UP))` cho `123.5`.

Quy tắc nào đúng phụ thuộc nghiệp vụ. Với khoản tiền, thường thống nhất cả đơn vị tiền tệ, số chữ số thập phân, cơ chế làm tròn thuế/phí và thời điểm làm tròn.

## 9. Ví dụ Java 17+: cộng, nhân, chia và làm tròn

Tạo tệp `DecimalArithmeticDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class DecimalArithmeticDemo {
    public static void main(String[] args) {
        BigDecimal price = new BigDecimal("19.95");
        BigDecimal quantity = new BigDecimal("3");
        BigDecimal total = price.multiply(quantity);

        System.out.println("total=" + total);
        System.out.println("sum="
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));
        System.out.println("exactDivision="
                + new BigDecimal("1").divide(new BigDecimal("4")));
        System.out.println("roundedDivision="
                + new BigDecimal("1").divide(
                        new BigDecimal("3"), 4, RoundingMode.HALF_UP));

        BigDecimal sample = new BigDecimal("2.345");
        System.out.println("halfUp="
                + sample.setScale(2, RoundingMode.HALF_UP));
        System.out.println("halfEven="
                + sample.setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("signAware="
                + new BigDecimal("-2.345").setScale(
                        2, RoundingMode.HALF_UP));

        BigDecimal precise = new BigDecimal("123.456");
        System.out.println("scale2="
                + precise.setScale(2, RoundingMode.HALF_UP));
        System.out.println("precision4="
                + precise.round(new MathContext(
                        4, RoundingMode.HALF_UP)));
    }
}
~~~

~~~bash
javac DecimalArithmeticDemo.java
java DecimalArithmeticDemo
~~~

Đầu ra:

~~~text
total=59.85
sum=0.3
exactDivision=0.25
roundedDivision=0.3333
halfUp=2.35
halfEven=2.34
signAware=-2.35
scale2=123.46
precision4=123.5
~~~

Dòng `halfUp` và `halfEven` khác nhau do cách xử lý trường hợp giá trị chính giữa hai lựa chọn. Với `2.345` ở scale 2, `HALF_UP` cho `2.35`, còn `HALF_EVEN` cho `2.34` vì chữ số cuối của kết quả 2.34 là chẵn.

**Không giả định quy tắc “làm tròn đến hai chữ số” mặc định luôn là `HALF_UP`**. Nếu một hệ thống cần `HALF_EVEN`, `DOWN`, `CEILING` hay quy tắc khác, phải truyền `RoundingMode` phù hợp.

## 10. `equals` và `compareTo`: khác biệt vì scale

Hai đối tượng `BigDecimal` có thể có **giá trị số bằng nhau** nhưng **khác scale**:

~~~java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("1.2");

a.equals(b);                // false
a.compareTo(b) == 0;        // true
~~~

`equals` xét cả **giá trị không scale và scale**, nên ở đây trả `false`. `compareTo` chỉ so sánh **giá trị số học**, nên cho 0.

| Phép kiểm tra | `"1.20"` và `"1.2"` | Ý nghĩa |
|---|---|---|
| `a.equals(b)` | `false` | Khác scale (2 so với 1) |
| `a.compareTo(b) == 0` | `true` | Cùng độ lớn thập phân |
| `a.hashCode() == b.hashCode()` | Không nên dựa vào | `hashCode` phù hợp với `equals`, không phải thay thế so sánh số học |
| `a.toString().equals(b.toString())` | `false` | Khác chuỗi/scale, không phải kiểm tra số học |

**Hệ quả với cấu trúc dữ liệu:** `HashSet<BigDecimal>` sử dụng `equals`/`hashCode` nên có thể giữ cả `1.2` và `1.20` như hai phần tử khác nhau. Trong khi `TreeSet<BigDecimal>` mặc định dùng `compareTo` để xếp thứ tự và thường coi hai giá trị đó tương đương theo comparator.

### Ví dụ Java 17+: scale, so sánh và collection

Tạo tệp `DecimalScaleEqualityDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class DecimalScaleEqualityDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.20");
        BigDecimal b = new BigDecimal("1.2");

        System.out.println("aScale=" + a.scale());
        System.out.println("bScale=" + b.scale());
        System.out.println("equals=" + a.equals(b));
        System.out.println("numericEqual=" + (a.compareTo(b) == 0));

        Set<BigDecimal> hashes = new HashSet<>();
        hashes.add(a);
        hashes.add(b);

        Set<BigDecimal> sorted = new TreeSet<>();
        sorted.add(a);
        sorted.add(b);

        System.out.println("hashSetSize=" + hashes.size());
        System.out.println("treeSetSize=" + sorted.size());

        BigDecimal original = new BigDecimal("1000.00");
        BigDecimal normalized = original.stripTrailingZeros();
        System.out.println("original=" + original);
        System.out.println("normalized=" + normalized);
        System.out.println("normalizedScale=" + normalized.scale());
        System.out.println("plain=" + normalized.toPlainString());
    }
}
~~~

~~~bash
javac DecimalScaleEqualityDemo.java
java DecimalScaleEqualityDemo
~~~

Đầu ra:

~~~text
aScale=2
bScale=1
equals=false
numericEqual=true
hashSetSize=2
treeSetSize=1
original=1000.00
normalized=1E+3
normalizedScale=-3
plain=1000
~~~

`stripTrailingZeros()` bỏ số 0 không cần thiết ở phần hệ số và có thể tạo **scale âm**. Không nên gọi nó tùy tiện nếu scale lưu trữ là thông tin nghiệp vụ quan trọng, chẳng hạn hệ thống yêu cầu ghi đúng `"1.20"` theo định dạng nhập.

## 11. `toString`, `toPlainString` và giữ **giá trị** so với giữ **cách viết ban đầu**

Khi parse chuỗi, `BigDecimal` giữ **giá trị thập phân và scale tương ứng**, nhưng **không giữ nguyên mọi ký tự của chuỗi đầu vào**.

Ví dụ:

~~~java
BigDecimal a = new BigDecimal("0001.2300");
BigDecimal b = new BigDecimal("1.23E+4");

System.out.println(a);                  // 1.2300
System.out.println(b);                  // 1.23E+4
System.out.println(b.toPlainString());  // 12300
~~~

Đây là minh họa. Dãy số 0 đứng đầu trong `"0001.2300"` không nhất thiết được xuất lại; ký pháp số mũ cũng có thể được chuẩn hóa. `BigDecimal` không phải công cụ lưu **token văn bản gốc** từng ký tự.

- `toString()` tạo biểu diễn chuỗi thập phân chuẩn của `BigDecimal`, có thể dùng số mũ.
- `toPlainString()` xuất **không dùng ký pháp số mũ**. Nó phù hợp với một số giao diện/CSV, nhưng có thể tạo chuỗi dài nếu scale lớn.
- `toEngineeringString()` có thể dùng số mũ ở bội số 3, phù hợp một số ứng dụng khoa học/kỹ thuật.
- `stripTrailingZeros()` tạo **giá trị `BigDecimal` mới** sau chuẩn hóa chữ số 0 cuối; nó có thể thay đổi scale.

Nếu cần lưu chứng từ chứa chính xác cách khách nhập `"+0001.2300"`, hãy lưu riêng **chuỗi đầu vào đã xác thực** cùng giá trị `BigDecimal` khi cần truy xuất bản gốc. Không kỳ vọng `toString()` khôi phục dấu cộng hoặc số 0 đầu.

Nếu xây dựng round-trip, cần xác định rõ “giữ nguyên” là **cùng giá trị số**, **cùng scale**, hay **cùng chuỗi ký tự**. Ba mục tiêu này không giống nhau.

## 12. Đọc dữ liệu ngoại vi: CSV, JSON và định dạng theo locale

### Khi nguồn đã là String

Với chuỗi JSON/CSV số thập phân như `"123.4500"`, dùng `new BigDecimal(text)` sau khi kiểm tra format. Hạn chế chuyển qua `Double.parseDouble` hoặc ép sang `double` trong pipeline.

Ví dụ một pipeline **mất ý nghĩa decimal**:

~~~java
String input = "0.1";
double intermediate = Double.parseDouble(input);
BigDecimal result = new BigDecimal(intermediate); // mang theo xấp xỉ nhị phân
~~~

Pipeline giữ giá trị theo chuỗi:

~~~java
String input = "0.1";
BigDecimal result = new BigDecimal(input);
~~~

### Khi nguồn là JSON number

Nhiều thư viện JSON cho phép chọn kiểu số khi parse. Nếu một thư viện tự parse mọi số thập phân thành `double` trước, các chữ số có thể đã mất hoặc bị làm tròn **trước khi** tạo `BigDecimal`. Với dữ liệu tiền/số thập phân cần chính xác, hãy cấu hình parser lấy numeric token trực tiếp thành `BigDecimal` hoặc chuỗi theo API cụ thể của thư viện.

Không thể nói tất cả thư viện JSON mặc định xử lý giống nhau; phải kiểm tra phiên bản, model DTO và cơ chế parser thực tế.

### Khi nguồn là số theo locale

Một người dùng có thể nhập `"1.234,56"` hoặc `"1,234.56"`. Đây là hai cách viết có thể tương ứng với 1234.56 ở các locale khác nhau. `new BigDecimal(String)` **không tự hiểu dấu tách hàng nghìn hoặc dấu phẩy thập phân theo locale**. Nếu ứng dụng cần nhập số theo ngôn ngữ:

1. Xác định và cố định **locale** đầu vào.
2. Dùng parser/formatter hỗ trợ locale phù hợp hoặc xác thực định dạng theo hợp đồng.
3. Chuyển số thu được sang `BigDecimal` **không đi qua `double`** nếu yêu cầu chính xác; một số API `NumberFormat` có thể cấu hình parse thành `BigDecimal` hoặc có thể xây dựng quy trình chuẩn hóa token có kiểm soát.
4. Đặt test cho các chuỗi mơ hồ, dấu âm, separator và khoảng trắng.

Đừng thay dấu phân cách kiểu “replace all comma thành dot” rồi tự nhận là đã hỗ trợ locale.

## 13. Các giới hạn cần biết

**`BigDecimal` chính xác theo hệ thập phân không có nghĩa mọi phép toán luôn giữ vô hạn chữ số.**

- `BigDecimal` là immutable: cần lưu kết quả phép toán trả về.
- Constructor `BigDecimal(String)` chỉ phân tích văn bản hợp lệ, không nhận `NaN`/vô cực.
- `BigDecimal.divide` không chỉ định rounding có thể ném `ArithmeticException` với phần thập phân tuần hoàn.
- `MathContext` giới hạn precision và có thể làm tròn.
- `setScale` có thể làm tròn hoặc ném lỗi khi dùng `UNNECESSARY`.
- `doubleValue()` có thể mất độ chính xác hoặc overflow sang Infinity; `intValue()`/`longValue()` có thể mất thông tin. Dùng phương thức `...ValueExact()` phù hợp nếu cần kiểm tra tính chính xác và miền.
- Độ lớn scale dùng `int`, kích thước số nguyên hệ số và thao tác tính toán vẫn bị ràng buộc bởi bộ nhớ/tài nguyên. Không xem `BigDecimal` là bộ nhớ vô hạn.
- Chuỗi cực dài hoặc số mũ cực trị từ nguồn không tin cậy cần được **giới hạn và kiểm tra** để giảm rủi ro tiêu tốn tài nguyên.
- Chính sách scale/rounding khi ghi dữ liệu cần được xác định nhất quán với yêu cầu nghiệp vụ.

### Khi nào chọn `BigDecimal`, `double` hoặc `long`?

| Bài toán | Lựa chọn thường phù hợp |
|---|---|
| Tiền tệ, thuế, giá thập phân chính xác | `BigDecimal` hoặc đơn vị nhỏ nhất trong `long` nếu thiết kế phù hợp |
| Vật lý, đồ họa, mô phỏng, thống kê chấp nhận sai số | `double`/`float` tùy precision/performance |
| Số lượng nguyên, bộ đếm chính xác trong miền | `int`/`long` với kiểm tra tràn khi cần |
| Phân tích dữ liệu đầu vào là chuỗi thập phân nhiều chữ số | `BigDecimal(String)` |
| Phân tích nguồn vốn đã là số nhị phân `double` | Xác định rõ mục tiêu; `valueOf(double)` hoặc `BigDecimal(double)` có ngữ nghĩa khác nhau |
| Cần giữ nguyên từng ký tự của token nhập | Giữ `String` gốc song song với `BigDecimal` |

## 14. Những lỗi phổ biến

| Nhận định sai | Thực tế |
|---|---|
| “`new BigDecimal(0.1)` chính là số thập phân 0.1.” | Nó lấy **giá trị binary64 xấp xỉ của 0.1** rồi chuyển chính xác giá trị đó. |
| “`BigDecimal.valueOf(0.1 + 0.2)` sửa phép cộng double.” | Không: nó nhận `0.30000000000000004` từ phép cộng đã thực hiện. |
| “Cứ gọi `BigDecimal` là mọi phép chia đều chính xác hữu hạn.” | `1.divide(3)` không rounding ném `ArithmeticException`. |
| “`setScale(2)` chỉ là đổi cách hiển thị.” | Khi giảm scale, nó có thể cần làm tròn hoặc ném lỗi; nó trả `BigDecimal` mới. |
| “`precision()` là số chữ số sau dấu phẩy.” | `precision` là số chữ số có nghĩa của hệ số; `scale` biểu thị vị trí dấu thập phân. |
| “`equals` và `compareTo == 0` luôn tương đương.” | `equals` quan tâm scale; `compareTo` so giá trị số. |
| “`stripTrailingZeros` luôn giữ scale không âm.” | Nó có thể tạo scale âm và hiển thị số mũ. |
| “`toString` luôn trả chuỗi nhập ban đầu.” | Nó chuẩn hóa biểu diễn; dấu cộng/số 0 đầu có thể mất. |
| “`new BigDecimal("1,23")` tự hiểu locale Việt Nam.” | Không: constructor không tự dùng locale thập phân. |
| “BigDecimal chấp nhận `NaN` hoặc `Infinity` như `double`.” | Không. |
| “Dùng `doubleValue()` là luôn giữ đúng số.” | Chuyển sang `double` có thể làm tròn hoặc tràn miền. |
| “Số học BigDecimal loại bỏ nhu cầu test và chính sách.” | Vẫn cần kiểm tra định dạng, rounding, scale, resource và quy tắc nghiệp vụ. |

## 15. Checklist triển khai trong dự án

1. **Giữ nguyên nguồn dạng chuỗi thập phân:** tránh chuyển sang `double` trước khi xây `BigDecimal`.
2. **Xác minh cú pháp đầu vào:** dấu, dấu thập phân, số mũ, khoảng trắng, locale, tập chữ số cho phép.
3. **Giới hạn đầu vào không tin cậy:** độ dài, số mũ, số chữ số và các giá trị theo miền nghiệp vụ.
4. **Parse bằng `new BigDecimal(text)`** và bắt `NumberFormatException` ở ranh giới.
5. **Chọn đơn vị và scale kỳ vọng:** ví dụ số tiền hai chữ số sau dấu thập phân, không tự giả định mọi loại tiền dùng hai chữ số.
6. **Dùng `add`/`subtract`/`multiply`/`divide`**, luôn gán kết quả mới nếu cần cập nhật.
7. **Chỉ định rounding rõ ràng** ở điểm làm tròn cần thiết; phân biệt scale với precision.
8. **So sánh bằng `compareTo`** nếu mục tiêu là độ lớn số học; sử dụng `equals` khi cả scale cũng là một phần của định danh.
9. **Chọn cách serialize:** `toString` có thể dùng số mũ; `toPlainString` không dùng số mũ, nhưng không tái tạo chính xác token gốc.
10. **Không quay lại `double`** ở các bước cần decimal exactness.
11. **Test các biên:** `"0"`, `"-0.00"`, `"0.1"`, `"1.20"`, số mũ, 0 đứng đầu, dữ liệu lỗi, chia cho 3, rounding ties và scale âm.
12. **Lưu ý API collection:** `HashSet<BigDecimal>` và `TreeSet<BigDecimal>` có hành vi khác nhau khi cùng giá trị nhưng khác scale.

## 16. Câu hỏi tự kiểm tra

1. `new BigDecimal("0.1")` khác `new BigDecimal(0.1)` ở đâu?
2. `BigDecimal.valueOf(0.1)` thường in ra gì? Nó có sửa được kết quả `0.1 + 0.2` đã tính bằng `double` không?
3. `new BigDecimal("1.20").scale()` là bao nhiêu? Còn `precision()`?
4. Hai số `"1.20"` và `"1.2"` có `equals` bằng nhau không? `compareTo`?
5. `new BigDecimal("1").divide(new BigDecimal("3"))` có thể ném lỗi nào?
6. `setScale(2, HALF_UP)` khác `round(new MathContext(2, HALF_UP))` ở ý nghĩa ra sao?
7. Tại sao `new BigDecimal("1_000.50")` không hợp lệ dù `1_000.50` là một kiểu literal Java có thể viết?
8. `new BigDecimal("1.23E+4").toPlainString()` in ra gì?
9. `stripTrailingZeros` có thể thay đổi `scale` như thế nào?
10. Nếu cần lưu đúng nguyên văn `"+0001.2300"` khách nhập, chỉ lưu `BigDecimal` đã đủ không?
11. Khi đọc số tiền trong JSON, nguy cơ gì nếu thư viện parse số thành `double` trước?
12. Vì sao `BigDecimal` không tự quyết định một mức rounding đúng cho mọi hệ thống?

**Đáp án gợi ý:** (1) Constructor `String` giữ chính xác ý nghĩa thập phân, constructor `double` giữ giá trị binary64 xấp xỉ đã có. (2) `0.1`; không, phép cộng `double` trước đó đã tạo `0.30000000000000004`. (3) Scale 2, precision 3. (4) `equals` false, `compareTo` 0. (5) `ArithmeticException` khi phần thập phân không kết thúc mà không chọn rounding. (6) Một cái chọn số chữ số sau dấu chấm, một cái chọn số chữ số có nghĩa. (7) Parser runtime không đọc cú pháp literal Java có underscore. (8) `12300`. (9) Có thể bỏ số 0 cuối và tạo scale âm. (10) Không, phải giữ `String` gốc khi yêu cầu nguyên văn. (11) Mất chính xác thập phân trước khi tạo `BigDecimal`. (12) Rounding là chính sách nghiệp vụ, cần lựa chọn rõ lúc cần.

**Ghi nhớ:** Với **chuỗi số thập phân**, sử dụng **`new BigDecimal(text)` ngay tại ranh giới nhập liệu** để giữ đúng giá trị thập phân. Phân biệt `BigDecimal(String)`, `BigDecimal(double)` và `BigDecimal.valueOf(double)`; hiểu **scale, precision, equals/compareTo, các phép toán và quy tắc làm tròn**. Tránh đưa dữ liệu qua `double` ở giữa nếu bài toán đòi hỏi độ chính xác thập phân.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-007">Vì sao new BigDecimal(double) khác new BigDecimal(String)</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `new BigDecimal(double)` khác `new BigDecimal(String)` trong Java

Khi viết `new BigDecimal(0.1)`, Java **không** nhận trực tiếp số thập phân “một phần mười” chính xác. Literal `0.1` được trình biên dịch xử lý thành một giá trị `double` nhị phân trước, và constructor nhận `double` sẽ chuyển **chính xác giá trị nhị phân đã lưu đó** sang biểu diễn thập phân của `BigDecimal`. Trong khi đó, `new BigDecimal("0.1")` đọc trực tiếp chuỗi thập phân, nên biểu diễn **chính xác 0.1 thập phân**.

Đây không chỉ là khác biệt về cách in: hai đối tượng có thể **khác giá trị toán học**, khác `scale` và dẫn tới kết quả khác khi `equals`, `compareTo`, cộng, trừ hoặc làm tròn.

Bài này thuộc nhóm **Phân tích số thực** của Java Core. Ta sẽ tách rõ ba cách tạo `BigDecimal` — từ `double`, từ `String` và từ `BigDecimal.valueOf(double)` — rồi xem tác động khi parse dữ liệu, làm tròn, tính toán tiền tệ và xử lý đầu vào thực tế.

## 1. Ví dụ mấu chốt: cùng thấy “0.1” nhưng số được lưu khác nhau

~~~java
double d = 0.1;

BigDecimal fromDouble = new BigDecimal(d);
BigDecimal fromString = new BigDecimal("0.1");
BigDecimal fromValueOf = BigDecimal.valueOf(d);

System.out.println(fromDouble);
// 0.1000000000000000055511151231257827021181583404541015625
System.out.println(fromString);
// 0.1
System.out.println(fromValueOf);
// 0.1
~~~

Giải thích chính xác từng bước:

1. Source literal `0.1` được chuyển thành một mẫu bit `double` IEEE 754 binary64. Phân số `1/10` không có biểu diễn hữu hạn trong cơ số 2; mẫu bit `double` chứa **số gần 0.1**, không phải 0.1 toán học chính xác.
2. `new BigDecimal(d)` tạo một số thập phân **đúng bằng giá trị binary64 đã làm tròn** đang nằm trong `d`. Nó không đoán “ý định” ban đầu là 0.1.
3. `new BigDecimal("0.1")` phân tích các chữ số thập phân trong chuỗi `"0.1"`; giá trị bằng chính xác `1/10`.
4. `BigDecimal.valueOf(d)` dùng biểu diễn thập phân qua `Double.toString(d)` rồi tạo `BigDecimal` tương ứng. Với `d = 0.1`, chuỗi đó là `"0.1"`, nên kết quả giống constructor nhận chuỗi ở trường hợp này.

Về số học: giá trị của `new BigDecimal(0.1)` **lớn hơn** `new BigDecimal("0.1")` một lượng xấp xỉ `5.55 × 10^-18`. Dạng `Double.toString(0.1)` chỉ nhằm cho phép đọc lại đúng giá trị `double` nhị phân; nó **không** khẳng định mẫu bit lưu tương ứng đúng chính xác 0.1 thập phân.

Một `double` là giá trị số; `String` là các chữ số do người dùng, file hoặc API truyền vào. Chúng chứa những mức thông tin khác nhau nên constructor có kết quả khác.

## 2. Cơ sở: thập phân và nhị phân biểu diễn phân số khác nhau

Một số thập phân như `0.5` = `1/2` được biểu diễn hữu hạn trong cả cơ số 10 lẫn cơ số 2. Nhưng `0.1` = `1/10` = `1/(2 × 5)` có mẫu số chứa thừa số 5, vì thế biểu diễn nhị phân của nó là một chuỗi vô hạn cần cắt/làm tròn ở binary64.

Kiểu `double` thường giữ khoảng 15–17 chữ số thập phân có nghĩa, với **53 bit precision ở phần trị**. Điều đó đủ cho nhiều ứng dụng khoa học, đồ họa và tính toán, nhưng không làm mọi phân số thập phân trở nên chính xác.

| Giá trị/biểu thức | Kết quả cần hiểu |
| --- | --- |
| `double d = 0.5` | Có thể lưu chính xác `1/2` trong binary64 |
| `double d = 0.1` | Lưu số nhị phân gần 0.1 |
| `System.out.println(0.1)` | In `0.1` — dạng decimal ngắn có khả năng round-trip, không in toàn bộ giá trị toán học của bit |
| `new BigDecimal(0.1)` | Giữ nguyên giá trị của bit `double` và hiện ra chuỗi dài |
| `new BigDecimal("0.1")` | Giữ đúng giá trị thập phân do chuỗi mô tả |
| `BigDecimal.valueOf(0.1)` | Dựa trên `Double.toString(0.1)` nên tạo `BigDecimal` bằng 0.1 trong trường hợp này |

Trong biểu diễn lưu trữ, `BigDecimal` gồm **unscaled integer** (số nguyên tùy ý độ dài) và **scale** (số chữ số thập phân theo lũy thừa mười): giá trị bằng `unscaledValue × 10^(-scale)`. Ví dụ `new BigDecimal("0.1")` có unscaled value bằng 1 và scale bằng 1.

`BigDecimal` **không bị giới hạn bởi 53 bit precision như `double`**, nhưng vẫn cần chính sách scale/precision/rounding khi phép toán tạo kết quả dài hoặc vô hạn, đặc biệt là phép chia.

## 3. Ba cách tạo BigDecimal: hợp đồng không giống nhau

### Cách A — constructor nhận `double`

~~~java
BigDecimal exactStoredDouble = new BigDecimal(0.1);
~~~

Hợp đồng: biểu diễn **chính xác số thực đã lưu trong đối số `double`** dưới dạng thập phân. Constructor này có ích khi bạn cố ý cần điều tra giá trị binary64, lưu lại chính xác nội dung số học đang có hoặc xử lý chuyển đổi bit/số thực theo yêu cầu cụ thể.

Nó thường **không** phù hợp khi bạn muốn tạo giá tiền `0.10` từ literal `0.1`. Vấn đề không phải constructor hoạt động sai: nó làm đúng việc chuyển một số `double` hiện hữu sang `BigDecimal` **mà không làm tròn về ý định thập phân**.

### Cách B — constructor nhận `String`

~~~java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal amount = new BigDecimal("19.99");
BigDecimal measured = new BigDecimal("1.2300");
~~~

Hợp đồng: parse văn bản hợp lệ thành **số thập phân chính xác** và ghi nhận scale theo biểu diễn chuỗi. Ví dụ `"1.2300"` có scale bằng 4, còn `"1.23"` có scale bằng 2, dù chúng bằng nhau về giá trị thập phân.

Nếu dữ liệu đã đến từ JSON/CSV/form người dùng dưới dạng chuỗi decimal đáng tin cậy, **tránh chuyển chuỗi sang `double` trước rồi mới tạo `BigDecimal`**:

~~~java
String input = "19.99";
BigDecimal correct = new BigDecimal(input);

// Không nên dùng nếu cần giữ đúng decimal từ input:
BigDecimal potentiallyDifferent =
        new BigDecimal(Double.parseDouble(input));
~~~

Cách thứ hai đã đi qua một số binary64 xấp xỉ trước khi constructor nhận `double` được gọi. Lúc này `BigDecimal` chỉ có thể giữ chính xác **giá trị sau bước xấp xỉ**, không biết người dùng định nhập số thập phân nào.

### Cách C — factory `BigDecimal.valueOf(double)`

~~~java
double d = 0.1;
BigDecimal commonConversion = BigDecimal.valueOf(d);
~~~

Hợp đồng: dùng dạng chuỗi canonical của `Double.toString(d)` để tạo `BigDecimal`. Với `d = 0.1`, nó tạo số `0.1`; nhờ đó giảm nhiều chữ số “đuôi” không mong đợi khi đổi một giá trị `double` hiện hữu sang decimal.

**Nhưng `valueOf` không tự sửa mọi sai số đã tích lũy:**

~~~java
double sum = 0.1 + 0.2;             // 0.30000000000000004
BigDecimal fromSum = BigDecimal.valueOf(sum);
// fromSum là 0.30000000000000004, không phải 0.3
~~~

`Double.toString(sum)` chính là `"0.30000000000000004"`, vì đó là dạng cần thiết để round-trip mẫu bit của tổng. Factory không thể suy ra phép tính ban đầu “đáng lẽ bằng 0.3”.

**Khuyến nghị theo ý định:** Dữ liệu decimal chính xác mới nên bắt đầu bằng `String`/`char[]` phù hợp hoặc nguồn `BigDecimal`. Khi đã có `double` và cần chuyển sang `BigDecimal` bằng dạng decimal ngắn tương ứng, `BigDecimal.valueOf` thường thuận tiện hơn constructor nhận `double`. Khi cần chính xác giá trị binary64 hiện tại, constructor nhận `double` mới là lựa chọn chủ đích.

## 4. Vì sao `equals` và `compareTo` có thể đưa ra kết quả khác?

Với `0.1`:

~~~java
BigDecimal a = new BigDecimal(0.1);
BigDecimal b = new BigDecimal("0.1");

boolean sameByEquals = a.equals(b);      // false
int numericOrder = a.compareTo(b);       // > 0
~~~

`a` thật sự **lớn hơn b về giá trị**, vì `double 0.1` được làm tròn hơi lớn hơn `1/10`. Do đó cả `equals` lẫn `compareTo == 0` đều không xem chúng bằng nhau.

Một vấn đề **khác** là scale:

~~~java
BigDecimal x = new BigDecimal("1.0");
BigDecimal y = new BigDecimal("1.00");

boolean equalObjects = x.equals(y);    // false: scale 1 khác scale 2
boolean equalValues = x.compareTo(y) == 0; // true: giá trị toán học bằng nhau
~~~

- `BigDecimal.equals` xem hai số bằng nhau khi **giá trị và scale** thích hợp khớp nhau. Đây là object equality quan trọng với `HashMap`/`HashSet`.
- `BigDecimal.compareTo` so sánh **giá trị số** và xem `1.0` và `1.00` bằng nhau.
- `stripTrailingZeros` có thể chuẩn hóa một phần biểu diễn decimal (`new BigDecimal("1.00").stripTrailingZeros()` in `1`), nhưng việc thay đổi scale không sửa được sự khác nhau thực về giá trị giữa `new BigDecimal(0.1)` và `new BigDecimal("0.1")`.

Nếu dùng `BigDecimal` làm khóa, hãy quyết định hợp đồng **scale có ý nghĩa hay không**. `HashSet<BigDecimal>` dựa trên `equals`, còn `TreeSet<BigDecimal>` dùng `compareTo` mặc định; hai tập có thể xử lý `1.0` và `1.00` khác nhau. Đây không phải lỗi của constructor mà là kết hợp giữa *giá trị* và *đại diện decimal*.

## 5. Làm tròn: sai khác khi khởi tạo có thể thành sai khác nghiệp vụ

Ví dụ kinh điển là `1.005` làm tròn đến hai chữ số thập phân theo `RoundingMode.HALF_UP`:

~~~java
BigDecimal a = new BigDecimal(1.005);
BigDecimal b = new BigDecimal("1.005");
BigDecimal c = BigDecimal.valueOf(1.005);

System.out.println(a.setScale(2, RoundingMode.HALF_UP)); // 1.00
System.out.println(b.setScale(2, RoundingMode.HALF_UP)); // 1.01
System.out.println(c.setScale(2, RoundingMode.HALF_UP)); // 1.01
~~~

Vì giá trị binary64 của literal `1.005` **hơi nhỏ hơn 1.005 thập phân**, constructor nhận `double` giữ đúng giá trị hơi nhỏ đó, nên làm tròn HALF_UP tới hai chữ số cho `1.00`. Với constructor nhận chuỗi, giá trị đúng ở điểm giữa `1.005` nên HALF_UP cho `1.01`.

Đây là lý do cùng một UI hiển thị `1.005` nhưng thuật toán tính giá/thuế có thể cho ra kết quả khác khi đi qua `double`. **Số chữ số hiển thị không chứng minh được tính chính xác hoặc chính sách làm tròn.**

Các phương thức `setScale` trả về **`BigDecimal` mới**, vì `BigDecimal` là immutable. Bạn phải dùng kết quả trả về, thay vì nghĩ rằng gọi phương thức sẽ thay đổi object cũ.

`RoundingMode.HALF_UP` chỉ là một chính sách. Các nghiệp vụ khác có thể yêu cầu `HALF_EVEN`, `DOWN`, `UP` hoặc cách xử lý đặc biệt; hãy định nghĩa rõ ở cấp nghiệp vụ.

## 6. Chương trình Java 17+ hoàn chỉnh để đối chiếu

Lưu tệp `BigDecimalConstructionDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalConstructionDemo {
    public static void main(String[] args) {
        double d = 0.1;
        BigDecimal fromDouble = new BigDecimal(d);
        BigDecimal fromString = new BigDecimal("0.1");
        BigDecimal fromValueOf = BigDecimal.valueOf(d);

        System.out.println("double d = " + d);
        System.out.println("new BigDecimal(d) = " + fromDouble);
        System.out.println("new BigDecimal(\"0.1\") = " + fromString);
        System.out.println("BigDecimal.valueOf(d) = " + fromValueOf);
        System.out.println("double constructor equals string = "
                + fromDouble.equals(fromString));
        System.out.println("valueOf equals string = "
                + fromValueOf.equals(fromString));
        System.out.println("double constructor compareTo string = "
                + fromDouble.compareTo(fromString));
        System.out.println("double constructor scale = " + fromDouble.scale());
        System.out.println("string constructor scale = " + fromString.scale());

        System.out.println("sum as double = " + (0.1 + 0.2));
        System.out.println("valueOf(double sum) = "
                + BigDecimal.valueOf(0.1 + 0.2));
        System.out.println("decimal string sum = "
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));

        System.out.println("new BigDecimal(1.005) rounded = "
                + new BigDecimal(1.005)
                .setScale(2, RoundingMode.HALF_UP));
        System.out.println("new BigDecimal(\"1.005\") rounded = "
                + new BigDecimal("1.005")
                .setScale(2, RoundingMode.HALF_UP));
        System.out.println("BigDecimal.valueOf(1.005) rounded = "
                + BigDecimal.valueOf(1.005)
                .setScale(2, RoundingMode.HALF_UP));

        System.out.println("equals 1.0 and 1.00 = "
                + new BigDecimal("1.0")
                .equals(new BigDecimal("1.00")));
        System.out.println("compareTo 1.0 and 1.00 = "
                + new BigDecimal("1.0")
                .compareTo(new BigDecimal("1.00")));
        System.out.println("stripTrailingZeros 1.00 = "
                + new BigDecimal("1.00").stripTrailingZeros());

        System.out.println("BigDecimal.valueOf(-0.0) = "
                + BigDecimal.valueOf(-0.0));
        System.out.println("new BigDecimal(\"-0.0\") = "
                + new BigDecimal("-0.0"));

        try {
            new BigDecimal(Double.NaN);
        } catch (NumberFormatException ex) {
            System.out.println("NaN constructor = NumberFormatException");
        }
        try {
            BigDecimal.valueOf(Double.POSITIVE_INFINITY);
        } catch (NumberFormatException ex) {
            System.out.println("Infinity valueOf = NumberFormatException");
        }
        try {
            new BigDecimal("0.1").divide(new BigDecimal("3"));
        } catch (ArithmeticException ex) {
            System.out.println("0.1 / 3 exact = ArithmeticException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalConstructionDemo.java
java BigDecimalConstructionDemo
~~~

Stdout chính xác của chương trình:

~~~text
double d = 0.1
new BigDecimal(d) = 0.1000000000000000055511151231257827021181583404541015625
new BigDecimal("0.1") = 0.1
BigDecimal.valueOf(d) = 0.1
double constructor equals string = false
valueOf equals string = true
double constructor compareTo string = 1
double constructor scale = 55
string constructor scale = 1
sum as double = 0.30000000000000004
valueOf(double sum) = 0.30000000000000004
decimal string sum = 0.3
new BigDecimal(1.005) rounded = 1.00
new BigDecimal("1.005") rounded = 1.01
BigDecimal.valueOf(1.005) rounded = 1.01
equals 1.0 and 1.00 = false
compareTo 1.0 and 1.00 = 0
stripTrailingZeros 1.00 = 1
BigDecimal.valueOf(-0.0) = 0.0
new BigDecimal("-0.0") = 0.0
NaN constructor = NumberFormatException
Infinity valueOf = NumberFormatException
0.1 / 3 exact = ArithmeticException
~~~

Một số ý nghĩa nổi bật từ kết quả:

- `fromDouble.scale() == 55` trong khi `fromString.scale() == 1`; đó là hai biểu diễn decimal khác hẳn nhau.
- `BigDecimal.valueOf(0.1)` và `new BigDecimal("0.1")` bằng nhau trong ví dụ, **nhưng đừng mở rộng thành kết luận `valueOf(double)` luôn khôi phục decimal mong muốn của mọi phép tính**.
- `0.1 + 0.2` trên `double` thành `0.30000000000000004` trước khi chuyển sang `BigDecimal`; không có constructor nào tự suy ngược ý định phép cộng decimal.
- Hai phép làm tròn `1.005` trả kết quả khác nhau vì dữ liệu đầu vào đã khác nhau.
- `BigDecimal` không hỗ trợ các giá trị NaN và infinity như `double`. Chuyển NaN hoặc vô cực qua `BigDecimal` không tạo một số hợp lệ, mà phát sinh `NumberFormatException` trong ví dụ.
- `BigDecimal` biểu diễn số **không giữ dấu âm riêng cho giá trị 0**: `"-0.0"` khi parse cho ra giá trị 0 với scale 1, in `0.0`.

## 7. `valueOf(double)` không phải “thuốc chữa” sai số số học

Xét hai quy trình:

~~~java
double computed = 0.1 + 0.2;
BigDecimal convertedLater = BigDecimal.valueOf(computed);

BigDecimal computedAsDecimal = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));
~~~

Kết quả `convertedLater` là `0.30000000000000004`, còn `computedAsDecimal` là `0.3` chính xác. Không thể lấy kết quả của phép cộng đã làm tròn trong binary64 rồi giả định factory biết chính xác hai số thập phân ban đầu.

Cách làm tốt cho dữ liệu decimal có hợp đồng tính toán chặt chẽ:

1. Nhận số thập phân dưới dạng chuỗi gốc hoặc `BigDecimal` ngay tại lớp input; nếu là chuỗi, xác thực định dạng trước khi tạo object.
2. Tiến hành tính toán bằng các phép `add`, `subtract`, `multiply`, `divide` của `BigDecimal`, không chuyển qua `double` giữa chừng.
3. Chỉ định precision/scale và `RoundingMode` ở đúng điểm nghiệp vụ cần lượng tử hóa.
4. Xuất bằng `toPlainString`, `toString`, formatter hoặc serialize theo hợp đồng bên nhận; không phụ thuộc số chữ số ngẫu nhiên của `double`.

Nếu nguồn **chỉ cung cấp `double`**, bạn không thể biết chắc giá trị decimal con người dự định từ bit đã lưu. `BigDecimal.valueOf(d)` thường là chuyển đổi hợp lý dựa trên chuỗi round-trip của `double`; `new BigDecimal(d)` là lựa chọn khi muốn giữ giá trị nhị phân hiện có chính xác tuyệt đối. Phải ghi rõ chính sách thay vì che giấu nguồn gốc dữ liệu.

## 8. Những điều cần lưu ý khi parse String thành BigDecimal

`new BigDecimal(String)` đòi chuỗi có cú pháp số thập phân hợp lệ, không phải bất kỳ chuỗi hiển thị số nào. Ví dụ:

~~~java
BigDecimal ok = new BigDecimal("-12.3400");
BigDecimal scientific = new BigDecimal("1.25E+3");

// new BigDecimal("12,34"); // NumberFormatException
// new BigDecimal("1_000"); // NumberFormatException
// new BigDecimal("");      // NumberFormatException
~~~

Trong mã Java, `1_000` là literal nguyên hợp lệ; trong văn bản runtime đưa vào constructor `BigDecimal(String)`, dấu gạch dưới **không** là ký tự số hợp lệ. Chuỗi chứa dấu phân cách hàng nghìn hoặc thập phân tùy ngôn ngữ cần được xử lý theo **hợp đồng locale** riêng trước khi gọi parser, không tự ý xóa mọi dấu phẩy.

Khác với `double`, `BigDecimal` có thể biểu diễn thập phân rất lớn hoặc rất nhỏ với độ dài tùy ý (trong giới hạn tài nguyên và giới hạn scale/precision nội tại). Tuy vậy constructor vẫn có thể ném `NumberFormatException` khi dữ liệu sai cú pháp hoặc vượt giới hạn cấu trúc có thể xử lý.

Nên xác thực input, bắt ngoại lệ ở ranh giới giao tiếp và đưa ra thông báo nghiệp vụ phù hợp, thay vì nuốt lỗi rồi thay bằng 0. Với dữ liệu chứa thông tin nhạy cảm, không log nguyên văn toàn bộ chuỗi nếu không cần thiết.

## 9. Tính toán BigDecimal vẫn phải thiết kế precision và rounding

`BigDecimal` giúp phép cộng/trừ/nhân decimal hữu hạn diễn ra chính xác nếu không giới hạn precision một cách chủ đích; **nó không làm mọi phép chia có kết quả decimal hữu hạn**.

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// one.divide(three); // ArithmeticException: khai triển thập phân vô hạn
BigDecimal approx = one.divide(three, 4, RoundingMode.HALF_UP);
// approx là 0.3333
~~~

Nếu bạn dùng `MathContext`, phép toán có thể được làm tròn theo precision yêu cầu:

~~~java
java.math.MathContext context =
        new java.math.MathContext(4, RoundingMode.HALF_EVEN);
BigDecimal roundedByPrecision =
        new BigDecimal("123.456").round(context);
// 123.5 (4 chữ số có nghĩa)
~~~

**Phân biệt:**

- `setScale(2, ...)` chủ yếu điều chỉnh số chữ số theo scale, thường liên quan chữ số sau dấu thập phân.
- `MathContext(4, ...)` quy định **4 chữ số có nghĩa**, không phải bốn chữ số sau dấu thập phân.
- `round`/`setScale` trả đối tượng mới; `BigDecimal` là immutable.
- `RoundingMode.UNNECESSARY` yêu cầu không được làm tròn; nếu số liệu cần làm tròn thì có thể ném `ArithmeticException`. Đây là một cách phát hiện phép làm tròn ngoài dự kiến.

Đối với tiền tệ, ngoài cách tạo `BigDecimal` từ chuỗi, bạn còn phải định nghĩa thời điểm làm tròn (mỗi dòng hay sau tổng), scale, rounding mode, loại tiền và quy tắc đối soát. Constructor đúng là **điều kiện cần**, không phải bảo đảm toàn bộ hệ thống tính tiền chính xác.

## 10. Số nguyên và constructor không bị vấn đề thập phân này giống double

Nếu nguồn thật sự là số nguyên chính xác trong `int` hoặc `long`, có thể tạo `BigDecimal` trực tiếp:

~~~java
BigDecimal units = BigDecimal.valueOf(42L);
BigDecimal alsoUnits = new BigDecimal("42");
~~~

Vì `long` biểu diễn mọi số nguyên trong miền của nó một cách chính xác, `BigDecimal.valueOf(long)` là lựa chọn thẳng và ít gây bất ngờ. `BigDecimal.valueOf(long)` và `BigDecimal.valueOf(double)` là **hai overload khác nhau**, không nên đánh đồng cơ chế của chúng.

Với số nguyên lớn vượt `long`, `BigInteger` và constructor `BigDecimal(BigInteger)` giúp giữ giá trị chính xác:

~~~java
java.math.BigInteger huge =
        new java.math.BigInteger("9223372036854775808");
BigDecimal exactHuge = new BigDecimal(huge);
~~~

Không nên ép `BigInteger` sang `double` rồi tạo `BigDecimal`, vì phép chuyển có thể mất bit độ chính xác hoặc tràn thành vô cực.

## 11. Những sai lầm phổ biến

1. **“`new BigDecimal(0.1)` chính xác hơn `new BigDecimal("0.1")` nên luôn tốt hơn.”** Nó chính xác **đối với số `double` đã làm tròn**, không chính xác theo ý định decimal `1/10`.
2. **“In `double` ra `0.1` nghĩa là bên trong lưu đúng 0.1.”** `Double.toString` tạo chuỗi ngắn có khả năng round-trip; nó không hiển thị tất cả chữ số decimal của giá trị bit đã lưu.
3. **“`BigDecimal.valueOf` luôn xóa sai số tính toán.”** Nó chỉ dùng `Double.toString` của **giá trị đang có**; `0.1 + 0.2` vẫn là `0.30000000000000004`.
4. **“Hai BigDecimal in gần giống nhau thì `equals` đúng.”** Phải xét cả giá trị lẫn scale; `1.0` và `1.00` không bằng nhau theo `equals` nhưng `compareTo == 0`.
5. **“`setScale(2)` làm tròn âm thầm mà không cần policy.”** Nếu phải bỏ chữ số và không chỉ định rounding mode, phép tính có thể ném `ArithmeticException`. Dùng `setScale(2, mode)` với chính sách rõ.
6. **“Đưa NaN/Infinity vào BigDecimal cũng được vì đều là số.”** `BigDecimal` không biểu diễn NaN hoặc vô cực; conversion phát sinh lỗi.
7. **“Tạo BigDecimal từ `double` rồi xử lý tiền là đủ.”** Nếu input đã qua phép tính binary64, decimal gốc có thể không phục hồi được. Hãy giữ dữ liệu decimal ngay từ nguồn.
8. **“Làm tròn ở bước hiển thị là đã làm tròn số tiền thực sự.”** Formatter chỉ xuất chuỗi. Cần lượng tử hóa giá trị nghiệp vụ và lưu kết quả theo chính sách.
9. **“Cứ dùng epsilon để làm BigDecimal.equals gần đúng.”** `BigDecimal` được dùng để mô hình hóa số decimal và scale cụ thể; nếu nghiệp vụ cần so sánh gần đúng thì phải định nghĩa quy tắc riêng, không phá hợp đồng equality/hash.
10. **“Chuỗi người dùng giống số là constructor luôn chấp nhận.”** Định dạng nhóm số, locale, ký tự không hợp lệ hoặc thiếu giá trị vẫn cần validation và xử lý `NumberFormatException`.

## 12. Hướng dẫn lựa chọn nhanh

| Nguồn dữ liệu / mục đích | Cách ưu tiên | Vì sao? |
| --- | --- | --- |
| Chuỗi decimal gốc `"0.10"` | `new BigDecimal("0.10")` | Giữ đúng giá trị và scale được nhập |
| Literal decimal cho tính tiền trong source | `new BigDecimal("19.99")` | Không đi qua binary64 |
| Đã có `double` và muốn decimal ngắn theo round-trip | `BigDecimal.valueOf(double)` | Dựa trên `Double.toString` |
| Đã có `double` và cần chính xác decimal tương ứng mẫu bit | `new BigDecimal(double)` | Giữ chính xác giá trị binary64 |
| Số nguyên `long` chính xác | `BigDecimal.valueOf(long)` | Không có sai số phân số của binary64 |
| Số nguyên rất lớn `BigInteger` | `new BigDecimal(bigInteger)` | Giữ số nguyên ngoài miền long |
| Thập phân cần làm tròn đến 2 chữ số | `setScale(2, RoundingMode.…)` | Quy định rõ phép lượng tử hóa |
| So sánh hai BigDecimal theo giá trị số | `compareTo(...) == 0` | Không phụ thuộc scale |
| So sánh BigDecimal theo object representation | `equals(...)` | Tính cả scale |
| Đầu vào NaN/Infinity | Xử lý trước khi tạo BigDecimal | Kiểu decimal này không biểu diễn chúng |

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `new BigDecimal(0.1)` in ra nhiều chữ số hơn `new BigDecimal("0.1")`?

**Đáp án:** Literal `0.1` đã bị làm tròn thành giá trị `double` nhị phân; constructor nhận `double` biểu diễn chính xác giá trị xấp xỉ đó dưới dạng decimal. Constructor nhận `String` biểu diễn trực tiếp số decimal chính xác.

**Câu 2.** `BigDecimal.valueOf(0.1)` có bằng `new BigDecimal("0.1")` không? Có phải `BigDecimal.valueOf(0.1 + 0.2)` cũng bằng `new BigDecimal("0.3")` không?

**Đáp án:** Ở trường hợp đầu có. Trường hợp sau không: phép cộng `double` cho `0.30000000000000004`, và factory giữ dạng chuỗi round-trip của kết quả ấy.

**Câu 3.** Tại sao `new BigDecimal(1.005).setScale(2, HALF_UP)` khác kết quả constructor nhận chuỗi?

**Đáp án:** Giá trị binary64 được lưu nhỏ hơn 1.005 thập phân, nên constructor nhận `double` làm tròn ra `1.00`; constructor nhận `"1.005"` giữ đúng điểm giữa thập phân và HALF_UP cho `1.01`.

**Câu 4.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` trả gì? `compareTo` có trả 0 không?

**Đáp án:** `equals` trả false vì scale khác; `compareTo` trả 0 vì giá trị decimal bằng nhau.

**Câu 5.** Vì sao chuyển JSON decimal `"19.99"` sang `double` trước khi tạo `BigDecimal` là rủi ro?

**Đáp án:** Việc parse thành `double` có thể làm mất sự chính xác thập phân và thông tin scale gốc. Nếu cần decimal chính xác, parse trực tiếp dữ liệu chuỗi thành `BigDecimal`.

**Câu 6.** Có nên dùng `new BigDecimal(Double.NaN)` hoặc kỳ vọng `BigDecimal.ONE.divide(new BigDecimal("3"))` trả số thập phân vô hạn không?

**Đáp án:** Không. `BigDecimal` không hỗ trợ NaN nên constructor đó ném `NumberFormatException`; phép chia có kết quả thập phân vô hạn mà không có chính sách làm tròn ném `ArithmeticException`.

## Tài liệu tham khảo

- [Java SE 21 — BigDecimal: constructor(double), constructor(String), valueOf, scale, rounding](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).
- [Java SE 21 — Double.toString và IEEE 754](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html).
- [Java SE 21 — MathContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html) và [RoundingMode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html).
- [Java Language Specification `4.2.3 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-008">BigDecimal.valueOf(double) và giới hạn của giá trị double nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.valueOf(double) và giới hạn của giá trị double nguồn trong Java

## 1. Vấn đề cốt lõi: chuyển kiểu không thể phục hồi thông tin đã mất

`BigDecimal.valueOf(double)` là cách thường được khuyến nghị khi bạn **đã có một giá trị `double`** và muốn tạo `BigDecimal` có biểu diễn thập phân ngắn, dễ hiểu. Nhưng phương thức này **không biến một phép tính `double` sai số thành phép tính decimal chính xác**, cũng không thể phục hồi chữ số đã bị làm tròn trước đó.

Ba câu lệnh trông giống nhau nhưng có ngữ nghĩa khác nhau:

```java
import java.math.BigDecimal;

BigDecimal fromText = new BigDecimal("0.1");
BigDecimal viaValueOf = BigDecimal.valueOf(0.1);
BigDecimal fromDoubleConstructor = new BigDecimal(0.1);
```

- `new BigDecimal("0.1")` đọc **chuỗi thập phân gốc**, lưu giá trị 0.1 chính xác.
- `BigDecimal.valueOf(0.1)` trước hết nhận **literal `double`** gần 0.1, sau đó sử dụng dạng chuỗi mà `Double.toString(0.1)` tạo ra, trong trường hợp này là `"0.1"`, để tạo `BigDecimal` có giá trị decimal 0.1.
- `new BigDecimal(0.1)` biểu diễn **chính xác giá trị nhị phân đang lưu trong `double`** dưới dạng decimal. Vì giá trị ấy không phải đúng 1/10, đối tượng thu được không bằng decimal 0.1.

Lớp `BigDecimal` không gây ra sai số nguồn. Sai số phát sinh **trước khi gọi** khi ta viết, parse, tính toán hoặc ép hẹp một giá trị thành `double`.

**Nguyên tắc học tập:** phải phân biệt **giá trị decimal gốc**, **giá trị binary64 thực sự được lưu**, và **chuỗi decimal ngắn dùng để biểu diễn lại giá trị binary64**. Ba thứ này có thể khác nhau.

## 2. Nhắc lại: `double` lưu số thực nhị phân như thế nào?

Kiểu `double` của Java là số dấu phẩy động nhị phân 64 bit, theo định dạng IEEE 754 binary64:

| Thành phần | Số bit | Ý nghĩa |
|---|---:|---|
| Dấu | 1 | Dương hoặc âm |
| Số mũ | 11 | Quy mô giá trị theo lũy thừa của 2 |
| Phần phân số lưu trữ | 52 | Các bit của phần trị |
| Độ chính xác phần trị hiệu dụng | 53 | Có thêm bit dẫn đầu ẩn ở số chuẩn hóa |

`double` cung cấp khoảng **15–17 chữ số thập phân có nghĩa**, nhưng không có nghĩa mọi số thập phân với 15 chữ số đều chính xác. Những phân số như 1/10 thường có biểu diễn nhị phân vô hạn, nên Java phải lưu một **giá trị nhị phân gần đúng**.

Ví dụ:

```java
double x = 0.1;
double y = 0.2;
double sum = x + y;

System.out.println(sum); // 0.30000000000000004
```

Dù toán học thập phân cho 0.1 + 0.2 = 0.3, các toán hạng đã được làm tròn thành binary64 và phép cộng cũng chịu làm tròn. Vì thế giá trị `sum` đang lưu tương ứng với biểu diễn in ra là `0.30000000000000004`.

Ngay lúc này, chuyển `sum` thành `BigDecimal` **không thể tự đoán** người viết mong 0.3 hay mong chính xác 0.30000000000000004. Nó chỉ biết giá trị `double` được truyền vào.

## 3. `BigDecimal.valueOf(double)` thực sự làm gì?

Theo hợp đồng API, `BigDecimal.valueOf(double)` dùng biểu diễn thập phân chuẩn do `Double.toString(double)` cung cấp, tương đương về **giá trị và cách đọc chuỗi** với cách gọi:

```java
double source = 0.1;

BigDecimal viaValueOf = BigDecimal.valueOf(source);
BigDecimal viaText =
        new BigDecimal(Double.toString(source));
```

Trong trường hợp này, cả hai đối tượng đều có giá trị decimal **0.1** và scale 1.

`Double.toString` tạo ra chuỗi thập phân chuẩn có thể **round-trip** về cùng giá trị `double` (với các quy tắc đặc biệt cho số 0, NaN và vô cực). Nó **không in toàn bộ khai triển decimal chính xác của mẫu bit binary64** trong mọi trường hợp.

Ví dụ:

```java
System.out.println(Double.toString(0.1)); // 0.1
System.out.println(BigDecimal.valueOf(0.1)); // 0.1
```

Nhưng:

```java
double sum = 0.1 + 0.2;
System.out.println(Double.toString(sum));
// 0.30000000000000004

System.out.println(BigDecimal.valueOf(sum));
// 0.30000000000000004
```

Sự khác biệt này là **kết quả có sẵn từ giá trị nguồn**, không phải một lựa chọn tự động “sửa lỗi” theo trực giác của `BigDecimal`.

### Câu hỏi thường gặp: `valueOf` có phải phép chuyển “chính xác” không?

Phải xác định **“chính xác so với cái gì?”**:

1. **Chính xác so với chuỗi `Double.toString(source)`:** có. `valueOf` tạo decimal đúng với chuỗi ấy.
2. **Chính xác với số thập phân gốc mà người dùng nhập trước khi chuyển thành `double`:** không bảo đảm.
3. **Chính xác với mọi bit của giá trị binary64 khi khai triển thành phân số decimal:** không. Muốn khai triển decimal chính xác của giá trị binary64, constructor `new BigDecimal(double)` có ngữ nghĩa đó.
4. **Chính xác với kết quả toán học lý tưởng trước các phép toán `double`:** không thể khẳng định.

Vì vậy `valueOf` hữu ích, nhưng nó là **chuyển đổi từ trạng thái hiện có**, không phải cơ chế khôi phục lịch sử đầu vào.

## 4. Ba cách tạo `BigDecimal` và điểm khác biệt

| Cách tạo | Nguồn ý nghĩa | Kết quả với ví dụ 0.1 |
|---|---|---|
| `new BigDecimal("0.1")` | Chuỗi decimal chính xác | Giá trị 0.1, scale 1 |
| `BigDecimal.valueOf(0.1)` | Decimal chuẩn từ `Double.toString` của `double` | Giá trị 0.1, scale 1 |
| `new BigDecimal(0.1)` | Khai triển chính xác giá trị binary64 đã được lưu | Decimal dài hơn, không bằng đúng 0.1 |
| `BigDecimal.valueOf(1L)` | Số nguyên `long` | Số nguyên 1 chính xác |
| `BigDecimal.valueOf(12345L, 2)` | Số nguyên không chia tỷ lệ + scale | 123.45 chính xác |

Biểu diễn decimal chính xác của mẫu binary64 gần 0.1 là:

```text
0.1000000000000000055511151231257827021181583404541015625
```

Đây là giá trị của `new BigDecimal(0.1)`, **không phải** chuỗi mà `Double.toString(0.1)` in ra.

### Phân biệt overload `valueOf(double)` và `valueOf(long)`

`BigDecimal.valueOf(123L)` nhận một **số nguyên `long`** nên có thể giữ chính xác mọi giá trị `long`. Đừng ép số nguyên lớn qua `double` trước khi tạo decimal:

```java
long original = 9_007_199_254_740_993L;

BigDecimal exact = BigDecimal.valueOf(original);
BigDecimal potentiallyRounded =
        BigDecimal.valueOf((double) original);
```

`original` vượt vùng mà mọi số nguyên liên tiếp đều biểu diễn chính xác dưới dạng binary64, nên đường qua `double` đã mất thông tin.

Ngoài ra, `BigDecimal.valueOf(unscaledValue, scale)` rất hữu ích để tạo số decimal chính xác từ đơn vị nhỏ nhất, ví dụ số xu, mà không đi qua số thực nhị phân.

## 5. Ví dụ Java 17+ số 1: thấy rõ ba cách tạo

Tạo tệp `ValueOfBasicsDemo.java`:

```java
import java.math.BigDecimal;

public class ValueOfBasicsDemo {
    public static void main(String[] args) {
        BigDecimal fromText = new BigDecimal("0.1");
        BigDecimal valueOf = BigDecimal.valueOf(0.1);
        BigDecimal fromBinary = new BigDecimal(0.1);

        System.out.println("text: " + fromText.toPlainString());
        System.out.println("valueOf: " + valueOf.toPlainString());
        System.out.println("binary exact: "
                + fromBinary.toPlainString());

        System.out.println("text equals valueOf: "
                + fromText.equals(valueOf));
        System.out.println("text equals binary: "
                + fromText.equals(fromBinary));

        double sum = 0.1 + 0.2;
        System.out.println("double sum: " + sum);
        System.out.println("valueOf(sum): "
                + BigDecimal.valueOf(sum).toPlainString());
        System.out.println("decimal sum: "
                + new BigDecimal("0.1")
                        .add(new BigDecimal("0.2"))
                        .toPlainString());

        System.out.println("valueOf(12345, 2): "
                + BigDecimal.valueOf(12345L, 2).toPlainString());
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac ValueOfBasicsDemo.java
java ValueOfBasicsDemo
```

Kết quả:

```text
text: 0.1
valueOf: 0.1
binary exact: 0.1000000000000000055511151231257827021181583404541015625
text equals valueOf: true
text equals binary: false
double sum: 0.30000000000000004
valueOf(sum): 0.30000000000000004
decimal sum: 0.3
valueOf(12345, 2): 123.45
```

Trong ví dụ, `new BigDecimal("0.1")` và `BigDecimal.valueOf(0.1)` bằng nhau, nhưng điều đó chỉ xảy ra vì chuỗi chuẩn của giá trị binary64 gần 0.1 là `"0.1"`. Đừng suy rộng rằng `valueOf` luôn hồi phục được chuỗi thập phân gốc hoặc tự động làm tròn về “giá trị dễ nhìn”.

## 6. Sai số do **phép tính `double` đã hoàn tất** trước khi gọi `valueOf`

Đây là giới hạn quan trọng nhất.

So sánh hai luồng dữ liệu:

**Luồng A — decimal chính xác ngay từ đầu:**

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal total = a.add(b); // 0.3
```

**Luồng B — tính bằng binary64 rồi mới chuyển:**

```java
double a = 0.1;
double b = 0.2;
double total = a + b;
BigDecimal converted = BigDecimal.valueOf(total);
// 0.30000000000000004
```

Ở luồng B, phép cộng đã chạy với quy tắc floating-point. `valueOf` không biết nguồn ban đầu của hai số là literal decimal 0.1 và 0.2, cũng không được thông báo rằng nghiệp vụ muốn 1 chữ số thập phân.

Tương tự:

```java
double third = 1.0 / 3.0;
BigDecimal saved = BigDecimal.valueOf(third);
// 0.3333333333333333
```

`saved` là decimal hữu hạn với 16 chữ số 3 sau dấu chấm. **Không thể coi nó là biểu diễn chính xác phân số toán học 1/3**, vì phân số đó có khai triển decimal vô hạn.

### “Chuyển sang `BigDecimal` rồi tính tiếp” có hữu ích không?

Có, nếu bạn hiểu ranh giới độ chính xác:

- Các phép toán `BigDecimal` **sau thời điểm chuyển** có thể sử dụng decimal của đối tượng và chính sách làm tròn do bạn xác định.
- **Sai số đã có trong giá trị `double` đầu vào sẽ đi theo** trong dữ liệu `BigDecimal` được tạo từ đó.
- Nếu còn chuỗi decimal gốc hoặc dữ liệu nguyên chính xác, **nên tạo `BigDecimal` trực tiếp từ nguồn đó** thay vì qua `double`.

Không nên gọi `setScale(2, ...)` tùy ý rồi tuyên bố “đã sửa hoàn toàn sai số”. Làm tròn có thể đúng nếu nghiệp vụ quy định 2 chữ số, nhưng đó là **một quyết định nghiệp vụ**, không phải bằng chứng giá trị gốc đã phục hồi.

## 7. Mất số nguyên lớn: giới hạn 53 bit phần trị

Binary64 có 53 bit độ chính xác phần trị. Mọi số nguyên có trị tuyệt đối không quá \(2^{53}\) được biểu diễn chính xác, nhưng **không phải mọi số nguyên liên tiếp lớn hơn giới hạn ấy** đều chính xác.

Giá trị:

\[
2^{53} = 9\,007\,199\,254\,740\,992
\]

là một số nguyên được biểu diễn chính xác. Số kế tiếp \(2^{53}+1\) **không** được biểu diễn chính xác bằng `double` và sẽ bị làm tròn.

Ví dụ:

```java
long original = 9_007_199_254_740_993L;
double rounded = (double) original;

BigDecimal wrong = BigDecimal.valueOf(rounded);
BigDecimal right = BigDecimal.valueOf(original);
```

`wrong` chứa 9_007_199_254_740_992, còn `right` chứa đúng 9_007_199_254_740_993.

**Bài học cho hệ thống:** không chuyển ID số nguyên lớn, số xu, bộ đếm, timestamp nanosecond hoặc số nguyên quan trọng sang `double` chỉ để dùng `BigDecimal.valueOf` sau đó. Nếu nguồn là `long`, hãy gọi trực tiếp `BigDecimal.valueOf(long)`.

### JavaScript/JSON và API trung gian

Một hệ thống khác có thể gửi số JSON qua runtime dùng number nhị phân 64 bit. Nếu dữ liệu đã bị làm tròn ở một bên, server Java **không thể khôi phục chữ số bằng `BigDecimal.valueOf`**. Đối với ID hoặc số tiền chính xác, cần quy định kiểu truyền phù hợp như chuỗi decimal hoặc đối tượng có số nguyên/đơn vị rõ ràng.

Điểm mấu chốt là **tính chính xác đầu-cuối**: Java chỉ bảo toàn được dữ liệu gốc nếu các bước trước đó cũng bảo toàn nó.

## 8. Ví dụ Java 17+ số 2: mất số nguyên và chữ số decimal gốc

Tạo tệp `SourcePrecisionDemo.java`:

```java
import java.math.BigDecimal;

public class SourcePrecisionDemo {
    public static void main(String[] args) {
        long exactId = 9_007_199_254_740_993L;
        double roundedId = (double) exactId;

        System.out.println("long source: " + exactId);
        System.out.println("double as long: " + (long) roundedId);
        System.out.println("via double: "
                + BigDecimal.valueOf(roundedId).toPlainString());
        System.out.println("via long: "
                + BigDecimal.valueOf(exactId).toPlainString());

        String preciseText = "0.1000000000000000000001";
        BigDecimal direct = new BigDecimal(preciseText);
        BigDecimal viaDouble =
                BigDecimal.valueOf(Double.parseDouble(preciseText));

        System.out.println("from decimal text: "
                + direct.toPlainString());
        System.out.println("after double parse: "
                + viaDouble.toPlainString());
        System.out.println("same decimal value: "
                + (direct.compareTo(viaDouble) == 0));
    }
}
```

Chạy:

```bash
javac SourcePrecisionDemo.java
java SourcePrecisionDemo
```

Kết quả:

```text
long source: 9007199254740993
double as long: 9007199254740992
via double: 9007199254740992
via long: 9007199254740993
from decimal text: 0.1000000000000000000001
after double parse: 0.1
same decimal value: false
```

Ở trường hợp decimal dài, `Double.parseDouble` chỉ giữ giá trị binary64 gần nhất, tương ứng chuỗi chuẩn `"0.1"`. Khi chuyển bằng `valueOf`, chữ số `...0001` cuối trong nguồn **không tồn tại trong đầu vào nữa**.

Đây là lý do **dữ liệu yêu cầu decimal chính xác phải được parse thành decimal chính xác ngay từ đầu**.

## 9. Số 0 cuối và `scale` cũng có thể bị mất

Một lỗi khác tinh tế hơn sai số số học: **giá trị số có thể giữ được nhưng cách biểu diễn và `scale` không giữ được**.

Giả sử nguồn vào là chuỗi:

```text
"19.90"
```

Nếu bạn parse nó qua `double`:

```java
double d = Double.parseDouble("19.90");
System.out.println(Double.toString(d)); // 19.9

BigDecimal fromDouble = BigDecimal.valueOf(d);
System.out.println(fromDouble);         // 19.9
System.out.println(fromDouble.scale()); // 1
```

Trong khi đó:

```java
BigDecimal fromText = new BigDecimal("19.90");
System.out.println(fromText);         // 19.90
System.out.println(fromText.scale()); // 2
```

Hai đối tượng có giá trị toán học bằng nhau, nhưng:

```java
System.out.println(fromDouble.compareTo(fromText)); // 0
System.out.println(fromDouble.equals(fromText));    // false
```

**Vì sao?** `compareTo` so giá trị số, còn `equals` của `BigDecimal` xét cả **scale**. `double` không mang thông tin “người nhập đã viết bao nhiêu chữ số 0 sau dấu chấm”.

Nếu scale có ý nghĩa trong hóa đơn, dữ liệu lưu hoặc giao thức, **phải giữ chuỗi gốc hoặc metadata về scale**. `valueOf` không thể suy đoán rằng 19.9 trước kia được viết 19.90 hay 19.9000.

### Giải thích scale ngắn gọn

Một `BigDecimal` có thể biểu diễn bằng:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

`new BigDecimal("19.90")` có phần trị nguyên 1990 và scale 2. `BigDecimal.valueOf(19.9)` có phần trị nguyên 199 và scale 1. Cả hai biểu diễn cùng giá trị toán học 19.9, nhưng **hợp đồng biểu diễn** khác nhau.

## 10. `BigDecimal.valueOf(double)` không mang khái niệm NaN hoặc Infinity

Kiểu `double` có các giá trị đặc biệt:

- `Double.NaN`.
- `Double.POSITIVE_INFINITY`.
- `Double.NEGATIVE_INFINITY`.

Nhưng `BigDecimal` **không có giá trị NaN hay ±Infinity** trong mô hình decimal của lớp. Vì vậy:

```java
// BigDecimal.valueOf(Double.NaN);               // NumberFormatException
// BigDecimal.valueOf(Double.POSITIVE_INFINITY); // NumberFormatException
// BigDecimal.valueOf(Double.NEGATIVE_INFINITY); // NumberFormatException
```

Các dòng trên bị comment để ví dụ không dừng chương trình. `BigDecimal.valueOf` không âm thầm biến NaN thành số 0 hay Infinity thành số lớn nhất.

Tình huống phổ biến:

```java
double invalid = 0.0 / 0.0; // NaN
// BigDecimal result = BigDecimal.valueOf(invalid);
// khi chạy sẽ ném NumberFormatException
```

Vì thế, với `double` đến từ đầu vào không tin cậy hoặc chuỗi tính toán có nguy cơ tràn, hãy kiểm tra trước:

```java
static BigDecimal requireFiniteDecimal(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(
                "NaN or infinity is not a decimal value");
    }
    return BigDecimal.valueOf(value);
}
```

Đây là phương thức **minh họa** cần được đặt bên trong class có import `java.math.BigDecimal`. Kiểm tra `Double.isFinite` chỉ bảo đảm đối số có thể chuyển thành một decimal hữu hạn; nó **không chứng minh nguồn được biểu diễn chính xác**.

## 11. Overflow và underflow trước khi gọi `valueOf`

### 11.1. Overflow: giá trị hữu hạn biến thành Infinity

`double` có giới hạn độ lớn. Ví dụ:

```java
double source = Double.MAX_VALUE;
double overflowed = source * 2.0;

System.out.println(Double.isFinite(source)); // true
System.out.println(Double.isFinite(overflowed)); // false
```

Tích của hai số hữu hạn có thể tràn thành +Infinity. Sau bước này `BigDecimal.valueOf(overflowed)` sẽ ném `NumberFormatException` thay vì tự suy ra kết quả decimal vô cùng lớn.

Nếu bài toán yêu cầu tính toán chính xác với những số vượt miền `double` và **dữ liệu gốc còn chính xác**, cần chuyển vào `BigDecimal` trước phép nhân, thay vì dùng kết quả Infinity làm đầu vào.

### 11.2. Underflow: giá trị quá nhỏ biến thành 0.0

Số dương nhỏ nhất khác 0 của `double` là `Double.MIN_VALUE` (một số dưới chuẩn dương). Nếu chia cho 2, kết quả được làm tròn về 0:

```java
double smallest = Double.MIN_VALUE;
double underflowed = smallest / 2.0;

System.out.println(underflowed); // 0.0
System.out.println(Double.isFinite(underflowed)); // true
```

Sau đó:

```java
BigDecimal converted = BigDecimal.valueOf(underflowed);
// giá trị decimal 0.0
```

Phần thông tin khác 0 đã mất **trước khi gọi `valueOf`**. Vì 0.0 vẫn hữu hạn, chỉ kiểm tra `isFinite` không phát hiện underflow.

**Phân biệt thêm:** `BigDecimal.valueOf(Double.MIN_VALUE)` được tạo từ chuỗi chuẩn `Double.toString(Double.MIN_VALUE)`, thông thường in `4.9E-324`. Đó là decimal ngắn round-trip về `Double.MIN_VALUE`, không phải toàn bộ khai triển decimal chính xác của bit pattern nhị phân cực nhỏ ấy.

## 12. Số 0 có dấu: `double` phân biệt nhưng `BigDecimal` không

IEEE 754 binary64 có hai mẫu bit cho số 0:

- +0.0.
- -0.0.

Theo so sánh số học `double`:

```java
System.out.println(0.0 == -0.0); // true
System.out.println(1.0 / 0.0);   // Infinity
System.out.println(1.0 / -0.0);  // -Infinity
```

Trong khi đó, `BigDecimal` có **số 0 decimal** nhưng không lưu **bit dấu âm của zero** như kiểu floating-point. Vì vậy:

```java
BigDecimal plus = BigDecimal.valueOf(0.0);
BigDecimal minus = BigDecimal.valueOf(-0.0);

System.out.println(plus.equals(minus)); // true
System.out.println(minus.toPlainString()); // 0.0
```

`Double.toString(-0.0)` có thể chứa dấu âm `"-0.0"`, nhưng khi được parse thành `BigDecimal`, giá trị decimal 0 không giữ một dấu âm tách biệt. Nếu một giao thức yêu cầu phân biệt chính xác **mẫu bit +0.0 và -0.0**, bạn phải lưu mẫu bit `double` riêng, ví dụ qua `Double.doubleToRawLongBits`, thay vì trông chờ `BigDecimal` bảo toàn chi tiết đó.

Đây là một ví dụ khác cho thấy **valueOf chuyển đổi giá trị**, không bảo toàn toàn bộ thông tin biểu diễn của `double`.

## 13. Ví dụ Java 17+ số 3: các trường hợp đặc biệt

Tạo tệp `FloatingBoundaryDemo.java`:

```java
import java.math.BigDecimal;

public class FloatingBoundaryDemo {
    private static void tryConvert(String label, double value) {
        try {
            System.out.println(label + " -> "
                    + BigDecimal.valueOf(value).toString());
        } catch (NumberFormatException e) {
            System.out.println(label + " -> NumberFormatException");
        }
    }

    public static void main(String[] args) {
        double overflow = Double.MAX_VALUE * 2.0;
        double underflow = Double.MIN_VALUE / 2.0;

        System.out.println("overflow finite: "
                + Double.isFinite(overflow));
        System.out.println("underflow: " + underflow);
        System.out.println("underflow finite: "
                + Double.isFinite(underflow));

        tryConvert("NaN", Double.NaN);
        tryConvert("+Infinity", Double.POSITIVE_INFINITY);
        tryConvert("-Infinity", Double.NEGATIVE_INFINITY);
        tryConvert("underflowed zero", underflow);

        BigDecimal negativeZero =
                BigDecimal.valueOf(-0.0);
        BigDecimal positiveZero =
                BigDecimal.valueOf(+0.0);

        System.out.println("zero decimals equal: "
                + negativeZero.equals(positiveZero));
        System.out.println("negative zero converted: "
                + negativeZero.toPlainString());
        System.out.println("double raw zero bits equal: "
                + (Double.doubleToRawLongBits(-0.0)
                    == Double.doubleToRawLongBits(+0.0)));
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingBoundaryDemo.java
java FloatingBoundaryDemo
```

Kết quả:

```text
overflow finite: false
underflow: 0.0
underflow finite: true
NaN -> NumberFormatException
+Infinity -> NumberFormatException
-Infinity -> NumberFormatException
underflowed zero -> 0.0
zero decimals equal: true
negative zero converted: 0.0
double raw zero bits equal: false
```

Ví dụ cho thấy `BigDecimal.valueOf` không thể biến một giá trị Infinity hoặc NaN thành decimal. Nó cũng không thể phục hồi một số rất nhỏ đã underflow về 0, và không bảo toàn thông tin dấu âm của zero.

## 14. Cẩn thận khi lấy `double` từ chuỗi, JSON và database

Có hai luồng parse rất khác nhau.

### Luồng A: đọc trực tiếp chuỗi decimal chính xác

```java
String text = "12345678901234567890.1234500";
BigDecimal value = new BigDecimal(text);
```

Kết quả giữ **đúng các chữ số decimal và scale từ chuỗi**, miễn là cú pháp hợp lệ và tài nguyên đủ. Điều này phù hợp với các dữ liệu mà hợp đồng yêu cầu bảo toàn decimal.

### Luồng B: đọc qua `double` trước

```java
String text = "12345678901234567890.1234500";
double approximate = Double.parseDouble(text);
BigDecimal value = BigDecimal.valueOf(approximate);
```

Trong luồng B, nhiều chữ số có thể đã bị làm tròn khi `Double.parseDouble` tạo binary64. `BigDecimal.valueOf` không thể lấy lại các chữ số ấy, dù đối tượng `BigDecimal` sau đó có khả năng chứa decimal dài.

Với JSON, spreadsheet, database hay giao diện người dùng, bạn cần xác định:

- Parser có map trường số sang `double`, `BigDecimal` hay một kiểu khác?
- Có yêu cầu giữ **giá trị decimal** hoặc **scale và các số 0 cuối** không?
- Đầu vào có thể lớn hơn miền `double` hay hơn 53 bit chính xác phần nguyên không?
- JSON number do một runtime khác tạo có thể đã bị làm tròn ở phía gửi hay không?
- Với cột database `DECIMAL/NUMERIC`, driver/ORM có trả `BigDecimal` trực tiếp hay đã chuyển qua kiểu số thực?

**Khuyến nghị thực tế:** nếu giá trị là tiền, tỷ lệ được quy định theo decimal hoặc một trường dữ liệu có yêu cầu chính xác, hãy **truyền nhận và parse trực tiếp bằng `BigDecimal` hoặc số nguyên có đơn vị**, thay vì đi qua `double` không cần thiết.

### Số 0 cuối trong dữ liệu tài chính

Nếu hệ thống nhận `"19.90"`, `BigDecimal.valueOf(Double.parseDouble("19.90"))` tạo 19.9. Điều đó có thể bằng nhau về giá trị nhưng khác scale với `new BigDecimal("19.90")`. Khi scale là một phần hợp đồng lưu trữ hoặc hiển thị, không thể bỏ qua khác biệt này.

## 15. Làm tròn có thể đúng nhưng phải là quy tắc đã chọn

Một số người giải quyết sai số `double` bằng:

```java
BigDecimal amount = BigDecimal.valueOf(value)
        .setScale(2, java.math.RoundingMode.HALF_UP);
```

Cách này **hợp lệ nếu** nghiệp vụ quy định làm tròn đến 2 chữ số decimal bằng `HALF_UP`. Nhưng cần hiểu:

1. `value` đã có giá trị binary64; lỗi do nguồn có thể đã xảy ra.
2. `BigDecimal.valueOf(value)` lấy decimal chuẩn từ giá trị ấy.
3. `setScale(2, HALF_UP)` **làm tròn giá trị decimal đó**, không hứa khôi phục dữ liệu nguồn.
4. Nếu ứng dụng cần làm tròn theo một chuẩn khác, `HALF_EVEN` hoặc chính sách khác có thể đúng hơn.

### Ví dụ điểm giữa

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal a = new BigDecimal("2.675");
BigDecimal b = BigDecimal.valueOf(2.675);
BigDecimal c = new BigDecimal(2.675);
```

Đây là **đoạn mã minh họa trong phương thức**, cần có import tương ứng. `a` và `b` có giá trị decimal 2.675; `c` biểu diễn chính xác giá trị binary64 xấp xỉ 2.675 (nằm dưới 2.675 toán học một chút).

Khi làm tròn hai chữ số sau dấu chấm bằng `HALF_UP`:

- `a.setScale(2, HALF_UP)` → **2.68**.
- `b.setScale(2, HALF_UP)` → **2.68**.
- `c.setScale(2, HALF_UP)` → **2.67**.

Ví dụ này không chứng minh `valueOf` “chữa được binary64”. Nó cho thấy **hai cách diễn giải nguồn binary64 thành decimal khác nhau**, và thời điểm chuyển dữ liệu quyết định số được đem đi làm tròn.

Nếu đầu vào ban đầu là chuỗi `"2.675"`, cách ít nhập nhằng nhất là `new BigDecimal("2.675")`. Nếu đầu vào chỉ còn là một `double` sau nhiều phép tính, cần xác định liệu làm tròn decimal dạng `Double.toString` có phù hợp nghiệp vụ hay không.

## 16. Chương trình Java 17+ số 4: parse, scale và làm tròn

Tạo tệp `DecimalInputPolicyDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalInputPolicyDemo {
    public static void main(String[] args) {
        String text = "19.90";
        BigDecimal exact = new BigDecimal(text);
        BigDecimal viaDouble = BigDecimal.valueOf(
                Double.parseDouble(text));

        System.out.println("direct decimal: "
                + exact.toPlainString());
        System.out.println("via double: "
                + viaDouble.toPlainString());
        System.out.println("direct scale: " + exact.scale());
        System.out.println("via double scale: " + viaDouble.scale());
        System.out.println("numeric compare: "
                + exact.compareTo(viaDouble));
        System.out.println("equals: "
                + exact.equals(viaDouble));

        BigDecimal directRounded =
                new BigDecimal("2.675")
                        .setScale(2, RoundingMode.HALF_UP);
        BigDecimal valueOfRounded =
                BigDecimal.valueOf(2.675)
                        .setScale(2, RoundingMode.HALF_UP);
        BigDecimal constructorRounded =
                new BigDecimal(2.675)
                        .setScale(2, RoundingMode.HALF_UP);

        System.out.println("direct rounding: "
                + directRounded);
        System.out.println("valueOf rounding: "
                + valueOfRounded);
        System.out.println("constructor rounding: "
                + constructorRounded);

        System.out.println("cents exact: "
                + BigDecimal.valueOf(1990L, 2)
                        .toPlainString());
    }
}
```

Biên dịch và chạy:

```bash
javac DecimalInputPolicyDemo.java
java DecimalInputPolicyDemo
```

Kết quả:

```text
direct decimal: 19.90
via double: 19.9
direct scale: 2
via double scale: 1
numeric compare: 0
equals: false
direct rounding: 2.68
valueOf rounding: 2.68
constructor rounding: 2.67
cents exact: 19.90
```

Ví dụ cho thấy điều mất đi không chỉ có thể là chữ số giá trị, mà còn là **scale và ý nghĩa của số 0 cuối**. Khi dữ liệu gốc cần giữ 2 chữ số decimal, mô hình `BigDecimal.valueOf(1990L, 2)` cũng có thể hợp lý vì nó nhận số nguyên đơn vị nhỏ nhất chính xác.

## 17. `valueOf(double)` không phải cách kiểm tra dữ liệu có hợp lệ không

Một đầu vào `double` hữu hạn vẫn có thể:

- Âm trong khi nghiệp vụ chỉ chấp nhận số dương.
- Quá lớn so với hạn mức thực tế.
- Có phần lẻ trong khi cần số nguyên.
- Đã bị làm tròn so với nguồn decimal.
- Có precision thấp hơn yêu cầu.
- Có giá trị -0.0 cần xử lý đặc biệt.
- Có scale gốc đã mất.

Vì vậy phương thức chuyển đổi chỉ nên là **một bước** trong pipeline. Ví dụ:

```java
static BigDecimal convertMeasuredRate(double input) {
    if (!Double.isFinite(input)) {
        throw new IllegalArgumentException("non-finite rate");
    }
    if (input < 0.0 || input > 1.0) {
        throw new IllegalArgumentException("rate outside 0..1");
    }
    return BigDecimal.valueOf(input);
}
```

Đây là một **phương thức minh họa** đặt trong class với import `BigDecimal`. Nó kiểm tra hữu hạn và giới hạn [0, 1], nhưng **không bảo đảm chính xác decimal theo nguồn**. Nó chỉ phù hợp nếu nghiệp vụ chấp nhận cách chuyển từ số đo/xác suất xấp xỉ `double` sang decimal chuẩn.

Nếu nhu cầu là một **tỷ lệ thập phân chính xác do người dùng nhập**, API nên nhận `String` hoặc `BigDecimal` ngay tại ranh giới, để không mất dữ liệu đầu vào:

```java
static BigDecimal parseExactRate(String text) {
    BigDecimal value = new BigDecimal(text);
    if (value.compareTo(BigDecimal.ZERO) < 0
            || value.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalArgumentException("rate outside 0..1");
    }
    return value;
}
```

Không nên dùng `Double.parseDouble` rồi `BigDecimal.valueOf` ở đây nếu yêu cầu là giữ đúng decimal gốc.

## 18. Sử dụng `BigDecimal.valueOf(double)` khi nào là phù hợp?

Những tình huống có thể phù hợp:

1. **Hệ thống đã có dữ liệu dạng `double` theo thiết kế**, ví dụ giá trị đo, kết quả phân tích thống kê, hoặc API thư viện chỉ trả `double`. Bạn chấp nhận rằng binary64 đã xác định giá trị hiện có.
2. Cần một **biểu diễn decimal ngắn và dễ đọc** theo `Double.toString` thay vì khai triển decimal dài chính xác của các bit binary64.
3. Muốn thực hiện các bước số học decimal tiếp theo theo một chính sách rõ ràng, **mà vẫn thừa nhận độ chính xác nguồn chỉ là `double`**.
4. Muốn lưu **một giá trị gần với thông tin mà `Double.toString` truyền đạt**, không phải những chữ số có thể đã mất trước đó.

Những tình huống **không nên xem là giải pháp đủ**:

- Chuỗi tiền tệ gốc cần giữ đúng số lẻ và scale.
- ID nguyên lớn, số đếm lớn, hoặc timestamps có độ chính xác vượt khả năng binary64.
- Các phép toán cần chính xác decimal ngay từ đầu.
- Giá trị đã overflow thành Infinity, underflow thành 0 hoặc rơi vào NaN.
- Đầu vào từ hệ thống khác đã làm tròn mà bạn cần chứng minh tính toàn vẹn dữ liệu.

### So sánh lựa chọn API

| Dữ liệu hiện có | API gợi ý | Bảo toàn gì? |
|---|---|---|
| `String "19.90"` | `new BigDecimal("19.90")` | Decimal gốc và scale |
| `long cents = 1990` | `BigDecimal.valueOf(cents, 2)` | Số nguyên chính xác với scale 2 |
| `long count` | `BigDecimal.valueOf(count)` | Mọi giá trị `long` chính xác |
| `double rate` đã được phép là xấp xỉ | `BigDecimal.valueOf(rate)` | Decimal chuẩn từ `Double.toString(rate)` |
| `double d` cần khai triển decimal chính xác của giá trị nhị phân | `new BigDecimal(d)` | Giá trị binary64 thực sự |
| `double` NaN/Infinity | Không thể tạo một `BigDecimal` bình thường | Cần từ chối hoặc biểu diễn trạng thái riêng |

Đặc biệt với dữ liệu decimal từ JSON, CSV, giao diện hoặc database, **hợp đồng dữ liệu phải xác định kiểu đầu vào trước khi chọn overload**. Đừng chọn `valueOf(double)` chỉ vì dễ viết.

## 19. Những lỗi phổ biến

| Nhầm lẫn | Thực tế | Cách giải quyết |
|---|---|---|
| `BigDecimal.valueOf(0.1 + 0.2)` thành 0.3 | Kết quả `double` đã là 0.30000000000000004 | Tính bằng `BigDecimal` từ nguồn decimal trước |
| `valueOf(double)` luôn ghi lại chính xác bit binary64 | Nó dùng decimal chuẩn từ `Double.toString` | Dùng `new BigDecimal(d)` nếu thật sự cần khai triển bit-value |
| `valueOf` khôi phục chữ số decimal đã mất | Không có thông tin đầu vào gốc | Parse trực tiếp chuỗi decimal |
| `valueOf((double) longBig)` giữ nguyên mọi số nguyên | Binary64 có giới hạn 53 bit phần trị | Dùng `valueOf(long)` |
| `valueOf(Double.NaN)` trả decimal NaN | `BigDecimal` không có NaN | Kiểm tra `Double.isFinite` và xử lý lỗi |
| `valueOf(Double.POSITIVE_INFINITY)` tự giới hạn thành MAX | Không có saturation kiểu đó; ném `NumberFormatException` | Xử lý infinity trước |
| Underflow về 0 vẫn khôi phục được số nhỏ ban đầu | Nguồn đã thành 0.0 | Xử lý decimal hoặc kiểu phù hợp trước |
| `valueOf(-0.0)` giữ dấu âm zero | `BigDecimal` không giữ signed zero | Lưu mẫu `double` riêng nếu cần |
| Decimal `"19.90"` qua `double` vẫn scale 2 | `Double.toString` in 19.9 | Giữ `String` hoặc tạo decimal với scale rõ |
| `equals` và `compareTo` luôn tương đương | `equals` xét cả scale | Chọn phép so theo mục tiêu |
| `setScale(2)` tự sửa chính xác nguồn | Đó là quy tắc làm tròn trên decimal đã có | Đặt rounding theo yêu cầu nghiệp vụ |
| `Double.isFinite` chứng minh decimal chính xác | Chỉ loại NaN/Infinity | Kiểm tra nguồn và precision riêng |
| Mọi hệ thống JSON truyền number giống nhau | Các runtime/parser có thể làm tròn ở bước khác | Quy định giao thức kiểu và đơn vị |

## 20. Tự kiểm tra

**Câu 1.** `BigDecimal.valueOf(0.1)` có bằng `new BigDecimal("0.1")` theo `equals` không?  
**Đáp án:** Có, trong trường hợp này cả hai có giá trị 0.1 và scale 1.

**Câu 2.** `new BigDecimal(0.1)` có phải decimal 0.1 chính xác không?  
**Đáp án:** Không. Nó lưu khai triển decimal chính xác của giá trị binary64 gần 0.1, dài hơn 0.1.

**Câu 3.** Vì sao `BigDecimal.valueOf(0.1 + 0.2)` không ra 0.3?  
**Đáp án:** Vì phép cộng `double` xảy ra trước, cho 0.30000000000000004. `valueOf` dùng biểu diễn decimal chuẩn của kết quả đã tính.

**Câu 4.** Nếu nhập chuỗi `"19.90"` và cần bảo toàn scale 2, nên làm gì?  
**Đáp án:** Dùng `new BigDecimal("19.90")` trực tiếp, không parse qua `double`.

**Câu 5.** `BigDecimal.valueOf(9_007_199_254_740_993L)` khác `BigDecimal.valueOf((double) 9_007_199_254_740_993L)` ở đâu?  
**Đáp án:** Overload `long` giữ đúng 9_007_199_254_740_993; đường qua `double` bị làm tròn xuống 9_007_199_254_740_992.

**Câu 6.** `BigDecimal.valueOf(Double.NaN)` có thành công không?  
**Đáp án:** Không. `BigDecimal` không có giá trị NaN; phương thức ném `NumberFormatException`.

**Câu 7.** Nếu `double` underflow về 0 trước khi chuyển, `valueOf` có thể lấy lại số dương rất nhỏ không?  
**Đáp án:** Không. Giá trị truyền vào đã bằng 0.0.

**Câu 8.** `BigDecimal.valueOf(-0.0)` và `BigDecimal.valueOf(+0.0)` có giữ hai dấu zero khác nhau không?  
**Đáp án:** Không. Chúng bằng nhau theo `equals`; `BigDecimal` không biểu diễn signed zero riêng.

**Câu 9.** Khi nào `new BigDecimal(d)` có thể hữu ích hơn `valueOf(d)`?  
**Đáp án:** Khi cần khai triển decimal **chính xác giá trị binary64** đang lưu, ví dụ phân tích sai số hoặc kiểm thử biểu diễn; không phải khi muốn khôi phục decimal nguồn.

**Câu 10.** Có phải `BigDecimal.valueOf(2.675).setScale(2, HALF_UP)` luôn có cùng kết quả với `new BigDecimal(2.675).setScale(2, HALF_UP)`?  
**Đáp án:** Không. Với ví dụ này, cách đầu làm tròn 2.675 thành 2.68, còn constructor `double` dùng giá trị binary64 hơi nhỏ hơn 2.675 nên thành 2.67.

**Câu 11.** `BigDecimal.valueOf(12345L, 2)` cho gì?  
**Đáp án:** Decimal chính xác 123.45, vì đầu vào là số nguyên `long` và scale 2.

**Câu 12.** `Double.isFinite(d)` có bảo đảm `BigDecimal.valueOf(d)` khôi phục được chuỗi decimal gốc không?  
**Đáp án:** Không. `isFinite` chỉ kiểm tra số không phải NaN/Infinity; precision hoặc scale có thể đã mất từ trước.

## 21. Checklist khi đưa `double` vào hệ thống decimal

Trước khi chọn `BigDecimal.valueOf(double)`, kiểm tra:

1. **Dữ liệu gốc là gì?** Chuỗi decimal, `long`, cảm biến hay giá trị tính bằng `double`?
2. **Có cần bảo toàn từng chữ số decimal nguồn không?** Nếu có, parse trực tiếp từ `String` hoặc kiểu decimal.
3. **Có cần giữ scale/số 0 cuối không?** `double` không mang thông tin này.
4. **Giá trị có thể vượt 53 bit chính xác phần nguyên không?** Nếu có, tránh luồng qua binary64.
5. **Đã thực hiện phép tính `double` nào chưa?** Sai số có thể hình thành từ trước.
6. **Có thể là NaN, ±Infinity, underflow về 0 không?** Xử lý trước khi chuyển nếu nghiệp vụ không cho phép.
7. **Có cần giữ signed zero hoặc payload NaN không?** `BigDecimal` không phải định dạng lưu mọi bit IEEE 754.
8. **Cần làm tròn theo quy tắc nào?** Quy định thời điểm, scale và `RoundingMode`.
9. **API/JSON/database có chuyển kiểu ẩn không?** Xác minh toàn bộ đường đi.
10. **Đã có test giá trị biên và dữ liệu gốc cụ thể chưa?** Kiểm thử số 0 cuối, decimal dài, số nguyên lớn, số cực nhỏ, cực lớn và lỗi parse.

**Kết luận:** `BigDecimal.valueOf(double)` là công cụ **chuyển một giá trị binary64 hiện có sang một decimal theo biểu diễn `Double.toString`**. Nó rất hữu ích khi bạn chấp nhận kiểu `double` của nguồn, nhưng **không khôi phục precision, scale, kết quả số học hay thông tin bit đã mất trước đó**. Để tính toán decimal chính xác theo dữ liệu nghiệp vụ, hãy dùng `BigDecimal` hoặc số nguyên theo đơn vị quy định **ngay từ nguồn**.

Tài liệu chính thức:
- [Java SE 17 — BigDecimal.valueOf(double)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html#valueOf(double))
- [Java SE 17 — BigDecimal(double)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html#%3Cinit%3E(double))
- [Java SE 17 — Double.toString(double)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html#toString(double))
- [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-parsing-009">Kiểm tra giá trị hữu hạn và miền dữ liệu sau khi parse</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra giá trị hữu hạn và miền dữ liệu sau khi parse trong Java

## 1. Vì sao parse thành công chưa có nghĩa dữ liệu hợp lệ?

Khi chương trình nhận số thực từ form, file cấu hình, CSV hoặc API, bước đầu thường là `Double.parseDouble(String)` hay `Float.parseFloat(String)`. Tuy nhiên **chuyển đổi chuỗi thành công chỉ chứng tỏ chuỗi được chấp nhận theo cú pháp số thực Java**; nó không đảm bảo kết quả phù hợp với miền dữ liệu, ý nghĩa nghiệp vụ hoặc yêu cầu về độ chính xác.

Ví dụ:

~~~java
double normal = Double.parseDouble("12.5");
double nan = Double.parseDouble("NaN");
double inf = Double.parseDouble("Infinity");
double overflow = Double.parseDouble("1e309");
double tiny = Double.parseDouble("1e-400");
~~~

Các lời gọi trên đều có thể hoàn thành **không ném `NumberFormatException`**. Nhưng:

- `normal` bằng 12,5 và hữu hạn.
- `nan` mang giá trị `NaN` (*Not a Number*).
- `inf` và `overflow` mang giá trị `+Infinity`.
- `tiny` bằng `0.0` do kết quả thập phân khác 0 quá nhỏ để biểu diễn dưới dạng `double`.

Do vậy, quy trình tin cậy là **đọc chuỗi → parse → kiểm tra tính hữu hạn → kiểm tra miền nghiệp vụ → kiểm tra thêm các điều kiện độ chính xác nếu cần → sử dụng**. Bài học tập trung vào từng bước và những lỗi khó thấy trong mã Java 17+.

## 2. Hai phương thức parse số thực thông dụng

| API | Trả về | Miền biểu diễn | Phản ứng với chuỗi sai cú pháp |
| --- | --- | --- | --- |
| `Double.parseDouble(text)` | `double` primitive | IEEE 754 binary64 | Ném `NumberFormatException` |
| `Float.parseFloat(text)` | `float` primitive | IEEE 754 binary32 | Ném `NumberFormatException` |
| `Double.valueOf(text)` | `Double` wrapper | Tương tự `double` | Ném `NumberFormatException` |
| `Float.valueOf(text)` | `Float` wrapper | Tương tự `float` | Ném `NumberFormatException` |

Ví dụ:

~~~java
double a = Double.parseDouble("3.14");
float b = Float.parseFloat("3.14");
Double wrapped = Double.valueOf("3.14");
~~~

Đây là đoạn minh họa đặt trong một phương thức, không phải một chương trình Java hoàn chỉnh. `parseDouble` và `parseFloat` trả **primitive**; `valueOf` trả **wrapper**. Dù dùng API nào, dữ liệu ngoài miền nghiệp vụ hoặc `NaN`/`Infinity` vẫn cần kiểm tra riêng.

`NumberFormatException` chỉ nói rằng quá trình chuyển chuỗi thất bại vì cú pháp; không nên coi việc **không** bắt được ngoại lệ này là một xác nhận dữ liệu “sạch”.

### Những chuỗi Java có thể parse được

~~~java
System.out.println(Double.parseDouble("-12.5"));  // -12.5
System.out.println(Double.parseDouble("1e3"));    // 1000.0
System.out.println(Double.parseDouble("0x1.8p2")); // 6.0
System.out.println(Double.parseDouble("NaN"));    // NaN
System.out.println(Double.parseDouble("-Infinity")); // -Infinity
~~~

`0x1.8p2` là số thực hexadecimal: `1.8` hệ 16 nhân `2²` nên bằng 6.0. Điều đó cho thấy `Double.parseDouble` chấp nhận **nhiều hơn cú pháp số thập phân đơn giản**.

Trong Java, các dạng `"NaN"` và `"Infinity"` là chuỗi đặc biệt được chấp nhận, nhưng chúng **không phải số hữu hạn**. Nếu API của bạn chỉ cho phép số thập phân thường, hãy đặt chính sách định dạng đầu vào riêng; đừng nhầm “parser Java chấp nhận” với “API nghiệp vụ cho phép”.

## 3. Giá trị hữu hạn, Infinity và NaN

Có ba nhóm kết quả quan trọng sau khi parse:

| Nhóm | Ví dụ | `Double.isFinite(x)` | `Double.isNaN(x)` | `Double.isInfinite(x)` |
| --- | --- | --- | --- | --- |
| Hữu hạn | `0.0`, `-0.0`, `12.5`, `Double.MAX_VALUE` | `true` | `false` | `false` |
| Không phải số | `Double.NaN` | `false` | `true` | `false` |
| Vô cực | `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` | `false` | `false` | `true` |

Dùng `Double.isFinite(x)` để chấp nhận **chỉ số hữu hạn**:

~~~java
double parsed = Double.parseDouble("NaN");

if (!Double.isFinite(parsed)) {
    throw new IllegalArgumentException("Giá trị phải hữu hạn");
}
~~~

Với `float`, dùng `Float.isFinite(value)`, `Float.isNaN(value)`, `Float.isInfinite(value)`. Những phương thức này hoạt động với giá trị primitive, không phụ thuộc vào cách chuỗi đầu vào được viết.

**Lưu ý:** `Double.isFinite` trả `true` với `0.0`, `-0.0` và cả các số `subnormal` rất nhỏ. Nó chỉ loại `NaN` và `±Infinity`; không có nghĩa số nằm trong phạm vi hợp lệ hoặc được chuyển đổi mà không mất thông tin.

## 4. Trường hợp overflow: parse hợp lệ nhưng cho Infinity

`double` có giá trị hữu hạn lớn nhất là `Double.MAX_VALUE`, xấp xỉ `1.7976931348623157E308`. `float` có giới hạn thấp hơn, `Float.MAX_VALUE` xấp xỉ `3.4028235E38`.

~~~java
double farTooLarge = Double.parseDouble("1e309");
float tooLargeFloat = Float.parseFloat("1e39");

System.out.println(farTooLarge); // Infinity
System.out.println(tooLargeFloat); // Infinity
System.out.println(Double.isFinite(farTooLarge)); // false
System.out.println(Float.isFinite(tooLargeFloat)); // false
~~~

Kết quả không phải `NumberFormatException`. Chuỗi có cú pháp số thực hợp lệ, nhưng giá trị vượt miền biểu diễn và được làm tròn thành vô cực.

Nếu ứng dụng chấp nhận một số dương hữu hạn trong miền từ 0 đến 100 thì `"1e309"` phải bị từ chối, dù đã parse thành công. Nếu dữ liệu không được phép là vô cực, **bắt `NumberFormatException` là chưa đủ**.

Một số âm có độ lớn cực lớn như `"-1e309"` có thể cho `-Infinity`. `Double.isFinite` loại cả hai dấu.

## 5. Underflow, subnormal và giá trị 0 sau khi parse

Ở đầu gần số 0, hệ dấu phẩy động có giới hạn biểu diễn:

- `Double.MIN_NORMAL` là số `double` *normal* dương nhỏ nhất, khoảng `2.2250738585072014E-308`.
- `Double.MIN_VALUE` là **số `double` dương khác 0 nhỏ nhất**, khoảng `4.9E-324`, thuộc nhóm *subnormal*.
- Số dương nhỏ hơn cả `Double.MIN_VALUE` có thể bị làm tròn về `0.0`; số âm tương ứng có thể thành `-0.0`.

~~~java
double subnormal = Double.parseDouble("4.9e-324");
double tiny = Double.parseDouble("1e-400");
double negativeTiny = Double.parseDouble("-1e-400");

System.out.println(subnormal == Double.MIN_VALUE); // true
System.out.println(tiny); // 0.0
System.out.println(negativeTiny); // -0.0
System.out.println(Double.isFinite(tiny)); // true
~~~

Đặc biệt, `"1e-400"` về mặt toán học là số **dương khác 0**, nhưng khi lưu vào `double` trở thành **0.0**. Vì `0.0` vẫn hữu hạn, `isFinite` không phát hiện được sự mất giá trị này.

**Không đánh đồng hai câu hỏi:**

1. Giá trị `double` cuối cùng có hữu hạn không?
2. Giá trị số trong chuỗi gốc có được bảo toàn đủ chính xác khi chuyển sang `double` không?

Câu hỏi thứ hai không thể luôn trả lời chỉ bằng một `double` đã parse xong. Nếu yêu cầu tuyệt đối không biến một giá trị thập phân khác 0 thành 0, cần kiểm tra chuỗi đầu vào hoặc dùng kiểu có độ chính xác phù hợp trước khi chuyển.

## 6. Kiểm tra miền dữ liệu sau khi xác nhận hữu hạn

Giả sử API nhận nhiệt độ trong phạm vi từ −40°C đến 85°C, gồm cả hai đầu mút. Phép kiểm tra cơ bản:

~~~java
double value = Double.parseDouble(text);

if (!Double.isFinite(value)) {
    throw new IllegalArgumentException("Nhiệt độ phải là số hữu hạn");
}
if (value < -40.0 || value > 85.0) {
    throw new IllegalArgumentException("Nhiệt độ ngoài [-40, 85]");
}
~~~

Đoạn này là phần **minh họa trong một phương thức**, nơi `text` đã được cung cấp trước. Thứ tự kiểm tra không phải trang trí: kiểm tra tính hữu hạn trước khiến chính sách từ chối `NaN`/`Infinity` minh bạch.

### Vì sao chỉ kiểm tra x < min || x > max là chưa đủ?

Mọi phép so sánh thứ tự `<`, `>`, `<=`, `>=` với `NaN` đều cho `false`. Vì vậy:

~~~java
double value = Double.NaN;
boolean outside = value < -40.0 || value > 85.0;

System.out.println(outside); // false
~~~

Nếu chỉ viết `if (outside) reject`, chương trình sẽ **chấp nhận nhầm NaN**. Đây là lỗi validation rất quan trọng khi parse dữ liệu số thực.

Có thể thiết kế `if (!Double.isFinite(value) || value < min || value > max) reject`. Biểu thức sử dụng `||` đánh giá ngắn mạch; viết riêng bước kiểm tra thường dễ đọc và dễ báo lỗi nguyên nhân hơn.

### Biên đóng và biên mở

Tùy yêu cầu nghiệp vụ:

- `min <= value && value <= max`: miền đóng, nhận hai biên.
- `min < value && value < max`: miền mở, không nhận hai biên.
- `value > 0`: nhận giá trị dương thực sự, từ chối `0.0` và `-0.0`.
- `value >= 0`: nhận cả `0.0` và `-0.0` vì hai số 0 so sánh bằng nhau theo toán tử số học.

Kiểm tra hữu hạn **không thay thế** bước chọn miền đóng/mở, yêu cầu số nguyên hay quy tắc với số 0.

## 7. Hàm parseFiniteInRange thực tế trên Java 17

Một thiết kế đơn giản có thể tái sử dụng:

~~~java
static double parseFiniteInRange(String text, double min, double max) {
    if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
        throw new IllegalArgumentException("Invalid finite bounds");
    }
    if (text == null) {
        throw new IllegalArgumentException("Missing numeric text");
    }

    final double value;
    try {
        value = Double.parseDouble(text);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Malformed numeric text", ex);
    }

    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Value must be finite");
    }
    if (value < min || value > max) {
        throw new IllegalArgumentException("Value outside bounds");
    }
    return value;
}
~~~

Phương thức này là **một method ở cấp lớp**, chưa phải chương trình chạy độc lập. Nó thể hiện bốn hợp đồng:

1. Miền cấu hình phải là hai giá trị hữu hạn và `min <= max`.
2. Chuỗi đầu vào không được `null`.
3. Văn bản phải parse được theo `Double.parseDouble`.
4. Kết quả parse phải hữu hạn và nằm trong miền đóng `[min, max]`.

`IllegalArgumentException` là lựa chọn thông báo lỗi ở ranh giới API; bạn cũng có thể định nghĩa exception riêng nếu hệ thống cần phân loại lỗi cấu hình/validation. Cần chú ý `NumberFormatException` chính là một lớp con của `IllegalArgumentException`, vì vậy mã gọi có thể bắt chung nếu muốn hiển thị một thông báo lỗi thống nhất.

### Mẫu Java cho Float

~~~java
static float parseFiniteFloat(String text, float min, float max) {
    if (!Float.isFinite(min) || !Float.isFinite(max) || min > max) {
        throw new IllegalArgumentException("Invalid bounds");
    }
    if (text == null) {
        throw new IllegalArgumentException("Missing numeric text");
    }

    final float value;
    try {
        value = Float.parseFloat(text);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Malformed float text", ex);
    }
    if (!Float.isFinite(value) || value < min || value > max) {
        throw new IllegalArgumentException("Float outside valid range");
    }
    return value;
}
~~~

Đây là ví dụ thay thế khi kiểu lưu trữ bắt buộc là `float`. Không tự parse sang `double` rồi ép `(float)` nếu bạn muốn biết chính xác hệ quả chuyển đổi ngay ở kiểu đích; nếu phải ép, hãy kiểm tra lại kết quả `float` sau ép.

## 8. Chương trình Java 17+ hoàn chỉnh

Lưu thành tệp `FiniteParseDemo.java`:

~~~java
public class FiniteParseDemo {
    static double parseFiniteInRange(String text, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("Invalid finite bounds");
        }
        if (text == null) {
            throw new IllegalArgumentException("Missing numeric text");
        }

        final double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed numeric text", ex);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Value must be finite");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("Value outside bounds");
        }
        return value;
    }

    public static void main(String[] args) {
        System.out.println("normal=" +
                parseFiniteInRange("37.5", 0.0, 100.0));
        System.out.println("negative zero=" +
                parseFiniteInRange("-0.0", 0.0, 100.0));

        String[] rejected = {
            "NaN", "Infinity", "-Infinity", "1e309", "101", "not-a-number"
        };
        for (String text : rejected) {
            try {
                parseFiniteInRange(text, 0.0, 100.0);
                System.out.println("accepted=" + text);
            } catch (IllegalArgumentException ex) {
                System.out.println("rejected=" + text);
            }
        }

        System.out.println("underflow=" +
                parseFiniteInRange("1e-400", 0.0, 100.0));
        System.out.println("float overflow finite=" +
                Float.isFinite(Float.parseFloat("1e39")));
        System.out.println("subnormal=" +
                (Double.parseDouble("4.9e-324") == Double.MIN_VALUE));
        System.out.println("hex float=" +
                Double.parseDouble("0x1.8p2"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 FiniteParseDemo.java
java FiniteParseDemo
~~~

Stdout:

~~~text
normal=37.5
negative zero=-0.0
rejected=NaN
rejected=Infinity
rejected=-Infinity
rejected=1e309
rejected=101
rejected=not-a-number
underflow=0.0
float overflow finite=false
subnormal=true
hex float=6.0
~~~

Đọc kết quả đúng:

- `"37.5"` là số hữu hạn thuộc `[0, 100]` nên được chấp nhận.
- `"-0.0"` vẫn thuộc miền đóng này vì theo phép so sánh số học `-0.0 == +0.0`. Nếu dấu zero có nghĩa nghiệp vụ, cần kiểm tra riêng.
- `"NaN"`, `"Infinity"` và `"1e309"` được parser chấp nhận dưới dạng giá trị dấu phẩy động đặc biệt nhưng bị validation từ chối.
- `"101"` là số hữu hạn nhưng **ngoài miền** nên bị từ chối.
- `"not-a-number"` không đúng cú pháp parse, được chuyển thành lỗi validation.
- `"1e-400"` cho `0.0` và **vẫn được chấp nhận** vì logic ở đây chỉ yêu cầu *hữu hạn* và *trong [0, 100]*. Đây là điểm cần bổ sung nếu nghiệp vụ yêu cầu bảo toàn mọi giá trị dương nhỏ.
- `Float.parseFloat("1e39")` trả Infinity nên `Float.isFinite` trả false.
- `"4.9e-324"` được làm tròn thành số subnormal dương nhỏ nhất; số thực hexadecimal `0x1.8p2` bằng 6.0.

## 9. Khi nào cần từ chối underflow thành zero?

Giả sử bạn xử lý xác suất **bắt buộc lớn hơn 0**. Khi đó:

~~~java
double probability = Double.parseDouble("1e-400");
if (!Double.isFinite(probability) ||
        probability <= 0.0 || probability > 1.0) {
    throw new IllegalArgumentException("Probability must be in (0, 1]");
}
~~~

Đoạn trên từ chối đầu vào vì kết quả đã thành 0.0, đúng với hợp đồng “giá trị `double` cuối cùng phải lớn hơn 0”. Tuy nhiên, nếu mục tiêu là lưu **mọi xác suất decimal dương khác 0** dù rất nhỏ, `double` không đủ miền biểu diễn.

### Dùng BigDecimal nếu phải giữ giá trị decimal nhỏ chính xác

~~~java
java.math.BigDecimal exact = new java.math.BigDecimal("1e-400");
double narrowed = exact.doubleValue();

System.out.println(exact.signum() > 0); // true
System.out.println(narrowed);           // 0.0
~~~

`BigDecimal` giữ giá trị decimal trong ví dụ này; ép về `double` khiến nó mất thông tin. Việc dùng `BigDecimal` cần quyết định từ **trước khi parse** dữ liệu thành `double` — không thể dùng `BigDecimal.valueOf(0.0)` để khôi phục giá trị `1e-400` ban đầu.

Nếu bắt buộc dùng `double` nhưng muốn phát hiện chính sách “đầu vào decimal khác 0 không được thành 0”, có thể parse `BigDecimal` từ chuỗi ở bước đầu, kiểm tra `signum()` rồi so với kết quả `doubleValue()`. Điều này phải đi kèm định nghĩa định dạng được chấp nhận: `BigDecimal` không hỗ trợ chuỗi `NaN`/`Infinity` giống `Double.parseDouble`.

## 10. Không nên chỉ dựa vào Double.isFinite khi kiểm tra độ chính xác

Một số hữu hạn vẫn có thể mất chữ số đáng kể khi parse:

~~~java
double rounded = Double.parseDouble("9007199254740993");
System.out.println(rounded == 9007199254740992.0); // true
~~~

`9007199254740993 = 2⁵³ + 1` không thể biểu diễn chính xác bằng binary64 và được làm tròn thành `9007199254740992`. `Double.isFinite(rounded)` vẫn trả true.

Nếu trường dữ liệu là mã định danh, số nguyên đếm được, số tiền hoặc lượng decimal yêu cầu chính xác tuyệt đối, hãy dùng loại dữ liệu phù hợp:

- `long` hoặc `BigInteger` cho số nguyên (tùy miền và quy tắc kiểm tra).
- `BigDecimal` cho số decimal cần độ chính xác thập phân.
- `double`/`float` khi chấp nhận mô hình dấu phẩy động xấp xỉ và đã quy định tolerance.

**Không nhầm tính hữu hạn với tính chính xác.** Đây là hai trục kiểm tra độc lập: một số có thể hữu hạn nhưng bị làm tròn; một chuỗi có thể parse hợp lệ nhưng tạo Infinity; một số có thể hữu hạn, chính xác theo kiểu `double` nhưng vẫn ngoài miền nghiệp vụ.

## 11. Kiểm tra chuỗi đầu vào: null, rỗng, whitespace và định dạng

`Double.parseDouble`/`Float.parseFloat` có quy tắc cú pháp riêng. Trên Java 17, parser chấp nhận whitespace đầu/cuối được loại như `String.trim()` trước khi phân tích, nhưng không chấp nhận các dấu gạch dưới dùng trong literal mã nguồn:

~~~java
System.out.println(Double.parseDouble(" 12.5 ")); // 12.5
// Double.parseDouble("1_000"); // Ném NumberFormatException.
~~~

Điểm này dẫn tới lựa chọn thiết kế:

- Nếu hệ thống cho phép người dùng thêm khoảng trắng, có thể chấp nhận theo hành vi parser.
- Nếu hệ thống đòi định dạng chặt như “chỉ chữ số thập phân, dấu chấm và dấu âm” hoặc cấm hex, NaN, suffix, phải **xác thực cú pháp** theo hợp đồng riêng.
- Nếu dữ liệu có thể là `null`, phải kiểm tra trước; `Double.parseDouble(null)` ném `NullPointerException`, không phải `NumberFormatException`.
- Với chuỗi rỗng hoặc sai dạng, bắt `NumberFormatException` và cung cấp thông báo lỗi phù hợp.

Không nên áp dụng regex tự chế thiếu kiểm thử rồi cho rằng đã bao phủ mọi định dạng Unicode, số mũ, dấu hoặc dạng thập phân. Xác định đúng grammar của API và dùng parser phù hợp.

### Không dùng locale để suy đoán dấu thập phân

`Double.parseDouble("12.5")` dùng dấu chấm theo cú pháp Java, không tự hiểu chuỗi dùng dấu phẩy decimal theo ngôn ngữ của người dùng. Trong ứng dụng đa ngôn ngữ, cần chuẩn hóa dữ liệu có quy tắc hoặc dùng `NumberFormat` phù hợp với locale và yêu cầu parse *toàn chuỗi*. Dù dùng phương thức nào, bước **kiểm tra hữu hạn và miền** vẫn cần được thực hiện sau khi có kết quả số.

## 12. Miền hợp lệ: từ yêu cầu nghiệp vụ đến điều kiện Java

Bảng ví dụ (các giới hạn minh họa, không áp đặt cho mọi hệ thống):

| Trường | Miền giả định | Kiểm tra |
| --- | --- | --- |
| Phần trăm | 0 đến 100, gồm hai đầu | `isFinite(x) && x >= 0 && x <= 100` |
| Xác suất | 0 đến 1, gồm hai đầu | `isFinite(x) && x >= 0 && x <= 1` |
| Xác suất bắt buộc dương | Lớn hơn 0, đến 1 | `isFinite(x) && x > 0 && x <= 1` |
| Nhiệt độ thiết bị | −40 đến 85 | `isFinite(x) && x >= -40 && x <= 85` |
| Khối lượng sản phẩm | Lớn hơn 0, không vượt max | `isFinite(x) && x > 0 && x <= max` với max hữu hạn đã xác thực |
| Điểm tọa độ / vector | Miền do định nghĩa nghiệp vụ | Xác thực từng thành phần và tính hợp lệ tổng thể |

Các biểu thức trên **không thể dùng nguyên xi** nếu `x` hoặc `max` là wrapper có thể bằng `null`: phải có null guard trước khi unbox.

Ngoài miền số học, cần kiểm tra quy tắc nghiệp vụ khác, như số phải là số nguyên, độ phân giải đo, giới hạn tổng các thành phần, ràng buộc liên trường và đơn vị. Ví dụ giá trị `37.5` có thể hợp lệ với nhiệt độ nhưng không phù hợp cho “số lượng thiết bị nguyên”.

### Về ±0.0

`Double.parseDouble("-0.0")` là số **hữu hạn**, và `-0.0 == 0.0` là true. Nếu nghiệp vụ yêu cầu không cho “âm” ở mức bit, `value >= 0` không phân biệt được `-0.0`; có thể kiểm tra dấu bằng `Double.doubleToRawLongBits(value)` hoặc dùng chính sách chuẩn hóa về `0.0`. Chỉ xử lý dấu zero riêng khi thực sự có ý nghĩa; đa số bài toán đếm/đo thông thường không cần.

## 13. Lỗi validation thường gặp

| Sai lầm | Vì sao sai? | Cách sửa |
| --- | --- | --- |
| Chỉ bắt `NumberFormatException` | `"NaN"`, `"Infinity"`, `"1e309"` parse được | Thêm `Double.isFinite`/`Float.isFinite` |
| Chỉ kiểm tra `x < min || x > max` | `NaN` không thỏa so sánh thứ tự, có thể lọt qua | Kiểm tra `isFinite` trước |
| Nghĩ `isFinite` nghĩa là nằm trong miền | `1e100` hữu hạn nhưng có thể ngoài giới hạn nghiệp vụ | Kiểm tra range độc lập |
| Nghĩ `isFinite` đảm bảo chính xác | Số hữu hạn có thể bị làm tròn | Chọn kiểu dữ liệu và kiểm tra precision phù hợp |
| Dùng `Double.MIN_VALUE` làm số âm nhỏ nhất | Đây là **số dương khác 0** nhỏ nhất | Dùng `-Double.MAX_VALUE` cho cận hữu hạn âm về độ lớn |
| Tự coi `0.0` sau parse luôn là số 0 chính xác của text | `1e-400` có thể bị làm tròn thành 0.0 | Kiểm tra chuỗi decimal gốc khi cần |
| Dùng `value >= 0` để cấm `-0.0` | Hai zero so sánh bằng nhau | Quy định cách kiểm tra dấu zero nếu cần |
| Dùng `Double.parseDouble(null)` trong catch NumberFormatException | `null` gây `NullPointerException` | Null guard |
| Dùng `Float.parseFloat` với miền của double | `float` có miền và precision khác | Xác thực theo kiểu lưu thực tế |
| Không xác thực min/max truyền vào validator | Bounds NaN/Infinity có thể làm logic sai | Kiểm tra cả hai bound hữu hạn và `min <= max` |
| Dùng `double` cho tiền hoặc ID nguyên lớn rồi dựa vào isFinite | Mất độ chính xác dù hữu hạn | `BigDecimal`, `BigInteger` hoặc `long` |
| Bỏ qua locale/đơn vị | Chuỗi khác định dạng hoặc khác thang đo | Quy định grammar, locale và đơn vị tường minh |

## 14. Cách xây dựng validation có thể kiểm thử

Một bộ kiểm thử nên có ít nhất các nhóm:

**Cú pháp:** `"12.5"`, `"-12.5"`, `"abc"`, `""`, `null`, khoảng trắng đầu/cuối, ký tự phân tách số không hợp lệ.

**Giá trị đặc biệt:** `"NaN"`, `"Infinity"`, `"-Infinity"` và một số lớn tạo Infinity như `"1e309"`.

**Miền dữ liệu:** ở đúng min/max, sát phía trong, sát phía ngoài, số âm, số 0 và số vượt giới hạn.

**Biên biểu diễn:** `"1e-400"` thành 0.0, số subnormal nhỏ nhất, `Double.MAX_VALUE`, giá trị lớn hơn `MAX_VALUE` và số có nhiều chữ số cần làm tròn.

**Ngữ nghĩa:** giá trị cần dương nghiêm ngặt, cấm NaN, yêu cầu giá trị nguyên, phân biệt dữ liệu thiếu với số 0, dữ liệu decimal yêu cầu chính xác.

Gợi ý các assertion khi đã có phương thức `parseFiniteInRange`:

~~~java
assert parseFiniteInRange("0", 0, 100) == 0.0;
assert parseFiniteInRange("100", 0, 100) == 100.0;
assert Double.doubleToRawLongBits(
        parseFiniteInRange("-0.0", 0, 100)) ==
        Double.doubleToRawLongBits(-0.0);

// Các trường hợp NaN, Infinity, "101", "abc" phải bị từ chối.
// Với test framework, dùng assertThrows(...) để kiểm tra exception.
~~~

Đây là các câu lệnh minh họa bên trong một phương thức. Từ khóa `assert` của Java chỉ được thực thi khi chạy JVM với `-ea`; với kiểm thử tự động nên dùng assertion của framework như JUnit 5. Các trường hợp chờ exception cần có testcase riêng; không nên suy từ việc thử “happy path” rằng mọi đầu vào sai đều được xử lý.

## 15. Câu hỏi tự kiểm tra

**1.** `Double.parseDouble("NaN")` có ném `NumberFormatException` không?

**Đáp án:** Không. Nó trả `Double.NaN`; phải dùng `Double.isNaN` hoặc `!Double.isFinite` để từ chối theo chính sách chỉ nhận số hữu hạn.

**2.** `Double.parseDouble("1e309")` và `Double.parseDouble("1e-400")` cho những giá trị nào?

**Đáp án:** Theo binary64, trường hợp đầu cho `Infinity` do vượt miền; trường hợp sau cho `0.0` do giá trị quá nhỏ. Cả hai chuỗi có cú pháp số hợp lệ.

**3.** Tại sao `if (x < 0 || x > 100) reject` không đủ nếu x do parse sinh ra?

**Đáp án:** Nếu `x` là `NaN`, cả hai điều kiện đều false, khiến mã không reject. Thêm `Double.isFinite(x)` trước khi so sánh.

**4.** `Double.isFinite(0.0)` và `Double.isFinite(Double.MIN_VALUE)` bằng gì?

**Đáp án:** Cả hai là true. Zero và subnormal dương nhỏ nhất đều là số hữu hạn.

**5.** Nếu đầu vào là xác suất dương `"1e-400"` mà chương trình phải lưu **giá trị decimal khác 0**, chỉ dùng `double` được không?

**Đáp án:** Không. Chuỗi đó bị làm tròn về 0.0 khi chuyển sang binary64. Dùng biểu diễn phù hợp như `BigDecimal` hoặc một mô hình xác suất/logarithm chuyên dụng tùy ứng dụng.

**6.** Nếu parse ra 101 trong khi miền hợp lệ là `[0, 100]`, có cần quan tâm `NumberFormatException` không?

**Đáp án:** Parse đã thành công và 101 là số hữu hạn. Đây là **lỗi miền nghiệp vụ**; cần kiểm tra sau parse và báo lỗi phù hợp.

**7.** Khi cần ngăn `-0.0` nhưng cho phép `0.0`, có dùng `value >= 0` được không?

**Đáp án:** Không, vì `-0.0 == 0.0` theo phép so sánh primitive. Cần kiểm tra dấu zero bằng bit hoặc chuẩn hóa đầu vào theo chính sách rõ ràng.

## 16. Ghi nhớ và nguồn tham khảo

- `Double.parseDouble`/`Float.parseFloat` giải quyết **chuyển cú pháp chuỗi sang floating point**, không tự kiểm tra tính hữu hạn, miền nghiệp vụ hoặc độ chính xác.
- `Double.isFinite`/`Float.isFinite` loại `NaN` và `±Infinity`, nhưng không loại zero hoặc subnormal.
- Kiểm tra **miền số** sau tính hữu hạn, với min/max và đầu mút được quy định rõ; nếu không, `NaN` có thể qua một bộ lọc chỉ dùng dấu so sánh.
- Phải hiểu overflow thành Infinity, underflow thành ±0.0 và rounding của số rất lớn/nhỏ. Không dùng dấu “parse thành công” làm bằng chứng không mất dữ liệu.
- Chọn `double` cho tính toán xấp xỉ theo thiết kế, hoặc dùng `BigDecimal`/`BigInteger`/`long` theo yêu cầu biểu diễn chính xác.

Tài liệu chính thức của Java:

- [Java SE 17 — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): `parseDouble`, `isFinite`, `isNaN`, `isInfinite` và các giá trị biên.
- [Java SE 17 — Float](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html): `parseFloat`, `isFinite` và miền binary32.
- [JLS 17 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): NaN, Infinity, normal/subnormal, signed zero.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): số decimal chính xác với độ chính xác tùy ý.


</details>

- [Quay lại đầu trang](#back-to-top)
