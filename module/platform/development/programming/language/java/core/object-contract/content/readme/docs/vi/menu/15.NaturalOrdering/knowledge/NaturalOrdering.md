<a id="back-to-top"></a>

# Thứ tự tự nhiên

## Menu
- [Comparable&lt;T&gt; và ý nghĩa của thứ tự tự nhiên](#natural-ordering-001)
- [compareTo() trả về dấu âm, bằng không hoặc dấu dương](#natural-ordering-002)
- [Quy tắc đảo dấu khi đổi vị trí hai đối tượng](#natural-ordering-003)
- [Tính bắc cầu của quan hệ thứ tự](#natural-ordering-004)
- [Hai giá trị compareTo() bằng 0 phải được so sánh nhất quán với giá trị thứ ba](#natural-ordering-005)
- [Khuyến nghị nhất quán giữa compareTo() bằng 0 và equals()](#natural-ordering-006)
- [So sánh với null và kiểu không tương thích](#natural-ordering-007)
- [Không dùng phép trừ dễ tràn số để cài đặt compareTo()](#natural-ordering-008)
- [Viết tài liệu khi thứ tự tự nhiên cố ý không nhất quán với equals()](#natural-ordering-009)

## <a id="natural-ordering-001">Comparable&lt;T&gt; và ý nghĩa của thứ tự tự nhiên</a>

<details>
<summary>Xem chi tiết</summary>

# `Comparable<T>` và ý nghĩa của thứ tự tự nhiên

Trong Java, có những kiểu dữ liệu mà ta có thể sắp xếp theo một quy tắc mặc định có ý nghĩa: số nguyên từ nhỏ đến lớn, ngày tháng từ sớm đến muộn, phiên bản theo số `major` rồi đến `minor`. Quy tắc mặc định đó gọi là **thứ tự tự nhiên (natural ordering)**.

Giao diện `java.lang.Comparable<T>` cho phép **chính kiểu dữ liệu** công bố thứ tự tự nhiên của các giá trị thuộc kiểu đó. Phương thức cốt lõi là `compareTo(T other)`. Các thuật toán và cấu trúc như `Collections.sort`, `List.sort(null)`, `Arrays.sort`, `TreeSet` và `TreeMap` có thể sử dụng thứ tự này khi không được cung cấp một `Comparator` khác.

Vấn đề quan trọng khi học `Comparable` không chỉ là “viết hàm trả về -1, 0 hoặc 1”. Cần hiểu **giá trị âm/0/dương có nghĩa gì, hợp đồng so sánh yêu cầu điều gì, quan hệ với `equals` ra sao, và vì sao `TreeSet`/`TreeMap` có thể coi hai đối tượng là trùng khi `compareTo` trả về 0**.

## 1. `Comparable<T>` là gì?

Giao diện có dạng khái quát:

~~~java
public interface Comparable<T> {
    int compareTo(T other);
}
~~~

Kiểu `T` là kiểu giá trị mà đối tượng hiện tại có thể so sánh. Ví dụ một `Version` triển khai `Comparable<Version>` thì phương thức `compareTo` nhận một `Version`, cho phép trình biên dịch kiểm tra kiểu của đối số.

Kết quả của `a.compareTo(b)` được hiểu theo **dấu**, không nhất thiết theo giá trị tuyệt đối:

| Kết quả | Ý nghĩa |
| --- | --- |
| Số âm | `a` đứng **trước** `b` theo thứ tự tự nhiên. |
| `0` | `a` và `b` **tương đương trong quan hệ sắp xếp**. |
| Số dương | `a` đứng **sau** `b` theo thứ tự tự nhiên. |

Dấu âm có thể là `-1`, `-2` hoặc một số âm khác tùy cách cài đặt; tương tự dấu dương **không bắt buộc bằng `1`**. Khi xử lý kết quả, hãy kiểm tra `< 0`, `== 0` hoặc `> 0`, không kiểm tra cố định `== -1` hay `== 1`.

~~~java
int result = a.compareTo(b);
if (result < 0) {
    // a đứng trước b
} else if (result == 0) {
    // a và b tương đương theo thứ tự
} else {
    // a đứng sau b
}
~~~

Đây là đoạn mã minh họa **bên trong một phương thức**, với `a` và `b` có kiểu hỗ trợ `Comparable`.

### Thứ tự tự nhiên khác thứ tự mong muốn trong từng màn hình

Một kiểu có thể có một thứ tự tự nhiên phù hợp nhất, nhưng ứng dụng đôi khi cần sắp xếp theo một tiêu chí khác. Ví dụ `Version` tự nhiên tăng dần theo số phiên bản, còn màn hình phát hành mới nhất có thể cần hiển thị **giảm dần**. Trường hợp đó nên cung cấp `Comparator` khi sắp xếp, thay vì đổi `compareTo` mỗi khi một màn hình có nhu cầu mới.

## 2. Cài đặt thứ tự tự nhiên cho một kiểu dữ liệu

Giả sử một phiên bản có hai thành phần `major` và `minor`. Ta định nghĩa `1.9 < 1.10 < 2.0` bằng cách so **số nguyên** của `major` trước, nếu bằng nhau mới so `minor`. Không so chuỗi biểu diễn phiên bản, bởi thứ tự từ điển của `"1.10"` và `"1.9"` có thể khác thứ tự số phiên bản mong muốn.

Tệp **`ComparableVersionDemo.java`**, chạy độc lập trên Java 17+:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ComparableVersionDemo {

    record Version(int major, int minor) implements Comparable<Version> {
        Version {
            if (major < 0 || minor < 0) {
                throw new IllegalArgumentException("Version must be nonnegative");
            }
        }

        @Override
        public int compareTo(Version other) {
            int byMajor = Integer.compare(major, other.major);
            if (byMajor != 0) {
                return byMajor;
            }
            return Integer.compare(minor, other.minor);
        }
    }

    public static void main(String[] args) {
        Version first = new Version(1, 9);
        Version second = new Version(1, 10);
        Version sameValue = new Version(1, 9);

        System.out.println(Integer.signum(first.compareTo(second)));
        System.out.println(Integer.signum(second.compareTo(first)));
        System.out.println(first.compareTo(sameValue));
        System.out.println(first.equals(sameValue));
        System.out.println(first == sameValue);

        List<Version> versions = new ArrayList<>(List.of(
            new Version(2, 0),
            new Version(1, 10),
            new Version(1, 9),
            new Version(2, 0)
        ));

        Collections.sort(versions);
        System.out.println(versions);

        Set<Version> unique = new TreeSet<>(versions);
        System.out.println(unique);
        System.out.println(unique.size());
        System.out.println(unique.contains(new Version(1, 10)));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ComparableVersionDemo.java
java ComparableVersionDemo
~~~

Kết quả chính xác:

~~~text
-1
1
0
true
false
[Version[major=1, minor=9], Version[major=1, minor=10], Version[major=2, minor=0], Version[major=2, minor=0]]
[Version[major=1, minor=9], Version[major=1, minor=10], Version[major=2, minor=0]]
3
true
~~~

Phân tích:

1. `first.compareTo(second)` âm: phiên bản `1.9` đứng trước `1.10`. `second.compareTo(first)` dương. `Integer.signum` ở đây chỉ để in `-1` hoặc `1` dễ nhìn.
2. `first.compareTo(sameValue) == 0` vì cả hai cùng `major = 1` và `minor = 9`.
3. `first.equals(sameValue)` là `true` vì **record** sinh `equals` theo hai thành phần. Nhưng `first == sameValue` là `false`: đây vẫn là hai đối tượng độc lập.
4. `Collections.sort` sử dụng thứ tự tự nhiên do `Version.compareTo` định nghĩa, nên các phiên bản xuất hiện theo thứ tự `1.9`, `1.10`, `2.0`, `2.0`.
5. `TreeSet` sắp tăng dần và giữ một phần tử đại diện cho mỗi giá trị khác biệt theo phép so sánh. Hai bản `2.0` có kết quả so sánh `0`, nên `unique.size()` bằng `3`.
6. `contains(new Version(1, 10))` trả `true` dù tham số là instance vừa tạo, vì nó tương đương theo quan hệ sắp xếp của tập.

Ví dụ dùng `record` và hai trường nguyên thủy để `equals`, `hashCode` và `compareTo` có cùng ý nghĩa giá trị. Đây là một lựa chọn thiết kế **nhất quán và dễ dùng với collection**.

## 3. Hợp đồng của `compareTo`

`Comparable` đặt ra những quy tắc để một phép so sánh có thể dùng đáng tin cậy trong sắp xếp và cấu trúc cây. Ký hiệu `sgn(n)` là dấu của số nguyên `n` (`-1`, `0`, hoặc `1`).

### 3.1. Đổi chiều so sánh phải đổi dấu

Với hai đối tượng phù hợp `x`, `y`:

~~~text
sgn(x.compareTo(y)) == -sgn(y.compareTo(x))
~~~

Nếu `x` đứng trước `y`, thì `y` phải đứng sau `x`. Nếu một chiều bằng `0`, chiều còn lại cũng phải bằng `0`. Không phải điều kiện “hai kết quả số nguyên phải cộng lại đúng bằng 0”; chỉ **dấu** mới quan trọng.

### 3.2. Tính bắc cầu

Nếu `x > y` và `y > z` theo `compareTo`, thì `x > z`. Tương tự quan hệ tăng dần. Ví dụ phiên bản `2.0 > 1.10` và `1.10 > 1.9`, nên `2.0 > 1.9`.

Vi phạm tính bắc cầu có thể làm thuật toán sắp xếp hoạt động sai hoặc ném lỗi liên quan đến hợp đồng so sánh trong một số trường hợp.

### 3.3. Các giá trị tương đương phải ứng xử giống nhau khi so với giá trị khác

Nếu `x.compareTo(y) == 0`, thì với mọi `z` cùng miền so sánh:

~~~text
sgn(x.compareTo(z)) == sgn(y.compareTo(z))
~~~

Nói cách khác, khi `x` và `y` được coi là tương đương về thứ tự, chúng không được có quan hệ trái ngược với một giá trị thứ ba. Đây là yêu cầu quan trọng để các phép sắp xếp và tìm kiếm theo thứ tự hoạt động có ý nghĩa.

### 3.4. Xử lý `null` và kiểu không tương thích

Các triển khai `compareTo` nên ném `NullPointerException` khi đối số là `null`; `null` không được xem là một giá trị `Version` đứng trước mọi phiên bản. Nếu đối tượng truyền vào không thể so với đối tượng nhận, việc so sánh không hợp lệ có thể gây `ClassCastException`. Khai báo `Comparable<Version>` đã giúp ngăn nhiều trường hợp sai kiểu ngay khi biên dịch, nhưng raw type hoặc một số đường gọi qua collection vẫn có thể gây lỗi lúc chạy.

Một `Comparator` được thiết kế riêng có thể quy định vị trí `null` bằng các tiện ích như `Comparator.nullsFirst` hoặc `nullsLast`; điều đó không làm thay đổi hợp đồng mặc định của `Version.compareTo`.

## 4. `compareTo() == 0` có phải `equals() == true`?

**Không phải lúc nào cũng vậy.** Hai phương thức xác định hai quan hệ khác nhau:

- `equals(Object)` định nghĩa **bằng nhau** của đối tượng theo hợp đồng `Object`; `HashSet` và `HashMap` dựa vào `equals`/`hashCode` để phân biệt phần tử/key.
- `compareTo(T)` định nghĩa **thứ tự tự nhiên**; `compareTo == 0` có nghĩa hai giá trị **tương đương theo thứ tự**, không tự động khẳng định `equals` trả `true`.
- Một kiểu có thứ tự tự nhiên **nhất quán với `equals`** khi và chỉ khi `x.compareTo(y) == 0` tương đương `x.equals(y) == true` đối với các giá trị tương ứng.

Java **rất khuyến nghị** thứ tự tự nhiên nhất quán với `equals`, nhưng không bắt buộc mọi kiểu phải có tính chất này. Khi không nhất quán, lập trình viên phải hiểu hệ quả, đặc biệt khi dùng trong các sorted collections.

### Ví dụ đặc biệt: `BigDecimal`

`BigDecimal` xét **giá trị số học** khi `compareTo`, nhưng `equals` còn xét cả `scale`. Vì vậy `1.0` và `1.00` có thể tương đương theo thứ tự nhưng không bằng nhau theo `equals`.

Tệp **`BigDecimalOrderingDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class BigDecimalOrderingDemo {
    public static void main(String[] args) {
        BigDecimal first = new BigDecimal("1.0");
        BigDecimal second = new BigDecimal("1.00");

        System.out.println(first.equals(second));
        System.out.println(first.compareTo(second));
        System.out.println(first.hashCode() == second.hashCode());

        Set<BigDecimal> hashValues = new HashSet<>();
        hashValues.add(first);
        hashValues.add(second);
        System.out.println(hashValues.size());

        Set<BigDecimal> sortedValues = new TreeSet<>();
        sortedValues.add(first);
        sortedValues.add(second);
        System.out.println(sortedValues.size());
        System.out.println(sortedValues.contains(second));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac BigDecimalOrderingDemo.java
java BigDecimalOrderingDemo
~~~

Kết quả chuẩn:

~~~text
false
0
false
2
1
true
~~~

`HashSet` giữ hai phần tử vì chúng không bằng nhau theo `equals`; `TreeSet` chỉ giữ một phần tử vì kết quả `compareTo` bằng `0`. Không phải lỗi của `TreeSet`: đây là hệ quả trực tiếp từ quy tắc phân biệt phần tử của một **sorted set**. Khi dùng `BigDecimal`, cần xác định rõ nghiệp vụ quan tâm cả `scale` hay chỉ quan tâm giá trị số học.

Trong bài toán tự thiết kế kiểu mới, thông thường nên làm cho `compareTo == 0` trùng với `equals == true` để hạn chế bất ngờ. Với `Version`, cả hai phương thức đều xét `major` và `minor`, nên không có sự bất nhất này.

## 5. Vì sao `TreeSet` và `TreeMap` dùng kết quả so sánh để nhận diện khóa?

`TreeSet` giữ các phần tử có thứ tự và `TreeMap` giữ các khóa có thứ tự. Nếu dùng thứ tự tự nhiên, chúng xác định tính tương đương của phần tử/key bằng `compareTo(...) == 0`; nếu được truyền một `Comparator`, chúng dùng kết quả `compare(...) == 0` của comparator đó.

| Cấu trúc | Cách phân biệt phần tử hoặc key |
| --- | --- |
| `HashSet` | Dùng `hashCode` để tổ chức tìm kiếm và `equals` để xác định trùng giá trị. |
| `HashMap` | Dùng `hashCode` và `equals` của key. |
| `TreeSet` | Dùng `compareTo == 0` hoặc `Comparator.compare == 0` để xác định tương đương theo thứ tự. |
| `TreeMap` | Dùng `compareTo == 0` hoặc `Comparator.compare == 0` đối với key. |

Ví dụ, với `TreeMap<Version, String>` dùng tự nhiên, gọi `put(new Version(1, 9), "old")` rồi `put(new Version(1, 9), "new")` sẽ giữ **một entry** cho `1.9`, với value `"new"`. Một instance key mới không nhất thiết tạo entry mới.

Tuy nhiên, nếu `compareTo` của một lớp **không nhất quán** với `equals`, sorted set/map có thể không tuân thủ đúng ngữ nghĩa tổng quát mà `Set`/`Map` đòi hỏi ở những tình huống có các đối tượng `equals` khác nhau. Vì vậy, đây là một rủi ro API thực tế, không chỉ là một chi tiết toán học của thứ tự.

## 6. Lỗi cài đặt: lấy hiệu hai số để so sánh

Một lỗi phổ biến là viết:

~~~java
@Override
public int compareTo(Version other) {
    return major - other.major; // Ví dụ sai: dễ overflow, bỏ qua minor.
}
~~~

Đoạn trên minh họa lỗi trong ngữ cảnh lớp `Version`, không phải mã thay thế cho phiên bản chính xác ở mục 2. Phép trừ `int` có thể **tràn số**; sau khi overflow, dấu của hiệu có thể bị đảo và làm vi phạm thứ tự mong muốn.

Ví dụ nhỏ chạy trong thân một phương thức:

~~~java
int a = Integer.MAX_VALUE;
int b = -1;

System.out.println(a - b);              // -2147483648 (overflow)
System.out.println(Integer.compare(a,b)); // 1
~~~

Ở đây `a` lớn hơn `b`, nhưng `a - b` trở thành số âm do tràn miền `int`, dễ làm thuật toán sắp xếp đưa giá trị lớn lên trước giá trị nhỏ. **Dùng `Integer.compare(a, b)`**, `Long.compare`, `Double.compare`, hoặc `compareTo` có sẵn cho kiểu thích hợp.

Tránh cả việc trả `1` cho mọi cặp khác nhau: nếu `x > y` luôn `true` và `y > x` cũng luôn `true`, hợp đồng đổi chiều so sánh đã bị phá vỡ.

## 7. Thứ tự tự nhiên và `Comparator<T>`

`Comparable<T>` định nghĩa thứ tự **ngay trong class**. Còn `java.util.Comparator<T>` là một đối tượng riêng định nghĩa một cách so sánh, nên có thể tạo nhiều thứ tự cho cùng kiểu mà không sửa class.

| Nhu cầu | Chọn |
| --- | --- |
| Một thứ tự mặc định ổn định, hợp lý cho kiểu | `implements Comparable<T>` với `compareTo`. |
| Sắp xếp khác thứ tự mặc định ở một màn hình/chức năng | Truyền `Comparator<T>` cho thuật toán sắp xếp. |
| Đảo chiều thứ tự tự nhiên | `Comparator.reverseOrder()` hoặc `Comparator.naturalOrder().reversed()`. |
| So sánh nhiều trường với quy tắc tùy biến | `Comparator.comparing(...).thenComparing(...)`. |

Ví dụ đầy đủ khác cho `Version`, vẫn dùng cùng lớp record từ tệp `ComparableVersionDemo.java` ở mục 2:

~~~java
List<Version> latestFirst = new ArrayList<>(List.of(
    new Version(1, 9), new Version(2, 0), new Version(1, 10)
));

latestFirst.sort(java.util.Comparator.reverseOrder());
System.out.println(latestFirst);
~~~

Khi chèn vào `main` của `ComparableVersionDemo`, đoạn trên sẽ in:

~~~text
[Version[major=2, minor=0], Version[major=1, minor=10], Version[major=1, minor=9]]
~~~

`Version.compareTo` **không bị sửa**. Chỉ thao tác sắp xếp này sử dụng thứ tự đảo chiều. Có thể dùng `List.sort(null)` để quay về thứ tự tự nhiên.

Một kiểu có thể triển khai `Comparable`, nhưng khi truyền một `Comparator` vào `TreeSet` hoặc `TreeMap`, **comparator được dùng** để xác định thứ tự và các phần tử/key tương đương. Do đó, comparator cũng cần tuân thủ hợp đồng so sánh và nên nhất quán với `equals` nếu collection cần thể hiện đúng ngữ nghĩa `Set`/`Map` tổng quát.

## 8. Một số kiểu Java đã có thứ tự tự nhiên

| Kiểu | Ý nghĩa thứ tự tự nhiên |
| --- | --- |
| `Integer`, `Long`, `BigInteger` | Thứ tự tăng dần theo giá trị số. |
| `String` | Thứ tự từ điển dựa trên giá trị các đơn vị mã UTF-16; phân biệt chữ hoa/chữ thường. |
| `LocalDate` | Ngày sớm hơn đứng trước ngày muộn hơn. |
| `Instant` | Thời điểm sớm hơn đứng trước thời điểm muộn hơn. |
| `BigDecimal` | Theo giá trị số học, có thể không nhất quán với `equals` do `scale`. |
| `Enum` | Thứ tự khai báo hằng enum trong kiểu enum đó. |

Không nên suy ra rằng thứ tự tự nhiên của `String` là thứ tự ngôn ngữ phù hợp với người đọc tiếng Việt hoặc mọi locale. Khi cần sắp xếp văn bản theo quy tắc ngôn ngữ, xem xét `Collator` hay quy tắc locale thích hợp qua một `Comparator`, thay vì giả định `String.compareTo` xử lý phân loại chữ theo ngôn ngữ tự nhiên.

Ngoài ra, **không phải mọi class Java đều triển khai `Comparable`**. Sắp xếp một danh sách các đối tượng không có thứ tự tự nhiên (và không cung cấp comparator) có thể gây `ClassCastException` khi thuật toán cần so sánh các phần tử. Muốn sắp xếp các đối tượng như vậy, định nghĩa `Comparator` hoặc triển khai `Comparable` nếu thứ tự mặc định thực sự hợp lý.

## 9. Lưu ý về tính ổn định và dữ liệu có thể thay đổi

Thứ tự tự nhiên thường dựa vào các trường xác định giá trị của đối tượng. Nếu những trường đó **có thể thay đổi** khi đối tượng đã nằm trong `TreeSet` hoặc đang làm key của `TreeMap`, cấu trúc cây có thể không còn được tổ chức phù hợp với thứ tự mới. Điều này dẫn tới việc `contains`, `get` hoặc `remove` không tìm thấy giá trị mong muốn.

Vì vậy, với kiểu dùng làm phần tử của sorted set hoặc key của sorted map, **ưu tiên bất biến** đối với các trường tham gia phép so sánh. `Version` ở mục 2 là record chứa hai `int` không thay đổi sau khi tạo, phù hợp với mục tiêu đó.

Cũng không nên cài đặt `compareTo` dựa trên một nguồn thay đổi theo thời gian như đồng hồ, random, hoặc trạng thái bên ngoài không ổn định. Với cùng hai đối tượng mà thứ tự thay đổi bất ngờ, phép sắp xếp và tra cứu có thể sai.

## 10. Quy trình thiết kế và kiểm tra `Comparable<T>`

1. **Xác định ý nghĩa thứ tự tự nhiên** của kiểu. Viết rõ trường chính và trường phụ dùng để phân xử khi trường chính bằng nhau.
2. **Triển khai `Comparable<T>`** và override `public int compareTo(T other)`. Dùng các phép so an toàn như `Integer.compare`, `Long.compare`, `String.compareTo` thay vì trừ trực tiếp các số nguyên.
3. **Kiểm tra dấu và hợp đồng:** đổi chiều phải đổi dấu; thứ tự phải bắc cầu; hai giá trị `compareTo == 0` phải so với giá trị thứ ba theo cùng chiều.
4. **Đối chiếu `equals` và `hashCode`:** cố gắng để `compareTo == 0` tương đương `equals == true` cho kiểu tự thiết kế, nhất là khi dùng `TreeSet`/`TreeMap`.
5. **Thử sắp xếp nhiều phần tử**, trong đó có dữ liệu trùng trường chính nhưng khác trường phụ; xác minh thứ tự sau `Collections.sort` hoặc `List.sort(null)`.
6. **Thử với sorted collection:** hai instance khác nhau nhưng `compareTo == 0` có được coi là một phần tử/key không? Đó có phù hợp nghiệp vụ không?
7. **Giữ trường so sánh ổn định** sau khi thêm vào cấu trúc cây; dùng `Comparator` riêng nếu chức năng khác cần một thứ tự khác.

### Tự kiểm tra

1. `a.compareTo(b)` trả `-5`. Điều gì được bảo đảm về thứ tự của `a` và `b`? Có phải mọi lần so sánh hợp lệ chỉ được trả `-1`, `0`, `1`?
2. `new Version(1, 9)` và `new Version(1, 10)` đối tượng nào đứng trước trong ví dụ? Vì sao không nên so chuỗi `"1.9"` với `"1.10"` để quyết định?
3. Hai đối tượng có `compareTo == 0` nhưng `equals == false`. Nếu đưa vào `TreeSet` và `HashSet`, điều gì có thể khác nhau?
4. Tại sao `return a - b;` trong `compareTo` có thể gây sai kết quả với giá trị `int` rất lớn và rất nhỏ?
5. Nếu một kiểu đã có thứ tự tự nhiên tăng dần nhưng một màn hình muốn giảm dần, nên sửa `compareTo` hay truyền `Comparator.reverseOrder()`?
6. Nếu thay đổi trường dùng trong `compareTo` của key đang nằm trong `TreeMap`, điều gì có thể xảy ra khi tìm kiếm key đó?

**Ghi nhớ:** `Comparable<T>` định nghĩa **thứ tự tự nhiên** qua `compareTo(T)`; **âm** nghĩa là đứng trước, **0** là tương đương theo thứ tự, **dương** là đứng sau. Quan hệ này phải nhất quán về dấu và tính bắc cầu. Nên giữ `compareTo == 0` nhất quán với `equals == true` để tránh bất ngờ trong `TreeSet`/`TreeMap`, và dùng `Comparator` cho những quy tắc sắp xếp riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-002">compareTo() trả về dấu âm, bằng không hoặc dấu dương</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tự nhiên: compareTo() trả về dấu âm, bằng không hoặc dấu dương

## 1. Ý nghĩa chính xác của kết quả compareTo

Trong Java, một lớp triển khai **Comparable<T>** khi muốn xác định thứ tự tự nhiên (natural ordering) cho các đối tượng cùng kiểu. Phương thức compareTo(T other) trả về một giá trị kiểu int, nhưng **dấu** của kết quả mới biểu thị quan hệ:

| Giá trị x.compareTo(y) | Ý nghĩa | Sắp xếp tăng dần |
| --- | --- | --- |
| Âm, nhỏ hơn 0 | x đứng trước y | x đứng trước |
| Bằng 0 | x và y tương đương theo thứ tự | Cùng vị trí logic theo so sánh |
| Dương, lớn hơn 0 | x đứng sau y | x đứng sau |

Đặc biệt, **compareTo không bắt buộc trả đúng -1, 0 hoặc 1**. Các giá trị -25, -4, 7, 25 đều hợp lệ khi phù hợp quan hệ thứ tự. Không diễn giải độ lớn của số trả về là khoảng cách giữa hai đối tượng.

Ví dụ với String:

~~~java
System.out.println("a".compareTo("z")); // -25
System.out.println("a".compareTo("a")); // 0
System.out.println("z".compareTo("a")); // 25
~~~

Với hai ký tự Latin đơn giản này, String.compareTo trả hiệu của giá trị ký tự UTF-16 đầu tiên khác nhau, nên có kết quả âm/dương với độ lớn 25.

Cách kiểm tra nên dùng:

~~~java
int result = x.compareTo(y);
if (result < 0) {
    System.out.println("x đứng trước y");
} else if (result > 0) {
    System.out.println("x đứng sau y");
} else {
    System.out.println("x tương đương y theo thứ tự");
}
~~~

Đây là mã minh họa trong ngữ cảnh đã khai báo x và y tương thích; chưa phải một chương trình chạy độc lập. **Không kiểm tra result == -1 để hiểu là nhỏ hơn**, vì kết quả -2 hoặc -25 cũng có cùng ý nghĩa. Có thể dùng Integer.signum(result) khi cần chuẩn hóa thành -1/0/1, nhưng điều đó không bắt buộc cho các nhánh điều kiện.

## 2. Comparable và Comparator cùng trả về dấu

`Comparable<T>` đặt quy tắc thứ tự tự nhiên trong kiểu dữ liệu; `Comparator<T>` xác định một thứ tự có thể truyền từ bên ngoài, chẳng hạn sắp theo ngày giảm dần hoặc theo điểm. Cả hai đều dùng **dấu âm/0/dương**, nên đoạn kiểm tra `result < 0` áp dụng cho cả `x.compareTo(y)` lẫn `comparator.compare(x, y)`.

Khi dùng `TreeSet` hoặc `TreeMap` với comparator riêng, `0` còn quyết định hai phần tử/khóa có cùng vị trí logic trong cấu trúc cây. Bài đầu chương giải thích đầy đủ khi nào chọn natural ordering hay comparator.

## 3. Dấu sai khi phép trừ bị tràn số

Dấu trả về chỉ hữu ích khi phép so sánh tính đúng: `return a - b` với hai `int` có thể tràn số và làm đảo chiều quan hệ.

~~~java
int low = Integer.MIN_VALUE;
int high = Integer.MAX_VALUE;
System.out.println(low - high);                  // 1 (sai dấu để so sánh)
System.out.println(Integer.compare(low, high)); // -1
~~~

Dùng `Integer.compare`, `Long.compare` và các helper tương ứng. Bài về **tràn số trong compareTo** sẽ kiểm tra các biên `MIN_VALUE`/`MAX_VALUE`, ép `long` xuống `int` và tác động tới tính bắc cầu.

## 4. Java 17+: ví dụ đầy đủ, chạy được

Lưu thành tệp **CompareToSignDemo.java**:

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class CompareToSignDemo {
    public static void main(String[] args) {
        Job first = new Job(3, "B");
        Job second = new Job(3, "A");
        Job third = new Job(1, "A");

        System.out.println("first vs second: " + first.compareTo(second));
        System.out.println("second vs first: " + second.compareTo(first));
        System.out.println("same value: "
                + second.compareTo(new Job(3, "A")));

        List<Job> jobs = new ArrayList<>();
        jobs.add(first);
        jobs.add(second);
        jobs.add(third);
        jobs.sort(null);
        System.out.println("sorted list: " + jobs);

        Set<Job> sortedJobs = new TreeSet<>(jobs);
        System.out.println("TreeSet<Job> size: " + sortedJobs.size());

        int low = Integer.MIN_VALUE;
        int high = Integer.MAX_VALUE;
        System.out.println("subtraction: " + (low - high));
        System.out.println("Integer.compare: " + Integer.compare(low, high));
        System.out.println("String compare z/a: " + "z".compareTo("a"));

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("BigDecimal compare: " + a.compareTo(b));
        System.out.println("BigDecimal equals: " + a.equals(b));

        Set<BigDecimal> tree = new TreeSet<>();
        tree.add(a);
        tree.add(b);
        Set<BigDecimal> hash = new HashSet<>();
        hash.add(a);
        hash.add(b);
        System.out.println("TreeSet<BigDecimal> size: " + tree.size());
        System.out.println("HashSet<BigDecimal> size: " + hash.size());
    }

    record Job(int priority, String code) implements Comparable<Job> {
        Job {
            Objects.requireNonNull(code);
        }

        @Override
        public int compareTo(Job other) {
            Objects.requireNonNull(other);
            int byPriority = Integer.compare(priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return code.compareTo(other.code);
        }
    }
}
~~~

Chạy lệnh từ thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 CompareToSignDemo.java
java CompareToSignDemo
~~~

Kết quả stdout:

~~~text
first vs second: 1
second vs first: -1
same value: 0
sorted list: [Job[priority=1, code=A], Job[priority=3, code=A], Job[priority=3, code=B]]
TreeSet<Job> size: 3
subtraction: 1
Integer.compare: -1
String compare z/a: 25
BigDecimal compare: 0
BigDecimal equals: false
TreeSet<BigDecimal> size: 1
HashSet<BigDecimal> size: 2
~~~

**Giải thích:** first và second có priority bằng 3, code "B" đứng sau "A" nên compareTo dương. Khi đảo chiều, kết quả âm. Hai Job cùng priority và code trả 0. Sau khi sort, Job có priority 1 đứng đầu; các Job priority 3 sắp theo code A rồi B. Record Job được sinh equals/hashCode dựa trên cả hai component, phù hợp với cặp trường mà compareTo dùng.

Hai số nguyên cực trị cho thấy phép trừ tràn số trả 1 còn Integer.compare trả -1. "z".compareTo("a") trả 25, minh chứng compareTo không bị giới hạn -1/0/1. Hai BigDecimal biểu diễn cùng giá trị nhưng khác scale dẫn đến khác biệt giữa compareTo và equals, phân tích ở phần tiếp theo.

## 5. Đọc dấu đúng trong toàn bộ hợp đồng

Ngoài dấu âm/0/dương, một `compareTo` hợp lệ còn giữ các tính chất sau trên miền đối tượng có thể so sánh:

| Quy tắc | Cần kiểm tra |
| --- | --- |
| Đảo dấu | `Integer.signum(x.compareTo(y)) == -Integer.signum(y.compareTo(x))` |
| Bắc cầu | Nếu `x > y` và `y > z` thì `x > z` |
| Giá trị tương đương | Nếu `x.compareTo(y) == 0`, dấu khi so `x` và `y` với bất kỳ `z` hợp lệ phải giống nhau |
| `null` | `x.compareTo(null)` ném `NullPointerException` |

Quy tắc đảo dấu chỉ nói về **dấu**, không yêu cầu hai kết quả có cùng trị tuyệt đối. Ví dụ `-3` và `8` vẫn trái dấu hợp lệ nếu các tính chất khác cũng được giữ. Các bài tiếp theo kiểm tra từng điều kiện bằng các cặp và bộ ba cụ thể.

## 6. Dấu 0 là tương đương theo thứ tự, không tự động là equals

`compareTo == 0` xác định hai giá trị **cùng vị trí theo thứ tự**. `equals == true` lại là bình đẳng logic của đối tượng. Java khuyến nghị mạnh hai quan hệ này nhất quán nhưng cho phép khác nhau có chủ đích.

Ví dụ, `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0` trong khi `equals` trả `false` do khác `scale`. Vì vậy `TreeSet<BigDecimal>` chỉ giữ một phần tử tương đương theo thứ tự, còn `HashSet<BigDecimal>` giữ hai phần tử theo `equals/hashCode`. Chương trình ở mục 4 quan sát cùng sự khác biệt này; bài về `equals` và `compareTo` phía sau hướng dẫn chọn hợp đồng đúng.

## 7. So sánh nhiều trường: xử lý hòa đúng cách

Mẫu chuẩn để so theo trường đầu, sau đó đến trường thứ hai:

~~~java
// Trích đoạn trong lớp Person có lastName, firstName là String không null.
@Override
public int compareTo(Person other) {
    int byLast = lastName.compareTo(other.lastName);
    if (byLast != 0) {
        return byLast;
    }
    return firstName.compareTo(other.firstName);
}
~~~

Nếu so sánh khóa trước trả **bất kỳ giá trị khác 0 nào**, hãy trả luôn; chỉ so khóa tiếp theo khi bằng 0. Không kiểm tra byLast == -1 hoặc byLast == 1. Nếu trường có thể null, dùng Comparator.nullsFirst/nullsLast hoặc xử lý rõ ràng, tránh gọi compareTo trực tiếp trên null.

Một Comparator thay thế có thể viết:

~~~java
// Minh họa trong ngữ cảnh có lớp Person và import java.util.Comparator.
Comparator<Person> byName =
        Comparator.comparing(Person::lastName)
                  .thenComparing(Person::firstName);
~~~

Với số nguyên nguyên thủy, Comparator.comparingInt và comparingLong giúp viết code gọn và tránh boxing không cần thiết. Nếu comparator được dùng trong TreeSet/TreeMap, hãy cân nhắc tác động của compare == 0 đến việc phân biệt khóa.

## 8. Những lỗi thường gặp

| Sai lầm | Vì sao sai | Cách khắc phục |
| --- | --- | --- |
| compareTo chỉ được trả -1/0/1 | Hợp đồng chỉ yêu cầu dấu | Kiểm tra < 0, == 0, > 0 |
| Dùng == -1 để nhận biết đứng trước | Bỏ sót -2, -25, ... | Kiểm tra < 0 |
| Lấy hiệu hai int hoặc long | Có thể tràn số | Integer.compare, Long.compare |
| Ép hiệu long về int | Có thể mất thông tin và đảo dấu | Long.compare |
| Coi độ lớn compareTo là khoảng cách | compareTo diễn tả thứ tự, không phải metric | Định nghĩa thước đo riêng |
| compareTo == 0 luôn đồng nghĩa equals | Không đúng với BigDecimal | Kiểm tra hợp đồng của kiểu |
| Chỉ so một khóa không đủ phân biệt | TreeSet có thể gộp giá trị khác nhau | Thêm khóa phá hòa nếu cần |
| Thiếu đối xứng hoặc bắc cầu | Sắp xếp và collection có thể sai kỳ vọng | Kiểm thử quan hệ thứ tự |
| Gọi compareTo của một trường null | Có thể NullPointerException | Chính sách null rõ ràng |
| Xem toán tử < trên float/double luôn đủ | Không xử lý đúng NaN và signed zero theo wrapper | Float.compare/Double.compare khi phù hợp |

## 9. Checklist và kiểm thử

- **Dấu:** nhỏ hơn trả âm, bằng theo thứ tự trả 0, lớn hơn trả dương.
- **Đảo chiều:** đổi thứ tự x/y thì dấu phải đảo.
- **Tự so sánh:** x.compareTo(x) == 0.
- **Bắc cầu:** các bộ ba x, y, z phải giữ thứ tự nhất quán.
- **Tương đương:** nếu x.compareTo(y) == 0, dấu khi so với cùng z phải khớp.
- **Biên số học:** thử Integer.MIN_VALUE, Integer.MAX_VALUE và long cực trị.
- **Phá hòa:** các trường trước trùng nhau phải được so bằng trường kế tiếp.
- **Null:** xác nhận chính sách xử lý null mong muốn.
- **Collections:** kiểm tra List.sort, TreeSet/TreeMap với các khóa tương đương.
- **Equals:** nếu tài liệu lớp tuyên bố thứ tự tương thích equals, kiểm tra compareTo == 0 **khi và chỉ khi** equals true.

Ví dụ assertion ngắn đặt trong ngữ cảnh đã khai báo ba đối tượng a, b, c:

~~~java
if (a.compareTo(b) >= 0) {
    throw new AssertionError("a phải đứng trước b");
}
if (b.compareTo(a) <= 0) {
    throw new AssertionError("b phải đứng sau a");
}
if (c.compareTo(c) != 0) {
    throw new AssertionError("Tự so sánh phải trả 0");
}
~~~

Những assertion này là đoạn minh họa, không phải chương trình đứng riêng; dữ liệu kiểm thử phải được chọn theo quy tắc so sánh của lớp.

## 10. Ghi nhớ

- **Âm:** đối tượng hiện tại đứng trước đối số.
- **Không:** hai đối tượng tương đương theo thứ tự.
- **Dương:** đối tượng hiện tại đứng sau đối số.

Điều cần nhớ là **dấu của compareTo**, không phải độ lớn. Dùng phương thức so sánh chuẩn thay phép trừ, tuân thủ đối xứng về dấu và tính bắc cầu, xử lý đúng nhiều khóa và hiểu rõ quan hệ compareTo == 0 với equals khi làm việc với TreeSet/TreeMap.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-003">Quy tắc đảo dấu khi đổi vị trí hai đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc đảo dấu khi đổi vị trí hai đối tượng trong thứ tự tự nhiên của Java

## 1. Phát biểu quy tắc: đổi vị trí thì đổi **dấu** kết quả so sánh

Trong Java, một lớp triển khai `Comparable<T>` để định nghĩa **thứ tự tự nhiên** (*natural ordering*) giữa những đối tượng có thể so sánh với nhau. Phương thức cốt lõi là:

```java
public int compareTo(T other)
```

Giá trị trả về là một số `int` có ý nghĩa qua **dấu**, không phải độ lớn tuyệt đối:

| `a.compareTo(b)` | Diễn giải |
| --- | --- |
| Nhỏ hơn `0` | `a` đứng trước `b` trong thứ tự tự nhiên |
| Bằng `0` | Hai đối tượng **tương đương theo thứ tự** (*compare-equivalent*) |
| Lớn hơn `0` | `a` đứng sau `b` trong thứ tự tự nhiên |

Một quy tắc bắt buộc của hợp đồng `Comparable` là **đảo dấu khi đổi vị trí hai đối tượng**:

```text
sgn(a.compareTo(b)) == -sgn(b.compareTo(a))
```

Trong đó `sgn(n)` bằng `-1` nếu `n < 0`, `0` nếu `n == 0`, `1` nếu `n > 0`. Trong Java dùng `Integer.signum(n)` để lấy dấu một cách rõ ràng.

Ví dụ, nếu `a.compareTo(b)` là số âm thì `b.compareTo(a)` phải là số dương; nếu một chiều trả về `0` thì chiều còn lại **cũng phải trả về `0`**. Quy tắc này có thể gọi là **tính phản đối xứng theo dấu** (*sign antisymmetry*), khác với khái niệm phản đối xứng của quan hệ `≤` trong toán học và khác với tính đối xứng của `equals()`.

**Điểm hay bị nhầm:** hợp đồng yêu cầu *đổi dấu*, **không bắt buộc đổi đúng trị số**. Nếu `a.compareTo(b) == -8`, chiều ngược lại được phép trả về `1`, `7` hoặc bất kỳ số dương nào khác; nó không nhất thiết phải bằng `8`.

Trong các ví dụ và công thức ở đây, `a` và `b` là những đối tượng khác `null` thuộc miền so sánh mà kiểu dữ liệu đã công bố. Nếu lớp không hỗ trợ so sánh với một loại đối tượng khác, cách xử lý ngoại lệ cũng phải tương thích với hợp đồng của `Comparable`. Một đối tượng `null` không phải giá trị hợp lệ của thứ tự tự nhiên thông thường; lời gọi `someObject.compareTo(null)` theo hợp đồng phải ném `NullPointerException`.

## 2. Vì sao quy tắc này quan trọng?

Giả sử hai điểm số: `low = 5` và `high = 10`. Ta kỳ vọng:

```text
low.compareTo(high)  < 0
high.compareTo(low)  > 0
```

Nếu **cả hai** đều trả về giá trị âm, thuật toán sắp xếp sẽ nhận được thông tin mâu thuẫn: `low` đứng trước `high`, nhưng `high` cũng đứng trước `low`. Kết quả có thể là thứ tự không nhất quán, tìm kiếm sai kỳ vọng hoặc lỗi từ các thuật toán sắp xếp khi hợp đồng `Comparator/Comparable` bị vi phạm.

Điều này ảnh hưởng trực tiếp đến:

- `Collections.sort`, `List.sort` và `Arrays.sort` khi dùng thứ tự tự nhiên;
- `TreeSet` và `TreeMap`, vì cây cân bằng cần kết quả so sánh nhất quán để xác định vị trí khóa/phần tử;
- Các thuật toán tìm kiếm nhị phân hoặc quy tắc chọn phần tử nhỏ nhất/lớn nhất dựa trên thứ tự;
- Bất kỳ phép so sánh nào được triển khai lại ở cả hai hướng.

Một bộ so sánh đúng phải giữ đồng thời ba nhóm quy tắc: **đảo dấu**, **bắc cầu**, và **nhất quán của kết quả so sánh bằng 0 với các đối tượng thứ ba** (giải thích tại mục 5). Chỉ chứng minh được quy tắc đảo dấu chưa đủ để đảm bảo toàn bộ hợp đồng.

## 3. Cách triển khai an toàn: `Integer.compare`, `Long.compare`, `Comparator.comparing`

Với trường số nguyên, cách thông dụng và an toàn nhất là dùng phương thức so sánh chuẩn:

```java
@Override
public int compareTo(Score other) {
    Objects.requireNonNull(other, "other");
    return Integer.compare(this.points, other.points);
}
```

Đây là **phương thức nằm trong lớp `Score` có trường `int points`**, chưa phải chương trình Java độc lập. `Integer.compare` so sánh dựa trên thứ tự số nguyên đúng nghĩa và trả về `-1`, `0` hoặc `1`. Nó không gặp lỗi tràn do tự lấy hiệu.

Nếu so sánh nhiều trường, ví dụ xếp hạng theo điểm tăng dần, sau đó theo mã học viên, có thể dùng bộ so sánh có thứ tự ưu tiên rõ ràng:

```java
record Student(int points, String id) implements Comparable<Student> {
    @Override
    public int compareTo(Student other) {
        Objects.requireNonNull(other, "other");
        int byPoints = Integer.compare(points, other.points);
        if (byPoints != 0) return byPoints;
        return id.compareTo(other.id);
    }
}
```

Đây là **khai báo record minh họa**, có thể đặt độc lập trong tệp thích hợp nếu bổ sung import `java.util.Objects`. Với `id` là chuỗi không null và không thay đổi, so sánh theo điểm trước, rồi theo `id` giúp tạo thứ tự từ điển: mọi trường hợp đối tượng có điểm bằng nhau đều được phân định nhất quán theo mã. `String.compareTo` có thể trả về số có độ lớn lớn hơn 1, nhưng dấu vẫn có đúng ý nghĩa.

Trong API chuyên dùng cho `Comparator`, cũng có thể viết:

```java
Comparator<Student> byPointsThenId =
        Comparator.comparingInt(Student::points)
                  .thenComparing(Student::id);
```

Đây là một biểu thức tạo `Comparator` cho `Student`, cần `import java.util.Comparator`. `Comparator` có quy tắc đảo dấu tương ứng đối với `compare(a,b)` và `compare(b,a)`, dù không cần thay đổi thứ tự tự nhiên mặc định của lớp.

Nếu sắp xếp giảm dần, ưu tiên `Comparator.reversed()` hoặc đổi **thứ tự đối số của phương thức compare chuẩn** thay vì tự đảo dấu một số nguyên tùy ý:

```java
int descending = Integer.compare(other.points, this.points);
```

Đoạn này nằm trong ngữ cảnh có hai giá trị `points`. Việc viết `-someCompareResult` chỉ luôn an toàn khi biết chắc kết quả không thể bằng `Integer.MIN_VALUE`. Với một phương thức `compareTo` tùy ý có quyền trả về số âm đó, phủ định có thể tràn số nguyên và **không đổi dấu**.

## 4. Ví dụ sai: dùng phép trừ để cài đặt `compareTo()`

Một lỗi thường gặp là:

```java
@Override
public int compareTo(BrokenScore other) {
    return points - other.points; // Không an toàn khi int tràn!
}
```

Đây là **phương thức trong lớp `BrokenScore`** của chương trình hoàn chỉnh ở mục 6. Khi hiệu toán học vượt giới hạn của `int`, phép trừ Java bị tràn theo số nguyên 32 bit và kết quả có thể sai dấu.

Hãy xét hai đối tượng có giá trị `0` và `Integer.MIN_VALUE`, tức `-2147483648`:

- Về mặt toán học, `0 > -2147483648` nên so `0` với số nhỏ nhất phải cho **dấu dương**.
- Nhưng phép tính `0 - Integer.MIN_VALUE` trên `int` tràn và cho `-2147483648`, tức **dấu âm**.
- Chiều ngược lại, `Integer.MIN_VALUE - 0` cũng bằng `-2147483648`, **vẫn dấu âm**.
- Như vậy `sgn(a.compareTo(b)) == -sgn(b.compareTo(a))` bị vi phạm: cùng một cặp lại cho hai số âm ở cả hai hướng.

Một lựa chọn chính xác là thay biểu thức trừ bằng `Integer.compare(points, other.points)`. Với `long` dùng `Long.compare`; với `double` và `float`, dùng `Double.compare`/`Float.compare` để có quy tắc chuẩn cho `NaN`, vô cực và `-0.0` thay vì tự viết biểu thức trừ hoặc chuỗi điều kiện đơn giản. So sánh số thực cần hiểu thứ tự do API định nghĩa, vì nó có thể khác phép toán `<`, `>` thông thường ở các giá trị đặc biệt.

Một lỗi khác là viết:

```java
return points > other.points ? 1 : -1;
```

Dòng này trả về `-1` kể cả khi hai điểm bằng nhau, làm mất trường hợp `0`. Nếu dùng cùng hai giá trị bằng nhau ở cả hai chiều, cả hai kết quả là `-1` nên quy tắc đảo dấu cũng sai. Khi thực hiện so sánh nhiều trường, luôn xử lý trường hợp bằng nhau rồi mới xét khóa so sánh tiếp theo.

## 5. Quy tắc đảo dấu nằm trong một hợp đồng lớn hơn

Đảo dấu là điều kiện bắt buộc nhưng chưa đủ để có thứ tự hợp lệ. Cần bảo đảm **tính bắc cầu** (`a > b` và `b > c` thì `a > c`) và **tính nhất quán của nhóm tương đương** (khi `a.compareTo(b) == 0`, hai giá trị phải có cùng dấu khi so với mọi `c` hợp lệ). Một comparator có thể đối dấu đúng theo từng cặp nhưng vẫn tạo vòng `a < b < c < a`.

Khác với `equals` (hai chiều cho cùng boolean), `compareTo` dùng **hai dấu ngược nhau** khi đổi vị trí, ngoại trừ trường hợp cùng trả `0`. Java khuyến nghị `compareTo == 0` nhất quán với `equals`, nhưng đây là yêu cầu thiết kế bổ sung, không phải nội dung của quy tắc đảo dấu. Ví dụ `BigDecimal("2.0")` và `BigDecimal("2.00")` cùng vị trí thứ tự nhưng khác `equals`; điều này tự nó không vi phạm quy tắc đảo dấu.

Để phép so sánh đáng tin cậy qua nhiều lượt, giữ các trường dùng xác định thứ tự ổn định khi đối tượng nằm trong `TreeSet`/`TreeMap`. Phần này định vị các quy tắc liên quan; bài về **bắc cầu**, **giá trị thứ ba** và **equals** phân tích riêng phản ví dụ cùng phép kiểm thử.

## 6. Chương trình Java 17+ hoàn chỉnh để kiểm chứng

Lưu toàn bộ tệp dưới tên **`CompareSignDemo.java`**. Chương trình dùng `record Score` để triển khai `Comparable<Score>` an toàn bằng `Integer.compare`, dùng `BrokenScore` cố ý minh họa phép trừ tràn, và kiểm thử mọi cặp trong một tập giá trị biên.

```java
import java.util.*;

public class CompareSignDemo {
    record Score(int points) implements Comparable<Score> {
        @Override
        public int compareTo(Score other) {
            Objects.requireNonNull(other, "other");
            return Integer.compare(points, other.points);
        }
    }

    static final class BrokenScore implements Comparable<BrokenScore> {
        private final int points;

        BrokenScore(int points) {
            this.points = points;
        }

        @Override
        public int compareTo(BrokenScore other) {
            return points - other.points; // Lỗi cố ý: overflow.
        }
    }

    static void checkSignRule(Score[] scores) {
        for (Score left : scores) {
            for (Score right : scores) {
                int lr = Integer.signum(left.compareTo(right));
                int rl = Integer.signum(right.compareTo(left));

                if (lr != -rl) {
                    throw new AssertionError("sign rule broken");
                }
            }
        }
    }

    public static void main(String[] args) {
        Score low = new Score(Integer.MIN_VALUE);
        Score zero = new Score(0);
        Score equal = new Score(0);

        System.out.println("low vs zero = " + low.compareTo(zero));
        System.out.println("zero vs low = " + zero.compareTo(low));
        System.out.println("zero vs equal = " + zero.compareTo(equal));

        checkSignRule(new Score[] {
                low, new Score(-7), zero,
                new Score(17), new Score(Integer.MAX_VALUE)
        });
        System.out.println("correct sign rule = PASS");

        BrokenScore badZero = new BrokenScore(0);
        BrokenScore badLow = new BrokenScore(Integer.MIN_VALUE);
        int brokenForward = badZero.compareTo(badLow);
        int brokenReverse = badLow.compareTo(badZero);

        System.out.println("broken zero vs min = " + brokenForward);
        System.out.println("broken min vs zero = " + brokenReverse);
        System.out.println("broken sign rule = "
                + (Integer.signum(brokenForward)
                        == -Integer.signum(brokenReverse)));

        TreeSet<Score> set = new TreeSet<>();
        set.add(new Score(4));
        set.add(new Score(-2));
        set.add(new Score(4));
        System.out.println("sorted set = " + set);
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac CompareSignDemo.java
java CompareSignDemo
```

Kết quả chính xác của chương trình:

```text
low vs zero = -1
zero vs low = 1
zero vs equal = 0
correct sign rule = PASS
broken zero vs min = -2147483648
broken min vs zero = -2147483648
broken sign rule = false
sorted set = [Score[points=-2], Score[points=4]]
```

Giải thích:

1. `low.compareTo(zero)` trả `-1` vì `Integer.MIN_VALUE < 0`; đảo thứ tự thành `zero.compareTo(low)` trả `1`. Hai dấu đối nhau.
2. `zero.compareTo(equal)` trả `0` vì cùng điểm số. Đảo thứ tự vẫn cho `0`.
3. `checkSignRule` duyệt **toàn bộ 25 cặp** trong 5 giá trị, bao gồm cặp có cùng tham chiếu, cùng giá trị, âm/dương, và các biên `Integer.MIN_VALUE` / `Integer.MAX_VALUE`. Nếu có cặp sai quy tắc, hàm ném `AssertionError`; vì không có lỗi nên in `PASS`.
4. `BrokenScore` dùng phép trừ. `0 - Integer.MIN_VALUE` bị tràn thành `-2147483648`, trong khi chiều ngược lại cũng cho cùng số âm. Do đó `broken sign rule = false`. Đây là ví dụ có kết quả xác định, không phụ thuộc ngẫu nhiên.
5. `TreeSet` chứa các `Score` theo thứ tự tăng dần. Hai đối tượng có `points == 4` được `compareTo` đánh giá bằng 0, nên cây chỉ giữ một phần tử tương đương theo thứ tự. `record` sinh `toString` hiển thị dạng `Score[points=...]`.

**Lưu ý khi kiểm thử:** Đừng viết khẳng định `left.compareTo(right) == -right.compareTo(left)` cho một implementation tổng quát, vì việc phủ định `Integer.MIN_VALUE` bị tràn và hợp đồng chỉ quy định dấu. Cách đúng là so `Integer.signum(left.compareTo(right))` với `-Integer.signum(right.compareTo(left))`. `Integer.signum` chỉ cho giá trị trong {-1, 0, 1} nên phủ định an toàn.

## 7. Những lỗi phổ biến khi viết và sử dụng `compareTo()`

| Lỗi | Hệ quả | Cách khắc phục |
| --- | --- | --- |
| `return this.x - other.x;` với `int` | Tràn số nguyên làm sai dấu, có thể vi phạm đảo dấu | `Integer.compare(this.x, other.x)` |
| Dùng `-compareResult` với kết quả có thể bằng `Integer.MIN_VALUE` | Không đổi được dấu vì tràn | Dùng `Integer.signum` để kiểm tra; đổi thứ tự tham số `compare` hoặc `Comparator.reversed` để đảo thứ tự |
| Chỉ trả `1` hoặc `-1`, không có trường hợp `0` | Hai giá trị bằng nhau vẫn bị đặt trước nhau ở cả hai chiều | Xử lý trường hợp bằng nhau; dùng API compare chuẩn |
| Giả sử kết quả chỉ là `-1`, `0`, `1` | Sai với các bộ so sánh hợp lệ trả `-8`, `42`,... | Chỉ kiểm tra dấu: `< 0`, `== 0`, `> 0` |
| `compareTo` phụ thuộc dữ liệu thay đổi khi đã đưa vào `TreeSet` | Vị trí lưu trong cây không còn phù hợp thứ tự mới | Dùng khóa bất biến hoặc xóa và thêm lại |
| Dùng cùng kiểu dữ liệu nhưng tiêu chí so sánh không bắc cầu | Thuật toán sắp xếp nhận thứ tự mâu thuẫn | Kiểm thử cả bộ ba và đặt tiêu chí từ điển nhất quán |
| `compareTo` trả `0` cho hai đối tượng mà `equals` coi là khác | `TreeSet/TreeMap` có thể gộp chúng theo thứ tự | Cố gắng nhất quán với `equals` hoặc ghi rõ ngoại lệ |
| Không xử lý `null` hoặc kiểu không thể so sánh | Có thể ném ngoại lệ sai loại hoặc hành vi khó đoán | Theo hợp đồng `Comparable`: `NullPointerException` khi so với null; loại không tương thích có thể gây `ClassCastException` |

## 8. Bài tự kiểm tra và quy trình rà soát

**Tình huống A.** `a.compareTo(b)` trả `-12`. Chiều đảo `b.compareTo(a)` có bắt buộc trả `12` không?

Không. Nó chỉ phải **dương**, ví dụ `1` hoặc `12`, miễn là toàn bộ các quy tắc còn lại của thứ tự cũng được tuân thủ.

**Tình huống B.** Với `a = 0` và `b = Integer.MIN_VALUE`, code `return a - b;` cho kết quả nào?

Kết quả `int` bị tràn thành `Integer.MIN_VALUE` (âm), dù 0 lớn hơn giá trị nhỏ nhất. Chiều ngược lại cũng âm, trực tiếp vi phạm quy tắc đảo dấu.

**Tình huống C.** Nếu `a.compareTo(b) == 0` và `b.compareTo(c) > 0`, điều kiện nhất quán với giá trị 0 suy ra điều gì?

`a.compareTo(c)` cũng phải có **dấu dương**. Hai phần tử tương đương theo thứ tự phải so sánh với bên thứ ba theo cùng cách. Nếu kết quả lại âm hoặc bằng 0, implementation vi phạm hợp đồng.

**Tình huống D.** Nếu `a.compareTo(b) == 0` nhưng `a.equals(b)` là `false`, có tự động làm sai quy tắc đảo dấu không?

Không. Quy tắc đảo dấu vẫn có thể được giữ. Nhưng thứ tự đó **không nhất quán với `equals`**, điều thường gây bất ngờ ở `TreeSet` và `TreeMap`.

Khi review một implementation `compareTo`, hãy làm lần lượt: xác định tất cả trường và thứ tự ưu tiên khi so; dùng `Integer.compare`/`Long.compare` hoặc API có hợp đồng rõ; kiểm tra trường hợp 0; kiểm thử **hai chiều cho mọi cặp**; kiểm thử **bắc cầu với bộ ba**; kiểm thử các giá trị biên và tình huống dữ liệu có thể thay đổi; cuối cùng xác nhận quan hệ giữa `compareTo == 0` và `equals` có đúng với thiết kế khóa/tập hay không.

**Ghi nhớ:** `Comparable.compareTo` trả về một **dấu thứ tự**, không phải khoảng cách giữa hai giá trị. Đổi vị trí đối tượng phải đổi dấu kết quả; dùng phép so sánh chuẩn và kiểm tra toàn bộ hợp đồng để giữ thứ tự tự nhiên nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-004">Tính bắc cầu của quan hệ thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Tính bắc cầu của quan hệ thứ tự trong Java

## 1. Tính bắc cầu nghĩa là gì?

Khi các đối tượng có **thứ tự tự nhiên** thông qua `Comparable<T>.compareTo(T)`, thứ tự phải có quy tắc nhất quán. Một trong những yêu cầu quan trọng nhất là **tính bắc cầu (transitivity)**:

> Nếu `a` đứng sau `b` và `b` đứng sau `c`, thì `a` cũng phải đứng sau `c`.

Với kết quả `compareTo`, điều đó được diễn đạt bằng dấu:

~~~text
a.compareTo(b) > 0
b.compareTo(c) > 0
----------------
a.compareTo(c) > 0
~~~

Tương tự, nếu `a.compareTo(b) < 0` và `b.compareTo(c) < 0` thì `a.compareTo(c) < 0`. Ở đây **số âm** nghĩa là đứng trước, **0** nghĩa là tương đương theo thứ tự và **số dương** nghĩa là đứng sau; Java không yêu cầu kết quả đúng bằng `-1` hoặc `1`.

Ví dụ, nếu ba điểm số có quan hệ `70 < 80 < 90`, chương trình không được có một quy tắc so sánh làm `90 < 70`. Nếu điều đó xảy ra, không thể sắp ba giá trị vào một trật tự tuyến tính nhất quán.

Tính bắc cầu cần được bảo đảm cho **mọi bộ ba đối tượng hợp lệ**, không chỉ trong một vài bộ dữ liệu kiểm thử. Nó quan trọng với việc sắp xếp, tìm kiếm nhị phân và các tập/bản đồ có thứ tự như `TreeSet`, `TreeMap`.

## 2. Hợp đồng của compareTo còn yêu cầu những gì?

Để định nghĩa một thứ tự tự nhiên đúng, chỉ kiểm tra riêng bắc cầu chưa đủ. Theo hợp đồng `Comparable`, cần xét thêm các quy tắc sau.

| Yêu cầu | Diễn giải | Ví dụ |
| --- | --- | --- |
| Đổi chiều so sánh | `sgn(x.compareTo(y)) == -sgn(y.compareTo(x))` | Nếu x đứng sau y thì y đứng trước x |
| Bắc cầu | Nếu `x > y` và `y > z` thì `x > z` | 90 > 80 > 70 ⇒ 90 > 70 |
| Đối tượng tương đương theo thứ tự | Nếu `x.compareTo(y) == 0` thì với mọi z, `sgn(x.compareTo(z)) == sgn(y.compareTo(z))` | Hai giá trị ngang hạng phải có cùng quan hệ trước/sau với mọi giá trị khác |
| Phù hợp với `equals` | Nên có `x.compareTo(y) == 0` khi và chỉ khi `x.equals(y)` | Các collection theo thứ tự xử lý việc trùng khóa theo `compareTo` |

Ký hiệu `sgn` chỉ **dấu** của một số nguyên: âm thành `-1`, bằng không thành `0`, dương thành `1`. Điều kiện đổi chiều ngụ ý `x.compareTo(x)` phải bằng `0`.

Việc **nhất quán với `equals`** được khuyến nghị mạnh mẽ nhưng không bắt buộc trong mọi lớp `Comparable`. Ví dụ nổi tiếng là `BigDecimal`: `new BigDecimal("4.0").compareTo(new BigDecimal("4.00")) == 0`, trong khi `equals` trả `false` vì biểu diễn scale khác nhau. Điều này không vi phạm tính bắc cầu; nó cho thấy **tương đương theo thứ tự** và **bình đẳng theo `equals`** là hai quan hệ có thể khác nhau.

Điều kiện “bằng 0 thì cùng quan hệ với mọi z” đặc biệt quan trọng: nếu một đối tượng được coi là bằng hạng với đối tượng thứ hai nhưng lại đứng khác phía so với đối tượng thứ ba, thuật toán sắp xếp sẽ nhận các kết quả mâu thuẫn.

## 3. Xây dựng thứ tự tự nhiên đúng bằng các khóa so sánh

Giả sử một kết quả thi có hai thuộc tính:

- `score`: điểm, tăng dần.
- `candidate`: tên, dùng để phân xử khi cùng điểm theo thứ tự từ điển.

Một thứ tự rõ ràng là **so điểm trước; nếu điểm bằng nhau mới so tên**. Đây là cách so sánh theo thứ tự từ điển của cặp khóa `(score, candidate)`. Khi mỗi thành phần dùng một quan hệ thứ tự nhất quán, phép so theo thứ tự ưu tiên cố định cũng giữ tính bắc cầu.

Một số quy tắc hữu ích:

1. Chọn các trường định nghĩa thứ tự và **thứ tự ưu tiên của các trường** ngay từ đầu.
2. Dùng `Integer.compare`, `Long.compare`, `String.compareTo` hoặc `Comparator.comparing(...).thenComparing(...)` thay vì tự tính dấu bằng phép trừ dễ tràn số.
3. Nếu khóa chính khác nhau, trả về kết quả của khóa chính ngay; chỉ xét khóa phụ khi khóa chính bằng nhau.
4. Đảm bảo các trường tham gia so sánh có trạng thái ổn định khi đối tượng đang nằm trong `TreeSet` hoặc làm khóa của `TreeMap`.
5. Xác định rõ `null` hợp lệ hay không. Nếu một trường có thể `null`, hãy dùng quy tắc `nullsFirst` hoặc `nullsLast` nhất quán, hoặc xác thực không được `null` khi tạo.

## 4. Ví dụ Java 17 chạy được

Chương trình gồm một lớp `ExamResult` có thứ tự tự nhiên bắc cầu, một comparator mang tính vòng tròn để minh họa vi phạm, và một phép so sai dùng phép trừ có nguy cơ tràn `int`.

Lưu vào tệp **TransitiveOrderDemo.java**:

~~~java
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class TransitiveOrderDemo {
    static final class ExamResult implements Comparable<ExamResult> {
        private final int score;
        private final String candidate;

        ExamResult(int score, String candidate) {
            this.score = score;
            this.candidate = Objects.requireNonNull(candidate, "candidate");
        }

        @Override
        public int compareTo(ExamResult other) {
            int byScore = Integer.compare(score, other.score);
            if (byScore != 0) {
                return byScore;
            }
            return candidate.compareTo(other.candidate);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof ExamResult that
                            && score == that.score
                            && candidate.equals(that.candidate));
        }

        @Override
        public int hashCode() {
            return Objects.hash(score, candidate);
        }

        @Override
        public String toString() {
            return candidate + ":" + score;
        }
    }

    enum Move { ROCK, PAPER, SCISSORS }

    static final Comparator<Move> CYCLIC = (left, right) -> {
        if (left == right) {
            return 0;
        }
        boolean leftWins =
                (left == Move.ROCK && right == Move.SCISSORS)
                || (left == Move.SCISSORS && right == Move.PAPER)
                || (left == Move.PAPER && right == Move.ROCK);
        return leftWins ? 1 : -1;
    };

    public static void main(String[] args) {
        ExamResult a = new ExamResult(70, "An");
        ExamResult b = new ExamResult(80, "Binh");
        ExamResult c = new ExamResult(90, "Chi");

        System.out.println("a < b: " + (a.compareTo(b) < 0));
        System.out.println("b < c: " + (b.compareTo(c) < 0));
        System.out.println("a < c: " + (a.compareTo(c) < 0));

        ExamResult tiedA = new ExamResult(80, "An");
        System.out.println("tie by name: "
                + (tiedA.compareTo(b) < 0));
        System.out.println("zero agrees with equals: "
                + (b.compareTo(new ExamResult(80, "Binh")) == 0
                        && b.equals(new ExamResult(80, "Binh"))));

        Set<ExamResult> results = new TreeSet<>();
        results.add(c);
        results.add(a);
        results.add(b);
        results.add(new ExamResult(80, "Binh"));
        System.out.println("TreeSet: " + results);

        System.out.println("rock > scissors: "
                + (CYCLIC.compare(Move.ROCK, Move.SCISSORS) > 0));
        System.out.println("scissors > paper: "
                + (CYCLIC.compare(Move.SCISSORS, Move.PAPER) > 0));
        System.out.println("rock > paper: "
                + (CYCLIC.compare(Move.ROCK, Move.PAPER) > 0));

        int high = Integer.MAX_VALUE;
        int middle = 0;
        int low = -1;
        System.out.println("subtract high > middle: "
                + (high - middle > 0));
        System.out.println("subtract middle > low: "
                + (middle - low > 0));
        System.out.println("subtract high > low: "
                + (high - low > 0));
        System.out.println("Integer.compare high > low: "
                + (Integer.compare(high, low) > 0));
    }
}
~~~

Biên dịch/chạy trên JDK 17 trở lên:

~~~bash
javac TransitiveOrderDemo.java
java TransitiveOrderDemo
~~~

Kết quả chuẩn:

~~~text
a < b: true
b < c: true
a < c: true
tie by name: true
zero agrees with equals: true
TreeSet: [An:70, Binh:80, Chi:90]
rock > scissors: true
scissors > paper: true
rock > paper: false
subtract high > middle: true
subtract middle > low: true
subtract high > low: false
Integer.compare high > low: true
~~~

### Giải thích

- Ba `ExamResult` có điểm `70 < 80 < 90`, do đó `a < b`, `b < c` và `a < c` đều đúng. Kết quả tuân quy tắc bắc cầu.
- Khi điểm đều là `80`, chương trình dùng tên làm khóa thứ cấp. Tên `"An"` đứng trước `"Binh"`, nên phép phân xử không mâu thuẫn với quan hệ theo điểm.
- `TreeSet` sử dụng `compareTo` để sắp xếp và xác định tính trùng nhau theo thứ tự. Bản sao `ExamResult(80, "Binh")` không tạo thêm một phần tử mới; kết quả duyệt được hiển thị tăng dần.
- `ExamResult` là `final`, các trường tham gia so sánh là `final`; `equals` và `hashCode` dùng chính cặp `(score, candidate)`, nên các hợp đồng nhất quán với nhau.
- `CYCLIC` coi ROCK đứng sau SCISSORS, SCISSORS đứng sau PAPER, nhưng PAPER đứng sau ROCK. Nó thể hiện logic trò chơi kéo–búa–bao, **không phải một quan hệ thứ tự tuyến tính**. Hai so sánh đầu là `true` nhưng kết luận bắc cầu là `false`.
- Phép trừ `high - low` làm tràn `int` vì `Integer.MAX_VALUE - (-1)` vượt giới hạn số nguyên. Kết quả dấu bị đảo, tạo bộ ba `high > middle > low` nhưng phép trừ lại báo `high` không lớn hơn `low`. `Integer.compare` xử lý trường hợp này đúng.

Ví dụ cố ý **không đưa comparator vòng tròn vào `TreeSet`**: nếu vi phạm hợp đồng, những kết quả sắp xếp/tìm kiếm sẽ không còn đáng tin. Việc không thấy lỗi hoặc ngoại lệ trong một lần chạy không chứng minh comparator hợp lệ.

## 5. Vì sao quy tắc “kéo–búa–bao” không thể là thứ tự tự nhiên?

Quan hệ “thắng” trong kéo–búa–bao có vòng:

~~~text
ROCK > SCISSORS
SCISSORS > PAPER
PAPER > ROCK
~~~

Nếu cố hiểu dấu `>` là “đứng sau theo thứ tự tự nhiên”, từ hai dòng đầu bắt buộc suy ra `ROCK > PAPER`. Nhưng dòng cuối khẳng định `PAPER > ROCK`. Vì vậy quan hệ này **không bắc cầu** và không thể sử dụng làm thứ tự tự nhiên đúng hợp đồng.

Một quan hệ nghiệp vụ có thể hợp lý mà **không phải quan hệ thứ tự**. Nếu muốn biểu diễn “đối thủ nào thắng”, nên dùng một phương thức như `beats(other)` trả `boolean` hoặc một kiểu kết quả chiến thắng riêng. Chỉ tạo `Comparator` khi thật sự có một thứ tự nhất quán mà thuật toán sắp xếp có thể dùng.

Nếu muốn hiển thị ba lựa chọn theo một thứ tự cố định, có thể sắp bằng mã/thứ bậc được xác định trước (ví dụ ROCK, PAPER, SCISSORS). Thứ tự hiển thị đó khác với quan hệ ai thắng ai.

## 6. Tác động khi vi phạm tính bắc cầu

### 6.1. Sắp xếp

`Arrays.sort`, `List.sort` và `Collections.sort` dựa vào comparator hoặc `compareTo` biểu thị thứ tự nhất quán. Một comparator không bắc cầu có thể khiến kết quả khó dự đoán hoặc làm thuật toán sắp xếp phát hiện vi phạm và ném `IllegalArgumentException` trong một số trường hợp. **Không nên trông chờ JVM luôn ném ngoại lệ**: có khi dữ liệu nhỏ vẫn được sắp và trông có vẻ đúng, nhưng không có bảo đảm về trật tự hợp lệ đối với comparator sai.

### 6.2. TreeSet và TreeMap

`TreeSet` và `TreeMap` dùng quan hệ thứ tự để tổ chức, tìm kiếm và xác định các khóa được coi là tương đương. Vi phạm hợp đồng có thể làm `contains`, `get`, `remove` hoặc việc duyệt theo thứ tự đưa ra kết quả không phù hợp kỳ vọng. Các collection này giả định rằng kết quả so sánh đủ ổn định và nhất quán để tìm được một vị trí trong cây.

Đặc biệt, `TreeSet`/ `TreeMap` xem **`compare(a, b) == 0`** hoặc **`a.compareTo(b) == 0`** là quan hệ tương đương khi xác định phần tử/khóa trùng nhau. Nếu quan hệ này không phù hợp với `equals`, collection vẫn có thể hoạt động theo quy tắc so sánh nhưng vi phạm kỳ vọng của hợp đồng `Set`/`Map` tổng quát. Vì vậy, nên bảo đảm sự phù hợp với `equals` hoặc ghi rõ chủ ý khác biệt.

### 6.3. Tìm kiếm nhị phân

Thuật toán tìm kiếm nhị phân cần dãy được sắp đúng theo cùng quy tắc so sánh được dùng khi tìm. Một comparator không bắc cầu khiến việc chia đôi phạm vi tìm kiếm mất cơ sở: không thể tin rằng những phần tử ở phía trái hoặc phải đều có quan hệ nhất quán với khóa đích.

Không thể sửa một quan hệ sai bằng cách đảo dấu tùy tiện hoặc lặp lại phép so sánh nhiều lần. Cần sửa **định nghĩa thứ tự**.

## 7. Những cách viết compareTo dễ mắc lỗi

### 7.1. Trừ trực tiếp hai giá trị nguyên

~~~java
@Override
public int compareTo(Score other) {
    return score - other.score; // Có thể tràn int
}
~~~

Đây là **đoạn minh họa lỗi trong lớp Score giả định**, không phải chương trình độc lập. Nếu hiệu số tràn `int`, dấu kết quả có thể sai, kéo theo vi phạm quy tắc đổi chiều hoặc bắc cầu. Thay bằng:

~~~java
@Override
public int compareTo(Score other) {
    return Integer.compare(score, other.score);
}
~~~

Với `long` cũng nên dùng `Long.compare` thay vì ép hiệu số về `int`. Không viết comparator chỉ trả `0` khi hai số có cùng dấu vì cách đó làm các giá trị khác nhau có thể bị coi là ngang hạng và không tương thích với chính sách bình đẳng đã chọn.

### 7.2. So bằng dữ liệu thay đổi theo thời gian

Một phương thức `compareTo` đọc đồng hồ, gọi dịch vụ ngoài hoặc dựa trên trường được sửa trong lúc phần tử đã nằm trong `TreeSet` có thể trả kết quả khác nhau cho cùng cặp đối tượng. Đây là vấn đề về **tính nhất quán qua các lần so sánh**, có thể làm hỏng giả định tìm kiếm của cây dù công thức bắc cầu trông đúng trong từng thời điểm.

Hãy ưu tiên khóa xếp hạng ổn định. Nếu thứ tự cần thay đổi, xóa mục khỏi collection khi vẫn còn nhận diện được bằng trạng thái cũ, cập nhật dữ liệu, rồi chèn lại theo trạng thái mới; trong ứng dụng nhiều luồng cần phối hợp đồng bộ cho toàn thao tác.

### 7.3. Đảo thứ tự ưu tiên trường theo điều kiện khác nhau

Ví dụ, lúc so `a` với `b` thì ưu tiên điểm, lúc so `b` với `c` lại ưu tiên tên vì một cờ nghiệp vụ tùy đối tượng. Nếu việc chọn trường ưu tiên phụ thuộc **vào cặp đang so**, thay vì một thứ tự ưu tiên toàn cục nhất quán, có thể xuất hiện vòng `a < b < c < a`.

Giải pháp thường là chọn một tuple khóa cố định, chẳng hạn `(priority, createdAt, id)`, rồi so theo thứ tự từ trái sang phải. Việc thêm khóa `id` cuối cùng cũng có thể dùng để phá hòa khi cần **thứ tự toàn phần có tính xác định**, nhưng cần xác định rõ khóa nào là một phần của bình đẳng theo thứ tự.

### 7.4. Quên chính sách null

Theo `Comparable`, `compareTo(null)` thông thường ném `NullPointerException`, ngay cả khi `equals(null)` trả `false`. Nếu một **trường bên trong** có thể `null`, phải chọn cách xử lý tường minh trong comparator, ví dụ:

~~~java
Comparator<String> nullableNames =
        Comparator.nullsLast(String::compareTo);
~~~

Đây là **đoạn khai báo comparator**, không phải chương trình độc lập. Nó xếp `null` sau chuỗi không `null`. Dùng chính sách này nhất quán trong toàn bộ quy tắc sắp xếp; đừng ngẫu nhiên coi `null` nhỏ hơn trong một nhánh và lớn hơn trong nhánh khác.

## 8. Kiểm tra tính bắc cầu khi viết hoặc sửa một lớp Comparable

Một bộ kiểm thử nhỏ cần đối chiếu cả trường hợp thông thường lẫn các ranh giới:

1. **Đổi chiều:** với các cặp `a, b`, dấu của `a.compareTo(b)` phải đối nghịch dấu của `b.compareTo(a)`. Kiểm tra cả `a.compareTo(a) == 0`.
2. **Bắc cầu tăng và giảm:** với mọi bộ ba mẫu có `a < b < c`, kiểm tra `a < c`; và với `a > b > c`, kiểm tra `a > c`.
3. **Trường hợp ngang hạng:** nếu `a.compareTo(b) == 0`, hai đối tượng phải có cùng dấu so sánh với mọi `c` mẫu.
4. **Nhiều khóa:** tạo những cặp cùng khóa chính nhưng khác khóa phụ, và những cặp khác khóa chính. Bảo đảm phép phá hòa hoạt động theo thứ tự ưu tiên cố định.
5. **Giá trị biên:** số âm, `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, chuỗi rỗng, `null` nếu thuộc tính cho phép.
6. **Sự nhất quán với equals:** kiểm tra `compareTo == 0` có phù hợp `equals` theo thiết kế không. Nếu chủ ý khác, viết rõ lý do và tác động tới `TreeSet`/`TreeMap`.
7. **Tính ổn định:** cùng các đối tượng không đổi phải cho dấu kết quả giống nhau qua nhiều lần so sánh.

Một hàm hỗ trợ kiểm tra có thể được viết trong lớp kiểm thử:

~~~java
static <T extends Comparable<? super T>> boolean transitiveLess(
        T a, T b, T c) {
    return !(a.compareTo(b) < 0 && b.compareTo(c) < 0)
            || a.compareTo(c) < 0;
}
~~~

Đây là **phương thức minh họa kiểm thử**, không phải chương trình độc lập. Nếu tiền đề `a < b` và `b < c` không đồng thời đúng, kiểm tra suy diễn này trả `true` vì không có vi phạm cho bộ ba đó. Để kiểm chứng đủ mạnh, cần tạo nhiều bộ ba bao gồm cả tiền đề đúng và đảo chiều. Kiểm thử theo tính chất (property-based testing) có thể hỗ trợ tạo dữ liệu ngẫu nhiên và giá trị biên, nhưng không thay thế việc thiết kế đúng quan hệ thứ tự từ đầu.

**Ghi nhớ:** Trong Java, một quan hệ thứ tự dùng bởi `Comparable` hoặc `Comparator` phải **bắc cầu**: nếu `a > b` và `b > c` thì `a > c`. Hãy chọn các khóa so sánh có thứ tự ưu tiên ổn định, dùng các hàm `compare` tránh tràn số và không đưa quan hệ có vòng vào các thuật toán sắp xếp hoặc collection theo thứ tự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-005">Hai giá trị compareTo() bằng 0 phải được so sánh nhất quán với giá trị thứ ba</a>

<details>
<summary>Xem chi tiết</summary>

# Hai giá trị `compareTo()` bằng `0` phải được so sánh nhất quán với giá trị thứ ba

## 1. Quy tắc của thứ tự tự nhiên trong Java

Trong Java, một lớp triển khai `Comparable<T>` định nghĩa **thứ tự tự nhiên** (natural ordering) qua phương thức `int compareTo(T other)`. Phương thức trả một số nguyên:

- **Âm** nếu đối tượng hiện tại đứng trước `other`.
- **`0`** nếu hai giá trị được coi là tương đương theo **thứ tự tự nhiên**.
- **Dương** nếu đối tượng hiện tại đứng sau `other`.

Các giá trị trả về không bắt buộc là `-1`, `0`, `1`; chỉ **dấu** mới có ý nghĩa đối với hợp đồng. Ví dụ `String.compareTo` có thể trả một số âm hoặc dương có trị tuyệt đối lớn hơn `1`.

Điểm quan trọng của bài học: Nếu `x.compareTo(y) == 0`, thì **với mọi `z` có thể so sánh hợp lệ**, dấu của `x.compareTo(z)` và `y.compareTo(z)` phải giống nhau. Nghĩa là, một đối tượng thứ ba không được thấy `x` đứng trước nó nhưng `y` lại đứng sau nó.

Viết chính xác bằng ký hiệu:

```text
x.compareTo(y) == 0
    => signum(x.compareTo(z)) == signum(y.compareTo(z))
       với mọi z thuộc cùng miền so sánh hợp lệ
```

`signum(n)` là dấu của `n`: âm thành `-1`, bằng không thành `0`, dương thành `1`. Trong Java dùng `Integer.signum(n)` để kiểm tra. Hợp đồng **không yêu cầu** `x.compareTo(z)` bằng đúng giá trị số nguyên trả về bởi `y.compareTo(z)`, mà chỉ yêu cầu hai giá trị trả về có **cùng dấu**.

Quy tắc này còn được gọi là **tính thay thế của các giá trị tương đương theo thứ tự**: đã coi `x` và `y` cùng vị trí trong thứ tự, ta phải có thể dùng chúng thay cho nhau trong mọi phép so sánh với `z`.

## 2. Vì sao cần so sánh nhất quán với giá trị thứ ba?

Hãy tưởng tượng có ba giá trị `x`, `y`, `z`:

```text
x.compareTo(y) == 0    // x và y được xếp cùng vị trí
x.compareTo(z) < 0     // x đứng trước z
y.compareTo(z) > 0     // y đứng sau z
```

Ba kết luận trên **mâu thuẫn**: nếu `x` và `y` tương đương theo thứ tự, chúng không thể cùng lúc ở hai phía đối nghịch của `z`. Những thuật toán sắp xếp, tìm nhị phân, cây cân bằng (`TreeSet`, `TreeMap`) và các cấu trúc dùng thứ tự dựa trên giả định rằng các phép so sánh tạo nên một thứ tự hợp lệ.

Không tuân thủ có thể làm kết quả phụ thuộc đường đi của thuật toán: giá trị vừa chèn lại khó tìm, kết quả `contains()` bất ngờ, hoặc những thao tác sắp xếp phức tạp gặp lỗi do hợp đồng so sánh bị vi phạm. Không nên suy đoán một thứ tự cụ thể của `TreeSet` khi triển khai `compareTo` đã sai.

## 3. Những yêu cầu liên quan của `Comparable`

Quy tắc về giá trị thứ ba thuộc hợp đồng lớn hơn. Với các `x`, `y`, `z` cùng miền so sánh hợp lệ:

**Đối xứng về dấu:**

```text
signum(x.compareTo(y)) == -signum(y.compareTo(x))
```

**Bắc cầu:** Nếu `x > y` và `y > z` theo `compareTo`, thì `x > z`; tương tự phía nhỏ hơn.

```text
x.compareTo(y) > 0  &&  y.compareTo(z) > 0
    => x.compareTo(z) > 0
```

**Nhất quán của lớp tương đương với giá trị thứ ba:**

```text
x.compareTo(y) == 0
    => signum(x.compareTo(z)) == signum(y.compareTo(z))
```

Từ các tính chất này, quan hệ `compareTo() == 0` có vai trò một **quan hệ tương đương** trong phép xếp thứ tự: các thành viên trong cùng một nhóm không thể bị một giá trị thứ ba phân biệt về vị trí.

Đừng dùng `return this.score - other.score` để cài đặt so sánh số nguyên: phép trừ có thể **tràn số**, làm đảo dấu và vi phạm hợp đồng. Hãy dùng `Integer.compare(this.score, other.score)` (tương tự với `Long.compare`, `Double.compare` khi phù hợp).

## 4. Ví dụ sai: coi cùng tên là bằng nhau nhưng so tên khác theo tuổi

Giả sử lớp `BadPerson` viết `compareTo` như sau:

```java
@Override
public int compareTo(BadPerson other) {
    if (name.equals(other.name)) {
        return 0;
    }
    return Integer.compare(age, other.age);
}
```

Đây là **một phương thức minh họa trong lớp** có các trường `name` và `age`; chưa phải chương trình độc lập. Ý định có vẻ hợp lý: cùng tên thì như nhau, khác tên thì xếp theo tuổi. Nhưng tiêu chí hai nhánh không tạo ra một thứ tự nhất quán.

Xét ba người:

- `x = ("An", 20)`
- `y = ("An", 30)`
- `z = ("Binh", 25)`

Kết quả so sánh:

| Phép so sánh | Kết quả | Lý do |
| --- | --- | --- |
| `x.compareTo(y)` | `0` | Cùng tên `"An"` |
| `x.compareTo(z)` | `-1` | Khác tên và `20 < 25` |
| `y.compareTo(z)` | `1` | Khác tên và `30 > 25` |

Vì `x` và `y` đã so sánh bằng `0`, cả hai **phải có cùng dấu khi so với `z`**. Một bên `-1`, một bên `1`: đây là lỗi hợp đồng, **dù từng phép so sánh riêng trông có lý**. Lỗi gốc là điều kiện nhận diện “tương đương” dùng tên, còn thứ tự tương quan với người khác lại dùng tuổi.

## 5. Hai cách sửa tùy theo mục tiêu

**Cách A — muốn cùng tên được xem như một nhóm theo thứ tự:** Hãy so sánh **chỉ theo tên** trong mọi trường hợp:

```java
@Override
public int compareTo(BadPerson other) {
    return name.compareTo(other.name);
}
```

Giờ `("An", 20)` và `("An", 30)` cùng cho `0`, còn khi so với `("Binh", 25)`, cả hai đều có dấu âm. Điều kiện với phần tử thứ ba được giữ vững. Tuy nhiên, nếu `equals()` của lớp vẫn xét cả tuổi, thì cách này có thể **không nhất quán với `equals()`** — cần xem phần 7.

**Cách B — muốn phân biệt cả tên và tuổi:** Dùng thứ tự ghép tiêu chí một cách thống nhất: so tên trước; nếu tên bằng nhau, so tuổi.

```java
@Override
public int compareTo(GoodPerson other) {
    int byName = name.compareTo(other.name);
    if (byName != 0) {
        return byName;
    }
    return Integer.compare(age, other.age);
}
```

Khi đó `("An", 20)` và `("An", 30)` **không** còn so sánh bằng `0`, vì tuổi khác nhau. Nếu hai đối tượng có cùng tên **và** cùng tuổi, chúng so sánh bằng `0` và cho dấu như nhau với mọi người thứ ba. Đây cũng là cách làm mà `Comparator.comparing(Person::name).thenComparingInt(Person::age)` thể hiện.

## 6. Chương trình Java 17+ hoàn chỉnh

Lưu mã sau vào `CompareToThirdDemo.java`:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class CompareToThirdDemo {
    record BadPerson(String name, int age)
            implements Comparable<BadPerson> {
        @Override
        public int compareTo(BadPerson other) {
            if (name.equals(other.name)) return 0;
            return Integer.compare(age, other.age);
        }
    }

    record GoodPerson(String name, int age)
            implements Comparable<GoodPerson> {
        @Override
        public int compareTo(GoodPerson other) {
            int byName = name.compareTo(other.name);
            return byName != 0
                    ? byName
                    : Integer.compare(age, other.age);
        }
    }

    public static void main(String[] args) {
        BadPerson x = new BadPerson("An", 20);
        BadPerson y = new BadPerson("An", 30);
        BadPerson z = new BadPerson("Binh", 25);

        System.out.println("bad x-y: "
                + Integer.signum(x.compareTo(y)));
        System.out.println("bad x-z: "
                + Integer.signum(x.compareTo(z)));
        System.out.println("bad y-z: "
                + Integer.signum(y.compareTo(z)));
        System.out.println("bad third consistency: "
                + (Integer.signum(x.compareTo(z))
                == Integer.signum(y.compareTo(z))));

        GoodPerson a = new GoodPerson("An", 20);
        GoodPerson b = new GoodPerson("An", 20);
        GoodPerson c = new GoodPerson("Binh", 25);
        System.out.println("good a-b: "
                + Integer.signum(a.compareTo(b)));
        System.out.println("good a-c: "
                + Integer.signum(a.compareTo(c)));
        System.out.println("good b-c: "
                + Integer.signum(b.compareTo(c)));
        System.out.println("good equals: " + a.equals(b));

        BigDecimal p = new BigDecimal("2.0");
        BigDecimal q = new BigDecimal("2.00");
        BigDecimal r = new BigDecimal("3.0");
        System.out.println("decimal p-q: " + p.compareTo(q));
        System.out.println("decimal p.equals(q): " + p.equals(q));
        System.out.println("decimal p-r sign: "
                + Integer.signum(p.compareTo(r)));
        System.out.println("decimal q-r sign: "
                + Integer.signum(q.compareTo(r)));

        Set<BigDecimal> ordered = new TreeSet<>();
        ordered.add(p);
        ordered.add(q);
        Set<BigDecimal> hashed = new HashSet<>();
        hashed.add(p);
        hashed.add(q);
        System.out.println("TreeSet size: " + ordered.size());
        System.out.println("HashSet size: " + hashed.size());
    }
}
```

Chạy trong thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac CompareToThirdDemo.java
java CompareToThirdDemo
```

Kết quả:

```text
bad x-y: 0
bad x-z: -1
bad y-z: 1
bad third consistency: false
good a-b: 0
good a-c: -1
good b-c: -1
good equals: true
decimal p-q: 0
decimal p.equals(q): false
decimal p-r sign: -1
decimal q-r sign: -1
TreeSet size: 1
HashSet size: 2
```

Ở `BadPerson`, phép so sánh bằng `0` nhưng hai dấu khi so với `z` khác nhau. Ở `GoodPerson`, những đối tượng trùng cả tên lẫn tuổi được xem là tương đương theo `compareTo` và cũng bằng nhau theo `record.equals()`. Khi so với `c`, dấu đều là `-1`; thuật toán nhất quán.

`BigDecimal` trong ví dụ vừa đảm bảo **quy tắc so sánh với giá trị thứ ba** (`2.0` và `2.00` đều nhỏ hơn `3.0`) vừa là trường hợp quan trọng về **khác biệt giữa `compareTo()` và `equals()`**.

## 7. So với equals: hai yêu cầu có mức bắt buộc khác nhau

**Bắt buộc theo `Comparable`:** nếu `x.compareTo(y) == 0`, mọi `z` hợp lệ phải nhận **cùng dấu** từ `x.compareTo(z)` và `y.compareTo(z)`. Đây là điều kiện đang được minh họa bằng `BadPerson` và `GoodPerson`.

**Khuyến nghị mạnh:** `x.compareTo(y) == 0` nên tương đương `x.equals(y)`. `BigDecimal("2.0")` và `BigDecimal("2.00")` có `compareTo == 0` và `equals == false`, nhưng cả hai vẫn có cùng dấu khi so với `3.0`. Vì vậy, bất nhất với `equals` tự nó **không** vi phạm quy tắc bắt buộc về giá trị thứ ba. Bài tiếp theo xét rủi ro của sự bất nhất này trong `TreeSet` và `TreeMap`.

## 8. Hậu quả thực tế đối với `TreeSet`, `TreeMap`, sắp xếp và tìm kiếm

Với `TreeSet`/`TreeMap`, phép so sánh của `Comparable` (hoặc `Comparator` được cung cấp) phục vụ cả **thứ tự** lẫn việc tìm vị trí một khoá:

- Nếu hai khoá được so sánh bằng `0`, cấu trúc cây xem chúng là cùng khoá theo thứ tự; `TreeMap.put()` có thể cập nhật giá trị gắn với khoá tương đương, còn `TreeSet.add()` có thể không thêm phần tử thứ hai.
- Nếu phép so sánh không bắc cầu hoặc vi phạm quy tắc của giá trị thứ ba, cấu trúc cây có thể có hành vi truy vấn bất ngờ vì việc điều hướng dựa trên các phép so sánh mâu thuẫn.
- Nếu thứ tự không nhất quán với `equals()`, `TreeSet` và `HashSet` có thể đánh giá số phần tử khác nhau dù dùng cùng các đối tượng.
- Thuật toán sắp xếp (`Collections.sort`, `List.sort`, `Arrays.sort`) đòi quan hệ thứ tự đúng hợp đồng; vi phạm có thể khiến kết quả không đáng tin cậy hoặc ném `IllegalArgumentException` trong một số trường hợp.

Không nên kiểm thử tính đúng của `compareTo` chỉ bằng việc chạy `TreeSet` trên vài phần tử rồi quan sát kết quả: thuật toán có thể tình cờ không đi qua trường hợp mâu thuẫn. Hãy **kiểm tra trực tiếp hợp đồng** trên những bộ ba giá trị đại diện.

## 9. Cách thiết kế và kiểm tra `compareTo()`

1. **Xác định tiêu chí nhận diện bằng `0`.** Ví dụ: cùng mã định danh, hay cùng cả tên và tuổi? Những đối tượng có `compareTo == 0` phải hoàn toàn thay thế được nhau trong các phép so sánh tiếp theo.
2. **Chọn thứ tự tiêu chí cố định.** Nếu so theo tên rồi tuổi, dùng tên là tiêu chí đầu tiên; chỉ khi tên bằng nhau mới xét tuổi. Không đổi qua lại giữa hai tiêu chí tùy theo cặp giá trị đang so.
3. **Sử dụng các phương thức so sánh có sẵn.** Chọn `Integer.compare`, `Long.compare`, `String.compareTo`, `Comparator.comparing(...).thenComparing(...)`; tránh tự trừ số nguyên hoặc tự xử lý dấu thủ công khi không cần.
4. **Thống nhất ngữ nghĩa `null`.** `Comparable.compareTo(null)` thông thường phải ném `NullPointerException`; nếu dùng `Comparator`, có thể chủ động chọn quy tắc `Comparator.nullsFirst(...)`/`nullsLast(...)` ở nơi cần hỗ trợ `null`.
5. **Ưu tiên nhất quán với `equals()` khi thiết kế lớp giá trị**, đặc biệt nếu cùng đối tượng làm khoá cho cả collection băm và collection sắp xếp.
6. **Thử bộ ba giá trị.** Với mọi bộ kiểm thử `(x, y, z)` mà `x.compareTo(y) == 0`, xác nhận `Integer.signum(x.compareTo(z)) == Integer.signum(y.compareTo(z))`. Cũng kiểm tra đối xứng dấu và bắc cầu.

Ví dụ kiểm tra giản lược trong một phương thức Java:

```java
static <T extends Comparable<? super T>> boolean samePositionToThird(
        T x, T y, T z) {
    return x.compareTo(y) != 0
            || Integer.signum(x.compareTo(z))
                    == Integer.signum(y.compareTo(z));
}
```

Phương thức trên **chỉ kiểm tra điều kiện với một bộ ba đầu vào**, không chứng minh hợp đồng đúng với *mọi* giá trị có thể có. Biểu thức `x.compareTo(y) != 0` làm điều kiện trở nên đúng theo logic khi tiền đề không thỏa. Cần thêm dữ liệu biên (giá trị trùng, khác nhau, cực trị số nguyên, tiêu chí phụ, chuỗi Unicode, v.v.) và các kiểm tra bắc cầu/đối xứng.

## 10. Các nhầm lẫn thường gặp

| Nhầm lẫn | Giải thích đúng |
| --- | --- |
| `compareTo()` chỉ có thể trả `-1`, `0`, `1` | Kết quả có thể là bất kỳ `int`; quy tắc dùng **dấu** |
| `x.compareTo(y) == 0` chỉ có nghĩa là cặp `x`, `y` tình cờ bằng nhau | Còn bắt buộc phải **cùng dấu so sánh với mọi `z`** |
| Cùng tên thì trả `0`, khác tên thì so tuổi là hợp lý | Tạo ra thứ tự mâu thuẫn khi hai người cùng tên nhưng khác tuổi |
| `compareTo == 0` luôn bắt buộc `equals() == true` | Tính nhất quán với `equals()` được khuyến nghị mạnh, nhưng không tuyệt đối bắt buộc (ví dụ `BigDecimal`) |
| `TreeSet` luôn dùng `equals()` để bỏ trùng | Nó dùng `compareTo` hoặc `Comparator` để xác định vị trí tương đương |
| Có thể `return a - b` với mọi số nguyên | Phép trừ có thể tràn và đảo dấu; dùng `Integer.compare(a, b)` |
| Chỉ kiểm tra xếp thứ tự hai phần tử là đủ | Cần kiểm tra cả quan hệ với **phần tử thứ ba**, bắc cầu và đối xứng |

## 11. Tự kiểm tra

1. Nếu `x.compareTo(y) == 0`, `x.compareTo(z) == -7` và `y.compareTo(z) == -1`, có vi phạm? **Không**, vì cả hai đều âm.
2. Nếu `x.compareTo(y) == 0`, `x.compareTo(z) < 0` nhưng `y.compareTo(z) > 0`, có hợp lệ? **Không**, vì chúng mâu thuẫn về vị trí của `z`.
3. Nếu `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0` nhưng `equals()` là `false`, điều đó có tự động vi phạm quy tắc giá trị thứ ba không? **Không**. `compareTo` của `BigDecimal` vẫn đặt cả hai cùng vị trí tương quan với mọi số `z`.
4. Vì sao so tên trước, rồi so tuổi nếu tên bằng nhau tốt hơn việc cùng tên trả `0`, khác tên so tuổi? **Vì tiêu chí luôn được áp dụng theo một thứ tự thống nhất, cho phép định nghĩa thứ tự hợp lệ.**

**Ghi nhớ:** `compareTo(x, y) == 0` (viết theo phương thức thể hiện là `x.compareTo(y) == 0`) tạo ra một nhóm **tương đương theo thứ tự**. Mọi thành viên trong nhóm phải so sánh với bất kỳ giá trị thứ ba `z` bằng **cùng một dấu**. Đây là yêu cầu bắt buộc của hợp đồng `Comparable`, độc lập với khuyến nghị `compareTo() == 0` nên nhất quán với `equals()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-006">Khuyến nghị nhất quán giữa compareTo() bằng 0 và equals()</a>

<details>
<summary>Xem chi tiết</summary>

# Khuyến nghị nhất quán giữa `compareTo() == 0` và `equals()`

## 1. Hiểu đúng hai khái niệm “bằng nhau”

Trong Java, một đối tượng có thể tham gia **hai cơ chế so sánh độc lập**:

- **`equals(Object)`** trả về `boolean` để xác định hai đối tượng có bằng nhau về mặt logic hay không. Đây là hợp đồng chung của `Object`; các collection như `HashSet` và `HashMap` dùng nó cùng `hashCode()` để nhận diện phần tử/khóa.
- **`compareTo(T)`** trả về `int` âm, 0 hoặc dương để xác định thứ tự tự nhiên của các đối tượng triển khai `Comparable<T>`. Các collection có thứ tự như `TreeSet` và `TreeMap` dùng phép so sánh này, hoặc một `Comparator` được cung cấp.

Hợp đồng `Comparable` **khuyến nghị mạnh** thứ tự tự nhiên nhất quán với `equals`, nghĩa là:

```java
x.compareTo(y) == 0   // nên tương đương với
x.equals(y)           // true
```

Đây là đoạn biểu thức minh họa, giả sử `x` và `y` cùng kiểu thích hợp và khác `null`.

Viết chính xác bằng logic:

```text
x.compareTo(y) == 0  ⇔  x.equals(y) == true
```

Dấu `⇔` có nghĩa **cả hai chiều đều đúng**:

1. Nếu `compareTo` trả 0 thì `equals` phải trả `true`.
2. Nếu `equals` trả `true` thì `compareTo` phải trả 0.

Điều này nói về **cặp đối tượng có giá trị so sánh**, không có nghĩa `compareTo` phải trả chính xác `-1` hoặc `1` khi chúng khác nhau. Giá trị âm/dương bất kỳ là hợp lệ.

**Mấu chốt:** Đây là **khuyến nghị**, không phải điều kiện bắt buộc để một lớp triển khai `Comparable`. Một số lớp chuẩn, nổi bật là `BigDecimal`, cố ý có thứ tự tự nhiên **không nhất quán** với `equals`; JavaDoc yêu cầu trường hợp này nên được ghi rõ để người sử dụng hiểu hậu quả.

## 2. Vì sao Java khuyến nghị sự nhất quán?

Giả sử một hệ thống định nghĩa hai sản phẩm có cùng mã `sku` là cùng một đối tượng logic. Nếu `equals` dùng `sku` nhưng `compareTo` lại chỉ so giá bán, ta có thể gặp hai tình huống:

- Hai sản phẩm khác mã nhưng cùng giá: `compareTo == 0` dù `equals == false`. Một `TreeSet` có thể bỏ sản phẩm thứ hai vì coi là trùng, trong khi `HashSet` giữ cả hai.
- Hai sản phẩm cùng mã nhưng giá khác: `equals == true` dù `compareTo != 0`. Một `HashSet` coi là trùng, nhưng `TreeSet` có thể giữ hai phần tử mà `equals` lại cho là bằng nhau.

Sự khác biệt này đặc biệt gây khó hiểu nếu cùng đối tượng được truyền lần lượt qua các API nhận `Set`, hoặc khi thay `HashMap` bằng `TreeMap` để sắp xếp khóa.

| Collection | Cơ chế nhận diện hai phần tử/khóa tương đương | Hệ quả khi tiêu chí khác nhau |
| --- | --- | --- |
| `HashSet` | `equals(Object)` và `hashCode()` | Gộp các phần tử bằng nhau theo `equals` |
| `HashMap` | `equals(Object)` và `hashCode()` của khóa | Cập nhật giá trị khi gặp khóa bằng nhau |
| `TreeSet` | `compareTo(T) == 0` hoặc `Comparator.compare(...) == 0` | Coi các phần tử có cùng thứ tự so sánh là một |
| `TreeMap` | `compareTo(T) == 0` hoặc `Comparator.compare(...) == 0` | Coi các khóa so sánh bằng 0 là một khóa |

`TreeSet` và `TreeMap` vẫn vận hành theo **thứ tự so sánh đã cung cấp**, kể cả khi không nhất quán với `equals`. Nhưng khi đó chúng không đáp ứng trọn vẹn ý nghĩa equality mà giao diện `Set`/`Map` thông thường mô tả. API gọi cần hiểu rõ quy tắc này.

Ngoài tính đúng đắn, sự nhất quán còn giúp mã dễ đọc: “hai khóa trùng nhau” có cùng nghĩa dù dữ liệu đang nằm trong map theo băm, map theo thứ tự hay được so sánh trực tiếp.

## 3. Ví dụ sai: compareTo chỉ xét mức ưu tiên, equals xét toàn bộ record

Tạo tệp `InconsistentComparisonDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class InconsistentComparisonDemo {
    record Event(String code, int priority)
            implements Comparable<Event> {

        @Override
        public int compareTo(Event other) {
            return Integer.compare(priority, other.priority);
        }
    }

    public static void main(String[] args) {
        Event a = new Event("E-1", 5);
        Event b = new Event("E-2", 5);

        System.out.println(a.equals(b));
        System.out.println(a.compareTo(b) == 0);

        Set<Event> hashSet = new HashSet<>();
        hashSet.add(a);
        hashSet.add(b);
        System.out.println(hashSet.size());

        Set<Event> treeSet = new TreeSet<>();
        treeSet.add(a);
        treeSet.add(b);
        System.out.println(treeSet.size());
        System.out.println(treeSet.contains(b));
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac InconsistentComparisonDemo.java
java InconsistentComparisonDemo
```

Kết quả:

```text
false
true
2
1
true
```

Diễn giải:

1. Record `Event` tự sinh `equals(Object)` theo **cả `code` và `priority`**. `a` và `b` khác mã nên `equals` là `false`.
2. `compareTo` chỉ so `priority`. Hai sự kiện đều ưu tiên 5 nên `compareTo == 0`.
3. `HashSet` giữ **2 phần tử** theo equality của record.
4. `TreeSet` chỉ giữ **1 phần tử**, vì thứ tự tự nhiên coi hai sự kiện bằng nhau. `contains(b)` vẫn trả `true` theo tiêu chí của cây, dù đối tượng `b` chưa được thêm thành một phần tử riêng.

`TreeSet.contains(b)` ở đây không chứng minh `a.equals(b)`. Nó chỉ chứng minh cây có phần tử tương đương với `b` theo cách so sánh đang sử dụng.

**Vấn đề thiết kế:** Nếu yêu cầu nghiệp vụ là một sự kiện phân biệt bởi cả mã và mức ưu tiên, `compareTo` thiếu tiêu chí phá hòa (`code`). Nếu mục đích thực sự là gộp sự kiện cùng mức ưu tiên, hãy chủ động diễn đạt quy tắc đó trong cấu trúc dữ liệu hoặc `Comparator`, thay vì giả định equality thông thường đã đổi theo.

## 4. Thiết kế đúng: cùng định nghĩa về “trùng” trong equals và compareTo

Ví dụ dưới đây mô hình hóa một mã sản phẩm `sku` duy nhất. Giá hiển thị chỉ là thuộc tính mô tả, **không quyết định danh tính**. Vì thế, cả `equals` và `compareTo` đều dựa trên `sku`.

Tạo tệp `ConsistentComparisonDemo.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ConsistentComparisonDemo {
    static final class Product implements Comparable<Product> {
        private final String sku;
        private final int displayPrice;

        Product(String sku, int displayPrice) {
            this.sku = Objects.requireNonNull(sku);
            this.displayPrice = displayPrice;
        }

        @Override
        public int compareTo(Product other) {
            return sku.compareTo(other.sku);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Product other
                    && sku.equals(other.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    public static void main(String[] args) {
        Product a = new Product("A-01", 100);
        Product sameSku = new Product("A-01", 200);
        Product b = new Product("B-01", 100);

        System.out.println(a == sameSku);
        System.out.println(a.equals(sameSku));
        System.out.println(a.compareTo(sameSku) == 0);
        System.out.println(a.equals(b));
        System.out.println(a.compareTo(b) < 0);

        Set<Product> hashSet = new HashSet<>();
        hashSet.add(a);
        hashSet.add(sameSku);
        hashSet.add(b);
        System.out.println(hashSet.size());

        Set<Product> treeSet = new TreeSet<>();
        treeSet.add(a);
        treeSet.add(sameSku);
        treeSet.add(b);
        System.out.println(treeSet.size());

        Map<Product, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        hashMap.put(sameSku, "updated");
        System.out.println(hashMap.size());
        System.out.println(hashMap.get(a));

        Map<Product, String> treeMap = new TreeMap<>();
        treeMap.put(a, "first");
        treeMap.put(sameSku, "updated");
        System.out.println(treeMap.size());
        System.out.println(treeMap.get(a));
    }
}
```

Chạy:

```bash
javac ConsistentComparisonDemo.java
java ConsistentComparisonDemo
```

Stdout:

```text
false
true
true
false
true
2
2
1
updated
1
updated
```

Giải thích:

- `a` và `sameSku` khác tham chiếu (`== false`) nhưng có cùng `sku`; vì vậy `equals == true` và `compareTo == 0`.
- `a` và `b` khác mã; `equals == false`, còn `compareTo` cho đúng thứ tự từ điển của mã (`"A-01"` trước `"B-01"`).
- Cả `HashSet` lẫn `TreeSet` có đúng **2 sản phẩm logic**.
- Cả `HashMap` lẫn `TreeMap` đều chỉ có **1 khóa logic** sau khi cập nhật mã `A-01`; `get(a)` trả `"updated"`.

Ở đây `displayPrice` không tham gia equality và thứ tự tự nhiên, nên hai sản phẩm cùng SKU nhưng giá hiển thị khác vẫn biểu thị cùng đối tượng nghiệp vụ. **Đây là quyết định có chủ ý của ví dụ**, không phải quy tắc rằng mọi kiểu `Product` đều nên bỏ qua giá.

`Product` là `final`, `sku` là `final` và `String` bất biến; điều này giúp duy trì tính nhất quán khi khóa/phần tử đang nằm trong collection. Với kiểu có kế thừa hoặc thuộc tính mutable, cần kiểm tra thêm hợp đồng bình đẳng của toàn hierarchy và nguy cơ thay đổi khóa.

## 5. Sửa ví dụ Event bằng tiêu chí phá hòa

Với record `Event(String code, int priority)`, `equals` được tạo dựa vào **cả hai thành phần**. Có thể làm natural ordering nhất quán bằng cách so mức ưu tiên trước rồi dùng mã để phân biệt những sự kiện có cùng ưu tiên:

```java
@Override
public int compareTo(Event other) {
    int byPriority = Integer.compare(priority, other.priority);
    if (byPriority != 0) {
        return byPriority;
    }
    return code.compareTo(other.code);
}
```

Đây là **đoạn mã đặt bên trong record `Event` ở ví dụ trước**, giả sử `code` không phải `null`. Với các chuỗi hợp lệ, phép so sánh chỉ trả 0 khi **`priority` bằng nhau và `code` bằng nhau**, phù hợp với `equals` được sinh tự động.

Nếu thứ tự chỉ là nhu cầu tạm thời của một màn hình hay báo cáo, không cần thay đổi natural ordering của kiểu. Thay vào đó, dùng `Comparator` tại nơi tạo collection. Cần nhớ comparator đó sẽ quyết định ý nghĩa trùng khóa/phần tử của `TreeSet`/`TreeMap` sử dụng nó.

Ví dụ comparator so cả hai tiêu chí:

```java
Comparator<Event> byPriorityThenCode =
        Comparator.comparingInt(Event::priority)
                .thenComparing(Event::code);
```

Đoạn này cũng là **mã minh họa**, cần `import java.util.Comparator;` và record `Event` có accessor `priority()`, `code()`. Nếu `code` có thể là `null`, phải quy định cách xử lý `null` (ví dụ `Comparator.nullsFirst`).

## 6. Ba hợp đồng riêng phải cùng đúng

Một lớp định nghĩa cả equality và natural ordering nên đáp ứng **ba lớp cam kết**:

### Hợp đồng equals(Object)

`equals` phải phản xạ, đối xứng, bắc cầu, nhất quán khi dữ liệu liên quan không đổi, và `x.equals(null) == false` với `x` khác `null`.

### Hợp đồng hashCode()

Nếu `x.equals(y)` là `true`, thì `x.hashCode() == y.hashCode()` bắt buộc là `true`. Khóa trong `HashMap` hoặc phần tử trong `HashSet` nên có dữ liệu tham gia bình đẳng **ổn định trong thời gian được lưu**.

`compareTo` **không thay thế** `hashCode`. Hai object có `compareTo == 0` nhưng `equals == false` không có nghĩa chúng bắt buộc cùng mã băm. Quy tắc hash xuất phát từ `equals`.

### Hợp đồng compareTo(T)

Phép so sánh tự nhiên phải có thứ tự nhất quán: kết quả theo chiều đối ngược nhau phải có dấu ngược, quan hệ lớn hơn phải bắc cầu, và khi `x.compareTo(y) == 0` thì `x` với `y` phải được so như nhau khi đứng trước một đối tượng thứ ba thích hợp. Nói cách khác, comparator không được tạo ra thứ tự mâu thuẫn.

**Nhất quán giữa compareTo và equals là khuyến nghị bổ sung**, không thay thế các điều kiện nội tại của hai phương thức. Một lớp có thể có `compareTo` bắc cầu nhưng vẫn không nhất quán với `equals`, như ví dụ `Event`.

## 7. Ngoại lệ có chủ đích: BigDecimal

`BigDecimal` là ví dụ Java chuẩn được tài liệu ghi rõ: hai số có thể bằng nhau theo giá trị số học nhưng khác **scale**.

```java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");

System.out.println(x.compareTo(y) == 0); // true
System.out.println(x.equals(y));         // false
```

Đây là đoạn minh họa cần `import java.math.BigDecimal;`. `compareTo` coi hai số bằng nhau về số học; `equals` còn yêu cầu cùng scale. Kết quả là `HashSet<BigDecimal>` có thể giữ `2.0` và `2.00` làm hai phần tử, trong khi `TreeSet<BigDecimal>` với natural ordering chỉ giữ một.

Điều đó **không làm BigDecimal trở thành một kiểu Java sai**: nó cố ý thể hiện hai khái niệm bình đẳng khác nhau. Nhưng người dùng API phải xác định rõ “trùng” nghĩa là cùng giá trị số học hay cùng giá trị và scale, đặc biệt trước khi dùng làm khóa hoặc phần tử set.

Nếu nghiệp vụ muốn gộp tất cả dạng biểu diễn của cùng một số decimal, có thể chuẩn hóa có chủ đích bằng `stripTrailingZeros()` ở cả bước thêm lẫn tra cứu; nhớ rằng phương thức này có thể cho scale âm hoặc dạng ký pháp khoa học. Nếu nghiệp vụ cần scale cố định (ví dụ hai chữ số thập phân), có thể `setScale(2, roundingMode)` theo quy tắc làm tròn rõ ràng. Không áp dụng việc chuẩn hóa tùy tiện khi scale mang ý nghĩa nghiệp vụ.

## 8. Những lỗi cài đặt thường gặp

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| `compareTo` chỉ so một trường trong khi `equals` so nhiều trường | Các phần tử khác nhau theo `equals` có thể bị TreeSet gộp | Thêm tiêu chí phá hòa phù hợp hoặc định nghĩa lại equality theo nghiệp vụ |
| `compareTo` so nhiều trường trong khi `equals` chỉ so một trường | TreeSet có thể giữ hai phần tử mà `equals` coi là bằng nhau | Thống nhất định nghĩa trùng nhau |
| Dùng `return a - b;` để so `int` | Tràn số có thể làm đảo thứ tự, phá quy tắc so sánh | Dùng `Integer.compare(a, b)` |
| Mặc định `compareTo` luôn trả `-1`, `0`, `1` | Kiểm tra sai điều kiện, ví dụ `compareTo(x) == 1` | Chỉ kiểm tra âm / 0 / dương |
| Ghi đè `equals` nhưng quên `hashCode` | HashSet/HashMap không nhận diện đúng đối tượng bằng nhau | Ghi đè cả hai bằng cùng những thành phần |
| Đưa khóa mutable vào collection rồi đổi trường so sánh | Tra cứu trong HashMap hoặc thứ tự của TreeSet/TreeMap có thể hỏng | Ưu tiên kiểu bất biến; xóa trước, cập nhật rồi thêm lại khi cần |
| Gọi `compareTo(null)` và trông chờ kết quả so với `null` | `Comparable` thông thường không coi `null` là giá trị so sánh hợp lệ | Xử lý `null` riêng hoặc dùng comparator có chính sách null |
| Dùng `Comparator` theo cột hiển thị rồi tưởng TreeSet vẫn phân biệt phần tử theo `equals` | TreeSet gộp khi comparator trả 0 | Dùng tiêu chí phá hòa hoặc một cấu trúc cho phép phần tử trùng khóa sắp xếp |

Một nguyên tắc thực tế: **natural ordering nên đại diện cho thứ tự ổn định và có ý nghĩa lâu dài của kiểu**, còn những kiểu sắp xếp theo yêu cầu màn hình (theo giá, ngày, mức ưu tiên) nên được biểu diễn bằng comparator riêng. Tuy nhiên, ngay cả comparator riêng cũng phải được xem xét nếu dùng nó làm cơ chế nhận diện khóa trong sorted set/map.

## 9. Checklist thiết kế và kiểm thử

1. **Viết trước định nghĩa bình đẳng:** hai object nào thực sự là một giá trị logic? Chọn trường tham gia `equals` và `hashCode`.
2. **Chọn thứ tự tự nhiên:** tiêu chí nào tạo được thứ tự đầy đủ, bắc cầu và ổn định? Phải phá hòa đủ để `compareTo == 0` biểu thị đúng một giá trị logic nếu muốn nhất quán với `equals`.
3. **Dùng helper so sánh đúng:** `Integer.compare`, `Long.compare`, `String.compareTo`, `Comparator.comparing(...).thenComparing(...)` thay vì phép trừ dễ tràn số.
4. **Kiểm thử cả hai chiều:** với các object khác nhau `a`, `b`, xác minh `(a.compareTo(b) == 0) == a.equals(b)` và `b.compareTo(a)` có dấu ngược khi cần.
5. **Kiểm thử ba phần tử:** nếu `a < b` và `b < c` thì `a < c`; các giá trị so sánh 0 không làm thay đổi kết quả khi so với phần tử thứ ba.
6. **Kiểm thử Set/Map thực tế:** đưa các đối tượng bằng nhau và khác nhau vào `HashSet`/ `TreeSet`, dùng làm khóa `HashMap`/`TreeMap`, so kích thước và kết quả `contains/get`.
7. **Kiểm tra null, kế thừa, mutable state:** xác định rõ tiền điều kiện so sánh và tránh thay đổi trường quyết định thứ tự khi object nằm trong sorted collection.
8. **Nếu cố ý không nhất quán, ghi tài liệu rõ:** giải thích tiêu chí `compareTo`, tiêu chí `equals`, và ảnh hưởng đến sorted set/map; người dùng phải chọn collection phù hợp.

**Tự kiểm tra:**

- Nếu `a.compareTo(b) == 0` nhưng `a.equals(b) == false`, natural ordering có nhất quán với equals không? **Không.**
- Nếu một `TreeSet` chỉ lưu một phần tử trong khi `HashSet` lưu hai phần tử cùng cặp object, nguyên nhân thường là gì? **Hai tiêu chí “trùng” khác nhau giữa so thứ tự và equality.**
- Nhất quán giữa `compareTo` và `equals` có bắt buộc với mọi lớp `Comparable` không? **Không, nhưng được khuyến nghị mạnh; khi không nhất quán cần tài liệu hóa.**
- Khi dùng `Comparator` để sort theo điểm số, nếu nhiều người cùng điểm nhưng phải giữ tất cả, cần làm gì? **Thêm một tiêu chí phá hòa hoặc dùng cấu trúc dữ liệu thích hợp, thay vì dùng TreeSet chỉ so điểm.**

**Ghi nhớ:** Nên định nghĩa `compareTo(a, b) == 0` đúng khi `a.equals(b)` là `true`, để mọi collection hiểu thống nhất ý nghĩa “cùng phần tử/khóa”. Trường hợp cố ý khác như `BigDecimal` là hợp lệ, nhưng phải hiểu và tài liệu hóa hành vi khi dùng cấu trúc dữ liệu có thứ tự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-007">So sánh với null và kiểu không tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh với `null` và kiểu không tương thích trong thứ tự tự nhiên

Khi một lớp Java cài đặt `Comparable<T>`, phương thức `compareTo(T other)` định nghĩa **thứ tự tự nhiên (natural ordering)** giữa các đối tượng cùng miền so sánh. Kết quả không đơn giản là `true`/`false`: số âm nghĩa là đứng trước, `0` nghĩa là tương đương theo thứ tự, số dương nghĩa là đứng sau.

Hai trường hợp biên cần có chính sách rõ ràng là **đối số `null`** và **đối tượng không thuộc kiểu có thể so sánh**. Theo hợp đồng của `Comparable`, lời gọi `x.compareTo(null)` trên một `x` khác `null` phải ném `NullPointerException`; nếu đối tượng không thể so sánh với `x` theo kiểu được hỗ trợ, phép so sánh có thể ném `ClassCastException`. Đây là hành vi khác với `equals(null)`, vốn phải trả về `false`.

Mục tiêu: biết **khi nào phép so sánh nên ném ngoại lệ**, cách generic giúp phát hiện lỗi kiểu sớm, và cách dùng `Comparator` nếu **thực sự cần** sắp xếp cả giá trị `null`.

## 1. `compareTo` biểu diễn thứ tự, không phải phép kiểm tra kiểu tùy ý

Một dạng khai báo phổ biến:

```java
public final class Lesson implements Comparable<Lesson> {
    @Override
    public int compareTo(Lesson other) {
        // So sánh các thuộc tính quyết định thứ tự.
        throw new UnsupportedOperationException("Phần thân chỉ minh họa");
    }
}
```

Đây chỉ là **khung minh họa API**, không phải cài đặt hoàn chỉnh: phần thân phải được thay bằng quy tắc so sánh thật. Khi đã dùng `Comparable<Lesson>`, một biến kiểu `Lesson` chỉ được gọi `compareTo` với đối số kiểu `Lesson` (hoặc subtype phù hợp) hay `null` ở mức kiểm tra kiểu tĩnh.

Các điều kiện đáng nhớ của `compareTo`:

- `x.compareTo(y) < 0`: `x` đứng trước `y`; `== 0`: bằng nhau theo *thứ tự*; `> 0`: `x` đứng sau `y`. **Không giả định kết quả luôn là đúng `-1`, `0`, `1`**; chỉ dấu của kết quả có ý nghĩa.
- Phải giữ tính đối xứng dấu: `signum(x.compareTo(y)) == -signum(y.compareTo(x))` với các đối tượng so sánh được. Nếu một chiều ném ngoại lệ vì không so sánh được, chiều còn lại cũng không được vô cớ trở thành một phép so sánh hợp lệ.
- Thứ tự phải có tính bắc cầu và nhất quán với các trường hợp so sánh tương đương theo thứ tự. Nếu `x.compareTo(y) == 0`, kết quả so sánh `x` và `y` với một `z` khác phải có cùng dấu khi các phép so sánh đó hợp lệ.
- Nhất quán với `equals` **được khuyến nghị mạnh**: `x.compareTo(y) == 0` nên tương đương với `x.equals(y)`. Tuy nhiên, API `Comparable` không bắt buộc mọi lớp phải giữ sự tương đương này; trường hợp `BigDecimal` được trình bày ở phần sau.

Phép `compareTo` chỉ có ý nghĩa trong miền đối tượng mà lớp thực sự biết sắp thứ tự. Nó không phải bộ chuyển đổi dữ liệu, cũng không nên dựa vào việc tự trả `0` cho mọi đối tượng không hợp lệ.

## 2. Đối số `null`: `compareTo(null)` phải ném `NullPointerException`

Một lớp cài đặt `Comparable` đúng hợp đồng không xem `null` như phần tử thuộc thứ tự tự nhiên của nó:

```java
// Minh họa khi value khác null:
value.equals(null);     // false nếu equals tuân thủ hợp đồng
value.compareTo(null);  // ném NullPointerException
```

Lý do: `null` không phải instance của một lớp, nên việc một đối tượng có đứng trước hay sau `null` không được `Comparable` tự định nghĩa. Trong một `compareTo` tự viết, có thể dùng `Objects.requireNonNull(other)` ngay đầu phương thức để khẳng định điều kiện này; sau đó so sánh các trường theo chính sách của lớp.

Nếu chỉ viết `other.field`, việc truy cập đó cũng ném `NullPointerException` khi `other == null`, nhưng cách kiểm tra tường minh giúp ý định rõ hơn và tránh vô tình xử lý `null` như một đối tượng hợp lệ.

**Không sửa `compareTo` để trả `0` khi `other == null`**. Làm vậy khiến `null` bị coi là tương đương theo thứ tự với một instance có thật, trái với hợp đồng `Comparable` và gây ngữ nghĩa khó hiểu cho các cấu trúc dựa trên thứ tự.

Cũng phân biệt hai tình huống:

- **Đối số `null`:** `someValue.compareTo(null)` có receiver hợp lệ và phải ném `NullPointerException`.
- **Receiver `null`:** `someNullableVariable.compareTo(x)` cũng có thể ném `NullPointerException`, nhưng vì cố gọi instance method trên tham chiếu `null` ngay trước khi có thể chạy thân `compareTo`; đây là lỗi gọi phương thức, không phải một thứ tự được định nghĩa cho `null`.

## 3. Kiểu không tương thích và vai trò của generic

Với lớp `Lesson implements Comparable<Lesson>`, Java kiểm tra kiểu đối số ngay lúc biên dịch:

```java
// Chỉ là đoạn minh họa; giả sử first có kiểu Lesson:
first.compareTo(anotherLesson); // Hợp lệ nếu anotherLesson là Lesson
first.compareTo(null);          // Hợp lệ khi biên dịch; NPE khi chạy
// first.compareTo("java");     // Không biên dịch: String không phải Lesson
```

Dấu comment ở dòng cuối cố ý giữ ví dụ có thể đặt trong mã nguồn hợp lệ; nếu bỏ comment, compiler sẽ phát hiện sai kiểu. Nhưng với **raw type** (`Comparable` không có tham số generic), ép kiểu không an toàn, hoặc Java code cũ, lỗi có thể lọt qua compiler rồi xuất hiện tại runtime:

```java
// Minh họa code cũ/không an toàn, không nên dùng trong API bình thường:
Comparable raw = first;
raw.compareTo("java"); // Có thể biên dịch với unchecked warning; CCE khi chạy
```

Một lớp triển khai `Comparable<Lesson>` thông thường sinh ra bridge method cho cơ chế type erasure. Khi raw caller đưa vào một `String`, runtime không thể chuyển `String` thành `Lesson`, nên ném `ClassCastException` trước khi đi vào logic sắp thứ tự thông thường.

**`ClassCastException` khác `NullPointerException`:** ngoại lệ đầu tiên báo đối tượng không thể so sánh vì kiểu, ngoại lệ thứ hai báo tham chiếu không tồn tại. Không nên bắt cả hai rồi im lặng coi như `compareTo` trả `0`; như vậy che giấu vi phạm hợp đồng và có thể làm `TreeSet`, `TreeMap` mất hoặc gộp sai dữ liệu.

## 4. Chương trình Java 17+ hoàn chỉnh

Ví dụ sau xây dựng một thứ tự tự nhiên rõ ràng cho `LessonOrder`: trước tiên sắp theo `priority` tăng dần, nếu trùng thì so `title` bằng thứ tự từ điển của `String`. Constructor từ chối `title == null`, nên các trường dùng trong `compareTo` luôn hợp lệ.

Lưu vào tệp **`NaturalOrderBoundariesDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class LessonOrder implements Comparable<LessonOrder> {
    private final int priority;
    private final String title;

    LessonOrder(int priority, String title) {
        this.priority = priority;
        this.title = Objects.requireNonNull(title, "title");
    }

    @Override
    public int compareTo(LessonOrder other) {
        Objects.requireNonNull(other, "other");

        int byPriority = Integer.compare(priority, other.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        return title.compareTo(other.title);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LessonOrder other)) {
            return false;
        }
        return priority == other.priority
                && title.equals(other.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, title);
    }
}

public class NaturalOrderBoundariesDemo {
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String compareWithWrongType(LessonOrder lesson) {
        Comparable raw = lesson;
        try {
            raw.compareTo("wrong type");
            return "accepted";
        } catch (ClassCastException expected) {
            return expected.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        LessonOrder first = new LessonOrder(1, "Core");
        LessonOrder same = new LessonOrder(1, "Core");
        LessonOrder later = new LessonOrder(2, "Advanced");

        System.out.println("compare equal values: "
                + first.compareTo(same));
        System.out.println("equals equal values: " + first.equals(same));
        System.out.println("first before later: "
                + (first.compareTo(later) < 0));
        System.out.println("equals(null): " + first.equals(null));

        try {
            first.compareTo(null);
            System.out.println("compareTo(null): accepted");
        } catch (NullPointerException expected) {
            System.out.println("compareTo(null): NullPointerException");
        }

        System.out.println("wrong type via raw Comparable: "
                + compareWithWrongType(first));

        Set<LessonOrder> naturalSet = new TreeSet<>();
        naturalSet.add(first);
        naturalSet.add(same);
        naturalSet.add(later);
        System.out.println("natural TreeSet size: " + naturalSet.size());

        Comparator<LessonOrder> nullFirst =
                Comparator.nullsFirst(Comparator.naturalOrder());
        TreeSet<LessonOrder> nullableSet = new TreeSet<>(nullFirst);
        nullableSet.add(first);
        nullableSet.add(null);
        System.out.println("nullable TreeSet first is null: "
                + (nullableSet.first() == null));

        BigDecimal one = new BigDecimal("1.0");
        BigDecimal otherOne = new BigDecimal("1.00");
        System.out.println("BigDecimal compareTo zero: "
                + (one.compareTo(otherOne) == 0));
        System.out.println("BigDecimal equals: "
                + one.equals(otherOne));
        Set<BigDecimal> decimalSet = new TreeSet<>();
        decimalSet.add(one);
        decimalSet.add(otherOne);
        System.out.println("BigDecimal TreeSet size: "
                + decimalSet.size());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac NaturalOrderBoundariesDemo.java
java NaturalOrderBoundariesDemo
```

Kết quả stdout xác định:

```text
compare equal values: 0
equals equal values: true
first before later: true
equals(null): false
compareTo(null): NullPointerException
wrong type via raw Comparable: ClassCastException
natural TreeSet size: 2
nullable TreeSet first is null: true
BigDecimal compareTo zero: true
BigDecimal equals: false
BigDecimal TreeSet size: 1
```

Giải thích:

1. Hai `LessonOrder(1, "Core")` là hai instance độc lập nhưng bằng nhau theo `equals` và tương đương theo `compareTo`, vì cả hai phương thức dùng cùng cặp trường.
2. `LessonOrder(1, "Core")` đứng trước `LessonOrder(2, "Advanced")` vì `priority` nhỏ hơn; không cần quan tâm `title` khi đã phân định được theo `priority`.
3. `first.equals(null)` trả `false`, trong khi `first.compareTo(null)` ném `NullPointerException`. Đây là hai hợp đồng khác nhau, đều đúng.
4. Hàm `compareWithWrongType` cố tình dùng raw `Comparable`. Runtime từ chối `String` bằng `ClassCastException`, thay vì đưa ra một kết quả thứ tự giả.
5. `naturalSet` dùng thứ tự tự nhiên. Vì `first.compareTo(same) == 0`, `TreeSet` coi chúng là một phần tử theo thứ tự sắp xếp.
6. `nullableSet` có **`Comparator.nullsFirst` rõ ràng**, nên có thể chứa `null` và đặt nó trước các đối tượng hợp lệ. Không cần sửa `LessonOrder.compareTo(null)`.
7. `BigDecimal("1.0")` và `BigDecimal("1.00")` bằng nhau theo giá trị số học khi gọi `compareTo`, nhưng khác `scale` nên `equals` là `false`. `TreeSet` dùng quan hệ `compareTo == 0` làm tương đương khi sắp xếp, vì vậy chỉ giữ một phần tử.

## 5. `Comparator.nullsFirst`/`nullsLast`: cách định nghĩa thứ tự có `null`

Nếu ứng dụng thật sự có danh sách chứa `null` cần sắp xếp, hãy định nghĩa chính sách **ở bên ngoài thứ tự tự nhiên**, qua `Comparator`:

```java
Comparator<LessonOrder> nullFirst =
        Comparator.nullsFirst(Comparator.naturalOrder());
Comparator<LessonOrder> nullLast =
        Comparator.nullsLast(Comparator.naturalOrder());
```

Với `nullFirst`, `null` được xếp trước đối tượng không `null`; với `nullLast`, `null` được xếp sau. Khi hai đối số đều không `null`, comparator ủy quyền cho `compareTo` tự nhiên. Khi cả hai cùng `null`, comparator coi chúng bằng nhau theo thứ tự.

| So sánh | Thứ tự tự nhiên `x.compareTo(y)` | `nullsFirst(naturalOrder())` | `nullsLast(naturalOrder())` |
| --- | --- | --- | --- |
| `x` khác `null`, `y` khác `null` | Theo `compareTo` | Theo `compareTo` | Theo `compareTo` |
| `x == null`, `y != null` | Không gọi instance method được | `null` đứng trước | `null` đứng sau |
| `x != null`, `y == null` | `compareTo(null)` ném NPE | `x` đứng sau | `x` đứng trước |
| `x == null`, `y == null` | Không có receiver hợp lệ | `0` | `0` |

Chọn một trong hai tùy nghiệp vụ (ví dụ dữ liệu thiếu cần ưu tiên đầu danh sách hay đặt cuối). `Comparator` giúp chính sách này tường minh mà vẫn giữ hợp đồng `Comparable`.

**Cảnh báo về `null` ở hai cấp:** `Comparator.nullsFirst(Comparator.comparing(Person::getName))` chỉ xử lý **đối tượng `Person` có thể `null`**, không tự xử lý **`getName()` trả về `null`**. Nếu cả phần tử lẫn khóa so sánh có thể `null`, cần chọn comparator ở mỗi cấp:

```java
// Minh họa đặt tại nơi đã định nghĩa lớp Person có getName():
Comparator<Person> byName = Comparator.nullsFirst(
        Comparator.comparing(
                Person::getName,
                Comparator.nullsLast(Comparator.naturalOrder())
        )
);
```

Đây là đoạn minh họa kiểu `Person` do ứng dụng cung cấp, **không phải ví dụ biên dịch độc lập**. `nullsFirst` ngoài cùng xử lý `Person == null`; `nullsLast` bên trong xử lý `Person.getName() == null`.

Một `TreeSet`/`TreeMap` dùng thứ tự tự nhiên thường không nhận khóa `null`, vì cần phép so sánh tự nhiên. Chúng có thể nhận `null` nếu comparator do ứng dụng cung cấp **thật sự định nghĩa được** cách so với `null`. Không suy ra rằng mọi `SortedSet` hoặc mọi comparator đều chấp nhận `null`.

## 6. Kiểu khác nhau: `equals` có thể trả `false`, còn `compareTo` có thể ném CCE

Mục đích của `equals(Object)` là kiểm tra bằng nhau với một đối số `Object` bất kỳ; nếu khác kiểu và không thuộc quan hệ bằng nhau hợp lệ, kết quả thường là `false`:

```java
// Giả sử lesson là LessonOrder:
lesson.equals("Core"); // false: String không phải LessonOrder
```

Ngược lại, `compareTo` không phải phương thức có nghĩa “khác kiểu thì trả `1`”. Việc trả một số âm hoặc dương cho mọi kiểu không có quy tắc sắp xếp chung có thể phá tính đối xứng và bắc cầu. Tránh cố tạo quan hệ thứ tự tùy tiện chỉ để không ném ngoại lệ.

Generics tạo ra một đường ranh giới rõ:

- **Đúng kiểu tại compile time:** `Comparable<LessonOrder>` chỉ cho phép đối số `LessonOrder` hoặc `null`; compiler loại bỏ nhiều lời gọi sai.
- **Raw type/unchecked cast/reflection hoặc mã cũ:** có thể đưa đối số sai kiểu tại runtime; lúc này `ClassCastException` là tín hiệu phù hợp.
- **Khi cần so các kiểu khác nhau theo quy tắc ứng dụng:** dùng **`Comparator` trên một miền chung đã xác định**, ví dụ chuyển các dạng dữ liệu sang một key chung trước khi so. Không tự động trộn `String`, số và đối tượng nghiệp vụ trong một natural ordering vô căn cứ.

Không bắt ngoại lệ để thay bằng `0`; `0` có ý nghĩa **cùng thứ hạng** và có thể khiến `TreeSet` xem hai phần tử như một.

## 7. Ảnh hưởng đối với `TreeSet`, `TreeMap`, `Collections.sort`

Các API sắp xếp/dựa trên thứ tự sử dụng `compareTo` hoặc `Comparator.compare`:

- `TreeSet` dùng dấu của phép so sánh để xác định vị trí và tương đương theo thứ tự. Nếu hai phần tử có kết quả so sánh `0`, thường không thể lưu như hai phần tử phân biệt theo semantics của set đó.
- `TreeMap` dùng thứ tự để phân biệt khóa; nếu comparator coi hai khóa là tương đương (`0`), thêm khóa thứ hai sẽ tác động vào cùng entry logic theo thứ tự của map.
- `Collections.sort(list)` hoặc `list.sort(null)` dùng thứ tự tự nhiên; nếu có `null` hoặc phần tử không thể so sánh với nhau, thao tác có thể ném NPE/CCE. Với danh sách có `null` hợp lệ, hãy truyền comparator minh thị qua `list.sort(Comparator.nullsLast(...))`.

Đặc biệt, cấu trúc `SortedSet` và `SortedMap` có hành vi đúng theo quy tắc tập hợp/map thông thường khi thứ tự của chúng **nhất quán với `equals`**. Trong ví dụ `BigDecimal`, `compareTo` có thể trả `0` trong khi `equals` trả `false`, làm `TreeSet` có hành vi khác `HashSet` trên cùng dữ liệu. Đây là điều cần biết để chọn mô hình và tránh nhầm số lượng phần tử.

Nếu một lớp custom được dùng làm khóa `TreeMap` hoặc phần tử `TreeSet`, nên đặt các trường quyết định thứ tự là **bất biến** trong thời gian lưu. Nếu thay đổi trường tham gia `compareTo` sau khi chèn, cấu trúc cây có thể không còn sắp xếp và tra cứu đúng như kỳ vọng.

## 8. Checklist khi viết hoặc sử dụng `Comparable<T>`

1. **Miền kiểu:** lớp chỉ chấp nhận những đối tượng nào? `Comparable<T>` đã biểu diễn đúng miền này chưa?
2. **Null:** `x.compareTo(null)` có ném `NullPointerException` không? Có vô tình coi `null` là bằng một giá trị thật không?
3. **Kiểu không tương thích:** lời gọi sai kiểu qua raw/unchecked path có bị từ chối bằng `ClassCastException` thay vì trả một thứ tự giả không?
4. **Thứ tự toán học:** phép so sánh có giữ đối xứng dấu, bắc cầu và ngữ nghĩa `compareTo == 0` ổn định không?
5. **Quan hệ với `equals`:** nếu `compareTo == 0` khác `equals == true`, hành vi `TreeSet`/`TreeMap` có được hiểu và chấp nhận không?
6. **Chính sách null của collection:** nếu dữ liệu hợp lệ có `null`, đã dùng `Comparator.nullsFirst` hoặc `nullsLast` ở đúng cấp phần tử hoặc khóa chưa?
7. **Trạng thái mutable:** các trường tham gia thứ tự có được giữ ổn định khi đối tượng đang nằm trong cấu trúc sắp xếp không?
8. **Giá trị trả về:** phần code sử dụng có kiểm tra `< 0`, `== 0`, `> 0` thay vì so đúng `-1` hoặc `1` không?

**Ghi nhớ:** `Comparable.compareTo` định nghĩa thứ tự **giữa các giá trị thuộc miền so sánh**. `compareTo(null)` ném **`NullPointerException`**; kiểu không tương thích bị từ chối, thường bằng **`ClassCastException`** nếu vượt qua kiểm tra generic. Khi nghiệp vụ muốn sắp xếp cả `null`, hãy viết quy tắc riêng bằng **`Comparator.nullsFirst`/`nullsLast`**, và cân nhắc sự tương thích với `equals` trong `TreeSet`/`TreeMap`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-008">Không dùng phép trừ dễ tràn số để cài đặt compareTo()</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng phép trừ dễ tràn số để cài đặt `compareTo()`

## 1. Vấn đề: phép trừ không luôn cho biết đúng thứ tự

Khi cài đặt `Comparable<T>.compareTo(T other)`, một cách viết rất dễ gặp là lấy hai trường số trừ cho nhau:

```java
@Override
public int compareTo(Item other) {
    return this.priority - other.priority; // Nguy hiểm nếu int bị tràn.
}
```

Đây chỉ là đoạn mã minh họa bên trong một lớp `Item` giả định có trường `int priority`, **không phải chương trình độc lập**. Nó có vẻ hợp lý: hiệu âm khi giá trị bên trái nhỏ hơn, hiệu bằng 0 khi bằng nhau, hiệu dương khi lớn hơn. Nhưng trong Java, `int` và `long` là các kiểu số nguyên có **giới hạn**. Kết quả toán học của phép trừ có thể vượt phạm vi lưu trữ; khi đó kết quả số nguyên Java bị tràn và có thể **đảo dấu**.

Thí dụ với `int`:

```text
Integer.MAX_VALUE =  2147483647
Integer.MIN_VALUE = -2147483648

Theo toán học: 2147483647 - (-1) = 2147483648, một số dương.
Trong int Java: 2147483647 - (-1) = -2147483648 (tràn số).
```

Như vậy, dù `2147483647 > -1`, phép trừ `2147483647 - (-1)` lại cho kết quả **âm**, khiến `compareTo()` sai về mặt thứ tự. Lỗi này khó phát hiện khi chỉ thử các số nhỏ như 1, 2, 3.

**Cách viết đúng là dùng `Integer.compare(a, b)` cho `int`, `Long.compare(a, b)` cho `long`, hoặc `Comparator.comparingInt`/`comparingLong` khi tạo comparator.** Các API đó so sánh trực tiếp thứ tự của hai giá trị thay vì phụ thuộc phép trừ có khả năng tràn.

## 2. Hợp đồng của `compareTo()`: quan trọng là dấu, không phải độ lớn

Với một lớp `T implements Comparable<T>`, `a.compareTo(b)` phải có ý nghĩa:

| Kết quả | Cách hiểu |
| --- | --- |
| Nhỏ hơn 0 | `a` đứng **trước** `b` trong thứ tự tự nhiên. |
| Bằng 0 | `a` và `b` được coi là **tương đương về thứ tự**. |
| Lớn hơn 0 | `a` đứng **sau** `b`. |

Không có yêu cầu kết quả phải chính xác là `-1`, `0`, `1`: chỉ **dấu** mới là hợp đồng quan trọng. Ví dụ, một triển khai `String.compareTo` có thể trả một số âm hoặc dương có độ lớn khác 1.

Các quy tắc đặc biệt cần bảo đảm:

1. **Phản đối xứng về dấu:** `sign(a.compareTo(b)) == -sign(b.compareTo(a))`. Hai chiều không thể cùng âm hoặc cùng dương.
2. **Bắc cầu:** nếu `a > b` và `b > c` theo `compareTo`, thì `a > c`. Tương tự đối với quan hệ nhỏ hơn.
3. **Tương đương nhất quán:** khi `a.compareTo(b) == 0`, `a` và `b` phải so với mọi đối tượng thứ ba theo cùng dấu.
4. **Không thay đổi khi dữ liệu so sánh giữ nguyên:** phép so sánh nên ổn định theo trạng thái dùng để xác định thứ tự.

Với một đối tượng `x`, `x.compareTo(null)` thông thường ném `NullPointerException` theo hợp đồng `Comparable`; bản thân comparator có thể được cấu hình chính sách xử lý `null`, ví dụ `Comparator.nullsFirst` hoặc `nullsLast`.

`compareTo` có kết quả 0 không **bắt buộc** đồng nghĩa với `equals == true`, nhưng **nên nhất quán với `equals`** khi dùng lớp làm kiểu giá trị hoặc khóa trong collection có thứ tự. Ví dụ nổi tiếng là `BigDecimal`: `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0`, nhưng `equals` trả `false` vì khác scale. Phải xác định rõ điều này khi dùng `TreeSet`/`TreeMap`.

Nếu phép trừ gây sai dấu, ngay quy tắc đầu tiên đã có thể bị phá vỡ; sau đó thuật toán sắp xếp và cây cân bằng có thể hoạt động không đúng kỳ vọng.

## 3. Tràn `int` có thể phá cả phản đối xứng và bắc cầu

### 3.1. Cả hai chiều đều âm

Đặt `a = Integer.MAX_VALUE`, `b = -1`. Khi dùng hiệu `a - b`:

```text
a - b = 2147483647 - (-1)
      = -2147483648  (tràn int, dấu âm)

b - a = -1 - 2147483647
      = -2147483648  (không tràn, vẫn âm)
```

Cả `a.compareTo(b)` lẫn `b.compareTo(a)` đều có dấu âm. Điều này **vi phạm hợp đồng phản đối xứng về dấu**: không thể đồng thời khẳng định `a < b` và `b < a`.

Một sai lầm khác là lấy `Math.abs(a - b)` để so độ chênh lệch. `Math.abs(Integer.MIN_VALUE)` vẫn trả về `Integer.MIN_VALUE` vì `2147483648` không biểu diễn được bằng `int` dương. Dùng `Math.abs` không khắc phục vấn đề này.

### 3.2. Một bộ ba tạo thứ tự vòng

Chọn:

```text
a = Integer.MIN_VALUE = -2147483648
b = 0
c = Integer.MAX_VALUE =  2147483647
```

Với comparator sai `(x, y) -> x - y`:

```text
a - b = -2147483648 < 0, nên comparator nói a < b.
b - c = -2147483647 < 0, nên comparator nói b < c.
a - c = 1 > 0 do tràn int, nên comparator lại nói a > c.
```

Ta có `a < b`, `b < c`, nhưng `a > c`: **vi phạm tính bắc cầu**. Vì vậy, đây không đơn thuần là lỗi thứ tự của một cặp giá trị. Nó có thể khiến toàn bộ quan hệ thứ tự không còn hợp lệ.

Một thuật toán sắp xếp dựa vào comparator hợp lệ có thể cho thứ tự sai, và một số đường chạy của thư viện sắp xếp Java có thể phát hiện vi phạm hợp đồng và ném `IllegalArgumentException` với thông báo về hợp đồng comparator. Không được giả định rằng Java luôn phát hiện lỗi hoặc luôn ném ngoại lệ: hành vi tùy dữ liệu và đường đi thuật toán.

## 4. Cách an toàn với từng kiểu số

Java có các phương thức so sánh số nguyên theo **dấu thứ tự**:

```java
Integer.compare(aInt, bInt);
Long.compare(aLong, bLong);
Short.compare(aShort, bShort);
Byte.compare(aByte, bByte);
```

Các dòng trên là **lời gọi minh họa** với các biến đã tồn tại, không phải một chương trình độc lập. Phương thức trả số âm/0/dương theo giá trị thực của kiểu, không bị tràn do lấy hiệu.

Cài đặt `compareTo` theo kiểu điển hình:

```java
record Rank(int priority) implements Comparable<Rank> {
    @Override
    public int compareTo(Rank other) {
        return Integer.compare(priority, other.priority);
    }
}
```

Đối với `long`:

```java
record Sequence(long id) implements Comparable<Sequence> {
    @Override
    public int compareTo(Sequence other) {
        return Long.compare(id, other.id);
    }
}
```

Hai đoạn `record` là các khai báo kiểu độc lập có thể đặt ở top-level một tệp Java 17 (hoặc làm kiểu lồng hợp lệ); `record` tạo sẵn `equals/hashCode` dựa trên component và ở đây `compareTo` dùng đúng component đó.

Có thể viết phép so sánh phân nhánh thủ công:

```java
return a < b ? -1 : (a > b ? 1 : 0);
```

Nó cũng an toàn trước tràn số khi `a` và `b` cùng kiểu số nguyên có thứ tự. Tuy nhiên, `Integer.compare` hoặc `Long.compare` giúp biểu đạt ý định ngắn gọn và ít sai hơn.

### Nếu số không dấu hoặc dấu phẩy động?

- Với `int`/`long` đại diện **giá trị không dấu** theo các bit, dùng `Integer.compareUnsigned` hoặc `Long.compareUnsigned`. `Integer.compare`/ `Long.compare` coi số là **có dấu**. Chỉ chọn unsigned khi đây là yêu cầu dữ liệu thực tế.
- Với `float`/`double`, dùng `Float.compare` hoặc `Double.compare` khi cần thứ tự toàn phần theo quy tắc của wrapper, bao gồm `NaN` và phân biệt `-0.0` với `+0.0`. Không dùng phép trừ rồi ép sang `int`, vì còn có sai số, vô cực, NaN và kết quả ép kiểu không biểu đạt đúng quan hệ so sánh.
- Với `BigInteger`/`BigDecimal`, dùng `a.compareTo(b)` của chính kiểu đó; không cố chuyển sang `int`/`long` hoặc ép kết quả hiệu thành `int`.

## 5. Ví dụ Java 17: nhìn thấy tràn số và kết quả đúng

Tạo tệp `CompareOverflowDemo.java`:

```java
public class CompareOverflowDemo {
    static int wrong(int a, int b) {
        return a - b;
    }

    static int safe(int a, int b) {
        return Integer.compare(a, b);
    }

    static long wrongLong(long a, long b) {
        return a - b;
    }

    public static void main(String[] args) {
        int high = Integer.MAX_VALUE;
        int low = -1;

        System.out.println("high > low: " + (high > low));
        System.out.println("wrong(high, low): " + wrong(high, low));
        System.out.println("wrong(low, high): " + wrong(low, high));
        System.out.println("safe(high, low): " + safe(high, low));
        System.out.println("safe(low, high): " + safe(low, high));

        int min = Integer.MIN_VALUE;
        int mid = 0;
        int max = Integer.MAX_VALUE;

        System.out.println("bad min < mid: " + (wrong(min, mid) < 0));
        System.out.println("bad mid < max: " + (wrong(mid, max) < 0));
        System.out.println("bad min > max: " + (wrong(min, max) > 0));
        System.out.println("safe min < max: " + (safe(min, max) < 0));

        long big = Long.MAX_VALUE;
        System.out.println("wrong long sign: " + (wrongLong(big, -1L) < 0));
        System.out.println("safe long sign: " + (Long.compare(big, -1L) > 0));

        long gap = 4_294_967_296L;
        System.out.println("cast diff zero: " + ((int) (gap - 0L) == 0));
        System.out.println("compare long gap: " + (Long.compare(gap, 0L) > 0));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac CompareOverflowDemo.java
java CompareOverflowDemo
```

Stdout chính xác:

```text
high > low: true
wrong(high, low): -2147483648
wrong(low, high): -2147483648
safe(high, low): 1
safe(low, high): -1
bad min < mid: true
bad mid < max: true
bad min > max: true
safe min < max: true
wrong long sign: true
safe long sign: true
cast diff zero: true
compare long gap: true
```

Phân tích:

- Hai phép `wrong(high, low)` và `wrong(low, high)` đều âm, cho thấy **cùng một cặp nhưng hai chiều trái hợp đồng**.
- Bộ ba `min, mid, max` tạo thứ tự vòng với `wrong`: `min < mid < max` nhưng comparator kết luận `min > max`.
- Với `long`, `Long.MAX_VALUE - (-1L)` cũng tràn thành `Long.MIN_VALUE`; đừng nghĩ đổi `int` sang `long` là sửa tận gốc.
- Ép `(int)(longA - longB)` đặc biệt nguy hiểm: hai giá trị `long` khác nhau đúng `2^32` có hiệu khi ép sang `int` bằng **0**, khiến comparator sai ngay cả về việc coi hai giá trị là bằng thứ tự. `Long.compare` xử lý chính xác.

Nếu cần trình bày sai lệch giữa hai số mà không bị tràn, đó là **bài toán tính chênh lệch**, khác với bài toán **xác định thứ tự**. Khi thật sự cần hiệu chính xác vượt phạm vi kiểu, có thể dùng `Math.subtractExact` để phát hiện overflow hoặc `BigInteger` để biểu diễn số lớn, nhưng các cách này không phải lựa chọn cần thiết chỉ để viết `compareTo`.

## 6. So sánh nhiều trường: thứ tự khóa chính, khóa phụ

Một lớp thường có nhiều trường xác định thứ tự, chẳng hạn sắp xếp nhân viên theo `level` tăng dần, rồi `employeeId` tăng dần. Tuyệt đối không viết:

```java
// Sai: cả hai trường số đều có nguy cơ tràn; đây là đoạn minh họa.
int byLevel = this.level - other.level;
if (byLevel != 0) return byLevel;
return (int) (this.employeeId - other.employeeId);
```

Ở nhánh `employeeId`, ngoài overflow của `long` còn có lỗi ép xuống `int`. Nên thực hiện **so sánh theo dấu** cho từng trường và chỉ chuyển sang khóa phụ khi kết quả khóa trước là 0:

```java
@Override
public int compareTo(Employee other) {
    int byLevel = Integer.compare(this.level, other.level);
    if (byLevel != 0) {
        return byLevel;
    }
    return Long.compare(this.employeeId, other.employeeId);
}
```

Đoạn mã chỉ là phần thân phương thức minh họa cho `Employee` có hai trường tương ứng. Đối với nhiều trường kiểu chuỗi, sử dụng `String.compareTo` và xác định riêng chính sách `null` nếu dữ liệu có thể vắng.

Một công thức thay thế dùng `Comparator` có thể dễ đọc hơn:

```java
Comparator<Employee> ORDER =
        Comparator.comparingInt(Employee::level)
                  .thenComparingLong(Employee::employeeId);
```

`Comparator.comparingInt` và `thenComparingLong` dùng so sánh primitive an toàn. Các lệnh này là đoạn minh họa với lớp `Employee` có accessor `level()` và `employeeId()`. Không nên tạo comparator bằng `(a, b) -> a.level() - b.level()` chỉ để ngắn dòng.

### So sánh giảm dần không cần nhân với `-1`

Một thói quen khác là đảo thứ tự bằng `-ascendingCompare(a, b)`. Hợp đồng `Comparable` chỉ yêu cầu giá trị âm/0/dương, **không bảo đảm** kết quả của mọi `compareTo` chỉ thuộc `-1, 0, 1`. Nếu một comparator hợp lệ trả `Integer.MIN_VALUE`, lấy dấu âm của kết quả đó vẫn bằng `Integer.MIN_VALUE` do tràn.

Để đảo thứ tự, dùng `comparator.reversed()` hoặc so sánh **đảo đối số** bằng API an toàn, ví dụ `Integer.compare(b, a)`, thay vì nhân hoặc đổi dấu một kết quả comparator bất kỳ.

## 7. Ví dụ hoàn chỉnh Java 17: thứ tự tự nhiên nhiều trường

Tạo tệp `EmployeeOrderDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EmployeeOrderDemo {
    record Employee(int level, long employeeId, String name)
            implements Comparable<Employee> {

        @Override
        public int compareTo(Employee other) {
            int byLevel = Integer.compare(level, other.level);
            if (byLevel != 0) {
                return byLevel;
            }

            int byId = Long.compare(employeeId, other.employeeId);
            if (byId != 0) {
                return byId;
            }

            return name.compareTo(other.name);
        }
    }

    public static void main(String[] args) {
        Employee high = new Employee(Integer.MAX_VALUE, 10L, "H");
        Employee low = new Employee(Integer.MIN_VALUE, 11L, "L");
        Employee lowSecond = new Employee(Integer.MIN_VALUE, 12L, "S");

        System.out.println("high > low: " + (high.compareTo(low) > 0));
        System.out.println("low < high: " + (low.compareTo(high) < 0));
        System.out.println("low < lowSecond: " + (low.compareTo(lowSecond) < 0));

        List<Employee> employees =
                new ArrayList<>(List.of(high, lowSecond, low));
        employees.sort(Comparator.naturalOrder());

        System.out.println("first: " + employees.get(0).name());
        System.out.println("second: " + employees.get(1).name());
        System.out.println("third: " + employees.get(2).name());

        Comparator<Employee> byFields =
                Comparator.comparingInt(Employee::level)
                          .thenComparingLong(Employee::employeeId)
                          .thenComparing(Employee::name);

        System.out.println("same ordering: "
                + (Integer.signum(high.compareTo(low))
                   == Integer.signum(byFields.compare(high, low))));

        Comparator<Employee> descending = byFields.reversed();
        System.out.println("reversed high first: "
                + (descending.compare(high, low) < 0));
    }
}
```

Biên dịch/chạy:

```bash
javac EmployeeOrderDemo.java
java EmployeeOrderDemo
```

Stdout:

```text
high > low: true
low < high: true
low < lowSecond: true
first: L
second: S
third: H
same ordering: true
reversed high first: true
```

Giải thích: `level` được xét trước `employeeId`, sau đó mới xét `name`. Hai nhân viên cùng `Integer.MIN_VALUE` được xếp theo `employeeId` 11 trước 12; nhân viên `Integer.MAX_VALUE` xếp sau. Bộ `Comparator` tạo bằng các hàm so sánh trường biểu diễn đúng cùng thứ tự, còn `reversed()` dùng khi cần giảm dần.

Ở đây record `Employee` sinh sẵn `equals/hashCode` dựa trên cả ba component; `compareTo` cũng xét cả ba component, nên với dữ liệu `name` không null, ví dụ duy trì tính nhất quán tự nhiên giữa thứ tự và bình đẳng. Nếu cố tình chỉ so sánh `level` mà bỏ `employeeId/name`, hai record khác nhau có thể có `compareTo == 0`, điều này cần được quyết định rõ theo nghiệp vụ.

## 8. Những lỗi sửa sai tưởng đúng nhưng vẫn nguy hiểm

| Cách viết | Vấn đề | Thay thế |
| --- | --- | --- |
| `return a - b;` với `int` | Tràn, trả sai dấu, phá hợp đồng. | `Integer.compare(a, b)`. |
| `return (int) (aLong - bLong);` | Có thể tràn `long` và mất bit khi ép `int`. | `Long.compare(aLong, bLong)`. |
| `return Math.abs(a - b);` | Mất dấu thứ tự và `abs(MIN_VALUE)` vẫn âm. | Dùng API compare trực tiếp. |
| `return (a - b) > 0 ? 1 : ...;` | Kiểm tra **sau phép trừ** vẫn chịu overflow. | `a < b ? -1 : a > b ? 1 : 0`, hoặc API compare. |
| `return -other.compareTo(this);` | Đổi dấu `Integer.MIN_VALUE` có thể tràn nếu comparator trả giá trị đó. | `Comparator.reversed()` hoặc so sánh đối số đảo đúng cách. |
| So sánh khóa phụ bằng phép trừ sau khi khóa chính bằng nhau | Cùng lỗi overflow ở nhánh ít gặp. | `thenComparingInt`, `thenComparingLong`, hoặc `Long.compare`. |
| Chỉ kiểm tra số nhỏ và dương | Không bắt lỗi ở biên miền số, dấu khác nhau. | Kiểm tra `MIN_VALUE`, `MAX_VALUE`, âm, 0, dương. |

Ngoài overflow, một `compareTo` có thể sai hợp đồng vì dùng dữ liệu thay đổi theo thời gian, quy tắc xử lý `null` thiếu nhất quán, cài đặt không đối xứng ở các nhánh lớp con, hoặc so sánh theo một trường không ổn định khi đã đưa đối tượng vào `TreeSet`/`TreeMap`. Đừng xem việc thay dấu trừ bằng `Integer.compare` là đủ nếu các phần còn lại của chính sách thứ tự vẫn mâu thuẫn.

### Lưu ý với `TreeSet` và `TreeMap`

Các collection có thứ tự như `TreeSet` và `TreeMap` dựa vào `compareTo` hoặc `Comparator` để tìm kiếm và xác định hai khóa tương đương về thứ tự. Nếu comparator trả `0` cho hai khóa khác nhau do ép kiểu làm mất bit, collection có thể coi chúng là cùng vị trí khóa dù `equals` trả `false`. Nếu comparator sai bắc cầu, cấu trúc cây có thể không còn bảo đảm các thao tác `contains`/`get`/`remove` như mong đợi.

Không nên thay đổi các trường tham gia `compareTo`/`Comparator` trong khi đối tượng đang được dùng làm khóa `TreeMap` hoặc phần tử `TreeSet`, vì collection không tự xây dựng lại thứ tự khi trạng thái khóa đổi.

Với những kiểu mà `compareTo == 0` khác `equals == true` theo thiết kế (ví dụ `BigDecimal`), cần hiểu `TreeSet` sử dụng **tương đương về thứ tự** để xác định một phần tử đã có mặt. Đây là một chủ đề riêng nhưng liên quan trực tiếp đến việc chọn khóa so sánh và viết comparator không lỗi.

## 9. Cách kiểm thử một cài đặt `compareTo`

Thay vì chỉ gọi `sort` rồi nhìn danh sách, hãy kiểm tra **hợp đồng** của phép so sánh trên các mẫu đã chọn.

### Mẫu biên bắt buộc

Với trường `int`, nên có:

```text
Integer.MIN_VALUE
Integer.MIN_VALUE + 1
-1
0
1
Integer.MAX_VALUE - 1
Integer.MAX_VALUE
```

Với `long`, dùng tương tự `Long.MIN_VALUE` và `Long.MAX_VALUE`, cùng một cặp cách nhau `2^32` để bắt lỗi ép xuống `int`.

### Kiểm tra từng cặp hai chiều

Đối với mọi cặp mẫu `a` và `b`, cần xác nhận:

```text
sign(a.compareTo(b)) == -sign(b.compareTo(a))
```

Dùng `Integer.signum(result)` để lấy dấu `-1`, `0`, hoặc `1` **sau khi comparator trả kết quả**; không có phép trừ ở đây. Với phép so sánh thứ tự từ giá trị gốc, có thể đối chiếu dấu với `Integer.compare` hoặc `Long.compare`.

Nên thử ít nhất những cặp sau: `MIN` với `MAX`; `MAX` với `-1`; `MIN` với `1`; hai giá trị bằng nhau; hai giá trị sát nhau; và cặp `long` rất xa nhau.

### Kiểm tra bộ ba có thứ tự

Với bộ ba `a`, `b`, `c` và một comparator dự kiến tăng dần:

```text
Nếu compare(a,b) < 0 và compare(b,c) < 0
thì compare(a,c) phải < 0.
```

Tương tự cho chiều lớn hơn. Khi comparator có thể trả `0`, cần kiểm tra thêm rằng quan hệ tương đương theo `compare == 0` là bắc cầu và các đối tượng tương đương có cùng dấu khi so sánh với một đối tượng thứ ba. Một bộ kiểm thử tự động có thể duyệt tất cả cặp và bộ ba có thứ tự trên tập dữ liệu biên nhỏ.

Thử nghiệm hữu hạn giúp **tìm** sai lệch nhưng không chứng minh tuyệt đối mọi giá trị có thể có. Cách cài đặt dựa trên `Integer.compare`/`Long.compare` và chain nhất quán vẫn có nền tảng đáng tin cậy hơn thử nghiệm ngẫu nhiên của `a - b`.

### Checklist trước khi sử dụng

1. Tất cả trường số đều dùng phương thức `compare` phù hợp, không dùng phép trừ để quyết định dấu?
2. Mọi khóa phụ cũng sử dụng phép so sánh an toàn và chỉ chạy khi khóa trước bằng nhau?
3. Có ép kết quả `long` xuống `int`, dùng `Math.abs`, hoặc đảo dấu một giá trị comparator bất kỳ?
4. Các giá trị biên âm/dương, cực tiểu/cực đại được bao phủ?
5. Có kiểm tra hai hướng từng cặp và một số bộ ba, đặc biệt những bộ ba nằm gần biên?
6. `compareTo == 0` có phù hợp với ý nghĩa bằng nhau theo nghiệp vụ và với `equals` hay đã được ghi rõ lý do khác biệt?
7. Các trường tham gia thứ tự có ổn định khi đối tượng đã được lưu trong `TreeMap`/`TreeSet`?

## 10. Câu hỏi tự luyện

1. Vì sao `Integer.MAX_VALUE - (-1)` làm comparator dùng phép trừ trả dấu sai?
2. Nếu cả `a - b` và `b - a` cùng âm trong `int`, điều kiện nào của `compareTo` bị vi phạm?
3. Cho `a = Integer.MIN_VALUE`, `b = 0`, `c = Integer.MAX_VALUE`. Bộ ba này bộc lộ lỗi bắc cầu nào với `(x, y) -> x - y`?
4. Vì sao chỉ đổi kiểu `int` thành `long` không khắc phục hoàn toàn tràn số?
5. Vì sao `(int)(leftLong - rightLong)` còn nguy hiểm hơn việc so sánh `long` bằng phép trừ?
6. Với đối tượng có `level`, `id`, `name`, cách triển khai sắp xếp tăng dần theo cả ba trường là gì?
7. Khi cần thứ tự giảm dần, vì sao `comparator.reversed()` đáng tin cậy hơn phủ định trực tiếp một kết quả comparator tùy ý?
8. Một comparator trả `0` cho hai đối tượng có ID khác nhau có thể ảnh hưởng gì đến `TreeSet`?
9. Khi nào nên dùng `Integer.compareUnsigned` thay cho `Integer.compare`?

**Ghi nhớ:** `compareTo()` và `Comparator.compare()` cần trả **đúng dấu của quan hệ thứ tự**, không cần trả hiệu hai số. Phép trừ `a - b` trên `int`/`long` có thể overflow và phá cả phản đối xứng lẫn bắc cầu. Hãy dùng `Integer.compare`, `Long.compare`, và các phương thức xây dựng `Comparator` tương ứng cho mọi trường, kể cả khóa phụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="natural-ordering-009">Viết tài liệu khi thứ tự tự nhiên cố ý không nhất quán với equals()</a>

<details>
<summary>Xem chi tiết</summary>

# Viết tài liệu khi thứ tự tự nhiên cố ý không nhất quán với `equals()` trong Java

Một lớp Java triển khai `Comparable<T>` có thể quy định **thứ tự tự nhiên** thông qua `compareTo()`. Thông thường nên thiết kế sao cho hai đối tượng so sánh bằng `0` cũng là hai đối tượng bằng nhau theo `equals()`. Tuy nhiên, Java **khuyến nghị mạnh mẽ nhưng không bắt buộc** hai quan hệ phải nhất quán. Có trường hợp nghiệp vụ chủ ý dùng một tiêu chí cho **định danh hoặc giá trị đầy đủ** và một tiêu chí khác cho **thứ tự tự nhiên**.

Khi cố ý chọn thiết kế như vậy, **tài liệu API phải nói rõ sự khác biệt**, định nghĩa chính xác ý nghĩa của `compareTo() == 0`, nêu hệ quả lên `TreeSet`/`TreeMap`, và hướng dẫn cách giữ tính duy nhất theo `equals()` nếu người dùng API cần điều đó. Chỉ ghi “implements Comparable” hoặc “sorts by priority” là chưa đủ.

## 1. Hiểu chính xác hai quan hệ

Giả sử `a` và `b` cùng thuộc lớp `T`:

- **Bằng nhau theo `equals()`**: `a.equals(b)` có nghĩa hai đối tượng cùng một giá trị logic theo hợp đồng của lớp. Nếu ghi đè `equals()`, `hashCode()` phải nhất quán: hai đối tượng bằng nhau có cùng hash.
- **Tương đương theo thứ tự tự nhiên**: `a.compareTo(b) == 0` nghĩa chúng thuộc cùng một **nhóm tương đương của phép so sánh**; trong một collection có thứ tự dùng quan hệ này, chúng được xem là trùng về mặt sắp xếp/khóa.
- **Nhất quán với `equals()`** nghĩa với **mọi** cặp `a`, `b`, hai biểu thức `(a.compareTo(b) == 0)` và `a.equals(b)` luôn cùng giá trị boolean.
- **Không nhất quán** chỉ cần tồn tại một cặp mà hai biểu thức trả kết quả khác nhau. Ví dụ `compareTo() == 0` nhưng `equals()` là `false`; chiều ngược lại cũng là một dạng bất nhất có thể gây hại.

Không nhất quán với `equals()` **không đồng nghĩa** phương thức `compareTo()` được phép tùy tiện. `Comparable` vẫn yêu cầu quan hệ thứ tự ổn định và hợp lệ: dấu của phép so sánh đảo chiều phải đối nhau, quan hệ lớn hơn phải có tính bắc cầu, và những phần tử so bằng `0` phải có cùng dấu khi so sánh với mọi phần tử thứ ba. Kết quả âm/không/dương mới quan trọng, không phải chính xác `-1`, `0`, `1`.

Một ví dụ nghiệp vụ: `RankedItem(id, priority)` có danh tính giá trị gồm cả mã `id` và `priority` (như một record), nhưng thứ tự tự nhiên **chỉ so `priority`**. Hai mục mã khác nhau cùng mức ưu tiên không bằng nhau theo `equals()`, song `compareTo()` trả `0`. Thiết kế có thể dùng được, nhưng cần công bố rõ rằng tập có thứ tự sẽ giữ **tối đa một mục trên mỗi mức ưu tiên**.

## 2. Vì sao phải ghi tài liệu rõ ràng?

`Comparable<T>` quy định thứ tự tự nhiên được dùng mặc định bởi một số API:

- `Collections.sort(list)`, `List.sort(null)` và `Arrays.sort(array)` với phần tử so sánh được bằng thứ tự tự nhiên.
- `TreeSet<E>` và `TreeMap<K,V>` khi được tạo **không truyền `Comparator`**.
- `PriorityQueue<E>` khi được tạo không truyền comparator: thứ tự ưu tiên dựa trên `compareTo()`.

Đáng chú ý, API **sắp xếp danh sách** vẫn giữ các phần tử dù `compareTo() == 0`. Nhưng `TreeSet` và `TreeMap` dùng **kết quả so sánh bằng `0` để quyết định phần tử/khóa trùng**. Đây là nguồn ngạc nhiên lớn nhất cho người sử dụng API.

| Hành vi | Khi `a.compareTo(b) == 0` nhưng `!a.equals(b)` |
| --- | --- |
| `HashSet.add(a); add(b)` | Thường giữ **cả hai**, vì đánh giá trùng theo `equals()` và hash tương ứng |
| `TreeSet` dùng thứ tự tự nhiên | Chỉ giữ **một**, lần thêm thứ hai trả `false` |
| `HashMap` dùng `a`, `b` làm hai khóa | Giữ **hai khóa** khác nhau theo `equals()` |
| `TreeMap` dùng thứ tự tự nhiên | `put(b, value2)` có thể **thay giá trị** của khóa tương đương `a` |
| `List.sort(null)` / `Collections.sort(list)` | Sắp xếp, **không loại bản ghi** cùng thứ hạng |
| `PriorityQueue` | Dùng thứ tự lấy phần tử, **không loại trùng** chỉ vì `compareTo() == 0` |

Những collection được xây dựng theo thứ tự tự nhiên vẫn vận hành nhất quán với **quy tắc so sánh** của chúng, nhưng `TreeSet`/`TreeMap` có thể không tuân trực giác và hợp đồng `Set`/`Map` được định nghĩa theo `equals()` của phần tử/khóa. Việc ghi chú giúp người gọi quyết định kiểu collection, comparator và mô hình khóa đúng.

**Hệ quả khó nhận thấy:** `treeSet.contains(x)` có thể trả `true` ngay cả khi tập không chứa phần tử nào `equals(x)`, miễn là một phần tử hiện có so sánh bằng `0` với `x`. Tương tự, `treeMap.get(x)` có thể trả giá trị của một khóa khác theo `equals()` nhưng bằng `0` theo `compareTo()`.

## 3. Điều tối thiểu phải ghi trong Javadoc

Tài liệu của `Comparable` trong Java SE 17 khuyến nghị một câu cảnh báo chuẩn cho trường hợp bất nhất có chủ ý:

> Note: this class has a natural ordering that is inconsistent with equals.

Cần ghi **cảnh báo này ở tài liệu của kiểu** (hoặc tài liệu public API dễ tìm), bên cạnh mô tả định nghĩa thứ tự. Có thể nhắc lại tại Javadoc của `compareTo()` và hướng dẫn dùng collection. Câu cảnh báo là điểm bắt đầu; tài liệu tốt phải trả lời được những điều sau:

1. **`equals()` dùng những trường nào?** Nói rõ hai đối tượng được xem là cùng giá trị khi nào.
2. **`compareTo()` dùng những trường nào và chiều thứ tự ra sao?** Ví dụ theo `priority` tăng dần; không dùng `id` để phá hòa.
3. **Khi nào `compareTo()` trả `0` dù `equals()` là `false`?** Nêu cặp minh họa cụ thể.
4. **Vì sao chủ ý thiết kế khác nhau?** Chẳng hạn nghiệp vụ muốn tạo nhóm mức ưu tiên, thay vì gộp định danh.
5. **Ảnh hưởng lên tập/map có thứ tự là gì?** Mô tả trùng phần tử, thay thế giá trị và kết quả `contains/get`.
6. **Làm thế nào nếu muốn giữ từng đối tượng khác nhau?** Chọn `HashSet`/`HashMap` khi muốn `equals()` quyết định tính duy nhất, hoặc truyền comparator nhất quán với equality vào `TreeSet`/`TreeMap` nếu thật sự cần cả thứ tự.
7. **Các giới hạn khác**: `null` có hỗ trợ không, tiêu chí có bất biến khi đối tượng nằm trong collection không, và phương thức có ổn định với mọi phiên bản không.

Không nhất thiết cần một Javadoc quá dài, nhưng người đọc phải dự đoán được hành vi collection **trước khi dùng lớp làm khóa hoặc phần tử tập hợp**.

## 4. Ví dụ tài liệu đầy đủ ngay trên lớp (Java 17+)

Trong ví dụ sau, một `record` có `id` và `priority`:

- `equals()`/`hashCode()` do record sinh tự động xét **cả hai component**.
- `compareTo()` cố tình chỉ xét `priority` để thiết lập thứ tự tự nhiên theo mức ưu tiên.
- Hai mục cùng ưu tiên nhưng khác mã ID **khác nhau theo `equals()`** và **tương đương theo thứ tự**.
- `id` được kiểm tra không null; bản thân `String` và `int` bất biến, làm quan hệ so sánh ổn định.

Lưu toàn bộ chương trình vào **`DocumentedOrderDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Một mục xếp theo priority tăng dần.
 *
 * <p>Hai mục bằng nhau theo equals khi cả id và priority
 * bằng nhau. Thứ tự tự nhiên chỉ xét priority; các mục
 * khác id nhưng cùng priority so sánh bằng 0.
 *
 * <p>Note: this class has a natural ordering that is
 * inconsistent with equals.
 *
 * <p>TreeSet/TreeMap không truyền Comparator coi các mục
 * cùng priority là một phần tử hoặc khóa tương đương.
 * Nếu cần giữ các id khác nhau, dùng HashSet/HashMap hoặc
 * comparator xét thêm id.
 */
record RankedItem(String id, int priority)
        implements Comparable<RankedItem> {

    RankedItem {
        Objects.requireNonNull(id, "id");
    }

    /**
     * So sánh thứ tự tự nhiên theo priority tăng dần,
     * cố ý không xét id. Trả về 0 với các id khác nhau
     * nhưng cùng priority.
     */
    @Override
    public int compareTo(RankedItem other) {
        Objects.requireNonNull(other, "other");
        return Integer.compare(priority, other.priority);
    }
}

public class DocumentedOrderDemo {
    public static void main(String[] args) {
        RankedItem a = new RankedItem("A", 10);
        RankedItem b = new RankedItem("B", 10);
        RankedItem unseen = new RankedItem("C", 10);

        System.out.println("equals: " + a.equals(b));
        System.out.println("compareTo zero: "
                + (a.compareTo(b) == 0));

        Set<RankedItem> hashed =
                new HashSet<>(List.of(a, b));
        Set<RankedItem> ordered =
                new TreeSet<>(List.of(a, b));

        System.out.println("HashSet size: " + hashed.size());
        System.out.println("TreeSet size: " + ordered.size());
        System.out.println("TreeSet contains unseen: "
                + ordered.contains(unseen));

        List<RankedItem> list =
                new ArrayList<>(List.of(a, b));
        list.sort(null);
        System.out.println("sorted list size: " + list.size());

        Map<RankedItem, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        hashMap.put(b, "second");

        Map<RankedItem, String> treeMap = new TreeMap<>();
        treeMap.put(a, "first");
        treeMap.put(b, "second");

        System.out.println("HashMap size: " + hashMap.size());
        System.out.println("TreeMap size: " + treeMap.size());
        System.out.println("TreeMap lookup unseen: "
                + treeMap.get(unseen));
        System.out.println("TreeMap first key: "
                + treeMap.keySet().iterator().next().id());

        Comparator<RankedItem> completeOrder =
                Comparator.comparingInt(RankedItem::priority)
                          .thenComparing(RankedItem::id);
        Set<RankedItem> distinctSorted =
                new TreeSet<>(completeOrder);
        distinctSorted.add(a);
        distinctSorted.add(b);
        System.out.println("TreeSet with tiebreaker: "
                + distinctSorted.size());
    }
}
```

Biên dịch và chạy:

```bash
javac DocumentedOrderDemo.java
java DocumentedOrderDemo
```

Đầu ra:

```text
equals: false
compareTo zero: true
HashSet size: 2
TreeSet size: 1
TreeSet contains unseen: true
sorted list size: 2
HashMap size: 2
TreeMap size: 1
TreeMap lookup unseen: second
TreeMap first key: A
TreeSet with tiebreaker: 2
```

**Giải thích các kết quả ít trực quan:**

- `HashSet` và `HashMap` phân biệt `"A"` với `"B"` theo equality của record.
- `TreeSet` chỉ giữ `a` vì `b.compareTo(a) == 0`. `contains(unseen)` cũng trả `true` do `"C"` cùng `priority = 10`, dù chưa hề được thêm vào và không `equals()` với `"A"`.
- `TreeMap` coi `a`, `b`, `unseen` là các khóa tương đương theo `priority`. Lần `put(b, "second")` thay giá trị của mapping trước, nên map có kích thước `1`. Đối tượng khóa lưu lại vẫn là `a`, nên `"TreeMap first key"` là `"A"`; tra cứu bằng `unseen` nhận `"second"`.
- `List.sort(null)` chỉ sắp xếp theo thứ tự tự nhiên; danh sách **không mất phần tử**. Với hai phần tử bằng thứ tự, thuật toán sort ổn định của List cũng giữ thứ tự tương đối.
- `completeOrder` xét `priority` rồi `id`, vì vậy cùng ưu tiên nhưng khác ID được phân biệt. Với record trong ví dụ này, so sánh hai trường phù hợp định nghĩa equality dựa trên cả hai component.

**Quan trọng:** comparator `completeOrder` có thể phù hợp `equals()` **cho kiểu dữ liệu cụ thể này**, vì record equality xét đúng `id` và `priority`. Nếu equality của một kiểu khác chỉ dựa vào `id` nhưng comparator còn xét trạng thái phụ mutable, chỉ thêm `thenComparing(id)` chưa chắc đã tạo thứ tự nhất quán với equality. Cần kiểm chứng cặp quan hệ trên **mọi trường hợp**, không chỉ hai ví dụ minh họa.

## 5. Ví dụ thư viện chuẩn: `BigDecimal`

`BigDecimal` là trường hợp chuẩn, được JDK nhắc tới trong tài liệu `Comparable`:

- `new BigDecimal("2.0")` có scale `1`, `new BigDecimal("2.00")` có scale `2`.
- Chúng cùng giá trị số học, nên `compareTo()` trả `0`.
- `equals()` của `BigDecimal` xem xét cả **giá trị không chia thang và scale** nên hai đại diện có scale khác nhau có thể không bằng nhau.

Tạo file **`BigDecimalOrderDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class BigDecimalOrderDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("2.0");
        BigDecimal y = new BigDecimal("2.00");

        System.out.println("x scale: " + x.scale());
        System.out.println("y scale: " + y.scale());
        System.out.println("equals: " + x.equals(y));
        System.out.println("compareTo zero: "
                + (x.compareTo(y) == 0));
        System.out.println("HashSet size: "
                + new HashSet<>(List.of(x, y)).size());
        System.out.println("TreeSet size: "
                + new TreeSet<>(List.of(x, y)).size());
    }
}
```

Lệnh:

```bash
javac BigDecimalOrderDemo.java
java BigDecimalOrderDemo
```

Stdout:

```text
x scale: 1
y scale: 2
equals: false
compareTo zero: true
HashSet size: 2
TreeSet size: 1
```

Đó là **hành vi có chủ ý của API**, không phải lỗi Java. Với bài toán tài chính, giá trị số học và biểu diễn thập phân đôi khi mang ý nghĩa khác nhau; lập trình viên cần biết đang yêu cầu quan hệ nào. Không nên tự động gọi `stripTrailingZeros()` để “sửa” mọi trường hợp: phép chuẩn hóa có thể thay đổi scale và cách biểu diễn (kể cả dạng số mũ), tác động đến nghiệp vụ cần giữ scale ban đầu.

Văn bản chính thức: [Java SE 17 – Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html) và [Java SE 17 – BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).

## 6. Javadoc nên đặt ở đâu và viết sâu đến mức nào?

### 6.1. Tài liệu cấp lớp/record là nơi chính

Người dùng thường thấy thông tin khi tra cứu lớp trước lúc dùng nó làm khóa. Cấp lớp nên nêu:

- Quan hệ equality (những thành phần quyết định bằng nhau).
- Quan hệ thứ tự tự nhiên (những thành phần, chiều tăng/giảm).
- Câu cảnh báo chuẩn về việc không nhất quán.
- Hệ quả lên sorted collection và lựa chọn thay thế.

Nếu API công khai nhiều overload hoặc có comparator phụ trợ, tài liệu lớp cũng nên trỏ đến phương án phù hợp. Ví dụ: `Comparator.comparing(RankedItem::id)` chỉ nhất quán với equality khi ID **tự nó đủ** làm danh tính. Với record gồm `id` và `priority`, comparator chỉ dựa vào ID sẽ gộp những bản ghi cùng ID nhưng khác priority, vẫn bất nhất với equality; cần kiểm tra đủ trường nếu mục tiêu là nhất quán hoàn toàn.

### 6.2. Tài liệu `compareTo()` nói rõ quan hệ trả `0`

Ở phương thức nên mô tả giá trị âm/0/dương và đặc biệt giải thích khi trả 0, ví dụ:

> So sánh theo mức ưu tiên tăng dần. Hai mục khác ID nhưng cùng mức ưu tiên được xem là tương đương theo thứ tự và `compareTo()` trả 0.

Nếu muốn phù hợp chuẩn Javadoc, thêm các phần diễn giải `@param other`, `@return`, `@throws NullPointerException` và `@see` khi hữu ích. Không nên chỉ ghi “so sánh hai đối tượng” hoặc đưa duy nhất một ví dụ không chỉ ra điều kiện trả `0`.

Về `null`: đối với `Comparable.compareTo(null)`, cách thông thường và theo hợp đồng là ném `NullPointerException`, dù `equals(null)` luôn `false`. Nếu cần xếp các phần tử nullable của **collection**, thường dùng `Comparator.nullsFirst()` hoặc `Comparator.nullsLast()` ở nơi gọi, thay vì làm lớp chấp nhận đối tượng null trong thứ tự tự nhiên.

### 6.3. README, hướng dẫn sử dụng và ví dụ phải cùng nói một quy tắc

Javadoc chỉ có ích nếu ví dụ và tài liệu tích hợp không gây hiểu nhầm. Một mục hướng dẫn có thể viết:

- **Muốn khử trùng theo toàn bộ giá trị (`equals`)**: dùng `HashSet`/`HashMap` với `hashCode()` nhất quán.
- **Muốn phân nhóm/tối đa một giá trị theo thứ hạng**: `TreeSet`/`TreeMap` với natural ordering đó có thể phù hợp, nhưng lựa chọn phần tử giữ lại và cập nhật map phải rõ ràng.
- **Muốn vừa sắp xếp vừa giữ các phần tử phân biệt**: dùng `List.sort()` nếu được phép trùng, hoặc truyền một comparator có kết quả `0` khớp quan hệ equality cho `TreeSet`/`TreeMap`.
- **Muốn thứ tự theo thứ hạng nhưng tính duy nhất theo ID**: thường tốt hơn nên lưu trong map theo ID rồi sắp xếp các value khi cần, hoặc chọn cấu trúc phù hợp; không dùng `TreeSet` với phép so sánh chỉ xét thứ hạng và kỳ vọng nó giữ đủ ID.

Phải phân biệt **tính duy nhất** với **thứ tự hiển thị**. Một comparator nhóm theo thứ hạng có thể tốt cho phân nhóm, nhưng không tự động là định nghĩa identity tốt cho khóa.

## 7. Khi nào thực sự nên chủ ý không nhất quán?

Tình huống có thể cân nhắc:

1. **Mô hình giá trị có nhiều biểu diễn hợp lệ của cùng độ lớn**, như `BigDecimal` và scale: equality giữ thông tin biểu diễn, thứ tự tự nhiên chỉ xét độ lớn.
2. **Miền nghiệp vụ định nghĩa thứ hạng cơ bản theo một tiêu chí**, trong khi đối tượng lưu thêm thông tin phân biệt: dùng `compareTo()` để biểu diễn lớp tương đương theo thứ hạng nếu đây thực sự là khái niệm tự nhiên duy nhất.
3. **Tương thích API đã công bố**: đổi ngữ nghĩa `equals()` hay `compareTo()` của một kiểu công khai có thể là breaking change; cần tài liệu minh bạch về quan hệ đang tồn tại.

Không nên mặc nhiên chọn thiết kế bất nhất chỉ để tiết kiệm một phép so sánh phụ. Nếu lớp có nhiều cách sắp xếp hợp lý (theo ID, điểm, tên, thời gian...), hãy cân nhắc **không triển khai `Comparable`** và yêu cầu người gọi cung cấp `Comparator`. Cách này giảm nguy cơ người dùng mặc định chọn natural ordering với nghĩa không mong muốn.

Một phương án khác là cho thứ tự tự nhiên phù hợp `equals()` (ví dụ `priority` rồi `id` khi record equality đúng hai trường), và cung cấp comparator riêng `BY_PRIORITY` để phục vụ nhóm theo mức ưu tiên. Khi đó phần tài liệu vẫn cần cảnh báo rằng **Comparator này không nhất quán với equality** nếu dùng trong `TreeSet`, dù thứ tự tự nhiên của lớp nhất quán.

## 8. Hợp đồng `compareTo()` vẫn bắt buộc tuân thủ

Sự bất nhất được cho phép chỉ là khác biệt giữa **định nghĩa nhóm tương đương của thứ tự** và **định nghĩa equality**. Nó không cho phép:

- So sánh không bắc cầu, ví dụ `a < b`, `b < c` nhưng `a > c`.
- Hai đối tượng so bằng `0` nhưng khi so với đối tượng thứ ba lại trả dấu khác nhau.
- Kết quả thay đổi tùy đồng hồ, số ngẫu nhiên hoặc trạng thái mutable mà collection không kiểm soát.
- Dùng phép trừ `return a.priority - b.priority;` làm tiêu chí `int` khi có nguy cơ tràn số. Dùng `Integer.compare(a.priority, b.priority)` như ví dụ.
- Gọi `compareTo()` dựa trên một tiêu chí khác hoàn toàn từ lần này sang lần khác hoặc thay đổi tiêu chí trong khi đối tượng nằm trong `TreeSet`/`TreeMap`.

Cũng đừng sửa `hashCode()` để cố ép bằng nhau theo `compareTo()`: `hashCode()` cần phù hợp **`equals()`**, không nhất thiết phù hợp các nhóm do thứ tự tự nhiên định nghĩa. Đối với hai đối tượng `compareTo() == 0` nhưng `equals() == false`, hash **được phép bằng hoặc khác nhau**.

Nếu đối tượng có trường tham gia thứ tự tự nhiên có thể thay đổi khi đã nằm trong một sorted collection, phép tìm kiếm/thứ tự của cây có thể trở nên không còn phù hợp. Giữ khóa/tiêu chí so sánh ổn định trong suốt thời gian lưu; record và lớp bất biến thường dễ đảm bảo hơn.

## 9. Kiểm thử hợp đồng và xác minh tài liệu

Bộ test nên không chỉ xác minh một danh sách đã sắp đúng. Cần kiểm tra **những ca thể hiện sự bất nhất có chủ ý** và những quy tắc thứ tự bắt buộc:

| Trường hợp | Kỳ vọng khi tự nhiên chỉ xét `priority` |
| --- | --- |
| Cùng `id`, cùng `priority` | `equals() == true`, `compareTo() == 0`, hash bằng nhau |
| Khác `id`, cùng `priority` | `equals() == false`, `compareTo() == 0` |
| Khác `priority` | `compareTo()` âm/dương đúng chiều |
| So với chính nó | `compareTo() == 0`, `equals() == true` |
| Dãy ba mức ưu tiên 1, 2, 3 | Thứ tự bắc cầu và ổn định |
| `compareTo(null)` | `NullPointerException` theo hợp đồng |
| Hai phần tử khác ID cùng priority trong `TreeSet` | `size()` là 1 |
| Hai khóa khác ID cùng priority trong `TreeMap` | Khóa tương đương được dùng cho cùng một mapping |
| Cùng hai phần tử trong `HashSet` | `size()` là 2 |
| Cùng hai phần tử trong `List.sort(null)` | Vẫn có 2 phần tử sau sắp xếp |

Với kiểu dữ liệu phức tạp, nên bổ sung property-based tests cho đối xứng dấu, bắc cầu và tính nhất quán khi so với đối tượng thứ ba. Nếu class là API công khai, một ca regression test có thể dùng chính cặp ví dụ trong Javadoc để bảo đảm tài liệu và hành vi không bị lệch khi refactor.

Nếu nhà phát triển về sau “sửa” `compareTo()` bằng cách thêm `id` làm tiêu chí phá hòa, hai đối tượng từng so bằng `0` có thể trở thành khác nhóm. Đây **không chỉ là thay đổi hiển thị**, mà có thể làm `TreeSet` giữ thêm phần tử hoặc `TreeMap` có thêm khóa. Thay đổi như vậy cần đánh giá tương thích, cập nhật tài liệu và test chứ không tự ý xem là tối ưu nội bộ.

## 10. Mẫu checklist trước khi phát hành

- [ ] Javadoc của class công bố rõ **natural ordering intentionally inconsistent with equals** bằng câu cảnh báo chuẩn hoặc câu tương đương dễ thấy.
- [ ] Mô tả chính xác tiêu chí và chiều thứ tự; đặc biệt nói rõ **khi nào `compareTo()` trả `0`**.
- [ ] Mô tả rõ thuộc tính nào quyết định `equals()` và `hashCode()`; không sửa hash dựa trên tiêu chí so sánh riêng.
- [ ] Có ví dụ hai đối tượng `equals()` khác nhau nhưng `compareTo() == 0`, hoặc chiều bất nhất thực sự mà lớp hỗ trợ.
- [ ] Nêu tác động tới `TreeSet` (`add`/`contains`) và `TreeMap` (`put`/`get`) khi không truyền comparator.
- [ ] Hướng dẫn lựa chọn `HashSet`/`HashMap`, `List.sort()` hoặc comparator nhất quán với equality tùy ý định.
- [ ] Kiểm thử thứ tự hợp lệ, bắc cầu, `null`, ổn định tiêu chí và bất biến của khóa trong collection.
- [ ] Nếu API có thay đổi quy tắc so sánh, cập nhật Javadoc, README, test và đánh giá tác động tương thích.

**Ghi nhớ:** một thứ tự tự nhiên có thể cố ý không nhất quán với `equals()` mà vẫn thỏa hợp đồng `Comparable`. Khi chọn thiết kế đó, tài liệu phải giải thích **định nghĩa hai quan hệ, lý do chúng khác nhau, và những hành vi collection bị ảnh hưởng**. Tránh để người dùng chỉ phát hiện sự khác biệt sau khi `TreeSet` làm mất một phần tử hoặc `TreeMap` thay một giá trị.


</details>

- [Quay lại đầu trang](#back-to-top)
