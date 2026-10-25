<a id="back-to-top"></a>

# List

## Menu
- [Đặc trưng theo vị trí, phần tử trùng và thứ tự duyệt](#list-001)
- [get, set, add theo chỉ số và dịch chuyển phần tử](#list-002)
- [Phân biệt remove(int) với remove(Object)](#list-003)
- [indexOf, lastIndexOf và tìm phần tử không tồn tại](#list-004)
- [Chèn nhiều phần tử bằng addAll có vị trí](#list-005)
- [replaceAll và sort trên danh sách](#list-006)
- [subList với khoảng đầu bao gồm, cuối không bao gồm](#list-007)
- [equals và hashCode phụ thuộc thứ tự của List](#list-008)
- [Chọn List khi cần truy cập chỉ số hoặc giữ phần tử trùng](#list-009)

## <a id="list-001">Đặc trưng theo vị trí, phần tử trùng và thứ tự duyệt</a>

<details>
<summary>Xem chi tiết</summary>

# List: đặc trưng theo vị trí, phần tử trùng và thứ tự duyệt

`List<E>` biểu diễn một **dãy phần tử có thứ tự theo vị trí**. Khi đọc một list, ta quan tâm không chỉ “có những giá trị nào”, mà còn “mỗi giá trị đang xuất hiện ở vị trí nào và theo thứ tự nào”.

Ví dụ:

```text
[C, A, C, B]
```

Dãy này có bốn phần tử. Hai lần xuất hiện của C nằm ở hai vị trí khác nhau. Thứ tự hiện tại là C rồi A rồi C rồi B; nó không cần là thứ tự tăng dần.

Ba đặc điểm cần nắm cùng nhau:

1. **Theo vị trí:** có thể truy cập hoặc thao tác tại một chỉ số.
2. **Có thể giữ phần tử trùng:** các lần xuất hiện bằng nhau vẫn là những vị trí riêng.
3. **Duyệt theo thứ tự của dãy:** cách duyệt tiến thông thường đi từ vị trí đầu đến cuối.

## 1. Vì sao dùng mô hình List?

Một danh sách bài hát có thể phát cùng một bài nhiều lần. Một lịch sử thao tác có thể ghi cùng một hành động ở nhiều thời điểm. Các bước trong một quy trình có thứ tự trước–sau.

Với những tình huống này, chỉ biết tập các giá trị duy nhất là chưa đủ:

```text
Danh sách phát 1: [A, B, A]
Danh sách phát 2: [A, A, B]
```

Cả hai cùng có A và B, cùng ba lần xuất hiện, nhưng trải nghiệm phát khác nhau vì thứ tự khác nhau.

List phù hợp khi **vị trí và số lần xuất hiện** là một phần của dữ liệu cần giữ. Nếu yêu cầu là không có giá trị trùng, không nên mong List tự thực thi ràng buộc duy nhất đó.

List là một interface của Java Collections Framework, kế thừa Collection và Iterable. Các lớp như ArrayList và LinkedList cung cấp cách triển khai khác nhau cho mô hình này. Xem [API List của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

## 2. Chỉ số bắt đầu từ 0 và gắn với trạng thái hiện tại

Với:

```text
List:   [C, A, C, B]
Index:   0  1  2  3
```

- Phần tử đầu ở chỉ số 0.
- Phần tử cuối ở chỉ số `size() - 1`.
- Với size bằng 4, chỉ số phần tử hợp lệ là 0, 1, 2, 3.

Chỉ số không phải mã định danh bền vững của một đối tượng. Nó mô tả vị trí trong **dãy hiện tại**.

### 2.1. size đếm số lần xuất hiện

`size()` của `[C, A, C, B]` là 4, không phải 3. C xuất hiện hai lần và mỗi lần chiếm một vị trí.

Nếu list cho phép null, một null cũng là một phần tử được tính vào size. Null không tự tạo “lỗ trống” ngoài dãy chỉ số.

### 2.2. Giới hạn chỉ số khác nhau theo thao tác

Giả sử list có n phần tử và các thao tác thay đổi tương ứng được hỗ trợ:

| Thao tác | Miền chỉ số |
| --- | --- |
| `get(index)` | `0 <= index < n` |
| `set(index, value)` | `0 <= index < n` |
| `remove(index)` | `0 <= index < n` |
| `add(index, value)` | `0 <= index <= n` |

Với thao tác chèn, index bằng n nghĩa là chèn vào cuối. Nhưng `get(n)` không hợp lệ vì chưa có phần tử nào ở đó.

List rỗng có size bằng 0:

- Không có chỉ số nào dùng được để đọc phần tử.
- Có thể chèn tại 0 nếu list hỗ trợ thêm.

Không gọi `get(size())` để lấy phần tử cuối. Với list không rỗng, vị trí cuối là `size() - 1`.

### 2.3. Chèn và xóa làm thay đổi vị trí

Ban đầu:

```text
[C, A, C, B]
 0  1  2  3
```

Chèn X tại index 1:

```text
[C, X, A, C, B]
 0  1  2  3  4
```

Phần tử trước ở index 1 chuyển sang index 2. Những phần tử phía sau cũng có chỉ số mới.

Nếu lưu một chỉ số rồi tiếp tục chèn hoặc xóa ở phía trước, chỉ số cũ có thể trỏ tới giá trị khác hoặc không còn hợp lệ. Khi nghiệp vụ cần nhận diện một bản ghi qua nhiều thay đổi, hãy dùng mã định danh phù hợp thay vì coi chỉ số là danh tính.

## 3. Phân biệt thêm, chèn, thay thế và xóa

Các thao tác sau không cùng ý nghĩa:

| Thao tác | Tác động nếu thành công | Thay đổi size |
| --- | --- | --- |
| `add(value)` | Thêm vào cuối dãy | Tăng 1 |
| `add(index, value)` | Chèn trước phần tử đang ở vị trí đó, hoặc cuối dãy | Tăng 1 |
| `set(index, value)` | Thay giá trị ở một vị trí đã có | Không đổi |
| `remove(index)` | Loại lần xuất hiện ở vị trí đó | Giảm 1 |
| `remove(value)` | Loại lần xuất hiện đầu tiên phù hợp theo giá trị | Giảm 1 nếu có phần tử bị loại |

Ví dụ ban đầu là `[C, A, C, B]`:

- `add(1, "X")` cho `[C, X, A, C, B]`.
- Từ kết quả đó, `set(2, "M")` cho `[C, X, M, C, B]`; A được thay thế, không có vị trí mới.
- `remove(0)` loại C đầu tiên, cho `[X, M, C, B]`.

`set` trả giá trị cũ ở vị trí được thay. `remove(int)` trả phần tử bị xóa. `remove(Object)` trả boolean cho biết có loại được một lần xuất hiện hay không.

Những khác biệt này giúp giải thích thay đổi của dãy mà không cần biết bên trong lớp triển khai dùng mảng hay các nút liên kết.

## 4. Phần tử trùng là nhiều lần xuất hiện riêng biệt

### 4.1. Trùng theo giá trị không đòi cùng tham chiếu

Một list có thể chứa hai đối tượng khác nhau nhưng bằng nhau theo equals.

Với record:

```java
record Entry(String code) {}
```

hai lệnh `new Entry("A")` tạo hai đối tượng riêng. Tuy nhiên, equals của record xem chúng bằng nhau vì cùng code.

Nếu cùng đưa cả hai vào ArrayList, cả hai lần xuất hiện được giữ. Đây không phải hai biến “trỏ vào cùng một ô danh sách”.

List cũng có thể giữ chính cùng một tham chiếu ở nhiều vị trí. Khi đó, sửa trạng thái đối tượng có thể được nhìn thấy ở mọi vị trí đang tham chiếu nó. List chứa các tham chiếu; thêm lại một đối tượng không tự sao chép sâu đối tượng đó.

### 4.2. indexOf và lastIndexOf trả lời hai câu hỏi khác nhau

Với `[A, B, A]`:

| Câu hỏi | Kết quả |
| --- | --- |
| `indexOf("A")` | 0 |
| `lastIndexOf("A")` | 2 |
| `indexOf("Z")` | -1 |
| `contains("A")` | true |

IndexOf tìm lần xuất hiện đầu, lastIndexOf tìm lần cuối, còn contains chỉ hỏi có ít nhất một phần tử phù hợp.

Các thao tác tìm theo đối tượng sử dụng hợp đồng equals phù hợp, không chỉ so tham chiếu bằng `==`.

Không dùng kết quả -1 làm chỉ số để get. Cần kiểm tra đã tìm thấy hay chưa.

### 4.3. remove theo giá trị không xóa mọi bản sao

Trên `[A, B, A]`, `remove("A")` loại A đầu, còn `[B, A]`.

Muốn loại mọi lần xuất hiện thỏa điều kiện phải dùng thao tác phù hợp với ý định đó, chẳng hạn removeIf trên nguồn hỗ trợ, thay vì mong một remove theo giá trị tự xóa hết.

Nếu cần loại chính lần xuất hiện đang duyệt, iterator tường minh có thể cung cấp mục tiêu chính xác khi nguồn hỗ trợ xóa. Không thay thế tùy tiện bằng remove theo giá trị nếu có nhiều phần tử bằng nhau.

### 4.4. Không dùng List làm ràng buộc duy nhất

Các triển khai thông dụng như ArrayList và LinkedList cho phép phần tử trùng. Hợp đồng List mô tả đây là hành vi điển hình; một triển khai chuyên biệt vẫn có thể đặt giới hạn được công bố.

Điểm thiết kế thực tế là: **không chọn List rồi giả định nó tự chống trùng**. Nếu nghiệp vụ cần vừa thứ tự lần xuất hiện vừa kiểm tra mã duy nhất, phải xác định ràng buộc và cơ chế kiểm tra rõ ràng.

## 5. Thứ tự của List không đồng nghĩa đã được sắp xếp

Dãy:

```text
[C, A, C, B]
```

có thứ tự xác định dù không tăng dần.

### 5.1. Thứ tự theo vị trí

Đây là thứ tự đang có trong dãy. Khi chỉ nối thêm bằng add(value), phần tử mới ở cuối. Khi chèn tại một vị trí, dãy thay đổi theo yêu cầu chèn.

### 5.2. Thứ tự sắp xếp theo tiêu chí

Đây là kết quả của một quy tắc như theo tên, điểm hoặc thời gian. List không tự chọn quy tắc đó.

Nếu sort list bằng một comparator, thứ tự duyệt sau đó phản ánh dãy mới đã được sắp. Nếu thêm một phần tử bất kỳ vào cuối sau khi sort, list vẫn là một dãy có thứ tự theo vị trí nhưng có thể không còn tăng dần theo comparator.

### 5.3. Thứ tự không phải bất biến

Có thể đảo vị trí, đổi chỗ, chèn, xóa hoặc sắp xếp list nếu nguồn hỗ trợ.

“List có thứ tự” nghĩa là mỗi trạng thái của dãy có thứ tự được xác định để thao tác và duyệt. Nó không có nghĩa vị trí của một giá trị không bao giờ thay đổi.

Ví dụ, `Collections.reverse` đảo dãy hiện tại mà không tự sắp theo giá trị. Hợp đồng của thao tác này được mô tả trong [API Collections.reverse](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#reverse(java.util.List)).

## 6. Thứ tự duyệt gắn với dãy hiện tại

### 6.1. Duyệt tiến bằng iterator hoặc for-each

Iterator của List trả phần tử theo thứ tự của dãy. Vòng for-each tiến thông thường cũng phản ánh thứ tự đó.

Với:

```text
[X, M, C, B, X]
```

lượt duyệt tiến đi qua X, M, C, B rồi X. Lần xuất hiện cuối không bị bỏ vì bằng lần đầu.

Biến trong for-each là phần tử của lượt hiện tại, không phải chỉ số. Nếu cần cả vị trí để hiển thị, có thể dùng cách duyệt có theo dõi chỉ số phù hợp, nhưng không suy ra kiểu phần tử là một mã vị trí.

### 6.2. Duyệt theo chỉ số

Đoạn minh họa trong phương thức:

```java
for (int i = 0; i < values.size(); i++) {
    System.out.println(i + ": " + values.get(i));
}
```

Nó diễn đạt rõ vị trí đang đọc.

Tuy nhiên, interface List không bảo đảm mọi get(index) đều có chi phí hằng số. Việc duyệt bằng chỉ số lặp đi lặp lại có thể không phù hợp với một triển khai truy cập tuần tự.

ArrayList dùng cấu trúc hỗ trợ truy cập theo chỉ số hiệu quả; LinkedList có thể phải đi qua các nút để tới vị trí. Khi không cần chỉ số và không biết triển khai, iterator hoặc for-each thường thể hiện đúng ý định duyệt hơn. Xem [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) và [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

### 6.3. Duyệt ngược không nhất thiết sửa dãy

ListIterator hỗ trợ previous để duyệt ngược. Có thể bắt đầu tại ranh giới sau phần tử cuối bằng:

```java
ListIterator<String> iterator = values.listIterator(values.size());

while (iterator.hasPrevious()) {
    System.out.println(iterator.previous());
}
```

Chỉ đi ngược qua các phần tử không tự đảo cấu trúc list. Đây là thay đổi hướng duyệt, khác với gọi Collections.reverse để đổi dãy. Xem [API ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html).

## 7. Chương trình hoàn chỉnh về vị trí và thứ tự

Lưu vào **ListPositionDemo.java**. Chương trình tương thích Java 17 trở lên:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class ListPositionDemo {
    public static void main(String[] args) {
        List<String> values =
                new ArrayList<>(List.of("C", "A", "C", "B"));

        System.out.println("Initial: " + values);
        System.out.println("Size: " + values.size());
        System.out.println("Index 2: " + values.get(2));
        System.out.println("First/last C: "
                + values.indexOf("C") + "/" + values.lastIndexOf("C"));

        values.add(1, "X");
        System.out.println("After insert: " + values);

        String previous = values.set(2, "M");
        System.out.println("Replaced value: " + previous);
        System.out.println("After set: " + values);

        String removed = values.remove(0);
        values.add("X");
        System.out.println("Removed at index 0: " + removed);
        System.out.println("Current: " + values);

        List<String> forward = new ArrayList<>();
        for (String value : values) {
            forward.add(value);
        }
        System.out.println("Forward traversal: " + forward);

        List<String> backward = new ArrayList<>();
        ListIterator<String> iterator = values.listIterator(values.size());
        while (iterator.hasPrevious()) {
            backward.add(iterator.previous());
        }
        System.out.println("Backward traversal: " + backward);

        List<String> sameSequence = new LinkedList<>(values);
        System.out.println("ArrayList equals LinkedList: "
                + values.equals(sameSequence));

        List<String> reversedCopy = new ArrayList<>(values);
        Collections.reverse(reversedCopy);
        System.out.println("Equals reversed sequence: "
                + values.equals(reversedCopy));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListPositionDemo.java
java ListPositionDemo
```

Stdout:

```text
Initial: [C, A, C, B]
Size: 4
Index 2: C
First/last C: 0/2
After insert: [C, X, A, C, B]
Replaced value: A
After set: [C, X, M, C, B]
Removed at index 0: C
Current: [X, M, C, B, X]
Forward traversal: [X, M, C, B, X]
Backward traversal: [X, B, C, M, X]
ArrayList equals LinkedList: true
Equals reversed sequence: false
```

### 7.1. Vị trí và thay đổi dãy

Ban đầu C nằm ở 0 và 2. Chèn X tại 1 làm dãy tăng từ bốn lên năm phần tử.

Set tại 2 thay A bằng M và trả A. Nó không thêm vị trí thứ sáu.

Remove tại 0 trả C. Sau đó nối thêm X, kết quả có hai lần xuất hiện của X ở đầu và cuối.

### 7.2. Hai hướng duyệt

Lượt tiến thu đúng dãy hiện tại.

Lượt ngược bắt đầu ở ranh giới size và lấy previous liên tiếp, cho `[X, B, C, M, X]`. List nguồn vẫn là `[X, M, C, B, X]`; hướng duyệt không tự sửa nguồn.

### 7.3. List khác lớp triển khai vẫn có thể bằng nhau

ArrayList và LinkedList trong ví dụ chứa cùng dãy phần tử nên equals trả true.

Bản đảo có cùng các giá trị và số lần xuất hiện nhưng khác thứ tự, nên không bằng list ban đầu.

Điều này dẫn đến một đặc điểm quan trọng: equality của List mang ngữ nghĩa **dãy**, không chỉ ngữ nghĩa “có cùng tập giá trị”.

## 8. Thứ tự và số lần xuất hiện tham gia equality

Hai list bằng nhau khi cùng kích thước và các phần tử tương ứng ở cùng vị trí bằng nhau theo hợp đồng.

Ví dụ:

```text
[A, B, A] bằng [A, B, A]
[A, B, A] khác [A, A, B]
[A, B, A] khác [A, B]
```

Không cần hai list thuộc cùng một lớp triển khai. Nhưng thứ tự và số lần xuất hiện không thể bỏ qua.

Hệ quả thực tế:

- Khi kiểm thử một kết quả List, phải quyết định có cần xác nhận đúng thứ tự không.
- Nếu nghiệp vụ không quan tâm thứ tự, không nên dùng List.equals rồi ngạc nhiên vì kết quả false.
- Nếu chuyển List sang Set để so sánh, các phần tử trùng có thể bị gộp; phép chuyển đổi đó thay đổi câu hỏi đang được kiểm tra.
- Nếu hai list bằng nhau, hashCode của chúng phải tương thích theo hợp đồng; không nên tự thay equality bằng phép đếm phần tử duy nhất.

Các phương thức tìm phần tử, equals và hashCode của List được mô tả trong [API List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

## 9. Chương trình hoàn chỉnh về trùng lặp và giới hạn của nguồn

Lưu vào **ListDuplicateDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListDuplicateDemo {
    record Entry(String code) {}

    private static List<String> codes(List<Entry> entries) {
        return entries.stream().map(Entry::code).toList();
    }

    public static void main(String[] args) {
        Entry first = new Entry("A");
        Entry last = new Entry("A");
        List<Entry> entries =
                new ArrayList<>(List.of(first, new Entry("B"), last));

        System.out.println("Same reference: " + (first == last));
        System.out.println("Equal values: " + first.equals(last));
        System.out.println("Entries: " + codes(entries));
        System.out.println("First/last equal A: "
                + entries.indexOf(new Entry("A")) + "/"
                + entries.lastIndexOf(new Entry("A")));

        boolean removed = entries.remove(new Entry("A"));
        System.out.println("Removed by value: " + removed);
        System.out.println("After removal: " + codes(entries));

        List<String> nulls =
                new ArrayList<>(Arrays.asList("A", null, null));
        System.out.println("Null list size: " + nulls.size());
        System.out.println("First/last null: "
                + nulls.indexOf(null) + "/" + nulls.lastIndexOf(null));

        List<String> readOnly = List.of("A", "A", "B");
        System.out.println("Read-only duplicates: " + readOnly);

        try {
            readOnly.set(0, "C");
        } catch (UnsupportedOperationException ex) {
            System.out.println("Read-only set: " + ex.getClass().getSimpleName());
        }

        try {
            List.of("A", null);
        } catch (NullPointerException ex) {
            System.out.println("List.of null: " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListDuplicateDemo.java
java ListDuplicateDemo
```

Stdout:

```text
Same reference: false
Equal values: true
Entries: [A, B, A]
First/last equal A: 0/2
Removed by value: true
After removal: [B, A]
Null list size: 3
First/last null: 1/2
Read-only duplicates: [A, A, B]
Read-only set: UnsupportedOperationException
List.of null: NullPointerException
```

### 9.1. Hai đối tượng nhưng cùng giá trị

`first` và `last` được tạo bằng hai lần new, nên `first == last` là false.

Equals của record so thành phần code, nên chúng bằng nhau. List vẫn giữ cả hai và indexOf/lastIndexOf tìm được ở hai vị trí 0 và 2.

Lệnh remove nhận một Entry mới bằng giá trị, loại lần xuất hiện đầu. Dãy còn B rồi A.

### 9.2. Null được tính như một phần tử khi nguồn hỗ trợ

ArrayList trong ví dụ chứa hai null, size là 3 và hai chỉ số null là 1 và 2.

Điều này không chứng minh mọi List đều chấp nhận null. List.of từ chối null ngay khi tạo trong ví dụ.

### 9.3. Có thứ tự và phần tử trùng không đòi list phải cho sửa

`List.of("A", "A", "B")` có một dãy xác định và giữ cả hai A. Tuy nhiên, nó không cho set, add hoặc remove.

Khả năng biểu diễn một dãy, chính sách null và khả năng sửa đổi là các đặc tính phải kiểm tra riêng. Không nên kết luận “khai báo List nên chắc chắn sửa được”.

## 10. Các ranh giới dễ nhầm

### 10.1. List có vị trí khác với Set có thứ tự duyệt

Một LinkedHashSet có thể giữ thứ tự thêm của các phần tử duy nhất, nhưng không trở thành List. Nó không cung cấp get(index) của List và không giữ nhiều phần tử bằng nhau theo ngữ nghĩa tập hợp.

Nếu chuyển `[A, B, A]` thành tập hợp duy nhất, dữ liệu về lần phát A thứ hai bị mất. Giữ thứ tự các giá trị còn lại không khôi phục được thông tin đó. Xem [API LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html).

### 10.2. List không đồng nghĩa ArrayList

List là hợp đồng. ArrayList là một cách tổ chức dữ liệu.

Các phép đọc theo vị trí và thứ tự dãy không yêu cầu mọi triển khai phải dùng mảng bên trong. Chọn triển khai còn cần dựa vào kiểu truy cập, cập nhật và yêu cầu đồng thời; bài này tập trung vào ngữ nghĩa chung của list.

### 10.3. Thứ tự của nguồn sao chép có ý nghĩa

Khi tạo một danh sách từ một Collection khác, thứ tự nhận được phụ thuộc thứ tự mà nguồn cung cấp trong phép sao chép.

Một ArrayList mới không thể tự khôi phục “thứ tự thêm ban đầu” nếu nguồn không còn giữ thông tin đó. Muốn một thứ tự nghiệp vụ cụ thể, phải cung cấp dữ liệu hoặc quy tắc sắp xếp tương ứng.

## 11. Lỗi thực hành thường gặp

| Lỗi | Vì sao sai | Cách nghĩ đúng |
| --- | --- | --- |
| Dùng get(size()) | Vị trí cuối là size - 1 | Kiểm tra rỗng và miền chỉ số |
| Dùng add(index, value) để thay phần tử | Add chèn một vị trí mới | Dùng set nếu cần thay tại chỗ |
| Lưu chỉ số như mã định danh | Chèn/xóa phía trước làm chỉ số đổi | Chỉ số thuộc trạng thái hiện tại |
| Nghĩ List tự loại trùng | Triển khai thông dụng giữ các lần xuất hiện | Chọn hoặc kiểm tra ràng buộc duy nhất riêng |
| Nghĩ remove(value) xóa mọi bản sao | Nó loại lần xuất hiện đầu phù hợp | Dùng thao tác lọc khi cần loại tất cả |
| Nghĩ có thứ tự nghĩa là đã tăng dần | Thứ tự dãy khác quy tắc sắp xếp | Sort khi có yêu cầu và comparator rõ |
| Nghĩ hai list cùng giá trị duy nhất là equals | List xét từng vị trí và số lần xuất hiện | So sánh đúng mô hình dữ liệu |
| Nghĩ get(index) luôn O(1) | Chi phí phụ thuộc triển khai | Chọn cách duyệt phù hợp |
| Nghĩ List nào cũng cho null và cho sửa | Những bảo đảm đó không đến từ tên kiểu | Đọc nguồn tạo và lớp cụ thể |

### Cẩn thận overload remove với số nguyên

Trong `List<Integer>`:

```java
numbers.remove(1);
```

chọn thao tác xóa theo chỉ số int, không có nghĩa “xóa giá trị Integer bằng 1”.

Muốn xóa theo giá trị có thể viết:

```java
numbers.remove(Integer.valueOf(1));
```

Hai cách đặt ra hai câu hỏi khác nhau. Khi cần xóa một lần xuất hiện đang được duyệt, chọn API duyệt có hỗ trợ thao tác phù hợp.

### Cẩn thận khi xóa và tiếp tục dùng chỉ số

Khi xóa một phần tử trong vòng lặp tăng chỉ số, phần tử phía sau có thể chuyển vào vị trí vừa xóa. Nếu tăng chỉ số ngay mà không thiết kế lại điều kiện, một phần tử có thể bị bỏ qua.

Đây là hệ quả trực tiếp của mô hình vị trí. Dùng removeIf hoặc iterator có hỗ trợ xóa thường rõ hơn khi mục tiêu là lọc dữ liệu. Không sửa cấu trúc list tùy tiện trong for-each rồi dựa vào việc một lần chạy không báo lỗi.

## 12. Cách chọn List theo nhu cầu dữ liệu

List thường phù hợp khi một hoặc nhiều điều sau đúng:

- Thứ tự các lần xuất hiện cần được giữ.
- Cùng một giá trị có thể xuất hiện nhiều lần với ý nghĩa riêng.
- Cần thao tác theo vị trí hiện tại.
- Cần trả một kết quả có thứ tự rõ ràng cho bên sử dụng.
- Equality theo cả dãy phản ánh đúng điều ứng dụng cần so sánh.

Nếu chỉ cần kiểm tra giá trị duy nhất hoặc tra cứu ổn định bằng khóa, hãy đánh giá một mô hình khác thay vì ép mọi yêu cầu vào chỉ số List.

Ngay cả khi chọn đúng interface, vẫn phải xác định nguồn có thể sửa hay không, chấp nhận null hay không và yêu cầu đồng thời ra sao.

## 13. Bài tập tự kiểm tra

1. Với `[A, B, A]`, nêu size, indexOf(A) và lastIndexOf(A).
2. Chèn X tại index 1 rồi xóa index 0. Hãy viết dãy và mọi chỉ số mới sau từng thao tác.
3. Giải thích khác biệt giữa add(1, X) và set(1, X) trên cùng một list có thể sửa.
4. Hai ArrayList và LinkedList chứa cùng dãy có thể bằng nhau không? Nếu đổi thứ tự một list thì sao?
5. Vì sao hai đối tượng khác tham chiếu nhưng bằng equals vẫn có thể cùng tồn tại trong list?
6. Đổi chương trình đầu để chỉ duyệt ngược nhưng không gọi Collections.reverse. Nguồn có bị đảo không?
7. Với một List.of giữ hai A, vì sao vừa có phần tử trùng vừa không cho sửa là hai đặc điểm không mâu thuẫn?
8. Nếu cần lưu một danh sách phát có bài hát lặp, điều gì bị mất khi chuyển nó thành Set?

Mô hình cần giữ là một dãy gồm các lần xuất hiện tại vị trí hiện tại. Truy cập, chèn, xóa, tìm kiếm theo giá trị, equality và lượt duyệt đều dựa trên mô hình đó, dù cách lưu trữ bên trong của từng lớp có thể khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-002">get, set, add theo chỉ số và dịch chuyển phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# get, set, add theo chỉ số và dịch chuyển phần tử

`List<E>` mô tả một dãy có vị trí. Khi làm việc theo chỉ số, ba thao tác có ý nghĩa khác nhau:

- `get(index)`: đọc phần tử đang có tại một vị trí.
- `set(index, value)`: thay phần tử ở một vị trí đã tồn tại.
- `add(index, value)`: chèn một phần tử mới vào dãy.

Chèn không phải thay thế. Thay thế không phải thêm một phần tử. Phân biệt đúng ba ý nghĩa này giúp tránh lỗi sai kích thước, mất dữ liệu hoặc dùng một chỉ số đã không còn trỏ đến phần tử dự định.

Ví dụ xuyên suốt dùng một danh sách có thể chỉnh sửa:

~~~java
// Đoạn mã trong một phương thức; cần import java.util.List và java.util.ArrayList.
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
~~~

## 1. Chỉ số phần tử và vị trí chèn

Java dùng chỉ số bắt đầu từ `0`:

~~~text
Phần tử: A  B  C
Chỉ số:  0  1  2
size:    3
~~~

Danh sách có `n` phần tử thì:

- Chỉ số phần tử hợp lệ là từ `0` đến `n - 1`.
- Vị trí chèn hợp lệ là từ `0` đến `n`.

Có thể hình dung các khoảng trống để chèn:

~~~text
Khoảng:  0   1   2   3
         | A | B | C |
~~~

Khoảng `3` ở cuối hợp lệ để chèn, nhưng không có phần tử nào ở chỉ số `3` để đọc hoặc thay thế.

| Thao tác | Miền chỉ số hợp lệ khi size = n | Giá trị trả về | Kích thước sau khi thành công |
| --- | --- | --- | --- |
| `get(index)` | `0 <= index < n` | Phần tử ở vị trí đó | `n` |
| `set(index, value)` | `0 <= index < n` | Phần tử cũ bị thay | `n` |
| `add(index, value)` | `0 <= index <= n` | `void` | `n + 1` |

Bảng nói về tác động khi thao tác được nguồn hỗ trợ và hoàn tất thành công. Khả năng chỉnh sửa là điều kiện riêng, không chỉ dựa vào chỉ số hợp lệ. Các hợp đồng được nêu trong [List API của Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

## 2. get: đọc một phần tử, không xóa hay dịch dãy

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));

String result = values.get(1);

System.out.println(result);        // B
System.out.println(values);        // [A, B, C]
System.out.println(values.size()); // 3
~~~

`get(1)` trả B, nhưng B vẫn nằm ở chỉ số `1`. Danh sách không bị tiêu thụ như một lượt duyệt iterator và cũng không bị xóa phần tử như một hàng đợi khi lấy đầu.

Có thể gọi `get(1)` nhiều lần để đọc lại vị trí đó, miễn danh sách và nội dung tại vị trí chưa thay đổi.

### get không trả null để báo chỉ số không tồn tại

~~~java
// values có size bằng 3.
values.get(3);  // IndexOutOfBoundsException
values.get(-1); // IndexOutOfBoundsException
~~~

Nếu nguồn cho phép phần tử `null`, `get` ở một chỉ số hợp lệ có thể trả `null` như dữ liệu. Điều đó khác với chỉ số không hợp lệ.

Không kiểm tra “vị trí có tồn tại không” bằng cách gọi `get` rồi so sánh với `null`. Với thao tác đọc thông thường, miền hợp lệ được xác định bằng `size()` và chỉ số.

Java không dùng `get(-1)` để lấy phần tử cuối như một số ngôn ngữ khác. Nếu cần phần tử cuối theo API theo chỉ số, chỉ số là `size() - 1` và phải có ít nhất một phần tử.

### Giá trị trả về có thể là tham chiếu dùng chung

Với phần tử là đối tượng, `get` trả tham chiếu tới phần tử, không tự sao chép sâu đối tượng đó.

Nếu đối tượng cho phép cập nhật nội dung, sửa đối tượng đã lấy có thể được quan sát qua danh sách vì danh sách vẫn giữ cùng tham chiếu. Ngược lại, gán biến cục bộ sang một đối tượng mới không tự thay ô trong danh sách.

Với ví dụ String, ta dùng các giá trị bất biến để tập trung vào thứ tự vị trí. Không suy ra mọi phần tử của mọi List cũng bất biến.

## 3. set: thay tại vị trí có sẵn

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));

String old = values.set(1, "B*");

System.out.println(old);           // B
System.out.println(values);        // [A, B*, C]
System.out.println(values.size()); // 3
~~~

Ý nghĩa:

~~~text
Trước:
index  0   1   2
       A   B   C

set(1, "B*")

Sau:
index  0   1   2
       A   B*  C
~~~

A và C giữ nguyên chỉ số. Không có phần tử mới được chèn, không có dịch chuyển phần tử phía sau và size vẫn là `3`.

`set` trả phần tử cũ. Điều này có thể hữu ích nếu cần ghi nhận giá trị trước khi cập nhật hoặc thực hiện một quyết định dựa trên giá trị đã bị thay.

### set không tự mở rộng danh sách

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));

// Không hợp lệ: chỉ số 3 chưa có phần tử.
values.set(3, "D");
~~~

Không có quy tắc “set vào đúng cuối thì tự thêm”. Muốn tạo vị trí mới, phải chọn thao tác thêm.

Tương tự, không thể dùng set ở chỉ số `10` để tự tạo các vị trí trống từ `3` đến `9`. Java List không mặc nhiên là một bảng thưa tự điền các khoảng thiếu.

### set không tự cập nhật biến đã đọc trước đó

~~~java
List<String> values = new ArrayList<>(List.of("A", "B"));
String saved = values.get(0);

values.set(0, "NEW");

System.out.println(saved);         // A
System.out.println(values.get(0));  // NEW
~~~

`saved` không phải một liên kết động tới “ô số 0”. Nó giữ giá trị tham chiếu nhận được tại lần `get`. Thay tham chiếu mà danh sách lưu không tự gán lại mọi biến đã từng đọc phần tử cũ.

Nếu biến khác trỏ tới chính danh sách:

~~~java
List<String> alias = values;
values.set(0, "HEAD");

System.out.println(alias.get(0)); // HEAD
~~~

`alias` nhìn thấy thay đổi vì hai biến đang tham chiếu cùng đối tượng List, không phải hai bản sao.

## 4. add theo chỉ số: chèn và dịch phần đuôi sang phải

~~~java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));
values.add(1, "X");

System.out.println(values);        // [A, X, B, C]
System.out.println(values.size()); // 4
~~~

`add(1, "X")` đặt X ở chỉ số `1`, trước phần tử trước đó đang ở chỉ số `1`.

~~~text
Trước:
index  0   1   2
       A   B   C

add(1, "X")

Sau:
index  0   1   2   3
       A   X   B   C
~~~

B chuyển từ `1` sang `2`; C chuyển từ `2` sang `3`. A đứng trước điểm chèn nên vẫn ở `0`.

Quy tắc vị trí với chỉ số chèn `p`:

- Các phần tử cũ ở chỉ số nhỏ hơn `p` giữ chỉ số.
- Phần tử mới nhận chỉ số `p`.
- Các phần tử cũ từ `p` trở về sau tăng chỉ số thêm một.

Thứ tự tương đối của các phần tử cũ không thay đổi. B vẫn trước C; chỉ có X được chen vào trước B.

### Chèn ở đầu

~~~java
values.add(0, "HEAD");
~~~

Tất cả phần tử cũ tăng chỉ số thêm một. Đây là lý do lưu một chỉ số rồi tiếp tục dùng nó sau khi chèn ở đầu có thể đọc nhầm phần tử.

### Chèn ở cuối

~~~java
values.add(values.size(), "TAIL");
~~~

Không có phần tử cũ nào ở phía sau để dịch chỉ số. Phần tử mới nhận chỉ số bằng size cũ.

Nếu size ban đầu là `3`, phần tử thêm cuối ở chỉ số `3`, rồi size mới là `4`.

### Chèn vào danh sách rỗng

~~~java
List<String> values = new ArrayList<>();
values.add(0, "first");

System.out.println(values); // [first]
~~~

Với size `0`, chỉ có một vị trí chèn hợp lệ là `0`. Nhưng không có chỉ số hợp lệ nào để gọi get hoặc set trước khi thêm.

## 5. Hai overload add có kiểu trả về khác nhau

| Lời gọi | Ý nghĩa | Kiểu trả về |
| --- | --- | --- |
| `add(element)` | Thêm vào cuối | `boolean` |
| `add(index, element)` | Chèn tại vị trí xác định | `void` |

Ví dụ:

~~~java
List<String> values = new ArrayList<>(List.of("A", "B"));

boolean changed = values.add("C");
values.add(1, "X");

System.out.println(changed); // true
System.out.println(values);  // [A, X, B, C]
~~~

Không gán kết quả của overload hai tham số vào biến:

~~~java
// Không biên dịch: add(index, element) trả void.
// boolean changed = values.add(1, "X");
~~~

Về nội dung kết quả của danh sách thông thường hỗ trợ thêm, `add(e)` có tác dụng thêm cuối như `add(size(), e)`. Nhưng chúng là hai chữ ký khác nhau, với kiểu trả về khác nhau.

### List<Integer>: vị trí vẫn là tham số thứ nhất

~~~java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

numbers.add(1, 99);
System.out.println(numbers); // [10, 99, 20, 30]
~~~

Số `1` là chỉ số chèn, `99` là phần tử được boxing thành Integer. Không có cách hiểu “chèn 1 tại giá trị 99” chỉ vì cả hai tham số đều viết bằng số.

Tương tự:

~~~java
int old = numbers.set(2, 77);

System.out.println(old);     // 20
System.out.println(numbers); // [10, 99, 77, 30]
~~~

Chỉ số nói vị trí trong dãy, không nói giá trị cần tìm.

## 6. Nhiều lần chèn và sự dịch chuyển của chỉ số

Bắt đầu:

~~~text
[A, B, C]
~~~

Hai lần chèn tại cùng chỉ số:

~~~java
values.add(1, "X");
values.add(1, "Y");
~~~

Kết quả:

~~~text
[A, Y, X, B, C]
~~~

Lần đầu X ở chỉ số `1`. Lần sau Y chèn trước phần tử hiện ở chỉ số `1`, chính là X. Vì vậy Y đứng trước X.

Nếu muốn X rồi Y:

~~~java
values.add(1, "X");
values.add(2, "Y");
~~~

Kết quả từ nguồn ban đầu là:

~~~text
[A, X, Y, B, C]
~~~

Cần tính chỉ số theo trạng thái danh sách **sau mỗi bước**, không theo một ảnh ban đầu đã hết đúng.

Với nhu cầu chèn cả một dãy tại một vị trí, API như `addAll(index, collection)` có thể diễn đạt ý định rõ hơn. Khi chèn nhiều lần, phải kiểm tra cả nội dung lẫn thứ tự của những phần tử thêm, không chỉ size cuối.

### Chỉ số không phải định danh ổn định

Trong chương trình hoàn chỉnh:

~~~text
[A, B*, C]        -> B* ở 1
[A, X, B*, C]     -> B* ở 2
[H, A, X, B*, C]  -> B* ở 3
~~~

B* không đổi nội dung, nhưng chỉ số đổi hai lần.

Nếu ứng dụng lưu “người dùng đang chọn hàng số 1” rồi dữ liệu được chèn trước đó, vị trí `1` có thể không còn là thực thể cũ. Khi cần theo dõi thực thể, dùng định danh thuộc về dữ liệu; khi cần theo dõi vị trí, phải cập nhật vị trí theo đúng quy tắc thay đổi.

`indexOf(value)` có thể tìm một vị trí theo phép so sánh giá trị của danh sách, nhưng không tự trở thành định danh duy nhất. Nếu có phần tử trùng, nó chỉ cho một kết quả theo hợp đồng tìm kiếm của List.

## 7. Vì sao capacity không mở rộng miền chỉ số?

~~~java
ArrayList<String> values = new ArrayList<>(10);

System.out.println(values.size()); // 0

// values.set(0, "A"); // IndexOutOfBoundsException
values.add(0, "A");    // Hợp lệ.
~~~

Tham số `10` đặt sức chứa ban đầu của ArrayList, không tạo mười phần tử.

Phân biệt:

- **Size:** số phần tử hiện thuộc danh sách.
- **Capacity:** sức chứa của vùng lưu trữ nội bộ ở một thời điểm.

Miền chỉ số công khai của get/set/add được xác định bởi **size**, không bởi số ô mà mảng nội bộ có thể còn chứa được.

Một mảng `new String[10]` có độ dài `10` và các ô ban đầu mang giá trị `null`. Một ArrayList có capacity `10` nhưng size `0` vẫn là danh sách rỗng. Hai cách khởi tạo không có cùng ý nghĩa.

Không dùng nhiều lần set để lấp sức chứa đã đặt trước. Nếu ý định là thêm phần tử, hãy dùng add theo đúng miền vị trí chèn.

## 8. Dịch chuyển logic và cách lưu trữ vật lý

Hợp đồng List xác định các chỉ số sau khi chèn. Nó không bắt buộc mọi lớp List phải lưu bằng mảng và sao chép phần tử theo cùng một thuật toán.

### Với ArrayList

Có thể hình dung một mảng nội bộ đang chứa:

~~~text
A B C D _
~~~

Chèn X tại chỉ số `2` cần giữ C và D, tạo chỗ cho X:

~~~text
Trước: A B C D _
Sau:   A B X C D
~~~

Phần đuôi từ vị trí chèn phải được dời trong mảng lưu trữ. Nếu vùng lưu không còn đủ, còn có chi phí tạo vùng lưu lớn hơn và chuyển dữ liệu sang đó.

Đây là mô hình để hiểu chi phí, không phải hướng dẫn tự truy cập mảng nội bộ của ArrayList. Tốc độ tăng capacity là chi tiết triển khai; không dựa vào một hệ số cụ thể để viết logic ứng dụng. Xem [ArrayList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

### Với LinkedList

LinkedList có thể tìm tới nút ở vị trí cần thiết rồi thay đổi các liên kết. Các phần tử phía sau vẫn có chỉ số logic tăng một, dù không phải dịch từng phần tử sang một ô mảng khác.

Do đó câu “chèn làm dịch các chỉ số phía sau” đúng ở mức List. Câu “mọi List đều phải sao chép toàn bộ phần đuôi trong bộ nhớ” không đúng.

Việc tìm nút theo chỉ số cũng có chi phí. Không từ “thêm nút nhanh khi đã biết vị trí nút” suy ra `add(index, value)` ở một chỉ số bất kỳ luôn là hằng số. Xem đặc điểm truy cập theo chỉ số trong [LinkedList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

### Bảng chi phí để định hướng

Với các triển khai thông thường, n là số phần tử, p là vị trí:

| Thao tác | ArrayList | LinkedList |
| --- | --- | --- |
| get/set tại chỉ số bất kỳ | Truy cập vị trí mảng, thường O(1) | Cần tìm nút, O(n) trong trường hợp xấu nhất |
| Chèn đầu | Phải dời phần đuôi, O(n) | Có thể thao tác trực tiếp ở đầu, O(1) |
| Chèn giữa bằng chỉ số | Chi phí dời phần đuôi, thường O(n - p), có thể thêm chi phí mở rộng | Tìm vị trí có thể O(n), rồi nối nút |
| Thêm cuối | O(1) khấu hao, nhưng một lần mở rộng có thể O(n) | Có thể thao tác trực tiếp ở cuối, O(1) |

Bảng không phải lời khuyên luôn chọn lớp này hay lớp kia. Kích thước dữ liệu, kiểu truy cập, tính cục bộ bộ nhớ và toàn bộ mẫu xử lý đều ảnh hưởng lựa chọn.

Nếu chỉ cần duyệt toàn bộ một List mà không biết lớp thực tế, for-each hoặc iterator tránh việc ép thuật toán phụ thuộc truy cập ngẫu nhiên qua get. Lặp `get(i)` trên LinkedList có thể làm tổng chi phí tăng lên O(n²).

## 9. Chỉ số đúng chưa đủ: nguồn có cho sửa không?

`get` là thao tác đọc. `set` và `add` là các thao tác thay đổi có thể không được nguồn hỗ trợ.

| Nguồn | get ở chỉ số hợp lệ | set | add làm tăng kích thước |
| --- | --- | --- | --- |
| ArrayList có thể sửa | Được | Được | Được |
| LinkedList có thể sửa | Được | Được | Được |
| Arrays.asList(array) | Được | Được; thay đổi phản ánh về mảng | Không hỗ trợ |
| List.of / List.copyOf | Được | Không hỗ trợ | Không hỗ trợ |
| Góc nhìn Collections.unmodifiableList | Được | Không hỗ trợ qua góc nhìn | Không hỗ trợ qua góc nhìn |

Ví dụ danh sách có kích thước cố định:

~~~java
String[] array = {"L", "R"};
List<String> fixed = Arrays.asList(array);

fixed.set(0, "L*");

System.out.println(fixed);                  // [L*, R]
System.out.println(Arrays.toString(array)); // [L*, R]

// fixed.add(1, "M"); // UnsupportedOperationException
~~~

Nó có thể thay một phần tử nhưng không đổi kích thước. Không gọi mọi danh sách từ `Arrays.asList` là hoàn toàn không sửa được. Xem [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

Với danh sách không cho sửa đổi, muốn chỉnh sửa một dãy mới có thể tạo bản sao:

~~~java
List<String> editable = new ArrayList<>(source);
editable.add(0, "new");
~~~

Tạo bản sao không biến nguồn thành có thể sửa; nó tạo một đối tượng danh sách khác. Các phần tử dạng đối tượng vẫn có thể được chia sẻ do đây không phải sao chép sâu.

### Giới hạn phần tử là điều kiện riêng

Một nguồn có thể giới hạn `null`, kiểu runtime hoặc giá trị hợp lệ. Không suy ra “chỉ số đúng và có hỗ trợ add” nghĩa mọi phần tử đều được chấp nhận.

Với generic, trình biên dịch giúp kiểm tra kiểu phần tử, nhưng các lớp bao bọc kiểm tra hoặc nguồn chuyên biệt còn có thể có quy tắc runtime riêng. Hãy phân biệt lỗi miền chỉ số với lỗi thao tác không được hỗ trợ và lỗi dữ liệu bị từ chối.

## 10. Chèn khi đang duyệt: tránh quản lý sai tiến độ

Một vòng lặp đọc theo chỉ số:

~~~java
for (int i = 0; i < values.size(); i++) {
    String value = values.get(i);
    System.out.println(value);
}
~~~

Khi danh sách không bị chỉnh sửa trong vòng lặp, ý nghĩa vị trí thường dễ hiểu. Nhưng nếu chèn trước phần tử hiện tại:

~~~java
// Minh họa một bẫy logic, không phải mẫu xử lý nên dùng.
for (int i = 0; i < values.size(); i++) {
    if (needsMarker(values.get(i))) {
        values.add(i, "marker");
    }
}
~~~

`needsMarker` là một điều kiện minh họa do ứng dụng cung cấp.

Sau khi chèn tại `i`, phần tử vừa được kiểm tra chuyển sang `i + 1`. Vòng lặp tăng i rồi có thể kiểm tra lại chính phần tử đó. Nếu điều kiện tiếp tục đúng, danh sách có thể liên tục tăng mà thuật toán không tiến qua phần tử gốc như dự định.

Cần thiết kế rõ có muốn duyệt các phần tử mới chèn hay không. Những lựa chọn khác gồm:

- Dùng ListIterator để điều khiển chèn/thay theo vị trí duyệt.
- Tạo danh sách kết quả mới từ nguồn không đổi.
- Thu các thay đổi rồi áp dụng ở giai đoạn sau.
- Tính quy tắc cập nhật chỉ số thật rõ nếu chỉ số là phần thiết yếu của thuật toán.

Không sửa trực tiếp cấu trúc List trong for-each rồi chỉ kiểm tra xem có ngoại lệ hay không. Không có ngoại lệ không chứng minh thuật toán duyệt đúng, và fail-fast không phải công cụ điều khiển việc chèn.

### Chỉ số của view là tương đối với view

Nếu thao tác trên một `subList`, chỉ số `0` là đầu của view đó, không mặc nhiên là đầu danh sách gốc. Thay đổi qua view có thể phản ánh về nguồn phía sau.

Vì vậy, khi truyền một chỉ số giữa các tầng, cần biết nó thuộc dãy nào và tại thời điểm nào. Một số nguyên không tự mang thông tin về nguồn mà nó mô tả.

### Kiểm tra rồi thao tác không tự nguyên tử

Trong môi trường nhiều luồng, kiểm tra `index < size()` rồi gọi `get/set/add` ở bước sau không tự bảo đảm nguồn không bị đổi giữa hai bước. Đây là vấn đề về giao thức truy cập đồng thời, cần giải quyết riêng; các miền chỉ số vẫn đúng nhưng không tạo khóa cho chuỗi thao tác.

## 11. Chương trình hoàn chỉnh

Lưu thành **ListIndexOperationsDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListIndexOperationsDemo {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        show("Initial", values);

        String read = values.get(1);
        System.out.println("get(1) = " + read);
        show("After get", values);

        String replaced = values.set(1, "B*");
        System.out.println("set(1) returned = " + replaced);
        show("After set", values);

        values.add(1, "X");
        show("After add(1, X)", values);
        System.out.println("B* now has index = " + values.indexOf("B*"));

        values.add(0, "H");
        show("After add(0, H)", values);
        System.out.println("B* after another insertion = "
                + values.indexOf("B*"));

        values.add(values.size(), "T");
        show("After add(size, T)", values);
        boolean changed = values.add("U");
        System.out.println("add(U) returned = " + changed);
        show("After append", values);

        String oldFirst = values.get(0);
        List<String> alias = values;
        values.set(0, "HEAD");
        System.out.println("Saved old first = " + oldFirst);
        System.out.println("Alias first = " + alias.get(0));

        expectIndexError("get(size)", () -> values.get(values.size()));
        expectIndexError("set(size)",
                () -> values.set(values.size(), "bad"));
        expectIndexError("add(size + 1)",
                () -> values.add(values.size() + 1, "bad"));
        expectIndexError("get(-1)", () -> values.get(-1));

        ArrayList<String> reserved = new ArrayList<>(10);
        System.out.println("Reserved capacity, size = " + reserved.size());
        expectIndexError("set on empty", () -> reserved.set(0, "first"));
        reserved.add(0, "first");
        show("Empty list after add(0)", reserved);

        List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
        numbers.add(1, 99);
        System.out.println("Integer list after add(1, 99) = " + numbers);
        int oldNumber = numbers.set(2, 77);
        System.out.println("Integer set returned = " + oldNumber);
        System.out.println("Integer list after set(2, 77) = " + numbers);

        String[] array = {"L", "R"};
        List<String> fixed = Arrays.asList(array);
        fixed.set(0, "L*");
        System.out.println("Fixed-size after set = " + fixed);
        System.out.println("Backing array = " + Arrays.toString(array));
        expectUnsupported("Fixed-size indexed add",
                () -> fixed.add(1, "M"));

        List<String> unmodifiable = List.of("A", "B");
        System.out.println("Unmodifiable get(1) = " + unmodifiable.get(1));
        expectUnsupported("Unmodifiable set",
                () -> unmodifiable.set(1, "B*"));
    }

    private static void show(String label, List<?> values) {
        System.out.println(label + " = " + values + "; size=" + values.size());
    }

    private static void expectIndexError(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException exception) {
            System.out.println(label + " -> "
                    + exception.getClass().getSimpleName());
        }
    }

    private static void expectUnsupported(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException exception) {
            System.out.println(label + " -> "
                    + exception.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 ListIndexOperationsDemo.java
java ListIndexOperationsDemo
~~~

Stdout:

~~~text
Initial = [A, B, C]; size=3
get(1) = B
After get = [A, B, C]; size=3
set(1) returned = B
After set = [A, B*, C]; size=3
After add(1, X) = [A, X, B*, C]; size=4
B* now has index = 2
After add(0, H) = [H, A, X, B*, C]; size=5
B* after another insertion = 3
After add(size, T) = [H, A, X, B*, C, T]; size=6
add(U) returned = true
After append = [H, A, X, B*, C, T, U]; size=7
Saved old first = H
Alias first = HEAD
get(size) -> IndexOutOfBoundsException
set(size) -> IndexOutOfBoundsException
add(size + 1) -> IndexOutOfBoundsException
get(-1) -> IndexOutOfBoundsException
Reserved capacity, size = 0
set on empty -> IndexOutOfBoundsException
Empty list after add(0) = [first]; size=1
Integer list after add(1, 99) = [10, 99, 20, 30]
Integer set returned = 20
Integer list after set(2, 77) = [10, 99, 77, 30]
Fixed-size after set = [L*, R]
Backing array = [L*, R]
Fixed-size indexed add -> UnsupportedOperationException
Unmodifiable get(1) = B
Unmodifiable set -> UnsupportedOperationException
~~~

Các hàm bắt ngoại lệ trong chương trình phục vụ kiểm chứng ca lỗi. Nếu thao tác không ném như dự kiến, chương trình chủ động ném AssertionError để ví dụ không âm thầm coi một kết quả khác là đúng.

## 12. Đọc kết quả theo từng thao tác

### Đọc rồi thay thế

get(1) trả B nhưng dãy và size không đổi. set(1, B*) trả B cũ và thay đúng vị trí `1`, vẫn size `3`.

### Chèn ở giữa rồi ở đầu

add(1, X) làm B* từ `1` sang `2`. Thêm H vào đầu làm B* sang `3`. Đây là hai lần đổi chỉ số của cùng nội dung, không phải B* tự bị thay giá trị.

### Hai cách thêm cuối

add(size, T) thêm T vào cuối. add(U) cũng thêm cuối nhưng trả boolean true. Chương trình không gán kết quả của overload hai tham số vì nó trả void.

### Biến đã lưu và alias của danh sách

Biến oldFirst nhận H trước khi phần tử đầu được thay thành HEAD. Nó vẫn giữ H. Biến alias trỏ cùng List nên alias.get(0) nhận HEAD sau thay đổi.

### Miền chỉ số lỗi

get(size), set(size), add(size + 1) và get(-1) đều không nằm trong miền tương ứng. Chương trình chỉ kiểm tra tên loại ngoại lệ, không phụ thuộc toàn văn thông điệp lỗi có thể khác theo phiên bản.

### Capacity ban đầu

new ArrayList(10) chưa tạo phần tử; size vẫn `0`. set(0) thất bại, nhưng add(0) tạo phần tử đầu.

### Danh sách số nguyên

add(1, 99) chèn `99` trước `20`. Sau đó set(2, 77) thay `20` bằng `77` và trả lại `20`. Chỉ số và giá trị số là hai vai trò riêng.

### Nguồn có giới hạn

Arrays.asList cho thay L thành L*, phản ánh về mảng, nhưng từ chối chèn. List.of cho đọc B ở chỉ số hợp lệ nhưng từ chối set. Khả năng sửa không được suy ra chỉ từ kiểu khai báo `List<String>`.

## 13. Cách kiểm tra một thuật toán dùng chỉ số

Với mỗi thao tác, hãy viết trạng thái trước và sau:

1. Size trước thao tác là bao nhiêu?
2. Thao tác cần một phần tử có sẵn hay một khoảng trống để chèn?
3. Chỉ số có trong miền đúng không?
4. Nguồn có hỗ trợ thay đổi không?
5. Phần tử nào đổi giá trị, phần tử nào đổi chỉ số?
6. Chỉ số đang lưu ở nơi khác còn đúng với ý định không?

Các ca nhỏ hữu ích:

- Danh sách rỗng: get/set đều không có chỉ số hợp lệ; add chỉ có vị trí `0`.
- Một phần tử: get/set ở `0`; add ở `0` hoặc `1`.
- Danh sách nhiều phần tử: thao tác đầu, giữa và cuối.
- Chỉ số `-1`, `size()`, `size() + 1`.
- Set rồi kiểm tra phần tử cũ được trả về và size không đổi.
- Add rồi kiểm tra toàn bộ thứ tự phần đuôi, không chỉ phần tử vừa thêm.
- Hai lần chèn cùng vị trí.
- Các phần tử trùng để tránh coi giá trị là định danh duy nhất.
- Nguồn cố định kích thước hoặc không cho sửa đổi.

Khi một chỉ số được tính từ một bước tìm kiếm hoặc lựa chọn của người dùng, hãy kiểm tra nó còn mô tả đúng trạng thái hiện tại trước khi thực hiện cập nhật.

## 14. Lỗi thường gặp

| Hiểu nhầm hoặc cách viết | Vấn đề | Cách sửa |
| --- | --- | --- |
| Dùng set để thêm phần tử ở cuối | Chỉ số chưa có phần tử | Dùng add cho vị trí mới. |
| Dùng add để cập nhật một giá trị | Dữ liệu cũ bị đẩy, size tăng | Dùng set nếu muốn thay thế. |
| Cho rằng get(size) lấy phần tử cuối | Chỉ số cuối là size - 1 khi không rỗng | Phân biệt số lượng với chỉ số lớn nhất. |
| Nghĩ add(size, e) cũng vượt miền | Vị trí chèn được phép bằng size | Dùng miền riêng cho add. |
| Dùng get(-1) | Java không có quy tắc chỉ số âm ở đây | Tính chỉ số cuối và kiểm tra danh sách không rỗng. |
| Đặt capacity rồi gọi set vào phần tử chưa có | Capacity không phải size | Tạo phần tử bằng thao tác thêm. |
| Chèn nhiều lần cùng chỉ số và mong thứ tự thêm được giữ | Mỗi lần mới đều chèn trước vị trí hiện tại | Tăng vị trí hoặc dùng thao tác thêm cả dãy phù hợp. |
| Lưu chỉ số như ID lâu dài | Chèn/xóa/sắp xếp có thể đổi phần tử tại vị trí | Theo dõi định danh dữ liệu hoặc cập nhật vị trí. |
| Mọi List đều truy cập chỉ số O(1) | Hợp đồng List không áp đặt cách lưu | Xét triển khai và mẫu truy cập. |
| Mọi List có thể get thì đều có thể set/add | Khả năng sửa là tùy chọn | Kiểm tra nguồn hoặc tạo bản sao có thể sửa. |
| Muốn loại bỏ ngoại lệ nên đổi sang vòng lặp chỉ số | Vẫn có thể sai về tiến độ hoặc đồng thời | Kiểm tra nội dung và số lần xử lý, không chỉ ngoại lệ. |

## 15. Thực hành

1. Với `[A, B, C, D]`, dự đoán kết quả và size sau get(2), set(2, X), add(2, Y). Ghi riêng giá trị trả về của từng phương thức.
2. Từ danh sách rỗng, tạo `[A, B, C]` bằng add theo chỉ số. Chỉ ra vị trí hợp lệ ở từng bước.
3. Chèn X rồi Y tại cùng chỉ số `0`. Viết thứ tự cuối, sau đó sửa cách gọi để giữ X trước Y.
4. Lưu chỉ số của B trong `[A, B, C]`, thêm hai phần tử trước B rồi quan sát chỉ số cũ trỏ tới đâu.
5. So sánh set và add trên ArrayList, Arrays.asList và List.of. Phân biệt sai chỉ số với thao tác không được hỗ trợ.
6. Viết một hàm nhận List và vị trí chèn do người dùng chọn. Định nghĩa rõ miền vị trí, chính sách lỗi và việc hàm sửa nguồn hay tạo dãy mới.
7. Đánh giá chi phí một vòng get(i) trên ArrayList và LinkedList, rồi chọn cách duyệt khi chỉ cần xử lý mọi phần tử.

Khi đọc một lời gọi theo chỉ số, hãy xác định trước nó đang **đọc**, **thay vị trí có sẵn** hay **tạo vị trí mới**. Sau đó dùng size hiện tại để kiểm tra miền và cập nhật mọi suy luận về những phần tử phía sau điểm chèn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-003">Phân biệt remove(int) với remove(Object)</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt remove(int) với remove(Object)

## 1. Một tên phương thức, hai ý định xóa khác nhau

Một `List` lưu phần tử theo vị trí. Vì vậy, có hai câu hỏi riêng biệt khi muốn xóa:

- “Xóa phần tử đang ở vị trí nào?”: dùng `remove(int index)`.
- “Xóa phần tử có giá trị bằng đối tượng nào?”: dùng `remove(Object o)`.

Đây là hai phương thức nạp chồng (overload): cùng tên `remove` nhưng khác kiểu tham số. Java không xem nội dung danh sách rồi đoán ý định của người viết. Trình biên dịch chọn phương thức từ kiểu của biểu thức gọi và kiểu của đối số.

Ví dụ, danh sách số nguyên `[10, 20, 10, 30]` có thể được nhìn theo hai hàng thông tin:

| Chỉ số | 0 | 1 | 2 | 3 |
|---|---|---|---|---|
| Giá trị | 10 | 20 | 10 | 30 |

`remove(1)` xóa phần tử ở chỉ số 1, tức giá trị 20. Muốn xóa giá trị 10 đầu tiên, viết `remove(Integer.valueOf(10))`. Hai lời gọi này khác nhau ngay cả khi cùng thao tác trên một đối tượng `ArrayList<Integer>`.

Nguy cơ lớn nhất xuất hiện với `List<Integer>`: cả chỉ số và giá trị đều trông giống số nguyên, nên một lời gọi vẫn biên dịch thành công nhưng thực hiện sai điều người viết mong muốn.

## 2. Hợp đồng của từng phương thức

| Thuộc tính | `E remove(int index)` | `boolean remove(Object o)` |
|---|---|---|
| Ý nghĩa đối số | Chỉ số của phần tử cần xóa | Đối tượng dùng để tìm phần tử bằng nó |
| Cách xác định phần tử | Vị trí tính từ 0 | Vị trí đầu tiên thỏa điều kiện bằng nhau |
| Kết quả trả về | Chính phần tử vừa xóa, có kiểu `E` | `true` nếu đã xóa được một phần tử; `false` nếu không tìm thấy |
| Số phần tử bị xóa trong một lời gọi thành công | Một | Một, kể cả khi có nhiều giá trị bằng nhau |
| Khi không có mục tiêu | Chỉ số ngoài phạm vi gây `IndexOutOfBoundsException` | Thường trả `false` khi đối tượng hợp lệ nhưng không có trong danh sách |
| Tác động đến vị trí còn lại | Các phần tử sau vị trí bị xóa chuyển sang chỉ số nhỏ hơn một | Tương tự, sau khi tìm và xóa phần tử đầu tiên phù hợp |

Với xóa theo chỉ số, điều kiện hợp lệ là `0 <= index && index < list.size()`. Chỉ số bằng `size()` không hợp lệ. Đừng áp dụng nhầm quy tắc chèn của `add(index, element)`, vốn có thể cho phép chèn ở cuối.

Với xóa theo đối tượng, hợp đồng của `List` mô tả việc xóa phần tử có chỉ số nhỏ nhất thỏa `Objects.equals(o, get(i))`, nếu tồn tại. Nó dùng quan hệ bằng nhau, không dùng “cùng đối tượng trong bộ nhớ” làm tiêu chuẩn chung. `remove(Object)` cũng không có nghĩa là xóa tất cả phần tử trùng giá trị.

Cả hai là thao tác sửa đổi tùy chọn. Danh sách không hỗ trợ xóa có thể ném `UnsupportedOperationException`. Với `remove(Object)`, một số triển khai còn có thể từ chối kiểu đối số không hợp lệ hoặc `null` bằng ngoại lệ theo hợp đồng API. Không khái quát kết quả của một `ArrayList` thành mọi triển khai `List`. [Hợp đồng List.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#remove(int)), [List.remove(Object)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#remove(java.lang.Object)).

## 3. Vì sao int và Integer chọn hai overload khác nhau?

### 3.1. Đối số int chọn xóa theo chỉ số

Đoạn sau minh họa bên trong một phương thức:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

Integer removed = numbers.remove(1);

// removed = 20
// numbers = [10, 30]
```

Literal `1` có kiểu `int`. Với đối số này, `remove(int)` khớp trực tiếp. Mặc dù Java có thể boxing số nguyên thành `Integer` để truyền vào tham số `Object`, trình biên dịch không cần đến phép chuyển đổi đó khi đã có phương thức phù hợp ở giai đoạn trước.

Đừng đọc `numbers.remove(1)` thành “xóa số 1” chỉ vì `numbers` chứa `Integer`. Trong lời gọi này, số 1 là chỉ số.

### 3.2. Đối số Integer chọn xóa theo đối tượng

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
Integer value = 20;

boolean removed = numbers.remove(value);

// removed = true
// numbers = [10, 30]
```

Đối số `value` có kiểu `Integer`. Nó có thể được chuyển rộng tham chiếu sang `Object` mà không cần unboxing. Phương thức `remove(Object)` vì vậy phù hợp ở giai đoạn gọi nghiêm ngặt. Phương án unboxing `Integer` thành `int` thuộc giai đoạn gọi có cho phép chuyển đổi bổ sung; Java không chuyển sang giai đoạn đó khi đã tìm được phương thức phù hợp trước đó.

Cách giải thích chính xác không phải là “Java luôn ưu tiên kiểu nguyên thủy” hay “Java luôn ưu tiên kiểu bao”. Cần nhìn kiểu đối số và các giai đoạn xác định phương thức áp dụng được. [JLS 15.12.2 về lựa chọn phương thức](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.2), [JLS 5.3 về chuyển đổi khi gọi phương thức](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.3).

### 3.3. Viết rõ ý định với List<Integer>

Nếu giá trị cần tìm đang là `int`, dùng `Integer.valueOf(value)` để xóa theo giá trị:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
int valueToDelete = 20;

boolean found = numbers.remove(Integer.valueOf(valueToDelete));
```

`Integer.valueOf` tạo hoặc cung cấp đối tượng bao mang giá trị đó. Việc có thể tái sử dụng đối tượng trong bộ nhớ đệm không quyết định kết quả xóa; `remove(Object)` dựa trên phép so sánh bằng nhau.

Nếu một biến `Integer` thực sự biểu diễn chỉ số, có thể chuyển rõ sang `int`:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
Integer index = 1;

Integer deleted = numbers.remove(index.intValue());
```

Chỉ làm điều này khi biến thực sự mang ý nghĩa vị trí, đồng thời xử lý khả năng `index == null` nếu đầu vào cho phép `null`. Đổi kiểu để ép một overload không thay cho việc xác định đúng ý nghĩa dữ liệu. [Integer.valueOf(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#valueOf(int)).

### 3.4. Bảng đọc nhanh đối số

Bảng dưới giả sử biến nhận có kiểu khai báo `List<Integer>`, đối tượng thực tế là `ArrayList<Integer>`, và các biến không phải `null` trừ hàng ghi rõ:

| Biểu thức gọi | Kiểu đối số | Overload được chọn | Ý nghĩa |
|---|---|---|---|
| `list.remove(1)` | `int` | `remove(int)` | Xóa chỉ số 1 |
| `list.remove(intValue)` | `int` | `remove(int)` | Xóa chỉ số có trong biến |
| `list.remove(Integer.valueOf(1))` | `Integer` | `remove(Object)` | Xóa giá trị `Integer` bằng 1 đầu tiên |
| `list.remove(integerValue)` | `Integer` | `remove(Object)` | Tìm theo giá trị của đối tượng |
| `list.remove(integerValue.intValue())` | `int` | `remove(int)` | Xóa theo chỉ số sau khi unboxing tường minh |
| `list.remove((int) integerValue)` | `int` | `remove(int)` | Cũng là xóa theo chỉ số; có thể lỗi nếu biến là `null` |
| `list.remove((Object) 1)` | `Object` | `remove(Object)` | Số 1 được boxing thành `Integer`, rồi dùng làm giá trị tìm |
| `list.remove(1L)` | `long` | `remove(Object)` | Boxing thành `Long`; không xóa số `Integer` bằng 1 trong `ArrayList<Integer>` |
| `list.remove(null)` | Kiểu null | `remove(Object)` | Tìm phần tử `null`, nếu triển khai cho phép |
| `list.remove((Integer) null)` | `Integer` | `remove(Object)` | Cũng tìm theo đối tượng; không unboxing tại lời gọi này |

Các đối số `byte`, `short`, `char` có thể được chuyển rộng thành `int`, nên khi gọi qua `List`, chúng chọn `remove(int)`. Ví dụ, một `char` có giá trị `'A'` sẽ được xem như chỉ số số học 65, không phải ký tự cần tìm. Với `List<Character>`, muốn xóa ký tự, truyền một `Character`, chẳng hạn `Character.valueOf('A')`.

Ngược lại, `long` không được tự thu hẹp thành `int` trong lời gọi này. `remove(1L)` vẫn có thể biên dịch vì `Long` là một `Object`. `Integer.valueOf(1).equals(Long.valueOf(1L))` là `false`, dù hai số có cùng giá trị toán học. Kiểu tham số là `Object` cũng có nghĩa rằng tham số kiểu `E = Integer` không bảo đảm mọi đối số truyền vào `remove(Object)` đều là `Integer`.

Với `var`, kiểu vẫn được suy luận lúc biên dịch: `var x = 1;` tạo biến `int`, còn `var x = Integer.valueOf(1);` tạo biến `Integer`. `var` không làm mất sự khác biệt giữa hai trường hợp.

## 4. Kiểu khai báo của biến nhận cũng quan trọng

`List` bổ sung thao tác xóa theo vị trí; giao diện `Collection` có `remove(Object)` nhưng không có `remove(int)`.

Đoạn minh họa:

```java
List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3));
Collection<Integer> view = numbers;

boolean removed = view.remove(1);

// removed = true
// numbers = [2, 3]
```

Qua biến `view` có kiểu `Collection<Integer>`, trình biên dịch chỉ thấy phương thức xóa theo đối tượng. Đối số `int` được boxing thành `Integer`, rồi dùng làm đối tượng tìm. Kết quả là xóa giá trị 1.

Nếu gọi `numbers.remove(1)` trên một danh sách ban đầu khác cũng chứa `[1, 2, 3]`, phương thức được chọn là xóa theo chỉ số và kết quả là `[1, 3]`.

`view` và `numbers` trong ví dụ cùng tham chiếu đến một đối tượng; không có bản sao danh sách nào được tạo khi gán `view = numbers`. Khác biệt nằm ở tập phương thức nhìn thấy tại chỗ gọi. Sau khi overload đã được chọn lúc biên dịch, cơ chế gọi phương thức của đối tượng thực tế mới thực thi phương thức tương ứng. Kiểu thực tế `ArrayList` không tự chọn lại giữa “chỉ số” và “giá trị” lúc chạy. [Collection.remove(Object)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#remove(java.lang.Object)).

Không nên đổi kiểu biến sang `Collection` chỉ để che một overload trong một đoạn mã khó hiểu. Hãy chọn giao diện theo nhu cầu của API, và diễn đạt rõ thao tác xóa giá trị khi đang làm việc với `List<Integer>`.

## 5. Kiểu kết quả nhận về không sửa được một lời gọi sai

Đoạn dưới cố ý không biên dịch:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

boolean removed = numbers.remove(1);
```

`remove(1)` vẫn được chọn là `remove(int)` và trả `Integer`. Java không nhìn biến đích `boolean` rồi quay lại chọn `remove(Object)`.

Sửa theo đúng ý định:

```java
// Muốn xóa giá trị 1:
boolean found = numbers.remove(Integer.valueOf(1));

// Muốn xóa phần tử tại chỉ số 1:
Integer deleted = numbers.remove(1);
```

Trong trường hợp đầu, `false` nghĩa là không tìm thấy giá trị 1. Trong trường hợp sau, kết quả là phần tử bị xóa, không phải cờ thành công. Phân biệt hai dạng kết quả cũng giúp người đọc phát hiện thao tác xóa bị viết nhầm.

Nếu không sử dụng kết quả trả về, nguy cơ lỗi kín càng lớn: `numbers.remove(1);` có thể biên dịch và chạy bình thường, nhưng đã xóa nhầm vị trí. Vì thế, đừng dùng tiêu chí “không báo lỗi biên dịch” để kết luận rằng ý định xóa đã được diễn đạt đúng.

## 6. Chương trình chạy đầy đủ: kiểm chứng lựa chọn overload

Các ví dụ ngắn phía trên minh họa thân một phương thức và cần các import thích hợp. Chương trình sau là tệp độc lập, dùng API có trong Java 17.

Lưu thành `RemoveOverloadDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RemoveOverloadDemo {
    private static ArrayList<Integer> numbers() {
        return new ArrayList<>(List.of(10, 20, 10, 30));
    }

    public static void main(String[] args) {
        ArrayList<Integer> byIndex = numbers();
        Integer removedElement = byIndex.remove(1);
        System.out.println("remove(1) -> " + removedElement);
        System.out.println("byIndex = " + byIndex);

        ArrayList<Integer> byValue = numbers();
        boolean removedValue = byValue.remove(Integer.valueOf(10));
        System.out.println("remove(Integer.valueOf(10)) -> " + removedValue);
        System.out.println("byValue = " + byValue);

        ArrayList<Integer> primitiveArgument =
                new ArrayList<>(List.of(1, 2, 3));
        int index = 1;
        System.out.println("int argument -> " + primitiveArgument.remove(index));
        System.out.println("primitiveArgument = " + primitiveArgument);

        ArrayList<Integer> wrapperArgument =
                new ArrayList<>(List.of(1, 2, 3));
        Integer value = 1;
        System.out.println("Integer argument -> " + wrapperArgument.remove(value));
        System.out.println("wrapperArgument = " + wrapperArgument);

        ArrayList<Integer> differentWrapper =
                new ArrayList<>(List.of(1, 2, 3));
        System.out.println("long argument -> " + differentWrapper.remove(1L));
        System.out.println("differentWrapper = " + differentWrapper);

        List<Integer> backingList = new ArrayList<>(List.of(1, 2, 3));
        Collection<Integer> collectionView = backingList;
        System.out.println("Collection receiver -> " + collectionView.remove(1));
        System.out.println("backingList = " + backingList);

        List<String> words = new ArrayList<>(List.of("A", "B", "C"));
        System.out.println("String list, int argument -> " + words.remove(1));
        System.out.println("words = " + words);
        System.out.println("String list, String argument -> " + words.remove("C"));
        System.out.println("words = " + words);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RemoveOverloadDemo.java
java RemoveOverloadDemo
```

Kết quả chuẩn:

```text
remove(1) -> 20
byIndex = [10, 10, 30]
remove(Integer.valueOf(10)) -> true
byValue = [20, 10, 30]
int argument -> 2
primitiveArgument = [1, 3]
Integer argument -> true
wrapperArgument = [2, 3]
long argument -> false
differentWrapper = [1, 2, 3]
Collection receiver -> true
backingList = [2, 3]
String list, int argument -> B
words = [A, C]
String list, String argument -> true
words = [A]
```

Đọc kết quả theo từng quyết định:

- `remove(1)` trên `[10, 20, 10, 30]` trả phần tử 20; hai giá trị 10 vẫn còn.
- `remove(Integer.valueOf(10))` trả `true` và chỉ xóa giá trị 10 đầu tiên; giá trị 10 còn lại vẫn giữ thứ tự tương đối với các phần tử khác.
- Hai biến cùng biểu diễn số 1 nhưng có kiểu `int` và `Integer` khiến lời gọi xóa hai phần tử khác nhau trên `[1, 2, 3]`.
- Đối số `1L` được xem là đối tượng `Long`; `ArrayList<Integer>` trong ví dụ không tìm thấy phần tử bằng nó nên trả `false`.
- Lời gọi qua `Collection<Integer>` xóa giá trị 1 trong chính danh sách gốc.
- Ngay cả `List<String>` vẫn có cả hai phương thức: truyền `1` xóa phần tử chỉ số 1, truyền `"C"` tìm chuỗi bằng `"C"`. Bẫy không chỉ tồn tại vì danh sách chứa số; danh sách số chỉ khiến nó khó nhận ra hơn.

## 7. Bằng nhau, phần tử trùng và vị trí sau khi xóa

### 7.1. Xóa theo đối tượng không phải xóa theo định danh trong bộ nhớ

Với các lớp có `equals` dựa trên nội dung như `String` và `Integer`, một đối tượng khác nhưng bằng nhau vẫn có thể làm đối số xóa.

Đoạn minh họa:

```java
List<String> labels = new ArrayList<>(List.of("java", "sql", "java"));

boolean changed = labels.remove(new String("java"));

// changed = true
// labels = [sql, java]
```

`new String("java")` không phải chính tham chiếu `String` đang lưu ở vị trí đầu, nhưng nó bằng chuỗi đó theo `equals`. Tạo `new String` ở đây chỉ để làm rõ cơ chế; trong mã thông thường, dùng literal `"java"` là đủ.

Với kiểu miền nghiệp vụ tự định nghĩa, cần biết hợp đồng `equals` của kiểu đó. Nếu lớp giữ cách so sánh tham chiếu kế thừa từ `Object`, một đối tượng mới chứa các trường trông giống nhau chưa chắc bằng đối tượng đang có trong danh sách. Đổi từ `remove(int)` sang `remove(Object)` không tự tạo ra quy tắc “trùng mã thì bằng nhau”.

### 7.2. Chỉ phần tử phù hợp đầu tiên bị xóa

Từ `[10, 20, 10, 30]`, xóa theo giá trị 10 một lần cho `[20, 10, 30]`. Nếu mục tiêu nghiệp vụ là “xóa mọi giá trị 10”, một lần gọi `remove(Object)` không đáp ứng mục tiêu đó. `removeIf` là API khác dành cho xóa theo điều kiện; cần xem hợp đồng riêng trước khi dùng, không gán ý nghĩa đó cho `remove(Object)`.

Sau một lần xóa, thứ tự tương đối của phần tử còn lại được bảo toàn, nhưng chỉ số thay đổi. Ví dụ, từ `[A, B, C, D]`, `remove(1)` tạo `[A, C, D]`. Nếu tiếp tục `remove(2)`, phần tử bị xóa là `D`, vì `C` đã chuyển sang chỉ số 1. Một chỉ số cũ không phải danh tính bền vững của phần tử.

Không sửa bằng cách thay máy móc `remove(index)` thành `remove(Integer.valueOf(index))`. Hai dòng diễn đạt hai ý định khác nhau; cần xác định đang giữ vị trí hay giữ giá trị.

## 8. null: phân biệt đối số null và kết quả null

`ArrayList` cho phép chứa `null`. Các tình huống sau vì vậy có thể xảy ra:

1. `list.remove(null)` hoặc `list.remove(nullableInteger)`, với `nullableInteger` có kiểu `Integer`, chọn `remove(Object)`. Nếu danh sách có `null`, nó xóa lần xuất hiện đầu tiên và trả `true`; lời gọi này không cần unboxing đối số.
2. `list.remove(index)` chọn xóa theo chỉ số. Nếu phần tử tại vị trí đó là `null`, phương thức xóa thành công và trả `null`.
3. Nếu gán kết quả `null` đó vào biến `int`, unboxing gây `NullPointerException` sau khi danh sách đã bị thay đổi.
4. Nếu gọi `nullableIndex.intValue()` khi biến chỉ số đang là `null`, lỗi xảy ra khi tính đối số, trước khi phương thức `remove` được gọi. Danh sách chưa bị xóa bởi lời gọi đó.

Hai lỗi `NullPointerException` ở mục 3 và 4 có cùng tên nhưng khác thời điểm và khác trạng thái danh sách sau lỗi. Bắt ngoại lệ không tự hoàn tác thao tác xóa đã thực hiện. [ArrayList và khả năng lưu null](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [JLS 5.1.8 về unboxing](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8).

Chương trình độc lập dưới đây cũng kiểm tra chỉ số sai và danh sách không cho phép sửa đổi. Lưu thành `RemoveNullAndBoundaryDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemoveNullAndBoundaryDemo {
    public static void main(String[] args) {
        List<Integer> byValue =
                new ArrayList<>(Arrays.asList(7, null, 9, null));
        Integer nullableValue = null;
        System.out.println("remove nullable value -> " + byValue.remove(nullableValue));
        System.out.println("byValue = " + byValue);

        List<Integer> nullableResult =
                new ArrayList<>(Arrays.asList((Integer) null, 7));
        Integer removed = nullableResult.remove(0);
        System.out.println("removed element = " + removed);
        System.out.println("nullableResult = " + nullableResult);

        List<Integer> unboxAfter =
                new ArrayList<>(Arrays.asList(7, null, 9));
        try {
            int result = unboxAfter.remove(1);
            System.out.println("result = " + result);
        } catch (NullPointerException ex) {
            System.out.println("unbox returned null -> NullPointerException");
        }
        System.out.println("unboxAfter = " + unboxAfter);

        List<Integer> unboxBefore =
                new ArrayList<>(Arrays.asList(7, null, 9));
        Integer nullableIndex = null;
        try {
            unboxBefore.remove(nullableIndex.intValue());
        } catch (NullPointerException ex) {
            System.out.println("unbox argument null -> NullPointerException");
        }
        System.out.println("unboxBefore = " + unboxBefore);

        List<Integer> boundaries = new ArrayList<>(List.of(4, 5));
        System.out.println("missing value -> "
                + boundaries.remove(Integer.valueOf(-1)));
        try {
            boundaries.remove(-1);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("negative index -> IndexOutOfBoundsException");
        }
        try {
            boundaries.remove(boundaries.size());
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("index equal to size -> IndexOutOfBoundsException");
        }
        System.out.println("boundaries = " + boundaries);

        List<Integer> fixed = List.of(1, 2, 3);
        try {
            fixed.remove(Integer.valueOf(1));
        } catch (UnsupportedOperationException ex) {
            System.out.println("unmodifiable list -> UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RemoveNullAndBoundaryDemo.java
java RemoveNullAndBoundaryDemo
```

Kết quả chuẩn:

```text
remove nullable value -> true
byValue = [7, 9, null]
removed element = null
nullableResult = [7]
unbox returned null -> NullPointerException
unboxAfter = [7, 9]
unbox argument null -> NullPointerException
unboxBefore = [7, null, 9]
missing value -> false
negative index -> IndexOutOfBoundsException
index equal to size -> IndexOutOfBoundsException
boundaries = [4, 5]
unmodifiable list -> UnsupportedOperationException
```

Các điểm cần đọc từ trạng thái sau thao tác:

- `byValue` ban đầu có hai `null`; sau `remove(nullableValue)` vẫn còn một `null`.
- `nullableResult.remove(0)` trả `null` hợp lệ khi nhận vào biến `Integer`.
- `unboxAfter` mất phần tử `null` dù lệnh gán vào `int` kết thúc bằng ngoại lệ.
- `unboxBefore` giữ nguyên vì `nullableIndex.intValue()` đã lỗi trước khi gọi `remove`.
- `Integer.valueOf(-1)` là đối tượng tìm; không tìm thấy thì trả `false`. Đối số `int` bằng -1 lại là chỉ số sai và gây ngoại lệ.
- Chỉ số bằng `size()` nằm ngay sau phần tử cuối, nên không có phần tử để xóa.
- `List.of` tạo danh sách không sửa đổi được. Dù đã chọn đúng overload xóa theo giá trị, việc xóa phần tử hiện có vẫn gây `UnsupportedOperationException`. Chọn đúng overload và có quyền sửa danh sách là hai điều kiện riêng.

Danh sách có kích thước cố định tạo bởi `Arrays.asList` cũng không hỗ trợ xóa phần tử. Trong chương trình, `Arrays.asList` được bọc bằng `new ArrayList<>(...)` để có danh sách cho phép xóa và đồng thời khởi tạo được các phần tử `null`.

## 9. Áp dụng trong mã thực tế

### 9.1. Tách ý nghĩa “mã phần tử” khỏi “vị trí hiển thị”

Giả sử hệ thống đang có `List<Integer> userIds`. Biến `selectedUserId` chứa mã người dùng, không phải số thứ tự trên giao diện.

Đoạn minh họa bên trong một phương thức:

```java
List<Integer> userIds = new ArrayList<>(List.of(101, 205, 309));
int selectedUserId = 205;

boolean deleted = userIds.remove(Integer.valueOf(selectedUserId));
```

Nếu viết `userIds.remove(selectedUserId)`, Java xem 205 là chỉ số và thường gây `IndexOutOfBoundsException`. Nguy hiểm hơn, với mã nhỏ như 1 trên danh sách đủ dài, thao tác có thể xóa nhầm người dùng mà không báo lỗi.

Ngược lại, nếu giao diện cung cấp chỉ số hàng đang chọn, hãy dùng `remove(selectedIndex)` sau khi kiểm tra đầu vào và xác định danh sách tại thời điểm xóa. Đừng boxing một chỉ số rồi vô tình chuyển thành tìm theo giá trị.

Tên biến nên biểu đạt ý nghĩa như `index`, `position`, `userId`, `valueToDelete`, thay vì một tên số chung chung. Khi sửa mã, cần kiểm tra cả nơi tạo ra biến, không chỉ kiểu của biến tại lời gọi.

### 9.2. API nội bộ có thể đặt tên rõ hơn

Khi một lớp tiện ích thực sự cần cung cấp cả hai ý định cho danh sách số, có thể đặt hai tên riêng thay vì tiếp tục tạo thêm một cặp overload khó đọc. Đây là các phương thức minh họa để đặt trong một lớp, không phải một chương trình độc lập:

```java
static Integer removeAt(List<Integer> numbers, int index) {
    return numbers.remove(index);
}

static boolean removeValue(List<Integer> numbers, int value) {
    return numbers.remove(Integer.valueOf(value));
}
```

`removeAt(numbers, 1)` và `removeValue(numbers, 1)` buộc ý nghĩa xuất hiện ngay tại chỗ gọi. Không cần tạo lớp tiện ích chỉ cho mọi lời gọi đơn lẻ; mục đích là giữ ngữ nghĩa rõ khi thiết kế API có nguy cơ nhầm lẫn.

### 9.3. Đừng suy luận hiệu năng chỉ từ tên overload

`remove(Object)` thường phải tìm phần tử phù hợp trước khi xóa. `remove(int)` đã nhận vị trí nhưng chi phí đến vị trí và cập nhật cấu trúc phụ thuộc triển khai.

Với `ArrayList`, xóa theo vị trí còn có thể phải dịch nhiều phần tử phía sau; có sẵn chỉ số không làm mọi lần xóa thành thời gian hằng số. Với danh sách liên kết, việc đi đến một vị trí cũng có chi phí. Chọn thao tác theo ý nghĩa đúng trước, rồi đánh giá hiệu năng bằng cấu trúc dữ liệu và khối lượng công việc cụ thể. Không đổi sang xóa theo chỉ số chỉ để bỏ bước tìm kiếm nếu chưa biết đúng vị trí.

## 10. Lỗi phổ biến và cách tự kiểm tra

| Dấu hiệu | Nguyên nhân thường gặp | Cách sửa theo ý định |
|---|---|---|
| Muốn xóa số 1 nhưng phần tử thứ hai biến mất | Truyền literal `int` vào `List.remove` | Dùng `Integer.valueOf(1)` nếu 1 là giá trị cần tìm |
| Muốn xóa chỉ số trong biến `Integer` nhưng danh sách không đổi | `remove(Object)` đã được chọn và không tìm thấy giá trị bằng biến đó | Dùng kiểu `int` cho chỉ số hoặc chuyển rõ sau khi xử lý `null` |
| `boolean ok = list.remove(1)` không biên dịch | Phương thức được chọn trả phần tử `Integer` | Sửa đối số hoặc kiểu kết quả theo ý định, không dựa vào biến nhận để chọn overload |
| `remove(1L)` trả `false` trên `ArrayList<Integer>` | Đang tìm một `Long`, không phải một `Integer` | Chuẩn hóa đúng kiểu giá trị trước khi xóa; không ép kiểu thu hẹp tùy tiện |
| Một giá trị trùng vẫn còn | `remove(Object)` chỉ xóa lần xuất hiện đầu tiên | Xác định có cần xóa một phần tử hay mọi phần tử thỏa điều kiện |
| Bị lỗi `null` nhưng phần tử đã biến mất | Kết quả `Integer null` bị unboxing sau khi xóa theo chỉ số | Nhận kết quả bằng `Integer` và xử lý `null` phù hợp |
| Đúng đối số nhưng vẫn có `UnsupportedOperationException` | Danh sách không hỗ trợ xóa | Chọn cấu trúc cho phép sửa đổi khi yêu cầu nghiệp vụ cần xóa |
| Cùng dữ liệu nhưng gọi qua `Collection` cho kết quả khác | Tập overload tại chỗ gọi khác với `List` | Kiểm tra kiểu khai báo của biến nhận |

Khi đọc một lời gọi `remove`, tự kiểm tra theo thứ tự:

1. Biến nhận được khai báo là `List`, `Collection` hay kiểu khác?
2. Đối số có kiểu gì sau khi biểu thức được tính: `int`, `Integer`, `Object`, `long` hay kiểu khác?
3. Overload được chọn biểu đạt vị trí hay giá trị?
4. Kết quả mong đợi là phần tử bị xóa hay cờ tìm thấy?
5. Danh sách có cho phép xóa và có thể chứa `null` không?
6. Sau khi xóa, chỉ số và trạng thái danh sách thay đổi thế nào?

## 11. Tự dự đoán trước khi chạy

Mỗi câu dùng danh sách mới, độc lập với câu khác:

```java
List<Integer> values = new ArrayList<>(List.of(2, 0, 2, 4));
```

| Câu | Lời gọi | Cần dự đoán |
|---|---|---|
| A | `values.remove(2)` | Giá trị trả về và danh sách còn lại |
| B | `values.remove(Integer.valueOf(2))` | Cờ trả về và phần tử 2 nào bị xóa |
| C | `values.remove(2L)` | Có xóa được số 2 không? |
| D | `Integer target = 0; values.remove(target);` | Xóa chỉ số 0 hay giá trị 0? |
| E | `Collection<Integer> view = values; view.remove(2);` | Đối tượng nào bị thay đổi? |

Đáp án và lý do:

- A: trả `Integer` bằng 2 ở chỉ số 2; còn `[2, 0, 4]`.
- B: trả `true`; xóa số 2 tại chỉ số 0; còn `[0, 2, 4]`.
- C: trên `ArrayList<Integer>` này, trả `false`; vẫn `[2, 0, 2, 4]`, vì đối số là `Long`.
- D: chọn `remove(Object)`, trả `true`, xóa giá trị 0; còn `[2, 2, 4]`. Kiểu `Integer` của `target` là yếu tố quyết định.
- E: qua `Collection`, số 2 được boxing và dùng để tìm theo giá trị; trả `true`, danh sách `values` còn `[0, 2, 4]`. `view` là tham chiếu khác đến cùng đối tượng.

Kỹ năng cần hình thành là đọc ra đúng ý định từ kiểu của lời gọi: `remove(int)` trả phần tử tại một vị trí, còn `remove(Object)` báo việc xóa lần xuất hiện đầu tiên bằng một đối tượng tìm. Với danh sách số, hãy viết rõ sự chuyển đổi cần thiết ngay nơi gọi để giá trị không bị hiểu thành chỉ số.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-004">indexOf, lastIndexOf và tìm phần tử không tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

# indexOf, lastIndexOf và tìm phần tử không tồn tại

Một danh sách có thể chứa nhiều phần tử giống nhau. Khi cần biết vị trí của một giá trị, ta không chỉ hỏi “có hay không” mà còn phải xác định muốn lấy **lần xuất hiện đầu tiên**, **lần cuối cùng** hay **mọi vị trí**.

`List.indexOf` và `List.lastIndexOf` giải quyết hai câu hỏi đầu:

- `indexOf(target)`: vị trí xuất hiện đầu tiên của phần tử phù hợp.
- `lastIndexOf(target)`: vị trí xuất hiện cuối cùng của phần tử phù hợp.
- Không tìm thấy phần tử phù hợp: trả về `-1`.

Điểm dễ gây lỗi là `-1` vẫn có kiểu `int`, nhưng **không phải chỉ số hợp lệ để truy cập danh sách**.

Bài dùng Java 17. Ba chương trình hoàn chỉnh đã được biên dịch với `--release 17` và chạy để đối chiếu stdout. Các đoạn rút gọn khác minh họa thao tác trong một phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn có thể:

- Đọc đúng chỉ số đầu/cuối trong một danh sách có phần tử trùng.
- Xử lý `-1` trước khi dùng kết quả với `get` hoặc thao tác theo vị trí.
- Giải thích quy tắc so khớp bằng `equals`.
- Phân biệt tìm `null`, tìm chuỗi, tìm đối tượng và tìm theo thuộc tính.
- Nhận diện những trường hợp cần một vòng duyệt thay vì lặp lại indexOf.

## 1. Mô hình vị trí của List

Xét danh sách:

```text
Giá trị:   Lan   Minh   Lan   An   Lan
Chỉ số:     0     1      2    3     4
```

Các vị trí bắt đầu từ `0`. Danh sách có `5` phần tử, nên chỉ số hợp lệ là `0..4`.

Với giá trị `"Lan"`:

```text
indexOf("Lan")     = 0
lastIndexOf("Lan") = 4
```

Với giá trị `"Minh"`:

```text
indexOf("Minh")     = 1
lastIndexOf("Minh") = 1
```

Với giá trị `"Mai"`:

```text
indexOf("Mai")     = -1
lastIndexOf("Mai") = -1
```

### Chỉ số cuối cùng vẫn tính từ đầu danh sách

`lastIndexOf` trả về chỉ số thông thường của danh sách. Nó không trả số bước tính từ cuối về đầu.

Trong ví dụ, `"Lan"` cuối cùng ở chỉ số `4`, nên kết quả là `4`, không phải `0` vì phần tử nằm sát cuối.

### Hai giá trị đầu/cuối không cho biết mọi vị trí

Biết đầu tiên là `0` và cuối cùng là `4` chưa cho biết các vị trí ở giữa. Chúng có thể là `0, 2, 4`, hoặc một cách phân bố khác.

Không thể lấy:

```text
lastIndex - firstIndex + 1
```

làm số lần xuất hiện, vì khoảng giữa hai vị trí có thể chứa các phần tử khác.

## 2. Chữ ký và ý nghĩa của hai phương thức

Dạng chữ ký:

```java
int indexOf(Object target);
int lastIndexOf(Object target);
```

Đây là mô tả chữ ký của `List`, không phải một tệp Java độc lập.

Ý nghĩa theo hợp đồng:

| Phương thức | Kết quả khi tìm thấy |
| --- | --- |
| `indexOf(target)` | Chỉ số nhỏ nhất của phần tử phù hợp |
| `lastIndexOf(target)` | Chỉ số lớn nhất của phần tử phù hợp |

Hai phương thức:

- Không làm thay đổi danh sách.
- Không sắp xếp lại dữ liệu.
- Không xóa các phần tử trùng.
- Không yêu cầu danh sách đã sắp xếp.
- Trả về chỉ số, không trả về chính đối tượng phần tử.

Với đối số được nguồn chấp nhận, không có phần tử phù hợp thì kết quả là `-1`. Một danh sách rỗng cũng không có vị trí phù hợp nên trả `-1`.

### indexOf khác get

| Lời gọi | Đối số có nghĩa gì? | Kết quả |
| --- | --- | --- |
| `list.get(1)` | Chỉ số `1` | Phần tử ở vị trí đó |
| `list.indexOf(1)` trên `List<Integer>` | Giá trị số `1` cần tìm | Chỉ số đầu tiên chứa giá trị phù hợp hoặc `-1` |

Đối số số nguyên truyền cho `indexOf` được dùng như một giá trị cần tìm, không phải chỉ số truy cập.

Vì chữ ký nhận `Object`, khả năng biên dịch của một lời gọi không tự chứng minh đối số đó phù hợp với hợp đồng của mọi lớp triển khai. Nên giữ đúng mô hình kiểu phần tử và đọc chính sách của nguồn đối với các giá trị bị hạn chế.

## 3. Phần tử phù hợp được xác định như thế nào?

Về ý nghĩa, một vị trí phù hợp khi:

```java
Objects.equals(target, list.get(index))
```

biểu diễn quan hệ bằng nhau cần tìm.

Đoạn này cần `java.util.Objects` nếu viết trong mã. Nó mô tả hợp đồng so khớp, không yêu cầu mọi lớp triển khai phải dùng đúng biểu thức và thuật toán truy cập theo chỉ số như trên.

### equals khác so sánh tham chiếu

Hai đối tượng khác nhau trong bộ nhớ có thể bằng nhau theo `equals`.

Ví dụ:

- Hai chuỗi có cùng nội dung có thể được xem bằng nhau dù không cùng đối tượng.
- Một kiểu record có các thành phần bằng nhau cung cấp phép bằng nhau theo giá trị.
- Một lớp không ghi đè `equals` sẽ giữ ý nghĩa mặc định của `Object.equals`, dựa trên cùng đối tượng.

Vì vậy, nếu tìm bằng một đối tượng mới nhưng luôn nhận `-1`, hãy kiểm tra hợp đồng `equals` của kiểu trước khi kết luận indexOf không hoạt động.

### Không tự dùng comparator hoặc thứ tự tự nhiên

Một danh sách đã được sắp bằng comparator vẫn dùng hợp đồng bằng nhau của phần tử khi gọi indexOf.

“Đứng ngang hàng trong phép sắp xếp” và “bằng nhau theo equals” là hai quan hệ cần phân biệt. Việc truyền comparator cho một lần sắp xếp không lưu comparator đó như chính sách tìm kiếm mới của danh sách.

### Với String: tìm cả phần tử, không tìm chuỗi con

`String.equals` so nội dung có phân biệt hoa/thường. Nó không tự bỏ khoảng trắng hoặc chuẩn hóa đầu vào.

Ví dụ:

| Lời gọi | Ý nghĩa |
| --- | --- |
| `List.of("Lan").indexOf("Lan")` | Tìm một phần tử bằng chuỗi Lan |
| `List.of("Lan").indexOf("lan")` | Không khớp theo quy tắc equals của String |
| `List.of("Lan").indexOf("an")` | Không tìm chuỗi con trong phần tử Lan |
| `"Lan".indexOf("an")` | Phương thức của String, tìm chuỗi con trong một chuỗi |

Các tên phương thức giống nhau không có nghĩa đối tượng nhận lời gọi và loại tìm kiếm giống nhau.

Nếu bài toán muốn tìm không phân biệt hoa/thường, tìm chuỗi con hoặc tìm theo một thuộc tính, hãy dùng tiêu chí riêng một cách tường minh.

## 4. Chương trình hoàn chỉnh: đầu, cuối và mọi vị trí

Tạo tệp **`ListPositionLookupDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class ListPositionLookupDemo {
    public static void main(String[] args) {
        List<String> names = List.of("Lan", "Minh", "Lan", "An", "Lan");

        System.out.println("names = " + names);
        show(names, "Lan");
        show(names, "Minh");
        show(names, "Mai");
        show(names, "lan");

        List<Integer> positions = new ArrayList<>();
        ListIterator<String> cursor = names.listIterator();
        while (cursor.hasNext()) {
            int index = cursor.nextIndex();
            if (Objects.equals(cursor.next(), "Lan")) {
                positions.add(index);
            }
        }
        System.out.println("all Lan positions = " + positions);
    }

    private static void show(List<String> values, String target) {
        int first = values.indexOf(target);
        int last = values.lastIndexOf(target);
        System.out.println(target + ": first=" + first + ", last=" + last);

        if (first < 0) {
            System.out.println("  not found");
        } else {
            System.out.println("  get(first) = " + values.get(first));
        }
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ListPositionLookupDemo.java
java -Dfile.encoding=UTF-8 ListPositionLookupDemo
```

Stdout đã kiểm chứng:

```text
names = [Lan, Minh, Lan, An, Lan]
Lan: first=0, last=4
  get(first) = Lan
Minh: first=1, last=1
  get(first) = Minh
Mai: first=-1, last=-1
  not found
lan: first=-1, last=-1
  not found
all Lan positions = [0, 2, 4]
```

### Đọc kết quả

- Lan xuất hiện tại `0, 2, 4`; hai phương thức trả lần lượt `0` và `4`.
- Minh chỉ xuất hiện tại `1`, nên đầu và cuối trùng nhau.
- Mai không tồn tại, nên cả hai kết quả đều là `-1`.
- Chuỗi `"lan"` khác `"Lan"` theo `String.equals`, nên không khớp.
- Nhánh `first < 0` xử lý không tìm thấy; nhánh còn lại mới gọi `get(first)`.
- Vị trí `0` được nhận diện là tìm thấy bình thường.

### Vì sao có thêm vòng duyệt tìm mọi vị trí?

Danh sách vị trí `[0, 2, 4]` được thu thập bằng một lượt ListIterator:

1. Đọc `nextIndex()` trước khi lấy phần tử.
2. `next()` trả phần tử tại chỉ số đó.
3. Nếu phù hợp, lưu chỉ số.

Đây là một lượt duyệt riêng có tiêu chí rõ ràng. `indexOf` và `lastIndexOf` không trả về tập vị trí đó thay cho chương trình.

Dùng `Objects.equals` trong vòng duyệt giúp tiêu chí so khớp xử lý được cả trường hợp tham chiếu `null`, khi nguồn cho phép chúng.

## 5. Xử lý -1 an toàn

Một cách dùng phù hợp:

```java
int found = values.indexOf(target);

if (found >= 0) {
    String value = values.get(found);
    System.out.println(value);
} else {
    System.out.println("Không tìm thấy");
}
```

Đoạn minh họa giả định `values` là danh sách chuỗi, `target` là giá trị cần tìm và danh sách không bị thay đổi giữa các thao tác.

### Lỗi: dùng kết quả trực tiếp

```java
String value = values.get(values.indexOf(target));
```

Nếu không tìm thấy, chương trình thực hiện `get(-1)`, gây `IndexOutOfBoundsException`.

Không tìm thấy phần tử là một kết quả bình thường của tìm kiếm; biến nó thành lỗi truy cập chỉ số thường là do thiếu nhánh xử lý trong ứng dụng.

### Lỗi: chỉ kiểm tra lớn hơn 0

```java
if (found > 0) {
    System.out.println("Đã tìm thấy");
}
```

Điều kiện này bỏ qua phần tử ở vị trí đầu tiên. Một chỉ số hợp lệ có thể bằng `0`.

Hai cách kiểm tra thường dùng là:

```text
found >= 0
found != -1
```

Cách thứ nhất diễn đạt trực tiếp miền chỉ số không âm.

### Lỗi: chỉ kiểm tra nhỏ hơn size

Với danh sách không rỗng, `-1 < values.size()` vẫn đúng. Chỉ kiểm tra cận trên không loại được giá trị báo thiếu.

Nếu đang kiểm tra một chỉ số bất kỳ để truy cập List, cần đủ hai cận:

```text
0 <= index < size
```

Với chỉ số vừa nhận từ indexOf trong một danh sách không thay đổi, nhánh `found >= 0` đã dựa vào hợp đồng của kết quả để biết nó nằm trong cận trên.

### Lỗi: đổi -1 thành 0 để “có giá trị mặc định”

Việc ép một kết quả thiếu về vị trí đầu không làm phần tử cần tìm xuất hiện. Nó khiến chương trình lấy một phần tử khác nhưng có vẻ vẫn chạy.

Nếu cần hành vi dự phòng, hãy viết nhánh đó rõ ràng: hiển thị thông báo, trả kết quả rỗng theo hợp đồng API, hoặc xử lý theo yêu cầu nghiệp vụ. Không dùng chỉ số giả để che sự vắng mặt.

## 6. Null là một phần tử khác với không tìm thấy

Với một danh sách cho phép `null`:

```text
[null, A, null, B]
```

có thể có:

```text
indexOf(null)     = 0
lastIndexOf(null) = 2
```

`-1` báo rằng không có vị trí phù hợp. Nó không có cùng ý nghĩa với “phần tử tại vị trí tìm được là null”.

Cần phân biệt:

| Tình huống | Kết quả tìm vị trí |
| --- | --- |
| Có phần tử null ở đầu | Chỉ số `0` |
| Có phần tử null ở vị trí khác | Chỉ số không âm tương ứng |
| Không có phần tử phù hợp | `-1`, nếu đối số được nguồn chấp nhận |

### Nguồn có thể hạn chế null

Không phải mọi List đều cho phép phần tử `null` hoặc chấp nhận truy vấn `null` theo cùng chính sách. Ví dụ, các danh sách không cho phép null có thể từ chối kiểu truy vấn đó bằng `NullPointerException`.

Không nên tự quy ước rằng mọi lời gọi `indexOf(null)` trên mọi List chắc chắn chỉ trả số hoặc `-1`. Hãy xác định hợp đồng nguồn.

Trong chương trình sau, nguồn được chọn là `ArrayList`, tạo từ một đầu vào có phần tử null để tình huống được xác định rõ.

## 7. Chương trình hoàn chỉnh: thiếu, rỗng và null

Tạo tệp **`MissingAndNullDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MissingAndNullDemo {
    public static void main(String[] args) {
        List<String> values =
                new ArrayList<>(Arrays.asList(null, "A", null, "B"));

        System.out.println("values = " + values);
        System.out.println("first null = " + values.indexOf(null));
        System.out.println("last null = " + values.lastIndexOf(null));

        int found = values.indexOf("Z");
        System.out.println("Z position = " + found);

        if (found >= 0) {
            System.out.println("Z value = " + values.get(found));
        } else {
            System.out.println("Z is absent");
        }

        try {
            values.get(found);
        } catch (IndexOutOfBoundsException exception) {
            System.out.println("get(-1) = "
                    + exception.getClass().getSimpleName());
        }

        List<String> empty = List.of();
        System.out.println("empty first A = " + empty.indexOf("A"));
        System.out.println("empty last A = " + empty.lastIndexOf("A"));

        List<Integer> numbers = List.of(10, 20, 10);
        System.out.println("numbers.get(1) = " + numbers.get(1));
        System.out.println("numbers.indexOf(1) = " + numbers.indexOf(1));
        System.out.println("numbers.lastIndexOf(10) = " + numbers.lastIndexOf(10));
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 MissingAndNullDemo.java
java -Dfile.encoding=UTF-8 MissingAndNullDemo
```

Stdout đã kiểm chứng:

```text
values = [null, A, null, B]
first null = 0
last null = 2
Z position = -1
Z is absent
get(-1) = IndexOutOfBoundsException
empty first A = -1
empty last A = -1
numbers.get(1) = 20
numbers.indexOf(1) = -1
numbers.lastIndexOf(10) = 2
```

### Những trường hợp đã kiểm chứng

1. `null` có vị trí đầu và cuối cụ thể.
2. Z không tồn tại, nên nhận `-1`.
3. Nhánh kiểm tra chỉ số xử lý Z vắng mặt mà không gọi `get`.
4. Lần gọi `get(found)` ngoài nhánh an toàn được cố ý đặt trong thử nghiệm để thấy `get(-1)` gây lỗi.
5. Danh sách rỗng trả `-1` khi tìm A.
6. `numbers.get(1)` trả phần tử `20`.
7. `numbers.indexOf(1)` lại tìm giá trị `1`, không có nên trả `-1`.
8. `lastIndexOf(10)` trả vị trí cuối của giá trị `10`, là `2`.

Khối bắt ngoại lệ trong chương trình dùng để minh họa một cách sử dụng sai đã biết. Trong mã xử lý tìm kiếm thông thường, hãy kiểm tra kết quả trước khi truy cập thay vì dùng ngoại lệ làm nhánh “không tìm thấy”.

## 8. Tìm đối tượng không phải tự động tìm theo một thuộc tính

Giả sử một sản phẩm gồm:

```text
code
name
```

Ta có thể cần hai bài toán khác nhau:

- Tìm một Product bằng nhau theo `equals`.
- Tìm sản phẩm có cùng code, bất kể name.

`indexOf` chỉ trực tiếp giải bài toán thứ nhất theo hợp đồng của Product. Nó không biết thuộc tính code có ý nghĩa nghiệp vụ đặc biệt nào nếu bạn chưa thiết kế như vậy.

Trong chương trình, Product là record. Phép bằng nhau do record cung cấp xét các thành phần code và name.

### Chương trình hoàn chỉnh

Tạo tệp **`ObjectPositionLookupDemo.java`**:

```java
import java.util.List;
import java.util.Objects;

public class ObjectPositionLookupDemo {
    public record Product(String code, String name) {
    }

    public static void main(String[] args) {
        List<Product> products = List.of(
                new Product("P1", "Pen"),
                new Product("P2", "Book"),
                new Product("P1", "Pen"));

        Product sameValue = new Product("P1", "Pen");
        Product sameCodeOnly = new Product("P1", "Pencil");

        System.out.println("same reference = " + (products.get(0) == sameValue));
        System.out.println("same value = " + products.get(0).equals(sameValue));
        System.out.println("sameValue first = " + products.indexOf(sameValue));
        System.out.println("sameValue last = " + products.lastIndexOf(sameValue));
        System.out.println("sameCodeOnly indexOf = " + products.indexOf(sameCodeOnly));
        System.out.println("find P1 by code = " + firstIndexByCode(products, "P1"));
        System.out.println("find P9 by code = " + firstIndexByCode(products, "P9"));
    }

    private static int firstIndexByCode(List<Product> products, String code) {
        for (int index = 0; index < products.size(); index++) {
            if (Objects.equals(products.get(index).code(), code)) {
                return index;
            }
        }
        return -1;
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ObjectPositionLookupDemo.java
java -Dfile.encoding=UTF-8 ObjectPositionLookupDemo
```

Stdout đã kiểm chứng:

```text
same reference = false
same value = true
sameValue first = 0
sameValue last = 2
sameCodeOnly indexOf = -1
find P1 by code = 0
find P9 by code = -1
```

### Vì sao một đối tượng mới vẫn tìm được?

`sameValue` không phải cùng tham chiếu với phần tử đầu, nhưng code và name đều giống. Theo phép bằng nhau của record, chúng bằng nhau nên indexOf tìm được ở `0`.

Vì cũng có một Product bằng nhau ở cuối, lastIndexOf trả `2`.

### Vì sao cùng code vẫn có thể không tìm được?

`sameCodeOnly` có code P1 nhưng name Pencil, khác name Pen ở dữ liệu nguồn. Theo phép bằng nhau của record đang dùng, nó không khớp.

Hàm `firstIndexByCode` mô tả một tiêu chí khác:

```text
Mã sản phẩm bằng code cần tìm
```

Do đó tìm P1 theo code trả `0`, còn tìm P9 trả `-1`.

Hàm minh họa giả định các phần tử Product không null. Việc dùng `Objects.equals` cho code giúp so các giá trị code có thể null, nhưng không tự bảo vệ thao tác gọi `code()` trên một phần tử Product null.

### Có nên sửa equals để indexOf tìm đúng theo code?

Chỉ sửa nếu “hai Product bằng nhau khi cùng code” là hợp đồng đúng của kiểu trong toàn hệ thống. `equals` còn ảnh hưởng nhiều nơi khác, không riêng một lần tìm kiếm.

Nếu chỉ màn hình hoặc tác vụ hiện tại cần tìm theo một thuộc tính, hãy viết tiêu chí tìm riêng. Không thay đổi định nghĩa bằng nhau của cả kiểu chỉ để tiết kiệm một vòng duyệt.

## 9. Chỉ số tìm được gắn với trạng thái danh sách

Kết quả là một vị trí trong danh sách tại lúc tìm, không phải mã định danh ổn định của phần tử.

Đoạn minh họa:

```java
List<String> values = new ArrayList<>(List.of("A", "B", "C"));

int found = values.indexOf("B"); // found = 1
values.add(0, "X");

// values hiện là [X, A, B, C].
// values.get(found) bây giờ trả A, không phải B.
```

Chèn ở đầu đã làm vị trí B thay đổi.

Các thao tác có thể khiến chỉ số đã lưu không còn mang ý nghĩa cũ gồm:

- Chèn hoặc xóa trước vị trí đó.
- Sắp xếp lại danh sách.
- Thay phần tử tại vị trí tìm được.
- Đổi dữ liệu mà quy tắc equals dựa vào.

Vì vậy:

- Dùng chỉ số ngay trong phạm vi trạng thái được giữ ổn định.
- Nếu phải lưu nhận diện lâu dài, dùng định danh phù hợp thay vì chỉ số.
- Nếu có nhiều bên sửa, việc tìm rồi truy cập cần cơ chế phối hợp theo yêu cầu.
- Một collection có các phương thức an toàn luồng cũng không tự làm chuỗi “tìm rồi dùng chỉ số” nguyên tử.

Trong những ví dụ hoàn chỉnh của bài, dữ liệu không có bên sửa đồng thời giữa tìm và truy cập, nên điều kiện đó được xác định rõ.

## 10. Chi phí và khi nào nên dùng một lượt duyệt

Ở các danh sách thông dụng như ArrayList và LinkedList, tìm theo giá trị có thể phải kiểm tra nhiều phần tử. Trường hợp xấu nhất thường tăng theo số phần tử.

Hai phương thức còn có thể dừng ở những điểm khác nhau:

- Khi phần tử phù hợp nằm ở đầu, indexOf có thể tìm thấy sớm.
- Khi nó nằm ở cuối, lastIndexOf có thể tìm thấy sớm.
- Không tồn tại thì phải xác định rằng không có vị trí phù hợp trong phạm vi tìm.

Đừng dùng các ví dụ nhỏ để cho rằng tìm kiếm theo giá trị luôn có chi phí không đổi.

### Tránh contains rồi indexOf khi chỉ cần vị trí

Một mẫu không cần thiết thường gặp:

```java
if (values.contains(target)) {
    int found = values.indexOf(target);
    System.out.println(found);
}
```

Nếu mục tiêu đã là lấy chỉ số, gọi indexOf một lần rồi kiểm tra `-1` thường đủ. Hai lời gọi trên có thể tạo hai lượt tìm, và nếu nguồn thay đổi giữa chúng thì hai kết quả còn có thể không cùng trạng thái.

Ngược lại, nếu chỉ cần câu trả lời có/không, `contains` diễn đạt ý định trực tiếp.

### Khi cần mọi vị trí

Dùng một vòng duyệt và thu thập các chỉ số phù hợp, như chương trình đầu.

`List.indexOf` không có tham số bắt đầu kiểu `fromIndex` như một số phương thức tìm của String. Nếu cứ tìm lại trên các đoạn danh sách, bạn phải tự xử lý độ lệch chỉ số và có thể tạo nhiều lượt quét.

Một lượt iterator có chỉ số giúp nhìn rõ phạm vi và tránh tìm lại cùng dữ liệu một cách không cần thiết.

### Khi tìm theo thuộc tính

Một vòng duyệt với điều kiện tường minh thường phù hợp cho tác vụ đơn lẻ.

Nếu hệ thống thường xuyên tra dữ liệu theo một khóa, hãy xem lại mô hình lưu trữ: có thể cần một chỉ mục hoặc Map đi kèm. Tuy nhiên, phải giữ rõ yêu cầu về phần tử trùng, thứ tự và đồng bộ dữ liệu; không mặc định đổi List thành Map mà vẫn giữ nguyên mọi ý nghĩa.

### Cẩn thận với vòng get(index) trên LinkedList

Hàm tìm theo code trong ví dụ nhấn mạnh cách quyết định vị trí trên một dữ liệu nhỏ. Nếu dùng một thuật toán truy cập từng chỉ số trên danh sách liên kết lớn, việc tìm lại nút cho mỗi get có thể làm tổng chi phí tăng không phù hợp.

Khi cần duyệt chung cho nhiều kiểu List, một ListIterator hoặc cơ chế duyệt tuần tự có thể tránh giả định về truy cập ngẫu nhiên. Đây là chuyện chọn thuật toán phù hợp cấu trúc, không làm thay đổi hợp đồng kết quả `-1` hoặc chỉ số tìm thấy.

## 11. Lỗi thường gặp

### Nhầm -1 với phần tử cuối

Trong List của Java, `-1` không phải cú pháp truy cập phần tử cuối. Nó là giá trị báo không tìm thấy của hai phương thức đang học.

### Bỏ qua vị trí 0

Dùng `found > 0` làm điều kiện tìm thấy sẽ khiến dữ liệu đầu danh sách bị coi như không có. Dùng kiểm tra miền không âm.

### Tưởng lastIndexOf đếm từ cuối

Chỉ số trả về vẫn theo hệ chỉ số từ đầu danh sách. Phần tử cuối của danh sách kích thước n có chỉ số `n - 1`.

### Tưởng indexOf tìm chuỗi con hoặc tự bỏ qua hoa/thường

Với List<String>, phép tìm vẫn so cả phần tử bằng String.equals. Muốn tiêu chí khác thì phải mô tả khác.

### Tưởng cùng mã là equals tự động

Ý nghĩa bằng nhau phụ thuộc kiểu phần tử. Record có nhiều thành phần không tự coi một thành phần là khóa duy nhất.

### Dùng get trước khi kiểm tra -1

Đảo thứ tự này gây lỗi truy cập khi đối tượng không tồn tại. Không tìm thấy là một nhánh cần được dự tính trước.

### Dùng phần tử null để kết luận không tìm thấy

Tìm được một vị trí đang chứa null vẫn là tìm thấy. Dùng kết quả chỉ số để phân biệt sự tồn tại, không chỉ nhìn giá trị trả về từ get.

### Lưu chỉ số qua nhiều thay đổi

Chỉ số không phải định danh ổn định. Nếu danh sách được sửa hoặc sắp xếp lại, phải đánh giá lại cách dùng vị trí cũ.

### Lấy khoảng đầu-cuối làm số lượng phần tử trùng

Khoảng đó có thể chứa các giá trị khác. Muốn đếm hoặc lấy mọi vị trí, cần duyệt theo tiêu chí cần thiết.

## 12. Thực hành

### Bài 1: dự đoán vị trí

Với:

```text
[red, blue, red, green, red]
```

Không chạy mã trước, hãy điền:

| Giá trị cần tìm | indexOf | lastIndexOf |
| --- | --- | --- |
| red | ? | ? |
| blue | ? | ? |
| yellow | ? | ? |
| RED | ? | ? |

Giải thích quy tắc dùng để tìm và vì sao số lần xuất hiện của red không bằng khoảng từ chỉ số đầu đến cuối.

### Bài 2: sửa nhánh không tìm thấy

Đoạn mã:

```java
int found = names.indexOf(target);
System.out.println(names.get(found));
```

Hãy bổ sung nhánh xử lý để:

1. Phần tử ở vị trí `0` vẫn được nhận là tìm thấy.
2. Đối tượng không tồn tại không gây lỗi get.
3. Không biến `-1` thành một vị trí giả.
4. Không tìm kiếm hai lần khi chỉ cần một kết quả vị trí.

### Bài 3: phân biệt null và vắng mặt

Tạo ArrayList từ:

```text
[A, null, B, null]
```

Dự đoán:

- Vị trí đầu của null.
- Vị trí cuối của null.
- Kết quả tìm C.
- Điều kiện để phân biệt tìm thấy null với không tìm thấy.

Hai vị trí null cần lần lượt là `1` và `3`; tìm C trả `-1`.

### Bài 4: tìm theo giá trị và tìm theo thuộc tính

Từ ObjectPositionLookupDemo:

1. Thêm Product có code P1 nhưng name khác.
2. Tìm đối tượng bằng cả code và name.
3. Tìm lần đầu theo code.
4. Viết một lượt duyệt để lấy mọi vị trí theo code.
5. Giải thích vì sao hai tiêu chí có thể cho các tập vị trí khác nhau.

### Bài 5: kiểm tra chỉ số đã cũ

Tìm vị trí B trong `[A, B, C]`, sau đó thử riêng từng thao tác:

- Chèn X ở đầu.
- Xóa A.
- Thay B bằng Y ở vị trí đang biết.

Theo dõi giá trị của biến chỉ số và nội dung tại vị trí đó. Nêu khi nào chỉ số còn biểu diễn đúng phần tử ban đầu và khi nào cần tìm lại hoặc dùng một cách nhận diện khác.

Tiêu chí hoàn thành là xác định đúng quan hệ so khớp, chọn đúng chỉ số đầu/cuối, xử lý rõ sự vắng mặt và chỉ dùng kết quả vị trí trong trạng thái danh sách phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-005">Chèn nhiều phần tử bằng addAll có vị trí</a>

<details>
<summary>Xem chi tiết</summary>

# Chèn nhiều phần tử bằng addAll có vị trí

Khi cần chèn một nhóm phần tử vào giữa danh sách, `List.addAll(index, source)` biểu đạt trực tiếp ý định: đặt toàn bộ nhóm vào một vị trí, giữ thứ tự mà nguồn cung cấp, rồi đẩy phần đuôi của danh sách đích sang phải.

Ví dụ:

~~~text
Đích ban đầu: [A, D]
Nhóm chèn:   [B, C]
Vị trí:      1

Kết quả:     [A, B, C, D]
~~~

Đây là **chèn thêm**, không phải thay thế hai phần tử và cũng không phải chuyển nhóm khỏi nguồn. Với nguồn và đích độc lập, nhóm nguồn vẫn còn sau thao tác.

Bài dùng các ví dụ Java 17 trở lên và chủ yếu dùng ArrayList có thể thay đổi để nhìn rõ kết quả.

## 1. Chữ ký và ba vai trò cần phân biệt

Chữ ký trong `List<E>`:

~~~java
boolean addAll(int index, Collection<? extends E> source)
~~~

Đây là chữ ký để đọc hiểu, không phải phương thức cần tự cài đặt khi chỉ sử dụng List.

- Đối tượng nhận lời gọi là **danh sách đích**.
- `index` là vị trí trong đích mà phần tử đầu tiên của nhóm sẽ nhận.
- `source` là collection cung cấp các phần tử của nhóm.
- Giá trị trả về cho biết đích có thay đổi do lời gọi hay không, không phải số phần tử được thêm.

Ví dụ:

~~~java
// Đoạn mã trong một phương thức; cần các import java.util tương ứng.
List<String> destination = new ArrayList<>(List.of("A", "D"));
List<String> source = List.of("B", "C");

boolean changed = destination.addAll(1, source);

System.out.println(changed);     // true
System.out.println(destination); // [A, B, C, D]
~~~

Nguồn không nhất thiết là List; tham số nhận một `Collection` phù hợp. Một `Set` cũng có thể cung cấp phần tử, nhưng quy tắc thứ tự của nguồn vẫn phải được hiểu rõ.

Một `Iterable` tùy ý hoặc một `Stream` không tự trở thành `Collection` chỉ vì có thể tạo ra các phần tử. Nếu API đầu vào không phải Collection, cần chọn một cách thu thập hoặc duyệt phù hợp trước khi dùng overload này.

Hợp đồng về vị trí, thứ tự nguồn và nguồn bị thay đổi trong khi thực hiện có tại [List.addAll trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#addAll(int,java.util.Collection)).

## 2. Vị trí chèn: từ 0 đến size, bao gồm cả size

Với danh sách đích có `n` phần tử:

~~~text
0 <= index <= n
~~~

là miền vị trí chèn hợp lệ.

Ví dụ đích A, D có size `2`:

~~~text
Vị trí: 0   1   2
        | A | D |
~~~

- `index == 0`: chèn trước A.
- `index == 1`: chèn giữa A và D.
- `index == 2`: chèn sau D, tức thêm cuối.

Không nhầm với get hoặc set: hai phương thức đó cần một phần tử đã có nên chỉ số phải nhỏ hơn size. addAll chèn tại một khoảng trống, nên được phép bằng size.

### Đầu danh sách

~~~java
List<String> destination = new ArrayList<>(List.of("A", "D"));
destination.addAll(0, List.of("H1", "H2"));

System.out.println(destination); // [H1, H2, A, D]
~~~

Cả nhóm H1, H2 đứng đầu theo đúng thứ tự nguồn.

### Cuối danh sách

~~~java
destination.addAll(destination.size(), List.of("T1", "T2"));
~~~

Nhóm được đặt ở cuối. Không có phần tử cũ nào phía sau điểm chèn để đổi chỉ số.

Về nội dung dãy khi thao tác được hỗ trợ, `destination.addAll(source)` là dạng thêm cuối; overload có chỉ số cho phép chọn vị trí khác thay vì chỉ cuối.

### Đích rỗng

~~~java
List<String> destination = new ArrayList<>();
destination.addAll(0, List.of("first", "second"));

System.out.println(destination); // [first, second]
~~~

Khi đích rỗng, `0` là vị trí chèn duy nhất hợp lệ. Capacity được dự trữ trong một ArrayList không làm xuất hiện những vị trí công khai khác khi size vẫn bằng `0`.

## 3. Mô hình phần đầu – nhóm mới – phần đuôi

Gọi:

- `n`: size đích trước thao tác.
- `p`: vị trí chèn.
- `m`: số phần tử trong nguồn ổn định của lần thao tác.

Khi thành công, có thể đọc kết quả theo dạng:

~~~text
đích cũ [0, p)
    + các phần tử nguồn theo thứ tự duyệt
    + đích cũ [p, n)
~~~

Ví dụ:

~~~text
Đích:   [A, B, E, F]
p = 2
Nguồn:  [C, D]
m = 2

Phần đầu: [A, B]
Nhóm mới: [C, D]
Phần đuôi:[E, F]

Kết quả: [A, B, C, D, E, F]
~~~

Các vị trí thay đổi:

| Nhóm | Vị trí sau chèn |
| --- | --- |
| Phần tử đích cũ ở j < p | Giữ vị trí j |
| Phần tử thứ k của nguồn, k bắt đầu từ 0 | Nhận vị trí p + k |
| Phần tử đích cũ ở j >= p | Chuyển sang j + m |

Size mới là `n + m` nếu toàn bộ nhóm đã được chèn thành công.

Thứ tự tương đối giữa các phần tử đích cũ không bị đảo. E vẫn đứng trước F. Thứ tự của nhóm nguồn cũng không bị đảo: C đứng trước D.

### Đây là chèn, không phải ghi đè

Nếu dùng set để thay các vị trí từ p, ta đang viết một thuật toán khác, có thể mất các phần tử cũ hoặc vượt miền khi chưa có đủ vị trí.

`addAll` giữ phần đuôi cũ bằng cách tạo chỗ cho nhóm mới. Không cần tự xóa, tự giữ lại phần đuôi rồi gắn vào chỉ để thực hiện một phép chèn thông thường.

## 4. Thứ tự nhóm đến từ iterator của nguồn

addAll không tự sắp xếp các phần tử.

Với:

~~~java
List<String> source = List.of("C", "B");
~~~

nhóm chèn là C rồi B, không phải B rồi C.

Với nguồn có thứ tự chèn rõ ràng:

~~~java
LinkedHashSet<String> source =
        new LinkedHashSet<>(List.of("C", "B", "C"));

List<String> destination = new ArrayList<>(List.of("A", "D"));
destination.addAll(1, source);

System.out.println(destination); // [A, C, B, D]
~~~

Nguồn Set đã loại lần xuất hiện C lặp theo hợp đồng của nó. Danh sách đích nhận C rồi B theo thứ tự mà nguồn cung cấp.

Nếu nguồn là HashSet, không dựa vào một thứ tự duyệt cụ thể tình cờ quan sát được. Việc đích là ArrayList không tự khôi phục lịch sử chèn hoặc sắp nguồn theo thứ tự tự nhiên.

Nếu cần một quy tắc hiển thị rõ:

~~~java
// source là Collection<String> đã có; giả định không có null.
List<String> orderedBatch = new ArrayList<>(source);
orderedBatch.sort(Comparator.naturalOrder());

destination.addAll(position, orderedBatch);
~~~

Đoạn mã này chủ động chọn thứ tự tự nhiên cho khóa String. Đó là quyết định bổ sung trước khi chèn, không phải hành vi mặc định của addAll.

### Nguồn List có phần tử lặp

~~~java
List<String> destination = new ArrayList<>(List.of("A", "D"));
destination.addAll(1, List.of("B", "B"));

System.out.println(destination); // [A, B, B, D]
~~~

Trong ArrayList này, hai lần xuất hiện B đều được thêm. Giá trị đã có trong đích hoặc lặp trong nguồn không làm addAll tự trở thành phép hợp tập hợp.

Với một lớp List chuyên biệt có giới hạn phần tử riêng, cần xem hợp đồng cụ thể. Không suy ra mọi lớp mang kiểu List đều cho mọi dữ liệu mà một ArrayList cho phép.

## 5. Boolean trả về và nguồn rỗng

Với ArrayList có thể sửa:

~~~java
List<String> destination = new ArrayList<>(List.of("A", "D"));

boolean changed = destination.addAll(1, List.of());

System.out.println(changed);     // false
System.out.println(destination); // [A, D]
~~~

Nguồn rỗng không cung cấp phần tử mới để chèn.

Giá trị boolean không phải:

- Số phần tử thêm.
- Chỉ số cuối của nhóm.
- Một List kết quả.
- Chỉ báo rằng các phần tử nguồn đều khác với mọi giá trị đã có ở đích.

Nếu chèn B, B vào ArrayList, đích vẫn tăng hai phần tử và kết quả là true. List không loại trùng như Set.

### Nguồn rỗng không bỏ qua mọi điều kiện

Không coi nguồn rỗng là lý do truyền vị trí tùy ý hoặc bỏ qua chính sách khả năng sửa của nguồn.

Ví dụ, với một ArrayList có size `2`, index `3` vẫn ngoài miền chèn. Một danh sách không hỗ trợ addAll còn có thể từ chối lời gọi dù nó không tạo thêm phần tử nào.

Cũng không suy từ false rằng mọi trạng thái nội bộ hoặc iterator cũ chắc chắn không bị ảnh hưởng. Boolean nói về kết quả thay đổi nội dung theo hợp đồng; đừng dùng nó như một giao thức quản lý phiên bản iterator.

### Collection null khác collection rỗng

~~~java
destination.addAll(1, null); // NullPointerException
~~~

`null` không phải một collection không có phần tử.

Một nguồn có chứa phần tử `null` lại là trường hợp khác. ArrayList cho phép phần tử null, nên nguồn như `Arrays.asList("B", null)` có thể được chèn vào nó. Với đích cấm null, phải theo hợp đồng và kiểm tra dữ liệu tương ứng.

Không dùng `List.of("B", null)` để tạo nguồn cho ca này, vì chính List.of từ chối null trước khi tới lời gọi addAll.

## 6. Vì sao vòng lặp add tại cùng một chỉ số bị đảo thứ tự?

Cách viết:

~~~java
List<String> destination = new ArrayList<>(List.of("A", "D"));

for (String value : List.of("B", "C")) {
    destination.add(1, value);
}

System.out.println(destination); // [A, C, B, D]
~~~

Diễn biến:

~~~text
Ban đầu:       [A, D]
add(1, "B"):   [A, B, D]
add(1, "C"):   [A, C, B, D]
~~~

Lần thứ hai vẫn chèn vào vị trí `1`, trước B vừa thêm. Vì vậy cách này không tương đương addAll(1, [B, C]).

Nếu phải viết vòng lặp theo ý định giữ thứ tự, cần cập nhật điểm chèn:

~~~java
int insertion = 1;
for (String value : List.of("B", "C")) {
    destination.add(insertion, value);
    insertion++;
}
~~~

Đoạn này giả định bắt đầu từ đích ban đầu A, D. Kết quả là A, B, C, D.

Tuy nhiên, khi nguồn đã là Collection và mục tiêu là chèn nguyên nhóm, một lời gọi addAll thường biểu đạt ý định trực tiếp hơn. Với một số triển khai, nó còn có thể giảm việc dịch phần đuôi lặp lại.

Không lấy khả năng đúng của vòng lặp với một ArrayList nhỏ để kết luận hai phương án có cùng chi phí trên mọi List.

## 7. Nguồn độc lập không bị tiêu thụ hoặc gắn thành view

Ví dụ:

~~~java
List<String> destination = new ArrayList<>(List.of("A", "D"));
List<String> source = new ArrayList<>(List.of("B", "C"));

destination.addAll(1, source);

System.out.println(source); // [B, C]

source.set(0, "source-only");

System.out.println(source);      // [source-only, C]
System.out.println(destination); // [A, B, C, D]
~~~

Đích không giữ một liên kết động kiểu “đoạn này luôn nhìn theo các ô của source”. Nó đã thêm các phần tử được cung cấp trong lần thao tác đó.

Thay phần tử mà source giữ sau khi chèn không tự thay phần tử tương ứng trong destination. Xóa các phần tử khỏi source cũng không phải cách xóa nhóm đã được chèn khỏi đích.

### Nhưng không có sao chép sâu đối tượng

Các phần tử dạng đối tượng được thêm bằng tham chiếu. Nếu cả nguồn và đích cùng giữ tham chiếu tới một đối tượng có thể thay đổi, việc sửa nội dung đối tượng đó có thể ảnh hưởng điều cả hai bên quan sát.

Phân biệt:

- Thay một ô của source bằng `source.set(...)`: thay tham chiếu ở source.
- Sửa nội dung đối tượng mà cả hai bên cùng giữ: cùng một đối tượng được dùng chung.
- Sao chép sâu: cần chính sách sao chép riêng của ứng dụng, addAll không tự làm.

Các ví dụ String dùng đối tượng bất biến để quan sát thứ tự và tính độc lập về cấu trúc danh sách, không chứng minh mọi kiểu phần tử đều bất biến.

## 8. Nguồn và đích dùng chung dữ liệu: cần chụp nhóm trước

Không viết một thuật toán tổng quát dựa vào hành vi tự chèn chính danh sách:

~~~java
// Không dựa vào lời gọi này như một hợp đồng sao chép tổng quát.
destination.addAll(position, destination);
~~~

Hợp đồng không xác định hành vi chung nếu collection nguồn bị sửa trong khi thao tác đang thực hiện. Trường hợp nguồn chính là đích không rỗng là một tình huống phải tránh dựa vào hành vi cụ thể. Việc một triển khai có thể tạm chụp nguồn và cho kết quả dễ hiểu trong một lần chạy không biến nó thành bảo đảm chung.

Tương tự, một `subList` có thể là view của chính đích. Sửa đích có thể ảnh hưởng cấu trúc mà view nguồn dựa vào.

### Chụp phần cần chèn trước khi sửa đích

~~~java
List<String> destination =
        new ArrayList<>(List.of("A", "B", "C", "D"));

List<String> captured =
        new ArrayList<>(destination.subList(0, 2));

destination.addAll(2, captured);

System.out.println(destination); // [A, B, A, B, C, D]
~~~

Nhóm A, B được chụp vào một collection có cấu trúc riêng trước khi destination bị thay đổi.

Trong chương trình này không có thay đổi đồng thời của nguồn khi chụp. Một lời gọi tạo bản sao không tự giải quyết việc luồng khác đang sửa dữ liệu; nếu có chia sẻ nhiều luồng, cần một quy trình đồng bộ hoặc ảnh chụp phù hợp.

Sau khi cấu trúc gốc bị đổi, cũng không tiếp tục dùng một view cũ theo giả định mọi vị trí vẫn hợp lệ. Bài dùng `captured` độc lập nên không cần truy cập lại view đã dùng để tạo nhóm.

## 9. Kiểu generic: Collection<? extends E>

Nếu đích là `List<Number>`, có thể nhận một nguồn `List<Integer>`:

~~~java
List<Number> destination =
        new ArrayList<>(List.of(0.5, 1.5));

List<Integer> integers = List.of(10, 20);
destination.addAll(1, integers);

System.out.println(destination); // [0.5, 10, 20, 1.5]
~~~

Đích nhận các phần tử Integer vì mỗi Integer là một Number.

`? extends E` ở tham số nguồn giúp đọc các phần tử có kiểu phù hợp để thêm vào đích. Nó không có nghĩa phương thức biến nguồn thành `List<Number>`, và không cần ép kiểu toàn bộ collection để làm điều đó.

Ngược lại, một `List<Object>` không được tự coi là nguồn hợp lệ cho `List<String>` chỉ vì người gọi tin các phần tử hiện tại đều là String. Kiểm tra kiểu tĩnh xét hợp đồng có thể chứa phần tử gì, không chỉ một ảnh dữ liệu đang quan sát.

### add không phải addAll

Với `List<String>`, gọi `add(sourceList)` không phải cách thêm các String của sourceList; kiểu phần tử String không khớp một đối tượng List.

Với một đích đủ rộng như `List<Object>`, `add(sourceList)` có thể thêm **một đối tượng List** làm một phần tử. `addAll(sourceList)` lại thêm từng phần tử trực tiếp của collection nguồn.

addAll cũng không tự làm phẳng mọi mức lồng nhau. Nó xử lý các phần tử trực tiếp do source cung cấp; nếu những phần tử đó là các List con phù hợp với kiểu đích, chúng vẫn là các phần tử dạng List con.

## 10. Khả năng chỉnh sửa và ngoại lệ

| Vấn đề | Điều cần kiểm tra |
| --- | --- |
| Vị trí chèn | Phải từ 0 đến size của đích ở thời điểm thao tác. |
| Khả năng hỗ trợ | Đích phải hỗ trợ thao tác làm tăng kích thước. |
| Nguồn | Không được là tham chiếu null. |
| Kiểu và giá trị phần tử | Đáp ứng quy tắc của đích, gồm chính sách null nếu có. |
| Nguồn bị sửa trong khi thao tác | Phải tránh hoặc tuân theo một chính sách concurrent được công bố rõ. |

ArrayList và LinkedList có thể sửa thông thường hỗ trợ indexed addAll. Một danh sách kích thước cố định từ Arrays.asList không hỗ trợ chèn thêm; danh sách không cho sửa từ List.of hoặc List.copyOf cũng không hỗ trợ.

Chỉ số hợp lệ không làm một thao tác không hỗ trợ trở thành hợp lệ:

~~~java
List<String> fixed = Arrays.asList("A", "D");

// Vị trí 1 hợp lệ, nhưng danh sách không cho tăng kích thước.
fixed.addAll(1, List.of("B", "C")); // UnsupportedOperationException
~~~

Không cần dự đoán thứ tự ngoại lệ khi cùng lúc vi phạm nhiều điều kiện. Tách ca kiểm tra chỉ số, nguồn null và đích không hỗ trợ để biết đang kiểm chứng điều gì.

### Một lời gọi không có nghĩa một giao dịch

Không có một bảo đảm chung rằng addAll trên mọi List sẽ kiểm tra toàn bộ rồi hoặc thêm đủ tất cả hoặc không thay đổi gì khi gặp lỗi.

Một triển khai, collection chuyên biệt hoặc nguồn tùy biến có thể có cơ chế và hành vi lỗi khác. Không bắt một ngoại lệ giữa chừng rồi mặc nhiên kết luận đích chưa hề bị sửa.

Nếu yêu cầu là giữ nguyên dữ liệu gốc khi chuẩn bị kết quả không thành công, có thể xây một danh sách kết quả riêng, kiểm tra rồi chỉ dùng kết quả khi bước chuẩn bị hoàn tất.

Điều này không tự tạo giao dịch nhiều luồng hoặc nhiều tài nguyên; phạm vi bảo đảm phải được thiết kế đúng với yêu cầu.

## 11. Tạo danh sách kết quả mà không sửa nguồn gốc

Một hàm tiện ích có thể biểu đạt rõ lựa chọn tạo kết quả mới:

~~~java
// Đặt phương thức trong một lớp; cần import List, Collection và ArrayList.
static <T> List<T> withInserted(
        List<? extends T> original,
        int index,
        Collection<? extends T> incoming
) {
    List<T> result = new ArrayList<>(original);
    List<T> batch = new ArrayList<>(incoming);
    result.addAll(index, batch);
    return result;
}
~~~

Ví dụ:

~~~java
List<String> original = List.of("A", "D");
List<String> result = withInserted(original, 1, List.of("B", "C"));

System.out.println(result);   // [A, B, C, D]
System.out.println(original); // [A, D]
~~~

Hàm chọn rõ:

- Kết quả là một ArrayList mới có thể sửa.
- Không thay cấu trúc original.
- Chụp incoming thành batch trước khi chèn vào result.
- Dùng đúng quy tắc vị trí chèn của result.

Đây không phải sao chép sâu. Hàm cũng không tự bảo toàn mọi giới hạn riêng của một lớp List chuyên biệt mà original có thể thuộc về, vì kết quả đã được chọn là ArrayList. Nếu ứng dụng cần các giới hạn đó, phải kiểm tra hoặc dùng một kiểu kết quả phù hợp.

Với dữ liệu được chia sẻ giữa nhiều luồng, việc đọc original và incoming để tạo bản sao vẫn cần tuân theo chính sách nhất quán của nguồn.

## 12. Chi phí: vì sao chèn theo nhóm có thể tốt hơn thêm lặp?

Gọi n là size đích, p là vị trí chèn, m là kích thước nhóm.

Ở triển khai ArrayList thông thường, một phép chèn theo nhóm có thể tạo chỗ cho m phần tử bằng cách dịch phần đuôi một lần, rồi đặt nhóm vào khoảng trống. Nếu cần mở rộng mảng nội bộ, còn có chi phí cấp phát và chuyển dữ liệu.

Mô hình chi phí thường gặp:

~~~text
Đọc/chuẩn bị m phần tử nguồn
+ dịch n - p phần tử đuôi
+ đặt m phần tử vào vùng chèn
+ chi phí mở rộng nếu cần
~~~

Trong khi đó, thêm từng phần tử vào giữa có thể phải dịch phần đuôi nhiều lần. Kết quả nội dung có thể giống nhau nếu chỉ số được cập nhật đúng, nhưng số lần di chuyển dữ liệu không nhất thiết giống.

Tham khảo các triển khai và khả năng của [ArrayList.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#addAll(int,java.util.Collection)) và [LinkedList.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html#addAll(int,java.util.Collection)).

Với LinkedList, cách thực hiện dựa trên tìm vị trí và tạo/nối các nút, không phải dịch mảng phần đuôi. Tuy nhiên, tìm vị trí theo chỉ số vẫn có chi phí.

Không coi interface List là một cam kết rằng mọi addAll đều có cùng thuật toán hoặc luôn nhanh hơn mọi vòng lặp. Xét triển khai, kích thước nhóm, vị trí chèn và chi phí nguồn. Với nguồn lớn, việc chụp một batch còn có nhu cầu bộ nhớ riêng.

## 13. Chương trình hoàn chỉnh

Lưu thành **IndexedAddAllDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class IndexedAddAllDemo {
    public static void main(String[] args) {
        List<String> destination = new ArrayList<>(List.of("A", "D"));
        List<String> incoming = new ArrayList<>(List.of("B", "C"));

        boolean changed = destination.addAll(1, incoming);
        System.out.println("Changed = " + changed);
        System.out.println("Inserted = " + destination);
        System.out.println("Size = " + destination.size());
        System.out.println("Source after insertion = " + incoming);

        incoming.set(0, "source-only");
        System.out.println("Source after set = " + incoming);
        System.out.println("Destination after source set = " + destination);

        boolean emptyChanged = destination.addAll(2, List.of());
        System.out.println("Empty source changed = " + emptyChanged);
        System.out.println("After empty insertion = " + destination);

        destination.addAll(0, List.of("H1", "H2"));
        destination.addAll(destination.size(), List.of("T1", "T2"));
        System.out.println("Head and tail batches = " + destination);

        List<String> empty = new ArrayList<>();
        empty.addAll(0, List.of("first", "second"));
        System.out.println("Empty destination = " + empty);

        List<String> repeated = new ArrayList<>(List.of("A", "D"));
        for (String value : List.of("B", "C")) {
            repeated.add(1, value);
        }
        System.out.println("Repeated same-index add = " + repeated);

        LinkedHashSet<String> orderedSource =
                new LinkedHashSet<>(List.of("C", "B", "C"));
        List<String> fromSet = new ArrayList<>(List.of("A", "D"));
        fromSet.addAll(1, orderedSource);
        System.out.println("Ordered set source = " + fromSet);

        List<String> duplicates = new ArrayList<>(List.of("A", "D"));
        duplicates.addAll(1, List.of("B", "B"));
        System.out.println("Duplicate source elements = " + duplicates);

        List<String> original = List.of("A", "D");
        List<String> result = withInserted(original, 1, List.of("B", "C"));
        System.out.println("Copy result = " + result);
        System.out.println("Original unchanged = " + original);

        List<String> overlap =
                new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> captured = new ArrayList<>(overlap.subList(0, 2));
        overlap.addAll(2, captured);
        System.out.println("Captured overlapping range = " + overlap);

        List<Number> numbers = new ArrayList<>(List.of(0.5, 1.5));
        List<Integer> integers = List.of(10, 20);
        numbers.addAll(1, integers);
        System.out.println("Subtype source = " + numbers);

        List<String> validTarget = new ArrayList<>(List.of("A", "D"));
        expect("Negative index", IndexOutOfBoundsException.class,
                () -> validTarget.addAll(-1, List.of("B")));
        expect("Index beyond size", IndexOutOfBoundsException.class,
                () -> validTarget.addAll(3, List.of("B")));
        expect("Null source", NullPointerException.class,
                () -> validTarget.addAll(1, null));

        List<String> fixed = Arrays.asList("A", "D");
        expect("Fixed-size destination", UnsupportedOperationException.class,
                () -> fixed.addAll(1, List.of("B", "C")));
    }

    private static <T> List<T> withInserted(
            List<? extends T> original,
            int index,
            Collection<? extends T> incoming
    ) {
        List<T> result = new ArrayList<>(original);
        List<T> batch = new ArrayList<>(incoming);
        result.addAll(index, batch);
        return result;
    }

    private static void expect(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable operation
    ) {
        try {
            operation.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (RuntimeException exception) {
            if (!expected.isInstance(exception)) {
                throw exception;
            }
            System.out.println(label + " -> "
                    + exception.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 IndexedAddAllDemo.java
java IndexedAddAllDemo
~~~

Stdout:

~~~text
Changed = true
Inserted = [A, B, C, D]
Size = 4
Source after insertion = [B, C]
Source after set = [source-only, C]
Destination after source set = [A, B, C, D]
Empty source changed = false
After empty insertion = [A, B, C, D]
Head and tail batches = [H1, H2, A, B, C, D, T1, T2]
Empty destination = [first, second]
Repeated same-index add = [A, C, B, D]
Ordered set source = [A, C, B, D]
Duplicate source elements = [A, B, B, D]
Copy result = [A, B, C, D]
Original unchanged = [A, D]
Captured overlapping range = [A, B, A, B, C, D]
Subtype source = [0.5, 10, 20, 1.5]
Negative index -> IndexOutOfBoundsException
Index beyond size -> IndexOutOfBoundsException
Null source -> NullPointerException
Fixed-size destination -> UnsupportedOperationException
~~~

Hàm `expect` chỉ bắt loại lỗi đang được kiểm chứng. Nếu nhận một RuntimeException khác, nó ném lại để không ghi nhận sai là đã đạt ca thử; nếu không có lỗi như dự kiến, nó ném AssertionError.

Chương trình không thử hành vi không được xác định của tự addAll hoặc sửa nguồn trong khi addAll đang thực hiện. Với nguồn chồng lấn, nó chụp batch trước.

## 14. Giải thích kết quả

**Changed, Inserted và Size**

Chèn B, C tại vị trí `1` của A, D tạo A, B, C, D và size `4`. Boolean true nói đích đã có thay đổi, không phải đã thêm đúng một phần tử.

**Source after insertion**

Nguồn vẫn là B, C. addAll không di chuyển các phần tử ra khỏi collection nguồn.

**Source after set**

Thay ô đầu của nguồn bằng source-only không biến ô tương ứng của đích thành source-only. Đích không phải một view ghép động theo nguồn.

**Empty source**

Không có phần tử mới để chèn; ArrayList trả false và nội dung vẫn A, B, C, D. Chương trình không từ kết quả này suy ra điều gì về iterator cũ.

**Head and tail**

Nhóm H1, H2 được chèn trước phần đầu, còn T1, T2 thêm cuối. Thứ tự nội bộ của từng batch đều được giữ.

**Repeated same-index add**

Chèn B rồi C nhưng luôn tại chỉ số `1` tạo C trước B. Nó không tương đương addAll tại một vị trí với nguồn B, C.

**Ordered set source**

Nguồn LinkedHashSet chứa C rồi B, nên nhóm chèn có thứ tự C rồi B. Đích không tự sort.

**Duplicate source elements**

Hai B đều xuất hiện trong List đích. Không có cơ chế loại trùng tự động trong phép chèn vào ArrayList này.

**Copy result**

Hàm tiện ích tạo A, B, C, D nhưng original vẫn A, D, vì thao tác diễn ra trên result mới.

**Captured overlapping range**

A, B được chụp từ một đoạn của đích trước khi chèn. Kết quả A, B, A, B, C, D không dựa vào hành vi nguồn tự thay đổi trong lúc chèn.

**Subtype source**

Các Integer `10` và `20` được thêm vào List<Number> giữa hai Double. Wildcard của nguồn cho phép quan hệ kiểu này mà không ép kiểu collection.

**Các ca lỗi**

Chỉ số âm và chỉ số vượt size bị từ chối; nguồn null bị từ chối; đích kích thước cố định không thể chèn cả nhóm. Các ca được tạo riêng để xác định đúng điều kiện đang kiểm tra.

## 15. Kiểm thử và lỗi thường gặp

Kiểm tra một phép chèn theo nhóm bằng ba phần:

1. Prefix trước vị trí chèn giữ nguyên.
2. Batch xuất hiện đúng thứ tự và đủ số lần phần tử.
3. Suffix cũ giữ thứ tự, bắt đầu sau batch.

Ngoài ra, kiểm tra size mới, giá trị boolean và việc có chủ ý sửa nguồn gốc hay tạo kết quả mới.

Các ca quan trọng:

- Đích rỗng và nguồn không rỗng.
- Nguồn rỗng tại một vị trí hợp lệ.
- Chèn đầu, giữa và cuối.
- Nguồn có một phần tử, nhiều phần tử, phần tử trùng.
- Nguồn có thứ tự khác thứ tự tự nhiên.
- Chỉ số `-1`, `size()` và `size() + 1`.
- Nguồn null so với nguồn chứa null.
- Đích cố định kích thước hoặc không cho sửa.
- Nguồn chồng lấn được chụp riêng trước khi dùng.
- Nguồn dùng kiểu con của kiểu phần tử đích.

| Hiểu nhầm | Cách hiểu cần giữ |
| --- | --- |
| addAll ghi đè các phần tử ở vị trí chèn | Nó tạo chỗ và đẩy phần đuôi sang phải. |
| addAll trả số lượng đã thêm | Nó trả boolean về thay đổi của đích. |
| Nguồn phải là List | Collection phù hợp có thể được dùng; thứ tự theo nguồn. |
| Đích là ArrayList nên nhóm luôn được sort | Không có thao tác sắp xếp tự động. |
| Chèn lặp tại cùng chỉ số giữ nguyên thứ tự nguồn | Mỗi lần mới chen trước phần tử ở vị trí đó, có thể đảo thứ tự nhóm. |
| Nguồn rỗng là null | Hai trạng thái hoàn toàn khác. |
| Nguồn bị tiêu thụ hoặc gắn động vào đích | Với các nguồn độc lập, cấu trúc nguồn còn nguyên và đích không thành view theo ô của nguồn. |
| addAll sao chép sâu mọi đối tượng | Các phần tử dạng đối tượng vẫn có thể dùng chung tham chiếu. |
| Tự chèn hoặc dùng view chồng lấn luôn có bảo đảm | Cần tránh dựa vào nguồn bị sửa trong quá trình thực hiện; chụp batch trước khi phù hợp. |
| Một lời gọi đồng nghĩa hoặc đủ tất cả hoặc không đổi gì khi lỗi | Không có bảo đảm giao dịch chung cho mọi List và nguồn. |

## 16. Thực hành

1. Từ `[A, F]`, chèn `[B, C, D, E]` tại vị trí thích hợp. Viết chỉ số trước và sau của F.
2. Dự đoán kết quả khi nguồn là `[C, B]` và khi nguồn là một LinkedHashSet tạo từ `[C, B, C]`.
3. Viết vòng lặp add từng phần tử tương đương về nội dung với addAll, sau đó chỉ ra lỗi khi không tăng vị trí chèn.
4. Tạo một hàm chèn batch nhưng giữ nguyên original. Kiểm tra cả kết quả, original và việc phần tử dạng đối tượng có thể vẫn dùng chung.
5. Chụp một đoạn đầu của danh sách rồi chèn bản chụp vào giữa chính danh sách đó. Giải thích vì sao chụp trước quan trọng.
6. Kiểm thử nguồn rỗng, nguồn null, chèn ở size và chèn ở size + 1 trên một ArrayList có thể sửa.
7. Với một bài toán nhập dữ liệu, xác định thứ tự batch đến từ đâu, nguồn có thể bị đổi khi đọc không, và cần kiểm tra gì trước khi dùng kết quả.

Một phép chèn theo nhóm gồm đích, vị trí và thứ tự nguồn. Khi ba yếu tố này rõ ràng, có thể suy ra toàn bộ kết quả bằng mô hình phần đầu – batch – phần đuôi, rồi xem xét riêng khả năng sửa, nguồn dùng chung và chi phí triển khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-006">replaceAll và sort trên danh sách</a>

<details>
<summary>Xem chi tiết</summary>

# replaceAll và sort trên danh sách

Một danh sách có hai loại thay đổi rất khác nhau:

- **Biến đổi phần tử:** chuẩn hóa chuỗi, tính lại một giá trị hoặc thay một đối tượng bằng kết quả mới.
- **Sắp xếp phần tử:** giữ các phần tử nhưng tổ chức lại vị trí theo một quy tắc so sánh.

`List.replaceAll` phục vụ loại thứ nhất; `List.sort` phục vụ loại thứ hai. Cả hai tác động lên chính danh sách nhận lời gọi và đều trả về `void`.

Bài lấy Java 17 làm mốc. Bốn chương trình hoàn chỉnh đã được biên dịch với `--release 17` và chạy để đối chiếu stdout. Các đoạn rút gọn còn lại minh họa biểu thức hoặc thao tác trong một phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn có thể:

- Dùng UnaryOperator để biến đổi từng phần tử.
- Chọn thứ tự tự nhiên hoặc comparator khi sắp xếp.
- Giải thích tính ổn định của sort.
- Phân biệt khả năng thay phần tử với khả năng tăng/giảm kích thước.
- Dự đoán ảnh hưởng tới tham chiếu dùng chung và xử lý khi phép biến đổi thất bại.

## 1. Phân biệt biến đổi giá trị và đổi vị trí

Với dữ liệu:

```text
[3, 1, 2]
```

thay mỗi số bằng số đối cho:

```text
[-3, -1, -2]
```

Các vị trí được thay bằng kết quả tính từ phần tử cũ. Chưa có bước sắp xếp.

Sắp xếp tăng dần dữ liệu mới cho:

```text
[-3, -2, -1]
```

Đây là một bước riêng, quyết định vị trí tương đối của các phần tử.

| Đặc điểm | replaceAll | sort |
| --- | --- | --- |
| Đầu vào chính | Hàm biến đổi một phần tử | Quy tắc so sánh hai phần tử |
| Kết quả tại mỗi vị trí | Có thể được thay bằng giá trị/đối tượng khác | Các phần tử được tổ chức lại theo thứ tự |
| Số phần tử trong thao tác thành công hợp lệ | Giữ nguyên | Giữ nguyên |
| Tự lọc hoặc loại trùng | Không | Không |
| Giá trị trả về | `void` | `void` |
| Cần danh sách hỗ trợ sửa đổi | Có | Có |
| Có cần tăng/giảm kích thước? | Không | Không |

Những bảo đảm về nội dung ở đây giả định hàm truyền vào chỉ thực hiện vai trò của nó, không tự thêm/xóa cùng danh sách hoặc gây các tác dụng phụ phá vỡ điều kiện sử dụng.

## 2. replaceAll và UnaryOperator

Chữ ký:

```java
default void replaceAll(UnaryOperator<E> operator)
```

`UnaryOperator<E>` biểu diễn một hàm nhận một giá trị kiểu E và trả về kết quả cùng kiểu E. Có thể hiểu phần thao tác cốt lõi là:

```text
E apply(E value)
```

Đây là ý nghĩa của giao diện hàm, không phải một phương thức cần tự thêm vào List.

Tài liệu [UnaryOperator của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/UnaryOperator.html) mô tả đây là phép toán trên một toán hạng và trả cùng kiểu. Với replaceAll, kết quả đó được dùng để thay phần tử.

Ví dụ:

```java
names.replaceAll(value -> value.strip());
```

hoặc dùng tham chiếu phương thức:

```java
names.replaceAll(String::strip);
```

Đoạn minh họa giả định `names` là List<String> hỗ trợ thay phần tử và không chứa null.

### Kết quả hàm phải được trả về

Hàm biến đổi cần cung cấp giá trị thay thế. Với thân khối, phải có return phù hợp:

```java
names.replaceAll(value -> {
    String cleaned = value.strip();
    return cleaned;
});
```

Nếu cần chuyển từ List<String> sang List<Integer>, replaceAll trên danh sách chuỗi không phải công cụ phù hợp: kết quả phải giữ kiểu phần tử của danh sách. Khi đổi kiểu dữ liệu, hãy tạo một danh sách kết quả bằng cách biến đổi phù hợp.

### replaceAll khác forEach

```java
names.forEach(value -> value.strip());
```

không tự lưu chuỗi được strip trở lại danh sách. Hàm gọi trên String trả về một chuỗi kết quả, nhưng trong cách dùng Consumer này, giá trị trả về bị bỏ qua.

Với replaceAll, chính hợp đồng của thao tác sẽ lưu kết quả hàm vào vị trí phần tử.

### Không phải thao tác thay theo một giá trị cũ cố định

Hai API có tên gần nhau:

| API | Vai trò |
| --- | --- |
| `list.replaceAll(operator)` | Áp dụng hàm cho từng phần tử và lưu kết quả |
| `Collections.replaceAll(list, oldValue, newValue)` | Thay các phần tử phù hợp với oldValue bằng newValue |

Đừng nhầm replaceAll của List với thao tác thay chuỗi theo biểu thức chính quy của String. Đối tượng nhận lời gọi và kiểu đối số quyết định hợp đồng đang dùng.

## 3. sort và Comparator

Chữ ký:

```java
default void sort(Comparator<? super E> comparator)
```

Có hai cách dùng chính:

```java
numbers.sort(null);
numbers.sort(Comparator.naturalOrder());
```

Cả hai diễn đạt sắp theo thứ tự tự nhiên khi phần tử đáp ứng điều kiện so sánh.

Với thứ tự riêng:

```java
tickets.sort(Comparator.comparingInt(Ticket::priority));
```

Các đoạn cần import Comparator và các kiểu dữ liệu tương ứng.

### sort(null) không có nghĩa bỏ qua sắp xếp

Ở API này, comparator null là tín hiệu dùng thứ tự tự nhiên. Điều đó khác với:

```text
replaceAll(null)
```

vì replaceAll không có hàm biến đổi để áp dụng trong trường hợp đó.

### Hiểu dấu của comparator

```text
compare(first, second) < 0  -> first đứng trước second
compare(first, second) = 0  -> ngang hàng theo tiêu chí
compare(first, second) > 0  -> first đứng sau second
```

Comparator phải mô tả một quan hệ thứ tự hợp lệ và nhất quán. Đừng lấy hiệu hai trường int/long rồi ép kiểu để tạo kết quả, vì tràn số hoặc mất dữ liệu có thể làm sai dấu. Các hàm như comparingInt hoặc comparingLong giúp diễn đạt tiêu chí đúng kiểu.

Comparator cũng không nên sửa chính danh sách, thay khóa so sánh trong lúc đang sort hoặc dựa vào trạng thái bên ngoài thay đổi tùy ý.

### Tính ổn định

List.sort bảo đảm ổn định: các phần tử được comparator coi ngang hàng giữ thứ tự tương đối trước đó. Sort cần danh sách cho phép sửa đổi nhưng không bắt buộc nó tăng/giảm được kích thước. Các đặc tả này nằm ở [List.sort Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

Ổn định không có nghĩa:

- Thứ tự mọi phần tử được giữ nguyên.
- Các phần tử trùng bị loại.
- Dữ liệu tự có một tiêu chí phụ mà bạn chưa khai báo.

Nếu comparator chỉ xét priority, hai đối tượng có cùng priority được coi ngang hàng, dù chúng không equals nhau.

## 4. Chương trình biến đổi rồi sắp xếp

Tạo tệp **`ListReplaceSortDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ListReplaceSortDemo {
    public static void main(String[] args) {
        List<String> names =
                new ArrayList<>(List.of("  beta ", "ALPHA", " beta"));

        names.replaceAll(value -> value.strip().toLowerCase(Locale.ROOT));
        System.out.println("normalized = " + names);
        names.sort(null);
        System.out.println("sorted = " + names);
        System.out.println("size = " + names.size());

        List<Integer> transformFirst = new ArrayList<>(List.of(3, 1, 2));
        transformFirst.replaceAll(value -> -value);
        transformFirst.sort(null);

        List<Integer> sortFirst = new ArrayList<>(List.of(3, 1, 2));
        sortFirst.sort(null);
        sortFirst.replaceAll(value -> -value);

        System.out.println("transform then sort = " + transformFirst);
        System.out.println("sort then transform = " + sortFirst);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ListReplaceSortDemo.java
java -Dfile.encoding=UTF-8 ListReplaceSortDemo
```

Stdout đã kiểm chứng:

```text
normalized = [beta, alpha, beta]
sorted = [alpha, beta, beta]
size = 3
transform then sort = [-3, -2, -1]
sort then transform = [-1, -2, -3]
```

### Nhóm chuỗi

1. strip loại phần khoảng trắng ở hai đầu theo quy tắc của phương thức.
2. toLowerCase với Locale.ROOT tạo chính sách chữ thường không phụ thuộc locale mặc định của máy chạy.
3. replaceAll thay từng phần tử bằng kết quả chuẩn hóa.
4. sort(null) xếp các chuỗi mới theo thứ tự tự nhiên.
5. Hai lần beta vẫn tồn tại; kích thước vẫn là 3.

Các chuỗi là đối tượng bất biến. Chương trình không sửa nội dung một String cũ; nó thay tham chiếu ở vị trí danh sách bằng chuỗi kết quả.

### Thứ tự hai bước có thể làm đổi kết quả

Với các số 3, 1, 2:

- Biến đổi thành số đối trước rồi sort cho `[-3, -2, -1]`.
- Sort trước rồi biến đổi thành số đối cho `[-1, -2, -3]`.

Một phép biến đổi có thể thay đổi quan hệ lớn/nhỏ. Vì vậy, không được đổi chỗ hai bước chỉ vì thấy chúng đều “xử lý tất cả phần tử”.

Nếu mục tiêu là thứ tự của dữ liệu sau chuẩn hóa, hãy chuẩn hóa trước rồi sắp theo chính dữ liệu hoặc khóa đã chuẩn hóa.

## 5. Sắp xếp ổn định, tiêu chí phụ và null

Giả sử các Ticket có id và priority. Danh sách ban đầu:

```text
T3(priority=2)
T1(priority=1)
T2(priority=2)
T4(priority=1)
```

Sắp theo priority tăng dần cần cho nhóm 1 đứng trước nhóm 2. Trong từng nhóm bằng priority, thứ tự cũ được giữ:

```text
T1, T4, T3, T2
```

Nếu thêm id làm tiêu chí phụ:

```text
T1, T4, T2, T3
```

thì T2 đứng trước T3 vì tiêu chí id, chứ không phải vì tính ổn định tự phát sinh cách xếp đó.

### Chương trình hoàn chỉnh

Tạo tệp **`StableListSortDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class StableListSortDemo {
    public record Ticket(String id, int priority) {
    }

    public static void main(String[] args) {
        List<Ticket> tickets = new ArrayList<>(List.of(
                new Ticket("T3", 2),
                new Ticket("T1", 1),
                new Ticket("T2", 2),
                new Ticket("T4", 1)));

        tickets.sort(Comparator.comparingInt(Ticket::priority));
        System.out.println("priority only = " + ids(tickets));

        tickets.sort(Comparator.comparingInt(Ticket::priority)
                .thenComparing(Ticket::id));
        System.out.println("priority then id = " + ids(tickets));
        System.out.println("ticket count = " + tickets.size());

        List<Integer> nullable =
                new ArrayList<>(Arrays.asList(3, null, 1, null, 2));
        nullable.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("nulls last = " + nullable);
    }

    private static List<String> ids(List<Ticket> tickets) {
        return tickets.stream().map(Ticket::id).toList();
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 StableListSortDemo.java
java -Dfile.encoding=UTF-8 StableListSortDemo
```

Stdout đã kiểm chứng:

```text
priority only = [T1, T4, T3, T2]
priority then id = [T1, T4, T2, T3]
ticket count = 4
nulls last = [1, 2, 3, null, null]
```

### Đọc các kết quả

- Chỉ xét priority: T1 vẫn trước T4, T3 vẫn trước T2.
- Có id làm tiêu chí phụ: các phần tử trước đó ngang priority được xét tiếp theo id.
- Số Ticket vẫn bằng 4.
- Danh sách số có null dùng một chính sách tường minh: số không null tăng dần, null ở cuối.

### Comparator cho null khác comparator null

Hai biểu thức có ý nghĩa khác nhau:

```java
nullable.sort(null);
```

yêu cầu thứ tự tự nhiên, còn:

```java
nullable.sort(Comparator.nullsLast(Comparator.naturalOrder()));
```

cấp một comparator có chính sách xử lý null.

Nếu thuật toán cần so tới null mà quy tắc không hỗ trợ, có thể gặp lỗi. Không mặc định rằng mọi danh sách có null đều được sắp tự nhiên một cách hợp lệ.

### Đảo chiều đúng phạm vi

Nếu muốn các số không null giảm dần nhưng null vẫn ở cuối, một chính sách phù hợp là:

```java
Comparator<Integer> order =
        Comparator.nullsLast(Comparator.reverseOrder());
```

Đảo cả comparator nullsLast sau khi tạo có thể đảo luôn vị trí của null. Hãy xác định đang đảo phần thứ tự nào.

## 6. Danh sách cố định kích thước vẫn có thể hỗ trợ hai thao tác

Cần phân biệt:

- Không tăng/giảm kích thước.
- Không cho thay thế phần tử.
- Không cho bất kỳ thao tác sửa đổi nào.

Một danh sách có số vị trí cố định vẫn có thể cho phép thay giá trị từng vị trí và tổ chức lại thứ tự.

Ví dụ, Arrays.asList tạo một List có kích thước cố định và được chống lưng bởi mảng nguồn. Thay đổi phần tử ở danh sách và mảng được phản ánh qua dữ liệu dùng chung. Xem [Arrays.asList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

### Bảng thực hành

| Nguồn | replaceAll/sort | Thêm/xóa |
| --- | --- | --- |
| ArrayList thông thường | Hỗ trợ | Hỗ trợ |
| LinkedList thông thường | Hỗ trợ | Hỗ trợ |
| Arrays.asList | Có thể thay phần tử và sắp xếp | Không đổi kích thước |
| List.of hoặc List.copyOf | Không cho sửa đổi | Không cho sửa đổi |
| Collections.unmodifiableList | Không cho sửa qua góc nhìn này | Không cho sửa qua góc nhìn này |

Giao diện List không đồng nghĩa đối tượng nào cũng nhận mọi thao tác sửa. Cần biết danh sách được tạo ở đâu và nó chia sẻ dữ liệu với ai.

### Chương trình hoàn chỉnh

Tạo tệp **`FixedSizeListOperationsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FixedSizeListOperationsDemo {
    public static void main(String[] args) {
        String[] source = {" B ", "a", " C "};
        List<String> view = Arrays.asList(source);

        view.replaceAll(value -> value.strip().toLowerCase(Locale.ROOT));
        view.sort(null);

        System.out.println("view = " + view);
        System.out.println("source = " + Arrays.toString(source));

        try {
            view.add("d");
        } catch (UnsupportedOperationException exception) {
            System.out.println("fixed-size add = "
                    + exception.getClass().getSimpleName());
        }

        List<String> readOnly = List.of("b", "a");

        try {
            readOnly.replaceAll(value -> value);
        } catch (UnsupportedOperationException exception) {
            System.out.println("read-only replaceAll = "
                    + exception.getClass().getSimpleName());
        }

        try {
            readOnly.sort(null);
        } catch (UnsupportedOperationException exception) {
            System.out.println("read-only sort = "
                    + exception.getClass().getSimpleName());
        }

        List<String> copy = new ArrayList<>(readOnly);
        copy.sort(null);

        System.out.println("read-only original = " + readOnly);
        System.out.println("sorted copy = " + copy);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 FixedSizeListOperationsDemo.java
java -Dfile.encoding=UTF-8 FixedSizeListOperationsDemo
```

Stdout đã kiểm chứng:

```text
view = [a, b, c]
source = [a, b, c]
fixed-size add = UnsupportedOperationException
read-only replaceAll = UnsupportedOperationException
read-only sort = UnsupportedOperationException
read-only original = [b, a]
sorted copy = [a, b]
```

### Những điểm đã kiểm chứng

- View từ Arrays.asList được chuẩn hóa rồi sort thành a, b, c.
- Mảng nguồn cũng thành a, b, c vì dữ liệu được chia sẻ.
- Thêm d bị từ chối do cần đổi kích thước.
- List.of từ chối cả replaceAll với hàm đồng nhất và sort.
- Tạo một ArrayList bản sao cho phép sort mà không đổi tập phần tử của danh sách gốc.

Không nên dùng một hàm đồng nhất hoặc dữ liệu đã có thứ tự để dò khả năng sửa một danh sách không cho sửa đổi. Các API sửa đổi có thể từ chối ngay cả khi bạn cho rằng kết quả nhìn thấy sẽ không khác.

## 7. Hai phương thức trả void và thay danh sách nhận lời gọi

Cách tổ chức:

```java
names.replaceAll(String::strip);
names.sort(Comparator.naturalOrder());
```

Không có danh sách kết quả mới được trả về để gán hoặc nối chuỗi lời gọi.

Nếu hai biến cùng trỏ tới một danh sách:

```java
List<String> alias = names;
names.replaceAll(String::strip);
```

thì alias cũng nhìn thấy các phần tử đã được thay trong cùng đối tượng danh sách.

### Muốn giữ nguyên danh sách nguồn

Tạo danh sách kết quả riêng trước:

```java
List<String> result = new ArrayList<>(source);
result.replaceAll(String::strip);
result.sort(Comparator.naturalOrder());
```

Đoạn này giả định nguồn không chứa null và được đọc trong điều kiện ổn định phù hợp.

Bản sao cấu trúc không tự sao chép sâu các phần tử. Với String bất biến, thay vị trí trong result không sửa nội dung String của source. Với đối tượng khả biến, một operator sửa trực tiếp thuộc tính của đối tượng chung vẫn có thể tác động dữ liệu mà source đang tham chiếu.

### replaceAll có thể trả cùng tham chiếu hoặc một tham chiếu mới

Một hàm có thể:

- Trả lại chính đối tượng nhận vào.
- Trả một đối tượng mới cùng kiểu.
- Trả null nếu chính sách danh sách và nghiệp vụ cho phép.

Không có quy tắc rằng mọi lần replaceAll đều tạo các đối tượng mới hoặc đều sửa đối tượng cũ. Điều đó do hàm biến đổi quyết định; API chịu trách nhiệm dùng kết quả để thay phần tử.

## 8. Khi hàm biến đổi ném lỗi: đừng giả định tự rollback

Tài liệu [List.replaceAll Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#replaceAll(java.util.function.UnaryOperator)) quy định lỗi hoặc ngoại lệ lúc chạy từ operator được chuyển cho bên gọi. API này không được mô tả như một giao dịch tự hoàn tác toàn bộ danh sách khi thất bại.

Xét phép biến đổi:

```text
value -> 20 / value
```

Với đầu vào:

```text
[2, 4, 0, 5]
```

hai phần tử đầu có thể đã được thay trước khi phép chia cho 0 thất bại. Chương trình sau xác định rõ trường hợp đó trên ArrayList.

### Chương trình hoàn chỉnh

Tạo tệp **`PartialReplaceAllDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class PartialReplaceAllDemo {
    public static void main(String[] args) {
        List<Integer> values = new ArrayList<>(List.of(2, 4, 0, 5));
        List<Integer> alias = values;

        try {
            values.replaceAll(value -> 20 / value);
        } catch (ArithmeticException exception) {
            System.out.println("in-place failure = "
                    + exception.getClass().getSimpleName());
        }
        System.out.println("values after failure = " + values);
        System.out.println("alias after failure = " + alias);

        List<Integer> original = new ArrayList<>(List.of(2, 4, 0, 5));
        try {
            List<Integer> result = transformedCopy(original);
            System.out.println("result = " + result);
        } catch (ArithmeticException exception) {
            System.out.println("copy transform failure = "
                    + exception.getClass().getSimpleName());
        }
        System.out.println("original preserved = " + original);

        System.out.println("successful copy = "
                + transformedCopy(List.of(2, 4, 5)));
    }

    private static List<Integer> transformedCopy(List<Integer> source) {
        List<Integer> candidate = new ArrayList<>(source);
        candidate.replaceAll(value -> 20 / value);
        candidate.sort(null);
        return candidate;
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 PartialReplaceAllDemo.java
java -Dfile.encoding=UTF-8 PartialReplaceAllDemo
```

Stdout đã kiểm chứng:

```text
in-place failure = ArithmeticException
values after failure = [10, 5, 0, 5]
alias after failure = [10, 5, 0, 5]
copy transform failure = ArithmeticException
original preserved = [2, 4, 0, 5]
successful copy = [4, 5, 10]
```

### Lượt sửa trực tiếp

- 2 được thay thành 10.
- 4 được thay thành 5.
- Đến 0 thì phép chia ném ArithmeticException.
- Danh sách trong ví dụ giữ trạng thái `[10, 5, 0, 5]`.
- Alias trỏ cùng danh sách nên thấy cùng kết quả đã sửa một phần.

Đây là trạng thái được kiểm chứng trên lớp và phép toán trong chương trình. Không được dùng nó để buộc mọi lớp triển khai khác phải cập nhật theo cùng một cách khi có lỗi.

### Lượt sửa bản sao

Hàm transformedCopy:

1. Tạo một ArrayList mới từ nguồn.
2. Biến đổi trên bản sao.
3. Sort nếu bước biến đổi thành công.
4. Chỉ trả kết quả khi toàn bộ chuỗi bước hoàn tất.

Với đầu vào có 0, hàm không trả kết quả; danh sách original vẫn nguyên dữ liệu. Với đầu vào hợp lệ 2, 4, 5, nó trả `[4, 5, 10]`.

Trong mô hình này, dữ liệu Integer là bất biến và operator không có tác dụng phụ ngoài phép tính. Vì vậy, lỗi trên candidate không làm đổi phần tử của nguồn.

### Giới hạn của cách dùng bản sao

- Sao chép nông không ngăn hàm sửa trực tiếp một đối tượng khả biến dùng chung.
- Không hoàn tác được gửi thông báo, ghi tệp hoặc gọi dịch vụ mà operator đã làm.
- Không tự tạo snapshot nguyên tử nếu nguồn đang được sửa không theo giao thức phối hợp.
- Việc công bố kết quả cho nhiều luồng còn là một bước cần thiết kế riêng.

Tạo bản sao là cách kiểm soát quyền sở hữu dữ liệu và thời điểm sử dụng kết quả, không phải một cơ chế giao dịch tổng quát.

## 9. Null, loại dữ liệu và các điều kiện lỗi

### Phân biệt ba vấn đề null của replaceAll

| Trường hợp | Câu hỏi cần giải quyết |
| --- | --- |
| Bản thân operator là null | Không có hàm để áp dụng; lời gọi không hợp lệ |
| Một phần tử đầu vào là null | Hàm có xử lý đầu vào đó không? |
| Hàm trả về null | Nguồn và nghiệp vụ có chấp nhận phần tử null không? |

Ví dụ, String::strip không xử lý một tham chiếu String null. Nếu ứng dụng muốn giữ hoặc thay dữ liệu thiếu, phải chọn chính sách rõ.

Một phép chuẩn hóa minh họa:

```java
names.replaceAll(value -> value == null ? "" : value.strip());
```

Đoạn này chọn chuỗi rỗng làm kết quả cho null; đó là quyết định nghiệp vụ của ví dụ, không phải hành vi mặc định của replaceAll.

### Sort cần các phần tử so sánh được theo quy tắc đang dùng

Với thứ tự tự nhiên, các phần tử phải hỗ trợ phép so sánh phù hợp với nhau. Với comparator riêng, comparator phải xử lý các cặp phần tử mà nó nhận.

Những lỗi có thể liên quan:

- Đối tượng hoặc phần tử không có kiểu so sánh phù hợp.
- Null không được quy tắc xử lý.
- Comparator vi phạm hợp đồng thứ tự.
- Comparator ném ngoại lệ từ logic riêng.
- Danh sách không hỗ trợ sửa đổi.
- Nguồn bị sửa không hợp lệ trong lúc thao tác.

Không dựa vào trạng thái sau khi sort thất bại như thể nó chắc chắn đã hoàn tác hoặc chắc chắn chỉ sửa một tiền tố. Chi tiết còn phụ thuộc lớp thực tế và điểm phát sinh lỗi.

### Không sửa cùng danh sách bên trong hàm

Một operator hoặc comparator tự gọi add, remove, sort hoặc replaceAll lên chính danh sách đang được xử lý có thể phá vỡ điều kiện thao tác.

Nếu cần một quy trình phức tạp, hãy tách việc tính kết quả và áp dụng thay đổi. Đừng dựa vào cơ chế phát hiện sửa đổi như một cách điều khiển luồng hợp lệ.

## 10. Hiệu quả và thiết kế hàm truyền vào

### Với replaceAll

Nếu mỗi lần biến đổi có chi phí cố định và cơ chế duyệt/thay thế phù hợp, tổng công việc tăng theo số phần tử.

Hàm quá nặng, có I/O hoặc truy cập nguồn chậm có thể quyết định toàn bộ chi phí, dù thao tác ngoài nhìn như một lời gọi ngắn.

Nên ưu tiên:

- Hàm có ý nghĩa rõ, không phụ thuộc số lần hoặc thứ tự tác dụng phụ để đúng.
- Không sửa chính nguồn.
- Kiểm tra dữ liệu cần thiết trước nếu có thể.
- Tách xử lý bên ngoài khỏi biến đổi phần tử.

### Với sort

Sort có thể gọi comparator nhiều lần và với nhiều cặp theo thứ tự mà bạn không nên phụ thuộc. Comparator không phải hàm biến đổi “mỗi phần tử đúng một lần”.

Nếu khóa so sánh rất đắt để tính, cân nhắc chuẩn bị khóa hoặc một mô hình dữ liệu trung gian phù hợp trước khi sort. Tránh đặt thao tác gọi dịch vụ hoặc thay đổi dữ liệu vào comparator.

Không suy từ tên giao diện List rằng mọi lớp sẽ sort theo cùng một thuật toán hoặc có cùng chi phí. Tính ổn định là phần hợp đồng cần dùng; chi tiết triển khai phải xem theo lớp.

### Sắp xếp không duy trì thứ tự cho các lần thêm về sau

Một lần sort chỉ xử lý trạng thái lúc thao tác thành công. Nếu sau đó thêm phần tử hoặc sửa giá trị quyết định thứ tự, danh sách không tự giữ có thứ tự theo comparator cũ.

Cần sắp xếp lại khi yêu cầu dùng thứ tự, hoặc chọn mô hình dữ liệu khác phù hợp với nhu cầu duy trì trật tự.

## 11. Những lỗi thường gặp

### Tưởng replaceAll lọc phần tử

Nó thay mỗi phần tử bằng kết quả, không bỏ phần tử. Trả null không có nghĩa xóa vị trí đó.

### Tưởng sort loại trùng

Danh sách vẫn có thể giữ nhiều phần tử so bằng nhau. Tính ổn định chỉ quy định thứ tự tương đối của chúng.

### Gán kết quả của phương thức void

Cả replaceAll và sort đều không trả List mới. Dùng hai câu lệnh riêng hoặc tự tạo bản sao khi cần kết quả độc lập.

### Gọi trên List.of vì “chỉ đổi thứ tự, không thêm/xóa”

Thay thế và sắp xếp vẫn là sửa danh sách. Danh sách không cho sửa đổi có thể từ chối ngay.

### Cho rằng Arrays.asList hoàn toàn bất biến

Nó cố định kích thước nhưng có thể thay phần tử. Ví dụ đã cho thấy replaceAll và sort cũng phản ánh lên mảng nguồn.

### Dùng comparator bằng phép trừ

Sai dấu do tràn có thể phá hợp đồng thứ tự. Dùng hàm so sánh đúng kiểu hoặc các hàm tạo comparator.

### Mong thứ tự ổn định tự sắp theo một tiêu chí phụ

Nếu cần id hoặc một thuộc tính khác để phá hòa, phải khai báo tiêu chí đó. Ổn định giữ thứ tự cũ trong nhóm ngang hàng.

### Gọi sort trước chuẩn hóa rồi mặc định vẫn đúng

Phép biến đổi có thể làm đổi quan hệ thứ tự. Xác định khóa muốn sắp trước khi chọn thứ tự các bước.

### Bắt lỗi rồi coi dữ liệu vẫn nguyên vẹn

Thử nghiệm ArrayList cho thấy replaceAll có thể đã sửa một phần. Khi cần giữ nguồn, kiểm soát bản sao và tác dụng phụ từ đầu.

## 12. Thực hành

### Bài 1: dự đoán quy trình chuỗi

Tạo một ArrayList từ:

```text
[" Z ", "b", " A", "b "]
```

Chuẩn hóa bằng strip và chữ thường theo Locale.ROOT, rồi sort tự nhiên.

Trước khi chạy, hãy dự đoán:

- Nội dung sau replaceAll.
- Nội dung sau sort.
- Số phần tử còn lại.
- Hai phần tử b có bị loại đi không.

### Bài 2: tính ổn định hay tiêu chí phụ?

Với Ticket trong chương trình:

1. Chỉ sort theo priority.
2. Đối chiếu thứ tự trong từng nhóm priority.
3. Sau đó thêm id giảm dần làm tiêu chí phụ.
4. Giải thích kết quả nào đến từ comparator và kết quả nào đến từ tính ổn định.

### Bài 3: thay nguồn dữ liệu

Chạy các thao tác phù hợp trên:

- Một ArrayList bản sao.
- Một view từ Arrays.asList.
- Một List.of.

Ghi rõ thao tác nào sửa được, thao tác nào bị từ chối, và dữ liệu nguồn nào cùng thay đổi qua quan hệ chia sẻ.

### Bài 4: chính sách null

Tạo danh sách cho phép null và chọn một chính sách:

- Giữ null nhưng đặt ở cuối khi sort.
- Thay null bằng chuỗi rỗng trước khi sort.
- Từ chối toàn bộ dữ liệu trước khi bắt đầu sửa.

Mô tả ý nghĩa của từng chính sách. Không coi ba lựa chọn là cùng một kết quả nghiệp vụ.

### Bài 5: lỗi giữa chừng

Từ PartialReplaceAllDemo:

1. Đặt số 0 ở đầu, giữa và cuối trong các thử nghiệm riêng.
2. Theo dõi trạng thái ArrayList sau lỗi.
3. Làm lại trên bản sao cấu trúc.
4. Giải thích vì sao bản sao với Integer khác tình huống operator sửa thuộc tính một đối tượng khả biến dùng chung.
5. Xác định thời điểm được phép công bố kết quả cho bên sử dụng.

Tiêu chí hoàn thành là phân biệt đúng biến đổi với sắp xếp, chọn hàm phù hợp, giữ rõ quyền sửa dữ liệu và không giả định nguồn có khả năng sửa hoặc khả năng hoàn tác mà hợp đồng chưa cam kết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-007">subList với khoảng đầu bao gồm, cuối không bao gồm</a>

<details>
<summary>Xem chi tiết</summary>

# subList với khoảng đầu bao gồm, cuối không bao gồm

## 1. subList chọn một khoảng vị trí như thế nào?

`List.subList(fromIndex, toIndex)` trả về một danh sách nhìn vào đoạn liên tiếp của danh sách ban đầu. Khoảng được viết là `[fromIndex, toIndex)`:

- Dấu `[` ở đầu nghĩa là lấy phần tử tại `fromIndex`.
- Dấu `)` ở cuối nghĩa là dừng trước `toIndex`, không lấy phần tử tại chỉ số đó.
- Các phần tử được chọn vẫn theo thứ tự của danh sách ban đầu.

Với danh sách `[A, B, C, D, E]`:

| Chỉ số trong danh sách gốc | 0 | 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| Phần tử | A | B | C | D | E |
| Thuộc khoảng `[1, 4)`? | Không | Có | Có | Có | Không |

Vì vậy, `list.subList(1, 4)` cho đoạn `[B, C, D]`. Nó không chọn `E`, cũng không hiểu đối số thứ hai là “lấy 4 phần tử”.

Đoạn minh họa trong một phương thức, với các import `java.util.List` và `java.util.ArrayList`:

```java
List<String> letters =
        new ArrayList<>(List.of("A", "B", "C", "D", "E"));

List<String> middle = letters.subList(1, 4);

// middle = [B, C, D]
// middle.size() = 3
```

Có hai kiến thức cần đi cùng nhau: cách tính khoảng và bản chất kết quả. Kết quả là một khung nhìn (view) có liên hệ với danh sách gốc, không mặc nhiên là một bản sao độc lập. Đọc đúng hai biên nhưng hiểu sai quan hệ này vẫn có thể làm chương trình sửa nhầm dữ liệu. [List.subList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)).

## 2. Vì sao dùng đầu bao gồm và cuối không bao gồm?

### 2.1. Độ dài được tính bằng phép trừ hai biên

Nếu khoảng hợp lệ, kích thước ban đầu của kết quả là:

```text
length = toIndex - fromIndex
```

Ví dụ:

| Lời gọi trên danh sách 5 phần tử | Các chỉ số được lấy | Kết quả | Kích thước |
|---|---|---|---|
| `subList(0, 2)` | 0, 1 | `[A, B]` | 2 |
| `subList(1, 4)` | 1, 2, 3 | `[B, C, D]` | 3 |
| `subList(4, 5)` | 4 | `[E]` | 1 |
| `subList(0, 5)` | 0, 1, 2, 3, 4 | `[A, B, C, D, E]` | 5 |
| `subList(2, 2)` | Không có | `[]` | 0 |
| `subList(5, 5)` | Không có | `[]` | 0 |

Muốn lấy `count` phần tử bắt đầu ở `from`, biên cuối là `from + count`, sau khi đã bảo đảm phép tính không tràn số và không vượt kích thước danh sách. Không dùng `from + count - 1` làm `toIndex`; cách đó làm thiếu phần tử cuối.

Nếu bài toán cho một chỉ số cuối có bao gồm, chẳng hạn muốn lấy từ chỉ số 1 đến hết chỉ số 3, thì khoảng tương ứng là `[1, 4)`. Việc chuyển từ “chỉ số cuối được lấy” sang “biên dừng” phải được thực hiện rõ ràng.

### 2.2. Hai khoảng kề nhau dùng chung biên

Có thể chia danh sách ở vị trí `split` thành:

```java
List<String> left = letters.subList(0, split);
List<String> right = letters.subList(split, letters.size());
```

Đây là các biểu thức minh họa, giả sử `letters` và `split` đã được khai báo hợp lệ. Phần tử tại `split` chỉ thuộc phía phải. Hai khoảng không bỏ sót hoặc lặp lại phần tử ở điểm chia.

Ví dụ, với `split = 2` trên `[A, B, C, D, E]`, hai đoạn là `[A, B]` và `[C, D, E]`. Nếu chỉ đọc, đây là cách chia thuận tiện. Nếu định sửa cấu trúc một đoạn trong khi giữ đoạn kia, cần xử lý quan hệ giữa các view được giải thích phía dưới.

### 2.3. size() là biên hợp lệ, nhưng không phải chỉ số phần tử hợp lệ

Danh sách có `n` phần tử có chỉ số phần tử từ 0 đến `n - 1`, còn các vị trí ranh giới chạy từ 0 đến `n`.

Với `n = 5`, biên 5 nằm ngay sau `E`. Nó được dùng làm `toIndex` để lấy tới hết danh sách, nhưng `get(5)` vẫn sai. Việc cho phép `subList(5, 5)` không tạo ra một phần tử ở chỉ số 5; nó chỉ chọn khoảng rỗng ở cuối.

## 3. Điều kiện hợp lệ và khoảng rỗng

Mọi lời gọi hợp lệ đều phải thỏa:

```text
0 <= fromIndex <= toIndex <= list.size()
```

Ba điều cần kiểm tra riêng:

1. Biên đầu không âm.
2. Biên đầu không đứng sau biên cuối.
3. Biên cuối không vượt kích thước hiện tại.

`subList` không tự cắt biên cho vừa danh sách. Trên danh sách có 5 phần tử, `subList(0, 100)` không có nghĩa “lấy tối đa 100 phần tử”; đó là khoảng sai.

Với `ArrayList`, biên ngoài phạm vi gây `IndexOutOfBoundsException`, còn cặp biên đúng phạm vi nhưng đảo thứ tự như `subList(4, 2)` gây `IllegalArgumentException`. Các ví dụ ngoại lệ trong bài dùng `ArrayList`; khi xử lý một triển khai khác, xem hợp đồng cụ thể của triển khai đó thay vì dựa vào chuỗi thông báo lỗi. [ArrayList.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#subList(int,int)).

Khoảng rỗng hoàn toàn hợp lệ khi hai biên bằng nhau và nằm trong `[0, size()]`. Với danh sách rỗng, `subList(0, 0)` là khoảng hợp lệ duy nhất.

Một view rỗng không mặc nhiên là danh sách không sửa đổi được. Nếu danh sách nền cho phép thêm và view vẫn hợp lệ, có thể thêm qua view rỗng; phần tử được chèn vào vị trí ranh giới mà view đại diện. Vì vậy, đừng suy ra khả năng sửa đổi chỉ từ kết quả `isEmpty()`.

## 4. Chỉ số của kết quả bắt đầu lại từ 0

Giả sử:

```java
List<String> source =
        new ArrayList<>(List.of("A", "B", "C", "D", "E"));
List<String> part = source.subList(1, 4);
```

Đây là đoạn khởi tạo minh họa trong một phương thức. Khi chưa có thay đổi cấu trúc, phép ánh xạ là:

```text
chỉ số trong source = fromIndex + chỉ số trong part
```

| Chỉ số trong part | Chỉ số tương ứng trong source | Giá trị |
|---|---|---|
| 0 | 1 | B |
| 1 | 2 | C |
| 2 | 3 | D |

`part.get(0)` trả `B`, không trả `A`. `part.get(3)` gây lỗi vì `part.size()` là 3. Mặc dù `source.get(3)` hợp lệ, chỉ số 3 không hợp lệ khi dùng để đọc phần tử trong `part`.

Tương tự, `part.set(1, "X")` tác động đến vị trí tương ứng với chỉ số 2 trong `source`. Các thao tác như `get`, `set`, `remove(int)`, `indexOf` trên view dùng hệ chỉ số của chính view. Nếu `part.indexOf("D")` trả 2, đó là vị trí trong `part`, không phải chỉ số 3 của danh sách gốc.

Phép ánh xạ cộng biên giúp giải thích cách view hoạt động; không nên lưu các chỉ số đã tính như danh tính bền vững của phần tử. Sau thêm hoặc xóa, vị trí của phần tử còn lại có thể thay đổi.

## 5. Chương trình đầy đủ: khoảng, biên và phân trang

Chương trình sau dùng các API có trong Java 17. Lưu thành `SubListRangeDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SubListRangeDemo {
    private static void showRange(List<String> source, int from, int to) {
        try {
            List<String> part = source.subList(from, to);
            System.out.println("[" + from + ", " + to + ") -> "
                    + part + "; size = " + part.size());
        } catch (IndexOutOfBoundsException | IllegalArgumentException ex) {
            System.out.println("[" + from + ", " + to + ") -> "
                    + ex.getClass().getSimpleName());
        }
    }

    private static <T> List<T> pageCopy(
            List<T> source, int pageIndex, int pageSize) {
        if (pageIndex < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("Invalid page");
        }
        int size = source.size();
        long offset = (long) pageIndex * pageSize;
        if (offset >= size) {
            return new ArrayList<>();
        }
        int from = (int) offset;
        int to = (int) Math.min((long) size, offset + pageSize);
        return new ArrayList<>(source.subList(from, to));
    }

    public static void main(String[] args) {
        List<String> source =
                new ArrayList<>(List.of("A", "B", "C", "D", "E"));
        showRange(source, 1, 4);
        showRange(source, 0, source.size());
        showRange(source, 2, 2);
        showRange(source, source.size(), source.size());
        showRange(source, -1, 2);
        showRange(source, 0, 6);
        showRange(source, 4, 2);

        List<String> part = source.subList(1, 4);
        System.out.println("part.get(0) = " + part.get(0));
        System.out.println("part.get(2) = " + part.get(2));
        try {
            part.get(part.size());
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("get(size) -> IndexOutOfBoundsException");
        }

        for (int page = 0; page < 4; page++) {
            System.out.println("page " + page + " = "
                    + pageCopy(source, page, 2));
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListRangeDemo.java
java SubListRangeDemo
```

Kết quả chuẩn:

```text
[1, 4) -> [B, C, D]; size = 3
[0, 5) -> [A, B, C, D, E]; size = 5
[2, 2) -> []; size = 0
[5, 5) -> []; size = 0
[-1, 2) -> IndexOutOfBoundsException
[0, 6) -> IndexOutOfBoundsException
[4, 2) -> IllegalArgumentException
part.get(0) = B
part.get(2) = D
get(size) -> IndexOutOfBoundsException
page 0 = [A, B]
page 1 = [C, D]
page 2 = [E]
page 3 = []
```

Các lời gọi đầu kiểm tra cả trường hợp thường và các biên đặc biệt. Hai khoảng `[2, 2)` và `[5, 5)` cùng rỗng nhưng đại diện cho hai vị trí khác nhau. Các lời gọi sai không được “sửa hộ” thành khoảng hợp lệ.

Phương thức `pageCopy` minh họa một chính sách phân trang cụ thể:

- `pageIndex` bắt đầu từ 0; `pageSize` phải dương.
- Trang vượt cuối trả danh sách rỗng.
- Trang cuối chỉ lấy phần còn lại: với 5 phần tử và kích thước trang 2, các khoảng là `[0, 2)`, `[2, 4)`, `[4, 5)`.
- Phép nhân được thực hiện bằng `long` ngay từ đầu bằng `(long) pageIndex * pageSize`, tránh tràn số `int` trước khi kiểm tra biên.
- Kết quả được bọc bằng `new ArrayList<>(...)` để tách cấu trúc danh sách trả về khỏi danh sách nguồn.
- Ví dụ giả sử `source` khác `null` và không bị sửa cấu trúc trong lúc tính kích thước rồi lấy khoảng.

Đây là chính sách của `pageCopy`, không phải hành vi tự có của `subList`. Một hàm khác có thể chọn báo lỗi khi yêu cầu trang ngoài phạm vi; cần quyết định rõ trong API thay vì vô tình phụ thuộc vào ngoại lệ.

## 6. Kết quả là view: thay đổi có thể phản ánh hai chiều

### 6.1. Thay thế phần tử qua view tác động vào nguồn

Đoạn minh họa:

```java
List<String> source =
        new ArrayList<>(List.of("A", "B", "C", "D", "E"));
List<String> part = source.subList(1, 4);

part.set(1, "c");

// part = [B, c, D]
// source = [A, B, c, D, E]
```

Không có một mảng phần tử độc lập được hứa tạo ra cho `part`. View cung cấp cách thao tác trên một vùng của nguồn.

Chiều ngược lại cũng có ý nghĩa. Với `ArrayList`, thay thế một phần tử bằng `source.set(index, value)` không thay đổi kích thước và không phải thay đổi cấu trúc. Nếu phần tử nằm trong vùng đang nhìn, view thấy giá trị mới.

Điều này khác với thêm hoặc xóa trực tiếp qua nguồn. Không gộp mọi phương thức sửa đổi vào một quy tắc “gốc đổi thì view tự cập nhật ổn định”; thay đổi cấu trúc bên ngoài view có giới hạn sử dụng riêng.

### 6.2. Thêm hoặc xóa qua view có thể sửa cấu trúc nguồn

Với một `ArrayList` cho phép sửa đổi:

- `part.remove(0)` xóa phần tử đầu của view khỏi nguồn.
- `part.add(value)` thêm ở cuối view, tức chèn trước phần tử nằm ngay sau khoảng hiện tại trong nguồn.
- `part.clear()` xóa tất cả phần tử thuộc khoảng đang nhìn khỏi nguồn.
- Kích thước của view có thể thay đổi sau các thao tác này.

Do đó, cặp biên lúc tạo view không có nghĩa view mãi mãi giữ đúng số phần tử ban đầu. Sau khi thêm hoặc xóa thông qua chính view, nó tiếp tục đại diện cho đoạn đã thay đổi.

Nếu chỉ muốn bỏ biến view, không gọi `clear()`. `clear()` là thao tác xóa dữ liệu khỏi danh sách nền, không phải thao tác ngắt liên kết hoặc giải phóng riêng một đối tượng view.

### 6.3. Xóa một khoảng bằng subList(...).clear()

Đoạn minh họa:

```java
List<Integer> values =
        new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));

values.subList(1, 4).clear();

// values = [0, 4, 5]
```

Khoảng `[1, 4)` gồm các giá trị 1, 2, 3 trong danh sách này. Lệnh xóa cả đoạn, không xóa phần tử tại biên 4. Cách này tránh phải tự lặp nhiều lần với chỉ số đang dịch chuyển.

Không nhầm với `values.clear()`, vốn xóa toàn bộ danh sách. Cũng không nhầm với việc tạo bản sao rồi xóa bản sao; khi đó cấu trúc nguồn sẽ không bị xóa.

## 7. subList lồng nhau dùng biên theo view trực tiếp

Giả sử danh sách gốc là `[A, B, C, D, E, F]`:

```java
List<String> middle = root.subList(1, 5);  // [B, C, D, E]
List<String> inner = middle.subList(1, 3); // [C, D]
```

Đây là đoạn minh họa với `root` đã được khởi tạo. Biên `1, 3` của lời gọi thứ hai áp dụng cho `middle`, không áp dụng lại cho `root`. Trước khi có thay đổi cấu trúc, `inner.get(0)` tương ứng `middle.get(1)`, rồi tương ứng `root.get(2)`.

Nếu khoảng ngoài bắt đầu ở `a` và khoảng trong bắt đầu ở `c`, biên bắt đầu hiệu dụng trong nguồn là `a + c`. Cách cộng này giúp kiểm tra phạm vi, nhưng một `inner` vẫn là view liên kết; việc lồng nhiều lần không tạo ra bản sao.

Khi cần sửa cấu trúc qua view lồng nhau, thực hiện qua đúng view sẽ dùng tiếp và quản lý các view liên quan cẩn thận. Đừng giữ tùy ý nhiều view chồng lấn rồi thêm/xóa thông qua các đường khác nhau. Ví dụ chạy sau minh họa thay đổi qua `inner` và đọc các view tổ tiên tương ứng.

## 8. Chương trình đầy đủ: sửa qua view và view lồng nhau

Lưu thành `SubListViewDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SubListViewDemo {
    public static void main(String[] args) {
        List<String> source =
                new ArrayList<>(List.of("A", "B", "C", "D", "E"));
        List<String> part = source.subList(1, 4);
        System.out.println("initial part = " + part);

        part.set(1, "c");
        System.out.println("after part.set: source = " + source);

        source.set(3, "d");
        System.out.println("after source.set: part = " + part);

        part.add(part.size(), "X");
        System.out.println("after part.add: source = " + source);
        System.out.println("after part.add: part = " + part);

        String removed = part.remove(0);
        System.out.println("removed = " + removed);
        System.out.println("after part.remove: source = " + source);
        System.out.println("after part.remove: part = " + part);

        part.clear();
        System.out.println("after part.clear: source = " + source);
        System.out.println("after part.clear: part = " + part);

        part.add("Z");
        System.out.println("after adding to empty part: source = " + source);
        System.out.println("after adding to empty part: part = " + part);

        List<Integer> numbers =
                new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        numbers.subList(1, 4).clear();
        System.out.println("range cleared = " + numbers);

        List<String> root =
                new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));
        List<String> middle = root.subList(1, 5);
        List<String> inner = middle.subList(1, 3);
        inner.set(0, "C*");
        inner.remove(1);
        System.out.println("nested root = " + root);
        System.out.println("nested middle = " + middle);
        System.out.println("nested inner = " + inner);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListViewDemo.java
java SubListViewDemo
```

Kết quả chuẩn:

```text
initial part = [B, C, D]
after part.set: source = [A, B, c, D, E]
after source.set: part = [B, c, d]
after part.add: source = [A, B, c, d, X, E]
after part.add: part = [B, c, d, X]
removed = B
after part.remove: source = [A, c, d, X, E]
after part.remove: part = [c, d, X]
after part.clear: source = [A, E]
after part.clear: part = []
after adding to empty part: source = [A, Z, E]
after adding to empty part: part = [Z]
range cleared = [0, 4, 5]
nested root = [A, B, C*, E, F]
nested middle = [B, C*, E]
nested inner = [C*]
```

Theo dõi vùng dữ liệu ở các bước quan trọng:

| Thao tác | Ý nghĩa trong view | Hệ quả trong nguồn |
|---|---|---|
| `part.set(1, "c")` | Thay phần tử thứ hai của view | Giá trị `C` ở chỉ số 2 của nguồn thành `c` |
| `source.set(3, "d")` | View thấy phần tử tương ứng được thay | `part` trở thành `[B, c, d]` |
| `part.add(part.size(), "X")` | Chèn tại biên cuối hiện tại của view | `X` đứng trước `E` trong nguồn |
| `part.remove(0)` | Xóa `B` | `B` cũng biến mất khỏi nguồn |
| `part.clear()` | Xóa hết đoạn `[c, d, X]` | Nguồn chỉ còn `[A, E]` |
| `part.add("Z")` khi view rỗng | Thêm tại ranh giới view còn giữ | Nguồn thành `[A, Z, E]` |

Phần lồng nhau thay `C` thành `C*`, rồi xóa `D` qua `inner`. Nguồn, view giữa và view trong cùng phản ánh đoạn đã đổi. Chương trình không sửa cấu trúc trực tiếp qua `root` rồi tiếp tục giả định các view cũ vẫn hợp lệ.

## 9. Khi nguồn bị sửa cấu trúc ngoài view

### 9.1. Ranh giới sử dụng được bảo đảm

Hợp đồng `subList` không bảo đảm ngữ nghĩa của view sau khi danh sách nền bị sửa cấu trúc bằng đường khác với chính view đó. Việc thêm/xóa làm thay đổi kích thước là những ví dụ trực tiếp.

Khái niệm thay đổi cấu trúc không chỉ có nghĩa là kích thước tăng hoặc giảm. Hợp đồng còn tính đến thay đổi làm việc duyệt đang diễn ra có thể cho kết quả sai. Cần xem thao tác và triển khai cụ thể; không dựa riêng vào việc `size()` giữ nguyên để kết luận view vẫn hợp lệ.

Đoạn dưới minh họa một cách dùng sai; không phải chương trình có kết quả đầu ra ổn định để dựa vào:

```java
List<String> source =
        new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> part = source.subList(1, 3);

source.add("E"); // Sửa cấu trúc qua nguồn, ngoài part.

// Không tiếp tục dùng part như một view còn hợp lệ.
// Hãy bỏ view này hoặc tạo lại view theo dữ liệu và biên mới.
```

Ngay cả thêm ở cuối, bên ngoài đoạn `[1, 3)`, cũng không làm cách dùng đó trở nên an toàn theo hợp đồng. Không suy luận rằng view vẫn hợp lệ vì “không đụng tới các phần tử đang nhìn”.

Nếu xóa qua một view chị em, view đang giữ cũng có thể không còn dùng được. Ví dụ, hai view đều trỏ vào cùng nguồn; sửa cấu trúc qua view thứ nhất là thay đổi bên ngoài đường thao tác của view thứ hai.

### 9.2. ConcurrentModificationException chỉ là dấu hiệu phát hiện lỗi

Các triển khai thường dùng cơ chế kiểm tra thay đổi cấu trúc để phát hiện view đã không còn đồng bộ với nguồn. Với `ArrayList`, một thao tác tiếp theo trên view cũ có thể ném `ConcurrentModificationException`, kể cả thao tác đọc như `size()` hoặc thao tác in làm phát sinh duyệt phần tử.

Tên ngoại lệ không có nghĩa phải có hai luồng mới xảy ra lỗi. Một luồng tạo view, sửa cấu trúc nguồn trực tiếp, rồi dùng lại view cũ đã có thể gặp tình huống này.

Không xây dựng luồng điều khiển theo cách “cứ dùng, nếu có ngoại lệ thì bắt và thử lại”. Điều cần sửa là vòng đời view và đường sửa đổi dữ liệu. Việc không thấy ngoại lệ trong một phép thử không mở rộng hợp đồng sử dụng của `subList`. [Giới hạn của view trong ArrayList.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#subList(int,int)), [cách AbstractList theo dõi thay đổi của view](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html#subList(int,int)).

Nếu đã có thay đổi bên ngoài, hãy tạo view mới từ trạng thái nguồn hiện tại và tính lại biên theo ý định nghiệp vụ. Đừng dùng lại biên cũ một cách máy móc: sau chèn/xóa, cùng cặp chỉ số có thể chỉ vào các phần tử khác.

## 10. Khi nào cần bản sao độc lập?

Nếu cần trả một đoạn dữ liệu để bên nhận có thể giữ lâu hoặc sửa danh sách riêng, tạo một danh sách mới từ view:

```java
List<String> copy = new ArrayList<>(source.subList(from, to));
```

Đây là biểu thức minh họa với nguồn và biên hợp lệ. `copy.add`, `copy.remove` hoặc `copy.set` sẽ thay đổi cấu trúc hoặc vị trí chứa tham chiếu của `copy`, không sửa tương ứng các vị trí chứa tham chiếu trong `source`.

Nếu cần kết quả không cho phép thêm, xóa hoặc thay phần tử qua API danh sách:

```java
List<String> snapshot = List.copyOf(source.subList(from, to));
```

`List.copyOf` không chấp nhận phần tử `null`; cần chọn cách sao chép phù hợp nếu vùng nguồn có thể chứa `null`. Tên `snapshot` ở đây nói về các phần tử/tham chiếu được chọn tại thời điểm tạo kết quả, không hứa sao chép sâu trạng thái của mọi đối tượng phần tử. [ArrayList(Collection)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#%3Cinit%3E(java.util.Collection)), [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

| Cách tạo kết quả | Còn phụ thuộc cấu trúc danh sách nguồn? | Bên nhận có thể sửa danh sách kết quả? | Tự sao chép sâu đối tượng phần tử? |
|---|---|---|---|
| `source.subList(from, to)` | Có | Tùy khả năng sửa đổi của nguồn | Không |
| `new ArrayList<>(source.subList(from, to))` | Không | Có | Không |
| `List.copyOf(source.subList(from, to))` | Không phản ánh việc nguồn thay danh sách phần tử về sau | Không | Không |
| `Collections.unmodifiableList(source.subList(from, to))` | Có, vẫn bọc một view | Không sửa được qua lớp bọc này | Không |

`Collections.unmodifiableList` là lớp bọc ngăn sửa qua tham chiếu trả về, không biến view thành một bản chụp độc lập. Nếu nguồn còn bị thay đổi, các hạn chế về view vẫn cần được tôn trọng. [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)).

### 10.1. Sao chép danh sách không đồng nghĩa sao chép phần tử

Giả sử mỗi phần tử là một đối tượng `Item` có thể đổi tên. Cả view và bản sao có thể cùng chứa tham chiếu đến một `Item`.

- Đổi tên chính `Item` đó có thể được nhìn thấy từ mọi danh sách còn giữ tham chiếu.
- Dùng `source.set(index, newItem)` thay tham chiếu trong nguồn; một bản sao đã tạo trước đó vẫn giữ tham chiếu cũ.
- Thêm phần tử vào bản sao không làm nguồn dài thêm.

Nếu yêu cầu là độc lập cả trạng thái phần tử, cần sao chép từng đối tượng theo quy tắc của kiểu dữ liệu hoặc dùng các kiểu phần tử bất biến phù hợp. `subList`, hàm dựng `ArrayList(Collection)` và `List.copyOf` không tự giải quyết yêu cầu đó.

### 10.2. Khả năng sửa đổi của nguồn vẫn chi phối view

`subList` không tự cấp thêm quyền sửa đổi:

- View của `new ArrayList<>(...)` hỗ trợ các thao tác sửa đổi thông thường.
- View của danh sách từ `List.of(...)` không cho thêm, xóa hoặc thay phần tử.
- View của danh sách từ `Arrays.asList(array)` có thể thay phần tử bằng `set`, nhưng không cho thay đổi kích thước bằng thêm/xóa.
- Khả năng chứa `null`, ràng buộc kiểu phần tử và các điều kiện khác còn phụ thuộc danh sách nền.

Nếu muốn một bản sao có thể sửa đổi từ nguồn không sửa được, `new ArrayList<>(view)` tạo cấu trúc mới để sửa riêng. [Arrays.asList và danh sách kích thước cố định](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

## 11. Chương trình đầy đủ: bản sao và phần tử dùng chung

Lưu thành `SubListCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SubListCopyDemo {
    private static final class Item {
        private String name;

        private Item(String name) {
            this.name = name;
        }

        private void rename(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) {
        List<Item> source = new ArrayList<>(List.of(
                new Item("A"), new Item("B"), new Item("C")));
        List<Item> view = source.subList(1, 3);
        List<Item> copy = new ArrayList<>(view);
        List<Item> snapshot = List.copyOf(view);

        source.get(1).rename("B*");
        System.out.println("after item rename: view = " + view);
        System.out.println("after item rename: copy = " + copy);
        System.out.println("after item rename: snapshot = " + snapshot);

        source.set(1, new Item("NEW"));
        System.out.println("after source.set: view = " + view);
        System.out.println("after source.set: copy = " + copy);
        System.out.println("after source.set: snapshot = " + snapshot);

        copy.add(new Item("COPY"));
        System.out.println("after copy.add: source = " + source);
        System.out.println("after copy.add: copy = " + copy);

        try {
            snapshot.add(new Item("X"));
        } catch (UnsupportedOperationException ex) {
            System.out.println("snapshot.add -> UnsupportedOperationException");
        }

        source.add(new Item("D"));
        System.out.println("after source.add: source = " + source);
        System.out.println("after source.add: copy = " + copy);
        System.out.println("after source.add: snapshot = " + snapshot);
        // Không dùng view sau thay đổi cấu trúc qua source.
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListCopyDemo.java
java SubListCopyDemo
```

Kết quả chuẩn:

```text
after item rename: view = [B*, C]
after item rename: copy = [B*, C]
after item rename: snapshot = [B*, C]
after source.set: view = [NEW, C]
after source.set: copy = [B*, C]
after source.set: snapshot = [B*, C]
after copy.add: source = [A, NEW, C]
after copy.add: copy = [B*, C, COPY]
snapshot.add -> UnsupportedOperationException
after source.add: source = [A, NEW, C, D]
after source.add: copy = [B*, C, COPY]
after source.add: snapshot = [B*, C]
```

Đọc sự khác biệt giữa sửa đối tượng và thay tham chiếu:

1. `rename("B*")` sửa một đối tượng `Item` đang được ba danh sách cùng tham chiếu; cả ba đều in tên mới.
2. `source.set(1, new Item("NEW"))` thay phần tử ở nguồn. View nhìn thấy `NEW`, còn `copy` và `snapshot` vẫn giữ đối tượng cũ tên `B*`.
3. `copy.add(...)` chỉ làm bản sao dài thêm.
4. `snapshot.add(...)` gây `UnsupportedOperationException` vì `List.copyOf` tạo danh sách không sửa đổi được.
5. `source.add(...)` ở cuối sửa cấu trúc nguồn. Chương trình vẫn dùng được các bản sao nhưng chủ động kết thúc việc sử dụng view cũ.

Một danh sách không sửa đổi được vẫn có thể chứa phần tử có trạng thái thay đổi được. Do đó, “không được gọi `add`” và “mọi giá trị quan sát được luôn bất biến” là hai yêu cầu khác nhau.

## 12. Chọn phạm vi và vòng đời view trong thực tế

`subList` hữu ích khi xử lý một đoạn ngay trong phạm vi có kiểm soát: đọc một khoảng, tìm kiếm trong đoạn, sắp xếp riêng đoạn trên nguồn cho phép thay phần tử, hoặc xóa đoạn bằng `clear`. Trước khi truyền view cho một hàm khác, phải biết hàm đó chỉ đọc hay có thể sửa danh sách.

Nếu một hàm nhận `List<T>` rồi gọi `clear()`, truyền `source.subList(from, to)` cho hàm đó đồng nghĩa cho phép hàm xóa đoạn tương ứng trong nguồn, khi nguồn hỗ trợ thao tác này. Kiểu tham số `List` không tự diễn đạt quyền sở hữu dữ liệu.

Với kết quả trả về từ dịch vụ, trang dữ liệu giữ lâu, bộ nhớ đệm hoặc xử lý trì hoãn, thường cần quyết định rõ có giữ quan hệ với nguồn hay trả cấu trúc độc lập. Không chỉ nhìn vào vài phần tử đang in ra giống nhau để coi view và bản sao là tương đương.

Các triển khai view như view của `ArrayList` còn giữ liên hệ với danh sách nền. Một view nhỏ được giữ lâu có thể làm cấu trúc nguồn lớn còn được giữ trong bộ nhớ. Đây là hệ quả của việc giữ tham chiếu, không phải bằng chứng rằng mọi `subList` đều luôn rò rỉ bộ nhớ. Nếu chỉ cần giữ lâu một đoạn nhỏ và không cần liên hệ với nguồn, bản sao riêng thường diễn đạt đúng ý định hơn.

Về chi phí, không suy ra rằng mọi cách tạo view, truy cập hay sửa view đều có cùng độ phức tạp trên mọi `List`. Bản sao cần đi qua các phần tử được chọn và có nơi lưu riêng; view tránh việc tự sao chép toàn bộ phần tử chỉ để tạo một đoạn nhìn, nhưng vẫn dùng cơ chế truy cập và sửa đổi của triển khai nền.

## 13. Các lỗi cần nhận ra khi đọc mã

| Cách hiểu hoặc cách viết | Vấn đề | Cách kiểm tra |
|---|---|---|
| `subList(1, 4)` lấy cả phần tử chỉ số 4 | Nhầm biên cuối có bao gồm | Liệt kê chỉ số 1, 2, 3; độ dài là 3 |
| `subList(from, count)` để lấy `count` phần tử | Đối số thứ hai bị hiểu sai | Tính biên cuối từ `from + count` với kiểm tra thích hợp |
| Dùng `size() - 1` làm biên cuối để lấy hết | Bỏ mất phần tử cuối | Dùng `size()` làm biên dừng |
| Dùng `part.get(fromIndex)` để đọc phần tử đầu | Trộn chỉ số nguồn và view | Phần tử đầu của view có chỉ số 0 |
| Cho rằng `subList(i, i)` là lỗi | Không nhận ra khoảng rỗng | Kiểm tra `i` nằm trong `[0, size()]` |
| Gọi `part.clear()` để “bỏ view” | Xóa dữ liệu thật trong nguồn | Chỉ dùng `clear` khi muốn xóa đoạn |
| Thêm vào nguồn rồi tiếp tục dùng view cũ | Vượt giới hạn hợp đồng view | Quản lý đường sửa cấu trúc, hoặc dùng bản sao |
| Bắt `ConcurrentModificationException` rồi tiếp tục dùng view | Không sửa nguyên nhân view mất hiệu lực | Bỏ view cũ và tính lại theo trạng thái hiện tại |
| Bọc bằng `unmodifiableList` rồi cho rằng đã sao chép | Lớp bọc vẫn liên kết với view nền | Chọn `new ArrayList` hoặc `List.copyOf` khi cần tách cấu trúc |
| Cho rằng bản sao danh sách tạo các phần tử mới | Nhầm sao chép cấu trúc với sao chép sâu | Kiểm tra phần tử có thay đổi trạng thái được và còn dùng chung tham chiếu không |

## 14. Tự kiểm tra bằng dự đoán

Mỗi câu độc lập, dùng danh sách mới:

```java
List<String> data =
        new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));
```

Đây là đoạn khởi tạo cho bài tập trong một phương thức.

1. `data.subList(2, 5)` có những phần tử nào, kích thước bao nhiêu?
2. Muốn lấy đúng ba phần tử bắt đầu ở chỉ số 1, hai biên là gì?
3. Muốn lấy hai phần tử cuối, hai biên là gì?
4. `data.subList(data.size(), data.size())` có hợp lệ không?
5. Sau `List<String> part = data.subList(1, 4);`, `part.get(1)` là gì?
6. Nếu gọi `part.clear()` ngay sau bước khởi tạo ở câu 5, `data` còn gì?
7. Nếu tạo `List<String> copy = new ArrayList<>(data.subList(1, 4));` rồi `copy.clear()`, `data` có bị xóa đoạn không?
8. Nếu giữ một view rồi gọi `data.add("G")`, có thể tiếp tục dựa vào view cũ theo hợp đồng không?

Đáp án:

1. `[C, D, E]`, kích thước 3; chỉ số 5 chứa `F` bị loại.
2. `[1, 4)`; biên cuối bằng 1 + 3.
3. `[data.size() - 2, data.size())`, tức `[4, 6)`. Công thức này cần nguồn có ít nhất hai phần tử.
4. Hợp lệ, kết quả rỗng.
5. `C`; chỉ số 1 của view tương ứng chỉ số 2 của nguồn.
6. `[A, E, F]`; các phần tử `B, C, D` bị xóa qua view.
7. Không; `copy` có cấu trúc riêng nên nguồn vẫn `[A, B, C, D, E, F]`.
8. Không; nguồn đã bị sửa cấu trúc ngoài view. Cần tạo lại view hợp lệ hoặc dùng bản sao theo yêu cầu.

Khi dùng `subList`, hãy xác định đồng thời ba điều: khoảng `[fromIndex, toIndex)`, hệ chỉ số bắt đầu lại từ 0 của kết quả, và việc kết quả còn là view của nguồn. Ba điều này quyết định đúng phần tử nào được chọn, thao tác nào sửa dữ liệu gốc và kết quả có thể được giữ lại trong bao lâu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-008">equals và hashCode phụ thuộc thứ tự của List</a>

<details>
<summary>Xem chi tiết</summary>

# equals và hashCode phụ thuộc thứ tự của List

Một `List` biểu diễn **một dãy**. Vì vậy, khi hỏi hai danh sách có bằng nhau không, Java xét cả phần tử và vị trí của chúng, không chỉ hỏi “có cùng những giá trị nào?”.

Ví dụ:

~~~text
[A, B] và [A, B] -> có thể equals, dù là hai đối tượng List khác nhau
[A, B] và [B, A] -> không equals
[A, A, B] và [A, B] -> không equals
~~~

`hashCode` của List cũng kết hợp hash của các phần tử theo thứ tự. Nhưng “phụ thuộc thứ tự” không có nghĩa mọi dãy khác nhau luôn có hash khác nhau. Hash là một giá trị `int` có thể va chạm; nó không thay thế phép `equals`.

Hiểu hai hợp đồng này đặc biệt cần thiết khi kiểm thử, loại các dãy trùng, dùng List trong HashSet hoặc dùng List làm khóa của HashMap.

## 1. Ba mức so sánh khác nhau

| Cách so sánh | Câu hỏi được trả lời |
| --- | --- |
| `left == right` | Hai biến có trỏ đến cùng đối tượng không? |
| `left.equals(right)` | Hai danh sách có cùng dãy phần tử theo hợp đồng List không? |
| `left.hashCode() == right.hashCode()` | Hai giá trị băm có bằng nhau không? Không đủ để kết luận hai List bằng nhau. |

Ví dụ:

~~~java
// Đoạn mã trong một phương thức; cần các import java.util tương ứng.
List<String> first = new ArrayList<>(List.of("A", "B"));
List<String> second = new LinkedList<>(List.of("A", "B"));

System.out.println(first == second);      // false
System.out.println(first.equals(second)); // true
~~~

Hai đối tượng có cách lưu khác nhau nhưng cùng là List, cùng size và cùng giá trị ở từng vị trí.

Không cần biến LinkedList thành ArrayList mới so sánh được về nội dung dãy. Hợp đồng List được thiết kế để các triển khai tuân thủ có thể so sánh theo cùng một ý nghĩa.

Ngược lại, chỉ nhìn hai biến có cùng kiểu khai báo `List<String>` cũng không chứng minh chúng bằng nhau. Cần kiểm tra dữ liệu.

## 2. Hợp đồng equals: cùng loại tập hợp dãy, cùng size, cùng từng vị trí

Về nội dung, hai List bằng nhau khi:

1. Đối tượng được so sánh cũng là một List.
2. Hai List có cùng số phần tử.
3. Các cặp phần tử tương ứng, từ đầu đến cuối, bằng nhau theo `equals`, có xử lý `null`.

Có thể hình dung:

~~~text
left[0]  tương ứng right[0]
left[1]  tương ứng right[1]
...
left[n-1] tương ứng right[n-1]
~~~

Phép so sánh phần tử có ý nghĩa tương tự `Objects.equals(leftElement, rightElement)`.

Các quy tắc equals và công thức hash có thứ tự được quy định trong [List API của Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#equals(java.lang.Object)).

### Thứ tự có ý nghĩa

~~~java
List<String> first = List.of("A", "B");
List<String> reversed = List.of("B", "A");

System.out.println(first.equals(reversed)); // false
~~~

Ở chỉ số `0`, A không bằng B. Việc hai dãy có cùng size và cùng các giá trị phân biệt không làm chúng bằng nhau.

Nếu hoán đổi hai lần xuất hiện có giá trị bằng nhau, dãy quan sát được có thể vẫn bằng nhau. “Xét thứ tự” không có nghĩa mọi thao tác đổi chỗ nội bộ đều tạo một dãy khác theo equals.

### Số lần xuất hiện cũng có ý nghĩa

~~~java
List<String> first = List.of("A", "A", "B");
List<String> second = List.of("A", "B", "B");

System.out.println(first.equals(second)); // false
~~~

Hai dãy đều có A và B, đều size `3`, nhưng khác tại vị trí `1`.

Không bỏ qua phần tử lặp khi đánh giá bằng nhau của List. Đây là khác biệt quan trọng với việc chuyển dữ liệu thành một Set.

## 3. Equals giữa các triển khai và khả năng chỉnh sửa

Cùng dãy nội dung có thể bằng nhau giữa:

- ArrayList.
- LinkedList.
- Danh sách từ Arrays.asList.
- Danh sách không cho sửa từ List.of hoặc List.copyOf.
- Một view List có cùng nội dung hiện tại.

Ví dụ:

~~~java
List<String> mutable = new ArrayList<>(List.of("A", "B"));
List<String> unmodifiable = List.of("A", "B");

System.out.println(mutable.equals(unmodifiable)); // true
~~~

Khả năng thêm, xóa hay thay phần tử không phải một thành phần trong hợp đồng equals của List. Capacity của một ArrayList cũng không phải một phần của giá trị dãy.

Không đồng nhất “bằng nhau” với:

- Cùng lớp runtime.
- Cùng sức chứa.
- Cùng khả năng chỉnh sửa.
- Cùng cách quản lý bộ nhớ.
- Cùng đối tượng.

Những đặc tính đó có thể quan trọng với một yêu cầu khác, nhưng không được đưa vào kết luận List.equals nếu không phải hợp đồng đã chọn.

### List không bằng Set chỉ vì cùng phần tử

~~~java
List<String> sequence = List.of("A", "B");
Set<String> members = Set.of("A", "B");

System.out.println(sequence.equals(members)); // false
~~~

List so sánh một dãy với một List khác. Set có một hợp đồng giá trị khác. Không dùng một Collection bất kỳ làm đối tượng thay thế cho List trong phép so sánh theo dãy.

## 4. Null và kiểu của phần tử

### Null được so sánh theo vị trí

Nếu nguồn cho phép null:

~~~java
List<String> first = Arrays.asList("A", null, "B");
List<String> second = new ArrayList<>(first);
List<String> moved = Arrays.asList(null, "A", "B");

System.out.println(first.equals(second)); // true
System.out.println(first.equals(moved));  // false
~~~

Hai phần tử null ở cùng vị trí được coi là bằng nhau. Null ở vị trí khác có thể làm dãy khác.

Không dùng List.of để tạo một danh sách có null, vì chính factory đó từ chối null trước khi đến bước so sánh.

### Nội dung hiển thị giống nhau chưa đủ

~~~java
List<Integer> integers = List.of(1);
List<Long> longs = List.of(1L);

System.out.println(integers);             // [1]
System.out.println(longs);                // [1]
System.out.println(integers.equals(longs)); // false
~~~

Integer `1` và Long `1L` không bằng nhau theo equals của các kiểu wrapper đó.

Mặt khác, khác kiểu generic khai báo không tự làm hai danh sách khác nhau. Ví dụ một `List<Object>` và một `List<String>` thực sự chứa các String bằng nhau theo từng vị trí có thể bằng nhau. Equals nhận Object và xét nội dung runtime theo hợp đồng, không quyết định bằng tên tham số generic đã viết trong mã.

### Cùng nội dung khác tham chiếu của phần tử

Hai đối tượng String khác tham chiếu nhưng cùng nội dung có thể equals. Vì List dùng equals của phần tử, nó không đòi mọi cặp phần tử phải là cùng đối tượng.

Nếu một lớp phần tử không cung cấp ý nghĩa equals theo giá trị mà vẫn dùng hành vi mặc định của Object, hai đối tượng riêng biệt có thể không bằng nhau dù trường dữ liệu nhìn giống nhau. List không tự phân tích mọi trường để tạo một định nghĩa equals mới cho phần tử.

## 5. Công thức hashCode của List

List kết hợp hash theo thứ tự bằng phép tích lũy:

~~~java
// Hàm minh họa trong một lớp; cần import java.util.List và java.util.Objects.
static int listHash(List<?> values) {
    int hash = 1;
    for (Object value : values) {
        hash = 31 * hash + Objects.hashCode(value);
    }
    return hash;
}
~~~

`Objects.hashCode(value)` trả `0` nếu value là null; nếu khác null, dùng hashCode của phần tử.

Các điểm cần giữ:

- Giá trị khởi đầu là `1`.
- Mỗi bước nhân kết quả trước đó với `31`.
- Sau đó cộng hash của phần tử hiện tại.
- Duyệt theo đúng thứ tự của List.

Đây là công thức hợp đồng, không phải một thuật toán băm bảo mật. Xem [List.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#hashCode()).

Với List rỗng, vòng lặp không chạy nên hash là `1`.

### Tính từng bước với A, B

Hash của String `"A"` là `65`, của `"B"` là `66`.

~~~text
[A, B]:

h0 = 1
h1 = 31 * 1  + 65 = 96
h2 = 31 * 96 + 66 = 3042
~~~

Đảo thứ tự:

~~~text
[B, A]:

h0 = 1
h1 = 31 * 1  + 66 = 97
h2 = 31 * 97 + 65 = 3072
~~~

Phần tử ở trước ảnh hưởng tới các lần nhân sau đó, nên phép tích lũy không đơn giản là cộng hash của mọi phần tử theo cách bỏ qua thứ tự.

### Tràn int là một phần của phép tính Java

Hash dùng `int` 32 bit. Với danh sách dài hoặc hash phần tử lớn, phép nhân/cộng có thể vượt miền giá trị có dấu và được tính theo quy tắc số nguyên của Java.

Không tự đổi hàm thành phép cộng kiểm tra tràn rồi ném ngoại lệ, hoặc đổi kết quả sang một kiểu khác mà vẫn gọi đó là cùng công thức List.hashCode.

Giá trị hash có thể âm. Số âm không có nghĩa List hoặc hàm băm bị lỗi.

## 6. Quy tắc bắt buộc và điều không được suy ngược

Với các đối tượng tuân thủ hợp đồng equals/hashCode:

~~~text
left.equals(right) == true
    -> left.hashCode() == right.hashCode()
~~~

Chiều ngược lại không đúng:

~~~text
left.hashCode() == right.hashCode()
    -> chưa thể kết luận left.equals(right)
~~~

Hash khác nhau đủ để biết hai đối tượng không bằng nhau tại trạng thái đang xét, nếu các hợp đồng được tuân thủ. Hash bằng nhau chỉ có nghĩa cần tiếp tục dùng equals để phân biệt.

Không viết:

~~~java
// Sai nếu mục tiêu là quyết định hai List bằng nhau.
boolean same = left.hashCode() == right.hashCode();
~~~

Dùng:

~~~java
boolean same = left.equals(right);
~~~

Nếu left có thể null, có thể dùng `Objects.equals(left, right)` theo ý nghĩa mong muốn.

### Va chạm số đơn giản

~~~text
[1, 0]:
h1 = 31 + 1 = 32
h2 = 31 * 32 + 0 = 992

[0, 31]:
h1 = 31 + 0 = 31
h2 = 31 * 31 + 31 = 992
~~~

Hai List không equals nhưng có cùng hash `992`.

### Khác thứ tự vẫn có thể cùng hash

Hai String `"Aa"` và `"BB"` có cùng hashCode theo công thức của String.

Vì vậy:

~~~text
["Aa", "BB"] và ["BB", "Aa"]
~~~

không bằng nhau theo List.equals, nhưng cùng cho hash `68545`.

Đây không phải List bỏ qua thứ tự. Công thức vẫn xử lý vị trí; các giá trị đầu vào đã có va chạm nên kết quả có thể tiếp tục va chạm.

Nói “hash phụ thuộc thứ tự” cần đi cùng giới hạn “không bảo đảm ánh xạ duy nhất cho mọi dãy”.

## 7. List làm phần tử của HashSet hoặc khóa của HashMap

Hai dãy bằng nhau về giá trị có thể đóng vai trò cùng một khóa:

~~~java
Map<List<String>, String> cache = new HashMap<>();
cache.put(List.of("A", "B"), "cached");

System.out.println(
        cache.get(new LinkedList<>(List.of("A", "B")))
); // cached

System.out.println(cache.get(List.of("B", "A"))); // null
~~~

Lookup không yêu cầu cùng lớp List hoặc cùng tham chiếu với khóa ban đầu.

Trong ví dụ này map không lưu giá trị null, nên null từ lần tra thứ hai có nghĩa không có ánh xạ đó. Với một Map cho phép giá trị null nói chung, cần phân biệt “không có khóa” với “có khóa ánh xạ tới null” bằng API phù hợp khi cần.

Tương tự:

~~~java
Set<List<String>> sequences = new HashSet<>();

sequences.add(new ArrayList<>(List.of("A", "B")));
sequences.add(new LinkedList<>(List.of("A", "B")));
sequences.add(List.of("B", "A"));

System.out.println(sequences.size()); // 2
~~~

HashSet loại hai khóa dãy A, B bằng nhau thành một giá trị tập hợp, còn dãy B, A là một giá trị khác.

Không từ ví dụ suy ra thứ tự duyệt của HashSet sẽ là một trình tự cố định. Bài chỉ kiểm tra số lượng, không in hoặc dựa vào thứ tự các dãy bên trong tập hợp.

## 8. Rủi ro của khóa List có thể thay đổi

Nếu dùng một List có thể sửa làm khóa rồi thay đổi nó, equals hoặc hashCode có thể đổi trong khi cấu trúc băm vẫn đang giữ thông tin từ lúc chèn.

~~~java
// Ví dụ thiết kế có rủi ro, không dùng làm mẫu khóa ổn định.
List<String> key = new ArrayList<>(List.of("A", "B"));
Map<List<String>, String> cache = new HashMap<>();

cache.put(key, "cached");
key.add("C");

// Không dựa vào một kết quả tra cứu cụ thể sau khi sửa khóa như vậy.
~~~

Không có bảo đảm chung để ứng dụng dựa vào hành vi map sau khi một khóa bị thay đổi theo cách ảnh hưởng equals. Xem lưu ý về khóa có thể thay đổi trong [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

Không cố sửa bằng cách gọi lại get nhiều lần, hoặc cho rằng map sẽ tự chuyển khóa sang vị trí phù hợp khi nhận ra hash đã đổi. Cần thiết kế dữ liệu khóa ổn định trong thời gian nó thuộc map hoặc set.

### Chụp cấu trúc với các phần tử bất biến

Với các String không null:

~~~java
List<String> input = new ArrayList<>(List.of("A", "B"));
List<String> key = List.copyOf(input);

Map<List<String>, String> cache = new HashMap<>();
cache.put(key, "cached");

input.add("C");

System.out.println(input); // [A, B, C]
System.out.println(key);   // [A, B]
System.out.println(cache.get(List.of("A", "B"))); // cached
~~~

List.copyOf tạo kết quả không cho sửa đổi và không bị những thay đổi cấu trúc tiếp theo của input phản ánh vào nó theo cách một view dùng chung nguồn sẽ làm. Nó có thể tái sử dụng một đối tượng đầu vào phù hợp; không cần và không nên dựa vào danh tính đối tượng của kết quả để kiểm tra tính đúng.

Trong ví dụ này, String bất biến nên cả cấu trúc khóa và dữ liệu ảnh hưởng equals/hash của phần tử đều ổn định. List.copyOf từ chối phần tử null; nếu miền dữ liệu cho phép null, phải chọn chính sách chụp phù hợp thay vì truyền vào rồi giả định mọi dữ liệu đều được chấp nhận.

## 9. Không cho sửa List chưa đủ nếu phần tử vẫn thay đổi

Xét một phần tử có equals/hashCode theo một thuộc tính có thể đổi:

~~~text
List cố định cấu trúc: [label]
label.value ban đầu: "A"
label.value sau đó:   "B"
~~~

Dù không thêm, xóa hay thay phần tử trong List, hash của phần tử đã đổi. Theo công thức List, hash của cả List cũng có thể đổi.

Chương trình đầy đủ bên dưới minh họa:

~~~text
Hash của List trước: 96
Hash của List sau:   97
~~~

List đó được tạo bằng `List.of(label)`, nên không cho sửa cấu trúc. Nhưng đối tượng label bên trong vẫn có thể đổi value.

Phân biệt ba mức:

| Mức | Điều được giữ ổn định |
| --- | --- |
| Chặn add/remove/set qua một List | Chỉ là giới hạn thao tác qua đối tượng hoặc view đó. |
| Chụp cấu trúc List độc lập với một nguồn có thể đổi | Dãy tham chiếu không theo các thay đổi cấu trúc tiếp theo của nguồn. |
| Khóa giá trị thực sự ổn định | Mọi dữ liệu tham gia equals/hashCode của List và phần tử đều ổn định trong thời gian dùng làm khóa. |

Một `Collections.unmodifiableList(backing)` còn có thể phản ánh thay đổi từ backing nếu nơi khác sửa nguồn gốc. Nó không tương đương snapshot.

Ngay cả snapshot nông bằng List.copyOf cũng không tự sao chép sâu một phần tử có thể thay đổi. Với khóa nghiệp vụ, thường nên lưu các giá trị hoặc định danh bất biến đã chọn rõ, thay vì cả một đối tượng nhiều trạng thái.

## 10. equals/hashCode của phần tử là nền móng

Nếu lớp phần tử tự định nghĩa equals theo giá trị, nó cũng cần hashCode tương thích:

~~~text
hai phần tử equals nhau
    -> hash của hai phần tử phải bằng nhau
~~~

Nếu phần tử vi phạm quy tắc đó, hai List có thể bị kéo vào tình trạng bằng nhau nhưng hash khác. List không thể sửa một hợp đồng phần tử sai bằng cách tự hiểu mọi trường hoặc tự suy ra một hàm băm mới.

Các thuộc tính cần có của equals như đối xứng và bắc cầu cũng phải được các phần tử giữ. Ví dụ một lớp equals chỉ so với một lớp khác theo một chiều có thể khiến việc so sánh các container trở nên khó hiểu và sai hợp đồng.

Bài không yêu cầu mọi phần tử tự viết equals. Các kiểu giá trị có sẵn hoặc record có thể phù hợp khi đúng định nghĩa dữ liệu. Điều cần làm là chọn ý nghĩa bằng nhau của phần tử một cách rõ ràng rồi giữ hashCode tương thích.

### Không nhầm với comparator trả 0

Một comparator sắp xếp theo tuổi có thể cho hai người cùng tuổi kết quả `0`, dù equals của người xét ID và coi họ khác nhau.

List.equals không nhận comparator và không tự dùng quy tắc sắp xếp gần nhất. Hai dãy đã sort với các khóa ngang nhau vẫn được so từng phần tử bằng equals.

Vì vậy, việc sort hai danh sách rồi gọi equals chỉ giải quyết một yêu cầu bỏ qua thứ tự khi quy tắc chuẩn hóa được chọn thực sự phù hợp với định nghĩa giá trị cần so sánh. Đừng mặc nhiên cho rằng comparator bằng 0 tương đương equals.

## 11. List lồng nhau và mảng bên trong List

### List lồng nhau xét thứ tự ở mỗi tầng

~~~java
List<List<Integer>> first =
        List.of(List.of(1, 2), List.of(3));

List<List<Integer>> second =
        List.of(List.of(2, 1), List.of(3));

System.out.println(first.equals(second)); // false
~~~

Phần tử đầu của outer List là một inner List. Equals của inner List tiếp tục xét thứ tự `1, 2` so với `2, 1`.

Nếu đổi vị trí hai inner List ở outer List, thứ tự tầng ngoài cũng có ý nghĩa.

Đây là kết quả của việc các phần tử List tự có equals theo dãy, không phải List.equals tự tạo một phép duyệt sâu đặc biệt cho mọi loại đối tượng.

### Mảng dùng equals khác với List

~~~java
int[] first = {1, 2};
int[] second = {1, 2};

System.out.println(
        List.of(first).equals(List.of(second))
); // false

System.out.println(Arrays.equals(first, second)); // true
~~~

Mỗi List ở đây chứa một phần tử kiểu `int[]`. Hai mảng riêng biệt không equals theo nội dung bằng phương thức Object.equals mà mảng thừa hưởng.

Nếu nghiệp vụ muốn phần tử mảng có ngữ nghĩa giá trị, phải chọn cách biểu đạt hoặc wrapper có equals/hashCode theo nội dung phù hợp, hoặc chọn một kiểu dãy giá trị khác. Không chỉ sửa equals mà bỏ qua hashCode của wrapper. Các hàm so sánh mảng được nêu trong [Arrays API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#equals(int%5B%5D,int%5B%5D)).

Tránh tạo cấu trúc chứa chính nó hoặc chu trình tham chiếu rồi mong equals/hashCode đệ quy theo nội dung luôn có kết quả hữu hạn. Đó không phải một mô hình khóa giá trị List thông thường và có những giới hạn hợp đồng cần xem xét.

## 12. Nếu nghiệp vụ muốn bỏ qua thứ tự

Hãy chọn đúng ý nghĩa thay vì cố diễn giải lại List.equals:

| Nhu cầu | Mô hình so sánh |
| --- | --- |
| Cùng dãy và cùng số lần phần tử ở đúng vị trí | List.equals |
| Cùng tập giá trị phân biệt, bỏ qua số lần lặp | Set.equals với các phần tử tuân thủ hợp đồng |
| Cùng số lần xuất hiện của từng giá trị, bỏ qua thứ tự | So sánh bảng tần suất hoặc một biểu diễn đa tập phù hợp |

Một cách kiểm tra dễ sai:

~~~java
boolean same = left.size() == right.size()
        && left.containsAll(right)
        && right.containsAll(left);
~~~

Với:

~~~text
left  = [A, A, B]
right = [A, B, B]
~~~

size bằng nhau và hai bên đều chứa A, B, nhưng số lần xuất hiện khác. Biểu thức không chứng minh bằng nhau theo dãy, cũng không chứng minh bằng nhau theo đa tập.

Chuyển cả hai thành HashSet rồi so sánh sẽ bỏ thông tin số lần lặp. Đó có thể là điều nghiệp vụ muốn, nhưng phải chủ động chọn, không coi là một cách tối ưu giữ nguyên ý nghĩa List.equals.

Khi kiểm thử một API trả danh sách, xác định hợp đồng có cam kết thứ tự không. Nếu có, so sánh List là phù hợp. Nếu không có, kiểm thử cần thể hiện rõ ý nghĩa tập hoặc đa tập mong muốn, không tùy tiện sort dữ liệu rồi che một lỗi thứ tự mà API thật sự đã cam kết.

## 13. Chương trình hoàn chỉnh

Lưu thành **ListEqualityHashDemo.java**. Lớp MutableLabel được dùng có chủ ý để minh họa hash thay đổi khi phần tử thay trạng thái; chương trình không đưa danh sách chứa MutableLabel vào Map rồi tiếp tục sửa khóa.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ListEqualityHashDemo {
    public static void main(String[] args) {
        List<String> arrayList = new ArrayList<>(List.of("A", "B"));
        List<String> linkedList = new LinkedList<>(List.of("A", "B"));
        List<String> reversed = List.of("B", "A");

        System.out.println("Same reference = " + (arrayList == linkedList));
        System.out.println("Cross-implementation equals = "
                + arrayList.equals(linkedList));
        System.out.println("Reverse direction equals = "
                + linkedList.equals(arrayList));
        System.out.println("Reordered equals = " + arrayList.equals(reversed));
        System.out.println("AB hash = " + arrayList.hashCode());
        System.out.println("BA hash = " + reversed.hashCode());
        System.out.println("Manual AB hash = " + listHash(arrayList));
        System.out.println("Empty list hash = " + List.of().hashCode());

        List<Integer> collisionLeft = List.of(1, 0);
        List<Integer> collisionRight = List.of(0, 31);
        System.out.println("Collision equals = "
                + collisionLeft.equals(collisionRight));
        System.out.println("Collision hashes = "
                + collisionLeft.hashCode() + ", " + collisionRight.hashCode());

        List<String> swappedCollisionLeft = List.of("Aa", "BB");
        List<String> swappedCollisionRight = List.of("BB", "Aa");
        System.out.println("Reordered collision equals = "
                + swappedCollisionLeft.equals(swappedCollisionRight));
        System.out.println("Reordered collision hashes = "
                + swappedCollisionLeft.hashCode()
                + ", " + swappedCollisionRight.hashCode());

        List<String> withNull = Arrays.asList("A", null, "B");
        List<String> withNullCopy = new ArrayList<>(withNull);
        List<String> nullMoved = Arrays.asList(null, "A", "B");
        System.out.println("Null-aware equals = "
                + withNull.equals(withNullCopy));
        System.out.println("Null moved equals = " + withNull.equals(nullMoved));

        System.out.println("Duplicate count equals = "
                + List.of("A", "A", "B").equals(List.of("A", "B")));
        System.out.println("List versus set equals = "
                + arrayList.equals(Set.of("A", "B")));

        Set<List<String>> sequences = new HashSet<>();
        sequences.add(arrayList);
        sequences.add(linkedList);
        sequences.add(reversed);
        System.out.println("Distinct sequences in HashSet = "
                + sequences.size());

        List<String> mutableInput = new ArrayList<>(List.of("A", "B"));
        List<String> stableKey = List.copyOf(mutableInput);
        Map<List<String>, String> cache = new HashMap<>();
        cache.put(stableKey, "cached");
        mutableInput.add("C");
        System.out.println("Changed input = " + mutableInput);
        System.out.println("Stable key = " + stableKey);
        System.out.println("Equal sequence lookup = "
                + cache.get(new LinkedList<>(List.of("A", "B"))));
        System.out.println("Reordered lookup = "
                + cache.get(List.of("B", "A")));

        MutableLabel label = new MutableLabel("A");
        List<MutableLabel> fixedStructure = List.of(label);
        int before = fixedStructure.hashCode();
        label.rename("B");
        int after = fixedStructure.hashCode();
        System.out.println("Unmodifiable list hash before/after = "
                + before + ", " + after);

        int[] firstArray = {1, 2};
        int[] secondArray = {1, 2};
        System.out.println("Lists of separate arrays equal = "
                + List.of(firstArray).equals(List.of(secondArray)));
        System.out.println("Array contents equal = "
                + Arrays.equals(firstArray, secondArray));

        List<List<Integer>> nested = List.of(List.of(1, 2), List.of(3));
        List<List<Integer>> nestedReordered =
                List.of(List.of(2, 1), List.of(3));
        System.out.println("Nested inner order equals = "
                + nested.equals(nestedReordered));
    }

    private static int listHash(List<?> values) {
        int hash = 1;
        for (Object value : values) {
            hash = 31 * hash + Objects.hashCode(value);
        }
        return hash;
    }

    private static final class MutableLabel {
        private String value;

        private MutableLabel(String value) {
            this.value = Objects.requireNonNull(value);
        }

        private void rename(String newValue) {
            value = Objects.requireNonNull(newValue);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableLabel label
                    && value.equals(label.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 ListEqualityHashDemo.java
java ListEqualityHashDemo
~~~

Stdout:

~~~text
Same reference = false
Cross-implementation equals = true
Reverse direction equals = true
Reordered equals = false
AB hash = 3042
BA hash = 3072
Manual AB hash = 3042
Empty list hash = 1
Collision equals = false
Collision hashes = 992, 992
Reordered collision equals = false
Reordered collision hashes = 68545, 68545
Null-aware equals = true
Null moved equals = false
Duplicate count equals = false
List versus set equals = false
Distinct sequences in HashSet = 2
Changed input = [A, B, C]
Stable key = [A, B]
Equal sequence lookup = cached
Reordered lookup = null
Unmodifiable list hash before/after = 96, 97
Lists of separate arrays equal = false
Array contents equal = true
Nested inner order equals = false
~~~

## 14. Giải thích bằng chứng

**Cùng dãy, khác triển khai**

Hai List không cùng tham chiếu nhưng equals cả hai chiều. Cách lưu ArrayList hay LinkedList không thay ý nghĩa dãy A, B.

**Đảo thứ tự**

A, B không bằng B, A. Hash lần lượt là `3042` và `3072` trong ví dụ, phù hợp phép tính tích lũy.

**Manual hash và List rỗng**

Hàm listHash tự tính theo từng phần tử cho cùng hash với List. List rỗng không có bước tích lũy nào nên hash là `1`.

**Hai loại va chạm**

Các dãy số khác nhau cho cùng `992`. Hai dãy String khác thứ tự cũng cho cùng `68545` do hash các String Aa và BB trùng nhau. Trong cả hai trường hợp, equals vẫn false.

**Null và số lần xuất hiện**

Null ở đúng vị trí được so sánh nhất quán; chuyển null sang vị trí khác làm dãy khác. Dãy có thêm một A không bằng dãy thiếu lần xuất hiện đó.

**HashSet chứa các dãy**

A, B từ hai triển khai là cùng một giá trị List, còn B, A là giá trị khác. Vì vậy có hai phần tử List trong HashSet.

**Khóa snapshot của String**

Input được thêm C nhưng stableKey vẫn A, B. Một LinkedList khác có cùng dãy tìm được giá trị đã lưu. Dãy đảo không tìm được khóa trong map của chương trình.

**Cấu trúc không cho sửa nhưng hash đổi**

MutableLabel chuyển A thành B, làm hash phần tử đổi từ hash String A sang hash String B. List một phần tử tương ứng đổi `96` thành `97`, dù không có add/remove/set trên List.

**Mảng và List lồng nhau**

Hai mảng khác tham chiếu không tự được List so sánh theo nội dung. Hai inner List lại được so bằng hợp đồng List của chính chúng, nên thứ tự bên trong vẫn có ý nghĩa.

## 15. Kiểm thử và chi phí

Các ca kiểm thử nên có:

1. Hai List cùng nội dung nhưng khác đối tượng và khác triển khai.
2. Hai List khác thứ tự.
3. Phần tử trùng và số lần trùng khác nhau.
4. Null cùng vị trí và null khác vị trí khi nguồn cho phép.
5. List rỗng.
6. Hai List bằng nhau phải có cùng hash.
7. Hai List khác nhau có thể có cùng hash; không viết yêu cầu mọi hash phải khác.
8. Phần tử có equals/hashCode theo giá trị.
9. Phần tử mảng hoặc đối tượng có thể thay đổi.
10. Khóa được chụp để không bị sửa cấu trúc từ nguồn ban đầu.

Đừng kiểm thử hash bằng yêu cầu “đổi thứ tự thì mọi trường hợp hash phải đổi”. Hãy kiểm tra đúng công thức và quan hệ bắt buộc từ equals sang hash.

Về chi phí, so sánh dãy có thể kết thúc sớm khi thấy khác size hoặc một cặp phần tử khác nhau; trường hợp bằng nhau cần kiểm tra đủ dữ liệu liên quan. Chi phí thực tế còn phụ thuộc equals của phần tử và triển khai List.

Tính hash theo công thức thường phải tích lũy qua các phần tử, cùng chi phí hash của từng phần tử. Đừng giả định hashCode là một định danh sẵn có có chi phí hằng số cho mọi List, hoặc cache hash của một cấu trúc có thể thay đổi mà không có cơ chế vô hiệu hóa đúng.

Việc đọc equals/hashCode trên một nguồn đang bị cập nhật đồng thời cũng không tự tạo snapshot hoặc một bảo đảm nhất quán cho cả dãy. Chọn dữ liệu ổn định hoặc giao thức truy cập phù hợp với yêu cầu.

## 16. Lỗi thường gặp

| Hiểu nhầm | Cách hiểu cần giữ |
| --- | --- |
| Cùng phần tử phân biệt là List.equals | List còn xét thứ tự và số lần xuất hiện. |
| Phải cùng lớp ArrayList mới bằng nhau | Các triển khai List tuân thủ so theo cùng hợp đồng dãy. |
| Cùng toString là equals | Phần tử khác kiểu hoặc khác equals có thể hiển thị giống nhau. |
| List.equals so phần tử bằng == | Nó dùng equals theo từng vị trí, có xử lý null. |
| Hash bằng nhau là hai List bằng nhau | Có thể có va chạm; cần equals. |
| Hash phụ thuộc thứ tự nên mọi hoán vị có hash khác | Va chạm phần tử hoặc công thức có thể cho cùng hash. |
| List không cho sửa thì hash chắc chắn bất biến | Phần tử hoặc nguồn phía sau một view vẫn có thể thay đổi. |
| List.copyOf luôn sao chép sâu mọi dữ liệu | Nó không sao chép sâu đối tượng phần tử. |
| Thay khóa trong map thì map tự cập nhật vị trí băm | Phải giữ dữ liệu tham gia equals/hash ổn định khi là khóa. |
| List chứa mảng tự được so sâu theo nội dung mảng | List dùng equals/hash của phần tử; mảng có quy tắc khác. |
| containsAll hai chiều kèm size thay cho List.equals | Không giữ được thứ tự và có thể sai cả số lần lặp. |

## 17. Thực hành

1. Tính tay hash của `List.of(1, 2)` và `List.of(2, 1)`, rồi kiểm tra bằng chương trình.
2. Tạo một ArrayList và một LinkedList có cùng null ở cùng vị trí; kiểm tra equals hai chiều và hash.
3. Dùng các String Aa, BB để tạo hai dãy đảo nhau có hash bằng nhau nhưng không equals.
4. Tạo hai danh sách cùng size và containsAll hai chiều nhưng khác số lần xuất hiện. Giải thích vì sao phép kiểm tra đó không đúng cho đa tập.
5. Thiết kế một khóa ghép dạng List của các giá trị bất biến. Kiểm tra tra cứu bằng một List khác triển khai có cùng dãy.
6. Thay phần tử có thể đổi trạng thái bên trong một List không cho sửa. Quan sát hash trước–sau mà không đưa khóa không ổn định vào một map thực.
7. Chọn mô hình so sánh cho ba yêu cầu: tuyến đường có thứ tự điểm đi qua, tập quyền không trùng, và giỏ hàng cần đếm số lần xuất hiện của từng mã.

Trước khi so sánh hoặc dùng List làm khóa, hãy xác định giá trị đang cần biểu diễn là một dãy, một tập hay một đa tập. Với dãy, equals xét từng vị trí; hashCode tích lũy theo cùng thứ tự và chỉ hỗ trợ tra cứu, không thay thế định nghĩa bằng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-009">Chọn List khi cần truy cập chỉ số hoặc giữ phần tử trùng</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn List khi cần truy cập chỉ số hoặc giữ phần tử trùng

Chọn collection bắt đầu từ **ý nghĩa của nhóm dữ liệu**, không phải tên lớp quen thuộc.

Nếu dữ liệu là một chuỗi có vị trí cần truy cập, hoặc mỗi lần xuất hiện của cùng một giá trị đều phải được giữ lại, `List<E>` là một lựa chọn cần xét. Tuy nhiên, hai nhu cầu đó còn phải được đặt cạnh yêu cầu về thứ tự, tra cứu, sửa đổi và cách xử lý phần tử.

Bài dùng Java 17. Ba chương trình hoàn chỉnh đã được biên dịch với `--release 17` và chạy để đối chiếu stdout. Các đoạn mã rút gọn khác minh họa thao tác trong một phương thức, không phải tệp chạy độc lập.

Sau bài học, bạn có thể:

- Nhận diện yêu cầu nào thực sự cần vị trí của danh sách.
- Phân biệt một giá trị duy nhất với nhiều lần xuất hiện của giá trị đó.
- Giải thích khi nào Set, Map hoặc hàng đợi mô tả bài toán tốt hơn.
- Chọn lớp triển khai List theo thao tác chủ yếu.
- Tránh dùng chỉ số làm định danh ổn định của dữ liệu.

## 1. List biểu diễn một chuỗi phần tử có vị trí

Xét:

```text
Giá trị:   P1   P2   P1   P3
Chỉ số:     0    1    2    3
```

Ta có thể hỏi:

- Phần tử ở vị trí thứ ba là gì?
- Cần chèn một phần tử trước vị trí nào?
- Có những lần xuất hiện nào của P1 trong chuỗi?
- Nếu đổi chỗ hai phần tử, chuỗi kết quả có còn cùng ý nghĩa không?

List cung cấp hợp đồng truy cập và điều khiển vị trí theo chỉ số. Những lớp thông dụng như ArrayList cho phép các phần tử bằng nhau xuất hiện nhiều lần. Đây là mô hình chuỗi được mô tả trong [List Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

Cần phân biệt ba tính chất:

1. **Có thứ tự:** phần tử có vị trí tương đối trong chuỗi.
2. **Có chỉ số:** có thể gọi các thao tác vị trí như get hoặc set.
3. **Có sắp xếp:** dữ liệu đang theo một quy tắc so sánh như tăng dần.

List có mô hình thứ tự vị trí, nhưng không tự sắp xếp giá trị. Danh sách `[P1, P2, P1, P3]` có thứ tự rõ, dù không được nhóm các mã bằng nhau lại với nhau.

### Thứ tự không chỉ là thứ tự thêm ở cuối

Khi gọi add ở cuối, vị trí thường theo thứ tự thêm. Nhưng chương trình còn có thể:

- Chèn ở giữa.
- Xóa một vị trí.
- Hoán đổi phần tử.
- Sắp xếp lại.
- Thay dữ liệu ở một vị trí.

Vì vậy, nên nói List giữ **chuỗi vị trí hiện tại**, thay vì hiểu rằng nó vĩnh viễn giữ nguyên lịch sử chèn ban đầu.

## 2. Khi nào truy cập chỉ số là yêu cầu thật sự?

Những tình huống thường phù hợp:

| Tình huống | Vì sao vị trí có ý nghĩa? |
| --- | --- |
| Danh sách phát nhạc | Một bài có vị trí phát cụ thể; cùng bài có thể phát lại |
| Các bước trong một quy trình | Bước trước/sau và thao tác đổi vị trí có ý nghĩa |
| Các hàng kết quả theo thứ tự được xác định | Bên sử dụng cần truy cập hoặc trình bày theo vị trí |
| Các dòng trong một tài liệu | Chèn, thay và lấy dòng theo vị trí là thao tác thực |
| Một chuỗi quan sát hoặc lượt quét | Trình tự xuất hiện cần được giữ |

Một phép truy cập minh họa:

```java
String third = scans.get(2);
```

Chỉ số bắt đầu từ 0. Phần tử thứ ba theo cách đếm thông thường nằm ở chỉ số 2.

### Không phải mọi câu hỏi có số đều là chỉ số

“Tìm bản ghi có mã 1002” khác với “lấy phần tử ở vị trí 1002”.

- Mã bản ghi là thuộc tính nhận diện.
- Chỉ số là vị trí hiện tại trong một danh sách.
- Chỉ số có thể đổi khi thêm/xóa hoặc sắp xếp.
- Mã hợp lệ không nhất thiết liên tục hoặc bắt đầu từ 0.

Nếu yêu cầu chính là tra bằng mã, Map thường là mô hình cần xét. List vẫn có thể dùng để trình bày thứ tự, nhưng không nên coi chỉ số là mã nghiệp vụ.

### Nếu chỉ duyệt tuần tự, có thể chưa cần List

Một phương thức chỉ in hoặc xử lý lần lượt các phần tử có thể chỉ cần Iterable hoặc Collection phù hợp. Yêu cầu riêng List chỉ để viết vòng get(index) có thể làm API phụ thuộc vào khả năng không cần thiết.

Chọn List khi thứ tự chuỗi hoặc khả năng vị trí là một phần hợp đồng cần cung cấp, không chỉ vì đó là cấu trúc đầu tiên được học.

## 3. “Giữ trùng” nghĩa là giữ các lần xuất hiện

Giả sử máy quét ghi nhận:

```text
P1, P2, P1, P3
```

Có bốn lượt quét, dù chỉ có ba mã khác nhau.

Hai yêu cầu sau khác nhau:

- **Lưu lịch sử lượt quét:** P1 xuất hiện hai lần, và hai vị trí đều có ý nghĩa.
- **Lưu danh mục mã đã từng thấy:** mỗi mã chỉ cần xuất hiện một lần.

List phù hợp với mô hình thứ nhất. Một Set có thể phù hợp với mô hình thứ hai, nếu quan hệ duy nhất được xác định đúng.

### Trùng theo giá trị không bắt buộc cùng tham chiếu

Một danh sách có thể chứa:

- Hai đối tượng khác nhau nhưng equals nhau.
- Cùng một tham chiếu được thêm vào nhiều vị trí.

Kích thước danh sách đếm các phần tử ở các vị trí, không đếm số đối tượng Java khác nhau đang tồn tại trong bộ nhớ.

Giữ trùng cũng không có nghĩa tự sao chép đối tượng. Nếu thêm cùng một đối tượng khả biến hai lần, hai vị trí có thể cùng tham chiếu tới đối tượng đó.

### Cùng thuộc tính chưa chắc là phần tử bằng nhau

Hai bản ghi có cùng mã sản phẩm nhưng khác mã lần quét, thời gian hoặc dữ liệu khác có thể không equals nhau.

Do đó, trước khi chọn “không trùng”, hãy trả lời:

- Duy nhất theo toàn bộ giá trị đối tượng?
- Duy nhất theo một mã?
- Hay mỗi lần xuất hiện phải được giữ riêng dù có cùng mã?

Tên collection không thể tự quyết định quan hệ nghiệp vụ đó thay bạn.

## 4. Chương trình giữ lượt xuất hiện và truy cập vị trí

Tạo tệp **`ChoosingListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChoosingListDemo {
    public static void main(String[] args) {
        String repeated = "P1";
        List<String> scans =
                new ArrayList<>(List.of(repeated, "P2", repeated, "P3"));

        System.out.println("scans = " + scans);
        System.out.println("occurrence count = " + scans.size());
        System.out.println("at index 2 = " + scans.get(2));
        System.out.println("same reference at 0 and 2 = "
                + (scans.get(0) == scans.get(2)));

        Set<String> uniqueCodes = new LinkedHashSet<>(scans);
        System.out.println("unique codes = " + uniqueCodes);
        System.out.println("unique count = " + uniqueCodes.size());

        boolean removed = scans.remove("P1");
        System.out.println("one P1 removed = " + removed);
        System.out.println("remaining scans = " + scans);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ChoosingListDemo.java
java -Dfile.encoding=UTF-8 ChoosingListDemo
```

Stdout đã kiểm chứng:

```text
scans = [P1, P2, P1, P3]
occurrence count = 4
at index 2 = P1
same reference at 0 and 2 = true
unique codes = [P1, P2, P3]
unique count = 3
one P1 removed = true
remaining scans = [P2, P1, P3]
```

### Đọc kết quả

- Danh sách có 4 phần tử vì giữ cả hai lần P1.
- get(2) trả P1 ở lần xuất hiện thứ hai.
- Hai vị trí 0 và 2 giữ cùng tham chiếu trong ví dụ vì chương trình chủ động dùng lại biến repeated.
- LinkedHashSet giữ ba mã duy nhất và trình bày chúng theo thứ tự gặp đầu tiên.
- remove("P1") trên danh sách xóa một lần xuất hiện đầu tiên, nên P1 còn lại vẫn được giữ.

Nếu bài toán phải biết mọi lượt quét, việc chuyển thẳng sang Set đã làm mất một phần thông tin.

### Có thứ tự vẫn chưa chắc thay được List

LinkedHashSet trong ví dụ có thứ tự duyệt rõ, nhưng không cung cấp thao tác get(index) của List và không giữ hai lần P1.

Điều này cho thấy “có thứ tự” không phải tiêu chí duy nhất để hai cấu trúc thay thế nhau.

### Chuyển ngược không phục hồi thông tin đã mất

Tạo một ArrayList mới từ uniqueCodes có thể cho một chuỗi ba mã, nhưng không đủ dữ liệu để biết P1 từng có hai lượt hoặc đã nằm ở vị trí nào trong chuỗi gốc.

Sao chép sang List chỉ thay cấu trúc biểu diễn của dữ liệu đang có; nó không khôi phục những lần xuất hiện đã bị loại.

## 5. Chỉ giữ số lần hay phải giữ cả chuỗi?

Nếu chỉ cần biết:

```text
A xuất hiện 2 lần
B xuất hiện 1 lần
```

một bảng đếm theo giá trị có thể mô tả đúng nhu cầu mà không lưu từng lần thành một vị trí riêng.

Nhưng hai chuỗi:

```text
[A, B, A]
[A, A, B]
```

có cùng bảng đếm mà khác thứ tự.

### Chương trình hoàn chỉnh

Tạo tệp **`SequenceVsCountsDemo.java`**:

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SequenceVsCountsDemo {
    public static void main(String[] args) {
        List<String> first = List.of("A", "B", "A");
        List<String> second = List.of("A", "A", "B");

        Map<String, Integer> firstCounts = count(first);
        Map<String, Integer> secondCounts = count(second);

        System.out.println("first = " + first);
        System.out.println("second = " + second);
        System.out.println("same list values = " + first.equals(second));
        System.out.println("first counts = " + firstCounts);
        System.out.println("second counts = " + secondCounts);
        System.out.println("same counts = " + firstCounts.equals(secondCounts));

        Map<String, List<Integer>> positions = new LinkedHashMap<>();
        for (int index = 0; index < first.size(); index++) {
            positions.computeIfAbsent(
                    first.get(index), key -> new java.util.ArrayList<>()).add(index);
        }
        System.out.println("first positions by value = " + positions);
    }

    private static Map<String, Integer> count(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 SequenceVsCountsDemo.java
java -Dfile.encoding=UTF-8 SequenceVsCountsDemo
```

Stdout đã kiểm chứng:

```text
first = [A, B, A]
second = [A, A, B]
same list values = false
first counts = {A=2, B=1}
second counts = {A=2, B=1}
same counts = true
first positions by value = {A=[0, 2], B=[1]}
```

### Những mô hình được so sánh

**List:**

- Giữ các phần tử ở đúng vị trí.
- Hai chuỗi khác thứ tự không bằng nhau theo List.equals trong ví dụ.

**Map<String, Integer> đếm số lần:**

- Giữ mỗi giá trị cùng số lần xuất hiện.
- Hai bảng đếm bằng nhau, dù các chuỗi ban đầu khác nhau.
- Số đếm không đủ để tái dựng vị trí từng lần.

**Map<String, List<Integer>> giữ vị trí:**

- Nhóm theo giá trị.
- Mỗi nhóm có danh sách chỉ số.
- Ví dụ A có các vị trí 0 và 2, B có vị trí 1.

Cấu trúc ghép cho thấy List và Map có thể cùng tham gia một mô hình. Việc chọn không phải lúc nào cũng là loại bỏ toàn bộ một bên.

Trong ví dụ nhỏ, số lần và chỉ số đều nằm trong phạm vi int. Với dữ liệu quy mô khác, phải chọn kiểu và chính sách số học phù hợp cho bảng đếm.

### Giữ trùng chưa đủ để kết luận phải dùng List

Nếu chỉ xử lý công việc theo đầu hàng đợi, một Queue hoặc Deque phù hợp vẫn có thể giữ nhiều công việc bằng nhau mà không cần hợp đồng truy cập vị trí.

Câu hỏi đúng là: **có cần một chuỗi vị trí để truy cập/sửa, hay chỉ cần quy tắc lấy phần tử tiếp theo?**

## 6. Đối chiếu với các cấu trúc khác

| Yêu cầu chủ yếu | Mô hình cần xét | Điểm cần kiểm tra |
| --- | --- | --- |
| Chuỗi phần tử có vị trí và giữ từng lần xuất hiện | List | Chi phí truy cập và khả năng sửa |
| Tập giá trị duy nhất | Set | Quan hệ duy nhất và chính sách thứ tự |
| Tra giá trị theo khóa | Map | Khóa có duy nhất không; cập nhật trùng khóa có ý nghĩa gì |
| Nhận/xử lý phần tử theo chính sách hàng đợi | Queue hoặc Deque | Thao tác ở đầu/cuối và thứ tự xử lý |
| Chỉ cần số lần của mỗi giá trị | Map giá trị → số lần | Có chấp nhận mất thông tin thứ tự không |
| Mỗi khóa có nhiều giá trị | Map khóa → List giá trị | Thứ tự trong nhóm và thứ tự toàn cục |
| Số vị trí cố định, đặc biệt với dữ liệu nguyên thủy | Mảng | Kích thước, kiểu dữ liệu và thao tác cần có |

### Map có thể chứa các giá trị trùng

Map yêu cầu cách xử lý khóa theo hợp đồng của nó; không có nghĩa các giá trị của mọi khóa đều phải khác nhau.

Nếu dùng mã bài hát làm khóa rồi put cùng mã nhiều lần, một Map đơn giá trị không tự lưu nhiều lượt phát dưới cùng khóa. Muốn nhiều lần dưới một khóa, phải chọn giá trị dạng nhóm hoặc một mô hình khác.

### Nhóm theo khóa có thể làm mất cách đan xen toàn cục

Map<LoạiSựKiện, List<SựKiện>> có thể giữ thứ tự trong mỗi nhóm, nhưng chỉ những danh sách nhóm đó chưa chắc đủ để biết các loại đã đan xen ra sao trong chuỗi toàn cục.

Nếu thứ tự toàn cục quan trọng, cần giữ chuỗi gốc hoặc lưu thông tin vị trí/thời điểm đủ rõ. Ví dụ chương trình đã giữ chỉ số cho từng nhóm là một cách làm thông tin vị trí trở nên tường minh.

### Set không phải cách “làm sạch” luôn đúng

Nếu mỗi lần xuất hiện là một sự kiện thực, loại phần tử bằng nhau có thể xóa thông tin hợp lệ.

Ngược lại, nếu lặp lại là lỗi dữ liệu, việc List giữ được nó không có nghĩa hệ thống nên chấp nhận. Ràng buộc nghiệp vụ cần được kiểm tra rõ thay vì trông chờ cấu trúc tự quyết định.

## 7. Chỉ số không phải định danh ổn định

Giả sử một danh sách quy trình có bước Review ở chỉ số 1. Khi chèn một bước Prepare ở đầu, Review chuyển sang chỉ số 2.

Nếu chương trình đã lưu số 1 để đại diện Review, số đó bây giờ trỏ tới một bước khác.

### Chương trình hoàn chỉnh

Tạo tệp **`PositionIsNotIdDemo.java`**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PositionIsNotIdDemo {
    public record Step(String id, String text) {
    }

    public static void main(String[] args) {
        Step review = new Step("S20", "Review");
        List<Step> steps = new ArrayList<>(List.of(
                new Step("S10", "Start"),
                review,
                new Step("S30", "Finish")));

        int savedIndex = 1;
        String savedId = steps.get(savedIndex).id();

        steps.add(0, new Step("S05", "Prepare"));

        Map<String, Step> byId = new LinkedHashMap<>();
        for (Step step : steps) {
            byId.put(step.id(), step);
        }

        System.out.println("current ids = "
                + steps.stream().map(Step::id).toList());
        System.out.println("saved index now points to = "
                + steps.get(savedIndex).id());
        System.out.println("review current index = " + steps.indexOf(review));
        System.out.println("saved id = " + savedId);
        System.out.println("lookup by saved id = " + byId.get(savedId).text());
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 PositionIsNotIdDemo.java
java -Dfile.encoding=UTF-8 PositionIsNotIdDemo
```

Stdout đã kiểm chứng:

```text
current ids = [S05, S10, S20, S30]
saved index now points to = S10
review current index = 2
saved id = S20
lookup by saved id = Review
```

### Cách đọc

- savedIndex vẫn là 1, nhưng danh sách đã thay cấu trúc nên vị trí đó hiện chứa S10.
- Review vẫn là bước có id S20.
- indexOf(review) trong trạng thái mới trả 2.
- Map byId được xây từ danh sách hiện tại và tra bằng id đã lưu, nên nhận đúng Review.

Ví dụ giả định id của mỗi Step là duy nhất. Nếu đầu vào cho phép trùng id, cần quy định cách xử lý thay vì để put vô tình thay giá trị cũ.

Map trong chương trình là một chỉ mục được tạo **sau lần chèn**. Nó không tự đồng bộ theo mọi thay đổi tương lai của List. Nếu hệ thống giữ đồng thời cả List và Map, phải thiết kế cách cập nhật để chúng nhất quán.

### Khi vị trí vẫn là dữ liệu đúng

Chỉ số rất phù hợp khi thao tác thật sự là:

- “Đổi phần tử ở hàng thứ ba trong trạng thái hiện tại”.
- “Chèn trước bước đang nằm ở vị trí này”.
- “Lấy phần tử theo vị trí do bộ điều khiển danh sách quản lý”.

Vấn đề là dùng một vị trí thay cho danh tính qua nhiều thay đổi mà không có quy tắc giữ trạng thái.

## 8. Chọn lớp triển khai khi đã chọn List

Hợp đồng chỉ số không đồng nghĩa mọi List truy cập chỉ số nhanh như nhau.

### ArrayList khi truy cập vị trí là thao tác nổi bật

ArrayList dùng mô hình mảng có khả năng tăng dung lượng. get và set có thời gian không tăng theo số phần tử trong mô hình chi phí thông thường; thêm cuối có chi phí hằng số khấu hao. Chèn/xóa giữa thường cần dịch dữ liệu. Xem [ArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

Vì vậy, với một danh sách cần đọc theo chỉ số, duyệt và thêm cuối, ArrayList thường là điểm bắt đầu hợp lý để đánh giá.

Đó không phải lời hứa rằng nó tốt nhất cho mọi tải hoặc mọi kích thước. Còn phải xét dung lượng, tần suất sửa, phần tử và điều kiện thực thi.

### LinkedList có chỉ số nhưng cần tìm tới vị trí

LinkedList tổ chức các nút liên kết hai chiều; thao tác theo chỉ số phải đi từ đầu hoặc cuối tới vị trí cần thiết. Tài liệu [LinkedList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html) mô tả rõ cách tìm từ đầu gần hơn.

Hệ quả:

- Có phương thức get(index) không có nghĩa truy cập ngẫu nhiên hằng số.
- Nếu đã duyệt tới vị trí bằng ListIterator, chèn/xóa tại đó có thể thao tác trên liên kết đang biết.
- Nếu mỗi lần chèn đều phải tìm theo chỉ số từ đầu hoặc cuối, phải tính cả chi phí tìm vị trí.

Không nên chọn LinkedList chỉ vì thấy yêu cầu “có nhiều lần thêm/xóa”. Cần biết thêm/xóa ở đâu, đã có con trỏ chưa và các thao tác đọc khác diễn ra như thế nào.

### Bảng định hướng

| Mẫu sử dụng | Điều cần ưu tiên |
| --- | --- |
| Nhiều get/set theo chỉ số | Khả năng truy cập vị trí của lớp |
| Chủ yếu duyệt tuần tự | Iterator hoặc enhanced for; tránh tìm lại từng chỉ số không cần thiết |
| Chèn/xóa giữa qua con trỏ đã có | Chi phí chỉnh sửa ở vị trí đó |
| Chủ yếu thao tác hai đầu | Xem cả hợp đồng Deque thay vì chỉ List |
| Đọc nhiều và yêu cầu đồng thời đặc biệt | Chọn chính sách đồng thời riêng, không suy từ List |

Nếu hiệu năng là yêu cầu thực tế, hãy đo trên mẫu dữ liệu và chuỗi thao tác đại diện. Độ phức tạp giúp loại những giả định sai, nhưng không thay thế tất cả điều kiện triển khai.

## 9. Khả năng sửa đổi là một quyết định riêng

Một List có thể đúng về mặt thứ tự và phần tử trùng nhưng không hỗ trợ thao tác bạn sắp làm.

Ví dụ:

```java
List<String> fixedData = List.of("A", "B", "A");
List<String> editable = new ArrayList<>(fixedData);
```

Cả hai giữ ba lần xuất hiện theo thứ tự đó. Khác biệt là:

- fixedData không cho thêm, xóa hoặc thay thế phần tử.
- editable là bản sao cấu trúc có thể sửa qua các thao tác ArrayList hỗ trợ.

Khả năng giữ trùng không phụ thuộc việc danh sách có sửa được hay không.

### Null không phải tiêu chí suy ra từ tên List

Một số List cho phép null, một số nguồn tạo List từ chối nó. Hãy quyết định rõ dữ liệu thiếu được biểu diễn thế nào và kiểm tra hợp đồng của nguồn.

Không dùng “List cho phép trùng” làm suy luận rằng nó cũng cho phép mọi giá trị hoặc mọi thao tác.

### Phần tử khả biến cần chính sách riêng

Danh sách không sửa đổi cấu trúc không tự làm phần tử trở thành bất biến. Ngược lại, một danh sách có thể sửa không bắt buộc phần tử cũng là đối tượng khả biến.

Khi cùng tham chiếu xuất hiện nhiều lần, việc sửa đối tượng đó có thể được nhìn thấy qua nhiều vị trí. Nếu muốn mỗi lần xuất hiện có trạng thái độc lập, hãy tạo các bản ghi/lần xuất hiện riêng hoặc sao chép theo chính sách phù hợp.

## 10. Thiết kế API từ hợp đồng cần có

Trước khi công bố một List cho phần khác sử dụng, nên nói rõ:

- Thứ tự mang ý nghĩa gì.
- Phần tử bằng nhau có được xuất hiện nhiều lần không.
- Bên nhận có quyền thêm/xóa/sửa không.
- Kết quả là bản sao, góc nhìn dùng chung hay đối tượng sống.
- Chỉ số hợp lệ trong phạm vi trạng thái nào.
- Có được nhiều bên cùng sử dụng và sửa không.

### Chỉ yêu cầu khả năng thật sự cần dùng

Nếu một phương thức cần lấy vị trí cụ thể, tham số List có ý nghĩa:

```java
static String valueAt(List<String> values, int index) {
    return values.get(index);
}
```

Đây là một khai báo phương thức minh họa; nó dựa vào List để kiểm tra chỉ số và không tự định nghĩa thêm chính sách dữ liệu thiếu.

Nếu phương thức chỉ đọc lần lượt, Iterable có thể là hợp đồng đủ hẹp hơn. Nếu phương thức cần “mỗi giá trị chỉ xuất hiện một lần”, cần xem Set hoặc kiểm tra riêng thay vì để tham số List tạo kỳ vọng sai.

### Trả List là công bố ý nghĩa thứ tự

Nếu API trả List, bên sử dụng có thể dựa vào chuỗi vị trí. Hãy xác định thứ tự đó có ổn định giữa các lần gọi không và đến từ nguồn nào.

Một danh sách được tạo từ một nguồn không bảo đảm thứ tự không tự có thứ tự nghiệp vụ chỉ vì kết quả mang kiểu List. Nó có một thứ tự cụ thể trong lần tạo, nhưng ý nghĩa và tính lặp lại cần được thiết kế.

### Dữ liệu dùng chung cần quyền sở hữu rõ

Cho nhiều bên sửa cùng một List làm các chỉ số và thứ tự có thể thay đổi ngoài phạm vi dự kiến.

Tùy bài toán, có thể:

- Giữ một bên sở hữu việc sửa.
- Trả bản sao cấu trúc.
- Trả dữ liệu không cho sửa đổi.
- Dùng cơ chế phối hợp hoặc cấu trúc đồng thời phù hợp.

Đừng mặc định ArrayList thông thường an toàn khi nhiều luồng cùng sửa. Cũng không coi mỗi phương thức được bảo vệ riêng là toàn bộ một chuỗi thao tác theo chỉ số tự trở thành nguyên tử.

## 11. Quy trình quyết định ngắn

### Bước 1: xác định một phần tử là gì

Một mã sản phẩm, một lượt quét hay một dòng sự kiện là ba đơn vị khác nhau.

Nếu “một phần tử” chưa được định nghĩa, quy tắc trùng lặp và đếm rất dễ bị sai.

### Bước 2: xác định thông tin cần giữ

Hỏi:

- Có cần thứ tự toàn chuỗi không?
- Có cần giữ từng lần xuất hiện không?
- Có cần truy cập/sửa theo chỉ số không?
- Hay chỉ cần thành viên duy nhất, số lượng hoặc tra bằng khóa?

### Bước 3: chọn hợp đồng

Chọn List, Set, Map, Queue/Deque hoặc cấu trúc ghép theo những câu trả lời trên.

Không ép mọi nhu cầu vào List chỉ vì nó chứa được dữ liệu.

### Bước 4: chọn đối tượng cụ thể

Kiểm tra:

- Chi phí các thao tác chính.
- Khả năng sửa đổi.
- Chính sách null.
- Quan hệ chia sẻ với nguồn.
- Yêu cầu sử dụng đồng thời.

### Bước 5: kiểm chứng bằng dữ liệu làm lộ khác biệt

Một tập thử hữu ích cần có:

- Phần tử ở đầu.
- Phần tử trùng.
- Chuỗi cùng số đếm nhưng khác thứ tự.
- Chèn/xóa trước một vị trí đang được dùng.
- Truy vấn theo mã khác với truy vấn theo vị trí.

Các chương trình trong bài dùng đúng những tình huống này để làm rõ ý nghĩa, thay vì chỉ kiểm tra một danh sách không trùng và không bị sửa.

## 12. Những sai lầm thường gặp

### Chọn List chỉ vì “có nhiều đối tượng”

Mọi collection đều có thể quản lý nhóm dữ liệu theo những mô hình khác nhau. Cần xác định vị trí, trùng lặp, khóa và cách xử lý.

### Chọn Set để bỏ phần trùng mà chưa biết phần trùng có ý nghĩa gì

Hai lượt quét giống mã vẫn có thể là hai sự kiện thật. Loại chúng có thể làm mất dữ liệu hợp lệ.

### Chọn List vì “giữ trùng”, dù chỉ cần số lượng

Nếu không cần từng vị trí hay từng bản ghi, bảng đếm có thể diễn đạt nhu cầu rõ hơn. Ngược lại, số đếm không tái dựng được chuỗi.

### Cho rằng giữ thứ tự nghĩa là luôn sắp tăng dần

List giữ trật tự vị trí mà chương trình đang có. Sort là một thao tác hoặc chính sách riêng.

### Cho rằng mọi List get nhanh như nhau

LinkedList có get(index) nhưng phải tìm vị trí. Xem lớp triển khai và thuật toán duyệt.

### Dùng chỉ số làm khóa lâu dài

Chèn, xóa và sort đều có thể làm vị trí thay đổi. Dùng định danh riêng khi cần nhận diện qua nhiều trạng thái.

### Nghĩ thêm hai lần là tạo hai bản sao đối tượng

List giữ hai tham chiếu ở hai vị trí; đó có thể là cùng một đối tượng. Nếu cần trạng thái mỗi lần riêng, phải mô hình hóa rõ.

### Dùng song song List và Map mà không có cách cập nhật

Hai cấu trúc không tự giữ nhất quán. Chương trình cần một nơi chịu trách nhiệm thêm, xóa và thay dữ liệu tương ứng.

### Suy rằng List phải sửa được

Một List không sửa đổi vẫn có vị trí và có thể giữ trùng. Chọn nguồn tạo phù hợp thao tác cần làm.

## 13. Thực hành

### Bài 1: giữ lượt quét hay giữ mã?

Một thiết bị ghi nhận:

```text
X, Y, X, X, Z
```

Thiết kế riêng cho ba yêu cầu:

1. Hiển thị đầy đủ lịch sử theo thứ tự.
2. Hiển thị các mã đã từng xuất hiện, mỗi mã một lần.
3. Hiển thị số lần của mỗi mã.

Nêu cấu trúc và thông tin nào được giữ hoặc không còn giữ ở mỗi lựa chọn.

### Bài 2: hai chuỗi cùng số đếm

Tạo hai chuỗi có cùng bảng đếm nhưng khác thứ tự. Chứng minh:

- List.equals không xem chúng là cùng chuỗi.
- Bảng đếm có thể bằng nhau.
- Không thể chỉ từ số lần mà suy ra thứ tự gốc.

### Bài 3: mô hình lượt phát

Một danh sách phát cho phép cùng một bài xuất hiện nhiều lần.

Hãy xác định:

- Một phần tử là bài hát hay một lượt phát.
- Khi cần trạng thái riêng cho mỗi lượt, những trường nào cần có.
- Mã bài hát và vị trí phát có khác nhau thế nào.
- Chuyển sang Set theo mã bài sẽ làm mất điều gì.

### Bài 4: chọn lớp theo thao tác

So sánh các tình huống:

- Nhiều lần đọc phần tử tại vị trí bất kỳ.
- Duyệt từ đầu đến cuối mà không cần chỉ số.
- Đã có ListIterator ở giữa và cần chèn/xóa quanh đó.
- Chủ yếu thêm/lấy ở hai đầu.

Giải thích bằng cách tìm vị trí và cập nhật cấu trúc, không chỉ câu “lớp này nhanh hơn”.

### Bài 5: chỉ số và định danh

Mở rộng PositionIsNotIdDemo:

1. Lưu một id và một chỉ số ban đầu.
2. Chèn hoặc xóa trước vị trí đó.
3. Sắp xếp lại danh sách theo text.
4. Đối chiếu phần tử tại chỉ số cũ với bước có id đã lưu.
5. Nếu giữ cả Map và List, mô tả cách làm chúng nhất quán sau mỗi thao tác.

Tiêu chí hoàn thành là chọn cấu trúc theo đúng đơn vị dữ liệu, giữ đủ thông tin về vị trí hoặc lần xuất hiện, và tách rõ thứ tự hiện tại khỏi định danh ổn định.


</details>

- [Quay lại đầu trang](#back-to-top)
