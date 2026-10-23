<a id="back-to-top"></a>

# Giá trị của Collection

## Menu
- [List.equals() xét phần tử theo thứ tự](#collection-value-semantics-001)
- [Set.equals() xét cùng các phần tử và không theo thứ tự duyệt](#collection-value-semantics-002)
- [Map.equals() xét cùng các ánh xạ khóa, giá trị](#collection-value-semantics-003)
- [Hash code của List, Set và Map theo hợp đồng tương ứng](#collection-value-semantics-004)
- [Quan hệ bằng nhau có thể áp dụng giữa các lớp triển khai cùng hợp đồng](#collection-value-semantics-005)
- [Tập hợp lồng nhau phụ thuộc equals() của từng phần tử](#collection-value-semantics-006)
- [Phần tử mảng trong Collection vẫn dùng quan hệ bằng nhau của mảng](#collection-value-semantics-007)
- [Tập hợp không cho sửa cấu trúc không đồng nghĩa với bất biến sâu](#collection-value-semantics-008)

## <a id="collection-value-semantics-001">List.equals() xét phần tử theo thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# `List.equals()` xét phần tử theo thứ tự

## 1. Hai danh sách bằng nhau khi nào?

Trong Java, `java.util.List.equals(Object)` xác định **bằng nhau theo giá trị của một dãy có thứ tự**. Hai danh sách được xem là bằng nhau khi và chỉ khi:

1. Đối tượng đem so sánh cũng là một `List`.
2. Hai danh sách có **cùng số lượng phần tử**.
3. Với mỗi chỉ số hợp lệ `i`, phần tử ở vị trí `i` của danh sách thứ nhất bằng phần tử ở **đúng vị trí `i`** của danh sách thứ hai. Nếu cả hai phần tử là `null` thì được xem là bằng nhau tại vị trí đó; nếu không, so sánh bằng `equals` của phần tử.

Diễn đạt bằng điều kiện:

```text
left.equals(right)
    = right là List
      AND left.size() == right.size()
      AND mọi i trong [0, size):
          Objects.equals(left.get(i), right.get(i)) == true
```

Đoạn trên là **mô tả khái niệm**, không phải mã thư viện Java hay bản cài đặt tối ưu. API không bắt buộc phải gọi `get(i)` trên từng phần tử theo cách trên; việc duyệt bằng iterator thường phù hợp hơn cho một số implementation, chẳng hạn danh sách liên kết.

Ví dụ:

```text
["Java", "Spring"] ==giá_trị==> ["Java", "Spring"]    đúng
["Java", "Spring"] ==giá_trị==> ["Spring", "Java"]    sai
["Java", "Java"]   ==giá_trị==> ["Java"]             sai
```

**Thứ tự và số lần xuất hiện đều quan trọng.** Chỉ có cùng những phần tử, hoặc cùng tổng số phần tử, là chưa đủ. Đây là đặc điểm thiết yếu phân biệt `List` với `Set`.

Trong phần dưới, `list.equals(other)` luôn là phép so sánh giá trị. Biểu thức `list == other` lại hỏi hai biến có cùng tham chiếu không.

## 2. Quy tắc theo từng chỉ số

Hãy so sánh:

```java
List<String> a = List.of("A", "B", "A");
List<String> b = List.of("A", "B", "A");
List<String> c = List.of("A", "A", "B");
```

Các đoạn trên dùng kiểu `List` và có thể đặt trong một phương thức sau khi `import java.util.List;`; chưa phải một tệp hoàn chỉnh.

| Chỉ số | `a` | `b` | `c` | `a` so với `b` | `a` so với `c` |
| --- | --- | --- | --- | --- | --- |
| 0 | A | A | A | Bằng | Bằng |
| 1 | B | B | A | Bằng | **Khác** |
| 2 | A | A | B | Bằng | Khác |

Vì thế `a.equals(b)` là `true` còn `a.equals(c)` là `false`. Dù `a` và `c` có cùng tập giá trị và cùng số lần xuất hiện từng giá trị, chúng có **thứ tự khác nhau**. `List.equals` không tự sắp xếp lại trước khi so sánh.

Những trường hợp cần ghi nhớ:

- **Danh sách rỗng:** hai `List` rỗng bằng nhau, kể cả khác implementation; một danh sách rỗng không bằng danh sách có một phần tử `null`.
- **Độ dài khác:** kết quả `false` dù phần đầu của danh sách dài trùng hoàn toàn với danh sách ngắn.
- **Phần tử lặp:** `["x","x","y"]` khác `["x","y","x"]`. Một `Set` có thể loại phần tử lặp, `List` thì giữ.
- **Đổi vị trí hai phần tử bằng nhau:** không làm danh sách khác về giá trị, vì phép so sánh tại mỗi vị trí vẫn đúng.
- **Đổi vị trí hai phần tử khác nhau:** thường làm kết quả `equals` thay đổi, trừ khi các vị trí liên quan vẫn chứa những phần tử được coi là bằng nhau theo chính `equals` của chúng.

## 3. Không phụ thuộc implementation cụ thể

`List.equals` là **hợp đồng của interface `List`**. Vì vậy, hai danh sách cùng phần tử và thứ tự phải bằng nhau dù một bên là `ArrayList`, bên kia là `LinkedList`, hay một danh sách không thể sửa được tạo bởi `List.of`.

```java
// Đặt trong main với các import java.util.* tương ứng.
List<String> array = new ArrayList<>(List.of("one", "two"));
List<String> linked = new LinkedList<>(List.of("one", "two"));

System.out.println(array == linked);       // false
System.out.println(array.equals(linked));  // true
System.out.println(linked.equals(array));  // true
```

Hai biến trỏ đến **hai đối tượng khác nhau** và **hai lớp triển khai khác nhau**, nhưng nội dung dãy tương đương. Điều này minh họa tính đối xứng của hợp đồng `equals`.

Không nên cài đặt `equals` của một lớp `List` tùy biến theo kiểu bắt buộc `obj.getClass() == getClass()`, bởi như vậy có thể làm một `List` mới không bằng `ArrayList` cùng phần tử, trái với hợp đồng chung của `List`.

Về bản chất, tính bằng nhau của `List` **không phụ thuộc** khả năng sửa được, dung lượng cấp phát nội bộ, cách lưu phần tử hay lớp triển khai. Những thuộc tính đó có thể ảnh hưởng đến hiệu năng và hành vi thao tác thêm/xóa, nhưng không nên làm thay đổi kết quả so sánh giá trị.

## 4. Giá trị bằng nhau của từng phần tử: `equals`, kiểu và `null`

### Phần tử được so sánh theo hợp đồng riêng

`List.equals` không biết thế nào là hai phần tử "giống nhau" về nghiệp vụ; nó dựa vào `equals` của từng phần tử. Chẳng hạn:

```java
record Book(String isbn) {}

List<Book> first = List.of(new Book("978-1"));
List<Book> second = List.of(new Book("978-1"));

System.out.println(first.equals(second)); // true
```

Đoạn trên minh họa một `record` và hai danh sách; có thể đặt các khai báo đúng vị trí trong một tệp Java 17+. Record `Book` tự có `equals` dựa trên `isbn`, nên hai đối tượng `Book` khác tham chiếu vẫn bằng nhau.

Ngược lại, nếu `Book` là lớp thông thường không override `equals`, hai lần `new Book(...)` sẽ không tự động bằng nhau chỉ vì các trường trùng nội dung. Kết quả `List.equals` vì thế cũng sẽ là `false`.

Một danh sách `List<Integer>` chứa `1` khác giá trị so với một `List<Long>` chứa `1L`: `Integer.equals(Long)` trả `false`. Java không tự chuyển kiểu số để làm bằng nhau ở cấp phần tử.

### Phần tử `null`

Một số implementation cho phép chứa `null`, ví dụ `ArrayList`. Hai vị trí cùng `null` được coi là bằng nhau:

```java
List<String> withNull1 = new ArrayList<>(Arrays.asList("A", null));
List<String> withNull2 = new LinkedList<>(Arrays.asList("A", null));

System.out.println(withNull1.equals(withNull2)); // true
```

`List.equals` có cách so sánh tương đương `Objects.equals(elementA, elementB)`: hai `null` bằng nhau, một `null` và một phần tử khác `null` thì không. **`List.of(...)` không chấp nhận phần tử `null`** và sẽ ném `NullPointerException` khi tạo danh sách; đây là giới hạn của factory, không phải quy tắc rằng `List.equals` cấm `null`.

Nếu chính biến danh sách là `null`, gọi `list.equals(other)` sẽ ném `NullPointerException`; có thể dùng `Objects.equals(listA, listB)` khi một trong hai **tham chiếu danh sách** có thể `null`. Phải phân biệt danh sách `[null]` (một phần tử) với tham chiếu danh sách `null` (không có đối tượng `List`).

## 5. Ví dụ Java 17 độc lập: kiểm tra thứ tự và implementation

Tạo tệp `ListEqualsOrderDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ListEqualsOrderDemo {
    record Ticket(String code) {}

    public static void main(String[] args) {
        List<String> a = new ArrayList<>(List.of("A", "B", "A"));
        List<String> same = new LinkedList<>(List.of("A", "B", "A"));
        List<String> reordered = List.of("A", "A", "B");
        List<String> shorter = List.of("A", "B");

        System.out.println("same reference: " + (a == same));
        System.out.println("same sequence: " + a.equals(same));
        System.out.println("symmetric: " + same.equals(a));
        System.out.println("different order: " + a.equals(reordered));
        System.out.println("different size: " + a.equals(shorter));
        System.out.println("hash agrees: "
                + (a.hashCode() == same.hashCode()));

        List<String> withNull = new ArrayList<>(
                Arrays.asList("A", null));
        List<String> otherNull = new LinkedList<>(
                Arrays.asList("A", null));
        System.out.println("same null position: "
                + withNull.equals(otherNull));
        System.out.println("null vs nonnull: "
                + withNull.equals(List.of("A", "B")));

        List<Ticket> t1 = List.of(new Ticket("X"));
        List<Ticket> t2 = List.of(new Ticket("X"));
        System.out.println("record elements: " + t1.equals(t2));

        List<List<Integer>> nested1 =
                List.of(List.of(1, 2), List.of(3));
        List<List<Integer>> nested2 =
                List.of(List.of(1, 2), List.of(3));
        List<List<Integer>> nestedReordered =
                List.of(List.of(3), List.of(1, 2));
        System.out.println("nested same: "
                + nested1.equals(nested2));
        System.out.println("nested reordered: "
                + nested1.equals(nestedReordered));

        Set<String> leftSet =
                new HashSet<>(List.of("A", "B"));
        Set<String> rightSet =
                new HashSet<>(List.of("B", "A"));
        System.out.println("set ignores order: "
                + leftSet.equals(rightSet));
    }
}
```

Lệnh chạy:

```bash
javac ListEqualsOrderDemo.java
java ListEqualsOrderDemo
```

Kết quả:

```text
same reference: false
same sequence: true
symmetric: true
different order: false
different size: false
hash agrees: true
same null position: true
null vs nonnull: false
record elements: true
nested same: true
nested reordered: false
set ignores order: true
```

**Giải thích:** `a` là `ArrayList`, `same` là `LinkedList`. Hai đối tượng không cùng tham chiếu nhưng bằng nhau vì ba vị trí khớp. `reordered` giữ cùng số lần xuất hiện các chữ nhưng đổi thứ tự, nên không bằng `a`. `shorter` thiếu phần tử cuối, không bằng `a`. Các phần còn lại minh họa so sánh `null`, đối tượng giá trị dưới dạng record, danh sách lồng nhau và sự khác nhau giữa `List` với `Set`.

Lưu ý `HashSet` ở cuối chỉ dùng để đối chiếu khái niệm: `Set.equals` dựa trên việc chứa cùng các phần tử, **không quan tâm thứ tự duyệt**. Không nên dùng `new HashSet<>(listA).equals(new HashSet<>(listB))` để thay cho `List.equals`, vì việc chuyển sang `Set` có thể làm mất thông tin thứ tự **và số lần xuất hiện**.

## 6. `List.hashCode()` cũng phụ thuộc thứ tự

Hợp đồng của `List` quy định `hashCode()` được tính tuần tự từ phần tử đầu đến cuối. Tư duy thuật toán:

```java
// Thuật toán mô tả theo hợp đồng List; list là một biến List có sẵn.
int hash = 1;
for (Object element : list) {
    hash = 31 * hash + (element == null ? 0 : element.hashCode());
}
```

Đây là đoạn minh họa, không phải một chương trình độc lập. Phép toán `int` có thể tràn theo quy tắc số nguyên Java; đó là một phần của cách tính hash, không phải lỗi.

Hệ quả:

- Hai `List` bằng nhau phải có cùng hash, dù thuộc những implementation khác nhau.
- Thứ tự các phần tử có thể ảnh hưởng hash. Ví dụ, với phần tử `String`, `["A","B"]` thường có hash khác `["B","A"]`.
- Hai danh sách **khác nhau** vẫn có thể có cùng hash do collision; không được dùng `hashCode()` thay cho `equals()` để kết luận hai dãy bằng nhau.
- Phần tử `null` đóng góp `0` ở vị trí tương ứng; nó vẫn chiếm một vị trí trong dãy và ảnh hưởng số lần áp dụng phép nhân `31`.
- Nếu phần tử trong danh sách thay đổi dữ liệu quyết định `equals/hashCode`, hash của chính danh sách có thể thay đổi, dù bạn không thêm/xóa phần tử trong danh sách.

Ví dụ `["A","B"]` có hash:

```text
Bắt đầu h = 1
Sau "A": h = 31 * 1  + 65 = 96
Sau "B": h = 31 * 96 + 66 = 3042

Với ["B","A"]:
Sau "B": h = 31 * 1  + 66 = 97
Sau "A": h = 31 * 97 + 65 = 3072
```

Những phép tính này minh họa việc **đổi thứ tự dẫn tới mã băm khác** trong ví dụ cụ thể. Chúng không chứng minh mọi phép hoán vị đều cho mã băm khác.

## 7. Khác biệt với `Arrays.equals`, `Objects.equals`, `Set.equals`

| Cách so sánh | Ý nghĩa | Điểm cần phân biệt |
| --- | --- | --- |
| `listA.equals(listB)` | So sánh độ dài và từng phần tử **theo thứ tự**, đối số phải là `List`. | Dùng hợp đồng `equals` của từng phần tử. |
| `Objects.equals(listA, listB)` | So sánh hai tham chiếu có thể `null` an toàn. | Khi cả hai là danh sách khác `null`, ủy quyền tới `listA.equals(listB)`. |
| `Arrays.equals(arrayA, arrayB)` | So sánh nội dung hai mảng cùng kiểu tương ứng theo vị trí. | Phải gọi `Arrays.equals`, vì mảng Java tự nó không override `Object.equals`. |
| `Arrays.deepEquals(arrayA, arrayB)` | So sánh sâu mảng tham chiếu, xử lý phần tử là mảng lồng nhau. | Không tự biến so sánh `List` thành tập hợp không thứ tự. |
| `setA.equals(setB)` | Hai `Set` có cùng phần tử theo quan hệ bằng nhau của phần tử. | Không quan tâm thứ tự và không chứa phần tử trùng. |
| `listA == listB` | So sánh hai tham chiếu `List`. | Không so sánh nội dung dãy. |

**Danh sách chứa mảng là bẫy thường gặp.** Nếu `List<int[]>` chứa hai mảng `new int[]{1,2}` khác nhau, `List.equals` sẽ gọi `equals` của phần tử mảng; mảng dùng mặc định `Object.equals` theo **tham chiếu**. Vì vậy hai danh sách có phần tử mảng chứa cùng các số vẫn có thể **không bằng nhau**. Khi muốn so nội dung mảng, phải thiết kế so sánh phần tử bằng `Arrays.equals` hoặc chuyển sang những kiểu giá trị phù hợp.

**Danh sách lồng danh sách** có hành vi khác: vì mỗi phần tử bên trong cũng là `List`, so sánh từng phần tử sẽ gọi `List.equals` ở cấp tiếp theo, theo thứ tự của từng danh sách con. Đây là lý do `nested1.equals(nested2)` trong ví dụ trả `true`.

## 8. Khi danh sách hoặc phần tử thay đổi

Vì `equals` và `hashCode` của `List` phụ thuộc vào **nội dung có thứ tự tại thời điểm gọi**, các thao tác sau có thể làm kết quả thay đổi:

- `add`, `remove`, `clear`: đổi số lượng và/hoặc các vị trí.
- `set(index, value)`: giữ kích thước nhưng đổi phần tử tại một vị trí.
- `Collections.sort`, `Collections.reverse`, hoặc đổi thứ tự thủ công: giữ tập phần tử nhưng thay đổi thứ tự.
- Sửa **trạng thái bên trong một phần tử mutable** có tham gia `equals/hashCode`: dãy giữ nguyên tham chiếu ở từng vị trí nhưng ý nghĩa bằng nhau có thể đổi.
- Sửa nội dung một **danh sách con** trong `List<List<T>>`: danh sách ngoài có thể đổi kết quả `equals/hashCode` dù chưa thêm hoặc xóa phần tử ngoài.

Khi dùng một `List` mutable làm khóa `HashMap` hoặc phần tử của `HashSet`, các thay đổi kể trên có thể làm giá trị hash đổi trong khi cấu trúc băm **không tự lập chỉ mục lại entry**. Hệ quả là `containsKey`, `get`, `contains`, `remove` có thể không tìm được entry đã lưu. Vì vậy, khóa phải giữ ổn định về `equals/hashCode` trong suốt thời gian tồn tại ở cấu trúc băm.

Ví dụ minh họa dùng tệp độc lập `ListKeyDemo.java`:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListKeyDemo {
    public static void main(String[] args) {
        List<String> key = new ArrayList<>(List.of("A"));
        Map<List<String>, String> map = new HashMap<>();
        map.put(key, "saved");

        int before = key.hashCode();
        System.out.println("before: "
                + map.get(List.of("A")));

        key.add("B");
        int after = key.hashCode();
        System.out.println("hash changed: " + (before != after));
        System.out.println("content after: "
                + key.equals(List.of("A", "B")));
        System.out.println("map size: " + map.size());

        List<String> safeKey = List.copyOf(key);
        Map<List<String>, String> safeMap = new HashMap<>();
        safeMap.put(safeKey, "new");
        key.add("C");

        System.out.println("safe key unchanged: "
                + safeKey.equals(List.of("A", "B")));
        System.out.println("safe lookup: "
                + safeMap.get(List.of("A", "B")));
    }
}
```

```bash
javac ListKeyDemo.java
java ListKeyDemo
```

Stdout:

```text
before: saved
hash changed: true
content after: true
map size: 1
safe key unchanged: true
safe lookup: new
```

Không in `map.get(key)` sau khi sửa vì kết quả phụ thuộc cách bảng băm tra cứu; ví dụ tập trung vào điều chắc chắn: **danh sách được sửa, hash đổi, nhưng entry cũ vẫn tồn tại**. `List.copyOf(key)` tạo một danh sách không sửa được và không bị thay đổi bởi thao tác thêm phần tử vào `key` sau đó. Với `String` bất biến, giá trị của `safeKey` cũng ổn định.

Cần phân biệt **không sửa được danh sách** với **bất biến sâu**. Nếu `List.copyOf` chứa những đối tượng mutable, sửa trạng thái của phần tử có thể vẫn làm thay đổi kết quả `equals/hashCode` của danh sách được sao chép. Nếu cần một khóa băm ổn định, phải bảo đảm cả các phần tử và những dữ liệu con mà phép bằng nhau phụ thuộc cũng ổn định.

## 9. Độ phức tạp, hợp đồng và một số lỗi thực tế

Đối với hai danh sách có cùng kích thước `n`, việc so sánh có thể phải xét đến `n` cặp phần tử; độ phức tạp tổng quát của một lần duyệt tuyến tính là **O(n)** cộng thêm chi phí `equals` của từng phần tử. Nó có thể dừng sớm ngay khi phát hiện khác nhau ở một vị trí. Nếu kích thước khác, có thể loại ngay. Bản thân `List.equals` không yêu cầu sắp xếp hay phân tích tập hợp, nên không nên tự chuyển dữ liệu thành `Set` để so sánh khi thứ tự quan trọng.

Những nhầm lẫn dễ gặp:

1. **Chỉ kiểm tra `containsAll`:** `a.containsAll(b)` và `b.containsAll(a)` không chứng minh hai danh sách bằng nhau; thiếu kiểm tra thứ tự và số lần lặp.
2. **Chuyển sang `HashSet` rồi so:** mất thông tin thứ tự và phần tử lặp, dẫn đến `true` cho những danh sách không bằng nhau.
3. **So bằng `==`:** chỉ so tham chiếu, bỏ qua hai danh sách khác instance nhưng cùng nội dung.
4. **So bằng `toString()`:** biểu diễn chuỗi dùng cho hiển thị, không phải hợp đồng bình đẳng. Hai kiểu dữ liệu khác nhau có thể có chuỗi tương tự; một số phần tử có `toString` không phù hợp để định danh.
5. **So riêng hash:** cùng `hashCode` không chứng minh `equals` là `true`.
6. **Tự viết `equals` cho custom `List` nhưng quên quy tắc đối xứng/kiểu:** một `ArrayList` và custom list cùng dãy phải tuân thủ hợp đồng của `List`; lỗi triển khai custom có thể làm hai hướng cho kết quả khác nhau.
7. **Dùng `List.of` với `null`:** factory này cấm `null`, dù nhiều implementation khác cho phép.
8. **Nghĩ `List.copyOf` khiến mọi đối tượng con bất biến:** bản sao không sửa được ở cấp danh sách nhưng phần tử mutable vẫn có thể bị sửa.
9. **So sánh hai danh sách đang bị sửa đồng thời:** nếu collection không được đồng bộ thích hợp, có thể có kết quả không ổn định hoặc ngoại lệ; phải tạo snapshot ổn định hay dùng cơ chế phối hợp phù hợp trước khi so sánh.

Nếu một đối tượng phần tử cài đặt `equals` sai hợp đồng (chẳng hạn không đối xứng hoặc bắc cầu), so sánh ở cấp danh sách cũng có thể biểu lộ sự bất nhất. `List.equals` **dựa vào** `equals` của phần tử và không tự sửa các lỗi ở cấp phần tử.

## 10. Checklist và tự kiểm tra

Trước khi quyết định hai danh sách có bằng nhau hay không, hãy hỏi:

- Có cùng số phần tử không? `[]` khác `[null]`.
- Mỗi chỉ số có một cặp phần tử bằng nhau theo `equals` không?
- Việc lặp phần tử và vị trí xuất hiện có ảnh hưởng đến nghiệp vụ không?
- Các phần tử có định nghĩa `equals` phù hợp không, đặc biệt với class tự tạo hoặc mảng?
- Cần so sánh `List` có thể `null` không? Nếu có, dùng `Objects.equals` cho **tham chiếu danh sách**.
- Danh sách hoặc phần tử có thể sửa không? Nếu dùng làm khóa băm, tính ổn định phải được đảm bảo.
- Yêu cầu thực sự là "cùng dãy có thứ tự" hay "cùng tập phần tử"? Chọn `List.equals` hoặc `Set.equals` theo **ngữ nghĩa nghiệp vụ**.

**Câu hỏi tự luyện:**

1. `List.of(1, 2, 1).equals(List.of(1, 1, 2))` trả gì? Vì sao?
2. `new ArrayList<>(List.of("a")).equals(new LinkedList<>(List.of("a")))` trả gì?
3. Hai `List` cùng kích thước, cùng hash có nhất thiết `equals == true` không?
4. `[null, "x"]` và `["x", null]` có bằng nhau không nếu implementation đều cho phép `null`?
5. Vì sao `List<int[]>` thường không so nội dung của từng mảng như mong đợi?
6. Đổi vị trí hai phần tử có thể làm `List.hashCode` đổi không? Có bắt buộc mọi lần đổi vị trí đều làm hash khác không?
7. Vì sao `List.copyOf` chứa các phần tử mutable vẫn có thể không phù hợp làm khóa băm?
8. Khi ứng dụng không quan tâm thứ tự nhưng cần loại phần tử lặp, nên biểu diễn bằng `List` hay `Set`?

**Ghi nhớ:** `List.equals()` so sánh **độ dài và từng cặp phần tử ở cùng chỉ số**, không phụ thuộc implementation. **Thứ tự và số lần xuất hiện đều có ý nghĩa**. `List.hashCode()` cũng được tính theo thứ tự, vì vậy hãy giữ dữ liệu liên quan đến bình đẳng ổn định khi dùng danh sách trong cấu trúc băm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-002">Set.equals() xét cùng các phần tử và không theo thứ tự duyệt</a>

<details>
<summary>Xem chi tiết</summary>

# `Set.equals()` xét cùng các phần tử, không phụ thuộc thứ tự duyệt

## 1. Quy tắc cốt lõi của `Set.equals()`

Trong Java, `java.util.Set` biểu diễn một tập hợp **không chứa các phần tử trùng nhau theo quan hệ bằng nhau mà cấu trúc sử dụng**. Hợp đồng `Set.equals(Object)` xác định hai tập hợp bằng nhau khi:

1. Đối tượng được so sánh cũng là một `Set`.
2. Hai tập hợp có **cùng số phần tử** (`size()`).
3. **Mọi phần tử** của tập hợp này đều xuất hiện trong tập hợp kia, theo cách xác định quan hệ phần tử của các tập hợp hợp lệ.

Thứ tự thêm phần tử, thứ tự duyệt bằng `iterator()` và kiểu triển khai cụ thể **không phải tiêu chí** của hợp đồng này.

Ví dụ về mặt ý nghĩa:

```text
{ "Java", "Kotlin", "Scala" }
{ "Scala", "Java", "Kotlin" }
→ Set.equals(): true (cùng 3 phần tử, dù thứ tự liệt kê khác nhau)

{ "Java", "Kotlin", "Scala" }
{ "Java", "Kotlin" }
→ Set.equals(): false (khác số phần tử)
```

Ký hiệu trong khối trên là **minh họa tập hợp**, không phải cú pháp khởi tạo `Set` của Java.

**Phân biệt với `List`:** `List.equals()` yêu cầu cùng kích thước và các phần tử tương ứng **đúng cùng vị trí** phải bằng nhau. Vì vậy, hai danh sách có cùng phần tử nhưng hoán đổi vị trí thường không bằng nhau, trong khi hai `Set` chứa cùng phần tử sẽ bằng nhau.

## 2. `HashSet`, `LinkedHashSet` và `TreeSet` khác thứ tự duyệt nhưng vẫn có thể bằng nhau

Ba triển khai thường gặp:

| Triển khai | Đặc điểm duyệt | Khi so sánh bằng `equals()` |
| --- | --- | --- |
| `HashSet` | **Không cam kết** thứ tự duyệt | So sánh tính thành viên và kích thước, không dựa vào thứ tự duyệt |
| `LinkedHashSet` | Thường duyệt theo **thứ tự chèn** | Vẫn áp dụng hợp đồng `Set.equals()` không xét vị trí |
| `TreeSet` | Duyệt theo **thứ tự sắp xếp** (natural order hoặc `Comparator`) | Vẫn phải tuân hợp đồng `Set` khi cách sắp xếp **nhất quán với `equals`** |

Với các phần tử `String` khác `null` và cách sắp xếp tự nhiên thông thường, một `HashSet`, một `LinkedHashSet` và một `TreeSet` có thể bằng nhau nếu chứa cùng các chuỗi. Ví dụ, thứ tự duyệt thấy ở ba tập hợp có thể khác mà `equals()` vẫn trả về `true`.

Không suy ra thứ tự duyệt của `HashSet` từ kết quả chạy máy hoặc nội dung hàm băm. Java không cam kết `HashSet` duyệt theo thứ tự chèn, thứ tự từ điển hay thứ tự ổn định sau các thao tác thay đổi tập hợp.

## 3. Chương trình Java 17+ chạy được

Lưu mã sau vào tệp `SetEqualityDemo.java`:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class SetEqualityDemo {
    record Tag(String code) {}

    public static void main(String[] args) {
        Set<String> hash = new HashSet<>(
                List.of("Java", "Kotlin", "Scala"));
        Set<String> linked = new LinkedHashSet<>(
                List.of("Scala", "Java", "Kotlin"));
        Set<String> sorted = new TreeSet<>(
                List.of("Kotlin", "Scala", "Java"));

        System.out.println("hash.equals(linked): " + hash.equals(linked));
        System.out.println("linked.equals(sorted): " + linked.equals(sorted));
        System.out.println("same set hashCode: "
                + (hash.hashCode() == linked.hashCode()));
        System.out.println("List order matters: "
                + List.of("Java", "Kotlin")
                        .equals(List.of("Kotlin", "Java")));
        System.out.println("missing an element: "
                + hash.equals(Set.of("Java", "Scala")));
        System.out.println("Set vs List: "
                + hash.equals(List.of("Java", "Kotlin", "Scala")));

        Set<Integer> unique = new HashSet<>(Arrays.asList(1, 2, 2, 3));
        System.out.println("unique.size(): " + unique.size());
        System.out.println("integer set hashCode: "
                + Set.of(1, 2, 3).hashCode());

        Set<String> withNull = new HashSet<>(
                Arrays.asList(null, "Java"));
        Set<String> nullInOtherOrder = new LinkedHashSet<>(
                Arrays.asList("Java", null));
        System.out.println("sets with null: "
                + withNull.equals(nullInOtherOrder));

        Set<Tag> firstTags = Set.of(new Tag("A"), new Tag("B"));
        Set<Tag> secondTags = new LinkedHashSet<>(
                List.of(new Tag("B"), new Tag("A")));
        System.out.println("record member equality: "
                + firstTags.equals(secondTags));

        Set<String> absent = null;
        System.out.println("Objects.equals(null, null): "
                + Objects.equals(absent, null));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SetEqualityDemo.java
java SetEqualityDemo
```

Kết quả dự kiến, không phụ thuộc thứ tự duyệt các `Set`:

```text
hash.equals(linked): true
linked.equals(sorted): true
same set hashCode: true
List order matters: false
missing an element: false
Set vs List: false
unique.size(): 3
integer set hashCode: 6
sets with null: true
record member equality: true
Objects.equals(null, null): true
```

Giải thích:

- `hash`, `linked`, `sorted` có cùng ba giá trị chuỗi, nên bằng nhau bất kể thứ tự thêm hoặc duyệt.
- Hai `List` chứa `"Java"` và `"Kotlin"` theo thứ tự trái ngược nên **không bằng nhau**.
- `Set` không bằng `List`, ngay cả khi các giá trị xuất hiện giống hệt: điều kiện đối tượng kia phải là một `Set` không thỏa.
- `new HashSet<>(Arrays.asList(1, 2, 2, 3))` loại phần tử `2` lặp; kết quả chỉ có ba phần tử khác nhau.
- `HashSet` và `LinkedHashSet` cho phép một phần tử `null`; vị trí thêm `null` không làm thay đổi sự bằng nhau. Không suy rộng khả năng chứa `null` cho mọi triển khai `Set`.
- `Tag` là Java `record`, tự cung cấp `equals()`/`hashCode()` theo trường `code`. Hai đối tượng `new Tag("A")` khác nhau vẫn **bằng nhau theo giá trị**; hai tập hợp chứa các thẻ tương đương được coi là bằng nhau.

## 4. Hình dung thuật toán so sánh tập hợp

Về mặt quy tắc, có thể diễn giải việc so sánh hai `Set` hợp lệ bằng giả mã:

```text
if (this == other)                 return true
if (!(other instanceof Set))      return false
if (this.size() != other.size())   return false
return this chứa mọi phần tử của other
```

Đây là **giả mã mô tả hợp đồng**, không phải mã nguồn bắt buộc của mọi bản triển khai. Trong Java, kiểm tra thành viên thường dùng `contains(...)`; `HashSet` dựa vào giá trị `hashCode()` và `equals()` của phần tử, còn `TreeSet` tìm theo quan hệ thứ tự do `compareTo`/`Comparator` cung cấp.

Điều kiện **cùng kích thước** là cần thiết: chỉ kiểm tra `containsAll(...)` một chiều sẽ cho kết quả sai với tập lớn chứa toàn bộ tập nhỏ. Và việc duyệt các phần tử để ghép vào `List`, rồi so sánh hai danh sách bằng `equals`, cũng sai vì vô tình đưa thứ tự vào tiêu chí tập hợp.

## 5. `Set.hashCode()` cũng không phụ thuộc thứ tự duyệt

Hợp đồng `Set.hashCode()` được định nghĩa là **tổng `hashCode()` của mọi phần tử trong tập hợp**; phần tử `null` đóng góp `0` nếu được phép tồn tại. Phép cộng diễn ra theo kiểu `int`, có thể tràn số. Vì phép cộng không phụ thuộc thứ tự nên các tập hợp chứa cùng phần tử cho cùng mã băm, dù thứ tự duyệt khác nhau.

Ví dụ `Set.of(1, 2, 3).hashCode()` trả về `1 + 2 + 3 = 6`, vì `Integer.hashCode(int)` trả lại chính giá trị `int`.

Quy tắc bắt buộc vẫn là:

```text
s1.equals(s2) == true  =>  s1.hashCode() == s2.hashCode()
s1.hashCode() == s2.hashCode()  =/=>  s1.equals(s2) == true
```

Chiều thứ hai không đúng vì va chạm mã băm có thể xảy ra. Chẳng hạn hai tập hợp số nguyên khác nhau `{1, 4}` và `{2, 3}` đều có mã băm bằng `5`, nhưng **không bằng nhau** vì không cùng phần tử.

Không được viết phép so sánh tập hợp bằng cách **chỉ** so `hashCode()`. Hash code hỗ trợ tra cứu; nó không thay thế kiểm tra thành viên.

## 6. Sự bằng nhau của `Set` phụ thuộc sự bằng nhau của phần tử

Hai `Set` có thể chứa các đối tượng thuộc hai vùng nhớ khác nhau nhưng vẫn bằng nhau nếu các phần tử tương ứng **bằng nhau theo `equals()`** và các hàm băm nhất quán.

Ví dụ dùng `record` trong chương trình ở trên. Với lớp tự tạo thông thường, cần ghi đè `equals()` và `hashCode()` nhất quán theo đúng dữ liệu định danh phần tử. Nếu không, `Object.equals()` mặc định kiểm tra danh tính đối tượng, dẫn đến hai phần tử trông giống nhau nhưng không được nhận là bằng nhau.

Các trường hợp cần phân biệt:

- `Set.of(1).equals(Set.of(1L))` trả `false`: `Integer(1)` và `Long(1)` không bằng nhau theo `equals()`, dù có cùng trị số toán học.
- Hai tập hợp có cùng số phần tử nhưng chứa giá trị khác nhau trả `false`.
- Các triển khai có hợp đồng `Set` đúng không đòi hai bên dùng cùng loại cụ thể, ví dụ `HashSet` vẫn so sánh được với `LinkedHashSet`.
- Đưa phần tử có `equals()`/`hashCode()` **phụ thuộc thuộc tính có thể thay đổi** vào `HashSet`, rồi sửa thuộc tính ấy, có thể khiến `contains()`/`remove()` không tìm lại được phần tử. Nên ưu tiên phần tử bất biến khi dùng trong cấu trúc băm.

## 7. Ngoại lệ thiết kế cần biết: `TreeSet` và `Comparator`

`TreeSet` dùng `compareTo()` hoặc `Comparator.compare()` để xác định vị trí sắp xếp và nhận diện phần tử trùng trong cây. Để đáp ứng đầy đủ hợp đồng `Set`, **quan hệ so sánh cho kết quả `0` phải nhất quán với `equals()`** của phần tử.

Ví dụ `String.CASE_INSENSITIVE_ORDER` coi `"java"` và `"JAVA"` bằng nhau theo thứ tự (`compare(...) == 0`), trong khi `"java".equals("JAVA")` là `false`. Một `TreeSet<String>` dùng comparator này có thể chỉ giữ một trong hai chuỗi, khác với `HashSet` thông thường.

```java
TreeSet<String> insensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
insensitive.add("java");
insensitive.add("JAVA");
System.out.println(insensitive.size()); // 1
```

Đoạn mã trên là **minh họa trong một phương thức Java**, cần import `java.util.TreeSet` hoặc dùng tên đầy đủ. Dùng comparator không nhất quán với `equals` có thể khiến kết quả so sánh giữa các kiểu `Set` trở nên bất ngờ, thậm chí vi phạm tính đối xứng kỳ vọng của `Set.equals`. Do đó, không nên áp dụng một cách máy móc kết luận “cùng phần tử” khi mỗi cấu trúc đang dùng **một quan hệ nhận diện phần tử khác nhau**.

Với `TreeSet` dùng thứ tự tự nhiên của `String` như ví dụ chạy đầy đủ, `compareTo()` nhất quán với `String.equals()`, nên không gặp vấn đề này.

## 8. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| Hai tập hợp được duyệt khác thứ tự thì `equals()` phải là `false` | `Set.equals()` không xét thứ tự duyệt |
| `LinkedHashSet` giữ thứ tự chèn nên `equals()` cũng xét thứ tự chèn | Thứ tự chỉ là đặc tính duyệt; hợp đồng bằng nhau vẫn là hợp đồng `Set` |
| `HashSet` không giữ thứ tự nên kết quả `equals()` ngẫu nhiên | `equals()` có quy tắc rõ ràng, độc lập thứ tự duyệt khi phần tử và hợp đồng được cài đặt đúng |
| Hai tập hợp cùng kích thước thì bằng nhau | Cần **cùng các phần tử**, không chỉ cùng số lượng |
| Hai tập hợp có cùng `hashCode()` thì bằng nhau | Mã băm có thể va chạm; cần `equals()`/kiểm tra thành viên |
| Dùng `List.equals()` sau khi đổi `Set` thành `List` | Sai nếu thứ tự duyệt khác nhau, đặc biệt với `HashSet` |
| Bất kỳ hai `TreeSet` và `HashSet` nào chứa chuỗi “gần giống” cũng bằng nhau | Comparator trong `TreeSet` phải nhất quán với `equals()` để giữ đúng hợp đồng tập hợp |
| `Set` nào cũng chấp nhận `null` | `HashSet` cho phép, nhưng `Set.of(...)` và `TreeSet` với thứ tự tự nhiên thường từ chối `null` |

Đừng nhầm hai thao tác: **so sánh nội dung `Set`** và **so sánh thứ tự duyệt**. Nếu bài toán cần giữ vị trí, dùng `List`; nếu cần xét cùng các phần tử mà không xét vị trí, dùng `Set` và quan hệ bằng nhau đúng với miền dữ liệu.

## 9. Câu hỏi tự kiểm tra

1. `new HashSet<>(List.of("A", "B")).equals(new LinkedHashSet<>(List.of("B", "A")))` trả gì? **`true`**, vì cùng các phần tử.
2. Hai `Set` cùng kích thước 3 có chắc bằng nhau? **Không**, còn phải kiểm tra từng thành viên.
3. Hai `Set` bằng nhau có được phép có mã băm khác nhau? **Không**, phải thỏa hợp đồng `hashCode()`.
4. Hai `Set` khác nhau có được phép cùng mã băm? **Có**, đó là va chạm.
5. Một `TreeSet` bỏ qua chữ hoa/chữ thường có thể có ngữ nghĩa thành viên khác `HashSet<String>` không? **Có**, vì comparator có thể không nhất quán với `String.equals()`.

**Ghi nhớ:** Hai `Set` bằng nhau khi **cùng kích thước và cùng các phần tử theo hợp đồng `Set`**, không phụ thuộc vào thứ tự chèn hay duyệt. Hãy bảo đảm `equals()`/`hashCode()` của phần tử và `Comparator` (nếu dùng `TreeSet`) nhất quán với quan hệ bằng nhau mong muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-003">Map.equals() xét cùng các ánh xạ khóa, giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Map.equals() xét cùng các ánh xạ khóa, giá trị

## 1. Khi nào hai Map được coi là bằng nhau?

Trong Java, `Map<K, V>` biểu diễn tập hợp các **ánh xạ từ khóa tới giá trị** (key–value mappings). Hai `Map` được xem là bằng nhau khi chúng chứa **cùng những ánh xạ khóa–giá trị**, dù có thể được tạo từ các lớp triển khai khác nhau hoặc thêm dữ liệu theo thứ tự khác nhau.

Có thể diễn đạt hợp đồng của `Map.equals(Object)` theo quy tắc:

```text
m1.equals(m2) là true nếu:
  1. m2 cũng là một Map;
  2. m1 và m2 có cùng số lượng ánh xạ;
  3. với mỗi khóa trong m1, m2 chứa khóa tương đương
     và giá trị gắn với khóa đó cũng tương đương.
```

Theo đặc tả `Map`, cách định nghĩa chính thức liên hệ với tập các entry: hai map có `entrySet()` bằng nhau thì hai map bằng nhau. Một **entry** là cặp khóa–giá trị.

Ví dụ, hai map cùng chứa `"A" -> 10` và `"B" -> 20` là bằng nhau. Nếu một map đổi `"B" -> 21`, chúng không còn bằng nhau. Nếu có giá trị 20 nhưng gắn với một khóa khác, chúng cũng không bằng nhau.

**Mấu chốt:** `Map.equals()` so sánh **nội dung ánh xạ**, không so sánh hai tham chiếu có trỏ tới cùng một object hay không, không chỉ so danh sách khóa và cũng không chỉ so danh sách giá trị.

## 2. Phân biệt ba tình huống thường bị nhầm

| Tình huống | `Map.equals()` | Giải thích |
| --- | --- | --- |
| Cùng các khóa và cùng giá trị tương ứng, thứ tự thêm khác | `true` | Thứ tự thêm không thuộc tiêu chí equality của `Map` |
| Cùng tập khóa nhưng một giá trị khác | `false` | Đã khác một ánh xạ khóa → giá trị |
| Cùng tập giá trị nhưng giá trị gắn với khóa khác | `false` | Quan hệ ánh xạ khác nhau |
| Cùng cặp ánh xạ nhưng `HashMap` và `LinkedHashMap` | `true` | Khác implementation không làm đổi equality, nếu tuân thủ hợp đồng `Map` |
| Một map có thêm một entry | `false` | Số lượng ánh xạ khác |
| Hai biến tham chiếu tới cùng một map | `true` | Cùng object thì luôn bằng chính nó nếu hợp đồng `equals` được tuân thủ |

Các collection có thứ tự như `LinkedHashMap` có thể giữ thứ tự chèn khi duyệt, và `TreeMap` có thể duyệt theo thứ tự khóa. Nhưng **thứ tự duyệt không phải thành phần trực tiếp của hợp đồng `Map.equals()`**. Đừng dùng `map.toString()` hoặc kết quả duyệt entry làm tiêu chí equality của map.

## 3. Map.equals() phối hợp equals() của khóa và giá trị ra sao?

Một cách hiểu hữu ích: một ánh xạ `(key, value)` được coi là tương đương với `(otherKey, otherValue)` khi:

```java
Objects.equals(key, otherKey)
        && Objects.equals(value, otherValue)
```

Đây là **đoạn biểu thức minh họa**, không phải chương trình độc lập; cần `import java.util.Objects;`. `Objects.equals(a, b)` xử lý được `null`: cả hai cùng `null` thì bằng nhau; nếu chỉ một đối tượng `null`, kết quả là `false`; còn lại dùng `a.equals(b)`.

Khi khóa hoặc giá trị là kiểu do bạn tạo:

- Hai đối tượng khóa **khác instance** vẫn có thể đại diện cho cùng khóa logic nếu `equals(Object)` xác định chúng bằng nhau.
- Hai đối tượng giá trị **khác instance** vẫn có thể tương đương nếu `equals(Object)` của kiểu giá trị trả `true`.
- Nếu hai map có cùng khóa nhưng giá trị không bằng nhau, toàn bộ hai map không bằng nhau.
- `equals(Object)` và `hashCode()` của các kiểu dùng làm khóa phải tuân thủ hợp đồng Java; nếu không, map dựa trên băm có thể hoạt động sai kỳ vọng.

Lưu ý: hai cặp có **cùng giá trị nhưng khác khóa** không trở thành cùng ánh xạ. Map biểu diễn quan hệ “khóa này gắn với giá trị nào”, không phải một tập giá trị độc lập.

## 4. Ví dụ Java 17+: cùng nội dung dù khác implementation và thứ tự

Tạo tệp `MapEqualityDemo.java`:

```java
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class MapEqualityDemo {
    public static void main(String[] args) {
        Map<String, Integer> first = new HashMap<>();
        first.put("A", 10);
        first.put("B", 20);

        Map<String, Integer> second = new LinkedHashMap<>();
        second.put("B", 20);
        second.put("A", 10);

        Map<String, Integer> third = new TreeMap<>();
        third.put("B", 20);
        third.put("A", 10);

        System.out.println(first.equals(second));
        System.out.println(second.equals(first));
        System.out.println(first.equals(third));
        System.out.println(first.hashCode() == second.hashCode());

        second.put("B", 21);
        System.out.println(first.equals(second));

        second.put("B", 20);
        second.remove("A");
        System.out.println(first.equals(second));

        Map<String, Integer> withNull = new HashMap<>();
        withNull.put("missing", null);
        System.out.println(withNull.get("missing") == null);
        System.out.println(withNull.containsKey("missing"));
        System.out.println(withNull.equals(Map.of()));
    }
}
```

Biên dịch và chạy:

```bash
javac MapEqualityDemo.java
java MapEqualityDemo
```

Kết quả:

```text
true
true
true
true
false
false
true
true
false
```

Giải thích:

1. `first`, `second` và `third` đều có `A -> 10`, `B -> 20`. Dù lớp triển khai và thứ tự thêm khác nhau, `equals` vẫn trả `true` giữa chúng.
2. Hai map bằng nhau phải có cùng `hashCode()`. Vì vậy phép so sánh mã băm trả `true`. Chiều ngược lại không đúng: cùng hash chưa chứng minh hai map bằng nhau.
3. Đổi `B` từ 20 thành 21 làm một entry khác đi, nên `first.equals(second)` thành `false`.
4. Khôi phục giá trị `B` rồi xóa entry `A` khiến map còn một entry; kích thước khác nhau nên vẫn `false`.
5. `withNull.get("missing") == null` là `true`, nhưng `containsKey("missing")` cũng là `true`: map có entry `"missing" -> null`. Map rỗng `Map.of()` không bằng map chứa một entry này.

Ví dụ dùng `HashMap`, `LinkedHashMap`, `TreeMap` với khóa `String` theo thứ tự tự nhiên. Một `TreeMap` dùng comparator **không nhất quán với `equals` của khóa** có thể phá các kỳ vọng equality khi so sánh chéo các implementation; xem phần lưu ý phía dưới.

## 5. Nếu khóa và giá trị đều là đối tượng tự định nghĩa

Java record có thể biểu diễn một kiểu giá trị bất biến đơn giản và tự cung cấp `equals(Object)` cùng `hashCode()` theo các thành phần record.

Tạo tệp `MapObjectValueDemo.java`:

```java
import java.util.HashMap;
import java.util.Map;

public class MapObjectValueDemo {
    record ProductId(String code) {}
    record PriceTag(int amount, String currency) {}

    public static void main(String[] args) {
        ProductId keyOne = new ProductId("P-001");
        ProductId keyTwo = new ProductId("P-001");

        PriceTag priceOne = new PriceTag(120, "VND");
        PriceTag priceTwo = new PriceTag(120, "VND");

        System.out.println(keyOne == keyTwo);
        System.out.println(keyOne.equals(keyTwo));
        System.out.println(priceOne.equals(priceTwo));

        Map<ProductId, PriceTag> left = new HashMap<>();
        left.put(keyOne, priceOne);

        Map<ProductId, PriceTag> right = new HashMap<>();
        right.put(keyTwo, priceTwo);

        System.out.println(left.equals(right));

        right.put(keyTwo, new PriceTag(130, "VND"));
        System.out.println(left.equals(right));

        right.clear();
        right.put(new ProductId("P-002"), priceOne);
        System.out.println(left.equals(right));
    }
}
```

Chạy trên JDK 17+:

```bash
javac MapObjectValueDemo.java
java MapObjectValueDemo
```

Stdout:

```text
false
true
true
true
false
false
```

`keyOne` và `keyTwo` là hai object khác tham chiếu (dòng đầu `false`) nhưng cùng `code`, nên bình đẳng theo giá trị (`true`). `priceOne` và `priceTwo` tương tự. Vì hai map chứa cùng ánh xạ logic `P-001 -> (120, VND)`, `left.equals(right)` là `true`.

Đổi giá trị tương ứng thành 130 khiến hai map khác nhau. Tiếp theo, dù lại dùng giá trị `priceOne`, thay khóa bằng `P-002` vẫn làm hai map khác nhau. Qua đó thấy rõ điều kiện equality gồm **cả khóa và giá trị gắn với khóa đó**.

Record không tự biến các thành phần mutable được chứa bên trong thành bất biến sâu. Ví dụ `record DataKey(List<String> parts) {}` vẫn giữ tham chiếu tới danh sách có thể thay đổi. Khi dùng record làm khóa, cần bảo đảm trạng thái tham gia `equals/hashCode` ổn định khi đang nằm trong map.

## 6. Map.hashCode() cũng dựa trên các entry

Theo hợp đồng `Map.hashCode()`, mã băm của map bằng **tổng các mã băm của entry** trong `entrySet()`. Mã băm của một `Map.Entry` theo hợp đồng là:

```text
entryHash = hashCode(key) XOR hashCode(value)
            (null được tính là 0)

mapHash = tổng entryHash của tất cả entry
```

Điều này giải thích vì sao hai map bằng nhau phải có cùng hash **mà không phụ thuộc thứ tự duyệt entry**: phép cộng không phụ thuộc thứ tự. Tuy vậy, hai map khác nhau vẫn có thể trùng hash do collision; tuyệt đối không dùng `map1.hashCode() == map2.hashCode()` thay cho `map1.equals(map2)`.

Có thể kiểm chứng hợp đồng trên một map đơn giản:

```java
Map<String, Integer> m = Map.of("A", 10, "B", 20);
int sumOfEntries = m.entrySet().stream()
        .mapToInt(Map.Entry::hashCode)
        .sum();
boolean sameHash = m.hashCode() == sumOfEntries;
```

Đây là **đoạn mã minh họa**, cần `import java.util.Map;`. `sameHash` phải là `true` với một implementation tuân thủ hợp đồng `Map`.

Mã băm của một map không phải ID duy nhất, chữ ký số hay giá trị bền vững để lưu vào hệ thống khác. Nếu khóa hoặc giá trị mutable thay đổi những trường tham gia equality, mã băm map cũng có thể thay đổi.

## 7. Null, map rỗng và sự khác biệt giữa “không có khóa” và “có khóa ánh xạ tới null”

Ba map sau không bằng nhau:

```text
{}                   (không có entry)
{"x" -> null}        (một entry có value null)
{null -> "x"}        (một entry có key null)
```

`HashMap` cho phép tối đa một khóa `null` và có thể có nhiều khóa khác nhau mang giá trị `null`. `Map.of(...)` và `Map.ofEntries(...)` không cho phép khóa hay giá trị `null`; nhiều implementation khác cũng có chính sách riêng.

- `get(key) == null` **không phân biệt** một khóa không tồn tại và một khóa đang ánh xạ đến `null`.
- `containsKey(key)` trả lời khóa có tồn tại hay không.
- Hai map chỉ bằng nhau khi tình trạng **tồn tại khóa** và **giá trị đi kèm** tương ứng. Một entry `"x" -> null` phải được so với entry có cùng khóa và cũng có giá trị `null`.

Khi cần xử lý `null`, dựa vào hợp đồng của implementation cụ thể trước khi gọi `put(null, value)` hoặc `put(key, null)`. Đừng suy rằng mọi `Map` đều hỗ trợ null chỉ vì `HashMap` hỗ trợ.

## 8. Map khác List và Set về điều kiện equality

Mỗi collection có hợp đồng equality riêng:

| Kiểu | Điều kiện bằng nhau chính | Thứ tự có quan trọng? |
| --- | --- | --- |
| `List` | Cùng độ dài, các phần tử cùng vị trí tương đương | **Có** |
| `Set` | Cùng các phần tử logic | Thông thường **không** |
| `Map` | Cùng các ánh xạ khóa–giá trị | **Không** theo hợp đồng Map |

Ví dụ, `List.of("A", "B")` không bằng `List.of("B", "A")`. Nhưng một map chứa `A -> 1` rồi `B -> 2` vẫn bằng map tạo `B -> 2` rồi `A -> 1`.

Đồng thời, map **không chỉ là tập khóa**: hai map có cùng keySet nhưng các value khác nhau thì không bằng nhau. Nó cũng không chỉ là một tập value: nếu đổi hai giá trị giữa hai khóa thì các ánh xạ đã thay đổi.

## 9. Những ngoại lệ và tình huống cần cẩn trọng

### Comparator không nhất quán với equals

`TreeMap` dùng comparator hoặc natural ordering để xác định hai khóa được xem là cùng vị trí trong cấu trúc cây. Nếu comparator trả 0 cho hai khóa mà `equals` lại trả `false`, map có thể coi chúng là cùng khóa trong thao tác `put/get` nhưng các map dùng equality thông thường lại phân biệt chúng.

Ví dụ comparator không phân biệt chữ hoa/thường có thể xem `"A"` và `"a"` tương đương về thứ tự, trong khi `String.equals` phân biệt chúng. JavaDoc của `TreeMap` yêu cầu thứ tự phải **nhất quán với equals** nếu muốn tuân thủ đúng hợp đồng `Map` khi so sánh giữa các implementation.

Một `TreeMap<BigDecimal, ...>` dùng thứ tự tự nhiên cũng cần cân nhắc tương tự, vì `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0`, trong khi `equals` trả `false`.

### IdentityHashMap không dùng equality thông thường

`IdentityHashMap` chủ ý xem hai khóa bằng nhau khi **cùng tham chiếu (`==`)** thay vì `equals`. Đây là implementation đặc biệt mà tài liệu Java nói rõ là **không tuân thủ hợp đồng chung của `Map` về equality theo đối tượng**. Vì vậy không nên dùng nó như một lựa chọn thay thế trực tiếp cho `HashMap` nếu mã gọi dựa vào `Map.equals()` theo giá trị.

### Giá trị mutable và key mutable

Hai map hiện tại có thể bằng nhau nhưng về sau trở thành không bằng nhau nếu một đối tượng value bên trong bị sửa làm `equals` thay đổi. Nếu đối tượng key bị sửa làm `hashCode` hoặc `equals` thay đổi sau khi đã thêm vào `HashMap`, việc tra cứu và `Map.equals()` cũng có thể không còn đáng tin cậy theo ý định ban đầu.

Đặc biệt, tránh **đưa chính map làm khóa hoặc giá trị của nó theo một cấu trúc tự tham chiếu** rồi trông chờ các phương thức `equals/hashCode/toString` xử lý an toàn; một số phép toán có thể không còn được định nghĩa tốt với cấu trúc đệ quy này.

## 10. Cách áp dụng trong code và kiểm thử

1. **Định nghĩa bình đẳng của khóa:** nếu hai key được coi là cùng thực thể logic, cài `equals(Object)` và `hashCode()` tương thích. Cân nhắc dùng record hoặc kiểu bất biến cho khóa.
2. **Định nghĩa bình đẳng của giá trị:** kiểm tra xem `equals` của value có phản ánh đúng điều kiện nghiệp vụ hay không. Ví dụ `BigDecimal.equals` phân biệt scale; `List.equals` phân biệt thứ tự.
3. **Kiểm thử khác thứ tự chèn:** tạo `HashMap` và `LinkedHashMap` với cùng mapping theo thứ tự ngược nhau; chúng vẫn phải bằng nhau.
4. **Kiểm thử thay đổi một thành phần:** đổi value của một key hoặc thay key, xác minh `Map.equals` trở thành `false`.
5. **Kiểm thử null và thiếu khóa:** phân biệt map có `"x" -> null` với map rỗng; dùng `containsKey` khi cần.
6. **Kiểm tra hash:** hai map bằng nhau phải có cùng `hashCode`; không dùng hash bằng nhau để suy ra map bằng nhau.
7. **Kiểm tra collection có comparator đặc biệt:** tránh giả định comparator trả 0 có nghĩa `equals` của khóa cũng trả `true`.
8. **Không so bằng chuỗi hiển thị:** thay vì so `map.toString()`, hãy dùng `map.equals(other)` khi mục đích là cùng nội dung ánh xạ.
9. **Giữ trạng thái ổn định khi so sánh:** không thay đổi map hoặc object khóa/giá trị đồng thời trong lúc thao tác equality; các collection thông thường không tự thread-safe.

**Tự kiểm tra:**

- Hai map có `A -> 1`, `B -> 2` nhưng thứ tự chèn ngược nhau có bằng nhau không? **Có.**
- Hai map có cùng `keySet()` nhưng một map chứa `B -> 3` thay vì `B -> 2` có bằng nhau không? **Không.**
- Hai map có cùng danh sách giá trị nhưng ánh xạ sang khóa khác nhau có bằng nhau không? **Không.**
- Một map chứa entry `A -> null` có bằng map rỗng không? **Không.**
- Hai map bằng nhau có bắt buộc `hashCode()` bằng nhau không? **Có.**

**Ghi nhớ:** `Map.equals(Object)` biểu thị **sự tương đương của toàn bộ các ánh xạ khóa–giá trị**. Cùng số entry, cùng khóa logic và giá trị tương ứng bằng nhau mới làm hai map bằng nhau; thứ tự chèn, thứ tự duyệt và lớp triển khai thông thường không quyết định kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-004">Hash code của List, Set và Map theo hợp đồng tương ứng</a>

<details>
<summary>Xem chi tiết</summary>

# Hash code của List, Set và Map theo hợp đồng tương ứng

## 1. Mục tiêu: hiểu công thức được API quy định

Trong Java Collections Framework, `List`, `Set` và `Map` định nghĩa **quy tắc cụ thể cho `equals(Object)` và `hashCode()`** để các cách cài đặt khác nhau vẫn tương thích khi biểu diễn cùng một giá trị logic.

Điểm mấu chốt: **không có một công thức `hashCode()` chung cho mọi collection**. Ba loại này có ý nghĩa bình đẳng khác nhau:

- **`List`:** dãy phần tử có vị trí, nên thứ tự là một phần của giá trị.
- **`Set`:** tập hợp phần tử không trùng lặp; vị trí/thứ tự duyệt không phải một phần của bình đẳng theo hợp đồng `Set`.
- **`Map`:** tập hợp các ánh xạ từ khóa đến giá trị; thứ tự duyệt/chèn không phải một phần của bình đẳng theo hợp đồng `Map`.

Vì vậy, `List` dùng công thức lũy tiến nhân **31**; `Set` cộng mã băm các phần tử; còn `Map` cộng mã băm các *entry*, mà mỗi entry kết hợp mã băm khóa và giá trị bằng XOR. Đây là các **công thức được Javadoc Java 17 quy định**, không phải chi tiết bucket ngẫu nhiên của riêng `ArrayList`, `HashSet` hoặc `HashMap`.

> Phân biệt kiểu: `List` và `Set` đều mở rộng `Collection`; `Map` **không** mở rộng `Collection`. Chúng thuộc cùng Java Collections Framework nhưng có các hợp đồng riêng.

## 2. Nền tảng: hợp đồng `equals()` và `hashCode()`

Trước khi xét công thức, cần nhớ hợp đồng của `Object`:

1. Nếu `a.equals(b)` trả `true` thì **bắt buộc** `a.hashCode() == b.hashCode()`.
2. Nếu hai đối tượng **không** bằng nhau, chúng **vẫn có thể** trùng mã băm (collision); mã băm không phải ID duy nhất.
3. Trong một lần thực thi Java, nếu không có thông tin dùng bởi `equals` thay đổi thì kết quả `hashCode` trên cùng đối tượng phải ổn định.
4. Không có yêu cầu mã băm cố định giữa các lần chạy JVM khác nhau.

Mỗi kiểu `List`/`Set`/`Map` quy định công thức băm để bảo đảm rằng **hai instance bằng nhau về mặt logic, dù thuộc các lớp triển khai khác nhau, có cùng mã băm**.

Ví dụ, một `ArrayList<String>` và một `LinkedList<String>` chứa cùng dãy phần tử có thể bằng nhau; một `HashSet<String>` và `LinkedHashSet<String>` chứa cùng tập phần tử cũng bằng nhau; tương tự cho `HashMap` và `LinkedHashMap` chứa cùng các cặp khóa–giá trị.

Các phần tử, khóa và giá trị tự chúng vẫn phải triển khai `equals/hashCode` đúng hợp đồng. Collection không thể “sửa hộ” một phần tử có cặp phương thức sai.

## 3. `List.hashCode()`: phép tích lũy theo thứ tự

### 3.1. Quy tắc chính thức

Javadoc của `List.hashCode()` quy định thuật toán tương đương:

```java
int hashCode = 1;
for (E element : list) {
    hashCode = 31 * hashCode
            + (element == null ? 0 : element.hashCode());
}
```

Đây là **đoạn mã giả Java theo hợp đồng API**, với biến `list` đại diện cho một danh sách. Nó không phải chương trình độc lập vì `E` và `list` chưa được khai báo.

Giải thích:

- Bắt đầu từ `1`, vì vậy **danh sách rỗng có mã băm bằng `1`**.
- Mỗi phần tử, theo **đúng thứ tự duyệt của danh sách**, đóng góp vào giá trị hiện tại.
- Phần tử `null` đóng góp `0`; công thức vẫn nhân mã băm tích lũy với `31`.
- Các phép nhân/cộng được thực hiện trên `int` của Java; nếu vượt miền 32 bit, giá trị số nguyên tràn theo quy tắc của `int`. Không chuyển thành phép toán số thực, chuỗi hay hash ngẫu nhiên.

Vì phép nhân `31` diễn ra sau từng bước, **thứ tự thường ảnh hưởng mã băm**. Tuy nhiên, hai danh sách khác thứ tự *có thể tình cờ trùng mã băm*; sự khác biệt vị trí làm chúng không bằng nhau theo `List.equals`, chứ không bảo đảm mã băm luôn khác nhau.

### 3.2. Tính thử bằng tay

Với `"A".hashCode() == 65`, `"B".hashCode() == 66`:

| Danh sách | Cách tính | Mã băm |
| --- | --- | ---: |
| `[]` | Giá trị khởi tạo | `1` |
| `["A"]` | `31 × 1 + 65` | `96` |
| `["A", "B"]` | `31 × 96 + 66` | `3042` |
| `["B", "A"]` | `31 × (31 + 66) + 65` | `3072` |
| `["A", null]` | `31 × (31 + 65) + 0` | `2976` |

Thứ tự này khớp với `List.equals`: hai list bằng nhau khi đối tượng còn lại cũng là `List`, có cùng kích thước và từng cặp phần tử tại **cùng vị trí** bằng nhau (xử lý `null` đúng cách).

Hai `List` có cùng phần tử nhưng thứ tự khác nhau không được mặc định xem là bằng nhau. Danh sách cũng có thể chứa phần tử lặp, ví dụ `["A", "A"]`, và mỗi **vị trí** vẫn được tính vào công thức.

### 3.3. Không phụ thuộc vào lớp lưu trữ

Một `ArrayList<>(List.of("A", "B"))` và một `LinkedList<>(List.of("A", "B"))` bằng nhau và đều có `hashCode() == 3042`. Bất kể một lớp dùng mảng, một lớp dùng danh sách liên kết, **hợp đồng `List` quyết định mã băm của chúng**.

Nếu tự triển khai `List`, phải tuân thủ hợp đồng này; chỉ override `equals` mà tính một giá trị băm tùy ý theo vị trí bộ nhớ sẽ phá tính tương thích với các cách triển khai khác.

## 4. `Set.hashCode()`: tổng mã băm các phần tử

### 4.1. Quy tắc chính thức

Theo Javadoc của `Set`, mã băm của tập hợp là **tổng mã băm của các phần tử**, với `null` được coi là `0`:

```java
int hashCode = 0;
for (E element : set) {
    hashCode += (element == null ? 0 : element.hashCode());
}
```

Đây là **đoạn mã giả theo hợp đồng**, không phải tệp Java độc lập.

- **Tập hợp rỗng có mã băm `0`.**
- Thứ tự cộng không thay đổi kết quả số nguyên; mã băm của một tập hợp **không phụ thuộc thứ tự chèn hoặc duyệt**.
- Mỗi phần tử có mặt trong set được cộng một lần; các giá trị bằng nhau không được lưu thành hai phần tử khác biệt theo hợp đồng `Set`.
- Mã băm có thể trùng giữa các tập hợp khác nhau, kể cả khi không có phần tử nào chung.

Ví dụ, `Set.of("A", "B")` có giá trị `65 + 66 = 131`. Một `HashSet` thêm A rồi B và một `LinkedHashSet` thêm B rồi A đều biểu diễn cùng tập và có mã băm `131`. Tập chứa `"A"` và `null`, với triển khai cho phép `null` như `HashSet`, có mã băm `65 + 0 = 65`.

### 4.2. Vì sao không áp dụng công thức `List` cho `Set`?

Một `Set` không có *vị trí phần tử* trong định nghĩa bình đẳng. Nếu mã băm phụ thuộc thứ tự duyệt, hai set bằng nhau nhưng có thứ tự lặp khác nhau có thể nhận mã băm khác nhau, vi phạm `equals => hashCode`.

Định nghĩa `Set.equals` so sánh hai set có cùng kích thước và các phần tử tương đương nhau, **không so vị trí**. Vì vậy công thức tổng là phép kết hợp giao hoán phù hợp với ngữ nghĩa set.

Cần phân biệt việc `LinkedHashSet` duy trì thứ tự lặp với việc thứ tự ấy **không tham gia** `Set.equals/hashCode`. Một lớp có thứ tự duyệt xác định vẫn phải tuân thủ cùng hợp đồng của `Set`.

### 4.3. Collision không vi phạm hợp đồng

Giả sử hai phần tử khác nhau có mã băm `10` và `20`, trong khi một phần tử thứ ba có mã băm `30`. Khi ấy một set chứa hai phần tử đầu và set chứa phần tử thứ ba **có thể cùng mã băm `30`** nhưng không bằng nhau.

Không được kết luận hai set bằng nhau chỉ từ mã băm. Khi cần so sánh chính xác, dùng `set1.equals(set2)`.

## 5. `Map.hashCode()`: tổng mã băm các ánh xạ

### 5.1. Quy tắc chính thức của `Map`

Theo hợp đồng `Map.hashCode()`:

> Mã băm của một map bằng tổng mã băm của các entry trong `entrySet()`.

Viết dưới dạng công thức:

```text
hashCode(map) = Σ hashCode(entry) với mỗi entry ∈ map.entrySet()
```

Đây là công thức của giao diện `Map`, không đòi hỏi map phải được cài bằng bảng băm. Những triển khai khác nhau có cùng các ánh xạ logic phải sinh cùng mã băm.

### 5.2. Mã băm của một `Map.Entry`

`Map.Entry.hashCode()` quy định:

```java
int entryHash = (key == null ? 0 : key.hashCode())
              ^ (value == null ? 0 : value.hashCode());
```

Đây là **đoạn mã minh họa công thức**, trong đó `key` và `value` là các biến chưa khai báo. Ký hiệu `^` trong Java là **bitwise XOR** trên hai số nguyên, **không phải phép lũy thừa**.

Sau đó `Map.hashCode()` cộng các mã băm entry:

```java
int mapHash = 0;
for (Map.Entry<K, V> entry : map.entrySet()) {
    K key = entry.getKey();
    V value = entry.getValue();
    mapHash += (key == null ? 0 : key.hashCode())
            ^ (value == null ? 0 : value.hashCode());
}
```

**Lưu ý về ưu tiên toán tử:** trong biểu thức Java cuối trên, phép cộng `+=` có ưu tiên gán thấp; để thể hiện rõ mỗi entry được XOR trước rồi cộng vào tổng, nên dùng dấu ngoặc như phiên bản sau:

```java
mapHash += ((key == null ? 0 : key.hashCode())
         ^  (value == null ? 0 : value.hashCode()));
```

Nếu sao chép vào mã chạy thực tế, luôn dùng dấu ngoặc này để không nhầm biểu thức và thuận tiện cho người đọc.

### 5.3. Ví dụ tính tay

Với map chứa `"A" -> 1` và `"B" -> 2`:

| Ánh xạ | Mã băm khóa | Mã băm giá trị | Mã băm entry (XOR) |
| --- | ---: | ---: | ---: |
| `"A" -> 1` | `65` | `1` | `65 ^ 1 = 64` |
| `"B" -> 2` | `66` | `2` | `66 ^ 2 = 64` |

Tổng của hai entry là `64 + 64 = 128`. Do phép cộng giao hoán, map thêm B trước A và map thêm A trước B vẫn có mã băm `128` nếu chúng biểu diễn cùng các ánh xạ.

Các trường hợp đặc biệt:

- **Map rỗng:** không có entry nào để cộng, do đó `hashCode() == 0`.
- **Khóa `null` hoặc giá trị `null`:** thành phần mã băm ấy đóng góp `0` nếu triển khai cho phép. Chẳng hạn `HashMap` có `"A" -> null` và `null -> "B"` thì tổng băm là `(65 ^ 0) + (0 ^ 66) = 131`.
- **Đổi giá trị ứng với cùng khóa:** ánh xạ logic đã thay đổi, nên kết quả mã băm có thể thay đổi. Tuy nhiên không phải mỗi lần thay đổi mapping đều bắt buộc tạo mã băm khác; collision vẫn có thể xảy ra.

`Map.equals` so sánh **các ánh xạ**: hai map bằng nhau nếu chứa cùng tập cặp khóa–giá trị theo ngữ nghĩa bình đẳng tương ứng, không xét thứ tự chèn. Vì vậy mã băm dựa trên entry, chứ không chỉ khóa.

## 6. Chương trình Java 17: đối chiếu ba hợp đồng

Ví dụ hoàn chỉnh dùng hai cách triển khai khác nhau cho từng loại. Các phép kiểm tra dựa vào công thức giao diện và dùng dữ liệu `String`/`Integer` có mã băm xác định; không suy luận từ bucket hoặc thứ tự không được bảo đảm của `HashMap`/`HashSet`.

Tạo tệp **`CollectionHashContractDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionHashContractDemo {
    public static void main(String[] args) {
        List<String> arrayList = new ArrayList<>(List.of("A", "B"));
        List<String> linkedList = new LinkedList<>(List.of("A", "B"));
        List<String> reversedList = List.of("B", "A");
        System.out.println("list equals: " + arrayList.equals(linkedList));
        System.out.println("list hashes: " + arrayList.hashCode()
                + ", " + linkedList.hashCode());
        System.out.println("reversed list hash: " + reversedList.hashCode());
        System.out.println("list with null hash: "
                + Arrays.asList("A", null).hashCode());

        Set<String> hashSet = new HashSet<>(List.of("A", "B"));
        Set<String> linkedSet = new LinkedHashSet<>(List.of("B", "A"));
        System.out.println("set equals: " + hashSet.equals(linkedSet));
        System.out.println("set hashes: " + hashSet.hashCode()
                + ", " + linkedSet.hashCode());
        Set<String> nullSet = new HashSet<>();
        nullSet.add("A");
        nullSet.add(null);
        System.out.println("set with null hash: " + nullSet.hashCode());

        Map<String, Integer> hashMap = new HashMap<>();
        hashMap.put("A", 1);
        hashMap.put("B", 2);
        Map<String, Integer> linkedMap = new LinkedHashMap<>();
        linkedMap.put("B", 2);
        linkedMap.put("A", 1);
        System.out.println("map equals: " + hashMap.equals(linkedMap));
        System.out.println("map hashes: " + hashMap.hashCode()
                + ", " + linkedMap.hashCode());
        System.out.println("map entry A hash: "
                + ("A".hashCode() ^ Integer.hashCode(1)));
        System.out.println("map entry B hash: "
                + ("B".hashCode() ^ Integer.hashCode(2)));
        Map<String, String> nullMap = new HashMap<>();
        nullMap.put("A", null);
        nullMap.put(null, "B");
        System.out.println("map with null hash: " + nullMap.hashCode());

        System.out.println("empty hashes: " + List.of().hashCode()
                + ", " + Set.of().hashCode()
                + ", " + Map.of().hashCode());
    }
}
```

Chạy bằng JDK 17+:

```bash
javac --release 17 CollectionHashContractDemo.java
java CollectionHashContractDemo
```

**Output đã kiểm chứng:**

```text
list equals: true
list hashes: 3042, 3042
reversed list hash: 3072
list with null hash: 2976
set equals: true
set hashes: 131, 131
set with null hash: 65
map equals: true
map hashes: 128, 128
map entry A hash: 64
map entry B hash: 64
map with null hash: 131
empty hashes: 1, 0, 0
```

Phân tích:

1. **`List`:** `ArrayList` và `LinkedList` chứa cùng `["A","B"]` bằng nhau; cùng mã băm `3042`. Đổi thứ tự cho ví dụ cụ thể tạo mã băm `3072`, đúng công thức phụ thuộc vị trí. `Arrays.asList("A", null)` minh họa quy tắc phần tử `null` đóng góp `0`.
2. **`Set`:** `HashSet` và `LinkedHashSet` bằng nhau dù thứ tự thêm khác nhau; cùng tổng băm `65+66=131`. Tập có `null` dùng mã băm phần tử `0`; `HashSet` cho phép `null`.
3. **`Map`:** `HashMap` và `LinkedHashMap` có cùng hai ánh xạ nên bằng nhau, băm bằng `128`. Mỗi entry trong dữ liệu mẫu có XOR bằng `64`, tổng là `128`. Trường hợp `null` sử dụng `HashMap` vì `HashMap` cho phép khóa/giá trị `null`.
4. **Rỗng:** công thức List khởi tạo `1`, còn Set và Map lấy tổng rỗng `0`. Vì vậy ba mã băm của collection rỗng lần lượt là `1`, `0` và `0`.

**Chú ý API factory:** `List.of(...)`, `Set.of(...)` và `Map.of(...)` **không chấp nhận phần tử/khóa/giá trị `null`**. Ví dụ dùng `Arrays.asList`, `HashSet` và `HashMap` cho các trường hợp có `null`; không thay các dòng ấy bằng `List.of(null)` hoặc `Map.of("A", null)` vì sẽ ném `NullPointerException`.

## 7. So sánh nhanh các công thức

| Kiểu | Điều kiện `equals` chính | Công thức `hashCode` | Có xét thứ tự? | Mã băm khi rỗng |
| --- | --- | --- | --- | ---: |
| `List` | Cùng kích thước, từng phần tử ở vị trí tương ứng bằng nhau | Bắt đầu `1`; với từng phần tử: `h = 31*h + elementHash` | Có, theo thứ tự list | `1` |
| `Set` | Cùng tập phần tử logic, không xét vị trí | Tổng `elementHash` của từng phần tử | Không | `0` |
| `Map` | Cùng các ánh xạ khóa → giá trị logic | Tổng `(keyHash ^ valueHash)` của từng entry | Không | `0` |

Trong bảng, `elementHash`, `keyHash` hoặc `valueHash` bằng `0` khi thành phần tương ứng là `null` (và kiểu collection cho phép `null`).

Đừng nhầm **thứ tự tham gia băm** với **thứ tự duyệt**: một `LinkedHashSet` có thứ tự duyệt cụ thể nhưng mã băm vẫn theo công thức Set không nhạy thứ tự. Tương tự, một `LinkedHashMap` có thể duyệt theo thứ tự chèn nhưng `Map.hashCode` không dựa trên thứ tự entry.

Ngoài ra, một `List` không cần bằng một `Set` chỉ vì chúng chứa cùng tên các phần tử. Hai giao diện định nghĩa bình đẳng **trong cùng loại cấu trúc logic**; không gộp các công thức để so sánh khác loại.

## 8. Mối liên hệ giữa `Map.hashCode()` và `entrySet()`

Theo hai hợp đồng:

```text
Map.hashCode()
  = tổng hashCode của các Map.Entry thuộc entrySet()
  = entrySet().hashCode()
```

Điều này đúng với một `Map` triển khai hợp lệ, vì `entrySet()` là một `Set<Map.Entry<K,V>>` và `Set.hashCode` chính là tổng mã băm phần tử. Lưu ý sự tương đương **của mã băm** ở đây không có nghĩa một `Map` bằng một `Set` theo `equals`.

Phân biệt ba view trên map:

- `keySet()` là một `Set<K>`: băm dựa trên **các khóa**, không chứa mã băm giá trị.
- `values()` là một `Collection<V>`: giao diện `Collection` không quy định một công thức `hashCode` chung như `List` hoặc `Set`; đừng giả sử nó bằng tổng mã băm các giá trị.
- `entrySet()` là một `Set<Map.Entry<K,V>>`: băm dựa trên **các ánh xạ khóa–giá trị**, nên tổng chính là mã băm map.

Chọn đúng view khi cần xử lý khóa, giá trị hoặc toàn bộ ánh xạ; không suy ra băm của map từ `keySet().hashCode()`.

## 9. Các lỗi phổ biến khi triển khai hoặc sử dụng

### 9.1. Dùng cùng công thức băm cho mọi kiểu

Dùng tổng cho `List` sẽ bỏ qua vai trò vị trí và không đáp ứng thuật toán mà giao diện List đã quy định. Dùng nhân `31` cho `Set` theo thứ tự duyệt sẽ khiến hai set bằng nhau có nguy cơ sinh mã băm khác nhau. Với `Map`, chỉ băm khóa sẽ bỏ qua giá trị và không tuân theo công thức `Map.hashCode`.

Không chỉ kiểm tra quy tắc tổng quát “bằng nhau ⇒ cùng băm”; khi triển khai một giao diện cụ thể, hãy tuân thủ **công thức mà chính giao diện đó yêu cầu**.

### 9.2. Tưởng mã băm giống nhau nghĩa là collection bằng nhau

Collision xảy ra ở cả ba loại. Ví dụ `Map.Entry("A", 1)` và `Map.Entry("B", 2)` trong bài có cùng mã băm `64` nhưng không phải cùng ánh xạ. Đừng dùng `hashCode` thay `equals` để quyết định tính bằng nhau.

Thậm chí tổng hash `Set` hoặc `Map` bằng nhau còn dễ xuất hiện trong các cấu hình phần tử khác nhau. Mã băm là dấu hiệu phục vụ tra cứu, **không phải bằng chứng tương đương nội dung**.

### 9.3. Phụ thuộc thứ tự của `HashSet` hoặc `HashMap`

Không đưa thứ tự iterator của `HashSet`/`HashMap` vào phép tính băm theo cách riêng. Hợp đồng Set/Map là độc lập thứ tự. Nếu nghiệp vụ cần thứ tự, dùng đúng collection có cam kết tương ứng; đừng viết `equals/hashCode` dựa trên thứ tự bucket của triển khai hiện tại.

### 9.4. Sửa phần tử hoặc key mutable khi collection làm khóa

Giả sử danh sách `List<String>` được dùng như khóa của một `HashMap`. Nếu nội dung danh sách đổi, `List.hashCode` có thể đổi. Bảng băm bên ngoài không tự di chuyển entry đã lưu theo mã băm mới; lookup có thể thất bại.

Tương tự:

- Trong `HashSet<Person>`, sửa thuộc tính của `Person` dùng trong `equals/hashCode` có thể khiến `contains` không tìm thấy phần tử.
- Với `Map` được dùng làm khóa của map khác, sửa ánh xạ bên trong có thể khiến mã băm của khóa kiểu Map thay đổi.
- Trong `Map<Key, Value>`, sửa **key** khi đang lưu có rủi ro phá lookup của chính map đó; còn sửa value mutable có thể thay đổi mã băm của đối tượng Map **nếu giá trị ấy có hashCode thay đổi**, ngay cả khi map vẫn tra cứu được theo key cũ.

Ưu tiên dữ liệu bất biến khi dùng collection hoặc phần tử làm khóa băm; nếu bắt buộc thay đổi cấu trúc, cân nhắc tạo khóa mới.

### 9.5. Nhầm mảng Java với list giá trị

`int[]`, `String[]` là mảng Java, **không phải `List`**. Mảng không tự dùng công thức `List.hashCode`; so sánh và băm mảng theo nội dung phải dùng `Arrays.equals`/`Arrays.hashCode` (hoặc `deepEquals`/`deepHashCode` phù hợp cho mảng lồng nhau).

Ví dụ, `List.of(new int[]{1,2})` có phần tử là *một mảng*, và mã băm của phần tử ấy dựa trên `int[].hashCode()`, không mặc định dựa trên giá trị `[1,2]`. Nếu muốn bình đẳng theo nội dung, hãy mô hình dữ liệu phù hợp và định nghĩa `equals/hashCode` nhất quán.

### 9.6. Quên xử lý `null` và số nguyên tràn

Hợp đồng nêu `null` đóng góp mã băm `0`. Tuy nhiên việc **collection có cho phép `null` hay không** phụ thuộc lớp/factory cụ thể. `HashSet` và `HashMap` chấp nhận `null`, còn `Set.of` và `Map.of` thì không.

Khi mã băm lớn, phép cộng và nhân của `int` có thể tràn. Đó là hành vi có định nghĩa của phép toán số nguyên Java và là một phần ngữ nghĩa công thức này; **không đổi công thức sang `long` rồi ép kiểu theo một quy trình khác** nếu đang cài đặt hợp đồng giao diện.

### 9.7. Tối ưu hash mà bỏ qua contract

Một `hashCode` “phân bố đẹp” nhưng khác công thức giao diện vẫn sai khi triển khai `List`/`Set`/`Map`. Mục tiêu đầu tiên là đúng API; tối ưu bucket và hiệu năng nằm ở tầng cấu trúc dữ liệu hoặc lựa chọn kiểu phần tử.

## 10. Cách kiểm tra khi review code

Với lớp tự triển khai `List`, `Set` hay `Map`, có thể dùng các phép kiểm tra sau:

1. So sánh instance với một cách triển khai chuẩn tương đương: `ArrayList` ↔ `LinkedList`, `HashSet` ↔ `LinkedHashSet`, `HashMap` ↔ `LinkedHashMap`. Nếu `equals` là `true` thì mã băm phải giống nhau.
2. Tính tay theo công thức giao diện: List bắt đầu từ `1` và nhân `31` theo vị trí; Set cộng mỗi phần tử; Map cộng mỗi entry XOR key/value.
3. Thử trường hợp **rỗng** và **`null`** bằng các lớp cho phép `null`.
4. Thay đổi thứ tự **nhưng giữ cùng nội dung logic**: Set/Map phải giữ mã băm, List có thể đổi (đừng yêu cầu luôn đổi vì collision).
5. Kiểm tra dữ liệu phần tử/khóa/giá trị có tuân thủ `equals/hashCode` và có bị sửa trong khi collection làm khóa băm hay không.
6. Kiểm tra `hashCode` ổn định khi thông tin dùng cho `equals` không thay đổi; không dùng nguồn thời gian hoặc trạng thái bất định.

Khi cần mã nguồn chuẩn để tham khảo, xem tài liệu Oracle Java 17:

- [`List.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#hashCode()) — công thức nhân 31 theo thứ tự.
- [`Set.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#hashCode()) — tổng mã băm phần tử.
- [`Map.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#hashCode()) — tổng mã băm các entry.
- [`Map.Entry.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html#hashCode()) — XOR mã băm khóa và giá trị.

## 11. Ghi nhớ

- **`List`:** `h = 1`, sau đó theo từng vị trí `h = 31*h + elementHash`; list rỗng có băm `1`.
- **`Set`:** tổng `elementHash`, không xét thứ tự; set rỗng có băm `0`.
- **`Map`:** tổng `entryHash`, với `entryHash = keyHash ^ valueHash`, không xét thứ tự; map rỗng có băm `0`.
- Thành phần `null` đóng góp mã băm `0`, nhưng không phải collection nào cũng cho phép `null`.
- Hợp đồng bảo đảm **bằng nhau ⇒ cùng băm**, không bảo đảm **cùng băm ⇒ bằng nhau**.
- Khi cài đặt giao diện List/Set/Map, phải theo **công thức chính thức của giao diện**, đồng thời giữ phần tử và khóa ổn định khi chúng tham gia tra cứu bằng bảng băm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-005">Quan hệ bằng nhau có thể áp dụng giữa các lớp triển khai cùng hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ bằng nhau có thể áp dụng giữa các lớp triển khai cùng hợp đồng

## 1. Cùng một giao diện, khác lớp triển khai: có thể `equals()` trả về `true`?

Trong Java, `ArrayList` và `LinkedList` là hai lớp khác nhau nhưng đều triển khai giao diện `List`. Khi hai danh sách chứa **các phần tử bằng nhau ở cùng vị trí**, chúng được coi là bằng nhau theo hợp đồng của `List.equals(Object)`, cho dù kiểu triển khai và cấu trúc dữ liệu nội bộ khác nhau.

```java
List<String> a = new ArrayList<>(List.of("java", "core"));
List<String> b = new LinkedList<>(List.of("java", "core"));

System.out.println(a.equals(b)); // true
System.out.println(b.equals(a)); // true
```

Đây là fragment minh họa; cần import `java.util.List`, `ArrayList` và `LinkedList`. Ví dụ chạy hoàn chỉnh xuất hiện ở phần sau.

**Điểm cốt lõi:** bình đẳng của các collection tiêu chuẩn thường được định nghĩa bằng **hợp đồng của giao diện trừu tượng** (`List`, `Set`, `Map`) thay vì bắt buộc `a.getClass() == b.getClass()`. Ta có thể so sánh hai lớp triển khai khác nhau khi chúng cùng tuân thủ một quan hệ bằng nhau có ngữ nghĩa thống nhất.

Nhưng không được mở rộng phát biểu này thành “mọi `Collection` đều so sánh theo nội dung”: **`Collection` không áp đặt một thuật toán `equals()` chung cho mọi loại collection**. Sự tương thích xuyên lớp phụ thuộc hợp đồng cụ thể của kiểu đang so sánh và việc triển khai đúng hợp đồng đó.

## 2. Vì sao các lớp khác nhau vẫn có thể bằng nhau?

`Object.equals(Object)` chỉ quy định các tính chất của quan hệ bằng nhau (phản xạ, đối xứng, bắc cầu, nhất quán và không bằng `null`). Lớp tự quyết định tiêu chí bản sắc logic miễn tuân thủ các tính chất này.

Với các collection, nhiều tiêu chí được chuẩn hóa ở cấp giao diện:

| Loại | Điều kiện bằng nhau theo hợp đồng | Ví dụ khác triển khai có thể bằng nhau |
| --- | --- | --- |
| `List` | Đối tượng còn lại là `List`, cùng kích thước, từng vị trí tương ứng có phần tử bằng nhau | `ArrayList` và `LinkedList` |
| `Set` | Đối tượng còn lại là `Set`, cùng kích thước, chứa các phần tử tương đương theo ngữ nghĩa phần tử | `HashSet` và `LinkedHashSet` hoặc `TreeSet` với thứ tự phù hợp |
| `Map` | Đối tượng còn lại là `Map`, cùng tập ánh xạ khóa → giá trị; không phụ thuộc thứ tự duyệt | `HashMap`, `LinkedHashMap` và `TreeMap` với so sánh khóa phù hợp |

Khi các lớp của một loại cùng tuân thủ tiêu chí này, thuật toán nội bộ, thứ tự lưu trữ, độ phức tạp và khả năng đồng bộ có thể khác nhau mà kết quả `equals()` vẫn đúng.

Ví dụ, `ArrayList` lưu phần tử trong mảng động còn `LinkedList` là danh sách liên kết. Những khác biệt đó không xuất hiện trong định nghĩa `List.equals`; thứ được so sánh là **chuỗi giá trị theo vị trí**.

## 3. So sánh hai List khác implementation

Với hai `List`, điều kiện bằng nhau là **cùng kích thước và các phần tử ở từng vị trí tương ứng bằng nhau**. Vì vậy, `ArrayList` và `LinkedList` có thể bằng nhau theo cả hai chiều:

```java
List<String> array = new ArrayList<>(List.of("A", "B"));
List<String> linked = new LinkedList<>(List.of("A", "B"));
System.out.println(array.equals(linked)); // true
System.out.println(linked.equals(array)); // true
```

Đây là đoạn minh họa cần import các kiểu từ `java.util`. Đảo hai vị trí trong `linked` sẽ làm danh sách khác giá trị; thay lớp triển khai mà giữ nội dung theo thứ tự thì không. Hai list bằng nhau phải có hash giống nhau theo công thức của `List`, dù chi tiết lưu trữ nội bộ khác. Bài chuyên đề `List.equals()` phân tích các vị trí lặp, `null` và phần tử có `equals` riêng.

## 4. So sánh hai Set khác implementation

`HashSet` và `LinkedHashSet` có thể bằng nhau dù thứ tự chèn khác; `Set.equals()` yêu cầu hai bên là `Set`, có cùng kích thước và cùng các phần tử theo quan hệ thành viên tương thích:

```java
Set<String> hash = new HashSet<>(List.of("A", "B"));
Set<String> linked = new LinkedHashSet<>(List.of("B", "A"));
System.out.println(hash.equals(linked)); // true
System.out.println(linked.equals(hash)); // true
```

Ví dụ cần các import `java.util`. Cả hai có cùng hash theo hợp đồng tổng mã băm phần tử. `TreeSet` cũng có thể tham gia so sánh xuyên implementation khi `Comparator`/thứ tự tự nhiên nhận diện trùng phù hợp với `equals`; nếu comparator coi hai chuỗi khác nhau theo `equals` là một, tính đối xứng có thể bị phá (xem mục 7). Bài `Set.equals()` đã triển khai kỹ nguyên tắc thành viên và thứ tự duyệt.

## 5. So sánh hai Map khác implementation

`HashMap` và `LinkedHashMap` bằng nhau khi cùng các ánh xạ **khóa → giá trị**, cho dù thứ tự chèn hoặc duyệt không giống nhau:

```java
Map<String, Integer> hash = new HashMap<>();
hash.put("A", 1);
hash.put("B", 2);
Map<String, Integer> linked = new LinkedHashMap<>();
linked.put("B", 2);
linked.put("A", 1);
System.out.println(hash.equals(linked)); // true
```

Đây là fragment với các import `java.util` phù hợp. Khác giá trị của cùng một khóa, hoặc giá trị khác kiểu mà `equals` từ chối (ví dụ `Integer(1)` và `Long(1)`), thì hai map khác nhau. Map bằng nhau phải có hash giống nhau, do cùng tổng hash của các entry. `TreeMap` chỉ tương thích xuyên implementation khi comparator của khóa không làm lệch quan hệ bình đẳng; mục 7 và bài `Map.equals()` trình bày các ranh giới cụ thể.

## 6. Chương trình Java 17 chứng minh các tình huống

Lưu mã sau dưới tên `CollectionContractEqualityDemo.java`. Chương trình có các case `List`, `Set`, `Map`, một `Collection` không sử dụng so sánh nội dung chung, và ví dụ comparator `TreeSet` cố ý không nhất quán với `equals()`.

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CollectionContractEqualityDemo {
    public static void main(String[] args) {
        List<String> array = new ArrayList<>(List.of("java", "core"));
        List<String> linked = new LinkedList<>(List.of("java", "core"));
        List<String> swapped = new LinkedList<>(List.of("core", "java"));

        System.out.println("list cross implementation: "
                + array.equals(linked));
        System.out.println("list symmetric: " + linked.equals(array));
        System.out.println("list order matters: "
                + array.equals(swapped));
        System.out.println("list same hash: "
                + (array.hashCode() == linked.hashCode()));

        Set<String> hashSet = new HashSet<>(List.of("java", "core"));
        Set<String> linkedSet = new LinkedHashSet<>(
                List.of("core", "java"));
        Set<String> treeSet = new TreeSet<>(
                List.of("java", "core"));

        System.out.println("set cross implementation: "
                + hashSet.equals(treeSet));
        System.out.println("set symmetric: " + treeSet.equals(hashSet));
        System.out.println("set order ignored: "
                + hashSet.equals(linkedSet));
        System.out.println("set same hash: "
                + (hashSet.hashCode() == treeSet.hashCode()));

        Map<String, Integer> hashMap = new HashMap<>();
        hashMap.put("alice", 1);
        hashMap.put("bob", 2);

        Map<String, Integer> linkedMap = new LinkedHashMap<>();
        linkedMap.put("bob", 2);
        linkedMap.put("alice", 1);
        Map<String, Integer> treeMap = new TreeMap<>(linkedMap);

        System.out.println("map cross implementation: "
                + hashMap.equals(linkedMap));
        System.out.println("map symmetric: "
                + linkedMap.equals(hashMap));
        System.out.println("map TreeMap cross: "
                + hashMap.equals(treeMap));
        System.out.println("map same hash: "
                + (hashMap.hashCode() == linkedMap.hashCode()));

        Collection<String> deque = new ArrayDeque<>(
                List.of("java", "core"));
        System.out.println("list vs deque: " + array.equals(deque));
        System.out.println("deque vs list: " + deque.equals(array));
        System.out.println("Objects.equals(list, linked): "
                + Objects.equals(array, linked));

        // CỐ Ý SAI VỀ TÍNH TƯƠNG THÍCH CỦA COMPARATOR VỚI equals().
        Set<String> insensitive = new TreeSet<>(
                String.CASE_INSENSITIVE_ORDER);
        insensitive.add("java");
        Set<String> sensitive = new HashSet<>();
        sensitive.add("JAVA");

        System.out.println("bad comparator tree to hash: "
                + insensitive.equals(sensitive));
        System.out.println("bad comparator hash to tree: "
                + sensitive.equals(insensitive));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac CollectionContractEqualityDemo.java
java CollectionContractEqualityDemo
```

Kết quả stdout:

```text
list cross implementation: true
list symmetric: true
list order matters: false
list same hash: true
set cross implementation: true
set symmetric: true
set order ignored: true
set same hash: true
map cross implementation: true
map symmetric: true
map TreeMap cross: true
map same hash: true
list vs deque: false
deque vs list: false
Objects.equals(list, linked): true
bad comparator tree to hash: true
bad comparator hash to tree: false
```

### Đọc những điểm quan trọng trong kết quả

- `ArrayList` và `LinkedList` cùng hai chuỗi theo đúng thứ tự → bằng nhau **theo cả hai chiều**, hash giống nhau. Đảo thứ tự trong list → không bằng nhau.
- `HashSet`, `LinkedHashSet` và `TreeSet` dùng natural ordering của `String` cùng biểu diễn đúng tập chuỗi → bằng nhau bất chấp cách lưu trữ và thứ tự duyệt.
- `HashMap`, `LinkedHashMap` và `TreeMap` có cùng hai ánh xạ chuỗi → số nguyên → bằng nhau, hash giống nhau, dù thứ tự chèn/duyệt khác.
- `ArrayDeque` thuộc `Collection`, nhưng không triển khai `List.equals`; đối với hai đối tượng khác nhau trong ví dụ, `deque.equals(array)` vẫn là so sánh danh tính và trả `false`. `array.equals(deque)` cũng `false` vì `deque` không phải `List`.
- `Objects.equals(array, linked)` gọi `array.equals(linked)` nên vẫn `true`; nó chỉ cung cấp lớp bảo vệ null, không thay đổi hợp đồng nội dung.

Hai dòng “bad comparator” cố ý cho kết quả trái chiều để phơi bày một trường hợp không bảo đảm hợp đồng; **đừng dùng comparator không nhất quán với `equals()` trong set/map mà chương trình cần so sánh với các implementation khác**.

## 7. Khi nào cùng giao diện mà bình đẳng xuyên lớp có thể gặp vấn đề?

### a. Comparator không nhất quán với `equals()`

`TreeSet` có thể dùng `String.CASE_INSENSITIVE_ORDER` để coi `"java"` và `"JAVA"` là tương đương trong cấu trúc cây. Trong khi đó `HashSet` dựa trên `String.equals()`, phân biệt chữ hoa/chữ thường.

Trong chương trình:

- `insensitive` chỉ chứa `"java"`, và có thể tìm `"JAVA"` bằng comparator không phân biệt hoa/thường.
- `sensitive` chỉ chứa `"JAVA"`, và không coi `"java"` là cùng chuỗi theo `String.equals`.
- `insensitive.equals(sensitive)` có thể trả `true` do việc kiểm tra `containsAll` dựa trên comparator của `TreeSet`.
- `sensitive.equals(insensitive)` trả `false` do `HashSet.contains("java")` không tìm được chuỗi giống chính xác.

Đây là **sự không nhất quán của tiêu chí nhận diện phần tử**, dẫn đến vi phạm kỳ vọng đối xứng khi so sánh giữa các set. Không phải lỗi của `String.equals()`. Theo hợp đồng `SortedSet`, thứ tự nên **nhất quán với equals** nếu sorted set phải triển khai đúng hợp đồng `Set`; trường hợp khác là một cấu trúc theo comparator có ngữ nghĩa tập phần tử khác, và cần hiểu rõ giới hạn.

`TreeMap` có vấn đề tương tự về việc comparator có thể coi hai khóa bằng nhau (`compare(k1,k2) == 0`) dù `k1.equals(k2)` là `false`. Vì vậy sự tương thích `Map.equals` giữa `TreeMap` và `HashMap` cũng phụ thuộc chính sách bằng nhau của khóa. Ví dụ chuẩn của phần 6 sử dụng natural ordering của `String`, vốn nhất quán với `String.equals`.

### b. Một custom implementation không tuân theo hợp đồng

Giao diện không tự động cưỡng chế mọi implementation viết đúng `equals()` và `hashCode()`. Nếu tự xây một lớp `List` mà `equals()` chỉ so sánh `getClass()`, lớp đó có thể không đáp ứng hợp đồng `List.equals()` và làm hỏng tính đối xứng với `ArrayList` hoặc `LinkedList`.

Vì thế, khi viết collection tùy biến, đọc hợp đồng giao diện và kiểm thử cả **hai hướng** khi so sánh với các implementation chuẩn, thay vì chỉ dựa trên kết quả một lần gọi. Cần bảo đảm giá trị hash nhất quán với quan hệ bằng nhau.

### c. Phần tử bên trong có `equals()` bất thường

List, set và map dựa vào quan hệ bằng nhau của **phần tử, khóa và giá trị**. Nếu một class phần tử dùng epsilon không bắc cầu, phụ thuộc trạng thái thay đổi, so sánh lớp cha/con không đối xứng hoặc tạo hash trái hợp đồng, các collection sử dụng đối tượng đó cũng có thể thể hiện hành vi bất ngờ.

Ví dụ, `HashSet` không thể “sửa” một cặp phần tử có `equals()` sai. Trước khi so sánh collection, bảo đảm từng kiểu giá trị liên quan có quan hệ bằng nhau hợp lệ và hash tương ứng.

### d. Hai kiểu collection có bản chất khác nhau

Một `List` có thứ tự và có thể chứa phần tử lặp; một `Set` không cho phép hai phần tử trùng theo quan hệ bằng nhau. Dù chúng cùng xuất hiện dưới tham chiếu `Collection` hoặc cùng chứa những chuỗi giống nhau, **không có hợp đồng bắt buộc `List.equals(Set)` phải trả `true`**. Thực tế các lớp chuẩn không định nghĩa chúng là bằng nhau.

`Queue` hoặc `Deque` cũng không mặc nhiên thực hiện so sánh nội dung như `List`. Chẳng hạn `ArrayDeque` thừa hưởng `equals()` theo danh tính đối tượng. Thấy hai collection in ra các phần tử giống nhau hoặc có cùng thứ tự duyệt chưa đủ để kết luận bằng nhau.

## 8. Giá trị, sự thay đổi và cách sử dụng trong collection khác

### Vì sao quan hệ bằng nhau cần ổn định?

Một `List` chứa phần tử có trạng thái thay đổi có thể đổi kết quả `equals()` và `hashCode()` khi phần tử được sửa. Một `Set` hoặc `Map` chứa khóa có trường tham gia hash thay đổi sau khi chèn có thể không còn tìm được khóa bằng chính giá trị hiện tại theo cách mong đợi.

Với các đối tượng được dùng làm **phần tử của `HashSet` hoặc khóa của `HashMap`**, ưu tiên các lớp bất biến hoặc khóa bất biến. Ngay cả khi tập/map giữ đối tượng đó bằng tham chiếu, thay đổi giá trị liên quan `equals()`/`hashCode()` sau khi lưu có thể phá vỡ các phép `contains`/`get`/`remove`.

### Cẩn thận khi dùng collection làm key của map hoặc phần tử của set khác

Nếu một `List` được dùng làm khóa `HashMap`, việc thêm/xóa/sắp xếp phần tử trong list sẽ thay đổi `List.hashCode()`. Map có thể không tìm lại được khóa ở bucket cũ. Một list bất biến bằng `List.copyOf` hoặc `List.of` có thể phù hợp khi phần tử cũng ổn định, nhưng chỉ sự bất biến cấu trúc list **không đảm bảo bất biến sâu** của các đối tượng chứa trong list.

`Set.hashCode()` cũng phụ thuộc mã băm phần tử; `Map.hashCode()` phụ thuộc mã băm entry. Khi dùng tập/map làm khóa hoặc phần tử của một cấu trúc hash khác, cần bảo đảm nội dung tạo nên bản sắc không bị thay đổi trong vòng đời lưu trữ.

## 9. Kiểm thử hợp đồng khi so sánh xuyên implementation

Một bộ kiểm thử nhỏ nên bao gồm:

1. **Giữa cùng loại:** `ArrayList` ↔ `LinkedList` cùng phần tử/thứ tự, `HashSet` ↔ `TreeSet` với comparator hợp lệ, `HashMap` ↔ `LinkedHashMap` cùng ánh xạ.
2. **Hai chiều:** kiểm tra cả `a.equals(b)` lẫn `b.equals(a)` để phát hiện lỗi đối xứng.
3. **Bắc cầu:** nếu `a`, `b`, `c` dùng ba implementation nhưng cùng biểu diễn giá trị theo hợp đồng, `a.equals(b) && b.equals(c)` phải kéo theo `a.equals(c)`.
4. **Hash:** khi hai đối tượng bằng nhau, hash phải giống nhau, dù không yêu cầu hai đối tượng khác nhau có hash khác nhau.
5. **Phản ví dụ cố ý:** list đảo thứ tự; set thiếu một phần tử; map cùng khóa nhưng khác value; comparator không nhất quán với equals; phần tử thay đổi trạng thái.
6. **Null và implementation đặc biệt:** `HashMap` và `HashSet` thường cho phép `null`; `Map.of`/`Set.of` cấm `null`. Khi tạo dữ liệu test, chọn cách khởi tạo tương thích với chính collection.

Ví dụ mẫu (fragment giả sử đã có hai đối tượng không null `a`, `b` của cùng một hợp đồng bằng nhau):

```java
if (a.equals(b) != b.equals(a)) {
    throw new AssertionError("equals() is not symmetric");
}
if (a.equals(b) && a.hashCode() != b.hashCode()) {
    throw new AssertionError("equal objects have different hashes");
}
```

Hai điều kiện trên kiểm tra đối xứng và kết hợp hash trên **một cặp** đã chọn. Chúng không chứng minh mọi giá trị đều đúng; phải kết hợp kiểm thử biên, bộ ba và phân tích định nghĩa bằng nhau.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** `new ArrayList<>(List.of("A", "B")).equals(new LinkedList<>(List.of("A", "B")))` trả gì?  
**Đáp án:** `true`, do cả hai cùng tuân thủ hợp đồng `List.equals` theo thứ tự phần tử, không yêu cầu cùng lớp triển khai.

**Câu 2.** `new HashSet<>(List.of("A", "B")).equals(new LinkedHashSet<>(List.of("B", "A")))` trả gì?  
**Đáp án:** `true`; set không xét thứ tự chèn hay thứ tự duyệt.

**Câu 3.** `HashMap` và `LinkedHashMap` có cùng entry nhưng thứ tự chèn ngược nhau có bằng nhau không?  
**Đáp án:** Có, nếu khóa và giá trị tuân thủ ngữ nghĩa `equals()` đúng; `Map.equals` dựa trên ánh xạ, không xét thứ tự entry.

**Câu 4.** Vì sao `ArrayDeque` và `ArrayList` cùng chứa `"A", "B"` thường không bằng nhau?  
**Đáp án:** `ArrayDeque` không cam kết `List.equals`; bản thân `Collection` không áp đặt so sánh nội dung chung cho mọi implementation.

**Câu 5.** Có thể luôn coi `TreeSet` và `HashSet` chứa “cùng chữ cái” là bằng nhau không?  
**Đáp án:** Không. Nếu comparator của `TreeSet` xác định tương đương khác với `equals()` của phần tử, thậm chí có thể mất tính đối xứng trong so sánh xuyên implementation.

## Ghi nhớ

**Quan hệ bằng nhau theo giá trị có thể vượt qua ranh giới lớp triển khai khi các lớp cùng tuân thủ một hợp đồng ngữ nghĩa.** `ArrayList` và `LinkedList` so theo vị trí phần tử; các `Set` chuẩn so theo tập phần tử; các `Map` chuẩn so theo tập ánh xạ. Điều kiện thiết yếu là thuật toán và chính sách nhận diện phần tử/khóa phải thống nhất. `Collection` nói chung không có một quy tắc `equals()` nội dung áp dụng cho mọi kiểu, còn comparator hoặc phần tử không tuân thủ hợp đồng có thể làm hỏng tính đối xứng, tính bắc cầu và hành vi collection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-006">Tập hợp lồng nhau phụ thuộc equals() của từng phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Tập hợp lồng nhau phụ thuộc `equals()` của từng phần tử

Trong Java, hai collection có cùng **nội dung logic** hay không phụ thuộc trước hết vào quy tắc `equals(Object)` của loại collection ở **từng tầng lồng nhau**, sau đó đến `equals()` của các phần tử cuối cùng. Collection **không tự suy đoán** rằng hai đối tượng cùng một vài thuộc tính, hai mảng có cùng nội dung, hoặc hai danh sách giống nhau nhưng đảo thứ tự phải được coi là bằng nhau.

Ví dụ với kiểu `List<Set<Product>>`:

- Tầng ngoài là `List`: thứ tự **các tập hợp con** có ý nghĩa.
- Mỗi tầng trong là `Set`: thứ tự liệt kê **sản phẩm trong một tập hợp** không có ý nghĩa.
- Mỗi phần tử là `Product`: một cặp sản phẩm có bằng nhau hay không tùy cách lớp `Product` ghi đè `equals(Object)`.
- Nếu các tầng dùng hash (`HashSet` hoặc `HashMap`), cách tính `hashCode()` của phần tử cũng phải nhất quán với `equals()`.

**Ý chính:** `equals()` của collection không thay thế việc thiết kế đúng phép bằng nhau của kiểu phần tử; nó sử dụng phép bằng nhau đã được các kiểu bên trong cung cấp.

## 1. Quy tắc bằng nhau của các collection phổ biến

| Kiểu | Điều kiện hai collection bằng nhau | Thứ tự có ý nghĩa? | Điều quan trọng ở tầng con |
| --- | --- | --- | --- |
| `List<E>` | Cùng số phần tử và từng vị trí tương ứng bằng nhau | **Có** | So sánh phần tử theo `equals()` |
| `Set<E>` | Cùng số phần tử và mỗi phần tử của một set có phần tử bằng nó trong set kia | **Không** | `equals()` định nghĩa trùng phần tử; `hashCode()` phải tương thích với hash set |
| `Map<K,V>` | Cùng tập ánh xạ khóa → giá trị | **Không** phụ thuộc thứ tự chèn | Khóa dùng `equals()/hashCode()` phù hợp; giá trị được so sánh theo `equals()` |
| `Collection<E>` nói chung | Không có một quy tắc `equals()` chung cho mọi implementation | Tùy loại cụ thể | Không tự giả định mọi collection được so theo cùng tiêu chí |

Các hợp đồng của `List`, `Set` và `Map` được định nghĩa trên interface tương ứng; ví dụ một `ArrayList` và một `List.of(...)` có thể bằng nhau nếu thứ tự, kích thước và phần tử tương ứng bằng nhau. Ngược lại, một `List` không trở thành bằng một `Set` chỉ vì chứa các giá trị nhìn giống nhau. `Collection` chỉ là giao diện chung, không áp một phép so giá trị thống nhất lên mọi implementation.

### Ví dụ suy luận từng tầng

Giả sử:

~~~java
List<Set<String>> left = List.of(
    Set.of("A", "B"),
    Set.of("C")
);
List<Set<String>> right = List.of(
    Set.of("B", "A"),
    Set.of("C")
);
~~~

`left.equals(right)` là `true`: hai `List` có hai vị trí tương ứng, trong đó `Set.of("A", "B")` bằng `Set.of("B", "A")` vì set không tính thứ tự.

Nếu hoán đổi **vị trí hai set** của `right` thành `List.of(Set.of("C"), Set.of("A", "B"))`, kết quả sẽ là `false` vì list ngoài có thứ tự.

Nếu tầng ngoài là `Set<List<String>>`, thứ tự của các **list thành viên** không quan trọng, nhưng thứ tự các chuỗi **bên trong mỗi list** vẫn quan trọng:

~~~java
Set<List<String>> a = Set.of(List.of("A", "B"), List.of("C"));
Set<List<String>> b = Set.of(List.of("C"), List.of("A", "B"));
Set<List<String>> c = Set.of(List.of("C"), List.of("B", "A"));

// a.equals(b) == true; a.equals(c) == false.
~~~

Những kết luận trên xuất phát từ hợp đồng của từng tầng, chứ không phải từ việc Java sử dụng một cơ chế “so sánh sâu” riêng biệt.

## 2. Tầng cuối phụ thuộc vào `equals()` của đối tượng

Giả sử `Product` có hai trường:

- `sku`: mã sản phẩm duy nhất theo nghiệp vụ, cố định trong vòng đời đối tượng.
- `displayName`: tên trình bày; hai bản ghi cùng `sku` có thể dùng tên khác nhau nhưng vẫn biểu diễn **cùng sản phẩm**.

Nếu định nghĩa bằng nhau của sản phẩm là “cùng SKU” thì hai bản thể hiện `new Product("P-1", "Bút")` và `new Product("P-1", "Pen")` phải được `Product.equals()` coi là bằng nhau. Khi chúng xuất hiện trong hai list hoặc set tương ứng, collection cũng phải tính chúng như những phần tử bằng nhau.

Đây là cách cài đặt **một quy tắc nghiệp vụ cụ thể**, không phải mặc định cho mọi lớp: với kiểu giá trị khác, tên hoặc những thuộc tính khác có thể là một phần của định nghĩa bằng nhau. Nếu `Product` **không ghi đè `equals()`**, nó sẽ thừa kế `Object.equals()`, vốn so đồng nhất tham chiếu; hai instance được tạo bằng hai lệnh `new` khác nhau thường không bằng nhau dù trường `sku` giống hệt.

Một lớp tự định nghĩa `equals()` cần đồng thời ghi đè `hashCode()`: nếu `a.equals(b)` đúng, `a.hashCode() == b.hashCode()` phải đúng. Điều này đặc biệt quan trọng vì tầng `Set` hoặc khóa `Map` có thể dùng hash ngay cả khi tầng ngoài là `List`.

## 3. Ví dụ Java 17 chạy được: `List<Set<T>>`, `Set<List<T>>` và `Map<String,List<T>>`

Tạo tệp **`NestedCollectionEqualityDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NestedCollectionEqualityDemo {

    static final class Product {
        private final String sku;
        private final String displayName;

        Product(String sku, String displayName) {
            this.sku = Objects.requireNonNull(sku, "sku");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Product other)) {
                return false;
            }
            return sku.equals(other.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    public static void main(String[] args) {
        List<Set<Product>> groupsA = List.of(
            Set.of(new Product("A", "Pen"), new Product("B", "Pencil")),
            Set.of(new Product("C", "Book"))
        );

        List<Set<Product>> groupsB = List.of(
            Set.of(new Product("B", "New name"), new Product("A", "Other name")),
            Set.of(new Product("C", "Renamed"))
        );

        List<Set<Product>> reversedGroups = List.of(
            Set.of(new Product("C", "Book")),
            Set.of(new Product("A", "Pen"), new Product("B", "Pencil"))
        );

        System.out.println("list of sets equal: "
            + groupsA.equals(groupsB));
        System.out.println("list of sets same hash: "
            + (groupsA.hashCode() == groupsB.hashCode()));
        System.out.println("outer list order matters: "
            + groupsA.equals(reversedGroups));

        Set<List<Product>> listsA = Set.of(
            List.of(new Product("A", "Pen"), new Product("B", "Pencil")),
            List.of(new Product("C", "Book"))
        );
        Set<List<Product>> listsB = Set.of(
            List.of(new Product("C", "New")),
            List.of(new Product("A", "Different"), new Product("B", "Different"))
        );
        Set<List<Product>> changedInnerOrder = Set.of(
            List.of(new Product("C", "Book")),
            List.of(new Product("B", "Pencil"), new Product("A", "Pen"))
        );

        System.out.println("set of lists equal: "
            + listsA.equals(listsB));
        System.out.println("inner list order matters: "
            + listsA.equals(changedInnerOrder));

        Map<String, List<Product>> mapA = new LinkedHashMap<>();
        mapA.put("front", List.of(
            new Product("A", "Pen"), new Product("B", "Pencil")));
        mapA.put("back", List.of(new Product("C", "Book")));

        Map<String, List<Product>> mapB = new LinkedHashMap<>();
        mapB.put("back", List.of(new Product("C", "Renamed")));
        mapB.put("front", List.of(
            new Product("A", "Another name"),
            new Product("B", "Another name")));

        Map<String, List<Product>> mapC = new LinkedHashMap<>(mapB);
        mapC.put("front", List.of(
            new Product("B", "Pencil"), new Product("A", "Pen")));

        System.out.println("map insertion order irrelevant: "
            + mapA.equals(mapB));
        System.out.println("map value list order matters: "
            + mapA.equals(mapC));

        List<int[]> arraysA = List.of(new int[] {1, 2});
        List<int[]> arraysB = List.of(new int[] {1, 2});
        System.out.println("array elements equal by content: "
            + arraysA.equals(arraysB));
        System.out.println("nested value lists equal: "
            + List.of(List.of(1, 2)).equals(List.of(List.of(1, 2))));

        List<String> inner = new ArrayList<>(List.of("A"));
        List<List<String>> outer = List.of(inner);
        Set<List<List<String>>> indexed = new HashSet<>();
        indexed.add(outer);
        int before = outer.hashCode();
        inner.add("B");
        System.out.println("nested hash changed after mutation: "
            + (before != outer.hashCode()));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac NestedCollectionEqualityDemo.java
java NestedCollectionEqualityDemo
~~~

**Đầu ra chuẩn:**

~~~text
list of sets equal: true
list of sets same hash: true
outer list order matters: false
set of lists equal: true
inner list order matters: false
map insertion order irrelevant: true
map value list order matters: false
array elements equal by content: false
nested value lists equal: true
nested hash changed after mutation: true
~~~

Giải thích từng nhóm kết quả:

1. Hai `groupsA` và `groupsB` bằng nhau vì từng set ở cùng vị trí chứa các SKU tương đương; thứ tự khai báo bên trong set và `displayName` khác nhau không làm thay đổi kết quả.
2. Khi toàn bộ hai list bằng nhau theo hợp đồng, hash của chúng cũng phải bằng nhau. `List.hashCode()` dựa vào hash của các phần tử, trong ví dụ là các `Set<Product>`.
3. `reversedGroups` đổi thứ tự **hai set trong list ngoài** nên không bằng `groupsA`.
4. `listsA` và `listsB` đều là set chứa hai list bằng nhau; đảo thứ tự liệt kê **hai list trong set ngoài** không ảnh hưởng. Nhưng đổi vị trí `A` và `B` **bên trong một list** làm kết quả thành `false`.
5. Hai `LinkedHashMap` có các entry cùng khóa và các list giá trị tương đương nên bằng nhau, dù được chèn theo thứ tự khác nhau. `mapC` khác `mapA` vì giá trị list của khóa `"front"` đã đảo thứ tự.
6. Hai `int[]` chứa `1, 2` là hai đối tượng mảng khác nhau. Mảng Java không ghi đè `Object.equals()` theo nội dung; `List<int[]>` gọi `equals()` của mảng và cho `false`. Thay mỗi mảng bằng `List<Integer>` cho kết quả bằng nhau theo từng số nguyên.
7. `outer` là một list **không cho sửa cấu trúc trực tiếp**, nhưng nó chứa tham chiếu tới `inner` có thể thay đổi. Khi thêm `"B"` vào `inner`, hash tính qua các tầng của `outer` thay đổi. Việc chèn `outer` vào `HashSet` trước đó tạo ra tình huống nguy hiểm: đường tra cứu theo hash hiện tại có thể không khớp vị trí đã chèn. Ví dụ chỉ in việc hash thay đổi, **không dựa vào một kết quả `contains()` sau khi cấu trúc đã bị làm mất ổn định**.

`displayName` trong `Product` được giữ để làm rõ rằng việc một trường **không thuộc định nghĩa bằng nhau** khác nhau giữa hai instance không làm thay đổi equality của collection.

## 4. `equals()` lan qua các tầng thế nào?

Có thể đọc `List<Set<Product>>` theo sơ đồ:

~~~text
List.equals(otherList)
  ├─ kiểm tra kích thước và vị trí thứ nhất
  │    └─ Set.equals(otherSet)
  │         └─ tìm phần tử tương đương
  │              └─ Product.equals(otherProduct)
  └─ kiểm tra vị trí thứ hai
       └─ Set.equals(otherSet)
            └─ Product.equals(otherProduct)
~~~

Mỗi lớp gọi phương thức bình đẳng theo **hợp đồng của kiểu đối tượng tại tầng đó**. Với `List<List<Product>>`, cả hai tầng list yêu cầu thứ tự. Với `Set<Set<Product>>`, cả hai tầng set bỏ qua thứ tự của các phần tử. Với `Map<Key,List<Set<Product>>>`, map bỏ qua thứ tự entry; value là list nên giữ thứ tự các set; từng set lại không xét thứ tự sản phẩm bên trong.

Điều này dẫn đến hai hệ quả quan trọng:

- **Kiểu chứa khác thì ngữ nghĩa khác:** thay tầng ngoài từ `List` sang `Set` có thể làm mất phân biệt thứ tự, thay một tầng từ `Set` sang `List` có thể khiến thứ tự trở thành một phần của bằng nhau.
- **Kiểu phần tử khác thì ngữ nghĩa khác:** thay `Product` có equality theo SKU bằng một lớp không override `equals()` khiến hai bản thể hiện cùng SKU có thể không còn bằng nhau; đổi cách triển khai `Product.equals()` cũng ảnh hưởng toàn bộ cấu trúc chứa nó.

Không phải mọi đối tượng lồng nhau đều là collection. Nếu phần tử là `LocalDate`, `String`, `BigDecimal`, mảng, một record hay lớp miền, tầng ngoài sử dụng đúng `equals()` do kiểu phần tử đó định nghĩa. Chẳng hạn `BigDecimal("1.0")` và `BigDecimal("1.00")` không bằng nhau theo `BigDecimal.equals()` dù có thể so giá trị số học tương đương bằng `compareTo() == 0`; đặt chúng vào hai list tương ứng **không tự chuyển** sang phép so số học. Hãy chọn và chuẩn hóa kiểu giá trị theo yêu cầu nghiệp vụ trước khi dùng làm phần tử.

## 5. `hashCode()` của cấu trúc lồng nhau phải nhất quán

`List`, `Set` và `Map` đều có quy tắc mã băm gắn với hợp đồng bằng nhau của chúng:

- `List.hashCode()` kết hợp mã băm từng phần tử **theo thứ tự**.
- `Set.hashCode()` lấy tổng mã băm của các phần tử; về bản chất **không phụ thuộc thứ tự**.
- `Map.hashCode()` lấy tổng mã băm của các entry; mỗi entry kết hợp hash của key và value, **không phụ thuộc thứ tự entry**.

Vì thế, một thay đổi ở `hashCode()` của phần tử trong cùng có thể lan ra hash của set, rồi tới list, rồi tới object khác đang chứa list đó.

Giả sử `Product.equals()` chỉ so `sku` nhưng `Product.hashCode()` lại đưa thêm `displayName` vào phép băm. Khi đó, hai `Product` cùng SKU khác tên có thể **bằng nhau nhưng hash khác nhau**. Một `Set<Product>` có thể tra cứu, kiểm tra phần tử trùng và so bằng nhau không còn đúng hợp đồng; hai `List<Set<Product>>` tưởng như cùng giá trị cũng có thể nhận hash khác nhau. Vấn đề bắt nguồn từ `Product` và lan lên collection lồng ngoài.

Cần nhớ điều kiện **một chiều**:

~~~text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
~~~

Chiều ngược lại không bắt buộc: hai phần tử khác nhau được phép trùng hash. Không được dùng `hashCode() == hashCode()` làm bằng chứng rằng hai object hoặc hai collection bằng nhau. Muốn so bằng nhau phải gọi `equals()`.

Với `Map<K,V>`, hash và equality không chỉ phụ thuộc vào key mà còn phụ thuộc vào **value**. Nếu value là collection có thể bị sửa, map nhìn tổng thể có thể đổi `equals()/hashCode()` theo value ngay cả khi các key không đổi. Đây là rủi ro đặc biệt khi chính map (hoặc một collection chứa map) được dùng làm khóa/phần tử trong một cấu trúc hash khác.

## 6. Mảng và null ở tầng phần tử cuối

Một tầng `List` không tự so sâu phần tử `int[]`:

~~~java
List<int[]> a = List.of(new int[] {1, 2});
List<int[]> b = List.of(new int[] {1, 2});
// a.equals(b) == false: hai phần tử là hai mảng riêng.
~~~

Để coi mảng là giá trị, phải chọn cách so sánh theo nội dung (`Arrays.equals`/`Arrays.deepEquals` phù hợp) và một kiểu bọc có `equals/hashCode` tương ứng nếu dùng làm phần tử hoặc khóa băm. Bài về **phần tử mảng trong Collection** có chương trình và kỹ thuật sao chép phòng vệ đầy đủ.

`null` được một số kiểu như `ArrayList`, `HashSet`, `HashMap` chấp nhận và xử lý theo hợp đồng bằng nhau của từng tầng; `List.of`, `Set.of`, `Map.of` từ chối phần tử/khóa/giá trị `null`. Không dùng thứ tự duyệt của `Set` để suy đoán bình đẳng của collection lồng nhau.

## 7. Phần tử mutable có thể đổi cả hash của cấu trúc ngoài

Một collection bên ngoài không cho sửa cấu trúc vẫn có thể giữ tham chiếu đến một collection con sửa được:

~~~java
List<String> inner = new ArrayList<>(List.of("A"));
List<List<String>> outer = List.of(inner);
inner.add("B"); // outer vẫn có một phần tử, nhưng giá trị phần tử đã đổi.
~~~

Nếu `outer` đang là phần tử của `HashSet`, `outer.hashCode()` có thể đổi sau khi chèn, khiến `contains` hoặc `remove` không còn tìm đúng dữ liệu. Với các phần tử `String` bất biến, `List.copyOf(inner)` trước khi lồng giúp tách **cấu trúc** của list con khỏi `inner`; với đối tượng mutable, cần sao chép hoặc chuyển đổi trạng thái phần tử riêng.

Chọn dữ liệu tham gia `equals/hashCode` ổn định trong suốt thời gian làm khóa hoặc phần tử của cấu trúc băm. Bài cuối chương phân biệt kỹ `Collections.unmodifiableXxx`, bản sao nông và bất biến sâu.

## 8. Những lỗi nhận thức thường gặp

| Nhận định dễ nhầm | Điều đúng cần nhớ |
| --- | --- |
| “Hai collection có cùng số phần tử và cùng dữ liệu nhìn thấy thì bằng nhau.” | Còn phụ thuộc loại collection, thứ tự và `equals()` từng phần tử |
| “Set bỏ qua thứ tự nên Set<List<T>> cũng bỏ qua thứ tự trong mỗi list.” | Sai: set ngoài bỏ qua thứ tự các list, **list con vẫn có thứ tự** |
| “Map được chèn theo thứ tự khác sẽ không bằng nhau.” | `Map.equals()` không dựa vào thứ tự chèn; so ánh xạ khóa–giá trị |
| “Các Product có cùng SKU đương nhiên bằng nhau.” | Phải cài đặt `Product.equals()` theo SKU nếu đó là hợp đồng nghiệp vụ |
| “Cứ dùng `equals()`, không cần quan tâm `hashCode()`.” | Sai với hash collections và hash của collection lồng nhau |
| “Mảng có cùng nội dung nên bằng nhau trong List.” | Mảng dùng identity equality nếu không có lớp bọc hoặc chuyển đổi |
| “`List.of(...)` làm mọi thứ bên trong bất biến.” | Chỉ cấu trúc list ngoài không cho sửa; phần tử mutable vẫn có thể đổi |
| “Hai hash giống nhau nghĩa là hai collection bằng nhau.” | Hash trùng có thể do collision; phải kiểm tra `equals()` |

## 9. Checklist thiết kế và kiểm thử

Khi xử lý một kiểu như `List<Set<T>>`, `Set<List<T>>` hoặc `Map<K,List<V>>`:

1. Vẽ ra **từng tầng** và ghi rõ tầng nào giữ thứ tự (list), tầng nào không (set/map).
2. Xác định `equals()` và `hashCode()` của kiểu phần tử/khóa cuối cùng; nếu dùng class tự viết, kiểm tra bằng nhau có đúng nhu cầu nghiệp vụ.
3. Tạo **hai cấu trúc riêng biệt** với cùng dữ liệu logic, không chỉ so object với chính nó.
4. Đổi thứ tự ở tầng `Set` và xác nhận equality không đổi; đổi thứ tự ở tầng `List` và xác nhận equality đổi **khi vị trí các phần tử thực sự khác nhau**.
5. Cho hai object phần tử cùng khóa bằng nhau nhưng khác trường hiển thị; xác nhận collection vẫn bằng nhau và hash cũng bằng nhau.
6. Kiểm tra mảng, `null`, kiểu số có quy tắc `equals()` đặc thù, và các tình huống phần tử mutable theo nhu cầu.
7. Với cấu trúc được lưu làm key trong hash collection, bảo đảm không thay đổi ngầm bất kỳ giá trị lồng sâu nào ảnh hưởng equality hoặc hash.

**Ghi nhớ:** Bằng nhau của collection lồng nhau là kết quả của **nhiều hợp đồng `equals()` được áp dụng nối tiếp**. `List` so theo vị trí, `Set` so theo thành viên, `Map` so theo entry; mỗi tầng sử dụng equality của những object nó chứa. Thiết kế đúng `equals()/hashCode()` của phần tử và giữ giá trị khóa ổn định là điều kiện để cả cấu trúc lồng nhau hoạt động đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-007">Phần tử mảng trong Collection vẫn dùng quan hệ bằng nhau của mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Phần tử mảng trong Collection vẫn dùng quan hệ bằng nhau của mảng

## 1. Điều gì xảy ra khi đưa mảng vào Collection?

Trong Java, `List`, `Set` và `Map` thường sử dụng các phương thức `equals()` hoặc `hashCode()` của đối tượng phần tử hay khóa để quyết định hai giá trị có tương đương hay không. **Collection không tự đổi quy tắc so sánh của phần tử mảng thành so sánh từng phần tử bên trong mảng.**

Xét hai mảng độc lập:

```java
int[] a = {1, 2, 3};
int[] b = {1, 2, 3};

System.out.println(a == b);             // false
System.out.println(a.equals(b));        // false
System.out.println(Arrays.equals(a, b)); // true
```

*Đây là đoạn lệnh minh họa đặt bên trong phương thức có `import java.util.Arrays;`. Chương trình đầy đủ ở mục 6.*

Mặc dù `a` và `b` chứa các số nguyên giống nhau theo cùng thứ tự, chúng là **hai đối tượng mảng khác nhau**. Mảng Java **không ghi đè `Object.equals()` để so nội dung**. Do đó:

- `a == b` là `false` vì khác tham chiếu.
- `a.equals(b)` cũng `false` vì kế thừa so sánh tham chiếu từ `Object`.
- `Arrays.equals(a, b)` là `true` vì phương thức tiện ích này **chủ động so từng phần tử tương ứng**.

Đây chính là điểm dễ nhầm: khi mảng trở thành phần tử của `Collection`, nó **vẫn là một object mảng với equality mặc định của mảng**, chứ không được Collection biến thành value object. Cùng dữ liệu không đồng nghĩa cùng phần tử theo `equals()` của mảng.

> **Quy tắc nhớ nhanh:** Collection dùng hợp đồng equality của phần tử. Phần tử là mảng Java thì equality mặc định là theo identity của mảng; muốn so nội dung phải chọn một cách biểu diễn hoặc thuật toán riêng.

## 2. Sự khác nhau giữa mảng và Collection có phần tử thông thường

Hai `List<Integer>` có các phần tử số giống nhau theo cùng thứ tự thường `equals()` nhau vì `Integer.equals()` so giá trị số:

```java
List<Integer> numbers1 = List.of(1, 2, 3);
List<Integer> numbers2 = List.of(1, 2, 3);

System.out.println(numbers1.equals(numbers2)); // true
```

Nhưng với **List chứa phần tử mảng**:

```java
int[] a = {1, 2, 3};
int[] b = {1, 2, 3};

List<int[]> list1 = List.of(a);
List<int[]> list2 = List.of(b);

System.out.println(list1.equals(list2)); // false
System.out.println(list1.contains(b));   // false
System.out.println(list1.contains(a));   // true
```

*Các đoạn code trên minh họa trong một phương thức với `import java.util.List;`.*

Lý do: hợp đồng `List.equals()` yêu cầu hai list có cùng số phần tử, và **cặp phần tử ở mỗi vị trí phải bằng nhau**. Khi phần tử là `int[]`, lời so sánh phần tử dùng `equals()` của mảng. `list1` có `a` còn `list2` có `b`, nên khác nhau dù `Arrays.equals(a, b)` là `true`.

`List.contains(o)` cũng sử dụng quan hệ equality để tìm phần tử. Một object mảng mới có cùng nội dung không tự được nhận diện là phần tử cũ. Ngược lại, cùng tham chiếu `a` thì được nhận diện.

| Biểu thức | Ý nghĩa | Với hai mảng `a` và `b` độc lập nhưng cùng nội dung |
| --- | --- | --- |
| `a == b` | Cùng tham chiếu? | `false` |
| `a.equals(b)` | Equality mặc định của mảng? | `false` |
| `Arrays.equals(a, b)` | Nội dung một chiều tương ứng bằng nhau? | `true` |
| `List.of(a).equals(List.of(b))` | List có phần tử bằng nhau theo `equals()`? | `false` |
| `List.of(a).contains(b)` | Có phần tử bằng `b` theo equality mặc định? | `false` |
| `List.of(a).contains(a)` | Có cùng đối tượng mảng `a`? | `true` |

Nên phân biệt **mảng là phần tử của Collection** với **dữ liệu đã được chuyển thành các phần tử giá trị của Collection**:

- `List<int[]>` chứa mảng như một đối tượng phần tử.
- `List<Integer>` chứa từng `Integer`; equality của mỗi `Integer` là theo giá trị.
- `List<List<Integer>>` so các list con theo `List.equals()`, nếu các phần tử bên trong có equality phù hợp.

Một Collection không có khái niệm mặc định “bóc mảng ở bất kỳ độ sâu nào rồi so các giá trị”.

## 3. Tác động lên List, Set và Map

### 3.1. List: equals, contains, indexOf, remove

Một `ArrayList<int[]>` hoặc `List<int[]>` sử dụng equality của mảng khi kiểm tra `contains()` hay tìm chỉ số bằng `indexOf()`. Nếu gọi `remove(Object)` bằng một mảng mới cùng nội dung, nó cũng không xóa mảng đã lưu, vì không tìm thấy phần tử bằng nhau theo quy tắc của mảng.

```java
List<int[]> rows = new ArrayList<>();
int[] oldRow = {10, 20};
rows.add(oldRow);

System.out.println(rows.contains(new int[]{10, 20})); // false
System.out.println(rows.indexOf(oldRow));               // 0
System.out.println(rows.remove(new int[]{10, 20}));     // false
```

*Đây là đoạn minh họa nằm trong phương thức với `import java.util.ArrayList;` và `import java.util.List;`. Phương thức `remove(Object)` của `List` trả về `boolean`, khác `remove(int index)`.*

### 3.2. HashSet: không tự loại mảng trùng nội dung

`HashSet` dựa trên `hashCode()/equals()` của phần tử. Các đối tượng mảng có `hashCode()` và `equals()` kế thừa từ `Object`, nên hai mảng khác tham chiếu sẽ được xem là **hai phần tử khác nhau**, kể cả khi mọi phần tử con bằng nhau.

```java
Set<int[]> set = new HashSet<>();
set.add(new int[]{1, 2});
set.add(new int[]{1, 2});

System.out.println(set.size()); // 2
```

Không được suy từ `Arrays.equals(first, second)` là `true` rằng `HashSet<int[]>` sẽ coi chúng là trùng nhau. Việc hai đối tượng có giá trị hash tình cờ giống nhau cũng không khiến chúng tự thành một phần tử: `HashSet` vẫn cần kiểm tra equality. Ngược lại, nếu thêm **chính instance mảng** hai lần, `HashSet` chỉ giữ một phần tử.

### 3.3. HashMap: mảng dùng làm khóa vẫn được so theo identity

Với `HashMap<int[], String>`, mảng khóa mới có cùng nội dung thường không tra được mapping được lưu bằng mảng khác:

```java
Map<int[], String> map = new HashMap<>();
int[] key = {1, 2};

map.put(key, "stored");

System.out.println(map.get(new int[]{1, 2})); // null
System.out.println(map.get(key));              // stored
```

*Đoạn minh họa dùng `java.util.Map` và `java.util.HashMap`.*

Điều này vẫn đúng về mặt hợp đồng `HashMap`: khóa mới không bằng khóa cũ theo `equals()` của mảng. Muốn map sử dụng **giá trị của mảng làm định danh**, cần gói dữ liệu mảng trong một kiểu khóa định nghĩa `equals()/hashCode()` theo nội dung, hoặc chuyển mảng thành một dạng key khác có semantics giá trị rõ ràng.

Còn nếu mảng là **giá trị** thay vì khóa, ví dụ `Map<String, int[]>`, việc `get("id")` được hay không dựa vào `String` làm khóa. Nhưng so sánh hai `Map<String, int[]>` bằng `Map.equals()` vẫn xét `equals()` của các **giá trị** tương ứng; hai mảng value khác tham chiếu nhưng cùng nội dung sẽ không tự được xem là bằng nhau.

## 4. Những phương thức Arrays nào thực sự so nội dung?

`java.util.Arrays` cung cấp các phép so sánh rõ ràng dành cho mảng:

| Phương thức | Tình huống áp dụng | Điểm cần nhớ |
| --- | --- | --- |
| `Arrays.equals(int[], int[])` và các overload primitive | So hai mảng nguyên thủy một chiều | So các phần tử cùng vị trí bằng quy tắc tương ứng với kiểu dữ liệu. |
| `Arrays.equals(Object[], Object[])` | So mảng đối tượng một chiều | So từng phần tử bằng equality tương ứng; phần tử **lại là mảng** thì mặc định vẫn so identity của mảng con. |
| `Arrays.deepEquals(Object[], Object[])` | So mảng đối tượng có cấu trúc lồng, gồm mảng con | Khi gặp mảng con, đi sâu theo nội dung; phù hợp với nhiều tình huống mảng đa chiều. |
| `Arrays.hashCode(array)` | Tính hash theo nội dung một chiều | Phải đi cùng `Arrays.equals(...)` thích hợp khi làm kiểu khóa giá trị. |
| `Arrays.deepHashCode(Object[])` | Hash theo nội dung mảng lồng | Đi cặp với `Arrays.deepEquals(...)` khi chọn chính sách equality sâu. |
| `Arrays.toString(array)` | In mảng một chiều dễ đọc | Chỉ là biểu diễn; không định nghĩa equality. |
| `Arrays.deepToString(Object[])` | In mảng lồng dễ đọc | Cũng chỉ dùng cho biểu diễn, không làm thay đổi equality. |

Ví dụ mảng hai chiều trong Java thực chất là **mảng các mảng**, nên:

```java
int[][] a = {{1, 2}, {3}};
int[][] b = {{1, 2}, {3}};

System.out.println(Arrays.equals(a, b));     // false
System.out.println(Arrays.deepEquals(a, b)); // true
```

Ở đây `Arrays.equals(a, b)` được chọn theo overload `Object[]` vì `int[][]` là mảng gồm các phần tử `int[]`. Hai phần tử `int[]` độc lập không bằng nhau theo `Object.equals()`. `Arrays.deepEquals(a, b)` nhận ra các mảng con và so nội dung nguyên thủy tương ứng.

Lưu ý sự khác nhau giữa các kiểu:

- `Arrays.equals(new int[]{1,2}, new int[]{1,2})` → `true`.
- `Arrays.equals(new int[][]{{1}}, new int[][]{{1}})` → `false` vì so hai mảng con theo tham chiếu.
- `Arrays.deepEquals(new int[][]{{1}}, new int[][]{{1}})` → `true`.

Nếu chỉ cần xử lý mảng nguyên thủy một chiều, `Arrays.equals()` là lựa chọn trực tiếp và dễ hiểu. Với mảng lồng, dùng `deepEquals` khi ngữ nghĩa nghiệp vụ thật sự cần so đến nội dung các cấp. Các phương thức `Arrays.equals` không biến mảng thành object có `equals()` theo nội dung cho những nơi khác sử dụng.

## 5. Vì sao việc bọc bằng List.of, Arrays.asList hay List.copyOf chưa đủ?

Một sai lầm thường gặp là cho rằng chỉ cần đặt mảng vào một list bất biến thì equality của mảng sẽ trở thành equality theo nội dung. Trên thực tế:

```java
int[] a = {1, 2};
int[] b = {1, 2};

List<int[]> first = List.of(a);
List<int[]> second = List.of(b);

System.out.println(first.equals(second)); // false
```

`List.of(...)` tạo list không sửa được cấu trúc, **không sao chép sâu phần tử mảng** và không ghi đè phương thức của `int[]`. Nếu đổi `a[0]`, nội dung array đang chứa trong `first` cũng đổi. Tương tự, `List.copyOf(...)` tạo list không sửa được cấu trúc với các phần tử của nguồn, nhưng vẫn có thể chứa các object mutable.

`Arrays.asList(...)` cũng dễ gây nhầm lẫn với mảng primitive. Đặc biệt:

```java
List<int[]> oneElement = Arrays.asList(new int[]{1, 2, 3});
System.out.println(oneElement.size()); // 1
```

Đây là một list **có một phần tử kiểu `int[]`**, không phải `List<Integer>` có ba phần tử. Primitive `int[]` không tự tách và boxing thành ba `Integer` cho lời gọi varargs này.

Đối với mảng tham chiếu `String[]`, `Arrays.asList(stringArray)` thường tạo danh sách các phần tử `String` vì `String[]` khớp trực tiếp tham số varargs `T...`. Hai trường hợp rất khác nhau nên cần kiểm tra kiểu generic và ý nghĩa chính xác của dữ liệu.

Nếu muốn một `List<Integer>` từ `int[]`, có thể chủ động boxing:

```java
int[] source = {1, 2, 3};
List<Integer> values = Arrays.stream(source)
        .boxed()
        .toList();

System.out.println(values.equals(List.of(1, 2, 3))); // true
```

Trong Java 17+, `Stream.toList()` tạo list không sửa được cấu trúc. Các phần tử lúc này là `Integer`, nên equality của phần tử xét giá trị. Cách chuyển đổi này thay đổi mô hình dữ liệu và có chi phí boxing; chỉ nên dùng khi thật sự cần list các số nguyên theo giá trị.

## 6. Chương trình Java 17+ hoàn chỉnh, có thể tự chạy

Chương trình minh họa một chuỗi hành vi cụ thể: array identity, `List.contains()`, `HashSet`, `HashMap`, so mảng lồng, sự khác biệt của `Arrays.asList` và một **wrapper so nội dung**.

Tạo tệp `ArrayCollectionEqualityDemo.java`:

```java
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ArrayCollectionEqualityDemo {

    // Value wrapper: bảo vệ snapshot và định nghĩa equality theo nội dung.
    record IntArrayKey(int[] values) {
        IntArrayKey {
            Objects.requireNonNull(values, "values");
            values = Arrays.copyOf(values, values.length);
        }

        @Override
        public int[] values() {
            return Arrays.copyOf(values, values.length);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IntArrayKey that
                    && Arrays.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};

        System.out.println(a == b);
        System.out.println(a.equals(b));
        System.out.println(Arrays.equals(a, b));

        List<int[]> listA = List.of(a);
        List<int[]> listB = List.of(b);
        System.out.println(listA.equals(listB));
        System.out.println(listA.contains(b));
        System.out.println(listA.contains(a));

        Set<int[]> rawSet = new HashSet<>();
        rawSet.add(a);
        rawSet.add(b);
        System.out.println(rawSet.size());
        System.out.println(rawSet.contains(new int[]{1, 2, 3}));

        Map<int[], String> rawMap = new HashMap<>();
        rawMap.put(a, "stored");
        System.out.println(rawMap.get(b));
        System.out.println(rawMap.get(a));

        int[][] nested1 = {{1, 2}, {3}};
        int[][] nested2 = {{1, 2}, {3}};
        System.out.println(Arrays.equals(nested1, nested2));
        System.out.println(Arrays.deepEquals(nested1, nested2));

        System.out.println(
                List.of(1, 2, 3).equals(List.of(1, 2, 3)));
        System.out.println(Arrays.asList(new int[]{1, 2, 3}).size());

        List<IntArrayKey> valueList = List.of(new IntArrayKey(a));
        System.out.println(valueList.contains(new IntArrayKey(b)));

        Set<IntArrayKey> valueSet = new HashSet<>();
        valueSet.add(new IntArrayKey(a));
        valueSet.add(new IntArrayKey(b));
        System.out.println(valueSet.size());

        Map<IntArrayKey, String> valueMap = new HashMap<>();
        valueMap.put(new IntArrayKey(a), "number-sequence");
        System.out.println(valueMap.get(new IntArrayKey(b)));

        int[] source = {5, 6};
        IntArrayKey snapshot = new IntArrayKey(source);
        source[0] = 9;               // Sửa input sau constructor.
        int[] exported = snapshot.values();
        exported[0] = 10;            // Sửa array trả ra từ accessor.
        System.out.println(Arrays.toString(snapshot.values()));
    }
}
```

Biên dịch và chạy:

```bash
javac ArrayCollectionEqualityDemo.java
java ArrayCollectionEqualityDemo
```

Stdout theo đúng thứ tự:

```text
false
false
true
false
false
true
2
false
null
stored
false
true
true
1
true
1
number-sequence
[5, 6]
```

Giải thích kết quả:

1. Ba dòng đầu lần lượt cho biết hai instance mảng khác nhau, `Object.equals()` của array vẫn so tham chiếu, nhưng `Arrays.equals` so nội dung là `true`.
2. `listA.equals(listB)` và `listA.contains(b)` đều `false` vì phần tử mảng `a` và `b` không `equals()` nhau. `listA.contains(a)` là `true` vì đây là cùng đối tượng.
3. `rawSet.size() == 2`: cùng nội dung không bị loại trùng khi phần tử là hai đối tượng array khác nhau. Tìm bằng mảng mới tiếp tục thất bại.
4. `rawMap.get(b)` là `null` còn `rawMap.get(a)` là `stored`: làm khóa `HashMap` bằng array mặc định là chọn **identity của array**, không chọn nội dung.
5. Với hai mảng hai chiều, `Arrays.equals` cho `false` vì mảng con khác tham chiếu; `Arrays.deepEquals` cho `true` vì nội dung lồng giống nhau.
6. Hai `List<Integer>` cùng dãy giá trị bằng nhau; `Arrays.asList(new int[]{...})` lại chỉ có một phần tử `int[]`.
7. `valueList.contains(new IntArrayKey(b))` là `true`: wrapper cung cấp equality theo nội dung. `valueSet.size()` là `1` vì hai wrapper có cùng nội dung khóa. Tra cứu `valueMap` bằng wrapper tạo mới thành công.
8. `snapshot` vẫn in `[5, 6]` dù mảng đầu vào đã bị sửa và accessor trả ra mảng đã bị sửa: wrapper chủ động **copy phòng vệ** ở cả lúc nhận và lúc trả dữ liệu.

Kết quả không phụ thuộc vào giá trị số cụ thể của `Object.hashCode()`, vì chương trình không in identity hash hay thứ tự duyệt `HashSet`.

## 7. Tạo value wrapper đúng cách: equals, hashCode và bất biến

Khi thật sự cần mảng **là một khóa giá trị**, lớp bọc thường rõ ràng hơn cố dùng trực tiếp `int[]`. `IntArrayKey` trong chương trình có ba trách nhiệm:

**(1) Equality theo nội dung:** `Arrays.equals(values, that.values)` quyết định hai khóa có cùng dãy số và thứ tự hay không.

**(2) Hash tương thích:** `Arrays.hashCode(values)` phản ánh nội dung đúng theo `Arrays.equals(...)`. Nếu hai wrapper bằng nhau, chúng có cùng hash. Không được chỉ ghi đè `equals()` rồi giữ hash mặc định của `Object`.

**(3) Snapshot ổn định:** constructor `Arrays.copyOf(...)` để ngăn code ngoài sửa array đã giao, và accessor cũng trả bản sao. Nếu chỉ có `private final int[] values` nhưng lưu trực tiếp array đầu vào, code ngoài vẫn có thể thay các phần tử. Khi làm khóa `HashMap` hoặc phần tử `HashSet`, nội dung mutable làm hash thay đổi có thể khiến thao tác tra cứu hoạt động sai.

Một `record` thông thường như:

```java
record BadArrayKey(int[] values) {}
```

**không tự có equality theo nội dung mảng**. Record mặc định tính equality theo các component, nhưng component là mảng vẫn được so theo equality của array; `new BadArrayKey(new int[]{1,2})` và một `BadArrayKey` khác chứa mảng `{1,2}` riêng biệt không tự bằng nhau. `record` cũng không tự sao chép sâu các object tham chiếu. Nếu dùng record làm wrapper, cần ghi đè `equals()/hashCode()` tương thích và bảo vệ dữ liệu mutable như ví dụ đầy đủ.

### Mảng đối tượng, mảng lồng và wrapper tổng quát

Nếu mảng chứa `String` hoặc value object có `equals()` thích hợp, dùng `Arrays.equals(Object[], Object[])` có thể đủ vì nó so từng phần tử bằng nhau theo equality của phần tử.

Nếu mỗi phần tử lại là mảng, `Arrays.equals(Object[], Object[])` có thể vẫn so identity của mảng con. Khi bài toán cần so toàn bộ cấu trúc lồng, cân nhắc `Arrays.deepEquals` và `Arrays.deepHashCode` theo cặp. Cần xác định trước xem thứ tự và phân biệt `null` ở các cấp có quan trọng không; `deepEquals` không phải chính sách chuẩn cho mọi đồ thị đối tượng tùy ý.

Nếu phần tử mảng là đối tượng mutable khác, dù wrapper đã `Arrays.copyOf` lớp mảng bên ngoài, **các phần tử tham chiếu bên trong vẫn được chia sẻ**. Muốn snapshot thực sự ổn định, phải áp dụng copy phòng vệ hoặc thiết kế bất biến cho các phần tử có ảnh hưởng equality/hash. Không có cơ chế tự sao chép sâu mọi object graph trong Java.

## 8. Khi nào có thể dùng array identity, khi nào cần so theo giá trị?

Không phải dùng mảng làm phần tử hay khóa Collection luôn sai. Quyết định phụ thuộc vào nghĩa của dữ liệu:

| Nhu cầu | Hướng xử lý |
| --- | --- |
| Theo dõi **chính instance mảng** như một buffer/tài nguyên riêng | Giữ tham chiếu mảng trực tiếp có thể phù hợp, vì identity là điều mong muốn. |
| Chỉ cần in hoặc so sánh hai mảng độc lập một lần | Dùng `Arrays.toString` hoặc `Arrays.equals`/`Arrays.deepEquals` trực tiếp, không cần wrapper. |
| `List` chứa các dãy số và muốn `contains` nhận dãy khác instance nhưng cùng nội dung | Dùng value wrapper hoặc biểu diễn bằng `List<Integer>` nếu mô hình và chi phí boxing phù hợp. |
| `HashSet` phải loại các mảng có cùng nội dung | Bọc bằng kiểu có equality/hash theo nội dung và snapshot ổn định. |
| `HashMap` dùng mảng làm khóa logic | Dùng wrapper bất biến với `equals()` và `hashCode()` tương ứng, hoặc encode khóa bằng một value type thích hợp. |
| Cấu trúc mảng nhiều cấp cần so sâu | Dùng `Arrays.deepEquals/deepHashCode` theo cặp khi phù hợp với semantics cần thiết. |

Một cách chuyển `int[]` thành `List<Integer>` trong Java 17+ là:

```java
int[] input = {1, 2, 3};

List<Integer> values = Arrays.stream(input)
        .boxed()
        .toList();

System.out.println(values.equals(List.of(1, 2, 3))); // true
```

*Đoạn minh họa nằm trong phương thức đã import `Arrays` và `List`.* Đây là **chuyển đổi mô hình từ mảng nguyên thủy thành list phần tử boxed**, không phải thay đổi equality của `int[]`. Có chi phí boxing và bộ nhớ cho collection, nên cân nhắc theo mục đích. Nếu cần xử lý một dãy primitive mà không cần Collection semantics, tiếp tục dùng array và `Arrays.equals` có thể đơn giản hơn.

## 9. Các lỗi thường gặp

**Lỗi 1 — Nghĩ `Collection.equals()` tự so sâu tất cả kiểu phần tử.** Equality của Collection thường đi qua equality của từng phần tử. Phần tử mảng khác instance thì `equals()` mặc định không so nội dung.

**Lỗi 2 — Dùng `array.toString()` để tạo khóa.** Mảng thường có chuỗi biểu diễn kiểu/tên lớp và hash, không mô tả nội dung như `Arrays.toString(array)`. Dù dùng `Arrays.toString` để in, không nên tự động coi chuỗi mô tả là kiểu khóa thay vì value type có hợp đồng rõ ràng.

**Lỗi 3 — Dùng `Arrays.equals` cho mảng đa chiều rồi cho rằng đã so sâu.** Với `int[][]`, phần tử bên ngoài là `int[]` nên `Arrays.equals` chỉ so equality mặc định của mảng con. Nếu muốn so từng giá trị lồng, dùng `Arrays.deepEquals`.

**Lỗi 4 — Bọc array trong `record` rồi nghĩ equality đã theo nội dung.** Record equality vẫn dùng semantics của từng component, mà array component là kiểu tham chiếu với equality theo identity. Muốn nội dung, ghi đè đúng `equals()/hashCode()`.

**Lỗi 5 — Quên hashCode của wrapper.** `HashSet`, `HashMap` dựa vào cả `hashCode` và `equals`. Wrapper có content equality nhưng identity hash có thể phá contract và làm lookup thất bại.

**Lỗi 6 — Chỉ đánh dấu trường array là `final`.** `final` ngăn gán một mảng khác vào trường nhưng không ngăn `values[0] = ...`. Nếu nội dung tham gia hash, phải bảo vệ snapshot.

**Lỗi 7 — Nhầm `Arrays.asList(int[])` thành `List<Integer>`.** Nó tạo một list có một phần tử `int[]`. Muốn từng số nguyên như phần tử Collection, cần boxing/chuyển đổi tường minh.

**Lỗi 8 — Đồng nhất mọi dạng Map theo `equals()`.** `IdentityHashMap` dùng identity một cách chủ đích; `TreeMap` chọn tương đương khóa theo `Comparator`/`Comparable`; bài này nói về các collection/map thông thường sử dụng hợp đồng equality của phần tử/khóa, đặc biệt `List`, `HashSet`, `HashMap`.

## 10. Tự kiểm tra

**Câu 1.** Hai mảng `int[]` riêng biệt cùng chứa `{4,5}` có bằng nhau theo `Object.equals()` không?  
**Đáp án:** Không. Mảng dùng reference equality mặc định; `Arrays.equals` mới so nội dung một chiều.

**Câu 2.** `new HashSet<int[]>()` thêm hai mảng mới cùng dữ liệu có thể có `size() == 1` vì nội dung trùng không?  
**Đáp án:** Không. Khi cả hai là instance riêng, Set coi là hai phần tử khác nhau; `size() == 2`.

**Câu 3.** `List.of(new int[]{1}).equals(List.of(new int[]{1}))` cho kết quả gì?  
**Đáp án:** `false`, vì mỗi list có một phần tử là mảng khác tham chiếu.

**Câu 4.** `Arrays.deepEquals` và `Arrays.equals` khác nhau như thế nào với `int[][]`?  
**Đáp án:** `Arrays.equals` so mỗi mảng con theo equality mặc định của mảng, còn `deepEquals` so tiếp nội dung các mảng con.

**Câu 5.** Một record có component `int[]` được Java tự sinh equality theo nội dung array chưa?  
**Đáp án:** Chưa. Cần định nghĩa equality/hash theo `Arrays.equals/Arrays.hashCode` và bảo vệ tính bất biến nếu dùng làm khóa.

**Câu 6.** Muốn tra cứu `HashMap` bằng một array khác instance nhưng cùng nội dung thì nên làm gì?  
**Đáp án:** Dùng value wrapper bất biến có `equals()` và `hashCode()` theo nội dung, hoặc chuyển biểu diễn khóa thành value type phù hợp; đừng dùng `int[]` trực tiếp nếu cần content equality.

**Câu 7.** Vì sao wrapper phải copy mảng ở constructor và accessor khi nội dung dùng làm khóa?  
**Đáp án:** Để bên ngoài không thể thay giá trị tham gia equality/hash trong thời gian object nằm trong collection băm.

Điểm cần nhớ: **Collection tuân theo hợp đồng equality của đối tượng phần tử. Mảng Java tự thân dùng identity equality; `Arrays.equals/deepEquals` chỉ so nội dung khi bạn gọi rõ ràng.** Nếu nghiệp vụ cần các mảng khác instance nhưng cùng nội dung được xem là cùng giá trị trong `List`, `Set` hoặc `Map`, hãy biểu diễn chúng bằng một kiểu khóa hoặc phần tử có content equality và hash tương thích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-value-semantics-008">Tập hợp không cho sửa cấu trúc không đồng nghĩa với bất biến sâu</a>

<details>
<summary>Xem chi tiết</summary>

# Tập hợp không cho sửa cấu trúc không đồng nghĩa với bất biến sâu trong Java

## 1. Vấn đề: không gọi được `add()` vẫn chưa có nghĩa dữ liệu không đổi

Trong Java, một tập hợp có thể **không cho phép thay đổi cấu trúc thông qua giao diện của chính nó**, nhưng giá trị mà người dùng nhìn thấy qua tập hợp đó vẫn thay đổi. Nguyên nhân là tập hợp lưu **tham chiếu tới các phần tử**, và có thể chỉ là một *view* trên tập hợp nguồn.

Xét hai ví dụ:

```java
List<String> source = new ArrayList<>(List.of("A"));
List<String> view = Collections.unmodifiableList(source);
source.add("B");
// view giờ là [A, B], dù view.add(...) không được hỗ trợ.
```

```java
StringBuilder item = new StringBuilder("A");
List<StringBuilder> list = List.of(item);
item.append("B");
// list.get(0).toString() giờ là "AB", dù list.add(...) không được hỗ trợ.
```

Hai đoạn là mảnh minh họa (cần import `java.util.*`); chương trình Java 17 đầy đủ có ở phần 6. Trong ví dụ thứ nhất, **collection nguồn** tiếp tục thay đổi và *unmodifiable view* phản ánh thay đổi đó. Trong ví dụ thứ hai, chính **đối tượng phần tử** thay đổi, dù container có giao diện không sửa được.

Do đó cần phân biệt ba khái niệm:

1. **Không sửa được cấu trúc qua tham chiếu đang cầm** (*unmodifiable interface/view*): không thể gọi thành công `add`, `remove`, `set`, `put` hoặc các thao tác cập nhật tương ứng qua tham chiếu đó.
2. **Bản chụp cấu trúc độc lập** (*structural snapshot*): việc thêm, xóa, thay thế phần tử trong collection nguồn về sau không làm thay đổi danh sách/phần tử liên kết được giữ trong bản chụp.
3. **Bất biến sâu** (*deep immutability*): không có đường sửa hợp lệ nào qua các tham chiếu được cung cấp hoặc chia sẻ để thay đổi cả cấu trúc container lẫn trạng thái mutable của những phần tử được coi là một phần giá trị.

Điểm mấu chốt: `unmodifiable` nói về **các thao tác bị cấm qua một API**; `immutable` nói về **trạng thái có thể quan sát có thay đổi theo thời gian không**. Một lớp có thể đáp ứng điều thứ nhất mà không đáp ứng hai điều còn lại.

## 2. Hai trục độc lập: cấu trúc collection và trạng thái phần tử

Một collection chứa các tham chiếu, không phải bản sao tự động của object phần tử. Vì vậy có ít nhất hai kiểu biến đổi cần xem xét:

| Loại biến đổi | Ví dụ | Container unmodifiable có ngăn được không? |
| --- | --- | --- |
| Thêm phần tử mới | `list.add(newItem)` | Ngăn qua API của chính list đó |
| Xóa phần tử | `list.remove(0)` | Ngăn qua API của chính list đó |
| Thay phần tử ở vị trí | `list.set(0, other)` | Ngăn qua API của chính list đó |
| Sửa chính object ở vị trí 0 | `list.get(0).rename("X")` | **Không**, nếu phần tử mutable và lộ tham chiếu |
| Sửa qua alias tới collection nguồn | `backing.add(newItem)` | **Không** với unmodifiable *view* bọc backing mutable |
| Sửa collection lồng trong collection | `list.get(0).add("X")` | **Không**, nếu danh sách con còn mutable |

Một sơ đồ tham chiếu giúp nhận ra rủi ro:

```text
source (ArrayList) ──────────────┐
                                 ▼
unmodifiableView ────────────> backing ArrayList ──> MutableItem #1
                                                       ▲
                                                       │
caller.item ────────────────────────────────────────────┘

view.add(...)                 => bị từ chối
source.add(...)               => thay đổi dữ liệu view thấy được
caller.item.rename(...)       => thay đổi phần tử view thấy được
```

Trong sơ đồ, hai đường thay đổi cuối cùng **không cần** gọi một phương thức sửa trên `unmodifiableView`.

## 3. `Collections.unmodifiableXxx`: view không sửa được

Các hàm như `Collections.unmodifiableList`, `unmodifiableSet`, `unmodifiableMap` trả một lớp bọc không hỗ trợ sửa qua giao diện wrapper. Phần tử và cấu trúc vẫn có thể biến đổi qua **backing collection** nếu mã khác tiếp tục giữ tham chiếu đến nó.

Ví dụ có thể dự đoán:

```java
List<String> backing = new ArrayList<>(List.of("red"));
List<String> view = Collections.unmodifiableList(backing);

backing.add("blue");
System.out.println(view); // [red, blue]

view.add("green"); // Ném UnsupportedOperationException
```

Một `unmodifiableList` không tự gọi `new ArrayList<>(backing)` để tách nguồn. Tương tự, `Collections.unmodifiableMap(map)` không tự chụp một bản độc lập của map, còn `Collections.unmodifiableSet(set)` không tự tách set gốc.

Không nên nhầm với **read-only view** có chủ đích: trong một số API, ta *muốn* người nhận nhìn thấy các cập nhật tiếp theo từ chủ sở hữu nhưng không có quyền cập nhật qua view. Đây là một ngữ nghĩa hợp lệ nếu được thiết kế và mô tả rõ ràng. Nhưng nếu API hứa “bản chụp tại thời điểm gọi” hoặc “giá trị không đổi”, chỉ wrapper là chưa đủ.

Muốn có bản chụp về **cấu trúc**, có thể sao chép trước rồi mới bọc:

```java
List<String> snapshot =
    Collections.unmodifiableList(new ArrayList<>(backing));
```

Sau đó `backing.add(...)` không đổi kích thước `snapshot`. Dù vậy, nếu danh sách chứa `MutableItem` thay vì `String`, cả bản chụp và nguồn vẫn có thể trỏ tới cùng `MutableItem`; việc sửa phần tử còn ảnh hưởng.

## 4. `List.of`, `List.copyOf` và `Stream.toList` bảo đảm gì?

### 4.1. `List.of(...)`

`List.of(a, b, ...)` tạo một list không hỗ trợ thay đổi cấu trúc. Nó không cho phép phần tử `null`. Tuy nhiên, nếu `a` hoặc `b` là mutable object, các object đó **vẫn mutable**; list giữ tham chiếu chứ không tự tạo clone của phần tử.

```java
StringBuilder name = new StringBuilder("A");
List<StringBuilder> names = List.of(name);
name.append("B");
System.out.println(names.get(0)); // AB
```

### 4.2. `List.copyOf(source)`

`List.copyOf` (Java 10+) trả một list không sửa được chứa các phần tử của collection đầu vào tại thời điểm copy, theo thứ tự lặp được quan sát. Khi `source` là một collection mutable thông thường, thay đổi cấu trúc nguồn về sau sẽ không đổi nội dung cấu trúc của list kết quả.

Nhưng:

- Đây là bản sao **nông về phần tử**: các object mutable bên trong vẫn được chia sẻ bằng tham chiếu.
- `List.copyOf` không cho phần tử `null`; nếu input chứa `null` sẽ ném `NullPointerException`.
- Nếu input đã là unmodifiable list phù hợp, JDK có thể **tái sử dụng chính instance đó**; không được phụ thuộc vào việc `copyOf` luôn cấp phát object list mới.
- Bản chụp không tự định nghĩa ngữ nghĩa equality của phần tử; `List.equals()` và `List.hashCode()` sử dụng quan hệ equality/hash của phần tử.

### 4.3. `Stream.toList()` trong Java 17

`stream.toList()` trả một `List` không cho sửa qua các thao tác cập nhật thông thường, giữ thứ tự encounter của stream nếu có. Khác `List.copyOf`, `Stream.toList()` có thể chứa `null`. Việc list không cho sửa **không** khiến mutable object phần tử bất biến. Không dùng tính chất “không sửa được” của kết quả stream để suy ra “bất biến sâu”.

### 4.4. `Set` và `Map` cũng có vấn đề tương tự

- `Set.of`, `Set.copyOf` và `Map.of`, `Map.copyOf` tạo container không sửa được nhưng không sao chép sâu key/value mutable.
- `Set.of` từ chối phần tử trùng theo equality; `Set.copyOf` từ collection có phần tử trùng chỉ giữ một đại diện, không có bảo đảm sẽ giữ đại diện nào trong các bản bằng nhau. Nếu thông tin trùng lặp quan trọng, đừng chuyển danh sách thành set một cách máy móc.
- `Map.copyOf` không nhận key/value `null`; `Set.copyOf` không nhận `null`. Việc chuyển có thể thay đổi chính sách null đang được API chấp nhận.
- Thứ tự duyệt của các collection không sửa được từ `Set.of/copyOf` và `Map.of/copyOf` không nên được giả định là thứ tự chèn hay thứ tự sắp xếp.
- Nếu mutable object được dùng làm key trong map hoặc phần tử set và bị sửa những trường tham gia `equals/hashCode`, lookup có thể hỏng **ngay cả khi chính map/set không sửa được cấu trúc**.

## 5. Bản sao nông và bất biến sâu khác nhau như thế nào?

**Shallow copy** tạo container mới nhưng vẫn giữ các tham chiếu phần tử. Với phần tử bất biến như `String`, `Integer` hoặc một record chỉ chứa giá trị bất biến, shallow copy thường đủ để bảo vệ toàn bộ giá trị mà API quan tâm.

Với phần tử mutable, shallow copy không đủ:

```java
StringBuilder part = new StringBuilder("A");
List<StringBuilder> input = new ArrayList<>();
input.add(part);

List<StringBuilder> shallow = List.copyOf(input);
part.append("B");

System.out.println(shallow.get(0)); // AB
```

Nếu bên ngoài giữ `part`, họ vẫn có thể thay đổi dữ liệu mà `shallow` trình bày. Muốn snapshot thực sự ổn định, phải **sao chép hoặc chuyển đổi từng phần tử mutable** sang biểu diễn bất biến tương ứng:

```java
List<String> stable =
    input.stream()
         .map(StringBuilder::toString)
         .toList();
```

Ở đây `StringBuilder::toString` tạo giá trị `String` bất biến tại thời điểm duyệt; sửa builder sau đó không thay đổi các String đã ghi. Đây là một **phương án snapshot theo giá trị** phù hợp khi chỉ cần nội dung chuỗi, không bảo toàn bản sắc của từng builder.

Với dữ liệu lồng nhau, copy cần đi qua từng cấp mutable:

```java
// input: List<List<String>>, các danh sách con ban đầu mutable.
// Sau thao tác này, cả container ngoài và từng container trong đều
// không sửa được; String cũng bất biến.
List<List<String>> nestedSnapshot = List.copyOf(
    input.stream().map(List::copyOf).toList()
);
```

Đây là mảnh minh họa dùng `java.util.List`, đặt trong bối cảnh có biến `input` đúng kiểu. Trường hợp `List<List<MutableItem>>` vẫn cần sao chép/chuyển đổi từng `MutableItem`; wrapper hai tầng chưa thành deep immutability.

Một điểm quan trọng: “bất biến sâu” phải được xét đối với **đồ thị trạng thái liên quan**. Nếu object con là một kết nối mạng, một đối tượng có ID định danh hay một nguồn dữ liệu sống, việc sao chép mọi thứ theo kiểu clone tổng quát vừa không luôn khả thi vừa có thể sai nghĩa. Lớp cần xác định dữ liệu nào thuộc giá trị và ranh giới nào cần snapshot.

## 6. Ví dụ Java 17 chạy được: view, snapshot và phần tử mutable

Ví dụ đầy đủ dưới đây đối chiếu bốn trường hợp: view phản ánh backing list; structural snapshot tách backing nhưng chưa tách phần tử; snapshot sâu của giá trị phần tử; danh sách lồng nhau được sao chép từng cấp.

Lưu thành **`CollectionDeepImmutabilityDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CollectionDeepImmutabilityDemo {
    static final class MutableItem {
        private String code;

        MutableItem(String code) {
            this.code = Objects.requireNonNull(code);
        }

        String code() {
            return code;
        }

        void rename(String newCode) {
            this.code = Objects.requireNonNull(newCode);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MutableItem that)) return false;
            return code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    record ImmutableItem(String code) {
        ImmutableItem {
            Objects.requireNonNull(code);
        }
    }

    static final class Catalog {
        private final List<ImmutableItem> items;

        Catalog(List<MutableItem> source) {
            Objects.requireNonNull(source);
            this.items = source.stream()
                .map(item -> new ImmutableItem(item.code()))
                .toList();
        }

        List<ImmutableItem> items() {
            return items;
        }
    }

    static final class ShallowKey {
        private final List<MutableItem> parts;

        ShallowKey(List<MutableItem> source) {
            parts = List.copyOf(source);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ShallowKey that)) return false;
            return parts.equals(that.parts);
        }

        @Override
        public int hashCode() {
            return parts.hashCode();
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("red"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);

        source.add("blue");
        System.out.println("View: " + view);
        System.out.println("Snapshot: " + snapshot);
        try {
            view.add("green");
        } catch (UnsupportedOperationException ex) {
            System.out.println("View rejects add: true");
        }

        MutableItem item = new MutableItem("A");
        List<MutableItem> shallow = List.copyOf(List.of(item));
        Catalog catalog = new Catalog(List.of(item));
        item.rename("B");

        System.out.println("Shallow item: " + shallow.get(0).code());
        System.out.println("Catalog item: " + catalog.items().get(0).code());
        try {
            catalog.items().clear();
        } catch (UnsupportedOperationException ex) {
            System.out.println("Catalog rejects clear: true");
        }

        List<String> inner = new ArrayList<>(List.of("x"));
        List<List<String>> outer = new ArrayList<>();
        outer.add(inner);
        List<List<String>> outerOnly = List.copyOf(outer);
        List<List<String>> allLevels = List.copyOf(
            outer.stream().map(List::copyOf).toList()
        );

        inner.add("y");
        System.out.println("Outer-only nested: " + outerOnly.get(0));
        System.out.println("All-level nested: " + allLevels.get(0));

        MutableItem keyPart = new MutableItem("K1");
        ShallowKey key = new ShallowKey(List.of(keyPart));
        Set<ShallowKey> keys = new HashSet<>();
        keys.add(key);
        int before = key.hashCode();
        keyPart.rename("K2");
        System.out.println("Key hash changed: "
            + (before != key.hashCode()));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên** trong thư mục chứa tệp:

```bash
javac CollectionDeepImmutabilityDemo.java
java CollectionDeepImmutabilityDemo
```

Kết quả chuẩn:

```text
View: [red, blue]
Snapshot: [red]
View rejects add: true
Shallow item: B
Catalog item: A
Catalog rejects clear: true
Outer-only nested: [x, y]
All-level nested: [x]
Key hash changed: true
```

Giải thích chi tiết:

1. `Collections.unmodifiableList(source)` là **view** trên `source`; khi thêm `"blue"` vào nguồn, view hiện `[red, blue]`. `List.copyOf(source)` đã tạo structural snapshot ban đầu `[red]`, nên không thay đổi.
2. View không cho `add` thông qua nó; exception chỉ xác nhận API view không sửa được, **không xác nhận backing không đổi**.
3. `shallow` giữ chính tham chiếu `MutableItem`. Dù container không cho cập nhật, `item.rename("B")` làm giá trị phần tử quan sát được đổi sang `B`.
4. `Catalog` chuyển mỗi `MutableItem` thành một `ImmutableItem` có field kiểu `String` bất biến. Kết quả thu tại thời điểm `"A"`, độc lập với việc đổi `item` về sau. `Stream.toList()` cho container không sửa được, nên cả cấu trúc lẫn các giá trị record trong ví dụ đều ổn định.
5. `outerOnly` copy **list ngoài** nhưng vẫn tham chiếu `inner` mutable; khi sửa `inner`, nội dung của `outerOnly.get(0)` đổi. `allLevels` copy list con trước khi copy list ngoài; giá trị `[x]` được giữ nguyên.
6. `ShallowKey` copy container bằng `List.copyOf` nhưng vẫn chứa một `MutableItem` có equality/hash dựa trên `code` mutable. Đổi code từ `K1` sang `K2` làm hash của cả `ShallowKey` thay đổi. Điều này nguy hiểm nếu `ShallowKey` đang được dùng làm khóa trong `HashSet`/`HashMap`. Ví dụ chỉ kiểm tra hash đã đổi, không phụ thuộc giả định về bucket hay kết quả tra cứu cụ thể của implementation.

## 7. Liên hệ hợp đồng `equals()` và `hashCode()`

Trong Java, một `List` được so sánh theo thứ tự phần tử: `List.equals` lần lượt so các phần tử bằng quy tắc equality; `List.hashCode` tổng hợp hash của các phần tử theo thứ tự. `Set` và `Map` có hợp đồng equality/hash dựa trên các phần tử hoặc entry tương ứng.

Nếu một phần tử mutable nằm trong list, thay đổi của phần tử có thể thay đổi:

- Kết quả `list.equals(otherList)`.
- Giá trị `list.hashCode()`.
- Giá trị `hashCode()` của một đối tượng bọc list và sử dụng list làm thành phần equality/hash.

Đây là lý do một khóa được tạo bằng `List.copyOf(mutableElements)` **vẫn có thể là khóa không ổn định** dù list không cho thay đổi cấu trúc. Khi object đã nằm trong một cấu trúc hash, nên tránh thay đổi các thông tin dùng trong `equals()` và `hashCode()` của khóa. Nếu cần khóa theo nội dung, hãy lưu các phần tử dưới dạng value object bất biến hoặc snapshot.

Các nguyên tắc cần tuân theo:

- **Equal implies same hash:** nếu `a.equals(b)` là `true` thì `a.hashCode() == b.hashCode()` phải là `true`; chiều đảo lại không bắt buộc.
- **Hash nhất quán theo trạng thái equality:** mã hash phải giữ ổn định khi các thông tin được dùng trong equality không đổi. Sửa mutable element có thể làm cả equality và hash biến đổi.
- **Không dùng tính unmodifiable của container để chứng minh an toàn khi làm hash key.** Kiểm tra cả keys, values và các đối tượng lồng nhau.
- **Không đưa object mutable làm key của một `Map` chỉ vì map đã được tạo bằng `Map.copyOf`.** Bất biến cấu trúc map và tính ổn định của key là hai chuyện khác nhau.

Nếu một phần tử mutable **không** dùng những trường thay đổi trong `equals/hashCode` (ví dụ class kế thừa identity equality), sửa dữ liệu hiển thị có thể không làm hash của list đổi. Nhưng list đó vẫn **không bất biến sâu**: nội dung có thể quan sát từ phần tử vẫn biến đổi. Đây là hai tiêu chí riêng cần kiểm tra.

## 8. Thiết kế API bảo vệ giá trị collection

### 8.1. Khi collection chứa giá trị bất biến

Ví dụ với `List<String>`:

```java
final class Tags {
    private final List<String> tags;

    Tags(List<String> input) {
        tags = List.copyOf(input);
    }

    List<String> tags() {
        return tags;
    }
}
```

Đây là một lớp minh họa Java hoàn chỉnh không có `main`. `Tags` giữ snapshot cấu trúc bằng `List.copyOf` và `String` là bất biến. Vì vậy getter có thể trả trực tiếp list đã lưu: caller không thể `add/set/remove` qua đó và không thể sửa nội dung String. Nếu business domain cho phép `null` hoặc đòi bảo toàn một cấu trúc khác, phải chọn cách sao chép theo hợp đồng thay vì áp dụng `copyOf` bất kể dữ liệu.

### 8.2. Khi collection chứa phần tử mutable

Đừng dừng ở `List.copyOf(input)`:

```java
// Ví dụ: giá trị cần công bố chỉ là tên tại thời điểm tạo.
List<String> namesSnapshot = input.stream()
    .map(Person::name)
    .toList();
```

Đây là mảnh code minh họa, giả định `input` là `List<Person>` và `Person.name()` trả `String`. Kết quả là list không sửa được của các String bất biến. Nếu cần toàn bộ trạng thái người, hãy thiết kế immutable DTO/record snapshot:

```java
record PersonSnapshot(String id, String name) {}
// Trong luồng chuyển đổi, nếu Person có các getter tương ứng:
List<PersonSnapshot> snapshots = input.stream()
    .map(p -> new PersonSnapshot(p.id(), p.name()))
    .toList();
```

Đây cũng là mảnh code minh họa; record trên chỉ đủ bất biến sâu nếu các component là giá trị bất biến. Nếu đưa `List<MutableAddress>` vào record thì record không tự copy sâu chúng.

### 8.3. Khi giá trị lồng nhau là map/list

Với `Map<String, List<String>>` và String bất biến, có thể copy từng list rồi copy map:

```java
// input là Map<String, List<String>>.
Map<String, List<String>> nested = new HashMap<>();
input.forEach((key, value) ->
    nested.put(key, List.copyOf(value)));
Map<String, List<String>> snapshot = Map.copyOf(nested);
```

Đoạn này cần import `java.util.Map` và `java.util.HashMap`, là mảnh code minh họa ở phạm vi có biến `input`. Kết quả không cho sửa cấu trúc map và list value, còn String ở cả key và value là bất biến. Nếu map chứa các object mutable làm key hoặc value, phải xử lý chúng. Nếu cần giữ thứ tự chèn hoặc giá trị `null`, cần dùng loại collection và cách bọc phù hợp, không tự ý đổi nghĩa dữ liệu.

### 8.4. Phân biệt snapshot với dữ liệu sống

Một API có thể chọn một trong hai hợp đồng:

- **Snapshot:** “Dữ liệu nhận được là giá trị tại thời điểm gọi; cập nhật tiếp theo từ chủ sở hữu không phản ánh vào bản trả về.”
- **View đang sống:** “Dữ liệu trả về có thể phản ánh cập nhật từ chủ sở hữu; bên nhận không thể cập nhật qua view này.”

Cả hai đều hợp lệ nếu tài liệu hóa rõ. Để hỗ trợ an toàn luồng hoặc hashing, cần thêm cam kết về đồng bộ và tính ổn định của **đối tượng phần tử**; chỉ API unmodifiable không giải quyết toàn bộ.

## 9. Các ngộ nhận thường gặp

| Ngộ nhận | Thực tế | Cách xử lý |
| --- | --- | --- |
| “Gọi `add` bị exception thì list bất biến sâu” | Có thể chỉ là view và/hoặc chứa phần tử mutable | Kiểm tra backing alias và từng cấp phần tử |
| “`Collections.unmodifiableList` tự sao chép source” | Là view bọc backing collection | Copy trước rồi bọc nếu cần snapshot |
| “`List.copyOf` clone từng object phần tử” | Sao chép tham chiếu phần tử | Chuyển thành DTO/giá trị bất biến hoặc deep copy thích hợp |
| “`List.of` chứa phần tử mutable mà vẫn immutable sâu” | Container không sửa được, phần tử vẫn mutable | Tạo snapshot giá trị phần tử |
| “`final List<Person>` là một danh sách bất biến” | `final` chỉ ngăn gán lại field; không khóa nội dung list/person | Lưu immutable snapshot và kiểm soát getter |
| “Record luôn bất biến sâu” | Record component có thể trỏ tới array/collection/object mutable | Copy-in, copy-out và xử lý từng component mutable |
| “Copy một list ngoài đủ cho mọi nested list” | List con vẫn có thể được chia sẻ | Copy các tầng có thể sửa |
| “Unmodifiable `Map` bảo vệ giá trị mutable” | Value mutable vẫn thay đổi từ alias | Copy hoặc dùng value bất biến |
| “Collection unmodifiable làm `hashCode` ổn định” | Hash có thể phụ thuộc các phần tử mutable | Dùng trường/giá trị ổn định trong equality/hash |
| “Copy luôn là snapshot nguyên tử trong concurrency” | Nguồn có thể bị sửa trong quá trình copy | Đồng bộ khi đọc/ghi hoặc dùng snapshot bất biến theo thiết kế |

## 10. Những giới hạn cần biết

### 10.1. Không có một `deepCopy` tổng quát cho mọi object graph

Đối tượng có thể tham chiếu vòng, chứa tài nguyên hệ thống, cache, trạng thái đa luồng hoặc khóa nhận diện duy nhất. Sao chép tất cả node có thể làm sai ngữ nghĩa. Nên xác định rõ **biểu diễn giá trị cần bất biến**, xây immutable representation riêng hoặc đặt quyền sở hữu dữ liệu rõ ràng.

Ví dụ, với một `Person` có danh sách `Address` mutable, có thể lưu `List<AddressSnapshot>` với từng địa chỉ gồm các trường String và số bất biến. Đây là copy theo giá trị nghiệp vụ, thường rõ hơn cố clone mù toàn bộ `Person`.

### 10.2. Tính bất biến và thread safety không đồng nhất

Một graph thực sự bất biến sau khi được khởi tạo và **công bố an toàn** có thể được chia sẻ để đọc mà không cần khóa cho việc bảo vệ giá trị đó. Nhưng bản thân việc gọi `List.copyOf(source)` khi một thread khác đang sửa `source` không bảo đảm có snapshot nguyên tử; có thể phát sinh dữ liệu không nhất quán hoặc exception tùy cấu trúc. Cần giao thức đồng bộ giữa bên ghi và bên sao chép, hoặc chuyển quyền sở hữu/trao đổi snapshot bất biến.

Unmodifiable view của `ArrayList` không tự làm `ArrayList` thread-safe. Tương tự, object phần tử mutable trong immutable container vẫn cần cơ chế đồng bộ nếu bị nhiều thread truy cập thay đổi.

### 10.3. Chi phí sao chép và lựa chọn đường API

Copy container có chi phí tỷ lệ với kích thước collection; copy sâu còn cộng chi phí từng phần tử và dữ liệu lồng nhau. Nếu getter thường xuyên tạo snapshot của danh sách lớn, có thể cân nhắc lưu immutable snapshot một lần, trả thao tác truy vấn dạng `size()`/`get()` cho giá trị bất biến, hoặc tổ chức cơ chế version/snapshot phù hợp. Không tối ưu bằng cách vô tình trả alias mutable làm lộ bất biến.

## 11. Quy trình review một trường collection

Khi đánh giá class có field `List`, `Set` hoặc `Map`, hãy lần lượt trả lời:

1. Collection được tạo ở đâu? Có caller nào vẫn giữ tham chiếu đến backing collection không?
2. Constructor có giữ nguyên tham chiếu của collection đầu vào, dùng wrapper view hay tạo structural snapshot?
3. Getter có trả bản sao, immutable container hoặc view đang sống? Hợp đồng có nêu rõ sự khác biệt không?
4. Phần tử thuộc loại bất biến hay mutable? Có thể sửa từng phần tử thông qua alias bên ngoài hoặc `get` không?
5. Các phần tử có chứa array, list, map hay object mutable bên trong không? Đã bảo vệ tới độ sâu cần thiết chưa?
6. Key/value của map và phần tử của set có equality/hash dựa vào trạng thái mutable không?
7. Có yêu cầu đặc biệt về `null`, phần tử trùng, thứ tự hoặc tính tương thích của dữ liệu không?
8. Khi thread khác sửa source lúc copy, dữ liệu snapshot có cần nhất quán không và cơ chế nào bảo đảm điều đó?
9. Bài test có thử thay đổi source sau constructor, sửa phần tử qua tham chiếu giữ lại, sửa thông qua getter và sửa collection lồng nhau không?

Một phép kiểm tra thực tế tốt là vừa thử `add` lên list trả về, **vừa thử sửa backing list, từng object phần tử và nested collection**. Chỉ kiểm tra ngoại lệ từ `add` là chưa đủ để kết luận bất biến sâu.

## 12. Tự kiểm tra

**Câu 1.** `Collections.unmodifiableList(backing)` trả view; sau đó `backing.add("B")`. View có hiển thị `"B"` không?

**Đáp án:** Có. Wrapper cấm cập nhật qua view nhưng phản ánh thay đổi của backing collection.

**Câu 2.** `List.copyOf(source)` chứa `MutableItem`. Sau copy, bên gọi có thể gọi `item.rename("B")` và thấy tên đổi qua list không?

**Đáp án:** Có, nếu list và bên gọi vẫn giữ cùng tham chiếu `MutableItem`. `List.copyOf` chỉ sao chép cấu trúc/phần tử dưới dạng tham chiếu, không clone từng object mutable.

**Câu 3.** Bạn lưu `List<String>` từ `List.copyOf` vào một field cuối và chỉ trả chính list đó qua getter. Bất biến sâu đối với những chuỗi ấy được bảo vệ chưa?

**Đáp án:** Có, nếu không có dữ liệu mutable khác trong graph liên quan, bởi list không sửa được và String là bất biến. Việc container có phải instance mới ở mỗi lần gọi hay không không ảnh hưởng tính bất biến của giá trị này.

**Câu 4.** Tạo `List<List<String>> outerCopy = List.copyOf(outer)` trong khi các list con vẫn mutable. Bất biến sâu đã có chưa?

**Đáp án:** Chưa. Cần snapshot từng list con trước (ví dụ `List.copyOf(inner)`) rồi mới đóng băng list ngoài.

**Câu 5.** Vì sao `Map.copyOf(source)` không bảo đảm khóa map an toàn khi dùng trong cấu trúc hash nếu key mutable?

**Đáp án:** `Map.copyOf` không sao chép sâu key. Nếu key bị thay đổi dữ liệu tham gia `equals/hashCode`, quan hệ equality hoặc hash có thể thay đổi, làm hỏng việc tìm kiếm dù bản thân map cấm sửa cấu trúc.

**Câu 6.** Một class trả `Collections.unmodifiableList(new ArrayList<>(source))`. Đây là snapshot sâu chưa?

**Đáp án:** Đây là snapshot **cấu trúc** độc lập ở cấp list ngoài; chưa phải snapshot sâu nếu source chứa các object mutable hoặc collection lồng nhau.

**Câu 7.** Một record chỉ chứa component `List<MutableItem>`. Record tự động làm giá trị đó bất biến sâu không?

**Đáp án:** Không. Trường component là final nhưng mutable list và mutable item có thể bị thay đổi qua tham chiếu chia sẻ; cần thiết kế copy-in/copy-out và snapshot của phần tử khi cần.

---

**Ghi nhớ:** “Không cho sửa cấu trúc” mới bảo vệ **các thao tác lên container thông qua một tham chiếu**. Một *unmodifiable view* vẫn có thể phản ánh cập nhật của backing collection; một immutable structural snapshot vẫn có thể chứa object mutable. Muốn bất biến sâu, hãy xem toàn bộ graph dữ liệu có thể quan sát, bảo vệ alias ở cả đầu vào và đầu ra, và chuyển các thành phần mutable liên quan thành giá trị hoặc snapshot bất biến.

</details>

- [Quay lại đầu trang](#back-to-top)
