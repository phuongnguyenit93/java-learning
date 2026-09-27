# Lựa chọn Collection Implementation

Chọn collection nên bắt đầu từ **ngữ nghĩa mà bài toán cần**: có cần index không, có cho duplicate không, có cần lookup theo key không, thứ tự nào phải được bảo toàn, phần tử nào cần lấy ra trước. Sau khi quy ước đúng mới xét độ phức tạp, bộ nhớ và đặc điểm tải thao tác.

Việc chọn chỉ vì “`HashMap` nhanh” hoặc “`LinkedList` insert O(1)” dễ sai vì độ phức tạp còn phụ thuộc thao tác đi kèm. Ví dụ insert vào giữa `LinkedList` vẫn phải tìm node trước nếu bên gọi chỉ có index.

## <a id="list-choice">Chọn List: ArrayList hay LinkedList?</a>

`ArrayList` là lựa chọn mặc định tốt cho phần lớn sequence cần index và duyệt:

- `get(index)`: O(1).
- append: amortized O(1).
- insert/remove giữa list: O(n) vì phải dịch phần tử.
- dữ liệu nằm liên tục trong backing array nên thường có locality tốt.

`LinkedList` dùng node liên kết hai chiều:

- `get(index)`: O(n).
- thêm/xóa ở đầu/cuối: O(1).
- thêm/xóa tại vị trí **sau khi đã có node/iterator ở vị trí đó**: liên kết lại node là O(1).
- mỗi phần tử tốn thêm reference cho liên kết và locality kém hơn array.

Vì vậy “nhiều insert” chưa đủ để chọn `LinkedList`. Nếu mỗi insert bắt đầu bằng `get(i)` hoặc traversal từ đầu, chi phí tìm vị trí vẫn là O(n). Nếu tải thao tác chủ yếu là append, iterate và random access, `ArrayList` thường phù hợp hơn.

Nếu nhu cầu thực sự là thêm/xóa ở hai đầu, hãy xem `Deque`; `ArrayDeque` thường diễn đạt intent đó trực tiếp hơn.

## <a id="set-choice">Chọn Set: HashSet, LinkedHashSet hay TreeSet?</a>

Ba cách triển khai cùng giữ uniqueness nhưng khác quy ước về thứ tự và chi phí:

| Implementation | Thứ tự | Lookup/add/remove | Khi phù hợp |
| --- | --- | --- | --- |
| `HashSet` | Không cam kết encounter order | expected O(1) | Chỉ cần membership/uniqueness |
| `LinkedHashSet` | Giữ encounter order theo insertion | expected O(1) | Cần uniqueness và thứ tự duyệt ổn định |
| `TreeSet` | Sorted theo natural order/comparator | O(log n) | Cần dữ liệu luôn có thứ tự và range/navigation |

`LinkedHashSet` trong Java 21 tham gia `SequencedSet`, nên order còn được truy cập rõ bằng các operation đầu/cuối và `reversed()`.

`TreeSet` yêu cầu ordering hợp lệ và dùng kết quả comparison để quyết định uniqueness. Nếu comparator trả `0` cho hai object mà nghiệp vụ vẫn muốn giữ cả hai, comparator hoặc cấu trúc đang chọn chưa phù hợp.

## <a id="map-choice">Chọn Map: HashMap, LinkedHashMap hay TreeMap?</a>

Chọn map dựa trên quy ước lookup theo key và thứ tự:

| Implementation | Thứ tự key | Lookup/put/remove | Điểm nổi bật |
| --- | --- | --- | --- |
| `HashMap` | Không cam kết encounter order | expected O(1) | General-purpose key lookup |
| `LinkedHashMap` | Insertion order hoặc access order | expected O(1) | Duyệt có thứ tự, có thể xây access-order cache |
| `TreeMap` | Sorted theo key | O(log n) | Range query, first/last, nearest-key style navigation |

`LinkedHashMap` Java 21 là `SequencedMap`, giúp biểu diễn rõ first/last/reversed encounter order. Constructor với `accessOrder=true` còn cho phép thứ tự thay đổi theo lần truy cập, hữu ích cho cấu trúc kiểu LRU khi kết hợp eviction policy thích hợp.

Nếu key là enum, đừng mặc định dùng `HashMap`. `EnumMap` ở chương tiếp theo thể hiện miền dữ liệu rõ hơn và có cách biểu diễn chuyên biệt.

## <a id="queue-choice">Chọn Queue: ArrayDeque, LinkedList hay PriorityQueue?</a>

