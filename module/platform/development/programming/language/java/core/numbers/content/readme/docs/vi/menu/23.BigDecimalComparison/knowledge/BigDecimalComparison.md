<a id="back-to-top"></a>

# So sánh BigDecimal

## Menu
- [compareTo so sánh giá trị số](#big-decimal-comparison-001)
- [equals đồng thời xét giá trị và scale](#big-decimal-comparison-002)
- [Ví dụ 2.0 và 2.00 theo từng cách so sánh](#big-decimal-comparison-003)
- [hashCode và hệ quả trong HashSet hoặc HashMap](#big-decimal-comparison-004)
- [Thứ tự tự nhiên không nhất quán với equals](#big-decimal-comparison-005)
- [Hệ quả khi dùng TreeSet hoặc TreeMap](#big-decimal-comparison-006)
- [Chính sách chuẩn hóa scale trước khi lưu hoặc so sánh](#big-decimal-comparison-007)
- [Kiểm tra số 0 bằng signum hoặc compareTo](#big-decimal-comparison-008)
- [So sánh null và lựa chọn hợp đồng API](#big-decimal-comparison-009)
- [Không thay đổi equals toàn hệ thống chỉ để giải quyết một phép so sánh cụ thể](#big-decimal-comparison-010)

## <a id="big-decimal-comparison-001">compareTo so sánh giá trị số</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.compareTo(): so sánh giá trị số trong Java

## 1. Vấn đề cần giải quyết

Trong Java, `BigDecimal` lưu giá trị thập phân có độ chính xác tùy ý bằng một **số nguyên không có scale** (`unscaledValue`) và một **scale** (số mũ thập phân âm). Giá trị toán học có thể hình dung là `unscaledValue × 10^(-scale)`. Cùng một số có thể mang những cách biểu diễn khác nhau:

- `new BigDecimal("2.0")`: giá trị số 2; unscaled value 20; scale 1.
- `new BigDecimal("2.00")`: giá trị số 2; unscaled value 200; scale 2.
- `new BigDecimal("2")`: giá trị số 2; unscaled value 2; scale 0.

Khi cần trả lời “giá trị nào lớn hơn?”, “có bằng nhau về **giá trị số** không?” hoặc “số này đã vượt ngưỡng chưa?”, dùng **`BigDecimal.compareTo(BigDecimal other)`**. Phương thức so sánh **giá trị số**, nên coi `2.0`, `2.00` và `2` ngang nhau về thứ tự, dù scale khác nhau.

Đây là điểm khác biệt quan trọng so với `equals(Object)`, vốn xét cả giá trị số lẫn scale. Chọn đúng phép so sánh giúp tránh lỗi khi dữ liệu đến từ những nguồn có định dạng thập phân không đồng nhất.

## 2. Cách đọc kết quả compareTo

Cú pháp:

~~~java
int result = left.compareTo(right);
~~~

Kết quả có ba nhóm ý nghĩa:

| Điều kiện | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `result < 0` | `left` nhỏ hơn `right` | `1.25` so với `1.30` |
| `result == 0` | Hai đối tượng có **cùng giá trị số** | `2.0` so với `2.00` |
| `result > 0` | `left` lớn hơn `right` | `9.5` so với `9.05` |

Hợp đồng của `Comparable.compareTo` yêu cầu kết quả **âm, 0 hoặc dương**; khi kiểm tra điều kiện, viết `< 0` hoặc `> 0`, không phụ thuộc vào việc phương thức trả chính xác `-1` hay `1`.

Hướng so sánh theo **trục số có dấu**:

- `-8.0 < -2.00` vì -8 nằm bên trái -2.
- `-0.001 < 0` dù giá trị tuyệt đối rất nhỏ.
- `0.000` và `0` bằng nhau về giá trị số.
- `10.00 > 9.999` dù số phía phải có nhiều chữ số thập phân hơn.

**Scale không quyết định số lớn hay nhỏ.** Không thể so sánh hai `BigDecimal` bằng cách lấy scale, độ dài chuỗi, số chữ số thập phân hay lexicographical ordering.

## 3. Ví dụ Java 17 chạy được

Lưu đoạn sau thành tệp `BigDecimalCompareDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class BigDecimalCompareDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println("compareTo = " + a.compareTo(b));
        System.out.println("equals = " + a.equals(b));
        System.out.println("scales = " + a.scale() + ", " + b.scale());

        BigDecimal negative = new BigDecimal("-1.25");
        System.out.println("negative < zero = "
                + (negative.compareTo(BigDecimal.ZERO) < 0));
        System.out.println("3.50 > 3.05 = "
                + (new BigDecimal("3.50")
                        .compareTo(new BigDecimal("3.05")) > 0));
        System.out.println("1.20 < 1.3 = "
                + (new BigDecimal("1.20")
                        .compareTo(new BigDecimal("1.3")) < 0));

        List<BigDecimal> values = new ArrayList<>(List.of(
                new BigDecimal("10.0"),
                new BigDecimal("2.00"),
                new BigDecimal("-1.2"),
                new BigDecimal("2.0")
        ));
        values.sort(BigDecimal::compareTo);
        System.out.println("sorted = " + values);

        Set<BigDecimal> hashSet = new HashSet<>(List.of(a, b));
        Set<BigDecimal> treeSet = new TreeSet<>(List.of(a, b));
        System.out.println("HashSet size = " + hashSet.size());
        System.out.println("TreeSet size = " + treeSet.size());

        BigDecimal representedZero = new BigDecimal("0.000");
        System.out.println("numeric zero = "
                + (representedZero.compareTo(BigDecimal.ZERO) == 0));
        System.out.println("normalized b = "
                + b.stripTrailingZeros().toPlainString());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac BigDecimalCompareDemo.java
java BigDecimalCompareDemo
~~~

Kết quả:

~~~text
compareTo = 0
equals = false
scales = 1, 2
negative < zero = true
3.50 > 3.05 = true
1.20 < 1.3 = true
sorted = [-1.2, 2.00, 2.0, 10.0]
HashSet size = 2
TreeSet size = 1
numeric zero = true
normalized b = 2
~~~

Giải thích theo từng nhóm:

1. `a.compareTo(b) == 0`: `2.0` và `2.00` có cùng **giá trị số**.
2. `a.equals(b) == false`: hai đối tượng biểu diễn cùng số 2 nhưng có scale lần lượt là 1 và 2.
3. Các so sánh âm/dương dùng `< 0` hoặc `> 0`, không cần đổi sang `double`.
4. `values.sort(BigDecimal::compareTo)` sắp xếp tăng theo số. Hai phần tử `2.00` và `2.0` có thứ tự bằng nhau theo comparator; `List.sort` ổn định nên giữ thứ tự tương đối ban đầu của chúng.
5. `HashSet` coi `2.0` và `2.00` là hai phần tử khác nhau dựa trên `equals` và `hashCode`; `TreeSet` dùng thứ tự tự nhiên do `compareTo` và coi chúng trùng nhau.
6. `stripTrailingZeros()` đưa `2.00` về biểu diễn không có số 0 thừa; `toPlainString()` in thành `2`. Thao tác này không sửa `b` vì `BigDecimal` là immutable.

## 4. compareTo và equals: khác nhau ở đâu?

Hai phương thức giải quyết hai câu hỏi khác nhau:

| Phương thức | Câu hỏi | `2.0` và `2.00` |
| --- | --- | --- |
| `compareTo` | Hai số lớn, nhỏ hay **bằng nhau về mặt số học**? | `compareTo(...) == 0` |
| `equals` | Hai `BigDecimal` có cùng giá trị và cùng **scale** không? | `equals(...) == false` |
| `hashCode` | Mã băm tuân theo quy ước bằng nhau của `equals`? | Có thể khác; không dùng mã băm để so sánh số |

Với `BigDecimal`, `equals` chỉ trả true nếu số được biểu diễn có giá trị bằng nhau **và scale bằng nhau**; `compareTo` không yêu cầu cùng scale. Điều này được Java ghi rõ: natural ordering của `BigDecimal` **không nhất quán với `equals`** (`inconsistent with equals`).

~~~java
BigDecimal priceA = new BigDecimal("19.9");
BigDecimal priceB = new BigDecimal("19.90");

boolean sameNumericValue = priceA.compareTo(priceB) == 0; // true
boolean sameRepresentation = priceA.equals(priceB);         // false
~~~

Đừng thay `compareTo` bằng `equals` chỉ vì đang cần kiểm tra hai **số** có bằng nhau hay không. Ngược lại, khi yêu cầu của chương trình coi scale là một phần quan trọng của thông tin (ví dụ kiểm tra đối tượng có đúng biểu diễn cần lưu hay không), `equals` có thể là lựa chọn chủ ý.

### Một trường hợp dễ ngạc nhiên khi thao tác

Cùng một số có thể cho kết quả `divideToIntegralValue` hay các phép toán dựa vào biểu diễn với scale đầu ra khác nhau tùy scale toán hạng. Thế nên “`compareTo == 0`” **không đảm bảo mọi thao tác trên hai đối tượng sẽ tạo ra kết quả có cùng scale**. Không suy diễn từ bằng nhau về giá trị số thành đồng nhất về biểu diễn.

## 5. So sánh với 0, ngưỡng và khoảng

`BigDecimal.ZERO` là hằng số biểu diễn số 0; so sánh với nó rõ ràng và tránh tạo đối tượng không cần thiết.

~~~java
BigDecimal balance = new BigDecimal("-0.01");

boolean isNegative = balance.compareTo(BigDecimal.ZERO) < 0;
boolean isZero = balance.compareTo(BigDecimal.ZERO) == 0;
boolean isPositive = balance.compareTo(BigDecimal.ZERO) > 0;
~~~

Các phép so sánh thường gặp:

| Yêu cầu | Biểu thức |
| --- | --- |
| `x < y` | `x.compareTo(y) < 0` |
| `x <= y` | `x.compareTo(y) <= 0` |
| `x == y` theo giá trị số | `x.compareTo(y) == 0` |
| `x >= y` | `x.compareTo(y) >= 0` |
| `x > y` | `x.compareTo(y) > 0` |
| `x != y` theo giá trị số | `x.compareTo(y) != 0` |

Ví dụ chấp nhận đơn hàng nếu số tiền đạt tối thiểu `100.00`:

~~~java
BigDecimal amount = new BigDecimal("100.0");
BigDecimal minimum = new BigDecimal("100.00");

if (amount.compareTo(minimum) >= 0) {
    System.out.println("Đạt ngưỡng");
}
~~~

Kết quả là `Đạt ngưỡng` vì giá trị số bằng nhau, dù scale khác nhau. Nếu cần kiểm tra khoảng đóng `[min, max]`, dùng:

~~~java
boolean inRange = value.compareTo(min) >= 0
        && value.compareTo(max) <= 0;
~~~

Đây là các **mảnh code minh họa**, với biến `value`, `min`, `max` được giả định đã là `BigDecimal` không null và khoảng `min <= max`. Để thực sự chạy, đặt các mảnh này vào phương thức hoặc chương trình khai báo đủ biến.

## 6. Sắp xếp và collection: hậu quả của sự khác biệt

`BigDecimal` triển khai `Comparable<BigDecimal>`, vì thế Java dùng `compareTo` làm **thứ tự tự nhiên**:

~~~java
List<BigDecimal> values = new ArrayList<>(List.of(
        new BigDecimal("9.00"),
        new BigDecimal("1.1"),
        new BigDecimal("1.10")
));
values.sort(BigDecimal::compareTo);
~~~

Kết quả được sắp tăng theo giá trị số: `[1.1, 1.10, 9.00]`. Hai giá trị `1.1` và `1.10` có `compareTo == 0` nên không có thứ tự số học khác nhau; một sort ổn định giữ thứ tự của chúng như trước khi sắp.

Tuy nhiên, **collection nào quyết định “trùng” theo cách nào** rất quan trọng:

- `HashSet<BigDecimal>` và `HashMap<BigDecimal, ...>` dựa vào `equals` và `hashCode` của khóa. `new BigDecimal("2.0")` khác `new BigDecimal("2.00")` theo tiêu chí này, nên hai khóa có thể cùng tồn tại.
- `TreeSet<BigDecimal>` và `TreeMap<BigDecimal, ...>` theo thứ tự tự nhiên dùng `compareTo` (hoặc comparator được cung cấp) để nhận diện hai khóa có cùng vị trí so sánh. `2.0` và `2.00` được xem là cùng một khóa, vì `compareTo == 0`.
- Nếu `TreeMap` được đưa khóa `2.00` sau khi đã chứa `2.0`, giá trị được ghi vào mục khóa có sẵn theo tiêu chí comparator; đừng mong tự động giữ hai mục độc lập.
- `Stream.distinct()` dùng `equals`, nên riêng thao tác này vẫn có thể giữ cả hai cách biểu diễn `2.0` và `2.00`. Sắp xếp bằng `compareTo` trước đó không tự đổi quy tắc `distinct()`.

Nếu nghiệp vụ coi các số thập phân cùng giá trị là cùng một danh tính, hãy xác định cách **chuẩn hóa** khóa trước khi dùng collection theo `equals`, hoặc chọn `TreeMap`/`TreeSet` theo comparator phù hợp. Tránh trộn lẫn nhiều quy tắc định danh mà không mô tả rõ.

### Chuẩn hóa số 0 cuối

`stripTrailingZeros()` trả về một `BigDecimal` tương đương về số học với các chữ số 0 cuối trong phần unscaled value đã được lược bỏ:

~~~java
BigDecimal first = new BigDecimal("2.0").stripTrailingZeros();
BigDecimal second = new BigDecimal("2.00").stripTrailingZeros();

System.out.println(first.equals(second)); // true
~~~

Lưu ý:

- Chuẩn hóa không mất **giá trị số**, nhưng có thể làm đổi **scale** và dạng hiển thị.
- Ví dụ `new BigDecimal("600.0").stripTrailingZeros()` có thể biểu diễn thành `6E+2` với scale âm, thay vì chuỗi `600.0`. `toPlainString()` dùng khi cần cách in không có số mũ.
- `stripTrailingZeros()` của giá trị số 0 trả về biểu diễn zero chuẩn, có thể làm mất scale ban đầu của `0.000`.
- Nếu scale cố định là điều kiện nghiệp vụ hoặc hợp đồng dữ liệu, không tự chuẩn hóa mọi số mà chưa đánh giá tác động. Khi dữ liệu bắt buộc có scale 2, `setScale(2)` chỉ thành công không cần rounding mode nếu có thể đạt scale 2 mà **không làm tròn**; các giá trị khác có thể cần quy tắc làm tròn rõ ràng.

## 7. Dữ liệu đầu vào và tính chính xác

Hãy tạo `BigDecimal` từ dữ liệu thập phân chính xác như chuỗi JSON hoặc chuỗi nhập vào:

~~~java
BigDecimal expected = new BigDecimal("0.3");
BigDecimal actual = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));

System.out.println(actual.compareTo(expected) == 0); // true
~~~

Nếu thay bằng `double` trước, các giá trị thập phân như `0.1` và `0.2` thường không biểu diễn chính xác được ở nhị phân dấu phẩy động. `BigDecimal.valueOf(double)` chuyển từ chuỗi thập phân chuẩn của giá trị `double`, hữu ích khi đầu vào đã ở dạng đó; nhưng không thể khôi phục độ chính xác đã mất do tính toán trước đó trong `double`.

Không nên dùng:

~~~java
double x = 0.1 + 0.2;
double y = 0.3;
boolean equal = (x == y); // Thường là false do biểu diễn nhị phân.
~~~

Trường hợp này minh họa riêng cho `double`. Với `BigDecimal` xây từ chuỗi và phép cộng chính xác không có `MathContext`, phép so sánh số học cho kết quả đúng như ví dụ ở trên.

Về nguyên tắc, `compareTo` **không làm tròn** hai số để so sánh và không yêu cầu hai bên có cùng scale. Nếu muốn so sánh các số *sau* khi đã được làm tròn theo quy tắc nghiệp vụ, phải làm tròn từng giá trị trước, rồi gọi `compareTo`; đó là một yêu cầu khác với so sánh giá trị gốc.

## 8. Các lưu ý khi thiết kế API

### Null không phải giá trị số

`BigDecimal` là lớp tham chiếu. Nếu đối tượng gọi phương thức là `null`, biểu thức `amount.compareTo(other)` gây `NullPointerException`; truyền `null` làm đối số so sánh cũng gây `NullPointerException`. Xử lý null theo hợp đồng dữ liệu trước khi so sánh:

~~~java
if (amount == null || minimum == null) {
    throw new IllegalArgumentException("Thiếu giá trị tiền");
}
boolean accepted = amount.compareTo(minimum) >= 0;
~~~

Đoạn trên là ví dụ kiểm tra đầu vào, với `amount` và `minimum` được cung cấp từ nơi khác.

### BigDecimal là immutable

`compareTo` chỉ **quan sát thứ tự** giữa hai giá trị, không sửa `BigDecimal` nào. Các phương thức như `stripTrailingZeros`, `setScale`, `add` cũng trả đối tượng kết quả mới (hoặc đối tượng có thể tái dùng) mà không thay đổi giá trị của đối tượng nhận.

### Không dùng toán tử < hoặc >

Toán tử `<`, `>`, `<=`, `>=` hoạt động trên kiểu số nguyên thủy và các phép chuyển kiểu thích hợp; chúng **không** được overload cho `BigDecimal`. Viết `a > b` với hai tham chiếu `BigDecimal` là lỗi biên dịch. Dùng `a.compareTo(b) > 0`.

### Không lấy chênh lệch rồi chuyển sang int để so sánh

Biểu thức kiểu `a.subtract(b).intValue()` có thể trả 0 dù hai số không bằng nhau hoặc làm mất thông tin do cắt phần thập phân/vượt khoảng kiểu int. `compareTo` diễn đạt trực tiếp mục đích, tránh phép tính trung gian không cần thiết.

### Thứ tự có tính bắc cầu

Nếu `a.compareTo(b) < 0` và `b.compareTo(c) < 0` thì `a.compareTo(c) < 0`. Đây là phần thiết yếu của hợp đồng so sánh, cho phép dùng với sắp xếp và cấu trúc dữ liệu có thứ tự. Khi viết comparator riêng để thay thế, hãy giữ tính đối xứng về dấu và bắc cầu; tránh comparator dựa trên `doubleValue()` nếu điều đó làm mất phân biệt giữa hai giá trị `BigDecimal` rất gần hoặc rất lớn.

## 9. Lỗi phổ biến và cách sửa

**Lỗi: dùng `equals` cho hai số nhập với scale không đồng nhất.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là false. Nếu chỉ muốn biết hai số bằng nhau, dùng `compareTo(...) == 0`.

**Lỗi: so sánh qua `toString()`.** `"10.0".compareTo("2.0") < 0` do so chuỗi theo ký tự, dù 10.0 lớn hơn 2.0. Luôn dùng `BigDecimal.compareTo` cho thứ tự số học.

**Lỗi: tưởng `compareTo == 0` có nghĩa là `equals == true`.** Đây là nguyên nhân kết quả khác nhau giữa `HashSet` và `TreeSet`. Hãy chọn tiêu chí định danh, tập hợp và chuẩn hóa cho nhất quán.

**Lỗi: viết `compareTo(...) == 1`.** Tốt nhất so `> 0` hoặc `< 0` theo hợp đồng của `Comparable`; điều này cũng đúng với comparator tùy chỉnh.

**Lỗi: đổi sang `double` hoặc `long` để so sánh.** Việc chuyển kiểu có thể gây mất độ chính xác, cắt phần thập phân hoặc vượt phạm vi. `BigDecimal.compareTo` giữ nguyên miền số thập phân có độ chính xác tùy ý.

**Lỗi: mặc định null bằng 0.** Null là trạng thái không có dữ liệu, khác với `BigDecimal.ZERO`. Chỉ chuyển null thành 0 nếu đặc tả ứng dụng cho phép.

**Lỗi: sắp xếp rồi dùng `distinct()` để gộp các số bằng nhau.** `distinct()` vẫn dựa trên `equals`; `2.0` và `2.00` có thể cùng tồn tại sau thao tác đó.

## 10. Câu hỏi tự kiểm tra

Trước khi chạy thử, hãy xác định dấu của `left.compareTo(right)`:

| left | right | Dấu của kết quả | `left.equals(right)` |
| --- | --- | --- | --- |
| `3.0` | `3.00` | `0` | `false` |
| `-3.00` | `-2.99` | `< 0` | `false` |
| `0.000` | `0` | `0` | `false` |
| `0.100` | `0.10` | `0` | `false` |
| `100.01` | `100.001` | `> 0` | `false` |
| `7.00` | `7.00` | `0` | `true` |

Để thực hành, tự viết biểu thức xác định “âm”, “dương”, “bằng 0”, “ít nhất bằng ngưỡng” và thử nhập các chuỗi có khác biệt ở số 0 cuối. Sau đó đưa hai số `2.0`, `2.00` vào `HashSet` và `TreeSet` để tự quan sát khác biệt.

**Ghi nhớ:** `BigDecimal.compareTo` là phép so sánh **theo giá trị số**; kết quả âm/0/dương biểu thị nhỏ hơn/bằng/lớn hơn. Khi cần bằng nhau về số học, dùng `compareTo(other) == 0`. `equals` còn quan tâm đến scale, vì vậy lựa chọn giữa hai phương thức ảnh hưởng trực tiếp đến collection, khóa và logic ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-002">equals đồng thời xét giá trị và scale</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal.equals()` đồng thời xét giá trị và scale

Khi làm việc với `BigDecimal`, hai đối tượng có thể **bằng nhau về giá trị số học** nhưng `equals()` vẫn trả về `false`. Lý do: `BigDecimal.equals(Object)` xét **cả giá trị và `scale`**. Vì vậy `new BigDecimal("2.0")` không `equals()` với `new BigDecimal("2.00")`, dù hai số đều có giá trị 2.

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.equals(b));         // false
System.out.println(a.compareTo(b) == 0); // true
```

Việc phân biệt này ảnh hưởng trực tiếp đến điều kiện so sánh, `HashSet`/`TreeSet`, khóa `HashMap`/`TreeMap` và cách thiết kế `equals`/`hashCode` cho lớp nghiệp vụ.

## 1. Giá trị số học và scale là hai thuộc tính khác nhau

Một `BigDecimal` có dạng:

```text
giá trị = unscaledValue × 10^(-scale)
```

`unscaledValue` là hệ số nguyên, còn `scale` xác định vị trí dấu thập phân trong biểu diễn.

| Cách tạo | `unscaledValue()` | `scale()` | Giá trị số học |
|---|---:|---:|---:|
| `new BigDecimal("2")` | 2 | 0 | 2 |
| `new BigDecimal("2.0")` | 20 | 1 | 2 |
| `new BigDecimal("2.00")` | 200 | 2 | 2 |
| `new BigDecimal("2E+0")` | 2 | 0 | 2 |
| `new BigDecimal("1000")` | 1000 | 0 | 1000 |
| `new BigDecimal("1E+3")` | 1 | -3 | 1000 |

Quy tắc `equals()`:

- Đối số phải là một `BigDecimal`.
- Giá trị số học phải bằng nhau.
- **Scale phải bằng nhau**.

Hệ quả:

```java
new BigDecimal("2.0").equals(new BigDecimal("2.00")); // false
new BigDecimal("2.00").equals(new BigDecimal("2.00")); // true
new BigDecimal("2").equals(new BigDecimal("2E+0"));  // true
new BigDecimal("1000").equals(new BigDecimal("1E+3")); // false
```

Scale có thể âm, bằng 0 hoặc dương. Vì vậy, không nên định nghĩa scale đơn giản là “có bao nhiêu chữ số sau dấu thập phân trong chuỗi nhập”; mô hình `unscaledValue × 10^(-scale)` bao quát cả ký pháp khoa học.

**Ngay cả số 0 cũng có scale**: `new BigDecimal("0.00")` có scale 2; `BigDecimal.ZERO` có scale 0. Chúng cùng giá trị toán học 0 nhưng `equals()` trả `false`.

## 2. `equals()` và `compareTo()` trả lời hai câu hỏi khác nhau

- `a.equals(b)`: hai biểu diễn có **cùng giá trị và cùng scale** không?
- `a.compareTo(b)`: theo **giá trị số học**, `a` nhỏ hơn, bằng hay lớn hơn `b`?

`compareTo()` trả số âm, 0 hoặc số dương; **0** nghĩa là hai giá trị số học bằng nhau. Nó không yêu cầu scale trùng nhau.

| `a` | `b` | `a.equals(b)` | `a.compareTo(b)` |
|---|---|---|---:|
| `2.0` | `2.00` | `false` | 0 |
| `2.00` | `2.00` | `true` | 0 |
| `0.0` | `0` | `false` | 0 |
| `1000` | `1E+3` | `false` | 0 |
| `1.99` | `2.00` | `false` | Số âm |
| `2.01` | `2.00` | `false` | Số dương |

Cần so sánh theo **giá trị số học**:

```java
boolean sameNumericValue = a.compareTo(b) == 0;
boolean smaller = a.compareTo(b) < 0;
boolean greater = a.compareTo(b) > 0;
```

Đừng giả định `compareTo()` luôn trả chính xác `-1` hay `1`; hợp đồng `Comparable` chỉ đảm bảo dấu của kết quả.

Cần so sánh theo **cả giá trị và scale**, ví dụ kiểm tra biểu diễn có đúng số chữ số thập phân quy định:

```java
boolean sameRepresentationValue = a.equals(b);
```

Java công bố rõ rằng **thứ tự tự nhiên của `BigDecimal` không nhất quán với `equals()`**: có những cặp mà `compareTo() == 0` nhưng `equals() == false`. Đây là hành vi thiết kế có chủ đích.

## 3. `equals()`, `hashCode()` và hệ quả đối với collection

Quy tắc chung của Java: nếu `a.equals(b)` thì `a.hashCode() == b.hashCode()`. `BigDecimal.hashCode()` xét cả scale, nên `2.0` và `2.00` có thể có hash code khác nhau. Không được kết luận ngược rằng hai hash code bằng nhau thì hai đối tượng chắc chắn `equals()`, vì có thể xảy ra va chạm hash.

Hai nhóm collection sử dụng tiêu chí khác nhau:

| Cấu trúc | Tiêu chí tương đương mặc định | `2.0` và `2.00` |
|---|---|---|
| `HashSet<BigDecimal>` | `equals()` / `hashCode()` | Hai phần tử |
| `HashMap<BigDecimal, V>` | `equals()` / `hashCode()` | Hai khóa |
| `TreeSet<BigDecimal>` | `compareTo() == 0` | Một phần tử |
| `TreeMap<BigDecimal, V>` | `compareTo() == 0` | Một khóa tương đương |

Ví dụ:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

Set<BigDecimal> hash = new HashSet<>(List.of(a, b));
Set<BigDecimal> tree = new TreeSet<>(List.of(a, b));

System.out.println(hash.size()); // 2
System.out.println(tree.size()); // 1
```

`HashSet` giữ cả hai vì `equals()` trả `false`. `TreeSet` sử dụng thứ tự tự nhiên nên `compareTo() == 0` làm hai giá trị trở thành một phần tử tương đương theo thứ tự sắp xếp.

Với `Map` cũng tương tự. Nếu `map.put(new BigDecimal("2.0"), "A")` trên một `HashMap`, gọi `map.get(new BigDecimal("2.00"))` không tìm được ánh xạ tương ứng. Trên `TreeMap` dùng thứ tự tự nhiên, `put` với khóa thứ hai sẽ thay giá trị gắn với khóa tương đương đầu tiên.

**Cạm bẫy quan trọng:** chuyển giữa `HashSet` và `TreeSet` hoặc `HashMap` và `TreeMap` có thể làm thay đổi số phần tử/khóa được xem là duy nhất khi kiểu khóa là `BigDecimal`.

## 4. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp `BigDecimalEqualsDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalEqualsDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println("a.scale() = " + a.scale());
        System.out.println("b.scale() = " + b.scale());
        System.out.println("a.equals(b) = " + a.equals(b));
        System.out.println("a.compareTo(b) = " + a.compareTo(b));
        System.out.println("numeric equality = " + (a.compareTo(b) == 0));

        Set<BigDecimal> hash = new HashSet<>(List.of(a, b));
        Set<BigDecimal> tree = new TreeSet<>(List.of(a, b));
        System.out.println("HashSet size = " + hash.size());
        System.out.println("TreeSet size = " + tree.size());

        Map<BigDecimal, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        System.out.println("HashMap get b = " + hashMap.get(b));

        Map<BigDecimal, String> treeMap = new TreeMap<>();
        treeMap.put(a, "first");
        treeMap.put(b, "second");
        System.out.println("TreeMap size = " + treeMap.size());
        System.out.println("TreeMap get a = " + treeMap.get(a));

        BigDecimal normalizedA = a.stripTrailingZeros();
        BigDecimal normalizedB = b.stripTrailingZeros();
        System.out.println("normalized equals = "
                + normalizedA.equals(normalizedB));

        BigDecimal fixedScale = a.setScale(2);
        System.out.println("setScale(2) equals b = "
                + fixedScale.equals(b));

        BigDecimal normalizedZero =
                new BigDecimal("0.00").stripTrailingZeros();
        System.out.println("normalized zero = " + normalizedZero
                + ", scale=" + normalizedZero.scale());

        BigDecimal normalizedThousand =
                new BigDecimal("1000.00").stripTrailingZeros();
        System.out.println("normalized thousand = " + normalizedThousand
                + ", scale=" + normalizedThousand.scale());
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalEqualsDemo.java
java BigDecimalEqualsDemo
```

Stdout chính xác:

```text
a.scale() = 1
b.scale() = 2
a.equals(b) = false
a.compareTo(b) = 0
numeric equality = true
HashSet size = 2
TreeSet size = 1
HashMap get b = null
TreeMap size = 1
TreeMap get a = second
normalized equals = true
setScale(2) equals b = true
normalized zero = 0, scale=0
normalized thousand = 1E+3, scale=-3
```

Phần `TreeMap` chỉ giữ một khóa tương đương theo `compareTo`; lần `put(b, "second")` cập nhật giá trị từ `"first"` thành `"second"`. Trong khi đó, `HashMap.get(b)` trả `null` vì khóa ban đầu `a` có scale khác `b`.

Phần `stripTrailingZeros()` cho thấy khi loại các số 0 ở cuối hệ số, `2.0` và `2.00` về cùng biểu diễn `2`. `setScale(2)` lại đổi `2.0` thành `2.00` mà không phải làm tròn.

## 5. Chọn cách so sánh theo quy tắc nghiệp vụ

### Trường hợp A: chỉ quan tâm giá trị số học

Ví dụ, hai tổng tiền có cùng giá trị dù một nguồn trả `125.0` còn nguồn khác trả `125.00`:

```java
BigDecimal fromA = new BigDecimal("125.0");
BigDecimal fromB = new BigDecimal("125.00");

boolean sameAmount = fromA.compareTo(fromB) == 0; // true
```

Nếu chỉ cần kiểm tra bằng nhau tại một thời điểm, `compareTo() == 0` rõ ý nghĩa và không cần thay đổi dữ liệu.

### Trường hợp B: cần dùng khóa cho `HashMap` hoặc phần tử `HashSet`

Nếu muốn `125`, `125.0`, `125.00` cùng một khóa thì phải **chuẩn hóa khi đưa vào và khi tra cứu**. Một cách là `stripTrailingZeros()`:

```java
Map<BigDecimal, String> lookup = new HashMap<>();

BigDecimal entered = new BigDecimal("125.00");
lookup.put(entered.stripTrailingZeros(), "matched");

BigDecimal query = new BigDecimal("125.0");
String found = lookup.get(query.stripTrailingZeros());
// found = "matched"
```

`stripTrailingZeros()` giữ nguyên giá trị toán học nhưng chuẩn hóa biểu diễn:

- `2.0` và `2.00` đều thành `2`.
- Số 0 ở bất kỳ scale nào trở thành `BigDecimal.ZERO`, scale 0.
- `1000.00` có thể trở thành `1E+3`, scale -3. Scale âm là hợp lệ, và cách hiển thị có thể đổi sang ký pháp khoa học.

`stripTrailingZeros()` không sửa đối tượng gốc vì `BigDecimal` là kiểu bất biến; hãy dùng giá trị trả về.

### Trường hợp C: cần chuẩn hóa đúng scale theo miền nghiệp vụ

Nếu quy ước số tiền phải có **scale 2**, bạn có thể chuẩn hóa về scale 2:

```java
BigDecimal a = new BigDecimal("125.0").setScale(2);
BigDecimal b = new BigDecimal("125.00").setScale(2);
System.out.println(a.equals(b)); // true
```

Việc **tăng** scale bằng cách thêm các số 0 ở cuối không đổi giá trị. Nhưng **giảm** scale không phải lúc nào cũng chính xác:

```java
new BigDecimal("125.000").setScale(2); // 125.00: chính xác
new BigDecimal("125.001").setScale(2); // ArithmeticException
```

Để cho phép mất phần chữ số dư, cần có quy tắc làm tròn rõ ràng, ví dụ `setScale(2, RoundingMode.HALF_UP)`. Không tự làm tròn nếu mục tiêu là đánh giá liệu **hai số gốc** có bằng nhau hay không: sau làm tròn, hai số khác nhau có thể thành cùng một số.

### Trường hợp D: ứng dụng cần bảo toàn scale

Trong dữ liệu đo lường, hợp đồng hoặc định dạng nhập, `1.0` và `1.00` đôi khi có nghĩa biểu diễn khác nhau. Nếu quy tắc nghiệp vụ yêu cầu phân biệt chúng, dùng `equals()` và không chuẩn hóa xóa phần scale. Quyết định này phải nhất quán trong so sánh, lưu trữ và lập chỉ mục.

## 6. Khi `BigDecimal` là trường trong `equals()`/`hashCode()` của lớp khác

Một lỗi tinh vi: lớp nghiệp vụ dùng `amount.compareTo(other.amount) == 0` trong `equals()`, nhưng lại lấy `amount.hashCode()` chưa chuẩn hóa trong `hashCode()`. Khi `2.0` và `2.00` được coi là bằng nhau theo `equals()` mà có hash code khác nhau, lớp đó vi phạm hợp đồng hash của Java. `HashMap` và `HashSet` có thể xử lý sai kỳ vọng.

Cách đơn giản là **chuẩn hóa một lần lúc tạo khóa**, sau đó để `equals()` và `hashCode()` tự động dùng cùng biểu diễn chuẩn:

```java
import java.math.BigDecimal;

public record AmountKey(BigDecimal value) {
    public AmountKey {
        value = value.stripTrailingZeros();
    }
}
```

Đây là ví dụ độc lập về kiểu `record` trong Java 17. Với `value` khác `null`, hai khóa `new AmountKey(new BigDecimal("2.0"))` và `new AmountKey(new BigDecimal("2.00"))` có cùng trạng thái chuẩn hóa; `record` tự sinh `equals()` và `hashCode()` nhất quán. Nếu cần nhận `null`, lớp phải quy định và xử lý trường hợp đó riêng.

Nếu ứng dụng cần phân biệt scale thì không dùng cách chuẩn hóa này: nên giữ giá trị và scale như dữ liệu gốc trong phép so sánh.

## 7. Lỗi thường gặp và cách tránh

1. **Dùng `==` với hai đối tượng `BigDecimal`.** `==` so sánh tham chiếu, không phải giá trị. Dùng `equals()` hoặc `compareTo()` theo mục đích.
2. **Nhầm `equals()` với `compareTo() == 0`.** Một bên xét cả scale; một bên chỉ xét giá trị số học.
3. **Dùng `HashMap` với scale nhập vào không nhất quán.** Khóa lưu là `5.00` nhưng tra là `5.0` có thể không tìm được. Chuẩn hóa cả hai đầu nếu chỉ quan tâm giá trị.
4. **Đổi `HashSet` thành `TreeSet` rồi tưởng dữ liệu bị mất ngẫu nhiên.** Số phần tử khác nhau xuất phát từ cơ chế tương đương của hai collection.
5. **Giả định `stripTrailingZeros()` giữ nguyên scale hoặc `toString()`.** Giá trị không đổi, nhưng `1000.00` có thể thành `1E+3` với scale -3.
6. **Gọi `setScale` mà không xử lý trường hợp cần làm tròn.** Nếu cần giảm scale và phải bỏ chữ số khác 0, quá trình sẽ ném `ArithmeticException` nếu không có chế độ làm tròn.
7. **Chuyển sang `doubleValue()` để so sánh.** `double` có thể mất chính xác; hãy giữ phép so sánh trong miền `BigDecimal`.
8. **Tưởng mọi cách tạo số 2 đều có scale giống nhau.** `BigDecimal.valueOf(2.0)` có scale 1, còn `BigDecimal.valueOf(2L)` có scale 0.
9. **Quên trường hợp `null`.** `a.compareTo(b)` đòi các đối số hợp lệ. `Objects.equals(a, b)` xử lý `null` nhưng vẫn dùng semantics `equals()`, nên vẫn xét scale khi cả hai là `BigDecimal`.

## 8. Tự kiểm tra

**1.** `new BigDecimal("0.00").equals(BigDecimal.ZERO)` trả gì?  
**Đáp án:** `false`, vì scale 2 khác scale 0. Tuy vậy `compareTo() == 0`.

**2.** `new BigDecimal("1E+3").equals(new BigDecimal("1000"))` trả gì?  
**Đáp án:** `false`, vì scale -3 khác scale 0, dù cả hai cùng giá trị 1000.

**3.** Thêm `"1"`, `"1.0"`, `"1.00"` vào `HashSet<BigDecimal>` và `TreeSet<BigDecimal>` mặc định thì kích thước là bao nhiêu?  
**Đáp án:** `HashSet` là 3, `TreeSet` là 1.

**4.** Vì sao không nên để lớp nghiệp vụ dùng `compareTo() == 0` làm `equals()` nhưng dùng `BigDecimal.hashCode()` gốc?  
**Đáp án:** Các số khác scale có thể `equals() == true` nhưng hash code khác nhau, vi phạm hợp đồng của Java.

**5.** Khi cần phân biệt người dùng nhập `"2.0"` và `"2.00"`, nên dùng gì?  
**Đáp án:** `equals()` vì scale là một phần của yêu cầu so sánh.

**Ghi nhớ:** `BigDecimal.equals()` yêu cầu **cùng giá trị số học và cùng scale**. `BigDecimal.compareTo() == 0` chỉ yêu cầu **cùng giá trị số học**. Chọn tiêu chí theo mục đích trước khi so sánh hoặc chọn loại `Set`/`Map`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-003">Ví dụ 2.0 và 2.00 theo từng cách so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ 2.0 và 2.00 theo từng cách so sánh `BigDecimal` trong Java

## 1. Vấn đề: bằng nhau về giá trị nhưng khác cách biểu diễn

Trong Java, hai đối tượng `BigDecimal` này đều mang giá trị số học bằng 2:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");
```

Nhưng kết quả của từng cách so sánh **không giống nhau**:

```java
a == b;             // false: hai đối tượng khác tham chiếu
a.equals(b);        // false: BigDecimal xét cả giá trị và scale
a.compareTo(b);     // 0: cùng giá trị số học
a.compareTo(b) == 0; // true: bằng nhau theo thứ tự số học
```

Điều quan trọng là phải xác định từ đầu “bằng nhau” đang có ý nghĩa gì: **cùng đối tượng**, **cùng giá trị và cùng scale**, hay **cùng giá trị số học**. Đây là ba câu hỏi riêng, với các cách kiểm tra riêng.

Bài học này dùng `2.0` và `2.00` để quan sát trực tiếp từng kết quả, giải thích lý do và chỉ ra ảnh hưởng tới `HashSet`, `TreeSet`, bản đồ khóa và dữ liệu nghiệp vụ.

## 2. Vì sao `BigDecimal` phân biệt 2.0 và 2.00?

Một `BigDecimal` biểu diễn giá trị thập phân bằng cặp thông tin:

- **Unscaled value**: một số nguyên không giới hạn độ dài (`BigInteger`).
- **Scale**: số mũ thập phân có dấu, quyết định vị trí dấu phẩy.

Công thức:

```text
giá trị số học = unscaledValue × 10^(-scale)

2.0  = 20  × 10^(-1)
2.00 = 200 × 10^(-2)

=> Cùng giá trị số học là 2, nhưng có cặp (unscaledValue, scale) khác nhau.
```

Đọc giá trị thực tế:

| Thuộc tính | `new BigDecimal("2.0")` | `new BigDecimal("2.00")` |
|---|---:|---:|
| `toString()` | `2.0` | `2.00` |
| `unscaledValue()` | `20` | `200` |
| `scale()` | `1` | `2` |
| `precision()` | `2` | `3` |
| Giá trị số học | `2` | `2` |

`precision()` ở đây là số chữ số của phần unscaled (20 có 2 chữ số, 200 có 3 chữ số). `precision` và `scale` không phải cùng một khái niệm.

**Vai trò của chuỗi đầu vào:** `new BigDecimal("2.0")` giữ scale 1, còn `new BigDecimal("2.00")` giữ scale 2. Điều này hữu ích khi biểu diễn có ý nghĩa như độ chính xác đã được nhập, đơn vị đo hoặc số chữ số cần hiển thị.

## 3. Bốn cách so sánh: `==`, `equals`, `compareTo`, `compareTo == 0`

| Biểu thức | Kết quả với `a=2.0`, `b=2.00` | Đang kiểm tra điều gì? |
|---|---|---|
| `a == b` | `false` | Hai biến có trỏ **cùng một đối tượng** hay không |
| `a.equals(b)` | `false` | Giá trị số học **và scale** đều bằng nhau hay không |
| `a.compareTo(b)` | `0` | Quan hệ thứ tự giữa hai giá trị số học |
| `a.compareTo(b) == 0` | `true` | Hai giá trị có **bằng nhau về mặt số học** hay không |

### 3.1. Toán tử `==`: so sánh tham chiếu

Vì `BigDecimal` là đối tượng, `==` kiểm tra hai biến có cùng địa chỉ đối tượng theo nghĩa Java reference identity, không kiểm tra giá trị thập phân.

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");
BigDecimal c = a;

System.out.println(a == b); // false
System.out.println(a == c); // true
```

Đây là đoạn mã minh họa cần đặt trong phương thức (chẳng hạn `main`) để chạy. `==` có thể trả `true` khi hai biến giữ cùng tham chiếu, nhưng không đáp ứng nhu cầu so sánh số học.

### 3.2. `equals(Object)`: giá trị số học và scale phải khớp

Hợp đồng `BigDecimal.equals` yêu cầu hai `BigDecimal` cùng giá trị số học **và cùng scale**. Do đó:

```java
new BigDecimal("2.0").equals(new BigDecimal("2.00"));  // false
new BigDecimal("2.0").equals(new BigDecimal("2.0"));   // true
new BigDecimal("2.00").equals(new BigDecimal("2.00")); // true
```

Không nên diễn giải `false` ở ví dụ đầu thành “2.0 khác 2.00 về độ lớn”. Hai đối tượng khác nhau theo **tiêu chuẩn `equals`** vì scale khác nhau. Hợp đồng `hashCode()` cũng tương ứng với `equals`, nên các biểu diễn khác scale thường có mã băm khác nhau; không dùng mã băm để kết luận hai số có bằng nhau về số học hay không.

### 3.3. `compareTo(BigDecimal)`: so sánh theo giá trị số học

`compareTo` trả về:

- Một số **âm** nếu đối tượng bên trái nhỏ hơn bên phải.
- **0** nếu hai giá trị số học bằng nhau.
- Một số **dương** nếu đối tượng bên trái lớn hơn bên phải.

Ví dụ:

```java
new BigDecimal("2.0").compareTo(new BigDecimal("2.00")); // 0
new BigDecimal("1.9").compareTo(new BigDecimal("2.00")); // âm
new BigDecimal("2.1").compareTo(new BigDecimal("2.00")); // dương
```

**Không dựa vào kết quả phải chính xác bằng `-1` hoặc `1`**: hợp đồng chỉ cam kết dấu của kết quả. Viết `a.compareTo(b) < 0`, `> 0`, `== 0` tương ứng với nhỏ hơn, lớn hơn và bằng nhau.

### 3.4. So sánh `compareTo(b) == 0`: kiểm tra bằng nhau về số học

Đây là lựa chọn thông dụng khi quy tắc nghiệp vụ chỉ quan tâm số lượng hoặc độ lớn:

```java
boolean sameNumericValue = a.compareTo(b) == 0; // true
```

`compareTo` vốn là phép so sánh thứ tự; thêm `== 0` mới thu được biểu thức boolean. Đừng viết `a.compareTo(b).equals(0)`: kết quả `compareTo` là `int` nguyên thủy và không có phương thức `equals`.

## 4. Ví dụ Java hoàn chỉnh: in từng kết quả

**Tệp `BigDecimalComparisonDemo.java`** — chạy với Java 17+:

```java
import java.math.BigDecimal;

public class BigDecimalComparisonDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        BigDecimal sameReference = a;

        System.out.println("a=" + a + ", scale=" + a.scale()
                + ", precision=" + a.precision()
                + ", unscaled=" + a.unscaledValue());
        System.out.println("b=" + b + ", scale=" + b.scale()
                + ", precision=" + b.precision()
                + ", unscaled=" + b.unscaledValue());

        System.out.println("a == b: " + (a == b));
        System.out.println("a == sameReference: " + (a == sameReference));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.compareTo(b): " + a.compareTo(b));
        System.out.println("a.compareTo(b) == 0: "
                + (a.compareTo(b) == 0));
        System.out.println("a.stripTrailingZeros().equals(b.stripTrailingZeros()): "
                + a.stripTrailingZeros().equals(b.stripTrailingZeros()));
        System.out.println("a.setScale(2).equals(b): "
                + a.setScale(2).equals(b));
    }
}
```

Lệnh chạy:

```bash
javac BigDecimalComparisonDemo.java
java BigDecimalComparisonDemo
```

Kết quả chính xác:

```text
a=2.0, scale=1, precision=2, unscaled=20
b=2.00, scale=2, precision=3, unscaled=200
a == b: false
a == sameReference: true
a.equals(b): false
a.compareTo(b): 0
a.compareTo(b) == 0: true
a.stripTrailingZeros().equals(b.stripTrailingZeros()): true
a.setScale(2).equals(b): true
```

Có hai cách khác nhau khiến `equals` trả về `true` trong những dòng cuối: bỏ các số 0 không cần thiết bằng `stripTrailingZeros()`, hoặc thống nhất hai số về **cùng scale 2**. Chúng đều trả về đối tượng `BigDecimal` mới hoặc kết quả đã điều chỉnh; đối tượng đầu vào không bị sửa vì `BigDecimal` là immutable.

## 5. Đối chiếu hệ quả của bốn phép so sánh trong collection

Với `2.0` và `2.00`, `equals` trả false nhưng `compareTo` trả 0. Cấu trúc băm dựa trên **equals/hashCode**; cấu trúc cây theo thứ tự tự nhiên dựa trên **compareTo**:

| Collection | Kết quả khi thêm 2.0 rồi 2.00 | Lý do |
| --- | --- | --- |
| `HashSet` | Hai phần tử | Khác equals |
| `HashMap` | Hai khóa | Khác equals/hashCode |
| `TreeSet` | Một phần tử | compareTo == 0 |
| `TreeMap` | Một khóa, value lần sau ghi đè | compareTo == 0 |

```java
Set<BigDecimal> hash = new HashSet<>();
hash.add(new BigDecimal("2.0"));
hash.add(new BigDecimal("2.00"));
Set<BigDecimal> tree = new TreeSet<>();
tree.add(new BigDecimal("2.0"));
tree.add(new BigDecimal("2.00"));
// hash.size() == 2, tree.size() == 1
```

Đoạn mã nằm trong phương thức, cần import `BigDecimal`, `Set`, `HashSet`, `TreeSet`. Bài **hashCode và hệ quả trong HashSet hoặc HashMap** phân tích tra cứu khóa; bài **Hệ quả khi dùng TreeSet hoặc TreeMap** phân tích `contains`, `remove`, `put`, `get` và comparator. Ví dụ hiện tại chỉ minh họa sự khác biệt của **chính cặp 2.0 và 2.00**.

## 6. Chuẩn hóa có thể làm phép equals đổi kết quả

Nếu chỉ hỏi hai số có cùng giá trị không, dùng `a.compareTo(b) == 0` trực tiếp. Khi cần tạo khóa đồng nhất theo giá trị, có thể dùng `stripTrailingZeros()` cho cả hai phía:

```java
BigDecimal a = new BigDecimal("2.0").stripTrailingZeros();
BigDecimal b = new BigDecimal("2.00").stripTrailingZeros();
// a.equals(b) == true
```

Tuy nhiên `stripTrailingZeros()` có thể biến `1000.00` thành `1E+3` (scale âm) hoặc xóa scale của zero. Một chính sách **scale cố định** như `setScale(2, RoundingMode.UNNECESSARY)` giữ đủ hai chữ số nhưng từ chối `2.001` vì cần làm tròn. Chuẩn hóa là quyết định nghiệp vụ riêng cho khóa/lưu trữ; không cần làm chỉ để so sánh số học. Bài **Chính sách chuẩn hóa scale trước khi lưu hoặc so sánh** đi sâu vào quy tắc chọn ranh giới và xử lý rounding.
## 7. Bẫy khi khởi tạo: `2.0` và `2.00` dưới dạng `double`

Đoạn dưới tạo ra **hai giá trị literal `double` giống nhau**:

```java
double x = 2.0;
double y = 2.00;
System.out.println(x == y); // true
```

Kiểu `double` không ghi nhớ người viết đã thêm bao nhiêu số 0 sau dấu phẩy. Vì vậy:

| Cách tạo đối tượng | Biểu diễn `BigDecimal` | Scale |
|---|---|---:|
| `new BigDecimal("2.0")` | `2.0` | 1 |
| `new BigDecimal("2.00")` | `2.00` | 2 |
| `BigDecimal.valueOf(2.00)` | `2.0` | 1 |
| `new BigDecimal(2.00)` | `2` | 0 |
| `BigDecimal.valueOf(2L)` | `2` | 0 |

`BigDecimal.valueOf(double)` dùng biểu diễn thập phân chuẩn từ `Double.toString(double)`. Với đúng giá trị 2.0, chuỗi đó là `"2.0"`. Constructor `BigDecimal(double)` chuyển chính xác giá trị nhị phân nhận được thành một `BigDecimal`; 2 là số nguyên biểu diễn chính xác, nên kết quả có scale 0 trong ví dụ này.

**Không áp dụng kết luận “constructor từ double là chính xác theo hệ thập phân” cho mọi số.** Ví dụ `0.1` không có biểu diễn nhị phân hữu hạn; `new BigDecimal(0.1)` sẽ bộc lộ phần sai khác rất dài so với giá trị thập phân `0.1` mà người dùng thường mong đợi. Để bảo toàn dạng chữ số ban đầu, nhận đầu vào dạng `String` và truyền vào constructor `BigDecimal(String)`.

## 8. Phép toán có thể làm thay đổi scale và kết quả `equals`

Scale không chỉ ảnh hưởng cách so sánh, mà còn có thể đi theo kết quả số học:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.add(b));      // 4.00
System.out.println(a.multiply(b)); // 4.000
```

Với phép cộng, scale của kết quả không làm tròn chính xác thường là `max(scale(a), scale(b))`; ở đây `max(1, 2) = 2`. Với phép nhân chính xác, scale kết quả thường là tổng hai scale; ở đây `1 + 2 = 3`.

Hệ quả: hai chuỗi thao tác có thể tạo ra những `BigDecimal` cùng giá trị số học nhưng scale khác, nên `equals` khác `compareTo == 0`. Khi kiểm tra giá trị tính toán trong bài toán tài chính, đo lường hoặc test, hãy xác định tiêu chí so sánh độc lập với hình thức trình bày.

## 9. Chọn phép so sánh đúng theo mục đích

| Mục tiêu | Cách nên dùng | Lý do |
|---|---|---|
| Kiểm tra hai biến có trỏ cùng object | `a == b` | Chỉ kiểm tra danh tính tham chiếu |
| Kiểm tra giá trị **và scale** giống nhau | `a.equals(b)` | Xét đúng hợp đồng `BigDecimal.equals` |
| Kiểm tra hai giá trị số học bằng nhau | `a.compareTo(b) == 0` | Bỏ qua khác biệt về trailing zero/scale |
| Kiểm tra nhỏ hơn/lớn hơn | `a.compareTo(b) < 0` hoặc `> 0` | Thứ tự số học |
| Muốn `HashSet`/khóa `HashMap` gộp các dạng số học tương đương | Chuẩn hóa khóa theo một quy ước thống nhất | `HashSet` và `HashMap` dùng `equals`/`hashCode` |
| Muốn lưu cả `2.0` và `2.00` trong tập có thứ tự | `TreeSet` với comparator xét thêm scale | Thứ tự tự nhiên chỉ xét giá trị số học |
| Muốn giữ đúng 2 chữ số để hiển thị/ghi sổ | `setScale(2, mode)` theo chính sách | Scale là một phần của định dạng và hợp đồng nghiệp vụ |

**Vấn đề null:** `a.equals(b)` và `a.compareTo(b)` sẽ lỗi nếu `a == null`. Nếu `null` được phép, kiểm tra trước; đừng mặc định `null` tương đương số 0. `Objects.equals(a,b)` xử lý null an toàn nhưng vẫn dùng hợp đồng `equals`, tức vẫn cho `false` với `2.0` và `2.00`.

**Vấn đề test:** Với `assertEquals(expected, actual)` (ví dụ JUnit), framework thường dựa trên `equals`, nên một đối tượng mong đợi `2.00` có thể không bằng kết quả thực tế `2.0`. Nếu test yêu cầu bằng nhau về số học, hãy kiểm tra `expected.compareTo(actual) == 0` theo cách assertion phù hợp. Chỉ dùng `equals` khi scale thực sự là yêu cầu cần kiểm tra.

## 10. Bài tập và lời giải

1. **Cho `a = 2.0`, `b = 2.00` (đều khởi tạo từ chuỗi). `a.equals(b)` và `a.compareTo(b) == 0` trả gì?**  
   Đáp án: `false` và `true`. Scale khác nhau nhưng giá trị số học bằng nhau.
2. **Tạo `c = new BigDecimal("2.0")`. `a.equals(c)` và `a == c` trả gì?**  
   Đáp án: `true` và `false` nếu `a`, `c` được tạo bằng hai lệnh `new` riêng biệt: cùng value/scale nhưng khác object.
3. **`HashSet` lần lượt thêm `2.0` rồi `2.00` có bao nhiêu phần tử? `TreeSet` thứ tự tự nhiên thì sao?**  
   Đáp án: `HashSet` có 2; `TreeSet` có 1. Hai tập dùng tiêu chuẩn xác định trùng khác nhau.
4. **`new BigDecimal("2.00").stripTrailingZeros().scale()` bằng mấy?**  
   Đáp án: `0`, vì biểu diễn chuẩn hóa là `2`.
5. **Sau `new BigDecimal("2.0").setScale(2)`, `equals(new BigDecimal("2.00"))` là gì?**  
   Đáp án: `true`: cả giá trị và scale đều trùng.
6. **Tại sao `BigDecimal.valueOf(2.00)` không giữ lại 2 chữ số thập phân?**  
   Đáp án: `2.00` đã được chuyển thành `double` và mất thông tin về cách viết; `Double.toString` biểu diễn nó là `"2.0"`.

## 11. Ghi nhớ

`2.0` và `2.00` là **cùng giá trị số học** nhưng là **hai biểu diễn `BigDecimal` có scale khác nhau** khi tạo từ chuỗi. Hãy chọn tiêu chí theo ý định:

- Dùng `compareTo() == 0` cho **bằng nhau về số học**.
- Dùng `equals()` khi **scale cũng là một phần của tính bằng nhau**.
- Dùng `==` chỉ khi thật sự cần hỏi **có cùng tham chiếu đối tượng hay không**.
- Với collections, nhớ `HashSet`/`HashMap` theo `equals` còn `TreeSet`/`TreeMap` thứ tự tự nhiên theo `compareTo`.
- Chuẩn hóa `stripTrailingZeros` hoặc `setScale` chỉ khi quy tắc dữ liệu cho phép thay đổi cách biểu diễn.

Tài liệu chính thức:

- [Java 21 — `BigDecimal`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Java 21 — `Comparable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html)
- [Java 21 — `TreeSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-004">hashCode và hệ quả trong HashSet hoặc HashMap</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.hashCode và hệ quả trong HashSet hoặc HashMap

## 1. Vấn đề: hai số bằng nhau về giá trị vẫn có thể là hai khóa khác nhau

Với `BigDecimal`, điều quan trọng là phân biệt **so sánh giá trị số** với **so sánh biểu diễn**. Hai đối tượng `new BigDecimal("2.0")` và `new BigDecimal("2.00")` có cùng giá trị toán học. Nhưng chúng khác `scale` nên `equals()` trả `false`; `hashCode()` cũng xét `scale`.

Đây là nguyên nhân làm lập trình viên bất ngờ khi dùng `BigDecimal` làm phần tử của `HashSet` hoặc khóa của `HashMap`: thêm `2.0` rồi kiểm tra sự tồn tại bằng `2.00` có thể cho kết quả **không tìm thấy**, dù `compareTo()` báo hai giá trị bằng nhau.

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));          // false
System.out.println(a.hashCode() == b.hashCode()); // false với cặp giá trị này
```

Đoạn trên minh họa ba cách kiểm tra khác nhau, chưa phải chương trình Java độc lập. Phần ví dụ chạy đầy đủ sẽ cho thấy kết quả trong từng loại collection.

## 2. Vì sao BigDecimal giữ cả giá trị và scale?

Một `BigDecimal` về bản chất biểu diễn:

```text
giá trị = unscaledValue × 10^(-scale)

BigDecimal("2.0")  : unscaledValue = 20,  scale = 1
BigDecimal("2.00") : unscaledValue = 200, scale = 2
```

Hai cách biểu diễn cùng một số, nhưng giữ lại thông tin tỷ lệ thập phân ban đầu. Điều này hữu ích khi `scale` mang ý nghĩa về định dạng, độ chi tiết hoặc hợp đồng dữ liệu.

Ba phương thức có hợp đồng riêng:

| Phương thức | Điều được kiểm tra | `2.0` với `2.00` |
| --- | --- | --- |
| `a.compareTo(b)` | Thứ tự **theo giá trị số**; `0` nghĩa là ngang giá trị | `0` |
| `a.equals(b)` | Giá trị số **và scale** phải bằng nhau | `false` |
| `a.hashCode()` | Mã băm nhất quán với `equals()`, có xét scale | Hai mã băm khác nhau đối với cặp này |

Hợp đồng chung của Java quy định:

- Nếu `a.equals(b)` là `true` thì **bắt buộc** `a.hashCode() == b.hashCode()`.
- Nếu hai `hashCode()` bằng nhau thì **không thể kết luận** `equals()` bằng nhau: va chạm mã băm vẫn có thể xảy ra.
- Nếu `a.equals(b)` là `false`, mã băm **có thể** trùng hoặc khác nhau. Không nên kiểm tra tính bằng nhau bằng cách so sánh mã băm.
- `BigDecimal.compareTo()` có thứ tự **không nhất quán với equals**: `compareTo() == 0` không đảm bảo `equals() == true`.

Vì vậy, muốn hỏi “hai số có cùng giá trị toán học không?”, hãy dùng `compareTo() == 0`. Muốn hỏi “hai biểu diễn BigDecimal có đúng cùng scale và giá trị không?”, dùng `equals()`.

## 3. HashSet dùng equals và hashCode như thế nào?

`HashSet<BigDecimal>` sử dụng quy tắc tương đương của `equals()`, với `hashCode()` để tìm vị trí ứng viên. Nó không dùng `compareTo()` để quyết định phần tử đã tồn tại.

```java
Set<BigDecimal> values = new HashSet<>();
values.add(new BigDecimal("2.0"));
values.add(new BigDecimal("2.00"));

System.out.println(values.size()); // 2
System.out.println(values.contains(new BigDecimal("2.0")));   // true
System.out.println(values.contains(new BigDecimal("2.000"))); // false
```

Ở đây, cả `2.0` và `2.00` đều được thêm: chúng là **hai phần tử khác nhau theo equals**. Giá trị `2.000` là một cách biểu diễn thứ ba, nên `contains` không tìm thấy đối tượng tương đương theo `equals()`.

Luồng kiểm tra có thể hiểu như sau:

```text
add(BigDecimal("2.0"))
  → tính hashCode theo biểu diễn của 2.0
  → lưu phần tử

add(BigDecimal("2.00"))
  → tính hashCode theo biểu diễn của 2.00
  → không có phần tử equals(...) == true
  → thêm phần tử mới

contains(BigDecimal("2.000"))
  → tìm ứng viên bằng hashCode
  → cần ứng viên equals(...) == true
  → không có → false
```

Đây là mô tả ở mức hợp đồng của collection; cách triển khai bucket, bảng băm hoặc xử lý collision có thể thay đổi. **HashSet không sai và BigDecimal không sai**: ứng dụng phải xác định mình muốn duy nhất theo *giá trị* hay theo *biểu diễn*.

## 4. HashMap dùng BigDecimal làm khóa: put và get dễ gây nhầm

`HashMap<BigDecimal, V>` xác định khóa bằng `hashCode()` và `equals()`. Nếu lần ghi và lần đọc tạo `BigDecimal` từ các chuỗi có scale khác nhau, `get` có thể trả `null`.

```java
Map<BigDecimal, String> map = new HashMap<>();
map.put(new BigDecimal("2.0"), "dữ liệu A");
map.put(new BigDecimal("2.00"), "dữ liệu B");

System.out.println(map.size()); // 2
System.out.println(map.get(new BigDecimal("2.0")));   // dữ liệu A
System.out.println(map.get(new BigDecimal("2.000"))); // null
```

Cả hai lần `put` tạo hai entry riêng; lần `get` thứ hai không khớp biểu diễn nào. `null` có thể nghĩa là thiếu khóa **hoặc** có khóa ánh xạ tới giá trị `null`; dùng `containsKey` khi cần phân biệt hai tình huống.

Vấn đề tương tự xuất hiện ở cache, dữ liệu tổng hợp, thuật toán đếm tần suất, gom nhóm theo giá, hoặc so khớp ID dạng số thập phân:

- Dữ liệu nhập vào là `"2.0"` nhưng truy vấn là `"2.00"`.
- Dữ liệu lấy từ JSON, database hay phép tính trung gian có scale khác nhau.
- Nhóm `Map<BigDecimal, List<Item>>` bị tách thành nhiều nhóm tuy các khóa ngang giá trị.

Đừng đổi sang `double` hay lấy `intValue()` chỉ để tránh trường hợp này: có thể mất độ chính xác hoặc phần thập phân và tạo ra một quy tắc khóa sai.

## 5. Chương trình Java 17+ chứng minh HashSet, HashMap và TreeSet, TreeMap

Lưu tệp `BigDecimalCollectionsDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalCollectionsDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        BigDecimal lookup = new BigDecimal("2.000");

        System.out.println("compareTo == 0: " + (a.compareTo(b) == 0));
        System.out.println("equals: " + a.equals(b));
        System.out.println("hashCode equal: "
                + (a.hashCode() == b.hashCode()));

        Set<BigDecimal> hashSet = new HashSet<>();
        hashSet.add(a);
        hashSet.add(b);
        System.out.println("HashSet size: " + hashSet.size());
        System.out.println("HashSet contains 2.000: "
                + hashSet.contains(lookup));

        Map<BigDecimal, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        hashMap.put(b, "second");
        System.out.println("HashMap size: " + hashMap.size());
        System.out.println("HashMap get 2.0: " + hashMap.get(a));
        System.out.println("HashMap get 2.000: " + hashMap.get(lookup));

        Set<BigDecimal> treeSet = new TreeSet<>();
        treeSet.add(a);
        treeSet.add(b);
        System.out.println("TreeSet size: " + treeSet.size());
        System.out.println("TreeSet contains 2.000: "
                + treeSet.contains(lookup));

        Map<BigDecimal, String> treeMap = new TreeMap<>();
        treeMap.put(a, "first");
        treeMap.put(b, "second");
        System.out.println("TreeMap size: " + treeMap.size());
        System.out.println("TreeMap get 2.000: " + treeMap.get(lookup));
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalCollectionsDemo.java
java BigDecimalCollectionsDemo
```

Stdout:

```text
compareTo == 0: true
equals: false
hashCode equal: false
HashSet size: 2
HashSet contains 2.000: false
HashMap size: 2
HashMap get 2.0: first
HashMap get 2.000: null
TreeSet size: 1
TreeSet contains 2.000: true
TreeMap size: 1
TreeMap get 2.000: second
```

Chương trình không in trực tiếp toàn bộ collection vì thứ tự duyệt của `HashSet` và `HashMap` không phải thứ tự được đảm bảo.

Điểm đáng chú ý: `TreeSet` và `TreeMap` mặc định dùng thứ tự tự nhiên qua `compareTo()`, nên xem `2.0`, `2.00`, `2.000` là cùng một vị trí theo thứ tự. `TreeMap.put(b, "second")` cập nhật giá trị của entry đã có vì so sánh khóa trả `0`; `get(lookup)` tìm được `"second"`.

## 6. Vì sao TreeSet và TreeMap có kết quả khác?

Các collection hash và sorted dùng quy tắc xác định khóa khác nhau:

| Collection | Quy tắc mặc định để coi là cùng phần tử/khóa | `2.0` và `2.00` |
| --- | --- | --- |
| `HashSet<BigDecimal>` | `hashCode` + `equals` | Hai phần tử |
| `HashMap<BigDecimal, V>` | `hashCode` + `equals` | Hai khóa |
| `TreeSet<BigDecimal>` | `compareTo` trả `0` | Một phần tử |
| `TreeMap<BigDecimal, V>` | `compareTo` trả `0` | Một khóa; put sau cập nhật giá trị |
| `TreeSet/TreeMap` có `Comparator` riêng | Comparator trả `0` | Phụ thuộc comparator |

`TreeSet` và `TreeMap` có hành vi sắp xếp nhất quán với `compareTo()`, nhưng vì thứ tự tự nhiên của `BigDecimal` **không nhất quán với equals**, các sorted collection sử dụng nó sẽ không hoàn toàn tuân theo hợp đồng tổng quát dựa trên `equals` của `Set`/`Map`. Hành vi của sorted collection vẫn được xác định theo comparator.

Hệ quả thực tế: chuyển code từ `HashMap` sang `TreeMap` có thể thay đổi cả **số lượng khóa** và **quy tắc ghi đè**, không chỉ thay đổi thứ tự duyệt. Hãy chọn collection dựa trên yêu cầu nghiệp vụ, rồi đảm bảo mọi phần của ứng dụng sử dụng cùng một định nghĩa về tính duy nhất.

Không có `Comparator` nào được truyền vào `HashMap` hoặc `HashSet` để đổi hành vi `equals` của khóa. Nếu dùng hash collection mà muốn các scale khác nhau vẫn là một khóa, cần chuẩn hóa khóa hoặc thiết kế kiểu khóa riêng.

## 7. Ba chính sách khóa ứng với ba yêu cầu dữ liệu

**Giữ scale:** dùng `BigDecimal` nguyên dạng làm khóa hash nếu `2.0` và `2.00` cần phân biệt. Điều này sử dụng đúng hợp đồng `equals/hashCode` hiện có.

**Cùng số học thì cùng khóa:** chuẩn hóa giá trị trước khi `put`, `get`, `containsKey` và `remove`, kể cả khi tải lại từ storage:

```java
static BigDecimal numericKey(BigDecimal input) {
    return java.util.Objects.requireNonNull(input).stripTrailingZeros();
}
```

```java
Map<BigDecimal, String> map = new HashMap<>();
map.put(numericKey(new BigDecimal("2.00")), "two");
String result = map.get(numericKey(new BigDecimal("2.000"))); // "two"
```

Đây là các đoạn đặt trong một class/method với import `BigDecimal`, `Map`, `HashMap`. `stripTrailingZeros` giữ nguyên số nhưng có thể làm scale âm (`1000.0 → 1E+3`) và đưa mọi zero về scale 0. Việc nhiều khóa khác representation trở thành một khóa chuẩn buộc ứng dụng có chính sách xử lý dữ liệu trùng (giữ, từ chối, ghi đè, gộp). Lưu văn bản/scale gốc ở trường riêng nếu cần audit.

**Scale cố định:** nếu miền dữ liệu thật sự yêu cầu hai chữ số và không được mất dữ liệu, dùng `setScale(2, RoundingMode.UNNECESSARY)`. Giá trị không thể biểu diễn chính xác gây `ArithmeticException`. Nếu cho phép rounding, cần mode và quy tắc xử lý khóa bị gộp rõ ràng; hai giá trị số học khác nhau có thể thành một giá trị sau làm tròn. Bài **Chính sách chuẩn hóa scale trước khi lưu hoặc so sánh** xử lý riêng DB, JSON, precision, scale âm và các ranh giới đó.

## 8. Cố định chính sách trong kiểu khóa bất biến

```java
public record NumericDecimalKey(BigDecimal value) {
    public NumericDecimalKey {
        value = java.util.Objects.requireNonNull(value)
                .stripTrailingZeros();
    }
}
```

Đây là `record` độc lập trong Java 17, cần import `java.math.BigDecimal`. `equals` và `hashCode` sinh tự động từ thành phần **đã chuẩn hóa**. Khi cần giữ nguyên chuỗi hoặc scale đầu vào, lưu chúng riêng; canonical key chỉ phục vụ danh tính theo giá trị số.
## 9. Các góc cạnh và lỗi thường gặp

**1. So sánh bằng compareTo nhưng tra cứu bằng HashMap.** Nếu code kiểm tra trước bằng `compareTo() == 0`, sau đó truy vấn bằng `map.get(otherBigDecimal)`, hai bước đang dùng hai định nghĩa bằng nhau khác nhau. Kiểm tra số học không đảm bảo lookup bằng hash thành công.

**2. Chỉ so hashCode để quyết định trùng.** Hash collision có thể xảy ra. Luôn theo hợp đồng `equals()`, hoặc chuẩn hóa rồi để collection thực hiện việc so sánh.

**3. Dùng `stripTrailingZeros()` trên một khóa nhưng không trên khóa truy vấn.** Dễ dẫn đến cache miss hoặc bản ghi không tìm thấy. Chuẩn hóa nhất quán ở mọi thao tác, bao gồm ghi, đọc, xóa và khôi phục trạng thái.

**4. Nhầm scale âm sau chuẩn hóa là lỗi.** `1000.0.stripTrailingZeros()` tạo `1E+3`, scale `-3`. Số vẫn chính xác. Nếu nơi nhận cấm dạng này, phải xử lý định dạng/scale ở ranh giới xuất dữ liệu theo hợp đồng riêng.

**5. Gộp dữ liệu vô tình sau khi chuẩn hóa.** Ví dụ ban đầu `2.0` và `2.00` là hai khóa khác nhau. Sau chuẩn hóa, chúng trùng nhau. Khi dùng `Collectors.toMap`, trùng khóa mà không có hàm gộp có thể làm collector ném `IllegalStateException`. Trong vòng lặp với `HashMap.put`, giá trị sau sẽ ghi đè giá trị trước. Cần chọn chính sách rõ ràng.

**6. Dùng phép làm tròn để tạo khóa dù định danh yêu cầu đúng giá trị.** Rounding có thể đồng nhất hai số vốn khác nhau về số học. `stripTrailingZeros()` chỉ bỏ số 0 dư nên giữ nguyên giá trị; `setScale(..., HALF_UP)` có thể thay đổi giá trị.

**7. Tạo BigDecimal từ literal double.** `new BigDecimal(0.1)` thể hiện chính xác giá trị nhị phân gần đúng của `double`, không phải thập phân đúng bằng `0.1`. Khi yêu cầu decimal nhập chính xác, dùng `new BigDecimal("0.1")`; nếu nguồn đã là `double`, `BigDecimal.valueOf(double)` thường phù hợp hơn constructor nhận `double`, nhưng không phục hồi sai số đã phát sinh trước đó.

**8. Cho rằng BigDecimal tự thay đổi sau khi làm khóa.** `BigDecimal` là kiểu **immutable**, vì thế bản thân đối tượng khóa không bị chỉnh scale hay giá trị tại chỗ. Tuy nhiên, một đối tượng wrapper chứa trường có thể thay đổi và tự triển khai `equals/hashCode` dựa trên trường đó có thể làm hỏng hành vi hash collection khi trường bị sửa sau `put`. Ưu tiên khóa bất biến.

**9. Lẫn dữ liệu lưu trữ với biểu diễn hiển thị.** Số `2.00` có thể cần hiện đúng hai chữ số cho người dùng, nhưng chính sách cache chỉ quan tâm giá trị `2`. Giữ các quy tắc này ở những bước riêng: nhận dạng khóa, kiểm tra hợp lệ và định dạng.

## 10. Chọn hướng nào trong thực tế?

| Yêu cầu | Cách xử lý | Cần kiểm tra thêm |
| --- | --- | --- |
| Scale là một phần của danh tính | Dùng `BigDecimal` trực tiếp trong `HashMap/HashSet` | Nhà cung cấp dữ liệu có giữ scale ổn định không? |
| Mọi biểu diễn ngang giá trị là một khóa | Dùng `stripTrailingZeros()` nhất quán hoặc kiểu khóa chuẩn hóa | Xử lý entry trùng sau chuẩn hóa và serialization |
| Khóa phải có số chữ số thập phân cố định | `setScale(fixedScale, UNNECESSARY)` | Giá trị nào phải bị từ chối khi không exact? |
| Cần thứ tự tăng/giảm theo giá trị số | `TreeMap/TreeSet` mặc định dùng `compareTo()` | Có chấp nhận việc các scale khác nhau bị xem là một khóa? |
| Cần lưu số đơn vị nhỏ nhất bằng số nguyên | Đổi chính xác sang `BigInteger`/`long` nếu phạm vi cho phép | Đơn vị, overflow và phép đổi exact |

Một `TreeMap` có thể đáp ứng tự nhiên nhu cầu sắp xếp và gom các scale khác nhau, nhưng không thể xem đây là một phép thay thế trong suốt cho `HashMap`: hành vi nhận dạng khóa cũng thay đổi.

## 11. Câu hỏi tự kiểm tra

1. `new BigDecimal("0.0").equals(BigDecimal.ZERO)` trả gì? **False**: scale 1 khác scale 0, dù `compareTo(BigDecimal.ZERO) == 0`.
2. Thêm `new BigDecimal("3.0")` và `new BigDecimal("3.00")` vào `HashSet` có bao nhiêu phần tử? **Hai**: `equals()` phân biệt scale.
3. Thêm cùng hai giá trị vào `TreeSet` mặc định có bao nhiêu phần tử? **Một**: `compareTo()` coi hai số bằng nhau.
4. Vì sao hai đối tượng khác nhau có cùng `hashCode` chưa chắc là cùng khóa `HashMap`? **Vì** collection còn cần `equals()` để phân biệt các khóa có mã băm trùng.
5. Nên dùng `setScale(2, HALF_UP)` hay `stripTrailingZeros()` để tạo khóa theo đúng giá trị số? **`stripTrailingZeros()`**: nó không làm thay đổi giá trị số; `HALF_UP` có thể gộp các giá trị khác nhau.
6. Phải chuẩn hóa ở những bước nào để lookup nhất quán? **Mọi ranh giới tạo khóa**, đặc biệt `put`, `get`, `containsKey`, `remove`, và khi đọc lại từ dữ liệu đã lưu.

## 12. Kết luận

`BigDecimal.compareTo()` so sánh **giá trị số**, còn `BigDecimal.equals()` và `hashCode()` xét cả **giá trị số lẫn scale**. Vì vậy, `HashSet` và `HashMap` có thể giữ `2.0` và `2.00` thành hai phần tử/khóa riêng, trong khi `TreeSet` và `TreeMap` mặc định xem chúng là một theo `compareTo()`.

Điều quyết định là **định nghĩa tính duy nhất của ứng dụng**. Nếu biểu diễn quan trọng, dùng BigDecimal trực tiếp; nếu chỉ giá trị số quan trọng, chuẩn hóa chính xác và nhất quán; nếu domain cần scale cố định, kiểm tra và từ chối dữ liệu không đáp ứng trước khi tạo khóa. Luôn có chính sách xử lý trùng khi chuyển đổi từ nhiều biểu diễn sang một khóa chung.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-005">Thứ tự tự nhiên không nhất quán với equals</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal: thứ tự tự nhiên không nhất quán với equals

Trong Java, `BigDecimal` có một đặc điểm dễ gây lỗi: **hai giá trị bằng nhau về mặt số học có thể được `compareTo()` xem là bằng nhau nhưng `equals()` lại xem là khác nhau**. Ví dụ `new BigDecimal("2.0")` và `new BigDecimal("2.00")` cùng mang giá trị số 2, song khác số chữ số sau dấu thập phân (`scale`). Sự khác biệt này ảnh hưởng trực tiếp đến sắp xếp, loại phần tử trùng, kiểm tra `contains()` và các thao tác khóa trong `Set`/`Map`.

Mục tiêu của bài:
- Giải thích chính xác khái niệm **natural ordering consistent with equals** của `Comparable` và lý do `BigDecimal` là ngoại lệ có chủ ý.
- Dự đoán kết quả `compareTo()`, `equals()`, `hashCode()` cùng tác động đối với `HashSet`, `TreeSet`, `HashMap` và `TreeMap`.
- Chọn quy tắc so sánh/chuẩn hóa phù hợp tùy việc ứng dụng xem **giá trị số** hay **biểu diễn kèm `scale`** là danh tính.
- Tránh mất dữ liệu về `scale` hoặc bị gộp khóa ngoài ý muốn.

## 1. “Thứ tự tự nhiên nhất quán với equals” nghĩa là gì?

Một lớp triển khai `Comparable<T>` có **thứ tự tự nhiên** do phương thức `compareTo(T other)` xác định. Theo hợp đồng của `Comparable`, `compareTo()` trả về số âm khi đối tượng đứng trước, số dương khi đứng sau và `0` khi hai đối tượng **tương đương theo thứ tự**. Đừng phụ thuộc độ lớn tuyệt đối của kết quả khi lớp khác có thể trả về những số nguyên khác ngoài -1, 0, 1.

Thứ tự tự nhiên được gọi là **nhất quán với `equals`** khi với *mọi* cặp `a`, `b` hợp lệ:

~~~text
(a.compareTo(b) == 0)  ==  a.equals(b)
~~~

Ở đây dấu `==` ngoài cùng diễn đạt sự bằng nhau giữa hai giá trị `boolean`: hai mệnh đề cần cùng đúng hoặc cùng sai.

Java khuyến nghị tính nhất quán này vì giao diện `Set`/`Map` nói về danh tính thông qua `equals()`, trong khi những cấu trúc có sắp xếp như `TreeSet`/`TreeMap` dựa trên **kết quả so sánh bằng 0**. Tuy nhiên, đây là khuyến nghị mạnh chứ không phải điều kiện bắt buộc để triển khai `Comparable`. Tài liệu API xác định rõ `BigDecimal` là một ngoại lệ, vì nó dùng một quy tắc cho **thứ tự số học** và quy tắc khác cho **bằng nhau theo biểu diễn**.

**Hai mệnh đề phải phân biệt:**

1. `a.compareTo(b) == 0`: cùng giá trị số, có thể khác `scale`.
2. `a.equals(b)`: cùng giá trị số **và** cùng `scale`.

Với `BigDecimal`, `equals()` bằng `true` thì `compareTo() == 0` cũng đúng; chiều ngược lại không nhất thiết đúng. Đây là **sự không nhất quán về quan hệ tương đương**, không phải là lỗi sắp thứ tự tăng/giảm hay việc `compareTo()` vi phạm tính bắc cầu.

## 2. Nguồn gốc: unscaled value và scale

`BigDecimal` biểu diễn một số thập phân bằng một số nguyên `unscaledValue` và `scale`:

~~~text
giá trị số = unscaledValue × 10^(-scale)
~~~

| Chuỗi khởi tạo | `unscaledValue()` | `scale()` | Giá trị số |
|---|---:|---:|---:|
| `"2"` | 2 | 0 | 2 |
| `"2.0"` | 20 | 1 | 2 |
| `"2.00"` | 200 | 2 | 2 |
| `"2E+0"` | 2 | 0 | 2 |
| `"0.00"` | 0 | 2 | 0 |
| `"0.000"` | 0 | 3 | 0 |

`compareTo()` nhìn vào **giá trị số**, nên coi `2`, `2.0` và `2.00` là tương đương. Ngược lại, `equals()` còn xét `scale`, do đó `2.0` khác `2.00` theo phương thức này. Một cách dễ nhớ: thêm số 0 cuối phần thập phân có thể giữ nguyên *giá trị* nhưng thay đổi *biểu diễn*.

Hai giá trị có cùng `scale` và cùng giá trị số thì có cùng `unscaledValue`, do đó `equals()` trả về `true`. Dấu cách trình bày từ chuỗi đầu vào cũng không trở thành thuộc tính riêng trong `BigDecimal`; hai chuỗi khác nhau có thể tạo ra đối tượng `equals()` bằng nhau nếu chúng cho đúng cùng cặp giá trị/số mũ.

## 3. So sánh rõ ba kiểu kiểm tra

~~~java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
System.out.println(a == b);              // false
~~~

Đây là *đoạn minh họa đặt bên trong phương thức* và cần `import java.math.BigDecimal;`. Chương trình đầy đủ chạy độc lập nằm ở mục 5.

| Cách kiểm tra | Tiêu chí | `2.0` so với `2.00` |
|---|---|---|
| `a.compareTo(b) == 0` | Bằng giá trị số | `true` |
| `a.equals(b)` | Bằng giá trị số và `scale` | `false` |
| `a == b` | Cùng tham chiếu đối tượng | `false` với hai lần `new` riêng |
| `a.compareTo(b) < 0` | Nhỏ hơn về giá trị số | `false` |
| `a.compareTo(b) > 0` | Lớn hơn về giá trị số | `false` |

**`hashCode()` đi theo `equals()`, không theo `compareTo()`.** Hai đối tượng có `equals() == true` bắt buộc cùng `hashCode()`. Hai đối tượng chỉ bằng giá trị số nhưng khác `scale` không được `BigDecimal` hứa có hash giống nhau; hàm băm của nó cũng xét `scale`. Vì thế, dữ liệu băm không tự xem `2.0` và `2.00` là một khóa.

Không dùng `a.hashCode() == b.hashCode()` làm phép thử bằng nhau: hash collision vẫn có thể xảy ra, và hash chỉ là công cụ chọn bucket, trong khi `equals()` xác nhận danh tính.

## 4. Vì sao điều này ảnh hưởng đến Set và Map?

Hai họ cấu trúc dữ liệu sử dụng quy tắc “trùng” khác nhau:

| Cấu trúc | Mặc định xác định trùng bằng | `2.0` và `2.00` cùng tồn tại? |
|---|---|---|
| `HashSet<BigDecimal>` | `hashCode()` kết hợp `equals()` | **Có**, hai phần tử |
| `TreeSet<BigDecimal>` | `compareTo() == 0` nếu không chỉ định comparator | **Không**, một phần tử |
| `HashMap<BigDecimal, V>` | `hashCode()` kết hợp `equals()` cho khóa | **Có**, hai khóa |
| `TreeMap<BigDecimal, V>` | `compareTo() == 0` nếu không chỉ định comparator | **Không**, lần `put` sau cập nhật value của khóa tương đương |

Các cấu trúc `TreeSet`/`TreeMap` có hành vi **xác định rõ theo hợp đồng sắp xếp**, dù việc dùng `BigDecimal` với thứ tự mặc định khiến quan hệ danh tính theo cây không tương thích với cách `Set`/`Map` thông thường diễn đạt thông qua `equals`.

Điều này có hai kiểu lỗi phổ biến:
- Hệ thống cần phân biệt `2.0` và `2.00`, nhưng chuyển từ `HashSet` sang `TreeSet` dẫn tới phần tử thứ hai không được thêm.
- Hệ thống dùng `TreeMap` để lưu dữ liệu theo đơn giá. Khóa `2.00` được xem là khóa tương đương với `2.0`, làm `put` sau **thay value cũ**, thay vì tạo một entry mới.

Bản thân việc sắp xếp theo `compareTo()` không “sai”; vấn đề xuất hiện khi nghiệp vụ ngầm coi **mỗi cách biểu diễn** là một danh tính riêng nhưng lại chọn collection gộp theo **giá trị số**.

## 5. Chương trình Java 17+ chạy độc lập

Tạo tệp `BigDecimalOrderDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalOrderDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println("compareTo: " + a.compareTo(b));
        System.out.println("equals: " + a.equals(b));
        System.out.println("same scale: " + (a.scale() == b.scale()));

        HashSet<BigDecimal> hashSet = new HashSet<>();
        System.out.println("hash add a: " + hashSet.add(a));
        System.out.println("hash add b: " + hashSet.add(b));
        System.out.println("hash size: " + hashSet.size());

        TreeSet<BigDecimal> treeSet = new TreeSet<>();
        System.out.println("tree add a: " + treeSet.add(a));
        System.out.println("tree add b: " + treeSet.add(b));
        System.out.println("tree size: " + treeSet.size());
        System.out.println("tree contains b: " + treeSet.contains(b));

        HashMap<BigDecimal, String> hashMap = new HashMap<>();
        System.out.println("hash first put: " + hashMap.put(a, "first"));
        System.out.println("hash second put: " + hashMap.put(b, "second"));
        System.out.println("hash map size: " + hashMap.size());
        System.out.println("hash get a: " + hashMap.get(a));

        TreeMap<BigDecimal, String> treeMap = new TreeMap<>();
        System.out.println("tree first put: " + treeMap.put(a, "first"));
        System.out.println("tree second put: " + treeMap.put(b, "second"));
        System.out.println("tree map size: " + treeMap.size());
        System.out.println("tree get a: " + treeMap.get(a));
        System.out.println("tree first key: " + treeMap.firstKey());

        Comparator<BigDecimal> valueThenScale =
                Comparator.<BigDecimal>naturalOrder()
                        .thenComparingInt(BigDecimal::scale);
        TreeSet<BigDecimal> representationSet = new TreeSet<>(valueThenScale);
        representationSet.add(a);
        representationSet.add(b);
        System.out.println("representation size: " + representationSet.size());
        System.out.println("representation contents: " + representationSet);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalOrderDemo.java
java BigDecimalOrderDemo
~~~

Kết quả:

~~~text
compareTo: 0
equals: false
same scale: false
hash add a: true
hash add b: true
hash size: 2
tree add a: true
tree add b: false
tree size: 1
tree contains b: true
hash first put: null
hash second put: null
hash map size: 2
hash get a: first
tree first put: null
tree second put: first
tree map size: 1
tree get a: second
tree first key: 2.0
representation size: 2
representation contents: [2.0, 2.00]
~~~

Giải thích kết quả:

1. `compareTo: 0` không kéo theo `equals: true` vì `a.scale() == 1` trong khi `b.scale() == 2`.
2. `HashSet` thêm được cả hai. `TreeSet` thêm `a` trước, rồi từ chối `b` vì `compareTo() == 0`; `treeSet.contains(b)` vẫn trả `true`. Điều này không có nghĩa phần tử `b` đã được lưu nguyên dạng trong cây.
3. `HashMap.put(a, "first")` và `put(b, "second")` tạo hai khóa riêng. `TreeMap.put(b, "second")` cập nhật entry theo thứ tự so sánh nên trả lại `"first"` và kích thước vẫn bằng 1.
4. Khi `TreeMap` có khóa tương đương theo comparator, lần ghi thứ hai thay giá trị của entry đang có; trong ví dụ `firstKey()` vẫn in `2.0`, là đối tượng khóa được chèn đầu tiên.
5. Comparator tự định nghĩa `valueThenScale` sắp theo giá trị số **rồi so tiếp `scale` nếu giá trị bằng nhau**, vì vậy xem `a` và `b` là khác và giữ cả hai trong `TreeSet`.

Chương trình chỉ in kích thước và kết quả kiểm tra đối với `HashSet`/`HashMap`, tránh giả định thứ tự duyệt của hash collection là cố định. Ví dụ này không sử dụng `double` để khởi tạo dữ liệu đầu vào.

## 6. Sorted collections dùng “bằng nhau theo comparator”

Trong `TreeSet`, `add()`, `contains()` và `remove()` xác định đối tượng tương đương thông qua `compareTo() == 0` khi dùng thứ tự tự nhiên. Trong `TreeMap`, `put()`, `get()`, `containsKey()` và `remove()` cũng dùng tương đương theo comparator cho khóa. Một `Comparator` tùy chỉnh sẽ thay cho `compareTo()` trong những quyết định đó.

Hãy xem kỹ hai thao tác này:

~~~java
TreeSet<BigDecimal> prices = new TreeSet<>();
prices.add(new BigDecimal("8.0"));

boolean found = prices.contains(new BigDecimal("8.00")); // true
boolean added = prices.add(new BigDecimal("8.00"));      // false
~~~

Kết quả `found == true` chỉ nói rằng trong tập có một phần tử **bằng theo thứ tự** với `8.00`, không chứng minh đối tượng `8.00` theo đúng `scale` đã được lưu. Nếu tiếp tục `prices.remove(new BigDecimal("8.000"))`, thao tác có thể xóa phần tử `8.0` dù `equals()` giữa chúng là `false`.

Với `TreeMap`, rủi ro tương tự đặc biệt quan trọng nếu khóa đại diện cho **một mục nghiệp vụ phải phân biệt theo quy cách ghi**: lần ghi thứ hai có thể thay payload của khóa đầu. Quy tắc so sánh phải được chọn rõ trước khi tạo collection.

## 7. Sắp xếp List, tìm kiếm và min/max

`Collections.sort(list)`, `List.sort(null)` và `Arrays.sort(array)` sử dụng thứ tự tự nhiên của `BigDecimal` khi không cung cấp comparator. Điều này có nghĩa chúng sắp **theo giá trị số**. Những đối tượng bằng nhau theo `compareTo` không bị bắt buộc trở thành cùng một đối tượng hoặc bị loại khỏi `List`. `List` giữ đủ số phần tử.

Nếu dùng `Collections.binarySearch(sortedList, target)` theo thứ tự tự nhiên, kết quả có thể trỏ vào phần tử bằng **giá trị số** nhưng khác `scale` so với `target`. Nếu có nhiều phần tử được xem là tương đương theo comparator, API không hứa trả lại một phần tử cụ thể trong số đó. Muốn tìm **đúng biểu diễn**, phải dùng một tiêu chí có xét `scale` hoặc duyệt kiểm tra bằng `equals()`.

Tương tự, `min()` và `max()` của `BigDecimal` chọn theo `compareTo()`. Nếu giá trị so sánh bằng 0, chúng trả về chính đối tượng nhận lệnh (`this`), vì vậy `min`/`max` không tự chuẩn hóa `scale`:

~~~java
BigDecimal one = new BigDecimal("2.0");
BigDecimal two = new BigDecimal("2.00");

System.out.println(one.min(two)); // 2.0
System.out.println(two.min(one)); // 2.00
~~~

Đây không phải lỗi trong thuật toán min/max; hai đối tượng bằng nhau theo thứ tự số học, và biểu diễn được chọn là một phần của hợp đồng phương thức.

## 8. Chiến lược A: danh tính dựa trên giá trị số

Nếu nghiệp vụ nói rằng `2.0`, `2.00` và `2.000` phải là **cùng một giá trị** để lọc trùng, tổng hợp hoặc tra cứu, có thể **chuẩn hóa trước khi sử dụng làm khóa**. Một cách phổ biến là `stripTrailingZeros()`:

~~~java
BigDecimal a = new BigDecimal("2.0").stripTrailingZeros();
BigDecimal b = new BigDecimal("2.00").stripTrailingZeros();

System.out.println(a);            // 2
System.out.println(b);            // 2
System.out.println(a.equals(b));  // true
~~~

Khi chuẩn hóa hai phía theo cùng quy tắc, `HashSet`/`HashMap` có thể thực hiện danh tính số học nhất quán với `TreeSet`/`TreeMap` cho những giá trị đã chuẩn hóa. Cần thực hiện chuẩn hóa **ngay tại biên nhận dữ liệu hoặc trước mọi lần `put`, `get`, `contains` và `remove`**. Chuẩn hóa chỉ lúc chèn mà không chuẩn hóa lúc tra cứu sẽ tiếp tục sinh ra hành vi khó hiểu.

Ví dụ quy tắc chuẩn hóa:

~~~java
private static BigDecimal numericKey(BigDecimal value) {
    return value.stripTrailingZeros();
}
~~~

Đoạn trên cần đặt bên trong một lớp Java. `stripTrailingZeros()` giữ **giá trị số**, nhưng có thể đổi cách hiển thị và `scale`. Chẳng hạn `new BigDecimal("1000.00").stripTrailingZeros()` có thể hiển thị `1E+3` với `scale = -3`; mọi giá trị 0 sau chuẩn hóa có kết quả tương đương `BigDecimal.ZERO` (`scale = 0`).

**Giới hạn quan trọng:** Không áp dụng chuẩn hóa nếu `scale` biểu đạt số chữ số thập phân theo hợp đồng, độ phân giải phép đo, hoặc quy tắc số chữ số tiền tệ cần được giữ nguyên. Khi đó `2.0` và `2.00` có thể cần được phân biệt.

## 9. Chiến lược B: danh tính gồm giá trị số và scale

Khi bắt buộc phải giữ cả hai biểu diễn trong một sorted collection, dùng comparator sắp theo **giá trị số rồi tới `scale`**:

~~~java
Comparator<BigDecimal> valueThenScale =
        Comparator.<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);

TreeSet<BigDecimal> values = new TreeSet<>(valueThenScale);
values.add(new BigDecimal("2.0"));
values.add(new BigDecimal("2.00"));

System.out.println(values.size()); // 2
~~~

Phép so sánh này nhất quán với `BigDecimal.equals()`:

- Nếu giá trị số khác nhau, comparator phân biệt.
- Nếu giá trị số bằng nhau nhưng `scale` khác nhau, bước `thenComparingInt` phân biệt.
- Nếu giá trị số và `scale` đều bằng nhau, hai đối tượng `BigDecimal` cũng bằng nhau theo `equals()`, nên comparator trả 0.

**Không** dùng `Comparator.comparing(BigDecimal::stripTrailingZeros)` nếu mục tiêu là giữ riêng từng `scale`: cách đó vẫn gộp những giá trị số bằng nhau. Cũng không so sánh chỉ bằng `scale`, vì hai giá trị số khác nhau nhưng có cùng `scale` sẽ bị coi là trùng. Thứ tự “giá trị trước, `scale` sau” là điều kiện quyết định trong chiến lược này.

Lưu ý comparator này phù hợp với đúng ngữ nghĩa `BigDecimal.equals()`, không phải mọi yêu cầu nghiệp vụ. Ví dụ một hệ thống có định danh giao dịch thì hai số `2.00` đến từ **hai giao dịch khác nhau** vẫn phải giữ cả hai; tập hợp các `BigDecimal` không thể mang đầy đủ định danh giao dịch. Khi đó nên lưu thực thể hoặc khóa có định danh riêng.

## 10. Chiến lược C: quy định scale cố định theo nghiệp vụ

Nếu toàn hệ thống yêu cầu **hai chữ số thập phân**, hãy chuẩn hóa về `scale = 2` ngay khi xác nhận đầu vào. Khi đó `2`, `2.0`, `2.00` có thể trở thành cùng `BigDecimal("2.00")`, nếu việc chuyển đổi không làm mất dữ liệu.

~~~java
BigDecimal first = new BigDecimal("2.0").setScale(2);
BigDecimal second = new BigDecimal("2.00").setScale(2);
System.out.println(first.equals(second)); // true
~~~

`setScale(2)` **không phải** phép nhân với 100; nó yêu cầu biểu diễn giá trị ở hai vị trí thập phân. Nếu cần bỏ chữ số khác 0, phiên bản không truyền rounding mode ném `ArithmeticException`:

~~~java
// Ném ArithmeticException vì 2.005 không biểu diễn chính xác với scale 2:
new BigDecimal("2.005").setScale(2);
~~~

Nếu nghiệp vụ cho phép làm tròn, hãy chỉ định rõ quy tắc, ví dụ `setScale(2, RoundingMode.HALF_EVEN)`. Làm tròn có thể thay đổi **giá trị số**, vì vậy không thể mặc nhiên coi việc chuẩn hóa `scale` là thao tác vô hại.

Có thể phối hợp chiến lược: dùng số đã chuẩn hóa để tính tiền, nhưng giữ chuỗi đầu vào hoặc metadata riêng khi cần kiểm toán độ chính xác mà người dùng khai báo. Không dùng một đối tượng `BigDecimal` duy nhất để giải quyết đồng thời hai định nghĩa danh tính mâu thuẫn nhau.

## 11. Vì sao scale đôi khi thực sự quan trọng?

`BigDecimal.equals()` phân biệt biểu diễn không chỉ vì “số 0 thừa”. Một số phép toán cung cấp kết quả phụ thuộc vào `scale` của toán hạng. JavaDoc đưa ra ví dụ hai phiên bản của số 2 khi dùng overload phép chia với `RoundingMode`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal divisor = new BigDecimal("3");

System.out.println(
        new BigDecimal("2.0").divide(divisor, RoundingMode.HALF_UP)
); // 0.7

System.out.println(
        new BigDecimal("2.00").divide(divisor, RoundingMode.HALF_UP)
); // 0.67
~~~

Overload `divide(BigDecimal, RoundingMode)` dùng `scale` của **số bị chia** làm `scale` kết quả, rồi làm tròn theo `RoundingMode`. Hai số cùng giá trị 2 nhưng có `scale` khác dẫn đến số chữ số giữ lại khác nhau. Đoạn trên đặt trong phương thức Java; nó minh họa một lý do thực tế phải hiểu `scale` trước khi chuẩn hóa.

Đối với phép chia cần quy định precision theo `MathContext`, hoặc `scale` kết quả bằng `divide(divisor, requestedScale, roundingMode)`, hãy chọn overload phù hợp một cách có ý thức. Không suy rằng `compareTo() == 0` cho phép hoán đổi mọi đối tượng mà không đổi kết quả biểu diễn trong toàn bộ API số học.

## 12. Những lỗi phổ biến cần tránh

1. **Dùng `equals()` để so sánh số tiền theo độ lớn hoặc bằng giá trị:** với `BigDecimal`, `amount.compareTo(other) == 0` diễn đạt bằng số học. Muốn kiểm tra nhỏ/lớn thì dùng `< 0` hoặc `> 0` trên kết quả `compareTo()`.
2. **Dùng `HashSet` rồi chuyển sang `TreeSet` mà không đánh giá lại danh tính:** cùng dữ liệu đầu vào có thể cho kích thước và kết quả `contains()` khác nhau.
3. **Dùng `TreeMap` để lưu hai khóa chỉ khác `scale`:** lần `put` sau sẽ cập nhật value thay vì tăng kích thước map.
4. **Cho rằng `TreeSet.contains(x)` đồng nghĩa có phần tử `equals(x)`:** `contains()` của cây áp dụng quy tắc so sánh; với `BigDecimal` mặc định, hai quy tắc có thể bất đồng.
5. **So sánh `BigDecimal` bằng `==`:** toán tử này so sánh tham chiếu trong Java, không phải bằng nhau về giá trị số hoặc `scale`.
6. **Chỉ dựa vào `hashCode()`:** hash bằng nhau không bảo đảm `equals()` bằng nhau; nếu muốn danh tính số học trong hash collection, chuẩn hóa khóa theo quy ước.
7. **Tưởng `stripTrailingZeros()` chỉ là thay đổi hiển thị:** kết quả có thể có `scale` âm, và `equals()` của kết quả với đối tượng ban đầu có thể khác.
8. **Dùng `new BigDecimal(0.1)` để lập ví dụ hoặc khóa:** constructor từ `double` đưa vào giá trị nhị phân thực sự của `double`, thường không bằng đúng thập phân được viết. Dùng chuỗi như `"0.1"` hoặc `BigDecimal.valueOf(0.1)` khi dữ liệu gốc là giá trị thập phân quen thuộc.
9. **Đọc quy tắc comparator bằng mắt nhưng quên tra cứu:** với `TreeMap`/`TreeSet` tùy chỉnh, cùng comparator phải được áp dụng cho toàn bộ vòng đời collection; dữ liệu dùng qua nhiều collection cần chính sách tương đương rõ ràng.
10. **Tưởng thêm comparator luôn bảo toàn mọi bản ghi:** nếu hai bản ghi có cùng số và cùng `scale` nhưng là hai thực thể khác nhau, comparator ở mục 9 vẫn coi chúng tương đương. Hãy dùng khóa danh tính thực thể phù hợp.

## 13. Bài tập kiểm tra hiểu biết

### Bài 1: dự đoán số lượng

~~~java
BigDecimal x = new BigDecimal("0.0");
BigDecimal y = new BigDecimal("0.00");

HashSet<BigDecimal> h = new HashSet<>();
TreeSet<BigDecimal> t = new TreeSet<>();
h.add(x);
h.add(y);
t.add(x);
t.add(y);
~~~

**Đáp án:** `h.size() == 2`, `t.size() == 1`. `x.compareTo(y) == 0` nhưng `x.equals(y) == false`. Nếu gọi `stripTrailingZeros()` trên cả `x` và `y` **trước khi thêm**, cả hai cùng trở thành dạng zero chuẩn và `HashSet` chỉ còn một phần tử.

### Bài 2: Map nào ghi đè?

~~~java
BigDecimal first = new BigDecimal("10.0");
BigDecimal second = new BigDecimal("10.00");

TreeMap<BigDecimal, String> map = new TreeMap<>();
map.put(first, "A");
String old = map.put(second, "B");
~~~

**Đáp án:** `old` là `"A"`, `map.size() == 1`, `map.get(first)` là `"B"` và `map.get(second)` cũng là `"B"`. Nếu dùng `HashMap` thay thế và giữ nguyên các khóa, kích thước là 2 vì chúng khác `equals()`.

### Bài 3: comparator nhất quán?

Xét ba cách tạo comparator:

~~~java
Comparator<BigDecimal> c1 = Comparator.naturalOrder();
Comparator<BigDecimal> c2 =
        Comparator.comparing(BigDecimal::stripTrailingZeros);
Comparator<BigDecimal> c3 =
        Comparator.<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);
~~~

**Đáp án:** `c1` và `c2` cùng xem `2.0` và `2.00` là tương đương; chúng **không nhất quán với `BigDecimal.equals()`**. `c3` vừa sắp theo giá trị số vừa phân biệt `scale`, do đó nhất quán với `equals()` trên các đối tượng `BigDecimal`.

### Bài 4: chọn thiết kế

Một hệ thống nhận giá `12.0` và `12.00` từ hai tệp khác nhau. Yêu cầu (A) là đếm số **giá trị số duy nhất**, còn yêu cầu (B) là đếm số **biểu diễn số học phân biệt theo `scale`**.

**Đáp án định hướng:** (A) chuẩn hóa bằng `stripTrailingZeros()` rồi dùng `HashSet`, hoặc dùng `TreeSet` theo thứ tự tự nhiên nếu chỉ cần tập giá trị số. (B) dùng `HashSet` với `equals()` mặc định, hoặc `TreeSet` với comparator giá trị rồi `scale`. Nếu còn cần phân biệt **từng dòng nhập**, phải dùng ID của dòng thay vì chỉ dùng `BigDecimal`.

## 14. Ghi nhớ và tài liệu chính thức

- `compareTo()` của `BigDecimal` là **so sánh số học**; `equals()` kiểm tra **giá trị và `scale`**.
- “Thứ tự tự nhiên không nhất quán với `equals`” nghĩa là có các cặp mà `compareTo() == 0` nhưng `equals() == false`. Điều này có thể khiến các tập/bản đồ có sắp xếp và có băm không đồng thuận về phần tử/khóa trùng nhau.
- **Giá trị số**, **biểu diễn kèm `scale`** và **danh tính của bản ghi nghiệp vụ** là ba khái niệm khác nhau. Hãy chọn một quy tắc cụ thể và áp dụng nhất quán lúc lưu cũng như lúc truy xuất.

Tài liệu tham khảo (Java SE 17/21):
- [`BigDecimal` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): mục `compareTo`, `equals`, `hashCode`, `divide` và `stripTrailingZeros`.
- [`Comparable` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html): định nghĩa natural ordering nhất quán với `equals` và mô tả trường hợp `BigDecimal`.
- [`Comparator` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html): tính nhất quán giữa comparator và `equals` đối với sorted collections.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-006">Hệ quả khi dùng TreeSet hoặc TreeMap</a>

<details>
<summary>Xem chi tiết</summary>

# Hệ quả khi dùng BigDecimal trong TreeSet hoặc TreeMap

## 1. Vấn đề: cùng giá trị số học nhưng không cùng cách biểu diễn

Trong Java, `BigDecimal` có hai cách xem xét sự bằng nhau:

- `compareTo(BigDecimal)` so sánh **giá trị số học**. Nếu hai số bằng nhau về giá trị, `compareTo()` trả `0`, dù khác `scale`.
- `equals(Object)` yêu cầu **cùng giá trị số học và cùng scale**. Vì thế, `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false`.

Ví dụ ngắn:

```java
BigDecimal a = new BigDecimal("2.0");  // unscaled=20, scale=1
BigDecimal b = new BigDecimal("2.00"); // unscaled=200, scale=2

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
```

Trong tài liệu Java, hai đối tượng như `a` và `b` thuộc cùng một *cohort*: bằng nhau về giá trị số học, nhưng có biểu diễn thập phân khác nhau. Thứ tự tự nhiên của `BigDecimal` (*natural ordering*) được xác định qua `compareTo()` và **không nhất quán với `equals()`** (*inconsistent with equals*).

Sự khác biệt này tác động trực tiếp khi dùng `BigDecimal` làm **phần tử của `TreeSet`** hoặc **khóa của `TreeMap`**. Hai cấu trúc này dựa vào **thứ tự so sánh**, chứ không dựa vào `equals()` để xác định hai phần tử/khóa được xem như cùng một.

## 2. TreeSet: một phần tử có thể bị coi là trùng dù equals là false

`TreeSet` sắp xếp phần tử theo thứ tự tự nhiên, hoặc theo `Comparator` truyền khi khởi tạo. Nếu không truyền comparator, với `BigDecimal` nó dùng `compareTo()`.

Các thao tác như `add()`, `contains()`, `remove()` dựa vào kết quả so sánh. Khi `compareTo() == 0`, `TreeSet` coi hai đối tượng tương đương để quyết định một phần tử đã tồn tại, bất kể `equals()` có thể trả `false`.

```java
TreeSet<BigDecimal> values = new TreeSet<>();
boolean first = values.add(new BigDecimal("2.0"));  // true
boolean second = values.add(new BigDecimal("2.00")); // false

System.out.println(values.size());                   // 1
System.out.println(values.contains(new BigDecimal("2.000"))); // true
```

Điều gì diễn ra?

1. Phần tử `2.0` được thêm vào vì tập ban đầu rỗng.
2. `2.00` so sánh bằng `2.0` theo `compareTo() == 0`, nên lần `add` thứ hai trả `false` và không tăng kích thước.
3. Lời gọi `contains(new BigDecimal("2.000"))` trả `true`, dù `"2.000"` không `equals()` với phần tử `"2.0"` đang lưu.
4. Với `remove(new BigDecimal("2.000"))`, `TreeSet` có thể xóa phần tử `"2.0"` vì chúng bằng nhau theo cách so sánh của tập.

**Hệ quả thực tế:** nếu người lập trình mong `TreeSet<BigDecimal>` lưu cả `2.0` lẫn `2.00` như hai biểu diễn riêng, việc mất phần tử là hành vi *được định nghĩa bởi comparator*, không phải lỗi ngẫu nhiên của Java.

Theo hợp đồng chung của `Set`, tính duy nhất được mô tả bằng `equals()`. Vì `TreeSet` với thứ tự tự nhiên `BigDecimal` lại xác định trùng nhau bằng `compareTo()`, thứ tự này **không nhất quán với hợp đồng `Set`**; tài liệu Java cảnh báo rõ trường hợp đó. Các thao tác của `TreeSet` vẫn hoạt động theo quy tắc sắp xếp đã chọn.

## 3. TreeMap: hai khóa khác equals có thể trỏ tới cùng một entry

Với `TreeMap<BigDecimal, V>` dùng thứ tự tự nhiên, nếu hai khóa so sánh bằng nhau (`compareTo() == 0`), `TreeMap` xem chúng là **một khóa logic**.

```java
TreeMap<BigDecimal, String> prices = new TreeMap<>();

prices.put(new BigDecimal("2.0"), "first");
prices.put(new BigDecimal("2.00"), "second");

System.out.println(prices.size());                      // 1
System.out.println(prices.get(new BigDecimal("2.000"))); // second
```

Ở lần `put` đầu, map tạo một entry. Lần `put` thứ hai tìm thấy khóa tương đương theo comparator, nên **giá trị của entry cũ bị thay bằng `"second"`**; nó không tạo entry mới. Khóa được lưu trong ví dụ vẫn là đối tượng `2.0` đã chèn đầu tiên. Có thể quan sát qua `firstKey()` hoặc duyệt `keySet()`; việc thay giá trị không đồng nghĩa thay đối tượng khóa đang lưu.

Các thao tác tìm kiếm như `get()`, `containsKey()`, `remove()` cũng dựa trên thứ tự so sánh. Một khóa mới với giá trị `2.000` có thể truy cập hoặc xóa entry đã tạo bằng `2.0`.

Điều này đặc biệt đáng lưu ý nếu khóa `BigDecimal` đại diện cho giá trị có **scale mang ý nghĩa nghiệp vụ** (ví dụ độ chính xác đo lường hoặc định dạng số thập phân cần phân biệt). Trong trường hợp đó, hai khóa có thể bị gộp bất ngờ, gây ghi đè dữ liệu.

## 4. Chương trình Java 17 chạy được: đối chiếu TreeSet, TreeMap, HashSet, HashMap

Tạo tệp **`BigDecimalSortedCollectionsDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalSortedCollectionsDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        BigDecimal probe = new BigDecimal("2.000");

        System.out.println("equals=" + a.equals(b));
        System.out.println("compareToZero=" + (a.compareTo(b) == 0));
        System.out.println("scales=" + a.scale() + "," + b.scale());

        Set<BigDecimal> treeSet = new TreeSet<>();
        System.out.println("treeAddA=" + treeSet.add(a));
        System.out.println("treeAddB=" + treeSet.add(b));
        System.out.println("treeSize=" + treeSet.size());
        System.out.println("treeContainsProbe=" + treeSet.contains(probe));
        System.out.println("treeStored=" + treeSet.iterator().next());

        Map<BigDecimal, String> treeMap = new TreeMap<>();
        System.out.println("mapPutA=" + treeMap.put(a, "first"));
        System.out.println("mapPutB=" + treeMap.put(b, "second"));
        System.out.println("mapSize=" + treeMap.size());
        System.out.println("mapGetProbe=" + treeMap.get(probe));
        System.out.println("mapStoredKey=" + treeMap.keySet().iterator().next());

        Set<BigDecimal> hashSet = new HashSet<>();
        hashSet.add(a);
        hashSet.add(b);
        Map<BigDecimal, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        hashMap.put(b, "second");
        System.out.println("hashSetSize=" + hashSet.size());
        System.out.println("hashMapSize=" + hashMap.size());
        System.out.println("hashMapGetProbe=" + hashMap.get(probe));

        Comparator<BigDecimal> byValueThenScale = (left, right) -> {
            int result = left.compareTo(right);
            return result != 0 ? result
                    : Integer.compare(left.scale(), right.scale());
        };
        Set<BigDecimal> strictTreeSet = new TreeSet<>(byValueThenScale);
        strictTreeSet.add(a);
        strictTreeSet.add(b);
        System.out.println("strictTreeSize=" + strictTreeSet.size());
        System.out.println("strictContainsProbe=" + strictTreeSet.contains(probe));

        Set<BigDecimal> normalizedSet = new TreeSet<>();
        normalizedSet.add(a.stripTrailingZeros());
        normalizedSet.add(b.stripTrailingZeros());
        System.out.println("normalizedSize=" + normalizedSet.size());
        System.out.println("normalizedValue=" + normalizedSet.iterator().next());
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalSortedCollectionsDemo.java
java BigDecimalSortedCollectionsDemo
```

Kết quả (đã chạy thực tế với biên dịch `javac --release 17`):

```text
equals=false
compareToZero=true
scales=1,2
treeAddA=true
treeAddB=false
treeSize=1
treeContainsProbe=true
treeStored=2.0
mapPutA=null
mapPutB=first
mapSize=1
mapGetProbe=second
mapStoredKey=2.0
hashSetSize=2
hashMapSize=2
hashMapGetProbe=null
strictTreeSize=2
strictContainsProbe=false
normalizedSize=1
normalizedValue=2
```

Các điểm cần đọc kỹ trong output:

- `treeAddB=false`: `TreeSet` bỏ qua phần tử thứ hai vì so sánh số học bằng nhau.
- `treeContainsProbe=true`: phần tử truy vấn `2.000` được coi là có trong tập dù khác scale với phần tử lưu `2.0`.
- `mapPutB=first`: `Map.put` trả giá trị cũ, chứng tỏ lần chèn thứ hai cập nhật một entry có sẵn; `mapGetProbe=second` cho biết giá trị mới.
- `mapStoredKey=2.0`: entry hiện có vẫn mang khóa của lần chèn đầu tiên trong tình huống này.
- `hashSetSize=2` và `hashMapSize=2`: cấu trúc băm coi `a` và `b` khác nhau vì dựa vào `equals()` và `hashCode()`.
- `strictTreeSize=2`: một comparator được thiết kế để phân biệt scale đã giữ cả hai đối tượng.
- `normalizedSize=1`: chủ động chuẩn hóa dữ liệu khiến hai biểu diễn cùng giá trị trở thành một dạng để lưu.

## 5. So sánh ý nghĩa “trùng nhau” trong các collection

| Cấu trúc | Quy tắc xác định trùng phần tử/khóa | `2.0` với `2.00` theo mặc định |
| --- | --- | --- |
| `HashSet<BigDecimal>` | `equals()` và `hashCode()` | Hai phần tử riêng (size 2) |
| `HashMap<BigDecimal, V>` | `equals()` và `hashCode()` | Hai khóa riêng (size 2) |
| `TreeSet<BigDecimal>` | `compareTo() == 0` theo thứ tự tự nhiên | Một phần tử (size 1) |
| `TreeMap<BigDecimal, V>` | `compareTo() == 0` theo thứ tự tự nhiên | Một khóa (size 1), giá trị sau ghi đè |
| `TreeSet/TreeMap` với `Comparator` tùy chỉnh | `comparator.compare(a, b) == 0` | Tùy comparator |

Với `HashSet`/`HashMap`, `hashCode()` của `BigDecimal` phản ánh giá trị không scale và scale. Vì vậy `2.0` và `2.00` có thể có mã băm khác nhau; điều cốt yếu vẫn là `equals()` trả `false`. Không được suy từ việc hai số có cùng giá trị số học rằng chúng sẽ là cùng khóa trong `HashMap`.

Với `TreeSet`/`TreeMap`, **các phương thức `equals()` của đối tượng khóa/phần tử không phải tiêu chí duy nhất** để xác định tương đương. Cấu trúc sắp xếp dùng kết quả so sánh làm tiêu chí định danh trong thao tác tìm kiếm.

## 6. Cách chọn thiết kế đúng theo yêu cầu dữ liệu

### Trường hợp A — Hai biểu diễn cùng số phải được xem là một giá trị

Ví dụ nghiệp vụ chỉ quan tâm `2`, `2.0` và `2.00` như **cùng một giá trị số học**.

Dùng `TreeSet`/`TreeMap` theo thứ tự tự nhiên **có thể chủ đích phù hợp** nếu bạn chấp nhận rằng khóa/phần tử được định danh theo `compareTo() == 0` và hiểu hạn chế về tính nhất quán với `equals()`. Cần ghi rõ quy tắc này trong API để tránh nhầm với `HashSet`/`HashMap`.

Một lựa chọn khác là **chuẩn hóa khóa trước khi lưu và khi truy vấn**, đặc biệt khi muốn cấu trúc băm và cấu trúc sắp xếp thống nhất về tính duy nhất:

```java
static BigDecimal numericKey(BigDecimal value) {
    return value.stripTrailingZeros();
}

// Ví dụ trong một phương thức:
Set<BigDecimal> keys = new HashSet<>();
keys.add(numericKey(new BigDecimal("2.0")));
keys.add(numericKey(new BigDecimal("2.00")));
// keys.size() == 1

boolean found = keys.contains(numericKey(new BigDecimal("2.000")));
// found == true
```

Phương thức `numericKey` là **đoạn minh họa**, phải đặt trong một lớp Java. Quy tắc chuẩn hóa phải áp dụng **nhất quán ở cả ghi và đọc**. `stripTrailingZeros()` có thể trả số dạng khoa học với scale âm (ví dụ `600.0` thành `6E+2`), và số 0 được chuẩn hóa về `BigDecimal.ZERO`. Nếu scale có ý nghĩa, cách chuẩn hóa này không đáp ứng yêu cầu.

### Trường hợp B — Hai biểu diễn khác scale phải là hai phần tử/khóa riêng

Ví dụ yêu cầu phân biệt `2.0` (scale 1) và `2.00` (scale 2). Với collection không cần sắp xếp, `HashSet`/`HashMap` đã tuân theo `equals()` của `BigDecimal`.

Nếu vẫn cần thứ tự sắp xếp, cung cấp comparator: **so sánh giá trị trước, so sánh scale khi giá trị số học bằng nhau**.

```java
Comparator<BigDecimal> byValueThenScale = (left, right) -> {
    int result = left.compareTo(right);
    return result != 0 ? result
            : Integer.compare(left.scale(), right.scale());
};

TreeSet<BigDecimal> distinctScales = new TreeSet<>(byValueThenScale);
distinctScales.add(new BigDecimal("2.0"));
distinctScales.add(new BigDecimal("2.00"));
// distinctScales.size() == 2

TreeMap<BigDecimal, String> details = new TreeMap<>(byValueThenScale);
details.put(new BigDecimal("2.0"), "scale 1");
details.put(new BigDecimal("2.00"), "scale 2");
// details.size() == 2
```

Vì sao comparator này nhất quán với `equals()` của `BigDecimal`? Nếu hai giá trị có cùng giá trị số học **và cùng scale**, phần unscaled của chúng cũng giống nhau, nên `equals()` trả `true`. Do đó comparator trả 0 đúng trong trường hợp `equals()` trả `true`. Comparator vẫn thỏa tính phản đối xứng, bắc cầu và thứ tự nhất quán.

Tuy nhiên, comparator này sẽ cho phép hai số cùng giá trị nhưng khác scale tồn tại cùng lúc. Nếu ứng dụng cần tính duy nhất **theo giá trị số học**, hãy chọn quy tắc của trường hợp A; đừng áp dụng comparator này chỉ vì muốn “khắc phục” cảnh báo.

### Trường hợp C — Có nhiều thuộc tính tạo nên danh tính nghiệp vụ

Nếu một bản ghi có cả số tiền, mã tiền tệ, mức độ chính xác, thời điểm và mã định danh, thì việc chọn `BigDecimal` làm khóa duy nhất có thể đánh mất ngữ nghĩa. Cần tạo khóa nghiệp vụ rõ ràng, ví dụ một record chứa mã tiền tệ và giá trị tiền đã được quy đổi về đơn vị nhỏ nhất theo một quy tắc được định nghĩa trước:

```java
record MoneyKey(String currencyCode, long minorUnits) {}
```

Đây là ví dụ về **kiểu khóa**; việc quy đổi `BigDecimal` sang `minorUnits` phải kiểm tra scale, làm tròn, phạm vi `long` và quy tắc đơn vị tiền tệ riêng. Khi cần giá trị lớn, có thể dùng `BigInteger` thay `long`. Không quy đổi bằng `double` rồi ép kiểu vì có thể mất chính xác.

## 7. Những hệ quả dễ bị bỏ qua khi triển khai

**Gộp dữ liệu và mất record:** chương trình thống kê đưa `2.0` và `2.00` vào `TreeMap<BigDecimal, Result>` có thể chỉ còn một entry. Nếu mỗi chuỗi đầu vào là một phép đo riêng cần lưu, khóa cần bổ sung danh tính khác hoặc đổi comparator.

**Đọc được bằng khóa chưa từng lưu:** truy vấn `treeMap.get(new BigDecimal("2.000"))` có thể trả dữ liệu của khóa `2.0`. Điều này thuận tiện cho tra cứu theo số học, nhưng nguy hiểm khi người dùng tưởng scale là một phần của định danh.

**Ghi đè ngoài ý muốn:** các lời gọi `put`, `putIfAbsent`, `computeIfAbsent`, `merge` có thể xem các khóa `BigDecimal` khác scale là một khóa. Tùy phương thức, kết quả là giữ giá trị trước, cập nhật hoặc gộp dữ liệu. Kiểm tra logic nghiệp vụ thay vì coi mỗi đối tượng mới là một khóa mới.

**Kết quả khác giữa hai loại collection:** dữ liệu đưa vào `HashSet` có thể có 2 phần tử, chuyển sang `TreeSet` mặc định chỉ còn 1. Việc chuyển đổi kiểu collection có thể thay đổi chính sách trùng lặp, không chỉ khả năng sắp xếp.

**Tác động tới `contains` và `remove`:** với `TreeSet`, một đối tượng tìm kiếm khác scale vẫn có thể tìm được hoặc xóa đối tượng đang lưu. Điều này quan trọng với code dùng kiểm tra quyền, điều kiện tồn tại hay thao tác xóa dựa theo khóa được dựng lại từ chuỗi đầu vào.

**Vấn đề giữ định dạng:** khi `TreeMap.put` cập nhật value cho khóa có `compareTo() == 0`, entry ban đầu vẫn có thể hiển thị scale của khóa chèn trước. Không dựa vào `keySet().iterator().next().toString()` để suy ra cách người dùng nhập dữ liệu mới nhất. Nếu cần lưu nguyên văn số đầu vào, lưu nó thành trường dữ liệu riêng.

## 8. Lỗi thường gặp và cách phòng tránh

1. **Cho rằng `compareTo() == 0` đồng nghĩa `equals() == true`.** Với `BigDecimal` điều này sai khi scale khác. Hãy xác định bạn cần so sánh số học hay so sánh cả biểu diễn.
2. **Tin rằng `TreeSet` gọi `equals()` khi thêm phần tử.** Nó dựa trên thứ tự, nên hai đối tượng `equals=false` vẫn có thể bị coi là trùng.
3. **Dùng `TreeMap` làm bảng lưu bản ghi khác nhau nhưng chỉ lấy giá trị thập phân làm khóa.** Những khóa cùng giá trị số học sẽ gộp entry. Bổ sung mã định danh hoặc comparator phù hợp.
4. **Chuyển `HashMap` sang `TreeMap` để có sắp xếp mà không kiểm tra chính sách trùng lặp.** Kích thước có thể thay đổi. Hãy test bộ dữ liệu có `2.0`, `2.00` và `2.000`.
5. **Dùng comparator dựa trên `toString()` khi muốn so sánh BigDecimal số học.** Thứ tự từ điển của chuỗi không tương đương thứ tự số học và còn phụ thuộc dạng biểu diễn (ví dụ `"10"` và `"2"`). Dùng `compareTo()` và, khi cần, thêm scale làm tiêu chí phụ.
6. **Chuẩn hóa bằng `stripTrailingZeros()` mà không xét hậu quả.** Số 0 và scale âm là trường hợp quan trọng; chuẩn hóa có thể xóa thông tin số chữ số thập phân ban đầu. Quy định cách lưu định dạng nguyên gốc riêng nếu cần.
7. **Tạo `BigDecimal` từ `double` rồi khó hiểu kết quả.** `new BigDecimal(0.1)` phản ánh giá trị nhị phân gần 0.1 của `double`. Với dữ liệu thập phân đầu vào, thường nên khởi tạo từ chuỗi `new BigDecimal("0.1")`, hoặc cân nhắc `BigDecimal.valueOf(0.1)`.
8. **Bỏ qua ràng buộc nhất quán của comparator.** Comparator cho `TreeSet`/`TreeMap` phải bảo đảm thứ tự hợp lệ (chẳng hạn phản đối xứng và bắc cầu); muốn tuân thủ đầy đủ hợp đồng `Set`/`Map`, điều kiện `compare(a,b) == 0` cũng cần tương đương `a.equals(b)`.

## 9. Checklist ra quyết định

Trước khi đặt `BigDecimal` vào `TreeSet` hoặc làm khóa `TreeMap`, tự trả lời:

1. “Trùng” nghĩa là **cùng giá trị số học** hay **cùng giá trị và scale**?
2. Ứng dụng có cần giữ nguyên các số 0 tận cùng, nguyên văn đầu vào hoặc đơn vị đo không?
3. Có cần sắp xếp không? Nếu không, `HashSet`/`HashMap` có thể phù hợp hơn khi dùng định nghĩa `equals()`.
4. Có cần một comparator nhất quán với `equals()`, chẳng hạn so sánh số học rồi so sánh scale?
5. Nếu chuẩn hóa, mọi đường ghi, tìm kiếm, cập nhật và xóa đã áp dụng cùng quy tắc chưa?
6. Các trường hợp `0.0`/`0.00`, `2.0`/`2.00`, scale âm và giá trị âm đã được kiểm tra chưa?

## 10. Tự kiểm tra

**Câu 1.** `TreeSet<BigDecimal>` lần lượt nhận `"1.0"`, `"1.00"`, `"2.0"`. Kích thước là bao nhiêu?  
**Đáp án:** 2, vì hai biểu diễn giá trị 1 so sánh bằng nhau qua `compareTo()`.

**Câu 2.** `TreeMap<BigDecimal, String>` lưu `"1.0" -> "A"` rồi `"1.00" -> "B"`. Truy vấn khóa `"1.000"` trả gì?  
**Đáp án:** `"B"`. Mọi khóa đó bằng nhau theo thứ tự tự nhiên, và `put` thứ hai thay value của entry đầu.

**Câu 3.** Với cùng hai giá trị `"1.0"`, `"1.00"`, `HashSet` có bao nhiêu phần tử?  
**Đáp án:** 2 vì `equals()` phân biệt scale.

**Câu 4.** Nếu ứng dụng cần cả hai scale trong một `TreeSet`, nên làm gì?  
**Đáp án:** truyền comparator so sánh theo giá trị số học, sau đó theo scale để tránh `compare()==0` khi `equals()==false`.

**Câu 5.** Dùng `stripTrailingZeros()` có luôn bảo toàn định dạng đầu vào không?  
**Đáp án:** không; `600.0` có thể trở thành `6E+2` và mất scale/chuỗi thể hiện ban đầu.

## 11. Ghi nhớ

- `BigDecimal.compareTo()` so sánh **số học**; `BigDecimal.equals()` so sánh **số học và scale**.
- `TreeSet` và `TreeMap` quyết định trùng nhau dựa trên **`compareTo()` hoặc `Comparator.compare()` bằng 0**, nên `2.0` và `2.00` bị gộp mặc định.
- `HashSet` và `HashMap` dùng `equals()`/`hashCode()` nên hai biểu diễn trên là hai phần tử/khóa riêng.
- Giải pháp đúng phụ thuộc ý nghĩa của khóa: **nhất quán số học** có thể chuẩn hóa; **phân biệt scale** có thể dùng comparator so sánh số học rồi scale; **định danh nghiệp vụ phức tạp** cần một kiểu khóa rõ ràng.

Tài liệu chính thức (Java SE 17):
- [`BigDecimal` — so sánh, equals, hashCode, stripTrailingZeros](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [`Comparable` — natural ordering và consistency with equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html)
- [`TreeSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [`TreeMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)
- [`Comparator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-007">Chính sách chuẩn hóa scale trước khi lưu hoặc so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# Chính sách chuẩn hóa `scale` của `BigDecimal` trước khi lưu hoặc so sánh

## 1. Vì sao cần một chính sách chuẩn hóa?

Trong Java, hai đối tượng `BigDecimal` có thể biểu diễn **cùng một giá trị toán học** nhưng có **`scale` khác nhau**:

```java
BigDecimal a = new BigDecimal("1.0");  // scale = 1
BigDecimal b = new BigDecimal("1.00"); // scale = 2

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
```

`compareTo` chỉ xét thứ tự/giá trị số, trong khi `equals` còn yêu cầu cùng `scale`. Sự khác biệt này có thể gây kết quả bất ngờ khi dùng `BigDecimal` làm khóa `HashMap`, phần tử `HashSet`, dữ liệu định danh, giá trị lưu trong cơ sở dữ liệu hoặc trường trong bản ghi được so sánh bằng `equals`.

**Mục tiêu của bài:** xây dựng chính sách nhất quán để quyết định *khi nào* chuẩn hóa `scale`, *chuẩn hóa bằng cách nào* và *ở ranh giới nào* (trước khi lưu, trước khi so sánh, khi tạo khóa, khi trao đổi dữ liệu), mà không làm mất thông tin hoặc âm thầm thay đổi giá trị.

Không có một cách chuẩn hóa phù hợp cho mọi miền nghiệp vụ. Ba mục tiêu thường gặp rất khác nhau:

1. **So sánh giá trị số:** `1.0` và `1.00` được coi bằng nhau; có thể dùng `compareTo` trực tiếp.
2. **Tạo dạng biểu diễn duy nhất cho định danh/khóa:** hai giá trị số bằng nhau cần nhận cùng dạng `BigDecimal`; cân nhắc `stripTrailingZeros`.
3. **Lưu theo số chữ số thập phân cố định:** ví dụ số tiền với 2 chữ số phần thập phân; cần `setScale(2, ...)` theo chính sách hợp lệ hoặc làm tròn.

Nhầm lẫn ba mục tiêu này thường gây ra lỗi dữ liệu hoặc logic khó nhận ra.

## 2. Hiểu đúng `scale`, `precision` và tính bằng nhau

`BigDecimal` lưu giá trị dưới dạng:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

Ví dụ:

| Chuỗi khởi tạo | `precision()` | `scale()` | Giá trị số |
| --- | ---: | ---: | ---: |
| `"1.0"` | 2 | 1 | 1 |
| `"1.00"` | 3 | 2 | 1 |
| `"12.30"` | 4 | 2 | 12.3 |
| `"0.0000"` | 1 | 4 | 0 |
| `"1000.00"` | 6 | 2 | 1000 |

**Precision** là số chữ số có nghĩa của cách biểu diễn, còn **scale** xác định vị trí dấu thập phân. `MathContext(6)` giới hạn chữ số có nghĩa của phép tính; nó không phải chính sách “6 chữ số sau dấu phẩy” để lưu dữ liệu. Để yêu cầu scale cố định, dùng `setScale`.

Những phương thức quan trọng:

- `a.compareTo(b) == 0`: bằng nhau về **giá trị số**, bỏ qua khác biệt do scale.
- `a.equals(b)`: bằng nhau cả về **giá trị số lẫn scale**.
- `a.hashCode()`: tương ứng với `equals`; vì vậy những số bằng nhau theo `compareTo` nhưng khác scale thường có hash code khác nhau.
- `a.stripTrailingZeros()`: loại bỏ các số 0 dư ở cuối cách biểu diễn, có thể tạo scale âm.
- `a.setScale(targetScale, roundingMode)`: tạo đối tượng có scale yêu cầu; có thể chỉ thêm số 0 hoặc cần bỏ bớt chữ số.
- `a.toPlainString()`: xuất chuỗi thập phân không dùng ký hiệu số mũ, không phải thao tác chuẩn hóa khóa.

`BigDecimal` bất biến: tất cả phương thức biến đổi trả về đối tượng mới hoặc giá trị mới. Cần sử dụng giá trị trả về; gọi `amount.setScale(2, ...)` mà không gán lại sẽ không thay đổi `amount`.

## 3. Chính sách A — Chỉ cần so sánh giá trị số

Nếu câu hỏi nghiệp vụ là “hai số này có cùng độ lớn không?”, **không cần chuẩn hóa trước khi so sánh**:

```java
BigDecimal supplied = new BigDecimal("10.500");
BigDecimal expected = new BigDecimal("10.5");

boolean sameNumericValue = supplied.compareTo(expected) == 0; // true
boolean greater = supplied.compareTo(expected) > 0;            // false
```

Dùng `compareTo` để so sánh `>`, `<`, `>=`, `<=` hoặc bằng nhau về số học. Không dùng `equals` khi scale không có ý nghĩa nghiệp vụ.

Không được làm tròn/ép scale chỉ để “giúp so sánh” nếu cần so sánh giá trị gốc:

```java
BigDecimal x = new BigDecimal("1.004");
BigDecimal y = new BigDecimal("1.00");

System.out.println(x.compareTo(y) == 0); // false
```

Nếu cả hai được làm tròn về 2 chữ số thập phân với `HALF_EVEN`, chúng đều thành `1.00`. Khi ấy so sánh sau làm tròn là một **quyết định nghiệp vụ khác**, vì đã cố ý loại bỏ sự khác nhau ở phần nghìn. Phải xác định rõ đang so sánh giá trị gốc hay giá trị sau lượng tử hóa.

### Vì sao `HashSet` và `TreeSet` có thể cho kết quả khác?

`HashSet` và `HashMap` sử dụng `equals`/`hashCode` của phần tử hoặc khóa; vì thế `new BigDecimal("1.0")` và `new BigDecimal("1.00")` được coi là hai khóa khác nhau. `TreeSet` với thứ tự tự nhiên sử dụng `compareTo`; vì thế hai giá trị này được coi là cùng vị trí sắp xếp và một trong hai không được thêm vào. Đây là đặc điểm quan trọng của `BigDecimal`: thứ tự tự nhiên **không nhất quán với `equals`**.

Không thay `HashSet` bằng `TreeSet` chỉ để né chuẩn hóa. Hãy định nghĩa trước quy tắc bằng nhau của bộ dữ liệu rồi chọn cách triển khai phù hợp.

## 4. Chính sách B — Chuẩn hóa theo giá trị số để dùng làm khóa

Khi hệ thống muốn `1`, `1.0` và `1.00` là **cùng một khóa**, có thể đưa mọi giá trị về dạng chuẩn bằng `stripTrailingZeros()`:

```java
BigDecimal a = new BigDecimal("1.0").stripTrailingZeros();
BigDecimal b = new BigDecimal("1.00").stripTrailingZeros();

System.out.println(a.equals(b)); // true
```

Hàm chuẩn hóa sau hỗ trợ giá trị `0` nhất quán và từ chối `null` một cách rõ ràng:

```java
import java.math.BigDecimal;
import java.util.Objects;

public static BigDecimal numericKey(BigDecimal input) {
    BigDecimal normalized =
            Objects.requireNonNull(input, "input").stripTrailingZeros();
    return normalized.signum() == 0 ? BigDecimal.ZERO : normalized;
}
```

Đây là **một phương thức minh họa**, đặt trong lớp Java hoặc dùng phương thức cùng tên trong chương trình hoàn chỉnh ở mục 7.

Hai lưu ý khi áp dụng:

**1. `stripTrailingZeros()` có thể tạo scale âm.**

```java
BigDecimal canonical = new BigDecimal("1000.00").stripTrailingZeros();

System.out.println(canonical.toString());      // 1E+3
System.out.println(canonical.toPlainString()); // 1000
System.out.println(canonical.scale());         // -3
```

Scale âm hợp lệ, không biểu thị sai số. Đừng giả định dạng chuẩn hóa sẽ luôn có `scale >= 0`. Nếu cần chữ số thập phân cố định, hãy dùng chính sách C ở mục tiếp theo.

**2. Chuỗi hiển thị và khóa `BigDecimal` không hoàn toàn là cùng một hợp đồng.**

`canonical.toPlainString()` từ ví dụ trên xuất `"1000"`. Tạo lại `new BigDecimal("1000")` sẽ có `scale == 0`; nó *bằng về số học* nhưng không `equals` với giá trị chuẩn có scale `-3`. Vì thế, nếu đọc khóa từ chuỗi hoặc hệ thống khác, **chuẩn hóa lại cả hai phía** khi cần so sánh bằng `equals`/làm khóa hash. Không coi `toPlainString` là phép round-trip bảo toàn scale.

Chính sách `numericKey` phù hợp cho khóa tra cứu, khử trùng lặp và nhóm dữ liệu theo **giá trị số**. Phải áp dụng nhất quán ở cả lúc chèn, lúc tìm và lúc khôi phục dữ liệu từ kho lưu trữ. Không chỉ chuẩn hóa một nhánh dữ liệu.

## 5. Chính sách C — Ép `scale` cố định trước khi lưu

Nhiều mô hình lưu trữ có ràng buộc số chữ số phần thập phân, chẳng hạn một cột SQL `DECIMAL(18,2)` hoặc loại giá trị tiền tệ với hai chữ số thập phân. Lúc này, chính sách có thể là:

- Giá trị hợp lệ phải **biểu diễn chính xác** ở scale đích, nếu không thì từ chối.
- Hoặc giá trị được phép **làm tròn** theo quy tắc nghiệp vụ đã công bố, rồi lưu scale đích.

### 5.1. Chính sách nghiêm ngặt: không cho phép mất thông tin

```java
BigDecimal amount = new BigDecimal("12.3");
BigDecimal storage = amount.setScale(2, RoundingMode.UNNECESSARY);
// 12.30
```

`UNNECESSARY` yêu cầu không cần bỏ chữ số có giá trị. `12.3` trở thành `12.30`, và `12.3400` trở thành `12.34` mà vẫn chính xác. Nhưng `12.345` không thể lưu chính xác ở scale 2 nên ném `ArithmeticException`.

Dùng cách này khi đầu vào bắt buộc tuân thủ hợp đồng, ví dụ giá sản phẩm chỉ nhận đơn vị nhỏ nhất là xu. Nếu dữ liệu đầu vào có độ chi tiết vượt quá quy định, một lỗi xác thực minh bạch tốt hơn việc cắt hoặc tự làm tròn.

### 5.2. Chính sách cho phép làm tròn

```java
BigDecimal raw = new BigDecimal("2.345");
BigDecimal stored = raw.setScale(2, RoundingMode.HALF_EVEN);
// 2.34
```

Với `HALF_EVEN`, điểm giữa được làm tròn về chữ số cuối chẵn:

| Giá trị | `setScale(2, HALF_EVEN)` |
| --- | --- |
| `2.345` | `2.34` |
| `2.355` | `2.36` |
| `12.3` | `12.30` |

Chọn `RoundingMode` theo quy tắc nghiệp vụ, pháp lý hoặc hợp đồng trao đổi dữ liệu: `HALF_EVEN` không phải mặc định đúng cho mọi loại tiền tệ/giao dịch. Chẳng hạn `DOWN` cắt phần dư về phía 0, khác với `FLOOR` đối với số âm. Không được dùng `setScale(2)` thiếu chế độ làm tròn nếu đầu vào có thể cần làm tròn: overload đó có thể ném `ArithmeticException`.

**Nguyên tắc:** phân biệt rõ “kiểm tra dữ liệu có scale hợp lệ” với “cho phép biến đổi giá trị về scale hợp lệ”. Hai đường đi này phải có tên hàm và cách xử lý lỗi tách bạch.

## 6. Đặt quy tắc ở đúng ranh giới hệ thống

### 6.1. Trước khi ghi cơ sở dữ liệu

Giả sử cột `amount DECIMAL(18,2)` có tổng cộng tối đa 18 chữ số thập phân, trong đó 2 chữ số phần thập phân. Nó cho phép tối đa 16 chữ số phần nguyên. Một quy trình lưu an toàn nên:

1. Xác định scale và quy tắc làm tròn của miền dữ liệu.
2. Kiểm tra đầu vào/null, trường hợp vượt miền giá trị và các quy tắc nghiệp vụ khác.
3. Áp dụng `setScale(2, UNNECESSARY)` nếu cần bảo toàn chính xác, hoặc `setScale(2, selectedMode)` nếu được phép làm tròn.
4. Kiểm tra giá trị chuẩn hóa có phù hợp `precision` và phạm vi của cột hay không.
5. Ghi qua JDBC/ORM với kiểu số thập phân phù hợp; đọc lại và kiểm tra nếu hợp đồng đòi tương đương chính xác.

Ví dụ ánh xạ ORM `@Column(precision = 18, scale = 2)` chỉ mô tả cột dự kiến; hành vi làm tròn, ép kiểu hoặc từ chối của cơ sở dữ liệu và trình điều khiển có thể khác nhau. **Không dựa vào database để âm thầm chọn chính sách làm tròn thay cho ứng dụng.** Đặc biệt, scale của đối tượng `BigDecimal` sau khi đọc qua JDBC có thể phản ánh kiểu cột/driver, không nhất thiết giữ nguyên cách biểu diễn trong đầu vào ban đầu.

Với các số đo, kết quả khoa học hoặc tỷ lệ có precision biến đổi, việc áp đặt scale 2 sẽ làm mất dữ liệu. Chọn schema, scale và quy tắc lưu phù hợp từng loại giá trị.

### 6.2. Trước khi tạo khóa `Map`, `Set`, cache hoặc nhóm dữ liệu

Nếu định danh dựa trên giá trị số:

```java
Map<BigDecimal, String> labels = new HashMap<>();
labels.put(numericKey(new BigDecimal("5.0")), "five");

String found = labels.get(numericKey(new BigDecimal("5.00")));
// "five"
```

Mã nhúng này cần `import java.util.Map; import java.util.HashMap;` và phương thức `numericKey` đã định nghĩa. Nên tập trung bước chuẩn hóa ở constructor, factory, value object hoặc lớp truy cập dữ liệu để mọi nơi chèn và truy vấn đều tuân thủ cùng hợp đồng.

Nếu scale có ý nghĩa nghiệp vụ — ví dụ số đo `"1.0"` có độ phân giải khác `"1.00"` — **đừng chuẩn hóa mất scale** khi thiết kế định danh, nhật ký kiểm toán hoặc kiểm tra tính bằng nhau.

### 6.3. Trước khi so sánh đối tượng chứa `BigDecimal`

Một `record`, đối tượng value hoặc DTO tự sinh `equals` có thể kế thừa hành vi `BigDecimal.equals`. Vì vậy:

- Nếu scale là một phần bản sắc dữ liệu, giữ nguyên `BigDecimal` và dùng `equals` bình thường.
- Nếu chỉ giá trị số quyết định tính bằng nhau, chuẩn hóa tại lúc xây dựng đối tượng rồi giữ bất biến; nếu tự định nghĩa `equals`, phải định nghĩa `hashCode` tương ứng.
- Với so sánh một lần theo giá trị, dùng `compareTo` trực tiếp, không cần sửa dữ liệu đang lưu.

Không triển khai `equals` dựa trên `compareTo` nhưng giữ `hashCode` mặc định của `BigDecimal`; điều đó vi phạm hợp đồng các đối tượng bằng nhau phải có cùng hash code.

### 6.4. Khi trao đổi JSON hoặc chuỗi

Nếu hệ thống yêu cầu biểu diễn văn bản **đúng hai chữ số phần thập phân**, hãy dùng `setScale(2, ...)` rồi `toPlainString()` trong bước xuất. Nếu hệ thống yêu cầu **dạng số thập phân chuẩn theo giá trị**, có thể xuất `numericKey(value).toPlainString()` và buộc phía nhận áp dụng lại quy tắc chuẩn hóa.

JSON cho phép biểu diễn số theo nhiều chuỗi chữ số; serializer, parser và ngôn ngữ tiêu thụ có thể xử lý scale khác nhau hoặc chuyển về kiểu dấu chấm động. Nếu phải giữ nguyên số 0 cuối hoặc chuỗi gốc để ký số/kiểm toán, hãy xác định rõ schema và encoding (có thể dùng chuỗi JSON nếu giao thức cho phép) thay vì dựa vào hành vi ngầm của thư viện.

## 7. Ví dụ Java 17+ hoàn chỉnh

Lưu file **`BigDecimalScalePolicyDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class BigDecimalScalePolicyDemo {
    private static final int MONEY_SCALE = 2;

    // Chính sách khóa: cùng giá trị số -> cùng BigDecimal chuẩn hóa.
    static BigDecimal numericKey(BigDecimal input) {
        BigDecimal normalized =
                Objects.requireNonNull(input, "input").stripTrailingZeros();
        return normalized.signum() == 0 ? BigDecimal.ZERO : normalized;
    }

    // Chính sách lưu nghiêm ngặt: từ chối mọi giá trị cần làm tròn.
    static BigDecimal strictAmount(BigDecimal input) {
        return Objects.requireNonNull(input, "input")
                .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    // Chính sách lưu cho phép làm tròn (chỉ là ví dụ nghiệp vụ).
    static BigDecimal roundedAmount(BigDecimal input) {
        return Objects.requireNonNull(input, "input")
                .setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");

        System.out.println("numeric equals: " + (a.compareTo(b) == 0));
        System.out.println("object equals: " + a.equals(b));
        System.out.println("raw set size: "
                + new HashSet<>(List.of(a, b)).size());
        System.out.println("canonical set size: "
                + new HashSet<>(List.of(numericKey(a), numericKey(b))).size());

        BigDecimal thousand = numericKey(new BigDecimal("1000.00"));
        System.out.println("canonical text: " + thousand.toPlainString());
        System.out.println("canonical scale: " + thousand.scale());
        System.out.println("zero scale: "
                + numericKey(new BigDecimal("0.0000")).scale());

        System.out.println("strict amount: "
                + strictAmount(new BigDecimal("12.3")).toPlainString());

        try {
            strictAmount(new BigDecimal("12.345"));
        } catch (ArithmeticException ex) {
            System.out.println("reject excessive decimals");
        }

        System.out.println("round 2.345: "
                + roundedAmount(new BigDecimal("2.345")).toPlainString());
        System.out.println("round 2.355: "
                + roundedAmount(new BigDecimal("2.355")).toPlainString());
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac BigDecimalScalePolicyDemo.java
java BigDecimalScalePolicyDemo
```

Đầu ra:

```text
numeric equals: true
object equals: false
raw set size: 2
canonical set size: 1
canonical text: 1000
canonical scale: -3
zero scale: 0
strict amount: 12.30
reject excessive decimals
round 2.345: 2.34
round 2.355: 2.36
```

Trong chương trình, `numericKey` chuẩn hóa biểu diễn để có hành vi `equals`/hash ổn định theo giá trị số, trong khi `strictAmount` và `roundedAmount` thực thi **hai chính sách lưu khác nhau**. `toPlainString` quyết định biểu diễn văn bản; `setScale` quyết định scale, và có thể quyết định việc làm tròn. Ba trách nhiệm này không được đánh đồng.

## 8. Những trường hợp biên phải kiểm thử

**Giá trị 0 và dấu âm:** `new BigDecimal("0.0000").stripTrailingZeros()` thành giá trị `BigDecimal.ZERO` với scale 0. Nếu hệ thống phải giữ đầu vào nguyên văn (ví dụ dấu `-0.00` trong một thông điệp ban đầu), cần lưu thêm văn bản gốc riêng; chuẩn hóa số học không bảo tồn tất cả chi tiết văn bản.

**Scale âm:** `1000.00` sau `stripTrailingZeros` có thể thành `1E+3`, scale `-3`. Đây là số hợp lệ; chấp nhận khi dùng làm khóa chuẩn, hoặc dùng chính sách scale cố định nếu kho lưu không hỗ trợ ý nghĩa biểu diễn đó.

**Scale cao nhưng chỉ có số 0:** `new BigDecimal("1.2300").setScale(2, UNNECESSARY)` thành `1.23` mà không mất giá trị. Đếm số ký tự phần thập phân của văn bản nguồn để từ chối tất cả trường hợp quá hai chữ số sẽ khác chính sách “không mất thông tin” này; hãy chọn rõ một trong hai.

**Giá trị sát ngưỡng làm tròn:** kiểm thử các giá trị như `2.345`, `2.355`, số âm và các giá trị rất nhỏ. Không dùng cùng một mode theo thói quen cho mọi miền. Đảm bảo so sánh trước hoặc sau làm tròn đúng theo hợp đồng.

**Đọc/ghi nhiều hệ thống:** canonicalize tại cùng ranh giới trên cả đường nhập và đường truy vấn; xem xét trường hợp SQL `DECIMAL`, JSON, serializer, cache và khóa phân tán có chính sách khác nhau.

**Bộ sưu tập:** kiểm thử chèn/truy vấn `HashMap`, `HashSet`, `TreeSet` với `1`, `1.0`, `1.00` để kiểm chứng quy tắc bằng nhau mong muốn.

**Input `double`:** tránh `new BigDecimal(0.1)` khi mục tiêu là giá trị thập phân chính xác `0.1`; chuyển đổi từ `double` có thể đã mang sai số trước khi bất kỳ chuẩn hóa scale nào diễn ra.

**Null/ngoại lệ:** quyết định liệu `null` có hợp lệ hay không. Các hàm ví dụ dùng `Objects.requireNonNull` để báo lỗi rõ ràng. Với `RoundingMode.UNNECESSARY`, `ArithmeticException` là tín hiệu đầu vào không thỏa hợp đồng; xử lý thành lỗi xác thực, không tự thay bằng 0 hoặc âm thầm làm tròn.

## 9. Bảng quyết định chính sách

| Nhu cầu | Cách làm | Lưu ý |
| --- | --- | --- |
| So sánh giá trị số, không quan tâm scale | `a.compareTo(b) == 0` | Không cần biến đổi đầu vào |
| So sánh cả giá trị và scale | `a.equals(b)` | `1.0` khác `1.00` |
| Khóa hash đồng nhất theo giá trị số | `numericKey(a)` trước khi chèn/tìm | Chuẩn hóa mọi phía; có thể tạo scale âm |
| Lưu đúng scale 2, không mất dữ liệu | `a.setScale(2, UNNECESSARY)` | Ném ngoại lệ nếu cần làm tròn |
| Lưu đúng scale 2, cho phép làm tròn | `a.setScale(2, selectedMode)` | Mode phải theo hợp đồng nghiệp vụ |
| Chuỗi số học chuẩn không có ký hiệu mũ | `numericKey(a).toPlainString()` | Khi parse lại, chuẩn hóa nếu dùng làm khóa |
| Lưu nguyên độ phân giải/biểu diễn đầu vào | Giữ `BigDecimal` và/hoặc chuỗi gốc | Không loại 0 cuối vô điều kiện |

## 10. Bài tập tự kiểm tra

1. Vì sao `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false`, nhưng `compareTo` cho `0`? Điều gì xảy ra khi đưa hai giá trị vào `HashSet`?
2. Viết hàm tạo khóa của một bộ đếm theo giá trị số để `1000`, `1000.0`, `1E+3` trùng khóa. Kiểm tra scale của giá trị chuẩn.
3. Thiết kế API lưu số tiền `DECIMAL(18,2)` có hai đường đi rõ ràng: (a) chỉ cho phép giá trị chính xác; (b) chấp nhận làm tròn theo quy tắc đã định. Đầu vào `12.345` có kết quả gì với mỗi đường?
4. Hệ thống đo lường cần phân biệt độ phân giải giữa `1.0` và `1.00`. Có nên gọi `stripTrailingZeros` ngay khi đọc dữ liệu không? Vì sao?
5. Tại sao `numericKey(x).toPlainString()` không tự bảo đảm rằng `new BigDecimal(resultString).equals(numericKey(x))` cho mọi số nguyên có nhiều số 0 ở cuối?

Gợi ý: (1) `equals` so cả scale; `HashSet` giữ 2 phần tử nếu chưa chuẩn hóa. (2) áp dụng `stripTrailingZeros` nhất quán cho mọi khóa; số nguyên 1000 có thể có scale `-3`. (3) đường nghiêm ngặt từ chối; đường làm tròn chọn kết quả theo mode, ví dụ `HALF_EVEN` thành `12.34`. (4) không, vì scale chứa ý nghĩa đo lường. (5) `toPlainString` không ghi lại scale âm, nên cần chuẩn hóa lại giá trị sau parse nếu dùng `BigDecimal.equals`.

**Kết luận thực hành:** đặt tên và ghi rõ chính sách tại ranh giới. So sánh số học dùng `compareTo`; khóa đồng nhất số học có thể dùng `stripTrailingZeros`; lưu có scale cố định dùng `setScale` với quy tắc mất dữ liệu minh bạch. Chỉ chuẩn hóa khi biết chắc scale không chứa thông tin có ý nghĩa trong miền đang xử lý.

Tài liệu đối chiếu: [Java SE 17 — `BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), đặc biệt `compareTo`, `equals`, `hashCode`, `stripTrailingZeros`, `setScale` và `toPlainString`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-008">Kiểm tra số 0 bằng signum hoặc compareTo</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra số 0 bằng signum() hoặc compareTo() trong Java BigDecimal

## 1. Mục tiêu: kiểm tra giá trị số, không nhầm với cách biểu diễn

Khi làm việc với `java.math.BigDecimal`, ta thường cần xác định một số có bằng 0 hay không: số dư tài khoản có hết không, kết quả phép trừ đã về 0 chưa, một lượng thay đổi có âm/dương/không đổi không, hoặc có nên thực hiện phép chia cho một mẫu số hay không.

Hai cách diễn đạt trực tiếp, chính xác là:

~~~java
value.signum() == 0
value.compareTo(BigDecimal.ZERO) == 0
~~~

Cả hai đều kiểm tra **giá trị số bằng 0**, bất kể đối tượng `BigDecimal` đang có scale nào. Đây là điều khác với `value.equals(BigDecimal.ZERO)`: `equals` còn kiểm tra **scale**, vì vậy có thể trả về `false` dù giá trị số đúng bằng 0.

Bài học tập trung vào cách chọn giữa `signum()` và `compareTo(BigDecimal.ZERO)`, phân biệt `equals`, xử lý số 0 có nhiều dạng thập phân, và các trường hợp thực tế có thể gây lỗi.

## 2. Cách 1: BigDecimal.signum()

`public int signum()` trả về **dấu của giá trị số**:

| `value.signum()` | Giá trị của `value` | Có bằng 0? |
| ---: | --- | --- |
| `-1` | Nhỏ hơn 0 | Không |
| `0` | Bằng 0 | **Có** |
| `1` | Lớn hơn 0 | Không |

Vì thế:

~~~java
boolean isZero = value.signum() == 0;
boolean isNegative = value.signum() < 0;
boolean isPositive = value.signum() > 0;
~~~

Ở đây, `value` là một `BigDecimal` đã được khai báo và không phải `null`. Không có phép làm tròn, đổi sang `double`, hay thay đổi scale. `signum()` trả kết quả đúng `-1`, `0`, hoặc `1`.

Các ví dụ:

| Giá trị được tạo | `signum()` | Giải thích |
| --- | ---: | --- |
| `new BigDecimal("0")` | `0` | Số 0 |
| `new BigDecimal("0.000")` | `0` | Nhiều chữ số 0 vẫn là 0 |
| `new BigDecimal("-0.00")` | `0` | Không có giá trị số âm 0 riêng biệt trong BigDecimal |
| `new BigDecimal("0.0000000001")` | `1` | Rất nhỏ nhưng vẫn lớn hơn 0 |
| `new BigDecimal("-0.0000000001")` | `-1` | Rất nhỏ nhưng vẫn âm |
| `new BigDecimal("100.0")` | `1` | Dương |

Ưu điểm của `signum()`: diễn đạt ngắn gọn câu hỏi “giá trị đang âm, dương hay bằng 0?” và không cần tạo hay truyền một đối tượng mốc để so sánh.

## 3. Cách 2: BigDecimal.compareTo(BigDecimal.ZERO)

`compareTo(BigDecimal other)` so sánh **giá trị số** của hai `BigDecimal`. Khi `other` là `BigDecimal.ZERO`, ý nghĩa của kết quả là:

~~~java
boolean isZero = value.compareTo(BigDecimal.ZERO) == 0;
boolean isNegative = value.compareTo(BigDecimal.ZERO) < 0;
boolean isPositive = value.compareTo(BigDecimal.ZERO) > 0;
~~~

`BigDecimal.ZERO` là hằng số số 0 có sẵn trong thư viện, vì vậy không cần lặp lại `new BigDecimal("0")` mỗi lần kiểm tra. Nếu kết quả `compareTo` là âm, giá trị đứng trước nhỏ hơn số 0; nếu là 0, hai số bằng nhau về mặt số học; nếu là dương, giá trị đứng trước lớn hơn số 0.

Với mọi `BigDecimal value` không null:

~~~java
(value.signum() == 0)
        == (value.compareTo(BigDecimal.ZERO) == 0)
~~~

Hai biểu thức có cùng kết quả boolean. Tương tự, `value.signum() < 0` tương đương `value.compareTo(BigDecimal.ZERO) < 0`, và `> 0` cũng tương đương.

**Đừng so kết quả `compareTo` bằng `1` hoặc `-1` để biểu thị lớn hơn/nhỏ hơn.** Theo hợp đồng `Comparable`, chỉ cần kiểm tra kết quả **dương** hoặc **âm**. Riêng `signum()` có hợp đồng trả đúng ba giá trị `-1`, `0`, `1`, nhưng dùng `< 0` và `> 0` vẫn dễ đọc và nhất quán.

## 4. Vì sao equals(BigDecimal.ZERO) có thể trả false?

`BigDecimal` lưu số thông qua một giá trị nguyên không có scale (`unscaledValue`) và một `scale`. Giá trị toán học tương ứng là `unscaledValue × 10^(-scale)`.

Ví dụ:

| Chuỗi tạo BigDecimal | Giá trị số | `scale()` | `signum()` | `compareTo(ZERO) == 0` | `equals(ZERO)` |
| --- | ---: | ---: | ---: | --- | --- |
| `"0"` | 0 | 0 | 0 | `true` | `true` |
| `"0.0"` | 0 | 1 | 0 | `true` | `false` |
| `"0.00"` | 0 | 2 | 0 | `true` | `false` |
| `"-0.00"` | 0 | 2 | 0 | `true` | `false` |
| `"0E+3"` | 0 | -3 | 0 | `true` | `false` |

Các số trên **đều bằng 0 về mặt số học**. Nhưng `BigDecimal.ZERO` có scale 0; với `equals`, số 0 có scale 1, 2 hoặc -3 khác biểu diễn của số 0 có scale 0.

Do đó, đoạn sau dễ tạo lỗi nghiệp vụ:

~~~java
BigDecimal amount = new BigDecimal("0.00");

System.out.println(amount.equals(BigDecimal.ZERO));    // false
System.out.println(amount.signum() == 0);               // true
System.out.println(amount.compareTo(BigDecimal.ZERO) == 0); // true
~~~

Khi yêu cầu là “**số tiền này có bằng 0 hay không?**”, sử dụng `signum()` hoặc `compareTo`. Chỉ dùng `equals` nếu yêu cầu thực sự bao gồm cả sự giống nhau về **scale**, chẳng hạn kiểm tra một dạng biểu diễn thập phân cố định.

Cũng không dùng `value == BigDecimal.ZERO`: toán tử `==` giữa hai tham chiếu đối tượng kiểm tra **có phải cùng đối tượng** chứ không kiểm tra giá trị số.

## 5. Ví dụ Java 17+ đầy đủ, chạy được

Lưu chương trình sau thành `BigDecimalZeroDemo.java`.

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalZeroDemo {
    private static void describe(String input) {
        BigDecimal value = new BigDecimal(input);

        int comparisonSign =
                Integer.signum(value.compareTo(BigDecimal.ZERO));

        System.out.printf(
                "%s: signum=%d, compareToZeroSign=%d, equalsZero=%s%n",
                input,
                value.signum(),
                comparisonSign,
                value.equals(BigDecimal.ZERO));
    }

    public static void main(String[] args) {
        for (String input : new String[]{
                "0", "0.00", "-0.00", "0.0001", "-0.0001"}) {
            describe(input);
        }

        BigDecimal tiny = new BigDecimal("0.004");
        BigDecimal rounded = tiny.setScale(2, RoundingMode.DOWN);

        System.out.println("tiny is zero = " + (tiny.signum() == 0));
        System.out.println("rounded = " + rounded.toPlainString());
        System.out.println("rounded is zero = " + (rounded.signum() == 0));

        BigDecimal sum = new BigDecimal("0.1")
                .add(new BigDecimal("-0.10"));

        System.out.println("sum = " + sum.toPlainString());
        System.out.println("sum compared to zero = "
                + sum.compareTo(BigDecimal.ZERO));
    }
}
~~~

Mở terminal trong thư mục chứa file, dùng JDK 17 trở lên:

~~~bash
javac BigDecimalZeroDemo.java
java BigDecimalZeroDemo
~~~

Kết quả chuẩn:

~~~text
0: signum=0, compareToZeroSign=0, equalsZero=true
0.00: signum=0, compareToZeroSign=0, equalsZero=false
-0.00: signum=0, compareToZeroSign=0, equalsZero=false
0.0001: signum=1, compareToZeroSign=1, equalsZero=false
-0.0001: signum=-1, compareToZeroSign=-1, equalsZero=false
tiny is zero = false
rounded = 0.00
rounded is zero = true
sum = 0.00
sum compared to zero = 0
~~~

Các điểm cần chú ý:

1. `Integer.signum(value.compareTo(BigDecimal.ZERO))` biến kết quả so sánh thành `-1`, `0`, hoặc `1` để tiện in minh họa; khi chỉ cần kiểm tra bằng 0, hãy viết trực tiếp `compareTo(...) == 0`.
2. `"0.00"` và `"-0.00"` đều có `signum() == 0`, nhưng `equals(BigDecimal.ZERO)` là `false` vì scale bằng 2 thay vì 0.
3. `0.0001` không phải là 0 dù rất gần 0. Kiểm tra đúng giá trị số không sử dụng sai số ngầm định.
4. Khi làm tròn `0.004` bằng `RoundingMode.DOWN` về hai chữ số sau dấu thập phân, kết quả là `0.00`. **Giá trị gốc khác 0; giá trị sau làm tròn bằng 0.**
5. Phép cộng `0.1 + (-0.10)` cho số 0 có scale 2 (`0.00`). `compareTo(BigDecimal.ZERO)` nhận diện giá trị bằng 0 chính xác, không phụ thuộc scale.

## 6. Kiểm tra số 0 trước và sau phép tính

Điểm dễ bị bỏ sót: thời điểm kiểm tra có thể thay đổi ý nghĩa.

### Kiểm tra mẫu số khác 0 trước phép chia

Nếu mẫu số là `new BigDecimal("0.000")`, `equals(BigDecimal.ZERO)` là false dù phép chia cho mẫu số đó vẫn là chia cho số 0. Kiểm tra theo giá trị số:

~~~java
static BigDecimal safeDivide(BigDecimal numerator,
                             BigDecimal denominator) {
    if (denominator.signum() == 0) {
        throw new IllegalArgumentException("Mẫu số phải khác 0");
    }
    return numerator.divide(
            denominator, 6, java.math.RoundingMode.HALF_EVEN);
}
~~~

Đây là **mảnh phương thức** đặt bên trong một lớp Java có import `BigDecimal`; giả định hai đối số không null. Scale 6 và `HALF_EVEN` chỉ minh họa một chính sách tính toán, phải thay theo yêu cầu cụ thể. Dùng `compareTo(BigDecimal.ZERO) == 0` trong điều kiện `if` cũng cho cùng hành vi.

### Kiểm tra kết quả phép trừ

~~~java
BigDecimal debit = new BigDecimal("10.00");
BigDecimal credit = new BigDecimal("10.0");
BigDecimal balance = debit.subtract(credit);

boolean fullyOffset = balance.signum() == 0; // true
~~~

Hai đầu vào có scale khác nhau nhưng kết quả bằng 0 về số học. Không cần `setScale(0)` hay `stripTrailingZeros()` để kiểm tra.

### Kiểm tra giá trị đã làm tròn

~~~java
BigDecimal original = new BigDecimal("-0.004");
BigDecimal displayed = original.setScale(2, RoundingMode.DOWN);

boolean rawZero = original.signum() == 0;        // false
boolean displayedZero = displayed.signum() == 0; // true
~~~

Cần phân biệt hai câu hỏi:

- “**Dữ liệu thật sự bằng 0?**” → kiểm tra `original`.
- “**Kết quả sau quy tắc làm tròn có bằng 0?**” → kiểm tra `displayed`.

Đừng làm tròn tùy ý để biến số nhỏ thành 0 trước khi kiểm tra, vì điều đó có thể làm mất sự khác biệt cần thiết cho nghiệp vụ.

## 7. Số 0 âm: BigDecimal không giữ dấu âm riêng cho zero

Nhiều người từng gặp `-0.0` với `double` và `float` theo chuẩn IEEE 754. Một giá trị `double` có thể mang bit dấu âm dù bằng zero khi so sánh số học. `BigDecimal` vận hành khác: số 0 có giá trị nguyên không có scale bằng 0, và `signum()` trả **0** cho cả chuỗi nhập `"0.00"` và `"-0.00"`.

~~~java
BigDecimal minusZero = new BigDecimal("-0.00");

System.out.println(minusZero.signum());       // 0
System.out.println(minusZero.toPlainString()); // 0.00
~~~

Không có cách xác định người dùng **đã gõ dấu trừ** chỉ từ dấu của đối tượng `BigDecimal` sau khi parse. Nếu việc lưu dấu âm của chuỗi gốc là một yêu cầu (ví dụ biểu mẫu cần bảo toàn chính xác nội dung nhập), phải lưu chuỗi gốc hoặc thông tin dấu như một trường riêng **trước khi chuyển đổi**.

`BigDecimal` cũng không có `NaN` hoặc vô cực như `double`. Không cần thêm trường hợp `NaN` vào quy tắc `signum() == 0`; nếu dữ liệu không phải số thập phân hợp lệ, quá trình khởi tạo từ chuỗi sẽ báo lỗi thay vì tạo `NaN`.

## 8. Giá trị cực nhỏ và “gần bằng 0” là hai yêu cầu khác nhau

`BigDecimal` có thể biểu diễn số thập phân rất nhỏ với độ chính xác cao. Ví dụ:

~~~java
BigDecimal x = new BigDecimal("0.000000000000000000000000000001");
System.out.println(x.signum() == 0); // false
~~~

Số này khác 0, nên cả hai phương pháp kiểm tra chính xác đều trả false:

~~~java
x.signum() == 0                        // false
x.compareTo(BigDecimal.ZERO) == 0      // false
~~~

Nếu bài toán thật sự yêu cầu chấp nhận **sai số cho phép** (`tolerance`), cần đặc tả một khoảng rõ ràng, chẳng hạn `-0.001 <= x <= 0.001`. Khi `tolerance` đã được xác minh **không âm**:

~~~java
BigDecimal tolerance = new BigDecimal("0.001");
BigDecimal measurement = new BigDecimal("-0.0005");

boolean nearZero =
        measurement.abs().compareTo(tolerance) <= 0;
// true: trị tuyệt đối của -0.0005 không vượt 0.001
~~~

Đây là phép kiểm tra **gần 0**, không phải **bằng 0**. Nó phụ thuộc vào đơn vị đo, độ phân giải cảm biến, quy tắc thống kê hoặc hợp đồng nghiệp vụ. Không mặc định áp dụng một epsilon tùy tiện trong các phép tính BigDecimal, đặc biệt khi xử lý tiền hoặc số dư.

Nếu `tolerance = 0`, biểu thức `abs().compareTo(tolerance) <= 0` tương đương kiểm tra bằng 0. Nếu `tolerance` âm, khoảng được định nghĩa không hợp lệ và phải từ chối hoặc xử lý theo quy tắc ứng dụng.

## 9. Chọn signum hay compareTo?

Cả hai đúng khi kiểm tra BigDecimal bằng 0, nhưng mức độ diễn đạt hơi khác:

| Mục đích | Cách viết | Gợi ý |
| --- | --- | --- |
| Chỉ kiểm tra bằng 0 | `x.signum() == 0` | Ngắn, thể hiện trực tiếp phép kiểm tra dấu |
| Kiểm tra dương, âm, bằng 0 | `x.signum() > 0`, `< 0`, `== 0` | Phù hợp khi phân nhánh theo dấu |
| Kiểm tra bằng 0 trong các biểu thức so sánh | `x.compareTo(BigDecimal.ZERO) == 0` | Đồng nhất với các so sánh ngưỡng `compareTo` khác |
| So với một ngưỡng khác 0 | `x.compareTo(threshold) >= 0` | Đây là điểm mạnh tổng quát của `compareTo` |
| So sánh giá trị và scale của đối tượng | `x.equals(other)` | Dùng khi biểu diễn là một phần của điều kiện |

Một cách viết phân nhánh dễ hiểu:

~~~java
int sign = amount.signum();

if (sign < 0) {
    System.out.println("Âm");
} else if (sign == 0) {
    System.out.println("Bằng 0");
} else {
    System.out.println("Dương");
}
~~~

Đây là mảnh code giả định `amount` đã được khởi tạo với `BigDecimal` không null. Nó tránh gọi nhiều lần khi ta cần dùng cùng kết quả dấu.

Về hiệu năng, không nên giả định một lựa chọn luôn nhanh hơn đáng kể trong mọi phiên bản JDK và mọi dạng số. Hãy chọn cách diễn đạt phản ánh chính xác ý định và chỉ tối ưu dựa trên phép đo phù hợp khi cần.

## 10. Null, đối tượng bất biến và những lỗi dễ mắc

**1. Đối tượng null.** `null` không phải giá trị số 0. Nếu `value == null`, cả `value.signum()` và `value.compareTo(BigDecimal.ZERO)` đều gây `NullPointerException`. Hãy kiểm tra đầu vào hoặc sử dụng `Objects.requireNonNull(value)` theo hợp đồng API. Không mặc định biến null thành zero khi chưa được quy định.

**2. Dùng equals để kiểm tra số 0.** `new BigDecimal("0.00").equals(BigDecimal.ZERO)` là false do scale khác. Chuyển sang `signum() == 0` hoặc `compareTo(ZERO) == 0` khi so giá trị số.

**3. Dùng toán tử == với BigDecimal.** `value == BigDecimal.ZERO` là so tham chiếu đối tượng, không so giá trị. Một đối tượng `BigDecimal` mới biểu diễn số 0 sẽ không được nhận diện đáng tin theo cách này.

**4. Cắt xuống int rồi kiểm tra.** `new BigDecimal("0.5").intValue() == 0` là true nhưng giá trị gốc không phải 0. `intValue()` có thể bỏ phần thập phân hoặc gây mất thông tin; không dùng để kiểm tra zero.

**5. Chuyển sang double.** Không cần `value.doubleValue() == 0.0`. Những số `BigDecimal` có độ lớn cực nhỏ có thể **underflow thành 0.0** khi chuyển sang `double`, dù giá trị gốc khác 0. Kiểm tra trực tiếp bằng `signum`/`compareTo`.

**6. Làm tròn trước khi kiểm tra mà không chủ đích.** `0.004` làm tròn về scale 2 với `DOWN` thành `0.00`; phải xác định yêu cầu nói về số gốc hay số sau làm tròn.

**7. Tưởng số 0 âm có `signum() == -1`.** `BigDecimal("-0.00")` có `signum() == 0`. Không thể suy ra dấu trừ từ dữ liệu đã parse.

**8. Chuẩn hóa để giải quyết một phép kiểm tra đơn giản.** `value.stripTrailingZeros().equals(BigDecimal.ZERO)` có thể nhận diện số 0, nhưng tạo ra bước xử lý biểu diễn không cần thiết. Khi chỉ hỏi giá trị bằng 0, dùng `signum() == 0` cho rõ nghĩa.

**9. Nhầm điều kiện bằng 0 với “số đủ nhỏ”.** `0.0001` vẫn khác 0. Nếu cần ngưỡng gần 0, đưa ngưỡng thành dữ liệu nghiệp vụ và so `abs()` với ngưỡng rõ ràng.

## 11. Bài luyện tập có đáp án

Với từng giá trị dưới đây, dự đoán `signum()`, `compareTo(BigDecimal.ZERO) == 0` và `equals(BigDecimal.ZERO)`:

| Giá trị | signum | compareTo zero == 0 | equals ZERO |
| --- | ---: | --- | --- |
| `new BigDecimal("0")` | 0 | true | true |
| `new BigDecimal("0.000")` | 0 | true | false |
| `new BigDecimal("-0.000")` | 0 | true | false |
| `new BigDecimal("0.00000001")` | 1 | false | false |
| `new BigDecimal("-0.00000001")` | -1 | false | false |
| `new BigDecimal("10").subtract(new BigDecimal("10.00"))` | 0 | true | false |

Bài tập ứng dụng:

1. Viết `boolean isZero(BigDecimal amount)` trả true cho `0`, `0.000`, `-0.00`, nhưng false cho `0.000001`. Nêu rõ hành vi khi `amount == null`.
2. Viết điều kiện chỉ cho phép chia khi `denominator.signum() != 0`. Thử với mẫu số `"0.000"` và `"-0.00"`.
3. Tính `new BigDecimal("0.009").setScale(2, RoundingMode.DOWN)`. Giá trị ban đầu có bằng 0 không? Giá trị kết quả có bằng 0 không?
4. Viết kiểm tra “gần 0” với `tolerance = 0.01`, phân biệt rõ với kiểm tra bằng 0 chính xác.

Một cách triển khai bài 1 khi null không hợp lệ:

~~~java
static boolean isZero(BigDecimal amount) {
    return java.util.Objects.requireNonNull(amount, "amount").signum() == 0;
}
~~~

Đây là **mảnh phương thức** cần đặt trong một lớp Java, có import `java.math.BigDecimal`. Với bài 3, `0.009` sử dụng `DOWN` ở scale 2 trở thành `0.00`, nên giá trị gốc không bằng 0, còn giá trị sau làm tròn bằng 0.

**Ghi nhớ:** Với `BigDecimal`, `value.signum() == 0` và `value.compareTo(BigDecimal.ZERO) == 0` đều xác định **bằng 0 theo giá trị số**. `equals(BigDecimal.ZERO)` kiểm tra thêm scale và có thể sai mục đích. Luôn xác định giá trị cần so sánh là số gốc hay số sau biến đổi/làm tròn, và phân biệt rõ bằng 0 chính xác với gần 0 theo ngưỡng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-009">So sánh null và lựa chọn hợp đồng API</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh `null` và lựa chọn hợp đồng API khi làm việc với `BigDecimal`

`BigDecimal` là kiểu tham chiếu, vì vậy một biến `BigDecimal` có thể là `null`. Khi viết API so sánh, cần trả lời hai câu hỏi độc lập: **hai số được so sánh theo giá trị số hay theo biểu diễn (bao gồm scale)?**, và **thiếu một hoặc hai giá trị phải được xử lý ra sao?**. Chính sách thiếu giá trị ảnh hưởng đến quy tắc nghiệp vụ, thao tác sắp xếp, tập hợp có thứ tự và kết quả trả về của API.

Bài này phân biệt ba hợp đồng: (1) từ chối `null`, (2) sắp xếp `null` theo một thứ tự rõ ràng, (3) trả kết quả “chưa xác định” khi thiếu toán hạng. Mục tiêu là chọn một hợp đồng phù hợp và thực thi nhất quán, không đánh đồng `null` với 0 hoặc với kết quả so sánh bằng nhau.

## 1. `null` không phải là số 0

```java
BigDecimal zero = BigDecimal.ZERO; // Có giá trị, chính xác bằng 0.
BigDecimal missing = null;         // Không có đối tượng BigDecimal.
```

Trong báo cáo tài chính, số dư `0` nghĩa là số dư đã được xác định và bằng 0; `null` có thể mang nghĩa chưa cung cấp, chưa tính hoặc không áp dụng. Tự thay `null` bằng `BigDecimal.ZERO` là một **quyết định nghiệp vụ**, không phải kỹ thuật sửa lỗi. Nó có thể làm sai so sánh ngưỡng hoặc biến “chưa biết” thành “bằng 0”.

Trước khi triển khai, hãy xác định nguồn dữ liệu có cho phép `null` và vì sao. Nếu một đối số luôn bắt buộc thì API nên từ chối thiếu dữ liệu. Nếu dữ liệu có thể thiếu, quyết định rõ phải xếp dòng thiếu ở đâu trong danh sách hay phải trả về trạng thái không thể kết luận.

## 2. Hành vi gốc của `BigDecimal` khi gặp `null`

`BigDecimal.compareTo(BigDecimal)` so sánh theo **giá trị số** và trả về một số âm, 0 hoặc dương. So sánh với `null` không được hỗ trợ:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

a.compareTo(b);    // 0: cùng giá trị số.
a.equals(b);       // false: scale 1 khác scale 2.
a.equals(null);    // false.
a.compareTo(null); // NullPointerException.
```

Nếu chính `a` là `null`, gọi `a.compareTo(b)` hoặc `a.equals(b)` đều ném `NullPointerException` vì không có đối tượng nhận lời gọi. Các phương thức `a.min(null)` và `a.max(null)` cũng không phải là lựa chọn null-safe.

`Objects.equals(left, right)` khác `compareTo`:

- `Objects.equals(null, null)` trả `true`.
- `Objects.equals(null, BigDecimal.ZERO)` trả `false`.
- `Objects.equals(new BigDecimal("1.0"), new BigDecimal("1.00"))` trả `false`, vì vẫn dùng `BigDecimal.equals`.
- `Objects.equals` chỉ giải quyết **kiểm tra bằng nhau theo `equals`**, không thay `BigDecimal.compareTo` hay tạo thứ tự số học.

`Objects.compare(left, right, comparator)` cũng không tự trở thành một comparator chấp nhận null. Nó trả 0 nếu hai tham chiếu là cùng một đối tượng (kể cả cả hai cùng null); nếu khác tham chiếu, nó gọi comparator được cung cấp. Với comparator không cho phép null, trường hợp chỉ một bên null vẫn ném ngoại lệ.

### `compareTo` và `equals`: hai quan hệ cần phân biệt

| Cặp `BigDecimal` | `compareTo == 0` | `equals` |
| --- | --- | --- |
| `"1.0"` / `"1.00"` | `true` | `false` |
| `"0"` / `"0.00"` | `true` | `false` |
| `"1.0"` / `"2.0"` | `false` | `false` |
| `"1.00"` / `"1.00"` | `true` | `true` |

`equals` và `hashCode` của `BigDecimal` xét cả giá trị và **scale**. Nếu cần kiểm tra “cùng số tiền” độc lập scale, `compareTo == 0` thường hợp lý. Nếu biểu diễn (`1.0` so với `1.00`) là một phần của dữ liệu, `equals` là lựa chọn phù hợp hơn.

## 3. Hợp đồng A: `null` là đầu vào không hợp lệ

Với ngưỡng bắt buộc, tổng tiền đã duyệt hoặc số tiền thanh toán đã ghi nhận, thường nên quy định **hai toán hạng đều khác null**. Thất bại tại điểm nhận tham số giúp lỗi được phát hiện rõ:

```java
import java.math.BigDecimal;
import java.util.Objects;

static int compareRequired(BigDecimal left, BigDecimal right) {
    Objects.requireNonNull(left, "left must not be null");
    Objects.requireNonNull(right, "right must not be null");
    return Integer.signum(left.compareTo(right));
}
```

Đây là một phương thức minh họa; cần đặt trong class phù hợp. `Objects.requireNonNull` ném `NullPointerException` với thông báo chỉ rõ đối số vi phạm. `Integer.signum(...)` chuẩn hóa kết quả thành **-1, 0 hoặc 1**; nếu không chuẩn hóa, `compareTo` chỉ cam kết âm/0/dương, không nhất thiết là -1/0/1.

Có thể mô tả hợp đồng bằng Javadoc:

```java
/**
 * So sánh hai giá trị thập phân bắt buộc có dữ liệu.
 * @param left không được null
 * @param right không được null
 * @return -1 nếu left nhỏ hơn right, 0 nếu bằng nhau về số học,
 *         1 nếu left lớn hơn right
 * @throws NullPointerException nếu một trong hai đối số là null
 */
```

Mảnh Javadoc này chỉ minh họa nội dung tài liệu của phương thức phía trên. API cần xác nhận rằng **0 nghĩa là hai giá trị đã được so sánh và bằng nhau**, không có nghĩa là dữ liệu thiếu.

Khi thực thi các điều kiện so sánh, nên viết `left.compareTo(right) > 0` thay vì `left.compareTo(right) == 1`, vì hợp đồng của `Comparable` cam kết **dấu** kết quả.

**Không** xử lý `NullPointerException` bằng cách tự động trả 0: như vậy API biến một lỗi thành kết luận “bằng nhau”, dẫn đến các nhánh logic nghiệp vụ sai.

## 4. Hợp đồng B: `null` được xếp ở đầu hoặc cuối

Nếu một danh sách vẫn phải sắp xếp khi có ô số tiền thiếu, Java cung cấp `Comparator.nullsFirst` và `Comparator.nullsLast`. Chúng tạo thứ tự xác định cho `null` và các giá trị không null:

```java
import java.math.BigDecimal;
import java.util.Comparator;

Comparator<BigDecimal> nullsFirst =
        Comparator.nullsFirst(Comparator.naturalOrder());

Comparator<BigDecimal> nullsLast =
        Comparator.nullsLast(Comparator.naturalOrder());
```

Trong `nullsFirst`, `null` đứng trước mọi giá trị không null; trong `nullsLast`, `null` đứng sau mọi giá trị không null. Hai `null` được coi là bằng nhau để **sắp xếp** (`compare(null, null) == 0`). Khi cả hai đều khác null, comparator dùng thứ tự tự nhiên của `BigDecimal`, tức `compareTo`.

| Hai giá trị | `nullsFirst` | `nullsLast` |
| --- | --- | --- |
| `null`, `null` | `0` | `0` |
| `null`, `BigDecimal.ZERO` | Âm | Dương |
| `BigDecimal.ZERO`, `null` | Dương | Âm |
| `1.0`, `1.00` | `0` | `0` |
| `2`, `1` | Dương | Dương |

Quy tắc cho `null` ở đầu/cuối chỉ là **quy ước thứ tự hiển thị**. Nó không có nghĩa số tiền “chưa biết” thực sự nhỏ hơn hoặc lớn hơn số tiền bất kỳ. Không dùng ngầm quy ước hiển thị như một quy tắc so sánh nghiệp vụ.

Ví dụ, đoạn mã sau đặt giá trị thiếu ở cuối:

```java
List<BigDecimal> values = new ArrayList<>(Arrays.asList(
        new BigDecimal("2.0"),
        null,
        new BigDecimal("1.00"),
        new BigDecimal("1.0"),
        null,
        BigDecimal.ZERO));

values.sort(Comparator.nullsLast(Comparator.naturalOrder()));
System.out.println(values);
```

Đoạn này cần các import `BigDecimal`, `List`, `ArrayList`, `Arrays`, `Comparator` và phải nằm trong một phương thức. Kết quả:

```text
[0, 1.00, 1.0, 2.0, null, null]
```

`List.sort` giữ thứ tự tương đối của các phần tử có kết quả comparator bằng 0, vì vậy `1.00` vẫn đứng trước `1.0`. Hai giá trị cùng số học không bị ép về cùng scale.

Khi muốn **giảm dần nhưng null vẫn ở cuối**, phải chọn thứ tự ngược cho các số rồi mới bọc chính sách null:

```java
Comparator<BigDecimal> descendingNullLast =
        Comparator.nullsLast(Comparator.reverseOrder());
```

`Comparator.nullsLast(Comparator.naturalOrder()).reversed()` đảo **toàn bộ** thứ tự, bao gồm vị trí của null; khi đó null sẽ lên đầu.

### `null` của phần tử khác `null` của thuộc tính

Giả sử có một bản ghi gồm mã hóa đơn và số tiền:

```java
record Invoice(String code, BigDecimal amount) {}

Comparator<Invoice> byNullableAmount =
        Comparator.comparing(
                Invoice::amount,
                Comparator.nullsLast(Comparator.naturalOrder()));
```

Comparator trên cho phép `amount()` là null, nhưng **không** cho phép chính `Invoice` là null, bởi method reference vẫn phải gọi `amount()` từ một đối tượng tồn tại. Nếu danh sách có cả phần tử null:

```java
Comparator<Invoice> nullableInvoices =
        Comparator.nullsLast(
                Comparator.comparing(
                        Invoice::amount,
                        Comparator.nullsLast(Comparator.naturalOrder())));
```

Ví dụ record và comparator này là các mảnh mã cần đặt vào chương trình đầy đủ. Hai lớp null được xử lý độc lập: null ở **phần tử** và null ở **trường amount**. Không nên bọc nhầm tầng rồi tưởng rằng mọi trường hợp null đều đã an toàn.

## 5. Hợp đồng C: thiếu giá trị thì **chưa thể kết luận**

Với câu hỏi nghiệp vụ như “giá hiện tại cao hơn mức giới hạn hay không?”, nếu một trong hai số chưa được cung cấp, câu trả lời hợp lý có thể là “chưa biết”, không phải “nhỏ hơn”, “bằng nhau” hoặc “lớn hơn”.

Một API trả về `int` so sánh thuần túy không có chỗ biểu diễn trạng thái thứ tư đó. Với Java, có thể sử dụng `OptionalInt`:

```java
import java.math.BigDecimal;
import java.util.OptionalInt;

static OptionalInt compareIfPresent(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) {
        return OptionalInt.empty();
    }
    return OptionalInt.of(Integer.signum(left.compareTo(right)));
}
```

`OptionalInt.empty()` nghĩa là **không có kết quả so sánh**, còn `OptionalInt.of(0)` nghĩa là **đã có đủ hai giá trị và bằng nhau**. Đặc biệt không được chuyển `empty()` thành số 0 một cách vô điều kiện.

Bên gọi có thể xử lý:

```java
OptionalInt result = compareIfPresent(amount, limit);
if (result.isEmpty()) {
    // Chưa xác định: yêu cầu bổ sung dữ liệu hoặc hiển thị trạng thái riêng.
} else if (result.getAsInt() > 0) {
    // amount đã biết và lớn hơn limit.
} else {
    // amount đã biết và nhỏ hơn hoặc bằng limit.
}
```

`amount` và `limit` ở ví dụ này là các biến `BigDecimal` của ứng dụng, đoạn mã không chạy độc lập. Với API nghiệp vụ phức tạp, có thể dùng enum `LESS`, `EQUAL`, `GREATER`, `UNKNOWN`, nhưng phải giải thích `UNKNOWN` phát sinh vì điều kiện nào.

`Comparator` phải trả `int` và cung cấp thứ tự nhất quán cho các giá trị thuộc miền của nó. Vì thế **không** nhét chính sách “unknown” thành một giá trị số đặc biệt trong comparator. Nếu thực sự không muốn xếp hạng các mục thiếu dữ liệu, hãy tách hoặc lọc chúng theo chính sách nghiệp vụ; nếu vẫn muốn sắp xếp tất cả, dùng một chính sách `nullsFirst/Last` rõ ràng.

Dùng `Optional<BigDecimal>` làm giá trị trả về có thể giúp phân biệt có/không có giá trị. Tuy nhiên, khi dùng ở tham số hay trường của DTO, nên theo quy ước của dự án và tài liệu hóa hợp đồng. Không kết hợp tùy tiện `null`, `Optional.empty()` và số 0 như ba cách diễn đạt cùng một trạng thái.

## 6. Bằng nhau với giá trị có thể null: chọn đúng nghĩa

Có ba cách định nghĩa “bằng nhau” tùy mục đích:

**Cách 1 — `equals`, chấp nhận cả hai null là cùng trạng thái:**

```java
boolean sameObjects = Objects.equals(left, right);
```

Kết quả `true` cho `(null, null)`, nhưng `false` cho `(1.0, 1.00)` vì scale khác nhau.

**Cách 2 — bằng nhau về số học, chỉ khi cả hai có mặt:**

```java
boolean sameNumericValueIfPresent =
        left != null && right != null && left.compareTo(right) == 0;
```

Hai giá trị null trả `false` vì chưa có hai số để so. Nếu muốn phân biệt “khác” và “chưa biết”, dùng kiểu trả kết quả ba trạng thái thay cho một boolean.

**Cách 3 — cùng trạng thái nullable hoặc cùng giá trị số:**

```java
boolean sameNullableAmount =
        left == right
                || (left != null && right != null && left.compareTo(right) == 0);
```

Ở đây hai bên đều null được xem là tương đương **theo hợp đồng dữ liệu nullable**. Điều đó không khẳng định hai số chưa biết bằng nhau về toán học.

| Cặp giá trị | `Objects.equals` | Bằng số học khi đủ dữ liệu | Tương đương nullable/số học |
| --- | --- | --- | --- |
| `null`, `null` | `true` | `false` | `true` |
| `null`, `0` | `false` | `false` | `false` |
| `1.0`, `1.00` | `false` | `true` | `true` |
| `1.0`, `2.0` | `false` | `false` | `false` |

Tên hàm nên nói rõ nghĩa, ví dụ `sameNumericValueIfPresent` hoặc `sameNullableAmount`, hơn là `isEqual` không có tài liệu đi kèm.

## 7. `TreeSet`, `TreeMap`, `HashSet` và vấn đề scale

Do `BigDecimal.compareTo` bỏ qua khác biệt scale khi hai số bằng nhau, thứ tự tự nhiên của nó **không nhất quán với `equals`**:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

a.compareTo(b) == 0; // true
a.equals(b);         // false
```

`HashSet` dùng `equals`/`hashCode`, nên hai đối tượng trên được coi là hai phần tử khác nhau. `TreeSet` và `TreeMap` dựa trên comparator (hoặc `compareTo` khi không cung cấp comparator), nên chúng xem hai giá trị có kết quả compare bằng 0 là **cùng khóa theo thứ tự**. Chỉ một phần tử/khóa đại diện được lưu.

`Comparator.nullsFirst/Last(Comparator.naturalOrder())` cho phép đặt `null` theo thứ tự được chọn nhưng **không thay đổi** tính không nhất quán giữa `BigDecimal.compareTo` và `BigDecimal.equals`.

Nếu cần phân biệt thêm scale trong tập hợp có thứ tự, có thể nối một tiêu chí phụ:

```java
Comparator<BigDecimal> numericThenScale =
        Comparator.<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);

Comparator<BigDecimal> nullableNumericThenScale =
        Comparator.nullsLast(numericThenScale);
```

Với những giá trị `BigDecimal` không null, comparator này so theo số học trước, rồi so scale. Hai `BigDecimal` cùng số học **và** cùng scale là bằng nhau theo `equals`, vì vậy thứ tự này phù hợp hơn khi scale mang ý nghĩa định danh. Tuy nhiên, nếu nghiệp vụ xem `1.0` và `1.00` là cùng một số tiền, phải chọn chính sách chuẩn hóa hoặc chấp nhận thứ tự tự nhiên xem chúng tương đương. Đừng đổi quy tắc chỉ để ép số lượng phần tử trong `HashSet` và `TreeSet` trùng nhau.

Một `TreeSet` được khởi tạo với comparator cho phép null có thể chứa một phần tử null và sắp xếp nó theo chính sách; `TreeSet` dùng thứ tự tự nhiên không hỗ trợ điều đó. Trong dữ liệu thực tế, cần tài liệu hóa cả **quy tắc null** lẫn **quy tắc xem hai giá trị là trùng**.

## 8. Chương trình Java 17+ hoàn chỉnh

Lưu mã sau thành **`BigDecimalNullComparisonDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

public class BigDecimalNullComparisonDemo {
    static int compareRequired(BigDecimal left, BigDecimal right) {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        return Integer.signum(left.compareTo(right));
    }

    static OptionalInt compareIfPresent(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.signum(left.compareTo(right)));
    }

    static boolean sameNumericValueIfPresent(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("compareTo = " + a.compareTo(b));
        System.out.println("equals = " + a.equals(b));
        System.out.println("Objects.equals(null, null) = " + Objects.equals(null, null));
        System.out.println("sameNumericValueIfPresent = " + sameNumericValueIfPresent(a, b));

        System.out.println("compareRequired = " + compareRequired(a, b));
        System.out.println("compareIfPresent(null, b).isEmpty = "
                + compareIfPresent(null, b).isEmpty());
        try {
            compareRequired(null, b);
        } catch (NullPointerException ex) {
            System.out.println("compareRequired(null, b) = rejected");
        }

        Comparator<BigDecimal> nullsFirst =
                Comparator.nullsFirst(Comparator.naturalOrder());
        Comparator<BigDecimal> nullsLast =
                Comparator.nullsLast(Comparator.naturalOrder());
        System.out.println("nullsFirst(null, 0) = "
                + Integer.signum(nullsFirst.compare(null, BigDecimal.ZERO)));
        System.out.println("nullsLast(null, 0) = "
                + Integer.signum(nullsLast.compare(null, BigDecimal.ZERO)));

        List<BigDecimal> values = new ArrayList<>(Arrays.asList(
                new BigDecimal("2.0"), null, b, a, null, BigDecimal.ZERO));
        values.sort(nullsLast);
        System.out.println("sorted = " + values);

        Set<BigDecimal> hashSet = new HashSet<>(List.of(a, b));
        Set<BigDecimal> naturalTreeSet = new TreeSet<>(List.of(a, b));
        Comparator<BigDecimal> numericThenScale =
                Comparator.<BigDecimal>naturalOrder().thenComparingInt(BigDecimal::scale);
        Set<BigDecimal> scaleAwareTreeSet = new TreeSet<>(numericThenScale);
        scaleAwareTreeSet.addAll(List.of(a, b));
        System.out.println("set sizes = " + hashSet.size() + "/"
                + naturalTreeSet.size() + "/" + scaleAwareTreeSet.size());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 BigDecimalNullComparisonDemo.java
java BigDecimalNullComparisonDemo
```

Stdout đã xác minh trên Java 21; mã tương thích Java 17:

```text
compareTo = 0
equals = false
Objects.equals(null, null) = true
sameNumericValueIfPresent = true
compareRequired = 0
compareIfPresent(null, b).isEmpty = true
compareRequired(null, b) = rejected
nullsFirst(null, 0) = -1
nullsLast(null, 0) = 1
sorted = [0, 1.00, 1.0, 2.0, null, null]
set sizes = 2/1/2
```

Dòng cuối lần lượt là kích thước `HashSet`, `TreeSet` theo thứ tự tự nhiên và `TreeSet` xét thêm scale. Khi so sánh kết quả, cần phân biệt **cùng giá trị số**, **cùng biểu diễn gồm scale**, và **hai vị trí đứng cạnh nhau trong một danh sách đã sắp xếp**.

## 9. Chọn hợp đồng theo tình huống

| Tình huống | Hợp đồng khuyên dùng | Lý do |
| --- | --- | --- |
| Hai số tiền bắt buộc trước khi thanh toán | `Objects.requireNonNull` rồi `compareTo` | Thiếu dữ liệu phải bị từ chối |
| Sắp xếp danh sách có thể thiếu giá | `Comparator.nullsLast(Comparator.naturalOrder())` | Hiển thị được mọi dòng, giá thiếu ở cuối |
| Xếp mức thiếu lên đầu danh sách cần bổ sung | `Comparator.nullsFirst(...)` | Lộ rõ dữ liệu cần hoàn thiện |
| Quyết định một giá có vượt ngưỡng hay không, nhưng ngưỡng chưa có | `OptionalInt` hoặc enum có `UNKNOWN` | Không gán kết luận sai khi chưa đủ dữ liệu |
| So khớp giá trị thập phân không quan tâm số chữ số sau dấu phẩy | Hai bên khác null và `compareTo(...) == 0` | `equals` có xét scale |
| So khớp dữ liệu khi cả hai cùng null được xem là cùng trạng thái | Kiểm tra `left == right` trước rồi `compareTo` nếu đủ dữ liệu | Quy tắc nullable được diễn đạt rõ |
| Khóa phải phân biệt `1.0` và `1.00` | `equals`/`hashCode` hoặc comparator xét thêm scale | Giữ phân biệt biểu diễn |
| Khóa chỉ phân biệt giá trị số | Tập hợp có thứ tự theo `compareTo` hoặc chuẩn hóa được xác định trước | Tránh bất nhất định danh |

Nếu API có nhiều người gọi, nên mô tả trong Javadoc: đối số nào nullable; `null` nghĩa là gì; trả kết quả gì khi thiếu; so theo số học hay so theo scale; nếu dùng comparator thì null đứng ở đâu; nếu dùng làm khóa thì các giá trị nào được coi là trùng. Tránh tên `isEqual` hoặc `compare` quá chung khi có nhiều quy tắc null cùng tồn tại.

## 10. Lỗi phổ biến và bộ trường hợp kiểm tra

Các lỗi dễ xuất hiện:

1. Gọi `left.compareTo(right)` trong khi không chắc cả hai khác null.
2. Dùng `Objects.equals` để kiểm tra hai `BigDecimal` bằng nhau về số học, quên `scale`.
3. Coi `null` là 0 bằng cách `Objects.requireNonNullElse(value, BigDecimal.ZERO)` mà không có quy tắc nghiệp vụ.
4. Trả `0` khi một bên thiếu, làm sai ý nghĩa “đã so sánh và bằng nhau”.
5. Dùng `Comparator.nullsLast` trong điều kiện nghiệp vụ thay vì chỉ dùng để sắp xếp.
6. Bọc comparator của thuộc tính nullable, nhưng quên chính đối tượng chứa thuộc tính cũng có thể null.
7. Dùng `.reversed()` trên comparator null-last rồi ngạc nhiên vì null xuất hiện đầu.
8. Cho rằng `TreeSet` sẽ giữ hai `BigDecimal` có `equals == false` dù `compareTo == 0`.
9. Chuyển sang `double` trước khi so, có nguy cơ mất độ chính xác.
10. Chỉ test cặp giá trị thường, không test `null` và scale khác nhau.

Bộ tình huống tối thiểu cho một API nullable gồm `(null, null)`, `(null, 0)`, `(0, null)`, `(0, 0)`, `(1.0, 1.00)`, `(-1, 0)`, `(1, 2)`, `(2, 1)`. Với comparator, kiểm tra thêm chiều đối xứng, tính bắc cầu và kết quả khi dùng làm thứ tự của `TreeSet`/`TreeMap`.

Tóm lại, **`null` là trạng thái thiếu dữ liệu, `compareTo` là quan hệ thứ tự theo số học, còn `equals` xét cả scale**. Ba vấn đề ấy cần được quyết định riêng trong hợp đồng API. API từ chối dữ liệu thiếu, API sắp xếp có quy tắc null và API cho phép kết quả “chưa xác định” giải quyết những nhu cầu khác nhau.

Tài liệu Java SE 21 để đối chiếu:

- [`BigDecimal`: `compareTo`, `equals`, `min`, `max`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [`Comparable`: hợp đồng thứ tự và tính nhất quán với `equals`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html)
- [`Comparator`: `nullsFirst`, `nullsLast`, `comparing`, `reversed`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html)
- [`Objects`: `requireNonNull`, `equals`, `compare`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-comparison-010">Không thay đổi equals toàn hệ thống chỉ để giải quyết một phép so sánh cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh BigDecimal: không đổi equals toàn hệ thống vì một phép so sánh cụ thể

## 1. Tình huống và nguyên tắc

Trong Java, hai giá trị `BigDecimal` có thể **bằng nhau về số học nhưng không bằng nhau theo `equals`**:

- `new BigDecimal("2.0")` và `new BigDecimal("2.00")` đều biểu diễn số 2, nhưng có `scale()` lần lượt là 1 và 2.
- `a.compareTo(b) == 0` kiểm tra hai số bằng nhau **về giá trị số học**.
- `a.equals(b)` kiểm tra cùng **giá trị số học và scale**. Vì vậy `a.equals(b)` trả `false` cho cặp trên.

Vấn đề thường xuất hiện khi một yêu cầu rất cụ thể, chẳng hạn “so sánh tổng tiền đã tính với tổng tiền người dùng nhập, bỏ qua số lượng số 0 ở cuối”, bị xử lý bằng cách **thay đổi quy tắc equals của cả mô hình, cả ứng dụng hoặc mọi nơi đang dùng BigDecimal**. Đây là giải pháp quá rộng: có thể phá tính nhất quán của `hashCode`, khóa trong collection, quy tắc định danh đối tượng, cache và dữ liệu lưu trữ.

**Nguyên tắc:** đặt chính sách so sánh tại đúng thao tác có nhu cầu đó. Giữ nguyên hợp đồng equality hiện có của các phần còn lại, trừ khi **đặc tả của chính kiểu dữ liệu** yêu cầu một equality mới và bạn kiểm soát đầy đủ các hệ quả.

## 2. Vì sao equals và compareTo khác nhau?

`BigDecimal` lưu một giá trị nguyên chưa scale và một `scale`:

`value = unscaledValue × 10^(-scale)`.

| Biểu diễn | `unscaledValue()` | `scale()` | Giá trị toán học |
|---|---:|---:|---:|
| `2` | 2 | 0 | 2 |
| `2.0` | 20 | 1 | 2 |
| `2.00` | 200 | 2 | 2 |
| `0.00` | 0 | 2 | 0 |

Ví dụ:

~~~java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");

System.out.println(x.equals(y));     // false
System.out.println(x.compareTo(y));  // 0
System.out.println(x.scale());       // 1
System.out.println(y.scale());       // 2
~~~

`compareTo` trả số âm, 0 hoặc số dương khi giá trị toán học của toán hạng thứ nhất tương ứng nhỏ hơn, bằng hoặc lớn hơn toán hạng thứ hai. **Không kiểm tra `compareTo(...) == -1` hay `== 1`**, vì hợp đồng chỉ bảo đảm dấu của kết quả. Để kiểm tra bằng nhau về số học, dùng `compareTo(...) == 0`.

`equals` của `BigDecimal` xét cả giá trị và scale; `hashCode` tương thích với cách hiểu này, nên hai số bằng nhau về toán học nhưng khác scale thường có hash khác nhau. `BigDecimal` là lớp `final`: không thể subclass để override `equals` của chính `BigDecimal`. Tuy nhiên, vẫn có thể vô tình thay đổi equality của **wrapper, record, entity** hoặc ép normalize BigDecimal trên toàn hệ thống chỉ nhằm sửa một so sánh.

Phân biệt các hợp đồng:

- **Bằng nhau về số học:** `a.compareTo(b) == 0`.
- **Bằng nhau theo biểu diễn BigDecimal:** `a.equals(b)`.
- **Bằng nhau theo nghiệp vụ:** có thể còn yêu cầu **loại tiền, trạng thái, đơn vị, thời điểm, định danh** hoặc những trường khác. Chỉ kiểm tra amount bằng số học chưa chắc xác định được hai đối tượng nghiệp vụ là một.

## 3. Ví dụ chạy được: tác động tới HashSet, TreeSet, HashMap và TreeMap

Lưu chương trình sau trong `BigDecimalEqualityDemo.java` (Java 17+):

~~~java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BigDecimalEqualityDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println("equals: " + a.equals(b));
        System.out.println("compareTo: " + a.compareTo(b));

        Set<BigDecimal> hashSet = new HashSet<>(List.of(a, b));
        Set<BigDecimal> treeSet = new TreeSet<>(List.of(a, b));
        System.out.println("HashSet size: " + hashSet.size());
        System.out.println("TreeSet size: " + treeSet.size());

        Map<BigDecimal, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        Map<BigDecimal, String> treeMap = new TreeMap<>();
        treeMap.put(a, "first");

        System.out.println("HashMap get(b): " + hashMap.get(b));
        System.out.println("TreeMap get(b): " + treeMap.get(b));
        System.out.println("local numeric equality: "
                + (a.compareTo(b) == 0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalEqualityDemo.java
java BigDecimalEqualityDemo
~~~

Stdout:

~~~text
equals: false
compareTo: 0
HashSet size: 2
TreeSet size: 1
HashMap get(b): null
TreeMap get(b): first
local numeric equality: true
~~~

**Giải thích:**

- `HashSet` và `HashMap` dựa trên `equals`/`hashCode`. Khóa `2.0` **khác** khóa `2.00` theo quy tắc BigDecimal.
- `TreeSet` và `TreeMap` sử dụng thứ tự tự nhiên từ `compareTo` (hoặc `Comparator` đã cấp). `compareTo` coi hai biểu diễn là một vị trí trong cấu trúc sắp xếp; khóa thứ hai có thể được xem như trùng khóa thứ nhất.
- Chính tài liệu Java mô tả thứ tự tự nhiên của `BigDecimal` **không nhất quán với equals**. `TreeSet` với hai giá trị này xử lý theo quy tắc thứ tự của nó; không nên tự giả định cùng một khái niệm “trùng” giữa mọi collection.

Các thao tác dựa trên `equals` như `List.contains`, `List.remove`, `Stream.distinct`, `HashMap.get` hoặc `Collectors.groupingBy` (với `HashMap` mặc định) có thể hành xử khác `TreeSet` hoặc `TreeMap`. Hãy chọn cấu trúc và hàm so sánh theo yêu cầu, không sửa equality ở toàn bộ hệ thống để che giấu khác biệt.

## 4. Cách đúng khi chỉ cần một phép so sánh số học

Giả sử kiểm tra “giá trị người dùng gửi có bằng tổng tính được về mặt số học không?”. Việc người dùng nhập `100.0` hay `100.00` không ảnh hưởng kết quả xác nhận. So sánh trực tiếp tại vị trí cần kiểm tra:

~~~java
BigDecimal calculated = new BigDecimal("100.00");
BigDecimal entered = new BigDecimal("100.0");

boolean matches = calculated.compareTo(entered) == 0;
System.out.println(matches); // true
~~~

Không cần sửa model, equals, hashCode hay quy tắc chuẩn hóa đầu vào chung.

Nếu thao tác đó được dùng ở một số vị trí có cùng nghĩa, gom thành hàm đặt tên cụ thể:

~~~java
import java.math.BigDecimal;
import java.util.Objects;

public final class AmountComparisons {
    private AmountComparisons() {}

    public static boolean sameNumericValue(BigDecimal left, BigDecimal right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.compareTo(right) == 0;
    }
}
~~~

Đoạn trên là **utility class**, không tự chạy vì không có `main`; có thể biên dịch riêng bằng `javac AmountComparisons.java` hoặc gọi từ code khác. Quyết định xử lý `null` là một phần của contract: bản này từ chối `null` để không âm thầm coi “thiếu giá trị” bằng 0. Nếu cần `null == null` theo nghĩa “cùng vắng mặt”, hãy xây dựng hàm riêng có tên và kiểm thử rõ ràng. Không dùng `Objects.equals(left, right)` như một cách thay thế âm thầm vì nó quay lại `BigDecimal.equals`.

**Giới hạn của hàm này:** nó chỉ so sánh hai con số. Nếu là tiền, phải xác thực đơn vị tiền. `100 USD` không đồng nghĩa `100 EUR` chỉ vì hai amount bằng 100.

### Không dùng ==, không dùng toString, không chuyển qua double

- `a == b` chỉ kiểm tra **cùng tham chiếu** đối tượng `BigDecimal`, không phải giá trị.
- `a.toString().equals(b.toString())` so sánh chuỗi biểu diễn, không phù hợp cho equality số học.
- `a.doubleValue() == b.doubleValue()` có thể mất precision hoặc tràn sang vô cực, khiến những giá trị `BigDecimal` thực sự khác nhau bị coi là bằng nhau.
- `a.subtract(b).abs().compareTo(epsilon) <= 0` là **so sánh với dung sai**, một đặc tả khác, không phải equality BigDecimal. Dung sai phải có đơn vị và lý do kinh doanh/kỹ thuật rõ ràng.

## 5. Tại sao không nên override equals của wrapper hoặc entity để sửa một nhu cầu?

Hãy xem model có identity ổn định:

~~~java
record InvoiceLine(String lineId, BigDecimal amount) {}
~~~

Đây là record Java 17; equality sinh tự động từ **cả** `lineId` và `amount`, trong đó amount dùng `BigDecimal.equals`. Nếu có một màn hình cần so sánh riêng **số tiền bỏ qua scale**, đừng vội override `InvoiceLine.equals` chỉ vì màn hình đó. Làm như vậy đổi ý nghĩa của “hai dòng hóa đơn bằng nhau” trong toàn chương trình, trong khi hai dòng vẫn có thể có id khác nhau, thuế suất khác nhau hoặc là các nghiệp vụ khác nhau.

Hậu quả phổ biến của thay đổi equality toàn cục:

1. **Hash contract:** nếu `equals` đã đổi thì `hashCode` phải đổi tương ứng; mọi đối tượng được coi là bằng nhau phải có cùng hash. Chỉ đổi một phương thức là lỗi hợp đồng.
2. **Dữ liệu đang làm khóa:** `HashSet`/`HashMap` có thể mất khả năng tìm khóa hoặc thay đổi logic trùng lặp. Nếu equality hoặc các trường tham gia hash thay đổi **sau khi đưa khóa vào HashMap**, tra cứu có thể thất bại.
3. **Cache, nhóm và distinct:** đối tượng trước kia khác nhau nay bị gộp, làm mất kết quả hay sai số lượng phần tử.
4. **Identity và persistence:** entity có quy tắc định danh/lifecycle riêng; đổi equals chỉ vì scale của một amount có thể ảnh hưởng việc xử lý entity chưa có id, entity đã lưu, collection quan hệ hoặc caching. Cách implement tùy công nghệ; phải đánh giá trên contract của entity.
5. **API và kiểm thử:** equality là hợp đồng được nhiều nơi dựa vào. Thay đổi nó có thể gây regression dù phép so sánh đầu tiên đã “đúng”.

Ngược lại, có những **value object được thiết kế từ đầu** với equality theo giá trị đã chuẩn hóa; đó là quyết định thuộc *kiểu giá trị* và có thể hợp lý. Nhưng cần có đặc tả rõ, đồng bộ `equals`/`hashCode`, xác định cách canonicalize dữ liệu, cùng kiểm thử ở mọi nơi sử dụng. Đừng áp dụng quy tắc đó lên mọi class chỉ vì một màn hình.

## 6. Khi nào normalize có ý nghĩa? Khi nào dễ gây hậu quả?

Hai kỹ thuật thường gặp:

### 6.1. Loại bỏ số 0 cuối bằng stripTrailingZeros

~~~java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.stripTrailingZeros().equals(b.stripTrailingZeros())); // true
System.out.println(new BigDecimal("1000.00").stripTrailingZeros());        // 1E+3
~~~

`stripTrailingZeros()` trả BigDecimal khác có thể có **scale âm** và chuỗi khoa học (`1E+3`), đặc biệt khi số là lũy thừa 10. Kết quả có thể hợp lý cho một **canonical key riêng** nhưng không tự động thích hợp cho định dạng, serializing, validation hay báo cáo kế toán vốn cần thể hiện scale. Nếu mục tiêu chỉ là kiểm tra hai con số bằng nhau, `compareTo(...) == 0` rõ nghĩa hơn và tránh biến đổi dữ liệu.

Một giá trị `0.00` sau strip trailing zeros có thể trở thành zero scale 0; điều đó càng nhắc rằng normalize có thể xóa thông tin về biểu diễn ban đầu.

### 6.2. Chốt scale khi kiểu dữ liệu *thực sự* có bất biến scale

Ví dụ một loại số tiền nội bộ **được định nghĩa rõ** là luôn có hai chữ số thập phân, và đầu vào không được làm tròn ngầm:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record FixedTwoDecimalAmount(BigDecimal value) {
    public FixedTwoDecimalAmount {
        Objects.requireNonNull(value, "value");
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
~~~

Đây là **value object dùng trong một miền có scale cố định**, không phải chính sách cho mọi loại tiền. Java record tự sinh `equals`/`hashCode` từ thành phần `value` **sau khi đã normalize trong constructor**. Kết quả `new FixedTwoDecimalAmount(new BigDecimal("2.0"))` và phiên bản `2.00` bằng nhau; đầu vào `2.001` sẽ ném `ArithmeticException` vì đòi làm tròn mà chính sách cấm.

Không sử dụng `setScale(2, HALF_UP)` ở constructor một cách tùy tiện, vì nó tự thay đổi giá trị đầu vào. Nếu cần chấp nhận rồi làm tròn, đó phải là rule rõ trong đặc tả và tên phương thức tạo. Các loại tiền khác nhau có thể sử dụng số chữ số thập phân khác 2; không suy luận bất biến từ thói quen hiển thị.

### Quyết định đặt normalize ở đâu

Nếu bạn **chỉ cần một lần đối chiếu**, gọi `compareTo` ngay trong nghiệp vụ. Nếu loại dữ liệu có bất biến scale ổn định, normalize tại biên tạo **kiểu dữ liệu đó** sau khi thống nhất nghiệp vụ. Nếu dữ liệu làm khóa hash cần canonicalization, chuẩn hóa **khi tạo khóa** và không thay đổi khóa sau khi đưa vào map/set. Không “dọn sạch số 0” khi đọc/ghi khắp hệ thống chỉ để làm cho một phép `equals` tình cờ thành true.

## 7. Collection, comparator và tính nhất quán với equals

`Comparator<BigDecimal> numericOrder = BigDecimal::compareTo;` có thể dùng rõ ràng khi bộ sưu tập cần **thứ tự số học**. Trong sorted collection, comparator trả 0 làm hai phần tử được xem là cùng khóa, kể cả khi `equals` trả false.

~~~java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

Comparator<BigDecimal> numericOrder = BigDecimal::compareTo;
Set<BigDecimal> uniqueNumericValues = new TreeSet<>(numericOrder);
uniqueNumericValues.add(new BigDecimal("2.0"));
uniqueNumericValues.add(new BigDecimal("2.00"));
System.out.println(uniqueNumericValues.size()); // 1
~~~

Đây là **đoạn minh họa đặt trong thân phương thức** (không phải chương trình standalone); đã khai báo đầy đủ import dùng trong đoạn. Lưu ý `TreeSet` và `TreeMap` bỏ/gộp phần tử khi comparator trả 0. Nếu giữ nhiều bản biểu diễn để phục vụ báo cáo/audit, việc dùng comparator này để “deduplicate” có thể mất dữ liệu. Nếu cần giữ cả hai và vẫn sắp xếp theo giá trị, dùng collection phù hợp hoặc comparator có **tie-breaker theo scale** để phân biệt hai bản biểu diễn — chọn theo đúng semantics mong muốn.

Đừng thay toàn bộ collection thành `TreeSet` chỉ để workaround equality ở một bước. Khi cần giữ nguyên `HashMap`, có thể dùng khóa chuẩn hóa cục bộ được tài liệu hóa; nhưng chú ý giá trị gốc, scale, null, currency và cách hash/serialize. Đừng lạm dụng `stripTrailingZeros` khi `compareTo == 0` cho phép giải quyết nhu cầu một cách trực tiếp.

### Phân biệt equality với “gần bằng nhau”

Ví dụ dung sai 0.10: `0.00` gần `0.09`, `0.09` gần `0.18`, nhưng `0.00` không gần `0.18`. Quan hệ gần bằng nhau kiểu này **không có tính bắc cầu** nên không thích hợp để trở thành `equals` hoặc comparator mà một sorted collection dùng làm quan hệ trùng khóa. Nếu bài toán cần chấp nhận sai lệch, hãy viết predicate cục bộ với ngưỡng cụ thể, không thay equality và không đưa comparator gần đúng vào `TreeSet`/`TreeMap` một cách mù quáng.

## 8. Cách quyết định theo tình huống

| Yêu cầu | Cách thực hiện phù hợp | Điều cần tránh |
|---|---|---|
| Kiểm tra hai BigDecimal bằng nhau về giá trị số | `a.compareTo(b) == 0` ngay tại vị trí kiểm tra | Override equals của model chỉ vì một dòng if |
| Kiểm tra scale/biểu diễn chính xác như dữ liệu gốc | `a.equals(b)` hoặc kiểm tra cả giá trị và `scale()` | Dùng compareTo rồi bỏ qua yêu cầu scale |
| Kiểm tra số tiền giao dịch | Kiểm tra currency, amount, và rule nghiệp vụ liên quan | Chỉ so sánh amount rồi coi hai giao dịch giống hệt |
| Sắp xếp theo trị số | `Comparator<BigDecimal>` hoặc `compareTo` | Giả định comparator trùng với equals |
| Loại trùng số học cho một báo cáo | Dùng cấu trúc/khóa canonical riêng với nghĩa trùng được mô tả | Áp dụng normalize toàn database hoặc toàn model |
| Kiểu amount bất biến scale | Chốt scale khi tạo value object theo domain contract | Rải `setScale` ở mọi nơi |
| Kiểm tra sai lệch trong phạm vi dung sai | Predicate `abs(a-b) <= tolerance`, quy định cả đơn vị | Sử dụng quan hệ gần bằng như equals/hash/comparator |
| Entity cần so sánh identity | Dựa vào hợp đồng identity của entity, xét lifecycle | Lấy phép so sánh amount để thay định danh entity |

Các lựa chọn này độc lập về mặt **ý nghĩa**. Một hệ thống có thể đồng thời dùng `equals` cho khóa định danh, `compareTo` cho một phép đối soát, comparator cho thứ tự hiển thị và canonical value object cho một nghiệp vụ riêng.

## 9. Thiết kế test bảo vệ cả hành vi mong muốn lẫn hệ quả phụ

Trong đơn vị có phép đối soát theo số học, kiểm thử cả hai hướng:

~~~java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

if (a.compareTo(b) != 0) {
    throw new AssertionError("Numeric value should match");
}
if (a.equals(b)) {
    throw new AssertionError("BigDecimal representation unexpectedly matched");
}
~~~

Đoạn này là **code assertion đặt trong method**, không chứa `main`; ghép vào unit test phù hợp.

Kiểm thử bổ sung:

1. **Cặp khác scale:** `2.0` và `2.00` bằng số học, khác về `equals`; `2.00` và `2.01` phải khác về số học.
2. **Số âm và zero:** `-0.00` và `0.0` có giá trị số bằng nhau; so sánh scale vẫn khác. `-2.0` và `-2.00` cũng bằng nhau theo `compareTo`.
3. **Null:** quy định ném lỗi, coi là vắng dữ liệu hay bỏ qua so sánh. Không cho null bị hiểu nhầm là zero.
4. **Hash contract của model:** nếu thiết kế kiểu mới có custom equality, kiểm tra tính phản xạ, đối xứng, bắc cầu, nhất quán và `equals ⇒ cùng hashCode`; thử thao tác `HashSet` và `HashMap`.
5. **Collection theo comparator:** thử số lượng phần tử, `contains`, put/get khi trùng compareTo nhưng khác equals; xác nhận hệ thống có thực sự muốn mất bản biểu diễn hay không.
6. **Giao dịch nhiều tiền tệ:** số tiền cùng amount nhưng khác currency không được coi là cùng giá trị tiền trong domain.
7. **Regression:** khi chỉ sửa một phép so sánh, kiểm thử rằng equality/identity ở cache, mapping và các nghiệp vụ còn lại vẫn tuân theo hợp đồng hiện có.

Khi cần một hàm so sánh riêng, đặt tên phản ánh **nghĩa nghiệp vụ**, ví dụ `sameNumericTotal` hay `matchesEnteredAmount`; tránh một helper chung tên mơ hồ như `isEqual` rồi bị dùng trong mọi ngữ cảnh.

## 10. Những sai lầm dễ mắc

- **“equals sai nên sửa nó”:** thường chỉ là bạn đang cần equality số học tại một vị trí, còn `BigDecimal.equals` đang thực hiện đúng hợp đồng của nó.
- **Override equals ở entity để một phép so sánh amount thành true:** đổi luôn định nghĩa “cùng entity” trên toàn ứng dụng.
- **Sửa equals mà quên hashCode:** vi phạm hợp đồng collection và dễ tạo lỗi khó tái hiện.
- **Sử dụng BigDecimal làm khóa mutable:** `BigDecimal` bất biến, nhưng **wrapper chứa khóa hoặc các trường tham gia equality/hash** có thể bị sửa sau khi insert; đó mới là rủi ro.
- **Thay HashSet bằng TreeSet và giả định hành vi vẫn tương đương:** natural ordering của BigDecimal không nhất quán với equals, dẫn đến số phần tử/khóa khác.
- **Chuẩn hóa bằng stripTrailingZeros ở mọi layer:** có thể đổi scale và chuỗi hiển thị, như `1000.00 → 1E+3`.
- **Chỉ dùng compareTo mà bỏ qua currency:** equality về amount không đồng nghĩa equality về tiền.
- **Dùng double cho so sánh:** có thể mất thông tin chính xác vốn được giữ trong BigDecimal.
- **Dùng tolerance như equality:** quan hệ gần bằng có thể không bắc cầu; không phù hợp để làm hợp đồng set/map.
- **Dùng equals để kiểm tra “đã thanh toán đủ” nhưng người dùng nhập khác scale:** chọn `compareTo == 0` cho phép đối soát được đặc tả là bằng nhau về số học.

## 11. Bài tập củng cố

**Bài 1.** Với `new BigDecimal("0.00")` và `BigDecimal.ZERO`, hãy dự đoán `equals`, `compareTo`, số phần tử của `HashSet`/`TreeSet` sau khi thêm cả hai. Giải thích dựa trên scale.

**Bài 2.** Một API nhận số lượng hàng `"5.0"`, dữ liệu đã tính là `"5"`. Nghiệp vụ chỉ cần kiểm tra cùng số lượng. Chọn cách so sánh và xử lý null; có cần sửa equals của DTO không?

**Bài 3.** Một màn hình lịch sử giao dịch phải giữ nguyên cả số nhập gốc và số đã định dạng. Nếu dùng `stripTrailingZeros` ngay khi đọc database, thông tin nào có thể mất?

**Bài 4.** Viết test chứng minh quan hệ “chênh lệch tuyệt đối không quá 0.10” không bắc cầu với bộ `0.00`, `0.09`, `0.18`. Vì sao không nên dùng quan hệ đó cho `Comparator` của `TreeSet`?

**Gợi ý đáp án:** (1) `equals=false`, `compareTo=0`, HashSet có 2, TreeSet có 1. (2) dùng `compareTo == 0` trong validator có xử lý null rõ; không cần sửa equals DTO. (3) scale và biểu diễn số 0 cuối, thậm chí dạng số mũ. (4) A gần B, B gần C, nhưng A không gần C; sự không bắc cầu phá giả định của thứ tự toàn phần khi sorted collection gom khóa.

## Tài liệu chuẩn

- Java SE 17 — `BigDecimal` (`equals`, `compareTo`, `hashCode`, `stripTrailingZeros`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html
- Java SE 17 — `Comparable`, quy tắc “consistent with equals”: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html
- Java SE 17 — `TreeSet`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html
- Java SE 17 — `Object.equals` và `hashCode`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html

**Ghi nhớ:** một nhu cầu so sánh theo *giá trị số học* nên được thể hiện trực tiếp bằng `compareTo(...) == 0` tại đúng chỗ. Việc thay đổi `equals` của model/value object chỉ hợp lý khi **toàn bộ định nghĩa của kiểu đó** thực sự yêu cầu như vậy và hợp đồng `hashCode`, collection, identity cùng dữ liệu được xem xét đầy đủ.


</details>

- [Quay lại đầu trang](#back-to-top)
