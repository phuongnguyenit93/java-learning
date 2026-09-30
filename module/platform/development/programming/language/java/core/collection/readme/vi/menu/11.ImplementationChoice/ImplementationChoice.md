# Lựa chọn cách triển khai Collection

Chặng cuối của ROADMAP, **Lựa chọn cách triển khai và cấu trúc chuyên biệt**, tổng hợp các quy ước đã học thành một quy trình ra quyết định thực tế. Chọn collection nên bắt đầu từ **ngữ nghĩa mà bài toán cần**: có cần chỉ số không, có cho phần tử trùng không, có cần tra cứu theo khóa không, thứ tự nào phải được bảo toàn, phần tử nào cần lấy ra trước. Sau khi quy ước đúng mới xét độ phức tạp, bộ nhớ, khả năng thay đổi, chính sách `null` và kiểu sử dụng.

Việc chọn chỉ vì “`HashMap` nhanh” hoặc “`LinkedList` chèn O(1)” dễ sai vì độ phức tạp còn phụ thuộc thao tác đi kèm. Ví dụ chèn vào giữa `LinkedList` vẫn phải tìm nút trước nếu bên gọi chỉ có chỉ số.

## <a id="list-choice">Chọn List: ArrayList hay LinkedList?</a>

`ArrayList` là lựa chọn mặc định tốt cho phần lớn chuỗi dữ liệu cần chỉ số và duyệt:

- `get(index)`: O(1).
- thêm cuối: amortized O(1).
- chèn/xóa giữa List: O(n) vì phải dịch phần tử.
- dữ liệu nằm liên tục trong mảng nền nên thường có tính cục bộ bộ nhớ tốt.

`LinkedList` dùng các nút liên kết hai chiều:

- `get(index)`: O(n).
- thêm/xóa ở đầu/cuối: O(1).
- thêm/xóa tại vị trí **sau khi đã có nút/iterator ở vị trí đó**: liên kết lại nút là O(1).
- mỗi phần tử tốn thêm tham chiếu cho liên kết và có tính cục bộ bộ nhớ kém hơn mảng.

Vì vậy “nhiều thao tác chèn” chưa đủ để chọn `LinkedList`. Nếu mỗi lần chèn bắt đầu bằng `get(i)` hoặc duyệt từ đầu, chi phí tìm vị trí vẫn là O(n). Nếu kiểu sử dụng chủ yếu là thêm cuối, duyệt và truy cập ngẫu nhiên, `ArrayList` thường phù hợp hơn.

Nếu nhu cầu thực sự là thêm/xóa ở hai đầu, hãy xem `Deque`; `ArrayDeque` thường diễn đạt ý định đó trực tiếp hơn.

## <a id="set-choice">Chọn Set: HashSet, LinkedHashSet hay TreeSet?</a>

Ba cách triển khai cùng giữ tính duy nhất nhưng khác quy ước về thứ tự và chi phí:

| Cách triển khai | Thứ tự | tra cứu/thêm/xóa | Khi phù hợp |
| --- | --- | --- | --- |
| `HashSet` | Không cam kết thứ tự duyệt | kỳ vọng O(1) | Chỉ cần kiểm tra thành viên/tính duy nhất |
| `LinkedHashSet` | Giữ thứ tự duyệt theo thứ tự chèn | kỳ vọng O(1) | Cần tính duy nhất và thứ tự duyệt ổn định |
| `TreeSet` | Sắp theo thứ tự tự nhiên/comparator | O(log n) | Cần dữ liệu luôn có thứ tự và phạm vi/điều hướng |

`LinkedHashSet` trong Java 21 tham gia `SequencedSet`, nên thứ tự còn được truy cập rõ bằng các thao tác đầu/cuối và `reversed()`.

`TreeSet` yêu cầu thứ tự hợp lệ và dùng kết quả so sánh để quyết định tính duy nhất. Nếu comparator trả `0` cho hai đối tượng mà nghiệp vụ vẫn muốn giữ cả hai, comparator hoặc cấu trúc đang chọn chưa phù hợp.

## <a id="map-choice">Chọn Map: HashMap, LinkedHashMap hay TreeMap?</a>

