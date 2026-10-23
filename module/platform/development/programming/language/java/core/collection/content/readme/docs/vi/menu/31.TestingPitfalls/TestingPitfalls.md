<a id="back-to-top"></a>

# Kiểm thử và lỗi

## Menu
- [Kiểm thử collection rỗng, một phần tử, phần tử trùng và null](#testing-pitfalls-001)
- [Kiểm thử hợp đồng equals–hashCode và Comparator](#testing-pitfalls-002)
- [Kiểm thử thứ tự chèn, thứ tự sắp xếp và thứ tự ưu tiên](#testing-pitfalls-003)
- [ConcurrentModificationException và thay đổi nguồn qua nhiều đường](#testing-pitfalls-004)
- [UnsupportedOperationException trên factory, wrapper và view](#testing-pitfalls-005)
- [IndexOutOfBoundsException, NoSuchElementException và IllegalStateException](#testing-pitfalls-006)
- [ClassCastException, NullPointerException và kiểu dữ liệu không hợp lệ](#testing-pitfalls-007)
- [Lỗi khóa khả biến, comparator không nhất quán và phần tử mất dấu](#testing-pitfalls-008)
- [Kiểm thử tính nguyên tử, ảnh chụp và nhất quán yếu khi chạy đồng thời](#testing-pitfalls-009)

## <a id="testing-pitfalls-001">Kiểm thử collection rỗng, một phần tử, phần tử trùng và null</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử collection rỗng, một phần tử, phần tử trùng và null

## 1. Kiểm thử điều gì khi một phương thức nhận collection?

Một phương thức có thể chạy đúng với `["A", "B", "C"]` nhưng vẫn lỗi khi nhận danh sách rỗng, chỉ một phần tử, giá trị lặp lại hoặc phần tử `null`. Những trường hợp này không chỉ là dữ liệu “ít gặp”. Chúng kiểm tra các giả định mà mã thường vô tình dựa vào:

- Luôn có phần tử đầu tiên hoặc cuối cùng.
- Vòng lặp luôn chạy ít nhất một lần.
- Hai giá trị bằng nhau luôn là cùng một đối tượng.
- Mọi phần tử đều khác `null`.
- Kết quả có thể bị sắp xếp lại mà không ảnh hưởng yêu cầu.
- Hàm được phép sửa trực tiếp collection đầu vào.

Một ca kiểm thử có giá trị cần xác định **đầu vào, hành động và kết quả mong đợi theo hợp đồng**. Chỉ chạy mã rồi thấy “không có exception” chưa đủ chứng minh rằng phần tử, thứ tự, số lần xuất hiện và tác dụng phụ đều đúng.

Bài này đi từ bốn nhóm dữ liệu biên đến một chương trình Java có thể chạy độc lập, sau đó chuyển cùng các ý tưởng sang kiểm thử JUnit 5.

## 2. Viết hợp đồng trước khi viết kết quả mong đợi

### 2.1. Không có một đáp án chung cho mọi collection

Xét đầu vào `["A", "A", null]`. Một hàm có thể:

- Giữ nguyên cả ba phần tử.
- Bỏ phần tử `null`, nhưng giữ hai lần `"A"`.
- Bỏ `null` và chỉ giữ lần xuất hiện đầu của `"A"`.
- Từ chối đầu vào chứa `null` bằng một exception đã quy định.

Các lựa chọn này đều có thể hợp lệ trong những bài toán khác nhau. Bài kiểm thử không được tự quyết định chính sách thay cho hợp đồng của hàm.

Trước khi lập dữ liệu, hãy trả lời:

| Khía cạnh | Câu hỏi cần làm rõ |
| --- | --- |
| Collection đầu vào | Có được truyền `null` không, hay chỉ được truyền collection không null? |
| Rỗng | Trả kết quả rỗng, giá trị mặc định hay báo lỗi nghiệp vụ? |
| Phần tử null | Giữ lại, bỏ qua, chuyển thành giá trị khác hay từ chối? |
| Phần tử trùng | Giữ đủ số lần xuất hiện, loại trùng hay cộng dồn? |
| Thứ tự | Giữ thứ tự đầu vào, sắp xếp hay không có bảo đảm? |
| Tác dụng phụ | Có được sửa nguồn không? Kết quả có dùng chung cấu trúc với nguồn không? |
| Khả năng sửa đổi | Kết quả có cho phép thêm/xóa hay phải không thể sửa đổi? |

Không phải hợp đồng nào cũng cần mọi dòng, nhưng những dòng liên quan phải rõ trước khi viết assertion.

### 2.2. Hợp đồng mẫu dùng trong bài

Ta sẽ kiểm thử hàm:

```text
distinctNonNull(Collection<String> source) -> List<String>
```

Hàm mẫu có hợp đồng cụ thể:

1. `source == null` bị từ chối bằng `NullPointerException`.
2. Collection rỗng tạo ra danh sách rỗng.
3. Phần tử `null` bị bỏ qua.
4. Giá trị trùng được xác định theo `String.equals()` và chỉ giữ một lần.
5. Kết quả giữ thứ tự gặp lần đầu khi duyệt nguồn.
6. Không sửa collection nguồn.
7. Kết quả là một danh sách mới có thể thêm/xóa phần tử; thay đổi cấu trúc nguồn và kết quả không lan sang nhau.
8. Không cắt khoảng trắng và không đổi hoa/thường. `"A"`, `"a"`, `""` và `" "` là các giá trị khác nhau.

Đây là chính sách của ví dụ, không phải quy tắc bắt buộc cho mọi hàm xử lý collection. Nếu nguồn là `HashSet` không bảo đảm thứ tự duyệt, hàm cũng không thể tạo ra bảo đảm “thứ tự chèn ban đầu” mà nguồn không cung cấp. Các ca kiểm tra thứ tự trong bài dùng nguồn `List` có thứ tự rõ ràng.

## 3. Nhóm rỗng: không có phần tử vẫn là một trạng thái hợp lệ

### 3.1. Phân biệt collection rỗng với collection bằng null

```text
[]      : có đối tượng collection, kích thước bằng 0
null    : không có tham chiếu tới đối tượng collection
[null]  : có collection, kích thước bằng 1, phần tử duy nhất là null
```

Ba đầu vào này kiểm tra ba điều khác nhau. Không gộp chúng thành một ca mang tên “không có dữ liệu” nếu hợp đồng phân biệt chúng.

Với hàm mẫu:

| Đầu vào | Kết quả mong đợi |
| --- | --- |
| `[]` | `[]` |
| `null` | Ném `NullPointerException` |
| `[null]` | `[]` vì chính sách bỏ qua phần tử null |

### 3.2. Những lỗi mà ca rỗng thường phát hiện

- Gọi `get(0)` trước khi kiểm tra kích thước.
- Khởi tạo kết quả từ phần tử đầu, nhưng không có nhánh xử lý rỗng.
- Trả `null` thay vì collection rỗng trong khi hợp đồng yêu cầu collection.
- Tính trung bình bằng cách chia cho kích thước bằng `0`.
- Trả danh sách rỗng không thể sửa đổi ở nhánh rỗng, trong khi nhánh có dữ liệu trả danh sách có thể sửa đổi và hợp đồng yêu cầu tính nhất quán.

Đối với hàm mẫu, kiểm tra `result.isEmpty()` là chưa đủ cho toàn bộ hợp đồng. Ta còn cần biết `result` không null, có thể thêm phần tử và việc thêm không thay đổi nguồn.

Không nên gọi mọi thao tác phá hủy lên mọi collection rỗng rồi giả định đều thành công. Khả năng sửa đổi phụ thuộc loại collection và hợp đồng riêng của nó.

## 4. Nhóm một phần tử: kiểm tra biên vòng lặp và nhánh khởi tạo

Dữ liệu chỉ một phần tử giúp tìm các lỗi như:

- Vòng lặp dùng `i < size - 1` nên bỏ qua phần tử cuối.
- Bắt đầu duyệt ở chỉ số `1` nên không xử lý phần tử nào.
- Mã luôn truy cập phần tử kế tiếp dù không tồn tại.
- Nhánh “không có gì để loại trùng” trả trực tiếp nguồn, làm nguồn và kết quả dùng chung cấu trúc.

Với hợp đồng mẫu, `List.of("A")` phải tạo kết quả `["A"]`. Tuy vậy, chỉ so sánh nội dung chưa phát hiện việc hàm trả lại nguyên danh sách nguồn.

Nếu hợp đồng yêu cầu kết quả mới, hãy kiểm tra hành vi sau khi thay đổi cấu trúc:

1. Gọi hàm với nguồn một phần tử.
2. Thêm phần tử vào kết quả.
3. Xác nhận nguồn vẫn chỉ có phần tử ban đầu.

Kiểm tra tham chiếu khác nhau có thể bổ sung, nhưng hành vi sửa đổi độc lập mới thể hiện rõ mục đích của yêu cầu. Không kiểm tra danh tính đối tượng khi hợp đồng chỉ hứa giá trị tương đương.

## 5. Nhóm trùng: số lượng, vị trí và khái niệm bằng nhau

### 5.1. Dùng cả phần tử trùng liền nhau và cách nhau

Chỉ thử `["A", "A"]` có thể bỏ sót thuật toán chỉ loại các giá trị trùng kề nhau. Hãy thêm trường hợp như:

```text
Nguồn:   ["B", "A", "B", "C", "A"]
Kết quả: ["B", "A", "C"]
```

Trường hợp này cùng lúc kiểm tra:

- Loại trùng dù hai lần xuất hiện không đứng cạnh nhau.
- Không bỏ nhầm giá trị khác.
- Giữ thứ tự lần xuất hiện đầu, thay vì sắp xếp thành `["A", "B", "C"]`.

Nếu hợp đồng yêu cầu giữ mọi lần xuất hiện, kỳ vọng phải khác. Ví dụ một hàm sao chép danh sách phải giữ `["A", "A"]`, không được tự chuyển thành tập hợp.

### 5.2. Trùng về giá trị không nhất thiết trùng tham chiếu

Một kiểm thử chỉ dùng chuỗi literal có thể vô tình không phát hiện mã dùng `==` để so sánh chuỗi. Tạo hai đối tượng riêng nhưng bằng nhau về giá trị:

```java
String first = new String("A");
String second = new String("A");
```

Đây là dữ liệu minh họa phục vụ kiểm thử sự khác biệt giữa tham chiếu và giá trị, không phải cách khởi tạo chuỗi cần dùng thường xuyên trong mã ứng dụng.

Với hợp đồng mẫu, cả hai chỉ tạo một giá trị `"A"` trong kết quả. Với lớp tự định nghĩa, cần kiểm tra `equals()` và `hashCode()` phù hợp nếu dùng collection dựa trên băm. Nếu miền dữ liệu dùng quy tắc khác, chẳng hạn so sánh không phân biệt hoa/thường, hãy đưa quy tắc đó vào hợp đồng và dữ liệu kiểm thử.

### 5.3. Không có quy tắc trùng duy nhất cho List, Set và Map

| Cấu trúc | “Trùng” cần kiểm tra theo điều gì? |
| --- | --- |
| `List` thông thường | Có thể giữ nhiều phần tử bằng nhau; thứ tự và số lần xuất hiện đều quan trọng |
| `HashSet` | Phần tử bằng nhau không tạo thêm phần tử; cần hợp đồng `equals/hashCode` đúng |
| `Map` thông thường | Khóa không trùng; hai khóa khác nhau vẫn có thể ánh xạ tới cùng một giá trị |
| `TreeSet`/`TreeMap` | Việc xác định cùng vị trí khóa/phần tử dựa trên phép so sánh thứ tự; cần kiểm tra quy tắc so sánh của miền dữ liệu; xem [tài liệu TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html) |

Không dùng `toString()` để biến mọi collection thành một chuỗi rồi so sánh. Điều đó dễ gắn kiểm thử với cách hiển thị hoặc thứ tự không được bảo đảm, thay vì ngữ nghĩa cần kiểm tra.

## 6. Nhóm null: kiểm tra đúng tầng và đúng API

### 6.1. Chọn cách tạo dữ liệu cho phép chứa null

Đoạn sau không tạo được một danh sách chứa `null`:

```java
List<String> input = List.of("A", null, "B");
```

`List.of` từ chối phần tử `null`, nên exception xuất hiện ngay khi chuẩn bị đầu vào, trước khi hàm cần kiểm thử được gọi. `List.of` vẫn cho phép phần tử trùng; quy tắc trùng và quy tắc null là hai chuyện riêng. [Tài liệu List của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)

Để tạo nguồn chứa null cho ví dụ, có thể dùng:

```java
List<String> input = new ArrayList<>(
        Arrays.asList("A", null, "B", null));
```

Đoạn minh họa cần import `java.util.List`, `java.util.ArrayList` và `java.util.Arrays`. `Arrays.asList` cho phép phần tử null nhưng có kích thước cố định; bọc bằng `new ArrayList<>(...)` tạo danh sách có thể thêm/xóa. Nếu chỉ đọc dữ liệu, không phải lúc nào cũng cần lớp bọc này.

Với một phần tử null, có thể dùng `Collections.singletonList(null)` có tham số kiểu phù hợp hoặc tạo danh sách rồi `add(null)`. Hãy tránh biểu thức varargs `Arrays.asList(null)` không rõ ý định: null có thể được hiểu là tham chiếu mảng varargs. `Arrays.asList((String) null)` diễn tả rõ một phần tử kiểu String bằng null.

### 6.2. Thử vị trí null có ý nghĩa

Tùy thuật toán, chọn những trường hợp có khả năng kích hoạt nhánh khác nhau:

- Null ở đầu: `[null, "A"]`.
- Null ở giữa: `["A", null, "B"]`.
- Null ở cuối: `["A", null]`.
- Chỉ có null: `[null]` hoặc `[null, null]`.
- Null xen với giá trị trùng: `[null, "A", null, "A", "B"]`.

Không nhất thiết liệt kê mọi hoán vị. Hãy chọn đại diện cho các giả định của mã, rồi bổ sung trường hợp kết hợp khi các yếu tố có thể tương tác.

### 6.3. Chính sách null phụ thuộc triển khai

`HashSet` cho phép phần tử null, nhưng chỉ có tối đa một phần tử null trong tập hợp. `HashMap` cho phép khóa null và giá trị null. Hai đặc tính này không được suy ra cho mọi `Set` hoặc mọi `Map`. [Tài liệu HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [tài liệu HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

`Set.of` từ chối null và từ chối phần tử trùng khi tạo tập hợp. `Map.of` từ chối khóa/giá trị null và khóa trùng khi tạo map. Khác với `HashMap.put`, truyền khóa trùng vào `Map.of` không phải yêu cầu ghi đè giá trị cũ. [Tài liệu Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [tài liệu Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

`ArrayDeque` không cho phép phần tử null. Khi kiểm thử hàng đợi, còn cần tách trạng thái rỗng khỏi chính sách phần tử; chẳng hạn `poll()` trả null khi không có phần tử để lấy. [Tài liệu ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)

### 6.4. Map: giá trị null khác với không có khóa

Với một `HashMap` có thể chứa giá trị null:

```text
get("known")   -> null, dù khóa "known" có ánh xạ
get("missing") -> null, vì không có khóa "missing"
```

Nếu yêu cầu cần phân biệt hai trạng thái, kiểm tra cả `containsKey()`. Chỉ `assertNull(map.get(key))` không phân biệt được chúng.

Đối với số dạng wrapper, một phần tử `Integer` null còn có thể gây `NullPointerException` khi được mở hộp để cộng hoặc so sánh bằng toán tử số. Kiểm thử cần đi qua thao tác sử dụng phần tử, không chỉ xác nhận collection chứa được null.

## 7. Ma trận kiểm thử cho hàm mẫu

Kết quả mong đợi dưới đây được viết từ hợp đồng ở mục 2, trước khi xem triển khai.

| Ca | Nguồn | Kỳ vọng | Lỗi có thể phát hiện |
| --- | --- | --- | --- |
| Rỗng | `[]` | `[]` | Truy cập phần tử đầu không tồn tại |
| Một phần tử | `["A"]` | `["A"]` | Bỏ phần tử duy nhất |
| Trùng liền nhau | `["A", "A"]` | `["A"]` | Không loại trùng |
| Trùng cách nhau | `["B", "A", "B", "C", "A"]` | `["B", "A", "C"]` | Chỉ loại trùng liền nhau; sai thứ tự |
| Null xen kẽ | `[null, "A", null, "B", "A"]` | `["A", "B"]` | Gọi phương thức trên null; sai chính sách kết hợp |
| Chỉ null | `[null, null]` | `[]` | Giữ null ngoài hợp đồng |
| Hai đối tượng bằng giá trị | Hai `new String("A")` | `["A"]` | Dùng `==` thay `equals()` |
| Không tự chuẩn hóa | `["", " ", "", "a", "A"]` | `["", " ", "a", "A"]` | Tự cắt khoảng trắng hoặc đổi hoa/thường |
| Cả nguồn null | `null` | `NullPointerException` | Nhầm nguồn null với nguồn rỗng |
| Nguồn và kết quả | Sửa cấu trúc từng bên sau khi gọi | Hai bên độc lập | Trả nguyên nguồn hoặc trả một view |

Để dễ chẩn đoán, các ca đầu nên tập trung vào một quy tắc. Sau đó dùng dữ liệu kết hợp để phát hiện lỗi tương tác. Một ca chứa tất cả yếu tố không thay thế tốt cho các ca có mục tiêu rõ ràng.

## 8. Chương trình Java 17+ tự chạy các kiểm tra

Chương trình dưới đây không cần thư viện bên ngoài. Nó dùng hàm kiểm tra ném `AssertionError` khi sai, nên không phụ thuộc việc bật tùy chọn `-ea` của từ khóa `assert` trong Java.

`LinkedHashSet` lưu các giá trị chưa gặp theo thứ tự duyệt đầu tiên; kết quả được chuyển sang một `ArrayList` mới. Việc triển khai ngắn không thay cho các kiểm tra về hợp đồng. [Tài liệu LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

Lưu thành `CollectionBoundaryDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CollectionBoundaryDemo {
    private static int passed;

    static List<String> distinctNonNull(Collection<String> source) {
        Objects.requireNonNull(source, "source");
        Set<String> unique = new LinkedHashSet<>();
        for (String value : source) {
            if (value != null) {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    private static void checkEquals(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected
                    + ", actual=" + actual);
        }
        passed++;
    }

    private static void expectThrows(String label,
                                     Class<? extends Throwable> expected,
                                     Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw new AssertionError(label + ": wrong exception", actual);
            }
            passed++;
            return;
        }
        throw new AssertionError(label + ": expected " + expected.getSimpleName());
    }

    public static void main(String[] args) {
        checkEquals("empty", List.of(), distinctNonNull(List.of()));
        checkEquals("singleton", List.of("A"), distinctNonNull(List.of("A")));
        checkEquals("adjacent duplicates", List.of("A"),
                distinctNonNull(List.of("A", "A")));
        checkEquals("separated duplicates and order", List.of("B", "A", "C"),
                distinctNonNull(List.of("B", "A", "B", "C", "A")));
        checkEquals("nulls and duplicates", List.of("A", "B"),
                distinctNonNull(Arrays.asList(null, "A", null, "B", "A")));
        checkEquals("only nulls", List.of(),
                distinctNonNull(Arrays.asList((String) null, (String) null)));
        checkEquals("equal values in separate objects", List.of("A"),
                distinctNonNull(List.of(new String("A"), new String("A"))));
        checkEquals("no extra normalization", List.of("", " ", "a", "A"),
                distinctNonNull(List.of("", " ", "", "a", "A")));
        expectThrows("null source", NullPointerException.class,
                () -> distinctNonNull(null));

        List<String> source = new ArrayList<>(Arrays.asList("A", null, "A", "B"));
        List<String> before = new ArrayList<>(source);
        List<String> result = distinctNonNull(source);
        checkEquals("source unchanged by call", before, source);
        checkEquals("result content", List.of("A", "B"), result);
        result.add("C");
        checkEquals("result is mutable", List.of("A", "B", "C"), result);
        checkEquals("result mutation does not change source", before, source);
        source.add("D");
        checkEquals("source mutation does not change result",
                List.of("A", "B", "C"), result);
        List<String> emptyResult = distinctNonNull(List.of());
        emptyResult.add("X");
        checkEquals("empty result is also mutable", List.of("X"), emptyResult);

        expectThrows("List.of rejects null", NullPointerException.class,
                () -> List.of("A", (String) null));
        expectThrows("Set.of rejects duplicates", IllegalArgumentException.class,
                () -> Set.of("A", "A"));
        expectThrows("Map.of rejects duplicate keys", IllegalArgumentException.class,
                () -> Map.of("A", 1, "A", 2));
        expectThrows("ArrayDeque rejects null", NullPointerException.class,
                () -> new ArrayDeque<String>().add(null));

        Set<String> nullableSet = new HashSet<>(
                Arrays.asList("A", "A", null, "B", null));
        checkEquals("HashSet removes duplicates including null", 3, nullableSet.size());
        checkEquals("HashSet contains null", true, nullableSet.contains(null));

        Map<String, String> nullableMap = new HashMap<>();
        nullableMap.put(null, "fallback");
        nullableMap.put("known", null);
        checkEquals("HashMap null key", "fallback", nullableMap.get(null));
        checkEquals("present null value", null, nullableMap.get("known"));
        checkEquals("missing key lookup", null, nullableMap.get("missing"));
        checkEquals("known key exists", true, nullableMap.containsKey("known"));
        checkEquals("missing key does not exist", false,
                nullableMap.containsKey("missing"));

        System.out.println("PASS: " + passed + " checks");
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CollectionBoundaryDemo.java
java CollectionBoundaryDemo
```

Stdout khi tất cả kiểm tra đạt:

```text
PASS: 26 checks
```

Nếu một kiểm tra sai, chương trình dừng bằng `AssertionError`, thay vì vẫn in dòng PASS. Những kiểm tra API ở phần sau `main` được tách về ý nghĩa với kiểm tra hàm mẫu: chúng xác nhận cách chuẩn bị dữ liệu và sự khác biệt giữa các collection cụ thể.

Việc bắt `Throwable` ở đây chỉ nằm trong một tiện ích kiểm thử nhỏ để kiểm tra kiểu lỗi được ném; không phải lời khuyên bắt mọi lỗi trong mã nghiệp vụ rồi bỏ qua. Thông báo exception của JVM không được dùng làm điều kiện đạt vì nội dung thông báo có thể khác giữa môi trường.

## 9. Chuyển cùng ma trận sang JUnit 5

Trong dự án đã có JUnit Jupiter, dùng kiểm thử tham số hóa để giữ một cấu trúc assertion và cung cấp nhiều cặp nguồn/kỳ vọng. `@MethodSource` thuận tiện khi mỗi ca có collection phức tạp hoặc chứa null; kiểm thử ngoại lệ nên nằm trong một phương thức có mục tiêu riêng. [Hướng dẫn JUnit 5 về MethodSource](https://docs.junit.org/5.10.2/user-guide/#writing-tests-parameterized-tests-sources-MethodSource)

Đoạn dưới là lớp kiểm thử minh họa cho môi trường có `junit-jupiter-api`, `junit-jupiter-params` và engine JUnit Jupiter khi chạy. Nó không phải chương trình độc lập chạy bằng lệnh `java` ở mục 8. Đặt cùng package với `CollectionBoundaryDemo` hoặc điều chỉnh khả năng truy cập của hàm trong mã thực tế.

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CollectionBoundaryTest {
    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(List.of(), List.of()),
                Arguments.of(List.of("A"), List.of("A")),
                Arguments.of(List.of("B", "A", "B", "C"), List.of("B", "A", "C")),
                Arguments.of(Arrays.asList(null, "A", null, "A"), List.of("A")),
                Arguments.of(Arrays.asList((String) null, (String) null), List.of()));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void followsValueContract(List<String> input, List<String> expected) {
        List<String> before = new ArrayList<>(input);
        List<String> actual = CollectionBoundaryDemo.distinctNonNull(input);
        assertEquals(expected, actual);
        assertEquals(before, input);
    }

    @Test
    void rejectsNullCollection() {
        assertThrows(NullPointerException.class,
                () -> CollectionBoundaryDemo.distinctNonNull(null));
    }
}
```

`assertEquals` với hai `List` kiểm tra nội dung theo thứ tự, nên phù hợp với hợp đồng giữ thứ tự. Các ca về khả năng sửa đổi và tính độc lập cấu trúc cần được chuyển thành kiểm thử riêng tương tự phần chương trình đầy đủ, thay vì nghĩ rằng bảng tham số trên đã bao phủ toàn bộ hợp đồng.

Nếu dùng `@NullSource`, cần nhớ nó cung cấp tham số null cho phương thức kiểm thử; nó không tự tạo ra một danh sách có một phần tử null. Hai trường hợp phải có dữ liệu nguồn khác nhau.

## 10. Chọn assertion theo ngữ nghĩa cần bảo vệ

### 10.1. List: nội dung, thứ tự và số lần xuất hiện

Nếu kết quả là danh sách có thứ tự, so sánh trực tiếp với một `List` kỳ vọng. Chỉ kiểm tra `size()` không phát hiện thay nhầm phần tử hoặc đảo thứ tự. Chỉ kiểm tra `contains()` không phát hiện thừa một bản sao hay mất một lần xuất hiện.

Ví dụ `["A", "A", "B"]` và `["A", "B", "B"]` có cùng kích thước và cùng tập giá trị, nhưng không có cùng số lần xuất hiện. Đừng chuyển cả hai sang `Set` rồi kết luận chúng tương đương nếu số lần lặp thuộc hợp đồng.

### 10.2. Set: không dựa vào thứ tự khi không được bảo đảm

Với tập hợp không có bảo đảm thứ tự, so sánh tập phần tử bằng `Set.equals()`. Không đòi `HashSet` in ra đúng một chuỗi theo thứ tự tình cờ thấy trên máy.

Nếu hợp đồng yêu cầu thứ tự duyệt, phải chọn collection có bảo đảm phù hợp và kiểm tra thứ tự duyệt riêng. `Set.equals()` tự nó không kiểm tra thứ tự.

### 10.3. Map: kiểm tra ánh xạ, không chỉ tập giá trị

Hai map có thể có cùng tập giá trị nhưng ghép chúng với khóa khác nhau. So sánh map hoặc kiểm tra từng ánh xạ cần thiết. Khi null được phép, thêm `containsKey()` nếu cần phân biệt thiếu khóa với khóa có giá trị null.

Đối với việc chèn lại cùng khóa, kiểm tra cả giá trị cuối, kích thước và giá trị cũ trả về nếu chúng thuộc hành vi cần bảo vệ. Không nhầm “trùng giá trị” với “trùng khóa”.

### 10.4. Ngoại lệ: khoanh vùng đúng hành động

Một kiểm thử “có NullPointerException” có thể đạt vì lỗi ở giai đoạn chuẩn bị dữ liệu, chứ không phải vì hàm cần kiểm thử đã từ chối nguồn.

Hãy tạo dữ liệu hợp lệ cho mục tiêu kiểm thử trước, rồi bao chính lời gọi cần kiểm tra trong `assertThrows`. Chỉ đặt thao tác tạo dữ liệu bên trong vùng kỳ vọng ngoại lệ khi chính factory đó là đối tượng đang được kiểm thử, như các kiểm tra `List.of` trong chương trình mẫu.

Không bắt exception rồi chỉ in ra thông báo. Nếu lỗi không xuất hiện hoặc sai loại, kiểm thử phải thất bại rõ ràng.

## 11. Những lỗi thiết kế kiểm thử thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Chỉ thử danh sách nhiều giá trị khác nhau | Bỏ sót nhánh rỗng, singleton, trùng và null | Lập các nhóm dữ liệu đại diện trước |
| Dùng `List.of` để tạo dữ liệu chứa null | Lỗi trước khi gọi hàm cần kiểm thử | Dùng cách tạo dữ liệu có chính sách null phù hợp |
| Chỉ kiểm tra kích thước | Không phát hiện sai phần tử/thứ tự | Kiểm tra nội dung theo ngữ nghĩa kết quả |
| Dùng `Set` để so mọi kết quả danh sách | Làm mất thông tin số lần xuất hiện và thứ tự | Giữ dạng `List` khi hợp đồng là danh sách |
| Tính expected bằng chính thuật toán đang kiểm thử | Cùng sai mà vẫn đạt | Viết ví dụ expected độc lập từ hợp đồng |
| Dùng chung một collection có thể sửa đổi cho nhiều ca | Ca trước làm thay đổi dữ liệu của ca sau | Tạo nguồn mới cho mỗi ca khi có khả năng bị sửa |
| Chỉ kiểm tra collection chấp nhận null | Bỏ sót lỗi khi sử dụng phần tử null | Chạy cả thao tác xử lý liên quan |
| Ép mọi hàm coi null như rỗng | Che lỗi vi phạm hợp đồng hoặc mất phân biệt nghiệp vụ | Giữ chính sách null đã quy định |
| Yêu cầu một loại triển khai cụ thể ngoài hợp đồng | Kiểm thử vỡ khi thay triển khai tương đương | Kiểm tra hành vi và bảo đảm đã công bố |

Một bản sao đầu vào bằng `new ArrayList<>(source)` đủ ghi lại cấu trúc và các tham chiếu để kiểm tra ví dụ String trong bài. Với phần tử có thể thay đổi, bản sao nông không giúp phát hiện mọi thay đổi bên trong đối tượng; cần chụp trạng thái giá trị cần bảo vệ hoặc dùng dữ liệu bất biến phù hợp.

## 12. Tự kiểm tra và bài tập

### Câu 1: Một hàm trả danh sách rỗng cho đầu vào null có luôn đúng không?

Không. Nó đúng khi hợp đồng quy định null tương đương không có phần tử. Hàm mẫu trong bài yêu cầu từ chối null, nên trả danh sách rỗng sẽ sai dù không có exception.

### Câu 2: Hàm loại trùng trả `["A", "B", "C"]` cho `["B", "A", "B", "C"]` có đạt không?

Không đạt hợp đồng mẫu vì đã đổi thứ tự gặp lần đầu. Nó có thể đạt một hợp đồng khác yêu cầu kết quả được sắp xếp. Assertion phải theo hợp đồng đang kiểm thử.

### Câu 3: `map.get(key) == null` có chứng minh không có khóa không?

Không đối với map cho phép giá trị null. Cần kiểm tra `containsKey(key)` nếu sự khác biệt đó có ý nghĩa.

### Câu 4: Kiểm thử dùng List.of("A", null) rồi thấy NullPointerException đã kiểm tra được hàm xử lý null chưa?

Chưa. Factory đã thất bại trước khi hàm nhận dữ liệu. Hãy tạo nguồn chứa null bằng API phù hợp và khoanh assertion quanh đúng hành động.

### Bài thực hành

1. Thêm ca một phần tử null, null chỉ ở đầu và null chỉ ở cuối cho hàm mẫu; viết kết quả kỳ vọng trước khi chạy.
2. Đổi hợp đồng mẫu thành “giữ cả phần tử null nhưng loại trùng”, rồi xác định chính xác các ca nào phải thay kỳ vọng. Không sửa kỳ vọng chỉ vì nhìn thấy kết quả mới từ chương trình.
3. Viết các ca cho hàm đếm số lần xuất hiện, trong đó `["A", "A", "B"]` phải thể hiện số đếm `A=2`, `B=1`; quyết định chính sách null trước.
4. Tạo một kiểm thử mà đầu vào chỉ một phần tử, sửa kết quả sau khi gọi, rồi xác nhận nguồn độc lập theo hợp đồng.

Bốn nhóm dữ liệu biên là điểm xuất phát để tìm giả định ẩn. Một bộ kiểm thử đáng tin còn bảo vệ nội dung, thứ tự, số lần xuất hiện, chính sách null và tác dụng phụ mà người gọi thực sự được hứa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-002">Kiểm thử hợp đồng equals–hashCode và Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử hợp đồng equals–hashCode và Comparator

## 1. Vì sao collection làm lộ lỗi của đối tượng?

Một đối tượng có thể in ra đúng dữ liệu nhưng vẫn không được tìm thấy trong `HashSet`. Hai bản ghi có thể khác nhau nhưng chỉ còn một phần tử khi đưa vào `TreeSet`. Một comparator có thể sắp xếp đúng vài số nhỏ rồi sai khi gặp giá trị biên.

Các hiện tượng đó thường bắt nguồn từ quan hệ bằng nhau hoặc thứ tự mà lớp đối tượng cung cấp. Collection dựa vào những quan hệ này để tìm kiếm, loại trùng, thay thế hoặc sắp xếp.

Cần kiểm thử hai lớp yêu cầu riêng:

1. **Luật của hợp đồng Java**: phản xạ, đối xứng, bắc cầu, tính nhất quán và các quan hệ giữa kết quả.
2. **Ý nghĩa của miền bài toán**: những trường nào xác định hai bản ghi bằng nhau, ưu tiên sắp xếp nào đúng, trường hợp hòa có được gộp thành một phần tử không.

Một phép `equals` chỉ so danh tính tham chiếu vẫn có thể thỏa các luật chung, nhưng sai nếu ứng dụng yêu cầu bằng nhau theo mã và phiên bản. Tương tự, comparator chỉ so điểm có thể hợp lệ về thứ tự nhưng chưa phù hợp để phân biệt bản ghi trong `TreeSet`.

## 2. Biến hợp đồng equals thành các ca kiểm thử

Với `a`, `b`, `c` là các tham chiếu khác `null` trong miền đang xét:

| Luật | Điều cần kiểm tra | Dữ liệu cần có |
| --- | --- | --- |
| Phản xạ | `a.equals(a)` là `true` | Mỗi đối tượng mẫu |
| Đối xứng | `a.equals(b)` bằng `b.equals(a)` | Cặp bằng nhau, cặp khác nhau; chú ý kế thừa nếu được hỗ trợ |
| Bắc cầu | Nếu `a.equals(b)` và `b.equals(c)` thì `a.equals(c)` | Ba đối tượng khác tham chiếu nhưng cùng giá trị được coi là bằng nhau |
| Nhất quán | Gọi lại cho cùng kết quả khi thông tin liên quan không đổi | Giữ nguyên trạng thái trong lúc kiểm tra |
| So với `null` | `a.equals(null)` là `false` | Đối tượng hợp lệ và `null` |

Đây là hợp đồng của [Object.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)). Không gọi `null.equals(...)`; đó là dereference một tham chiếu `null`, không phải kiểm tra luật equals.

### 2.1. Không để phép thử bắc cầu trở thành rỗng

Nếu bộ mẫu chỉ có các đối tượng khác nhau hoàn toàn, tiền đề `a.equals(b) && b.equals(c)` hầu như luôn sai. Vòng kiểm tra vẫn chạy và báo PASS nhưng chưa thực sự thử nhóm bằng nhau.

Hãy chủ động tạo:

- Ba instance khác nhau thuộc cùng một nhóm giá trị bằng nhau.
- Instance chỉ khác từng trường có ý nghĩa bằng nhau.
- Instance chỉ khác trường không tham gia bằng nhau, nếu mô hình có trường đó.
- Kiểu khác, đối tượng lớp con hoặc lớp cha nếu thiết kế cho phép và cần hỗ trợ chúng.

Cặp “hai biến cùng trỏ vào một đối tượng” không thay thế được cặp “hai đối tượng độc lập có cùng giá trị”. Nhiều lỗi chỉ xuất hiện khi dữ liệu được đọc lại từ tệp, cơ sở dữ liệu hoặc API thành instance mới.

### 2.2. Kỳ vọng nghiệp vụ phải độc lập với mã đang kiểm thử

Nếu lớp `Key(id, score)` được định nghĩa bằng nhau khi cả `id` và `score` bằng nhau, hãy ghi rõ kỳ vọng:

```text
Key(A, 0) và Key(A, 0): bằng nhau, dù khác instance
Key(A, 0) và Key(B, 0): khác nhau
Key(A, 0) và Key(A, 1): khác nhau
```

Không tạo expected bằng cách gọi lại chính `equals` hoặc dùng một Set làm “chuẩn đúng” duy nhất. Khi đó phép thử có thể chỉ lặp lại lỗi của quan hệ đang cần kiểm tra.

## 3. Kiểm thử hashCode đúng chiều suy luận

Quan hệ bắt buộc là:

```text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
```

Chiều ngược lại không đúng. Hai đối tượng khác nhau có thể có cùng mã băm; đó là va chạm băm (hash collision), không tự động là lỗi hợp đồng.

Trong cùng một lần thực thi, gọi lại `hashCode` phải cho cùng kết quả khi thông tin dùng cho equals không đổi. Không kiểm thử rằng giá trị hash cụ thể phải giống giữa mọi lần chạy JVM. [Hợp đồng Object.hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#hashCode()).

### Các assertion nên tránh

- `a` khác `b` nên bắt buộc hash khác nhau.
- Hash của đối tượng này luôn phải là một con số đã chụp từ lần chạy trước, khi API không cam kết thuật toán cụ thể.
- Hai đối tượng cùng hash nên phải equals.
- Chỉ cần hash phân bố đẹp là quan hệ equals đã đúng.

Chất lượng phân bố hash là vấn đề hiệu năng riêng. Một hàm luôn trả `0` có thể vẫn đúng hợp đồng nếu equals đúng, nhưng gây nhiều va chạm và thường là lựa chọn hiệu năng kém.

### Kiểm tra qua collection băm

Từ hai instance khác nhau nhưng bằng nhau theo hợp đồng:

1. Thêm instance thứ nhất vào `HashSet`.
2. Thêm instance thứ hai và kiểm tra kích thước không tăng.
3. Dùng instance bằng nhau khác để `contains` và `remove`.
4. Với `HashMap`, đặt giá trị bằng khóa thứ nhất, tra hoặc thay thế bằng khóa bằng nhau, rồi kiểm tra Map vẫn chỉ có một ánh xạ cho khóa đó.

Các phép thử này xác nhận hành vi tích hợp mà ứng dụng thực sự sử dụng. Chúng bổ sung cho kiểm tra luật trực tiếp, không thay thế các fixture nghiệp vụ.

## 4. Comparator: kiểm tra dấu, không học thuộc -1 và 1

`compare(a, b)` trả một số âm, bằng không hoặc dương. Hợp đồng không bắt buộc mọi comparator chỉ trả đúng `-1`, `0`, `1`. Khi kiểm tra quan hệ, dùng dấu của kết quả.

Đặt `s(a,b) = Integer.signum(comparator.compare(a,b))`. Các yêu cầu cốt lõi là:

| Thuộc tính | Phép kiểm tra |
| --- | --- |
| So với chính mình | `compare(a,a) == 0` |
| Đổi thứ tự thì đổi dấu | `s(a,b) == -s(b,a)` |
| Bắc cầu của thứ tự | Nếu `compare(a,b) > 0` và `compare(b,c) > 0` thì `compare(a,c) > 0` |
| Nhóm được coi là hòa phải ứng xử giống nhau | Nếu `compare(a,b) == 0` thì `s(a,c) == s(b,c)` với mọi `c` trong miền |
| Ổn định khi đầu vào và tiêu chí không đổi | Gọi lại không tự đổi quan hệ nhỏ hơn/bằng/lớn hơn |

Nếu phép so sánh với một cặp ném ngoại lệ, đổi thứ tự cặp đó cũng phải ném ngoại lệ theo hợp đồng. Cần kiểm thử riêng miền không hỗ trợ, chẳng hạn `null`, theo chính sách đã công bố. [Hợp đồng Comparator.compare](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html#compare(T,T)).

Không kiểm tra bằng cách viết `compare(a,b) == -compare(b,a)` trên các giá trị nguyên thô. Đó là yêu cầu mạnh hơn hợp đồng, và phép đổi dấu còn có thể tràn ở `Integer.MIN_VALUE`. Chuẩn hóa về dấu rồi đối chiếu tránh lỗi kiểm thử này.

### 4.1. Lỗi phép trừ chỉ hiện rõ ở giá trị biên

Comparator sau không an toàn:

```java
Comparator<Integer> broken = (left, right) -> left - right;
```

Khi phép trừ tràn `int`, dấu có thể sai. Với `left = Integer.MIN_VALUE`, `right = 0`, kết quả âm; đổi chiều tính `0 - Integer.MIN_VALUE` cũng bị tràn và vẫn âm. Hai chiều không còn đổi dấu đúng.

Dùng `Integer.compare(left, right)`, `Comparator.naturalOrder()` hoặc `Comparator.comparingInt(...)` phù hợp với kiểu dữ liệu. Với `long`, dùng cơ chế so sánh cho `long` thay vì ép hiệu xuống `int`.

### 4.2. Kiểm tra ưu tiên và cách xử lý hòa

Comparator `comparingInt(Key::score).thenComparing(Key::id)` nghĩa là so điểm trước; chỉ khi điểm bằng nhau mới so mã. Hãy chuẩn bị các cặp có cùng điểm, khác mã, cùng mã nhưng khác điểm, và các cực trị của điểm.

Thêm `thenComparing` chỉ đúng nếu tiêu chí phụ phù hợp yêu cầu. Không thể khẳng định một chuỗi comparator bất kỳ nhất quán với equals chỉ vì nó có tiêu chí phụ; phải kiểm tra nhóm `compare == 0` có trùng nhóm equals hay không.

## 5. Nhất quán với equals là một yêu cầu cần kiểm tra riêng

Điều kiện nhất quán với equals là:

```text
(compare(a,b) == 0) == a.equals(b)
```

Comparator nói chung có thể không nhất quán với equals và vẫn thỏa các luật thứ tự. Nhưng khi dùng cho `TreeSet` hoặc khóa của `TreeMap`, việc không nhất quán có thể làm collection không tuân thủ hợp đồng chung của Set/Map dựa trên equals. `TreeSet` xác định hai phần tử thuộc cùng vị trí bằng phép so sánh, không bằng một lần gọi equals độc lập. [Tài liệu TreeSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html).

Ví dụ hai `Key` có cùng điểm `0` nhưng mã `A` và `B`:

- `equals` theo cả hai trường cho `false`.
- Comparator chỉ theo điểm cho `0` và có thể vẫn hợp lệ về các luật comparator.
- `TreeSet` dùng comparator chỉ theo điểm sẽ giữ một phần tử từ hai lần thêm đó.

Nếu mục đích chỉ là sắp xếp List theo điểm, việc hòa giữa các mã có thể là chủ ý. Nếu cần lưu cả hai mã trong Set, cần chọn quan hệ thứ tự phù hợp với danh tính mà Set phải biểu diễn.

### BigDecimal là ví dụ có thật

`new BigDecimal("1.0")` và `new BigDecimal("1.00")` bằng nhau theo giá trị số khi dùng `compareTo`, nhưng `equals` còn phân biệt scale. Vì vậy tập băm và tập cây theo thứ tự tự nhiên có thể có số phần tử khác nhau khi đưa hai giá trị này vào. Đây là hành vi đã được công bố, không phải bằng chứng collection bị hỏng. [Tài liệu BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).

Trong kiểm thử, cần xác định ứng dụng coi scale là thông tin có ý nghĩa hay chỉ quan tâm giá trị số. Không thay quan hệ đó một cách ngầm định khi chuyển loại collection.

## 6. Chương trình Java 17+ kiểm thử bằng các luật

Chương trình sau dùng hàm `check` ném `AssertionError`, vì vậy không cần cờ `-ea`. Nó không cần JUnit hoặc thư viện ngoài. Lưu thành `ContractChecks.java`.

Hợp đồng ví dụ: `Key` có `id` khác `null`; cả `id` và `score` tham gia equals. Comparator đầy đủ so `score` rồi `id`. Các mẫu được giữ nguyên trong suốt quá trình kiểm tra.

```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ContractChecks {
    record Key(String id, int score) {
        Key {
            Objects.requireNonNull(id);
        }
    }

    record CollisionKey(int value) {
        @Override
        public int hashCode() {
            return 0;
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static <T> void verifyEquality(List<T> samples) {
        for (T a : samples) {
            check(a != null, "use non-null equality samples");
            check(a.equals(a), "reflexivity: " + a);
            check(!a.equals(null), "null equality: " + a);
            int hash = a.hashCode();
            for (T b : samples) {
                boolean equal = a.equals(b);
                check(equal == b.equals(a), "symmetry: " + a + ", " + b);
                if (equal) {
                    check(a.hashCode() == b.hashCode(), "equal objects need equal hashes");
                }
                for (int repeat = 0; repeat < 3; repeat++) {
                    check(a.equals(b) == equal, "unstable equality");
                    check(a.hashCode() == hash, "unstable hash without mutation");
                }
                for (T c : samples) {
                    if (equal && b.equals(c)) {
                        check(a.equals(c), "transitivity: " + a + ", " + b + ", " + c);
                    }
                }
            }
        }
    }

    static <T> void verifyComparator(
            List<T> samples, Comparator<T> comparator, boolean consistentWithEquals) {
        for (T a : samples) {
            check(comparator.compare(a, a) == 0, "compare self");
            for (T b : samples) {
                int ab = Integer.signum(comparator.compare(a, b));
                int ba = Integer.signum(comparator.compare(b, a));
                check(ab == -ba, "opposite signs: " + a + ", " + b);
                if (consistentWithEquals) {
                    check((ab == 0) == Objects.equals(a, b), "compare zero versus equals");
                }
                for (int repeat = 0; repeat < 3; repeat++) {
                    check(Integer.signum(comparator.compare(a, b)) == ab, "unstable order");
                }
                for (T c : samples) {
                    int bc = Integer.signum(comparator.compare(b, c));
                    int ac = Integer.signum(comparator.compare(a, c));
                    if (ab > 0 && bc > 0) {
                        check(ac > 0, "order transitivity: " + a + ", " + b + ", " + c);
                    }
                    if (ab == 0) {
                        check(ac == bc, "zero group must compare alike against third value");
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        Key a = new Key("A", 0);
        Key b = new Key("A", 0);
        Key c = new Key("A", 0);
        Key otherId = new Key("B", 0);
        Key otherScore = new Key("A", 1);
        List<Key> samples = List.of(
                a, b, c, otherId, otherScore,
                new Key("MIN", Integer.MIN_VALUE),
                new Key("MAX", Integer.MAX_VALUE));

        check(a != b && a.equals(b), "equal values must include distinct instances");
        check(!a.equals(otherId), "id belongs to equality");
        check(!a.equals(otherScore), "score belongs to equality");
        check(!a.equals("A"), "foreign type");
        verifyEquality(samples);
        System.out.println("equals/hashCode=PASS");

        Set<Key> hashSet = new HashSet<>();
        check(hashSet.add(a), "first insertion");
        check(!hashSet.add(b), "equal insertion must not grow set");
        check(hashSet.contains(c), "lookup through equal distinct object");
        check(hashSet.remove(c) && hashSet.isEmpty(), "remove through equal object");

        Map<Key, String> map = new HashMap<>();
        map.put(a, "first");
        check("first".equals(map.put(b, "found")) && map.size() == 1, "equal key replaces value");
        check("found".equals(map.get(c)), "map lookup through equal object");
        check("found".equals(map.remove(c)) && map.isEmpty(), "map remove through equal object");
        System.out.println("hash collection lookup=PASS");

        Comparator<Key> byScore = Comparator.comparingInt(Key::score);
        Comparator<Key> byScoreThenId = byScore.thenComparing(Key::id);
        verifyComparator(samples, byScore, false);
        verifyComparator(samples, byScoreThenId, true);
        System.out.println("comparator laws=PASS");

        List<Key> sorted = new ArrayList<>(List.of(otherScore, otherId, a));
        sorted.sort(byScoreThenId);
        check(sorted.equals(List.of(a, otherId, otherScore)), "expected business order");
        System.out.println("ordered=" + sorted.stream()
                .map(key -> key.id() + ":" + key.score())
                .toList());

        Set<Key> coarse = new TreeSet<>(byScore);
        coarse.add(a);
        coarse.add(otherId);
        Set<Key> precise = new TreeSet<>(byScoreThenId);
        precise.add(a);
        precise.add(otherId);
        System.out.println("score-only tree size=" + coarse.size());
        System.out.println("score-and-id tree size=" + precise.size());

        List<Key> nullableSamples = new ArrayList<>(samples);
        nullableSamples.add(null);
        verifyComparator(nullableSamples, Comparator.nullsFirst(byScoreThenId), true);
        System.out.println("nullsFirst policy=PASS");

        boolean rejected = false;
        try {
            verifyComparator(
                    List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE),
                    (left, right) -> left - right,
                    true);
        } catch (AssertionError ex) {
            rejected = true;
        }
        check(rejected, "subtraction comparator should fail on boundary values");
        System.out.println("subtraction comparator rejected=" + rejected);

        CollisionKey x = new CollisionKey(1);
        CollisionKey y = new CollisionKey(2);
        verifyEquality(List.of(x, new CollisionKey(1), y));
        Set<CollisionKey> collisions = new HashSet<>(List.of(x, y));
        check(collisions.size() == 2, "collision must not merge unequal objects");
        System.out.println("collision set size=" + collisions.size());

        BigDecimal one = new BigDecimal("1.0");
        BigDecimal anotherOne = new BigDecimal("1.00");
        System.out.println("decimal equals=" + one.equals(anotherOne));
        System.out.println("decimal compare zero=" + (one.compareTo(anotherOne) == 0));
        System.out.println("decimal hash set size=" + new HashSet<>(List.of(one, anotherOne)).size());
        System.out.println("decimal tree set size=" + new TreeSet<>(List.of(one, anotherOne)).size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 ContractChecks.java
java ContractChecks
```

Stdout chính xác:

```text
equals/hashCode=PASS
hash collection lookup=PASS
comparator laws=PASS
ordered=[A:0, B:0, A:1]
score-only tree size=1
score-and-id tree size=2
nullsFirst policy=PASS
subtraction comparator rejected=true
collision set size=2
decimal equals=false
decimal compare zero=true
decimal hash set size=2
decimal tree set size=1
```

### 6.1. Mỗi nhóm kiểm thử chứng minh điều gì?

- Ba instance `Key("A", 0)` giúp phép thử có nhóm bằng nhau thực sự để kiểm tra bắc cầu, đối xứng và hash.
- Các ca chỉ khác `id` hoặc `score` ghi rõ kỳ vọng nghiệp vụ, thay vì chỉ kiểm tra các luật trừu tượng.
- `HashSet` và `HashMap` được truy cập qua các instance bằng nhau nhưng khác tham chiếu; Map còn kiểm tra thay giá trị và xóa khóa bằng nhau.
- Comparator chỉ theo điểm được kiểm tra các luật với cờ `false`, vì không yêu cầu nó nhất quán với equals trong phép kiểm tra đó.
- Comparator theo điểm rồi mã được kiểm tra với cờ `true`, bổ sung điều kiện `compare == 0` tương đương equals.
- Danh sách đã sắp xếp được đối chiếu với thứ tự kỳ vọng viết độc lập, không tạo expected bằng cách sắp xếp lại bằng cùng comparator.
- `CollisionKey` cố ý trả hash `0` cho mọi giá trị nhưng vẫn phân biệt giá trị trong equals. Set phải giữ cả `1` và `2`.
- Comparator dùng phép trừ bị kiểm tra với cực trị và bị phát hiện vi phạm; chương trình không chờ thuật toán sort tự ném lỗi.

### 6.2. Giới hạn của chương trình

Bộ kiểm tra chỉ xét hữu hạn mẫu. PASS không chứng minh hợp đồng đúng với mọi đối tượng có thể tạo ra. Việc gọi lại ba lần chỉ là kiểm tra thực hành về ổn định trên mẫu, không chứng minh một hàm không phụ thuộc trạng thái ẩn.

Hai hàm có vòng ba tầng, với chi phí O(s³) theo số mẫu `s`. Chúng dành cho tập fixture nhỏ trong kiểm thử, không dành để chạy trên hàng triệu đối tượng ở đường xử lý chính.

`verifyEquality` nhận mẫu khác `null` và kiểm tra `a.equals(null)` bên trong. `verifyComparator` coi các mẫu là miền được phép so; comparator từ chối `null` phải có ca kiểm thử ngoại lệ riêng. Bộ kiểm tra này không tự sinh mọi kiểu ngoại lệ hoặc mọi lớp khác có thể truyền vào equals.

## 7. Chính sách null và tính ổn định của dữ liệu

### 7.1. Comparator cho phép null hay từ chối null?

`Comparator.nullsFirst(base)` và `nullsLast(base)` thêm chính sách cho các tham chiếu phần tử `null`. Trong ví dụ, `nullsFirst` được kiểm tra cả với `null` và các `Key` hợp lệ.

Cần phân biệt phần tử `null` với trường bên trong phần tử là `null`. Bọc comparator bên ngoài bằng `nullsFirst` không tự làm một phép lấy `key.id()` chứa `null` trở nên an toàn với so sánh chuỗi tự nhiên. Nếu trường được phép `null`, cần comparator cho chính trường đó hoặc quy tắc chuẩn hóa rõ ràng.

Nếu chính sách là từ chối `null`, kiểm tra đúng ngoại lệ cho cả `compare(null, a)` và `compare(a, null)` theo hợp đồng. Không biến ca ngoại lệ dự kiến thành “test bị bỏ qua”.

### 7.2. Không dùng kết quả sau khi làm hỏng điều kiện sử dụng collection

Một đối tượng có thể thay đổi các trường được dùng cho equals/hashCode. Nhưng sửa các trường đó khi đối tượng đang là phần tử của Set băm hoặc khóa của Map có thể phá điều kiện mà collection dựa vào. Tài liệu Set cảnh báo hành vi không được xác định nếu thay đổi phần tử làm ảnh hưởng equals khi nó còn ở trong Set. [Tài liệu Set](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html).

Tương tự, thay đổi trường sắp xếp khi phần tử đang nằm trong cấu trúc cây có thể làm thứ tự lưu trữ không còn phản ánh comparator hiện tại. Không viết một phép thử đòi `contains` sau thao tác sử dụng sai đó phải luôn trả đúng một giá trị cụ thể.

Cách kiểm thử thiết kế thường hữu ích hơn:

- Khóa bất biến được tạo đúng và không lộ đối tượng thành phần có thể bị sửa ngoài ý định.
- Nếu cho phép đổi khóa, luồng nghiệp vụ xóa đối tượng khỏi collection trước khi đổi rồi chèn lại đúng cách.
- Các trường chỉ là dữ liệu mô tả có hoặc không tham gia bằng nhau theo hợp đồng đã chọn, và kiểm thử phản ánh điều đó.

## 8. Mở rộng thành kiểm thử dựa trên thuộc tính

Kiểm thử dựa trên thuộc tính (property-based testing) sinh nhiều đầu vào rồi kiểm tra các luật thay vì chỉ vài ví dụ. Để có giá trị, bộ sinh phải chủ động tạo các cấu trúc khó:

1. Sinh một giá trị nền rồi tạo các instance bằng nhau độc lập.
2. Sinh biến thể chỉ khác từng trường có ý nghĩa.
3. Tăng xác suất giá trị biên: `MIN_VALUE`, `MAX_VALUE`, `-1`, `0`, `1`, chuỗi rỗng và độ dài lớn nếu miền cho phép.
4. Sinh cặp hòa theo tiêu chí chính nhưng khác tiêu chí phụ của comparator.
5. Thêm `null` chỉ khi chính sách cho phép; nếu không, kiểm tra miền bị từ chối bằng ca ngoại lệ.
6. Với quan hệ kế thừa được hỗ trợ, sinh cả đối tượng lớp cha/lớp con để kiểm tra đối xứng và bắc cầu giữa chúng.

Ba đối tượng ngẫu nhiên hoàn toàn thường không bằng nhau nên có thể bỏ lỡ nhiều lỗi equals. Cần bộ sinh các nhóm bằng nhau, không chỉ bộ sinh giá trị lẻ.

Lưu seed hoặc tập đầu vào làm lỗi và thu nhỏ về cặp/bộ ba tối thiểu. Thông báo lỗi nên chứa đối tượng, các trường liên quan và dấu kết quả so sánh. Sau khi sửa, giữ ca nhỏ đó như kiểm thử hồi quy ổn định. Không dùng dữ liệu ngẫu nhiên thay đổi khó tái lập làm bằng chứng duy nhất.

Khi đưa vào JUnit, có thể tách từng nhóm luật thành test riêng hoặc test tham số hóa để lỗi cho biết đúng thuộc tính nào thất bại. Các helper cần áp vào lớp và comparator thật của ứng dụng; kiểm thử chỉ một record minh họa không xác nhận các lớp khác trong hệ thống.

## 9. Những phép thử có thể PASS nhưng vẫn bỏ sót lỗi

| Cách kiểm tra thiếu | Vấn đề bị bỏ sót | Cách bổ sung |
| --- | --- | --- |
| Chỉ gọi `a.equals(a)` | Cách so danh tính có thể qua dù sai yêu cầu bằng nhau theo giá trị | Hai instance độc lập cùng giá trị và các biến thể từng trường |
| Chỉ kiểm tra cặp khác nhau | Luật hash khi bằng nhau và bắc cầu có thể không được kích hoạt | Nhóm ba instance bằng nhau |
| Bắt buộc hash của hai giá trị khác nhau phải khác | Đánh trượt va chạm hợp lệ | Chỉ suy từ equals sang hash; thêm ca va chạm qua collection |
| Chỉ sort một danh sách rồi kiểm tra không có lỗi | Nhiều comparator sai không bị phát hiện ở đầu vào đó | Kiểm tra luật theo cặp/bộ ba và thứ tự nghiệp vụ |
| So kết quả compare với đúng `-1` hoặc `1` | Đặt yêu cầu mạnh hơn hợp đồng | So dấu |
| Chỉ dùng các số nhỏ | Lỗi tràn số của phép trừ không lộ | Các cực trị và cặp nằm hai phía miền số |
| Comparator qua luật nên dùng được cho mọi TreeSet | Bỏ qua nhóm `compare == 0` khác nhóm equals | Kiểm tra tính nhất quán với equals và kích thước/tra cứu Set |
| So hai comparator bằng `equals` để suy cách sắp xếp giống nhau | Hai comparator tương đương không bắt buộc phải equals | Đối chiếu quan hệ thứ tự trên mẫu khi đó là mục tiêu test |

Comparator vi phạm hợp đồng có thể bị một thuật toán sắp xếp phát hiện và báo lỗi, nhưng việc không nhận ngoại lệ không phải chứng minh comparator hợp lệ. Hợp đồng API còn coi việc phát hiện vi phạm là tùy chọn ở một số phép sort. [Tài liệu Arrays.sort](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html).

## 10. Bài thực hành

Một lớp `Student` có `studentId`, `displayName` và `score`. Yêu cầu bằng nhau theo `studentId`, còn giao diện cần hiển thị theo điểm giảm dần rồi tên.

1. Viết nhóm fixture để phân biệt luật equals/hashCode với ý nghĩa “cùng sinh viên”. Nếu chỉ đổi tên/điểm mà cùng mã, equals và hash cần quan hệ gì?
2. Kiểm tra comparator hiển thị theo luật và theo thứ tự kỳ vọng, gồm điểm hòa và tên hòa.
3. Hai bản ghi cùng sinh viên nhưng khác điểm có thể được comparator hiển thị phân biệt. Comparator đó có nhất quán với equals không?
4. Nếu cần Set theo danh tính sinh viên, có nên dùng trực tiếp comparator hiển thị cho `TreeSet` không? Xác định cấu trúc hoặc quan hệ phù hợp trước khi viết assertion.
5. Bổ sung các ca biên, chính sách `null` và ca hồi quy cho lỗi đã tìm thấy.

Một bộ kiểm thử tốt nêu rõ vì sao hai đối tượng bằng nhau hoặc vì sao một đối tượng phải đứng trước đối tượng khác. Các luật giúp bắt lỗi cấu trúc; fixture nghiệp vụ giúp giữ đúng ý nghĩa mà ứng dụng cần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-003">Kiểm thử thứ tự chèn, thứ tự sắp xếp và thứ tự ưu tiên</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử thứ tự chèn, thứ tự sắp xếp và thứ tự ưu tiên

## 1. “Đúng phần tử” chưa chắc đã “đúng thứ tự”

Một kết quả chứa đủ `A`, `B`, `C` vẫn có thể sai nếu người gọi cần nhận `B`, `A`, `C`. Ngược lại, một kiểm thử ép thứ tự `A`, `B`, `C` có thể sai nếu collection không hứa thứ tự đó.

Khi kiểm thử collection, cần phân biệt ba ý nghĩa:

- **Thứ tự chèn (insertion order):** thứ tự các phần tử hoặc khóa được thêm vào, theo quy tắc của cấu trúc.
- **Thứ tự sắp xếp (sorted order):** thứ tự do `Comparable` hoặc `Comparator` quyết định.
- **Thứ tự ưu tiên (priority order):** thứ tự lấy phần tử ra từ hàng đợi theo quy tắc ưu tiên.

Với dữ liệu chèn lần lượt `30`, `10`, `20`:

| Cấu trúc và hành động quan sát | Thứ tự cần kỳ vọng |
| --- | --- |
| Duyệt `LinkedHashSet` | `30, 10, 20` |
| Duyệt `TreeSet<Integer>` dùng thứ tự tự nhiên | `10, 20, 30` |
| Liên tục `poll()` trên `PriorityQueue<Integer>` dùng thứ tự tự nhiên | `10, 20, 30` |
| Duyệt `PriorityQueue` bằng iterator | Không có bảo đảm duyệt thành dãy đã sắp xếp |

Hai dòng cuối đặc biệt dễ bị nhầm. `PriorityQueue` bảo đảm lựa chọn phần tử đầu hàng đợi theo thứ tự ưu tiên; nó không biến mọi cách quan sát nội dung thành một dãy đã sắp xếp.

Mục tiêu kiểm thử là bảo vệ **thứ tự tại đúng điểm quan sát mà API hoặc nghiệp vụ cam kết**, đồng thời kiểm tra đủ phần tử và số lần xuất hiện.

## 2. Xác định hợp đồng trước khi chọn assertion

Một yêu cầu như “trả kết quả theo đúng thứ tự” còn thiếu thông tin. Cần làm rõ:

1. Thứ tự của phần tử, của khóa hay của giá trị?
2. Quan sát qua iterator, danh sách đầu ra hay chuỗi lấy-xóa phần tử?
3. Tăng dần hay giảm dần theo thuộc tính nào?
4. Nếu hai phần tử ngang nhau, có quy tắc phụ không?
5. Thêm lại, cập nhật hoặc truy cập một phần tử có làm đổi vị trí không?
6. Việc kiểm tra có được tiêu thụ cấu trúc nguồn không?

Ví dụ hai hợp đồng hàng đợi khác nhau:

```text
Hợp đồng A:
    số priority lớn hơn được xử lý trước;
    các việc có cùng priority được lấy theo thứ tự bất kỳ.

Hợp đồng B:
    số priority lớn hơn được xử lý trước;
    nếu bằng priority thì việc được đưa vào sớm hơn được xử lý trước.
```

Không được dùng assertion yêu cầu thứ tự cụ thể giữa các việc đồng hạng khi chỉ có hợp đồng A. Với hợp đồng B, phải có một cơ chế thể hiện thứ tự đưa vào và phải kiểm tra nó.

Trong các ví dụ của bài, `priority` càng lớn nghĩa là càng ưu tiên. Đây là lựa chọn nghiệp vụ của ví dụ, không phải cách Java tự diễn giải một trường có tên `priority`.

## 3. Kiểm thử thứ tự chèn

### 3.1. Chọn đầu vào có thể phân biệt thứ tự chèn với thứ tự sắp xếp

Nếu luôn chèn `A`, `B`, `C`, một cấu trúc vô tình sắp xếp tăng dần vẫn trả đúng dãy mong đợi. Hãy dùng dữ liệu như `B`, `A`, `C`.

Với `LinkedHashMap` theo thứ tự chèn mặc định:

```text
put(B, 2)
put(A, 1)
put(C, 3)

Thứ tự duyệt khóa mong đợi: B, A, C
```

Thay giá trị của khóa `A` đang tồn tại không chuyển khóa đó xuống cuối. Nhưng xóa khóa rồi thêm lại là một chuỗi thao tác khác: khóa được thêm mới ở cuối. [Tài liệu LinkedHashMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

Vì vậy nên có những ca phân biệt:

| Hành động sau khi có `B, A, C` | Thứ tự khóa mong đợi trong chế độ insertion-order |
| --- | --- |
| `put(A, giá_trị_mới)` | `B, A, C` |
| `get(B)` | `B, A, C` |
| `remove(A)`, rồi `put(A, ...)` | `B, C, A` |
| Thêm khóa `D` | `B, A, C, D` |

Kết quả kiểm thử phải theo đúng trạng thái trước từng ca. Tạo dữ liệu riêng hoặc khởi tạo lại khi các ca không được thiết kế để nối tiếp nhau.

### 3.2. LinkedHashSet: thêm trùng không phải xóa rồi thêm lại

Với `LinkedHashSet`, thêm một phần tử đã tồn tại không thay đổi thứ tự. Nguồn `B, A, B, C` tạo thứ tự duyệt `B, A, C`. Nếu xóa `A` rồi thêm lại, thứ tự trở thành `B, C, A`. [Tài liệu LinkedHashSet của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

Một ca chỉ so sánh tập giá trị không phát hiện được lỗi thứ tự này. Cần thu thứ tự duyệt thành `List` để so sánh.

### 3.3. Access-order là một chế độ khác

`LinkedHashMap` có constructor bật thứ tự truy cập:

```java
Map<String, Integer> cache = new LinkedHashMap<>(16, 0.75f, true);
```

Đây là đoạn minh họa cần import `java.util.Map` và `java.util.LinkedHashMap`. Với chế độ này, một lần `get` thành công có thể đưa mục được truy cập về phía được truy cập gần nhất. Ví dụ sau khi chèn `B, A, C`, gọi `get(B)` cho thứ tự duyệt `A, C, B`.

Không áp kỳ vọng của insertion-order cho một map được tạo ở chế độ access-order. Khi kiểm thử cấu trúc dùng làm cache, còn phải để ý rằng một lệnh đọc để “kiểm tra giá trị” có thể thay đổi trạng thái thứ tự trước assertion tiếp theo.

## 4. Kiểm thử thứ tự sắp xếp

### 4.1. TreeSet và TreeMap dựa trên quy tắc so sánh

`TreeSet` duyệt theo thứ tự tự nhiên hoặc comparator được cấu hình. `TreeMap` sắp xếp theo **khóa**, không tự sắp xếp theo giá trị. [Tài liệu TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html), [tài liệu TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

Một ca có khả năng phân biệt tốt:

```text
Các ánh xạ được đưa vào:
    B -> 2
    A -> 3
    C -> 1

Duyệt TreeMap theo khóa tự nhiên:
    khóa:    A, B, C
    giá trị: 3, 2, 1
```

Nếu chỉ dùng `A->1`, `B->2`, `C->3`, ta khó phát hiện nhầm lẫn giữa “sắp theo khóa” và “sắp theo giá trị”.

Các ca nên có đầu vào đã tăng, đã giảm và trộn thứ tự, nhưng kỳ vọng phải cùng tuân theo comparator. Không cần giữ thứ tự chèn trong cấu trúc đã cam kết sắp xếp.

### 4.2. Kiểm tra hướng so sánh và quy tắc phụ

Với số nguyên dùng thứ tự tự nhiên, tăng dần thường là điều mong đợi. Với cấu trúc dùng `Comparator.reverseOrder()`, thứ tự mong đợi đổi thành giảm dần.

Đối với đối tượng nghiệp vụ, nên có ít nhất:

- Hai phần tử khác nhau ở thuộc tính chính.
- Hai phần tử bằng nhau ở thuộc tính chính nhưng khác ở thuộc tính phụ.
- Các giá trị biên có ý nghĩa, nếu comparator xử lý số hoặc dữ liệu đặc biệt.

Không viết comparator số bằng phép trừ như `a.priority() - b.priority()`. Hiệu có thể tràn số nguyên và làm sai dấu so sánh. Dùng `Integer.compare`, `Comparator.comparingInt` hoặc hàm kết hợp phù hợp. API `Comparator` cung cấp các cách xây dựng, đảo và nối quy tắc so sánh. [Tài liệu Comparator của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)

### 4.3. compare bằng 0 có ý nghĩa đặc biệt với cấu trúc cây

`TreeSet` dùng kết quả so sánh để nhận diện phần tử cùng vị trí trong tập. Nếu comparator chỉ so độ dài chuỗi, `"AA"` và `"BB"` so sánh bằng `0`; tập có thể chỉ giữ một trong hai dù `String.equals()` trả false.

Điều đó có thể làm bộ kiểm thử thất bại không phải vì dãy bị sắp sai, mà vì đã mất phần tử theo một hợp đồng không phù hợp. Kiểm tra cả số lượng và nội dung, không chỉ tính tăng dần.

Đối với tập hợp/map sắp xếp cần tuân theo hợp đồng `Set`/`Map` thông thường, quy tắc thứ tự phải nhất quán với `equals`. Nếu bài toán muốn giữ nhiều đối tượng khác nhau cùng giá trị thuộc tính chính, có thể cần một quy tắc phụ đủ phân biệt hoặc một cấu trúc khác như danh sách được sắp xếp.

### 4.4. Tính ổn định của List.sort không tự truyền sang hàng đợi

`List.sort` có bảo đảm sắp xếp ổn định: các phần tử so sánh bằng nhau giữ thứ tự tương đối trước khi sắp. [Tài liệu List.sort của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator))

Ví dụ `B` và `C` cùng priority, với `B` đứng trước `C` trong danh sách nguồn. Sắp xếp ổn định chỉ theo priority giữ thứ tự tương đối đó.

Đây là bảo đảm của phép sắp xếp danh sách, không phải lý do để giả định `PriorityQueue` cũng xử lý các phần tử đồng hạng theo thứ tự chèn. Mỗi cấu trúc cần được kiểm thử theo bảo đảm riêng.

## 5. Kiểm thử thứ tự ưu tiên bằng chuỗi lấy phần tử

### 5.1. Phần tử nhỏ nhất theo comparator được lấy trước

Trong `PriorityQueue`, phần tử ở đầu là phần tử nhỏ nhất theo thứ tự tự nhiên hoặc comparator cấu hình. Nếu có nhiều phần tử đồng hạng ở đầu, hàng đợi không hứa chọn theo thứ tự chèn. Iterator không được bảo đảm duyệt theo thứ tự ưu tiên. [Tài liệu PriorityQueue của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

Với số nguyên và thứ tự tự nhiên:

```text
Chèn: 30, 10, 20, 10
poll liên tục: 10, 10, 20, 30
```

Với quy ước “số priority lớn hơn làm trước”, comparator cần đảo chiều của thuộc tính đó:

```java
Comparator<Job> byPriority = Comparator.comparingInt(Job::priority).reversed();
```

Đây là đoạn minh họa dùng kiểu `Job` trong chương trình đầy đủ bên dưới. Giá trị priority lớn trở thành phần tử “nhỏ hơn” theo comparator đã đảo, nên được lấy trước.

### 5.2. Không kiểm thử bằng thứ tự iterator của heap

Các cách sau chỉ lấy thứ tự duyệt nội bộ, không biểu diễn chuỗi lấy phần tử ưu tiên:

```java
new ArrayList<>(queue);
queue.toArray();
queue.stream().toList();
```

Chúng là những đoạn biểu thức minh họa, không phải assertion đúng cho thứ tự `poll()`. Kể cả khi một lần chạy cho ra dãy có vẻ đã sắp xếp, không biến quan sát đó thành bảo đảm API.

Nếu cần kiểm tra quá trình lấy việc ra, hãy thực sự lấy ra theo thao tác mà sản phẩm dùng. Nếu chỉ cần trình bày một bản sắp xếp của các giá trị, đó là một mục tiêu khác và nên được mô tả riêng.

### 5.3. Kiểm tra bằng bản sao để giữ nguyên hàng đợi nguồn

`poll()` xóa phần tử, nên một hàm trợ giúp cho kiểm thử có thể tạo bản sao của `PriorityQueue` rồi rút hết từ bản sao. Constructor nhận `PriorityQueue` giữ cùng quy tắc sắp xếp của nguồn.

```java
static <E> List<E> pollOrder(PriorityQueue<E> source) {
    PriorityQueue<E> copy = new PriorityQueue<>(source);
    List<E> result = new ArrayList<>();
    while (!copy.isEmpty()) {
        result.add(copy.poll());
    }
    return result;
}
```

Đoạn phương thức này cần các import tương ứng như chương trình đầy đủ. Nó dùng cho trường hợp không có sửa đổi đồng thời và phần tử không đổi thuộc tính so sánh trong lúc kiểm tra.

Bản sao giữ các tham chiếu phần tử; nó không sao chép sâu đối tượng. Không sửa priority bên trong một đối tượng rồi kỳ vọng hàng đợi tự định vị lại đối tượng đó. Ví dụ trong bài dùng record bất biến về các trường so sánh để tránh làm hỏng giả định này.

## 6. Phần tử đồng hạng: hai chiến lược assertion

### 6.1. Không yêu cầu thứ tự phụ: kiểm tra đúng mức bảo đảm

Giả sử `B` và `C` cùng priority `5`, `A` và `E` cùng priority `2`, còn `D` có priority `1`. Nếu hợp đồng chỉ có priority giảm dần, các chuỗi sau đều hợp lệ:

```text
B, C, A, E, D
C, B, E, A, D
```

Một cách kiểm tra gồm hai điều kiện:

1. Các phần tử lấy liên tiếp không đảo thứ tự theo comparator.
2. Tập các phần tử kèm số lần xuất hiện bằng đúng đầu vào.

Chỉ kiểm tra điều kiện thứ nhất không phát hiện việc thiếu phần tử. Chỉ chuyển sang `Set` để kiểm tra điều kiện thứ hai có thể làm mất thông tin phần tử trùng. Một map đếm số lần xuất hiện phù hợp khi phép bằng nhau của phần tử phản ánh đúng danh tính cần kiểm thử.

Có thể kiểm tra dãy priority là `[5, 5, 2, 2, 1]` và kiểm tra riêng đủ các việc; không ép thứ tự ID giữa những việc đồng hạng.

### 6.2. Cần xử lý đồng hạng theo thứ tự đưa vào: đưa thứ tự đó vào comparator

Có thể gắn số thứ tự tăng dần khi đưa việc vào:

```java
Comparator<Job> byPriorityThenSequence =
        Comparator.comparingInt(Job::priority).reversed()
                .thenComparingLong(Job::sequence);
```

Comparator này có hai quy tắc:

1. Priority giảm dần.
2. Nếu bằng priority, sequence tăng dần.

Để biểu đạt thứ tự đưa vào, sequence phải được gán theo đúng thời điểm/điểm điều phối đưa việc vào và đủ phân biệt trong phạm vi hàng đợi. Đừng dùng sequence trùng rồi vẫn hứa một thứ tự hoàn toàn xác định. Việc cấp sequence khi có nhiều luồng là yêu cầu thiết kế riêng; ví dụ ở đây chỉ xử lý dữ liệu trong một luồng.

Chú ý vị trí gọi `reversed()`:

```java
Comparator.comparingInt(Job::priority).reversed()
        .thenComparingLong(Job::sequence);
```

khác với đảo toàn bộ comparator sau khi đã nối sequence. Đảo toàn bộ sẽ đảo cả hướng của quy tắc phụ. Kiểm thử với nhiều phần tử cùng priority giúp phát hiện lỗi này.

## 7. Chương trình Java 17+ kiểm tra cả ba loại thứ tự

Chương trình dùng các hàm tự kiểm tra có ném `AssertionError` khi sai. Không cần thư viện ngoài và không cần bật `-ea`.

Lưu thành `CollectionOrderDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CollectionOrderDemo {
    record Job(String id, int priority, long sequence) { }

    private static int passed;

    private static void checkEquals(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected
                    + ", actual=" + actual);
        }
        passed++;
    }

    private static <E> List<E> pollOrder(PriorityQueue<E> source) {
        PriorityQueue<E> copy = new PriorityQueue<>(source);
        List<E> result = new ArrayList<>();
        while (!copy.isEmpty()) {
            result.add(copy.poll());
        }
        return result;
    }

    private static <E> Map<E, Integer> frequencies(Collection<E> values) {
        Map<E, Integer> counts = new HashMap<>();
        for (E value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    private static <E> boolean isOrdered(List<E> values, Comparator<? super E> order) {
        for (int i = 1; i < values.size(); i++) {
            if (order.compare(values.get(i - 1), values.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<String> ids(List<Job> jobs) {
        return jobs.stream().map(Job::id).toList();
    }

    public static void main(String[] args) {
        Map<String, Integer> insertion = new LinkedHashMap<>();
        insertion.put("B", 2);
        insertion.put("A", 1);
        insertion.put("C", 3);
        checkEquals("insertion order", List.of("B", "A", "C"),
                new ArrayList<>(insertion.keySet()));
        System.out.println("insertion keys = " + new ArrayList<>(insertion.keySet()));
        insertion.put("A", 10);
        checkEquals("updating existing key keeps insertion position",
                List.of("B", "A", "C"), new ArrayList<>(insertion.keySet()));
        insertion.remove("A");
        insertion.put("A", 20);
        checkEquals("remove then insert appends key", List.of("B", "C", "A"),
                new ArrayList<>(insertion.keySet()));

        Map<String, Integer> access = new LinkedHashMap<>(16, 0.75f, true);
        access.put("B", 2);
        access.put("A", 1);
        access.put("C", 3);
        access.get("B");
        checkEquals("access order after get", List.of("A", "C", "B"),
                new ArrayList<>(access.keySet()));

        Set<String> insertionSet = new LinkedHashSet<>(List.of("B", "A", "B", "C"));
        checkEquals("set duplicate does not move value", List.of("B", "A", "C"),
                new ArrayList<>(insertionSet));
        insertionSet.remove("A");
        insertionSet.add("A");
        checkEquals("set remove and reinsert", List.of("B", "C", "A"),
                new ArrayList<>(insertionSet));

        Set<String> sortedSet = new TreeSet<>(List.of("B", "A", "B", "C"));
        checkEquals("natural sorted set", List.of("A", "B", "C"),
                new ArrayList<>(sortedSet));
        Set<String> descendingSet = new TreeSet<>(Comparator.reverseOrder());
        descendingSet.addAll(List.of("B", "A", "C"));
        checkEquals("reverse sorted set", List.of("C", "B", "A"),
                new ArrayList<>(descendingSet));

        Map<String, Integer> sortedMap = new TreeMap<>();
        sortedMap.put("B", 2);
        sortedMap.put("A", 3);
        sortedMap.put("C", 1);
        checkEquals("map sorted by keys", List.of("A", "B", "C"),
                new ArrayList<>(sortedMap.keySet()));
        checkEquals("values follow keys, not value sorting", List.of(3, 2, 1),
                new ArrayList<>(sortedMap.values()));
        System.out.println("sorted keys = " + new ArrayList<>(sortedMap.keySet()));

        PriorityQueue<Integer> numbers = new PriorityQueue<>(List.of(30, 10, 20, 10));
        List<Integer> numberOrder = pollOrder(numbers);
        checkEquals("natural priority order with duplicate", List.of(10, 10, 20, 30),
                numberOrder);
        checkEquals("peek reads first priority", 10, numbers.peek());
        checkEquals("copy leaves source size unchanged", 4, numbers.size());
        System.out.println("priority values = " + numberOrder);

        List<Job> jobs = List.of(
                new Job("A", 2, 0),
                new Job("B", 5, 1),
                new Job("C", 5, 2),
                new Job("D", 1, 3),
                new Job("E", 2, 4));
        Comparator<Job> byPriority = Comparator.comparingInt(Job::priority).reversed();
        List<Job> stableSorted = new ArrayList<>(jobs);
        stableSorted.sort(byPriority);
        checkEquals("stable list sorting", List.of("B", "C", "A", "E", "D"),
                ids(stableSorted));

        PriorityQueue<Job> priorityOnly = new PriorityQueue<>(byPriority);
        priorityOnly.addAll(jobs);
        List<Job> priorityResult = pollOrder(priorityOnly);
        List<Integer> levels = priorityResult.stream().map(Job::priority).toList();
        checkEquals("priority levels", List.of(5, 5, 2, 2, 1), levels);
        checkEquals("all jobs preserved", frequencies(jobs), frequencies(priorityResult));
        checkEquals("ordered under comparator", true, isOrdered(priorityResult, byPriority));
        System.out.println("priority-only levels = " + levels);

        Comparator<Job> withSequence = byPriority.thenComparingLong(Job::sequence);
        PriorityQueue<Job> deterministic = new PriorityQueue<>(withSequence);
        deterministic.addAll(jobs);
        List<Job> exactOrder = pollOrder(deterministic);
        checkEquals("priority then sequence", List.of("B", "C", "A", "E", "D"),
                ids(exactOrder));
        checkEquals("job source queue not consumed", jobs.size(), deterministic.size());
        System.out.println("priority with sequence = " + ids(exactOrder));

        PriorityQueue<Integer> empty = new PriorityQueue<>();
        checkEquals("empty queue output", List.of(), pollOrder(empty));
        checkEquals("empty peek", null, empty.peek());
        PriorityQueue<Integer> singleton = new PriorityQueue<>(List.of(7));
        checkEquals("singleton queue output", List.of(7), pollOrder(singleton));

        System.out.println("PASS: " + passed + " checks");
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CollectionOrderDemo.java
java CollectionOrderDemo
```

Stdout khi các kiểm tra đạt:

```text
insertion keys = [B, A, C]
sorted keys = [A, B, C]
priority values = [10, 10, 20, 30]
priority-only levels = [5, 5, 2, 2, 1]
priority with sequence = [B, C, A, E, D]
PASS: 22 checks
```

Chương trình cố ý không in hoặc kiểm tra thứ tự ID của `priorityOnly` khi đồng hạng, vì hợp đồng của comparator đó không xác định thứ tự này. Nó vẫn kiểm tra dãy priority, tính không đảo thứ tự và số lần xuất hiện của từng việc.

`isOrdered()` trong chương trình nhận kết quả là một `ArrayList` do hàm `pollOrder()` tạo ra, nên truy cập chỉ số tại đây phù hợp. Nếu tái sử dụng hàm trợ giúp với danh sách liên kết lớn, nên chuyển sang duyệt iterator để không tìm lại chỉ số ở mỗi lần so sánh.

## 8. Vì sao Set.equals và Map.equals chưa kiểm tra được thứ tự?

Hai `LinkedHashSet` có thể chứa cùng phần tử nhưng duyệt theo thứ tự khác nhau. Phép bằng nhau của tập hợp vẫn xem chúng tương đương về nội dung. Tương tự, hai map chứa cùng ánh xạ vẫn có thể bằng nhau dù thứ tự duyệt khóa khác nhau.

Ví dụ dữ liệu:

```text
Map thứ nhất: chèn B rồi A
Map thứ hai:  chèn A rồi B

Cùng ánh xạ: có
Cùng thứ tự duyệt: không
```

Nếu cần kiểm tra thứ tự, hãy chuyển thứ tự quan sát thành một `List`:

```java
List<String> actualKeys = new ArrayList<>(actualMap.keySet());
```

Sau đó so sánh với một danh sách kỳ vọng có thứ tự. Nếu cần cả ánh xạ và thứ tự, kiểm tra cả hai; kiểm tra khóa có thứ tự không thay thế được việc kiểm tra giá trị ứng với từng khóa.

Trong JUnit 5, có thể dùng `assertIterableEquals(expectedList, actualMap.keySet())` hoặc so sánh các `List` bằng `assertEquals`. Với `PriorityQueue`, đối tượng quan sát nên là kết quả `pollOrder(queue)` thay vì chính iterator của queue. Các lệnh assertion này là gợi ý cho môi trường đã có JUnit Jupiter; chương trình đầy đủ ở mục 7 chạy độc lập.

## 9. Những cách kiểm tra làm mất thông tin cần bảo vệ

### 9.1. Sắp xếp actual trước khi so sánh thứ tự chèn

Giả sử hợp đồng cần `B, A, C`, nhưng mã trả `A, B, C`. Nếu kiểm thử sắp xếp cả expected và actual trước khi so sánh, hai bên đều thành `A, B, C` và lỗi bị che mất.

Việc chuẩn hóa bằng sắp xếp chỉ phù hợp khi hợp đồng không quan tâm thứ tự và mục tiêu là so thành phần. Không dùng nó cho một bài kiểm tra thứ tự chèn.

### 9.2. Chuyển kết quả ưu tiên sang Set

Một hàng đợi có thể chứa nhiều phần tử bằng nhau. Chuyển thành `Set` làm mất số lần xuất hiện, nên không phát hiện việc queue bị thiếu một bản sao hoặc thừa bản sao khác.

Để kiểm tra đủ nội dung khi không yêu cầu thứ tự phụ, dùng số đếm theo phần tử hoặc một biểu diễn đa tập tương đương. Trong bài, `frequencies()` dùng `HashMap` và hợp đồng `equals/hashCode` của record `Job`.

### 9.3. Tính expected bằng đúng comparator đang cần kiểm thử

Nếu comparator nghiệp vụ sai hướng, vừa dùng nó trong cấu trúc thật vừa dùng nó để sắp xếp expected có thể khiến hai bên cùng sai mà kiểm thử vẫn đạt.

Các ví dụ expected cụ thể như `[5, 5, 2, 2, 1]` hoặc `[B, C, A, E, D]` phải xuất phát từ quy tắc nghiệp vụ đã viết. Kiểm tra tính có thứ tự bằng comparator là một lớp kiểm tra bổ sung, không phải nguồn sự thật duy nhất về ý nghĩa ưu tiên.

### 9.4. Kiểm tra toString thay cho hành vi

Chuỗi in ra hữu ích để giải thích và chẩn đoán, nhưng một assertion dựa vào định dạng `toString()` của collection hoặc phần tử có thể hỏng vì thay cách hiển thị mà không đổi hành vi.

Trong chương trình, các dòng stdout chỉ là kết quả minh họa. Các kiểm tra thật so sánh `List`, map số đếm hoặc giá trị cụ thể.

## 10. Ma trận ca kiểm thử có khả năng phân biệt tốt

| Mục tiêu | Dữ liệu/hành động | Điều cần khẳng định |
| --- | --- | --- |
| Thứ tự chèn khác thứ tự tự nhiên | Chèn `B, A, C` | Duyệt ra đúng `B, A, C` |
| Cập nhật khóa đang tồn tại | Đổi giá trị của `A` | Vị trí giữ nguyên trong insertion-order; giá trị đổi đúng |
| Xóa rồi thêm lại | Xóa `A`, thêm `A` | `A` chuyển về cuối theo hợp đồng chèn |
| Chế độ access-order | Chèn `B, A, C`, đọc `B` | Duyệt thành `A, C, B` |
| Sắp theo khóa, không theo giá trị | `B->2`, `A->3`, `C->1` | Khóa `A,B,C`; giá trị theo khóa `3,2,1` |
| Đảo hướng sắp xếp | Comparator giảm dần | Dãy giảm đúng, không vô tình tăng dần |
| Đồng hạng trong TreeSet | Hai phần tử compare bằng 0 | Số lượng và chính sách nhận diện phần tử đúng |
| Đồng hạng trong PriorityQueue | Nhiều việc cùng priority | Kiểm tra nhóm ưu tiên; chỉ ép thứ tự phụ nếu được hứa |
| Quy tắc phụ sequence | Cùng priority, sequence khác | Sequence nhỏ hơn được lấy trước |
| Phần tử trùng trong queue | Hai giá trị `10` | Cả hai lần xuất hiện đều được lấy ra |
| Rỗng và singleton | Không phần tử hoặc chỉ một | Dãy đầu ra đúng; không truy cập phần tử không tồn tại |
| Giữ nguyên nguồn khi kiểm tra | Rút từ bản sao | Kích thước và trạng thái nguồn không bị tiêu thụ |
| Thêm ưu tiên mới sau một lần lấy | Poll một phần tử rồi offer việc mới | Các lần lấy tiếp chọn tốt nhất trong tập đang còn |

Ca cuối nhắc rằng hàng đợi động không có một dãy priority toàn cục luôn đơn điệu nếu ta tiếp tục đưa việc mới vào giữa các lần lấy. Một việc ưu tiên cao hơn có thể được thêm sau khi đã xử lý một việc thấp hơn. Khi đó, kiểm thử phải theo từng bước trạng thái: mỗi lần lấy chọn phần tử đứng đầu trong **tập hiện có tại thời điểm đó**, không áp một dãy sắp xếp tĩnh cho toàn bộ lịch sử.

## 11. Kiểm thử ưu tiên động và tránh thay đổi khóa sắp xếp tại chỗ

Một kịch bản nhỏ:

```text
Đưa vào: A(priority=2), B(priority=5)
Lấy ra: B
Đưa thêm: C(priority=9)
Lấy ra: C
Lấy ra: A
```

Dãy priority lấy ra là `5, 9, 2`. Nó không giảm dần trên toàn bộ lịch sử, nhưng từng lần lấy đều đúng với các việc đang có trong queue.

Không sửa trực tiếp trường priority của phần tử đang nằm trong queue rồi kỳ vọng cấu trúc tự sắp lại. Nếu ứng dụng cần thay đổi ưu tiên, thiết kế phải cập nhật cấu trúc theo một quy trình xác định, chẳng hạn tìm/xóa việc cũ rồi đưa phiên bản mới vào, hoặc dùng cấu trúc chuyên biệt. Kiểm thử quy trình đó riêng, bao gồm danh tính việc và chính sách xử lý bản cũ.

Với `TreeSet` hoặc khóa `TreeMap`, thay đổi thuộc tính dùng để so sánh khi phần tử/khóa còn trong cấu trúc cũng làm mất giả định tìm kiếm và thứ tự. Dùng khóa ổn định hoặc quy trình xóa/cập nhật/thêm lại rõ ràng.

## 12. Tự kiểm tra và thực hành

### Câu 1: Hai LinkedHashMap bằng nhau theo Map.equals có bảo đảm cùng thứ tự duyệt không?

Không. Phép bằng nhau kiểm tra các ánh xạ. Cần quan sát thứ tự khóa hoặc entry thành dãy và kiểm tra riêng khi thứ tự thuộc hợp đồng.

### Câu 2: Vì sao không dùng new ArrayList<>(priorityQueue) làm actual cho thứ tự ưu tiên?

Vì đó là thứ tự iterator, không phải chuỗi lấy phần tử ở đầu hàng đợi. Rút bằng `poll()` từ nguồn hoặc bản sao phù hợp mới quan sát hành vi cần kiểm tra.

### Câu 3: Hai việc cùng priority có phải được lấy theo thứ tự chèn không?

Không nếu chỉ dựa vào `PriorityQueue` và comparator chỉ so priority. Nếu cần quy tắc này, thêm một khóa phụ biểu đạt thứ tự đưa vào và kiểm tra nó.

### Câu 4: Dãy priority đã giảm dần có đủ chứng minh hàng đợi xử lý đúng toàn bộ đầu vào không?

Chưa. Có thể thiếu hoặc thừa phần tử mà dãy còn lại vẫn giảm dần. Cần kiểm tra nội dung và số lần xuất hiện, cùng chính sách đồng hạng.

### Bài thực hành

1. Thêm kiểm tra rằng đổi giá trị `A` trong insertion-order map vừa giữ thứ tự vừa thật sự cập nhật giá trị.
2. Thêm một việc priority bằng `Integer.MAX_VALUE` và một việc bằng `Integer.MIN_VALUE`; viết expected thủ công theo quy tắc priority lớn làm trước.
3. Viết kiểm thử kịch bản ưu tiên động ở mục 11; xác nhận từng lần `poll()` thay vì sắp xếp toàn bộ lịch sử.
4. Cố ý chuyển `reversed()` xuống sau `thenComparingLong()` và xác định ca kiểm thử nào phát hiện việc đảo cả quy tắc phụ.
5. Thay queue thành tập hợp trong một bản thử nghiệm; xác định ca nào chứng minh phần tử trùng hoặc đồng hạng đã bị mất ngoài hợp đồng.

Một kiểm thử thứ tự đáng tin phải chọn đúng hành động quan sát, dữ liệu phân biệt được các chính sách và assertion giữ nguyên thông tin quan trọng. Thứ tự chèn, thứ tự sắp xếp và thứ tự lấy theo ưu tiên cần những cách kiểm tra tương ứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-004">ConcurrentModificationException và thay đổi nguồn qua nhiều đường</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentModificationException và thay đổi nguồn qua nhiều đường

## 1. Ngoại lệ nói điều gì và không nói điều gì?

`ConcurrentModificationException` (CME) báo rằng một thao tác đã phát hiện sự thay đổi không được phép của đối tượng đang được sử dụng. Tình huống thường gặp là iterator đang duyệt một collection, còn cấu trúc nguồn bị thay đổi bằng một đường khác.

Tên ngoại lệ dễ khiến người mới chỉ nghĩ đến nhiều luồng. Thực tế, một luồng cũng có thể gây CME: vòng lặp đang dùng iterator, nhưng chính mã trong vòng lặp hoặc hàm được gọi lại xóa/thêm vào collection nguồn.

Cơ chế dừng sớm khi phát hiện sai lệch này thường được gọi là **fail-fast**. Nó hỗ trợ phát hiện lỗi, không phải khóa, giao dịch hay cơ chế bảo đảm an toàn giữa các luồng. Việc phát hiện chỉ mang tính nỗ lực tốt nhất; chương trình không được dựa vào việc chắc chắn có CME để hoạt động đúng. [Tài liệu ConcurrentModificationException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ConcurrentModificationException.html).

Bài này tập trung vào một câu hỏi khi gỡ lỗi: **iterator đang duyệt cấu trúc nào, và những đường nào khác có thể thay đổi chính cấu trúc đó?**

## 2. Mô hình nguồn và trạng thái kỳ vọng của iterator

Ở một số triển khai phổ biến như `ArrayList` của OpenJDK, collection theo dõi thay đổi cấu trúc bằng một bộ đếm. Iterator giữ một giá trị kỳ vọng tương ứng. Khi tới điểm kiểm tra, nếu hai giá trị không khớp, iterator có thể ném CME.

```text
Tạo iterator
  nguồn có trạng thái thay đổi M
  iterator ghi nhận kỳ vọng M

Sửa nguồn qua đường ngoài iterator đó
  nguồn chuyển sang trạng thái M'
  iterator vẫn giữ kỳ vọng M

Iterator tiếp tục ở điểm kiểm tra
  trạng thái hiện tại khác kỳ vọng -> phát hiện sai lệch -> CME
```

Các tên `modCount`, `expectedModCount` và vị trí kiểm tra cụ thể là chi tiết triển khai, không phải một giao thức công khai để ứng dụng tự điều chỉnh. Không dùng reflection để sửa bộ đếm hoặc “đồng bộ lại” iterator. [Mã nguồn ArrayList của OpenJDK 21](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/ArrayList.java).

Điểm quan trọng là **iterator riêng nào được phép thay đổi nguồn**. Với iterator hỗ trợ `remove`, xóa qua chính iterator đó có cách cập nhật trạng thái nội bộ phù hợp. Xóa qua một iterator khác vẫn là thay đổi từ bên ngoài đối với iterator đang xét.

### Không phải mọi điểm đều kiểm tra ngay

Không suy rằng dòng `add` hoặc `remove` phải là nơi ném lỗi. Thay đổi có thể đã xảy ra trước đó; ngoại lệ chỉ lộ khi `next`, một thao tác duyệt tiếp theo hoặc một kiểm tra cuối của thao tác hàng loạt phát hiện.

Cũng không suy rằng gọi `hasNext()` không lỗi thì `next()` chắc chắn hợp lệ sau một thay đổi ngoài hợp đồng. Vị trí kiểm tra phụ thuộc triển khai. Một đường chạy kết thúc sớm mà không gặp CME chưa chứng minh cách thay đổi đó hợp lệ.

## 3. “Thay đổi cấu trúc” không chỉ là đổi size

Với `ArrayList`, thêm/xóa phần tử là thay đổi cấu trúc; thay giá trị một vị trí bằng `set` không phải thay đổi cấu trúc theo mô tả của lớp. Iterator fail-fast của lớp được thiết kế để phát hiện các thay đổi cấu trúc ngoài đường sửa được hỗ trợ. [Tài liệu ArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html).

Tuy nhiên không dùng quy tắc “size giữ nguyên nên an toàn” cho mọi thao tác:

| Thao tác điển hình | Điều cần hiểu |
| --- | --- |
| Thêm rồi xóa để size trở lại như cũ | Nguồn vẫn đã trải qua thay đổi; không phục hồi tính hợp lệ của iterator cũ |
| `ArrayList.set(index, value)` | Không phải thay đổi cấu trúc; không biến iterator thành bản chụp dữ liệu |
| `ArrayList.sort(...)` | Có thể làm iterator cũ bị phát hiện sai lệch trong triển khai OpenJDK, dù số phần tử không đổi |
| Thêm khóa mới vào Map | Thay đổi tập ánh xạ |
| Chỉ thay giá trị của khóa đang có trong `HashMap` | Khác với thêm/xóa ánh xạ; vẫn không tạo bảo đảm về bản chụp hoặc đồng bộ |
| `get` trên `LinkedHashMap` theo thứ tự truy cập | Có thể đổi thứ tự liên kết và ảnh hưởng iterator |
| Sửa trường bên trong một phần tử | Không nhất thiết đổi cấu trúc collection; có thể gây vấn đề hash/thứ tự riêng mà CME không phát hiện |

Phải đọc hợp đồng của lớp triển khai và thao tác cụ thể. Không đồng nhất “không tạo CME” với “kết quả không đổi”, “có thể chạy nhiều luồng” hoặc “đúng yêu cầu nghiệp vụ”.

## 4. Những đường cùng đi về một nguồn

### 4.1. Hai biến không có nghĩa hai collection

```java
List<String> first = new ArrayList<>(List.of("A", "B"));
List<String> second = first;
```

`first` và `second` là hai đường tham chiếu đến cùng đối tượng. Thay đổi `second` là thay đổi nguồn của iterator lấy từ `first`. Việc đặt tên biến là `copy`, `backup` hay `readOnly` không quyết định có sao chép dữ liệu hay không.

### 4.2. View của List và Map

Một view là cách nhìn vào dữ liệu được giữ ở nơi khác, không mặc định là bản sao:

```text
List gốc <---- subList
    ^
    +-------- wrapper không cho sửa
    +-------- nhiều iterator

Map gốc <----- keySet
    ^
    +--------- values
    +--------- entrySet
```

Thay đổi thông qua view có thể đi xuyên về nguồn. Ví dụ `map.keySet().remove(key)` xóa ánh xạ trong Map, vì thế iterator đang duyệt `entrySet` của Map đó có thể bị vô hiệu hóa. Các view không phải những kho dữ liệu tách biệt. [Tài liệu Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#keySet()).

Với `subList`, thay đổi được hỗ trợ qua chính view có tác động lên List gốc. Nếu List gốc bị sửa cấu trúc bằng cách khác thay vì qua subList, ngữ nghĩa của subList không còn được xác định theo hợp đồng. Một triển khai có thể báo CME khi dùng view đã mất hiệu lực, nhưng không viết logic ứng dụng yêu cầu mọi triển khai phải báo đúng ngoại lệ đó. Khi cần một phần dữ liệu độc lập về cấu trúc, tạo bản sao thay vì tiếp tục dùng view cũ. [Tài liệu List.subList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html#subList(int,int)).

### 4.3. Wrapper không cho sửa vẫn đọc nguồn đang thay đổi

`Collections.unmodifiableList(source)` ngăn sửa qua wrapper, nhưng không ngăn mã khác giữ `source` rồi thay đổi nó. Iterator từ wrapper vẫn dựa vào dữ liệu phía sau. Vì vậy “không thể sửa qua biến này” không có nghĩa “không ai có thể sửa nguồn”.

Muốn tách cấu trúc tại một thời điểm, có thể tạo `new ArrayList<>(source)` hoặc chọn một phép sao chép phù hợp. Bản sao đó vẫn thường dùng chung tham chiếu tới phần tử; nó không tự sao chép sâu trạng thái của từng phần tử.

### 4.4. Hàm được gọi lại và lời gọi lồng

Đoạn sau là mã minh họa sai; `notifyListener` đại diện cho hàm trong ứng dụng:

```java
for (Listener listener : listeners) {
    notifyListener(listener); // có thể gián tiếp gọi listeners.remove(...)
}
```

Nhìn vào thân vòng lặp không thấy `remove` chưa đủ để loại trừ thay đổi. Cần lần theo những hàm, sự kiện hoặc phương thức tiện ích giữ cùng List. Một listener tự đăng ký/hủy đăng ký trong khi đang được thông báo là tình huống thường gặp.

Tương tự, `list.forEach`, `removeIf`, một mapping function hoặc một stream có thể gọi mã tùy biến. Hàm đó không nên tự thêm/xóa nguồn đang được thao tác nếu API không cho phép. Ghi nhận ý định thay đổi để áp dụng sau, hoặc chọn chính sách duyệt có bản chụp khi yêu cầu cho phép.

### 4.5. Một thao tác trông như đọc cũng có thể đổi cấu trúc liên quan

`LinkedHashMap` được cấu hình `accessOrder = true` duy trì thứ tự theo truy cập. `get` một khóa có thể chuyển ánh xạ sang vị trí mới. Vì vậy duyệt `keySet` rồi gọi `get` trên chính Map để lấy giá trị có thể gây CME nếu truy cập làm đổi thứ tự.

Nếu cần khóa và giá trị khi duyệt, cân nhắc duyệt `entrySet` và đọc giá trị của entry theo hợp đồng phù hợp, thay vì tạo truy cập đổi thứ tự không cần thiết. Không áp hành vi của Map theo thứ tự chèn cho Map theo thứ tự truy cập. [Tài liệu LinkedHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html).

## 5. Chương trình Java 17+ tái hiện các đường thay đổi

Lưu thành `CmePathsDemo.java`. Chương trình chạy một luồng, không dùng `sleep` hoặc tranh chấp thời điểm. Các dòng CME minh họa hành vi fail-fast của những triển khai JDK cụ thể được khởi tạo trong mã, như `ArrayList` và `LinkedHashMap`; không phải lời hứa rằng mọi collection hoặc mọi vi phạm đều báo CME.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class CmePathsDemo {
    static void expectCme(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected CME for demonstration: " + label);
        } catch (ConcurrentModificationException ex) {
            System.out.println(label + "=CME");
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B", "C"));
        List<String> alias = source;
        Iterator<String> aliasCursor = source.iterator();
        alias.add("D");
        expectCme("alias next", aliasCursor::next);

        List<String> parent = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> window = parent.subList(1, 3);
        Iterator<String> parentCursor = parent.iterator();
        window.remove("B");
        System.out.println("parent after view remove=" + parent);
        expectCme("parent iterator after view edit", parentCursor::next);

        List<String> backing = new ArrayList<>(List.of("A", "B"));
        List<String> readOnly = Collections.unmodifiableList(backing);
        Iterator<String> readOnlyCursor = readOnly.iterator();
        backing.add("C");
        expectCme("read-only view iterator", readOnlyCursor::next);

        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        Iterator<Map.Entry<String, Integer>> entries = map.entrySet().iterator();
        map.keySet().remove("B");
        expectCme("entry iterator after keySet edit", entries::next);

        List<String> shared = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> first = shared.iterator();
        Iterator<String> second = shared.iterator();
        first.next();
        first.remove();
        System.out.println("same iterator continues=" + first.next());
        expectCme("other iterator after remove", second::next);

        List<String> replacement = new ArrayList<>(List.of("A", "B"));
        Iterator<String> replacementCursor = replacement.iterator();
        replacement.set(0, "Z");
        System.out.println("ArrayList set then next=" + replacementCursor.next());

        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        Iterator<Integer> numberCursor = numbers.iterator();
        while (numberCursor.hasNext()) {
            if (numberCursor.next() % 2 == 0) {
                numberCursor.remove();
            }
        }
        System.out.println("iterator filtered=" + numbers);

        List<Integer> bulk = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        bulk.removeIf(number -> number % 2 == 0);
        System.out.println("removeIf filtered=" + bulk);

        List<String> editable = new ArrayList<>(List.of("A", "C"));
        ListIterator<String> editCursor = editable.listIterator();
        editCursor.next();
        editCursor.add("B");
        editCursor.next();
        editCursor.set("Z");
        System.out.println("ListIterator edited=" + editable);

        List<String> original = new ArrayList<>(List.of("A", "B"));
        List<String> snapshot = new ArrayList<>(original);
        for (String value : snapshot) {
            original.add(value + "*");
        }
        System.out.println("snapshot=" + snapshot);
        System.out.println("original after processing=" + original);

        Map<String, Integer> accessOrdered = new LinkedHashMap<>(16, 0.75f, true);
        accessOrdered.put("A", 1);
        accessOrdered.put("B", 2);
        accessOrdered.put("C", 3);
        Iterator<String> accessCursor = accessOrdered.keySet().iterator();
        accessOrdered.get("A");
        expectCme("access-order get", accessCursor::next);
        System.out.println("keys after access=" + accessOrdered.keySet());

        CopyOnWriteArrayList<String> copyOnWrite =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        Iterator<String> oldSnapshot = copyOnWrite.iterator();
        copyOnWrite.add("C");
        List<String> seen = new ArrayList<>();
        oldSnapshot.forEachRemaining(seen::add);
        System.out.println("copy-on-write iterator=" + seen);
        System.out.println("copy-on-write current=" + copyOnWrite);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CmePathsDemo.java
java CmePathsDemo
```

Stdout của ví dụ trên các triển khai OpenJDK thông dụng tương ứng:

```text
alias next=CME
parent after view remove=[A, C, D]
parent iterator after view edit=CME
read-only view iterator=CME
entry iterator after keySet edit=CME
same iterator continues=B
other iterator after remove=CME
ArrayList set then next=Z
iterator filtered=[1, 3, 5]
removeIf filtered=[1, 3, 5]
ListIterator edited=[A, B, Z]
snapshot=[A, B]
original after processing=[A, B, A*, B*]
access-order get=CME
keys after access=[B, C, A]
copy-on-write iterator=[A, B]
copy-on-write current=[A, B, C]
```

### Đọc từng nhóm kết quả

- `alias` thay đổi cùng đối tượng với `source`, nên iterator cũ phát hiện sai lệch khi tiếp tục.
- Xóa qua `window` là thay đổi qua subList và sửa List cha. Iterator đã tạo trước trên List cha không được cập nhật theo iterator của view.
- Wrapper không cho sửa không bảo vệ iterator khỏi thay đổi được thực hiện qua `backing`.
- `keySet` và `entrySet` dùng chung Map; sửa qua view này ảnh hưởng việc duyệt view kia.
- `first.remove()` hợp lệ cho chính `first`, nhưng không làm iterator `second` trở nên hợp lệ sau thay đổi.
- `ArrayList.set` không làm thay đổi cấu trúc trong trường hợp minh họa; iterator đọc giá trị `Z` hiện tại, chứ không giữ bản chụp giá trị `A` ban đầu.
- Các bản lọc bằng iterator của chính vòng duyệt và `removeIf` cho cùng kết quả mong muốn.
- `ListIterator` cho phép thêm/thay trong trạng thái hợp lệ của chính nó.
- Duyệt bản sao không duyệt trực tiếp cấu trúc `original`; thêm vào `original` không đổi cấu trúc bản sao.
- `get("A")` của Map theo thứ tự truy cập chuyển `A` về cuối; nguồn không cần đổi số phần tử để iterator phát hiện thay đổi.
- Iterator `CopyOnWriteArrayList` vẫn thấy `A`, `B`, còn List hiện tại đã có `C` theo chính sách bản chụp của lớp.

Hàm `expectCme` cố ý bắt ngoại lệ để trình bày thí nghiệm và dừng bằng `AssertionError` nếu không thấy hành vi được mong đợi ở ví dụ đó. Đây không phải mẫu xử lý lỗi nghiệp vụ để đưa vào vòng lặp ứng dụng.

## 6. Chọn cách sửa theo mục đích thay đổi

### 6.1. Xóa phần tử đang duyệt: dùng chính iterator hỗ trợ remove

```java
Iterator<Item> cursor = items.iterator();
while (cursor.hasNext()) {
    Item item = cursor.next();
    if (shouldRemove(item)) {
        cursor.remove();
    }
}
```

Đoạn này là mẫu áp dụng trong ứng dụng; cần cung cấp kiểu `Item`, `items` và hàm điều kiện. Không gọi thêm `items.remove(item)` bên trong vì đó lại là đường sửa ngoài iterator.

`Iterator.remove` là thao tác tùy chọn; một số iterator không hỗ trợ. Với iterator thông thường có hỗ trợ, cần gọi `next` trước và chỉ `remove` tối đa một lần cho lần `next` đó. Vi phạm trạng thái có thể dẫn tới `IllegalStateException`, khác CME. Không mặc định mọi iterator, đặc biệt iterator bản chụp, đều cho phép xóa. [Tài liệu Iterator.remove](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Iterator.html#remove()).

Không lấy việc `Iterator.remove` hoạt động trong vòng `while` rồi suy rằng có thể gọi nó từ action của `forEachRemaining`. Hợp đồng của `forEachRemaining` không xác định hành vi khi action sửa nguồn, kể cả qua phương thức sửa của iterator, trừ khi triển khai công bố chính sách khác.

### 6.2. Lọc theo điều kiện: dùng removeIf với điều kiện không sửa nguồn

```java
items.removeIf(item -> shouldRemove(item));
```

Điều kiện chỉ quyết định giữ hay bỏ. Không gọi `items.add`, `items.remove` hoặc hàm có tác dụng tương đương lên cùng nguồn từ trong điều kiện. `removeIf` cũng cần collection hỗ trợ thao tác xóa; wrapper không cho sửa có thể ném `UnsupportedOperationException`. [Tài liệu Collection.removeIf](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate)).

### 6.3. Cần thêm hoặc thay tại con trỏ: dùng ListIterator khi phù hợp

Với List hỗ trợ, `ListIterator.add`, `set` và `remove` cho phép thay đổi theo trạng thái của chính con trỏ. Quy tắc gọi từng phương thức vẫn phải được tuân thủ. Chúng không cấp quyền cho hàm khác tự do sửa cùng List hoặc giữ các iterator khác tiếp tục dùng sau thay đổi.

### 6.4. Muốn quyết định trước rồi áp dụng sau

Có thể thu thập các thay đổi dự kiến vào collection riêng, kết thúc việc duyệt nguồn rồi áp dụng. Cần giữ đúng ý nghĩa:

- Nếu xóa theo giá trị, `removeAll` có thể xóa mọi phần tử bằng giá trị đó, không chỉ một lần xuất hiện đã chọn.
- Nếu xóa theo chỉ số, các chỉ số sau thay đổi có thể dịch chuyển; phải có chiến lược áp dụng phù hợp.
- Nếu cần thêm phần tử mới nhưng không muốn chúng được xử lý trong lượt hiện tại, danh sách chờ thêm riêng làm rõ ranh giới lượt xử lý.

Không thay một lỗi CME bằng một cách xóa làm mất số lượng bản sao hoặc sai vị trí.

### 6.5. Muốn duyệt tập thành viên tại đầu lượt: dùng bản chụp

Bản sao cấu trúc thích hợp khi yêu cầu là xử lý những phần tử đã có lúc bắt đầu. Thêm/xóa nguồn sau đó không tự thay số thành viên trong bản sao. Tuy nhiên đối tượng phần tử có thể vẫn được chia sẻ và thay đổi.

Nếu nguồn đang được nhiều luồng sửa, thao tác tạo bản sao cũng phải được bảo vệ theo hợp đồng của nguồn. `new ArrayList<>(source)` không tự biến việc đọc một `ArrayList` đang bị sửa không đồng bộ thành thao tác an toàn. `List.copyOf` còn có chính sách không cho phần tử `null`; cần chọn API sao chép đúng dữ liệu.

## 7. Stream và hàm được gọi lại vẫn có nguồn chung

Đổi vòng `for` thành stream không giải quyết sự can thiệp vào nguồn:

```java
// Mã sai về chính sách nguồn; không dùng làm cách xóa.
items.stream().forEach(item -> items.remove(item));
```

Với nguồn không được thiết kế cho thay đổi đồng thời, hàm trong pipeline phải không can thiệp vào nguồn trong lúc pipeline chạy. Kết quả của vi phạm không được bảo đảm chỉ là CME; có thể xuất hiện hành vi sai hoặc ngoại lệ khác. Yêu cầu này có ý nghĩa với cả stream tuần tự lẫn song song. [Tài liệu stream: non-interference](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html).

Cũng không coi thời điểm tạo stream là thời điểm chụp dữ liệu: pipeline thường thực hiện lười và nhiều nguồn gắn việc duyệt muộn. Bài toán “tôi thay List sau khi tạo stream nhưng trước terminal operation” cần đọc chính sách nguồn, không áp nguyên mô hình tạo iterator rồi sửa vào mọi stream.

Một phương án rõ nghĩa là tạo kết quả mới bằng `filter` nếu không cần sửa nguồn, hoặc dùng `removeIf` nếu mục tiêu là xóa trên nguồn và API cho phép. Tác dụng phụ trong predicate vẫn cần tránh theo hợp đồng.

## 8. Nếu thực sự có nhiều luồng

### 8.1. Wrapper đồng bộ cần khóa đúng phạm vi

`Collections.synchronizedList` đồng bộ các phương thức theo chính sách của wrapper, nhưng khi duyệt cần tự đồng bộ cả đoạn duyệt trên đối tượng wrapper theo hướng dẫn API:

```java
List<String> shared = Collections.synchronizedList(new ArrayList<>());
synchronized (shared) {
    for (String value : shared) {
        consume(value);
    }
}
```

Đây là đoạn minh họa, cần hàm `consume`. Các đường truy cập khác phải tuân thủ cùng chính sách; giữ và dùng tham chiếu trực tiếp tới List gốc ngoài wrapper có thể phá sự bảo vệ. Khi duyệt view của một wrapper đồng bộ, phải dùng đúng khóa mà API yêu cầu, không tự chọn khóa của một đối tượng view khác. [Tài liệu Collections.synchronizedList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)).

Khóa không làm hợp lệ việc chính `consume` sửa cấu trúc nguồn qua đường ngoài iterator trong cùng luồng. Monitor Java cho phép vào lại ở cùng luồng; vì thế callback vẫn có thể gây CME nếu sửa sai cách. Đồng bộ và chính sách thay đổi trong lúc duyệt là hai yêu cầu phải giải quyết đồng thời.

### 8.2. Chọn collection đồng thời theo cách nhìn dữ liệu mong muốn

| Chính sách duyệt | Ví dụ | Cần chấp nhận |
| --- | --- | --- |
| Iterator fail-fast trên collection thông thường | `ArrayList`, các view của Map thông thường | Không sửa nguồn ngoài đường được iterator/API cho phép |
| Iterator bản chụp | `CopyOnWriteArrayList` | Không thấy thay đổi thành viên sau lúc tạo iterator; chi phí ghi cao và iterator không cho sửa |
| Duyệt nhất quán yếu | `ConcurrentHashMap` | Có thể phản ánh trạng thái trong quá trình thay đổi; không phải ảnh chụp nguyên tử của toàn Map |

Iterator `CopyOnWriteArrayList` được bảo đảm không ném CME theo chính sách của nó; các thao tác `remove`, `set`, `add` qua iterator không được hỗ trợ. Đây là lựa chọn cho yêu cầu đọc nhiều hơn ghi rất nhiều, không phải phép đổi lớp để che mọi lỗi. [Tài liệu CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).

Các iterator của `ConcurrentHashMap` không ném CME, nhưng việc duyệt không bảo đảm có đúng toàn bộ trạng thái Map tại một thời điểm duy nhất. Không suy thứ tự hoặc danh sách phần tử cố định trong khi có cập nhật đồng thời. Chuỗi nhiều thao tác nghiệp vụ vẫn cần xét tính nguyên tử riêng. [Tài liệu ConcurrentHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

## 9. Cách gỡ lỗi thay đổi nguồn qua nhiều đường

1. Xác định lớp collection thật và kiểu duyệt: iterator, vòng `for` mở rộng, view, stream hay thao tác có callback.
2. Ghi lại thời điểm iterator/lượt duyệt bắt đầu và dòng phát hiện lỗi. Đừng mặc định dòng stack trace chính là nơi đã sửa nguồn.
3. Vẽ các tham chiếu cùng nguồn: biến khác tên, trường đối tượng, getter trả collection thật, subList, các view Map và wrapper.
4. Lần theo mọi lời gọi trong thân duyệt, nhất là callback, listener, hàm tiện ích và truy cập có thể đổi thứ tự.
5. Phân biệt sửa phần tử với sửa cấu trúc; kiểm tra chính sách của đúng triển khai, không chỉ so size trước/sau.
6. Chọn sửa bằng iterator, thao tác hàng loạt, hai giai đoạn, bản chụp hoặc đồng bộ/collection đồng thời theo kết quả cần có.
7. Kiểm tra lại nội dung và số lần xử lý sau sửa, không chỉ kiểm tra “không còn CME”.

Không bắt CME rồi tiếp tục dùng iterator đã sai lệch. Cũng không tự retry cả lượt duyệt nếu lượt trước đã thực hiện tác dụng phụ như gửi thông báo hoặc cập nhật dữ liệu: retry có thể lặp tác dụng phụ. Chính sách thử lại chỉ đúng khi ứng dụng đã có cách bảo đảm nhất quán và xử lý lặp phù hợp.

## 10. Kiểm thử sau khi sửa

Tách thí nghiệm tái hiện lỗi của một triển khai cụ thể khỏi kiểm thử tính đúng của ứng dụng:

- Thí nghiệm CME nên dùng một luồng và thứ tự lời gọi xác định nếu mục tiêu chỉ là minh họa alias/view; không cần tạo tranh chấp ngẫu nhiên.
- Kiểm thử của bản sửa nên đối chiếu phần tử được giữ/xóa, thứ tự, số lần callback và ảnh hưởng lên nguồn/view.
- Với bản chụp, kiểm tra rõ phần tử thêm sau đầu lượt có được xử lý ngay hay để lượt sau.
- Với thao tác đồng thời, điều phối bằng cơ chế phối hợp như latch/barrier khi cần kiểm soát thứ tự thử nghiệm; không dùng một số mili giây `sleep` để suy chắc lịch chạy.
- Không viết test bắt buộc một cuộc đua không đồng bộ phải luôn ném CME. Fail-fast không đưa ra bảo đảm đó.

Câu hỏi tự kiểm tra:

1. Vì sao sửa `alias` có thể làm iterator từ `source` báo lỗi dù hai tên biến khác nhau?
2. Vì sao xóa bằng iterator thứ nhất có thể hợp lệ cho nó nhưng vô hiệu hóa iterator thứ hai?
3. Wrapper không cho sửa và bản sao cấu trúc khác nhau ở điểm nào?
4. Trong Map theo thứ tự truy cập, vì sao một lời gọi `get` có thể liên quan đến CME?
5. Nếu không còn CME sau khi chuyển sang bản chụp hoặc collection đồng thời, cần kiểm chứng thêm điều gì về tập phần tử được xử lý?

Để giải thích được một lỗi CME, hãy chỉ rõ nguồn dùng chung, đường thay đổi và chính sách duyệt bị vi phạm. Để sửa đúng, hãy quyết định lượt xử lý phải nhìn thấy dữ liệu nào và ai có quyền thay đổi nó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-005">UnsupportedOperationException trên factory, wrapper và view</a>

<details>
<summary>Xem chi tiết</summary>

# UnsupportedOperationException trên factory, wrapper và view

## 1. Vì sao gọi được phương thức nhưng chạy lại bị từ chối?

Đoạn mã sau biên dịch hợp lệ nhưng lỗi khi chạy:

```java
List<String> names = List.of("An", "Binh");
names.add("Chi"); // UnsupportedOperationException
```

Đây là đoạn minh họa bên trong một phương thức có import `java.util.List`.

Kiểu `List` công bố phương thức `add`, nhưng không hứa rằng mọi đối tượng thực hiện `List` đều hỗ trợ thao tác này. Một số phương thức sửa đổi trong Java Collections là **thao tác tùy chọn (optional operation)**. Nếu đối tượng không hỗ trợ thao tác được yêu cầu, nó có thể ném `UnsupportedOperationException`, viết tắt trong phần giải thích là UOE. [Tài liệu Collection của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

UOE là ngoại lệ lúc chạy, không phải lỗi biên dịch. Nó thường cho biết mã đang yêu cầu một khả năng sửa đổi mà đối tượng hiện tại không cung cấp. Muốn sửa đúng, cần xem collection được tạo từ đâu và chính xác thao tác nào bị từ chối.

Ba nguồn thường gặp:

- **Factory:** phương thức tạo/trả collection, chẳng hạn `List.of`, `Arrays.asList` hoặc `Stream.toList`.
- **Wrapper:** đối tượng bao một collection khác, chẳng hạn `Collections.unmodifiableList`.
- **View:** cách nhìn liên kết tới dữ liệu nguồn, chẳng hạn `subList`, `keySet` hoặc `values`.

Đây không phải ba nhãn loại trừ nhau. `Arrays.asList` là factory trả về view của mảng; `Collections.unmodifiableList` là wrapper đồng thời cung cấp một view không cho sửa qua wrapper.

## 2. Tách ba câu hỏi về khả năng thay đổi

Khi đọc hợp đồng của một collection, đừng chỉ hỏi “có mutable không?”. Hãy hỏi cụ thể:

1. Có được thêm/xóa phần tử, tức thay đổi kích thước không?
2. Có được thay thế phần tử hoặc giá trị đang có không?
3. Nếu dữ liệu nguồn hoặc đối tượng phần tử thay đổi từ nơi khác, collection có nhìn thấy thay đổi không?

| Cách tạo ví dụ | Thêm/xóa phần tử | Thay phần tử bằng `set` | Quan hệ với dữ liệu nguồn |
| --- | --- | --- | --- |
| `new ArrayList<>(source)` | Có | Có | Cấu trúc mới; các tham chiếu phần tử vẫn có thể dùng chung |
| `Arrays.asList(array)` | Không đổi kích thước | Có | View của chính mảng |
| `List.of(...)` | Không | Không | Không có danh sách nền có thể sửa qua một tham chiếu nguồn do người gọi giữ |
| `List.copyOf(source)` | Không | Không | Kết quả không phản ánh việc thêm/xóa/thay tham chiếu trong source sau khi sao chép |
| `Collections.unmodifiableList(source)` | Không qua wrapper | Không qua wrapper | View đọc từ source; source còn có thể đổi qua tham chiếu khác |
| `source.subList(from, to)` | Phụ thuộc khả năng của source | Phụ thuộc source | View của một đoạn trong source |

Bảng nói về thao tác trên cấu trúc và các tham chiếu được lưu. Nó không hứa rằng đối tượng phần tử đã trở thành bất biến sâu.

Một biến `final List<String>` cũng không thay đổi hợp đồng này: `final` ngăn gán biến sang danh sách khác, không tự ngăn sửa đối tượng danh sách.

## 3. Factory không phải lúc nào cũng trả cùng một loại collection

### 3.1. List.of, Set.of và Map.of không cho sửa qua API

Các factory `List.of`, `Set.of`, `Map.of` trả collection/map không cho phép sửa đổi qua API của kết quả. Với `List.of`, ngay cả `set(index, value)` cũng không được hỗ trợ. Không phải chỉ có thao tác tăng/giảm kích thước mới bị từ chối.

Ví dụ:

```java
List<String> values = List.of("A", "B");
values.set(0, "X"); // UnsupportedOperationException
```

Các factory này còn có quy tắc dữ liệu riêng: `List.of` không cho phần tử null; `Map.of` không cho khóa/giá trị null. Một lỗi ở bước tạo dữ liệu do null không phải UOE do sửa một collection đã tạo xong. [Tài liệu List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [tài liệu Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [tài liệu Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

Không suy ra kiểu triển khai nội bộ từ factory. Ép kiểu kết quả thành `ArrayList` không biến nó thành collection có thể sửa; phép ép kiểu còn có thể gây `ClassCastException`.

### 3.2. Arrays.asList: kích thước cố định nhưng vẫn thay được phần tử

`Arrays.asList(array)` trả một danh sách có kích thước cố định, được chống lưng bởi mảng đã cho. `set` được hỗ trợ và thay đổi được nhìn thấy từ cả mảng lẫn danh sách. Các thao tác làm đổi kích thước không được hỗ trợ. [Tài liệu Arrays.asList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...))

Ví dụ minh họa:

```java
String[] array = {"A", "B"};
List<String> fixed = Arrays.asList(array);
fixed.set(0, "X");  // hợp lệ; array[0] cũng là "X"
fixed.add("C");     // UnsupportedOperationException
```

Cần import `java.util.List` và `java.util.Arrays`.

Vì không đổi số phần tử, một thao tác như thay thế mọi giá trị hoặc sắp xếp lại có thể thực hiện trên danh sách này bằng các khả năng được hỗ trợ. Không gắn nhãn “không sửa được gì” chỉ từ việc `add` thất bại.

Lưu ý kiểu: danh sách nội bộ mà `Arrays.asList` trả không phải `java.util.ArrayList` chỉ vì tên lớp nội bộ có thể chứa chữ `ArrayList`. Dựa vào hợp đồng phương thức thay vì tên lớp quan sát được trong debugger.

### 3.3. Stream.toList khác với Collectors.toList

Trên Java 17:

- `stream.toList()` trả danh sách không cho sửa; gọi phương thức sửa đổi sẽ ném UOE.
- `stream.collect(Collectors.toList())` không cam kết kiểu triển khai hoặc khả năng sửa đổi của danh sách kết quả.
- Khi cần rõ một `ArrayList` có thể sửa, dùng `collect(Collectors.toCollection(ArrayList::new))`.

Đừng sửa lỗi bằng cách dựa vào việc một phiên bản triển khai tình cờ trả `ArrayList`. [Tài liệu Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()), [tài liệu Collectors.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toList())

Đây là sự khác biệt về hợp đồng kết quả, không phải việc các phương thức có tên gần giống nhau chắc chắn tương đương.

## 4. Wrapper không cho sửa không đồng nghĩa dữ liệu đã đóng băng

### 4.1. Unmodifiable wrapper chặn đường sửa qua wrapper

Ví dụ:

```java
List<String> source = new ArrayList<>(List.of("A"));
List<String> exposed = Collections.unmodifiableList(source);
```

Đoạn minh họa cần import `java.util.List`, `java.util.ArrayList` và `java.util.Collections`.

`exposed.add(...)`, `exposed.set(...)` và sửa thông qua iterator của `exposed` bị từ chối. Nhưng nếu còn giữ `source`, mã khác vẫn có thể thêm hoặc thay phần tử ở nguồn; các thay đổi được quan sát qua `exposed` vì wrapper là view đọc từ nguồn. [Tài liệu Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List))

```text
source -----> ArrayList nền
                  ^
                  |
exposed ---> wrapper chỉ cho đọc qua đường này
```

Wrapper kiểm soát một đường truy cập, không tước quyền sửa của mọi tham chiếu khác tới đối tượng nền.

Không phải wrapper nào cũng có mục tiêu cấm sửa. Chẳng hạn một wrapper đồng bộ hóa có trách nhiệm khác. Đọc đúng hợp đồng của phương thức tạo wrapper, không suy ra từ riêng chữ “wrapper”.

### 4.2. Bản chụp cấu trúc khác view đang liên kết

Nếu cần kết quả không phản ánh các thay đổi về danh sách nguồn sau khi tạo, có thể dùng `List.copyOf(source)` khi chính sách null của nó phù hợp. API cho phép tái sử dụng một kết quả không thể sửa đổi thích hợp; không kiểm thử dựa vào việc tham chiếu chắc chắn khác hay giống nguồn.

Sự khác biệt có thể kiểm tra bằng hành vi:

```text
Tạo source = [A]
Tạo live = unmodifiableList(source)
Tạo snapshot = List.copyOf(source)
Thêm B vào source

live quan sát [A, B]
snapshot vẫn quan sát [A]
```

“Bản chụp” ở đây nói về các phần tử/tham chiếu được lưu trong collection. Nếu một phần tử là đối tượng có thể thay đổi, sửa bên trong đối tượng đó vẫn có thể được nhìn thấy qua cả hai.

### 4.3. Không thể sửa collection không có nghĩa phần tử bất biến sâu

Ví dụ minh họa:

```java
StringBuilder item = new StringBuilder("A");
List<StringBuilder> values = List.of(item);
item.append("!"); // values.get(0).toString() giờ là "A!"
```

Không có thao tác thêm/xóa/thay phần tử trong `values`. Đối tượng được tham chiếu vẫn tự thay đổi trạng thái. Vì vậy, một kiểm thử chỉ xác nhận `add` ném UOE không chứng minh toàn bộ dữ liệu bất biến.

Nếu hợp đồng yêu cầu dữ liệu không thể thay đổi sâu, phải thiết kế kiểu phần tử và chính sách sao chép phù hợp, rồi kiểm thử chúng riêng.

## 5. View có thể cho sửa một số hướng và từ chối hướng khác

### 5.1. subList kế thừa khả năng của danh sách nền

`subList(from, to)` là view của đoạn `[from, to)`, không phải bản sao độc lập. Với nguồn `ArrayList` có thể sửa, thao tác `set`, thêm hoặc xóa qua sublist được phản ánh ở nguồn. Nếu nguồn không cho sửa hoặc chỉ có kích thước cố định, giới hạn tương ứng cũng xuất hiện ở view. [Tài liệu List.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int))

Ví dụ trạng thái:

```text
base = [A, B, C, D]
part = base.subList(1, 3)   -> [B, C]
part.set(0, X)             -> base = [A, X, C, D]
part.remove(1)             -> base = [A, X, D]
part.add(Y)                -> base = [A, X, Y, D]
```

Vì thế không kết luận “view luôn ném UOE khi sửa”.

Một vấn đề khác: nếu sửa cấu trúc của danh sách nền trực tiếp ngoài đường sublist, ngữ nghĩa của view trở thành không xác định theo hợp đồng. Không thiết kế kiểm thử chung đòi mọi triển khai phải ném chính xác `ConcurrentModificationException` trong mọi tình huống đó. Đó cũng không phải UOE do thao tác bị cấm.

### 5.2. keySet, values và entrySet không hỗ trợ add như collection độc lập

Với một map có thể sửa, các view thông thường từ `keySet()`, `values()` và `entrySet()` cho phép xóa ánh xạ qua những thao tác xóa được hỗ trợ. Nhưng chúng không hỗ trợ `add`/`addAll` theo hợp đồng các view này. [Tài liệu Map collection views](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#keySet())

Lý do cần hiểu về mô hình:

- Thêm một khóa vào `keySet` không cung cấp giá trị cho ánh xạ mới.
- Thêm một giá trị vào `values` không cung cấp khóa.
- Ngay cả `entrySet` cũng không được mặc nhiên coi như một tập độc lập hỗ trợ thêm entry.

Hãy thêm bằng `map.put(key, value)` hoặc API map phù hợp. Khi xóa qua view, nhớ rằng dữ liệu nguồn cũng bị xóa; đây có thể là hành vi mong muốn hoặc một tác dụng phụ ngoài ý định.

Nếu map nền không cho sửa, các đường xóa qua view cũng bị chặn. Phải xác định cả loại view lẫn khả năng của map nền.

### 5.3. Entry có đường sửa giá trị riêng

Entry lấy từ iterator của `HashMap`/`LinkedHashMap` có thể hỗ trợ `setValue`, cập nhật map nền. Entry qua `Collections.unmodifiableMap(...).entrySet()` không cung cấp đường sửa vòng qua wrapper: `setValue` bị từ chối.

Do đó khi kiểm thử map không cho sửa, đừng chỉ kiểm tra `put`. Kiểm tra cả các đường sửa liên quan như xóa qua `keySet` hoặc cập nhật entry nếu hợp đồng của API công bố những đường truy cập đó.

## 6. Không dùng thao tác không có tác dụng để đoán khả năng sửa đổi

Những phép thử sau không phải kiểm tra khả năng sửa tổng quát đáng tin:

```java
collection.addAll(List.of());
collection.remove("gia-tri-khong-ton-tai");
collection.clear(); // khi collection đang rỗng
```

Đây là các biểu thức minh họa. Ở mức hợp đồng collection tổng quát, thao tác sửa không có tác dụng thực tế có thể được phép không làm gì hoặc ném UOE nếu API cụ thể không quy định mạnh hơn. Vì vậy, “không ném exception” trong một ca no-op không chứng minh thao tác sửa có tác dụng sẽ được hỗ trợ. [Tài liệu Collection về thao tác không có tác dụng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

Một factory cụ thể có thể công bố bảo đảm chặt hơn, chẳng hạn các phương thức sửa của kết quả `List.of` bị từ chối. Hãy tôn trọng bảo đảm đó, nhưng không áp nó lên mọi đối tượng chỉ vì cùng kiểu `List`.

Để kiểm tra hành vi từ chối sửa, chọn thao tác chắc chắn cần thay đổi nội dung và có đối số hợp lệ: thêm một phần tử mới, xóa một phần tử đang có, hoặc `set` tại chỉ số hiện hữu bằng giá trị khác.

## 7. Chương trình Java 17+ chạy đầy đủ

Chương trình dưới đây kiểm tra cả thao tác bị từ chối và các thay đổi hợp lệ. Nó dùng các hàm tự kiểm tra, không phụ thuộc `-ea` hoặc thư viện bên ngoài.

Lưu thành `UnsupportedOperationsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnsupportedOperationsDemo {
    private static int passed;

    private static void checkEquals(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected
                    + ", actual=" + actual);
        }
        passed++;
    }

    private static void expectUoe(String label, Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            passed++;
            return;
        }
        throw new AssertionError(label + ": expected UnsupportedOperationException");
    }

    public static void main(String[] args) {
        List<String> factory = List.of("A", "B");
        expectUoe("List.of add", () -> factory.add("C"));
        expectUoe("List.of set", () -> factory.set(0, "X"));
        expectUoe("List.of remove", () -> factory.remove(0));
        Iterator<String> factoryIterator = factory.iterator();
        factoryIterator.next();
        expectUoe("List.of iterator remove", factoryIterator::remove);
        expectUoe("Set.of add", () -> Set.of("A", "B").add("C"));
        expectUoe("Map.of put", () -> Map.of("A", 1).put("B", 2));

        String[] array = {"A", "B"};
        List<String> fixed = Arrays.asList(array);
        fixed.set(0, "X");
        checkEquals("set updates backing array", "X", array[0]);
        array[1] = "Y";
        checkEquals("array update visible in list", List.of("X", "Y"), fixed);
        expectUoe("fixed size add", () -> fixed.add("Z"));
        expectUoe("fixed size remove existing", () -> fixed.remove(0));
        fixed.replaceAll(String::toLowerCase);
        checkEquals("replace values without resizing", List.of("x", "y"),
                Arrays.asList(array));

        List<String> backing = new ArrayList<>(List.of("A"));
        List<String> live = Collections.unmodifiableList(backing);
        expectUoe("wrapper add", () -> live.add("B"));
        expectUoe("wrapper set", () -> live.set(0, "X"));
        backing.add("B");
        checkEquals("wrapper observes backing add", List.of("A", "B"), live);
        List<String> snapshot = List.copyOf(backing);
        backing.set(0, "X");
        checkEquals("wrapper observes backing set", List.of("X", "B"), live);
        checkEquals("copyOf snapshot stays unchanged", List.of("A", "B"), snapshot);
        expectUoe("copyOf add", () -> snapshot.add("Z"));

        StringBuilder item = new StringBuilder("A");
        List<StringBuilder> shallow = List.of(item);
        item.append("!");
        checkEquals("unmodifiable is not deep immutability", "A!",
                shallow.get(0).toString());

        List<String> base = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> part = base.subList(1, 3);
        part.set(0, "X");
        checkEquals("subList set updates parent", List.of("A", "X", "C", "D"), base);
        part.remove(1);
        checkEquals("subList remove updates parent", List.of("A", "X", "D"), base);
        part.add("Y");
        checkEquals("subList add updates parent", List.of("A", "X", "Y", "D"), base);
        List<String> readOnlyPart = Collections.unmodifiableList(base).subList(1, 3);
        expectUoe("unmodifiable subList set", () -> readOnlyPart.set(0, "Z"));
        List<String> fixedPart = fixed.subList(0, 1);
        expectUoe("fixed size subList add", () -> fixedPart.add("Z"));
        fixedPart.set(0, "Z");
        checkEquals("fixed size subList still supports set", "Z", array[0]);

        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("A", 1);
        map.put("B", 2);
        expectUoe("keySet add", () -> map.keySet().add("C"));
        expectUoe("values add", () -> map.values().add(3));
        expectUoe("entrySet add", () -> map.entrySet().add(Map.entry("C", 3)));
        map.keySet().remove("A");
        checkEquals("keySet removal updates map", false, map.containsKey("A"));
        Map.Entry<String, Integer> entry = map.entrySet().iterator().next();
        entry.setValue(20);
        checkEquals("entry setValue updates map", 20, map.get("B"));
        Map<String, Integer> readOnlyMap = Collections.unmodifiableMap(map);
        Map.Entry<String, Integer> readOnlyEntry =
                readOnlyMap.entrySet().iterator().next();
        expectUoe("unmodifiable map entry setValue", () -> readOnlyEntry.setValue(30));
        expectUoe("unmodifiable map keySet remove", () -> readOnlyMap.keySet().remove("B"));
        checkEquals("map unchanged by rejected view edits", Map.of("B", 20), map);

        List<String> streamResult = Stream.of("A", "B").toList();
        expectUoe("Stream.toList add", () -> streamResult.add("C"));
        List<String> collected = Stream.of("A", "B")
                .collect(Collectors.toCollection(ArrayList::new));
        collected.add("C");
        checkEquals("explicit mutable collector", List.of("A", "B", "C"), collected);
        List<String> editable = new ArrayList<>(factory);
        editable.add("C");
        checkEquals("mutable copy", List.of("A", "B", "C"), editable);
        checkEquals("factory source preserved", List.of("A", "B"), factory);

        System.out.println("fixed-size list = " + fixed);
        System.out.println("live wrapper = " + live);
        System.out.println("snapshot = " + snapshot);
        System.out.println("mutable subList parent = " + base);
        System.out.println("map after view operations = " + map);
        System.out.println("PASS: " + passed + " checks");
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 UnsupportedOperationsDemo.java
java UnsupportedOperationsDemo
```

Stdout khi tất cả kiểm tra đạt:

```text
fixed-size list = [Z, y]
live wrapper = [X, B]
snapshot = [A, B]
mutable subList parent = [A, X, Y, D]
map after view operations = {B=20}
PASS: 36 checks
```

`expectUoe` chỉ coi UOE là kết quả mong đợi; một lỗi khác vẫn làm chương trình thất bại. Những thao tác sửa bị kiểm tra dùng phần tử hoặc chỉ số hợp lệ, nên không vô tình kiểm tra lỗi đầu vào thay cho giới hạn sửa đổi.

Phần `replaceAll` dùng dữ liệu chữ cái ASCII để tập trung vào khả năng thay phần tử. Trong một bài toán chuẩn hóa ngôn ngữ thực tế, chính sách chữ hoa/thường và locale cần được xác định riêng.

## 8. Đọc stack trace và sửa đúng ý định của mã

### 8.1. Truy ngược đến chỗ tạo collection

Khi gặp UOE, hãy kiểm tra:

1. Dòng mã nào yêu cầu sửa: `add`, `remove`, `set`, `sort`, `replaceAll`, iterator hay thao tác trên entry?
2. Đối tượng được tạo bởi constructor, factory, wrapper hay view nào?
3. Hợp đồng của đối tượng có hỗ trợ thao tác đó không?
4. Ý định của chương trình là sửa nguồn, sửa một bản sao hay chỉ đọc?

Tên lớp nội bộ trong stack trace có thể giúp truy tìm nguồn gốc, nhưng không nên trở thành điều kiện rẽ nhánh trong mã ứng dụng hoặc assertion phụ thuộc tên triển khai.

### 8.2. Cần bản có thể sửa: tạo collection thuộc quyền sửa của mình

Ví dụ:

```java
List<String> editable = new ArrayList<>(readOnlyValues);
editable.add("new-value");
```

Đoạn minh họa tạo cấu trúc mới có thể sửa; nó không làm `readOnlyValues` đổi khả năng. Với map có thể dùng `new HashMap<>(sourceMap)` hoặc `new LinkedHashMap<>(sourceMap)` tùy bảo đảm cần giữ.

Phải kiểm tra hệ quả:

- Sửa bản sao không cập nhật cấu trúc nguồn.
- Các đối tượng phần tử có thể vẫn dùng chung tham chiếu.
- Việc chuyển loại collection có thể ảnh hưởng thứ tự, cách xử lý khóa hoặc khả năng chứa dữ liệu đặc biệt.

Không tự sao chép nếu ý định nghiệp vụ là cập nhật đúng nguồn. Trong trường hợp đó cần thay thiết kế/cách cung cấp dữ liệu hoặc gọi API được hỗ trợ trên đối tượng sở hữu nguồn.

### 8.3. Chỉ cần đọc: bỏ thao tác sửa ngoài hợp đồng

Một hàm được thiết kế chỉ đọc không nên sắp xếp trực tiếp tham số rồi mới xử lý, vì `sort` sửa danh sách. Nếu cần dãy đã sắp để tính toán nhưng phải giữ nguồn, sao chép rồi sắp bản sao.

Tương tự, không dùng `clear()` để “dọn dẹp” một collection mà hàm không sở hữu quyền sửa. Việc nhận một tham số kiểu `List` hoặc `Map` không tự cấp quyền thay đổi mọi đối tượng truyền vào.

### 8.4. Đừng bắt UOE rồi bỏ qua

Bỏ qua lỗi có thể khiến dữ liệu mới không bao giờ được thêm nhưng luồng xử lý vẫn báo thành công. Tự động thử sửa nguồn rồi, nếu thất bại, tạo bản sao cũng dễ làm hành vi phụ thuộc dữ liệu và triển khai.

Ngoài ra, các thao tác sửa hàng loạt tổng quát không mang một bảo đảm giao dịch tự động chỉ vì dùng Collections API. Nếu một thao tác gặp lỗi sau khi đã thực hiện một phần công việc, không được mặc nhiên coi toàn bộ trạng thái đã hoàn tác. Hãy dựa vào bảo đảm cụ thể, hoặc chuẩn bị kết quả riêng trước khi thay dữ liệu thuộc quyền sở hữu.

## 9. Kiểm thử giới hạn sửa đổi như một phần của hợp đồng

### 9.1. Kiểm thử cả thao tác bị cấm lẫn khả năng được hứa

Một danh sách chỉ có kích thước cố định cần được kiểm tra khác danh sách không cho sửa:

| Đối tượng | Thao tác nên xác nhận hợp lệ | Thao tác nên xác nhận bị từ chối |
| --- | --- | --- |
| `Arrays.asList` với mảng hai phần tử | `set` phản ánh vào mảng | Thêm phần tử hoặc xóa chỉ số đang có |
| `List.of` có dữ liệu | Đọc nội dung đúng | `add`, `set`, `remove` |
| Unmodifiable wrapper | Quan sát cập nhật hợp lệ từ backing collection | Sửa trực tiếp hoặc qua iterator/view của wrapper |
| `ArrayList.subList` | Sửa qua view cập nhật đúng nguồn | Không tự gắn kỳ vọng UOE cho mọi thao tác sửa |
| View từ map có thể sửa | Xóa một ánh xạ hiện hữu qua view | `add` vào các collection view thông thường |
| View từ unmodifiable map | Đọc ánh xạ đúng | Xóa qua view hoặc `Entry.setValue` |

Không chỉ kiểm tra “ném lỗi đúng” rồi quên kiểm tra dữ liệu còn lại. Với API cụ thể được kiểm tra, xác nhận các thao tác bị từ chối không làm thay đổi dữ liệu mà hợp đồng bảo vệ.

### 9.2. Khoanh vùng đúng lời gọi khi kiểm thử ngoại lệ

Trong môi trường đã có JUnit Jupiter, có thể viết một phương thức kiểm thử tương tự:

```java
List<String> values = List.of("A", "B");
assertThrows(UnsupportedOperationException.class, () -> values.add("C"));
assertEquals(List.of("A", "B"), values);
```

Đây là đoạn minh họa bên trong phương thức kiểm thử có các static import từ `org.junit.jupiter.api.Assertions`. Nó không phải chương trình độc lập.

Tạo dữ liệu trước assertion giúp phân biệt lỗi chuẩn bị dữ liệu với lỗi thao tác đang cần kiểm tra. Đừng dùng chỉ số không hợp lệ hoặc phần tử không hợp lệ rồi kỳ vọng một loại lỗi nhất định khi có nhiều vi phạm cùng lúc.

Với iterator, gọi `next()` trước `remove()` khi mục tiêu là kiểm tra giới hạn sửa. Nếu iterator chưa có phần tử vừa trả về, một đối tượng có thể báo lỗi trạng thái; đó là mục tiêu kiểm thử khác.

### 9.3. Kiểm tra view, snapshot và phần tử ở những ca riêng

Ba kiểm tra độc lập:

- Thử sửa qua API của kết quả: có bị từ chối theo hợp đồng không?
- Sửa nguồn qua đường hợp lệ: kết quả phải nhìn thấy hay giữ nguyên?
- Sửa trạng thái đối tượng phần tử: hợp đồng có yêu cầu độc lập sâu không?

Trộn cả ba rồi kết luận chung “immutable” dễ làm kiểm thử hứa nhiều hơn điều mã thực sự cung cấp.

## 10. Những kết luận dễ sai

| Kết luận | Điểm cần sửa |
| --- | --- |
| “Khai báo List thì chắc add được.” | Phương thức sửa có thể là thao tác tùy chọn trên đối tượng cụ thể. |
| “Arrays.asList không sửa được.” | Không đổi kích thước, nhưng thay phần tử được và phản ánh vào mảng. |
| “Unmodifiable wrapper là một bản sao.” | Nó thường đọc xuyên tới collection nền; cần phân biệt với bản chụp. |
| “Mọi view đều chỉ đọc.” | Sublist của nguồn có thể sửa và các map view hỗ trợ một số hướng xóa. |
| “Map sửa được thì keySet.add cũng được.” | Các collection view không tương đương collection độc lập hỗ trợ thêm. |
| “Không add được nghĩa là phần tử không đổi được.” | Đối tượng phần tử có thể vẫn thay đổi trạng thái. |
| “remove phần tử không có không ném lỗi, vậy remove được.” | Một thao tác không có tác dụng chưa chứng minh khả năng sửa. |
| “Collectors.toList luôn trả ArrayList có thể sửa.” | Hợp đồng không bảo đảm điều đó; chỉ định factory khi cần khả năng rõ ràng. |
| “Bắt UOE là đã xử lý thành công.” | Cần thực hiện được ý định hoặc báo thất bại rõ ràng, không nuốt lỗi. |

## 11. Tự kiểm tra và thực hành

### Câu 1: Vì sao set thành công trên Arrays.asList nhưng thất bại trên List.of?

`Arrays.asList` cung cấp view có kích thước cố định nhưng cho thay thế phần tử. `List.of` trả danh sách không cho sửa qua API, bao gồm cả `set`. Hai factory có hợp đồng khác nhau.

### Câu 2: Unmodifiable list nhìn thấy phần tử mới do nguồn được thêm từ bên ngoài có mâu thuẫn không?

Không. Wrapper chặn sửa qua đường wrapper, nhưng đọc từ nguồn đang liên kết. Đó không phải bảo đảm giữ nguyên bản chụp nội dung.

### Câu 3: Vì sao keySet.remove có thể đổi map nhưng keySet.add lại bị từ chối?

Xóa khóa xác định được ánh xạ cần xóa. Thêm một khóa không cung cấp đủ một cặp khóa/giá trị, và hợp đồng view không hỗ trợ thao tác thêm. Muốn thêm ánh xạ, dùng API map.

### Câu 4: Copy sang ArrayList có giải quyết bất biến sâu của phần tử không?

Không. Nó tạo cấu trúc danh sách có thể sửa và độc lập về cấu trúc, nhưng các tham chiếu phần tử vẫn có thể dùng chung. Cần chính sách riêng cho đối tượng phần tử.

### Bài thực hành

1. Thêm kiểm tra rằng xóa qua iterator của unmodifiable wrapper bị từ chối sau khi đã gọi `next()` hợp lệ.
2. Tạo `subList` từ `Arrays.asList`, xác nhận `set` đổi mảng gốc còn `add` bị từ chối.
3. So sánh `unmodifiableList(source)` với `unmodifiableList(new ArrayList<>(source))` khi nguồn thay đổi sau khi tạo kết quả.
4. Viết hàm trả danh sách đã sắp xếp mà giữ nguyên đầu vào, rồi kiểm thử với `ArrayList`, `List.of` và `Arrays.asList`.
5. Xác định rõ trong một API của bạn: người gọi được sửa kết quả không, kết quả là view hay bản chụp và các phần tử có thể thay đổi không?

Khi gặp UOE, hãy xác định thao tác bị yêu cầu, hợp đồng của đối tượng thực tế và quyền sửa dữ liệu mà chương trình cần. Cách sửa đúng xuất phát từ ba điều đó, thay vì thay tên factory hoặc bắt ngoại lệ theo phỏng đoán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-006">IndexOutOfBoundsException, NoSuchElementException và IllegalStateException</a>

<details>
<summary>Xem chi tiết</summary>

# IndexOutOfBoundsException, NoSuchElementException và IllegalStateException

## 1. Ba câu hỏi khác nhau trước một thao tác collection

Cùng một danh sách rỗng nhưng `list.get(0)`, `list.iterator().next()` và `list.iterator().remove()` có thể thất bại theo ba cách khác nhau. Không nên gom chúng thành một lỗi chung mang tên “collection không có dữ liệu”.

Hãy tách ba câu hỏi:

1. **Địa chỉ có hợp lệ không?** Một chỉ số phải nằm trong miền mà phương thức chấp nhận. Vi phạm miền chỉ số dẫn tới `IndexOutOfBoundsException`.
2. **Có phần tử để lấy không?** Một phương thức yêu cầu phải lấy được phần tử tiếp theo hoặc phần tử đầu/cuối, nhưng không có phần tử đó, có thể ném `NoSuchElementException`.
3. **Lúc này có được thực hiện thao tác không?** Thứ tự lời gọi trước đó hoặc trạng thái hiện tại chưa cho phép thao tác. Ví dụ điển hình là `Iterator.remove()` chưa có phần tử vừa được trả về, hoặc `add` vào hàng đợi hữu hạn đang đầy: `IllegalStateException`.

Tên lớp ngoại lệ là đầu mối. Hợp đồng của **đúng phương thức trên đúng loại đối tượng** mới quyết định cách sửa.

| Lời gọi minh họa | Điều đang được yêu cầu | Lỗi cần phân biệt |
| --- | --- | --- |
| `new ArrayList<String>().get(0)` | Đọc phần tử ở chỉ số 0 | Không có chỉ số phần tử hợp lệ |
| Iterator của một List rỗng gọi `next()` | Trả phần tử kế tiếp | Không còn phần tử để trả |
| Iterator có hỗ trợ xóa gọi `remove()` trước `next()` | Xóa phần tử vừa được iterator trả về | Chưa có phần tử như vậy trong trạng thái con trỏ |
| `ArrayBlockingQueue` đã đầy gọi `add(value)` | Chèn ngay một phần tử mới | Trạng thái sức chứa không cho phép chèn |

Bài này dùng Java 17+ và collection chuẩn. Các thí nghiệm chạy một luồng để tách lỗi hợp đồng khỏi tranh chấp giữa các luồng.

## 2. IndexOutOfBoundsException: phân biệt chỉ số phần tử và vị trí chèn

### 2.1. Có n phần tử nhưng có n + 1 vị trí đặt con trỏ

Với danh sách có `n = size()` phần tử:

- Chỉ số của **phần tử hiện hữu** là từ `0` đến `n - 1`.
- **Vị trí chèn** hoặc vị trí khởi tạo `ListIterator` là từ `0` đến `n`.

Hai miền này khác nhau ở biên phải:

```text
Danh sách:           A       B       C
Chỉ số phần tử:      0       1       2
Vị trí chèn:     0       1       2       3
size() = 3

get(2)       -> đọc C
get(3)       -> vượt miền phần tử
add(3, D)    -> chèn D vào cuối, vị trí hợp lệ
listIterator(3) -> con trỏ ở cuối; next() không còn phần tử
```

Đừng diễn giải `size()` là “chỉ số cuối”. Nó là số phần tử, và trong các phương thức thích hợp còn là vị trí ngay sau phần tử cuối.

| Phương thức List | Miền hợp lệ của đối số chỉ số |
| --- | --- |
| `get(index)` | `0 <= index < size()` |
| `set(index, value)` | `0 <= index < size()` |
| `remove(index)` | `0 <= index < size()` |
| `add(index, value)` | `0 <= index <= size()` |
| `listIterator(index)` | `0 <= index <= size()` |

`set(size(), value)` không phải cách nối thêm vào cuối: `set` thay một phần tử đang tồn tại. Muốn chèn, dùng `add` theo đúng ý định. Các miền chỉ số được công bố trong [hợp đồng List của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

Với List rỗng, không có chỉ số nào hợp lệ để `get`, `set` hoặc xóa theo chỉ số. Tuy nhiên, vị trí chèn `0` vẫn hợp lệ nếu List hỗ trợ thêm.

### 2.2. Chỉ số của view được tính trong chính view

Một đoạn con dùng quy ước nửa mở `[fromIndex, toIndex)`: lấy từ đầu bao gồm `fromIndex`, dừng trước `toIndex`.

```text
base = [A, B, C, D]
part = base.subList(1, 3) = [B, C]

part.get(0) -> B
part.get(1) -> C
part.get(2) -> vượt miền của part, dù base vẫn có chỉ số 2
```

`subList(size(), size())` tạo một view rỗng ở cuối. View rỗng vẫn không có phần tử ở chỉ số `0`.

Giữ các đầu mút trong miền của danh sách, với đầu trái không lớn hơn đầu phải. Đừng biến bài kiểm tra chỉ số thành bài kiểm tra cùng lúc nhiều đầu mút sai rồi suy ra một thứ tự ưu tiên ngoại lệ cho mọi triển khai. View cũng có quy tắc liên kết với nguồn; một view bị vô hiệu hóa do sửa cấu trúc nguồn bằng đường khác là vấn đề riêng, không được xử lý bằng cách sửa chỉ số theo phỏng đoán.

### 2.3. Những nguyên nhân thường gặp

**Sai dấu trong điều kiện vòng lặp.** Đoạn sau sai khi đi tới `i == size()`:

```java
// Đoạn minh họa sai; values là một List đã có trong phương thức.
for (int i = 0; i <= values.size(); i++) {
    System.out.println(values.get(i));
}
```

Nếu chỉ đọc và không cần chỉ số, vòng `for` mở rộng giúp biểu đạt đúng mục đích. Nếu cần chỉ số, dùng `i < values.size()` trong điều kiện phù hợp, đồng thời xác định rõ danh sách có được thay đổi trong lúc duyệt hay không.

**Lấy phần tử cuối khi rỗng.** `get(size() - 1)` trên List rỗng trở thành `get(-1)`. Công thức đúng khi có phần tử, nhưng chưa tự xử lý trường hợp rỗng.

**Dùng kết quả tìm kiếm mà bỏ qua trường hợp không tìm thấy.**

```java
// Đoạn minh họa bên trong phương thức; names hỗ trợ thao tác cần dùng.
int index = names.indexOf("An");
if (index >= 0) {
    names.set(index, "An Nguyen");
}
```

Khi không tìm thấy, `indexOf` trả `-1`; giá trị đó không phải chỉ số hợp lệ để đọc hoặc thay thế. Việc không tìm thấy có thể là kết quả bình thường của bài toán. Xử lý nó trước thao tác theo chỉ số.

**Lưu chỉ số rồi thay đổi danh sách.** Xóa phần tử làm các phần tử phía sau dịch trái. Một chỉ số từng trỏ tới một mục có thể trỏ tới mục khác hoặc vượt miền sau thay đổi. Với yêu cầu xóa trong lúc duyệt, chọn iterator hỗ trợ xóa hoặc một chiến lược áp dụng chỉ số đã được xác định rõ. Chỉ đổi `<=` thành `<` không sửa được sai lệch về danh tính phần tử.

## 3. remove(int) và remove(Object): cùng tên, hai hợp đồng

Với `List<Integer>`, lời gọi sau **không** có nghĩa xóa số `20`:

```java
// Đoạn minh họa bên trong phương thức; cần import java.util.List và ArrayList.
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
numbers.remove(20); // Chọn remove(int index): yêu cầu xóa chỉ số 20.
```

Danh sách chỉ có ba phần tử, nên chỉ số `20` không hợp lệ. Nếu muốn xóa theo giá trị:

```java
boolean removed = numbers.remove(Integer.valueOf(20));
```

Ở đây đối số có kiểu `Integer`, nên lời gọi chọn `remove(Object)`: xóa lần xuất hiện đầu tiên bằng giá trị đó nếu có, rồi trả kết quả boolean.

Điểm cần kiểm thử là **phương thức thực sự được chọn**, không chỉ dữ liệu đang lưu:

- `remove(0)` trên List là xóa theo chỉ số.
- `remove(Integer.valueOf(0))` trên `List<Integer>` là xóa theo giá trị.
- `Queue.remove()` không có đối số là lấy và xóa phần tử đầu.
- `queue.remove(value)` là một lời gọi khác, tìm và xóa một phần tử bằng giá trị.

Một Stack Trace có chữ `remove` chưa đủ để phân loại lỗi. Xem chữ ký phương thức và kiểu tĩnh của đối số.

## 4. NoSuchElementException: yêu cầu phải trả phần tử nhưng không có

### 4.1. Iterator.next không phải phép dò xem có dữ liệu

`Iterator.next()` phải trả phần tử kế tiếp. Nếu lượt duyệt không còn phần tử, nó ném `NoSuchElementException`. `hasNext()` dùng để kiểm tra khả năng đi tiếp trong điều kiện sử dụng iterator hợp lệ. [Hợp đồng Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

Mẫu đọc thông thường:

```java
// Mẫu áp dụng bên trong phương thức; iterator đã được tạo từ nguồn phù hợp.
while (iterator.hasNext()) {
    Object value = iterator.next();
    System.out.println(value);
}
```

Một lỗi dễ bị bỏ sót là gọi `next()` hai lần cho một lần kiểm tra:

```java
// Sai: một lần hasNext chỉ xác nhận còn ít nhất một phần tử.
while (iterator.hasNext()) {
    System.out.println(iterator.next());
    System.out.println(iterator.next()); // có thể vượt cuối khi chỉ còn một phần tử
}
```

Với hai phần tử, đoạn này có thể kết thúc không lỗi; với một hoặc ba phần tử, lần next thứ hai ở vòng cuối không còn dữ liệu. Vì thế kiểm thử chỉ với một đầu vào có số lượng chẵn có thể che mất lỗi.

Với `ListIterator`, `previous()` có tình huống đối xứng: con trỏ ở đầu thì không có phần tử trước đó. `hasPrevious()` kiểm tra hướng lùi. Một con trỏ hợp lệ ở cuối không có phần tử tiếp theo nhưng có thể có phần tử phía trước.

### 4.2. Queue và Deque có hai họ phương thức ở đầu mút

Java cung cấp cặp phương thức để người viết chọn cách biểu đạt trường hợp rỗng:

| Mục đích | Yêu cầu phải có phần tử | Trả giá trị đặc biệt khi rỗng |
| --- | --- | --- |
| Đọc đầu Queue | `element()` ném `NoSuchElementException` | `peek()` trả `null` |
| Lấy và xóa đầu Queue | `remove()` ném `NoSuchElementException` | `poll()` trả `null` |
| Đọc đầu Deque | `getFirst()` ném `NoSuchElementException` | `peekFirst()` trả `null` |
| Đọc cuối Deque | `getLast()` ném `NoSuchElementException` | `peekLast()` trả `null` |
| Lấy và xóa đầu Deque | `removeFirst()` ném `NoSuchElementException` | `pollFirst()` trả `null` |
| Lấy và xóa cuối Deque | `removeLast()` ném `NoSuchElementException` | `pollLast()` trả `null` |

`Deque.pop()` tương đương lấy-xóa đầu bằng `removeFirst()`, nên pop từ ngăn xếp rỗng cũng ném `NoSuchElementException`. Đây là hành vi được công bố của [Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html) và [Deque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html).

Chọn phương thức theo ý nghĩa nghiệp vụ:

- Nếu rỗng là điều bình thường, chẳng hạn chưa có việc để xử lý trong lượt này, `poll` và kiểm tra kết quả thường biểu đạt đúng hơn.
- Nếu trước đó chương trình đã thiết lập điều kiện phải có một phần tử, `remove()` hoặc `element()` có thể giúp phát hiện việc điều kiện bị phá.
- Đừng đổi mọi lời gọi sang `poll` rồi bỏ qua kết quả. Có thể chỉ chuyển lỗi hợp đồng thành dữ liệu bị mất âm thầm.

### 4.3. Giá trị null cần được xử lý trước khi dùng

Trong ví dụ đầy đủ, các hàng đợi dùng `ArrayDeque` hoặc `ArrayBlockingQueue`, đều không nhận phần tử `null`. Vì thế `null` từ `poll` có thể được dùng rõ ràng để nhận biết rỗng.

Không mặc định mọi triển khai Queue đều có chính sách null giống nhau. Queue cho phép null có thể làm giá trị đặc biệt trở nên khó phân biệt với dữ liệu. Nên xác định chính sách dữ liệu ngay ở biên API.

Đoạn sau có thể ném `NullPointerException` do tự mở hộp, dù đã tránh `NoSuchElementException`:

```java
// Đoạn minh họa sai khi queue rỗng; queue có kiểu Queue<Integer>.
int value = queue.poll();
```

Đọc vào `Integer`, kiểm tra `null`, rồi mới sử dụng giá trị. Việc thay phương thức phải đi cùng xử lý hợp đồng kết quả của nó.

## 5. IllegalStateException trên iterator: lỗi trình tự lời gọi

### 5.1. remove xóa phần tử vừa được trả về

Với iterator có hỗ trợ xóa, `remove()` không có đối số vì nó đã gắn với **phần tử gần nhất được chính iterator đó trả về bởi next()**.

Quy tắc cơ bản:

1. Gọi `next()` thành công để có phần tử vừa trả về.
2. Có thể xóa phần tử đó bằng `remove()` tối đa một lần.
3. Muốn xóa một phần tử tiếp theo, phải tiếp tục gọi `next()` thành công.

```text
Tạo iterator
    remove chưa hợp lệ
        |
        | next thành công
        v
Có phần tử vừa trả về
    remove hợp lệ một lần
        |
        | remove thành công
        v
Chưa có phần tử mới để xóa
    remove tiếp ngay -> IllegalStateException
```

`hasNext()` chỉ kiểm tra khả năng đi tiếp; nó không tạo “phần tử vừa trả về”. Vì thế gọi `hasNext()` rồi `remove()` ngay sau khi tạo iterator vẫn chưa đúng trình tự.

Một chi tiết khác: sau khi `next()` trả phần tử cuối, `hasNext()` có thể là false, nhưng quyền xóa phần tử cuối vừa trả về vẫn còn nếu chưa xóa nó. “Không còn phần tử tiếp theo” và “không có phần tử vừa trả về để xóa” là hai trạng thái khác nhau.

Các quy tắc trên cần đặt trong hợp đồng iterator cho phép sửa. Iterator không hỗ trợ `remove` có thể ném `UnsupportedOperationException`; gọi `next` không biến một thao tác bị cấm thành thao tác được hỗ trợ. Không tạo ca thử vừa sai trạng thái vừa không hỗ trợ xóa rồi dùng nó để khẳng định ngoại lệ nào luôn thắng.

### 5.2. ListIterator: vị trí con trỏ khác phần tử vừa trả về

`ListIterator` có con trỏ ở **khoảng giữa các phần tử**, còn `set` và `remove` tác động lên phần tử gần nhất được `next` hoặc `previous` trả về. Đó không phải một “phần tử hiện tại” luôn có sẵn chỉ vì đã tạo con trỏ.

Với ListIterator hỗ trợ các thao tác sửa tương ứng:

| Trình tự | Điều cần hiểu |
| --- | --- |
| Mới tạo, chưa đi bằng `next`/`previous` | `set` và `remove` chưa hợp lệ |
| Vừa đi bằng `next` hoặc `previous` | Có thể `set` phần tử vừa trả về; có thể `remove` nếu chưa add/remove sau lần đi đó |
| Gọi `set` nhiều lần sau một lần đi | Được phép nếu không có add/remove xen vào |
| Gọi `remove` thành công | `set` hoặc `remove` tiếp ngay chưa hợp lệ |
| Gọi `add` thành công | Phải đi lại bằng next/previous trước khi set/remove |
| Gọi `add` khi mới tạo con trỏ | Không cần một lần next/previous trước đó |

Theo [hợp đồng ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html), `add` chèn trước phần tử mà `next()` sẽ trả, nếu có. Sau khi thêm, `previous()` trả phần tử vừa thêm, còn một lần `next()` sẽ đi tới phần tử vốn nằm sau vị trí chèn.

Chẳng hạn:

```text
values = [A, C], con trỏ ở đầu
add(B)  -> values = [B, A, C], con trỏ nằm giữa B và A
set(X)  -> chưa hợp lệ: add vừa làm mất điều kiện set/remove
next()  -> trả A
set(X)  -> values = [B, X, C]
set(Y)  -> values = [B, Y, C], vẫn hợp lệ
remove()-> values = [B, C]
set(Z)  -> chưa hợp lệ sau remove
```

Hãy kiểm thử **chuỗi thao tác**, không chỉ từng phương thức riêng lẻ. Hai lời gọi `set` với cùng đối số có thể khác kết quả chỉ vì trước đó có hoặc không có `add`.

## 6. IllegalStateException do hàng đợi đầy khác lỗi iterator

Một hàng đợi có giới hạn sức chứa có trạng thái khác: còn chỗ hoặc đã đầy. Với `ArrayBlockingQueue`, `add(value)` vào hàng đợi đầy ném `IllegalStateException`; `offer(value)` không chờ trả `false` khi không còn chỗ. [Tài liệu ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html).

```text
Sức chứa = 1, hàng đợi ban đầu rỗng
add(A)   -> true,  nội dung [A]
add(B)   -> IllegalStateException vì đầy
offer(B) -> false, nội dung vẫn [A]
poll()   -> A, nội dung []
offer(B) -> true, nội dung [B]
```

Đây không phải lỗi “chưa gọi next”. Cùng loại ngoại lệ nhưng điều kiện sử dụng hoàn toàn khác.

Chọn chính sách theo yêu cầu nhận việc:

- `add`: không nhận được là trường hợp cần báo lỗi qua ngoại lệ.
- `offer`: không nhận được ngay là kết quả boolean mà người gọi phải xử lý.
- Các phương thức chờ như `put`, `take` có hợp đồng khác về chờ và ngắt luồng. Không thay bằng chúng chỉ để làm biến mất ngoại lệ; có thể khiến chương trình chờ vô thời hạn nếu không có bên làm thay đổi trạng thái.

Chương trình bên dưới chỉ dùng các thao tác không chờ và không tạo luồng phụ. Khi nhận `false` từ offer, ứng dụng vẫn phải quyết định từ chối, lưu chỗ khác, thử lại theo chính sách hoặc báo người gọi. Boolean không thay thế quyết định đó.

## 7. Chương trình Java 17+ kiểm tra hợp đồng

Lưu mã dưới đây thành **ExceptionContractsDemo.java**. Chương trình dùng `AssertionError` để dừng nếu một kỳ vọng sai; không cần `-ea`, JUnit hoặc thư viện ngoài.

Mỗi thao tác thử ngoại lệ được khoanh trong một lambda ngắn. Hàm trợ giúp chấp nhận ngoại lệ thuộc kiểu đã yêu cầu, kể cả lớp con, nhưng không nuốt một RuntimeException khác. Các nguồn được tạo riêng theo từng nhóm để trình tự của một nhóm không làm sai dữ liệu đầu vào của nhóm khác.

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class ExceptionContractsDemo {
    private static int passed;

    private static void checkEquals(String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected
                    + ", actual=" + actual);
        }
        passed++;
    }

    private static void expectThrows(
            String label, Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            if (!type.isInstance(ex)) {
                throw new AssertionError(label + ": unexpected exception", ex);
            }
            passed++;
            return;
        }
        throw new AssertionError(label + ": expected " + type.getSimpleName());
    }

    private static void checkIndices() {
        List<String> empty = new ArrayList<>();
        expectThrows("empty get", IndexOutOfBoundsException.class, () -> empty.get(0));
        expectThrows("empty set", IndexOutOfBoundsException.class, () -> empty.set(0, "X"));
        expectThrows("empty indexed remove", IndexOutOfBoundsException.class,
                () -> empty.remove(0));
        empty.add(0, "A");
        checkEquals("empty insertion position", List.of("A"), empty);

        List<String> values = new ArrayList<>(List.of("B", "A", "C"));
        expectThrows("negative element index", IndexOutOfBoundsException.class,
                () -> values.get(-1));
        expectThrows("size is not an element index", IndexOutOfBoundsException.class,
                () -> values.get(values.size()));
        expectThrows("set cannot append", IndexOutOfBoundsException.class,
                () -> values.set(values.size(), "D"));
        expectThrows("indexed remove at size", IndexOutOfBoundsException.class,
                () -> values.remove(values.size()));
        checkEquals("last element", "C", values.get(values.size() - 1));
        values.add(values.size(), "D");
        checkEquals("insert at end", List.of("B", "A", "C", "D"), values);
        expectThrows("beyond insertion range", IndexOutOfBoundsException.class,
                () -> values.add(values.size() + 1, "E"));
        checkEquals("half-open slice", List.of("A", "C"), values.subList(1, 3));
        checkEquals("empty slice at end", List.of(),
                values.subList(values.size(), values.size()));

        List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
        expectThrows("remove int selects index", IndexOutOfBoundsException.class,
                () -> numbers.remove(20));
        checkEquals("failed indexed remove preserves values", List.of(10, 20, 30), numbers);
        checkEquals("remove Integer selects value", true, numbers.remove(Integer.valueOf(20)));
        checkEquals("value removed", List.of(10, 30), numbers);
        checkEquals("absent value", false, numbers.remove(Integer.valueOf(99)));
        System.out.println("indices=PASS");
    }

    private static void checkIterator() {
        List<String> values = new ArrayList<>(List.of("A", "B"));
        Iterator<String> cursor = values.iterator();
        expectThrows("remove before next", IllegalStateException.class, cursor::remove);
        checkEquals("first next", "A", cursor.next());
        cursor.remove();
        checkEquals("first removal", List.of("B"), values);
        expectThrows("double remove", IllegalStateException.class, cursor::remove);
        checkEquals("last next", "B", cursor.next());
        checkEquals("no next element", false, cursor.hasNext());
        cursor.remove();
        checkEquals("last returned element can still be removed", List.of(), values);
        expectThrows("exhausted next", java.util.NoSuchElementException.class, cursor::next);

        Iterator<String> readOnly = List.of("A").iterator();
        checkEquals("read-only iterator can read", "A", readOnly.next());
        expectThrows("unsupported remove after valid next", UnsupportedOperationException.class,
                readOnly::remove);
        System.out.println("iterator=PASS");
    }

    private static void checkListIterator() {
        List<String> values = new ArrayList<>(List.of("A", "C"));
        ListIterator<String> cursor = values.listIterator();
        expectThrows("no previous at start", java.util.NoSuchElementException.class,
                cursor::previous);
        expectThrows("set before movement", IllegalStateException.class,
                () -> cursor.set("X"));
        expectThrows("remove before movement", IllegalStateException.class, cursor::remove);

        cursor.add("B");
        checkEquals("add without prior movement", List.of("B", "A", "C"), values);
        expectThrows("set immediately after add", IllegalStateException.class,
                () -> cursor.set("X"));
        expectThrows("remove immediately after add", IllegalStateException.class, cursor::remove);
        checkEquals("next after add", "A", cursor.next());
        cursor.set("X");
        cursor.set("Y");
        checkEquals("repeated set is allowed", List.of("B", "Y", "C"), values);
        cursor.remove();
        checkEquals("remove after set", List.of("B", "C"), values);
        expectThrows("set after remove", IllegalStateException.class, () -> cursor.set("X"));
        expectThrows("remove twice", IllegalStateException.class, cursor::remove);

        checkEquals("next after removal", "C", cursor.next());
        cursor.set("Z");
        checkEquals("replace last returned element", List.of("B", "Z"), values);
        checkEquals("at end", false, cursor.hasNext());
        checkEquals("previous at end", "Z", cursor.previous());
        cursor.remove();
        checkEquals("remove after previous", List.of("B"), values);

        ListIterator<String> end = values.listIterator(values.size());
        checkEquals("end position is valid", false, end.hasNext());
        expectThrows("next at end", java.util.NoSuchElementException.class, end::next);
        expectThrows("negative iterator position", IndexOutOfBoundsException.class,
                () -> values.listIterator(-1));
        expectThrows("too-large iterator position", IndexOutOfBoundsException.class,
                () -> values.listIterator(values.size() + 1));
        System.out.println("listIterator=PASS");
    }

    private static void checkEmptyEndpoints() {
        Queue<String> queue = new ArrayDeque<>();
        expectThrows("empty queue remove", java.util.NoSuchElementException.class,
                () -> queue.remove());
        expectThrows("empty queue element", java.util.NoSuchElementException.class,
                queue::element);
        checkEquals("empty poll", null, queue.poll());
        checkEquals("empty peek", null, queue.peek());
        checkEquals("remove absent value has another contract", false, queue.remove("missing"));
        queue.add("A");
        checkEquals("element reads head", "A", queue.element());
        checkEquals("element does not remove", 1, queue.size());
        checkEquals("remove returns head", "A", queue.remove());
        checkEquals("queue emptied", true, queue.isEmpty());

        Deque<String> stack = new ArrayDeque<>();
        expectThrows("empty getFirst", java.util.NoSuchElementException.class, stack::getFirst);
        expectThrows("empty getLast", java.util.NoSuchElementException.class, stack::getLast);
        expectThrows("empty removeFirst", java.util.NoSuchElementException.class,
                stack::removeFirst);
        expectThrows("empty removeLast", java.util.NoSuchElementException.class, stack::removeLast);
        expectThrows("empty pop", java.util.NoSuchElementException.class, stack::pop);
        checkEquals("empty pollFirst", null, stack.pollFirst());
        checkEquals("empty pollLast", null, stack.pollLast());
        checkEquals("empty peekFirst", null, stack.peekFirst());
        checkEquals("empty peekLast", null, stack.peekLast());
        stack.push("A");
        stack.push("B");
        checkEquals("LIFO first pop", "B", stack.pop());
        checkEquals("LIFO second pop", "A", stack.pop());
        checkEquals("stack emptied", true, stack.isEmpty());
        System.out.println("empty endpoints=PASS");
    }

    private static void checkBoundedQueue() {
        Queue<String> queue = new ArrayBlockingQueue<>(1);
        checkEquals("first add", true, queue.add("A"));
        expectThrows("add to full queue", IllegalStateException.class, () -> queue.add("B"));
        checkEquals("offer to full queue", false, queue.offer("B"));
        checkEquals("failed insertions preserve queue", List.of("A"), new ArrayList<>(queue));
        checkEquals("make room", "A", queue.poll());
        checkEquals("offer after space becomes available", true, queue.offer("B"));
        checkEquals("read new head", "B", queue.element());
        checkEquals("remove new head", "B", queue.remove());
        checkEquals("poll after draining", null, queue.poll());
        System.out.println("bounded queue=PASS");
    }

    public static void main(String[] args) {
        checkIndices();
        checkIterator();
        checkListIterator();
        checkEmptyEndpoints();
        checkBoundedQueue();
        System.out.println("PASS: " + passed + " checks");
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all ExceptionContractsDemo.java
java ExceptionContractsDemo
```

Stdout khi toàn bộ kiểm tra đạt:

```text
indices=PASS
iterator=PASS
listIterator=PASS
empty endpoints=PASS
bounded queue=PASS
PASS: 78 checks
```

Chương trình đã được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2. `--release 17` kiểm tra mức ngôn ngữ và API đích; nó không có nghĩa quá trình chạy sử dụng một JVM 17.

### 7.1. Vì sao có kiểm tra dữ liệu sau thao tác?

Chỉ kiểm tra “có ném ngoại lệ” chưa bảo vệ đủ ý nghĩa của bài toán. Chương trình còn xác nhận:

- Thử xóa chỉ số 20 không biến thành xóa giá trị 20.
- Xóa theo giá trị thật sự bỏ đúng phần tử, và không tìm thấy trả false.
- Xóa qua iterator cập nhật danh sách; lần xóa tiếp theo chỉ hợp lệ sau khi đi tiếp.
- ListIterator cho thay cùng phần tử nhiều lần, nhưng add/remove làm thay đổi điều kiện set/remove.
- `element` không xóa, còn `remove()` và `poll()` lấy-xóa đầu hàng đợi.
- Hai lần pop cho đúng thứ tự ngăn xếp.
- Không chèn được vào hàng đợi đầy không âm thầm thay phần tử A bằng B.
- Giải phóng một chỗ cho phép thao tác chèn sau đó thành công.

Những xác nhận về trạng thái ở đây gắn với các lớp và thao tác cụ thể đã khởi tạo. Không suy ra một bảo đảm hoàn tác tự động cho mọi thao tác hàng loạt hoặc cho mọi collection tùy biến.

### 7.2. Giới hạn của thí nghiệm

Đây là kiểm tra hợp đồng trên dữ liệu nhỏ trong một luồng, không phải kiểm thử đồng thời hoặc đo hiệu năng. `ArrayList` trong ví dụ có hỗ trợ các thao tác sửa; `List.of` chỉ xuất hiện ở ca đối chiếu iterator không cho sửa và để tạo expected.

Không khẳng định mọi cách dùng sai đều phải ném một trong đúng ba ngoại lệ ở tiêu đề. Sai kiểu, null, thao tác không hỗ trợ hoặc sửa nguồn ngoài chính sách có thể dẫn đến loại lỗi khác. Cần tạo ca thử có **một điều kiện vi phạm rõ ràng**.

## 8. Quy trình gỡ lỗi: đọc phương thức, đầu vào và trạng thái

### 8.1. Khi gặp IndexOutOfBoundsException

Ghi lại:

1. Phương thức là get/set/remove theo chỉ số, add ở vị trí hay khởi tạo con trỏ?
2. Giá trị index tại lúc gọi và kích thước danh sách tại lúc đó?
3. Chỉ số tính trong danh sách gốc hay một view nhỏ hơn?
4. Chỉ số lấy từ vòng lặp, indexOf, dữ liệu nhập hay được lưu trước một thay đổi?
5. Với remove, overload nào đã được chọn?

Sửa nguyên nhân tạo địa chỉ sai. Không kẹp mọi chỉ số về đoạn hợp lệ một cách máy móc: biến chỉ số 100 thành phần tử cuối có thể làm chương trình sửa nhầm dữ liệu nhưng không còn báo lỗi.

### 8.2. Khi gặp NoSuchElementException

Xác định đúng hướng và điểm quan sát:

- Iterator đã đọc hết chưa, có vô tình gọi next hai lần không?
- ListIterator đang ở đầu hay cuối, đang đi tiến hay lùi?
- Queue/Deque thật sự rỗng hay phần tử đã bị lấy bởi một đường khác?
- Hợp đồng có cho phép rỗng không, và phương thức nên dùng ngoại lệ hay giá trị đặc biệt?

Nếu thiếu dữ liệu là một tình huống bình thường, biểu đạt nó rõ ràng. Nếu đó là vi phạm một điều kiện phải đúng, giữ thông tin giúp tìm nơi đã làm mất phần tử. Đừng bắt ngoại lệ rồi tự tạo một giá trị mặc định giả mà nghiệp vụ không cho phép.

### 8.3. Khi gặp IllegalStateException

Tên ngoại lệ không mô tả đủ trạng thái. Với iterator, ghi chuỗi `next/previous/add/set/remove` liên quan. Với hàng đợi hữu hạn, ghi sức chứa và kết quả chèn/lấy trước đó.

Một bản ghi thao tác nhỏ như sau thường hữu ích hơn chỉ in size:

```text
create iterator
next -> A
remove -> thành công
remove -> IllegalStateException
```

Size của danh sách ở cuối có thể vẫn lớn hơn 0, nhưng điều đó không cấp quyền xóa lần hai bằng cùng iterator. Điều kiện bị vi phạm là trình tự lời gọi, không phải còn dữ liệu hay không.

Không bắt `IllegalStateException` của toàn bộ một phương thức lớn rồi giả định “queue đang đầy”. Một lời gọi khác bên trong có thể là nguồn lỗi. Khoanh phạm vi thao tác và đọc stack trace.

## 9. Kiểm tra trước thao tác không tự tạo tính nguyên tử

Trong một luồng và khi không có mã khác can thiệp vào nguồn, kiểm tra index hoặc hasNext có thể giúp chọn thao tác đúng. Khi trạng thái có thể đổi giữa hai lời gọi, một phép kiểm tra trước chưa đủ.

Ví dụ minh họa một cách xử lý dễ nhầm:

```java
// Đoạn minh họa: hai lời gọi riêng, không phải một giao dịch.
if (!queue.isEmpty()) {
    String value = queue.remove();
    System.out.println(value);
}
```

Với hàng đợi được nhiều bên lấy phần tử, queue có thể rỗng sau lần kiểm tra isEmpty. Nếu API hàng đợi đồng thời được chọn có cung cấp thao tác lấy an toàn và yêu cầu cho phép không có kết quả, hãy gọi `poll()` rồi xử lý chính kết quả của lời gọi đó. Chọn collection đồng thời hoặc đồng bộ đúng phạm vi vẫn là yêu cầu riêng; đổi `remove` thành `poll` không làm một `ArrayDeque` chia sẻ không đồng bộ trở nên an toàn.

Tương tự, kiểm tra `index < list.size()` rồi gọi `get(index)` không bảo vệ một List đang bị sửa không đồng bộ. Cần giải quyết quyền truy cập nguồn, không dùng vòng lặp bắt IndexOutOfBoundsException rồi thử lại để thay cho đồng bộ.

Với iterator, sửa nguồn ngoài đường được hợp đồng cho phép còn có thể dẫn tới `ConcurrentModificationException` hoặc hành vi không được xác định. Những bảo đảm về hasNext/next phải được hiểu trong điều kiện sử dụng hợp lệ của iterator.

## 10. Thiết kế các ca kiểm thử có sức phân biệt

### 10.1. Kiểm tra cả hai phía của ranh giới

Với danh sách có kích thước n, chọn các điểm như `-1`, `0`, `n - 1`, `n` và `n + 1`, nhưng tính ý nghĩa từng điểm theo n thực tế:

- Khi n = 0, `n - 1` cũng là -1; không còn ca “phần tử cuối hợp lệ”.
- Khi n = 1, chỉ số phần tử duy nhất là 0, vị trí chèn có cả 0 và 1.
- Với n lớn hơn, index = n phân biệt rõ thao tác đọc/thay với thao tác chèn.

Không đếm các giá trị trùng nhau trong bảng mẫu thành bằng chứng đã thử thêm một vùng hành vi mới.

### 10.2. Kiểm tra chuyển trạng thái, không chỉ trạng thái đầu

| Nhóm | Ca chưa đủ | Ca bổ sung quan trọng |
| --- | --- | --- |
| Chỉ số | Chỉ thử get(0) trên List có dữ liệu | Rỗng, chỉ số âm, index = size và add tại size |
| Iterator | Chỉ thử next khi còn dữ liệu | Cạn; remove trước next; double remove; remove sau next cuối |
| ListIterator | Chỉ thử set ngay sau next | set trước khi đi, sau add/remove, set lặp và remove sau previous |
| Queue | Chỉ thử lấy từ queue có dữ liệu | Cặp remove/poll, element/peek khi rỗng |
| Sức chứa | Chỉ thử add khi còn chỗ | add thất bại, offer false, lấy ra rồi chèn lại |
| Ngăn xếp | Chỉ thử pop một lần | Rỗng, một phần tử, hai phần tử và thứ tự LIFO |

Với ListIterator, các phép quan sát `nextIndex()` và `previousIndex()` cũng hữu ích để chẩn đoán vị trí, nhưng việc biết index không thay điều kiện “đã có phần tử vừa trả về” của set/remove.

### 10.3. Không đặt kỳ vọng mạnh hơn hợp đồng

Trong kiểm thử ứng dụng:

- Chỉ bắt ngoại lệ quanh đúng lời gọi đang kiểm tra, sau khi đã chuẩn bị dữ liệu hợp lệ.
- Khi hợp đồng công bố IndexOutOfBoundsException, không bắt buộc một lớp con nội bộ hay chuỗi thông báo cụ thể, trừ khi API của chính ứng dụng cam kết điều đó.
- Không dựa vào số dòng stack trace hoặc tên lớp iterator nội bộ; chúng có thể đổi theo JDK.
- Không trộn lỗi chỉ số, thao tác không hỗ trợ và lỗi trạng thái trong cùng một ca rồi suy ra thứ tự kiểm tra cho mọi triển khai.
- Kiểm tra nội dung, vị trí hoặc số lần xử lý sau thao tác hợp lệ, không chỉ kiểm tra chương trình “không ném lỗi”.
- Không dùng một lần chạy không lỗi làm bằng chứng nguồn đang bị sửa đồng thời là an toàn.

Trong JUnit Jupiter, ý tưởng tương ứng là dùng `assertThrows` cho một lambda ngắn và assertion khác cho trạng thái sau thao tác. Chương trình của bài dùng hàm trợ giúp tự kiểm tra để chạy độc lập, nên không yêu cầu thiết lập JUnit.

## 11. Nhận diện các lỗi ở gần nhưng khác nguyên nhân

Ba ngoại lệ trong bài đều là ngoại lệ không bắt buộc khai báo hay bắt bằng checked exception. Điều đó không có nghĩa chương trình nên bỏ qua chúng; nhiều ca phản ánh lỗi sử dụng API cần sửa ở nguồn.

| Tình huống | Loại vấn đề cần xem xét |
| --- | --- |
| Chỉ số List nằm ngoài miền phương thức | IndexOutOfBoundsException |
| Iterator.next hoặc Queue.remove không có phần tử để trả | NoSuchElementException |
| Iterator.remove chưa đúng trình tự; add vào ArrayBlockingQueue đầy | IllegalStateException |
| add vào List.of hoặc remove qua iterator không hỗ trợ sửa | UnsupportedOperationException |
| Sửa nguồn ngoài chính sách iterator fail-fast | ConcurrentModificationException có thể phát hiện vi phạm |
| Tự mở hộp kết quả null từ poll | NullPointerException |

Bảng là định hướng theo các tình huống đã nêu, không phải danh sách loại trừ mọi lỗi khác. Khi đối tượng đồng thời bị dùng sai nhiều điều kiện, đọc hợp đồng cụ thể thay vì chọn ngoại lệ theo cảm giác.

Một cách sửa tốt phải trả lời được hai điều: điều kiện nào cần đúng trước thao tác, và ai chịu trách nhiệm duy trì điều kiện đó. Người gọi có thể cần kiểm tra đầu vào; bên sở hữu collection có thể cần cung cấp API phù hợp; thuật toán có thể cần đổi cách duyệt hoặc cách xử lý trạng thái rỗng/đầy.

## 12. Tự kiểm tra và thực hành

### Câu 1: List có ba phần tử, vì sao add(3, value) hợp lệ còn set(3, value) không?

Add dùng vị trí chèn, có cả vị trí sau phần tử cuối. Set cần một phần tử đang tồn tại; chỉ số phần tử chỉ tới 2.

### Câu 2: hasNext trả false có đồng nghĩa remove phải ném IllegalStateException không?

Không. Nếu next vừa trả phần tử cuối và chưa xóa phần tử đó, remove vẫn có thể hợp lệ với iterator hỗ trợ xóa. HasNext mô tả phần tử tiếp theo, còn remove dựa vào phần tử đã trả về.

### Câu 3: Đổi Queue.remove thành poll có đủ sửa lỗi không?

Chưa. Phải quyết định rỗng có hợp lệ không và xử lý null. Cũng cần giữ đúng chính sách đồng thời của collection; hai tên phương thức không thay thế thiết kế truy cập nguồn.

### Câu 4: ListIterator có thể set hai lần sau một next không?

Có, nếu nó hỗ trợ set và chưa có add/remove xen vào. Một lần set không tiêu thụ quyền thay thế như cách remove làm mất điều kiện xóa lần nữa.

### Câu 5: IllegalStateException có luôn liên quan đến iterator không?

Không. Hàng đợi hữu hạn đầy là một ví dụ khác. Cần nhìn phương thức ném lỗi và trạng thái của đối tượng đó.

### Bài thực hành

1. Thêm các ca kiểm tra get/set/add trên List một phần tử; ghi riêng miền chỉ số phần tử và vị trí chèn.
2. Viết một phép thử chứng minh `remove(1)` và `remove(Integer.valueOf(1))` có thể xóa hai thứ khác nhau trên cùng dữ liệu ban đầu.
3. Theo dõi `nextIndex()` trước và sau chuỗi ListIterator: add, next, set, remove, previous. Dự đoán danh sách sau từng bước trước khi chạy.
4. Tạo deque có hai phần tử và so sánh getFirst, peekFirst, removeFirst, pollFirst; xác nhận thao tác nào giữ nguyên kích thước.
5. Với hàng đợi sức chứa 2, viết ca đầy rồi lấy một phần tử và chèn lại. Đối chiếu cả kết quả boolean, ngoại lệ và nội dung theo thứ tự.
6. Tạo một lỗi gọi next hai lần trong vòng while, thử dữ liệu có 0, 1, 2 và 3 phần tử, rồi sửa để mỗi phần tử được xử lý đúng một lần.
7. Thiết kế một hàm có yêu cầu “rỗng là bình thường” và một hàm có yêu cầu “phải có phần tử”. Chọn rõ API và kết quả lỗi phù hợp cho từng hàm.

Mục tiêu là nhìn được địa chỉ, phần tử khả dụng và trình tự thao tác như ba điều kiện riêng. Khi một điều kiện sai, sửa cách tạo hoặc duy trì điều kiện đó, rồi kiểm thử lại cả kết quả lẫn thay đổi dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-007">ClassCastException, NullPointerException và kiểu dữ liệu không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# ClassCastException, NullPointerException và kiểu dữ liệu không hợp lệ trong Java Collections

## 1. Mục tiêu

Trong Java Collections (`List`, `Set`, `Queue`, `Map`), lỗi có thể xuất hiện lúc thêm, đọc, sắp xếp, chuyển đổi hoặc sử dụng phần tử. Cần phân biệt ba nhóm:

- **`ClassCastException`**: một đối tượng bị ép sang kiểu mà đối tượng thực tế không thuộc về. Với generic, lỗi có thể phát sinh tại `get()` do trình biên dịch chèn phép ép kiểu ngầm.
- **`NullPointerException` (NPE)**: dereference `null`, tự động unboxing giá trị `null` hoặc truyền null vào factory/API không chấp nhận null.
- **Kiểu/giá trị không hợp lệ**: dữ liệu bên ngoài vi phạm hợp đồng kiểu hoặc quy tắc nghiệp vụ (ví dụ nhận chuỗi `"90"` ở nơi cần `Integer`, hoặc điểm số `120` ở nơi chỉ nhận 0–100).

Mục tiêu là xác định điểm gây lỗi, phòng tránh bằng generic và validation, đồng thời kiểm thử cả luồng đúng và luồng sai trên Java 17+.

## 2. Nhận diện đúng loại lỗi

| Hiện tượng | Exception | Giải thích |
| --- | --- | --- |
| Đọc `String` nhưng phần tử thực là `Integer` | `ClassCastException` | Cast sai; thường do raw type/unchecked cast làm ô nhiễm kiểu trong collection. |
| Gọi phương thức trên phần tử `null` | `NullPointerException` | `null` không phải đối tượng để dereference. |
| Lấy `Integer` null rồi gán vào `int` | `NullPointerException` | Unboxing cần giá trị không null. |
| Tạo `List.of("A", null)`, `List.copyOf` có null | `NullPointerException` | Các factory này cấm phần tử null. |
| Dữ liệu đúng kiểu nhưng ngoài miền chấp nhận | `IllegalArgumentException` nếu chủ động validate | Đây là lỗi hợp đồng dữ liệu, không phải ép kiểu. |
| Parse `"12x"` thành số nguyên | `NumberFormatException` | Chuỗi không đúng dạng số, khác với cast từ `String` sang `Integer`. |
| Copy phần tử `Integer` vào `String[]` qua `toArray(T[])` | `ArrayStoreException` | Mảng đích có kiểu thành phần không tương thích. |
| Thêm vào `List.of` hoặc `List.copyOf` | `UnsupportedOperationException` | Kết quả không sửa đổi được; không liên quan đến kiểu phần tử. |
| Đọc chỉ số ngoài kích thước danh sách | `IndexOutOfBoundsException` | Lỗi biên chỉ số, không phải NPE hoặc ClassCastException. |

Tên exception cho biết triệu chứng tại một thao tác cụ thể; vị trí dữ liệu sai đi vào hệ thống có thể ở một dòng khác. Không bắt mọi `RuntimeException` rồi trả về giá trị mặc định: điều đó dễ che mất lỗi dữ liệu.

## 3. ClassCastException và generic

### 3.1. Vì sao generic vẫn có thể lỗi lúc chạy?

`List<String>` giúp trình biên dịch từ chối `names.add(42)` nếu `names` được khai báo đúng kiểu. Nhưng Java sử dụng **type erasure**: phần lớn tham số generic không được giữ để kiểm tra tự động từng phần tử ở runtime. Raw type, cast không kiểm tra (`unchecked cast`), reflection hay một thư viện cũ có thể đưa sai kiểu vào danh sách.

```java
List<String> names = new ArrayList<>();
List raw = names;         // Raw type: mất kiểm tra generic.
raw.add(42);             // Chèn Integer vào List<String>.
String first = names.get(0); // ClassCastException tại điểm đọc.
```

Đoạn trên cần import `java.util.ArrayList` và `java.util.List`; trình biên dịch cảnh báo về raw/unchecked operation. Không dùng cách này trong production. Phần tử sai đã được chèn trước khi exception xuất hiện; sửa dòng `get` mà không sửa điểm `add` chỉ chữa triệu chứng.

Không thể viết `value instanceof List<String>`: tham số `String` bị xóa. `value instanceof List<?>` chỉ xác nhận đối tượng là `List`; muốn tin rằng mọi phần tử là `String`, phải kiểm tra các phần tử hoặc nhận collection từ nguồn có hợp đồng kiểu đáng tin cậy.

### 3.2. Chạy một bài lab tái hiện lỗi

Lưu thành tệp `CollectionFailureDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class CollectionFailureDemo {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<String> pollutedList() {
        List<String> names = new ArrayList<>();
        List raw = names;
        raw.add(42); // Cố ý sai để mô phỏng API cũ.
        return names;
    }

    static void show(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + ": OK");
        } catch (RuntimeException ex) {
            System.out.println(label + ": " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        show("wrong-cast", () -> {
            String name = pollutedList().get(0);
            System.out.println(name);
        });

        show("null-element", () -> {
            List<String> names = new ArrayList<>();
            names.add(null);
            System.out.println(names.get(0).length());
        });

        show("factory-null", () -> {
            List.of("An", null);
        });

        show("null-unboxing", () -> {
            List<Integer> values = new ArrayList<>();
            values.add(null);
            int number = values.get(0);
            System.out.println(number);
        });
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac CollectionFailureDemo.java
java CollectionFailureDemo
```

Stdout:

```text
wrong-cast: ClassCastException
null-element: NullPointerException
factory-null: NullPointerException
null-unboxing: NullPointerException
```

`@SuppressWarnings` chỉ được dùng trong ví dụ mô phỏng mã không an toàn; nó không kiểm tra hoặc sửa nội dung collection. Hàm `show` bắt exception để minh họa; ứng dụng thật nên để lỗi bất ngờ được báo rõ hoặc chuyển sang lỗi có ngữ cảnh tại ranh giới phù hợp.

### 3.3. Cách tránh ép kiểu sai

Với một giá trị `Object` đến từ nguồn không đáng tin cậy:

```java
Object value = 42;

if (value instanceof String text) {
    System.out.println(text.length());
} else {
    System.out.println("Không phải String");
}
```

Kết quả: `Không phải String`. `instanceof` với `null` cho kết quả `false`. Tuy nhiên, nếu hợp đồng yêu cầu *mọi* phần tử đều là `String`, chỉ im lặng bỏ qua phần tử sai sẽ làm mất dữ liệu. Tốt hơn là validate toàn bộ đầu vào, chỉ rõ chỉ số và loại giá trị sai, rồi trả về collection đã được chuẩn hóa.

`Collections.checkedList(new ArrayList<String>(), String.class)` có thể bảo vệ một ranh giới với raw API: wrapper kiểm tra kiểu khi thêm và có thể ném `ClassCastException` sớm. Wrapper **không tự quét và sửa** các phần tử sai vốn đã có trong danh sách gốc. Generic đúng và validation ở đầu vào vẫn là biện pháp chính.

### 3.4. Sorted collections cũng có hợp đồng kiểu

`TreeSet`/`TreeMap` phải so sánh các phần tử/khóa. Nếu dùng thứ tự tự nhiên, các giá trị cần hỗ trợ so sánh tương thích; nếu dùng `Comparator`, comparator phải nhận đúng kiểu và tạo thứ tự nhất quán. Trộn các kiểu không tương thích qua raw collection có thể gây `ClassCastException` khi thêm, tìm hoặc sắp xếp, tùy dữ liệu và thao tác.

Dùng `TreeSet<String>` hoặc comparator khai báo kiểu rõ ràng. Nếu cần so sánh với null, xác định quy tắc bằng `Comparator.nullsFirst(...)` hoặc `nullsLast(...)` kết hợp comparator thích hợp. Không giả định thứ tự tự nhiên chấp nhận null.

## 4. NullPointerException trong các thao tác collection

### 4.1. Chính sách null thay đổi theo API

- `ArrayList` có thể chứa phần tử null; `HashMap` cho phép một khóa null và các giá trị null.
- `List.of(...)`, `Set.of(...)`, `Map.of(...)`, `List.copyOf(...)` từ chối các null không được phép; lỗi thường xảy ra ngay khi tạo.
- `ConcurrentHashMap` không cho khóa/giá trị null.
- `TreeMap` với thứ tự khóa tự nhiên không cho khóa null; với comparator riêng, khả năng xử lý null phụ thuộc comparator.
- `Collections.unmodifiableList(list)` chỉ tạo view ngăn sửa qua wrapper; nó không tự loại bỏ null đã có trong danh sách nguồn.

Ví dụ:

```java
List<String> names = new ArrayList<>();
names.add(null);                    // Hợp lệ.
System.out.println(names.size()); // 1
System.out.println(names.get(0)); // null
names.get(0).length();            // NullPointerException.
```

`null` có thể được chèn hợp lệ nhưng gây NPE ở lần dùng sau. Tương tự, `Integer boxed = null; int n = boxed;` ném NPE vì Java phải gọi quá trình unboxing.

### 4.2. Map.get trả về null chưa đủ để kết luận thiếu khóa

Với `HashMap`:

```java
import java.util.HashMap;
import java.util.Map;

Map<String, String> labels = new HashMap<>();
labels.put("has-null", null);

System.out.println(labels.containsKey("has-null")); // true
System.out.println(labels.containsKey("missing"));  // false
System.out.println(labels.get("has-null"));         // null
System.out.println(labels.get("missing"));          // null
```

`get(key) == null` không phân biệt được khóa vắng mặt và khóa có mapping đến null. `getOrDefault(key, fallback)` chỉ trả fallback khi không có mapping; mapping hiện có với giá trị null vẫn có thể khiến kết quả là null. Hãy quyết định rõ nghiệp vụ có cần phân biệt hai trạng thái này không, rồi dùng `containsKey` hoặc thay đổi mô hình dữ liệu cho phù hợp.

### 4.3. Thiết lập hợp đồng null ngay tại ranh giới

Nếu toàn bộ tham số không được phép null, dùng `Objects.requireNonNull(input, "input")` để ném lỗi sớm và rõ tên tham số. Nếu **phần tử** có thể null, phải có chính sách: từ chối, lọc có chủ đích, hoặc dùng giá trị thay thế được nghiệp vụ chấp nhận. Không tự chuyển `null` thành `0` hay chuỗi rỗng chỉ để tránh exception nếu chúng có nghĩa khác nhau.

Sau khi validation xác nhận không có null, `List.copyOf` là một lựa chọn để trả về danh sách không sửa đổi được. `Optional` có thể phù hợp với một API trả về *một giá trị tùy chọn*, nhưng bọc tràn lan mọi phần tử của collection vào `Optional` không giải quyết hợp đồng dữ liệu chưa rõ.

## 5. Kiểu dữ liệu không hợp lệ: validate thay vì cast

### 5.1. Phân biệt sai kiểu và sai miền giá trị

Một API nhận điểm số `Integer` trong khoảng 0–100 có thể gặp:

- `"90"`: kiểu Java là `String`, không thể cast trực tiếp thành `Integer`. Nếu cho phép nhập chuỗi, phải parse bằng `Integer.parseInt` và xử lý `NumberFormatException` khi định dạng sai.
- `null`: không có giá trị; cần chính sách null.
- `120`: đúng kiểu `Integer` nhưng sai miền; có thể ném `IllegalArgumentException` với thông tin chỉ số và khoảng hợp lệ.

Đừng trộn validation với cast unchecked. Khi dữ liệu đến từ CSV/JSON/API cũ, có thể nhận `Collection<?>` ở biên ngoài, kiểm tra từng phần tử, sau đó chỉ chuyển collection đã xác thực cho tầng nghiệp vụ.

### 5.2. Ví dụ validation đầy đủ kèm test

Lưu thành `CollectionValidationLab.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CollectionValidationLab {
    static List<Integer> validatedScores(Collection<?> input) {
        Objects.requireNonNull(input, "input");
        List<Integer> result = new ArrayList<>();

        int index = 0;
        for (Object item : input) {
            if (!(item instanceof Integer score)) {
                String actual = item == null
                        ? "null"
                        : item.getClass().getSimpleName();
                throw new IllegalArgumentException(
                        "index " + index + ": expected Integer, got " + actual);
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException(
                        "index " + index + ": outside 0..100");
            }
            result.add(score);
            index++;
        }
        return List.copyOf(result);
    }

    static void expect(Class<? extends RuntimeException> expected,
                       Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            if (expected.isInstance(ex)) {
                return;
            }
            throw new AssertionError(
                    "Expected " + expected.getSimpleName()
                            + " but got " + ex, ex);
        }
        throw new AssertionError(
                "Expected " + expected.getSimpleName() + " but no exception");
    }

    public static void main(String[] args) {
        List<Integer> good = validatedScores(Arrays.asList(80, 90));
        if (!good.equals(List.of(80, 90))) {
            throw new AssertionError("Wrong values");
        }

        expect(NullPointerException.class,
                () -> validatedScores(null));
        expect(IllegalArgumentException.class,
                () -> validatedScores(Arrays.asList(80, null)));
        expect(IllegalArgumentException.class,
                () -> validatedScores(Arrays.asList(80, "90")));
        expect(IllegalArgumentException.class,
                () -> validatedScores(Arrays.asList(80, 101)));
        expect(UnsupportedOperationException.class,
                () -> good.add(95));

        System.out.println("6 checks passed");
    }
}
```

Chạy với JDK 17+:

```bash
javac CollectionValidationLab.java
java CollectionValidationLab
```

Stdout chính xác nếu mọi check đạt:

```text
6 checks passed
```

Một lần kiểm tra danh sách hợp lệ và năm lần `expect` tạo thành sáu check. Phương thức không tin kiểu phần tử từ đầu vào `Collection<?>`; nó xác nhận đúng `Integer`, loại bỏ null và số ngoài 0–100 bằng lỗi rõ ràng, rồi trả về `List<Integer>` bất biến theo giao diện. Null của **toàn collection** được coi là lỗi tham số bắt buộc (NPE); null của **phần tử** được coi là lỗi dữ liệu (IllegalArgumentException). Đó là một hợp đồng đã chọn nhất quán, có thể thay bằng loại exception miền nghiệp vụ khác nếu dự án quy định.

`expect` không chấp nhận exception bất kỳ: sai loại hoặc không có exception đều dẫn đến `AssertionError`. Việc chỉ gọi hàm trong `try/catch` và không assert gì sẽ dễ tạo test “xanh giả”. Không nên bắt `RuntimeException` chung trong logic nghiệp vụ rồi âm thầm tiếp tục.

**Mở rộng quan trọng:** ví dụ từ chối chuỗi `"90"` thay vì tự ý parse. Nếu hệ thống cần hỗ trợ chuỗi, hãy bổ sung yêu cầu vào hợp đồng và test cả `"90"`, `""`, `"90x"`, chuỗi có khoảng trắng, số âm, số vượt miền, và vấn đề định dạng. Đừng biến dữ liệu không hợp lệ thành dữ liệu hợp lệ theo phỏng đoán.

## 6. Những lỗi kiểu liên quan dễ nhầm

**ArrayStoreException với `toArray`:** lời gọi `List.of(1, 2).toArray(new String[0])` có thể biên dịch vì chữ ký generic của `toArray(T[])`, nhưng lỗi runtime khi sao chép `Integer` vào `String[]`. Dùng `toArray(new Integer[0])` hoặc `toArray(Integer[]::new)`.

**ClassCastException trong comparator:** một collection sắp xếp đã trộn kiểu qua raw reference có thể ném lỗi ở chỗ so sánh, thay vì chỗ thêm phần tử ban đầu. Dùng comparator cùng kiểu với element/key, kiểm tra null và quy tắc thứ tự.

**UnsupportedOperationException với factory bất biến:** `List.of("A").add("B")` ném lỗi vì không cho sửa. Đừng diễn giải thành kiểu phần tử không hợp lệ. `List.copyOf` từ chối null và không cho sửa; đó là hai hợp đồng khác nhau.

**ClassCastException không phải NumberFormatException:** `(Integer) (Object) "123"` là ép kiểu sai; `Integer.parseInt("123x")` là parse thất bại. Hướng sửa hoàn toàn khác nhau.

## 7. Ma trận kiểm thử nên có

| Kịch bản | Kỳ vọng |
| --- | --- |
| `[80, 90]` | Trả đúng phần tử, kiểu và thứ tự. |
| `[]` | Theo hợp đồng: ví dụ `validatedScores` cho phép và trả danh sách rỗng. |
| `null` là collection đầu vào | Lỗi tham số rõ ràng; ví dụ dùng NPE. |
| `[80, null]` | Từ chối null ở index 1. |
| `[80, "90"]` | Từ chối kiểu `String` dù nội dung trông như số. |
| `[80, 101]` | Từ chối số ngoài miền. |
| `good.add(95)` | `UnsupportedOperationException` nếu kết quả không cho sửa. |
| Raw `List<String>` chứa `Integer` | Tái hiện CCE tại điểm đọc và xác định điểm thêm sai. |
| `Map` có khóa trỏ tới null / khóa không tồn tại | Dùng `containsKey` khi cần phân biệt. |
| `toArray(new String[0])` từ danh sách `Integer` | `ArrayStoreException` với dữ liệu không tương thích. |

Trong dự án cấu hình JUnit 5/JUnit Jupiter, test exception và thông báo lỗi được viết chẳng hạn như sau (đây là file test riêng, không tự chạy chỉ với `javac` nếu chưa có JUnit trong classpath):

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CollectionValidationLabTest {
    @Test
    void rejectsNullElementAtCorrectIndex() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CollectionValidationLab.validatedScores(
                        Arrays.asList(80, null)));
        assertEquals("index 1: expected Integer, got null", ex.getMessage());
    }
}
```

Đặt test cùng package với `CollectionValidationLab`, hoặc điều chỉnh visibility theo cấu trúc dự án. Với thông báo NPE do JVM tự tạo, không nên phụ thuộc nguyên văn vào một biến thể thông báo có thể đổi theo phiên bản/runtime; hãy assert loại lỗi và hợp đồng do API của mình định nghĩa.

## 8. Quy trình debug và phòng ngừa

1. **Đọc exception và stack trace đầu tiên.** Ghi nhận thao tác: `get`, cast, sort, lookup, dereference, unboxing, factory hay `toArray`.
2. **Kiểm tra dữ liệu ở điểm lỗi.** Với giá trị khác null, `value.getClass().getName()` giúp biết runtime type; luôn kiểm tra null trước khi gọi `getClass()`. Trong log chỉ ghi thông tin được phép, tránh lộ dữ liệu nhạy cảm.
3. **Lần ngược đến điểm dữ liệu đi vào.** CCE ở `get` thường bắt nguồn từ raw `add`, adapter hoặc cast unchecked. NPE ở lần gọi phương thức thường do null được trả về ở thao tác trước.
4. **Đối chiếu hợp đồng.** Null có được phép? Collection nào cho null? Kiểu và miền giá trị nào hợp lệ? `Map.get` thiếu khóa có bình thường không? Có được sửa collection kết quả không?
5. **Sửa ở ranh giới sớm.** Khai báo generic đúng; validate/parsing tường minh; trả lỗi có vị trí và nguyên nhân; tạo collection đã kiểm tra để các tầng sau không phải cast lại.
6. **Thêm regression test.** Kiểm tra cả đầu vào hợp lệ, null, sai kiểu, sai miền, rỗng, mảng đích sai kiểu, và hành vi khi kết quả không cho sửa.

Không coi việc bỏ qua phần tử sai, thay `null` bằng `0` hoặc bắt mọi exception rồi trả danh sách rỗng là giải pháp mặc định. Chúng có thể che giấu việc mất dữ liệu. Nếu chuyển ngoại lệ sang một lỗi ở tầng cao, nên giữ cause (ví dụ `new IllegalArgumentException("Invalid scores", ex)`).

## 9. Checklist trước khi giao code

- Có generic rõ ràng trên collection và tránh raw type/unchecked cast không kiểm soát.
- Hợp đồng quy định null cho collection, từng phần tử và khóa/giá trị map.
- Dữ liệu ngoài được validate về kiểu **và** miền giá trị trước khi dùng.
- Không dùng cast để parse chuỗi, không nhầm CCE với `NumberFormatException` hay `IllegalArgumentException`.
- Collection sắp xếp có comparator đúng kiểu, chính sách null và thứ tự nhất quán.
- Test khẳng định đúng exception, dữ liệu, vị trí lỗi và trạng thái sửa đổi; có regression test cho lỗi từng gặp.
- Nếu exception xuất hiện ở **điểm đọc**, vẫn tìm nguyên nhân tại **điểm ghi**.

**Kết luận:** Generic loại bỏ nhiều lỗi ở thời điểm biên dịch; collection nhận dữ liệu không đáng tin vẫn cần validation runtime. Hợp đồng null/kiểu rõ ràng, kiểm tra sớm và test có chủ đích là cách ngăn `ClassCastException` và `NullPointerException` tái xuất hiện trong những luồng nghiệp vụ phức tạp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-008">Lỗi khóa khả biến, comparator không nhất quán và phần tử mất dấu</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi khóa khả biến, comparator không nhất quán và phần tử “mất dấu”

Một collection có thể vẫn chứa đối tượng nhưng chương trình không tìm được nó bằng cách tra cứu thông thường. Hiện tượng này thường đến từ **quy tắc định danh đã thay đổi** hoặc **quy tắc so sánh không phù hợp**, chứ không nhất thiết do phần tử bị xóa khỏi bộ nhớ.

Bài học phân biệt ba cơ chế: (1) cấu trúc băm dùng `hashCode` và `equals`, (2) cấu trúc sắp xếp dùng `Comparator`/`Comparable`, và (3) đối tượng hoặc khóa thay đổi sau khi được đưa vào cấu trúc. Đây là các lỗi về *hợp đồng dữ liệu*; thay đổi thao tác tìm kiếm đơn lẻ không sửa được gốc rễ.

## 1. “Có trong cấu trúc” khác với “tìm được bằng quy tắc hiện tại”

Giả sử `Map` lưu một hồ sơ theo khóa đối tượng. Sau khi chèn, chương trình có thể đổi một trường của khóa. Mã tra cứu về sau tính toán dựa trên trạng thái mới, trong khi cấu trúc đã sắp xếp/lưu khóa theo trạng thái cũ. Kết quả có thể là `get` trả `null`, `containsKey` trả `false` hoặc `remove` không tìm được phần tử dù khi duyệt ta vẫn quan sát thấy tham chiếu đó.

Cần hỏi ba câu trước khi kết luận dữ liệu “biến mất”:

1. Đối tượng có thật sự bị loại khỏi collection hay chỉ không được tìm thấy?
2. Quy tắc `equals`, `hashCode`, hoặc `compare` có thay đổi từ lúc thêm tới lúc tìm không?
3. Cấu trúc dựa vào **băm**, **thứ tự so sánh**, hay chỉ **vị trí/chỉ số** để tra cứu?

`HashMap` và `HashSet` sử dụng băm để tổ chức và tìm phần tử. `TreeMap` và `TreeSet` dựa vào quan hệ thứ tự. `ArrayList` lại có cơ chế tìm theo `equals` qua việc duyệt các phần tử. Vì vậy cùng một sai sót có thể xuất hiện khác nhau tùy cấu trúc.

## 2. Khóa khả biến trong HashMap/HashSet

Điều kiện thiết yếu cho khóa băm là: **trong toàn bộ thời gian khóa nằm trong cấu trúc, các dữ liệu ảnh hưởng đến `equals` và `hashCode` phải ổn định**. Hai đối tượng bằng nhau theo `equals` phải có cùng `hashCode`; hai đối tượng có cùng mã băm thì *chưa chắc* bằng nhau.

Ví dụ kiểu khóa nguy hiểm:

```java
import java.util.Objects;

final class MutableKey {
    String code;

    MutableKey(String code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MutableKey key
                && Objects.equals(code, key.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
```

Một tình huống lỗi điển hình là:

```java
MutableKey key = new MutableKey("alpha");
Map<MutableKey, String> map = new HashMap<>();
map.put(key, "Đã lưu");

key.code = "beta"; // thay đổi đầu vào của equals/hashCode
String result = map.get(key); // không còn có bảo đảm tra cứu như trước
```

Khi `put`, `HashMap` chọn vị trí lưu bằng giá trị băm thời điểm đó. Sau khi `code` đổi, mã băm có thể khác; yêu cầu tìm kiếm có thể đi tới vùng khác. **Không khẳng định mọi thay đổi đều gây `null`:** có thể tình cờ trùng vùng băm hoặc một trường không ảnh hưởng mã băm. Vấn đề là hợp đồng sử dụng đã không còn đáng tin và theo Javadoc `Map`, hành vi khi khóa thay đổi theo cách ảnh hưởng so sánh `equals` là không được xác định.

Với `HashSet`, phần tử đóng vai trò khóa của cấu trúc băm. Một phần tử có trường định danh bị sửa sau `add` cũng có thể không tìm/xóa được bằng quy tắc hiện tại. Gọi `remove` trước khi sửa, rồi `add` lại sau khi sửa là một cách hợp lệ **nếu** bạn bảo đảm thao tác đơn luồng hoặc đồng bộ đúng và không làm thay đổi định danh trong lúc nó đang nằm trong Set.

### Chọn khóa ổn định

Ưu tiên giá trị định danh bất biến, chẳng hạn một mã đã chuẩn hóa, `String`, `UUID` hoặc một `record` không giữ thành phần khả biến tham gia định danh:

```java
record CustomerKey(String tenant, String customerId) {}

Map<CustomerKey, String> customers = new HashMap<>();
customers.put(new CustomerKey("vn", "C-42"), "Lan");
System.out.println(customers.get(new CustomerKey("vn", "C-42")));
```

Đầu ra:

```text
Lan
```

Hai record khác nhau có cùng thành phần mang giá trị bằng nhau được so khớp đúng. Tuy nhiên, **`record` không tự làm bất biến sâu**: nếu một thành phần là `List` khả biến và nội dung list tham gia `equals`/`hashCode`, việc sửa list vẫn có thể phá quy tắc khóa. Hãy dùng thành phần bất biến hoặc tạo bản sao bất biến đúng chỗ.

## 3. Comparator “đánh đồng” hai giá trị khác nhau

Một `TreeSet` quyết định một phần tử mới có trùng với phần tử đã có hay không bằng kết quả `compare(a, b) == 0`. Nó không gọi `equals` như một bước kiểm tra trùng bổ sung. Nếu comparator chỉ xét một phần dữ liệu, hai phần tử thực sự khác nhau có thể bị xem là một.

Chương trình `ComparatorPitfallDemo.java`:

```java
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class ComparatorPitfallDemo {
    public static void main(String[] args) {
        Set<String> names = new TreeSet<>(
                Comparator.comparingInt(String::length)
        );
        System.out.println(names.add("Lan"));
        System.out.println(names.add("Min"));
        System.out.println(names.contains("Min"));
        System.out.println(names);
    }
}
```

Chạy:

```text
javac -encoding UTF-8 --release 17 ComparatorPitfallDemo.java
java -Dfile.encoding=UTF-8 ComparatorPitfallDemo
```

Đầu ra:

```text
true
false
true
[Lan]
```

`"Lan"` và `"Min"` dài bằng nhau, nên comparator trả `0`. `add("Min")` không thêm giá trị mới; `contains("Min")` lại là `true` vì tìm thấy một phần tử tương đương **theo comparator**. Như vậy `contains` không có nghĩa rằng chuỗi `"Min"` từng được lưu. Đây không phải lỗi ngẫu nhiên của JVM.

Nếu muốn sắp xếp tên theo độ dài nhưng vẫn phân biệt chuỗi khác nhau, thêm tiêu chí phụ:

```java
Comparator<String> byLengthThenText =
        Comparator.comparingInt(String::length)
                  .thenComparing(Comparator.naturalOrder());
Set<String> names = new TreeSet<>(byLengthThenText);
names.add("Lan");
names.add("Min");
System.out.println(names);
```

Đầu ra:

```text
[Lan, Min]
```

Tiêu chí phụ giúp tránh việc hai tên *khác nội dung* cùng độ dài bị gộp. Nếu mô hình nghiệp vụ coi hai chuỗi khác nhau theo chữ hoa/thường là cùng người, hãy định nghĩa **định danh nghiệp vụ** riêng; đừng ngầm suy rằng `Comparator.naturalOrder()` có cùng ý nghĩa với định danh người.

Với `TreeMap`, comparator cũng quyết định khóa nào được xem là tương đương. Nếu hai khóa so sánh bằng `0`, `put` khóa thứ hai có thể **cập nhật giá trị gắn với khóa hiện có**, thay vì tạo thêm entry mới. Điều này rất nguy hiểm nếu tác giả chỉ mong comparator chi phối cách hiển thị.

## 4. Comparator phải thỏa hợp đồng thứ tự

Một comparator hợp lệ không cần trả chính xác `-1` hoặc `1`; chỉ dấu âm, không hoặc dương có ý nghĩa. Nó phải bảo đảm:

- **Đối xứng về dấu:** dấu của `compare(a,b)` phải ngược dấu `compare(b,a)`.
- **Bắc cầu:** nếu `a > b` và `b > c` theo comparator thì `a > c`.
- **Nhất quán của nhóm bằng nhau:** nếu `compare(a,b)==0` thì với mọi `z`, dấu so sánh `a` với `z` và `b` với `z` phải giống nhau.
- Với `SortedSet`/`SortedMap`, nên **nhất quán với `equals`** nếu cần tuân thủ đầy đủ hợp đồng `Set`/`Map`.

Ví dụ sai dễ gặp:

```java
Comparator<Integer> wrong = (a, b) -> a - b;
```

Phép trừ có thể **tràn số**, dẫn tới dấu sai. Dùng `Integer.compare(a, b)`. Comparator viết kiểu “nếu một người ưu tiên thì đứng trước; nếu không thì đứng sau” mà không xử lý trường hợp ngang nhau thường phá đối xứng. Comparator phụ thuộc vào thời gian hiện tại, cấu hình thay đổi, hoặc trạng thái khả biến bên ngoài cũng không phù hợp để làm quy tắc ổn định cho `TreeSet`/`TreeMap`.

Một bộ sắp xếp không hợp đồng có thể cho kết quả sai và trong một số thuật toán sắp xếp có thể phát sinh `IllegalArgumentException` về vi phạm contract. **Không dựa vào việc có/không có ngoại lệ để chứng minh comparator đúng.**

## 5. Trường tham gia thứ tự cũng không nên thay đổi khi đang nằm trong TreeSet/TreeMap

`TreeSet` đặt phần tử vào cấu trúc cây dựa trên thứ tự tại thời điểm chèn. Nếu đối tượng đổi một trường mà comparator đang đọc, cấu trúc không tự dựng lại cây.

Ví dụ một `Task` có `priority` được chỉnh sau khi thêm vào `TreeSet<Task>`. Duyệt cây có thể cho thứ tự không còn đúng; `contains`/`remove` có thể không đi theo nhánh chứa đối tượng. Cũng như với khóa băm, không phải mọi lần sửa đều thể hiện lỗi ngay, nhưng việc sửa làm hỏng bất biến của cấu trúc.

Cách sửa theo từng nhu cầu:

| Nhu cầu | Mô hình phù hợp |
| --- | --- |
| Khóa nghiệp vụ ổn định, nội dung bản ghi thay đổi | Khóa `record`/ID bất biến → value khả biến trong `Map` |
| Cần cập nhật một phần tử trong tập có thứ tự | `remove` khi còn trạng thái cũ → tạo phần tử/khóa mới → `add` |
| Ưu tiên công việc liên tục thay đổi | Quản lý lại hàng đợi/cấu trúc theo chính sách cập nhật; không nghĩ `PriorityQueue` tự sắp lại khi sửa đối tượng |
| Chỉ cần danh sách hiển thị mới nhất | Giữ dữ liệu nguồn ổn định và tạo danh sách mới để `sort` khi cần |
| Muốn so sánh nhiều tiêu chí | Comparator đủ tiêu chí phân biệt theo đúng mô hình định danh |

**Chú ý:** `PriorityQueue` không đảm bảo tự điều chỉnh vị trí khi sửa độ ưu tiên của phần tử đã ở trong heap. Một bản ghi “thay đổi priority tại chỗ” có thể khiến `peek`/`poll` không còn phản ánh ưu tiên như mong đợi.

## 6. Cách điều tra khi phần tử “mất dấu”

1. **Chụp dữ liệu trước khi chèn:** trường tham gia `equals`/`hashCode`/`compare`, định danh và kết quả so sánh với khóa khác.
2. **Tách tra cứu khỏi duyệt:** với Map/Set, kiểm tra `get`/`contains` và đồng thời quan sát các entry qua phép duyệt (trong môi trường kiểm thử đơn luồng). Đừng sửa dữ liệu trong lúc kiểm tra.
3. **So sánh trạng thái hiện tại với snapshot:** một trường định danh bị cập nhật có thể giải thích việc băm/thứ tự thay đổi.
4. **Thử hai đối tượng khác tham chiếu nhưng cùng định danh:** chúng phải có kết quả `equals` và `hashCode` tương thích; trong cấu trúc sắp xếp, thử `compare` tương ứng.
5. **Tạo phép thử nhỏ tái hiện:** thêm hai chuỗi cùng độ dài vào TreeSet để xác minh điều kiện comparator; dùng lớp khóa khả biến trong test để chứng minh rủi ro.
6. **Đổi mô hình dữ liệu:** ưu tiên khóa bất biến, comparator ổn định và tách trường dùng sắp xếp khỏi trường chỉnh sửa thường xuyên.

Không dựa vào thứ tự in từ `HashMap` hoặc `HashSet` để xác định trạng thái đúng; các cấu trúc này không hứa giữ thứ tự chèn. Kiểm tra điều kiện dữ liệu bằng các phép khẳng định như `containsKey`, giá trị nhận được, và số lượng phần tử.

## 7. Bài tự kiểm tra

- Hai đối tượng có cùng `hashCode` nhưng `equals` trả `false` có là cùng khóa trong `HashMap` không? **Không.** Va chạm băm được xử lý bằng so khớp tương ứng.
- `TreeSet<String>` so theo độ dài và đã chứa `"Lan"`. Khi thêm `"Min"`, chuyện gì xảy ra? **Không thêm** vì comparator trả `0`; `contains("Min")` vẫn có thể trả `true`.
- Khi đổi giá trị `phone` của `Customer`, có luôn làm hỏng khóa trong `HashMap` không? **Không**, nếu `phone` không tham gia định nghĩa `equals`/`hashCode`; phải xem hợp đồng kiểu khóa.
- Có thể sửa mọi vấn đề bằng cách gọi `rehash` trên `HashMap` không? **Không** có API `rehash` công khai để người dùng tùy tiện “chữa” hợp đồng sai. Sửa thiết kế khóa và vòng đời cập nhật.
- Tại sao thêm `thenComparing` hữu ích? Vì tiêu chí bổ sung giúp phân biệt các trường hợp tiêu chí đầu cho kết quả `0`, miễn nó phù hợp định danh và thứ tự nghiệp vụ.

## Tài liệu tham khảo

- Javadoc `Map` (Java SE 21), mục cảnh báo khóa khả biến: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html
- Javadoc `SortedSet` (Java SE 21), điều kiện thứ tự nhất quán với equals: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SortedSet.html
- Javadoc `Comparator` (Java SE 21), quy tắc dấu và tính bắc cầu: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-pitfalls-009">Kiểm thử tính nguyên tử, ảnh chụp và nhất quán yếu khi chạy đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử tính nguyên tử, ảnh chụp và nhất quán yếu khi chạy đồng thời

> **Phạm vi:** Java Core Collections — Kiểm thử và lỗi. Bài này tập trung vào cách thiết kế phép thử và chọn tiêu chí đúng/sai cho collection bị đọc, ghi đồng thời. Ví dụ dùng Java 17 trở lên.

## 1. Ba hợp đồng khác nhau cần kiểm thử

Một collection hỗ trợ truy cập đồng thời không có nghĩa là mọi chuỗi thao tác của chương trình đều nguyên tử, cũng không có nghĩa việc duyệt nó sẽ thấy đúng một trạng thái cố định. Trước khi viết test, hãy xác định chính xác hợp đồng mà API hứa hẹn.

| Thuộc tính | Câu hỏi cần trả lời | Ví dụ | Tiêu chí kiểm thử |
| --- | --- | --- | --- |
| **Tính nguyên tử (atomicity)** | Một thao tác có diễn ra như một đơn vị không thể bị xen vào hay không? | `ConcurrentHashMap.merge` cập nhật một key | Tổng số lần cập nhật không bị mất khi mọi tác vụ hoàn tất |
| **Ảnh chụp (snapshot)** | Người đọc có nhìn thấy một phiên bản cố định trong suốt lần duyệt không? | Iterator của `CopyOnWriteArrayList` | Iterator cũ không thấy phần tử thêm sau khi tạo iterator |
| **Nhất quán yếu (weak consistency)** | Khi có ghi đồng thời, iterator được phép phản ánh những thay đổi nào? | Iterator của `ConcurrentHashMap` | Không lỗi do sửa đổi đồng thời; mỗi mục quan sát được thỏa bất biến, nhưng không ép danh sách chính xác |

**Điểm mấu chốt:** một thao tác nguyên tử trên từng key không tạo ra snapshot nguyên tử của toàn map. Một iterator duyệt an toàn cũng không đồng nghĩa iterator trả về trạng thái của map tại một thời điểm duy nhất.

## 2. Nền tảng: data race, happens-before và linearizability

- **Data race:** nhiều luồng truy cập cùng dữ liệu có ít nhất một thao tác ghi mà thiếu cơ chế đồng bộ phù hợp; kết quả có thể phụ thuộc lịch chạy và tính hiển thị bộ nhớ. `HashMap`, `ArrayList` thông thường không cung cấp hợp đồng an toàn cho việc sửa đồng thời không đồng bộ.
- **Happens-before:** quan hệ đảm bảo thứ tự và hiển thị giữa các hành động. Chẳng hạn, các thao tác trước `CountDownLatch.countDown()` có quan hệ happens-before với tác vụ vượt qua `await()` tương ứng; tác vụ kết thúc có quan hệ happens-before với hành động sau `Future.get()` thành công trong luồng chờ. Dùng các cơ chế này để test đọc kết quả cuối một cách hợp lệ.
- **Atomicity / linearizability:** một phép thao tác nguyên tử có thể được lý giải như xảy ra tại một điểm giữa lúc gọi và lúc trả về. Khi nhiều lời gọi chồng lấn, thứ tự quan sát được phải phù hợp với một trình tự hợp lệ theo hợp đồng của chính phép thao tác. Không suy rộng điều này cho nhiều lệnh riêng biệt.
- **Visibility khác atomicity:** `volatile` có thể giúp thấy giá trị cập nhật nhưng không biến `x++` thành phép cộng nguyên tử. `synchronized` chỉ bảo vệ toàn bộ giao dịch nếu mọi đường đọc/ghi liên quan dùng cùng một khóa.

Ví dụ không nguyên tử:

```java
Integer old = counts.get("java");
counts.put("java", old + 1);
```

`ConcurrentHashMap.get` và `put` đều an toàn khi gọi riêng, nhưng hai luồng vẫn có thể cùng đọc `0` rồi cùng ghi `1`. Muốn tăng bộ đếm cùng một key, dùng `merge` hoặc `compute` trên `ConcurrentHashMap`, hoặc dùng biến đếm riêng như `AtomicInteger`/`LongAdder` khi mô hình dữ liệu thích hợp.

## 3. Kiểm thử lỗi mất cập nhật bằng lịch chạy có kiểm soát

Chỉ khởi chạy hai luồng hàng nghìn lần rồi hy vọng lỗi xuất hiện là phép thử yếu: race có thể không xảy ra trong máy thử. Ta tạo lịch chạy để **cả hai luồng bắt buộc đọc cùng giá trị cũ** trước khi cho phép ghi. Sau đó so sánh với phép `merge` đúng.

Lưu file `LostUpdateDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LostUpdateDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        counts.put("java", 0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                jobs.add(pool.submit(() -> {
                    int old = counts.get("java");
                    bothRead.countDown();
                    allowWrite.await();
                    counts.put("java", old + 1);
                    return null;
                }));
            }

            if (!bothRead.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Không đủ hai lượt đọc");
            }
            allowWrite.countDown();
            for (Future<?> job : jobs) {
                job.get(5, TimeUnit.SECONDS);
            }
            System.out.println("get + put = " + counts.get("java"));

            counts.put("java", 0);
            jobs.clear();
            for (int i = 0; i < 2; i++) {
                jobs.add(pool.submit(() -> {
                    counts.merge("java", 1, Integer::sum);
                    return null;
                }));
            }
            for (Future<?> job : jobs) {
                job.get(5, TimeUnit.SECONDS);
            }
            System.out.println("merge = " + counts.get("java"));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac LostUpdateDemo.java
java LostUpdateDemo
```

Kết quả khi tác vụ hoàn tất bình thường:

```text
get + put = 1
merge = 2
```

Giải thích:

1. `bothRead` bảo đảm hai tác vụ đã đọc giá trị `0`. `allowWrite` chỉ mở sau cả hai lần đọc, nên hai `put` ghi cùng `1`: mất một lượt cập nhật **một cách tái lập**.
2. `merge("java", 1, Integer::sum)` trên `ConcurrentHashMap` cập nhật nguyên tử cho key `java`; hai phép tăng hoàn tất thì giá trị phải bằng `2`.
3. `Future.get` làm lỗi trong tác vụ nổi lên tại luồng kiểm thử và thiết lập thứ tự cần thiết trước khi kiểm tra giá trị cuối. Dùng giới hạn thời gian để test không treo vô hạn.
4. `shutdownNow` nằm trong `finally` nhằm giải phóng executor kể cả khi test thất bại. Khi ứng dụng thực cần chờ dừng êm, ưu tiên `shutdown` rồi `awaitTermination` phù hợp.

**Chú ý về hợp đồng:** phép `merge` ở trên bảo đảm cập nhật *một key*. Nó không biến việc đọc hai key rồi chuyển số dư thành một giao dịch nguyên tử. Nếu phải kiểm tra bất biến trải trên nhiều key, cần thiết kế khóa/giao dịch/trạng thái bất biến thống nhất cho cả thao tác.

### Khi kiểm thử một bộ đếm lớn

Nếu có `T` luồng, mỗi luồng gọi `merge` `N` lần cho cùng key, sau khi **mọi `Future` hoàn thành thành công**, bất biến cuối là `count == T * N`. Cần kiểm tra tràn số với kiểu `int`; dùng `long` hoặc `LongAdder` tùy bài toán. `LongAdder.sum()` không phải snapshot nguyên tử trong lúc vẫn có cập nhật; chỉ so chính xác sau khi các luồng ghi đã dừng. Không dùng `ConcurrentHashMap.size()` hay `isEmpty()` làm chứng cứ trạng thái tức thời chính xác khi map đang biến đổi đồng thời.

## 4. Kiểm thử snapshot: đối tượng được duyệt là phiên bản nào?

Snapshot là một bản nhìn ổn định trong giới hạn hợp đồng. Iterator của `CopyOnWriteArrayList` giữ tham chiếu tới mảng tại **thời điểm iterator được tạo**, nên thêm/xóa phần tử sau đó không đổi nội dung lần duyệt ấy. Iterator này không hỗ trợ `remove`, `add` hay `set`.

Lưu file `SnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class SnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> shared =
                new CopyOnWriteArrayList<>(List.of("A", "B"));

        Iterator<String> oldIterator = shared.iterator();
        shared.add("C");

        List<String> observed = new ArrayList<>();
        oldIterator.forEachRemaining(observed::add);

        System.out.println("Iterator cũ: " + observed);
        System.out.println("Danh sách mới: " + shared);

        AtomicReference<List<String>> published =
                new AtomicReference<>(List.of("A", "B"));
        List<String> before = published.get();

        published.updateAndGet(current -> {
            List<String> next = new ArrayList<>(current);
            next.add("C");
            return List.copyOf(next);
        });

        System.out.println("Snapshot cũ: " + before);
        System.out.println("Snapshot mới: " + published.get());
    }
}
```

```bash
javac SnapshotDemo.java
java SnapshotDemo
```

```text
Iterator cũ: [A, B]
Danh sách mới: [A, B, C]
Snapshot cũ: [A, B]
Snapshot mới: [A, B, C]
```

Hai cách giải quyết nhu cầu khác nhau:

- **`CopyOnWriteArrayList`:** mỗi lần sửa tạo phiên bản mảng mới. Iterator đang dùng thấy mảng cũ, thích hợp với tình huống đọc/duyệt nhiều, sửa ít. Chi phí ghi và cấp phát bộ nhớ tăng theo kích thước list.
- **`AtomicReference<List<T>>` với danh sách bất biến:** người đọc lấy một reference duy nhất và giữ nó cho cả tác vụ. Writer tạo danh sách mới rồi thay reference nguyên tử; `updateAndGet` có thể thử lại lambda khi tranh chấp, nên lambda phải không có tác dụng phụ. `List.copyOf` chỉ bất biến về cấu trúc danh sách, không tự biến từng phần tử mutable bên trong thành bất biến.

**Đừng đánh đồng snapshot với “copy bất kỳ”.** `new ArrayList<>(sharedArrayList)` hoặc `List.copyOf(sharedArrayList)` không đảm bảo ảnh chụp nhất quán nếu một `ArrayList` thông thường vẫn bị sửa đồng thời không đồng bộ. Nếu dùng `Collections.synchronizedList`, hãy sao chép **trong cùng khóa** mà mọi writer dùng:

```java
// Mảnh mã minh họa: shared được tạo và dùng nhất quán qua wrapper.
List<String> shared = java.util.Collections.synchronizedList(
        new java.util.ArrayList<>());

// Các luồng ghi dùng shared.add/remove, không đụng backing list riêng.
List<String> stable;
synchronized (shared) {
    stable = List.copyOf(shared);
}
// Duyệt stable ngoài khóa: cấu trúc danh sách không đổi.
```

Việc giữ khóa khi tạo snapshot có thể tạm chặn writer. Nếu duyệt trực tiếp `shared.iterator()`, phải giữ khóa trên `shared` **trong toàn bộ lần duyệt** theo hợp đồng của wrapper. Iterator trả về từ `synchronizedList` tự nó không được biến thành snapshot.

### Bộ test snapshot nên chứng minh

1. Tạo iterator/snapshot tại thời điểm đã định.
2. Thêm, xóa hoặc thay đổi cấu trúc bộ sưu tập **sau** thời điểm đó.
3. Duyệt iterator/snapshot cũ và so với phiên bản ban đầu.
4. Lấy iterator/snapshot mới và kiểm tra nó thấy trạng thái sau cập nhật.
5. Tách rõ “ổn định về danh sách phần tử” và “bất biến sâu”: nếu chính đối tượng phần tử có state mutable bị sửa, snapshot cấu trúc không tự bảo vệ state của đối tượng ấy.

Không nên chờ thread khác “chạy đủ lâu” để tạo thời điểm chụp; hãy dùng `CountDownLatch`, `Phaser` hoặc barrier để điều khiển chính xác thứ tự tạo iterator → ghi → duyệt khi kịch bản đòi hỏi tính tất định.

## 5. Kiểm thử iterator nhất quán yếu

`ConcurrentHashMap` có iterator **weakly consistent**: có thể quan sát vài cập nhật xảy ra trong khi duyệt, không buộc thấy tất cả, và không ném `ConcurrentModificationException` chỉ vì map được sửa đồng thời. Những entry nhìn thấy phản ánh trạng thái ở những thời điểm khác nhau. Vì vậy, test kiểu “iterator phải trả về đúng [1, 2, 3, 4] khi writer chạy” là sai hợp đồng.

Lưu file `WeakIteratorDemo.java`:

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WeakIteratorDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<Integer, String> data = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) {
            data.put(i, "v" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> writer = pool.submit(() -> {
                start.await();
                for (int i = 1000; i < 2000; i++) {
                    data.put(i, "v" + i);
                }
                return null;
            });

            Future<Integer> reader = pool.submit(() -> {
                start.await();
                int observed = 0;
                for (Map.Entry<Integer, String> entry : data.entrySet()) {
                    String expected = "v" + entry.getKey();
                    if (!expected.equals(entry.getValue())) {
                        throw new AssertionError("Sai bất biến entry");
                    }
                    observed++;
                }
                return observed;
            });

            start.countDown();
            writer.get(5, TimeUnit.SECONDS);
            int observed = reader.get(5, TimeUnit.SECONDS);
            if (data.size() != 2000) {
                throw new AssertionError("Writer chưa ghi đủ");
            }

            System.out.println("Đã duyệt hợp lệ: " + observed);
            System.out.println("Số phần tử cuối: " + data.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac WeakIteratorDemo.java
java WeakIteratorDemo
```

Dòng thứ nhất **không có số lượng cố định**; tùy lịch chạy, reader có thể thấy số entry khác nhau. Dòng thứ hai, sau khi `writer.get` trả về thành công và vì không có writer khác, luôn là:

```text
Số phần tử cuối: 2000
```

Điều được kiểm tra là: mỗi entry **thực sự đã quan sát** phải có cặp key/value tương ứng; iterator không thất bại vì việc thêm phần tử; khi các writer đã kết thúc, kiểm tra được trạng thái cuối cùng. Không được lấy số lượng entry đọc trong lúc update làm “kích thước snapshot” hay bắt nó bằng `map.size()` đo cùng thời gian.

Những lưu ý quan trọng:

- Không suy ra rằng mọi thay đổi mới **đều** phải xuất hiện, hoặc tất cả thay đổi **đều** bị ẩn.
- Không dùng `ConcurrentModificationException` làm “đồng hồ phát hiện race”: iterator fail-fast của `ArrayList`/`HashMap` chỉ cố gắng phát hiện sửa đổi, không đảm bảo ném lỗi trong mọi trường hợp; dùng nó dưới data race có thể sai từ bản chất.
- “Không ném exception” là tiêu chí cần nhưng chưa đủ: vẫn phải kiểm tra bất biến dữ liệu, kết quả nghiệp vụ, tính kết thúc và việc không mất cập nhật.
- `ConcurrentHashMap` không cho key/value `null`. Không đưa yêu cầu cho phép null từ `HashMap` sang test của nó.
- Iterator của `ConcurrentHashMap` được thiết kế cho **một luồng sử dụng trên mỗi iterator**; các luồng riêng có thể tạo iterator riêng trên cùng map.

## 6. Thiết kế oracle: khẳng định điều được bảo đảm, không đoán lịch chạy

Một *test oracle* là quy tắc xác định kết quả đúng. Với chương trình đồng thời, nhiều lịch chạy đều hợp lệ; oracle phải mô tả **bất biến** hoặc **tập kết quả hợp lệ** chứ không gắn cứng một lịch tùy ý.

| Bối cảnh | Khẳng định hợp lệ | Khẳng định sai dễ gặp |
| --- | --- | --- |
| Hai luồng `merge` cùng key, đã `get()` mọi `Future` | Giá trị cuối tăng đúng hai | Hai lời gọi nhất thiết chạy theo thứ tự thread 1 rồi thread 2 |
| Iterator `CopyOnWriteArrayList` tạo trước khi thêm `C` | Iterator cũ giữ [A, B] | Iterator cũ thấy C vì list hiện tại có C |
| Iterator `ConcurrentHashMap` và writer thêm key | Mỗi entry đọc được thỏa bất biến; duyệt không lỗi do sửa cấu trúc | Số entry nhìn thấy phải bằng kích thước cuối hoặc một số chính xác bất kỳ |
| Snapshot cấu trúc chứa đối tượng mutable | Danh sách tham chiếu ổn định | Mọi trường bên trong từng object tự động đóng băng |
| `Collections.synchronizedList` | Mọi truy cập qua wrapper, duyệt/copy dưới cùng khóa | Chỉ gọi `iterator()` qua wrapper là đủ |
| `ConcurrentHashMap.size()` khi đang ghi | Chỉ số có thể phù hợp quan sát tạm thời; kiểm tra kết quả chính xác khi đã dừng writer | `size()` là ảnh chụp tuyến tính hóa của toàn bộ map trong lúc ghi |

Nếu yêu cầu nghiệp vụ cần phép đọc nhất quán cả map — ví dụ kiểm tra “tổng tiền của mọi tài khoản luôn bằng hằng số tại đúng một thời điểm” — iterator weakly consistent không cung cấp snapshot đó. Cần thiết kế giao thức đồng bộ hay phiên bản bất biến được publish nguyên tử, hoặc giao dịch ở tầng lưu trữ. Không “sửa” test bằng cách ép kết quả vượt quá hợp đồng API.

## 7. Quy trình lập test đáng tin cậy

1. **Viết rõ invariant và ranh giới nguyên tử** trước khi chạy. Ví dụ “sau khi tất cả 8 writer xong, tổng một key bằng 80.000”, hoặc “iterator snapshot tạo trước append không thấy append”.
2. **Chọn collection đúng hợp đồng.** `ArrayList`/`HashMap` cần đồng bộ ngoài khi đọc-ghi đồng thời; `ConcurrentHashMap` cho truy cập concurrent và iterator weakly consistent; `CopyOnWriteArrayList` cho snapshot iterator; wrapper `synchronizedList` cần khóa khi duyệt.
3. **Điều khiển đúng điểm cạnh tranh.** Dùng latch/barrier để đồng bộ khởi động hoặc tạo lịch interleaving xác định. `Thread.sleep` không là chứng cứ rằng luồng kia đã đến vị trí mong muốn.
4. **Thu tất cả kết quả và lỗi.** Dùng `Future.get(timeout, unit)`, xử lý `ExecutionException`, `InterruptedException`/timeout thích hợp. Chỉ kiểm tra trạng thái cuối sau khi đã xác nhận toàn bộ writer hoàn thành.
5. **Tách test tất định và stress test.** Test có latch làm tái lập một interleaving nguy hiểm; stress chạy nhiều lịch để mở rộng độ phủ nhưng không thể chứng minh vắng mặt race. Khi cần khảo sát interleaving ở mức JVM, xem công cụ `jcstress` của OpenJDK.
6. **Bảo đảm cleanup.** Đóng executor, giải phóng barrier nếu lỗi, tránh rò thread làm cả bộ test treo. Timeout là cơ chế phát hiện test không kết thúc, không phải bằng chứng độc lập cho kết quả đúng.
7. **Giữ kết quả kiểm tra độc lập với tốc độ máy.** Hạn chế đo thời gian để quyết định tính đúng, không suy luận từ thứ tự log `println` của các luồng.

### Mẫu phân loại lỗi

- **Lost update:** tổng cuối nhỏ hơn số thao tác nguyên tử dự kiến → kiểm tra xem code dùng chuỗi `get` rồi `put` hoặc `x++` không đồng bộ.
- **Stale read:** một luồng không quan sát cập nhật đúng khi contract có happens-before → kiểm tra publish/đồng bộ, sau đó mới nghi implementation.
- **Torn hoặc inconsistent snapshot:** báo cáo tổng hợp lẫn state của nhiều thời điểm → có thể dùng live traversal trong khi nghiệp vụ cần snapshot đồng bộ.
- **Flaky assertion:** lúc pass lúc fail vì ép một thứ tự iteration không được bảo đảm → sửa oracle theo hợp đồng.
- **Unexpected exception:** kiểm tra class collection, quyền sửa đồng thời, comparator/key/value, và việc có dùng wrapper bên ngoài khóa hay không.
- **Hang:** barrier thiếu party, đợi hai chiều, quên release/cleanup, deadlock do khóa hoặc lambda remapping làm việc nguy hiểm.

## 8. Bẫy quan trọng khi áp dụng vào code thật

**Atomic map method và side effect:** đối với `ConcurrentHashMap.compute`, `merge` hay `computeIfAbsent`, hàm được truyền phải ngắn, đơn giản, không cập nhật đệ quy vào cùng map, không khóa kéo dài và không dựa vào side effect bên ngoài để tạo một giao dịch lớn hơn. Nếu một invariant trải trên nhiều key thì giải pháp phải bảo vệ invariant ấy ở mức thiết kế.

**Tham chiếu value mutable:** một map concurrent chỉ quản lý sự an toàn của thao tác map theo hợp đồng; việc sửa trường của `MutableValue` đặt trong map cần cơ chế đồng bộ riêng. Snapshot map hoặc `List.copyOf` không tự tạo bản sao sâu của giá trị.

**Synchronized wrapper và backing object:** sau khi bọc `Collections.synchronizedList(backing)`, đừng tiếp tục sửa `backing` từ luồng khác. Mọi đường truy cập phải tuân thủ cùng giao thức khóa; tương tự khi copy hay iterate.

**Fail-fast không phải bảo đảm thread safety:** `ConcurrentModificationException` là chỉ báo lỗi sử dụng collection ở một số tình huống, không phải oracle đầy đủ hay cơ chế đồng bộ.

**Không nhầm consistency với isolation:** weakly consistent traversal hợp lệ cho dashboard, cache, quan sát gần đúng, nơi trạng thái có thể biến động; khi cần snapshot nhất quán cho kiểm toán, đối soát hoặc giao dịch, phải xây dựng ranh giới snapshot thực sự.

## 9. Bài tập tự kiểm chứng

1. Chỉnh `LostUpdateDemo` chạy 8 tác vụ, mỗi tác vụ tăng 10.000 lần bằng `merge`. Chờ đủ mọi `Future` rồi kiểm tra đúng 80.000. Thử thay bằng `get` + `put` để thấy thất bại **có thể** xảy ra trong stress; không giả định test sai luôn thất bại nếu không điều khiển interleaving.
2. Tạo iterator của `CopyOnWriteArrayList` trước một lần `remove`. Kiểm tra iterator cũ vẫn thấy phần tử cũ; iterator mới thấy phiên bản mới. Thử gọi `oldIterator.remove()` và xác nhận ném `UnsupportedOperationException`.
3. Cho một writer liên tục thêm phần tử vào `ConcurrentHashMap`, reader duyệt bằng iterator riêng; ghi số quan sát được để minh họa dao động, nhưng assertion chỉ kiểm tra cặp key/value hợp lệ và trạng thái cuối sau join.
4. Tạo một danh sách bất biến được publish qua `AtomicReference`. Cho nhiều reader nắm snapshot khác nhau và kiểm tra từng reader chỉ thấy phiên bản mình đã nhận; thảo luận chi phí sao chép.
5. Viết lại một test bị flaky vì dùng `Thread.sleep(20)` để chờ writer. Thay bằng latch/`Future.get` và ghi rõ happens-before nào cho phép kiểm tra.

## 10. Ghi nhớ và tài liệu chuẩn

- **Atomicity:** kiểm tra phép cập nhật và invariant ở đúng phạm vi API cam kết; một chuỗi thao tác riêng lẻ không tự nguyên tử.
- **Snapshot:** cố định phiên bản khi tạo iterator hoặc khi publish reference bất biến; kiểm tra thời điểm lấy snapshot.
- **Weak consistency:** chấp nhận nhiều kết quả duyệt hợp lệ, kiểm tra invariant của entry quan sát được; không đòi một snapshot mà API không bảo đảm.
- **Concurrency test tốt:** điều khiển lịch chạy ở các điểm quan trọng, truyền lỗi từ worker, có timeout/cleanup, và chỉ assert kết quả cuối sau đồng bộ hợp lệ.

Tài liệu Java SE 17:

- [`ConcurrentHashMap` — atomic methods và weakly consistent iterators](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [`CopyOnWriteArrayList` — snapshot iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)
- [`Collections` — synchronized wrappers và yêu cầu khóa khi duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html)
- [`Future` — memory consistency và chờ kết quả](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)


</details>

- [Quay lại đầu trang](#back-to-top)
