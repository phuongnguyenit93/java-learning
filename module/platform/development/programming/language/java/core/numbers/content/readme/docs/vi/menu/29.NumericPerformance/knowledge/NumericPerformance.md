<a id="back-to-top"></a>

# Hiệu năng

## Menu
- [Primitive so với wrapper về cấp phát và bố trí dữ liệu](#numeric-performance-001)
- [Boxing trong collection, stream và vòng lặp](#numeric-performance-002)
- [Chi phí số học BigInteger theo độ dài toán hạng](#numeric-performance-003)
- [Chi phí BigDecimal theo precision và scale](#numeric-performance-004)
- [Tránh tăng precision hoặc scale không cần thiết](#numeric-performance-005)
- [Không thay đổi quy tắc chính xác chỉ để rút ngắn thời gian chạy](#numeric-performance-006)
- [Benchmark cần tránh constant folding và dead-code elimination](#numeric-performance-007)
- [Đo với miền dữ liệu thực tế và cả tình huống biên](#numeric-performance-008)

## <a id="numeric-performance-001">Primitive so với wrapper về cấp phát và bố trí dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Primitive so với wrapper về cấp phát và bố trí dữ liệu trong Java

## 1. Vấn đề hiệu năng cần hiểu

Java phân biệt **kiểu nguyên thủy** (*primitive*) như `int`, `long`, `double` với các **lớp bao** (*wrapper*) như `Integer`, `Long`, `Double`.

Về mặt giá trị số học, một `int` chứa 100 và một `Integer` đại diện cho 100 có thể phục vụ cùng phép tính. Nhưng về mô hình dữ liệu, bố trí bộ nhớ và chi phí chạy, chúng không tương đương:

- Một biến hoặc ô mảng kiểu primitive biểu diễn **giá trị trực tiếp** theo kiểu đã khai báo; không yêu cầu một đối tượng wrapper riêng chỉ để giữ con số.
- Một biến hoặc ô mảng kiểu wrapper chứa **tham chiếu** tới đối tượng (hoặc `null`). Khi đối tượng wrapper thực sự được cấp phát, nó cần vùng lưu dữ liệu đối tượng bên cạnh giá trị số.
- Việc đổi giữa primitive và wrapper bằng **boxing/unboxing** có thể gây cấp phát đối tượng, tăng lượng dữ liệu phải đọc, tăng áp lực GC và tốn các bước chuyển đổi. Tuy nhiên, JVM có thể tái sử dụng wrapper hoặc tối ưu bỏ một số cấp phát; không thể mặc định *mỗi phép boxing luôn tạo một đối tượng mới*.

Bài này phân tích sự khác biệt về **cấp phát và bố trí dữ liệu**, cách nó ảnh hưởng đến thao tác trên tập số lớn, và cách chọn cấu trúc dữ liệu đúng. Đây là mô hình Java Core và những đặc trưng JVM cần hiểu để đo hiệu năng có căn cứ.

## 2. Primitive và wrapper tương ứng

| Primitive | Wrapper | Kích thước giá trị primitive theo Java | Đặc điểm |
| --- | --- | --- | --- |
| `byte` | `Byte` | 8 bit | Số nguyên có dấu |
| `short` | `Short` | 16 bit | Số nguyên có dấu |
| `int` | `Integer` | 32 bit | Số nguyên có dấu |
| `long` | `Long` | 64 bit | Số nguyên có dấu |
| `float` | `Float` | 32 bit | Số chấm động IEEE 754 |
| `double` | `Double` | 64 bit | Số chấm động IEEE 754 |
| `char` | `Character` | 16 bit | Đơn vị mã UTF-16 |
| `boolean` | `Boolean` | Không quy định một kích thước lưu trữ byte duy nhất trong mọi ngữ cảnh | Hai giá trị logic `true`, `false` |

Các số bit trong bảng áp dụng cho **miền giá trị/biểu diễn primitive**, **không phải kích thước một đối tượng wrapper**. Ví dụ `Integer` chứa một trường `int`, nhưng đối tượng `Integer` có thể cần thêm phần quản lý đối tượng của JVM. Kích thước đối tượng phụ thuộc JVM, kiến trúc, cấu hình nén tham chiếu và căn chỉnh.

Ví dụ về kiểu:

```java
int direct = 42;
Integer reference = Integer.valueOf(42);

int[] directArray = {10, 20, 30};
Integer[] referenceArray = {10, 20, 30};
```

`direct` mang giá trị `42` theo kiểu `int`; `reference` là tham chiếu có kiểu `Integer`. Với dữ liệu số học số lượng lớn, sự khác biệt thường rõ nhất ở **mảng primitive** so với **mảng wrapper** hoặc collection lưu wrapper.

## 3. Bố trí bộ nhớ của int[] và Integer[]

### 3.1. int[]: phần tử giá trị nguyên thủy nằm ngay trong mảng

`int[] values = new int[4]` tạo **một đối tượng mảng** chứa bốn ô `int`. Phần dữ liệu phần tử của mảng chiếm tổng cộng `4 × 4 = 16` byte cho bốn giá trị `int`, **chưa tính header và padding của đối tượng mảng**.

Sơ đồ logic:

```text
Biến values
      |
      v
+-----------------------------------------+
| int[]: header | 10 | 20 | 30 | 40 | ...  |
+-----------------------------------------+
                4B   4B   4B   4B
```

Các ô phần tử nằm liên tiếp trong vùng phần tử của một đối tượng mảng. JVM có thể quét dữ liệu tuần tự với locality tốt, nhiều giá trị nằm trong cùng cache line. Không có **đối tượng `Integer` riêng** chỉ để giữ từng phần tử.

Lưu ý: `int[]` vẫn là **đối tượng** trong Java. Tuyên bố “primitive luôn nằm trên stack” là sai. Nơi dữ liệu được đặt vật lý do JVM quyết định; một biến cục bộ kiểu primitive có thể nằm trong thanh ghi, stack frame hoặc bị tối ưu, còn phần tử `int[]` thuộc dữ liệu của đối tượng mảng.

### 3.2. Integer[]: mảng lưu tham chiếu, giá trị nằm trong wrapper

`Integer[] values = new Integer[4]` tạo **một đối tượng mảng gồm bốn tham chiếu**. Ban đầu cả bốn tham chiếu là `null`. Để lưu các giá trị số, chương trình phải có các đối tượng `Integer` được tham chiếu.

Sơ đồ logic (không thể hiện kích thước thực tế):

```text
Biến values
      |
      v
+-----------------------------------+
| Integer[] header | ref | ref | ref |
+--------------------|-----|-----|--+
                     |     |     |
                     v     v     v
                  +----+ +----+ +----+
                  |1000| |2000| |3000|
                  +----+ +----+ +----+
                  Mỗi ô là một đối tượng Integer
                  nếu không được tái sử dụng/tối ưu
```

Các tham chiếu nằm liên tiếp trong mảng, nhưng các đối tượng wrapper mà chúng trỏ tới **không được Java bảo đảm nằm sát nhau**. Khi duyệt mảng, CPU có thể cần truy cập thêm vùng dữ liệu theo các tham chiếu, làm giảm locality so với `int[]`. Các wrapper vẫn cần được tính là đối tượng có thể được GC quản lý nếu chúng tồn tại thực sự trong heap.

Đối với ví dụ:

```java
int[] primitive = {1000, 2000, 3000};
Integer[] boxed = {1000, 2000, 3000};
```

- Cả hai đều là **một mảng** và có `length = 3`.
- Mảng `primitive` chứa trực tiếp ba giá trị `int`.
- Mảng `boxed` chứa ba tham chiếu `Integer`; cú pháp gán số nguyên vào `Integer[]` kích hoạt autoboxing. Các đối tượng tương ứng có thể đã tồn tại, được tạo mới, hoặc được JVM tối ưu theo ngữ cảnh.
- Không được tính `Integer[]` như “12 byte dữ liệu cho ba số” vì nó chứa **tham chiếu**, còn dữ liệu số thuộc wrapper.

### 3.3. Mô hình ước lượng, không phải con số tuyệt đối

Với `N` phần tử kiểu `int`:

- `int[]`: phần dữ liệu phần tử là **`4 × N` byte**, cộng một header mảng và padding.
- `Integer[]`: phần dữ liệu mảng là **`N × kích thước tham chiếu`**, cộng header mảng; ngoài ra có dữ liệu của các **wrapper phân biệt thực sự còn tồn tại**, mỗi wrapper có header, trường `int`, padding và có thể được dùng chung giữa nhiều ô.
- `ArrayList<Integer>`: ngoài đối tượng danh sách và backing array của tham chiếu, còn có wrapper cho các phần tử được lưu, nếu không dùng chung; backing array có thể có capacity lớn hơn size.

Không có một con số “mỗi `Integer` chính xác 16 byte trên mọi JVM” hoặc “tham chiếu luôn 8 byte”. Ví dụ, một HotSpot 64 bit thường có thể dùng **compressed ordinary object pointers** (compressed oops) để lưu tham chiếu nhỏ hơn kích thước con trỏ máy; thiết lập nén tham chiếu, header, alignment và cấu hình khác sẽ thay đổi kết quả thực tế.

Phép so sánh có ích là **cấu trúc cấp phát và số lần theo tham chiếu**, sau đó đo bằng công cụ phù hợp trên JVM ứng dụng.

## 4. Autoboxing/unboxing và chi phí ẩn

**Boxing** chuyển primitive thành wrapper. **Unboxing** lấy primitive từ wrapper. Java thường tự chèn các chuyển đổi này:

```java
int a = 7;
Integer b = a; // autoboxing: tương đương Integer.valueOf(a)
int c = b;     // unboxing: tương đương b.intValue()
```

Điểm quan trọng:

1. `Integer.valueOf(int)` có thể trả lại đối tượng đã cache thay vì tạo đối tượng mới.
2. Java bắt buộc hỗ trợ vùng cache nhất định, bao gồm giá trị `Integer` trong khoảng **-128 đến 127** với boxing/`valueOf`; giá trị ngoài vùng có thể có chiến lược tái sử dụng khác tùy JVM.
3. Với wrapper được tạo mới nhưng không “thoát” ra khỏi một phạm vi tối ưu, JIT có thể loại bỏ cấp phát hoặc phép boxing bằng các kỹ thuật như escape analysis và scalar replacement. Đây là **khả năng tối ưu**, không phải cam kết về mọi đường chạy.
4. Unboxing `null` gây `NullPointerException`.
5. Khi boxing diễn ra trên hàng triệu phần tử không tận dụng cache hoặc tối ưu, lượng cấp phát có thể đáng kể.

Ví dụ:

```java
Integer smallA = Integer.valueOf(127);
Integer smallB = Integer.valueOf(127);
System.out.println(smallA == smallB);       // true: cùng đối tượng cached

Integer largeA = Integer.valueOf(1000);
Integer largeB = Integer.valueOf(1000);
System.out.println(largeA.equals(largeB)); // true: cùng giá trị
// Không dựa vào largeA == largeB: chiến lược cache có thể thay đổi.
```

Toán tử `==` với **hai tham chiếu `Integer`** so sánh danh tính đối tượng, còn `equals()` so sánh giá trị `int` mà wrapper chứa. Khi một bên là `int`, Java có thể unbox bên còn lại rồi so sánh số học, khiến code dùng `==` có ngữ nghĩa khác theo kiểu toán hạng. Nên dùng `equals()` khi chủ ý so sánh hai wrapper theo giá trị, và kiểm tra `null` khi cần.

## 5. Chương trình Java 17 độc lập, có đầu ra kiểm chứng

Tạo tệp **`PrimitiveWrapperLayoutDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrimitiveWrapperLayoutDemo {
    public static void main(String[] args) {
        int[] primitive = {1000, 2000, 3000};
        Integer[] wrapper = {1000, 2000, 3000};

        System.out.println("primitiveType=" + primitive.getClass().getName());
        System.out.println("wrapperType=" + wrapper.getClass().getName());
        System.out.println("primitiveSum=" + Arrays.stream(primitive).sum());
        System.out.println("wrapperSum=" + Arrays.stream(wrapper)
                .mapToInt(Integer::intValue).sum());

        List<Integer> list = new ArrayList<>();
        for (int number : primitive) {
            list.add(number);
        }
        System.out.println("listSum=" + list.stream()
                .mapToInt(Integer::intValue).sum());

        int[] zeroDefaults = new int[2];
        Integer[] nullDefaults = new Integer[2];
        System.out.println("primitiveDefault=" + zeroDefaults[0]);
        System.out.println("wrapperDefault=" + nullDefaults[0]);

        Integer first = Integer.valueOf(127);
        Integer second = Integer.valueOf(127);
        System.out.println("cachedIdentity=" + (first == second));
        System.out.println("cachedEquality=" + first.equals(second));

        Integer[] nullable = {1, null, 3};
        System.out.println("wrapperCanContainNull="
                + Arrays.asList(nullable).contains(null));
        try {
            int invalid = nullable[1];
            System.out.println(invalid);
        } catch (NullPointerException exception) {
            System.out.println("unboxNull=NullPointerException");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac PrimitiveWrapperLayoutDemo.java
java PrimitiveWrapperLayoutDemo
```

Kết quả đã chạy kiểm chứng với `javac --release 17`:

```text
primitiveType=[I
wrapperType=[Ljava.lang.Integer;
primitiveSum=6000
wrapperSum=6000
listSum=6000
primitiveDefault=0
wrapperDefault=null
cachedIdentity=true
cachedEquality=true
wrapperCanContainNull=true
unboxNull=NullPointerException
```

Giải thích từng nhóm:

- `[I` là tên runtime của mảng `int[]`; `[Ljava.lang.Integer;` là tên runtime của mảng tham chiếu `Integer[]`. Tên dạng này xuất hiện ở `Class.getName()` theo quy ước JVM.
- Ba đường tính tổng đều cho 6000. Kết quả số học bằng nhau **không có nghĩa** ba cấu trúc dữ liệu được lưu với cùng lượng bộ nhớ hoặc tốc độ.
- `new int[2]` khởi tạo phần tử về `0`; `new Integer[2]` khởi tạo phần tử về `null`.
- Hai lời gọi `Integer.valueOf(127)` trả cùng đối tượng trong vùng cache bảo đảm; cả `==` và `equals()` đều trả true ở ví dụ này.
- `Integer[]` có thể lưu `null`. Đưa `null` qua phép chuyển thành `int` gây `NullPointerException`.
- Chương trình không tự đo heap vì phép kiểm tra `Runtime.totalMemory() - freeMemory()` trước/sau một vòng lặp nhỏ không phải phép đo tin cậy cho kích thước đối tượng; GC, JIT và các cấp phát khác ảnh hưởng số liệu.

## 6. ArrayList<Integer> so với int[] trong tập dữ liệu lớn

Khi cần một triệu số:

```java
int[] raw = new int[1_000_000];

List<Integer> items = new ArrayList<>();
// items.add(number) sẽ boxing nếu number có kiểu int.
```

**Mảng primitive**:
- Dữ liệu `int` ở các ô liên tiếp, cho locality tốt khi duyệt tuần tự.
- Không có trường hợp phần tử `null`.
- Có độ dài cố định; không tự tăng kích thước.
- Có thể đổi giá trị tại chỉ số trực tiếp, không cần boxing.

**`ArrayList<Integer>`**:
- Danh sách quản lý một backing array chứa tham chiếu, capacity có thể lớn hơn số phần tử thực.
- Các phần tử biểu diễn bằng `Integer`, có thể có những đối tượng wrapper được dùng chung, tái sử dụng hoặc riêng lẻ.
- Cung cấp API `List` linh hoạt, tăng/giảm kích thước, tích hợp Collections/Generics.
- Chấp nhận `null` và có các phép tra cứu/thao tác đối tượng phù hợp.
- Khi thao tác `get(i)` để tính toán với `int`, thường có bước unboxing và có thể thêm truy cập gián tiếp.

Nếu kích thước được biết trước, `new ArrayList<>(capacity)` có thể giảm các lần mở rộng backing array, nhưng không tự xóa chi phí tham chiếu/wrapper. Nếu cần số liệu lưu trữ dày đặc và xử lý số học, `int[]` thường là lựa chọn khởi đầu hợp lý.

Với `long[]`, `double[]` cũng tương tự. Giá trị primitive `long`/`double` có 8 byte mỗi phần tử, còn `Long[]`/`Double[]` lưu tham chiếu tới đối tượng. Không dùng bảng kích thước `int[]` để suy ra chính xác overhead của tất cả loại wrapper.

## 7. Primitive stream: giảm nhu cầu boxing trong pipeline

Java có các stream dành cho số nguyên thủy:

- `IntStream` cho `int`.
- `LongStream` cho `long`.
- `DoubleStream` cho `double`.

Từ `int[]` có thể tạo `IntStream` bằng `Arrays.stream(intArray)`. Từ `Integer[]`, `Arrays.stream(wrapperArray)` tạo `Stream<Integer>` và thường cần unboxing khi xử lý số học:

```java
int[] numbers = {1, 2, 3};
Integer[] wrappers = {1, 2, 3};

int directSum = Arrays.stream(numbers).sum();
int unboxedSum = Arrays.stream(wrappers)
        .mapToInt(Integer::intValue)
        .sum();
// Hai tổng đều là 6.
```

`IntStream` còn có `average()`, `min()`, `max()`, `summaryStatistics()` và `toArray()` cho các bước xử lý số, giúp tránh việc tạo `Stream<Integer>` và boxing mỗi giá trị nếu không cần.

Khi thật sự cần đối tượng, có thể dùng:

```java
List<Integer> boxed = Arrays.stream(new int[] {1, 2, 3})
        .boxed()
        .toList(); // Java 16+, trả List<Integer>
```

`boxed()` là ranh giới chuyển primitive thành wrapper: nếu kết quả giữ các đối tượng lâu dài thì chi phí lưu trữ có thể tăng. Không có khẳng định rằng stream luôn nhanh hơn vòng lặp: với pipeline rất nhỏ, chi phí tạo/điều phối pipeline và mức tối ưu JIT có thể làm vòng lặp nhanh hơn. Hãy đo trên workload thật.

## 8. Cấp phát xảy ra ở đâu, và JVM có thể tối ưu thế nào?

### 8.1. Không đồng nhất “wrapper” với “mỗi biểu thức có một allocation”

`Integer.valueOf(42)` có thể trả đối tượng từ cache. Mã tạo wrapper tạm thời đôi khi được JIT tối ưu hết nếu không cần danh tính đối tượng.

Ví dụ về ý tưởng:

```java
static int compute(int x) {
    Integer boxed = Integer.valueOf(x);
    return boxed.intValue() * 2;
}
```

Đây là một **phương thức minh họa** để đặt trong một lớp. Trình biên dịch hoặc JIT có thể tối ưu phần boxing/unboxing vì đối tượng không cần được giữ lại. Nhưng không nên khẳng định lời gọi trên **luôn** được loại bỏ allocation ở mọi JVM, mọi mức tối ưu hoặc cách chạy.

### 8.2. Khi nào wrapper khó bị tối ưu bỏ?

Nếu wrapper được lưu lâu dài trong một collection, được trả ra khỏi một hàm, hoặc danh tính của nó được quan sát, JIT có ít cơ hội hơn để loại bỏ biểu diễn đối tượng. Ví dụ:

```java
static List<Integer> makeList(int[] values) {
    List<Integer> result = new ArrayList<>(values.length);
    for (int value : values) {
        result.add(value); // autoboxing
    }
    return result;
}
```

Với dữ liệu ở ngoài vùng cache và các phần tử được giữ lại bởi danh sách, các đối tượng wrapper có thể tạo thêm nhiều cấp phát so với `int[]`. Phải đo lượng heap đã giữ (*retained size*) hoặc cấp phát thực tế thay vì suy ra một lượng byte cố định từ source code.

### 8.3. Heap, stack và thanh ghi

Không nên viết “primitive nằm trên stack, object nằm trên heap” như một quy tắc tuyệt đối:

- Biến cục bộ `int` có thể được giữ trong thanh ghi hoặc stack tùy tối ưu của JVM.
- Trường `int` của một đối tượng là một phần dữ liệu trong đối tượng đó.
- Mảng `int[]` và `Integer[]` đều là các đối tượng Java.
- Một đối tượng wrapper có thể được JVM loại bỏ thông qua tối ưu và không còn một allocation heap thực tế.
- Kích thước header/căn chỉnh, compressed references và cách GC di chuyển đối tượng thuộc chi tiết triển khai JVM.

Trong phân tích hiệu năng, câu hỏi tốt hơn là **“dữ liệu này được lưu và truy cập bằng bao nhiêu đối tượng/tham chiếu ở runtime đã đo?”** thay vì suy đoán chỉ từ việc một biến được khai báo bên trong phương thức.

## 9. Ảnh hưởng tới CPU cache và garbage collection

### CPU cache và duyệt tuần tự

`int[]` lưu phần tử `int` trực tiếp và liên tiếp, nên khi duyệt một vùng số lớn, CPU thường có lợi từ locality và prefetch. `Integer[]` hoặc `ArrayList<Integer>` phải đọc tham chiếu rồi thường đọc wrapper tương ứng; nếu các wrapper ở vị trí rải rác, có thể cần thêm cache line và nhiều truy cập bộ nhớ hơn.

Điều này thường quan trọng trong thống kê, đồ thị số, dữ liệu đo lường hoặc pipeline tính toán trên tập lớn. Nhưng hiệu năng cụ thể vẫn phụ thuộc CPU, cache, kích thước mảng, branch prediction, JIT và công việc trong mỗi vòng lặp.

### Áp lực GC

Nếu ứng dụng tạo hàng triệu wrapper sống ngắn, GC có thêm đối tượng cần theo dõi và thu hồi. Nếu wrapper sống lâu trong collection, chúng chiếm retained heap và có thể tăng chi phí đánh dấu/duyệt đối tượng của GC.

Không tự động kết luận “cứ xuất hiện `Integer` trong code là có một object allocation”. Caching, scalar replacement và các tối ưu khác có thể làm số allocation runtime khác số biểu thức boxing ở source.

### Tính toán và bộ nhớ không luôn là cùng bottleneck

Một vòng lặp cộng `int[]` đơn giản có thể chịu ảnh hưởng mạnh bởi băng thông bộ nhớ, trong khi phép tính phức tạp trên từng phần tử có thể bị giới hạn CPU trước. Lựa chọn wrapper hay primitive cần xét tổng chi phí của chương trình, không chỉ tốc độ một thao tác.

## 10. Khi nào bắt buộc hoặc nên dùng wrapper?

Dù có chi phí tiềm ẩn, wrapper có những vai trò chính đáng:

1. **Generics/Collections:** `List<int>`, `Map<String, long>` không hợp lệ trong Java 17; cần `List<Integer>`, `Map<String, Long>` (hoặc thiết kế mảng/cấu trúc chuyên biệt).
2. **Giá trị có thể vắng mặt:** `Integer` có thể `null`, còn `int` không thể. Nhưng đừng dùng `null` tùy tiện nếu có thể thiết kế trạng thái rõ ràng hơn.
3. **API yêu cầu đối tượng:** framework, reflection, generic processing hoặc một API nhận `Object`/`Number` có thể cần wrapper.
4. **Khả năng tích hợp:** wrapper cung cấp các phương thức như `Integer.parseInt`, `Integer.compare`, `Long.parseLong`. Các phương thức này là thành viên của lớp wrapper nhưng vẫn có thể trả giá trị primitive.
5. **Tập hợp số lượng nhỏ hoặc không phải đường nóng:** ưu tiên API rõ ràng, đơn giản và đúng nghiệp vụ nếu overhead không đáng kể.

Khi cần biểu diễn kết quả tính toán “có hoặc không” mà không cần trả `null`, Java cũng có `OptionalInt`, `OptionalLong`, `OptionalDouble`. Chúng là đối tượng Optional nhưng giúp tránh boxing giá trị số vào `Integer`/`Long`/`Double` bên trong API đó.

**Không đánh đổi correctness lấy hiệu năng:** nếu “không có dữ liệu” khác với số 0, chuyển một `Integer nullable` thành `int` mà không thiết kế quy ước vắng mặt có thể làm sai nghiệp vụ hoặc gây `NullPointerException`.

## 11. Các cạm bẫy thường gặp

### 11.1. Null unboxing

```java
Integer amount = null;
// int x = amount; // Ném NullPointerException tại runtime.
```

Cần kiểm tra `null` trước khi unbox, sử dụng giá trị mặc định phù hợp hoặc định nghĩa trạng thái vắng mặt rõ ràng.

### 11.2. So sánh tham chiếu thay vì giá trị

```java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);

boolean sameValue = a.equals(b); // true
boolean sameIdentity = a == b;   // không dựa vào kết quả này
```

Tái sử dụng wrapper là tối ưu triển khai; `==` giữa hai tham chiếu không phải phép so sánh giá trị số. Tương tự, `Double` và `Float` có quy tắc `equals()`/`compareTo()` cần hiểu đối với `NaN` và dấu của số 0.

### 11.3. Nhầm mảng primitive với danh sách wrapper

```java
int[] values = {1, 2, 3};
List<int[]> oneArray = Arrays.asList(values);
// oneArray.size() == 1, không phải 3
```

`Arrays.asList(int[])` nhận cả mảng nguyên thủy như **một đối tượng mảng**. Nếu muốn `List<Integer>`, có thể dùng `Arrays.stream(values).boxed().toList()`.

### 11.4. Quên tràn số hoặc độ chính xác

Primitive nhanh và gọn không có nghĩa mọi phép toán đều chính xác:

- `int`/`long` có thể tràn khi cộng/nhân, kể cả bên trong `IntStream.sum()` hoặc `LongStream.sum()`.
- `double` có sai số dấu chấm động nhị phân. `Double` là wrapper của cùng kiểu `double`, **không phải** cách tăng độ chính xác số.
- Để tính toán thập phân tài chính chính xác, cân nhắc `BigDecimal` với quy tắc làm tròn cụ thể; nó là đối tượng với đặc điểm cấp phát và thuật toán khác primitive.

Không giải bài toán tràn hoặc sai số bằng cách thay `int` thành `Integer` hay `double` thành `Double`.

### 11.5. Giả định kích thước wrapper cố định

Không nên dùng một “kích thước đối tượng `Integer`” cố định để tính heap trên mọi JVM. Phần tử `int[]` là giá trị 4 byte nhưng đối tượng mảng còn header; `Integer[]` chứa các tham chiếu có kích thước cấu hình phụ thuộc JVM. Kích thước wrapper và cách cache/GC làm số liệu thực tế khác nhau.

### 11.6. Microbenchmark dễ bị sai

Không đo bằng cách thực hiện một lần `System.nanoTime()` quanh một loop rất nhỏ rồi kết luận. Những yếu tố làm lệch số đo gồm:

- JVM chưa warm-up để JIT tối ưu.
- GC chạy trong thời gian đo hoặc allocation biến mất do tối ưu.
- Code có thể bị loại bỏ vì kết quả không được sử dụng (*dead code elimination*).
- Dữ liệu test không phản ánh phân phối giá trị/cache/trường hợp `null` thực tế.
- Benchmark gộp cả thời gian tạo dữ liệu, boxing, tính toán và thu gom mà không phân biệt phần nào đang đo.
- Chế độ JVM, kiến trúc CPU, số luồng và kích thước dữ liệu khác nhau.

Với các câu hỏi latency/throughput, dùng **JMH** hoặc công cụ benchmark thích hợp, công bố môi trường JVM và các tham số, có warm-up/fork hợp lý. Để đo allocation hoặc retained memory, dùng profiler như Java Flight Recorder/JDK Mission Control hoặc công cụ heap profiler phù hợp. Không đưa ra con số tốc độ hay byte/object nếu chưa đo trong ngữ cảnh đó.

## 12. Tiêu chí chọn cấu trúc dữ liệu

| Nhu cầu | Lựa chọn nên cân nhắc | Lý do |
| --- | --- | --- |
| Một lượng lớn số nguyên, duyệt tuần tự/tính tổng | `int[]` / `long[]` | Dữ liệu phần tử trực tiếp, không cần wrapper trên mỗi phần tử |
| Một lượng lớn số thực | `double[]` | Lưu double trực tiếp; cần hiểu sai số dấu chấm động |
| Dữ liệu thay đổi số phần tử, API List | `ArrayList<Integer>` / `ArrayList<Long>` | Đáp ứng API collection; chấp nhận overhead tham chiếu/boxing |
| Cần `null` để phân biệt vắng mặt | `Integer` / `Long` | Có thể thiếu giá trị nhưng phải kiểm tra null |
| Pipeline chỉ làm toán trên mảng primitive | `IntStream` / `LongStream` / `DoubleStream` | Tránh boxing không cần thiết |
| API đòi `Stream<Integer>` hoặc `List<Integer>` | `boxed()` ở điểm biên | Chuyển kiểu khi thực sự cần đối tượng |
| Cần chính xác số thập phân theo quy tắc nghiệp vụ | `BigDecimal` | Correctness quan trọng hơn so sánh primitive-wrapper đơn thuần |
| Đường nóng nhạy allocation và cache | Đo nhiều phương án | Layout, JIT và GC quyết định hiệu quả thực tế |

Một quy trình quyết định hiệu quả: **xác định ngữ nghĩa** (độ chính xác, nullability, collection cần có), **chọn cấu trúc lưu số đơn giản**, rồi **đo profile trên dữ liệu thật** nếu hiệu năng là vấn đề. Đừng thay toàn bộ `List<Integer>` thành `int[]` nếu điều đó phá giao diện hoặc làm phức tạp cách xử lý mà chưa có bằng chứng chi phí.

## 13. Bài tự kiểm tra

**Câu 1.** Với một triệu giá trị `int`, tại sao `int[]` thường có locality tốt hơn `Integer[]`?  
**Đáp án:** dữ liệu `int` được lưu liên tiếp bên trong một mảng; `Integer[]` lưu tham chiếu và thường phải theo tham chiếu tới các đối tượng wrapper có thể nằm rải rác.

**Câu 2.** `Integer[] refs = new Integer[2]` chứa gì trước khi gán phần tử?  
**Đáp án:** hai tham chiếu `null`. `int[] values = new int[2]` tương ứng chứa hai giá trị 0.

**Câu 3.** Nếu source code có 1000 lần `Integer.valueOf(x)`, có chắc runtime đã cấp phát 1000 đối tượng `Integer` không?  
**Đáp án:** không. `valueOf` có cache và JVM có thể tối ưu hoặc tái sử dụng đối tượng tùy ngữ cảnh.

**Câu 4.** `new ArrayList<Integer>()` có tạo ngay một `Integer` cho mỗi vị trí capacity dự kiến không?  
**Đáp án:** không. Danh sách quản lý backing array của **tham chiếu**; các phần tử `Integer` là đối tượng được tham chiếu khi thêm giá trị tương ứng.

**Câu 5.** Vì sao `int[]` vẫn có overhead bộ nhớ?  
**Đáp án:** `int[]` là một đối tượng mảng có header và padding, dù mỗi ô phần tử `int` chiếm 4 byte.

**Câu 6.** `Integer sum = null; int value = sum;` gây gì?  
**Đáp án:** `NullPointerException` khi unboxing.

**Câu 7.** Nếu thay `double` thành `Double`, phép tính `0.1 + 0.2` có trở nên chính xác theo thập phân không?  
**Đáp án:** không. `Double` bọc giá trị `double` cùng ngữ nghĩa chấm động nhị phân, không tạo số thập phân chính xác tùy ý.

**Câu 8.** Khi nào dùng `List<Integer>` dù `int[]` thường gọn hơn?  
**Đáp án:** khi cần API Generic/Collection, danh sách thay đổi kích thước hoặc giao tiếp với các thành phần nhận đối tượng, và chi phí được chấp nhận theo yêu cầu.

## 14. Ghi nhớ

- Primitive là **giá trị trực tiếp**; wrapper là **đối tượng được tham chiếu** và có thể thêm overhead, dereference, boxing/unboxing, GC.
- `int[]` lưu trực tiếp các phần tử `int` trong cùng một mảng; `Integer[]`/`ArrayList<Integer>` lưu **tham chiếu tới wrapper**.
- JVM có cache và JIT tối ưu: **không phải mọi boxing đều tạo allocation mới**, cũng không có kích thước wrapper chính xác áp dụng mọi JVM.
- Chọn `int[]`, `long[]`, `double[]` và primitive streams khi xử lý khối lượng lớn số học; chọn wrapper khi cần ngữ nghĩa đối tượng/nullable/Generics.
- Đo hiệu năng với benchmark và profiler phù hợp trước khi kết luận về tốc độ hoặc lượng bộ nhớ trong một ứng dụng cụ thể.

Tài liệu tra cứu chính thức (Java SE 17): [Java Language Specification — Primitive Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html), [Boxing and Unboxing Conversions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7), [Integer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html), [Arrays](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html), [IntStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-002">Boxing trong collection, stream và vòng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Boxing trong collection, stream và vòng lặp Java

## 1. Hiểu đúng vấn đề hiệu năng

Java có hai nhóm kiểu số thường gặp:

- **Primitive:** `byte`, `short`, `int`, `long`, `float`, `double`. Một biến primitive giữ trực tiếp giá trị theo kiểu tương ứng; ví dụ `int count = 42;`.
- **Wrapper (kiểu tham chiếu):** `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`. Ví dụ `Integer count = 42;` lưu tham chiếu đến một đối tượng biểu diễn giá trị số.

**Boxing** là chuyển primitive thành wrapper, ví dụ `int` → `Integer`. **Unboxing** là chuyển wrapper thành primitive, ví dụ `Integer` → `int`. Java hỗ trợ **autoboxing/unboxing** để trình biên dịch thêm những chuyển đổi này tại nơi cú pháp cho phép:

~~~java
int primitive = 42;
Integer boxed = primitive; // boxing, tương đương Integer.valueOf(primitive)
int again = boxed;         // unboxing, tương đương boxed.intValue()
~~~

Cú pháp ngắn không làm hai kiểu trở thành một. Wrapper vẫn là đối tượng tham chiếu, có thể chứa `null` khi dùng biến tham chiếu, có chi phí lưu trữ/quản lý và có thể tạo thêm đối tượng. Đổi lại, wrapper dùng được trong API generic như `List<Integer>`, `Map<Integer, Long>` hoặc những nơi cần sự vắng mặt `null`.

Chủ đề **“Boxing trong collection, stream và vòng lặp”** tập trung vào cả **độ đúng** lẫn **hiệu năng**: boxing xuất hiện ở đâu, tác động của nó khi số lượng phần tử lớn, và lúc nào nên dùng `int[]`, `IntStream`, `LongStream` hoặc `DoubleStream`.

Không phải mọi boxing đều luôn cấp phát đối tượng mới. `Integer.valueOf` có cache cho các giá trị thông dụng; JIT có thể tối ưu một số allocation bằng phân tích escape. Vì vậy cách nói chính xác là **boxing có thể phát sinh công việc, tham chiếu và allocation/GC**, còn mức chi phí thực tế phải đo ở đúng workload.

## 2. Boxing/unboxing diễn ra như thế nào?

### 2.1. Chuyển đổi tự động trong phép gán và biểu thức

~~~java
Integer a = 10; // autobox: Integer.valueOf(10)
int b = a;      // unbox: a.intValue()

Integer sum = a + 5;
// unbox a thành int -> cộng 5 -> box kết quả thành Integer

int next = a + 1;
// unbox a, phép cộng primitive, gán vào int: không cần box kết quả
~~~

Các comment mô tả **ngữ nghĩa chuyển đổi**, không khẳng định máy ảo bắt buộc cấp phát một đối tượng riêng cho mỗi lần boxing. Thư viện `Integer.valueOf(int)` được Java bảo đảm cache ít nhất các giá trị từ `-128` đến `127`; giá trị nằm ngoài khoảng có thể được cache rộng hơn tùy JVM/cấu hình. Không nên suy luận tốc độ từ việc một ví dụ `10` nằm trong cache.

### 2.2. Unboxing của null ném NullPointerException

~~~java
Integer missing = null;
int n = missing; // NullPointerException tại chỗ unboxing
~~~

Không có giá trị `int` đại diện cho `null`. Phép toán số học cũng có thể ngầm unbox và lỗi:

~~~java
Integer count = null;
// int doubled = count * 2; // Ném NullPointerException
~~~

Ví dụ khi cần phân biệt “không có giá trị” với `0`, hãy quyết định chính sách trước khi chuyển kiểu: từ chối null, giữ wrapper/`OptionalInt` ở ranh giới, hoặc dùng giá trị mặc định có chủ đích. Không âm thầm biến mọi null thành 0 nếu hai trạng thái có ý nghĩa khác nhau.

### 2.3. So sánh wrapper theo giá trị, không theo identity

~~~java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);

System.out.println(a == b); // true: valueOf cache giá trị 127
System.out.println(a.equals(b)); // true: cùng giá trị số
~~~

`==` giữa hai biến kiểu `Integer` kiểm tra **cùng tham chiếu đối tượng**, không so giá trị số. Với các giá trị không được bảo đảm cache như `1000`, kết quả `==` không phải một hợp đồng để kiểm tra bằng nhau. Dùng `a.equals(b)` nếu a chắc chắn khác null hoặc `Objects.equals(a, b)` khi có thể null.

Cũng phải phân biệt khi một vế là `int`:

~~~java
Integer wrapped = 1000;
int primitive = 1000;
boolean same = wrapped == primitive; // true: wrapped được unbox trước khi so sánh
~~~

Khi `wrapped == null`, biểu thức `wrapped == primitive` có thể ném `NullPointerException` vì unboxing. Đừng dùng dấu `==` cho wrapper chỉ vì đã từng thấy ví dụ trả `true`.

## 3. Collection: List<Integer> không phải int[]

### 3.1. Generic collection cần kiểu tham chiếu

~~~java
import java.util.ArrayList;
import java.util.List;

List<Integer> numbers = new ArrayList<>();
numbers.add(1);  // 1 được box khi truyền tới add(Integer)
numbers.add(2);
int first = numbers.get(0); // lấy Integer rồi unbox
~~~

Cú pháp `List<int>` **không hợp lệ** trong Java 17: generic type argument phải là kiểu tham chiếu. Dù danh sách chứa toàn số, `List<Integer>` vẫn quản lý phần tử như các tham chiếu đến wrapper. `ArrayList<Integer>` có một mảng tham chiếu ở bên trong, khác với `int[]` là mảng giá trị primitive.

Điều này có tác động:

- Với `int[]`, mỗi vị trí chứa trực tiếp một `int`; không có `Integer` cho từng phần tử.
- Với `ArrayList<Integer>`, các vị trí chứa tham chiếu. Các đối tượng wrapper có thể mang chi phí bộ nhớ, tham chiếu và tạo áp lực GC, nhất là khi nhiều giá trị nằm ngoài cache.
- `List<Integer>` có thể chứa `null` (nếu loại danh sách cho phép), còn `int[]` không thể.
- `List<Integer>` cung cấp API collection thuận tiện: thay đổi kích thước, thao tác generic, tương tác với thư viện; `int[]` cố định kích thước và dùng API mảng.

Không nên khẳng định mọi `ArrayList<Integer>` luôn dùng đúng một số byte trên từng phần tử: kích thước reference/object còn phụ thuộc JVM, kích thước con trỏ, object alignment và tối ưu hóa runtime. Nhưng cấu trúc **tham chiếu vs primitive** là khác biệt căn bản.

### 3.2. List.of, Arrays.asList và chi phí boxing

~~~java
List<Integer> fixedSize = java.util.Arrays.asList(1, 2, 3);
List<Integer> immutable = List.of(1, 2, 3);
~~~

Các literal nguyên được boxing để trở thành `Integer` trong danh sách. `Arrays.asList` trả một danh sách kích thước cố định được hỗ trợ bởi mảng và **cho phép giá trị null**; `List.of` tạo danh sách không sửa đổi và **không cho null**. Cả hai đều là `List<Integer>`, không biến thành `int[]` chỉ vì phần tử là số.

Nếu cần xử lý mảng primitive:

~~~java
int[] values = {1, 2, 3};
int first = values[0]; // không boxing
~~~

Khi dữ liệu có hàng triệu số nguyên và chủ yếu tính tổng/thống kê, nên cân nhắc lưu/đọc dưới dạng primitive. Khi dữ liệu vốn từ API generic, việc chuyển sang mảng primitive cũng cần **unbox từng phần tử một lần**; không thể loại bỏ chi phí lưu trữ wrapper đã xuất hiện từ trước bằng một lời gọi stream.

### 3.3. Map với key/value là số

~~~java
Map<Integer, Integer> counts = new java.util.HashMap<>();

counts.merge(7, 1, Integer::sum);
counts.merge(7, 1, Integer::sum);
// counts.get(7) -> Integer có giá trị 2
~~~

Ở `Map<Integer, Integer>`, cả key và value là wrapper. Trong `Integer::sum`, hai giá trị `Integer` được unbox để cộng primitive và kết quả được box trở lại theo chữ ký `BiFunction` mà `merge` cần. Với nhiều lượt cập nhật tần suất trong vòng lặp nóng, việc quản lý wrapper có thể trở thành chi phí đáng kể.

Không vì thế tự thay mọi map bằng mảng: mảng `int[] counts` chỉ phù hợp khi miền khóa nhỏ, liên tục và ánh xạ trực tiếp được thành chỉ số. Với khóa thưa, miền lớn hoặc tính năng collection phức tạp, `Map` thường là lựa chọn đúng về thiết kế. Đánh giá bộ nhớ, tốc độ, tính đơn giản và yêu cầu API trước khi tối ưu.

## 4. Boxing trong vòng lặp

### 4.1. Vòng lặp primitive tránh boxing ở biến đếm

~~~java
int sum = 0;
for (int i = 0; i < 1_000; i++) {
    sum += i; // chỉ dùng int trong phần phép toán này
}
~~~

Ngược lại:

~~~java
Integer sum = 0;
for (Integer i = 0; i < 1_000; i++) {
    sum += i;
}
~~~

Vòng lặp thứ hai làm nhiều chuyển đổi ngầm: điều kiện `i < 1000` phải unbox, bước `i++` phải unbox/tăng/box, và `sum += i` phải unbox cả hai toán hạng rồi box lại kết quả cho biến `sum`. Những chuyển đổi đó không cần thiết nếu mục tiêu chỉ là tính toán trên số nguyên.

Một số đối tượng có thể được tối ưu hóa bởi JIT và giá trị nhỏ có thể lấy từ cache, nên không suy ra “mỗi vòng luôn có đúng ba object mới”. Nhưng mã primitive có ý nghĩa rõ hơn và tránh đặt trách nhiệm tối ưu toàn bộ phép toán lên compiler/runtime.

### 4.2. Enhanced for trên List<Integer>

~~~java
List<Integer> values = List.of(1, 2, 3, 4);

int total = 0;
for (Integer item : values) {
    total += item; // item được unbox để cộng
}
~~~

Phần tử được lấy từ danh sách là `Integer`; dù biến tích lũy `total` là `int`, giá trị `item` vẫn phải unbox khi cộng. Có thể viết `for (int item : values)` và để unboxing xuất hiện ngay khi gán biến lặp, nhưng **không xóa** phần tử wrapper trong list.

Trong enhanced for trên mảng primitive:

~~~java
int[] values = {1, 2, 3, 4};
int total = 0;

for (int item : values) {
    total += item; // không boxing/unboxing
}
~~~

Với `List<Integer>`, Java thường dùng cơ chế `Iterator<Integer>` cho enhanced for; overhead iterator và thao tác collection là yếu tố riêng ngoài boxing. Với `int[]`, enhanced for duyệt phần tử mảng trực tiếp. Đừng đồng nhất overhead vòng lặp với chỉ boxing: bounds check, branch, cấu trúc dữ liệu, CPU cache và tối ưu JIT đều có thể ảnh hưởng.

### 4.3. Null trong list gây lỗi khi cộng

~~~java
List<Integer> values = java.util.Arrays.asList(1, null, 3);

int total = 0;
for (Integer item : values) {
    total += item; // NPE khi đến phần tử null
}
~~~

Nếu null cần được bỏ qua, hãy nói rõ:

~~~java
int total = 0;
for (Integer item : values) {
    if (item != null) {
        total += item;
    }
}
~~~

Nếu null là dữ liệu không hợp lệ, nên kiểm tra và từ chối thay vì bỏ qua để tránh thay đổi tổng một cách âm thầm.

## 5. Stream<Integer> và IntStream: khác biệt rất quan trọng

### 5.1. Generic Stream chứa wrapper

~~~java
List<Integer> values = List.of(1, 2, 3, 4);

int total = values.stream()
        .mapToInt(Integer::intValue)
        .sum(); // 10
~~~

`values.stream()` có kiểu `Stream<Integer>`. Mỗi phần tử vẫn là `Integer` từ collection. `mapToInt(Integer::intValue)` **unbox** giá trị thành primitive stream `IntStream`. Sau bước này, các phép toán chuyên biệt như `sum()` hoạt động theo giao diện primitive và không cần boxing lại từng giá trị chỉ để cộng.

Vì sao không viết:

~~~java
Integer total = values.stream().reduce(0, Integer::sum);
~~~

Câu lệnh `reduce` trên `Stream<Integer>` hợp lệ, nhưng các lần cập nhật giá trị tích lũy thao tác qua `Integer` theo chữ ký generic; việc unbox/box trung gian có thể tạo thêm chi phí. `mapToInt(...).sum()` diễn đạt mục tiêu tính tổng số nguyên trực tiếp hơn và là lựa chọn phổ biến khi chỉ cần kết quả primitive.

### 5.2. Các primitive stream của Java 17

| Primitive stream | Phần tử | Phương thức tiêu biểu | Khi dùng |
| --- | --- | --- | --- |
| `IntStream` | `int` | `range`, `rangeClosed`, `of`, `sum`, `average` | Chỉ số, số đếm, phép toán nguyên 32 bit |
| `LongStream` | `long` | `range`, `of`, `sum`, `average` | Số nguyên 64 bit, miền rộng hơn |
| `DoubleStream` | `double` | `of`, `sum`, `average`, `map` | Phép tính số thực dấu phẩy động |

Java không có `IntegerStream` chuẩn riêng; `IntStream` là chuyên biệt primitive của `int`. Không có primitive stream chuẩn cho mọi primitive như `ByteStream` hay `ShortStream` trong Java 17.

Ví dụ sinh số nguyên trực tiếp không qua wrapper collection:

~~~java
import java.util.stream.IntStream;

int sumExclusive = IntStream.range(1, 4).sum(); // 1+2+3 = 6
int sumInclusive = IntStream.rangeClosed(1, 4).sum(); // 1+2+3+4 = 10
~~~

`range(startInclusive, endExclusive)` loại cận cuối; `rangeClosed(startInclusive, endInclusive)` gồm cận cuối. Đây là ranh giới thường gặp khiến thuật toán sai một phần tử, không phải sai do boxing.

### 5.3. Chuyển giữa primitive stream và boxed stream

~~~java
IntStream primitive = IntStream.rangeClosed(1, 4);
List<Integer> boxedList = primitive.boxed().toList();
// [1, 2, 3, 4]
~~~

`boxed()` cố ý biến từng phần tử `int` thành `Integer` để dùng API generic hoặc thu về `List<Integer>`. Đây là **ranh giới boxing** rõ ràng. `Stream.toList()` (Java 16+) trả về danh sách **không sửa đổi**; muốn danh sách thay đổi được, có thể tạo `new ArrayList<>(boxedList)`.

Ngược lại:

~~~java
List<Integer> boxed = List.of(1, 2, 3, 4);
int[] primitiveArray = boxed.stream()
        .mapToInt(Integer::intValue)
        .toArray();
~~~

Mỗi phần tử `Integer` cần unbox khi chuyển sang `int[]`. Sau đó, sử dụng `int[]` có thể giúp những vòng lặp/tính toán tiếp theo không duy trì wrapper theo phần tử. Nhưng copy/chuyển đổi cũng có chi phí; không nên làm nhiều lần trong cùng pipeline một cách vô ích.

### 5.4. Chọn đúng thời điểm đưa về primitive

Hai pipeline:

~~~java
List<Integer> values = List.of(1, 2, 3, 4);

int a = values.stream()
        .map(x -> x + 1)
        .mapToInt(Integer::intValue)
        .sum();

int b = values.stream()
        .mapToInt(Integer::intValue)
        .map(x -> x + 1)
        .sum();
~~~

Cả hai cho cùng kết quả 14 với danh sách trên. Tuy nhiên pipeline `a` dùng `Stream<Integer>.map`: lambda đọc `Integer`, tính số nguyên và tạo kết quả theo kiểu wrapper `Integer` của stream. Pipeline `b` chuyển một lần sang `IntStream` rồi dùng phép `map` trên primitive. Khi chuỗi xử lý dài và mỗi bước chỉ làm số học, **chuyển sang primitive sớm** thường làm rõ ý nghĩa và có thể giảm overhead.

Đừng chuyển sang `IntStream` sớm nếu thao tác còn cần biểu diễn `null` hoặc đối tượng phức tạp. Chính sách với null phải được quyết định rõ trước khi unbox.

### 5.5. Xử lý null trong Stream<Integer>

~~~java
List<Integer> values = java.util.Arrays.asList(1, null, 3);

// Nếu null được quy ước là bỏ qua:
int total = values.stream()
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue)
        .sum(); // 4
~~~

Nếu bỏ `filter`, `Integer::intValue` gặp null sẽ ném `NullPointerException`. Trường hợp null là lỗi nguồn dữ liệu, hãy xác thực thay vì lọc âm thầm. Với trường hợp “có hoặc không có một số nguyên” đơn lẻ, `OptionalInt` có thể giúp diễn đạt vắng mặt mà không dùng `Integer null`, nhưng cần cân nhắc API và ngữ nghĩa.

## 6. Tổng hợp và thống kê trên primitive stream

Các stream primitive có toán tử được thiết kế cho số:

~~~java
int[] values = {1, 2, 3, 4};

int total = java.util.Arrays.stream(values).sum(); // 10

java.util.IntSummaryStatistics stats =
        java.util.Arrays.stream(values).summaryStatistics();

// stats.getCount(): 4
// stats.getSum(): 10
// stats.getMin(): 1
// stats.getMax(): 4
// stats.getAverage(): 2.5
~~~

`IntSummaryStatistics` chứa count, sum, min, max và average. `getSum()` của thống kê dùng `long`, còn `IntStream.sum()` trả `int` nên tổng primitive vẫn **có thể tràn** nếu tổng vượt phạm vi 32 bit:

~~~java
int overflowed = IntStream.of(Integer.MAX_VALUE, 1).sum();
// -2147483648 do tràn số nguyên int

long safer = IntStream.of(Integer.MAX_VALUE, 1)
        .asLongStream()
        .sum();
// 2147483648L
~~~

Chuyển sang `LongStream` giúp tăng phạm vi nhưng không loại trừ tràn `long` trong mọi trường hợp. Nếu cần tổng vượt 64 bit hoặc số thập phân chính xác, dùng mô hình số học phù hợp như `BigInteger` hoặc `BigDecimal`; chúng là **kiểu đối tượng** và không có primitive stream riêng trong JDK. Mục tiêu hiệu năng không được đặt cao hơn yêu cầu chính xác.

`average()` trả `OptionalDouble` vì stream có thể rỗng; `sum()` trên stream rỗng trả 0. Phải xử lý trường hợp không có dữ liệu theo chính sách nghiệp vụ, thay vì mặc định average luôn có giá trị:

~~~java
double mean = IntStream.of(1, 2, 3, 4)
        .average()
        .orElseThrow(); // 2.5
~~~

Ví dụ là đoạn mã đặt trong phương thức với import `java.util.stream.IntStream`.

## 7. Chương trình Java 17+ chạy được

Tạo file `BoxingPerformanceDemo.java`:

~~~java
import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.IntStream;

public class BoxingPerformanceDemo {
    public static void main(String[] args) {
        List<Integer> values = Arrays.asList(1, 2, 3, 4);

        int loopSum = 0;
        for (Integer boxed : values) {
            loopSum += boxed; // unbox khi cộng
        }
        System.out.println("List<Integer> sum (loop): " + loopSum);

        System.out.println("Stream<Integer> sum: "
                + values.stream().mapToInt(Integer::intValue).sum());

        System.out.println("IntStream sum: "
                + IntStream.rangeClosed(1, 4).sum());

        System.out.println("Primitive -> boxed List: "
                + IntStream.rangeClosed(1, 4).boxed().toList());

        System.out.println("int[] sum: "
                + Arrays.stream(new int[] {1, 2, 3, 4}).sum());

        IntSummaryStatistics stats =
                IntStream.of(1, 2, 3, 4).summaryStatistics();
        System.out.println("stats: "
                + stats.getCount() + ", " + stats.getSum()
                + ", " + stats.getAverage());

        Integer small1 = Integer.valueOf(127);
        Integer small2 = Integer.valueOf(127);
        System.out.println("127 same reference: " + (small1 == small2));
        System.out.println("1000 equal value: "
                + Integer.valueOf(1000).equals(Integer.valueOf(1000)));

        Integer nullable = null;
        try {
            int n = nullable;
            System.out.println(n);
        } catch (NullPointerException ex) {
            System.out.println("unboxing null: NullPointerException");
        }

        List<Integer> withNull = Arrays.asList(1, null, 3);
        try {
            System.out.println(withNull.stream()
                    .mapToInt(Integer::intValue).sum());
        } catch (NullPointerException ex) {
            System.out.println("stream null: NullPointerException");
        }

        System.out.println("range(1,4) sum: "
                + IntStream.range(1, 4).sum());
        System.out.println("rangeClosed(1,4) sum: "
                + IntStream.rangeClosed(1, 4).sum());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BoxingPerformanceDemo.java
java BoxingPerformanceDemo
~~~

Kết quả:

~~~text
List<Integer> sum (loop): 10
Stream<Integer> sum: 10
IntStream sum: 10
Primitive -> boxed List: [1, 2, 3, 4]
int[] sum: 10
stats: 4, 10, 2.5
127 same reference: true
1000 equal value: true
unboxing null: NullPointerException
stream null: NullPointerException
range(1,4) sum: 6
rangeClosed(1,4) sum: 10
~~~

Kết quả chỉ minh họa **hành vi**, không đo tốc độ. `int[]`, `List<Integer>` và các dạng stream đều cho tổng 10 ở ví dụ nhỏ nhưng có mô hình lưu trữ/thực thi khác nhau. Hai dòng xử lý null cho thấy unboxing có thể thất bại ở cả vòng lặp và stream. Dòng cache chỉ cho thấy `Integer.valueOf(127)` dùng tham chiếu được chia sẻ theo hợp đồng cache; không dùng `==` để so giá trị wrapper nói chung.

## 8. Cân nhắc hiệu năng thực tế

### 8.1. Nguồn overhead khi boxing nhiều lần

Trong tập dữ liệu lớn, boxing có thể:

- Làm tăng các **tham chiếu** và metadata của wrapper so với mảng primitive.
- Tạo wrapper mới (đặc biệt giá trị ngoài cache), gây allocation và áp lực cho garbage collector.
- Tạo thêm chuyển đổi unbox/box ở vòng lặp, lambda hoặc các phương thức generic.
- Dẫn đến truy cập dữ liệu gián tiếp qua tham chiếu, ảnh hưởng đến locality của bộ nhớ và CPU cache.

Những chi phí này phụ thuộc việc wrapper có sống đủ lâu để phải nằm trên heap, cấu trúc collection, tối ưu escape analysis của JIT, thời gian warmup và đặc tính phần cứng. Không có con số “List<Integer> chậm hơn int[] X lần” áp dụng phổ quát.

### 8.2. Tối ưu đúng điểm

Khi dữ liệu nguyên là mảng `int[]` và nhiệm vụ là tính toán lặp đi lặp lại, dùng vòng lặp primitive hoặc `Arrays.stream(int[])` thường phù hợp. Nếu đang làm việc với `List<Integer>`, dùng `mapToInt` hoặc chuyển một lần sang `int[]` khi việc tính toán số học dài/ngốn tài nguyên đủ để bù chi phí chuyển đổi.

Ngược lại, việc thay tất cả `List<Integer>` thành `int[]` có thể làm mất tính linh hoạt của API, gây thêm công copy khi thêm/xóa phần tử, tăng độ phức tạp hoặc tạo bug. Nếu bottleneck nằm ở I/O, network hoặc database thì giảm boxing trong vài vòng lặp hiếm khi mang lại cải thiện đáng kể.

Với biến đếm và tích lũy ở vòng lặp nóng, chọn primitive khi không cần null và không cần generic wrapper. Ở ranh giới thư viện bắt buộc cần đối tượng, hãy boxing đúng nơi cần thiết.

### 8.3. Benchmark có ý nghĩa: JMH, không đo bằng một lần System.nanoTime

Một thử nghiệm hiệu năng đáng tin nên dùng công cụ như **JMH (Java Microbenchmark Harness)**, với các đặc điểm:

- Có warmup để JIT tối ưu; có nhiều iteration/fork theo cấu hình.
- Dùng `Blackhole` hoặc cách tiêu thụ kết quả để tránh phép tính bị compiler loại bỏ.
- So cùng workload/dữ liệu, kích thước đầu vào và mục tiêu tính toán; phân biệt **chi phí xây dựng collection** với **chi phí duyệt/tính tổng**.
- Đo throughput/latency cùng với allocation và GC khi phù hợp; đánh giá phân phối số, kích thước và tỷ lệ giá trị nằm trong cache của wrapper.
- Không dùng một kết quả nhỏ ở laptop làm bằng chứng áp dụng cho production.

Ví dụ ý tưởng benchmark: chuẩn bị trước một `int[]` và một `List<Integer>` tương đương trong bước setup, rồi đo riêng `for-each` và `stream/mapToInt/sum` trên mỗi cấu trúc. Nếu vừa tạo danh sách vừa đo phép tính, kết quả không còn chỉ phản ánh chi phí duyệt. Đây là hướng thiết kế benchmark, **không phải mã JMH hoàn chỉnh**.

### 8.4. Stream song song không tự động giải quyết boxing

`parallelStream()` có thêm chi phí tách tác vụ, phối hợp thread, gom kết quả và yêu cầu phép toán không có side effect không an toàn. Với danh sách nhỏ, thường không có lý do để sử dụng chỉ nhằm “tăng tốc”. Ngay cả pipeline song song, nếu dữ liệu ở `List<Integer>` thì đầu vào vẫn là wrapper; song song hóa không biến nó thành mảng primitive.

Nếu cần song song, kiểm tra dữ liệu đủ lớn, tính toán đủ tốn CPU, nguồn spliterator phù hợp và phép giảm có tính kết hợp đúng. Không dùng một biến `int sum` bên ngoài rồi tăng từ nhiều luồng bằng `forEach`: vừa tạo race condition vừa không tận dụng phép tổng hợp `sum` vốn có.

## 9. Những lỗi phổ biến

1. **Viết `List<int>`:** Java 17 generic cần kiểu tham chiếu, nên dùng `List<Integer>` hoặc dùng `int[]` khi phù hợp.
2. **Dùng `Integer` cho mọi biến đếm:** trong vòng lặp số học, biến primitive tránh các chuyển đổi không cần thiết.
3. **Nghĩ `List<Integer>` là mảng int bên trong:** danh sách quản lý reference tới wrapper.
4. **Tin boxing nào cũng tạo object mới:** cache `Integer.valueOf` và tối ưu JIT có thể thay đổi việc cấp phát thực tế; hãy đo trước khi kết luận.
5. **Tin boxing không có chi phí vì compiler tự làm:** cách biểu diễn tham chiếu, nullable và boxing ở pipeline vẫn có thể tác động bộ nhớ/GC/latency.
6. **So sánh wrapper bằng `==`:** so identity, không bảo đảm so giá trị; dùng `equals` hoặc `Objects.equals`.
7. **Unbox null không kiểm tra:** `Integer x = null; int y = x;` ném NPE.
8. **`Stream<Integer>.reduce` cho mọi phép tổng:** khi chỉ tính tổng số nguyên, xem xét `mapToInt(...).sum()` hoặc primitive stream từ đầu.
9. **Tưởng `mapToInt` xóa overhead lưu trữ của danh sách đã boxed:** nó unbox từng phần tử nhưng dữ liệu gốc vẫn là wrapper.
10. **Gọi `boxed()` rồi tiếp tục nhiều bước số học:** nếu không cần API generic, giữ `IntStream`, `LongStream` hoặc `DoubleStream` càng lâu càng phù hợp.
11. **Nhầm `range` với `rangeClosed`:** cận trên của `range` không được tính.
12. **Bỏ qua overflow khi chuyển sang primitive sum:** `IntStream.sum()` trả `int` và có thể tràn, ngay cả khi không có boxing.
13. **Lọc null âm thầm:** chỉ dùng `filter(Objects::nonNull)` nếu đây là quy tắc dữ liệu được chấp nhận.
14. **Benchmark bằng vài lần chạy ngắn:** JIT, warmup, GC và loại bỏ mã không sử dụng làm số đo thiếu tin cậy.

## 10. Tự kiểm tra

**Câu 1.** Tại sao `List<Integer>` không thể dùng `List<int>`?

**Đáp án:** Generic type parameter trong Java 17 là kiểu tham chiếu. `Integer` là wrapper cho `int`; cấu trúc primitive như `int[]` không phải một generic collection.

**Câu 2.** Trong vòng lặp `for (Integer i = 0; i < 1000; i++)` có những conversion gì?

**Đáp án:** Giá trị ban đầu 0 được boxing. Kiểm tra `i < 1000` unbox, `i++` unbox để tăng rồi box lại để gán cho biến `Integer`. Tùy mã trong body còn có các conversion khác.

**Câu 3.** `List.of(1,2,3).stream().mapToInt(Integer::intValue).sum()` được bao nhiêu và boxing còn tồn tại ở đâu?

**Đáp án:** Tổng bằng 6. Các phần tử list là `Integer`; `mapToInt` unbox sang primitive stream trước khi cộng.

**Câu 4.** Nếu `List<Integer>` có phần tử null, gọi `mapToInt(Integer::intValue)` có kết quả gì?

**Đáp án:** `NullPointerException` khi gặp null. Lọc bỏ hoặc từ chối null tùy hợp đồng dữ liệu.

**Câu 5.** `IntStream.range(1,4).sum()` và `IntStream.rangeClosed(1,4).sum()` khác nhau thế nào?

**Đáp án:** Lần lượt là 6 (1+2+3) và 10 (1+2+3+4).

**Câu 6.** Muốn thu `IntStream` thành `List<Integer>` thì dùng gì, và khi nào cần cân nhắc chi phí?

**Đáp án:** `stream.boxed().toList()`. Mỗi giá trị primitive được chuyển sang wrapper về mặt ngữ nghĩa; khi dữ liệu lớn hoặc pipeline chủ yếu tính số, nên kiểm tra xem có thật sự cần list generic không.

**Câu 7.** Dùng `IntStream` có ngăn tổng vượt `Integer.MAX_VALUE` bị tràn không?

**Đáp án:** Không. `IntStream.sum()` là phép cộng số nguyên `int`. Có thể chuyển qua `asLongStream().sum()` nếu long đủ phạm vi hoặc dùng số nguyên tùy ý khi cần.

## 11. Tài liệu Java chính thức

- [Autoboxing and Unboxing — Java Tutorials](https://docs.oracle.com/javase/tutorial/java/data/autoboxing.html): cơ chế chuyển primitive sang wrapper và ngược lại.
- [`Integer` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): `valueOf`, `intValue`, `equals` và cache.
- [`List` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): API collection generic.
- [`Stream` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): các toán tử `mapToInt`, `reduce`, `toList`.
- [`IntStream` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html): `range`, `rangeClosed`, `boxed`, `sum`, `summaryStatistics`.
- [`IntSummaryStatistics` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IntSummaryStatistics.html): thống kê trên số nguyên.

**Ghi nhớ:** Dùng `Integer` khi cần ngữ nghĩa đối tượng/generic hoặc nullable; dùng `int`, `int[]` và `IntStream` khi chủ yếu xử lý số nguyên trong các đoạn tính toán nhiều lần. Nhìn rõ ranh giới boxing/unboxing, kiểm tra null và overflow, rồi đo đúng workload trước khi đánh đổi thiết kế chỉ vì lo chi phí.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-003">Chi phí số học BigInteger theo độ dài toán hạng</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí số học BigInteger theo độ dài toán hạng

## 1. Vì sao cùng một phép toán nhưng thời gian chạy khác nhau?

`int` và `long` có độ rộng cố định (32 và 64 bit); còn `java.math.BigInteger` lưu số nguyên có độ chính xác tùy ý, miễn là đủ bộ nhớ và nằm trong giới hạn biểu diễn của thư viện. Hai lời gọi:

~~~java
BigInteger small = BigInteger.valueOf(123);
BigInteger large = BigInteger.ONE.shiftLeft(1_000_000);
BigInteger a = small.multiply(small);
BigInteger b = large.multiply(large);
~~~

cùng gọi `multiply` nhưng **lượng dữ liệu phải xử lý hoàn toàn khác nhau**. Số thứ nhất chỉ cần ít bit, số thứ hai có hơn một triệu bit; tích của số thứ hai có khoảng hai triệu bit. Ngoài phép nhân, JVM còn cần lưu và quản lý dữ liệu kết quả, vùng tạm, cấp phát và thu gom rác.

Do vậy “mỗi phép cộng/nhân/chia BigInteger tốn O(1)” là sai nếu đầu vào có độ dài thay đổi. Chi phí phụ thuộc vào **số bit hoặc số từ (limb) biểu diễn độ lớn toán hạng**, loại phép toán, hình dạng toán hạng (một bên ngắn hay hai bên dài), thuật toán được chọn và môi trường chạy.

**Mục tiêu:** biết ước lượng bậc tăng chi phí theo độ dài, phân biệt số bit với giá trị số học, tránh kết quả trung gian phình to, và đo bằng benchmark phù hợp trước khi tối ưu.

## 2. Độ dài toán hạng: bitLength và số limb

Với số nguyên dương `x`, `x.bitLength()` là số bit tối thiểu cần để biểu diễn độ lớn theo nhị phân. Ví dụ:

| Giá trị | Dạng nhị phân (rút gọn) | `bitLength()` |
|---|---|---:|
| `0` | `0` | 0 |
| `1` | `1` | 1 |
| `7` | `111` | 3 |
| `8` | `1000` | 4 |
| `255` | `11111111` | 8 |
| `256` | `100000000` | 9 |

Với **số âm**, `BigInteger.bitLength()` có định nghĩa phù hợp biểu diễn two's complement, không đơn giản luôn là số bit của trị tuyệt đối. Chẳng hạn `BigInteger.valueOf(-1).bitLength() == 0`, trong khi `abs().bitLength() == 1`. Khi phân tích **chi phí phụ thuộc độ lớn toán hạng**, dùng `x.abs().bitLength()` cho cả số âm và dương nếu đang muốn đo số bit của trị tuyệt đối.

Trong OpenJDK, độ lớn BigInteger được giữ bằng các nhóm từ máy (limb, hiện thường là các phần tử `int` 32 bit) cùng dấu. Với giá trị khác 0 và `n` bit độ lớn, số limb xấp xỉ:

~~~text
L = ceil(n / 32)
~~~

Đây là **mô hình giải thích chi phí trên triển khai OpenJDK**, không phải hợp đồng công khai yêu cầu mọi JVM phải lưu bằng mảng `int[]` 32 bit. Thuật toán có thể làm việc với dữ liệu tạm, mảng khác và các đường xử lý riêng cho trường hợp đặc biệt.

Điểm quan trọng là phân biệt hai thước đo:

- **Giá trị số học:** `10^1000` lớn hơn `10^100` theo phép so sánh.
- **Kích thước biểu diễn:** `10^1000` cần xấp xỉ `1000 × log2(10) ≈ 3322` bit. Thuật toán xử lý dữ liệu theo độ dài biểu diễn này.

Một số có một triệu chữ số thập phân không chỉ “lớn hơn” số có mười chữ số; nó đòi nhiều thao tác trên các từ máy và bộ nhớ hơn rất nhiều.

## 3. Ước lượng độ phức tạp theo số từ máy

Đặt `n`, `m` là số limb (hoặc tương đương số bit theo hệ số hằng) của hai toán hạng, với `n ≥ m ≥ 1`. Bảng sau mô tả **bậc chi phí điển hình**, không hứa hẹn thời gian chính xác của mọi phiên bản JDK:

| Phép toán | Quy mô chi phí thường gặp | Điều cần nhớ |
|---|---|---|
| `signum()`, một vài truy vấn metadata | Thường O(1) | Phụ thuộc dữ liệu đã lưu/được cache |
| `compareTo(b)`, `equals(b)` | Tối đa O(n) trong trường hợp kích thước tương đương | Có thể kết luận sớm nhờ dấu, độ dài hoặc limb khác đầu tiên |
| `add(b)`, `subtract(b)` | O(max(n, m)) trong trường hợp thông thường | Phải xử lý carry/borrow qua các limb |
| `negate()`, `abs()` | Có đường xử lý nhẹ; không suy ra luôn sao chép toàn bộ | BigInteger bất biến, tối ưu nội bộ tùy triển khai |
| `shiftLeft(k)`, `shiftRight(k)` | Phụ thuộc số limb của đầu vào/kết quả và lượng dịch | Dịch trái lớn có thể tạo số và vùng nhớ rất lớn |
| `and`, `or`, `xor`, `not` | Thường tỉ lệ độ dài dữ liệu cần xử lý | Ngữ nghĩa bitwise với dấu âm là two's complement |
| `multiply(b)` kiểu nhân từng chữ số | O(nm), nếu n≈m thì O(n²) | Thuật toán nhanh hơn có thể được chọn cho số lớn |
| `pow(exponent)` | Phụ thuộc exponent, độ dài kết quả và chi phí nhân | Kết quả có thể tăng rất nhanh |
| `divide`, `remainder` | Phụ thuộc cả số bị chia và số chia; thường nặng hơn phép cộng | Có nhiều thuật toán và trường hợp đặc biệt |
| `gcd`, `modInverse` | Nhiều bước chia/biến đổi tùy giá trị | Không cố định chỉ bởi một độ dài chung |
| `modPow` | Phụ thuộc độ dài modulus, exponent và số phép nhân/rút gọn môđun | Tránh tạo trực tiếp `base.pow(e)` cực lớn |

Đây là các quy luật để **xác định điểm nóng tiềm năng**, không phải bảng đo latency. Đầu vào có cấu trúc đặc biệt (0, 1, lũy thừa của 2, một toán hạng rất ngắn) và đường tối ưu của JDK có thể làm kết quả thực tế khác mô hình trung bình.

**Phân biệt n và m:** nhân một số 4096 bit với số 8 bit có bài toán khác nhân hai số đều 4096 bit. Không đánh đồng cả hai là “phép nhân số 4096 bit” mà quên độ dài toán hạng thứ hai.

## 4. Cộng và trừ: gần tuyến tính theo độ dài

Cộng hai số lớn có thể được hình dung như cộng theo từng limb, mang theo bit nhớ:

~~~text
A = [a0, a1, a2, ..., an-1]
B = [b0, b1, b2, ..., bm-1]
=> cộng các limb tương ứng và truyền carry
~~~

Nếu hai số có khoảng `n` limb, trường hợp thông thường tốn công việc tuyến tính theo `n`. Các tình huống có carry chạy dài (ví dụ cộng 1 vào số toàn bit 1) cần chạm tới nhiều limb.

Ví dụ:

~~~java
BigInteger allOnes = BigInteger.ONE.shiftLeft(256)
        .subtract(BigInteger.ONE);
BigInteger nextPowerOfTwo = allOnes.add(BigInteger.ONE);

System.out.println(allOnes.bitLength());      // 256
System.out.println(nextPowerOfTwo.bitLength()); // 257
~~~

Dù chỉ cộng **1**, kết quả thay đổi 256 bit thấp và tạo thêm một bit ở đầu. Không thể suy từ toán hạng `BigInteger.ONE` rất ngắn rằng phép cộng luôn có chi phí hằng số, hoặc số kết quả không tăng độ dài.

Với các phép `add/subtract` lặp hàng triệu lần trên số ngày càng lớn, mỗi bước có thể đắt hơn bước trước, dù vòng lặp không đổi câu lệnh Java. Nếu chỉ cần đếm giá trị nằm trong `long`, `long` thường đơn giản và nhẹ hơn; chỉ dùng `BigInteger` khi phạm vi số thực sự đòi hỏi.

## 5. Nhân: từ O(n²) đến Karatsuba và Toom-Cook

Nhân theo cách phổ thông từng limb của số thứ nhất với từng limb của số thứ hai có chi phí **O(nm)**; khi hai độ dài xấp xỉ nhau là **O(n²)**. Nếu tăng gấp đôi số limb của cả hai toán hạng, mô hình nhân phổ thông có thể tăng số phép xử lý cặp limb khoảng bốn lần. Đây là **ước lượng bậc độ phức tạp**, không khẳng định runtime thực tế luôn đúng 4×.

Các thuật toán cho số lớn có thể giảm số phép nhân con:

- **Karatsuba:** tách đôi toán hạng, dùng ba tích con thay vì bốn, độ phức tạp với toán hạng cân bằng xấp xỉ **O(n^log₂3) ≈ O(n^1.585)**.
- **Toom-Cook ba phần:** có thể đạt khoảng **O(n^log₃5) ≈ O(n^1.465)** cho cách phân chia phù hợp.
- **Nhân phổ thông:** có ít overhead, thường có lợi cho số nhỏ.

OpenJDK 17 có các đường xử lý nhân phổ thông, Karatsuba và Toom-Cook, cùng ngưỡng thực nghiệm để lựa chọn. **Ngưỡng và cách lựa chọn là chi tiết triển khai**, có thể thay đổi theo phiên bản và distribution JDK; không thiết kế tính đúng của chương trình dựa vào một ngưỡng hard-code hoặc đảm bảo 4×/2× khi tăng số bit.

Với hai số dương khác 0 có độ dài bit `a` và `b`, tích có bit length thuộc khoảng:

~~~text
a + b - 1 <= product.bitLength() <= a + b
~~~

Ví dụ nhân số 256 bit với số 257 bit thường cần tới 513 bit; đó là **độ dài kết quả**, không phải số phép toán CPU. Ngay cả khi thuật toán nhân nhanh hơn, bộ nhớ để chứa kết quả vẫn tăng theo độ dài của tích.

### Bình phương không nhất thiết tốn như nhân hai toán hạng độc lập

`x.multiply(x)` có thể được tối ưu bởi thuật toán bình phương chuyên biệt trong OpenJDK. Vì nhiều tích con trùng nhau khi hai toán hạng giống nhau, mã thư viện có thể giảm overhead. Tuy nhiên, không viết một giả định về tỷ lệ tốc độ không được đo trên JDK triển khai thực tế. Kết quả `x.pow(2)` và `x.multiply(x)` giống nhau về mặt số học, nhưng đường code, trường hợp đặc biệt và chi phí có thể khác.

## 6. Chia, remainder và gcd: không thể đánh giá chỉ bằng số bit số bị chia

Chia một `BigInteger` lớn cho số nhỏ khác chia cho một số gần bằng độ lớn của nó. Thuật toán chia theo limb có thể phải ước lượng thương, nhân trừ và sửa phần dư nhiều lần. OpenJDK 17 có các đường xử lý chia kiểu truyền thống và Burnikel–Ziegler cho trường hợp toán hạng đủ dài.

Với:

~~~java
BigInteger quotient = dividend.divide(divisor);
BigInteger remainder = dividend.remainder(divisor);
~~~

hai phép gọi độc lập có thể gây công việc trùng lặp. Khi cần **cả thương và dư**, dùng:

~~~java
BigInteger[] qr = dividend.divideAndRemainder(divisor);
BigInteger quotient = qr[0];
BigInteger remainder = qr[1];
~~~

Đây là đoạn dùng bên trong phương thức, với `dividend`/`divisor` là `BigInteger` và divisor khác 0. `divideAndRemainder` cho phép thư viện tính chung hai kết quả, có thể giảm công việc so với hai lời gọi độc lập; không suy ra luôn nhanh hơn bao nhiêu phần trăm.

Một số điều cần nhớ:

1. Chia cho **0** ném `ArithmeticException`; tính hiệu năng không có ý nghĩa khi tiền điều kiện không được thỏa.
2. `divide` chia số nguyên, làm tròn thương **về 0**; `remainder` giữ dấu tương ứng với số bị chia (khác `mod`, vốn yêu cầu môđun dương và cho dư không âm).
3. Nếu divisor có độ dài lớn hơn dividend về trị tuyệt đối thì thương nguyên bằng 0; có thể là đường xử lý rẻ.
4. `gcd` thường liên quan nhiều bước biến đổi theo các giá trị trung gian. Số bit đầu vào quan trọng, nhưng cấu trúc số và số vòng thuật toán cũng ảnh hưởng runtime.

Không thay `divide(x)` bằng `shiftRight(k)` trừ khi **bạn đã chứng minh** x là lũy thừa 2 và kiểm tra ngữ nghĩa số âm: `shiftRight` mang hành vi dịch số có dấu, trong khi `divide` làm tròn về 0. Ví dụ `-3 >> 1` là `-2`, còn `-3 / 2` là `-1`.

## 7. Lũy thừa làm độ dài kết quả bùng nổ

Nếu `a` có độ lớn lớn hơn 1, phép `a.pow(e)` có thể làm số bit kết quả tăng theo **e × log₂|a|**. Với trường hợp `a = 2^k`, kết quả là `2^(ke)`, có bit length `ke + 1`. Một số phép nhân có thể sử dụng lũy thừa bằng bình phương liên tiếp, nhưng **không thể bỏ qua kích thước kết quả khổng lồ** nếu mục đích là giữ toàn bộ lũy thừa.

Ví dụ:

~~~java
BigInteger two = BigInteger.TWO;
BigInteger huge = two.pow(1_000_000);
System.out.println(huge.bitLength()); // 1000001
~~~

Số có khoảng một triệu bit này yêu cầu chừng 125 KB chỉ để chứa các bit độ lớn tối thiểu, chưa kể mảng, object, kết quả tạm và việc cấp phát/copy. Đây là ước lượng dung lượng bit, **không phải kích thước chính xác trên heap**.

Với bài toán chỉ cần **phần dư** của `base^exponent mod modulus`, tránh xây dựng toàn bộ `base.pow(exponent)` rồi `mod(modulus)`. Dùng:

~~~java
BigInteger result = base.modPow(exponent, modulus);
~~~

`modPow` tính lũy thừa môđun qua các bước tối ưu, trong đó giá trị trung gian được giảm theo modulus thay vì nhất thiết phải chứa toàn bộ số mũ khổng lồ. Chi phí vẫn phụ thuộc lớn vào số bit của modulus, số bit exponent và cơ chế nhân/rút gọn môđun. Không nói `modPow` là O(1) chỉ vì **độ dài kết quả** bị chặn bởi modulus.

Ví dụ toán học với exponent dương và modulus dương:

~~~java
BigInteger base = BigInteger.TWO;
BigInteger modulus = BigInteger.valueOf(97);
BigInteger e = BigInteger.valueOf(1_000);

boolean same = base.modPow(e, modulus)
        .equals(base.pow(1_000).mod(modulus));
System.out.println(same); // true
~~~

Đoạn này chỉ để đối chiếu kết quả với exponent **nhỏ đủ để minh họa**. Trong chương trình thật nếu exponent cực lớn, việc tạo `pow` rồi lấy `mod` có thể tiêu tốn thời gian/bộ nhớ nghiêm trọng.

## 8. Chương trình Java 17+ quan sát kích thước kết quả

Lưu thành **`BigIntegerLengthDemo.java`**:

~~~java
import java.math.BigInteger;

public class BigIntegerLengthDemo {
    private static int magnitudeBits(BigInteger x) {
        return x.abs().bitLength();
    }

    public static void main(String[] args) {
        BigInteger one = BigInteger.ONE;
        BigInteger a = one.shiftLeft(256).subtract(one);
        BigInteger b = one.shiftLeft(257).subtract(one);

        System.out.println("a bits = " + magnitudeBits(a));
        System.out.println("b bits = " + magnitudeBits(b));
        System.out.println("a+a bits = " + magnitudeBits(a.add(a)));
        System.out.println("a*b bits = " + magnitudeBits(a.multiply(b)));
        System.out.println("a^3 bits = " + magnitudeBits(a.pow(3)));

        BigInteger modulus = BigInteger.valueOf(97);
        BigInteger base = BigInteger.valueOf(2);
        BigInteger exponent = BigInteger.valueOf(1_000);
        boolean sameModularResult = base.modPow(exponent, modulus)
                .equals(base.pow(1_000).mod(modulus));
        System.out.println("modPow matches = " + sameModularResult);

        System.out.println("negative -1 magnitude bits = "
                + magnitudeBits(BigInteger.valueOf(-1)));
        System.out.println("negative -1 bitLength = "
                + BigInteger.valueOf(-1).bitLength());
    }
}
~~~

Chạy:

~~~bash
javac BigIntegerLengthDemo.java
java BigIntegerLengthDemo
~~~

Stdout đã kiểm chứng:

~~~text
a bits = 256
b bits = 257
a+a bits = 257
a*b bits = 513
a^3 bits = 768
modPow matches = true
negative -1 magnitude bits = 1
negative -1 bitLength = 0
~~~

Vì `a = 2^256 - 1` và `b = 2^257 - 1`, tích có bit length 513. Bình phương/cubing số gần lũy thừa 2 cũng cho kết quả có số bit tăng gần tuyến tính theo số mũ. Chương trình **không đo thời gian**; nó minh họa kích thước dữ liệu mà các thuật toán số học phải xử lý.

## 9. Immutable BigInteger, allocation và memory pressure

`BigInteger` là bất biến. Phần lớn phép toán sinh giá trị kết quả mới; JVM có thể sử dụng lại hằng, trả lại cùng tham chiếu trong một số nhánh hoặc tối ưu cấp phát, nên đừng khẳng định **mỗi lời gọi chắc chắn cấp phát đúng một đối tượng mới**. Dù vậy, vòng lặp tạo giá trị lớn liên tục có thể dẫn đến:

- Nhiều vùng dữ liệu kết quả và mảng limb tạm.
- Sao chép/ghi các limb dù phép toán nhìn như một dòng Java.
- Allocation rate tăng, heap chiếm nhiều chỗ hơn và tần suất GC tăng.
- Áp lực cache/băng thông bộ nhớ khi toán hạng dài.
- Sự chênh lệch thời gian do JIT compiler, CPU, GC và phân phối kích thước đầu vào.

Ví dụ cộng dồn:

~~~java
BigInteger sum = BigInteger.ZERO;
for (int i = 1; i <= 1_000_000; i++) {
    sum = sum.add(BigInteger.valueOf(i));
}
~~~

Đây là đoạn minh họa trong phương thức (import `java.math.BigInteger`). `sum` được cập nhật qua nhiều giá trị bất biến, có thể tạo nhiều object tạm. Nếu tổng bảo đảm vừa `long`, cộng bằng `long` rồi chuyển thành BigInteger ở biên có thể giảm chi phí; **chỉ được làm vậy khi chứng minh không overflow** và yêu cầu domain cho phép.

Ví dụ khi chỉ đếm số lượng request (không cần số nguyên vô hạn), `long` hoặc primitive/atomic tương ứng có thể phù hợp hơn. Khi tính giai thừa, tổ hợp hoặc mật mã nơi kích thước số vượt `long`, BigInteger có giá trị vì giữ kết quả chính xác, nhưng phải kiểm soát số bước và kích thước trung gian.

### Tránh chuyển đổi chuỗi trong vòng lặp nóng

~~~java
// Không nên parse lại cùng một hằng thập phân trong hot loop:
BigInteger a = new BigInteger("123456789012345678901234567890");

// Khi đối số vừa long, biểu diễn đơn giản hơn:
BigInteger b = BigInteger.valueOf(42L);
~~~

Chuyển từ chuỗi thập phân dài sang số nhị phân/limb có chi phí xử lý dữ liệu. Chuyển ngược qua `toString()` cũng tốn thời gian và có thể tạo chuỗi cực lớn. Đừng dùng `toString().length()` để “đo độ lớn” bên trong vòng lặp; dùng `abs().bitLength()` khi cần thước đo nhị phân, và tránh gọi các thao tác đo không cần thiết trong đoạn code tính toán hiệu năng.

Không để `System.out.println(hugeBigInteger)` trở thành một phần của benchmark nhân/chia: lúc đó thời gian format và ghi console có thể áp đảo phép toán mà bạn định đo.

## 10. Chi phí biểu diễn kết quả và giữ trung gian ở độ dài hợp lý

Một số quy tắc gần đúng khi toán hạng dương:

| Phép toán | Độ dài kết quả |
|---|---|
| `a.add(b)` | Tối đa `max(bits(a), bits(b)) + 1` |
| `a.subtract(b)` | Trị tuyệt đối kết quả có thể nhỏ hoặc gần bằng toán hạng lớn nhất |
| `a.multiply(b)` với a,b khác 0 | Từ `bits(a) + bits(b) - 1` đến `bits(a) + bits(b)` |
| `a.shiftLeft(k)` với a dương khác 0, k≥0 | `bits(a) + k` |
| `a.mod(m)` với m dương | Kết quả từ 0 đến `m - 1`, nên bit length không vượt `bits(m)` |
| `a.pow(e)` với e không âm | Có thể có độ dài tăng tỉ lệ với e |

Các công thức theo `bits()` ở bảng dùng **độ dài bit của trị tuyệt đối**. Trường hợp 0, 1, số âm và các tối ưu đặc biệt cần xét riêng. `BigInteger` có giới hạn triển khai và bộ nhớ thực tế; cụm “arbitrary precision” không có nghĩa cho phép cấp phát vô hạn.

Khi xử lý nhiều bước, cần nghĩ tới **giá trị trung gian lớn nhất**, không chỉ giá trị đầu vào/đầu ra. Ví dụ để tính `(a × b) mod m`, nếu `a` và `b` đều gần độ dài modulus, một tích trung gian có thể dài gần gấp đôi modulus trước khi giảm. Các thuật toán modular chuyên dụng có thể sử dụng cách biểu diễn và giảm trung gian hiệu quả hơn cho nhiều phép lặp.

Khi số bit tăng theo từng vòng lặp, tổng chi phí của cả quá trình là tổng chi phí từng bước:

~~~text
TotalCost = Cost(size_1) + Cost(size_2) + ... + Cost(size_k)
~~~

Không chỉ lấy số vòng lặp nhân với một hằng số nếu kích thước toán hạng thay đổi. Ví dụ giai thừa `n!` tăng số chữ số theo n; dùng cùng một phép nhân nhưng độ dài một toán hạng tiếp tục tăng theo n.

## 11. Đo hiệu năng thực tế: JMH và những bẫy benchmark

Phân tích O(n) và O(n²) là mô hình bậc tăng, **không thay thế đo trên tải thực tế**. JVM có JIT, tối ưu nội tuyến, escape analysis, cấp phát theo vùng, GC và các đường xử lý nội bộ dựa vào kích thước. Để so sánh đáng tin, nên dùng **Java Microbenchmark Harness (JMH)** với fork, warmup, measurement và `Blackhole` đúng cách.

Một thiết kế benchmark tốt:

1. **Giữ cả hai toán hạng:** ví dụ 64/64, 256/256, 1024/1024, 4096/4096 bit; thêm 4096/64 để so sánh trường hợp một toán hạng ngắn.
2. **Kiểm soát nội dung:** các số cùng bit length nhưng có cấu trúc rất khác (toàn bit 1, số ngẫu nhiên, lũy thừa 2) có thể chạy theo đường xử lý khác nhau. Không lấy một số 0 hoặc 1 để thay cho số lớn ngẫu nhiên.
3. **Tách từng phép toán:** benchmark add, multiply, divide, modPow ở các nhóm riêng; không để dựng BigInteger, parse chuỗi, random, format số hay ghi console trong phần đo nếu mục tiêu là phép toán.
4. **Không cho JIT loại bỏ kết quả:** tiêu thụ kết quả qua `Blackhole` hoặc sink được thiết kế phù hợp; không viết vòng lặp với kết quả không dùng.
5. **Tránh phụ thuộc vòng trước:** nếu mỗi iteration cập nhật kết quả ngày càng dài, bạn đang đo một **thuật toán tăng kích thước**, không so chi phí một phép nhân của các kích thước cố định. Cả hai loại đều có ích nhưng trả lời câu hỏi khác nhau.
6. **Ghi môi trường:** phiên bản JDK, CPU, heap, GC, JMH version, fork/iteration, số phép đo, phân phối input, nguồn dữ liệu và ngưỡng thuật toán.
7. **Báo kết quả có biến thiên:** ns/op hoặc ops/s cùng sai số/độ phân tán; không dùng một lần `System.nanoTime()` để cam kết “phép nhân số 4096 bit mất đúng X ns”.

### Ví dụ JMH minh họa ý tưởng

Đây là **đoạn benchmark cần JMH dependency và JMH annotation processor**, không thể biên dịch chỉ với JDK nếu chưa thêm JMH vào dự án. Lưu trong file `BigIntegerMultiplyBenchmark.java` thuộc mã benchmark của dự án:

~~~java
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BigIntegerMultiplyBenchmark {
    @Param({"128", "512", "2048", "4096"})
    public int bits;

    private BigInteger left;
    private BigInteger right;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(12345L);
        left = new BigInteger(bits, random).setBit(bits - 1);
        right = new BigInteger(bits, random).setBit(bits - 1);
    }

    @Benchmark
    public void multiply(Blackhole blackhole) {
        blackhole.consume(left.multiply(right));
    }
}
~~~

`@Param` khiến JMH chạy các cấu hình kích thước riêng, `@Setup` loại việc sinh dữ liệu khỏi đoạn đo, và `Blackhole` tiêu thụ kết quả. Với kích thước khác nhau, throughput có thể thay đổi không trơn vì JDK đổi chiến lược thuật toán, tối ưu CPU/JVM và hoạt động GC. Đây chỉ là một benchmark minh họa **nhân hai toán hạng có cùng bit length**, chưa bao phủ chia, modular exponentiation hay phép nhân lệch kích thước. Không so sánh JDK/CPU bằng một số đo lấy từ máy khác.

Để benchmark JMH thật sự chạy, dự án cần cấu hình thư viện `org.openjdk.jmh:jmh-core` và annotation processor `jmh-generator-annprocess`, sau đó chạy bằng launcher JMH phù hợp build tool. Không khẳng định lớp trên chạy độc lập bằng `javac` chỉ với JDK tiêu chuẩn.

## 12. Chiến lược tối ưu theo đúng nguồn chi phí

**Giữ phạm vi giá trị thực sự cần thiết.** Nếu yêu cầu chỉ tới `long`, dùng primitive khi có kiểm tra overflow. Nếu nghiệp vụ có thể vượt `long`, dùng BigInteger; đừng đổi sang `double` để tăng tốc khi cần số nguyên chính xác vì `double` không biểu diễn chính xác mọi số nguyên lớn.

**Ưu tiên thuật toán thay vì mẹo cục bộ.** Tránh triển khai nhân BigInteger lặp quá nhiều khi có biểu thức toán học tốt hơn: ví dụ thuật toán lũy thừa theo bình phương, divide-and-conquer, tính tổ hợp có khử ước chung, giảm sớm qua modulus khi đúng về mặt toán học. Tuy nhiên, giữ đúng semantics phép toán (đặc biệt số âm, chia nguyên, dư và overflow nếu chuyển kiểu).

**Tái sử dụng hằng và tránh parse thừa.** Dùng `BigInteger.ZERO`, `ONE`, `TWO` và `valueOf(long)`; xây dữ liệu lớn một lần ngoài hot loop nếu nó không đổi. Tránh chuyển qua chuỗi khi thao tác toán học có API trực tiếp.

**Đọc đúng chi phí của số trung gian.** Bài toán đầu ra ngắn không đồng nghĩa đường tính tự động rẻ; `base.pow(hugeExponent).mod(modulus)` có thể tạo kết quả khổng lồ trước khi rút gọn, trong khi `modPow` là cách thiết kế đúng cho lũy thừa môđun.

**Tách ứng dụng đồng thời và thuật toán số học.** Nếu nhiều thread cùng cập nhật một `BigInteger` qua biến chia sẻ, tính immutable của đối tượng không khiến thao tác `current = current.add(ONE)` trở thành atomic. Cần đồng bộ hoặc thao tác atomic với cập nhật điều kiện phù hợp; chi phí tranh chấp lock/GC và việc tăng bit length đều ảnh hưởng tổng throughput.

**Chú ý an toàn tài nguyên.** Với dữ liệu bên ngoài, giới hạn độ dài input/bit, số vòng lặp, exponent và các phép toán có thể làm kết quả tăng cực nhanh. Với dịch trái số bit rất lớn, lũy thừa lớn hoặc chuỗi decimal hàng triệu ký tự, nguy cơ dùng quá nhiều bộ nhớ/time không phải do kết quả sai. Kiểm tra bounds trước khi cấp phát và dùng timeout/quota ở cấp hệ thống nếu cần.

**Đối với mật mã:** BigInteger phục vụ số học chính xác nhưng thời gian của nhiều phép toán phụ thuộc kích thước và nội dung toán hạng; không coi các phép toán thông thường là constant-time. Khi xử lý bí mật, chọn thư viện/triển khai mật mã đã được xem xét về timing side channel và các yêu cầu an toàn khác; hiệu năng đơn thuần không đủ làm tiêu chí.

## 13. Lỗi phổ biến

- **Coi BigInteger như int có độ dài vô hạn nhưng chi phí O(1):** toán hạng lớn làm tăng số limb và cấp phát.
- **So thời gian với input 0/1 rồi suy ra cho mọi số dài:** giá trị đặc biệt có đường tối ưu khác.
- **Cho rằng nhân luôn O(n²):** đó là nhân phổ thông; JDK có thể đổi sang thuật toán nhanh hơn ở kích thước lớn.
- **Khẳng định Karatsuba bắt đầu ở đúng một bit length cố định:** ngưỡng nằm trong triển khai, tính theo limb và các điều kiện khác, không phải API contract.
- **Chỉ xét một toán hạng:** phép nhân/chia phụ thuộc hình dạng cả hai toán hạng.
- **Tính `pow` cực lớn chỉ để lấy modulo:** thiếu kiểm soát kết quả trung gian; xem `modPow`.
- **Gọi `divide` và `remainder` riêng khi cần cả hai:** có thể trùng công việc; xem `divideAndRemainder`.
- **Benchmark bao gồm `new BigInteger(String)`, `toString` hoặc `println`:** đo nhầm chi phí chuyển đổi và I/O.
- **Dùng `System.nanoTime` một lần để kết luận phức tạp thuật toán:** JIT, GC, noise và thay đổi đường code có thể chi phối mẫu.
- **Quên bộ nhớ:** kết quả và vùng tạm có thể rất lớn dù một API chỉ trả một biến.
- **Lấy `bitLength()` trực tiếp trên số âm để đo bit trị tuyệt đối:** ví dụ -1 cho bitLength 0; dùng `abs().bitLength()` khi đó là thước đo mong muốn.
- **Thay chia số âm cho lũy thừa 2 bằng shiftRight:** khác hướng làm tròn.

## 14. Bài tập và gợi ý

**Bài 1.** Hai số dương đều 1024 bit, rồi tăng mỗi số lên 2048 bit. Nếu chỉ xét phép nhân kiểu phổ thông O(n²) với hai toán hạng cân bằng, lượng công việc tương đối thay đổi khoảng bao nhiêu? Vì sao không được biến tỷ lệ đó thành cam kết đo thực tế?

**Bài 2.** Vì sao phép cộng `(2^256 - 1) + 1` có thể cần xử lý nhiều limb, dù toán hạng thứ hai rất ngắn? Bit length kết quả là bao nhiêu?

**Bài 3.** Muốn tính `2^100000000 mod 97`, nên dùng `pow().mod()` hay `modPow()`? Giải thích theo kích thước dữ liệu trung gian.

**Bài 4.** Với phép nhân một toán hạng 4096 bit và một toán hạng 32 bit, vì sao không nên so trực tiếp với nhân hai số 4096 bit rồi kết luận mọi phép nhân “4096 bit” đều giống nhau?

**Bài 5.** `BigInteger.valueOf(-1).bitLength()` bằng bao nhiêu? Nếu muốn ước lượng độ dài của độ lớn toán hạng, viết biểu thức nào?

**Bài 6.** Thiết kế benchmark JMH đo thời gian phép nhân các cặp 128×128, 1024×1024 và 4096×64 bit. Những thứ gì cần loại khỏi phần đo để đảm bảo đo gần đúng riêng phép nhân?

**Gợi ý đáp án:**

1. Mô hình O(n²) dự đoán khoảng **4 lần** khi cả hai độ dài tăng gấp đôi; triển khai có thể dùng Karatsuba/Toom-Cook, cache và GC khác nhau, nên cần benchmark để biết tỷ lệ thực tế.
2. Carry có thể lan qua chuỗi limb; kết quả bằng `2^256`, có **257 bit**.
3. Dùng `modPow` để tránh tạo và giữ số `2^100000000` đầy đủ khi chỉ cần số dư.
4. Nhân phổ thông có chi phí O(nm), nên cả hai toán hạng ảnh hưởng; đường tối ưu thực tế cũng có thể khác.
5. `-1.bitLength()` theo ý nghĩa đối tượng là **0**; dùng `x.abs().bitLength()` để đo magnitude (trả 1 cho -1).
6. Sinh toán hạng trước trong `@Setup`, tiêu thụ kết quả qua `Blackhole`, có warmup/fork, không parse/format/log trong phần đo; ghi rõ JDK/CPU/config.

## Tài liệu tham khảo

- Java SE 17, BigInteger API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html
- OpenJDK 17, mã nguồn BigInteger (các đường nhân/chia và ngưỡng lựa chọn): https://github.com/openjdk/jdk17/blob/master/src/java.base/share/classes/java/math/BigInteger.java
- OpenJDK JMH project: https://github.com/openjdk/jmh

**Ghi nhớ:** độ dài của **cả hai toán hạng và kết quả trung gian** quyết định rất nhiều tới chi phí BigInteger. Ước lượng bằng số bit/limb để chọn thuật toán, và đo bằng benchmark chuẩn trên tải thực để ra quyết định tối ưu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-004">Chi phí BigDecimal theo precision và scale</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí BigDecimal theo precision và scale

## 1. Vì sao hiệu năng BigDecimal phụ thuộc dữ liệu?

`BigDecimal` trong Java dùng để tính toán giá trị thập phân với độ chính xác tùy ý. Nó cho phép cộng, trừ, nhân, chia và làm tròn theo các quy tắc kiểm soát được, nhưng thường cần nhiều bộ nhớ và công việc hơn kiểu nguyên thủy `long` hoặc `double`. Hai phép `add` nhìn giống nhau trong source code có thể chênh lệch chi phí rất lớn: một phép cộng hai số vài chữ số và một phép cộng giữa `1E+1000000` với `1` đều gọi `add`, nhưng kết quả chính xác của trường hợp thứ hai cần khoảng một triệu chữ số nguyên.

Để phân tích hiệu năng cần hiểu **precision**, **scale**, kích thước số nguyên chưa đặt dấu thập phân, chênh lệch scale, kết quả trung gian, chế độ làm tròn và việc format chuỗi. Bài này dùng ví dụ Java 17+, chỉ ra các ranh giới đúng/sai khi tối ưu và cách đo thực tế.

## 2. Precision, scale và unscaled value

BigDecimal biểu diễn giá trị theo công thức:

\[
\text{value} = \text{unscaledValue}\times10^{-\text{scale}}.
\]

- **Unscaled value** là số nguyên mang các chữ số của giá trị. Ví dụ, `123.4500` có unscaled value `1234500`.
- **Scale** là số nguyên `int`: `123.4500` có scale 4; `1E+6` có scale -6; `1000` có scale 0. Scale có thể âm.
- **Precision** là số chữ số thập phân của giá trị tuyệt đối unscaled value, không tính các số 0 ở đầu. Với zero, precision là 1. `123.4500` có precision 7, `1E+6` có precision 1.
- **MathContext precision** là giới hạn số chữ số có nghĩa sau phép toán khi chỉ định `MathContext`; nó **không phải** số chữ số sau dấu thập phân.

Precision = 1 không có nghĩa giá trị nhỏ: `1E+1000000` có precision 1 nhưng trị số cực lớn. Scale lớn không có nghĩa precision lớn: `1E-1000000` có precision 1 và scale 1000000.

### Ví dụ 1: đọc metadata của BigDecimal

Lưu thành `BigDecimalMetaDemo.java`:

~~~java
import java.math.BigDecimal;

public class BigDecimalMetaDemo {
    private static void inspect(String source) {
        BigDecimal x = new BigDecimal(source);
        System.out.println(source + ": p=" + x.precision()
                + ", s=" + x.scale());
    }

    public static void main(String[] args) {
        inspect("123.4500");
        inspect("1000.00");
        inspect("1E+6");
        inspect("0.000001");
        inspect("0.00");
    }
}
~~~

Biên dịch/chạy bằng JDK 17:

~~~bash
javac BigDecimalMetaDemo.java
java BigDecimalMetaDemo
~~~

Kết quả:

~~~text
123.4500: p=7, s=4
1000.00: p=6, s=2
1E+6: p=1, s=-6
0.000001: p=1, s=6
0.00: p=1, s=2
~~~

Các số có **giá trị số học bằng nhau** vẫn có thể khác biểu diễn. `2.0` và `2.00` có `compareTo(...) == 0` nhưng `equals(...) == false` vì scale khác nhau; việc chuẩn hóa scale có thể tác động đến khóa bản đồ và dữ liệu hiển thị.

## 3. Biểu diễn nội bộ và chi phí của số nguyên lớn

BigDecimal lưu unscaled value và scale. Trong JDK 17, những giá trị unscaled nhỏ có thể đi qua biểu diễn gọn tương tự `long`; những giá trị lớn hơn có thể cần cấu trúc số nguyên lớn như `BigInteger`. Chi tiết nội bộ và ngưỡng tối ưu hóa có thể thay đổi theo phiên bản JDK, nên đây là lời giải thích cơ chế phổ biến, **không phải hợp đồng thời gian chạy của API**.

Về nguyên tắc, các phép số học trên số nguyên gồm hàng trăm hoặc hàng nghìn chữ số cần nhiều công việc hơn phép toán trên số nguyên nhỏ. Nhân/chia số lớn còn có thể dùng các thuật toán khác nhau theo kích cỡ. BigDecimal là **immutable**: các kết quả logic mới, biểu diễn trung gian và số nguyên lớn có thể gây cấp phát bộ nhớ/GC. Implementation có thể tái sử dụng một số đối tượng hoặc tối ưu đường nhanh trong vài trường hợp, nhưng chương trình không được giả định mọi phép cộng là “không cấp phát”.

Vì vậy, chi phí thực phải đo trên **độ dài chữ số, scale và phép toán cụ thể**, không đoán từ số lượng object BigDecimal hoặc một phép tính tổng quát.

## 4. Cộng và trừ: căn chỉnh scale

Với phép cộng chính xác, các toán hạng cần được đưa về một đơn vị thập phân tương thích:

\[
1.20 + 0.003 = 1.203.
\]

Khi cộng/trừ chính xác, scale kết quả thường là scale lớn hơn trong hai toán hạng. Chênh lệch scale lớn có thể buộc JDK xử lý lũy thừa 10 rất lớn và số nguyên kết quả dài.

Ví dụ:

- `1E+6`: precision 1, scale -6.
- `1`: precision 1, scale 0.
- Tổng **chính xác**: `1000001`, precision 7, scale 0.

Nếu thay `1E+6` bằng `1E+1000000`, kết quả chính xác cộng `1` cần khoảng một triệu chữ số nguyên. Dù hai toán hạng có precision nhỏ, **chênh lệch scale quyết định lượng dữ liệu phải được biểu diễn**.

### Ví dụ 2: so sánh cộng chính xác và có MathContext

Lưu thành `BigDecimalAddDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalAddDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1E+6");
        BigDecimal b = BigDecimal.ONE;

        BigDecimal exact = a.add(b);
        BigDecimal rounded = a.add(b,
                new MathContext(4, RoundingMode.HALF_UP));

        System.out.println("a scale = " + a.scale());
        System.out.println("exact = " + exact.toPlainString());
        System.out.println("exact p = " + exact.precision());
        System.out.println("rounded = " + rounded.toPlainString());
        System.out.println("rounded p = " + rounded.precision());
    }
}
~~~

~~~bash
javac BigDecimalAddDemo.java
java BigDecimalAddDemo
~~~

Kết quả:

~~~text
a scale = -6
exact = 1000001
exact p = 7
rounded = 1000000
rounded p = 4
~~~

`MathContext(4, HALF_UP)` giới hạn bốn chữ số có nghĩa và làm mất đóng góp `1` trong kết quả. Đây là **thay đổi ngữ nghĩa**, không đơn thuần tăng tốc. Một số thuật toán có thể tiết kiệm công việc nhờ precision hữu hạn, nhưng không được khẳng định mọi phép toán có MathContext đều nhanh hơn.

## 5. Nhân: độ dài chữ số và scale có thể tăng

Với `a.multiply(b)` chính xác, scale kết quả thông thường bằng tổng scale của hai toán hạng, nếu còn trong phạm vi hợp lệ. Độ dài unscaled value của tích thường tăng theo số chữ số của cả hai toán hạng. Chi phí nhân số có vài chữ số khác đáng kể nhân hai số có hàng nghìn chữ số.

### Ví dụ 3: scale sản phẩm giữ chữ số 0

Lưu thành `BigDecimalMultiplyDemo.java`:

~~~java
import java.math.BigDecimal;

public class BigDecimalMultiplyDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("12.3400");
        BigDecimal b = new BigDecimal("0.010");
        BigDecimal product = a.multiply(b);

        System.out.println("a scale = " + a.scale());
        System.out.println("b scale = " + b.scale());
        System.out.println("product = " + product.toPlainString());
        System.out.println("product scale = " + product.scale());
        System.out.println("product p = " + product.precision());
    }
}
~~~

~~~bash
javac BigDecimalMultiplyDemo.java
java BigDecimalMultiplyDemo
~~~

Kết quả:

~~~text
a scale = 4
b scale = 3
product = 0.1234000
product scale = 7
product p = 7
~~~

Số 0 cuối của `0.1234000` không làm thay đổi giá trị, nhưng được giữ trong biểu diễn scale 7 của phép nhân chính xác. Lặp phép nhân nhiều lần có thể tạo precision và scale trung gian ngày càng lớn. Chọn làm tròn chỉ khi chính sách độ chính xác của bài toán cho phép.

## 6. Chia: precision, scale và rounding mode tác động trực tiếp

`divide(divisor)` không tự chọn mức làm tròn. Nếu kết quả thập phân vô hạn như `1/3`, phương thức ném `ArithmeticException`. Các overload nhận tham số cho phép kiểm soát:

- `divide(divisor, scale, roundingMode)`: số chữ số **sau dấu thập phân** và cách làm tròn.
- `divide(divisor, MathContext)`: số chữ số **có nghĩa** và cách làm tròn.
- `divide(divisor)`: chỉ chấp nhận kết quả thập phân chính xác hữu hạn.

Yêu cầu nhiều chữ số thương thường tăng công việc, bộ nhớ và chi phí làm tròn. Nhưng thời gian thực còn phụ thuộc kích thước và cấu trúc của divisor; không đưa ra một hệ số tốc độ cố định khi chưa benchmark.

### Ví dụ 4: so sánh hai hợp đồng chia

Lưu thành `BigDecimalDivideDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalDivideDemo {
    public static void main(String[] args) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");

        try {
            one.divide(three);
            System.out.println("unexpected exact result");
        } catch (ArithmeticException ex) {
            System.out.println("exact division rejected");
        }

        System.out.println("scale 2 = " + one.divide(
                three, 2, RoundingMode.HALF_UP));
        System.out.println("precision 6 = " + one.divide(
                three, new MathContext(6, RoundingMode.HALF_UP)));
    }
}
~~~

~~~bash
javac BigDecimalDivideDemo.java
java BigDecimalDivideDemo
~~~

Kết quả:

~~~text
exact division rejected
scale 2 = 0.33
precision 6 = 0.333333
~~~

Giới hạn precision một cách mù quáng có thể làm sai kết quả, còn đặt precision cực lớn “cho an toàn” có thể lãng phí tài nguyên. Cần xác định chính sách làm tròn theo nghiệp vụ và đo trên dữ liệu thực.

## 7. setScale không phải lúc nào cũng rẻ

`setScale(newScale)` có thể:

- **Tăng scale:** nhân unscaled value với lũy thừa 10 để giữ giá trị, làm số chữ số unscaled tăng.
- **Giảm scale:** chia unscaled value, có thể cần rounding nếu phần bị bỏ khác 0.
- **Giữ scale:** thường ít công việc hơn, nhưng API không cam kết kết quả luôn không cấp phát.
- **Dùng scale âm:** ví dụ `1E+3` có scale -3 và giá trị 1000.

### Ví dụ 5: tăng scale làm precision tăng

Lưu thành `ScaleExpansionDemo.java`:

~~~java
import java.math.BigDecimal;

public class ScaleExpansionDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("12.3");
        BigDecimal b = a.setScale(5);

        System.out.println("original = " + a);
        System.out.println("expanded = " + b);
        System.out.println("before p = " + a.precision());
        System.out.println("after p = " + b.precision());
        System.out.println("after s = " + b.scale());
    }
}
~~~

~~~bash
javac ScaleExpansionDemo.java
java ScaleExpansionDemo
~~~

Kết quả:

~~~text
original = 12.3
expanded = 12.30000
before p = 3
after p = 7
after s = 5
~~~

Giá trị số học vẫn là 12.3, nhưng biểu diễn unscaled dài hơn. Scale tăng hàng trăm nghìn bậc có thể gây cấp phát lớn; không cho phép đầu vào tùy ý quyết định scale không giới hạn trong ứng dụng cần kiểm soát tài nguyên.

## 8. stripTrailingZeros và so sánh

`stripTrailingZeros()` loại các số 0 cuối trong unscaled value và điều chỉnh scale. Nó có thể làm biểu diễn ngắn hơn, nhưng cũng có thể đổi scale sang số âm. Ví dụ `1000.00` trở thành dạng `1E+3`, scale -3.

`BigDecimal.equals` và `compareTo` khác nhau:

- `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false` vì scale khác nhau.
- `compareTo` trên hai số này trả `0` vì giá trị số học bằng nhau.

Không xem `stripTrailingZeros` như tối ưu miễn phí trong mọi vòng lặp; phép chuẩn hóa cũng có thể xử lý unscaled value và làm thay đổi hợp đồng biểu diễn. Sự khác biệt equals/compareTo còn tác động tới khóa `HashMap` và tập `TreeSet`. Với dữ liệu lớn, chi phí so sánh có thể phụ thuộc độ dài và scale; không giả định mọi phép compareTo là O(1).

### Ví dụ 6: chuẩn hóa có thể tạo scale âm

Lưu thành `NormalizeDemo.java`:

~~~java
import java.math.BigDecimal;

public class NormalizeDemo {
    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("1000.00");
        BigDecimal normalized = original.stripTrailingZeros();
        BigDecimal x = new BigDecimal("2.0");
        BigDecimal y = new BigDecimal("2.00");

        System.out.println("before s = " + original.scale());
        System.out.println("after s = " + normalized.scale());
        System.out.println("normalized = " + normalized);
        System.out.println("compare = " + x.compareTo(y));
        System.out.println("equals = " + x.equals(y));
    }
}
~~~

~~~bash
javac NormalizeDemo.java
java NormalizeDemo
~~~

Kết quả:

~~~text
before s = 2
after s = -3
normalized = 1E+3
compare = 0
equals = false
~~~

Chỉ loại zero cuối khi hợp đồng của bài toán cho phép thay đổi scale. Với dữ liệu tiền tệ bắt buộc hai chữ số thập phân, phải giữ quy ước scale thích hợp thay vì chuẩn hóa vô điều kiện.

## 9. Chuỗi có thể rất dài dù precision là 1

`toString()` có thể biểu diễn giá trị bằng ký pháp khoa học, còn `toPlainString()` xuất số thập phân không dùng số mũ. Do đó `new BigDecimal("1E+1000000")` có precision 1 nhưng `toPlainString()` tạo chuỗi gồm chữ số 1 cùng khoảng một triệu số 0.

### Ví dụ 7: phí tạo chuỗi

Lưu thành `StringRepresentationDemo.java`:

~~~java
import java.math.BigDecimal;

public class StringRepresentationDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("1E+6");

        System.out.println("precision = " + x.precision());
        System.out.println("scale = " + x.scale());
        System.out.println("toString = " + x.toString());
        System.out.println("plain = " + x.toPlainString());
        System.out.println("plain length = " + x.toPlainString().length());
    }
}
~~~

~~~bash
javac StringRepresentationDemo.java
java StringRepresentationDemo
~~~

Kết quả:

~~~text
precision = 1
scale = -6
toString = 1E+6
plain = 1000000
plain length = 7
~~~

Việc tạo chuỗi dài tốn bộ nhớ theo độ dài chuỗi đầu ra ngay cả khi giá trị BigDecimal ngắn gọn. Không gọi `toPlainString` liên tục trong vòng lặp hoặc log số có scale cực đoan mà không giới hạn độ dài và nhu cầu hiển thị.

## 10. MathContext và trade-off độ chính xác

`MathContext` chứa precision và rounding mode. Nó **không trực tiếp quy định scale**. Ví dụ `new MathContext(4, HALF_UP)` chỉ giữ bốn chữ số có nghĩa; `setScale(4, HALF_UP)` giữ bốn chữ số sau dấu thập phân.

Giới hạn precision có thể làm một số phép toán xử lý kết quả ngắn hơn, nhưng cũng có thể tạo bước làm tròn. Chi phí phụ thuộc thuật toán và JDK; **không bảo đảm MathContext nhỏ luôn nhanh hơn**. Quan trọng hơn, precision thấp có thể loại đóng góp của số nhỏ hoặc làm thay đổi kết quả.

Cộng chính xác trước rồi làm tròn sau cũng có thể khác làm tròn ở **mỗi bước cộng**, nên chính sách này phải được thiết kế từ yêu cầu độ chính xác. Với tiền, thuế và sổ sách, không được thay đổi thời điểm làm tròn chỉ vì thấy nhanh hơn.

## 11. Cấp phát, bộ nhớ và thiết kế dữ liệu

Vì BigDecimal bất biến, một vòng lặp tích lũy như đoạn minh họa sau tạo kết quả logic sau mỗi phép cộng:

~~~java
BigDecimal total = BigDecimal.ZERO;
for (BigDecimal item : values) {
    total = total.add(item);
}
~~~

Đây chỉ là đoạn minh họa; `values` cần được khai báo khi dùng trong chương trình độc lập. Trong quá trình chạy, implementation có thể tối ưu hoặc tái sử dụng một số đối tượng, nhưng phép tính trên unscaled value lớn vẫn có thể tạo nhiều dữ liệu trung gian.

Hướng cải thiện hợp lý:

- Dùng `BigDecimal.ZERO`, `ONE`, `TEN` và `BigDecimal.valueOf(long)` khi thích hợp; tránh parse chuỗi trong mỗi bước nếu dữ liệu đã là số decimal chính xác.
- Với literal thập phân, dùng `new BigDecimal("0.1")` nếu cần giá trị đúng 0.1; `new BigDecimal(0.1)` giữ giá trị nhị phân gần đúng của `double`.
- Hạn chế gọi `setScale`, `stripTrailingZeros` và format chuỗi trong vòng lặp nóng nếu chỉ cần biểu diễn sau cùng.
- Với nghiệp vụ scale cố định và miền số cho phép, cân nhắc lưu bằng đơn vị nhỏ nhất trong `long`; bắt buộc kiểm soát overflow và rounding. Không áp dụng mẹo này cho bài toán scale/precision tùy ý.
- Cấu hình bộ nhớ và đánh giá lượng cấp phát/GC bằng profiler hoặc benchmark khi có bằng chứng đó là điểm nghẽn.

## 12. Benchmark đúng để tránh kết luận sai

Dùng một phép `System.nanoTime()` cho một lời gọi BigDecimal thường thiếu độ tin cậy vì JVM warmup/JIT, GC, lịch CPU và tối ưu loại bỏ phép tính không dùng kết quả.

**JMH (Java Microbenchmark Harness)** là công cụ phù hợp cho microbenchmark, cần thêm dependency vào dự án thử nghiệm riêng; nó không tích hợp sẵn trong JDK. Quy trình đo nên:

1. Chuẩn bị toán hạng trước vùng đo nếu mục tiêu chỉ đo `add` hoặc `multiply`.
2. Warmup đủ, chạy nhiều lần và báo cáo phân bố, không chỉ một trị số.
3. Tiêu thụ kết quả bằng `Blackhole` hoặc cơ chế JMH tương đương để tránh dead-code elimination.
4. Đo cả thời gian/op và allocation/op; quan sát GC nếu liên quan.
5. Thử dữ liệu có unscaled ngắn/dài, scale bằng/khác xa, precision 16/100/1000, kết quả format ngắn/dài.
6. Xác minh giá trị và scale đầu ra trước khi so sánh tốc độ; tối ưu làm sai số học không phải phép tối ưu tương đương.

Một ma trận benchmark hữu ích:

| Tình huống | Ví dụ | Điều cần đo |
| --- | --- | --- |
| Cộng cùng scale | `1234.56 + 78.90` | Chi phí cơ bản |
| Cộng scale rất lệch | `1E+1000 + 1` | Căn chỉnh và cấp phát |
| Nhân số dài | Hai số hàng nghìn chữ số | Độ dài tích và CPU |
| Chia precision lớn | `1/7` precision 16, 100, 1000 | Chi phí tính thương/làm tròn |
| Format số mũ | `1E+100000` | `toString` vs `toPlainString` |

Bảng là **đề xuất thử nghiệm**, không phải kết quả đo. Không tuyên bố “BigDecimal chậm hơn double N lần” khi chưa so đúng công việc và tập dữ liệu.

## 13. Đầu vào cực đoan và an toàn tài nguyên

Scale là kiểu `int`, nhưng các phép toán với scale cực lớn vẫn có thể đòi cấp phát hàng triệu chữ số. Ví dụ:

- Cộng `1E+1000000 + 1` tạo kết quả decimal chính xác khổng lồ.
- `setScale` tăng nhiều bậc có thể tạo unscaled value dài.
- `multiply` nhiều lần có thể làm precision/scale tăng.
- `divide` với precision rất cao phải xử lý nhiều chữ số thương.
- `toPlainString` có thể tạo chuỗi lớn hơn nhiều so với biểu diễn khoa học.
- Phép toán có thể phát sinh `ArithmeticException` với scale không hợp lệ, phép chia vô hạn không được làm tròn hoặc chế độ `UNNECESSARY`; tải bộ nhớ quá lớn còn có thể gây `OutOfMemoryError`.

Nên kiểm soát độ dài chuỗi nhập, precision, scale, số phần tử và kích thước kết quả trước khi chạy phép tính từ input bên ngoài. Giới hạn phải xuất phát từ ứng dụng và tài nguyên cho phép.

## 14. Sai lầm phổ biến

1. **Nhầm precision với scale:** `1E+6` có precision 1 và scale -6.
2. **Nghĩ precision thấp nghĩa là mọi phép toán nhẹ:** chênh lệch scale vẫn có thể tạo kết quả dài.
3. **Nghĩ setScale chỉ đổi metadata:** nó có thể nhân/chia unscaled value với lũy thừa 10.
4. **Dùng MathContext precision thấp chỉ để tăng tốc:** có thể làm mất dữ liệu.
5. **Dùng divide không quy định rounding cho 1/3:** sẽ ném `ArithmeticException`.
6. **Tưởng stripTrailingZeros giữ nguyên scale:** có thể tạo scale âm.
7. **Nhầm equals và compareTo:** equals xét scale, compareTo xét giá trị.
8. **Gọi toPlainString với số mũ cực lớn:** có thể tạo chuỗi rất dài.
9. **Chuyển sang double vì nhanh:** có thể mất tính chính xác thập phân.
10. **Dùng long đơn vị nhỏ nhất mà không kiểm tra overflow:** làm sai miền giá trị.
11. **Benchmark không warmup hoặc đo cả parse:** không phân lập được chi phí phép tính.
12. **Bỏ qua precision/scale do người dùng cung cấp:** có thể gây quá tải bộ nhớ/CPU.

## 15. Tự kiểm tra

**Câu 1.** `new BigDecimal("123.4500")` có precision và scale bằng bao nhiêu?

**Đáp án:** Precision 7, scale 4 vì unscaled value là 1234500.

**Câu 2.** Tại sao `1E+1000000 + 1` tốn tài nguyên dù toán hạng thứ nhất có precision 1?

**Đáp án:** Phép cộng chính xác cần căn scale và biểu diễn kết quả có khoảng một triệu chữ số nguyên.

**Câu 3.** `new BigDecimal("12.3").setScale(5)` tạo giá trị nào?

**Đáp án:** `12.30000`, precision tăng từ 3 lên 7, scale trở thành 5; giá trị số học không đổi.

**Câu 4.** `new BigDecimal("1000.00").stripTrailingZeros()` có thể có scale nào?

**Đáp án:** -3, biểu diễn `1E+3`.

**Câu 5.** Vì sao MathContext precision thấp không phải tối ưu miễn phí?

**Đáp án:** Nó làm tròn, có thể loại những chữ số cần thiết và thay đổi kết quả nghiệp vụ.

**Câu 6.** Muốn đo hiệu năng riêng của `add`, cần chú ý gì?

**Đáp án:** Chuẩn bị operand ngoài phần benchmark, warmup, tiêu thụ kết quả, đo nhiều mẫu với precision/scale khác nhau và đối chiếu độ đúng.

## 16. Kết luận

- Precision đo số chữ số có nghĩa của unscaled value; scale xác định vị trí dấu thập phân và có thể âm.
- Chi phí BigDecimal phụ thuộc **precision, scale, chênh lệch scale, phép toán, intermediate allocation và định dạng đầu ra**.
- Cộng scale lệch lớn và `toPlainString` của số mũ lớn có thể gây tải rất cao dù precision ban đầu nhỏ.
- Chia/nhân với precision lớn tốn thêm công việc, nhưng làm tròn sớm chỉ hợp lệ khi nghiệp vụ cho phép.
- Đo trên dữ liệu mục tiêu bằng công cụ như JMH và kiểm tra độ đúng trước khi tối ưu.

Tài liệu tham khảo: Java SE 17 API `BigDecimal`, `BigInteger`, `MathContext`, `RoundingMode`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-005">Tránh tăng precision hoặc scale không cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tăng precision hoặc scale không cần thiết với BigDecimal

## 1. Vấn đề và mục tiêu học tập

`BigDecimal` là lựa chọn quan trọng khi Java phải xử lý số thập phân chính xác, chẳng hạn tiền tệ, tỷ lệ hoặc giá trị đo có yêu cầu làm tròn cụ thể. Nhưng “chính xác” không đồng nghĩa với việc luôn giữ **càng nhiều chữ số càng tốt**. Một pipeline cứ kéo dài phần thập phân, nhân nhiều giá trị có scale lớn, hoặc chuyển sang chuỗi thập phân quá dài có thể tăng chi phí bộ nhớ, CPU, lưu trữ và truyền dữ liệu.

Cần phân biệt hai điều:

- **Không đánh mất độ chính xác cần thiết:** không làm tròn tùy tiện, không âm thầm chuyển qua `double`, không xóa scale nếu scale mang ý nghĩa nghiệp vụ.
- **Không tạo độ lớn biểu diễn không cần thiết:** không tự thêm hàng nghìn chữ số 0, không để scale tăng qua nhiều phép nhân mà không có kế hoạch, không yêu cầu precision hoặc cách hiển thị quá mức.

Mục tiêu của bài học là hiểu quan hệ giữa `precision`, `scale` và kích thước biểu diễn; nhận ra những phép toán có thể làm tăng chúng; thiết kế giới hạn hợp lý tại ranh giới tính toán, lưu trữ và hiển thị; kiểm chứng tính đúng đắn trước khi tối ưu.

Điều kiện tiên quyết: **hiệu năng không được đánh đổi âm thầm bằng kết quả sai**. Một phép chuẩn hóa, làm tròn hay cắt precision chỉ hợp lệ khi hợp đồng dữ liệu cho phép.

## 2. Precision và scale không phải một thứ

Một giá trị `BigDecimal` được mô hình hóa như sau:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

Trong đó:

- `unscaledValue()` trả số nguyên không giới hạn kiểu `BigInteger` biểu diễn các chữ số.
- `scale()` chỉ vị trí dấu thập phân theo cơ số 10; có thể dương, bằng 0 hoặc âm.
- `precision()` là số chữ số thập phân trong unscaled value của biểu diễn hiện tại, không tính dấu. Số 0 có precision bằng 1.

Ví dụ:

| Giá trị được tạo | `unscaledValue` | `precision` | `scale` | Giá trị số học |
| --- | ---: | ---: | ---: | --- |
| `new BigDecimal("1.2300")` | `12300` | `5` | `4` | `1.23` |
| `new BigDecimal("1.23")` | `123` | `3` | `2` | `1.23` |
| `new BigDecimal("1000")` | `1000` | `4` | `0` | `1000` |
| `new BigDecimal("1E+3")` | `1` | `1` | `-3` | `1000` |
| `new BigDecimal("1E-100000")` | `1` | `1` | `100000` | `10^-100000` |

Hai số có cùng giá trị theo `compareTo` vẫn có thể khác `equals` vì `equals` xét thêm `scale`. Chẳng hạn `1.2300` và `1.23` có cùng giá trị nhưng khác scale.

Ví dụ cuối trong bảng là một bẫy quan trọng: `1E-100000` có **precision bằng 1** nhưng **scale bằng 100000**. Scale lớn không tự chứng minh rằng đối tượng đang chứa 100000 chữ số của unscaled value; ngược lại, tạo chuỗi plain hoặc ép về scale khác có thể khiến bộ nhớ tăng mạnh.

## 3. Vì sao precision hoặc scale có thể ảnh hưởng hiệu năng?

`BigDecimal` là kiểu **bất biến** (*immutable*). Mỗi phép toán tạo ra kết quả mới (dù implementation đôi khi có thể tái sử dụng object nội bộ hoặc giá trị cache). Số chữ số lớn hơn thường dẫn tới biểu diễn số nguyên lớn hơn, nhiều công việc số học và nhiều dữ liệu phải sao chép hoặc chuyển đổi.

Các tình huống dễ có chi phí không cần thiết:

1. **Scale bị cộng dồn trong phép nhân chính xác**: kết quả giữ cả chữ số 0 cuối, dù các số 0 đó không làm thay đổi giá trị toán học.
2. **`setScale` lên một giá trị quá lớn**: để tăng scale từ 0 lên hàng chục nghìn, có thể phải tạo unscaled value chứa rất nhiều chữ số 0.
3. **Ép chuỗi không có ký pháp mũ**: `toPlainString()` có thể mở rộng một số rất nhỏ hoặc rất lớn thành một chuỗi khổng lồ.
4. **Phép chia với precision/scale quá cao**: tính và giữ nhiều chữ số hơn yêu cầu có thể tốn chi phí đáng kể.
5. **Chuỗi phép toán không có ranh giới**: precision, scale và độ dài khi serialize có thể tăng theo các đầu vào hoặc lặp không được kiểm soát.

Đây là các **nguy cơ phụ thuộc dữ liệu và thuật toán**, không phải khẳng định cứ tăng một đơn vị precision hoặc scale thì hiệu năng giảm theo một tỷ lệ cố định. Cần đo đạc bằng benchmark phù hợp và theo dõi đầu vào thực tế trước khi đưa ra kết luận tối ưu.

## 4. Scale tăng tự nhiên qua phép toán nào?

### Phép cộng và phép trừ

Trong phép cộng/trừ chính xác thông thường, kết quả có scale bằng **scale lớn nhất của hai toán hạng** (trong trường hợp các phép toán hợp lệ và biểu diễn được):

~~~java
BigDecimal a = new BigDecimal("1.2");    // scale 1
BigDecimal b = new BigDecimal("3.4500"); // scale 4
BigDecimal sum = a.add(b);

System.out.println(sum.toPlainString()); // 4.6500
System.out.println(sum.scale());         // 4
~~~

Vì phải căn theo phần thập phân, một toán hạng có scale lớn có thể tác động tới scale kết quả. Nếu số chữ số 0 cuối không có ý nghĩa trong hợp đồng, chúng có thể là nguồn tăng kích thước biểu diễn qua nhiều bước.

### Phép nhân

Với `multiply(BigDecimal)` **không dùng MathContext**, scale kết quả bằng tổng scale của hai toán hạng:

\[
\text{result.scale} = \text{left.scale} + \text{right.scale}
\]

~~~java
BigDecimal x = new BigDecimal("1.2300"); // scale 4
BigDecimal y = new BigDecimal("2.00");   // scale 2
BigDecimal product = x.multiply(y);

System.out.println(product.toPlainString()); // 2.460000
System.out.println(product.scale());         // 6
System.out.println(product.precision());     // 7
~~~

Giá trị số học chỉ là `2.46`, nhưng kết quả giữ scale 6. Điều này có thể **cần thiết** để bảo toàn một chính sách biểu diễn; nếu không cần, số 0 tận cùng có thể làm tăng precision/scale ở các bước tiếp theo.

Ví dụ nhân `1.000000` năm lần, bắt đầu từ `BigDecimal.ONE`:

~~~java
BigDecimal value = BigDecimal.ONE;
BigDecimal factor = new BigDecimal("1.000000");

for (int i = 0; i < 5; i++) {
    value = value.multiply(factor);
}

System.out.println(value.toPlainString());
// 1.000000000000000000000000000000
System.out.println(value.precision()); // 31
System.out.println(value.scale());     // 30
~~~

**Giá trị luôn là 1**, nhưng precision của biểu diễn tăng lên 31, scale lên 30. Khi quy mô vòng lặp tăng và đầu vào có scale lớn, kích thước biểu diễn có thể trở thành vấn đề.

### Phép chia

`divide(BigDecimal)` không có chế độ làm tròn chỉ trả kết quả thập phân **chính xác**, hoặc ném `ArithmeticException` nếu kết quả thập phân không kết thúc (ví dụ `1 / 3`).

~~~java
BigDecimal one = BigDecimal.ONE;
System.out.println(one.divide(new BigDecimal("8"))); // 0.125

// one.divide(new BigDecimal("3"))
// -> ArithmeticException: decimal expansion không kết thúc
~~~

Khi chia để lấy số gần đúng có kiểm soát, cần cung cấp **scale** hoặc **MathContext** theo yêu cầu đầu ra. Không đặt precision tùy ý quá lớn “cho chắc” nếu pipeline không có lý do sử dụng các chữ số đó.

## 5. `stripTrailingZeros()`: giảm phần biểu diễn dư thừa, nhưng có điều kiện

`stripTrailingZeros()` trả một `BigDecimal` có giá trị số học tương đương, loại bỏ các thừa số 10 cuối của unscaled value và điều chỉnh scale. Đây không phải phép làm tròn.

~~~java
BigDecimal original = new BigDecimal("1.2300");
BigDecimal compact = original.stripTrailingZeros();

System.out.println(original.precision()); // 5
System.out.println(original.scale());     // 4
System.out.println(compact.precision());  // 3
System.out.println(compact.scale());      // 2
System.out.println(compact);              // 1.23
~~~

Sau chuẩn hóa, các phép toán tiếp theo có thể giữ biểu diễn ngắn hơn:

~~~java
BigDecimal rawProduct = original.multiply(new BigDecimal("2.00"));
BigDecimal compactProduct = compact.multiply(new BigDecimal("2"));

System.out.println(rawProduct.toPlainString());     // 2.460000
System.out.println(compactProduct.toPlainString()); // 2.46
~~~

**Không dùng phương thức này một cách mù quáng.** Những khác biệt sau có thể quan trọng:

- `new BigDecimal("1.2300").equals(new BigDecimal("1.23"))` là `false` vì scale khác.
- `new BigDecimal("1000").stripTrailingZeros()` thường thành biểu diễn `1E+3` với **scale âm** (`-3`), không phải `scale = 0`.
- `new BigDecimal("0.000").stripTrailingZeros()` trả giá trị zero với `scale = 0`.
- Nếu đơn vị nghiệp vụ hoặc schema yêu cầu “giữ đúng hai chữ số thập phân”, giảm scale từ 2 về 0 có thể không phù hợp.

Phép chuẩn hóa chỉ nên được áp dụng khi **khác biệt scale không mang ý nghĩa**. Dù `stripTrailingZeros` có thể giúp giảm kích thước biểu diễn, bản thân nó cũng có chi phí xử lý, nên không tự động gọi sau **mọi phép toán** nếu chưa có lý do hoặc bằng chứng đo đạc.

## 6. `setScale`: chọn scale có chủ đích, tránh phình biểu diễn

`setScale(newScale, roundingMode)` tạo biểu diễn ở scale được yêu cầu. Tăng scale thường thêm chữ số 0 vào unscaled value, không đổi giá trị số học. Giảm scale có thể làm mất chữ số khác 0, lúc đó phải có `RoundingMode` hoặc dùng `UNNECESSARY` để yêu cầu chính xác.

~~~java
BigDecimal number = new BigDecimal("123");

System.out.println(number.setScale(6).toPlainString());
// 123.000000

BigDecimal exact = new BigDecimal("123.4500")
        .setScale(2, RoundingMode.UNNECESSARY);

System.out.println(exact.toPlainString()); // 123.45
~~~

Ở ví dụ đầu, thêm 6 chữ số 0 để biểu diễn 123 chính xác dưới scale 6; nếu định dạng đích chỉ cần số nguyên, phần biểu diễn phát sinh là không cần thiết. Ở ví dụ thứ hai, giảm scale vẫn chính xác vì phần bị bỏ toàn số 0.

Khi giảm scale của `123.456` xuống 2, phải quyết định có làm tròn hay không:

~~~java
BigDecimal x = new BigDecimal("123.456");

BigDecimal rounded = x.setScale(2, RoundingMode.HALF_EVEN);
System.out.println(rounded.toPlainString()); // 123.46

// x.setScale(2, RoundingMode.UNNECESSARY)
// -> ArithmeticException vì phải bỏ chữ số khác 0
~~~

Đối với tiền tệ hoặc đo lường, không tự chọn `HALF_EVEN`, `HALF_UP` hay mode khác chỉ để giảm kích thước. Quy tắc, vị trí và thời điểm làm tròn phải đến từ đặc tả nghiệp vụ.

Một thiết kế thực tế thường lưu giá trị ở độ chính xác nội bộ đủ cho phép toán và chỉ áp dụng format/scale đầu ra **tại ranh giới được quy định**, chẳng hạn khi ghi sổ hoặc xuất hóa đơn. Tuy nhiên, với một số hệ thống, luật định yêu cầu làm tròn **từng dòng** trước khi cộng tổng; làm tròn một lần ở cuối sẽ cho kết quả khác. Phải tuân theo ranh giới nghiệp vụ thay vì coi “round cuối” là quy tắc phổ quát.

## 7. `MathContext` giới hạn precision khi bài toán cho phép làm tròn

`MathContext` chứa precision (số chữ số có nghĩa mong muốn) và `RoundingMode`. Ví dụ:

~~~java
MathContext mc = new MathContext(8, RoundingMode.HALF_EVEN);

BigDecimal third = BigDecimal.ONE.divide(new BigDecimal("3"), mc);
System.out.println(third.toPlainString()); // 0.33333333
System.out.println(third.precision());     // 8
~~~

Có thể truyền `MathContext` cho các phép toán hỗ trợ, ví dụ `add`, `multiply`, `divide` hoặc `pow`, nhằm giới hạn **precision của kết quả phép toán đó**.

Không được nhầm:

| API | Giới hạn điều gì? | Ví dụ |
| --- | --- | --- |
| `setScale(2, HALF_EVEN)` | Hai chữ số sau dấu thập phân | `123.456 → 123.46` |
| `round(new MathContext(4, HALF_EVEN))` | Bốn chữ số có nghĩa | `123.456 → 123.5` |
| `divide(divisor, 2, HALF_EVEN)` | Scale của thương bằng 2 | `1 / 3 → 0.33` |
| `divide(divisor, new MathContext(8, HALF_EVEN))` | Precision của thương tối đa 8 | `1 / 3 → 0.33333333` |

Đặc biệt, **MathContext không đồng nghĩa với giới hạn scale tuyệt đối**:

~~~java
BigDecimal tiny = new BigDecimal("1E-100000");
MathContext mc = new MathContext(8, RoundingMode.HALF_EVEN);

System.out.println(tiny.precision()); // 1
System.out.println(tiny.scale());     // 100000
System.out.println(tiny.round(mc).scale()); // 100000
~~~

Precision đã nhỏ hơn 8 nên không cần làm tròn; scale vẫn cực lớn. Một API nhận dữ liệu từ bên ngoài phải kiểm soát **cả precision và scale**, nếu có thể gặp số mũ rất lớn.

### Không “làm tròn để tối ưu” qua từng bước khi chưa chứng minh đúng

Làm tròn ở các bước trung gian có thể làm sai kết quả so với thực hiện phép toán chính xác rồi làm tròn tại ranh giới được yêu cầu.

Ví dụ:

~~~java
BigDecimal first = new BigDecimal("0.125")
        .setScale(2, RoundingMode.HALF_UP);
BigDecimal second = new BigDecimal("0.125")
        .setScale(2, RoundingMode.HALF_UP);

System.out.println(first.add(second)); // 0.26

BigDecimal exactSum = new BigDecimal("0.125")
        .add(new BigDecimal("0.125"));
System.out.println(
        exactSum.setScale(2, RoundingMode.HALF_UP)
); // 0.25
~~~

Cả hai phép tính đều làm tròn theo `HALF_UP`, nhưng **thời điểm làm tròn** khác nhau. Một phương án tối ưu chỉ hợp lệ nếu giữ đúng ngữ nghĩa đã thỏa thuận.

Khi cần tính toán nhiều bước ở precision hữu hạn, hãy thiết kế độ chính xác nội bộ, số chữ số bảo vệ (*guard digits*) nếu cần, và kiểm chứng sai số chấp nhận được. Không dùng một `MathContext` quá nhỏ xuyên suốt pipeline chỉ vì nó có thể làm giảm kích thước object.

## 8. Ví dụ Java 17+ chạy độc lập

Tạo tệp `BigDecimalPrecisionScaleDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalPrecisionScaleDemo {
    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("1.2300");
        BigDecimal compact = original.stripTrailingZeros();

        System.out.printf(
                "original: %s, precision=%d, scale=%d%n",
                original.toPlainString(),
                original.precision(),
                original.scale()
        );
        System.out.printf(
                "compact: %s, precision=%d, scale=%d%n",
                compact.toPlainString(),
                compact.precision(),
                compact.scale()
        );

        BigDecimal rawProduct = original.multiply(new BigDecimal("2.00"));
        BigDecimal compactProduct = compact.multiply(new BigDecimal("2"));
        System.out.printf(
                "raw product: %s, precision=%d, scale=%d%n",
                rawProduct.toPlainString(),
                rawProduct.precision(),
                rawProduct.scale()
        );
        System.out.printf(
                "compact product: %s, precision=%d, scale=%d%n",
                compactProduct.toPlainString(),
                compactProduct.precision(),
                compactProduct.scale()
        );

        BigDecimal grown = BigDecimal.ONE;
        BigDecimal factor = new BigDecimal("1.000000");
        for (int i = 0; i < 5; i++) {
            grown = grown.multiply(factor);
        }
        System.out.printf(
                "repeated multiply: precision=%d, scale=%d%n",
                grown.precision(),
                grown.scale()
        );

        MathContext mc = new MathContext(8, RoundingMode.HALF_EVEN);
        BigDecimal third = BigDecimal.ONE.divide(new BigDecimal("3"), mc);
        System.out.println("third: " + third.toPlainString());

        BigDecimal exactScale = new BigDecimal("123.4500")
                .setScale(2, RoundingMode.UNNECESSARY);
        System.out.println("exact scale reduction: "
                + exactScale.toPlainString());

        BigDecimal exponent = new BigDecimal("1E+3");
        System.out.println("exponent scale: " + exponent.scale());
        System.out.println("exponent plain: " + exponent.toPlainString());
    }
}
~~~

Lệnh chạy trong thư mục chứa tệp:

~~~bash
javac BigDecimalPrecisionScaleDemo.java
java BigDecimalPrecisionScaleDemo
~~~

Stdout chính xác:

~~~text
original: 1.2300, precision=5, scale=4
compact: 1.23, precision=3, scale=2
raw product: 2.460000, precision=7, scale=6
compact product: 2.46, precision=3, scale=2
repeated multiply: precision=31, scale=30
third: 0.33333333
exact scale reduction: 123.45
exponent scale: -3
exponent plain: 1000
~~~

Điều có thể kết luận từ chương trình là **biểu diễn thực sự khác nhau** và scale/precision tăng theo các phép toán đã chỉ ra. Chương trình này không đo thời gian, số lần cấp phát hoặc thông lượng, nên **không phải một benchmark hiệu năng** và không chứng minh mức tăng tốc cụ thể của chuẩn hóa.

Lưu ý scale âm trong dòng cuối: `1E+3` có precision 1, scale -3 và `toPlainString()` hiển thị `1000`. Nếu lấy chuỗi plain đó tạo lại bằng `new BigDecimal("1000")`, đối tượng mới có scale 0. Điều này quan trọng nếu muốn vừa tối ưu vừa giữ round-trip biểu diễn.

## 9. Giới hạn tài nguyên khi nhận decimal từ bên ngoài

Một dịch vụ có thể nhận giá trị quá dài hoặc exponent cực lớn. Dù việc parse và lưu một số như `1E-100000` có thể khá gọn vì unscaled chỉ là 1, gọi `toPlainString()` sẽ phải tạo một chuỗi gồm rất nhiều ký tự 0.

Ví dụ **không nên chạy với exponent không được kiểm soát**:

~~~java
BigDecimal externallySupplied = new BigDecimal("1E-100000");
// Chuỗi plain sẽ rất dài dù precision chỉ bằng 1.
String huge = externallySupplied.toPlainString();
~~~

Thiết kế ranh giới:

1. Hạn chế độ dài chuỗi đầu vào trước khi parse, đồng thời không cho phép định dạng ngoài schema.
2. Parse trực tiếp từ `String` thành `BigDecimal`, không đi qua `double`.
3. Kiểm tra `precision()`, `scale()` và miền giá trị tùy hợp đồng; sử dụng phép chuyển sang `long` khi cần để lấy trị tuyệt đối scale an toàn, tránh `Math.abs(Integer.MIN_VALUE)` vẫn cho số âm.
4. Không tạo chuỗi plain, ép scale lớn, nhân lặp quá nhiều hay lưu database trước khi kiểm soát ranh giới.
5. Khi xuất JSON, biết rõ consumer cần chuỗi decimal hay numeric token, có chấp nhận ký pháp số mũ không, và có phải giữ scale hay không.

Ví dụ phương thức validation minh họa:

~~~java
static BigDecimal parseBounded(String raw) {
    if (raw == null || raw.length() > 128) {
        throw new IllegalArgumentException("Decimal input too long");
    }

    final BigDecimal parsed;
    try {
        parsed = new BigDecimal(raw);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Invalid decimal", ex);
    }

    if (parsed.precision() > 40
            || Math.abs((long) parsed.scale()) > 12) {
        throw new IllegalArgumentException("Decimal outside limits");
    }
    return parsed;
}
~~~

Đây là **phương thức minh họa** để đặt vào lớp Java, không phải một ứng dụng đầy đủ. Các giới hạn 128/40/12 chỉ là ví dụ; schema nghiệp vụ phải quyết định chúng. Trường hợp exponent khổng lồ, cách xử lý parse cũng có thể ném `NumberFormatException` vì scale vượt miền `int`, nên cần bắt lỗi hợp lý.

Một giới hạn `precision <= 40` không thay thế giới hạn scale, và giới hạn scale không thay thế giới hạn **giá trị số học**. Số `999999999999` có thể quá lớn so với miền nghiệp vụ dù precision và scale đều nằm trong ngưỡng.

## 10. Lưu trữ và truyền dữ liệu đúng độ chính xác

### Database `DECIMAL(p,s)` hoặc `NUMERIC(p,s)`

Schema database thường quy định độ rộng số có nghĩa và scale. Nếu cột yêu cầu `DECIMAL(18,2)`, không có lý do chung để thường xuyên mở rộng giá trị thành 100 chữ số thập phân trước khi ghi. Nhưng cũng không được tự làm tròn để vừa cột mà không có chính sách.

Ví dụ:

~~~java
BigDecimal amount = new BigDecimal("19.9900");
BigDecimal valueForColumn =
        amount.setScale(2, RoundingMode.UNNECESSARY);
System.out.println(valueForColumn.toPlainString()); // 19.99
~~~

`UNNECESSARY` chấp nhận vì chỉ bỏ số 0 thừa. Với `19.995`, nó ném `ArithmeticException`; cần quyết định có cho phép làm tròn và dùng mode nào. Không gọi `setScale(2)` mặc định nếu trường hợp cần làm tròn mà chưa nêu mode rõ ràng.

Khi dùng JDBC, truyền bằng `PreparedStatement.setBigDecimal` và đọc bằng `ResultSet.getBigDecimal`; tránh chuyển sang `Double` trung gian. Driver/database có thể áp đặt quy tắc về precision/scale, nên cần kiểm thử round-trip với schema thật.

### JSON và client JavaScript

Một API phải trao đổi các decimal lớn hoặc chính xác cao với JavaScript thường có thể chọn biểu diễn **JSON string**:

~~~json
{
  "amount": "9007199254740993.01"
}
~~~

Nếu dùng JSON number và một client gọi `JSON.parse` thông thường, giá trị có thể bị chuyển thành binary64 và mất chính xác. Chuỗi giữ nguyên ký tự và cho consumer lựa chọn thư viện decimal phù hợp. Khi bảo toàn **scale**, nên xác định có dùng `BigDecimal.toString()` (round-trip cả scale) hay một wire format gồm `unscaledValue + scale`. `toPlainString()` có thể gây mất scale âm khi parse lại; không tự biến mọi giá trị thành plain string nếu ràng buộc biểu diễn quan trọng.

### Log và hiển thị

Thường không cần hiển thị toàn bộ chữ số mà `BigDecimal` đang chứa nếu UI có hợp đồng số chữ số riêng. Hãy áp dụng format tại UI hoặc tầng presenter theo chính sách, không đổi dữ liệu nguồn chỉ để ghi log đẹp. Tránh ghi chuỗi plain cho giá trị có scale cực lớn khi không có giới hạn đầu vào; ký pháp số mũ có thể phù hợp hơn nếu bên nhận hỗ trợ.

## 11. Chọn chiến lược tối ưu theo bài toán

| Bài toán | Cách tiếp cận | Ràng buộc |
| --- | --- | --- |
| Phép nhân liên tiếp khiến scale tăng | Xem xét loại số 0 thừa ở đầu vào hoặc điểm kiểm soát bằng `stripTrailingZeros()` | Chỉ khi không cần giữ scale gốc |
| Kết quả cần đúng hai chữ số sau dấu thập phân | Dùng `setScale(2, mode)` ở ranh giới nghiệp vụ | Mode và thời điểm phải được quy định |
| Phép chia không kết thúc | Chỉ định scale hoặc `MathContext` vừa đủ | Có thể thay đổi kết quả do làm tròn |
| Payload có exponent không tin cậy | Giới hạn text length, precision, scale trước chuyển đổi tốn bộ nhớ | Chặn tài nguyên quá mức |
| Dữ liệu tính toán tài chính cần decimal chính xác | Giữ `BigDecimal` và decimal string/kiểu decimal chuẩn | Không đưa qua `double` |
| Tính toán khoa học cho phép sai số xác định | Chọn precision theo ngân sách sai số đã phân tích | Không cắt precision tùy tiện |
| Kết quả dùng để so sánh theo giá trị số học | `compareTo`, có thể chuẩn hóa nếu hợp đồng cho phép | `equals` và hash vẫn nhạy scale |
| Cần giữ nguyên biểu diễn/scale trên đường truyền | Dùng `toString()` hoặc cặp unscaled/scale | Không bỏ trailing zeros tùy tiện |

Không có một cấu hình `MathContext` hoặc một lời gọi `stripTrailingZeros` “nhanh nhất” cho mọi ứng dụng. Việc quyết định cần xem xét **độ lớn thực tế, số lượng phép toán, ngữ nghĩa scale, tần suất serialize, chi phí lưu trữ và mức sai số cho phép**.

## 12. Đo hiệu năng mà không làm sai kết luận

Một phép thử đơn lẻ dùng `System.nanoTime()` thường bị ảnh hưởng bởi JIT compilation, warmup, garbage collector và tối ưu hóa của JVM. Với một tối ưu quan trọng, nên dùng framework microbenchmark phù hợp như **JMH**, đo trên dữ liệu gần thực tế và kiểm soát kết quả không bị loại bỏ bởi tối ưu hóa.

Có thể thiết kế các nhóm thử:

- **Scale thấp vs scale cao**: cùng giá trị toán học nhưng khác số 0 tận cùng.
- **Phép nhân lặp**: giữ scale chính xác so với một pipeline đã được phép chuẩn hóa.
- **Serialize**: `toString()` so với `toPlainString()` trên dữ liệu có exponent khác nhau.
- **Giới hạn precision**: đúng precision nghiệp vụ so với precision cao hơn nhiều.
- **Allocation và GC**: số object, byte được cấp phát và độ trễ, ngoài throughput.

Một benchmark phải xác nhận hai bên thực hiện **cùng nhiệm vụ nghiệp vụ**. Chẳng hạn so sánh phép tính exact với phép tính bị cắt precision không phải so sánh công bằng nếu đầu ra yêu cầu exact. Đừng coi việc giảm chữ số là chiến thắng khi kết quả đã sai.

## 13. Lỗi phổ biến

1. **Nhầm scale với precision:** `1E-100000` có precision 1, scale 100000; kiểm tra một trong hai không bảo vệ toàn bộ.
2. **Tăng `setScale` “cho an toàn” ở mọi bước:** có thể chỉ thêm số 0, làm tăng kích thước biểu diễn mà không tăng thông tin.
3. **Chỉ dùng `MathContext` để bảo vệ scale:** một giá trị có precision nhỏ vẫn có scale rất lớn.
4. **Gọi `stripTrailingZeros` khi cần giữ scale:** kết quả có thể khác `equals` và thậm chí có scale âm.
5. **Làm tròn trung gian quá sớm:** nhiều lần làm tròn có thể đổi tổng cuối; thiết kế ranh giới rõ ràng.
6. **Chuyển qua `double` để “tăng tốc” số tiền:** có thể gây mất chính xác, không thể khôi phục khi chuyển lại sang `BigDecimal`.
7. **Dùng `toPlainString` với exponent cực lớn không tin cậy:** có thể tạo chuỗi rất dài và gây áp lực bộ nhớ.
8. **Benchmark thiếu warmup/kiểm tra đúng kết quả:** chênh lệch do môi trường hoặc bỏ công việc có thể làm kết luận sai.

## 14. Tự kiểm tra

Xét đoạn mã:

~~~java
BigDecimal a = new BigDecimal("2.5000");
BigDecimal b = new BigDecimal("3.00");
BigDecimal c = a.multiply(b);
BigDecimal d = a.stripTrailingZeros()
        .multiply(b.stripTrailingZeros());
~~~

Trả lời trước khi chạy:

1. `a.precision()` và `a.scale()` là bao nhiêu?
2. `c` in bằng `toPlainString()` là gì? Precision và scale của `c`?
3. `d` in bằng `toPlainString()` là gì? Precision và scale của `d`?
4. `c.compareTo(d)` và `c.equals(d)` cho kết quả nào?
5. Tại sao dùng `new MathContext(4, RoundingMode.HALF_EVEN)` **không** thay thế được kiểm soát scale trên mọi đầu vào?
6. Nếu cần lưu chính xác `"2.5000"` với scale 4, có nên thay nó bằng `stripTrailingZeros()` không?

**Đáp án:**

1. `a` có unscaled value `25000`, precision 5 và scale 4.
2. `c` là `7.500000`, precision 7, scale 6 (4 + 2).
3. `d` là `7.5`, precision 2, scale 1.
4. `compareTo` trả `0` vì cùng giá trị số học; `equals` trả `false` vì scale khác.
5. MathContext giới hạn **số chữ số có nghĩa** của kết quả phép toán, nhưng một số như `1E-100000` vẫn có precision nhỏ và scale cực lớn.
6. Không, nếu scale gốc là một phần yêu cầu trao đổi hoặc lưu trữ.

Cần ghi nhớ: muốn tối ưu số thập phân phải xác định **độ chính xác cần giữ, scale có ý nghĩa gì và khi nào được phép làm tròn**. Giảm biểu diễn dư thừa có thể hữu ích, nhưng tính đúng đắn, hợp đồng dữ liệu và phép đo thực tế luôn là điều kiện trước mọi tối ưu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-006">Không thay đổi quy tắc chính xác chỉ để rút ngắn thời gian chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Không thay đổi quy tắc chính xác chỉ để rút ngắn thời gian chạy

## 1. Vấn đề cốt lõi

Một phép tính số trong Java có thể chậm vì kiểu dữ liệu, số lần cấp phát đối tượng, thuật toán, cách truy cập bộ nhớ hoặc số lượng phép toán. Khi tối ưu, việc đổi từ `BigDecimal` sang `double`, giảm `MathContext.precision`, thay `long` bằng `int` hoặc đổi chỗ làm tròn thường giúp một đoạn code ngắn hơn và *có thể* nhanh hơn. Nhưng nếu ứng dụng trước và sau đưa ra các kết quả khác nhau đối với cùng đầu vào thì đó không còn là tối ưu tương đương: **hợp đồng tính toán đã thay đổi**.

Ví dụ ba dòng tiền dưới dạng chuỗi thập phân chính xác:

```text
0.005
0.005
0.005
```

Nếu hợp đồng yêu cầu **cộng chính xác tất cả dòng, rồi làm tròn tổng tới 2 chữ số thập phân bằng `HALF_UP`**, kết quả là `0.02`. Nếu một thay đổi “tối ưu” làm tròn **từng dòng** trước khi cộng, kết quả là `0.03`. Không thể gọi cả hai cách là tương đương vì chúng đáp ứng hai chính sách nghiệp vụ khác nhau.

**Mục tiêu bài học:** xác định hợp đồng chính xác của phép toán số; nhận diện thay đổi ngữ nghĩa khi tối ưu; ưu tiên những cải tiến giữ nguyên kết quả; đo hiệu năng đúng phương pháp; và kiểm chứng cả tính đúng đắn lẫn lợi ích hiệu năng trong Java 17+.

Bài thuộc chủ đề **Hiệu năng của Java core - numbers**. Trọng tâm là phép tính số trong một chương trình Java. Hợp đồng dữ liệu với cơ sở dữ liệu hoặc API là phần mở rộng cần đối chiếu khi quyết định quy tắc lưu/trao đổi.

## 2. “Đúng” nghĩa là gì: hợp đồng số học trước benchmark

Không có khái niệm “nhanh và đủ chính xác” mang tính phổ quát. Trước khi sửa code, cần trả lời rõ những yêu cầu dưới đây:

| Thuộc tính hợp đồng | Câu hỏi cần chốt | Ví dụ |
| --- | --- | --- |
| **Miền dữ liệu** | Âm, dương, 0, số rất lớn/rất nhỏ; có NaN/vô cực không? | Số lượng phải không âm, giới hạn trên xác định |
| **Kiểu và miền biểu diễn** | `int`, `long`, `BigInteger`, `double`, `BigDecimal`? | Tổng không được vượt miền `long` |
| **Độ chính xác** | Chính xác tuyệt đối, precision hữu hạn, hay sai số được định lượng? | Giá trị thập phân phải chính xác đến đơn vị 0.01 |
| **Điểm làm tròn** | Làm tròn mỗi đơn vị, mỗi dòng hay cuối phép tính? | `HALF_UP` một lần khi chốt tổng |
| **Chế độ làm tròn** | `HALF_EVEN`, `HALF_UP`, `DOWN`, `UNNECESSARY`...? | Từ chối dữ liệu có quá 2 chữ số có nghĩa sau dấu phẩy |
| **Scale/định dạng** | Cần giá trị hay cả biểu diễn (`1.0` khác `1.00`)? | Lưu tiền với `scale = 2` |
| **Tràn số/ngoại lệ** | Tràn được phép quay vòng, bão hòa hay phải ném lỗi? | `Math.addExact` từ chối tràn |
| **Thứ tự và tính tái lập** | Kết quả phải trùng từng bit hay chỉ trong ngưỡng sai số? | Cùng input tạo cùng output trên môi trường mục tiêu |
| **Giá trị đặc biệt** | Có chấp nhận `NaN`, `+0.0`, `-0.0`, `Infinity` không? | Thống kê không được âm thầm bỏ `NaN` |
| **Đơn vị/ngữ nghĩa** | Xu, đồng, mili giây, phần trăm, tỷ lệ hay số đếm? | Không tự đổi đơn vị khi đổi kiểu lưu |

Phải quyết định **sự tương đương** được kiểm thử bằng tiêu chí nào:

- **Đúng bằng về số học:** `BigDecimal.compareTo(other) == 0` khi không quan tâm scale.
- **Đúng cả biểu diễn:** `BigDecimal.equals(other)` khi scale cũng là một phần hợp đồng.
- **Đúng đến số chữ số quy định:** áp dụng rounding/scale tại ranh giới đã định, sau đó so sánh.
- **Sai số có giới hạn:** ví dụ công thức khoa học cho phép absolute error hoặc relative error dưới ngưỡng được mô tả cẩn thận; ngưỡng này phải được phê duyệt theo bài toán, không suy diễn từ thời gian chạy.
- **Đúng cả hành vi lỗi:** cùng input không hợp lệ phải tiếp tục bị từ chối; không được biến tràn hoặc chia không chính xác thành một kết quả giả.

Một tối ưu chỉ được coi **giữ nguyên hợp đồng** khi đáp ứng toàn bộ thuộc tính có ý nghĩa. Benchmark không thể chứng minh một phép tính sai trở nên đúng.

## 3. Vì sao đổi `BigDecimal` sang `double` có thể phá hợp đồng?

`double` theo IEEE 754 là số dấu phẩy động nhị phân có độ chính xác hữu hạn. Nhiều phân số thập phân như `0.1` và `0.2` không biểu diễn chính xác dưới dạng nhị phân hữu hạn.

```java
import java.math.BigDecimal;

BigDecimal exact = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));

BigDecimal viaDouble = BigDecimal.valueOf(0.1 + 0.2);

System.out.println(exact.toPlainString());     // 0.3
System.out.println(viaDouble.toPlainString()); // 0.30000000000000004
```

Đây là đoạn mã *nhúng*, cần đặt trong `main` hoặc phương thức khác. `BigDecimal.valueOf(double)` chuyển kết quả `double` đã có thành dạng thập phân theo `Double.toString`; nó **không khôi phục** giá trị chính xác của hai toán hạng thập phân gốc.

Nếu nghiệp vụ yêu cầu tổng thập phân chính xác, thay `BigDecimal` bằng `double` chỉ để giảm allocation là thay đổi sai. Nếu nghiệp vụ ngay từ đầu cho phép sai số nhị phân đã công bố, `double` có thể là lựa chọn hợp lệ; nhưng đó là **quyết định hợp đồng**, không phải tối ưu tùy tiện.

### 3.1. `MathContext.precision` không phải tham số “càng nhỏ càng nhanh càng tốt”

```java
import java.math.MathContext;
import java.math.RoundingMode;

BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

BigDecimal p6 = one.divide(
        three, new MathContext(6, RoundingMode.HALF_EVEN));
BigDecimal p12 = one.divide(
        three, new MathContext(12, RoundingMode.HALF_EVEN));

// p6  = 0.333333
// p12 = 0.333333333333
```

Giảm precision thay đổi số chữ số có nghĩa, có thể đổi kết quả ở các bước tính tiếp theo. Nếu bài toán đòi 12 chữ số có nghĩa, đổi sang 6 chỉ để tiết kiệm CPU **vi phạm hợp đồng**. `MathContext.UNLIMITED` có thể ném `ArithmeticException` khi phép chia không có biểu diễn thập phân hữu hạn, chẳng hạn `1 / 3`; không được tự trả 0 hoặc một xấp xỉ thiếu cơ sở để né ngoại lệ.

### 3.2. `setScale` không tương đương với `round(MathContext)`

- `setScale(2, HALF_EVEN)` quyết định **hai chữ số sau dấu thập phân**.
- `round(new MathContext(2, HALF_EVEN))` quyết định **hai chữ số có nghĩa**.

Với `123.45`, cách thứ nhất giữ `123.45` (đã có scale 2), cách thứ hai làm tròn thành xấp xỉ `120`. Không thay hai API cho nhau dù có thể giảm số phép toán.

### 3.3. Không lược bỏ `RoundingMode.UNNECESSARY`

```java
new BigDecimal("1.234").setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException vì cần bỏ chữ số có giá trị.
```

Nếu chính sách là **từ chối** số có quá nhiều chữ số thập phân có giá trị, đổi thành `setScale(2, HALF_UP)` là chấp nhận dữ liệu trước đây bị từ chối. Đây là thay đổi validation nghiệp vụ. Việc dữ liệu được xử lý “trơn tru hơn” không chứng minh kết quả đúng.

## 4. Vị trí làm tròn là một phần của hợp đồng

Dữ liệu: ba dòng `0.005`, `0.005`, `0.005`, rounding mode `HALF_UP`, scale đầu ra 2.

**Chính sách A — làm tròn tổng cuối:**

```java
BigDecimal total = new BigDecimal("0.005")
        .add(new BigDecimal("0.005"))
        .add(new BigDecimal("0.005"))
        .setScale(2, RoundingMode.HALF_UP);
// 0.02
```

**Chính sách B — làm tròn từng dòng trước khi cộng:**

```java
BigDecimal line = new BigDecimal("0.005")
        .setScale(2, RoundingMode.HALF_UP); // 0.01

BigDecimal total = line.add(line).add(line); // 0.03
```

Cả hai cách đều có thể có lý do nghiệp vụ. Nhưng chúng **không thay thế được cho nhau**. Đừng kéo `setScale()` vào trong vòng lặp, đưa ra ngoài vòng lặp hoặc đổi thời điểm kết toán nếu không xác nhận yêu cầu làm tròn.

Phép cộng `BigDecimal.add` **không truyền `MathContext`** là phép cộng chính xác trong miền biểu diễn và tài nguyên cho phép. Nếu mỗi bước `add` được truyền một `MathContext`, một phép cộng có thể làm tròn ở từng bước; đổi thứ tự/lược bớt context có thể thay đổi kết quả. Tương tự với `multiply`, `divide` và `pow` có context.

### 4.1. Rounding hai lần (*double rounding*)

Nếu một pipeline làm tròn tới precision trung gian rồi lại `setScale` để hiển thị, chữ số trung gian bị bỏ có thể khiến lần làm tròn cuối lệch so với làm tròn một lần trên giá trị toán học chính xác. Vì vậy, nếu muốn “precision phụ để tăng tốc”, hãy chứng minh ràng buộc sai số của toàn pipeline. Không có một số chữ số dự phòng cố định phù hợp mọi phép tính.

### 4.2. `stripTrailingZeros()` và biểu diễn cũng có thể là ngữ nghĩa

```java
BigDecimal left = new BigDecimal("1.0");
BigDecimal right = new BigDecimal("1.00");

System.out.println(left.compareTo(right) == 0); // true
System.out.println(left.equals(right));          // false
```

Chuẩn hóa bằng `stripTrailingZeros` có thể thích hợp cho khóa số học, nhưng đổi `scale` và kết quả của `equals`/`hashCode`; giá trị như `1000.00` có thể trở thành `1E+3` (scale âm). Đừng vô điều kiện loại số 0 cuối để giảm kích thước/chi phí khi ứng dụng cần bảo toàn đơn vị đo, văn bản đầu vào, kết quả JSON hoặc khóa phân biệt scale.

## 5. Không đánh đổi quy tắc tràn số lấy tốc độ

`int` và `long` là các số nguyên có phạm vi hữu hạn. Trong Java, phép cộng/nhân nguyên thông thường có thể tràn và quay vòng theo biểu diễn hai bù. Nếu hợp đồng **yêu cầu phát hiện tràn**, hãy dùng `Math.addExact`, `Math.multiplyExact`, `Math.subtractExact` theo kiểu phù hợp:

```java
long current = 5_000L;
long incoming = 7_525L;
long cents = Math.addExact(current, incoming);

BigDecimal amount = BigDecimal.valueOf(cents, 2);
System.out.println(amount.toPlainString()); // 125.25
```

Lưu **đơn vị nhỏ nhất** bằng `long` có thể là một thiết kế hiệu quả và chính xác cho miền có thang đo cố định (ví dụ xu), **nếu** đáp ứng các điều kiện:

1. Toàn bộ giá trị đầu vào có thể đổi **chính xác** sang đơn vị nhỏ nhất.
2. Chính sách rounding của hệ thống được áp dụng đúng ở ranh giới chuyển đổi.
3. Số nhỏ nhất/lớn nhất và mọi phép tính trung gian không vượt miền `long`.
4. Phép chia, tỷ lệ/thuế và phần dư được xử lý theo đúng hợp đồng, không âm thầm dùng chia nguyên.
5. Định dạng, lưu trữ và giao tiếp giữ nguyên đơn vị và `scale` mong muốn.

Ví dụ `BigDecimal.valueOf(cents, 2)` tạo giá trị thập phân theo đơn vị xu mà không đi qua `double`. Nếu hợp đồng cần số chữ số thập phân biến đổi hoặc giá trị vượt miền `long`, cách lưu đơn vị nhỏ nhất bằng `long` có thể **không phù hợp**.

### 5.1. Khi cần số nguyên vượt miền `long`

Dùng `BigInteger` cho phép tính chính xác không bị tràn theo kích thước cố định:

```java
import java.math.BigInteger;

BigInteger factorial = BigInteger.ONE;
for (int n = 2; n <= 21; n++) {
    factorial = factorial.multiply(BigInteger.valueOf(n));
}
System.out.println(factorial); // 51090942171709440000
```

`21!` vượt `Long.MAX_VALUE`. Thay `BigInteger` bằng `long` để nhanh hơn mà không có kiểm tra miền sẽ làm sai giá trị. Ngược lại, nếu toàn miền dữ liệu được chứng minh chắc chắn nằm trong `long`, có thể cân nhắc thiết kế `long` và dùng checked arithmetic, kiểm thử ranh giới và công bố bất biến đó.

**Atomicity không chữa tràn số:** `AtomicLong.incrementAndGet()` bảo vệ cập nhật concurrent nhưng vẫn có thể quay vòng nếu vượt phạm vi `long`. Khi số đếm tuyệt đối không được tràn, phải kết hợp quy tắc miền và cập nhật có điều kiện phù hợp.

## 6. Số dấu phẩy động, thứ tự cộng và tính tái lập

Với `float`/`double`, phép cộng không luôn kết hợp theo nghĩa toán học khi có làm tròn:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

System.out.println((a + b) + c); // 1.0
System.out.println(a + (b + c)); // 0.0
```

Biểu thức `b + c` làm mất `1.0` do độ lớn chênh lệch. Vì vậy thay một vòng lặp tuần tự bằng phép reduction song song, đổi thứ tự phần tử hoặc nhóm cặp hạng để “tăng throughput” **có thể đổi kết quả**, ngay cả khi không đổi kiểu `double`.

Những vấn đề cần xét trong hợp đồng:

- Có yêu cầu trùng bit giữa các lần chạy hay chỉ yêu cầu sai số trong ngưỡng đã phê duyệt?
- Cách cộng đã được định nghĩa theo thứ tự phần tử chưa?
- Có hỗ trợ `NaN`, `Infinity`, signed zero (`-0.0`/`+0.0`), overflow/underflow không?
- Có cần thuật toán ổn định hơn, ví dụ cộng bù (*compensated summation*) hay tổng đôi/cặp, để giảm sai số?
- Các phép biến đổi của compiler/JVM và thư viện số trong JDK mục tiêu có được đặc tả phù hợp không?

Kỹ thuật tăng độ ổn định số học có thể *cải thiện sai số*, nhưng nếu nó đổi output cũ thì vẫn phải kiểm chứng so với hợp đồng. Nếu hợp đồng yêu cầu kết quả từng bit giống thuật toán cũ, “chính xác hơn” vẫn có thể là thay đổi không tương thích.

Java 17 luôn sử dụng semantics floating-point chặt theo quy tắc ngôn ngữ (*always-strict floating-point semantics*); điều đó không biến `double` thành số thập phân chính xác và không khiến phép cộng trở nên kết hợp.

### 6.1. Với `BigDecimal`, thứ tự tính và context vẫn quan trọng

Cộng `BigDecimal` chính xác, không dùng `MathContext`, thường bảo toàn cùng một giá trị toán học bất kể cách nhóm phép cộng; tuy nhiên biểu diễn `scale` và việc tạo đối tượng vẫn có thể quan trọng với API. Khi áp dụng `MathContext` ở các bước trung gian, thứ tự tính có thể làm thay đổi các lần làm tròn. Vì vậy không hoán đổi phép toán, song song hóa hay vector hóa mà bỏ qua vị trí rounding đã quy định.

## 7. Các tối ưu nên xét trước khi giảm độ chính xác

Những cải tiến dưới đây thường bảo toàn quy tắc tính toán hơn, **nhưng vẫn cần kiểm chứng với hợp đồng cụ thể**.

### 7.1. Chọn thuật toán tốt hơn

Tránh tính lại cùng một giá trị không thay đổi; dùng precomputation/cache cho kết quả của hàm thuần túy nếu khóa cache chứa **mọi tham số ngữ nghĩa**: operands, rounding mode, precision, scale, đơn vị và phiên bản quy tắc. Với đầu vào nhiều số rất lớn, kiểm tra độ phức tạp thuật toán, giới hạn lặp và bộ nhớ thay vì cắt bớt chữ số.

Nếu một báo cáo cần min/max/count, `IntStream.summaryStatistics()` có thể duyệt một lượt thay vì dựng nhiều stream; nhưng phải giữ đúng cách xử lý rỗng và tràn tổng, không dựa vào giá trị khởi tạo min/max khi count bằng 0.

### 7.2. Loại việc cấp phát/chuyển đổi không cần thiết

**Tốt hơn cho dữ liệu đã đúng kiểu:**

```java
static BigDecimal sumAmounts(List<BigDecimal> values) {
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal value : values) {
        total = total.add(value); // chính xác, không chuyển qua double
    }
    return total;
}
```

Hàm trên cần `import java.math.BigDecimal; import java.util.List;` và là một phương thức *nhúng*. `BigDecimal` bất biến nên các phép cộng có thể tạo đối tượng mới; không thể “sửa” đối tượng hiện có bằng `+=`.

Nếu đầu vào đến dưới dạng `String`, parse **một lần ở ranh giới** vào kiểu phù hợp; tránh liên tục đổi `BigDecimal -> String -> double -> BigDecimal` trong hot path. Có thể giữ constant/context dùng chung cho cùng một chính sách:

```java
static final MathContext MONEY_MC =
        new MathContext(18, RoundingMode.HALF_EVEN);
```

`MathContext` bất biến và có thể tái sử dụng khi quy tắc tính toán giống nhau. Tuy nhiên không thay precision hoặc rounding mode của constant chỉ để giảm chi phí.

### 7.3. Tách phép tính số khỏi định dạng và I/O

Nếu workload phải cộng một triệu giá trị, không gọi `toPlainString()`, formatter hoặc ghi log cho từng phép cộng trừ khi hợp đồng cần log từng bước. Thực hiện tính toán rồi định dạng tại **ranh giới xuất**. Nhưng nếu hợp đồng yêu cầu làm tròn/chuẩn hóa từng dòng, không được dời `setScale` sang cuối chỉ vì việc tạo đối tượng ít hơn.

Tách allocation, serialization, log và truy cập DB khỏi vùng CPU nóng có thể có lợi mà không thay đổi kết quả số.

### 7.4. Dùng primitive đúng miền, với checked arithmetic

Nếu quy tắc dùng số nguyên và miền đã được chứng minh, `int`/`long` có thể tránh overhead của object. Tuy nhiên:

- `int` có miền nhỏ hơn `long`; chuyển `long` sang `int` có thể cắt dữ liệu.
- `long` là số nguyên, không biểu diễn phân số trực tiếp; dùng đơn vị cố định với factor đã thỏa hợp đồng.
- `Math.addExact`/`multiplyExact` giúp giữ quy tắc phát hiện tràn.
- Không dùng phép chia nguyên nếu trước đây nghiệp vụ cho phép phân số.
- Không giả định đổi sang kiểu primitive sẽ tự động nhanh hơn: đo thực tế.

### 7.5. Điều chỉnh lưu trữ/cấu trúc dữ liệu, không điều chỉnh toán học

Với tập dữ liệu lớn, cách truy cập tuần tự, dung lượng cache CPU, lựa chọn array/collection, số lần boxing, batch I/O và concurrency có thể quyết định hiệu năng. Chọn cấu trúc phù hợp vẫn phải bảo đảm thứ tự, null handling, duplicate handling và ngữ nghĩa của dữ liệu.

Ví dụ `Arrays.stream(intArray).sum()` dùng `int` và có thể tràn; nếu tổng yêu cầu `long`, phải mở rộng đúng cách (`mapToLong`) và kiểm tra tràn phù hợp. Không chọn API tiện tay chỉ vì cú pháp ngắn hơn.

## 8. Ví dụ Java 17+ hoàn chỉnh: nhìn thấy thay đổi hợp đồng

Tạo file **`ExactnessPerformanceDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ExactnessPerformanceDemo {

    // Hợp đồng A: cộng chính xác, chỉ làm tròn khi chốt tổng.
    static BigDecimal roundAtTotal(List<String> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (String text : values) {
            total = total.add(new BigDecimal(text));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // Hợp đồng B: làm tròn mỗi dòng trước khi cộng.
    static BigDecimal roundPerItem(List<String> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (String text : values) {
            total = total.add(
                    new BigDecimal(text).setScale(2, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static void main(String[] args) {
        List<String> amounts = List.of("0.005", "0.005", "0.005");

        System.out.println("round at total = "
                + roundAtTotal(amounts).toPlainString());
        System.out.println("round per item = "
                + roundPerItem(amounts).toPlainString());

        System.out.println("exact sum = "
                + new BigDecimal("0.1").add(new BigDecimal("0.2"))
                        .toPlainString());
        System.out.println("double sum = "
                + BigDecimal.valueOf(0.1 + 0.2).toPlainString());

        long cents = Math.addExact(5_000L, 7_525L);
        System.out.println("checked minor units = "
                + BigDecimal.valueOf(cents, 2).toPlainString());

        try {
            new BigDecimal("1.234")
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("strict scale = rejected");
        }

        BigDecimal first = new BigDecimal("1.0");
        BigDecimal second = new BigDecimal("1.00");
        System.out.println("numeric compare = "
                + (first.compareTo(second) == 0));
        System.out.println("structural equals = "
                + first.equals(second));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ExactnessPerformanceDemo.java
java ExactnessPerformanceDemo
```

Stdout xác định:

```text
round at total = 0.02
round per item = 0.03
exact sum = 0.3
double sum = 0.30000000000000004
checked minor units = 125.25
strict scale = rejected
numeric compare = true
structural equals = false
```

Điều chương trình chứng minh:

1. **Thời điểm rounding là ngữ nghĩa.** Hai hàm dùng cùng dữ liệu, scale và `HALF_UP`, nhưng mỗi hàm làm tròn tại ranh giới khác nhau nên kết quả khác nhau.
2. **Đổi sang `double` không bảo toàn thập phân.** Ngay cả ví dụ `0.1 + 0.2` đã có giá trị khác.
3. **Dùng `long` với đơn vị nhỏ nhất có thể đúng khi miền cố định.** `Math.addExact` làm rõ hợp đồng tràn, `BigDecimal.valueOf(cents, 2)` khôi phục biểu diễn hai chữ số thập phân mà không đi qua `double`.
4. **`UNNECESSARY` là một quy tắc từ chối.** Đổi nó sang rounding mode khác sẽ thay đổi dữ liệu được hệ thống chấp nhận.
5. **Giá trị số và biểu diễn là hai khái niệm.** `compareTo` coi `1.0` và `1.00` bằng nhau, `equals` thì không. Đừng loại bỏ scale để “tối ưu cache” nếu ứng dụng cần phân biệt chúng.

Đây là bằng chứng về **tính đúng đắn**, không phải benchmark: không có kết luận nào về thời gian nhanh/chậm chỉ từ stdout của chương trình này.

## 9. Benchmark có ý nghĩa sau khi đã khóa hợp đồng

Quy trình tối ưu nên chạy theo thứ tự:

1. **Viết đặc tả đầu ra và xử lý lỗi:** kiểu, miền giá trị, scale, precision, rounding mode, thời điểm rounding, overflow và ngoại lệ.
2. **Tạo tập dữ liệu đối chiếu:** các giá trị bình thường và trường hợp biên. Giữ giá trị kỳ vọng được tính độc lập khi có thể.
3. **Đo baseline:** ghi môi trường JDK, CPU, dung lượng heap, input size, mức tải đồng thời, throughput/latency/GC.
4. **Tối ưu một yếu tố rõ ràng:** thuật toán, allocation, định dạng, cấu trúc dữ liệu, chi phí đồng bộ hoặc I/O.
5. **Chạy kiểm thử hợp đồng trước:** đối chiếu kết quả, lỗi, scale, precision và điều kiện biên.
6. **Đo lại theo cùng workload:** đánh giá trung vị, phân bố và độ nhiễu; chỉ giữ tối ưu khi có lợi ích đủ rõ và tính đúng đắn được bảo toàn.
7. **Ghi lại quyết định:** vì sao thay đổi an toàn, giới hạn miền áp dụng và điều kiện phải benchmark lại.

### 9.1. Vì sao không đo một lần với `System.nanoTime()`?

JVM có JIT compilation, class loading, warmup, GC và tối ưu code dựa trên profiling. Benchmark một lần thường đo lẫn quá trình khởi động và các yếu tố không liên quan. Compiler cũng có thể loại bỏ phép tính không ảnh hưởng đầu ra (*dead-code elimination*); đo một vòng lặp như vậy có thể khiến phương pháp sai trông “cực nhanh”.

Trong Java, JMH là thư viện benchmark vi mô thường được dùng để kiểm soát warmup, nhiều lần đo, fork JVM, tải song song và việc tiêu thụ kết quả thông qua `Blackhole`. Nhưng ngay cả JMH chỉ trả lời **hiệu năng của workload được đo**, không chứng minh tính tương đương số học.

### 9.2. Tách chi phí benchmark và chi phí sản phẩm

Ví dụ so sánh `BigDecimal` với một thuật toán long minor-units cần:

- Chuẩn bị dữ liệu **ngoài đoạn đo** nếu chỉ benchmark phép cộng; hoặc đo cả parse/format nếu đó là một phần đường xử lý thực tế.
- Dùng cùng input và quy tắc đơn vị, xác định overflow handling nhất quán.
- Đánh giá chính sách làm tròn trên các đầu vào không phải bội của đơn vị nhỏ nhất.
- Bảo đảm vòng đo tiêu thụ kết quả, không để JVM loại công việc.
- Thử với kích thước dữ liệu vừa, lớn và giá trị ở ranh giới.
- Đo GC/allocation nếu lý do tối ưu là giảm đối tượng, và đo tail latency nếu hệ thống nhạy với độ trễ.

Không được chỉ đo phương pháp mới trên các số “đẹp”, còn phương pháp cũ trên số cần làm tròn nhiều chữ số; workload khác nhau không chứng minh phương pháp mới vượt trội.

## 10. Kiểm thử hồi quy: phải so cái gì?

Tạo test ở đúng ranh giới mà nghiệp vụ có thể quan sát.

**Các case thập phân nên có:**

| Input/đặc điểm | Mục tiêu |
| --- | --- |
| `"0.1" + "0.2"` | Phát hiện chuyển đổi qua `double` |
| `0.005` lặp 3 lần | Phát hiện đổi điểm rounding |
| `"1.0"` và `"1.00"` | Phân biệt giá trị số với scale |
| `1.234` với scale 2 + `UNNECESSARY` | Giữ hành vi từ chối |
| `Long.MAX_VALUE + 1` với `Math.addExact` | Kiểm tra overflow bị từ chối |
| Số âm và điểm giữa như `-2.345`, `2.345`, `2.355` | Kiểm tra rounding mode |
| Giá trị rất nhỏ và rất lớn | Kiểm tra precision/scale/exponent và miền kiểu |
| Phép chia không kết thúc như `1/3` | Kiểm tra MathContext và ngoại lệ |
| `NaN`, ±infinity, `-0.0` nếu dùng `double` | Kiểm tra quy tắc cho giá trị đặc biệt |

Nếu hợp đồng yêu cầu đúng đến xu, so sánh **giá trị và scale đã chuẩn hóa theo hợp đồng**, không lấy `doubleValue()` của hai `BigDecimal` rồi dùng `==`. Nếu hợp đồng yêu cầu giữ biểu diễn, kiểm tra `equals`, `scale()` hoặc chuỗi xuất. Nếu hợp đồng cho phép sai số, định nghĩa sai số tuyệt đối/tương đối phù hợp cả gần 0, cực lớn, `NaN` và vô cực.

Có thể áp dụng **differential testing**: chạy bản baseline và bản tối ưu trên cùng tập dữ liệu, so kết quả theo đúng quan hệ tương đương đã đặc tả. Đây là cách phát hiện hồi quy, nhưng một baseline có bug không tự trở thành tiêu chuẩn đúng; khi có nghi ngờ, đối chiếu yêu cầu nghiệp vụ hoặc oracle độc lập.

### 10.1. Test tính đúng và test hiệu năng có nhiệm vụ khác nhau

- Unit/property test: xác nhận tính đúng, phạm vi dữ liệu, ngoại lệ, rounding.
- Benchmark: xác nhận thời gian/tài nguyên, mức dao động, khả năng mở rộng.
- Integration test: xác nhận kiểu dữ liệu, database scale, serialization và toàn pipeline.
- Kiểm thử concurrency: xác nhận trạng thái chia sẻ và thứ tự xử lý không làm mất hoặc thay đổi dữ liệu theo hợp đồng.

Một benchmark chạy nhanh và không ném exception vẫn có thể xuất ra số sai. Một unit test chạy đúng trên vài đầu vào vẫn không chứng minh hiệu năng trong môi trường production.

## 11. Ra quyết định khi tối ưu khiến kết quả khác đi

Có những trường hợp lợi ích thời gian rất lớn khiến doanh nghiệp muốn chấp nhận độ chính xác thấp hơn. Điều này có thể hợp lệ **chỉ khi có thay đổi hợp đồng rõ ràng**, ví dụ thay yêu cầu “tính chính xác tuyệt đối” bằng “sai số tương đối dưới ngưỡng X trên miền Y”, với phương pháp đánh giá và tình huống không đáp ứng đã thống nhất.

Khi đề xuất như vậy, cần ghi rõ:

1. **Trước:** quy tắc đầu vào, output và lỗi hiện tại.
2. **Sau:** kiểu/precision/rounding/sai số mới, những đầu vào thay đổi kết quả.
3. **Ảnh hưởng:** tiền, thuế, giới hạn quota, quyết định so sánh, downstream API, định dạng và audit.
4. **Mức độ chấp nhận:** ai có quyền phê duyệt việc đổi hợp đồng; quy trình migration/backfill nếu dữ liệu đã lưu.
5. **Bằng chứng:** test đối chiếu, phân tích sai số và benchmark trên workload thực.
6. **Phương án phục hồi:** cờ cấu hình, version API hoặc rollback nếu cần.

Nếu chưa có yêu cầu hoặc phê duyệt thay đổi hợp đồng, giữ nguyên tiêu chuẩn cũ và tìm những tối ưu an toàn hơn. Không âm thầm đánh đổi tính chính xác để đạt một ngưỡng throughput hoặc giảm vài mili giây.

## 12. Những sai lầm thường gặp

| Sai lầm khi tối ưu | Hậu quả | Hướng đúng |
| --- | --- | --- |
| Thay `BigDecimal` bằng `double` không đối chiếu yêu cầu | Khác giá trị thập phân, rounding/overflow/NaN | Xác nhận sai số cho phép trước; giữ BigDecimal nếu cần chính xác |
| Hạ `MathContext` để làm nhanh | Mất chữ số có nghĩa ở phép tính hiện tại và bước sau | Giữ precision theo hợp đồng; tối ưu chi phí khác |
| Di chuyển `setScale` từ cuối sang trong vòng lặp | Đổi vị trí rounding, sai tổng | Giữ nguyên ranh giới làm tròn |
| Đổi `HALF_EVEN` sang `HALF_UP` tùy tiện | Khác kết quả tại điểm giữa | Rounding mode theo nghiệp vụ |
| Bỏ `UNNECESSARY` | Chấp nhận input trước đây phải từ chối | Bảo toàn validation |
| Đổi `BigInteger` sang `long` không xét miền | Tràn số | Chỉ đổi khi chứng minh miền, dùng checked arithmetic |
| Dùng `int` cho tổng lớn vì ít tốn bộ nhớ | Tràn miền `int` | Tính đúng kích thước miền từ input và tổng |
| Đưa reduction `double` sang song song rồi đòi output trùng bit | Thứ tự cộng khác, kết quả khác | Đặc tả thứ tự hoặc dung sai hợp lệ |
| `stripTrailingZeros` mọi nơi | Mất scale/biểu diễn cần thiết | Chuẩn hóa chỉ ở ranh giới có hợp đồng tương ứng |
| `BigDecimal.valueOf(doubleSum)` để “sửa” sai số double | Chỉ chuyển sai số đã có sang dạng thập phân | Tính trực tiếp từ dữ liệu decimal chính xác |
| Chỉ benchmark mà không test số | Tối ưu sai có thể nhanh hơn | Đặt correctness gate trước performance gate |
| Đo một lượt bằng `nanoTime()` | Dễ nhiễu bởi JIT/GC/OS | Benchmark có warmup, fork, nhiều mẫu và workload thích hợp |
| Benchmark cả parse bản cũ nhưng bỏ parse ở bản mới | So sánh không công bằng | Đo cùng ranh giới xử lý và cùng input |
| Dựa vào kết quả đầu ra trên vài số “đẹp” | Không thấy lỗi ở ranh giới | Test số âm, zero, large, tiny, ties, overflow, precision |

## 13. Bài tập áp dụng

1. Đội phát triển muốn thay mọi `BigDecimal` trong hệ thống tính thuế bằng `double` vì benchmark nhanh hơn. Bạn cần hỏi và kiểm chứng những điều gì trước khi chấp nhận?
2. Có 1.000 dòng dữ liệu, mỗi dòng `0.005` đơn vị. Tính tổng theo chính sách “round từng dòng bằng HALF_UP” và “round một lần ở tổng”. Hai kết quả khác nhau thế nào? Chính sách nào đúng nếu hóa đơn được quy định là tổng của các dòng đã làm tròn?
3. Một hàm `sum` đang dùng `Math.addExact` để từ chối overflow. Đổi sang phép cộng `+` thông thường có giữ hợp đồng không? Hãy lập case biên.
4. Một chỉ số số thực được tổng hợp bằng `double`; bản mới dùng `parallelStream()` cho phép cộng. Vì sao khác biệt chữ số cuối không phải lúc nào cũng là bug, nhưng cũng không thể tự bỏ qua?
5. Bạn có thể dùng `long` đếm xu thay `BigDecimal` trong hệ thống giá tiền khi nào? Hãy liệt kê quy tắc chuyển đổi, overflow và rounding.
6. Nếu dữ liệu `"1.0"` và `"1.00"` dùng làm khóa `HashMap`, liệu thay bằng `stripTrailingZeros()` trong đường xử lý mới có thể ảnh hưởng kết quả tra cứu không?
7. Lên kế hoạch benchmark cho một hàm xử lý 10 triệu giá trị, phân biệt chi phí parse, arithmetic, logging và serialization; đề xuất test hợp đồng trước khi đo.

**Gợi ý:** (1) xác định rounding, scale, đơn vị, sai số, miền, serializing và đầu vào biên; không dựa duy nhất vào benchmark. (2) từng dòng thành `0.01` nên tổng `10.00`; làm tròn tổng `5.000` thành `5.00`. Nếu hóa đơn quy định cộng các dòng đã làm tròn, đáp án đúng là `10.00`. (3) không giữ hợp đồng: `Long.MAX_VALUE + 1` có thể quay vòng với `+`, nhưng `addExact` ném `ArithmeticException`. (4) phép cộng `double` không kết hợp, chỉ chấp nhận khác biệt nếu hợp đồng sai số/tái lập cho phép. (5) scale cố định, miền đủ, conversion chính xác, checked arithmetic và quy tắc làm tròn minh bạch. (6) có thể vì `equals/hashCode` xem scale như một phần của identity. (7) đo cùng pipeline, có warmup/fork; so correctness với oracle trước.

## 14. Ghi nhớ cho quyết định hiệu năng

Một tối ưu số học thành công phải có **hai bằng chứng độc lập**: (1) kết quả, lỗi và biểu diễn đáp ứng hợp đồng đã định; (2) thời gian hoặc tài nguyên thực sự được cải thiện trong workload đại diện. Những thay đổi như giảm precision, đổi kiểu số, đổi điểm rounding hay bỏ overflow check có thể làm thay đổi bài toán; không thực hiện chỉ để giảm thời gian.

Ưu tiên cải thiện thuật toán, tránh chuyển đổi thừa, cô lập format/I/O, lựa chọn cấu trúc dữ liệu phù hợp, cache kết quả thuần túy với khóa đầy đủ, và benchmark có kiểm soát. Khi cần đánh đổi độ chính xác, định nghĩa đó là **thay đổi hợp đồng có chủ đích**, với phạm vi áp dụng, sai số, test và phê duyệt rõ ràng.

Tài liệu Java 17 tham khảo:
- [`BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [`MathContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html), [`RoundingMode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html).
- [`BigInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html), [`Math`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html), [`Double`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html).
- [Java Language Specification §15, §17](https://docs.oracle.com/javase/specs/jls/se17/html/index.html) cho quy tắc biểu thức số và thực thi Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-007">Benchmark cần tránh constant folding và dead-code elimination</a>

<details>
<summary>Xem chi tiết</summary>

# Benchmark Java: tránh constant folding và dead-code elimination

Một phép toán Java chạy mất "0 ns" hoặc nhanh hơn cả việc đọc một biến chưa chắc là thuật toán đã được tối ưu xuất sắc. Đôi khi JVM đã chứng minh **đầu ra luôn là hằng số** rồi thay cả phép tính bằng một giá trị có sẵn (*constant folding*), hoặc đã chứng minh **kết quả không ảnh hưởng điều gì có thể quan sát** rồi loại phép tính (*dead-code elimination*, DCE). Nếu mục tiêu là đo chi phí của một phép tính thực sự được ứng dụng sử dụng, hai tối ưu hợp lệ của JVM này có thể làm benchmark đo sai đối tượng.

Bài học thuộc chủ đề **Hiệu năng** trong Java Core - Numbers. Mục tiêu là hiểu khi nào benchmark số học bị JVM tối ưu mất, cách thiết kế đầu vào/đầu ra để đo đúng công việc, cách dùng **Java Microbenchmark Harness (JMH)** và cách đọc kết quả thận trọng. Bài dùng cú pháp Java 17+, giải thích cả trường hợp mã “chạy đúng” nhưng phép đo không còn đại diện cho workload thật.

## 1. Benchmark đo điều gì?

Một *microbenchmark* cô lập một thao tác nhỏ, chẳng hạn phép nhân `long`, hàm chuyển đổi số, `BigDecimal.add()`, hoặc một hàm kiểm tra điều kiện. Mục tiêu là ước lượng chi phí và/hoặc thông lượng khi JVM đã thực thi mã trong bối cảnh tương ứng.

Một phép đo chỉ có ý nghĩa khi ta trả lời được:

1. **Tác vụ được đo:** chính xác phương thức và đầu vào nào?
2. **Kết quả có được sử dụng?** Trong ứng dụng thật, phép tính có tác động đến kết quả trả về, dữ liệu lưu trữ, hoặc trạng thái quan sát được không?
3. **Đầu vào thay đổi ở mức nào?** Một hằng số trong bytecode, trường `final`, trường trạng thái được gán lúc chạy, hay dữ liệu thật?
4. **Có đang đo cả thứ khác không?** Tạo đối tượng, đọc I/O, cấp phát, khởi tạo thư viện, chuyển đổi dữ liệu và gọi hàm có thể đã bị trộn vào thời gian tính toán.
5. **JIT đã warm up chưa?** JVM có thể diễn dịch và biên dịch tối ưu cùng phương thức ở các giai đoạn khác nhau; lần đầu chạy thường không đại diện cho trạng thái ổn định.
6. **Chi phí đo và điều kiện chạy:** có fork JVM độc lập, warmup, chu kỳ đo, GC, CPU contention và cách xử lý thống kê hay chưa?

Đơn vị báo cáo `ns/op` nghĩa là **nanosecond trên một đơn vị công việc benchmark** theo cấu hình harness. Nó không tự chứng minh đồng hồ đo có độ phân giải một nanosecond hay thao tác tương đương sẽ mất chừng ấy thời gian trong mọi ứng dụng.

## 2. JVM được phép tối ưu những gì?

Java cho phép compiler/JIT thực hiện những biến đổi giữ nguyên **hành vi quan sát được** của chương trình. Đối với các phép tính thuần túy, không có tác động phụ, việc bỏ đi hoặc thay bằng hằng số là hợp lệ khi nó không làm thay đổi đầu ra cần quan sát.

Mô hình đơn giản:

~~~text
Java source
    |
    v
javac tạo bytecode
    |
    v
JVM diễn dịch / biên dịch JIT các đoạn nóng
    |
    +--> constant propagation / constant folding
    +--> inline method / hoist loop-invariant work
    +--> dead-code elimination / escape analysis
    |
    v
Machine code được thực thi
~~~

Đây là sơ đồ khái niệm; vị trí và mức độ tối ưu thay đổi giữa JVM, phiên bản, workload và profile chạy. `javac` có thể rút gọn một số biểu thức hằng từ thời điểm biên dịch, còn JIT có thể tối ưu sau khi đã biết thêm thông tin về các lời gọi thực tế.

**Benchmark cần đo mã *sau tối ưu*** vì đó là mã ứng dụng thực sự chạy. Ta không cố “tắt mọi tối ưu”. Ta cần tạo bối cảnh đầu vào và đầu ra **đúng với tình huống thực tế**, để JIT không biến mất chính công việc mà benchmark giả định đang đo.

## 3. Constant folding: khi cả phép tính chỉ còn hằng số

*Constant folding* xảy ra khi compiler biết các toán hạng và có thể xác định kết quả trước, ví dụ:

~~~java
int result = 12 * 12; // Kết quả 144 có thể được tính sẵn.
~~~

Đối với benchmark, tình huống nguy hiểm là đưa toàn bộ đầu vào như hằng số:

~~~java
static long compute(long x) {
    long result = x;
    for (int i = 0; i < 8; i++) {
        result = (result * 31 + i) ^ (result >>> 3);
    }
    return result;
}

// Phép tính với đầu vào cố định:
long result = compute(7L);
~~~

Nếu JIT inline `compute` rồi suy luận được vòng lặp và `x = 7L` không đổi, nó **có thể** precompute hoặc rút gọn phần lớn công việc. Một hàm có nhiều phép nhân, phép cộng và XOR ở mã nguồn không bảo đảm số lượng lệnh máy tương ứng thực sự được chạy ở mỗi iteration đo.

Lưu ý ba cấp độ:

- **Constant folding:** rút gọn một biểu thức khi đã biết đầu vào.
- **Constant propagation:** truyền thông tin về giá trị hằng qua biến hoặc lời gọi, mở cơ hội folding.
- **Loop-invariant code motion:** đưa một phép tính không đổi giữa các vòng ra ngoài vòng lặp khi hợp lệ; benchmark thủ công lặp lại cùng phép tính có thể thực hiện công việc chỉ một lần.

Một số tối ưu có thể diễn ra ngay trong `javac`, số khác do JIT quyết định. Không nên nói rằng JVM **luôn** tối ưu mọi hàm thuần thành hằng; đúng hay không phụ thuộc cấu trúc chương trình, khả năng inline, điều kiện chạy và trình biên dịch. Nguy cơ benchmark là **JVM được phép và có cơ hội làm điều đó**, nên kết quả cực nhanh chưa đủ chứng minh phép tính đã được thực hiện như mong đợi.

## 4. Dead-code elimination: tính xong nhưng không ai dùng

*Dead-code elimination* (DCE) loại những thao tác mà kết quả của chúng không được chương trình quan sát. Ví dụ:

~~~java
static long compute(long x) {
    return (x * 31 + 17) ^ (x >>> 3);
}

static void wrongBenchmark(long input) {
    compute(input); // Kết quả bị bỏ; không có side effect.
}
~~~

Đoạn trên là minh họa **không phải chương trình hoàn chỉnh**. Nếu `compute` được inline, JVM có thể nhận ra phép tính không thay đổi trạng thái quan sát được rồi xóa toàn bộ. Benchmark có thể chủ yếu đo overhead của harness hoặc biến đổi còn sót lại, không đo chi phí phép toán.

Điểm cần hiểu: **đầu vào biến đổi vẫn chưa đủ** nếu đầu ra bị bỏ. Ngược lại, **đầu ra được dùng vẫn chưa đủ** nếu toàn bộ công việc có thể được thay bằng kết quả hằng. Một benchmark tốt phải bảo vệ cả hai đầu:

~~~text
Dữ liệu đầu vào thực tế, không bị compiler coi là hằng
                        |
                        v
                   Công việc đo
                        |
                        v
Kết quả được dùng/tiêu thụ theo cách harness có thể quan sát
~~~

Đừng sửa bằng cách thêm `System.out.println(result)` **trong chính vòng benchmark**: I/O console đắt và biến động hơn nhiều phép toán số học, khiến phép đo trở thành benchmark in log. Đừng dùng `Thread.sleep()` để làm số đo “thấy rõ”: nó đo việc chờ và scheduler, không đo phép tính.

## 5. Hai lỗi có thể cùng xuất hiện

Xét một benchmark thủ công:

~~~java
long start = System.nanoTime();

for (int i = 0; i < 1_000_000; i++) {
    long result = compute(7L);
}

long elapsed = System.nanoTime() - start;
System.out.println(elapsed);
~~~

Mã có thể báo số rất nhỏ hoặc thay đổi thất thường dù một triệu lượt lặp xuất hiện trong source:

- `result` không được dùng; JVM có thể loại việc tính `compute`.
- Đầu vào `7L` cố định; compiler có thể rút gọn hoặc đưa công việc ra ngoài vòng lặp.
- Việc tự đo bằng `nanoTime()` thiếu warmup/fork kiểm soát, mắc nhiễu JIT/GC/OS scheduling; timing overhead có thể đáng kể so với phép toán nhỏ.
- Nếu thay kết quả bằng một biến cộng dồn dùng ở cuối, JIT vẫn có thể biến đổi phép tính lặp, và khi phép toán quá nhỏ thì chính vòng lặp/accumulator trở thành phần lớn chi phí.
- Nếu đo mà in kết quả trong mỗi lượt, chi phí I/O áp đảo phép tính.

Không kết luận "phép nhân Java tốn 0 ns" từ số đo này. Có thể **phép nhân không còn nằm trong đoạn máy được thực thi để đo**.

## 6. JMH: harness dành cho microbenchmark JVM

**Java Microbenchmark Harness (JMH)** là dự án OpenJDK được tạo để thiết kế và chạy microbenchmark trên JVM, có hỗ trợ warmup, measurement iterations, fork JVM, chế độ đo và cơ chế tiêu thụ kết quả. Với benchmark hiệu năng Java nhỏ, đây là điểm bắt đầu đáng tin cậy hơn vòng lặp `nanoTime()` tự viết.

JMH giúp kiểm soát điều kiện đo, **không thể tự biết nghiệp vụ muốn đo gì**. Nếu tác giả đưa đầu vào hằng, bỏ kết quả hoặc đo sai công việc, JMH vẫn có thể chạy benchmark đó và trả số đo hợp lệ cho đoạn mã *thực sự được thực thi*. Thiết kế workload vẫn là trách nhiệm của người viết benchmark.

Để có cấu hình annotation processing và đóng gói chuẩn, dùng Maven archetype của JMH tạo một dự án biệt lập. Ví dụ dùng JMH 1.37:

~~~bash
mvn archetype:generate -B -DinteractiveMode=false -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DarchetypeVersion=1.37 -DgroupId=example.bench -DartifactId=jmh-demo -Dversion=1.0-SNAPSHOT
cd jmh-demo
~~~

Lệnh cần Maven và khả năng tải dependency khi chưa có cache. Dự án tạo ra có `pom.xml`, mã mẫu và cấu hình JMH. Những ví dụ tiếp theo đặt trong `src/main/java/example/bench/`. Phiên bản `1.37` là phiên bản đã được đối chiếu trong Maven Central; khi dự án có quy trình quản lý phiên bản dependency riêng, sử dụng phiên bản phù hợp được kiểm thử trong môi trường của dự án.

Không chạy `@Benchmark` như một hàm thông thường rồi tự gọi `System.nanoTime()` quanh nó; như vậy không sử dụng các tính năng của harness.

## 7. Ví dụ JMH đầy đủ: giữ đầu vào runtime và dùng kết quả

Tạo `src/main/java/example/bench/ComputeBench.java` trong dự án archetype:

~~~java
package example.bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ComputeBench {

    @Param({"7", "11", "19"})
    public long input;

    private static long compute(long x) {
        for (int i = 0; i < 8; i++) {
            x = (x * 31 + i) ^ (x >>> 3);
        }
        return x;
    }

    @Benchmark
    public long resultReturned() {
        return compute(input);
    }

    @Benchmark
    public void resultConsumed(Blackhole blackhole) {
        blackhole.consume(compute(input));
    }
}
~~~

Biên dịch/đóng gói bằng Maven trong thư mục dự án:

~~~bash
mvn clean verify
java -jar target/benchmarks.jar ComputeBench -t 1
~~~

**Giải thích những điểm chống benchmark sai:**

1. `@State(Scope.Thread)` cung cấp một trạng thái trên mỗi thread benchmark. Ta đo trên đối tượng do JMH quản lý, không phụ thuộc một biến cục bộ có thể được compiler nhận ngay là hằng.
2. `@Param({"7", "11", "19"})` yêu cầu JMH chạy các trường hợp tham số. Trường `input` không khai báo `final` và được harness gán tại runtime. Đây là cách tránh việc đưa trực tiếp cùng một literal vào phép tính có thể gập thành hằng. Nếu ứng dụng thật chỉ có vài hằng và JIT thực sự được phép gập, benchmark đó cần mục tiêu khác, không nên cố “cản JIT” ngoài ngữ cảnh.
3. `resultReturned()` trả kết quả từ `@Benchmark`. JMH **tiêu thụ kết quả trả về** để hạn chế DCE; trả một giá trị khác hoặc một hằng giả thay cho kết quả thật sẽ không đạt mục tiêu.
4. `resultConsumed(Blackhole)` minh họa cách chuyển kết quả trực tiếp vào `Blackhole.consume(...)` khi phương thức benchmark cần kiểu `void` hoặc xử lý nhiều kết quả trung gian. JMH sử dụng cơ chế này để hạn chế compiler coi giá trị đầu ra là không cần thiết.
5. `@Warmup` và `@Measurement` tách giai đoạn khởi động tối ưu và giai đoạn lấy số liệu; `@Fork(2)` chạy hai JVM fork độc lập để giảm phụ thuộc trạng thái một phiên chạy.
6. `@BenchmarkMode(Mode.AverageTime)` cùng `@OutputTimeUnit(NANOSECONDS)` yêu cầu JMH báo thời gian trung bình trên một operation. Đây **không phải** hứa hẹn latency từng lời gọi là chính xác một số nano giây cố định.

Hai phương thức dùng hai cách khác nhau để giữ kết quả; chúng **không được kỳ vọng cho số đo giống hệt từng chữ số**, vì đường đi tiêu thụ kết quả/harness có overhead và đặc tính tối ưu khác nhau. Mục tiêu bài là thiết kế một phép đo không vô tình bỏ toàn bộ công việc. Kết quả thời gian tùy CPU, JVM, môi trường và phiên chạy; **không cung cấp bảng số ns/op giả định**.

Nếu Maven build báo không tìm `org.openjdk.jmh.generated` hay benchmark không được đăng ký, hãy kiểm tra annotation processor JMH trong cấu hình archetype/build. Chỉ chép lớp `ComputeBench` vào một chương trình Java thường rồi gọi `main` không đủ để JMH tự chạy.

## 8. Phân biệt ví dụ sai và ví dụ đúng

Các đoạn sau là **minh họa về vấn đề**, không phải các benchmark JMH độc lập nên chạy để rút kết luận hiệu năng.

### Sai A: dùng đầu vào hằng

~~~java
@Benchmark
public long foldable() {
    return compute(7L);
}
~~~

Dù trả kết quả về harness, JIT vẫn có thể inline và constant-fold `compute(7L)`. Đầu ra đã được sử dụng **nhưng công việc cần đo có thể đã bị gập**. So với ví dụ đúng `compute(input)` mà `input` là trường trạng thái runtime.

### Sai B: bỏ kết quả

~~~java
@Benchmark
public void unused() {
    compute(input);
}
~~~

Dù `input` đến từ một trạng thái runtime, phép tính thuần không có hiệu ứng quan sát khi kết quả bị bỏ. DCE có thể loại bỏ nó. So với ví dụ đúng `return compute(input)` hoặc `blackhole.consume(compute(input))`.

### Sai C: tạo hằng với `final`

~~~java
private static final long FIXED = 7L;

@Benchmark
public long foldedConstant() {
    return compute(FIXED);
}
~~~

Khai báo `final` thường có ý nghĩa tốt trong mã ứng dụng; nhưng khi mục tiêu benchmark là **tính toán với dữ liệu thay đổi**, `static final` và literal tạo điều kiện cho folding. Không “cấm final” ở mọi nơi; hãy phân biệt dữ liệu benchmark phải động với các thông số thực sự cố định theo hợp đồng ứng dụng.

### Đúng theo mục tiêu đo dữ liệu runtime

~~~java
@State(Scope.Thread)
public static class Inputs {
    @Param({"7", "11", "19"})
    public long value;
}

@Benchmark
public long runtimeInput(Inputs inputs) {
    return compute(inputs.value);
}
~~~

Đoạn này cần nằm trong lớp benchmark có phương thức `compute` và các import JMH tương ứng. `@Param`/`@State` không bảo đảm tuyệt đối mọi phép tính đều còn tồn tại ở máy; chúng giúp benchmark phản ánh dữ liệu được cung cấp ở runtime và giảm nguy cơ gập thành hằng. Kiểm chứng đầu ra máy khi phép đo cực nhỏ hoặc bất thường vẫn có giá trị.

## 9. Chương trình Java 17 độc lập để kiểm tra chức năng, không đo hiệu năng

Trước khi đo thời gian, hãy chứng minh hàm tính toán **thực sự phụ thuộc đầu vào** và kết quả kỳ vọng là gì. Ví dụ đơn giản không phụ thuộc JMH:

Tạo tệp `BenchmarkPitfallsDemo.java`:

~~~java
public final class BenchmarkPitfallsDemo {
    private static volatile long sink;

    static long compute(long x) {
        for (int i = 0; i < 8; i++) {
            x = (x * 31 + i) ^ (x >>> 3);
        }
        return x;
    }

    public static void main(String[] args) {
        long a = compute(7);
        long b = compute(11);
        sink = a;
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        System.out.println("different results = " + (a != b));
        System.out.println("sink = " + sink);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 BenchmarkPitfallsDemo.java
java BenchmarkPitfallsDemo
~~~

Kết quả xác định cho các giá trị minh họa:

~~~text
a = 5985353341353
b = 9465354105287
different results = true
sink = 5985353341353
~~~

Chương trình chứng minh hàm `compute` có hai kết quả khác nhau theo hai đầu vào; biến `volatile sink` cho phép một cách sử dụng kết quả có thể quan sát. **Đây không phải benchmark thời gian.** Việc thêm `volatile` không thay thế warmup, fork, thống kê và cơ chế chống compiler tối ưu sai ngữ cảnh của JMH. Nếu viết thêm vòng lặp đo quanh chương trình này, vẫn phải phân tích các tối ưu và chi phí truy cập `volatile` riêng.

## 10. Blackhole: tiêu thụ kết quả đúng cách

JMH cung cấp `org.openjdk.jmh.infra.Blackhole`. Ta có thể dùng:

~~~java
@Benchmark
public void benchmark(Blackhole blackhole) {
    long value = compute(input);
    blackhole.consume(value);
}
~~~

Nếu benchmark là `public long benchmark()`, chỉ cần `return compute(input)` thường là đủ: JMH xử lý giá trị trả về. Blackhole hữu ích hơn khi một iteration tính nhiều kết quả hoặc thao tác benchmark không có kiểu trả về thuận tiện.

**Không gọi `Blackhole.consumeCPU(...)` chỉ để đảm bảo CPU làm việc**: phương thức này được dùng cho những mục tiêu mô phỏng tải CPU riêng; nó không chứng minh công việc cần đo đã chạy và có thể tạo thêm chi phí không thuộc workload.

**Không tự viết một biến static `volatile` thành giải pháp phổ quát.** Ghi volatile có chi phí, hiệu ứng bộ nhớ và cạnh tranh trạng thái khi nhiều thread dùng chung. Nó có thể làm kết quả so sánh hai thuật toán lệch theo chi phí phụ do chính benchmark thêm vào.

**Không in kết quả trong `@Benchmark`** và tránh cập nhật shared `List`/`Map` chỉ để giữ kết quả nếu ứng dụng thật không yêu cầu hoạt động đó. Việc làm giả “observable use” có thể đo cả allocation, synchronization và contention không liên quan. Chọn `return` hoặc `Blackhole` trước, sau đó xác minh JVM không loại/biến đổi phần quan trọng.

## 11. @State, @Param và dữ liệu đầu vào thực tế

Một benchmark đáng tin cậy phải phản ánh **phân bố đầu vào** của ứng dụng, không chỉ một hằng thuận tiện.

`@Param` cho phép báo cáo nhiều trường hợp, ví dụ các số nguyên nhỏ, lớn, âm và gần biên. Với bài toán số học, thời gian có thể phụ thuộc số chữ số hoặc kích thước nội bộ, đặc biệt với `BigInteger` và `BigDecimal`. Đo một đối tượng đặc biệt như `BigInteger.ONE` rồi suy ra hiệu năng cho hàng nghìn chữ số là thiếu căn cứ.

`@State(Scope.Thread)` tạo state riêng trên mỗi benchmark thread; `Scope.Benchmark` tạo state chia sẻ trong phạm vi benchmark theo hợp đồng JMH, phù hợp nếu bạn **thực sự muốn đo contention**. Nếu mục tiêu là tốc độ tính toán đơn luồng, vô tình dùng shared mutable state có thể khiến ta đo tranh chấp thay vì thuật toán.

Với dữ liệu số phức tạp, có thể dùng `@Setup(Level.Trial)` để chuẩn bị dữ liệu ngoài đường đo:

~~~java
@State(Scope.Thread)
public static class DecimalInputs {
    @Param({"16", "128"})
    public int digits;

    public java.math.BigInteger value;

    @Setup(Level.Trial)
    public void setup() {
        value = java.math.BigInteger.TEN.pow(digits)
                .add(java.math.BigInteger.valueOf(12345));
    }
}
~~~

Đoạn này cần các import JMH `@State`, `Scope`, `@Param`, `@Setup` và `Level`. Không xây dựng `BigInteger` lớn bên trong mỗi `@Benchmark` nếu mục tiêu là **chỉ đo phép toán trên số đã có sẵn**; còn nếu ứng dụng thật phải parse/chuyển đổi số trong mỗi tác vụ, việc đưa công đoạn đó vào lại hợp lý. Hãy phát biểu phạm vi đo trong tên benchmark và giải thích.

Đừng chuyển mọi đầu vào thành `final static` để giảm noise nếu bạn cần biết hiệu năng trên dữ liệu runtime. Và đừng cập nhật tham số theo một quy luật giả tạo ngay trong mỗi invocation nếu chính việc cập nhật tham số sẽ áp đảo phép tính nhỏ được đo.

## 12. Warmup, fork và các mode đo

Khi chạy Java, JVM có thể trải qua thông dịch, compiler tầng khác nhau, thu thập profile, inline, cache warming, tải class và GC. Nếu đo vài lời gọi ban đầu rồi chia thời gian, kết quả phụ thuộc mạnh vào trạng thái khởi tạo.

Các thuộc tính JMH quan trọng:

| Cấu hình | Ý nghĩa |
|---|---|
| `@Warmup(iterations = 3, time = 1)` | Chạy các khoảng warmup không dùng làm kết quả cuối |
| `@Measurement(iterations = 5, time = 1)` | Lấy 5 khoảng đo trong mỗi fork |
| `@Fork(2)` | Chạy hai JVM fork độc lập |
| `@BenchmarkMode(Mode.AverageTime)` | Báo thời gian trung bình trên operation |
| `@BenchmarkMode(Mode.Throughput)` | Báo số operation mỗi đơn vị thời gian |
| `@OutputTimeUnit(TimeUnit.NANOSECONDS)` | Chỉ định đơn vị báo cáo |
| `-t 1` | Chạy một benchmark thread, tránh trộn contention vào phép đo đơn luồng |

Ví dụ chạy JMH sau khi build có thể ghi đè cấu hình annotation:

~~~bash
java -jar target/benchmarks.jar ComputeBench -wi 3 -i 5 -w 1s -r 1s -f 2 -t 1
~~~

Các tùy chọn này chọn warmup 3 iteration, measurement 5 iteration, mỗi khoảng một giây, hai fork và một thread. Điều kiện CPU, nhiệt độ, hệ điều hành, power plan và workload cạnh tranh đều có thể tác động số đo.

JMH output thường có tên benchmark, số lượng mẫu, mode, score, error và units. `Score Error` là chỉ báo bất định theo cách tính của harness, **không có nghĩa tất cả phép đo của ứng dụng ngoài đời đều nằm trong đúng khoảng đó**. Khi khác biệt nhỏ hơn noise hoặc phụ thuộc hệ thống, không tuyên bố chiến thắng hiệu năng chỉ từ một lần benchmark.

## 13. Các tối ưu khác cũng làm benchmark lệch mục tiêu

Constant folding và DCE là hai ví dụ nổi bật, nhưng microbenchmark Java còn gặp:

- **Inlining:** JIT thay lời gọi method bằng thân hàm. Đó là tối ưu thực tế cần cân nhắc; nếu benchmark cố đo “overhead lời gọi”, hãy xác định ứng dụng có thực sự không được inline hay không.
- **Escape analysis và scalar replacement:** object được tạo nhưng không “thoát” có thể được JVM loại allocation hoặc thay bằng trường scalar. Nếu đo allocation thật, bảo đảm tình huống object sống/thoát phù hợp ứng dụng, nhưng đừng cố tình giữ object sai mục tiêu.
- **Loop unrolling và loop-invariant hoisting:** vòng lặp lặp lại cùng đầu vào có thể được rút gọn/đưa phép tính ra ngoài. JMH thường điều khiển lặp nội bộ, nên tránh tự thêm vòng `for` lớn vào benchmark nếu không thật sự đang đo batch.
- **Profile-guided/speculative optimization:** JVM tối ưu theo kiểu/nhánh thường gặp; đầu vào benchmark quá đồng nhất có thể khiến branch prediction và việc inline thuận lợi hơn workload thực tế.
- **Cấp phát và GC:** benchmark tạo mảng hoặc `BigDecimal` mới có thể có chi phí garbage collection không đều giữa lần chạy. Chọn đo cả cấp phát hay chỉ xử lý dữ liệu có sẵn.
- **Cache locality và memory layout:** dữ liệu nhỏ luôn nằm trong cache có thể tạo kết quả khác hẳn input lớn trên máy thật.
- **Autoboxing:** đo `Long`/`Integer` trong khi ứng dụng sử dụng primitive hoặc ngược lại sẽ pha thêm chi phí không mong muốn.

Các tối ưu này **không xấu**. Chúng làm benchmark lệch khi thiết lập dữ liệu hoặc việc sử dụng kết quả khác với mục tiêu cần đo. Mục tiêu là có một phép đo tương ứng với workload, không phải chống compiler bằng mọi giá.

## 14. Ví dụ với Numbers: đo BigDecimal đúng phạm vi

Giả sử cần so sánh chi phí `BigDecimal.add()` và `BigDecimal.multiply()`. Tạo hai đối tượng từ chuỗi bên trong mỗi `@Benchmark` sẽ khiến thời gian bao gồm **parse decimal + cấp phát + phép toán**, khác với trường hợp ứng dụng đã có sẵn dữ liệu.

Thiết kế gợi ý:

~~~java
@State(Scope.Thread)
public static class NumberState {
    @Param({"12.345", "123456789.012345"})
    public String text;

    public BigDecimal left;
    public BigDecimal right;

    @Setup(Level.Trial)
    public void setup() {
        left = new BigDecimal(text);
        right = new BigDecimal("7.8901");
    }
}

@Benchmark
public BigDecimal add(NumberState state) {
    return state.left.add(state.right);
}

@Benchmark
public BigDecimal multiply(NumberState state) {
    return state.left.multiply(state.right);
}
~~~

Đây là **đoạn minh họa cần đặt trong lớp JMH** với import `BigDecimal`, các annotation JMH và `Level`; không phải tệp Java chạy trực tiếp. Bên ngoài đường đo, `@Setup(Level.Trial)` dựng dữ liệu để benchmark phản ánh phép toán trên các `BigDecimal` đã tồn tại. Kết quả `BigDecimal` được trả về và JMH tiêu thụ, tránh để DCE xóa thao tác.

Tuy nhiên, chọn trước `left/right` cố định có thể giúp JIT khai thác thêm đặc tính dữ liệu và phép toán tùy cấu trúc mã; nó không tự chứng minh benchmark bao trùm mọi giá trị thực tế. Để đo phân bố đầu vào lớn/nhỏ, âm/dương hoặc các `scale` khác nhau, cần nhiều case và dữ liệu thay đổi hợp lý theo nghiệp vụ. Khi muốn so sánh bộ xử lý, luôn kiểm chứng phép toán thực sự được tính chứ không đã được “trả về từ cache” theo một đặc điểm riêng của input.

## 15. Checklist đánh giá một microbenchmark

Trước khi tin kết luận hiệu năng, kiểm tra lần lượt:

1. **Định nghĩa phép toán:** Bạn đang đo hàm thuần, parse, khởi tạo, I/O, đồng bộ hay cả quy trình?
2. **Chặn constant folding không phù hợp:** Dữ liệu ứng dụng thật có biến đổi không? Nếu có, tham số benchmark có đến từ state runtime (ví dụ `@Param`) thay vì literal hoặc `static final` không?
3. **Chặn DCE:** Kết quả của công việc cần đo có được `return` hoặc `Blackhole.consume` trong JMH không? Có side effect phù hợp thực tế không?
4. **Không đưa chi phí ngoại lai vào đường đo:** In console, sleep, cấp phát test fixture, tạo RNG, parse không cần thiết hoặc cập nhật global `volatile` có đang chiếm thời gian không?
5. **Dùng JMH đúng cách:** Annotation processor hoạt động, benchmark chạy qua harness, có warmup, measurement, fork phù hợp.
6. **Đảm bảo chức năng:** Kết quả tính toán với các đầu vào có thể kiểm chứng; benchmark sai logic nhưng rất nhanh không có giá trị.
7. **Xem xét tối ưu máy:** Với phép đo quá nhanh/bất thường, có thể kiểm tra profiling/disassembly nếu môi trường hỗ trợ để hiểu mã máy và tỷ lệ công việc bị bỏ; không suy đoán từ số ns/op một mình.
8. **Kiểm soát nguồn nhiễu:** JVM/JDK version, CPU, GC, load hệ thống, mức song song và điều kiện nhiệt/điện phải được ghi lại.
9. **Không nhầm khác biệt ngắn hạn với cải tiến:** Chạy lại khi có lý do, xem sai số và phạm vi ứng dụng; báo kết quả kèm điều kiện, không suy ra một con số tuyệt đối cho mọi máy.
10. **So sánh công bằng:** Hai phương án phải cùng đầu vào, cùng tiêu chí đầu ra đúng, cùng loại chi phí được tính. Không để một bên thực hiện ít công việc hơn chỉ nhờ benchmark quên tiêu thụ kết quả.

Không có một “anti-optimizer trick” duy nhất đủ cho mọi bài benchmark. `Blackhole` giúp hạn chế DCE, `@State`/`@Param` giúp cung cấp runtime input, còn **thiết kế thí nghiệm** quyết định tính đại diện cho bài toán.

## 16. Bài tập dự đoán và sửa lỗi

**Bài 1 — Kết quả bị bỏ.** Phương thức dưới đây sai ở đâu?

~~~java
@Benchmark
public void test() {
    expensivePureCalculation(input);
}
~~~

**Đáp án:** Nếu hàm không có side effect, đầu ra bị bỏ nên JVM có thể DCE. Đổi thành benchmark trả kết quả hoặc truyền kết quả vào `Blackhole.consume`. Đồng thời kiểm tra `input` không là hằng số ngoài ý muốn.

**Bài 2 — Có return vẫn có thể sai.**

~~~java
@Benchmark
public long test() {
    return compute(123L);
}
~~~

**Đáp án:** Trả kết quả giúp chống DCE nhưng `123L` cố định tạo điều kiện cho constant folding, inlining và hoisting. Nếu ứng dụng xử lý nhiều số runtime, dùng `@State` và `@Param` hoặc dữ liệu runtime tương ứng.

**Bài 3 — Dùng volatile là đủ chưa?** Một lập trình viên thay `Blackhole` bằng `private static volatile long sink;`, rồi ghi `sink = compute(7L);` sau mỗi lần gọi. Đã an toàn để so sánh hiệu năng chưa?

**Đáp án:** Chưa. Phép tính với literal vẫn có thể gập thành hằng; ghi volatile trở thành phần đáng kể của chi phí. Cần JMH, đầu vào runtime và tiêu thụ kết quả đúng ngữ cảnh.

**Bài 4 — Đầu vào cố định khi nào hợp lệ?** Ứng dụng thật luôn gọi `compute(7L)` và JIT thực sự biến nó thành hằng. Có nên cố buộc benchmark thực hiện tám vòng phép tính?

**Đáp án:** Nếu mục tiêu là đo **chi phí thực tế của đúng call-site này**, để JIT tối ưu theo điều kiện thật có thể là lựa chọn hợp lý. Chỉ khi muốn đo thuật toán với đầu vào biến đổi mới cần thiết kế state runtime để tránh kết quả bị gập. Benchmark phải phản ánh yêu cầu, không máy móc “ngăn mọi tối ưu”.

**Bài 5 — Kết quả 0.001 ns/op.** Một phép nhân phức tạp được báo ở tốc độ khó tin. Nên làm gì trước khi quảng bá đây là tối ưu vượt trội?

**Đáp án:** Kiểm tra kết quả có được tiêu thụ không, input có constant-fold được không, benchmark có lặp batch khiến đơn vị `op` bị chia sai không, có chạy đúng JMH không, và nếu cần kiểm tra phần mã máy/JIT. Không được kết luận thuật toán nhanh từ số đo bất thường mà chưa kiểm chứng nội dung phép đo.

## 17. Ghi nhớ và nguồn tham khảo

- **Constant folding:** nếu dữ liệu/biểu thức đủ xác định, JVM có thể thay phép tính bằng kết quả biết trước. Với benchmark cần đo dữ liệu runtime, dùng trường trạng thái không `final`, `@Param` và tình huống có phân bố input đại diện.
- **Dead-code elimination:** nếu kết quả không được dùng, JVM có thể bỏ hẳn phép tính thuần. Trong JMH, hãy `return` kết quả hoặc `Blackhole.consume(...)`.
- **Hai điều kiện là độc lập:** dùng kết quả chưa chắc chống folding; dùng runtime input chưa chắc chống DCE.
- **JMH cung cấp harness**, warmup, fork, đo và xử lý kết quả; người viết vẫn phải chọn đúng công việc, dữ liệu và cách quan sát.
- **Đánh giá kết quả theo hợp đồng workload và điều kiện máy**, không suy ra một quy luật tuyệt đối từ số ns/op đơn lẻ.

Tài liệu chính thức và ví dụ nên đọc:
- [OpenJDK JMH](https://github.com/openjdk/jmh): dự án, cách khởi tạo bằng Maven archetype, cấu hình chạy microbenchmark.
- [JMHSample_08_DeadCode](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java): so sánh benchmark bỏ kết quả với benchmark trả kết quả.
- [JMHSample_09_Blackholes](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_09_Blackholes.java): sử dụng `Blackhole` khi kết quả cần được tiêu thụ.
- [JMHSample_10_ConstantFold](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java): đầu vào `final`, constant folding và dữ liệu trong trường trạng thái runtime.
- [JMH Core trên Maven Central](https://central.sonatype.com/artifact/org.openjdk.jmh/jmh-core): phiên bản dependency được xuất bản.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-performance-008">Đo với miền dữ liệu thực tế và cả tình huống biên</a>

<details>
<summary>Xem chi tiết</summary>

# Đo hiệu năng tính toán số với miền dữ liệu thực tế và tình huống biên trong Java

## 1. Đo nhanh trên một vài số đẹp chưa phản ánh hiệu năng thực tế

Giả sử có hai cách cộng, chia hoặc chuẩn hóa số trong Java. Một cách có vẻ nhanh hơn khi chạy với 100 số nguyên nhỏ hoặc vài giá trị thập phân ngắn. Kết luận đó có thể đảo ngược khi dữ liệu thực tế gồm nhiều chữ số, scale chênh lệch lớn, số âm, số 0 hoặc tỷ lệ ngoại lệ cao.

Vì vậy, đo hiệu năng có ý nghĩa đòi hỏi **cả hai**:

1. **Miền dữ liệu đại diện:** kích thước đầu vào, phân bố giá trị, precision/scale, tần suất phép toán và tỷ lệ đầu vào thông thường phải gần với tải công việc cần tối ưu.
2. **Tình huống biên:** dữ liệu ở các giới hạn có thể khiến thuật toán đổi nhánh, overflow, phải cấp phát nhiều hơn, ném ngoại lệ hoặc chạy với độ phức tạp khác hẳn.

Bài học tập trung vào cách thiết kế, kiểm chứng và đọc kết quả phép đo cho các kiểu số `int`, `long`, `double`, `BigInteger`, `BigDecimal` và các thao tác tổng hợp liên quan. Mục tiêu là chọn triển khai **đúng yêu cầu số học** và **đủ nhanh với tải thực**, thay vì chọn mã chỉ nhanh trong một bài thử nhỏ.

Ba câu hỏi phải được trả lời trước khi benchmark:

- **Đo thao tác nào?** Ví dụ parse chuỗi thành `BigDecimal`, cộng các `BigDecimal` đã được tạo sẵn, nhân `BigInteger`, tính tổng `IntStream`, hoặc format đầu ra.
- **Đầu vào thật trông như thế nào?** Bao nhiêu phần tử một lần gọi? Bao nhiêu chữ số? Có dữ liệu âm, 0, số rất lớn, scale khác nhau, số bất thường?
- **Định nghĩa đúng là gì?** Kết quả phải chính xác tuyệt đối, được làm tròn theo quy tắc nào, có được overflow không, và xử lý đầu vào không hợp lệ ra sao?

Không thể so sánh hai phép tính chỉ vì chúng trả về một con số nhìn có vẻ giống nhau. Ví dụ, thay `BigDecimal` bằng `double` có thể nhanh nhưng làm mất bảo đảm chính xác thập phân cần cho tiền tệ. Đó là **thay đổi ngữ nghĩa**, không phải chỉ tối ưu.

## 2. Xây dựng hồ sơ dữ liệu trước khi đo

Một benchmark có thể đo đúng thời gian chạy của dữ liệu nó nhận, nhưng vẫn trả lời sai câu hỏi nếu dữ liệu đó không đại diện cho sản phẩm thật.

Ví dụ một dịch vụ xử lý giao dịch nhận số tiền ở dạng chuỗi:

- Phần lớn số tiền có hai chữ số thập phân và nằm trong giới hạn thường gặp.
- Một tỷ lệ nhỏ là số tiền 0, số âm (hoàn tiền), số rất lớn hoặc có nhiều chữ số lẻ.
- Một số dòng là dữ liệu đầu vào sai định dạng và phải bị từ chối.
- Khối lượng giao dịch mỗi batch có thể là 1, 100, 10.000 hoặc nhiều hơn.

Nếu chỉ đo `new BigDecimal("1.00").add(new BigDecimal("2.00"))` trong vòng lặp, ta đã bỏ qua parse, bộ nhớ, số lượng phần tử, scale, giá trị cực lớn và tính chất batch.

### 2.1. Ma trận dữ liệu đại diện

| Trục dữ liệu | Cần ghi nhận | Vì sao tác động hiệu năng |
|---|---|---|
| Kích thước tập | 0, 1, vài chục, hàng nghìn, hàng triệu; phân bố thực tế | Chi phí khởi tạo pipeline, vòng lặp, cache, phân bổ và GC |
| Độ lớn số nguyên | Giá trị nhỏ, sát `Integer.MAX_VALUE`/`Long.MAX_VALUE`, số `BigInteger` rất dài | Overflow, số limb nội bộ, thuật toán nhân/chia |
| `BigDecimal.precision()` | Ít chữ số, nhiều chữ số, độ dài cực lớn hợp lệ | Chi phí phép toán và định dạng thay đổi |
| `BigDecimal.scale()` | Scale 0, 2, âm, lớn, chênh lệch scale | Phép cộng có thể cần căn chỉnh scale; `setScale`/`divide` cần thêm xử lý |
| Dấu và giá trị | Số dương, âm, 0, gần 0, đổi dấu liên tục | Nhánh điều kiện, hủy số, quy tắc làm tròn |
| Phép tính | Cộng, nhân, chia, làm tròn, parse, format | Mỗi phép có độ phức tạp và kiểu cấp phát riêng |
| Dạng đầu vào | Chuỗi, primitive array, wrapper collection | Chi phí parse, boxing/unboxing, chuyển đổi |
| Kiểu truy cập | Tuần tự, song song, nhiều request đồng thời | Cạnh tranh CPU, cache, allocator và GC |

**Tập phổ biến** và **tập biên** nên là hai nhóm kết quả riêng. Một trường hợp cực đoan hiếm gặp có thể được ghi nhận, nhưng không nên đánh đồng nó với thời gian trung bình của mọi request. Đồng thời, tình huống biên có thể là một yêu cầu chính thức, ví dụ ngưỡng latency tối đa hoặc giới hạn chống quá tải; khi đó nó cần có tiêu chí riêng.

### 2.2. Phân bố quan trọng hơn giá trị trung bình

Giả sử một triệu request có 99% đầu vào là `BigDecimal` 6 chữ số và 1% là số dài hàng nghìn chữ số. Lấy độ dài trung bình rồi chỉ benchmark một giá trị có độ dài trung bình **không tương đương** với benchmark hỗn hợp thật: thời gian của phép nhân/chia và cấp phát bộ nhớ có thể tăng không tuyến tính theo số chữ số.

Thực hành tốt:

1. Tách **bucket** theo kích thước: ví dụ 1–9, 10–99, 100–999, từ 1000 chữ số trở lên (chỉ là ví dụ; ranh giới phải theo dữ liệu thực).
2. Đo từng bucket để biết chi phí của từng lớp đầu vào.
3. Đo một workload **hỗn hợp có trọng số theo tỷ lệ thật** để ước lượng tải phổ biến.
4. Đo trường hợp xấu riêng nếu cần đáp ứng ngưỡng SLO hoặc giới hạn an toàn.

Khi nguồn production chứa dữ liệu nhạy cảm, tạo dữ liệu tổng hợp có **cùng đặc điểm thống kê**; không sao chép nguyên thông tin cá nhân hoặc giá trị nhạy cảm vào benchmark.

## 3. Bộ tình huống biên theo từng kiểu số

Các loại biên phải xuất phát từ miền số và thao tác thực sự được sử dụng.

### 3.1. int và long: kiểm tra giới hạn và overflow

~~~java
int maxInt = Integer.MAX_VALUE;            // 2147483647
long beyondInt = (long) maxInt + 1;        // 2147483648

int overflow = maxInt + 1;                 // -2147483648 (wrap)
~~~

Cần kiểm tra tối thiểu các giá trị `0`, `1`, `-1`, `MIN_VALUE`, `MAX_VALUE`, và các tổ hợp cộng/nhân gần ngưỡng. Khi dùng `Math.addExact` hoặc `Math.multiplyExact`, kiểm tra cả đường thành công lẫn đường ném `ArithmeticException`.

**Đo hai phương án chỉ khi ngữ nghĩa phù hợp**: so sánh phép cộng int có wrap với `Math.addExact` không phải cuộc đua tương đương nếu nghiệp vụ yêu cầu phát hiện tràn số. Trong hệ thống thực, đường ném exception có thể chiếm chi phí đáng kể nhưng không được tự ý bỏ kiểm tra chỉ vì chậm.

### 3.2. double: độ chính xác, NaN, Infinity và độ lớn

~~~java
double[] sample = {
        0.0, -0.0, 0.1, -0.1,
        Double.MIN_VALUE,
        Double.MIN_NORMAL,
        Double.MAX_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY
};
~~~

Cần thử số gần 0, subnormal, số rất lớn, nhiều giá trị triệt tiêu nhau và trường hợp tính ra `Infinity`/`NaN`. `Double.MIN_VALUE` là số dương nhỏ nhất khác 0 dạng subnormal, **không phải** giá trị âm lớn nhất về độ lớn; hãy nhớ `-Double.MAX_VALUE` khi cần giá trị âm hữu hạn cực trị.

Tổng `double` phụ thuộc thứ tự tính vì phép cộng số thực không kết hợp tuyệt đối. Chạy parallel hoặc dùng thuật toán cộng bù có thể cho kết quả hơi khác; nên xác định dung sai phù hợp thay vì kiểm tra mọi kết quả bằng `==`. Với `NaN`, so sánh thường cần `Double.isNaN(...)`.

### 3.3. BigInteger: số chữ số và phép toán cụ thể

~~~java
BigInteger small = BigInteger.valueOf(7);
BigInteger large = BigInteger.TEN.pow(1000);
BigInteger boundary = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE);
~~~

Thử độ dài operand khác nhau (ví dụ 1, 20, 100, 1000 chữ số), dấu âm/dương, zero và các tổ hợp có operand chênh lệch độ dài. Chi phí phép nhân và phép chia có thể thay đổi theo cỡ số, thậm chí theo thuật toán nội bộ được JVM chọn. Chỉ benchmark phép cộng các `BigInteger` nhỏ rồi ngoại suy tới phép nhân số có hàng nghìn chữ số là thiếu căn cứ.

**Lưu ý:** `BigInteger.TEN.pow(1000)` tạo số giá trị `10^1000`, tức 1001 chữ số thập phân. Khi tạo dữ liệu theo số chữ số cần đo, phải phân biệt "lũy thừa 1000" với "có đúng 1000 chữ số".

### 3.4. BigDecimal: precision, scale, rounding và định dạng

~~~java
BigDecimal[] values = {
        new BigDecimal("0"),
        new BigDecimal("0.00"),
        new BigDecimal("-0.005"),
        new BigDecimal("1.00"),
        new BigDecimal("1E+8"),
        new BigDecimal("0.00000001"),
        new BigDecimal("999999999999999999.99")
};
~~~

Các đối tượng bằng nhau về giá trị có thể có **scale khác nhau**. Ví dụ `new BigDecimal("1.0")` và `new BigDecimal("1.00")` so sánh `compareTo() == 0`, nhưng `equals()` trả false. Benchmark chỉ một scale sẽ không đại diện cho workload có scale hỗn hợp.

Với phép chia, dữ liệu biên phải có cả kết quả thập phân hữu hạn và vô hạn:

~~~java
BigDecimal.ONE.divide(new BigDecimal("8")); // 0.125

// BigDecimal.ONE.divide(new BigDecimal("3"));
// ArithmeticException vì không biểu diễn chính xác hữu hạn.
BigDecimal rounded = BigDecimal.ONE.divide(
        new BigDecimal("3"),
        new java.math.MathContext(12, java.math.RoundingMode.HALF_EVEN));
~~~

Không thể coi một bản cài đặt "nhanh hơn" nếu nó âm thầm thay đổi scale, làm tròn ở bước khác hoặc bỏ ngoại lệ bắt buộc. Benchmark có độ lớn `BigDecimal` cao cần giới hạn thực tế để tránh vô tình dùng tài nguyên quá mức; ghi rõ giới hạn đầu vào được hệ thống hỗ trợ.

## 4. Tách chi phí tạo dữ liệu khỏi phép toán đang đo

Phép đo có thể vô tình tính cả công đoạn **parse từ chuỗi** hoặc **tạo đối tượng**:

~~~java
// Bao gồm cả parse và cộng: hợp lý nếu đây là toàn bộ đường xử lý thực.
BigDecimal result = new BigDecimal("123.45")
        .add(new BigDecimal("67.89"));
~~~

Nhưng nếu câu hỏi là "`BigDecimal.add` nhanh đến đâu với hai operand đã chuẩn bị sẵn?", phải tạo operand trước vùng thời gian đo:

~~~java
BigDecimal left = new BigDecimal("123.45");
BigDecimal right = new BigDecimal("67.89");

// Chỉ phép toán add là đối tượng của benchmark.
BigDecimal result = left.add(right);
~~~

Cả hai phép đo đều có giá trị, **nếu được đặt tên chính xác**:

- **Operation benchmark:** operand đã có sẵn; đo cộng/nhân/chia.
- **End-to-end benchmark:** gồm parse, kiểm tra dữ liệu, tính toán, làm tròn và format, phản ánh đường request thực.
- **Allocation benchmark:** theo dõi số byte/operation và GC khi tạo/biến đổi nhiều số.

Với các số bất biến, thao tác có thể tạo đối tượng kết quả mới. Nếu bạn chỉ đo thời gian CPU mà bỏ qua cấp phát, phương án "nhanh hơn" có thể gây tần suất GC cao khi hệ thống tải lớn.

Không dùng cùng một đối tượng mutable bị thay đổi liên tục trong các lần đo nếu điều đó khiến đầu vào thay đổi hoặc làm xuất hiện tranh chấp ngoài chủ ý. Tách rõ dữ liệu chuẩn bị trước, dữ liệu cho từng invocation và trạng thái mà benchmark được phép thay đổi.

## 5. Độ đúng là điều kiện trước khi so tốc độ

Mỗi candidate phải vượt qua **đối chiếu kết quả** trên cùng tập dữ liệu trước khi báo cáo tốc độ. Có thể dùng một triển khai chuẩn dễ hiểu để làm đối chứng (*oracle*), nhưng oracle cũng cần được kiểm thử.

Ví dụ đo hai cách tính tổng các `BigDecimal` đã tạo sẵn:

~~~java
import java.math.BigDecimal;
import java.util.Arrays;

BigDecimal[] input = {
        new BigDecimal("12.50"),
        new BigDecimal("-3.20"),
        new BigDecimal("0.70")
};

BigDecimal loop = BigDecimal.ZERO;
for (BigDecimal x : input) {
    loop = loop.add(x);
}

BigDecimal stream = Arrays.stream(input)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

System.out.println(loop.toPlainString());   // 10.00
System.out.println(stream.toPlainString()); // 10.00
~~~

Phải kiểm tra kết quả trước. Nếu yêu cầu chỉ xét giá trị toán học, dùng `compareTo(...) == 0`. Nếu scale/format là một phần của hợp đồng, cần kiểm tra `scale()` hoặc biểu diễn chuỗi. Với một số phép toán như chia hay làm tròn ở bước trung gian, thay đổi thứ tự có thể dẫn đến kết quả khác ngay cả khi không có lỗi concurrency.

Các kiểm tra nên bao gồm:

- Tổng đúng cho tập 0 phần tử và 1 phần tử.
- Dấu âm/0; các giá trị cùng dấu và trái dấu.
- Overflow đối với primitive; xử lý exception nếu cần.
- Precision/scale và `RoundingMode` đối với thập phân.
- Đường đầu vào sai định dạng/không hợp lệ nếu có trong ứng dụng.
- Đúng trong các tình huống có xử lý song song khi triển khai sử dụng parallel.

**Không được bỏ qua validation/exception theo nghiệp vụ để khiến benchmark nhìn nhanh hơn.** Nếu hai triển khai khác hợp đồng đầu ra, kết quả thời gian không đủ để thay thế lẫn nhau.

## 6. Ví dụ Java 17+ chạy độc lập: tạo dữ liệu thường và biên, so sánh kết quả

Chương trình bên dưới kiểm tra các tập mẫu cố định và đo **thời gian thô để quan sát sơ bộ**. Số nanosecond in ra phụ thuộc máy, JDK, tải CPU và trạng thái JVM; chương trình này **không phải microbenchmark đáng tin cậy**. Mục tiêu là minh họa cách giữ tập đầu vào, kiểm tra kết quả, và phát hiện nhầm lẫn giữa dữ liệu thường với dữ liệu biên.

Tạo tệp `NumericWorkloadsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.Arrays;

public class NumericWorkloadsDemo {
    private record Case(String name, String[] values) {}

    private static final Case[] CASES = {
        new Case("typical",
                new String[] {"12.50", "-3.20", "0.70"}),
        new Case("zeros",
                new String[] {"0.00", "0.00", "0.00"}),
        new Case("different-scale",
                new String[] {"1E+8", "0.00000001", "-0.00000001"}),
        new Case("large",
                new String[] {"999999999999999999.99", "0.01", "-1.00"}),
        new Case("empty", new String[] {})
    };

    private static BigDecimal sumLoop(BigDecimal[] values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    private static BigDecimal sumStream(BigDecimal[] values) {
        return Arrays.stream(values)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static void main(String[] args) {
        for (Case testCase : CASES) {
            // Chuẩn bị input bên ngoài phép đo:
            BigDecimal[] data = Arrays.stream(testCase.values())
                    .map(BigDecimal::new)
                    .toArray(BigDecimal[]::new);

            // Kiểm tra đúng trước khi đo:
            BigDecimal expected = sumLoop(data);
            BigDecimal actual = sumStream(data);
            if (expected.compareTo(actual) != 0
                    || expected.scale() != actual.scale()) {
                throw new AssertionError("Mismatch: " + testCase.name());
            }

            System.out.println(testCase.name()
                    + " | inputs=" + data.length
                    + " | sum=" + actual.toPlainString());

            // Chỉ mang tính chẩn đoán sơ bộ, không dùng để xếp hạng:
            long start = System.nanoTime();
            BigDecimal measured = sumLoop(data);
            long elapsed = System.nanoTime() - start;
            if (measured.compareTo(expected) != 0) {
                throw new AssertionError("Unexpected result");
            }
            System.out.println("  timing-ns=" + elapsed);
        }
    }
}
~~~

Chạy:

~~~bash
javac NumericWorkloadsDemo.java
java NumericWorkloadsDemo
~~~

Các dòng kết quả **ổn định về mặt số học** (cứ sau mỗi dòng sẽ có thêm một dòng `timing-ns=...` với giá trị không cố định):

~~~text
typical | inputs=3 | sum=10.00
zeros | inputs=3 | sum=0.00
different-scale | inputs=3 | sum=100000000.00000000
large | inputs=3 | sum=999999999999999999.00
empty | inputs=0 | sum=0
~~~

Kết quả thực tế sẽ có xen kẽ một dòng thời gian sau mỗi dòng số học, ví dụ `timing-ns=12345`; con số đó **không ổn định** và không được diễn giải là kết luận hiệu năng. Trên chương trình nhỏ, nhiễu từ JIT, GC, hệ điều hành, phân giải đồng hồ và overhead của phép đo có thể lớn hơn chính thao tác. Ngoài ra, chương trình chỉ tính một lần mỗi case. Để xếp hạng các phương án, dùng benchmark được thiết kế riêng, chẳng hạn JMH ở mục kế tiếp.

Đáng chú ý ở đầu vào `different-scale`: dù các số `0.00000001` và `-0.00000001` triệt tiêu nhau, tổng vẫn giữ scale 8 do quy tắc `BigDecimal.add`. Đây là lý do test cả giá trị và scale.

## 7. Dùng JMH khi cần đo vi mô đáng tin cậy

**JMH (Java Microbenchmark Harness)** là công cụ benchmark chuẩn thường dùng cho mã JVM. Nó hỗ trợ warmup, measurement iterations, forks, các kiểu kết quả (`AverageTime`, `Throughput`...), tham số đầu vào và cơ chế tiêu thụ kết quả giúp giảm rủi ro benchmark bị JVM tối ưu theo cách không đại diện.

Một benchmark tối thiểu dành cho phép cộng các `BigDecimal` đã parse trước có thể viết như sau (đây là **fragment JMH**, không chạy trực tiếp chỉ bằng `javac` của JDK; project phải thêm dependency JMH và annotation processor tương ứng):

~~~java
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DecimalAddBenchmark {
    @Param({"8", "64", "512"})
    public int elements;

    private BigDecimal[] values;

    @Setup(Level.Trial)
    public void prepare() {
        values = IntStream.range(0, elements)
                .mapToObj(i -> new BigDecimal(
                        i % 2 == 0 ? "123.45" : "-23.40"))
                .toArray(BigDecimal[]::new);
    }

    @Benchmark
    public void sumLoop(Blackhole blackhole) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        blackhole.consume(total);
    }

    @Benchmark
    public void sumStream(Blackhole blackhole) {
        BigDecimal total = java.util.Arrays.stream(values)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        blackhole.consume(total);
    }
}
~~~

Ý nghĩa:

- `@Param` đo nhiều kích thước tập, thay vì một danh sách ngắn duy nhất.
- `@Setup(Level.Trial)` chuẩn bị dữ liệu **ngoài đoạn được đo**; vì thế benchmark so sánh phần cộng đã có operand.
- `Blackhole.consume(...)` ngăn việc vô tình bỏ đi kết quả được tính, giúp benchmark diễn đạt rằng kết quả có giá trị.
- `@BenchmarkMode(Mode.AverageTime)` yêu cầu thời gian trung bình của invocation trong các measurement; không phải thời gian latency p99 của hệ thống thật.
- `@State(Scope.Thread)` cho mỗi worker một state riêng. Khi cần mô phỏng tranh chấp trên một state chia sẻ, phải chọn state/luồng thích hợp theo tình huống muốn đo, nhưng benchmark đó là câu hỏi khác.

Ví dụ này **chưa đại diện cho mọi dữ liệu thực tế**, vì mỗi phần tử chỉ là một trong hai chuỗi ngắn. Để đo workload thực, thay `prepare()` bằng bộ dữ liệu có phân bố precision, scale, dấu và độ dài lấy từ phân tích dữ liệu nguồn; các nhóm biên nên tách thành các tham số case khác nhau. Với đầu vào random, tạo theo **seed cố định** trước giai đoạn đo để các candidate làm việc trên cùng dữ liệu; tránh tạo random trong chính benchmark nếu không chủ đích đo bộ sinh số ngẫu nhiên.

### 7.1. Quy trình đo có thể tái lập

1. **Định nghĩa phép toán và output:** nêu tên trường hợp, input, đơn vị đo, đúng/sai, và điều kiện hoàn thành.
2. **Chuẩn bị corpus:** dữ liệu thường có tỷ lệ thực tế, case kích thước khác nhau, case biên và case lỗi rõ ràng.
3. **Xác thực correctness:** chạy oracle/test cho mọi triển khai, bao gồm giá trị và scale khi cần.
4. **Warmup:** cho JVM biên dịch và tối ưu các đường mã được gọi thường xuyên trước khi thu kết quả.
5. **Measurement:** chạy nhiều iteration và nhiều fork JVM độc lập, ghi nhận độ phân tán và sai số do harness báo.
6. **Ghi môi trường:** JDK/vendor/version, CPU, RAM, JVM flags, hệ điều hành, GC, số thread, kích thước heap và nguồn dữ liệu.
7. **Đo allocation/GC:** dùng profiler hoặc tính năng profiling của JMH nếu việc tạo `BigDecimal`/`BigInteger` là chi phí đáng kể.
8. **Lặp lại trên tải mục tiêu:** kiểm tra workload đầu-cuối và tải đồng thời phù hợp, vì kết quả microbenchmark chỉ mô tả đoạn mã được đo.

Không chọn một kết quả duy nhất sau một vòng chạy làm kết luận. Nếu chênh lệch nhỏ so với độ biến động của phép đo, phải coi chưa đủ bằng chứng.

### 7.2. Cạm bẫy của microbenchmark

| Cạm bẫy | Vì sao nguy hiểm | Cách phòng tránh |
|---|---|---|
| Benchmark code chỉ dùng hằng số | JVM có thể constant-fold hoặc tối ưu phần tính | Dữ liệu biến thiên từ `@State`/`@Param`, tiêu thụ kết quả |
| Không sử dụng output | JVM có thể loại phép tính không quan sát được | Trả kết quả từ benchmark hoặc dùng `Blackhole` |
| Không warmup | Đo cả interpreter/class loading/JIT | Cấu hình warmup + measurement riêng |
| Một lần `nanoTime` với vài phép toán | Chi phí đồng hồ và nhiễu áp đảo | JMH với iterations/forks |
| Tạo đối tượng bên trong phép đo dù chỉ muốn tính toán | Kết quả đo chủ yếu parse/cấp phát | Chuẩn bị trong `@Setup` khi hợp đồng cần |
| Chuẩn bị sẵn input dù mục tiêu là end-to-end | Bỏ qua parse/format/validation | Benchmark riêng đường request thực |
| Dùng random mỗi lần trong benchmark | Đo cả random, dữ liệu candidate khác nhau | Tạo corpus seed cố định ngoài đoạn đo |
| Chạy hai candidate trên tải hệ thống khác nhau | Nhiễu khiến kết quả khó so sánh | Môi trường ổn định, nhiều lần thử/forks |
| Đồng nhất average với p99 | Không đo được tail latency thực của hệ thống | Đo latency distribution ở cấp request |
| So code có quy tắc overflow/rounding khác | "Nhanh hơn" nhờ đổi semantics | Correctness gate trước performance |
| Chọn một dataset cực trị làm đại diện duy nhất | Không phản ánh tần suất sản xuất | Cả mixed workload và case biên riêng |

## 8. Chọn thước đo gắn với mục tiêu

Không có một chỉ số tốc độ phù hợp mọi hệ thống. Khi báo cáo, xác định **đơn vị và phạm vi**:

| Chỉ số | Trả lời câu hỏi | Khi hữu ích |
|---|---|---|
| `ns/op` hoặc `µs/op` | Một lần tính toán trong benchmark vi mô tốn bao lâu? | So hai thuật toán cho cùng thao tác |
| `ops/s` | Một worker/hệ thống xử lý được bao nhiêu phép trong một giây? | Throughput tổng hợp |
| `bytes/op` | Mỗi thao tác cấp phát bao nhiêu bộ nhớ? | `BigDecimal`/`BigInteger` tạo nhiều đối tượng |
| `GC pauses`, allocation rate | Dữ liệu lớn hoặc nhiều lần cấp phát gây áp lực GC ra sao? | Dịch vụ chạy lâu, tải cao |
| `p50/p95/p99 latency` | Nửa, 95%, 99% request hoàn thành trong bao lâu? | Đảm bảo thời gian phản hồi |
| `CPU utilization`, request concurrency | Tải đồng thời ảnh hưởng hiệu năng thế nào? | Phục vụ thực tế trên server |

Ví dụ một phép nhân `BigInteger` hiếm gặp mất nhiều thời gian có thể ít ảnh hưởng đến throughput trung bình nhưng làm đuôi latency tăng. Ngược lại, thêm một allocation nhỏ ở mỗi phép tính lặp hàng triệu lần có thể gây GC đáng kể dù mỗi invocation rất nhanh.

**Không diễn giải trung bình số học của một microbenchmark thành p99 của request.** Request có thể chứa parse, I/O, queueing, phân phối nhiều phép tính và pause GC. Nếu hệ thống có SLO p99, đo bằng công cụ tạo tải ở tầng yêu cầu thực với tỷ lệ request và mức concurrency đúng.

## 9. Tập biên phải thử cả lỗi, không chỉ số lớn

Dữ liệu biên gồm cả **đường lỗi có ý nghĩa nghiệp vụ**:

- `new BigDecimal("invalid")` ném `NumberFormatException`.
- `BigDecimal.ONE.divide(new BigDecimal("3"))` ném `ArithmeticException` nếu yêu cầu chia chính xác hữu hạn.
- `Math.addExact(Integer.MAX_VALUE, 1)` ném `ArithmeticException`.
- `BigDecimal` với precision/scale rất lớn có thể gây áp lực bộ nhớ hoặc gặp giới hạn thực thi/biểu diễn.
- `DoubleStream` chứa `NaN` có thể làm phép tổng hợp trả `NaN`, cần xử lý theo quy tắc ứng dụng.

Phải xác định tỷ lệ lỗi trong sản phẩm: nhiều trường hợp hiếm nhưng hợp lệ cần có benchmark riêng, còn yêu cầu sai định dạng có thể cần được đo ở đường kiểm tra đầu vào. Tuy nhiên, không cố tạo vô hạn dữ liệu nguy hiểm chỉ để chứng minh một điểm; thiết kế giới hạn đầu vào hợp lệ và thử ở ngay các mốc đó.

Nếu tài nguyên khan hiếm, dùng một bộ kiểm thử chức năng đầy đủ cho input cực đoan và một bộ benchmark giới hạn có thể tái lập; ghi rõ giới hạn đã đo để tránh ngoại suy quá xa.

## 10. Thiết kế thí nghiệm so sánh hai cách hiện thực

Giả sử mục tiêu là chọn giữa vòng lặp `for` và `Stream.reduce` để cộng một mảng `BigDecimal`.

### Bước 1 — định nghĩa hợp đồng

Đầu vào là `BigDecimal[]` đã được parse; phần tử không `null`. Cả hai giải pháp dùng cộng chính xác (`add` không có `MathContext`) và kết quả phải cùng giá trị **và scale**. Không thay đổi dữ liệu đầu vào.

### Bước 2 — thiết kế tập đo

- **Typical:** mảng có độ dài xuất hiện thường xuyên, chủ yếu precision thấp và scale 2.
- **Large batch:** số phần tử lớn đại diện batch đỉnh.
- **Mixed scale:** bao gồm scale âm, scale 0, scale 2, scale cao.
- **Long precision:** `BigDecimal` có hàng trăm/hàng nghìn chữ số ở những ngưỡng được phép.
- **Empty/single:** 0 hoặc 1 phần tử; kiểm tra overhead của pipeline.
- **Mixed sign:** số dương/âm/0, gồm trường hợp gần triệt tiêu nhau.

Mỗi case được đo riêng. Sau đó có thể xây dựng workload hỗn hợp theo tỷ lệ production để ước tính hiệu ứng tổng quát.

### Bước 3 — kiểm tra correctness trước

So `compareTo` và `scale` hoặc so chuỗi chuẩn nếu hợp đồng yêu cầu. Tránh chuẩn hóa bằng `stripTrailingZeros()` trong bài test nếu việc bảo toàn scale là bắt buộc: thao tác này thay đổi biểu diễn.

### Bước 4 — benchmark và đo phụ

Dùng JMH để đo thời gian mỗi lần cộng mảng cùng dữ liệu. Bật thêm profiling allocation khi cần. Với microbenchmark trên một vài operand, chênh lệch có thể rất nhỏ; với batch lớn, chi phí stream setup, vòng lặp và số lượng đối tượng kết quả đều góp vào kết quả.

### Bước 5 — diễn giải trong phạm vi chứng cứ

Một báo cáo tốt không chỉ viết "A nhanh hơn B 30%". Nó nêu **dataset**, **JDK/CPU**, **mode**, **số lần thử**, **độ biến động**, **allocation**, **correctness**, và trường hợp nào thắng/thua. Kết luận có thể là "Với batch 100 phần tử, A nhanh hơn có ý nghĩa; với batch 10.000 phần tử kết quả tương đương trong sai số", thay vì một lựa chọn tuyệt đối.

Bảng ghi nhận kết quả (điền bằng số đo thực, **không tự bịa giá trị**):

| Case | Size | Precision/scale | Cách A ns/op | Cách B ns/op | bytes/op | Correctness | Ghi chú |
|---|---:|---|---:|---:|---:|---|---|
| Typical | Theo dữ liệu thực | Ngắn, scale 2 | *Điền số đo* | *Điền số đo* | *Điền số đo* | PASS/FAIL | Warmup/fork |
| Mixed scale | Theo dữ liệu thực | Scale hỗn hợp | *Điền số đo* | *Điền số đo* | *Điền số đo* | PASS/FAIL | Kiểm tra scale |
| Large precision | Theo dữ liệu thực | Hàng trăm chữ số | *Điền số đo* | *Điền số đo* | *Điền số đo* | PASS/FAIL | Bộ nhớ/GC |
| Empty/single | 0 hoặc 1 | Biên cardinality | *Điền số đo* | *Điền số đo* | *Điền số đo* | PASS/FAIL | Overhead |

Đây là khung **ghi chép đo kiểm**, không phải kết quả benchmark. Không được thay các ô bằng số ước đoán rồi khẳng định đã đo trên môi trường sản xuất.

## 11. Các lỗi phổ biến và cách khắc phục

| Lỗi | Tác động | Cách khắc phục |
|---|---|---|
| Dùng duy nhất một giá trị số nhỏ | Bỏ qua các nhánh/độ phức tạp ở dữ liệu khác | Dùng phân bố thực + nhiều bucket |
| Đo tập biên rồi lấy làm "hiệu năng trung bình" | Sai tỷ lệ xảy ra | Tách kết quả biên với workload theo trọng số |
| Chỉ đo average time | Bỏ qua request chậm và GC pause | Thu p95/p99 ở tầng hệ thống khi có SLO |
| Dùng `new BigDecimal("1.00")` bên trong vòng loop không chủ đích | Lẫn chi phí parse với phép toán | Chọn rõ operation vs end-to-end |
| Benchmark nhận hằng, output bỏ đi | JIT có thể tối ưu mất thao tác | Dùng JMH `@State`, `@Param` và consume kết quả |
| Không kiểm tra output trước benchmark | Chọn phương án sai số học | Correctness gate và oracle |
| Bỏ qua overflow/trường hợp vô hạn | Sản phẩm sai dù benchmark nhanh | Test ở ranh giới và tình huống lỗi |
| So `double` với `BigDecimal` như cùng semantics | Đánh đổi precision không được công bố | Làm rõ hợp đồng số học |
| Mặc định parallel nhanh | Có overhead, tranh chấp và kết quả số thực thay đổi | Đo theo workload + core count |
| Thay data giữa các candidate | Không còn so cùng công việc | Dùng seed/dataset cố định |
| Không ghi JDK/flags/CPU | Khó tái lập kết luận | Lưu đầy đủ cấu hình |
| Báo "ns/op" như latency thật | Microbenchmark không mô phỏng request | Đo cả end-to-end dưới tải thật |

## 12. Bài tập tự kiểm tra

**Câu 1.** Vì sao một phép cộng `BigInteger` nhanh với các số có một chữ số không chứng minh nó nhanh với operand có hàng nghìn chữ số?

**Đáp án:** Chi phí số học phụ thuộc vào độ dài biểu diễn và thuật toán được dùng. Benchmark phải có các bucket độ dài đại diện cho miền được hỗ trợ.

**Câu 2.** Một hệ thống trả tiền bằng `BigDecimal` có scale bắt buộc 2. Nếu bản tối ưu trả cùng giá trị nhưng scale 1, có được coi là đúng không?

**Đáp án:** Nếu scale là một phần hợp đồng, không. Cần xác nhận giá trị và scale trước khi đo hiệu năng.

**Câu 3.** Benchmark chỉ gọi `sumLoop(input)` với operand parse sẵn. Nó đo được chi phí parse từ request không?

**Đáp án:** Không. Benchmark đó đo bước cộng trên dữ liệu đã chuẩn bị. Muốn đo end-to-end, phải đưa parse và các bước thực tế vào vùng được đo trong một benchmark riêng.

**Câu 4.** `IntStream.of(Integer.MAX_VALUE, 1).sum()` trả gì và có ý nghĩa gì đối với benchmark?

**Đáp án:** Trả `-2147483648` do overflow `int`. Một bản cài đặt có hiệu năng tốt nhưng kết quả này sai với nghiệp vụ cần tổng số nguyên lớn, nên phải sửa contract/kiểu dữ liệu trước khi xếp hạng hiệu năng.

**Câu 5.** Nếu 1% request có dữ liệu lớn và chậm, chỉ đo 99% dữ liệu phổ biến có đủ để báo latency p99 không?

**Đáp án:** Không. Phải quan sát phân bố latency trên toàn bộ request theo tỷ lệ thực và kiểm tra tail latency; đo riêng trường hợp lớn cũng hữu ích để hiểu nguyên nhân.

**Câu 6.** Tại sao cần warmup/forks khi benchmark mã Java ngắn?

**Đáp án:** JVM có thể khởi chạy ở interpreter, sau đó JIT tối ưu; trạng thái heap và cache có thể khác giữa lần chạy. Warmup và forks giúp giảm thiên lệch, nhưng vẫn cần báo độ biến động.

**Câu 7.** Một phương án dùng ít CPU hơn nhưng cấp phát gấp nhiều lần. Có thể tuyên bố nó chắc chắn tốt hơn không?

**Đáp án:** Chưa. Cấp phát thêm có thể làm tăng GC và p99 latency khi tải lớn. Cần đối chiếu nhiều chỉ số và giới hạn sản phẩm.

---

**Ghi nhớ:** Benchmark số học trong Java phải dùng **miền dữ liệu đại diện** và **tình huống biên được lựa chọn có lý do**, kiểm tra đúng giá trị/precision/scale/exception **trước khi so tốc độ**, rồi đo có warmup, nhiều lần thử và ghi rõ môi trường. Để ra quyết định thực tế, kết hợp microbenchmark với dữ liệu end-to-end, throughput, tail latency, allocation và GC; không suy rộng từ một phép cộng hoặc một `System.nanoTime()` đơn lẻ.

</details>

- [Quay lại đầu trang](#back-to-top)