Chọn map dựa trên quy ước tra cứu theo khóa và thứ tự:

| Cách triển khai | Thứ tự khóa | tra cứu/put/xóa | Điểm nổi bật |
| --- | --- | --- | --- |
| `HashMap` | Không cam kết thứ tự duyệt | kỳ vọng O(1) | Tra cứu theo khóa đa dụng |
| `LinkedHashMap` | Thứ tự chèn hoặc thứ tự truy cập | kỳ vọng O(1) | Duyệt có thứ tự, có thể xây cache theo thứ tự truy cập |
| `TreeMap` | Sắp theo khóa | O(log n) | Truy vấn phạm vi, đầu/cuối và điều hướng khóa lân cận |

`LinkedHashMap` trong Java 21 là `SequencedMap`, giúp biểu diễn rõ thứ tự duyệt đầu/cuối/đảo ngược. Constructor với `accessOrder=true` còn cho phép thứ tự thay đổi theo lần truy cập, hữu ích cho cấu trúc kiểu LRU khi kết hợp chính sách loại bỏ phù hợp.

Nếu khóa là enum, đừng mặc định dùng `HashMap`. `EnumMap` ở chương tiếp theo thể hiện miền dữ liệu rõ hơn và có cách biểu diễn chuyên biệt.

## <a id="queue-choice">Chọn Queue: ArrayDeque, LinkedList hay PriorityQueue?</a>

Nếu cần FIFO/LIFO hoặc thao tác hai đầu, `ArrayDeque` thường là lựa chọn đa dụng:

- thêm/xóa đầu và cuối hiệu quả;
- dùng được như Queue qua `offerLast/pollFirst`;
- dùng được như stack qua `push/pop`;
- không cho `null`.

`LinkedList` cũng triển khai `Deque` và `List`. Nó phù hợp khi thật sự cần quy ước/hành vi của danh sách liên kết; việc dùng nó chỉ vì cần Queue thường không đem lại lợi ích rõ ràng so với `ArrayDeque` và có thêm chi phí cho các nút.

`PriorityQueue` giải một bài toán khác: luôn lấy phần tử có **độ ưu tiên cao nhất theo quy tắc sắp xếp** ở đầu Queue — tức phần tử nhỏ nhất theo comparator, trừ khi comparator được đảo thứ tự.

```java
PriorityQueue<Order> queue = new PriorityQueue<>(
        Comparator.comparingInt(Order::totalCents).reversed()
);

queue.offer(new Order(1, 1, 20_000));
queue.offer(new Order(2, 2, 10_000));

System.out.println(queue.peek().id()); // 1
```

Iterator của `PriorityQueue` **không cam kết** trả mọi phần tử theo thứ tự ưu tiên. Muốn lấy lần lượt theo độ ưu tiên, phải `poll()`. Các phần tử có cùng độ ưu tiên cũng không có thứ tự ổn định mặc định.

## <a id="null-policy-matrix">Chính sách null của các lớp triển khai phổ biến</a>

Chính sách `null` thuộc **quy ước của cách triển khai**, không thể suy ra chỉ từ `List`, `Set`, `Map` hay `Queue`. Một bảng nhớ nhanh:

| Cách triển khai | Chính sách `null` cơ bản |
| --- | --- |
| `ArrayList` | cho phép `null` phần tử |
| `LinkedList` | cho phép phần tử `null`, nhưng dùng `null` trong API kiểu Queue dễ gây nhập nhằng |
| `HashSet` / `LinkedHashSet` | cho phép một `null` phần tử |
| `TreeSet` | thứ tự tự nhiên thông thường không chấp nhận `null`; comparator tùy chỉnh có thể định nghĩa cách xử lý |
| `HashMap` / `LinkedHashMap` | cho phép một `null` khóa và nhiều `null` giá trị |
| `TreeMap` | thứ tự tự nhiên thông thường không chấp nhận `null` khóa; comparator tùy chỉnh có thể định nghĩa; `null` giá trị được phép |
| `ArrayDeque` | không cho `null` |
| `PriorityQueue` | không cho `null` |
| `List.of` / `Set.of` / `Map.of` và các họ `copyOf` tương ứng | không cho `null` phần tử/khóa/giá trị |

