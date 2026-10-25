<a id="back-to-top"></a>

# Mảng và cấu trúc lồng

## Menu
- [equals() và hashCode() của mảng mặc định dựa trên danh tính](#arrays-nested-001)
- [Arrays.equals() và Arrays.hashCode() cho mảng một chiều](#arrays-nested-002)
- [Arrays.deepEquals() và Arrays.deepHashCode() cho mảng lồng](#arrays-nested-003)
- [Objects.deepEquals() và cách xử lý hai đối số](#arrays-nested-004)
- [Mảng nguyên thủy khác mảng lớp bao](#arrays-nested-005)
- [Chọn so sánh theo thứ tự hay theo tập giá trị trước khi viết mã](#arrays-nested-006)
- [Trường mảng trong lớp tự viết và yêu cầu đồng bộ equals() với hashCode()](#arrays-nested-007)
- [Giới hạn khi cấu trúc có tham chiếu vòng](#arrays-nested-008)
- [Kiểm tra null, mảng rỗng và phần tử mảng null](#arrays-nested-009)

## <a id="arrays-nested-001">equals() và hashCode() của mảng mặc định dựa trên danh tính</a>

<details>
<summary>Xem chi tiết</summary>

# `equals()` và `hashCode()` của mảng mặc định dựa trên danh tính

## 1. Mục tiêu: mảng có dữ liệu giống nhau chưa chắc “bằng nhau”

Trong Java, **mảng (array) là đối tượng**, kể cả mảng của kiểu nguyên thủy như `int[]`, `double[]`. Nhưng mảng **không ghi đè** `Object.equals(Object)` hoặc `Object.hashCode()` để so sánh các phần tử. Theo mặc định, mảng dùng quy tắc **danh tính đối tượng** (object identity):

- `arrayA.equals(arrayB)` là `true` khi `arrayA` và `arrayB` trỏ đến **chính cùng một mảng**, không phải chỉ vì hai mảng có các phần tử giống nhau.
- `arrayA == arrayB` cũng kiểm tra cùng tham chiếu đối với hai biến kiểu mảng.
- `arrayA.hashCode()` là mã băm theo danh tính mặc định của đối tượng, **không dựa trên nội dung các phần tử**. Không được suy ra nó là địa chỉ bộ nhớ, hay mỗi mảng chắc chắn có hash khác mọi mảng khác.
- Muốn so sánh hoặc băm **theo nội dung**, cần chọn API của `java.util.Arrays` phù hợp với mảng một chiều hay mảng lồng nhau; hoặc thiết kế lớp bao (value object) với `equals`/`hashCode` rõ ràng.

Ví dụ độc lập về ngữ nghĩa:

```java
int[] first = {1, 2, 3};
int[] second = {1, 2, 3};
int[] alias = first;

System.out.println(first == second);        // false
System.out.println(first.equals(second));   // false
System.out.println(first == alias);         // true
System.out.println(first.equals(alias));    // true
```

Đây là đoạn mã minh họa (đặt trong một phương thức khi chạy), chưa phải chương trình độc lập. `first` và `second` là **hai đối tượng mảng** riêng biệt; `alias` chỉ tham chiếu lại mảng `first`.

Ngược lại, `Arrays.equals(first, second)` là `true` vì hai mảng `int[]` có các phần tử tương ứng bằng nhau. Hai quan hệ này trả lời **hai câu hỏi khác nhau**: có phải cùng đối tượng không, và nội dung có tương đương không.

## 2. Vì sao `Object.equals` và `Object.hashCode` hoạt động theo danh tính với mảng?

Java quy định mọi kiểu mảng đều là kiểu tham chiếu, kế thừa hành vi thích hợp từ `Object`. Bản thân mảng cung cấp độ dài `length` và các phần tử truy cập bằng chỉ số, nhưng không có một `equals` hay `hashCode` tự sinh theo nội dung như nhiều lớp giá trị.

Nếu một lớp thông thường không ghi đè `equals`, `Object.equals` mặc định so sánh danh tính: `this == other`. Đối với mảng cũng vậy. `Object.hashCode` cho mã băm mặc định tương ứng với danh tính đối tượng. Hai lần gọi trên **cùng một mảng**, khi không có yếu tố bất thường ngoài hợp đồng, trả về cùng mã băm trong một lần chạy.

Điều cần nhớ:

1. **Cùng tham chiếu ⇒ `equals` đúng và hash giống nhau.** Một mảng vẫn là chính nó ngay cả khi các phần tử thay đổi.
2. **Khác tham chiếu ⇒ `array1.equals(array2)` sai**, kể cả khi chúng có các phần tử và độ dài bằng nhau.
3. **Mã băm trùng nhau không chứng minh cùng đối tượng.** `hashCode` trả về `int` nên các đối tượng khác nhau có thể va chạm.
4. **Thay đổi phần tử không chuyển `array.hashCode()` sang “băm nội dung”.** Nó vẫn theo danh tính; trong khi `Arrays.hashCode(array)` lại được tính từ các phần tử hiện tại.
5. `System.identityHashCode(array)` trả mã băm danh tính mặc định của đối tượng, ngay cả khi đối tượng thuộc lớp khác đã ghi đè `hashCode`. Vì mảng không ghi đè phương thức đó, `array.hashCode() == System.identityHashCode(array)`.

Đừng nhầm “identity hash” với ID duy nhất hay địa chỉ trong heap. Hợp đồng Java **không cam kết** mã băm duy nhất trên mọi đối tượng và không cam kết giá trị của mã băm mặc định có thể dùng để nhận dạng đối tượng qua các lần chạy ứng dụng.

## 3. So nội dung một chiều: chuyển từ identity sang `Arrays.equals`

Khi mục tiêu là **dữ liệu** chứ không phải chính đối tượng mảng, dùng `java.util.Arrays` thay cho `array.equals(...)`. Thứ tự, độ dài và quy tắc bằng nhau của từng phần tử vẫn được tôn trọng:

| Dữ liệu và mục đích | So sánh | Băm tương ứng |
| --- | --- | --- |
| Cùng đối tượng mảng | `a == b` hoặc `a.equals(b)` nếu a không null | `a.hashCode()` (identity) |
| Hai `int[]`, `byte[]`, `double[]` theo nội dung | `Arrays.equals(a, b)` đúng overload | `Arrays.hashCode(a)` |
| Hai `String[]` hoặc `Object[]` so từng phần tử | `Arrays.equals(a, b)` | `Arrays.hashCode(a)` |
| Hai `int[][]` hoặc `Object[]` có mảng con, muốn so cả tầng trong | `Arrays.deepEquals(a, b)` | `Arrays.deepHashCode(a)` |

```java
import java.util.Arrays;
// Bên trong main hoặc một phương thức:
int[] first = {1, 2};
int[] second = {1, 2};
System.out.println(first.equals(second));         // false: khác identity
System.out.println(Arrays.equals(first, second)); // true: cùng nội dung
System.out.println(Arrays.hashCode(first)
        == Arrays.hashCode(second));               // true
```

`Arrays.equals` và `Arrays.hashCode` cho mảng phẳng là **cặp nhất quán**: nếu hai mảng bằng nhau theo phép so nội dung thì hai mã băm nội dung phải bằng nhau; chiều ngược lại không đúng do hash collision. Các overload xử lý mảng `null` có kiểu rõ ràng: hai mảng `null` bằng nhau, còn `null` khác mảng rỗng. Đối với `float[]`/`double[]`, phương thức phân biệt `+0.0` với `-0.0`, và coi các giá trị NaN là bằng nhau theo quy tắc của `Float`/`Double`, khác toán tử `==` của primitive. Bài *Arrays.equals() và Arrays.hashCode() cho mảng một chiều* là nơi giải thích chi tiết overload, công thức hash, ví dụ chạy và các biên này.

## 4. Vì sao mảng lồng cần so và băm sâu?

`int[][]` là mảng có phần tử trực tiếp là các `int[]`. Khi dùng `Arrays.equals(int[][], int[][])`, các mảng con riêng biệt được so theo `Object.equals` của **từng mảng con**, không theo số bên trong. Cặp `Arrays.deepEquals`/`Arrays.deepHashCode` đi qua các mảng con để so/băm nội dung đúng mức:

```java
// Trong một phương thức có import java.util.Arrays:
int[][] a = {{1, 2}, {3}};
int[][] b = {{1, 2}, {3}};
System.out.println(Arrays.equals(a, b));     // false: mảng con khác identity
System.out.println(Arrays.deepEquals(a, b)); // true: cùng giá trị ở các tầng
```

Hai phương thức `deep*` nhận mảng ngoài có kiểu `Object[]` (ví dụ `int[][]`), nên **không gọi trực tiếp** `Arrays.deepEquals(int[], int[])`. Chúng xử lý mảng nguyên thủy ở tầng con bằng overload phù hợp, giữ thứ tự, phân biệt loại mảng, null và độ sâu; không tự đi vào các trường của mọi object tùy ý. Mảng `Object[]` tự chứa chính nó, trực tiếp hoặc gián tiếp, nằm ngoài phạm vi hành vi được xác định của `deepEquals/deepHashCode`: cần thuật toán chu trình riêng. Bài *Arrays.deepEquals() và Arrays.deepHashCode() cho mảng lồng* phân tích sâu các trường hợp, còn bài *Giới hạn khi cấu trúc có tham chiếu vòng* xử lý giới hạn này.

## 5. Đừng nhầm phép so nội dung với hành vi của hash collection

`HashMap` và `HashSet` luôn dùng `equals/hashCode` của **đối tượng khóa/phần tử** được lưu. Với `int[]` trần, chúng dùng identity, không tự gọi `Arrays.equals/hashCode`. Nếu muốn hai mảng mới tạo có cùng phần tử biểu diễn cùng khóa, cần **value object** với `equals` dựa trên `Arrays.equals`, `hashCode` dựa trên `Arrays.hashCode`, cùng bản sao phòng vệ của mảng mutable.

Nếu chính sách là so nội dung mảng lồng, dùng cả cặp `Arrays.deepEquals`/`Arrays.deepHashCode` và sao chép đủ các hàng/phần tử có thể sửa. `final` chỉ giữ tham chiếu không bị gán lại, **không đóng băng mảng**. Bài *Trường mảng trong lớp tự viết và yêu cầu đồng bộ equals() với hashCode()* đi sâu vào trường hợp nhiều trường và cách kết hợp hash.

## 6. Chương trình Java 17+ chạy độc lập

Ví dụ dưới đây so sánh danh tính mảng, nội dung mảng một chiều, cấu trúc lồng nhau và tác động khi đưa mảng trực tiếp vào `HashSet`/`HashMap`. Cuối cùng là một **value object bất biến** dùng bản sao của mảng để có `equals`/`hashCode` dựa theo nội dung.

Lưu thành tệp `ArrayIdentityDemo.java`:

```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ArrayIdentityDemo {
    static final class IntArrayKey {
        private final int[] values;

        IntArrayKey(int[] input) {
            Objects.requireNonNull(input, "input");
            this.values = input.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof IntArrayKey that
                        && Arrays.equals(values, that.values));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        int[] alias = a;

        System.out.println("a == b: " + (a == b));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a == alias: " + (a == alias));
        System.out.println("same identity hash: "
                + (a.hashCode() == System.identityHashCode(a)));
        System.out.println("Arrays.equals(a, b): " + Arrays.equals(a, b));
        System.out.println("content hash equal: "
                + (Arrays.hashCode(a) == Arrays.hashCode(b)));

        Set<int[]> arrays = new HashSet<>();
        arrays.add(a);
        arrays.add(b);
        arrays.add(alias);
        System.out.println("HashSet<int[]> size: " + arrays.size());
        System.out.println("contains new equal-content array: "
                + arrays.contains(new int[] {1, 2, 3}));

        Map<int[], String> map = new HashMap<>();
        map.put(a, "value-a");
        System.out.println("get same reference: " + map.get(alias));
        System.out.println("get equal-content new reference: " + map.get(b));

        Object[] nestedA = {
                new int[] {1, 2},
                new String[] {"x", "y"}
        };
        Object[] nestedB = {
                new int[] {1, 2},
                new String[] {"x", "y"}
        };
        System.out.println("nested Arrays.equals: "
                + Arrays.equals(nestedA, nestedB));
        System.out.println("nested Arrays.deepEquals: "
                + Arrays.deepEquals(nestedA, nestedB));
        System.out.println("nested deepHashCode equal: "
                + (Arrays.deepHashCode(nestedA)
                   == Arrays.deepHashCode(nestedB)));

        IntArrayKey k1 = new IntArrayKey(a);
        IntArrayKey k2 = new IntArrayKey(b);
        Set<IntArrayKey> keys = new HashSet<>();
        keys.add(k1);
        System.out.println("value key equals: " + k1.equals(k2));
        System.out.println("value key found: " + keys.contains(k2));

        a[0] = 99;
        System.out.println("source a now equals b by content: "
                + Arrays.equals(a, b));
        System.out.println("snapshot key still found: "
                + keys.contains(k2));
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 ArrayIdentityDemo.java
java ArrayIdentityDemo
```

Đầu ra kỳ vọng:

```text
a == b: false
a.equals(b): false
a == alias: true
same identity hash: true
Arrays.equals(a, b): true
content hash equal: true
HashSet<int[]> size: 2
contains new equal-content array: false
get same reference: value-a
get equal-content new reference: null
nested Arrays.equals: false
nested Arrays.deepEquals: true
nested deepHashCode equal: true
value key equals: true
value key found: true
source a now equals b by content: false
snapshot key still found: true
```

### Phân tích kết quả

**Mảng gốc khác nhau:** `a` và `b` là hai mảng, mặc dù có cùng chuỗi phần tử `1, 2, 3`. Vì mảng không ghi đè `equals`, `a.equals(b)` trả về `false`. `alias` trỏ đến `a` nên hai biểu thức `a == alias` và `a.equals(alias)` đều đúng.

**Hai loại hash:** `a.hashCode()` là mã băm mặc định theo danh tính, bằng `System.identityHashCode(a)`. `Arrays.hashCode(a)` là mã băm nội dung; với `a` và `b` có cùng nội dung, kết quả nội dung hash bằng nhau. Đừng thử suy từ việc hai hash danh tính khác/giống nhau để quyết định dữ liệu có bằng nhau.

**Collection băm:** `HashSet<int[]>` sử dụng `hashCode`/`equals` của chính đối tượng mảng, nên `a` và `b` được lưu như **hai khóa/phần tử khác nhau**. Thêm `alias` không tăng kích thước vì nó trỏ lại `a`. Một mảng mới có cùng các số cũng không được tìm thấy. Tương tự, `HashMap.get(alias)` tìm được giá trị `value-a`, còn `get(b)` trả về `null` dù `b` chứa cùng số.

**Cấu trúc lồng:** `Arrays.equals(nestedA, nestedB)` trả về `false` vì hai mảng `int[]` bên trong khác tham chiếu. `Arrays.deepEquals` đi vào mảng con và xác nhận dữ liệu tương ứng bằng nhau; `deepHashCode` của hai cấu trúc cũng khớp.

**Value object:** `IntArrayKey` sao chép mảng đầu vào ngay trong constructor, giữ bản sao ở trường `private final` và không để lộ mảng nội bộ ra bên ngoài. `equals`/`hashCode` đều dùng nội dung `values`, nên `k1` và `k2` bằng nhau và tìm thấy trong `HashSet`. Sau khi sửa `a[0]` từ `1` thành `99`, khóa `k1` đã tạo vẫn chứa bản sao gốc; tra cứu bằng `k2` tiếp tục thành công.

Mã này dùng `hashCode` mặc định của mảng **và** `Arrays.hashCode` để minh họa hai ý nghĩa; không in một giá trị số cố định cho mã băm danh tính, vì giá trị ấy có thể thay đổi theo lần chạy.

## 7. Dùng mảng làm khóa `HashMap` hoặc phần tử `HashSet`

Cần chọn xem khóa đại diện cho **danh tính của một vùng dữ liệu mảng** hay **giá trị các phần tử**.

### Khi cần danh tính mảng

Nếu nghiệp vụ muốn hai mảng khác nhau luôn là hai khóa riêng, kể cả khi chứa cùng phần tử, `HashMap<int[], V>` hoặc `HashSet<int[]>` có thể phản ánh yêu cầu ấy. Điều này thường xuất hiện khi theo dõi chính **đối tượng mảng cụ thể** trong bộ nhớ.

Tuy nhiên phải công bố rõ kỳ vọng: tra cứu bằng một mảng **mới được tạo từ cùng phần tử** sẽ không khớp với mảng cũ. Việc này không phải lỗi của collection.

Nếu cần cấu trúc chuyên dùng để kiểm tra danh tính khóa khi các lớp khóa khác có `equals` theo giá trị, Java còn có `IdentityHashMap`; nhưng với mảng, bản thân `equals` đã theo danh tính. `IdentityHashMap` có semantics riêng và chỉ nên chọn khi chủ ý dùng tham chiếu để nhận dạng khóa.

### Khi cần giá trị nội dung mảng

Nếu hai mảng `{1, 2, 3}` phải được coi là cùng khóa logic, **không dùng trần `int[]` làm khóa với kỳ vọng `HashMap` sẽ gọi `Arrays.equals`**. Cần một lớp bao như `IntArrayKey` trong ví dụ: cài đặt `equals` bằng `Arrays.equals`, cài đặt `hashCode` bằng `Arrays.hashCode`, và bảo vệ tính bất biến.

Có thể dùng cấu trúc bất biến khác với hợp đồng bằng nhau theo phần tử, chẳng hạn một danh sách bất biến chứa các giá trị đã được sao chép. Nhưng cần hiểu đúng kiểu phần tử; một `List<int[]>` vẫn mang các phần tử là mảng và phép so sánh phần tử có thể tiếp tục dựa trên danh tính mảng con.

### Nếu mảng đã thay đổi sau khi đưa vào map thì sao?

Có hai trường hợp cần phân biệt:

- **Khóa là `int[]` trần:** sửa phần tử không thay đổi `equals`/`hashCode` danh tính mặc định của mảng. Với cùng tham chiếu khóa, tra cứu bằng nó vẫn giữ quan hệ danh tính thông thường. Tuy vậy nếu nghiệp vụ kỳ vọng so nội dung thì thiết kế đã không phù hợp từ đầu.
- **Khóa là wrapper so nội dung nhưng giữ tham chiếu mảng mutable:** sửa phần tử có thể khiến `hashCode` thay đổi trong khi khóa vẫn nằm ở nhóm băm xác định bởi hash cũ. `get`/`contains` có thể không tìm thấy khóa. Đây là lỗi thiết kế phổ biến.

Vì vậy wrapper dùng cho khóa băm nên **sao chép dữ liệu đầu vào** và nếu có phương thức lấy dữ liệu ra cũng trả về **bản sao phòng vệ** (defensive copy), thay vì để bên ngoài thay đổi dữ liệu mà `equals`/`hashCode` sử dụng. `private final int[] values` tự nó **chỉ cố định tham chiếu tới mảng**, không làm cho phần tử mảng bất biến.

## 8. Phân biệt các API theo nhu cầu

| Nhu cầu cụ thể | API / thiết kế phù hợp | Điều cần tránh |
| --- | --- | --- |
| Hỏi hai biến có trỏ cùng một mảng không | `a == b`, hoặc `a.equals(b)` khi `a` chắc chắn khác `null` | Dùng `Arrays.equals` nếu yêu cầu là danh tính. |
| So hai mảng `int[]` theo phần tử | `Arrays.equals(a, b)` | Dùng `a.equals(b)` rồi suy ra nội dung khác nhau. |
| Tạo hash tương ứng cho `int[]` theo nội dung | `Arrays.hashCode(a)` | Dùng `a.hashCode()` làm fingerprint nội dung. |
| So hai `String[]` theo các chuỗi phần tử | `Arrays.equals(a, b)` | Quên rằng mỗi phần tử tham chiếu dùng `equals` riêng của lớp đó. |
| So hai mảng đa chiều/lồng theo nội dung | `Arrays.deepEquals(a, b)` khi gốc là `Object[]` | Gọi `Arrays.equals` ở cấp ngoài rồi tưởng đi sâu mọi tầng. |
| Tạo hash cho mảng lồng tương ứng | `Arrays.deepHashCode(a)` | Kết hợp `deepEquals` với `Arrays.hashCode` nông nếu dùng làm hợp đồng value object. |
| Dùng nội dung mảng làm khóa băm | Value object sao chép mảng, cặp `Arrays.equals`/`Arrays.hashCode` hoặc deep tương ứng | Dùng `int[]` trần rồi kỳ vọng collection nhận cùng giá trị. |
| Kiểm tra hai mảng có giống nhau khi một/both có thể `null` | `Arrays.equals`, hoặc `Arrays.deepEquals` với mảng lồng phù hợp | Gọi `a.equals(b)` trên `a == null`. |

Ngoài `equals`, lớp tiện ích `Arrays` còn có `mismatch` để tìm vị trí khác đầu tiên của hai mảng và `compare` để so thứ tự từ điển theo overload tương ứng. Đây là các câu hỏi khác với hợp đồng bằng nhau của đối tượng mảng; chỉ dùng khi việc xác định vị trí khác hoặc thứ tự so sánh có giá trị cho bài toán.

## 9. Các lỗi dễ mắc khi làm việc với mảng và cấu trúc lồng

### Lỗi 1: So mảng bằng `equals` và kết luận dữ liệu khác

`new int[]{1,2}.equals(new int[]{1,2})` là `false`, vì hai đối tượng mảng khác nhau. Nếu muốn biết nội dung `int[]` có bằng nhau, phải dùng `Arrays.equals`.

### Lỗi 2: Thấy hash của hai mảng giống nhau rồi xem chúng bằng nhau

Không có hàm hash nào dạng `int` đảm bảo duy nhất cho mọi mảng khả dĩ. Cùng hash có thể là **va chạm**, cả với hash danh tính lẫn hash nội dung. So sánh chính xác bằng API bằng nhau phù hợp, thay vì thay `equals` bằng `hashCode()==hashCode()`.

### Lỗi 3: Dùng `Arrays.equals` cho `int[][]`

`int[][]` là mảng chứa các mảng `int[]`. `Arrays.equals` cấp ngoài chỉ so từng mảng con theo `equals` của chúng, dẫn tới trả `false` cho hai cấu trúc mới nhưng có cùng ma trận số. Muốn đi sâu, dùng `Arrays.deepEquals`; khi băm nội dung cho wrapper tương ứng, dùng `Arrays.deepHashCode`.

### Lỗi 4: Dùng `Arrays.deepEquals` mà không xem xét vòng tham chiếu

Nếu `Object[]` chứa chính nó hoặc các mảng tạo thành chu trình, thuật toán deep không phải giải pháp so sánh đồ thị có chu trình. Tránh thiết kế dữ liệu có vòng trong phạm vi dùng `deepEquals`/`deepHashCode`, hoặc cài thuật toán so sánh với theo dõi nút đã duyệt và hợp đồng riêng.

### Lỗi 5: Chỉ ghi đè `equals` của wrapper

Nếu wrapper định nghĩa bằng nhau theo `Arrays.equals(values, other.values)` nhưng không ghi đè `hashCode` tương ứng, hai wrapper có nội dung bằng nhau có thể nhận hash mặc định khác nhau và không thể dùng làm khóa băm một cách đáng tin cậy. Cần cả `Arrays.hashCode(values)` cho mảng một chiều, hoặc cặp deep tương ứng khi chính sách là so nội dung mảng lồng.

### Lỗi 6: Sao chép bề mặt rồi tưởng đã bất biến sâu

`array.clone()` sao chép **mảng ngoài**, nhưng với `Object[]` chứa các mảng con hay đối tượng mutable, các phần tử được sao chép dưới dạng tham chiếu. Bên ngoài vẫn có thể sửa những mảng con. Nếu wrapper muốn làm khóa băm **theo toàn cấu trúc lồng**, phải cân nhắc sao chép sâu phù hợp với miền dữ liệu, đảm bảo từng thành phần tham gia bằng nhau/hash không thể bị thay đổi từ ngoài.

### Lỗi 7: Dùng `Arrays.toString` làm chuỗi khóa rồi băm

`Arrays.toString(int[])` hữu ích khi hiển thị, nhưng với `Object[]` lồng mảng, kết quả có thể chứa chuỗi mô tả danh tính phần tử mảng con. Dùng `Arrays.deepToString` để hiển thị mảng lồng thông thường khi cần, song **chuỗi hiển thị không phải định dạng serialization hay canonical identity chuẩn** cho mọi kiểu phần tử. Không biến đầu ra debug thành quy tắc danh tính mà chưa đặc tả.

## 10. Checklist kiểm thử

Khi làm việc với các lớp bao mảng hoặc các cấu trúc lưu trữ mảng, nên kiểm tra:

1. Hai mảng **khác tham chiếu, cùng phần tử**: `a.equals(b)` sai, `Arrays.equals(a,b)` đúng.
2. Hai biến trỏ **cùng mảng**: so danh tính đúng, kể cả khi sửa phần tử.
3. Hai mảng **khác độ dài** hoặc khác ít nhất một phần tử: `Arrays.equals` sai.
4. `null`: `Arrays.equals(null, null)` với overload xác định trả đúng; gọi phương thức trên tham chiếu `null` gây lỗi.
5. Mảng tham chiếu có phần tử `null`: `Arrays.equals(Object[], Object[])` cần cho kết quả đúng theo từng vị trí.
6. Hai mảng lồng với **các mảng con riêng** nhưng cùng nội dung: `Arrays.equals` cấp ngoài sai, `Arrays.deepEquals` đúng.
7. Nếu dùng so nội dung, mọi cặp bằng nhau phải cho cùng `Arrays.hashCode`/`Arrays.deepHashCode` tương ứng; hash bằng nhau **không suy ra** bằng nhau.
8. Với `HashMap`/`HashSet`: xác định rõ muốn dùng danh tính mảng hay giá trị phần tử; test cả trường hợp truy vấn bằng mảng mới có cùng phần tử.
9. Với wrapper bất biến: sửa mảng gốc sau khi tạo khóa, và sửa bản sao nếu có API xuất dữ liệu, phải **không làm đổi** kết quả `equals`/`hashCode` của khóa đã lưu.
10. Với cấu trúc lồng: xác định rõ có chứa chu trình, các đối tượng mutable, và cần so sánh nông hay sâu.

## 11. Tổng kết

Mảng Java dùng `equals()` và `hashCode()` mặc định dựa trên **danh tính đối tượng**, nên hai mảng được tạo riêng có cùng phần tử vẫn không bằng nhau theo `array1.equals(array2)`. Khi bài toán cần **ngữ nghĩa nội dung**, `Arrays.equals`/`Arrays.hashCode` là cặp tương ứng cho mảng một chiều; `Arrays.deepEquals`/`Arrays.deepHashCode` xử lý mảng tham chiếu lồng nhau không có chu trình theo cách thích hợp. Nếu mảng dùng làm khóa trong collection băm mà cần so nội dung, hãy đưa nó vào một **value object bảo vệ tính bất biến**, thiết kế `equals` và `hashCode` cùng tiêu chí. Sự phân biệt giữa danh tính, nội dung, so sánh nông và so sánh sâu quyết định tính đúng đắn của mọi thao tác tra cứu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-002">Arrays.equals() và Arrays.hashCode() cho mảng một chiều</a>

<details>
<summary>Xem chi tiết</summary>

# `Arrays.equals()` và `Arrays.hashCode()` cho mảng một chiều trong Java

## 1. Vấn đề: hai mảng có cùng phần tử chưa chắc là cùng đối tượng

Trong Java, **mảng là đối tượng**. Hai biến mảng có thể cùng tham chiếu đến **một mảng**, hoặc tham chiếu đến **hai mảng riêng biệt** có các phần tử giống hệt nhau.

~~~java
int[] a = {1, 2, 3};
int[] b = {1, 2, 3};
int[] alias = a;
~~~

- `a == b` trả về `false`: hai biểu thức khởi tạo mảng đã tạo **hai đối tượng mảng** khác nhau.
- `a == alias` trả về `true`: phép gán `alias = a` chỉ sao chép **tham chiếu**, không tạo mảng mới.
- `a.equals(b)` trả về `false`: mảng không ghi đè phương thức `equals(Object)` từ `Object` để so sánh từng phần tử.
- `Arrays.equals(a, b)` trả về `true`: so sánh **nội dung theo vị trí** của hai mảng một chiều.

Tương tự, phương thức `hashCode()` kế thừa từ `Object` trên một đối tượng mảng **không định nghĩa hash theo các phần tử**. Nếu cần so sánh hai mảng như **hai giá trị có nội dung bằng nhau**, dùng cặp API:

~~~java
java.util.Arrays.equals(a, b);
java.util.Arrays.hashCode(a);
~~~

Cặp phương thức này đặc biệt quan trọng khi mảng là một phần trạng thái của một value object hay đối tượng dùng làm khóa `HashMap`/phần tử `HashSet`.

## 2. `Arrays.equals()` kiểm tra bằng nhau theo nội dung như thế nào?

`java.util.Arrays` cung cấp các overload `equals` cho mảng một chiều chứa từng kiểu nguyên thủy (`boolean[]`, `byte[]`, `short[]`, `char[]`, `int[]`, `long[]`, `float[]`, `double[]`) và mảng tham chiếu `Object[]`.

Với hai mảng cùng kiểu thành phần thích hợp:

1. Hai tham chiếu cùng chỉ một mảng thì bằng nhau, kể cả trường hợp **cả hai là `null`**.
2. Một bên là `null`, bên kia không phải `null` thì không bằng nhau.
3. Độ dài khác nhau thì không bằng nhau.
4. Độ dài giống nhau thì kiểm tra **từng cặp phần tử ở cùng chỉ số**, từ đầu đến cuối.
5. Nếu phát hiện phần tử đầu tiên không bằng nhau thì kết quả là `false`; chỉ khi tất cả vị trí tương ứng bằng nhau mới trả về `true`.

Thứ tự phần tử có ý nghĩa: `[1, 2, 3]` không bằng `[3, 2, 1]`. Với cách so sánh nội dung này, hai mảng không cần có cùng địa chỉ hay cùng tham chiếu.

### Ví dụ chạy được: mảng `int[]`

Tạo tệp `PrimitiveArraysDemo.java`:

~~~java
import java.util.Arrays;

public class PrimitiveArraysDemo {
    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        int[] differentOrder = {3, 2, 1};
        int[] alias = a;

        System.out.println("a == b: " + (a == b));
        System.out.println("a == alias: " + (a == alias));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("Arrays.equals(a,b): " + Arrays.equals(a, b));
        System.out.println("same contents hash: "
                + (Arrays.hashCode(a) == Arrays.hashCode(b)));
        System.out.println("order equal: "
                + Arrays.equals(a, differentOrder));
        System.out.println("hash(a): " + Arrays.hashCode(a));
        System.out.println("null equals null: "
                + Arrays.equals((int[]) null, (int[]) null));
        System.out.println("null equals a: "
                + Arrays.equals((int[]) null, a));
        System.out.println("hash(null): " + Arrays.hashCode((int[]) null));
        System.out.println("hash(empty): " + Arrays.hashCode(new int[0]));

        b[0] = 9;
        System.out.println("after mutation: " + Arrays.equals(a, b));
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac -encoding UTF-8 PrimitiveArraysDemo.java
java PrimitiveArraysDemo
~~~

Stdout:

~~~text
a == b: false
a == alias: true
a.equals(b): false
Arrays.equals(a,b): true
same contents hash: true
order equal: false
hash(a): 30817
null equals null: true
null equals a: false
hash(null): 0
hash(empty): 1
after mutation: false
~~~

`Arrays.hashCode(new int[] {1, 2, 3})` là `30817` theo công thức ở phần tiếp theo. Đây là giá trị của **thuật toán hash theo nội dung**; đừng nhầm với `a.hashCode()`, vốn kế thừa cơ chế hash của đối tượng.

Trong lời gọi `Arrays.equals(null, null)` hoặc `Arrays.hashCode(null)` viết trực tiếp, trình biên dịch có thể không chọn được overload vì có nhiều kiểu mảng. Ép kiểu rõ ràng, chẳng hạn `(int[]) null`, hoặc truyền biến có kiểu khai báo `int[]` giúp chọn đúng overload.

## 3. `Arrays.hashCode()` tạo mã băm theo nội dung

`Arrays.hashCode` cũng có các overload tương ứng với các loại mảng một chiều nguyên thủy và `Object[]`. Đối với một mảng không `null`, hàm duyệt các phần tử **theo thứ tự**, bắt đầu với `result = 1` và lặp công thức:

~~~text
result = 1
for each element:
    result = 31 * result + elementHash
return result
~~~

Ở đây `elementHash` là giá trị hash của từng phần tử theo quy tắc tương ứng với kiểu phần tử:

- `int[]`: chính giá trị `int` của phần tử.
- `long[]`: giá trị như `Long.hashCode(long)`.
- `boolean[]`: giá trị như `Boolean.hashCode(boolean)`.
- `byte[]`, `short[]` và `char[]`: giá trị số của phần tử theo phép chuyển sang `int` thích hợp.
- `float[]` và `double[]`: phép băm tương ứng với `Float.hashCode` hoặc `Double.hashCode`.
- `Object[]`: `0` nếu phần tử là `null`; nếu khác `null` thì gọi `element.hashCode()`.

Với mảng `int[] {1, 2, 3}`:

~~~text
ban đầu:            result = 1
sau phần tử 1:      result = 31 * 1   + 1 = 32
sau phần tử 2:      result = 31 * 32  + 2 = 994
sau phần tử 3:      result = 31 * 994 + 3 = 30817
~~~

Với mảng `null`, `Arrays.hashCode` trả về `0`; với một mảng **rỗng nhưng không `null`**, kết quả là `1`. Phép tính dùng `int` nên có thể tràn số theo ngữ nghĩa số nguyên Java; đây không phải lỗi của thuật toán.

### Hợp đồng giữa `equals` và `hashCode`

Nếu `Arrays.equals(a, b)` trả về `true` với overload thích hợp thì `Arrays.hashCode(a) == Arrays.hashCode(b)` cũng phải đúng. Điều ngược lại **không bảo đảm**: hai mảng có nội dung khác nhau vẫn có thể trùng mã băm vì số kết quả chỉ có 32 bit.

Do đó, **không** dùng điều kiện `Arrays.hashCode(a) == Arrays.hashCode(b)` thay cho so sánh nội dung thực sự. Hash thích hợp để chọn bucket hoặc tăng tốc tra cứu; `equals` mới xác định bằng nhau về giá trị.

## 4. Mảng nguyên thủy và mảng tham chiếu khác nhau ở phép so sánh phần tử

### Mảng kiểu nguyên thủy

Với `int[]`, `byte[]`, `char[]` và các kiểu nguyên thủy khác, `Arrays.equals` áp dụng cách so sánh giá trị phù hợp với kiểu. Ví dụ `char[] {'a', 'b'}` và `char[] {'b', 'a'}` không bằng nhau vì khác thứ tự.

Không có overload `Arrays.equals(int[], long[])` để tự đổi kiểu rồi so. Hai mảng nguyên thủy khác loại phải được chuyển đổi/chuẩn hóa có chủ đích nếu yêu cầu nghiệp vụ cần so nội dung liên kiểu.

### Mảng tham chiếu `Object[]`

Với mảng chứa đối tượng, `Arrays.equals(Object[] a, Object[] b)` so từng cặp phần tử bằng **`equals()` của phần tử** (có xử lý `null`), không bắt buộc hai phần tử là cùng đối tượng.

~~~java
String[] first = {"Java", "Core"};
String[] second = {new String("Java"), "Core"};
System.out.println(Arrays.equals(first, second)); // true
~~~

Hai chuỗi ở vị trí đầu có thể là các đối tượng String khác nhau, nhưng `String.equals` so nội dung nên hai mảng bằng nhau. Khi băm `Object[]`, `Arrays.hashCode` gọi `hashCode()` của từng phần tử; nếu phần tử triển khai đúng hợp đồng `equals/hashCode` thì toàn mảng giữ được sự nhất quán.

Nếu phần tử là một lớp do bạn viết và lớp đó **không ghi đè `equals`**, kết quả vẫn dựa trên danh tính phần tử. Bởi vậy `Arrays.equals(Object[], Object[])` chỉ là **so sánh một cấp** theo quy tắc của các phần tử, không tự biến mọi loại đối tượng thành giá trị được so theo các trường sâu bên trong.

### Ví dụ chạy được: `String[]`, phần tử null và thứ tự

Tạo tệp `ReferenceArraysDemo.java`:

~~~java
import java.util.Arrays;

public class ReferenceArraysDemo {
    public static void main(String[] args) {
        String[] first = {"Java", "Core"};
        String[] second = {new String("Java"), "Core"};
        String[] reordered = {"Core", "Java"};

        Object[] left = {null, new String("A"), 7};
        Object[] right = {null, "A", Integer.valueOf(7)};

        System.out.println("same String content: "
                + Arrays.equals(first, second));
        System.out.println("same String hash: "
                + (Arrays.hashCode(first) == Arrays.hashCode(second)));
        System.out.println("different order: "
                + Arrays.equals(first, reordered));
        System.out.println("null and objects equal: "
                + Arrays.equals(left, right));
        System.out.println("null and objects hash: "
                + (Arrays.hashCode(left) == Arrays.hashCode(right)));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ReferenceArraysDemo.java
java ReferenceArraysDemo
~~~

Stdout:

~~~text
same String content: true
same String hash: true
different order: false
null and objects equal: true
null and objects hash: true
~~~

`Integer.valueOf(7)` là đối tượng wrapper; `Integer.equals` so cùng kiểu và cùng giá trị. Mảng `Object[]` có thể chứa nhiều kiểu phần tử, miễn là từng cặp phần tử cung cấp ngữ nghĩa `equals/hashCode` tương thích với yêu cầu.

## 5. Điểm đặc biệt của mảng `float[]` và `double[]`

Đối với `float[]` và `double[]`, `Arrays.equals` xử lý hai giá trị dấu phẩy động tương ứng theo ngữ nghĩa của các phương thức so sánh wrapper (`Float.compare`, `Double.compare`), thay vì chỉ dựa vào trực giác về toán tử `==` trên primitive:

- Các giá trị `NaN` được coi là bằng nhau với nhau khi so bằng `Arrays.equals` (các biểu diễn NaN được chuẩn hóa theo quy tắc tương ứng).
- `+0.0` và `-0.0` được coi là **khác nhau**.
- `Arrays.hashCode` dùng cách tạo hash tương ứng với ngữ nghĩa trên, nên mảng được `Arrays.equals` coi là bằng nhau có cùng hash.

Tạo tệp `FloatingArraysDemo.java`:

~~~java
import java.util.Arrays;

public class FloatingArraysDemo {
    public static void main(String[] args) {
        double[] plusZero = {+0.0d};
        double[] minusZero = {-0.0d};
        double[] nanA = {Double.NaN};
        double[] nanB = {
                Double.longBitsToDouble(0x7ff0000000000001L)
        };

        System.out.println("primitive +0 == -0: " + (+0.0d == -0.0d));
        System.out.println("array +0 equals -0: "
                + Arrays.equals(plusZero, minusZero));
        System.out.println("primitive NaN == NaN: "
                + (Double.NaN == Double.NaN));
        System.out.println("NaN arrays equal: "
                + Arrays.equals(nanA, nanB));
        System.out.println("NaN hashes match: "
                + (Arrays.hashCode(nanA) == Arrays.hashCode(nanB)));
    }
}
~~~

~~~bash
javac -encoding UTF-8 FloatingArraysDemo.java
java FloatingArraysDemo
~~~

Stdout:

~~~text
primitive +0 == -0: true
array +0 equals -0: false
primitive NaN == NaN: false
NaN arrays equal: true
NaN hashes match: true
~~~

Với bài toán số học muốn coi hai giá trị gần bằng nhau trong **ngưỡng sai số** (tolerance), `Arrays.equals(double[], double[])` không làm điều đó. Bạn cần quy tắc so sánh riêng. Nếu đối tượng cần một `hashCode()` tương ứng với quy tắc “gần bằng”, phải thiết kế thật cẩn thận vì quan hệ so sai số thông thường có thể không **bắc cầu**, gây khó khăn hoặc làm mất hợp đồng `equals()`.

## 6. Mảng một chiều chứa đối tượng mảng: ranh giới của so sánh nông

Một mảng `int[][]` có thể nhìn như mảng hai chiều, nhưng ở mức Java nó là **mảng một chiều chứa các tham chiếu đến `int[]`**. Do đó:

~~~java
int[][] left = {{1, 2}, {3, 4}};
int[][] right = {{1, 2}, {3, 4}};
~~~

- `Arrays.equals(left, right)` sử dụng overload `Object[]` (vì `int[][]` là mảng của các đối tượng `int[]`) rồi gọi `equals` trên **từng mảng con**. Mảng con không định nghĩa so nội dung; những mảng con mới tạo không cùng danh tính, nên kết quả là `false`.
- `Arrays.hashCode(left)` băm các tham chiếu/đối tượng mảng con theo `hashCode` của từng phần tử; đây **không phải băm sâu nội dung số nguyên**.
- `Arrays.deepEquals(left, right)` và `Arrays.deepHashCode(left/right)` là cặp API thích hợp nếu muốn đi sâu vào mảng lồng nhau.

Ví dụ ngắn:

~~~java
System.out.println(Arrays.equals(left, right));       // false
System.out.println(Arrays.deepEquals(left, right));   // true
~~~

Các đoạn trên giả định đang ở trong phương thức, đã khai báo `left`, `right` và đã `import java.util.Arrays;`. Phạm vi bài hiện tại là **mảng một chiều**. Cặp `deepEquals/deepHashCode` chỉ được nhắc để nhận diện đúng ranh giới: `Arrays.equals/hashCode` bản thường **không đệ quy băm/so nội dung của các đối tượng mảng bên trong**.

## 7. Dùng đúng cặp phương thức trong `equals()` và `hashCode()` của value object

Giả sử có một lớp biểu diễn mã định danh dạng `byte[]`. Nếu định nghĩa hai instance bằng nhau khi **chuỗi byte có cùng nội dung**, thì:

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ByteArrayKey other)) return false;
    return Arrays.equals(bytes, other.bytes);
}

@Override
public int hashCode() {
    return Arrays.hashCode(bytes);
}
~~~

Đây là **đoạn trích minh họa**, giả định lớp có trường `byte[] bytes` và import `java.util.Arrays`. Nếu dùng `bytes.equals(other.bytes)` hoặc `bytes.hashCode()`, ý nghĩa sẽ quay về **danh tính mảng**; đặc biệt nguy hiểm nếu `equals` so nội dung nhưng `hashCode` lại băm danh tính, vì có thể vi phạm hợp đồng của `Object`.

### Ví dụ chạy được: khóa `HashMap` dùng nội dung mảng

Tạo tệp `ByteArrayKeyDemo.java`:

~~~java
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ByteArrayKeyDemo {
    static final class ByteArrayKey {
        private final byte[] bytes;

        ByteArrayKey(byte[] input) {
            this.bytes = input.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ByteArrayKey other)) return false;
            return Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        byte[] copyBytes() {
            return bytes.clone();
        }
    }

    public static void main(String[] args) {
        byte[] source = {10, 20, 30};
        ByteArrayKey first = new ByteArrayKey(source);
        ByteArrayKey same = new ByteArrayKey(new byte[] {10, 20, 30});

        Map<ByteArrayKey, String> map = new HashMap<>();
        map.put(first, "found");

        System.out.println("equal content: " + first.equals(same));
        System.out.println("matching hashes: "
                + (first.hashCode() == same.hashCode()));
        System.out.println("lookup: " + map.get(same));

        source[0] = 99;
        byte[] exposedCopy = first.copyBytes();
        exposedCopy[1] = 88;

        System.out.println("key stayed unchanged: "
                + Arrays.equals(first.copyBytes(), new byte[] {10, 20, 30}));
        System.out.println("lookup after edits: " + map.get(same));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ByteArrayKeyDemo.java
java ByteArrayKeyDemo
~~~

Stdout:

~~~text
equal content: true
matching hashes: true
lookup: found
key stayed unchanged: true
lookup after edits: found
~~~

**Vì sao constructor dùng `clone()`?** Mảng mutable: bên ngoài có thể đổi phần tử sau khi khóa đã được đưa vào `HashMap`. Khi hash phụ thuộc nội dung, sửa nội dung có thể khiến lookup hoạt động không đúng như kỳ vọng. Lớp minh họa phòng vệ bằng cách sao chép mảng đầu vào và chỉ trả về **bản sao** qua `copyBytes()`; lớp cũng là `final` và không có phương thức sửa mảng nội bộ.

Nếu một lớp có cả `id` và `byte[] bytes` tham gia equality, hãy kết hợp hash của cả hai:

~~~java
int result = Integer.hashCode(id);
result = 31 * result + Arrays.hashCode(bytes);
return result;
~~~

Đoạn trên chỉ là minh họa trong một lớp có trường `int id` và `byte[] bytes`, với `equals` cũng so `id` và `Arrays.equals(bytes, other.bytes)`. Trong trường hợp có nhiều trường mảng, mỗi trường cần so và băm theo cùng chính sách nông/sâu đã chọn.

## 8. Các API gần tên dễ bị nhầm

| Cách viết | So sánh/băm theo điều gì? | Phù hợp khi nào? |
| --- | --- | --- |
| `a == b` với hai biến mảng | Danh tính: cùng một đối tượng mảng hay không | Cần biết hai tham chiếu có cùng mảng |
| `a.equals(b)` | Thường là danh tính, do mảng dùng `Object.equals` | Ít phù hợp để kiểm tra dữ liệu mảng |
| `Arrays.equals(a, b)` | Nội dung **một chiều**, cùng thứ tự, từng phần tử theo kiểu tương ứng | Mảng primitive, mảng đối tượng một chiều |
| `Arrays.hashCode(a)` | Hash theo nội dung **một chiều** | Kết hợp với `Arrays.equals` trong value object |
| `Arrays.deepEquals(a, b)` | Đi sâu vào mảng được lồng bên trong mảng đối tượng | Cấu trúc mảng lồng nhau |
| `Arrays.deepHashCode(a)` | Hash sâu tương ứng | Kết hợp với `Arrays.deepEquals` |
| `Objects.equals(a, b)` với `a`, `b` là mảng | Null-safe nhưng nếu khác tham chiếu thì gọi `a.equals(b)`, **không tự so phần tử** | Giá trị đối tượng thông thường; tránh dùng thay `Arrays.equals` cho mảng |
| `Objects.hash(a)` với `a` là mảng primitive | Một đối số tham chiếu mảng; **không băm từng phần tử primitive** | Không phù hợp thay `Arrays.hashCode(a)` |

Với `Object[]`, `Objects.hash(array)` còn có **bẫy varargs**: tùy kiểu biểu thức, mảng có thể được coi là chính mảng tham số `Object...`, hoặc là một phần tử được bọc trong varargs. Muốn băm nội dung mảng một chiều thì `Arrays.hashCode(array)` diễn đạt ý định trực tiếp và rõ ràng hơn.

## 9. Chi phí thực thi: khi nào so và băm mảng tốn kém?

`Arrays.equals` có thể phải kiểm tra lần lượt tối đa `n` phần tử nếu hai mảng có cùng độ dài; nếu khác độ dài hoặc gặp phần tử không bằng nhau từ sớm, phương thức có thể kết thúc sớm. Về độ phức tạp, trường hợp xấu nhất là **O(n)** theo chiều dài mảng.

`Arrays.hashCode` phải kết hợp tất cả phần tử để tạo mã hash theo nội dung, nên có độ phức tạp **O(n)**. Với mảng tham chiếu, còn phụ thuộc vào độ phức tạp `hashCode()` của **từng phần tử**. Bản thân `Arrays.hashCode(int[])` không cần boxing từng phần tử thành `Integer`; overload cho mảng primitive xử lý trực tiếp các giá trị nguyên thủy.

Một số JVM có thể tối ưu cách triển khai so sánh mảng (đặc biệt một số kiểu primitive), nhưng không nên giả định một mức tăng tốc cố định. Khi băm mảng lớn trong một vòng lặp nóng, hãy đo bằng profiler/JMH ở hoàn cảnh thực tế trước khi quyết định chiến lược cache hash hay thay đổi cấu trúc dữ liệu.

Đừng dùng hash như phép so nhanh mang ý nghĩa kết luận: hai hash bằng nhau **không chứng minh** hai mảng bằng nhau. Đối với khóa mảng lớn bất biến, có thể cân nhắc cache hash, nhưng phải bảo đảm không còn thao tác sửa mảng ảnh hưởng đến equality; cache cũng có chi phí bộ nhớ và đồng bộ trong những thiết kế chia sẻ giữa luồng.

## 10. Sai lầm thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| Hai mảng cùng giá trị thì `a.equals(b)` là `true`. | Mảng dùng `Object.equals`; hãy dùng `Arrays.equals(a, b)`. |
| Hai mảng có hash bằng nhau thì chắc chắn có phần tử giống nhau. | Hash có thể va chạm; kết luận bằng nhau phải dùng `Arrays.equals`. |
| `Arrays.equals` không phân biệt vị trí phần tử. | Thứ tự **có ý nghĩa**; đây không phải phép so tập hợp. |
| `Arrays.equals(null, null)` luôn biên dịch mà không có kiểu cụ thể. | Nhiều overload có thể khiến lời gọi mơ hồ; dùng biến mảng có kiểu hoặc cast. |
| `Arrays.hashCode(new int[0])` bằng `0` giống mảng `null`. | Mảng rỗng trả `1`; mảng `null` trả `0`. |
| `+0.0` và `-0.0` được `Arrays.equals(double[])` coi bằng nhau. | Sai: chúng được coi khác nhau theo quy tắc so tương ứng. |
| `Arrays.equals` tự đi sâu vào `int[][]`. | Sai: phần tử `int[]` của mảng ngoài được so theo `equals` của đối tượng; cân nhắc `deepEquals`. |
| Trong value object, `equals` dùng `Arrays.equals` nhưng `hashCode` dùng `array.hashCode()`. | Có nguy cơ phá hợp đồng; dùng `Arrays.hashCode` tương ứng. |
| Đưa mảng mutable vào hash key rồi sửa phần tử vẫn tra cứu chắc chắn đúng. | Nội dung và hash thay đổi có thể làm lookup không còn tìm thấy khóa; sao chép phòng vệ hoặc giữ dữ liệu bất biến. |
| `Objects.equals(a,b)` tự so nội dung mảng. | Nó chỉ null-safe và chuyển sang `a.equals(b)`; mảng vẫn dùng so danh tính. |

## 11. Checklist áp dụng

Khi cần so sánh hoặc băm **mảng một chiều**:

1. Xác định rõ mục tiêu là so **danh tính** hay so **nội dung**. Chọn `==` cho danh tính, `Arrays.equals` cho nội dung.
2. Chọn overload đúng kiểu mảng. `Arrays.equals(int[], int[])` khác `Arrays.equals(Object[], Object[])` ở quy tắc so phần tử.
3. Tính đến `null`, mảng rỗng, độ dài và thứ tự phần tử.
4. Với `float[]`/`double[]`, nhớ quy tắc cho `NaN` và hai dấu zero.
5. Khi dùng trong một value object, cài đặt `equals()` và `hashCode()` đồng bộ: `Arrays.equals` đi với `Arrays.hashCode`, không đi với `array.hashCode()`.
6. Nếu phần tử của mảng cũng là mảng, xem xét chính sách **nông hay sâu**; không tự động coi `Arrays.equals/hashCode` một chiều là đệ quy.
7. Nếu mảng tham gia làm khóa hash, hạn chế sửa nội dung; cân nhắc bản sao phòng vệ khi nhận và trả mảng.
8. Kiểm thử cặp mảng khác tham chiếu nhưng cùng dữ liệu: `Arrays.equals(a,b)` phải `true` và `Arrays.hashCode(a) == Arrays.hashCode(b)` cũng phải `true`. Kiểm thử thêm các cặp dữ liệu khác nhau, `null`, rỗng và biên số thực khi phù hợp.

**Điểm ghi nhớ:** `Arrays.equals()` và `Arrays.hashCode()` là cặp công cụ tiêu chuẩn để mô tả **bằng nhau theo nội dung và băm theo nội dung của mảng một chiều**. Chúng khác với `equals()/hashCode()` kế thừa từ `Object` trên chính mảng. Khi đặt mảng vào trong một lớp, chính sách so sánh mảng phải khớp với chính sách băm mảng thì các cấu trúc hash mới hoạt động đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-003">Arrays.deepEquals() và Arrays.deepHashCode() cho mảng lồng</a>

<details>
<summary>Xem chi tiết</summary>

# `Arrays.deepEquals()` và `Arrays.deepHashCode()` cho mảng lồng trong Java

## Mục tiêu bài học

Trong Java, **mảng là đối tượng**. Hai mảng khác nhau có thể chứa cùng giá trị nhưng `==` vẫn là `false`; ngay cả `Arrays.equals()` cũng có giới hạn khi các phần tử của mảng ngoài lại là **những mảng khác**. `Arrays.deepEquals()` và `Arrays.deepHashCode()` giúp so sánh và tính mã băm **theo cấu trúc lồng nhau** trong trường hợp phù hợp.

Sau bài này, bạn có thể:

- Phân biệt `==`, `Arrays.equals()`, `Arrays.deepEquals()`, `Arrays.hashCode()` và `Arrays.deepHashCode()`.
- Giải thích vì sao `int[][]`, `String[][]` và `Object[]` chứa mảng có kết quả so sánh khác nhau giữa cách nông và sâu.
- Biết kiểu tham số mà hai API `deep*` chấp nhận và dùng `Arrays.equals()/hashCode()` cho mảng nguyên thủy một chiều.
- Hiểu cách xử lý mảng con, `null`, phần tử thường, mảng nguyên thủy, thứ tự và cấu trúc lồng.
- Giữ hợp đồng giữa bằng nhau và mã băm khi xây dựng lớp chứa mảng; nhận biết tác động của dữ liệu mutable và mảng tự tham chiếu.

## 1. Bốn phép so sánh thường gặp

Giả sử mỗi mảng con được tạo bởi một lệnh `new` riêng:

```java
int[][] a = {{1, 2}, {3, 4}};
int[][] b = {{1, 2}, {3, 4}};

System.out.println(a == b);                  // false
System.out.println(Arrays.equals(a, b));     // false
System.out.println(Arrays.deepEquals(a, b)); // true
```

Đây là đoạn mã minh họa đặt bên trong một phương thức, cần `import java.util.Arrays;`.

- **`a == b`** so sánh danh tính mảng ngoài. Hai mảng ngoài riêng biệt nên kết quả `false`.
- **`Arrays.equals(a, b)`** kiểm tra chiều dài và các phần tử cùng vị trí của mảng ngoài. Các phần tử này là các mảng `int[]` riêng biệt, và chúng không ghi đè `Object.equals()` để so theo phần tử. Do đó kết quả `false`.
- **`Arrays.deepEquals(a, b)`** đi xuống từng mảng con và so sánh phần tử tương ứng bằng quy tắc phù hợp: ở đây, các phần tử `int` bên trong tương ứng bằng nhau, nên kết quả `true`.

Mảng hai chiều `int[][]` thực chất là một mảng có các phần tử kiểu `int[]`. Các mảng con có thể dài khác nhau, và một phần tử mảng con có thể là `null`. Nó không phải một ma trận bộ nhớ phẳng bắt buộc có cùng số cột.

### Shallow vs. deep

```text
a: int[][]                    b: int[][]

  a[0] ──► [1, 2]              b[0] ──► [1, 2]
  a[1] ──► [3, 4]              b[1] ──► [3, 4]

Arrays.equals(a, b):
  So a[0] với b[0] như hai phần tử tham chiếu → khác đối tượng → false.

Arrays.deepEquals(a, b):
  So a[0] với b[0] bằng nội dung int[] → true;
  so a[1] với b[1] bằng nội dung int[] → true;
  kết quả toàn bộ → true.
```

Điểm cốt lõi: `deepEquals` so sánh **giá trị các phần tử trong cấu trúc mảng lồng**; nó không yêu cầu các mảng con nằm ở cùng địa chỉ hay là cùng đối tượng.

## 2. Kiểu tham số của `Arrays.deepEquals()` và `Arrays.deepHashCode()`

Hai phương thức có chữ ký cơ bản:

```java
public static boolean deepEquals(Object[] a1, Object[] a2);
public static int deepHashCode(Object[] a);
```

Vì `int[][]` là một mảng các `int[]` (mỗi `int[]` là một `Object`), `int[][]` có thể được truyền như một `Object[]`. `String[][]`, `long[][][]` và `Object[]` cũng có thể truyền vào các API này.

Ngược lại, **`int[]` nguyên thủy một chiều không phải `Object[]`**:

```java
int[] a = {1, 2};
int[] b = {1, 2};

boolean same = java.util.Arrays.equals(a, b); // true
int hash = java.util.Arrays.hashCode(a);

// Các dòng sau KHÔNG BIÊN DỊCH:
// java.util.Arrays.deepEquals(a, b);
// java.util.Arrays.deepHashCode(a);
```

Đây là đoạn minh họa trong một phương thức; hai dòng sai được comment cố ý.

Chọn API theo kiểu mảng tại vị trí so sánh:

| Dữ liệu | So sánh nội dung | Tính hash theo nội dung |
| --- | --- | --- |
| `int[]`, `byte[]`, `char[]`, `boolean[]`,… | `Arrays.equals(a, b)` | `Arrays.hashCode(a)` |
| `String[]`, không có mảng con | `Arrays.equals(a, b)` | `Arrays.hashCode(a)` |
| `int[][]` hoặc mảng nguyên thủy nhiều chiều | `Arrays.deepEquals(a, b)` | `Arrays.deepHashCode(a)` |
| `Object[]` chứa các mảng, hoặc `Object[][]` | `Arrays.deepEquals(a, b)` | `Arrays.deepHashCode(a)` |

Với mảng một chiều chứa đối tượng thường không phải mảng, `Arrays.deepEquals` cũng có thể dùng nhưng không mang lại lợi ích so sánh sâu đặc biệt. Cả hai đường thường đi đến `equals()` của phần tử. **Deep** ở đây chỉ nghĩa là đi sâu vào **các mảng lồng nhau**; nó không tự duyệt các trường của mọi đối tượng Java tùy ý.

## 3. Java 17+ hoàn chỉnh: so sánh nông, sâu, `null` và mảng nguyên thủy

Tạo tệp `DeepArraysDemo.java`:

```java
import java.util.Arrays;

public class DeepArraysDemo {
    public static void main(String[] args) {
        int[][] left = {{1, 2}, {3, 4}};
        int[][] right = {{1, 2}, {3, 4}};
        int[][] changed = {{1, 2}, {3, 5}};

        System.out.println("same outer reference: " + (left == right));
        System.out.println("shallow equals: "
                + Arrays.equals(left, right));
        System.out.println("deep equals: "
                + Arrays.deepEquals(left, right));
        System.out.println("deep hash equal: "
                + (Arrays.deepHashCode(left)
                   == Arrays.deepHashCode(right)));
        System.out.println("changed equals: "
                + Arrays.deepEquals(left, changed));

        Object[] mixedLeft = {
                new int[] {7, 8},
                new Object[] {new String[] {"A", "B"}, null}
        };
        Object[] mixedRight = {
                new int[] {7, 8},
                new Object[] {new String[] {"A", "B"}, null}
        };
        System.out.println("mixed shallow: "
                + Arrays.equals(mixedLeft, mixedRight));
        System.out.println("mixed deep: "
                + Arrays.deepEquals(mixedLeft, mixedRight));
        System.out.println("mixed hashes match: "
                + (Arrays.deepHashCode(mixedLeft)
                   == Arrays.deepHashCode(mixedRight)));

        int[] singleLeft = {10, 20};
        int[] singleRight = {10, 20};
        System.out.println("single primitive equals: "
                + Arrays.equals(singleLeft, singleRight));
        System.out.println("single primitive hash equal: "
                + (Arrays.hashCode(singleLeft)
                   == Arrays.hashCode(singleRight)));

        int[][] nullableLeft = {null, {9}};
        int[][] nullableRight = {null, {9}};
        System.out.println("nested null equals: "
                + Arrays.deepEquals(nullableLeft, nullableRight));

        left[1][1] = 99;
        System.out.println("after mutation equals: "
                + Arrays.deepEquals(left, right));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac DeepArraysDemo.java
java DeepArraysDemo
```

Kết quả chuẩn:

```text
same outer reference: false
shallow equals: false
deep equals: true
deep hash equal: true
changed equals: false
mixed shallow: false
mixed deep: true
mixed hashes match: true
single primitive equals: true
single primitive hash equal: true
nested null equals: true
after mutation equals: false
```

Giải thích:

1. `left` và `right` được tạo thành hai đối tượng mảng riêng nên `==` là `false`. `Arrays.equals` cũng là `false` do các mảng con là những đối tượng riêng.
2. `Arrays.deepEquals(left, right)` là `true` vì tất cả các hàng `int[]` tương ứng cùng số phần tử và cùng giá trị; bởi vậy hai mã `deepHashCode` bắt buộc bằng nhau.
3. `changed` khác một phần tử `5` thay vì `4` nên `deepEquals` là `false`.
4. Với `mixedLeft`/`mixedRight`, cấu trúc gồm `int[]`, một `Object[]` lồng trong, `String[]` và `null`. Phép so sâu xử lý được cả những cấp lồng này.
5. Mảng nguyên thủy một chiều dùng `Arrays.equals` và `Arrays.hashCode`.
6. Hai cấu trúc có phần tử mảng con `null` tại cùng vị trí có thể bằng nhau theo phép so sâu.
7. Sau khi thay `left[1][1]` thành `99`, nội dung đã khác `right`, nên `deepEquals` chuyển từ `true` sang `false`. Các API không “đóng băng” kết quả hay lưu snapshot dữ liệu.

## 4. `deepEquals` xử lý các phần tử như thế nào?

Ở mức khái niệm, `Arrays.deepEquals(a, b)` với hai `Object[]` có quy tắc:

1. Nếu hai tham chiếu mảng ngoài là cùng tham chiếu (kể cả cùng `null`), kết quả là `true`.
2. Nếu một mảng ngoài là `null` còn mảng kia không phải, kết quả là `false`.
3. Nếu độ dài khác nhau, kết quả là `false`.
4. Với từng vị trí tương ứng, kiểm tra hai phần tử. Nếu là các `Object[]` tương ứng, so đệ quy. Nếu là hai mảng nguyên thủy cùng loại, dùng phép so sánh phần tử nguyên thủy thích hợp. Nếu không phải cặp mảng cần so sâu, áp dụng quan hệ bằng nhau phù hợp với các đối tượng bình thường. Chỉ cần một vị trí không bằng nhau là trả về `false`.

Do đó, **kiểu mảng, mức lồng, thứ tự, độ dài và phần tử** đều quan trọng.

### Thứ tự không bị bỏ qua

```java
int[][] a = {{1}, {2}};
int[][] b = {{2}, {1}};

System.out.println(Arrays.deepEquals(a, b)); // false
```

Các dòng này minh họa bên trong phương thức và cần import `java.util.Arrays`. Hai mảng chứa cùng tập giá trị nếu không xét thứ tự, nhưng `deepEquals` là phép so sánh **theo vị trí**, không phải phép so sánh tập hợp hay hoán vị.

### Khác kiểu mảng nguyên thủy không được chuyển đổi

```java
Object[] a = {new int[] {1, 2}};
Object[] b = {new long[] {1L, 2L}};

System.out.println(Arrays.deepEquals(a, b)); // false
```

Mặc dù những số hiển thị có cùng độ lớn, `int[]` và `long[]` là hai kiểu mảng khác nhau. Không có quy tắc tự nâng kiểu nguyên thủy để biến các mảng thành “bằng nhau” về cấu trúc.

### Mảng răng cưa và mảng có phần tử `null`

```java
int[][] a = {{1}, null, {2, 3}};
int[][] b = {{1}, null, {2, 3}};
int[][] c = {{1}, {}, {2, 3}};

System.out.println(Arrays.deepEquals(a, b)); // true
System.out.println(Arrays.deepEquals(a, c)); // false
```

Một hàng `null` khác với một mảng rỗng `new int[0]`. Phương thức không tự coi dữ liệu vắng mặt là mảng rỗng.

### Phần tử không phải mảng

```java
Object[] a = {"hello", Integer.valueOf(7)};
Object[] b = {new String("hello"), Integer.valueOf(7)};

System.out.println(Arrays.deepEquals(a, b)); // true
```

Khi phần tử không phải mảng, `deepEquals` dựa vào `equals()` của các đối tượng. Không có “deep reflection” để tự truy cập những trường `private` trong lớp tùy ý. Nếu một lớp tự định nghĩa `equals` theo danh tính, các đối tượng khác nhau của lớp đó sẽ không tự bằng nhau chỉ vì đặt trong `Object[]`.

Các ví dụ ở phần này là các đoạn thân phương thức, không phải tệp Java chạy độc lập.

## 5. `Arrays.deepHashCode()` tính hash của cấu trúc lồng

`Arrays.deepHashCode(Object[] a)` tính mã băm theo các phần tử của mảng ngoài. Nếu một phần tử là mảng con:

- Mảng con kiểu tham chiếu (`Object[]`) được xử lý sâu bằng cách áp dụng `deepHashCode` cho mảng con đó.
- Mảng con kiểu nguyên thủy, ví dụ `int[]` hoặc `double[]`, dùng phép `Arrays.hashCode` thích hợp cho kiểu mảng đó.
- Phần tử đối tượng bình thường được hash dựa trên `hashCode()` của đối tượng; phần tử `null` được xử lý theo quy tắc của thuật toán.

Phép tổng hợp mã băm tuân theo cách kết hợp tuần tự của các phần tử. Với mảng không lồng, nó phù hợp ý tưởng tích lũy `31 * result + elementHash`; với mảng lồng, `elementHash` có thể là hash tính sâu của mảng con.

Ví dụ:

```java
int[][] a = {{1, 2}, {3}};
int[][] b = {{1, 2}, {3}};

System.out.println(Arrays.deepEquals(a, b)); // true
System.out.println(Arrays.deepHashCode(a)
        == Arrays.deepHashCode(b));           // true
```

Không cần nhớ một mã số hash cụ thể để dùng API đúng. Điều quan trọng là **quan hệ** giữa hai phép toán:

```text
Arrays.deepEquals(a, b) == true
                  ↓ bắt buộc
Arrays.deepHashCode(a) == Arrays.deepHashCode(b)
```

**Không được đảo chiều:** hai mảng khác nhau về phép so sánh sâu vẫn có thể có cùng deep hash. Mã băm là `int` nên collision là điều có thể xảy ra với bất kỳ hàm băm chất lượng tốt nào.

### Vì sao phải dùng cặp phương thức nhất quán?

Nếu định nghĩa “bằng nhau” cho một đối tượng chứa mảng bằng `Arrays.deepEquals`, hàm `hashCode` của đối tượng đó phải có quan hệ tương thích; `Arrays.deepHashCode` là cách tự nhiên để thực hiện. Ngược lại, nếu dùng `Arrays.hashCode` trên **mảng ngoài** chứa mảng con, nó nhìn các mảng con như những phần tử tham chiếu. Những mảng con riêng biệt nhưng cùng nội dung không bắt buộc có cùng hash theo cách tính nông.

Vì vậy, không nên ghép tùy tiện một phép so sánh sâu với một hàm hash nông khi các mảng con quyết định bằng nhau theo nội dung.

## 6. Cạm bẫy: `Arrays.hashCode`/`Arrays.equals` trên mảng ngoài

Với `int[][]`, mỗi phần tử của mảng ngoài là `int[]`. `int[]` không ghi đè `equals()` và `hashCode()` theo giá trị phần tử, nên các phép toán **nông** xử lý mảng con theo quy tắc mặc định của đối tượng.

So sánh:

```java
int[][] a = {{1, 2}};
int[][] b = {{1, 2}};

boolean shallow = Arrays.equals(a, b);       // false
boolean deep = Arrays.deepEquals(a, b);      // true
boolean deepHashesEqual =
        Arrays.deepHashCode(a)
        == Arrays.deepHashCode(b);           // true
```

Không nên in hay khẳng định trước một kết quả cố định cho `Arrays.hashCode(a) == Arrays.hashCode(b)` ở đây: mã băm danh tính của các mảng con có thể vô tình trùng nhau, dù điều đó không làm `Arrays.equals(a, b)` thành `true`. Về mặt thiết kế, hash nông **không tương thích được bảo đảm** với tiêu chí bằng nhau sâu.

Một cách nhìn khác là thay các mảng con bằng cách dùng chung tham chiếu:

```java
int[] row = {1, 2};
int[][] a = {row};
int[][] b = {row};

System.out.println(Arrays.equals(a, b)); // true
```

Hai mảng ngoài khác nhau nhưng đều chứa **chính đối tượng** `row`, nên phép so nông của phần tử cũng cho `true`. Đây không phải bằng chứng rằng `Arrays.equals` so dữ liệu sâu. Phép so sâu sẽ đúng cả khi mỗi bên có một mảng con mới cùng nội dung.

## 7. Khi cấu trúc mảng có alias (chia sẻ mảng con)

`deepEquals` so sánh **nội dung theo vị trí**; nó không yêu cầu biểu đồ tham chiếu bên trong phải có cùng kiểu chia sẻ đối tượng.

```java
int[] shared = {4, 5};
Object[] left = {shared, shared};
Object[] right = {new int[] {4, 5}, new int[] {4, 5}};

System.out.println(Arrays.deepEquals(left, right)); // true
```

Trong `left`, hai vị trí cùng tham chiếu đến `shared`. Trong `right`, chúng trỏ đến hai mảng `int[]` khác nhau. Tuy nhiên, ở mỗi vị trí, dữ liệu bên trong tương ứng bằng nhau nên phép so sâu cho `true`. Nếu yêu cầu nghiệp vụ của bạn cần biết hai vị trí có chia sẻ cùng một instance hay không, `deepEquals` **không phải** phép kiểm tra đó; hãy kiểm tra danh tính tham chiếu bằng `==` theo từng vị trí.

Một vấn đề khác là **mutable aliasing**: sửa `shared[0]` sẽ thay đổi nội dung có thể quan sát ở cả hai vị trí của `left`, còn `right` không đổi. Kết quả `deepEquals`/`deepHashCode` của cùng một cấu trúc có thể thay đổi theo thời gian nếu mảng con bị sửa.

## 8. Các mảng tự tham chiếu hoặc chứa chu trình

Mảng `Object[]` có thể trực tiếp hoặc gián tiếp chứa tham chiếu đến chính nó:

```java
Object[] cycle = new Object[1];
cycle[0] = cycle;
```

Với cấu trúc như trên, **không được dựa vào `Arrays.deepEquals` hoặc `Arrays.deepHashCode`**. Tài liệu API Java nêu rằng hành vi của các phép toán sâu là **không xác định** khi mảng chứa chính nó như phần tử, trực tiếp hoặc gián tiếp. Trên một số môi trường, việc đệ quy có thể dẫn đến `StackOverflowError`; đây không phải kết quả chuẩn mà ứng dụng có thể dựa vào.

Nếu mô hình dữ liệu thực sự có đồ thị đối tượng chứa chu trình (ví dụ nút cha/con tham chiếu qua lại), cần thiết kế thuật toán so sánh/hash chuyên biệt có theo dõi các đối tượng đã thăm và quy tắc rõ ràng cho chu trình. Không thể thay thế hoàn toàn bằng một lời gọi `deep*`.

**Phạm vi dùng tốt nhất:** mảng lồng có cấu trúc hữu hạn, không có chu trình tham chiếu tự chứa.

## 9. Áp dụng cho `equals()` và `hashCode()` của lớp chứa mảng

Mặc định, một trường kiểu mảng dùng phương thức `equals()` thừa kế từ `Object`, tức là so sánh **danh tính của đối tượng mảng**. Nếu mô hình nghiệp vụ muốn coi hai đối tượng là bằng nhau khi dữ liệu mảng lồng giống nhau, phải cài đặt rõ.

Ví dụ Java 17+ dùng một lớp value object đơn giản. Đoạn sau là **một chương trình độc lập** để thấy cách dùng cặp API sâu:

Tạo tệp `DeepValueEqualityDemo.java`:

```java
import java.util.Arrays;

public class DeepValueEqualityDemo {
    static final class MatrixValue {
        private final int[][] values;

        MatrixValue(int[][] source) {
            this.values = copy(source);
        }

        private static int[][] copy(int[][] source) {
            if (source == null) {
                return null;
            }
            int[][] result = new int[source.length][];
            for (int i = 0; i < source.length; i++) {
                result[i] = source[i] == null
                        ? null : source[i].clone();
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MatrixValue other)) {
                return false;
            }
            return Arrays.deepEquals(values, other.values);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(values);
        }
    }

    public static void main(String[] args) {
        int[][] input = {{1, 2}, {3}};
        MatrixValue a = new MatrixValue(input);
        MatrixValue b = new MatrixValue(new int[][] {{1, 2}, {3}});
        MatrixValue c = new MatrixValue(new int[][] {{1, 2}, {4}});

        System.out.println("equal values: " + a.equals(b));
        System.out.println("equal hashes: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("different values: " + a.equals(c));

        input[0][0] = 99;
        System.out.println("safe from source mutation: "
                + a.equals(b));
    }
}
```

Biên dịch và chạy:

```bash
javac DeepValueEqualityDemo.java
java DeepValueEqualityDemo
```

Đầu ra:

```text
equal values: true
equal hashes: true
different values: false
safe from source mutation: true
```

Ở đây `MatrixValue` là lớp `final` nên pattern matching với `instanceof` không đặt ra lựa chọn phức tạp về kế thừa. `Arrays.deepEquals` so dữ liệu mảng lồng, còn `Arrays.deepHashCode` tính hash từ cùng dữ liệu, bảo đảm chiều bắt buộc của hợp đồng.

Constructor sao chép **mảng ngoài và từng hàng `int[]`**, cho nên việc sửa `input` sau khi tạo `MatrixValue` không ảnh hưởng đến đối tượng. Các trường không có phương thức setter hay getter trả trực tiếp mảng nội bộ; trong ví dụ này, đối tượng giữ dữ liệu ổn định trong suốt vòng đời của nó. Nếu cấu trúc thay bằng các mảng tham chiếu lồng nhau chứa đối tượng mutable, `clone()` từng lớp như ví dụ này **chưa đủ** để bảo đảm bất biến hoàn toàn; phải xem xét chiến lược sao chép sâu hoặc đóng gói tương ứng.

### Vì sao không dùng mảng trực tiếp làm khóa nếu muốn bằng nhau theo nội dung?

`HashMap<int[][], String>` vẫn chấp nhận mảng làm khóa, nhưng khóa mảng không tự dùng `Arrays.deepEquals`:

```java
Map<int[][], String> map = new HashMap<>();
map.put(new int[][] {{1, 2}}, "found");

String result = map.get(new int[][] {{1, 2}});
// result là null vì khóa truy vấn là một đối tượng mảng mới
```

Đây là đoạn minh họa cần `import java.util.Map;` và `import java.util.HashMap;` trong một phương thức.

Nếu muốn so khóa theo nội dung mảng lồng, dùng lớp wrapper bất biến có cài đặt `equals/hashCode` theo nội dung như `MatrixValue`. Đừng kỳ vọng `HashMap` tự gọi `Arrays.deepEquals` cho từng khóa mảng.

**Lưu ý với `record` chứa mảng:** record tự sinh `equals/hashCode` cho các thành phần theo hợp đồng của từng kiểu. Thành phần là mảng vẫn có ngữ nghĩa equality/hash của chính đối tượng mảng (không tự so sâu). Vì vậy, `record Payload(int[][] values) {}` không tự thay thế một wrapper có `Arrays.deepEquals/deepHashCode` nếu nghiệp vụ cần so nội dung mảng lồng.

## 10. Lỗi thường gặp

1. **Dùng `Arrays.equals` cho `int[][]` rồi ngạc nhiên vì `false`.** Phần tử của mảng ngoài là các mảng con; dùng `Arrays.deepEquals` khi muốn so nội dung các hàng.
2. **Dùng `Arrays.deepEquals` cho `int[]` một chiều.** Không biên dịch vì chữ ký đòi `Object[]`; dùng `Arrays.equals`.
3. **Gọi `Arrays.deepHashCode` rồi suy ra hai mảng có hash giống nhau chắc chắn bằng nhau.** Sai: collision vẫn có thể xảy ra; dùng `deepEquals` để kết luận.
4. **So sâu trong `equals` nhưng hash nông trong `hashCode` của wrapper.** Có thể vi phạm hợp đồng giữa hai đối tượng bằng nhau theo nội dung; dùng cặp hàm nhất quán.
5. **Cho rằng `deepEquals` tự so sâu mọi đối tượng người dùng tự định nghĩa.** Với phần tử không phải mảng, kết quả theo `equals` của lớp phần tử.
6. **Bỏ qua thứ tự, độ dài, kiểu mảng hoặc `null`.** Các khác biệt này thường làm `deepEquals` cho `false`; phép so sâu không phải phép chuyển đổi kiểu hay so tập hợp.
7. **Đưa cấu trúc có chu trình vào `deepEquals/deepHashCode`.** Hành vi không xác định; cần thuật toán dành cho đồ thị có chu trình.
8. **Dùng đối tượng chứa mảng mutable làm khóa trong `HashMap`.** Nếu các phần tử dùng tính hash thay đổi khi đối tượng đang ở trong bảng, việc tra cứu có thể thất bại. Cần đóng gói/sao chép và bảo đảm dữ liệu dùng cho equality/hash ổn định.
9. **Nhầm cùng cấu trúc dữ liệu với cùng sơ đồ alias.** Hai vị trí trỏ chung một mảng con vẫn có thể so sâu bằng một cấu trúc chứa hai bản sao mảng con độc lập.

## 11. Tự kiểm tra

Xét các biến sau:

```java
int[][] a = {{1, 2}, null};
int[][] b = {{1, 2}, null};
int[][] c = {{2, 1}, null};
int[][] d = {{1, 2}, {}};

boolean q1 = a == b;
boolean q2 = Arrays.equals(a, b);
boolean q3 = Arrays.deepEquals(a, b);
boolean q4 = Arrays.deepHashCode(a)
          == Arrays.deepHashCode(b);
boolean q5 = Arrays.deepEquals(a, c);
boolean q6 = Arrays.deepEquals(a, d);
```

Đoạn mã minh họa đặt trong phương thức đã import `java.util.Arrays`.

**Đáp án và lý do:**

- `q1 = false`: hai mảng ngoài là hai đối tượng khác nhau.
- `q2 = false`: ở vị trí đầu, `a[0]` và `b[0]` là hai mảng `int[]` khác nhau về danh tính; so nông dừng ở đây.
- `q3 = true`: hai mảng con đầu cùng `[1, 2]` và hai phần tử tiếp theo đều `null`.
- `q4 = true`: khi phép so sâu cho `true`, mã băm sâu bắt buộc bằng nhau.
- `q5 = false`: `[1, 2]` khác `[2, 1]` do thứ tự phần tử.
- `q6 = false`: hàng `null` không tương đương với hàng rỗng.

Một bài tập mở rộng: tạo hai `Object[]` mỗi bên chứa `new int[] {1,2}` và `"abc"`, rồi so `Arrays.equals`, `Arrays.deepEquals`, `Arrays.hashCode`, `Arrays.deepHashCode`. Giải thích vì sao phép so sâu có thể là `true` ngay cả khi phép so nông là `false`, và vì sao không nên dùng một kết quả hash bằng nhau làm bằng chứng hai giá trị bằng nhau.

## Ghi nhớ

- **`Arrays.equals`/`Arrays.hashCode`** phù hợp cho so sánh nội dung mảng nguyên thủy một chiều, hoặc mảng đối tượng không cần đi xuống những mảng con.
- **`Arrays.deepEquals`/`Arrays.deepHashCode`** phù hợp cho `Object[]` chứa cấu trúc mảng lồng, bao gồm `int[][]` và `String[][]`; chúng đi sâu qua các mảng con để so sánh và tính hash theo nội dung.
- **`deepEquals(a, b) == true` ⇒ deep hash của `a` và `b` bằng nhau**, nhưng cùng deep hash không bảo đảm hai mảng bằng nhau.
- Các phương thức `deep*` không tự xử lý mọi đồ thị đối tượng tùy ý có chu trình, không tự so trường bên trong mọi object và không tự làm dữ liệu trở nên bất biến. Khi dùng mảng lồng trong `equals/hashCode` hoặc làm khóa của cấu trúc băm, phải thiết kế hợp đồng và bảo vệ trạng thái dữ liệu phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-004">Objects.deepEquals() và cách xử lý hai đối số</a>

<details>
<summary>Xem chi tiết</summary>

# `Objects.deepEquals()` và cách xử lý hai đối số

`java.util.Objects.deepEquals(Object a, Object b)` là phương thức tiện ích để kiểm tra **hai đối số có bằng nhau không**, đồng thời xử lý **mảng và các mảng lồng nhau** theo nội dung. Điểm khác biệt quan trọng so với `Objects.equals(a, b)` là: khi cả hai đối số là mảng, `deepEquals` có khả năng so sánh các phần tử tương ứng, kể cả phần tử là một mảng khác; còn `Objects.equals` thường dựa vào `equals` của chính mảng, tức so sánh danh tính.

Điều này hữu ích khi dữ liệu gồm `int[]`, `String[]`, `int[][]`, `Object[]` chứa nhiều loại mảng, hoặc cấu trúc lồng nhau. Tuy nhiên, **`deepEquals` không tự đi sâu vào mọi trường của một đối tượng Java thông thường**: nếu hai đối số không phải mảng, kết quả vẫn dựa trên `equals` của đối tượng bên trái.

## 1. Khai báo và ý nghĩa

Phương thức thuộc `java.util.Objects`:

~~~java
public static boolean deepEquals(Object a, Object b)
~~~

Hai tham số đều kiểu `Object`, vì vậy bạn có thể truyền một đối tượng thông thường, một mảng nguyên thủy như `int[]`, một mảng đối tượng như `String[]`, mảng nhiều chiều như `int[][]`, hoặc `null`. Không cần ép một mảng nguyên thủy thành `Object[]`.

Tóm tắt hành vi:

| Hai đối số | Kết quả |
| --- | --- |
| Cùng một tham chiếu, kể cả đều `null` | `true`. |
| Một đối số là `null`, đối số kia không `null` | `false`. |
| Cả hai là mảng đối tượng (`Object[]` hoặc các kiểu mảng tương thích) | So sánh phần tử theo thứ tự và tiếp tục đi sâu khi phần tử là mảng. |
| Cả hai là mảng nguyên thủy cùng kiểu, ví dụ `int[]` | So sánh nội dung từng phần tử theo quy tắc của `Arrays.equals` tương ứng. |
| Cả hai là mảng lồng, ví dụ `int[][]` | So các mảng con tương ứng một cách đệ quy. |
| Không phải cả hai đều là mảng | Về cơ bản sử dụng `a.equals(b)` sau khi xử lý `null`/cùng tham chiếu; không tự khám phá mọi trường. |

Các đối số là **cùng mảng** thì luôn bằng nhau theo nhánh kiểm tra tham chiếu trước tiên, kể cả bên trong mảng có các cấu trúc phức tạp. Với **hai mảng độc lập**, `deepEquals` mới cần kiểm tra các phần tử.

### Thứ tự xử lý quan trọng

Có thể hiểu quy trình chính như sau:

1. Nếu `a == b`, trả `true` ngay. Trường hợp này cũng bao gồm `a == null` và `b == null`.
2. Nếu đúng một đối số là `null`, trả `false`.
3. Với hai đối số không `null`, thực hiện phép so sánh sâu thích hợp cho mảng: nếu chúng là mảng đối tượng thì duyệt phần tử và có thể đi sâu vào mảng con; nếu là hai mảng nguyên thủy cùng loại thì so nội dung; các trường hợp còn lại sử dụng ngữ nghĩa `equals` phù hợp.

Đây là mô hình hành vi để học, không phải lời hứa về thứ tự từng phép gọi nội bộ của mọi bản JDK. Điều cần nhớ là **so mảng theo nội dung khi phù hợp, xử lý `null` an toàn, còn đối tượng không phải mảng vẫn phụ thuộc `equals`**.

## 2. Ví dụ Java 17+: hai đối số, `null`, mảng và đối tượng thường

Tệp **`DeepEqualsArgumentsDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public class DeepEqualsArgumentsDemo {
    public static void main(String[] args) {
        System.out.println(Objects.deepEquals(null, null));
        System.out.println(Objects.deepEquals(null, new int[] {1, 2}));

        int[] first = {1, 2};
        int[] second = {1, 2};
        int[] alias = first;

        System.out.println(first == second);
        System.out.println(Objects.equals(first, second));
        System.out.println(Objects.deepEquals(first, second));
        System.out.println(Objects.deepEquals(first, alias));
        System.out.println(Objects.deepEquals(first, new int[] {1, 3}));
        System.out.println(Objects.deepEquals(first, new long[] {1, 2}));

        String left = new String("Java");
        String right = new String("Java");
        System.out.println(left == right);
        System.out.println(Objects.deepEquals(left, right));

        String[] names1 = {"Lan", "An"};
        String[] names2 = {"Lan", "An"};
        System.out.println(Objects.equals(names1, names2));
        System.out.println(Objects.deepEquals(names1, names2));
        System.out.println(Arrays.equals(names1, names2));
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac DeepEqualsArgumentsDemo.java
java DeepEqualsArgumentsDemo
~~~

Kết quả chuẩn:

~~~text
true
false
false
false
true
true
false
false
false
true
false
true
true
~~~

Giải thích theo nhóm:

- `deepEquals(null, null)` trả `true`; chỉ một bên `null` thì `false`. Vì thế dùng phương thức này không gây `NullPointerException` chỉ do một tham số là `null`.
- Hai `int[]` độc lập chứa `{1, 2}` có `first == second` là `false`. `Objects.equals(first, second)` cũng là `false` vì `int[]` không override `Object.equals` để so phần tử. Nhưng `Objects.deepEquals(first, second)` là `true`.
- `alias = first` là cùng tham chiếu, nên `deepEquals(first, alias)` là `true`. Nếu thay `2` bằng `3`, nội dung khác nên `false`. Mảng `int[]` và `long[]` chứa những số trông giống nhau vẫn là hai kiểu mảng nguyên thủy khác nhau, không được `deepEquals` tự ép thành một kiểu chung.
- Hai `String` độc lập có `==` là `false` nhưng `deepEquals` là `true`: với chuỗi không phải mảng, việc so sánh dùng `String.equals`, không phải phản chiếu/so từng trường nội bộ của chuỗi.
- Với hai `String[]` độc lập, `Objects.equals` trả `false`, trong khi `Objects.deepEquals` và `Arrays.equals` cùng trả `true` khi phần tử tương ứng có cùng nội dung.

Lưu ý: `Arrays` được import trong ví dụ để trình bày phép so sánh mảng cùng cấp; nó không phải yêu cầu khi chỉ gọi `Objects.deepEquals`.

## 3. Mảng lồng nhau: “deep” diễn ra ở đâu?

Xét hai ma trận `int[][]`. Trong Java, đây là **mảng chứa các tham chiếu tới mảng `int[]`**, nên việc so mảng ở tầng ngoài phải tiếp tục so nội dung mảng con.

~~~java
int[][] a = {{1, 2}, {3}};
int[][] b = {{1, 2}, {3}};

System.out.println(a == b);                    // false
System.out.println(java.util.Arrays.equals(a, b));     // false
System.out.println(java.util.Arrays.deepEquals(a, b)); // true
System.out.println(java.util.Objects.deepEquals(a, b)); // true
~~~

Đây là đoạn mã minh họa trong thân một phương thức. `Arrays.equals(a, b)` ở đây chỉ so các **phần tử của mảng tầng ngoài**; mỗi phần tử là `int[]`, mà `int[]` không có `equals` theo nội dung. `Arrays.deepEquals(a, b)` và `Objects.deepEquals(a, b)` đi sâu vào từng `int[]` nên cho `true`.

Có thể hình dung phép so sánh:

~~~text
a: int[][{1, 2}, {3}]
b: int[][{1, 2}, {3}]

Tầng ngoài: cùng số phần tử?              Có
  a[0] và b[0]: so {1, 2} với {1, 2}     Bằng
  a[1] và b[1]: so {3} với {3}           Bằng
Kết luận:                                 true
~~~

Với một mảng `Object[]` chứa các phần tử hỗn hợp, `deepEquals` cũng biết phân biệt phần tử thường và phần tử mảng:

~~~java
Object[] x = {
    "Java",
    new int[] {1, 2},
    new Object[] {"nested", new long[] {9, 10}}
};
Object[] y = {
    new String("Java"),
    new int[] {1, 2},
    new Object[] {"nested", new long[] {9, 10}}
};

System.out.println(java.util.Objects.equals(x, y));     // false
System.out.println(java.util.Objects.deepEquals(x, y)); // true
~~~

`"Java"` và `new String("Java")` có nội dung bằng nhau; `int[]` cùng phần tử và `long[]` bên trong cùng phần tử. Hai mảng ngoài khác tham chiếu nhưng bằng nhau khi so sâu.

**Không có chuyện Java tự “flatten” mảng:** cách phân tầng, độ dài và thứ tự phần tử vẫn có ý nghĩa. Ví dụ `int[][] {{1, 2}, {3}}` **không** bằng `int[][] {{1}, {2, 3}}`, dù ghép tất cả số theo thứ tự đều có thể ra `1, 2, 3`.

## 4. `Objects.deepEquals`, `Objects.equals` và `Arrays.equals`

Ba API này trả lời các câu hỏi hơi khác nhau:

| API | Tham số | Cách so sánh nổi bật |
| --- | --- | --- |
| `Objects.equals(a, b)` | Hai `Object` | An toàn `null`, nếu cả hai khác `null` thì gọi `a.equals(b)`. Mảng thường so tham chiếu. |
| `Objects.deepEquals(a, b)` | Hai `Object` | An toàn `null`; nếu hai đối số là mảng, so sâu thích hợp cho mảng; nếu là đối tượng thường, dựa trên `equals`. |
| `Arrays.equals(a, b)` | Hai mảng một chiều có overload tương ứng | So phần tử tầng hiện tại; với mảng lồng chứa mảng con, không tự động so sâu mảng con. |
| `Arrays.deepEquals(a, b)` | Hai `Object[]` | So sâu phần tử/mảng con. Có thể dùng với `int[][]` vì mỗi `int[][]` là một mảng tham chiếu chứa `int[]`; không gọi trực tiếp với hai `int[]`. |

Một điểm rất quan trọng về kiểu dữ liệu: `Arrays.deepEquals(Object[] a, Object[] b)` yêu cầu **mảng tham chiếu ở tầng ngoài**. Vì `int[]` không phải `Object[]`, lệnh `Arrays.deepEquals(new int[]{1}, new int[]{1})` **không biên dịch**. Với hai `int[]`, dùng `Arrays.equals` hoặc `Objects.deepEquals`.

### Chọn công cụ theo dữ liệu

- Hai `int[]` hoặc `double[]` cùng loại: `Arrays.equals` thể hiện rõ ý định so nội dung một chiều.
- Hai `String[]` một chiều: `Arrays.equals` là đủ nếu chỉ cần so từng `String`.
- Hai `int[][]` hoặc hai `Object[]` chứa mảng con: `Arrays.deepEquals` hoặc `Objects.deepEquals` khi muốn so sâu.
- Hai tham số đang có kiểu `Object`, có thể là mảng hoặc không: `Objects.deepEquals` giúp xử lý cả hai trường hợp theo quy tắc đã nêu.
- Hai đối tượng thông thường, không liên quan mảng: `Objects.equals` thường bộc lộ rõ mục đích hơn; dùng `Objects.deepEquals` không mang lại phép so sâu trường dữ liệu bên trong đối tượng.

## 5. “Deep” không có nghĩa là tự so sánh mọi thuộc tính của class

Giả sử một class `Box` chứa trường `int[]`, nhưng **không override `equals`**. Khi đưa hai `Box` độc lập vào `Objects.deepEquals`, kết quả vẫn là `false`, ngay cả khi hai mảng bên trong cùng nội dung.

Tệp **`DeepEqualsObjectDemo.java`**:

~~~java
import java.util.Objects;

public class DeepEqualsObjectDemo {
    static final class Box {
        private final int[] values;

        Box(int[] values) {
            this.values = values;
        }
    }

    record ArrayRecord(int[] values) {}

    public static void main(String[] args) {
        Box first = new Box(new int[] {4, 5});
        Box second = new Box(new int[] {4, 5});

        System.out.println(first == second);
        System.out.println(Objects.deepEquals(first, second));
        System.out.println(Objects.deepEquals(first.values, second.values));

        ArrayRecord r1 = new ArrayRecord(new int[] {4, 5});
        ArrayRecord r2 = new ArrayRecord(new int[] {4, 5});

        System.out.println(r1.equals(r2));
        System.out.println(Objects.deepEquals(r1, r2));
        System.out.println(Objects.deepEquals(r1.values(), r2.values()));
    }
}
~~~

Chạy:

~~~bash
javac DeepEqualsObjectDemo.java
java DeepEqualsObjectDemo
~~~

Kết quả chuẩn:

~~~text
false
false
true
false
false
true
~~~

Phân tích:

- `Box` là class thường không override `equals`, nên `Box.equals` thừa hưởng từ `Object` và chỉ so danh tính. `Objects.deepEquals(first, second)` không tự dò trường `values`: kết quả `false`.
- Nhưng khi truyền **chính hai mảng `first.values` và `second.values`**, `deepEquals` so phần tử nên trả `true`.
- Record Java sinh `equals` dựa trên component, nhưng với component là **mảng**, phép so vẫn theo danh tính mảng khi hai record khác nhau. Do đó hai `ArrayRecord` độc lập chứa `int[]` cùng nội dung vẫn `equals` là `false`.
- Muốn hai instance của class hoặc record **bằng nhau theo nội dung mảng**, bạn phải tự xây dựng ngữ nghĩa `equals`/ `hashCode` của kiểu bao ngoài, không chỉ thay lời gọi ngoài thành `Objects.deepEquals(box1, box2)`.

Điều này giúp phân biệt **so sánh sâu trên mảng là đối số** với **so sánh sâu xuyên qua các trường của bất kỳ object nào**. Java chỉ cung cấp trường hợp thứ nhất trong `Objects.deepEquals`.

## 6. Dùng `Objects.deepEquals` trong `equals` của một value object

Một ứng dụng phổ biến là class giữ cấu trúc mảng lồng và muốn định nghĩa bằng nhau dựa trên toàn bộ phần tử. Khi tự override `equals`, có thể sử dụng `Objects.deepEquals(data, that.data)`. Nhưng **`hashCode` phải sử dụng thuật toán hash tương ứng với phép so sâu**.

Tệp **`MatrixKeyDemo.java`** (Java 17+):

~~~java
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MatrixKeyDemo {
    static final class MatrixKey {
        private final int[][] data;

        MatrixKey(int[][] source) {
            Objects.requireNonNull(source);
            data = new int[source.length][];
            for (int i = 0; i < source.length; i++) {
                int[] row = Objects.requireNonNull(source[i]);
                data[i] = Arrays.copyOf(row, row.length);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MatrixKey that)) {
                return false;
            }
            return Objects.deepEquals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(data);
        }
    }

    public static void main(String[] args) {
        int[][] source = {{1, 2}, {3}};
        MatrixKey first = new MatrixKey(source);
        MatrixKey sameValue = new MatrixKey(new int[][] {{1, 2}, {3}});
        MatrixKey different = new MatrixKey(new int[][] {{1, 2}, {4}});

        System.out.println(first == sameValue);
        System.out.println(first.equals(sameValue));
        System.out.println(first.hashCode() == sameValue.hashCode());
        System.out.println(first.equals(different));

        Map<MatrixKey, String> names = new HashMap<>();
        System.out.println(names.put(first, "old"));
        System.out.println(names.put(sameValue, "new"));
        System.out.println(names.put(different, "other"));
        System.out.println(names.size());
        System.out.println(names.get(first));

        source[0][0] = 99;
        System.out.println(first.equals(sameValue));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac MatrixKeyDemo.java
java MatrixKeyDemo
~~~

Kết quả chuẩn:

~~~text
false
true
true
false
null
old
null
2
new
true
~~~

Ý nghĩa:

1. `first` và `sameValue` là hai đối tượng `MatrixKey` độc lập, nhưng dữ liệu ma trận giống nhau nên `equals` trả `true`.
2. `hashCode` dùng `Arrays.deepHashCode(data)` để hai ma trận bằng nhau theo `Objects.deepEquals` cũng có mã băm bằng nhau. Đây là một yêu cầu **bắt buộc** của hợp đồng `equals`/`hashCode`.
3. `different` chứa giá trị `4` ở vị trí khác nên không bằng `first`.
4. Thêm `sameValue` vào `HashMap` thay giá trị của khóa logic đã có; `size()` không tăng. Khóa `different` tạo entry thứ hai.
5. Constructor sao chép **từng mảng con**, nên sửa `source[0][0]` bên ngoài không làm thay đổi dữ liệu trong `first`. Điều này giúp khóa có hash ổn định sau khi dùng trong `HashMap`.

Ví dụ cho phép các hàng có độ dài khác nhau (`int[][]` là mảng của các mảng) nhưng từ chối hàng `null` bằng `requireNonNull`. Đó là quyết định thiết kế của `MatrixKey`, không phải giới hạn của `Objects.deepEquals`: bản thân phép so sâu có thể so các vị trí `null` tương ứng.

**Không thay `Arrays.deepHashCode(data)` bằng `Objects.hashCode(data)`:** đối với mảng, `Objects.hashCode` đi theo `hashCode` của chính mảng, vốn không phải hash sâu theo nội dung. Hai `MatrixKey` bằng nhau theo dữ liệu mảng có thể nhận hash không khớp và làm hỏng cách dùng làm key trong hash collection.

## 7. Những trường hợp góc quan trọng

### 7.1. Cả hai đối số `null`

~~~java
Objects.deepEquals(null, null);             // true
Objects.deepEquals(null, new Object[0]);    // false
Objects.deepEquals(new int[0], new int[0]); // true
~~~

Hai mảng rỗng khác tham chiếu vẫn bằng nhau về nội dung nếu cùng kiểu/cấu trúc phù hợp. Mảng rỗng không giống `null`.

### 7.2. Hai mảng có cùng dữ liệu nhưng không cùng loại mảng nguyên thủy

~~~java
Objects.deepEquals(new int[] {1, 2}, new long[] {1, 2}); // false
Objects.deepEquals(new int[] {1}, new int[] {1});         // true
~~~

`Objects.deepEquals` **không thực hiện chuyển kiểu số** để coi `int[]` và `long[]` là cùng cấu trúc giá trị. Nếu nghiệp vụ muốn so sau chuẩn hóa kiểu, phải định nghĩa phép chuyển/so riêng.

### 7.3. Mảng tham chiếu có thể có kiểu runtime khác nhau

~~~java
Objects.deepEquals(
    new String[] {"Java"},
    new Object[] {"Java"}
); // true
~~~

Cả hai đối số đều là mảng tham chiếu (`Object[]`) với một phần tử có nội dung bằng nhau; phép so sâu có thể trả `true` dù lớp mảng runtime khác nhau. Đừng hiểu `deepEquals` là yêu cầu `getClass()` của hai mảng phải giống hệt nhau.

### 7.4. Cách so `NaN` và `+0.0/-0.0` trong mảng số thực

Các phép `Arrays.equals` cho mảng `float[]`/`double[]` có quy tắc theo bit chuẩn hóa tương ứng với `Float.equals`/`Double.equals`: các biểu diễn `NaN` được coi là bằng nhau và `+0.0` khác `-0.0`. Vì `Objects.deepEquals` dùng ngữ nghĩa mảng tương ứng, cần chú ý:

~~~java
Objects.deepEquals(
    new double[] {Double.NaN},
    new double[] {Double.NaN}
); // true

Objects.deepEquals(
    new double[] {+0.0},
    new double[] {-0.0}
); // false
~~~

Nếu nghiệp vụ cần quy tắc số thực khác, không nên tự giả định `deepEquals` dùng đúng phép so mong muốn. Hãy định nghĩa phép so phù hợp, đồng thời cân nhắc hợp đồng bằng nhau và mã băm nếu triển khai `equals`.

### 7.5. Mảng chứa tham chiếu vòng

Nếu mảng `Object[]` chứa **chính nó** làm phần tử, hoặc các mảng tham chiếu lẫn nhau tạo chu trình, phép duyệt sâu có thể tiếp tục đệ quy không kết thúc và gây lỗi như `StackOverflowError`. Tài liệu `Arrays.deepEquals` không định nghĩa hành vi đối với mảng chứa tham chiếu vòng. Không dùng `Objects.deepEquals` như một thuật toán so sánh đồ thị tổng quát có khả năng xử lý chu trình; nếu dữ liệu là đồ thị, hãy thiết kế thuật toán có theo dõi các nút đã thăm.

### 7.6. Phần tử là đối tượng tùy biến

~~~java
class Item {
    final int id;
    Item(int id) { this.id = id; }
}

Object[] left = {new Item(1)};
Object[] right = {new Item(1)};
System.out.println(java.util.Objects.deepEquals(left, right)); // false
~~~

Đoạn trên là mã minh họa nằm trong ngữ cảnh khai báo lớp và phương thức phù hợp. Vì `Item` không override `equals`, so sánh hai `Item` bên trong mảng vẫn chỉ theo danh tính. `deepEquals` **không tự mở từng `Item` để so trường `id`**. Nếu muốn phần tử `Item` bằng nhau theo giá trị, cần cài đặt `Item.equals` và `Item.hashCode` thích hợp.

## 8. Sai lầm thường gặp và cách tránh

1. **Cho rằng `Objects.equals` đã đủ cho mảng.** Với hai `int[]` hoặc `String[]` độc lập có cùng nội dung, `Objects.equals` thường trả `false`. Chọn `Arrays.equals` hoặc `Objects.deepEquals` khi muốn so nội dung.
2. **Dùng `Arrays.equals` cho mảng lồng rồi chờ so sâu.** Đối với `int[][]`, các phần tử ở tầng ngoài là các `int[]`. Chọn `Arrays.deepEquals` hoặc `Objects.deepEquals` nếu muốn so từng mảng con.
3. **Tưởng `Objects.deepEquals` phản chiếu mọi trường của class.** Với hai object thông thường, phương thức dựa vào `equals` của class. Nó không tự so các trường hay property lồng nhau.
4. **Dùng `Objects.deepEquals` trong `equals` nhưng quên sửa `hashCode`.** Với mảng nhiều chiều, dùng `Arrays.deepHashCode` tương ứng để tránh vi phạm hợp đồng của các hash collection.
5. **Để cấu trúc mảng có thể thay đổi sau khi làm key của `HashMap`.** Khi dữ liệu dùng trong `equals`/`hashCode` thay đổi, map có thể không tìm được entry. Dùng bản sao bảo vệ và thiết kế value object bất biến khi phù hợp.
6. **Dùng so sâu trên đồ thị có chu trình.** Trường hợp mảng chứa chính nó hoặc chứa vòng qua các mảng khác không được đảm bảo an toàn.
7. **Coi “giá trị tương đương theo nghiệp vụ” và “`deepEquals` bằng nhau” là một.** `deepEquals` tôn trọng kiểu mảng, thứ tự phần tử và `equals` của đối tượng thành phần; không tự chuẩn hóa văn bản, số liệu, thứ tự hay định danh nghiệp vụ.

## 9. Quy trình chọn phép so sánh

Khi phải so sánh hai giá trị trong Java:

1. **Kiểm tra loại dữ liệu thực tế:** đối tượng thường, mảng nguyên thủy, mảng tham chiếu, mảng lồng hay một đối tượng chứa trường là mảng.
2. **Nếu muốn biết cùng instance**, sử dụng `==` với tham chiếu.
3. **Nếu chỉ muốn so giá trị của hai đối tượng thường**, dùng `Objects.equals(a, b)` khi cần xử lý `null`, bảo đảm kiểu đó có `equals` phù hợp.
4. **Nếu là mảng một chiều rõ kiểu**, dùng `Arrays.equals(a, b)`.
5. **Nếu là mảng nhiều tầng**, chọn `Arrays.deepEquals` với mảng `Object[]` ở tầng ngoài hoặc `Objects.deepEquals` với hai giá trị kiểu `Object`.
6. **Nếu viết value object chứa mảng**, cài đặt `equals` so trường mảng theo nội dung và `hashCode` theo cùng dữ liệu; bảo vệ khỏi thay đổi ngoài ý muốn.
7. **Nếu có yêu cầu đặc biệt** như so số theo dung sai, bỏ qua thứ tự, chuẩn hóa chữ, hoặc dữ liệu có vòng tham chiếu, hãy viết thuật toán so sánh riêng phù hợp với hợp đồng và mục tiêu bài toán.

## 10. Câu hỏi tự kiểm tra

1. Vì sao `Objects.equals(new int[]{1,2}, new int[]{1,2})` là `false` nhưng `Objects.deepEquals(...)` là `true`?
2. Với hai đối số đều `null`, kết quả `Objects.deepEquals` là gì? Nếu đúng một đối số `null` thì sao?
3. Vì sao `Arrays.equals(a, b)` với hai `int[][]` độc lập cùng nội dung có thể trả `false`, trong khi `Arrays.deepEquals(a,b)` trả `true`?
4. Nếu `Box` có trường `int[] values` nhưng không override `equals`, gọi `Objects.deepEquals(box1,box2)` có tự so từng phần tử của `values` không?
5. Vì sao một class dùng `Objects.deepEquals(matrix1,matrix2)` trong `equals` nên dùng `Arrays.deepHashCode(matrix)` tương ứng trong `hashCode`?
6. `Objects.deepEquals(new int[]{1}, new long[]{1})` trả gì? Phương thức có tự chuyển `int` sang `long` không?
7. Nếu một mảng `Object[]` chứa tham chiếu tới chính nó, vì sao không thể giả định phép so sâu luôn kết thúc an toàn?

**Ghi nhớ:** `Objects.deepEquals(a, b)` nhận **hai đối số kiểu `Object`**, kiểm tra cùng tham chiếu/`null` trước, rồi so sánh **nội dung mảng, kể cả mảng lồng** khi cả hai đối số là mảng. Với các đối tượng thông thường, nó vẫn dựa vào `equals`. Khi dùng so sâu trong `equals` của class chứa mảng, luôn giữ `hashCode` tương ứng và bảo vệ dữ liệu khỏi thay đổi ngoài ý muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-005">Mảng nguyên thủy khác mảng lớp bao</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng nguyên thủy khác mảng lớp bao trong Java: kiểu dữ liệu, hợp đồng bằng nhau và cấu trúc lồng

## 1. Phân biệt ngay từ khai báo

Trong Java, **mảng nguyên thủy** (primitive array) và **mảng lớp bao** (wrapper array) không phải hai cách viết thay thế cho cùng một kiểu. Chúng có kiểu phần tử, cách lưu trữ, giá trị mặc định, khả năng chứa `null` và hành vi so sánh khác nhau.

Ví dụ:

```java
int[] primitive = {1, 2, 3};
Integer[] wrapper = {1, 2, 3};
```

Đây là hai dòng minh họa được đặt trong phạm vi thích hợp như một phương thức Java; chương trình độc lập ở mục 6 sẽ sử dụng cả hai kiểu.

- `int[]` chứa những phần tử kiểu nguyên thủy `int`. Mỗi phần tử là một giá trị số nguyên 32 bit; phần tử không thể là `null`.
- `Integer[]` chứa những **tham chiếu** đến đối tượng kiểu `java.lang.Integer`. Một phần tử có thể tham chiếu đến một `Integer` hoặc bằng `null`.
- Cả hai biến `primitive` và `wrapper` đều là **tham chiếu đến đối tượng mảng**. Bản thân biến mảng có thể bằng `null`; sự khác biệt là **kiểu của từng phần tử bên trong**.
- Java có **autoboxing** giữa `int` và `Integer` trong ngữ cảnh phù hợp, chẳng hạn khi gán `Integer x = 1`. Nhưng Java **không tự động chuyển cả mảng** `int[]` thành `Integer[]`.

Bảng đối chiếu:

| Đặc điểm | `int[]` | `Integer[]` |
| --- | --- | --- |
| Kiểu phần tử | Giá trị nguyên thủy `int` | Tham chiếu `Integer` |
| Phần tử mặc định khi `new ...[n]` | `0` | `null` |
| Phần tử có thể là `null` | Không | Có |
| Bộ nhớ điển hình | Các phần tử `int` được lưu trực tiếp trong mảng | Mảng lưu các tham chiếu; đối tượng `Integer` có thể được lưu riêng |
| Đọc phần tử thành `int` | Trực tiếp | Cần unboxing; có thể lỗi nếu phần tử là `null` |
| Kiểu runtime | `[I` | `[Ljava.lang.Integer;` |
| Dùng `Arrays.equals` theo nội dung | Overload `Arrays.equals(int[], int[])` | Overload `Arrays.equals(Object[], Object[])` |
| Truyền cho `Object[]` | Không | Có |
| Truyền vào `Arrays.asList` | Một phần tử kiểu `int[]` | Một phần tử danh sách cho mỗi `Integer` |
| Dòng stream phù hợp | `IntStream` từ `Arrays.stream(int[])` | `Stream<Integer>` từ `Arrays.stream(Integer[])` |

Tên kiểu runtime dạng `[I` và `[Ljava.lang.Integer;` là biểu diễn tên lớp mảng của JVM; thông thường khi viết mã ứng dụng, nên dùng kiểu khai báo dễ đọc `int[]` và `Integer[]`.

## 2. Vì sao không thể gán trực tiếp, ép kiểu hoặc tự động chuyển cả mảng?

Autoboxing chỉ giúp chuyển **một giá trị** trong ngữ cảnh phù hợp:

```java
int n = 7;
Integer wrapped = n; // boxing: int -> Integer
int again = wrapped; // unboxing: Integer -> int
```

Ngược lại, cả ba dòng bên dưới **không biên dịch** (đưa ra để giải thích lỗi, không nằm trong chương trình hoàn chỉnh):

```java
// Integer[] boxed = new int[] {1, 2, 3}; // incompatible types
// int[] unboxed = new Integer[] {1, 2, 3}; // incompatible types
// Object[] objects = new int[] {1, 2, 3}; // incompatible types
```

Mỗi mảng là một đối tượng có kiểu thành phần riêng, không có luật boxing tự động đi qua toàn bộ các phần tử. Một phép ép kiểu không thể biến đối tượng mảng `int[]` thành đối tượng mảng `Integer[]`.

Cách chuyển đổi có chủ đích:

```java
int[] numbers = {1, 2, 3};
Integer[] boxed = Arrays.stream(numbers)
        .boxed()
        .toArray(Integer[]::new);
```

Đây là đoạn minh họa có thể đặt trong một phương thức khi đã `import java.util.Arrays`. `Arrays.stream(numbers)` tạo `IntStream`; `boxed()` tạo `Stream<Integer>`; `toArray(Integer[]::new)` tạo một mảng `Integer[]` **mới**. Chuyển đổi có chi phí tạo mảng và có thể tạo/cần các đối tượng lớp bao.

Chiều ngược lại cần xử lý được các phần tử `null`:

```java
Integer[] boxed = {1, 2, 3};
int[] numbers = Arrays.stream(boxed)
        .mapToInt(Integer::intValue)
        .toArray();
```

Nếu có phần tử `null`, `Integer::intValue` sẽ gây `NullPointerException` khi chạy. Phải xác định chính sách miền dữ liệu trước khi chuyển: bác bỏ null, lọc bỏ null (có thể làm thay đổi số phần tử), hoặc ánh xạ null thành giá trị được quy ước. Không tự mặc định null tương đương với 0 khi nghiệp vụ không quy định.

## 3. Hợp đồng `equals()`: mảng khác các đối tượng giá trị thông thường

Trong Java, mảng **không ghi đè `Object.equals` theo nội dung**. Vì thế `array1.equals(array2)`, `Objects.equals(array1, array2)` và toán tử `==` đều kiểm tra **cùng tham chiếu**, nếu cả hai đều là những đối tượng mảng khác nhau.

```java
int[] a = {1, 2, 3};
int[] b = {1, 2, 3};

System.out.println(a == b);                 // false
System.out.println(a.equals(b));            // false
System.out.println(Objects.equals(a, b));   // false
System.out.println(Arrays.equals(a, b));    // true
```

Đoạn trên là mã minh họa đặt trong phương thức với `import java.util.*`. Mảng `Integer[]` cũng có **cùng sự khác biệt giữa tham chiếu và nội dung**: hai mảng mới chứa ba giá trị `Integer` giống nhau vẫn là hai **đối tượng mảng** khác nhau.

Muốn so phần tử của **hai mảng một chiều cùng kiểu tương thích**, dùng:

- `Arrays.equals(int[], int[])` đối với mảng `int[]`: từng cặp phần tử `int` so sánh theo giá trị.
- `Arrays.equals(Object[], Object[])` đối với `Integer[]`: từng cặp phần tử được so theo `Objects.equals`; nhờ vậy hai phần tử `Integer` có cùng giá trị được xem là bằng nhau, và hai phần tử `null` cùng vị trí cũng bằng nhau.

**Không có overload `Arrays.equals(int[], Integer[])` để tự boxing từng phần tử.** Lệnh dưới đây không biên dịch:

```java
// Arrays.equals(new int[] {1, 2}, new Integer[] {1, 2});
```

Nếu nghiệp vụ yêu cầu so sánh **giá trị số tương ứng giữa hai kiểu**, cần chuẩn hóa về một dạng trước, ví dụ:

```java
int[] primitive = {1, 2, 3};
Integer[] boxed = {1, 2, 3};
Integer[] converted = Arrays.stream(primitive)
        .boxed()
        .toArray(Integer[]::new);
boolean equivalent = Arrays.equals(converted, boxed); // true
```

Đây là so sánh sau **chuyển đổi tường minh**, không phải hai kiểu mảng vốn bằng nhau theo `equals`. Nếu dữ liệu có null, cần một định nghĩa nghiệp vụ riêng vì `int[]` không biểu diễn được phần tử null.

### Đừng nhầm `Integer.equals` với `==` trên `Integer`

`Integer.equals(Object)` so theo giá trị nguyên, trong khi `==` trên hai biến kiểu `Integer` so cùng tham chiếu. Cơ chế cache đối tượng `Integer` cho một số giá trị có thể khiến `==` có vẻ đúng trong các trường hợp nhất định; không dựa vào đó để kiểm tra giá trị. Với phần tử lớp bao, `Arrays.equals(Integer[], Integer[])` phù hợp vì nó dùng logic bình đẳng của phần tử thay cho so tham chiếu trực tiếp.

## 4. Mảng đa chiều và `Arrays.deepEquals()`

Một mảng hai chiều Java thực chất là **mảng của các mảng**. Cụ thể:

- `int[][]` có kiểu phần tử trực tiếp là `int[]`. **Mảng ngoài** là mảng tham chiếu nên có thể dùng như `Object[]`; **mảng trong** `int[]` vẫn chứa nguyên thủy và không phải `Object[]`.
- `Integer[][]` có phần tử trực tiếp là `Integer[]`; cả mảng ngoài và từng mảng trong đều là mảng tham chiếu.
- `Arrays.equals(int[][], int[][])` dùng overload đối tượng `Object[]`, rồi so các phần tử là `int[]` theo `equals` của mảng (tham chiếu). Nếu các hàng là hai mảng mới có cùng nội dung, kết quả có thể là `false`.
- `Arrays.deepEquals(int[][], int[][])` kiểm tra sâu các mảng con tương ứng, bao gồm so nội dung nguyên thủy bằng overload phù hợp. Nó là lựa chọn đúng khi cần so **cấu trúc lồng** cùng biểu diễn.

Ví dụ:

```java
int[][] a = {{1, 2}, {3, 4}};
int[][] b = {{1, 2}, {3, 4}};

System.out.println(Arrays.equals(a, b));     // false
System.out.println(Arrays.deepEquals(a, b)); // true
```

`Arrays.deepEquals` nhận hai tham số `Object[]`; một `int[][]` truyền vào được vì mảng ngoài chứa các tham chiếu `int[]`. **Một `int[]` đơn lẻ thì không truyền trực tiếp cho `Arrays.deepEquals` được.**

Điều quan trọng trong bài này là so sánh sâu **không tự boxing** giữa phần tử nguyên thủy và lớp bao:

```java
int[][] primitiveNested = {{1, 2}, {3, 4}};
Integer[][] wrapperNested = {{1, 2}, {3, 4}};
System.out.println(
        Arrays.deepEquals(primitiveNested, wrapperNested)); // false
```

Mặc dù nhìn bằng mắt có cùng các con số, các hàng `int[]` và `Integer[]` có kiểu khác nhau và không thể so bằng nhau chỉ nhờ `deepEquals`. Để định nghĩa *equivalence* xuyên kiểu, bạn phải chuyển đổi dữ liệu hoặc viết bộ so sánh rõ quy tắc cho các kiểu đầu vào.

**Hiển thị dữ liệu:** `Arrays.toString(int[])` và `Arrays.toString(Integer[])` đều in phần tử một chiều; với mảng lồng, `Arrays.deepToString(...)` giúp hiển thị nội dung các mảng con thay vì biểu diễn tham chiếu khó đọc.

## 5. Các hệ quả về null, bộ nhớ, generic và collection

### 5.1. Mảng nguyên thủy không có phần tử null

`new int[3]` chứa ba giá trị mặc định `0`. `new Integer[3]` chứa ba **tham chiếu null**. Phép đọc `int x = boxed[0]` thực hiện unboxing; nếu `boxed[0]` là null thì ném `NullPointerException`. Ngược lại, `Arrays.equals(Integer[], Integer[])` có thể so hai mảng cùng null ở vị trí tương ứng mà không cần unbox chúng.

Khi dữ liệu bên ngoài có khái niệm “thiếu giá trị”, mảng wrapper cho phép biểu diễn null nhưng cũng làm tăng yêu cầu kiểm tra dữ liệu. Không dùng `0` để ngầm biểu thị thiếu giá trị nếu 0 là một giá trị hợp lệ trong miền dữ liệu.

### 5.2. Chi phí bộ nhớ và hiệu năng

Một mảng `int[]` lưu giá trị nguyên thủy trực tiếp, thường sử dụng ít bộ nhớ hơn và có tính cục bộ truy cập tốt hơn so với `Integer[]` chứa tham chiếu. `Integer[]` có thể khiến chương trình tạo thêm đối tượng, tăng chi phí boxing/unboxing và áp lực thu gom rác. Tuy nhiên JVM có thể tối ưu hóa hoặc tái sử dụng một số đối tượng wrapper, vì vậy **không kết luận một con số overhead cố định** hoặc một thứ hạng hiệu năng cho mọi workload. Khi lựa chọn ảnh hưởng đáng kể tới hệ thống, đo benchmark với dữ liệu và JVM thực tế.

### 5.3. Mảng tham chiếu và generic

`Integer[]` dùng được ở nơi nhận `Object[]` vì `Integer` là một kiểu tham chiếu con của `Object`. `int[]` **không** dùng được ở nơi nhận `Object[]` vì phần tử `int` là nguyên thủy. Tuy nhiên cả hai đối tượng mảng đều là `Object`, có thể truyền vào tham số `Object`.

`List<Integer>` nhận các phần tử `Integer`; Java generic không chấp nhận nguyên thủy làm tham số kiểu, ví dụ `List<int>` không hợp lệ. Bạn có thể dùng `Integer[]` hoặc `List<Integer>` khi API đòi kiểu tham chiếu, và dùng `int[]` khi thao tác số lượng lớn với dữ liệu nguyên thủy mà không cần null.

Mảng tham chiếu có tính hiệp biến (*covariant*): `Integer[]` có thể gán vào `Object[]`, nhưng nếu thử ghi một `String` vào đó qua tham chiếu `Object[]`, Java sẽ ném `ArrayStoreException` ở runtime. Tính hiệp biến này **không áp dụng** để biến `int[]` thành `Object[]`.

### 5.4. `Arrays.asList()` là bẫy thường gặp

`Arrays.asList` là phương thức varargs dùng kiểu tham chiếu. Do đó:

```java
int[] raw = {1, 2, 3};
Integer[] boxed = {1, 2, 3};

System.out.println(Arrays.asList(raw).size());   // 1
System.out.println(Arrays.asList(boxed).size()); // 3
```

Với `raw`, toàn bộ đối tượng `int[]` trở thành **một phần tử** trong `List<int[]>`; không hề có tự động boxing ba số. Với `boxed`, mỗi phần tử mảng trở thành một phần tử `List<Integer>`. Danh sách do `Arrays.asList` trả về có độ dài cố định và được hỗ trợ bởi mảng đầu vào; nếu cần danh sách có thể thêm/xóa phần tử, tạo một danh sách phù hợp khác.

Để tạo danh sách các số từ mảng nguyên thủy:

```java
List<Integer> list = Arrays.stream(raw).boxed().toList();
```

Đoạn này dùng Java 17 (API `Stream.toList()` có từ Java 16), cần `import java.util.*`. Kết quả là danh sách không cho phép sửa cấu trúc/nội dung bằng các thao tác mutator của `List`; nếu cần danh sách có thể thay đổi, có thể dùng `new ArrayList<>(list)`.

## 6. Chương trình Java 17+ đầy đủ và kết quả kiểm chứng

Lưu toàn bộ mã dưới đây dưới tên **`PrimitiveWrapperArraysDemo.java`**; ví dụ tập trung vào kiểu runtime, hợp đồng bình đẳng của mảng, so sánh sâu và các khác biệt thường gây lỗi.

```java
import java.util.*;

public class PrimitiveWrapperArraysDemo {
    public static void main(String[] args) {
        int[] raw = {1, 2, 3};
        int[] rawCopy = {1, 2, 3};
        Integer[] boxed = {1, 2, 3};
        Integer[] boxedCopy = {1, 2, 3};

        System.out.println("primitive class = " + raw.getClass().getName());
        System.out.println("wrapper class = " + boxed.getClass().getName());
        System.out.println("primitive is Object = " + (raw instanceof Object));
        System.out.println("primitive is Object[] = "
                + (((Object) raw) instanceof Object[]));
        System.out.println("wrapper is Object[] = " + (boxed instanceof Object[]));

        System.out.println("primitive content equal = "
                + Arrays.equals(raw, rawCopy));
        System.out.println("wrapper content equal = "
                + Arrays.equals(boxed, boxedCopy));
        System.out.println("Objects.equals primitive arrays = "
                + Objects.equals(raw, rawCopy));
        System.out.println("Objects.equals wrapper arrays = "
                + Objects.equals(boxed, boxedCopy));
        System.out.println("mixed deep equality = "
                + Arrays.deepEquals(new Object[] {raw}, new Object[] {boxed}));
        System.out.println("primitive content = " + Arrays.toString(raw));
        System.out.println("wrapper content = " + Arrays.toString(boxed));

        Integer[] converted = Arrays.stream(raw)
                .boxed()
                .toArray(Integer[]::new);
        System.out.println("converted content = "
                + Arrays.toString(converted));
        System.out.println("after conversion equal = "
                + Arrays.equals(converted, boxed));

        Integer[] withNull = {1, null, 3};
        System.out.println("wrapper with null = " + Arrays.toString(withNull));
        System.out.println("wrapper null equal = "
                + Arrays.equals(withNull, new Integer[] {1, null, 3}));
        try {
            int item = withNull[1];
            System.out.println(item);
        } catch (NullPointerException exception) {
            System.out.println("unboxing null = NullPointerException");
        }

        int[][] primitiveNested = {{1, 2}, {3, 4}};
        Integer[][] wrapperNested = {{1, 2}, {3, 4}};
        System.out.println("primitive nested is Object[] = "
                + (((Object) primitiveNested) instanceof Object[]));
        System.out.println("primitive deep equal = "
                + Arrays.deepEquals(
                        primitiveNested, new int[][] {{1, 2}, {3, 4}}));
        System.out.println("wrapper deep equal = "
                + Arrays.deepEquals(
                        wrapperNested, new Integer[][] {{1, 2}, {3, 4}}));
        System.out.println("nested cross-type deep equal = "
                + Arrays.deepEquals(primitiveNested, wrapperNested));

        System.out.println("asList primitive size = "
                + Arrays.asList(raw).size());
        System.out.println("asList wrapper size = "
                + Arrays.asList(boxed).size());
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac PrimitiveWrapperArraysDemo.java
java PrimitiveWrapperArraysDemo
```

Kết quả:

```text
primitive class = [I
wrapper class = [Ljava.lang.Integer;
primitive is Object = true
primitive is Object[] = false
wrapper is Object[] = true
primitive content equal = true
wrapper content equal = true
Objects.equals primitive arrays = false
Objects.equals wrapper arrays = false
mixed deep equality = false
primitive content = [1, 2, 3]
wrapper content = [1, 2, 3]
converted content = [1, 2, 3]
after conversion equal = true
wrapper with null = [1, null, 3]
wrapper null equal = true
unboxing null = NullPointerException
primitive nested is Object[] = true
primitive deep equal = true
wrapper deep equal = true
nested cross-type deep equal = false
asList primitive size = 1
asList wrapper size = 3
```

Nhìn vào phần output:

1. `primitive class` và `wrapper class` chứng minh hai **kiểu mảng runtime** khác nhau, dù cùng ba giá trị số.
2. `raw` là một `Object` nhưng **không** là `Object[]`; `boxed` là `Object[]`. Phép thử với `raw` được ép lên `Object` trước khi dùng `instanceof Object[]` để đây là một biểu thức Java hợp lệ có kết quả `false`.
3. Hai cặp mảng riêng biệt bằng nhau theo **nội dung** với `Arrays.equals`, nhưng khác nhau theo `Objects.equals` vì đó không phải cùng đối tượng.
4. So sánh sâu giữa `new Object[]{raw}` và `new Object[]{boxed}` là `false`; dữ liệu mảng nguyên thủy không được tự động boxing trong thuật toán so sánh sâu.
5. Sau khi chuyển `raw` thành `Integer[]` mới, `Arrays.equals(converted, boxed)` là `true`: chuyển đổi làm hai bên có cùng **kiểu biểu diễn thích hợp** cho phép so nội dung.
6. `Integer[]` chứa `null` hợp lệ và có thể so bằng `Arrays.equals`, nhưng đọc phần tử null thành `int` sẽ ném `NullPointerException`.
7. `int[][]` dùng được như `Object[]` ở **tầng ngoài**. `Arrays.deepEquals` là `true` khi so hai ma trận nguyên thủy tương ứng, hoặc hai ma trận wrapper tương ứng; so chéo hai biểu diễn vẫn `false`.
8. `Arrays.asList(raw)` có kích thước 1; `Arrays.asList(boxed)` có kích thước 3. Đây là khác biệt về phần tử varargs, không phải dữ liệu bị mất.

## 7. Chọn phương thức so sánh đúng trong bài toán thực tế

| Bạn muốn kiểm tra | API nên dùng | Ghi chú |
| --- | --- | --- |
| Hai tham chiếu có trỏ cùng đối tượng mảng? | `a == b` hoặc `Objects.equals(a, b)` | Đối với mảng, `Objects.equals` vẫn so tham chiếu |
| Hai `int[]` cùng dãy số? | `Arrays.equals(a, b)` | So từng giá trị nguyên thủy |
| Hai `Integer[]` cùng dãy giá trị, kể cả null? | `Arrays.equals(a, b)` | Phần tử dùng bình đẳng kiểu đối tượng; null được xử lý |
| Hai `int[][]` hoặc `Integer[][]` có nội dung lồng tương ứng? | `Arrays.deepEquals(a, b)` | Dùng ở cấp mảng ngoài của mảng lồng |
| Một `int[]` và một `Integer[]` thể hiện cùng dãy số? | Chuyển về cùng kiểu rồi `Arrays.equals` | Xác định cách xử lý null trước khi so |
| Hiển thị mảng một chiều | `Arrays.toString(array)` | Tránh `array.toString()` |
| Hiển thị mảng lồng | `Arrays.deepToString(array)` | Hiển thị nội dung của mảng con |
| Tạo `List<Integer>` từ `int[]` | `Arrays.stream(raw).boxed().toList()` | Không dùng `Arrays.asList(raw)` nếu muốn một phần tử list cho mỗi số |

## 8. Lỗi phổ biến và cách tự kiểm chứng

1. **Tin rằng autoboxing áp dụng cho cả mảng:** `Integer[] b = intArray` là sai kiểu. Phải tạo mảng mới và chuyển từng phần tử.
2. **Gọi `Objects.equals` hoặc `array.equals` để so nội dung:** với cả hai loại mảng, nó vẫn so cùng tham chiếu. Dùng `Arrays.equals` cho mảng một chiều.
3. **Dùng `Arrays.equals` cho mảng hai chiều và mong so sâu:** phần tử trực tiếp của mảng ngoài là tham chiếu mảng con; với hai ma trận riêng biệt thường cần `Arrays.deepEquals`.
4. **Mong `Arrays.deepEquals` tự quy đổi `int[]` thành `Integer[]`:** hàm không thực hiện chuyển đổi kiểu số nguyên như vậy. Cần chuẩn hóa rõ ràng.
5. **Bỏ quên null trong `Integer[]`:** unboxing null ném `NullPointerException`; mảng nguyên thủy không có phần tử null.
6. **Nhầm `Arrays.asList(int[])` với danh sách từng số:** nó tạo danh sách một phần tử `int[]`. Dùng stream `boxed()` hoặc chuyển sang wrapper trước.
7. **Dùng `==` trên hai đối tượng `Integer` để so giá trị:** đó là so tham chiếu và có thể phụ thuộc caching. Dùng `Objects.equals`/`Integer.equals` khi cần bình đẳng giá trị.
8. **Dùng giả định mọi phần tử `Integer[]` được tạo riêng:** caching, autoboxing và tối ưu JVM có thể ảnh hưởng identity; đừng xây hợp đồng bằng nhau dựa trên cách tạo instance wrapper.

**Tự kiểm tra:** Xét `int[][] a = {{1, 2}}`, `int[][] b = {{1, 2}}` và `Integer[][] c = {{1, 2}}`. Tại sao `Arrays.equals(a, b)` có thể là `false` nhưng `Arrays.deepEquals(a, b)` là `true`, còn `Arrays.deepEquals(a, c)` lại là `false`? Vì `Arrays.equals` ở mảng ngoài so hai tham chiếu `int[]` là hai đối tượng khác nhau; `deepEquals` đi vào nội dung của hai mảng `int[]` tương ứng; nhưng nó **không tự boxing** một hàng `int[]` thành `Integer[]` khi so xuyên kiểu.

Điểm cốt lõi: `int[]` và `Integer[]` có thể trông giống nhau khi in ra, nhưng **khác kiểu dữ liệu và hợp đồng thao tác**. Chọn mảng nguyên thủy khi cần giá trị không-null và thao tác số trực tiếp; chọn mảng lớp bao khi API cần đối tượng, generic hoặc cần biểu diễn null. Khi so sánh, phân biệt rõ **bình đẳng tham chiếu**, **bình đẳng nội dung một chiều**, **bình đẳng sâu của cấu trúc lồng** và **bình đẳng sau chuyển đổi tường minh**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-006">Chọn so sánh theo thứ tự hay theo tập giá trị trước khi viết mã</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn so sánh theo thứ tự hay theo tập giá trị trước khi viết mã

## 1. Quyết định ý nghĩa của “bằng nhau” trước khi gọi equals

Hai cấu trúc có cùng các giá trị vẫn có thể **bằng nhau hoặc không**, tùy yêu cầu của bài toán. Với mảng, danh sách và cấu trúc lồng nhau, câu hỏi đầu tiên phải là: **vị trí phần tử có mang ý nghĩa hay không, và số lần xuất hiện của mỗi giá trị có mang ý nghĩa hay không?**

Ví dụ, với hai dãy số:

~~~text
A = [2, 1, 2]
B = [1, 2, 2]
C = [1, 1, 2]
~~~

- Nếu so sánh **theo thứ tự**, A khác B vì phần tử ở vị trí đầu tiên khác nhau.
- Nếu so sánh như **tập giá trị (set)**, A, B và C đều đại diện cho tập `{1, 2}`: thứ tự và số lần xuất hiện không quan trọng.
- Nếu so sánh như **đa tập (multiset/bag)**, A bằng B vì cả hai có một số `1` và hai số `2`; A khác C vì C có hai số `1` và một số `2`.
- Nếu cấu trúc là **ánh xạ (map)**, bình đẳng được xác định theo tập các cặp khóa–giá trị, không theo thứ tự duyệt các mục.

Đó là **bốn chính sách bình đẳng khác nhau**. Không nên chọn `Arrays.equals`, `List.equals`, `Set.equals` hay cách chuyển đổi dữ liệu trước khi đã xác định chính sách. Một đoạn code ngắn vẫn có thể đúng cú pháp nhưng sai yêu cầu nghiệp vụ nếu nó chọn nhầm ý nghĩa của “bằng nhau”.

Trong module hợp đồng đối tượng của Java, quyết định này còn chi phối việc cài đặt **cả `equals(Object)` lẫn `hashCode()`**. Nếu hai đối tượng được xem là bằng nhau theo chính sách đã chọn, mã băm của chúng bắt buộc phải giống nhau.

## 2. Ba chính sách thường gặp đối với dãy giá trị

| Chính sách | Thứ tự quan trọng? | Số lần lặp quan trọng? | A so với B | A so với C |
| --- | --- | --- | --- | --- |
| Dãy có thứ tự (sequence) | Có | Có | Khác | Khác |
| Tập giá trị (set) | Không | Không | Bằng | Bằng |
| Đa tập (multiset/bag) | Không | Có | Bằng | Khác |

### 2.1. So sánh theo thứ tự

Dùng khi các vị trí có ý nghĩa: chuỗi bước xử lý, lịch sử sự kiện, tọa độ, thứ tự ưu tiên, danh sách tham số, một hàng dữ liệu có cột cố định, hoặc đường đi theo các nút.

Hai dãy chỉ bằng nhau khi:

1. Có cùng số phần tử.
2. Mỗi cặp phần tử ở **cùng chỉ số** bằng nhau theo chính sách bình đẳng của kiểu phần tử.

Với mảng `int[]`, `Arrays.equals(left, right)` so sánh lần lượt các giá trị nguyên. Với `List<T>`, `List.equals` so sánh phần tử tại từng vị trí bằng `equals` tương ứng. Chỉ thay đổi vị trí hai phần tử khác nhau cũng có thể làm hai dãy không bằng nhau.

### 2.2. So sánh theo tập giá trị

Dùng khi chỉ cần biết **những giá trị nào xuất hiện**, như tập quyền, tập nhãn không lặp, tập mã tính năng bật, hoặc tập ID đã được chọn. Dãy `[2, 1, 2]` và `[1, 2]` biểu thị cùng tập `{1, 2}`, nếu hợp đồng cho phép bỏ qua bản sao trùng lặp.

Một `Set<E>` theo hợp đồng Java không coi thứ tự duyệt là tiêu chí bình đẳng. Hai set bằng nhau khi có cùng các phần tử theo quan hệ `equals` và có cùng kích thước. **Chuyển một dãy sang `Set` là quyết định loại bỏ thông tin về số lần lặp**: đây là thao tác đúng chỉ khi số lượng bản sao không mang ý nghĩa.

Ví dụ, một danh sách người đăng ký có hai dòng giống nhau đôi khi là lỗi cần phát hiện, hoặc là hai lượt đăng ký riêng biệt. Chuyển thẳng nó sang `Set` có thể âm thầm che giấu khác biệt quan trọng.

### 2.3. So sánh theo đa tập

Dùng khi thứ tự không quan trọng nhưng **tần suất xuất hiện** vẫn quan trọng, như giỏ hàng theo số lượng, danh sách nguyên liệu, phân bố nhãn, hoặc kiểm tra hai bộ dữ liệu có cùng phần tử kèm số lần lặp.

Một cách rõ nghĩa là lập bảng tần suất: với mỗi giá trị, đếm số lần xuất hiện, sau đó so sánh các bảng đếm theo `Map.equals`. Đối với `int[]`, ta có thể dùng `Map<Integer, Integer>` và gọi `merge(value, 1, Integer::sum)`.

Việc sắp xếp hai dãy rồi so sánh theo thứ tự cũng có thể kiểm tra bình đẳng đa tập **khi phép sắp xếp và phép so sánh tuân cùng định nghĩa bình đẳng**. Nhưng sắp xếp trực tiếp sẽ thay đổi dãy đầu vào; nếu cần giữ nguyên dữ liệu, phải tạo bản sao trước. Với kiểu tùy biến, comparator có thể coi hai phần tử là tương đương dù `equals` cho `false`; cần xác định rõ chính sách thay vì mặc nhiên xem sắp xếp là đáp án tổng quát.

## 3. Mảng trong Java: tham chiếu, nội dung nông và nội dung lồng

Các phương thức khác nhau trả lời các câu hỏi khác nhau:

| Cách so sánh | Ý nghĩa |
| --- | --- |
| `arrayA == arrayB` | Hai biến có trỏ đến cùng đối tượng mảng không? |
| `arrayA.equals(arrayB)` | Mảng không ghi đè `Object.equals`: kiểm tra cùng đối tượng mảng |
| `Arrays.equals(int[], int[])` | So sánh lần lượt các phần tử `int` theo chỉ số |
| `Arrays.equals(Object[], Object[])` | So sánh từng phần tử bằng `Objects.equals`; mảng con vẫn được xem như đối tượng tham chiếu |
| `Arrays.deepEquals(Object[], Object[])` | So sánh đệ quy nội dung các mảng lồng, kể cả phần tử là mảng primitive |
| `List.equals` | So sánh danh sách theo thứ tự, gọi bình đẳng của từng phần tử |
| `Set.equals` | So sánh theo tập phần tử; không dùng vị trí duyệt |

Ví dụ, hai biến `int[]` khác nhau chứa cùng `[1, 2]` không bằng nhau bằng `==`, nhưng bằng nhau với `Arrays.equals`. Với `int[][]`, các hàng là những đối tượng `int[]` riêng biệt, nên `Arrays.equals(matrixA, matrixB)` thường cho `false` khi các hàng được tạo riêng; `Arrays.deepEquals(matrixA, matrixB)` mới so nội dung từng hàng.

Với cấu trúc mảng lồng, dùng cặp hàm băm phù hợp: `Arrays.hashCode` cho mảng một chiều so theo thứ tự, `Arrays.deepHashCode` cho mảng lồng được so bằng `Arrays.deepEquals`. Không được dùng `Object.hashCode()` mặc định của mảng cho một lớp đã định nghĩa bình đẳng theo nội dung, vì hai mảng khác tham chiếu nhưng cùng nội dung có thể nhận hash khác nhau.

**Lưu ý:** `Arrays.deepEquals` giải quyết cấu trúc **mảng**, nhưng không tự đổi `List` thành tập không thứ tự, cũng không tự đi sâu vào mọi loại đối tượng tùy biến. Khi phần tử là `List` hoặc `Set`, nó sử dụng cách bình đẳng mà các kiểu phần tử đó định nghĩa. Những cấu trúc mảng tự tham chiếu theo chu trình còn cần thiết kế đặc biệt; đừng coi `deepEquals` là bộ so sánh đồ thị tổng quát có xử lý chu trình.

## 4. Ví dụ Java 17 chạy được: một dữ liệu, nhiều kết quả bình đẳng

Chương trình dưới đây minh họa đồng thời so sánh theo thứ tự, theo tập, theo đa tập và theo cấu trúc mảng lồng. Nó cũng đưa vào `HashSet` hai đối tượng có mảng cùng nội dung để kiểm tra `equals`/`hashCode` của lớp chứa mảng.

Lưu trong tệp **ComparisonPolicyDemo.java**:

~~~java
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComparisonPolicyDemo {
    static Set<Integer> valuesAsSet(int[] numbers) {
        Set<Integer> result = new HashSet<>();
        for (int number : numbers) {
            result.add(number);
        }
        return result;
    }

    static Map<Integer, Integer> frequencies(int[] numbers) {
        Map<Integer, Integer> count = new HashMap<>();
        for (int number : numbers) {
            count.merge(number, 1, Integer::sum);
        }
        return count;
    }

    static final class OrderedSequence {
        private final int[] values;

        OrderedSequence(int... values) {
            this.values = values.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof OrderedSequence that
                            && Arrays.equals(values, that.values));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    static final class ValueSet {
        private final Set<Integer> values;

        ValueSet(int... numbers) {
            this.values = Set.copyOf(valuesAsSet(numbers));
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof ValueSet that
                            && values.equals(that.values));
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }
    }

    public static void main(String[] args) {
        int[] a = {2, 1, 2};
        int[] b = {1, 2, 2};
        int[] c = {1, 1, 2};
        int[] sameAsA = {2, 1, 2};

        System.out.println("same array reference: " + (a == sameAsA));
        System.out.println("ordered A/B: " + Arrays.equals(a, b));
        System.out.println("ordered A/copy: "
                + Arrays.equals(a, sameAsA));
        System.out.println("set A/B: "
                + valuesAsSet(a).equals(valuesAsSet(b)));
        System.out.println("set A/C: "
                + valuesAsSet(a).equals(valuesAsSet(c)));
        System.out.println("multiset A/B: "
                + frequencies(a).equals(frequencies(b)));
        System.out.println("multiset A/C: "
                + frequencies(a).equals(frequencies(c)));

        Set<OrderedSequence> orderedKeys = new HashSet<>();
        orderedKeys.add(new OrderedSequence(a));
        System.out.println("ordered HashSet contains copy: "
                + orderedKeys.contains(new OrderedSequence(sameAsA)));
        System.out.println("ordered HashSet contains B: "
                + orderedKeys.contains(new OrderedSequence(b)));

        Set<ValueSet> setKeys = new HashSet<>();
        setKeys.add(new ValueSet(a));
        System.out.println("set-key HashSet contains B: "
                + setKeys.contains(new ValueSet(b)));
        System.out.println("set-key HashSet contains C: "
                + setKeys.contains(new ValueSet(c)));

        int[][] matrixA = {{1, 2}, {3, 4}};
        int[][] matrixB = {{1, 2}, {3, 4}};
        System.out.println("matrix shallow equality: "
                + Arrays.equals(matrixA, matrixB));
        System.out.println("matrix deep equality: "
                + Arrays.deepEquals(matrixA, matrixB));
        System.out.println("matrix deep hashes agree: "
                + (Arrays.deepHashCode(matrixA)
                        == Arrays.deepHashCode(matrixB)));

        List<Set<Integer>> nestedA =
                List.of(Set.of(1, 2), Set.of(3));
        List<Set<Integer>> nestedB =
                List.of(Set.of(2, 1), Set.of(3));
        List<Set<Integer>> nestedReordered =
                List.of(Set.of(3), Set.of(1, 2));
        System.out.println("nested same structure: "
                + nestedA.equals(nestedB));
        System.out.println("nested outer order: "
                + nestedA.equals(nestedReordered));
    }
}
~~~

Dùng JDK 17 trở lên:

~~~bash
javac ComparisonPolicyDemo.java
java ComparisonPolicyDemo
~~~

Kết quả dự kiến chính xác:

~~~text
same array reference: false
ordered A/B: false
ordered A/copy: true
set A/B: true
set A/C: true
multiset A/B: true
multiset A/C: false
ordered HashSet contains copy: true
ordered HashSet contains B: false
set-key HashSet contains B: true
set-key HashSet contains C: true
matrix shallow equality: false
matrix deep equality: true
matrix deep hashes agree: true
nested same structure: true
nested outer order: false
~~~

Giải thích:

- Hai mảng `a` và `sameAsA` là hai đối tượng khác nhau nhưng các phần tử tại từng vị trí giống nhau, nên `==` trả `false` còn `Arrays.equals` trả `true`.
- `a` và `b` không cùng thứ tự; tuy nhiên, cả hai chứa cùng tập `{1, 2}` và cùng số lần xuất hiện của mỗi giá trị. Vì vậy tập và đa tập đều xem chúng là bằng nhau.
- `c` vẫn có tập giá trị `{1, 2}`, nhưng tần suất khác `a`; phép so sánh đa tập phải trả `false`.
- `OrderedSequence` sao chép mảng trong constructor, so nội dung bằng `Arrays.equals`, đồng thời dùng `Arrays.hashCode`. Hai khóa cùng nội dung được `HashSet` nhận diện là cùng giá trị; dãy khác thứ tự thì khác.
- `ValueSet` chuyển đầu vào thành set bất biến, nên bỏ qua thứ tự và số lần lặp. `equals` và `hashCode` đều dựa trên cùng đối tượng `Set`, giữ đúng hợp đồng bình đẳng.
- Hai ma trận có các hàng `int[]` được tạo riêng: so sánh nông không coi các hàng là cùng tham chiếu; so sánh sâu thấy cùng nội dung và có cùng deep hash.
- Cấu trúc `List<Set<Integer>>` dùng **quy tắc kết hợp**: danh sách ngoài quan tâm vị trí, còn mỗi tập bên trong không quan tâm thứ tự. Do đó hoán đổi thứ tự khai báo phần tử trong set vẫn bằng nhau, nhưng đảo vị trí hai set ở danh sách ngoài thì khác.

Cặp `equals`/`hashCode` của hai lớp wrapper minh họa chính sách bình đẳng được mã hóa ở **cấp kiểu dữ liệu**. Hai wrapper có cùng tập đầu vào có thể bằng nhau theo `ValueSet` dù không bằng nhau theo `OrderedSequence`; đây là quyết định thiết kế có chủ đích, không phải mâu thuẫn.

## 5. Cấu trúc lồng nhau: áp dụng chính sách theo từng tầng

Không có quy tắc rằng tất cả các tầng phải cùng quan tâm thứ tự. Hãy xác định ý nghĩa của **mỗi nút** trong cấu trúc dữ liệu:

| Cấu trúc | Quy tắc ở tầng ngoài | Quy tắc ở tầng trong |
| --- | --- | --- |
| `List<List<String>>` | Danh sách ngoài có thứ tự | Mỗi danh sách trong có thứ tự |
| `List<Set<String>>` | Danh sách ngoài có thứ tự | Tập trong không thứ tự, không lặp |
| `Set<List<String>>` | Tập ngoài không thứ tự | Mỗi danh sách trong có thứ tự |
| `Map<String, List<Integer>>` | Các cặp khóa–giá trị không theo thứ tự duyệt map | Danh sách ở từng khóa có thứ tự |
| `int[][]` với `Arrays.deepEquals` | Các hàng theo chỉ số | Phần tử trong mỗi hàng theo chỉ số |

Ví dụ, một `List<Set<String>>` có thể biểu diễn “các giai đoạn có thứ tự, trong mỗi giai đoạn là một nhóm quyền không thứ tự”. Hoán đổi hai quyền trong cùng giai đoạn không đổi ý nghĩa; đảo vị trí hai giai đoạn có thể đổi ý nghĩa. Còn `Set<List<String>>` có thể biểu diễn “tập các đường đi”, nơi thứ tự bước trong một đường đi phải giữ nguyên, nhưng thứ tự liệt kê các đường đi là tùy ý.

Nếu nghiệp vụ yêu cầu một đa tập **ở tầng bên trong**, `Set` không còn phù hợp vì nó loại bỏ bản sao; có thể dùng `Map<Element, Integer>` biểu diễn tần suất cho từng nhóm. Tương tự, nếu yêu cầu “ma trận có cùng các hàng theo bất kỳ thứ tự nào nhưng giữ thứ tự các cột bên trong từng hàng”, ta phải thiết kế chính sách cho tập/đa tập của **các hàng** trước. Không thể chỉ gọi `Arrays.deepEquals` là đủ, vì hàm này vẫn so từng hàng theo vị trí.

Cẩn thận khi dùng các mảng làm phần tử của `Set` hoặc khóa của `Map`: mảng Java mặc định so bình đẳng theo tham chiếu. Chẳng hạn, hai mảng `int[]` độc lập với cùng `[1, 2]` vẫn được coi là hai phần tử **khác nhau** trong `HashSet<int[]>`. Nếu cần tập của các hàng theo nội dung, hãy dùng kiểu wrapper có `equals`/`hashCode` theo `Arrays.equals`, hoặc chuyển mỗi hàng thành kiểu giá trị phù hợp.

## 6. Giữ đúng hợp đồng equals/hashCode với chính sách đã chọn

### 6.1. Bình đẳng có thứ tự

Với lớp chứa `int[] values` và bình đẳng theo dãy, cách phối hợp nhất quán là:

~~~java
@Override
public boolean equals(Object other) {
    return this == other
            || (other instanceof OrderedSequence that
                    && java.util.Arrays.equals(values, that.values));
}

@Override
public int hashCode() {
    return java.util.Arrays.hashCode(values);
}
~~~

Đây là **đoạn phương thức minh họa trong lớp OrderedSequence đã khai báo ở chương trình chính**, không phải tệp Java độc lập. Thay đổi thứ tự phần tử có thể làm thay đổi cả bình đẳng và mã băm. Một số mảng khác nhau vẫn có thể trùng mã băm; điều đó được phép.

### 6.2. Bình đẳng theo tập

Với lớp chứa `Set<Integer> values`:

~~~java
@Override
public boolean equals(Object other) {
    return this == other
            || (other instanceof ValueSet that
                    && values.equals(that.values));
}

@Override
public int hashCode() {
    return values.hashCode();
}
~~~

Đây là **đoạn minh họa trong ValueSet**, không phải chương trình độc lập. Hợp đồng `Set.hashCode` dựa trên các phần tử nên không phụ thuộc thứ tự duyệt. Bởi vậy hai tập bằng nhau phải có hash bằng nhau dù thứ tự duyệt khác.

### 6.3. Bình đẳng theo đa tập

Nếu lớp lưu `Map<T, Integer>` để đếm số lần mỗi phần tử xuất hiện, thì `equals` có thể so hai bảng đếm và `hashCode` dùng `Map.hashCode` trên cùng bảng đếm. Phải bảo đảm số đếm phản ánh đúng dữ liệu được phép, được cập nhật nhất quán và đủ lớn cho số lần xuất hiện thực tế.

Không nên dùng `Arrays.hashCode` theo thứ tự nếu `equals` của lớp lại xem `[2,1,2]` và `[1,2,2]` bằng nhau theo đa tập: hai dãy bằng nhau về nghiệp vụ nhưng rất có thể khác hash, **vi phạm hợp đồng**. Ngược lại, băm theo tập giá trị trong khi `equals` theo thứ tự có thể tạo nhiều va chạm nhưng chưa chắc vi phạm hợp đồng; đó là lựa chọn phân bố kém và khó thể hiện mục đích thiết kế.

## 7. Bảo vệ dữ liệu trước thay đổi trạng thái

Nếu một lớp xác định bình đẳng theo nội dung mảng, việc giữ tham chiếu trực tiếp từ constructor cho phép mã bên ngoài sửa mảng và thay đổi kết quả `equals`/`hashCode` sau khi đối tượng được đưa vào `HashMap` hoặc `HashSet`. Hậu quả là tra cứu có thể thất bại ngay cả khi phần tử vẫn còn trong collection.

Trong ví dụ, `OrderedSequence` gọi `values.clone()` để lấy bản sao của `int[]`. Vì trường là `private final` và không công khai mảng bên trong, nội dung mảng dùng làm khóa không bị thay đổi bởi việc sửa đầu vào sau khi tạo wrapper.

Với mảng hai chiều, **`matrix.clone()` chỉ sao chép mảng ngoài, không tự sao chép các hàng**. Nếu mỗi hàng `int[]` có thể bị sửa, phải sao chép riêng từng hàng hoặc dùng một kiểu bất biến khác. `List.copyOf` hay `Set.copyOf` cũng chỉ đóng băng cấu trúc tập hợp bên ngoài; các phần tử bên trong có thể tiếp tục thay đổi nếu bản thân chúng mutable.

Đối với `List` dùng làm khóa, hãy cân nhắc bản sao bất biến và bảo đảm tất cả phần tử tham gia bình đẳng có trạng thái ổn định. Với tập `Set`, bảo đảm không thay đổi những thuộc tính của phần tử ảnh hưởng tới `equals`/`hashCode`, vì bản thân collection băm cũng phụ thuộc vào chúng.

## 8. Những sai lầm điển hình

### Sai lầm 1: chuyển sang Set chỉ để tránh quan tâm thứ tự

Nếu hai đơn hàng có dòng sản phẩm trùng lặp, chuyển danh sách dòng thành `Set` có thể loại mất ý nghĩa của **số lần xuất hiện**. Khi thứ tự không quan trọng nhưng số lượng quan trọng, cần so đa tập hoặc gom theo khóa cùng tổng số lượng.

### Sai lầm 2: dùng Arrays.equals trên mảng nhiều chiều rồi nghĩ là đã so sâu

`Arrays.equals(int[][], int[][])` so các hàng như các đối tượng `int[]`, thường dẫn tới `false` khi hàng được cấp phát riêng. Nếu mục đích là từng phần tử số ở đúng tọa độ, dùng `Arrays.deepEquals` và `Arrays.deepHashCode` theo cùng chính sách.

### Sai lầm 3: so hai List bằng cách sắp xếp trực tiếp

Sắp xếp tại chỗ làm thay đổi danh sách đầu vào và có thể phá thứ tự nghiệp vụ. Với danh sách không hỗ trợ sửa, thao tác có thể ném `UnsupportedOperationException`. Hãy chọn đúng kiểu dữ liệu ngay từ đầu, hoặc sắp xếp **bản sao** theo tiêu chí phù hợp nếu chính sách thực sự cần so đa tập.

### Sai lầm 4: dùng Set chứa mảng để đại diện cho tập hàng theo nội dung

`HashSet<int[]>` dùng `equals` và `hashCode` mặc định của mảng, tức là theo danh tính đối tượng. Nó không biến hai mảng có cùng nội dung thành phần tử bằng nhau. Cần wrapper, chuyển đổi cấu trúc, hoặc khóa giá trị có hợp đồng phù hợp.

### Sai lầm 5: triển khai equals và hashCode theo hai chính sách khác nhau

Nếu `equals` không quan tâm thứ tự nhưng `hashCode` lại dùng một cách băm nhạy thứ tự, hai đối tượng bằng nhau có thể nhận mã băm khác nhau. Điều này làm hỏng giả định của `HashMap`/`HashSet` và vi phạm hợp đồng.

### Sai lầm 6: giả định mọi Collection đều so nội dung theo cùng quy tắc

`List` và `Set` có hợp đồng bình đẳng khác nhau. Hai collection cùng hiển thị các giá trị tương tự chưa đủ để khẳng định bằng nhau. Với cấu trúc lồng, phải đọc đúng **kiểu dữ liệu và hợp đồng ở từng tầng**.

## 9. Quy trình chọn chính sách trước khi viết mã

Khi gặp đề bài “so sánh hai mảng, hai danh sách hoặc hai cấu trúc lồng”, hãy xác định:

1. **Đơn vị so sánh:** phần tử nguyên thủy, đối tượng tùy biến, hàng của ma trận hay nhóm lồng nhau?
2. **Thứ tự:** đảo vị trí hai phần tử có làm đổi ý nghĩa không? Nếu có, chọn so sánh theo dãy.
3. **Bản sao trùng lặp:** `[A,A,B]` có bằng `[A,B]` không? Nếu có, có thể chọn tập; nếu không mà thứ tự vẫn không quan trọng, chọn đa tập.
4. **Mỗi tầng lồng:** một danh sách các nhóm có thể cần danh sách ngoài theo thứ tự nhưng nhóm bên trong là tập. Viết rõ chính sách cho từng tầng trước.
5. **Đầu vào `null` và phần tử `null`:** quy định `null` được phép ở đâu, cách hiểu hai giá trị thiếu, và chọn API đáp ứng hợp đồng. Ví dụ, `Set.copyOf` không chấp nhận phần tử `null`.
6. **Khả năng thay đổi:** các trường quyết định bình đẳng có thể đổi khi đối tượng đang là khóa trong collection băm không? Nếu có, thiết kế bản sao/bất biến hoặc quy trình cập nhật phù hợp.
7. **Hợp đồng `hashCode`:** nếu các đối tượng bằng nhau theo chính sách chọn, phương thức băm có luôn cho cùng kết quả không?
8. **Ví dụ phản chứng:** thử cặp chỉ khác thứ tự và cặp chỉ khác số lần lặp. Hai ví dụ nhỏ này thường phơi bày ngay một quy tắc bình đẳng sai.

**Ghi nhớ:** “Cùng phần tử” là phát biểu chưa đủ để cài đặt `equals`. Hãy quyết định rõ **thứ tự có quan trọng không**, **số lần xuất hiện có quan trọng không**, và **mỗi tầng lồng nhau mang ý nghĩa gì**. Sau đó mới chọn `Arrays.equals`, `Arrays.deepEquals`, `List.equals`, `Set.equals` hoặc bảng tần suất, đồng thời thiết kế `hashCode` nhất quán với chính sách đã chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-007">Trường mảng trong lớp tự viết và yêu cầu đồng bộ equals() với hashCode()</a>

<details>
<summary>Xem chi tiết</summary>

# Trường mảng trong lớp tự viết: đồng bộ `equals()` với `hashCode()`

Khi một lớp Java tự viết có trường là **mảng** (`int[]`, `String[]`, `Object[]`, `int[][]`…), quyết định “hai đối tượng có bằng nhau không” cần đi kèm **cách tính mã băm tương ứng**. Mảng là đối tượng đặc biệt trong Java: nó không ghi đè `Object.equals()` và `Object.hashCode()` theo nội dung phần tử. Vì thế, dù hai mảng chứa chính xác các giá trị giống nhau, `array1.equals(array2)` hoặc `Objects.equals(array1, array2)` vẫn có thể trả `false` nếu đó là hai instance khác nhau.

Nếu lớp muốn định nghĩa bằng nhau **theo nội dung mảng**, hãy dùng đúng cặp API:

- Mảng một chiều: **`Arrays.equals(...)` đi cùng `Arrays.hashCode(...)`**.
- Mảng lồng nhau cần so sánh sâu: **`Arrays.deepEquals(...)` đi cùng `Arrays.deepHashCode(...)`**.
- Các trường khác tham gia `equals` cũng phải được phản ánh nhất quán trong `hashCode`.

Việc dùng khác quy tắc giữa hai phương thức có thể làm `HashMap` và `HashSet` xử lý đối tượng trái với dự kiến.

## 1. Bắt đầu từ hợp đồng `equals`/`hashCode`

Với một lớp tự viết, hợp đồng quan trọng nhất là:

> Nếu `a.equals(b)` trả `true`, **bắt buộc** `a.hashCode() == b.hashCode()`.

Chiều ngược lại **không bắt buộc**: các đối tượng không bằng nhau vẫn có thể có mã băm trùng nhau.

Ngoài ra, `equals` cần bảo đảm phản xạ, đối xứng, bắc cầu, tính nhất quán khi dữ liệu dùng để so sánh không thay đổi và `x.equals(null)` trả `false`. Khi ghi đè `equals`, hãy ghi đè `hashCode` để các cấu trúc băm có thể tìm đúng các đối tượng bằng nhau.

Một ví dụ dễ sai (chỉ là các đoạn minh họa trong lớp, **không phải chương trình độc lập**):

```java
// Sai nếu muốn so sánh nội dung mảng:
return Objects.equals(scores, other.scores);

// Sai nếu equals đã dùng Arrays.equals(scores, other.scores):
@Override
public int hashCode() {
    return scores.hashCode();
}
```

`Objects.equals` chỉ hỗ trợ xử lý `null` và gọi `equals` của đối số; đối với mảng, `equals` kế thừa từ `Object` kiểm tra **cùng tham chiếu**. Tương tự, `scores.hashCode()` không băm từng phần tử. Nếu hai đối tượng chứa **hai mảng khác nhau nhưng cùng nội dung** được coi là bằng nhau, chúng có thể nhận hash khác nhau, vi phạm hợp đồng.

Lưu ý: nếu một lớp **cố ý** xem mảng là thành phần có ngữ nghĩa danh tính tham chiếu, việc dùng `==`/`Objects.equals` và hash tương ứng có thể hợp lệ. Nhưng đây là **quyết định về ngữ nghĩa**, phải được chọn nhất quán, không phải mặc định đúng cho mọi value object.

## 2. Mảng một chiều: `Arrays.equals` và `Arrays.hashCode`

Dùng `import java.util.Arrays;`.

Các overload phổ biến của `Arrays.equals` gồm mảng các kiểu nguyên thủy như `int[]`, `long[]`, `byte[]`, `char[]`, `boolean[]`, và mảng tham chiếu như `String[]`, `Object[]`.

```java
// Biểu thức độc lập để minh họa, đặt trong main hoặc một method:
int[] x = {1, 2, 3};
int[] y = {1, 2, 3};

// x == y                 -> false (hai mảng khác instance)
// Objects.equals(x, y)   -> false (mảng không ghi đè equals theo giá trị)
// Arrays.equals(x, y)    -> true  (so sánh phần tử cùng vị trí)
// Arrays.hashCode(x) == Arrays.hashCode(y) -> true
```

Với `int[]`, các phần tử được so sánh theo giá trị nguyên thủy. Với `String[]`, mỗi cặp phần tử được so sánh theo `Objects.equals` nên hai chuỗi khác instance nhưng cùng nội dung được coi là bằng nhau. Với `Object[]`, cách so sánh nội dung từng phần tử **phụ thuộc `equals` của lớp phần tử**.

Hai mảng một chiều chỉ bằng nhau khi cùng độ dài và từng vị trí tương ứng bằng nhau. Vì thế thứ tự là quan trọng:

```text
[1, 2, 3]  và  [1, 2, 3]  -> bằng nhau
[1, 2, 3]  và  [3, 2, 1]  -> không bằng nhau
[1, 2, 3]  và  [1, 2]     -> không bằng nhau
```

`Arrays.hashCode` băm theo cùng thứ tự phần tử, bảo đảm hai mảng được `Arrays.equals` coi là bằng nhau sẽ có cùng hash. Hash trùng không chứng minh hai mảng bằng nhau vì va chạm vẫn có thể xảy ra.

Các overload này cũng xử lý `null`: hai mảng `null` được `Arrays.equals` coi là bằng nhau; một mảng `null`, một mảng không `null` thì không bằng nhau; `Arrays.hashCode` trả `0` với mảng `null`. Do đó có thể dùng trực tiếp trên trường mảng nullable khi đúng overload.

## 3. Mảng lồng nhau: khi nào cần `deepEquals` và `deepHashCode`?

Trong Java, `int[][]` là **mảng chứa các tham chiếu đến `int[]`**, không phải một vùng “ma trận” đơn khối với cơ chế bằng nhau riêng. Nếu gọi `Arrays.equals(int[][], int[][])`, phương thức dùng overload nhận `Object[]` và so sánh **mỗi phần tử hàng**. Mỗi hàng `int[]` lại dùng `Object.equals` mặc định, nên hai hàng khác instance dù cùng số vẫn không bằng nhau.

Đối với dữ liệu lồng nhau cần so sánh toàn bộ nội dung, dùng:

```java
// Hai ma trận khác instance, kể cả các mảng hàng:
int[][] first = {{1, 2}, {3, 4}};
int[][] second = {{1, 2}, {3, 4}};

// Arrays.equals(first, second)       -> false
// Arrays.deepEquals(first, second)   -> true
// Arrays.deepHashCode(first) == Arrays.deepHashCode(second) -> true
```

`Arrays.deepEquals(Object[] a1, Object[] a2)` nhận các mảng tham chiếu bên ngoài. Nó đi sâu qua các phần tử có kiểu mảng, kể cả các mảng kiểu nguyên thủy được lồng bên trong, và dùng phép so sánh phù hợp ở từng cấp. `Arrays.deepHashCode(Object[] a)` tính mã băm tương ứng. Cặp này phù hợp với các cấu trúc như `int[][]`, `String[][]` hoặc `Object[]` chứa những mảng con.

**Giới hạn cần hiểu:** `Arrays.deepEquals` không phải cơ chế tự động duyệt mọi trường của **một object bất kỳ**. Nếu phần tử là một đối tượng Java thông thường, ngữ nghĩa so sánh của nó vẫn do `equals` của lớp đó quyết định. Các cấu trúc mảng có tham chiếu vòng về chính chúng cũng không phù hợp để so sánh sâu theo cách ngây thơ: có thể dẫn tới đệ quy không kết thúc hoặc lỗi tràn stack.

`Arrays.deepHashCode` và `Arrays.deepEquals` tạo ra quy tắc phù hợp nhau; đừng dùng `deepEquals` trong `equals` nhưng lại gọi `Arrays.hashCode` ở cấp mảng ngoài trong `hashCode`.

## 4. Ví dụ Java 17+ hoàn chỉnh với lớp có ba loại trường mảng

Ta thiết kế lớp `CourseReport` gồm:

- `title`: chuỗi có thể `null`, so sánh bằng `Objects.equals`.
- `scores`: `int[]`, so sánh và băm theo nội dung một chiều.
- `tags`: `String[]`, so sánh và băm từng phần tử theo giá trị chuỗi.
- `matrix`: `int[][]`, so sánh và băm sâu theo nội dung các hàng.

Mọi trường này đều tham gia tiêu chí bằng nhau của `CourseReport`. Lớp `final`, giữ các trường `private final` và **sao chép đầu vào** để tránh bên ngoài sửa mảng sau khi tạo đối tượng.

Lưu thành tệp **`ArrayFieldContractDemo.java`**:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class CourseReport {
    private final String title;
    private final int[] scores;
    private final String[] tags;
    private final int[][] matrix;

    CourseReport(String title, int[] scores, String[] tags, int[][] matrix) {
        this.title = title;
        this.scores = Objects.requireNonNull(scores, "scores").clone();
        this.tags = Objects.requireNonNull(tags, "tags").clone();
        this.matrix = copyMatrix(matrix);
    }

    private static int[][] copyMatrix(int[][] source) {
        Objects.requireNonNull(source, "matrix");
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Objects.requireNonNull(source[i], "matrix row").clone();
        }
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CourseReport other)) {
            return false;
        }
        return Objects.equals(title, other.title)
                && Arrays.equals(scores, other.scores)
                && Arrays.equals(tags, other.tags)
                && Arrays.deepEquals(matrix, other.matrix);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(title);
        result = 31 * result + Arrays.hashCode(scores);
        result = 31 * result + Arrays.hashCode(tags);
        result = 31 * result + Arrays.deepHashCode(matrix);
        return result;
    }
}

public class ArrayFieldContractDemo {
    public static void main(String[] args) {
        int[] scores = {9, 10};
        String[] tags = {"java", "core"};
        int[][] matrix = {{1, 2}, {3, 4}};

        CourseReport a = new CourseReport("lesson", scores, tags, matrix);
        CourseReport b = new CourseReport(
                "lesson",
                new int[]{9, 10},
                new String[]{"java", "core"},
                new int[][]{{1, 2}, {3, 4}}
        );

        System.out.println("a equals b: " + a.equals(b));
        System.out.println("Equal hash: " + (a.hashCode() == b.hashCode()));

        scores[0] = 0;
        tags[0] = "changed";
        matrix[0][0] = 99;

        System.out.println("Equal after changing inputs: " + a.equals(b));
        System.out.println("Hash still equal: "
                + (a.hashCode() == b.hashCode()));

        CourseReport different = new CourseReport(
                "lesson",
                new int[]{9, 11},
                new String[]{"java", "core"},
                new int[][]{{1, 2}, {3, 4}}
        );
        System.out.println("Different scores: " + a.equals(different));

        Set<CourseReport> reports = new HashSet<>();
        reports.add(a);
        reports.add(b);
        reports.add(different);
        System.out.println("HashSet size: " + reports.size());

        int[][] nested1 = {{1, 2}};
        int[][] nested2 = {{1, 2}};
        System.out.println("Nested shallow equals: "
                + Arrays.equals(nested1, nested2));
        System.out.println("Nested deep equals: "
                + Arrays.deepEquals(nested1, nested2));
        System.out.println("Nested deep hashes equal: "
                + (Arrays.deepHashCode(nested1)
                        == Arrays.deepHashCode(nested2)));

        System.out.println("Array Objects.equals: "
                + Objects.equals(new int[]{1}, new int[]{1}));
        System.out.println("Array Arrays.equals: "
                + Arrays.equals(new int[]{1}, new int[]{1}));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac ArrayFieldContractDemo.java
java ArrayFieldContractDemo
```

Đầu ra xác định:

```text
a equals b: true
Equal hash: true
Equal after changing inputs: true
Hash still equal: true
Different scores: false
HashSet size: 2
Nested shallow equals: false
Nested deep equals: true
Nested deep hashes equal: true
Array Objects.equals: false
Array Arrays.equals: true
```

### Giải thích đầu ra và thiết kế

1. `a` và `b` có hai bộ mảng **khác tham chiếu** nhưng cùng nội dung. Nhờ `Arrays.equals` và `Arrays.deepEquals`, `a.equals(b)` là `true`.
2. `hashCode()` tính theo cùng các trường và cùng quy tắc so sánh, vì vậy `a.hashCode() == b.hashCode()` chắc chắn đúng.
3. Sau khi sửa `scores`, `tags` và `matrix` gốc, nội dung bên trong `a` **không thay đổi** nhờ clone ở từng cấp cần thiết. Do đó `a` và `b` vẫn bằng nhau và có cùng hash.
4. `different` có phần tử score khác nên không bằng `a`, dù các trường còn lại giống nhau. `HashSet` chứa hai phần tử logic: một tương đương với `a`/`b` và một là `different`.
5. Hai `int[][]` độc lập cho `Arrays.equals` là `false` vì các mảng hàng khác instance; `Arrays.deepEquals` là `true` và mã băm sâu bằng nhau.
6. `Objects.equals` trên hai `int[]` mới tạo là `false`, còn `Arrays.equals` là `true`, minh họa chính xác khác biệt danh tính và nội dung.

Chương trình **không in các mã hash cụ thể** vì đề bài cần xác minh hợp đồng bằng nhau, không cần dựa trên một giá trị hash số học trùng hợp. Hơn nữa, hai đối tượng khác nhau vẫn có thể trùng hash, nên không kiểm tra tính khác nhau bằng điều kiện “hash khác”.

## 5. Vì sao phải sao chép mảng đầu vào?

Một field được khai báo `private final int[] scores` chỉ ngăn **gán lại tham chiếu `scores`**; nó không ngăn `scores[0] = ...`. Nếu constructor giữ trực tiếp mảng do caller truyền, caller có thể sửa mảng sau khi đối tượng đã được dùng làm key trong `HashMap`/`HashSet`. Khi `hashCode` đổi theo phần tử, tra cứu theo khóa có thể thất bại.

Trong ví dụ:

- `int[]`: `clone()` sao chép các giá trị nguyên thủy; đủ để cô lập nội dung.
- `String[]`: `clone()` sao chép cấu trúc mảng, còn các đối tượng `String` vốn bất biến; đủ trong phạm vi ví dụ.
- `int[][]`: `matrix.clone()` **chỉ sao chép mảng ngoài**, còn những hàng `int[]` bên trong vẫn dùng chung tham chiếu. Cần clone từng hàng, như `copyMatrix`.
- Nếu mảng chứa phần tử là đối tượng **mutable**, ví dụ `Address[]`, clone mảng không clone từng `Address`; cần chính sách sao chép phòng vệ thích hợp hoặc giữ các đối tượng phần tử bất biến.

Nguyên tắc áp dụng cả cho **getter**: nếu cung cấp getter trả về trực tiếp mảng nội bộ, bên ngoài vẫn có thể sửa nó. Hãy trả về bản sao hoặc cấu trúc read-only/bất biến phù hợp. “Read-only view” không đồng nghĩa dữ liệu nền tự bất biến nếu nơi khác còn được quyền sửa.

Nếu bài toán thật sự yêu cầu đối tượng có mảng mutable, phải định nghĩa rõ thời điểm được thay đổi, cơ chế đồng bộ hóa khi có đa luồng và ảnh hưởng đến collection. Thông thường, **không dùng các trường mutable tham gia hash của key đang được lưu** là cách đơn giản hơn.

## 6. Các cách ghép hash phổ biến và bẫy `Objects.hash`

Một cách viết thủ công như ví dụ:

```java
int result = Objects.hashCode(title);
result = 31 * result + Arrays.hashCode(scores);
result = 31 * result + Arrays.hashCode(tags);
result = 31 * result + Arrays.deepHashCode(matrix);
return result;
```

Với hai `CourseReport` được `equals` coi là bằng nhau, mỗi thành phần hash tương ứng bằng nhau, nên phép kết hợp theo cùng công thức sẽ cho kết quả bằng nhau. Phép nhân cộng trên `int` có thể tràn theo số học Java; **tràn int không làm hỏng hợp đồng** nếu công thức áp dụng nhất quán.

Cũng có thể kết hợp các hash thành phần bằng:

```java
return Objects.hash(
        title,
        Arrays.hashCode(scores),
        Arrays.hashCode(tags),
        Arrays.deepHashCode(matrix)
);
```

Đây vẫn là mã băm `int` thông thường, có khả năng va chạm, nhưng nhất quán với `equals`.

**Không dùng trực tiếp:**

```java
// Sai cho lớp dùng Arrays.equals/deepEquals trong equals:
return Objects.hash(title, scores, tags, matrix);
```

Lý do: `Objects.hash` không tự chuyển mọi tham chiếu mảng thành băm nội dung/sâu phù hợp; khi mảng xuất hiện như phần tử của danh sách varargs, nó được xử lý theo `hashCode` của chính mảng. Hai lớp có các mảng khác instance nhưng nội dung bằng nhau có thể nhận hash khác, dẫn tới vi phạm hợp đồng của `CourseReport.equals`.

Cũng đừng nhầm `Objects.hash(array)` và `Arrays.hashCode(array)`: cách overload/varargs diễn giải tham số có thể gây ngạc nhiên, đặc biệt với `Object[]`; hãy **chọn rõ API của `Arrays` với đúng mức sâu**.

## 7. Bảng quyết định theo kiểu trường

| Trường trong lớp | Nếu muốn bằng nhau theo nội dung | Hash tương ứng |
| --- | --- | --- |
| `int[]`, `long[]`, `byte[]`, `boolean[]`… | `Arrays.equals(a, b)` | `Arrays.hashCode(a)` |
| `String[]`, `Object[]` không có mảng con cần so sâu | `Arrays.equals(a, b)` | `Arrays.hashCode(a)` |
| `int[][]`, `String[][]`, `Object[]` có mảng con | `Arrays.deepEquals(a, b)` | `Arrays.deepHashCode(a)` |
| `String` hoặc object nullable thông thường | `Objects.equals(a, b)` | `Objects.hashCode(a)` hoặc thành phần tương đương |
| Kiểu nguyên thủy như `int`, `boolean` | `==` | `Integer.hashCode`, `Boolean.hashCode` hoặc phép kết hợp phù hợp |

Với mảng `float[]` hoặc `double[]`, nên dùng đồng bộ `Arrays.equals` và `Arrays.hashCode`; các quy tắc xử lý `NaN` và `-0.0` của các overload này được thiết kế nhất quán. Tránh tự kết hợp so sánh `==` từng phần tử float với hash tính theo `Float.hashCode` nếu chưa xem xét các giá trị đặc biệt.

Nếu trường là collection như `List<String>`, nó có `equals`/`hashCode` theo quy ước collection; **không tự động áp dụng `Arrays.equals`** lên collection. Chọn cách so sánh đúng cho kiểu dữ liệu thực sự.

## 8. Record chứa mảng và trường hợp cần thận trọng

Java `record` tự sinh `equals`/`hashCode` từ các component, nhưng với component kiểu mảng, phép so sánh được sinh theo **ngữ nghĩa tham chiếu của mảng**, không tự chuyển thành `Arrays.equals` hay `Arrays.deepEquals` theo nội dung. Chẳng hạn:

```java
record Sample(int[] values) {}

// Hai Sample sau không tự nhiên bằng nhau theo nội dung values:
Sample x = new Sample(new int[]{1, 2});
Sample y = new Sample(new int[]{1, 2});
// x.equals(y) -> false
```

Đoạn này chỉ minh họa, **không phải chương trình độc lập**. Nếu cần record có mảng và so sánh theo nội dung, phải tự cân nhắc cài đặt lại cả `equals` lẫn `hashCode`, kèm sao chép phòng vệ lúc tạo/trả dữ liệu nếu cần ổn định. Một lựa chọn khác dễ kiểm soát hơn là lưu dữ liệu trong kiểu value bất biến có `equals`/`hashCode` theo nội dung như `List.copyOf(...)` khi phù hợp, nhưng bản thân phần tử của list vẫn cần có ngữ nghĩa bằng nhau đúng.

## 9. Các lỗi thường gặp và cách rà soát

1. **Dùng `Objects.equals(array1, array2)` và nghĩ rằng nó so từng phần tử:** nó thường chỉ so danh tính mảng. Hãy dùng `Arrays.equals` hoặc `Arrays.deepEquals` tùy cấu trúc.
2. **Dùng `Arrays.equals` cho `int[][]` nhưng muốn so toàn bộ số:** overload `Object[]` chỉ so hàng theo `equals` của từng hàng; dùng `Arrays.deepEquals`.
3. **Cài `equals` theo mảng nội dung nhưng `hashCode` theo tham chiếu:** vi phạm yêu cầu hash bằng nhau khi đối tượng bằng nhau.
4. **Dùng `Objects.hash(title, array)` mà tưởng nó băm sâu:** hãy chuyển mảng thành `Arrays.hashCode(array)` hoặc `Arrays.deepHashCode(array)` trước khi kết hợp.
5. **Bỏ quên một trường trong `equals` nhưng đưa nó vào `hashCode`:** hai đối tượng có thể `equals` bằng nhau nhưng hash khác nhau. Ngược lại, không băm đủ trường có thể vẫn đúng hợp đồng nhưng gây nhiều va chạm; nên thống nhất quy tắc.
6. **Chỉ clone mảng ngoài của cấu trúc nhiều chiều:** dữ liệu hàng con vẫn có thể bị sửa. Clone từng cấp cần cô lập.
7. **Cho caller sửa mảng sau khi object được chèn vào `HashSet` hoặc làm key `HashMap`:** hash hoặc quan hệ bằng nhau có thể thay đổi; ưu tiên value object bất biến, key bất biến.
8. **Dựa vào mã hash trùng/khác để kết luận `equals`:** hash bằng nhau không bảo đảm đối tượng bằng nhau; cần so `equals` thật sự.
9. **Giả định một mảng chứa object được clone là bất biến sâu:** các phần tử object mutable vẫn có thể thay đổi qua tham chiếu khác.
10. **Nhầm `Arrays.deepEquals` với bộ so sánh đồ thị đối tượng tùy ý:** nó chủ yếu đi sâu qua mảng lồng, không tự hiểu toàn bộ quan hệ nghiệp vụ hay vòng tham chiếu tùy ý.

### Checklist cho lớp tự viết chứa mảng

- Đã xác định từng mảng so **tham chiếu** hay **nội dung** theo quy tắc nghiệp vụ?
- Mảng một chiều dùng cặp `Arrays.equals`/`Arrays.hashCode`; mảng lồng dùng cặp `Arrays.deepEquals`/`Arrays.deepHashCode`?
- Các trường thường khác được xử lý nhất quán trong cả `equals` và `hashCode`?
- Đã kiểm thử hai instance có **mảng khác tham chiếu nhưng cùng nội dung**, mảng khác một phần tử, mảng khác độ dài, mảng lồng, và nếu được phép thì mảng `null`?
- Constructor/getter có làm lộ mảng mutable tham gia `equals`/`hashCode` không? Mảng đa chiều đã sao chép đủ sâu cho cấu trúc cụ thể chưa?
- Có dùng object làm khóa `HashMap` hoặc phần tử `HashSet` không? Các dữ liệu tham gia hash có ổn định trong suốt thời gian lưu trữ không?

**Ghi nhớ:** khi trường mảng của lớp được xem là **giá trị**, `equals` và `hashCode` phải dùng **cùng mức so sánh/băm nội dung**: `Arrays.equals` đi với `Arrays.hashCode`, `Arrays.deepEquals` đi với `Arrays.deepHashCode`. Bảo vệ nội dung mảng khỏi thay đổi ngoài ý muốn để giữ tính nhất quán trong các collection dựa trên băm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-008">Giới hạn khi cấu trúc có tham chiếu vòng</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn khi cấu trúc có tham chiếu vòng trong Java

## 1. Vấn đề: cấu trúc lồng nhau không nhất thiết là cây

Trong Java, một mảng hoặc đồ thị đối tượng có thể chứa **tham chiếu trở lại chính nó**. Khi chuỗi tham chiếu dẫn về đối tượng đã gặp, ta có **chu trình tham chiếu** (*reference cycle*). Điều này khác với cấu trúc lồng nhau thông thường, nơi mỗi nhánh cuối cùng kết thúc ở giá trị hoặc `null`.

Ví dụ với mảng tham chiếu:

~~~java
Object[] direct = new Object[1];
direct[0] = direct; // Mảng chứa trực tiếp chính nó.

Object[] outer = new Object[1];
Object[] inner = new Object[1];
outer[0] = inner;
inner[0] = outer; // Chu trình gián tiếp: outer -> inner -> outer.
~~~

Đây là **đoạn minh họa**, không phải tệp chạy độc lập. Cả hai trường hợp đều hợp lệ về mặt kiểu dữ liệu: mảng `Object[]` có thể chứa tham chiếu đến các mảng `Object[]` khác, kể cả chính mình.

Một cấu trúc **không có chu trình** cũng có thể chia sẻ phần tử:

~~~java
Object[] shared = {"leaf"};
Object[] acyclic = {shared, shared};
~~~

Hai vị trí trong `acyclic` trỏ đến cùng `shared`. Việc **dùng lại tham chiếu** không tự động tạo chu trình, vì không có đường đi từ `shared` quay về `acyclic`. Phân biệt hai trường hợp này rất quan trọng khi thực hiện so sánh sâu, tính hash, in nội dung và duyệt đồ thị.

## 2. Giới hạn chính thức của các API `Arrays`

Các phương thức trong `java.util.Arrays` có hành vi khác nhau khi gặp mảng lồng nhau hoặc mảng có chu trình:

| API | Với mảng lồng nhau không có chu trình | Với mảng chứa chu trình |
| --- | --- | --- |
| `Arrays.equals(Object[], Object[])` | So sánh các phần tử theo `Objects.equals` ở mức trực tiếp; mảng con mặc định so sánh theo tham chiếu | Không chủ động đi sâu qua mảng con nên không tự đệ quy vì cấu trúc mảng; kết quả không phải so sánh sâu |
| `Arrays.hashCode(Object[])` | Băm các phần tử trực tiếp; mảng con dùng `hashCode()` của đối tượng mảng | Không chủ động băm sâu các mảng con; vẫn phải cẩn trọng nếu phần tử khác có `hashCode()` tự đệ quy |
| `Arrays.toString(Object[])` | In phần tử trực tiếp; mảng con thường hiện dạng tên kiểu + mã băm theo danh tính | Không tạo biểu diễn sâu hay mô tả liên kết vòng đầy đủ |
| `Arrays.deepEquals(Object[], Object[])` | So sánh đệ quy mảng tham chiếu và so sánh nội dung mảng nguyên thủy tương ứng | **Hành vi không được xác định** (*undefined*) nếu một trong hai mảng chứa chính nó trực tiếp hoặc gián tiếp |
| `Arrays.deepHashCode(Object[])` | Tính mã băm từ nội dung lồng nhau | **Hành vi không được xác định** với mảng tự tham chiếu trực tiếp hoặc gián tiếp |
| `Arrays.deepToString(Object[])` | In nội dung mảng lồng nhau | **Có xử lý vòng giữa các mảng**: thay nhánh tham chiếu vòng bằng `[...]` để tránh đệ quy vô hạn trong trường hợp này |

**Điểm quan trọng nhất:** `deepEquals` và `deepHashCode` có phạm vi được đặc tả cho cấu trúc mảng lồng nhau **không có chu trình**. Tên “deep” không có nghĩa các phương thức này là thuật toán tổng quát xử lý mọi đồ thị đối tượng có tham chiếu vòng.

Theo tài liệu API Java, việc gọi `Arrays.deepHashCode` trên một mảng chứa chính nó qua một hoặc nhiều cấp là *unacceptable* và có hành vi không xác định; tài liệu `Arrays.deepEquals` cũng quy định tương tự. Vì thế **không dựa vào kết quả trả về hay một loại ngoại lệ cụ thể** nếu truyền cấu trúc có vòng.

## 3. Vì sao `deepEquals` và `deepHashCode` gặp giới hạn?

Với dữ liệu **không có vòng**, định nghĩa sâu có thể triển khai bằng việc đi theo các mảng con cho tới phần tử lá:

- `deepEquals(a, b)` so sánh các vị trí tương ứng; nếu cùng là mảng tham chiếu, nó tiếp tục so sánh sâu; nếu là hai mảng nguyên thủy cùng loại, nó dùng cách so sánh tương ứng như `Arrays.equals(int[], int[])`.
- `deepHashCode(a)` tính toán mã băm dựa trên từng phần tử; khi gặp mảng tham chiếu bên trong, nó tiếp tục băm sâu.
- Với mảng không có vòng, `Arrays.deepEquals(a, b) == true` kéo theo `Arrays.deepHashCode(a) == Arrays.deepHashCode(b)`.

Một quá trình duyệt đệ quy không theo dõi các đối tượng đã ghé thăm có thể đi theo đường:

~~~text
A -> B -> C -> A -> B -> C -> ...
~~~

Đường đi không kết thúc ở lá. Triển khai có thể rơi vào đệ quy vô hạn rồi gây `StackOverflowError`; trong một số trường hợp khác, so sánh tham chiếu có thể khiến một nhánh dừng sớm. Tuy nhiên, **chuẩn API không quy định hành vi có chu trình**, nên không được dựa vào hiện tượng quan sát được của một JDK để kết luận phương thức hỗ trợ vòng.

Đặc biệt, đừng dùng `Arrays.deepEquals(cycle, cycle)` làm bài kiểm tra “API chịu được vòng”. Nếu cùng tham chiếu được nhận biết sớm thì một lời gọi cụ thể có thể trả về `true`, nhưng điều đó không biến lời gọi trên cấu trúc cyclic thành hợp lệ theo đặc tả.

### Không tự thử mã nguy hiểm trong chương trình ví dụ

Các lệnh sau chỉ minh họa **những lời gọi cần tránh**, không chạy chúng trong ứng dụng hay ví dụ thực hành:

~~~java
Object[] cycle = new Object[1];
cycle[0] = cycle;

// KHÔNG GỌI trên dữ liệu có vòng:
// Arrays.deepHashCode(cycle);
// Arrays.deepEquals(cycle, anotherCyclicArray);
~~~

Không nên bắt `StackOverflowError` rồi xem đó là cách xử lý chu trình bình thường. Đó là lỗi cạn tài nguyên stack; thuật toán đúng phải nhận diện và xử lý cấu trúc vòng **trước khi đệ quy không kiểm soát**.

## 4. Trường hợp riêng: `Arrays.deepToString` có xử lý vòng mảng

`Arrays.deepToString(Object[])` được thiết kế để tạo biểu diễn chuỗi của mảng nhiều chiều. Đối với **mảng tham chiếu lồng nhau có đường dẫn quay lại mảng đã nằm trên đường đệ quy**, nó thay nhánh tự tham chiếu bằng chuỗi `[...]`.

Ví dụ một mảng chỉ chứa chính nó sẽ được in thành `[[...]]`. Nếu mảng chứa phần tử `"root"` rồi một tham chiếu quay lại chính nó, kết quả là `[root, [...]]`. Chu trình gián tiếp cũng được xử lý; ví dụ `outer -> inner -> outer` tạo `[[[...]]]`.

Các dấu `[...]` là **ký hiệu hiển thị**, không có nghĩa đối tượng thực sự chứa chuỗi ký tự đó. `deepToString` không cung cấp một biểu diễn chuẩn duy nhất cho mọi đồ thị, không đánh số danh tính đối tượng và không tự quyết định hai đồ thị cyclic có bằng nhau hay không.

Giới hạn cần nhớ: việc phòng vòng được mô tả cho **mảng lồng nhau**. Nếu phần tử là đối tượng tùy biến mà `toString()` của nó tự duyệt qua một đồ thị cyclic không kiểm soát, `deepToString` không thể tự sửa thuật toán `toString()` đó. Tương tự, một `List` hoặc `Map` có chu trình vẫn cần xem xét hợp đồng riêng của lớp và các phần tử của nó.

## 5. Ví dụ hoàn chỉnh chạy với Java 17+

Chương trình sau minh họa ba nhóm kết quả an toàn: so sánh sâu trên mảng không có vòng, hiển thị mảng có vòng, và tự duyệt đồ thị liên kết đơn với khả năng ghi nhận đối tượng đã gặp.

Tạo tệp **`CyclicStructureDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public class CyclicStructureDemo {
    static final class Node {
        final String label;
        Node next;

        Node(String label) {
            this.label = label;
        }
    }

    static String describe(Node start) {
        Map<Node, Integer> seen = new IdentityHashMap<>();
        StringBuilder out = new StringBuilder();
        Node current = start;

        while (current != null) {
            if (out.length() > 0) {
                out.append(" -> ");
            }

            Integer oldId = seen.get(current);
            if (oldId != null) {
                out.append("#").append(oldId).append("(cycle)");
                break;
            }

            int id = seen.size() + 1;
            seen.put(current, id);
            out.append("#").append(id)
               .append("(").append(current.label).append(")");
            current = current.next;
        }

        return out.toString();
    }

    // Chỉ so sánh đồ thị Node có một cạnh "next" cho mỗi nút,
    // yêu cầu bảo toàn tên và đúng mẫu tham chiếu/vòng.
    static boolean sameShape(Node left, Node right) {
        Map<Node, Node> leftToRight = new IdentityHashMap<>();
        Map<Node, Node> rightToLeft = new IdentityHashMap<>();

        while (left != null && right != null) {
            if (leftToRight.containsKey(left)
                    || rightToLeft.containsKey(right)) {
                return leftToRight.get(left) == right
                        && rightToLeft.get(right) == left;
            }

            if (!Objects.equals(left.label, right.label)) {
                return false;
            }

            leftToRight.put(left, right);
            rightToLeft.put(right, left);
            left = left.next;
            right = right.next;
        }

        return left == null && right == null;
    }

    public static void main(String[] args) {
        Object[] first = {
            new int[] {1, 2},
            new Object[] {"x", new int[] {3}}
        };
        Object[] equivalent = {
            new int[] {1, 2},
            new Object[] {"x", new int[] {3}}
        };

        System.out.println("shallow equals: "
                + Arrays.equals(first, equivalent));
        System.out.println("deep equals: "
                + Arrays.deepEquals(first, equivalent));
        System.out.println("deep hash equal: "
                + (Arrays.deepHashCode(first)
                   == Arrays.deepHashCode(equivalent)));
        System.out.println("deep text: " + Arrays.deepToString(first));

        Object[] direct = new Object[2];
        direct[0] = "root";
        direct[1] = direct;
        System.out.println("direct cycle: "
                + Arrays.deepToString(direct));

        Object[] outer = new Object[1];
        Object[] inner = new Object[1];
        outer[0] = inner;
        inner[0] = outer;
        System.out.println("indirect cycle: "
                + Arrays.deepToString(outer));

        // Ba đồ thị có cấu trúc lặp; không dùng deepEquals/deepHashCode.
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;

        Node c = new Node("A");
        Node d = new Node("B");
        c.next = d;
        d.next = c;

        Node e = new Node("A");
        Node f = new Node("B");
        e.next = f;
        f.next = f; // Khác mẫu vòng của a -> b -> a.

        System.out.println("graph walk: " + describe(a));
        System.out.println("same graph shape: " + sameShape(a, c));
        System.out.println("different graph shape: " + sameShape(a, e));
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac CyclicStructureDemo.java
java CyclicStructureDemo
~~~

Đầu ra xác định:

~~~text
shallow equals: false
deep equals: true
deep hash equal: true
deep text: [[1, 2], [x, [3]]]
direct cycle: [root, [...]]
indirect cycle: [[[...]]]
graph walk: #1(A) -> #2(B) -> #1(cycle)
same graph shape: true
different graph shape: false
~~~

### Giải thích đầu ra

1. Hai mảng `first` và `equivalent` có những mảng con được tạo riêng. `Arrays.equals` so sánh phần tử trực tiếp, nên không xem các mảng con mới là bằng nhau theo nội dung và cho `false`.
2. `Arrays.deepEquals` duyệt mảng con và phần tử lá. Cả hai cấu trúc **không có vòng** và tương đương nội dung, nên trả về `true`; mã băm sâu cũng bằng nhau.
3. `Arrays.deepToString(first)` trình bày dữ liệu lồng nhau thành `[[1, 2], [x, [3]]]`.
4. Hai mảng cyclic chỉ được truyền cho `Arrays.deepToString`. Hàm này đánh dấu tham chiếu quay vòng bằng `[...]` thay vì đi sâu mãi.
5. Hàm `describe` sử dụng `IdentityHashMap` ghi nhận đúng từng **tham chiếu Node** đã gặp. Khi quay về nút `a`, nó phát hiện ID `#1` và dừng.
6. Hàm `sameShape` duy trì ánh xạ hai chiều giữa các Node theo danh tính, đối chiếu `label` và quan hệ `next`; hai chu trình `A -> B -> A` khớp nhau, còn `A -> B -> B` khác mẫu vòng.

**Phạm vi ví dụ:** `sameShape` chỉ là thuật toán minh họa cho đồ thị mà mỗi Node có **tối đa một liên kết `next`**. Nó kiểm tra sự tương ứng từng nút và vòng tham chiếu của chính mô hình đó. Không coi nó là thuật toán tổng quát cho đồ thị có nhiều cạnh, collection lồng nhau hoặc mọi cấu trúc Java.

## 6. Vì sao dùng `IdentityHashMap` để theo dõi đối tượng đã thăm?

Khi kiểm tra vòng trong **đồ thị đối tượng**, câu hỏi cần trả lời trước tiên là: “Ta đã gặp **chính thể hiện này** chưa?”, nghĩa là dùng `==` đối với tham chiếu.

`IdentityHashMap` thực hiện so sánh khóa bằng `==`, thay cho `equals()` theo nội dung. Điều đó hữu ích vì:

- Hai Node khác tham chiếu nhưng `equals()` bằng nhau theo nghiệp vụ vẫn được phân biệt.
- Việc thêm Node vào tập đã thăm không gọi `equals()` hoặc `hashCode()` do người dùng định nghĩa, vốn có thể tự duyệt qua cấu trúc vòng.
- Duyệt dựa vào danh tính đối tượng, đúng mục tiêu phát hiện tham chiếu lặp.

Có thể tạo một `Set` theo danh tính bằng:

~~~java
Set<Object> seen =
        java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
~~~

Đây là **đoạn mã minh họa** cần đặt trong ngữ cảnh có `import java.util.Set;` hoặc dùng tên đầy đủ. Lưu ý `IdentityHashMap` không có cùng ngữ nghĩa khóa như `HashMap`: nên chỉ chọn nó khi thực sự cần bình đẳng theo **danh tính**.

Trong đồ thị có nhiều nhánh, cần phân biệt ít nhất hai tình huống:

- **Đã thăm toàn cục**: một Node xuất hiện ở hai nhánh nhưng không tạo vòng. Nó là phần tử được chia sẻ (*shared reference*), không mặc nhiên là chu trình.
- **Đang nằm trên đường duyệt hiện tại**: gặp lại Node đang nằm trong ngăn xếp đường đi hiện tại là dấu hiệu có cạnh quay về tổ tiên, tạo chu trình trong một lượt duyệt theo chiều sâu.

Với một danh sách liên kết đơn `next` như ví dụ, gặp lại cùng tham chiếu khi đi tuyến tính là đủ để xác định một vòng. Với đồ thị có nhiều cạnh, thuật toán DFS dùng các trạng thái *chưa thăm / đang thăm / hoàn tất* hoặc tập `visiting` và `visited` riêng theo mục tiêu phát hiện vòng.

## 7. So sánh hai cấu trúc có vòng cần định nghĩa “bằng nhau”

Không tồn tại một nghĩa duy nhất cho “hai đồ thị cyclic bằng nhau”. Ví dụ:

- **Bằng nhau theo danh tính:** hai biến phải trỏ cùng đối tượng (kiểm tra bằng `==`).
- **Bằng nhau theo giá trị nút:** có thể coi các nút có cùng mã nghiệp vụ là bằng nhau dù nối cạnh khác.
- **Bằng nhau theo cấu trúc (topology):** bảo toàn một ánh xạ giữa các nút, các nhãn và các cạnh; nhánh dùng chung hay chu trình khác nhau phải được phân biệt.
- **Bằng nhau theo cách triển khai nghiệp vụ:** ứng dụng có thể chỉ quan tâm một tập thuộc tính chọn lọc.

`sameShape` trong ví dụ thực thi **một định nghĩa cụ thể**: nhãn tương ứng giống nhau và quan hệ tham chiếu `next` có cấu trúc tương ứng một-một. Khi hai đường duyệt quay về cùng cặp Node đã ánh xạ, thuật toán dừng thành công; nếu một bên quay về Node khác cấu trúc, nó trả về `false`.

Đối với đồ thị tổng quát, cần kiểm soát ánh xạ hai chiều (*one-to-one mapping*), thứ tự cạnh nếu thứ tự có ý nghĩa, alias chung và chu trình. Một tập các cặp đã so sánh đôi khi tránh được đệ quy vô hạn nhưng **chưa tự động chứng minh** hai đồ thị có cùng cấu trúc theo mọi định nghĩa. Hãy xác định ngữ nghĩa trước khi lựa chọn thuật toán.

## 8. `equals()`, `hashCode()`, `toString()` tự viết cũng có thể lặp vô hạn

Rủi ro không chỉ xuất hiện trong `Arrays.deepEquals` hoặc `Arrays.deepHashCode`. Nếu lớp tự ghi đè phương thức rồi đi qua các liên kết mà không kiểm soát vòng, sự đệ quy có thể chạy mãi.

Ví dụ minh họa **không chạy độc lập**:

~~~java
@Override
public String toString() {
    return "Node(" + label + ", next=" + next + ")";
}
~~~

Nếu `next` quay lại Node hiện tại hoặc nằm trong một chu trình, việc nối chuỗi gọi `next.toString()` rồi lại quay về đối tượng cũ. Với cách triển khai này, ta có thể gặp `StackOverflowError`.

Một cách an toàn hơn cho log là chỉ in **thông tin không đệ quy**:

~~~java
@Override
public String toString() {
    return "Node(" + label + ")";
}
~~~

Nếu thật sự cần in **đồ thị đầy đủ**, nên có hàm hiển thị riêng nhận trạng thái đã thăm, như `describe`. Cách tiếp cận tương tự áp dụng cho `equals()`/`hashCode()`: không để chúng tự đệ quy không hạn chế qua mọi cạnh, đặc biệt khi đối tượng dự định làm khóa `HashMap`.

Một hàm băm dành cho **đồ thị cyclic** cần được thiết kế theo một quy ước xác định (chẳng hạn định danh nút bất biến, thứ tự duyệt và đánh dấu alias nhất quán). Phải bảo đảm điều kiện bắt buộc: nếu hai giá trị bằng nhau theo `equals()` đã chọn thì chúng có cùng mã băm. Chỉ chọn mã băm ngẫu nhiên hoặc `System.identityHashCode()` sẽ không tương thích nếu `equals()` coi các đồ thị khác tham chiếu là bằng nhau theo cấu trúc.

## 9. Phân biệt mảng, collection và tham chiếu vòng

Một số trường hợp thường bị nhập nhằng:

- **Mảng nguyên thủy** như `int[]` không thể tự chứa chính nó theo kiểu phần tử nguyên thủy. Vòng chỉ hình thành từ các tham chiếu, chẳng hạn trong `Object[]`, Node hoặc collection.
- **Mảng nhiều chiều kiểu `int[][]`** có các mảng con nguyên thủy; bản thân mảng `int[][]` không thể chứa chính nó làm phần tử `int[]` vì kiểu không khớp. Tuy nhiên một `Object[]` có thể chứa `int[]` và tham chiếu quay vòng tới các mảng `Object[]`.
- **`List`, `Set`, `Map`** có thể tổ chức dữ liệu chứa tham chiếu qua lại. Hợp đồng và cách xử lý của từng API không đồng nhất với `Arrays.deepToString`. Đừng mặc định chúng đều tự chống vòng ở `equals()`, `hashCode()` hoặc `toString()`.
- **Mảng lồng nhau rất sâu nhưng không có vòng** vẫn có thể gây cạn stack nếu một thuật toán dùng đệ quy quá sâu. Không có vòng chưa có nghĩa mọi thao tác đệ quy đều an toàn với mọi độ sâu.
- **`record` chứa mảng** không tự tạo so sánh sâu các phần tử. `equals()`/`hashCode()` được sinh cho record xử lý thành phần mảng theo ngữ nghĩa của chính đối tượng mảng (thường là danh tính), không tự thay bằng `Arrays.deepEquals`/`deepHashCode`.

Đặc biệt, `Objects.deepEquals(a, b)` cũng **không phải lời giải cho mảng có vòng**: khi cả hai đối số là mảng, phương thức này dựa vào `Arrays.deepEquals`; hạn chế về cấu trúc cyclic vẫn còn.

## 10. Chiến lược xử lý thực tế

1. **Nếu dữ liệu là mảng lồng nhau không có vòng và muốn so sánh nội dung:** dùng `Arrays.deepEquals` cùng `Arrays.deepHashCode`, khi cần biểu diễn dùng `Arrays.deepToString`.
2. **Nếu chỉ cần ghi log một mảng có tham chiếu vòng:** `Arrays.deepToString` có xử lý vòng của mảng; không dùng kết quả in làm khóa duy nhất hoặc định nghĩa cấu trúc bằng nhau.
3. **Nếu cần phát hiện vòng trong đồ thị:** duyệt theo danh tính; theo dõi trạng thái đang thăm, đã thăm bằng `IdentityHashMap` hoặc `Set` xây từ nó.
4. **Nếu cần so sánh hai đồ thị cyclic:** trước tiên định nghĩa tính bằng nhau, sau đó dùng ánh xạ cặp Node hoặc thuật toán đồ thị tương ứng, có cơ chế dừng và bảo toàn alias khi cần.
5. **Nếu cần dùng cấu trúc làm khóa băm:** ưu tiên khóa bất biến theo mã nghiệp vụ hoặc một đại diện bất biến đã chuẩn hóa. Tránh tính `equals()`/`hashCode()` bằng cách đi sâu không hạn chế trong đồ thị có vòng.
6. **Nếu dữ liệu đến từ nguồn ngoài:** kiểm tra trước tính hợp lệ và khả năng có vòng, giới hạn số nút, số cạnh hoặc độ sâu theo yêu cầu bảo vệ tài nguyên của ứng dụng.

### Checklist nhanh

- Có **tham chiếu quay về cùng đối tượng** trực tiếp hoặc qua nhiều lớp không?
- Đang sử dụng `Arrays.deepEquals` hoặc `deepHashCode` với mảng có vòng chứ?
- Có nhầm `deepToString` xử lý vòng trong mảng với một cơ chế chống vòng cho mọi đối tượng Java không?
- Tập đối tượng đã thăm có dựa vào **danh tính tham chiếu** khi mục tiêu là theo dõi cùng thể hiện không?
- Thuật toán có phân biệt **chia sẻ tham chiếu** và **chu trình thực sự**?
- Có xác định trước nghĩa của “bằng nhau” khi so sánh cấu trúc cyclic?
- Có giới hạn tài nguyên và trường hợp cấu trúc lồng quá sâu?

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Một `Object[]` chứa chính nó. Có thể yên tâm gọi `Arrays.deepHashCode(array)` không?

**Đáp án:** Không. Java API quy định hành vi không xác định cho mảng tự tham chiếu trực tiếp hoặc gián tiếp.

**Câu 2.** `Arrays.deepToString(array)` có gặp vấn đề giống `Arrays.deepHashCode(array)` khi chính mảng xuất hiện lại trong cấu trúc không?

**Đáp án:** `deepToString` có cơ chế biểu diễn các nhánh mảng quay vòng bằng `[...]`. Đây là cơ chế in nội dung, không tự cung cấp khả năng so sánh hoặc băm sâu trên cấu trúc cyclic.

**Câu 3.** Hai nhánh cùng trỏ đến một mảng con, nhưng mảng con không trỏ ngược về cha. Đó có phải vòng?

**Đáp án:** Không. Đó là *shared reference*. Có cạnh dùng chung không đồng nghĩa có đường quay về điểm xuất phát.

**Câu 4.** Vì sao `IdentityHashMap` có ích khi phát hiện đối tượng đã thăm?

**Đáp án:** Nó phân biệt khóa bằng `==` thay vì gọi `equals()`/`hashCode()` của đối tượng, nhờ đó theo dõi chính xác từng thể hiện và tránh kích hoạt logic so sánh đệ quy tự viết.

**Câu 5.** Có thể dùng cùng một thuật toán băm cho mọi định nghĩa bằng nhau của đồ thị cyclic không?

**Đáp án:** Không. Cần chọn ngữ nghĩa bằng nhau, rồi thiết kế thuật toán băm tương thích; đặc biệt phải duy trì “`equals()` bằng nhau ⇒ `hashCode()` bằng nhau”.

**Ghi nhớ:** `Arrays.deepEquals` và `Arrays.deepHashCode` dành cho so sánh/băm sâu mảng **không có vòng**; việc dùng chúng với mảng cyclic có hành vi không xác định. `Arrays.deepToString` xử lý các nhánh quay vòng giữa mảng để hiển thị. Với đồ thị tham chiếu tổng quát, hãy chủ động phát hiện danh tính đã thăm và định nghĩa rõ ngữ nghĩa so sánh.

## Tham khảo API

- [Java `Arrays` API — deepEquals, deepHashCode, deepToString](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html)
- [Java `IdentityHashMap` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arrays-nested-009">Kiểm tra null, mảng rỗng và phần tử mảng null</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra `null`, mảng rỗng và phần tử mảng `null` trong Java

Khi thiết kế `equals()`/`hashCode()` cho đối tượng chứa mảng hoặc cấu trúc lồng nhau, ba trạng thái dễ bị nhầm lẫn là **mảng `null`**, **mảng rỗng**, và **mảng có phần tử `null`**. Chúng có ý nghĩa khác nhau và thường dẫn tới kết quả bằng nhau, mã băm hoặc ngoại lệ khác nhau. Việc chọn `Arrays.equals()`, `Arrays.deepEquals()` và hàm hash tương ứng cần bám sát cấu trúc thực tế của dữ liệu.

Ví dụ:

```java
String[] missing = null;           // Không có đối tượng mảng.
String[] empty = {};               // Có mảng, độ dài 0.
String[] oneNull = { null };       // Có mảng, độ dài 1; phần tử 0 là null.
String[] oneEmpty = { "" };        // Có mảng, độ dài 1; phần tử 0 là chuỗi rỗng.
```

Bốn trạng thái này **không đồng nghĩa**. Đặc biệt, `null` của cả biến mảng khác với `null` ở một vị trí **bên trong mảng**.

## 1. Phân biệt bằng thuộc tính và ý nghĩa nghiệp vụ

| Trạng thái | Ví dụ | `array == null` | `array.length` | Diễn giải phổ biến |
| --- | --- | --- | --- | --- |
| Mảng `null` | `String[] a = null;` | `true` | Ném `NullPointerException` nếu truy cập | Chưa có dữ liệu, không được cung cấp hoặc vắng mặt |
| Mảng rỗng | `String[] a = {};` | `false` | `0` | Dữ liệu được cung cấp, nhưng không có phần tử |
| Mảng chứa `null` | `String[] a = {null};` | `false` | `1` | Có một vị trí chưa có giá trị |
| Mảng chứa chuỗi rỗng | `String[] a = {""};` | `false` | `1` | Có một giá trị `String` thật, dài 0 |

Trong API nhận danh sách các lựa chọn, `null` có thể nghĩa là “không chỉ định”; `[]` có thể nghĩa là “đã chỉ định nhưng không chọn gì”; `[null]` có thể bị xem là dữ liệu không hợp lệ hoặc có thể là một vị trí trống hợp lệ. **Ý nghĩa chính xác do hợp đồng API quyết định**, không tự suy luận mọi trạng thái về cùng một giá trị.

Mảng nguyên thủy như `int[]` **không thể chứa phần tử `null`**. Ví dụ `int[] a = {null};` không biên dịch. Tuy nhiên, `int[] a` vẫn có thể là `null`, và mảng nhiều chiều `int[][]` có thể chứa **hàng `int[]` bằng `null`**, vì các hàng đó là tham chiếu.

## 2. Kiểm tra an toàn trước khi truy cập

Truy cập `array.length`, `array[0]` hoặc duyệt bằng vòng `for` sẽ ném `NullPointerException` nếu chính mảng là `null`. Với mảng không `null` nhưng rỗng, `array[0]` lại ném `ArrayIndexOutOfBoundsException`.

```java
static boolean hasFirstNonNull(String[] values) {
    return values != null
            && values.length > 0
            && values[0] != null;
}
```

Đây là **mảnh phương thức minh họa**, không phải chương trình độc lập. Nhờ toán tử `&&` tính ngắn mạch, điều kiện bên phải không được tính nếu điều kiện bên trái sai; vì vậy có thể kiểm tra mảng, độ dài rồi phần tử theo đúng thứ tự.

Nếu yêu cầu là không được xuất hiện phần tử `null` trong toàn bộ mảng, kiểm tra từng phần tử, sau khi xử lý mảng `null` theo chính sách đã định:

```java
static boolean hasNoNullElement(String[] values) {
    if (values == null) {
        return false;
    }
    for (String value : values) {
        if (value == null) {
            return false;
        }
    }
    return true;
}
```

Đoạn này chấp nhận mảng rỗng vì không có phần tử nào vi phạm; nếu nghiệp vụ bắt buộc **ít nhất một phần tử**, hãy kiểm tra thêm `values.length > 0`.

`Objects.requireNonNull(values, "values")` phù hợp khi API cấm mảng `null`; nó ném ngoại lệ ngay tại ranh giới nhận dữ liệu, giúp phát hiện sai sót sớm. Nếu cả mảng và phần tử đều bắt buộc không null, phải kiểm tra **cả hai cấp**: `requireNonNull` trên biến mảng không tự kiểm tra phần tử.

## 3. `Arrays.equals()` phân biệt ba trạng thái thế nào?

Dùng `java.util.Arrays.equals(...)` để so sánh **nội dung của mảng một chiều** thay vì toán tử `==` hay `array.equals(other)`.

Các quy tắc chính:

1. Hai biến mảng đều `null` được `Arrays.equals()` xem là bằng nhau.
2. Một mảng `null` và một mảng rỗng là **khác nhau**.
3. Hai mảng rỗng cùng kiểu tương ứng bằng nhau về nội dung dù khác tham chiếu.
4. Hai mảng cùng độ dài được so sánh theo **từng vị trí**.
5. Với mảng tham chiếu như `String[]`, hai phần tử cùng `null` ở cùng vị trí là bằng nhau; một `null` và một `String` không null là khác nhau; hai phần tử không null được so sánh bằng `equals()` tương ứng.
6. Với mảng nguyên thủy như `int[]`, mỗi giá trị nguyên thủy được so sánh theo quy tắc của overload tương ứng, không có phần tử null.

### 3.1. Ví dụ Java 17+ chạy được

Lưu thành **`NullArrayDemo.java`**:

```java
import java.util.Arrays;

public class NullArrayDemo {
    public static void main(String[] args) {
        String[] missing = null;
        String[] empty = {};
        String[] oneNull = {null};
        String[] anotherNull = {null};
        String[] oneEmpty = {""};

        System.out.println("missing is null: "
                + (missing == null));
        System.out.println("empty length: " + empty.length);
        System.out.println("oneNull length: " + oneNull.length);

        System.out.println("null vs null: "
                + Arrays.equals(missing, (String[]) null));
        System.out.println("null vs empty: "
                + Arrays.equals(missing, empty));
        System.out.println("empty vs empty: "
                + Arrays.equals(empty, new String[0]));
        System.out.println("null-element vs null-element: "
                + Arrays.equals(oneNull, anotherNull));
        System.out.println("null-element vs empty: "
                + Arrays.equals(oneNull, empty));
        System.out.println("null-element vs empty-string: "
                + Arrays.equals(oneNull, oneEmpty));

        System.out.println("hash null array: "
                + Arrays.hashCode(missing));
        System.out.println("hash empty array: "
                + Arrays.hashCode(empty));
        System.out.println("hash [null]: "
                + Arrays.hashCode(oneNull));
    }
}
```

Biên dịch/chạy:

```bash
javac NullArrayDemo.java
java NullArrayDemo
```

Stdout:

```text
missing is null: true
empty length: 0
oneNull length: 1
null vs null: true
null vs empty: false
empty vs empty: true
null-element vs null-element: true
null-element vs empty: false
null-element vs empty-string: false
hash null array: 0
hash empty array: 1
hash [null]: 31
```

`Arrays.hashCode((Object[]) null)` trả `0`; mảng rỗng có mã băm `1`; mảng đối tượng một phần tử `null` có mã băm `31` vì quy tắc tích lũy bắt đầu từ `1`, sau đó lấy `31 * 1 + 0`. Các giá trị trên xác định bởi `Arrays.hashCode()` với mảng tham chiếu đang minh họa; chúng **không phải mã định danh duy nhất**.

Khi muốn gọi trực tiếp với hằng `null`, phải tránh gọi `Arrays.equals(null, null)` hoặc `Arrays.hashCode(null)` không có kiểu đích: lớp `Arrays` có nhiều overload cho các loại mảng, nên lời gọi có thể **mơ hồ khi biên dịch**. Sử dụng biến có kiểu rõ ràng hoặc ép kiểu phù hợp như `(String[]) null`.

## 4. `==`, `Objects.equals()` và `Arrays.equals()` không thay thế nhau

Với hai mảng riêng biệt có cùng phần tử:

```java
String[] a = {"Java", null};
String[] b = {"Java", null};
```

- `a == b` là `false` vì chúng là **hai tham chiếu khác nhau**.
- `a.equals(b)` là `false` vì các lớp mảng không ghi đè `Object.equals()` để so nội dung.
- `Objects.equals(a, b)` cũng là `false`: hàm này xử lý null nhưng với mảng không null, nó vẫn gọi `a.equals(b)`.
- `Arrays.equals(a, b)` là `true`: so sánh độ dài và từng phần tử ở cùng vị trí.
- `Objects.deepEquals(a, b)` cũng là `true` vì cả hai là mảng; nhưng với mảng một chiều, `Arrays.equals()` thường làm rõ ý định hơn.

Hàm `Objects.equals()` hữu ích khi thuộc tính là một đối tượng giá trị có `equals()` đúng; nó **không tự biến một trường mảng thành phép so sánh phần tử**. Trong `equals()` của lớp chứa mảng, sử dụng đúng overload `Arrays.equals()` hoặc `Arrays.deepEquals()` theo cấu trúc.

Nguyên tắc song hành: nếu trường mảng tham gia `equals()` bằng `Arrays.equals()`, phần tương ứng của `hashCode()` nên dùng `Arrays.hashCode()`; nếu dùng `Arrays.deepEquals()`, dùng `Arrays.deepHashCode()` cho phần cấu trúc lồng tương ứng. Tránh dùng `Objects.hashCode(array)` hoặc bản thân `array.hashCode()` rồi kỳ vọng cùng kết quả theo nội dung: chúng dùng mã băm của **đối tượng mảng**, không thay thế `Arrays.hashCode()`.

## 5. Mảng lồng nhau: vì sao cần `deepEquals()` và `deepHashCode()`?

Mảng hai chiều trong Java là **mảng chứa các mảng**. Với `String[][]`, mỗi phần tử hàng có thể là một `String[]` hoặc `null`. Hàng không null lại có thể rỗng, hoặc chứa phần tử `String` bằng `null`. Các cấp này cần được phân biệt.

`Arrays.equals(String[][], String[][])` thực chất chọn overload `equals(Object[], Object[])`: nó so sánh **các phần tử hàng** bằng `equals()`. Vì mỗi hàng là một đối tượng mảng, hai hàng riêng biệt có nội dung giống nhau **không bằng nhau** theo `Object.equals()`. Đó là so sánh *nông* tại lớp lồng.

`Arrays.deepEquals(Object[], Object[])` so sánh đệ quy phần tử là mảng; xử lý cả mảng nguyên thủy bên trong bằng overload so sánh nội dung tương ứng. `Arrays.deepHashCode(Object[])` tính hash tương thích với quan hệ `deepEquals()`.

### 5.1. Ví dụ phân biệt hàng `null`, hàng rỗng, phần tử `null`

Tạo file **`NestedArrayDemo.java`**:

```java
import java.util.Arrays;

public class NestedArrayDemo {
    public static void main(String[] args) {
        String[][] left = {
            null,
            {},
            {null, "ok"}
        };
        String[][] same = {
            null,
            {},
            {null, "ok"}
        };
        String[][] changed = {
            null,
            {},
            {"", "ok"}
        };
        String[][] nullRow = {null};
        String[][] emptyRow = {new String[0]};

        System.out.println("shallow equals: "
                + Arrays.equals(left, same));
        System.out.println("deep equals: "
                + Arrays.deepEquals(left, same));
        System.out.println("null vs empty row: "
                + Arrays.deepEquals(nullRow, emptyRow));
        System.out.println("null vs empty element: "
                + Arrays.deepEquals(left, changed));
        System.out.println("same deep hash: "
                + (Arrays.deepHashCode(left)
                        == Arrays.deepHashCode(same)));
        System.out.println("nested data: "
                + Arrays.deepToString(left));

        int[][] numbers = {{1, 2}, null, {}};
        int[][] copy = {{1, 2}, null, {}};
        System.out.println("primitive nested equal: "
                + Arrays.deepEquals(numbers, copy));
    }
}
```

Chạy:

```bash
javac NestedArrayDemo.java
java NestedArrayDemo
```

Stdout:

```text
shallow equals: false
deep equals: true
null vs empty row: false
null vs empty element: false
same deep hash: true
nested data: [null, [], [null, ok]]
primitive nested equal: true
```

**Phân tích từng cấp:**

- `left` và `same` là hai mảng ngoài riêng biệt; các hàng rỗng và hàng chứa `null` cũng được tạo thành đối tượng mảng riêng. Vì vậy so sánh nông `Arrays.equals(left, same)` là `false`.
- `Arrays.deepEquals(left, same)` cho `true`: hàng thứ nhất đều `null`; hàng thứ hai đều rỗng; hàng thứ ba cùng chuỗi `"ok"` và cùng phần tử `null` ở vị trí đầu.
- `nullRow` khác `emptyRow`, vì **hàng không tồn tại** khác **hàng tồn tại với 0 phần tử**.
- `changed` chứa `""` thay cho `null` ở vị trí đầu của hàng thứ ba, nên so sánh sâu khác nhau.
- `Arrays.deepHashCode(left)` bằng `Arrays.deepHashCode(same)` vì dữ liệu so sánh sâu bằng nhau.
- `int[][]` cũng là `Object[]` ở **cấp ngoài** (mỗi phần tử là một `int[]`), nên gọi `Arrays.deepEquals(numbers, copy)` hợp lệ. Bên trong, các phần tử của `int[]` là số nguyên thủy, không thể là `null`; nhưng cả hàng `int[]` có thể bằng `null`.

Cần nhớ: `Arrays.deepEquals()` và `Arrays.deepHashCode()` nhận `Object[]`, nên dùng cho mảng tham chiếu và các cấu trúc lồng. Với **mảng nguyên thủy một chiều** (`int[]`, `byte[]`, `long[]`, ...), dùng đúng overload `Arrays.equals(int[], int[])` / `Arrays.hashCode(int[])`. Không thể đưa trực tiếp một `int[]` vào tham số `Object[]` của `Arrays.deepEquals()`.

Đối với mảng có cấu trúc **tự tham chiếu/vòng lặp** (mảng chứa chính nó trực tiếp hoặc gián tiếp), không nên áp dụng máy móc các phép deep recursion: hợp đồng của các phương thức deep comparison/hash không bảo đảm kết quả xác định cho mảng vòng. Hãy tránh chu trình hoặc thiết kế thuật toán xử lý identity/chu trình riêng.

## 6. Lồng trong một lớp: `equals()` và `hashCode()` phải đối xứng về độ sâu

Giả sử một giá trị biểu diễn ma trận `int[][]`. Nếu `equals()` dùng `Arrays.deepEquals(rows, other.rows)` nhưng `hashCode()` lại dùng `Arrays.hashCode(rows)`, hai ma trận có các hàng **khác tham chiếu nhưng cùng nội dung** có thể được đánh giá bằng nhau, trong khi mã băm dựa trên identity của các hàng và khác nhau. Đó là **nguy cơ vi phạm hợp đồng**.

Cặp đúng là:

```java
boolean same = Arrays.deepEquals(rows, other.rows);
int hash = Arrays.deepHashCode(rows);
```

Đây là **mảnh code minh họa**, giả định `rows` và `other.rows` đều thuộc loại `int[][]`. Với mảng phẳng `byte[]`, cặp tương ứng là `Arrays.equals(bytes, other.bytes)` và `Arrays.hashCode(bytes)`.

Không được suy luận rằng `Objects.hash(rows)` là lựa chọn tương đương với `Arrays.deepHashCode(rows)`. `Objects.hash(...)` dùng varargs, dễ gây nhầm lẫn về cách mảng được trải thành các phần tử; với mảng lồng, nó không tự có ngữ nghĩa deep hash đúng như yêu cầu.

## 7. Ví dụ lớp giá trị bất biến, chấp nhận hàng `null`

Một đối tượng dùng làm khóa `HashMap` hoặc phần tử `HashSet` nên giữ các trường tham gia equality/hash **ổn định**. Với mảng, `final int[][] rows` chỉ làm **tham chiếu bên ngoài** không đổi; mã gọi vẫn có thể sửa `rows[0][0]` qua alias nếu không sao chép. Đối với ma trận `int[][]`, cần sao chép cả **mảng ngoài lẫn từng hàng không null** khi nhận đầu vào, và không trả trực tiếp mảng bên trong ra ngoài.

Tạo file **`MatrixKeyDemo.java`**:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class MatrixKey {
    private final int[][] rows;

    MatrixKey(int[][] source) {
        Objects.requireNonNull(source, "source");
        this.rows = deepCopy(source);
    }

    private static int[][] deepCopy(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null
                    ? null
                    : source[i].clone();
        }
        return copy;
    }

    public int[][] rowsCopy() {
        return deepCopy(rows);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof MatrixKey other
                && Arrays.deepEquals(rows, other.rows);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(rows);
    }
}

public class MatrixKeyDemo {
    public static void main(String[] args) {
        int[][] input = {{1, 2}, null, {}};
        MatrixKey first = new MatrixKey(input);
        MatrixKey second =
                new MatrixKey(new int[][] {{1, 2}, null, {}});
        MatrixKey different =
                new MatrixKey(new int[][] {{1, 2}, {}, {}});

        System.out.println("equal contents: "
                + first.equals(second));
        System.out.println("same hash: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("null row vs empty row: "
                + first.equals(different));

        Set<MatrixKey> keys =
                new HashSet<>(List.of(first, second));
        System.out.println("set size: " + keys.size());

        input[0][0] = 99;
        System.out.println("after source mutation: "
                + first.equals(second));

        int[][] externalCopy = first.rowsCopy();
        externalCopy[0][0] = 77;
        System.out.println("after copy mutation: "
                + first.equals(second));
    }
}
```

Chạy:

```bash
javac MatrixKeyDemo.java
java MatrixKeyDemo
```

Stdout:

```text
equal contents: true
same hash: true
null row vs empty row: false
set size: 1
after source mutation: true
after copy mutation: true
```

**Vì sao ví dụ này quan trọng?**

1. Ma trận có hàng `null`, hàng rỗng và hàng có dữ liệu; sự khác nhau ấy được giữ nguyên bằng `deepCopy`.
2. `Arrays.deepEquals()` và `Arrays.deepHashCode()` xét các hàng theo nội dung một cách tương ứng.
3. Lớp `final` giữ `rows` là `private final`, sao chép từng hàng khi nhận và trả dữ liệu, nên thay đổi bên ngoài không phá trạng thái khóa.
4. Hai khóa có cùng nội dung logic được `HashSet` coi là một; khóa có hàng `null` khác khóa có hàng rỗng.
5. Ví dụ dành riêng cho `int[][]`; nếu phần tử lồng là **đối tượng mutable** (ví dụ `List<MutableItem>[]`) thì `clone()` từng hàng chỉ sao chép tham chiếu phần tử, **chưa đủ để tạo bất biến sâu**. Cần sao chép/đóng băng phù hợp với mô hình dữ liệu.

Tùy API, bạn có thể lựa chọn **cấm hoàn toàn hàng `null`**, thay vì giữ nó. Nhưng phải kiểm tra trong constructor và thể hiện rõ với người gọi, không lén chuẩn hóa nếu `null` mang ý nghĩa nghiệp vụ.

## 8. Khi nào nên chuẩn hóa `null` thành mảng rỗng?

Có hai lựa chọn đều hợp lệ, miễn là thống nhất giữa đọc dữ liệu, `equals()` và `hashCode()`:

### Lựa chọn A: bảo toàn khác biệt

Nếu `null` nghĩa là “chưa biết”, còn rỗng nghĩa là “biết chắc không có”, hãy giữ nguyên. Dùng `Arrays.equals()` và `Arrays.hashCode()` cho mảng phẳng; `Arrays.deepEquals()` và `Arrays.deepHashCode()` cho mảng lồng. Hai trạng thái sẽ khác nhau theo phép so sánh nội dung chuẩn.

### Lựa chọn B: coi `null` như rỗng ngay ở ranh giới

Nếu nghiệp vụ coi **không cung cấp** và **danh sách rỗng** là cùng một giá trị, có thể chuẩn hóa trong constructor:

```java
this.tags = tags == null ? new String[0] : tags.clone();
```

Đây là **mảnh code minh họa** cho trường `String[] tags`. Sau chuẩn hóa, không còn sự phân biệt giữa đầu vào `null` và đầu vào rỗng. Dùng `Arrays.equals(this.tags, other.tags)` và `Arrays.hashCode(this.tags)` sẽ nhất quán, với điều kiện không để sửa mảng nội bộ qua getter. Nếu còn cần phân biệt “không được cung cấp”, phải lưu riêng thông tin hiện diện, hoặc giữ `null`.

**Không chuẩn hóa chỉ trong một phương thức.** Nếu `equals()` coi `null` và `[]` bằng nhau nhưng `hashCode()` vẫn trả 0 cho `null` và 1 cho `[]`, bạn đã vi phạm hợp đồng. Tốt nhất chuẩn hóa dữ liệu **một lần tại ranh giới**, rồi dùng cùng trạng thái chuẩn hóa để so sánh và tính hash.

Cũng không nên tự động bỏ các phần tử `null` hoặc đổi `null` thành `""`: `[null]`, `[]` và `[""]` mang những thông tin khác nhau, và hành vi làm sạch dữ liệu phải là **quyết định nghiệp vụ tường minh**.

## 9. Các lỗi rất dễ gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Truy cập `array.length` khi array là `null` | `NullPointerException` | Kiểm tra biến mảng trước |
| Truy cập `array[0]` khi mảng rỗng | `ArrayIndexOutOfBoundsException` | Kiểm tra `length > 0` |
| Chỉ kiểm tra mảng ngoài mà quên phần tử/hàng null | `NullPointerException` khi dùng phần tử/hàng | Kiểm tra từng cấp phù hợp |
| Dùng `==`, `array.equals()` hoặc `Objects.equals()` để so nội dung mảng | So tham chiếu, khác với nội dung | Dùng `Arrays.equals()` hoặc `Arrays.deepEquals()` |
| Dùng `Arrays.equals()` cho hai mảng lồng, rồi kỳ vọng so mọi cấp | Mảng con khác tham chiếu bị coi là khác | Dùng `Arrays.deepEquals()` nếu muốn so sâu |
| Dùng `Arrays.deepEquals()` nhưng `hashCode()` tính theo tham chiếu của mảng con | Nguy cơ hai đối tượng bằng nhau khác hash | Dùng `Arrays.deepHashCode()` tương ứng |
| Coi `null`, `[]` và `[null]` như nhau vô điều kiện | Mất ý nghĩa dữ liệu | Thống nhất quy tắc đầu vào |
| Dùng `Arrays.equals(null, null)` mà không chỉ kiểu | Lời gọi overload có thể mơ hồ | Dùng biến đã khai báo kiểu hoặc ép kiểu |
| Đưa mảng mutable trực tiếp vào khóa hash | Thay đổi dữ liệu làm mất tra cứu | Tạo wrapper bất biến và sao chép phòng vệ |
| Chỉ `clone()` mảng ngoài của cấu trúc lồng | Mảng/hàng con còn bị sửa từ ngoài | Sao chép đủ mức lồng của dữ liệu |

**Lưu ý về `Arrays.toString()` / `Arrays.deepToString()`:** Chúng giúp kiểm tra dữ liệu, nhưng chuỗi hiển thị không phải định nghĩa equality/hash. Ví dụ `Arrays.toString(new String[]{null})` hiển thị `[null]`, còn `Arrays.toString(new String[]{"null"})` cũng hiển thị `[null]`. Đừng so sánh kết quả `toString()` để xác định hai mảng có bằng nhau hay không. Với mảng lồng, dùng `deepToString()` để quan sát các cấp nội dung khi debug.

## 10. Checklist thực hành

- [ ] Xác định rõ `null` của **cả mảng**, mảng `length == 0`, và phần tử `null` có phải ba trạng thái khác nhau theo nghiệp vụ không.
- [ ] Nếu chỉ cho phép đầu vào không null, kiểm tra sớm bằng `Objects.requireNonNull()`; nếu cần cấm phần tử null, kiểm tra từng phần tử/hàng.
- [ ] Khi truy cập `length` hoặc `[0]`, đã kiểm tra mảng không null và đủ số phần tử.
- [ ] Mảng phẳng dùng cặp `Arrays.equals()` / `Arrays.hashCode()` đúng overload.
- [ ] Mảng lồng dùng cặp `Arrays.deepEquals()` / `Arrays.deepHashCode()` khi ý định là so nội dung các cấp.
- [ ] Không nhầm `Objects.equals()` với phép so nội dung mảng; không nhầm `String` rỗng với phần tử null.
- [ ] Không tự chuẩn hóa `null` thành rỗng hay bỏ phần tử null nếu hợp đồng không quy định.
- [ ] Nếu mảng tham gia equality/hash của khóa trong `HashMap`/`HashSet`, trạng thái của nó được bảo vệ khỏi thay đổi ngoài ý muốn.
- [ ] Kiểm thử cả hai mảng null, một null một rỗng, hai rỗng, `[null]`, `[""]`, các hàng null/rỗng trong mảng lồng, và mảng nguyên thủy.

**Nguyên tắc ghi nhớ:** `null` không có nghĩa là “mảng độ dài 0”, và phần tử `null` cũng không có nghĩa là “không có phần tử”. Hãy xác định trước ý nghĩa của dữ liệu, rồi chọn phép kiểm tra, phép so sánh và mã băm tương ứng. Khi cấu trúc lồng nhau, bảo đảm **độ sâu so sánh và độ sâu tính hash nhất quán**.


</details>

- [Quay lại đầu trang](#back-to-top)
