<a id="back-to-top"></a>

# Tổng hợp số

## Menu
- [int&#91;&#93;, long&#91;&#93;, double&#91;&#93; và các stream chuyên biệt](#numeric-synthesis-001)
- [IntStream, LongStream, DoubleStream và giảm boxing](#numeric-synthesis-002)
- [sum có thể tràn đối với số nguyên](#numeric-synthesis-003)
- [average và yêu cầu xử lý tập rỗng](#numeric-synthesis-004)
- [min, max và các Optional chuyên biệt](#numeric-synthesis-005)
- [SummaryStatistics và thu thập nhiều số đo](#numeric-synthesis-006)
- [Thứ tự cộng ảnh hưởng tổng dấu phẩy động](#numeric-synthesis-007)
- [Reduce song song và yêu cầu tính kết hợp](#numeric-synthesis-008)
- [Tổng BigDecimal và chính sách MathContext](#numeric-synthesis-009)
- [Không trộn đơn vị, precision hoặc scale không được thống nhất](#numeric-synthesis-010)

## <a id="numeric-synthesis-001">int&#91;&#93;, long&#91;&#93;, double&#91;&#93; và các stream chuyên biệt</a>

<details>
<summary>Xem chi tiết</summary>

# `int[]`, `long[]`, `double[]` và các stream chuyên biệt trong Java

## 1. Mảng số là nguồn dữ liệu, stream là pipeline xử lý

Java có ba loại mảng nguyên thủy thường dùng để tổng hợp số: `int[]`, `long[]` và `double[]`. Mảng giữ các phần tử theo **chỉ số** và có thể thay đổi phần tử; stream trình bày **một lượt duyệt và chuỗi thao tác** trên nguồn dữ liệu. Việc tạo stream không tự sao chép toàn bộ mảng.

```java
int[] counts = {3, 1, 4, 1, 5};
long[] bytes = {3_000_000_000L, 4_000_000_000L};
double[] temperatures = {19.5, 20.0, 21.5};
```

Độ rộng mỗi phần tử nguyên thủy là 32 bit (`int`), 64 bit (`long`) hoặc 64 bit dấu phẩy động nhị phân (`double`). Mảng có `length` và hỗ trợ truy cập `counts[0]`. Stream không cung cấp chỉ số tự động, sử dụng **một lần** và chỉ chạy pipeline trung gian khi có thao tác kết thúc như `sum()`, `average()` hoặc `toArray()`.

**Trọng tâm bài này** là chọn đúng nguồn mảng, đoạn mảng và vị trí chuyển kiểu. Bài tiếp theo đi sâu vào primitive stream so với `Stream<Integer/Long/Double>`, boxing và các collector.

## 2. Tạo đúng stream từ ba loại mảng

| Mảng đầu vào | Factory | Loại stream | Phần tử được duyệt |
| --- | --- | --- | --- |
| `int[]` | `Arrays.stream(array)` | `IntStream` | từng giá trị `int` |
| `long[]` | `Arrays.stream(array)` | `LongStream` | từng giá trị `long` |
| `double[]` | `Arrays.stream(array)` | `DoubleStream` | từng giá trị `double` |

```java
int intSum = java.util.Arrays.stream(new int[] {1, 2, 3}).sum(); // 6
long longSum = java.util.Arrays.stream(new long[] {3_000_000_000L, 4_000_000_000L}).sum(); // 7000000000
double doubleMean = java.util.Arrays.stream(new double[] {2.0, 4.0})
        .average().orElseThrow(); // 3.0
```

**Bẫy `Stream.of(array)`**: với mảng primitive, `Stream.of(new int[] {1, 2, 3})` có kiểu `Stream<int[]>` và **một phần tử là nguyên mảng**. `Arrays.stream(new int[] {1, 2, 3})` có kiểu `IntStream` với **ba phần tử số**. `Arrays.asList(intArray)` cũng giữ nguyên mảng primitive thành một phần tử của list thay vì tạo ba `Integer`.

### Một đoạn liên tiếp của mảng

`Arrays.stream(array, fromInclusive, toExclusive)` chỉ đọc vùng chỉ số `[fromInclusive, toExclusive)`. Với `int[] a = {10,20,30,40,50}`, `Arrays.stream(a,1,4).sum()` bằng `20+30+40 = 90`. `fromInclusive > toExclusive` gây `IllegalArgumentException`; chỉ số ngoài mảng có thể gây `ArrayIndexOutOfBoundsException`. Ba kiểu mảng đều có overload đọc đoạn.

## 3. Khi tính toán cần chỉ số

`Arrays.stream(array)` cho **giá trị** từng phần tử; nó không tự cung cấp vị trí trong mảng. Nếu cần chọn vị trí, xử lý hai mảng song song theo chỉ số hoặc tra cứu metadata tại vị trí, hãy tạo dãy chỉ số bằng `IntStream.range(0, array.length)`.

```java
int[] scores = {7, 9, 4, 10};
int[] highIndices = java.util.stream.IntStream.range(0, scores.length)
        .filter(i -> scores[i] >= 9)
        .toArray(); // {1, 3}
```

`range(start, end)` không gồm cận trên. Nếu xử lý hai mảng `prices[]` và `quantities[]`, phải kiểm tra độ dài tương ứng trước khi dùng cùng chỉ số. Khi đầu vào không bảo đảm có cùng số phần tử, đừng bỏ qua kiểm tra và trông chờ stream tự biết ý nghĩa quan hệ giữa hai mảng.

```java
int[] prices = {100, 200, 300};
int[] quantities = {2, 3, 1};
if (prices.length != quantities.length) {
    throw new IllegalArgumentException("Array lengths differ");
}
long revenue = java.util.stream.IntStream.range(0, prices.length)
        .mapToLong(i -> (long) prices[i] * quantities[i])
        .sum(); // 1100
```

Phép cast `(long) prices[i]` đặt **trước phép nhân** để nhân theo `long`. Nếu viết `(long) (prices[i] * quantities[i])`, phép nhân `int` có thể đã tràn trước khi bị ép kiểu. Tất nhiên tổng nhiều tích cũng phải nằm trong miền `long` nếu dùng `.sum()` kiểu đó.

## 4. Chuyển kiểu trước phép toán có thể mất dữ liệu

Việc đưa mảng `int[]` qua `Arrays.stream(...).sum()` tạo tổng kiểu `int`, có thể tràn. Khi tổng toán học của một `int[]` có thể vượt miền `int`, hãy nâng từng phần tử thành `long` **trước khi cộng**:

```java
int[] values = {2_000_000_000, 2_000_000_000};
int wrapped = java.util.Arrays.stream(values).sum(); // -294967296
long widened = java.util.Arrays.stream(values).asLongStream().sum(); // 4000000000
```

Với một mảng `int[]` thực tế (độ dài và phần tử đều trong giới hạn của Java), tổng toán học vẫn vừa miền `long`; nhưng tổng của `long[]` hoặc một luồng vô hạn/gom qua nhiều batch có thể vượt `long`. Khi cần tổng nguyên vượt miền `long`, dùng `BigInteger`. Bài **sum có thể tràn đối với số nguyên** phân biệt chi tiết phát hiện tràn từng bước và kiểm tra giới hạn tổng cuối.

Chuyển số nguyên `long` lớn sang `double` cũng có rủi ro: `double` chỉ bảo toàn liên tiếp mọi số nguyên đến `2^53`. Không chuyển dữ liệu ID/số đếm lớn qua `DoubleStream` chỉ vì muốn gọi phép trung bình khi yêu cầu là chính xác đến từng đơn vị.

## 5. Các terminal operation và ý nghĩa dữ liệu rỗng

| Terminal operation | `IntStream` | Khi rỗng |
| --- | --- | --- |
| `sum()` | `int` | `0` |
| `count()` | `long` | `0` |
| `average()` | `OptionalDouble` | `OptionalDouble.empty()` |
| `min()` / `max()` | `OptionalInt` | `OptionalInt.empty()` |
| `summaryStatistics()` | `IntSummaryStatistics` | `getCount()==0`, các cực trị là sentinel |
| `toArray()` | `int[]` | mảng rỗng |

Đối với `LongStream` và `DoubleStream`, các phép tương tự trả kiểu `long`/`double` hoặc `OptionalLong`/`OptionalDouble` phù hợp. `sum()` của dãy rỗng bằng 0 theo quy ước reduction; **trung bình hoặc cực trị của dãy rỗng không có giá trị số**. Vì vậy `average().orElse(0.0)` chỉ nên dùng nếu domain chủ động định nghĩa mặc định là 0.

`summaryStatistics()` cho count/sum/min/max/average trong **một lượt tiêu thụ stream**, hữu ích khi nguồn không dễ duyệt nhiều lần. Chú ý `IntSummaryStatistics.getSum()` trả `long`; với `LongSummaryStatistics` trường tổng vẫn là `long`. Khi `getCount() == 0`, không diễn giải `getMin()` hoặc `getMax()` là phép đo thực. Những ranh giới này được trình bày kỹ trong các bài về average, min/max và SummaryStatistics.

## 6. Ví dụ độc lập trên Java 17+

Tạo file `NumericArraysDemo.java`:

```java
import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class NumericArraysDemo {
    public static void main(String[] args) {
        int[] counts = {3, 1, 4, 1, 5};
        long[] distances = {3_000_000_000L, 4_000_000_000L};
        double[] temperatures = {19.5, 20.0, 21.5};

        System.out.println("array sum=" + Arrays.stream(counts).sum());
        System.out.println("long sum=" + Arrays.stream(distances).sum());
        System.out.println("double mean="
                + Arrays.stream(temperatures).average().orElseThrow());
        System.out.println("array as one=" + Stream.of(counts).count());
        System.out.println("primitive elements=" + Arrays.stream(counts).count());

        int[] indices = IntStream.range(0, counts.length)
                .filter(i -> counts[i] >= 4)
                .toArray();
        System.out.println("high indices=" + Arrays.toString(indices));

        int[] big = {2_000_000_000, 2_000_000_000};
        System.out.println("wrapped sum=" + Arrays.stream(big).sum());
        System.out.println("widened sum=" + Arrays.stream(big).asLongStream().sum());

        IntSummaryStatistics stats = Arrays.stream(counts).summaryStatistics();
        System.out.println("stats=" + stats.getCount() + "/"
                + stats.getSum() + "/" + stats.getMin() + "/"
                + stats.getMax() + "/" + stats.getAverage());
        OptionalDouble empty = Arrays.stream(new int[0]).average();
        System.out.println("empty average present=" + empty.isPresent());
    }
}
```

```bash
javac --release 17 NumericArraysDemo.java
java NumericArraysDemo
```

```text
array sum=14
long sum=7000000000
double mean=20.333333333333332
array as one=1
primitive elements=5
high indices=[2, 4]
wrapped sum=-294967296
widened sum=4000000000
stats=5/14/1/5/2.8
empty average present=false
```

Để kiểm chứng một kết quả, hãy tách câu hỏi thành **nguồn là mảng nào**, **pipeline xử lý theo giá trị hay chỉ số**, **kiểu tính toán tại mỗi phép toán**, và **trường hợp nguồn rỗng**. `DoubleStream.average()` có thể làm tròn theo binary64; đừng nhầm chữ số output với decimal exact.

## 7. Chọn cách duyệt theo bài toán

- **Mảng và vòng lặp:** thuận tiện khi cần sửa phần tử tại chỗ, truy cập chỉ số hoặc quản lý trạng thái theo thứ tự tường minh.
- **Primitive stream:** thuận tiện khi cần chuỗi lọc/biến đổi/tổng hợp không đòi boxing từng phần tử.
- **`IntStream.range` theo chỉ số:** thuận tiện khi cần vị trí hoặc tương quan giữa nhiều mảng; phải xác thực kích thước và tránh sửa nguồn từ lambda.
- **`summaryStatistics()`:** thuận tiện khi cần nhiều thống kê trong cùng một lần tiêu thụ; phải xác nhận số mẫu và giới hạn số học.

Trong pipeline song song, không mutate mảng/biến cộng dồn dùng chung và không kỳ vọng `parallel()` tự tăng tốc với mảng nhỏ. Với tổng `double`, cách phân nhóm có thể làm thay đổi các bit cuối; với yêu cầu decimal exact dùng loại dữ liệu và chính sách rounding phù hợp.

## 8. Câu hỏi tự kiểm tra

1. `Stream.of(new int[] {1,2,3}).count()` và `Arrays.stream(new int[] {1,2,3}).count()` khác nhau vì sao?
2. Làm sao lấy chỉ số của phần tử chẵn mà không nhầm chỉ số với giá trị?
3. Trong tổng doanh thu `price * quantity`, cast phải đứng ở đâu để phép nhân thực hiện bằng `long`?
4. Vì sao `long total = Arrays.stream(intArray).sum()` không sửa được tổng `int` đã bị tràn?
5. Khi `average()` rỗng, giữ Optional, ném lỗi hay hiển thị zero nên được quyết định bởi yếu tố nào?
6. Khi cần cả min/max/count trên một nguồn chỉ duyệt một lần, dùng API nào và cần guard điều kiện gì?

**Ghi nhớ:** Dạng mảng quyết định loại primitive stream; việc sử dụng một stream chỉ là một lượt xử lý trên nguồn. Với bài toán tổng hợp số, tính đúng đòi hỏi **chọn nguồn, chỉ số, kiểu tính toán và chính sách nguồn rỗng** rõ ràng trước khi tối ưu pipeline.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-002">IntStream, LongStream, DoubleStream và giảm boxing</a>

<details>
<summary>Xem chi tiết</summary>

# IntStream, LongStream, DoubleStream và cách giảm boxing khi tổng hợp số trong Java

## 1. Vấn đề: tổng hợp số có cần tạo đối tượng Integer, Long, Double?

Java Stream API cho phép xử lý tập dữ liệu bằng các phép biến đổi như `filter`, `map` và các phép tổng hợp như `sum`, `average`, `min`, `max`. Khi phần tử là giá trị nguyên thủy (*primitive*) `int`, `long` hoặc `double`, Java cung cấp ba loại stream chuyên biệt:

- `IntStream` xử lý phần tử kiểu `int`.
- `LongStream` xử lý phần tử kiểu `long`.
- `DoubleStream` xử lý phần tử kiểu `double`.

Ba kiểu đều nằm trong package `java.util.stream`. Khác với `Stream<Integer>`, `Stream<Long>` hoặc `Stream<Double>`, phần tử của chúng được truyền trong các thao tác chuyên biệt dưới dạng giá trị primitive. Vì thế chúng **có thể giảm việc boxing/unboxing và số đối tượng trung gian** khi tính tổng, trung bình hay tìm cực trị.

**Boxing** là chuyển primitive thành wrapper, như `int → Integer`. **Unboxing** là lấy primitive từ wrapper, như `Integer → int`. Java có thể tự động thực hiện hai chuyển đổi này, nhưng chúng vẫn là các thao tác có chi phí và có thể phát sinh đối tượng hoặc lỗi `NullPointerException` khi unbox giá trị `null`.

Ví dụ cùng một mục tiêu tính tổng:

~~~java
import java.util.List;
import java.util.stream.IntStream;

List<Integer> numbers = List.of(10, 20, 30);

// Đã có List<Integer>: chuyển một lần sang IntStream để tính toán primitive.
int sumFromList = numbers.stream()
        .mapToInt(Integer::intValue)
        .sum(); // 60

// Tạo dữ liệu bằng primitive ngay từ nguồn.
int sumFromPrimitive = IntStream.of(10, 20, 30).sum(); // 60
~~~

Cả hai cho kết quả 60. Lựa chọn tối ưu phụ thuộc nguồn dữ liệu: nếu dữ liệu đã được chứa trong `List<Integer>` thì việc unbox tại ranh giới là cần thiết; nếu có thể tạo và giữ dữ liệu primitive từ đầu, `IntStream` tránh được bước tạo wrapper chỉ để tính tổng.

## 2. Phân biệt Stream<T> và ba primitive stream

| Khía cạnh | `Stream<Integer>` (tương tự Long/Double) | `IntStream` | `LongStream` | `DoubleStream` |
|---|---|---|---|---|
| Kiểu phần tử | Đối tượng `Integer` | `int` | `long` | `double` |
| Có `sum()`? | Không | Có, trả `int` | Có, trả `long` | Có, trả `double` |
| Có `average()`? | Không | `OptionalDouble` | `OptionalDouble` | `OptionalDouble` |
| `min()`, `max()` | `Optional<T>` khi có comparator/natural order | `OptionalInt` | `OptionalLong` | `OptionalDouble` |
| `summaryStatistics()` | Không có phương thức tương ứng trực tiếp | `IntSummaryStatistics` | `LongSummaryStatistics` | `DoubleSummaryStatistics` |
| `toArray()` | `T[]` nếu dùng constructor thích hợp; không đối số trả `Object[]` | `int[]` | `long[]` | `double[]` |
| `boxed()` | Không cần vì đã là stream đối tượng | `Stream<Integer>` | `Stream<Long>` | `Stream<Double>` |
| `map(...)` | Biến đổi đối tượng → đối tượng | `int → int` | `long → long` | `double → double` |

`Stream<T>` là abstraction cho đối tượng tham chiếu; nó không cung cấp `sum()` vì một `T` bất kỳ không nhất thiết là số, cũng không có một kiểu kết quả tổng mặc định. Trong khi đó, stream primitive biết sẵn kiểu của phần tử nên cung cấp phép toán số học chuyên biệt.

Một điểm cần chú ý: `IntStream` không thể chứa `null`. Điều này loại bỏ lỗi null do chính phần tử primitive, nhưng nếu nguồn là `Stream<Integer>` có `null`, thao tác `mapToInt(Integer::intValue)` vẫn có thể ném `NullPointerException` khi unbox.

Các primitive stream chỉ chuyên biệt cho ba kiểu `int`, `long`, `double`. Không có `ByteStream`, `ShortStream`, `CharStream` hoặc `FloatStream` trong Stream API chuẩn. Các giá trị `byte`, `short`, `char` có thể được đẩy sang `IntStream` khi phù hợp; `float` thường được xử lý bằng `DoubleStream` nếu cần phép toán số thực.

## 3. Cách tạo stream primitive

### 3.1. Từ các giá trị, mảng primitive

~~~java
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;

IntStream ints = IntStream.of(2, 4, 6);
LongStream longs = LongStream.of(1_000_000_000L, 2_000_000_000L);
DoubleStream doubles = DoubleStream.of(1.5, 2.5, 3.5);

int[] readings = {4, 6, 8};
int total = Arrays.stream(readings).sum(); // 18
~~~

`Arrays.stream(int[])` trả `IntStream`. Tương tự, `Arrays.stream(long[])` trả `LongStream` và `Arrays.stream(double[])` trả `DoubleStream`. Đây là con đường tự nhiên khi dữ liệu đã nằm trong mảng primitive.

Dễ mắc lỗi với `Stream.of(array)`:

~~~java
int[] readings = {4, 6, 8};

long a = Arrays.stream(readings).count(); // 3
long b = java.util.stream.Stream.of(readings).count(); // 1
~~~

`Stream.of(readings)` trong ví dụ này là stream chứa **một đối tượng mảng `int[]`**, không phải ba số nguyên. Muốn duyệt các phần tử số, dùng `Arrays.stream(readings)` hoặc `IntStream.of(readings)`.

### 3.2. Tạo dãy liên tiếp với range, rangeClosed

~~~java
IntStream.range(1, 5);         // 1, 2, 3, 4
IntStream.rangeClosed(1, 5);   // 1, 2, 3, 4, 5
LongStream.range(10L, 13L);   // 10, 11, 12
LongStream.rangeClosed(10L, 13L); // 10, 11, 12, 13
~~~

`range(startInclusive, endExclusive)` gồm đầu, không gồm cuối; `rangeClosed(startInclusive, endInclusive)` gồm cả hai đầu. Hai họ phương thức này có trên `IntStream` và `LongStream`; `DoubleStream` **không có** `range`/`rangeClosed`.

`range` rất hữu ích cho chỉ số mảng, vòng lặp đếm và dữ liệu có khoảng liên tục, nhưng lưu ý tính đúng của biên trước khi thay thế vòng lặp thông thường.

### 3.3. Từ Stream đối tượng qua mapToInt, mapToLong, mapToDouble

~~~java
import java.util.List;
import java.util.stream.Stream;

List<String> texts = List.of("7", "10", "13");

int sum = texts.stream()
        .mapToInt(Integer::parseInt)
        .sum(); // 30

long totalLengths = Stream.of("Java", "Stream")
        .mapToLong(s -> s.length())
        .sum(); // 10

double average = Stream.of("1.5", "2.5", "3.5")
        .mapToDouble(Double::parseDouble)
        .average()
        .orElseThrow(); // 2.5
~~~

Các toán tử `mapToInt`/`mapToLong`/`mapToDouble` là ranh giới chuyển từ stream đối tượng sang stream primitive. Sau chuyển đổi, phép lọc, ánh xạ và tổng hợp số có thể diễn ra mà không phải liên tục chuyển đổi từng phần tử sang wrapper.

## 4. Các phép tổng hợp số: sum, average, min, max, count

Ví dụ:

~~~java
int sum = IntStream.of(2, 4, 6).sum(); // 12
double average = IntStream.of(2, 4, 6).average().orElseThrow(); // 4.0
int minimum = IntStream.of(2, 4, 6).min().orElseThrow(); // 2
int maximum = IntStream.of(2, 4, 6).max().orElseThrow(); // 6
long count = IntStream.of(2, 4, 6).count(); // 3
~~~

`count()` trả `long`, không phải `int`. `average()` trên cả ba primitive stream đều trả `OptionalDouble`, vì trung bình của các số nguyên có thể là phân số: ví dụ trung bình của `1` và `2` là `1.5`.

`min()`/`max()` trả `OptionalInt`, `OptionalLong` hoặc `OptionalDouble` tương ứng; chúng không trả trực tiếp primitive vì stream có thể rỗng.

### 4.1. Xử lý stream rỗng đúng cách

~~~java
import java.util.OptionalDouble;
import java.util.stream.IntStream;

int sum = IntStream.empty().sum();       // 0
long count = IntStream.empty().count();  // 0
OptionalDouble avg = IntStream.empty().average(); // OptionalDouble.empty()
~~~

Với `min()`, `max()` và `average()`, hãy chọn cách xử lý stream rỗng theo nghiệp vụ:

~~~java
double fallback = IntStream.empty().average().orElse(0.0); // 0.0

// Chỉ dùng khi tập dữ liệu bắt buộc không rỗng.
// double required = IntStream.empty().average().orElseThrow();
~~~

`orElse(0.0)` là quyết định riêng của ứng dụng: về mặt toán học, trung bình của tập rỗng không phải 0. Nếu 0 có thể bị hiểu thành kết quả hợp lệ, tốt hơn nên giữ `OptionalDouble` hoặc báo lỗi rõ ràng.

### 4.2. summaryStatistics: tổng hợp nhiều chỉ số trong một lượt

Thay vì tạo nhiều stream riêng cho `count`, `sum`, `min`, `max` và `average`, có thể lấy một đối tượng thống kê:

~~~java
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

IntSummaryStatistics stats = IntStream.of(2, 4, 6).summaryStatistics();

System.out.println(stats.getCount());   // 3
System.out.println(stats.getSum());     // 12
System.out.println(stats.getMin());     // 2
System.out.println(stats.getMax());     // 6
System.out.println(stats.getAverage()); // 4.0
~~~

Tương tự:

- `LongStream.summaryStatistics()` trả `LongSummaryStatistics`.
- `DoubleStream.summaryStatistics()` trả `DoubleSummaryStatistics`.

Các lớp thống kê cung cấp tổng, số lượng, cực trị và trung bình. Đối với stream rỗng, các giá trị cực trị và trung bình của summary-statistics dùng giá trị mặc định theo API; cần **kiểm tra `getCount()`** trước khi coi kết quả là một cực trị/trung bình có ý nghĩa.

`summaryStatistics()` cũng là một **terminal operation**: nó tiêu thụ stream. Không thể gọi lại `sum()` trên cùng một instance stream đã dùng.

## 5. Biến đổi primitive rồi tổng hợp: filter, map, reduce

Primitive stream có các thao tác trung gian `filter`, `map`, `flatMap`, `distinct`, `sorted`, `limit`, `skip` và nhiều thao tác khác với hàm nhận primitive:

~~~java
int totalEvenSquares = IntStream.rangeClosed(1, 5)
        .filter(n -> n % 2 == 0) // 2, 4
        .map(n -> n * n)         // 4, 16
        .sum();                  // 20
~~~

Trong chuỗi này, `filter` và `map` làm việc với `int` qua các functional interface chuyên biệt `IntPredicate`, `IntUnaryOperator`. Dữ liệu không phải biến thành `Integer` giữa các thao tác.

Một số kiểu callback quan trọng:

| Primitive stream | `filter` nhận | `map` nhận | `forEach` nhận |
|---|---|---|---|
| `IntStream` | `IntPredicate` | `IntUnaryOperator` | `IntConsumer` |
| `LongStream` | `LongPredicate` | `LongUnaryOperator` | `LongConsumer` |
| `DoubleStream` | `DoublePredicate` | `DoubleUnaryOperator` | `DoubleConsumer` |

### 5.1. reduce với phép gộp tùy chọn

~~~java
int product = IntStream.rangeClosed(1, 4)
        .reduce(1, (a, b) -> a * b); // 24

int biggest = IntStream.of(4, 9, 2)
        .reduce(Integer::max)
        .orElseThrow(); // 9
~~~

`reduce(identity, operator)` trả một primitive (có giá trị identity nếu stream rỗng). `reduce(operator)` không có identity trả một `OptionalInt`/`OptionalLong`/`OptionalDouble`. Dùng phép kết hợp **có tính kết hợp (associative)** và identity phù hợp để đúng cả khi stream chạy parallel. Ví dụ phép cộng với identity 0, phép nhân với identity 1; không dùng phép trừ như một phép reduce tổng quát nếu cần kết quả ổn định dưới phân hoạch song song.

Khi chỉ cần tổng, ưu tiên `sum()` hơn `reduce(0, Integer::sum)` vì mã rõ mục đích hơn.

### 5.2. Không phải lúc nào cũng cần reduce tùy biến

`sum`, `average`, `summaryStatistics` đã thể hiện các kiểu tổng hợp phổ biến. Nếu cần gom vào cấu trúc đặc biệt mà không muốn boxing từng phần tử, `IntStream` có overload `collect(supplier, accumulator, combiner)` với accumulator là `ObjIntConsumer<R>`:

~~~java
String joined = IntStream.of(2, 3, 5)
        .collect(
            StringBuilder::new,
            (builder, value) -> builder.append(value),
            (left, right) -> left.append(right)
        )
        .toString(); // "235"
~~~

Accumulator nhận `int` primitive. Với tác vụ song song, supplier tạo container riêng cho các nhánh và combiner phải ghép đúng, không dùng một container chia sẻ bên ngoài. Trường hợp chỉ nối chuỗi đơn giản chưa chắc cần collector tùy biến; ví dụ này minh họa cơ chế chuyên biệt.

## 6. Chuyển đổi qua lại: boxed(), mapToObj(), mapToInt()

Khi API phía sau cần đối tượng, hãy **boxing ở ranh giới thực sự cần thiết**.

### 6.1. Primitive stream → Stream wrapper bằng boxed()

~~~java
import java.util.List;
import java.util.stream.IntStream;

List<Integer> ids = IntStream.of(3, 1, 3, 2)
        .distinct()
        .sorted()
        .boxed()
        .toList(); // [1, 2, 3]
~~~

`boxed()` chuyển:

| Đầu vào | Kết quả |
|---|---|
| `IntStream.boxed()` | `Stream<Integer>` |
| `LongStream.boxed()` | `Stream<Long>` |
| `DoubleStream.boxed()` | `Stream<Double>` |

`toList()` trong ví dụ là terminal operation của `Stream<Integer>` (có từ Java 16); `IntStream` không có phương thức `toList()` để tạo `List<Integer>` trực tiếp. Bước `boxed()` là cần thiết khi kiểu đầu ra được yêu cầu là list các wrapper.

Chỉ nên chuyển sang wrapper khi thực sự cần đối tượng, ví dụ gọi API nhận `List<Integer>`. Nếu chỉ tính tổng thì `IntStream.of(...).sum()` gọn và tránh ranh giới boxing đó.

### 6.2. Primitive → kiểu đối tượng khác bằng mapToObj()

~~~java
List<String> labels = IntStream.of(2, 3, 5)
        .mapToObj(n -> "id-" + n)
        .toList(); // [id-2, id-3, id-5]
~~~

`mapToObj` khác `boxed`: nó ánh xạ primitive sang **đối tượng bất kỳ**. Tương tự, `LongStream` và `DoubleStream` cũng có `mapToObj`. Việc tạo `String` là chủ ý của nghiệp vụ, không phải boxing số nguyên không cần thiết.

### 6.3. Stream wrapper → primitive bằng mapToInt(), mapToLong(), mapToDouble()

~~~java
List<Integer> values = List.of(5, 10, 15);

int total = values.stream()
        .mapToInt(Integer::intValue)
        .sum(); // 30
~~~

Ở ranh giới này, Java cần unbox từng `Integer` thành `int`. Điều hữu ích là phần còn lại của pipeline có thể tiếp tục làm việc với primitive. Nếu danh sách có `null` thì `Integer::intValue` gây `NullPointerException`; nên xử lý nguồn có null rõ ràng, ví dụ lọc trước bằng `filter(java.util.Objects::nonNull)` nếu nghiệp vụ cho phép bỏ qua dữ liệu thiếu.

`IntStream` còn có `mapToLong`, `mapToDouble` để đổi loại primitive; `LongStream` có `mapToInt`, `mapToDouble`; `DoubleStream` có `mapToInt`, `mapToLong`. Các chuyển đổi *narrowing* từ `long`/`double` xuống `int` có thể mất thông tin, vì thế không coi chúng là thao tác luôn an toàn.

### 6.4. asLongStream(), asDoubleStream()

~~~java
long safeForThisSum = IntStream.of(Integer.MAX_VALUE, 1)
        .asLongStream()
        .sum(); // 2147483648

double mean = IntStream.of(1, 2)
        .asDoubleStream()
        .average()
        .orElseThrow(); // 1.5
~~~

`asLongStream()` trên `IntStream` là phép *widening* giữ chính xác giá trị `int`. `IntStream.asDoubleStream()` cũng giữ chính xác mọi giá trị `int` (vì `double` biểu diễn chính xác các số nguyên 32 bit). Nhưng `LongStream.asDoubleStream()` **có thể mất độ chính xác** đối với `long` lớn hơn ngưỡng chính xác nguyên của `double` (khoảng `2^53`). Không dùng chuyển đổi đó cho phép tính đòi hỏi giữ chính xác từng đơn vị với số nguyên lớn.

## 7. Chương trình Java 17+ hoàn chỉnh và kết quả kiểm chứng

Tạo tệp `PrimitiveStreamsDemo.java`:

~~~java
import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class PrimitiveStreamsDemo {
    public static void main(String[] args) {
        int[] readings = {2, 4, 6, 8};
        System.out.println("array sum=" + Arrays.stream(readings).sum());
        System.out.println("range=" + IntStream.range(1, 5).sum());
        System.out.println("rangeClosed=" + LongStream.rangeClosed(1, 5).sum());

        IntSummaryStatistics stats =
                IntStream.of(2, 4, 6).summaryStatistics();
        System.out.println("stats count=" + stats.getCount()
                + ", sum=" + stats.getSum()
                + ", avg=" + stats.getAverage());

        System.out.println("max=" +
                IntStream.of(8, 2, 5).max().orElseThrow());
        System.out.println("empty average present=" +
                IntStream.empty().average().isPresent());

        List<Integer> ids = IntStream.of(3, 1, 3, 2)
                .distinct().sorted().boxed().toList();
        System.out.println("boxed ids=" + ids);

        List<String> labels = IntStream.of(2, 3, 5)
                .mapToObj(i -> "id-" + i).toList();
        System.out.println("labels=" + labels);

        System.out.println("parsed sum=" +
                Stream.of("7", "10", "13")
                        .mapToInt(Integer::parseInt).sum());

        System.out.println("long sum=" +
                IntStream.of(Integer.MAX_VALUE, 1)
                        .asLongStream().sum());
        System.out.println("int sum=" +
                IntStream.of(Integer.MAX_VALUE, 1).sum());

        System.out.println("double total=" +
                DoubleStream.of(1.5, 2.5, 3.5).sum());
        System.out.println("double avg=" +
                DoubleStream.of(1.5, 2.5, 3.5)
                        .average().orElseThrow());

        String joined = IntStream.of(2, 3, 5)
                .collect(
                        StringBuilder::new,
                        (builder, value) -> builder.append(value),
                        (left, right) -> left.append(right))
                .toString();
        System.out.println("collected=" + joined);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PrimitiveStreamsDemo.java
java PrimitiveStreamsDemo
~~~

Kết quả stdout:

~~~text
array sum=20
range=10
rangeClosed=15
stats count=3, sum=12, avg=4.0
max=8
empty average present=false
boxed ids=[1, 2, 3]
labels=[id-2, id-3, id-5]
parsed sum=30
long sum=2147483648
int sum=-2147483648
double total=7.5
double avg=2.5
collected=235
~~~

Chương trình minh họa các ranh giới quan trọng. `Arrays.stream(int[])` và `IntStream.range` tạo luồng primitive; `summaryStatistics` tổng hợp các chỉ số trong một lần tiêu thụ; `boxed()` chỉ xuất hiện tại nơi cần `List<Integer>`; `mapToObj` tạo đối tượng `String` khi cần. Hai dòng `long sum` và `int sum` cố ý cho thấy tràn số `int` trong một phép tổng hợp mặc định.

## 8. Kiểm soát overflow khi tổng hợp số nguyên

Dùng stream primitive **không tự động bảo đảm tổng luôn nằm trong kiểu kết quả**.

~~~java
int overflowed = IntStream.of(Integer.MAX_VALUE, 1).sum();
// -2147483648 do tràn int

long widenedFirst = IntStream.of(Integer.MAX_VALUE, 1)
        .asLongStream()
        .sum();
// 2147483648
~~~

`IntStream.sum()` trả `int` nên có thể overflow theo quy tắc số nguyên Java; `LongStream.sum()` cũng có thể overflow với dữ liệu `long` đủ lớn. Không phải lúc nào `asLongStream()` cũng giải quyết được mọi bài toán: nó chỉ mở rộng giới hạn từ 32 bit sang 64 bit.

Quan trọng là **mở rộng trước khi thực hiện phép toán có thể tràn**, không phải cast sau:

~~~java
long wrong = IntStream.of(50_000, 60_000)
        .map(n -> n * n)        // phép nhân trong int: đã tràn
        .asLongStream()
        .sum();

long correct = IntStream.of(50_000, 60_000)
        .mapToLong(n -> (long) n * n)
        .sum(); // 6100000000
~~~

Cast `(long) n` được đặt **trước phép nhân** nên toán tử nhân tính ở `long`. Với dữ liệu có thể vượt cả phạm vi `long`, cần thiết kế phép tổng hợp dùng `BigInteger` hoặc xử lý giới hạn theo yêu cầu.

Khi cần **phát hiện** overflow thay vì để giá trị wrap, có thể dùng `Math.addExact`:

~~~java
int exact = IntStream.of(1, 2, 3)
        .reduce(0, Math::addExact); // 6

// IntStream.of(Integer.MAX_VALUE, 1)
//          .reduce(0, Math::addExact);
// Ném ArithmeticException khi phép cộng vượt miền int.
~~~

Đối với một pipeline song song, các phép reduce phải thỏa điều kiện về phép kết hợp; phép cộng chính xác bằng `Math.addExact` có thể ném ngoại lệ trong quá trình gộp nếu một bước vượt giới hạn. Hãy xác định rõ bạn muốn kết quả kiểu `int`, `long`, số nguyên không giới hạn, hay hành vi lỗi khi tràn.

## 9. DoubleStream có tốc độ và đặc tính số thực riêng

`DoubleStream` phù hợp với dữ liệu đo lường hoặc số thực mà độ chính xác xấp xỉ IEEE 754 được chấp nhận:

~~~java
double sum = DoubleStream.of(1.5, 2.5, 3.5).sum(); // 7.5
double average = DoubleStream.of(1.5, 2.5, 3.5)
        .average().orElseThrow(); // 2.5
~~~

Tuy nhiên `double` **không thể biểu diễn chính xác mọi phân số thập phân**, chẳng hạn `0.1`. `DoubleStream.sum()` có cơ chế cộng bù (*compensated summation*) nhằm giảm sai số so với cộng ngây thơ trong nhiều tình huống, nhưng **không bảo đảm** kết quả thập phân chính xác như một phép tính `BigDecimal`.

Ví dụ diễn giải:

~~~java
double a = 0.1;
double b = 0.2;
System.out.println(a + b == 0.3); // false trên phép cộng double thông thường
~~~

Không nên dùng ví dụ này để khẳng định một chuỗi `DoubleStream.sum()` cụ thể luôn tạo cùng chuỗi thập phân với phép cộng trực tiếp: thuật toán gộp có thể khác. Bài học là phải chọn kiểu số phù hợp với yêu cầu chính xác.

Nếu xử lý số tiền hoặc các đại lượng có quy định thập phân chính xác, `BigDecimal` thường là lựa chọn thích hợp hơn; khi đó pipeline đối tượng có thể là chủ ý đúng đắn, và lợi ích giảm boxing không quan trọng bằng bảo đảm tính đúng của phép toán.

Một số khác biệt khác cần biết:

- `DoubleStream` có thể chứa `NaN`, `Infinity` và `-Infinity`; cách các giá trị này lan truyền trong tổng hợp cần được quy định ở ứng dụng.
- `DoubleStream.min()`/`max()` trả `OptionalDouble`. Không dùng kết quả nếu rỗng mà thiếu kiểm tra.
- `double` mất khả năng biểu diễn chính xác mọi số nguyên lớn hơn `2^53`. Tránh chuyển số lượng `long` lớn thành `double` chỉ để dùng `average()` nếu nghiệp vụ cần giữ nguyên từng đơn vị.
- Trong phép cộng số thực, thứ tự gộp có thể ảnh hưởng chữ số thấp của kết quả. Chạy parallel có thể thay đổi thứ tự tính, vì vậy so sánh số thực thường cần ngưỡng sai số thay vì `==` khi nghiệp vụ cho phép.

## 10. flatMapToInt và xử lý nhiều mảng primitive

Một dữ liệu đầu vào có thể là nhiều mảng `int[]` nằm trong một `Stream<int[]>`. Không cần boxing từng phần tử để nối chúng:

~~~java
import java.util.Arrays;
import java.util.stream.Stream;

int total = Stream.of(
        new int[] {1, 2},
        new int[] {3, 4, 5}
    )
    .flatMapToInt(Arrays::stream)
    .sum(); // 15
~~~

`flatMapToInt` lấy từng `int[]`, tạo `IntStream` tương ứng rồi nối các phần tử primitive. Tương tự, `flatMapToLong` và `flatMapToDouble` áp dụng khi kết quả ánh xạ là `LongStream` hoặc `DoubleStream`.

Nếu nguồn đã là `IntStream` và mỗi giá trị phải sinh ra nhiều `int`, chính `IntStream.flatMap` nhận hàm trả `IntStream`:

~~~java
int sum = IntStream.of(1, 2, 3)
        .flatMap(n -> IntStream.of(n, n * 10))
        .sum(); // 66
~~~

Đây là mô hình hữu ích khi trải phẳng dữ liệu số trước khi tổng hợp mà không cần dựng `List<Integer>` trung gian.

## 11. Parallel stream: có thể nhanh hơn, nhưng không phải mặc định

Các primitive stream hỗ trợ `parallel()`:

~~~java
long sum = IntStream.rangeClosed(1, 1_000_000)
        .asLongStream()
        .parallel()
        .sum(); // 500000500000
~~~

Phép tính này hợp lệ vì tổng `long` trong ví dụ không overflow, và việc cộng số nguyên có tính kết hợp phù hợp cho phân hoạch dữ liệu. Tuy nhiên `parallel()` **không mặc nhiên nhanh hơn**; với đầu vào nhỏ hoặc mỗi phần tử xử lý quá nhẹ, chi phí chia việc, lập lịch và gộp có thể cao hơn lợi ích.

Đừng thay một pipeline tuần tự nhỏ bằng parallel chỉ vì nó dùng primitive. Cũng không nên ghi vào biến cộng dồn dùng chung trong `forEach`:

~~~java
// Không an toàn khi chạy parallel vì cập nhật biến chung không đồng bộ:
int[] total = {0};
IntStream.rangeClosed(1, 100).parallel()
        .forEach(n -> total[0] += n);
~~~

Kết quả ví dụ trên có thể sai do data race. Hãy dùng `sum()`, `reduce()` hoặc collector có supplier/accumulator/combiner hợp lệ. Với `forEach` trên parallel stream, thứ tự thực thi không được bảo đảm; `forEachOrdered` giữ thứ tự gặp phần tử khi cần nhưng có thể làm giảm lợi ích song song.

Các primitive stream, giống `Stream<T>`, thường **chỉ được tiêu thụ một lần**. Sau một terminal operation (`sum`, `toArray`, `count`, `collect`...), muốn tính chỉ số khác cần tạo stream mới hoặc lấy nhiều thống kê trong một lượt bằng `summaryStatistics`.

## 12. Giảm boxing thế nào cho đúng, và khi nào nên đo hiệu năng?

So sánh hai pipeline cùng mục tiêu:

~~~java
// Có thể tạo các giá trị wrapper ở nhiều bước.
int totalA = IntStream.rangeClosed(1, 1000)
        .boxed()
        .map(n -> n * 2)
        .reduce(0, Integer::sum);

// Giữ primitive xuyên suốt.
int totalB = IntStream.rangeClosed(1, 1000)
        .map(n -> n * 2)
        .sum();
~~~

Cả hai trả kết quả `1001000`. Pipeline thứ hai mô tả ý định tính toán rõ hơn và tránh chuyển phần tử thành `Integer` trước khi `map`/`reduce`. Tuy nhiên không suy luận một tỉ lệ tăng tốc cố định: JVM có tối ưu JIT, cache wrapper trong một số trường hợp, escape analysis và chi phí tối ưu khác tùy workload.

Các quy tắc thực hành:

1. **Bắt đầu từ nguồn primitive nếu có thể:** `int[]`, `long[]`, `double[]`, `IntStream.range`.
2. **Giữ primitive trong phần xử lý số học:** `filter`, `map`, `sum`, `average`, `summaryStatistics`.
3. **Chuyển khi cần:** dùng `mapToInt` cho dữ liệu đối tượng đầu vào; dùng `boxed` khi đầu ra bắt buộc là wrapper/list; dùng `mapToObj` khi cần một đối tượng nghiệp vụ.
4. **Không hy sinh tính đúng:** chọn `long` hoặc `BigInteger` khi `int` có thể tràn; chọn `BigDecimal` khi cần tính thập phân chính xác.
5. **Đo ở môi trường phù hợp:** với hệ thống quan trọng hiệu năng, dùng profiler và microbenchmark đáng tin cậy (thường dùng JMH) trên dữ liệu và kích thước thực tế; tránh kết luận từ một lần chạy nhỏ hoặc đo qua `System.currentTimeMillis()`.

Mục tiêu của primitive stream không phải "xóa sạch mọi đối tượng" trong JVM. Mục tiêu là tránh boxing/unboxing của **từng phần tử số** tại những điểm không cần thiết, đồng thời có API tổng hợp số rõ ràng, dễ kiểm tra.

## 13. Những lỗi thường gặp

| Lỗi | Hậu quả | Hướng xử lý |
|---|---|---|
| `Stream.of(intArray)` để tính tổng các phần tử | Stream chứa một mảng, không phải nhiều số | `Arrays.stream(intArray).sum()` |
| Gọi `sum()` trực tiếp trên `Stream<Integer>` | Không có phương thức `sum()` | `mapToInt(Integer::intValue).sum()` |
| Gọi `toList()` trên `IntStream` | Không có API trả `List<Integer>` như vậy | `boxed().toList()` nếu cần list |
| Boxing sớm trước chuỗi tính toán dài | Thêm chuyển đổi và có thể tăng cấp phát wrapper | Giữ primitive tới ranh giới object |
| Unbox `Integer` có `null` | `NullPointerException` | Xử lý null theo nghiệp vụ trước `mapToInt` |
| Cho rằng `IntStream.sum()` không thể overflow | Tổng có thể wrap ra số âm | Mở rộng sớm sang long, hoặc kiểm tra bằng `Math.addExact` |
| Cast sang long **sau** khi nhân int | Tích đã overflow trước cast | Ép kiểu `(long) n * n` ngay trong `mapToLong` |
| Gọi `max().getAsInt()` trên stream rỗng | `NoSuchElementException` | Kiểm tra `isPresent` hoặc `orElseThrow` theo contract |
| Dùng `DoubleStream` cho tiền cần thập phân chính xác | Sai số số thực | Dùng `BigDecimal` và quy tắc làm tròn rõ |
| Kỳ vọng parallel luôn nhanh | Chi phí đồng bộ/phân hoạch lớn | Benchmark và chỉ parallel khi đáng giá |
| Tích lũy vào biến chung trong parallel `forEach` | Data race, kết quả không xác định | Dùng `sum`, `reduce`, collector đúng |
| Gọi `sum()` rồi `average()` trên cùng stream | `IllegalStateException` vì stream đã tiêu thụ | Tạo stream mới hoặc `summaryStatistics` |
| Chuyển `long` rất lớn sang `double` để làm tổng/trung bình | Có thể mất độ chính xác nguyên | Giữ `long`/`BigInteger` hoặc chọn kỹ thuật chính xác thích hợp |

## 14. Bài tập tự kiểm tra

**Bài 1.** Viết tổng các số chẵn từ 1 đến 10 mà không boxing.

**Đáp án:**

~~~java
int sum = IntStream.rangeClosed(1, 10)
        .filter(n -> n % 2 == 0)
        .sum(); // 30
~~~

**Bài 2.** Với mảng `int[] data = {3, 6, 9}`, vì sao `Stream.of(data).count()` trả 1 còn `Arrays.stream(data).count()` trả 3?

**Đáp án:** `Stream.of(data)` coi cả `int[]` là một phần tử đối tượng; `Arrays.stream(data)` tạo `IntStream` gồm ba phần tử primitive.

**Bài 3.** Tổng `Integer.MAX_VALUE + 1` nên dùng thao tác nào để tránh overflow `int` trong phép tổng hợp này?

**Đáp án:** `IntStream.of(Integer.MAX_VALUE, 1).asLongStream().sum()` trả `2147483648`; không dùng `IntStream.sum()` cho trường hợp đó.

**Bài 4.** `IntStream.of(1, 2, 3).average()` trả loại gì? Nếu stream rỗng thì sao?

**Đáp án:** `OptionalDouble`, có giá trị trung bình `2.0` khi có phần tử. Với stream rỗng là `OptionalDouble.empty()`; người gọi quyết định fallback hoặc báo lỗi.

**Bài 5.** Sau `IntStream.range(0, 4).sum()` có thể tiếp tục dùng cùng stream để tính `max()` không?

**Đáp án:** Không, stream đã kết thúc sau terminal operation. Hãy tạo stream mới, hoặc nếu cần nhiều chỉ số trong một lượt thì dùng `summaryStatistics()`.

**Bài 6.** Khi đầu ra phải là `List<Integer>` có tăng dần từ 1 đến 5, có phải boxing không?

**Đáp án:** Có vì `List<Integer>` chứa đối tượng wrapper. Ví dụ `IntStream.rangeClosed(1, 5).boxed().toList()`. Điều cần tránh là boxing sớm hơn nhu cầu đầu ra.

**Bài 7.** Kết quả của `IntStream.of(50_000, 60_000).mapToLong(n -> (long)n * n).sum()` bằng bao nhiêu?

**Đáp án:** `6_100_000_000L`. Cast lên long trước phép nhân giữ chính xác hai bình phương và tổng trong giới hạn long.

---

**Ghi nhớ:** `IntStream`, `LongStream` và `DoubleStream` là những stream chuyên biệt cho tính toán số, cung cấp `sum`, `average`, `summaryStatistics` và nhiều phép toán trên phần tử primitive. **Giữ primitive trong phần tính toán, boxing khi API thực sự cần đối tượng**, đồng thời luôn kiểm tra overflow, độ chính xác số thực, trường hợp stream rỗng và yêu cầu nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-003">sum có thể tràn đối với số nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# `sum` có thể tràn đối với số nguyên trong Java

Khi tính tổng một dãy số nguyên, tất cả phần tử đều hợp lệ chưa chắc tổng cũng nằm trong phạm vi kiểu dữ liệu. Java sử dụng các kiểu số nguyên có độ rộng cố định. Nếu phép cộng `int` hoặc `long` vượt giới hạn, kết quả **quấn vòng (wrap around)**; Java không tự đổi sang kiểu lớn hơn và phép cộng thông thường không ném lỗi. Điều này áp dụng cho vòng lặp, `IntStream.sum()`, `LongStream.sum()`, `reduce` và nhiều collector.

Bài này giúp nhận biết nơi xảy ra tràn, chọn cách tổng hợp chính xác và định nghĩa hợp đồng API rõ ràng: cho phép tổng rộng hơn, kiểm tra từng phép cộng, hay trả về kết quả không giới hạn 64 bit.

## 1. Bản chất tràn số nguyên

| Kiểu | Số bit | Nhỏ nhất | Lớn nhất |
| --- | ---: | ---: | ---: |
| `byte` | 8 | -128 | 127 |
| `short` | 16 | -32.768 | 32.767 |
| `int` | 32 | -2.147.483.648 | 2.147.483.647 |
| `long` | 64 | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 |

`int` biểu diễn số nguyên có dấu bằng 32 bit; `long` bằng 64 bit. Khi cộng vượt miền, các bit kết quả được giữ theo modulo \(2^{32}\) hoặc \(2^{64}\). Bởi vậy:

```java
int a = Integer.MAX_VALUE;
int b = 1;
int wrapped = a + b; // -2147483648
```

Ở đây tổng toán học là `2147483648`, lớn hơn `Integer.MAX_VALUE`, còn `wrapped` trở thành `Integer.MIN_VALUE`. Tương tự, `Long.MAX_VALUE + 1L` cho `Long.MIN_VALUE`.

Phân biệt bốn đại lượng: **giới hạn của một phần tử**, **tổng toán học**, **kiểu dùng để cộng trung gian**, và **kiểu trả về**. Một danh sách có các số hợp lệ trong `int` có thể có tổng vượt `int` nếu đủ nhiều phần tử. Ngược lại, có trường hợp tổng cuối vừa `int` nhưng tổng trung gian theo một thứ tự cộng lại vượt `int`.

Lỗi đặc biệt phổ biến:

```java
int x = Integer.MAX_VALUE;
int y = 1;

long wrong = x + y;          // -2147483648: cộng bằng int trước khi gán
long correct = (long) x + y; //  2147483648: cộng bằng long
```

Kiểu của biến đích **không** quyết định kiểu của phép cộng đã thực hiện. Cần nâng kiểu **trước** phép cộng, hoặc dùng bộ tích lũy kiểu rộng ngay từ đầu.

## 2. `IntStream.sum()` và `LongStream.sum()` có thể overflow

`IntStream.sum()` trả `int`. `LongStream.sum()` trả `long`. Các API này không tự nâng kiểu hoặc kiểm tra tràn:

```java
import java.util.Arrays;

int[] values = {Integer.MAX_VALUE, 1};

int wrong = Arrays.stream(values).sum();                // -2147483648
long right = Arrays.stream(values).asLongStream().sum(); // 2147483648
```

`asLongStream()` đổi từng `int` thành `long` trước khi cộng. Vì vậy phép cộng tích lũy diễn ra trong miền `long`, đủ để biểu diễn tổng của ví dụ.

Với nguồn là `long[]`, dù dùng `LongStream` vẫn có giới hạn:

```java
long[] values = {Long.MAX_VALUE, 1L};
long wrong = Arrays.stream(values).sum(); // Long.MIN_VALUE
```

Hai mảnh mã trên dùng tên biến trùng nhau vì là **hai ví dụ riêng**; đặt chúng trong những phạm vi riêng nếu thử cùng một chương trình.

Các API tổng hợp có hành vi và kiểu trả về đáng chú ý:

| API/biểu thức | Loại kết quả | Điểm có thể tràn |
| --- | --- | --- |
| `int + int` | `int` | Ngay phép `+` |
| `long target = intA + intB` | Phép cộng `int`, sau đó gán `long` | Trước khi gán |
| `(long) intA + intB` | `long` | Nếu vượt miền `long` |
| `IntStream.sum()` | `int` | Bộ tích lũy 32 bit |
| `IntStream.asLongStream().sum()` | `long` | Bộ tích lũy 64 bit |
| `LongStream.sum()` | `long` | Bộ tích lũy 64 bit |
| `Collectors.summingInt(...)` | `Integer` | Tổng 32 bit |
| `Collectors.summingLong(...)` | `Long` | Tổng 64 bit |
| `IntSummaryStatistics.getSum()` | `long` | Tổng 64 bit |
| `LongSummaryStatistics.getSum()` | `long` | Tổng 64 bit |

`IntSummaryStatistics` là một điểm dễ nhầm: mặc dù nhận các phần tử `int`, trường tổng của nó sử dụng `long`. Tuy nhiên, các API có tổng `long` cũng không phải tổng chính xác không giới hạn.

## 3. `reduce`, `Integer::sum` và vòng lặp không tự chống tràn

Stream là mô hình xử lý dữ liệu, không phải cơ chế bảo vệ arithmetic. Những cách sau đều có thể cho tổng sai khi dùng `int`:

```java
int[] data = {Integer.MAX_VALUE, 1};

int loopSum = 0;
for (int value : data) {
    loopSum += value;
}

int reduced = Arrays.stream(data).reduce(0, Integer::sum);
int streamSum = Arrays.stream(data).sum();
```

`Integer.sum(int, int)` thực hiện phép cộng kiểu `int`; nó **không** có hành vi giống `Math.addExact`. `Collectors.summingInt` cũng tích lũy theo `int`:

```java
int result = Stream.of(Integer.MAX_VALUE, 1)
        .collect(Collectors.summingInt(Integer::intValue));
// result = -2147483648
```

Ví dụ collector cần `import java.util.stream.Stream;` và `import java.util.stream.Collectors;`.

Nếu nguồn là `List<Integer>`, hãy nâng từng phần tử trước khi tổng hợp:

```java
List<Integer> numbers = List.of(Integer.MAX_VALUE, 1);
long result = numbers.stream().mapToLong(Integer::longValue).sum();
```

Nhớ rằng đoạn dưới **đã tràn trước khi gán**:

```java
long wrong = numbers.stream().mapToInt(Integer::intValue).sum();
```

Một mảng `int[]` có chiều dài tối đa biểu diễn được bằng `int`, còn mỗi phần tử nằm trong miền `int`. Vì thế tổng toán học của mọi phần tử trong **một mảng `int[]`** luôn vừa `long`: tích của số phần tử lớn nhất với độ lớn một phần tử vẫn nhỏ hơn `Long.MAX_VALUE`. Đây là lý do `long` là kiểu thích hợp cho phép tổng toàn bộ `int[]`.

Điều đó **không áp dụng vô điều kiện cho mọi `IntStream`**, vì một luồng có thể phát sinh lượng phần tử lớn hơn giới hạn chiều dài một mảng. Một hệ thống tổng hợp qua nhiều đợt cũng có thể vượt `long` sau thời gian dài.

## 4. Giải pháp A: nâng kiểu trước khi cộng và trả `long`

Nếu biết trước tổng sẽ nằm trong miền `long`, hãy sử dụng bộ tích lũy `long`:

```java
long total = 0L;
for (int value : values) {
    total += value;
}
```

Trong biểu thức `total += value`, `value` được nâng lên `long` trước phép cộng. Với `int[]`, cách tương đương:

```java
long total = Arrays.stream(values).asLongStream().sum();
```

Nếu API **phải trả `int`** nhưng chỉ quan tâm tổng cuối có vừa `int` không, cộng an toàn bằng `long` rồi dùng `Math.toIntExact`:

```java
long total = Arrays.stream(values).asLongStream().sum();
int result = Math.toIntExact(total);
```

`Math.toIntExact(long)` ném `ArithmeticException` khi giá trị nằm ngoài phạm vi `int`, thay vì cắt bit như ép kiểu `(int) total`.

Tuy nhiên, nếu bản thân tổng `long` đã tràn ở bước trước, `Math.toIntExact` không thể khôi phục kết quả toán học ban đầu. Phải bảo đảm bộ tích lũy đủ rộng hoặc dùng `BigInteger`.

### Lưu ý phép nhân trước phép cộng

Nhiều tổng thực tế là tổng của `quantity * unitPrice`. Nếu cả hai toán hạng là `int`, phép nhân có thể tràn trước khi đưa vào `long`:

```java
int quantity = 100_000;
int unitPrice = 50_000;

long wrong = quantity * unitPrice;           // int đã tràn
long correct = (long) quantity * unitPrice;  // 5_000_000_000
```

Trong collector hoặc `mapToLong`, cần đặt phép ép kiểu vào đúng toán hạng **trước phép nhân**. Với phép nhân cần báo lỗi ngay khi tràn trong kiểu nguyên thủy, Java còn cung cấp `Math.multiplyExact`.

## 5. Giải pháp B: phát hiện tràn trong từng bước với `Math.addExact`

`Math.addExact(int, int)` và `Math.addExact(long, long)` ném `ArithmeticException` nếu tổng vượt miền tương ứng.

```java
int checked = Math.addExact(Integer.MAX_VALUE, 1);
// ArithmeticException: integer overflow
```

Có thể dùng trong vòng lặp:

```java
int sum = 0;
for (int value : values) {
    sum = Math.addExact(sum, value);
}
```

Hoặc trong `IntStream.reduce`:

```java
int sum = Arrays.stream(values).reduce(0, Math::addExact);
```

Đây là hợp đồng **kiểm tra mọi phép cộng trung gian**, khác với kiểm tra **chỉ tổng cuối**. Ví dụ:

```text
[2147483647, 1, -1]
```

Tổng toán học cuối là `2147483647`, hợp lệ đối với `int`. Nhưng khi cộng từ trái sang phải, hai phần tử đầu cho tổng `2147483648` và `Math.addExact` ném lỗi ngay, dù cộng thêm `-1` sẽ đưa tổng về miền `int`.

So sánh:

```java
int[] values = {Integer.MAX_VALUE, 1, -1};

int finalChecked = Math.toIntExact(
        Arrays.stream(values).asLongStream().sum());
// finalChecked = Integer.MAX_VALUE, thành công.

int everyStepChecked = Arrays.stream(values).reduce(0, Math::addExact);
// ArithmeticException do tổng trung gian vượt int.
```

Trong **parallel stream**, phép giảm có thể nhóm các phần tử theo thứ tự trung gian khác nhau. Với `Math::addExact`, một cách nhóm có thể gặp tràn còn cách khác không gặp, dù tổng toán học cuối giống nhau. Do đó, nếu nghiệp vụ chỉ muốn kiểm tra phạm vi tổng cuối, dùng kiểu tích lũy đủ rộng hoặc `BigInteger` để tính chính xác **trước khi** kiểm tra kiểu trả về. Nếu nghiệp vụ bắt buộc từ chối phép cộng trung gian ngoài phạm vi trong một thứ tự xác định, nên mô tả rõ thứ tự đó và dùng vòng lặp tuần tự.

**Quy tắc thiết kế:** Chọn và đặt tên rõ hai chế độ `sumCheckingEveryAddition` (báo lỗi ở bất kỳ bước nào) và `sumExactlyThenNarrow` (kiểm tra tổng cuối). Chúng không có cùng hợp đồng.

## 6. Giải pháp C: dùng `BigInteger` khi `long` cũng không đủ

Nếu tổng có thể vượt 64 bit, dùng `java.math.BigInteger` để cộng các số nguyên chính xác:

```java
import java.math.BigInteger;
import java.util.Arrays;

long[] data = {Long.MAX_VALUE, 1L};

BigInteger total = Arrays.stream(data)
        .mapToObj(BigInteger::valueOf)
        .reduce(BigInteger.ZERO, BigInteger::add);

// total = 9223372036854775808
```

`BigInteger` lưu số nguyên với độ dài biến thiên; `BigInteger.add` không quấn vòng ở giới hạn 32/64 bit. `BigInteger.valueOf(long)` giữ nguyên giá trị `long` đầu vào. Với nguồn là `int[]`, có thể dùng phương thức tương tự trên `IntStream.mapToObj(BigInteger::valueOf)`; Java mở rộng `int` thành `long` phù hợp với chữ ký `valueOf`.

Khi cần thu hẹp kết quả về kiểu nguyên thủy, phân biệt:

```java
long unsafe = total.longValue();       // Có thể cắt bớt bit.
long checked = total.longValueExact(); // Ném ArithmeticException nếu ngoài miền long.
```

Tương tự có `intValueExact()`. `BigInteger` phải cấp phát đối tượng và xử lý nhiều bit hơn kiểu nguyên thủy; chỉ dùng khi phạm vi dữ liệu đòi hỏi. Nó không đảm bảo tính toán có chi phí vô hạn: chương trình vẫn có giới hạn tài nguyên.

### Vì sao không thay bằng `double`?

`double` có miền độ lớn rộng nhưng chỉ giữ chính xác 53 bit significand cho các số nguyên. Vượt ngưỡng đó, không phải mọi số nguyên đều biểu diễn chính xác; tổng có thể bị làm tròn. Vì vậy, `double` không phải lựa chọn để giữ tổng nguyên tuyệt đối chính xác khi tổng có thể vượt `long`.

Nếu dữ liệu là số thập phân có phần lẻ với yêu cầu độ chính xác decimal, hãy xem xét `BigDecimal`; đó là bài toán khác với tổng nguyên và cũng cần chính sách scale/rounding phù hợp.

## 7. Tình huống với `summaryStatistics` và collectors

Đôi khi không chỉ cần tổng mà còn cần số phần tử, min và max:

```java
import java.util.IntSummaryStatistics;

IntSummaryStatistics stats = Arrays.stream(values).summaryStatistics();
long count = stats.getCount();
long sum = stats.getSum();
int min = stats.getMin();
int max = stats.getMax();
```

`getSum()` trả `long`, thường thuận tiện và đủ cho một `int[]`. Với stream rỗng, `count` bằng 0 và `sum` bằng 0; `min` là `Integer.MAX_VALUE`, `max` là `Integer.MIN_VALUE`. Kiểm tra `count > 0` trước khi diễn giải min/max thành quan sát thực tế.

Đối với `Stream<Order>` mà phương thức `Order::quantity` trả `int`, hai lựa chọn:

```java
int risky = orders.stream()
        .collect(Collectors.summingInt(Order::quantity));

long wider = orders.stream()
        .collect(Collectors.summingLong(Order::quantity));
```

Ví dụ này sử dụng lớp `Order` giả định; đây là **mảnh mã minh họa**, không phải chương trình độc lập. `summingLong` giúp nâng số lượng sang `long` trước phép cộng nhưng vẫn có thể tràn nếu tổng vượt `long`.

Nếu cần tổng giá trị `quantity * unitPrice`, nhớ nâng kiểu **trước nhân**:

```java
long total = orders.stream()
        .mapToLong(order -> (long) order.quantity() * order.unitPrice())
        .sum();
```

Nếu không nâng kiểu ngay trong mapper, biểu thức `int * int` có thể tràn rồi mới thành `long`. Nếu tổng nhiều tích vẫn có thể vượt miền `long`, chuyển sang `BigInteger` và tính từng tích bằng `BigInteger.multiply`.

## 8. Chương trình Java 17+ đầy đủ

Lưu nội dung sau trong tệp **`IntegerSumOverflowDemo.java`**:

```java
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IntegerSumOverflowDemo {
    public static void main(String[] args) {
        int[] values = {Integer.MAX_VALUE, 1};

        System.out.println("int sum = " + Arrays.stream(values).sum());
        System.out.println("long sum = "
                + Arrays.stream(values).asLongStream().sum());
        System.out.println("summary sum = "
                + Arrays.stream(values).summaryStatistics().getSum());
        System.out.println("collector sum = "
                + Stream.of(Integer.MAX_VALUE, 1)
                        .collect(Collectors.summingInt(Integer::intValue)));

        try {
            int checked = Arrays.stream(values).reduce(0, Math::addExact);
            System.out.println("checked sum = " + checked);
        } catch (ArithmeticException ex) {
            System.out.println("checked sum = overflow");
        }

        long widened = Arrays.stream(values).asLongStream().sum();
        try {
            System.out.println("toIntExact = " + Math.toIntExact(widened));
        } catch (ArithmeticException ex) {
            System.out.println("toIntExact = overflow");
        }

        int[] mixed = {Integer.MAX_VALUE, 1, -1};
        System.out.println("mixed long final = "
                + Math.toIntExact(Arrays.stream(mixed).asLongStream().sum()));
        try {
            Arrays.stream(mixed).reduce(0, Math::addExact);
            System.out.println("mixed checked = valid");
        } catch (ArithmeticException ex) {
            System.out.println("mixed checked = prefix overflow");
        }

        long[] large = {Long.MAX_VALUE, 1L};
        System.out.println("long overflow = " + Arrays.stream(large).sum());
        BigInteger exact = Arrays.stream(large)
                .mapToObj(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);
        System.out.println("big sum = " + exact);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 IntegerSumOverflowDemo.java
java IntegerSumOverflowDemo
```

Stdout chính xác (đã đối chiếu các biểu thức trên Java 21; mã dùng API có từ Java 17):

```text
int sum = -2147483648
long sum = 2147483648
summary sum = 2147483648
collector sum = -2147483648
checked sum = overflow
toIntExact = overflow
mixed long final = 2147483647
mixed checked = prefix overflow
long overflow = -9223372036854775808
big sum = 9223372036854775808
```

Điều cần quan sát: `IntStream.sum()` và `summingInt` đều quấn vòng; `asLongStream().sum()` và `IntSummaryStatistics.getSum()` giữ đúng tổng đầu tiên; `Math.addExact` và `Math.toIntExact` kiểm tra **những điều kiện khác nhau**; `LongStream.sum()` có thể tràn trong khi `BigInteger` giữ được tổng chính xác.

## 9. Định nghĩa hợp đồng của API tổng hợp

Một hàm tên `sum()` dễ khiến người dùng nghĩ rằng kết quả luôn bằng tổng toán học. Nếu có nguy cơ vượt phạm vi, API cần nói rõ kiểu trả về và chính sách overflow.

| Nhu cầu | Cách triển khai | Hành vi với overflow |
| --- | --- | --- |
| Tổng đầy đủ của một `int[]` | `Arrays.stream(values).asLongStream().sum()` | Trả `long` an toàn cho mảng `int[]` |
| API phải trả `int`, chỉ quan tâm kết quả cuối | Tính bằng `long`, rồi `Math.toIntExact` | Ném khi tổng cuối ngoài `int` |
| Từ chối ngay mọi phép cộng `int` bị tràn | Vòng lặp với `Math.addExact` | Ném ở phép cộng trung gian |
| Tổng các giá trị `long` có thể vượt 64 bit | `BigInteger` | Trả tổng nguyên chính xác |
| Cần count/min/max/sum trên `int[]` | `IntSummaryStatistics` | Tổng dùng `long`; kiểm tra điều kiện nguồn |
| Tổng số lượng từ object | `mapToLong` hoặc `Collectors.summingLong` | Tính trên `long` |
| `BigInteger` cần chuyển về `long` | `longValueExact()` | Ném khi tổng ngoài `long` |

Tên và chữ ký phương thức nên phản ánh chính sách, ví dụ:

```java
long sumIntsAsLong(int[] values);
int sumIntsAndCheckFinalRange(int[] values);
int sumIntsAndCheckEachAddition(int[] values);
BigInteger sumLongsExactly(long[] values);
```

Những dòng trên là **chữ ký minh họa**, cần thêm thân phương thức và đặt trong class thích hợp để biên dịch. Tài liệu API cũng cần quy định `null` đầu vào có hợp lệ không, mảng rỗng trả 0 hay không, thứ tự xử lý có quan trọng không, và có cho phép trả tổng lớn hơn phần tử nguồn không.

Trong nghiệp vụ tiền tệ lưu số cent bằng `long`, đừng mặc định rằng `long` luôn đủ: cần xét giới hạn số giao dịch, số lượng mỗi giao dịch và thời gian tích lũy. Với miền lớn khó chứng minh, cân nhắc `BigInteger`.

## 10. Những lỗi thường gặp và cách kiểm thử

### Lỗi cần tránh

1. Tin rằng `IntStream.sum()` tự mở rộng lên `long` hoặc ném ngoại lệ khi tràn.
2. Gán biểu thức `int + int` vào `long` rồi nghĩ phép cộng đã dùng `long`.
3. Dùng `Integer::sum` thay `Math.addExact`, tưởng rằng hai API có cùng hành vi.
4. Dùng `Collectors.summingInt` khi tổng có thể vượt giới hạn `int`.
5. Nghĩ rằng kiểm tra từng phép cộng với `Math.addExact` luôn tương đương với kiểm tra tổng cuối.
6. Dùng `Math.toIntExact` sau một phép cộng `long` đã tràn; khi đó kết quả toán học đã mất.
7. Dùng `BigInteger.longValue()` khi cần bảo toàn giá trị thay vì `longValueExact()`.
8. Dùng `double` để “tránh tràn nguyên” khi cần số nguyên tuyệt đối chính xác.
9. Quên phép nhân `int * int` có thể tràn trước khi tổng hợp.
10. Giả định parallel reduction không ảnh hưởng đến thời điểm phát hiện tràn từng phép cộng.

### Các ca kiểm thử tối thiểu

| Dữ liệu | Tổng toán học | Điều cần kiểm tra |
| --- | --- | --- |
| `[]` | 0 | Hợp đồng mảng rỗng |
| `[1, 2, 3]` | 6 | Trường hợp bình thường |
| `[Integer.MAX_VALUE, 1]` | 2.147.483.648 | Tràn `int` phía dương |
| `[Integer.MIN_VALUE, -1]` | -2.147.483.649 | Tràn `int` phía âm |
| `[Integer.MAX_VALUE, 1, -1]` | 2.147.483.647 | Tràn trung gian dù tổng cuối hợp lệ |
| `[Long.MAX_VALUE, 1L]` | 9.223.372.036.854.775.808 | Tràn `long`, cần `BigInteger` |
| `[100_000 * 50_000]` với toán hạng `int` | 5.000.000.000 | Cần nâng kiểu trước phép nhân |

Với code có `parallel()` và kiểm tra từng phép cộng, nên kiểm thử dữ liệu có số dương và âm lớn để hiểu rõ kết quả chịu ảnh hưởng bởi thứ tự nhóm. Với API kiểm tra tổng cuối, dùng kiểu tích lũy đủ rộng rồi kiểm tra một lần.

**Nguyên tắc:** Xác định độ lớn cực đại của phần tử, số phần tử tối đa và giới hạn tổng. Nâng kiểu **trước phép tính**; sử dụng `long` nếu chứng minh đủ rộng, `BigInteger` nếu cần miền vượt 64 bit. Nếu phải ném lỗi khi vượt kiểu trả về, nêu rõ đó là lỗi tại **mỗi bước cộng** hay ở **tổng cuối**.

### Tài liệu Java chính thức

- [`IntStream.sum()` và `IntStream.summaryStatistics()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/IntStream.html)
- [`LongStream.sum()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/LongStream.html)
- [`Math.addExact()` và `Math.toIntExact()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
- [`Collectors.summingInt()` và `Collectors.summingLong()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)
- [`BigInteger`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-004">average và yêu cầu xử lý tập rỗng</a>

<details>
<summary>Xem chi tiết</summary>

# average và yêu cầu xử lý tập rỗng trong Java

## 1. Vì sao tập rỗng là trường hợp riêng?

Trung bình cộng (arithmetic mean) của `n` số là tổng của chúng chia cho số lượng phần tử:

```text
average = (x1 + x2 + ... + xn) / n
Điều kiện: n > 0
```

Ví dụ, dãy `[10, 20, 30]` có tổng 60, số lượng 3, trung bình 20.0. Nhưng một tập rỗng không chứa số hạng nào: tổng quy ước bằng 0, số lượng bằng 0, và `0 / 0` **không xác định trung bình cộng**.

Trong Java, **các API khác nhau chọn cách biểu diễn trường hợp rỗng khác nhau**:

| API | Trên tập không rỗng | Trên tập rỗng |
| --- | --- | --- |
| `IntStream.average()` | `OptionalDouble` chứa giá trị | `OptionalDouble.empty()` |
| `LongStream.average()` | `OptionalDouble` chứa giá trị | `OptionalDouble.empty()` |
| `DoubleStream.average()` | `OptionalDouble` chứa giá trị | `OptionalDouble.empty()` |
| `Collectors.averagingInt/Long/Double` | `Double` chứa trung bình | `0.0` |
| `IntSummaryStatistics.getAverage()` | `double` chứa trung bình | `0.0` |
| `LongSummaryStatistics.getAverage()` | `double` chứa trung bình | `0.0` |
| `DoubleSummaryStatistics.getAverage()` | `double` chứa trung bình | `0.0` |

Điểm cốt lõi: **0.0 là một kết quả số hợp lệ**, ví dụ trung bình của `[-10, 10]`; vì vậy không thể nhìn riêng số `0.0` để biết có dữ liệu hay không. Nếu xử lý sai tập rỗng, chương trình có thể báo điểm trung bình 0 cho lớp chưa có điểm, hoặc báo thời gian phản hồi trung bình 0 ms dù chưa ghi nhận yêu cầu nào.

## 2. average() của primitive stream trả OptionalDouble

Java cung cấp `average()` trên ba primitive stream: `IntStream`, `LongStream` và `DoubleStream`. Mỗi phương thức trả `java.util.OptionalDouble`, thay vì trả trực tiếp `double`.

```java
OptionalDouble present = IntStream.of(2, 4, 6).average();
OptionalDouble absent = IntStream.empty().average();

// present.isPresent() == true, present.orElseThrow() == 4.0
// absent.isEmpty() == true
```

Khối trên chỉ minh họa API trong một phương thức; muốn dùng riêng cần import `java.util.OptionalDouble` và `java.util.stream.IntStream`.

`OptionalDouble` là kiểu mang hai trạng thái:

- **Có giá trị:** ví dụ `OptionalDouble.of(0.0)` vẫn là kết quả có mặt, dù giá trị là số không.
- **Không có giá trị:** `OptionalDouble.empty()` biểu thị phép tổng hợp không có trung bình vì thiếu phần tử.

Các phương thức xử lý thường dùng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `isPresent()` | Có kết quả không? |
| `isEmpty()` | Không có kết quả không? (Java 11+) |
| `ifPresent(action)` | Chỉ thực hiện hành động khi có kết quả |
| `orElse(defaultValue)` | Trả giá trị thật hoặc giá trị mặc định do caller quy định |
| `orElseGet(supplier)` | Tính giá trị mặc định khi không có kết quả |
| `orElseThrow()` | Bắt buộc phải có giá trị; nếu không có thì ném `NoSuchElementException` |
| `orElseThrow(exceptionSupplier)` | Rỗng thì ném ngoại lệ do nghiệp vụ chỉ định |
| `getAsDouble()` | Đọc giá trị hiện có; rỗng sẽ ném `NoSuchElementException` |
| `stream()` | Chuyển thành `DoubleStream` có 0 hoặc 1 phần tử (Java 9+) |

Việc gọi `average()` trên một primitive stream rỗng **không tự ném ngoại lệ**; ngoại lệ xuất hiện khi người gọi cố lấy giá trị không có sẵn bằng `getAsDouble()` hoặc `orElseThrow()`.

## 3. Chương trình Java 17+ đầu tiên: kết quả có mặt, vắng mặt và mặc định

Lưu mã sau thành `AverageEmptyDemo.java`:

```java
import java.util.IntSummaryStatistics;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AverageEmptyDemo {
    public static void main(String[] args) {
        OptionalDouble normal = IntStream.of(10, 20, 30).average();
        OptionalDouble zeroMean = IntStream.of(-5, 5).average();
        OptionalDouble missing = IntStream.empty().average();

        System.out.println("Normal: " + normal.orElseThrow());
        System.out.println("Zero mean present: " + zeroMean.isPresent());
        System.out.println("Zero mean value: " + zeroMean.orElseThrow());
        System.out.println("Empty present: " + missing.isPresent());
        System.out.println("Empty fallback: " + missing.orElse(0.0));

        double collectorEmpty = Stream.<Integer>empty()
                .collect(Collectors.averagingInt(Integer::intValue));
        IntSummaryStatistics stats = IntStream.empty().summaryStatistics();

        System.out.println("Collector empty: " + collectorEmpty);
        System.out.println("Summary count: " + stats.getCount());
        System.out.println("Summary average: " + stats.getAverage());
    }
}
```

Biên dịch và chạy:

```bash
javac AverageEmptyDemo.java
java AverageEmptyDemo
```

Stdout chính xác:

```text
Normal: 20.0
Zero mean present: true
Zero mean value: 0.0
Empty present: false
Empty fallback: 0.0
Collector empty: 0.0
Summary count: 0
Summary average: 0.0
```

Các dòng `Zero mean value: 0.0` và `Empty fallback: 0.0` trùng số nhưng **khác ý nghĩa**: một phép tính đã có hai số, còn phép tính kia không có giá trị và được người gọi *thay thế* bằng số 0. Dòng `Collector empty: 0.0` là quy ước của collector, không biến trung bình cộng của tập rỗng thành kết quả toán học 0.

## 4. Ba chính sách khi gặp tập rỗng

### 4.1. Cho phép thiếu kết quả: giữ OptionalDouble

Đối với báo cáo có thể chưa có giao dịch, chưa có điểm thi hoặc chưa có mẫu cảm biến, việc **không có trung bình** là trạng thái bình thường.

```java
OptionalDouble average = scores.stream()
        .mapToInt(Integer::intValue)
        .average();
```

Đoạn minh họa giả sử `scores` là `Collection<Integer>` không chứa `null` và nằm trong một phương thức. Trả nguyên `OptionalDouble` từ service giúp tầng hiển thị chọn “Chưa có dữ liệu” thay vì vô tình hiển thị `0`.

### 4.2. Không được phép rỗng: ném lỗi nghiệp vụ

Nếu ít nhất một giá trị là điều kiện bắt buộc, hãy kiểm tra và báo rõ nguyên nhân:

```java
double mean = scores.stream()
        .mapToInt(Integer::intValue)
        .average()
        .orElseThrow(() ->
                new IllegalArgumentException("At least one score is required"));
```

`orElseThrow(Supplier)` cung cấp ngoại lệ rõ ngữ cảnh. Cách này dễ chẩn đoán hơn việc gọi `getAsDouble()` không kiểm tra rồi để `NoSuchElementException` lan ra.

### 4.3. Có chính sách mặc định: dùng orElse

Nếu thiết kế báo cáo quy định “không có mẫu thì hiển thị 0”, có thể dùng:

```java
double displayAverage = values.stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);
```

Nhưng phải hiểu: đây là **giá trị thay thế theo nghiệp vụ**, không phải trung bình được tính từ tập rỗng. Khi cần phân biệt “rỗng” và “trung bình thật bằng 0”, hãy lưu thêm số lượng phần tử hoặc trạng thái tồn tại.

Không dùng `-1`, `NaN` hay `0` làm dấu hiệu “thiếu” tùy tiện: domain có thể có giá trị đó; xử lý JSON/database hoặc toán học tiếp theo cũng có thể thay đổi ý nghĩa. `OptionalDouble` hoặc kết quả có trường `count` thể hiện sự thiếu dữ liệu rõ ràng hơn.

## 5. Phân biệt Stream.average và Collectors.averaging*

`Stream<T>` của đối tượng **không** có `average()` trực tiếp. Nếu có `List<Integer>`, thường chuyển sang primitive stream:

```java
OptionalDouble average = numbers.stream()
        .mapToInt(Integer::intValue)
        .average();
```

Hoặc dùng collector:

```java
double average = numbers.stream()
        .collect(Collectors.averagingInt(Integer::intValue));
```

Hai khối trên giả định `numbers` là một `List<Integer>` hợp lệ ở phạm vi gọi; chúng không phải chương trình Java độc lập. Trong trường hợp đầu, rỗng cho `OptionalDouble.empty()`. Trong trường hợp sau, rỗng cho `0.0` vì `Collectors.averagingInt` trả `Double` và được định nghĩa như vậy.

`Collectors.averagingLong`, `Collectors.averagingDouble` có chính sách tương tự trên tập rỗng. Sự khác biệt kiểu trả về quyết định lượng thông tin còn giữ lại: collector chỉ chứa số thì **không giữ được** trạng thái “không có phần tử”.

Nếu đang dùng `groupingBy`, mỗi nhóm xuất hiện trong kết quả khi có ít nhất một phần tử thuộc nhóm ấy; một nhóm không xuất hiện trong map **không phải** là một entry có average 0.0. Phải xác định danh sách nhóm bắt buộc từ nghiệp vụ nếu muốn biểu diễn cả nhóm rỗng.

## 6. SummaryStatistics: kiểm tra count trước khi diễn giải average

`summaryStatistics()` tính nhiều đại lượng trong một terminal operation. Ví dụ:

```java
IntSummaryStatistics statistics = numbers.stream()
        .mapToInt(Integer::intValue)
        .summaryStatistics();

long count = statistics.getCount();
if (count == 0) {
    System.out.println("No observations");
} else {
    System.out.println("Average = " + statistics.getAverage());
}
```

Đoạn trên giả định đã import `java.util.IntSummaryStatistics`, `numbers` là collection không chứa `null`.

Với `IntSummaryStatistics` rỗng:

- `getCount()` trả `0`.
- `getSum()` trả `0`.
- `getAverage()` trả `0.0`.
- `getMin()` trả `Integer.MAX_VALUE`.
- `getMax()` trả `Integer.MIN_VALUE`.

Vì vậy phải kiểm tra `count` trước khi trình bày average, min và max như số thống kê của mẫu thật. `LongSummaryStatistics` và `DoubleSummaryStatistics` cũng có khái niệm count và giá trị mặc định cho tập rỗng, dù kiểu và ranh giới số của min/max khác nhau.

`summaryStatistics()` phù hợp khi cần vừa count vừa mean, min, max. Nếu chỉ quan tâm trung bình và trạng thái có/không có, `average()` trả `OptionalDouble` trực tiếp thường đơn giản hơn.

## 7. Thiết kế API để bảo toàn ý nghĩa tập rỗng

Một utility trả `OptionalDouble` giúp người gọi tự áp quy tắc nghiệp vụ:

```java
import java.util.Collection;
import java.util.Objects;
import java.util.OptionalDouble;

public class AverageService {
    public static OptionalDouble ofIntegers(Collection<Integer> values) {
        Objects.requireNonNull(values, "values");
        return values.stream()
                .mapToInt(Integer::intValue)
                .average();
    }
}
```

Class này biên dịch được nhưng không có `main`; nó cung cấp hàm tiện ích để gọi từ code khác.

Ví dụ:

```java
OptionalDouble x = AverageService.ofIntegers(java.util.List.of(4, 6));
// x.orElseThrow() == 5.0

OptionalDouble y = AverageService.ofIntegers(java.util.List.of());
// y.isEmpty() == true
```

Lưu ý: `Objects.requireNonNull` chỉ kiểm tra **collection** khác `null`, không tự kiểm tra các phần tử. Nếu một phần tử `Integer` là `null`, unboxing từ `Integer::intValue` sẽ ném `NullPointerException`. Phải xác định dữ liệu `null` là lỗi nhập, giá trị thiếu cần ghi nhận, hay bản ghi được phép bỏ qua, rồi triển khai đúng chính sách đó.

Với API JSON, có thể dùng DTO biểu thị `count` và `average` có thể thiếu, thay vì kỳ vọng mọi thư viện serialization đều hiểu `OptionalDouble` như một trường dữ liệu tiêu chuẩn. Khi đó tên trường và quy tắc vắng mặt nên được mô tả rõ.

## 8. Chương trình minh họa nhóm dữ liệu

Với thống kê theo lớp, nhóm không có bản ghi thường **không có khóa** trong kết quả `groupingBy`. Lưu `GroupAverageDemo.java`:

```java
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupAverageDemo {
    record Score(String group, int points) {}

    public static void main(String[] args) {
        List<Score> data = List.of(
                new Score("A", 10),
                new Score("A", 20));

        Map<String, Double> result = data.stream().collect(
                Collectors.groupingBy(
                        Score::group,
                        Collectors.averagingInt(Score::points)));

        System.out.println("A average: " + result.get("A"));
        System.out.println("Has B: " + result.containsKey("B"));
        System.out.println("Group count: " + result.size());
    }
}
```

Chạy:

```bash
javac GroupAverageDemo.java
java GroupAverageDemo
```

Stdout:

```text
A average: 15.0
Has B: false
Group count: 1
```

Không có khóa B vì dữ liệu ban đầu không chứa Score của B. Nếu báo cáo bắt buộc phải hiển thị B, hãy lấy danh sách lớp từ nguồn domain, rồi đối chiếu với kết quả thống kê; không thể tự suy ra các lớp vắng mặt từ một stream đầu vào.

## 9. Độ chính xác và lỗi số học khi tính average

Việc xử lý tập rỗng khác với việc chọn độ chính xác. `average()` trả `double`, kể cả khi dữ liệu đầu vào là `int` hay `long`. `double` có độ chính xác hữu hạn, nên phép tính có thể bị làm tròn, đặc biệt khi dữ liệu lớn. Với `DoubleStream`, kết quả có thể là `NaN` hoặc vô cực tùy dữ liệu.

Các lưu ý:

1. `OptionalDouble.of(Double.NaN)` **không giống** `OptionalDouble.empty()`. Nếu ứng dụng chỉ chấp nhận số hữu hạn, sau khi kiểm tra có giá trị hãy dùng `Double.isFinite(value)`.
2. Nếu tự triển khai bằng `int sum` và `int count`, biểu thức `sum / count` là **phép chia nguyên**. Ví dụ tổng 5 và count 2 cho kết quả nguyên 2 trước khi gán sang `double`. Cần đổi sang `double` trước phép chia: `((double) sum) / count`.
3. Khi tự tính bằng integer, tổng `int` hoặc `long` có thể tràn số. Đừng giả định dữ liệu đầu vào nhỏ chỉ vì một phần tử có kiểu `int`.
4. Thứ tự cộng các `double` có thể ảnh hưởng sai số. Một số phép tổng hợp trong Java dùng kỹ thuật bù sai số, nhưng điều đó không đảm bảo kết quả chính xác tuyệt đối như số thực toán học.
5. Không dùng `==` để so trung bình dấu phẩy động tính từ các luồng thuật toán khác nhau một cách thiếu cân nhắc; thiết kế ngưỡng sai số và xử lý `NaN`, vô cực tùy bài toán.
6. Với dữ liệu tiền tệ cần decimal chính xác và rounding rõ ràng, cân nhắc `BigDecimal`.

Ngoài ra, `Stream` là luồng tiêu thụ **một lần**. Nếu gọi `count()` rồi `average()` trên cùng một primitive stream sẽ không hợp lệ; hai terminal operation không được chạy trên cùng stream đã tiêu thụ. Hãy tạo stream mới từ collection, hoặc dùng `summaryStatistics()` để lấy count và average trong cùng lần duyệt.

## 10. BigDecimal: kiểm tra rỗng và khai báo rounding

Với số thập phân cần kiểm soát độ chính xác và làm tròn, có thể tính tổng bằng `BigDecimal` rồi chia theo số phần tử. Nhưng cần kiểm tra tập rỗng trước khi chia để tránh mẫu số bằng 0.

Lưu `DecimalAverageDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DecimalAverageDemo {
    static Optional<BigDecimal> average(List<BigDecimal> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal item : values) {
            sum = sum.add(Objects.requireNonNull(item, "item"));
        }

        BigDecimal mean = sum.divide(
                BigDecimal.valueOf(values.size()),
                2,
                RoundingMode.HALF_UP);

        return Optional.of(mean);
    }

    public static void main(String[] args) {
        System.out.println("Mean: " + average(List.of(
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                new BigDecimal("2.00"))).orElseThrow());
        System.out.println("Empty: " + average(List.of()).isEmpty());
        System.out.println("Zero mean: " + average(List.of(
                new BigDecimal("-1.00"),
                new BigDecimal("1.00"))).orElseThrow());
    }
}
```

Chạy:

```bash
javac DecimalAverageDemo.java
java DecimalAverageDemo
```

Stdout:

```text
Mean: 1.67
Empty: true
Zero mean: 0.00
```

`5.00 / 3` có khai triển thập phân vô hạn, nên `divide(divisor)` *không chỉ rõ chế độ làm tròn* sẽ ném `ArithmeticException`. Ví dụ đã chọn scale 2, `HALF_UP` nên kết quả là `1.67`. Scale và chế độ rounding phải đến từ yêu cầu nghiệp vụ, **không mặc định mọi giá trị tiền tệ cần hai chữ số lẻ**.

Khi không có phần tử, hàm trả `Optional.empty()`; không thực hiện phép chia. Khi có phần tử và trung bình thật bằng 0, hàm trả `Optional.of(new BigDecimal("0.00"))` về mặt giá trị và scale của kết quả đã tính.

## 11. average theo nhóm và trung bình có trọng số

Không phải mọi nhu cầu gọi là “average” đều tương đương nhau:

- **Trung bình cộng thông thường:** `sum(values) / count`, chỉ xác định khi count > 0.
- **Trung bình có trọng số:** `sum(value × weight) / sum(weight)`; phải kiểm tra tổng trọng số theo hợp đồng trước khi chia.
- **Trung bình các trung bình nhóm:** khác với trung bình mọi phần tử nếu nhóm có số lượng không bằng nhau.
- **Trung bình theo thời lượng:** nếu khoảng thời gian lấy mẫu khác nhau, có thể phải dùng trọng số theo thời gian thay vì coi mọi quan sát là một đơn vị.

Ví dụ nhóm A có một điểm 10, nhóm B có ba điểm 20, 20, 20. Trung bình các nhóm `(10 + 20) / 2 = 15`; trung bình trên bốn học sinh `(10 + 20 + 20 + 20) / 4 = 17.5`. Phép nào phù hợp tùy vào câu hỏi báo cáo.

Tập rỗng, nhóm rỗng, và tổng trọng số bằng 0 đều cần được quy định rõ. Không tự đồng nhất `Collectors.averagingInt` trả 0.0 với một trung bình có trọng số hợp lệ.

## 12. Gợi ý kiểm thử các trường hợp quan trọng

Đối với utility tính trung bình, nên có những trường hợp:

| Đầu vào | Điều cần kiểm tra |
| --- | --- |
| `[]` | Chính sách rỗng: Optional empty, lỗi, hay mặc định |
| `[0]` | Trung bình thực sự bằng `0.0` nhưng có một phần tử |
| `[-5, 5]` | Trung bình `0.0` từ hai phần tử, không phải tập rỗng |
| `[1, 2]` | Trung bình `1.5`; tránh lỗi chia nguyên |
| `[1, 2, 2]` với BigDecimal | Có chính sách rounding hợp lệ, ví dụ scale 2 cho `1.67` |
| Dữ liệu chứa `null` | Chính sách chấp nhận, từ chối hay bỏ qua rõ ràng |
| Dữ liệu chứa `Double.NaN` hoặc vô cực | Không nhầm số không hữu hạn với Optional rỗng |
| Chỉ có nhóm A | Không giả định nhóm B có average thật 0 |
| Danh sách rất lớn | Chú ý tràn tổng nguyên, mất precision khi dùng double |

Có thể unit test hàm `OptionalDouble` bằng `isEmpty()` cho trường hợp rỗng và `orElseThrow()` cho trường hợp có dữ liệu; không nhất thiết xây dựng stream ẩn bên trong test.

## 13. Các lỗi phổ biến

| Lỗi | Tác hại | Hướng sửa |
| --- | --- | --- |
| Gọi `average().getAsDouble()` khi rỗng | `NoSuchElementException` | Kiểm tra Optional hoặc `orElseThrow` có ngữ cảnh |
| Dùng `orElse(0.0)` mọi nơi | Nhầm “chưa có mẫu” với “trung bình 0” | Giữ Optional/count trừ khi nghiệp vụ có mặc định |
| Cho rằng `Collectors.averagingInt` trả Optional | Hiểu sai hợp đồng API | Kiểm tra count hoặc dùng primitive stream |
| Đọc `summaryStatistics.getAverage()` khi count=0 như giá trị đo được | Báo cáo sai | Kiểm tra `getCount()` |
| Viết `sum / count` bằng số nguyên | Mất chữ số thập phân | Ép kiểu trước phép chia |
| Dùng lại một stream sau terminal operation | Stream đã tiêu thụ | Tạo stream mới hoặc summary một lượt |
| Đưa danh sách rỗng vào phép chia BigDecimal | Chia cho 0 | Guard count > 0 trước khi divide |
| Không xác định scale/rounding cho BigDecimal | Có thể ném `ArithmeticException` vì thương vô hạn | Khai báo chính sách decimal |
| Coi `Double.NaN` là thiếu dữ liệu | Sai trạng thái | Phân biệt `isPresent` với `Double.isFinite` |
| Lấy trung bình các trung bình mà quên count từng nhóm | Kết quả toàn bộ sai | Tính tổng và count có trọng số |

## 14. Tự kiểm tra

1. **`IntStream.empty().average()` trả gì?** `OptionalDouble.empty()`; `average()` không tự ném lỗi.
2. **Có phân biệt `OptionalDouble.empty()` và `OptionalDouble.of(0.0)` không?** Có; một bên thiếu dữ liệu, bên kia có giá trị 0.0.
3. **`Stream.<Integer>empty().collect(Collectors.averagingInt(Integer::intValue))` trả gì?** `0.0` theo hợp đồng collector.
4. **`IntStream.empty().summaryStatistics().getAverage()` trả gì?** `0.0`; cần kiểm tra `getCount() == 0` trước khi diễn giải.
5. **Nếu phải có ít nhất một điểm, nên xử lý ra sao?** Chọn `average().orElseThrow(() -> new IllegalArgumentException(...))` hoặc kiểm tra dữ liệu đầu vào.
6. **Nếu chưa có điểm là trạng thái hợp lệ?** Trả Optional hoặc cấu trúc có count; hiển thị “Chưa có dữ liệu”.
7. **Có dùng cùng một stream để `count()` rồi `average()` được không?** Không; terminal operation tiêu thụ stream.
8. **Kết quả average là NaN có phải nghĩa là Optional rỗng?** Không; Optional có thể chứa giá trị NaN.
9. **Vì sao `BigDecimal` chia trung bình có thể cần rounding?** Vì thương như 5/3 có vô hạn chữ số thập phân, nên phải quyết định scale/rounding nếu cho phép làm tròn.

## 15. Kết luận

Khi dùng `average` trong Java, vấn đề quan trọng không chỉ là chia tổng cho số lượng, mà còn là **định nghĩa hành vi khi không có phần tử**. `IntStream`, `LongStream` và `DoubleStream` trả `OptionalDouble` để giữ trạng thái thiếu; `Collectors.averaging*` và `SummaryStatistics.getAverage()` trả `0.0` khi rỗng, vì đó là hợp đồng API, không phải khẳng định trung bình toán học của tập rỗng là 0.

Hãy chọn chính sách cụ thể: giữ Optional khi thiếu dữ liệu là bình thường, ném lỗi có ngữ cảnh nếu input bắt buộc không rỗng, hoặc dùng mặc định chỉ khi có quy tắc nghiệp vụ rõ ràng. Với dữ liệu thực tế, kiểm tra thêm `count`, độ chính xác `double`, giá trị không hữu hạn, và rounding của `BigDecimal` để kết quả có ý nghĩa và đáng tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-005">min, max và các Optional chuyên biệt</a>

<details>
<summary>Xem chi tiết</summary>

# `min`, `max` và các `Optional` chuyên biệt khi tổng hợp số trong Java

## 1. Mục tiêu và bài toán

Khi tổng hợp dữ liệu số trong Java, hai yêu cầu phổ biến là tìm **giá trị nhỏ nhất (`min`)** và **giá trị lớn nhất (`max`)**. Ví dụ: mức nhiệt thấp nhất trong ngày, độ trễ lớn nhất của request, điểm số cao nhất hoặc số lượng đơn hàng nhỏ nhất.

Khó khăn xuất hiện khi **nguồn dữ liệu rỗng**. Một dãy không có phần tử không có giá trị nhỏ nhất hoặc lớn nhất xác định. Java Stream API biểu diễn điều này bằng `OptionalInt`, `OptionalLong`, `OptionalDouble` hoặc `Optional<T>`; người gọi phải quyết định cách xử lý trường hợp thiếu dữ liệu.

```java
OptionalInt minimum = IntStream.of(8, 3, 12).min();
OptionalInt maximum = IntStream.of(8, 3, 12).max();
// minimum chứa 3; maximum chứa 12
```

Sau bài học, bạn có thể chọn đúng overload `min`/`max`, hiểu vì sao API trả `Optional`, xử lý dãy rỗng an toàn, phân biệt stream nguyên thủy và stream đối tượng, đánh giá trường hợp `NaN`/`-0.0`, và áp dụng `summaryStatistics()` khi cần nhiều phép tổng hợp trên cùng nguồn.

## 2. `min` và `max` trên ba kiểu stream nguyên thủy

Java có ba giao diện stream chuyên biệt cho số nguyên thủy:

| Kiểu stream | Phương thức | Kết quả | Loại giá trị |
| --- | --- | --- | --- |
| `IntStream` | `min()`, `max()` | `OptionalInt` | `int` |
| `LongStream` | `min()`, `max()` | `OptionalLong` | `long` |
| `DoubleStream` | `min()`, `max()` | `OptionalDouble` | `double` |
| `Stream<T>` | `min(Comparator)`, `max(Comparator)` | `Optional<T>` | Đối tượng |

Các phương thức `min()`/`max()` của primitive stream là **terminal operation**: chúng tiêu thụ stream để tính kết quả. Không có overload nhận `Comparator` trên `IntStream`, `LongStream`, `DoubleStream`; các kiểu số nguyên thủy dùng thứ tự số tự nhiên của mình. Với `Stream<T>`, bạn phải truyền một `Comparator` xác định thứ tự.

Ví dụ:

```java
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;

OptionalInt smallestInt = IntStream.of(6, -2, 9).min();
OptionalLong largestLong = LongStream.of(2L, 5_000_000_000L).max();
OptionalDouble smallestDouble = DoubleStream.of(3.5, 1.25).min();

System.out.println(smallestInt.orElseThrow());  // -2
System.out.println(largestLong.orElseThrow());   // 5000000000
System.out.println(smallestDouble.orElseThrow()); // 1.25
```

Đây là **đoạn mã nhúng**: đặt các lệnh trong một phương thức, cùng các `import` đã nêu. Chương trình độc lập có thể chạy ở mục 8.

Các hàm này tìm cực trị theo **giá trị phần tử**. Nếu muốn tìm *phần tử có giá trị điểm số lớn nhất* nhưng vẫn giữ ID, nhãn hoặc thông tin khác, hãy dùng `Stream<T>.max(comparator)` thay vì chỉ chuyển mọi đối tượng thành `int`.

### 2.1. Vì sao kết quả là `Optional`?

Vì không thể gán cực trị hợp lệ cho một stream rỗng:

```java
OptionalInt none = IntStream.empty().max();

System.out.println(none.isPresent()); // false
System.out.println(none.isEmpty());   // true
```

`OptionalInt.empty()` không phải số `0`. Trả `0`, `Integer.MIN_VALUE` hay `null` làm kết quả mặc định sẽ thay đổi ý nghĩa của bài toán nếu giá trị đó vốn có thể xuất hiện trong dữ liệu.

Các overload `min`/`max` với primitive stream tương đương một phép **reduction không có identity** theo phép lấy cực tiểu/cực đại, như `reduce(Integer::min)` với `IntStream`. Vì không có identity phổ quát phân biệt được dãy rỗng, kết quả tự nhiên là `OptionalInt`.

### 2.2. Khoảng kiểu dữ liệu và chuyển đổi

- `IntStream` chứa `int`, từ `Integer.MIN_VALUE` đến `Integer.MAX_VALUE`. Nó không tự nâng mọi đầu vào thành `long`.
- `LongStream` chứa `long`, phù hợp số vượt giới hạn `int`.
- `DoubleStream` chứa `double`, nên chịu các đặc tính số dấu phẩy động IEEE 754 như `NaN`, vô cực và `-0.0`.
- `Stream<Integer>`, `Stream<Long>`, `Stream<Double>` là **stream đối tượng**, có thể cần boxing/unboxing nếu chuyển kiểu và dùng `Optional<T>` thay vì `OptionalInt`/`OptionalLong`/`OptionalDouble`.

Ví dụ danh sách `List<Integer>`:

```java
List<Integer> numbers = List.of(7, 2, 11);
OptionalInt min = numbers.stream()
        .mapToInt(Integer::intValue)
        .min();
```

Lệnh trên cần `import java.util.List; import java.util.OptionalInt;`. `List.of(...)` không chứa `null`, nên chuyển thành primitive an toàn. Với một `List<Integer>` khác có phần tử `null`, unboxing sẽ gây `NullPointerException`; phải đưa ra chính sách lọc/từ chối `null` rõ ràng.

## 3. Các phương thức của `OptionalInt`, `OptionalLong` và `OptionalDouble`

Ba lớp chuyên biệt lưu trạng thái **có hoặc không có một giá trị nguyên thủy**, tránh phải đóng hộp vào `Integer`, `Long`, `Double` chỉ để biểu diễn “có thể vắng mặt”.

| Nhu cầu | `OptionalInt` | `OptionalLong` | `OptionalDouble` |
| --- | --- | --- | --- |
| Kiểm tra hiện diện | `isPresent()` / `isEmpty()` | `isPresent()` / `isEmpty()` | `isPresent()` / `isEmpty()` |
| Lấy giá trị khi chắc chắn có | `getAsInt()` | `getAsLong()` | `getAsDouble()` |
| Có giá trị thì lấy, thiếu thì thay | `orElse(int)` | `orElse(long)` | `orElse(double)` |
| Tính fallback khi thiếu | `orElseGet(IntSupplier)` | `orElseGet(LongSupplier)` | `orElseGet(DoubleSupplier)` |
| Thiếu thì ném ngoại lệ | `orElseThrow()` | `orElseThrow()` | `orElseThrow()` |
| Chạy hành động nếu có | `ifPresent(IntConsumer)` | `ifPresent(LongConsumer)` | `ifPresent(DoubleConsumer)` |
| Hai nhánh có/không | `ifPresentOrElse(...)` | `ifPresentOrElse(...)` | `ifPresentOrElse(...)` |
| Chuyển thành stream 0 hoặc 1 phần tử | `stream()` | `stream()` | `stream()` |

Trong Java 17, `isEmpty()`, `stream()`, `ifPresentOrElse()` và `orElseThrow()` đều có thể được sử dụng. Các kiểu primitive Optional **không cung cấp đầy đủ mọi phép biến đổi** giống `Optional<T>`; chẳng hạn không thể mặc định viết `optionalInt.map(x -> ...)` như với `Optional<Integer>`.

### 3.1. Khi nào dùng `orElse`, `orElseGet` và `orElseThrow`?

```java
OptionalInt optional = IntStream.of(4, 9, 2).max();

int mustExist = optional.orElseThrow(); // 9
int fallback = optional.orElse(-1);     // 9 vì Optional có giá trị

OptionalInt missing = IntStream.empty().min();
int defaultValue = missing.orElse(0);   // 0 theo CHÍNH SÁCH người gọi
```

`orElse(0)` chỉ phù hợp nếu nghiệp vụ định nghĩa **không có phần tử = 0**. Đây không phải định nghĩa toán học của min/max trên tập rỗng. Ví dụ đo nhiệt độ, `0°C` là một phép đo hợp lệ, nên dùng `0` để biểu diễn “không có phép đo” là nguy hiểm.

Nếu dữ liệu bắt buộc không rỗng, thể hiện điều đó bằng ngoại lệ:

```java
int peak = IntStream.of(4, 9, 2)
        .max()
        .orElseThrow(() ->
                new IllegalStateException("Không có dữ liệu tính cực đại"));
```

Đây là đoạn nhúng. `orElseGet(() -> expensiveFallback())` chỉ tính fallback khi Optional rỗng; còn đối số truyền vào `orElse(expensiveFallback())` được đánh giá trước khi gọi `orElse`, ngay cả khi đã có giá trị. Fallback kiểu primitive phải trả đúng kiểu `int`, `long` hoặc `double`.

Khi thiếu dữ liệu là trạng thái bình thường, hãy **giữ nguyên Optional** ở ranh giới API để tầng gọi quyết định. Khi thiếu dữ liệu là lỗi của hợp đồng, dùng `orElseThrow` có thông báo phù hợp.

### 3.2. Không gọi `getAs...()` khi chưa xác thực

```java
OptionalLong empty = LongStream.empty().min();
// empty.getAsLong() -> NoSuchElementException
```

`getAsInt`, `getAsLong` và `getAsDouble` chỉ dùng sau `isPresent()` hoặc khi có bảo đảm miền dữ liệu khác rằng Optional không thể rỗng. Nếu bạn định ném lỗi khi thiếu dữ liệu, `orElseThrow()` truyền đạt ý định rõ hơn.

### 3.3. Xử lý với `ifPresent`, `ifPresentOrElse` và `stream`

```java
OptionalInt maybe = IntStream.of(2, 8, 5).max();

maybe.ifPresent(value -> System.out.println("max=" + value));

maybe.ifPresentOrElse(
        value -> System.out.println("Có giá trị: " + value),
        () -> System.out.println("Dữ liệu rỗng"));

int[] result = maybe.stream().map(x -> x * 2).toArray();
// result là mảng chỉ chứa 16
```

Các đoạn này là ví dụ nhúng dùng `OptionalInt` và `IntStream`. `stream()` hữu ích nếu muốn xử lý tiếp 0 hoặc 1 giá trị bằng pipeline primitive mà không tự kiểm tra nhánh rỗng.

## 4. `Stream<T>.min/max` và bộ so sánh (`Comparator`)

Với stream đối tượng, thư viện không biết quy tắc sắp xếp của từng kiểu; API cần comparator:

```java
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

record Reading(String id, int score) {}

List<Reading> readings = List.of(
        new Reading("sensor-A", 4),
        new Reading("sensor-B", 9),
        new Reading("sensor-C", 3));

Comparator<Reading> byScore =
        Comparator.comparingInt(Reading::score);

Optional<Reading> smallest = readings.stream().min(byScore);
Optional<Reading> largest = readings.stream().max(byScore);

System.out.println(smallest.orElseThrow().id()); // sensor-C
System.out.println(largest.orElseThrow().id());  // sensor-B
```

Đây là **đoạn nhúng** minh họa một `record` Java 17. Nếu đặt record trong cùng một file `.java` với lớp demo khác, bảo đảm tuân thủ quy tắc tên và số lớp `public`.

`Comparator.comparingInt` đọc `int` qua `ToIntFunction`, tránh boxing cần thiết chỉ để so sánh. Với trường `long`/`double`, có các hàm `comparingLong`/`comparingDouble`. `Stream<Integer>` có thể dùng `Comparator.naturalOrder()` để lấy `Optional<Integer>`; `mapToInt(Integer::intValue).min()` phù hợp hơn nếu cần `OptionalInt`.

### 4.1. Khi nhiều phần tử cùng cực trị

Giả sử hai đơn hàng có điểm `9`. `max(byScore)` có thể chọn một trong hai; **không dựa vào phần tử cụ thể được chọn khi comparator coi chúng ngang nhau**, đặc biệt trong xử lý song song hoặc sau khi thứ tự nguồn thay đổi.

Nếu nghiệp vụ yêu cầu một phần tử xác định, quy định tiêu chí phụ:

```java
Comparator<Reading> ranking = Comparator
        .comparingInt(Reading::score)
        .thenComparing(Reading::id);

Reading selected = readings.stream().max(ranking).orElseThrow();
```

Với comparator này, khi score bằng nhau, `max` chọn ID lớn hơn theo thứ tự từ điển. Nếu muốn ưu tiên ID nhỏ hơn khi score bằng nhau, phải định nghĩa lại tiêu chí phụ, ví dụ `thenComparing(Reading::id, Comparator.reverseOrder())` cho trường hợp lấy `max`. Đây là quyết định miền dữ liệu, không phải mặc định của Stream API.

Comparator nên có tính nhất quán, tính bắc cầu và không dựa vào trạng thái thay đổi trong lúc stream đang chạy. Nếu không, kết quả tổng hợp trở nên khó dự đoán.

### 4.2. `Collectors.minBy/maxBy`

Khi đang dùng `collect` hoặc nhóm dữ liệu:

```java
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

Optional<Integer> min = List.of(6, 3, 9)
        .stream()
        .collect(Collectors.minBy(Comparator.naturalOrder()));
```

`Collectors.minBy` trả `Optional<T>` giống `Stream.min(comparator)`; nó hữu ích khi kết hợp với collector khác như `groupingBy`. Nếu chỉ lấy cực trị của toàn stream, gọi trực tiếp `.min(comparator)` hoặc `.max(comparator)` thường đơn giản hơn.

## 5. Dữ liệu rỗng và ý nghĩa nghiệp vụ

Một nguồn có thể rỗng vì hoàn toàn không có bản ghi, vì filter loại hết mọi phần tử, hoặc vì dữ liệu đầu vào lỗi/không có quyền truy cập. Các trường hợp này có thể có cùng kết quả `Optional.empty()` nhưng **nguyên nhân nghiệp vụ khác nhau**.

```java
OptionalInt expensivePeak = IntStream.of(10, 20, 30)
        .filter(x -> x > 100)
        .max(); // OptionalInt.empty()
```

Trước khi chọn fallback, cần trả lời:

- **Không có dữ liệu là trạng thái bình thường?** Trả về `OptionalInt`/`OptionalLong`/`OptionalDouble` cho caller.
- **Dữ liệu bắt buộc phải tồn tại?** Ném lỗi rõ nguyên nhân, chẳng hạn `orElseThrow`.
- **Có đơn vị mặc định được quy định?** Có thể dùng `orElse(fallback)`; ghi rõ fallback là chính sách của miền nghiệp vụ.
- **Cần JSON/REST?** Thiết kế biểu diễn “không có giá trị” trong DTO/API rõ ràng. Không giả định mọi thư viện JSON đều serialize primitive Optional theo cùng một cách; chuyển sang kiểu DTO/giao thức đã định nghĩa khi xuất dữ liệu.

Không thay Optional rỗng bằng một giá trị cực biên như `Integer.MAX_VALUE` hoặc `Integer.MIN_VALUE` rồi không ghi rõ điều kiện tồn tại, vì giá trị cực biên là một giá trị số thực sự hợp lệ trong miền `int`.

## 6. Trường hợp đặc biệt với `DoubleStream.min/max`

`double` không giống số nguyên vì có các giá trị và thứ tự đặc biệt:

### 6.1. `NaN`

Nếu **bất kỳ phần tử** nào trong `DoubleStream` là `Double.NaN`, thì theo hợp đồng Java 17, `min()`/`max()` trả `OptionalDouble` **có giá trị `NaN`**, kể cả khi các phần tử khác hữu hạn.

```java
double result = DoubleStream.of(3.0, Double.NaN, -5.0)
        .max()
        .orElseThrow();

System.out.println(result); // NaN
```

`OptionalDouble` lúc này **không rỗng**. `isPresent()` chỉ cho biết có phần tử được tổng hợp, không khẳng định giá trị là số hữu hạn.

Nếu miền dữ liệu quy định phải bỏ `NaN`, lọc có chủ đích:

```java
OptionalDouble cleanMin = DoubleStream.of(3.0, Double.NaN, -5.0)
        .filter(d -> !Double.isNaN(d))
        .min();
// cleanMin chứa -5.0
```

Nếu không chấp nhận cả vô cực, dùng `Double.isFinite(d)` để chỉ giữ các giá trị hữu hạn. Tuy nhiên, lọc dữ liệu là **quy tắc nghiệp vụ**; với dữ liệu đáng lẽ phải hợp lệ, từ chối một bản ghi có `NaN` có thể thích hợp hơn là âm thầm bỏ qua.

### 6.2. Hai loại số không: `-0.0` và `+0.0`

Java phân biệt dấu của số không ở kiểu `double`:

```java
System.out.println(DoubleStream.of(+0.0, -0.0).min().orElseThrow()); // -0.0
System.out.println(DoubleStream.of(+0.0, -0.0).max().orElseThrow()); // 0.0
```

`DoubleStream.min()` coi `-0.0` nhỏ hơn `+0.0`; `max()` coi `+0.0` lớn hơn `-0.0`, phù hợp với thứ tự của `Double.compare`. Nếu dấu của số 0 có ý nghĩa với phép đo hay phép tính tiếp theo, không xóa dấu một cách tùy tiện.

### 6.3. Vô cực và độ chính xác số dấu phẩy động

`Double.POSITIVE_INFINITY`/`Double.NEGATIVE_INFINITY` có thể tham gia min/max. Kết quả vẫn thuộc kiểu `double` và chịu sai số của biểu diễn số dấu phẩy động. Ví dụ phép tổng hợp **min/max** tự nó không làm tròn thêm như phép cộng liên tục, nhưng từng giá trị nguồn có thể đã bị xấp xỉ trước đó.

Nếu miền dữ liệu là tiền tệ/thập phân cần biểu diễn chính xác, cân nhắc `BigDecimal` trong `Stream<BigDecimal>` cùng comparator tự nhiên thay vì tự động chuyển sang `double`:

```java
Optional<BigDecimal> min = List.of(
        new BigDecimal("0.10"),
        new BigDecimal("0.20"))
        .stream()
        .min(BigDecimal::compareTo);
```

Đây là đoạn nhúng cần `import java.math.BigDecimal; import java.util.List; import java.util.Optional;`. Chú ý `BigDecimal.compareTo` so theo **giá trị số**, trong khi `BigDecimal.equals` còn xét scale; bài toán cực trị thường muốn so giá trị.

## 7. Tính đồng thời min và max bằng `summaryStatistics()`

Nếu cần cả min, max, count, sum và average của cùng một nguồn, có thể lấy tất cả trong **một lần duyệt**:

```java
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

IntSummaryStatistics stats = IntStream.of(7, -2, 13, 13, 0)
        .summaryStatistics();

System.out.println(stats.getCount()); // 5
System.out.println(stats.getMin());   // -2
System.out.println(stats.getMax());   // 13
System.out.println(stats.getSum());   // 31
System.out.println(stats.getAverage()); // 6.2
```

Java có `IntSummaryStatistics`, `LongSummaryStatistics` và `DoubleSummaryStatistics`. `summaryStatistics()` hữu ích khi cần nhiều metric và tránh việc cố tái sử dụng một stream đã bị tiêu thụ.

**Lưu ý với nguồn rỗng:** `IntSummaryStatistics` có `count == 0`; khi chưa có phần tử, `getMin()` trả `Integer.MAX_VALUE`, `getMax()` trả `Integer.MIN_VALUE`. Đây là **giá trị khởi tạo nội bộ**, không phải min/max có nghĩa cho tập rỗng. Với `LongSummaryStatistics`, các giá trị mặc định tương tự theo miền `long`. Với `DoubleSummaryStatistics`, min và max của nguồn rỗng lần lượt là `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY`.

Vì vậy, trước khi sử dụng cực trị:

```java
IntSummaryStatistics stats = IntStream.empty().summaryStatistics();
if (stats.getCount() == 0) {
    System.out.println("Không có dữ liệu");
} else {
    System.out.println(stats.getMin());
}
```

`summaryStatistics()` có thêm chính sách về tổng/average: `int` sum có thể tràn số nguyên, `long` sum cũng có thể tràn, còn phép tính trên `double` có thể mang sai số dấu phẩy động. Vì vậy, chọn công cụ theo yêu cầu phép tổng hợp, không dùng statistics như một giải pháp “chính xác tuyệt đối” cho mọi chỉ số.

### Stream chỉ dùng một lần

Đoạn sau **không hợp lệ** vì một stream không được dùng lại sau terminal operation:

```java
IntStream source = IntStream.of(3, 7, 2);
int min = source.min().orElseThrow();
// source.max(); // IllegalStateException: stream đã bị tiêu thụ
```

Muốn lấy cả min và max, tạo hai stream mới từ nguồn có thể duyệt lại (ví dụ `Arrays.stream(array)` mỗi lần) hoặc dùng `summaryStatistics()` một lượt. Với nguồn I/O chỉ đọc một lần, `summaryStatistics()` thường là hướng hợp lý cho các thống kê được hỗ trợ.

## 8. Chương trình Java 17+ chạy được

Lưu vào **`MinMaxOptionalDemo.java`**:

```java
import java.util.Arrays;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class MinMaxOptionalDemo {
    record Reading(String id, int score) {}

    public static void main(String[] args) {
        int[] scores = {7, -2, 13, 13, 0};

        OptionalInt smallest = Arrays.stream(scores).min();
        OptionalInt biggest = Arrays.stream(scores).max();
        OptionalInt empty = IntStream.empty().min();
        OptionalLong biggestLong =
                LongStream.of(5_000_000_000L, -4L, 9L).max();
        OptionalDouble smallestDouble =
                DoubleStream.of(2.5, -0.0, 7.75).min();

        List<Reading> readings = List.of(
                new Reading("A", 4),
                new Reading("B", 9),
                new Reading("C", 3));
        Comparator<Reading> ranking = Comparator
                .comparingInt(Reading::score)
                .thenComparing(Reading::id);

        Reading minReading = readings.stream()
                .min(ranking).orElseThrow();
        Reading maxReading = readings.stream()
                .max(ranking).orElseThrow();

        IntSummaryStatistics stats =
                Arrays.stream(scores).summaryStatistics();

        System.out.println("int min = " + smallest.orElseThrow());
        System.out.println("int max = " + biggest.orElseThrow());
        System.out.println("empty = " + empty.isEmpty());
        System.out.println("long max = " + biggestLong.orElseThrow());
        System.out.println("double min = " + smallestDouble.orElseThrow());
        System.out.println("reading min = " + minReading);
        System.out.println("reading max = " + maxReading);
        System.out.println("count = " + stats.getCount());
        System.out.println("stats min/max = "
                + stats.getMin() + "/" + stats.getMax());
        System.out.println("NaN max = "
                + DoubleStream.of(3.0, Double.NaN, -5.0)
                              .max().orElseThrow());
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac MinMaxOptionalDemo.java
java MinMaxOptionalDemo
```

Stdout chính xác:

```text
int min = -2
int max = 13
empty = true
long max = 5000000000
double min = -0.0
reading min = Reading[id=C, score=3]
reading max = Reading[id=B, score=9]
count = 5
stats min/max = -2/13
NaN max = NaN
```

**Giải thích:** Các số trong `scores` cho min `-2` và max `13`; `IntStream.empty()` dẫn tới Optional rỗng. `LongStream` giữ được giá trị `5_000_000_000` vượt phạm vi `int`. `DoubleStream` giữ dấu của `-0.0`. Comparator giữ nguyên bản ghi `Reading` gắn với điểm cực trị. `summaryStatistics` đếm năm phần tử và cùng cực trị. Giá trị `NaN` trong nguồn làm `DoubleStream.max()` trả `NaN` thay vì trả `3.0`.

Trong chương trình này, `orElseThrow()` trên các stream không rỗng là hợp lệ vì dữ liệu mẫu đã định rõ. Với nguồn có thể rỗng từ API hoặc database, phải xử lý từng Optional theo chính sách của miền thay vì mặc định `orElseThrow` ở mọi nơi.

## 9. Hiệu năng, song song và thứ tự so sánh

Để tìm riêng min hoặc max, một phép duyệt đầy đủ thông thường có độ phức tạp thời gian **O(n)** và không cần sắp xếp toàn bộ dữ liệu. `stream().sorted().findFirst()` có thể tìm giá trị thấp nhất nhưng phải sắp xếp và thường tốn nhiều tài nguyên hơn `min(comparator)`.

Với `IntStream`/`LongStream`/`DoubleStream`, tránh boxing không cần thiết trong quá trình reduction. Khi dữ liệu đã là `List<Reading>`, việc chuyển sang một primitive stream để lấy giá trị rồi truy ngược về đối tượng có thể tạo thêm vấn đề; dùng comparator trực tiếp để giữ metadata.

`min` và `max` có thể dùng với stream song song nếu nguồn, mapping và comparator được thiết kế an toàn:

```java
OptionalInt min = IntStream.rangeClosed(1, 1_000_000)
        .parallel()
        .min();
// Kết quả có giá trị 1, nhưng việc parallel có nhanh hơn hay không phải đo.
```

Nếu nhiều đối tượng được comparator coi ngang nhau, không dựa vào việc chọn một phần tử cụ thể; hãy tạo tiêu chí phụ nếu cần kết quả duy nhất theo hợp đồng. Không để comparator đọc/ghi shared mutable state hay thay đổi các giá trị đang được so sánh giữa chừng.

Không sử dụng `min`/`max` trên một nguồn đang bị sửa đồng thời thiếu đồng bộ; tính đúng đắn của kết quả còn phụ thuộc vào ràng buộc của collection/source. Với các Stream API, nguồn và các lambda phải tuân thủ nguyên tắc không can thiệp và không có trạng thái dùng chung nguy hiểm.

## 10. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách làm |
| --- | --- | --- |
| Gọi `getAsInt()` trên kết quả của stream rỗng | `NoSuchElementException` | Dùng `orElse`, `orElseThrow`, `ifPresent` hoặc trả Optional |
| Nghĩ `OptionalInt.empty()` tương đương `0` | Báo min/max giả, che mất dữ liệu | Giữ trạng thái vắng mặt rõ ràng |
| Dùng `Integer.MIN_VALUE` làm mặc định max mà không kiểm tra hiện diện | Không phân biệt được rỗng với dữ liệu thật có giá trị cực biên | Trả Optional hoặc thêm flag hiện diện |
| Gọi `.min()` rồi `.max()` trên cùng một Stream | `IllegalStateException` | Tạo stream mới hoặc dùng `summaryStatistics()` |
| Dùng `Stream<T>.min()` không có comparator | Không biên dịch | Truyền `Comparator` hoặc chuyển sang primitive stream phù hợp |
| Gọi `getAsInt` trên `Optional<Integer>` | Không biên dịch | Với object Optional dùng `get`/`orElseThrow`; muốn primitive dùng `mapToInt` |
| Không xử lý `NaN` trong `DoubleStream` | Min/max chứa `NaN` dù các số khác hợp lệ | Định nghĩa chính sách reject/filter theo miền |
| Nhầm `-0.0` bằng `+0.0` về mọi ngữ nghĩa | Kết quả hiển thị và thứ tự cực trị bất ngờ | Xem quy tắc `Double.compare` và hợp đồng ứng dụng |
| Comparator chỉ xét score khi cần kết quả thắng cuộc duy nhất | Các bản ghi cùng score không phân biệt | Thêm tiêu chí phụ bằng `thenComparing` |
| Dùng `summaryStatistics.getMin()` trên tập rỗng mà không xét count | Nhận giá trị khởi tạo nội bộ thay vì min thực | Kiểm tra `getCount() > 0` |
| Dùng `sorted()` để lấy một cực trị | Tốn thời gian/bộ nhớ không cần thiết | Dùng terminal `min` hoặc `max` |
| Đưa `null` vào `mapToInt(Integer::intValue)` | `NullPointerException` khi unboxing | Xác thực, lọc hoặc xử lý null có chủ đích |

## 11. Bài tập tự kiểm tra

1. Cho `IntStream.of(-3, 0, 9, 9)`: `min`, `max` trả gì? Vì sao dùng `OptionalInt` thay vì trả trực tiếp `int`?
2. Sau `.filter(x -> x > 100)` trên dãy trên, Optional của `max()` là gì? Khi nào bạn sẽ dùng `orElseThrow` thay vì `orElse(0)`?
3. Với các `Reading(id, score)` có hai phần tử cùng `score`, hãy định nghĩa tie-breaker để lấy score lớn nhất, rồi ưu tiên ID *nhỏ nhất*.
4. Với `DoubleStream.of(2.0, Double.NaN, -10.0)`, kết quả `.min()` là gì? Sửa pipeline để chỉ xét giá trị hữu hạn.
5. Vì sao `IntStream.empty().summaryStatistics().getMin()` không thể coi là cực tiểu của tập dữ liệu? Bạn cần kiểm tra thuộc tính nào?
6. Một request cần `min`, `max`, `count` trên nguồn chỉ đọc được một lần. Nên gọi ba terminal operation trên cùng stream, hay chọn phương pháp nào?
7. Viết hàm nhận `List<Integer>` và trả `OptionalInt` chứa giá trị lớn nhất, với quy tắc từ chối `null`, không dùng `sorted()`.

**Gợi ý:** (1) min `-3`, max `9`, vì nguồn bất kỳ có thể rỗng. (2) `OptionalInt.empty()`; `orElseThrow` khi thiếu dữ liệu vi phạm hợp đồng. (3) `comparingInt(Reading::score).thenComparing(Reading::id, Comparator.reverseOrder())` rồi `.max(...)`. (4) `NaN`; dùng `.filter(Double::isFinite)` nếu quy tắc miền yêu cầu. (5) `getCount() == 0` có nghĩa chưa có phần tử. (6) `summaryStatistics()` một lần. (7) dùng `stream().mapToInt(Integer::intValue).max()` sau khi bảo đảm không có null.

## 12. Ghi nhớ áp dụng

Để lấy cực trị trên dữ liệu số, ưu tiên `IntStream.min/max`, `LongStream.min/max`, `DoubleStream.min/max` theo kiểu dữ liệu và nhận `OptionalInt`, `OptionalLong`, `OptionalDouble`. Với dữ liệu đối tượng, dùng `Stream<T>.min/max(Comparator)` để giữ toàn bộ đối tượng. Hãy xác định chính sách **thiếu dữ liệu** rõ ràng, đặc biệt khi stream rỗng do filter; không tùy tiện thay bằng 0.

Nếu cần nhiều chỉ số trên cùng stream, dùng `summaryStatistics()` và kiểm tra `count` trước khi đọc ý nghĩa min/max của nguồn rỗng. Với `double`, xác định chính sách cho `NaN`, vô cực và dấu số không. Khi cần thứ tự ổn định cho các bản ghi bằng điểm, định nghĩa comparator có tiêu chí phụ.

Tài liệu API Java SE 17:
- [`IntStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html), [`LongStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/LongStream.html), [`DoubleStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/DoubleStream.html).
- [`OptionalInt`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalInt.html), [`OptionalLong`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalLong.html), [`OptionalDouble`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalDouble.html).
- [`Stream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html), [`IntSummaryStatistics`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IntSummaryStatistics.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-006">SummaryStatistics và thu thập nhiều số đo</a>

<details>
<summary>Xem chi tiết</summary>

# SummaryStatistics và thu thập nhiều số đo trong Java

## 1. Mục tiêu: một lượt dữ liệu, nhiều thống kê

Trong một hệ thống thực tế, cùng một tập số đo thường cần trả lời nhiều câu hỏi: đã nhận **bao nhiêu** giá trị, **tổng** bao nhiêu, **nhỏ nhất/lớn nhất** là gì, và **trung bình** bao nhiêu? Nếu có nhiều thuộc tính đo (thời gian phản hồi, số byte, nhiệt độ), ta còn muốn tổng hợp tất cả trong một lượt duyệt và, khi cần, chia thống kê theo nhóm.

Java cung cấp ba lớp tích lũy thống kê cơ bản trong gói `java.util`:

- `IntSummaryStatistics`: nhận các giá trị `int`.
- `LongSummaryStatistics`: nhận các giá trị `long`.
- `DoubleSummaryStatistics`: nhận các giá trị `double`.

Cả ba cho các thống kê **count, sum, min, max, average**; hỗ trợ `accept(...)` để nhận thêm một giá trị và `combine(...)` để hợp nhất hai bộ thống kê. Chúng không tự tính phương sai, trung vị, phân vị hay số lượng giá trị phân biệt.

Bài này trình bày hai mức sử dụng: (1) lấy nhiều chỉ số thống kê cho **một dãy số**, (2) thu thập đồng thời nhiều trường đo lường của **một dãy bản ghi**, kể cả khi có giá trị cần loại khỏi một chỉ số.

## 2. Chọn đúng kiểu và hiểu các kết quả

| Đầu vào | Bộ tích lũy | Cách tạo nhanh từ stream | Kiểu `getSum()` |
| --- | --- | --- | --- |
| `int` | `IntSummaryStatistics` | `IntStream.summaryStatistics()` | `long` |
| `long` | `LongSummaryStatistics` | `LongStream.summaryStatistics()` | `long` |
| `double` | `DoubleSummaryStatistics` | `DoubleStream.summaryStatistics()` | `double` |

Các getter đều có ý nghĩa tương tự:

| Getter | Ý nghĩa |
| --- | --- |
| `getCount()` | Số phần tử được đưa vào bộ tích lũy, kiểu `long` |
| `getSum()` | Tổng các phần tử đã nhận |
| `getMin()` | Phần tử nhỏ nhất đã nhận |
| `getMax()` | Phần tử lớn nhất đã nhận |
| `getAverage()` | Trung bình cộng, kiểu `double` |

Ví dụ tối thiểu:

~~~java
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

IntSummaryStatistics stats =
        IntStream.of(12, 8, 20, 10).summaryStatistics();

System.out.println(stats.getCount());   // 4
System.out.println(stats.getSum());     // 50
System.out.println(stats.getMin());     // 8
System.out.println(stats.getMax());     // 20
System.out.println(stats.getAverage()); // 12.5
~~~

Đây là **đoạn mã dùng được trong `jshell` hoặc trong một phương thức Java**; ví dụ chương trình độc lập có đủ lớp và `main` ở phần sau.

Không cần duyệt stream năm lần để gọi năm phép `count`, `sum`, `min`, `max`, `average`: chỉ cần một phép `summaryStatistics()`, sau đó đọc các getter trên **cùng một kết quả đã chốt**. Một `Stream` của Java không thể dùng lại sau thao tác kết thúc (terminal operation).

Với `Stream<T>` chứa các đối tượng, dùng các collector của `java.util.stream.Collectors`:

~~~java
// Giả sử đã có Stream<Order> orders và Order có phương thức units().
IntSummaryStatistics stats = orders.collect(
        java.util.stream.Collectors.summarizingInt(Order::units)
);
~~~

Ở đây `Order` là kiểu giả định của ứng dụng, không phải lớp Java chuẩn hay ví dụ chạy độc lập. Các collector tương ứng là `summarizingInt`, `summarizingLong`, `summarizingDouble`.

## 3. Cơ chế hoạt động của bộ tích lũy

Một đối tượng `IntSummaryStatistics` bắt đầu ở trạng thái rỗng; mỗi lần gọi `accept(int)`, nó cập nhật count, sum, min, max. Trung bình được suy ra từ tổng và số lượng khi gọi getter. Nhờ vậy, số lượng ô nhớ bổ sung **không tăng theo số mẫu**: không cần lưu mọi giá trị để tính năm thống kê này.

~~~java
import java.util.IntSummaryStatistics;

IntSummaryStatistics stats = new IntSummaryStatistics();
stats.accept(5);
stats.accept(15);
stats.accept(-5);

System.out.println(stats.getCount());   // 3
System.out.println(stats.getSum());     // 15
System.out.println(stats.getMin());     // -5
System.out.println(stats.getMax());     // 15
System.out.println(stats.getAverage()); // 5.0
~~~

Sau khi nhận thêm giá trị, các getter phản ánh **trạng thái mới**. Do đó, hãy phân biệt rõ:

- **Accumulator**: đối tượng mutable, có thể tiếp tục nhận dữ liệu.
- **Snapshot**: các con số đã sao chép vào DTO/record báo cáo để phát hành cho người tiêu dùng.

Nếu ứng dụng cần một báo cáo bất biến, đừng đưa đối tượng thống kê mutable đang còn được cập nhật cho những phần khác rồi giả định đó là snapshot.

### Hợp nhất các phần đã thu thập

~~~java
IntSummaryStatistics first = new IntSummaryStatistics();
first.accept(2);
first.accept(4);

IntSummaryStatistics second = new IntSummaryStatistics();
second.accept(6);
second.accept(8);

first.combine(second);
System.out.println(first.getCount());   // 4
System.out.println(first.getSum());     // 20
System.out.println(first.getAverage()); // 5.0
~~~

`combine` cập nhật **đối tượng nhận** (`first`) bằng thông tin từ đối tượng còn lại. Nó hữu ích trong pipeline chia dữ liệu thành nhiều phần, nhất là stream song song. Không tự coi các lớp này là thread-safe để nhiều luồng đồng thời gọi `accept` trên **cùng một instance**.

Đối với phép tổng hợp số nguyên thông thường, thứ tự ghép các phần không đổi ý nghĩa toán học nếu không bị tràn. Với số dấu phẩy động `double`, thứ tự cộng có thể làm thay đổi các bit cuối của tổng và trung bình.

## 4. Trạng thái rỗng và cách tránh kết quả gây hiểu nhầm

Nếu không nhận giá trị nào, các getter vẫn trả số chứ không trả `Optional`:

| Lớp | `count` | `sum` | `min` | `max` | `average` |
| --- | ---: | ---: | ---: | ---: | ---: |
| `IntSummaryStatistics` | `0` | `0` | `Integer.MAX_VALUE` | `Integer.MIN_VALUE` | `0.0` |
| `LongSummaryStatistics` | `0` | `0` | `Long.MAX_VALUE` | `Long.MIN_VALUE` | `0.0` |
| `DoubleSummaryStatistics` | `0` | `0.0` | `Double.POSITIVE_INFINITY` | `Double.NEGATIVE_INFINITY` | `0.0` |

Ví dụ:

~~~java
IntSummaryStatistics empty = new IntSummaryStatistics();
System.out.println(empty.getCount()); // 0
System.out.println(empty.getMin());   // 2147483647
System.out.println(empty.getMax());   // -2147483648
~~~

`getAverage() == 0.0` **không có nghĩa** tập dữ liệu có trung bình thực bằng 0 khi `count == 0`. Tương tự, `getMin()` hoặc `getMax()` của bộ rỗng là giá trị sentinel, **không phải phép đo thực**.

Khi xuất báo cáo, hãy luôn xét `getCount() > 0` trước khi trình bày min, max và average. Một API có thể trả `null`, bỏ trường, hoặc dùng một trạng thái `hasData` cho bộ rỗng; lựa chọn nào cũng cần được định nghĩa rõ.

## 5. Tập hợp nhiều số đo từ một bản ghi

Giả sử mỗi bản ghi telemetry có bốn trường:

- `region`: nơi phát sinh mẫu.
- `latencyMs`: độ trễ không âm dưới dạng `int`.
- `bytes`: lượng dữ liệu không âm dưới dạng `long`.
- `temperatureC`: nhiệt độ dưới dạng `double`; hệ thống dùng giá trị không hữu hạn (`NaN`, `±Infinity`) để chỉ mẫu nhiệt độ không hợp lệ.

Báo cáo yêu cầu tổng số dòng, độ trễ (count/min/max/average), tổng byte, nhiệt độ hợp lệ (count/average) và số mẫu nhiệt độ không hợp lệ. **Count của từng đại lượng có thể khác count số dòng** vì có chính sách lọc riêng.

Có thể gọi `stream()` nhiều lần trên cùng một `List` và tạo các báo cáo riêng, nhưng với nguồn chỉ có thể đọc một lần (file streaming, event stream), với dữ liệu lớn hoặc khi muốn kiểm soát chính sách nhất quán, một accumulator chung thuận tiện hơn.

### Chương trình Java 17+ hoàn chỉnh

Lưu mã sau thành `MultiMeasureSummaryDemo.java`:

~~~java
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MultiMeasureSummaryDemo {

    record Reading(String region, int latencyMs, long bytes,
                   double temperatureC) {}

    static final class Metrics {
        long rows;
        long invalidTemperatures;

        final IntSummaryStatistics latency = new IntSummaryStatistics();
        final LongSummaryStatistics bytes = new LongSummaryStatistics();
        final DoubleSummaryStatistics temperature =
                new DoubleSummaryStatistics();

        void accept(Reading reading) {
            if (reading.latencyMs() < 0 || reading.bytes() < 0) {
                throw new IllegalArgumentException(
                        "latencyMs and bytes must be non-negative");
            }

            rows++;
            latency.accept(reading.latencyMs());
            bytes.accept(reading.bytes());

            if (Double.isFinite(reading.temperatureC())) {
                temperature.accept(reading.temperatureC());
            } else {
                invalidTemperatures++;
            }
        }

        Metrics merge(Metrics other) {
            rows += other.rows;
            invalidTemperatures += other.invalidTemperatures;
            latency.combine(other.latency);
            bytes.combine(other.bytes);
            temperature.combine(other.temperature);
            return this;
        }
    }

    static Collector<Reading, Metrics, Metrics> metricsCollector() {
        return Collector.of(Metrics::new, Metrics::accept, Metrics::merge);
    }

    static void print(String label, Metrics m) {
        System.out.printf(
                Locale.ROOT,
                "%s: rows=%d, latency(count=%d,min=%d,max=%d,avg=%.1f), "
                        + "bytes=%d, temp(count=%d,avg=%.1f), invalid=%d%n",
                label,
                m.rows,
                m.latency.getCount(),
                m.latency.getMin(),
                m.latency.getMax(),
                m.latency.getAverage(),
                m.bytes.getSum(),
                m.temperature.getCount(),
                m.temperature.getAverage(),
                m.invalidTemperatures
        );
    }

    public static void main(String[] args) {
        IntSummaryStatistics simple =
                IntStream.of(2, 4, 6).summaryStatistics();

        System.out.printf(
                Locale.ROOT,
                "simple: count=%d sum=%d min=%d max=%d avg=%.1f%n",
                simple.getCount(),
                simple.getSum(),
                simple.getMin(),
                simple.getMax(),
                simple.getAverage()
        );

        List<Reading> readings = List.of(
                new Reading("North", 10, 100L, 21.5),
                new Reading("North", 20, 300L, 22.5),
                new Reading("South", 30, 200L, Double.NaN),
                new Reading("South", 40, 400L, 19.0)
        );

        Metrics all = readings.stream().collect(metricsCollector());
        print("all", all);

        Map<String, Metrics> byRegion = readings.stream().collect(
                Collectors.groupingBy(
                        Reading::region,
                        TreeMap::new,
                        metricsCollector()
                )
        );

        byRegion.forEach(MultiMeasureSummaryDemo::print);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac MultiMeasureSummaryDemo.java
java MultiMeasureSummaryDemo
~~~

Kết quả stdout:

~~~text
simple: count=3 sum=12 min=2 max=6 avg=4.0
all: rows=4, latency(count=4,min=10,max=40,avg=25.0), bytes=1000, temp(count=3,avg=21.0), invalid=1
North: rows=2, latency(count=2,min=10,max=20,avg=15.0), bytes=400, temp(count=2,avg=22.0), invalid=0
South: rows=2, latency(count=2,min=30,max=40,avg=35.0), bytes=600, temp(count=1,avg=19.0), invalid=1
~~~

### Đọc cấu trúc của collector

`Collector.of` được cung cấp ba thành phần:

1. **Supplier `Metrics::new`**: tạo một accumulator mới.
2. **Accumulator `Metrics::accept`**: cập nhật mọi số đo được giao cho một mẫu; một mẫu chỉ đi qua bộ tích lũy chung một lần.
3. **Combiner `Metrics::merge`**: hợp nhất các phần, quan trọng khi collector được sử dụng với stream song song.

Sau `collect`, `all` là kết quả tổng hợp toàn tập. `groupingBy(Reading::region, TreeMap::new, metricsCollector())` tạo **một accumulator theo từng region**, đưa mỗi bản ghi vào nhóm tương ứng, và dùng `TreeMap` để in nhóm theo thứ tự khóa ổn định trong ví dụ. Với k nhóm, bộ nhớ tích lũy phụ thuộc vào số nhóm k (ngoài nguồn dữ liệu), thay vì phải tạo danh sách mọi bản ghi của mỗi nhóm.

Vì `South` có một nhiệt độ `NaN`, tổng số dòng bằng 4 nhưng số mẫu nhiệt độ hợp lệ chỉ bằng 3. `latency` và `bytes` vẫn đếm cả bốn dòng. **Không được dùng `rows` làm mẫu số cho nhiệt độ** nếu đã loại mẫu nhiệt độ không hợp lệ.

Các trường `rows` và `invalidTemperatures` trong ví dụ cũng là bộ đếm `long`. Với luồng dữ liệu rất lớn, hãy có chính sách phát hiện tràn hoặc chia phiên tổng hợp; `rows++` không tự bảo vệ khỏi tràn số.

## 6. Tình huống chỉ cần tổng hợp một trường theo nhóm

Nếu chỉ quan tâm độ trễ, không cần viết custom collector:

~~~java
Map<String, IntSummaryStatistics> latencyByRegion =
        readings.stream().collect(
                Collectors.groupingBy(
                        Reading::region,
                        Collectors.summarizingInt(Reading::latencyMs)
                )
        );
~~~

**Đây là đoạn trích trong phạm vi `main` của chương trình ở trên**, vì `readings` và `Reading` đã được định nghĩa ở đó.

Tương ứng, `Collectors.summarizingLong(Reading::bytes)` trả `LongSummaryStatistics`; `Collectors.summarizingDouble(Reading::temperatureC)` trả `DoubleSummaryStatistics` (nhưng phải chủ động xử lý `NaN` nếu có trong dữ liệu).

Ví dụ dùng `Collectors.filtering` (Java 9+) để loại bỏ mẫu nhiệt độ không hữu hạn **mà vẫn giữ nhóm**, kể cả nhóm không có nhiệt độ hợp lệ:

~~~java
Map<String, DoubleSummaryStatistics> temperatureByRegion =
        readings.stream().collect(
                Collectors.groupingBy(
                        Reading::region,
                        Collectors.filtering(
                                reading -> Double.isFinite(
                                        reading.temperatureC()),
                                Collectors.summarizingDouble(
                                        Reading::temperatureC)
                        )
                )
        );
~~~

Cần phân biệt `stream().filter(...).collect(groupingBy(...))` với `groupingBy(..., filtering(...))`: cách đầu có thể **bỏ hẳn nhóm** không còn dòng sau lọc; cách sau có thể giữ nhóm với một downstream accumulator rỗng. Hành vi nào phù hợp phụ thuộc ý nghĩa báo cáo.

Nếu muốn gộp hai collector khác loại trong một lượt với Java 12+, `Collectors.teeing` có thể phối hợp hai downstream collector và một hàm kết hợp kết quả. Khi cần ba hoặc nhiều phép đo với điều kiện chấp nhận khác nhau, custom collector như `Metrics` thường rõ ràng hơn.

## 7. Tổng hợp nhiều số đo đúng cách khi chạy song song

Khi sử dụng `parallelStream()` hay `stream().parallel()`, framework có thể tạo nhiều accumulator độc lập rồi gọi combiner để hợp nhất. Đối với custom collector, phải thỏa ba yêu cầu:

- **Accumulator đúng cho từng mẫu**: mỗi `accept` xử lý chính xác các chỉ số và điều kiện lọc của mẫu.
- **Combine đúng về ngữ nghĩa**: `merge` cộng bộ đếm, gọi `combine` cho từng loại thống kê, và trả về trạng thái hợp nhất.
- **Không phụ thuộc thứ tự** nếu muốn kết quả ổn định theo ngữ nghĩa trong parallel; các phép tính `double` vẫn có thể khác các chữ số cuối do thứ tự cộng.

Ví dụ với collector đã khai báo:

~~~java
Metrics parallel = readings.parallelStream()
        .collect(metricsCollector());
~~~

Đây là đoạn trích cùng phạm vi `main` ví dụ hoàn chỉnh. `Collector.of` ở trên **không** khai báo `CONCURRENT`: framework có thể xử lý trên nhiều accumulator riêng rồi merge, thay vì cho nhiều thread cùng sửa một `Metrics`. Tuy nhiên, không được chia sẻ một `Metrics` và gọi `accept` đồng thời từ nhiều thread bên ngoài cơ chế collector nếu không có đồng bộ.

Không tự thêm đặc tính `Collector.Characteristics.CONCURRENT` vào collector mutable này. Với `groupingByConcurrent`, quy tắc container, tính kết hợp và thread safety của downstream cần được xem xét cẩn thận; không thể suy từ việc dùng parallel stream rằng mọi đối tượng thống kê đều thread-safe.

Một custom accumulator có các phép cộng không giao hoán hay phụ thuộc thứ tự (ví dụ ghép chuỗi theo thứ tự đến) sẽ cần thiết kế combiner chặt chẽ hơn. Với cộng số thực, ngay cả khi phép cộng có tính kết hợp trên số thực toán học, **cộng floating point hữu hạn không hoàn toàn kết hợp**; khác biệt nhỏ giữa execution plan có thể xuất hiện.

## 8. Độ chính xác, tràn số và dữ liệu bất thường

### `IntSummaryStatistics` dùng `long` cho tổng

~~~java
IntSummaryStatistics stats = java.util.stream.IntStream
        .of(Integer.MAX_VALUE, Integer.MAX_VALUE)
        .summaryStatistics();

System.out.println(stats.getSum()); // 4294967294
~~~

Tổng không bị giới hạn trong miền `int` ở trường hợp này vì `getSum()` của `IntSummaryStatistics` là `long`. Tuy nhiên, nếu nhận số lượng cực lớn hoặc tổng vượt `Long.MAX_VALUE`/`Long.MIN_VALUE`, kiểu `long` vẫn có thể tràn.

`LongSummaryStatistics` cũng trả tổng `long`, **không** tự nâng thành `BigInteger`. Nếu bài toán cần tổng chính xác không giới hạn miền `long`, hãy thiết kế bộ tích lũy `BigInteger` riêng. Khi tổng thập phân cần chính xác, dùng `BigDecimal` với một accumulator phù hợp, không đưa giá trị qua `double` chỉ để dùng `DoubleSummaryStatistics`.

### `DoubleSummaryStatistics` và sai số dấu phẩy động

`DoubleSummaryStatistics` tính tổng và trung bình từ giá trị `double`. Nó có biện pháp giảm sai số tích lũy trong tính tổng, nhưng vẫn chịu giới hạn binary64; thứ tự nhận/ghép có thể ảnh hưởng đến những chữ số cuối. Nếu dữ liệu tài chính yêu cầu chính xác từng đơn vị nhỏ nhất, đây không phải công cụ thay thế `BigDecimal`.

Nếu chuỗi số đo chứa `NaN`, `DoubleSummaryStatistics` có thể trả `NaN` cho min, max, sum và average. `+Infinity`, `-Infinity` có thể làm kết quả vô hạn hoặc `NaN` (ví dụ khi trộn vô cực trái dấu). Chỉ lọc giá trị không hữu hạn khi **hợp đồng của hệ thống cho phép loại mẫu**; với ứng dụng khác, invalid values nên dẫn đến lỗi hoặc được ghi thành metric lỗi riêng.

Ví dụ một phép lọc thận trọng:

~~~java
DoubleSummaryStatistics validTemperature =
        java.util.stream.DoubleStream.of(
                21.5, Double.NaN, Double.POSITIVE_INFINITY, 22.5
        )
        .filter(Double::isFinite)
        .summaryStatistics();

System.out.println(validTemperature.getCount());   // 2
System.out.println(validTemperature.getAverage()); // 22.0
~~~

Ghi rõ rằng `getCount() == 2` ở đây là số phần tử **đã qua lọc**, không phải số sự kiện thô ban đầu (4).

### Không tính một số đo từ mẫu số không phù hợp

Nếu nguồn có một số trường là tùy chọn hoặc có chính sách lọc riêng, ví dụ 100 bản ghi nhưng chỉ 72 bản ghi có giá trị nhiệt độ hữu hạn, trung bình nhiệt độ phải dùng **72 mẫu nhiệt độ**. Tổng số bản ghi 100 là một chỉ số khác.

Với phân tích cần thêm percentile hoặc median, `SummaryStatistics` không đủ. Tính median/percentile chính xác thường cần giữ thông tin phân phối hoặc cấu trúc dữ liệu hỗ trợ; phương sai và độ lệch chuẩn cũng cần accumulator riêng (chẳng hạn thuật toán online có hỗ trợ merge). Không suy ra những đại lượng này chỉ từ count/sum/min/max/average.

## 9. Thiết kế API và kiểm thử cho báo cáo thống kê

Nên phân biệt dạng **trạng thái thu thập** với dạng **báo cáo đã chốt**. Một DTO có thể trả:

~~~json
{
  "region": "North",
  "rows": 2,
  "latency": {
    "count": 2,
    "minMs": 10,
    "maxMs": 20,
    "averageMs": 15.0
  },
  "bytes": {
    "count": 2,
    "sum": 400
  },
  "temperature": {
    "validCount": 2,
    "averageC": 22.0,
    "invalidCount": 0
  }
}
~~~

Chỉ tạo các trường `min`, `max`, `average` khi `count > 0`, hoặc thống nhất biểu diễn null cho thống kê rỗng. Nếu JSON được dùng với client JavaScript, lưu ý tổng `long` có thể vượt giới hạn số nguyên an toàn của `Number`; với số tổng byte đặc biệt lớn và yêu cầu bảo toàn chính xác, có thể truyền dưới dạng chuỗi decimal kèm schema rõ ràng.

### Bộ kiểm thử nên có

1. **Luồng rỗng:** count = 0, không diễn giải sentinel min/max như phép đo.
2. **Một mẫu:** min = max = giá trị; average tương ứng; sum đúng.
3. **Giá trị âm và dương:** min/max vẫn chính xác.
4. **Nhiều trường với missing/invalid:** mỗi trường có count riêng; tổng dòng vẫn đúng.
5. **Hợp nhất hai accumulator:** tổng hợp từng phần sau `combine` tương đương thống kê của dữ liệu kết hợp, trong giới hạn độ chính xác số thực.
6. **Một nhóm hoàn toàn invalid ở một trường:** nhóm còn tồn tại và thông báo count hợp lệ bằng 0 nếu chính sách downstream giữ nhóm.
7. **Dữ liệu lớn và biên kiểu:** kiểm thử các số `int` lớn, số `long` gần giới hạn và giá trị `double` không hữu hạn.

Mô hình kiểm chứng thủ công cho ví dụ chính: có 4 bản ghi, `latency` bằng 10, 20, 30, 40 nên tổng là 100 và trung bình là 25; byte là 100 + 300 + 200 + 400 = 1000. Ba nhiệt độ hợp lệ 21.5, 22.5, 19.0 có tổng 63.0 và trung bình 21.0; mẫu nhiệt độ thứ tư được báo vào `invalidTemperatures`, không được đưa vào tổng.

## 10. Lỗi phổ biến và cách tránh

- **Duyệt một stream nhiều lần:** `IntStream.sum()` và sau đó `IntStream.average()` trên cùng instance sẽ không hoạt động. Gọi `summaryStatistics()` một lần hoặc tạo lại stream từ nguồn có thể lặp.
- **Tưởng min/max của thống kê rỗng là giá trị thực:** luôn xét count trước.
- **Trộn count dòng với count mẫu hợp lệ:** thu thập số mẫu được dùng của từng chỉ số và số mẫu bị loại.
- **Tin rằng `DoubleSummaryStatistics` chính xác tuyệt đối:** nó xử lý `double`, có thể chịu sai số, `NaN`, vô cực và ảnh hưởng thứ tự cộng.
- **Tin rằng `LongSummaryStatistics.getSum()` không thể tràn:** tổng vẫn là `long`.
- **Dùng một instance mutable chung giữa các thread:** chỉ dùng collector có supplier/combiner đúng hợp đồng, hoặc tự đồng bộ trong kiến trúc concurrent phù hợp.
- **Lọc rồi quên dữ liệu mất mát:** nếu bỏ `NaN`, nên có thêm chỉ số đếm mẫu lỗi/không hợp lệ.
- **Lẫn lộn thống kê mô tả với phép đo phân phối:** bộ `SummaryStatistics` chỉ hỗ trợ các getter có sẵn, không suy ra chính xác median/percentile từ đó.

### Câu hỏi tự kiểm tra

1. Với `IntStream.of(4, 8, 12).summaryStatistics()`, năm getter trả gì?
2. Một nhóm có 5 bản ghi nhưng chỉ 3 nhiệt độ hữu hạn. `getCount()` của `DoubleSummaryStatistics` sau khi lọc nên là bao nhiêu?
3. Có thể gọi `getMin()` cho bộ rỗng không? Nếu có, tại sao không nên công bố nó như kết quả đo?
4. Muốn thống kê độ trễ theo `region` mà không viết accumulator riêng, dùng cặp collector nào?
5. Tại sao muốn tổng chính xác `BigDecimal` thì không nên chuyển tất cả giá trị sang `double` rồi dùng `DoubleSummaryStatistics`?
6. Khi chuyển sang `parallelStream()`, phần nào của custom collector phải chính xác để không làm mất mẫu?

**Đáp án:** (1) count = 3, sum = 24, min = 4, max = 12, average = 8.0. (2) count = 3. (3) Có, nhưng trả sentinel theo kiểu số; cần kiểm tra count = 0. (4) `Collectors.groupingBy(..., Collectors.summarizingInt(...))`. (5) `double` không giữ chính xác mọi số thập phân. (6) Supplier, accumulator và đặc biệt combiner cần bao phủ toàn bộ các trường thống kê và bộ đếm.

Điểm cần ghi nhớ: `SummaryStatistics` giúp **thu thập count/sum/min/max/average trong một lượt**, còn việc thu thập **nhiều trường đo trên cùng bản ghi** có thể xây dựng bằng một collector tổng hợp. Để báo cáo đáng tin, luôn ghi rõ cách lọc từng chỉ số, điều kiện dữ liệu rỗng, giới hạn kiểu số và cách hợp nhất kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-007">Thứ tự cộng ảnh hưởng tổng dấu phẩy động</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự cộng ảnh hưởng tổng dấu phẩy động trong Java

## 1. Vấn đề và mục tiêu

Trong toán học, phép cộng số thực có tính kết hợp: \((a+b)+c=a+(b+c)\). Với kiểu `double` hoặc `float` trong Java, **thay đổi thứ tự cộng có thể làm thay đổi kết quả được lưu**. Nguyên nhân là máy tính lưu số dấu phẩy động bằng một số bit hữu hạn và phải làm tròn các kết quả trung gian không biểu diễn chính xác được.

Đặc tính này quan trọng khi tổng hợp hàng triệu bản ghi, tính điểm số, thống kê, xử lý nhiều giá trị có độ lớn chênh lệch, thay đổi thứ tự phần tử hoặc gộp kết quả từ nhiều tác vụ song song.

Sau bài học, người đọc có thể giải thích nguồn gốc của sai số, tự tái hiện kết quả phụ thuộc thứ tự bằng Java 17, phân biệt những chiến lược cải thiện độ chính xác và quyết định khi nào cần dùng `BigDecimal` thay vì `double`.

## 2. Giới hạn của biểu diễn dấu phẩy động

Java sử dụng định dạng dấu phẩy động nhị phân tương ứng IEEE 754 cho `float` (32 bit) và `double` (64 bit). `float` có khoảng 24 bit chính xác significand, còn `double` có 53 bit (xấp xỉ 15–17 chữ số thập phân có nghĩa).

Các giá trị như `0.1`, `0.2` không có biểu diễn nhị phân hữu hạn chính xác. Khi tạo một `double` bằng literal `0.1`, máy lưu giá trị nhị phân gần nhất. Mỗi phép cộng có thể tiếp tục sinh kết quả nằm giữa các giá trị biểu diễn được, nên phải làm tròn.

**Khoảng cách giữa hai số biểu diễn liên tiếp thay đổi theo độ lớn của số.** Khi tổng đang rất lớn, thêm một số nhỏ có thể không làm thay đổi tổng. `Math.ulp(x)` cung cấp kích thước một bước (*unit in the last place*) tại `x`.

### Ví dụ Java 17: một số hạng nhỏ bị mất

Lưu thành `FloatingUlpDemo.java`:

~~~java
public class FloatingUlpDemo {
    public static void main(String[] args) {
        double large = 1.0e16;

        System.out.println("ulp = " + Math.ulp(large));
        System.out.println("unchanged = " + (large + 1.0 == large));
        System.out.println("add before cancel = "
                + ((large + 1.0) - large));
        System.out.println("cancel before add = "
                + ((large - large) + 1.0));
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac FloatingUlpDemo.java
java FloatingUlpDemo
~~~

Đầu ra:

~~~text
ulp = 2.0
unchanged = true
add before cancel = 0.0
cancel before add = 1.0
~~~

Cả `large` và `1.0` đều có thể biểu diễn trong `double`. Nhưng quanh `1.0e16`, bước biểu diễn là `2.0`. Khi cộng `1.0`, kết quả đúng nằm ở điểm giữa hai giá trị biểu diễn được; phép làm tròn đưa kết quả về `large`. Vì vậy đóng góp `1.0` không còn để phép trừ phía sau khôi phục.

## 3. Không kết hợp: cách đặt ngoặc và thứ tự làm thay đổi tổng

Khi ký hiệu `fl` là thao tác làm tròn về kiểu dấu phẩy động, hai biểu thức

\[
\mathrm{fl}(\mathrm{fl}(a+b)+c)
\quad\text{và}\quad
\mathrm{fl}(a+\mathrm{fl}(b+c))
\]

có các bước trung gian khác nhau. Chúng không nhất thiết cho cùng kết quả.

### Ví dụ Java 17: cùng ba số, hai tổng

Lưu thành `FloatingAssociativityDemo.java`:

~~~java
public class FloatingAssociativityDemo {
    public static void main(String[] args) {
        double a = 1.0e16;
        double b = -1.0e16;
        double c = 1.0;

        double left = (a + b) + c;
        double right = a + (b + c);

        System.out.println("left = " + left);
        System.out.println("right = " + right);
        System.out.println("same = " + (left == right));
    }
}
~~~

~~~bash
javac FloatingAssociativityDemo.java
java FloatingAssociativityDemo
~~~

Đầu ra:

~~~text
left = 1.0
right = 0.0
same = false
~~~

Tổng toán học là `1`. Vế trái hủy hai số lớn trước rồi cộng `1`. Vế phải cộng `1` với số lớn âm trước, khiến đóng góp nhỏ bị làm tròn mất. Khi đổi **thứ tự phần tử** từ `[1e16, 1, -1e16]` sang `[1e16, -1e16, 1]` trong một vòng cộng trái sang phải, kết quả cũng có thể thay đổi từ `0.0` sang `1.0`.

**Hệ quả:** không thể áp dụng tùy tiện phép biến đổi đại số của cộng số thực cho `double` rồi kỳ vọng kết quả từng bit luôn giống nhau. Java đánh giá biểu thức với những phép toán trung gian cụ thể; mỗi bước có thể mất thông tin.

## 4. Sai số tích lũy khi cộng nhiều lần

Ngay cả khi không có số rất lớn, việc lặp phép cộng có thể tạo sai số khác so với một phép nhân tương ứng. Ví dụ `0.1` là số nhị phân gần đúng; mười phép cộng có cách làm tròn khác với phép nhân `10 * 0.1`.

Lưu thành `RepeatedFloatingSumDemo.java`:

~~~java
public class RepeatedFloatingSumDemo {
    public static void main(String[] args) {
        double sum = 0.0;
        for (int i = 0; i < 10; i++) {
            sum += 0.1;
        }
        double product = 10 * 0.1;

        System.out.println("repeated = " + sum);
        System.out.println("product = " + product);
        System.out.println("equal = " + (sum == product));
    }
}
~~~

~~~bash
javac RepeatedFloatingSumDemo.java
java RepeatedFloatingSumDemo
~~~

Đầu ra:

~~~text
repeated = 0.9999999999999999
product = 1.0
equal = false
~~~

`println(double)` hiển thị chuỗi decimal ngắn cho phép chuyển lại về cùng `double`, không hiển thị đầy đủ mọi bit nhị phân. Việc `println(0.1)` in `0.1` **không có nghĩa 0.1 được lưu chính xác**.

Lỗi làm tròn không nhất thiết chỉ tăng dần: có lần sai số cùng chiều, có lần bù trừ nhau. Kết quả phụ thuộc độ lớn, dấu và thứ tự của các số được cộng.

## 5. Mất số nhỏ và cancellation

Có hai hiện tượng cần tách bạch:

1. **Swamping (số nhỏ bị nuốt):** một số hạng rất nhỏ so với tổng hiện tại có thể nhỏ hơn độ phân giải của `double` tại đó, khiến phép cộng không thay đổi kết quả.
2. **Cancellation (triệt tiêu):** hai giá trị gần đối nhau cho tổng nhỏ. Những lỗi có sẵn ở các số hạng hoặc phép tính trung gian có thể lớn tương đối so với kết quả nhỏ.

Nếu chỉ cộng các số dương, đưa các số nhỏ lên trước thường có thể giảm việc mất đóng góp nhỏ. Với dữ liệu gồm cả số âm/dương, **không có một thứ tự sắp xếp luôn tối ưu**, vì vị trí các cặp triệt tiêu cũng thay đổi.

Sai số phải được đánh giá trong bối cảnh mục tiêu. Tổng `0.0` thay vì `1.0` là lỗi rất lớn nếu tổng đúng là 1, dù từng số hạng ban đầu có độ lớn `1e16`.

## 6. Cộng bù Kahan

**Kahan summation** thêm biến `correction` để ước lượng phần đóng góp bị bỏ khi cộng. Phần bù đó được đưa vào phép cộng tiếp theo nhằm giảm sai số tích lũy trong nhiều trường hợp.

Cơ chế:

~~~text
sum = 0
correction = 0
for each x:
    adjusted = x - correction
    next = sum + adjusted
    correction = (next - sum) - adjusted
    sum = next
return sum
~~~

Đây là pseudocode, không phải chương trình Java hoàn chỉnh. Các bước vẫn thực hiện bằng `double`, nên **Kahan không bảo đảm đúng tuyệt đối với mọi input**.

### Ví dụ Java 17: Kahan cải thiện tổng lặp

Lưu thành `KahanSummationDemo.java`:

~~~java
public class KahanSummationDemo {
    static double naive(int count, double value) {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            sum += value;
        }
        return sum;
    }

    static double kahan(int count, double value) {
        double sum = 0.0;
        double correction = 0.0;
        for (int i = 0; i < count; i++) {
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }
        return sum;
    }

    public static void main(String[] args) {
        System.out.println("naive = " + naive(10, 0.1));
        System.out.println("kahan = " + kahan(10, 0.1));
    }
}
~~~

~~~bash
javac KahanSummationDemo.java
java KahanSummationDemo
~~~

Đầu ra:

~~~text
naive = 0.9999999999999999
kahan = 1.0
~~~

Bộ số này minh họa lợi ích của phép cộng bù. Tuy nhiên, kết quả `1.0` không chứng minh thuật toán đã khôi phục mọi giá trị decimal đầu vào; `0.1` vốn được biểu diễn gần đúng khi chuyển thành `double`.

## 7. Neumaier: cải thiện khi có số lớn và số nhỏ xen kẽ

**Neumaier summation** là một biến thể cộng bù. Nó theo dõi độ lớn tương đối của tổng tạm và số mới để tính phần bù. Với `[1e16, 1, -1e16]`, cả phép cộng thường lẫn Kahan cơ bản có thể cho `0.0`, nhưng Neumaier có thể giữ lại `1.0`.

### Ví dụ Java 17: ba kết quả khác nhau

Lưu thành `CompensatedSummationDemo.java`:

~~~java
public class CompensatedSummationDemo {
    static double naive(double[] values) {
        double sum = 0.0;
        for (double x : values) {
            sum += x;
        }
        return sum;
    }

    static double kahan(double[] values) {
        double sum = 0.0;
        double correction = 0.0;
        for (double x : values) {
            double adjusted = x - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }
        return sum;
    }

    static double neumaier(double[] values) {
        double sum = 0.0;
        double correction = 0.0;
        for (double x : values) {
            double next = sum + x;
            if (Math.abs(sum) >= Math.abs(x)) {
                correction += (sum - next) + x;
            } else {
                correction += (x - next) + sum;
            }
            sum = next;
        }
        return sum + correction;
    }

    public static void main(String[] args) {
        double[] data = {1.0e16, 1.0, -1.0e16};

        System.out.println("naive = " + naive(data));
        System.out.println("kahan = " + kahan(data));
        System.out.println("neumaier = " + neumaier(data));
    }
}
~~~

~~~bash
javac CompensatedSummationDemo.java
java CompensatedSummationDemo
~~~

Đầu ra:

~~~text
naive = 0.0
kahan = 0.0
neumaier = 1.0
~~~

Neumaier có thể giữ phần nhỏ trong biến bù trong tình huống mà Kahan cơ bản vẫn đánh mất nó. Dù vậy, cả hai không thể bảo đảm tổng toán học chính xác trong mọi trường hợp, đặc biệt với `NaN`, `Infinity`, overflow, underflow hoặc dữ liệu đã mang sai số từ trước. Thuật toán phải được thử với đặc điểm dữ liệu thực tế.

## 8. Pairwise summation và chia khối

**Pairwise summation** thay chuỗi cộng dài từ trái sang phải bằng cây phép cộng: chia mảng thành hai nửa, cộng mỗi nửa, sau đó cộng hai kết quả. Trong nhiều bài toán tổng các số không âm, phương pháp này giảm số bước cộng liên tiếp trên một nhánh và có thể giảm lỗi tích lũy.

Pseudocode:

~~~text
pairwise(values):
    if empty: return 0
    if only one value: return value
    split into left and right halves
    return pairwise(left) + pairwise(right)
~~~

Mỗi phép cộng trong cây vẫn làm tròn. **Pairwise không tốt hơn mọi thứ tự** trên mọi bộ số âm/dương, vì việc chia nhóm có thể di chuyển vị trí cancellation. Nếu hệ thống cần kết quả tái lập từng bit, phải cố định **cây cộng, kích thước khối và thứ tự các phần tử**, chứ không chỉ ghi “dùng pairwise”.

## 9. Stream, reduce và tổng song song

Trong Java, tính tổng với Stream có nhiều cách và không nhất thiết dùng cùng thứ tự:

- Vòng `for` trên mảng có trình tự xác định cộng theo thứ tự vòng lặp.
- `Arrays.stream(values).reduce(0.0, Double::sum)` giảm các giá trị bằng cộng. Hợp đồng giảm song song thường giả định phép kết hợp là associative; cộng `double` không có tính kết hợp về mặt số học máy, nên các cây gộp có thể cho kết quả khác.
- `DoubleStream.sum()` theo API Java sử dụng một thuật toán cộng có bù, giúp cải thiện nhiều tình huống nhưng không loại bỏ ảnh hưởng của thứ tự hoặc giới hạn độ chính xác.
- `Collectors.summingDouble(...)` cũng có thể chịu ảnh hưởng từ thứ tự và độ lớn các giá trị.

### Ví dụ Java 17: cùng dữ liệu, các API khác nhau

Lưu thành `FloatingStreamDemo.java`:

~~~java
import java.util.Arrays;

public class FloatingStreamDemo {
    public static void main(String[] args) {
        double[] data = {1.0e16, 1.0, -1.0e16};

        double reduce = Arrays.stream(data)
                .reduce(0.0, Double::sum);
        double specialized = Arrays.stream(data).sum();
        double parallel = Arrays.stream(data).parallel()
                .reduce(0.0, Double::sum);

        System.out.println("reduce = " + reduce);
        System.out.println("sum finite = " + Double.isFinite(specialized));
        System.out.println("parallel finite = " + Double.isFinite(parallel));
    }
}
~~~

~~~bash
javac FloatingStreamDemo.java
java FloatingStreamDemo
~~~

Đầu ra:

~~~text
reduce = 0.0
sum finite = true
parallel finite = true
~~~

Hai phép kiểm tra `isFinite` tránh giả định một tổng cụ thể cho thuật toán và cách phân hoạch có thể khác nhau. Dữ liệu trong ví dụ đều hữu hạn, không gây overflow. Không suy diễn rằng tổng cụ thể của `parallel().reduce(...)` luôn giống tổng vòng lặp trên mọi hệ thống hay mọi kích thước đầu vào.

Khi tính song song mà cần kết quả ổn định, cố định phân hoạch và phương thức gộp hoặc dùng bộ tích lũy đáp ứng yêu cầu độ chính xác. Độ ổn định phải được xác minh bằng kiểm thử thực tế.

## 10. BigDecimal khi cần phép cộng decimal chính xác

Nếu bài toán cần tính chính xác **giá trị thập phân đầu vào**, ví dụ tiền, `BigDecimal` là giải pháp thường phù hợp. Tạo BigDecimal từ chuỗi decimal và dùng `add(...)` không truyền `MathContext` để cộng chính xác các giá trị decimal đã lưu.

### Ví dụ Java 17: tổng không phụ thuộc thứ tự với phép cộng decimal chính xác

Lưu thành `BigDecimalOrderDemo.java`:

~~~java
import java.math.BigDecimal;

public class BigDecimalOrderDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("10000000000000000");
        BigDecimal b = BigDecimal.ONE;
        BigDecimal c = new BigDecimal("-10000000000000000");

        BigDecimal first = a.add(b).add(c);
        BigDecimal second = a.add(c).add(b);

        System.out.println("first = " + first.toPlainString());
        System.out.println("second = " + second.toPlainString());
        System.out.println("same value = "
                + (first.compareTo(second) == 0));
    }
}
~~~

~~~bash
javac BigDecimalOrderDemo.java
java BigDecimalOrderDemo
~~~

Đầu ra:

~~~text
first = 1
second = 1
same value = true
~~~

Phải hiểu phạm vi của kết luận:

- `new BigDecimal("0.1")` giữ đúng giá trị thập phân 0.1. `new BigDecimal(0.1)` giữ chính xác **giá trị nhị phân gần đúng của kiểu double**, không phải số decimal 0.1 lý tưởng.
- `BigDecimal.valueOf(double)` dùng biểu diễn chuỗi chuẩn của double nhưng không phục hồi những thông tin đã mất khi làm phép tính double trước đó.
- Nếu dùng `add(other, MathContext)` với precision giới hạn, **mỗi lần cộng có thể làm tròn**, khiến tổng BigDecimal cũng có thể phụ thuộc cách nhóm.
- BigDecimal dùng nhiều chi phí hơn double trong nhiều bài toán. Không tự động chuyển mọi thống kê dấu phẩy động sang BigDecimal khi dung sai và hiệu năng cho phép dùng double.

Với tiền tệ, cần xác định thời điểm làm tròn theo nghiệp vụ. **Làm tròn từng dòng rồi cộng có thể khác cộng chính xác trước rồi làm tròn tổng.**

## 11. Đo sai số và đặt kiểm thử đúng

Sai số tuyệt đối so với tham chiếu chính xác hơn:

\[
E_{\mathrm{abs}} = |S_{\mathrm{computed}}-S_{\mathrm{reference}}|.
\]

Sai số tương đối bằng sai số tuyệt đối chia cho \(|S_{\mathrm{reference}}|\), nhưng không phù hợp khi tổng tham chiếu bằng hoặc rất gần 0. Các bộ dữ liệu có cancellation thường cần đánh giá cả sai số tuyệt đối và các tiêu chuẩn gắn với nghiệp vụ.

Một công thức dung sai gần đúng thường gặp:

\[
|a-b| \le \epsilon_{\mathrm{abs}}+
\epsilon_{\mathrm{rel}}\max(|a|,|b|).
\]

Không chọn `1e-9` làm ngưỡng mặc định cho mọi trường hợp: dung sai phải phụ thuộc độ lớn dữ liệu, số phép tính và mục tiêu. Kiểm tra `NaN`, vô cực, tràn số, underflow và zero có dấu theo hợp đồng bài toán nếu đầu vào có thể chứa chúng.

Một kế hoạch thử nghiệm hữu ích là chạy cùng bộ số ở thứ tự gốc, đảo ngược, sắp xếp theo độ lớn và chia khối; so sánh với giá trị tham chiếu chính xác hơn. Bao gồm cả dữ liệu `[1e16, 1, -1e16]`, nhiều số nhỏ lặp lại và nhiều số âm/dương gần triệt tiêu.

## 12. Khi nào chọn cách tính nào?

| Yêu cầu | Cách tiếp cận | Giới hạn |
| --- | --- | --- |
| Tổng double đơn giản, chấp nhận dung sai | Vòng lặp cộng theo thứ tự cố định | Có thể tích lũy sai số |
| Nhiều phép cộng double | Kahan, pairwise hoặc phương pháp cộng bù khác | Không bảo đảm đúng tuyệt đối |
| Số lớn/nhỏ âm/dương xen kẽ | Neumaier hoặc bộ tích lũy phù hợp | Kiểm thử cancellation và ngoại lệ |
| Tính toán song song | Cố định cách chia và cây gộp nếu cần tái lập | Thay đổi phân hoạch có thể đổi tổng |
| Giá trị tiền/decimal phải chính xác | BigDecimal từ nguồn decimal, cộng chính xác trước làm tròn | Tăng chi phí xử lý và bộ nhớ |
| Cần tổng đúng bất kể thứ tự | Thuật toán tích lũy chính xác phù hợp hợp đồng | Cần giải pháp và kiểm chứng chuyên biệt |

Không có một cách cộng nào tốt nhất cho mọi bộ số. Kết quả phải phù hợp sai số cho phép, độ trễ, khả năng tái lập và đặc điểm dữ liệu.

## 13. Lỗi thường gặp

1. **Cho rằng `double` cộng có tính kết hợp hoàn toàn:** mỗi phép cộng trung gian có thể làm tròn.
2. **Nghĩ tổng luôn giống nhau khi chỉ đổi thứ tự phần tử:** các số nhỏ có thể bị mất ở vị trí khác.
3. **Dùng `parallel().reduce` và mong từng bit giống vòng lặp tuần tự:** cây gộp có thể khác.
4. **Tưởng `DoubleStream.sum()` cho kết quả số thực chính xác:** thuật toán có bù vẫn chịu giới hạn nhị phân.
5. **Sắp xếp mọi số nhỏ trước rồi coi như đã giải quyết hoàn toàn:** có số âm/dương và cancellation làm kết quả phức tạp.
6. **Tin Kahan luôn đủ:** bộ `[1e16, 1, -1e16]` có thể cần kỹ thuật bù mạnh hơn.
7. **Tưởng `println(0.1)` chứng minh double lưu chính xác 0.1:** chuỗi hiển thị là biểu diễn decimal phù hợp, không phải bit lưu trữ.
8. **Chuyển một double đã sai số sang BigDecimal để mong phục hồi dữ liệu:** thông tin đã mất không tự quay lại.
9. **Để MathContext làm tròn giữa các lần cộng BigDecimal mà không xét thứ tự:** phép cộng sau làm tròn cũng có thể không kết hợp.
10. **Bỏ qua NaN, Infinity, overflow hoặc dữ liệu rất chênh lệch:** chúng có thể phá những giả định của phép tính gần đúng.
11. **So sánh số gần đúng bằng `==` trong mọi kiểm thử:** chỉ hợp lý nếu yêu cầu phải bằng chính xác theo giá trị double.
12. **Dùng sai số tương đối khi tổng đúng bằng 0:** phép chia theo công thức tương đối không xác định.

## 14. Bài tập và đáp án

**Câu 1.** Vì sao `(1e16 + (-1e16)) + 1` khác `1e16 + ((-1e16) + 1)` khi tính bằng double?

**Đáp án:** Biểu thức đầu triệt tiêu hai số lớn trước, trả `1.0`. Biểu thức sau làm mất đóng góp `1` ở phép cộng với số lớn âm, trả `0.0`.

**Câu 2.** Vì sao cộng `0.1` mười lần không nhất thiết bằng `0.1 * 10`?

**Đáp án:** `0.1` là giá trị nhị phân gần đúng; mười phép cộng làm tròn khác với phép nhân.

**Câu 3.** Kahan có luôn khôi phục số nhỏ đã mất trong bộ `[1e16, 1, -1e16]` không?

**Đáp án:** Không. Kahan cơ bản có thể trả `0.0`; ví dụ Neumaier trả `1.0`.

**Câu 4.** Khi nào nên dùng BigDecimal?

**Đáp án:** Khi cần tổng chính xác theo giá trị decimal của đầu vào (như tiền), xây dựng các đối tượng từ dữ liệu decimal chính xác và không làm tròn mỗi phép cộng nếu mục tiêu là tổng chính xác.

**Câu 5.** Có nên dùng sai số tương đối khi tổng tham chiếu là `0`?

**Đáp án:** Không, vì chia cho 0 không xác định; dùng sai số tuyệt đối hoặc tiêu chuẩn khác từ nghiệp vụ.

## 15. Ghi nhớ

- **Thứ tự cộng có thể thay đổi kết quả float/double** vì các phép tính trung gian phải làm tròn.
- Số hạng nhỏ dễ bị mất khi cộng vào tổng lớn; các số lớn đối nhau có thể gây cancellation và làm lộ sai số.
- Kahan, Neumaier, pairwise cải thiện nhiều trường hợp, nhưng không biến double thành số học thực vô hạn chính xác.
- Stream song song có thể dùng cây cộng khác; xác định thuật toán và trình tự gộp khi cần kết quả tái lập.
- Khi cần tổng decimal chính xác, dùng BigDecimal từ nguồn decimal và quản lý thời điểm làm tròn.

Tài liệu tham khảo: Java SE 17 API `Double`, `Math.ulp`, `DoubleStream.sum`, `Collectors.summingDouble`, `BigDecimal` và IEEE 754.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-008">Reduce song song và yêu cầu tính kết hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Reduce song song và yêu cầu tính kết hợp trong tổng hợp số Java

## 1. Mục tiêu và vấn đề

Trong Java Stream, `reduce` gộp nhiều phần tử thành một kết quả: tổng, tích, giá trị lớn nhất, hoặc một đối tượng tổng hợp như tổng và số lượng mẫu. Khi xử lý **song song** bằng `parallel()` hay `parallelStream()`, Stream có thể chia đầu vào thành nhiều phần, tổng hợp mỗi phần riêng rồi gộp các kết quả đó lại. Vì **cách nhóm phép tính có thể thay đổi**, phép kết hợp (*reduction operator*) phải tuân thủ những quy tắc nhất định.

Với phép cộng số nguyên thông thường trong miền không tràn, mọi cách nhóm đều cho cùng một tổng:

```text
(2 + 3) + 5 = 10
2 + (3 + 5) = 10
```

Nhưng với phép trừ:

```text
(10 - 3) - 2 = 5
10 - (3 - 2) = 9
```

Đó là lý do **không thể lấy một phép gộp chạy được khi tuần tự rồi mặc định chuyển sang song song**. Kết quả song song chỉ đáng tin khi operation có tính kết hợp, giá trị khởi tạo là phần tử đơn vị phù hợp, và lambda không phụ thuộc trạng thái ngoài hoặc gây can thiệp nguồn.

Bài này tập trung vào **tổng hợp số** với `reduce`, gồm tổng/tích/giá trị thống kê; giải thích hợp đồng API, tương tác với `int`/`long`/`double`/`BigDecimal`, cách viết combiner và giải pháp thay thế bằng `sum()` hoặc `collect()`.

## 2. Reduce hoạt động ra sao?

Dạng đơn giản:

```java
int sum = java.util.stream.IntStream.rangeClosed(1, 4)
        .parallel()
        .reduce(0, Integer::sum);

System.out.println(sum); // 10
```

Đây là đoạn mã minh họa có thể đặt trong thân một phương thức Java. `rangeClosed(1, 4)` tạo `1, 2, 3, 4`; `reduce(0, Integer::sum)` dùng `0` làm giá trị đơn vị và `Integer::sum` làm phép kết hợp.

Một cách chia **minh họa** (Stream không cam kết sẽ chọn đúng cách chia này):

```text
Dữ liệu:           1    2    3    4
Nhóm thứ nhất:    (1 + 2)  = 3
Nhóm thứ hai:     (3 + 4)  = 7
Kết hợp:           3 + 7  = 10
```

Một lịch thực thi khác có thể nhóm `1 + (2 + (3 + 4))`. Vì phép cộng tương ứng có tính kết hợp nên không ảnh hưởng tới kết quả toán học. `parallel()` cho phép framework chia và gộp lại công việc; **không cam kết số luồng, cách chia, thứ tự thực thi hoặc tốc độ luôn nhanh hơn**.

Với `Stream<T>` có ba dạng `reduce` chính:

| Dạng API | Kết quả | Điểm chính |
| --- | --- | --- |
| `reduce(BinaryOperator<T> accumulator)` | `Optional<T>` | Không có identity; stream rỗng cho `Optional.empty()`. |
| `reduce(T identity, BinaryOperator<T> accumulator)` | `T` | Có identity; stream rỗng cho identity. |
| `reduce(U identity, BiFunction<U,? super T,U> accumulator, BinaryOperator<U> combiner)` | `U` | Kiểu kết quả có thể khác kiểu phần tử; combiner gộp kết quả giữa các nhánh song song. |

Với primitive stream, các biến thể `IntStream.reduce`, `LongStream.reduce` và `DoubleStream.reduce` tránh boxing không cần thiết. Khi chỉ cộng primitive, `sum()` thường diễn đạt ý định rõ ràng hơn `reduce(0, Integer::sum)`.

## 3. Tính kết hợp (*associativity*) là điều kiện cốt lõi

Một phép toán nhị phân `op` có **tính kết hợp** nếu đối với mọi `a`, `b`, `c` trong miền dữ liệu hợp lệ:

```text
op(op(a, b), c) == op(a, op(b, c))
```

Tính kết hợp cho phép thay đổi vị trí dấu ngoặc mà không đổi kết quả. Đây chính là điều reduction song song cần để gộp các đoạn được xử lý ở những nhóm khác nhau.

### 3.1. Ví dụ có tính kết hợp

- Tổng các số nguyên trong miền mà phép tính được định nghĩa đúng theo hợp đồng cần có: `(a + b) + c = a + (b + c)`.
- Tích các số nguyên tương tự: `(a × b) × c = a × (b × c)`, lưu ý giới hạn kiểu số và quy tắc tràn.
- `min(a, b)` hoặc `max(a, b)` với phép so sánh nhất quán.
- Phép cộng `BigInteger` và phép cộng `BigDecimal` **chính xác** theo giá trị số học.
- Gộp cặp thông tin `(sum, count)` bằng cộng tương ứng từng thành phần, khi kiểu lưu trữ có đủ miền giá trị.

### 3.2. Ví dụ không có tính kết hợp

- `a - b`: `(10 - 3) - 2 = 5`, còn `10 - (3 - 2) = 9`.
- `a / b`: thay đổi dấu ngoặc thường đổi kết quả, kể cả số hữu tỉ.
- Trung bình đôi `(a + b) / 2`: ba phần tử bị cho trọng số khác nhau tùy nhóm; không tương đương tổng chia cho số lượng.
- Cộng số dấu phẩy động `double` theo nghĩa phép tính máy có làm tròn: về toán học phép cộng là kết hợp, nhưng biểu diễn hữu hạn khiến `(a + b) + c` có thể khác `a + (b + c)`.
- Các phép cộng decimal có làm tròn ở **mỗi bước trung gian** bằng `MathContext` hoặc `setScale`: mất chữ số ở các nhóm khác nhau có thể tạo kết quả khác.

**Không nhầm kết hợp với giao hoán.** Tính giao hoán là `op(a,b) = op(b,a)`, nghĩa là đổi thứ tự toán hạng. Một phép như nối chuỗi có tính kết hợp nhưng không giao hoán. Với stream có encounter order xác định, reduction kết hợp có thể giữ được ý nghĩa thứ tự đó; còn với nguồn/operation không có yêu cầu thứ tự, đừng tự phụ thuộc vào thứ tự gộp để tạo kết quả mà phép toán không giao hoán.

## 4. Identity: phần tử đơn vị phải đúng

Trong `reduce(identity, accumulator)`, `identity` phải là **phần tử đơn vị** của operation:

```text
op(identity, x) == x
op(x, identity) == x
```

Một số ví dụ:

| Phép gộp | Identity phù hợp | Vì sao |
| --- | --- | --- |
| Cộng `int`/`long` | `0`/`0L` | `x + 0 = 0 + x = x` |
| Nhân số | `1` | `x × 1 = 1 × x = x` |
| `min` trên miền `int` đầy đủ | `Integer.MAX_VALUE` | `min(MAX_VALUE, x) = x` |
| `max` trên miền `int` đầy đủ | `Integer.MIN_VALUE` | `max(MIN_VALUE, x) = x` |
| Cộng `BigDecimal` | `BigDecimal.ZERO` | Bằng nhau theo **trị số**; cân nhắc scale khi hợp đồng kết quả yêu cầu biểu diễn cụ thể. |
| Gộp cặp `(sum,count)` | `(0,0)` | Cộng từng thành phần không đổi kết quả. |

Identity sai dễ tạo lỗi khi song song vì nó có thể được dùng làm kết quả khởi tạo cho nhiều phần chia:

```java
// Sai: 10 không phải phần tử đơn vị của phép cộng.
int suspicious = java.util.stream.IntStream.of(1, 2, 3, 4)
        .parallel()
        .reduce(10, Integer::sum);
```

Không nên dựa vào một kết quả cụ thể của đoạn **cố ý sai** này: nó vi phạm contract identity, nên cách chia stream có thể làm phần `10` xuất hiện nhiều lần. Nếu thật sự muốn cộng thêm một lượng cố định `10` một lần, hãy tính tổng với `0` rồi cộng `10` ở **ngoài** phép reduce:

```java
int correct = 10 + java.util.stream.IntStream.of(1, 2, 3, 4)
        .parallel()
        .reduce(0, Integer::sum);
System.out.println(correct); // 20
```

Identity đúng còn quan trọng với **stream rỗng**: `IntStream.empty().reduce(0, Integer::sum)` trả `0`. Ngược lại, `IntStream.empty().reduce(Integer::sum)` trả `OptionalInt.empty()`; tùy nghiệp vụ, một tổng rỗng có thể là 0 còn min/max rỗng lại nên biểu diễn là “không có giá trị”.

## 5. Accumulator và combiner ở reduce ba tham số

Dạng tổng quát:

```java
<U> U reduce(
        U identity,
        BiFunction<U, ? super T, U> accumulator,
        BinaryOperator<U> combiner)
```

Ở đây `T` là kiểu phần tử input, `U` là kiểu kết quả. `accumulator` tích lũy một phần tử `T` vào một kết quả `U`; `combiner` gộp **hai kết quả `U`** được tính từ các phần chia.

Ví dụ chuyển chuỗi số thành tổng kiểu `long`:

```java
long total = java.util.stream.Stream.of("10", "20", "30")
        .parallel()
        .reduce(
                0L,
                (sum, text) -> sum + Long.parseLong(text),
                Long::sum);

System.out.println(total); // 60
```

Để đúng với việc gộp song song, hợp đồng Java Stream yêu cầu:

1. `combiner` có **tính kết hợp**.
2. `identity` là **phần tử đơn vị** cho `combiner`.
3. **Tính tương thích giữa accumulator và combiner**: với mọi `u`, `t` hợp lệ,

```text
combiner(u, accumulator(identity, t)) == accumulator(u, t)
```

Ví dụ trên: `accumulator(0, "20") = 20`. Khi có tổng phần trước `u = 10`, `combiner(10, 20) = 30`, giống `accumulator(10, "20") = 30`. Do đó kết quả không phụ thuộc cách chia.

**Ví dụ sai** là dùng accumulator cộng phần tử nhưng combiner trừ hai tổng. Dù một lần chạy tuần tự có thể cho đúng vì không phải gộp nhánh, phép gộp song song sẽ thay đổi ý nghĩa; không dùng:

```java
// Sai: accumulator cộng còn combiner trừ, vi phạm compatibility.
long incorrect = java.util.stream.Stream.of("10", "20", "30")
        .parallel()
        .reduce(0L,
                (sum, text) -> sum + Long.parseLong(text),
                (left, right) -> left - right);
```

Không cần chạy để “thử may mắn” một kết quả: đây là lỗi thiết kế cần sửa bằng `Long::sum`.

### Accumulator phải stateless và non-interfering

Lambda trong reduce không được phụ thuộc trạng thái thay đổi từ bên ngoài, hoặc sửa nguồn Stream trong lúc pipeline chạy. Đặc biệt, không nên ghi vào biến cộng dồn bên ngoài bằng `forEach`:

```java
int[] total = {0};
java.util.stream.IntStream.rangeClosed(1, 1000)
        .parallel()
        .forEach(n -> total[0] += n); // Sai: data race.
```

Kết quả có thể bị mất cập nhật vì nhiều luồng cùng đọc/sửa một phần tử mảng. Cách đúng cho phép cộng:

```java
int total = java.util.stream.IntStream.rangeClosed(1, 1000)
        .parallel()
        .sum();
System.out.println(total); // 500500
```

Một `AtomicInteger` có thể giúp tránh data race ở phép cộng đơn giản, nhưng dùng mutation bên ngoài vẫn làm ý định tổng hợp kém rõ ràng và có thể tạo contention; `reduce` hoặc `sum()` thể hiện cách tiếp cận phù hợp hơn.

## 6. Vì sao cộng double không thật sự có tính kết hợp?

`double` theo IEEE 754 biểu diễn hữu hạn chữ số nhị phân. Nhiều phép cộng cần làm tròn ngay khi tạo ra kết quả trung gian. Xét:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

double left = (a + b) + c;
double right = a + (b + c);

System.out.println(left);  // 1.0
System.out.println(right); // 0.0
```

Giải thích:

- `a + b = 0` nên `(a + b) + c = 1`.
- `b + c` được làm tròn về `-1e16` vì `1.0` quá nhỏ so với độ lớn của `-1e16` ở precision của `double`; sau đó cộng `a` cho `0`.

Vì vậy, khi `DoubleStream.parallel().sum()` hoặc một `reduce(0.0, Double::sum)` được thực hiện, **không nên kỳ vọng từng bit của kết quả luôn trùng với một vòng lặp cộng tuần tự theo thứ tự trái sang phải**. Cả thứ tự nhóm và thuật toán tổng có thể ảnh hưởng độ chính xác. Đặc biệt `DoubleStream.sum()` có thể dùng kỹ thuật cộng bù (*compensated summation*) để cải thiện độ chính xác, nhưng kết quả vẫn không được coi là phép cộng số thực chính xác tuyệt đối.

Tình huống đặc biệt của `NaN`, dương/âm vô cực và `-0.0` cũng cần được kiểm thử nếu bộ dữ liệu chứa chúng. Chẳng hạn `Double.sum()`/`DoubleStream.sum()` có những quy tắc riêng về `NaN` và vô cực; không tự so sánh các kết quả dấu phẩy động như thể chúng là số decimal chính xác.

Các chiến lược thực tế:

- Nếu cần **tổng decimal chính xác** theo nghiệp vụ, cân nhắc `BigDecimal` với cách khởi tạo và scale phù hợp.
- Nếu `double` đủ cho mô phỏng/thống kê, chấp nhận sai khác trong một dung sai được định nghĩa, đồng thời kiểm tra trường hợp giá trị lớn/nhỏ chênh lệch mạnh.
- Nếu cần tái hiện chính xác bit khi chạy ở những lịch thực thi khác nhau, kiểm soát thuật toán, thứ tự nhóm và môi trường; chỉ gọi `parallel()` không đảm bảo yêu cầu đó.
- Không dùng `==` làm kiểm tra tổng quát cho các tổng tính qua hai đường khác nhau khi phần sai số làm tròn được cho phép; thay vào đó so sánh theo ngưỡng có nghĩa và xử lý riêng `NaN`/vô cực.

## 7. BigDecimal: phép cộng chính xác và phép cộng làm tròn trung gian

`BigDecimal.add(other)` **không truyền `MathContext`** thực hiện phép cộng decimal chính xác, với scale kết quả phản ánh scale của các toán hạng. Về trị số, phép cộng chính xác có tính kết hợp nên là lựa chọn hợp lý khi cần cộng các khoản decimal trước lúc làm tròn theo nghiệp vụ:

```java
import java.math.BigDecimal;
import java.util.stream.Stream;

BigDecimal total = Stream.of("0.10", "0.20", "0.30")
        .map(BigDecimal::new)
        .parallel()
        .reduce(BigDecimal.ZERO, BigDecimal::add);

System.out.println(total.toPlainString()); // 0.60
```

Mỗi giá trị đầu vào có scale 2. Khi cộng chính xác, kết quả có scale phù hợp; việc dùng `BigDecimal.ZERO` làm identity hợp lý về trị số. Nếu dữ liệu có scale khác nhau, cần phân biệt **bằng nhau về trị số** (dùng `compareTo()`) và **bằng nhau theo `equals()`** (xét cả scale). Khi hợp đồng đầu ra yêu cầu scale cố định, chuẩn hóa ở ranh giới được định nghĩa bằng `setScale(requiredScale, roundingMode)`.

### Rounding ở từng lần cộng làm mất tính kết hợp

Nếu một lambda làm tròn sau **mỗi phép cộng**, tình huống thay đổi cách nhóm có thể tạo sai khác. Ví dụ với `HALF_UP` về scale 2:

```text
(a + b) + c, mỗi bước làm tròn:
a = 1.24, b = 0.005, c = 0.005
round2(1.24 + 0.005) = 1.25
round2(1.25 + 0.005) = 1.26

a + (b + c), mỗi bước làm tròn:
round2(0.005 + 0.005) = 0.01
round2(1.24 + 0.01) = 1.25
```

Biểu thức kiểm chứng bằng Java:

```java
BigDecimal a = new BigDecimal("1.24");
BigDecimal b = new BigDecimal("0.005");
BigDecimal c = new BigDecimal("0.005");
java.util.function.BinaryOperator<BigDecimal> roundedAdd =
        (x, y) -> x.add(y).setScale(2, java.math.RoundingMode.HALF_UP);

System.out.println(roundedAdd.apply(roundedAdd.apply(a, b), c)); // 1.26
System.out.println(roundedAdd.apply(a, roundedAdd.apply(b, c))); // 1.25
```

**Không truyền `roundedAdd` này vào `parallel().reduce(...)` nếu bạn kỳ vọng một kết quả độc lập cách phân nhóm.** Tương tự, `a.add(b, mathContext)` có thể làm tròn ở mỗi bước và không tự đảm bảo tính kết hợp theo nghĩa kết quả máy. Trong nghiệp vụ cho phép, hãy cộng ở độ chính xác thích hợp rồi làm tròn **một lần ở ranh giới cuối**:

```java
BigDecimal result = Stream.of(a, b, c)
        .parallel()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, java.math.RoundingMode.HALF_UP);

System.out.println(result); // 1.25
```

Nếu quy tắc nghiệp vụ buộc phải làm tròn từng hóa đơn hoặc từng hạng mục trước khi cộng, đó là **phép tính khác** và phải được mô hình hóa rõ; không được tự ý đổi chính sách để tối ưu song song.

## 8. Tràn số nguyên và ý nghĩa của tổng

Trong Java, phép cộng `int`/`long` có thể tràn giới hạn kiểu số mà không ném lỗi. Phép cộng wraparound trên các kiểu nguyên có hành vi xác định, và thực tế phép cộng modulo theo độ rộng bit vẫn có tính kết hợp, nhưng **kết quả không còn bằng tổng số nguyên toán học bạn có thể đang mong đợi**.

Ví dụ:

```java
int result = java.util.stream.IntStream.of(
        Integer.MAX_VALUE, 1)
        .parallel()
        .reduce(0, Integer::sum);

System.out.println(result); // -2147483648
```

Đây là hành vi tràn `int`, không phải do Stream song song tự tạo lỗi. Nếu tổng có thể vượt miền `int`, cân nhắc `long` hoặc `BigInteger`:

```java
long exactWithinLong = java.util.stream.IntStream.of(
        Integer.MAX_VALUE, 1)
        .asLongStream()
        .parallel()
        .sum();

System.out.println(exactWithinLong); // 2147483648
```

`long` cũng có giới hạn. `Math.addExact` giúp **phát hiện** overflow bằng `ArithmeticException`, nhưng nó không phải phép toán luôn xác định cho mọi bộ ba số `long`: với những dãy chứa giá trị bù trừ, một cách nhóm có thể tràn trung gian trong khi cách khác không. Nếu yêu cầu tính chính xác cho tổng lớn không giới hạn, `BigInteger::add` an toàn hơn về miền giá trị.

## 9. Thiết kế tổng hợp số có nhiều đại lượng

Muốn tính cả tổng và số lượng để lấy trung bình, **trung bình đôi không có tính kết hợp**:

```text
avg2(avg2(2, 4), 6) = 4.5
avg2(2, avg2(4, 6)) = 3.5
```

Trong khi trung bình cộng của ba số là `(2 + 4 + 6) / 3 = 4.0`. Thay vì reduce bằng công thức trung bình hai số, gộp một trạng thái gồm **tổng + số đếm** rồi chia một lần cuối:

```java
record SumCount(long sum, long count) {}

SumCount left = new SumCount(10, 1);
SumCount right = new SumCount(20, 1);
SumCount joined = new SumCount(
        left.sum() + right.sum(),
        left.count() + right.count());

System.out.println(joined); // SumCount[sum=30, count=2]
```

Đây là ví dụ **đặt record vào một lớp hoặc source file Java hợp lệ** (không đặt trực tiếp trong thân phương thức nếu code phụ thuộc phạm vi ngoài mà không rõ). Chương trình hoàn chỉnh ở phần 11 cho thấy việc tạo record bất biến và reduce song song.

Về toán học, phép gộp hai `SumCount`:

```text
combine((s1, n1), (s2, n2)) = (s1 + s2, n1 + n2)
identity = (0, 0)
mean = sum / count   (chỉ sau khi hoàn thành reduce)
```

có tính kết hợp cho các phép cộng chính xác. Nếu dùng kiểu `long`, tổng và count vẫn cần nằm trong miền hợp lệ hoặc chuyển sang cấu trúc có miền giá trị lớn hơn.

Trường hợp cơ bản có thể dùng API thư viện:

```java
java.util.IntSummaryStatistics stats =
        java.util.stream.IntStream.of(10, 20, 30, 40)
                .parallel()
                .summaryStatistics();

System.out.println(stats.getSum());   // 100
System.out.println(stats.getCount()); // 4
System.out.println(stats.getAverage()); // 25.0
```

`summaryStatistics()` thể hiện rõ mục tiêu thống kê (count, sum, min, max, average) mà không cần tự viết combiner. Tuy nhiên, nó vẫn dùng các kiểu số primitive và có giới hạn tràn/độ chính xác phù hợp với kiểu đó.

## 10. Giữa reduce, collect, sum và summaryStatistics nên chọn gì?

| Mục tiêu | API phù hợp | Lý do |
| --- | --- | --- |
| Tổng `int`/`long`/`double` | `IntStream.sum()`, `LongStream.sum()`, `DoubleStream.sum()` | Dễ đọc, tránh boxing không cần thiết; vẫn tôn trọng giới hạn số học. |
| Tổng `BigDecimal` chính xác | `stream.reduce(BigDecimal.ZERO, BigDecimal::add)` | Phép gộp tạo giá trị BigDecimal bất biến. |
| Max/min của stream có thể rỗng | `max()`/`min()` hoặc `reduce(BinaryOperator)` | Kết quả `Optional*` phản ánh tình huống không có phần tử. |
| Sum + count + min + max primitive | `summaryStatistics()` | API chuyên dụng, không phải tự ghép nhiều reduce. |
| Gộp nhiều đại lượng khác nhau vào một immutable result | `reduce` với record và combiner có tính kết hợp | Kiểm soát rõ mô hình trạng thái và identity. |
| Gom phần tử vào danh sách/map/set hoặc bộ tích lũy mutable | `collect()` / `Collectors` | Mutable reduction có supplier, accumulator và combiner được thiết kế để hợp nhất container. |

**Đừng mutate một identity chung trong `reduce`.** Ví dụ:

```java
// Sai cho reduce: List mutable được dùng làm identity và bị sửa bên trong.
java.util.List<Integer> numbers = java.util.stream.Stream.of(1, 2, 3)
        .parallel()
        .reduce(
                new java.util.ArrayList<>(),
                (list, x) -> { list.add(x); return list; },
                (left, right) -> { left.addAll(right); return left; });
```

Mẫu này có thể vi phạm hợp đồng reduction và gây hành vi không xác định do dữ liệu dùng chung giữa các nhánh. Dùng `collect(java.util.stream.Collectors.toList())` để tạo collection, hoặc một collector thích hợp với yêu cầu sắp xếp/đồng thời. `reduce` thích hợp nhất với kiểu accumulator **bất biến** hoặc phép gộp không gây side effect.

## 11. Chương trình Java 17+ hoàn chỉnh: kiểm tra kết hợp và song song

Lưu thành **`ParallelNumberReductionDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ParallelNumberReductionDemo {
    record SumCount(long sum, long count) {}

    public static void main(String[] args) {
        int parallelSum = IntStream.rangeClosed(1, 1000)
                .parallel()
                .reduce(0, Integer::sum);
        int sequentialSum = IntStream.rangeClosed(1, 1000)
                .reduce(0, Integer::sum);
        System.out.println("parallel integer sum = " + parallelSum);
        System.out.println("same as sequential = "
                + (parallelSum == sequentialSum));

        BigDecimal decimalSum = Stream.of("0.10", "0.20", "0.30")
                .map(BigDecimal::new)
                .parallel()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("exact decimal sum = "
                + decimalSum.toPlainString());

        double a = 1e16;
        double b = -1e16;
        double c = 1.0;
        System.out.println("floating left = " + ((a + b) + c));
        System.out.println("floating right = " + (a + (b + c)));

        BigDecimal x = new BigDecimal("1.24");
        BigDecimal y = new BigDecimal("0.005");
        BigDecimal z = new BigDecimal("0.005");
        java.util.function.BinaryOperator<BigDecimal> roundedAdd =
                (left, right) -> left.add(right)
                        .setScale(2, RoundingMode.HALF_UP);
        System.out.println("rounded left = "
                + roundedAdd.apply(roundedAdd.apply(x, y), z));
        System.out.println("rounded right = "
                + roundedAdd.apply(x, roundedAdd.apply(y, z)));

        SumCount summary = List.of(10L, 20L, 30L, 40L)
                .parallelStream()
                .map(n -> new SumCount(n, 1))
                .reduce(new SumCount(0, 0),
                        (left, right) -> new SumCount(
                                left.sum() + right.sum(),
                                left.count() + right.count()));
        System.out.println("sum = " + summary.sum());
        System.out.println("count = " + summary.count());
        System.out.println("average = "
                + ((double) summary.sum() / summary.count()));

        long parsedTotal = Stream.of("10", "20", "30")
                .parallel()
                .reduce(0L,
                        (sum, text) -> sum + Long.parseLong(text),
                        Long::sum);
        System.out.println("parsed total = " + parsedTotal);
    }
}
```

Biên dịch và chạy trong thư mục chứa file, bằng JDK 17 hoặc mới hơn:

```bash
javac ParallelNumberReductionDemo.java
java ParallelNumberReductionDemo
```

Kết quả chuẩn:

```text
parallel integer sum = 500500
same as sequential = true
exact decimal sum = 0.60
floating left = 1.0
floating right = 0.0
rounded left = 1.26
rounded right = 1.25
sum = 100
count = 4
average = 25.0
parsed total = 60
```

Các giá trị trong output là **kết quả có thể kiểm tra**: phép cộng `int` ở miền không tràn, `BigDecimal.add` chính xác và record tổng/số đếm có tính kết hợp; còn hai cặp “left/right” được nhóm tường minh để minh họa sai khác do làm tròn. Chương trình không dựa vào một lịch chia nhánh cụ thể để tạo ra kết quả stdout.

Lưu ý: **không chứng minh operator sai bằng việc chạy `parallel().reduce` đúng một lần và quan sát kết quả bất thường**. Có những lần chạy tình cờ nhóm giống tuần tự, vẫn cho kết quả “đẹp”. Chứng minh bằng điều kiện toán học và phản ví dụ với dấu ngoặc như trên đáng tin hơn.

## 12. Hiệu năng, side effects và đặc điểm của nguồn dữ liệu

`parallel()` không tự động tăng hiệu năng. Việc chia tác vụ, chuyển dữ liệu giữa các luồng và gộp kết quả có chi phí. Với tập nhỏ hoặc operation rất rẻ như cộng vài chục số, tính song song có thể **chậm hơn** tuần tự. Benchmark cần cùng dữ liệu, kiểm soát khởi động JIT, số lần đo và môi trường; tránh suy luận từ một lượt chạy.

Sự phân chia của `Spliterator` nguồn, kích thước dữ liệu, số nhân CPU, độ phức tạp của phép xử lý mỗi phần tử và chi phí của combiner đều ảnh hưởng kết quả. Stream song song thường sử dụng cơ chế fork/join, nhưng **không cam kết một tập worker cố định** hoặc trật tự thực thi dự đoán được cho mọi pipeline.

Đặt câu hỏi trước khi chọn song song:

1. Phép gộp có **kết hợp** trên miền số liệu và chính sách làm tròn đang dùng không?
2. Identity có là phần tử đơn vị đối với mọi phần gộp không?
3. Với dạng ba tham số, combiner có tương thích accumulator không?
4. Các lambda có stateless, non-interfering, không chia sẻ trạng thái mutable không?
5. Nếu có stream không có thứ tự cố định, kết quả có phụ thuộc thứ tự các phần tử không?
6. Kiểu số có thể tràn hay mất precision đáng kể không?
7. Có đủ khối lượng công việc để bù chi phí song song không?

Một lưu ý nhỏ về luồng dữ liệu: `forEach` trên stream song song có thể gọi action ở những luồng/thứ tự không xác định. `forEachOrdered` bảo đảm encounter order đối với nguồn có thứ tự nhưng cũng có thể tăng chi phí. Điều này **khác** với cách `reduce` phân nhóm phép gộp: ngay cả khi encounter order được bảo toàn, một phép tính không kết hợp vẫn có thể cho kết quả khác do dấu ngoặc thay đổi.

## 13. Lỗi điển hình và cách khắc phục

**Lỗi 1 — Dùng phép trừ/chia làm reducer.** Những phép này không kết hợp; phải viết lại mục tiêu bằng trạng thái có phép gộp đúng, hoặc thực hiện theo trật tự tuần tự nếu ý nghĩa bắt buộc phụ thuộc thứ tự.

**Lỗi 2 — Dùng `identity` khác phần tử đơn vị.** Với tổng, chọn `0` thay vì một số cộng sẵn; cộng phần bù một lần bên ngoài `reduce`.

**Lỗi 3 — Accumulator cộng nhưng combiner trừ hoặc nhân.** Kiểm tra phương trình tương thích; một hàm combiner có tên “merge” không đủ, nó phải gộp đúng ngữ nghĩa của accumulator.

**Lỗi 4 — Mutate object identity hoặc biến ngoài.** Dùng immutable value trong `reduce`; dùng `collect` cho container mutable; tránh data race do `forEach`.

**Lỗi 5 — Cho rằng tổng `double` luôn giống từng bit khi tuần tự/song song.** Không đúng với floating point; cần kiểm tra dung sai hoặc một thuật toán có định nghĩa thứ tự gộp rõ ràng.

**Lỗi 6 — Làm tròn từng bước với `BigDecimal` rồi kỳ vọng tính kết hợp.** `add(..., MathContext)` hay `setScale` sau mỗi lần cộng có thể cho kết quả phụ thuộc cách nhóm; khi nghiệp vụ cho phép, cộng chính xác trước rồi round một lần.

**Lỗi 7 — Tính trung bình qua phép `(a+b)/2` lặp lại.** Cách nhóm làm thay đổi trọng số. Hãy tổng hợp `sum/count` rồi chia cuối.

**Lỗi 8 — Bỏ qua overflow.** `int`/`long` có thể wraparound; dùng kiểu rộng hơn hoặc `BigInteger` nếu tổng toán học phải chính xác.

**Lỗi 9 — Giả định kết quả nhanh hơn chỉ nhờ `parallel()`.** Đo lường mới quyết định; nhiều pipeline đơn giản hợp lý hơn khi chạy tuần tự.

**Lỗi 10 — Dùng equality nhầm mức ý nghĩa.** Với decimal có scale khác nhau, `BigDecimal.equals()` xét scale còn `compareTo()` xét trị số; với `double`, dùng kiểm tra số học có nhận thức về làm tròn, `NaN` và vô cực.

## 14. Tự kiểm tra

**Câu 1.** Khi giảm `Stream.of(10, 3, 2)` bằng phép trừ, vì sao `parallel()` không có kết quả toán học ổn định độc lập cách nhóm?

**Đáp án:** `(10 - 3) - 2 = 5` nhưng `10 - (3 - 2) = 9`; phép trừ không kết hợp.

**Câu 2.** Identity phù hợp của phép cộng và phép nhân là gì?

**Đáp án:** `0` và `1`. Identity phải trung hòa phần tử khi đứng ở cả hai phía của phép gộp.

**Câu 3.** Dạng `reduce(0L, accumulator, combiner)` đòi phương trình tương thích nào?

**Đáp án:** `combiner(u, accumulator(identity, t)) == accumulator(u, t)`, ngoài tính kết hợp của combiner và tính đơn vị của identity.

**Câu 4.** Vì sao `(1e16 + -1e16) + 1.0` có thể khác `1e16 + (-1e16 + 1.0)` với `double`?

**Đáp án:** Mỗi phép cộng bị giới hạn bởi độ chính xác dấu phẩy động. Phép cộng `-1e16 + 1.0` làm mất số 1.0 do làm tròn, dẫn đến `0.0` ở nhóm bên phải.

**Câu 5.** Có nên reduce song song bằng `(x,y) -> x.add(y).setScale(2, HALF_UP)`?

**Đáp án:** Không nếu cần kết quả độc lập cách chia. Phép cộng có làm tròn sau từng bước không kết hợp; ví dụ `1.24, 0.005, 0.005` cho `1.26` và `1.25` theo cách nhóm.

**Câu 6.** Muốn tính trung bình hàng triệu phần tử, vì sao không dùng reducer `(a+b)/2`?

**Đáp án:** Nó không giữ cùng trọng số của từng quan sát khi gộp nhiều lần. Gộp một state có tổng và số đếm, hoặc dùng `summaryStatistics()` theo yêu cầu độ chính xác.

**Câu 7.** Tổng `Integer.MAX_VALUE + 1` bằng `-2147483648` có phải lỗi do parallel reduce không?

**Đáp án:** Không. Đó là tràn kiểu `int`. Chọn `long`/`BigInteger` khi tổng toán học vượt miền `int`.

**Câu 8.** Với stream rỗng, `reduce(0, Integer::sum)` và `reduce(Integer::sum)` khác nhau thế nào?

**Đáp án:** Dạng có identity trả `0`; dạng không identity trả `Optional.empty()` hoặc `OptionalInt.empty()` đối với primitive stream.

**Câu 9.** Có thể dùng `Collectors.toList()` thay vì mutate `ArrayList` bên trong `reduce` không?

**Đáp án:** Có, đó là mục đích của `collect` dành cho mutable reduction. Accumulator của `reduce` nên tuân thủ contract và tránh chia sẻ mutation.

**Câu 10.** Điều kiện toán học đủ để nhận một phép tổng hợp làm reduction song song đúng về mặt ngữ nghĩa là gì?

**Đáp án:** Phép gộp có tính kết hợp trên miền hợp lệ, identity trung hòa, combiner tương thích accumulator ở dạng ba tham số; đồng thời các lambda phải stateless và non-interfering. Với số thực máy và phép làm tròn, còn phải quyết định nghĩa của “đúng” theo hợp đồng độ chính xác.

## 15. Tài liệu chính thức

- [Java SE 17 — Stream.reduce](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): overload, identity, yêu cầu associativity, stateless/non-interfering và phương trình compatibility.
- [Java SE 17 — IntStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html): reduce, sum, summaryStatistics và primitive stream.
- [Java SE 17 — DoubleStream.sum](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/DoubleStream.html#sum()): hành vi tổng dấu phẩy động và chú ý về độ chính xác.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): cộng chính xác, MathContext, scale và rounding.

**Ghi nhớ:** Reduce song song được phép thay đổi cách **phân nhóm** các phần tử. Phép gộp đúng cần **tính kết hợp**, **identity** chuẩn, **combiner** tương thích và lambda không can thiệp trạng thái. Cộng `double` hoặc `BigDecimal` có làm tròn ở bước trung gian có thể đổi kết quả theo nhóm; hãy đặt chính sách độ chính xác và ranh giới làm tròn rõ ràng trước khi lựa chọn parallel reduction.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-009">Tổng BigDecimal và chính sách MathContext</a>

<details>
<summary>Xem chi tiết</summary>

# Tổng `BigDecimal` và chính sách `MathContext` trong Java

## 1. Tổng nhiều số thập phân cần chính sách rõ ràng

Khi cộng nhiều khoản tiền, phép đo hoặc giá trị nghiệp vụ, vấn đề không chỉ là lấy tổng mà còn là **tổng phải chính xác đến đâu**, **khi nào được làm tròn** và **quy tắc làm tròn nào được phép**.

`BigDecimal` hỗ trợ hai cách cộng quan trọng:

~~~java
BigDecimal exact = left.add(right);
BigDecimal limited = left.add(right, context);
~~~

- `add(BigDecimal)` tính tổng **chính xác về mặt giá trị thập phân**. Phép cộng không mặc định giới hạn số chữ số có nghĩa.
- `add(BigDecimal, MathContext)` áp dụng **precision** và **rounding mode** cho kết quả phép cộng. Nếu precision hữu hạn, một phần chữ số có thể bị làm tròn ngay ở bước đó.
- “Cộng chính xác tất cả phần tử rồi làm tròn một lần” **có thể khác** “làm tròn ở mỗi lần cộng”. Đó là hai chính sách tính toán riêng.

Nếu hợp đồng chỉ yêu cầu “lấy tổng rồi biểu diễn thành hai chữ số thập phân”, thường nên **cộng chính xác trước, áp scale cuối cùng**. Nếu đặc tả yêu cầu kết quả trung gian luôn giới hạn precision, hãy dùng `MathContext` ở từng phép tính và chấp nhận tác động của làm tròn tích lũy.

## 2. Kiến thức nền: giá trị, scale và precision

Giá trị của một `BigDecimal` được xác định bởi:

~~~text
value = unscaledValue × 10^(-scale)
~~~

| Chuỗi | Giá trị | Scale | Precision |
| --- | --- | ---: | ---: |
| `"1.20"` | 1.20 | 2 | 3 |
| `"2.345"` | 2.345 | 3 | 4 |
| `"0.005"` | 0.005 | 3 | 1 |
| `"1234.00"` | 1234.00 | 2 | 6 |

**Scale** quy định vị trí dấu thập phân; **precision** đếm số chữ số có nghĩa trong unscaled value. `MathContext(3, HALF_UP)` giới hạn **ba chữ số có nghĩa trên toàn bộ kết quả**, không phải ba chữ số sau dấu chấm.

Phép cộng chính xác có *preferred scale* bằng giá trị lớn hơn giữa scale hai toán hạng. Ví dụ:

~~~java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("2.345");
BigDecimal result = a.add(b);
System.out.println(result);         // 3.545
System.out.println(result.scale()); // 3
~~~

Cộng thêm `new BigDecimal("0.005")` được `3.550`, vẫn có scale 3. Giá trị toán học này bằng `3.55`; tuy nhiên `equals` còn xét scale nên `new BigDecimal("3.550").equals(new BigDecimal("3.55"))` là `false`, còn `compareTo` trả về 0.

Vì `BigDecimal` bất biến, mỗi phép `add` trả về một đối tượng kết quả; toán hạng không thay đổi. Cần gán kết quả cho biến tổng, không chỉ gọi `total.add(value)` rồi bỏ kết quả.

## 3. Cộng chính xác trước khi xử lý đầu ra

### 3.1. Dùng vòng lặp

Đoạn sau cần đặt trong một phương thức và import `java.math.BigDecimal`, `java.util.List`:

~~~java
List<BigDecimal> amounts = List.of(
        new BigDecimal("1.20"),
        new BigDecimal("2.345"),
        new BigDecimal("0.005"));

BigDecimal total = BigDecimal.ZERO;
for (BigDecimal amount : amounts) {
    total = total.add(amount);
}
System.out.println(total); // 3.550
~~~

`BigDecimal.ZERO` là phần tử trung hòa cho phép cộng về **giá trị**: bắt đầu với zero và lần lượt cộng các giá trị là cách đơn giản, minh bạch. Với danh sách rỗng, kết quả ở cách này là `BigDecimal.ZERO` (scale 0). Nếu đầu ra yêu cầu scale 2, có thể chuẩn hóa zero đó bằng `setScale(2)`.

### 3.2. Dùng `Stream.reduce`

~~~java
BigDecimal total = amounts.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add);
~~~

Khi không truyền `MathContext`, cách này giữ nguyên chính sách **cộng chính xác**. Với stream rỗng, `reduce(identity, accumulator)` trả `BigDecimal.ZERO`. Nếu “không có bản ghi” phải khác với “tổng bằng 0”, có thể dùng overload:

~~~java
Optional<BigDecimal> optionalTotal = amounts.stream()
        .reduce(BigDecimal::add);
~~~

Overload này trả `Optional.empty()` khi không có phần tử. Đây là **đoạn mã minh họa**, cần import `java.util.Optional`.

Không có phương thức tĩnh `BigDecimal.sum(List)` chuẩn giống `IntStream.sum()`. `reduce` hoặc vòng lặp là lựa chọn phổ biến.

### 3.3. Chất lượng dữ liệu đầu vào

Hãy xây dựng các số thập phân từ chuỗi khi muốn bảo toàn số thập phân nguồn: `new BigDecimal("0.1")` có đúng giá trị 0.1, còn `new BigDecimal(0.1)` nhận giá trị `double` nhị phân đã làm tròn. Nếu dữ liệu đến từ `double`, `BigDecimal.valueOf(value)` thường phù hợp hơn constructor nhận `double`, nhưng không thể tự khôi phục văn bản thập phân gốc chưa biết.

Danh sách chứa `null` không thể cộng trực tiếp; `BigDecimal.add(null)` sẽ gây lỗi. Phải có hợp đồng đầu vào: từ chối, loại bỏ bản ghi thiếu hoặc xem thiếu là 0 **chỉ khi nghiệp vụ cho phép**. Đừng âm thầm loại dữ liệu sai.

## 4. `MathContext`: precision và rounding mode

Một `MathContext` gồm hai thành phần:

- `precision`: giới hạn số chữ số có nghĩa của kết quả. Giá trị 0 nghĩa là không giới hạn.
- `roundingMode`: chính sách làm tròn khi số chữ số của kết quả vượt giới hạn, ví dụ `HALF_UP`, `HALF_EVEN`, `DOWN`, `UNNECESSARY`.

~~~java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

System.out.println(new BigDecimal("1.234").round(mc)); // 1.23
System.out.println(new BigDecimal("12345").round(mc)); // 1.23E+4
~~~

Đoạn mã trên cần import `java.math.MathContext`, `java.math.BigDecimal`, `java.math.RoundingMode`. Khi precision hữu hạn, `BigDecimal.add(other, mc)` có thể làm tròn **mỗi tổng trung gian**.

`MathContext.UNLIMITED` có precision 0. Dùng `add(other, MathContext.UNLIMITED)` cho tổng chính xác về giá trị như overload không có context, miễn thao tác không gặp vấn đề tài nguyên. Rounding mode không tạo khác biệt khi precision không giới hạn và không có chữ số nào cần bỏ.

## 5. Cộng rồi làm tròn một lần và làm tròn từng lần

Giả sử ta có `1.234`, `0.004`, `0.004` với precision 3 và `HALF_UP`.

**Chính sách A — Cộng chính xác rồi làm tròn cuối:**

~~~java
BigDecimal exact = BigDecimal.ZERO;
for (BigDecimal x : values) {
    exact = exact.add(x);
}
BigDecimal roundedOnce = exact.round(mc);
~~~

**Chính sách B — Mỗi phép cộng áp `MathContext`:**

~~~java
BigDecimal roundedEveryStep = BigDecimal.ZERO;
for (BigDecimal x : values) {
    roundedEveryStep = roundedEveryStep.add(x, mc);
}
~~~

| Bước | A: Tổng chính xác | B: Tổng làm tròn từng bước |
| --- | ---: | ---: |
| Cộng `1.234` | 1.234 | 1.23 |
| Cộng `0.004` | 1.238 | 1.23 |
| Cộng `0.004` | 1.242 | 1.23 |
| Áp precision 3 tại cuối | **1.24** | **1.23** |

Đối với A, `1.242` làm tròn một lần thành `1.24`. Với B, `1.234` bị làm tròn thành `1.23` ngay ở bước đầu. Hai giá trị `0.004` không đủ lớn để thay đổi từng tổng đã bị làm tròn, nên kết quả cuối vẫn `1.23`.

Đây là **mất đóng góp do làm tròn trung gian**. Không thể khôi phục các chữ số đã bị loại ở một bước trước chỉ bằng việc cộng thêm những giá trị khác.

Chính sách B không phải lúc nào cũng sai: nếu một thuật toán quy định precision hữu hạn cho **từng phép tính**, đó là hành vi được chủ đích. Nhưng khi đặc tả là “tổng các khoản chính xác rồi mới làm tròn”, phải áp dụng A.

## 6. Rounding mode ảnh hưởng thế nào?

Các chính sách làm tròn có thể khác nhau ngay cả khi precision giống nhau. Ví dụ:

~~~java
BigDecimal x = new BigDecimal("2.345");

MathContext halfEven = new MathContext(3, RoundingMode.HALF_EVEN);
MathContext halfUp = new MathContext(3, RoundingMode.HALF_UP);

System.out.println(x.round(halfEven)); // 2.34
System.out.println(x.round(halfUp));   // 2.35
~~~

Ở trường hợp đúng nửa đơn vị, `HALF_EVEN` ưu tiên chữ số cuối được giữ lại là số chẵn; `HALF_UP` làm tròn theo hướng tăng độ lớn khi đúng nửa. Không có một rounding mode “đúng cho mọi nghiệp vụ”.

`RoundingMode.UNNECESSARY` yêu cầu **kết quả phải chính xác mà không cần loại chữ số khác 0**. Nếu tổng chính xác không thể biểu diễn dưới precision/scale yêu cầu mà vẫn giữ giá trị, thao tác sẽ ném `ArithmeticException`.

~~~java
MathContext strict = new MathContext(3, RoundingMode.UNNECESSARY);

new BigDecimal("3.550").round(strict); // 3.55, hợp lệ: bỏ zero dư

// Ném ArithmeticException:
// new BigDecimal("1.242").round(strict);
~~~

Chú ý: `UNNECESSARY` có thể bỏ những số 0 tận cùng; nó **không** có nghĩa là cấm thay đổi cách biểu diễn.

## 7. Thứ tự cộng và tính kết hợp

Phép cộng số học chính xác có tính kết hợp: `(a + b) + c = a + (b + c)`. Nhưng nếu làm tròn **mỗi phép cộng**, kết quả có thể phụ thuộc thứ tự.

Xét ba giá trị `1000`, `1`, `-1000`, precision 3 và `HALF_UP`:

~~~java
BigDecimal a = new BigDecimal("1000");
BigDecimal b = BigDecimal.ONE;
BigDecimal c = new BigDecimal("-1000");
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

BigDecimal first = a.add(b, mc).add(c, mc);
BigDecimal second = a.add(c, mc).add(b, mc);

System.out.println(first);  // 0
System.out.println(second); // 1
~~~

Trong thứ tự thứ nhất, `1000 + 1 = 1001` bị làm tròn về `1.00E+3`, rồi cộng `-1000` thành 0. Trong thứ tự thứ hai, `1000 + (-1000) = 0`, rồi cộng 1 thành 1. **Tổng chính xác luôn là 1.**

Điều này dẫn tới quy tắc quan trọng đối với `Stream.reduce`:

- Với `BigDecimal::add` **không truyền context**, phép cộng kết hợp về giá trị và có thể dùng trong parallel stream nếu mọi phần tử hợp lệ, không có side effect.
- Với lambda `(a, b) -> a.add(b, mc)` khi precision hữu hạn, phép toán có thể **không còn kết hợp**. Parallel reduction có thể gom nhóm khác nhau và cho kết quả khác nhau. Tránh dùng accumulator kiểu này trong `parallelStream` nếu không có thiết kế thuật toán và kiểm chứng riêng.

Mẫu an toàn hơn cho bài toán “tổng chính xác rồi làm tròn”:

~~~java
BigDecimal exact = values.parallelStream()
        .reduce(BigDecimal.ZERO, BigDecimal::add);
BigDecimal result = exact.round(mc);
~~~

Cần bảo đảm đầu vào không chứa `null` và danh sách không bị sửa trong khi tính tổng. `BigDecimal` bất biến nên bản thân phép cộng trên các giá trị hợp lệ không có side effect.

## 8. Đừng nhầm giới hạn precision với scale đầu ra

Ví dụ với dữ liệu khoản tiền:

~~~java
BigDecimal total = new BigDecimal("1.20")
        .add(new BigDecimal("2.345"))
        .add(new BigDecimal("0.005"));

System.out.println(total); // 3.550

BigDecimal normalized = total.setScale(2, RoundingMode.HALF_EVEN);
System.out.println(normalized); // 3.55
~~~

`setScale(2, ...)` yêu cầu **2 chữ số sau dấu chấm**. `new MathContext(2, ...)` lại giới hạn **2 chữ số có nghĩa**, kể cả phần nguyên. Với số lớn, hai chính sách tạo ra kết quả rất khác nhau.

Nếu nghiệp vụ **không cho phép làm tròn** khi chuẩn hóa scale, dùng:

~~~java
BigDecimal normalized =
        total.setScale(2, RoundingMode.UNNECESSARY);
~~~

`3.550` đổi chính xác thành `3.55` vì chỉ bỏ zero dư. Nhưng `3.551` không thể biểu diễn chính xác với scale 2 nên sẽ ném `ArithmeticException`.

Nếu cần giới hạn cả scale và tổng precision, hãy ghi rõ **thứ tự kiểm tra**:

~~~java
BigDecimal scaled = total.setScale(2, RoundingMode.HALF_EVEN);
BigDecimal checked = scaled.round(
        new MathContext(12, RoundingMode.UNNECESSARY));
~~~

Chính sách ví dụ: cho phép làm tròn tổng một lần về scale 2, sau đó yêu cầu biểu diễn được trong 12 chữ số có nghĩa mà không làm tròn thêm. Kiểm tra precision không thay thế kiểm tra miền giá trị, loại tiền hoặc giới hạn dữ liệu của cơ sở dữ liệu.

## 9. Chương trình Java 17+ hoàn chỉnh

Lưu tệp **BigDecimalSumPolicyDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class BigDecimalSumPolicyDemo {
    private static BigDecimal sumExactly(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    private static BigDecimal sumWithContext(
            List<BigDecimal> values, MathContext context) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value, context);
        }
        return total;
    }

    public static void main(String[] args) {
        List<BigDecimal> values = List.of(
                new BigDecimal("1.234"),
                new BigDecimal("0.004"),
                new BigDecimal("0.004"));
        MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

        BigDecimal exact = sumExactly(values);
        System.out.println("exact total: " + exact);
        System.out.println("round once: " + exact.round(mc));
        System.out.println("round each: "
                + sumWithContext(values, mc));

        BigDecimal a = new BigDecimal("1000");
        BigDecimal b = BigDecimal.ONE;
        BigDecimal c = new BigDecimal("-1000");
        System.out.println("first order: "
                + a.add(b, mc).add(c, mc));
        System.out.println("second order: "
                + a.add(c, mc).add(b, mc));

        BigDecimal money = sumExactly(List.of(
                new BigDecimal("1.20"),
                new BigDecimal("2.345"),
                new BigDecimal("0.005")));
        System.out.println("money exact: " + money);
        System.out.println("money scale2: "
                + money.setScale(2, RoundingMode.HALF_EVEN));

        MathContext strict =
                new MathContext(3, RoundingMode.UNNECESSARY);
        System.out.println("strict exact: " + money.round(strict));
        try {
            System.out.println(exact.round(strict));
        } catch (ArithmeticException ex) {
            System.out.println("strict inexact: ArithmeticException");
        }

        System.out.println("empty sum: "
                + sumExactly(List.of()));
        System.out.println("parallel exact: "
                + values.parallelStream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal tie = new BigDecimal("2.345");
        System.out.println("half even: " + tie.round(
                new MathContext(3, RoundingMode.HALF_EVEN)));
        System.out.println("half up: " + tie.round(
                new MathContext(3, RoundingMode.HALF_UP)));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalSumPolicyDemo.java
java BigDecimalSumPolicyDemo
~~~

Đầu ra chính xác:

~~~text
exact total: 1.242
round once: 1.24
round each: 1.23
first order: 0
second order: 1
money exact: 3.550
money scale2: 3.55
strict exact: 3.55
strict inexact: ArithmeticException
empty sum: 0
parallel exact: 1.242
half even: 2.34
half up: 2.35
~~~

Chương trình chứng minh sự khác biệt giữa hai chính sách tổng, sự phụ thuộc vào thứ tự khi làm tròn ở mỗi lần cộng, chuẩn hóa về scale đầu ra, `UNNECESSARY` khi bắt buộc chính xác, và phép cộng chính xác trong parallel stream.

## 10. Mẫu thiết kế tổng cho ứng dụng

### 10.1. Chính sách tổng cuối, làm tròn cuối

~~~java
public static BigDecimal sumToTwoDecimals(List<BigDecimal> values) {
    BigDecimal exact = BigDecimal.ZERO;
    for (BigDecimal value : values) {
        exact = exact.add(value);
    }
    return exact.setScale(2, RoundingMode.HALF_EVEN);
}
~~~

Đây là **phương thức để đặt trong một class**, cần import `BigDecimal`, `RoundingMode`, `List`. Hàm hợp với quy tắc “cộng tất cả giá trị chính xác rồi trả kết quả scale 2 theo `HALF_EVEN`”. Nếu bị cấm làm tròn, đổi sang `UNNECESSARY` và xử lý lỗi.

Cần quy định trước liệu danh sách rỗng có đại diện tổng bằng `0.00` hay một trạng thái không có dữ liệu. Hàm trên trả `0.00` trong trường hợp rỗng, do `setScale(2)` trên zero.

### 10.2. Khi thực sự cần `MathContext` mỗi lần cộng

~~~java
BigDecimal total = BigDecimal.ZERO;
for (BigDecimal value : values) {
    total = total.add(value, context);
}
~~~

Chỉ nên chọn cách này khi **precision trung gian** được chỉ định trong hợp đồng tính toán, ví dụ thuật toán mô phỏng số học hữu hạn, hoặc cần tái tạo kết quả của một quy trình xử lý đã có quy định làm tròn theo bước. Chú ý thứ tự duyệt phải có ý nghĩa xác định; test với số rất lớn, số rất nhỏ và các phần tử có dấu ngược nhau dễ triệt tiêu.

### 10.3. Tối ưu hiệu năng không được làm đổi hợp đồng

Các đối tượng `BigDecimal` bất biến, nên việc cộng nhiều phần tử có thể cấp phát nhiều đối tượng và tốn tài nguyên với giá trị/scale rất lớn. Tuy nhiên, giới hạn precision ở từng bước để giảm chi phí **có thể thay đổi tổng**. Đây là lựa chọn thuật toán cần kiểm chứng với sai số cho phép; không phải một tối ưu hóa bảo toàn hoàn toàn ngữ nghĩa.

Với bài toán tài chính dùng đơn vị nhỏ nhất cố định, biểu diễn bằng số nguyên cũng có thể phù hợp, nhưng cần một chính sách khác cho scale, overflow và đơn vị tiền. Không chuyển kiểu chỉ vì tốc độ trước khi xác định đúng hợp đồng.

## 11. Lỗi phổ biến và câu hỏi tự kiểm tra

| Hiểu lầm | Cách hiểu đúng |
| --- | --- |
| `MathContext(2, HALF_UP)` cho ra 2 chữ số sau dấu chấm | Nó cho tối đa **2 chữ số có nghĩa**; dùng `setScale(2, ...)` cho phần thập phân |
| Cộng với `MathContext` mỗi bước tương đương cộng chính xác rồi làm tròn | Có thể mất các đóng góp nhỏ và cho tổng khác |
| Đổi thứ tự cộng không ảnh hưởng khi có context | Làm tròn trung gian có thể phá vỡ tính kết hợp |
| `parallelStream` với accumulator `add(mc)` luôn an toàn | Accumulator có thể không kết hợp, làm kết quả tùy cách nhóm |
| `BigDecimal.ZERO` đã có scale 2 | Zero mặc định có scale 0; chuẩn hóa sau theo hợp đồng |
| `UNNECESSARY` bỏ qua xử lý làm tròn | Nó **báo lỗi** nếu phải thay đổi giá trị để đạt precision/scale mong muốn |
| `equals` và `compareTo` giống nhau | `equals` còn so scale; `compareTo` xét giá trị |
| `new BigDecimal(0.1)` luôn chính xác số thập phân 0.1 | Constructor nhận giá trị `double` nhị phân đã làm tròn |

Tự kiểm tra:

1. Tổng chính xác của `1.234 + 0.004 + 0.004`? **`1.242`**.
2. Làm tròn tổng đó một lần với precision 3, `HALF_UP`? **`1.24`**.
3. Nếu làm tròn sau từng phép cộng cùng context? **`1.23`**.
4. Vì sao `1000 + 1 - 1000` có thể ra 0 với precision 3? **Làm tròn mất đơn vị 1 ở bước đầu**, trong khi tổng chính xác là 1.
5. Muốn đầu ra luôn có hai chữ số sau dấu chấm thì dùng gì? **`setScale(2, roundingMode)`** sau khi tổng đã tính theo hợp đồng.
6. Có thể dùng `BigDecimal::add` làm accumulator cho tổng chính xác trong parallel stream không? **Có**, nếu dữ liệu hợp lệ và không có tác dụng phụ.
7. `RoundingMode.UNNECESSARY` phù hợp lúc nào? **Khi ràng buộc precision hoặc scale phải được đáp ứng mà không đổi giá trị**; nếu không thể, thao tác ném `ArithmeticException`.

**Ghi nhớ:** Tách ba quyết định: **cộng chính xác bằng `add`**, **giới hạn chữ số có nghĩa bằng `MathContext`**, và **chuẩn hóa chữ số sau dấu chấm bằng `setScale`**. Thời điểm áp chính sách làm tròn ảnh hưởng trực tiếp kết quả, khả năng song song hóa và tính nhất quán nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-synthesis-010">Không trộn đơn vị, precision hoặc scale không được thống nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Không trộn đơn vị, precision hoặc scale không được thống nhất

## 1. Vấn đề: phép tính đúng cú pháp có thể sai ý nghĩa

Trong Java, kiểu số như `BigDecimal` biết cộng, trừ, nhân, chia nhưng **không tự biết** các số đang biểu thị mét, centimet, USD, giây hay phần trăm. Nó cũng không tự biết hợp đồng nghiệp vụ đòi hỏi bao nhiêu chữ số có nghĩa, bao nhiêu chữ số sau dấu phẩy và làm tròn vào thời điểm nào.

Ba nguồn sai lệch thường gặp:

1. **Đơn vị không đồng nhất:** cộng `1.20 m + 30 cm` như `1.20 + 30 = 31.20`. Giá trị đúng phải quy đổi `30 cm = 0.30 m`, sau đó được `1.50 m`.
2. **Precision không đồng nhất:** một phép tính giữ kết quả đầy đủ, phép tiếp theo dùng `MathContext.DECIMAL32` chỉ giữ tối đa bảy chữ số có nghĩa. Sai số của bước làm tròn có thể lan đến kết quả cuối.
3. **Scale không đồng nhất:** `1.2`, `1.20` và `1.200` bằng nhau về giá trị toán học nhưng có scale khác nhau; `BigDecimal.equals`, lưu trữ hoặc hiển thị có thể xử lý chúng khác nhau.

Nguyên tắc xuyên suốt: **kiểm tra đại lượng và đơn vị, thống nhất chính sách tính toán, rồi chuẩn hóa kết quả tại ranh giới thích hợp**. Đừng dùng phép làm tròn hoặc ép kiểu chỉ để che đi dữ liệu nguồn sai đơn vị.

## 2. Không trộn đơn vị trước khi tổng hợp

Một số `42` không diễn đạt đầy đủ “42 mét”, “42 giây”, “42 USD” hay “42%”. Nếu phép cộng/trừ có ý nghĩa, các toán hạng phải cùng **loại đại lượng** và được đưa về **đơn vị tương thích** trước khi tính.

| Đầu vào | Phép tính sai | Cách quy đổi đúng |
| --- | --- | --- |
| `1.20 m` và `30 cm` | `1.20 + 30 = 31.20` | `1.20 m + 0.30 m = 1.50 m` |
| `2 kg` và `500 g` | `2 + 500 = 502` | `2 kg + 0.5 kg = 2.5 kg` |
| `2 phút` và `30 giây` | `2 + 30 = 32` | `120 s + 30 s = 150 s` |
| `15%` và tỷ lệ `0.10` | `15 + 0.10 = 15.10` | Nếu cùng cơ sở: `0.15 + 0.10 = 0.25 = 25%` |
| `10 USD` và `10 EUR` | `20` kèm một đơn vị tiền tệ tùy ý | Muốn cộng cần quy đổi theo tỷ giá được chọn, thời điểm và quy tắc làm tròn |

Ví dụ về phần trăm chỉ hợp lệ khi cùng mẫu số và việc cộng các tỷ lệ thật sự có ý nghĩa. Tỷ lệ 15% trên nhóm A và 10% trên nhóm B thường không thể cộng thẳng để ra 25% của toàn bộ tập khách hàng; phải xem quy mô, quan hệ của hai nhóm và mẫu số.

Không phải chuyển đổi đơn vị nào cũng là nhân một hằng số. Nhiệt độ tuyệt đối từ Celsius sang Kelvin có offset `+273.15`; độ chênh nhiệt độ lại có quy tắc ngữ nghĩa khác. Giá theo kilogram nhân với khối lượng tạo thành tiền và thay đổi **thứ nguyên**. Không nên viết một hàm `convert(value)` chung cho mọi loại dữ liệu mà bỏ qua ngữ nghĩa này.

### 2.1. Quy đổi chính xác bằng BigDecimal

Với đơn vị hệ thập phân, có thể dùng `BigDecimal` để không đi qua `double`:

~~~java
import java.math.BigDecimal;

BigDecimal meters = new BigDecimal("1.20");
BigDecimal centimeters = new BigDecimal("30");

BigDecimal centimetersAsMeters = centimeters.movePointLeft(2);
BigDecimal totalMeters = meters.add(centimetersAsMeters);
// Giá trị chính xác: 1.50 mét
~~~

`movePointLeft(2)` dịch dấu thập phân sang trái hai vị trí, nghĩa là chia giá trị cho 100. Nó phù hợp cho centimet → mét, không phải công thức tổng quát cho các đơn vị khác. Nếu hệ số đổi đơn vị hoặc phép chia tạo số thập phân vô hạn, chính sách precision/scale và rounding mode phải được quy định.

### 2.2. Đưa đơn vị vào kiểu dữ liệu

Nếu API nhận `BigDecimal left, BigDecimal right`, người gọi có thể truyền hai đại lượng bất kỳ. Một cách phòng tránh là bao gói giá trị cùng đơn vị: `record Length(BigDecimal amount, LengthUnit unit)`.

Khi đó, API cộng độ dài có thể chuyển mỗi đầu vào thành mét trước khi cộng. Với tiền, bản ghi `Money(amount, currency)` có thể từ chối phép cộng giữa USD và EUR. Điều này biến một lỗi âm thầm thành lỗi có thể phát hiện.

Ở ranh giới JSON, database hoặc file, cần truyền hoặc quy định đơn vị chính thức. Ví dụ một cột `value` chứa lúc thì mét, lúc thì centimet nhưng không có thông tin unit sẽ khiến bài toán tổng hợp khó có thể làm đúng.

## 3. Phân biệt precision và scale

Với `BigDecimal`, giá trị được biểu diễn theo công thức:

`giá trị = unscaledValue × 10^(-scale)`.

- **`precision()`:** số chữ số trong biểu diễn nguyên không chia tỷ lệ của `BigDecimal`, thường gọi là số chữ số có nghĩa **của biểu diễn**.
- **`scale()`:** số mũ điều chỉnh bằng lũy thừa 10; trong dạng thập phân thông thường là số chữ số sau dấu phẩy.
- **Precision của `MathContext`:** giới hạn **tổng số chữ số có nghĩa của kết quả** khi một phép toán áp context.
- **Rounding mode:** quy tắc xử lý chữ số phải loại bỏ khi cần làm tròn.

| Chuỗi đầu vào | `precision()` | `scale()` | Ghi chú |
| --- | ---: | ---: | --- |
| `"1.2"` | 2 | 1 | Hai chữ số |
| `"1.20"` | 3 | 2 | Bằng `1.2` về số học, khác biểu diễn |
| `"1.200"` | 4 | 3 | Có thêm số 0 cuối |
| `"123.4500"` | 7 | 4 | Hai số 0 cuối có trong biểu diễn |
| `"0.00120"` | 3 | 5 | Số 0 dẫn đầu không tăng precision |
| `"1E+3"` | 1 | -3 | Scale có thể âm, giá trị là 1000 |

`new MathContext(2, RoundingMode.HALF_EVEN)` giới hạn hai chữ số có nghĩa. `setScale(2, RoundingMode.HALF_EVEN)` đặt hai chữ số sau dấu phẩy. Hai thao tác **không tương đương**.

Đừng nhầm độ chính xác của phép đo với `BigDecimal.precision()`. Một máy đo báo `1.200 m` có thể hàm ý độ phân giải millimet, nhưng bản thân `BigDecimal` không biết sai số, độ tin cậy hay thiết bị đo. Nếu bài toán cần uncertainty/số chữ số có nghĩa theo quy ước đo lường, hãy lưu metadata tương ứng.

## 4. Scale khác nhau có thể gây sai lệch dù giá trị số bằng nhau

### 4.1. `equals` không giống `compareTo`

~~~java
BigDecimal x = new BigDecimal("1.20");
BigDecimal y = new BigDecimal("1.2");

boolean sameNumericValue = x.compareTo(y) == 0; // true
boolean sameRepresentation = x.equals(y);       // false
~~~

`compareTo` so giá trị toán học, `equals` xem cả giá trị và scale. Điều này tác động đến việc dùng `BigDecimal` làm khóa trong `HashMap` hoặc phần tử trong `HashSet`: `1.2` và `1.20` có thể được lưu thành hai khóa khác nhau. Không chuyển qua `double` để “sửa” việc so sánh, vì nó có thể mất precision.

### 4.2. Cộng chính xác không tự chuẩn hóa scale

~~~java
BigDecimal first = new BigDecimal("1.20");   // scale 2
BigDecimal second = new BigDecimal("0.300"); // scale 3

BigDecimal sum = first.add(second);
// sum.toPlainString(): "1.500"
// sum.precision(): 4
// sum.scale(): 3
~~~

Với cộng/trừ không truyền `MathContext`, scale ưu tiên là scale lớn hơn của hai toán hạng. Về số học, `1.500` và `1.50` bằng nhau; trong hợp đồng JSON hoặc quy định hiển thị chính xác hai chữ số sau dấu phẩy, chúng là hai biểu diễn khác nhau.

Với nhân không truyền context, scale ưu tiên thường là **tổng scale** của hai toán hạng; với phép chia exact, kết quả có thể không tồn tại ở dạng thập phân hữu hạn, chẳng hạn `1/3`. Không thể dùng một quy tắc scale giống hệt nhau cho mọi phép toán.

### 4.3. Chuẩn hóa bằng `setScale` có kiểm soát

~~~java
BigDecimal total = new BigDecimal("1.500");
BigDecimal normalized = total.setScale(2, RoundingMode.UNNECESSARY);
// 1.50: không mất giá trị toán học
~~~

`UNNECESSARY` yêu cầu không được làm tròn mất chữ số khác 0. Bỏ một chữ số `0` thừa khỏi `1.500` là chính xác nên thành công. Ngược lại:

~~~java
new BigDecimal("1.234").setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException: không thể giảm scale mà vẫn giữ nguyên giá trị
~~~

Khi nghiệp vụ **cho phép làm tròn**, chọn mode:

~~~java
new BigDecimal("1.005").setScale(2, RoundingMode.HALF_UP);   // 1.01
new BigDecimal("1.005").setScale(2, RoundingMode.HALF_EVEN); // 1.00
~~~

`HALF_UP` chọn xa số 0 khi đúng nửa. `HALF_EVEN` chọn chữ số cuối chẵn khi đúng nửa. Chúng không tự phù hợp với mọi quy định tính tiền hoặc thống kê. Nếu tăng scale, `setScale` thường thêm số 0 mà không đổi giá trị số.

`stripTrailingZeros()` có thể bỏ các số 0 cuối nhưng **thay đổi scale** và có thể tạo biểu diễn khoa học hoặc scale âm, ví dụ số 1000 có thể thành `1E+3`. Không nên gọi máy móc khi schema yêu cầu scale cố định.

## 5. Precision phải nhất quán với chính sách tính toán

### 5.1. MathContext chỉ ảnh hưởng tới phép toán có dùng nó

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

MathContext mc = new MathContext(7, RoundingMode.HALF_EVEN);
BigDecimal a = new BigDecimal("123456.789");
BigDecimal b = new BigDecimal("0.0012345");

BigDecimal exact = a.add(b);
BigDecimal bounded = a.add(b, mc);
~~~

`exact` giữ tổng chính xác, còn `bounded` làm tròn theo **7 chữ số có nghĩa**. Việc khai báo biến `mc` không tự đổi mọi phép toán về sau; bạn phải truyền context tới từng phép toán cần giới hạn.

| Context | Precision tối đa | Rounding mode |
| --- | ---: | --- |
| `MathContext.DECIMAL32` | 7 | `HALF_EVEN` |
| `MathContext.DECIMAL64` | 16 | `HALF_EVEN` |
| `MathContext.DECIMAL128` | 34 | `HALF_EVEN` |
| `MathContext.UNLIMITED` | Không giới hạn (giá trị precision là 0) | Lưu `HALF_UP`; không áp làm tròn do giới hạn precision |

`UNLIMITED` không làm cho thương `1/3` có biểu diễn hữu hạn: chia chính xác không có scale/mode thích hợp vẫn có thể ném `ArithmeticException`.

Nếu tầng xử lý A dùng `DECIMAL32` và tầng B dùng `DECIMAL128` hoặc không truyền context, kết quả có thể không nhất quán. Tạo một **chính sách số học cho từng đại lượng**, thay vì lựa chọn context khác nhau theo sở thích mỗi hàm.

### 5.2. Thời điểm làm tròn thay đổi kết quả

Xét:

~~~java
BigDecimal a = new BigDecimal("0.005");
BigDecimal b = new BigDecimal("0.005");

BigDecimal roundEach = a.setScale(2, RoundingMode.HALF_UP)
        .add(b.setScale(2, RoundingMode.HALF_UP)); // 0.02

BigDecimal roundSum = a.add(b)
        .setScale(2, RoundingMode.HALF_UP);       // 0.01
~~~

Nếu làm tròn từng phần, `0.005` thành `0.01` và tổng bằng `0.02`. Nếu cộng chính xác `0.010` rồi làm tròn kết quả, được `0.01`. Cả hai đều dùng cùng mode và cùng scale mục tiêu nhưng cho kết quả khác.

Đối với hóa đơn, phí, thuế hay báo cáo, quy định có thể yêu cầu làm tròn từng dòng hoặc tổng cộng cuối cùng. Vì vậy chính sách phải nói rõ **làm tròn giá trị nào và vào thời điểm nào**. Không có quy tắc “luôn làm tròn ở cuối” đúng cho mọi nghiệp vụ.

### 5.3. Tránh precision mất mát do đầu vào kiểu double

~~~java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal fromBinaryDouble = new BigDecimal(0.1);
~~~

`new BigDecimal(0.1)` nhận giá trị nhị phân `double` đã được xấp xỉ trước đó, nên có thể có chuỗi thập phân dài ngoài dự kiến. `new BigDecimal("0.1")` nhận đúng giá trị thập phân đã định. `BigDecimal.valueOf(0.1)` thường dễ dùng hơn constructor nhận `double` nhưng không phục hồi chính xác những thông tin đã mất trong các phép tính dấu phẩy động trước đó.

Nếu đơn vị hoặc tỷ giá được mô tả bằng decimal chính xác, dùng chuỗi decimal hoặc dữ liệu decimal được xác thực. Khi phép đo vốn là float/double từ cảm biến, cần đánh giá mức sai số thật sự thay vì giả định chuyển sang `BigDecimal` tự sửa được sai số cảm biến.

## 6. Chương trình Java 17+ đầy đủ

Chương trình dưới đây biểu diễn cả một phép cộng **cố ý sai** vì thiếu chuyển đơn vị và cách xử lý đúng. Nó cũng kiểm tra precision/scale, rounding mode và điều kiện không cho cộng hai loại tiền khác nhau.

Tạo file `UnitPrecisionScaleDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class UnitPrecisionScaleDemo {
    enum LengthUnit {
        METER, CENTIMETER
    }

    record Length(BigDecimal amount, LengthUnit unit) {
        public Length {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(unit, "unit");
        }

        BigDecimal inMeters() {
            return switch (unit) {
                case METER -> amount;
                case CENTIMETER -> amount.movePointLeft(2);
            };
        }
    }

    enum CurrencyCode {
        USD, EUR
    }

    record Money(BigDecimal amount, CurrencyCode currency) {
        public Money {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
        }

        Money add(Money other) {
            if (currency != other.currency) {
                throw new IllegalArgumentException("Currency mismatch");
            }
            return new Money(amount.add(other.amount), currency);
        }
    }

    public static void main(String[] args) {
        Length a = new Length(new BigDecimal("1.20"), LengthUnit.METER);
        Length b = new Length(new BigDecimal("30"), LengthUnit.CENTIMETER);

        System.out.println("raw lengths (WRONG): "
                + a.amount().add(b.amount()));
        System.out.println("sum normalized: "
                + a.inMeters().add(b.inMeters())
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .toPlainString()
                + " m");

        BigDecimal p = new BigDecimal("1.20");
        BigDecimal q = new BigDecimal("0.300");
        BigDecimal sum = p.add(q);

        System.out.println("raw sum: " + sum.toPlainString()
                + " (precision=" + sum.precision()
                + ", scale=" + sum.scale() + ")");
        System.out.println("display scale: "
                + sum.setScale(2, RoundingMode.UNNECESSARY)
                        .toPlainString());

        System.out.println("compareTo equal: "
                + (new BigDecimal("1.20").compareTo(
                        new BigDecimal("1.2")) == 0));
        System.out.println("equals equal: "
                + new BigDecimal("1.20").equals(
                        new BigDecimal("1.2")));

        try {
            new BigDecimal("1.234")
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("UNNECESSARY: ArithmeticException");
        }

        System.out.println("HALF_UP: "
                + new BigDecimal("1.005")
                        .setScale(2, RoundingMode.HALF_UP));
        System.out.println("HALF_EVEN: "
                + new BigDecimal("1.005")
                        .setScale(2, RoundingMode.HALF_EVEN));

        Money usdA = new Money(new BigDecimal("1.50"), CurrencyCode.USD);
        Money usdB = new Money(new BigDecimal("2.00"), CurrencyCode.USD);

        System.out.println("same currency: "
                + usdA.add(usdB).amount().toPlainString() + " USD");

        try {
            usdA.add(new Money(new BigDecimal("2.00"), CurrencyCode.EUR));
        } catch (IllegalArgumentException ex) {
            System.out.println("mixed currency: IllegalArgumentException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac UnitPrecisionScaleDemo.java
java UnitPrecisionScaleDemo
~~~

Kết quả:

~~~text
raw lengths (WRONG): 31.20
sum normalized: 1.50 m
raw sum: 1.500 (precision=4, scale=3)
display scale: 1.50
compareTo equal: true
equals equal: false
UNNECESSARY: ArithmeticException
HALF_UP: 1.01
HALF_EVEN: 1.00
same currency: 3.50 USD
mixed currency: IllegalArgumentException
~~~

Đoạn `raw lengths` minh họa **hành vi sai cần tránh**; mã chỉ chạy được vì cả hai số được lấy ra từ record rồi cộng thủ công. `Length.inMeters()` mới là đường đi đúng để cộng các độ dài khác đơn vị. `Money.add` là phép cộng trong cùng đồng tiền, từ chối cặp tiền tệ khác nhau. Nếu thực sự muốn đổi ngoại tệ, cần phương thức chuyển đổi riêng có tỷ giá, thời điểm và quy tắc làm tròn đã xác định.

`setScale(2, UNNECESSARY)` trong ví dụ xác nhận kết quả mét có thể đưa về hai chữ số sau dấu phẩy **mà không mất giá trị**. Nếu có số đo đến millimet như `1.234 m`, phép chuẩn hóa ấy ném ngoại lệ; quyết định từ chối hay làm tròn phải dựa vào chính sách của dữ liệu đo.

## 7. Thống nhất chính sách tại ranh giới dữ liệu

### 7.1. Trên API và thông điệp

Một JSON rõ nghĩa có thể mang cả giá trị decimal và đơn vị:

~~~json
{
  "value": "125.50",
  "unit": "CENTIMETER"
}
~~~

Trường `value` dạng chuỗi giúp client và server không phải suy đoán cách biểu diễn nhị phân nếu contract yêu cầu decimal chính xác. Bên nhận kiểm tra tên đơn vị, phạm vi giá trị, độ phân giải cho phép và cách quy đổi sang đơn vị chuẩn.

Đừng tin tưởng rằng chữ số `0` cuối là metadata đủ để mô tả độ chính xác đo lường. Nếu bài toán cần độ sai số, sai số tuyệt đối/tương đối hoặc độ phân giải, cần quy định những thuộc tính ấy riêng.

### 7.2. Trong database

SQL `DECIMAL(p,s)` có giới hạn precision/scale lưu trữ theo schema, trong đó `p` và `s` thường biểu thị tổng chữ số và chữ số sau dấu thập phân. Các quy tắc cụ thể về phạm vi, xử lý tràn và làm tròn có thể khác theo hệ quản trị. Một cột `DECIMAL(12,2)` **không tương đương** với việc áp `new MathContext(12, ...)` trong Java, bởi `MathContext` giới hạn chữ số có nghĩa của phép toán và không đặt scale về 2.

Schema cần đồng thời nêu hoặc lưu đơn vị chuẩn và, nếu là tiền, mã tiền tệ. `1000` có thể nghĩa là 1000 gram hoặc 1000 kilogram; đặt scale 2 trong SQL không giải quyết sự nhập nhằng đó.

### 7.3. Khi hiển thị

Phân biệt dữ liệu tính toán và biểu diễn giao diện. Có thể dùng `toPlainString()` khi cần tránh biểu diễn khoa học của `BigDecimal.toString()`. Nếu hợp đồng hiển thị hai chữ số, chuẩn hóa **kết quả phục vụ hiển thị** thay vì làm mất độ phân giải dữ liệu gốc.

Một số hệ thống có chính sách làm tròn ở mức dòng hóa đơn, một số ở tổng cuối, hoặc cả hai ở các điểm khác nhau. Ghi rõ chính sách thành tài liệu và test. Khi thay đổi nó, cần đánh giá hiệu ứng lên dữ liệu cũ thay vì chỉ thay formatter.

## 8. Bảng kiểm khi thiết kế phép tổng hợp

| Câu hỏi | Quyết định cần có | Ví dụ |
| --- | --- | --- |
| Hai toán hạng có cùng đại lượng không? | Cấm trộn thứ nguyên | Không cộng mét với kilogram |
| Đơn vị chuẩn là gì? | Quy đổi trước khi tính | Centimet → mét, gram → kilogram |
| Đồng tiền nào? | Giữ mã tiền; tỷ giá phải tường minh | Không cộng USD với EUR trực tiếp |
| Precision trung gian bao nhiêu? | Chọn exact hay `MathContext` | Dùng `DECIMAL64` nếu được chấp nhận |
| Scale kết quả bao nhiêu? | Xác định theo loại dữ liệu | Mức tiền lưu 2 chữ số theo quy tắc riêng |
| Khi nào được làm tròn? | Xác định thời điểm | Mỗi dòng hay tổng cuối |
| Rounding mode nào? | Thỏa thuận rõ | `HALF_EVEN`, `HALF_UP`, `UNNECESSARY` |
| Mất dữ liệu có được phép không? | Làm tròn rõ ràng hoặc báo lỗi | `setScale(2, UNNECESSARY)` nếu cấm mất phần lẻ |
| Có cần độ tin cậy đo lường không? | Lưu metadata sai số riêng | `1.200 m ± 0.005 m` |

Khi cần lượng tiền theo đơn vị nhỏ nhất (cent), có thể biểu diễn bằng số nguyên sau khi **quy định rõ phép chuyển đổi và rounding policy**. Ví dụ 1 USD = 100 cent, nhưng không suy rộng giả định “mọi loại tiền đều có hai chữ số thập phân”.

Trong bài toán tỉ lệ và đơn vị đo, cần xem xét cả phạm vi số rất lớn/nhỏ, overflow của phép tính nền, phần dư khi chia, và cách áp precision để không làm tròn trung gian ngoài ý muốn.

## 9. Lỗi phổ biến

1. **Cộng giá trị mà bỏ nhãn đơn vị:** kết quả tính toán hợp lệ nhưng sai đại lượng.
2. **Cộng USD với EUR như cùng tiền tệ:** muốn quy đổi phải có tỷ giá, ngày hiệu lực và chính sách làm tròn.
3. **Dùng phần trăm như tỷ lệ thập phân:** 15% là 0.15; ngay cả khi đổi xong, chỉ cộng tỷ lệ khi mẫu số/cơ sở phù hợp.
4. **Nhầm precision và scale:** `MathContext(2)` không phải giữ hai chữ số sau dấu phẩy.
5. **Dùng `equals` khi cần so giá trị số:** `1.20` và `1.2` bằng nhau theo `compareTo` nhưng không bằng nhau theo `equals`.
6. **Làm tròn từng bước mà không có chính sách:** kết quả khác phép cộng chính xác rồi làm tròn sau cùng.
7. **Ép scale quá sớm:** có thể mất thông tin đo lường như millimet trong số mét.
8. **Dùng `new BigDecimal(double)` cho input decimal:** có thể đưa sai số nhị phân vào luồng tính toán.
9. **Gọi `stripTrailingZeros()` để chữa mọi chênh lệch scale:** có thể thay đổi scale/chuỗi hiển thị ngoài mong đợi.
10. **Giữ một scale áp cho mọi đại lượng:** tiền, khối lượng, thời gian, tỷ lệ có yêu cầu khác nhau.
11. **Tin precision nhiều tức là phép đo đáng tin:** precision của biểu diễn không nói lên sai số đo.
12. **Không kiểm tra unit/scale ở ranh giới API:** dữ liệu sai lan vào tổng hợp và khó truy nguyên.

## 10. Bài tập kiểm tra

**Câu 1.** Có `2.50 kg` và `750 g`. Tổng bằng bao nhiêu kilogram?

**Đáp án:** `750 g = 0.750 kg`, nên tổng exact là `3.250 kg`. Nếu hợp đồng yêu cầu scale 2 và không mất giá trị, có thể chuẩn hóa thành `3.25 kg` bằng `setScale(2, UNNECESSARY)`.

**Câu 2.** `new BigDecimal("12.0").equals(new BigDecimal("12.00"))` đúng hay sai? Muốn so giá trị số thì làm gì?

**Đáp án:** Sai vì scale khác nhau. Dùng `compareTo(...) == 0` để kiểm tra bằng nhau về giá trị số.

**Câu 3.** Cộng `0.005 + 0.005` rồi làm tròn tổng theo `HALF_UP` đến hai chữ số được bao nhiêu? Làm tròn mỗi phần trước thì sao?

**Đáp án:** Làm tròn tổng sau khi cộng exact được `0.01`. Làm tròn mỗi `0.005` thành `0.01` rồi cộng thành `0.02`.

**Câu 4.** API chấp nhận độ dài tối đa hai chữ số sau dấu thập phân của mét và nhận `1.234 m`. `setScale(2, UNNECESSARY)` có âm thầm đổi thành `1.23` không?

**Đáp án:** Không, nó ném `ArithmeticException` vì việc hạ scale sẽ mất chữ số khác 0. Muốn đổi sang centimet chính xác hay làm tròn đến centimet phải có yêu cầu rõ ràng.

**Câu 5.** Một giá trị `15` biểu thị 15%, còn giá trị `0.05` biểu thị tỷ lệ 5%. Kết quả cộng 20% được tính thế nào?

**Đáp án:** Đổi 15% về `0.15`; khi cùng cơ sở và được phép cộng tỷ lệ, tổng là `0.20` = 20%.

**Câu 6.** Vì sao `DECIMAL128` không thay thế chính sách hai chữ số sau dấu phẩy?

**Đáp án:** `DECIMAL128` giới hạn 34 chữ số có nghĩa và mặc định `HALF_EVEN`, không đặt scale bằng 2. Cần dùng `setScale(2, mode)` hoặc quy tắc trình bày phù hợp tại điểm được chỉ định.

## 11. Tài liệu Java chính thức

- [BigDecimal — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `precision()`, `scale()`, `setScale()`, `compareTo()`, `equals()`, `movePointLeft()`.
- [MathContext — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html): giới hạn chữ số có nghĩa và rounding mode.
- [RoundingMode — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): quy tắc làm tròn.
- [Record declarations — JLS Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10): mô hình dữ liệu record.

**Ghi nhớ:** Tổng hợp số đúng bắt đầu từ **cùng đại lượng, cùng đơn vị**, tiếp đến **chính sách precision của phép toán**, rồi **scale và rounding mode tại ranh giới đã quy định**. `BigDecimal` giúp tính toán decimal chính xác nhưng không tự hiểu đơn vị, đồng tiền, độ tin cậy phép đo hay quy tắc làm tròn của hệ thống.


</details>

- [Quay lại đầu trang](#back-to-top)
