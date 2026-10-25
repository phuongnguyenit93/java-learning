<a id="back-to-top"></a>

# Hiệu năng

## Menu
- [So sánh độ phức tạp truy cập, tìm kiếm, thêm, xóa và duyệt](#performance-001)
- [Chi phí khấu hao, trường hợp trung bình và trường hợp xấu](#performance-002)
- [Dung lượng dự phòng, nút liên kết và bộ nhớ cho từng phần tử](#performance-003)
- [Boxing, cấp phát đối tượng, thu gom rác và tính cục bộ bộ nhớ](#performance-004)
- [Ảnh hưởng của hàm băm và Comparator](#performance-005)
- [Tránh duyệt LinkedList bằng get theo chỉ số](#performance-006)
- [Giảm vòng lặp lồng bằng Set hoặc chỉ mục Map](#performance-007)
- [Đo bằng JMH với dữ liệu, làm nóng và phép so sánh phù hợp](#performance-008)

## <a id="performance-001">So sánh độ phức tạp truy cập, tìm kiếm, thêm, xóa và duyệt</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh độ phức tạp truy cập, tìm kiếm, thêm, xóa và duyệt

## 1. Bắt đầu từ thao tác cụ thể

Một cấu trúc dữ liệu không có một “độ phức tạp chung”. Cần hỏi: đang gọi phương thức nào, đã biết gì về phần tử cần xử lý, và thao tác đó lặp bao nhiêu lần?

Ví dụ, với một danh sách:

- Biết chỉ số `i`: lấy phần tử bằng `get(i)`.
- Biết giá trị `x`: tìm bằng `contains(x)` hoặc `indexOf(x)`.
- Biết vị trí hiện tại qua iterator: có thể xóa phần tử vừa đọc.
- Chỉ biết “xóa phần tử có giá trị x”: trước hết phải tìm ra nó.

Hai yêu cầu cuối có thể cùng tạo một kết quả nhưng chi phí khác nhau. Bài này so sánh các cấu trúc phổ biến trong Java 17: `ArrayList`, `LinkedList`, `ArrayDeque`, `PriorityQueue`, các tập hợp và các ánh xạ dựa trên bảng băm hoặc cây. Các cấu trúc đồng thời và phép toán hàng loạt cần mô hình riêng.

## 2. Đọc ký hiệu và giả định

Gọi:

- `n`: số phần tử đang có; với `Map`, là số cặp khóa–giá trị.
- `c`: dung lượng bảng băm, tức số ngăn chứa (bucket).
- `i`: chỉ số hợp lệ trong danh sách.
- Một lần đọc/ghi tham chiếu, so sánh, `equals` và `hashCode` được tạm coi là O(1). Phần 7 giải thích khi giả định này không phù hợp.

| Ký hiệu | Ý nghĩa tăng trưởng khi dữ liệu lớn |
| --- | --- |
| O(1) | Số bước không tăng theo n |
| O(log n) | Mỗi bước loại bỏ một phần đáng kể miền tìm kiếm |
| O(n) | Có thể phải xử lý số phần tử tỉ lệ với n |
| O(n log n) | Thường gặp khi sắp xếp hoặc lấy lần lượt n phần tử từ heap |
| O(n²) | Thường xuất hiện khi lặp n lần một thao tác tuyến tính |

Big O mô tả tốc độ tăng trưởng và bỏ qua hệ số hằng. O(1) vẫn có thể gồm nhiều bước; O(n) có thể rất nhanh khi n nhỏ. Dùng log n trong bảng là cách viết thông dụng cho n đủ lớn; không lấy log 0 để diễn giải một collection rỗng.

Phân biệt ba cách đánh giá:

1. **Trường hợp xấu nhất:** cận chi phí của một thao tác khi gặp đầu vào bất lợi.
2. **Trung bình/kỳ vọng:** cần giả định về dữ liệu, chẳng hạn mã băm phân bố tốt.
3. **Khấu hao (amortized):** chia tổng chi phí của một chuỗi thao tác cho số thao tác; không phải lấy trung bình xác suất của đầu vào.

Chẳng hạn, thêm cuối `ArrayList` có chi phí khấu hao O(1). Một lần thêm khi mảng đầy vẫn có thể cần O(n) để chuyển phần tử sang mảng lớn hơn. Ngược lại, O(1) kỳ vọng của tra cứu bảng băm dựa vào chất lượng phân bố khóa. Hai nhận định này có điều kiện khác nhau.

## 3. Danh sách: chỉ số, giá trị và vị trí iterator

Các ô không ghi “khấu hao” dưới đây là cận trường hợp xấu nhất cho thao tác đơn, với giả định ở phần 2.

| Thao tác | ArrayList | LinkedList |
| --- | --- | --- |
| `get(i)`, `set(i, x)` | O(1) | O(n); đi từ đầu hoặc cuối gần hơn |
| `contains(x)`, `indexOf(x)` | O(n) | O(n) |
| Thêm cuối bằng `add(x)` | O(1) khấu hao; một lần tăng mảng O(n) | O(1) |
| Thêm đầu bằng `add(0, x)` | O(n) | O(1) |
| Thêm vào vị trí bên trong bằng `add(i, x)` | O(n) | O(n), gồm bước tìm vị trí |
| Xóa cuối bằng `remove(size() - 1)` | O(1) | O(1) |
| Xóa đầu bằng `remove(0)` | O(n) | O(1) |
| Xóa tại vị trí bên trong bằng `remove(i)` | O(n) | O(n), gồm bước tìm vị trí |
| Xóa theo giá trị bằng `remove(Object)` | O(n) | O(n) |
| Duyệt đủ n phần tử bằng iterator | O(n) | O(n) |

### 3.1. Vì sao ArrayList truy cập nhanh nhưng chèn giữa chậm?

`ArrayList` giữ phần tử trong mảng tham chiếu. Biết chỉ số là biết ngay ô mảng cần đọc. Ngược lại, chèn vào vị trí `i` phải dịch các phần tử phía sau để tạo chỗ; xóa tại `i` phải lấp khoảng trống.

Nếu bỏ qua tăng dung lượng, số phần tử cần dịch gần bằng:

- Thêm tại `i`: `n - i`.
- Xóa tại `i`: `n - i - 1`.

Vì vậy, xóa phần tử cuối khác rõ xóa phần tử đầu dù cùng gọi `remove(int)`. `set(i, x)` chỉ thay một tham chiếu, không phải thao tác chèn. Chính sách tăng dung lượng cụ thể không phải điều kiện mà mã ứng dụng nên dựa vào. [Tài liệu ArrayList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

### 3.2. “LinkedList thêm/xóa O(1)” thiếu điều kiện nào?

`LinkedList` là danh sách liên kết đôi. Sau khi đã đứng tại nút cần sửa, việc nối hoặc tháo liên kết chỉ cần O(1). Nhưng `add(i, x)` hay `remove(i)` phải tìm đến nút trước.

Với `get(i)`, số bước di chuyển xấp xỉ `min(i, n - 1 - i)`; cộng chi phí xử lý cố định ta có O(1 + min(i, n - 1 - i)). Truy cập sát đầu/cuối rẻ, truy cập giữa có thể O(n).

Một `ListIterator` của `LinkedList` đã ở đúng vị trí có thể `add(x)` hoặc `remove()` hợp lệ với O(1). Nếu tạo mới `listIterator(i)` trước mỗi lần sửa, chi phí tìm vị trí lại xuất hiện. `remove()` của iterator chỉ được gọi theo trạng thái hợp lệ, chẳng hạn sau `next()`, và không được gọi hai lần liên tiếp nếu chưa di chuyển lại. [Tài liệu LinkedList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html)

### 3.3. Cùng một vòng lặp, tổng chi phí khác nhau

Đoạn sau là phương thức minh họa, cần đặt trong một lớp Java:

~~~java
static long sumByIndex(java.util.List<Integer> values) {
    long sum = 0;
    for (int i = 0; i < values.size(); i++) {
        sum += values.get(i);
    }
    return sum;
}
~~~

Với `ArrayList`, n lần `get(i)` O(1) cho tổng O(n). Với `LinkedList`, tổng số bước đi qua các nút tăng theo n², dù mỗi lần truy cập chọn đầu gần hơn.

Duyệt tuần tự bằng iterator tránh việc tìm lại vị trí từ đầu hoặc cuối:

~~~java
static long sumSequentially(java.util.List<Integer> values) {
    long sum = 0;
    for (int value : values) {
        sum += value;
    }
    return sum;
}
~~~

Với hai triển khai trên, phương thức thứ hai là O(n), giả sử mỗi phép cộng và đọc một phần tử có chi phí hằng. Việc đổi kiểu biến từ `LinkedList` thành giao diện `List` không làm đổi cấu trúc thực tế hay chi phí `get(i)`.

## 4. Hàng đợi hai đầu và hàng đợi ưu tiên

Hai cấu trúc này giải quyết hai yêu cầu khác nhau:

- `ArrayDeque`: lấy/thêm ở đầu hoặc cuối theo vị trí trong dãy.
- `PriorityQueue`: lấy phần tử có mức ưu tiên cao nhất theo quy tắc so sánh; với thứ tự tự nhiên tăng dần, đó là phần tử nhỏ nhất.

| Thao tác | ArrayDeque | PriorityQueue |
| --- | --- | --- |
| Đọc đầu bằng `peek()` | O(1) | O(1) |
| Đọc cuối bằng `peekLast()` | O(1) | Không có thao tác tương đương |
| Thêm đầu/cuối | O(1) khấu hao | Không hỗ trợ ngữ nghĩa hai đầu |
| Thêm bằng `offer(x)` | O(1) khấu hao, thêm cuối | O(log n) cho điều chỉnh heap; xem lưu ý tăng mảng |
| Lấy và xóa đầu bằng `poll()` | O(1) | O(log n) |
| Tìm bằng `contains(x)` | O(n) | O(n) |
| Xóa giá trị bằng `remove(x)` | O(n) | O(n) |
| Duyệt toàn bộ bằng iterator | O(n), theo thứ tự dãy | O(n), không bảo đảm thứ tự ưu tiên |
| Truy cập bằng chỉ số tùy ý | Không có `get(i)` | Không có `get(i)` |

`ArrayDeque` dùng mảng vòng: tiến đầu/cuối thường chỉ cập nhật vị trí. Khi thêm làm đầy mảng, một lần tăng dung lượng có thể tốn O(n). `iterator.remove()` của nó cũng có thể O(n); không áp dụng kết luận xóa tại iterator của `LinkedList` cho mọi collection. [Tài liệu ArrayDeque của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)

`PriorityQueue` dùng heap. Đường đi từ lá tới gốc hoặc ngược lại dài O(log n), nhưng heap không bảo đảm toàn bộ mảng đã được sắp xếp. Do đó tìm một giá trị bất kỳ vẫn phải quét; `peek()` nhanh không kéo theo `contains(x)` nhanh. Gọi `poll()` đến hết n phần tử có tổng chi phí O(n log n), đồng thời làm rỗng hàng đợi. [Tài liệu PriorityQueue của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

**Lưu ý về một lần offer:** ghi chú API nêu O(log n) cho thao tác thêm vào heap. Nếu tính cả sao chép mảng nền khi tăng dung lượng, một lần `offer` trong OpenJDK có thể O(n); O(log n) là cận khấu hao khi cộng chi phí tăng mảng vào chuỗi lần thêm. Đây là phân tích từ triển khai, không phải lời hứa về thời gian thực của mỗi lời gọi. [Mã nguồn PriorityQueue trong OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java)

## 5. Set: kiểm tra thành viên và thứ tự duyệt

`Set` không cung cấp truy cập vị trí như `get(i)`. “Truy cập một phần tử” cần được chuyển thành yêu cầu rõ ràng: kiểm tra thành viên, lấy phần tử đầu theo thứ tự, hay tìm phần tử gần một mốc.

| Triển khai | `contains(x)` | `add(x)` | `remove(x)` | Duyệt toàn bộ | Thứ tự |
| --- | --- | --- | --- | --- | --- |
| `HashSet` | O(1) kỳ vọng | O(1) kỳ vọng, khấu hao | O(1) kỳ vọng | O(n + c) | Không bảo đảm |
| `LinkedHashSet` | O(1) kỳ vọng | O(1) kỳ vọng, khấu hao | O(1) kỳ vọng | O(n) | Thứ tự thêm |
| `TreeSet` | O(log n) | O(log n) | O(log n) | O(n) | Theo comparator/thứ tự tự nhiên |

Bảng băm cần mã băm phân bố tốt; thêm có thể kích hoạt tăng dung lượng. Với cây, các cận O(log n) là cận trường hợp xấu nhất của thao tác cơ bản dưới giả định so sánh O(1).

`HashSet` phải đi qua cả các ngăn trong bảng nền khi duyệt. Nếu đã cấp dung lượng rất lớn nhưng chỉ giữ ít phần tử, không thể bỏ qua c. Khi c cùng bậc n, O(n + c) rút gọn thành O(n). [Tài liệu HashSet của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

`LinkedHashSet` duy trì liên kết qua các phần tử đang có, nên chi phí duyệt không phụ thuộc dung lượng bảng. Đổi lại, nó cần thêm bộ nhớ và công việc duy trì liên kết. [Tài liệu LinkedHashSet của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

`TreeSet` duy trì thứ tự bằng cây. Nó phù hợp khi cần các phép tìm lân cận như `floor`, `ceiling` hoặc xử lý theo miền giá trị. Comparator quyết định hai phần tử có được xem là cùng vị trí trong cây hay không; thiết kế comparator thiếu nhất quán với `equals` có thể làm sai kỳ vọng của người dùng về tính duy nhất. [Tài liệu TreeSet của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

## 6. Map: truy cập theo khóa khác tìm theo giá trị

`map.get(k)` là tra cứu bằng khóa, không phải lấy phần tử ở chỉ số k. Kể cả khi khóa là `Integer`, `get(5)` vẫn có nghĩa “khóa bằng 5”.

| Thao tác | HashMap | LinkedHashMap | TreeMap |
| --- | --- | --- | --- |
| `get(k)`, `containsKey(k)` | O(1) kỳ vọng | O(1) kỳ vọng | O(log n) |
| `put(k, v)` | O(1) kỳ vọng, khấu hao khi thêm khóa mới | O(1) kỳ vọng, khấu hao khi thêm khóa mới | O(log n) |
| `remove(k)` | O(1) kỳ vọng | O(1) kỳ vọng | O(log n) |
| `containsValue(v)` | O(n + c) trường hợp xấu nhất | O(n) | O(n) |
| Duyệt `entrySet()` đầy đủ | O(n + c) | O(n) | O(n) |
| Thứ tự duyệt | Không bảo đảm | Thường là thứ tự thêm; có chế độ thứ tự truy cập | Thứ tự khóa |

`HashMap` lập chỉ mục bằng khóa, nên tra giá trị không được hưởng cùng đường đi. Muốn truy vấn ngược theo giá trị thường xuyên, cần cân nhắc chỉ mục bổ sung và chi phí đồng bộ chỉ mục đó. Trong cách dùng thông thường c = O(n), cận của `containsValue` hay duyệt bảng băm được viết ngắn là O(n). [Tài liệu HashMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [triển khai containsValue trong OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/HashMap.java)

`LinkedHashMap` nối các mục đang có để duyệt O(n). Với chế độ thứ tự truy cập, một lần `get(k)` thành công có thể cập nhật vị trí mục trong dãy; vẫn không có việc sắp xếp toàn bộ map ở mỗi lần đọc. [Tài liệu LinkedHashMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

`TreeMap` tổ chức cây theo khóa. Nó bảo đảm O(log n) cho tra cứu, thêm, xóa bằng khóa, nhưng không tạo chỉ mục thứ hai theo giá trị. Việc đã biết khóa gần một giá trị nào đó cũng không có nghĩa biết chỉ số thứ hạng của khóa trong O(1). [Tài liệu TreeMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

## 7. Những chi phí ẩn sau một ô O(1)

### 7.1. Va chạm băm và tăng dung lượng

Nhiều khóa có cùng mã băm có thể làm thao tác chậm hơn O(1). Java có cơ chế giảm tác động của va chạm trong một số tình huống, nhưng không nên suy ra “mọi thao tác HashMap luôn O(log n) trong trường hợp xấu nhất” chỉ từ việc có ngăn dạng cây. API công bố điều kiện phân bố băm tốt cho chi phí hằng của thao tác cơ bản.

Khi tăng bảng, phải cấp bảng mới và phân phối lại các mục; chi phí còn phụ thuộc dung lượng bảng. Trong mô hình dung lượng tỉ lệ với số mục, một đợt tăng bảng là tuyến tính theo n. Cấp sẵn dung lượng hợp lý có thể giảm số lần tăng, nhưng cấp quá dư lại tốn bộ nhớ và làm duyệt `HashMap`/`HashSet` đắt hơn.

Khóa trong bảng băm cần giữ ổn định các thông tin dùng cho `hashCode` và `equals` khi đang lưu. Thay đổi chúng có thể khiến tra cứu không tìm được mục đã thêm; đó là lỗi tính đúng đắn trước khi là vấn đề tốc độ.

### 7.2. HashCode, equals và comparator không miễn phí

Nếu khóa là cấu trúc lớn, một lần tính mã băm hoặc kiểm tra bằng nhau có thể phải xử lý nhiều trường hay nhiều ký tự. Nếu mỗi lần so sánh tốn O(L), đường tìm kiếm cây có thể tốn O(L log n). Tìm tuần tự một giá trị trong danh sách có thể tốn O(nL).

Có thể hình dung chi phí tra bảng băm là: chi phí tính mã băm + chi phí xác định ngăn + chi phí so sánh các ứng viên. Ký hiệu O(1) theo n không nói rằng kích thước bản thân khóa không ảnh hưởng.

### 7.3. Duyệt gồm cả phần việc trong vòng lặp

Duyệt O(n) chỉ đếm chi phí đi qua collection. Nếu mỗi phần tử lại thực hiện một truy vấn O(n), toàn bộ đoạn mã có thể O(n²).

Ví dụ, với hai danh sách kích thước n và m, gọi `otherList.contains(x)` cho từng x trong danh sách thứ nhất có cận O(nm). Nếu chuyển danh sách thứ hai thành tập băm trước, tổng chi phí kỳ vọng có thể là O(m + n), kèm bộ nhớ bổ sung và yêu cầu hợp đồng bằng nhau/băm đúng.

Việc chuyển đổi cũng có chi phí. Với chỉ một truy vấn nhỏ, xây một tập mới có thể không mang lại lợi ích.

### 7.4. Sắp xếp trước chỉ có lợi khi được dùng lại

`contains` của một `ArrayList` đã sắp xếp vẫn tìm tuần tự; nó không tự đổi sang tìm nhị phân. Muốn tìm nhị phân phải dùng thuật toán phù hợp và bảo đảm dữ liệu đã sắp xếp theo đúng quy tắc.

`Collections.binarySearch` trên danh sách truy cập ngẫu nhiên như `ArrayList` là O(log n). Với danh sách liên kết lớn, Java dùng cách dựa trên iterator, có O(n) bước đi qua liên kết dù chỉ O(log n) lần so sánh. Nếu phải sắp xếp từ đầu cho một lần tìm, chi phí chuẩn bị vẫn phải tính vào tổng. [Tài liệu Collections.binarySearch của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#binarySearch(java.util.List,T))

## 8. Chương trình minh họa chạy được

Ví dụ sau làm rõ ba loại thao tác: truy cập theo chỉ số, sửa tại vị trí iterator và lấy theo ưu tiên. Nó minh họa hành vi và kết quả; không dùng thời gian chạy của vài phần tử để chứng minh Big O.

Lưu thành `CollectionCostDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;

public class CollectionCostDemo {
    public static void main(String[] args) {
        List<Integer> numbers =
                new ArrayList<>(List.of(10, 20, 30, 40));

        System.out.println("get(2) = " + numbers.get(2));
        System.out.println("contains(30) = " + numbers.contains(30));

        // Chon remove(Object), khong phai remove(int index).
        numbers.remove(Integer.valueOf(20));
        System.out.println("after remove value = " + numbers);

        LinkedList<String> tasks =
                new LinkedList<>(List.of("A", "B", "C", "D"));

        // Viec tao iterator tai vi tri 2 cung co chi phi tim vi tri.
        ListIterator<String> cursor = tasks.listIterator(2);
        String removed = cursor.next();
        cursor.remove();
        cursor.add("X");

        System.out.println("removed by iterator = " + removed);
        System.out.println("tasks = " + tasks);

        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("Lan", 9);
        scores.put("Minh", 7);

        System.out.println("get key = " + scores.get("Minh"));
        System.out.println("contains value = " + scores.containsValue(7));

        PriorityQueue<Integer> queue =
                new PriorityQueue<>(List.of(7, 2, 5, 1));
        List<Integer> ordered = new ArrayList<>();

        while (!queue.isEmpty()) {
            ordered.add(queue.poll());
        }

        System.out.println("poll order = " + ordered);
        System.out.println("remaining size = " + queue.size());
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~shell
javac --release 17 CollectionCostDemo.java
java CollectionCostDemo
~~~

Stdout chính xác:

~~~text
get(2) = 30
contains(30) = true
after remove value = [10, 30, 40]
removed by iterator = C
tasks = [A, B, X, D]
get key = 7
contains value = true
poll order = [1, 2, 5, 7]
remaining size = 0
~~~

Đối chiếu với mô hình:

- `get(2)` của `ArrayList` đi thẳng tới vị trí; `contains(30)` phải tìm theo giá trị.
- Xóa số 20 cần tìm nó và dịch phần phía sau. Viết `remove(20)` sẽ chọn overload nhận chỉ số, có thể ném `IndexOutOfBoundsException`.
- Tạo `listIterator(2)` phải xác định vị trí. Khi iterator đã đứng đó, xóa C và chèn X không phải tìm lại từ đầu.
- `scores.get("Minh")` tra bằng khóa; `containsValue(7)` có thể cần quét các mục.
- Thứ tự lấy từ `poll()` được bảo đảm theo ưu tiên. Không thay vòng lặp này bằng việc in `queue` rồi mặc định kết quả đã tăng dần.

## 9. Chọn cấu trúc theo công việc chi phối

| Công việc chính | Lựa chọn cần cân nhắc | Cơ sở |
| --- | --- | --- |
| Đọc theo chỉ số, thêm cuối, duyệt nhiều | `ArrayList` | Truy cập vị trí O(1), thêm cuối khấu hao O(1) |
| Xử lý FIFO hoặc ngăn xếp, thao tác hai đầu | `ArrayDeque` | Thêm hai đầu khấu hao O(1), lấy hai đầu O(1) |
| Duyệt và liên tục sửa tại vị trí con trỏ đã có | `LinkedList` với `ListIterator` | Thay liên kết tại vị trí O(1); vẫn tính đường đi tới vị trí |
| Kiểm tra trùng/thành viên nhiều lần | `HashSet` | O(1) kỳ vọng cho kiểm tra thành viên |
| Tra dữ liệu bằng khóa | `HashMap` | O(1) kỳ vọng cho tra khóa |
| Cần giữ thứ tự thêm khi duyệt | `LinkedHashSet` hoặc `LinkedHashMap` | Duyệt O(n) với thứ tự xác định |
| Cần dữ liệu theo thứ tự và tìm lân cận | `TreeSet` hoặc `TreeMap` | Các thao tác cơ bản O(log n) |
| Liên tục chọn phần tử ưu tiên nhất | `PriorityQueue` | Đọc đầu O(1), lấy đầu O(log n) |

Không dùng bảng như một bảng xếp hạng tốc độ tuyệt đối. Cần xét kích thước dữ liệu, tỉ lệ đọc/ghi, bộ nhớ, thứ tự, tính duy nhất và yêu cầu đồng thời. Hai cấu trúc có cùng O(n) khi duyệt vẫn khác nhau về số đối tượng, khả năng tận dụng bộ nhớ đệm CPU và áp lực thu gom rác.

## 10. Tự kiểm tra cách phân tích

1. **Xóa đầu ArrayList n lần đến khi rỗng:** O(n²), vì số phần tử dịch lần lượt gần n - 1, n - 2, …, 0. Với `ArrayDeque.pollFirst()`, tổng là O(n).
2. **Duyệt LinkedList bằng get(i):** O(n²). Duyệt bằng iterator là O(n).
3. **Tìm một giá trị trong TreeMap bằng containsValue:** O(n), dù `get(key)` là O(log n).
4. **Duyệt HashSet sau khi cấp dung lượng rất lớn:** cần tính O(n + c); số phần tử ít không bảo đảm duyệt rẻ.
5. **Lấy tất cả phần tử PriorityQueue theo thứ tự bằng poll:** O(n log n), không phải O(n) như duyệt iterator.
6. **Thêm cuối ArrayList một triệu lần:** tổng O(n) theo mô hình khấu hao; không bảo đảm mỗi lần gọi có cùng độ trễ.

Khi đọc một đoạn mã, hãy ghi rõ phương thức, triển khai thực tế, điều kiện đánh giá và số lần lặp. Bốn thông tin đó giúp biến một nhận xét mơ hồ như “collection này nhanh” thành một lập luận kiểm chứng được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-002">Chi phí khấu hao, trường hợp trung bình và trường hợp xấu</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí khấu hao, trường hợp trung bình và trường hợp xấu

## 1. Vì sao cùng một thao tác có thể được mô tả bằng nhiều mức chi phí?

Khi đọc rằng `ArrayList.add(element)` có chi phí hằng số khấu hao, ta có thể đặt câu hỏi: nếu danh sách đã đầy, Java phải tạo mảng lớn hơn và sao chép các phần tử; vậy thao tác đó có thật sự là `O(1)` không?

Câu trả lời phụ thuộc vào **đối tượng đang được đánh giá**:

- Một lần gọi đắt nhất khi collection đang có `n` phần tử: phân tích **trường hợp xấu**.
- Một lần gọi lấy trung bình theo một phân phối đầu vào đã nêu: phân tích **trường hợp trung bình**.
- Cả một chuỗi thao tác, sau đó phân bổ tổng chi phí cho các thao tác trong chuỗi: phân tích **khấu hao**.

Ba cách nhìn này trả lời những câu hỏi khác nhau. Một thao tác có thể đồng thời có chi phí khấu hao `O(1)` và chi phí trường hợp xấu `O(n)`. Không có mâu thuẫn: một vài lần gọi đắt được bù bởi nhiều lần gọi rẻ trong một chuỗi mà tổng chi phí vẫn tăng tuyến tính.

Mục tiêu của bài là biết đọc đúng các phát biểu hiệu năng của Java Collections, nêu được giả định phía sau chúng và tránh suy từ một con số Big-O sang lời hứa về độ trễ của mọi lần gọi.

## 2. Xác định mô hình chi phí trước khi nói Big-O

### 2.1. `n` là gì, và ta đang đếm điều gì?

Trong bài này:

- `n` thường là số phần tử hiện có trong collection trước một thao tác.
- `m` là số thao tác trong một chuỗi.
- Khi cần phân tích độ dài khóa, dùng thêm `L` thay vì giấu mọi chi phí trong `n`.
- Một đơn vị chi phí có thể là một lần so sánh, một lần ghi phần tử hoặc một lần sao chép một tham chiếu. Phải nói rõ mô hình được dùng.

Ví dụ, sao chép `n` tham chiếu từ mảng cũ sang mảng mới là công việc tuyến tính theo `n`. Nó không sao chép sâu mọi đối tượng được các tham chiếu trỏ đến. Đối tượng có nhiều trường không có nghĩa mỗi lần tăng dung lượng `ArrayList` phải nhân bản toàn bộ các đối tượng đó.

Phân tích thường giả định truy cập một ô mảng và thao tác với một tham chiếu có chi phí hằng số. Với `HashMap`, ta còn phải xét giả định về `hashCode()` và `equals()`. Với `TreeMap`, chi phí của `Comparator.compare()` hoặc `compareTo()` cũng cần được tính nếu việc so sánh không hằng số.

### 2.2. Big-O không phải tên gọi của trường hợp xấu

`O(f(n))` là một cận trên về tốc độ tăng của một đại lượng chi phí. Đại lượng đó có thể là chi phí trường hợp xấu, chi phí kỳ vọng hoặc chi phí khấu hao. Ký hiệu `O` tự nó không chỉ ra ta đang xét loại nào.

`Θ(f(n))` diễn tả cận chặt theo bậc tăng, còn `Ω(f(n))` diễn tả cận dưới. Vì vậy, một chi phí hằng số cũng có cận trên `O(n)`, nhưng mô tả `Θ(1)` cung cấp thông tin chặt hơn.

Trong trao đổi thực tế, người ta hay nói ngắn “thao tác này `O(1)`”. Khi điều đó ảnh hưởng tới thiết kế, hãy hỏi tiếp: đối với loại dữ liệu nào, một lần gọi hay cả chuỗi, và có giả định nào về khóa hoặc vị trí thao tác?

## 3. Trường hợp xấu: lần gọi tốn nhiều nhất ở kích thước đã cho

Với một thao tác và tập trạng thái/đầu vào hợp lệ ở kích thước `n`, chi phí trường hợp xấu là chi phí lớn nhất trong tập đó.

Có thể viết:

```text
W(n) = max chi phí của một thao tác trên các đầu vào/trạng thái kích thước n
```

Ví dụ, tìm kiếm tuyến tính trong danh sách `n` phần tử:

- Nếu phần tử cần tìm ở đầu: có thể chỉ cần một lần so sánh.
- Nếu nó ở cuối: cần `n` lần so sánh.
- Nếu không tồn tại: cần kiểm tra cả `n` phần tử.

Chi phí trường hợp xấu của việc tìm kiếm này là `Θ(n)`. Không cần biết người dùng “thường tìm gì” để đưa ra cận đó.

Với mảng động đang đầy, thêm một phần tử có thể phải sao chép `n` phần tử cũ rồi ghi phần tử mới. Trong mô hình sao chép từng phần tử, riêng lần thêm đó có chi phí `Θ(n)`.

**Giá trị của phân tích trường hợp xấu:** nó giúp nhận diện lần gọi có thể đắt và kiểm tra tính ổn định của số bước thuật toán. Tuy nhiên, một cận Big-O về số bước không phải cam kết số mili giây trên JVM. Cấp phát bộ nhớ, thu gom rác, JIT, lịch chạy luồng và trạng thái hệ điều hành vẫn ảnh hưởng đến thời gian thực tế.

Cũng cần phân biệt:

- Trường hợp xấu của **một thao tác** khi đang có `n` phần tử.
- Trường hợp xấu của **toàn bộ chuỗi** `m` thao tác hợp lệ.

Không phải lúc nào cũng có thể tạo chuỗi mà mọi thao tác đều đạt mức đắt nhất của một lần gọi. Ràng buộc này là cơ sở quan trọng của phân tích khấu hao.

## 4. Trường hợp trung bình: phải có phân phối đầu vào

### 4.1. Trung bình theo cái gì?

Phân tích trường hợp trung bình tính kỳ vọng chi phí khi đầu vào hoặc trạng thái được lấy theo một phân phối xác suất đã xác định.

```text
A(n) = tổng của [xác suất đầu vào x × chi phí xử lý x]
       trên các đầu vào kích thước n
```

Không thể chỉ nói “dữ liệu bình thường” rồi mặc nhiên xem đó là một mô hình xác suất. Kết luận phụ thuộc vào việc các trường hợp xuất hiện thường xuyên đến mức nào.

Xét tìm kiếm tuyến tính trong danh sách có `n` giá trị phân biệt, với hai giả định:

1. Chắc chắn tìm một giá trị đang có trong danh sách.
2. Mỗi vị trí có xác suất được tìm bằng nhau: `1/n`.

Số lần so sánh kỳ vọng là:

```text
A(n) = (1 + 2 + ... + n) / n
     = [n(n + 1) / 2] / n
     = (n + 1) / 2
     = Θ(n)
```

Một nửa số phần tử vẫn là tuyến tính. “Trung bình chỉ tìm nửa danh sách” không biến thuật toán thành `O(1)`.

### 4.2. Thay đổi phân phối có thể thay đổi kết quả

Nếu phần lớn truy vấn nhắm vào vài phần tử đầu danh sách thì chi phí kỳ vọng có thể nhỏ hơn. Nếu truy vấn thường không tìm thấy thì mỗi truy vấn đó vẫn phải quét toàn bộ danh sách.

Giả sử xác suất truy vấn thành công là `p`; khi thành công, vị trí cần tìm phân bố đều. Khi thất bại phải kiểm tra `n` phần tử. Ta có:

```text
E[số lần so sánh] = p × (n + 1) / 2 + (1 - p) × n
```

Với mô hình này, chi phí vẫn là `Θ(n)` đối với `n` tăng. Công thức giúp ta thấy rõ cả ảnh hưởng của truy vấn thất bại thay vì âm thầm bỏ chúng ra khỏi phép tính.

Cần phân biệt thêm:

- **Trung bình lý thuyết:** kỳ vọng theo một mô hình xác suất.
- **Trung bình đo được:** trung bình các mẫu thời gian trên máy và dữ liệu đã chạy.

Trung bình đo được là bằng chứng về tập mẫu đó. Nó không tự chứng minh mô hình xác suất đúng, không chứng minh cận thuật toán và không cung cấp cận cho lần gọi chậm nhất.

## 5. Khấu hao: đánh giá cả chuỗi, không cần giả định ngẫu nhiên

### 5.1. Tổng chi phí mới là điểm xuất phát

Giả sử một chuỗi có `m` thao tác với chi phí thực tế lần lượt là `c1, c2, ..., cm`.

```text
T(m) = c1 + c2 + ... + cm
```

Nếu chứng minh được rằng mọi chuỗi thuộc phạm vi đang xét đều có `T(m) = O(m)`, thì chi phí khấu hao trên một thao tác là `O(1)`.

Đây là việc phân bổ chi phí trong một chứng minh. Không phải thao tác nào cũng thực sự dùng cùng số bước. Không cần cho rằng đầu vào ngẫu nhiên, may mắn, hoặc các lần gọi đắt có xác suất thấp.

Ví dụ điển hình: một mảng động tăng dung lượng theo hệ số cố định lớn hơn `1`. Sau một lần tăng dung lượng, có thêm nhiều ô trống. Các lần thêm tiếp theo dùng các ô đó trước khi cần sao chép lần nữa. Chính quy tắc thay đổi trạng thái tạo ra bảo đảm tổng chi phí.

### 5.2. “Chia tổng cho số thao tác” chưa đủ để chứng minh

Ta có thể lấy tổng thời gian một lần chạy bất kỳ chia cho số thao tác. Đó mới chỉ là một số trung bình của lần chạy.

Để kết luận khấu hao, cần chứng minh cận tổng cho **mọi chuỗi thao tác được phép trong mô hình**, hoặc dùng một phương pháp tương đương. Phải nêu:

- Trạng thái khởi đầu.
- Tập thao tác được phép.
- Cách collection tăng/giảm dung lượng.
- Những chi phí được tính.

Ví dụ, cận `O(m)` cho `m` lần thêm bắt đầu từ mảng động rỗng không thể được áp dụng nguyên xi cho một lần thêm vào mảng đang đầy sẵn `n` phần tử. Nếu chỉ có một thao tác, chi phí sao chép `n` phần tử không thể biến mất. Khi bắt đầu từ một cấu trúc có sẵn, cận tổng cần tính cả chi phí hoặc trạng thái ban đầu, chẳng hạn `O(n + m)` trong mô hình tăng dung lượng tương ứng.

## 6. Chứng minh với mảng động tăng gấp đôi

Để thấy rõ cơ chế, dùng một **mô hình giảng giải**:

- Bắt đầu với dung lượng `0`.
- Lần thêm đầu tạo mảng dung lượng `1`.
- Khi mảng đầy, tạo mảng mới có dung lượng gấp đôi.
- Chi phí của một lần thêm = số phần tử cũ phải sao chép + một lần ghi phần tử mới.

Mô hình này không khẳng định `ArrayList` của mọi JDK luôn tăng đúng gấp đôi. Chính sách tăng dung lượng cụ thể của `ArrayList` không được API quy định; bảo đảm được công bố là chi phí thêm khấu hao hằng số. [Tài liệu ArrayList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

### 6.1. Theo dõi 16 lần thêm

| Lần thêm | Dung lượng trước | Có tăng dung lượng? | Số phần tử sao chép | Tổng đơn vị chi phí |
| --- | --- | --- | --- | --- |
| 1 | 0 | Có, lên 1 | 0 | 1 |
| 2 | 1 | Có, lên 2 | 1 | 2 |
| 3 | 2 | Có, lên 4 | 2 | 3 |
| 4 | 4 | Không | 0 | 1 |
| 5 | 4 | Có, lên 8 | 4 | 5 |
| 6–8 | 8 | Không | 0 mỗi lần | 1 mỗi lần |
| 9 | 8 | Có, lên 16 | 8 | 9 |
| 10–16 | 16 | Không | 0 mỗi lần | 1 mỗi lần |

Có `16` lần ghi giá trị mới. Tổng số phần tử được sao chép là:

```text
1 + 2 + 4 + 8 = 15
```

Tổng chi phí là `16 + 15 = 31`, trong khi lần thêm đắt nhất tốn `9` đơn vị. Tổng nhỏ không loại bỏ lần gọi đắt.

### 6.2. Tổng hợp chi phí cho `m` lần thêm

Các lần sao chép có kích thước là các lũy thừa của `2`:

```text
1 + 2 + 4 + ... + 2^k = 2^(k + 1) - 1
```

Với `m` lần thêm, lũy thừa lớn nhất phải sao chép nhỏ hơn `m`. Do đó tổng số phần tử sao chép nhỏ hơn `2m` và:

```text
T(m) < m + 2m = 3m
```

Kết luận:

- Tổng chi phí của `m` lần thêm là `Θ(m)` trong mô hình này: ít nhất cũng phải ghi `m` phần tử mới.
- Chi phí khấu hao mỗi lần thêm là `Θ(1)`.
- Một lần thêm khi mảng đầy vẫn có chi phí trường hợp xấu `Θ(n)`.

Chi phí cấp phát và khởi tạo mảng mới bị lược bỏ trong phép đếm đơn giản trên. Nếu tính chúng là tuyến tính theo dung lượng mảng mới, tổng các dung lượng được cấp phát vẫn là một cấp số nhân. Bậc tổng vẫn tuyến tính theo `m`; các hệ số cụ thể thay đổi.

### 6.3. Vì sao tăng thêm một ô lại khác?

Nếu mỗi lần đầy chỉ tăng dung lượng thêm `1`, để thêm `m` phần tử ta phải sao chép:

```text
0 + 1 + 2 + ... + (m - 1) = m(m - 1) / 2
```

Tổng là `Θ(m²)`, nên chi phí khấu hao một lần thêm là `Θ(m)`, không còn hằng số.

Điểm tạo ra bảo đảm không phải chỉ là “thỉnh thoảng có sao chép”, mà là **dung lượng tăng đủ nhanh để khoảng cách giữa các lần sao chép cũng tăng**. Một hệ số cố định lớn hơn `1` cho phép tổng hợp theo cấp số nhân; hệ số càng gần `1` thì chi phí sao chép và mức bộ nhớ dư có đánh đổi khác nhau.

### 6.4. Cách nghĩ bằng khoản dự trữ

Có thể hình dung mỗi lần thêm trả một khoản chi phí khấu hao cố định: một phần chi trả lần ghi hiện tại, phần còn lại được dự trữ để trả cho lần sao chép sau.

Khoản dự trữ là công cụ toán học, không phải một trường tiền hoặc một bộ đếm bắt buộc trong chương trình. Một chứng minh theo cách này phải bảo đảm khoản dự trữ không âm. Ta không được lấy một giá trị tùy ý rồi gọi đó là khấu hao.

Phương pháp tổng hợp ở trên đã đủ chứng minh cho mô hình tăng gấp đôi. Cách dự trữ giúp giải thích trực giác: các lần rẻ đã góp chi phí cho lần đắt; điều này không cần một phân phối đầu vào.

## 7. Chương trình Java: đếm thao tác và kiểm chứng phép tính

Chương trình dưới đây chạy với Java 17+. Lớp `DoublingBuffer` là mô hình mảng động tự viết để quan sát số phần tử sao chép. Nó không đọc dung lượng nội bộ hoặc đo thời gian của `ArrayList`.

Phần tìm kiếm cuối chương trình đếm số phép so sánh cho năm vị trí thành công, rồi tính trung bình khi cả năm vị trí có trọng số bằng nhau.

Lưu toàn bộ mã vào `CostModelsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CostModelsDemo {
    private static final class DoublingBuffer {
        private int[] elements = new int[0];
        private int size;
        private long copied;
        private long writes;

        int add(int value) {
            int copiedNow = 0;
            if (size == elements.length) {
                int newCapacity = elements.length == 0
                        ? 1
                        : elements.length * 2;
                copiedNow = size;
                elements = Arrays.copyOf(elements, newCapacity);
                copied += copiedNow;
            }
            elements[size++] = value;
            writes++;
            return copiedNow + 1;
        }
    }

    private static int comparisonsUntilFound(List<Integer> values, int target) {
        int comparisons = 0;
        for (int value : values) {
            comparisons++;
            if (value == target) {
                return comparisons;
            }
        }
        return comparisons;
    }

    public static void main(String[] args) {
        DoublingBuffer buffer = new DoublingBuffer();
        List<Integer> costs = new ArrayList<>();
        int worst = 0;
        for (int i = 0; i < 16; i++) {
            int cost = buffer.add(i);
            costs.add(cost);
            worst = Math.max(worst, cost);
        }

        System.out.println("append costs = " + costs);
        System.out.println("writes = " + buffer.writes);
        System.out.println("copied = " + buffer.copied);
        System.out.println("total units = " + (buffer.writes + buffer.copied));
        System.out.println("worst append = " + worst);

        List<Integer> values = List.of(10, 20, 30, 40, 50);
        int successfulTotal = 0;
        for (int target : values) {
            successfulTotal += comparisonsUntilFound(values, target);
        }
        System.out.println("successful search total = " + successfulTotal);
        System.out.println("uniform successful mean = "
                + (double) successfulTotal / values.size());
        System.out.println("unsuccessful search = "
                + comparisonsUntilFound(values, 99));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CostModelsDemo.java
java CostModelsDemo
```

Stdout:

```text
append costs = [1, 2, 3, 1, 5, 1, 1, 1, 9, 1, 1, 1, 1, 1, 1, 1]
writes = 16
copied = 15
total units = 31
worst append = 9
successful search total = 15
uniform successful mean = 3.0
unsuccessful search = 5
```

Cách đọc kết quả:

1. Các đỉnh `2`, `3`, `5`, `9` cho thấy chi phí thực tế mỗi lần thêm không cố định.
2. `31` đơn vị cho `16` lần thêm phù hợp với cận tổng nhỏ hơn `3m`.
3. `15 / 5 = 3` là trung bình theo giả định năm mục tiêu xuất hiện đều nhau; số `3` không phải cận trường hợp xấu.
4. Truy vấn không có kết quả vẫn kiểm tra đủ năm phần tử.

Chương trình dùng dữ liệu nhỏ để trình bày cơ chế, không phải một collection tổng quát có xử lý giới hạn dung lượng hay mọi tình huống lỗi. Một lần chạy là minh chứng cụ thể; chứng minh cấp số nhân mới giải thích vì sao cận khấu hao đúng với mọi số lần thêm trong mô hình.

## 8. Đọc đúng các phát biểu hiệu năng của Java Collections

### 8.1. `ArrayList`: phân biệt thêm cuối, chèn giữa và tìm kiếm

`ArrayList.add(element)` thêm vào cuối. Một lần gọi có thể phải tăng dung lượng; một chuỗi thêm cuối có bảo đảm khấu hao hằng số. API cũng cho phép chuẩn bị dung lượng bằng constructor hoặc `ensureCapacity()`, giúp giảm các lần cấp phát lại khi biết trước lượng phần tử sẽ thêm. Không suy ra một hệ số tăng cụ thể từ bảo đảm đó. [Tài liệu ArrayList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Không chuyển bảo đảm này sang mọi phương thức cùng tên:

- `add(0, element)` trên danh sách có `n` phần tử phải dịch `n` phần tử sang phải, kể cả khi vẫn còn dung lượng trống.
- `remove(0)` phải dịch các phần tử phía sau sang trái.
- `contains(value)` phải tìm giá trị; không có tra cứu bằng chỉ số miễn phí.
- `get(index)` truy cập vị trí đã biết, nên không phải làm bước tìm kiếm tuyến tính.

Nếu liên tục chèn `m` phần tử ở đầu một `ArrayList` rỗng, số phần tử cần dịch là `0 + 1 + ... + (m - 1)`. Tổng `Θ(m²)` xuất phát từ dịch chuyển, dù chi phí tăng dung lượng được khấu hao tốt.

### 8.2. `HashMap`: giả định phân tán băm và chi phí tăng bảng

Tài liệu `HashMap` nêu hiệu năng hằng số cho `get` và `put` với giả định hàm băm phân tán phần tử phù hợp giữa các bucket. Bảng tăng dung lượng khi số ánh xạ vượt ngưỡng liên quan đến dung lượng và hệ số tải. Dung lượng ban đầu và hệ số tải cũng ảnh hưởng hiệu năng, không chỉ số ánh xạ. [Tài liệu HashMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

Ở đây cần giữ riêng hai nguồn chi phí:

1. **Phân bố khóa trong các bucket:** ảnh hưởng số ứng viên phải kiểm tra khi tìm khóa. Nếu dùng mô hình ngẫu nhiên cho băm/khóa, ta có thể phân tích chi phí kỳ vọng theo mô hình đó.
2. **Tăng dung lượng:** một lần `put` có thể phải tổ chức lại bảng. Chi phí đắt này được phân bổ qua chuỗi chèn bằng phân tích khấu hao.

Vì vậy, một phát biểu chặt chẽ thường là: “Chèn có chi phí kỳ vọng khấu hao `O(1)` dưới giả định băm phân tán tốt, hệ số tải được kiểm soát, và chi phí băm/so sánh khóa là hằng số.” Chữ “kỳ vọng” nói về phân bố; chữ “khấu hao” nói về chuỗi tăng bảng. Chúng có thể cùng xuất hiện vì xử lý hai khía cạnh khác nhau.

Không coi `HashMap.get` hoặc `put` là lời hứa thời gian hằng số cho mọi khóa. Nhiều khóa va chạm, `hashCode()` tốn kém hoặc `equals()` phải duyệt dữ liệu lớn đều làm lời mô tả đơn giản kém chính xác. Cũng không suy ra cận trường hợp xấu `O(log n)` cho mọi khóa chỉ vì biết một triển khai có thể dùng cây trong bucket; đó không phải bảo đảm tổng quát được tài liệu API nêu cho mọi tình huống.

### 8.3. `ArrayDeque`: thao tác ở hai đầu khác thao tác tìm giá trị

Các thao tác phổ biến ở hai đầu `ArrayDeque` có chi phí khấu hao hằng số. Nhưng `contains`, xóa theo giá trị và một số thao tác khác phải làm công việc tuyến tính; không áp dụng đặc tính hai đầu cho mọi phương thức. [Tài liệu ArrayDeque của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)

Nếu cần lần lượt đưa việc vào cuối và lấy việc ở đầu, cơ chế này tránh việc dịch chuyển toàn bộ phần tử như `ArrayList.remove(0)`.

### 8.4. `TreeMap`: cận theo cây và chi phí so sánh

`TreeMap` có bảo đảm `O(log n)` cho `containsKey`, `get`, `put` và `remove`. Khi quy đổi sang thời gian xử lý khóa, cần tính thêm chi phí phép so sánh nếu nó không hằng số. [Tài liệu TreeMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

Ví dụ, nếu mỗi phép so sánh khóa có chi phí tối đa `O(L)`, cận theo mô hình đó trở thành `O(L log n)`. “Cây có chiều cao logarit” không loại bỏ công việc bên trong mã so sánh do ứng dụng cung cấp.

### 8.5. Bảng đọc nhanh theo thao tác

Bảng dưới giả định chi phí thao tác với một phần tử, băm và so sánh liên quan là hằng số, trừ khi được nêu khác.

| Collection và thao tác | Cách mô tả phù hợp | Điều cần giữ trong đầu |
| --- | --- | --- |
| `ArrayList.get(index)` | `O(1)` | Đã có chỉ số hợp lệ, không cần tìm giá trị |
| `ArrayList.add(element)` | Khấu hao `O(1)`; một lần tăng mảng có thể `O(n)` | Áp dụng cho thêm cuối và chuỗi được phân tích |
| `ArrayList.add(0, element)` | `O(n)` | Cần dịch phần tử dù không tăng mảng |
| `ArrayList.contains(value)` | Trường hợp xấu `O(n)` | Trung bình phụ thuộc phân phối truy vấn |
| `HashMap.get(key)` | Hằng số với giả định băm phân tán tốt | Không có bảo đảm hằng số cho mọi khóa |
| Chuỗi `HashMap.put(key, value)` | Thường phân tích kỳ vọng khấu hao `O(1)` dưới các giả định đã nêu | Xét riêng va chạm và tăng bảng |
| Thêm/lấy ở hai đầu `ArrayDeque` | Khấu hao `O(1)` | Không suy sang tìm hoặc xóa theo giá trị |
| `TreeMap.get/put/remove` | Bảo đảm `O(log n)` theo số bước so sánh | Chi phí một phép so sánh có thể phụ thuộc dữ liệu khóa |

## 9. Phạm vi của chuỗi thao tác có thể làm thay đổi kết luận

### 9.1. Không bỏ qua thao tác chủ động thay đổi dung lượng

Phân tích khấu hao thêm cuối dựa vào việc giữ lại các ô trống sau tăng dung lượng. Nếu ứng dụng chủ động phá điều kiện đó, phải phân tích lại cả chuỗi.

Ví dụ mã minh họa, đặt bên trong một phương thức có sẵn:

```java
ArrayList<Integer> values = new ArrayList<>();
for (int i = 0; i < limit; i++) {
    values.add(i);
    values.trimToSize();
}
```

`trimToSize()` yêu cầu thu dung lượng về kích thước hiện tại. Gọi sau mỗi lần thêm có thể liên tục làm mất phần dung lượng dư và tạo lại công việc sao chép tuyến tính. Không được lấy cận khấu hao của một chuỗi chỉ thêm cuối rồi áp vào chuỗi mới mà bỏ qua chi phí `trimToSize()` và ảnh hưởng của nó. Trong mô hình mảng thực hiện sao chép tuyến tính để thu dung lượng, chuỗi này có thể có tổng chi phí bậc hai.

Đoạn trên không phải hướng dẫn tối ưu bộ nhớ. Nó minh họa việc **thêm một loại thao tác vào chuỗi có thể thay đổi chứng minh**.

### 9.2. Chuẩn bị dung lượng chuyển thời điểm trả chi phí

Nếu biết sẽ thêm khoảng `expectedCount` phần tử:

```java
List<String> names = new ArrayList<>(expectedCount);
```

Constructor tạo danh sách có **kích thước bằng `0`**, dung lượng dự kiến không phải số phần tử hiện có. Phần bộ nhớ chuẩn bị trước có chi phí và có thể giảm số lần tăng mảng trong giai đoạn thêm. Không có nghĩa toàn bộ công việc chuẩn bị trở thành miễn phí.

Với `HashMap`, tham số dung lượng ban đầu liên quan số bucket, không đồng nhất với số ánh xạ mong đợi do còn hệ số tải. Không sao chép cách suy luận của `ArrayList` sang `HashMap` mà bỏ điều kiện đó.

## 10. Từ độ phức tạp đến quyết định thiết kế và phép đo

### 10.1. Tổng lượng việc và độ trễ một lần gọi

Hai yêu cầu sau không đồng nhất:

- “Nạp một triệu phần tử với tổng lượng công việc tăng tuyến tính.”
- “Không một lần thêm nào được có đỉnh công việc tuyến tính.”

Khấu hao hằng số phù hợp để lập luận về tổng lượng công việc của quá trình nạp. Nó không đáp ứng tự động yêu cầu độ trễ chặt cho từng lần gọi.

Khi độ trễ quan trọng, có thể xem xét chuẩn bị dung lượng từ trước, giới hạn kích thước dữ liệu hoặc chọn cấu trúc phù hợp với mẫu thao tác. Sau đó vẫn cần đo trên JVM và tải thực tế. Không có collection tốt nhất chỉ từ một nhãn Big-O.

### 10.2. Đừng suy độ phức tạp từ một lần bấm đồng hồ

Một lần đo bằng `System.nanoTime()` có thể bị chi phối bởi khởi động JVM, JIT, GC, cấp phát, nhiễu hệ thống hoặc mã kiểm tra. Một khoảng thời gian nhỏ cũng không cho biết ta đang quan sát nhánh không tăng dung lượng hay nhánh phải sao chép.

Một đánh giá hữu ích cần xác định:

1. **Tác vụ:** thêm cuối, thêm đầu, tra cứu thành công, tra cứu thất bại hay trộn nhiều thao tác?
2. **Trạng thái:** collection mới, đã có sẵn dữ liệu, gần đầy hay đã được cấp đủ dung lượng?
3. **Dữ liệu:** số phần tử, kích thước khóa, chất lượng băm và tần suất truy vấn?
4. **Chỉ số:** tổng thời gian, số thao tác mỗi giây hay phân bố độ trễ từng thao tác?
5. **Cách so sánh:** cùng chức năng, cùng điều kiện khởi đầu và nhiều kích thước dữ liệu?

Đếm thao tác như chương trình trong bài giúp kiểm chứng cơ chế mà không bị nhiễu thời gian. Đo hiệu năng giúp biết cơ chế đó biểu hiện ra sao trong môi trường chạy cụ thể. Hai cách bổ sung cho nhau.

## 11. Những cách kết luận dễ sai

| Phát biểu dễ gây hiểu nhầm | Cách diễn đạt chính xác hơn |
| --- | --- |
| “Khấu hao là trường hợp trung bình.” | Khấu hao phân bổ tổng chi phí một chuỗi; trung bình cần phân phối xác suất đầu vào. |
| “`ArrayList.add` là `O(1)`, nên mọi lần gọi đều nhanh như nhau.” | Thêm cuối là khấu hao `O(1)`; lần phải tăng mảng có thể tuyến tính. |
| “Tìm kiếm trung bình qua nửa danh sách nên là `O(1)`.” | `(n + 1) / 2` vẫn là `Θ(n)`. |
| “Mọi thao tác đều có trường hợp xấu `O(n)`, nên tổng chắc chắn là `Θ(mn)`.” | Nhân các cận trên có thể tạo cận lỏng; muốn cận chặt phải xem các trạng thái đắt có lặp lại được sau mỗi bước không. |
| “Đo một nghìn lần rồi chia trung bình là chứng minh khấu hao.” | Đó là số đo trên một chuỗi; chứng minh phải bao quát mọi chuỗi trong phạm vi đã nêu. |
| “`HashMap` luôn hằng số vì đã có cây chống va chạm.” | Phải nêu giả định băm, đặc điểm khóa và chi phí phương thức khóa; không gán bảo đảm không có trong API. |
| “Đã chuẩn bị dung lượng nên mọi chi phí bộ nhớ đã biến mất.” | Công việc được chuyển về giai đoạn chuẩn bị; vẫn cần tính chi phí và bộ nhớ giữ lại. |
| “Một triệu lần thêm cuối và một triệu lần chèn đầu là cùng bài toán.” | Chèn đầu còn phải dịch phần tử; khấu hao tăng dung lượng không loại bỏ khoản đó. |

## 12. Tự kiểm tra cách lập luận

### Câu 1: Một mảng động tăng gấp đôi có một lần thêm tốn `n + 1` đơn vị. Có bác bỏ được khấu hao hằng số không?

Không. Phải xét tổng chi phí cả chuỗi. Các lần tăng dung lượng sao chép `1, 2, 4, ...` phần tử có tổng tuyến tính theo số lần thêm. Lần đắt vẫn tồn tại, nhưng không xảy ra ở mọi lần thêm trong chuỗi chỉ thêm cuối.

### Câu 2: Tìm một phần tử có sẵn, mọi vị trí đều được chọn như nhau. Trung bình có nhanh hơn trường hợp xấu về bậc tăng không?

Không. Trung bình cần `(n + 1) / 2` phép so sánh, trường hợp xấu cần `n`; cả hai đều có bậc tuyến tính. Hệ số khác nhau có ý nghĩa thực tế nhưng không thay đổi bậc tăng.

### Câu 3: Chương trình nhận dữ liệu không đáng tin cậy và tra cứu bằng `HashMap`. Có đủ cơ sở để hứa mọi truy vấn đều `O(1)` không?

Không. Cần xét khả năng va chạm, chi phí xử lý khóa và đặc điểm triển khai. Phát biểu dựa trên băm phân tán tốt không phải bảo đảm trường hợp xấu cho mọi đầu vào.

### Câu 4: Vì sao `ArrayList.add(0, value)` không hưởng tổng chi phí tuyến tính khi lặp lại nhiều lần từ danh sách rỗng?

Vì mỗi lần chèn phải dịch các phần tử hiện có. Tổng số phần tử dịch là `0 + 1 + ... + (m - 1) = Θ(m²)`. Cơ chế tăng dung lượng tốt chỉ xử lý chi phí cấp phát lại, không xử lý chi phí dịch vị trí.

### Bài thực hành

1. Đổi số lần thêm trong `CostModelsDemo` từ `16` sang `17`. Dự đoán số phần tử sao chép, tổng chi phí và chi phí lần thêm cuối trước khi chạy.
2. Tính số phép so sánh trung bình nếu mục tiêu chỉ là phần tử đầu với xác suất `0.9` và phần tử cuối với xác suất `0.1` trong danh sách `n` phần tử.
3. Viết một câu mô tả hiệu năng cho một tác vụ thực tế của bạn, nêu đủ thao tác, kích thước, loại phân tích và giả định.

Đáp án để đối chiếu:

- Với `17` lần thêm: thêm lần thứ `17` sao chép `16` phần tử; tổng sao chép là `31`, tổng chi phí là `48`, chi phí lần cuối là `17`.
- Mô hình truy vấn câu 2 có kỳ vọng `0.9 × 1 + 0.1 × n = 0.9 + 0.1n`, vẫn là `Θ(n)` khi `n` tăng.
- Ví dụ câu mô tả: “Khi nạp `m` phần tử bằng cách thêm cuối vào `ArrayList` mới, tổng chi phí thêm có bậc `O(m)` theo bảo đảm khấu hao; một lần tăng mảng vẫn có thể tốn công việc tuyến tính theo kích thước lúc đó.”

Khi đánh giá collection, hãy xác định rõ **một lần gọi hay cả chuỗi**, **phân phối nào nếu nói trung bình**, và **trạng thái/chi phí nào đã được tính**. Ba câu hỏi này giúp biến một nhãn Big-O thành một lập luận có thể kiểm tra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-003">Dung lượng dự phòng, nút liên kết và bộ nhớ cho từng phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Dung lượng dự phòng, nút liên kết và bộ nhớ cho từng phần tử

## 1. Vì sao cùng số phần tử nhưng bộ nhớ khác nhau?

Một collection không chỉ gồm dữ liệu mà người dùng đưa vào. Nó còn cần cấu trúc để giữ các tham chiếu, tìm phần tử và thực hiện thao tác thêm/xóa. Hai danh sách cùng chứa 100.000 đối tượng có thể khác đáng kể về số byte và số đối tượng phụ trợ.

Để đọc đúng chi phí bộ nhớ, hãy tách ba lớp:

1. **Đối tượng collection**: các trường như số phần tử, tham chiếu đến mảng hoặc nút đầu/cuối.
2. **Cấu trúc lưu trữ**: mảng tham chiếu, ô dự phòng, nút liên kết hoặc bảng bucket.
3. **Đối tượng dữ liệu**: các đối tượng được tham chiếu tới, kể cả những đối tượng nằm sâu hơn trong đồ thị dữ liệu.

Bài này đi từ sức chứa của mảng đến chi phí nút, rồi xây dựng cách tính và đo bộ nhớ cho mỗi phần tử. Mục tiêu là giải thích được bộ nhớ đang nằm ở đâu; không học thuộc một con số byte cho mọi JVM.

## 2. `size`, sức chứa và phần dự phòng

Với danh sách dựa trên mảng, gọi:

- `n = size()`: số phần tử đang thuộc danh sách.
- `c`: sức chứa (capacity), tức số ô của mảng lưu trữ.
- `c - n`: số ô dự phòng chưa dùng.
- `n / c`: tỷ lệ sử dụng sức chứa, khi `c > 0`.

Ví dụ `n = 100.000`, `c = 150.000`: còn 50.000 ô dự phòng; phần dự phòng bằng 50% số phần tử đang dùng nhưng chỉ bằng khoảng 33,3% sức chứa. Hai tỷ lệ có mẫu số khác nhau nên không được gọi lẫn nhau.

Một ô trong `Object[]` chứa **tham chiếu**, không chứa toàn bộ đối tượng. Ô chưa dùng thường chứa `null`; nó vẫn chiếm chỗ trong mảng, nhưng không đồng nghĩa có một đối tượng dữ liệu đã được tạo sẵn. Đặt trước sức chứa một triệu ô không tạo trước một triệu `String`, `Integer` hoặc đối tượng nghiệp vụ.

Sơ đồ minh họa, không mô tả địa chỉ thật:

```text
ArrayList
  |
  +--> Object[]: [ref A][ref B][ref A][null][null]
                    |      |      |
                    +------|------+--> đối tượng A
                           +---------> đối tượng B

size = 3; sức chứa = 5; số đối tượng dữ liệu khác nhau = 2
```

Việc cùng một tham chiếu xuất hiện nhiều lần làm tăng số phần tử và số ô đang dùng, nhưng không sao chép đối tượng dữ liệu.

## 3. `ArrayList`: đổi phần dự phòng lấy ít lần cấp phát hơn

`ArrayList` sử dụng mảng có thể mở rộng. Khi còn chỗ, thêm cuối chủ yếu ghi một tham chiếu vào ô tiếp theo; khi hết chỗ, việc tăng sức chứa có thể cần mảng mới và sao chép tham chiếu. Phần dự phòng giúp nhiều lần thêm sau đó tránh phải thay mảng.

API bảo đảm sức chứa đủ cho số phần tử; không quy định một hệ số tăng cố định cho mọi phiên bản. Không xây logic ứng dụng dựa trên nhận định “luôn tăng đúng 1,5 lần”. [Tài liệu `ArrayList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html).

### 3.1. Chọn thời điểm cấp phát

- Biết gần đúng số phần tử cuối cùng: truyền sức chứa ban đầu hoặc gọi `ensureCapacity` trước một đợt thêm lớn.
- Chưa biết quy mô: để danh sách tăng tự động thường đơn giản hơn; đo trước khi đặt một mức dự phòng lớn cho mọi danh sách.
- Có nhiều danh sách nhỏ hoặc rỗng: chi phí cố định của từng collection và từng mảng có thể quan trọng hơn chi phí mỗi phần tử ở danh sách lớn.

Ở OpenJDK 21, constructor không đối số dùng một mảng rỗng chung và hoãn cấp phát mảng riêng đến khi cần. Vì vậy không suy từ mô tả sức chứa mặc định rằng mọi danh sách rỗng đã giữ riêng mười ô. Đây là chi tiết triển khai cần gắn với phiên bản khi phân tích heap.

### 3.2. Xóa phần tử và thu nhỏ mảng là hai việc khác nhau

Trong OpenJDK 21, xóa phần tử khỏi `ArrayList` bỏ tham chiếu không còn dùng ở phần đuôi sau khi dịch chuyển; `clear()` bỏ các tham chiếu phần tử nhưng giữ mảng hiện có. Vì thế `size() == 0` chưa chứng minh mảng dự phòng đã được giải phóng.

`trimToSize()` yêu cầu sức chứa về số phần tử hiện tại. Nó có thể tạo và sao chép sang mảng nhỏ hơn; không nên gọi sau mỗi lần `add` trong một vòng lặp. Cách đó có thể làm mất lợi ích của tăng trưởng có dự phòng và khiến tổng lượng sao chép tăng mạnh.

Thu nhỏ sau một đợt dữ liệu lớn có thể hợp lý nếu danh sách sẽ sống lâu với ít phần tử. Nếu sắp nạp lại quy mô tương tự, giữ mảng lại có thể tiết kiệm cấp phát. [Mã nguồn `ArrayList` của OpenJDK 21](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/ArrayList.java).

### 3.3. Bộ nhớ ổn định và bộ nhớ tại thời điểm mở rộng

Giả sử cần thay mảng cũ sức chứa `c` bằng mảng mới sức chứa `c'`. Trong lúc sao chép, hai mảng có thể cùng tồn tại. Phần lưu trữ đỉnh khi đó gần với tổng hai mảng, cộng các đối tượng liên quan, thay vì chỉ mảng mới.

Sau thao tác, mảng cũ chỉ trở thành đối tượng có thể được bộ gom rác thu hồi khi không còn được giữ lại; bộ nhớ tiến trình không nhất thiết giảm ngay. Cần phân biệt:

- **Bộ nhớ đang được giữ**: cấu trúc vẫn còn sống sau khi thao tác xong.
- **Lượng cấp phát trong một khoảng thời gian**: gồm cả các mảng cũ đã bị thay thế.
- **Mức đỉnh**: lượng bộ nhớ cần đồng thời trong giai đoạn chuyển đổi.

Đặt trước sức chứa phù hợp có thể giảm số lần thay mảng và lượng cấp phát tạm, nhưng đặt quá cao làm tăng phần giữ lại không dùng.

## 4. `LinkedList`: không có mảng dự phòng nhưng có nút cho từng phần tử

Trong OpenJDK 21, mỗi phần tử `LinkedList` đi qua một đối tượng nút riêng. Một nút giữ tham chiếu dữ liệu, nút kế tiếp và nút trước đó. Ngoài ba tham chiếu còn có phần đầu đối tượng (object header) và khoảng đệm do căn chỉnh (alignment). [Mã nguồn `LinkedList`](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/LinkedList.java).

```text
LinkedList
  first -------------------------------------------+
  last ---------------------------------------+    |
                                              |    v
null <--> [prev | item A | next] <--> [prev | item B | next] <--> null
           đối tượng nút 1             đối tượng nút 2
                     |                           |
                     v                           v
                đối tượng A                 đối tượng B
```

Dù `item` là `null`, nút chứa phần tử đó vẫn tồn tại. Nếu cùng một đối tượng được thêm `n` lần, danh sách vẫn cần `n` nút; chỉ đối tượng dữ liệu được dùng chung.

“Không có ô dự phòng” không kéo theo “ít bộ nhớ hơn”. Danh sách mảng thường trả chi phí vài byte tham chiếu cho một ô; danh sách liên kết trả chi phí một đối tượng nút hoàn chỉnh cho mỗi phần tử. Nhiều đối tượng nhỏ còn làm tăng công việc cấp phát và theo dõi của bộ gom rác.

Các ô mảng nằm liên tiếp về mặt bố trí mảng, còn đối tượng dữ liệu không nhất thiết nằm cạnh nhau. Với danh sách liên kết, duyệt cần lần theo tham chiếu giữa các nút; tính cục bộ bộ nhớ thường kém thuận lợi hơn. Đây là yếu tố cần đo theo tải thực tế, không phải bảo đảm thời gian chạy trên mọi máy.

Cũng không được chọn `LinkedList` chỉ vì nghe “chèn/xóa O(1)”: phải có sẵn vị trí nút thông qua thao tác phù hợp, chẳng hạn iterator đã đứng đúng chỗ. Truy cập bằng chỉ số vẫn phải tìm vị trí trước. [Tài liệu `LinkedList`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html).

## 5. `HashMap`: trả cả bảng bucket lẫn nút ánh xạ

Một ánh xạ khóa–giá trị khác một phần tử danh sách. Khi báo bộ nhớ mỗi phần tử cho map, nên ghi rõ đơn vị là **mỗi ánh xạ**.

Ở OpenJDK 21, cấu trúc điển hình gồm đối tượng map, một mảng tham chiếu bucket và các nút giữ mã băm, khóa, giá trị, liên kết. Bucket trống vẫn chiếm một ô mảng. Một bucket có thể chứa nhiều nút; vì vậy số bucket trống không thể suy bằng `số bucket - số ánh xạ` như ô dự phòng của `ArrayList`. [Mã nguồn `HashMap`](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/HashMap.java).

Gọi `b` là số bucket, `n` là số ánh xạ, `f` là hệ số tải (load factor). `n / b` mô tả tải trung bình, không phải tỷ lệ bucket có dữ liệu. Với giả định thông thường và chưa chạm giới hạn kích thước, vượt ngưỡng theo `b × f` dẫn đến mở rộng bảng. Giá trị mặc định của `f` là `0.75`.

Ví dụ mô hình: `b = 1.024`, `f = 0.75` cho ngưỡng 768 ánh xạ. Đặt `new HashMap<>(1_000)` không có nghĩa chắc chắn chứa 1.000 ánh xạ mà không mở rộng; tham số constructor là yêu cầu sức chứa, không phải số ánh xạ dự kiến. API Java 19+ có `HashMap.newHashMap(expectedMappings)` dành cho số ánh xạ dự kiến; ví dụ chạy Java 17 phải dùng constructor phù hợp.

Chọn bảng rất lớn giảm áp lực mở rộng nhưng giữ thêm ô bucket, và duyệt map còn chịu ảnh hưởng của cả số bucket lẫn số ánh xạ. Không tùy tiện giảm hệ số tải chỉ để “nhanh hơn”. [Tài liệu `HashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).

Một mô hình nút thông thường cũng không bao quát mọi trường hợp: bucket có thể dùng nút cây với nhiều trường hơn; cấu trúc duy trì thứ tự có thể cần liên kết phụ. Muốn biết chi phí thực tế phải nhận diện đúng lớp triển khai và dạng nút đang có.

## 6. Xây mô hình bộ nhớ mỗi phần tử

### 6.1. Công thức trước, số byte sau

Đặt:

- `S_list`, `S_linked`, `S_map`: kích thước riêng của đối tượng collection tương ứng.
- `A(c)`: kích thước toàn bộ mảng gồm `c` tham chiếu, đã tính đầu mảng và căn chỉnh.
- `N_linked`: kích thước một nút danh sách liên kết.
- `N_map`: kích thước một nút map thông thường.
- `D`: tổng kích thước các đối tượng dữ liệu khác nhau mà phép tính quyết định đưa vào.

Với cấu trúc đã được cấp phát và bỏ qua các đối tượng phụ khác, mô hình đơn giản là:

```text
ArrayList:   M_structure ≈ S_list   + A(c)
LinkedList:  M_structure ≈ S_linked + n × N_linked
HashMap:     M_structure ≈ S_map    + A(b) + n × N_map

M_graph ≈ M_structure + D
bytes_per_element = M_structure / n       (n > 0)
```

Đây là mô hình cho các cấu trúc thông thường, không phải công thức chính xác cho mọi trạng thái rỗng, view được tạo thêm, nút cây hoặc collection tùy biến. Phải định nghĩa `D` trước khi cộng để tránh tính hai lần đối tượng dùng chung.

Chi phí trung bình chia tổng cho `n` khác chi phí tăng thêm của một thao tác `add`. Một lần thêm có thể không tạo mảng; lần kế tiếp có thể kích hoạt cấp phát một mảng lớn. Khi `n = 0`, không báo “byte mỗi phần tử” bằng phép chia này; hãy báo tổng chi phí của collection rỗng.

### 6.2. Ví dụ có giả định công khai

Xét một **mô hình minh họa**, không phải số đo trên máy người đọc:

- Tham chiếu 4 byte; đầu đối tượng 12 byte; đầu mảng tham chiếu 16 byte.
- Kích thước đối tượng/mảng được làm tròn lên bội số 8 byte.
- Nút `LinkedList` gồm đầu đối tượng và ba tham chiếu, nên trong mô hình là 24 byte.
- `n = 100.000`, sức chứa `ArrayList` được giả sử là `c = 150.000`.
- Chưa tính đối tượng dữ liệu và phần cố định của hai collection.

| Phần được tính | Phép tính theo mô hình | Kết quả |
| --- | --- | --- |
| Mảng tham chiếu của `ArrayList` | Làm tròn `16 + 150.000 × 4` | 600.016 byte |
| Trong đó, các ô dự phòng | `50.000 × 4` | 200.000 byte |
| Các nút của `LinkedList` | `100.000 × 24` | 2.400.000 byte |
| Mảng chia cho số phần tử đang dùng | `600.016 / 100.000` | Khoảng 6 byte/phần tử |
| Các nút chia cho số phần tử | `2.400.000 / 100.000` | 24 byte/phần tử |

Dù mảng có phần dự phòng khá lớn, chi phí cấu trúc trong mô hình vẫn thấp hơn phần nút liên kết. Không chuyển kết quả này thành kết luận “mọi `ArrayList` tốn đúng 6 byte/phần tử”: đổi sức chứa, số phần tử, độ rộng tham chiếu hoặc căn chỉnh sẽ đổi kết quả.

Căn chỉnh cũng khiến số byte không luôn tăng đều từng tham chiếu. Một mảng thêm một ô có thể nằm trong khoảng đệm đang có; thêm ô khác lại vượt sang bội số căn chỉnh tiếp theo. JOL minh họa cách xem đầu đối tượng, trường và căn chỉnh trong [ví dụ cơ bản chính thức](https://raw.githubusercontent.com/openjdk/jol/master/jol-samples/src/main/java/org/openjdk/jol/samples/JOLSample_01_Basic.java).

### 6.3. Chi phí dữ liệu và tự đóng hộp

`ArrayList<Integer>` giữ các tham chiếu tới `Integer`; `int[]` giữ trực tiếp các giá trị `int`. Vì vậy hai cấu trúc có cùng số lượng số nguyên chưa có cùng mô hình bộ nhớ.

Khi tự đóng hộp (autoboxing), không được mặc định mỗi vị trí luôn tạo một `Integer` mới. `Integer.valueOf` có bộ đệm cho một miền giá trị; tham chiếu còn có thể được chia sẻ chủ động. Trái lại, dữ liệu lớn có thể bao gồm nhiều đối tượng khác nhau. [Tài liệu `Integer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html#valueOf(int)).

Nếu dữ liệu mỗi phần tử giữ nhiều chuỗi, mảng hoặc đồ thị con lớn, `D` có thể chiếm phần chủ yếu. Khi đó tối ưu vài ô tham chiếu chưa chắc giải quyết được vấn đề bộ nhớ. Hãy đo cả cấu trúc và dữ liệu để biết phần nào đáng tối ưu.

## 7. Thí nghiệm Java 17+: sức chứa không phải số phần tử

Lưu chương trình đầy đủ sau thành `CapacityDemo.java`:

```java
import java.util.ArrayList;

public class CapacityDemo {
    public static void main(String[] args) {
        ArrayList<StringBuilder> values = new ArrayList<>(8);
        System.out.println("initial size=" + values.size());

        StringBuilder shared = new StringBuilder("A");
        values.add(shared);
        values.add(shared);
        values.add(shared);

        values.ensureCapacity(100);
        System.out.println("after reserve size=" + values.size());
        System.out.println("same object=" + (values.get(0) == values.get(1)));

        values.remove(2);
        shared.append("B");
        System.out.println("after remove size=" + values.size());
        System.out.println("values=" + values);

        values.clear();
        System.out.println("after clear size=" + values.size());

        values.trimToSize();
        System.out.println("after trim size=" + values.size());
        System.out.println("outside reference=" + shared);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CapacityDemo.java
java CapacityDemo
```

Kết quả stdout:

```text
initial size=0
after reserve size=3
same object=true
after remove size=2
values=[AB, AB]
after clear size=0
after trim size=0
outside reference=AB
```

Giải thích theo đúng điều đã quan sát:

- Constructor có sức chứa 8 vẫn tạo danh sách với `size() == 0`.
- `ensureCapacity(100)` không thêm phần tử; danh sách vẫn có ba vị trí.
- Ba lần `add(shared)` dùng cùng đối tượng. Sau khi xóa một vị trí và sửa `shared`, hai vị trí còn lại cùng hiển thị `AB`.
- `clear()` và `trimToSize()` đều cho `size() == 0`; chỉ nhìn kết quả `size()` không thể phân biệt mảng đang lớn hay đã thu nhỏ.
- Biến `shared` bên ngoài vẫn giữ đối tượng sau khi xóa khỏi danh sách. Xóa tham chiếu trong collection không đồng nghĩa đối tượng dữ liệu phải được thu hồi.

`ArrayList` không có phương thức công khai `capacity()`. Chương trình trên kiểm chứng ngữ nghĩa số phần tử và tham chiếu; nó không in số byte, không đo dung lượng thật của mảng và không chứng minh thời điểm bộ gom rác thu hồi đối tượng.

## 8. Đo đúng: đối tượng riêng, đồ thị có thể đi tới và bộ nhớ giữ lại

Ba khái niệm cần phân biệt khi xem công cụ:

| Khái niệm | Câu hỏi cần trả lời | Điểm dễ nhầm |
| --- | --- | --- |
| Kích thước riêng (shallow size) | Bản thân một đối tượng chiếm bao nhiêu byte? | Đo riêng `ArrayList` chưa tính mảng phía sau |
| Kích thước đồ thị có thể đi tới | Từ collection có thể lần theo tham chiếu tới các đối tượng nào? | Dữ liệu dùng chung cũng có thể được tính vào đồ thị |
| Bộ nhớ giữ lại (retained size) | Những đối tượng nào sẽ có thể được thu hồi nếu bỏ đối tượng gốc này, xét toàn bộ các gốc giữ sống khác? | Không thể suy chỉ từ tổng đồ thị của một collection |

JOL hỗ trợ xem bố trí đối tượng và tổng dung lượng của đồ thị. Để so sánh cấu trúc, có thể cho hai collection cùng giữ một đối tượng dùng chung nhiều lần. Cách này hạn chế việc số đối tượng dữ liệu khác nhau che khuất chi phí mảng và nút. Nó không đại diện cho mọi tải dữ liệu thực tế. [Dự án JOL của OpenJDK](https://github.com/openjdk/jol).

### 8.1. Chương trình đo với JOL

Ví dụ đầy đủ dưới đây cần thư viện `org.openjdk.jol:jol-core` có trên classpath. Các lệnh giả sử tệp thư viện đã được chuẩn bị dưới tên `jol-core.jar`. Lưu mã thành `MemoryProbe.java`:

```java
import java.util.ArrayList;
import java.util.LinkedList;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;

public class MemoryProbe {
    public static void main(String[] args) {
        int count = 10_000;
        Object shared = new Object();
        ArrayList<Object> array = new ArrayList<>(20_000);
        LinkedList<Object> linked = new LinkedList<>();

        for (int i = 0; i < count; i++) {
            array.add(shared);
            linked.add(shared);
        }

        System.out.println(VM.current().details());
        System.out.println("ArrayList object only:");
        System.out.println(ClassLayout.parseInstance(array).toPrintable());
        System.out.println("ArrayList reachable graph:");
        System.out.println(GraphLayout.parseInstance((Object) array).toFootprint());
        System.out.println("LinkedList reachable graph:");
        System.out.println(GraphLayout.parseInstance((Object) linked).toFootprint());

        array.trimToSize();
        System.out.println("ArrayList reachable graph after trim:");
        System.out.println(GraphLayout.parseInstance((Object) array).toFootprint());
    }
}
```

Trên Windows:

```shell
javac --release 17 -cp jol-core.jar MemoryProbe.java
java -cp ".;jol-core.jar" MemoryProbe
```

Trên Linux/macOS, dùng dấu `:` giữa các phần của classpath:

```shell
java -cp ".:jol-core.jar" MemoryProbe
```

Không có một bảng stdout byte cố định áp dụng cho mọi môi trường. Đọc kết quả theo các kiểm tra sau:

1. Phần `ClassLayout` chỉ nói về đối tượng `ArrayList` gốc; đừng lấy nó làm toàn bộ chi phí danh sách.
2. Trong footprint của danh sách mảng, tìm mảng tham chiếu và kích thước của nó; so sánh trước/sau `trimToSize`.
3. Trong footprint của danh sách liên kết, tìm số đối tượng nút; với 10.000 phần tử, cấu trúc OpenJDK thông thường có 10.000 nút.
4. Đối tượng `shared` chỉ được đếm một lần trong từng đồ thị dù có nhiều đường tham chiếu tới nó. Cộng hai báo cáo riêng sẽ đếm nó hai lần nếu đang muốn tính tổng hợp nhất của cả hai đồ thị.
5. Nếu JOL cảnh báo không xác định được thông tin JVM hoặc chỉ ước tính một số thuộc tính, đọc cảnh báo và kiểm chứng cấu hình trước khi dùng kết quả như số đo chính xác.

Vì cả `array` và `linked` giữ `shared`, đối tượng đó không được giữ sống độc quyền bởi một danh sách. Tổng đồ thị mà JOL đi tới không tự động bằng retained size. Phân tích retained size cần công cụ phân tích heap có ngữ cảnh các tham chiếu giữ sống và quan hệ thống trị (dominator).

### 8.2. Ghi điều kiện đo để người khác tái lập

Ghi phiên bản JDK/JVM, kiến trúc, tùy chọn tham chiếu nén, căn chỉnh, công cụ và phiên bản công cụ; đồng thời ghi `n`, sức chứa dự kiến, tỷ lệ xóa, dữ liệu dùng chung hay riêng, có tính dữ liệu hay chỉ cấu trúc. Chạy phép so sánh trong cùng điều kiện.

Không lấy `Runtime.totalMemory() - Runtime.freeMemory()` trước/sau một lần `add` rồi kết luận số byte của một phần tử. Heap còn có hoạt động của JVM, thư viện, thời điểm gom rác và vùng nhớ được giữ để tái sử dụng. Gọi `System.gc()` cũng không biến phép trừ đó thành phép đo kích thước đối tượng chính xác.

Nếu vấn đề là nhiều cấp phát trong một luồng xử lý, cần theo dõi lượng cấp phát và hoạt động GC theo tải đại diện. Nếu vấn đề là heap còn sống quá lớn sau xử lý, cần xem cấu trúc đang giữ lại và các đường tham chiếu. Hai vấn đề có thể cần cách tối ưu khác nhau.

## 9. Chọn cấu trúc và dự phòng theo tình huống

| Tình huống | Hướng kiểm tra | Đánh đổi cần nhớ |
| --- | --- | --- |
| Biết gần đúng số phần tử và chủ yếu thêm cuối/duyệt | Đặt sức chứa `ArrayList` theo quy mô hợp lý | Giảm thay mảng nhưng giữ ô dự phòng nếu ước lượng quá cao |
| Danh sách từng rất lớn, sau đó nhỏ và sống lâu | Đo phần mảng giữ lại, cân nhắc `trimToSize` sau giai đoạn thu nhỏ | Thu nhỏ có thể sao chép; sắp tăng lại thì lợi ích có thể mất |
| Đang dùng `LinkedList` vì muốn tránh ô thừa | So chi phí nút, số đối tượng và kiểu thao tác thật | Tránh ô thừa nhưng trả chi phí nút cho từng vị trí |
| Hàng đợi/ngăn xếp không cần chứa `null` | Đo thêm phương án `ArrayDeque` dựa trên mảng | Không cần nút cho từng phần tử nhưng vẫn có sức chứa và mở rộng |
| Map có nhiều ánh xạ | Tính cả bảng bucket và nút, chọn sức chứa theo số ánh xạ dự kiến | Bucket và ngưỡng tải không cùng nghĩa với phần tử danh sách |
| Khối lượng lớn dữ liệu số nguyên thủy | So sánh mô hình mảng nguyên thủy và collection kiểu bọc | Phải giữ đúng yêu cầu API, thao tác và cách biểu diễn dữ liệu |

Phương án `ArrayDeque` ở đây dựa trên việc triển khai dùng mảng vòng; nó không chấp nhận phần tử `null`. Cần giữ đúng yêu cầu chức năng trước khi so bộ nhớ. [Mã nguồn `ArrayDeque`](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/ArrayDeque.java).

## 10. Tự kiểm tra

1. Một `ArrayList` có 1.000 phần tử và sức chứa 1.600 có bao nhiêu ô dự phòng? Có thể kết luận có 1.600 đối tượng dữ liệu không?
2. Vì sao danh sách liên kết không cần dự phòng mà vẫn có thể tốn nhiều bộ nhớ hơn danh sách mảng?
3. Hai collection cùng giữ một đối tượng lớn: có được cộng hai tổng đồ thị riêng để suy ra lượng bộ nhớ khác nhau của cả hai không?
4. Sau `clear()`, số phần tử bằng 0. Cần kiểm tra gì trước khi kết luận bộ nhớ đã được trả lại?
5. Một báo cáo nói “8 byte/phần tử” nhưng không nêu JVM, sức chứa và có tính dữ liệu hay không. Vì sao chưa đủ để dùng ra quyết định?

Gợi ý đối chiếu:

1. Có 600 ô dự phòng; số đối tượng dữ liệu khác nhau phụ thuộc tham chiếu trùng, `null` và dữ liệu thực tế.
2. Mỗi nút cần đầu đối tượng, liên kết và căn chỉnh; các phần đó có thể lớn hơn phần ô dự phòng của mảng.
3. Không; cần hợp nhất theo danh tính đối tượng để tránh tính trùng, và phân biệt tổng đồ thị với bộ nhớ giữ lại độc quyền.
4. Kiểm tra mảng/bảng còn được collection giữ hay không, dữ liệu còn được nơi khác tham chiếu không, và đang nói tới heap sống, heap đã cấp cho JVM hay bộ nhớ tiến trình.
5. Con số chưa xác định mẫu số, phạm vi đo và giả định bố trí; không thể so công bằng với một cấu trúc khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-004">Boxing, cấp phát đối tượng, thu gom rác và tính cục bộ bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Boxing, cấp phát đối tượng, thu gom rác và tính cục bộ bộ nhớ

## 1. Từ thao tác trên collection đến chi phí phần cứng

Hai cách lưu và duyệt cùng một tập số có thể cùng có độ phức tạp O(n) nhưng khác đáng kể về thời gian, lượng cấp phát và bộ nhớ đang giữ. Big O mô tả cách số thao tác tăng theo quy mô; nó không mô tả đầy đủ chi phí chuyển kiểu, số đối tượng, các lần lần theo tham chiếu hay thời gian chờ dữ liệu từ bộ nhớ.

Trong bài này, bốn khái niệm nối với nhau:

- **Đóng hộp (boxing)**: chuyển giá trị nguyên thủy, chẳng hạn `int`, sang đối tượng kiểu bọc như `Integer`.
- **Cấp phát (allocation)**: dành bộ nhớ cho đối tượng hoặc mảng thực sự xuất hiện khi chương trình chạy.
- **Thu gom rác (garbage collection, GC)**: xác định và thu hồi bộ nhớ của đối tượng không còn cần giữ sống theo quy tắc của JVM.
- **Tính cục bộ bộ nhớ (memory locality)**: mức độ các truy cập tiếp theo có thể tận dụng dữ liệu đã ở gần hoặc đã nằm trong bộ nhớ đệm của CPU.

Chuỗi nguyên nhân có thể là:

```text
Chọn biểu diễn dữ liệu và kiểu collection
    -> cần giá trị nguyên thủy hay tham chiếu tới đối tượng?
    -> có boxing, nút, mảng thay thế hoặc đối tượng tạm?
    -> lượng cấp phát, lượng dữ liệu sống và đồ thị tham chiếu thay đổi
    -> công việc GC và đường truy cập bộ nhớ thay đổi
    -> thông lượng, độ trễ và dung lượng bộ nhớ có thể thay đổi
```

Đây là đường phân tích để đặt giả thuyết và đo, không phải công thức khẳng định mọi collection có boxing đều chậm trong mọi tình huống.

## 2. Boxing và unboxing xuất hiện ở đâu?

### 2.1. Collection tổng quát giữ kiểu tham chiếu

Với Java 17/21, tham số kiểu của generic phải là kiểu tham chiếu. `List<Integer>` hợp lệ, còn `List<int>` không hợp lệ. Vì thế một danh sách số nguyên tiêu chuẩn thường giữ tham chiếu tới `Integer`, trong khi `int[]` chứa trực tiếp các giá trị nguyên thủy.

Đoạn sau minh họa phép chuyển đổi; cần đặt trong một phương thức có import phù hợp:

```java
List<Integer> values = new ArrayList<>();
int input = 1_000_000;
values.add(input);        // int -> Integer: boxing
int output = values.get(0); // Integer -> int: unboxing
```

Có thể hiểu các phép chuyển đổi này tương ứng với việc lấy đối tượng kiểu bọc và đọc lại giá trị nguyên thủy, thường thấy dưới dạng `Integer.valueOf(input)` và `intValue()` trong mã biên dịch. Cách diễn đạt đó không phải lời hứa về số đối tượng vật lý sau khi JIT tối ưu.

Vòng lặp `for (int value : values)` cũng unbox từng phần tử được lấy ra. Phép toán số học với biến kiểu bọc có thể unbox trước khi tính, rồi boxing trở lại nếu kết quả gán cho kiểu bọc. Các quy tắc chuyển đổi được nêu trong [JLS §5.1.7–§5.1.8](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.7).

### 2.2. Bộ đệm đối tượng kiểu bọc không phải bộ nhớ đệm CPU

`Integer.valueOf(int)` dùng lại đối tượng cho ít nhất miền từ `-128` đến `127`, và có thể dùng lại cho giá trị ngoài miền đó. Do đó boxing không luôn tạo đối tượng mới. [Tài liệu `Integer.valueOf`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html#valueOf(int)).

Cần phân biệt hai từ “cache”:

| Cơ chế | Cái được dùng lại | Tác động chính |
| --- | --- | --- |
| Bộ đệm đối tượng `Integer` | Một số đối tượng kiểu bọc | Có thể tránh cấp phát đối tượng kiểu bọc mới |
| Bộ nhớ đệm CPU | Các khối dữ liệu được phần cứng nạp gần CPU | Có thể giảm thời gian chờ khi truy cập dữ liệu |

Dùng lại một `Integer` không bảo đảm mọi truy cập đều trúng bộ nhớ đệm CPU. Ngược lại, một đối tượng mới vẫn có thể được truy cập thuận lợi trong một giai đoạn nhất định.

Không dùng `==` giữa hai biến `Integer` để kiểm tra giá trị số. Nó so danh tính tham chiếu và có thể tình cờ cho kết quả mong muốn nhờ bộ đệm. Dùng `equals`, `Objects.equals` khi có thể có `null`, hoặc chuyển về nguyên thủy sau khi xử lý `null` theo yêu cầu nghiệp vụ. Không dựa vào một giá trị lớn để khẳng định rằng hai lần boxing chắc chắn cho tham chiếu khác nhau.

### 2.3. `null` và ngữ nghĩa phải được giải quyết trước tối ưu

Collection kiểu bọc có thể cho phép `null`; một giá trị `int` không biểu diễn được `null`. Unbox một tham chiếu `null` ném `NullPointerException`.

Khi chuyển từ `List<Integer>` sang `int[]` hoặc một cấu trúc chuyên cho nguyên thủy, phải quyết định cách biểu diễn dữ liệu thiếu. Không tùy tiện đổi `null` thành `0` nếu `0` là dữ liệu hợp lệ. Giảm cấp phát không được làm thay đổi kết quả chương trình.

## 3. Cấp phát không chỉ đến từ boxing

Một thao tác trên collection có thể liên quan nhiều lớp đối tượng:

| Nguồn cấp phát có thể có | Ví dụ | Điều cần phân biệt |
| --- | --- | --- |
| Đối tượng kiểu bọc | Đưa giá trị số vào collection tham chiếu | Bộ đệm hoặc tối ưu có thể tránh một số cấp phát |
| Nút cấu trúc | Thêm phần tử vào danh sách liên kết hoặc ánh xạ vào bảng băm | Nút và đối tượng dữ liệu là hai đối tượng khác nhau |
| Mảng lưu trữ mới | Mở rộng danh sách mảng | Mảng mới không đồng nghĩa sao chép sâu các phần tử |
| Collection trung gian | Tạo một danh sách kết quả ở mỗi bước xử lý | Các danh sách có thể cùng tham chiếu dữ liệu cũ |
| Đối tượng hỗ trợ | Iterator, đối tượng của pipeline hoặc lambda trong một số trường hợp | Không được mặc định mỗi lần gọi luôn tạo một đối tượng vật lý |

Ví dụ, đặt trước sức chứa `ArrayList` có thể giảm số mảng thay thế nhưng không tự loại bỏ boxing ở `add(intValue)`. Tương tự, đổi `LinkedList<Integer>` sang `ArrayList<Integer>` có thể giảm các nút nhưng vẫn còn các tham chiếu và đối tượng `Integer`.

### 3.1. Đếm thao tác trong mã khác đếm đối tượng thực sự

JVM có thể tối ưu một số đối tượng không thoát ra ngoài phạm vi tính toán, chẳng hạn bằng phân tích thoát (escape analysis) và thay thế bằng các giá trị thành phần. Các khả năng đó phụ thuộc JVM, đường thực thi và quyết định biên dịch; không phải mọi đối tượng tạm đều được loại bỏ. [Tài liệu tối ưu HotSpot](https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html).

Vì vậy cả hai kết luận sau đều thiếu căn cứ:

- “Mã có 10.000 phép boxing nên chắc chắn cấp phát đúng 10.000 đối tượng.”
- “JIT có escape analysis nên mọi boxing trong vòng lặp đều miễn phí.”

Đối tượng được lưu trong collection trả về hoặc được giữ để sử dụng về sau có ràng buộc khác đối tượng chỉ phục vụ một phép tính cục bộ. Cần đo lượng cấp phát của chính trường hợp đó, trong điều kiện biên dịch đại diện.

### 3.2. Một bộ cộng dồn kiểu bọc dễ tạo công việc thừa

Đoạn minh họa:

```java
Long total = 0L;
for (int value : primitiveValues) {
    total += value;
}
```

Về ngữ nghĩa, mỗi vòng phải đọc giá trị `long` từ `total`, cộng rồi chuyển kết quả về `Long`. Nếu không cần khả năng biểu diễn `null` hoặc một API đòi kiểu bọc, dùng `long total = 0L` làm rõ ý định và tránh yêu cầu boxing lặp lại ở cấp mã nguồn. Khi phải trả `Long`, có thể boxing ở ranh giới trả kết quả thay vì dùng kiểu bọc cho toàn bộ quá trình cộng.

Số đối tượng thực tế vẫn phải kiểm chứng; ví dụ này giải thích nguồn công việc có thể tránh, không đưa ra số byte hoặc số lần GC cố định.

## 4. Từ cấp phát đến công việc thu gom rác

### 4.1. Cấp phát nhiều và giữ nhiều là hai vấn đề khác nhau

Cần tách ba chỉ số:

1. **Lượng cấp phát trên một đơn vị công việc**: chẳng hạn byte cho mỗi yêu cầu hoặc mỗi lần chạy toàn bộ một batch.
2. **Tốc độ cấp phát**: byte mỗi giây.
3. **Tập dữ liệu sống**: các đối tượng còn phải giữ sống tại thời điểm đang xét.

Một dịch vụ có thể tạo rất nhiều đối tượng ngắn hạn nhưng chỉ giữ một tập dữ liệu nhỏ. Dịch vụ khác cấp phát ít mỗi giây nhưng giữ một map lớn trong nhiều giờ. Chỉ nhìn mức heap đang dùng tại một thời điểm không mô tả đầy đủ hai tải này.

Ví dụ số học giả định: mỗi yêu cầu cấp phát 2.000 byte và xử lý 10.000 yêu cầu/giây thì tốc độ cấp phát là 20.000.000 byte/giây. Đây là kết quả nhân của hai số liệu, không phải dự đoán chu kỳ GC. Sau tối ưu, nếu tốc độ cấp phát mỗi giây giảm vì chương trình xử lý ít yêu cầu hơn thì chưa thể kết luận tốt hơn; hãy xem cả byte/yêu cầu lẫn thông lượng và độ trễ.

### 4.2. Vì sao đối tượng ngắn hạn vẫn có chi phí?

Cấp phát đối tượng nhỏ có thể được JVM thực hiện rất nhanh. Tuy nhiên hệ thống vẫn cần bộ nhớ cho chúng; lượng cấp phát cao làm tăng nhu cầu thu hồi hoặc tái sử dụng vùng nhớ. Công việc GC còn phụ thuộc số đối tượng sống, các tham chiếu phải theo dõi, kiểu bộ gom rác và cấu hình heap.

Với G1 trên JDK 21, phần lớn cấp phát thông thường đi vào vùng thế hệ trẻ, còn đối tượng rất lớn có cách xử lý riêng. Thu hồi có thể phải sao chép các đối tượng còn sống. Cơ chế này giúp giải thích vì sao thời gian sống cũng quan trọng; không được áp quy trình của G1 cho mọi collector. [Tài liệu G1](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html).

Không suy rằng thêm một đối tượng sẽ kích hoạt ngay một lần GC, cũng không suy rằng giảm một nửa số đối tượng luôn giảm một nửa thời gian tạm dừng. Quan hệ còn phụ thuộc trạng thái heap, kích thước, thời gian sống và các hoạt động đồng thời.

### 4.3. Collection có thể kéo dài thời gian sống ngoài dự kiến

Một danh sách tạm sau khi xử lý xong sẽ ít gây vấn đề hơn một cache không giới hạn tiếp tục giữ mọi kết quả. Khi đối tượng vẫn được một collection còn sống tham chiếu mạnh, GC không thể tự quyết rằng dữ liệu đó “đã cũ nên bỏ”.

Ví dụ cần kiểm tra trong ứng dụng:

- Map dùng làm cache không có giới hạn kích thước hoặc chính sách loại bỏ.
- Danh sách gắn với phiên làm việc nhưng giữ dữ liệu của nhiều yêu cầu cũ.
- Hàng đợi có tốc độ đưa vào lâu dài lớn hơn tốc độ xử lý.
- Collection được giữ trong trường `static` hoặc đối tượng sống lâu ngoài ý định.

Xử lý đúng thời gian sống và giới hạn dữ liệu thường có ý nghĩa trực tiếp hơn việc tìm cách làm GC thu hồi thứ vẫn còn được tham chiếu. `System.gc()` chỉ là đề nghị JVM nỗ lực thu hồi, không có bảo đảm về dung lượng hoặc thời điểm thu hồi cụ thể. [Tài liệu `System.gc`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#gc()).

## 5. Tính cục bộ: CPU cần đi qua những đâu để lấy một số?

### 5.1. `int[]`, `ArrayList<Integer>` và danh sách liên kết

Sơ đồ sau minh họa các tầng truy cập; vị trí thật do JVM quản lý:

```text
int[]
  [1000][1001][1002][1003]          các giá trị nằm trong mảng

ArrayList<Integer>
  [ref0][ref1][ref2][ref3]          các tham chiếu nằm trong mảng
     |     |     |     |
     v     v     v     v
  Integer Integer Integer Integer  các đối tượng nằm ngoài mảng

LinkedList<Integer>
  node0 -> node1 -> node2 -> node3  phải lần theo liên kết nút
    |        |        |        |
    v        v        v        v
  Integer  Integer  Integer  Integer
```

Duyệt tuần tự `int[]` đọc trực tiếp các giá trị được bố trí liên tiếp trong mảng. Duyệt danh sách mảng chứa số kiểu bọc phải đọc tham chiếu rồi đọc giá trị trong đối tượng được trỏ tới. Với danh sách liên kết còn có tầng nút và phụ thuộc vào liên kết tới nút tiếp theo.

Các ô của mảng tham chiếu liên tiếp không có nghĩa các đối tượng được tham chiếu cũng liên tiếp. Đối tượng có thể được cấp phát hoặc di chuyển qua các giai đoạn khác nhau. Không suy thứ tự địa chỉ vật lý chỉ từ thứ tự phần tử.

### 5.2. Bộ nhớ đệm CPU và truy cập liên tiếp

CPU thường nạp dữ liệu theo các khối của bộ nhớ đệm. Duyệt dữ liệu gần nhau có thể dùng được nhiều giá trị từ dữ liệu đã nạp và giúp cơ chế nạp trước dự đoán truy cập tiếp theo. Lần theo nhiều tham chiếu tới các vị trí phân tán có thể làm CPU phải chờ nhiều hơn, đặc biệt khi dữ liệu lớn vượt khả năng chứa của các mức bộ nhớ đệm.

Đây là cơ sở cho giả thuyết rằng biểu diễn đặc hơn có thể cải thiện việc duyệt dữ liệu. Kết quả vẫn phụ thuộc kích thước tập dữ liệu, thứ tự truy cập, phần cứng và lượng tính toán thực hiện trên mỗi phần tử.

Ví dụ chính thức của JMH so sánh mảng nguyên thủy, danh sách số kiểu bọc và danh sách bị xáo thứ tự tham chiếu để làm rõ ảnh hưởng của cách truy cập bộ nhớ. Không dùng lại các con số thời gian trong ví dụ như định luật cho máy khác. [JMH: Memory Access](https://raw.githubusercontent.com/openjdk/jmh/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_39_MemoryAccess.java).

### 5.3. Đừng quy lỗi thuật toán cho bộ nhớ đệm

Nếu duyệt `LinkedList` bằng `get(i)` cho mọi chỉ số, mỗi lần có thể phải tìm nút. Toàn bộ vòng lặp có thể thành O(n²), thay vì O(n) khi dùng iterator. Chênh lệch lớn trong phép đo như vậy có thể đến từ thuật toán truy cập trước khi cần xét đến tính cục bộ.

Thứ tự phân tích hợp lý là kiểm tra thuật toán và số thao tác, sau đó kiểm tra biểu diễn dữ liệu, lượng cấp phát và đường truy cập bộ nhớ. Big O và tính cục bộ bổ sung cho nhau; không thay thế nhau.

## 6. Chương trình Java 17+ kiểm chứng ngữ nghĩa

Lưu thành `BoxingDemo.java`. Chương trình độc lập, không có thư viện ngoài:

```java
import java.util.ArrayList;
import java.util.List;

public class BoxingDemo {
    public static void main(String[] args) {
        Integer first = 42;
        Integer second = 42;
        System.out.println("cached identity=" + (first == second));

        Integer largeA = Integer.valueOf(1_000_000);
        Integer largeB = Integer.valueOf(1_000_000);
        System.out.println("same numeric value=" + largeA.equals(largeB));

        int[] primitive = new int[5];
        List<Integer> boxed = new ArrayList<>(primitive.length);
        for (int i = 0; i < primitive.length; i++) {
            primitive[i] = 1_000_000 + i;
            boxed.add(primitive[i]);
        }

        long primitiveSum = 0;
        for (int value : primitive) {
            primitiveSum += value;
        }

        long boxedSum = 0;
        for (int value : boxed) {
            boxedSum += value;
        }

        Long boxedAccumulator = 0L;
        for (int value : primitive) {
            boxedAccumulator += value;
        }

        long streamSum = boxed.stream()
                .mapToLong(Integer::longValue)
                .sum();

        System.out.println("primitive sum=" + primitiveSum);
        System.out.println("boxed sum=" + boxedSum);
        System.out.println("boxed accumulator=" + boxedAccumulator);
        System.out.println("primitive stream sum=" + streamSum);

        boxed.add(null);
        try {
            int missing = boxed.get(boxed.size() - 1);
            System.out.println(missing);
        } catch (NullPointerException ex) {
            System.out.println("unboxing null=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 BoxingDemo.java
java BoxingDemo
```

Stdout chính xác:

```text
cached identity=true
same numeric value=true
primitive sum=5000010
boxed sum=5000010
boxed accumulator=5000010
primitive stream sum=5000010
unboxing null=NullPointerException
```

Cách đọc kết quả:

- Hai phép boxing hằng `42` thể hiện trường hợp dùng chung đối tượng trong miền nhỏ được bảo đảm. Điều này không biến `==` thành phép so giá trị cho kiểu bọc nói chung.
- Giá trị lớn được kiểm tra bằng `equals`; chương trình không khẳng định hai tham chiếu phải khác nhau.
- Mảng nguyên thủy và danh sách kiểu bọc cho cùng tổng; đây là bằng chứng về kết quả, không phải bằng chứng về tốc độ ngang nhau.
- `Long boxedAccumulator` cho đúng tổng nhưng có yêu cầu unboxing/boxing trong phép gán cộng. Stdout không cho biết JIT loại bỏ bao nhiêu cấp phát.
- `mapToLong` dùng luồng giá trị `long` cho bước tính tổng, nhưng danh sách nguồn vẫn chứa `Integer` và phải đọc giá trị từ chúng.
- Chỉ sau khi tính các tổng, chương trình mới thêm `null`. Việc unbox phần tử đó gây `NullPointerException` và được bắt để in thông báo ổn định.

Chương trình không đo thời gian, số đối tượng hay GC. Không thêm một vòng `nanoTime()` vào nó rồi dùng vài lần chạy làm kết luận hiệu năng.

## 7. Tránh boxing không cần thiết trong đường xử lý

### 7.1. Giữ giá trị nguyên thủy trong phần tính toán

Dùng `int`, `long`, `double` cho biến tính toán khi ngữ nghĩa cho phép. Chỉ boxing ở ranh giới cần đối tượng, chẳng hạn đưa kết quả vào API generic. Với phép tổng lớn, còn cần chọn kiểu đủ miền giá trị; tránh boxing không giải quyết lỗi tràn số.

Nếu dữ liệu vốn đã ở `List<Integer>`, việc unbox trong phép tổng là cần thiết để tính số học. Tối ưu đáng cân nhắc là tránh tạo thêm một collection kiểu bọc chỉ để chứa các kết quả trung gian rồi lại unbox chúng.

### 7.2. Dùng luồng nguyên thủy khi phù hợp

Ví dụ minh họa trong một phương thức có sẵn `List<Integer> values` không chứa `null`:

```java
long total = values.stream()
        .mapToLong(Integer::longValue)
        .sum();
```

`Stream<Integer>` là luồng tham chiếu; `IntStream`, `LongStream` và `DoubleStream` là các dạng chuyên cho nguyên thủy. Chuyển sang dạng nguyên thủy cho các phép toán tiếp theo có thể tránh boxing kết quả ở từng bước. Tuy nhiên pipeline vẫn có cơ chế hỗ trợ riêng; không mô tả nó là “không tạo bất kỳ đối tượng nào”. [Tài liệu `java.util.stream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html).

Gọi `.boxed()` lại đưa giá trị về luồng kiểu bọc. Nếu cuối cùng thu thập vào `List<Integer>`, yêu cầu biểu diễn bằng đối tượng ở kết quả vẫn còn. `mapToInt` không xóa các đối tượng `Integer` đã tồn tại trong collection nguồn.

### 7.3. Đổi cấu trúc khi yêu cầu sử dụng cho phép

Mảng nguyên thủy phù hợp cho nhiều bài toán số với quy mô đã biết hoặc được quản lý rõ. Collection chuyên cho nguyên thủy của thư viện ngoài có thể cung cấp thêm thao tác nhưng phải xem hợp đồng API, cách biểu diễn phần tử thiếu và đo trong dự án cụ thể.

Không thay một map tra cứu nhanh bằng quét mảng chỉ để giảm đối tượng mà bỏ qua số lần tra cứu. Cũng không ép mọi dữ liệu nghiệp vụ thành mảng chỉ vì mảng đặc hơn. Cấu trúc cần đáp ứng kiểu truy cập, cập nhật và tính đúng trước, rồi mới tối ưu phần chi phí đã đo được.

## 8. Thiết kế phép đo để không kết luận nhầm

### 8.1. Tách các câu hỏi khác nhau

| Phép đo | Phần đặt trong thao tác được đo | Câu hỏi được trả lời |
| --- | --- | --- |
| Xây collection | Cấp phát collection, thêm dữ liệu, trả kết quả để được sử dụng | Chi phí tạo và vật chất hóa biểu diễn là bao nhiêu? |
| Duyệt collection có sẵn | Chỉ duyệt/tính; dữ liệu chuẩn bị ngoài phần đo | Chi phí đọc cấu trúc và tính toán là bao nhiêu? |
| Xử lý một yêu cầu hoặc batch hoàn chỉnh | Toàn bộ đường xử lý đại diện | Tối ưu có cải thiện mục tiêu thực tế không? |

Nếu tạo collection trong một phương án nhưng chuẩn bị sẵn cho phương án khác, kết quả không cô lập được chi phí duyệt. Ngược lại, chỉ đo duyệt không cho biết tổng chi phí tạo rồi hủy collection ở mỗi yêu cầu.

Muốn cô lập phần cấu trúc giữa hai collection tham chiếu, có thể dùng cùng các đối tượng dữ liệu làm đầu vào. Nếu mỗi phương án boxing riêng một bộ dữ liệu, phép so sánh còn chứa khác biệt về các đối tượng kiểu bọc và cách chúng được bố trí. Cần ghi rõ đang cố tình đo trường hợp nào.

### 8.2. Kiểm soát các bẫy của microbenchmark

Dùng JMH cho phép đo vi mô nghiêm túc và thiết lập dữ liệu phù hợp. Các điểm cần kiểm tra:

- Có giai đoạn làm nóng và nhiều lần đo; tách các tiến trình chạy thử độc lập khi cần đánh giá độ ổn định.
- Kết quả được trả về hoặc được tiêu thụ đúng cách để tránh trình biên dịch bỏ phép tính không có tác dụng.
- Không in stdout trong phần đang đo.
- Dùng dữ liệu đại diện: miền số nhỏ được dùng lại trong bộ đệm và miền số lớn có thể cho lượng cấp phát khác nhau.
- Thử nhiều kích thước, gồm trường hợp lớn hơn khả năng chứa của bộ nhớ đệm; không suy tuyến tính từ một danh sách rất nhỏ.
- Giữ điều kiện JDK, JVM, heap, collector và máy đo nhất quán; ghi rõ cấu hình đã dùng.

JMH có ví dụ cụ thể về việc kết quả bị loại bỏ khi mã không được sử dụng. [JMH: Dead Code](https://raw.githubusercontent.com/openjdk/jmh/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java).

### 8.3. Đọc nhiều chỉ số thay vì chỉ thời gian trung bình

Với JMH, GC profiler có thể cung cấp tốc độ cấp phát và số byte chuẩn hóa cho mỗi thao tác. `B/op` phải được hiểu theo định nghĩa “một thao tác” trong benchmark: nếu một lần benchmark xử lý 10.000 phần tử thì đó là byte cho cả lần xử lý, trừ khi đã cấu hình cách chuẩn hóa khác.

So `gc.alloc.rate.norm` với thời gian và thông lượng; không lấy tốc độ MB/giây thấp hơn làm bằng chứng độc lập rằng mỗi yêu cầu cấp phát ít hơn. Các kết quả GC cũng cần đủ thời gian và số lần lặp để có ý nghĩa. [JMH: Profilers](https://raw.githubusercontent.com/openjdk/jmh/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_35_Profilers.java).

Ở ứng dụng thật, theo dõi đồng thời thông lượng, các phân vị độ trễ, lượng cấp phát và GC theo tải đại diện. Nếu nghi ngờ dữ liệu bị giữ quá lâu, phân tích heap và các đường tham chiếu; nếu nghi ngờ truy cập bộ nhớ, dùng profiler/phân tích phần cứng thích hợp thay vì suy ra cache miss chỉ từ thời gian chạy.

## 9. Những kết luận dễ sai và cách sửa

| Kết luận vội | Cách phân tích chính xác hơn |
| --- | --- |
| “Boxing lần nào cũng tạo đối tượng mới.” | Xét bộ đệm kiểu bọc, chia sẻ tham chiếu và tối ưu JIT; đo cấp phát thực tế |
| “GC sẽ tự xử lý nên số đối tượng không quan trọng.” | GC có chi phí và phụ thuộc dữ liệu sống; đo theo mục tiêu thông lượng/độ trễ |
| “Ít GC hơn chắc chắn nhanh hơn.” | Có thể chương trình chỉ chạy ít công việc hơn hoặc giữ dữ liệu lâu hơn; so cùng đơn vị công việc |
| “`ArrayList<Integer>` chứa các số liền nhau.” | Nó chứa các tham chiếu liền nhau trong mảng; các đối tượng `Integer` ở ngoài mảng |
| “Cùng O(n) thì duyệt gần như ngang nhau.” | Đường truy cập, boxing, số đối tượng và chi phí tính toán mỗi phần tử có thể khác |
| “Đổi sang primitive stream là xóa mọi boxing đã có.” | Dữ liệu nguồn kiểu bọc vẫn tồn tại; lợi ích nằm ở những bước được chuyển sang nguyên thủy |
| “Tạo pool cho mọi đối tượng nhỏ sẽ giảm chi phí.” | Pool có thể kéo dài thời gian sống, giữ bộ nhớ và thêm quản lý/đồng bộ; cần đo lợi ích ròng |

## 10. Thực hành phân tích một đường xử lý số

Giả sử mỗi yêu cầu nhận 100.000 số, tạo `List<Integer>`, tạo thêm danh sách kết quả sau biến đổi, tính tổng rồi bỏ các danh sách. Hãy tiến hành theo thứ tự:

1. Xác nhận đầu ra, miền giá trị, dữ liệu thiếu và khả năng tràn số.
2. Vẽ nơi bắt đầu boxing, nơi có collection trung gian và nơi unboxing để tính tổng.
3. Đo byte/yêu cầu, thông lượng và độ trễ của phiên bản hiện tại; ghi thời gian sống của kết quả.
4. Thử tính trực tiếp hoặc dùng luồng nguyên thủy để tránh vật chất hóa danh sách trung gian nếu nó không cần được dùng lại.
5. Chỉ thay biểu diễn đầu vào khi hợp đồng sử dụng cho phép; so kết quả và đo lại cùng tải.
6. Nếu lượng cấp phát đã giảm nhưng độ trễ không cải thiện, kiểm tra lại nút thắt như I/O, tính toán hoặc truy cập dữ liệu thay vì mặc định GC là nguyên nhân duy nhất.

Câu hỏi tự kiểm tra:

- Vì sao benchmark chỉ dùng các số từ `0` đến `100` có thể không đại diện cho dữ liệu số tùy ý?
- Vì sao tổng trên `List<Integer>` vẫn cần unboxing dù các đối tượng `Integer` đã tồn tại từ trước?
- Nếu đổi kiểu bộ cộng dồn từ `Long` sang `long`, điều gì được đơn giản hóa ở ngữ nghĩa và điều gì vẫn cần đo?
- Khi GC không thu hồi được một map chứa dữ liệu cũ, nên tìm tham chiếu giữ sống ở đâu?
- Vì sao xáo thứ tự tham chiếu trong danh sách có thể đổi hiệu năng dù số phần tử và số phép cộng vẫn giữ nguyên?

Một lời giải tốt phải nối được biểu diễn dữ liệu với cấp phát, thời gian sống và đường truy cập bộ nhớ, rồi chỉ ra phép đo nào xác nhận từng giả thuyết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-005">Ảnh hưởng của hàm băm và Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của hàm băm và Comparator

## 1. Vì sao cùng một collection có thể nhanh, chậm hoặc cho kết quả bất ngờ?

Collection không tự biết hai đối tượng “giống nhau” theo nghiệp vụ hay đối tượng nào nên đứng trước. Nó phải gọi những quy tắc do kiểu dữ liệu hoặc chương trình cung cấp:

- `equals`: hai đối tượng có được xem là bằng nhau không?
- `hashCode`: đối tượng nên được đưa tới nhóm ứng viên nào trong bảng băm?
- `Comparator.compare` hoặc `Comparable.compareTo`: đối tượng nào đứng trước, đứng sau, hay cùng mức theo thứ tự?

Các quy tắc này ảnh hưởng đồng thời đến **tính đúng đắn** và **hiệu năng**. Một hàm băm trả cùng một số cho mọi khóa có thể vẫn đúng hợp đồng nhưng làm tra cứu chậm. Một comparator chỉ so điểm có thể sắp xếp danh sách đúng yêu cầu, nhưng khi dùng cho `TreeSet` lại khiến hai người khác nhau cùng điểm được xem là cùng phần tử.

Bài này dùng Java 17 để phân tích hai câu hỏi: collection gọi quy tắc nào, và mỗi lần gọi tốn bao nhiêu?

## 2. Nhìn đúng vai trò của từng quy tắc

| Cấu trúc/thao tác | Quy tắc quan trọng | Hệ quả |
| --- | --- | --- |
| `HashMap`, `HashSet` | `hashCode` để định vị; `equals` để phân biệt khóa/phần tử | Băm tốt giúp giảm số ứng viên cần xét |
| `LinkedHashMap`, `LinkedHashSet` | Cũng dùng băm và bằng nhau | Liên kết bổ sung phục vụ thứ tự duyệt |
| `TreeMap`, `TreeSet` | Comparator hoặc thứ tự tự nhiên | Kết quả so sánh bằng 0 quyết định cùng khóa/phần tử trong cây |
| `PriorityQueue` | Comparator hoặc thứ tự tự nhiên để duy trì heap | Quyết định phần tử được lấy trước; không loại trùng |
| `List.sort(comparator)` | Comparator | Quyết định thứ tự; danh sách vẫn giữ các phần tử cùng mức |

`HashMap` thông thường không nhận một `Comparator` để sắp xếp khóa. Ngược lại, cải thiện `hashCode` không làm `TreeMap.get` nhanh hơn vì đường tìm kiếm của cây dựa trên so sánh. Đừng chọn điểm tối ưu chỉ từ tên lớp; hãy xác định thao tác thực tế và quy tắc nó sử dụng.

## 3. Hàm băm: đúng hợp đồng trước, phân bố tốt sau

### 3.1. HashCode không phải mã định danh duy nhất

Một mã băm là số `int`. Nhiều đối tượng khác nhau có thể có cùng mã băm, gọi là va chạm (collision). Vì vậy:

~~~text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
a.hashCode() == b.hashCode()  không suy ra  a.equals(b) == true
~~~

Nếu thông tin dùng để so sánh bằng nhau không đổi, mã băm phải ổn định trong cùng một lần chạy chương trình. Java không yêu cầu mã băm phải giữ nguyên giữa hai lần chạy khác nhau.

Khi tự viết `equals`, phải viết `hashCode` nhất quán với cùng quan hệ bằng nhau. Không dùng số ngẫu nhiên hay thời gian hiện tại để làm mã băm mỗi lần gọi. Cũng không dùng mã băm để quyết định duy nhất hai bản ghi có trùng nghiệp vụ hay không. [Hợp đồng Object.hashCode và Object.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)

### 3.2. Hai loại lỗi khác nhau

**Lỗi hợp đồng:** hai khóa bằng nhau nhưng có mã băm khác nhau. Collection có thể tìm ở vị trí không chứa khóa đã lưu, nên `contains`, `get`, `remove` không còn làm việc như mong đợi. Đây là lỗi dữ liệu và tính đúng đắn.

**Phân bố kém:** các khóa khác nhau đều có cùng mã băm. Collection vẫn có thể phân biệt bằng `equals`, nhưng phải xét nhiều ứng viên hơn. Đây chủ yếu là vấn đề hiệu năng khi hợp đồng còn đúng.

Ví dụ, một lớp định nghĩa bằng nhau theo `id` có thể trả `id % 2` từ `hashCode`. Hàm này nhất quán với bằng nhau nhưng chỉ tạo hai mã băm, nên hầu hết lợi ích phân bố biến mất khi dữ liệu lớn.

Hàm băm tốt cho collection cần giữ hợp đồng, tính đủ rẻ và phân bố phù hợp với dữ liệu thực tế. Việc thêm nhiều phép tính phức tạp chưa chắc làm chương trình nhanh hơn.

### 3.3. Chi phí của một lần tra cứu

Có thể hình dung:

~~~text
Chi phí tra cứu =
    chi phí tính mã băm
  + chi phí xác định ngăn
  + chi phí kiểm tra các ứng viên trong ngăn
~~~

Nếu gọi:

- H là chi phí tính mã băm;
- E là chi phí một lần `equals`;
- q là số ứng viên cần kiểm tra;

thì một mô hình gần đúng là H + O(q) + O(qE), cộng các chi phí cấu trúc liên quan. Đây là mô hình phân tích, không phải số lần gọi phương thức được API bảo đảm. Một triển khai có thể tránh gọi `equals` nhờ kiểm tra tham chiếu hoặc mã băm trước. [Quy định Map về tra cứu và equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

Khi mã băm phân bố tốt và các phép tính trên có chi phí hằng, các thao tác cơ bản của `HashMap` có chi phí O(1) kỳ vọng. Nếu khóa rất lớn, H hay E có thể tăng theo kích thước khóa; O(1) theo số mục n không có nghĩa thao tác miễn phí.

### 3.4. Va chạm không được giải quyết bằng cách tăng capacity vô hạn

Tăng dung lượng có thể giúp khi nhiều mã băm khác nhau đang rơi vào các ngăn đông. Nếu mọi khóa đều có cùng mã băm, tăng dung lượng không tạo ra sự phân biệt mới giữa chúng.

Java có cơ chế giảm tác động của va chạm trong một số tình huống, có thể tận dụng thứ tự tự nhiên của khóa để phá hòa. Điều đó không biến mọi hàm băm kém thành hàm tốt, cũng không phải bảo đảm chung rằng mọi tra cứu đều O(1) hoặc trường hợp xấu nhất luôn O(log n).

Thêm khóa còn có thể kích hoạt tăng bảng và phân phối lại các mục. Chi phí khấu hao của chuỗi lần thêm phải phân biệt với độ trễ của một lần thêm. Dung lượng quá lớn cũng làm tốn bộ nhớ và có thể làm duyệt bảng chậm hơn. [Tài liệu hiệu năng HashMap của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

## 4. Khóa thay đổi và chi phí tính băm

### 4.1. Không thay đổi thông tin định danh khi khóa đang nằm trong bảng

Giả sử lưu một đối tượng `Customer` vào `HashSet`, trong đó `equals` và `hashCode` dùng `customerId`. Sau đó đổi `customerId`.

Nút lưu trữ vẫn đang ở vị trí được chọn khi thêm, trong khi lần tra cứu sau tính mã băm từ dữ liệu mới. Kết quả tra cứu có thể không tìm thấy đối tượng dù vòng lặp vẫn nhìn thấy nó. Không nên dựa vào một biểu hiện cụ thể của lỗi này vì hợp đồng collection đã bị vi phạm.

Cách xử lý tốt hơn:

- Dùng khóa bất biến, chẳng hạn một mã `long` hoặc một kiểu chỉ chứa các thành phần định danh bất biến.
- Nếu cần thay đổi khóa, xóa bằng trạng thái khóa cũ trước khi đổi, rồi thêm lại.
- Nếu chỉ thay đổi thuộc tính không tham gia bằng nhau/băm, đánh giá riêng tác động tới các cấu trúc khác như cây hoặc heap.

Một collection không cho thêm/xóa vẫn có thể chứa đối tượng thay đổi được; lớp bọc không biến các phần tử bên trong thành bất biến. [Cảnh báo về phần tử thay đổi được trong Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 4.2. Ghi nhớ kết quả băm chỉ có ích khi dữ liệu ổn định

Nếu mỗi lần `hashCode` phải quét cả một danh sách dài, việc định vị bảng nhanh không loại bỏ chi phí quét đó. Có thể cân nhắc dùng khóa nhỏ hơn hoặc tính trước giá trị khi tạo đối tượng bất biến.

Không ghi nhớ mã băm của một đối tượng còn thay đổi mà thiếu cơ chế duy trì tính nhất quán. Một mã băm cũ kết hợp với `equals` dựa trên dữ liệu mới còn nguy hiểm hơn một hàm băm chậm.

`record` giúp biểu diễn dữ liệu với các thành phần cuối cùng (final), nhưng không tự làm bất biến sâu những đối tượng được tham chiếu. Một thành phần kiểu `List` vẫn cần được bảo vệ nếu nội dung danh sách tham gia định danh.

## 5. Comparator: thứ tự phải là một quy tắc nhất quán

### 5.1. Chỉ dấu của kết quả có ý nghĩa

`compare(a, b)` trả:

- Số âm nếu a đứng trước b.
- 0 nếu a và b cùng mức theo comparator.
- Số dương nếu a đứng sau b.

Không bắt buộc phải trả đúng -1, 0 hoặc 1. Khi kiểm tra kết quả, dùng `< 0`, `== 0`, `> 0`.

Một comparator hợp lệ cần bảo đảm các tính chất như:

- Đổi hai đối số phải đổi dấu kết quả.
- Tính bắc cầu: nếu a lớn hơn b và b lớn hơn c, thì a phải lớn hơn c.
- Nếu a và b so sánh bằng 0, chúng phải có cùng quan hệ dấu khi so với một đối tượng thứ ba.
- Cùng dữ liệu và cùng quy tắc phải cho kết quả ổn định.

Không so theo trạng thái thay đổi ngẫu nhiên hay số lần comparator đã được gọi. Một bộ đếm chỉ để quan sát có thể tăng sau mỗi lần gọi, nhưng nó không được làm thay đổi kết quả so sánh.

### 5.2. Dùng phép trừ có thể làm hỏng thứ tự

Đoạn minh họa sau sai với một số giá trị biên:

~~~java
java.util.Comparator<Integer> unsafe = (a, b) -> a - b;
~~~

Với a = `Integer.MIN_VALUE`, b = 1, phép trừ tràn số và cho kết quả dương, dù a nhỏ hơn b.

Dùng `Integer.compare(a, b)` hoặc `Comparator.comparingInt` cho trường số nguyên; với `long` dùng `Long.compare` hay `comparingLong`. Không ép hiệu hai số `long` về `int` để tạo kết quả so sánh.

Tương tự, đảo thứ tự bằng `reversed()` rõ ràng hơn tự phủ định một kết quả tùy ý: số đối của `Integer.MIN_VALUE` vẫn bị tràn. [Hợp đồng và các hàm kết hợp Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)

## 6. So sánh bằng 0 có ý nghĩa khác nhau tùy nơi dùng

### 6.1. TreeSet và TreeMap dùng thứ tự để nhận diện trùng

Nếu comparator chỉ so điểm, hai người khác id nhưng cùng điểm có `compare(a, b) == 0`.

Trong `TreeSet`, phần tử thêm sau không tạo thêm một phần tử riêng. Trong `TreeMap`, một lần `put` với khóa cùng mức sẽ cập nhật ánh xạ đã có thay vì tạo khóa riêng theo thứ tự cây.

Vì vậy, comparator cho cây cần nhất quán với `equals` nếu muốn tuân thủ hợp đồng chung của `Set`/`Map`. “So sánh bằng 0” không chỉ là cách hiển thị hai đối tượng cạnh nhau. [TreeSet và quan hệ bằng nhau](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html), [TreeMap và thứ tự khóa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

Có thể thêm tiêu chí phụ bằng `thenComparing`. Tuy nhiên, đây không phải cách sửa tự động mọi mô hình: nếu `equals` chỉ theo id nhưng comparator so điểm trước, hai đối tượng cùng id khác điểm vẫn có thể so sánh khác 0. Cần chọn rõ mô hình định danh; đôi khi nên lưu theo id trong một map và tạo một danh sách sắp xếp riêng để trình bày.

### 6.2. BigDecimal là ví dụ sẵn có trong Java

`new BigDecimal("1.0")` và `new BigDecimal("1.00")`:

- So sánh thứ tự tự nhiên cho kết quả 0 vì cùng giá trị số.
- `equals` cho kết quả `false` vì khác scale.

Do đó, với thứ tự tự nhiên, một `TreeSet<BigDecimal>` có thể giữ một phần tử trong khi `HashSet<BigDecimal>` giữ hai. Đây là khác biệt được tài liệu hóa, cần quyết định theo nghiệp vụ trước khi đổi collection. [Tài liệu BigDecimal của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)

### 6.3. PriorityQueue không dùng compare bằng 0 để loại trùng

Hai công việc có cùng mức ưu tiên vẫn có thể cùng nằm trong `PriorityQueue`. Hàng đợi không bảo đảm lấy các phần tử hòa theo thứ tự thêm. Nếu cần ưu tiên trước rồi FIFO khi hòa, thêm số thứ tự tăng dần làm tiêu chí phụ và duy trì số này đúng trong toàn bộ thời gian sử dụng.

`contains(x)` và `remove(Object)` của hàng đợi tìm theo bằng nhau, không phải “mọi đối tượng có comparator trả 0”. Việc duyệt iterator cũng không bảo đảm theo thứ tự ưu tiên. [Tài liệu PriorityQueue của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html)

### 6.4. Sort giữ phần tử và có quy tắc ổn định riêng

Sắp xếp một danh sách theo điểm không xóa những người cùng điểm. `List.sort` là phép sắp xếp ổn định: phần tử cùng mức giữ thứ tự tương đối ban đầu.

Sự ổn định đó không được suy rộng thành FIFO của `PriorityQueue`, cũng không thay đổi cách `TreeSet` xác định trùng. Một comparator có thể phù hợp để trình bày danh sách nhưng không phù hợp làm thứ tự định danh cho tập cây. [Tài liệu List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator))

## 7. Comparator đắt làm chi phí cấu trúc tăng theo

Nếu một lần so sánh có chi phí C, những cận quen thuộc cần được đọc lại:

| Công việc | Số phép so sánh thường dùng để phân tích | Khi một lần so sánh tốn O(C) |
| --- | --- | --- |
| Tra/thêm/xóa khóa trong cây cân bằng | O(log n) | Phần chi phí so sánh là O(C log n) |
| Điều chỉnh heap khi thêm/lấy đầu | O(log n) | Phần chi phí so sánh là O(C log n) |
| Sắp xếp bằng so sánh trong trường hợp tổng quát | O(n log n) | Phần chi phí so sánh là O(C n log n) |

Bảng mô tả phần chi phí so sánh; không loại bỏ các chi phí khác như cấp phát hay tăng mảng nền khi thêm vào heap. Với sắp xếp, đầu vào gần có thứ tự và thuật toán cụ thể có thể dùng ít phép so sánh hơn cận tổng quát.

Nếu comparator mỗi lần đều phân tích một chuỗi ngày, chuẩn hóa tên, truy cập cơ sở dữ liệu hoặc đọc tệp, C có thể chi phối toàn bộ công việc. Một comparator trông chỉ có một dòng vẫn có thể rất đắt.

Cách cải thiện thường gặp:

1. Tính một khóa sắp xếp ổn định cho mỗi đối tượng trước khi sắp xếp.
2. So sánh các khóa đã tính trong comparator.
3. Dùng tiêu chí phụ đúng nghĩa khi hòa.
4. Chỉ cập nhật hoặc tính lại khóa khi dữ liệu nguồn thay đổi.

Ví dụ, nếu cần sắp theo tháng từ một chuỗi ngày đã kiểm tra hợp lệ, phân tích n chuỗi một lần rồi so sánh tháng thường hợp lý hơn phân tích lại trong mọi lần `compare`. Phải giữ nguyên quy tắc nghiệp vụ: xử lý múi giờ, ngôn ngữ hoặc dấu tiếng Việt không thể thay thế tùy tiện chỉ để comparator nhanh hơn.

`comparingInt` và `thenComparingInt` diễn đạt phép so sánh khóa nguyên thủy rõ ràng và tránh nhu cầu đóng hộp khóa `int` của một thiết kế dùng khóa đối tượng. Tác động thực tế vẫn cần đo trên đường chạy cần tối ưu.

## 8. Dữ liệu và quy tắc so sánh phải ổn định khi đang lưu

Đưa một công việc vào `PriorityQueue` rồi đổi trường ưu tiên không tự khiến heap được sắp lại. Tương tự, đổi trường tham gia comparator của một khóa `TreeMap` hoặc phần tử `TreeSet` có thể làm vị trí hiện tại không còn phù hợp với thứ tự mới.

Cách cập nhật là lấy/xóa mục theo trạng thái cũ, thay đổi hoặc tạo đối tượng mới, rồi chèn lại; khi không bảo đảm thao tác này an toàn, nên xây lại cấu trúc từ nguồn dữ liệu chuẩn. Không sửa comparator dùng cho cây bằng cách thay một cấu hình bên ngoài trong lúc cây còn dữ liệu.

Với `null`, có hai lớp quy tắc độc lập:

- Comparator có thể được thiết kế bằng `nullsFirst`/`nullsLast`.
- Bản thân collection có thể cấm `null`.

Chẳng hạn, comparator xử lý được `null` không làm `PriorityQueue` chấp nhận phần tử `null`. Nếu dùng thứ tự tự nhiên, cũng phải bảo đảm kiểu phần tử có thể so sánh đúng với nhau.

## 9. Chương trình minh họa Java 17

Ví dụ tách rõ va chạm băm, quan hệ trùng trong cây, hòa ưu tiên và tràn số. Nó kiểm tra kết quả hành vi, không dùng vài phần tử để kết luận tốc độ.

Lưu thành `HashComparatorDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

public class HashComparatorDemo {
    record BadHashKey(int id) {
        @Override
        public int hashCode() {
            return 0; // Hop dong dung, phan bo rat kem.
        }
    }

    record Student(int id, int score) {
    }

    public static void main(String[] args) {
        Set<BadHashKey> keys = new HashSet<>();
        keys.add(new BadHashKey(1));
        keys.add(new BadHashKey(2));
        keys.add(new BadHashKey(3));

        System.out.println("colliding keys size = " + keys.size());
        System.out.println("contains id 2 = "
                + keys.contains(new BadHashKey(2)));

        List<Student> students = List.of(
                new Student(1, 90),
                new Student(2, 90),
                new Student(3, 80)
        );

        Comparator<Student> byScore =
                Comparator.comparingInt(Student::score).reversed();

        Set<Student> scoreOnly = new TreeSet<>(byScore);
        scoreOnly.addAll(students);
        System.out.println("score-only tree size = " + scoreOnly.size());

        Comparator<Student> byScoreThenId =
                byScore.thenComparingInt(Student::id);
        Set<Student> fullOrder = new TreeSet<>(byScoreThenId);
        fullOrder.addAll(students);

        List<Integer> orderedIds = new ArrayList<>();
        for (Student student : fullOrder) {
            orderedIds.add(student.id());
        }
        System.out.println("score-and-id tree size = " + fullOrder.size());
        System.out.println("ordered ids = " + orderedIds);

        PriorityQueue<Student> queue = new PriorityQueue<>(byScore);
        queue.addAll(students);
        System.out.println("priority queue size = " + queue.size());

        List<Integer> scores = new ArrayList<>();
        while (!queue.isEmpty()) {
            scores.add(queue.poll().score());
        }
        System.out.println("polled scores = " + scores);

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("decimal equals = " + a.equals(b));
        System.out.println("decimal compare = " + a.compareTo(b));
        System.out.println("decimal hash set size = "
                + new HashSet<>(List.of(a, b)).size());
        System.out.println("decimal tree set size = "
                + new TreeSet<>(List.of(a, b)).size());

        Comparator<Integer> unsafe = (x, y) -> x - y;
        Comparator<Integer> safe = Integer::compare;
        System.out.println("unsafe sign = "
                + Integer.signum(unsafe.compare(Integer.MIN_VALUE, 1)));
        System.out.println("safe sign = "
                + Integer.signum(safe.compare(Integer.MIN_VALUE, 1)));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 HashComparatorDemo.java
java HashComparatorDemo
~~~

Stdout chính xác:

~~~text
colliding keys size = 3
contains id 2 = true
score-only tree size = 2
score-and-id tree size = 3
ordered ids = [1, 2, 3]
priority queue size = 3
polled scores = [90, 90, 80]
decimal equals = false
decimal compare = 0
decimal hash set size = 2
decimal tree set size = 1
unsafe sign = 1
safe sign = -1
~~~

Giải thích:

- `BadHashKey` dùng `equals` do record sinh theo id, còn mã băm luôn 0. Ba id vẫn được giữ riêng; va chạm không đồng nghĩa bằng nhau.
- Cây chỉ so điểm xem hai sinh viên 90 điểm là cùng phần tử. Comparator theo điểm rồi id phù hợp với record này vì đã xét đủ cả hai thành phần.
- Hàng đợi giữ đủ ba sinh viên. Chương trình chỉ in điểm khi lấy ra, không giả định sinh viên nào trong hai người cùng điểm được lấy trước.
- BigDecimal cho thấy khác biệt giữa bằng nhau và cùng giá trị theo thứ tự.
- Phép trừ trả dấu sai tại giá trị biên; `Integer.compare` xử lý đúng.

## 10. Kiểm tra và tối ưu có căn cứ

Trước khi tối ưu, kiểm tra hợp đồng trên các trường hợp có ý nghĩa:

- Hai đối tượng bằng nhau phải có cùng mã băm; va chạm giữa đối tượng khác nhau vẫn được phép.
- Comparator phải xử lý đúng giá trị nhỏ nhất/lớn nhất, đối tượng cùng mức và chuỗi ba đối tượng để kiểm tra tính bắc cầu.
- Quan hệ bằng 0 có đúng mục đích của nơi sử dụng không: sắp danh sách, tập duy nhất hay hàng đợi?
- Dữ liệu tham gia định danh và thứ tự có thể thay đổi khi đang lưu không?
- Quy tắc với `null`, chữ hoa/thường, ngôn ngữ và dữ liệu thiếu đã được xác định chưa?

Khi đo hiệu năng, dùng phân bố dữ liệu gần thực tế, gồm khóa trùng, nhiều khóa va chạm và các đối tượng có chi phí so sánh lớn. Tách thời gian chuẩn bị khóa, xây collection và truy vấn nếu cần hiểu từng phần; khi đánh giá công việc hoàn chỉnh thì phải cộng đủ tất cả.

Có thể đếm số lần gọi `hashCode`, `equals` hoặc `compare` để hiểu đường đi, nhưng số đếm không phải thời gian. Ghi log trong từng lời gọi sẽ làm nhiễu phép đo. Cũng không biến số lần gọi của một phiên bản JDK thành hợp đồng API.

Hàm băm tốt giúp thu hẹp ứng viên; comparator tốt giúp định hướng tìm kiếm và duy trì thứ tự. Chất lượng của chúng được đánh giá trước hết bằng quan hệ đúng, sau đó bằng tổng chi phí trên dữ liệu thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-006">Tránh duyệt LinkedList bằng get theo chỉ số</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh duyệt LinkedList bằng get theo chỉ số

## 1. Một vòng lặp đúng kết quả vẫn có thể sai về hiệu năng

Đoạn mã sau rất quen thuộc khi làm việc với danh sách:

```java
long total = 0;
for (int i = 0; i < values.size(); i++) {
    total += values.get(i);
}
```

Đây là đoạn mã minh họa bên trong một phương thức; `values` được giả định là danh sách số nguyên không chứa `null`.

Nếu `values` là `ArrayList`, truy cập phần tử theo chỉ số có chi phí hằng số và một lượt duyệt như trên có chi phí tuyến tính theo số phần tử. Nếu `values` là `LinkedList`, mỗi lần `get(i)` phải tìm nút ở vị trí `i`. Lặp lại việc tìm vị trí cho mọi chỉ số làm tổng công việc tăng theo bậc hai.

Vấn đề khó nhận ra vì:

- Mã vẫn trả về kết quả chính xác.
- Chỉ có một vòng lặp hiện ra trong mã gọi.
- Dữ liệu nhỏ có thể chạy đủ nhanh để không lộ lỗi.
- Biến thường được khai báo bằng `List`, khiến người đọc quên xem đối tượng thực tế là loại danh sách nào.

Để duyệt tuần tự một `LinkedList`, hãy dùng vòng `for-each`, `Iterator` hoặc `ListIterator` phù hợp. Các cách này giữ vị trí hiện tại và đi tiếp theo liên kết, thay vì yêu cầu danh sách tìm lại vị trí từ đầu hoặc cuối sau mỗi bước.

## 2. Hiểu cơ chế: chỉ số không phải địa chỉ của nút

### 2.1. Mảng và danh sách liên kết trả lời hai câu hỏi khác nhau

Với cấu trúc dựa trên mảng, biết chỉ số cho phép xác định trực tiếp ô chứa phần tử. Với danh sách liên kết đôi, mỗi nút giữ phần tử cùng liên kết đến nút trước và nút sau:

```text
first                                              last
  |                                                  |
  v                                                  v
[10] <-> [20] <-> [30] <-> [40] <-> [50] <-> [60] <-> [70] <-> [80]
 0        1        2        3        4        5        6        7
```

Các số `0..7` ở dòng dưới là vị trí logic. Chúng không tạo ra một mảng tra cứu từ chỉ số đến nút.

`LinkedList` hỗ trợ API `get(index)` vì nó thực hiện giao diện `List`, nhưng để lấy phần tử ở một chỉ số bất kỳ, nó phải lần theo liên kết. Việc một phương thức tồn tại trong giao diện không đồng nghĩa mọi triển khai có cùng chi phí thực hiện phương thức đó.

Tài liệu Java mô tả rõ truy cập theo chỉ số trên `LinkedList` sẽ đi từ đầu hoặc cuối, chọn phía gần vị trí cần tìm hơn. [Tài liệu LinkedList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html)

### 2.2. Đi từ phía gần hơn vẫn không tạo ra truy cập hằng số

Giả sử danh sách có `n` phần tử và `0 <= i < n`. Số bước lần theo liên kết để tới nút `i`, theo cơ chế trên, là:

```text
h(i) = min(i, n - 1 - i)
```

Cần tính thêm một lượng công việc hằng số để kiểm tra chỉ số và lấy giá trị, nên chi phí một lần gọi có thể mô tả bằng:

```text
get(i): Θ(1 + min(i, n - 1 - i))
```

Ví dụ với `n = 8`:

| Chỉ số `i` | Phía bắt đầu | Số bước theo liên kết |
| --- | --- | --- |
| 0 | Đầu | 0 |
| 1 | Đầu | 1 |
| 2 | Đầu | 2 |
| 3 | Đầu | 3 |
| 4 | Cuối | 3 |
| 5 | Cuối | 2 |
| 6 | Cuối | 1 |
| 7 | Cuối | 0 |

`get(0)` và `get(n - 1)` có chi phí hằng số. Những vị trí gần giữa cần số bước tỷ lệ với `n`. Vì thế nói “mọi `get(i)` đều phải đi qua `n` nút” là không chính xác; nói “truy cập chỉ số bất kỳ trên LinkedList có trường hợp xấu tuyến tính” mới đúng.

Quan trọng hơn, `get(4)` không tận dụng nút mà lần `get(3)` trước đó vừa tìm ra. Mỗi lời gọi định vị lại nút từ một đầu danh sách. Đây là điểm khác biệt với iterator. Có thể kiểm chứng cơ chế định vị trong mã nguồn OpenJDK 17, tại phương thức nội bộ `node(index)`. [Mã nguồn LinkedList của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/LinkedList.java)

## 3. Vì sao một vòng for lại có tổng chi phí bậc hai?

### 3.1. Cộng công việc ẩn trong từng get

Xét vòng lặp gọi `get(0)`, `get(1)`, ..., `get(n - 1)` đúng một lần trên danh sách không bị thay đổi trong lúc duyệt.

Tổng số bước theo liên kết là:

```text
H(n) = tổng min(i, n - 1 - i), với i từ 0 đến n - 1
```

Với `n = 8`, tổng là:

```text
0 + 1 + 2 + 3 + 3 + 2 + 1 + 0 = 12
```

Với số phần tử lớn hơn:

- Nếu `n = 2k`, tổng là `k(k - 1)`.
- Nếu `n = 2k + 1`, tổng là `k²`.

Hai trường hợp có thể viết gọn:

```text
H(n) = floor((n - 1)² / 4), với n >= 1
```

Ngoài các bước theo liên kết, vẫn có `n` lần lấy phần tử và xử lý điều kiện vòng lặp. Tổng chi phí duyệt là:

```text
T(n) = Θ(n + H(n)) = Θ(n²)
```

Đây là kết luận cho vòng lặp duyệt toàn bộ bằng chỉ số, không phải kết luận rằng mọi cách dùng `LinkedList` đều bậc hai.

### 3.2. Đọc bảng tăng trưởng

| Số phần tử `n` | Số lần gọi `get(i)` | Tổng bước theo liên kết `H(n)` |
| --- | --- | --- |
| 8 | 8 | 12 |
| 1.000 | 1.000 | 249.500 |
| 2.000 | 2.000 | 999.000 |
| 4.000 | 4.000 | 3.998.000 |
| 10.000 | 10.000 | 24.995.000 |

Tăng kích thước gấp đôi làm số bước theo liên kết tiến gần mức gấp bốn. Việc đi từ đầu gần hơn giảm công việc so với luôn đi từ đầu, nhưng không đổi bậc tăng.

Bảng đếm bước theo mô hình định vị nút, không phải số nano giây, số lệnh máy hay số lần truy cập bộ nhớ thực tế. Nó giải thích nguyên nhân thuật toán; không dự đoán thời gian cụ thể trên mọi máy.

### 3.3. Đưa size ra ngoài vòng lặp không giải quyết gốc vấn đề

Đổi thành:

```java
int n = values.size();
for (int i = 0; i < n; i++) {
    process(values.get(i));
}
```

không làm việc duyệt `LinkedList` trở thành tuyến tính. Trong triển khai này, kích thước đã được lưu lại; chi phí chi phối là tìm nút trong `get(i)`, không phải đếm lại toàn bộ danh sách trong `size()`.

Đoạn trên là mã minh họa; `process` đại diện cho công việc xử lý một phần tử. Nếu mỗi lần xử lý có chi phí riêng lớn, tổng chi phí cần tính cả phần đó. Tuy nhiên, công việc tìm lại nút vẫn tồn tại và có thể tránh được.

## 4. Duyệt tuần tự bằng iterator

### 4.1. Chỉ cần đọc từng phần tử: dùng for-each

```java
long total = 0;
for (int value : values) {
    total += value;
}
```

Với đối tượng triển khai `Iterable` như `LinkedList`, vòng `for-each` duyệt thông qua iterator. Nó không được biến thành chuỗi `get(0)`, `get(1)`, ... như vòng lặp chỉ số ở đầu bài.

Iterator của danh sách liên kết giữ nút kế tiếp cần đọc. Mỗi lần `next()` lấy giá trị rồi chuyển sang nút liền sau. Một lượt duyệt `n` phần tử có chi phí `Θ(n)`, nếu công việc xử lý mỗi phần tử là hằng số.

Đây thường là cách rõ nhất khi thuật toán chỉ cần giá trị, không cần chỉnh sửa cấu trúc danh sách trong lúc duyệt.

### 4.2. Cần điều khiển việc duyệt: dùng Iterator tường minh

```java
long total = 0;
Iterator<Integer> iterator = values.iterator();
while (iterator.hasNext()) {
    total += iterator.next();
}
```

Đoạn mã minh họa này cần import `java.util.Iterator` và `values` không chứa `null` khi thực hiện phép cộng bằng cách mở hộp `Integer` sang `int`.

Khác biệt về cơ chế:

```text
Duyệt bằng get(i):
    yêu cầu vị trí 0 -> tìm nút
    yêu cầu vị trí 1 -> tìm lại nút
    yêu cầu vị trí 2 -> tìm lại nút
    ...

Duyệt bằng một iterator:
    bắt đầu tại đầu danh sách
    đọc nút hiện tại -> đi tới nút kế
    đọc nút hiện tại -> đi tới nút kế
    ...
```

Việc giữ lại vị trí mới là điều tiết kiệm công việc. Chỉ đổi cú pháp mà vẫn gọi `get(i)` trong phần thân thì không đạt mục tiêu đó.

### 4.3. Cần số thứ tự: tăng một biến đếm bên cạnh iterator

Không nhất thiết phải dùng truy cập chỉ số chỉ vì đầu ra cần số thứ tự.

Ví dụ minh họa bên trong một phương thức, không sửa cấu trúc danh sách trong vòng lặp:

```java
int index = 0;
for (String value : names) {
    System.out.println(index + ": " + value);
    index++;
}
```

Biến `index` theo dõi thứ tự đang duyệt; nó không được dùng để tìm lại phần tử. Do đó vẫn có một lượt duyệt tuyến tính trên `LinkedList`.

Nếu thuật toán cần vị trí con trỏ hoặc duyệt hai chiều, `ListIterator` cung cấp trực tiếp những thao tác đó.

## 5. Duyệt ngược và cập nhật bằng ListIterator

### 5.1. Duyệt ngược cũng không nên gọi get giảm dần

Vòng lặp này vẫn có tổng chi phí bậc hai trên `LinkedList`:

```java
for (int i = values.size() - 1; i >= 0; i--) {
    process(values.get(i));
}
```

Nó chỉ đổi thứ tự của cùng các lần định vị nút. Dùng một `ListIterator` bắt đầu ở cuối sẽ giữ vị trí khi đi ngược:

```java
ListIterator<Integer> iterator = values.listIterator(values.size());
while (iterator.hasPrevious()) {
    int value = iterator.previous();
    process(value);
}
```

Hoặc, khi biến có kiểu `LinkedList`/`Deque` phù hợp, có thể dùng `descendingIterator()` để duyệt ngược. Hãy chọn API phù hợp với kiểu được công bố cho phương thức, thay vì ép kiểu chỉ để dùng một cách viết ngắn hơn.

### 5.2. Tạo iterator một lần rồi đi tiếp

`ListIterator` có con trỏ nằm giữa các phần tử, cho phép `next()`, `previous()` và lấy chỉ số liên quan tới vị trí con trỏ. `set()` tác động lên phần tử vừa được trả về bởi `next()` hoặc `previous()`, theo điều kiện của API. [Tài liệu ListIterator của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html)

Ví dụ cập nhật mọi giá trị trong một danh sách có thể sửa đổi:

```java
ListIterator<Integer> iterator = values.listIterator();
while (iterator.hasNext()) {
    int current = iterator.next();
    iterator.set(current * 10);
}
```

Duyệt một lần và cập nhật ngay tại vị trí đã tìm được tránh dạng sau:

```java
for (int i = 0; i < values.size(); i++) {
    values.set(i, values.get(i) * 10);
}
```

Trong dạng chỉ số, cả `get(i)` lẫn `set(i, ...)` đều cần định vị nút. Hai lần tìm ở mỗi chỉ số vẫn tạo tổng bậc hai.

Cũng không nên tạo lại `values.listIterator(i)` ở mỗi bước. Khởi tạo iterator tại một vị trí ở giữa có thể phải tìm vị trí đó. Hiệu quả tuyến tính đến từ việc **dùng lại cùng một iterator để di chuyển tuần tự**, không đến từ tên kiểu `ListIterator` đứng riêng.

## 6. Chương trình Java 17+ chạy đầy đủ

Chương trình sau có ba mục đích:

1. Chứng minh các cách duyệt trả về cùng tổng.
2. Đưa ra số bước định vị theo mô hình, không dựa vào phép đo thời gian nhiễu.
3. Minh họa duyệt ngược và chỉnh sửa an toàn thông qua iterator của chính vòng lặp.

Lưu thành `LinkedListTraversalDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class LinkedListTraversalDemo {
    private static long sumByIndex(List<Integer> values) {
        long total = 0;
        for (int i = 0; i < values.size(); i++) {
            total += values.get(i);
        }
        return total;
    }

    private static long sumByForEach(List<Integer> values) {
        long total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static long sumByIterator(List<Integer> values) {
        long total = 0;
        Iterator<Integer> iterator = values.iterator();
        while (iterator.hasNext()) {
            total += iterator.next();
        }
        return total;
    }

    private static List<Integer> reverseValues(List<Integer> values) {
        List<Integer> result = new ArrayList<>();
        ListIterator<Integer> iterator = values.listIterator(values.size());
        while (iterator.hasPrevious()) {
            result.add(iterator.previous());
        }
        return result;
    }

    private static long modeledIndexHops(int size) {
        long hops = 0;
        for (int i = 0; i < size; i++) {
            hops += Math.min(i, size - 1 - i);
        }
        return hops;
    }

    public static void main(String[] args) {
        List<Integer> values = new LinkedList<>(
                List.of(10, 20, 30, 40, 50, 60, 70, 80));
        System.out.println("input = " + values);
        System.out.println("sum by index = " + sumByIndex(values));
        System.out.println("sum by for-each = " + sumByForEach(values));
        System.out.println("sum by iterator = " + sumByIterator(values));
        System.out.println("reverse = " + reverseValues(values));

        for (int size : new int[] {8, 1000, 2000, 4000}) {
            System.out.println("n=" + size
                    + ", modeled index hops=" + modeledIndexHops(size));
        }

        List<Integer> odds = new LinkedList<>(List.of(1, 2, 3, 4, 5, 6));
        Iterator<Integer> removalIterator = odds.iterator();
        while (removalIterator.hasNext()) {
            int value = removalIterator.next();
            if (value % 2 == 0) {
                removalIterator.remove();
            }
        }
        System.out.println("after removing even values = " + odds);

        ListIterator<Integer> updateIterator = odds.listIterator();
        while (updateIterator.hasNext()) {
            int value = updateIterator.next();
            updateIterator.set(value * 10);
        }
        System.out.println("after multiplying by ten = " + odds);
        System.out.println("input remains = " + values);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 LinkedListTraversalDemo.java
java LinkedListTraversalDemo
```

Stdout:

```text
input = [10, 20, 30, 40, 50, 60, 70, 80]
sum by index = 360
sum by for-each = 360
sum by iterator = 360
reverse = [80, 70, 60, 50, 40, 30, 20, 10]
n=8, modeled index hops=12
n=1000, modeled index hops=249500
n=2000, modeled index hops=999000
n=4000, modeled index hops=3998000
after removing even values = [1, 3, 5]
after multiplying by ten = [10, 30, 50]
input remains = [10, 20, 30, 40, 50, 60, 70, 80]
```

`modeledIndexHops()` cộng khoảng cách theo mô hình đã phân tích; nó không gắn bộ đếm vào các nút nội bộ của Java và không đo thời gian. Hàm tính mô hình tự nó chạy tuyến tính theo tham số `size`, dù con số mà nó tính biểu diễn tổng công việc bậc hai của một cách duyệt khác.

Ba hàm tính tổng không có khác biệt về kết quả trên dữ liệu này. Sự khác biệt nằm ở lượng công việc tìm phần tử. Một kiểm thử chỉ so sánh tổng bằng `360` sẽ không phát hiện vấn đề hiệu năng của `sumByIndex()`.

## 7. Các cách đổi cú pháp nhưng vẫn giữ nguyên lỗi

### 7.1. Duyệt dãy chỉ số bằng Stream

Đoạn minh họa này vẫn gọi `get(i)` cho từng chỉ số:

```java
long total = IntStream.range(0, values.size())
        .mapToLong(i -> values.get(i))
        .sum();
```

Nó cần import `java.util.stream.IntStream`. Với `values` là `LinkedList`, thay `for` bằng một luồng chỉ số không thay đổi chi phí định vị nút. Tổng công việc vẫn bậc hai.

Nếu chỉ cần duyệt các giá trị, có thể dùng vòng `for-each` hoặc luồng phần tử:

```java
long total = values.stream()
        .mapToLong(Integer::longValue)
        .sum();
```

Luồng phần tử tuần tự của `LinkedList` duyệt cấu trúc qua cơ chế của collection thay vì tự tạo chuỗi truy cập chỉ số như ví dụ đầu. Không cần dùng Stream chỉ để đạt độ phức tạp tuyến tính; vòng `for-each` đã làm được điều đó.

Cả hai ví dụ tính tổng đều giả định không có `null`. Cho phép lưu `null` trong `LinkedList` không làm phép mở hộp số nguyên chấp nhận `null`.

### 7.2. Giữ một biến chỉ số trong for-each rồi gọi get lại

```java
int index = 0;
for (Integer ignored : values) {
    process(values.get(index++));
}
```

Đoạn trên đã có iterator để đọc phần tử, nhưng bỏ giá trị nhận được rồi tìm lại bằng chỉ số. Hãy xử lý trực tiếp biến phần tử của vòng lặp.

### 7.3. Dùng indexOf cho từng giá trị để lấy số thứ tự

Nếu trong mỗi lượt duyệt ta lại gọi `values.indexOf(value)`, đó là một cuộc tìm kiếm mới. Tổng công việc có thể trở thành bậc hai; với phần tử trùng nhau, chỉ số nhận được còn là vị trí xuất hiện đầu tiên chứ không nhất thiết là vị trí đang duyệt.

Dùng biến đếm hoặc `ListIterator.nextIndex()` phù hợp với mục tiêu thay vì tìm lại vị trí từ giá trị.

## 8. Thay đổi danh sách trong lúc duyệt: giữ đúng iterator và ngữ nghĩa

### 8.1. Đọc bằng for-each không cấp quyền xóa trực tiếp trên danh sách

Đoạn sau là ví dụ lỗi, không phải cách xóa nên dùng:

```java
for (Integer value : values) {
    if (value < 0) {
        values.remove(value);
    }
}
```

Xóa trực tiếp qua `values` thay đổi cấu trúc mà iterator của vòng `for-each` không quản lý. Với iterator thông thường của `LinkedList`, điều này có thể dẫn đến `ConcurrentModificationException`. Cơ chế phát hiện lỗi là nỗ lực tốt nhất; không dùng việc “chưa thấy exception” để kết luận cách duyệt đó an toàn. [Tài liệu LinkedList của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html)

Khi cần lọc tại chỗ bằng vòng lặp, gọi `remove()` trên chính iterator sau khi `next()` trả về phần tử. Chương trình đầy đủ ở trên minh họa cách này. Không gọi `remove()` hai lần cho cùng một lần `next()`.

Việc tháo một nút đã được iterator tìm tới không cần tìm lại chỉ số. Tuy vậy, đừng suy rộng điều này thành “mọi thao tác xóa trên LinkedList đều hằng số”: xóa theo chỉ số hoặc tìm giá trị để xóa vẫn có thể phải duyệt để xác định nút.

### 8.2. Duyệt ngược không đồng nghĩa xóa các phần tử

`ListIterator` đi ngược hoặc `descendingIterator()` có thể đọc từ cuối về đầu mà giữ nguyên collection. Nếu dùng `removeFirst()`, `pollFirst()` hoặc thao tác lấy-xóa tương tự, cấu trúc nguồn bị tiêu thụ.

Tiêu thụ từng phần tử đầu có thể phù hợp với bài toán hàng đợi, nhưng không phải phép thay thế tương đương cho một lượt đọc giữ nguyên danh sách. Khi sửa mã để tối ưu, phải bảo toàn cả kết quả lẫn tác dụng lên dữ liệu nguồn.

### 8.3. Dùng iterator không làm collection tự an toàn giữa các luồng

Lợi ích ở đây là giữ vị trí khi duyệt trong điều kiện truy cập hợp lệ. Nó không cung cấp cơ chế đồng bộ cho một danh sách đang bị luồng khác thay đổi. Cần giải quyết quyền truy cập dữ liệu đồng thời riêng, thay vì coi iterator như một khóa bảo vệ.

## 9. Khi nào dùng ArrayList hoặc tạo bản sao?

### 9.1. Phân biệt nhu cầu duyệt tuần tự và truy cập vị trí

Nếu thuật toán chỉ cần một lượt đọc, sửa vòng lặp sang `for-each` hoặc iterator là đủ; không nhất thiết phải thay cấu trúc dữ liệu.

Nếu thuật toán thường xuyên truy cập các vị trí bất kỳ, chẳng hạn nhiều lần `get(randomIndex)`, có thể lựa chọn cấu trúc hỗ trợ truy cập chỉ số nhanh. Điều này là quyết định về mẫu thao tác, không phải khẩu hiệu rằng `LinkedList` luôn tốt hay luôn xấu.

Kiểu `List` không hứa chi phí truy cập hằng số cho mọi triển khai. Giao diện đánh dấu `RandomAccess` giúp thuật toán tổng quát nhận biết những danh sách hỗ trợ truy cập ngẫu nhiên nhanh và chọn chiến lược phù hợp. `ArrayList` thực hiện giao diện này, còn `LinkedList` không thực hiện. [Tài liệu RandomAccess của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/RandomAccess.html)

Với một phép duyệt đơn giản, không cần thêm nhánh `instanceof RandomAccess`: iterator đã biểu đạt đúng ý định duyệt tuần tự.

### 9.2. Một bản sao có chi phí và ngữ nghĩa riêng

Khi có nhiều truy cập chỉ số tiếp theo, có thể tạo bản sao:

```java
List<String> indexed = new ArrayList<>(linkedNames);
```

Đoạn minh họa này cần import `java.util.ArrayList` và `java.util.List`; `linkedNames` là danh sách nguồn.

Với nguồn `LinkedList`, sao chép các tham chiếu cần công việc tuyến tính và thêm bộ nhớ tuyến tính. Sau đó, truy cập chỉ số trên bản sao hỗ trợ mẫu xử lý dựa trên mảng.

Cần kiểm tra:

- Có đủ nhiều truy cập tiếp theo để đáng trả chi phí sao chép không?
- Chương trình có cần thấy những thay đổi cấu trúc sau này của danh sách nguồn không?
- Có được phép giữ thêm bộ nhớ `O(n)` không?

Đây là bản sao cấu trúc chứa các tham chiếu. Các đối tượng phần tử không được sao chép sâu. Thêm/xóa phần tử trong danh sách nguồn không tự cập nhật danh sách đích, nhưng nếu cùng tham chiếu tới một đối tượng có thể thay đổi, thay đổi bên trong đối tượng đó vẫn có thể được quan sát từ cả hai.

Nếu chỉ đọc một lần, sao chép rồi duyệt thường thêm công việc không cần thiết so với duyệt trực tiếp bằng iterator.

## 10. Kiểm tra một thay đổi tối ưu như thế nào?

### 10.1. Kiểm tra tính đúng trước

Khi đổi vòng lặp, hãy xác nhận:

- Phần tử vẫn được xử lý theo cùng thứ tự.
- Mỗi phần tử cần thiết được xử lý đúng số lần.
- Danh sách nguồn vẫn được giữ lại hoặc bị sửa đúng theo yêu cầu cũ.
- Dữ liệu rỗng, một phần tử, phần tử trùng và `null` được xử lý theo cùng hợp đồng.
- Mọi thao tác thêm/xóa trong lúc duyệt tuân theo API của iterator đang dùng.

Đối với ví dụ tính tổng, `null` phải được loại trừ hoặc có chính sách xử lý rõ ràng; thay kiểu vòng lặp không tự giải quyết vấn đề đó.

### 10.2. Kiểm tra cơ chế và tăng trưởng

Tìm những cuộc định vị bị lặp lại: `get(i)`, `set(i, ...)`, `listIterator(i)`, `indexOf(...)` bên trong vòng duyệt. Phải xét cả phương thức được gọi trong thân vòng lặp, không chỉ nhìn số vòng lặp ở mã ngoài.

Đo với nhiều kích thước dữ liệu nếu cần xác nhận tác động trong chương trình thực tế. Tránh xem một lần đo đồng hồ trên danh sách nhỏ là bằng chứng độ phức tạp. JVM còn có khởi động, JIT, cấp phát bộ nhớ, GC và nhiễu hệ thống.

Với phần thân chỉ làm công việc hằng số:

- Duyệt chỉ số toàn bộ `LinkedList`: tăng kích thước gấp đôi dẫn đến lượng công việc định vị gần gấp bốn khi `n` lớn.
- Một lượt iterator: lượng công việc tăng tuyến tính với số phần tử.

Các tỷ lệ này là dự đoán về bậc tăng, không phải cam kết thời gian chạy chính xác gấp bốn hoặc gấp hai trên mọi lần đo.

## 11. Bảng lựa chọn theo nhu cầu

| Nhu cầu | Cách làm phù hợp trên LinkedList | Điều cần tránh |
| --- | --- | --- |
| Đọc mọi giá trị theo thứ tự | `for-each` | `get(i)` cho mọi chỉ số |
| Đọc kèm số thứ tự | `for-each` + biến đếm, hoặc `ListIterator` | `indexOf(value)` lặp lại |
| Duyệt ngược giữ nguyên danh sách | Một `ListIterator` ở cuối hoặc `descendingIterator()` | Vòng `get(i)` giảm dần |
| Xóa một số phần tử trong lúc duyệt | `remove()` của chính iterator | Xóa trực tiếp trên danh sách trong `for-each` |
| Thay giá trị tại phần tử đang duyệt | `ListIterator.set()` sau khi đọc phần tử | `get(i)` rồi `set(i, ...)` trên mọi chỉ số |
| Xử lý một đoạn liên tiếp từ vị trí `k` | Định vị iterator một lần rồi đi tiếp | Tạo lại iterator tại từng chỉ số |
| Truy cập nhiều vị trí bất kỳ | Cân nhắc cấu trúc hỗ trợ truy cập chỉ số hoặc một bản sao phù hợp | Giả định mọi `List.get()` đều hằng số |

## 12. Tự kiểm tra và thực hành

### Câu 1: Duyệt chỉ số vẫn bậc hai nếu LinkedList biết chọn đầu gần hơn không?

Có. Tổng khoảng cách là `floor((n - 1)² / 4)` với `n >= 1`, nên vẫn tăng theo `Θ(n²)`. Chọn đầu gần hơn cải thiện hệ số, không thay đổi bậc tăng.

### Câu 2: Đã lưu values.size() vào một biến, vì sao vẫn chậm?

Vì mỗi `get(i)` vẫn phải định vị nút. Cắt bớt các lần gọi `size()` không loại bỏ tổng công việc tìm nút.

### Câu 3: Cần in số thứ tự, có bắt buộc dùng get(i) không?

Không. Dùng một biến đếm cùng `for-each`, hoặc lấy chỉ số con trỏ bằng `ListIterator` khi phù hợp. Số thứ tự của vòng duyệt không đòi hỏi truy cập phần tử bằng chỉ số.

### Câu 4: Chuyển sang IntStream.range(...).mapToObj(values::get) có giải quyết được không?

Không. Các cuộc gọi `get(i)` vẫn diễn ra. Hãy duyệt các giá trị trực tiếp thay vì tạo lại cách truy cập chỉ số bằng cú pháp khác.

### Bài thực hành

1. Thêm phương thức tính tổng số lẻ vào chương trình đầy đủ, dùng một lượt `for-each` và giữ nguyên nguồn.
2. Viết hàm tìm vị trí đầu tiên có giá trị lớn hơn một ngưỡng, dùng iterator kèm biến đếm và dừng ngay khi tìm được.
3. Giải thích chi phí khi tạo `listIterator(k)` một lần rồi đọc `r` phần tử liên tiếp, với các vị trí hợp lệ và không có sửa đổi ngoài iterator.
4. Xem lại một phương thức nhận `List<T>` trong mã của bạn: nó có vô tình giả định truy cập chỉ số nhanh không?

Gợi ý đối chiếu cho câu 3: chi phí gồm một lần định vị ban đầu cộng công việc đi tiếp qua `r` phần tử. Trên `LinkedList`, cận tổng là `O(n + r)`; nếu xét khoảng cách đến vị trí khởi đầu thì có thể mô tả chặt hơn theo khoảng cách đó. Không có `r` lần định vị độc lập như khi lặp lại `get(k + j)`.

Khi mục tiêu là đi qua các phần tử liên tiếp, hãy giữ vị trí và đi tiếp. Đó là lý do một iterator phù hợp với `LinkedList` hơn một chuỗi yêu cầu tìm lại các chỉ số.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-007">Giảm vòng lặp lồng bằng Set hoặc chỉ mục Map</a>

<details>
<summary>Xem chi tiết</summary>

# Giảm vòng lặp lồng bằng Set hoặc chỉ mục Map

## 1. Nhận ra công việc tìm kiếm đang bị lặp lại

Giả sử có `n` đơn hàng và `m` khách hàng. Để tìm khách hàng của từng đơn hàng, cách trực tiếp là duyệt từng đơn hàng, rồi quét danh sách khách hàng đến khi tìm thấy mã khớp. Vấn đề không nằm ở hình thức “hai vòng `for`”, mà ở việc **cùng một tập dữ liệu bị tìm lại nhiều lần theo cùng điều kiện**.

Ý tưởng tối ưu là làm công việc chuẩn bị một lần:

```text
Cách quét lặp:
  mỗi phần tử bên trái -> quét dữ liệu bên phải -> lấy kết quả

Cách dùng chỉ mục:
  dữ liệu bên phải -> xây Set/Map một lần
  mỗi phần tử bên trái -> tra theo khóa -> lấy kết quả
```

Chỉ mục trong bài là một cấu trúc Java trong bộ nhớ, không phải chỉ mục cơ sở dữ liệu. Nó tổ chức lại cách tìm dữ liệu đã có trong chương trình.

Chọn cấu trúc dựa trên câu hỏi của vòng lặp bên trong:

| Cần trả lời | Cấu trúc thường phù hợp |
| --- | --- |
| Có ít nhất một phần tử mang khóa này không? | `Set<K>` |
| Bản ghi duy nhất theo khóa là gì? | `Map<K, V>` |
| Tất cả bản ghi có cùng khóa là gì? | `Map<K, List<V>>` |
| Có bao nhiêu bản ghi mang khóa này? | `Map<K, Long>` hoặc một kiểu đếm phù hợp |

Không đổi sang Map chỉ để làm mã ngắn hơn. Trước hết phải xác định khóa, quan hệ bằng nhau, số lượng kết quả và thứ tự cần giữ.

## 2. Phân tích chi phí trước và sau

### 2.1. Quét lặp

Với mỗi phần tử trong `n` phần tử bên trái, nếu cần kiểm tra tối đa `m` phần tử bên phải, số lượt kiểm tra có thể lên tới `n × m`. Khi `n` và `m` cùng tăng theo quy mô dữ liệu, đó là kiểu tăng trưởng bậc hai.

`break` sau khi thấy bản ghi đầu tiên có thể làm ít công việc hơn trên một số đầu vào, nhưng trường hợp không tìm thấy hoặc tìm thấy muộn vẫn phải quét nhiều. Ví dụ `n = 100.000`, `m = 50.000` cho giới hạn 5.000.000.000 cặp kiểm tra ở cách quét toàn bộ. Đây là số cặp lý thuyết, không phải thời gian chạy đã đo.

### 2.2. Xây chỉ mục rồi tra cứu

Với bảng băm có khóa phân bố phù hợp và coi chi phí tính mã băm/so sánh khóa là hằng số:

- Xây chỉ mục từ `m` phần tử: chi phí kỳ vọng O(m).
- Tra cứu cho `n` phần tử: chi phí kỳ vọng O(n).
- Tổng: **O(m + n)** cho kiểm tra tồn tại hoặc tra một bản ghi, cộng chi phí tạo đầu ra.
- Bộ nhớ phụ: O(u + c), với `u` là số khóa khác nhau và `c` là dung lượng bảng băm; constructor từ `Collection` như `new HashSet<>(allowed)` có thể dự trữ O(m) theo số phần tử nguồn, kể cả khi có nhiều phần tử trùng.

Đây là đánh đổi bộ nhớ để tránh tìm kiếm lặp. Không gọi `HashMap` hay `HashSet` là O(1) tuyệt đối với mọi khóa và mọi trường hợp. Tài liệu nêu điều kiện về sự phân bố mã băm; bản thân `hashCode` và `equals` cũng có thể đắt nếu khóa lớn hoặc thiết kế kém. [Tài liệu `HashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).

### 2.3. Nếu phải tạo nhiều kết quả

Gọi `r` là số dòng kết quả phải phát ra trong phép ghép một–nhiều hoặc nhiều–nhiều. Xây chỉ mục nhóm rồi duyệt các nhóm phù hợp có chi phí kỳ vọng **O(m + n + r)**, không phải luôn O(m + n).

Nếu mọi phần tử bên trái đều khớp mọi phần tử bên phải và yêu cầu xuất toàn bộ cặp, `r = n × m`. Không cấu trúc tra cứu nào làm biến mất chi phí tạo những kết quả mà yêu cầu bài toán thực sự cần. Lợi ích của chỉ mục là tránh kiểm tra các cặp không liên quan.

## 3. Mẫu Set: chỉ cần biết có tồn tại

Giả sử cần giữ những mã yêu cầu có trong một danh sách được phép. Vòng lặp quét có `break` khi gặp lần khớp đầu tiên; kết quả phải giữ thứ tự và số lần xuất hiện của danh sách yêu cầu.

Cách thay đổi:

1. Tạo `HashSet` từ danh sách được phép **trước vòng lặp bên ngoài**.
2. Tiếp tục duyệt danh sách yêu cầu theo thứ tự cũ.
3. Với mỗi mã, gọi `contains`; nếu có thì thêm chính phần tử đang duyệt vào danh sách kết quả.

Set loại phần tử trùng trong phía tra cứu, điều đó không làm mất bản sao ở phía yêu cầu vì ta vẫn duyệt danh sách yêu cầu. Ngược lại, nếu chuyển luôn kết quả sang Set, ta đã đổi hợp đồng đầu ra.

Ví dụ:

```text
yêu cầu:    [A, C, A, null, B]
được phép:  [B, A, null]
kết quả:    [A, A, null, B]
```

`HashSet` cho phép phần tử `null` và không bảo đảm thứ tự duyệt. Trong mẫu này không duyệt Set để xuất kết quả, nên thứ tự của Set không quyết định thứ tự đầu ra. [Tài liệu `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html).

### Khi Set làm thay đổi bài toán

Nếu cần kiểm tra số lần xuất hiện, Set không đủ. Ví dụ bên phải có hai mã `A`, bên trái có ba mã `A`, và mỗi mã bên phải chỉ được sử dụng một lần: kiểm tra `contains` sẽ nhận cả ba, trái với yêu cầu ghép theo số lượng.

Trong trường hợp đó, có thể cần Map đếm số lượng còn lại rồi giảm sau mỗi lần ghép. Nếu cần giữ toàn bộ bản ghi tương ứng, cần Map chứa danh sách chứ không chỉ Set các khóa.

## 4. Mẫu Map: lấy một bản ghi theo khóa

Đối với đơn hàng và khách hàng, khóa tự nhiên có thể là mã khách hàng:

```text
customers -> Map<customerId, Customer>
orders    -> tra customerId -> lấy Customer
```

### 4.1. Khóa trùng phải có chính sách rõ

Một Map chỉ giữ tối đa một giá trị cho mỗi khóa. Nếu hai bản ghi có cùng mã, cách xây Map phải phản ánh đúng hợp đồng dữ liệu:

| Hợp đồng | Cách xây chỉ mục khi giá trị luôn khác `null` | Ý nghĩa |
| --- | --- | --- |
| Khóa phải duy nhất | Phát hiện trùng và báo lỗi | Dữ liệu trùng là đầu vào không hợp lệ |
| Giữ bản ghi đầu tiên | `putIfAbsent` | Tương ứng quét theo thứ tự rồi `break` ở lần khớp đầu |
| Giữ bản ghi cuối cùng | `put` theo thứ tự nguồn | Bản ghi sau thay bản ghi trước |
| Cần tất cả bản ghi | `Map<K, List<V>>` | Giữ số lượng và thứ tự trong từng nhóm |

Không dùng `put` ghi đè rồi coi đó là tối ưu bảo toàn hành vi của vòng quét lấy bản ghi đầu tiên. Nếu dữ liệu trùng được phép, phải chọn đúng chính sách đầu tiên/cuối cùng thay vì tự thêm quy tắc loại bỏ hoặc báo lỗi.

`Collectors.toMap(keyMapper, valueMapper)` cũng không tự giải quyết mọi hợp đồng: overload không có hàm gộp sẽ báo lỗi khi khóa trùng. Khi dùng overload có hàm gộp, hàm đó phải mang ý nghĩa nghiệp vụ rõ ràng. [Tài liệu `Collectors`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html).

### 4.2. Không tìm thấy khác với giá trị `null`

`HashMap.get(key)` có thể trả `null` khi không có khóa, hoặc khi khóa được ánh xạ tới giá trị `null`. Nếu cho phép giá trị `null`, chỉ nhìn kết quả `get` không phân biệt được hai trường hợp; có thể cần `containsKey` hoặc thiết kế lại biểu diễn dữ liệu thiếu.

Ví dụ trong chương trình bên dưới quy định giá trị `Customer` của chỉ mục luôn khác `null`. Vì vậy `get(...) == null` được dùng để biểu diễn không tìm thấy. Quy định này thuộc ví dụ, không phải tính chất chung của mọi `Map`.

### 4.3. Giữ đầu ra của phép ghép

Nếu cần giữ mọi đơn hàng, kể cả đơn không tìm thấy khách hàng, hãy luôn phát một dòng cho mỗi đơn hàng và xử lý giá trị thiếu. Nếu chỉ cần các đơn có khách hàng, mới bỏ qua dòng không khớp.

Đó là khác biệt giữa phép ghép giữ toàn bộ phía trái (left join) và phép ghép chỉ giữ dòng khớp (inner join). Tối ưu tra cứu không được tự đổi loại phép ghép.

## 5. Mẫu Map chứa List: lấy tất cả bản ghi khớp

Giả sử một đơn hàng có nhiều dòng hàng. Chỉ mục `Map<orderId, Line>` sẽ làm mất dữ liệu nếu ghi đè. Dùng:

```java
Map<String, List<Line>> byOrder = new HashMap<>();
for (Line line : lines) {
    byOrder.computeIfAbsent(line.orderId(), key -> new ArrayList<>()).add(line);
}
```

Đoạn này cần có kiểu `Line` và các import; chương trình đầy đủ ở phần sau cung cấp chúng. `computeIfAbsent` tạo danh sách khi chưa có giá trị cho khóa, sau đó thêm dòng vào nhóm. Không thay đổi chính Map bên trong hàm tạo danh sách. [Tài liệu `Map.computeIfAbsent`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#computeIfAbsent(K,java.util.function.Function)).

Khi duyệt đơn hàng:

```java
for (Order order : orders) {
    for (Line line : byOrder.getOrDefault(order.id(), List.of())) {
        result.add(order.id() + ":" + line.sku());
    }
}
```

Mã vẫn có hai vòng lặp lồng về hình thức. Khác biệt là vòng trong chỉ duyệt các dòng thuộc đúng đơn hàng, không quét toàn bộ `lines` mỗi lần. Đừng đánh giá thuật toán chỉ bằng số vòng lặp nhìn thấy.

Nếu xây nhóm theo thứ tự `lines`, mỗi List trong nhóm giữ thứ tự tương ứng. Nếu tiếp tục duyệt `orders` theo thứ tự cũ, đầu ra giữ thứ tự của vòng quét ban đầu: đơn hàng trước, rồi các dòng khớp theo thứ tự nguồn.

Nếu `orders` lặp lại cùng mã, nhóm được phát lại cho từng lần xuất hiện, giống vòng lặp lồng đầy đủ. Nếu hai dòng hàng trùng nội dung, List vẫn giữ cả hai. Không dùng Set làm giá trị nhóm nếu yêu cầu là bảo toàn số lần xuất hiện.

## 6. Chương trình Java 17+ đầy đủ: so sánh ba mẫu

Lưu mã sau thành `IndexDemo.java`. Dữ liệu khách hàng trong phép ghép hợp lệ có mã duy nhất; `uniqueCustomers` còn minh họa cách từ chối dữ liệu vi phạm quy định đó. Bản lọc bằng Set chủ động hỗ trợ `null` bằng cách chọn `HashSet` và dùng cùng ngữ nghĩa `Objects.equals` ở bản quét.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class IndexDemo {
    record Customer(String id, String name) {
        Customer {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }
    }

    record Order(String id, String customerId) {}
    record Line(String orderId, String sku) {}

    static List<String> filterNested(List<String> wanted, List<String> allowed) {
        List<String> result = new ArrayList<>();
        for (String value : wanted) {
            for (String candidate : allowed) {
                if (Objects.equals(value, candidate)) {
                    result.add(value);
                    break;
                }
            }
        }
        return result;
    }

    static List<String> filterIndexed(List<String> wanted, List<String> allowed) {
        Set<String> allowedIndex = new HashSet<>(allowed);
        List<String> result = new ArrayList<>();
        for (String value : wanted) {
            if (allowedIndex.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    static Map<String, Customer> uniqueCustomers(List<Customer> customers) {
        Map<String, Customer> index = new HashMap<>();
        for (Customer customer : customers) {
            Customer previous = index.putIfAbsent(customer.id(), customer);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate customer: " + customer.id());
            }
        }
        return index;
    }

    static List<String> customerJoinNested(List<Order> orders, List<Customer> customers) {
        List<String> result = new ArrayList<>();
        for (Order order : orders) {
            String name = "UNKNOWN";
            for (Customer customer : customers) {
                if (Objects.equals(order.customerId(), customer.id())) {
                    name = customer.name();
                    break;
                }
            }
            result.add(order.id() + "->" + name);
        }
        return result;
    }

    static List<String> customerJoinIndexed(List<Order> orders, List<Customer> customers) {
        Map<String, Customer> byId = uniqueCustomers(customers);
        List<String> result = new ArrayList<>();
        for (Order order : orders) {
            Customer customer = byId.get(order.customerId());
            String name = customer == null ? "UNKNOWN" : customer.name();
            result.add(order.id() + "->" + name);
        }
        return result;
    }

    static List<String> allLinesNested(List<Order> orders, List<Line> lines) {
        List<String> result = new ArrayList<>();
        for (Order order : orders) {
            for (Line line : lines) {
                if (Objects.equals(order.id(), line.orderId())) {
                    result.add(order.id() + ":" + line.sku());
                }
            }
        }
        return result;
    }

    static List<String> allLinesIndexed(List<Order> orders, List<Line> lines) {
        Map<String, List<Line>> byOrder = new HashMap<>();
        for (Line line : lines) {
            byOrder.computeIfAbsent(line.orderId(), key -> new ArrayList<>()).add(line);
        }
        List<String> result = new ArrayList<>();
        for (Order order : orders) {
            for (Line line : byOrder.getOrDefault(order.id(), List.of())) {
                result.add(order.id() + ":" + line.sku());
            }
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> wanted = Arrays.asList("A", "C", "A", null, "B");
        List<String> allowed = Arrays.asList("B", "A", null);
        List<String> filtered = filterIndexed(wanted, allowed);
        System.out.println("membership=" + filtered);
        System.out.println("membership same=" + filtered.equals(filterNested(wanted, allowed)));

        List<Customer> customers = List.of(
                new Customer("c1", "Lan"),
                new Customer("c2", "Minh"));
        List<Order> orders = List.of(
                new Order("o2", "c2"),
                new Order("o1", "c1"),
                new Order("o9", "missing"));

        List<String> names = customerJoinIndexed(orders, customers);
        System.out.println("customer join=" + names);
        System.out.println("customer join same=" + names.equals(customerJoinNested(orders, customers)));

        List<Line> lines = List.of(
                new Line("o1", "pen"),
                new Line("o2", "book"),
                new Line("o1", "ink"),
                new Line("o1", "pen"));
        List<String> allLines = allLinesIndexed(orders, lines);
        System.out.println("line join=" + allLines);
        System.out.println("line join same=" + allLines.equals(allLinesNested(orders, lines)));

        try {
            uniqueCustomers(List.of(
                    new Customer("c1", "Lan"),
                    new Customer("c1", "Other")));
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 IndexDemo.java
java IndexDemo
```

Stdout chính xác:

```text
membership=[A, A, null, B]
membership same=true
customer join=[o2->Minh, o1->Lan, o9->UNKNOWN]
customer join same=true
line join=[o2:book, o1:pen, o1:ink, o1:pen]
line join same=true
duplicate customer: c1
```

### Đọc bằng chứng trong chương trình

- `filterNested` và `filterIndexed` trả cùng List, gồm hai lần `A`, một `null` và thứ tự của `wanted`.
- Hai phép ghép khách hàng có cùng kết quả trên dữ liệu đáp ứng điều kiện mã duy nhất. Đơn `o9` vẫn được giữ với nhãn `UNKNOWN`.
- Hai phép ghép dòng hàng giữ cả hai dòng `o1:pen`; bản có chỉ mục không làm mất bản sao.
- Không in chính `HashMap`, nên stdout không phụ thuộc thứ tự duyệt Map.
- Trường hợp mã khách hàng trùng bị `uniqueCustomers` từ chối. Đây là kiểm tra điều kiện đầu vào đã công bố. Bản quét lấy lần đầu không có kiểm tra đó, nên không được nói hai hàm có cùng hành vi với mọi đầu vào bất kỳ.

Nếu phải bảo toàn hành vi lấy bản ghi đầu tiên ngay cả khi mã trùng được phép, thay phần xây chỉ mục khách hàng bằng vòng lặp dùng `putIfAbsent` và **không ném lỗi khi đã có giá trị**. Điều này đúng trong ví dụ vì mọi giá trị `Customer` đưa vào Map đều khác `null`. Nếu cần giữ mọi khách hàng trùng mã, phải đổi kiểu chỉ mục sang List theo khóa.

Chương trình xác nhận kết quả trên đầu vào mẫu, không đo tốc độ và không chứng minh mọi điều kiện biên. Khi áp dụng thật, phải kiểm tra các hợp đồng của dữ liệu riêng.

## 7. Thiết kế khóa để chỉ mục trả đúng dữ liệu

### 7.1. Quan hệ so sánh phải tương đương với bản quét

Nếu vòng cũ so bằng `customer.id().equals(...)`, chỉ mục theo `id` có thể phù hợp. Nếu vòng cũ so không phân biệt hoa/thường hoặc có chuẩn hóa khoảng trắng, dùng chuỗi thô làm khóa sẽ đổi quan hệ khớp.

Chỉ chuẩn hóa theo quy tắc nghiệp vụ đã xác định và áp dụng cùng quy tắc cho cả khi xây lẫn khi tra. Không tự `trim()` hoặc đổi hoa/thường cho mã vốn phân biệt các ký tự đó. Với so sánh đặc biệt như quan hệ theo comparator hoặc danh tính đối tượng, phải kiểm tra lại vì một `HashSet` thông thường dựa trên `equals` không nhất thiết tương đương.

### 7.2. Khóa tổng hợp nên có cấu trúc rõ

Nếu điều kiện khớp gồm mã cửa hàng và mã sản phẩm, có thể dùng record:

```java
record ProductKey(String shopId, String sku) {}

// Minh họa trong phương thức đã có các biến tương ứng:
Map<ProductKey, Product> byKey = new HashMap<>();
byKey.put(new ProductKey(product.shopId(), product.sku()), product);
Product found = byKey.get(new ProductKey(shopId, sku));
```

Đoạn này minh họa thiết kế, không phải chương trình độc lập: kiểu `Product` và các biến phải được cung cấp bởi ứng dụng. Không ghép khóa bằng phép nối chuỗi thiếu quy tắc rõ; chẳng hạn ghép trực tiếp hai thành phần có thể khiến các cặp khác nhau ra cùng chuỗi.

Record có trường chuỗi phù hợp cho ví dụ vì quan hệ bằng nhau dựa trên các thành phần ổn định. Một record chứa thành phần có thể thay đổi nội dung vẫn cần được xem xét cẩn thận; tính bất biến của biến tham chiếu không tự biến toàn bộ đồ thị dữ liệu thành bất biến.

### 7.3. Tôn trọng `equals` và `hashCode`

Các đối tượng bằng nhau theo `equals` phải có cùng mã băm. Ngược lại, cùng mã băm chưa chứng minh bằng nhau. Dùng lớp khóa tùy biến nhưng chỉ override `equals` mà bỏ qua `hashCode` có thể khiến chỉ mục không tìm được dữ liệu như dự kiến. [Hợp đồng `Object.hashCode`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#hashCode()).

Không sửa các trường tham gia so sánh/mã băm khi đối tượng đang làm khóa trong Map hoặc phần tử của Set băm. Chọn một khóa ổn định tách khỏi đối tượng nghiệp vụ có thể thay đổi thường dễ quản lý hơn. Tài liệu Map cũng cảnh báo hành vi khi thay đổi khóa làm ảnh hưởng quan hệ bằng nhau. [Tài liệu `Map`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html).

## 8. Thứ tự và thời gian sống của chỉ mục

### 8.1. Thứ tự kết quả đến từ đâu?

Nếu Map chỉ dùng để `get`, còn đầu ra được tạo khi duyệt List nguồn, thứ tự đầu ra đến từ List đó. Không cần `LinkedHashMap` chỉ vì muốn giữ thứ tự của đơn hàng trong mẫu này.

Nếu sau đó phải duyệt chính các nhóm theo thứ tự khóa xuất hiện lần đầu, `LinkedHashMap` ở chế độ thứ tự chèn có thể phù hợp. Nếu cần sắp xếp khóa, đó lại là yêu cầu khác. [Tài liệu `LinkedHashMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html).

### 8.2. Xây một lần ở đúng phạm vi

Ví dụ sai về mặt tối ưu:

```java
for (String value : wanted) {
    Set<String> index = new HashSet<>(allowed);
    if (index.contains(value)) {
        result.add(value);
    }
}
```

Mỗi vòng lại xây cùng chỉ mục nên vẫn làm khoảng O(n × m) công việc xây dựng, còn phát sinh nhiều cấp phát hơn. Đưa việc xây chỉ mục ra ngoài vòng cần tra cứu.

Nếu cùng dữ liệu được dùng cho nhiều batch, tái sử dụng chỉ mục có thể phân bổ chi phí xây dựng qua nhiều lần tra. Nhưng khi dữ liệu nguồn thay đổi, một chỉ mục được tạo trước đó không tự cập nhật cấu trúc theo List nguồn.

Map thường giữ tham chiếu tới đối tượng dữ liệu chứ không sao chép sâu. Vì thế thay đổi trường giá trị có thể nhìn thấy qua tham chiếu, trong khi việc thêm/xóa bản ghi hoặc đổi khóa không tự duy trì chỉ mục đúng. Cần quy định xây lại, cập nhật đồng bộ hoặc dùng một phiên bản dữ liệu nhất quán cho cả phép xử lý. Không đưa chỉ mục cục bộ thành cache toàn cục chỉ để tránh vài lần xây dựng mà chưa xử lý tính mới và thời gian sống dữ liệu.

Nếu có nhiều luồng cập nhật, tính an toàn của cả dữ liệu nguồn và chỉ mục là một yêu cầu riêng. Đổi sang một Map đồng thời không tự làm chuỗi “đọc nguồn → xây → tra” trở thành một ảnh chụp nhất quán.

## 9. Khi nào chỉ mục không đáng xây hoặc chưa đủ?

| Tình huống | Hướng đánh giá |
| --- | --- |
| Chỉ tra một lần trên tập rất nhỏ | Quét trực tiếp có thể đơn giản hơn và tránh bộ nhớ phụ |
| Tra rất nhiều lần trên cùng tập lớn | Chi phí xây chỉ mục có cơ hội được phân bổ tốt |
| Bộ nhớ hạn chế | Tính cả bảng băm, nút, khóa mới và các List nhóm; không chỉ số phần tử |
| Điều kiện khớp là khoảng, tiền tố hoặc độ gần đúng | Bảng băm theo khóa bằng nhau không trực tiếp giải quyết toàn bộ; có thể cần phân nhóm, cấu trúc có thứ tự hoặc thuật toán khác |
| Mọi cặp đều phải được xử lý | Chi phí đầu ra hoặc công việc cặp vẫn có thể là O(n × m) |
| Khóa thay đổi liên tục | Tính cả chi phí duy trì chỉ mục và đồng bộ dữ liệu |

Một chỉ mục nhóm có thể giảm vùng tìm kiếm rồi vẫn cần kiểm tra điều kiện phụ trong nhóm. Khi đó phải cộng chi phí số ứng viên thực sự được duyệt; không công bố O(n + m) nếu các nhóm lớn vẫn bị quét lại nhiều lần.

## 10. Kiểm chứng tối ưu theo hai bước

### 10.1. Trước hết chứng minh kết quả

Đối chiếu bản quét và bản có chỉ mục với những tình huống phù hợp hợp đồng:

- Một hoặc cả hai tập rỗng.
- Không có phần tử khớp; tất cả đều khớp; phần tử khớp đầu/cuối.
- Phần tử trùng ở phía tra cứu và phía phát kết quả.
- Khóa trùng: duy nhất, giữ đầu, giữ cuối hay giữ tất cả theo quy định đã chọn.
- `null`, giá trị thiếu, khóa tổng hợp và quy tắc chuẩn hóa nếu có hỗ trợ.
- Thứ tự List kết quả và số lượng bản sao; không chỉ so một Set của các kết quả.

Trong mã có hiệu ứng phụ hoặc hàm so sánh không thuần, thay cách tra còn có thể thay số lần gọi, thứ tự gọi và thời điểm phát sinh lỗi. Không coi việc cùng tập kết quả là đủ nếu những hành vi đó thuộc hợp đồng ứng dụng.

### 10.2. Sau đó đo đúng phạm vi

Nếu chỉ mục được xây cho từng yêu cầu, đo cả xây chỉ mục và tạo đầu ra. Nếu chỉ mục đã tồn tại để phục vụ nhiều lần tra, có thể đo riêng phần tra nhưng phải công bố chi phí xây và duy trì ở đâu.

Thử nhiều quy mô `n`, `m`, tỷ lệ khớp và mức độ trùng khóa. Với phép ghép nhiều kết quả, ghi thêm `r`. Dùng JMH khi cần phép đo vi mô đáng tin và kiểm tra tải thật để xem thay đổi có cải thiện mục tiêu của ứng dụng hay không. Không dùng số phép tra cứu để suy thành số mili giây cố định.

## 11. Bài tập tự áp dụng

Một chương trình duyệt 200.000 giao dịch và, với mỗi giao dịch, quét 80.000 cấu hình để tìm cấu hình theo cặp `(merchantId, currency)`.

1. Xác định Set có đủ không, hay phải dùng Map theo khóa tổng hợp.
2. Làm rõ nếu có hai cấu hình cùng khóa: báo lỗi, giữ đầu, giữ cuối hay cần gộp?
3. Nêu cách giữ thứ tự giao dịch và cách xử lý không tìm thấy.
4. Viết công thức chi phí gồm xây chỉ mục, tra và đầu ra; ghi giả định về băm/so sánh khóa.
5. Nếu cấu hình đổi giữa lúc xử lý, quy định phiên bản dữ liệu hoặc cách cập nhật chỉ mục.

Một phương án đúng phải tránh tìm lại dữ liệu không cần thiết và đồng thời giữ nguyên hợp đồng về quan hệ khớp, thứ tự, số lần xuất hiện và dữ liệu thiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-008">Đo bằng JMH với dữ liệu, làm nóng và phép so sánh phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Đo hiệu năng Java Collections bằng JMH: dữ liệu, warmup và so sánh công bằng

## 1. Mục tiêu và câu hỏi đo lường

`Java Microbenchmark Harness` (JMH) là công cụ của OpenJDK để đo những thao tác Java đủ nhỏ mà tác động của JIT, tối ưu hóa mã, GC, khởi động JVM và nhiễu hệ thống có thể làm phép đo thủ công sai lệch. Trong module `Java core - collection`, ta dùng JMH để trả lời một câu hỏi cụ thể: **với tập dữ liệu và kiểu truy vấn xác định, thời gian kiểm tra phần tử bằng `ArrayList.contains` và `HashSet.contains` khác nhau thế nào?**

Bài học cần phân biệt ba điều:

- **Đúng chức năng:** hai cấu trúc phải trả về cùng kết quả cho cùng khóa; mọi khóa thử đều tồn tại (hit) hoặc đều không tồn tại (miss) theo kịch bản.
- **Đúng phạm vi đo:** benchmark bên dưới chỉ đo `contains` cùng chi phí lấy khóa truy vấn; khâu tạo collection, nạp dữ liệu và tạo khóa được làm ở `@Setup`, ngoài phần đo.
- **Đúng quyết định kỹ thuật:** kết quả một phép tra cứu chưa đủ quyết định giữa `List` và `Set`. Còn phải xét thứ tự, phần tử trùng, chi phí xây dựng/cập nhật, bộ nhớ, tính địa phương của cache và tỷ lệ đọc/ghi trong ứng dụng.

Về kỳ vọng thuật toán, một lần `ArrayList.contains` quét tuần tự và có độ phức tạp O(n) trong trường hợp xấu nhất; `HashSet.contains` thường có chi phí trung bình gần O(1) với hàm băm và phân bố khóa phù hợp, nhưng không được cam kết luôn O(1) cho mọi trường hợp. JMH kiểm tra hiệu năng **trong điều kiện đo**, không chứng minh mọi đầu vào hay mọi máy đều có cùng tỷ lệ nhanh/chậm.

## 2. Thiết kế phép so sánh trước khi chạy

Đặt giả thuyết: khi kích thước collection tăng, tra cứu bằng hash có thể có lợi hơn quét tuyến tính; với collection nhỏ, chi phí cố định của hashing và bộ nhớ có thể ảnh hưởng đến chênh lệch. Giữ những biến số sau minh bạch:

| Biến số | Thiết kế ví dụ | Vì sao cần kiểm soát |
| --- | --- | --- |
| Số phần tử (`size`) | 128, 8.192, 65.536 | Thấy cách hiệu năng đổi theo kích thước, tránh kết luận từ một kích thước. |
| Trường hợp tìm kiếm (`scenario`) | `hit` và `miss` | `ArrayList` có thể dừng sớm khi tìm thấy; tìm trượt thường phải quét hết. |
| Dữ liệu lưu trữ | Các `Integer` duy nhất từ 0 đến `size - 1` ở cả hai collection | Bảo đảm cùng nội dung logic và không có khác biệt do dữ liệu đầu vào. |
| Khóa thử | 1.024 khóa sinh bằng `Random` có seed cố định; hit nằm trong collection, miss nằm ngoài | Có nhiều khóa thay đổi qua các lời gọi, đồng thời có khả năng lặp lại thí nghiệm. |
| Môi trường | Cùng JDK, JVM options, CPU, số luồng, forks và điều kiện tải máy | Hạn chế nhiễu từ các yếu tố bên ngoài. |
| Công việc mỗi lần gọi | Một lần lấy khóa và một lần `contains` | So sánh cùng nhiệm vụ, không lẫn chi phí tạo dữ liệu. |

Không so trực tiếp `ArrayList.get(index)` với `HashSet.contains(value)`: đó là hai nghiệp vụ khác nhau. Tương tự, không so `new HashSet<>(list).contains(x)` với `list.contains(x)` rồi gọi kết quả là tốc độ **tra cứu**; phương án đầu đã cộng chi phí xây dựng cấu trúc băm. Nếu nghiệp vụ thật sự tạo set để tra cứu một lần, hãy đo **toàn bộ luồng xử lý đó** ở một benchmark riêng.

## 3. Tạo dự án JMH có cấu hình build đúng

Yêu cầu: JDK 17 trở lên, Maven và khả năng tải dependency từ Maven Central. Dùng Maven archetype chính thức của JMH để có annotation processor và JAR chạy độc lập; ví dụ cố định phiên bản JMH 1.37 để dễ tái lập.

~~~bash
mvn archetype:generate -B -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DarchetypeVersion=1.37 -DgroupId=org.sample -DartifactId=collection-jmh -Dversion=1.0-SNAPSHOT
cd collection-jmh
~~~

Tạo tệp `src/main/java/org/sample/CollectionLookupBenchmark.java` với nội dung đầy đủ bên dưới. Lớp mẫu do archetype sinh ra có thể giữ nguyên: khi chạy ta sẽ lọc đúng benchmark này.

## 4. Benchmark Java hoàn chỉnh

~~~java
package org.sample;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class CollectionLookupBenchmark {

    @State(Scope.Thread)
    public static class LookupData {
        @Param({"128", "8192", "65536"})
        public int size;

        @Param({"hit", "miss"})
        public String scenario;

        List<Integer> list;
        Set<Integer> set;
        Integer[] probes;
        int position;

        @Setup(Level.Trial)
        public void prepare() {
            list = new ArrayList<>(size);
            set = new HashSet<>();

            for (int i = 0; i < size; i++) {
                Integer value = Integer.valueOf(i);
                list.add(value);
                set.add(value);
            }

            Random random = new Random(2026L + size);
            probes = new Integer[1024];

            for (int i = 0; i < probes.length; i++) {
                int selected = random.nextInt(size);
                int key = scenario.equals("hit") ? selected : size + selected;
                probes[i] = Integer.valueOf(key);
            }
        }

        Integer nextProbe() {
            return probes[position++ & (probes.length - 1)];
        }
    }

    @Benchmark
    public boolean arrayListContains(LookupData data) {
        return data.list.contains(data.nextProbe());
    }

    @Benchmark
    public boolean hashSetContains(LookupData data) {
        return data.set.contains(data.nextProbe());
    }

    @Benchmark
    public Integer probeOnly(LookupData data) {
        return data.nextProbe();
    }
}
~~~

**Tại sao viết như vậy?**

1. `@State(Scope.Thread)` yêu cầu JMH cấp state riêng cho mỗi luồng benchmark. Collection chỉ được đọc, còn `position` thay đổi trong cùng luồng; không phát sinh tranh chấp ghi giữa các luồng. Các bộ sưu tập được chuẩn bị lại cho từng trial.
2. `@Param` tạo ma trận **3 kích thước × 2 kịch bản** cho mỗi benchmark. Ta xem từng cặp có cùng tham số trước khi so sánh hai cấu trúc.
3. `@Setup(Level.Trial)` nạp dữ liệu **trước khi đo**. Vì vậy kết quả không phải thời gian tạo `HashSet` hoặc `ArrayList`. Nếu câu hỏi là chi phí xây dựng, cần benchmark xây dựng riêng.
4. Seed cố định làm dữ liệu đầu vào ổn định giữa các lần chạy và giữa hai phương án. `probes` đã được tạo dưới dạng `Integer[]`, nên việc boxing và sinh số ngẫu nhiên không nằm trong thời gian `contains`; cả hai phương án truy vấn cùng loại khóa.
5. Mỗi `nextProbe()` đi qua 1.024 vị trí bằng phép AND vì 1.024 là lũy thừa của hai. Khóa thay đổi theo lời gọi, tránh chỉ đo một hằng số duy nhất. Benchmark vẫn bao gồm phần lấy khóa và tăng `position`; đây là chi phí nền chung, đặc biệt đáng chú ý khi thao tác tra cứu rất nhanh.
6. `hit` dùng khóa trong đoạn từ 0 đến `size - 1`; `miss` dùng từ `size` đến `2 × size - 1`. Vì vậy mọi lần gọi trong mỗi kịch bản trả về kết quả logic đã định: `true` hoặc `false`, giống nhau cho cả `List` lẫn `Set`.
7. Mỗi phương thức `@Benchmark` **trả về kết quả** để JMH tiêu thụ, làm giảm nguy cơ JIT xóa phép tính vì kết quả không được dùng (*dead-code elimination*). Nếu cần dùng nhiều kết quả trong cùng một lượt đo, có thể tiêm `Blackhole` và gọi `consume`.
8. `probeOnly` là phép đo tham khảo cho công việc chọn khóa. Không được lấy số đo của nó trừ thẳng khỏi số đo `contains` và xem hiệu số là độ trễ chính xác: JIT, tối ưu hóa và chi phí lời gọi của các benchmark có thể khác nhau.

### Các annotation ảnh hưởng đến phép đo

- `@BenchmarkMode(Mode.AverageTime)`: báo **thời gian trung bình cho một operation**, càng nhỏ càng tốt nếu ngữ nghĩa tương đương. Với throughput, dùng `Mode.Throughput` và đọc đơn vị operations/giây, khi đó càng cao càng tốt.
- `@OutputTimeUnit(TimeUnit.NANOSECONDS)`: trình bày kết quả dưới dạng `ns/op`, không bảo đảm phần cứng đo chính xác tuyệt đối đến từng nano giây.
- `@Warmup(iterations = 5, time = 1)`: 5 vòng làm nóng, mỗi vòng 1 giây; dữ liệu warmup không tính vào điểm số. JVM có thời gian tải lớp, JIT biên dịch và tối ưu đường chạy nóng.
- `@Measurement(iterations = 5, time = 1)`: tiếp theo là 5 vòng đo, mỗi vòng 1 giây cho một tập tham số và fork.
- `@Fork(2)`: chạy ở 2 tiến trình JVM con riêng biệt, giúp giảm phụ thuộc vào trạng thái một JVM. Dùng nhiều forks/iterations hơn khi cần độ tin cậy cao, chấp nhận thời gian chạy dài hơn.

Warmup **không tương đương** với chạy thử một lần bằng `System.nanoTime()`. JIT có thể biên dịch lại khi hồ sơ thực thi thay đổi; cấp phát và GC cũng có thể ảnh hưởng. JMH kiểm soát vòng warmup, measurement, fork và cách lấy mẫu, nhưng người thiết kế vẫn phải chọn workload hợp lý.

## 5. Build và chạy phép đo

Tại thư mục `collection-jmh`:

~~~bash
mvn clean verify
java -jar target/benchmarks.jar "CollectionLookupBenchmark.*" -t 1 -wi 5 -i 5 -w 1s -r 1s -f 2 -rf json -rff target/jmh-results.json
~~~

Ý nghĩa một số tham số CLI:

- `"CollectionLookupBenchmark.*"` chỉ chọn ba benchmark của lớp này; không chạy benchmark mẫu có sẵn của archetype.
- `-t 1` dùng một luồng benchmark; giảm nhiễu do tranh chấp CPU. Muốn đánh giá truy cập đồng thời, phải thiết kế benchmark riêng với state và mức cạnh tranh phù hợp.
- `-wi 5 -w 1s` là 5 vòng warmup, mỗi vòng 1 giây; `-i 5 -r 1s` là 5 vòng đo, mỗi vòng 1 giây. Tham số CLI ghi đè mặc định ở annotation.
- `-f 2` sử dụng 2 JVM forks; `-rf json -rff ...` ghi kết quả máy đọc được ra `target/jmh-results.json`.
- Để thử riêng một tình huống, có thể bổ sung `-p size=8192 -p scenario=hit` vào lệnh trên. Dùng tùy chọn `-h` của JAR để xem các cờ theo phiên bản JMH đang chạy.

Trước thí nghiệm chính, chạy smoke test ngắn với ít iterations để phát hiện lỗi cấu hình; không lấy điểm số smoke test làm kết luận. Khi đo chính thức, chạy máy ở điều kiện tương đối ổn định, tránh build nặng/chạy tác vụ nền, ghi rõ cấu hình phần cứng và môi trường Java. Kiểm tra nhiệt độ CPU, chế độ tiết kiệm điện, tần số, tài nguyên RAM và việc xuất hiện GC nếu dữ liệu đủ lớn.

Có thể chạy lại với profiler GC tích hợp của JMH:

~~~bash
java -jar target/benchmarks.jar "CollectionLookupBenchmark.*" -t 1 -f 2 -prof gc
~~~

Thông tin `gc.alloc.rate.norm` giúp xem byte được cấp phát trên mỗi operation trong điều kiện đo. Nó không đo trực tiếp tổng bộ nhớ mà collection đã chiếm khi dựng ở `@Setup`; muốn đánh giá footprint thực tế, dùng thêm đo bộ nhớ riêng có phương pháp kiểm soát.

## 6. Đọc kết quả và suy luận đúng

JMH xuất bảng với các cột như `Benchmark`, tham số `size`/`scenario`, `Mode`, `Cnt`, `Score`, `Error`, `Units`. Với `avgt` và `ns/op`, so `arrayListContains` với `hashSetContains` **trong đúng cùng kích thước, hit/miss, JDK, cấu hình và điều kiện chạy**. Hai nhóm số đo khác điều kiện không tạo thành so sánh kiểm soát.

Có thể tính tỷ lệ `Score(list) / Score(set)` khi cả hai đo cùng operation, score dương, đủ ổn định; tỷ lệ lớn hơn 1 biểu thị `HashSet` nhanh hơn về average time **trong trường hợp đo đó**. Báo cả điểm số, độ biến động/ước lượng sai số và số lần đo; không chỉ báo “nhanh hơn X lần”. Nếu chênh lệch cùng cỡ nhiễu, cần tăng phép lặp, xem phân tán giữa forks và chạy lại trong môi trường sạch hơn trước khi kết luận. `Error` không nên hiểu là bảo chứng rằng kết quả sẽ lặp lại y hệt trên máy khác.

Khi diễn giải, hãy trả lời lần lượt:

1. **`size` có ảnh hưởng gì?** Với tuyến tính, miss thường tốn nhiều lần so sánh hơn khi n tăng. Hash lookup có thể ổn định hơn nhưng phụ thuộc hash, cache, collision và tải bộ nhớ.
2. **`hit` khác `miss` ra sao?** List tìm thấy có thể kết thúc ở vị trí bất kỳ; list miss thường quét hết. Set hit và miss có đặc tính khác nhau về hash, so sánh khóa và nhánh rẽ.
3. **`probeOnly` đáng kể không?** Nếu thời gian chọn khóa chiếm tỷ trọng lớn so với lookup, phép đo nhỏ khó phân biệt. Có thể thiết kế benchmark nhiều thao tác trên một invocation và chuẩn hóa `@OperationsPerInvocation` khi cần, đồng thời phải cẩn thận để không kích hoạt loop optimizations làm sai kết quả.
4. **Cấu trúc nào phù hợp ứng dụng?** Nếu chỉ kiểm tra thành viên nhiều lần trên tập ổn định và chấp nhận bộ nhớ bổ sung, `HashSet` là ứng viên đáng thử. Nếu cần giữ thứ tự, cho phép trùng hoặc tập rất nhỏ, `ArrayList` có thể phù hợp hơn. Không đánh đồng performance của `contains` với performance mọi thao tác của collection.

**Phân biệt microbenchmark và end-to-end:** JMH chỉ trả lời tác vụ được định nghĩa. Quyết định triển khai còn cần dữ liệu sản xuất đại diện, độ trễ toàn luồng, throughput thực tế, bộ nhớ, GC, hiệu năng khi thêm/xóa, tính đúng đắn và hành vi khi chạy đồng thời.

## 7. Sai lầm thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách làm phù hợp |
| --- | --- | --- |
| Một lần chạy `System.nanoTime()` rồi kết luận | Chịu nhiễu khởi động, JIT, GC và scheduler | Dùng JMH với warmup, measurements và forks có kiểm soát. |
| Dùng một giá trị hằng, bỏ qua kết quả | JIT có thể gấp hằng hoặc bỏ phép tính | Dùng nhiều khóa đã chuẩn bị và trả về kết quả hoặc `Blackhole`. |
| Sinh `Random`, nạp dữ liệu, log, in console trong `@Benchmark` | Đo cả phần việc khác, I/O lấn át thao tác | Đưa fixture vào `@Setup(Level.Trial)` nếu chỉ đo lookup. |
| Mỗi bên nhận tập dữ liệu khác hoặc tỷ lệ hit khác | Khác workload nên không so sánh được | Dùng cùng quy tắc sinh, seed, kích thước và kịch bản. |
| Đo một `size` rất nhỏ rồi khái quát mọi quy mô | Bỏ qua chuyển dịch chi phí theo n và cache | Thử nhiều kích thước phù hợp dữ liệu thật. |
| So `List.get` với `Set.contains` hoặc bỏ qua chi phí xây set | So nghiệp vụ khác nhau, kết luận sai | Giữ cùng operation; thêm benchmark end-to-end nếu cần. |
| Cấp lại state bằng `@Setup(Level.Invocation)` một cách máy móc | Chi phí setup mỗi lời gọi và tương tác đo có thể đáng kể | Chọn đúng lifecycle; đọc rõ JMH xử lý phần setup và đo thao tác rất ngắn. |
| Chạy cùng lúc IDE, tải CPU cao hoặc đổi JDK giữa phép thử | Nhiễu hệ thống và JIT khác | Đo từ executable JAR, ghi cấu hình, kiểm soát môi trường. |
| Chỉ đọc điểm trung bình, bỏ qua forks/sai số | Dễ chọn phương án từ một kết quả không ổn định | Xem độ phân tán, chạy lặp lại và tái kiểm tra khi khác biệt nhỏ. |
| Cho rằng nhanh nhất theo `ns/op` là tối ưu toàn hệ thống | Không xét RAM, thiết lập, ghi, concurrency và GC | Ghép JMH với profiling và benchmark theo luồng nghiệp vụ thật. |

## 8. Quy trình áp dụng cho phép so sánh collection khác

Khi đổi sang `HashMap`/`TreeMap`, `ArrayList`/`LinkedList` hay `HashSet`/`TreeSet`, hãy bắt đầu bằng **một hành vi chung có cùng ý nghĩa** (ví dụ lookup cùng khóa hoặc duyệt toàn bộ cùng tập dữ liệu), rồi xác định rõ tập dữ liệu, kích thước, phân bố hit/miss, cách khởi tạo, số lượng thao tác, thứ tự chạy, số luồng và mục tiêu đầu ra. Tạo các dữ liệu thử đại diện ngoài thời gian đo; viết kiểm thử chức năng riêng để xác nhận kết quả trước khi benchmark; cho JMH warmup/measurement/fork đúng mức; cuối cùng diễn giải điểm số gắn với workload và trade-off.

Cần đánh giá nhiều chi phí thì tạo **nhiều benchmark có phạm vi tường minh**: một benchmark lookup trên cấu trúc đã dựng, một benchmark xây dựng cấu trúc, một benchmark cập nhật, và một phép đo workload kết hợp với tỷ lệ thao tác sát ứng dụng. Đừng gộp chúng thành một con số rồi gọi là “collection nhanh nhất”.

### Tài liệu đối chiếu

- Dự án JMH chính thức: https://github.com/openjdk/jmh
- Ví dụ về `@Param`: https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_27_Params.java
- Ví dụ về dead-code elimination: https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java
- Ví dụ về `Blackhole`: https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_09_Blackholes.java

**Kết quả mong đợi của bài thực hành:** tự chạy JMH trên JDK 17+, đọc đúng `ns/op` và tham số hit/miss, giải thích được vì sao cần warmup và forks, biết lựa chọn dữ liệu đại diện và phân biệt sự cải thiện của một thao tác lookup với lợi ích của cả ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)