Nếu cần FIFO/LIFO hoặc thao tác hai đầu, `ArrayDeque` thường là lựa chọn đa dụng:

- thêm/xóa đầu và cuối hiệu quả;
- dùng được như queue qua `offerLast/pollFirst`;
- dùng được như stack qua `push/pop`;
- không cho `null`.

`LinkedList` cũng implement `Deque` và `List`. Nó phù hợp khi thật sự cần quy ước/hành vi node của linked list; việc dùng nó chỉ vì cần queue thường không đem lại lợi ích rõ ràng so với `ArrayDeque` và có thêm chi phí cho node.

`PriorityQueue` giải một bài toán khác: luôn lấy phần tử có **priority nhỏ nhất theo ordering** ở head (hoặc lớn nhất nếu comparator đảo thứ tự).

```java
PriorityQueue<Order> queue = new PriorityQueue<>(
        Comparator.comparingInt(Order::totalCents).reversed()
);

queue.offer(new Order(1, 1, 20_000));
queue.offer(new Order(2, 2, 10_000));

System.out.println(queue.peek().id()); // 1
```

Iterator của `PriorityQueue` **không cam kết** trả mọi phần tử theo priority order. Muốn lấy lần lượt theo priority, phải `poll()`. Các phần tử có cùng priority cũng không có stable ordering mặc định.

## <a id="null-policy-matrix">Null policy của các implementation phổ biến</a>

`null` policy thuộc **implementation contract**, không thể suy ra chỉ từ `List`, `Set`, `Map` hay `Queue`. Một bảng nhớ nhanh:

| Implementation | `null` policy cơ bản |
| --- | --- |
| `ArrayList` | cho phép `null` element |
| `LinkedList` | cho phép `null` element, nhưng dùng `null` trong queue API dễ gây ambiguity |
| `HashSet` / `LinkedHashSet` | cho phép một `null` element |
| `TreeSet` | natural ordering thông thường không chấp nhận `null`; comparator tùy chỉnh có thể định nghĩa cách xử lý |
| `HashMap` / `LinkedHashMap` | cho phép một `null` key và nhiều `null` value |
| `TreeMap` | natural ordering thông thường không chấp nhận `null` key; comparator tùy chỉnh có thể định nghĩa; `null` value được phép |
| `ArrayDeque` | không cho `null` |
| `PriorityQueue` | không cho `null` |
| `List.of` / `Set.of` / `Map.of` và các family `copyOf` tương ứng | không cho `null` element/key/value |

Không nên biến bảng này thành lý do thiết kế API bằng `null`. Đặc biệt với `Queue`, `poll/peek` dùng `null` để biểu diễn “không có phần tử”, nên element `null` sẽ làm semantics khó đọc ngay cả khi một implementation khác có thể cho phép nó.

## <a id="choice-by-characteristics">Chọn theo đặc tính trước, tối ưu sau</a>

Một quy trình lựa chọn thực tế:

1. Xác định abstraction: `List`, `Set`, `Map` hay `Queue/Deque`.
2. Xác định uniqueness/key semantics và ordering bắt buộc.
3. Xác định operation nóng: random access, membership lookup, range query, endpoint operations, priority removal...
4. Xem xét quy tắc về null, mutability, bộ nhớ và kích thước dữ liệu.
5. Chỉ benchmark khi hiệu năng thực sự là vấn đề; đừng biến Big-O thành phỏng đoán về hiệu năng vi mô.

Ví dụ với cùng dataset `Order`:

```text
cần giữ thứ tự nhập và cho duplicate
→ ArrayList<Order>

cần tập orderId duy nhất, không quan tâm thứ tự
→ HashSet<Long>

cần lookup Order theo id
→ HashMap<Long, Order>

cần map theo id và duyệt theo thứ tự insert
→ LinkedHashMap<Long, Order>

cần xử lý order theo priority
→ PriorityQueue<Order>
```

Ưu tiên trả về interface phù hợp với quy ước của API, chẳng hạn `List<Order>` thay vì ép bên gọi phụ thuộc `ArrayList<Order>`, trừ khi tính năng riêng của cách triển khai là một phần cố ý của quy ước.

Thread-safety là một trục lựa chọn khác nhưng không nên giải bằng việc “đổi đại” collection ở chapter này. Khi dữ liệu được chia sẻ giữa thread, cần xét ownership, synchronization và concurrent collections trong phần concurrency tương ứng.