Không nên biến bảng này thành lý do thiết kế API bằng `null`. Đặc biệt với `Queue`, `poll/peek` dùng `null` để biểu diễn “không có phần tử”, nên phần tử `null` sẽ làm ngữ nghĩa khó đọc ngay cả khi một cách triển khai khác có thể cho phép nó.

## <a id="choice-by-characteristics">Chọn theo đặc tính trước, tối ưu sau</a>

Một quy trình lựa chọn thực tế:

1. Xác định mô hình trừu tượng: `List`, `Set`, `Map` hay `Queue/Deque`.
2. Xác định ngữ nghĩa tính duy nhất/khóa và thứ tự bắt buộc.
3. Xác định các thao tác chính: truy cập ngẫu nhiên, kiểm tra thành viên, tra cứu theo phạm vi, thao tác đầu/cuối, lấy theo độ ưu tiên...
4. Xác định mô hình thay đổi dữ liệu: có thể sửa hoàn toàn, cố định kích thước/liên kết với nguồn, khung nhìn không cho sửa hay bản chụp độc lập.
5. Kiểm tra chính sách `null` và giới hạn của các phương thức tạo. Ví dụ `List.of`/`Set.of`/`Map.of` và các họ `copyOf` từ chối `null`; `Set.of` từ chối đối số trùng và `Map.of` từ chối khóa trùng.
6. Tính đến chi phí bộ nhớ và kích thước dữ liệu dự kiến. Nút liên kết, metadata giữ thứ tự, nút cây và dung lượng mảng dự phòng có chi phí khác nhau; Big-O không mô tả hết phần bộ nhớ này.
7. Tìm cấu trúc chuyên biệt theo miền trước khi mặc định chọn cấu trúc đa dụng. Nếu phần tử hoặc khóa thuộc cùng một kiểu enum, `EnumSet` hoặc `EnumMap` có thể biểu đạt miền dữ liệu chính xác hơn.
8. Chỉ đo hiệu năng khi hiệu năng thực sự là vấn đề; đừng biến Big-O thành phỏng đoán về tối ưu vi mô.

Ví dụ với cùng dữ liệu `Order`:

```text
cần giữ thứ tự nhập và cho phép phần tử trùng
→ ArrayList<Order>

cần tập `orderId` duy nhất, không quan tâm thứ tự
→ HashSet<Long>

cần tra cứu Order theo id
→ HashMap<Long, Order>

cần ánh xạ theo id và duyệt theo thứ tự chèn
→ LinkedHashMap<Long, Order>

cần xử lý Order theo độ ưu tiên
→ PriorityQueue<Order>
```

Các tiêu chí trên có thể làm thay đổi lựa chọn ngay cả khi hai cách triển khai cùng thỏa một interface. Một collection có độ phức tạp phù hợp vẫn có thể sai nếu nó cho phép thay đổi mà API cần ngăn, từ chối `null` mà bài toán cần, làm mất bảo đảm thứ tự, vi phạm giới hạn của phương thức tạo hoặc mang chi phí cấu trúc không cần thiết cho kiểu sử dụng.

Ưu tiên trả về interface phù hợp với quy ước của API, chẳng hạn `List<Order>` thay vì ép bên gọi phụ thuộc `ArrayList<Order>`, trừ khi tính năng riêng của cách triển khai là một phần cố ý của quy ước.

Chương tiếp theo hoàn tất chặng học này với `EnumSet` và `EnumMap`. Cách biểu diễn chuyên biệt của chúng hữu ích khi miền dữ liệu là enum, nhưng lý do chính để chọn vẫn là quy ước kiểu và thứ tự của chúng khớp với miền bài toán.

An toàn luồng là một trục lựa chọn khác nhưng không nên giải bằng việc “đổi đại” collection ở chương này. Khi dữ liệu được chia sẻ giữa nhiều luồng, cần xét quyền sở hữu, đồng bộ hóa và các collection hỗ trợ truy cập đồng thời trong module về xử lý đồng thời tương ứng.
