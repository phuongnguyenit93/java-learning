# Các mô hình dữ liệu cốt lõi — List

Sau khi biết cách đọc hệ phân cấp, `List` là quy ước đầu tiên đáng học sâu vì nó phù hợp với dữ liệu mà **thứ tự và vị trí có ý nghĩa**. Danh sách đơn hàng theo thời điểm nhận là ví dụ tự nhiên: hai đơn hàng giống nhau vẫn có thể cùng xuất hiện, và “đơn đầu tiên” khác “đơn thứ ba”.

## <a id="list-semantics">Ngữ nghĩa của List</a>

`List<E>` là collection có thứ tự duyệt xác định và hỗ trợ truy cập theo vị trí bằng chỉ số bắt đầu từ 0.

```java
List<Order> orders = new ArrayList<>();
orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 8, 90));
orders.add(new Order(1003, 7, 120));

Order first = orders.get(0);
Order second = orders.get(1);
```

Thứ tự trong `List` là một phần của quy ước. Nếu thêm phần tử vào cuối, nó trở thành phần tử sau cùng; nếu chèn ở chỉ số 1, các phần tử phía sau dịch vị trí.

`List` cũng cho phép phần tử trùng theo `equals`:

```java
List<String> statuses = new ArrayList<>();
statuses.add("NEW");
statuses.add("NEW");

System.out.println(statuses.size()); // 2
```

Điều này khác `Set`, nơi tính duy nhất là quy ước chính. Nếu yêu cầu nghiệp vụ nói “mọi lần xuất hiện đều có ý nghĩa”, `List` thường là điểm bắt đầu phù hợp.

Các thao tác đặc trưng:

```java
orders.add(order);          // thêm cuối
orders.add(1, order);       // chèn theo vị trí
orders.get(1);              // đọc theo vị trí
orders.set(1, replacement); // thay phần tử tại vị trí
orders.remove(1);           // xóa theo vị trí
```

Từ Java 21, `List` là một `SequencedCollection`. Vì vậy các list phù hợp có API thống nhất để làm việc với hai đầu như `getFirst()`, `getLast()`, `addFirst()`, `addLast()` và `reversed()`. Khả năng sửa vẫn phụ thuộc collection cụ thể; một list không cho phép sửa có thể ném `UnsupportedOperationException` cho thao tác thay đổi.

## <a id="list-practical-boundaries">Các ranh giới thực tế của List</a>

### `remove(index)` khác `remove(value)` như thế nào?

`List` nạp chồng phương thức `remove`, nên với kiểu số rất dễ gọi nhầm:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

numbers.remove(1);                  // xóa phần tử tại index 1 -> 20
numbers.remove(Integer.valueOf(10)); // xóa value 10
```

Đối số kiểu nguyên thủy `int` chọn phiên bản `remove(int index)`. Muốn xóa một `Integer` theo giá trị, hãy truyền rõ một đối tượng `Integer`.

### `subList` là khung nhìn, không phải bản sao

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> middle = source.subList(1, 3); // [B, C]

middle.set(0, "X");
System.out.println(source); // [A, X, C, D]
```

`subList(from, to)` trả về một **khung nhìn liên kết với dữ liệu gốc** của một vùng trong List gốc. Thay đổi dữ liệu hợp lệ qua khung nhìn phản ánh vào nguồn và ngược lại. Nếu List nền bị thay đổi cấu trúc bằng bất kỳ cách nào ngoài thông qua sub-list được trả về, ngữ nghĩa của sub-list đó trở thành **không xác định**. Một lớp triển khai cụ thể có thể phát hiện trạng thái không khớp và ném `ConcurrentModificationException`, nhưng CME không phải là quy ước mà `subList` bảo đảm.

Nếu cần dữ liệu độc lập, hãy tạo bản sao rõ ràng:

```java
List<String> snapshot = new ArrayList<>(source.subList(1, 3));
```

### `Arrays.asList` không giống `ArrayList`, cũng không giống `List.of`

```java
Order[] array = {orderA, orderB};

List<Order> fixedSize = Arrays.asList(array);
fixedSize.set(0, orderC);  // hợp lệ và array[0] cũng đổi

// fixedSize.add(orderD);    // UnsupportedOperationException
// fixedSize.remove(orderB); // UnsupportedOperationException

List<Order> mutableCopy = new ArrayList<>(fixedSize);
List<Order> unmodifiableCopy = List.copyOf(fixedSize);
```

`Arrays.asList(array)` tạo một **List cố định kích thước, liên kết với mảng nguồn**: được thay phần tử bằng `set`, nhưng không được đổi kích thước bằng `add/remove`. `List.of(...)`/`List.copyOf(...)` tạo List không cho phép sửa; còn `new ArrayList<>(...)` tạo một List có thể thay đổi và độc lập về cấu trúc.

## <a id="arraylist-model">Mô hình ArrayList</a>

`ArrayList` nên được hình dung như một **mảng có khả năng tăng dung lượng**. Bên trong có vùng lưu trữ liên tiếp theo chỉ số và một kích thước logic cho biết có bao nhiêu phần tử đang được sử dụng.

