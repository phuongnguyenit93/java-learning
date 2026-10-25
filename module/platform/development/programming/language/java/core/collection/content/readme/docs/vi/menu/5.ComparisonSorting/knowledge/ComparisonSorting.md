<a id="back-to-top"></a>

# So sánh và sắp xếp

## Menu
- [Comparable và thứ tự tự nhiên](#comparison-sorting-001)
- [Comparator và hợp đồng dấu, tính bắc cầu, tính nhất quán](#comparison-sorting-002)
- [comparing, comparingInt và các bộ so sánh theo khóa](#comparison-sorting-003)
- [thenComparing và sắp xếp nhiều tiêu chí](#comparison-sorting-004)
- [reversed, reverseOrder, nullsFirst và nullsLast](#comparison-sorting-005)
- [Tránh tràn số khi viết phép so sánh bằng phép trừ](#comparison-sorting-006)
- [Tính ổn định của sắp xếp List và cách xử lý phần tử đồng hạng](#comparison-sorting-007)
- [Dùng cùng quy tắc so sánh cho sort, binarySearch, TreeSet và TreeMap](#comparison-sorting-008)

## <a id="comparison-sorting-001">Comparable và thứ tự tự nhiên</a>

<details>
<summary>Xem chi tiết</summary>

# Comparable và thứ tự tự nhiên

Khi có một danh sách số, “sắp xếp tăng dần” thường đã đủ rõ. Nhưng với đối tượng như một phiên bản phần mềm, hóa đơn hay sản phẩm, chương trình cần biết **đối tượng nào đứng trước đối tượng nào**.

`Comparable<T>` cho phép một kiểu tự khai báo quy tắc so sánh với các đối tượng phù hợp. Quy tắc mặc định đó được gọi là **thứ tự tự nhiên (natural ordering)** của kiểu.

Bài này giải thích ý nghĩa của thứ tự tự nhiên, hợp đồng của `compareTo`, cách tự cài đặt và tác động của quy tắc so sánh đối với collection. Các chương trình hoàn chỉnh dùng API Java 17, đã được biên dịch bằng `--release 17` và chạy để kiểm tra stdout. Các đoạn mã rút gọn còn lại minh họa một thao tác hoặc phần thân phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn cần có khả năng:

- Đọc kết quả âm, bằng không hoặc dương của `compareTo`.
- Giải thích khi nào một kiểu nên có thứ tự tự nhiên.
- Cài đặt so sánh nhiều trường mà không gây tràn số.
- Phân biệt “ngang hàng trong thứ tự” với `equals`.
- Dự đoán ảnh hưởng của `compareTo == 0` tới `TreeSet` và khóa của `TreeMap`.

## 1. Thứ tự tự nhiên giải quyết vấn đề gì?

Giả sử có các phiên bản:

```text
2.0.0
1.10.0
1.2.0
```

Ta muốn so sánh lần lượt phiên bản lớn, phiên bản nhỏ rồi bản vá:

```text
1.2.0 < 1.10.0 < 2.0.0
```

Nếu mỗi nơi sử dụng danh sách đều tự viết lại quy tắc, rất dễ có nơi so đúng theo số, nơi khác chỉ so chuỗi. Một quy tắc gắn với chính kiểu `Version` giúp các thao tác theo thứ tự tự nhiên dùng chung ý nghĩa.

### “Tự nhiên” là quy ước của kiểu

Thứ tự tự nhiên không phải một quy luật duy nhất cho mọi đối tượng:

- `Integer` có thứ tự theo giá trị số.
- `String` có thứ tự từ điển theo đơn vị mã UTF-16.
- Một kiểu phiên bản đơn giản có thể chọn thứ tự theo ba thành phần số.
- Một kiểu sản phẩm có nhiều nhu cầu như theo giá, tên hoặc ngày nhập; chưa chắc có một lựa chọn đủ rõ để làm mặc định.

Vì vậy, đừng thêm `Comparable` chỉ để một lời gọi sắp xếp biên dịch được. Trước hết hãy trả lời: **kiểu này có một quy tắc mặc định ổn định, có ý nghĩa và được các bên sử dụng cùng hiểu không?**

Nếu chỉ cần một cách xếp riêng cho một màn hình, một quy tắc bên ngoài qua `Comparator` thường phù hợp hơn. `Comparator` được nhắc ở đây để phân biệt trách nhiệm; trọng tâm của bài là thứ tự do chính kiểu khai báo.

### So sánh không tự sắp xếp

Gọi:

```java
first.compareTo(second);
```

chỉ trả về kết quả so sánh hai đối tượng. Nó không đổi chỗ chúng và không tự sắp xếp một danh sách.

Thuật toán sắp xếp sẽ gọi quy tắc đó nhiều lần để quyết định thứ tự các phần tử. Các cấu trúc có sắp thứ tự còn dùng quy tắc để tìm vị trí lưu và tra cứu.

## 2. Đọc giao diện Comparable<T>

Dạng rút gọn của giao diện:

```java
public interface Comparable<T> {
    int compareTo(T other);
}
```

Đây là phần chữ ký minh họa, không phải mã để tự định nghĩa lại giao diện của thư viện chuẩn.

Trong chương trình đầy đủ ở phần sau, kiểu `Version` khai báo `implements Comparable<Version>`.

`Comparable<Version>` nói rằng đối tượng của lớp cung cấp phép so sánh với đối tượng `Version` phù hợp. Khi triển khai thực tế, `compareTo` phải là phương thức `public`.

Nên dùng tham số kiểu cụ thể thay vì triển khai `Comparable` dạng thô. Kiểu cụ thể giúp trình biên dịch kiểm tra việc sử dụng và giảm nhu cầu ép kiểu không an toàn.

### Ý nghĩa giá trị trả về

Với `x.compareTo(y)`:

| Kết quả | Ý nghĩa |
| --- | --- |
| Nhỏ hơn `0` | `x` đứng trước `y` |
| Bằng `0` | `x` và `y` ngang hàng theo quy tắc thứ tự |
| Lớn hơn `0` | `x` đứng sau `y` |

Điều cần dùng là **dấu**, không phải một con số cụ thể.

Cách kiểm tra đúng:

```java
if (x.compareTo(y) < 0) {
    // x đứng trước y.
}
```

Không nên viết điều kiện `x.compareTo(y) == -1` để nhận biết mọi trường hợp “đứng trước”. Một phương thức hợp lệ có thể trả về `-5`, `-32` hoặc một giá trị âm khác.

### Bằng không không có nghĩa cùng đối tượng

Hai đối tượng khác nhau trong bộ nhớ có thể cho `compareTo == 0`. Ý nghĩa trực tiếp là chúng được quy tắc thứ tự xem là tương đương.

Để biết sự tương đương này có trùng với khái niệm bằng nhau của kiểu hay không, cần xét thêm `equals`. Đây là điểm quan trọng khi dùng các tập hợp có sắp thứ tự.

## 3. Những API dùng thứ tự tự nhiên

| API hoặc cấu trúc | Cách dùng thứ tự tự nhiên |
| --- | --- |
| `Collections.sort(list)` | Sắp xếp danh sách theo thứ tự tự nhiên của phần tử |
| `list.sort(null)` | Không truyền comparator riêng; dùng thứ tự tự nhiên |
| `Arrays.sort(objectArray)` | Sắp xếp mảng đối tượng có khả năng so sánh phù hợp |
| `Collections.min(values)`, `Collections.max(values)` | Tìm phần tử nhỏ nhất hoặc lớn nhất theo thứ tự tự nhiên |
| `new TreeSet<E>()` | Tổ chức phần tử bằng thứ tự tự nhiên khi không có comparator riêng |
| `new TreeMap<K, V>()` | Tổ chức khóa theo thứ tự tự nhiên khi không có comparator riêng |

Có hai điều cần phân biệt:

1. Một đối tượng `ArrayList` không tự sắp xếp sau khi `add`. Bạn phải yêu cầu sắp xếp khi cần.
2. Một `TreeSet` duy trì trật tự của các phần tử trong cấu trúc; nó còn dùng kết quả so sánh để xác định phần tử trùng.

`Comparable` áp dụng cho đối tượng. Các overload như `Arrays.sort(int[])` xử lý mảng nguyên thủy bằng hợp đồng riêng; các giá trị `int` không phải đối tượng triển khai `Comparable`.

### Điều kiện sử dụng

- Phần tử hoặc khóa phải so sánh được với nhau theo quy tắc đang dùng.
- Danh sách đem sắp xếp phải hỗ trợ việc thay đổi vị trí phần tử.
- Dữ liệu phải đáp ứng chính sách về `null`.
- `Collections.min` và `max` cần dữ liệu không rỗng; collection rỗng gây `NoSuchElementException`.

Ví dụ, `List.of(...)` không cho phép sửa đổi nên không thể được dùng trực tiếp như danh sách cần sắp xếp tại chỗ. Có thể đưa dữ liệu sang một `ArrayList` trước khi sắp xếp, như chương trình dưới đây.

## 4. Quan sát thứ tự có sẵn của Integer và String

Tạo tệp **`NaturalOrderDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NaturalOrderDemo {
    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>(List.of(10, 2, -3, 2));
        Collections.sort(numbers);
        System.out.println("numbers = " + numbers);
        System.out.println("min = " + Collections.min(numbers));
        System.out.println("max = " + Collections.max(numbers));

        String[] words = {"a", "B", "A", "b", "aa", "a2", "a10"};
        Arrays.sort(words);
        System.out.println("words = " + Arrays.toString(words));
        System.out.println("text 1.10 vs 1.2 = "
                + Integer.signum("1.10".compareTo("1.2")));
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 NaturalOrderDemo.java
java -Dfile.encoding=UTF-8 NaturalOrderDemo
```

Stdout:

```text
numbers = [-3, 2, 2, 10]
min = -3
max = 10
words = [A, B, a, a10, a2, aa, b]
text 1.10 vs 1.2 = -1
```

### Đọc kết quả

- Các số nguyên được xếp theo giá trị số, không phải theo hình thức chuỗi.
- Hai phần tử `2` vẫn còn trong danh sách sau sắp xếp. Sắp xếp danh sách không đồng nghĩa loại trùng.
- Trong thứ tự tự nhiên của `String`, `"A"` và `"B"` đứng trước các chữ thường trong dữ liệu này.
- Khi một chuỗi là tiền tố của chuỗi kia, chuỗi ngắn hơn đứng trước: `"a"` đứng trước `"a10"`.
- `"a10"` đứng trước `"a2"` vì vị trí khác nhau đầu tiên so `'1'` với `'2'`, không phân tích cả dãy thành số.
- Chuỗi `"1.10"` đứng trước `"1.2"`, trong khi phiên bản số `1.10` nên đứng sau `1.2` theo quy tắc phiên bản đã chọn.

Bài dùng `Integer.signum(...)` chỉ để hiển thị dấu bằng `-1`, `0` hoặc `1`. Đây là phép chuẩn hóa kết quả để đọc, không phải yêu cầu mọi `compareTo` đều trả về đúng ba giá trị đó.

Thứ tự tự nhiên của chuỗi không phải quy tắc sắp xếp tên người theo mọi ngôn ngữ. Khi cần quy tắc theo ngôn ngữ hoặc không phân biệt hoa thường, hãy thiết kế chính sách so sánh riêng và kiểm tra các yêu cầu về ký tự.

## 5. Hợp đồng mà compareTo phải tuân thủ

Một lần sắp xếp có thể so một cặp theo nhiều chiều và ở những thời điểm khác nhau. Nếu kết quả mâu thuẫn, cấu trúc hoặc thuật toán không còn có thể dựa vào một quan hệ thứ tự ổn định.

Gọi `sign(v)` là dấu của số nguyên `v`: âm, bằng không hoặc dương.

### 5.1. Đổi chiều phải đổi dấu

Với hai đối tượng hợp lệ:

```text
sign(x.compareTo(y)) == -sign(y.compareTo(x))
```

Nếu `x` đứng trước `y`, thì `y` phải đứng sau `x`. Không được viết quy tắc có thể kết luận mỗi đối tượng đều nhỏ hơn đối tượng kia.

Nên kiểm tra quan hệ qua dấu; không yêu cầu `x.compareTo(y)` luôn bằng đúng số đối của `y.compareTo(x)`.

Một phần của hợp đồng cũng yêu cầu tính đối xứng của khả năng so sánh: nếu so `x` với `y` gây ngoại lệ do chúng không so sánh được, chiều ngược lại cũng không được giả vờ là một phép so sánh hợp lệ.

### 5.2. Có tính bắc cầu

Nếu `x` đứng sau `y` và `y` đứng sau `z`, thì `x` phải đứng sau `z`:

```text
x > y và y > z  =>  x > z
```

Một quy tắc vòng tròn kiểu “A trước B, B trước C, C trước A” không thể làm thứ tự tự nhiên hợp lệ.

### 5.3. Hai đối tượng ngang hàng phải có quan hệ giống nhau với đối tượng thứ ba

Nếu:

```text
x.compareTo(y) == 0
```

thì đối với mọi `z` trong miền so sánh phù hợp:

```text
sign(x.compareTo(z)) == sign(y.compareTo(z))
```

Nếu quy tắc đã coi `x` và `y` là ngang hàng, nó không được cho một đối tượng đứng trước `z` và đối tượng kia đứng sau `z`.

### 5.4. So sánh chính mình cho kết quả bằng không

Với một đối tượng hợp lệ và không thay đổi trạng thái:

```text
x.compareTo(x) == 0
```

Đây là một kiểm tra nhỏ nhưng có ích khi viết kiểu tự định nghĩa.

### 5.5. Quy tắc phải ổn định trong lúc sử dụng

Không nên để kết quả phụ thuộc ngẫu nhiên, thời gian hiện tại hoặc một dữ liệu bên ngoài thay đổi không kiểm soát.

Nếu giá trị tham gia thứ tự có thể đổi, phải có chính sách sử dụng rõ ràng, đặc biệt khi đối tượng đang là phần tử hoặc khóa của một cấu trúc đã sắp thứ tự.

Vi phạm hợp đồng có thể dẫn tới kết quả sắp xếp sai, hành vi cấu trúc dữ liệu không đáng tin hoặc ngoại lệ do thuật toán phát hiện mâu thuẫn. Không phải mọi lỗi hợp đồng đều được Java phát hiện ngay; việc một lần chạy không có lỗi không chứng minh quy tắc đúng.

## 6. Cài đặt thứ tự tự nhiên cho kiểu Version

Ta xây dựng kiểu phiên bản gồm ba số không âm:

```text
major.minor.patch
```

Quy tắc:

1. So `major`.
2. Nếu ngang nhau, so `minor`.
3. Nếu vẫn ngang nhau, so `patch`.

Đây là **so sánh từ điển theo nhiều trường**: dừng tại trường đầu tiên tạo khác biệt. “Từ điển” ở đây mô tả cách ưu tiên từng thành phần; mỗi thành phần vẫn được so theo giá trị số.

Ví dụ:

| Hai phiên bản | Trường quyết định |
| --- | --- |
| `1.99.99` và `2.0.0` | `major` |
| `1.2.9` và `1.10.0` | `minor` |
| `1.2.3` và `1.2.4` | `patch` |
| `1.2.3` và `1.2.3` | Không có khác biệt |

Kiểu ví dụ chỉ biểu diễn bộ ba số. Nó không triển khai toàn bộ quy tắc Semantic Versioning, phần tiền phát hành, metadata hoặc phân tích chuỗi đầu vào.

### Chương trình hoàn chỉnh

Tạo tệp **`VersionComparableDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class VersionComparableDemo {
    public record Version(int major, int minor, int patch)
            implements Comparable<Version> {
        public Version {
            if (major < 0 || minor < 0 || patch < 0) {
                throw new IllegalArgumentException("Version parts must be non-negative");
            }
        }

        @Override
        public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);
            if (result != 0) {
                return result;
            }

            result = Integer.compare(minor, other.minor);
            if (result != 0) {
                return result;
            }

            return Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    public static void main(String[] args) {
        Version first = new Version(1, 2, 0);
        Version same = new Version(1, 2, 0);
        Version later = new Version(1, 10, 0);

        List<Version> versions = new ArrayList<>(List.of(
                new Version(2, 0, 0), later, first, same));
        versions.sort(null);

        System.out.println("sorted = " + versions);
        System.out.println("1.10.0 vs 1.2.0 = "
                + Integer.signum(later.compareTo(first)));
        System.out.println("same compareTo = " + first.compareTo(same));
        System.out.println("same equals = " + first.equals(same));
        System.out.println("set = " + new TreeSet<>(versions));
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 VersionComparableDemo.java
java -Dfile.encoding=UTF-8 VersionComparableDemo
```

Stdout:

```text
sorted = [1.2.0, 1.2.0, 1.10.0, 2.0.0]
1.10.0 vs 1.2.0 = 1
same compareTo = 0
same equals = true
set = [1.2.0, 1.10.0, 2.0.0]
```

### Vì sao cách cài đặt này đúng với mô hình đã chọn?

- `record` cung cấp các thành phần không thể gán lại sau khi tạo, phù hợp với việc giữ ổn định khóa so sánh.
- Hàm tạo từ chối thành phần âm, giữ miền giá trị đúng thiết kế.
- `Integer.compare` so theo số mà không dùng phép trừ có nguy cơ tràn.
- Trường sau chỉ được xét khi trường trước ngang nhau.
- Hai phiên bản trả về `0` khi cả ba thành phần đều bằng nhau.
- `equals` do record sinh cũng xét ba thành phần đó, nên khái niệm bằng nhau và ngang hàng trong thứ tự nhất quán trong ví dụ.
- `toString` chỉ phục vụ hiển thị. Việc sắp xếp không dựa vào chuỗi mà nó trả về.

Danh sách sau sắp xếp giữ cả hai phiên bản `1.2.0`. Khi chuyển sang `TreeSet`, hai phiên bản này trở thành một phần tử vì quy tắc thứ tự coi chúng bằng nhau.

### Tại sao không cộng tất cả thành phần?

Cách lấy tổng `major + minor + patch` làm mất mức ưu tiên. Ví dụ, `1.9.0` và `2.0.0` sẽ bị xếp theo tổng chứ không theo phiên bản lớn trước.

Cách ghép thành `major * 10000 + minor * 100 + patch` cũng đòi hỏi giới hạn chặt về kích thước mỗi thành phần và có thể tràn số. So từng trường giữ đúng ý nghĩa mà không cần mã hóa nhiều trường vào một số.

## 7. Tránh phép trừ và các lỗi dấu

Một cách viết dễ mắc lỗi:

```java
return this.value - other.value;
```

Với số nguyên có phạm vi hữu hạn, phép trừ có thể tràn và cho dấu ngược với quan hệ cần biểu diễn.

Ví dụ:

```text
Integer.MIN_VALUE < 1
```

nhưng phép trừ `Integer.MIN_VALUE - 1` tràn thành một số dương. Quy tắc so sánh như vậy sẽ nói số nhỏ hơn đứng sau số lớn hơn.

Cách viết phù hợp cho trường số nguyên:

```java
return Integer.compare(this.value, other.value);
```

Tương tự, có thể dùng `Long.compare` cho `long` và những hàm so sánh đúng kiểu. Với số dấu phẩy động, cần hiểu cả chính sách đối với giá trị đặc biệt; không áp dụng tùy tiện phép trừ rồi ép sang `int`.

### Không đảo dấu một kết quả tùy ý bằng phép âm

Dùng `-other.compareTo(this)` có thể gặp vấn đề nếu kết quả là `Integer.MIN_VALUE`, vì giá trị đó không có số đối biểu diễn được bằng `int`.

Nếu cần đảo hướng so sánh, hãy sử dụng quy tắc phù hợp như đổi thứ tự đối số ở thao tác so sánh an toàn, hoặc một comparator đảo chiều. Không nên biến một nhu cầu hiển thị giảm dần thành thay đổi tùy tiện của thứ tự tự nhiên.

## 8. Tính nhất quán với equals

Một thứ tự tự nhiên **nhất quán với equals** khi:

```text
x.compareTo(y) == 0  tương đương với  x.equals(y)
```

Đây là khuyến nghị mạnh, dù giao diện `Comparable` có các kiểu thực tế được tài liệu hóa là ngoại lệ.

### Vì sao cần quan tâm?

Các cấu trúc khác nhau xác định sự trùng lặp bằng cơ chế khác nhau:

| Cấu trúc | Cơ chế chính liên quan đến sự bằng nhau |
| --- | --- |
| `HashSet`, khóa của `HashMap` | `equals` cùng hợp đồng `hashCode` |
| `TreeSet`, khóa của `TreeMap` dùng thứ tự tự nhiên | Kết quả của `compareTo` |
| `List` | Cho phép nhiều phần tử; sắp xếp không tự loại các phần tử ngang hàng |

Nếu `compareTo` chỉ xét tuổi của người nhưng `equals` xét mã người dùng, hai người khác nhau cùng tuổi có thể bị một `TreeSet` coi là cùng phần tử.

Giải pháp không phải lúc nào cũng là thêm ngẫu nhiên một trường vào cuối phép so sánh. Trước hết phải xác định **khái niệm bằng nhau của kiểu** và việc dùng tuổi có thực sự là thứ tự tự nhiên hay chỉ là một cách xem dữ liệu.

Nếu muốn nhiều người cùng tuổi vẫn được giữ, lựa chọn có thể là một danh sách được sắp xếp theo tuổi hoặc một quy tắc sắp xếp có tiêu chí phân biệt phù hợp. Cần làm rõ ý nghĩa trước khi chọn cấu trúc.

### Quan hệ với hashCode

`Comparable` không thay thế hợp đồng `equals/hashCode`.

- Nếu tự ghi đè `equals`, cần bảo đảm `hashCode` nhất quán với nó.
- `compareTo == 0` không tự thay đổi giá trị hash.
- Một quy tắc thứ tự hợp lệ về mặt toán học vẫn có thể không nhất quán với `equals`; khi đó việc dùng cấu trúc tập hợp cần được cân nhắc và tài liệu hóa.

## 9. BigDecimal: một ngoại lệ thực tế

`BigDecimal` minh họa rõ hai cách nhìn:

- `compareTo` so giá trị số, nên `4.0` và `4.00` được xem ngang hàng.
- `equals` xét cả biểu diễn thang số (scale), nên hai giá trị đó không bằng nhau theo `equals`.

Đây không phải lỗi của trình sắp xếp. Nó là khác biệt trong hợp đồng bằng nhau và thứ tự của kiểu.

Tạo tệp **`ComparisonPitfallsDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ComparisonPitfallsDemo {
    public static void main(String[] args) {
        int small = Integer.MIN_VALUE;
        int large = 1;

        System.out.println("bad subtraction sign = "
                + Integer.signum(small - large));
        System.out.println("safe comparison sign = "
                + Integer.signum(Integer.compare(small, large)));

        BigDecimal first = new BigDecimal("4.0");
        BigDecimal second = new BigDecimal("4.00");

        Set<BigDecimal> hashSet = new HashSet<>();
        hashSet.add(first);
        hashSet.add(second);

        TreeSet<BigDecimal> treeSet = new TreeSet<>();
        treeSet.add(first);
        boolean secondAdded = treeSet.add(second);

        TreeMap<BigDecimal, String> treeMap = new TreeMap<>();
        treeMap.put(first, "first");
        treeMap.put(second, "second");

        System.out.println("decimal equals = " + first.equals(second));
        System.out.println("decimal compareTo = " + first.compareTo(second));
        System.out.println("HashSet size = " + hashSet.size());
        System.out.println("TreeSet second added = " + secondAdded);
        System.out.println("TreeSet size = " + treeSet.size());
        System.out.println("TreeMap size = " + treeMap.size());
        System.out.println("TreeMap value = " + treeMap.get(first));
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ComparisonPitfallsDemo.java
java -Dfile.encoding=UTF-8 ComparisonPitfallsDemo
```

Stdout:

```text
bad subtraction sign = 1
safe comparison sign = -1
decimal equals = false
decimal compareTo = 0
HashSet size = 2
TreeSet second added = false
TreeSet size = 1
TreeMap size = 1
TreeMap value = second
```

### Đọc hai nhóm bằng chứng

**Nhóm số nguyên:**

- Phép trừ cho dấu dương sai với quan hệ thực.
- `Integer.compare` cho dấu âm đúng.

**Nhóm BigDecimal:**

- `HashSet` giữ hai phần tử vì chúng khác nhau theo `equals`.
- `TreeSet` không thêm phần tử thứ hai vì phép so sánh trả về `0`.
- `TreeMap` có một khóa theo quy tắc thứ tự; lần `put` thứ hai cập nhật giá trị tương ứng thành `"second"`.

Nếu thiết kế hệ thống dùng số thập phân làm phần tử duy nhất hoặc khóa, cần lựa chọn rõ quan hệ “cùng giá trị” cần áp dụng. Không nên coi `TreeSet` và `HashSet` là hai bản thay thế chỉ khác hiệu năng.

Không có một cách chuẩn hóa biểu diễn phù hợp mọi nghiệp vụ. Nếu hệ thống cần chuẩn hóa scale hoặc dạng biểu diễn, hãy quy định điều đó tại ranh giới dữ liệu thay vì chắp vá sau khi thấy số phần tử bị khác.

## 10. Null, kiểu không phù hợp và dữ liệu khả biến

### Null không có vị trí trong thứ tự tự nhiên của đối tượng

Theo hợp đồng `Comparable`, so một đối tượng hợp lệ với `null` cần gây `NullPointerException`.

Trong ví dụ `Version`, việc truy cập thành phần của `other` khi `other == null` dẫn tới lỗi đó. Nếu cần, có thể kiểm tra tường minh ở đầu phương thức để lỗi được diễn đạt rõ hơn.

Đừng hiểu rằng mọi thao tác sắp xếp có `null` đều luôn báo lỗi: cách thuật toán thực thi còn tùy dữ liệu, chẳng hạn có thể chưa cần so cặp nào. Thiết kế an toàn là loại bỏ/từ chối `null`, hoặc cung cấp chính sách riêng qua comparator khi muốn xếp `null` trước hay sau.

Tương tự, `TreeSet` và `TreeMap` dùng thứ tự tự nhiên không phải nơi phù hợp để lưu `null` như một giá trị khóa có thứ hạng tự nhiên.

### Không so các đối tượng khác miền một cách tùy tiện

Một tập hợp chứa lẫn số nguyên, chuỗi và đối tượng riêng không tự có thứ tự tự nhiên chung.

Generics giúp hạn chế nhiều lỗi khi biên dịch. Dùng kiểu thô rồi trộn các kiểu không so sánh được có thể chỉ đẩy lỗi thành `ClassCastException` lúc chạy.

### Không thay khóa so sánh khi đối tượng đang nằm trong cây

Giả sử một đối tượng được `TreeSet` đặt vào vị trí dựa trên giá trị hiện tại. Nếu bạn sửa trường mà `compareTo` dùng, cấu trúc không tự biết để đưa đối tượng sang vị trí khác.

Hậu quả có thể là:

- Thứ tự duyệt không còn phản ánh giá trị hiện tại.
- Tìm hoặc xóa không hoạt động như bạn dự đoán.
- Ràng buộc về phần tử duy nhất trở nên không đáng tin.

Nên ưu tiên khóa bất biến. Nếu phải đổi, hãy loại bỏ đối tượng bằng trạng thái cũ trước, thực hiện thay đổi rồi thêm lại theo quy tắc của hệ thống. Với dữ liệu đã bị thay đổi sai trong cấu trúc, không nên giả định một lệnh `remove` muộn vẫn chắc chắn tìm đúng.

Danh sách đã sắp xếp cũng không tự sắp lại khi dữ liệu phần tử thay đổi. Tuy không có vị trí cây cần bảo toàn, bạn vẫn phải sắp xếp lại trước khi dựa vào thứ tự mới.

## 11. Phân biệt Comparable với Comparator

| Câu hỏi | `Comparable<T>` | `Comparator<T>` |
| --- | --- | --- |
| Quy tắc nằm ở đâu? | Gắn với chính kiểu đối tượng | Đối tượng quy tắc bên ngoài |
| Phương thức chính | `x.compareTo(y)` | `comparator.compare(x, y)` |
| Vai trò thường dùng | Thứ tự mặc định của kiểu | Các cách xếp theo từng nhu cầu |
| Số chính sách | Một thứ tự tự nhiên được kiểu công bố | Có thể có nhiều chính sách độc lập |
| Có cần sửa lớp đối tượng? | Có, khi tự khai báo triển khai | Không nhất thiết |

Một kiểu có thể triển khai `Comparable` và vẫn được sắp bằng nhiều comparator khác nhau.

Nếu truyền comparator cho một thao tác hoặc cấu trúc, comparator đó quyết định thứ tự trong ngữ cảnh ấy. Việc lớp có `compareTo` không có nghĩa quy tắc tự nhiên luôn được gọi bất kể cấu hình.

Ví dụ `Version` chọn thứ tự số tăng dần làm mặc định. Một màn hình muốn hiển thị mới nhất trước có thể dùng chính sách đảo chiều ở thao tác sắp xếp, giữ nguyên hợp đồng tự nhiên của kiểu.

Khi comparator được dùng cho `TreeSet` hoặc `TreeMap`, kết quả so bằng không của comparator cũng có tác động đến sự duy nhất giống vai trò đã thấy với `compareTo`.

## 12. Quy trình tự thiết kế và kiểm chứng

### Bước 1: viết quy tắc bằng lời

Ví dụ:

> Phiên bản đứng trước nếu thành phần số đầu tiên khác nhau, xét theo thứ tự major, minor, patch, có giá trị nhỏ hơn.

Nếu mô tả còn mơ hồ như “đối tượng quan trọng hơn đứng trước”, cần làm rõ dữ liệu nào thể hiện độ quan trọng và cách phá hòa.

### Bước 2: thống nhất miền dữ liệu và khái niệm bằng nhau

Trả lời:

- Có chấp nhận giá trị thiếu hay `null` không?
- Đối tượng có thể thay đổi sau khi tạo không?
- Hai đối tượng được xem là cùng một giá trị khi nào?
- `compareTo == 0` có trùng với `equals` không?
- Kiểu có được dùng làm khóa hoặc phần tử trong cấu trúc cây không?

### Bước 3: cài đặt từng tiêu chí, không gộp mất ý nghĩa

Dùng thao tác so sánh đúng kiểu; trả về ngay khi tiêu chí ưu tiên tạo khác biệt. Chỉ xét tiếp khi kết quả bằng không.

### Bước 4: kiểm tra dấu và các bộ dữ liệu biên

Một bộ kiểm tra hữu ích cần có:

| Tình huống | Điều cần kiểm chứng |
| --- | --- |
| Hai giá trị nhỏ/lớn thông thường | Dấu đúng theo mô tả |
| Cùng giá trị nhưng hai đối tượng khác nhau | So bằng không; kiểm tra thêm `equals` |
| Đổi thứ tự hai đối số | Đổi dấu |
| Ba giá trị theo chuỗi tăng | Tính bắc cầu |
| Hai đối tượng ngang hàng, thêm đối tượng thứ ba | Quan hệ với đối tượng thứ ba nhất quán |
| Giá trị sát biên kiểu số | Không sai do tràn |
| Chỉ khác tiêu chí cuối | Không bỏ quên tiêu chí |
| Null hoặc dữ liệu ngoài miền | Hành vi từ chối rõ ràng |

Các kiểm tra trên một số mẫu không chứng minh hợp đồng đúng cho mọi dữ liệu. Chúng là bằng chứng thực thi cần kết hợp với cách lập luận về quy tắc.

### Bước 5: kiểm tra cả nơi sử dụng

- Sắp một danh sách rồi đối chiếu từng cặp kề nhau.
- Thử các phần tử so bằng không để xem danh sách có giữ đủ không.
- Thêm vào `TreeSet` và kiểm tra số phần tử theo quy tắc mong muốn.
- Nếu dùng làm khóa, thử `put` hai khóa ngang hàng trong `TreeMap`.
- Đối chiếu với cấu trúc dựa trên `equals/hashCode` khi cần.

## 13. Lỗi thường gặp và cách sửa

### Chỉ trả về 1 hoặc -1, không có trường hợp bằng không

Điều đó có thể khiến một đối tượng lớn hơn chính nó hoặc hai chiều cùng mang dấu không hợp lệ. Hãy xử lý tiêu chí bằng nhau và trả về `0` khi không còn khác biệt.

### Dùng phép trừ hoặc ép kiểu mất thông tin

Dùng hàm so sánh đúng kiểu thay vì biến hiệu thành kết quả so sánh. Đặc biệt, ép hiệu của `long` xuống `int` có thể làm mất phần dữ liệu quyết định dấu.

### So theo chuỗi hiển thị thay vì ý nghĩa dữ liệu

Chuỗi phiên bản là một ví dụ: `"1.10"` không được so như hai thành phần số. Hãy so dữ liệu cấu trúc nếu đó mới là ý nghĩa nghiệp vụ.

### Chọn một thuộc tính phụ làm toàn bộ thứ tự tự nhiên

Hai người cùng tuổi hoặc hai sản phẩm cùng giá chưa chắc là một giá trị. Trước khi dùng một trường như vậy, kiểm tra tác động tới `equals` và cấu trúc duy nhất.

### Dựa vào thứ tự hiện tại của danh sách

Chỉ số, thứ tự chèn hoặc vị trí trong một danh sách bên ngoài không nên trở thành phụ thuộc biến động của `compareTo`. Quy tắc phải thuộc về giá trị của đối tượng và miền so sánh đã xác định.

### Đánh đồng thứ tự ổn định với loại trùng

Sắp xếp danh sách có tính ổn định giữ thứ tự tương đối của những phần tử so bằng không. Điều đó không biến chúng thành một phần tử duy nhất; `TreeSet` mới áp dụng quy tắc duy nhất theo phép so sánh.

### Cho rằng đã triển khai Comparable thì mọi kiểu con đều tự tương thích

Thiết kế kế thừa và bổ sung tiêu chí ở kiểu con có thể tạo mâu thuẫn với lớp cha. Cần xác định miền đối tượng được so sánh và hợp đồng đối xứng; với kiểu giá trị, mô hình bất biến và giới hạn kế thừa có thể giúp giảm độ phức tạp.

## 14. Thực hành

### Bài 1: dự đoán thứ tự chuỗi

Sửa mảng chuỗi thành:

```text
v2, v10, v1, V2, v01
```

Trước khi chạy, dự đoán thứ tự tự nhiên và giải thích từng vị trí theo ký tự khác nhau đầu tiên. Phân biệt kết quả này với yêu cầu “sắp theo số phiên bản” hoặc “không phân biệt hoa thường”.

### Bài 2: kiểm chứng tiêu chí cuối

Thêm vào danh sách phiên bản:

```text
1.2.9
1.2.1
1.2.0
```

Kiểm tra rằng chỉ khi `major` và `minor` ngang nhau, `patch` mới quyết định. Sau đó thử các giá trị rất lớn nhưng vẫn nằm trong `int` để bảo đảm không có phép tính gộp hoặc phép trừ gây sai.

### Bài 3: đối chiếu bằng nhau và ngang hàng

Với hai `BigDecimal` từ chuỗi `"7.0"` và `"7.00"`:

1. Dự đoán `equals`.
2. Dự đoán dấu của `compareTo`.
3. Dự đoán kích thước khi đưa vào `HashSet`.
4. Dự đoán kích thước khi đưa vào `TreeSet`.
5. Giải thích kết quả bằng đúng hợp đồng của mỗi cấu trúc.

### Bài 4: thiết kế một kiểu giá trị mới

Thiết kế kiểu `Point` có hai tọa độ nguyên `x`, `y`, với thứ tự được chọn rõ là so `x` trước rồi `y`.

- Dùng một kiểu bất biến.
- Cài đặt `Comparable<Point>` bằng hàm so sánh an toàn.
- Bảo đảm hai điểm ngang hàng khi cả hai tọa độ bằng nhau.
- Kiểm tra sự nhất quán với `equals`.
- Sắp danh sách có điểm trùng và đối chiếu với số phần tử của `TreeSet`.
- Giải thích vì sao đây là thứ tự đã quy ước cho kiểu, không phải khoảng cách đến gốc tọa độ.

Tiêu chí hoàn thành là quy tắc, mã cài đặt và hành vi collection đều thống nhất, kể cả khi đổi chiều so sánh hoặc dùng dữ liệu ở biên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-002">Comparator và hợp đồng dấu, tính bắc cầu, tính nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

# Comparator và hợp đồng dấu, tính bắc cầu, tính nhất quán

Một `Comparator<T>` trả lời câu hỏi: **theo một quy tắc đã chọn, đối tượng nào đứng trước, đối tượng nào đứng sau, và những đối tượng nào được xem là cùng hạng?**

Ví dụ, có thể xếp các mặt hàng theo điểm tăng dần, hoặc theo điểm giảm dần rồi đến mã tăng dần. Comparator mô tả quy tắc; thuật toán sắp xếp hoặc cấu trúc dữ liệu sử dụng quy tắc đó.

Điều quan trọng là comparator phải đưa ra những câu trả lời có thể phối hợp với nhau. Một lần so sánh đúng chưa đủ: kết quả phải hợp lý khi đổi chiều hai đối số, xét bộ ba đối tượng và thay một đối tượng bằng đối tượng được xem là cùng hạng.

## 1. Đọc đúng kết quả compare

Phương thức trung tâm có dạng:

```java
int compare(T x, T y);
```

Đây là chữ ký minh họa của phương thức trong interface, không phải chương trình độc lập.

| Kết quả | Ý nghĩa theo comparator |
| --- | --- |
| Nhỏ hơn 0 | `x` đứng trước `y` |
| Bằng 0 | `x` và `y` cùng hạng theo quy tắc |
| Lớn hơn 0 | `x` đứng sau `y` |

**Chỉ dấu của số trả về có ý nghĩa đối với thứ tự.** Comparator hợp lệ có thể trả `-7`, `0`, `12`; không bắt buộc trả đúng `-1`, `0`, `1`.

Khi diễn đạt hợp đồng, dùng hàm dấu:

```text
sign(n) = -1 nếu n < 0
sign(n) =  0 nếu n = 0
sign(n) =  1 nếu n > 0
```

Trong Java, `Integer.signum(result)` thực hiện việc chuẩn hóa này.

Vì vậy, bên sử dụng kiểm tra `compare(x, y) < 0`, không kiểm tra `compare(x, y) == -1`. Nếu muốn đảo thứ tự, hãy đảo quan hệ so sánh thay vì giả định giá trị tuyệt đối của kết quả.

## 2. Ba ràng buộc cốt lõi

Đặt `c(x, y)` là kết quả của `comparator.compare(x, y)`. Các ràng buộc xét trên miền đối tượng mà comparator chấp nhận.

### 2.1. Đổi chiều phải đổi dấu

```text
sign(c(x, y)) = -sign(c(y, x))
```

Nếu `x` đứng trước `y`, đổi chiều phải nói `y` đứng sau `x`. Nếu một chiều trả 0, chiều kia cũng phải trả 0.

Điều này là **đối xứng ngược về dấu**, không phải yêu cầu hai số nguyên trả về luôn là hai số đối nhau. Chẳng hạn, trả `-2` ở một chiều và `9` ở chiều kia vẫn thỏa điều kiện dấu.

Một hệ quả cần kiểm tra là `c(x, x) == 0`: một đối tượng không thể vừa đứng trước chính nó vừa tuân thủ quy tắc đổi dấu.

Nếu việc so sánh không được hỗ trợ và một chiều ném ngoại lệ, chiều đảo lại cũng phải ném ngoại lệ. Không thiết kế trường hợp `compare(x, y)` báo lỗi nhưng `compare(y, x)` lại trả kết quả hợp lệ.

### 2.2. Thứ tự phải có tính bắc cầu

```text
c(x, y) > 0 và c(y, z) > 0  =>  c(x, z) > 0
```

Ví dụ, nếu điểm của `A` cao hơn `B`, điểm của `B` cao hơn `C`, thì quy tắc cùng chiều phải đặt `A` cao hơn `C`.

Không được tạo một vòng:

```text
0 đứng trước 1
1 đứng trước 2
2 đứng trước 0
```

Với vòng này, không tồn tại một dãy tuyến tính đáp ứng tất cả câu trả lời. Thuật toán không thể sửa một quan hệ tự mâu thuẫn thành thứ tự hợp lệ.

Điều kiện tương ứng ở chiều “đứng trước” cũng phải đúng; nó đi cùng điều kiện đổi dấu.

### 2.3. Những đối tượng cùng hạng phải ứng xử nhất quán với đối tượng thứ ba

```text
c(x, y) == 0  =>  sign(c(x, z)) == sign(c(y, z))
```

Nếu `x` và `y` cùng hạng, thay `x` bằng `y` khi so với cùng một `z` không được làm kết quả chuyển từ “trước” sang “sau”, hoặc từ “cùng hạng” sang “trước”.

Một phản ví dụ nhỏ:

| Phép so sánh | Kết quả được trả |
| --- | --- |
| `compare(0, 2)` | `0`: coi 0 và 2 cùng hạng |
| `compare(0, 1)` | Âm |
| `compare(2, 1)` | Dương |

Comparator đồng thời nói 0 và 2 cùng hạng, nhưng 1 lại nằm giữa chúng. Các câu trả lời không tương thích.

Quy tắc này mạnh hơn việc chỉ kiểm tra “hai đối tượng bằng điểm thì trả 0”. Các đối tượng được gom vào cùng một nhóm thứ tự phải được nhìn nhận giống nhau khi so với mọi nhóm khác.

Các công thức chính thức, điều kiện ngoại lệ và quy định về `equals` nằm trong [hợp đồng Comparator.compare của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#compare(T,T)).

## 3. Hai nghĩa của “nhất quán” cần tách rõ

### 3.1. Nhất quán của nhóm cùng hạng

Đây là điều kiện bắt buộc vừa nêu: khi `compare(x, y) == 0`, hai đối tượng phải so với mọi đối tượng thứ ba theo cùng dấu.

Một comparator chỉ xếp mặt hàng theo `score` có thể hoàn toàn hợp lệ. Hai mặt hàng khác mã nhưng cùng điểm được xếp cùng hạng, miễn mọi lần so sánh đều tiếp tục dùng đúng điểm đó.

Tính hợp lệ này không phụ thuộc vào việc chúng là một hay hai đối tượng, cùng hay khác mã.

### 3.2. Nhất quán với equals của các đối tượng được so sánh

Điều kiện thường mong muốn là:

```text
(compare(x, y) == 0) == x.equals(y)
```

Trong biểu thức trên, dấu `==` ở giữa so sánh hai giá trị boolean. Nó yêu cầu:

- Nếu hai đối tượng bằng nhau theo `equals`, comparator cũng trả 0.
- Nếu comparator trả 0, hai đối tượng cũng bằng nhau theo `equals`.

Đây là điều kiện **được khuyến nghị nhưng không bắt buộc đối với mọi comparator**. Nếu cố ý khác `equals`, phải hiểu và mô tả điều đó, đặc biệt khi comparator được đưa vào cây tập hợp hoặc cây ánh xạ.

Ví dụ với kiểu:

```java
record Item(String code, int score) {}
```

Hai đối tượng `new Item("A", 90)` và `new Item("B", 90)` không bằng nhau theo `equals` do `code` khác nhau. Comparator chỉ dùng `score` vẫn trả 0.

Comparator này không nhất quán với `equals` của record, nhưng không vì thế mà vi phạm ba ràng buộc về thứ tự. Nó đang xếp theo điểm, không định nghĩa định danh đầy đủ của mặt hàng.

Ngay cả comparator trả 0 cho mọi cặp trong một miền không rỗng cũng có thể đáp ứng các ràng buộc thứ tự: nó gom toàn bộ miền vào một hạng. Nó có hữu ích và phù hợp nơi sử dụng hay không là câu hỏi khác.

### 3.3. Giữ quy tắc ổn định trong khi sử dụng

Còn một yêu cầu thực hành: với cùng dữ liệu có liên quan không đổi, comparator phải tiếp tục mô tả cùng quy tắc.

Tránh dùng ngẫu nhiên, trạng thái tăng dần sau mỗi lần gọi, giờ hiện tại hoặc cấu hình đang bị thay đổi trong quá trình sắp xếp. Cũng không để luồng khác sửa trường dùng làm khóa so sánh trong lúc thuật toán đang chạy.

Điều này không được hiểu là đối tượng không bao giờ được thay đổi. Vấn đề là các lời so sánh thuộc cùng một thao tác hoặc cấu trúc phải dựa trên dữ liệu và quy tắc phù hợp với nhau.

Một comparator không có field riêng vẫn có thể mất ổn định nếu nó đọc trạng thái biến đổi của các phần tử.

## 4. Ảnh hưởng đến List, TreeSet và TreeMap

### 4.1. Sắp xếp List giữ lại các phần tử cùng hạng

Một danh sách có thể chứa nhiều mặt hàng cùng điểm. Sắp xếp theo điểm không loại chúng khỏi danh sách.

`List.sort` bảo đảm sắp xếp ổn định: những phần tử comparator xem là cùng hạng giữ thứ tự tương đối trước đó. Tuy nhiên, “ổn định” ở đây nói về cách thuật toán xử lý các phần tử hòa nhau; nó không biến comparator sai thành đúng, cũng không làm comparator trở nên nhất quán với `equals`. Xem [API List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

Nếu cần thứ tự hiển thị không phụ thuộc vào thứ tự đầu vào khi bằng điểm, hãy thêm tiêu chí phụ rõ ràng.

### 4.2. TreeSet xem compare bằng 0 là cùng phần tử theo thứ tự

Khi dùng comparator theo điểm, thêm mặt hàng mã `"A"` điểm 90 và mặt hàng mã `"B"` điểm 90 vào một `TreeSet` sẽ chỉ giữ một phần tử đại diện cho hạng điểm 90.

Điều đó không chứng minh cây bị lỗi. Cây đang dùng đúng quy tắc mà comparator cung cấp. Nếu mục tiêu là lưu mọi mặt hàng riêng biệt, chỉ dùng điểm làm toàn bộ quy tắc là lựa chọn không phù hợp.

Cũng có thể tạo chiều sai lệch ngược lại: hai đối tượng `equals` nhau nhưng comparator phân biệt chúng do dùng thêm dữ liệu không thuộc `equals`. Khi đó cây có thể giữ những đối tượng mà hợp đồng tập hợp thông thường xem là trùng.

Vì vậy, nếu cần hành vi tập hợp đúng theo `equals`, quan hệ cùng hạng của comparator phải phù hợp với `equals`. Xem [API TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

### 4.3. TreeMap áp dụng cùng nguyên tắc cho khóa

Với `TreeMap`, điều quan trọng là comparator của **khóa**. Nếu hai khóa so sánh bằng 0, `put` cho khóa sau cập nhật ánh xạ tương ứng thay vì tạo một khóa riêng trong cây.

Tính duy nhất theo khóa của `TreeMap` do thứ tự quyết định. Không thể giữ mọi bản ghi có cùng khóa so sánh bằng cách ép comparator trả một kết quả tùy ý khác 0; cách đó có thể phá hợp đồng. Hãy chọn mô hình khóa hoặc cấu trúc lưu trữ phù hợp. Xem [API TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

### 4.4. BigDecimal là ví dụ khác biệt có chủ đích

`new BigDecimal("2.0")` và `new BigDecimal("2.00")` có cùng giá trị số khi so sánh bằng `compareTo`, nhưng không bằng nhau theo `equals`, vì `equals` xét cả scale.

Bởi vậy, `HashSet` chứa hai giá trị này có thể có kích thước 2, trong khi `TreeSet` theo thứ tự tự nhiên có kích thước 1. Đây là khác biệt giữa hai hợp đồng tương đương được dùng, không phải lý do để kết luận thứ tự tự nhiên của `BigDecimal` vi phạm tính bắc cầu. Xem [API BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).

## 5. Viết comparator an toàn và rõ ý định

### 5.1. Không so sánh số nguyên bằng phép trừ

Mẫu dễ sai:

```java
Comparator<Integer> bad = (a, b) -> a - b;
```

Nếu `a` là `Integer.MIN_VALUE`, `b` là 0, phép trừ ở hai chiều đều cho một số âm do tràn miền `int`. Quan hệ đổi dấu bị phá.

`Integer.signum(a - b)` cũng không cứu được vì phép trừ đã tràn trước khi tính dấu. Tương tự, trừ hai `long` rồi ép về `int` không phải giải pháp.

Dùng:

```java
Comparator<Integer> safe = Integer::compare;
Comparator<Item> byScore = Comparator.comparingInt(Item::score);
```

Với trường `long` hoặc `double`, chọn API tương ứng như `comparingLong` hoặc `comparingDouble`. Không ép chênh lệch `double` về `int`: hai số khác nhau có thể bị quy về 0, và các giá trị đặc biệt cần quy tắc rõ ràng.

Quy tắc trả dấu của `Integer.compare` được công bố trong [API Integer.compare](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#compare(int,int)).

### 5.2. Khi hai đối tượng cùng hạng, phải trả 0

Mẫu sau sai:

```java
Comparator<Integer> bad = (a, b) -> a > b ? 1 : -1;
```

Khi `a` bằng `b`, nó vẫn trả âm. Chính một phần tử so với nó cũng không trả 0.

Không “sửa” việc `TreeSet` gộp phần tử bằng cách cố tình trả 1 hoặc -1 cho những đối tượng cùng hạng. Nếu phải giữ nhiều bản ghi, dùng danh sách hoặc thiết kế thêm tiêu chí phụ có ý nghĩa.

### 5.3. Ghép tiêu chí phụ theo thứ tự ưu tiên

Các khai báo minh họa sau dùng kiểu `Item` ở trên:

```java
Comparator<Item> byScore = Comparator.comparingInt(Item::score);

Comparator<Item> byScoreThenCode =
        byScore.thenComparing(Item::code);

Comparator<Item> scoreDescendingCodeAscending =
        byScore.reversed().thenComparing(Item::code);
```

`thenComparing` chỉ dùng tiêu chí sau khi tiêu chí trước trả 0. Nó không cộng điểm số của các phép so sánh.

Ở comparator cuối:

1. Điểm lớn đứng trước.
2. Khi bằng điểm, mã nhỏ theo thứ tự của `String` đứng trước.

Vị trí gọi `reversed()` thay đổi ý nghĩa:

```java
// Đảo toàn bộ quy tắc: điểm giảm và mã cũng giảm khi hòa điểm.
Comparator<Item> bothDescending =
        byScore.thenComparing(Item::code).reversed();
```

Trong record `Item` của ví dụ, so sánh đủ `score` và `code` giúp quan hệ bằng 0 phù hợp với `equals`, với giả định mã không null. Không suy rộng rằng cứ thêm một `thenComparing` bất kỳ là luôn nhất quán với `equals`; phải đối chiếu toàn bộ trường và quy tắc equality của kiểu đang dùng.

Để đảo một comparator có sẵn, dùng `reversed()` hoặc so hai đối số theo chiều ngược. Tránh viết `-comparator.compare(a, b)`: một kết quả hợp lệ có thể là `Integer.MIN_VALUE`, mà đổi dấu vẫn âm do tràn số.

### 5.4. Xác định chính sách null

Nếu miền dữ liệu có `null`, hãy nói rõ nó đứng đâu:

```java
Comparator<String> nullLast =
        Comparator.nullsLast(Comparator.naturalOrder());
```

Comparator trên đặt chuỗi null sau các chuỗi khác, và hai null cùng hạng.

Cần phân biệt:

- **Cả phần tử là null:** bọc comparator của phần tử.
- **Một thuộc tính của phần tử là null:** cung cấp comparator xử lý null cho thuộc tính đó.

Ví dụ minh họa với thuộc tính `code` có thể null:

```java
Comparator<Item> nullableCode = Comparator.comparing(
        Item::code,
        Comparator.nullsLast(Comparator.naturalOrder()));

Comparator<Item> nullableItem = Comparator.nullsLast(nullableCode);
```

Khai báo đầu xử lý mã null, nhưng chưa xử lý cả `Item` null. Khai báo thứ hai bổ sung lớp xử lý đó.

Đảo toàn bộ comparator còn đảo vị trí null. Chẳng hạn, `nullLast.reversed()` sẽ đặt null lên trước. Nếu muốn giảm dần các giá trị thực nhưng vẫn giữ null ở cuối, đặt `nullsLast` bao ngoài comparator giảm dần.

## 6. Chương trình hoàn chỉnh để quan sát hành vi

Lưu vào **ComparatorContractDemo.java**. Các API được dùng tương thích Java 17 trở lên:

```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class ComparatorContractDemo {
    record Item(String code, int score) {}

    private static List<String> describe(Collection<Item> items) {
        return items.stream()
                .map(item -> item.code() + ":" + item.score())
                .toList();
    }

    public static void main(String[] args) {
        Comparator<Integer> subtraction = (a, b) -> a - b;
        Comparator<Integer> safe = Integer::compare;
        int min = Integer.MIN_VALUE;

        System.out.println("Subtraction signs: "
                + Integer.signum(subtraction.compare(min, 0)) + ", "
                + Integer.signum(subtraction.compare(0, min)));
        System.out.println("Integer.compare signs: "
                + Integer.signum(safe.compare(min, 0)) + ", "
                + Integer.signum(safe.compare(0, min)));

        Item b = new Item("B", 90);
        Item a = new Item("A", 90);
        Item c = new Item("C", 75);
        List<Item> input = List.of(b, a, c);

        Comparator<Item> byScore = Comparator.comparingInt(Item::score);
        Comparator<Item> byScoreThenCode =
                byScore.thenComparing(Item::code);

        List<Item> ascending = new ArrayList<>(input);
        ascending.sort(byScoreThenCode);
        System.out.println("Ascending: " + describe(ascending));

        List<Item> descending = new ArrayList<>(input);
        descending.sort(byScore.reversed().thenComparing(Item::code));
        System.out.println("Descending score: " + describe(descending));

        System.out.println("Score tie compare: " + byScore.compare(a, b));
        System.out.println("Record equals: " + a.equals(b));

        TreeSet<Item> scoreOnly = new TreeSet<>(byScore);
        scoreOnly.addAll(input);
        System.out.println("TreeSet score only size: " + scoreOnly.size());

        TreeSet<Item> complete = new TreeSet<>(byScoreThenCode);
        complete.addAll(input);
        System.out.println("TreeSet score/code: " + describe(complete));

        List<String> names =
                new ArrayList<>(Arrays.asList("b", null, "a"));
        names.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("Nulls last: " + names);

        BigDecimal x = new BigDecimal("2.0");
        BigDecimal y = new BigDecimal("2.00");
        System.out.println("Decimal compare: " + x.compareTo(y));
        System.out.println("Decimal equals: " + x.equals(y));
        System.out.println("HashSet decimals: "
                + new HashSet<>(List.of(x, y)).size());
        System.out.println("TreeSet decimals: "
                + new TreeSet<>(List.of(x, y)).size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparatorContractDemo.java
java ComparatorContractDemo
```

Stdout:

```text
Subtraction signs: -1, -1
Integer.compare signs: -1, 1
Ascending: [C:75, A:90, B:90]
Descending score: [A:90, B:90, C:75]
Score tie compare: 0
Record equals: false
TreeSet score only size: 2
TreeSet score/code: [C:75, A:90, B:90]
Nulls last: [a, b, null]
Decimal compare: 0
Decimal equals: false
HashSet decimals: 2
TreeSet decimals: 1
```

### Đọc kết quả

**Hai dòng đầu** cho thấy comparator dùng phép trừ không giữ dấu đối nhau, còn `Integer.compare` giữ được.

**Hai danh sách đã sắp xếp** cho thấy tiêu chí điểm và mã hoạt động theo đúng thứ tự ưu tiên. Các mặt hàng bằng điểm không mất khỏi danh sách.

**`Score tie compare: 0` nhưng `Record equals: false`** là sự khác biệt có chủ đích của comparator chỉ dùng điểm.

**`TreeSet score only size: 2`** là kết quả của ba mặt hàng thuộc hai hạng điểm. Khi so tiếp mã, cây giữ đủ ba mặt hàng trong thứ tự mong muốn.

**Danh sách tên** chứng minh vị trí null được đặt cuối bằng comparator có hỗ trợ.

**Bốn dòng về BigDecimal** cho thấy một thứ tự hợp lệ có thể khác `equals`, và sự khác biệt đó làm hai cấu trúc tập hợp nhận diện trùng lặp khác nhau.

## 7. Kiểm tra hợp đồng bằng cặp và bộ ba

Chỉ đưa một danh sách vào `sort` rồi nhìn thấy kết quả có vẻ đúng là kiểm tra chưa đủ. Thuật toán có thể không thực hiện đúng những phép so sánh làm lộ lỗi trên bộ dữ liệu nhỏ đó.

Chương trình dưới kiểm tra các ràng buộc trên **một mẫu hữu hạn**. Mẫu phải nằm trong miền mà comparator có thể so sánh bình thường; chương trình không đóng vai trò bộ kiểm thử đầy đủ cho chính sách ngoại lệ.

Lưu vào **ComparatorCheckDemo.java**:

```java
import java.util.Comparator;
import java.util.List;

public class ComparatorCheckDemo {
    static <T> String firstViolation(
            Comparator<? super T> comparator, List<T> sample) {
        for (T x : sample) {
            if (comparator.compare(x, x) != 0) {
                return "self comparison";
            }
        }

        for (T x : sample) {
            for (T y : sample) {
                int xy = Integer.signum(comparator.compare(x, y));
                int yx = Integer.signum(comparator.compare(y, x));
                if (xy != -yx) {
                    return "opposite signs";
                }
            }
        }

        for (T x : sample) {
            for (T y : sample) {
                for (T z : sample) {
                    int xy = comparator.compare(x, y);
                    int xz = comparator.compare(x, z);
                    int yz = comparator.compare(y, z);

                    if (xy == 0
                            && Integer.signum(xz) != Integer.signum(yz)) {
                        return "zero consistency";
                    }
                }
            }
        }

        for (T x : sample) {
            for (T y : sample) {
                for (T z : sample) {
                    if (comparator.compare(x, y) > 0
                            && comparator.compare(y, z) > 0
                            && comparator.compare(x, z) <= 0) {
                        return "transitivity";
                    }
                }
            }
        }
        return "no violation in sample";
    }

    public static void main(String[] args) {
        Comparator<Integer> subtract = (a, b) -> a - b;

        Comparator<Integer> cyclic = (a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            if ((a == 0 && b == 1)
                    || (a == 1 && b == 2)
                    || (a == 2 && b == 0)) {
                return -1;
            }
            return 1;
        };

        Comparator<Integer> brokenZero = (a, b) -> {
            if ((a == 0 && b == 2) || (a == 2 && b == 0)) {
                return 0;
            }
            return Integer.compare(a, b);
        };

        System.out.println("Safe: " + firstViolation(
                Integer::compare,
                List.of(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE)));
        System.out.println("Subtract: " + firstViolation(
                subtract, List.of(Integer.MIN_VALUE, 0)));
        System.out.println("Cyclic: " + firstViolation(
                cyclic, List.of(0, 1, 2)));
        System.out.println("Broken zero: " + firstViolation(
                brokenZero, List.of(0, 1, 2)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparatorCheckDemo.java
java ComparatorCheckDemo
```

Stdout:

```text
Safe: no violation in sample
Subtract: opposite signs
Cyclic: transitivity
Broken zero: zero consistency
```

### 7.1. Ý nghĩa của các comparator thử nghiệm

- `Integer::compare` không bị phát hiện vi phạm trên mẫu chứa cả giá trị biên.
- `subtract` làm lộ lỗi dấu do tràn số.
- `cyclic` định nghĩa vòng 0 trước 1, 1 trước 2, 2 trước 0. Nó tự so sánh đúng và đổi dấu đúng, nhưng vi phạm bắc cầu.
- `brokenZero` coi 0 và 2 cùng hạng, trong khi vẫn đặt 1 ở giữa. Bộ kiểm tra phát hiện điều kiện nhất quán của hạng 0 bị phá.

Một comparator có thể vi phạm nhiều điều kiện cùng lúc. Hàm trả về điều kiện đầu tiên tìm được theo thứ tự kiểm tra, không khẳng định đó là lỗi duy nhất.

### 7.2. Những giới hạn phải hiểu

Các vòng lặp ba lớp có chi phí bậc ba theo số phần tử mẫu. Chúng phù hợp mẫu nhỏ để minh họa hoặc kiểm thử, không dùng để kiểm tra toàn bộ dữ liệu lớn trong luồng xử lý chính.

Không tìm thấy vi phạm trong mẫu **không phải chứng minh đúng với mọi đầu vào**. Cần bổ sung:

- Số âm, số dương, 0 và biên của kiểu số.
- Hai đối tượng khác nhau nhưng `equals` nhau.
- Hai đối tượng cùng tiêu chí chính nhưng khác tiêu chí phụ.
- Null nếu được chấp nhận, và cả hai chiều so sánh nếu null bị từ chối.
- Bộ ba có khả năng tạo vòng hoặc làm lộ nhóm cùng hạng không nhất quán.
- Dữ liệu sinh ngẫu nhiên có thể tái lập để mở rộng phạm vi kiểm thử.

Nếu thiết kế yêu cầu nhất quán với `equals`, kiểm tra thêm điều kiện đó riêng. Không đưa nó vào nhóm lỗi bắt buộc rồi kết luận mọi comparator chỉ xếp theo một thuộc tính đều sai.

Nếu comparator dùng dữ liệu từ bên ngoài, còn phải kiểm tra việc cấu hình hoặc dữ liệu có bị thay đổi trong lúc sử dụng không. Ba vòng lặp không thể tự chứng minh tính ổn định của toàn bộ hệ thống.

## 8. Khi comparator sai hoặc khóa so sánh bị thay đổi

Thuật toán sắp xếp có thể ném `IllegalArgumentException` khi phát hiện vi phạm hợp đồng. Tài liệu `Arrays.sort` cho phép cơ chế phát hiện như vậy, nhưng không bảo đảm mọi comparator sai đều được phát hiện ở mọi lần chạy. Do đó, “không có ngoại lệ” không chứng minh comparator đúng. Xem [API Arrays.sort với Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#sort(T%5B%5D,java.util.Comparator)).

Tương tự, không thay trường dùng làm khóa so sánh của phần tử đang nằm trong `TreeSet` hoặc khóa đang nằm trong `TreeMap`. Vị trí đã tổ chức theo giá trị cũ không tự được xây lại khi field thay đổi; tra cứu có thể không còn theo đường mong muốn.

Nếu cần đổi dữ liệu quyết định vị trí, lựa chọn thường gặp là:

1. Xóa phần tử hoặc ánh xạ khi khóa so sánh còn ở trạng thái cũ.
2. Thay đổi dữ liệu.
3. Thêm lại theo thứ tự mới.

Thiết kế đối tượng khóa không cho sửa các trường quyết định equality và thứ tự thường dễ kiểm soát hơn. Với cập nhật đồng thời, toàn bộ quy trình còn cần cơ chế phối hợp phù hợp; comparator không cung cấp đồng bộ cho collection.

## 9. Danh sách tự rà soát

Trước khi đưa comparator vào mã sử dụng, hãy trả lời:

1. Miền đầu vào gồm những kiểu và những giá trị nào? Null có thuộc miền không?
2. Kết quả âm, 0 và dương thể hiện đúng quy tắc nghiệp vụ nào?
3. Đổi chiều có đổi dấu, và tự so sánh có trả 0 không?
4. Có bộ ba nào tạo vòng thứ tự không?
5. Các đối tượng cùng hạng có so với mọi đối tượng thứ ba theo cùng dấu không?
6. Quan hệ bằng 0 có phù hợp với `equals` không? Nếu không, nơi sử dụng có chấp nhận không?
7. Có phép trừ, ép kiểu, dữ liệu biến đổi hoặc ngoại lệ một chiều làm hỏng quy tắc không?
8. Danh sách cần giữ những phần tử hòa nhau hay một tập/map cần nhận diện chúng là cùng khóa?
9. Tiêu chí phụ, chiều giảm dần và vị trí null đã được kiểm chứng cùng nhau chưa?

Hợp đồng comparator là nền tảng để thuật toán và cấu trúc có thứ tự dựa vào cùng một quan hệ. Viết rõ quan hệ trước, ghép các phép so sánh an toàn, rồi kiểm chứng bằng cặp và bộ ba sẽ giúp giải thích được kết quả thay vì chỉ hy vọng một lần sắp xếp chạy qua.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-003">comparing, comparingInt và các bộ so sánh theo khóa</a>

<details>
<summary>Xem chi tiết</summary>

# comparing, comparingInt và các bộ so sánh theo khóa

Khi sắp xếp một danh sách sản phẩm, ta có thể muốn sắp theo tên, số lượng tồn, giá hoặc điểm đánh giá. Đối tượng vẫn là `Product`, nhưng **khóa sắp xếp** được lấy từ một thuộc tính hoặc phép tính trên đối tượng đó.

Các phương thức `Comparator.comparing(...)`, `comparingInt(...)`, `comparingLong(...)` và `comparingDouble(...)` giúp biểu đạt quy tắc này: chỉ ra cách lấy khóa, rồi dùng quy tắc phù hợp để so sánh các khóa. Không cần lặp lại toàn bộ mã so sánh hai đối tượng mỗi khi thay tiêu chí.

Trong bài, “khóa” là giá trị phục vụ so sánh, không mặc nhiên là khóa chính, mã duy nhất hay khóa của một `Map`.

## 1. Mô hình: lấy khóa rồi so sánh khóa

Giả sử mỗi sản phẩm có số lượng tồn kho kiểu `int`:

~~~java
// Đoạn mã minh họa; Product là kiểu có phương thức stock() trả int.
Comparator<Product> byStock = Comparator.comparingInt(Product::stock);
~~~

Khi gọi:

~~~java
// left và right là hai Product không null.
int result = byStock.compare(left, right);
~~~

Có thể hình dung ba bước:

1. Lấy `left.stock()`.
2. Lấy `right.stock()`.
3. So sánh hai giá trị `int` để quyết định thứ tự của hai sản phẩm.

Một cách viết dài hơn, có cùng ý nghĩa so sánh trong trường hợp này:

~~~java
Comparator<Product> byStock =
        (left, right) -> Integer.compare(left.stock(), right.stock());
~~~

Dấu của kết quả có ý nghĩa:

| Kết quả `compare(left, right)` | Ý nghĩa theo comparator |
| --- | --- |
| Âm | `left` đứng trước `right`. |
| Bằng `0` | Hai đối tượng ngang nhau theo các tiêu chí comparator đang xét. |
| Dương | `left` đứng sau `right`. |

Không kiểm tra comparator chỉ bằng `result == -1` hoặc `result == 1`. Hợp đồng dùng dấu, không yêu cầu mọi comparator chỉ trả đúng ba giá trị `-1, 0, 1`.

Việc tạo comparator không tự thay đổi danh sách:

~~~java
Comparator<Product> byStock = Comparator.comparingInt(Product::stock);

// Chưa có thao tác sắp xếp nào xảy ra ở dòng trên.
products.sort(byStock); // Đến đây mới sắp xếp danh sách có thể sửa.
~~~

Bài này dùng các API có từ Java 8 và ví dụ chương trình hoàn chỉnh chạy được trên Java 17 trở lên. Chữ ký các biến thể được đối chiếu với [Java SE 17 Comparator API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

## 2. comparing với thứ tự tự nhiên của khóa

Dạng một tham số nhận hàm lấy khóa có kiểu triển khai `Comparable`:

~~~java
static <T, U extends Comparable<? super U>>
Comparator<T> comparing(
        Function<? super T, ? extends U> keyExtractor
)
~~~

Đây là chữ ký API để đọc hiểu, không phải phương thức cần tự viết trong ứng dụng.

- `T`: kiểu đối tượng được sắp xếp, chẳng hạn `Product`.
- `U`: kiểu khóa lấy ra, chẳng hạn `String`.
- `keyExtractor`: hàm nhận đối tượng và trả khóa.
- Kết quả vẫn là `Comparator<T>`, không phải `Comparator<U>`.

Ví dụ:

~~~java
// Product.name() trả String.
Comparator<Product> byName = Comparator.comparing(Product::name);
~~~

`Product` không cần triển khai `Comparable<Product>`. Chỉ khóa `String` của dạng `comparing` này cần có thứ tự tự nhiên.

Method reference `Product::name` biểu đạt cách lấy tên từ một sản phẩm. Trong ngữ cảnh này, nó tương đương với lambda:

~~~java
Comparator<Product> byName =
        Comparator.comparing((Product product) -> product.name());
~~~

Comparator so sánh sản phẩm theo các chuỗi tên. Nó không “sắp xếp các trường bên trong từng Product”, không đổi tên và không tạo một danh sách tên thay cho danh sách sản phẩm.

### Các loại khóa đối tượng thường gặp

Có thể lấy `String`, `LocalDate`, `Instant`, `BigDecimal` hoặc một kiểu khác có thứ tự phù hợp. Chỉ chọn được API chưa đủ: phải xác nhận thứ tự của khóa đúng với nghiệp vụ.

Ví dụ, thứ tự tự nhiên của `String` không tự trở thành thứ tự ngôn ngữ tiếng Việt. Nếu cần sắp tên theo quy tắc ngôn ngữ, phải xác định một quy tắc so sánh phù hợp thay vì giả định `String.compareTo` giải quyết mọi trường hợp.

## 3. comparing với comparator riêng cho khóa

Dạng hai tham số tách rõ cách lấy khóa và cách so sánh khóa:

~~~java
static <T, U>
Comparator<T> comparing(
        Function<? super T, ? extends U> keyExtractor,
        Comparator<? super U> keyComparator
)
~~~

Trong dạng này, khóa không bắt buộc phải tự triển khai `Comparable`, vì đã có `keyComparator` chỉ ra cách so sánh.

Ví dụ so sánh tên không phân biệt chữ hoa–thường:

~~~java
Comparator<Product> byNameIgnoringCase =
        Comparator.comparing(
                Product::name,
                String.CASE_INSENSITIVE_ORDER
        );
~~~

Có hai comparator với hai vai trò:

- `String.CASE_INSENSITIVE_ORDER` so sánh **hai khóa String**.
- `byNameIgnoringCase` so sánh **hai Product**, sau khi lấy tên của chúng.

Không truyền `Comparator<Product>` vào vị trí comparator cho khóa `String`. Kiểu của comparator phụ thuộc vào thứ được so sánh ở từng tầng.

### Khi khóa là một kiểu không có thứ tự tự nhiên

Giả sử một chương trình có:

~~~java
// Minh họa các khai báo trong một lớp; cần import java.util.Comparator.
record Category(int rank, String label) {}
record Item(String code, Category category) {}

Comparator<Category> categoryOrder =
        Comparator.comparingInt(Category::rank)
                .thenComparing(Category::label);

Comparator<Item> itemOrder =
        Comparator.comparing(Item::category, categoryOrder);
~~~

`Category` không khai báo `implements Comparable<Category>`. Vì vậy, `Comparator.comparing(Item::category)` một tham số không có đủ cơ sở để dùng thứ tự tự nhiên của `Category`.

Dạng hai tham số giải quyết đúng vấn đề: lấy `Category` từ `Item`, rồi so sánh bằng `categoryOrder`. Các ví dụ này giả định đối tượng và khóa không `null`; cách xử lý `null` được trình bày riêng ở dưới.

## 4. comparingInt: khóa int, đối tượng vẫn là T

Chữ ký:

~~~java
static <T> Comparator<T> comparingInt(
        ToIntFunction<? super T> keyExtractor
)
~~~

`ToIntFunction<T>` là hàm nhận một `T` và trả một giá trị nguyên thủy `int`. So với dạng `Function<T, Integer>`, nó không cần biểu diễn khóa bằng đối tượng `Integer`.

~~~java
Comparator<Product> byStock = Comparator.comparingInt(Product::stock);
~~~

Chọn `comparingInt` khi khóa tự nhiên của bài toán là `int`, chẳng hạn số lượng, số lần thử hoặc độ dài chuỗi.

Có thể lấy khóa bằng một phép tính:

~~~java
Comparator<Product> byNameLength =
        Comparator.comparingInt(product -> product.name().length());
~~~

Ở đây, sản phẩm có tên ngắn hơn đứng trước. Hai tên khác nhau nhưng cùng độ dài sẽ được comparator xem là ngang nhau nếu chưa thêm tiêu chí phụ.

### comparing với int có nhất thiết sai không?

Không. Đoạn sau có thể biên dịch:

~~~java
Comparator<Product> boxed =
        Comparator.comparing(Product::stock);
~~~

Vì `comparing` cần khóa đối tượng, giá trị `int` có thể được boxing thành `Integer`, rồi so sánh bằng thứ tự tự nhiên của `Integer`.

Tuy nhiên, khi ý định rõ là so sánh số nguyên thủy, `comparingInt` diễn đạt trực tiếp hơn và tránh yêu cầu boxing của đường so sánh khóa. Không nên từ đó khẳng định mọi chương trình chắc chắn nhanh hơn một mức cố định: chi phí thực tế còn phụ thuộc dữ liệu, số lần so sánh, JVM và các công việc khác trong hàm lấy khóa.

Quan trọng hơn, `comparingInt` **không** biến `Comparator<Product>` thành một comparator dành cho `Integer`, và không làm thay đổi kiểu dữ liệu lưu trong danh sách.

## 5. Các biến thể cho long và double

| Kiểu khóa phù hợp | API tạo comparator | Kiểu hàm lấy khóa |
| --- | --- | --- |
| Đối tượng có thứ tự tự nhiên | `comparing(extractor)` | `Function<? super T, ? extends U>` |
| Đối tượng với quy tắc so sánh riêng | `comparing(extractor, keyComparator)` | `Function` cùng comparator cho khóa |
| `int` | `comparingInt(extractor)` | `ToIntFunction<? super T>` |
| `long` | `comparingLong(extractor)` | `ToLongFunction<? super T>` |
| `double` | `comparingDouble(extractor)` | `ToDoubleFunction<? super T>` |

Ví dụ:

~~~java
Comparator<Product> byPrice =
        Comparator.comparingLong(Product::priceInCents);

Comparator<Product> byRating =
        Comparator.comparingDouble(Product::rating);
~~~

Chọn API theo kiểu khóa và ý nghĩa dữ liệu. Đừng ép một giá trị `long` thành `int` chỉ để dùng `comparingInt`; việc thu hẹp kiểu có thể làm mất thông tin và đổi thứ tự.

Tương tự, không chuyển dữ liệu cần độ chính xác nguyên vẹn từ `long` sang `double` một cách tùy tiện. Chữ ký chấp nhận một phép chuyển kiểu không có nghĩa phép chuyển đó giữ nguyên ý nghĩa của mọi giá trị.

Với tiền trong ví dụ chương trình, `priceInCents` là một giá trị `long` tính theo đơn vị nhỏ nhất của hệ thống mẫu. Đây là quyết định mô hình dữ liệu của ví dụ, không phải quy tắc bắt buộc mọi ứng dụng tiền tệ đều phải dùng `long`.

`comparingDouble` thực hiện so sánh số theo hành vi so sánh `double` của Java. Nếu miền dữ liệu có `NaN`, vô cực hoặc hai dạng zero, cần xác định liệu các giá trị đó có hợp lệ với nghiệp vụ không. Không tự viết comparator bằng cách trừ hai `double` rồi ép sang `int`: hai số khác nhau có thể bị biến thành kết quả bằng `0`, chưa kể các giá trị đặc biệt.

Không có nhóm factory riêng tên `comparingBoolean`, `comparingFloat` hay `comparingShort` trong interface `Comparator`. Với khóa kiểu khác, chọn cách ánh xạ hoặc comparator thích hợp và nói rõ ý nghĩa thứ tự. Chẳng hạn, `float` có thể được mở rộng sang `double` để so sánh, còn thứ tự ưu tiên của một trạng thái boolean thường nên được diễn đạt theo yêu cầu nghiệp vụ.

## 6. Không dùng phép trừ để thay phép so sánh số

Cách viết dễ mắc lỗi:

~~~java
Comparator<Product> unsafe =
        (left, right) -> left.stock() - right.stock();
~~~

Phép trừ `int` có thể tràn. Với:

~~~text
left.stock()  = Integer.MIN_VALUE
right.stock() = Integer.MAX_VALUE
~~~

Về giá trị, bên trái nhỏ hơn bên phải. Nhưng phép trừ `int` trong Java cho kết quả `1`, khiến comparator trả dấu dương và khẳng định điều ngược lại.

Cách viết an toàn cho tiêu chí này:

~~~java
Comparator<Product> byStock =
        Comparator.comparingInt(Product::stock);

// Hoặc khi thật sự cần viết lambda:
Comparator<Product> byStockExplicit =
        (left, right) -> Integer.compare(left.stock(), right.stock());
~~~

Với `long`, cũng không viết:

~~~java
// Ví dụ sai: có thể tràn ở phép trừ hoặc mất thông tin khi ép về int.
Comparator<Product> unsafePrice =
        (left, right) -> (int) (left.priceInCents() - right.priceInCents());
~~~

Dùng `comparingLong` hoặc `Long.compare`. Mục tiêu là tạo dấu so sánh đúng, không phải lấy chênh lệch số học rồi hy vọng dấu vẫn đúng sau mọi phép biến đổi.

## 7. Thêm tiêu chí phụ bằng thenComparing

Nếu chỉ sắp theo số lượng tồn, nhiều sản phẩm có cùng `stock`. Khi muốn cùng số lượng thì theo mã:

~~~java
Comparator<Product> byStockThenCode =
        Comparator.comparingInt(Product::stock)
                .thenComparing(Product::code);
~~~

Quá trình so sánh:

1. So sánh `stock`.
2. Nếu kết quả khác `0`, dùng kết quả đó.
3. Chỉ khi `stock` bằng nhau, mới xét `code`.

Đây là so sánh lần lượt theo tiêu chí, không phải cộng các kết quả so sánh hoặc cộng giá trị các khóa.

Những biến thể có tên tương ứng giúp nối thêm khóa nguyên thủy:

~~~java
Comparator<Product> order =
        Comparator.comparing(Product::category)
                .thenComparingInt(Product::stock)
                .thenComparingLong(Product::priceInCents)
                .thenComparingDouble(Product::rating)
                .thenComparing(Product::code);
~~~

Ví dụ này giả định `category` và `code` không `null`; nó minh họa cú pháp, không phải quy tắc sắp xếp bắt buộc của một ứng dụng bán hàng.

Cũng có thể chỉ định quy tắc riêng cho khóa phụ:

~~~java
Comparator<Product> order =
        Comparator.comparingInt(Product::stock)
                .thenComparing(
                        Product::name,
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(Product::code);
~~~

Nếu hai sản phẩm ngang nhau theo tất cả các khóa đã chọn, comparator trả `0`. Không nên tự thêm một tiêu chí ngẫu nhiên để “bắt buộc khác nhau”; hãy xác định đúng yêu cầu về thứ tự và định nghĩa tương đương của dữ liệu.

## 8. Giảm dần: reversed đặt ở đâu thì đảo phần đó

Giảm dần theo số lượng tồn, sau đó tăng dần theo giá:

~~~java
Comparator<Product> stockDescPriceAsc =
        Comparator.comparingInt(Product::stock)
                .reversed()
                .thenComparingLong(Product::priceInCents)
                .thenComparing(Product::code);
~~~

`reversed()` ở đây chỉ đảo comparator số lượng đã tạo trước đó. Các tiêu chí nối sau vẫn theo chiều tăng dần.

Ngược lại:

~~~java
Comparator<Product> allDesc =
        Comparator.comparingInt(Product::stock)
                .thenComparingLong(Product::priceInCents)
                .thenComparing(Product::code)
                .reversed();
~~~

Dòng cuối đảo **toàn bộ chuỗi** comparator đã được ghép, gồm cả giá và mã.

Cần đọc chuỗi từ trái sang phải theo các comparator đã được hình thành, không chỉ đếm số lần có từ `reversed`.

Với khóa đối tượng, có thể đảo riêng khóa bằng dạng hai tham số:

~~~java
Comparator<Product> nameDescending =
        Comparator.comparing(Product::name, Comparator.reverseOrder());
~~~

Đây là chiều ngược của thứ tự tự nhiên của tên. Nó không thay thế một quy tắc so sánh ngôn ngữ hoặc quy tắc không phân biệt chữ hoa–thường nếu nghiệp vụ cần những quy tắc đó.

## 9. Null của phần tử và null của khóa là hai vấn đề

Xét một danh sách có thể chứa:

- Một `Product` không `null`, nhưng `product.category()` trả `null`.
- Chính phần tử `Product` là `null`.

Hai trường hợp thất bại ở hai tầng khác nhau.

### Khóa có thể null

Muốn sản phẩm chưa có nhóm đứng cuối:

~~~java
Comparator<Product> byCategory =
        Comparator.comparing(
                Product::category,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).thenComparing(Product::code);
~~~

Comparator bọc bên trong là `Comparator<String>` dành cho khóa `category`. Nó xử lý `null` của khóa.

Đoạn mã chưa xử lý một phần tử `Product` là `null`: khi cần gọi `Product::category` trên phần tử đó, vẫn có lỗi.

### Phần tử cũng có thể null

Bọc comparator hoàn chỉnh ở ngoài:

~~~java
Comparator<Product> safe =
        Comparator.nullsLast(byCategory);
~~~

Tầng ngoài xử lý `null` của `Product`. Với các sản phẩm khác `null`, nó giao việc so sánh cho `byCategory`.

Một cấu trúc dễ nhớ:

~~~text
null của Product?
    -> comparator bọc bên ngoài

Product tồn tại nhưng category null?
    -> comparator dành cho khóa category
~~~

Nếu `code` cũng có thể `null`, tiêu chí phụ `thenComparing(Product::code)` cần có chính sách `null` riêng. Một tầng xử lý `null` cho `category` không tự bao phủ mọi khóa khác.

### Hàm lấy khóa lồng nhau

Với biểu thức như:

~~~java
product -> product.category().label()
~~~

Nếu `category()` trả `null`, lỗi xảy ra ngay trong hàm lấy khóa, trước khi comparator của khóa có thể xử lý giá trị trả về.

Cần chọn một trong các thiết kế phù hợp:

- Không cho phép thiếu `category` và kiểm tra dữ liệu từ trước.
- Cho hàm lấy khóa trả `null` khi thiếu đối tượng trung gian, rồi dùng comparator hỗ trợ `null`.
- So sánh trực tiếp đối tượng `Category` bằng một comparator có xử lý `null`.

Không dùng chuỗi rỗng hoặc một giá trị số tùy ý để thay `null` nếu điều đó làm lẫn “thiếu dữ liệu” với một dữ liệu hợp lệ.

### Đảo thứ tự có thể đổi vị trí null

Đảo một comparator đã đặt `null` ở cuối sẽ đảo cả quan hệ đó, làm `null` về đầu.

Nếu muốn khóa khác `null` giảm dần nhưng `null` vẫn đứng cuối, hãy đặt:

~~~java
Comparator<Product> categoryDescendingNullLast =
        Comparator.comparing(
                Product::category,
                Comparator.nullsLast(Comparator.reverseOrder())
        );
~~~

Đây là một lý do nữa để xác định rõ vị trí của từng lớp bọc.

## 10. Kiểu generic, lambda và suy luận kiểu

Method reference thường làm kiểu rõ ràng:

~~~java
Comparator<Product> byStock = Comparator.comparingInt(Product::stock);
~~~

Trong một số chuỗi gọi phức tạp, lambda không có kiểu tham số rõ ràng có thể khiến trình biên dịch chưa suy ra `Product` ở điểm cần thiết. Có thể cung cấp thông tin kiểu bằng một trong hai cách:

~~~java
Comparator<Product> first =
        Comparator.comparingInt((Product product) -> product.stock())
                .reversed();

Comparator<Product> second =
        Comparator.<Product>comparingInt(product -> product.stock())
                .reversed();
~~~

Không sửa lỗi suy luận bằng cách dùng raw type `Comparator` rồi ép kiểu. Cách đó làm mất kiểm tra kiểu mà generic vốn cung cấp.

Với dạng `comparing` hai tham số, nhớ rằng `T` là kiểu phần tử còn `U` là kiểu khóa. Hàm lấy khóa và comparator của khóa phải thống nhất về `U`.

Dùng `var` không có nghĩa Java luôn đoán được kiểu phần tử từ một lambda chưa được định kiểu. Khi thiếu ngữ cảnh, hãy dùng method reference có kiểu rõ, kiểu tham số lambda, hoặc đối số kiểu tường minh.

## 11. Hàm lấy khóa cần ổn định và có chi phí phù hợp

Các factory không hứa chỉ gọi hàm lấy khóa đúng một lần cho mỗi phần tử trong toàn bộ quá trình sort. Sort có thể so sánh cùng phần tử nhiều lần.

Vì vậy, tránh hàm lấy khóa:

- Gọi mạng hoặc truy vấn cơ sở dữ liệu.
- Đọc giá trị thời gian hiện tại để quyết định thứ tự.
- Thay đổi thuộc tính của đối tượng mỗi lần được gọi.
- Phụ thuộc một biến toàn cục đang thay đổi.
- Tạo giá trị ngẫu nhiên.

Nếu khóa thay đổi giữa các lần so sánh, tính nhất quán của comparator có thể bị phá vỡ. Nếu khóa chỉ đắt đỏ nhưng ổn định, thời gian sort có thể bị chi phối bởi việc tính lại khóa.

Với khóa tính toán tốn kém, cân nhắc tính trước thành dữ liệu phụ dùng trong lần sắp xếp:

~~~java
// Minh họa ý tưởng, không phải chương trình độc lập.
record RankedProduct(Product product, int computedRank) {}

Comparator<RankedProduct> order =
        Comparator.comparingInt(RankedProduct::computedRank)
                .thenComparing(value -> value.product().code());
~~~

Bước tạo các `RankedProduct` sẽ tính khóa theo quy tắc nghiệp vụ, rồi sort chỉ đọc lại khóa đã có. Cách này tăng bộ nhớ và cần xác định thời điểm chụp dữ liệu. Không nên thêm bộ nhớ đệm toàn cục chỉ để tránh tính lại mà bỏ qua việc khóa có thể thay đổi.

Các factory cũng không bảo vệ khóa khỏi thay đổi sau khi đã đưa phần tử vào một `TreeSet` hoặc cấu trúc dựa trên comparator. Nếu khóa quyết định vị trí bị thay đổi tại chỗ, phải cập nhật cấu trúc theo một quy trình đúng, thay vì chờ comparator tự di chuyển đối tượng.

## 12. Chương trình hoàn chỉnh

Lưu thành **KeyComparatorDemo.java**. Dữ liệu mẫu dùng:

- `name`: khóa `String`.
- `stock`: khóa `int`.
- `priceInCents`: khóa `long`.
- `rating`: khóa `double`.
- `category`: khóa `String` có thể `null`.
- `code`: tiêu chí phụ để kết quả trong ví dụ được xác định rõ.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KeyComparatorDemo {
    record Product(
            String code,
            String name,
            int stock,
            long priceInCents,
            double rating,
            String category
    ) {}

    public static void main(String[] args) {
        List<Product> products = List.of(
                new Product("P3", "banana", 4, 250L, 4.6, "food"),
                new Product("P1", "Apple", 10, 500L, 4.2, "food"),
                new Product("P4", "cable", 4, 1000L, 4.8, null),
                new Product("P2", "apple", 4, 300L, 4.2, "tools")
        );

        Comparator<Product> byName = Comparator.comparing(Product::name);
        Comparator<Product> byNameIgnoringCase =
                Comparator.comparing(
                        Product::name,
                        String.CASE_INSENSITIVE_ORDER
                ).thenComparing(Product::code);

        Comparator<Product> byStockThenCode =
                Comparator.comparingInt(Product::stock)
                        .thenComparing(Product::code);

        Comparator<Product> byPrice =
                Comparator.comparingLong(Product::priceInCents)
                        .thenComparing(Product::code);

        Comparator<Product> byRatingDescending =
                Comparator.comparingDouble(Product::rating)
                        .reversed()
                        .thenComparing(Product::code);

        Comparator<Product> byStockDescendingThenPrice =
                Comparator.comparingInt(Product::stock)
                        .reversed()
                        .thenComparingLong(Product::priceInCents)
                        .thenComparing(Product::code);

        Comparator<Product> wholeOrderReversed =
                Comparator.comparingInt(Product::stock)
                        .thenComparingLong(Product::priceInCents)
                        .thenComparing(Product::code)
                        .reversed();

        Comparator<Product> byNullableCategory =
                Comparator.comparing(
                        Product::category,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(Product::code);

        printCodes("Name natural", products, byName);
        printCodes("Name ignoring case", products, byNameIgnoringCase);
        printCodes("Stock then code", products, byStockThenCode);
        printCodes("Price", products, byPrice);
        printCodes("Rating descending", products, byRatingDescending);
        printCodes("Stock descending, price ascending",
                products, byStockDescendingThenPrice);
        printCodes("Whole chain reversed", products, wholeOrderReversed);
        printCodes("Category nulls last", products, byNullableCategory);

        List<Product> includingNull = new ArrayList<>(products);
        includingNull.add(null);

        printCodes("Null elements last",
                includingNull, Comparator.nullsLast(byNullableCategory));

        System.out.println("Original = " + codes(products));

        Product min = new Product(
                "MIN", "min", Integer.MIN_VALUE, 0L, 0.0, "demo");
        Product max = new Product(
                "MAX", "max", Integer.MAX_VALUE, 0L, 0.0, "demo");

        Comparator<Product> bad =
                (left, right) -> left.stock() - right.stock();
        Comparator<Product> safe =
                Comparator.comparingInt(Product::stock);

        System.out.println("Subtraction sign = "
                + Integer.signum(bad.compare(min, max)));
        System.out.println("comparingInt sign = "
                + Integer.signum(safe.compare(min, max)));
    }

    private static void printCodes(
            String label,
            List<Product> source,
            Comparator<? super Product> order
    ) {
        List<Product> copy = new ArrayList<>(source);
        copy.sort(order);
        System.out.println(label + " = " + codes(copy));
    }

    private static List<String> codes(List<Product> products) {
        List<String> result = new ArrayList<>();
        for (Product product : products) {
            result.add(product == null ? "<null>" : product.code());
        }
        return result;
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 KeyComparatorDemo.java
java KeyComparatorDemo
~~~

Stdout:

~~~text
Name natural = [P1, P2, P3, P4]
Name ignoring case = [P1, P2, P3, P4]
Stock then code = [P2, P3, P4, P1]
Price = [P3, P2, P1, P4]
Rating descending = [P4, P3, P1, P2]
Stock descending, price ascending = [P1, P3, P2, P4]
Whole chain reversed = [P1, P4, P2, P3]
Category nulls last = [P1, P3, P2, P4]
Null elements last = [P1, P3, P2, P4, <null>]
Original = [P3, P1, P4, P2]
Subtraction sign = 1
comparingInt sign = -1
~~~

### Giải thích các kết quả quan trọng

- Hai dòng tên có cùng dãy mã trong bộ dữ liệu mẫu, nhưng quy tắc khác nhau: `"Apple"` và `"apple"` được so sánh khác theo thứ tự tự nhiên; quy tắc không phân biệt hoa–thường xem chúng ngang nhau và dùng mã làm tiêu chí phụ.
- Khi sắp theo tồn kho rồi mã, nhóm tồn `4` đứng trước nhóm tồn `10`; trong nhóm tồn `4`, mã lần lượt là `P2, P3, P4`.
- Giá tăng dần là `250, 300, 500, 1000`, tương ứng `P3, P2, P1, P4`.
- Điểm giảm dần đặt `P4` trước `P3`. `P1` và `P2` cùng điểm `4.2`, nên mã giải quyết thứ tự.
- Với tồn kho giảm dần rồi giá tăng dần, `P1` đứng đầu; nhóm tồn `4` đi theo giá `250, 300, 1000`.
- Đảo cả chuỗi làm nhóm tồn `4` đi theo chiều ngược của giá tăng dần, nên kết quả là `P4, P2, P3`.
- Nhóm `category == null` đứng sau các nhóm có tên; một phần tử `Product == null` được xử lý bởi comparator bọc ngoài và đứng cuối cùng.
- `printCodes` tạo bản sao trước khi sort. Dòng `Original` xác nhận danh sách nguồn giữ nguyên dãy mã ban đầu.
- Hai dòng cuối chứng minh bằng dữ liệu biên: phép trừ cho dấu `1` sai ý nghĩa, còn `comparingInt` cho dấu `-1` đúng với quan hệ `MIN_VALUE < MAX_VALUE`.

## 13. Comparator chỉ theo khóa và ý nghĩa compare bằng 0

Nếu chỉ dùng:

~~~java
Comparator<Product> byStock = Comparator.comparingInt(Product::stock);
~~~

thì mọi sản phẩm có cùng tồn kho đều có thể cho kết quả so sánh bằng `0`, dù khác mã, tên hoặc giá.

Với việc sort một danh sách, điều này không tự xóa bớt sản phẩm. Một phép sắp xếp ổn định giữ thứ tự tương đối trước sort của những phần tử được comparator xem là ngang nhau.

Nhưng nếu dùng comparator đó cho `TreeSet<Product>`, cấu trúc dùng kết quả so sánh để xác định vị trí tương đương. Hai sản phẩm cùng tồn kho có thể chỉ chiếm một vị trí trong tập hợp. Vì vậy, một comparator hợp lý cho **trình bày danh sách theo một tiêu chí** chưa chắc hợp lý cho **tập hợp xác định tính duy nhất**.

Chỉ thêm `thenComparing(Product::code)` khi mã thực sự là tiêu chí phân biệt phù hợp. Nếu cần comparator nhất quán với `equals`, phải đối chiếu toàn bộ định nghĩa `equals` của kiểu dữ liệu, không chỉ thêm một trường rồi cho rằng mọi trường hợp đều được giải quyết.

## 14. Kiểm tra một comparator theo khóa

Bắt đầu bằng dữ liệu nhỏ để người đọc có thể tự tính kết quả:

1. Hai phần tử có khóa khác nhau.
2. Hai phần tử có khóa chính bằng nhau nhưng khóa phụ khác nhau.
3. Hai phần tử ngang nhau theo toàn bộ tiêu chí.
4. Giá trị `int` hoặc `long` ở hai biên nếu có xử lý số.
5. Khóa `null`, đối tượng `null`, và đối tượng trung gian `null` nếu miền dữ liệu cho phép.
6. Thứ tự giảm dần với `null` để kiểm tra phạm vi `reversed()`.

Có thể kiểm tra các thuộc tính của quan hệ so sánh:

- Đổi chỗ hai đối tượng phải đảo dấu, trừ trường hợp cả hai chiều đều bằng `0`.
- Nếu `a` đứng sau `b`, `b` đứng sau `c`, thì `a` phải đứng sau `c`.
- Nếu `a` và `b` ngang nhau, chúng phải có cùng quan hệ về dấu khi được so với một `c` khác.

Với các factory, lỗi thường nằm ở quy tắc khóa, dữ liệu, lớp bọc hoặc chuỗi kết hợp hơn là ở việc tự viết thuật toán so sánh. Kiểm thử cần phản ánh đúng quy tắc nghiệp vụ, không chỉ chứng minh danh sách có thể chạy qua `sort`.

## 15. Lỗi thường gặp

| Cách viết hoặc cách hiểu | Vấn đề | Cách xử lý |
| --- | --- | --- |
| Gọi `comparing` rồi nghĩ danh sách đã được sort | Mới chỉ tạo một comparator | Truyền comparator cho thao tác cần nó. |
| Dùng `comparingInt` với lambda trả `long` | Sai kiểu khóa hoặc phải ép kiểu gây mất dữ liệu | Dùng `comparingLong`. |
| Dùng phép trừ làm kết quả so sánh | Có thể tràn số hoặc mất thông tin | Dùng factory số nguyên thủy hoặc hàm `compare`. |
| Dùng `comparing` một tham số cho khóa không có `Comparable` | Không có thứ tự tự nhiên để dùng | Truyền thêm comparator cho khóa. |
| Chỉ bọc `nullsLast` ở ngoài | Xử lý phần tử `null`, chưa xử lý khóa `null` | Đặt quy tắc ở từng tầng thực sự có thể thiếu dữ liệu. |
| Chỉ bọc `nullsLast` ở comparator khóa | Không ngăn gọi extractor trên phần tử `null` | Bọc comparator đối tượng nếu cần. |
| Gọi `reversed` cuối chuỗi trong khi chỉ muốn giảm một khóa | Đảo toàn bộ thứ tự đã ghép | Đảo comparator thành phần trước khi nối tiêu chí tiếp theo. |
| Khóa phụ đắt đỏ được tính sẵn cho mọi lần so sánh dù chưa cần | Tăng chi phí và có thể tạo tác dụng phụ | Dùng chuỗi comparator đúng ý nghĩa; cân nhắc tính trước khóa ở cấp dữ liệu khi phù hợp. |
| Đổi khóa của phần tử khi nó nằm trong cấu trúc có thứ tự | Vị trí cấu trúc không tự được cập nhật | Thiết kế thao tác lấy ra, cập nhật và thêm lại hoặc dùng dữ liệu bất biến. |
| Dùng comparator của danh sách cho TreeSet mà không xét tính duy nhất | Có thể mất những phần tử có khóa bằng nhau | Xác định lại quan hệ tương đương hoặc chọn cấu trúc khác. |

## 16. Thực hành

1. Thêm quy tắc: tên không phân biệt hoa–thường tăng dần, tồn kho giảm dần, rồi mã tăng dần. Viết comparator và giải thích phạm vi mỗi lần `reversed`.
2. Viết comparator cho sản phẩm theo độ dài tên, rồi tên theo thứ tự tự nhiên. Nêu các trường hợp phải xử lý nếu tên có thể `null`.
3. Sửa ví dụ để `category` giảm dần nhưng khóa `null` vẫn đứng cuối, còn phần tử `null` đứng đầu.
4. Tạo hai sản phẩm khác mã nhưng cùng tồn kho. So sánh kết quả khi dùng comparator theo tồn cho `List` và cho `TreeSet`.
5. Tạo một kiểu khóa không triển khai `Comparable`; viết comparator cho khóa rồi dùng dạng hai tham số của `comparing`.
6. Nếu khóa là một điểm đánh giá phải tính rất tốn kém, hãy thiết kế cách chụp khóa một lần cho mỗi phần tử trong một lượt xử lý và nêu cách xử lý khi dữ liệu gốc thay đổi.

Trước khi chọn factory, hãy trả lời ba câu: đang so sánh kiểu đối tượng nào, khóa lấy ra có kiểu gì, và quy tắc nào quyết định thứ tự giữa các khóa đó. Sau đó mới xét tiêu chí phụ, chiều tăng–giảm và chính sách dữ liệu thiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-004">thenComparing và sắp xếp nhiều tiêu chí</a>

<details>
<summary>Xem chi tiết</summary>

# thenComparing và sắp xếp nhiều tiêu chí

Một tiêu chí thường chưa đủ để trình bày dữ liệu theo ý nghĩa nghiệp vụ. Trong danh sách công việc, ta có thể muốn:

1. Độ ưu tiên cao hơn đứng trước.
2. Nếu cùng độ ưu tiên, hạn xử lý sớm hơn đứng trước.
3. Nếu vẫn hòa nhau, mã công việc tăng dần để kết quả có quy tắc rõ ràng.

`Comparator.thenComparing(...)` kết hợp các tiêu chí theo đúng thứ bậc đó. Tiêu chí sau chỉ giải quyết những cặp phần tử mà toàn bộ các tiêu chí trước chưa phân biệt được.

Bài sử dụng `Ticket` làm ví dụ. Số `priority` lớn hơn có nghĩa là ưu tiên cao hơn; `due == null` có nghĩa chưa có hạn xử lý. Đây là các quy ước nghiệp vụ được chọn cho ví dụ, không phải ý nghĩa Java tự áp đặt lên tên trường.

## 1. Vì sao cần tiêu chí phụ?

Dữ liệu mẫu:

| Mã | Độ ưu tiên | Hạn xử lý |
| --- | --- | --- |
| T4 | 2 | 2026-05-10 |
| T2 | 3 | 2026-05-08 |
| T6 | 3 | Chưa có |
| T1 | 3 | 2026-05-08 |
| T3 | 2 | 2026-05-06 |
| T5 | 3 | 2026-05-12 |

Chỉ sắp theo ưu tiên giảm dần có thể đưa toàn bộ nhóm ưu tiên `3` lên trước nhóm ưu tiên `2`. Nhưng nó chưa nói T1 và T5 trong cùng nhóm phải đứng như thế nào.

Thêm hạn xử lý sẽ sắp xếp từng nhóm ưu tiên theo hạn. Thêm mã sẽ tiếp tục phân biệt T1 và T2, vì hai công việc này có cùng ưu tiên và cùng hạn.

Kết quả theo đủ ba tiêu chí là:

~~~text
T1, T2, T5, T6, T3, T4
~~~

T3 có hạn sớm hơn T1 nhưng vẫn nằm sau nhóm ưu tiên `3`, vì **ưu tiên là tiêu chí chính**. Hạn xử lý không được phép đảo một kết quả đã được quyết định bởi độ ưu tiên.

Đây là khác biệt giữa thứ bậc tiêu chí và một cách chấm điểm tổng hợp. `thenComparing` không tính trung bình, không cân trọng số và không cho tiêu chí phụ “bù” cho tiêu chí chính.

## 2. Cơ chế: dùng kết quả khác 0 đầu tiên

Giả sử:

~~~java
Comparator<Ticket> order =
        primary.thenComparing(secondary).thenComparing(tertiary);
~~~

Khi so sánh hai công việc `left` và `right`, có thể hình dung:

~~~java
// Mã minh họa cơ chế; các comparator thành phần đã được khai báo.
int result = primary.compare(left, right);
if (result != 0) {
    return result;
}

result = secondary.compare(left, right);
if (result != 0) {
    return result;
}

return tertiary.compare(left, right);
~~~

Đoạn mã có `return` này là phần thân minh họa của một phép so sánh, không phải chương trình độc lập cần sao chép vào `main`.

Kết quả âm nghĩa là `left` đứng trước, dương nghĩa là `left` đứng sau, bằng `0` nghĩa là comparator chưa phân biệt được hai phần tử.

“Bằng nhau” ở bước quyết định có dùng tiêu chí tiếp theo hay không là **`compare(left, right) == 0`**, không phải `left.equals(right)`.

Ví dụ, T1 và T2 là hai công việc khác nhau nhưng comparator chỉ xét ưu tiên sẽ trả `0`. Vì vậy comparator hạn được xét tiếp, rồi comparator mã được xét khi hạn cũng bằng nhau.

Java gọi cách kết hợp này là thứ tự từ điển theo các comparator. Mô tả API và các overload có trong [Java SE 17 Comparator API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

### Không cộng các kết quả so sánh

Cách kết hợp sau không biểu đạt đúng thứ bậc:

~~~java
// Ví dụ sai về ý tưởng, không nên dùng.
int result = primary.compare(left, right)
        + secondary.compare(left, right);
~~~

Một kết quả dương của tiêu chí này có thể bị kết quả âm của tiêu chí khác triệt tiêu. Hơn nữa, comparator chỉ quy định ý nghĩa của dấu; độ lớn kết quả không phải trọng số nghiệp vụ.

Cũng không viết phép trừ giữa hai khóa số rồi coi đó luôn là comparator đúng. Tràn số có thể làm đổi dấu. Các factory như `comparingInt` hoặc hàm `Integer.compare` phù hợp hơn cho khóa `int`.

## 3. Tạo comparator nhiều tiêu chí

Với kiểu dữ liệu:

~~~java
// Khai báo kiểu minh họa; cần import java.time.LocalDate.
record Ticket(String id, int priority, LocalDate due) {}
~~~

Ta tạo comparator cho ngày có thể thiếu:

~~~java
Comparator<LocalDate> dateOrder =
        Comparator.nullsLast(Comparator.naturalOrder());
~~~

Sau đó ghép quy tắc:

~~~java
Comparator<Ticket> order =
        Comparator.comparingInt(Ticket::priority)
                .reversed()
                .thenComparing(Ticket::due, dateOrder)
                .thenComparing(Ticket::id);
~~~

Đọc theo thứ tự:

- `comparingInt(Ticket::priority).reversed()`: ưu tiên giảm dần.
- `thenComparing(Ticket::due, dateOrder)`: trong cùng mức ưu tiên, ngày tăng dần; ngày thiếu đứng cuối trong nhóm.
- `thenComparing(Ticket::id)`: nếu hai tiêu chí trên đều hòa, mã theo thứ tự tự nhiên của `String`.

Mã tăng dần trong bài được hiểu theo so sánh chuỗi. Với những mã như `T2` và `T10`, thứ tự chuỗi không đồng nghĩa thứ tự của phần số bên trong. Nếu nghiệp vụ cần thứ tự số của mã, phải thiết kế khóa so sánh tương ứng.

### Một comparator là dữ liệu mô tả quy tắc

Tạo `order` chưa sắp xếp dữ liệu. Cần truyền nó vào thao tác sử dụng:

~~~java
// tickets là nguồn đã có; ArrayList tạo một danh sách có thể sửa.
List<Ticket> sorted = new ArrayList<>(tickets);
sorted.sort(order);
~~~

`sort` thay thứ tự trong danh sách nhận lời gọi. Comparator không tự quyết định phải sửa nguồn hay tạo bản sao; đó là lựa chọn của đoạn mã sử dụng nó.

Nếu gọi `sort` trực tiếp trên danh sách không hỗ trợ sửa đổi như kết quả `List.of(...)`, thao tác có thể thất bại bằng `UnsupportedOperationException`. Dùng một bản sao có thể sửa khi mục đích là giữ nguyên nguồn hoặc nguồn không cho đổi thứ tự.

## 4. Ba dạng thenComparing cần phân biệt

| Dạng | Đầu vào | Khi nào dùng |
| --- | --- | --- |
| `thenComparing(other)` | Comparator cho cùng loại phần tử | Đã có một quy tắc so sánh hoàn chỉnh muốn tái sử dụng làm tiêu chí tiếp theo. |
| `thenComparing(keyExtractor)` | Hàm lấy khóa có thứ tự tự nhiên | Khóa như `String` hoặc `LocalDate` không cần quy tắc riêng. |
| `thenComparing(keyExtractor, keyComparator)` | Hàm lấy khóa và comparator cho khóa | Muốn kiểm soát `null`, chiều so sánh hoặc quy tắc riêng của khóa. |

### Ghép comparator đã có

~~~java
Comparator<Ticket> byPriority =
        Comparator.comparingInt(Ticket::priority).reversed();

Comparator<Ticket> byDue =
        Comparator.comparing(Ticket::due, dateOrder);

Comparator<Ticket> order =
        byPriority.thenComparing(byDue).thenComparing(Ticket::id);
~~~

`byDue` so sánh hai `Ticket`. Vì vậy nó phù hợp với overload nhận một comparator cho phần tử.

### Ghép hàm lấy khóa

~~~java
Comparator<Ticket> order =
        byPriority.thenComparing(Ticket::id);
~~~

`Ticket::id` không phải comparator: nó nhận một công việc và trả một khóa `String`. Overload này lấy hai khóa rồi dùng thứ tự tự nhiên của chúng.

### Ghép khóa cùng quy tắc riêng

~~~java
Comparator<Ticket> order =
        byPriority.thenComparing(
                Ticket::id,
                String.CASE_INSENSITIVE_ORDER
        );
~~~

Trong ví dụ này, tham số thứ hai là comparator của `String`, không phải comparator của `Ticket`.

Đừng nhầm hai tầng:

~~~text
Comparator<Ticket>
    so sánh Ticket với Ticket

Comparator<String>
    so sánh hai khóa id đã lấy từ Ticket
~~~

Nếu kiểu khóa không triển khai `Comparable`, overload nhận riêng hàm lấy khóa không đủ để chọn thứ tự tự nhiên. Khi đó phải truyền một comparator cho khóa hoặc ánh xạ đến khóa có quy tắc rõ ràng.

## 5. Các biến thể cho khóa nguyên thủy

Với khóa số, có các phương thức:

- `thenComparingInt(...)`: hàm lấy khóa trả `int`.
- `thenComparingLong(...)`: hàm lấy khóa trả `long`.
- `thenComparingDouble(...)`: hàm lấy khóa trả `double`.

Ví dụ bổ sung cho một kiểu dữ liệu khác:

~~~java
// Minh họa các khai báo trong một lớp; cần import java.util.Comparator.
record Shipment(
        String destination,
        int priority,
        long weightInGrams,
        double distance
) {}

Comparator<Shipment> order =
        Comparator.comparing(Shipment::destination)
                .thenComparingInt(Shipment::priority)
                .thenComparingLong(Shipment::weightInGrams)
                .thenComparingDouble(Shipment::distance);
~~~

Thứ bậc là nơi đến, rồi ưu tiên, rồi khối lượng, rồi khoảng cách. Tất cả ví dụ trên dùng chiều tăng theo mỗi khóa.

Các biến thể nguyên thủy làm rõ kiểu khóa và tránh yêu cầu boxing khóa thành `Integer`, `Long` hoặc `Double` trên đường so sánh tương ứng. Không ép giá trị `long` về `int` chỉ để dùng `thenComparingInt`: có thể mất thông tin và đổi kết quả.

Chọn kiểu dữ liệu và định nghĩa thứ tự trước. Nếu `distance` cho phép `NaN`, vô cực hoặc giá trị không hợp lệ với nghiệp vụ, phải xác định chính sách dữ liệu đó; việc API chấp nhận `double` không tự giải quyết ý nghĩa nghiệp vụ.

## 6. Điều khiển tăng và giảm cho từng tiêu chí

### Chỉ đảo tiêu chí chính

~~~java
Comparator<Ticket> order =
        Comparator.comparingInt(Ticket::priority)
                .reversed()
                .thenComparing(Ticket::due, dateOrder)
                .thenComparing(Ticket::id);
~~~

`reversed()` xuất hiện ngay sau comparator ưu tiên, trước khi ghép ngày và mã. Vì vậy nó chỉ đảo chiều ưu tiên.

### Đảo toàn bộ chuỗi

~~~java
Comparator<Ticket> different =
        Comparator.comparingInt(Ticket::priority)
                .thenComparing(Ticket::due, dateOrder)
                .thenComparing(Ticket::id)
                .reversed();
~~~

Ở thời điểm gọi `reversed()`, chuỗi đã gồm cả ba tiêu chí. Kết quả đảo cả ưu tiên, hạn và mã. Quan hệ “ngày thiếu đứng cuối” cũng bị đảo thành “ngày thiếu đứng đầu” trong nhóm tương ứng.

Không thể di chuyển `reversed()` từ giữa xuống cuối mà mặc nhiên giữ cùng ý nghĩa.

### Giảm dần một tiêu chí phụ

Giả sử cùng ưu tiên thì ngày muộn hơn đứng trước, nhưng ngày thiếu vẫn đứng cuối:

~~~java
Comparator<LocalDate> dueDescendingNullLast =
        Comparator.nullsLast(Comparator.reverseOrder());

Comparator<Ticket> order =
        Comparator.comparingInt(Ticket::priority)
                .reversed()
                .thenComparing(Ticket::due, dueDescendingNullLast)
                .thenComparing(Ticket::id);
~~~

Một cách khác khi muốn đảo một comparator phần tử đã có là truyền nó vào overload nhận comparator:

~~~java
// Ví dụ chỉ về phạm vi đảo: đảo toàn bộ quy tắc byDue sẵn có.
Comparator<Ticket> byPriorityThenReversedDue =
        byPriority.thenComparing(byDue.reversed());
~~~

Nếu `byDue` đã bọc `nullsLast` cho ngày, `byDue.reversed()` sẽ đảo cả vị trí `null`. Muốn `null` luôn ở cuối, nên tạo quy tắc khóa như `dueDescendingNullLast` ở ví dụ trước.

## 7. Null: chính sách ở từng tiêu chí và ở toàn phần tử

Nếu chỉ có ngày thiếu, xử lý ở comparator khóa ngày:

~~~java
Comparator<LocalDate> dateOrder =
        Comparator.nullsLast(Comparator.naturalOrder());

Comparator<Ticket> order =
        Comparator.comparingInt(Ticket::priority)
                .reversed()
                .thenComparing(Ticket::due, dateOrder)
                .thenComparing(Ticket::id);
~~~

Nếu danh sách còn có cả phần tử `Ticket == null`, bọc comparator hoàn chỉnh ở ngoài:

~~~java
Comparator<Ticket> nullTicketsLast =
        Comparator.nullsLast(order);
~~~

Hai lớp này giải quyết hai tình huống:

- Comparator ngoài kiểm tra chính phần tử `Ticket`.
- Comparator ngày xử lý giá trị `due` của một `Ticket` tồn tại.

Nếu `id` cũng có thể `null`, phải bổ sung quy tắc ở tiêu chí mã. Xử lý `null` cho `due` không tự xử lý `null` cho tất cả khóa khác.

### Không có ngoại lệ ở một bộ dữ liệu chưa chứng minh comparator an toàn

Vì tiêu chí phụ chỉ được dùng khi các tiêu chí trước hòa nhau, một khóa `null` có thể chưa được đọc trong một lần so sánh nào đó.

Ví dụ, hai công việc khác ưu tiên có thể được phân biệt ngay; comparator ngày chưa cần chạy. Nhưng chỉ cần thêm dữ liệu cùng ưu tiên, việc so ngày có thể phát sinh và làm lộ lỗi `null` chưa được xử lý.

Đừng dựa vào thứ tự so sánh tình cờ của thuật toán sort để che một dữ liệu không hợp lệ. Xác định rõ khóa nào được phép thiếu, ở mức nào và phải đứng đâu.

## 8. thenComparing trả comparator mới

Lời gọi không cập nhật biến comparator đã có:

~~~java
Comparator<Ticket> order =
        Comparator.comparingInt(Ticket::priority).reversed();

order.thenComparing(Ticket::id); // Bỏ qua comparator mới được trả về.
~~~

Sau đoạn mã này, biến `order` vẫn chỉ chứa quy tắc ưu tiên. Muốn dùng quy tắc đã ghép:

~~~java
order = order.thenComparing(Ticket::id);
~~~

Hoặc tạo thành một biểu thức hoàn chỉnh ngay từ đầu:

~~~java
Comparator<Ticket> order =
        Comparator.comparingInt(Ticket::priority)
                .reversed()
                .thenComparing(Ticket::id);
~~~

Điều này thuận tiện cho việc tái sử dụng các phần:

~~~java
Comparator<Ticket> base =
        Comparator.comparingInt(Ticket::priority).reversed();

Comparator<Ticket> forDashboard =
        base.thenComparing(Ticket::due, dateOrder)
                .thenComparing(Ticket::id);

Comparator<Ticket> forCodeLookup =
        base.thenComparing(Ticket::id);
~~~

Việc tạo một chuỗi mới không làm `base` tự xuất hiện các tiêu chí phụ. Tuy nhiên, các comparator vẫn có thể cùng tham chiếu các đối tượng bên ngoài do lambda bắt giữ; không nên hiểu điều này là mọi dữ liệu mà chúng đọc đều bất biến.

## 9. Chaining và sắp xếp nhiều lượt

Cách tự nhiên nhất để biểu đạt yêu cầu ưu tiên → hạn → mã là tạo một comparator rồi gọi sort một lần:

~~~java
sorted.sort(order);
~~~

Một nhầm lẫn phổ biến:

~~~java
sorted.sort(byPriority);
sorted.sort(byDueThenId);
~~~

Lần sort thứ hai sắp lại **toàn bộ danh sách** theo hạn và mã. Nó không tự hiểu “chỉ sắp các nhóm bằng nhau của lần đầu”.

Với dữ liệu bài này, T3 có ưu tiên `2` nhưng hạn sớm nhất, nên sẽ bị đưa lên đầu nếu sort toàn bộ theo hạn ở bước cuối. Kết quả trái với yêu cầu ưu tiên là tiêu chí chính.

### Sắp nhiều lượt có thể làm được khi hiểu tính ổn định

`List.sort` là sắp xếp ổn định: các phần tử được comparator xem là bằng nhau giữ thứ tự tương đối trước lần sort đó.

Nếu thật sự cần sắp nhiều lượt, có thể sort tiêu chí phụ trước rồi sort ổn định theo tiêu chí chính:

~~~java
sorted.sort(byDueThenId);
sorted.sort(byPriority);
~~~

Lần sort cuối tạo các nhóm ưu tiên. Trong từng nhóm, nó giữ thứ tự hạn và mã đã được tạo ở lần trước.

Đây là một kỹ thuật có điều kiện, không phải quy tắc dùng được với mọi thuật toán sort:

- Các lượt phải áp dụng từ tiêu chí ít quan trọng hơn đến tiêu chí quan trọng hơn.
- Phép sort phù hợp phải giữ ổn định để bảo toàn kết quả phụ trong nhóm hòa nhau.
- Dữ liệu và quy tắc so sánh phải không thay đổi giữa chừng.

Một chuỗi `thenComparing` thường dễ đọc và ít nhầm thứ bậc hơn, đồng thời không yêu cầu người đọc suy luận ngược qua nhiều lần thay đổi danh sách.

## 10. Sắp xếp ổn định khác với thứ tự được xác định đầy đủ

Chỉ sắp theo ưu tiên và hạn:

~~~java
Comparator<Ticket> priorityThenDue =
        byPriority.thenComparing(Ticket::due, dateOrder);
~~~

T1 và T2 vẫn hòa nhau. Nếu đầu vào đặt T2 trước T1, một lần `List.sort` ổn định giữ T2 trước T1. Nếu nguồn đầu vào đảo hai bản ghi, kết quả trong nhóm hòa có thể đảo theo.

Tính ổn định không tự tạo một tiêu chí còn thiếu. Nếu muốn kết quả được xác định theo dữ liệu thay vì phụ thuộc thứ tự đầu vào, cần chọn một quy tắc giải quyết hòa phù hợp, như mã duy nhất trong ví dụ:

~~~java
Comparator<Ticket> fullOrder =
        priorityThenDue.thenComparing(Ticket::id);
~~~

Điều này có giá trị với:

- Kiểm thử cần kết quả có thể tái lập.
- Xuất báo cáo cùng một tập dữ liệu.
- Phân trang cần thứ tự rõ ràng giữa các bản ghi ngang nhau theo tiêu chí chính.

Tuy nhiên, thêm mã cuối chuỗi không tự giải quyết mọi vấn đề phân trang. Dữ liệu có thể thay đổi giữa các yêu cầu, và mã có thể chỉ duy nhất trong một phạm vi. Hệ thống cần xác định cả phạm vi duy nhất, tính nhất quán dữ liệu và cách tạo con trỏ hoặc snapshot.

Đừng thêm `identityHashCode`, số ngẫu nhiên hoặc thời gian hiện tại chỉ để tránh `compare == 0`. Các giá trị đó không biểu đạt một thứ tự nghiệp vụ bền vững.

## 11. Dữ liệu bằng nhau theo comparator và TreeSet

Nếu comparator chỉ xét ưu tiên, T1 và T2 có thể cho kết quả `0` dù là hai công việc khác nhau.

- Với danh sách, sort không tự xóa phần tử vì hòa nhau.
- Với `TreeSet`, quan hệ so sánh còn quyết định việc xem hai phần tử thuộc cùng một vị trí trong tập hợp.
- Với `TreeMap`, comparator tham gia quyết định hai khóa có tương đương hay không.

Vì vậy, comparator thiết kế cho màn hình “nhóm theo độ ưu tiên” chưa chắc phù hợp làm comparator cho tập hợp chứa tất cả công việc.

Thêm mã có thể giúp phân biệt các công việc nếu mã thực sự là định danh duy nhất của chúng. Nhưng yêu cầu “nhất quán với `equals`” cần được kiểm tra theo định nghĩa `equals` thực tế.

Trong record `Ticket(id, priority, due)` của bài, `equals` xét cả ba thành phần. Chuỗi comparator đầy đủ cũng xét ba thành phần theo các quy tắc tương ứng. Nếu thay định nghĩa dữ liệu, cho phép mã trùng hoặc thêm quy tắc bỏ qua chữ hoa–thường, phải xem lại quan hệ tương đương thay vì chỉ giữ chuỗi comparator cũ.

## 12. Chi phí và tính nhất quán của các tiêu chí

Tiêu chí sau chỉ được gọi khi chuỗi trước trả `0`. Đây là một đặc điểm có thể giảm số lần tính khóa phụ.

Nhưng không nên đổi thứ tự các tiêu chí chỉ để giảm chi phí nếu việc đó làm sai yêu cầu nghiệp vụ. Chuyển mã duy nhất lên đầu sẽ khiến các tiêu chí ưu tiên và hạn gần như không còn được dùng để quyết định thứ tự giữa các bản ghi khác mã.

Hãy tối ưu trong phạm vi giữ đúng ý nghĩa:

- Dùng biến thể `thenComparingInt/Long/Double` cho khóa nguyên thủy phù hợp.
- Tránh gọi mạng hoặc truy vấn cơ sở dữ liệu trong hàm lấy khóa.
- Cân nhắc tính trước các khóa tốn kém cho một lượt xử lý.
- Không để khóa thay đổi trong khi đang sort.
- Không dựa vào số lần gọi comparator cố định của một thuật toán.

Một comparator cần cho kết quả nhất quán trong thời gian sử dụng: đổi chỗ hai đối tượng phải đảo dấu; quan hệ thứ tự phải có tính bắc cầu; các phần tử so sánh bằng nhau phải có cùng quan hệ về dấu với một phần tử thứ ba.

Ghép các comparator hợp lệ là cách biểu đạt quy tắc rõ ràng. Nó không sửa được một comparator thành phần vốn sai hợp đồng hoặc đọc dữ liệu thay đổi ngẫu nhiên.

## 13. Chương trình hoàn chỉnh

Lưu thành **MultiCriteriaSortDemo.java**. Chương trình dùng API có trong Java 17; `Ticket` là record để dữ liệu ví dụ không bị sửa khóa giữa lúc so sánh.

~~~java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MultiCriteriaSortDemo {
    record Ticket(String id, int priority, LocalDate due) {}

    public static void main(String[] args) {
        List<Ticket> tickets = List.of(
                new Ticket("T4", 2, LocalDate.of(2026, 5, 10)),
                new Ticket("T2", 3, LocalDate.of(2026, 5, 8)),
                new Ticket("T6", 3, null),
                new Ticket("T1", 3, LocalDate.of(2026, 5, 8)),
                new Ticket("T3", 2, LocalDate.of(2026, 5, 6)),
                new Ticket("T5", 3, LocalDate.of(2026, 5, 12))
        );

        Comparator<LocalDate> dateOrder =
                Comparator.nullsLast(Comparator.naturalOrder());

        Comparator<Ticket> byPriorityDescending =
                Comparator.comparingInt(Ticket::priority).reversed();

        Comparator<Ticket> byDueThenId =
                Comparator.comparing(Ticket::due, dateOrder)
                        .thenComparing(Ticket::id);

        Comparator<Ticket> priorityThenDue =
                byPriorityDescending.thenComparing(Ticket::due, dateOrder);

        Comparator<Ticket> fullOrder =
                priorityThenDue.thenComparing(Ticket::id);

        Comparator<Ticket> wholeOrderReversed =
                Comparator.comparingInt(Ticket::priority)
                        .thenComparing(Ticket::due, dateOrder)
                        .thenComparing(Ticket::id)
                        .reversed();

        printSorted("Priority only", tickets, byPriorityDescending);
        printSorted("Priority then due", tickets, priorityThenDue);
        printSorted("Full order", tickets, fullOrder);
        printSorted("Whole chain reversed", tickets, wholeOrderReversed);

        List<Ticket> wrongPassOrder = new ArrayList<>(tickets);
        wrongPassOrder.sort(byPriorityDescending);
        wrongPassOrder.sort(byDueThenId);
        System.out.println("Primary sort then secondary sort = "
                + ids(wrongPassOrder));

        List<Ticket> correctPassOrder = new ArrayList<>(tickets);
        correctPassOrder.sort(byDueThenId);
        correctPassOrder.sort(byPriorityDescending);
        System.out.println("Secondary sort then primary stable sort = "
                + ids(correctPassOrder));

        Ticket t1 = tickets.get(3);
        Ticket t2 = tickets.get(1);
        Ticket t4 = tickets.get(0);

        System.out.println("Primary compare T1,T2 = "
                + byPriorityDescending.compare(t1, t2));
        System.out.println("Full compare T1,T2 sign = "
                + Integer.signum(fullOrder.compare(t1, t2)));

        int[] secondaryReads = {0};
        Comparator<Ticket> probe =
                byPriorityDescending.thenComparing(ticket -> {
                    secondaryReads[0]++;
                    return ticket.id();
                });

        probe.compare(t2, t4);
        System.out.println("Secondary reads when primary differs = "
                + secondaryReads[0]);

        secondaryReads[0] = 0;
        probe.compare(t2, t1);
        System.out.println("Secondary reads when primary ties = "
                + secondaryReads[0]);

        System.out.println("Original = " + ids(tickets));
    }

    private static void printSorted(
            String label,
            List<Ticket> source,
            Comparator<? super Ticket> order
    ) {
        List<Ticket> copy = new ArrayList<>(source);
        copy.sort(order);
        System.out.println(label + " = " + ids(copy));
    }

    private static List<String> ids(List<Ticket> tickets) {
        List<String> result = new ArrayList<>();
        for (Ticket ticket : tickets) {
            result.add(ticket.id());
        }
        return result;
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 MultiCriteriaSortDemo.java
java MultiCriteriaSortDemo
~~~

Stdout:

~~~text
Priority only = [T2, T6, T1, T5, T4, T3]
Priority then due = [T2, T1, T5, T6, T3, T4]
Full order = [T1, T2, T5, T6, T3, T4]
Whole chain reversed = [T6, T5, T2, T1, T4, T3]
Primary sort then secondary sort = [T3, T1, T2, T4, T5, T6]
Secondary sort then primary stable sort = [T1, T2, T5, T6, T3, T4]
Primary compare T1,T2 = 0
Full compare T1,T2 sign = -1
Secondary reads when primary differs = 0
Secondary reads when primary ties = 2
Original = [T4, T2, T6, T1, T3, T5]
~~~

### Đọc từng nhóm kết quả

**Priority only**

Nhóm ưu tiên `3` xuất hiện trước nhóm ưu tiên `2`. Trong từng nhóm, chưa có quy tắc hạn hay mã nên `List.sort` giữ thứ tự tương đối ban đầu: T2, T6, T1, T5 trong nhóm `3`.

**Priority then due**

T2 và T1 cùng hạn `2026-05-08`, nên vẫn giữ T2 trước T1. T5 đến sau vì có hạn muộn hơn; T6 không có hạn nên đứng cuối nhóm ưu tiên `3`.

**Full order**

Mã được xét để giải quyết cặp T1 và T2. T1 đứng trước T2 vì so sánh mã tăng dần. Các quan hệ đã được quyết định bằng ưu tiên hoặc hạn được giữ nguyên.

**Whole chain reversed**

Chuỗi tăng dần theo ưu tiên, hạn, mã bị đảo ở cuối. Nhóm ưu tiên `3` lên trước, nhưng ngày trong nhóm đi theo chiều ngược; T6 có ngày `null` còn bị đưa lên đầu. Kết quả không phải quy tắc đầy đủ mong muốn.

**Hai thứ tự gọi sort**

Sort chính rồi sort phụ đưa T3 lên đầu vì lần cuối ưu tiên hạn. Sort phụ rồi sort ổn định theo chính cho cùng dãy mã như chuỗi comparator đầy đủ.

**So sánh T1 và T2**

Comparator ưu tiên trả `0`. Comparator đầy đủ trả dấu âm vì cuối cùng mã T1 đứng trước mã T2. Chương trình in dấu bằng `Integer.signum`, không giả định mọi comparator chỉ trả `-1, 0, 1`.

**Đếm số lần lấy khóa phụ**

Khi hai công việc khác ưu tiên, khóa mã phụ không được đọc: số đếm là `0`. Khi cùng ưu tiên, hàm lấy mã đọc khóa cho hai đối tượng: số đếm là `2`.

Bộ đếm này chỉ là dụng cụ quan sát cho hai lời gọi `compare` riêng lẻ trong ví dụ. Không viết logic nghiệp vụ phụ thuộc số lần comparator được thuật toán sort gọi, và không thêm tác dụng phụ như cập nhật dữ liệu trong hàm lấy khóa.

**Dữ liệu nguồn**

Các thao tác sort trong chương trình đều dùng bản sao. Vì vậy dòng `Original` giữ dãy mã ban đầu. Tạo comparator mới và sắp xếp bản sao là hai cơ chế riêng, cùng giúp ví dụ quan sát rõ mà không thay dữ liệu nguồn.

## 14. Những lỗi dễ gặp

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Xét tiêu chí phụ khi `equals` trả true | Nhầm bằng nhau của đối tượng với hòa theo tiêu chí | Dùng kết quả `compare == 0` trong cơ chế kết hợp. |
| Cộng các kết quả comparator | Mất thứ bậc và có thể triệt tiêu dấu | Dùng kết quả khác `0` đầu tiên hoặc `thenComparing`. |
| Đổi vị trí hai tiêu chí để “tối ưu” | Đổi yêu cầu nghiệp vụ | Giữ thứ bậc rồi tối ưu cách tính khóa. |
| Bỏ qua giá trị trả về của `thenComparing` | Comparator cũ chưa có tiêu chí mới | Gán kết quả hoặc ghép trong một biểu thức. |
| Đặt `reversed` ở cuối khi chỉ muốn đảo tiêu chí đầu | Đảo cả hạn, mã và quan hệ với `null` | Đảo đúng comparator thành phần. |
| Sort chính rồi sort phụ và mong chỉ nhóm hòa thay đổi | Lần cuối sắp lại toàn bộ danh sách | Dùng một chuỗi comparator, hoặc hiểu rõ kỹ thuật nhiều lượt ổn định. |
| Tin stable sort sẽ tạo thứ tự duy nhất | Nhóm hòa vẫn phụ thuộc đầu vào | Thêm tiêu chí giải quyết hòa có ý nghĩa khi cần. |
| Không xử lý khóa `null` vì bộ dữ liệu nhỏ chưa lỗi | Lỗi chỉ xuất hiện khi tiêu chí đó thật sự được dùng | Kiểm thử từng mức hòa và chính sách dữ liệu thiếu. |
| Dùng comparator chỉ xét một phần dữ liệu cho TreeSet | Những phần tử ngang nhau có thể bị gộp | Kiểm tra định nghĩa duy nhất và quan hệ với `equals`. |
| Dùng thuộc tính đang thay đổi hoặc số ngẫu nhiên làm khóa | Quan hệ so sánh không nhất quán | Dùng khóa ổn định trong lượt xử lý. |

## 15. Tự kiểm tra và thực hành

1. Viết bằng lời thứ bậc của `byA.thenComparing(byB).thenComparing(byC)`. Khi nào `byC` được gọi?
2. Tạo hai công việc khác ưu tiên nhưng ngày theo chiều ngược lại. Chứng minh ưu tiên vẫn quyết định kết quả.
3. Tạo hai công việc cùng ưu tiên, khác ngày, rồi hai công việc cùng ưu tiên và cùng ngày nhưng khác mã. Kiểm thử riêng hai tầng hòa.
4. Sửa comparator thành: ưu tiên giảm dần, ngày giảm dần nhưng ngày thiếu cuối, mã tăng dần.
5. Bổ sung dữ liệu có phần tử `Ticket == null`. Đặt phần tử đó ở đầu mà vẫn giữ ngày thiếu ở cuối trong từng nhóm công việc hợp lệ.
6. Đảo thứ tự đầu vào của T1 và T2. So sánh kết quả khi có và khi chưa có tiêu chí mã.
7. Bỏ qua kết quả trả về của `thenComparing` trong một bản thử nghiệm. Viết ca kiểm thử để phát hiện tiêu chí phụ chưa được áp dụng.
8. Cho một comparator chỉ xét ưu tiên vào `TreeSet`. Quan sát số phần tử, giải thích vì sao kết quả khác một danh sách đã sort, rồi chọn lại cấu trúc hoặc comparator theo yêu cầu giữ dữ liệu.

Khi đánh giá một chuỗi comparator, hãy viết rõ từng tiêu chí, chiều tăng–giảm, chính sách giá trị thiếu và ý nghĩa hòa nhau. Sau đó kiểm thử các cặp chỉ khác ở đúng một tầng để xác nhận chuỗi biểu đạt đúng thứ bậc mong muốn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-005">reversed, reverseOrder, nullsFirst và nullsLast</a>

<details>
<summary>Xem chi tiết</summary>

# reversed, reverseOrder, nullsFirst và nullsLast

Khi sắp xếp dữ liệu, cần quyết định riêng hai điều:

1. **Các giá trị có thật được xếp theo quy tắc nào, theo chiều nào?**
2. **Nếu có null, null đứng trước hay sau các giá trị đó?**

Các phương thức `reversed`, `reverseOrder`, `nullsFirst` và `nullsLast` giúp xây dựng comparator cho những quyết định này. Chúng tạo quy tắc so sánh; việc sắp xếp chỉ diễn ra khi quy tắc được truyền vào một thao tác như `List.sort`.

Trong bài này, `reversed()` chỉ phương thức của **Comparator**. Các ví dụ dùng API tương thích Java 17 trở lên.

## 1. Nhận diện từng công cụ

| Biểu thức | Loại phương thức | Chức năng |
| --- | --- | --- |
| `c.reversed()` | Phương thức mặc định trên một comparator có sẵn | Đảo toàn bộ thứ tự do `c` định nghĩa |
| `Comparator.reverseOrder()` | Phương thức static | Tạo comparator theo chiều ngược của thứ tự tự nhiên |
| `Comparator.nullsFirst(c)` | Phương thức static | Đặt null trước giá trị khác, dùng `c` cho hai giá trị không null |
| `Comparator.nullsLast(c)` | Phương thức static | Đặt null sau giá trị khác, dùng `c` cho hai giá trị không null |

Ở đây, `c` là một đối tượng `Comparator`, không phải một danh sách.

Cần phân biệt thêm hai phương thức thuộc lớp `Collections`:

- `Collections.reverseOrder(c)`: tạo comparator đảo quy tắc của `c`.
- `Collections.reverse(list)`: đảo **vị trí các phần tử đang có** trong một danh sách.

Hai thao tác này không thể thay thế cho nhau một cách tùy ý.

## 2. reversed(): đảo một quy tắc đã có

### 2.1. Đảo theo quy tắc nào?

Giả sử comparator ban đầu xếp chuỗi theo độ dài tăng dần:

```java
Comparator<String> byLength =
        Comparator.comparingInt(String::length);

Comparator<String> lengthDescending = byLength.reversed();
```

Đây là các khai báo minh họa, cần import `java.util.Comparator`.

Comparator mới đưa chuỗi dài hơn lên trước. Nó không tự đổi quy tắc thành thứ tự từ điển giảm dần, bởi quy tắc gốc đang xét **độ dài**.

Nếu comparator gốc xếp theo nhiều tiêu chí, đảo comparator là đảo toàn bộ thứ tự đó.

### 2.2. Hiểu bằng chiều so sánh, không bằng độ lớn kết quả

Về quan hệ thứ tự, comparator đảo tương đương việc dùng comparator gốc với hai đối số đổi chỗ:

```java
// Minh họa ý nghĩa:
Comparator<String> reverse = (a, b) -> byLength.compare(b, a);
```

Nếu `a` đứng trước `b` theo comparator gốc, nó đứng sau theo comparator đảo. Những đối tượng cùng hạng vẫn cùng hạng.

Không nên tự viết:

```java
// Không dùng làm mẫu đảo comparator:
// Comparator<String> reverse = (a, b) -> -byLength.compare(a, b);
```

Hợp đồng comparator cho phép trả về bất kỳ số nguyên âm hoặc dương phù hợp, không chỉ -1 hoặc 1. Nếu kết quả là `Integer.MIN_VALUE`, đổi dấu số đó vẫn âm do tràn miền `int`.

`reversed()` diễn đạt đúng ý định mà không đòi bên sử dụng tự xử lý trường hợp này.

### 2.3. Comparator gốc và danh sách không bị sửa

Lời gọi:

```java
Comparator<String> reverse = byLength.reversed();
```

không làm `byLength` đổi sang giảm dần, cũng không tự sắp xếp một danh sách nào. Sau đó vẫn có thể dùng hai comparator cho hai nơi khác nhau.

Đảo hai lần cho thứ tự tương đương quy tắc ban đầu. Điều đó là nhận xét về **kết quả so sánh**, không phải lời bảo đảm rằng hai biểu thức sẽ trả cùng một tham chiếu đối tượng.

Xem [API Comparator.reversed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#reversed()).

## 3. reverseOrder(): đảo thứ tự tự nhiên

### 3.1. Thứ tự tự nhiên là gì?

Thứ tự tự nhiên đến từ hợp đồng `Comparable` của kiểu dữ liệu. Chẳng hạn, `Integer` có thứ tự số và `String` có thứ tự so sánh chuỗi do kiểu đó định nghĩa.

```java
Comparator<Integer> descendingNumbers = Comparator.reverseOrder();
Comparator<String> descendingStrings = Comparator.reverseOrder();
```

Hai comparator này không cần một comparator gốc do người dùng truyền vào.

Không nên hiểu `reverseOrder()` là “đảo thứ tự mà danh sách hiện đang có”. Nó mô tả một thứ tự có thể áp dụng cho nhiều danh sách.

### 3.2. Khác với đảo comparator tùy chỉnh

Với các chuỗi:

```text
z, aa, bbb
```

hai yêu cầu cho kết quả khác nhau:

| Yêu cầu | Kết quả |
| --- | --- |
| Giảm dần theo thứ tự tự nhiên của String | `[z, bbb, aa]` |
| Giảm dần theo độ dài | `[bbb, aa, z]` |

Dùng `Comparator.reverseOrder()` cho hàng đầu. Dùng `Comparator.comparingInt(String::length).reversed()` cho hàng sau.

Nếu một kiểu miền nghiệp vụ không triển khai `Comparable`, không thể chỉ dùng `Comparator.reverseOrder()` rồi kỳ vọng Java tự biết cần đảo theo thuộc tính nào. Hãy tạo comparator theo thuộc tính mong muốn trước.

### 3.3. reverseOrder không tự hỗ trợ null

`Comparator.reverseOrder()` không có chính sách xếp null. Nếu việc so sánh chạm vào đối số null, nó ném `NullPointerException`.

Muốn các giá trị không null giảm dần nhưng null đứng cuối:

```java
Comparator<String> rule =
        Comparator.nullsLast(Comparator.reverseOrder());
```

Quy tắc trên có hai lớp: lớp ngoài xử lý null, lớp trong xử lý các chuỗi thực.

Xem [API Comparator.reverseOrder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#reverseOrder()).

## 4. nullsFirst và nullsLast hoạt động ra sao?

Giả sử `c` là comparator hợp lệ cho hai giá trị không null:

| Hai đối số | `nullsFirst(c)` | `nullsLast(c)` |
| --- | --- | --- |
| Cả hai null | Cùng hạng | Cùng hạng |
| Đối số đầu null, đối số sau không null | Đầu đứng trước | Đầu đứng sau |
| Đối số đầu không null, đối số sau null | Đầu đứng sau | Đầu đứng trước |
| Cả hai không null | Dùng `c` | Dùng `c` |

Lớp bao ngoài xử lý những trường hợp có null trước khi cần đến comparator bên trong. Nó không đổi giá trị null thành chuỗi rỗng, số 0 hoặc một đối tượng giả.

Hai khai báo thường gặp:

```java
Comparator<String> nullFirstAscending =
        Comparator.nullsFirst(Comparator.naturalOrder());

Comparator<String> nullLastAscending =
        Comparator.nullsLast(Comparator.naturalOrder());
```

`naturalOrder()` không tự chấp nhận null, nhưng có thể đặt bên trong lớp bao xử lý null như trên.

### 4.1. Đối số comparator null có ý nghĩa riêng

API cho phép:

```java
Comparator<String> onlyNullPlacement =
        Comparator.nullsFirst(null);
```

Trong trường hợp này:

- Null vẫn đứng trước các giá trị không null.
- **Mọi giá trị không null đều được xem là cùng hạng.**
- Không có bước tự động dùng thứ tự tự nhiên cho phần không null.

Tương tự, `Comparator.nullsLast(null)` chỉ tách null ra sau, còn các giá trị không null cùng hạng.

Đây không phải cách viết tắt của `nullsFirst(naturalOrder())`. Nếu cần phân biệt các giá trị không null, phải truyền comparator phù hợp.

Nếu dùng quy tắc chỉ phân biệt null vào `TreeSet`, mọi giá trị không null thuộc một hạng theo comparator; không thể mong cây giữ đủ mọi chuỗi khác nhau. Với danh sách, những phần tử cùng hạng vẫn được giữ lại.

Chi tiết của hai lớp bao nằm trong [API nullsFirst](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#nullsFirst(java.util.Comparator)) và [API nullsLast](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#nullsLast(java.util.Comparator)).

## 5. Vị trí reversed quyết định vị trí null

Đây là điểm cần đọc theo cấu trúc biểu thức.

### 5.1. Đảo toàn bộ lớp bao

```java
Comparator<String> original =
        Comparator.nullsLast(Comparator.naturalOrder());

Comparator<String> reverseEverything = original.reversed();
```

Quy tắc ban đầu xếp:

```text
giá trị nhỏ -> giá trị lớn -> null
```

Đảo toàn bộ cho:

```text
null -> giá trị lớn -> giá trị nhỏ
```

Null chuyển từ cuối lên đầu vì nó cũng là một phần của thứ tự đã đảo.

### 5.2. Chỉ đảo các giá trị không null

```java
Comparator<String> descendingButNullLast =
        Comparator.nullsLast(Comparator.reverseOrder());
```

Ở đây, null vẫn ở cuối. Comparator bên trong chỉ định chiều giảm dần cho các chuỗi không null.

Có thể đọc từ trong ra ngoài:

1. Xếp giá trị thực theo chiều giảm dần.
2. Bổ sung quy tắc đặt null cuối.

### 5.3. Bảng đối chiếu nhanh

Đặt `c` là thứ tự tăng dần cho các giá trị không null:

| Biểu thức | Chiều của giá trị thực | Vị trí null |
| --- | --- | --- |
| `nullsFirst(c)` | Tăng | Đầu |
| `nullsLast(c)` | Tăng | Cuối |
| `nullsFirst(c).reversed()` | Giảm | Cuối |
| `nullsLast(c).reversed()` | Giảm | Đầu |
| `nullsFirst(c.reversed())` | Giảm | Đầu |
| `nullsLast(c.reversed())` | Giảm | Cuối |

Bảng dùng cách viết rút gọn của các phương thức static thuộc `Comparator`.

Hai biểu thức cùng chiều của phần không null chưa chắc có cùng chính sách null. Vì vậy, cần kiểm tra cả hai trục khi đọc một comparator được ghép nhiều lớp.

## 6. Chương trình hoàn chỉnh: đảo chiều và xếp null

Lưu vào **ComparatorDirectionDemo.java**. Hàm `sorted` luôn tạo bản `ArrayList` mới để mỗi lần thử dùng cùng một đầu vào.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ComparatorDirectionDemo {
    private static List<String> sorted(
            List<String> source, Comparator<String> comparator) {
        List<String> result = new ArrayList<>(source);
        result.sort(comparator);
        return result;
    }

    public static void main(String[] args) {
        List<String> words = List.of("z", "aa", "bbb");
        Comparator<String> byLength =
                Comparator.comparingInt(String::length)
                        .thenComparing(Comparator.naturalOrder());

        System.out.println("Natural descending: "
                + sorted(words, Comparator.reverseOrder()));
        System.out.println("Length descending: "
                + sorted(words, byLength.reversed()));
        System.out.println("Collections reverse comparator: "
                + sorted(words, Collections.reverseOrder(byLength)));

        List<String> nullable =
                Arrays.asList("z", null, "aa", "bbb", null);
        Comparator<String> natural = Comparator.naturalOrder();
        Comparator<String> nullLastAscending =
                Comparator.nullsLast(natural);

        System.out.println("Null first ascending: "
                + sorted(nullable, Comparator.nullsFirst(natural)));
        System.out.println("Null last ascending: "
                + sorted(nullable, nullLastAscending));
        System.out.println("Reverse whole rule: "
                + sorted(nullable, nullLastAscending.reversed()));
        System.out.println("Null last descending: "
                + sorted(nullable, Comparator.nullsLast(
                        Comparator.reverseOrder())));

        Comparator<String> onlyNullPlacement =
                Comparator.nullsFirst(null);
        System.out.println("Only null placement: "
                + sorted(nullable, onlyNullPlacement));
        System.out.println("Non-null tie: "
                + onlyNullPlacement.compare("z", "aa"));
        System.out.println("Source unchanged: " + nullable);

        List<String> positional = new ArrayList<>(List.of("b", "a", "c"));
        Collections.reverse(positional);
        System.out.println("Reverse positions: " + positional);
        System.out.println("Sort descending: "
                + sorted(List.of("b", "a", "c"), Comparator.reverseOrder()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparatorDirectionDemo.java
java ComparatorDirectionDemo
```

Stdout:

```text
Natural descending: [z, bbb, aa]
Length descending: [bbb, aa, z]
Collections reverse comparator: [bbb, aa, z]
Null first ascending: [null, null, aa, bbb, z]
Null last ascending: [aa, bbb, z, null, null]
Reverse whole rule: [null, null, z, bbb, aa]
Null last descending: [z, bbb, aa, null, null]
Only null placement: [null, null, z, aa, bbb]
Non-null tie: 0
Source unchanged: [z, null, aa, bbb, null]
Reverse positions: [c, a, b]
Sort descending: [c, b, a]
```

### Đối chiếu kết quả

**Ba dòng đầu** phân biệt thứ tự tự nhiên với thứ tự theo độ dài. `Collections.reverseOrder(byLength)` cho kết quả cùng quy tắc đảo như `byLength.reversed()`.

**Bốn dòng về null** chứng minh vị trí của lời gọi `reversed()` có tác động:

- Đảo toàn bộ `nullLastAscending` đưa null lên đầu.
- Bọc thứ tự giảm dần bằng `nullsLast` giữ null ở cuối.

**`Only null placement`** giữ thứ tự tương đối `z, aa, bbb` của phần không null. Đây là hệ quả hai điều cùng xảy ra: comparator xem chúng cùng hạng và `List.sort` là phép sắp xếp ổn định. Nó không phải một phép sắp xếp tăng dần ẩn bên trong `nullsFirst(null)`. Bảo đảm ổn định được mô tả trong [API List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

**`Source unchanged`** xác nhận ví dụ sắp các bản sao; danh sách nguồn không bị đổi. Nếu trực tiếp gọi `source.sort(comparator)` trên danh sách có hỗ trợ, chính danh sách đó sẽ bị thay thứ tự.

**Hai dòng cuối** cho thấy đảo vị trí và sắp giảm dần là hai công việc khác nhau:

- Đảo `[b, a, c]` theo vị trí cho `[c, a, b]`.
- Sắp các chuỗi theo thứ tự tự nhiên giảm dần cho `[c, b, a]`.

## 7. Đảo một tiêu chí hay đảo cả chuỗi tiêu chí?

Giả sử kiểu dữ liệu:

```java
record Result(String code, int score) {}
```

Yêu cầu là điểm giảm dần, khi bằng điểm thì mã tăng dần.

Cách đọc đúng:

```java
Comparator<Result> scoreDescendingCodeAscending =
        Comparator.comparingInt(Result::score)
                .reversed()
                .thenComparing(Result::code);
```

`reversed()` chỉ tác động lên comparator điểm tại thời điểm nó được gọi. Sau đó, tiêu chí mã tăng dần mới được ghép thêm.

Biểu thức khác:

```java
Comparator<Result> scoreAndCodeDescending =
        Comparator.comparingInt(Result::score)
                .thenComparing(Result::code)
                .reversed();
```

đảo cả hai tiêu chí: điểm giảm dần và mã cũng giảm dần khi hòa điểm.

Thứ tự ưu tiên vẫn là điểm trước rồi mã; thay đổi nằm ở chiều so sánh của từng phần.

Nếu cần xử lý null, phải xác định null ở tiêu chí nào. Đặt một lớp `nullsLast` ở cuối cả chuỗi không có nghĩa mọi trường bên trong đều tự được bảo vệ.

## 8. Null của phần tử khác null của thuộc tính

Xét:

```java
record Task(String code, Integer priority) {}
```

Có thể có ba trường hợp riêng:

1. Tham chiếu `Task` là null.
2. `Task` tồn tại nhưng `priority` là null.
3. `Task` tồn tại nhưng `code` là null.

### 8.1. Chỉ bọc phần tử chưa đủ

Đoạn minh họa sau chưa bảo vệ thuộc tính null:

```java
Comparator<Task> incomplete = Comparator.nullsLast(
        Comparator.comparing(Task::priority));
```

Lớp ngoài xử lý một `Task` null. Khi cả hai task không null, comparator bên trong trích `priority`. Nếu priority null, thứ tự tự nhiên dùng cho thuộc tính đó vẫn có thể ném `NullPointerException`.

Tương tự, `comparingInt(task -> task.priority())` phải chuyển `Integer` thành `int`. Priority null gây lỗi khi mở hộp, trước khi có cơ hội so sánh số nguyên.

### 8.2. Xử lý null ở đúng lớp dữ liệu

Yêu cầu cụ thể:

- Priority không null giảm dần.
- Priority null ở sau những priority có thật.
- Khi bằng priority, code tăng dần và code null ở cuối nhóm hòa.
- Cả task null đứng cuối toàn bộ danh sách.

Xây từng lớp:

```java
Comparator<Task> priorityDescending = Comparator.comparing(
        Task::priority,
        Comparator.nullsLast(Comparator.reverseOrder()));

Comparator<Task> codeAscending = Comparator.comparing(
        Task::code,
        Comparator.nullsLast(Comparator.naturalOrder()));

Comparator<Task> complete = Comparator.nullsLast(
        priorityDescending.thenComparing(codeAscending));
```

`nullsLast` bên trong xử lý các **thuộc tính**; `nullsLast` bên ngoài xử lý **đối tượng Task**.

Khi hai priority đều null, comparator priority trả 0, nên tiêu chí code vẫn được xét. Nếu chính sách nghiệp vụ không muốn phân biệt thêm trong nhóm đó, phải mô tả một quy tắc khác có chủ đích.

## 9. Chương trình hoàn chỉnh với nhiều lớp null

Lưu vào **NullableTaskSortDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class NullableTaskSortDemo {
    record Task(String code, Integer priority) {}

    private static String describe(Task task) {
        if (task == null) {
            return "<null task>";
        }
        return String.valueOf(task.code()) + ":" + task.priority();
    }

    private static List<String> sorted(
            List<Task> source, Comparator<Task> comparator) {
        List<Task> copy = new ArrayList<>(source);
        copy.sort(comparator);
        return copy.stream().map(NullableTaskSortDemo::describe).toList();
    }

    public static void main(String[] args) {
        List<Task> tasks = Arrays.asList(
                new Task("B", 2),
                new Task("A", 2),
                new Task("C", null),
                null,
                new Task(null, 2),
                new Task("D", 1));

        Comparator<Task> priorityDescending = Comparator.comparing(
                Task::priority,
                Comparator.nullsLast(Comparator.reverseOrder()));

        Comparator<Task> codeAscending = Comparator.comparing(
                Task::code,
                Comparator.nullsLast(Comparator.naturalOrder()));

        Comparator<Task> rule = Comparator.nullsLast(
                priorityDescending.thenComparing(codeAscending));

        System.out.println("Selected rule: " + sorted(tasks, rule));
        System.out.println("Reverse whole rule: "
                + sorted(tasks, rule.reversed()));
        System.out.println("Original size: " + tasks.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NullableTaskSortDemo.java
java NullableTaskSortDemo
```

Stdout:

```text
Selected rule: [A:2, B:2, null:2, D:1, C:null, <null task>]
Reverse whole rule: [<null task>, C:null, D:1, null:2, B:2, A:2]
Original size: 6
```

### Đọc các nhóm dữ liệu

Với `rule`:

1. Priority 2 đứng trước priority 1.
2. Trong nhóm priority 2, code `A` rồi `B`, sau đó mới đến code null.
3. Task `C` có priority null đứng sau các task có priority thật.
4. Tham chiếu task null ở cuối cùng.

Chuỗi `null:2` trong output biểu diễn **task tồn tại, code null, priority 2**. Ký hiệu `<null task>` biểu diễn **không có đối tượng task**. Chương trình in khác nhau để không lẫn hai trạng thái.

Khi gọi `rule.reversed()`, toàn bộ thứ tự đảo lại: task null lên đầu, priority null đi lên, các tiêu chí giá trị thực và thứ tự mã cũng đổi chiều. Đây không còn là yêu cầu “priority giảm, code tăng, null cuối” ban đầu.

Muốn chỉ đổi chiều priority mà giữ các chính sách khác, hãy dựng lại phần comparator priority ở lớp tương ứng rồi ghép lại.

## 10. reverseOrder trong lớp Collections

Ngoài `Comparator.reverseOrder()`, Java có hai phương thức quen thuộc trong `Collections`:

| Biểu thức | Ý nghĩa |
| --- | --- |
| `Collections.reverseOrder()` | Thứ tự tự nhiên đảo chiều |
| `Collections.reverseOrder(c)` | Đảo thứ tự do comparator `c` định nghĩa |

Với `c` không null, lời gọi thứ hai đáp ứng cùng ý định thứ tự như `c.reversed()`. Nếu `c` đã hỗ trợ null, việc đảo cũng đảo quan hệ giữa null và các giá trị khác.

Có một trường hợp đặc biệt của overload nhận comparator: **`Collections.reverseOrder(null)` dùng thứ tự tự nhiên đảo chiều**. Điều đó khác với ý nghĩa của comparator null trong `Comparator.nullsFirst(null)`.

Xem hợp đồng của [Collections.reverseOrder không tham số](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#reverseOrder()) và [Collections.reverseOrder nhận comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#reverseOrder(java.util.Comparator)).

### Những giá trị null trong API không có cùng ý nghĩa

| Nơi truyền null | Ý nghĩa |
| --- | --- |
| Một phần tử trong dữ liệu | Một giá trị cần chính sách chấp nhận hoặc từ chối |
| `Comparator.nullsFirst(null)` | Null đầu; mọi giá trị không null cùng hạng |
| `Comparator.nullsLast(null)` | Null cuối; mọi giá trị không null cùng hạng |
| `Collections.reverseOrder(null)` | Dùng thứ tự tự nhiên đảo chiều |
| `list.sort(null)` | Dùng thứ tự tự nhiên; không tự trở thành phép sort hỗ trợ phần tử null |

Với biến comparator `c` có giá trị null, gọi `c.reversed()` là gọi phương thức trên tham chiếu null và gây `NullPointerException`. Không áp dụng trường hợp đặc biệt của `Collections.reverseOrder(null)` cho mọi phương thức cùng tên hoặc có liên quan.

## 11. Comparator đảo và thao tác đảo danh sách

`Collections.reverse(list)` thay đổi thứ tự vị trí hiện có:

```text
phần tử cuối -> ... -> phần tử đầu
```

Nó không cần biết các phần tử là số, chuỗi hay task, không xét tiêu chí, và không tự quyết định thứ tự tăng hay giảm. Vì không cần so sánh phần tử, thao tác đảo vị trí không cần một comparator để xử lý null.

Ví dụ, nếu dãy gốc đã được sắp tăng theo một comparator, đảo dãy có thể cho thứ tự giảm theo quan hệ đó. Nhưng nếu có các phần tử cùng hạng, đảo vị trí còn đảo thứ tự tương đối của chúng. Sắp xếp ổn định bằng comparator đảo sẽ giữ thứ tự tương đối của những phần tử cùng hạng. Hai thao tác có thể cùng đạt thứ tự hợp lệ nhưng khác vị trí của các phần tử hòa nhau.

Vì vậy, hãy chọn thao tác theo mục tiêu:

- Muốn đảo dãy hiện tại: `Collections.reverse`.
- Muốn tạo thứ tự theo quy tắc giảm dần: truyền comparator phù hợp vào thao tác sắp xếp.

Chi tiết sửa danh sách nằm trong [API Collections.reverse](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#reverse(java.util.List)).

## 12. Những lỗi thường gặp

| Biểu hiện hoặc cách viết | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Dùng `reverseOrder()` nhưng kết quả không theo độ dài | Đã đảo thứ tự tự nhiên thay vì quy tắc tùy chỉnh | Dùng `byLength.reversed()` |
| Null lên đầu sau khi đảo | Đã đảo cả lớp `nullsLast` | Bọc phần giảm dần bằng `nullsLast` nếu null phải ở cuối |
| Task không null nhưng vẫn lỗi null | Thuộc tính trích ra null chưa được bảo vệ | Truyền comparator hỗ trợ null ở cấp thuộc tính |
| Priority null gây lỗi khi dùng `comparingInt` | Mở hộp `Integer` null sang `int` | Dùng so sánh kiểu boxed có chính sách null |
| Mọi giá trị không null giữ nguyên thứ tự, không tăng dần | Đã truyền comparator null vào `nullsFirst/nullsLast` | Truyền comparator thực cho các giá trị đó |
| Cả tiêu chí phụ bị giảm dần | Gọi `reversed()` sau khi đã ghép chuỗi | Đảo từng tiêu chí ở đúng vị trí |
| Khởi tạo dữ liệu thử null bằng `List.of` bị lỗi | Factory này không chấp nhận phần tử null | Dùng một cấu trúc hỗ trợ null cho ví dụ, như `ArrayList` |
| Gọi sort lên danh sách không cho sửa bị lỗi | Việc sort cần sửa thứ tự danh sách | Tạo bản `ArrayList` khi cần sắp một bản sao |
| Tự đổi dấu kết quả compare | Có thể tràn số với một kết quả âm hợp lệ | Dùng `reversed()` hoặc đổi chiều đối số |
| Dùng `Collections.reverse` như phép sort giảm dần | Chỉ đảo vị trí, không sắp theo giá trị | Gọi sort với comparator giảm dần |

Các lớp bao null và thao tác đảo không làm một comparator sai trở nên đúng. Comparator nền vẫn cần có hợp đồng dấu, tính bắc cầu và sự nhất quán phù hợp với dữ liệu.

## 13. Cách thiết kế và kiểm chứng một quy tắc

Trước khi viết biểu thức ghép, mô tả yêu cầu bằng câu đầy đủ:

> Priority giảm dần; priority thiếu để cuối. Khi hòa priority, code tăng dần; code thiếu để cuối nhóm. Task null để cuối toàn bộ.

Sau đó xây từng lớp và kiểm tra các trường hợp:

1. Hai giá trị thực khác nhau.
2. Hai giá trị thực cùng tiêu chí chính.
3. Một null và một không null, ở cả hai chiều đối số.
4. Hai null.
5. Null của thuộc tính và null của toàn phần tử.
6. Tiêu chí chính hòa nhưng tiêu chí phụ khác.
7. Đảo riêng tiêu chí so với đảo toàn bộ comparator.
8. Danh sách nguồn có được phép thay đổi hay phải giữ nguyên?

Không kết luận một comparator hỗ trợ null chỉ vì thử trên danh sách rỗng hoặc một phần tử mà không có lỗi: thuật toán có thể chưa gọi phép so sánh liên quan.

Nếu muốn kiểm chứng chính sách null trực tiếp, có thể gọi `compare(null, value)`, `compare(value, null)` và `compare(null, null)` trên comparator đã được thiết kế để chấp nhận những trường hợp đó, rồi đối chiếu dấu với yêu cầu.

## 14. Bài tập tự kiểm tra

1. Với các chuỗi `z, aa, bbb`, giải thích vì sao `reverseOrder()` và comparator đảo theo độ dài cho kết quả khác nhau.
2. Dự đoán vị trí null của `nullsFirst(c).reversed()` và `nullsFirst(c.reversed())`.
3. Sửa một quy tắc đang giảm cả điểm và mã để chỉ giảm điểm, mã vẫn tăng.
4. Trong ví dụ Task, đổi priority từ giảm dần sang tăng dần nhưng vẫn giữ null cuối ở cả ba vị trí liên quan.
5. Giải thích vì sao `Comparator.nullsLast(null)` không tương đương `Comparator.nullsLast(Comparator.naturalOrder())`.
6. Tạo hai phần tử cùng hạng nhưng có mã khác, rồi so sánh việc đảo vị trí với sắp xếp ổn định bằng comparator đảo.

Điểm cần nắm là phạm vi tác động của từng lớp: **đảo quy tắc hiện có, chọn thứ tự tự nhiên ngược, đặt null ở một phía, và thực hiện thao tác trên danh sách** là những quyết định riêng có thể kết hợp theo đúng yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-006">Tránh tràn số khi viết phép so sánh bằng phép trừ</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tràn số khi viết phép so sánh bằng phép trừ

Khi viết `Comparable.compareTo` hoặc `Comparator.compare`, một cách làm dễ nghĩ tới là lấy giá trị thứ nhất trừ giá trị thứ hai:

```java
return first - second;
```

Lập luận có vẻ hợp lý: nếu thứ nhất nhỏ hơn thì hiệu âm, bằng nhau thì hiệu bằng không, lớn hơn thì hiệu dương. Lập luận đó đúng với phép toán trên các số nguyên không bị giới hạn độ lớn. Tuy nhiên, `int` và `long` của Java có phạm vi hữu hạn; phép trừ có thể **tràn số (overflow)** và trả về dấu sai.

Một comparator chỉ sai ở giá trị biên vẫn có thể làm hỏng kết quả sắp xếp, tìm kiếm hoặc nhận diện khóa trong collection.

Bài này tập trung vào cách nhận diện và sửa lỗi đó. Các chương trình hoàn chỉnh sử dụng API Java 17, đã được biên dịch với `--release 17` và chạy để đối chiếu stdout. Những đoạn rút gọn khác minh họa biểu thức hoặc thân phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn có thể:

- Giải thích vì sao phép trừ không phải cách so sánh an toàn trên toàn miền `int` hoặc `long`.
- Phân biệt lỗi tràn phép toán với lỗi ép kiểu thu hẹp.
- Dùng `Integer.compare`, `Long.compare` và các hàm tạo comparator đúng kiểu.
- Đảo chiều hoặc kết hợp tiêu chí mà không thao tác sai trên dấu.
- Kiểm tra hợp đồng comparator với dữ liệu biên.

## 1. So sánh cần dấu, không cần khoảng cách

Với `compare(first, second)`:

| Kết quả | Ý nghĩa |
| --- | --- |
| Âm | `first` đứng trước `second` |
| Bằng không | Hai giá trị ngang hàng theo quy tắc so sánh |
| Dương | `first` đứng sau `second` |

Kết quả không có nhiệm vụ mô tả “hai số cách nhau bao xa”. Vì vậy, việc tính cả hiệu thường tạo thêm rủi ro mà không cung cấp thông tin cần thiết.

Ví dụ, để so `Integer.MIN_VALUE` với `Integer.MAX_VALUE`, ta chỉ cần biết giá trị thứ nhất nhỏ hơn. Không cần đưa khoảng cách toán học giữa hai số vào một biến `int`.

Một comparator hợp lệ cũng không bắt buộc luôn trả về đúng `-1`, `0` hoặc `1`. Bên sử dụng phải kiểm tra dấu:

```java
if (order.compare(first, second) < 0) {
    // first đứng trước second.
}
```

Việc xử lý dấu đúng là nền tảng cho cả cài đặt comparator lẫn cách sử dụng kết quả của nó.

## 2. Phạm vi hữu hạn làm phép trừ đổi dấu

Hai kiểu số nguyên thường gặp:

| Kiểu | Phạm vi |
| --- | --- |
| `int` | `-2_147_483_648` đến `2_147_483_647` |
| `long` | `-9_223_372_036_854_775_808` đến `9_223_372_036_854_775_807` |

Xét:

```text
first  = 2_147_483_647
second = -1
```

Theo toán học:

```text
first - second = 2_147_483_648
```

Giá trị đó lớn hơn `Integer.MAX_VALUE`. Khi thực hiện bằng phép trừ `int` thông thường, kết quả tràn thành:

```text
-2_147_483_648
```

Một số dương đã thành số âm. Comparator dùng hiệu sẽ kết luận `first` đứng trước `second`, trái với quan hệ số cần có.

### Java không tự báo ngoại lệ cho phép trừ int hoặc long bị tràn

Với phép toán số nguyên thông thường, Java giữ kết quả theo số bit của kiểu; những bit cao vượt phạm vi không được giữ lại. Kết quả được diễn giải lại theo kiểu số nguyên có dấu.

Điều này có thể tạo ra kết quả hợp lệ về mặt kiểu dữ liệu nhưng sai với mục đích so sánh.

Không nên chờ `ArithmeticException` từ phép trừ thông thường để nhận diện lỗi. Các hàm có kiểm tra tràn như `Math.subtractExact` có hợp đồng khác, được bàn ở phần sau.

### Chiều ngược lại cũng có thể sai

```text
Integer.MIN_VALUE - 1
```

có giá trị toán học nhỏ hơn giới hạn dưới của `int`, nhưng kết quả `int` trở thành `Integer.MAX_VALUE`, một số dương.

Vì vậy, không có quy tắc “tràn chỉ làm số lớn thành số nhỏ” đủ dùng để cứu comparator. Cần tránh phép toán có thể tràn ngay từ thiết kế.

## 3. Một comparator sai có thể vi phạm cả hợp đồng thứ tự

Lỗi không chỉ là sắp nhầm hai phần tử. Nó có thể khiến quy tắc không còn biểu diễn một thứ tự nhất quán.

Giả sử comparator là:

```java
Comparator<Integer> unsafe = (first, second) -> first - second;
```

Chọn ba giá trị:

```text
a = Integer.MIN_VALUE
b = 0
c = Integer.MAX_VALUE
```

Theo comparator dùng phép trừ:

- `a - b` âm, nên nó nói `a < b`.
- `b - c` âm, nên nó nói `b < c`.
- `a - c` tràn thành `1`, nên nó lại nói `a > c`.

Quy tắc đã vi phạm **tính bắc cầu**: nếu `a < b` và `b < c`, thì phải có `a < c`.

Nó còn có thể vi phạm quy tắc đổi chiều phải đổi dấu. Ví dụ, `Integer.MIN_VALUE - 0` và `0 - Integer.MIN_VALUE` đều cho kết quả `Integer.MIN_VALUE`, tức đều âm.

### Tác động đến collection

| Nơi sử dụng | Rủi ro khi quy tắc so sánh sai |
| --- | --- |
| Sắp xếp `List` hoặc mảng đối tượng | Thứ tự sai; thuật toán có thể phát hiện vi phạm hợp đồng và báo lỗi |
| `TreeSet` | Phần tử được tổ chức hoặc nhận diện theo quan hệ không đúng |
| Khóa của `TreeMap` | Tìm và cập nhật khóa dựa trên quy tắc sai |
| `PriorityQueue` | Phần tử được lấy theo ưu tiên không đúng với yêu cầu |
| Tìm kiếm nhị phân trên dữ liệu đã sắp | Kết quả không đáng tin nếu điều kiện thứ tự không còn được bảo đảm |

Không phải mọi lần dùng comparator sai đều ném ngoại lệ. Việc một danh sách nhỏ “vẫn sắp đúng” không chứng minh quy tắc hợp lệ trên toàn miền dữ liệu.

Bài không đưa ra một stdout cố định cho việc sắp xếp bằng comparator sai. Khi hợp đồng bị vi phạm, không nên dùng một kết quả tình cờ của thuật toán làm hành vi cần dựa vào.

## 4. Chương trình kiểm chứng lỗi int và cách sửa

Tạo tệp **`SubtractionOrderDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SubtractionOrderDemo {
    public static void main(String[] args) {
        int left = Integer.MAX_VALUE;
        int right = -1;
        int wrapped = left - right;
        long exactForIntOperands = (long) left - right;

        System.out.println("exact int-operand difference = " + exactForIntOperands);
        System.out.println("wrapped int difference = " + wrapped);
        System.out.println("subtraction says left < right = " + (wrapped < 0));
        System.out.println("Integer.compare says left > right = "
                + (Integer.compare(left, right) > 0));

        Comparator<Integer> unsafe = (first, second) -> first - second;
        int first = Integer.MIN_VALUE;
        int middle = 0;
        int last = Integer.MAX_VALUE;

        System.out.println("unsafe first < middle = "
                + (unsafe.compare(first, middle) < 0));
        System.out.println("unsafe middle < last = "
                + (unsafe.compare(middle, last) < 0));
        System.out.println("unsafe first < last = "
                + (unsafe.compare(first, last) < 0));

        List<Integer> values = new ArrayList<>(
                List.of(Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1));
        values.sort(Integer::compare);
        System.out.println("safe sorted = " + values);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 SubtractionOrderDemo.java
java -Dfile.encoding=UTF-8 SubtractionOrderDemo
```

Stdout:

```text
exact int-operand difference = 2147483648
wrapped int difference = -2147483648
subtraction says left < right = true
Integer.compare says left > right = true
unsafe first < middle = true
unsafe middle < last = true
unsafe first < last = false
safe sorted = [-2147483648, -1, 0, 2147483647]
```

### Đọc kết quả

1. `exactForIntOperands` chuyển một toán hạng sang `long` **trước** phép trừ, đủ để chứa hiệu của hai giá trị `int` bất kỳ.
2. `wrapped` được tính bằng `int`, nên tràn và có dấu âm.
3. Comparator dùng phép trừ không giữ được tính bắc cầu ở bộ ba đã chọn.
4. `Integer.compare` xác định đúng quan hệ mà không cần tính hiệu.
5. Danh sách cuối được sắp bằng comparator an toàn, nên thứ tự tăng dần khớp giá trị số.

Việc dùng `long` để hiển thị hiệu chính xác trong ví dụ chỉ là công cụ minh họa. Khi cần so sánh hai `int`, cách trực tiếp vẫn là `Integer.compare`.

## 5. Cách sửa chính: so sánh theo đúng kiểu

### Với int

Thay:

```java
return this.priority - other.priority;
```

bằng:

```java
return Integer.compare(this.priority, other.priority);
```

Hoặc với một comparator:

```java
Comparator<Integer> order = Integer::compare;
```

### Với long

Không dùng:

```java
return (int) (this.sequence - other.sequence);
```

Hãy dùng:

```java
return Long.compare(this.sequence, other.sequence);
```

`Long.compare` trả về một `int` biểu diễn quan hệ, dù các toán hạng có kiểu `long`. Nó không cố nhét khoảng cách giữa hai số `long` vào một số `int`.

### Nếu tự viết bằng phép quan hệ

Với hai biến nguyên thủy có miền giá trị phù hợp, biểu thức minh họa sau cũng không tính hiệu:

```java
return first < second ? -1 : (first > second ? 1 : 0);
```

Các hàm chuẩn thường dễ đọc và ít cơ hội viết sai hơn. Chúng còn thể hiện rõ kiểu dữ liệu cần so sánh.

### Bảng lựa chọn

| Dữ liệu cần so | Lựa chọn nên dùng |
| --- | --- |
| `int` | `Integer.compare(first, second)` |
| `long` | `Long.compare(first, second)` |
| Trường `int` của đối tượng | `Comparator.comparingInt(...)` |
| Trường `long` của đối tượng | `Comparator.comparingLong(...)` |
| Thứ tự ngược của một comparator | `order.reversed()` |
| Nhiều tiêu chí | `thenComparing...` hoặc so từng tiêu chí, chỉ chuyển tiếp khi bằng không |

Với các kiểu số có hợp đồng riêng như `BigInteger` hoặc `BigDecimal`, hãy dùng phép so sánh của chính kiểu thay vì lấy hiệu rồi chuyển kết quả sang `int`.

## 6. Các cách sửa nửa chừng vẫn có lỗi

### 6.1. Ép sang long sau khi trừ là quá muộn

```java
long difference = (long) (first - second);
```

Nếu `first` và `second` là `int`, biểu thức trong ngoặc đã được tính bằng `int`. Phép ép kiểu chỉ chuyển **kết quả đã tràn** thành `long`.

Thậm chí viết:

```java
long difference = first - second;
```

cũng không làm phép trừ tự chuyển thành `long`. Kiểu của biến nhận không thay đổi kiểu toán hạng đã dùng để tính biểu thức.

Nếu thực sự cần hiệu toán học của hai `int`:

```java
long difference = (long) first - second;
```

việc mở rộng xảy ra trước phép toán, nên hiệu nằm trong phạm vi `long`.

### 6.2. Tính hiệu bằng long rồi ép lại int làm mất bảo đảm

Cách này vẫn nguy hiểm:

```java
return (int) ((long) first - second);
```

Phần hiệu có thể đúng trong `long`, nhưng phép ép trở lại `int` làm mất các bit cao. Một hiệu dương ngoài miền `int` có thể trở thành số âm.

Mục đích của comparator không cần hiệu đó. Dùng `Integer.compare` bỏ hẳn bước tính hiệu và thu hẹp không cần thiết.

### 6.3. Hiệu long ép xuống int có thể thành bằng không

Hai giá trị `long`:

```text
0
4_294_967_296
```

khác nhau đúng `2^32`. Hiệu nằm trong miền `long`, nhưng ép hiệu đó xuống `int` có thể cho `0`.

Khi một comparator trả `0` sai:

- Sắp xếp sẽ coi chúng ngang hàng.
- Một cấu trúc như `TreeSet` có thể coi chúng là cùng phần tử.
- `TreeMap` có thể coi chúng là cùng khóa theo quy tắc comparator.

Đây là lỗi **thu hẹp kiểu**, ngay cả khi phép trừ `long` trước đó chưa tràn.

### 6.4. Đổi int thành long không giải quyết toàn bộ miền long

Hiệu của hai số `int` luôn nằm trong phạm vi `long`, nhưng hiệu của hai số `long` bất kỳ có thể vượt phạm vi `long`.

Nếu hai toán hạng vốn là `long`, không có phép “ép sang `long`” nào giúp tăng phạm vi. Hãy dùng `Long.compare`.

### 6.5. Chuyển qua double có thể làm hai số khác nhau thành giống nhau

`double` có phạm vi lớn nhưng không biểu diễn chính xác mọi số nguyên `long`.

Ví dụ, hai số nguyên liên tiếp tại `2^53` và `2^53 + 1` không còn được phân biệt sau phép chuyển sang `double` trong ví dụ dưới đây. So kết quả đã mất thông tin có thể trả về `0`.

Vì vậy:

- Tràn số không phải rủi ro duy nhất.
- “Kiểu số có miền lớn hơn” không đồng nghĩa “giữ nguyên độ chính xác cần thiết”.
- Nếu dữ liệu là `long`, hãy so chúng như `long`.

## 7. Chương trình kiểm chứng lỗi ép kiểu và long

Tạo tệp **`NumericConversionDemo.java`**:

```java
public class NumericConversionDemo {
    public static void main(String[] args) {
        int intLeft = Integer.MAX_VALUE;
        int intRight = -1;

        long castAfter = (long) (intLeft - intRight);
        long castBefore = (long) intLeft - intRight;
        int narrowedAgain = (int) castBefore;

        System.out.println("cast after subtraction = " + castAfter);
        System.out.println("cast before subtraction = " + castBefore);
        System.out.println("narrowed again = " + narrowedAgain);

        long zero = 0L;
        long distant = 1L << 32;
        System.out.println("narrowed long difference = "
                + (int) (zero - distant));
        System.out.println("Long.compare distinct values = "
                + Long.compare(zero, distant));

        long largest = Long.MAX_VALUE;
        long negative = -1L;
        System.out.println("overflowed long difference = " + (largest - negative));
        System.out.println("Long.compare correct direction = "
                + Long.compare(largest, negative));

        long first = 1L << 53;
        long next = first + 1;
        System.out.println("compare after double conversion = "
                + Double.compare((double) first, (double) next));
        System.out.println("compare exact long values = " + Long.compare(first, next));

        try {
            Math.subtractExact(intLeft, intRight);
        } catch (ArithmeticException exception) {
            System.out.println("subtractExact = "
                    + exception.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 NumericConversionDemo.java
java -Dfile.encoding=UTF-8 NumericConversionDemo
```

Stdout:

```text
cast after subtraction = -2147483648
cast before subtraction = 2147483648
narrowed again = -2147483648
narrowed long difference = 0
Long.compare distinct values = -1
overflowed long difference = -9223372036854775808
Long.compare correct direction = 1
compare after double conversion = 0
compare exact long values = -1
subtractExact = ArithmeticException
```

### Mỗi nhóm kết quả nói điều gì?

| Nhóm | Bằng chứng |
| --- | --- |
| Ép sau phép toán | Kết quả tràn của `int` được giữ nguyên khi chuyển thành `long` |
| Ép trước phép toán | Hiệu của hai `int` được tính chính xác bằng `long` |
| Ép trở lại `int` | Thông tin bị mất, dấu có thể sai |
| Hiệu hai `long` ép xuống `int` | Hai số khác nhau có thể cho kết quả `0` |
| Tràn `long` | Phép trừ của hai `long` hợp lệ vẫn có thể đổi dấu |
| Chuyển sang `double` | Hai giá trị `long` khác nhau có thể bị làm thành cùng giá trị |
| `subtractExact` | Tràn số được báo bằng ngoại lệ, không biến thành một quan hệ so sánh hợp lệ |

Các lỗi khác nhau cần được gọi tên đúng. Một giá trị thu hẹp thành `0` không nhất thiết do phép trừ đã tràn; phần tính hiệu có thể đúng và phép ép mới là chỗ phá hỏng dữ liệu.

## 8. Vì sao subtractExact, abs hoặc signum không tự cứu phép trừ?

### Math.subtractExact kiểm tra phép tính, không cung cấp thứ tự đầy đủ

`Math.subtractExact(first, second)` ném `ArithmeticException` nếu hiệu vượt phạm vi.

Điều đó hữu ích khi nghiệp vụ thật sự cần một **hiệu có thể biểu diễn chính xác** và muốn từ chối phép tính không hợp lệ. Tuy nhiên, một comparator cần so được hai số hợp lệ ở hai đầu phạm vi, dù khoảng cách giữa chúng quá lớn để lưu cùng kiểu.

Thay `first - second` bằng `Math.subtractExact(first, second)` sẽ biến sai dấu âm thầm thành lỗi lúc chạy; nó chưa thay thế được `Integer.compare` hoặc `Long.compare`.

### signum sau một phép trừ đã tràn vẫn đọc dấu sai

Cách sau vẫn lỗi:

```java
return Integer.signum(first - second);
```

`signum` chỉ đọc dấu của **kết quả đang có**. Nếu kết quả trừ đã tràn và sai dấu, chuẩn hóa nó thành `-1` hoặc `1` không phục hồi ý nghĩa ban đầu.

Trong bài, `Integer.signum` chỉ được dùng để kiểm tra hoặc hiển thị dấu của một kết quả; nó không được dùng như cách sửa phép trừ bị tràn.

### abs làm mất hướng và còn có giới hạn riêng

`Math.abs(first - second)` không thể là comparator tăng dần, vì giá trị tuyệt đối làm mất thông tin ai lớn hơn ai.

Ngoài ra, số đối dương của `Integer.MIN_VALUE` không biểu diễn được bằng `int`; thao tác trị tuyệt đối với giá trị đó có giới hạn tương ứng. Cần hiểu hợp đồng hàm số, không ghép chúng để đoán dấu.

## 9. Đảo chiều đúng cách

Một comparator hợp lệ có thể trả về bất kỳ giá trị âm hoặc dương nào. Vì vậy, cách viết:

```java
return -order.compare(first, second);
```

không phải lựa chọn an toàn cho một comparator tùy ý.

Nếu kết quả gốc là `Integer.MIN_VALUE`, phép lấy số đối vẫn cho `Integer.MIN_VALUE`, nên dấu không đảo.

### Các cách phù hợp

- Với `Integer.compare` hoặc `Long.compare`, đổi thứ tự hai đối số để đổi chiều.
- Với một đối tượng `Comparator`, dùng `reversed()`.
- Khi kết hợp nhiều tiêu chí, xác định đảo toàn bộ thứ tự hay chỉ đảo một tiêu chí.

Ví dụ quy tắc:

```java
Comparator<Job> ascending = Comparator.comparingInt(Job::priority)
        .thenComparingLong(Job::sequence)
        .thenComparing(Job::id);
```

thì:

- `ascending.reversed()` đảo toàn bộ chuỗi tiêu chí.
- Đảo comparator của `priority` trước rồi mới `thenComparing...` chỉ đảo tiêu chí ưu tiên đó.

Đổi vị trí gọi `reversed()` có thể thay đổi ý nghĩa thứ tự, dù cả hai cách đều không bị tràn.

## 10. So sánh nhiều tiêu chí mà không cộng hoặc trừ kết quả

Cách viết nhiều trường bằng một phép tính gộp thường gây lỗi:

```java
return first.priority - second.priority
        + (int) (first.sequence - second.sequence);
```

Nó có nhiều vấn đề:

1. Mỗi phép trừ có thể tràn hoặc bị thu hẹp sai.
2. Tiêu chí sau có thể lấn át tiêu chí trước.
3. Hai hiệu có thể triệt tiêu nhau và trả về `0` dù dữ liệu khác nhau.
4. Quy tắc không còn thể hiện rõ tiêu chí nào được ưu tiên.

### Cách so từng bước

Trong một thân phương thức so sánh, cách tổ chức hợp lý là:

```java
int result = Integer.compare(first.priority(), second.priority());
if (result != 0) {
    return result;
}

return Long.compare(first.sequence(), second.sequence());
```

Tiêu chí thứ hai chỉ được dùng khi tiêu chí thứ nhất ngang hàng. Nếu cần thêm tiêu chí, tiếp tục theo đúng quy tắc đó.

### Dùng các hàm tạo comparator theo kiểu

`comparingInt` và `comparingLong` giúp diễn đạt cùng ý nghĩa bằng một chuỗi:

```java
Comparator<Job> order = Comparator.comparingInt(Job::priority)
        .thenComparingLong(Job::sequence)
        .thenComparing(Job::id);
```

Trong ví dụ, `id` là một tiêu chí phụ xác định thứ tự giữa những công việc có cùng `priority` và `sequence`. Không được coi “so bằng không” chỉ là chuyện trình bày; nếu quy tắc được dùng trong tập hợp duy nhất hoặc làm khóa, nó ảnh hưởng tới cách nhận diện phần tử.

Ví dụ giả định công việc và `id` đều không `null`. Nếu dữ liệu cho phép thiếu giá trị, cần có chính sách riêng và đặt nó rõ ở comparator phù hợp.

## 11. Chương trình hoàn chỉnh: comparator an toàn và đảo chiều

Tạo tệp **`SafeJobOrderDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SafeJobOrderDemo {
    public record Job(String id, int priority, long sequence) {
    }

    public static void main(String[] args) {
        List<Job> input = List.of(
                new Job("A", Integer.MAX_VALUE, 0),
                new Job("B", Integer.MIN_VALUE, Long.MAX_VALUE),
                new Job("C", 0, Long.MAX_VALUE),
                new Job("D", 0, Long.MIN_VALUE),
                new Job("E", 0, Long.MIN_VALUE));

        Comparator<Job> ascending = Comparator.comparingInt(Job::priority)
                .thenComparingLong(Job::sequence)
                .thenComparing(Job::id);

        List<Job> jobs = new ArrayList<>(input);
        jobs.sort(ascending);
        System.out.println("ascending ids = " + ids(jobs));

        jobs.sort(ascending.reversed());
        System.out.println("reverse all ids = " + ids(jobs));

        Comparator<Job> priorityDescending = Comparator
                .comparingInt(Job::priority).reversed()
                .thenComparingLong(Job::sequence)
                .thenComparing(Job::id);

        jobs.sort(priorityDescending);
        System.out.println("reverse priority only ids = " + ids(jobs));

        Comparator<Integer> arbitraryMagnitude = (left, right) -> {
            if (left < right) {
                return Integer.MIN_VALUE;
            }
            if (left > right) {
                return Integer.MAX_VALUE;
            }
            return 0;
        };

        int wrongReverse = -arbitraryMagnitude.compare(1, 2);
        int safeReverse = arbitraryMagnitude.reversed().compare(1, 2);
        System.out.println("negated result sign = " + Integer.signum(wrongReverse));
        System.out.println("reversed comparator sign = " + Integer.signum(safeReverse));
    }

    private static List<String> ids(List<Job> jobs) {
        return jobs.stream().map(Job::id).toList();
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 SafeJobOrderDemo.java
java -Dfile.encoding=UTF-8 SafeJobOrderDemo
```

Stdout:

```text
ascending ids = [B, D, E, C, A]
reverse all ids = [A, C, E, D, B]
reverse priority only ids = [A, D, E, C, B]
negated result sign = -1
reversed comparator sign = 1
```

### Theo dõi thứ tự công việc

- **Tăng dần:** `B` có priority nhỏ nhất; `A` lớn nhất. Các công việc priority bằng `0` được xét tiếp theo sequence rồi id.
- **Đảo toàn bộ:** cả priority, sequence và id đều bị đảo thứ tự.
- **Chỉ đảo priority:** priority giảm dần, nhưng các công việc cùng priority vẫn theo sequence tăng dần rồi id tăng dần.

Tên `priority` không tự quy định số lớn hơn hay nhỏ hơn phải được xử lý trước. Chương trình chọn rõ chiều tăng hoặc giảm; trong hệ thống thực tế, cần thống nhất ý nghĩa với yêu cầu nghiệp vụ.

### Vì sao comparator có độ lớn kết quả tùy ý vẫn hợp lệ?

`arbitraryMagnitude` trả về:

- `Integer.MIN_VALUE` khi nhỏ hơn.
- `Integer.MAX_VALUE` khi lớn hơn.
- `0` khi bằng nhau.

Dù độ lớn không đối xứng, dấu vẫn mô tả đúng thứ tự số. Hợp đồng yêu cầu quan hệ giữa các dấu, không yêu cầu hai kết quả là số đối chính xác của nhau.

Phép âm trực tiếp không đảo được `Integer.MIN_VALUE`, còn `reversed()` cho chiều đúng. Ví dụ này cho thấy không nên dựa vào giả định ngầm rằng comparator bên ngoài luôn trả về `-1` hoặc `1`.

## 12. Khi nào phép trừ không tràn?

Không nên kết luận rằng mọi phép trừ đều sai. Nó chỉ không an toàn như một cách so sánh tổng quát nếu không có bảo đảm về miền giá trị.

Nếu hai điểm số luôn thuộc `1..100`, hiệu nằm trong `-99..99`, có thể biểu diễn được bằng `int`.

Tuy nhiên, dùng phép trừ vẫn đặt ra nghĩa vụ chứng minh và giữ bất biến:

- Miền giá trị được kiểm tra ở đâu?
- Có đường nhập dữ liệu nào bỏ qua kiểm tra không?
- Yêu cầu có thể đổi sang số âm hoặc một phạm vi lớn hơn không?
- Cùng phương thức có được dùng lại cho kiểu dữ liệu khác không?
- Tiêu chí mới có làm biểu thức tính gộp vượt phạm vi không?

Nếu mục đích chỉ là so sánh, `Integer.compare` thường rõ ràng hơn và không phụ thuộc việc miền dữ liệu sau này được mở rộng.

### Phân biệt số có dấu với cách diễn giải không dấu

`Integer.compare` và `Long.compare` so theo giá trị có dấu. Nếu một miền nghiệp vụ thực sự diễn giải mẫu bit như số không dấu, Java có các thao tác như `compareUnsigned`.

Đây là lựa chọn về **ngữ nghĩa dữ liệu**, không phải cách chữa tùy tiện cho comparator bị tràn. Không đổi sang so không dấu chỉ vì thấy một giá trị âm, vì có thể làm sai hoàn toàn thứ tự mà nghiệp vụ muốn.

## 13. Kiểm tra hợp đồng bằng dữ liệu biên

Các kiểm tra hữu ích gồm:

1. **Tự so:** `compare(x, x) == 0`.
2. **Đổi chiều:** dấu của `compare(x, y)` phải là số đối của dấu `compare(y, x)`.
3. **Bắc cầu:** nếu `x > y` và `y > z` thì `x > z`.
4. **Nhóm ngang hàng:** nếu `compare(x, y) == 0`, quan hệ của `x` và `y` với một `z` phải nhất quán.

Tập mẫu cần có số âm, số dương, `0`, giá trị bằng nhau và các đầu phạm vi.

### Chương trình hoàn chỉnh

Tạo tệp **`BoundaryContractDemo.java`**:

```java
import java.util.Comparator;

public class BoundaryContractDemo {
    public static void main(String[] args) {
        int[] samples = {
                Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0,
                1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };

        Comparator<Integer> unsafe = (left, right) -> left - right;
        Comparator<Integer> safe = Integer::compare;

        System.out.println("subtraction passes samples = "
                + obeysContract(unsafe, samples));
        System.out.println("Integer.compare passes samples = "
                + obeysContract(safe, samples));
    }

    private static boolean obeysContract(
            Comparator<Integer> order, int[] samples) {
        for (int first : samples) {
            if (order.compare(first, first) != 0) {
                return false;
            }

            for (int second : samples) {
                int firstSecond = Integer.signum(order.compare(first, second));
                int secondFirst = Integer.signum(order.compare(second, first));

                if (firstSecond != -secondFirst) {
                    return false;
                }

                for (int third : samples) {
                    int firstThird = Integer.signum(order.compare(first, third));
                    int secondThird = Integer.signum(order.compare(second, third));

                    if (firstSecond > 0 && secondThird > 0 && firstThird <= 0) {
                        return false;
                    }

                    if (firstSecond == 0 && firstThird != secondThird) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 BoundaryContractDemo.java
java -Dfile.encoding=UTF-8 BoundaryContractDemo
```

Stdout:

```text
subtraction passes samples = false
Integer.compare passes samples = true
```

### Giới hạn của kiểm chứng này

Chương trình kiểm tra các cặp và bộ ba trong một tập mẫu hữu hạn, dừng khi thấy vi phạm.

- Kết quả `false` là bằng chứng có lỗi trên ít nhất một trường hợp đã kiểm tra.
- Kết quả `true` chỉ chứng minh những mẫu đã chọn vượt qua điều kiện trong chương trình.
- Nó không thay thế việc lập luận rằng quy tắc đúng cho toàn bộ miền dữ liệu.
- Nó chưa kiểm tra các chính sách ngoài mục tiêu như xử lý `null`, thay đổi đối tượng sau khi thêm vào cây hoặc tính nhất quán với `equals`.

Với comparator so số nguyên chuẩn, lập luận dựa trên việc so quan hệ trực tiếp và không tính hiệu là phần quan trọng bên cạnh kiểm tra thực thi.

Khi chuyển bài học vào kiểm thử dự án, có thể dùng cùng các nhóm tình huống để xây dựng kiểm tra phù hợp. Không cần đưa comparator sai vào hệ thống chỉ để “xem khi nào thuật toán báo lỗi”.

## 14. Danh sách tự rà soát khi viết compareTo hoặc Comparator

| Câu hỏi | Dấu hiệu cần sửa |
| --- | --- |
| Có dùng `a - b` để trả kết quả? | Cần chứng minh miền giá trị hoặc đổi sang hàm compare đúng kiểu |
| Có ép hiệu `long` về `int`? | Có thể sai dấu hoặc trả `0` sai |
| Có mở rộng sau phép trừ? | Tràn có thể đã xảy ra trước khi ép kiểu |
| Có chuyển `long` sang `double` để so? | Có nguy cơ mất độ chính xác |
| Có dùng `signum` trên hiệu? | Chuẩn hóa một dấu sai không sửa được phép trừ |
| Có lấy số đối của kết quả comparator tùy ý? | `Integer.MIN_VALUE` không đảo dấu được bằng phép âm |
| Có cộng các kết quả tiêu chí? | Tiêu chí có thể lấn át hoặc triệt tiêu nhau |
| Có dữ liệu ở đầu phạm vi trong kiểm tra? | Nếu chưa, ví dụ nhỏ có thể đang che lỗi |
| Có dùng thứ tự cho cấu trúc duy nhất? | Cần kiểm tra ý nghĩa của `compare == 0` |

Mục tiêu của việc rà soát là để quy tắc so sánh mô tả đúng quan hệ nghiệp vụ, không chỉ vượt qua một lần sắp xếp tình cờ.

## 15. Thực hành

### Bài 1: bắt lỗi bằng một cặp số

Viết ra giá trị toán học và giá trị `int` thực tế của:

```text
Integer.MAX_VALUE - (-1)
Integer.MIN_VALUE - 1
0 - Integer.MIN_VALUE
```

Với mỗi biểu thức, xác định dấu đó có mô tả đúng quan hệ giữa hai toán hạng không. Kiểm tra lại bằng `Integer.compare`.

### Bài 2: phân biệt tràn với thu hẹp

Chọn:

```text
first  = 0L
second = 1L << 32
```

So sánh kết quả của:

- Hiệu `long` ban đầu.
- Hiệu sau khi ép về `int`.
- `Long.compare(first, second)`.

Giải thích tại sao hai giá trị khác nhau có thể bị comparator sai coi là ngang hàng mà không có tràn ở bước trừ `long`.

### Bài 3: sửa thứ tự công việc

Từ `SafeJobOrderDemo`, tạo yêu cầu mới:

1. Priority tăng dần.
2. Nếu priority bằng nhau, sequence giảm dần.
3. Nếu vẫn bằng nhau, id tăng dần.

Không trừ hai giá trị và không lấy số đối của kết quả tùy ý. Với dữ liệu sẵn có, thứ tự id cần là:

```text
[B, C, D, E, A]
```

Giải thích vị trí gọi `reversed()` để chỉ đảo sequence, không đảo các tiêu chí khác.

### Bài 4: mở rộng kiểm tra long

Viết một bộ kiểm tra tương tự `BoundaryContractDemo` cho `long`, dùng các giá trị:

```text
Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE
```

Thêm hai giá trị cách nhau đúng `2^32`. Đối chiếu comparator ép hiệu về `int` với `Long.compare`, rồi xác định trường hợp nào trả về `0` sai.

Tiêu chí hoàn thành là mô tả được nguyên nhân lỗi, chỉ ra dữ liệu tái hiện và sửa bằng một quy tắc giữ đúng dấu trên miền giá trị cần hỗ trợ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-007">Tính ổn định của sắp xếp List và cách xử lý phần tử đồng hạng</a>

<details>
<summary>Xem chi tiết</summary>

# Tính ổn định của sắp xếp List và cách xử lý phần tử đồng hạng

Khi sắp xếp một danh sách theo điểm, mức ưu tiên hoặc ngày tạo, nhiều phần tử có thể có cùng khóa. Lúc đó có hai câu hỏi riêng:

1. Các nhóm khóa khác nhau phải đứng theo thứ tự nào?
2. Bên trong một nhóm đồng hạng, phần tử nào đứng trước?

**Sắp xếp ổn định (stable sort)** giải quyết câu hỏi thứ hai bằng cách giữ thứ tự tương đối mà những phần tử đồng hạng đã có trước lần sắp xếp.

Tính ổn định có ích khi thứ tự đầu vào mang ý nghĩa, chẳng hạn thứ tự tiếp nhận hồ sơ. Nhưng nó không tự biến một nguồn không có thứ tự xác định thành kết quả hoàn toàn xác định, và cũng không tự tạo tiêu chí phụ còn thiếu của nghiệp vụ.

## 1. Đồng hạng theo quy tắc so sánh

Giả sử danh sách gồm các bản ghi:

| Vị trí ban đầu | Mã | Hạng dùng để sắp xếp |
| --- | --- | --- |
| 0 | C | 2 |
| 1 | D | 1 |
| 2 | A | 2 |
| 3 | B | 1 |
| 4 | E | 2 |

Quy tắc là hạng nhỏ hơn đứng trước:

~~~java
// Entry là kiểu có phương thức rank() trả int.
Comparator<Entry> byRank = Comparator.comparingInt(Entry::rank);
~~~

D và B đồng hạng vì:

~~~java
byRank.compare(d, b) == 0
~~~

C, A và E cũng đồng hạng với nhau theo comparator này.

Các bản ghi có thể khác mã, khác đối tượng và không `equals` nhau. “Đồng hạng” ở đây chỉ có nghĩa comparator trả `0` theo tiêu chí đang dùng.

Sau một lần sắp xếp ổn định tăng dần theo hạng:

~~~text
Trước: C:2, D:1, A:2, B:1, E:2
Sau:   D:1, B:1, C:2, A:2, E:2
~~~

Hai nhóm được sắp lại theo hạng. Bên trong nhóm `1`, D vẫn trước B. Bên trong nhóm `2`, C vẫn trước A, A vẫn trước E.

Không có điều kiện mã phải tăng theo bảng chữ cái. Nếu yêu cầu mã tăng dần khi đồng hạng, đó là một tiêu chí bổ sung, cần được biểu đạt riêng.

## 2. Định nghĩa chính xác của tính ổn định

Gọi các phần tử trước sort là `x0, x1, ..., xn-1`. Với comparator `c`, nếu:

~~~text
i < j
và c.compare(xi, xj) == 0
~~~

thì một phép sort ổn định giữ lần xuất hiện `xi` trước lần xuất hiện `xj` trong kết quả.

Định nghĩa nói về **thứ tự tương đối**:

- Hai phần tử có thể chuyển sang chỉ số mới.
- Giữa chúng có thể thay đổi khoảng cách.
- Nhưng phần tử đứng trước trong cặp đồng hạng vẫn đứng trước sau sort.

Với dữ liệu trên, D chuyển từ chỉ số `1` sang `0`; B từ `3` sang `1`. Thứ tự tương đối D trước B được giữ, dù chỉ số đều thay đổi.

Tính ổn định cũng áp dụng cho các lần xuất hiện trong danh sách có phần tử lặp. Trong thực tế, cần dữ liệu có nhãn hoặc định danh riêng để quan sát rõ hai phần tử cùng khóa; nếu chỉ in `[1, 1, 2]`, sẽ khó thấy liệu hai số `1` đã đổi chỗ nội bộ hay chưa.

### Không nhầm “stable” với các thuộc tính khác

| Thuộc tính | Câu hỏi nó trả lời |
| --- | --- |
| Sắp xếp đúng theo comparator | Kết quả có tuân theo quan hệ thứ tự không? |
| Sắp xếp ổn định | Các phần tử so sánh bằng nhau có giữ thứ tự tương đối trước sort không? |
| Không thay đổi nguồn | Thao tác có sửa danh sách đầu vào không? |
| Kết quả xác định theo dữ liệu | Cùng tập dữ liệu có đủ quy tắc để chọn một thứ tự cụ thể không? |
| An toàn khi nhiều luồng cùng truy cập | Có quy tắc đồng bộ và cấu trúc phù hợp để tránh xung đột không? |

Một phép sort có thể vừa ổn định vừa thay đổi danh sách tại chỗ. Tính ổn định cũng không thay thế việc đồng bộ giữa các luồng.

## 3. Cam kết của API sắp xếp List

`List.sort(comparator)` có cam kết ổn định. Một triển khai đúng hợp đồng không được đổi thứ tự tương đối của các phần tử mà comparator xem là bằng nhau. Đây là yêu cầu API, không chỉ là một kết quả tình cờ của một thuật toán hiện tại. Xem [List.sort trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

~~~java
// values phải là danh sách hỗ trợ thay đổi thứ tự phần tử.
values.sort(byRank);
~~~

`Collections.sort(list, comparator)` cũng cam kết ổn định; biến thể dùng thứ tự tự nhiên có cam kết tương ứng. Xem [Collections.sort trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#sort(java.util.List,java.util.Comparator)).

~~~java
Collections.sort(values, byRank);
~~~

Hai cách gọi không có nghĩa giữ nguyên nguồn. Nếu cần giữ nguồn, tạo một danh sách mới:

~~~java
List<Entry> sorted = new ArrayList<>(source);
sorted.sort(byRank);
~~~

Cách này tạo một danh sách chứa các tham chiếu phần tử từ nguồn; không tự sao chép sâu từng đối tượng.

### Danh sách có hỗ trợ sort không?

Một danh sách không cho sửa đổi như kết quả `List.of(...)` không trở thành có thể sửa chỉ vì thao tác sort được cam kết ổn định.

~~~java
List<Integer> fixed = List.of(3, 1, 2);

// fixed.sort(Comparator.naturalOrder());
// Nếu chạy, lời gọi thay đổi này ném UnsupportedOperationException.
~~~

Không nên đồng nhất “kích thước cố định” với “không cho thay phần tử”. Ví dụ `Arrays.asList(...)` tạo danh sách có kích thước cố định nhưng hỗ trợ thay thế và sắp xếp; sự thay đổi phản ánh về mảng phía sau. Tính ổn định và khả năng chỉnh sửa là hai vấn đề độc lập cần đọc từ hợp đồng tương ứng.

## 4. compare bằng 0 không phải equals

Xét kiểu:

~~~java
// Khai báo kiểu dùng trong bài.
record Entry(String id, int rank) {}
~~~

Hai đối tượng:

~~~java
Entry first = new Entry("C", 2);
Entry second = new Entry("A", 2);
~~~

Record `equals` xét cả mã và hạng, nên hai đối tượng khác nhau. Comparator chỉ xét hạng vẫn trả `0`.

Khi sort ổn định theo hạng, C và A được xem là đồng hạng dù không `equals`. C đứng trước A nếu trước sort C đã đứng trước A.

### Ví dụ với BigDecimal

`BigDecimal` cho thấy sự phân biệt này với một kiểu có sẵn:

~~~java
BigDecimal first = new BigDecimal("1.0");
BigDecimal second = new BigDecimal("1.00");

System.out.println(first.equals(second));    // false
System.out.println(first.compareTo(second)); // 0
~~~

Hai giá trị có cùng độ lớn số học nhưng khác scale. Nếu sắp danh sách theo thứ tự tự nhiên, tính ổn định xét kết quả `compareTo == 0`, không xét `equals`.

Trong chương trình hoàn chỉnh ở dưới, thứ tự đầu vào của `1.0` và `1.00` được giữ; tương tự với `2.00` và `2.0`.

Không cần biến comparator thành `equals` mới sử dụng được cho sort danh sách. Tuy nhiên, nếu dùng cùng comparator cho `TreeSet` hoặc `TreeMap`, kết quả `0` còn ảnh hưởng tới ý nghĩa trùng phần tử hoặc tương đương khóa; đó là trách nhiệm phải xem xét riêng.

## 5. Stable sort giữ thứ tự nào?

Nó giữ thứ tự **ngay trước lời gọi sort đó**, không phải một lịch sử gốc mà chương trình không lưu.

Ví dụ:

1. Nhận dữ liệu theo thứ tự C, D, A, B, E.
2. Một thao tác khác sắp hoặc đảo danh sách.
3. Sau đó mới sort theo hạng.

Tính ổn định tại bước 3 bảo toàn thứ tự đồng hạng của danh sách sau bước 2.

Nếu nghiệp vụ muốn “đồng hạng thì ai đến trước đứng trước”, phải bảo đảm có thông tin đáng tin cậy về thứ tự tiếp nhận:

- Duy trì thứ tự tiếp nhận trong nguồn đầu vào của lần sort.
- Hoặc lưu một trường số thứ tự tiếp nhận, rồi dùng trường đó làm tiêu chí phụ.
- Nếu dùng thời điểm, xác định cách xử lý nhiều bản ghi cùng thời điểm.

Không tự coi mã định danh hoặc vị trí hiện tại là thời điểm tiếp nhận nếu mô hình dữ liệu không cam kết điều đó.

### Nguồn không cam kết thứ tự

Nếu tạo danh sách từ `HashSet` hoặc một truy vấn không có quy tắc thứ tự rõ ràng, đầu vào có thể có thứ tự khác giữa các lần xử lý.

Stable sort theo hạng sẽ giữ thứ tự đồng hạng mà nó nhận được trong từng lần. Nó không thể phục hồi lịch sử chèn đã mất hoặc tự đoán quy tắc sắp xếp mong muốn.

Không gọi sự thay đổi đó là “List.sort không ổn định” khi mỗi lần đầu vào khác nhau. Để kiểm tra tính ổn định, phải so với đúng dãy trước lần sort đang xét.

## 6. Chọn chính sách xử lý đồng hạng

Có ít nhất ba chính sách hợp lý, tùy yêu cầu:

| Chính sách | Khi phù hợp | Cách biểu đạt |
| --- | --- | --- |
| Giữ thứ tự hiện tại trong nhóm đồng hạng | Nguồn đã có trình tự có ý nghĩa và cần bảo toàn | Dùng comparator tiêu chí chính cùng stable sort. |
| Dùng tiêu chí phụ có ý nghĩa | Cần quy tắc rõ, độc lập với thứ tự đầu vào trong các nhóm hòa | Ghép `thenComparing` theo đúng thứ bậc. |
| Giữ cùng hạng nhưng tách quy tắc hiển thị | Các phần tử cần được xem là ngang hạng về nghiệp vụ nhưng vẫn cần trình tự trình bày | Dùng khóa phụ cho hiển thị, vẫn tính đồng hạng từ tiêu chí nghiệp vụ chính. |

### Giữ thứ tự hiện tại

~~~java
List<Entry> result = new ArrayList<>(source);
result.sort(Comparator.comparingInt(Entry::rank));
~~~

Không thêm tiêu chí mã nếu thứ tự trước sort là điều cần bảo toàn trong nhóm đồng hạng. Thêm mã sẽ thay đổi chính sách.

### Sắp mã tăng dần trong cùng hạng

~~~java
Comparator<Entry> displayOrder =
        Comparator.comparingInt(Entry::rank)
                .thenComparing(Entry::id);
~~~

Với nguồn C:2, D:1, A:2, B:1, E:2, kết quả trở thành:

~~~text
B:1, D:1, A:2, C:2, E:2
~~~

C và A không còn đồng hạng **theo comparator đầy đủ**, vì mã đã phân biệt chúng. Do đó việc A đứng trước C không vi phạm tính ổn định của sort.

Tính ổn định được áp dụng sau khi đã xét toàn bộ comparator, không chỉ khóa đầu tiên mà người viết chương trình đang chú ý.

### Cùng hạng nghiệp vụ không nhất thiết có cùng vị trí hiển thị

Hai thí sinh cùng điểm có thể phải cùng nhận hạng `1`, dù màn hình sắp tên tăng dần để dễ đọc. Nếu tính đồng hạng bằng comparator đã thêm tên hoặc ID, có thể vô tình biến hai người cùng điểm thành hai hạng khác nhau.

Hãy tách:

- Quy tắc xác định ngang hạng nghiệp vụ.
- Quy tắc chọn trình tự hiển thị.
- Quy tắc gán số hạng, nếu cần.

Sort không tự tính bảng xếp hạng kiểu `1, 1, 3` hoặc `1, 1, 2`. Những quy tắc đó là một bước xử lý khác.

## 7. Thứ tự giảm dần và bẫy reverse sau sort

Muốn hạng lớn hơn đứng trước nhưng giữ thứ tự tương đối trong nhóm đồng hạng:

~~~java
List<Entry> descending = new ArrayList<>(source);
descending.sort(byRank.reversed());
~~~

Từ dữ liệu mẫu, kết quả:

~~~text
C:2, A:2, E:2, D:1, B:1
~~~

Trong nhóm hạng `2`, C vẫn trước A trước E. Trong nhóm hạng `1`, D vẫn trước B.

Một cách tưởng tương đương là sort tăng rồi đảo cả danh sách:

~~~java
List<Entry> reversed = new ArrayList<>(source);
reversed.sort(byRank);
Collections.reverse(reversed);
~~~

Kết quả:

~~~text
E:2, A:2, C:2, B:1, D:1
~~~

`Collections.reverse` đảo cả trình tự của từng nhóm đồng hạng. Phép sort ban đầu vẫn ổn định; một thao tác khác sau đó đã đổi thứ tự tương đối.

Vì vậy:

~~~text
sort bằng comparator đảo chiều
~~~

không tương đương về chính sách đồng hạng với:

~~~text
sort bằng comparator ban đầu rồi reverse toàn bộ kết quả
~~~

Cần xác định muốn đổi chiều các nhóm hay muốn đảo mọi phần tử.

Khi có nhiều tiêu chí, vị trí `reversed()` cũng quan trọng. Đặt sau toàn bộ chuỗi comparator sẽ đảo cả tiêu chí chính lẫn tiêu chí phụ; đặt ngay sau comparator chính chỉ đảo phần chính đã tạo trước đó.

## 8. Sắp xếp nhiều lượt dựa trên tính ổn định

Giả sử muốn hạng tăng dần, trong cùng hạng thì mã tăng dần.

Một comparator biểu đạt trực tiếp:

~~~java
Comparator<Entry> order =
        Comparator.comparingInt(Entry::rank)
                .thenComparing(Entry::id);

values.sort(order);
~~~

Cũng có thể dùng nhiều lượt sort ổn định, từ tiêu chí ít quan trọng hơn đến tiêu chí quan trọng hơn:

~~~java
values.sort(Comparator.comparing(Entry::id));  // Tiêu chí phụ trước.
values.sort(Comparator.comparingInt(Entry::rank)); // Tiêu chí chính sau.
~~~

Lượt cuối tạo nhóm theo hạng. Do ổn định, trong nhóm cùng hạng nó giữ thứ tự mã đã hình thành ở lượt trước.

Không đổi hai dòng tùy ý:

~~~java
values.sort(Comparator.comparingInt(Entry::rank));
values.sort(Comparator.comparing(Entry::id));
~~~

Lần cuối lúc này ưu tiên mã trên toàn bộ danh sách. Nó không tự hiểu “chỉ sắp mã trong các nhóm đồng hạng”.

Kỹ thuật nhiều lượt dựa vào:

- Tính ổn định của các phép sort dùng để bảo toàn thứ tự phụ.
- Thứ tự áp dụng từ khóa ít quan trọng đến quan trọng.
- Dữ liệu và comparator không đổi ý nghĩa giữa các lượt.

Một chuỗi `thenComparing` thường giúp người đọc thấy thứ bậc rõ hơn. Biết kỹ thuật nhiều lượt vẫn hữu ích để hiểu stable sort thực sự bảo toàn thông tin nào.

## 9. Cần thứ tự gốc thì phải lưu trước khi làm mất nó

Trong một số luồng xử lý, danh sách đã trải qua nhiều lần biến đổi nhưng vẫn cần quay về chính sách “cùng hạng thì theo vị trí lúc tiếp nhận”.

Có thể gắn vị trí vào từng lần xuất hiện trước khi thay đổi thứ tự:

~~~java
// Minh họa các khai báo trong một lớp; Entry đã được định nghĩa.
record IndexedEntry(Entry value, int originalPosition) {}

List<IndexedEntry> tagged = new ArrayList<>();
for (int i = 0; i < source.size(); i++) {
    tagged.add(new IndexedEntry(source.get(i), i));
}

Comparator<IndexedEntry> order =
        Comparator.comparingInt(
                (IndexedEntry entry) -> entry.value().rank()
        ).thenComparingInt(IndexedEntry::originalPosition);

tagged.sort(order);
~~~

`originalPosition` chỉ có ý nghĩa đối với dãy `source` đã chọn ở thời điểm gắn nhãn. Nó không tự trở thành ID của thực thể hoặc số thứ tự toàn hệ thống.

Cách làm còn phân biệt được các lần xuất hiện khi cùng một đối tượng được đưa vào nguồn nhiều lần. Nếu chỉ gắn vị trí bằng một `Map<Entry, Integer>`, các phần tử `equals` nhau hoặc cùng đối tượng có thể bị gộp thông tin vị trí.

Nếu miền nghiệp vụ đã có số thứ tự tiếp nhận ổn định, nên dùng trường nghiệp vụ đó thay vì tạo lại từ một nguồn đã bị xáo trộn.

## 10. Đồng hạng tại ranh giới lấy N phần tử

Một quy tắc “lấy N phần tử đầu sau sort” chưa nói phải làm gì khi ranh giới cắt ngang nhóm đồng hạng.

Ví dụ đã sort theo hạng:

~~~text
D:1, B:1, C:2, A:2, E:2
~~~

Nếu lấy đúng `1` phần tử:

- Chính sách giới hạn đúng số lượng trả D.
- Chính sách lấy cả nhóm đồng hạng với phần tử cuối được chọn trả D và B.

Cả hai có thể hợp lý, nhưng phải được chỉ rõ.

Nếu dùng mã làm tiêu chí phụ để hiển thị mà vẫn muốn bao gồm mọi phần tử đồng hạng nghiệp vụ, cần xác định nhóm ở ranh giới theo comparator hạng chính, không theo comparator hiển thị đầy đủ. Nếu comparator đầy đủ phân biệt mọi ID, sẽ không còn nhóm hòa theo nó.

Với chính sách gồm cả nhóm ranh giới:

1. Sort theo quy tắc hiển thị được chọn.
2. Chọn ranh giới của số lượng ban đầu, nếu có dữ liệu.
3. Tiếp tục lấy các phần tử cùng hạng nghiệp vụ với phần tử ranh giới.
4. Xử lý rõ trường hợp N bằng `0`, N lớn hơn kích thước và đầu vào rỗng.

Tính ổn định hỗ trợ giữ một thứ tự trong nhóm; nó không tự lựa chọn chính sách phân trang, giới hạn số lượng hay trao hạng.

## 11. Chương trình hoàn chỉnh

Lưu thành **StableListSortDemo.java**. Ví dụ dùng các bản ghi có mã khác nhau để quan sát được thứ tự nội bộ của từng nhóm.

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StableListSortDemo {
    record Entry(String id, int rank) {}

    public static void main(String[] args) {
        List<Entry> source = List.of(
                new Entry("C", 2),
                new Entry("D", 1),
                new Entry("A", 2),
                new Entry("B", 1),
                new Entry("E", 2)
        );

        Comparator<Entry> byRank = Comparator.comparingInt(Entry::rank);

        List<Entry> ascending = new ArrayList<>(source);
        ascending.sort(byRank);
        System.out.println("Stable ascending = " + ids(ascending));

        List<Entry> usingCollections = new ArrayList<>(source);
        Collections.sort(usingCollections, byRank);
        System.out.println("Collections.sort = " + ids(usingCollections));

        List<Entry> descending = new ArrayList<>(source);
        descending.sort(byRank.reversed());
        System.out.println("Stable descending = " + ids(descending));

        List<Entry> reversedAfterSort = new ArrayList<>(ascending);
        Collections.reverse(reversedAfterSort);
        System.out.println("Ascending then reverse = "
                + ids(reversedAfterSort));

        List<Entry> explicitTieBreak = new ArrayList<>(source);
        explicitTieBreak.sort(byRank.thenComparing(Entry::id));
        System.out.println("Rank then id = " + ids(explicitTieBreak));

        List<Entry> multiPass = new ArrayList<>(source);
        multiPass.sort(Comparator.comparing(Entry::id));
        multiPass.sort(byRank);
        System.out.println("Id first, then stable rank = " + ids(multiPass));

        List<Entry> anotherInput = List.of(
                new Entry("B", 1),
                new Entry("D", 1),
                new Entry("E", 2),
                new Entry("A", 2),
                new Entry("C", 2)
        );
        List<Entry> anotherSorted = new ArrayList<>(anotherInput);
        anotherSorted.sort(byRank);
        System.out.println("Different input, rank only = "
                + ids(anotherSorted));
        anotherSorted.sort(byRank.thenComparing(Entry::id));
        System.out.println("Different input, rank then id = "
                + ids(anotherSorted));

        List<BigDecimal> amounts = new ArrayList<>(List.of(
                new BigDecimal("2.00"),
                new BigDecimal("1.0"),
                new BigDecimal("2.0"),
                new BigDecimal("1.00")
        ));
        amounts.sort(Comparator.naturalOrder());
        System.out.println("BigDecimal stable natural order = " + amounts);
        System.out.println("1.0 equals 1.00 = "
                + new BigDecimal("1.0").equals(new BigDecimal("1.00")));
        System.out.println("1.0 compareTo 1.00 = "
                + new BigDecimal("1.0").compareTo(new BigDecimal("1.00")));

        System.out.println("Original = " + ids(source));
    }

    private static List<String> ids(List<Entry> values) {
        List<String> result = new ArrayList<>();
        for (Entry value : values) {
            result.add(value.id());
        }
        return result;
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 StableListSortDemo.java
java StableListSortDemo
~~~

Stdout:

~~~text
Stable ascending = [D, B, C, A, E]
Collections.sort = [D, B, C, A, E]
Stable descending = [C, A, E, D, B]
Ascending then reverse = [E, A, C, B, D]
Rank then id = [B, D, A, C, E]
Id first, then stable rank = [B, D, A, C, E]
Different input, rank only = [B, D, E, A, C]
Different input, rank then id = [B, D, A, C, E]
BigDecimal stable natural order = [1.0, 1.00, 2.00, 2.0]
1.0 equals 1.00 = false
1.0 compareTo 1.00 = 0
Original = [C, D, A, B, E]
~~~

### Giải thích kết quả

**Stable ascending** và **Collections.sort**

Cùng tạo dãy D, B, C, A, E. Đây là hai nhóm hạng tăng dần, với thứ tự tương đối gốc được giữ trong mỗi nhóm.

**Stable descending**

C, A, E đứng trước D, B. Comparator đảo chiều nhóm nhưng các phần tử cùng hạng vẫn hòa nhau theo comparator đảo chiều, nên thứ tự trong nhóm được giữ.

**Ascending then reverse**

Kết quả đảo thành E, A, C, B, D. Thao tác đảo diễn ra sau sort và đảo luôn thứ tự nội bộ các nhóm.

**Rank then id**

Mã được dùng để giải quyết hòa, nên nhóm hạng `1` là B, D; nhóm hạng `2` là A, C, E. Đây là một yêu cầu khác với chỉ giữ trình tự đầu vào.

**Id first, then stable rank**

Sort mã trước rồi sort ổn định theo hạng cho cùng kết quả như comparator ghép. Thông tin thứ tự mã được giữ trong các nhóm của lần sort cuối.

**Different input, rank only**

Nguồn khác đã đặt B trước D và E trước A trước C. Stable sort đúng phải bảo toàn các trình tự đó trong nhóm, nên khác kết quả sort cùng tập bản ghi từ nguồn ban đầu.

**Different input, rank then id**

Khi thêm mã làm tiêu chí phụ, hai nguồn khác thứ tự nhưng chứa cùng các bản ghi cho cùng kết quả B, D, A, C, E trong ví dụ này.

**BigDecimal**

`1.0` trước `1.00`; `2.00` trước `2.0`, đúng theo thứ tự trước sort của các nhóm `compareTo == 0`. Dòng `equals` cho thấy hai số cùng độ lớn không nhất thiết `equals` nhau khi scale khác.

**Original**

Các phép sort dùng các bản sao có thể chỉnh sửa. Dữ liệu nguồn vẫn giữ C, D, A, B, E. Điều này đến từ cách tạo bản sao, không đến từ tính ổn định.

## 12. Kiểm thử tính ổn định đúng cách

Kiểm thử chỉ có các khóa khác nhau không thể phát hiện việc các phần tử đồng hạng bị đổi chỗ. Cần chủ động tạo dữ liệu có:

- Nhiều nhóm đồng hạng.
- Các phần tử cùng nhóm nằm xen kẽ với nhóm khác.
- Nhãn riêng để theo dõi từng lần xuất hiện.
- Trình tự nhãn không tự trùng với thứ tự mã tăng dần.

Dữ liệu C:2, D:1, A:2, B:1, E:2 trong bài được chọn để thấy rõ điều đó. Nếu dùng A:1, B:1, C:2, D:2 thì việc vô tình thêm tiêu chí mã có thể vẫn cho cùng kết quả, khiến kiểm thử bỏ sót lỗi chính sách.

Một kiểm tra đầy đủ cần tách ít nhất ba thuộc tính:

1. **Nội dung được bảo toàn:** không mất, không thêm hoặc lặp sai số lần phần tử.
2. **Thứ tự chính đúng:** hai phần tử kề nhau tuân theo comparator.
3. **Tính ổn định đúng:** với các phần tử so sánh bằng nhau, thứ tự tương đối trước sort được giữ.

Với các phần tử mang vị trí gốc riêng, sau sort có thể gom các phần tử bằng nhau theo comparator và kiểm tra vị trí gốc tăng dần trong từng nhóm.

Không viết kiểm thử ổn định bằng cách luôn yêu cầu ID tăng dần nếu comparator không xét ID. Đó là kiểm thử một quy tắc nghiệp vụ khác.

Cũng cần dữ liệu biên: đầu vào rỗng, một phần tử, tất cả đồng hạng, không có phần tử đồng hạng, và phần tử `null` nếu comparator lẫn danh sách cho phép. Comparator `nullsFirst` hoặc `nullsLast` quyết định vị trí `null`; sort ổn định không tự thêm khả năng xử lý `null` cho một comparator đang cấm nó.

## 13. Giới hạn và những suy luận không nên có

### Comparator sai thì stable sort không sửa được

Nếu comparator trừ hai số bị tràn, đổi kết quả theo thời gian hoặc không có tính bắc cầu, kết quả không có cơ sở để đáp ứng ý nghĩa mong muốn.

Một thuật toán có thể phát hiện một số vi phạm và ném ngoại lệ, nhưng việc không có ngoại lệ không chứng minh comparator hợp lệ. Khóa so sánh nên ổn định trong thời gian sort và quy tắc phải nhất quán.

### Ổn định không có nghĩa giữ nguyên chỉ số

Chỉ có thứ tự tương đối của cặp đồng hạng được giữ. Chỉ số thay đổi là hành vi bình thường của sắp xếp; không lưu chỉ số như định danh lâu dài của một thực thể.

### Ổn định không phải thuộc tính chung của mọi cấu trúc có thứ tự

Không từ cam kết của `List.sort` suy ra mọi thuật toán hoặc cấu trúc khác đều xử lý hòa theo cách tương tự. Chẳng hạn, không dựa vào `PriorityQueue` để tự giữ FIFO giữa mọi phần tử cùng mức ưu tiên; nếu cần FIFO trong nhóm, phải có chính sách và tiêu chí thích hợp.

### Ổn định không duy trì mãi trạng thái đã sort

Sau sort, thêm phần tử mới vào cuối hoặc thay đổi khóa trong đối tượng có thể khiến dãy không còn đúng thứ tự. `List` không tự sắp xếp lại mỗi khi dữ liệu thay đổi.

### Ổn định không khắc phục dữ liệu nguồn thay đổi

Nếu các bản ghi hoặc tập dữ liệu thay đổi giữa các lần đọc, kết quả có thể thay đổi dù comparator đầy đủ và phép sort đều đúng. Khi cần một báo cáo tái lập, phải xác định cả ảnh chụp dữ liệu và phiên bản quy tắc, không chỉ thêm một lời gọi sort.

## 14. Lỗi thường gặp

| Hiểu nhầm | Cách hiểu cần giữ |
| --- | --- |
| Stable sort không thay đổi vị trí phần tử | Nó giữ thứ tự tương đối trong nhóm đồng hạng; chỉ số vẫn có thể đổi. |
| Đồng hạng nghĩa là `equals` nhau | Với sort, đồng hạng được quyết định bởi comparator hoặc thứ tự tự nhiên. |
| Stable sort luôn tạo kết quả giống nhau từ cùng một tập dữ liệu | Nếu nguồn có trình tự khác nhau và còn nhóm hòa, kết quả trong nhóm có thể khác. |
| Thêm ID vẫn phải giữ thứ tự cũ của mọi phần tử cùng điểm | Sau khi thêm ID, các phần tử khác ID có thể không còn hòa theo comparator đầy đủ. |
| Sort tăng rồi reverse tương đương sort giảm ổn định | Reverse đảo cả thứ tự nội bộ các nhóm. |
| Sort chính rồi sort phụ là sắp nhiều tiêu chí | Lần sort cuối tác động toàn bộ dãy; muốn dùng nhiều lượt ổn định phải áp dụng từ phụ đến chính. |
| Dùng TreeSet thay cho List đã sort sẽ giữ mọi phần tử | Comparator trả `0` trong TreeSet có thể làm những phần tử bị xem là cùng một vị trí. |
| `limit(N)` hoặc lấy N phần tử sẽ tự bao gồm mọi người cùng hạng | Chính sách ở ranh giới phải được thiết kế riêng. |

## 15. Thực hành

1. Tự viết kết quả tăng và giảm ổn định cho dãy `X:3, A:1, B:3, C:1, D:3`.
2. Thêm tiêu chí mã tăng dần. Chỉ rõ các cặp trước đây đồng hạng nhưng nay không còn hòa theo comparator đầy đủ.
3. So sánh sort giảm trực tiếp với sort tăng rồi reverse; khoanh những cặp cùng hạng bị đảo.
4. Tạo hai nguồn có cùng tập bản ghi nhưng khác trình tự trong nhóm hạng. Kiểm chứng stable sort theo hạng cho các kết quả khác nhau nhưng đều đúng.
5. Chọn chính sách “hạng bằng nhau thì theo thứ tự tiếp nhận”. Thiết kế nguồn dữ liệu hoặc trường số thứ tự để giữ được ý nghĩa đó qua nhiều bước xử lý.
6. Với yêu cầu lấy ba phần tử đầu, tạo dữ liệu có nhóm đồng hạng cắt ngang vị trí thứ ba. Viết rõ hai kết quả theo chính sách giới hạn đúng số lượng và chính sách bao gồm cả nhóm.
7. Viết kiểm tra tính ổn định dùng vị trí gốc riêng của từng lần xuất hiện, rồi thử dữ liệu có các đối tượng `equals` nhau hoặc cùng đối tượng xuất hiện nhiều lần.

Trước mỗi yêu cầu sắp xếp có đồng hạng, hãy xác định thế nào là hòa, cần giữ trình tự nào trong nhóm và có cần một quy tắc hiển thị phụ hay không. Tính ổn định của `List.sort` là công cụ để thực hiện chính sách đã chọn, không thay thế việc chọn chính sách.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparison-sorting-008">Dùng cùng quy tắc so sánh cho sort, binarySearch, TreeSet và TreeMap</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng cùng quy tắc so sánh cho sort, binarySearch, TreeSet và TreeMap

Một comparator không chỉ quyết định thứ tự hiển thị. Nó còn trả lời hai câu hỏi quan trọng:

1. **Giá trị nào đứng trước hoặc sau giá trị nào?**
2. **Những giá trị nào được xem là cùng hạng khi kết quả compare bằng 0?**

Khi một tập dữ liệu được sắp xếp, tìm kiếm và đưa vào cấu trúc cây có thứ tự, các nơi đó phải hiểu các câu trả lời theo đúng quy tắc đã chọn. Nếu mỗi nơi dùng một quy tắc khác mà không có chủ đích, kết quả có thể trông mâu thuẫn: tìm không thấy dữ liệu, hai bản ghi bị gộp, hoặc một lần put lại thay giá trị cũ thay vì tăng kích thước.

Cách làm dễ kiểm soát là định nghĩa một quy tắc có tên rồi truyền rõ ràng cho các thao tác đang biểu diễn cùng một chỉ mục dữ liệu.

## 1. Vai trò của comparator trong bốn nơi sử dụng

| Nơi sử dụng | Comparator được dùng để làm gì? | Khi compare bằng 0 |
| --- | --- | --- |
| `list.sort(rule)` | Tổ chức lại thứ tự phần tử trong danh sách | Các phần tử hòa nhau vẫn được giữ |
| `Collections.binarySearch(list, key, rule)` | Chọn nửa cần tìm và nhận biết một kết quả phù hợp | Có thể trả một vị trí của phần tử cùng hạng với key |
| `new TreeSet<>(rule)` | Tổ chức cây, tìm, thêm, xóa phần tử | Được xem là cùng phần tử theo thứ tự của cây |
| `new TreeMap<>(rule)` | Tổ chức, tìm và cập nhật theo khóa | Hai khóa được xem là cùng khóa theo thứ tự của cây |

Điểm chung là quan hệ so sánh. Khác biệt là **mỗi cấu trúc làm gì với những giá trị cùng hạng**.

Comparator có thể có nhiều tiêu chí, chiều giảm dần hoặc chính sách null. Các chi tiết đó đều thuộc quy tắc, không phải phần trang trí chỉ dành cho lúc sort.

## 2. Trước hết, đặt tên cho toàn bộ quy tắc

Giả sử có mặt hàng gồm mã và giá:

```java
record Product(String sku, int price) {}
```

Yêu cầu của chỉ mục:

- Giá tăng dần.
- Khi bằng giá, mã tăng dần theo thứ tự của `String`.
- Mã không null trong miền dữ liệu của ví dụ.

Khai báo:

```java
private static final Comparator<Product> ORDER =
        Comparator.comparingInt(Product::price)
                .thenComparing(Product::sku);
```

Đây là phần khai báo trong lớp, không phải chương trình độc lập.

Tên `ORDER` đại diện cho **cả hai tiêu chí**, không chỉ cho giá. Nếu đoạn tìm kiếm vô tình bỏ `thenComparing`, nó đã đổi ý nghĩa của kết quả bằng 0: từ cùng giá và cùng mã thành chỉ cần cùng giá.

Dùng `comparingInt` thay cho phép trừ giá giúp tránh tự đưa lỗi tràn số vào comparator.

### Cùng quy tắc không bắt buộc cùng tham chiếu

Hai đối tượng comparator được tạo riêng nhưng đưa ra cùng quan hệ thứ tự trên miền dữ liệu có thể dùng tương đương về hành vi.

Không cần kiểm tra `sortRule == searchRule`. Cũng không thể dựa hoàn toàn vào `sortRule.equals(searchRule)` để xác nhận hai biểu thức lambda mô tả cùng quy tắc.

Dùng chung một hằng có tên giúp giảm nguy cơ lệch cấu hình và giúp người đọc nhìn thấy ý định. Điều quan trọng sau cùng vẫn là **quan hệ thứ tự thực sự**, không phải địa chỉ đối tượng comparator.

## 3. sort tạo ra tiền điều kiện cho binarySearch

### 3.1. Danh sách phải được sắp theo comparator tìm kiếm

Tìm kiếm nhị phân dựa vào khả năng loại bỏ một nửa dữ liệu: khi so phần tử giữa với khóa cần tìm, nó phải tin rằng các giá trị ở mỗi phía tuân theo thứ tự.

Vì vậy, trước khi gọi:

```java
Collections.binarySearch(products, query, ORDER);
```

danh sách phải đang được sắp theo `ORDER`.

Cách dùng rõ ràng:

```java
products.sort(ORDER);
int index = Collections.binarySearch(products, query, ORDER);
```

Các dòng trên là minh họa bên trong phương thức có các biến phù hợp.

Không được sắp theo giá tăng rồi tìm bằng giá giảm, hoặc sắp theo mã nhưng tìm theo giá, nếu danh sách không đồng thời đáp ứng thứ tự của comparator tìm kiếm.

Khi tiền điều kiện không thỏa, kết quả không được bảo đảm. API không hứa luôn ném ngoại lệ để báo rằng bạn đã dùng sai comparator. Xem [hợp đồng Collections.binarySearch với Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#binarySearch(java.util.List,T,java.util.Comparator)).

### 3.2. Điều kiện chính xác là “đang có thứ tự phù hợp”

Không phải cứ hai comparator khác nhau là tìm kiếm chắc chắn sai.

Ví dụ, danh sách sắp theo **giá rồi mã** cũng được sắp theo **giá** nếu comparator giá dùng cùng chiều. Tìm kiếm chỉ theo giá có thể hợp lệ về tiền điều kiện.

Nhưng khi đó câu hỏi đã đổi:

- Tìm bằng giá rồi mã: cần một mặt hàng có cả giá và mã cùng hạng.
- Tìm chỉ bằng giá: cần một mặt hàng bất kỳ thuộc hạng giá đó.

Nếu mục đích là tìm đúng mã, dùng comparator chỉ theo giá là sai ý định dù thuật toán tìm kiếm vẫn được cung cấp danh sách có thứ tự hợp lệ.

Vì vậy, hãy kiểm tra cả **thứ tự** và **ý nghĩa khớp**, không chỉ kết luận “binarySearch đã trả chỉ số dương nên mọi thứ đều đúng”.

### 3.3. Không bỏ comparator trong lời gọi tiếp theo

Overload không truyền comparator dùng thứ tự tự nhiên. Nó không nhớ comparator đã được dùng trong một lần sort trước.

```java
List<Integer> numbers = new ArrayList<>(List.of(4, 1, 9));
Comparator<Integer> descending = Comparator.reverseOrder();

numbers.sort(descending);
int index = Collections.binarySearch(numbers, 4, descending);
```

Đoạn này minh họa một cặp thao tác cùng quy tắc.

Không thay lời gọi cuối bằng overload tự nhiên rồi dựa vào việc nó có thể vẫn tình cờ tìm ra số 4 trong một dữ liệu nhỏ. Một kết quả tình cờ không xác nhận tiền điều kiện.

Danh sách không mang theo metadata “tôi được sắp bằng comparator nào”. Bên sử dụng phải duy trì thông tin đó.

## 4. Diễn giải kết quả binarySearch đúng cách

### 4.1. Kết quả không âm là một phần tử cùng hạng

Với `result >= 0`, vị trí đó có phần tử thỏa:

```text
rule.compare(list.get(result), key) == 0
```

Điều này không tự bảo đảm `list.get(result).equals(key)`, bởi comparator có thể cố ý bỏ qua một số khác biệt của đối tượng.

Nếu có nhiều phần tử cùng hạng, API không bảo đảm trả phần tử đầu hoặc cuối của nhóm. Muốn lấy toàn bộ nhóm phải có bước tìm biên hoặc thuật toán phù hợp riêng.

### 4.2. Kết quả âm mã hóa điểm chèn

Nếu không tìm được phần tử cùng hạng:

```text
result = -(insertionPoint) - 1
insertionPoint = -result - 1
```

Điểm chèn là vị trí đặt phần tử để giữ thứ tự theo comparator đang dùng. Trong thứ tự giảm dần, “điểm chèn” vẫn được hiểu theo chiều comparator, không theo độ lớn số học mặc định.

Mẫu xử lý tổng quát:

```java
int result = Collections.binarySearch(products, query, ORDER);

if (result >= 0) {
    Product match = products.get(result);
    System.out.println("Found: " + match);
} else {
    int insertionPoint = -result - 1;
    products.add(insertionPoint, query);
}
```

Đây là đoạn minh họa trong một phương thức. Nhánh thêm chỉ phù hợp khi danh sách cho sửa và chính sách nghiệp vụ muốn thêm query khi chưa có phần tử cùng hạng.

Không dùng trực tiếp kết quả âm làm chỉ số. Kết quả âm cũng không phải luôn là -1: -1 chỉ tương ứng điểm chèn 0.

Với mảng đối tượng, cặp `Arrays.sort(array, rule)` và `Arrays.binarySearch(array, key, rule)` có cùng yêu cầu phối hợp thứ tự; xem [API Arrays.binarySearch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#binarySearch(T%5B%5D,T,java.util.Comparator)).

## 5. TreeSet giữ quy tắc và dùng nó trong suốt vòng đời

Khi tạo:

```java
TreeSet<Product> orderedSet = new TreeSet<>(ORDER);
```

cây lưu comparator và dùng cho những thao tác tiếp theo. Không truyền comparator lần nữa ở `contains`, `add` hoặc `remove`.

Một danh sách đã được sắp không tự làm `TreeSet` hiểu quy tắc của danh sách đó:

```java
TreeSet<Product> orderedSet = new TreeSet<>(ORDER);
orderedSet.addAll(products);
```

Đây là cách thể hiện rõ việc dùng chung quy tắc.

### Hòa thứ tự là trùng theo cây

Nếu comparator chỉ dùng giá, hai sản phẩm khác mã nhưng cùng giá so sánh bằng 0. `TreeSet` xem chúng là cùng phần tử theo thứ tự, nên không giữ cả hai như một danh sách.

Thêm tiêu chí mã thay đổi quan hệ hòa: cùng giá nhưng khác mã được phân biệt.

Tuy nhiên, thêm một tiêu chí bất kỳ chưa chắc làm comparator nhất quán với `equals` của kiểu dữ liệu. Cần đối chiếu toàn bộ định nghĩa của kiểu. Với record `Product` trong ví dụ, so sánh đủ giá và mã không null phù hợp với các thành phần mà record dùng cho `equals`.

Nếu thứ tự không nhất quán với `equals`, hành vi nhận diện phần tử của cây có thể khác hợp đồng tập hợp thông thường dựa trên `equals`. Điều này cần được xử lý ở thiết kế, không che giấu bằng một comparator cố tình trả khác 0 cho mọi lần gọi. Xem [API TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

## 6. TreeMap áp dụng quy tắc lên khóa

```java
TreeMap<Product, String> labels = new TreeMap<>(ORDER);
```

Ở đây, `Product` là **khóa**, còn `String` là giá trị.

Nếu đặt một `Product` mới có cùng giá và cùng mã theo `ORDER`, nó thuộc cùng khóa thứ tự với khóa đã có. `put` cập nhật giá trị cho ánh xạ đó thay vì tăng số khóa.

Với comparator chỉ dùng giá, hai mã khác nhau nhưng cùng giá cũng có thể cập nhật cùng một ánh xạ. Nếu mục tiêu là một nhãn cho mỗi mặt hàng, đó là dấu hiệu quy tắc khóa chưa đủ chi tiết.

### Không nhầm comparator của khóa với comparator của giá trị

Nếu đổi mô hình thành:

```java
Map<String, Product> bySku;
```

thì quy tắc của một `TreeMap<String, Product>` sẽ áp dụng lên `String` là khóa, không áp dụng lên `Product` là giá trị.

Sắp một danh sách product theo giá và lưu map theo mã là hai chỉ mục khác nhau có thể hoàn toàn hợp lý. “Dùng cùng quy tắc” chỉ có ý nghĩa khi đang biểu diễn cùng một quan hệ trên cùng miền khóa/phần tử; không phải bắt mọi cấu trúc trong ứng dụng dùng một comparator duy nhất.

Các thao tác truy cập theo khóa và vai trò comparator được mô tả trong [API TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

## 7. Chương trình hoàn chỉnh: một quy tắc dùng xuyên suốt

Lưu vào **SharedOrderingDemo.java**. Giá ở đây là số nguyên dùng cho ví dụ thứ tự; chương trình tương thích Java 17 trở lên.

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class SharedOrderingDemo {
    record Product(String sku, int price) {}

    private static final Comparator<Product> ORDER =
            Comparator.comparingInt(Product::price)
                    .thenComparing(Product::sku);

    private static List<String> describe(Collection<Product> products) {
        return products.stream()
                .map(product -> product.sku() + ":" + product.price())
                .toList();
    }

    public static void main(String[] args) {
        List<Product> products = new ArrayList<>(List.of(
                new Product("D", 300),
                new Product("B", 200),
                new Product("A", 100),
                new Product("C", 200)));

        products.sort(ORDER);
        System.out.println("Sorted: " + describe(products));

        Product query = new Product("C", 200);
        int found = Collections.binarySearch(products, query, ORDER);
        System.out.println("Found index: " + found);

        Product missing = new Product("X", 200);
        int result = Collections.binarySearch(products, missing, ORDER);
        int insertionPoint = -result - 1;
        System.out.println("Missing result: " + result);
        System.out.println("Insertion point: " + insertionPoint);

        TreeSet<Product> orderedSet = new TreeSet<>(ORDER);
        orderedSet.addAll(products);
        System.out.println("TreeSet: " + describe(orderedSet));
        System.out.println("TreeSet contains C: " + orderedSet.contains(query));
        System.out.println("TreeSet contains X: " + orderedSet.contains(missing));

        TreeMap<Product, String> labels = new TreeMap<>(ORDER);
        for (Product product : products) {
            labels.put(product, "label-" + product.sku());
        }
        String previous = labels.put(new Product("C", 200), "updated");
        System.out.println("TreeMap key order: "
                + describe(labels.navigableKeySet()));
        System.out.println("Previous C label: " + previous);
        System.out.println("Current C label: " + labels.get(query));
        System.out.println("TreeMap size: " + labels.size());

        products.add(insertionPoint, missing);
        System.out.println("After ordered insert: " + describe(products));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SharedOrderingDemo.java
java SharedOrderingDemo
```

Stdout:

```text
Sorted: [A:100, B:200, C:200, D:300]
Found index: 2
Missing result: -4
Insertion point: 3
TreeSet: [A:100, B:200, C:200, D:300]
TreeSet contains C: true
TreeSet contains X: false
TreeMap key order: [A:100, B:200, C:200, D:300]
Previous C label: label-C
Current C label: updated
TreeMap size: 4
After ordered insert: [A:100, B:200, C:200, X:200, D:300]
```

### 7.1. Thứ tự và tìm kiếm

Danh sách được sắp theo giá rồi mã:

```text
A:100 -> B:200 -> C:200 -> D:300
```

Query `C:200` thuộc đúng một hạng trong dữ liệu này, nên tìm được chỉ số 2.

Query `X:200` không có. Nó phải đứng sau `C:200` và trước `D:300`. Điểm chèn là 3, mã hóa thành -4.

Mã chỉ giải mã điểm chèn trực tiếp trong chương trình vì query `X:200` được biết trước là thiếu. Với query tổng quát, phải phân nhánh theo dấu của kết quả như phần trước.

### 7.2. Cây tập hợp và cây ánh xạ

`TreeSet` giữ đủ `B:200` và `C:200` vì mã là tiêu chí phụ. Nó tìm thấy `C:200`, nhưng không coi `X:200` là phần tử đã có chỉ vì cùng giá 200.

`TreeMap` duyệt khóa theo cùng thứ tự. `new Product("C", 200)` là một đối tượng mới nhưng so bằng 0 với khóa tương ứng, nên `put` trả nhãn cũ và thay bằng nhãn mới. Kích thước map vẫn là 4.

### 7.3. Thêm vào danh sách không tự cập nhật cây

Dòng cuối chèn query vào danh sách tại điểm chèn đã tính. Nó vẫn giữ thứ tự danh sách.

`TreeSet` và `TreeMap` là các cấu trúc độc lập trong ví dụ. Việc thêm vào `products` không tự thêm vào chúng. “Cùng comparator” có nghĩa cùng quy tắc, không có nghĩa tự đồng bộ nội dung.

Nếu ứng dụng giữ nhiều chỉ mục cho cùng một dữ liệu, cần thiết kế cách cập nhật chúng nhất quán, hoặc chọn một nguồn dữ liệu để dựng lại các bản chiếu.

## 8. Khớp theo comparator không phải luôn là equals

Để thấy rõ điều này, dùng quy tắc so chuỗi không phân biệt hoa/thường.

Lưu vào **ComparisonEquivalenceDemo.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class ComparisonEquivalenceDemo {
    public static void main(String[] args) {
        Comparator<String> rule = String.CASE_INSENSITIVE_ORDER;
        List<String> names =
                new ArrayList<>(List.of("beta", "ALPHA", "alpha", "Gamma"));
        names.sort(rule);

        String query = "AlPhA";
        int index = Collections.binarySearch(names, query, rule);
        String matched = names.get(index);

        System.out.println("Sorted: " + names);
        System.out.println("Binary search found: " + (index >= 0));
        System.out.println("Comparator match: " + (rule.compare(matched, query) == 0));
        System.out.println("Equals match: " + matched.equals(query));
        System.out.println("List contains exact query: " + names.contains(query));

        TreeSet<String> set = new TreeSet<>(rule);
        set.addAll(names);
        System.out.println("TreeSet size: " + set.size());
        System.out.println("TreeSet contains query: " + set.contains(query));

        TreeMap<String, Integer> counts = new TreeMap<>(rule);
        counts.put("ALPHA", 1);
        Integer previous = counts.put("alpha", 2);
        System.out.println("TreeMap previous: " + previous);
        System.out.println("TreeMap size: " + counts.size());
        System.out.println("TreeMap query value: " + counts.get(query));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparisonEquivalenceDemo.java
java ComparisonEquivalenceDemo
```

Stdout:

```text
Sorted: [ALPHA, alpha, beta, Gamma]
Binary search found: true
Comparator match: true
Equals match: false
List contains exact query: false
TreeSet size: 3
TreeSet contains query: true
TreeMap previous: 1
TreeMap size: 1
TreeMap query value: 2
```

### 8.1. Vì sao không in chỉ số cụ thể của nhóm alpha?

`"ALPHA"` và `"alpha"` cùng hạng theo `String.CASE_INSENSITIVE_ORDER`. Query `"AlPhA"` cũng thuộc hạng đó.

Tìm kiếm có thể trả một vị trí phù hợp trong nhóm, nhưng không cam kết vị trí đầu hay cuối. Chương trình chỉ in những điều được bảo đảm: đã tìm thấy, kết quả compare bằng 0, và kết quả `equals` với query là false.

`List.sort` giữ cả hai phần tử; tính ổn định làm `"ALPHA"` vẫn trước `"alpha"` vì đó là thứ tự tương đối ban đầu. Xem [API List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

### 8.2. Vì sao contains của List và TreeSet khác nhau?

`names.contains(query)` dùng quy tắc bằng nhau của phần tử. Trong dữ liệu không có đúng chuỗi `"AlPhA"`, nên kết quả false.

`TreeSet.contains(query)` dùng quy tắc so sánh của cây, nên true. Hai chuỗi alpha chỉ chiếm một hạng trong cây; cộng với beta và Gamma cho kích thước 3.

Comparator cố ý không nhất quán với `String.equals`. Ví dụ minh họa hệ quả để người học nhận diện; không nên vô tình dùng một tập hợp như vậy rồi kỳ vọng mọi phép toán với các tập hợp dựa trên `equals` sẽ giữ cùng ý nghĩa.

Nếu nghiệp vụ coi các biến thể tên là cùng một khóa, hãy định nghĩa rõ chính sách khóa hoặc biểu diễn chuẩn phù hợp. Đừng để mỗi nơi âm thầm dùng một cách chuẩn hóa khác nhau.

### 8.3. Vì sao put không tạo khóa thứ hai?

`TreeMap` nhận `"ALPHA"` rồi `"alpha"`. Cả hai so bằng 0 theo quy tắc của map, nên lần sau thay giá trị từ 1 sang 2. Query với biến thể `"AlPhA"` lấy được 2.

`String.CASE_INSENSITIVE_ORDER` là quy tắc so chuỗi của Java, không tự trở thành phép sắp xếp ngôn ngữ cho mọi locale. Nếu ứng dụng cần quy tắc ngôn ngữ cụ thể, phải lựa chọn nó rõ ràng rồi áp dụng nhất quán. Xem [API String.CASE_INSENSITIVE_ORDER](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#CASE_INSENSITIVE_ORDER).

## 9. Tinh chỉnh quy tắc có thể đổi cả kết quả khớp

Đặt hai quy tắc:

```java
Comparator<Product> priceOnly =
        Comparator.comparingInt(Product::price);

Comparator<Product> priceThenSku =
        priceOnly.thenComparing(Product::sku);
```

Nếu dữ liệu chứa `B:200` và `C:200`:

| Nơi sử dụng | Chỉ theo giá | Theo giá rồi mã |
| --- | --- | --- |
| Sort danh sách | Hai phần tử hòa nhau | B đứng trước C |
| Binary search `X:200` | Có thể tìm một phần tử giá 200 | Không tìm thấy X:200 |
| TreeSet | B và C cùng một hạng, chỉ giữ một đại diện | Giữ được cả B và C |
| TreeMap dùng Product làm khóa | B và C thuộc cùng khóa thứ tự | Có thể có hai ánh xạ |

Không nên thêm tiêu chí phụ chỉ ở một nơi để “làm đẹp thứ tự” mà không xem nó thay đổi điều kiện bằng 0 ra sao.

Tương tự, thay chiều tăng thành giảm hoặc đổi vị trí null ở đoạn sort nhưng giữ đoạn tìm kiếm cũ có thể phá tiền điều kiện.

Nếu chỉ muốn lấy mọi sản phẩm của một mức giá, tìm một vị trí bằng binary search chưa đủ để lấy cả nhóm. Chọn thao tác tìm khoảng hoặc một chỉ mục theo giá phù hợp với mục tiêu đó.

## 10. Những thay đổi làm thứ tự không còn hợp lệ

### 10.1. Sửa danh sách sau khi sort

Sau khi có thứ tự, các thao tác như thêm vào cuối một phần tử phải đứng giữa, đổi một phần tử ở vị trí bất kỳ, hoặc `shuffle` có thể làm mất tiền điều kiện.

Các lựa chọn:

- Tính vị trí chèn bằng cùng comparator.
- Sắp lại trước khi tìm kiếm.
- Dùng cấu trúc duy trì thứ tự khi yêu cầu thay đổi thường xuyên.

Không cần sắp lại nếu thao tác thực sự giữ thứ tự, nhưng phải có lý do rõ ràng cho bảo đảm đó.

### 10.2. Sửa thuộc tính làm khóa so sánh

Một danh sách chưa đổi vị trí vẫn có thể mất thứ tự nếu giá bên trong đối tượng bị sửa.

Với cây, vấn đề còn xuất hiện ở vị trí đã tổ chức của phần tử/khóa. Cây không tự phát hiện việc một field thay đổi rồi chuyển nút sang chỗ mới.

Đối tượng khóa không cho sửa các trường quyết định thứ tự thường dễ quản lý. Nếu cần cập nhật trường đó, một cách là xóa khi giá trị cũ còn đúng, tạo đối tượng mới và thêm lại. Record `Product` với `String` và `int` trong chương trình giúp tránh việc sửa trực tiếp các thành phần.

### 10.3. Sửa cấu hình comparator

Comparator đọc một bảng trọng số hoặc một cờ chiều sắp xếp đang thay đổi cũng có thể làm quy tắc khác đi mà collection không đổi.

Tránh biến comparator của một cây đang dùng thành một quy tắc khác giữa chừng. Nếu chính sách thứ tự thay đổi, dựng lại cấu trúc theo quy tắc mới hoặc thực hiện quá trình chuyển đổi có kiểm soát.

Đa luồng làm những vấn đề này nghiêm trọng hơn. Dùng chung comparator không cung cấp đồng bộ cho danh sách, TreeSet hoặc TreeMap.

## 11. Lưu ý khi tạo bản sao và chuyển cấu trúc

### 11.1. Constructor không luôn kế thừa comparator của nguồn

Các constructor nhận nguồn tổng quát không thể suy ra comparator từ thứ tự phần tử:

```java
// Product không triển khai Comparable.
// Không dùng cách này để kỳ vọng kế thừa ORDER của một List:
// TreeSet<Product> set = new TreeSet<>(products);
```

Constructor `TreeSet(Collection)` dùng thứ tự tự nhiên. Constructor `TreeSet(SortedSet)` có hợp đồng riêng để giữ thứ tự của sorted set nguồn.

Tương tự:

- `TreeMap(Map)` tạo theo thứ tự tự nhiên của khóa.
- `TreeMap(SortedMap)` dùng thứ tự của sorted map nguồn.

Overload được chọn dựa trên kiểu tĩnh của đối số, nên hãy đọc rõ constructor đang gọi. Cách minh bạch trong ví dụ là tạo cây với `ORDER` rồi đưa dữ liệu vào.

### 11.2. List lấy từ cây không mang theo comparator

`new ArrayList<>(orderedSet)` nhận các phần tử theo thứ tự iterator của cây tại thời điểm sao chép, nhưng bản thân list không lưu comparator của cây.

Khi dùng binary search trên list đó, phải truyền lại quy tắc phù hợp. Có thể lấy quy tắc từ `sortedSet.comparator()` hoặc `sortedMap.comparator()`; kết quả null của các phương thức này biểu thị thứ tự tự nhiên.

Đừng diễn giải null đó thành một chính sách “null đứng đầu/cuối” cho dữ liệu.

### 11.3. TreeMap theo khóa không giữ thứ tự giá trị

Một danh sách lấy từ `treeMap.values()` đi theo thứ tự khóa của map. Nó không tự được sắp theo một comparator riêng của các giá trị.

Chỉ dùng binary search theo giá trị nếu bản dữ liệu hiện tại thực sự đáp ứng thứ tự đó, hoặc đã được sắp riêng theo quy tắc giá trị cần tìm.

## 12. Kiểm tra và chẩn đoán

Một kiểm tra hữu ích cho tiền điều kiện danh sách là kiểm tra mọi cặp kề nhau:

```java
// Phương thức minh họa, đặt trong một lớp có import phù hợp.
static <T> boolean isSorted(
        List<T> values, Comparator<? super T> rule) {
    for (int i = 1; i < values.size(); i++) {
        if (rule.compare(values.get(i - 1), values.get(i)) > 0) {
            return false;
        }
    }
    return true;
}
```

Cách này đánh giá thứ tự hiện tại theo comparator được truyền. Nó không tự chứng minh comparator đúng với mọi bộ ba hoặc ổn định trước thay đổi đồng thời. Với danh sách liên kết lớn, cách truy cập lặp theo chỉ số cũng cần xem lại về chi phí; đoạn mã tập trung vào minh họa điều kiện thứ tự.

Khi gặp kết quả bất thường, rà theo thứ tự:

1. Comparator thật sự được truyền vào sort và search là gì?
2. Có bỏ tiêu chí phụ, đảo chiều hoặc đổi null policy ở một nơi không?
3. Query có đủ dữ liệu của toàn bộ khóa so sánh không?
4. Danh sách có bị thêm, sửa, đảo hoặc thay field sau khi sắp không?
5. Kết quả tìm là bằng 0 theo comparator hay bằng nhau theo equals?
6. Cây đang dùng comparator nào, và constructor nào đã được gọi?
7. Có đang mong cây giữ nhiều phần tử mà comparator coi cùng hạng không?

Nếu chỉ biết mã sản phẩm nhưng thứ tự là giá rồi mã, không thể tùy ý gán một giá giả cho query rồi xem đó là truy vấn theo mã. Chọn chỉ mục theo mã, chẳng hạn một map dùng mã làm khóa, hoặc tìm theo điều kiện phù hợp.

## 13. Bài tập tự kiểm tra

1. Sắp danh sách số theo chiều giảm dần, rồi viết lời gọi binary search tương ứng. Giải thích vì sao overload tự nhiên không nhớ quy tắc sort.
2. Với `priceThenSku`, tính kết quả âm và điểm chèn của một sản phẩm có cùng giá nhưng mã chưa có.
3. Dùng `priceOnly` cho danh sách đã sắp bằng `priceThenSku`: tiền điều kiện có thể còn đúng, nhưng ý nghĩa “tìm thấy” thay đổi thế nào?
4. Dự đoán kích thước của List, TreeSet và TreeMap khi có hai mặt hàng khác mã nhưng cùng giá, với từng comparator.
5. Sao chép TreeSet sang ArrayList. Nêu hai thông tin cần giữ để binary search đúng sau khi sao chép.
6. Mô tả cách cập nhật giá của một khóa đang nằm trong TreeMap mà không để cấu trúc tiếp tục dựa trên khóa so sánh cũ.

Mục tiêu là giữ rõ một chuỗi hợp đồng: **quy tắc tạo thứ tự, tiền điều kiện của tìm kiếm, quan hệ khớp bằng 0 và cách cấu trúc nhận diện phần tử hoặc khóa**. Khi thay đổi quy tắc, cần đánh giá lại cả chuỗi thay vì chỉ quan sát thứ tự in ra.


</details>

- [Quay lại đầu trang](#back-to-top)
