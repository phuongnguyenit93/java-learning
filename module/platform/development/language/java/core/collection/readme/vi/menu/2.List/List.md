# List

Sau khi biết cách đọc hierarchy, `List` là hợp đồng đầu tiên đáng học sâu vì nó phù hợp với dữ liệu mà **thứ tự và vị trí có ý nghĩa**. Danh sách đơn hàng theo thời điểm nhận là ví dụ tự nhiên: hai đơn hàng giống nhau vẫn có thể cùng xuất hiện, và “đơn đầu tiên” khác “đơn thứ ba”.

## <a id="list-semantics">Ngữ nghĩa của List</a>

`List<E>` là collection có encounter order xác định và hỗ trợ truy cập theo vị trí bằng index bắt đầu từ 0.

```java
List<Order> orders = new ArrayList<>();
orders.add(new Order(1001, 7, 120));
orders.add(new Order(1002, 8, 90));
orders.add(new Order(1003, 7, 120));

Order first = orders.get(0);
Order second = orders.get(1);
```

Thứ tự trong `List` là một phần của hợp đồng. Nếu thêm phần tử vào cuối, nó trở thành phần tử sau cùng; nếu chèn ở index 1, các phần tử phía sau dịch vị trí.

`List` cũng cho phép phần tử trùng theo `equals`:

```java
List<String> statuses = new ArrayList<>();
statuses.add("NEW");
statuses.add("NEW");

System.out.println(statuses.size()); // 2
```

Điều này khác `Set`, nơi uniqueness là hợp đồng chính. Nếu business requirement nói “mọi lần xuất hiện đều có ý nghĩa”, `List` thường là điểm bắt đầu phù hợp.

Các thao tác đặc trưng:

```java
orders.add(order);          // thêm cuối
orders.add(1, order);       // chèn theo vị trí
orders.get(1);              // đọc theo vị trí
orders.set(1, replacement); // thay phần tử tại vị trí
orders.remove(1);           // xóa theo vị trí
```

Từ Java 21, `List` là một `SequencedCollection`. Vì vậy các list phù hợp có API thống nhất để làm việc với hai đầu như `getFirst()`, `getLast()`, `addFirst()`, `addLast()` và `reversed()`. Khả năng sửa vẫn phụ thuộc collection cụ thể; một list unmodifiable có thể ném `UnsupportedOperationException` cho thao tác thay đổi.

## <a id="list-practical-boundaries">Các boundary thực tế của List</a>

### `remove(index)` khác `remove(value)`

`List` overload `remove`, nên với kiểu số rất dễ gọi nhầm:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

numbers.remove(1);                  // xóa phần tử tại index 1 -> 20
numbers.remove(Integer.valueOf(10)); // xóa value 10
```

Argument primitive `int` chọn overload `remove(int index)`. Muốn xóa một `Integer` theo value, truyền một `Integer` object rõ ràng.

### `subList` là view, không phải copy

```java
List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
List<String> middle = source.subList(1, 3); // [B, C]

middle.set(0, "X");
System.out.println(source); // [A, X, C, D]
```

`subList(from, to)` trả về một **backed view** của một vùng trong list gốc. Mutation hợp lệ qua view phản ánh vào source và ngược lại. Structural modification trực tiếp trên source ngoài cơ chế mà sub-list biết có thể làm view không còn hợp lệ và dẫn tới `ConcurrentModificationException` khi tiếp tục sử dụng.

Nếu cần dữ liệu độc lập, copy rõ ràng:

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

`Arrays.asList(array)` tạo **fixed-size list backed by array**: được thay element bằng `set`, nhưng không được đổi size bằng `add/remove`. `List.of(...)`/`List.copyOf(...)` tạo list unmodifiable; còn `new ArrayList<>(...)` tạo một mutable list độc lập về cấu trúc.

## <a id="arraylist-model">Mô hình ArrayList</a>

`ArrayList` nên được hình dung như một **mảng có khả năng tăng dung lượng**. Bên trong có vùng lưu trữ liên tiếp theo index và một `size` logic cho biết có bao nhiêu phần tử đang được sử dụng.

Khi còn chỗ trống, thêm cuối rất rẻ:

```text
capacity = 8
size     = 5

[A][B][C][D][E][ ][ ][ ]
                  ↑
             add ở đây
```

Khi vùng lưu trữ không còn đủ, implementation cấp một mảng lớn hơn và sao chép các phần tử sang đó. Vì việc resize chỉ xảy ra thỉnh thoảng, `add(E)` ở cuối có chi phí **amortized O(1)**, dù một lần resize riêng lẻ tốn O(n).

Truy cập index cũng rất phù hợp với mô hình mảng:

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
| tìm theo value | O(n) nếu phải quét tuyến tính |

Big-O mô tả xu hướng chi phí, không phải benchmark tuyệt đối. Cache locality và overhead đối tượng còn ảnh hưởng hiệu năng thực tế. Với workload chủ yếu đọc theo index và thêm cuối, `ArrayList` thường là lựa chọn mặc định tốt cho `List`.

`ArrayList` không đồng bộ hóa cho truy cập nhiều thread. Chi tiết concurrency thuộc module riêng; ở đây chỉ cần nhớ rằng lựa chọn collection không tự động giải quyết quyền sở hữu và đồng bộ hóa dữ liệu.

## <a id="linkedlist-model">Mô hình LinkedList</a>

`LinkedList` dùng các node liên kết hai chiều. Mỗi node giữ element và liên kết tới node trước/sau.

```text
null ← [A] ⇄ [B] ⇄ [C] → null
```

Mô hình này làm cho thêm/xóa ở đầu hoặc cuối rất tự nhiên:

```java
Deque<Order> pending = new LinkedList<>();
pending.addFirst(urgentOrder);
pending.addLast(normalOrder);
```

Nếu đã có tham chiếu tới đúng node nội bộ, thao tác nối/cắt liên kết là hằng số. Nhưng API `List` không đưa node đó cho caller. Để thực hiện `get(5000)` hoặc `add(5000, value)`, `LinkedList` phải đi qua các node từ một đầu tới vị trí cần tìm, nên bước định vị là O(n).

```java
Order order = linkedOrders.get(5000); // phải traversal tới vị trí
```

Chi phí bộ nhớ cũng khác: mỗi phần tử cần một node với các liên kết, trong khi `ArrayList` chủ yếu giữ các reference trong mảng.

Vì vậy “`LinkedList` chèn/xóa nhanh hơn `ArrayList`” là kết luận quá rộng. Nó có lợi khi thao tác ở hai đầu hoặc khi thuật toán đã có vị trí iterator phù hợp; nó không tự động thắng với chèn/xóa theo index vì vẫn phải tìm node.

Trong Java hiện đại, nếu nhu cầu thật sự là queue/stack hai đầu, `ArrayDeque` thường là lựa chọn rõ ràng hơn. `LinkedList` vẫn hữu ích khi cần đúng hợp đồng `List` cùng với thao tác hai đầu, nhưng nên chọn dựa trên workload cụ thể.

## <a id="list-equality">Equality của List phụ thuộc thứ tự</a>

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

Do đó encounter order là một phần của giá trị logic của `List`. Nếu business semantics nói rằng thứ tự không quan trọng và chỉ cần uniqueness, `Set` có thể là abstraction đúng hơn.

Quy tắc đầy đủ của `equals/hashCode` thuộc module Object Contract. Trong Collection, điều cần giữ là: contract equality của element ảnh hưởng tới nhiều thao tác như `contains`, `indexOf` và equality của cả list; còn thứ tự làm cho `List` khác `Set` về ý nghĩa.