Khi còn chỗ trống, thêm cuối rất rẻ:

```text
capacity = 8
size     = 5

[A][B][C][D][E][ ][ ][ ]
                  ↑
             add ở đây
```

Khi vùng lưu trữ không còn đủ, cách triển khai cấp một mảng lớn hơn và sao chép các phần tử sang đó. Vì việc tăng dung lượng chỉ xảy ra thỉnh thoảng, `add(E)` ở cuối có chi phí **amortized O(1)**, dù một lần tăng dung lượng riêng lẻ tốn O(n).

Truy cập theo chỉ số cũng rất phù hợp với mô hình mảng:

```java
Order order = orders.get(500);
```

`get(index)` có O(1) vì vị trí có thể được tính trực tiếp. Ngược lại, chèn hoặc xóa ở giữa thường cần dịch các phần tử phía sau:

```text
before: [A][B][C][D]
insert X at index 1
after : [A][X][B][C][D]
              └────────→ các phần tử bị dịch
```

Vì vậy:

| Thao tác điển hình | `ArrayList` |
| --- | --- |
| `get(index)` | O(1) |
| `set(index, value)` | O(1) |
| thêm cuối | amortized O(1) |
| chèn/xóa giữa | O(n) do dịch phần tử |
| tìm theo giá trị | O(n) nếu phải quét tuyến tính |

Big-O mô tả xu hướng chi phí, không phải kết quả đo hiệu năng tuyệt đối. Tính cục bộ của bộ nhớ đệm và chi phí phụ của đối tượng còn ảnh hưởng hiệu năng thực tế. Với kiểu sử dụng chủ yếu đọc theo chỉ số và thêm cuối, `ArrayList` thường là lựa chọn mặc định tốt cho `List`.

`ArrayList` không tự đồng bộ hóa cho truy cập từ nhiều luồng. Chi tiết xử lý đồng thời thuộc module riêng; ở đây chỉ cần nhớ rằng lựa chọn collection không tự động giải quyết quyền sở hữu và đồng bộ hóa dữ liệu.

## <a id="linkedlist-model">Mô hình LinkedList</a>

`LinkedList` dùng các node liên kết hai chiều. Mỗi node giữ phần tử và liên kết tới node trước/sau.

```text
null ← [A] ⇄ [B] ⇄ [C] → null
```

Mô hình này làm cho thêm/xóa ở đầu hoặc cuối rất tự nhiên:

```java
Deque<Order> pending = new LinkedList<>();
pending.addFirst(urgentOrder);
pending.addLast(normalOrder);
```

Nếu đã có tham chiếu tới đúng nút nội bộ, thao tác nối/cắt liên kết là hằng số. Nhưng API `List` không đưa nút đó cho bên gọi. Để thực hiện `get(5000)` hoặc `add(5000, value)`, `LinkedList` phải đi qua các nút từ một đầu tới vị trí cần tìm, nên bước định vị là O(n).

```java
Order order = linkedOrders.get(5000); // phải duyệt tới vị trí
```

Chi phí bộ nhớ cũng khác: mỗi phần tử cần một node với các liên kết, trong khi `ArrayList` chủ yếu giữ các tham chiếu trong mảng.

Vì vậy “`LinkedList` chèn/xóa nhanh hơn `ArrayList`” là kết luận quá rộng. Nó có lợi khi thao tác ở hai đầu hoặc khi thuật toán đã có vị trí iterator phù hợp; nó không tự động thắng với chèn/xóa theo chỉ số vì vẫn phải tìm nút.

Trong Java hiện đại, nếu nhu cầu thật sự là queue/stack hai đầu, `ArrayDeque` thường là lựa chọn rõ ràng hơn. `LinkedList` vẫn hữu ích khi cần đúng hợp đồng `List` cùng với thao tác hai đầu, nhưng nên chọn dựa trên kiểu sử dụng cụ thể.

## <a id="list-equality">Tính bằng nhau của List phụ thuộc thứ tự</a>

Hợp đồng `List.equals` không chỉ kiểm tra “cùng phần tử”. Hai list bằng nhau khi:

1. đối tượng kia cũng là `List`;
2. hai list có cùng số phần tử;
3. từng cặp phần tử ở **cùng vị trí** bằng nhau theo `equals`.

```java
List<Long> a = List.of(10L, 20L, 30L);
List<Long> b = List.of(10L, 20L, 30L);
List<Long> c = List.of(30L, 20L, 10L);

System.out.println(a.equals(b)); // true
System.out.println(a.equals(c)); // false
```

Do đó thứ tự duyệt là một phần của giá trị logic của `List`. Nếu ngữ nghĩa nghiệp vụ nói rằng thứ tự không quan trọng và chỉ cần tính duy nhất, `Set` có thể là mô hình trừu tượng đúng hơn.

Quy tắc đầy đủ của `equals/hashCode` thuộc module **Quy ước của Object**. Trong Collection, điều cần giữ là: quy tắc tính bằng nhau của phần tử ảnh hưởng tới nhiều thao tác như `contains`, `indexOf` và tính bằng nhau của cả List; còn thứ tự làm cho `List` khác `Set` về ý nghĩa.
