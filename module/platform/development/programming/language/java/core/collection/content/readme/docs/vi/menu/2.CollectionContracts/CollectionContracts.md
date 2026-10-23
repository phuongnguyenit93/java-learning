<a id="back-to-top"></a>

# Hợp đồng chung

## Menu
- [Khai báo theo giao diện và lựa chọn lớp cài đặt](#collection-contracts-001)
- [size, isEmpty, contains và ý nghĩa giá trị trả về](#collection-contracts-002)
- [add, remove, clear và quy tắc thao tác tùy chọn](#collection-contracts-003)
- [addAll, containsAll, removeAll và retainAll](#collection-contracts-004)
- [removeIf và xử lý điều kiện xóa](#collection-contracts-005)
- [Chính sách phần tử null và giới hạn kiểu của từng cài đặt](#collection-contracts-006)
- [Phân biệt thay đổi cấu trúc, thay giá trị và sửa đối tượng phần tử](#collection-contracts-007)
- [Hợp đồng API so với chi tiết cài đặt có thể đổi giữa các JDK](#collection-contracts-008)

## <a id="collection-contracts-001">Khai báo theo giao diện và lựa chọn lớp cài đặt</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo theo giao diện và lựa chọn lớp cài đặt

## 1. Hai quyết định trong một dòng khai báo

Khi cần quản lý nhiều phần tử, ta phải trả lời hai câu hỏi khác nhau:

1. **Cấu trúc này cần cung cấp khả năng gì?** Danh sách có vị trí, tập không trùng, hàng đợi hay ánh xạ khóa–giá trị?
2. **Đối tượng nào sẽ thực hiện các khả năng đó?** Dùng mảng động, bảng băm, cây có thứ tự hay cấu trúc hai đầu?

Trong Java Collections Framework, giao diện (interface) giúp diễn đạt câu thứ nhất; lớp cài đặt (implementation class) là một phần quan trọng của câu thứ hai.

```java
List<String> names = new ArrayList<>();
```

Đọc dòng này theo ba phần:

- `List<String>`: biến `names` được khai báo là một danh sách các `String`.
- `new ArrayList<>()`: tạo đối tượng có lớp thực tế là `ArrayList`.
- `=`: lưu tham chiếu tới đối tượng đó vào biến; không tạo thêm một đối tượng `List`.

`List` là giao diện nên không thể viết `new List<>()`. Đối tượng vẫn là `ArrayList` sau khi gán vào biến kiểu `List`.

Mục tiêu của cách khai báo này là để phần mã sử dụng phụ thuộc vào **hợp đồng cần thiết**, đồng thời giữ việc chọn cách lưu trữ tại nơi khởi tạo. Nó giúp thay đổi có kiểm soát; không làm mọi lớp cài đặt trở nên tương đương.

Các ví dụ sử dụng API có trong Java 17. Trước khi thực hành, cần biết biến tham chiếu, phương thức, generics cơ bản và vòng lặp `for-each`.

## 2. Kiểu khai báo và lớp thực tế có vai trò gì?

### 2.1. Kiểu khai báo quyết định API có thể gọi trực tiếp

Hai biến sau cùng trỏ tới một đối tượng:

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.*.
ArrayList<String> storage = new ArrayList<>();
List<String> names = storage;

names.add("Lan");
storage.ensureCapacity(100);

System.out.println(names.size()); // 1
```

`add` thuộc hợp đồng `List` nên có thể gọi qua `names`. `ensureCapacity` là thao tác riêng của `ArrayList`, không nằm trong API `List` của Java 17. Vì vậy, `names.ensureCapacity(100)` không biên dịch, dù đối tượng thực tế là `ArrayList`.

Hai tham chiếu dùng chung đối tượng. Thay đổi thông qua một tham chiếu có thể được quan sát qua tham chiếu còn lại. Khai báo theo giao diện không sao chép dữ liệu và không tạo một lớp bọc.

Kiểu tham chiếu và giá trị đối tượng mà biến có thể giữ được quy định trong [JLS: Reference Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.3.1) và [Variables of Reference Type](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.2).

### 2.2. Đối tượng thực tế cung cấp phần cài đặt khi chạy

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.*.
List<String> tasks = new ArrayList<>();
tasks.add("code");

tasks = new LinkedList<>(tasks);
tasks.add("test");
```

Ban đầu `tasks` tham chiếu một `ArrayList`. Sau phép gán thứ hai, nó tham chiếu một `LinkedList` mới, được khởi tạo từ các phần tử của danh sách cũ.

Các lời gọi phương thức thể hiện thông thường như `tasks.add(...)` được phân phối tới phần cài đặt thích hợp của đối tượng khi chạy. Chữ ký có thể gọi vẫn phải hợp lệ với kiểu khai báo tại thời điểm biên dịch. Đây là mối liên hệ giữa đa hình (polymorphism), giao diện và lớp cài đặt; xem [JLS: Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12).

Lưu ý: phép gán trong ví dụ **tạo bản sao cấu trúc mới**, không biến đối tượng `ArrayList` cũ thành `LinkedList`. Nếu có biến khác giữ danh sách cũ, biến đó tiếp tục nhìn thấy danh sách cũ. Các phần tử vẫn là những tham chiếu được sao chép, không được nhân bản sâu.

## 3. Chọn giao diện theo ý nghĩa dữ liệu

Không phải cứ chọn giao diện ở cao nhất trong cây kế thừa là tốt. Hãy chọn giao diện đủ diễn đạt các thao tác và ý nghĩa mà phần mã đang cần.

| Nhu cầu | Giao diện phù hợp | Điều cần diễn đạt |
| --- | --- | --- |
| Chỉ cần duyệt từng phần tử | `Iterable<E>` | Không đòi hỏi chỉ số hay kiểm tra kích thước |
| Cần nhóm phần tử và thao tác chung như `size`, `contains` | `Collection<E>` | Chưa cam kết danh sách hay tập |
| Cần vị trí, chỉ số và có thể giữ phần tử trùng | `List<E>` | Thứ tự của chuỗi phần tử |
| Cần tính không trùng | `Set<E>` | Tập hợp theo quy tắc bằng nhau |
| Cần tập được sắp xếp | `SortedSet<E>` | Thứ tự tự nhiên hoặc `Comparator` |
| Cần tìm phần tử gần một mốc, như `ceiling` hoặc `floor` | `NavigableSet<E>` | Khả năng điều hướng trên tập có thứ tự |
| Cần đưa vào/lấy ra theo chính sách hàng đợi | `Queue<E>` | Thao tác trên đầu hàng đợi |
| Cần thao tác cả hai đầu, FIFO hoặc LIFO | `Deque<E>` | Ý định sử dụng hai đầu |
| Cần tra giá trị theo khóa | `Map<K, V>` | Mỗi khóa ánh xạ tối đa một giá trị |
| Cần ánh xạ được sắp theo khóa | `SortedMap<K, V>` | Thứ tự của khóa |
| Cần điều hướng theo khóa | `NavigableMap<K, V>` | Ví dụ tìm khóa nhỏ nhất không dưới một mốc |

`Map` thuộc Collections Framework nhưng **không kế thừa `Collection`**. Nó quản lý ánh xạ, thay vì chỉ một nhóm phần tử. Các view như `keySet()`, `values()` và `entrySet()` mới cung cấp cách nhìn dạng collection. Xem [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

Ví dụ, nếu thuật toán gọi `get(i)` thì tham số `Collection<String>` không đủ. Ngược lại, một phương thức chỉ duyệt và tính tổng không nhất thiết phải yêu cầu `ArrayList<String>`.

```java
// Đoạn minh họa phương thức; cần import java.util.Collection.
static int totalLength(Collection<String> values) {
    int total = 0;
    for (String value : values) {
        total += value.length();
    }
    return total;
}
```

Hợp đồng bổ sung của phương thức này là `values` và từng phần tử đều khác `null`. Kiểu `Collection<String>` tự nó chưa diễn đạt điều kiện đó.

Cách phân nhóm và các thao tác chung được mô tả trong [Collection API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html). Ngữ nghĩa chuỗi có vị trí được mô tả trong [List API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html); tính không trùng được mô tả trong [Set API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

## 4. Chọn lớp cài đặt theo yêu cầu cụ thể

Sau khi chọn giao diện, xét hành vi cần thiết rồi mới cân nhắc chi phí.

| Nhu cầu điển hình | Khai báo minh họa | Điểm cần lưu ý |
| --- | --- | --- |
| Danh sách có chỉ số, đọc và thêm cuối thường xuyên | `List<String> x = new ArrayList<>();` | Mảng động; phù hợp nhiều trường hợp danh sách thông thường |
| Có lý do cần cấu trúc danh sách liên kết | `List<String> x = new LinkedList<>();` | Truy cập theo chỉ số phải duyệt liên kết |
| Kiểm tra thuộc tập, không cần cam kết thứ tự duyệt | `Set<String> x = new HashSet<>();` | Phụ thuộc `equals`/`hashCode` |
| Loại trùng và giữ thứ tự thêm | `Set<String> x = new LinkedHashSet<>();` | Không phải sắp xếp theo giá trị |
| Tập có thứ tự và cần tìm quanh một mốc | `NavigableSet<Integer> x = new TreeSet<>();` | Dựa vào thứ tự tự nhiên hoặc `Comparator` |
| FIFO một luồng | `Queue<String> x = new ArrayDeque<>();` | `offer` vào cuối, `poll` từ đầu trong cách sử dụng này |
| Ngăn xếp hoặc thao tác hai đầu một luồng | `Deque<String> x = new ArrayDeque<>();` | Dùng `push/pop` cho LIFO hoặc API hai đầu |
| Lấy phần tử ưu tiên trước | `Queue<Integer> x = new PriorityQueue<>();` | Đầu hàng đợi theo độ ưu tiên, không phải thứ tự thêm |
| Tra cứu theo khóa, không yêu cầu thứ tự duyệt | `Map<String, Integer> x = new HashMap<>();` | Không cam kết thứ tự khóa |
| Ánh xạ giữ thứ tự thêm theo cấu hình mặc định | `Map<String, Integer> x = new LinkedHashMap<>();` | Lớp còn có cấu hình access-order riêng |
| Ánh xạ theo thứ tự khóa và có điều hướng | `NavigableMap<Integer, String> x = new TreeMap<>();` | Thứ tự khóa phụ thuộc phép so sánh |

Đây là bảng chọn ban đầu, không thay thế việc đọc hợp đồng của API đang dùng.

- `HashSet` không cam kết thứ tự duyệt. “Không cam kết” không có nghĩa là mỗi lần duyệt đều ngẫu nhiên. Việc một lần chạy trông có vẻ đã sắp xếp không tạo thành bảo đảm. [HashSet API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)
- `LinkedHashSet` giữ thứ tự chèn; thêm lại phần tử đã có không đưa nó tới cuối. [LinkedHashSet API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)
- `TreeSet` dùng phép so sánh để xác định vị trí và coi hai phần tử có kết quả so sánh bằng `0` là cùng một phần tử theo cấu trúc cây. Phép so sánh cần nhất quán với `equals` để đáp ứng hợp đồng chung của `Set`. [TreeSet API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- `PriorityQueue` không bảo đảm iterator duyệt theo thứ tự ưu tiên. Khi muốn lấy theo ưu tiên, dùng thao tác lấy đầu hàng đợi theo hợp đồng. [PriorityQueue API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)
- `ArrayDeque` không nhận `null` và không tự đồng bộ hóa cho truy cập nhiều luồng. [ArrayDeque API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)

Với `HashMap`, `LinkedHashMap` và `TreeMap`, tham khảo lần lượt [tra cứu theo bảng băm](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [thứ tự chèn hoặc thứ tự truy cập](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html), và [ánh xạ theo cây có thứ tự](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

## 5. Không đổi lớp cài đặt chỉ vì cùng biên dịch

Xét yêu cầu: “loại mã bị trùng nhưng giữ thứ tự xuất hiện đầu tiên”.

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.*.
Set<String> codes =
        new LinkedHashSet<>(List.of("B", "A", "B"));
System.out.println(codes); // [B, A]
```

Đổi `LinkedHashSet` thành `HashSet` vẫn đúng về kiểu, nhưng không còn bảo đảm thứ tự đầu ra. Đổi sang `TreeSet` với thứ tự tự nhiên sẽ tạo `[A, B]` đối với dữ liệu này, khác yêu cầu giữ thứ tự xuất hiện.

Vì vậy, khả năng thay thế cần được đánh giá theo ít nhất các chiều sau:

| Chiều kiểm tra | Câu hỏi trước khi thay |
| --- | --- |
| Phần tử trùng | Nghiệp vụ có cần giữ các lần xuất hiện không? |
| Thứ tự | Theo vị trí, theo lần thêm, theo phép so sánh hay không yêu cầu? |
| `null` | Có được thêm khóa, giá trị hoặc phần tử `null` không? |
| Sửa dữ liệu | Có cần `add`, `remove`, `set` hay cập nhật entry không? |
| Đồng thời | Có nhiều luồng cùng truy cập và sửa không? |
| Quan hệ bằng nhau | Dựa trên `equals/hashCode` hay comparator? |
| Chi phí | Thuật toán chủ yếu duyệt, tra cứu, thêm cuối hay truy cập ngẫu nhiên? |
| Sở hữu dữ liệu | Đây là dữ liệu riêng, bản sao hay view của dữ liệu khác? |

Viết `Set<String>` không truyền đạt đầy đủ yêu cầu giữ thứ tự chèn. Nếu thứ tự đó là một phần API, cần ghi rõ trong tài liệu và kiểm thử bằng kết quả có thể quan sát. Đừng dựa vào việc người đọc nhìn thấy `new LinkedHashSet<>()` ở một nơi xa.

## 6. Hợp đồng giao diện không bảo đảm mọi thao tác đều thành công

### 6.1. Có phương thức không có nghĩa đối tượng cho sửa

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.List.
List<String> fixed = List.of("A", "B");
// fixed.add("C"); // Biên dịch được, nhưng khi chạy sẽ ném UnsupportedOperationException.
```

`List.of` tạo danh sách không cho sửa qua các thao tác thay đổi của `List` và không nhận phần tử `null`. `List<String>` không phải kiểu “chỉ đọc”: API vẫn có `add`. Giới hạn đến từ đối tượng được trả về. Không dựa vào tên lớp nội bộ của kết quả `List.of`.

Ba trường hợp dễ nhầm:

| Cách tạo | Thêm/xóa phần tử | Thay phần tử bằng `set` | Quan hệ với nguồn |
| --- | --- | --- | --- |
| `new ArrayList<>(source)` | Được | Được | Bản sao nông của các phần tử |
| `Arrays.asList(array)` | Không thay kích thước | Được | View được chống lưng bởi mảng |
| `Collections.unmodifiableList(source)` | Không cho sửa qua view | Không | Thay đổi ở nguồn có thể xuất hiện qua view |
| `List.copyOf(source)` | Không | Không | Không phản ánh thay đổi cấu trúc của collection nguồn |

`List.copyOf` không nhất thiết tạo đối tượng mới nếu nguồn đã phù hợp; không dùng nó để yêu cầu một danh tính đối tượng khác. Các bản sao trên không tự nhân bản sâu các phần tử. Một phần tử có trạng thái thay đổi vẫn có thể bị sửa thông qua tham chiếu khác.

Xem hợp đồng [List factories và copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)), [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)) và [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)).

### 6.2. Khai báo theo giao diện không tạo an toàn nhiều luồng

`List<String> x = new ArrayList<>();` không an toàn hơn về đồng thời so với khai báo biến bằng `ArrayList<String>`. Nếu nhiều luồng cùng sửa, phải chọn cơ chế đồng bộ hoặc cấu trúc concurrent phù hợp với toàn bộ chuỗi thao tác.

Ngay cả khi từng thao tác được bảo vệ, chuỗi “kiểm tra rồi cập nhật” vẫn có thể cần một bảo đảm nguyên tử riêng. Bài này không thay thế việc thiết kế đồng thời.

### 6.3. `final` không làm collection bất biến

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.*.
final List<String> names = new ArrayList<>();
names.add("Lan"); // Hợp lệ: sửa đối tượng, không gán lại biến.
// names = new LinkedList<>(); // Không biên dịch: gán lại biến final.
```

Cần tách ba việc: gán lại tham chiếu, thay đổi cấu trúc collection và thay đổi trạng thái phần tử.

## 7. Cân nhắc chi phí mà không phá hợp đồng

`ArrayList` có `get/set` theo chỉ số với chi phí hằng số; thêm cuối có chi phí hằng số phân bổ (amortized), dù một lần thêm cụ thể có thể cần mở rộng mảng. Thêm hoặc xóa giữa danh sách thường cần dịch chuyển phần tử. Xem [ArrayList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

`LinkedList` cần đi qua liên kết khi truy cập theo chỉ số. Chèn/xóa tại vị trí mà iterator đã xác định khác với việc phải tìm vị trí trước. Vì vậy, kết luận “thêm/xóa nhiều thì luôn chọn LinkedList” bỏ qua chi phí tìm vị trí, cấp phát nút và đặc điểm truy cập bộ nhớ. [LinkedList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html)

Ví dụ thuật toán sau làm lộ giả định rằng truy cập theo chỉ số đủ rẻ:

```java
// Đoạn minh họa phương thức; cần import java.util.List.
static int totalByIndex(List<String> values) {
    int total = 0;
    for (int i = 0; i < values.size(); i++) {
        total += values.get(i).length();
    }
    return total;
}
```

Với `LinkedList`, nhiều lần `get(i)` có thể khiến tổng chi phí duyệt tăng tới bậc hai theo số phần tử. Nếu chỉ cần đi qua từng phần tử, cách `for-each` ở `totalLength` phù hợp hơn và không áp đặt truy cập ngẫu nhiên.

Khi thật sự cần đặc tính hiệu năng, hãy:

1. Xác định số lượng dữ liệu và tần suất thao tác.
2. Chọn cấu trúc đáp ứng đúng hành vi trước.
3. Đo trên dữ liệu và môi trường đại diện nếu đây là điểm nghẽn.
4. Giữ rõ giả định hiệu năng trong thuật toán hoặc hợp đồng.

Giao diện làm giảm phụ thuộc vào tên lớp; nó không xóa khác biệt về độ phức tạp.

## 8. Áp dụng ở tham số, giá trị trả về và trường dữ liệu

### 8.1. Tham số: yêu cầu đúng năng lực cần dùng

Nếu chỉ duyệt, nhận `Iterable<String>` là một lựa chọn. Nếu cần `size` hoặc `contains`, `Collection<String>` có thể phù hợp. Nếu cần vị trí, nhận `List<String>`. Nếu cần tính duy nhất, `Set<String>` diễn đạt ý định rõ hơn.

Nếu phương thức sửa collection của người gọi, phải nói rõ điều đó:

```java
// Đoạn minh họa phương thức; cần import java.util.List.
// Yêu cầu danh sách hỗ trợ add; phương thức sửa trực tiếp đối số.
static void appendReviewTask(List<String> tasks) {
    tasks.add("review");
}
```

Nhận `List` không có nghĩa là phương thức có thể an toàn gọi `add` trên mọi đối tượng `List`. Đối số `List.of(...)` không đáp ứng tiền điều kiện “hỗ trợ thêm phần tử”.

### 8.2. Trả về: cam kết hành vi mà người gọi cần

```java
// Đoạn minh họa phương thức; cần import java.util.*.
// Trả danh sách mới, sửa được; không sửa collection nguồn.
static List<String> copyTasks(Collection<String> source) {
    return new ArrayList<>(source);
}
```

`List` giữ thông tin về chuỗi và vị trí, trong khi `ArrayList` được giữ như quyết định cài đặt. Cần tài liệu hóa rằng kết quả sửa được và là bản sao nông.

Nếu API cần kết quả không cho sửa, có thể chọn `List.copyOf(source)`, đồng thời nêu giới hạn `null`. Nếu muốn trả view sống cùng dữ liệu nguồn, đó lại là hợp đồng khác và cần nói rõ.

Không trả trực tiếp collection nội bộ chỉ vì kiểu trả về là giao diện. Người gọi vẫn có thể gọi các thao tác sửa mà đối tượng hỗ trợ.

### 8.3. Trường dữ liệu: giữ ý định ổn định

```java
// Đoạn minh họa khai báo trường trong một lớp; cần import java.util.*.
private final Map<String, Integer> counts = new HashMap<>();
```

Ở đây lớp sở hữu ánh xạ và giữ quyết định chọn `HashMap` bên trong. Khi thay lớp cài đặt, vẫn phải kiểm tra thứ tự duyệt, `null`, tính đồng thời và những hành vi mà phần còn lại của lớp đang dựa vào.

## 9. Khi nào nên dùng kiểu lớp cụ thể?

Khai báo bằng lớp cụ thể hợp lý khi mã thật sự cần khả năng riêng của lớp đó.

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.ArrayList.
ArrayList<String> buffer = new ArrayList<>();
buffer.ensureCapacity(1_000);
buffer.add("A");
```

Có thể giới hạn phụ thuộc này trong bước xây dựng, rồi chuyển tham chiếu cho mã chỉ cần giao diện:

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.*.
ArrayList<String> buffer = new ArrayList<>();
buffer.ensureCapacity(1_000);
List<String> result = buffer;
```

Ngược lại, không cần ép về lớp chỉ để lấy một API đã có ở giao diện thích hợp. Nếu cần `ceiling`, khai báo `NavigableSet`; nếu cần `addFirst` trên Java 17, xem liệu ý định đúng có phải `Deque`.

Tránh lấy `List` rồi ép mù về `ArrayList`. Phương thức có thể nhận một `LinkedList` hoặc một danh sách không cho sửa, khiến phép ép thất bại với `ClassCastException`. Việc phải liên tục ép kiểu thường là dấu hiệu giao diện đang chọn chưa diễn đạt đúng nhu cầu.

### `var` vẫn suy luận ra một kiểu cụ thể

```java
// Đoạn minh họa đặt trong một phương thức; cần import java.util.*.
var first = new ArrayList<String>(); // Kiểu suy luận là ArrayList<String>.
List<String> second = new ArrayList<>();
```

`var` không tự nâng kiểu suy luận thành `List`. Nó là suy luận kiểu tĩnh cho biến cục bộ, không biến Java thành ngôn ngữ có kiểu động. Khi mục tiêu là thể hiện ranh giới `List` ngay ở dòng khai báo, viết tường minh giúp truyền đạt điều đó. [Local Variable Type Inference](https://docs.oracle.com/en/java/javase/17/language/local-variable-type-inference.html)

Generics cũng phải tương thích: `List<String>` không phải kiểu con của `List<Object>`. Dấu `<>` ở vế tạo đối tượng là cơ chế suy luận đối số kiểu, không bỏ kiểm tra kiểu. Chủ đề wildcard có thể giúp thiết kế tham số linh hoạt hơn, nhưng không cần dùng raw type để giải quyết vấn đề này.

## 10. Chương trình hoàn chỉnh

Lưu đúng tên `CollectionChoiceDemo.java`. Chương trình dùng các phần tử khác `null` và không có xử lý đồng thời.

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

public final class CollectionChoiceDemo {
    static void appendReviewTask(List<String> tasks) {
        tasks.add("review");
    }

    static int totalLength(Collection<String> values) {
        int total = 0;
        for (String value : values) {
            total += value.length();
        }
        return total;
    }

    static List<String> copyTasks(Collection<String> source) {
        return new ArrayList<>(source);
    }

    public static void main(String[] args) {
        List<String> arrayTasks =
                new ArrayList<>(List.of("code", "test"));
        List<String> linkedTasks =
                new LinkedList<>(List.of("code", "test"));

        appendReviewTask(arrayTasks);
        appendReviewTask(linkedTasks);
        System.out.println("ArrayList: " + arrayTasks);
        System.out.println("LinkedList: " + linkedTasks);
        System.out.println("Total length: " + totalLength(arrayTasks));

        Set<String> uniqueInOrder =
                new LinkedHashSet<>(List.of("b", "a", "b"));
        System.out.println("Unique in order: " + uniqueInOrder);

        NavigableSet<Integer> scores =
                new TreeSet<>(List.of(30, 10, 20));
        System.out.println("Sorted: " + scores);
        System.out.println("At least 15: " + scores.ceiling(15));

        Queue<String> jobs = new ArrayDeque<>();
        jobs.offer("first");
        jobs.offer("second");
        System.out.println("Queue: " + jobs.poll() + ", " + jobs.poll());

        Deque<String> history = new ArrayDeque<>();
        history.push("open");
        history.push("edit");
        System.out.println("Stack: " + history.pop() + ", " + history.pop());

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("code", 1);
        counts.put("test", 2);
        counts.put("code", 3);
        System.out.println("Counts: " + counts);

        List<String> snapshot = List.copyOf(arrayTasks);
        arrayTasks.add("deploy");
        System.out.println("Snapshot: " + snapshot);
        System.out.println("Original: " + arrayTasks);
        try {
            snapshot.add("extra");
        } catch (UnsupportedOperationException ex) {
            System.out.println("Snapshot add: unsupported");
        }

        List<String> editableCopy = copyTasks(snapshot);
        editableCopy.add("extra");
        System.out.println("Editable copy: " + editableCopy);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 CollectionChoiceDemo.java
java CollectionChoiceDemo
```

Cần JDK có `javac`; `--release 17` yêu cầu trình biên dịch kiểm tra mã và API theo nền Java 17. Chương trình đã được kiểm tra bằng lệnh này trên một JDK mới hơn.

Stdout:

```text
ArrayList: [code, test, review]
LinkedList: [code, test, review]
Total length: 14
Unique in order: [b, a]
Sorted: [10, 20, 30]
At least 15: 20
Queue: first, second
Stack: edit, open
Counts: {code=3, test=2}
Snapshot: [code, test, review]
Original: [code, test, review, deploy]
Snapshot add: unsupported
Editable copy: [code, test, review, extra]
```

Các kết quả cần giải thích được:

- `appendReviewTask` làm việc với hai lớp cùng đáp ứng yêu cầu danh sách sửa được; nó không cần nhận `ArrayList`.
- Tổng độ dài là `4 + 4 + 6 = 14`.
- `LinkedHashSet` giữ lần xuất hiện đầu tiên của `b` rồi `a`, loại lần `b` bị trùng.
- `TreeSet` sắp `10, 20, 30`; `ceiling(15)` trả `20`.
- `ArrayDeque` được nhìn qua `Queue` để dùng FIFO; cũng lớp đó được nhìn qua `Deque` để dùng LIFO.
- `LinkedHashMap` ở chế độ mặc định giữ thứ tự chèn khóa. Cập nhật `code` thành `3` không tạo khóa thứ hai và không chuyển khóa đó xuống cuối.
- `snapshot` không nhận phần tử `deploy` thêm sau vào danh sách nguồn; gọi `add` trên nó bị từ chối.
- `copyTasks` tạo danh sách mới sửa được, nên `editableCopy` thêm được `extra`.

Không có output nào dựa vào thứ tự duyệt không được cam kết của `HashSet` hay `HashMap`.

## 11. Lỗi thường gặp và cách sửa

| Cách làm | Vì sao có vấn đề | Cách xử lý |
| --- | --- | --- |
| Dùng `ArrayList` ở mọi tham số | Ép người gọi dùng một lớp dù thuật toán không cần | Chọn `List`, `Collection` hoặc `Iterable` theo yêu cầu |
| Dùng `Collection` cho cả chỗ cần `get(index)` | Làm mất API cần thiết | Dùng `List` nếu vị trí là một phần hợp đồng |
| Đổi `LinkedHashSet` sang `HashSet` vì cùng là `Set` | Làm mất bảo đảm thứ tự | Kiểm tra yêu cầu quan sát được trước khi thay |
| Coi mọi `Queue` đều FIFO | `PriorityQueue` dùng chính sách khác | Chọn và ghi rõ chính sách của lớp cài đặt |
| Gọi `add` trên kết quả `List.of` | Thao tác thay đổi không được hỗ trợ | Tạo bản sao sửa được nếu cần cập nhật |
| Dùng `final` để bảo đảm dữ liệu bất biến | Chỉ ngăn gán lại biến | Thiết kế quyền sửa collection và phần tử riêng |
| Ép mọi `List` về `ArrayList` | Đối tượng thực tế có thể là lớp khác | Dùng API giao diện hoặc thể hiện yêu cầu cụ thể |
| Chọn `LinkedList` chỉ vì “xóa nhanh” | Chưa tính chi phí tìm vị trí và thuật toán | Xem toàn bộ mẫu thao tác và đo khi cần |
| Trả collection nội bộ bằng kiểu `List` rồi coi là an toàn | Người gọi vẫn có thể sửa đối tượng | Chọn copy/view và tài liệu hóa quyền sở hữu |
| Cho rằng khai báo bằng interface tạo thread safety | Kiểu tham chiếu không cung cấp đồng bộ | Chọn chính sách đồng thời phù hợp |

## 12. Quy trình lựa chọn để áp dụng vào bài toán mới

1. **Viết yêu cầu dữ liệu:** có trùng không, cần thứ tự nào, có tra theo khóa không?
2. **Liệt kê thao tác:** duyệt, truy cập chỉ số, tìm thành viên, lấy hai đầu, tìm quanh mốc?
3. **Chọn giao diện:** đủ thao tác và đủ ý nghĩa, không kéo theo lớp cụ thể nếu không cần.
4. **Chọn lớp cài đặt:** xét cấu trúc lưu trữ, thứ tự, `null` và giới hạn của từng lớp.
5. **Xác định quyền sửa và sở hữu:** sửa nguồn, tạo bản sao hay trả view?
6. **Xét đồng thời và chi phí:** dựa trên cách dùng thực tế.
7. **Kiểm thử hợp đồng:** bảo đảm đầu ra, thứ tự và ngoại lệ vẫn đúng nếu thay lớp.

Ví dụ: “lưu mã sản phẩm duy nhất theo thứ tự nhập” dẫn tới `Set` ở ranh giới sử dụng, `LinkedHashSet` khi tạo đối tượng, cùng tài liệu và kiểm thử cho thứ tự nhập. “Tìm mức giá nhỏ nhất không thấp hơn ngân sách” dẫn tới `NavigableSet` và một cài đặt có thứ tự như `TreeSet`.

## 13. Tự kiểm tra và thực hành

### Câu hỏi

1. Với `List<String> x = new ArrayList<>();`, kiểu khai báo và lớp thực tế là gì?
2. Vì sao `x.ensureCapacity(100)` không biên dịch trên Java 17?
3. Một hàm chỉ tính tổng độ dài chuỗi có cần nhận `ArrayList<String>` không?
4. Thay `LinkedHashSet` bằng `TreeSet` có giữ thứ tự nhập không?
5. Vì sao `List<String> x = List.of("A");` vẫn không cho `add`?
6. Vì sao `final List<String>` vẫn có thể thay đổi nội dung?
7. Đổi `ArrayList` sang `LinkedList` có bảo đảm một thuật toán theo chỉ số giữ nguyên hiệu năng không?
8. API trả `List` cần nói thêm điều gì về quyền sửa và quan hệ với nguồn?

### Bài tập

- Thay `arrayTasks` trong chương trình bằng `LinkedList` mà không sửa `appendReviewTask`. Giải thích phần hành vi nào vẫn giữ.
- Dùng cùng dữ liệu `"B", "A", "B"` để tạo `HashSet`, `LinkedHashSet` và `TreeSet`. Chỉ khẳng định thứ tự trong những trường hợp có hợp đồng bảo đảm.
- Viết hàm trả danh sách mã không trùng theo thứ tự đầu tiên. Chọn kiểu trả về để biểu đạt kết quả có vị trí, tài liệu hóa tính duy nhất và quyền sửa.
- Sửa `copyTasks` thành trả kết quả không cho sửa bằng `List.copyOf`; đổi tên và tài liệu phương thức cho đúng hợp đồng mới.
- Viết kiểm tra rằng thêm vào bản sao sửa được không làm thay đổi cấu trúc collection nguồn.

### Gợi ý đối chiếu

1. `List<String>` là kiểu khai báo, `ArrayList` là lớp thực tế.
2. `ensureCapacity` không thuộc API `List` của nền Java 17.
3. Không; `Collection<String>` hoặc `Iterable<String>` có thể đủ tùy thao tác và hợp đồng.
4. Không; `TreeSet` sắp theo phép so sánh, không theo lần nhập.
5. Đối tượng không hỗ trợ thao tác sửa, dù chữ ký tồn tại trên giao diện.
6. `final` chỉ cố định tham chiếu.
7. Không; nhiều lần truy cập chỉ số có thể làm thay đổi đáng kể chi phí.
8. Có cho sửa không, có phải bản sao/view không, thay đổi nguồn có phản ánh vào kết quả không, và có giới hạn phần tử nào cần biết không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-002">size, isEmpty, contains và ý nghĩa giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# size, isEmpty, contains và ý nghĩa giá trị trả về

Khi làm việc với collection, ba câu hỏi xuất hiện rất thường xuyên:

- Hiện có bao nhiêu phần tử?
- Có phần tử nào hay không?
- Có một giá trị cụ thể trong collection hay không?

Java cung cấp `size()`, `isEmpty()` và `contains(Object)` để trả lời ba câu hỏi đó. Tuy đều là thao tác truy vấn, chúng mang ý nghĩa khác nhau. Hiểu đúng giá trị trả về giúp tránh nhầm số phần tử với dung lượng, nhầm sự tồn tại với số lần xuất hiện, hoặc nhầm hai đối tượng có nội dung bằng nhau với cùng một tham chiếu.

Bài này trình bày hợp đồng chung trong Java Collections Framework và các điểm cần kiểm tra ở cách triển khai cụ thể. Ví dụ hoàn chỉnh dùng Java 17 trở lên.

## 1. Ba phương thức, ba loại thông tin

Các chữ ký trong `Collection<E>` là:

```java
int size();
boolean isEmpty();
boolean contains(Object o);
```

Đây là chữ ký API, không phải một chương trình Java độc lập.

| Phương thức | Câu hỏi được trả lời | Giá trị trả về |
| --- | --- | --- |
| `size()` | Có bao nhiêu phần tử đang thuộc collection? | Một số nguyên `int` |
| `isEmpty()` | Collection có rỗng không? | `true` nếu không có phần tử; ngược lại `false` |
| `contains(o)` | Có ít nhất một phần tử tương ứng với giá trị `o` không? | `true` nếu có; `false` nếu không, với đầu vào hợp lệ |

Một danh sách có 10 phần tử vẫn có thể trả `false` cho `contains("Java")`: số lượng không nói lên nội dung. Một danh sách chỉ có một phần tử `null` lại không rỗng: nó có một vị trí đã được chiếm bởi một phần tử hợp lệ của cách triển khai đó.

Các thao tác trên trả lời trạng thái của đối tượng được truy vấn. Chúng không tải dữ liệu từ cơ sở dữ liệu, không tự lọc phần tử theo điều kiện nghiệp vụ và không đếm những phần tử mà chương trình “dự định” thêm sau này. [Java 17: Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

## 2. size(): số phần tử hiện có

### 2.1. Size không phải capacity

Trong một `ArrayList`, dung lượng mảng bên trong và số phần tử hiện có là hai đại lượng khác nhau:

```java
List<String> names = new ArrayList<>(20);

System.out.println(names.size()); // 0
names.add("Lan");
System.out.println(names.size()); // 1
```

Đây là đoạn minh họa trong một phương thức, với import `java.util.List` và `java.util.ArrayList`.

Tham số `20` chuẩn bị dung lượng ban đầu. Nó không thêm 20 phần tử, cũng không tạo 20 phần tử `null` để người dùng truy cập. Ngay sau khi khởi tạo, `names.get(0)` vẫn không hợp lệ vì danh sách chưa có phần tử nào.

Không suy ra kích thước từ dung lượng đã dự kiến hoặc từ số ô của mảng nội bộ. Chỉ `size()` mô tả số phần tử thuộc danh sách theo API. [Java 17: ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

### 2.2. Phần tử trùng và null vẫn được tính theo cấu trúc

Với một danh sách cho phép giá trị trùng và `null`:

```java
List<String> names = new ArrayList<>();
names.add("Lan");
names.add("Lan");
names.add(null);

System.out.println(names.size()); // 3
```

Hai lần xuất hiện của `"Lan"` chiếm hai vị trí khác nhau. Phần tử `null` chiếm vị trí thứ ba. `size()` không tự loại trùng, bỏ qua `null` hoặc đếm số người duy nhất.

Nếu chuyển dữ liệu sang `HashSet`, cấu trúc tập chỉ giữ một đại diện cho mỗi giá trị theo quy tắc bằng nhau của nó:

```java
Set<String> unique = new HashSet<>(names);
System.out.println(unique.size()); // 2: "Lan" và null
```

Các đoạn này cần import các kiểu được dùng và đặt trong một phương thức. Kết quả khác nhau do nội dung thực tế của hai cấu trúc khác nhau; `size()` không mang hai ý nghĩa tùy tiện.

Nếu cần đếm số lần một giá trị xuất hiện, `size()` không phải câu trả lời. Chẳng hạn, danh sách `["Lan", "Lan", null]` có kích thước 3 nhưng `"Lan"` xuất hiện 2 lần. Có thể dùng vòng lặp hoặc thao tác đếm theo đúng phép so sánh cần thiết.

### 2.3. Size phản ánh thay đổi đã thực hiện

Sau khi xóa một lần xuất hiện khỏi danh sách:

```java
names.remove("Lan");
System.out.println(names.size());          // 2
System.out.println(names.contains("Lan")); // true
```

Danh sách vẫn còn một `"Lan"` và một `null`. Xóa một phần tử không đồng nghĩa xóa mọi phần tử bằng nó.

Sau `clear()`, nếu thao tác được cách triển khai hỗ trợ và thành công, collection không còn phần tử nên `size()` là 0.

### 2.4. Kiểu int có giới hạn

Hợp đồng `Collection.size()` trả `Integer.MAX_VALUE` nếu số phần tử vượt giới hạn biểu diễn đó. Vì vậy, không nên dùng API này như một bộ đếm `long` không giới hạn.

Đây là giới hạn hợp đồng, không khẳng định một collection cụ thể có thể thực sự chứa số phần tử lớn như vậy trong bộ nhớ. Với những bài toán dữ liệu cực lớn, cần xem cả cấu trúc lưu trữ và API đếm thực tế. [Java 17: Collection.size](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#size())

## 3. isEmpty(): có phần tử hay không?

### 3.1. Giá trị boolean diễn đạt trực tiếp ý định

`isEmpty()` trả:

- `true`: collection không có phần tử.
- `false`: collection có ít nhất một phần tử.

Trong trạng thái ổn định, `collection.isEmpty()` và `collection.size() == 0` cho cùng câu trả lời. Khi chỉ cần biết có dữ liệu hay không, `isEmpty()` diễn đạt ý định trực tiếp hơn:

```java
if (orders.isEmpty()) {
    System.out.println("Chưa có đơn hàng");
}
```

Đây là đoạn minh họa; `orders` là một collection đã được khởi tạo. Không cần tính toàn bộ số lượng chỉ để kiểm tra rỗng.

Tuy nhiên, không kết luận rằng `isEmpty()` luôn có chi phí hằng số cho mọi lớp, hoặc luôn nhanh hơn `size()` trong mọi trường hợp. Interface quy định ý nghĩa; chi phí phụ thuộc cách triển khai.

### 3.2. Rỗng khác với null

Ba trạng thái sau khác nhau:

```java
List<String> missing = null;
List<String> empty = new ArrayList<>();

List<String> oneNull = new ArrayList<>();
oneNull.add(null);
```

| Biến | Có đối tượng danh sách không? | Kích thước |
| --- | --- | --- |
| `missing` | Không | Không thể gọi `size()` hay `isEmpty()` |
| `empty` | Có | 0 |
| `oneNull` | Có | 1 |

Gọi `missing.isEmpty()` ném `NullPointerException`: chưa có đối tượng để nhận lời gọi. Trong khi đó, `oneNull.isEmpty()` là `false`.

Nếu một API quy định luôn trả collection, nên trả collection rỗng khi không có kết quả. Người gọi khi đó có thể dùng `isEmpty()` mà không phải đoán `null` có nghĩa gì.

Nếu đang tích hợp với API có thể trả `null`, cần xử lý theo hợp đồng cụ thể:

```java
if (items == null || items.isEmpty()) {
    System.out.println("Không có danh sách để xử lý");
}
```

Đây là đoạn minh họa cho trường hợp nghiệp vụ chủ động xem hai trạng thái tương đương. Không tự gộp `null` và rỗng nếu chúng biểu thị hai trạng thái khác nhau, chẳng hạn “chưa tải” và “đã tải nhưng không có kết quả”.

## 4. contains(): hỏi sự tồn tại, không hỏi vị trí hoặc số lượng

### 4.1. True nghĩa là có ít nhất một phần tử phù hợp

Với danh sách `["Lan", "Lan", null]`:

| Lời gọi | Kết quả |
| --- | --- |
| `contains("Lan")` | `true` |
| `contains("Minh")` | `false` |
| `contains(null)` | `true`, vì `ArrayList` này chứa `null` |

Kết quả `true` không cho biết:

- Có bao nhiêu phần tử bằng giá trị đang tìm.
- Phần tử nằm ở chỉ số nào.
- Phần tử được thêm lúc nào.
- Đối tượng được lưu có phải chính tham chiếu truyền vào không.

Muốn biết vị trí trong `List`, cần API như `indexOf`. Muốn lấy đối tượng phù hợp, cần tìm và trả đối tượng ấy. Muốn đếm, cần thao tác đếm.

Không dùng `contains` rồi tiếp tục tìm lại cùng phần tử nếu có thể thực hiện việc tìm và lấy kết quả trong một lượt; điều đó vừa tránh lặp công việc, vừa giúp mục đích của mã rõ hơn.

### 4.2. Bình đẳng giá trị khác với cùng tham chiếu

Một mô hình đọc hợp đồng chung của `contains(o)` là: có tồn tại phần tử `e` sao cho `Objects.equals(o, e)` đúng hay không.

Với chuỗi:

```java
List<String> names = new ArrayList<>();
names.add("Lan");

String query = new String("Lan");
System.out.println(names.contains(query)); // true
```

Dù `query` là một đối tượng mới, `String.equals` so nội dung, nên `contains` vẫn trả `true`.

Đối với lớp tự định nghĩa, kết quả phụ thuộc quy tắc bằng nhau của lớp đó. Một lớp không ghi đè `equals` thường vẫn dùng bình đẳng tham chiếu từ `Object`. Hai đối tượng có trường trông giống nhau không tự nhiên trở thành bằng nhau.

Cũng không nên hiểu mô tả hợp đồng là yêu cầu mọi lớp phải gọi `equals` trên mọi phần tử. Một cấu trúc băm có thể dùng mã băm để loại bớt ứng viên trước. Ý nghĩa kết quả và thuật toán thực thi là hai điều cần phân biệt.

### 4.3. Contains nhận Object nên truy vấn khác kiểu có thể biên dịch

Chữ ký là `contains(Object o)`, không phải `contains(E o)`. Vì vậy:

```java
List<Integer> numbers = List.of(1, 2, 3);

System.out.println(numbers.contains(1));   // true
System.out.println(numbers.contains(1L));  // false
```

`1` được boxing thành `Integer`; `1L` được boxing thành `Long`. `Integer.equals` không coi một `Long` là bằng nó chỉ vì cùng giá trị số học.

Đây không phải phép tìm “số 1 bất kể kiểu”. Nếu dữ liệu có nhiều kiểu số, hãy chuẩn hóa kiểu hoặc viết điều kiện so sánh rõ ràng trước khi dùng cấu trúc.

Không suy ra rằng truy vấn sai kiểu luôn trả `false`. Một số collection có thể ném `ClassCastException` khi đối tượng không tương thích với cách so sánh hoặc kiểu phần tử được hỗ trợ.

### 4.4. HashSet cần equals và hashCode nhất quán

Khi tìm trong `HashSet`, mã băm hỗ trợ xác định vùng ứng viên và phép so sánh bằng nhau xác nhận giá trị phù hợp.

Hai nguyên tắc thiết yếu:

1. Nếu `a.equals(b)` là `true`, `a.hashCode()` và `b.hashCode()` phải bằng nhau.
2. Không sửa các trường dùng cho `equals`/`hashCode` khi đối tượng đang là phần tử trong tập băm.

Nếu một lớp vi phạm hợp đồng hoặc khóa thay đổi sau khi thêm, có thể xuất hiện tình huống phần tử vẫn thấy khi duyệt nhưng `contains` không tìm được như mong đợi. Khi chẩn đoán, cần kiểm tra mô hình bình đẳng của đối tượng, không chỉ nghi ngờ collection.

Cùng mã băm không bảo đảm bằng nhau: các giá trị khác nhau có thể va chạm mã băm và vẫn cùng tồn tại. [Java 17: HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

### 4.5. TreeSet dùng thứ tự so sánh để tìm phần tử

`TreeSet` dùng thứ tự tự nhiên hoặc comparator. Trong cấu trúc này, hai phần tử có kết quả so sánh bằng 0 được xử lý là tương đương để tìm và loại trùng.

Một ví dụ dễ quan sát là `BigDecimal`:

```java
BigDecimal first = new BigDecimal("1.0");
BigDecimal query = new BigDecimal("1.00");

System.out.println(first.equals(query));    // false
System.out.println(first.compareTo(query)); // 0
```

`equals` của `BigDecimal` xét cả giá trị và scale; `compareTo` so theo giá trị số. Do đó:

```java
List<BigDecimal> list = List.of(first);
Set<BigDecimal> tree = new TreeSet<>();
tree.add(first);

System.out.println(list.contains(query)); // false
System.out.println(tree.contains(query)); // true
```

Các đoạn minh họa này cần import `BigDecimal`, `List`, `Set` và `TreeSet`.

Điểm cần học không phải “`contains` không đáng tin”, mà là phải biết quy tắc bình đẳng của dữ liệu và cấu trúc. Với tập cây, thứ tự nhất quán với `equals` là điều kiện để hành vi tuân thủ đầy đủ hợp đồng chung của `Set`. Đừng chọn comparator chỉ vì đẹp khi sắp mà không xét việc nó đồng thời quyết định tính duy nhất. [Java 17: TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html), [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)

### 4.6. Contains null không luôn trả false khi collection không hỗ trợ null

Một collection không cho phép `null` có thể ném `NullPointerException` khi gọi `contains(null)`, hoặc trả `false` trong những trường hợp được hợp đồng cho phép.

Vì vậy, không dùng một phép thử chung kiểu “cứ hỏi `contains(null)` rồi xem” để thay thế việc biết hợp đồng đầu vào. Ví dụ `ArrayList` trong bài cho phép `null`, nhưng không nên mang kỳ vọng ấy áp dụng cho mọi collection khác.

Các ngoại lệ này không biến `false` thành giá trị biểu thị lỗi. `false` nghĩa là không tìm thấy theo hợp đồng; ngoại lệ nghĩa là yêu cầu không thể được xử lý hợp lệ trong tình huống đó.

## 5. Với Map, phải nói rõ đang tìm khóa hay giá trị

`Map<K,V>` không kế thừa `Collection<E>`, nhưng cũng có `size()` và `isEmpty()`:

- `size()` đếm số ánh xạ khóa–giá trị.
- `isEmpty()` hỏi có ánh xạ nào không.
- `containsKey(key)` hỏi khóa có tồn tại không.
- `containsValue(value)` hỏi có ít nhất một ánh xạ đến giá trị đó không.

```java
Map<String, String> notes = new HashMap<>();
notes.put("A", null);

System.out.println(notes.size());              // 1
System.out.println(notes.isEmpty());           // false
System.out.println(notes.containsKey("A"));    // true
System.out.println(notes.containsValue(null)); // true
```

Đoạn minh họa dùng `HashMap`, vốn cho phép giá trị `null`. Một ánh xạ đến `null` vẫn là một ánh xạ.

Không viết `map.get(key) != null` như cách kiểm tra tồn tại tổng quát khi map cho phép giá trị `null`: khóa có thể đã tồn tại và đang ánh xạ đến `null`.

`containsKey` và `containsValue` cũng không nhất thiết có cùng chi phí. Map được tổ chức để tra cứu theo khóa thường không có chỉ mục tương đương theo giá trị. Hãy dùng đúng câu hỏi thay vì coi hai phương thức là hoán đổi được. [Java 17: Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

## 6. Hợp đồng chung không cam kết mọi truy vấn đều nhanh như nhau

| Cấu trúc | `size()` thường gặp | `contains()` thường gặp |
| --- | --- | --- |
| `ArrayList` | Lấy số lượng đang quản lý | Duyệt tìm, tuyến tính trong trường hợp tổng quát |
| `LinkedList` | Lấy số lượng đang quản lý | Duyệt các nút để tìm |
| `HashSet` | Lấy số lượng đang quản lý | Trung bình thuận lợi khi mã băm phân bố tốt |
| `TreeSet` | Lấy số lượng đang quản lý | Theo đường tìm trong cây, chi phí logarit |
| `ConcurrentLinkedQueue` | Phải duyệt để đếm | Phải tìm theo dữ liệu của hàng đợi |

Bảng nhằm chỉ ra sự khác nhau về đường thực thi. Chi phí `equals`, comparator, mã băm và truy cập bộ nhớ còn ảnh hưởng thời gian thực tế.

`AbstractCollection` cung cấp cách triển khai `isEmpty()` dựa trên `size() == 0` và `contains` dựa trên duyệt. Lớp cụ thể có thể ghi đè. Không dùng cách triển khai mặc định để suy ra mọi collection đều có cùng hiệu năng. [Java 17: AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html)

Ví dụ, với một danh sách lớn, gọi `contains` lặp lại cho từng phần tử đầu vào có thể tạo tổng chi phí lớn. Nếu bài toán chủ yếu là kiểm tra thành viên và không cần giữ lần lặp, một tập được xây dựng một lần có thể phù hợp hơn. Trước khi chuyển, phải xác nhận quy tắc bằng nhau và yêu cầu giữ thứ tự.

## 7. Truy vấn không tự bảo vệ chuỗi thao tác đồng thời

### 7.1. Kết quả đúng lúc kiểm tra chưa chắc còn đúng lúc sử dụng

Đoạn minh họa sau có khoảng trống giữa hai thao tác:

```java
if (!queue.isEmpty()) {
    String job = queue.poll();
    // Luồng khác có thể đã lấy phần tử cuối trước poll().
}
```

Nếu `queue` được nhiều luồng tiêu thụ, nó có thể rỗng sau lần kiểm tra. Với queue không cho phép phần tử `null`, thường có thể gọi thao tác lấy trước rồi xử lý kết quả:

```java
String job = queue.poll();
if (job != null) {
    process(job);
}
```

Đây là mã minh họa luồng xử lý, không phải chương trình độc lập: `queue` và `process` được ứng dụng cung cấp. Muốn chờ khi rỗng, có thể cần API như `take()` của `BlockingQueue`, thay vì tự kiểm tra liên tục.

Tương tự, `if (!list.contains(x)) list.add(x)` không tự bảo đảm không trùng khi hai luồng cùng chạy. Phải dùng một thao tác đáp ứng yêu cầu nguyên tử hoặc một vùng đồng bộ bao trùm cả quy trình.

### 7.2. Size có thể không phải ảnh chụp toàn hệ thống

Tài liệu `ConcurrentLinkedQueue` nêu rằng `size()` phải duyệt và có thể không chính xác nếu hàng đợi đổi trong lúc đếm. Không dùng kết quả này để tự áp đặt một giới hạn sức chứa có tính nguyên tử:

```java
// Không đủ để bảo đảm queue không vượt 100 phần tử khi có ghi đồng thời.
if (queue.size() < 100) {
    queue.offer(job);
}
```

Nếu cần giới hạn số công việc chờ, chọn một queue có sức chứa và cơ chế thêm phù hợp với yêu cầu đó.

Các phương thức truy vấn cũng không làm một `ArrayList` hoặc `HashSet` thông thường an toàn khi luồng khác đang sửa mà không có cơ chế phối hợp. Không gọi `size()` như một cách “đồng bộ ngầm” hoặc bảo đảm nhìn thấy mọi thay đổi. [Java 17: ConcurrentLinkedQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html)

## 8. Chương trình Java 17 hoàn chỉnh

Lưu mã sau vào `CollectionQueriesDemo.java`:

```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CollectionQueriesDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(20);
        System.out.println("initial size = " + names.size());
        System.out.println("initial empty = " + names.isEmpty());

        names.add("Lan");
        names.add("Lan");
        names.add(null);
        System.out.println("list size = " + names.size());
        System.out.println("list empty = " + names.isEmpty());
        System.out.println("contains Lan = " + names.contains(new String("Lan")));
        System.out.println("contains null = " + names.contains(null));
        System.out.println("contains Minh = " + names.contains("Minh"));

        Set<String> unique = new HashSet<>(names);
        System.out.println("set size = " + unique.size());

        names.remove("Lan");
        System.out.println("after one removal size = " + names.size());
        System.out.println("after one removal contains Lan = " + names.contains("Lan"));

        List<Integer> numbers = List.of(1, 2, 3);
        System.out.println("contains Integer 1 = " + numbers.contains(1));
        System.out.println("contains Long 1 = " + numbers.contains(1L));

        BigDecimal first = new BigDecimal("1.0");
        BigDecimal query = new BigDecimal("1.00");
        List<BigDecimal> prices = List.of(first);
        Set<BigDecimal> sortedPrices = new TreeSet<>();
        sortedPrices.add(first);
        System.out.println("decimal equals = " + first.equals(query));
        System.out.println("decimal comparison = " + first.compareTo(query));
        System.out.println("price list contains = " + prices.contains(query));
        System.out.println("price tree contains = " + sortedPrices.contains(query));

        Map<String, String> notes = new HashMap<>();
        notes.put("A", null);
        System.out.println("map size = " + notes.size());
        System.out.println("map empty = " + notes.isEmpty());
        System.out.println("map contains key A = " + notes.containsKey("A"));
        System.out.println("map contains value null = " + notes.containsValue(null));

        names.clear();
        System.out.println("cleared size = " + names.size());
        System.out.println("cleared empty = " + names.isEmpty());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CollectionQueriesDemo.java
java CollectionQueriesDemo
```

Stdout:

```text
initial size = 0
initial empty = true
list size = 3
list empty = false
contains Lan = true
contains null = true
contains Minh = false
set size = 2
after one removal size = 2
after one removal contains Lan = true
contains Integer 1 = true
contains Long 1 = false
decimal equals = false
decimal comparison = 0
price list contains = false
price tree contains = true
map size = 1
map empty = false
map contains key A = true
map contains value null = true
cleared size = 0
cleared empty = true
```

Các nhóm kết quả minh chứng từng điểm:

1. Dung lượng ban đầu 20 không tạo phần tử, nên ban đầu kích thước bằng 0 và rỗng.
2. Hai chuỗi giống nhau cùng một `null` làm danh sách có 3 phần tử, nhưng tập băm có 2 giá trị.
3. Một đối tượng `String` mới vẫn được tìm thấy theo nội dung.
4. Xóa một lần `"Lan"` làm kích thước giảm nhưng không loại mọi lần xuất hiện.
5. `Integer(1)` không bằng `Long(1)` theo phép `equals` của các lớp bao tương ứng.
6. `BigDecimal` cho thấy sự khác biệt giữa `equals` và thứ tự tự nhiên khi tìm trong tập cây.
7. Một ánh xạ có giá trị `null` vẫn làm map không rỗng.
8. Sau khi xóa hết phần tử, `size()` và `isEmpty()` phản ánh trạng thái rỗng.

Chương trình không in thứ tự phần tử của `HashSet`, nên kết quả không phụ thuộc thứ tự duyệt mà lớp đó không bảo đảm.

## 9. Lỗi thường gặp và cách kiểm tra

| Hiểu nhầm | Điều thực tế cần nhớ |
| --- | --- |
| `new ArrayList<>(100)` có kích thước 100 | Chỉ chuẩn bị dung lượng; kích thước ban đầu là 0 |
| `isEmpty()` kiểm tra được biến có `null` không | Gọi phương thức trên tham chiếu `null` gây lỗi |
| Có một phần tử `null` thì danh sách rỗng | Nó vẫn có một phần tử |
| `contains` trả số lần xuất hiện hoặc vị trí | Nó chỉ trả thông tin có/không |
| Hai đối tượng khác tham chiếu không thể được tìm thấy bằng `contains` | Quy tắc bình đẳng giá trị có thể coi chúng bằng nhau |
| `contains(1L)` phải tìm được số `Integer` 1 | Kiểu số bao và quy tắc `equals` vẫn có ý nghĩa |
| Mọi collection xử lý `contains(null)` giống nhau | Phải kiểm tra chính sách của lớp |
| `contains` luôn duyệt toàn bộ và gọi `equals` | Cấu trúc có thể dùng băm hoặc thứ tự so sánh |
| Một kết quả `false` chứng minh không ai thêm phần tử ngay sau đó | Truy vấn không khóa trạng thái cho những thao tác tiếp theo |
| `size()` của mọi collection là O(1) | Một số cấu trúc phải duyệt để đếm |

Khi một phép truy vấn cho kết quả bất ngờ, kiểm tra lần lượt: collection thực sự đang chứa gì, kiểu của đối tượng truy vấn, quy tắc `equals`/`hashCode` hoặc comparator, chính sách `null`, rồi mới xét việc có luồng khác đang sửa dữ liệu hay không.

## 10. Tự luyện

1. Một `ArrayList<String>` chứa `"A"`, `"A"`, `null`. `size()`, `isEmpty()` và `contains("A")` trả gì?  
   **Đáp án:** 3, `false`, `true`.

2. Xóa đúng một lần xuất hiện của `"A"` khỏi danh sách đó. `contains("A")` còn đúng không?  
   **Đáp án:** còn, vì vẫn có một phần tử `"A"`.

3. Một `HashMap<String, String>` có khóa `"x"` ánh xạ đến `null`. Map có rỗng không?  
   **Đáp án:** không. Map có một ánh xạ; `containsKey("x")` là `true`.

4. Vì sao `List.of(1, 2).contains(1L)` trả `false`?  
   **Đáp án:** phần tử là `Integer`, còn truy vấn là `Long`; cùng giá trị số không làm chúng bằng nhau theo `equals`.

5. Vì sao không nên dùng `queue.size() < limit` rồi `offer` để bảo đảm sức chứa trong hệ thống nhiều luồng?  
   **Đáp án:** việc kiểm tra và thêm là hai thao tác tách biệt; trạng thái có thể đổi giữa chúng. Cần cấu trúc hoặc cơ chế đáp ứng giới hạn một cách nguyên tử.

Ba phương thức này giúp diễn đạt ba ý định riêng: đếm, kiểm tra rỗng và kiểm tra thành viên. Mỗi lần dùng, hãy xác định chính xác câu hỏi cần trả lời và phạm vi mà kết quả còn có ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-003">add, remove, clear và quy tắc thao tác tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

# add, remove, clear và quy tắc thao tác tùy chọn

## 1. Hiểu “có phương thức” và “hỗ trợ thao tác”

Một collection là đối tượng quản lý một nhóm phần tử. Khi làm việc với nó, ta thường muốn thêm phần tử, xóa một phần tử hoặc làm rỗng toàn bộ nhóm.

Ba phương thức chung của `Collection<E>` là:

```java
boolean add(E element);
boolean remove(Object element);
void clear();
```

Đây là các chữ ký API để đọc hợp đồng, không phải chương trình chạy độc lập.

Điểm quan trọng là: **một phương thức tồn tại trên giao diện không có nghĩa mọi đối tượng cài đặt giao diện đều hỗ trợ thay đổi tương ứng**.

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Collection<String> editable = new ArrayList<>();
Collection<String> readOnly = List.of("A", "B");

editable.add("C"); // Thêm được.
// readOnly.add("C"); // Biên dịch được, nhưng khi chạy ném UnsupportedOperationException.
```

Hai biến có cùng kiểu khai báo `Collection<String>`. Khác biệt nằm ở đối tượng: đối tượng thứ nhất cho phép thêm; đối tượng thứ hai không cho sửa.

“Thao tác tùy chọn” (optional operation) là thuật ngữ trong hợp đồng Collections Framework. Nó cho phép một lớp cài đặt không hỗ trợ một thao tác thay đổi và báo bằng `UnsupportedOperationException`. Nó không có nghĩa là phương thức được phép biến mất, hoặc người dùng có thể bỏ qua kết quả tùy ý.

Bài này tập trung vào hợp đồng thêm/xóa, các biến thể dễ nhầm và cách sử dụng an toàn trên Java 17. Cần biết biến tham chiếu, generics cơ bản, `List` và `Set`.

Tham khảo nền tảng: [Collection API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

## 2. Mục đích và kết quả của ba thao tác

| Phương thức | Ý định | Kết quả khi hoàn tất bình thường |
| --- | --- | --- |
| `add(e)` | Đưa `e` vào collection theo hợp đồng của nó | `true` nếu collection thay đổi; `false` trong trường hợp phần tử trùng bị từ chối bởi cấu trúc không giữ trùng |
| `remove(o)` | Xóa một lần xuất hiện phù hợp với `o` | `true` nếu xóa được; `false` nếu không có phần tử phù hợp |
| `clear()` | Xóa toàn bộ phần tử | Không trả giá trị; collection rỗng sau khi thao tác thành công |

“Hoàn tất bình thường” nghĩa là lời gọi không ném ngoại lệ. Không suy ra kết quả từ bảng nếu thao tác bị từ chối hoặc đầu vào không hợp lệ.

Đối với một đối tượng mà chương trình được phép thay đổi, giá trị trả về giúp diễn đạt điều đã xảy ra. Ví dụ, thêm vào tập đã có phần tử không làm thay đổi tập; đây không phải lỗi cần ném ngoại lệ.

Bài hướng dẫn [The Collection Interface](https://docs.oracle.com/javase/tutorial/collections/interfaces/collection.html) giải thích cùng ý tưởng: phương thức chung cần dùng được cho nhiều dạng collection, dù cách giữ phần tử khác nhau.

## 3. add(E): thêm phần tử theo hợp đồng của cấu trúc

### 3.1. Danh sách thường giữ cả phần tử trùng

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> names = new ArrayList<>();

boolean first = names.add("An");
boolean second = names.add("An");

System.out.println(first);  // true
System.out.println(second); // true
System.out.println(names);  // [An, An]
```

Mỗi lần `add` thêm một phần tử cuối danh sách. Hai lần thêm cùng chuỗi vẫn tạo hai vị trí, nên cả hai lần đều làm thay đổi danh sách.

Ở đây `true` không có nghĩa “trước đó chưa chứa giá trị này”. Nó có nghĩa collection đã thay đổi do lời gọi.

Hợp đồng thêm cuối của `List.add(E)` được mô tả trong [List API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#add(E)).

### 3.2. Tập không tạo thêm bản trùng

```java
// Đặt trong một phương thức; cần import java.util.*.
Set<String> codes = new LinkedHashSet<>();

boolean first = codes.add("A");
boolean second = codes.add("A");

System.out.println(first);  // true
System.out.println(second); // false
System.out.println(codes);  // [A]
```

Lần thứ hai không đổi tập. `false` là kết quả hợp lệ, không phải dấu hiệu lỗi hệ thống.

Có thể dùng trực tiếp kết quả khi nghiệp vụ cần biết phần tử vừa được thêm:

```java
// Đặt trong một phương thức; cần import java.util.*.
Set<String> registered = new LinkedHashSet<>();
if (registered.add("ID-01")) {
    System.out.println("New registration");
}
```

Ví dụ một luồng này tránh phải viết riêng `contains` rồi `add`. Tuy nhiên, nó không tự làm `LinkedHashSet` an toàn nhiều luồng.

Với đối tượng do ứng dụng định nghĩa, quy tắc bằng nhau phải đúng. Các tập dựa trên bảng băm còn dựa vào hợp đồng `equals/hashCode`. Tập dựa trên phép so sánh có thêm yêu cầu nhất quán giữa phép so sánh và `equals`. Đây là điều cần xét khi “trùng” khác với việc hai tham chiếu có cùng địa chỉ. [Set API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 3.3. add không dùng false để biểu diễn mọi lý do thất bại

Không được hiểu `false` là “bất cứ trường hợp nào thêm không được”. Một collection từ chối thêm vì lý do khác phần tử đã có phải báo lỗi phù hợp theo hợp đồng.

Ví dụ:

- Đối tượng không hỗ trợ thêm: `UnsupportedOperationException`.
- Đối tượng không nhận `null`: có thể ném `NullPointerException` khi thêm `null`.
- Có giới hạn kiểu hoặc giá trị: có thể ném `ClassCastException` hoặc `IllegalArgumentException`.
- Không thể thêm lúc này do giới hạn dung lượng: có thể ném `IllegalStateException`.

Với hàng đợi có giới hạn dung lượng, `Queue.offer` thường là API diễn đạt việc không còn chỗ bằng giá trị trả về; `Queue.add` dùng ngoại lệ cho tình huống này. Đừng chuyển cách hiểu của `offer` sang `add`. [Queue API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)

### 3.4. List.add(index, element) là một phương thức khác

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> values = new ArrayList<>(List.of("A", "C"));
values.add(1, "B");
System.out.println(values); // [A, B, C]
```

`List.add(int, E)` trả `void`, không trả `boolean`. Nó chèn ở vị trí được chỉ định, không thay thế phần tử tại đó. Chỉ số hợp lệ cho chèn là từ `0` đến `size()`, bao gồm `size()` để chèn cuối.

Để thay phần tử mà không thay kích thước danh sách, thao tác là `set(index, element)`. Khả năng hỗ trợ `set` không suy ra khả năng hỗ trợ `add`.

## 4. remove(Object): xóa theo giá trị, một lần xuất hiện

### 4.1. Kết quả true/false và phần tử trùng

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> values =
        new ArrayList<>(List.of("a", "b", "a"));

boolean removed = values.remove("a");
boolean missing = values.remove("missing");

System.out.println(removed); // true
System.out.println(missing); // false
System.out.println(values);  // [b, a]
```

`remove("a")` chỉ xóa **một** lần xuất hiện. Với `List`, đó là lần đầu tiên theo thứ tự danh sách. Nó không xóa tất cả các phần tử bằng `"a"`.

Không tìm thấy phần tử hợp lệ để xóa là trường hợp bình thường đối với một collection hỗ trợ thao tác này. Không cần bắt ngoại lệ chỉ để xử lý “không có phần tử”.

Nếu nghiệp vụ muốn xóa tất cả lần xuất hiện hoặc lọc theo điều kiện, hãy chọn thao tác phù hợp như `removeAll` hoặc `removeIf`, đồng thời kiểm tra hợp đồng sửa đổi của đối tượng. Không gọi `remove` một lần rồi giả định không còn giá trị đó.

### 4.2. Đối số là Object vì đây là thao tác tìm/xóa

Chữ ký là `remove(Object)`, không phải `remove(E)`. Ví dụ sau có thể biên dịch:

```java
// Đặt trong một phương thức; cần import java.util.*.
Collection<String> words = new ArrayList<>(List.of("one"));
System.out.println(words.remove(123)); // false với ArrayList trong ví dụ này.
```

Điều đó không cho phép đưa số nguyên vào `Collection<String>`. Thêm và tìm/xóa có chữ ký khác nhau.

Cũng không nên suy rộng rằng mọi collection đều trả `false` cho một đối số không phù hợp. Một số cấu trúc có thể ném ngoại lệ theo hợp đồng khi đối số sai kiểu hoặc là `null` không được chấp nhận. Hãy xem tài liệu của lớp và thao tác cụ thể.

Đối với dữ liệu có `null`, cần phân biệt:

- Xóa phần tử `null` trong cấu trúc cho phép giữ `null`.
- Gọi `remove(null)` trên cấu trúc cấm `null`.
- Gọi `remove` trên collection vốn không cho sửa.

Đây là các trục hợp đồng khác nhau, không phải cùng một loại lỗi.

## 5. remove(int) và remove(Object) trong List<Integer>

Đây là một trong những lỗi khó nhận ra vì mã vẫn biên dịch.

```java
// Đặt trong một phương thức; cần import java.util.*.
List<Integer> numbers =
        new ArrayList<>(List.of(10, 20, 30));

Integer removedAtIndex = numbers.remove(1);
System.out.println(removedAtIndex); // 20
System.out.println(numbers);       // [10, 30]

boolean removedValue = numbers.remove(Integer.valueOf(10));
System.out.println(removedValue);  // true
System.out.println(numbers);       // [30]
```

Có hai overload:

| Lời gọi | Phương thức được chọn | Ý nghĩa | Kiểu trả về |
| --- | --- | --- | --- |
| `numbers.remove(1)` khi `numbers` là `List<Integer>` | `remove(int index)` | Xóa ở chỉ số `1` | `Integer` |
| `numbers.remove(Integer.valueOf(10))` | `remove(Object value)` | Xóa giá trị bằng `10` | `boolean` |

Chỉ số của `remove(int)` phải nằm từ `0` đến `size() - 1`. Chỉ số ngoài phạm vi có thể gây `IndexOutOfBoundsException`; nó không trả `false` để nói “không có chỉ số này”.

Kiểu khai báo cũng ảnh hưởng tập phương thức có thể chọn:

```java
// Đặt trong một phương thức; cần import java.util.*.
Collection<Integer> values =
        new ArrayList<>(List.of(1, 2, 3));

boolean changed = values.remove(1);
System.out.println(changed); // true
System.out.println(values);  // [2, 3]
```

`Collection` không có `remove(int index)`. Số `1` được boxing thành `Integer` để truyền cho `remove(Object)`. Đối tượng thực tế vẫn là `ArrayList`; nó không làm trình biên dịch tự thêm overload của `List` vào biến khai báo bằng `Collection`.

Một tên `remove` khác nữa là `Queue.remove()` không có đối số: lấy và xóa đầu hàng đợi, có thể ném `NoSuchElementException` khi rỗng. Đó không phải `Collection.remove(Object)`.

Các thao tác theo vị trí của danh sách được giới thiệu trong [The List Interface](https://docs.oracle.com/javase/tutorial/collections/interfaces/list.html).

## 6. clear(): làm rỗng đối tượng đang có

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> tasks =
        new ArrayList<>(List.of("code", "test"));
tasks.clear();

System.out.println(tasks.isEmpty()); // true
System.out.println(tasks.size());    // 0
```

`clear` trả `void`. Vì vậy, `boolean changed = tasks.clear();` không biên dịch.

### 6.1. clear khác với gán biến sang một collection mới

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> original = new ArrayList<>(List.of("A"));
List<String> alias = original;

original.clear();
System.out.println(alias); // []
```

Hai biến tham chiếu cùng đối tượng. `clear` sửa đối tượng đó nên thay đổi được quan sát qua `alias`.

Ngược lại:

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> original = new ArrayList<>(List.of("A"));
List<String> alias = original;

original = new ArrayList<>();
System.out.println(original); // []
System.out.println(alias);    // [A]
```

Phép gán chỉ chuyển biến `original` sang đối tượng mới. Nó không làm rỗng đối tượng cũ.

### 6.2. clear không xóa sâu hoặc xóa an toàn dữ liệu nhạy cảm

Collection bỏ các phần tử khỏi cấu trúc quản lý của nó. Điều này không đồng nghĩa với:

- Gọi phương thức hủy trên từng phần tử.
- Đóng tài nguyên như tệp hoặc kết nối mà phần tử đang giữ.
- Xóa trạng thái của phần tử đang được nơi khác tham chiếu.
- Bảo đảm dữ liệu bị ghi đè khỏi RAM.
- Bảo đảm bộ nhớ được trả lại ngay cho hệ điều hành.
- Bảo đảm dung lượng lưu trữ nội bộ giảm theo một chính sách chung.

Với tài nguyên cần đóng, phải quản lý vòng đời tài nguyên riêng. Với dữ liệu nhạy cảm, không dùng `clear` như bằng chứng đã xóa an toàn.

## 7. Bốn nhóm đối tượng và mức hỗ trợ khác nhau

### 7.1. Collection sửa được

Một `ArrayList` thông thường hỗ trợ thêm, xóa, thay phần tử và làm rỗng. Điều đó không có nghĩa mọi thao tác đều miễn lỗi: chỉ số vẫn phải đúng, đầu vào vẫn phải đáp ứng các điều kiện cần thiết. [ArrayList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

### 7.2. Danh sách cố định kích thước

```java
// Đặt trong một phương thức; cần import java.util.*.
String[] array = {"red", "blue"};
List<String> fixed = Arrays.asList(array);

fixed.set(0, "green");
System.out.println(Arrays.toString(array)); // [green, blue]
// fixed.add("yellow"); // UnsupportedOperationException.
// fixed.remove("blue"); // UnsupportedOperationException.
// fixed.clear(); // UnsupportedOperationException với dữ liệu không rỗng này.
```

`Arrays.asList` trả danh sách được chống lưng bởi mảng. Nó cho thay phần tử hiện có nhưng không hỗ trợ thay đổi kích thước.

“Cố định kích thước” không đồng nghĩa “không thể thay đổi nội dung”. Sửa phần tử qua danh sách được phản ánh vào mảng và ngược lại. [Arrays.asList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...))

### 7.3. Đối tượng không cho sửa

`List.of(...)` tạo danh sách không cho sửa và không nhận `null`. Các thao tác thay đổi như thêm, xóa, thay thế hoặc làm rỗng bị từ chối theo hợp đồng factory.

```java
// Đặt trong một phương thức; cần import java.util.List.
List<String> values = List.of("x", "y");
// values.add("z");    // UnsupportedOperationException.
// values.remove("x"); // UnsupportedOperationException.
// values.clear();     // UnsupportedOperationException.
```

Không cho sửa collection không tự làm từng phần tử bất biến. Nếu phần tử là đối tượng có trạng thái thay đổi, code còn giữ tham chiếu đến phần tử vẫn có thể sửa trạng thái ấy.

### 7.4. View không cho sửa

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> backing = new ArrayList<>(List.of("A"));
List<String> view = Collections.unmodifiableList(backing);

backing.clear();
System.out.println(view); // []
```

`view.clear()` bị từ chối. Nhưng `backing.clear()` vẫn thay đổi dữ liệu mà view đang quan sát. Không được gọi view này là một bản sao độc lập.

Ngược lại, `List.copyOf(backing)` tạo kết quả không phản ánh thay đổi cấu trúc sau đó của collection nguồn; nó vẫn là bản sao nông về các tham chiếu phần tử và không hứa tạo một đối tượng mới trong mọi trường hợp.

Quy tắc view và thao tác được ủy quyền được mô tả tại [Collections.unmodifiableCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableCollection(java.util.Collection)) và các wrapper cùng họ.

## 8. Quy tắc thao tác tùy chọn

### 8.1. Hỗ trợ được xác định theo thao tác, không phải một cờ chung

Một đối tượng có thể cho `set` nhưng không cho `add`. Một view có thể cho `remove` và `clear` để thay đổi cấu trúc nguồn, nhưng không cho `add` vì thiếu thông tin cần thiết để thêm.

Vì vậy, đừng suy luận:

- “Đã `set` được thì chắc `add` được.”
- “`remove` thất bại nên mọi thao tác đều không được hỗ trợ.”
- “Đối tượng là `List` nên chắc chắn xóa được.”
- “Kích thước hiện tại bằng 0 thì gọi `clear` ở đâu cũng được.”

Khi viết lớp collection tùy chỉnh, các lớp khung như [AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html) và [AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html) cung cấp cách cài đặt mặc định. Muốn hỗ trợ thay đổi, người cài đặt phải thực hiện các điểm mở rộng tương ứng; chỉ kế thừa lớp khung không bảo đảm mọi thao tác sửa đều hoạt động.

### 8.2. Thao tác không làm thay đổi vẫn có thể bị từ chối

Hợp đồng tổng quát cho phép một thao tác không được hỗ trợ ném `UnsupportedOperationException` ngay cả khi lời gọi cụ thể có vẻ không gây thay đổi. Một số lớp cài đặt có thể kết thúc bình thường nếu không có gì để làm; các factory/wrapper cụ thể có thể đưa ra bảo đảm mạnh hơn.

Ví dụ cần xem hợp đồng cụ thể:

- `clear()` trên collection vốn đã rỗng.
- `remove(x)` khi không có `x`.
- `addAll` với nguồn rỗng.

Không lấy một lần `clear` thành công trên collection rỗng để kết luận nó hỗ trợ xóa phần tử khi có dữ liệu. Không dùng “thử thao tác vô hại” làm cơ chế nhận biết collection sửa được.

### 8.3. Không có phép kiểm tra kiểu chung cho mọi collection sửa được

`instanceof List` hoặc `instanceof Collection` chỉ kiểm tra quan hệ kiểu. Nó không trả lời thao tác `add` có được hỗ trợ không.

Kiểm tra `instanceof ArrayList` cũng không phải thiết kế tổng quát cho API chấp nhận nhiều loại dữ liệu. Thay vào đó:

1. Nêu rõ tiền điều kiện nếu phương thức sửa đối số.
2. Chỉ truyền đối tượng đáp ứng tiền điều kiện đó.
3. Hoặc tạo collection riêng khi phương thức không nên sửa nguồn.

Nếu cần một API chỉ đọc rõ ràng hơn, lựa chọn tham số và thiết kế quyền sở hữu phải thể hiện điều đó; giao diện `Collection` tự nó vẫn chứa các phương thức thay đổi.

## 9. Phân loại lỗi đúng để xử lý đúng

| Hiện tượng | Ý nghĩa cần xét | Không nên kết luận |
| --- | --- | --- |
| `add` vào `Set` trả `false` khi trùng | Không có thay đổi vì phần tử đã tồn tại | Hệ thống bị lỗi |
| `remove(Object)` trả `false` | Không xóa phần tử nào trong lời gọi hợp lệ | Collection chắc chắn không sửa được |
| `UnsupportedOperationException` | Thao tác bị từ chối bởi hợp đồng đối tượng | Đầu vào luôn sai |
| `IndexOutOfBoundsException` từ thao tác theo vị trí | Chỉ số không hợp lệ | Nên thử lại với cùng chỉ số |
| `NullPointerException` | Có thể do collection hoặc đầu vào `null`, tùy điểm gọi | Mọi collection đều cấm `null` |
| `ClassCastException` hoặc `IllegalArgumentException` | Kiểu hoặc giá trị không đáp ứng ràng buộc | Mọi collection có cùng ràng buộc |
| `IllegalStateException` khi thêm vào hàng đợi hạn chế | Không thể thêm trong trạng thái hiện tại | `add` phải trả `false` |

Trình tự các ngoại lệ khi một yêu cầu vi phạm nhiều điều kiện cùng lúc không phải bảo đảm chung. Không viết nghiệp vụ phụ thuộc vào việc “phải kiểm tra chỉ số trước, rồi mới kiểm tra tính sửa được” nếu tài liệu không quy định.

Cũng không bắt `RuntimeException` rồi bỏ qua toàn bộ. Làm như vậy có thể che lỗi hợp đồng, lỗi dữ liệu và lỗi lập trình.

## 10. Sửa trong lúc duyệt và ranh giới nguyên tử

Thêm/xóa cấu trúc trong vòng `for-each` bằng cách gọi trực tiếp collection có thể làm iterator mất hiệu lực. Với nhiều cài đặt như `ArrayList`, ta có thể gặp `ConcurrentModificationException`, nhưng không được dựa vào việc ngoại lệ này luôn xảy ra để bảo đảm tính đúng.

Nếu cần xóa phần tử hiện tại và iterator hỗ trợ, dùng `Iterator.remove` sau `next`:

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> values =
        new ArrayList<>(List.of("keep", "drop", "keep"));

Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    if (iterator.next().equals("drop")) {
        iterator.remove();
    }
}
System.out.println(values); // [keep, keep]
```

`Iterator.remove` cũng là thao tác tùy chọn. Nó chỉ được gọi một lần sau mỗi lần `next`; không phải mọi iterator đều hỗ trợ nó. [Iterator API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove())

Đối với một chuỗi gồm nhiều thao tác:

```java
// Ý tưởng minh họa; không có bảo đảm giao dịch chung.
target.clear();
target.add("replacement");
```

Không có hợp đồng chung rằng hai lời gọi là một giao dịch. Nếu lời gọi sau thất bại, đừng giả định phần tử đã xóa sẽ tự khôi phục.

Khi nhiều luồng cùng truy cập, việc một thao tác sửa được hỗ trợ cũng không có nghĩa đối tượng an toàn đồng thời. Cần thiết kế đồng bộ hoặc chọn cấu trúc concurrent phù hợp, không chỉ đổi kiểu khai báo.

## 11. Thiết kế phương thức khi cần thay đổi dữ liệu

### 11.1. Sửa trực tiếp: ghi rõ tác động và tiền điều kiện

```java
// Đoạn khai báo phương thức; cần import java.util.Collection.
// Sửa collection của người gọi; yêu cầu collection hỗ trợ clear.
static void reset(Collection<?> target) {
    target.clear();
}
```

Tên `reset` chưa đủ truyền đạt mọi tác động. Tài liệu cần nói:

- Phương thức sửa đối số, không trả bản sao.
- View hoặc tham chiếu khác có thể quan sát thay đổi.
- Collection phải hỗ trợ `clear`.
- Không được truyền `null` nếu phương thức không có xử lý riêng.

Không cần gọi `isEmpty` trước chỉ để né một ngoại lệ. Cách đó có thể làm API khi thì “thành công” khi thì thất bại theo dữ liệu đầu vào, trong khi yêu cầu sửa được vẫn chưa rõ.

### 11.2. Giữ nguồn: tạo bản sao sửa được

```java
// Đặt trong một phương thức; cần import java.util.*.
List<String> source = List.of("A", "B");
List<String> result = new ArrayList<>(source);

result.remove("A");
result.add("C");

System.out.println(source); // [A, B]
System.out.println(result); // [B, C]
```

Đây là lựa chọn tốt khi đầu vào chỉ cung cấp dữ liệu, không phải vật chứa mà phương thức có quyền sửa.

Tạo `ArrayList` cũng là một quyết định về hợp đồng: nó giữ thứ tự duyệt nguồn và cho phép phần tử trùng. Không dùng bản sao này nếu bài toán đòi giữ nguyên hành vi `Set` hoặc một cấu trúc có thứ tự theo comparator. Hãy chọn kiểu bản sao đúng yêu cầu.

### 11.3. Không dùng bắt ngoại lệ làm chiến lược sửa rồi sao chép mù

Tránh quy trình “thử một chuỗi thay đổi, nếu có `UnsupportedOperationException` thì sao chép và chạy lại” mà không biết trạng thái đã thay đổi đến đâu. Một bước trước có thể đã thành công.

Việc chọn sửa tại chỗ hay tạo kết quả mới nên là quyết định của hợp đồng phương thức. Bắt `UnsupportedOperationException` có thể phù hợp tại ranh giới hiển thị lỗi hoặc trong chương trình học để quan sát hành vi; nó không thay thế thiết kế quyền sở hữu.

## 12. Chương trình hoàn chỉnh

Lưu thành `OptionalOperationsDemo.java`. Chương trình minh họa các collection cụ thể nên kết quả có thể đối chiếu chính xác.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class OptionalOperationsDemo {
    private static void showUnsupported(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + ": completed");
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + ": UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        System.out.println("List add A: " + list.add("A"));
        System.out.println("List add A again: " + list.add("A"));
        list.add("B");
        System.out.println("List: " + list);

        Set<String> set = new LinkedHashSet<>();
        System.out.println("Set add A: " + set.add("A"));
        System.out.println("Set add A again: " + set.add("A"));
        System.out.println("Set: " + set);

        Collection<String> values =
                new ArrayList<>(List.of("a", "b", "a"));
        System.out.println("Remove a: " + values.remove("a"));
        System.out.println("After remove: " + values);
        System.out.println("Remove missing: " + values.remove("missing"));
        values.clear();
        System.out.println("After clear: " + values);

        List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
        System.out.println("remove(1): " + numbers.remove(1));
        System.out.println("Numbers: " + numbers);
        System.out.println("remove(Integer 10): "
                + numbers.remove(Integer.valueOf(10)));
        System.out.println("Numbers: " + numbers);

        Collection<Integer> numberCollection =
                new ArrayList<>(List.of(1, 2, 3));
        System.out.println("Collection remove(1): "
                + numberCollection.remove(1));
        System.out.println("Collection numbers: " + numberCollection);

        String[] array = {"red", "blue"};
        List<String> fixed = Arrays.asList(array);
        System.out.println("Fixed set old: " + fixed.set(0, "green"));
        System.out.println("Array: " + Arrays.toString(array));
        showUnsupported("Fixed add", () -> fixed.add("yellow"));
        showUnsupported("Fixed remove", () -> fixed.remove("blue"));
        showUnsupported("Fixed clear", fixed::clear);

        List<String> readOnly = List.of("x", "y");
        showUnsupported("Read-only add", () -> readOnly.add("z"));
        showUnsupported("Read-only remove", () -> readOnly.remove("x"));
        showUnsupported("Read-only clear", readOnly::clear);

        List<String> backing = new ArrayList<>(List.of("secret"));
        List<String> view = Collections.unmodifiableList(backing);
        List<String> copy = List.copyOf(backing);
        backing.clear();
        System.out.println("View after backing clear: " + view);
        System.out.println("Copy after backing clear: " + copy);

        List<String> editable = new ArrayList<>(readOnly);
        editable.add("z");
        editable.remove("x");
        System.out.println("Editable copy: " + editable);
        System.out.println("Original read-only: " + readOnly);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 OptionalOperationsDemo.java
java OptionalOperationsDemo
```

Cần JDK có `javac`. Mã đã được biên dịch bằng `--release 17` và chạy trên một JDK mới hơn; không sử dụng API chỉ có sau Java 17.

Stdout:

```text
List add A: true
List add A again: true
List: [A, A, B]
Set add A: true
Set add A again: false
Set: [A]
Remove a: true
After remove: [b, a]
Remove missing: false
After clear: []
remove(1): 20
Numbers: [10, 30]
remove(Integer 10): true
Numbers: [30]
Collection remove(1): true
Collection numbers: [2, 3]
Fixed set old: red
Array: [green, blue]
Fixed add: UnsupportedOperationException
Fixed remove: UnsupportedOperationException
Fixed clear: UnsupportedOperationException
Read-only add: UnsupportedOperationException
Read-only remove: UnsupportedOperationException
Read-only clear: UnsupportedOperationException
View after backing clear: []
Copy after backing clear: [secret]
Editable copy: [y, z]
Original read-only: [x, y]
```

### Đọc kết quả theo từng cơ chế

- Hai lần thêm `A` vào `ArrayList` đều thay đổi dữ liệu, còn lần hai trong `LinkedHashSet` trả `false`.
- Collection chứa `a, b, a` chỉ mất lần `a` đầu tiên. Xóa `missing` trả `false`.
- `clear` làm collection rỗng; không có giá trị trả về để in.
- `List<Integer>.remove(1)` xóa phần tử chỉ số `1` và trả `20`.
- `remove(Integer.valueOf(10))` xóa theo giá trị và trả `true`.
- Qua biến `Collection<Integer>`, `remove(1)` là xóa giá trị `1`.
- `Arrays.asList` cho `set`, thay đổi mảng nguồn, nhưng từ chối các thao tác thay kích thước được thực hiện.
- `List.of` từ chối các thao tác thay đổi trong ví dụ.
- View không cho sửa vẫn thấy nguồn bị `clear`. Bản `List.copyOf` không phản ánh thay đổi cấu trúc ấy.
- Bản sao `ArrayList` được sửa độc lập về cấu trúc với danh sách nguồn không cho sửa.

Hàm `showUnsupported` chỉ phục vụ minh họa: nó in ngoại lệ dự kiến thay vì làm chương trình dừng. Nó không bắt mọi loại ngoại lệ và không đưa ra chính sách xử lý lỗi sản xuất.

## 13. Các lỗi cần nhận ra khi đọc mã

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Coi `add` trả `true` nghĩa là giá trị chưa tồn tại | Diễn giải sai đối với `List` | Đọc kết quả theo “collection có thay đổi không” |
| Cho rằng `remove` xóa hết phần tử trùng | Dữ liệu trùng còn lại | Chọn thao tác xóa toàn bộ hoặc điều kiện phù hợp |
| Dùng `remove(1)` để xóa số `1` trong `List<Integer>` | Xóa nhầm phần tử ở chỉ số `1` | Truyền `Integer.valueOf(1)` khi muốn xóa giá trị |
| Coi `clear` trả `boolean` | Không biên dịch | Gọi `clear` như một thao tác `void` |
| Coi `Arrays.asList` là `ArrayList` sửa kích thước | Gặp `UnsupportedOperationException` | Tạo `new ArrayList<>(...)` nếu cần thay kích thước |
| Coi view không cho sửa là bản sao | Thấy dữ liệu nguồn thay đổi ngoài dự kiến | Chọn copy hoặc view có chủ đích |
| Thử `clear` trên collection rỗng để nhận biết tính sửa được | Kết luận không có bảo đảm | Dựa vào hợp đồng cụ thể |
| Bắt mọi ngoại lệ rồi tiếp tục | Che dữ liệu sai hoặc thao tác chưa hoàn tất | Phân loại lỗi và giữ trạng thái rõ ràng |
| Sửa collection trực tiếp trong `for-each` | Iterator có thể mất hiệu lực | Dùng cơ chế xóa được hỗ trợ khi duyệt |
| Dùng `clear` như xóa an toàn bí mật | Giả định bảo mật không đúng | Quản lý dữ liệu nhạy cảm bằng cơ chế riêng |

## 14. Tự kiểm tra và thực hành

### Câu hỏi

1. Vì sao hai lời gọi `add("A")` vào `List` có thể đều trả `true`?
2. `Set.add` trả `false` khi trùng có phải lỗi không?
3. `remove(Object)` xóa bao nhiêu lần xuất hiện?
4. `List<Integer>.remove(1)` khác `Collection<Integer>.remove(1)` thế nào?
5. Vì sao `Arrays.asList` cho `set` nhưng không cho thêm phần tử?
6. Gọi `clear` thành công trên dữ liệu rỗng có chứng minh đối tượng xóa được khi có dữ liệu không?
7. Tại sao biến `Collection` vẫn có thể trỏ tới một đối tượng không cho sửa?
8. View không cho sửa có tiếp tục quan sát thay đổi từ collection nguồn không?
9. `clear` có đóng tài nguyên hoặc xóa an toàn dữ liệu khỏi bộ nhớ không?
10. Một phương thức sửa collection nhận vào cần công bố điều gì?

### Bài tập

- Chạy chương trình đầy đủ và giải thích mỗi dòng `true/false` bằng trạng thái trước–sau.
- Thêm một lần `remove("A")` vào danh sách chứa `A, A, B`; kiểm chứng vẫn còn một `A`.
- Đổi ví dụ số nguyên để xóa **giá trị** `1` trong danh sách `10, 20, 1`, không xóa theo chỉ số.
- Tạo danh sách mới bằng `new ArrayList<>(Arrays.asList(...))` và kiểm chứng thêm/xóa được mà không đổi kích thước mảng gốc.
- Viết một phương thức tạo bản sao, xóa phần tử không mong muốn rồi trả kết quả; kiểm tra nguồn không bị sửa.
- Đối chiếu hợp đồng của ba nguồn `new ArrayList<>()`, `Arrays.asList(...)` và `List.of(...)`, không dùng kết quả một thao tác rỗng làm phép thử tính sửa được.

### Gợi ý đối chiếu

1. Mỗi lần thêm tạo một vị trí mới nên danh sách thay đổi.
2. Không; tập đã chứa phần tử và không tạo bản trùng.
3. Một lần; `List` xóa lần phù hợp đầu tiên.
4. Với `List` chọn overload theo chỉ số; với `Collection` chỉ có overload theo đối tượng.
5. Nó là view của mảng có độ dài cố định; thay nội dung phần tử không đổi độ dài.
6. Không; quy tắc thao tác không gây thay đổi không cung cấp bảo đảm đó.
7. Kiểu khai báo cho biết API, còn hợp đồng đối tượng xác định mức hỗ trợ.
8. Có, nếu đó là view sống của nguồn như wrapper được minh họa.
9. Không; đó là các trách nhiệm riêng.
10. Tác động lên đối số, thao tác phải được hỗ trợ, giới hạn đầu vào, quan hệ với view/tham chiếu khác và hành vi lỗi cần biết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-004">addAll, containsAll, removeAll và retainAll</a>

<details>
<summary>Xem chi tiết</summary>

# addAll, containsAll, removeAll và retainAll

Khi làm việc với nhiều phần tử, ta thường cần gộp dữ liệu, kiểm tra một nhóm yêu cầu, loại bỏ các giá trị bị chặn hoặc chỉ giữ những giá trị được chấp nhận. Java cung cấp bốn phương thức thuộc hợp đồng chung của `Collection` cho các tình huống đó: `addAll`, `containsAll`, `removeAll` và `retainAll`.

Điểm quan trọng không nằm ở chữ “All”, mà ở ba câu hỏi: **collection nào là đối tượng nhận lời gọi, collection đối số đóng vai trò gì, và kết quả có làm thay đổi dữ liệu hay không?** Nếu chưa phân biệt được ba điều này, chương trình có thể chạy mà vẫn xóa sai dữ liệu hoặc kiểm tra sai yêu cầu.

Bài này dùng Java 17, tập trung vào hợp đồng và cách áp dụng bốn phương thức. Chương trình đầy đủ có tên tệp, lệnh chạy và kết quả ở phần minh họa. Các đoạn Java ngắn khác là đoạn trích minh họa, không phải chương trình độc lập.

## 1. Mô hình chung: đối tượng nhận và collection đối số

Xét lời gọi:

```java
a.removeAll(b);
```

- `a` là collection nhận lời gọi, cũng là nơi có thể bị thay đổi.
- `b` cung cấp tiêu chí phần tử cần loại.
- Phương thức không tạo một collection kết quả để trả về.
- Nếu lời gọi kết thúc bình thường, `boolean` trả về cho biết `a` có bị thay đổi hay không.

Với `containsAll`, vai trò khác đi: `a` được kiểm tra có chứa đầy đủ các giá trị xuất hiện trong `b` không; phương thức này không phải thao tác sửa cấu trúc collection.

Bảng định hướng:

| Lời gọi | Cách đọc | Vai trò của đối số | Có sửa đối tượng nhận? |
| --- | --- | --- | --- |
| `a.addAll(b)` | Thêm các phần tử của `b` vào `a` | Nguồn phần tử | Có thể |
| `a.containsAll(b)` | `a` có chứa mọi giá trị mà `b` yêu cầu không? | Nhóm cần kiểm tra | Không theo hợp đồng thao tác |
| `a.removeAll(b)` | Xóa khỏi `a` mọi phần tử thuộc `b` | Nhóm bị loại | Có thể |
| `a.retainAll(b)` | Chỉ giữ trong `a` các phần tử thuộc `b` | Nhóm được giữ | Có thể |

Đổi vị trí `a` và `b` thường đổi cả câu hỏi lẫn dữ liệu bị sửa. Chẳng hạn, “quyền đang có chứa các quyền bắt buộc” phải viết theo chiều `granted.containsAll(required)`.

Các phương thức này được khai báo trong [Collection của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html). `List`, `Set`, `Queue` và các lớp liên quan nhận được hợp đồng đó qua hệ thống interface. `Map` không phải `Collection`; muốn thao tác trên khóa, giá trị hoặc entry của map phải xác định rõ view tương ứng.

## 2. Đọc chữ ký trước khi đọc ví dụ

Bốn chữ ký là:

```java
boolean addAll(Collection<? extends E> c);
boolean containsAll(Collection<?> c);
boolean removeAll(Collection<?> c);
boolean retainAll(Collection<?> c);
```

Khối trên chỉ liệt kê khai báo phương thức, không phải mã để đặt trực tiếp vào `main`.

### 2.1. Vì sao addAll có ? extends E?

`addAll` đưa các phần tử từ nguồn vào collection nhận. Vì vậy, nguồn phải cung cấp các phần tử phù hợp với kiểu `E`.

Đoạn minh họa:

```java
List<Number> numbers = new ArrayList<>();
List<Integer> integers = List.of(1, 2, 3);

numbers.addAll(integers);
```

`Integer` là kiểu con của `Number`, nên các phần tử trên được chấp nhận. Điều này không có nghĩa `List<Integer>` là kiểu con của `List<Number>`; wildcard của riêng tham số phương thức cho phép nhận nguồn phù hợp.

### 2.2. Vì sao ba phương thức còn lại nhận Collection<?>?

Chúng kiểm tra quan hệ có chứa, hoặc lọc những phần tử đã có trong collection nhận. Chúng không cần chép các phần tử từ đối số vào nơi nhận.

Chẳng hạn, biểu thức sau có thể biên dịch:

```java
List<Integer> numbers = new ArrayList<>(List.of(1, 2));
boolean changed = numbers.removeAll(List.of("x"));
```

Với `ArrayList` trong ví dụ này, không có phần tử nào khớp, nên danh sách giữ nguyên và `changed` là `false`.

Tuy nhiên, không được tổng quát thành “truy vấn bằng kiểu không phù hợp luôn trả `false`”. Một số collection hạn chế kiểu phần tử hoặc cách so sánh và có thể ném ngoại lệ theo hợp đồng của chúng. Chữ ký cho phép biên dịch chưa chứng minh mọi collection đều chấp nhận mọi giá trị khi chạy.

## 3. addAll: thêm nhiều phần tử vào collection nhận

### 3.1. Thêm phần tử, không thêm nguyên collection như một phần tử

Đoạn minh họa:

```java
List<String> a = new ArrayList<>(List.of("A", "B"));
List<String> b = List.of("B", "C");

boolean changed = a.addAll(b);
```

Kết quả:

```text
a = [A, B, B, C]
b = [B, C]
changed = true
```

Ở đây `a` là `ArrayList`, nên hai phần tử của `b` được nối vào cuối theo thứ tự của nguồn. `b` không bị lấy mất phần tử.

Nếu muốn chứa cả một danh sách như một phần tử, cần kiểu lồng như `List<List<String>>` và dùng `add(b)`. Đó là mô hình dữ liệu khác.

### 3.2. List và Set xử lý phần tử lặp khác nhau

Với `List`, thêm lại giá trị đã có vẫn có thể làm danh sách thay đổi, vì danh sách cho phép nhiều lần xuất hiện.

Với tập hợp:

```java
Set<String> codes = new LinkedHashSet<>(List.of("A", "B"));
boolean first = codes.addAll(List.of("B", "C", "C"));
boolean second = codes.addAll(List.of("A", "B"));
```

Kết quả:

```text
codes = [A, B, C]
first = true
second = false
```

Lần đầu thêm được giá trị mới `"C"`; các lần xuất hiện còn lại không tạo phần tử trùng trong tập hợp. Lần thứ hai không có giá trị mới nên không làm thay đổi tập.

Không suy ra rằng mọi `Set` đều in theo thứ tự thêm. Ví dụ chọn `LinkedHashSet` để thứ tự quan sát là xác định. Một `HashSet` không đưa ra cùng bảo đảm thứ tự duyệt.

### 3.3. Boolean trả về diễn đạt sự thay đổi

`false` không có nghĩa thao tác thất bại. Nó có thể cho biết mọi phần tử từ nguồn đã có trong một tập hợp.

Nếu thao tác không được hỗ trợ, phần tử không hợp lệ hoặc không thể thêm do hạn chế khác, phương thức có thể ném ngoại lệ. Không dùng `false` làm nhánh xử lý chung cho mọi lỗi thêm dữ liệu.

Với một `ArrayList` có thể sửa và nguồn độc lập, không rỗng, lời gọi thêm các phần tử sẽ làm kích thước tăng. Với một tập hợp, nguồn không rỗng vẫn có thể không tạo thay đổi.

### 3.4. Thứ tự phụ thuộc kiểu collection

Hợp đồng `Collection` không biến mọi cấu trúc thành danh sách. `List.addAll` có quy tắc nối phần tử; một cấu trúc có sắp xếp hoặc có ưu tiên tiếp tục tổ chức dữ liệu theo quy tắc riêng.

Đừng dùng `addAll` rồi mặc nhiên suy ra thứ tự duyệt toàn bộ collection là thứ tự của nguồn. Cần đọc hợp đồng của interface và lớp đang dùng.

## 4. containsAll: kiểm tra sự hiện diện của mọi giá trị cần tìm

### 4.1. Đây là câu hỏi theo chiều

Đoạn minh họa:

```java
Set<String> granted = Set.of("READ", "WRITE");
Set<String> required = Set.of("READ");

boolean allowed = granted.containsAll(required);
```

`allowed` là `true`. Nếu đổi chiều, `required.containsAll(granted)` là `false` vì `required` không chứa `"WRITE"`.

Có thể dùng mô hình suy luận sau:

```text
với mỗi giá trị x trong collection đối số:
    nếu collection nhận không chứa x:
        trả về false
trả về true
```

Đây là mô hình để hiểu ý nghĩa, không phải cam kết mọi lớp đều chạy đúng một vòng lặp cụ thể.

### 4.2. Không kiểm tra số lần xuất hiện

Đoạn minh họa:

```java
List<String> available = List.of("A");
boolean result = available.containsAll(List.of("A", "A", "A"));
```

`result` là `true`: từng yêu cầu kiểm tra giá trị `"A"` đều thấy giá trị đó tồn tại.

Không có cơ chế “dùng hết một lần xuất hiện rồi lấy lần tiếp theo”. Vì vậy, không dùng `containsAll` để chứng minh kho có đủ ba đơn vị hàng chỉ vì giỏ yêu cầu chứa cùng một mã ba lần.

Khi số lượng là điều kiện, cần đếm theo giá trị, chẳng hạn biểu diễn bằng `Map<String, Integer>`, rồi so sánh số lượng cần với số lượng hiện có.

### 4.3. Không kiểm tra thứ tự, đoạn liên tiếp hoặc bình đẳng danh sách

Biểu thức:

```java
List.of("A", "B").containsAll(List.of("B", "A"))
```

trả `true`. Nó không yêu cầu các phần tử xuất hiện đúng thứ tự trong đối số.

Thậm chí hai danh sách có cùng kích thước và cùng chứa mọi giá trị của nhau vẫn chưa chắc có cùng số lần lặp:

```text
a = [A, A, B]
b = [A, B, B]
```

Cả hai phép `containsAll` theo hai chiều đều là `true`, nhưng hai danh sách không bằng nhau.

Chọn phép kiểm tra theo nhu cầu:

| Nhu cầu | Hướng xử lý |
| --- | --- |
| Mọi giá trị bắt buộc đều xuất hiện | `containsAll` |
| Hai danh sách cùng phần tử ở từng vị trí | `List.equals` |
| Cùng giá trị và cùng số lần lặp, không xét thứ tự | So sánh bảng tần suất |
| Một nhóm là đoạn liên tiếp trong danh sách | Kiểm tra đoạn hoặc thuật toán tìm chuỗi con phù hợp |
| Có ít nhất một giá trị chung | Kiểm tra giao không rỗng, không dùng containsAll thay thế |

### 4.4. Collection đối số rỗng

Một collection chứa mọi phần tử của collection rỗng, vì không có giá trị nào cần kiểm tra. Do đó `a.containsAll(empty)` là `true` với đối số rỗng hợp lệ, kể cả khi `a` rỗng.

Trong kiểm tra nghiệp vụ, điều đó có thể đúng hoặc có thể làm lộ lỗi dữ liệu đầu vào. Nếu danh sách quyền bắt buộc không được phép rỗng theo quy trình, phải xác minh điều kiện đó riêng. `containsAll` không tự biết “không có yêu cầu” có phải là cấu hình hợp lệ của ứng dụng hay không.

## 5. removeAll: loại mọi lần xuất hiện của các giá trị được chỉ định

### 5.1. Xóa theo tư cách thành viên

Với dữ liệu:

```text
a = [A, B, A, C]
b = [A, X]
```

sau `a.removeAll(b)`, danh sách `a` còn `[B, C]`; kết quả là `true`.

Cả hai lần xuất hiện `"A"` đều bị xóa. `"X"` không có trong `a` nên không tạo thêm tác động. Đối số không cần chứa `"A"` hai lần để loại được cả hai phần tử của danh sách.

Mô hình suy luận:

```text
giữ lại mỗi phần tử x của a khi x không thuộc b
```

### 5.2. Khác remove một phần tử

Với một `ArrayList<String>` chứa `[A, B, A]`:

- `remove("A")` xóa một lần xuất hiện phù hợp.
- `removeAll(List.of("A"))` xóa mọi lần xuất hiện `"A"`.

`removeAll` không phải phép trừ có đếm số lượng. Nếu có ba đơn vị hàng và chỉ muốn trừ một đơn vị, cần thuật toán hoặc kiểu dữ liệu biểu diễn số lượng phù hợp.

### 5.3. Gọi lại có thể trả false

Sau khi đã loại hết `"A"`, gọi lại với cùng nhóm giá trị bị loại không còn thay đổi danh sách.

Điều này hữu ích khi chỉ cần biết có phát sinh thay đổi để cập nhật giao diện hoặc ghi nhận trạng thái. Tuy nhiên, không dùng nó như bằng chứng rằng một thao tác nghiệp vụ bên ngoài cũng đã xảy ra đúng một lần.

### 5.4. Ứng dụng

`removeAll` phù hợp với những yêu cầu như:

- Loại mã hàng thuộc danh sách ngừng kinh doanh.
- Loại nhãn bị chặn khỏi nhóm nhãn hiện có.
- Tính phần tử có trong nhóm thứ nhất nhưng không thuộc nhóm thứ hai.
- Lọc các giá trị đã được xử lý khi không cần giữ số lần lặp của việc xử lý.

Nếu muốn giữ nguyên dữ liệu ban đầu, phải tạo cấu trúc kết quả riêng trước khi gọi.

## 6. retainAll: lọc collection nhận theo nhóm được giữ

### 6.1. Chỉ giữ phần tử đang tồn tại

Với dữ liệu:

```text
a = [A, B, A, C]
b = [A, C, X]
```

sau `a.retainAll(b)`, `a` là `[A, A, C]`; kết quả là `true`.

Ba điểm cần quan sát:

1. `"B"` bị loại vì không thuộc nhóm được giữ.
2. Hai lần xuất hiện `"A"` vẫn còn vì danh sách cho phép lặp.
3. `"X"` không được thêm vào: đối số là tiêu chí giữ, không phải nguồn để bổ sung phần tử thiếu.

Mô hình suy luận:

```text
giữ lại mỗi phần tử x của a khi x thuộc b
```

### 6.2. Với List, đây không phải giao có đếm số lần

Nếu `a = [A, A, A]` và `b = [A]`, `retainAll` giữ cả ba phần tử `"A"` trong `a`.

Nếu bài toán yêu cầu giao của hai nhóm theo số lượng nhỏ hơn ở mỗi phía, kết quả cần một lần `"A"`; `retainAll` không thực hiện phép toán đó.

Với hai tập hợp có quan hệ bình đẳng nhất quán, `retainAll` diễn đạt phép giao theo cách quen thuộc. Với danh sách, nên đọc nó như **lọc theo sự hiện diện**, tránh bỏ qua phần tử lặp.

### 6.3. Giữ nguyên thứ tự của phần còn lại trong List

Lọc một danh sách không phải sắp xếp theo đối số. Nếu danh sách ban đầu là `[C, A, B, A]` và đối số là `[A, C]`, sau lọc còn `[C, A, A]`.

Nguồn tiêu chí không áp đặt lại thứ tự của danh sách nhận.

### 6.4. Đối số rỗng xóa hết phần tử hiện có

Với danh sách có thể sửa:

```java
List<String> selected = new ArrayList<>(List.of("A", "B"));
selected.retainAll(List.of());
```

danh sách `selected` trở thành rỗng.

Đây là hành vi đúng: không có giá trị nào nằm trong nhóm được giữ. Nếu ứng dụng coi danh sách được phép rỗng là “chưa tải được cấu hình”, gọi `retainAll` ngay có thể làm mất dữ liệu đang chọn. Phải phân biệt trạng thái tải lỗi với một tập cho phép rỗng hợp lệ.

## 7. So sánh đồng thời trên cùng dữ liệu ban đầu

Trong bảng dưới, mỗi dòng bắt đầu từ một bản `ArrayList` độc lập có nội dung `[A, B, A, C]`. Đối số là `[A, D]`.

| Phương thức | Giá trị trả về | Nội dung sau lời gọi | Cách hiểu |
| --- | --- | --- | --- |
| `addAll` | `true` | `[A, B, A, C, A, D]` | Bổ sung các phần tử từ nguồn |
| `containsAll` | `false` | `[A, B, A, C]` | Thiếu `D` |
| `removeAll` | `true` | `[B, C]` | Xóa mọi phần tử thuộc đối số |
| `retainAll` | `true` | `[A, A]` | Chỉ giữ phần tử thuộc đối số |

Không lấy kết quả của dòng trước làm đầu vào dòng sau rồi kỳ vọng còn khớp bảng này. Ba trong bốn phương thức có thể sửa đối tượng nhận.

## 8. Các trường hợp rỗng và giá trị boolean

Bảng dưới áp dụng cho `ArrayList` có thể sửa, các đầu vào độc lập và hợp lệ:

| Tình huống | Kết quả | Trạng thái nơi nhận |
| --- | --- | --- |
| `a.addAll(empty)` | `false` | Giữ nguyên |
| `a.containsAll(empty)` | `true` | Giữ nguyên |
| `empty.containsAll(nonEmpty)` | `false` | Giữ nguyên |
| `a.removeAll(empty)` | `false` | Giữ nguyên |
| `nonEmpty.retainAll(empty)` | `true` | Trở thành rỗng |
| `empty.retainAll(empty)` | `false` | Vẫn rỗng |
| `empty.addAll(nonEmpty)` | `true` | Chứa các phần tử nguồn |
| `empty.removeAll(nonEmpty)` | `false` | Vẫn rỗng |

Tách hai kiểu ý nghĩa của `boolean`:

- `containsAll`: câu trả lời cho điều kiện có chứa.
- `addAll`, `removeAll`, `retainAll`: có thay đổi collection nhận khi lời gọi kết thúc bình thường hay không.

Không gọi các phương thức này chỉ để kiểm tra “liệu có thay đổi không” nếu chưa muốn thay đổi dữ liệu. Riêng các phương thức sửa sẽ thực hiện thay đổi trước khi trả kết quả.

## 9. Dùng với phép toán tập hợp và giữ nguyên dữ liệu gốc

Khi hai đầu vào thực sự là các tập hợp, có thể tính:

```text
hợp:       bản sao của A rồi addAll(B)
giao:      bản sao của A rồi retainAll(B)
hiệu A−B:  bản sao của A rồi removeAll(B)
```

Đoạn minh họa:

```java
Set<String> union = new LinkedHashSet<>(a);
union.addAll(b);
```

Tên `union` được chọn vì biến này đại diện kết quả mới. Cách viết `Set<String> union = a;` chỉ tạo thêm một tham chiếu tới cùng tập hợp; gọi `addAll` sau đó vẫn sửa `a`.

Với danh sách, cần chọn rõ:

- Muốn giữ phần tử lặp: tạo `ArrayList` kết quả.
- Muốn loại phần tử lặp: tạo `Set` kết quả.
- Muốn giữ thứ tự xuất hiện khi loại lặp: chọn lớp triển khai có bảo đảm tương ứng, ví dụ `LinkedHashSet`.
- Muốn xử lý theo số lượng xuất hiện: dùng bảng đếm hoặc thuật toán đa tập phù hợp.

Sao chép collection theo các cách trên không tạo bản sao sâu của từng đối tượng bên trong. Nó giúp tách cấu trúc chứa phần tử, còn quyền sửa các đối tượng phần tử vẫn phải được xem xét riêng.

## 10. Bình đẳng, Comparator và đối tượng tự định nghĩa

Bốn phương thức không hiểu khái niệm “cùng người”, “cùng mã hàng” hay “cùng quyền” nếu mô hình dữ liệu không diễn đạt điều đó.

### 10.1. equals và hashCode

Với collection dùng bình đẳng theo giá trị, kết quả phụ thuộc cách lớp phần tử định nghĩa `equals`. Khi dùng cấu trúc băm, `hashCode` phải nhất quán với `equals`.

Nếu hai đối tượng có cùng mã nghiệp vụ nhưng vẫn dùng bình đẳng theo danh tính tham chiếu của `Object`, `containsAll` có thể không tìm được đối tượng mà người học tưởng là “giống nhau”.

Cần quyết định tiêu chí bình đẳng trước khi dùng collection làm công cụ đối chiếu dữ liệu.

### 10.2. Collection có thứ tự so sánh

Một `TreeSet` xác định sự tương đương qua phép so sánh. Nếu comparator không nhất quán với `equals`, các phép đối chiếu giữa collection có thể đem lại kết quả trái với trực giác của hợp đồng tập hợp.

Không pha trộn tùy tiện một tập so sánh không phân biệt hoa thường với một collection dùng `String.equals` rồi coi chúng đang dùng cùng một quan hệ bình đẳng. Có thể chuẩn hóa dữ liệu về khóa so sánh nhất quán trước khi thực hiện phép toán.

### 10.3. Không phụ thuộc số lần gọi equals

Để giải thích, có thể hình dung một vòng lặp kiểm tra thành viên. Nhưng không được thiết kế mã nghiệp vụ dựa vào giả định `equals` sẽ được gọi đúng một số lần hoặc theo một thứ tự cố định. Các lớp triển khai có quyền tối ưu theo cách phù hợp hợp đồng.

Các lớp nền [AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html) và [AbstractSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html) minh họa những chiến lược triển khai khác nhau. Chúng hữu ích để hiểu cơ chế, nhưng không thay thế hợp đồng của lớp đang dùng thực tế.

## 11. Khả năng sửa, ngoại lệ và tác động một phần

### 11.1. Ba thao tác sửa là thao tác tùy chọn

`addAll`, `removeAll` và `retainAll` không được mọi collection hỗ trợ.

Ví dụ:

```java
List<String> fixed = List.of("A", "B");
fixed.removeAll(List.of("A"));
```

Lời gọi sửa danh sách trên ném `UnsupportedOperationException`. Muốn sửa, có thể tạo `new ArrayList<>(fixed)` rồi thao tác trên bản mới.

Một danh sách kích thước cố định từ `Arrays.asList` không phải danh sách cho phép thêm hoặc xóa. Việc nó hỗ trợ `set` không suy ra hỗ trợ các thao tác làm đổi kích thước.

### 11.2. Không dựa vào đầu vào rỗng để né ngoại lệ

Với thao tác không được hỗ trợ, hợp đồng collection cho phép một số lời gọi không có tác dụng vẫn ném `UnsupportedOperationException`. Không thiết kế mã dựa vào suy luận “không có gì cần xóa nên chắc chắn không lỗi”.

Bảng về đầu vào rỗng ở trên đã chỉ rõ dùng `ArrayList` có thể sửa. Nó không phải lời hứa cho mọi collection tùy ý.

### 11.3. null và kiểu phần tử không hợp lệ

Cần phân biệt:

1. Truyền `null` thay cho chính collection đối số.
2. Truyền collection hợp lệ nhưng có phần tử `null`.

Bốn phương thức không coi đối số collection `null` là collection rỗng; đó là lỗi có thể dẫn tới `NullPointerException` theo hợp đồng.

Với phần tử `null`, hành vi phụ thuộc khả năng của các collection tham gia. Trong `removeAll` và `retainAll`, phép kiểm tra thành viên có thể đi vào đối số; vì thế cả collection nhận lẫn collection tiêu chí đều cần được xem xét.

Cũng cần nhớ `List.of(null)` tự ném ngoại lệ ngay khi tạo danh sách. Lỗi đó xảy ra trước khi gọi phương thức hàng loạt, nên không dùng nó để kết luận phương thức đang kiểm thử xử lý `null` thế nào.

Một số trường hợp khác có thể gặp `ClassCastException`, `IllegalArgumentException` hoặc, khi thêm bị giới hạn sức chứa, `IllegalStateException`. Đọc phần ngoại lệ của đúng phương thức và đúng lớp.

### 11.4. Không có bảo đảm giao dịch toàn bộ hoặc không gì cả

Chữ “All” không mang nghĩa nguyên tử.

Nếu một lần thêm hoặc một phép kiểm tra thành viên gặp ngoại lệ giữa chừng, hợp đồng chung không bảo đảm mọi thay đổi trước đó sẽ được hoàn tác. Trạng thái sau lỗi phụ thuộc cách triển khai và nơi xảy ra lỗi.

Không viết quy trình:

```text
thử sửa dữ liệu gốc bằng thao tác hàng loạt
nếu có lỗi thì mặc nhiên coi dữ liệu gốc chưa đổi
```

Nếu cần bảo vệ cấu trúc gốc khỏi tác động một phần, có thể:

1. Tạo một cấu trúc làm việc riêng có kiểu và ngữ nghĩa phù hợp.
2. Áp dụng thao tác lên cấu trúc đó.
3. Chỉ công bố kết quả sau khi kiểm tra thành công.
4. Dùng cơ chế đồng bộ hoặc giao dịch phù hợp nếu việc công bố phải nguyên tử.

Cách làm này không tự sao chép sâu các phần tử và không hoàn tác tác dụng phụ bên ngoài của mã người dùng, chẳng hạn một `equals` tùy chỉnh có hành vi phụ.

## 12. Alias, view và sửa dữ liệu ngoài ý muốn

### 12.1. Hai biến cùng trỏ một collection

Đoạn minh họa:

```java
List<String> original = new ArrayList<>(List.of("A", "B"));
List<String> filtered = original;
filtered.retainAll(List.of("A"));
```

`original` cũng còn `[A]`, vì hai biến trỏ cùng đối tượng. Tên biến mới không tạo dữ liệu mới.

### 12.2. View gắn với cấu trúc nền

Các view như `Map.keySet()`, `Map.values()` hoặc `List.subList(...)` có thể chia sẻ dữ liệu với cấu trúc nền. Khi thao tác xóa được hỗ trợ, `removeAll` hoặc `retainAll` qua view có thể xóa dữ liệu tương ứng trong nguồn.

Ví dụ, lọc tập khóa của map bằng `retainAll` có thể xóa cả các ánh xạ có khóa bị loại. Đây có thể là mục tiêu mong muốn, nhưng cần diễn đạt rõ trong thiết kế.

Không suy ra view nào cũng cho thêm phần tử chỉ vì nó là `Collection`. Đặc biệt, thêm khóa mới qua một tập khóa thường không đủ thông tin về giá trị để tạo ánh xạ.

### 12.3. Không dùng addAll với chính collection không rỗng làm nguồn

Hợp đồng chung của `Collection.addAll` không xác định hành vi khi nguồn bị sửa trong lúc thao tác; nó nêu cả trường hợp nguồn chính là collection nhận và collection đó không rỗng.

Vì vậy, không viết `a.addAll(a)` như cách tổng quát để nhân đôi. Một lớp cụ thể có thể cho ra kết quả trông hợp lý, nhưng đó không phải bảo đảm có thể áp dụng cho mọi `Collection`.

Nếu cần lặp lại nội dung một danh sách, hãy tạo nguồn độc lập trước:

```java
List<String> extra = new ArrayList<>(a);
a.addAll(extra);
```

Tương tự, không để nguồn là view chồng lấn với nơi nhận mà chưa đọc hợp đồng. Tạo bản chụp phù hợp giúp tránh nhiều phụ thuộc alias, nhưng vẫn phải giải quyết truy cập đồng thời nếu dữ liệu có thể bị luồng khác sửa.

## 13. Chi phí xử lý và cách chọn collection tiêu chí

Không có một độ phức tạp chung cho tất cả lớp triển khai của bốn phương thức.

Gọi:

- `n` là kích thước collection nhận.
- `m` là kích thước collection đối số.

Nếu thuật toán duyệt qua một phía và gọi `contains` trên phía còn lại, chi phí phụ thuộc mạnh vào cấu trúc được dùng cho kiểm tra thành viên.

### 13.1. Trực giác với danh sách

Khi cả hai phía đều là danh sách và kiểm tra tồn tại phải dò tuyến tính:

- Kiểm tra mọi phần tử của đối số có thể cần đến khoảng `O(n × m)` phép làm việc.
- Lọc các phần tử của nơi nhận theo danh sách tiêu chí cũng có thể có chi phí cùng bậc.

Thời gian còn phụ thuộc dừng sớm, dữ liệu lặp và chi phí `equals`. Không diễn giải biểu thức độ phức tạp thành số mili giây cố định.

### 13.2. Chuẩn bị Set làm tiêu chí

Nếu quan hệ bình đẳng được giữ nguyên và thao tác thực sự cần kiểm tra thành viên nhiều lần, có thể chuyển tiêu chí thành `HashSet`:

```java
Set<String> allowed = new HashSet<>(allowedValues);
List<String> result = new ArrayList<>(source);
result.retainAll(allowed);
```

Với bảng băm có phân bố phù hợp, tra cứu thành viên có chi phí kỳ vọng gần hằng số. Kết hợp việc xây tập tiêu chí và duyệt nguồn có thể đưa mô hình chi phí về gần tuyến tính theo tổng kích thước trong những trường hợp phù hợp.

Điều kiện cần xem xét:

- Có thêm chi phí tạo tập và dùng bộ nhớ.
- Tập bỏ phần tử lặp; điều đó phù hợp với lọc theo thành viên, nhưng không phù hợp nếu nghiệp vụ cần đếm.
- Phải bảo đảm `equals`–`hashCode` đúng.
- Không đổi sang `HashSet` nếu việc đó làm thay đổi quan hệ so sánh mà bài toán đang dùng.
- Với dữ liệu nhỏ hoặc chỉ gọi một lần, chi phí chuẩn bị có thể không đáng.
- Lớp triển khai có thể có thuật toán riêng, nên cần kiểm tra tài liệu hoặc đo trên tải thực.

### 13.3. HashSet ở phía nhận chưa đủ để kết luận thao tác luôn nhanh

Một triển khai `removeAll` có thể chọn hướng duyệt dựa trên kích thước hai phía. Có hướng gọi `remove` trên tập nhận; có hướng duyệt tập rồi gọi `contains` trên đối số.

Do đó, chỉ nhìn kiểu của `a` trong `a.removeAll(b)` chưa đủ để khẳng định mọi công việc là `O(n + m)`. Hợp đồng triển khai `AbstractSet.removeAll` là ví dụ nên đọc để hiểu sự khác biệt.

Tài liệu [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) và [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) giúp xác định đặc điểm lớp, còn phép đo phải dùng dữ liệu cùng ngữ nghĩa kết quả.

## 14. Chương trình Java hoàn chỉnh

Chương trình sau dùng các bản sao độc lập để bốn nhóm kết quả không vô tình phụ thuộc thay đổi của nhau. Dữ liệu là chuỗi, nên không cần tự định nghĩa bình đẳng nghiệp vụ trong ví dụ.

Lưu vào tệp `BulkCollectionDemo.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BulkCollectionDemo {
    public static void main(String[] args) {
        List<String> source = List.of("A", "B", "A", "C");
        List<String> incoming = List.of("B", "D");

        List<String> appended = new ArrayList<>(source);
        boolean added = appended.addAll(incoming);
        System.out.println("addAll changed: " + added);
        System.out.println("appended: " + appended);

        System.out.println("contains A and D: "
                + appended.containsAll(List.of("A", "D")));
        System.out.println("contains repeated A: "
                + List.of("A").containsAll(List.of("A", "A", "A")));
        System.out.println("contains reversed order: "
                + List.of("A", "B").containsAll(List.of("B", "A")));

        List<String> removed = new ArrayList<>(source);
        boolean removedAny = removed.removeAll(List.of("A", "X"));
        System.out.println("removeAll changed: " + removedAny);
        System.out.println("removed: " + removed);
        System.out.println("removeAll again: "
                + removed.removeAll(List.of("A", "X")));

        List<String> retained = new ArrayList<>(source);
        boolean retainedChanged = retained.retainAll(List.of("A", "C", "X"));
        System.out.println("retainAll changed: " + retainedChanged);
        System.out.println("retained: " + retained);
        System.out.println("retainAll again: "
                + retained.retainAll(List.of("A", "C", "X")));

        Set<String> codes = new LinkedHashSet<>(List.of("A", "B"));
        System.out.println("set addAll changed: "
                + codes.addAll(List.of("B", "C", "C")));
        System.out.println("set: " + codes);
        System.out.println("set existing only: "
                + codes.addAll(List.of("A", "B")));

        Set<String> union = new LinkedHashSet<>(source);
        union.addAll(incoming);
        Set<String> intersection = new LinkedHashSet<>(source);
        intersection.retainAll(incoming);
        Set<String> difference = new LinkedHashSet<>(source);
        difference.removeAll(incoming);

        System.out.println("union: " + union);
        System.out.println("intersection: " + intersection);
        System.out.println("difference: " + difference);
        System.out.println("source unchanged: " + source);

        List<String> emptyCase = new ArrayList<>(List.of("A"));
        System.out.println("contains empty: " + emptyCase.containsAll(List.of()));
        System.out.println("retain empty changed: "
                + emptyCase.retainAll(List.of()));
        System.out.println("after retain empty: " + emptyCase);

        List<Number> numbers = new ArrayList<>();
        numbers.addAll(List.of(1, 2));
        System.out.println("numbers: " + numbers);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 BulkCollectionDemo.java
java BulkCollectionDemo
```

Kết quả chuẩn:

```text
addAll changed: true
appended: [A, B, A, C, B, D]
contains A and D: true
contains repeated A: true
contains reversed order: true
removeAll changed: true
removed: [B, C]
removeAll again: false
retainAll changed: true
retained: [A, A, C]
retainAll again: false
set addAll changed: true
set: [A, B, C]
set existing only: false
union: [A, B, C, D]
intersection: [B]
difference: [A, C]
source unchanged: [A, B, A, C]
contains empty: true
retain empty changed: true
after retain empty: []
numbers: [1, 2]
```

Những dòng cần đối chiếu:

- `appended` vẫn có các phần tử `B` lặp, vì nơi nhận là danh sách.
- `contains repeated A` là `true` dù bên được kiểm tra chỉ có một phần tử `A`.
- `contains reversed order` là `true`, chứng minh phép kiểm tra không diễn đạt thứ tự.
- `removed` không còn bất kỳ lần xuất hiện `A` nào.
- `retained` vẫn giữ hai lần `A`.
- Các lần lọc lại không làm thay đổi dữ liệu nên trả `false`.
- `union`, `intersection`, `difference` là tập hợp mới, còn danh sách nguồn vẫn giữ nguyên.
- Các tập dùng `LinkedHashSet` để kết quả in ra theo thứ tự xác định.
- `numbers` chứng minh nguồn `Integer` có thể được thêm vào nơi nhận kiểu phần tử `Number`.

## 15. Khi nào cần cách xử lý khác?

### 15.1. Lọc theo điều kiện trên thuộc tính

Nếu muốn xóa mọi sản phẩm có giá nhỏ hơn một ngưỡng, đối số không phải một nhóm giá trị có sẵn. `removeIf` với điều kiện phù hợp thường diễn đạt rõ hơn `removeAll`.

Nếu chỉ muốn đối chiếu theo mã sản phẩm trong khi đối tượng có nhiều trường, cần xác định khóa đối chiếu và thuật toán lọc; đừng sửa `equals` tùy tiện chỉ để phục vụ một truy vấn.

### 15.2. Giữ nguyên nguồn và tạo kết quả

Có thể tạo một collection mới rồi gọi phương thức sửa trên bản đó. Stream cũng có thể diễn đạt lọc để tạo đầu ra riêng, nhưng phải chọn kết quả có thứ tự, phần tử lặp và khả năng sửa phù hợp.

`retainAll` tự nó không trả danh sách được lọc. Gán `List<String> result = a.retainAll(b)` là sai kiểu vì phương thức trả `boolean`.

### 15.3. Thay toàn bộ nội dung

Nếu mục đích là “đồng bộ nơi nhận thành dữ liệu của nguồn”, một mình `retainAll` không đủ vì không thêm giá trị còn thiếu; một mình `addAll` cũng không xóa giá trị thừa.

Cần thiết kế thao tác thay thế hoặc tính phần thêm, phần xóa theo yêu cầu. Nếu dữ liệu có thể bị đọc đồng thời, còn phải xác định ranh giới công bố kết quả, tránh để bên đọc thấy trạng thái trung gian.

### 15.4. Collection chia sẻ giữa nhiều luồng

Bốn lời gọi không tự tạo giao dịch trên hai collection.

Ngay cả khi một lớp hỗ trợ truy cập đồng thời, không được suy ra mọi thao tác hàng loạt hoặc chuỗi thao tác trên nhiều cấu trúc đều nguyên tử. Đối số cũng có thể thay đổi khi đang được duyệt.

Hãy đọc bảo đảm của đúng lớp và tổ chức đồng bộ, bản chụp hoặc mô hình sở hữu dữ liệu theo yêu cầu. Fail-fast iterator chỉ hỗ trợ phát hiện một số sửa đổi không hợp lệ; nó không thay thế kiểm soát đồng thời.

## 16. Bài tập tự kiểm chứng

### Bài 1: Dự đoán trước khi chạy

Cho `a = [red, blue, red, green]` và `b = [red, yellow]`. Với mỗi phương thức, tạo lại `a` từ dữ liệu ban đầu.

Hãy ghi trước:

- Giá trị `boolean`.
- Nội dung `a` sau lời gọi.
- Nội dung `b`.
- Có giữ phần tử lặp không?

Đáp án để đối chiếu:

| Phương thức | Boolean | a sau lời gọi |
| --- | --- | --- |
| `addAll` | `true` | `[red, blue, red, green, red, yellow]` |
| `containsAll` | `false` | `[red, blue, red, green]` |
| `removeAll` | `true` | `[blue, green]` |
| `retainAll` | `true` | `[red, red]` |

`b` giữ nguyên khi hai collection độc lập và không có hành vi phụ đặc biệt.

### Bài 2: Kiểm tra quyền bắt buộc

Có `granted = {READ, EXPORT}` và `required = {READ, WRITE}`.

Viết biểu thức kiểm tra đúng chiều. Kết quả phải là `false`. Sau đó tìm tập quyền còn thiếu bằng cách tạo bản sao của `required` rồi loại các quyền đã được cấp; tập còn lại chứa `WRITE`.

Câu hỏi bổ sung: nếu `required` rỗng, phép kiểm tra trả gì và chính sách nghiệp vụ của ứng dụng có chấp nhận điều đó không?

### Bài 3: Phân biệt số lượng với sự hiện diện

Cho kho hàng có một mã `A`, giỏ hàng yêu cầu ba lần mã `A`. Chứng minh `containsAll` không đủ bằng một ví dụ nhỏ, rồi viết lại kiểm tra theo bảng đếm.

Đừng sửa dữ liệu nguồn trong lúc chỉ đang xác minh đủ hàng.

### Bài 4: Bảo vệ dữ liệu gốc

Viết phương thức nhận danh sách nguồn và nhóm bị chặn, trả danh sách kết quả mới. Kiểm tra:

- Nguồn giữ nguyên.
- Mọi lần xuất hiện của mã bị chặn đều bị loại.
- Thứ tự của phần còn lại được giữ.
- Nhóm bị chặn rỗng.
- Nguồn rỗng.
- Không có phần tử khớp.

Sau đó giải thích vì sao đây chỉ là sao chép cấu trúc danh sách nếu phần tử là đối tượng có thể thay đổi.

### Bài 5: Lựa chọn cấu trúc tiêu chí

So sánh lọc một danh sách lớn với tiêu chí là danh sách và với tiêu chí là tập băm. Chỉ đo sau khi đã xác minh hai cách dùng cùng quan hệ bình đẳng và cho cùng kết quả.

Kết luận phải nêu cả chi phí chuẩn bị tập, bộ nhớ và số lần tái sử dụng tiêu chí.

## 17. Các điểm cần tự trả lời được

1. Trong `a.retainAll(b)`, bên nào bị sửa và bên nào chỉ làm tiêu chí?
2. Vì sao `containsAll` có thể trả `true` khi đối số chứa nhiều lần cùng một giá trị hơn nơi nhận?
3. `removeAll` khác gọi `remove` một lần thế nào?
4. Vì sao `retainAll` không bổ sung phần tử thiếu từ đối số?
5. `false` của `addAll` trên tập hợp có phải là lỗi không?
6. Đầu vào rỗng khác đầu vào `null` như thế nào?
7. Có thể dựa vào một lời gọi “không làm gì” để tránh `UnsupportedOperationException` trên mọi collection không?
8. Sau khi phương thức ném ngoại lệ, vì sao chưa được kết luận dữ liệu gốc còn nguyên?
9. Vì sao cần sao chép trước khi tính hiệu hoặc giao nếu nguồn phải được giữ lại?
10. View và hai biến tham chiếu cùng dữ liệu có thể làm thay đổi lan sang đâu?
11. Vì sao `HashSet` ở một phía chưa đủ để biết độ phức tạp toàn lời gọi?
12. Khi số lượng phần tử hoặc quan hệ so sánh khác nhau, cần thay đổi mô hình dữ liệu thế nào?

Điều cần nhớ khi áp dụng là: xác định bên nhận, hiểu đối số là nguồn hay tiêu chí, chọn đúng ngữ nghĩa phần tử lặp, rồi kiểm tra khả năng sửa và hiệu lực của kết quả. Các phương thức hàng loạt chỉ chính xác khi mô hình dữ liệu cùng hợp đồng mà chương trình dựa vào cũng chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-005">removeIf và xử lý điều kiện xóa</a>

<details>
<summary>Xem chi tiết</summary>

# removeIf và xử lý điều kiện xóa

Khi cần xóa tất cả phần tử không còn hợp lệ, ta có thể mô tả việc cần làm bằng một điều kiện: xóa đơn đã hủy, xóa chuỗi rỗng, xóa mục hết hạn hoặc xóa số chẵn. `removeIf` diễn đạt trực tiếp kiểu thao tác đó: **kiểm tra từng phần tử bằng một điều kiện và loại các phần tử thỏa điều kiện khỏi collection đang được gọi**.

Điểm cần nắm ngay từ đầu là chiều của điều kiện:

- Điều kiện trả `true`: phần tử thuộc diện cần xóa.
- Điều kiện trả `false`: phần tử được giữ lại.

Đây là thao tác sửa dữ liệu tại chỗ. Nó khác với tạo một danh sách kết quả bằng cách lọc stream. Bài này thuộc phần hợp đồng chung của Java Collections Framework; các ví dụ dùng Java 17 trở lên.

## 1. Hợp đồng của removeIf

Chữ ký được khai báo trong `Collection<E>`:

```java
default boolean removeIf(Predicate<? super E> filter)
```

Đây là chữ ký API, không phải một chương trình độc lập.

`Predicate<T>` biểu diễn một phép kiểm tra nhận một giá trị kiểu `T` và trả `boolean`. Lambda như `n -> n % 2 == 0` là một cách viết predicate cho số chẵn.

`? super E` cho phép nhận một predicate xử lý được phần tử kiểu `E` hoặc một kiểu cha phù hợp. Chẳng hạn, một `Predicate<Object>` có thể dùng để kiểm tra các phần tử của `Collection<String>`. Không cần ép kiểu tùy tiện để viết điều kiện thông thường.

Nếu phương thức hoàn tất bình thường:

| Tình huống | Kết quả |
| --- | --- |
| Có ít nhất một phần tử bị xóa | Trả `true` |
| Không xóa phần tử nào | Trả `false` |

Giá trị trả về **không phải số lượng phần tử đã xóa**, cũng không phải kết quả predicate của phần tử cuối. Nếu collection rỗng hoặc không có phần tử thỏa điều kiện, một collection có hỗ trợ thao tác có thể hoàn tất và trả `false`.

Predicate `null` không hợp lệ. Ngoại lệ phát sinh trong quá trình kiểm tra hoặc xóa được truyền ra bên gọi; hợp đồng chung không hứa hẹn hoàn tác mọi thay đổi đã xảy ra. [Java 17: Collection.removeIf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate))

## 2. Xóa mọi phần tử khớp, không chỉ lần đầu

Ví dụ trong một phương thức:

```java
List<Integer> numbers =
        new ArrayList<>(List.of(1, 2, 2, 3, 4));

boolean changed = numbers.removeIf(n -> n % 2 == 0);

System.out.println(changed); // true
System.out.println(numbers); // [1, 3]
```

Cần import `List` và `ArrayList` từ `java.util`.

Cả hai lần xuất hiện của `2` và phần tử `4` đều bị xóa. Với danh sách này, các phần tử còn lại giữ thứ tự tương đối ban đầu.

Nếu gọi tiếp:

```java
boolean changedAgain = numbers.removeIf(n -> n > 10);
System.out.println(changedAgain); // false
System.out.println(numbers);      // [1, 3]
```

Không có số nào lớn hơn 10 nên không thay đổi.

`removeIf` không thêm, thay thế hoặc biến đổi giá trị phần tử. Nếu cần chuyển mỗi giá trị sang một giá trị mới, phải chọn thao tác khác như `List.replaceAll` hoặc ánh xạ bằng stream.

### Phân biệt với các thao tác xóa khác

| Nhu cầu | Cách diễn đạt |
| --- | --- |
| Xóa một lần xuất hiện của một giá trị | `remove(value)` |
| Xóa các phần tử thuộc một tập giá trị | `removeAll(values)` |
| Chỉ giữ các phần tử thuộc một tập giá trị | `retainAll(values)` |
| Xóa theo thuộc tính hoặc điều kiện tính toán | `removeIf(predicate)` |
| Xóa toàn bộ | `clear()` |

Ví dụ `removeIf(n -> n == 2)` trên một danh sách số nguyên không có `null` xóa mọi số 2. Nó tránh sự nhập nhằng giữa `List<Integer>.remove(int index)` và `remove(Object value)`, vì điều kiện xóa được mô tả rõ. Tuy nhiên, `n == 2` có unboxing và sẽ gây lỗi nếu gặp `null`; chính sách `null` cần được thiết kế riêng.

## 3. Cơ chế mặc định và quyền sửa collection

### 3.1. Cách triển khai mặc định sử dụng iterator

Có thể hình dung cách triển khai mặc định của `Collection.removeIf` qua mã minh họa:

```java
boolean changed = false;
Iterator<E> iterator = collection.iterator();

while (iterator.hasNext()) {
    E element = iterator.next();
    if (filter.test(element)) {
        iterator.remove();
        changed = true;
    }
}
return changed;
```

Đây là mã mô tả thuật toán, đã lược bước kiểm tra predicate khác `null`; nó không phải chương trình hoàn chỉnh để sao chép trực tiếp.

Lưu ý chữ **mặc định**: một lớp có thể ghi đè phương thức bằng cách xử lý phù hợp với cấu trúc riêng. Không suy ra mọi collection đều xóa từng phần tử bằng iterator, hoặc đều có cùng chi phí và hành vi khi predicate lỗi.

`Iterator.remove()` là thao tác tùy chọn. Với cách triển khai mặc định, nếu iterator không hỗ trợ xóa thì khi cần xóa phần tử phù hợp, `UnsupportedOperationException` có thể được ném. [Java 17: Iterator.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove())

### 3.2. Không xóa bằng collection ngay trong for-each

Đoạn sau là một cách viết dễ gây lỗi:

```java
// Không dùng cách này để xóa trong lúc duyệt ArrayList.
for (Integer number : numbers) {
    if (number % 2 == 0) {
        numbers.remove(number);
    }
}
```

Vòng `for-each` sử dụng cơ chế duyệt. Sửa cấu trúc bằng đường gọi khác trong lúc đang duyệt có thể làm iterator mất hiệu lực và gây `ConcurrentModificationException` hoặc dẫn đến xử lý không như mong đợi. Không coi việc “một lần chạy không ném lỗi” là chứng minh đúng.

Với điều kiện chỉ phụ thuộc phần tử và trạng thái tham chiếu ổn định, dùng:

```java
numbers.removeIf(number -> number % 2 == 0);
```

Nếu cần điều khiển quá trình duyệt từng bước và iterator hỗ trợ xóa, có thể dùng `iterator.remove()` sau `next()`. Đó là trường hợp khác với gọi `numbers.remove` từ bên ngoài iterator.

### 3.3. Có List không đồng nghĩa có quyền xóa

Các danh sách có thể khác khả năng sửa:

| Nguồn tạo | Khả năng liên quan đến xóa |
| --- | --- |
| `new ArrayList<>(...)` | Hỗ trợ xóa phần tử |
| `Arrays.asList(...)` | Danh sách kích thước cố định; không hỗ trợ xóa làm đổi kích thước |
| `List.of(...)` | Không cho sửa nội dung qua API của danh sách |
| `Collections.unmodifiableList(source)` | View không cho sửa; nguồn bên dưới vẫn có thể đổi qua tham chiếu khác |
| `CopyOnWriteArrayList` | Có hỗ trợ `removeIf`, dù iterator của nó không hỗ trợ `remove` |

`Arrays.asList` không phải `java.util.ArrayList` thông thường. Nó cho thay phần tử bằng `set` nhưng không cho giảm số phần tử. Khi cần xóa, có thể chủ động tạo bản có thể sửa:

```java
List<String> editable =
        new ArrayList<>(Arrays.asList("old", "keep", "old"));

editable.removeIf("old"::equals);
System.out.println(editable); // [keep]
```

Đây là đoạn minh họa trong một phương thức, cần import các lớp tương ứng. [Java 17: Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...))

Không dùng predicate luôn `false` để thử xem một collection có sửa được hay không. Với thao tác không hỗ trợ, trường hợp không tạo thay đổi có thể vẫn bị từ chối tùy hợp đồng. Hãy biết rõ nguồn tạo và quyền sửa dữ liệu.

## 4. Thiết kế điều kiện xóa đúng ý nghĩa

### 4.1. Đặt tên theo điều kiện xóa

So sánh hai cách đặt tên:

```java
Predicate<Order> shouldDelete =
        order -> order.status() == Status.CANCELLED;

orders.removeIf(shouldDelete);
```

và:

```java
Predicate<Order> shouldKeep =
        order -> order.status() != Status.CANCELLED;

orders.removeIf(shouldKeep); // Sai ý định: đang xóa các đơn cần giữ.
```

Đây là các đoạn minh họa; `Order`, `Status` và `orders` do ứng dụng định nghĩa.

Tên như `expired`, `cancelled` hoặc `shouldDelete` giúp đối chiếu chiều điều kiện. Nếu đã có predicate mô tả “giữ”, phải đổi chiều bằng `negate()` khi dùng làm điều kiện xóa.

### 4.2. Xử lý null trước khi gọi phương thức trên phần tử

Nếu danh sách có thể chứa `null`, điều kiện sau không an toàn:

```java
names.removeIf(name -> name.isBlank());
```

Nó ném `NullPointerException` khi gặp `null`. Trước hết hãy quyết định `null` thuộc diện xóa hay giữ.

Xóa cả `null` lẫn chuỗi trắng:

```java
names.removeIf(name -> name == null || name.isBlank());
```

Giữ `null`, chỉ xóa chuỗi trắng không null:

```java
names.removeIf(name -> name != null && name.isBlank());
```

Chỉ xóa `null`:

```java
names.removeIf(Objects::isNull);
```

Các đoạn cần biến `names` đã được tạo và import `java.util.Objects` cho ví dụ cuối.

Toán tử `||` và `&&` có đánh giá ngắn mạch. Ở trường hợp thứ nhất, nếu `name == null` đúng thì không gọi `isBlank`. Đảo thứ tự thành `name.isBlank() || name == null` không còn bảo vệ được.

Một trường bên trong đối tượng cũng có thể là `null`. Chặn `item != null` chưa đủ nếu sau đó gọi `item.owner().isBlank()` mà `owner()` vẫn có thể trả `null`.

### 4.3. Ghép nhiều điều kiện có chủ đích

`Predicate` có các phương thức ghép:

- `a.and(b)`: cả hai điều kiện cùng đúng.
- `a.or(b)`: ít nhất một điều kiện đúng.
- `a.negate()`: đảo kết quả.

Ví dụ với dữ liệu đã được làm sạch `null`:

```java
Predicate<String> longName = name -> name.length() > 3;
Predicate<String> startsWithM = name -> name.startsWith("M");

names.removeIf(longName.and(startsWithM));
```

Điều kiện này xóa tên dài hơn 3 ký tự **và** bắt đầu bằng `M`. Nó không xóa mọi tên bắt đầu bằng `M` và cũng không xóa mọi tên dài.

`and` và `or` đánh giá ngắn mạch theo thứ tự. Nếu điều kiện trước đã quyết định kết quả, điều kiện sau không được gọi. Nếu một predicate ném ngoại lệ, ngoại lệ được truyền ra, không được tự chuyển thành `false`. [Java 17: Predicate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Predicate.html)

### 4.4. Cố định mốc tham chiếu của một đợt xử lý

Giả sử muốn xóa nhiệm vụ đã lưu trữ hoặc hết hạn trước ngày chốt:

```java
record Task(String id, LocalDate expiresOn, boolean archived) {}

LocalDate cutoff = LocalDate.of(2025, 1, 1);

Predicate<Task> shouldDelete = task ->
        task.archived()
        || (task.expiresOn() != null
            && task.expiresOn().isBefore(cutoff));

tasks.removeIf(shouldDelete);
```

Đây là minh họa cần import `LocalDate`, `Predicate` và một danh sách `tasks` không chứa phần tử `null`. Ở đây `expiresOn == null` được quy ước là không có ngày hết hạn.

Điều kiện dùng “trước ngày chốt”, nên nhiệm vụ có ngày hết hạn đúng `cutoff` chưa bị xóa vì ngày. Nếu muốn “đến hết ngày chốt”, phải diễn đạt điều kiện bao gồm ngày bằng.

Không gọi đồng hồ hoặc tải trạng thái thay đổi liên tục cho từng phần tử nếu yêu cầu nghiệp vụ là xét tất cả theo cùng một mốc. Tính mốc một lần làm kết quả dễ kiểm thử và giải thích hơn. Với thời điểm chính xác hoặc múi giờ, chọn kiểu thời gian và quy tắc phù hợp; không tự đổi mọi bài toán sang ngày địa phương.

## 5. Giữ predicate tập trung vào việc kiểm tra

Một predicate dễ kiểm soát thường:

- Chỉ đọc phần tử và các giá trị tham chiếu đã được chuẩn bị.
- Trả `true` hoặc `false` theo quy tắc rõ.
- Không tự thêm/xóa phần tử trong chính collection đang được xử lý.
- Không sửa thuộc tính được dùng để quyết định cho những phần tử khác.
- Không thực hiện giao dịch bên ngoài như gửi tin, trừ tiền hoặc xóa bản ghi từ xa.

Ví dụ cần tránh:

```java
// Minh họa một cách viết sai: predicate lại sửa chính danh sách đang xóa.
items.removeIf(item -> {
    items.add(replacement);
    return item.isExpired();
});
```

`items`, `replacement` và kiểu phần tử trong đoạn trên chỉ minh họa tình huống. Sửa cấu trúc ngay trong predicate có thể làm quá trình duyệt thất bại hoặc vi phạm yêu cầu của cách triển khai. Không dựa vào kiểu ngoại lệ cụ thể như một cơ chế điều khiển hợp lệ.

Một điều kiện cần gọi dịch vụ mạng để biết có xóa hay không cũng khiến thời gian xử lý và lỗi khó dự đoán. Khi phù hợp, chuẩn bị dữ liệu tham chiếu trước, kiểm tra tính hợp lệ rồi đánh giá điều kiện trong bộ nhớ.

Nếu cần ghi lại những mục bị loại, nên thiết kế quá trình phân loại hoặc tạo kết quả riêng. Đừng dựa vào số lần hoặc thứ tự predicate được gọi như một cơ chế kiểm toán chung cho mọi collection.

## 6. Ngoại lệ và trạng thái xóa dở dang

### 6.1. Không có bảo đảm giao dịch trong hợp đồng chung

Giả sử predicate xóa số chẵn nhưng ném lỗi khi gặp 3. Một cách triển khai mặc định có thể đã xóa 2 trước khi chạm đến 3. Ngoại lệ kết thúc phương thức, nhưng không tự đưa 2 trở lại.

Do đó:

- Khi phương thức ném ngoại lệ, không có giá trị `boolean` được trả cho lần gọi đó.
- Không được kết luận collection không đổi chỉ vì không nhận được `true`.
- Không tự thử lại nếu predicate đã có tác động bên ngoài hoặc yêu cầu phải xử lý chính xác một lần.

Một lớp cụ thể có thể xử lý khác, chẳng hạn đánh dấu trước rồi mới sửa dữ liệu. Điều đó không tạo một bảo đảm hoàn tác áp dụng cho tất cả `Collection`.

Chương trình cuối bài tạo một collection nhỏ dùng triển khai mặc định để quan sát xóa dở dang. Nó không lấy hành vi lỗi riêng của `ArrayList` để suy ra hợp đồng chung.

### 6.2. Khi cần “tất cả hoặc không có gì”

Nếu yêu cầu là chỉ công bố kết quả khi mọi điều kiện đều đánh giá thành công, có thể:

1. Tạo một collection kết quả riêng.
2. Đánh giá điều kiện và đưa các phần tử cần giữ vào kết quả.
3. Nếu tất cả thành công, thay tham chiếu do thành phần sở hữu quản lý.

Đây vẫn cần thiết kế phù hợp với các bên đang giữ tham chiếu và với truy cập đồng thời. Tạo bản sao không tự tạo giao dịch với cơ sở dữ liệu hoặc dịch vụ bên ngoài; bản sao thường chỉ chứa lại các tham chiếu phần tử.

Không xem `clear()` rồi `addAll(result)` trên collection dùng chung là một thao tác nguyên tử. Nếu nhiều bên phải cùng quan sát một trạng thái nhất quán, phải có cơ chế công bố hoặc đồng bộ riêng.

## 7. Xóa tại chỗ hay tạo kết quả mới?

Hai đoạn có chiều điều kiện khác nhau:

```java
numbers.removeIf(isEven); // Xóa phần tử isEven == true.
```

```java
List<Integer> kept = numbers.stream()
        .filter(isEven.negate()) // Giữ phần tử isEven == false.
        .toList();
```

Đây là minh họa với `Predicate<Integer> isEven` đã được định nghĩa.

| Đặc điểm | `removeIf` | `stream().filter(...).toList()` |
| --- | --- | --- |
| Đối tượng bị thay đổi | Collection đích | Không sửa cấu trúc nguồn qua thao tác lọc |
| `true` của điều kiện | Xóa | Giữ |
| Kết quả | `boolean` cho biết có xóa không | Một danh sách kết quả |
| Yêu cầu quyền xóa nguồn | Có | Không |
| Tham chiếu phần tử | Phần tử giữ lại vẫn là các đối tượng đó | Kết quả thường vẫn tham chiếu các đối tượng nguồn |

Trong Java 17, danh sách do `Stream.toList()` trả về không hỗ trợ sửa. Nếu cần danh sách kết quả có thể sửa, hãy tạo `ArrayList` rõ ràng hoặc dùng cách thu thập phù hợp. Không nhầm `toList()` với một cam kết tạo `ArrayList`. [Java 17: Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList())

Lựa chọn theo quyền sở hữu: nếu phương thức được giao nhiệm vụ làm sạch một collection có thể sửa, `removeIf` thường diễn đạt trực tiếp. Nếu bên gọi cần giữ dữ liệu gốc hoặc nhiều nơi đang dùng nó, một kết quả mới có thể phù hợp hơn.

## 8. Xóa trên view và trên Map

`Map` không có `removeIf` trực tiếp như `Collection`, nhưng các view của nó là collection.

Ví dụ xóa mục hết hàng:

```java
Map<String, Integer> stock = new LinkedHashMap<>();
stock.put("book", 0);
stock.put("pen", 2);
stock.put("case", 0);

stock.entrySet().removeIf(entry -> entry.getValue() == 0);

System.out.println(stock); // {pen=2}
```

Cần import `Map` và `LinkedHashMap`. Dữ liệu minh họa bảo đảm giá trị tồn kho không phải `null`.

`entrySet()` là view gắn với map; xóa entry qua view làm mất ánh xạ tương ứng trong map. Nó không phải bản sao độc lập.

Các hướng thường gặp:

```java
map.keySet().removeIf(keyRule);
map.values().removeIf(valueRule);
map.entrySet().removeIf(entryRule);
```

Đây là các dạng sử dụng, không phải chương trình độc lập. Nếu điều kiện cần cả khóa và giá trị, dùng `entrySet` giúp diễn đạt trực tiếp hơn.

Một giá trị có thể xuất hiện ở nhiều khóa. `map.values().removeIf(v -> condition)` có thể làm mất nhiều ánh xạ nếu nhiều giá trị thỏa điều kiện.

Tương tự, `subList` của một danh sách là một view. Xóa trên `subList` có thể xóa cả phần tử trong danh sách gốc. Luôn kiểm tra xem đối tượng đang giữ là dữ liệu riêng hay một lớp nhìn vào dữ liệu khác. [Java 17: Map views](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#entrySet()), [List.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int))

## 9. Đồng thời và chi phí thực thi

### 9.1. removeIf không làm collection thông thường thành cấu trúc đồng thời

Không gọi `removeIf` trên một `ArrayList` đang bị luồng khác sửa mà không có cơ chế phối hợp.

Ngay cả với cấu trúc hỗ trợ truy cập đồng thời, cần đọc hợp đồng cụ thể. Không suy ra toàn bộ lần gọi `removeIf` là một giao dịch cho mọi phần tử, hoặc toàn bộ predicate nhìn thấy cùng một ảnh chụp bất biến.

`CopyOnWriteArrayList` là ví dụ quan trọng về việc lớp có thể ghi đè hành vi: iterator của nó không cho xóa, nhưng bản thân danh sách hỗ trợ `removeIf`. Vì vậy, không kết luận khả năng của `removeIf` chỉ từ khả năng của iterator. Chi phí sao chép khi sửa cũng khiến lựa chọn này phù hợp hơn với mô hình đọc nhiều, sửa ít. [Java 17: CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

### 9.2. Tính cả chi phí điều kiện lẫn chi phí xóa

Nếu phải xét `n` phần tử, predicate được đánh giá trong quá trình xử lý số phần tử đó. Một điều kiện đơn giản khác xa điều kiện gọi mạng hoặc tìm trong một danh sách lớn mỗi lần.

Ví dụ:

```java
items.removeIf(item -> blockedIds.contains(item.id()));
```

Nếu `blockedIds` là một danh sách có `m` phần tử và thường phải duyệt dài để tìm, riêng phần kiểm tra có thể dẫn tới chi phí gần `n × m`. Một tập tra cứu được xây dựng trước có thể giảm chi phí trung bình nếu phù hợp với quy tắc bằng nhau và dữ liệu không đổi trong đợt xử lý.

Mặt khác, việc xóa phụ thuộc cấu trúc: liên kết nút, dịch chuyển dữ liệu, gom phần tử hoặc sao chép có chi phí khác nhau. Một lớp có thể tối ưu xóa hàng loạt. Không khẳng định mọi `removeIf` đều O(n), cũng không mặc định nó luôn nhanh hơn mọi vòng lặp thủ công.

Khi dữ liệu lớn, đo với kiểu collection, tỷ lệ khớp và predicate đại diện cho thực tế. Độ rõ của quy tắc vẫn quan trọng hơn việc rút ngắn lambda bằng mọi giá.

## 10. Chương trình Java 17 hoàn chỉnh

Lưu mã sau vào `RemoveIfDemo.java`:

```java
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class RemoveIfDemo {
    static final class IterableBag<E> extends AbstractCollection<E> {
        private final List<E> data;

        IterableBag(List<E> source) {
            data = new ArrayList<>(source);
        }

        @Override
        public Iterator<E> iterator() {
            return data.iterator();
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 2, 3, 4));
        boolean changed = numbers.removeIf(n -> n % 2 == 0);
        System.out.println("removed even = " + changed);
        System.out.println("numbers = " + numbers);
        System.out.println("removed above 10 = " + numbers.removeIf(n -> n > 10));

        List<String> names = new ArrayList<>(
                Arrays.asList(null, "", "  ", "Lan", "Minh"));
        names.removeIf(name -> name == null || name.isBlank());
        System.out.println("names = " + names);

        Predicate<String> longName = name -> name.length() > 3;
        Predicate<String> startsWithM = name -> name.startsWith("M");
        names.removeIf(longName.and(startsWithM));
        System.out.println("after composed rule = " + names);

        List<Integer> original = new ArrayList<>(List.of(1, 2, 3, 4));
        List<Integer> kept = original.stream().filter(n -> n % 2 != 0).toList();
        System.out.println("original = " + original);
        System.out.println("filtered copy = " + kept);

        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("book", 0);
        stock.put("pen", 2);
        stock.put("case", 0);
        stock.entrySet().removeIf(entry -> entry.getValue() == 0);
        System.out.println("stock = " + stock);

        try {
            Arrays.asList(1, 2, 3).removeIf(n -> n % 2 == 0);
        } catch (UnsupportedOperationException expected) {
            System.out.println("fixed-size removal is unsupported");
        }

        try {
            numbers.removeIf(null);
        } catch (NullPointerException expected) {
            System.out.println("null predicate is invalid");
        }

        CopyOnWriteArrayList<String> listeners =
                new CopyOnWriteArrayList<>(List.of("active", "stale"));
        listeners.removeIf("stale"::equals);
        System.out.println("listeners = " + listeners);

        IterableBag<Integer> bag = new IterableBag<>(List.of(1, 2, 3, 4));
        try {
            bag.removeIf(n -> {
                if (n == 3) {
                    throw new IllegalStateException("stop at 3");
                }
                return n % 2 == 0;
            });
        } catch (IllegalStateException expected) {
            System.out.println("predicate failure = " + expected.getMessage());
        }
        System.out.println("bag after failure = " + bag);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RemoveIfDemo.java
java RemoveIfDemo
```

Stdout:

```text
removed even = true
numbers = [1, 3]
removed above 10 = false
names = [Lan, Minh]
after composed rule = [Lan]
original = [1, 2, 3, 4]
filtered copy = [1, 3]
stock = {pen=2}
fixed-size removal is unsupported
null predicate is invalid
listeners = [active]
predicate failure = stop at 3
bag after failure = [1, 3, 4]
```

Cách kiểm chứng từng phần:

1. Danh sách số xóa mọi số chẵn, kể cả phần tử trùng. Kết quả trả về là `true`; lần tìm số lớn hơn 10 không xóa gì nên là `false`.
2. Điều kiện có kiểm tra `null` ở trước làm sạch được cả phần tử `null` lẫn chuỗi trắng.
3. `and` xóa `"Minh"` vì đồng thời dài hơn 3 và bắt đầu bằng `M`.
4. Stream tạo danh sách giữ số lẻ; danh sách `original` vẫn còn đủ 4 phần tử.
5. Xóa qua `entrySet` thay đổi chính map tồn kho.
6. Danh sách kích thước cố định không hỗ trợ việc xóa phần tử 2.
7. Predicate `null` bị từ chối.
8. `CopyOnWriteArrayList.removeIf` vẫn hoạt động dù iterator của lớp không hỗ trợ xóa.
9. `IterableBag` kế thừa `AbstractCollection`, chỉ cung cấp iterator và kích thước. Nó dùng thuật toán `removeIf` mặc định. Phần tử 2 đã bị xóa trước lúc predicate ném lỗi tại 3, nên sau lỗi còn `[1, 3, 4]`.

Lớp nhỏ `IterableBag` ở đây chỉ để làm rõ hợp đồng và trạng thái khi lỗi; không phải đề xuất viết một collection mới thay cho các lớp JDK trong ứng dụng thông thường.

## 11. Lỗi thường gặp

| Lỗi | Cách hiểu đúng |
| --- | --- |
| Viết điều kiện giữ vào `removeIf` | `true` nghĩa là xóa; đảo điều kiện khi cần |
| Cho rằng trả `true` là xóa đúng một phần tử | Nó chỉ nói có thay đổi, có thể đã xóa nhiều phần tử |
| Dùng predicate gọi phương thức trên phần tử có thể `null` | Đặt quy tắc và kiểm tra `null` trước |
| Coi `Arrays.asList` là danh sách có thể thêm/xóa | Nó có kích thước cố định |
| Coi view là bản sao | Xóa có thể truyền xuống dữ liệu gốc |
| Xóa hoặc thêm lại vào collection từ predicate | Có thể phá hợp đồng duyệt và gây lỗi |
| Predicate vừa kiểm tra vừa gửi thông báo/giao dịch | Lỗi hoặc thử lại có thể để lại tác động không thể hoàn tác |
| Coi ngoại lệ nghĩa là chưa có gì bị xóa | Hợp đồng chung có thể để lại thay đổi dở dang |
| Coi phương thức trên collection đồng thời là giao dịch toàn bộ | Phải kiểm tra bảo đảm cụ thể và bất biến cần giữ |
| Giả định iterator không xóa được thì `removeIf` cũng không được | Lớp có thể ghi đè phương thức để hỗ trợ |

## 12. Tự luyện

1. Danh sách `[1, 2, 2, 3]` gọi `removeIf(n -> n == 2)`. Còn gì và trả gì?  
   **Đáp án:** còn `[1, 3]`, trả `true`.

2. Danh sách có `null`, `""` và `"A"`. Viết điều kiện xóa hai phần tử đầu mà không gây lỗi.  
   **Đáp án:** `value -> value == null || value.isBlank()`.

3. Đã có predicate `shouldKeep`. Muốn xóa những phần tử không cần giữ thì truyền gì?  
   **Đáp án:** `shouldKeep.negate()`, với giả định predicate hợp lệ cho mọi phần tử.

4. Tại sao `removeIf` trên `Arrays.asList(1, 2, 3)` không thể xóa số 2?  
   **Đáp án:** danh sách đó không hỗ trợ thay đổi kích thước.

5. Predicate đã xóa một phần tử rồi ném lỗi ở phần tử tiếp theo. Có thể dựa vào hợp đồng chung để khẳng định dữ liệu được hoàn tác không?  
   **Đáp án:** không. Phải kiểm tra bảo đảm của cách triển khai và thiết kế quy trình riêng nếu cần tính nguyên tử.

Một điều kiện xóa tốt diễn đạt rõ đối tượng nào phải bị loại, xử lý đủ dữ liệu biên và không tạo thêm tác động khó kiểm soát. Trước khi gọi `removeIf`, xác nhận cả quy tắc điều kiện lẫn quyền sửa collection đang nắm giữ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-006">Chính sách phần tử null và giới hạn kiểu của từng cài đặt</a>

<details>
<summary>Xem chi tiết</summary>

# Chính sách phần tử null và giới hạn kiểu của từng cài đặt

## 1. Một giá trị có thể đúng kiểu nhưng vẫn không được chấp nhận

Xét hai collection cùng giữ chuỗi:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> names = new ArrayList<>();
Deque<String> jobs = new ArrayDeque<>();

names.add(null); // Được phép.
// jobs.add(null); // Biên dịch được, nhưng ném NullPointerException khi chạy.
```

Cả hai lời gọi đều hợp lệ về kiểu ở thời điểm biên dịch. Khác biệt là hợp đồng của đối tượng thực tế: `ArrayList` cho phép phần tử `null`, còn `ArrayDeque` không cho phép.

Khi làm việc với collection, có ba lớp câu hỏi:

1. **Kiểu khai báo cho phép truyền gì?** Ví dụ `List<String>` không cho thêm `Integer` bằng lời gọi có kiểm tra kiểu thông thường.
2. **Cài đặt chấp nhận giá trị nào?** Ví dụ `null` có thể bị cấm.
3. **Cấu trúc cần điều kiện bổ sung gì?** Ví dụ các phần tử phải so sánh được với nhau để lưu trong cây có thứ tự.

Chính sách `null` và giới hạn kiểu thuộc **hợp đồng của cài đặt hoặc factory/wrapper**, không chỉ thuộc tên giao diện `List`, `Set` hay `Map`.

Bài sử dụng API có trong Java 17. Mục tiêu là biết nơi nào kiểm tra trước khi thay cấu trúc dữ liệu, chẩn đoán ngoại lệ đúng nguyên nhân và thiết kế đầu vào rõ ràng.

## 2. Phân biệt ba vị trí xuất hiện null

### 2.1. Biến collection là null

```java
// Đoạn minh họa trong một phương thức; cần import java.util.List.
List<String> names = null;
// names.add("A"); // NullPointerException vì không có đối tượng collection.
```

Chưa có một collection nào để xét chính sách phần tử. Lỗi xuất hiện vì gọi phương thức qua tham chiếu `null`.

### 2.2. Collection tồn tại và chứa phần tử null

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> names = new ArrayList<>();
names.add(null);

System.out.println(names.size()); // 1
System.out.println(names.get(0)); // null
```

Danh sách không rỗng. Nó có một phần tử với giá trị `null`.

`null` không phải chuỗi `"null"`, không phải chuỗi rỗng `""` và cũng không phải “không có phần tử” trong cấu trúc này.

### 2.3. Phần tử tồn tại nhưng một trường bên trong là null

Một `List<Person>` có thể chứa một đối tượng `Person` khác `null` nhưng trường `name` của người đó là `null`. Collection thông thường không tự kiểm tra mọi thuộc tính của phần tử.

Tương tự, việc cấm phần tử `null` không bảo đảm phần tử thỏa mãn quy tắc nghiệp vụ như tên không rỗng, giá không âm hoặc dữ liệu đầy đủ. Đó là trách nhiệm của kiểu dữ liệu và lớp xử lý đầu vào.

## 3. Hợp đồng chung về giá trị không hợp lệ

Collections Framework cho phép cài đặt giới hạn phần tử, chẳng hạn cấm `null` hoặc chỉ nhận những giá trị có khả năng so sánh phù hợp.

Khi chèn phần tử không hợp lệ, các ngoại lệ thường gặp là:

| Ngoại lệ | Ý nghĩa cần xem xét |
| --- | --- |
| `NullPointerException` | Cài đặt không nhận `null` hoặc thao tác bên trong không xử lý được `null` |
| `ClassCastException` | Phần tử/khóa không tương thích với yêu cầu kiểu hoặc phép so sánh |
| `IllegalArgumentException` | Một thuộc tính của phần tử/đối số không đáp ứng ràng buộc được công bố |
| `UnsupportedOperationException` | Thao tác sửa không được hỗ trợ, dù giá trị có thể đúng kiểu và khác `null` |

Với truy vấn như `contains` hoặc thao tác xóa theo đối tượng, hợp đồng tổng quát có thể cho phép trả `false` hoặc ném ngoại lệ đối với phần tử không phù hợp. Không suy diễn rằng “cấm thêm `null`” đồng nghĩa mọi `contains(null)` đều có một hành vi giống nhau.

Xem [Collection API: restrictions on elements](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html). Khi lớp cụ thể có bảo đảm mạnh hơn, hãy dùng bảo đảm đó.

## 4. Bảng chính sách null của các collection thông dụng

Bảng sau mô tả việc **lưu phần tử**, không phải mọi truy vấn với đối số `null`.

| Cài đặt hoặc cách tạo | Nhận phần tử `null`? | Ghi chú |
| --- | --- | --- |
| `ArrayList` | Có | Có thể có nhiều vị trí `null` |
| `LinkedList` | Có | Dùng làm hàng đợi vẫn cần tránh nhập nhằng với kết quả `poll` |
| `HashSet` | Có | Tối đa một phần tử `null` |
| `LinkedHashSet` | Có | Tối đa một `null`, giữ thứ tự chèn |
| `TreeSet` với thứ tự tự nhiên | Không | `null` không so sánh được theo thứ tự tự nhiên |
| `TreeSet` với comparator | Tùy comparator | Có thể dùng comparator có chính sách `null` rõ ràng |
| `ArrayDeque` | Không | `add/offer` phần tử `null` bị từ chối |
| `PriorityQueue` | Không | Comparator chấp nhận `null` không bỏ được lệnh cấm này |
| `EnumSet` | Không | Phần tử phải thuộc đúng loại enum và khác `null` |
| `Arrays.asList(...)` | Có thể chứa `null` | Danh sách cố định kích thước, được chống lưng bởi mảng |
| `List.of`, `List.copyOf` | Không | Factory từ chối nguồn có phần tử `null` |
| `Set.of`, `Set.copyOf` | Không | Ngoài quy tắc `null` còn có quy tắc riêng về trùng |
| `Collections.unmodifiableList(backing)` | Có thể chứa `null` từ nguồn | Wrapper ngăn sửa, không làm sạch nội dung nguồn |

Nguồn cho các nhóm cài đặt:

- [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).
- [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).
- [ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html), [PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).
- [EnumSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html), [List factories](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#unmodifiable), [Set factories](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#unmodifiable).

Không biến bảng thành quy tắc theo tên họ: “mọi list đều nhận `null`” và “mọi cấu trúc có comparator đều nhận `null`” đều không đúng.

## 5. Map phải xét riêng khóa và giá trị

`Map` không kế thừa `Collection`, nhưng nằm trong cùng framework và thường được chọn cùng các cấu trúc collection. Với map, có hai vị trí cần kiểm tra độc lập.

| Cài đặt hoặc factory | Khóa `null` | Giá trị `null` |
| --- | --- | --- |
| `HashMap` | Cho phép một khóa `null` | Cho phép |
| `LinkedHashMap` | Cho phép một khóa `null` | Cho phép |
| `TreeMap` thứ tự tự nhiên | Không cho phép | Cho phép |
| `TreeMap` có comparator | Tùy comparator | Cho phép |
| `EnumMap` | Không cho phép | Cho phép |
| `ConcurrentHashMap` | Không cho phép | Không cho phép |
| `Map.of`, `Map.ofEntries`, `Map.copyOf` | Không cho phép | Không cho phép |

Tài liệu tương ứng: [HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html), [EnumMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumMap.html), [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html), [Map factories](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#unmodifiable).

### 5.1. get trả null có thể mang hai ý nghĩa

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Map<String, Integer> scores = new HashMap<>();
scores.put("pending", null);

System.out.println(scores.get("unknown"));       // null
System.out.println(scores.get("pending"));       // null
System.out.println(scores.containsKey("unknown")); // false
System.out.println(scores.containsKey("pending")); // true
```

Với map cho phép giá trị `null`:

- Không có khóa.
- Có khóa nhưng ánh xạ tới `null`.

Cả hai đều có thể làm `get` trả `null`. Nếu cần phân biệt trong mã một luồng này, kiểm tra sự tồn tại của khóa theo hợp đồng.

Không coi chuỗi `get` rồi `containsKey` là một giao dịch nguyên tử khi map có thể bị luồng khác sửa. Đồng thời là yêu cầu thiết kế riêng.

### 5.2. Vì sao phải kiểm tra khi đổi cài đặt?

Đổi `HashMap` thành `ConcurrentHashMap` có thể làm đầu vào từng được chấp nhận trở thành lỗi. Không chỉ có đặc tính nhiều luồng thay đổi; chính sách khóa/giá trị `null` cũng thay đổi.

Đổi `HashMap` thành `TreeMap` còn thêm yêu cầu về khả năng so sánh khóa. Kiểu khai báo `Map<K, V>` không xóa các khác biệt này.

## 6. null và giá trị báo “không có”

`Queue.poll` hoặc `Deque.poll` có thể dùng `null` để báo cấu trúc rỗng. Nếu một cài đặt còn cho phép lưu phần tử `null`, cùng một kết quả trở nên dễ hiểu nhầm.

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Queue<String> queue = new LinkedList<>();
queue.add(null);

System.out.println(queue.size()); // 1
System.out.println(queue.poll()); // null: vừa lấy phần tử null.
System.out.println(queue.size()); // 0
System.out.println(queue.poll()); // null: không có phần tử để lấy.
```

`LinkedList` cho lưu `null`, nhưng đó không phải lý do tốt để dùng `null` làm công việc thật trong hàng đợi. Chọn dữ liệu biểu diễn công việc rõ ràng hoặc quy định phần tử phải khác `null`.

Với `ArrayDeque`, chính sách cấm `null` làm kết quả `poll` khác `null` phân biệt rõ hơn với hàng đợi rỗng. Không mở rộng lập luận này thành quy tắc “mọi Queue đều cấm `null`”, vì ví dụ `LinkedList` cho thấy điều ngược lại.

Cũng không biến chuỗi `isEmpty` rồi `poll` thành cách kiểm tra an toàn nhiều luồng một cách tự động.

## 7. Cấu trúc có thứ tự: đúng kiểu khai báo chưa đủ

### 7.1. Thứ tự tự nhiên yêu cầu so sánh được

`TreeSet<E>` và khóa của `TreeMap<K, V>` cần được sắp theo thứ tự tự nhiên hoặc comparator. Với thứ tự tự nhiên, các phần tử/khóa phải có khả năng so sánh phù hợp với nhau.

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Set<Number> numbers = new TreeSet<>();
numbers.add(1);
// numbers.add(2.5); // ClassCastException: Integer và Double không có
                    // thứ tự tự nhiên so sánh chéo phù hợp trong cấu trúc này.
```

`Integer` và `Double` đều là `Number` nên được phép truyền vào API theo kiểu khai báo. Nhưng “cùng là Number” không tạo ra một thứ tự tự nhiên chung cho mọi loại số.

Nếu yêu cầu nghiệp vụ thật sự là sắp các dạng giá trị khác nhau, phải thiết kế comparator có thứ tự nhất quán trên toàn miền dữ liệu. Không chữa lỗi bằng raw type hoặc ép kiểu mù.

### 7.2. Comparator có thể định nghĩa vị trí của null

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Comparator<String> nullFirst =
        Comparator.nullsFirst(Comparator.naturalOrder());

NavigableSet<String> sorted = new TreeSet<>(nullFirst);
sorted.add("B");
sorted.add(null);
sorted.add("A");

System.out.println(sorted); // [null, A, B]
```

Comparator này xếp `null` trước các chuỗi khác `null`; chuỗi khác `null` được so theo thứ tự tự nhiên. Có thể dùng `nullsLast` nếu yêu cầu ngược lại. [Comparator.nullsFirst](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#nullsFirst(java.util.Comparator))

Comparator phải xử lý được các giá trị mà cấu trúc dự kiến nhận và phải tuân thủ hợp đồng so sánh. Không chỉ thêm một nhánh `null` rồi bỏ qua tính nhất quán còn lại.

Quan trọng: một comparator chấp nhận `null` chỉ có tác dụng nếu **bản thân cài đặt cho phép**. `PriorityQueue` vẫn cấm `null` ngay cả khi truyền comparator có chính sách này.

### 7.3. So sánh được và “bằng nhau” cần nhất quán

Trong `TreeSet`, kết quả so sánh bằng `0` được dùng để coi phần tử là cùng vị trí theo cấu trúc. Nếu comparator chỉ so độ dài chuỗi, `"An"` và `"Bo"` có thể bị coi là trùng dù `equals` khác nhau.

Đây là vấn đề khác với `null` nhưng thuộc cùng câu hỏi: “cài đặt yêu cầu gì ở phần tử?”. Đối với `Set`, thứ tự cần nhất quán với `equals` để giữ đúng hợp đồng chung của tập. Đối với map có thứ tự, phép so sánh khóa cũng cần được thiết kế phù hợp.

## 8. Generics kiểm tra gì và không kiểm tra gì?

### 8.1. Kiểm tra kiểu tại thời điểm biên dịch

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> words = new ArrayList<>();
words.add("hello");
// words.add(123); // Không biên dịch.
words.add(null);  // Kiểu tham chiếu cho phép null; ArrayList cũng chấp nhận.
```

Đối số kiểu generic là kiểu tham chiếu, không phải kiểu nguyên thủy. Vì vậy, dùng `List<Integer>`, không dùng `List<int>`. Tuy nhiên, `Integer` có thể là `null`; khi unboxing sang `int`, giá trị `null` sẽ gây `NullPointerException`.

Kiểu `String` không mã hóa một bảo đảm “khác `null`” trong hệ thống kiểu Java thông thường. Nếu API cần điều kiện đó, phải công bố và kiểm tra phù hợp.

### 8.2. Danh sách kiểu con không tự thành danh sách kiểu cha

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> integers = List.of(1, 2, 3);
// List<Number> numbers = integers; // Không biên dịch.

List<Number> numbers = new ArrayList<>();
numbers.addAll(integers);
numbers.add(2.5);

System.out.println(numbers); // [1, 2, 3, 2.5]
```

Nếu cho gán trực tiếp `List<Integer>` sang `List<Number>`, mã khác có thể thêm `Double` vào cùng danh sách vốn được kỳ vọng chỉ chứa `Integer`. Tính bất biến của tham số kiểu generic ngăn sai lệch đó.

`addAll` có chữ ký nhận nguồn `Collection<? extends E>` nên có thể sao chép các `Integer` vào đích chứa `Number`. Bản sao và phép gán tham chiếu là hai việc khác nhau.

### 8.3. Type erasure không tạo hàng rào kiểm tra phần tử ở mọi lần thêm

Với phần lớn generics Java, đối số kiểu bị xóa khỏi biểu diễn kiểu khi chạy. `new ArrayList<String>()` không giữ một hàng rào `String.class` riêng để tự kiểm tra mọi lời gọi raw vào `add`.

Nếu dùng raw type hoặc unchecked cast, có thể làm dữ liệu sai kiểu lọt vào rồi lỗi xuất hiện khi đọc. Không có mâu thuẫn với generics: mã đã đi qua ranh giới bỏ kiểm tra kiểu.

Các quy tắc về parameterized type, raw type, type erasure và heap pollution được mô tả trong [JLS Chapter 4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html), đặc biệt các mục 4.5, 4.6, 4.8 và 4.12.2.

## 9. Mã raw và checked collection

### 9.1. Lỗi có thể xuất hiện muộn tại chỗ đọc

```java
// Minh họa cố ý không an toàn; đặt trong một phương thức.
// Cần import java.util.*; trình biên dịch cảnh báo raw/unchecked.
List<String> words = new ArrayList<>();
List raw = words;
raw.add(123);

// String first = words.get(0); // ClassCastException khi chuyển kết quả sang String.
```

`ArrayList` không tự biết nó đang được mong đợi chứa `String` qua tham chiếu khác. Cảnh báo unchecked phải được xem là tín hiệu rủi ro, không phải phiền phức cần tắt đại trà.

### 9.2. Wrapper checked kiểm tra ngay tại ranh giới chèn

```java
// Minh họa khi phải tương tác mã cũ dùng raw type.
// Cần import java.util.*; raw.add là lời gọi cố ý không an toàn.
List<String> checked =
        Collections.checkedList(new ArrayList<>(), String.class);
List raw = checked;

// raw.add(123); // ClassCastException ngay khi chèn qua wrapper.
checked.add("A");
checked.add(null); // Được: backing ArrayList cho phép null.
```

`checkedList` cung cấp view kiểm tra kiểu lúc chạy:

- Kiểm tra dữ liệu đi qua các thao tác chèn/thay thế của view.
- Không tự cấm `null` nếu collection nguồn cho phép.
- Không tự sửa dữ liệu sai kiểu đã tồn tại trong nguồn.
- Không ngăn việc một tham chiếu khác chèn trực tiếp vào nguồn, bỏ qua wrapper.
- Không kiểm tra sâu tham số kiểu lồng nhau như “mọi List bên trong đều chứa String”.

Nó hữu ích khi buộc phải đi qua mã legacy, nhưng không thay thế thiết kế generic đúng và kiểm tra đầu vào nghiệp vụ. Xem [Collections.checkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#checkedList(java.util.List,java.lang.Class)).

## 10. Factory, wrapper và thao tác sao chép

### 10.1. Cùng là List nhưng chính sách khác nhau

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> nullable = new ArrayList<>();
nullable.add(null);

List<String> view = Collections.unmodifiableList(nullable);
System.out.println(view); // [null]

// List<String> snapshot = List.copyOf(nullable);
// Dòng trên ném NullPointerException vì nguồn chứa null.
```

“Không cho sửa” và “không cho `null`” là hai chính sách độc lập. Wrapper không cho sửa có thể quan sát dữ liệu `null` từ nguồn, trong khi `List.copyOf` từ chối phần tử đó khi tạo kết quả.

`Set.of` không nhận phần tử trùng trong các đối số được truyền; `Set.copyOf` có thể hợp nhất các phần tử bằng nhau của nguồn thành tập. Không suy diễn mọi quy tắc của factory `of` sang factory `copyOf` chỉ vì chúng cùng tạo kết quả không cho sửa.

### 10.2. Chú ý lời gọi được đánh giá từ trong ra ngoài

```java
// Minh họa một biểu thức sẽ ném NullPointerException.
// Cần import java.util.*.
// List<String> values = new ArrayList<>(List.of("A", (String) null));
```

`List.of` thất bại trước khi constructor `ArrayList` nhận dữ liệu. Điều này không chứng minh `ArrayList` cấm `null`.

Nếu thật sự cần tạo danh sách sửa được có `null`, dùng nguồn phù hợp:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> values =
        new ArrayList<>(Arrays.asList("A", null));
System.out.println(values); // [A, null]
```

### 10.3. null của mảng varargs khác phần tử null

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> oneNull = Arrays.asList((String) null);
System.out.println(oneNull.size()); // 1

// Arrays.asList((String[]) null); // NullPointerException: mảng nguồn là null.
```

Ép kiểu trong ví dụ làm ý định rõ ràng: truyền một phần tử `String` là `null`, hoặc truyền cả mảng là `null`. Xem [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

## 11. contains, remove và nguy cơ suy diễn từ truy vấn

`contains(Object)` và `remove(Object)` nhận `Object`, nên việc truyền đối số khác tham số kiểu không nhất thiết bị chặn lúc biên dịch.

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Collection<String> words = new ArrayList<>(List.of("one"));

System.out.println(words.contains(123)); // false trong ví dụ ArrayList này.
System.out.println(words.remove(123));   // false.
```

Một cấu trúc có thứ tự có thể cần so sánh đối số truy vấn với phần tử, dẫn tới `ClassCastException` với kiểu không phù hợp. Tương tự, truy vấn `null` trên cài đặt cấm `null` có thể bị từ chối theo hợp đồng.

Các kết luận nên tránh:

- “`contains(null)` trả `false` nên collection chắc chắn cho thêm `null`.”
- “Mọi phương thức nhận `Object` đều an toàn với mọi đối tượng.”
- “Thử thành công trên collection rỗng đủ chứng minh dữ liệu hợp lệ.”
- “Thêm một phần tử không so sánh được chưa lỗi thì thêm các phần tử tiếp theo cũng được.”

Cần đọc quy tắc theo từng thao tác. Một phép so sánh có thể chỉ xảy ra khi cấu trúc đã có dữ liệu, và các lớp có thể chủ động kiểm tra sớm hơn.

## 12. Thiết kế chính sách tại ranh giới ứng dụng

Nếu nghiệp vụ không chấp nhận phần tử `null`, hãy quyết định rõ từ đầu thay vì phụ thuộc tình cờ vào collection được dùng.

```java
// Minh họa phương thức trong một lớp; cần import java.util.*.
static List<String> validatedNames(Collection<String> source) {
    Objects.requireNonNull(source, "source");

    List<String> result = new ArrayList<>(source.size());
    for (String name : source) {
        result.add(Objects.requireNonNull(name, "name"));
    }
    return List.copyOf(result);
}
```

Phương thức này:

1. Từ chối collection nguồn `null`.
2. Từ chối từng phần tử `null` trong lúc dựng kết quả riêng.
3. Không thêm/xóa phần tử của nguồn.
4. Trả một danh sách không cho sửa, không nhận `null`.

Nó chưa kiểm tra tên rỗng hoặc chỉ gồm khoảng trắng. Nếu nghiệp vụ yêu cầu, bổ sung kiểm tra với thông báo thích hợp.

Đừng âm thầm loại `null` nếu vị trí hoặc số lượng phần tử mang ý nghĩa, chẳng hạn danh sách điểm theo thứ tự môn học. “Bỏ phần tử không hợp lệ” và “từ chối đầu vào” là hai chính sách khác nhau.

Với trường có ý nghĩa “chưa có giá trị”, có thể chọn một kiểu dữ liệu hoặc trạng thái tường minh thay vì nhét `null` vào cấu trúc mà các thao tác khác khó phân biệt. Không cần bọc mọi phần tử vào `Optional` theo một khuôn chung; lựa chọn phải phục vụ mô hình dữ liệu.

## 13. Chương trình hoàn chỉnh

Lưu thành `NullAndTypePolicyDemo.java`. Phần legacy cố ý dùng raw type để quan sát thời điểm lỗi; không nên sao chép cách đó vào mã ứng dụng bình thường.

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class NullAndTypePolicyDemo {
    private static void showRejection(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + ": accepted");
        } catch (NullPointerException ex) {
            System.out.println(label + ": NullPointerException");
        } catch (ClassCastException ex) {
            System.out.println(label + ": ClassCastException");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putThroughLegacyCode(List target, Object value) {
        target.add(value);
    }

    public static void main(String[] args) {
        List<String> nullable = new ArrayList<>();
        nullable.add(null);
        nullable.add("A");
        nullable.add(null);
        System.out.println("List: " + nullable);

        Set<String> unique = new LinkedHashSet<>(nullable);
        System.out.println("Set: " + unique);

        Deque<String> deque = new ArrayDeque<>();
        showRejection("Deque add null", () -> deque.add(null));
        System.out.println("Empty deque poll: " + deque.poll());

        NavigableSet<String> natural = new TreeSet<>();
        showRejection("Natural TreeSet add null", () -> natural.add(null));

        Comparator<String> nullFirst =
                Comparator.nullsFirst(Comparator.naturalOrder());
        NavigableSet<String> sorted = new TreeSet<>(nullFirst);
        sorted.add("B");
        sorted.add(null);
        sorted.add("A");
        System.out.println("Null-friendly TreeSet: " + sorted);

        Map<String, Integer> map = new HashMap<>();
        map.put(null, 7);
        map.put("missingValue", null);
        System.out.println("Null key value: " + map.get(null));
        System.out.println("Absent value: " + map.get("absent"));
        System.out.println("Present null value: " + map.get("missingValue"));
        System.out.println("Contains absent: " + map.containsKey("absent"));
        System.out.println("Contains missingValue: "
                + map.containsKey("missingValue"));

        Map<String, Integer> concurrent = new ConcurrentHashMap<>();
        showRejection("ConcurrentHashMap null key",
                () -> concurrent.put(null, 1));
        showRejection("ConcurrentHashMap null value",
                () -> concurrent.put("A", null));

        showRejection("List.of null", () -> List.of("A", (String) null));
        showRejection("List.copyOf nullable", () -> List.copyOf(nullable));

        List<String> fixed = Arrays.asList("A", null);
        System.out.println("Arrays.asList: " + fixed);
        List<String> view = Collections.unmodifiableList(nullable);
        System.out.println("Unmodifiable view contains null: "
                + view.contains(null));

        List<String> unsafe = new ArrayList<>();
        putThroughLegacyCode(unsafe, 123);
        showRejection("Read polluted List<String>", () -> {
            String value = unsafe.get(0);
            System.out.println(value);
        });

        List<String> checked =
                Collections.checkedList(new ArrayList<>(), String.class);
        showRejection("Checked legacy insert",
                () -> putThroughLegacyCode(checked, 123));
        checked.add(null);
        System.out.println("Checked allows backing null: " + checked);

        Collection<Integer> integers = List.of(1, 2, 3);
        List<Number> numbers = new ArrayList<>();
        numbers.addAll(integers);
        numbers.add(2.5);
        System.out.println("Numbers: " + numbers);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NullAndTypePolicyDemo.java
java NullAndTypePolicyDemo
```

Mã đã được kiểm tra bằng `--release 17` trên một JDK mới hơn. `@SuppressWarnings` chỉ đặt tại hàm legacy minh họa để tập trung output; nó không sửa tính không an toàn của raw type.

Stdout:

```text
List: [null, A, null]
Set: [null, A]
Deque add null: NullPointerException
Empty deque poll: null
Natural TreeSet add null: NullPointerException
Null-friendly TreeSet: [null, A, B]
Null key value: 7
Absent value: null
Present null value: null
Contains absent: false
Contains missingValue: true
ConcurrentHashMap null key: NullPointerException
ConcurrentHashMap null value: NullPointerException
List.of null: NullPointerException
List.copyOf nullable: NullPointerException
Arrays.asList: [A, null]
Unmodifiable view contains null: true
Read polluted List<String>: ClassCastException
Checked legacy insert: ClassCastException
Checked allows backing null: [null]
Numbers: [1, 2, 3, 2.5]
```

### Những gì ví dụ chứng minh

- `ArrayList` giữ được hai vị trí `null`; `LinkedHashSet` chỉ giữ một phần tử `null`.
- `ArrayDeque` cấm `null`; `poll` trên đối tượng rỗng trả `null`.
- `TreeSet` thứ tự tự nhiên cấm `null`; comparator được chọn có thể tạo thứ tự `null, A, B`.
- `HashMap` chấp nhận khóa và giá trị `null`. `get` không tự phân biệt khóa vắng với khóa ánh xạ tới `null`.
- `ConcurrentHashMap` từ chối cả hai loại `null`.
- `List.of` và `List.copyOf` từ chối nguồn có phần tử `null`.
- `Arrays.asList` giữ được `null`; wrapper không cho sửa không tự xóa `null` khỏi nguồn.
- Chèn sai kiểu qua raw `ArrayList` có thể chỉ lộ khi đọc thành `String`. Qua checked wrapper, lỗi bị phát hiện khi chèn.
- Chép các `Integer` vào `List<Number>` qua `addAll` hợp lệ; gán trực tiếp `List<Integer>` sang `List<Number>` là một chuyện khác.

Hàm `showRejection` chỉ dùng để quan sát ngoại lệ dự kiến. Nó không chứng minh rằng mọi nguồn đầu vào của ứng dụng đã được kiểm tra.

## 14. Lỗi phổ biến và cách kiểm tra

| Cách hiểu sai | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `List<String>` chắc chắn không có `null` | Generic không biểu đạt tính khác `null` | Công bố và kiểm tra điều kiện đầu vào |
| Mọi `List` đều nhận `null` | Factory như `List.of` cấm | Xem nguồn tạo đối tượng |
| Mọi `TreeSet` cấm `null` | Comparator có thể định nghĩa thứ tự cho `null` | Kiểm tra comparator và hợp đồng lớp |
| Comparator nhận `null` thì mọi collection nhận `null` | `PriorityQueue` có lệnh cấm riêng | Kiểm tra cả comparator lẫn cài đặt |
| `Map.get` trả `null` nghĩa là không có khóa | Có thể là giá trị `null` đã lưu | Dùng hợp đồng phân biệt phù hợp |
| Chuyển `HashMap` sang concurrent map không đổi đầu vào | `ConcurrentHashMap` cấm khóa/giá trị `null` | Kiểm tra chính sách trước khi thay |
| `List.of` lỗi trong constructor `ArrayList` nghĩa là `ArrayList` cấm `null` | Biểu thức nguồn đã lỗi trước | Xem thứ tự đánh giá biểu thức |
| Cùng là `Number` thì sắp tự nhiên được | Các loại số không tự so sánh chéo theo một thứ tự chung | Chọn kiểu dữ liệu/comparator phù hợp |
| Checked wrapper làm sạch toàn bộ nguồn | Nó bảo vệ thao tác đi qua wrapper | Kiểm tra nguồn và không giữ đường chèn bỏ qua |
| Bỏ cảnh báo unchecked là xử lý được vấn đề | Dữ liệu sai kiểu vẫn có thể lọt vào | Giảm raw type và kiểm soát ranh giới legacy |
| Không cho sửa đồng nghĩa không có `null` | Đây là hai đặc tính độc lập | Tài liệu hóa từng đặc tính |

## 15. Tự kiểm tra và thực hành

### Câu hỏi

1. `List<String> x = null;` khác `List<String> x = new ArrayList<>(); x.add(null);` thế nào?
2. Vì sao lời gọi `new ArrayDeque<String>().add(null)` biên dịch nhưng thất bại lúc chạy?
3. `HashSet` chứa tối đa bao nhiêu phần tử `null`?
4. Cần gì để `TreeSet<String>` nhận `null` một cách có chủ đích?
5. Comparator nhận `null` có đủ để `PriorityQueue` nhận `null` không?
6. Vì sao `get` trên `HashMap` có thể trả `null` dù khóa tồn tại?
7. `List<Number>` có thể giữ `Integer` và `Double`, vậy `TreeSet<Number>` thứ tự tự nhiên có tự hỗ trợ cả hai không?
8. Checked wrapper kiểm tra ở thời điểm nào và có tự cấm `null` không?
9. Vì sao `Arrays.asList((String) null)` khác `Arrays.asList((String[]) null)`?
10. Khi đổi cài đặt, ngoài kiểu generic cần kiểm tra những ràng buộc nào?

### Bài tập

- Tạo bảng thử riêng cho `add(null)`, `contains(null)` và `remove(null)`; phân biệt điều tài liệu bảo đảm với kết quả quan sát của cài đặt đang chạy.
- Dùng `TreeSet` với `nullsFirst` rồi đổi thành `nullsLast`; dự đoán thứ tự trước khi chạy.
- Tạo `HashMap` có khóa `"pending"` ánh xạ tới `null` và đối chiếu với khóa không tồn tại.
- Sửa một đoạn dùng raw type để không còn cảnh báo unchecked.
- Bọc ranh giới legacy bằng `Collections.checkedList` và kiểm chứng kiểu sai bị từ chối ngay lúc chèn.
- Viết hàm kiểm tra nguồn không có `null` trước khi chuyển sang collection có chính sách nghiêm ngặt hơn.
- Thử chuyển dữ liệu chứa `null` từ `ArrayList` sang `List.copyOf`; giải thích lỗi và chọn chính sách xử lý đúng nghiệp vụ thay vì loại bỏ dữ liệu âm thầm.

### Gợi ý đối chiếu

1. Trường hợp đầu không có đối tượng; trường hợp sau có danh sách chứa một phần tử `null`.
2. Kiểu tham chiếu cho phép truyền `null` nhưng lớp cài đặt từ chối giá trị đó.
3. Một.
4. Comparator phù hợp xử lý `null` và tuân thủ hợp đồng so sánh.
5. Không; cài đặt có quy tắc cấm riêng.
6. Map có thể chứa giá trị `null` cho khóa đó.
7. Không; cùng kiểu cha không tạo khả năng so sánh tự nhiên chéo.
8. Kiểm tra lúc dữ liệu được chèn qua view; `null` tùy backing collection.
9. Một trường hợp truyền một phần tử `null`, trường hợp kia truyền mảng nguồn `null`.
10. Chính sách `null`, khả năng so sánh, bằng nhau, loại phần tử/khóa, giới hạn giá trị và hành vi từng thao tác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-007">Phân biệt thay đổi cấu trúc, thay giá trị và sửa đối tượng phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thay đổi cấu trúc, thay giá trị và sửa đối tượng phần tử

Cùng được gọi là “sửa collection”, nhưng ba thao tác dưới đây tác động lên những lớp dữ liệu khác nhau:

```java
students.add(new Student("S3", "Chi"));
students.set(0, new Student("S4", "Dung"));
students.get(0).rename("Anh");
```

Một thao tác thêm phần tử vào danh sách; một thao tác thay phần tử ở vị trí có sẵn; thao tác còn lại sửa chính đối tượng đang được danh sách tham chiếu.

Phân biệt chúng giúp hiểu iterator, bản sao nông, view, collection không cho sửa và các lỗi với khóa có thể thay đổi. Nếu chỉ nhìn kết quả in ra hoặc chỉ nhìn `size()`, ta dễ bỏ qua điều thực sự đã thay đổi.

Bài dùng Java 17 và lấy `ArrayList` làm mô hình chính. Phần mở rộng sang `Map`, `Set` và các cấu trúc khác luôn cần đối chiếu hợp đồng cụ thể. Các đoạn Java ngắn là minh họa theo ngữ cảnh; chương trình đầy đủ cùng lệnh biên dịch và kết quả nằm ở phần thực nghiệm.

## 1. Vì sao cần ba khái niệm riêng?

Giả sử danh sách quản lý hai sinh viên. Các yêu cầu sau khác nhau:

| Yêu cầu | Điều thực sự cần làm |
| --- | --- |
| Tiếp nhận thêm sinh viên | Thêm một phần tử vào cấu trúc chứa |
| Thay sinh viên ở vị trí 0 bằng người khác | Đổi phần tử được gắn với một vị trí đang tồn tại |
| Sửa tên của sinh viên đang ở vị trí 0 | Đổi trạng thái của đối tượng đã được tham chiếu |
| Đổi thứ tự hiển thị | Sắp xếp lại quan hệ vị trí và phần tử |
| Đọc được danh sách nhưng không cho người ngoài sửa | Xác định lớp dữ liệu nào cần được bảo vệ |

Nếu người thiết kế nói “danh sách chỉ đọc”, cần hỏi thêm:

- Không được thêm hoặc xóa?
- Không được thay phần tử?
- Không được sửa thuộc tính của phần tử?
- Có được thấy thay đổi do bên khác thực hiện không?

Những câu hỏi này không thể được giải quyết bằng một nhãn “mutable” hoặc “immutable” thiếu ngữ cảnh.

## 2. Mô hình ba lớp: cấu trúc, tham chiếu và đối tượng

Với collection chứa đối tượng, hãy hình dung ba lớp:

```text
Biến students
    |
    v
Đối tượng danh sách
    vị trí 0 ----> đối tượng Student S1, tên "An"
    vị trí 1 ----> đối tượng Student S2, tên "Binh"
```

### 2.1. Lớp cấu trúc chứa

Danh sách quản lý các vị trí, kích thước và các tham chiếu phần tử. `ArrayList` còn có vùng lưu trữ mảng bên trong với sức chứa riêng.

Với map, cấu trúc quản lý các ánh xạ khóa–giá trị. Với tập hợp, nó quản lý các phần tử theo quy tắc bình đẳng, băm hoặc so sánh.

### 2.2. Lớp gắn phần tử với vị trí hoặc khóa

Một vị trí trong danh sách đang chứa tham chiếu đến đối tượng nào? Một khóa trong map đang gắn với giá trị nào?

Thay tham chiếu ở vị trí 0 có thể không đổi số phần tử, nhưng đối tượng được truy cập qua vị trí đó đã khác.

### 2.3. Lớp trạng thái của phần tử

Đối tượng `Student` có trường `name`. Gọi phương thức đổi tên tác động lên đối tượng này, không nhất thiết gọi bất kỳ phương thức sửa nào của danh sách.

Các lớp không tách rời hoàn toàn về ý nghĩa nghiệp vụ: đổi một trường có thể làm đổi kết quả `equals`, `hashCode`, thứ tự hoặc cách hiển thị. Tuy nhiên, đường tác động và cơ chế collection quan sát được vẫn khác nhau.

## 3. Thay đổi cấu trúc là gì?

### 3.1. Trường hợp điển hình: thêm và xóa phần tử

Với một `ArrayList` đang có hai phần tử:

```java
students.add(new Student("S3", "Chi"));
students.remove(0);
```

Lời gọi đầu thêm một vị trí phần tử; lời gọi sau loại một vị trí và làm các phần tử phía sau dịch chỉ số.

Đó là các thay đổi cấu trúc điển hình. Một iterator đã tạo trước đó có thể không còn duyệt đúng theo cấu trúc mà nó đang theo dõi.

Các thao tác như `addAll`, `removeAll`, `retainAll`, `removeIf` hoặc `clear` cũng có thể tác động lên cấu trúc nếu thực sự thêm hoặc xóa phần tử. Cần xét tác dụng cụ thể, không chỉ tên phương thức.

### 3.2. Không định nghĩa bằng “size có đổi không?”

`size()` giúp quan sát số phần tử logic, nhưng không phải định nghĩa tổng quát của thay đổi cấu trúc.

Ví dụ, chuỗi thao tác:

```java
students.add(new Student("S3", "Chi"));
students.remove(students.size() - 1);
```

đưa kích thước về như cũ. Điều đó không khôi phục hiệu lực của một iterator đã tồn tại trước hai lời gọi.

Với `ArrayList`, tài liệu còn nhắc đến việc thay đổi rõ ràng kích thước vùng mảng nền. Sức chứa và số phần tử là hai đại lượng khác nhau; không suy luận về hiệu lực iterator chỉ bằng so sánh `size()` trước và sau. Chi tiết được mô tả trong [ArrayList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

### 3.3. Khái niệm phụ thuộc cấu trúc và hợp đồng lớp

Với `HashMap`, thêm một ánh xạ mới hoặc xóa ánh xạ là thay đổi cấu trúc; chỉ thay giá trị của khóa đã có không được xem là thay đổi cấu trúc theo định nghĩa của lớp đó.

Một `LinkedHashMap` cấu hình theo thứ tự truy cập còn khác: thao tác truy cập có thể ảnh hưởng thứ tự duyệt và được xem là thay đổi cấu trúc. Vì vậy, không thể đưa ra quy tắc chung “gọi `get` luôn chỉ đọc, nên không bao giờ ảnh hưởng iterator”. Xem [LinkedHashMap: chế độ access-order và structural modification](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Ở mức lớp nền, `AbstractList` cũng diễn đạt thay đổi có thể làm xáo trộn cách duyệt. Điều cần học là **đọc định nghĩa của kiểu đang dùng**, thay vì coi một định nghĩa của `ArrayList` là định nghĩa cho toàn bộ framework.

## 4. Thay giá trị: giữ vị trí nhưng đổi phần tử ở đó

### 4.1. List.set không có nghĩa sửa đối tượng cũ

Đoạn minh họa:

```java
Student previous = students.set(0, new Student("S9", "Lan"));
```

Sau lời gọi:

- Vị trí 0 vẫn tồn tại.
- Kích thước danh sách không tăng hoặc giảm.
- Vị trí 0 tham chiếu đến đối tượng mới.
- Giá trị trả về là phần tử trước đó ở vị trí này.
- Đối tượng cũ không tự bị sửa thành đối tượng mới.

Có thể hình dung:

```text
Trước:
    students[0] ----> Student S1, tên "An"

Sau set:
    students[0] ----> Student S9, tên "Lan"
    previous    ----> Student S1, tên "An"
```

Với `ArrayList`, thay giá trị phần tử bằng `set` không phải thay đổi cấu trúc. Đó là đặc điểm cụ thể cần phân biệt với việc thêm hoặc xóa.

### 4.2. Kết quả đọc đã thay đổi dù không phải thay đổi cấu trúc

Một lời gọi `students.get(0)` sau `set` trả đối tượng khác so với trước.

Do đó:

- “Không đổi cấu trúc” không có nghĩa dữ liệu quan sát được không đổi.
- “Không có lỗi iterator” không có nghĩa đang duyệt một bản chụp cố định.
- “Không đổi số lượng” không có nghĩa thay đổi đó không quan trọng về nghiệp vụ.

Một hệ thống chấm điểm có thể giữ nguyên số sinh viên nhưng thay nhầm người ở một vị trí. Số lượng đúng không chứng minh nội dung đúng.

### 4.3. Với String hoặc Integer

`String` và các lớp bao như `Integer` là các giá trị bất biến. Khi muốn đổi một chuỗi hoặc một số trong danh sách, thông thường ta gắn giá trị mới vào vị trí đó:

```java
List<Integer> scores = new ArrayList<>(List.of(7, 8));
scores.set(0, scores.get(0) + 1);
```

Danh sách trở thành `[8, 8]`. Biểu thức trên không sửa đối tượng `Integer` đang mang giá trị 7 thành 8; nó tính giá trị mới rồi thay phần tử ở vị trí 0.

Điều này khác với gọi setter trên một đối tượng có thể thay đổi.

### 4.4. Không phải collection nào cũng có thao tác set

`set(index, value)` thuộc `List`, không thuộc hợp đồng chung của mọi `Collection`. Tập hợp không có vị trí phần tử theo chỉ số như danh sách.

Với map, thao tác thay giá trị theo khóa mang ý nghĩa tương ứng về liên kết, nhưng phải dùng hợp đồng map. Không chuyển cách gọi phương thức của list sang set hoặc map theo tên một cách máy móc.

## 5. Sửa đối tượng phần tử: tham chiếu giữ nguyên, trạng thái đổi

Xét:

```java
Student student = students.get(0);
student.rename("Anh");
```

Danh sách vẫn giữ tham chiếu đến cùng đối tượng. Phương thức `rename` thay trường tên bên trong đối tượng đó.

Hình dung:

```text
Trước:
    students[0] ----> Student S1, tên "An"

Sau rename:
    students[0] ----> cùng Student S1, tên "Anh"
```

Danh sách không được yêu cầu tăng số phần tử hoặc thay tham chiếu ở vị trí 0. Nếu in danh sách, chuỗi hiển thị có thể đổi vì `toString()` của phần tử đọc trạng thái mới.

### 5.1. Collection không tự quan sát mọi setter

Một `ArrayList` thông thường không tự nhận thông báo rằng đối tượng bên trong vừa đổi tên. Không có quy tắc chung khiến mọi thay đổi trường tự cập nhật bộ đếm sửa cấu trúc của collection.

Điều đó giải thích vì sao một setter có thể làm nội dung kết quả đổi mà iterator vẫn tiếp tục.

### 5.2. Một phần tử có thể được chia sẻ ở nhiều nơi

Nếu hai danh sách cùng chứa tham chiếu đến một `Student`, đổi tên qua danh sách thứ nhất có thể được nhìn thấy qua danh sách thứ hai.

Cũng có thể cùng một đối tượng xuất hiện ở hai vị trí của một list. Sửa đối tượng đó qua vị trí thứ nhất khiến đọc vị trí thứ hai cũng thấy trạng thái mới, vì không có hai đối tượng độc lập để sửa.

### 5.3. Sửa đối tượng có thể rất quan trọng dù collection không đổi cấu trúc

Đổi trường `name` chỉ ảnh hưởng hiển thị nếu nó không tham gia các quy tắc khác. Nhưng nếu trường bị đổi được dùng trong bình đẳng, băm hoặc thứ tự, collection có thể không còn duy trì được các giả định ban đầu.

Phải phân biệt:

- Thao tác có được bộ đếm sửa cấu trúc phát hiện hay không.
- Trạng thái sau thao tác có còn phù hợp hợp đồng collection và nghiệp vụ hay không.

Hai câu hỏi này độc lập.

## 6. Gán lại biến cục bộ không thay phần tử trong danh sách

Đây là một nhầm lẫn thường gặp:

```java
Student local = students.get(0);
local = new Student("S9", "Local");
```

Lời gọi `get` ban đầu đưa một tham chiếu vào biến `local`. Phép gán tiếp theo đổi riêng biến `local`, không sửa vị trí 0 của danh sách.

So sánh ba cách:

| Câu lệnh | Thứ được thay đổi |
| --- | --- |
| `local = new Student(...)` | Tham chiếu trong biến cục bộ |
| `students.set(0, new Student(...))` | Phần tử gắn với vị trí 0 |
| `local.rename("Anh")` | Trạng thái đối tượng mà local đang trỏ tới |

Điều này cũng đúng với biến vòng lặp `for-each`:

```java
for (Student student : students) {
    student = new Student("S9", "Replacement");
}
```

Đoạn trên không thay mọi phần tử trong danh sách. Nó chỉ gán lại biến vòng lặp ở từng lượt.

Nếu muốn thay các phần tử, dùng thao tác phù hợp như `ListIterator.set`, truy cập theo chỉ số trên kiểu hỗ trợ phù hợp, hoặc tạo một danh sách kết quả mới. Không dùng gán lại biến vòng lặp thay cho thao tác sửa collection.

## 7. Bảng phân biệt trên ArrayList

Bảng dưới giả định thao tác được hỗ trợ và đối số hợp lệ. Cột cấu trúc lấy `ArrayList` làm phạm vi, không áp dụng vô điều kiện cho mọi lớp.

| Thao tác | Kích thước logic | Tham chiếu phần tử | Trạng thái đối tượng cũ | Ý nghĩa |
| --- | --- | --- | --- | --- |
| `list.add(newValue)` | Tăng | Thêm phần tử | Không tự sửa | Thay đổi cấu trúc |
| `list.remove(i)` | Giảm | Bỏ phần tử ở vị trí | Không tự sửa hay hủy đối tượng | Thay đổi cấu trúc |
| `list.set(i, newValue)` | Giữ nguyên | Thay tại vị trí có sẵn | Không tự sửa | Thay phần tử, không phải thay đổi cấu trúc của ArrayList |
| `list.get(i).rename(...)` | Giữ nguyên | Giữ nguyên | Bị sửa bởi phương thức phần tử | Sửa trạng thái đối tượng |
| `local = newValue` | Giữ nguyên | Không sửa phần tử của list | Không tự sửa | Gán lại biến riêng |
| `list.get(i)` | Giữ nguyên | Giữ nguyên | Không tự sửa | Đọc phần tử trong cách dùng thông thường |

Bỏ tham chiếu khỏi list không có nghĩa đối tượng lập tức bị thu hồi bộ nhớ. Đối tượng có thể còn được biến khác hoặc collection khác tham chiếu; việc thu hồi bộ nhớ còn phụ thuộc khả năng tiếp cận của toàn bộ đồ thị đối tượng.

Thay vị trí bằng chính tham chiếu cũ cũng khác với thay bằng một đối tượng mới. Việc có lời gọi phương thức chưa đủ để kết luận nội dung nghiệp vụ đã khác.

## 8. Iterator liên quan thế nào đến ba loại thay đổi?

### 8.1. Vì sao thay đổi cấu trúc có thể làm iterator không còn hợp lệ?

Iterator đang giữ tiến trình duyệt: vị trí tiếp theo, phần tử vừa trả về và thông tin cần để phát hiện một số thay đổi không hợp lệ.

Một thao tác xóa bên ngoài iterator có thể làm các chỉ số dịch đi; thao tác thêm có thể làm tập phần tử mà iterator phải đi qua thay đổi. Cơ chế fail-fast của nhiều lớp dùng để phát hiện những tình huống như vậy.

`ConcurrentModificationException` có thể xảy ra ngay trong chương trình một luồng. Tên ngoại lệ không chứng minh có hai luồng.

### 8.2. set trên ArrayList không tạo bản chụp cho iterator

Với `ArrayList`, thay một vị trí bằng `set` không phải thay đổi cấu trúc. Iterator đọc phần tử sau đó có thể nhìn thấy phần tử mới ở vị trí tương ứng.

Nếu iterator đã trả đối tượng cũ trước khi `set`, biến bên ngoài đang giữ đối tượng cũ không tự chuyển sang trỏ đối tượng mới. Thời điểm đọc và việc chia sẻ tham chiếu vẫn quan trọng.

Không tổng quát hành vi này sang mọi loại iterator. Ví dụ, iterator dạng bản chụp của một lớp khác có hợp đồng riêng.

### 8.3. Sửa trường của phần tử không mặc nhiên bị fail-fast phát hiện

Setter của `Student` không phải lời gọi thêm hoặc xóa trên list. Iterator thường vẫn trả cùng đối tượng với trạng thái mới.

Điều đó không phải một bảo đảm rằng ứng dụng được phép sửa mọi trường trong mọi cấu trúc. Với tập băm, cây hoặc heap, những trường liên quan đến vị trí logic cần được xử lý rất thận trọng.

### 8.4. Thay đổi cấu trúc qua đúng iterator có thể được hỗ trợ

Nếu muốn xóa phần tử đang duyệt, có thể dùng:

```java
Iterator<Student> iterator = students.iterator();
while (iterator.hasNext()) {
    Student student = iterator.next();
    if (shouldRemove(student)) {
        iterator.remove();
    }
}
```

Đây là đoạn minh họa cần có điều kiện `shouldRemove` do ứng dụng định nghĩa. Nó không phải chương trình hoàn chỉnh.

Thao tác vẫn xóa phần tử và vẫn thay đổi cấu trúc. Điểm khác là thực hiện qua iterator đang quản lý tiến trình duyệt, theo hợp đồng cho phép.

`Iterator.remove` là thao tác tùy chọn. Cần có phần tử hợp lệ từ `next()` và tuân thủ giới hạn gọi; không phải iterator nào cũng hỗ trợ xóa.

Với `ListIterator`, `set` dùng để thay phần tử vừa duyệt, còn `add` chèn phần tử theo vị trí con trỏ. Các trạng thái hợp lệ và thao tác được hỗ trợ phải đọc ở [Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html) và [ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html).

### 8.5. Fail-fast là công cụ phát hiện lỗi

Không suy luận:

```text
không có ConcurrentModificationException
    ⇒ không có thay đổi
    ⇒ dữ liệu an toàn
    ⇒ an toàn giữa nhiều luồng
```

Chuỗi suy luận này sai ở cả ba bước.

Việc phát hiện có tính cố gắng tốt nhất, không phải khóa bảo vệ dữ liệu. Mã đúng phải tổ chức thao tác hợp lệ ngay từ đầu; không chờ ngoại lệ để quyết định thuật toán có đúng hay không.

## 9. Cùng kích thước nhưng iterator vẫn có thể bị ảnh hưởng

Ngoài ví dụ thêm rồi xóa, còn có thao tác thay đổi thứ tự hoặc cơ chế duyệt mà không đổi số lượng.

### 9.1. Sắp xếp và thay thế hàng loạt

`sort` làm thay đổi quan hệ vị trí và phần tử. `replaceAll` có thể thay nhiều phần tử.

Không được lấy quy tắc riêng “`ArrayList.set` không phải structural modification” rồi suy ra mọi phương thức giữ nguyên kích thước sẽ không làm iterator cũ mất hiệu lực.

Trong triển khai OpenJDK 17, `ArrayList.sort` và `ArrayList.replaceAll` cập nhật bộ đếm sửa đổi phục vụ kiểm tra iterator. Đây là chi tiết triển khai cần được phân biệt với quy tắc của một lời gọi `set`; xem [mã nguồn ArrayList trong OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/ArrayList.java).

Cách dùng rõ ràng là hoàn tất sắp xếp hoặc thay thế trước khi tạo iterator mới, thay vì tái sử dụng iterator đang có.

### 9.2. Map theo thứ tự truy cập

Một `LinkedHashMap` ở chế độ access-order có thể đổi thứ tự duyệt khi truy cập một ánh xạ. Số ánh xạ vẫn không đổi.

Bài học không phải “mọi `get` đều sửa cấu trúc”, mà là “tên phương thức và số phần tử chưa đủ để phân loại tác động nếu chưa biết cấu hình cùng lớp triển khai”.

### 9.3. modCount không phải số phiên bản nghiệp vụ

Ở một số lớp, bộ đếm như `modCount` hỗ trợ iterator. Nó không phải:

- Tổng số lần thay đổi dữ liệu nghiệp vụ.
- Mã giao dịch.
- Dấu vết mọi setter của phần tử.
- Cơ chế đồng bộ giữa luồng.
- API công khai để ứng dụng kiểm tra tính bất biến.

Đọc [AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html) giúp hiểu mục đích của bộ đếm, nhưng không nên thiết kế logic nghiệp vụ phụ thuộc vào trường bảo vệ của một lớp triển khai.

## 10. Khả năng sửa có nhiều tầng

### 10.1. final chỉ hạn chế gán lại biến

Đoạn minh họa:

```java
final List<String> names = new ArrayList<>();
names.add("An");
names.set(0, "Binh");
```

Hai lời gọi đều hợp lệ. `final` không làm đối tượng danh sách bất biến; nó ngăn gán lại chính biến `names` sang một list khác.

Tương tự, trường tham chiếu `final` trong một lớp không tự làm mọi đối tượng mà nó trỏ tới bất biến sâu.

### 10.2. Kích thước cố định không đồng nghĩa không cho sửa

Một list từ `Arrays.asList(array)` có kích thước cố định và gắn với mảng:

- Có thể thay phần tử bằng `set`.
- Không thể thêm hoặc xóa phần tử để đổi kích thước.
- Thay phần tử qua list hoặc mảng có thể được nhìn thấy qua phía còn lại.

Nó minh họa sự khác nhau giữa “không sửa kích thước” và “không được thay phần tử”.

### 10.3. List.of không cho sửa qua API danh sách

Một list tạo bằng `List.of(...)` không cho thêm, xóa hoặc thay phần tử bằng `set`.

Như vậy, `set` là thao tác không đổi cấu trúc đối với `ArrayList` nhưng vẫn có thể bị cấm trên một list khác. **Phân loại tác động và khả năng hỗ trợ thao tác là hai điều cần kiểm tra riêng.**

### 10.4. Unmodifiable không làm phần tử bất biến

Nếu một list không cho sửa chứa `Student` có setter:

```java
readOnlyStudents.get(0).rename("Anh");
```

lời gọi này có thể vẫn hợp lệ. Wrapper ngăn các phương thức sửa cấu trúc hoặc thay phần tử qua list, nhưng không biến đối tượng trả về từ `get` thành một kiểu chỉ đọc.

Nếu người gọi được phép lấy tham chiếu tới một đối tượng có thể sửa, họ có thể gọi phương thức của đối tượng đó theo quyền mà API cung cấp.

Tài liệu [Collection: unmodifiable collections và view](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html) phân biệt rõ collection không cho sửa với dữ liệu bất biến.

## 11. View, bản sao nông và ảnh chụp dữ liệu

### 11.1. View không cho sửa vẫn có thể nhìn thấy nguồn đổi

`Collections.unmodifiableList(source)` tạo góc nhìn không cho sửa qua wrapper, không tách dữ liệu khỏi `source`.

Nếu bên giữ nguồn gọi `source.add(...)`, kích thước đọc qua view có thể tăng. Nếu nguồn thay phần tử hoặc sửa đối tượng phần tử, view cũng có thể phản ánh thay đổi tương ứng.

Đây không phải lỗi của wrapper; đó là hợp đồng của một view sống theo dữ liệu nền.

### 11.2. Bản sao nông tách danh sách, vẫn chia sẻ phần tử

Với:

```java
List<Student> copy = new ArrayList<>(source);
```

- `copy` là đối tượng danh sách khác.
- Thêm hoặc xóa ở `source` không tự thêm hoặc xóa ở `copy`.
- `source.set(i, another)` không thay tham chiếu đang nằm ở `copy.get(i)`.
- Sửa đối tượng mà hai phía cùng tham chiếu có thể được thấy qua cả hai danh sách.

Không mâu thuẫn: cấu trúc đã tách, đối tượng phần tử vẫn chung.

### 11.3. List.copyOf không phải bản sao sâu

`List.copyOf(source)` cung cấp list không cho sửa với các phần tử của nguồn tại thời điểm gọi. Thay đổi về thêm, xóa hoặc thay phần tử của nguồn sau đó không làm kết quả tự đổi theo như một view.

Tuy nhiên, các phần tử vẫn có thể là những đối tượng dùng chung. Setter trên một phần tử có thể làm dữ liệu quan sát qua cả hai phía thay đổi.

Không dựa vào giả định `copyOf` luôn cấp phát một list mới khác tham chiếu; điều cần dựa vào là hợp đồng kết quả, quy tắc `null` và giới hạn không sao chép sâu.

### 11.4. Khi cần tách cả trạng thái phần tử

Cần xác định bản chụp nghiệp vụ phải giữ những dữ liệu nào, rồi tạo các đối tượng giá trị hoặc bản sao phần tử phù hợp.

Nếu một sinh viên chứa danh sách địa chỉ, sao chép đối tượng sinh viên nhưng tiếp tục dùng chung danh sách địa chỉ vẫn chưa tách toàn bộ trạng thái.

Một `record` có các trường tham chiếu cũng không tự sao chép sâu hoặc làm bất biến những đối tượng được tham chiếu. Thiết kế phần tử bất biến phải xét cả dữ liệu bên trong.

## 12. View một phần và tác động trở lại nguồn

Với `subList`, ta đang nhìn một vùng của list nền.

- `subList.set(...)` có thể thay phần tử tương ứng trong list nền khi thao tác được hỗ trợ.
- Xóa qua sublist có thể làm list nền giảm kích thước.
- Sửa đối tượng phần tử có thể nhìn thấy từ cả hai phía.
- Nếu list nền bị sửa cấu trúc ngoài đường đi được hợp đồng cho phép của view, không được tiếp tục coi sublist là một vùng dữ liệu độc lập còn hợp lệ.

Đừng diễn giải việc chưa có ngoại lệ là view chắc chắn còn đúng. Hợp đồng `List.subList` phải được kiểm tra khi nguồn có thể thay đổi; xem [List của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

Tương tự, `keySet`, `values` và `entrySet` của map là các góc nhìn dữ liệu. Việc thay giá trị của một ánh xạ khác với xóa khóa; một view đọc giá trị có thể thấy dữ liệu khác dù tập khóa vẫn giữ nguyên.

## 13. Với Set, Map và cấu trúc có thứ tự

### 13.1. HashSet: sửa thuộc tính tham gia băm

Giả sử phần tử dùng trường `code` trong `equals` và `hashCode`. Nếu đổi `code` sau khi đã thêm vào `HashSet`, đối tượng vẫn ở vị trí nội bộ được xác định theo trạng thái trước đó; tập không tự nhận setter rồi bố trí lại.

Hậu quả có thể là tra cứu, xóa hoặc quy tắc không trùng không còn hoạt động như mong đợi. Hợp đồng `Set` không bảo đảm hành vi khi phần tử bị thay đổi theo cách ảnh hưởng bình đẳng trong lúc còn thuộc tập; xem [Set của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

Không coi một lần thử cho kết quả cụ thể là bảo đảm tổng quát cho những phần tử đã vi phạm điều kiện này.

Cách thiết kế tốt là dùng thông tin định danh ổn định trong bình đẳng. Khi thật sự cần đổi trường liên quan, cần loại phần tử trước khi đổi, rồi thêm lại theo trạng thái mới và xử lý khả năng trùng. Chỉ thêm lại sau khi đã đổi mà không loại trạng thái cũ có thể không sửa được vấn đề.

### 13.2. Map: thay value khác với sửa key

Với `HashMap`:

```java
map.put(existingKey, anotherValue);
```

thay giá trị của khóa đã có không giống việc thêm một ánh xạ với khóa mới. Tập khóa có thể giữ nguyên trong khi giá trị quan sát được đã đổi.

Nhưng nếu sửa trạng thái của chính đối tượng đang làm khóa, nhất là các trường tham gia `equals` hoặc `hashCode`, ta gặp rủi ro tương tự phần tử của `HashSet`.

Khóa nên có giá trị ổn định. Sửa giá trị nghiệp vụ thường nên diễn đạt bằng thay value hoặc thao tác trên đối tượng value theo quy tắc sở hữu rõ ràng. Xem [HashMap: structural modification](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

### 13.3. TreeSet hoặc TreeMap: sửa trường dùng để so sánh

Cây được tổ chức theo quan hệ so sánh khi thêm phần tử hoặc khóa. Nếu đối tượng đổi trường dùng trong comparator, cây không tự sắp lại khi setter được gọi.

Đừng kết luận an toàn chỉ vì không có thay đổi số lượng hoặc không có lỗi iterator. Cần giữ tiêu chí so sánh ổn định trong thời gian đối tượng nằm ở cấu trúc.

Đây là ví dụ rõ về sự khác nhau giữa không thay đổi cấu trúc qua API và làm hỏng giả định mà cấu trúc đang dựa vào.

## 14. Chương trình Java hoàn chỉnh

Chương trình dưới đây đặt ba loại thay đổi cạnh nhau và quan sát hai kiểu chia sẻ: view dùng cùng dữ liệu nền, bản sao nông dùng khác list nhưng chung phần tử.

Lưu vào tệp `CollectionMutationDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class CollectionMutationDemo {
    static final class Student {
        private final String id;
        private String name;

        Student(String id, String name) {
            this.id = id;
            this.name = name;
        }

        void rename(String newName) {
            name = newName;
        }

        @Override
        public String toString() {
            return id + ":" + name;
        }
    }

    public static void main(String[] args) {
        List<Student> students = new ArrayList<>(List.of(
                new Student("S1", "An"),
                new Student("S2", "Binh")));

        List<Student> shallowCopy = new ArrayList<>(students);
        List<Student> readOnly = Collections.unmodifiableList(students);

        Iterator<Student> beforeSet = students.iterator();
        Student replaced = students.set(1, new Student("S3", "Chi"));

        System.out.println("size after set: " + students.size());
        System.out.println("replaced object: " + replaced);
        System.out.println("iterator after set: "
                + beforeSet.next() + ", " + beforeSet.next());

        students.get(0).rename("Anh");
        System.out.println("students after rename: " + students);
        System.out.println("shallow copy: " + shallowCopy);

        Student local = students.get(0);
        local = new Student("S9", "Local");
        System.out.println("local variable: " + local);
        System.out.println("list first element: " + students.get(0));

        readOnly.get(0).rename("Annie");
        System.out.println("rename through readOnly: " + students.get(0));

        try {
            readOnly.set(0, new Student("S8", "Blocked"));
        } catch (UnsupportedOperationException ex) {
            System.out.println("readOnly set: blocked");
        }

        students.add(new Student("S4", "Dung"));
        System.out.println("source size after add: " + students.size());
        System.out.println("readOnly size after add: " + readOnly.size());
        System.out.println("shallow copy size: " + shallowCopy.size());

        List<String> codes = new ArrayList<>(List.of("A", "B"));
        Iterator<String> stale = codes.iterator();
        codes.add("C");
        try {
            stale.next();
        } catch (ConcurrentModificationException ex) {
            System.out.println("stale iterator: ConcurrentModificationException");
        }

        Iterator<String> remover = codes.iterator();
        while (remover.hasNext()) {
            if (remover.next().equals("B")) {
                remover.remove();
            }
        }
        System.out.println("after iterator remove: " + codes);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CollectionMutationDemo.java
java CollectionMutationDemo
```

Kết quả:

```text
size after set: 2
replaced object: S2:Binh
iterator after set: S1:An, S3:Chi
students after rename: [S1:Anh, S3:Chi]
shallow copy: [S1:Anh, S2:Binh]
local variable: S9:Local
list first element: S1:Anh
rename through readOnly: S1:Annie
readOnly set: blocked
source size after add: 3
readOnly size after add: 3
shallow copy size: 2
stale iterator: ConcurrentModificationException
after iterator remove: [A, C]
```

### 14.1. Theo dõi thay phần tử bằng set

`students.set(1, ...)` thay sinh viên ở vị trí 1 từ `S2` sang `S3`. Danh sách vẫn có hai phần tử.

Biến `replaced` vẫn giữ đối tượng `S2:Binh`. Trong ví dụ `ArrayList` này, iterator tạo trước `set` duyệt được phần tử mới ở vị trí chưa đi qua.

### 14.2. Theo dõi sửa trạng thái đối tượng

Đổi tên phần tử đầu thành `Anh` được nhìn thấy từ `students` và `shallowCopy`, vì hai list cùng giữ tham chiếu đến sinh viên `S1`.

Trong khi đó, phần tử thứ hai ở `shallowCopy` vẫn là `S2:Binh`. Lời gọi `set` trên nguồn đã thay tham chiếu tại một vị trí của nguồn, không thay tham chiếu của bản sao.

### 14.3. Theo dõi gán lại biến riêng

Gán `local` sang `S9:Local` không thay `students.get(0)`. Hai biểu thức không còn trỏ cùng đối tượng sau phép gán.

### 14.4. Theo dõi giới hạn của view không cho sửa

`readOnly.get(0).rename(...)` vẫn sửa sinh viên. Nhưng `readOnly.set(...)` bị wrapper chặn.

Khi thêm sinh viên qua `students`, kích thước `readOnly` tăng theo, còn kích thước `shallowCopy` vẫn là 2.

### 14.5. Theo dõi thay đổi cấu trúc và iterator

Phần `codes` cố ý tạo một iterator cũ, sau đó thêm phần tử bằng list rồi gọi `next()`. Với `ArrayList` trong thực nghiệm một luồng này, lời gọi phát hiện thay đổi và ném `ConcurrentModificationException`.

Ví dụ dùng ngoại lệ để quan sát cơ chế, không dùng nó như bảo đảm đồng bộ hoặc như cách điều khiển một thuật toán đúng.

Cuối chương trình tạo một iterator mới, xóa `B` qua chính iterator đó và thu được `[A, C]`. Thao tác xóa vẫn sửa cấu trúc, nhưng thực hiện đúng đường đi mà iterator hỗ trợ.

Lớp `Student` trong chương trình chỉ phục vụ thí nghiệm tham chiếu và trạng thái; nó không được đưa vào cấu trúc yêu cầu bình đẳng hoặc băm theo mã nghiệp vụ.

## 15. Từ phân biệt khái niệm đến thiết kế API

### 15.1. Quy định điều gì được phép đổi

Một API trả `List<Student>` cần nói rõ:

- Người nhận được thêm hoặc xóa không?
- Được thay phần tử không?
- Được gọi phương thức thay đổi trạng thái sinh viên không?
- Kết quả là view sống theo nguồn hay dữ liệu ở một thời điểm?
- Nguồn hoặc phần tử có được chia sẻ với tác vụ khác không?

Nếu cần một bản chụp không thể bị người nhận thay đổi, chỉ bọc list bằng `unmodifiableList` có thể chưa đủ. Cần thiết kế cả phần tử và quyền sở hữu dữ liệu.

### 15.2. Chọn kiểu dữ liệu theo ranh giới cập nhật

Có thể chọn:

- Đối tượng có thể sửa trong nội bộ nơi có kiểm soát sở hữu.
- Đối tượng giá trị bất biến làm khóa hoặc phần tử cần bình đẳng ổn định.
- Bản chụp dữ liệu dành cho bên đọc.
- Phương thức cập nhật rõ ràng thay vì để bên ngoài giữ tham chiếu và tự gọi setter.

Lựa chọn phụ thuộc nhu cầu, không có yêu cầu mọi đối tượng đều phải bất biến.

### 15.3. Không dùng non-structural như giấy phép truy cập đa luồng

Hai luồng cùng thay phần tử bằng `set` có thể không gặp ngoại lệ cấu trúc nhưng vẫn có tranh chấp hoặc vấn đề hiển thị bộ nhớ.

Hai luồng cùng sửa trường của một phần tử cũng không được bảo vệ chỉ vì collection bao ngoài hỗ trợ đồng thời. Tính an toàn của cấu trúc chứa và tính an toàn của các đối tượng được chứa là hai phạm vi riêng.

Cần cơ chế đồng bộ, sở hữu theo luồng, truyền dữ liệu hoặc bất biến phù hợp. Không suy luận về đa luồng từ một chương trình một luồng chạy thành công.

## 16. Cách phân tích một thay đổi trong mã thực tế

Khi thấy một dòng có vẻ “sửa collection”, lần lượt hỏi:

1. Dòng này gọi phương thức của collection, của phần tử hay chỉ gán biến?
2. Collection cụ thể và cấu hình của nó là gì?
3. Có thêm hoặc xóa phần tử, ánh xạ hay đổi cơ chế duyệt không?
4. Chỉ thay phần tử ở một vị trí hoặc giá trị của khóa đã có?
5. Hay chỉ sửa trạng thái bên trong đối tượng đang được tham chiếu?
6. Có ai khác giữ tham chiếu tới cùng collection hoặc phần tử không?
7. Có view, iterator hoặc bản sao nông đang tồn tại không?
8. Thuộc tính bị sửa có tham gia băm, bình đẳng hoặc thứ tự không?
9. Thao tác có được hỗ trợ trên collection hiện tại không?
10. Có yêu cầu an toàn giữa nhiều luồng hoặc tính nhất quán nghiệp vụ không?

Cách đọc này tránh gộp mọi thay đổi vào một loại rồi đoán hành vi từ `size()` hoặc một ngoại lệ.

## 17. Bài tập tự kiểm chứng

### Bài 1: Phân loại ba thao tác

Tạo lại danh sách có hai sinh viên trước mỗi trường hợp:

- Thêm sinh viên thứ ba.
- Thay sinh viên ở vị trí 1.
- Đổi tên sinh viên ở vị trí 1.

Với từng trường hợp, ghi:

- Số phần tử trước và sau.
- Tham chiếu tại vị trí 1 có giữ nguyên không?
- Đối tượng ban đầu có bị sửa không?
- Một biến đang giữ đối tượng ban đầu sẽ quan sát gì?

Đáp án định hướng: thêm làm cấu trúc tăng; `set` thay liên kết tại vị trí; setter đổi trạng thái cùng đối tượng.

### Bài 2: Hai list, một phần tử chung

Tạo một `Student`, thêm cùng đối tượng đó vào hai list. Đổi tên qua list thứ nhất rồi thay phần tử của list thứ nhất bằng sinh viên khác.

Phải quan sát được hai kết quả khác nhau:

1. Đổi tên đối tượng chung được thấy ở cả hai list.
2. Thay phần tử của list thứ nhất không thay phần tử đang nằm ở list thứ hai.

### Bài 3: Bản sao nông và view

Từ một list nguồn, tạo:

- `new ArrayList<>(source)`.
- `Collections.unmodifiableList(source)`.
- `List.copyOf(source)`.

Sau đó thực hiện riêng từng loại thay đổi trên nguồn: thêm phần tử, thay phần tử, sửa đối tượng phần tử. Ghi nhận bên nào thấy thay đổi và giải thích bằng đồ thị tham chiếu.

Lưu ý `List.copyOf` không chấp nhận phần tử `null`; dùng dữ liệu hợp lệ để thí nghiệm tập trung vào việc chia sẻ.

### Bài 4: Kích thước bằng nhau nhưng iterator cũ

Tạo iterator của `ArrayList`, thêm rồi xóa phần tử để kích thước cuối bằng kích thước ban đầu. Sau đó thử duyệt bằng iterator cũ.

Giải thích vì sao so sánh kích thước không chứng minh iterator còn hợp lệ. Mục tiêu là hiểu thao tác không đúng hợp đồng, không phải tìm cách làm ngoại lệ biến mất.

### Bài 5: Thiết kế phần tử làm khóa

Một lớp có `id`, `name`, `email`. Hãy chọn trường dùng để nhận diện khi đối tượng làm khóa và quy định trường nào được sửa.

Giải thích hậu quả nếu đổi trường tham gia `hashCode` khi khóa còn ở map. Thiết kế lại bằng định danh ổn định hoặc đối tượng khóa bất biến, thay vì dựa vào may mắn của một lần tra cứu.

## 18. Những kết luận cần tránh

- “Không đổi size thì không đổi cấu trúc.”
- “Không structural nên không đổi nội dung.”
- “Không có ConcurrentModificationException nên mã đúng.”
- “List không cho sửa thì phần tử cũng không cho sửa.”
- “Sao chép list là sao chép mọi đối tượng bên trong.”
- “Gán lại biến lấy từ get sẽ cập nhật vị trí trong list.”
- “Xóa phần tử khỏi list là hủy ngay đối tượng.”
- “Collection an toàn giữa các luồng thì phần tử bên trong cũng an toàn.”
- “Định nghĩa của ArrayList áp dụng giống nhau cho mọi collection.”

Năng lực cần đạt là mô tả được **cái gì đổi, ai giữ tham chiếu tới nó, hợp đồng nào điều chỉnh thay đổi và các bên đọc sẽ thấy gì**. Khi bốn câu hỏi này rõ ràng, các hành vi về iterator, view, sao chép và bất biến trở nên có thể giải thích và kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-contracts-008">Hợp đồng API so với chi tiết cài đặt có thể đổi giữa các JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng API so với chi tiết cài đặt có thể đổi giữa các JDK

Khi học collection, ta thường vừa đọc Javadoc vừa xem mã nguồn JDK. Hai nguồn này trả lời hai câu hỏi liên quan nhưng khác nhau:

- **Hợp đồng API:** chương trình được phép yêu cầu gì và có thể dựa vào điều gì?
- **Chi tiết cài đặt:** một phiên bản thư viện cụ thể đang thực hiện yêu cầu đó bằng cách nào?

Phân biệt được hai lớp thông tin giúp viết chương trình vẫn đúng khi đổi cách triển khai, nâng cấp JDK hoặc đổi nhà cung cấp bản phân phối. Nó cũng giúp đọc mã nguồn sâu mà không biến một quan sát hiện tại thành quy tắc vĩnh viễn của Java.

Ví dụ, `ArrayList` bảo đảm truy cập theo chỉ số và mô tả một số đặc tính hiệu năng. Nhưng tỷ lệ tăng mảng chính xác không phải điều ứng dụng được quyền cố định. `HashMap` lưu ánh xạ theo khóa, nhưng không bảo đảm thứ tự duyệt mà ta tình cờ quan sát khi in nó.

Bài này sử dụng Java 17 làm mốc API cho ví dụ. Khi nhắc đến khác biệt phiên bản, cần xác định đó là thay đổi có tài liệu hay chỉ là khả năng mà hợp đồng để mở.

## 1. Hợp đồng API gồm nhiều hơn tên phương thức

Một chữ ký như `boolean add(E e)` mới chỉ cho biết cách gọi và kiểu kết quả. Để dùng đúng, còn cần biết:

| Thành phần hợp đồng | Câu hỏi cần đọc |
| --- | --- |
| Ý nghĩa thao tác | Thêm vào đâu, tìm theo quy tắc nào, thay dữ liệu hay tạo kết quả mới? |
| Điều kiện đầu vào | Có chấp nhận `null`, chỉ số biên hoặc kiểu phần tử này không? |
| Giá trị trả về | Là phần tử, giá trị cũ, số lượng hay chỉ báo có thay đổi? |
| Ngoại lệ | Khi nào đầu vào bị từ chối hoặc thao tác không được hỗ trợ? |
| Thứ tự | Giữ thứ tự chèn, thứ tự chỉ số, thứ tự comparator hay không cam kết? |
| Khả năng sửa | Có được thêm, xóa, thay phần tử không? |
| Quan hệ với dữ liệu khác | Là bản sao hay view gắn với nguồn? |
| Đồng thời | Có bảo đảm an toàn, nguyên tử hoặc cách quan sát thay đổi không? |
| Hiệu năng được công bố | Có bảo đảm độ phức tạp và giả định đi kèm không? |

Không phải mọi API đều có cùng mức cam kết ở từng mục. Nếu tài liệu không hứa một thuộc tính, chương trình không nên tự nâng quan sát thành lời hứa.

Chẳng hạn, hai lần chạy có thể cùng cho một thứ tự `HashMap`; điều đó chỉ chứng minh hai lần chạy ấy cho cùng thứ tự. Nó không tạo ra hợp đồng “map luôn sắp theo khóa”.

## 2. Đọc đúng tầng hợp đồng

### 2.1. Interface quy định mô hình chung

`List`, `Set`, `Queue` và `Map` diễn đạt những mô hình khác nhau. Một phương thức nhận `List<Integer>` có thể duyệt theo thứ tự danh sách mà không cần biết phần tử được lưu trong mảng hay các nút liên kết.

Nhưng `List` có các thao tác sửa tùy chọn. Chỉ vì kiểu khai báo có phương thức `add` không có nghĩa mọi đối tượng `List` nhận vào đều hỗ trợ `add`.

```java
List<String> first = new ArrayList<>();
List<String> second = List.of("A");
```

Cả hai biến có kiểu `List<String>`, nhưng không có cùng quyền sửa. Nếu một phương thức cần thay đổi danh sách nhận vào, hợp đồng của chính phương thức ấy phải nói rõ yêu cầu hoặc tự tạo một bản có thể sửa.

Interface cũng không quy định mọi chi phí truy cập giống nhau. Một vòng lặp dùng `get(i)` nhiều lần có thể hoạt động khác về thời gian trên `ArrayList` và `LinkedList`. [Java 17: List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)

### 2.2. Lớp cụ thể có thể đưa ra bảo đảm mạnh hơn

Khi chủ động chọn `ArrayList`, ta được dựa vào các thuộc tính đã công bố của chính lớp đó, chẳng hạn hỗ trợ các thao tác sửa thông thường và một số đặc điểm hiệu năng.

Điều này không mâu thuẫn với lời khuyên khai báo theo interface:

```java
List<String> names = new ArrayList<>();
```

Biến vẫn giữ đối tượng `ArrayList`; cách khai báo không xóa đặc điểm của đối tượng. Tuy nhiên, khi truyền biến qua một ranh giới API chỉ nói “nhận `List`”, bên nhận không nên giả định mọi lời gọi trong tương lai đều truyền `ArrayList`.

Nếu thật sự cần một khả năng đặc thù như `ensureCapacity`, có thể chọn kiểu cụ thể ở phần mã sở hữu dữ liệu. Đó là phụ thuộc có chủ đích vào API công khai, khác với đọc trường riêng bằng reflection.

### 2.3. Phương thức tạo có hợp đồng riêng

Một phương thức trả `List<T>` không tự hứa lớp cụ thể hoặc quyền sửa của kết quả. Phải đọc hợp đồng của chính phương thức tạo:

| Cách tạo | Điều có thể dựa vào |
| --- | --- |
| `new ArrayList<>(source)` | Tạo đối tượng `ArrayList` chứa các phần tử nguồn theo thứ tự duyệt nguồn |
| `Arrays.asList(array)` | Danh sách kích thước cố định, gắn với mảng được truyền vào |
| `List.of(...)` | Danh sách không sửa được, có các hạn chế đầu vào đã công bố |
| `List.copyOf(source)` | Danh sách không sửa được, không phản ánh việc thêm/xóa sau đó của nguồn |
| `Collections.unmodifiableList(source)` | View không cho sửa qua chính nó, vẫn gắn với nguồn |

Không gom tất cả thành “một danh sách immutable”. View có thể thay đổi nội dung quan sát được nếu nguồn bị sửa; một danh sách không sửa cấu trúc cũng không tự đóng băng các đối tượng phần tử. [Java 17: Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)), [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List))

## 3. Cách đọc Javadoc có hệ thống

Khi mở tài liệu một lớp hoặc phương thức, nên đọc theo thứ tự:

1. Xác nhận đúng phiên bản Java.
2. Đọc mô tả lớp/interface trước, vì quy tắc chung có thể không lặp ở từng phương thức.
3. Đọc mô tả phương thức, tham số và giá trị trả về.
4. Đọc ngoại lệ, điều kiện tùy chọn và ghi chú `null`.
5. Theo các liên kết đến phương thức kế thừa hoặc đặc tả liên quan.
6. Phân biệt giải thích cách dùng với mô tả triển khai.
7. Đọc `Since` nếu cần hỗ trợ một mức Java tối thiểu.

Tài liệu được sinh từ Javadoc có các phần tham số, trả về, ngoại lệ và lịch sử phiên bản. Đừng chỉ đọc tên phương thức hoặc một ví dụ rồi bỏ qua điều kiện áp dụng. [Đặc tả comment Javadoc của JDK 17](https://docs.oracle.com/en/java/javase/17/docs/specs/javadoc/doc-comment-spec.html)

### Các phần ghi chú có ý nghĩa khác nhau

Trong tài liệu JDK, có thể gặp các phần:

- **API Note:** giải thích cho người sử dụng, nêu tình huống hoặc cách dùng.
- **Implementation Requirements:** mô tả yêu cầu/cách thực hiện gắn với phần triển khai được tài liệu hóa, chẳng hạn một phương thức mặc định.
- **Implementation Note:** giải thích lựa chọn hoặc đặc điểm triển khai.

Không đọc mọi đoạn như thể nó ràng buộc mọi lớp triển khai interface. Ví dụ, `Collection.removeIf` mô tả thuật toán mặc định dựa trên iterator. Một lớp ghi đè `removeIf` không vì thế phải dùng đúng cùng cấu trúc vòng lặp.

Ngược lại, cũng không bỏ qua một bảo đảm chỉ vì nó nói về hiệu năng hoặc cấu trúc. Nếu tài liệu công khai của lớp đã nêu một đặc tính với phạm vi cụ thể, đó là thông tin để lựa chọn lớp trong phạm vi ấy. [Java 17: Collection.removeIf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate))

## 4. ArrayList: bảo đảm công khai và điều để mở

Tài liệu Java 17 mô tả `ArrayList` là cách triển khai danh sách bằng mảng có thể thay đổi dung lượng. Một số thao tác như `get`, `set` và `size` có thời gian hằng số; thêm phần tử có chi phí hằng số khấu hao.

Tài liệu đồng thời nói chi tiết chính sách tăng dung lượng không được quy định, ngoài bảo đảm chi phí khấu hao.

| Phát biểu | Đánh giá |
| --- | --- |
| `get(i)` trả phần tử ở chỉ số hợp lệ `i` | Có thể dựa vào |
| Thêm cuối giữ thứ tự danh sách | Có thể dựa vào |
| Dung lượng đủ chứa số phần tử hiện có | Thuộc mô hình được công bố |
| Mỗi lần đầy, mảng phải tăng đúng một tỷ lệ cố định | Không phải cam kết của API |
| Mảng thật luôn được cấp phát ngay tại đúng thời điểm quan sát được | Không nên suy từ API nếu không có bảo đảm tương ứng |
| `size()` bằng số ô đã dành trước | Sai ý nghĩa API |

Ví dụ:

```java
ArrayList<String> values = new ArrayList<>(100);
System.out.println(values.size()); // 0
```

Tham số dung lượng không tạo 100 phần tử. Nếu cần tối ưu dựa trên số phần tử dự kiến, dùng constructor hoặc `ensureCapacity`; không phải lấy công thức tăng mảng trong mã nguồn để suy ra dữ liệu nghiệp vụ.

Có thể học công thức đang được một bản OpenJDK sử dụng để hiểu chi phí sao chép. Khi ghi chú, hãy gắn nó với bản nguồn cụ thể và giữ nó ở vai trò giải thích, không phải điều kiện để chương trình trả kết quả đúng. [Java 17: ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

## 5. HashMap: không biến thứ tự quan sát thành quy tắc

`HashMap` công khai rằng không bảo đảm thứ tự các ánh xạ. Thứ tự tình cờ trông giống thứ tự số, thứ tự thêm hoặc một thứ tự ổn định vẫn không phải một yêu cầu đối với lần chạy khác.

Đoạn kiểm thử sau phụ thuộc sai:

```java
// Ví dụ sai về kỳ vọng, không phải bảo đảm của HashMap.
assert map.keySet().toString().equals("[A, B, C]");
```

Nếu yêu cầu chỉ là các ánh xạ đúng, kiểm tra bằng bình đẳng của map hoặc từng ánh xạ. Nếu sản phẩm cần một báo cáo sắp theo khóa, tạo một phép chiếu có thứ tự rõ ràng:

```java
Map<String, Integer> sorted = new TreeMap<>(map);
System.out.println(sorted);
```

Nếu cần giữ thứ tự chèn, xem xét `LinkedHashMap` theo hợp đồng của nó. Mỗi lựa chọn diễn đạt một yêu cầu khác nhau; không dùng một `HashMap` rồi hy vọng một tập khóa cụ thể sẽ cho thứ tự mong muốn. [Java 17: HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

### Một thay đổi lịch sử đã được ghi nhận

Tài liệu cải tiến Collections của Java SE 8 mô tả việc dùng cây cân bằng ở các vùng băm có nhiều khóa va chạm, nhằm cải thiện hiệu năng. Tài liệu cũng cảnh báo việc này có thể làm thay đổi thứ tự duyệt của `HashMap`/`HashSet` trong một số tình huống.

Đây là minh chứng cụ thể cho việc một thay đổi bên trong vẫn có thể phù hợp API nhưng làm lộ lỗi của chương trình đã phụ thuộc vào điều không được bảo đảm. Nó không có nghĩa mọi lần nâng JDK đều thay thứ tự, và cũng không có nghĩa mọi vùng băm luôn là cây. [Oracle: cải tiến Collections trong Java SE 8](https://docs.oracle.com/javase/8/docs/technotes/guides/collections/changes8.html)

Các ngưỡng chuyển cấu trúc, phép trộn bit và hình dạng trường nội bộ hữu ích khi nghiên cứu một bản nguồn. Không dùng chúng để nhận biết loại dữ liệu hoặc quyết định quyền truy cập, nghiệp vụ hay tính duy nhất của khóa.

## 6. Không suy lớp cụ thể từ kiểu List trả về

Một lỗi phổ biến là quan sát lớp lúc chạy rồi ép kiểu theo quan sát đó:

```java
List<String> result = Stream.of("A", "B")
        .collect(Collectors.toList());

// Không được hợp đồng Collectors.toList bảo đảm:
ArrayList<String> assumed = (ArrayList<String>) result;
```

`Collectors.toList()` không bảo đảm kiểu triển khai, khả năng sửa, khả năng tuần tự hóa hoặc an toàn đồng thời của danh sách kết quả. Một bản JDK đang trả lớp nào không biến lớp ấy thành kiểu được hứa.

Nếu cần một `ArrayList` có thể sửa, yêu cầu nó trực tiếp:

```java
ArrayList<String> editable = Stream.of("A", "B")
        .collect(Collectors.toCollection(ArrayList::new));

editable.add("C");
```

Nếu đã có một `List` và chỉ cần một bản có thể sửa, `new ArrayList<>(result)` cũng diễn đạt rõ ý định. [Java 17: Collectors.toList và toCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toList())

### Stream.toList là một hợp đồng khác

`stream.toList()` trong Java 17 trả danh sách không sửa được. Việc viết ngắn hơn không làm nó đồng nghĩa với `collect(Collectors.toList())`.

```java
List<String> fixed = Stream.of("A", "B").toList();
fixed.add("C"); // UnsupportedOperationException
```

Dù tên gọi đều có “toList”, phải đọc từng phương thức. Đồng thời không dựa vào tên lớp nội bộ, khả năng tuần tự hóa hoặc nhận dạng tham chiếu của kết quả nếu tài liệu không bảo đảm. [Java 17: Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList())

## 7. Đối tượng không sửa được: nội dung và nhận dạng khác nhau

Với các factory như `List.of`, điều cần dùng thường là nội dung và hợp đồng không sửa được. Tên lớp nội bộ, việc hai lần gọi có dùng lại một đối tượng hay không, hoặc cách chia lớp theo số phần tử không phải yêu cầu mà người gọi nên đặt.

Ví dụ nên kiểm tra:

```java
List<String> actual = List.of("A", "B");
boolean sameContent = actual.equals(List.of("A", "B"));
```

Không dùng `actual == anotherList` để kiểm tra nội dung. Các đối tượng được tài liệu mô tả là value-based cũng không phù hợp để làm khóa đồng bộ dựa trên nhận dạng.

`List.copyOf(source)` không hứa luôn cấp phát một đối tượng mới. Nếu nguồn đã phù hợp, cách triển khai có thể tránh sao chép không cần thiết. Điều được yêu cầu là kết quả đáp ứng hợp đồng, bao gồm không phản ánh các thay đổi cấu trúc sau đó của nguồn, chứ không phải `result != source` phải luôn đúng. [Java 17: các factory và copyOf của List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)

Cũng đừng suy từ “không phản ánh thêm/xóa ở nguồn” thành bản sao sâu. Nếu phần tử là đối tượng có thể sửa, các danh sách vẫn có thể tham chiếu cùng đối tượng phần tử.

## 8. Một ví dụ về thuật toán có thể đổi nhưng kết quả phải giữ

Tài liệu `Arrays` giải thích các mô tả thuật toán trong lớp được xem là ghi chú triển khai; có thể thay thuật toán nếu vẫn tuân thủ đặc tả. Ví dụ, sắp xếp mảng đối tượng phải giữ tính ổn định khi API quy định điều đó, nhưng không nhất thiết phải dùng một thuật toán cụ thể.

Phân biệt hai yêu cầu:

- “Các phần tử bằng nhau theo comparator giữ thứ tự tương đối ban đầu” là một tính chất kết quả có thể được bảo đảm.
- “Phương thức phải chia mảng theo đúng các đoạn có kích thước này” là một lựa chọn thuật toán, trừ khi hợp đồng nói rõ.

Đối với collection, cùng cách đọc ấy giúp tránh nhầm thuật toán sắp, cách dồn mảng hoặc cách tìm phần tử với điều mà bên gọi được bảo đảm. [Java 17: ghi chú triển khai của Arrays](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html)

“Chi tiết cài đặt có thể thay đổi” không có nghĩa thư viện được tự do bỏ tính ổn định hoặc đổi ý nghĩa giá trị trả về đã công bố. Chính ranh giới hợp đồng xác định phần nào được phép tối ưu.

## 9. Ngoại lệ và đồng thời cũng phải đọc đúng phạm vi

### 9.1. Fail-fast không phải cơ chế đồng bộ

Iterator của nhiều collection thông thường được mô tả là fail-fast. Nhưng việc ném `ConcurrentModificationException` là phát hiện theo khả năng, không phải bảo đảm phát hiện mọi sửa đổi không đồng bộ.

Chương trình không được dựa vào lập luận:

```java
// Lập luận sai:
// Nếu iterator không ném lỗi, dữ liệu chắc chắn đã được truy cập an toàn.
```

Nếu một bản JDK thay thời điểm kiểm tra hoặc tối ưu vòng duyệt, chương trình đã vi phạm quy tắc chia sẻ dữ liệu không trở thành đúng chỉ nhờ từng chạy qua. Hãy thiết kế quyền sở hữu dữ liệu, khóa hoặc cấu trúc đồng thời theo yêu cầu. [Java 17: ArrayList, giới hạn fail-fast](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

### 9.2. Một ngoại lệ không hứa thời điểm hoặc thông điệp tùy ý

Khi API nêu một loại ngoại lệ, vẫn cần đọc điều kiện phát sinh. Đừng suy thêm rằng:

- Mọi đối tượng không hợp lệ đều bị từ chối ở cùng một bước.
- Thông điệp ngoại lệ luôn có cùng văn bản.
- Khi một thao tác lỗi, mọi thay đổi trước đó chắc chắn được hoàn tác.
- Một collection không cho sửa phải luôn chấp nhận thao tác không tạo thay đổi.

Với đầu vào không được hỗ trợ, tài liệu `Collection` còn cho phép một số truy vấn ném ngoại lệ hoặc cho kết quả theo các điều kiện tùy chọn. Những trường hợp này cần được đọc tại API cụ thể, không đoán từ một phiên bản đang dùng. [Java 17: Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

Khi viết kiểm thử, chỉ so chuỗi thông báo lỗi nếu chính ứng dụng hoặc một hợp đồng cần kiểm chứng đã cam kết chuỗi ấy. Đối với JDK, thường nên kiểm tra loại ngoại lệ và trạng thái được đặc tả.

## 10. Đọc mã nguồn để hiểu, không để tự mở rộng lời hứa

Đọc nguồn JDK rất có giá trị khi muốn biết:

- Vì sao một thao tác tốn thời gian hoặc cấp phát nhiều bộ nhớ.
- Vòng lặp và cấu trúc dữ liệu hiện tại hoạt động thế nào.
- Một tối ưu hóa đã được thêm vào phiên bản nào.
- Lỗi cụ thể có liên quan đến thay đổi triển khai hay không.

Quy trình nên giữ hai ghi chú riêng:

| Ghi chú | Ví dụ cách viết |
| --- | --- |
| Điều ứng dụng được dựa vào | “Báo cáo cần sắp theo khóa; dùng một cấu trúc có thứ tự khóa.” |
| Quan sát triển khai | “Trong bản nguồn đang phân tích, thao tác này đi qua các nút của cấu trúc nội bộ theo cách sau...” |

Với quan sát triển khai, ghi rõ nhà cung cấp/bản phân phối, phiên bản đầy đủ, tag hoặc commit nguồn và điều kiện thử. Không lấy nhánh `master` hiện tại để giải thích chắc chắn cho một chương trình chạy trên JDK cũ mà chưa đối chiếu.

Việc một trường là `private`, tên lớp ở gói nội bộ hay một hằng số xuất hiện trong nguồn không khiến nó trở thành API được hỗ trợ. Truy cập bằng reflection hoặc cấu hình mở module cũng không tự tạo cam kết tương thích cho trường đó.

Không cần loại bỏ mọi kiến thức về triển khai khỏi bài học. Cần chỉ rõ vai trò của nó: cơ chế giải thích, dữ liệu chẩn đoán hoặc kết quả đo; không trình bày như quy tắc bắt buộc cho mọi JDK.

## 11. Kiểm thử theo hợp đồng thay vì theo tình cờ

### Những điều nên kiểm tra

- Nội dung danh sách và thứ tự mà hợp đồng danh sách yêu cầu.
- Tập khóa/giá trị hoặc các ánh xạ đúng của map.
- Khả năng sửa khi phương thức của chính ứng dụng cam kết kết quả có thể sửa.
- Hành vi view hoặc bản sao theo nguồn tạo đã chọn.
- Các điều kiện biên và loại ngoại lệ được đặc tả.
- Bất biến nghiệp vụ trước và sau thao tác.

### Những kỳ vọng cần loại bỏ nếu không có cam kết riêng

- Tên lớp nội bộ của kết quả factory.
- Thứ tự `HashMap` hoặc `HashSet`.
- Dung lượng riêng, số lần tăng mảng hoặc ngưỡng chuyển cây.
- Số lần gọi hàm phụ nếu API không cam kết.
- Thời gian chính xác tính bằng mili giây trên mọi máy.
- Việc một thao tác không đồng bộ “chắc chắn sẽ ném lỗi”.

Nếu yêu cầu sản phẩm cần một điều mà API hiện tại không hứa, hãy thay thiết kế: chọn kiểu có hợp đồng phù hợp, tạo bản sao rõ ràng hoặc đặt một lớp API do ứng dụng sở hữu với bảo đảm đã được thực hiện và kiểm thử.

Không nên sửa kiểm thử bằng cách chấp nhận mọi kết quả. Phải xác định điều nào là yêu cầu thực và biến nó thành một bảo đảm rõ trong thiết kế.

## 12. Nâng JDK: phân biệt khả dụng API với hành vi triển khai

Một API mới chỉ có ở phiên bản cao hơn là vấn đề khác với thay đổi thuật toán bên trong của một API cũ.

Các bước thực tế:

1. Xác định mức Java thấp nhất ứng dụng hỗ trợ.
2. Biên dịch với cấu hình phát hành phù hợp.
3. Chạy kiểm thử trên các runtime được hỗ trợ.
4. So tài liệu API của phiên bản nguồn và đích khi có khác biệt.
5. Đo lại những luồng nhạy cảm với độ trễ, bộ nhớ hoặc tải cao.
6. Khi phát hiện thay đổi, kiểm tra chương trình có dựa vào điều không được bảo đảm không.
7. Nếu vẫn có dấu hiệu vi phạm hợp đồng, tạo ví dụ tái hiện nhỏ và đối chiếu bản sửa lỗi/tài liệu của JDK.

Lệnh `javac --release 17` giúp kiểm tra mã với mức ngôn ngữ và API công khai tương ứng Java 17. Nó không làm lệnh `java` đang trỏ tới runtime khác tự biến thành Java 17.

Có thể kiểm tra công cụ đang dùng bằng:

```shell
javac --version
java --version
```

Kết quả phiên bản phụ thuộc môi trường nên không có một stdout cố định để chép làm kỳ vọng cho mọi máy.

Một bộ kiểm thử chạy thành công trên một runtime là bằng chứng cho lần kiểm thử ấy, không phải bằng chứng rằng mọi trường hợp không được đặc tả đã trở thành tương thích. Ngược lại, không nên mặc định mọi khác biệt đều là lỗi ứng dụng; cần kiểm tra đúng hợp đồng và dữ liệu đầu vào.

## 13. Chương trình Java 17 hoàn chỉnh

Chương trình sau chỉ in kết quả dựa trên hành vi được API bảo đảm. Lưu vào `ApiContractDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApiContractDemo {
    private static int total(List<Integer> values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }

    public static void main(String[] args) {
        List<Integer> arrayList = new ArrayList<>(List.of(3, 1, 2));
        List<Integer> linkedList = new LinkedList<>(List.of(3, 1, 2));
        System.out.println("array total = " + total(arrayList));
        System.out.println("linked total = " + total(linkedList));
        System.out.println("same list contents = " + arrayList.equals(linkedList));

        List<String> factory = List.of("A", "B");
        try {
            factory.add("C");
        } catch (UnsupportedOperationException expected) {
            System.out.println("List.of is unmodifiable");
        }

        List<String> collected =
                Stream.of("A", "B").collect(Collectors.toList());
        System.out.println("collected contents = " + collected);

        ArrayList<String> editable =
                Stream.of("A", "B")
                      .collect(Collectors.toCollection(ArrayList::new));
        editable.add("C");
        System.out.println("explicit editable list = " + editable);

        List<String> streamList = Stream.of("A", "B").toList();
        try {
            streamList.add("C");
        } catch (UnsupportedOperationException expected) {
            System.out.println("Stream.toList is unmodifiable");
        }

        Map<String, Integer> counts = new HashMap<>();
        counts.put("B", 2);
        counts.put("A", 1);
        System.out.println("explicit sorted keys = " + new TreeMap<>(counts));

        String[] sourceArray = {"A", "B"};
        List<String> fixed = Arrays.asList(sourceArray);
        fixed.set(0, "Z");
        System.out.println("backing array = " + Arrays.toString(sourceArray));
        try {
            fixed.add("C");
        } catch (UnsupportedOperationException expected) {
            System.out.println("Arrays.asList is fixed-size");
        }

        List<String> original = new ArrayList<>(List.of("A"));
        List<String> snapshot = List.copyOf(original);
        original.add("B");
        System.out.println("original = " + original);
        System.out.println("copy contents = " + snapshot);

        ArrayList<String> reserved = new ArrayList<>(100);
        System.out.println("reserved list size = " + reserved.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ApiContractDemo.java
java ApiContractDemo
```

Stdout:

```text
array total = 6
linked total = 6
same list contents = true
List.of is unmodifiable
collected contents = [A, B]
explicit editable list = [A, B, C]
Stream.toList is unmodifiable
explicit sorted keys = {A=1, B=2}
backing array = [Z, B]
Arrays.asList is fixed-size
original = [A, B]
copy contents = [A]
reserved list size = 0
```

Cách đọc minh chứng:

1. `total` chỉ cần duyệt một danh sách số nguyên không chứa `null`; cả hai cách lưu đều cho tổng 6.
2. `List.equals` so nội dung và thứ tự, không yêu cầu cùng lớp triển khai.
3. `List.of` và `Stream.toList` từ chối việc thêm.
4. Kết quả `Collectors.toList` chỉ được đọc theo nội dung; chương trình không thử suy lớp hay quyền sửa từ quan sát.
5. Khi cần danh sách có thể sửa, chương trình yêu cầu `ArrayList` rõ ràng bằng `toCollection`.
6. Map băm được chuyển sang `TreeMap` trước khi in, nên thứ tự hiển thị có căn cứ.
7. `Arrays.asList` phản ánh thay đổi giá trị vào mảng nguồn nhưng không cho tăng kích thước.
8. `List.copyOf` không phản ánh việc thêm phần tử vào nguồn sau đó.
9. Dung lượng dự kiến 100 không làm kích thước danh sách thành 100.

Chương trình cố ý không in tên lớp nội bộ, không đọc capacity bằng reflection và không xác nhận thứ tự trực tiếp của `HashMap`.

## 14. Tự kiểm tra cách phân loại một phát biểu

| Phát biểu | Cách đánh giá |
| --- | --- |
| “`List` có thứ tự theo vị trí” | Thuộc hợp đồng mô hình danh sách |
| “Mọi `List` đều thêm được” | Sai; có thao tác sửa tùy chọn và danh sách không sửa được |
| “`Collectors.toList` luôn trả `ArrayList`” | Không được phương thức bảo đảm |
| “`Stream.toList` trong Java 17 trả danh sách không sửa được” | Có thể dựa vào |
| “`HashMap` đang in tăng dần nên nó là map có thứ tự” | Suy luận sai từ quan sát |
| “`ArrayList` có thể thay chính sách tăng dung lượng nhưng vẫn đáp ứng chi phí khấu hao đã công bố” | Phù hợp ranh giới giữa hợp đồng và triển khai |
| “Đổi thuật toán sắp cho phép bỏ tính ổn định mà API đã hứa” | Sai; tối ưu phải giữ đặc tả |
| “Không ném `ConcurrentModificationException` chứng minh truy cập đồng thời đúng” | Sai; fail-fast không phải đồng bộ |

## 15. Tự luyện

1. Một phương thức của ứng dụng phải trả danh sách cho người gọi thêm phần tử. Có nên chỉ gọi `Collectors.toList()` và dựa vào lớp quan sát được không?  
   **Gợi ý:** yêu cầu rõ một cấu trúc có thể sửa, chẳng hạn `toCollection(ArrayList::new)` hoặc bản sao `ArrayList`.

2. Kiểm thử thất bại sau nâng JDK vì thứ tự khóa của `HashMap` khác trước. Cần hỏi gì trước tiên?  
   **Gợi ý:** sản phẩm có thật sự yêu cầu thứ tự không; nếu có, thiết kế đã chọn API bảo đảm thứ tự đó chưa?

3. `List.copyOf(source) == source` đôi lúc đúng. Có đủ để kết luận phương thức không tạo kết quả đúng hợp đồng không?  
   **Đáp án:** không. Hợp đồng không buộc mọi lần gọi cấp phát một đối tượng khác.

4. Đọc nguồn thấy một tỷ lệ tăng dung lượng. Cần ghi chú thế nào cho đúng?  
   **Gợi ý:** gắn với bản nguồn cụ thể và phân biệt nó với bảo đảm hiệu năng công khai.

5. Tại sao cần cả biên dịch theo `--release` và chạy kiểm thử trên runtime đích?  
   **Đáp án:** chúng kiểm tra những khía cạnh khác nhau; mức API khi biên dịch không lựa chọn thay runtime sẽ thực thi.

Khi dùng một kiến thức về collection để quyết định thiết kế, hãy truy ngược xem nó đến từ hợp đồng công khai, ghi chú triển khai hay một lần đo. Cách phân loại này cho biết điều gì có thể giữ ổn định trong chương trình và điều gì cần xác minh lại khi môi trường thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)
